"""User-input callback correlation, HTTP boundaries and reconnect; no model access."""
import copy
import json
import threading
import unittest
from unittest.mock import Mock
import test_auth

server = test_auth.server


def request_message(wire_id=0, thread='fixture-thread-1'):
    return {'id': wire_id, 'method': 'item/tool/requestUserInput', 'params': {
        'threadId': thread, 'turnId': 'turn-input', 'itemId': 'call-input',
        'isBlocking': False, 'autoResolutionMs': None,
        'questions': [{'id': 'name', 'header': 'Name', 'question': 'Filename?',
                       'isOther': True, 'isSecret': False, 'options': [
                           {'label': 'a.txt', 'description': 'A'}, {'label': 'b.txt', 'description': 'B'}]}]}}


class UserInputTest(unittest.TestCase):
    setUp = test_auth.AuthenticationTest.setUp
    tearDown = test_auth.AuthenticationTest.tearDown
    request = test_auth.AuthenticationTest.request

    def prepare(self):
        self.sent = Mock()
        self.registry = server.APP.user_inputs = server.UserInputs(self.sent)
        server.APP.publish = Mock()
        server.APP.threads['fixture-thread-1']['turns'] = [
            {'id': 'turn-input', 'status': 'inProgress', 'items': []}]
        self.message = request_message()
        self.registry.receive(self.message)
        self.entry = self.registry.snapshot('fixture-thread-1')[0]
        self.path = '/threads/fixture-thread-1/requests/' + self.entry['id'] + '/answer'
        self.headers = {'Authorization': 'Bearer ' + self.token}

    def post(self, body, path=None):
        return self.request(path or self.path, 'POST', self.headers, json.dumps(body))[0]

    def test_http_validation_scope_duplicate_and_reconciliation(self):
        self.prepare()
        answer = {'answers': {'name': {'text': 'custom.txt'}}}
        for invalid in ({}, {'answers': {}}, {'answers': {'wrong': {'text': 'x'}}},
                        {'answers': {'name': {'option': 'invalid'}}},
                        {'answers': {'name': {'text': ''}}}, {'answers': {'name': {'text': ['a']}}},
                        {'answers': {'name': {'text': 'x', 'option': 'a.txt'}}}):
            self.assertEqual(400, self.post(invalid))
        other = server.APP.call('thread/start', {'cwd': server.APP.threads['fixture-thread-1']['cwd']})['thread']['id']
        self.assertEqual(404, self.post(answer, self.path.replace('fixture-thread-1', other)))
        self.assertEqual(404, self.post(answer, self.path.replace(self.entry['id'], 'unknown')))
        self.sent.assert_not_called()
        self.assertEqual(202, self.post(answer))
        self.sent.assert_called_once_with({'id': 0, 'result': {'answers': {'name': {'answers': ['custom.txt']}}}})
        self.assertEqual(409, self.post(answer))
        self.registry.receive(self.message)  # Resume replay while the write is already claimed.
        self.assertEqual('answering', self.registry.snapshot('fixture-thread-1')[0]['status'])
        self.registry.notification({'method': 'serverRequest/resolved', 'params': {'threadId': 'other', 'requestId': 0}})
        self.assertEqual(1, len(self.registry.snapshot('fixture-thread-1')))
        self.registry.notification({'method': 'serverRequest/resolved', 'params': {'threadId': 'fixture-thread-1', 'requestId': 0}})
        self.assertEqual(409, self.post(answer))
        self.assertEqual([], self.registry.snapshot('fixture-thread-1'))
        self.registry.receive(self.message)
        self.assertEqual([], self.registry.snapshot('fixture-thread-1'))

    def test_history_reconnect_and_terminal_cleanup(self):
        self.prepare()
        for _ in range(2):
            result = json.loads(self.request('/threads/fixture-thread-1/history', headers=self.headers)[2])
            self.assertEqual([self.entry], result['thread']['pendingRequests'])
            self.assertNotIn('rpcId', result['thread']['pendingRequests'][0])
        self.registry.receive(self.message)
        self.assertEqual(1, len(self.registry.snapshot('fixture-thread-1')))
        server.APP.threads['fixture-thread-1']['turns'][0]['status'] = 'interrupted'
        result = json.loads(self.request('/threads/fixture-thread-1/history', headers=self.headers)[2])
        self.assertEqual([], result['thread']['pendingRequests'])
        self.assertEqual(409, self.post({'answers': {'name': {'option': 'a.txt'}}}))
        self.sent.assert_not_called()

    def test_concurrent_taps_and_lost_write_never_forward_twice(self):
        self.prepare()
        barrier = threading.Barrier(2)
        codes = []
        def tap():
            barrier.wait()
            codes.append(self.post({'answers': {'name': {'option': 'b.txt'}}}))
        workers = [threading.Thread(target=tap) for _ in range(2)]
        for worker in workers: worker.start()
        for worker in workers: worker.join()
        self.assertEqual([202, 409], sorted(codes))
        self.sent.assert_called_once()
        fresh = request_message('different')
        self.registry.receive(fresh)
        entry = next(e for e in self.registry.snapshot('fixture-thread-1') if e['status'] == 'pending')
        self.sent.side_effect = BrokenPipeError()
        with self.assertRaises(BrokenPipeError):
            self.registry.answer('fixture-thread-1', entry['id'], {'answers': {'name': {'text': 'x'}}})
        with self.assertRaises(server.ApiError) as error:
            self.registry.answer('fixture-thread-1', entry['id'], {'answers': {'name': {'text': 'x'}}})
        self.assertEqual(409, error.exception.status)
        self.assertEqual(2, self.sent.call_count)

    def test_text_multiple_questions_string_ids_and_child_restart(self):
        self.prepare()
        message = request_message('0')  # Distinct from numeric zero.
        message['params']['questions'][0]['options'] = None
        second = copy.deepcopy(message['params']['questions'][0])
        second.update(id='choice', isOther=False, options=[{'label': 'Only', 'description': 'Exact'}])
        message['params']['questions'].append(second)
        self.registry.receive(message)
        entry = self.registry.snapshot('fixture-thread-1')[1]
        self.assertNotEqual(self.entry['id'], entry['id'])
        with self.assertRaises(server.ApiError):
            self.registry.answer('fixture-thread-1', entry['id'], {'answers': {'name': {'text': 'x'}, 'choice': {'text': 'Only'}}})
        self.registry.answer('fixture-thread-1', entry['id'], {'answers': {'name': {'text': 'x'}, 'choice': {'option': 'Only'}}})
        self.assertEqual('0', self.sent.call_args.args[0]['id'])
        self.registry.closed()
        self.assertEqual([], self.registry.snapshot('fixture-thread-1'))
        restarted = server.UserInputs(self.sent)
        restarted.receive(message)
        self.assertNotEqual(entry['id'], restarted.snapshot('fixture-thread-1')[0]['id'])

"""Small result-download boundary regression suite; no model access."""
import json
import os
from pathlib import Path
from unittest.mock import patch
import unittest
import test_auth
server = test_auth.server


class ArtifactTest(unittest.TestCase):
    setUp = test_auth.AuthenticationTest.setUp
    tearDown = test_auth.AuthenticationTest.tearDown
    request = test_auth.AuthenticationTest.request
    def test_artifacts_history_download_and_boundary(self):
        current = server.APP.threads['fixture-thread-1']
        cwd = Path(current['cwd'])
        nonce = 'a' * 32
        folder = cwd / server.result_folder(current['id'], nonce)
        folder.mkdir(parents=True)
        (folder / 'notes.md').write_text('Hallo Tablet\n')
        (cwd / 'patch.txt').write_text('Patch result')
        outside = server.ROOT / 'outside.txt'
        outside.write_text('private')
        (cwd / 'link.txt').symlink_to(outside)
        os.link(outside, cwd / 'hardlink.txt')
        (cwd / 'escape').symlink_to(server.ROOT, target_is_directory=True)
        (cwd / 'secret.txt').write_text('private')
        (cwd / 'fake.png').write_text('not a PNG')
        (cwd / 'large.txt').write_bytes(b'x' * 20)
        turn = {'id': 'result-turn', 'status': 'completed', 'items': [
            {'id': 'u', 'type': 'userMessage', 'content': [{'type': 'text', 'text': server.result_instruction(current['id'], nonce)}]},
            {'id': 'p', 'type': 'fileChange', 'status': 'completed', 'changes': [
                {'path': p, 'kind': {'type': 'add'}} for p in ['patch.txt', 'link.txt', 'hardlink.txt',
                'escape/outside.txt', '../outside.txt', str(outside), 'secret.txt', 'fake.png', 'large.txt']]},
            {'id': 'failed', 'type': 'fileChange', 'status': 'failed', 'changes': [{'path': 'failed.txt', 'kind': {'type': 'add'}}]},
            {'id': 'a', 'type': 'agentMessage', 'text': str(outside)}]}
        current['turns'] = [turn]
        headers = {'Authorization': 'Bearer ' + self.token}
        with patch.object(server, 'MAX_ARTIFACT', 16):
            status, _, raw = self.request('/threads/fixture-thread-1/history', headers=headers)
            self.assertEqual(200, status)
            artifacts = json.loads(raw)['thread']['turns'][0]['artifacts']
            self.assertEqual({'notes.md', 'patch.txt'}, {a['name'] for a in artifacts})
            self.assertTrue(all('path' not in a for a in artifacts))
            for artifact in artifacts:
                route = '/threads/fixture-thread-1/artifacts/' + artifact['id']
                self.assertEqual(401, self.request(route)[0])
                status, response_headers, body = self.request(route, headers=headers)
                self.assertEqual(200, status)
                self.assertEqual(artifact['size'], len(body))
                self.assertEqual(artifact['mimeType'], response_headers['Content-Type'])
                self.assertIn("filename*=UTF-8''", response_headers['Content-Disposition'])
                other = dict(current, id='other-thread')
                server.APP.threads['other-thread'] = other
                self.assertEqual(404, self.request('/threads/other-thread/artifacts/' + artifact['id'], headers=headers)[0])
            again = json.loads(self.request('/threads/fixture-thread-1/history', headers=headers)[2])
            self.assertEqual(artifacts, again['thread']['turns'][0]['artifacts'])
            for bad in ['..%2Foutside.txt', '%2Fetc%2Fpasswd', '0'*64]:
                self.assertEqual(404, self.request('/threads/fixture-thread-1/artifacts/' + bad, headers=headers)[0])
            target = next(a for a in artifacts if a['name'] == 'patch.txt')
            (cwd / 'patch.txt').unlink()
            (cwd / 'patch.txt').symlink_to(outside)
            self.assertEqual(404, self.request('/threads/fixture-thread-1/artifacts/' + target['id'], headers=headers)[0])
            (folder / 'notes.md').unlink()
            self.assertEqual([], json.loads(self.request('/threads/fixture-thread-1/history', headers=headers)[2])['thread']['turns'][0]['artifacts'])

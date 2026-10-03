"""Prompt and text-file transfer regressions against the production HTTP handler."""
import json
from pathlib import Path
from unittest.mock import patch
import unittest
from urllib.parse import quote
import test_auth

server = test_auth.server


class TransferTest(unittest.TestCase):
    setUp = test_auth.AuthenticationTest.setUp
    tearDown = test_auth.AuthenticationTest.tearDown
    request = test_auth.AuthenticationTest.request

    def multipart(self, message, name='seite.html', mime='text/html', content=b'<p>Hallo</p>'):
        boundary = 'transfer-fixture'
        body = (f'--{boundary}\r\nContent-Disposition: form-data; name="message"\r\n\r\n'.encode()
                + message.encode() + b'\r\n'
                + f'--{boundary}\r\nContent-Disposition: form-data; name="file"; filename="{name}"\r\nContent-Type: {mime}\r\n\r\n'.encode()
                + content + f'\r\n--{boundary}--\r\n'.encode())
        return {'Authorization': 'Bearer ' + self.token,
                'Content-Type': f'multipart/form-data; boundary={boundary}'}, body

    def test_message_boundaries_and_unchanged_unicode_in_json_and_multipart(self):
        original = server.APP.call
        captured = []
        def rpc(method, params, **kwargs):
            if method == 'turn/start':
                captured.append(params['input'])
                return {'turn': {'id': 'u', 'status': 'inProgress', 'items': []}}
            return original(method, params, **kwargs)
        with patch.object(server.APP, 'call', side_effect=rpc) as calls:
            for length in (4000, 8000, 12000, 12001):
                # Whitespace, German, combining characters, Markdown, code and astral Unicode.
                prefix = ' \nGrüße äöü ß e\u0301 👋\n# Prompt\n```python\nprint("Hallo")\n```\n'
                message = prefix + '😀' * (length - len(prefix) - 2) + '\n '
                self.assertEqual(length, len(message))
                for multipart in (False, True):
                    with self.subTest(length=length, multipart=multipart):
                        calls.reset_mock()
                        if multipart:
                            headers, body = self.multipart(message)
                        else:
                            headers = {'Authorization': 'Bearer ' + self.token, 'Content-Type': 'application/json'}
                            # Default JSON escaping needs up to 12 bytes per Unicode code point.
                            body = json.dumps({'message': message}).encode()
                        status, _, raw = self.request('/threads/fixture-thread-1/turns', 'POST', headers, body)
                        if length <= 12000:
                            self.assertEqual(202, status, raw)
                            self.assertEqual({'type': 'text', 'text': message}, captured[-1][0])
                        else:
                            self.assertEqual(400, status)
                            self.assertEqual('message_too_long', json.loads(raw)['code'])
                            self.assertIn('12000', json.loads(raw)['error'])
                            self.assertFalse(any(c.args[0] in ('turn/start', 'thread/resume') for c in calls.call_args_list))

    def test_upload_text_allowlist_preserves_name_and_content(self):
        original = server.APP.call
        captured = []
        def rpc(method, params, **kwargs):
            if method == 'turn/start':
                captured.append(params['input'])
                return {'turn': {'id': 'u', 'status': 'inProgress', 'items': []}}
            return original(method, params, **kwargs)
        content = ' \n<!doctype html>\n<p>Grüße 👋</p>\n<script>alert(1)</script>\n '
        with patch.object(server.APP, 'call', side_effect=rpc):
            for name, mime in [('seite.html', 'text/html'), ('seite.htm', 'text/html'), ('Grüße.html', 'text/html'),
                               ('SEITE.HTML', 'text/plain'), ('note.txt', 'text/plain'), ('note.md', 'text/markdown')]:
                with self.subTest(name=name, mime=mime):
                    headers, body = self.multipart('', name, mime, content.encode())
                    status, _, raw = self.request('/threads/fixture-thread-1/turns', 'POST', headers, body)
                    self.assertEqual(202, status, raw)
                    self.assertEqual({'type': 'text', 'text': f'Dateianhang: {name}\n-----\n{content}\n-----'}, captured[-1][0])
            for name, mime, data in [('run.exe', 'text/html', b'text'), ('app.js', 'text/plain', b'text'),
                                     ('bad.html', 'application/octet-stream', b'text'),
                                     ('bad.htm', 'text/html', b'\xff'), ('bad.html', 'text/html', b'\0'),
                                     ('large.html', 'text/html', b'x' * (server.MAX_TEXT_FILE + 1))]:
                with self.subTest(name=name, mime=mime, size=len(data)):
                    count = len(captured)
                    headers, body = self.multipart('', name, mime, data)
                    self.assertIn(self.request('/threads/fixture-thread-1/turns', 'POST', headers, body)[0], (400, 415))
                    self.assertEqual(count, len(captured))

    def test_html_download_preserves_bytes_names_and_workspace_boundaries(self):
        current = server.APP.threads['fixture-thread-1']
        cwd = Path(current['cwd'])
        names = ['Grüße.html', 'seite.htm', 'SEITE.HTML', 'notes.md', 'note.txt']
        content = '<!doctype html>\r\n<p>Grüße 👋</p>\n<script>alert(1)</script>\n'.encode()
        for name in names:
            (cwd / name).write_bytes(content)
        (cwd / 'bad.html').write_bytes(b'\xff')
        (cwd / 'nul.htm').write_bytes(b'\0')
        (cwd / 'app.js').write_bytes(content)
        (cwd / 'secret.html').write_bytes(content)
        outside = server.ROOT / 'outside.html'
        outside.write_bytes(content)
        (cwd / 'link.html').symlink_to(outside)
        current['turns'] = [{'id': 'u', 'status': 'completed', 'items': [
            {'type': 'fileChange', 'status': 'completed', 'changes': [
                {'path': n, 'kind': {'type': 'add'}} for n in names +
                ['bad.html', 'nul.htm', 'app.js', 'secret.html', 'link.html', '../outside.html']]}]}]
        for nonce, name, legacy in [('a' * 32, 'new-results.html', False),
                                    ('b' * 32, 'old-results.htm', True)]:
            folder = cwd / server.result_folder(current['id'], nonce)
            folder.mkdir(parents=True)
            (folder / name).write_bytes(content)
            instruction = server.result_instruction(current['id'], nonce)
            if legacy:
                instruction = instruction.replace('TXT, Markdown or HTML results', 'TXT or Markdown results')
            current['turns'][0]['items'].append({'type': 'userMessage', 'content': [{'type': 'text', 'text': instruction}]})
            names.append(name)
        headers = {'Authorization': 'Bearer ' + self.token}
        status, _, raw = self.request('/threads/fixture-thread-1/history', headers=headers)
        self.assertEqual(200, status)
        artifacts = json.loads(raw)['thread']['turns'][0]['artifacts']
        self.assertEqual(set(names), {a['name'] for a in artifacts})
        for artifact in artifacts:
            path = '/threads/fixture-thread-1/artifacts/' + artifact['id']
            self.assertEqual(401, self.request(path)[0])
            status, response_headers, data = self.request(path, headers=headers)
            self.assertEqual(200, status)
            self.assertEqual(content, data)
            self.assertEqual(server.TEXT_TYPES[Path(artifact['name']).suffix.lower()], response_headers['Content-Type'])
            self.assertIn("filename*=UTF-8''" + quote(artifact['name']), response_headers['Content-Disposition'])

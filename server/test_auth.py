"""Local HTTP security regression tests. No Codex process, model, or remote access."""
import contextlib
import http.client
import importlib.util
import io
import os
import secrets
import socket
import subprocess
import sys
import time
import json
import tempfile
import threading
import unittest
from pathlib import Path
from unittest.mock import patch

spec = importlib.util.spec_from_file_location("fixture", Path(__file__).resolve().parents[1] / "android/tools/contract_server.py")
fixture = importlib.util.module_from_spec(spec)
spec.loader.exec_module(fixture)
server = fixture.server


class AuthenticationTest(unittest.TestCase):
    def setUp(self):
        self.directory = tempfile.TemporaryDirectory()
        server.ROOT = Path(self.directory.name)
        (server.ROOT / "demo space-ä").mkdir()
        server.APP = fixture.FixtureBackend(server.ROOT)
        self.token = secrets.token_urlsafe(48)
        server.ACCESS_TOKEN = self.token
        self.httpd = server.ThreadingHTTPServer(("127.0.0.1", 0), server.Handler)
        self.worker = threading.Thread(target=self.httpd.serve_forever, daemon=True)
        self.worker.start()

    def tearDown(self):
        self.httpd.shutdown()
        self.httpd.server_close()
        self.worker.join()
        self.directory.cleanup()

    def request(self, path, method="GET", headers=None, body=None):
        connection = http.client.HTTPConnection(*self.httpd.server_address, timeout=3)
        try:
            connection.request(method, path, body, headers or {})
            response = connection.getresponse()
            return response.status, dict(response.getheaders()), response.read()
        finally:
            connection.close()

    def test_every_route_rejects_before_backend_or_body(self):
        routes = [("GET", "/workspaces"), ("GET", "/workspaces/demo/threads"),
                  ("POST", "/workspaces/demo/threads"), ("GET", "/threads/t"),
                  ("GET", "/threads/t/history"), ("POST", "/threads/t/turns"),
                  ("GET", "/threads/t/events"), ("GET", "/unknown"), ("DELETE", "/threads/t")]
        with patch.object(server.APP, "call", side_effect=AssertionError("backend reached")):
            for method, path in routes:
                for auth in (None, "Bearer " + secrets.token_urlsafe(48), "Basic " + self.token):
                    with self.subTest(method=method, path=path, auth=bool(auth)):
                        status, headers, body = self.request(path, method, {"Authorization": auth} if auth else {}, "invalid")
                        self.assertEqual(401, status)
                        self.assertEqual('Bearer realm="CodexPad"', headers["WWW-Authenticate"])
                        self.assertEqual("close", headers["Connection"])
                        self.assertNotIn(self.token.encode(), body)

    def test_health_is_minimal_and_backend_failure_is_503(self):
        self.assertEqual((200, b'{"status": "ok"}'), self.request("/health")[::2])
        with patch.object(server.APP.proc, "poll", return_value=1):
            self.assertEqual((503, b'{"status": "unavailable"}'), self.request("/health")[::2])

    def test_valid_auth_and_rotation(self):
        headers = {"Authorization": "Bearer " + self.token}
        self.assertEqual(200, self.request("/workspaces", headers=headers)[0])
        server.ACCESS_TOKEN = secrets.token_urlsafe(48)
        self.assertEqual(401, self.request("/workspaces", headers=headers)[0])
        self.assertEqual(200, self.request("/workspaces", headers={"Authorization": "Bearer " + server.ACCESS_TOKEN})[0])

    def test_duplicate_header_rejected(self):
        connection = http.client.HTTPConnection(*self.httpd.server_address, timeout=3)
        try:
            connection.putrequest("GET", "/workspaces")
            for _ in range(2):
                connection.putheader("Authorization", "Bearer " + self.token)
            connection.endheaders()
            self.assertEqual(401, connection.getresponse().status)
        finally:
            connection.close()

    def test_no_query_auth_and_no_secret_logging_even_for_parser_errors(self):
        output = io.StringIO()
        with contextlib.redirect_stderr(output):
            self.assertEqual(401, self.request("/workspaces?token=" + self.token)[0])
            self.assertEqual(401, self.request("/health?token=" + self.token)[0])
            self.request("/" + self.token, method=self.token)
        self.assertEqual("", output.getvalue())

    def test_missing_config_fails_closed(self):
        server.ACCESS_TOKEN = None
        self.assertEqual(401, self.request("/workspaces", headers={"Authorization": "Bearer "})[0])

    def test_token_configuration_validation_and_child_environment(self):
        for value in ("", "short", "x" * 42, "x" * 513, "a " * 40, "ü" * 64):
            with patch.dict(os.environ, {"CODEXPAD_ACCESS_TOKEN": value}):
                with self.assertRaises(ValueError) as error:
                    server.load_access_token()
                self.assertNotIn(value, str(error.exception)) if value else None
                self.assertNotIn("CODEXPAD_ACCESS_TOKEN", os.environ)
        with patch.dict(os.environ, {"CODEXPAD_ACCESS_TOKEN": self.token}):
            self.assertEqual(self.token, server.load_access_token())
            self.assertNotIn("CODEXPAD_ACCESS_TOKEN", os.environ)


class StartupTest(unittest.TestCase):
    def test_missing_token_stops_before_codex(self):
        env = {key: value for key, value in os.environ.items() if key != "CODEXPAD_ACCESS_TOKEN"}
        result = subprocess.run([sys.executable, "-B", str(Path(server.__file__))],
                                env=env, capture_output=True, text=True, timeout=5)
        self.assertNotEqual(0, result.returncode)
        self.assertIn("CODEXPAD_ACCESS_TOKEN must be", result.stderr)
        self.assertEqual("", result.stdout)

    def test_stdio_child_has_no_token_and_sigterm_cleans_it_up(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            executable = root / "codex"
            executable.write_text("#!" + sys.executable + "\n" + '''
import json, os, sys
from pathlib import Path
if "--version" in sys.argv:
    print("codex-test-fixture")
    sys.exit(0)
Path(os.environ["TEST_MARKER"]).write_text(json.dumps({
    "secretInherited": "CODEXPAD_ACCESS_TOKEN" in os.environ,
    "pid": os.getpid(), "args": sys.argv[1:]}))
for line in sys.stdin:
    request = json.loads(line)
    if "id" in request:
        print(json.dumps({"id": request["id"], "result": {}}), flush=True)
''')
            executable.chmod(0o700)
            with socket.socket() as probe:
                probe.bind(("127.0.0.1", 0))
                port = probe.getsockname()[1]
            token = secrets.token_urlsafe(48)
            marker = root / "child.json"
            env = {**os.environ, "PATH": str(root) + os.pathsep + os.environ["PATH"],
                   "CODEXPAD_ACCESS_TOKEN": token, "CODEXPAD_PORT": str(port),
                   "CODEXPAD_WORKSPACE_ROOT": str(root / "workspaces"), "TEST_MARKER": str(marker)}
            proc = subprocess.Popen([sys.executable, "-B", str(Path(server.__file__))],
                                    env=env, stdout=subprocess.PIPE, stderr=subprocess.PIPE, text=True)
            try:
                for _ in range(100):
                    connection = http.client.HTTPConnection("127.0.0.1", port, timeout=.2)
                    try:
                        connection.request("GET", "/health")
                        if connection.getresponse().status == 200:
                            break
                    except OSError:
                        time.sleep(.02)
                    finally:
                        connection.close()
                else:
                    self.fail("Local fixture server failed to start")
                child = json.loads(marker.read_text())
                self.assertFalse(child["secretInherited"])
                self.assertEqual(["app-server", "--stdio",
                                  "-c", 'sandbox_mode="danger-full-access"',
                                  "-c", 'approval_policy="never"'], child["args"])
            finally:
                proc.terminate()
                stdout, stderr = proc.communicate(timeout=10)
            self.assertEqual(0, proc.returncode)
            self.assertNotIn(token, stdout + stderr)
            self.assertIn("127.0.0.1:" + str(port), stdout)
            with self.assertRaises(ProcessLookupError):
                os.kill(child["pid"], 0)


if __name__ == "__main__":
    unittest.main()

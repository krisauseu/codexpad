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
        server.ROOT = Path(self.directory.name).resolve()
        server.UPLOAD_ROOT = server.ROOT / "uploads"
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
        routes = [("GET", "/models"), ("GET", "/workspaces"), ("GET", "/workspaces/demo/threads"),
                  ("POST", "/workspaces/demo/threads"), ("GET", "/threads/t"),
                  ("GET", "/threads/t/history"), ("POST", "/threads/t/turns"), ("POST", "/threads/t/compact"),
                  ("POST", "/threads/t/turns/u/interrupt"), ("POST", "/threads/t/requests/r/answer"),
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

    def test_models_pagination_projection_and_turn_validation(self):
        headers = {"Authorization": "Bearer " + self.token}
        original = server.APP.call
        current = server.APP.threads["fixture-thread-1"]
        current["model"] = "a"
        entries = [{"id": "catalog-" + name, "model": name, "displayName": name,
                    "description": "test", "isDefault": name == "b",
                    "supportedReasoningEfforts": [{"reasoningEffort": e, "description": e} for e in efforts],
                    "defaultReasoningEffort": efforts[0], "hidden": False, "unneeded": "secret"}
                   for name, efforts in [("a", ["low", "custom"]), ("b", ["high"])]]
        def rpc(method, params, **kwargs):
            if method == "model/list":
                return {"data": [entries[1 if params["cursor"] else 0]],
                        "nextCursor": None if params["cursor"] else "page2"}
            if method == "turn/start":
                current.update({k: params[k] for k in ("model",) if k in params})
                if "effort" in params: current["reasoningEffort"] = params["effort"]
                return {"turn": {"id": "u", "status": "completed", "items": []}}
            return original(method, params, **kwargs)
        with patch.object(server.APP, "call", side_effect=rpc) as calls:
            status, _, body = self.request("/models", headers=headers)
            self.assertEqual(200, status)
            catalog = json.loads(body)["models"]
            self.assertEqual(["a", "b"], [m["model"] for m in catalog])
            self.assertNotIn("unneeded", catalog[0])
            self.assertEqual([None, "page2"], [c.args[1]["cursor"] for c in calls.call_args_list])
            for overrides in ({}, {"model": "a"}, {"model": "a", "effort": "custom"}, {"effort": "low"}):
                calls.reset_mock()
                self.assertEqual(202, self.request("/threads/fixture-thread-1/turns", "POST", headers,
                    json.dumps({"message": "test", **overrides}))[0])
                start = next(c.args[1] for c in calls.call_args_list if c.args[0] == "turn/start")
                self.assertEqual(overrides, {k: start[k] for k in ("model", "effort") if k in start})
                resume = next(c.args[1] for c in calls.call_args_list if c.args[0] == "thread/resume")
                self.assertEqual({"threadId": current["id"]}, resume)
            state = json.loads(self.request("/threads/fixture-thread-1/history", headers=headers)[2])["thread"]
            self.assertEqual(("a", "low"), (state["model"], state["reasoningEffort"]))
            for overrides in ({"model": "missing"}, {"model": "b", "effort": "low"},
                              {"effort": None}, {"model": 1}, {"model": ""}, {"config": {}}):
                calls.reset_mock()
                self.assertEqual(400, self.request("/threads/fixture-thread-1/turns", "POST", headers,
                    json.dumps({"message": "test", **overrides}))[0])
                self.assertFalse(any(c.args[0] in ("turn/start", "thread/resume") for c in calls.call_args_list))
        with patch.object(server.APP, "call", return_value={"data": [], "nextCursor": "loop"}):
            self.assertEqual(502, self.request("/models", headers=headers)[0])

    def test_multipart_image_reaches_turn_as_private_local_image(self):
        boundary = "codexpad-test-boundary"
        png = b"\x89PNG\r\n\x1a\n" + b"fixture"
        def part(name, value, filename=None, mime=None):
            heading = f'--{boundary}\r\nContent-Disposition: form-data; name="{name}"'
            if filename: heading += f'; filename="{filename}"'
            heading += "\r\n"
            if mime: heading += f"Content-Type: {mime}\r\n"
            return heading.encode() + b"\r\n" + value + b"\r\n"
        body = part("message", "Was ist zu sehen?".encode()) + part("image", png,
            "../../private.png", "image/png") + f"--{boundary}--\r\n".encode()
        headers = {"Authorization": "Bearer " + self.token,
                   "Content-Type": f"multipart/form-data; boundary={boundary}"}
        original = server.APP.call
        captured = []
        def rpc(method, params, **kwargs):
            if method == "turn/start":
                captured.append(params["input"])
                return {"turn": {"id": "u", "status": "inProgress", "items": []}}
            return original(method, params, **kwargs)
        with patch.object(server.APP, "call", side_effect=rpc):
            self.assertEqual(202, self.request("/threads/fixture-thread-1/turns", "POST", headers, body)[0])
            inputs = captured[0]
            self.assertEqual("Was ist zu sehen?", inputs[0]["text"])
            path = Path(inputs[1]["path"])
            self.assertEqual(server.UPLOAD_ROOT, path.parent)
            self.assertEqual(png, path.read_bytes())
            self.assertNotIn("private", path.name)
            bad = body.replace(b"image/png", b"image/jpeg")
            self.assertEqual(415, self.request("/threads/fixture-thread-1/turns", "POST", headers, bad)[0])
            self.assertEqual(1, len(captured))
            text_body = part("message", b"") + part("file", b"CODEXPAD-FILE-OK",
                "../note.md", "text/markdown") + f"--{boundary}--\r\n".encode()
            self.assertEqual(202, self.request("/threads/fixture-thread-1/turns", "POST", headers, text_body)[0])
            self.assertIn("Dateianhang: note.md", captured[-1][0]["text"])
            self.assertIn("CODEXPAD-FILE-OK", captured[-1][0]["text"])
            invalid = text_body.replace(b"CODEXPAD-FILE-OK", b"\xff")
            self.assertEqual(415, self.request("/threads/fixture-thread-1/turns", "POST", headers, invalid)[0])

    def test_compact_narrow_rpc_boundaries_and_no_retry(self):
        current = server.APP.threads["fixture-thread-1"]
        headers = {"Authorization": "Bearer " + self.token}
        path = "/threads/fixture-thread-1/compact"
        original = server.APP.call
        def rpc(method, params, **kwargs):
            return {} if method == "thread/compact/start" else original(method, params, **kwargs)
        with patch.object(server.APP, "call", side_effect=rpc) as calls:
            status, _, body = self.request(path, "POST", headers, "{}")
            self.assertEqual((202, {}), (status, json.loads(body)))
            self.assertEqual(["thread/read", "thread/compact/start"], [c.args[0] for c in calls.call_args_list])
            self.assertEqual({"threadId": current["id"]}, calls.call_args_list[-1].args[1])
            for payload in ('{"method":"anything"}', '{"experimentalApi":true}', '[]'):
                calls.reset_mock()
                self.assertEqual(400, self.request(path, "POST", headers, payload)[0])
                self.assertFalse(any(c.args[0] == "thread/compact/start" for c in calls.call_args_list))
            current["turns"] = [{"id": "busy", "status": "inProgress"}]
            self.assertEqual(409, self.request(path, "POST", headers, "{}")[0])
            current["turns"] = []
            current["cwd"] = "/outside"
            self.assertEqual(404, self.request(path, "POST", headers, "{}")[0])
        current["cwd"] = str(server.ROOT / "demo space-ä")
        server.APP.fresh[current["id"]] = current
        with patch.object(server.APP, "call", side_effect=server.ApiError(504, "timeout")) as calls:
            self.assertEqual(504, self.request(path, "POST", headers, "{}")[0])
            self.assertEqual(1, calls.call_count)  # No fresh fallback for a mutation.
        def lost(method, params, **kwargs):
            if method == "thread/compact/start":
                raise server.ApiError(504, "unknown outcome")
            return original(method, params, **kwargs)
        with patch.object(server.APP, "call", side_effect=lost) as calls:
            self.assertEqual(504, self.request(path, "POST", headers, "{}")[0])
            self.assertEqual(1, sum(c.args[0] == "thread/compact/start" for c in calls.call_args_list))

    def test_interrupt_exact_target_and_boundaries(self):
        current = server.APP.threads["fixture-thread-1"]
        current["turns"] = [{"id": "old", "status": "completed"},
                            {"id": "running", "status": "inProgress"}]
        headers = {"Authorization": "Bearer " + self.token}
        path = "/threads/fixture-thread-1/turns/"
        original = server.APP.call
        for target, body, expected in [("running", "{}", 202), ("old", "{}", 409),
                                       ("missing", "{}", 409), ("running", '{"turnId":"other"}', 400)]:
            with self.subTest(target=target, body=body):
                def rpc(method, params, **kwargs):
                    return {} if method == "turn/interrupt" else original(method, params, **kwargs)
                with patch.object(server.APP, "call", side_effect=rpc) as calls:
                    self.assertEqual(expected, self.request(path + target + "/interrupt", "POST", headers, body)[0])
                    interrupts = [c for c in calls.call_args_list if c.args[0] == "turn/interrupt"]
                    self.assertEqual(1 if expected == 202 else 0, len(interrupts))
                    if interrupts:
                        self.assertEqual({"threadId": current["id"], "turnId": "running"}, interrupts[0].args[1])
                    self.assertTrue(all(c.args[0] in ("thread/read", "turn/interrupt") for c in calls.call_args_list))
        current["cwd"] = "/outside/workspaces"
        with patch.object(server.APP, "call", wraps=original) as calls:
            self.assertEqual(404, self.request(path + "running/interrupt", "POST", headers, "{}")[0])
            self.assertEqual(["thread/read"], [c.args[0] for c in calls.call_args_list])

    def test_interrupt_does_not_fallback_or_retarget_on_read_failure_or_race(self):
        current = server.APP.threads["fixture-thread-1"]
        current["turns"] = [{"id": "running", "status": "inProgress"}]
        server.APP.fresh[current["id"]] = current
        headers = {"Authorization": "Bearer " + self.token}
        path = "/threads/fixture-thread-1/turns/running/interrupt"
        with patch.object(server.APP, "call", side_effect=server.ApiError(504, "timeout")) as calls:
            self.assertEqual(504, self.request(path, "POST", headers, "{}")[0])
            self.assertEqual(1, calls.call_count)
        original = server.APP.call
        def race(method, params, **kwargs):
            if method == "turn/interrupt":
                self.assertEqual("running", params["turnId"])
                current["turns"][0]["status"] = "completed"
                current["turns"].append({"id": "newer", "status": "inProgress"})
                raise server.ApiError(502, "turn is no longer active")
            return original(method, params, **kwargs)
        with patch.object(server.APP, "call", side_effect=race) as calls:
            self.assertEqual(502, self.request(path, "POST", headers, "{}")[0])
            self.assertEqual(409, self.request(path, "POST", headers, "{}")[0])
            self.assertEqual(1, sum(c.args[0] == "turn/interrupt" for c in calls.call_args_list))
        self.assertEqual("inProgress", current["turns"][-1]["status"])

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
                              "-c", 'approval_policy="never"',
                              "-c", 'features.default_mode_request_user_input=true'], child["args"])
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

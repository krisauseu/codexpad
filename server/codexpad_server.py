#!/usr/bin/env python3
"""Authenticated, loopback-only single-user CodexPad API. Standard library only."""

import hashlib
import hmac
import re
import signal
import json
import os
import platform
import queue
import subprocess
import sys
import threading
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
from urllib.parse import unquote, urlsplit


ROOT = Path(os.environ.get("CODEXPAD_WORKSPACE_ROOT", "~/projects")).expanduser().resolve()
PORT = int(os.environ.get("CODEXPAD_PORT", "8765"))


class ApiError(Exception):
    def __init__(self, status, message):
        self.status, self.message = status, message


def load_access_token():
    # Remove it before starting Codex or any other subprocess. Never include values in errors.
    token = os.environ.pop("CODEXPAD_ACCESS_TOKEN", "")
    if not re.fullmatch(r"[A-Za-z0-9_-]{43,512}", token):
        raise ValueError("CODEXPAD_ACCESS_TOKEN must be a random URL-safe token of 43–512 characters")
    return token


ACCESS_TOKEN = None


class AppServer:
    def __init__(self):
        self.proc = subprocess.Popen(["codex", "app-server", "--stdio"],
                                     stdin=subprocess.PIPE, stdout=subprocess.PIPE,
                                     stderr=subprocess.PIPE, text=True, bufsize=1)
        self.pending = {}
        self.next_id = 1
        self.lock = threading.Lock()
        self.subscribers = {}
        self.fresh = {}
        self.init_result = None
        threading.Thread(target=self._read, daemon=True).start()
        threading.Thread(target=self._stderr, daemon=True).start()
        self.init_result = self.call("initialize", {
            "clientInfo": {"name": "codexpad_local", "title": "CodexPad local spike", "version": "0.1.0"}
        })
        self.send({"method": "initialized", "params": {}})

    def _stderr(self):
        for line in self.proc.stderr:
            print("[codex]", line.rstrip().replace(ACCESS_TOKEN, "[REDACTED]") if ACCESS_TOKEN else line.rstrip(), file=sys.stderr)

    def send(self, message):
        with self.lock:
            if self.proc.poll() is not None:
                raise ApiError(502, "Codex App Server exited")
            self.proc.stdin.write(json.dumps(message, ensure_ascii=False) + "\n")
            self.proc.stdin.flush()

    def call(self, method, params, timeout=60):
        reply = queue.Queue(maxsize=1)
        with self.lock:
            request_id = self.next_id
            self.next_id += 1
            self.pending[request_id] = reply
        try:
            self.send({"id": request_id, "method": method, "params": params})
            try:
                result = reply.get(timeout=timeout)
            except queue.Empty:
                raise ApiError(504, f"App Server timeout: {method}")
            if "error" in result:
                raise ApiError(502, f"App Server {method}: {result['error'].get('message', 'error')}")
            return result["result"]
        finally:
            with self.lock:
                self.pending.pop(request_id, None)

    def _read(self):
        for line in self.proc.stdout:
            try:
                message = json.loads(line)
            except json.JSONDecodeError:
                continue
            if "id" in message and ("result" in message or "error" in message):
                with self.lock:
                    reply = self.pending.get(message["id"])
                if reply:
                    reply.put(message)
            elif "id" in message and "method" in message:
                # Approvals are deliberately outside this slice. Never leave a request unanswered.
                self.send({"id": message["id"], "error": {"code": -32601, "message": "CodexPad approval handling is not implemented"}})
            elif "method" in message:
                params = message.get("params") or {}
                thread_id = params.get("threadId") or (params.get("thread") or {}).get("id")
                if thread_id:
                    with self.lock:
                        listeners = list(self.subscribers.get(thread_id, ()))
                    for listener in listeners:
                        try:
                            listener.put_nowait(message)
                        except queue.Full:
                            # A slow client must resynchronize through thread/read.
                            try:
                                listener.get_nowait()
                                listener.put_nowait({"method": "codexpad/overflow", "params": {"threadId": thread_id}})
                            except queue.Empty:
                                pass
        with self.lock:
            pending = list(self.pending.values())
        for reply in pending:
            try:
                reply.put_nowait({"error": {"message": "App Server connection closed"}})
            except queue.Full:
                pass

    def subscribe(self, thread_id):
        listener = queue.Queue(maxsize=256)
        with self.lock:
            self.subscribers.setdefault(thread_id, set()).add(listener)
        return listener

    def unsubscribe(self, thread_id, listener):
        with self.lock:
            self.subscribers.get(thread_id, set()).discard(listener)


def workspaces():
    if not ROOT.is_dir():
        return {}
    return {p.name: p.resolve() for p in ROOT.iterdir()
            if p.is_dir() and not p.is_symlink() and p.name not in (".", "..")}


APP = None


def workspace(workspace_id):
    path = workspaces().get(workspace_id)
    if not path:
        raise ApiError(404, "Unknown workspace")
    return path


def thread(thread_id, include_turns=False):
    try:
        result = APP.call("thread/read", {"threadId": thread_id, "includeTurns": include_turns})["thread"]
    except ApiError:
        result = APP.fresh.get(thread_id)
        if not result:
            raise
    if result.get("cwd") not in {str(p) for p in workspaces().values()}:
        raise ApiError(404, "Thread is not in a configured workspace")
    return result


class Handler(BaseHTTPRequestHandler):
    protocol_version = "HTTP/1.1"

    # No request/header/path logging, including parser errors and query-string mistakes.
    def log_message(self, format, *args):
        pass

    def send_error(self, code, message=None, explain=None):
        self.close_connection = True
        self.send_json(code, {"error": "Invalid HTTP request"})

    def parse_request(self):
        if not super().parse_request():
            return False
        public_health = self.command == "GET" and self.path == "/health"
        if not public_health:
            values = self.headers.get_all("Authorization", [])
            expected = "Bearer " + (ACCESS_TOKEN or "")
            authorized = (ACCESS_TOKEN is not None and len(values) == 1 and
                          hmac.compare_digest(hashlib.sha256(values[0].encode()).digest(),
                                              hashlib.sha256(expected.encode()).digest()))
            if not authorized:
                # Do not reuse a connection containing an unread, unauthorized POST body.
                self.close_connection = True
                self.send_json(401, {"error": "Unauthorized"})
                return False
        return True

    def send_json(self, status, body):
        data = json.dumps(body, ensure_ascii=False).encode()
        self.send_response(status)
        self.send_header("Content-Type", "application/json; charset=utf-8")
        self.send_header("Content-Length", str(len(data)))
        self.send_header("Cache-Control", "no-store")
        if status == 401:
            self.send_header("WWW-Authenticate", 'Bearer realm="CodexPad"')
        if self.close_connection:
            self.send_header("Connection", "close")
        self.end_headers()
        self.wfile.write(data)

    def read_json(self):
        try:
            size = int(self.headers.get("Content-Length", "0"))
            if size < 1 or size > 16384:
                raise ValueError()
            data = json.loads(self.rfile.read(size))
            if not isinstance(data, dict):
                raise ValueError()
            return data
        except (ValueError, json.JSONDecodeError):
            raise ApiError(400, "Expected JSON object of at most 16 KiB")

    def route(self, method):
        parts = [unquote(x) for x in urlsplit(self.path).path.split("/") if x]
        if method == "GET" and parts == ["health"]:
            return (200, {"status": "ok"}) if APP is not None and APP.proc.poll() is None else (503, {"status": "unavailable"})
        if method == "GET" and parts == ["workspaces"]:
            return 200, {"workspaces": [{"id": key, "name": key} for key in sorted(workspaces())]}
        if len(parts) == 3 and parts[0] == "workspaces" and parts[2] == "threads":
            cwd = workspace(parts[1])
            if method == "POST":
                if self.read_json():
                    raise ApiError(400, "Thread creation accepts an empty JSON object")
                result = APP.call("thread/start", {"cwd": str(cwd), "approvalPolicy": "never"})
                t = result["thread"]
                APP.fresh[t["id"]] = t
                return 201, {"thread": t}
            if method == "GET":
                all_threads, cursor = [], None
                while True:
                    result = APP.call("thread/list", {"cwd": str(cwd), "limit": 100, "cursor": cursor})
                    all_threads += [t for t in result.get("data", []) if t.get("cwd") == str(cwd)]
                    cursor = result.get("nextCursor")
                    if not cursor:
                        break
                ids = {t["id"] for t in all_threads}
                all_threads += [t for t in APP.fresh.values() if t.get("cwd") == str(cwd) and t["id"] not in ids]
                return 200, {"threads": all_threads}
        if len(parts) >= 2 and parts[0] == "threads":
            thread_id = parts[1]
            if len(parts) == 2 and method == "GET":
                return 200, {"thread": thread(thread_id)}
            if len(parts) == 3 and parts[2] == "history" and method == "GET":
                return 200, {"thread": thread(thread_id, True)}
            if len(parts) == 3 and parts[2] == "turns" and method == "POST":
                current = thread(thread_id)
                payload = self.read_json()
                message = payload.get("message")
                if not isinstance(message, str) or not message.strip() or len(message) > 4096:
                    raise ApiError(400, "message must contain 1 to 4096 characters")
                # Resume is idempotent for a persisted thread; a new turn is not.
                try:
                    APP.call("thread/resume", {"threadId": thread_id}, timeout=60)
                except ApiError:
                    if thread_id not in APP.fresh:
                        raise
                result = APP.call("turn/start", {"threadId": thread_id,
                                                  "input": [{"type": "text", "text": message}]}, timeout=60)
                APP.fresh.pop(thread_id, None)
                return 202, result
            if len(parts) == 3 and parts[2] == "events" and method == "GET":
                thread(thread_id)
                listener = APP.subscribe(thread_id)
                try:
                    # Subscribe first, then read the authoritative snapshot: events during read queue up.
                    try:
                        APP.call("thread/resume", {"threadId": thread_id}, timeout=60)
                    except ApiError:
                        if thread_id not in APP.fresh:
                            raise
                    snapshot = thread(thread_id, True)
                    self.send_response(200)
                    self.send_header("Content-Type", "text/event-stream; charset=utf-8")
                    self.send_header("Cache-Control", "no-cache")
                    self.send_header("Connection", "close")
                    self.end_headers()
                    self.sse("snapshot", {"thread": snapshot})
                    while True:
                        try:
                            event = listener.get(timeout=15)
                        except queue.Empty:
                            self.wfile.write(b": heartbeat\n\n")
                            self.wfile.flush()
                            continue
                        self.sse("event", event)
                except (BrokenPipeError, ConnectionResetError):
                    pass
                finally:
                    APP.unsubscribe(thread_id, listener)
                return None
        raise ApiError(404, "Unknown endpoint")

    def sse(self, event_type, data):
        wire = f"event: {event_type}\ndata: {json.dumps(data, ensure_ascii=False)}\n\n".encode()
        self.wfile.write(wire)
        self.wfile.flush()

    def do_GET(self):
        self.dispatch("GET")

    def do_POST(self):
        self.dispatch("POST")

    def dispatch(self, method):
        try:
            result = self.route(method)
            if result is not None:
                self.send_json(*result)
        except ApiError as error:
            self.send_json(error.status, {"error": error.message})
        except Exception as error:
            print("Request failed", file=sys.stderr)
            self.send_json(500, {"error": "Internal server error"})


def stop_server(*_):
    raise KeyboardInterrupt


def main():
    global APP, ACCESS_TOKEN
    try:
        ACCESS_TOKEN = load_access_token()
    except ValueError as error:
        raise SystemExit(str(error)) from None
    # systemd SIGTERM follows the same child cleanup path as an interactive stop.
    signal.signal(signal.SIGTERM, stop_server)
    version = subprocess.check_output(["codex", "--version"], text=True).strip()
    print(json.dumps({"codex": version, "os": platform.platform(),
                      "runtime": sys.version.split()[0], "transport": "stdio",
                      "workspaceRoot": str(ROOT), "bind": f"127.0.0.1:{PORT}"}), flush=True)
    APP = AppServer()
    httpd = None
    try:
        httpd = ThreadingHTTPServer(("127.0.0.1", PORT), Handler)
        httpd.serve_forever()
    except KeyboardInterrupt:
        pass
    finally:
        if httpd is not None:
            httpd.server_close()
        APP.proc.terminate()
        try:
            APP.proc.wait(timeout=5)
        except subprocess.TimeoutExpired:
            APP.proc.kill()
            APP.proc.wait()


if __name__ == "__main__":
    main()

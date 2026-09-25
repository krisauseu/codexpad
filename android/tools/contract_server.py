#!/usr/bin/env python3
"""Real CodexPad HTTP handlers + deterministic in-memory backend. No Codex/model/VPS access."""
import copy
import importlib.util
import json
import queue
import sys
import tempfile
import threading
import time
from pathlib import Path
from types import SimpleNamespace

spec = importlib.util.spec_from_file_location("codexpad_server", Path(__file__).resolve().parents[2] / "server/codexpad_server.py")
server = importlib.util.module_from_spec(spec)
spec.loader.exec_module(server)


class FixtureBackend:
    def __init__(self, root):
        self.proc = SimpleNamespace(poll=lambda: None)
        self.init_result = {"fixture": True, "userAgent": "codexpad-contract-fixture"}
        self.fresh, self.threads, self.subscribers = {}, {}, {}
        self.lock = threading.RLock()
        self.call("thread/start", {"cwd": str(root / "demo space-ä")})

    def subscribe(self, thread_id):
        listener = queue.Queue()
        with self.lock:
            self.subscribers.setdefault(thread_id, set()).add(listener)
        return listener

    def unsubscribe(self, thread_id, listener):
        with self.lock:
            self.subscribers.get(thread_id, set()).discard(listener)

    def emit(self, thread_id, method, params):
        with self.lock:
            for listener in self.subscribers.get(thread_id, ()):
                listener.put({"method": method, "params": {"threadId": thread_id, **copy.deepcopy(params)}})

    def call(self, method, params, timeout=60):
        with self.lock:
            if method == "model/list":
                return {"data": [{"id": "fixture-catalog", "model": "fixture-model", "displayName": "Fixture",
                                  "description": "Contract model", "isDefault": True,
                                  "supportedReasoningEfforts": [{"reasoningEffort": "custom", "description": "Custom"}],
                                  "defaultReasoningEffort": "custom"}], "nextCursor": None}
            if method == "thread/list":
                return {"data": copy.deepcopy([t for t in self.threads.values() if t["cwd"] == params["cwd"]]), "nextCursor": None}
            if method == "thread/start":
                tid = f"fixture-thread-{len(self.threads) + 1}"
                thread = {"id": tid, "cwd": params["cwd"], "preview": "", "status": {"type": "idle"}, "turns": []}
                self.threads[tid] = thread
                return {"thread": copy.deepcopy(thread)}
            thread = self.threads[params["threadId"]]
            if method in ("thread/read", "thread/resume"):
                if method == "thread/resume" and thread["turns"]:
                    self.emit(thread["id"], "thread/tokenUsage/updated", {
                        "turnId": thread["turns"][-1]["id"], "tokenUsage": {
                            "last": {"totalTokens": 30}, "total": {"totalTokens": 900}, "modelContextWindow": 100}})
                result = copy.deepcopy(thread)
                if method == "thread/read" and not params.get("includeTurns"):
                    result["turns"] = []
                return {"thread": result}
            if method == "turn/interrupt":
                turn = next((t for t in thread["turns"] if t["id"] == params["turnId"]), None)
                if not turn or turn["status"] != "inProgress":
                    raise server.ApiError(502, "Turn is not active")
                turn["status"] = "interrupted"
                thread["status"] = {"type": "idle"}
                self.emit(thread["id"], "turn/completed", {"turn": turn})
                return {}
            if method == "turn/start":
                if "model" in params: thread["model"] = params["model"]
                if "effort" in params: thread["reasoningEffort"] = params["effort"]
                text = params["input"][0]["text"]
                turn = {"id": f"turn-{len(thread['turns']) + 1}", "status": "inProgress", "items": [
                    {"id": "legacy-user", "type": "userMessage", "content": [{"type": "text", "text": text}]}]}
                thread["preview"] = text
                thread["status"] = {"type": "active"}
                thread["turns"].append(turn)
                threading.Thread(target=self.complete, args=(thread, turn), daemon=True).start()
                return {"turn": copy.deepcopy(turn)}
            raise AssertionError(method)

    def complete(self, thread, turn):
        tid = thread["id"]
        time.sleep(.2)
        with self.lock:
            if turn["status"] != "inProgress":
                return
            self.emit(tid, "turn/started", {"turn": turn})
        self.emit(tid, "item/started", {"turnId": turn["id"], "item": turn["items"][0]})
        for part in ("Hallo vom ", "lokalen Vertragstest. ", "CODEXPAD-CLIENT-OK"):
            if turn["status"] != "inProgress":
                return
            self.emit(tid, "item/agentMessage/delta", {"turnId": turn["id"], "itemId": "live-agent", "delta": part})
            time.sleep(1)
        with self.lock:
            if turn["status"] != "inProgress":
                return
            turn["items"].append({"id": "legacy-agent", "type": "agentMessage", "text": "Hallo vom lokalen Vertragstest. CODEXPAD-CLIENT-OK"})
            turn["status"] = "completed"
            thread["status"] = {"type": "idle"}
        self.emit(tid, "turn/completed", {"turn": turn})


def main():
    server.ACCESS_TOKEN = server.load_access_token()
    with tempfile.TemporaryDirectory(prefix="codexpad-client-fixture-") as directory:
        server.ROOT = Path(directory).resolve()
        (server.ROOT / "demo space-ä").mkdir()
        server.APP = FixtureBackend(server.ROOT)
        port = int(sys.argv[1]) if len(sys.argv) > 1 else 18765
        httpd = server.ThreadingHTTPServer(("127.0.0.1", port), server.Handler)
        print(json.dumps({"fixture": True, "url": f"http://127.0.0.1:{httpd.server_port}"}), flush=True)
        try:
            httpd.serve_forever()
        except KeyboardInterrupt:
            pass
        finally:
            httpd.server_close()


if __name__ == "__main__":
    main()

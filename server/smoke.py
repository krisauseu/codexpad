#!/usr/bin/env python3
"""Exercise the local HTTP API, stream disconnect, and authoritative reconnect."""

import os
import json
import sys
import threading
import time
import urllib.request


TOKEN = os.environ["CODEXPAD_ACCESS_TOKEN"]
BASE = sys.argv[1] if len(sys.argv) > 1 else "http://127.0.0.1:8765"


def request(path, body=None):
    payload = None if body is None else json.dumps(body).encode()
    req = urllib.request.Request(BASE + path, data=payload,
                                 headers={"Content-Type": "application/json", "Authorization": "Bearer " + TOKEN})
    with urllib.request.urlopen(req, timeout=75) as response:
        return json.load(response)


def stream(thread_id, output, ready, stop, disconnect_after_event=False):
    with urllib.request.urlopen(urllib.request.Request(BASE + f"/threads/{thread_id}/events",
            headers={"Authorization": "Bearer " + TOKEN}), timeout=90) as response:
        event_type, data = None, None
        while not stop.is_set():
            line = response.readline().decode().strip()
            if line.startswith("event: "):
                event_type = line[7:]
            elif line.startswith("data: "):
                data = json.loads(line[6:])
            elif not line and event_type:
                output.append((event_type, data))
                ready.set()
                if event_type == "event":
                    method = data.get("method")
                    if method != "item/agentMessage/delta":
                        print("LIVE", method, flush=True)
                    if disconnect_after_event and method == "item/agentMessage/delta":
                        return
                event_type, data = None, None


def main():
    print("HEALTH", request("/health"))
    ws = request("/workspaces")["workspaces"]
    assert ws, "No configured workspace"
    workspace_id = ws[0]["id"]
    print("WORKSPACE", workspace_id)
    print("THREADS BEFORE", len(request(f"/workspaces/{workspace_id}/threads")["threads"]))
    created = request(f"/workspaces/{workspace_id}/threads", {})["thread"]
    thread_id = created["id"]
    print("THREAD", thread_id)
    assert request(f"/threads/{thread_id}")["thread"]["id"] == thread_id
    first, ready, stop = [], threading.Event(), threading.Event()
    worker = threading.Thread(target=stream, args=(thread_id, first, ready, stop, True), daemon=True)
    worker.start()
    assert ready.wait(30), "No initial SSE snapshot"
    assert first[0][0] == "snapshot"
    result = request(f"/threads/{thread_id}/turns", {
        "message": "Antworte auf Deutsch. Gib die Zahlen 1 bis 80, jeweils auf einer eigenen Zeile, und danach exakt den Text CODEXPAD-RECONNECT-OK aus. Keine Werkzeuge verwenden."
    })
    turn_id = result["turn"]["id"]
    print("TURN", turn_id)
    deadline = time.time() + 75
    while time.time() < deadline and worker.is_alive():
        time.sleep(.05)
    assert any(t == "event" and data.get("method") == "item/agentMessage/delta"
               for t, data in first), "No live text delta arrived"
    worker.join(10)
    assert not worker.is_alive(), "First client did not disconnect"
    print("DISCONNECTED after", len(first), "SSE messages")
    # A new HTTP stream stands for a new tablet connection. The first event is a fresh snapshot.
    second, second_ready, second_stop = [], threading.Event(), threading.Event()
    second_worker = threading.Thread(target=stream, args=(thread_id, second, second_ready, second_stop), daemon=True)
    second_worker.start()
    assert second_ready.wait(30), "No snapshot after reconnect"
    assert second[0][0] == "snapshot" and second[0][1]["thread"]["id"] == thread_id
    print("RECONNECTED snapshot status", second[0][1]["thread"].get("status"))
    while time.time() < deadline:
        history = request(f"/threads/{thread_id}/history")["thread"]
        turns = history.get("turns", [])
        matching = [turn for turn in turns if turn.get("id") == turn_id]
        if matching and matching[0].get("status") not in ("inProgress", "pending"):
            final = matching[0]
            break
        time.sleep(.5)
    else:
        raise AssertionError("Turn did not finish in time")
    second_stop.set()
    text = "\n".join(item.get("text", "") for item in final.get("items", []) if item.get("type") == "agentMessage")
    print("FINAL STATUS", final.get("status"))
    print("FINAL TEXT", text[-500:])
    assert final["status"] == "completed", final
    assert "CODEXPAD-RECONNECT-OK" in text, "Final answer missing from authoritative history"
    assert request(f"/threads/{thread_id}")["thread"]["id"] == thread_id
    assert any(t["id"] == thread_id for t in request(f"/workspaces/{workspace_id}/threads")["threads"])
    print("PASS: stream, disconnect, reconnect, complete history", flush=True)


if __name__ == "__main__":
    main()

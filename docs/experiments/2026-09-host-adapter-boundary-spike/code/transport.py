"""Private stdio harness. No remote transport, no credential loading."""
import json, os, queue, subprocess, threading, time
from pathlib import Path
BASE = Path("/tmp/codexpad-boundary-20260922")
RUNTIME = BASE / "runtime"
POLICY = dict(type="workspaceWrite", writableRoots=[], networkAccess=False,
              excludeTmpdirEnvVar=True, excludeSlashTmp=True)

class RPC:
    def __init__(self):
        assert os.getuid() != 0
        self.n = 0
        self.messages = []
        self.sent = []
        self.q = queue.Queue()
        self.err = open(RUNTIME / "server-stderr.txt", "a")
        self.p = subprocess.Popen([str(BASE/"bin/codex"), "app-server", "--listen", "stdio://"],
             cwd=BASE/"workspace-a", stdin=subprocess.PIPE, stdout=subprocess.PIPE,
             stderr=self.err, text=True, bufsize=1)
        threading.Thread(target=self._read, daemon=True).start()
        self.call("initialize", {"clientInfo":{"name":"boundary_spike","version":"0.1"},
                  "capabilities":{"experimentalApi":True}})
        self.send({"method":"initialized"})
    def _read(self):
        for line in self.p.stdout:
            try:
                self.q.put(json.loads(line))
            except ValueError:
                self.q.put({"parseError":True})
        self.q.put({"eof":True})
    def send(self, msg):
        self.sent.append(msg)
        self.p.stdin.write(json.dumps(msg)+"\n")
        self.p.stdin.flush()
    def call(self, method, params, timeout=12, request_id=None):
        self.n += 1
        rid = self.n if request_id is None else request_id
        self.send({"id":rid,"method":method,"params":params})
        end = time.monotonic()+timeout
        while time.monotonic() < end:
            m = self.q.get(timeout=max(.01,end-time.monotonic()))
            self.messages.append(m)
            if m.get("id") == rid and "method" not in m:
                if "error" in m:
                    raise RuntimeError(json.dumps(m["error"]))
                return m["result"]
            if m.get("eof"):
                raise EOFError("backend disconnected")
        raise TimeoutError(method)
    def drain(self, seconds=.1):
        end=time.monotonic()+seconds
        while time.monotonic()<end:
            try: self.messages.append(self.q.get(timeout=max(.001,end-time.monotonic())))
            except queue.Empty: break
    def close(self):
        if self.p.poll() is None:
            self.p.terminate()
            try: self.p.wait(timeout=3)
            except subprocess.TimeoutExpired:
                self.p.kill()
                self.p.wait(timeout=3)
        self.err.close()

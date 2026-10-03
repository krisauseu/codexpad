"""Explicit VPS before/after verification; no token output or workspace mutations.

Run on the dedicated server as root with `before` prior to the update, then `after`.
Snapshots are root-only /tmp files; normal production service never imports this module.
"""
import hashlib
import json
import os
from pathlib import Path
import stat
import sys
from urllib.parse import quote
from urllib.request import Request, urlopen

SNAPSHOT = Path("/tmp/codexpad-workspaces-20261003-before.json")
ROOT = Path("/srv/codexpad/workspaces")


def main():
    settings = dict(line.split("=", 1) for line in Path("/etc/codexpad/server.env").read_text().splitlines()
                    if "=" in line and not line.startswith("#"))
    token = settings["CODEXPAD_ACCESS_TOKEN"]

    def get(path):
        with urlopen(Request("http://127.0.0.1:8765" + path,
                headers={"Authorization": "Bearer " + token}), timeout=150) as response:
            return json.load(response)

    manifest = {}
    for ws in sorted(ROOT.iterdir()):
        if not ws.is_dir() or ws.is_symlink():
            continue
        entries = []
        for directory, folders, files in os.walk(ws, followlinks=False):
            for name in sorted(folders + files):
                path = Path(directory) / name
                info = path.lstat()
                content = os.readlink(path) if path.is_symlink() else (
                    hashlib.sha256(path.read_bytes()).hexdigest() if path.is_file() else None)
                entries.append([str(path.relative_to(ws)), stat.S_IMODE(info.st_mode), content])
        manifest[ws.name] = sorted(entries)
    api_workspaces = get("/workspaces")["workspaces"]
    threads, active = {}, []
    for ws in api_workspaces:
        entries = get("/workspaces/" + quote(ws["id"], safe="") + "/threads")["threads"]
        threads[ws["id"]] = sorted(t["id"] for t in entries)
        active += [t["id"] for t in entries if (t.get("status") or {}).get("type") == "active"]
    if sys.argv[1] == "before":
        if active:
            raise SystemExit("Active turns; do not restart the server: " + str(len(active)))
        fd = os.open(SNAPSHOT, os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600)
        with os.fdopen(fd, "w") as output:
            json.dump({"manifest": manifest, "threads": threads}, output)
        print("BEFORE: no active turns; file hashes captured for", sorted(manifest))
        print("Thread counts:", {name: len(ids) for name, ids in threads.items()})
    else:
        baseline = json.loads(SNAPSHOT.read_text())
        if manifest != baseline["manifest"]:
            raise SystemExit("Workspace file manifest differs from baseline")
        if set(threads) != set(baseline["threads"]):
            raise SystemExit("Workspace catalog differs from baseline")
        additional = 0
        for ws, ids in baseline["threads"].items():
            if not set(ids).issubset(threads[ws]):
                raise SystemExit("A previous thread is missing from its workspace")
            for tid in threads[ws]:
                current = get("/threads/" + quote(tid, safe="") + "/history")["thread"]
                assert current["cwd"] == str(ROOT / ws)
                if tid not in ids:
                    # Previously hidden empty sessions may now be visible, but
                    # this verification must never mistake newly created threads
                    # in existing workspaces for preserved previous sessions.
                    assert current["createdAt"] <= SNAPSHOT.stat().st_mtime
                    assert not current.get("turns")
                    additional += 1
        print("AFTER: all workspace file SHA-256 hashes, modes, names and thread assignments unchanged")
        print("Workspaces:", sorted(manifest))
        print("Thread counts:", {name: len(ids) for name, ids in threads.items()})
        print("Previously hidden persisted empty sessions now visible:", additional)


if __name__ == "__main__":
    main()

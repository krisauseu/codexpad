"""Production HTTP routes, real temporary directories, deterministic Codex backend."""
import json
import os
from pathlib import Path
import tempfile
import threading
import unittest
from urllib.parse import quote
from unittest.mock import patch
import test_auth

server = test_auth.server


class WorkspaceTest(unittest.TestCase):
    setUp = test_auth.AuthenticationTest.setUp
    tearDown = test_auth.AuthenticationTest.tearDown
    request = test_auth.AuthenticationTest.request

    def api(self, path, payload=None):
        status, _, body = self.request(path, "GET" if payload is None else "POST",
            {"Authorization": "Bearer " + self.token, "Content-Type": "application/json", "Connection": "close"},
            None if payload is None else json.dumps(payload))
        return status, json.loads(body)

    def route(self, name, action):
        return "/workspaces/" + quote(name, safe="") + "/" + action

    def create(self, name="temporary"):
        return self.api("/workspaces", {"name": name})

    def test_create_trim_list_no_template_and_duplicate(self):
        self.assertEqual(201, self.create("  Grüße 1  ")[0])
        path = server.ROOT / "Grüße 1"
        self.assertEqual([], list(path.iterdir()))
        self.assertEqual("Grüße 1", self.api("/workspaces")[1]["workspaces"][0]["id"])
        (path / "AGENTS.md").write_text("keep")
        status, error = self.create("Grüße 1")
        self.assertEqual((409, "workspace_exists"), (status, error["code"]))
        self.assertEqual("keep", (path / "AGENTS.md").read_text())

    def test_invalid_names_and_traversal_for_every_mutation(self):
        self.create()
        bad = ["", " ", ".", "..", "../escape", "/tmp/escape", "a/b", "a\\b", "a..b",
               "a\x00b", "a\nb", "x" * 81, "a;touch x", "$(id)", "a%2fb", "a.", "_hidden", None, 3]
        for name in bad:
            with self.subTest(name=name):
                self.assertEqual(400, self.create(name)[0])
                self.assertEqual(400, self.api(self.route("temporary", "rename"), {"name": name})[0])
                if isinstance(name, str) and name:
                    self.assertEqual(400, self.api(self.route(name, "delete"),
                        {"confirmation": name, "inspection": "bad"})[0])
        self.assertTrue((server.ROOT / "temporary").is_dir())
        self.assertEqual(400, self.api("/workspaces", {"name": "a", "cwd": "/tmp"})[0])

    def test_rename_preserves_files_agents_and_nested_directory(self):
        self.create()
        path = server.ROOT / "temporary"
        (path / "AGENTS.md").write_text("original instructions")
        (path / "nested").mkdir()
        (path / "nested/file.txt").write_text("original data")
        self.assertEqual(200, self.api(self.route("temporary", "rename"), {"name": " renamed "})[0])
        self.assertFalse(path.exists())
        self.assertEqual("original instructions", (server.ROOT / "renamed/AGENTS.md").read_text())
        self.assertEqual("original data", (server.ROOT / "renamed/nested/file.txt").read_text())
        self.assertNotIn("temporary", [w["id"] for w in self.api("/workspaces")[1]["workspaces"]])

    def test_rename_conflicting_directory_file_and_symlink(self):
        self.create()
        for kind in ("directory", "file", "symlink"):
            target = server.ROOT / kind
            if kind == "directory": target.mkdir()
            elif kind == "file": target.write_text("keep")
            else: target.symlink_to(server.ROOT.parent)
            self.assertEqual(409, self.api(self.route("temporary", "rename"), {"name": kind})[0])
            self.assertTrue(os.path.lexists(target))
        self.assertTrue((server.ROOT / "temporary").is_dir())
        # Also exercise the atomic no-replace primitive independent of preflight.
        with server.workspace_root() as fd:
            with self.assertRaises(FileExistsError):
                server.rename_workspace(fd, "temporary", "directory")

    def test_delete_inventory_confirmation_and_symlink_boundary(self):
        self.create()
        path = server.ROOT / "temporary"
        outside = server.ROOT / "outside.txt"
        outside.write_text("keep")
        (path / "nested").mkdir()
        (path / "nested/AGENTS.md").write_text("test")
        (path / "escape").symlink_to(server.ROOT.parent, target_is_directory=True)
        status, inventory = self.api(self.route("temporary", "inspection"))
        self.assertEqual((200, 2, 1, 0), (status, inventory["files"], inventory["directories"], inventory["threads"]))
        payload = {"confirmation": "wrong", "inspection": inventory["inspection"]}
        self.assertEqual(400, self.api(self.route("temporary", "delete"), payload)[0])
        payload["confirmation"] = "temporary"
        self.assertEqual(200, self.api(self.route("temporary", "delete"), payload)[0])
        self.assertFalse(path.exists())
        self.assertEqual("keep", outside.read_text())
        self.assertTrue((server.ROOT / "demo space-ä").is_dir())

    def test_delete_changed_contents_requires_new_confirmation(self):
        self.create()
        inventory = self.api(self.route("temporary", "inspection"))[1]
        (server.ROOT / "temporary/new.txt").write_text("new")
        status, error = self.api(self.route("temporary", "delete"),
            {"confirmation": "temporary", "inspection": inventory["inspection"]})
        self.assertEqual((409, "workspace_changed"), (status, error["code"]))
        self.assertTrue((server.ROOT / "temporary/new.txt").exists())

    def test_thread_guards_live_archived_nested_and_fresh(self):
        self.create()
        for kind in ("live", "archived", "nested", "fresh"):
            with self.subTest(kind=kind):
                entry = {"id": "guard", "cwd": str(server.ROOT / "temporary") + ("/nested" if kind == "nested" else ""),
                         "archived": kind == "archived"}
                target = server.APP.fresh if kind == "fresh" else server.APP.threads
                target["guard"] = entry
                inventory = self.api(self.route("temporary", "inspection"))[1]
                self.assertEqual(1, inventory["threads"])
                for action, payload in [("rename", {"name": "other"}),
                        ("delete", {"confirmation": "temporary", "inspection": inventory["inspection"]})]:
                    status, error = self.api(self.route("temporary", action), payload)
                    self.assertEqual((409, "workspace_has_threads"), (status, error["code"]))
                self.assertTrue((server.ROOT / "temporary").exists())
                self.assertEqual(entry, target.pop("guard"))

    def test_empty_persisted_sessions_are_visible_and_block_mutations(self):
        self.create()
        entry = server.APP.call("thread/start", {"cwd": str(server.ROOT / "temporary")})["thread"]
        original = server.APP.call
        def rpc(method, params, **kwargs):
            return {"data": [], "nextCursor": None} if method == "thread/list" else original(method, params, **kwargs)
        metadata = {"id": entry["id"], "cwd": entry["cwd"], "archived": False, "source": "vscode"}
        with patch.object(server.APP, "call", side_effect=rpc), patch.object(server.APP, "session_threads", return_value=[metadata]):
            threads = self.api(self.route("temporary", "threads"))[1]["threads"]
            self.assertEqual([entry["id"]], [t["id"] for t in threads])
            inventory = self.api(self.route("temporary", "inspection"))[1]
            self.assertEqual(1, inventory["threads"])
            self.assertEqual(409, self.api(self.route("temporary", "rename"), {"name": "new"})[0])
            self.assertEqual(409, self.api(self.route("temporary", "delete"),
                {"confirmation": "temporary", "inspection": inventory["inspection"]})[0])
        # Archived empty sessions remain protected without appearing in the UI list.
        metadata["archived"] = True
        with patch.object(server.APP, "call", side_effect=rpc), patch.object(server.APP, "session_threads", return_value=[metadata]):
            self.assertEqual([], self.api(self.route("temporary", "threads"))[1]["threads"])
            self.assertEqual(1, self.api(self.route("temporary", "inspection"))[1]["threads"])
        metadata.update(archived=False, source={"subagent": "other"})
        with patch.object(server.APP, "call", side_effect=rpc), patch.object(server.APP, "session_threads", return_value=[metadata]):
            self.assertEqual([], self.api(self.route("temporary", "threads"))[1]["threads"])
            self.assertEqual(1, self.api(self.route("temporary", "inspection"))[1]["threads"])

    def test_session_header_inventory_is_read_only_and_fails_closed(self):
        adapter = server.AppServer.__new__(server.AppServer)
        with tempfile.TemporaryDirectory() as directory:
            adapter.codex_home = Path(directory)
            for folder, archived in (("sessions/2026/10/03", False), ("archived_sessions", True)):
                root = adapter.codex_home / folder
                root.mkdir(parents=True)
                header = {"type": "session_meta", "payload": {"id": folder, "cwd": str(server.ROOT / "temporary")}}
                path = root / "rollout.jsonl"
                data = json.dumps(header).encode() + b'\n{"large":"history never parsed"}\n'
                path.write_bytes(data)
                self.assertEqual(data, path.read_bytes())
            entries = adapter.session_threads()
            self.assertEqual({False, True}, {t["archived"] for t in entries})
            self.assertEqual(2, len(entries))
            bad = adapter.codex_home / "sessions/bad.jsonl"
            bad.write_text("invalid")
            with self.assertRaises(server.ApiError): adapter.session_threads()
            bad.unlink()
            bad.symlink_to(adapter.codex_home / "archived_sessions/rollout.jsonl")
            with self.assertRaises(server.ApiError): adapter.session_threads()

    def test_orphan_target_threads_prevent_name_reuse(self):
        server.APP.threads["orphan"] = {"id": "orphan", "cwd": str(server.ROOT / "old")}
        self.assertEqual(409, self.create("old")[0])
        self.create()
        self.assertEqual(409, self.api(self.route("temporary", "rename"), {"name": "old"})[0])

    def test_thread_created_after_inspection_blocks_delete(self):
        self.create()
        inventory = self.api(self.route("temporary", "inspection"))[1]
        self.assertEqual(201, self.api(self.route("temporary", "threads"), {})[0])
        self.assertEqual(409, self.api(self.route("temporary", "delete"),
            {"confirmation": "temporary", "inspection": inventory["inspection"]})[0])

    def test_threads_through_symlinks_and_external_aliases_are_guarded(self):
        self.create()
        (server.ROOT / "temporary/link").symlink_to(server.ROOT.parent, target_is_directory=True)
        (server.ROOT / "alias").symlink_to(server.ROOT / "temporary", target_is_directory=True)
        for cwd in (server.ROOT / "temporary/link", server.ROOT / "alias"):
            server.APP.threads["guard"] = {"id": "guard", "cwd": str(cwd)}
            self.assertEqual(1, self.api(self.route("temporary", "inspection"))[1]["threads"])
            self.assertEqual(409, self.api(self.route("temporary", "rename"), {"name": "new"})[0])
            server.APP.threads.pop("guard")

    def test_symlink_workspace_is_never_followed(self):
        (server.ROOT / "link").symlink_to(server.ROOT / "demo space-ä", target_is_directory=True)
        self.assertEqual(409, self.create("link")[0])
        self.assertEqual(409, self.api(self.route("link", "rename"), {"name": "renamed"})[0])
        self.assertEqual(409, self.api(self.route("link", "delete"), {"confirmation": "link", "inspection": "bad"})[0])
        self.assertTrue((server.ROOT / "demo space-ä").is_dir())

    def test_backend_errors_fail_closed_and_pagination_uses_all_sources(self):
        self.create()
        original = server.APP.call
        calls = []
        def rpc(method, params, **kwargs):
            calls.append(params)
            if method == "thread/list":
                if params["archived"] and params["cursor"] == "next":
                    return {"data": [{"id": "a", "cwd": str(server.ROOT / "temporary")}], "nextCursor": None}
                return {"data": [], "nextCursor": "next" if params["archived"] else None}
            return original(method, params, **kwargs)
        with patch.object(server.APP, "call", side_effect=rpc):
            self.assertEqual(409, self.api(self.route("temporary", "rename"), {"name": "other"})[0])
        self.assertEqual([False, True, True], [c["archived"] for c in calls])
        self.assertTrue(all(c["sourceKinds"] == server.THREAD_SOURCES and "cwd" not in c for c in calls))
        for failure in (server.ApiError(502, "offline"),):
            with patch.object(server.APP, "call", side_effect=failure):
                self.assertEqual(502, self.api(self.route("temporary", "rename"), {"name": "other"})[0])
                self.assertEqual(502, self.create("new")[0])
            self.assertTrue((server.ROOT / "temporary").is_dir())
        with patch.object(server.APP, "call", return_value={"data": [], "nextCursor": "loop"}):
            self.assertEqual(502, self.api(self.route("temporary", "inspection"))[0])

    def test_concurrent_duplicate_creation_and_authentication(self):
        results = []
        workers = [threading.Thread(target=lambda: results.append(self.create()[0])) for _ in range(2)]
        for worker in workers: worker.start()
        for worker in workers: worker.join()
        self.assertEqual([201, 409], sorted(results))
        for route in ("/workspaces", self.route("temporary", "rename"), self.route("temporary", "delete")):
            self.assertEqual(401, self.request(route, "POST", body='{}')[0])
        self.assertEqual(401, self.request(self.route("temporary", "inspection"))[0])


if __name__ == "__main__":
    unittest.main()

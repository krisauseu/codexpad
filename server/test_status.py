"""Status RPC and global notifications without a Codex process or model calls."""
import json
import queue
import threading
import unittest
from unittest.mock import patch
import test_auth

server = test_auth.server


class StatusTest(unittest.TestCase):
    setUp = test_auth.AuthenticationTest.setUp
    tearDown = test_auth.AuthenticationTest.tearDown
    request = test_auth.AuthenticationTest.request

    def test_limits_endpoint_authenticates_and_only_reads_account_limits(self):
        limits = {"rateLimits": None, "rateLimitsByLimitId": {"codex": {
            "secondary": {"usedPercent": 6, "windowDurationMins": 10080, "resetsAt": 2000}}}}
        with patch.object(server.APP, "call", return_value=limits) as rpc:
            self.assertEqual(401, self.request("/account/rate-limits")[0])
            rpc.assert_not_called()
            status, _, body = self.request("/account/rate-limits", headers={"Authorization": "Bearer " + self.token})
            self.assertEqual(200, status)
            self.assertEqual(limits, json.loads(body))
            rpc.assert_called_once_with("account/rateLimits/read", {})

    def test_account_events_reach_all_sessions_and_overflow_forces_reconciliation(self):
        app = server.AppServer.__new__(server.AppServer)
        app.lock = threading.Lock()
        first, second = queue.Queue(maxsize=1), queue.Queue(maxsize=1)
        app.subscribers = {"a": {first}, "b": {second}}
        event = {"method": "account/rateLimits/updated", "params": {"rateLimits": {"limitId": "codex"}}}
        app.publish(event)
        self.assertEqual(event, first.get_nowait())
        self.assertEqual(event, second.get_nowait())
        app.publish({"method": "thread/tokenUsage/updated", "params": {"threadId": "a"}})
        self.assertTrue(second.empty())
        app.publish(event)
        self.assertEqual("codexpad/overflow", first.get_nowait()["method"])
        self.assertEqual(event, second.get_nowait())

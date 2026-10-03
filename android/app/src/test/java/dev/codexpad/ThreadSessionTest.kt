package dev.codexpad

import dev.codexpad.data.ThreadSession
import dev.codexpad.model.*
import dev.codexpad.network.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class ThreadSessionTest {
    private class FakeServer : CodexPadService {
        val calls = mutableListOf<String>()
        var toolMode = false
        fun toolJson() = org.json.JSONObject().put("id", "cmd").put("type", "commandExecution")
            .put("command", "pwd").put("status", if (completed) "completed" else "inProgress")
            .put("aggregatedOutput", if (completed) "final output" else "snapshot output")
        var completed = false
        var terminalStatus = "completed"
        var interruptCalls = 0
        var interruptAction: suspend () -> Unit = {}
        override suspend fun interruptTurn(threadId: String, turnId: String) {
            assertEquals("t", threadId)
            assertEquals("turn", turnId)
            interruptCalls++
            interruptAction()
        }
        var usageReplay = false
        var limit: WeeklyLimit = WeeklyLimit()
        var limitsFail = false
        var limitPush = false
        var limitAction: suspend () -> Unit = {}
        override suspend fun rateLimits(): WeeklyLimit {
            limitAction()
            if (limitsFail) throw IOException("Limits unavailable")
            return limit
        }
        var disconnect = false
        var connections = 0
        var posts = 0
        override suspend fun compactThread(threadId: String) { error("Unexpected compact") }
        var pendingRequest = false
        var answerCalls = 0
        var answerAction: suspend () -> Unit = {}
        fun requestJson() = org.json.JSONObject("""{"id":"r","threadId":"t","turnId":"turn","status":"pending","isBlocking":false,"questions":[{"id":"name","header":"Name","question":"Filename?","isOther":true,"isSecret":false,"options":[{"label":"a","description":"A"}]}]}""")
        override suspend fun answerRequest(threadId: String, requestId: String, answers: Map<String, InputAnswer>) {
            answerCalls++
            answerAction()
        }
        override suspend fun models() = emptyList<CatalogModel>()
        override suspend fun health() = "ok"
        override suspend fun workspaces() = emptyList<Workspace>()
        override suspend fun threads(workspaceId: String) = emptyList<CodexThread>()
        override suspend fun createThread(workspaceId: String): CodexThread = error("Unexpected POST")
        override suspend fun startTurn(threadId: String, message: String, model: String?, effort: String?): Turn { posts++; error("Unexpected POST") }
        fun snapshot() = CodexThread("t", status = if (completed) "idle" else "active", turns = listOf(
            Turn("turn", if (completed) terminalStatus else "inProgress",
                listOf(Message("legacy-2", "agentMessage", if (completed) "Complete answer" else "Prefix")) +
                    (if (toolMode) listOf(Wire.message(toolJson())) else emptyList()), startedAt = 1000)), pendingRequests = if (pendingRequest) Wire.thread(org.json.JSONObject()
                    .put("id", "t").put("pendingRequests", org.json.JSONArray().put(requestJson()))).pendingRequests else emptyList())
        override suspend fun thread(threadId: String): CodexThread { calls += "snapshot"; return snapshot() }
        override suspend fun history(threadId: String): CodexThread { calls += "history"; return snapshot() }
        override fun events(threadId: String) = flow {
            calls += "events"
            connections++
            val status = if (completed) terminalStatus else "inProgress"
            val text = if (completed) "Complete answer" else "Prefix"
            val snapshotJson = org.json.JSONObject("""{"thread":{"id":"t","model":"configured","reasoningEffort":"custom","status":{"type":"idle"},"turns":[
                {"id":"turn","status":"$status","startedAt":1000,"items":[{"id":"legacy-2","type":"agentMessage","text":"$text"}]}]}}""")
            if (pendingRequest) snapshotJson.getJSONObject("thread").put("pendingRequests", org.json.JSONArray().put(requestJson()))
            if (toolMode) snapshotJson.getJSONObject("thread").getJSONArray("turns").getJSONObject(0)
                .getJSONArray("items").put(toolJson())
            emit(SseFrame("snapshot", snapshotJson.toString()))
            if (limitPush) {
                emit(SseFrame("event", """{"method":"account/rateLimits/updated","params":{"rateLimits":{"limitId":"codex","secondary":{"usedPercent":6,"windowDurationMins":10080}}}}"""))
                emit(SseFrame("event", """{"method":"account/rateLimits/updated","params":{"rateLimits":{"limitId":"other","secondary":{"usedPercent":90,"windowDurationMins":10080}}}}"""))
            }
            if (toolMode) emit(SseFrame("event", """{"method":"item/commandExecution/outputDelta","params":{"threadId":"t","turnId":"turn","itemId":"cmd","delta":"slice $connections"}}"""))
            if (usageReplay) {
                emit(SseFrame("event", """{"method":"thread/tokenUsage/updated","params":{"threadId":"t","tokenUsage":{"last":{"totalTokens":30},"total":{"totalTokens":900},"modelContextWindow":100}}}"""))
                emit(SseFrame("event", """{"method":"model/rerouted","params":{"threadId":"t","turnId":"turn","fromModel":"configured","toModel":"runtime"}}"""))
            }
            emit(SseFrame("event", """{"method":"item/agentMessage/delta","params":{"threadId":"t","turnId":"turn","itemId":"live-2","delta":"fragment"}}"""))
            if (disconnect && connections == 1) {
                completed = !toolMode // The text turn finishes while the client is offline, without an end event.
                throw IOException("Disconnected")
            }
            awaitCancellation()
        }
    }

    @Test fun inputReconnectLostResponseDoubleTapAndResolution() = runTest {
        val server = FakeServer().apply { pendingRequest = true }
        var saved = emptySet<String>()
        val session = ThreadSession(server, "t", saveAnswers = { saved = it })
        val connection = backgroundScope.launch { session.run() }; runCurrent()
        assertEquals("r", session.state.value.requests.single().id)
        assertTrue(session.state.value.canMessageDuringInput)
        assertFalse(session.state.value.copy(timeline = session.state.value.timeline.copy(thread =
            session.state.value.timeline.thread!!.copy(pendingRequests = session.state.value.requests.map { it.copy(isBlocking = true) }))).canMessageDuringInput)
        connection.cancel(); runCurrent()
        backgroundScope.launch { session.run() }; runCurrent()
        assertEquals("r", session.state.value.requests.single().id)
        server.answerAction = { delay(100); throw IOException("Lost HTTP response") }
        val answer = mapOf("name" to InputAnswer("my-name.txt"))
        val posting = launch { session.answer("r", answer) }; runCurrent()
        session.answer("r", answer)
        assertEquals(setOf("r"), saved)
        advanceTimeBy(100); runCurrent(); posting.join()
        assertEquals(1, server.answerCalls)
        assertTrue(session.state.value.answerErrors.containsKey("r"))
        session.answer("r", answer)
        assertEquals(1, server.answerCalls)
        val restored = ThreadSession(server, "t", pendingAnswers = saved)
        backgroundScope.launch { restored.run() }; runCurrent()
        restored.answer("r", answer)
        assertEquals(1, server.answerCalls)
        server.pendingRequest = false // Another client/server resolves it, live event lost.
        server.completed = true
        advanceTimeBy(5000); runCurrent()
        assertTrue(session.state.value.requests.isEmpty())
        assertTrue(saved.isEmpty())
        assertTrue(session.state.value.timeline.turns.single().terminal)
        assertEquals(1, server.answerCalls)
    }

    @Test fun unansweredRequestRequiresExplicitFreshReviewAfterUncertainty() = runTest {
        val server = FakeServer().apply { pendingRequest = true }
        val session = ThreadSession(server, "t", pendingAnswers = setOf("r"))
        backgroundScope.launch { session.run() }; runCurrent()
        assertTrue("r" in session.state.value.answerAttempts)
        session.reviewAnswer("r")
        assertTrue(session.state.value.answerAttempts.isEmpty())
        session.answer("r", mapOf("name" to InputAnswer("a", true)))
        assertEquals(1, server.answerCalls)
    }

    @Test fun runningCommandReconnectAndMissedCompletionHealFromHistory() = runTest {
        val server = FakeServer().apply { toolMode = true; disconnect = true }
        val session = ThreadSession(server, "t")
        fun command() = session.state.value.timeline.turns.single().items.first { it.id == "cmd" }.activity as Activity.Command
        backgroundScope.launch { session.run() }; runCurrent()
        assertFalse(session.state.value.connected)
        assertEquals("slice 1", command().liveOutput)
        advanceTimeBy(1000); runCurrent()
        assertTrue(session.state.value.connected)
        assertEquals("inProgress", command().status)
        assertEquals("slice 2", command().liveOutput)
        server.completed = true // No item/completed or turn/completed event delivered.
        advanceTimeBy(5000); runCurrent()
        assertEquals("final output", command().output)
        assertNull(command().liveOutput)
        assertTrue(session.state.value.timeline.live.isEmpty())
        assertEquals(0, server.posts)
    }

    @Test fun usageReplayFreshnessAndRerouteNeverChangeConfiguration() = runTest {
        val server = FakeServer().apply { usageReplay = true; disconnect = true }
        val session = ThreadSession(server, "t")
        val job = backgroundScope.launch { session.run() }; runCurrent()
        assertTrue(session.state.value.usage.stale)
        assertEquals(30L, session.state.value.usage.used)
        advanceTimeBy(1000); runCurrent()
        assertFalse(session.state.value.usage.stale)
        assertEquals(0, session.state.value.usage.remainingPercent)
        assertEquals("configured", session.state.value.timeline.thread?.model)
        assertEquals("custom", session.state.value.timeline.thread?.reasoningEffort)
        assertEquals("runtime", session.state.value.reroutes["turn"]?.to)
        job.cancel(); runCurrent()
        server.usageReplay = false
        backgroundScope.launch { session.run() }; runCurrent()
        assertTrue(session.state.value.usage.stale) // History cannot refresh token usage.
    }

    @Test fun missingUsageRemainsUnknown() = runTest {
        val session = ThreadSession(FakeServer(), "t")
        backgroundScope.launch { session.run() }; runCurrent()
        assertNull(session.state.value.usage.used)
        assertNull(session.state.value.usage.remainingPercent)
    }

    @Test fun accountLimitsUpdateAcrossGlobalEventsFailureAndReconnect() = runTest {
        val server = FakeServer().apply { limitPush = true }
        val session = ThreadSession(server, "t")
        val job = backgroundScope.launch { session.run() }; runCurrent()
        assertEquals(94, session.state.value.weekly.remainingPercent)
        server.limitsFail = true
        advanceTimeBy(60000); runCurrent()
        assertTrue(session.state.value.connected)
        assertTrue(session.state.value.weekly.stale)
        assertEquals(94, session.state.value.weekly.remainingPercent)
        job.cancel(); runCurrent()
        server.limitPush = false
        server.limitsFail = false
        server.limit = WeeklyLimit(93)
        backgroundScope.launch { session.run() }; runCurrent()
        assertEquals(93, session.state.value.weekly.remainingPercent)
        assertFalse(session.state.value.weekly.stale)
        assertEquals(0, server.posts)
    }

    @Test fun pushedLimitsWinOverOlderInFlightRead() = runTest {
        val gate = CompletableDeferred<Unit>()
        val server = FakeServer().apply {
            limitPush = true
            limit = WeeklyLimit(99)
            limitAction = { gate.await() }
        }
        val session = ThreadSession(server, "t")
        backgroundScope.launch { session.run() }; runCurrent()
        assertEquals(94, session.state.value.weekly.remainingPercent)
        gate.complete(Unit); runCurrent()
        assertEquals(94, session.state.value.weekly.remainingPercent)
        assertTrue(session.state.value.connected)
    }

    @Test fun interruptWaitsForHistoryAndSuppressesDoubleTap() = runTest {
        val server = FakeServer()
        val saved = mutableListOf<String?>()
        val session = ThreadSession(server, "t", saveInterrupt = { saved += it })
        backgroundScope.launch { session.run() }; runCurrent()
        val reply = CompletableDeferred<Unit>()
        server.interruptAction = { reply.await() }
        val request = launch { session.interrupt("turn") }; runCurrent()
        assertEquals(listOf("turn"), saved)
        assertEquals("turn", session.state.value.interruptTurnId)
        assertNull(session.state.value.stoppableTurnId)
        session.interrupt("turn")
        reply.complete(Unit); request.join()
        assertEquals(1, server.interruptCalls)
        assertEquals("turn", session.state.value.interruptTurnId) // HTTP success is not completion.
        server.completed = true; server.terminalStatus = "interrupted"
        advanceTimeBy(5000); runCurrent()
        assertNull(session.state.value.interruptTurnId)
        assertEquals("interrupted", session.state.value.timeline.turns.single().status)
        assertFalse(session.state.value.statusLabel(1102).contains("1:42"))
        assertNull(session.state.value.runningTurn)
        assertEquals(listOf("turn", null), saved)
    }

    @Test fun completionRaceUsesActualTerminalStatusEvenWhenHttpFails() = runTest {
        for (status in listOf("interrupted", "completed", "failed", "cancelled", "stopped")) {
            val server = FakeServer()
            val session = ThreadSession(server, "t")
            val job = backgroundScope.launch { session.run() }; runCurrent()
            server.interruptAction = {
                server.completed = true; server.terminalStatus = status
                session.refresh() // SSE/history wins before a late HTTP error.
                throw IOException("Lost response")
            }
            session.interrupt("turn")
            assertEquals(status, session.state.value.timeline.turns.single().status)
            assertNull(session.state.value.runningTurn)
            assertNull(session.state.value.interruptTurnId)
            assertNull(session.state.value.interruptError)
            assertEquals(1, server.interruptCalls)
            job.cancel(); runCurrent()
        }
    }

    @Test fun lostResponseSurvivesPauseAndRestorationWithoutRetryOrRetarget() = runTest {
        val server = FakeServer()
        var saved: String? = null
        val session = ThreadSession(server, "t", saveInterrupt = { saved = it })
        val job = backgroundScope.launch { session.run() }; runCurrent()
        server.interruptAction = { throw IOException("Lost response") }
        session.interrupt("turn")
        assertNotNull(session.state.value.interruptError)
        assertEquals("turn", saved)
        session.interrupt("turn")
        session.interrupt("newer")
        job.cancel(); runCurrent()
        val restored = ThreadSession(server, "t", saved, { saved = it })
        assertNull(restored.state.value.stoppableTurnId)
        server.completed = true; server.terminalStatus = "interrupted"
        backgroundScope.launch { restored.run() }; runCurrent()
        assertNull(saved)
        assertEquals("interrupted", restored.state.value.timeline.turns.single().status)
        assertEquals(1, server.interruptCalls)
    }

    @Test fun unresolvedOldInterruptNeverTargetsNewRunningTurn() = runTest {
        val server = FakeServer()
        val session = ThreadSession(server, "t", pendingInterrupt = "old-turn")
        backgroundScope.launch { session.run() }; runCurrent()
        assertEquals("turn", session.state.value.timeline.turns.single().id)
        assertEquals("old-turn", session.state.value.interruptTurnId)
        assertNull(session.state.value.stoppableTurnId)
        session.interrupt("turn")
        assertEquals(0, server.interruptCalls)
    }

    @Test fun staleUiAndAmbiguousOrOfflineStateCannotSelectAnotherTurn() = runTest {
        val server = FakeServer()
        val session = ThreadSession(server, "t")
        session.refresh()
        session.interrupt("turn") // Offline history is insufficient.
        backgroundScope.launch { session.run() }; runCurrent()
        session.interrupt("old") // Never substitute the currently running turn.
        session.acknowledge(Turn("newer", "inProgress", emptyList()))
        assertNull(session.state.value.stoppableTurnId)
        session.interrupt("turn")
        assertEquals(0, server.interruptCalls)
    }

    @Test fun reconnectReloadsSnapshotHistoryBeforeSseAndDiscardsLateDeltas() = runTest {
        val server = FakeServer().apply { disconnect = true }
        val session = ThreadSession(server, "t")
        val job = backgroundScope.launch { session.run() }
        runCurrent()
        assertFalse(session.state.value.connected)
        assertTrue(session.state.value.statusLabel(1102).contains("1:42"))
        advanceTimeBy(1000); runCurrent()
        assertTrue(session.state.value.connected)
        assertEquals(listOf("snapshot", "history", "events", "snapshot", "history", "events"), server.calls)
        assertEquals("Complete answer", session.state.value.timeline.turns.single().items.single().text)
        assertNull(session.state.value.runningTurn)
        assertFalse(session.state.value.statusLabel(1102).contains("1:42"))
        assertEquals(0, server.posts)
        job.cancel(); runCurrent()
        assertFalse(session.state.value.connected)
    }

    @Test fun periodicHistoryHealsMissingCompletionWithoutReconnect() = runTest {
        val server = FakeServer()
        val session = ThreadSession(server, "t")
        backgroundScope.launch { session.run() }
        runCurrent()
        assertTrue(session.state.value.timeline.busy)
        server.completed = true
        advanceTimeBy(5000); runCurrent()
        assertEquals("Complete answer", session.state.value.timeline.turns.single().items.single().text)
        assertFalse(session.state.value.timeline.busy)
        assertEquals(1, server.connections)
        assertEquals(0, server.posts)
    }
}

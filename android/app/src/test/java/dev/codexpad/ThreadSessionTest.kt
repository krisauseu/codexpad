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
        var disconnect = false
        var connections = 0
        var posts = 0
        override suspend fun compactThread(threadId: String) { error("Unexpected compact") }
        override suspend fun models() = emptyList<CatalogModel>()
        override suspend fun health() = "ok"
        override suspend fun workspaces() = emptyList<Workspace>()
        override suspend fun threads(workspaceId: String) = emptyList<CodexThread>()
        override suspend fun createThread(workspaceId: String): CodexThread = error("Unexpected POST")
        override suspend fun startTurn(threadId: String, message: String, model: String?, effort: String?): Turn { posts++; error("Unexpected POST") }
        fun snapshot() = CodexThread("t", status = if (completed) "idle" else "active", turns = listOf(
            Turn("turn", if (completed) terminalStatus else "inProgress",
                listOf(Message("legacy-2", "agentMessage", if (completed) "Complete answer" else "Prefix")) +
                    if (toolMode) listOf(Wire.message(toolJson())) else emptyList())))
        override suspend fun thread(threadId: String): CodexThread { calls += "snapshot"; return snapshot() }
        override suspend fun history(threadId: String): CodexThread { calls += "history"; return snapshot() }
        override fun events(threadId: String) = flow {
            calls += "events"
            connections++
            val status = if (completed) terminalStatus else "inProgress"
            val text = if (completed) "Complete answer" else "Prefix"
            val snapshotJson = org.json.JSONObject("""{"thread":{"id":"t","model":"configured","reasoningEffort":"custom","status":{"type":"idle"},"turns":[
                {"id":"turn","status":"$status","items":[{"id":"legacy-2","type":"agentMessage","text":"$text"}]}]}}""")
            if (toolMode) snapshotJson.getJSONObject("thread").getJSONArray("turns").getJSONObject(0)
                .getJSONArray("items").put(toolJson())
            emit(SseFrame("snapshot", snapshotJson.toString()))
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
        assertEquals(70L, session.state.value.usage.remaining)
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
        assertNull(session.state.value.usage.remaining)
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
        assertEquals(listOf("turn", null), saved)
    }

    @Test fun completionRaceUsesActualTerminalStatusEvenWhenHttpFails() = runTest {
        for (status in listOf("interrupted", "completed", "failed")) {
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
        advanceTimeBy(1000); runCurrent()
        assertTrue(session.state.value.connected)
        assertEquals(listOf("snapshot", "history", "events", "snapshot", "history", "events"), server.calls)
        assertEquals("Complete answer", session.state.value.timeline.turns.single().items.single().text)
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

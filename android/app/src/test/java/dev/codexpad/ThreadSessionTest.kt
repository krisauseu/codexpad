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
        var completed = false
        var disconnect = false
        var connections = 0
        var posts = 0
        override suspend fun health() = "ok"
        override suspend fun workspaces() = emptyList<Workspace>()
        override suspend fun threads(workspaceId: String) = emptyList<CodexThread>()
        override suspend fun createThread(workspaceId: String): CodexThread = error("Unexpected POST")
        override suspend fun startTurn(threadId: String, message: String): Turn { posts++; error("Unexpected POST") }
        fun snapshot() = CodexThread("t", status = if (completed) "idle" else "active", turns = listOf(
            Turn("turn", if (completed) "completed" else "inProgress",
                listOf(Message("legacy-2", "agentMessage", if (completed) "Complete answer" else "Prefix")))))
        override suspend fun thread(threadId: String): CodexThread { calls += "snapshot"; return snapshot() }
        override suspend fun history(threadId: String): CodexThread { calls += "history"; return snapshot() }
        override fun events(threadId: String) = flow {
            calls += "events"
            connections++
            val status = if (completed) "completed" else "inProgress"
            val text = if (completed) "Complete answer" else "Prefix"
            emit(SseFrame("snapshot", """{"thread":{"id":"t","status":{"type":"idle"},"turns":[
                {"id":"turn","status":"$status","items":[{"id":"legacy-2","type":"agentMessage","text":"$text"}]}]}}"""))
            emit(SseFrame("event", """{"method":"item/agentMessage/delta","params":{"threadId":"t","turnId":"turn","itemId":"live-2","delta":"fragment"}}"""))
            if (disconnect && connections == 1) {
                completed = true // The turn finishes while the client is offline, without an end event.
                throw IOException("Disconnected")
            }
            awaitCancellation()
        }
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

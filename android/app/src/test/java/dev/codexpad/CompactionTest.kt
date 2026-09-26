package dev.codexpad

import dev.codexpad.data.*
import dev.codexpad.model.*
import dev.codexpad.network.*
import java.io.IOException
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.*
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CompactionTest {
    private class Backend : CodexPadService {
        val original = Turn("old", "completed", listOf(Message("answer", "agentMessage", "Keep this history")))
        var history = CodexThread("t", status = "idle", turns = listOf(original))
        var compactCalls = 0
        var action: suspend () -> Unit = {}
        var connections = 0
        val frames = Channel<SseFrame>(Channel.UNLIMITED)
        override suspend fun compactThread(threadId: String) { compactCalls++; action() }
        override suspend fun thread(threadId: String) = history
        override suspend fun history(threadId: String) = history
        override suspend fun answerRequest(threadId: String, requestId: String, answers: Map<String, InputAnswer>) { error("Unexpected answer") }
        override suspend fun models() = emptyList<CatalogModel>()
        override suspend fun health() = "ok"
        override suspend fun workspaces() = emptyList<Workspace>()
        override suspend fun threads(workspaceId: String) = listOf(history)
        override suspend fun createThread(workspaceId: String): CodexThread = error("Unexpected")
        override suspend fun interruptTurn(threadId: String, turnId: String) = error("Unexpected")
        override suspend fun startTurn(threadId: String, message: String, model: String?, effort: String?): Turn {
            val turn = Turn("next", "inProgress", listOf(Message("user", "userMessage", message)))
            history = history.copy(status = "active", turns = history.turns + turn)
            return turn
        }
        fun compactTurn(status: String) {
            history = history.copy(status = if (status == "inProgress") "active" else "idle",
                turns = listOf(original, Turn("compact", status, listOf(Message("summary", "contextCompaction", "")))))
        }
        fun event(method: String, params: String) {
            frames.trySend(SseFrame("event", """{"method":"$method","params":{"threadId":"t",$params}}"""))
        }
        fun usage(turn: String, tokens: Int) = event("thread/tokenUsage/updated",
            """"turnId":"$turn","tokenUsage":{"last":{"totalTokens":$tokens},"total":{"totalTokens":900},"modelContextWindow":100}""")
        override fun events(threadId: String) = flow {
            connections++
            val turns = JSONArray(history.turns.map { turn -> JSONObject().put("id", turn.id).put("status", turn.status)
                .put("items", JSONArray(turn.items.map { item -> JSONObject().put("id", item.id).put("type", item.type).put("text", item.text) })) })
            emit(SseFrame("snapshot", JSONObject().put("thread", JSONObject().put("id", "t")
                .put("status", JSONObject().put("type", history.status)).put("turns", turns)).toString()))
            for (frame in frames) {
                if (frame.event == "disconnect") throw IOException("Disconnected")
                emit(frame)
            }
        }
    }

    @Test fun compactWaitsForLifecyclePreservesHistoryAndUsesOnlyNewUsage() = runTest {
        val backend = Backend()
        val session = ThreadSession(backend, "t")
        backgroundScope.launch { session.run() }; runCurrent()
        backend.usage("old", 80); runCurrent()
        assertEquals(80L, session.state.value.usage.used)
        val gate = CompletableDeferred<Unit>()
        backend.action = { gate.await() }
        val request = launch { session.compact() }; runCurrent()
        assertEquals("requested", session.state.value.compaction?.phase)
        assertTrue(session.state.value.usage.stale)
        assertEquals(80L, session.state.value.usage.used)
        session.compact()
        assertEquals(1, backend.compactCalls)
        gate.complete(Unit); request.join()
        assertTrue(session.state.value.compaction!!.pending) // {} is never success.
        backend.compactTurn("inProgress")
        backend.event("item/started", """"turnId":"compact","item":{"id":"live-summary","type":"contextCompaction"}""")
        runCurrent()
        assertEquals("running", session.state.value.compaction?.phase)
        assertTrue(session.state.value.timeline.turns.last().items.single().isCompaction)
        backend.event("item/completed", """"turnId":"compact","item":{"id":"live-summary","type":"contextCompaction"}""")
        runCurrent()
        assertTrue(session.state.value.compaction!!.pending) // Item completion alone is insufficient.
        backend.compactTurn("completed")
        backend.event("turn/completed", """"turn":{"id":"compact","status":"completed","items":[]}""")
        runCurrent()
        assertEquals("completed", session.state.value.compaction?.phase)
        assertEquals(backend.original, session.state.value.timeline.turns.first())
        assertTrue(session.state.value.usage.stale)
        backend.usage("old", 80); runCurrent() // Old resume replay is not new usage.
        assertTrue(session.state.value.usage.stale)
        backend.usage("compact", 20); runCurrent()
        assertFalse(session.state.value.usage.stale)
        assertEquals(20L, session.state.value.usage.used)
        assertEquals(80L, session.state.value.usage.remaining)
        assertTrue(session.state.value.canCompact)
        session.acknowledge(backend.startTurn("t", "Continue", null, null))
        assertTrue(session.state.value.timeline.busy)
        assertEquals("next", session.state.value.stoppableTurnId)
        assertEquals(backend.original, session.state.value.timeline.turns.first())
    }

    @Test fun usageImmediatelyBeforeTurnCompletionBecomesFreshOnlyWithHistory() = runTest {
        val backend = Backend()
        val session = ThreadSession(backend, "t")
        backgroundScope.launch { session.run() }; runCurrent()
        session.compact()
        backend.compactTurn("inProgress")
        session.refresh()
        backend.usage("compact", 25); runCurrent()
        assertTrue(session.state.value.usage.stale)
        backend.compactTurn("completed")
        session.refresh()
        assertFalse(session.state.value.usage.stale)
        assertEquals(25L, session.state.value.usage.used)
    }

    @Test fun lostResponseAndReconnectRestoreWithoutRetry() = runTest {
        val backend = Backend()
        var saved: String? = null
        val session = ThreadSession(backend, "t", saveCompaction = { saved = it?.json() })
        val job = backgroundScope.launch { session.run() }; runCurrent()
        backend.action = { throw IOException("Lost response") }
        session.compact()
        assertEquals("unknown", session.state.value.compaction?.phase)
        session.compact()
        job.cancel(); runCurrent()
        val restored = ThreadSession(backend, "t", pendingCompaction = Compaction.restore(saved))
        backend.compactTurn("inProgress")
        backgroundScope.launch { restored.run() }; runCurrent()
        assertEquals("running", restored.state.value.compaction?.phase)
        backend.frames.trySend(SseFrame("disconnect", "")); runCurrent()
        assertEquals("unknown", restored.state.value.compaction?.phase)
        backend.compactTurn("completed")
        advanceTimeBy(1000); runCurrent()
        assertEquals("completed", restored.state.value.compaction?.phase)
        assertTrue(restored.state.value.connected)
        assertEquals(1, backend.compactCalls)
        assertEquals(backend.original, restored.state.value.timeline.turns.first())
    }

    @Test fun normalActiveTurnAndOfflineStateRejectCompact() = runTest {
        val backend = Backend()
        val session = ThreadSession(backend, "t")
        session.compact()
        backend.startTurn("t", "Hello", null, null)
        backgroundScope.launch { session.run() }; runCurrent()
        session.compact()
        assertEquals(0, backend.compactCalls)
        assertEquals("next", session.state.value.stoppableTurnId)
    }

    @Test fun historyHealsMissingEventsAndLateHttpErrorCannotUndoTerminalOutcome() = runTest {
        for (status in listOf("completed", "failed", "interrupted")) {
            val backend = Backend()
            val session = ThreadSession(backend, "t")
            val job = backgroundScope.launch { session.run() }; runCurrent()
            backend.action = {
                backend.compactTurn(status)
                session.refresh()
                throw IOException("Late failure")
            }
            session.compact()
            assertEquals(status, session.state.value.compaction?.phase)
            assertTrue(session.state.value.canCompact)
            job.cancel(); runCurrent()
        }
    }

    @Test fun oldCompactionAndUnrelatedTurnCannotResolveUnknownAttempt() = runTest {
        val backend = Backend()
        backend.compactTurn("completed")
        val session = ThreadSession(backend, "t")
        backgroundScope.launch { session.run() }; runCurrent()
        session.compact()
        backend.startTurn("t", "Elsewhere", null, null)
        session.refresh()
        assertEquals("unknown", session.state.value.compaction?.phase)
        assertFalse(session.state.value.canCompact)
        session.compact()
        assertEquals(1, backend.compactCalls)
    }

    @Test fun periodicHistoryResolvesCompactWithoutAnyEndEvent() = runTest {
        val backend = Backend()
        val session = ThreadSession(backend, "t")
        backgroundScope.launch { session.run() }; runCurrent()
        session.compact()
        backend.compactTurn("completed")
        advanceTimeBy(15000); runCurrent()
        assertEquals("completed", session.state.value.compaction?.phase)
        assertEquals(1, backend.compactCalls)
    }
}

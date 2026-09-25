package dev.codexpad

import dev.codexpad.network.CodexPadApi
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.onEach
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test

/** Opt-in test against tools/contract_server.py, which imports the production Handler unchanged. */
class PythonContractTest {
    @Test fun realPythonRoutesStreamDisconnectReconnectAndContinue() = runBlocking {
        val base = System.getenv("CODEXPAD_CONTRACT_URL")
        assumeTrue("Start tools/contract_server.py and set CODEXPAD_CONTRACT_URL", base != null)
        val api = CodexPadApi(base!!, System.getenv("CODEXPAD_ACCESS_TOKEN") ?: error("Missing contract token"))
        assertEquals("ok", api.health())
        val workspace = api.workspaces().single()
        assertEquals("demo space-ä", workspace.id)
        assertTrue(api.threads(workspace.id).isNotEmpty())
        val thread = api.createThread(workspace.id)
        assertEquals(thread.id, api.thread(thread.id).id)
        assertTrue(api.history(thread.id).turns.isEmpty())
        val ready = CompletableDeferred<Unit>()
        val firstDelta = async {
            api.events(thread.id).onEach { if (it.event == "snapshot") ready.complete(Unit) }
                .first { it.event == "event" && JSONObject(it.data).optString("method") == "item/agentMessage/delta" }
        }
        withTimeout(5000) { ready.await() }
        val turn = api.startTurn(thread.id, "Einmal vom Kotlin-Client")
        withTimeout(5000) { firstDelta.await() } // Cancels/closes the first SSE connection.
        val reconnected = withTimeout(5000) { api.events(thread.id).first() }
        assertEquals("snapshot", reconnected.event)
        assertEquals(thread.id, JSONObject(reconnected.data).getJSONObject("thread").getString("id"))
        val complete = withTimeout(10000) {
            var history = api.history(thread.id)
            while (history.turns.none { it.id == turn.id && it.terminal }) {
                delay(100)
                history = api.history(thread.id)
            }
            history
        }
        assertEquals("completed", complete.turns.single().status)
        assertEquals("Hallo vom lokalen Vertragstest. CODEXPAD-CLIENT-OK",
            complete.turns.single().items.last().text)
        assertTrue(api.threads(workspace.id).any { it.id == thread.id })
        val next = api.startTurn(thread.id, "Fortsetzen")
        assertNotEquals(turn.id, next.id)
    }
}

package dev.codexpad

import dev.codexpad.network.CodexPadApi
import dev.codexpad.network.UploadText
import dev.codexpad.model.TransferPolicy
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.onEach
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test

/** Opt-in test against tools/contract_server.py, which imports the production Handler unchanged. */
class PythonContractTest {
    @Test fun longPromptsAndHtmlUseProductionPythonHandler() = runBlocking {
        val base = System.getenv("CODEXPAD_CONTRACT_URL")
        assumeTrue("Start tools/contract_server.py and set CODEXPAD_CONTRACT_URL", base != null)
        val api = CodexPadApi(base!!, System.getenv("CODEXPAD_ACCESS_TOKEN") ?: error("Missing contract token"))
        val workspace = api.workspaces().single()
        val prefix = " \nGrüße 👋\n# Markdown\n```text\nCode\n```\n"
        val message = prefix + "😀".repeat(12000 - TransferPolicy.messageLength(prefix) - 2) + "\n "
        for (multipart in listOf(false, true)) {
            val thread = api.createThread(workspace.id)
            val files = if (multipart) listOf(UploadText("seite.html", "text/html", "<p>Grüße</p>".toByteArray())) else emptyList()
            val turn = api.startTurn(thread.id, message, null, null, emptyList(), files)
            assertEquals(message, turn.items.first().text)
            assertEquals(message, api.history(thread.id).turns.single().items.first().text)
            api.interruptTurn(thread.id, turn.id)
        }
        // Empty prompt exposes the attachment text as the fixture's first user input.
        for (name in listOf("Grüße.html", "seite.htm")) {
            val content = " \n<!doctype html>\n<p>Grüße 👋</p>\n "
            val thread = api.createThread(workspace.id)
            val turn = api.startTurn(thread.id, "", null, null, emptyList(),
                listOf(UploadText(name, "text/html", content.toByteArray())))
            assertEquals("Dateianhang: $name\n-----\n$content\n-----", turn.items.first().text)
            api.interruptTurn(thread.id, turn.id)
        }
    }

    @Test fun realPythonRoutesStreamDisconnectReconnectAndContinue() = runBlocking {
        val base = System.getenv("CODEXPAD_CONTRACT_URL")
        assumeTrue("Start tools/contract_server.py and set CODEXPAD_CONTRACT_URL", base != null)
        val api = CodexPadApi(base!!, System.getenv("CODEXPAD_ACCESS_TOKEN") ?: error("Missing contract token"))
        assertEquals("ok", api.health())
        assertEquals("fixture-model", api.models().single().model)
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
        val compactReady = CompletableDeferred<Unit>()
        val compactDone = async {
            api.events(thread.id).onEach { if (it.event == "snapshot") compactReady.complete(Unit) }
                .first { it.event == "event" && JSONObject(it.data).optString("method") == "turn/completed" }
        }
        withTimeout(5000) { compactReady.await() }
        api.compactThread(thread.id)
        withTimeout(5000) { compactDone.await() }
        val compactHistory = api.history(thread.id)
        assertEquals(complete.turns.single(), compactHistory.turns.first())
        assertTrue(compactHistory.turns.last().items.single().isCompaction)
        assertEquals("completed", compactHistory.turns.last().status)
        val next = api.startTurn(thread.id, "Fortsetzen", "fixture-model", "custom")
        assertNotEquals(turn.id, next.id)
        assertEquals("fixture-model", api.history(thread.id).model)
        assertEquals("custom", api.history(thread.id).reasoningEffort)
        val usageReplay = withTimeout(5000) { api.events(thread.id).first {
            it.event == "event" && JSONObject(it.data).optString("method") == "thread/tokenUsage/updated"
        } }
        assertEquals(30, JSONObject(usageReplay.data).getJSONObject("params").getJSONObject("tokenUsage")
            .getJSONObject("last").getInt("totalTokens"))
        val interruptReady = CompletableDeferred<Unit>()
        val ended = async {
            api.events(thread.id).onEach { if (it.event == "snapshot") interruptReady.complete(Unit) }
                .first { it.event == "event" && JSONObject(it.data).optString("method") == "turn/completed" }
        }
        withTimeout(5000) { interruptReady.await() }
        api.interruptTurn(thread.id, next.id)
        val eventTurn = JSONObject(withTimeout(5000) { ended.await() }.data)
            .getJSONObject("params").getJSONObject("turn")
        assertEquals(next.id, eventTurn.getString("id"))
        assertEquals("interrupted", eventTurn.getString("status"))
        assertEquals("interrupted", api.history(thread.id).turns.last().status)
        val afterReconnect = withTimeout(5000) { api.events(thread.id).first() }
        assertEquals("interrupted", JSONObject(afterReconnect.data).getJSONObject("thread")
            .getJSONArray("turns").getJSONObject(2).getString("status"))
    }
}

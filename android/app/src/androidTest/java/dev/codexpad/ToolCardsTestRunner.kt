package dev.codexpad

import android.app.Activity as AndroidActivity
import android.app.Instrumentation
import android.content.Intent
import android.graphics.Rect
import android.os.Bundle
import android.view.accessibility.AccessibilityNodeInfo
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.codexpad.model.*
import dev.codexpad.ui.MessageCard
import org.json.JSONObject

/** USB renderer checks using public accessibility, real Compose and the installed app; no settings writes. */
class ToolCardsTestRunner : Instrumentation() {
    override fun onCreate(arguments: Bundle?) { super.onCreate(arguments); start() }
    override fun onStart() {
        val result = Bundle()
        var activity: MainActivity? = null
        try {
            activity = startActivitySync(Intent(targetContext, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as MainActivity
            val displayed = mutableStateOf(Wire.message(JSONObject("""{"id":"c","type":"commandExecution","command":"test command","status":"inProgress","aggregatedOutput":null}""")))
            runOnMainSync {
                activity.setContent {
                    MaterialTheme {
                        Column(Modifier.fillMaxSize().padding(32.dp)) {
                            Text("TOOL-CARDS-USB")
                            Box(Modifier.weight(1f)) { MessageCard(displayed.value, false) }
                            Text("TABLET-COMPOSER-ANCHOR")
                        }
                    }
                }
            }
            awaitText("Läuft")
            clickText("Details öffnen")
            awaitText("Nicht vorhanden")
            val output = (1..2500).joinToString("\n") { "line $it " + "x".repeat(100) }
            runOnMainSync { displayed.value = displayed.value.copy(activity = Activity.Command("test command", "/tmp", "completed", output, 7, 2500)) }
            awaitText("Exit-Code: 7")
            awaitText("Fehler / abgebrochen")
            val scroll = awaitNode { it.isScrollable }
            val bounds = Rect().also(scroll::getBoundsInScreen)
            val anchor = Rect().also(awaitText("TABLET-COMPOSER-ANCHOR")::getBoundsInScreen)
            check(bounds.bottom <= anchor.top) { "Output overlaps composer" }
            // Reach chunk controls, advance, and prove content beyond first chunk is rendered.
            repeat(15) { scroll.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD); Thread.sleep(80) }
            clickText("Weiter (1/")
            awaitText("Weiter (2/")
            val cases = listOf(
                """{"id":"m","type":"mcpToolCall","server":"docs","tool":"lookup","status":"completed","arguments":{"q":"hello"},"result":{"content":[{"type":"text","text":"MCP-RESULT"}]}}""" to "MCP-RESULT",
                """{"id":"e","type":"mcpToolCall","tool":"failed","status":"failed","error":{"message":"MCP-ERROR"}}""" to "MCP-ERROR",
                """{"id":"d","type":"dynamicToolCall","tool":"dynamic","status":"completed","contentItems":[{"type":"inputText","text":"DYNAMIC-RESULT"}],"success":true}""" to "DYNAMIC-RESULT",
                """{"id":"missing","type":"dynamicToolCall","status":"completed"}""" to "Erfolg: unbekannt",
            )
            for ((json, expected) in cases) {
                runOnMainSync { displayed.value = Wire.message(JSONObject(json)) }
                clickText("Details öffnen")
                awaitText(expected)
            }
            runOnMainSync { displayed.value = Message("f", "fileChange", "", Activity.Files(listOf(
                FileEdit("/tmp/one", "add", null, output), FileEdit("/tmp/two", "update", "/tmp/moved", "-old\n+new")), "inProgress")) }
            clickText("Details öffnen")
            awaitText("Historischer Item-Diff")
            awaitText("noch nicht als angewandt")
            val diff = awaitNode { it.isScrollable }
            val diffBounds = Rect().also(diff::getBoundsInScreen)
            check(diffBounds.bottom <= anchor.top) { "Diff overlaps composer" }
            repeat(15) { diff.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD); Thread.sleep(80) }
            awaitText("/tmp/two")
            runOnMainSync { displayed.value = Message("text", "agentMessage", "NORMAL-TEXT-OK") }
            awaitText("NORMAL-TEXT-OK")
            result.putString("stream", "PASS: command running/completed/missing output, paged long output, MCP result/error, dynamic/unknown success, multiple file paths/long diff, bounded tablet layout, normal text.\n")
            finish(AndroidActivity.RESULT_OK, result)
        } catch (error: Throwable) {
            result.putString("stream", "FAIL: ${error.message}\n")
            finish(AndroidActivity.RESULT_CANCELED, result)
        } finally { activity?.let { runOnMainSync { it.finish() } } }
    }

    private fun awaitNode(predicate: (AccessibilityNodeInfo) -> Boolean): AccessibilityNodeInfo {
        repeat(50) {
            fun find(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
                if (predicate(node)) return node
                for (i in 0 until node.childCount) node.getChild(i)?.let { find(it)?.let { found -> return found } }
                return null
            }
            uiAutomation.rootInActiveWindow?.let { find(it)?.let { found -> return found } }
            Thread.sleep(100)
        }
        error("Expected accessibility node not found")
    }
    private fun awaitText(text: String) = awaitNode { it.text?.contains(text) == true }
    private fun clickText(text: String) {
        var node = awaitText(text)
        while (!node.isClickable) node = node.parent ?: error("No clickable parent for $text")
        check(node.performAction(AccessibilityNodeInfo.ACTION_CLICK))
        Thread.sleep(150)
    }
}

package dev.codexpad

import android.app.Activity
import android.app.Instrumentation
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.Bundle
import android.view.accessibility.AccessibilityNodeInfo
import dev.codexpad.model.*
import dev.codexpad.network.*
import dev.codexpad.settings.*
import java.io.File
import kotlinx.coroutines.runBlocking
import org.json.JSONObject

/** Opt-in real model/USB test. Connection file is app-private, never a production token.
 * Exercises the normal Activity, cards, API and reconnect. Restores encrypted preferences byte-for-byte.
 */
class UserInputTestRunner : Instrumentation() {
    override fun onCreate(arguments: Bundle?) { super.onCreate(arguments); start() }
    override fun onStart() {
        val result = Bundle()
        val prefs = targetContext.getSharedPreferences("connection", Context.MODE_PRIVATE)
        val original = prefs.all.toMap()
        var resultCode = Activity.RESULT_CANCELED
        var activity: MainActivity? = null
        try {
            val config = JSONObject(File(targetContext.filesDir, "input-test.json").readText())
            val url = config.getString("url")
            val token = config.getString("token")
            check(url == "http://127.0.0.1:18766") { "Test requires the isolated loopback server" }
            SettingsStore(targetContext).save(ConnectionSettings(url, token))
            val api = CodexPadApi(url, token, allowLocalHttp = true)
            val evidence = StringBuilder()
            runBlocking {
                val thread = api.createThread("input-test")
                evidence.append("THREAD ${thread.id}\n")
                for (kind in listOf("A", "B")) {
                    val prompt = if (kind == "A")
                        "Tablet-Test A: Erstelle eine Textdatei, aber frage mich vorher mit dem Tool request_user_input nach dem Dateinamen. Biete alpha.txt und beta.txt an und erlaube eine eigene Antwort. Warte auf meine Tool-Antwort. Schreibe dann exakt INPUT-TEXT-OK in die Datei im angegebenen Ergebnisordner und nenne den gewaehlten Namen im Endergebnis. Keine anderen Dateien lesen."
                    else "Tablet-Test B: Frage mich jetzt mit request_user_input welche Variante ich moechte. Biete genau die Optionen Rot und Blau. Warte auf meine Tool-Antwort und schreibe danach exakt AUSWAHL-OK gefolgt von meiner Auswahl in deine abschliessende Antwort. Keine Dateien lesen."
                    val turn = api.startTurn(thread.id, prompt)
                    var request: InputRequest? = null
                    repeat(120) {
                        if (request == null) {
                            request = api.history(thread.id).pendingRequests.singleOrNull()
                            if (request == null) Thread.sleep(1000)
                        }
                    }
                    val pending = checkNotNull(request) { "No real request for test $kind" }
                    check(pending.turnId == turn.id)
                    evidence.append("$kind TURN ${turn.id} REQUEST ${pending.id} blocking=${pending.isBlocking}\n")
                    if (activity == null) {
                        activity = openApp()
                        clickText("input-test")
                        openListedThread(thread.id)
                    }
                    awaitText("Codex braucht eine Angabe")
                    if (kind == "A") {
                        // Destroy the actual Activity/session and reopen from workspace/history.
                        runOnMainSync { activity!!.finish() }
                        waitForIdleSync()
                        activity = openApp()
                        clickText("input-test")
                        openListedThread(thread.id)
                        awaitText("Codex braucht eine Angabe")
                        val after = api.history(thread.id).pendingRequests.single()
                        check(after.id == pending.id)
                        evidence.append("A REOPEN same request; history recovered\n")
                        setFieldText("Weitere Nachricht an Codex", "Zusatz: Warte weiter auf meine Tool-Antwort; waehle keinen Namen selbst.")
                        clickText("Senden", exact = true)
                        Thread.sleep(1500)
                        val steered = api.history(thread.id)
                        check(steered.turns.single().id == turn.id)
                        check(steered.pendingRequests.single().id == pending.id)
                        evidence.append("A COMPOSER steered same turn; same request still pending\n")
                        setFieldText("Eigene Antwort", "tablet-eigener-name.txt")
                    } else clickText("Blau", exact = true)
                    screenshot("input-$kind-pending.png")
                    val button = clickable(awaitText("Antworten", exact = true))
                    check(button.performAction(AccessibilityNodeInfo.ACTION_CLICK))
                    button.performAction(AccessibilityNodeInfo.ACTION_CLICK) // Immediate double tap.
                    var terminal: Turn? = null
                    repeat(120) {
                        if (terminal == null) {
                            terminal = api.history(thread.id).turns.firstOrNull { it.id == turn.id && it.terminal }
                            if (terminal == null) Thread.sleep(1000)
                        }
                    }
                    val completed = checkNotNull(terminal) { "Turn did not continue" }
                    check(completed.status == "completed")
                    check(api.history(thread.id).pendingRequests.isEmpty())
                    val final = completed.items.filter { it.type == "agentMessage" }.joinToString("\n") { it.text }
                    check(final.contains(if (kind == "A") "tablet-eigener-name.txt" else "Blau")) { "Wrong final answer" }
                    awaitText(if (kind == "A") "tablet-eigener-name.txt" else "AUSWAHL-OK")
                    awaitAbsent("Codex braucht eine Angabe")
                    val answer = mapOf(pending.questions.single().id to InputAnswer(if (kind == "A") "tablet-eigener-name.txt" else "Blau", kind == "B"))
                    try { api.answerRequest(thread.id, pending.id, answer); error("Old request accepted") }
                    catch (error: ApiException) { check(error.status == 409) }
                    evidence.append("$kind card/answer/double-tap/same-turn completion/removed card/old ID=409 PASS\nFINAL $final\n")
                    screenshot("input-$kind-completed.png")
                }
            }
            File(targetContext.filesDir, "input-test-evidence.txt").writeText(evidence.toString())
            result.putString("stream", "PASS\n$evidence")
            resultCode = Activity.RESULT_OK
        } catch (error: Throwable) {
            result.putString("stream", "FAIL: ${error.javaClass.simpleName}: ${error.message}\n")
            screenshot("input-failed.png")
        } finally {
            activity?.let { runOnMainSync { it.finish() } }
            prefs.edit().clear().apply {
                original.forEach { (key, value) -> check(value is String); putString(key, value) }
            }.commit()
            File(targetContext.filesDir, "input-test.json").delete()
        }
        finish(resultCode, result)
    }

    private fun openApp() = startActivitySync(Intent(targetContext, MainActivity::class.java)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)) as MainActivity

    private fun find(node: AccessibilityNodeInfo, predicate: (AccessibilityNodeInfo) -> Boolean): AccessibilityNodeInfo? {
        if (predicate(node)) return node
        for (i in 0 until node.childCount) node.getChild(i)?.let { find(it, predicate)?.let { found -> return found } }
        return null
    }
    private fun awaitNode(predicate: (AccessibilityNodeInfo) -> Boolean): AccessibilityNodeInfo {
        repeat(150) {
            uiAutomation.rootInActiveWindow?.let { find(it, predicate)?.let { found -> return found } }
            Thread.sleep(100)
        }
        error("Expected accessibility node not found")
    }
    private fun awaitText(text: String, exact: Boolean = false): AccessibilityNodeInfo {
        try { return awaitNode { if (exact) it.text?.toString() == text else it.text?.contains(text) == true } }
        catch (_: IllegalStateException) { error("Text not found: $text") }
    }
    private fun awaitAbsent(text: String) {
        repeat(150) {
            val root = uiAutomation.rootInActiveWindow
            if (root != null && find(root) { it.text?.contains(text) == true } == null) return
            Thread.sleep(100)
        }
        error("Resolved card remains visible")
    }
    private fun clickable(start: AccessibilityNodeInfo): AccessibilityNodeInfo {
        var node = start
        while (!node.isClickable) node = node.parent ?: error("Missing clickable parent")
        return node
    }
    private fun clickText(text: String, exact: Boolean = false) {
        check(clickable(awaitText(text, exact)).performAction(AccessibilityNodeInfo.ACTION_CLICK))
        Thread.sleep(300)
    }
    private fun openListedThread(id: String) {
        repeat(30) {
            val node = uiAutomation.rootInActiveWindow?.let { root -> find(root) { it.text?.toString() == id } }
            if (node != null) {
                check(clickable(node).performAction(AccessibilityNodeInfo.ACTION_CLICK))
                Thread.sleep(300)
                return
            }
            clickText("Aktualisieren")
            Thread.sleep(1000)
        }
        error("Thread not listed: $id")
    }
    private fun setFieldText(label: String, text: String) {
        val field = awaitNode { node -> node.isEditable &&
            find(node) { it.text?.contains(label) == true || it.hintText?.contains(label) == true } != null }
        check(field.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
        }))
        Thread.sleep(300)
    }
    private fun screenshot(name: String) {
        uiAutomation.takeScreenshot()?.let { bitmap ->
            File(targetContext.filesDir, name).outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            bitmap.recycle()
        }
    }
}

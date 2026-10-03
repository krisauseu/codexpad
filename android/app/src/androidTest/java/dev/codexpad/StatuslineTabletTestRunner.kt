package dev.codexpad

import android.app.Activity
import android.app.Instrumentation
import android.content.Intent
import android.graphics.Bitmap
import android.os.Bundle
import android.view.accessibility.AccessibilityNodeInfo
import androidx.lifecycle.ViewModelProvider
import dev.codexpad.data.ThreadState
import dev.codexpad.ui.PadViewModel
import java.io.File

/** Opt-in production test: existing account/workspace/thread, ONE short real turn, no token export. */
class StatuslineTabletTestRunner : Instrumentation() {
    private lateinit var threadId: String
    override fun onCreate(arguments: Bundle?) {
        super.onCreate(arguments)
        threadId = requireNotNull(arguments?.getString("thread")) { "Explicit existing test thread required" }
        start()
    }

    override fun onStart() {
        val result = Bundle()
        var activity: MainActivity? = null
        try {
            activity = startActivitySync(Intent(targetContext, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)) as MainActivity
            lateinit var vm: PadViewModel
            runOnMainSync { vm = ViewModelProvider(activity)[PadViewModel::class.java] }
            click("testprojekt")
            await { !vm.loading && vm.threads.any { it.id == threadId } }
            click(threadId)
            await { vm.session?.state?.value?.let { it.connected && !it.timeline.busy && it.timeline.turns.isNotEmpty() } == true }
            checkIdle()
            screenshot("idle")
            val initialField = awaitNode { it.isEditable && find(it) { child -> child.text?.contains("Nachricht an Codex") == true } != null }
            check(initialField.performAction(AccessibilityNodeInfo.ACTION_CLICK)) { "Cannot focus composer" }
            Thread.sleep(500)
            val field = awaitNode { it.isEditable && it.isFocused }
            check(field.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, Bundle().apply {
                putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,
                    "CodexPad v0.1.1 Status-Prüfung: Führe ausschließlich sleep 15 aus. Antworte danach exakt CODEXPAD-QUIET-RELEASE-OK. Keine Dateien lesen oder ändern, keine weiteren Tools.")
            })) { "Cannot enter test prompt" }
            await { vm.draft.contains("CODEXPAD-QUIET-RELEASE-OK") }
            Thread.sleep(300)
            click("Senden")
            await { vm.session?.state?.value?.runningTurn?.startedAt != null }
            awaitNode { it.text?.toString()?.let { text -> text.startsWith("Kontext ") && text.contains("Woche ") && text.matches(Regex(".* · [0-9]+:[0-9]{2}")) } == true }
            checkQuietControls()
            screenshot("running")
            lateinit var active: ThreadState
            runOnMainSync { active = vm.session!!.state.value }
            val activeLabel = active.statusLabel(System.currentTimeMillis() / 1000)
            check(active.timeline.busy && active.runningTurn != null)
            await(1200) { vm.session?.state?.value?.let { !it.timeline.busy && it.timeline.turns.lastOrNull()?.terminal == true } == true }
            awaitNode { it.text?.toString()?.contains("CODEXPAD-QUIET-RELEASE-OK") == true }
            checkIdle()
            screenshot("completed")
            Thread.sleep(1500)
            checkIdle()
            click("Einstellungen")
            click("Verbindung testen")
            awaitNode { it.text?.toString() == "Verbunden · Token akzeptiert · Codex bereit" }
            screenshot("connection")
            click("Zurück")
            await { vm.session?.state?.value?.connected == true }
            checkIdle()
            click("Zurück")
            awaitNode { it.text?.toString() == "Neuer Thread" }
            click("Zurück")
            click("testprojekt")
            await { !vm.loading && vm.threads.any { it.id == threadId } }
            click(threadId)
            await { vm.session?.state?.value?.let { it.connected && !it.timeline.busy && it.timeline.turns.lastOrNull()?.terminal == true } == true }
            checkIdle()
            screenshot("reopened")
            lateinit var ended: ThreadState
            runOnMainSync { ended = vm.session!!.state.value }
            check(ended.timeline.turns.last().items.any { it.type == "agentMessage" && it.text.contains("CODEXPAD-QUIET-RELEASE-OK") })
            check(ended.usage.remainingPercent != null && ended.weekly.remainingPercent != null)
            result.putString("stream", "PASS: existing conversation, absent context action, quiet idle, real active duration, completed duration removal, no idle turn text, workspace/thread reopen, live reconnect and authenticated connection test.\nTHREAD $threadId\nACTIVE ${active.modelLabel} / $activeLabel\nFINAL ${ended.modelLabel} / ${ended.statusLabel(System.currentTimeMillis() / 1000)}\nTURN ${ended.timeline.turns.last().id}, startedAt=${ended.timeline.turns.last().startedAt}, durationMs=${ended.timeline.turns.last().durationMs}\n")
            finish(Activity.RESULT_OK, result)
        } catch (error: Throwable) {
            screenshot("failed")
            result.putString("stream", "FAIL: ${error.javaClass.simpleName}: ${error.message}\n${error.stackTrace.take(8).joinToString("\n")}\n")
            finish(Activity.RESULT_CANCELED, result)
        } finally {
            activity?.let { runOnMainSync { it.finish() } }
        }
    }

    private fun checkQuietControls() {
        val root = uiAutomation.rootInActiveWindow ?: error("No visible window")
        check(find(root) { it.text?.toString() in setOf("Kontext", "Kontext komprimieren") } == null)
        check(find(root) { it.text?.contains("Kein laufender Turn") == true } == null)
    }
    private fun checkIdle() {
        checkQuietControls()
        awaitNode { it.text?.toString()?.matches(Regex("Kontext [0-9]+ % frei · Woche [0-9]+ % frei")) == true }
        awaitNode { it.text?.toString() == "Live verbunden" }
    }
    private fun find(node: AccessibilityNodeInfo, predicate: (AccessibilityNodeInfo) -> Boolean): AccessibilityNodeInfo? {
        if (predicate(node)) return node
        for (i in 0 until node.childCount) node.getChild(i)?.let { find(it, predicate)?.let { found -> return found } }
        return null
    }
    private fun awaitNode(predicate: (AccessibilityNodeInfo) -> Boolean): AccessibilityNodeInfo {
        repeat(300) {
            uiAutomation.rootInActiveWindow?.let { find(it, predicate)?.let { found -> return found } }
            Thread.sleep(100)
        }
        error("Expected accessibility node missing")
    }
    private fun await(attempts: Int = 300, condition: () -> Boolean) {
        repeat(attempts) {
            var ready = false
            runOnMainSync { ready = condition() }
            checkQuietControls()
            if (ready) return
            Thread.sleep(100)
        }
        error("State condition timed out")
    }
    private fun click(text: String) {
        var node = awaitNode { it.text?.toString() == text && it.isEnabled }
        while (!node.isClickable) node = node.parent ?: error("Missing clickable parent: $text")
        check(node.performAction(AccessibilityNodeInfo.ACTION_CLICK))
        Thread.sleep(400)
    }
    private fun screenshot(stage: String) {
        uiAutomation.takeScreenshot()?.let { bitmap ->
            File(targetContext.filesDir, "quiet-$stage.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            bitmap.recycle()
        }
    }
}

package dev.codexpad

import android.app.Activity
import android.app.Instrumentation
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.os.Bundle
import android.view.accessibility.AccessibilityNodeInfo
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModelProvider
import dev.codexpad.ui.PadViewModel
import java.io.File

/** Explicit opt-in: uses the tablet's existing connection and a NEW testprojekt thread.
 * Exercises the production UI/transport with one real model turn. No settings/token export.
 * Screenshots remain app-private until explicitly pulled via adb.
 */
class PolishTabletTestRunner : Instrumentation() {
    private var resume = false
    private var controls = false
    override fun onCreate(arguments: Bundle?) {
        super.onCreate(arguments)
        controls = arguments?.getString("phase") == "controls"
        resume = controls || arguments?.getString("resume") == "true"
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
            awaitText("testprojekt", true)
            screenshot("workspaces")
            click("testprojekt", true)
            awaitText("Neuer Thread", true)
            screenshot("threads")
            click("Aktualisieren", true)
            if (resume) {
                awaitCondition { !vm.loading && vm.threads.isNotEmpty() }
                val pending = vm.threads.first { it.preview.startsWith("UI-Polish-Tablet-Test:") }
                runOnMainSync { vm.openThread(pending.id) }
            } else {
                click("Neuer Thread", true)
                awaitCondition { vm.session?.state?.value?.connected == true && vm.models.isNotEmpty() }
                screenshot("empty-chat")
                // Select through the normal dialogs, using an available low-cost model.
                val model = vm.models.firstOrNull { it.model == "gpt-6-luna" } ?: vm.models.first()
                val current = vm.models.firstOrNull { it.model == vm.session?.state?.value?.timeline?.thread?.model }
                click(current?.name ?: vm.session?.state?.value?.timeline?.thread?.model ?: "Modell unbekannt", true)
                click(model.name)
                val effort = model.efforts.firstOrNull { it.effort == "low" } ?: model.efforts.first()
                click(vm.nextEffort ?: "Reasoning unbekannt", true)
                click(effort.effort, true)
                val folder = File(targetContext.cacheDir, "artifacts/polish-input").apply { mkdirs() }
                val text = File(folder, "polish-upload.txt").apply { writeText("UPLOAD-POLISH-OK") }
                val picture = File(folder, "polish-preview.png")
                Bitmap.createBitmap(96, 96, Bitmap.Config.ARGB_8888).apply {
                    eraseColor(Color.rgb(39, 103, 94))
                    picture.outputStream().use { compress(Bitmap.CompressFormat.PNG, 100, it) }
                    recycle()
                }
                val uris = listOf(picture, text).map { FileProvider.getUriForFile(targetContext, "dev.codexpad.artifacts", it) }
                // Same entry point as the platform document picker callback, real content URIs/bytes.
                runOnMainSync { vm.addAttachments(uris) }
                awaitText("polish-preview.png", true)
                screenshot("composer-attachments")
                clickDescription("polish-preview.png entfernen")
                awaitCondition { vm.images.isEmpty() && vm.textFiles.size == 1 }
                runOnMainSync { vm.addAttachments(listOf(uris.first())) }
                setField("Nachricht an Codex", "UI-Polish-Tablet-Test: Lies nur den angehängten Text und das angehängte Bild, keine Projektdateien. Frage mich ZUERST mit request_user_input nach dem Namen einer Ergebnis-Textdatei. Biete genau polish-alpha.txt und polish-beta.txt an, erlaube eigene Antwort, und warte auf meine Antwort. Schreibe danach in die gewählte Ergebnisdatei exakt den Inhalt des Textanhangs. Bestätige im Chat UPLOAD-POLISH-OK und die Farbe des Bildes. Keine weiteren Änderungen.")
                click("Senden", true)
                awaitCondition { vm.session?.state?.value?.timeline?.busy == true }
                screenshot("streaming")
            }
            awaitCondition { vm.session?.state?.value?.connected == true }
            if (controls) {
                checkControls(vm)
                result.putString("stream", "PASS: attachment picker, keyboard, interrupt, compaction and reconnect. THREAD ${vm.threadId}\n")
                finish(Activity.RESULT_OK, result)
                return
            }
            if (!resume || vm.session!!.state.value.requests.isNotEmpty()) {
                awaitText("Codex braucht eine Angabe", timeout = 1800)
                click("polish-beta.txt", true)
                screenshot("question-selected")
                reveal("Antworten")
                screenshot("question-answer")
                check(clickable(awaitText("Antworten", true)).isEnabled)
                click("Antworten", true)
                awaitCondition(1800) { vm.session?.state?.value?.let { it.requests.isEmpty() && !it.timeline.busy && it.timeline.turns.any { turn -> turn.artifacts.isNotEmpty() } } == true }
            }
            awaitText("polish-beta.txt", true)
            screenshot("artifact")
            val thread = vm.threadId!!
            val turn = vm.session!!.state.value.timeline.turns.last { it.artifacts.isNotEmpty() }
            check(turn.items.any { it.type == "agentMessage" && it.text.contains("UPLOAD-POLISH-OK") })
            // The card's actual download and Android document-save action.
            if (!resume) {
                click("Speichern", true)
                awaitText("SPEICHERN", true)
                screenshot("save-picker")
                click("SPEICHERN", true)
                awaitText("Nachricht an Codex")
            }
            click("Öffnen", true)
            awaitText("HTML-Anzeige") // Honor's chooser omits the supplied chooser title.
            screenshot("open-chooser")
            click("HTML-Anzeige", true)
            awaitText("UPLOAD-POLISH-OK", true)
            screenshot("opened-file")
            uiAutomation.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_BACK)
            awaitText("Nachricht an Codex")
            // A normal follow-up also proves reconnection after external Android activities.
            setField("Nachricht an Codex", "Antworte nur mit POLISH-NACHRICHT-OK. Keine Tools und keine Dateiänderungen.")
            click("Senden", true)
            awaitText("POLISH-NACHRICHT-OK", true, 1800)
            screenshot("chat-completed")
            click("Zurück", true)
            awaitText("Neuer Thread", true)
            click("Aktualisieren", true)
            awaitText(thread, true)
            screenshot("threads-after")
            result.putString("stream", "PASS (${if (resume) "resumed" else "full"}): completed applicable workflow through file viewer, normal follow-up and refreshed thread list. THREAD $thread\n")
            finish(Activity.RESULT_OK, result)
        } catch (error: Throwable) {
            screenshot("failed")
            result.putString("stream", "FAIL: ${error.javaClass.simpleName}: ${error.message}\n")
            finish(Activity.RESULT_CANCELED, result)
        } finally { activity?.let { runOnMainSync { it.finish() } } }
    }
    private fun checkControls(vm: PadViewModel) {
        clickDescription("Datei anhängen")
        awaitNode { it.packageName?.toString()?.contains("documentsui") == true }
        screenshot("attachment-picker")
        uiAutomation.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_BACK)
        awaitText("Nachricht an Codex")
        val field = awaitNode { it.isEditable }
        check(field.performAction(AccessibilityNodeInfo.ACTION_CLICK))
        Thread.sleep(1000)
        screenshot("keyboard")
        uiAutomation.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_BACK)
        setField("Nachricht an Codex", "Bedienungstest: Führe ausschließlich sleep 30 aus, dann antworte FERTIG. Keine Dateien lesen oder ändern.")
        click("Senden", true)
        awaitCondition { vm.session?.state?.value?.stoppableTurnId != null }
        screenshot("interrupt-ready")
        click("Stoppen", true)
        awaitCondition(600) { vm.session?.state?.value?.timeline?.turns?.lastOrNull()?.status == "interrupted" }
        screenshot("interrupted")
        awaitCondition { vm.session?.state?.value?.canCompact == true }
        click("Kontext", true)
        click("Kontext komprimieren", true)
        awaitCondition(1800) { vm.session?.state?.value?.compaction?.phase == "completed" }
        screenshot("compaction")
        click("Einstellungen", true)
        click("Verbindung testen", true)
        awaitText("Verbunden · Token akzeptiert · Codex bereit")
        screenshot("connection")
        click("Zurück", true)
        awaitCondition { vm.session?.state?.value?.connected == true }
    }
    private fun find(node: AccessibilityNodeInfo, predicate: (AccessibilityNodeInfo) -> Boolean): AccessibilityNodeInfo? {
        if (predicate(node)) return node
        for (i in 0 until node.childCount) node.getChild(i)?.let { find(it, predicate)?.let { found -> return found } }
        return null
    }
    private fun awaitNode(timeout: Int = 150, predicate: (AccessibilityNodeInfo) -> Boolean): AccessibilityNodeInfo {
        repeat(timeout) {
            uiAutomation.rootInActiveWindow?.let { find(it, predicate)?.let { found -> return found } }
            Thread.sleep(100)
        }
        error("Expected accessibility node not found")
    }
    private fun awaitText(text: String, exact: Boolean = false, timeout: Int = 150): AccessibilityNodeInfo =
        try { awaitNode(timeout) { if (exact) it.text?.toString()?.replace("\u200b", "") == text else it.text?.toString()?.replace("\u200b", "")?.contains(text) == true } }
        catch (_: IllegalStateException) { error("Text not found: $text") }
    private fun awaitCondition(timeout: Int = 150, condition: () -> Boolean) {
        repeat(timeout) {
            var ready = false
            runOnMainSync { ready = condition() }
            if (ready) return
            Thread.sleep(100)
        }
        error("State condition timed out")
    }
    private fun reveal(text: String) {
        repeat(10) {
            val root = uiAutomation.rootInActiveWindow
            if (root != null && find(root) { it.text?.toString() == text && it.isVisibleToUser } != null) return
            root?.let { find(it) { node -> node.isScrollable } }?.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)
            Thread.sleep(300)
        }
        error("Could not scroll to $text")
    }
    private fun clickable(start: AccessibilityNodeInfo): AccessibilityNodeInfo {
        var node = start
        while (!node.isClickable) node = node.parent ?: error("Missing clickable parent")
        return node
    }
    private fun click(text: String, exact: Boolean = false) {
        check(clickable(awaitText(text, exact)).performAction(AccessibilityNodeInfo.ACTION_CLICK))
        Thread.sleep(500)
    }
    private fun clickDescription(description: String) {
        check(clickable(awaitNode { it.contentDescription?.toString() == description }).performAction(AccessibilityNodeInfo.ACTION_CLICK))
        Thread.sleep(300)
    }
    private fun setField(label: String, text: String) {
        val field = awaitNode { node -> node.isEditable && find(node) {
            it.text?.contains(label) == true || it.hintText?.contains(label) == true } != null }
        check(field.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
        }))
        Thread.sleep(300)
    }
    private fun screenshot(name: String) {
        Thread.sleep(400)
        uiAutomation.takeScreenshot()?.let { bitmap ->
            File(targetContext.filesDir, "polish-$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            bitmap.recycle()
        }
    }
}

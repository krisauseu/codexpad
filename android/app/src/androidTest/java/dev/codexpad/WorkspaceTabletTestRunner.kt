package dev.codexpad

import android.app.Activity
import android.app.Instrumentation
import android.content.Intent
import android.os.Bundle
import android.view.accessibility.AccessibilityNodeInfo
import androidx.lifecycle.ViewModelProvider
import dev.codexpad.ui.PadViewModel
import java.io.File

/** Explicit real-tablet verification. Only a new temporary workspace is mutated.
 * Existing threads are opened/resumed/read; no model turns or file writes are requested.
 */
class WorkspaceTabletTestRunner : Instrumentation() {
    private var emptyThreadId: String? = null
    override fun onCreate(arguments: Bundle?) {
        super.onCreate(arguments)
        emptyThreadId = arguments?.getString("emptyThreadId")
        start()
    }
    override fun onStart() {
        val result = Bundle()
        val log = StringBuilder()
        val name = "tablet-workspace-${System.currentTimeMillis()}"
        val renamed = "$name-renamed"
        var activity: MainActivity? = null
        try {
            activity = startActivitySync(Intent(targetContext, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)) as MainActivity
            lateinit var vm: PadViewModel
            runOnMainSync { vm = ViewModelProvider(activity)[PadViewModel::class.java] }
            awaitCondition { vm.ready && !vm.showSettings }
            runOnMainSync { while (vm.workspace != null) vm.back(); vm.reload() }
            awaitCondition { !vm.loading && vm.workspaces.isNotEmpty() }
            val originals = listOf("kurzfragen", "ideen", "projekt", "testprojekt")
            check(originals.all { id -> vm.workspaces.any { it.id == id } })
            screenshot("before")
            click("Neuer Workspace")
            check(!clickable(text("Speichern")).isEnabled)
            setName("../escape")
            check(!clickable(text("Speichern")).isEnabled)
            setName("  $name  ")
            click("Speichern")
            awaitCondition { !vm.workspaceBusy && vm.workspaces.any { it.id == name } }
            check(vm.workspace == null)
            log.appendLine("PASS: UI create, trimming, invalid name, no auto-entry: $name")
            // Duplicate is handled by the real server and displayed in the dialog.
            click("Neuer Workspace")
            setName(name)
            click("Speichern")
            text("Dieser Workspace-Name ist bereits vorhanden.")
            click("Abbrechen")
            log.appendLine("PASS: duplicate rejected and translated")
            click(name)
            text("Neuer Thread")
            awaitCondition { !vm.loading && vm.threads.isEmpty() }
            screenshot("opened")
            click("Zurück")
            awaitCondition { !vm.loading && vm.workspace == null }
            actions(name)
            click("Umbenennen")
            setName(renamed)
            click("Speichern")
            awaitCondition { !vm.workspaceBusy && vm.workspaces.any { it.id == renamed } }
            check(vm.workspaces.none { it.id == name })
            screenshot("renamed")
            log.appendLine("PASS: UI open and rename: $renamed")
            actions(renamed)
            click("Löschen")
            text("dauerhaft gelöscht", exact = false)
            awaitCondition { !vm.workspaceBusy && vm.deleteInspection != null }
            check(vm.deleteInspection!!.threads == 0)
            screenshot("confirmation")
            click("Abbrechen")
            check(vm.workspaces.any { it.id == renamed })
            actions(renamed)
            click("Löschen")
            awaitCondition { !vm.workspaceBusy && vm.deleteInspection != null }
            click("Endgültig löschen")
            awaitCondition { !vm.workspaceBusy && vm.workspaces.none { it.id == renamed } }
            log.appendLine("PASS: explicit confirmation, cancel, delete, refreshed list")

            for (id in originals) {
                val ws = vm.workspaces.first { it.id == id }
                click(id)
                awaitCondition { !vm.loading }
                check(vm.error == null)
                if (id == "ideen" && emptyThreadId != null) {
                    check(vm.threads.any { it.id == emptyThreadId })
                    click(emptyThreadId!!)
                    awaitCondition(600) { vm.session?.state?.value?.connected == true }
                    check(vm.session!!.state.value.timeline.turns.isEmpty())
                    click("Zurück")
                    awaitCondition { !vm.loading && vm.workspace != null }
                    log.appendLine("PASS: persisted empty thread remains visible and resumable after server restart")
                }
                val existing = vm.threads.firstOrNull { it.preview.isNotBlank() && it.status != "active" }
                    ?: error("No existing idle thread in $id")
                // Select the actual thread card by its stable id, avoiding long preview text.
                click(existing.id)
                awaitCondition(600) { vm.session?.state?.value?.connected == true }
                check(vm.session!!.state.value.timeline.thread?.id == existing.id)
                check(vm.session!!.state.value.timeline.turns.isNotEmpty())
                screenshot("thread-$id")
                log.appendLine("PASS: $id existing thread history + SSE resume: ${existing.id}")
                click("Zurück")
                awaitCondition { !vm.loading }
                click("Zurück")
                awaitCondition { !vm.loading && vm.workspace == null }
                // Both operations must be blocked without changing this workspace.
                actions(id)
                click("Umbenennen")
                setName("$id-tablet-must-not-rename")
                click("Speichern")
                text("zum Schutz", exact = false)
                awaitCondition { !vm.workspaceBusy && vm.workspaceActionError != null }
                check(vm.workspaces.any { it.id == ws.id })
                click("Abbrechen")
                actions(id)
                click("Löschen")
                awaitCondition { !vm.workspaceBusy && vm.deleteInspection != null }
                check(vm.deleteInspection!!.threads > 0)
                check(!clickable(text("Endgültig löschen")).isEnabled)
                screenshot("guard-$id")
                click("Abbrechen")
                log.appendLine("PASS: $id rename/delete guarded, preserved")
            }
            runOnMainSync { vm.reload() }
            awaitCondition { !vm.loading }
            check(originals.all { id -> vm.workspaces.any { it.id == id } })
            check(vm.workspaces.none { it.id == name || it.id == renamed })
            screenshot("after")
            result.putString("stream", log.toString())
            finish(Activity.RESULT_OK, result)
        } catch (failure: Throwable) {
            screenshot("failure")
            result.putString("stream", log.toString() + "FAIL: ${failure.javaClass.simpleName}: ${failure.message}\nTemporary workspace: $name / $renamed\n")
            finish(Activity.RESULT_CANCELED, result)
        } finally { activity?.finish() }
    }

    private fun find(node: AccessibilityNodeInfo, predicate: (AccessibilityNodeInfo) -> Boolean): AccessibilityNodeInfo? {
        if (predicate(node)) return node
        for (index in 0 until node.childCount) node.getChild(index)?.let { find(it, predicate)?.let { found -> return found } }
        return null
    }
    private fun node(scroll: Boolean = false, predicate: (AccessibilityNodeInfo) -> Boolean): AccessibilityNodeInfo {
        repeat(300) { attempt ->
            uiAutomation.rootInActiveWindow?.let { root ->
                find(root, predicate)?.let { found -> return found }
                if (scroll && attempt % 5 == 4) find(root) { it.isScrollable }?.performAction(
                    if ((attempt / 5) % 12 < 6) AccessibilityNodeInfo.ACTION_SCROLL_FORWARD
                    else AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD)
            }
            Thread.sleep(100)
        }
        error("Accessibility node not found")
    }
    private fun text(value: String, exact: Boolean = true): AccessibilityNodeInfo = node {
        val actual = it.text?.toString()?.replace("\u200b", "")
        if (exact) actual == value else actual?.contains(value) == true
    }
    private fun clickable(start: AccessibilityNodeInfo): AccessibilityNodeInfo {
        var current = start
        while (!current.isClickable) current = current.parent ?: error("Missing clickable parent")
        return current
    }
    private fun click(value: String) {
        check(clickable(node(scroll = true) { it.text?.toString()?.replace("\u200b", "") == value })
            .performAction(AccessibilityNodeInfo.ACTION_CLICK))
        Thread.sleep(400)
    }
    private fun actions(name: String) {
        check(clickable(node(scroll = true) { it.contentDescription?.toString() == "Aktionen für $name" })
            .performAction(AccessibilityNodeInfo.ACTION_CLICK))
        Thread.sleep(300)
    }
    private fun setName(value: String) {
        check(node { it.isEditable }.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, value)
        }))
        Thread.sleep(250)
    }
    private fun awaitCondition(timeout: Int = 300, condition: () -> Boolean) {
        repeat(timeout) {
            var ready = false
            runOnMainSync { ready = condition() }
            if (ready) return
            Thread.sleep(100)
        }
        error("State condition timed out")
    }
    private fun screenshot(name: String) {
        uiAutomation.takeScreenshot()?.let { bitmap ->
            File(targetContext.filesDir, "workspace-$name.png").outputStream().use {
                bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
            }
            bitmap.recycle()
        }
    }
}

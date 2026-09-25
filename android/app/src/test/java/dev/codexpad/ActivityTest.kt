package dev.codexpad

import dev.codexpad.data.Timeline
import dev.codexpad.model.*
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class ActivityTest {
    private fun item(body: String) = Wire.message(JSONObject(body))
    private fun command(status: String = "inProgress", output: String = "null", id: String = "c") =
        """{"id":"$id","type":"commandExecution","command":"pwd","cwd":"/tmp","status":"$status","aggregatedOutput":$output,"exitCode":null,"durationMs":null}"""
    private fun history(vararg items: Message, status: String = "inProgress") = CodexThread("t", turns = listOf(Turn("u", status, items.toList())))
    private fun event(body: String = "") = JSONObject("""{"threadId":"t","turnId":"u"$body}""")
    private fun Timeline.start(body: String) = event("item/started", event(",\"item\":$body"))
    private fun Timeline.finish(body: String) = event("item/completed", event(",\"item\":$body"))
    private fun Timeline.output(text: String) = event("item/commandExecution/outputDelta", event().put("itemId", "c").put("delta", text))
    private fun Timeline.command() = turns.single().items.first { it.id == "c" }.activity as Activity.Command

    @Test fun commandFinalOutputReplacesDeltasIncludingMissingOutput() {
        val live = Timeline(history()).start(command()).output("one").output("two")
        assertEquals("onetwo", live.command().liveOutput)
        assertNull(live.command().output)
        val complete = live.finish(command("completed", "\"onetwo\"").replace("\"exitCode\":null", "\"exitCode\":7").replace("\"durationMs\":null", "\"durationMs\":150"))
        assertEquals("onetwo", complete.command().output)
        assertEquals(7, complete.command().exitCode)
        assertEquals(150L, complete.command().durationMs)
        assertNull(complete.command().liveOutput)
        assertEquals(complete, complete.output("late"))
        assertEquals(complete, complete.start(command()))
        assertNull(live.finish(command("completed")).command().liveOutput)
        assertNull(live.finish(command("completed")).command().output)
    }

    @Test fun reconnectAndActiveHistoryPreferFullItemsWithoutDroppingOtherCommands() {
        val base = history(item(command(output = "\"prefix\"")), item(command(id = "other")))
        val live = Timeline(base).output("prefix")
        assertEquals(2, live.turns.single().items.size)
        assertEquals("prefix", live.command().liveOutput) // never prefix + overlapping replay
        val refreshed = live.reconcile(history(item(command(output = "\"full\"")), item(command(id = "other"))))
        assertEquals("full", refreshed.command().output)
        assertNull(refreshed.command().liveOutput)
        val reconnect = live.reconcile(base, true).output("new slice")
        assertEquals("new slice", reconnect.command().liveOutput)
        val final = reconnect.reconcile(history(item(command("completed", "\"authoritative\"")), status = "completed"))
        assertTrue(final.live.isEmpty())
        assertEquals("authoritative", final.command().output)
        assertEquals(final, final.output("stale"))
    }

    @Test fun terminalItemInActiveHistoryRejectsQueuedOldCompletions() {
        val final = Timeline(history(item(command("completed", "\"final\""))))
        assertEquals(final, final.finish(command("completed", "\"old\"")))
        assertEquals(final, final.output("late"))
    }

    @Test fun fullStartedStateReplacesPriorStateAndMissingStartsAreNotInvented() {
        val empty = Timeline(history())
        assertEquals(empty, empty.output("orphan"))
        assertEquals("replacement", empty.start(command()).output("partial").start(command(output = "\"replacement\"")).command().output)
        assertNull(empty.start(command()).output("partial").start(command()).command().liveOutput)
    }

    @Test fun mcpAndDynamicRespectDistinctResultShapesAndUnknownSuccess() {
        val mcp = item("""{"id":"m","type":"mcpToolCall","server":"docs","tool":"find","status":"completed","arguments":{"q":"hello"},"result":{"content":[{"type":"text","text":"found"}],"structuredContent":{"count":1}}}""").activity as Activity.Mcp
        assertEquals("docs", mcp.server)
        assertTrue(mcp.arguments!!.contains("hello"))
        assertTrue(mcp.result!!.contains("found"))
        assertTrue(mcp.result.contains("count"))
        val failed = item("""{"id":"m","type":"mcpToolCall","status":"failed","error":{"message":"offline"}}""").activity as Activity.Mcp
        assertEquals("offline", failed.error)
        assertNull(failed.result)
        val missing = item("""{"id":"m","type":"mcpToolCall","status":"completed","result":null}""").activity as Activity.Mcp
        assertNull(missing.result)
        val dynamic = item("""{"id":"d","type":"dynamicToolCall","tool":"test","status":"completed","success":false,"contentItems":[{"type":"inputText","text":"failure"},{"type":"inputImage","imageUrl":"data:SECRET"}]}""").activity as Activity.Dynamic
        assertEquals(false, dynamic.success)
        assertTrue(dynamic.content!!.contains("failure"))
        assertFalse(dynamic.content.contains("SECRET"))
        val absent = item("""{"id":"d","type":"dynamicToolCall","status":"completed"}""").activity as Activity.Dynamic
        assertNull(absent.success)
        assertNull(absent.content)
    }

    @Test fun patchesReplaceChangesAndDoNotCompleteStartedItem() {
        val initial = """{"id":"f","type":"fileChange","status":"inProgress","changes":[{"path":"a","kind":{"type":"add"},"diff":"+a"}]}"""
        val live = Timeline(history()).start(initial)
        val update = event().put("itemId", "f").put("changes", org.json.JSONArray("""[
            {"path":"b","kind":{"type":"update","move_path":"c"},"diff":"-b\n+c"},
            {"path":"d","kind":{"type":"delete"},"diff":"-d"}]"""))
        val patched = live.event("item/fileChange/patchUpdated", update)
        val files = patched.turns.single().items.single().activity as Activity.Files
        assertEquals(listOf("b", "d"), files.changes.map { it.path })
        assertEquals("c", files.changes.first().movePath)
        assertEquals("inProgress", files.status)
        val done = patched.finish(initial.replace("inProgress", "completed"))
        assertEquals(done, done.event("item/fileChange/patchUpdated", update))
        assertEquals(listOf("a"), (done.turns.single().items.single().activity as Activity.Files).changes.map { it.path })
        val refreshed = patched.reconcile(history(item(initial)), true)
        assertEquals(listOf("a"), (refreshed.turns.single().items.single().activity as Activity.Files).changes.map { it.path })
    }
}

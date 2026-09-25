package dev.codexpad

import dev.codexpad.data.Timeline
import dev.codexpad.model.*
import dev.codexpad.network.SseParser
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class TimelineTest {
    private fun delta(text: String, turnId: String = "turn") = JSONObject()
        .put("threadId", "thread").put("turnId", turnId).put("itemId", "live-agent").put("delta", text)
    private fun history(status: String, text: String) = CodexThread("thread", status = "idle", turns = listOf(
        Turn("turn", status, listOf(Message("legacy-item-2", "agentMessage", text)))))

    @Test fun completedHistoryReplacesIncompleteStreamDespiteDifferentItemIds() {
        val streaming = Timeline(CodexThread("thread"))
            .event("item/agentMessage/delta", delta("1\n"))
            .event("item/agentMessage/delta", delta("8\n"))
        val reconciled = streaming.reconcile(history("completed", "1\n2\n3\n4\n5\n6\n7\n8\n"))
        assertTrue(reconciled.live.isEmpty())
        assertEquals(1, reconciled.turns.single().items.size)
        assertTrue(reconciled.turns.single().items.single().text.contains("2\n3"))
        assertEquals(reconciled, reconciled.event("item/agentMessage/delta", delta("late")))
        assertEquals(reconciled, reconciled.event("turn/started", JSONObject()
            .put("threadId", "thread").put("turn", JSONObject().put("id", "turn"))))
        assertEquals(reconciled, reconciled.acknowledge(Turn("turn", "inProgress", emptyList())))
    }

    @Test fun activeSnapshotIsNeverConcatenatedWithOverlappingQueuedDeltas() {
        val timeline = Timeline().reconcile(history("inProgress", "Hello"), true)
            .event("item/agentMessage/delta", delta("Hello"))
        assertEquals("Hello", timeline.turns.single().items.single().text)
        val reset = timeline.reconcile(history("inProgress", "Hello world"), true)
        assertTrue(reset.live.isEmpty())
        assertEquals("Hello world", reset.turns.single().items.single().text)
    }

    @Test fun foreignThreadAndUnknownEventsDoNotChangeTimeline() {
        val timeline = Timeline(CodexThread("thread"))
        assertEquals(timeline, timeline.event("item/agentMessage/delta", delta("x").put("threadId", "other")))
        assertEquals(timeline, timeline.event("future/event", delta("x")))
    }

    @Test fun parseActualLegacyShapesAndTolerateUnknownItemTypes() {
        val thread = Wire.threadEnvelope(JSONObject("""{"thread":{"id":"t","preview":null,
            "status":{"type":"idle"},"turns":[{"id":"turn","status":"failed",
            "error":{"message":"Model unavailable"},"items":[
            {"id":"item-1","type":"userMessage","content":[{"type":"text","text":"Hi"}]},
            {"id":"item-2","type":"agentMessage","text":"Hello"},
            {"id":"item-3","type":"futureTool","status":"completed"}]}]}}"""))
        assertEquals("", thread.preview)
        assertEquals(listOf("Hi", "Hello", "futureTool: completed"), thread.turns.single().items.map { it.text })
        assertEquals("Model unavailable", thread.turns.single().error)
        assertTrue(thread.turns.single().terminal)
    }

    @Test fun sseHandlesMultilineDataCommentsAndDropsUnterminatedFrame() {
        val parser = SseParser()
        assertNull(parser.line(": heartbeat"))
        assertNull(parser.line(""))
        parser.line("event: snapshot")
        parser.line("data: {\"thread\":")
        parser.line("data: {\"id\":\"t\"}}")
        val frame = parser.line("")!!
        assertEquals("snapshot", frame.event)
        assertEquals("t", JSONObject(frame.data).getJSONObject("thread").getString("id"))
        parser.line("data: unfinished")
        // No empty line: caller must not dispatch anything on EOF.
    }
}

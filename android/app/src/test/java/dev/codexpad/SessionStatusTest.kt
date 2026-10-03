package dev.codexpad

import dev.codexpad.data.Timeline
import dev.codexpad.data.ThreadState
import dev.codexpad.model.*
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class SessionStatusTest {
    @Test fun contextMatchesCodexBaselineFormulaRatherThanAbsoluteRemainder() {
        fun usage(tokens: Long, size: Long = 112000) = ContextStatus.parse(JSONObject()
            .put("last", JSONObject().put("totalTokens", tokens))
            .put("total", JSONObject().put("totalTokens", 9999999)).put("modelContextWindow", size))
        assertEquals(100, usage(0).remainingPercent)
        assertEquals(100, usage(12000).remainingPercent)
        assertEquals(89, usage(23000).remainingPercent)
        assertEquals(50, usage(62000).remainingPercent)
        assertEquals(0, usage(112000).remainingPercent)
        assertEquals(0, usage(999999).remainingPercent)
        assertEquals(0, usage(0, 12000).remainingPercent)
        assertNull(ContextStatus.parse(JSONObject("""{"last":{"totalTokens":1}}""")).remainingPercent)
        assertNull(ContextStatus.parse(JSONObject("""{"modelContextWindow":112000}""")).remainingPercent)
        assertTrue(usage(23000).outdated().label.contains("veraltet"))
    }

    @Test fun weekUsesCodexBucketAndWindowDurationWithUnknownsAndExpiry() {
        val snapshot = JSONObject("""{"rateLimits":{"secondary":{"usedPercent":1,"windowDurationMins":10080}},
            "rateLimitsByLimitId":{"codex":{"primary":{"usedPercent":6,"windowDurationMins":10080,"resetsAt":1000},
            "secondary":{"usedPercent":99,"windowDurationMins":300}},"other":{"primary":{"usedPercent":88,"windowDurationMins":10080}}}}""")
        val limit = WeeklyLimit.parse(snapshot)
        assertEquals(94, limit.remainingPercent)
        assertFalse(limit.label(999).contains("veraltet"))
        assertTrue(limit.label(1000).contains("veraltet"))
        assertTrue(limit.outdated().label(0).contains("veraltet"))
        assertNull(WeeklyLimit.parse(JSONObject("""{"rateLimitsByLimitId":{},"rateLimits":{"secondary":{"usedPercent":6,"windowDurationMins":10080}}}""")).remainingPercent)
        assertNull(WeeklyLimit.parse(JSONObject("""{"rateLimits":{"secondary":{"usedPercent":6,"windowDurationMins":43200}}}""")).remainingPercent)
        assertNull(WeeklyLimit.update(JSONObject("""{"rateLimits":{"limitId":"other","secondary":{"usedPercent":6,"windowDurationMins":10080}}}""")))
        assertNull(WeeklyLimit.parse(JSONObject("""{"rateLimits":{"secondary":{"usedPercent":null,"windowDurationMins":10080}}}""")).remainingPercent)
        assertEquals(94, WeeklyLimit.parse(JSONObject("""{"rateLimits":{"secondary":{"usedPercent":6.5,"windowDurationMins":10080}}}""")).remainingPercent)
        assertEquals(0, WeeklyLimit.parse(JSONObject("""{"rateLimits":{"secondary":{"usedPercent":120,"windowDurationMins":10080}}}""")).remainingPercent)
    }

    @Test fun lifecycleAndHistoryPreserveAuthoritativeTurnTimes() {
        val thread = CodexThread("t", turns = listOf(Turn("u", "inProgress", emptyList())))
        val start = JSONObject("""{"threadId":"t","turn":{"id":"u","status":"inProgress","startedAt":1000}}""")
        val timeline = Timeline(thread).event("turn/started", start)
        assertEquals("1:05", timeline.turns.single().runningDuration(1065))
        assertNull(timeline.turns.single().runningDuration(900))
        val completed = timeline.event("turn/completed", JSONObject("""{"threadId":"t","turn":{"id":"u","status":"completed","completedAt":1070,"durationMs":70000}}"""))
        assertTrue(completed.turns.single().terminal)
        assertEquals(1000L, completed.turns.single().startedAt)
        assertEquals(70000L, completed.turns.single().durationMs)
        assertEquals(completed, completed.event("turn/started", start))
        val unknown = Wire.turn(JSONObject("""{"id":"u","status":"inProgress","startedAt":null,"durationMs":null}"""))
        assertNull(unknown.runningDuration(1065))
    }

    private fun state(turns: List<Turn> = emptyList(), status: String = "idle") = ThreadState(
        timeline = Timeline(CodexThread("t", status = status, turns = turns, model = "gpt-6-astra", reasoningEffort = "ultra")),
        usage = ContextStatus(45_000, 112_000), weekly = WeeklyLimit(93), connected = true,
    )

    @Test fun idleHasOnlyContextAndWeekWithModelAndEffortPreserved() {
        val idle = state()
        assertEquals("gpt-6-astra · ultra", idle.modelLabel)
        assertEquals("Kontext 67 % frei · Woche 93 % frei", idle.statusLabel(1102))
        assertFalse(idle.statusLabel(1102).contains("Kein laufender Turn"))
        assertEquals(idle.statusLabel(1102), idle.statusLabel(9999))
        assertNull(idle.runningTurn)
    }

    @Test fun runningDurationIsQuietAndRequiresAnUnambiguousStructuredStartTime() {
        val turn = Turn("u", "inProgress", emptyList(), startedAt = 1000)
        assertEquals("Kontext 67 % frei · Woche 93 % frei · 1:42", state(listOf(turn)).statusLabel(1102))
        for (start in listOf(null, -1L, 1200L)) {
            assertEquals(state().statusLabel(1102), state(listOf(turn.copy(startedAt = start))).statusLabel(1102))
        }
        assertEquals(state().statusLabel(1102), state(status = "active").statusLabel(1102))
        assertEquals(state().statusLabel(1102), state(listOf(turn.copy(status = "unknown"))).statusLabel(1102))
        assertEquals(state().statusLabel(1102), state(listOf(turn, turn.copy(id = "v"))).statusLabel(1102))
    }

    @Test fun everyTerminalEventImmediatelyRemovesDurationDespiteStaleActiveThreadStatus() {
        for (status in listOf("completed", "failed", "cancelled", "stopped", "interrupted")) {
            val active = state(listOf(Turn("u", "inProgress", emptyList(), startedAt = 1000)), "active")
            assertTrue(active.statusLabel(1102).endsWith("1:42"))
            val timeline = active.timeline.event("turn/completed", JSONObject()
                .put("threadId", "t").put("turn", JSONObject().put("id", "u").put("status", status)))
            val ended = active.copy(timeline = timeline)
            assertTrue(ended.timeline.turns.single().terminal)
            assertNull(ended.runningTurn)
            assertEquals(state().statusLabel(1102), ended.statusLabel(1102))
            assertEquals(ended.statusLabel(1102), ended.statusLabel(1202))
            val late = ended.copy(timeline = ended.timeline.event("turn/started", JSONObject()
                .put("threadId", "t").put("turn", JSONObject().put("id", "u").put("status", "inProgress").put("startedAt", 1000))))
            assertEquals(ended.statusLabel(1202), late.statusLabel(1202))
        }
    }

    @Test fun reconnectHistoryRestoresActiveDurationAndClearsOfflineCompletion() {
        val turn = Turn("u", "inProgress", emptyList(), startedAt = 1000)
        val initial = state(listOf(turn))
        val offline = initial.copy(connected = false)
        assertEquals("Kontext 67 % frei · Woche 93 % frei · 1:42 · letzter Stand", offline.statusLabel(1102))
        val resumed = offline.copy(connected = true, timeline = offline.timeline.reconcile(initial.timeline.thread!!, resetLive = true))
        assertEquals(initial.statusLabel(1102), resumed.statusLabel(1102))
        val completed = resumed.copy(timeline = resumed.timeline.reconcile(initial.timeline.thread!!.copy(
            turns = listOf(turn.copy(status = "completed", completedAt = 1103, durationMs = 103000))), resetLive = true))
        assertEquals(state().statusLabel(1200), completed.statusLabel(1200))
    }
}

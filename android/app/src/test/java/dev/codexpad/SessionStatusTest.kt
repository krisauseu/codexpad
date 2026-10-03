package dev.codexpad

import dev.codexpad.data.Timeline
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
        assertEquals("Turn u · 1:05", timeline.turns.single().runningLabel(1065))
        assertEquals("Turn u · 0:00", timeline.turns.single().runningLabel(900))
        val completed = timeline.event("turn/completed", JSONObject("""{"threadId":"t","turn":{"id":"u","status":"completed","completedAt":1070,"durationMs":70000}}"""))
        assertTrue(completed.turns.single().terminal)
        assertEquals(1000L, completed.turns.single().startedAt)
        assertEquals(70000L, completed.turns.single().durationMs)
        assertEquals(completed, completed.event("turn/started", start))
        val unknown = Wire.turn(JSONObject("""{"id":"u","status":"inProgress","startedAt":null,"durationMs":null}"""))
        assertTrue(unknown.runningLabel(1065).contains("Dauer unbekannt"))
    }
}

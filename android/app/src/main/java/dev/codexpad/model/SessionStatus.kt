package dev.codexpad.model

import org.json.JSONObject
import kotlin.math.roundToInt

// Codex rust-v0.160.0 TokenUsage::percent_of_context_window_remaining.
// See docs/research/codex-statusline.md. Missing measurements stay unknown.
data class ContextStatus(val used: Long? = null, val window: Long? = null, val stale: Boolean = false) {
    val remainingPercent: Int? get() {
        val tokens = used ?: return null
        val size = window ?: return null
        if (size <= 12_000) return 0
        val effective = size - 12_000
        val consumed = (tokens - 12_000).coerceAtLeast(0)
        return ((effective - consumed).coerceAtLeast(0).toDouble() / effective.toDouble() * 100.0)
            .coerceIn(0.0, 100.0).roundToInt()
    }
    val label get() = "Kontext ${remainingPercent?.let { "$it % frei" } ?: "unbekannt"}${if (stale) " · veraltet" else ""}"
    fun outdated() = copy(stale = used != null || window != null)
    companion object {
        fun parse(json: JSONObject?) = ContextStatus(
            json?.optJSONObject("last")?.nonnegativeLong("totalTokens"),
            json?.nonnegativeLong("modelContextWindow")?.takeIf { it > 0 },
        )
    }
}

data class WeeklyLimit(val remainingPercent: Int? = null, val resetsAt: Long? = null, val stale: Boolean = false) {
    fun label(nowSeconds: Long): String {
        val outdated = stale || (resetsAt != null && nowSeconds >= resetsAt)
        return "Woche ${remainingPercent?.let { "$it % frei" } ?: "unbekannt"}${if (outdated && remainingPercent != null) " · veraltet" else ""}"
    }
    fun outdated() = copy(stale = remainingPercent != null)
    companion object {
        // Modern maps are authoritative; never substitute a different metered bucket.
        fun parse(json: JSONObject?): WeeklyLimit {
            val buckets = json?.optJSONObject("rateLimitsByLimitId")
            val bucket = if (buckets != null) buckets.optJSONObject("codex")
                else json?.optJSONObject("rateLimits")?.takeIf { isCodex(it) }
            return fromBucket(bucket)
        }
        // Notifications carry a single bucket; other buckets must not erase Codex state.
        fun update(json: JSONObject): WeeklyLimit? {
            if (json.optJSONObject("rateLimitsByLimitId") != null) return parse(json)
            val bucket = json.optJSONObject("rateLimits") ?: return null
            return if (isCodex(bucket)) fromBucket(bucket) else null
        }
        private fun isCodex(json: JSONObject) = json.isNull("limitId") || json.optString("limitId") == "codex"
        private fun fromBucket(bucket: JSONObject?): WeeklyLimit {
            val window = listOf("primary", "secondary").mapNotNull { bucket?.optJSONObject(it) }
                .firstOrNull { (it.opt("windowDurationMins") as? Number)?.toDouble()?.let { minutes ->
                    minutes in (10080.0 * 0.95)..(10080.0 * 1.05)
                } == true } ?: return WeeklyLimit()
            val used = (window.opt("usedPercent") as? Number)?.toDouble()?.takeIf { it.isFinite() }
                ?: return WeeklyLimit()
            // Rust's fixed precision formatting uses round-to-even.
            return WeeklyLimit(Math.rint((100.0 - used).coerceIn(0.0, 100.0)).toInt(), window.nonnegativeLong("resetsAt"))
        }
    }
}

internal fun JSONObject.nonnegativeLong(key: String): Long? =
    (opt(key) as? Number)?.toDouble()?.takeIf { it.isFinite() && it >= 0 && it < Long.MAX_VALUE.toDouble() && it % 1.0 == 0.0 }?.toLong()

fun Turn.runningLabel(nowSeconds: Long): String {
    val duration = startedAt?.let { (nowSeconds - it).coerceAtLeast(0) }
    val elapsed = duration?.let { "${it / 60}:${(it % 60).toString().padStart(2, '0')}" } ?: "Dauer unbekannt"
    return "Turn ${id.take(8)} · $elapsed"
}

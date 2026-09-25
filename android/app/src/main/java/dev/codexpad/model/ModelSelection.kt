package dev.codexpad.model

import org.json.JSONObject

data class ReasoningOption(val effort: String, val description: String)
data class CatalogModel(
    val id: String, val model: String, val name: String, val description: String,
    val isDefault: Boolean, val efforts: List<ReasoningOption>, val defaultEffort: String?,
) {
    fun compatibleEffort(previous: String?): String? =
        efforts.firstOrNull { it.effort == previous }?.effort
            ?: efforts.firstOrNull { it.effort == defaultEffort }?.effort
            ?: efforts.firstOrNull()?.effort

    companion object {
        fun parse(json: JSONObject) = CatalogModel(
            json.getString("id"), json.getString("model"), json.getString("displayName"),
            json.optString("description"), json.optBoolean("isDefault"),
            json.getJSONArray("supportedReasoningEfforts").objects().map {
                ReasoningOption(it.getString("reasoningEffort"), it.optString("description"))
            }, if (json.isNull("defaultReasoningEffort")) null else json.getString("defaultReasoningEffort"),
        )
    }
}

data class ContextUsage(val used: Long? = null, val window: Long? = null, val stale: Boolean = false) {
    val remaining: Long? get() = if (used != null && window != null) (window - used).coerceAtLeast(0) else null
    val label: String get() = "Kontext${if (stale) " · veraltet" else ""}: verwendet ${used ?: "unbekannt"}" +
        " · Fenster ${window ?: "unbekannt"} · Rest (Schätzung) ${remaining ?: "unbekannt"}"
    fun outdated() = copy(stale = used != null || window != null)
    companion object {
        fun parse(json: JSONObject?): ContextUsage {
            fun positiveOrZero(obj: JSONObject?, key: String): Long? =
                (obj?.opt(key) as? Number)?.toLong()?.takeIf { it >= 0 }
            return ContextUsage(positiveOrZero(json?.optJSONObject("last"), "totalTokens"),
                positiveOrZero(json, "modelContextWindow")?.takeIf { it > 0 })
        }
    }
}

data class ModelReroute(val turnId: String, val from: String, val to: String)

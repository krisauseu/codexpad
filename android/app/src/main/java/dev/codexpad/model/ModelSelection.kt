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

data class ModelReroute(val turnId: String, val from: String, val to: String)

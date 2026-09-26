package dev.codexpad.model

import org.json.JSONArray
import org.json.JSONObject

data class Workspace(val id: String, val name: String)
data class Message(val id: String, val type: String, val text: String, val activity: Activity? = null,
    val completedEvent: Boolean = false, val images: Int = 0) {
    val activityTerminal get() = completedEvent || activity?.status in setOf("completed", "failed", "declined", "interrupted")
    val isCompaction get() = type == "contextCompaction"
}
data class Turn(val id: String, val status: String, val items: List<Message>, val error: String? = null) {
    val terminal get() = status in setOf("completed", "failed", "interrupted")
}
data class CodexThread(
    val id: String,
    val preview: String = "",
    val status: String = "unknown",
    val turns: List<Turn> = emptyList(),
    val model: String? = null,
    val reasoningEffort: String? = null,
)

fun JSONArray.objects(): List<JSONObject> = (0 until length()).map { getJSONObject(it) }
private fun JSONObject.optionalText(key: String) = if (isNull(key)) null else optString(key).takeIf { it.isNotEmpty() }

object Wire {
    fun threadEnvelope(json: JSONObject) = thread(json.getJSONObject("thread"))
    fun thread(json: JSONObject) = CodexThread(
        id = json.getString("id"),
        preview = json.optionalText("preview").orEmpty(),
        status = json.optJSONObject("status")?.optString("type", "unknown") ?: "unknown",
        model = json.optionalText("model"),
        reasoningEffort = json.optionalText("reasoningEffort"),
        turns = json.optJSONArray("turns")?.objects()?.map(::turn).orEmpty(),
    )
    fun turn(json: JSONObject) = Turn(
        id = json.getString("id"),
        status = json.optString("status", "unknown"),
        items = json.optJSONArray("items")?.objects()?.map(::message).orEmpty(),
        error = json.optJSONObject("error")?.optionalText("message"),
    )
    fun message(json: JSONObject): Message {
        val type = json.getString("type")
        val content = json.optJSONArray("content")?.objects().orEmpty()
        val text = when (type) {
            "userMessage" -> content.filter { it.optString("type") == "text" }
                .joinToString("\n") { it.optString("text") }
            "agentMessage" -> json.optString("text")
            "contextCompaction" -> "Kontextzusammenfassung · die Gesprächshistorie bleibt erhalten"
            else -> "${type}: ${json.optionalText("status") ?: "Eintrag im Serververlauf"}"
        }
        return Message(json.getString("id"), type, text, ActivityWire.parse(json),
            images = content.count { it.optString("type") in setOf("localImage", "image") })
    }
}

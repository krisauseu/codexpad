package dev.codexpad.model

import org.json.JSONArray
import org.json.JSONObject

data class Workspace(val id: String, val name: String)
data class Message(val id: String, val type: String, val text: String, val activity: Activity? = null,
    val completedEvent: Boolean = false, val images: Int = 0) {
    val activityTerminal get() = completedEvent || activity?.status in setOf("completed", "failed", "declined", "interrupted")
    val isCompaction get() = type == "contextCompaction"
}
data class Artifact(val id: String, val name: String, val mimeType: String, val size: Long)
data class InputOption(val label: String, val description: String)
data class InputQuestion(val id: String, val header: String, val question: String,
    val isOther: Boolean, val isSecret: Boolean, val options: List<InputOption>)
data class InputRequest(val id: String, val threadId: String, val turnId: String, val status: String,
    val isBlocking: Boolean, val questions: List<InputQuestion>)
data class InputAnswer(val value: String, val isOption: Boolean = false)

data class Turn(val id: String, val status: String, val items: List<Message>, val error: String? = null, val artifacts: List<Artifact> = emptyList(),
    val startedAt: Long? = null, val completedAt: Long? = null, val durationMs: Long? = null) {
    val terminal get() = status in setOf("completed", "failed", "interrupted")
}
data class CodexThread(
    val id: String,
    val preview: String = "",
    val status: String = "unknown",
    val turns: List<Turn> = emptyList(),
    val model: String? = null,
    val reasoningEffort: String? = null,
    val pendingRequests: List<InputRequest> = emptyList(),
)

fun JSONArray.objects(): List<JSONObject> = (0 until length()).map { getJSONObject(it) }
private fun JSONObject.optionalText(key: String) = if (isNull(key)) null else optString(key).takeIf { it.isNotEmpty() }

object Wire {
    fun threadEnvelope(json: JSONObject) = thread(json.getJSONObject("thread"))
    fun thread(json: JSONObject) = CodexThread(
        id = json.getString("id"),
        preview = json.optionalText("preview").orEmpty().substringBefore("[CodexPad results ").trimEnd(),
        status = json.optJSONObject("status")?.optString("type", "unknown") ?: "unknown",
        model = json.optionalText("model"),
        reasoningEffort = json.optionalText("reasoningEffort"),
        turns = json.optJSONArray("turns")?.objects()?.map(::turn).orEmpty(),
        pendingRequests = json.optJSONArray("pendingRequests")?.objects()?.map { request ->
            InputRequest(request.getString("id"), request.getString("threadId"), request.getString("turnId"),
                request.getString("status"), request.getBoolean("isBlocking"),
                request.getJSONArray("questions").objects().map { question ->
                    InputQuestion(question.getString("id"), question.getString("header"), question.getString("question"),
                        question.optBoolean("isOther"), question.optBoolean("isSecret"),
                        question.optJSONArray("options")?.objects()?.map {
                            InputOption(it.getString("label"), it.getString("description"))
                        }.orEmpty())
                })
        }.orEmpty(),
    )
    fun turn(json: JSONObject) = Turn(
        id = json.getString("id"),
        status = json.optString("status", "unknown"),
        items = json.optJSONArray("items")?.objects()?.map(::message).orEmpty(),
        error = json.optJSONObject("error")?.optionalText("message"),
        startedAt = json.nonnegativeLong("startedAt"),
        completedAt = json.nonnegativeLong("completedAt"),
        durationMs = json.nonnegativeLong("durationMs"),
        artifacts = json.optJSONArray("artifacts")?.objects()?.map {
            Artifact(it.getString("id"), it.getString("name"), it.getString("mimeType"), it.getLong("size"))
        }.orEmpty(),
    )
    fun message(json: JSONObject): Message {
        val type = json.getString("type")
        val content = json.optJSONArray("content")?.objects().orEmpty()
        val text = when (type) {
            "userMessage" -> content.filter { it.optString("type") == "text" && !it.optString("text").startsWith("[CodexPad results ") }
                .joinToString("\n") { it.optString("text") }
            "agentMessage" -> json.optString("text")
            "contextCompaction" -> "Kontextzusammenfassung · die Gesprächshistorie bleibt erhalten"
            else -> "${type}: ${json.optionalText("status") ?: "Eintrag im Serververlauf"}"
        }
        return Message(json.getString("id"), type, text, ActivityWire.parse(json),
            images = content.count { it.optString("type") in setOf("localImage", "image") })
    }
}

package dev.codexpad.model

import org.json.JSONArray
import org.json.JSONObject

/** Immutable projections of App-Server items; live supplements live inside the same timeline item. */
sealed interface Activity {
    val status: String
    data class Command(
        val command: String?, val cwd: String?, override val status: String,
        val output: String?, val exitCode: Int?, val durationMs: Long?,
        val liveOutput: String? = null,
    ) : Activity
    data class Mcp(
        val server: String?, val tool: String?, override val status: String,
        val arguments: String?, val result: String?, val error: String?, val rawResult: String?,
    ) : Activity
    data class Dynamic(
        val namespace: String?, val tool: String?, override val status: String,
        val arguments: String?, val content: String?, val success: Boolean?, val rawResult: String?,
    ) : Activity
    data class Files(val changes: List<FileEdit>, override val status: String) : Activity
}

data class FileEdit(val path: String, val kind: String, val movePath: String?, val diff: String?)

internal fun JSONObject.valueText(key: String): String? = if (isNull(key)) null else opt(key)?.let(::readable)
private fun readable(value: Any): String = when (value) {
    is JSONObject -> value.toString(2)
    is JSONArray -> value.toString(2)
    else -> value.toString()
}

object ActivityWire {
    fun changes(json: JSONArray?): List<FileEdit> = json?.objects()?.map {
        val kind = it.optJSONObject("kind")
        FileEdit(it.optString("path", "Pfad unbekannt"), kind?.optString("type", "unknown") ?: "unknown",
            kind?.valueText("move_path"), it.valueText("diff"))
    }.orEmpty()

    // Extract text/resources, label binary content without putting base64 into the card body.
    private fun content(array: JSONArray?): String? = array?.let { values ->
        (0 until values.length()).joinToString("\n\n") { index ->
            val item = values.optJSONObject(index)
            if (item == null) readable(values.get(index)) else when (val type = item.optString("type")) {
                "text", "inputText" -> item.valueText("text").orEmpty()
                "resource" -> item.optJSONObject("resource")?.let {
                    listOfNotNull(it.valueText("uri"), it.valueText("text") ?: "Ressource ohne Text").joinToString("\n")
                } ?: "Ressource"
                "resource_link" -> listOfNotNull(item.valueText("name"), item.valueText("uri")).joinToString(" · ")
                "image", "inputImage" -> "Bildinhalt (Rohdetails verfügbar)"
                "audio", "inputAudio" -> "Audioinhalt (Rohdetails verfügbar)"
                else -> "Inhalt: ${type.ifEmpty { "unbekannt" }} (Rohdetails verfügbar)"
            }
        }
    }

    fun parse(json: JSONObject): Activity? {
        val status = json.valueText("status") ?: "unknown"
        return when (json.optString("type")) {
            "commandExecution" -> Activity.Command(json.valueText("command"), json.valueText("cwd"), status,
                json.valueText("aggregatedOutput"), (json.opt("exitCode") as? Number)?.toInt(),
                (json.opt("durationMs") as? Number)?.toLong())
            "mcpToolCall" -> {
                val result = json.optJSONObject("result")
                Activity.Mcp(json.valueText("server"), json.valueText("tool"), status, json.valueText("arguments"),
                    result?.let { listOfNotNull(content(it.optJSONArray("content")), it.valueText("structuredContent"))
                        .joinToString("\n\n") },
                    json.optJSONObject("error")?.valueText("message") ?: json.valueText("error"), json.valueText("result"))
            }
            "dynamicToolCall" -> Activity.Dynamic(json.valueText("namespace"), json.valueText("tool"), status,
                json.valueText("arguments"), content(json.optJSONArray("contentItems")),
                json.opt("success") as? Boolean, json.valueText("contentItems"))
            "fileChange" -> Activity.Files(changes(json.optJSONArray("changes")), status)
            else -> null
        }
    }
}

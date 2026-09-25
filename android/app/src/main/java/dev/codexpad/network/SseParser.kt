package dev.codexpad.network

data class SseFrame(val event: String, val data: String)

/** Incremental framing only. Heartbeats, ids and retry hints are not a replay contract. */
class SseParser {
    private var event = "message"
    private val data = mutableListOf<String>()

    fun line(line: String): SseFrame? {
        if (line.isEmpty()) {
            val frame = if (data.isEmpty()) null else SseFrame(event, data.joinToString("\n"))
            event = "message"
            data.clear()
            return frame
        }
        if (line.startsWith(":")) return null
        val field = line.substringBefore(':')
        val value = line.substringAfter(':', "").removePrefix(" ")
        when (field) {
            "event" -> event = value
            "data" -> data.add(value)
        }
        return null
    }
}

package dev.codexpad.data

import dev.codexpad.model.CodexThread
import org.json.JSONArray
import org.json.JSONObject

/** No RPC request ID exists. Only a new structured compaction turn can confirm this attempt. */
data class Compaction(
    val baseline: Set<String>,
    val turnId: String? = null,
    val phase: String = "unknown",
) {
    val pending get() = phase !in setOf("completed", "failed", "interrupted")
    val label get() = when (phase) {
        "running", "requested" -> "Kontext wird komprimiert …"
        "completed" -> "Kontext komprimiert"
        "failed" -> "Kontextkomprimierung fehlgeschlagen"
        "interrupted" -> "Kontextkomprimierung unterbrochen"
        else -> "Kontextkomprimierung · Ausgang unbekannt · Serverabgleich ausstehend"
    }
    fun reconcile(history: CodexThread): Compaction {
        if (!pending) return this
        val turn = (if (turnId != null) history.turns.singleOrNull { it.id == turnId }
            else history.turns.filter { it.id !in baseline && it.items.any { item -> item.isCompaction } }.singleOrNull())
            ?: return copy(phase = "unknown")
        return copy(turnId = turn.id, phase = if (turn.terminal) turn.status else "running")
    }
    fun json(): String = JSONObject().put("baseline", JSONArray(baseline.toList()))
        .put("turnId", turnId).put("phase", phase).toString()

    companion object {
        fun restore(raw: String?): Compaction? = raw?.let {
            val json = JSONObject(it)
            val ids = json.getJSONArray("baseline")
            Compaction((0 until ids.length()).map { index -> ids.getString(index) }.toSet(),
                if (json.isNull("turnId")) null else json.getString("turnId"),
                json.getString("phase").let { phase ->
                    if (phase in setOf("completed", "failed", "interrupted")) phase else "unknown"
                })
        }
    }
}

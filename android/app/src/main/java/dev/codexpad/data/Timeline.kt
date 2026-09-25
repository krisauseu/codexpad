package dev.codexpad.data

import dev.codexpad.model.*
import org.json.JSONObject

data class Timeline(
    val thread: CodexThread? = null,
    val live: Map<String, Turn> = emptyMap(),
) {
    val busy get() = thread?.status == "active" || turns.any { !it.terminal }
    val turns: List<Turn> get() {
        val history = thread?.turns.orEmpty()
        return history.map { turn ->
            val streamed = live[turn.id]
            if (turn.terminal || streamed == null) turn else {
                // Legacy history IDs differ from live IDs. Never concatenate a snapshot prefix
                // with queued deltas: replace that role's partial items with a labelled live slice.
                val liveTypes = streamed.items.map { it.type }.toSet()
                turn.copy(items = turn.items.filterNot { it.type in liveTypes } + streamed.items)
            }
        } + live.values.filter { item -> history.none { it.id == item.id } }
    }

    fun reconcile(history: CodexThread, resetLive: Boolean = false): Timeline = copy(
        thread = history,
        live = if (resetLive) emptyMap() else live.filterKeys { id ->
            history.turns.none { it.id == id && it.terminal }
        },
    )

    fun acknowledge(turn: Turn): Timeline {
        if (thread?.turns?.any { it.id == turn.id && it.terminal } == true) return this
        return copy(live = live + (turn.id to (live[turn.id] ?: turn)))
    }

    fun event(method: String, params: JSONObject): Timeline {
        if (params.optString("threadId") != thread?.id) return this
        if (method == "thread/status/changed") {
            // The status event has no turn/version. A queued "active" cannot override history.
            return this
        }
        val turnId = params.optString("turnId").ifEmpty { params.optJSONObject("turn")?.optString("id").orEmpty() }
        if (turnId.isEmpty() || thread?.turns?.any { it.id == turnId && it.terminal } == true) return this
        val turn = live[turnId] ?: Turn(turnId, "inProgress", emptyList())
        val updated = when (method) {
            "turn/started" -> turn
            "item/agentMessage/delta" -> {
                val id = params.getString("itemId")
                val existing = turn.items.find { it.id == id }
                val message = Message(id, "agentMessage", existing?.text.orEmpty() + params.getString("delta"))
                turn.copy(items = turn.items.filterNot { it.id == id } + message)
            }
            "item/started", "item/completed" -> {
                val message = Wire.message(params.getJSONObject("item"))
                val items = turn.items.toMutableList()
                val index = items.indexOfFirst { it.id == message.id }
                if (index < 0) items.add(message) else if (method == "item/completed") items[index] = message
                turn.copy(items = items)
            }
            else -> return this
        }
        return copy(live = live + (turnId to updated))
    }
}

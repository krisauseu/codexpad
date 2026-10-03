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
                val liveTextTypes = streamed.items.filter { it.activity == null }.map { it.type }.toSet()
                val byId = streamed.items.associateBy { it.id }
                val merged = turn.items.filterNot { it.activity == null && it.type in liveTextTypes }
                    .map { byId[it.id] ?: it }
                turn.copy(status = if (streamed.terminal) streamed.status else turn.status,
                    startedAt = turn.startedAt ?: streamed.startedAt,
                    completedAt = streamed.completedAt ?: turn.completedAt,
                    durationMs = streamed.durationMs ?: turn.durationMs,
                    items = merged + streamed.items.filter { item -> merged.none { it.id == item.id } })
            }
        } + live.values.filter { item -> history.none { it.id == item.id } }
    }

    fun reconcile(history: CodexThread, resetLive: Boolean = false): Timeline = copy(
        thread = history,
        live = if (resetLive) emptyMap() else live.filterKeys { id ->
            history.turns.none { it.id == id && it.terminal }
        }.mapValues { (id, turn) ->
            val authoritativeIds = history.turns.find { it.id == id }?.items.orEmpty()
                .filter { it.activity != null }.map { it.id }.toSet()
            turn.copy(items = turn.items.filterNot { it.id in authoritativeIds })
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
            "turn/started", "turn/completed" -> {
                val reported = Wire.turn(params.getJSONObject("turn"))
                if (turn.terminal && method == "turn/started") return this
                turn.copy(status = reported.status, startedAt = reported.startedAt ?: turn.startedAt,
                    completedAt = reported.completedAt ?: turn.completedAt, durationMs = reported.durationMs ?: turn.durationMs,
                    error = reported.error ?: turn.error)
            }
            "item/agentMessage/delta" -> {
                val id = params.getString("itemId")
                val existing = turn.items.find { it.id == id }
                val message = Message(id, "agentMessage", existing?.text.orEmpty() + params.getString("delta"))
                turn.copy(items = turn.items.filterNot { it.id == id } + message)
            }
            "item/started", "item/completed" -> {
                val message = Wire.message(params.getJSONObject("item")).copy(completedEvent = method == "item/completed")
                val items = turn.items.toMutableList()
                val index = items.indexOfFirst { it.id == message.id }
                val historical = thread?.turns?.find { it.id == turnId }?.items?.find { it.id == message.id }
                val prior = items.getOrNull(index) ?: historical
                if (historical?.activityTerminal == true || (method == "item/started" && prior?.activityTerminal == true)) return this
                if (index < 0) items.add(message) else items[index] = message
                turn.copy(items = items)
            }
            "item/commandExecution/outputDelta", "item/fileChange/patchUpdated" -> {
                val id = params.optString("itemId")
                val prior = turn.items.find { it.id == id }
                    ?: thread?.turns?.find { it.id == turnId }?.items?.find { it.id == id }
                    ?: return this // No invented item: next snapshot heals a missing start.
                if (prior.activityTerminal) return this
                val activity = when (val activity = prior.activity) {
                    is Activity.Command -> if (method == "item/commandExecution/outputDelta" && !params.isNull("delta"))
                        activity.copy(liveOutput = activity.liveOutput.orEmpty() + params.getString("delta")) else return this
                    is Activity.Files -> if (method == "item/fileChange/patchUpdated" && params.optJSONArray("changes") != null)
                        activity.copy(changes = ActivityWire.changes(params.getJSONArray("changes"))) else return this
                    else -> return this
                }
                val items = turn.items.toMutableList()
                val index = items.indexOfFirst { it.id == id }
                val item = prior.copy(activity = activity)
                if (index < 0) items.add(item) else items[index] = item
                turn.copy(items = items)
            }
            else -> return this
        }
        return copy(live = live + (turnId to updated))
    }
}

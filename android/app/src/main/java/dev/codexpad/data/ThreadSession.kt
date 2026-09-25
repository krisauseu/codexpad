package dev.codexpad.data

import dev.codexpad.model.Wire
import dev.codexpad.model.ContextUsage
import dev.codexpad.model.ModelReroute
import dev.codexpad.network.CodexPadService
import dev.codexpad.network.connectionError
import java.io.IOException
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject

data class ThreadState(
    val timeline: Timeline = Timeline(),
    val usage: ContextUsage = ContextUsage(),
    val reroutes: Map<String, ModelReroute> = emptyMap(),
    val connection: String = "Lade Thread …",
    val connected: Boolean = false,
    val error: String? = null,
    val interruptTurnId: String? = null,
    val interruptError: String? = null,
) {
    // Ambiguous/missing IDs and offline snapshots never select a target implicitly.
    val stoppableTurnId: String? get() = if (connected && interruptTurnId == null)
        timeline.turns.filter { !it.terminal }.singleOrNull()
            ?.takeIf { it.status == "inProgress" && it.id.isNotBlank() }?.id else null
}

/** One foreground session. Cancelling run() closes SSE; no mutation is retried here. */
class ThreadSession(
    private val api: CodexPadService,
    private val threadId: String,
    pendingInterrupt: String? = null,
    private val saveInterrupt: (String?) -> Unit = {},
) {
    private val mutable = MutableStateFlow(ThreadState(interruptTurnId = pendingInterrupt))
    val state = mutable.asStateFlow()
    private val refreshLock = Mutex()

    suspend fun refresh(resetLive: Boolean = false) = refreshLock.withLock {
        val snapshot = api.thread(threadId)
        val history = api.history(threadId)
        require(snapshot.id == threadId && history.id == threadId) { "Falsche Thread-ID in Serverantwort" }
        mutable.update { it.copy(timeline = it.timeline.reconcile(history, resetLive), error = null) }
        reconcileInterrupt()
    }

    private fun reconcileInterrupt() {
        val pending = state.value.interruptTurnId ?: return
        if (state.value.timeline.thread?.turns?.any { it.id == pending && it.terminal } == true) {
            mutable.update { it.copy(interruptTurnId = null, interruptError = null) }
            saveInterrupt(null)
        }
    }

    suspend fun interrupt(expectedTurnId: String) {
        val before = state.value
        if (before.stoppableTurnId != expectedTurnId) return
        if (!mutable.compareAndSet(before, before.copy(interruptTurnId = expectedTurnId, interruptError = null))) return
        // Persist the exact target before I/O. Neither HTTP success nor failure clears it.
        saveInterrupt(expectedTurnId)
        try { api.interruptTurn(threadId, expectedTurnId) }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (error: Exception) {
            mutable.update { if (it.interruptTurnId == expectedTurnId)
                it.copy(interruptError = "Abbruch unbestätigt: ${connectionError(error)}") else it }
        }
        refreshVisible()
    }

    fun acknowledge(turn: dev.codexpad.model.Turn) {
        mutable.update { it.copy(timeline = it.timeline.acknowledge(turn)) }
    }

    suspend fun refreshVisible() {
        try { refresh() }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (error: Exception) { mutable.update { it.copy(error = connectionError(error)) } }
    }

    fun paused() { mutable.update { it.copy(usage = it.usage.outdated(), connected = false, connection = "Pausiert · Zustand wird bei Rückkehr geladen") } }

    suspend fun run(): Nothing {
        var backoff = 1_000L
        while (currentCoroutineContext().isActive) {
            try {
                mutable.update { it.copy(usage = it.usage.outdated(), connected = false, connection = "Snapshot und Verlauf laden …") }
                refresh(resetLive = true)
                coroutineScope {
                    val poll = launch {
                        while (isActive) {
                            delay(if (state.value.timeline.busy) 5_000 else 15_000)
                            // Also heals missed completion events and a dead backend with live HTTP heartbeats.
                            refresh()
                        }
                    }
                    var hasSnapshot = false
                    try {
                        api.events(threadId).collect { frame ->
                            val json = JSONObject(frame.data)
                            when (frame.event) {
                                "snapshot" -> {
                                    val snapshot = Wire.threadEnvelope(json)
                                    require(snapshot.id == threadId)
                                    mutable.update { it.copy(timeline = it.timeline.reconcile(snapshot, true),
                                        connected = true, connection = "Live verbunden", error = null) }
                                    reconcileInterrupt()
                                    hasSnapshot = true
                                    backoff = 1_000
                                }
                                "event" -> {
                                    check(hasSnapshot) { "SSE-Event ohne initialen Snapshot" }
                                    val method = json.getString("method")
                                    val params = json.optJSONObject("params") ?: JSONObject()
                                    if (params.optString("threadId") == threadId) {
                                        if (method == "codexpad/overflow") throw IOException("Live-Puffer übergelaufen")
                                        mutable.update { state -> state.copy(
                                            timeline = state.timeline.event(method, params),
                                            usage = if (method == "thread/tokenUsage/updated")
                                                ContextUsage.parse(params.optJSONObject("tokenUsage")) else state.usage,
                                            reroutes = if (method == "model/rerouted" && params.optString("turnId").isNotBlank())
                                                state.reroutes + (params.getString("turnId") to ModelReroute(
                                                    params.getString("turnId"), params.optString("fromModel"), params.optString("toModel")))
                                                else state.reroutes,
                                        ) }
                                        if (method in setOf("turn/completed", "thread/status/changed", "error")) refresh()
                                        if (method == "error") mutable.update {
                                            it.copy(error = params.optJSONObject("error")?.optString("message") ?: "Agentfehler")
                                        }
                                    }
                                }
                            }
                        }
                        throw IOException("SSE-Verbindung geschlossen")
                    } finally { poll.cancel() }
                }
            } catch (cancelled: CancellationException) {
                paused()
                throw cancelled
            } catch (error: Exception) {
                mutable.update { it.copy(usage = it.usage.outdated(), connected = false,
                    connection = "Verbindung verloren · erneuter Abgleich in ${backoff / 1000} s",
                    error = connectionError(error)) }
                delay(backoff)
                backoff = (backoff * 2).coerceAtMost(15_000)
            }
        }
        throw CancellationException()
    }
}

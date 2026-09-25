package dev.codexpad.data

import dev.codexpad.model.Wire
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
    val connection: String = "Lade Thread …",
    val connected: Boolean = false,
    val error: String? = null,
)

/** One foreground session. Cancelling run() closes SSE; no mutation is retried here. */
class ThreadSession(private val api: CodexPadService, private val threadId: String) {
    private val mutable = MutableStateFlow(ThreadState())
    val state = mutable.asStateFlow()
    private val refreshLock = Mutex()

    suspend fun refresh(resetLive: Boolean = false) = refreshLock.withLock {
        val snapshot = api.thread(threadId)
        val history = api.history(threadId)
        require(snapshot.id == threadId && history.id == threadId) { "Falsche Thread-ID in Serverantwort" }
        mutable.update { it.copy(timeline = it.timeline.reconcile(history, resetLive), error = null) }
    }

    fun acknowledge(turn: dev.codexpad.model.Turn) {
        mutable.update { it.copy(timeline = it.timeline.acknowledge(turn)) }
    }

    suspend fun refreshVisible() {
        try { refresh() }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (error: Exception) { mutable.update { it.copy(error = connectionError(error)) } }
    }

    fun paused() { mutable.update { it.copy(connected = false, connection = "Pausiert · Zustand wird bei Rückkehr geladen") } }

    suspend fun run(): Nothing {
        var backoff = 1_000L
        while (currentCoroutineContext().isActive) {
            try {
                mutable.update { it.copy(connected = false, connection = "Snapshot und Verlauf laden …") }
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
                                    hasSnapshot = true
                                    backoff = 1_000
                                }
                                "event" -> {
                                    check(hasSnapshot) { "SSE-Event ohne initialen Snapshot" }
                                    val method = json.getString("method")
                                    val params = json.optJSONObject("params") ?: JSONObject()
                                    if (params.optString("threadId") == threadId) {
                                        if (method == "codexpad/overflow") throw IOException("Live-Puffer übergelaufen")
                                        mutable.update { it.copy(timeline = it.timeline.event(method, params)) }
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
                mutable.update { it.copy(connected = false,
                    connection = "Verbindung verloren · erneuter Abgleich in ${backoff / 1000} s",
                    error = connectionError(error)) }
                delay(backoff)
                backoff = (backoff * 2).coerceAtMost(15_000)
            }
        }
        throw CancellationException()
    }
}

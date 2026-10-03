package dev.codexpad.data

import dev.codexpad.model.Wire
import dev.codexpad.model.ContextStatus
import dev.codexpad.model.WeeklyLimit
import dev.codexpad.model.ModelReroute
import dev.codexpad.model.InputAnswer
import dev.codexpad.model.runningDuration
import dev.codexpad.network.ApiException
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
    val usage: ContextStatus = ContextStatus(),
    val weekly: WeeklyLimit = WeeklyLimit(),
    val usageTurnId: String? = null,
    val reroutes: Map<String, ModelReroute> = emptyMap(),
    val connection: String = "Lade Thread …",
    val connected: Boolean = false,
    val error: String? = null,
    val interruptTurnId: String? = null,
    val interruptError: String? = null,
    val compaction: Compaction? = null,
    val answerAttempts: Set<String> = emptySet(),
    val answerSending: Set<String> = emptySet(),
    val answerErrors: Map<String, String> = emptyMap(),
) {
    val runningTurn get() = timeline.turns.filter { !it.terminal }.singleOrNull()?.takeIf { it.status == "inProgress" }
    val modelLabel get() = "${timeline.thread?.model ?: "Modell unbekannt"} · ${timeline.thread?.reasoningEffort ?: "Reasoning unbekannt"}"
    fun statusLabel(nowSeconds: Long): String = listOfNotNull(
        usage.label, weekly.label(nowSeconds), runningTurn?.runningDuration(nowSeconds),
        "letzter Stand".takeIf { !connected },
    ).joinToString(" · ")

    val requests get() = timeline.thread?.pendingRequests.orEmpty()
    // 0.156.1 turn/start can steer this same turn while a non-blocking question remains open.
    val canMessageDuringInput get() = requests.isNotEmpty() && requests.none { it.isBlocking }
    val canCompact get() = connected && !timeline.busy && interruptTurnId == null && compaction?.pending != true
    // Ambiguous/missing IDs and offline snapshots never select a target implicitly.
    val stoppableTurnId: String? get() = if (connected && interruptTurnId == null && compaction?.pending != true)
        timeline.turns.filter { !it.terminal }.singleOrNull()
            ?.takeIf { it.status == "inProgress" && it.id.isNotBlank() }?.id else null
}

/** One foreground session. Cancelling run() closes SSE; no mutation is retried here. */
class ThreadSession(
    private val api: CodexPadService,
    private val threadId: String,
    pendingInterrupt: String? = null,
    private val saveInterrupt: (String?) -> Unit = {},
    pendingCompaction: Compaction? = null,
    private val saveCompaction: (Compaction?) -> Unit = {},
    pendingAnswers: Set<String> = emptySet(),
    private val saveAnswers: (Set<String>) -> Unit = {},
) {
    private val mutable = MutableStateFlow(ThreadState(interruptTurnId = pendingInterrupt, compaction = pendingCompaction,
        answerAttempts = pendingAnswers, answerErrors = pendingAnswers.associateWith { "Antwort unbestätigt · Zustand abgleichen" }))
    val state = mutable.asStateFlow()
    private val refreshLock = Mutex()

    suspend fun refresh(resetLive: Boolean = false) = refreshLock.withLock {
        val snapshot = api.thread(threadId)
        val history = api.history(threadId)
        require(snapshot.id == threadId && history.id == threadId) { "Falsche Thread-ID in Serverantwort" }
        mutable.update { it.copy(timeline = it.timeline.reconcile(history, resetLive), error = null) }
        reconcileInterrupt()
        reconcileCompaction(history)
        reconcileAnswers()
    }

    private fun reconcileAnswers() {
        mutable.update { state ->
            val open = state.requests.map { it.id }.toSet()
            state.copy(answerAttempts = state.answerAttempts.intersect(open),
                answerErrors = state.answerErrors.filterKeys { it in open })
        }
        saveAnswers(state.value.answerAttempts)
    }

    suspend fun answer(requestId: String, answers: Map<String, InputAnswer>) {
        val before = state.value
        if (!before.connected || requestId in before.answerAttempts || requestId in before.answerSending ||
            before.requests.none { it.id == requestId && it.status == "pending" }) return
        if (!mutable.compareAndSet(before, before.copy(answerAttempts = before.answerAttempts + requestId,
                answerSending = before.answerSending + requestId, answerErrors = before.answerErrors - requestId))) return
        saveAnswers(state.value.answerAttempts) // Before I/O, including rotation/process restoration.
        try { api.answerRequest(threadId, requestId, answers) }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (error: Exception) {
            mutable.update { it.copy(answerErrors = it.answerErrors + (requestId to
                "Antwort unbestätigt: ${connectionError(error)}"),
                answerAttempts = if (error is ApiException && error.status == 400) it.answerAttempts - requestId else it.answerAttempts) }
        } finally {
            mutable.update { it.copy(answerSending = it.answerSending - requestId) }
            saveAnswers(state.value.answerAttempts)
        }
        refreshVisible()
    }

    // Explicit user action after uncertainty. Only a fresh, still-pending callback
    // permits another attempt; the server atomically prevents duplicate forwarding.
    suspend fun reviewAnswer(requestId: String) {
        if (requestId in state.value.answerSending) return
        try {
            refresh()
            mutable.update { state ->
                if (state.requests.any { it.id == requestId && it.status == "pending" })
                    state.copy(answerAttempts = state.answerAttempts - requestId, answerErrors = state.answerErrors - requestId)
                else state
            }
            saveAnswers(state.value.answerAttempts)
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (error: Exception) { mutable.update { it.copy(error = connectionError(error)) } }
    }

    private fun reconcileCompaction(history: dev.codexpad.model.CodexThread) {
        mutable.update { state ->
            val compact = state.compaction ?: return@update state
            val next = compact.reconcile(history)
            state.copy(compaction = next, usage = if (compact.pending && next.phase == "completed" &&
                next.turnId != null && state.usageTurnId == next.turnId) state.usage.copy(stale = false) else state.usage)
        }
        saveCompaction(state.value.compaction)
    }

    suspend fun compact() {
        val before = state.value
        if (!before.canCompact) return
        val pending = Compaction(before.timeline.turns.map { it.id }.toSet(), phase = "requested")
        if (!mutable.compareAndSet(before, before.copy(compaction = pending, usage = before.usage.outdated(), usageTurnId = null))) return
        // Save before I/O; neither HTTP success nor a missing item permits another POST.
        saveCompaction(pending)
        try { api.compactThread(threadId) }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { unknownCompaction(pending) }
        refreshVisible()
    }

    private fun unknownCompaction(expected: Compaction? = null) {
        mutable.update { state -> state.copy(compaction = state.compaction?.let {
            if (it.pending && (expected == null || it.baseline == expected.baseline)) it.copy(phase = "unknown") else it
        }) }
        saveCompaction(state.value.compaction)
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
        catch (error: Exception) { unknownCompaction(); mutable.update { it.copy(error = connectionError(error)) } }
    }

    fun paused() { unknownCompaction(); mutable.update { it.copy(usage = it.usage.outdated(), weekly = it.weekly.outdated(), usageTurnId = null, connected = false, connection = "Pausiert · Zustand wird bei Rückkehr geladen") } }

    private suspend fun refreshLimits() {
        val before = state.value.weekly
        try {
            val latest = api.rateLimits()
            // A newer pushed limit must win over an in-flight read.
            mutable.update { if (it.weekly === before) it.copy(weekly = latest) else it }
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) {
            // Account status failure must not interrupt the conversation.
            mutable.update { if (it.weekly === before) it.copy(weekly = before.outdated()) else it }
        }
    }

    suspend fun run(): Nothing {
        var backoff = 1_000L
        while (currentCoroutineContext().isActive) {
            try {
                mutable.update { it.copy(usage = it.usage.outdated(), weekly = it.weekly.outdated(), usageTurnId = null, connected = false, connection = "Snapshot und Verlauf laden …") }
                refresh(resetLive = true)
                coroutineScope {
                    val limits = launch(start = CoroutineStart.UNDISPATCHED) {
                        while (isActive) {
                            refreshLimits()
                            delay(60_000)
                        }
                    }
                    val poll = launch {
                        while (isActive) {
                            delay(if (state.value.timeline.busy || state.value.compaction?.pending == true) 5_000 else 15_000)
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
                                    reconcileCompaction(snapshot)
                                    reconcileAnswers()
                                    hasSnapshot = true
                                    backoff = 1_000
                                }
                                "event" -> {
                                    check(hasSnapshot) { "SSE-Event ohne initialen Snapshot" }
                                    val method = json.getString("method")
                                    val params = json.optJSONObject("params") ?: JSONObject()
                                    if (method == "codexpad/overflow") throw IOException("Live-Puffer übergelaufen")
                                    if (method == "account/rateLimits/updated") {
                                        WeeklyLimit.update(params)?.let { latest -> mutable.update { it.copy(weekly = latest) } }
                                    }
                                    if (method == "account/updated") {
                                        mutable.update { it.copy(weekly = WeeklyLimit()) }
                                        launch(start = CoroutineStart.UNDISPATCHED) { refreshLimits() }
                                    }
                                    if (params.optString("threadId") == threadId) {
                                        mutable.update { state -> state.copy(
                                            timeline = state.timeline.event(method, params),
                                            usage = if (method == "thread/tokenUsage/updated")
                                                ContextStatus.parse(params.optJSONObject("tokenUsage")).let { usage ->
                                                    val compact = state.compaction
                                                    // Resume can replay pre-compact usage. A later turn's measurement
                                                    // (including the compact turn) is required; never invent savings.
                                                    if (compact != null && (compact.pending || params.optString("turnId").isBlank() ||
                                                        params.optString("turnId") in compact.baseline)) usage.outdated() else usage
                                                } else state.usage,
                                            usageTurnId = if (method == "thread/tokenUsage/updated")
                                                params.optString("turnId").takeIf { it.isNotBlank() } else state.usageTurnId,
                                            reroutes = if (method == "model/rerouted" && params.optString("turnId").isNotBlank())
                                                state.reroutes + (params.getString("turnId") to ModelReroute(
                                                    params.getString("turnId"), params.optString("fromModel"), params.optString("toModel")))
                                                else state.reroutes,
                                        ) }
                                        if (method in setOf("item/started", "item/completed") &&
                                            params.optJSONObject("item")?.optString("type") == "contextCompaction") {
                                            mutable.update { state ->
                                                val compact = state.compaction
                                                val id = params.optString("turnId")
                                                if (compact?.pending == true && id.isNotBlank() && id !in compact.baseline)
                                                    state.copy(compaction = compact.copy(turnId = id, phase = "running"), usage = state.usage.outdated())
                                                else state
                                            }
                                            saveCompaction(state.value.compaction)
                                            refresh()
                                        }
                                        if (method in setOf("turn/completed", "thread/status/changed", "error",
                                            "codexpad/requests/changed", "serverRequest/resolved", "thread/closed")) refresh()
                                        if (method == "error") mutable.update {
                                            it.copy(error = params.optJSONObject("error")?.optString("message") ?: "Agentfehler")
                                        }
                                    }
                                }
                            }
                        }
                        throw IOException("SSE-Verbindung geschlossen")
                    } finally { poll.cancel(); limits.cancel() }
                }
            } catch (cancelled: CancellationException) {
                paused()
                throw cancelled
            } catch (error: Exception) {
                unknownCompaction()
                mutable.update { it.copy(usage = it.usage.outdated(), weekly = it.weekly.outdated(), usageTurnId = null, connected = false,
                    connection = "Verbindung verloren · erneuter Abgleich in ${backoff / 1000} s",
                    error = connectionError(error)) }
                delay(backoff)
                backoff = (backoff * 2).coerceAtMost(15_000)
            }
        }
        throw CancellationException()
    }
}

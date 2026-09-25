package dev.codexpad.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.SavedStateHandle
import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.codexpad.BuildConfig
import dev.codexpad.data.ThreadSession
import dev.codexpad.model.*
import dev.codexpad.network.CodexPadApi
import dev.codexpad.network.connectionError
import dev.codexpad.settings.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class PadViewModel(application: Application, private val saved: SavedStateHandle) : AndroidViewModel(application) {
    private val store = SettingsStore(application)
    private var config = ConnectionSettings(BuildConfig.SERVER_URL, "")
    private var api = CodexPadApi(config.serverUrl, config.token)
    var ready by mutableStateOf(false)
        private set
    var showSettings by mutableStateOf(false)
        private set
    var serverUrl by mutableStateOf(config.serverUrl)
        private set
    var hasToken by mutableStateOf(false)
        private set
    var settingsBusy by mutableStateOf(false)
        private set
    var connectionStatus by mutableStateOf("Einstellungen werden geladen …")
        private set
    var settingsError by mutableStateOf<String?>(null)
        private set


    fun openSettings() {
        if (ready && !creating && !sending) {
            listing?.cancel()
            showSettings = true
        }
    }

    fun closeSettings() { if (!settingsBusy && hasToken) showSettings = false }

    fun settingsEdited() {
        settingsError = null
        connectionStatus = "Eingaben geändert · noch nicht getestet"
    }

    private fun candidate(url: String, enteredToken: String): ConnectionSettings {
        val normalized = normalizeServerUrl(url, BuildConfig.DEBUG)
        require(normalized == config.serverUrl || enteredToken.isNotEmpty()) {
            "Bei einer neuen Serveradresse das zugehörige Token erneut eingeben."
        }
        val token = enteredToken.ifEmpty { config.token }
        validateToken(token)
        return ConnectionSettings(normalized, token)
    }

    // Test unsaved inputs without changing the active session or persisting the secret.
    fun testConnection(url: String, enteredToken: String) {
        if (settingsBusy) return
        val target = try { candidate(url, enteredToken) } catch (error: IllegalArgumentException) {
            settingsError = error.message; return
        }
        settingsBusy = true
        settingsError = null
        connectionStatus = "Verbindung wird geprüft …"
        viewModelScope.launch {
            try {
                withTimeout(20_000) {
                    val probe = CodexPadApi(target.serverUrl, target.token)
                    probe.workspaces() // Proves authentication; public /health alone is insufficient.
                    check(probe.health() == "ok")
                }
                connectionStatus = "Verbunden · Token akzeptiert · Codex bereit"
            } catch (error: TimeoutCancellationException) {
                connectionStatus = connectionError(error)
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) { connectionStatus = connectionError(error) }
            finally { settingsBusy = false }
        }
    }

    fun saveConnection(url: String, enteredToken: String, onSaved: () -> Unit) {
        if (settingsBusy || creating || sending) return
        val target = try { candidate(url, enteredToken) } catch (error: IllegalArgumentException) {
            settingsError = error.message; return
        }
        settingsBusy = true
        settingsError = null
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) { store.save(target) }
                listing?.cancel()
                if (target.serverUrl != config.serverUrl) saved.keys().toList().forEach { saved.remove<Any>(it) }
                saved["serverUrl"] = target.serverUrl
                config = target
                api = CodexPadApi(target.serverUrl, target.token)
                serverUrl = target.serverUrl
                hasToken = true
                session = null
                threadId = null
                workspace = null
                saved["threadId"] = null
                saved["workspaceId"] = null
                saved["workspaceName"] = null
                workspaces = emptyList()
                threads = emptyList()
                error = null
                connectionStatus = "Gespeichert · noch nicht getestet"
                onSaved()
                showSettings = false
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { settingsError = "Sicheres Speichern fehlgeschlagen. Bisherige Einstellungen bleiben aktiv." }
            finally { settingsBusy = false }
        }
    }
    var workspace by mutableStateOf(saved.get<String>("workspaceId")?.let { Workspace(it, saved["workspaceName"] ?: it) })
        private set
    var threadId by mutableStateOf(saved.get<String>("threadId"))
        private set
    var session by mutableStateOf(threadId?.let { ThreadSession(api, it) })
        private set
    var workspaces by mutableStateOf(emptyList<Workspace>())
        private set
    var threads by mutableStateOf(emptyList<CodexThread>())
        private set
    var loading by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    var creating by mutableStateOf(false)
        private set
    var sending by mutableStateOf(false)
        private set
    var draft by mutableStateOf(saved.get<String>("draft:${threadId}").orEmpty())
        private set
    var uncertain by mutableStateOf(saved.get<Boolean>("uncertain:${threadId}") ?: false)
        private set
    var createUncertain by mutableStateOf(saved.get<Boolean>("create:${workspace?.id}") ?: false)
        private set
    var sendError by mutableStateOf<String?>(null)
        private set
    private var listing: Job? = null

    init {
        viewModelScope.launch {
            try {
                config = withContext(Dispatchers.IO) { store.load() }
                api = CodexPadApi(config.serverUrl, config.token)
                serverUrl = config.serverUrl
                hasToken = config.token.isNotEmpty()
                if (saved.get<String>("serverUrl") != config.serverUrl) {
                    saved.keys().toList().forEach { saved.remove<Any>(it) }
                    workspace = null
                    threadId = null
                    draft = ""
                    uncertain = false
                    createUncertain = false
                }
                saved["serverUrl"] = config.serverUrl
                session = threadId?.let { ThreadSession(api, it) }
                connectionStatus = if (hasToken) "Noch nicht getestet" else "Zugriffstoken fehlt"
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) {
                settingsError = "Gespeicherte Zugangsdaten konnten nicht entschlüsselt werden. Bitte neu eingeben und speichern."
                connectionStatus = "Zugangsdaten nicht verfügbar"
            }
            ready = true
            showSettings = !hasToken
        }
    }


    fun selectWorkspace(selected: Workspace) {
        workspace = selected
        saved["workspaceId"] = selected.id
        saved["workspaceName"] = selected.name
        threads = emptyList()
        createUncertain = saved["create:${selected.id}"] ?: false
        reload()
    }

    fun openThread(id: String) {
        listing?.cancel()
        threadId = id
        saved["threadId"] = id
        session = ThreadSession(api, id)
        draft = saved.get<String>("draft:$id").orEmpty()
        uncertain = saved["uncertain:$id"] ?: false
        sendError = null
    }

    fun back() {
        if (threadId != null) {
            threadId = null
            saved["threadId"] = null
            session = null
        } else {
            workspace = null
            saved["workspaceId"] = null
            saved["workspaceName"] = null
        }
        reload()
    }

    fun reload() {
        if (!ready || !hasToken || showSettings) return
        listing?.cancel()
        val selected = workspace
        listing = viewModelScope.launch {
            loading = true
            error = null
            try {
                if (selected == null) {
                    check(api.health() == "ok") { "Codex App Server ist nicht verfügbar" }
                    workspaces = api.workspaces()
                } else threads = api.threads(selected.id)
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) { error = connectionError(failure) }
            finally { loading = false }
        }
    }

    fun createThread() {
        val selected = workspace ?: return
        if (creating || createUncertain) return
        creating = true
        saved["create:${selected.id}"] = true
        viewModelScope.launch {
            try {
                val created = api.createThread(selected.id)
                saved["create:${selected.id}"] = false
                if (workspace == selected) openThread(created.id)
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) {
                if (workspace == selected) {
                    createUncertain = true
                    reload()
                    error = "Thread-Anlage nicht bestätigt: ${connectionError(failure)}. Liste prüfen; keine automatische Wiederholung."
                }
            } finally { creating = false }
        }
    }

    fun editDraft(text: String) {
        draft = text
        saved["draft:${threadId}"] = text
    }

    fun reviewedUnknown() {
        uncertain = false
        saved["uncertain:${threadId}"] = false
        sendError = null
    }

    fun reviewedCreate() {
        createUncertain = false
        saved["create:${workspace?.id}"] = false
    }

    fun send() {
        val id = threadId ?: return
        val target = session ?: return
        val message = draft.trim()
        if (sending || uncertain || !target.state.value.connected || target.state.value.timeline.busy ||
            message.isEmpty() || message.codePointCount(0, message.length) > 4096) return
        // Persist before sending. Process death must not turn an unacknowledged POST into a retry.
        saved["uncertain:$id"] = true
        sending = true
        sendError = null
        viewModelScope.launch {
            try {
                val turn = api.startTurn(id, message)
                target.acknowledge(turn)
                saved["uncertain:$id"] = false
                saved["draft:$id"] = ""
                if (threadId == id) { draft = ""; uncertain = false }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) {
                if (threadId == id) {
                    uncertain = true
                    sendError = "Ausgang unbekannt: ${connectionError(failure)}"
                }
            } finally { sending = false }
            // A read failure after a confirmed POST never changes the mutation's known outcome.
            target.refreshVisible()
        }
    }
}

package dev.codexpad.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.SavedStateHandle
import android.app.Application
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.codexpad.BuildConfig
import dev.codexpad.data.ThreadSession
import dev.codexpad.data.Compaction
import dev.codexpad.model.*
import dev.codexpad.network.CodexPadApi
import dev.codexpad.network.UploadImage
import dev.codexpad.network.UploadText
import dev.codexpad.network.ApiException
import dev.codexpad.network.connectionError
import dev.codexpad.settings.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets

data class PendingImage(val uri: Uri, val name: String, val mimeType: String)
data class PendingText(val uri: Uri, val name: String, val mimeType: String)

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


    suspend fun downloadArtifact(thread: String, artifact: Artifact): java.io.File {
        val selectedApi = api
        return withContext(Dispatchers.IO) {
            val folder = java.io.File(getApplication<Application>().cacheDir, "artifacts").apply { mkdirs() }
            // Keep grants usable for a day; clean only expired files, never an active download.
            folder.listFiles()?.filter { System.currentTimeMillis() - it.lastModified() > 86_400_000 }?.forEach { it.deleteRecursively() }
            val directory = java.io.File(folder, java.util.UUID.randomUUID().toString()).apply { mkdirs() }
            val name = artifact.name.substringAfterLast('/').substringAfterLast('\\').take(180)
            val target = java.io.File(directory, name.takeUnless { it.isBlank() || it == "." || it == ".." } ?: "download")
            selectedApi.downloadArtifact(thread, artifact, target)
            target
        }
    }

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
                models = emptyList(); nextModel = null; nextEffort = null
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
    var session by mutableStateOf(threadId?.let { newSession(it) })
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
    var images by mutableStateOf(emptyList<PendingImage>())
        private set
    var textFiles by mutableStateOf(emptyList<PendingText>())
        private set
    var attachmentError by mutableStateOf<String?>(null)
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
                session = threadId?.let { newSession(it) }
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


    private fun newSession(id: String): ThreadSession {
        val server = config.serverUrl
        var previous: String? = saved["interrupt:$id"]
        var previousCompact: String? = saved["compact:$id"]
        return ThreadSession(api, id, previous, saveInterrupt = { pending ->
            if (config.serverUrl == server && saved.get<String>("interrupt:$id") == previous)
                saved["interrupt:$id"] = pending
            previous = pending
        }, pendingCompaction = Compaction.restore(previousCompact), saveCompaction = { compact ->
            if (config.serverUrl == server && saved.get<String>("compact:$id") == previousCompact)
                saved["compact:$id"] = compact?.json()
            previousCompact = compact?.json()
        })
    }

    fun compact(target: ThreadSession) {
        if (session !== target || sending || uncertain) return
        viewModelScope.launch { target.compact() }
    }

    fun stop(target: ThreadSession, turnId: String) {
        if (session !== target) return
        viewModelScope.launch { target.interrupt(turnId) }
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
        nextModel = null; nextEffort = null
        listing?.cancel()
        threadId = id
        saved["threadId"] = id
        session = newSession(id)
        draft = saved.get<String>("draft:$id").orEmpty()
        images = emptyList(); textFiles = emptyList(); attachmentError = null
        uncertain = saved["uncertain:$id"] ?: false
        sendError = null
    }

    fun back() {
        if (threadId != null) {
            images = emptyList(); textFiles = emptyList(); attachmentError = null
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

    fun addAttachments(uris: List<Uri>) {
        if (sending || uncertain) return
        val resolver = getApplication<Application>().contentResolver
        var rejected = false
        uris.forEach { uri ->
            val mime = resolver.getType(uri)
            val name = resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
                if (it.moveToFirst()) it.getString(0) else null
            } ?: "Bild"
            when {
                mime in setOf("image/png", "image/jpeg", "image/webp") && images.size < 4 ->
                    images = images + PendingImage(uri, name, mime!!)
                mime in setOf("text/plain", "text/markdown") && name.lowercase().endsWithAny(".txt", ".md") && textFiles.size < 2 ->
                    textFiles = textFiles + PendingText(uri, name, mime!!)
                else -> rejected = true
            }
        }
        attachmentError = if (rejected) "Erlaubt: bis zu vier Bilder und zwei .txt/.md-Dateien." else null
    }

    fun removeImage(uri: Uri) { if (!sending) images = images.filterNot { it.uri == uri } }
    fun removeText(uri: Uri) { if (!sending) textFiles = textFiles.filterNot { it.uri == uri } }

    private fun String.endsWithAny(vararg suffixes: String) = suffixes.any { endsWith(it) }

    private fun readLimited(uri: Uri, limit: Int): ByteArray {
        val stream = getApplication<Application>().contentResolver.openInputStream(uri)
            ?: throw IllegalArgumentException("Datei nicht lesbar")
        return stream.use { input ->
            val output = ByteArrayOutputStream()
            val buffer = ByteArray(8192)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                if (output.size() + count > limit) throw IllegalArgumentException("Datei ist zu groß.")
                output.write(buffer, 0, count)
            }
            output.toByteArray()
        }
    }

    private fun readImage(image: PendingImage): UploadImage {
        val bytes = readLimited(image.uri, 5 * 1024 * 1024)
        if (bytes.isEmpty()) throw IllegalArgumentException("Bilddatei ist leer")
        return UploadImage(image.name, image.mimeType, bytes)
    }

    private fun readText(file: PendingText): UploadText {
        val bytes = readLimited(file.uri, 64 * 1024)
        if (bytes.isEmpty()) throw IllegalArgumentException("Textdatei ist leer")
        val text = StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString()
        if ('\u0000' in text) throw IllegalArgumentException("Textdatei enthält ungültige Zeichen")
        return UploadText(file.name, file.mimeType, bytes)
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

    var models by mutableStateOf(emptyList<CatalogModel>())
        private set
    var modelError by mutableStateOf<String?>(null)
        private set
    var modelsLoading by mutableStateOf(false)
        private set
    var nextModel by mutableStateOf<String?>(null)
        private set
    var nextEffort by mutableStateOf<String?>(null)
        private set

    fun loadModels() {
        if (modelsLoading) return
        val source = api
        modelsLoading = true
        viewModelScope.launch {
            try {
                val catalog = source.models()
                if (api === source) { models = catalog; modelError = null }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) { if (api === source) modelError = connectionError(failure) }
            finally { modelsLoading = false }
        }
    }

    fun selectModel(model: CatalogModel) {
        if (sending || uncertain) return
        nextEffort = model.compatibleEffort(nextEffort ?: session?.state?.value?.timeline?.thread?.reasoningEffort)
        nextModel = model.model
    }

    fun selectEffort(effort: String) {
        if (sending || uncertain) return
        val selected = nextModel ?: session?.state?.value?.timeline?.thread?.model
        val model = models.firstOrNull { it.model == selected } ?: return
        if (model.efforts.none { it.effort == effort }) return
        // Bind effort to the chosen model even if a later server read changes the thread.
        nextModel = model.model
        nextEffort = effort
    }

    fun clearSelection() { if (!sending && !uncertain) { nextModel = null; nextEffort = null } }

    fun send() {
        val id = threadId ?: return
        val target = session ?: return
        val message = draft.trim()
        val selectedImages = images
        val selectedTexts = textFiles
        val model = nextModel
        val effort = nextEffort
        if (sending || uncertain || target.state.value.compaction?.pending == true || target.state.value.interruptTurnId != null ||
            !target.state.value.connected || target.state.value.timeline.busy ||
            (message.isEmpty() && selectedImages.isEmpty() && selectedTexts.isEmpty()) || message.codePointCount(0, message.length) > 4096) return
        sending = true
        sendError = null
        viewModelScope.launch {
            var posting = false
            try {
                val uploads = withContext(Dispatchers.IO) { selectedImages.map(::readImage) }
                val files = withContext(Dispatchers.IO) { selectedTexts.map(::readText) }
                // Persist before POST. An unacknowledged mutation is never retried automatically.
                saved["uncertain:$id"] = true
                posting = true
                val turn = api.startTurn(id, message, model, effort, uploads, files)
                target.acknowledge(turn)
                saved["uncertain:$id"] = false
                saved["draft:$id"] = ""
                if (threadId == id) { draft = ""; images = emptyList(); textFiles = emptyList(); uncertain = false; nextModel = null; nextEffort = null }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) {
                if (threadId == id) {
                    if (posting && failure is ApiException && failure.status in setOf(400, 413, 415)) {
                        saved["uncertain:$id"] = false
                        attachmentError = "Datei abgelehnt (HTTP ${failure.status}). Typ und Größe prüfen."
                    } else if (posting) { uncertain = true; sendError = "Ausgang unbekannt: ${connectionError(failure)}" }
                    else attachmentError = failure.message ?: "Bild konnte nicht gelesen werden."
                }
            } finally { sending = false }
            // A read failure after a confirmed POST never changes the mutation's known outcome.
            target.refreshVisible()
        }
    }
}

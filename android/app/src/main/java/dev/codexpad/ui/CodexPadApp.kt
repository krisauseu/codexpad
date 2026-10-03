package dev.codexpad.ui

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.BorderStroke
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.codexpad.data.ThreadSession
import dev.codexpad.data.ThreadState
import dev.codexpad.model.Message
import dev.codexpad.model.TransferPolicy
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
private fun SessionStatusLine(state: ThreadState) {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var now by remember { mutableLongStateOf(System.currentTimeMillis() / 1000) }
    val running = state.runningTurn
    LaunchedEffect(lifecycle, state.connected, running?.id, running?.startedAt, state.weekly.resetsAt) {
        now = System.currentTimeMillis() / 1000
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (state.connected) {
                now = System.currentTimeMillis() / 1000
                // Only an active, timed turn needs second-by-second updates. Keep limit expiry current in idle.
                val untilReset = state.weekly.resetsAt?.takeIf { it > now }?.let { (it - now).coerceAtMost(60) * 1000 }
                delay(if (running?.startedAt != null) 1000 else untilReset ?: 60_000)
            }
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(state.modelLabel,
            style = MaterialTheme.typography.labelMedium)
        Text(state.statusLabel(now),
            style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CodexPadApp(vm: PadViewModel = viewModel()) {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val session = vm.session
    LaunchedEffect(session, vm.workspace, vm.ready, vm.showSettings) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            if (vm.ready && !vm.showSettings) {
                if (session != null) session.run() else vm.reload()
            }
        }
    }
    BackHandler(vm.showSettings || vm.workspace != null) {
        if (vm.showSettings) vm.closeSettings() else vm.back()
    }
    PadTheme {
        Scaffold(topBar = {
            TopAppBar(title = {
                Column {
                    Text(if (vm.showSettings) "Einstellungen" else if (vm.threadId != null) "Unterhaltung" else vm.workspace?.name ?: "CodexPad", maxLines = 1)
                    Text(if (vm.showSettings) "CodexPad · Verbindung" else if (vm.threadId != null) vm.workspace?.name ?: "Workspace" else if (vm.workspace != null) "Workspace · CodexPad" else "Dein Arbeitsbereich",
                        style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }, navigationIcon = {
                if (vm.showSettings && vm.hasToken) TextButton(onClick = vm::closeSettings, enabled = !vm.settingsBusy) { Text("Zurück") }
                else if (vm.workspace != null && !vm.showSettings) TextButton(onClick = vm::back) { Text("Zurück") }
            }, actions = {
                if (!vm.showSettings) TextButton(onClick = vm::openSettings,
                    enabled = vm.ready && !vm.creating && !vm.sending && !vm.workspaceBusy) { Text("Einstellungen") }
            })
        }) { padding ->
            Box(Modifier.fillMaxSize().padding(padding).imePadding(), contentAlignment = Alignment.TopCenter) {
                Column(Modifier.widthIn(max = 960.dp).fillMaxSize().padding(horizontal = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (!vm.ready) {
                        LinearProgressIndicator(Modifier.fillMaxWidth())
                    } else if (vm.showSettings) {
                        ConnectionSettingsScreen(vm)
                    } else if (session != null) {
                        // Only the timeline grows; controls leave it the remaining bounded height.
                        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            ThreadDetail(vm, session)
                        }
                    } else {
                        Column(Modifier.padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(if (vm.workspace == null) "Woran möchtest du arbeiten?" else "Deine Unterhaltungen",
                                style = MaterialTheme.typography.headlineSmall)
                            Text(if (vm.workspace == null) "Wähle einen Workspace, um mit Codex weiterzuarbeiten." else "Setze einen Thread fort oder beginne etwas Neues.",
                                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            OutlinedButton(onClick = vm::reload, enabled = !vm.loading && !vm.workspaceBusy) { Text("Aktualisieren") }
                            if (vm.workspace != null) Button(onClick = vm::createThread,
                                enabled = !vm.creating && !vm.createUncertain) {
                                Text(if (vm.creating) "Wird angelegt …" else "Neuer Thread")
                            }
                        }
                        if (vm.loading) LinearProgressIndicator(Modifier.fillMaxWidth())
                        vm.error?.let { ErrorText(it) }
                        if (vm.workspace == null) {
                            WorkspaceOverview(vm)
                        } else {
                            if (vm.createUncertain) {
                                ErrorText("Eine Thread-Anlage ist unbestätigt. Erst die aktualisierte Liste prüfen.")
                                OutlinedButton(onClick = vm::reviewedCreate, enabled = !vm.loading && vm.error == null) {
                                    Text("Liste geprüft")
                                }
                            }
                            if (!vm.loading && vm.error == null && vm.threads.isEmpty()) EmptyState("Platz für eine neue Idee", "Starte mit „Neuer Thread“ deine erste Unterhaltung in diesem Workspace.")
                            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                items(vm.threads, key = { it.id }) { thread ->
                                    Card(onClick = { vm.openThread(thread.id) }, modifier = Modifier.fillMaxWidth(),
                                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                                        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                            Text(thread.preview.ifBlank { "Thread ${thread.id.take(8)}" },
                                                style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                                Text(thread.id, Modifier.weight(1f), style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                                StatusBadge(statusLabel(thread.status), thread.status == "active")
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ColumnScope.ThreadDetail(vm: PadViewModel, session: ThreadSession) {
    val state by session.state.collectAsStateWithLifecycle()
    val turns = state.timeline.turns
    LaunchedEffect(session) { vm.loadModels() }
    var modelPicker by remember(session) { mutableStateOf(false) }
    var effortPicker by remember(session) { mutableStateOf(false) }
    val configured = state.timeline.thread
    val selectedModel = vm.models.firstOrNull { it.model == (vm.nextModel ?: configured?.model) }
    if (modelPicker) AlertDialog(onDismissRequest = { modelPicker = false },
        title = { Text("Modell für nächste Nachricht") },
        text = { LazyColumn {
            items(vm.models, key = { it.id }) { model ->
                TextButton(onClick = { vm.selectModel(model); modelPicker = false }) {
                    Column(Modifier.fillMaxWidth()) {
                        Text(model.name + if (model.isDefault) " · Katalogdefault" else "")
                        Text(model.description, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        } }, confirmButton = { TextButton(onClick = { modelPicker = false }) { Text("Schließen") } })
    if (effortPicker) AlertDialog(onDismissRequest = { effortPicker = false },
        title = { Text("Reasoning für nächste Nachricht") },
        text = { LazyColumn {
            items(selectedModel?.efforts.orEmpty(), key = { it.effort }) { option ->
                TextButton(onClick = { vm.selectEffort(option.effort); effortPicker = false }) {
                    Column(Modifier.fillMaxWidth()) { Text(option.effort); Text(option.description) }
                }
            }
        } }, confirmButton = { TextButton(onClick = { effortPicker = false }) { Text("Schließen") } })
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    Row(verticalAlignment = Alignment.CenterVertically) {
        StatusBadge(state.connection, state.connected)
        Spacer(Modifier.width(8.dp))
        state.timeline.thread?.let { Text(statusLabel(it.status), style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant) }
        Spacer(Modifier.weight(1f))
        if (state.compaction?.pending != true && (state.timeline.busy || state.interruptTurnId != null)) {
            val target = state.stoppableTurnId
            OutlinedButton(onClick = { target?.let { vm.stop(session, it) } }, enabled = target != null,
                modifier = Modifier.heightIn(min = 48.dp)) {
                Text(if (state.interruptTurnId != null) "Wird gestoppt …" else "Stoppen")
            }
        }
    }
    state.compaction?.let { compact ->
        Text(compact.label, style = MaterialTheme.typography.labelMedium)
        if (compact.phase == "unknown") TextButton(onClick = { scope.launch { session.refreshVisible() } }) {
            Text("Zustand abgleichen")
        }
    }
    if (state.requests.isNotEmpty()) Text("Deine Antwort ist gefragt · ${state.requests.size} offene Rückfrage(n)",
        style = MaterialTheme.typography.labelLarge)
    if (!state.connected) LinearProgressIndicator(Modifier.fillMaxWidth())
    state.error?.let { ErrorText(it) }
    // Capture the reader's intent while scrolling, before incoming content changes the layout.
    var followTail by remember { mutableStateOf(true) }
    val tailIndex = turns.sumOf { 1 + it.items.size + it.artifacts.size + if (it.error == null) 0 else 1 } + state.requests.size
    LaunchedEffect(listState) {
        snapshotFlow { listState.isScrollInProgress to listState.canScrollForward }.collect { (scrolling, more) ->
            if (scrolling) followTail = !more
        }
    }
    LaunchedEffect(turns, state.requests) {
        if (followTail && turns.isNotEmpty()) listState.scrollToItem(
            if (state.requests.isNotEmpty()) tailIndex - state.requests.size else tailIndex)
    }
    LazyColumn(state = listState, modifier = Modifier.weight(1f).fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp), contentPadding = PaddingValues(vertical = 12.dp)) {
        if (turns.isEmpty()) item { EmptyState(if (state.connected) "Was möchtest du angehen?" else "Verlauf wird geladen …",
            if (state.connected) "Stelle eine Frage, beschreibe eine Aufgabe oder hänge eine Datei an." else "Deine Unterhaltung wird mit dem Server abgeglichen.") }
        turns.forEach { turn ->
            item(key = "turn:${turn.id}") {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    HorizontalDivider(Modifier.weight(1f))
                    Text("${statusLabel(turn.status)} · ${turn.id.take(8)}", style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    HorizontalDivider(Modifier.weight(1f))
                }
                state.reroutes[turn.id]?.let { Text("Laufzeitumleitung: ${it.from} → ${it.to}",
                    style = MaterialTheme.typography.labelSmall) }
            }
            items(turn.items, key = { "item:${turn.id}:${it.id}" }) { message ->
                    val live = state.timeline.live[turn.id]?.items?.any { it.id == message.id } == true
                    MessageCard(message, live && !turn.terminal, turn.terminal)
            }
            items(turn.artifacts, key = { "artifact:${turn.id}:${it.id}" }) { artifact ->
                ArtifactCard(artifact, vm.threadId.orEmpty(), vm)
            }
            turn.error?.let { error -> item(key = "error:${turn.id}") { ErrorText(error) } }
        }
        items(state.requests, key = { "request:${it.id}" }) { request ->
            UserInputCard(request, state.connected, request.id in state.answerAttempts,
                request.id in state.answerSending, state.answerErrors[request.id],
                onAnswer = { answers -> scope.launch { session.answer(request.id, answers) } },
                onReview = { scope.launch { session.reviewAnswer(request.id) } })
        }
        item(key = "tail") { Spacer(Modifier.height(1.dp)) }
    }
    if (!followTail) {
        TextButton(onClick = { followTail = true; scope.launch { listState.scrollToItem(tailIndex) } },
            modifier = Modifier.align(Alignment.End)) { Text("Zu den neuesten Nachrichten") }
    }
    if (vm.uncertain) {
        ErrorText(vm.sendError ?: "Letzter Sendevorgang unbestätigt. Er kann bereits ausgeführt worden sein.")
        Text("Verlauf prüfen. Die Nachricht wird nicht automatisch erneut gesendet.", style = MaterialTheme.typography.bodySmall)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { scope.launch { session.refreshVisible() } }, enabled = state.connected) { Text("Verlauf laden") }
            OutlinedButton(onClick = vm::reviewedUnknown, enabled = state.connected && !state.timeline.busy) { Text("Verlauf geprüft") }
        }
    }
    if (!vm.uncertain) vm.sendError?.let { ErrorText(it) }
    state.interruptError?.let { ErrorText(it) }
    Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            SessionStatusLine(state)
            if (state.requests.isEmpty()) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(0.dp), itemVerticalAlignment = Alignment.CenterVertically) {
                    Text("Nächste Nachricht${if (vm.nextModel != null) " · vorgemerkt" else ""}",
                        style = MaterialTheme.typography.labelSmall)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        AssistChip(onClick = { modelPicker = true }, enabled = vm.models.isNotEmpty() && !vm.sending && !vm.uncertain,
                            label = { Text(selectedModel?.name ?: vm.nextModel ?: configured?.model ?: "Modell unbekannt") })
                        AssistChip(onClick = { effortPicker = true }, enabled = !selectedModel?.efforts.isNullOrEmpty() && !vm.sending && !vm.uncertain,
                            label = { Text((if (vm.nextModel != null) vm.nextEffort else configured?.reasoningEffort) ?: "Reasoning unbekannt") })
                    }
                    if (vm.nextModel != null) TextButton(onClick = vm::clearSelection, enabled = !vm.sending && !vm.uncertain) { Text("Auswahl verwerfen") }
                }
                vm.modelError?.let { ErrorText("Modellkatalog: $it") }
                if (vm.modelError != null || vm.models.isEmpty()) TextButton(onClick = vm::loadModels, enabled = !vm.modelsLoading) {
                    Text(if (vm.modelsLoading) "Katalog lädt …" else "Modellkatalog laden")
                }
            }
            val count = TransferPolicy.messageLength(vm.draft)
            val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
                vm.addAttachments(uris)
            }
            vm.attachmentError?.let { ErrorText(it) }
            if (vm.images.isNotEmpty() || vm.textFiles.isNotEmpty()) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    vm.images.forEach { image ->
                        Surface(shape = MaterialTheme.shapes.small, color = MaterialTheme.colorScheme.surfaceContainer) {
                            Row(Modifier.padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
                                ImageThumbnail(image.uri)
                                Text(image.name, style = MaterialTheme.typography.labelSmall,
                                    modifier = Modifier.widthIn(max = 160.dp).padding(start = 8.dp), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                IconButton(onClick = { vm.removeImage(image.uri) }, enabled = !vm.sending,
                                    modifier = Modifier.semantics { contentDescription = "${image.name} entfernen" }) { Text("×") }
                            }
                        }
                    }
                    vm.textFiles.forEach { file ->
                        Surface(shape = MaterialTheme.shapes.small, color = MaterialTheme.colorScheme.surfaceContainer) {
                            Row(Modifier.padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text(file.name, modifier = Modifier.widthIn(max = 200.dp).padding(start = 8.dp), style = MaterialTheme.typography.labelSmall,
                                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                                IconButton(onClick = { vm.removeText(file.uri) }, enabled = !vm.sending,
                                    modifier = Modifier.semantics { contentDescription = "${file.name} entfernen" }) { Text("×") }
                            }
                        }
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedButton(onClick = { picker.launch(TransferPolicy.pickerMimeTypes) },
                    enabled = !vm.sending && !vm.uncertain && (vm.images.size < 4 || vm.textFiles.size < 2),
                    contentPadding = PaddingValues(horizontal = 12.dp),
                    modifier = Modifier.size(52.dp).semantics { contentDescription = "Datei anhängen" }) {
                    Text("+", style = MaterialTheme.typography.titleLarge)
                }
                OutlinedTextField(value = vm.draft, onValueChange = vm::editDraft, modifier = Modifier.weight(1f),
                    label = { Text(if (state.canMessageDuringInput) "Weitere Nachricht an Codex" else "Nachricht an Codex") },
                    shape = MaterialTheme.shapes.medium,
                    minLines = 1, maxLines = 5,
                    enabled = !vm.sending, isError = count > TransferPolicy.MAX_MESSAGE,
                    supportingText = if (count > TransferPolicy.MESSAGE_WARNING) ({ Text(if (count > TransferPolicy.MAX_MESSAGE)
                        TransferPolicy.MESSAGE_LIMIT_ERROR + " ($count / ${TransferPolicy.MAX_MESSAGE})"
                        else "$count / ${TransferPolicy.MAX_MESSAGE} Zeichen") }) else null)
                Button(onClick = vm::send, modifier = Modifier.heightIn(min = 52.dp),
                    enabled = state.connected && (!state.timeline.busy || state.canMessageDuringInput) && state.compaction?.pending != true && state.interruptTurnId == null && !vm.sending && !vm.uncertain &&
                        (vm.draft.isNotBlank() || vm.images.isNotEmpty() || vm.textFiles.isNotEmpty()) && count <= TransferPolicy.MAX_MESSAGE) {
                    Text(if (vm.sending) "Wird gesendet …" else if (state.timeline.busy && !state.canMessageDuringInput) "Codex arbeitet …" else "Senden")
                }
            }
        }
    }
    Spacer(Modifier.height(8.dp))
}

@Composable
private fun ImageThumbnail(uri: Uri) {
    val resolver = LocalContext.current.contentResolver
    val bitmap by produceState<android.graphics.Bitmap?>(null, uri) {
        value = withContext(Dispatchers.IO) {
            resolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, BitmapFactory.Options().apply { inSampleSize = 8 })
            }
        }
    }
    bitmap?.let { Image(it.asImageBitmap(), contentDescription = "Bildvorschau",
        modifier = Modifier.size(48.dp).clip(MaterialTheme.shapes.small), contentScale = ContentScale.Crop) }
}

@Composable
internal fun MessageCard(message: Message, live: Boolean, turnTerminal: Boolean = false) {
    if (message.activity != null) {
        ActivityCard(message, turnTerminal)
        return
    }
    val user = message.type == "userMessage"
    val agent = message.type == "agentMessage"
    Box(Modifier.fillMaxWidth(), contentAlignment = if (user) Alignment.CenterEnd else Alignment.CenterStart) {
        Surface(color = if (user) MaterialTheme.colorScheme.primaryContainer else if (agent) MaterialTheme.colorScheme.surface
                else MaterialTheme.colorScheme.surfaceContainerLow,
            shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth(if (user) 0.88f else 1f)) {
            Column(Modifier.padding(if (agent || user) 22.dp else 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(if (user) "Du" else if (agent) "Codex" else if (message.isCompaction) "Kontextkomprimierung" else "System",
                    style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                if (live && agent) Text("Schreibt … · Live-Ausschnitt",
                    style = MaterialTheme.typography.labelSmall)
                if (message.text.isNotBlank()) SelectionContainer { Text(message.text, style = MaterialTheme.typography.bodyLarge) }
                if (message.images > 0) Text("▧ ${message.images} Bild${if (message.images == 1) "" else "er"} angehängt",
                    style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

@Composable
private fun ErrorText(text: String) {
    Surface(color = MaterialTheme.colorScheme.errorContainer, contentColor = MaterialTheme.colorScheme.onErrorContainer,
        shape = MaterialTheme.shapes.small, modifier = Modifier.fillMaxWidth()) {
        Text(text, Modifier.padding(horizontal = 16.dp, vertical = 12.dp), style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun ConnectionSettingsScreen(vm: PadViewModel) {
    var url by remember(vm.serverUrl) { mutableStateOf(vm.serverUrl) }
    // Intentionally never saveable: no token in Bundle, SavedStateHandle or restored UI text.
    var enteredToken by remember { mutableStateOf("") }
    Column(Modifier.widthIn(max = 680.dp).fillMaxWidth().padding(top = 16.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("Serververbindung", style = MaterialTheme.typography.titleLarge)
        OutlinedTextField(value = url, onValueChange = { url = it; vm.settingsEdited() }, modifier = Modifier.fillMaxWidth(),
            enabled = !vm.settingsBusy, singleLine = true, label = { Text("Server-URL") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, autoCorrectEnabled = false))
        OutlinedTextField(value = enteredToken, onValueChange = { enteredToken = it; vm.settingsEdited() },
            modifier = Modifier.fillMaxWidth(), enabled = !vm.settingsBusy, singleLine = true,
            label = { Text(if (vm.hasToken) "Neues Zugriffstoken (optional)" else "Zugriffstoken") },
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, autoCorrectEnabled = false),
            supportingText = { Text(if (vm.hasToken) "Token sicher gespeichert. Leer lassen zum Beibehalten." else "Token aus deiner Serverkonfiguration eingeben.") })
        Text("Bei einer anderen Serveradresse das Token erneut eingeben. Speichern öffnet die Workspace-Liste.",
            style = MaterialTheme.typography.bodySmall)
        Text(vm.connectionStatus, style = MaterialTheme.typography.labelLarge)
        vm.settingsError?.let { ErrorText(it) }
        if (vm.settingsBusy) LinearProgressIndicator(Modifier.fillMaxWidth())
        OutlinedButton(onClick = { vm.testConnection(url, enteredToken) }, enabled = !vm.settingsBusy) {
            Text("Verbindung testen")
        }
        Button(onClick = { vm.saveConnection(url, enteredToken) { enteredToken = "" } }, enabled = !vm.settingsBusy) {
            Text("Speichern")
        }
    }
}

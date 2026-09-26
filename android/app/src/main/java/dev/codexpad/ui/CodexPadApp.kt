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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.codexpad.data.ThreadSession
import dev.codexpad.model.Message
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

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
    MaterialTheme(colorScheme = lightColorScheme(primary = Color(0xFF245E58), background = Color(0xFFF7F8F6))) {
        Scaffold(topBar = {
            TopAppBar(title = {
                Column {
                    Text(if (vm.showSettings) "Einstellungen" else if (vm.threadId != null) "Thread" else vm.workspace?.name ?: "CodexPad", maxLines = 1)
                    Text(vm.workspace?.name ?: "Workspaces", style = MaterialTheme.typography.labelMedium)
                }
            }, navigationIcon = {
                if (vm.showSettings && vm.hasToken) TextButton(onClick = vm::closeSettings, enabled = !vm.settingsBusy) { Text("Zurück") }
                else if (vm.workspace != null && !vm.showSettings) TextButton(onClick = vm::back) { Text("Zurück") }
            }, actions = {
                if (!vm.showSettings) TextButton(onClick = vm::openSettings,
                    enabled = vm.ready && !vm.creating && !vm.sending) { Text("Einstellungen") }
            })
        }) { padding ->
            Box(Modifier.fillMaxSize().padding(padding).imePadding(), contentAlignment = Alignment.TopCenter) {
                Column(Modifier.widthIn(max = 1000.dp).fillMaxSize().padding(horizontal = 20.dp),
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
                        Text(vm.serverUrl, style = MaterialTheme.typography.labelSmall)
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            OutlinedButton(onClick = vm::reload, enabled = !vm.loading) { Text("Aktualisieren") }
                            if (vm.workspace != null) Button(onClick = vm::createThread,
                                enabled = !vm.creating && !vm.createUncertain) {
                                Text(if (vm.creating) "Wird angelegt …" else "Neuer Thread")
                            }
                        }
                        if (vm.loading) LinearProgressIndicator(Modifier.fillMaxWidth())
                        vm.error?.let { ErrorText(it) }
                        if (vm.workspace == null) {
                            if (!vm.loading && vm.error == null && vm.workspaces.isEmpty())
                                Text("Keine Workspaces konfiguriert. Der Server stellt die Projekte bereit.")
                            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                items(vm.workspaces, key = { it.id }) { ws ->
                                    Card(onClick = { vm.selectWorkspace(ws) }, modifier = Modifier.fillMaxWidth()) {
                                        Column(Modifier.padding(20.dp)) {
                                            Text(ws.name, style = MaterialTheme.typography.titleMedium)
                                            Text(ws.id, style = MaterialTheme.typography.bodySmall)
                                        }
                                    }
                                }
                            }
                        } else {
                            if (vm.createUncertain) {
                                ErrorText("Eine Thread-Anlage ist unbestätigt. Erst die aktualisierte Liste prüfen.")
                                OutlinedButton(onClick = vm::reviewedCreate, enabled = !vm.loading && vm.error == null) {
                                    Text("Liste geprüft")
                                }
                            }
                            if (!vm.loading && vm.error == null && vm.threads.isEmpty()) Text("Noch keine Threads. Starte eine neue Unterhaltung.")
                            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                items(vm.threads, key = { it.id }) { thread ->
                                    Card(onClick = { vm.openThread(thread.id) }, modifier = Modifier.fillMaxWidth()) {
                                        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                            Text(thread.preview.ifBlank { "Thread ${thread.id.take(8)}" },
                                                style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                            Text(thread.id, style = MaterialTheme.typography.bodySmall)
                                            Text("Status: ${thread.status}", style = MaterialTheme.typography.labelMedium)
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
    Text(vm.threadId.orEmpty(), style = MaterialTheme.typography.labelSmall)
    var contextMenu by remember(session) { mutableStateOf(false) }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(state.connection, style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
        Box {
            TextButton(onClick = { contextMenu = true }) { Text("Kontext") }
            DropdownMenu(expanded = contextMenu, onDismissRequest = { contextMenu = false }) {
                Text("Codex fasst den bisherigen Kontext zusammen, um Platz im Kontextfenster zu schaffen.",
                    modifier = Modifier.widthIn(max = 280.dp).padding(16.dp), style = MaterialTheme.typography.bodySmall)
                DropdownMenuItem(text = { Text("Kontext komprimieren") },
                    enabled = state.canCompact && !vm.sending && !vm.uncertain,
                    onClick = { contextMenu = false; vm.compact(session) })
            }
        }
    }
    state.compaction?.let { compact ->
        Text(compact.label, style = MaterialTheme.typography.labelMedium)
        if (compact.phase == "unknown") TextButton(onClick = { scope.launch { session.refreshVisible() } }) {
            Text("Zustand abgleichen")
        }
    }
    state.timeline.thread?.let { Text("Threadstatus: ${it.status}", style = MaterialTheme.typography.labelMedium) }
    if (state.requests.isNotEmpty()) Text("Codex wartet auf eine Angabe · ${state.requests.size} offene Rückfrage(n)",
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
        verticalArrangement = Arrangement.spacedBy(20.dp), contentPadding = PaddingValues(vertical = 12.dp)) {
        if (turns.isEmpty()) item { Text(if (state.connected) "Noch keine Nachrichten." else "Verlauf wird geladen …") }
        turns.forEach { turn ->
            item(key = "turn:${turn.id}") {
                Text("Turn ${turn.id.take(8)} · ${turn.status}", style = MaterialTheme.typography.labelMedium)
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
    state.interruptError?.let { ErrorText(it) }
    if (state.compaction?.pending != true && (state.timeline.busy || state.interruptTurnId != null)) {
        val target = state.stoppableTurnId
        OutlinedButton(onClick = { target?.let { vm.stop(session, it) } }, enabled = target != null,
            modifier = Modifier.align(Alignment.End)) {
            Text(if (state.interruptTurnId != null) "Wird gestoppt …" else "Stoppen")
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text("Konfiguriert: ${configured?.model ?: "unbekannt"} · Reasoning ${configured?.reasoningEffort ?: "unbekannt"}",
            style = MaterialTheme.typography.labelMedium)
        Text(state.usage.label, style = MaterialTheme.typography.labelSmall)
    }
    if (state.requests.isEmpty()) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(0.dp), itemVerticalAlignment = Alignment.CenterVertically) {
            Text("Auswahl für nächste Nachricht${if (vm.nextModel != null) " · vorgemerkt" else " · ohne Override"}",
                style = MaterialTheme.typography.labelSmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AssistChip(onClick = { modelPicker = true }, enabled = vm.models.isNotEmpty() && !vm.sending && !vm.uncertain,
                    label = { Text(selectedModel?.name ?: vm.nextModel ?: configured?.model ?: "Modell unbekannt") })
                AssistChip(onClick = { effortPicker = true }, enabled = !selectedModel?.efforts.isNullOrEmpty() && !vm.sending && !vm.uncertain,
                    label = { Text((if (vm.nextModel != null) vm.nextEffort else configured?.reasoningEffort) ?: "Reasoning unbekannt") })
            }
        }
        if (vm.nextModel != null) TextButton(onClick = vm::clearSelection, enabled = !vm.sending && !vm.uncertain) { Text("Auswahl verwerfen") }
        vm.modelError?.let { ErrorText("Modellkatalog: $it") }
        if (vm.modelError != null || vm.models.isEmpty()) TextButton(onClick = vm::loadModels, enabled = !vm.modelsLoading) {
            Text(if (vm.modelsLoading) "Katalog lädt …" else "Modellkatalog laden")
        }
    }
    val count = vm.draft.codePointCount(0, vm.draft.length)
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        vm.addAttachments(uris)
    }
    vm.attachmentError?.let { ErrorText(it) }
    if (vm.images.isNotEmpty() || vm.textFiles.isNotEmpty()) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            vm.images.forEach { image ->
                Surface(shape = MaterialTheme.shapes.small, color = MaterialTheme.colorScheme.surfaceContainer) {
                    Row(Modifier.padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
                        ImageThumbnail(image.uri)
                        Text(image.name.take(18), style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.widthIn(max = 90.dp), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        TextButton(onClick = { vm.removeImage(image.uri) }, enabled = !vm.sending) { Text("×") }
                    }
                }
            }
            vm.textFiles.forEach { file ->
                Surface(shape = MaterialTheme.shapes.small, color = MaterialTheme.colorScheme.surfaceContainer) {
                    Row(Modifier.padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("▤ ${file.name.take(24)}", style = MaterialTheme.typography.labelSmall,
                            maxLines = 1, overflow = TextOverflow.Ellipsis)
                        TextButton(onClick = { vm.removeText(file.uri) }, enabled = !vm.sending) { Text("×") }
                    }
                }
            }
        }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        OutlinedButton(onClick = { picker.launch(arrayOf("image/png", "image/jpeg", "image/webp", "text/plain", "text/markdown")) },
            enabled = !vm.sending && !vm.uncertain && (vm.images.size < 4 || vm.textFiles.size < 2),
            contentPadding = PaddingValues(horizontal = 12.dp),
            modifier = Modifier.padding(bottom = 8.dp).semantics { contentDescription = "Datei anhängen" }) {
            Text("+", style = MaterialTheme.typography.titleLarge)
        }
        OutlinedTextField(value = vm.draft, onValueChange = vm::editDraft, modifier = Modifier.weight(1f),
            label = { Text(if (state.canMessageDuringInput) "Weitere Nachricht an Codex" else "Nachricht an Codex") },
            minLines = if (state.requests.isEmpty()) 2 else 1, maxLines = 5,
            enabled = !vm.sending, isError = count > 4096,
            supportingText = { Text("$count / 4096 Zeichen") })
        Button(onClick = vm::send, modifier = Modifier.padding(bottom = 8.dp),
            enabled = state.connected && (!state.timeline.busy || state.canMessageDuringInput) && state.compaction?.pending != true && state.interruptTurnId == null && !vm.sending && !vm.uncertain &&
                (vm.draft.isNotBlank() || vm.images.isNotEmpty() || vm.textFiles.isNotEmpty()) && count <= 4096) {
            Text(if (vm.sending) "Wird gesendet …" else if (state.timeline.busy && !state.canMessageDuringInput) "Turn läuft …" else "Senden")
        }
    }
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
        modifier = Modifier.size(48.dp), contentScale = ContentScale.Crop) }
}

@Composable
internal fun MessageCard(message: Message, live: Boolean, turnTerminal: Boolean = false) {
    if (message.activity != null) {
        ActivityCard(message, turnTerminal)
        return
    }
    val user = message.type == "userMessage"
    val agent = message.type == "agentMessage"
    Surface(color = if (user) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer,
        shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(if (user) "Du" else if (agent) "Codex" else if (message.isCompaction) "Kontextkomprimierung" else "Servereintrag", fontWeight = FontWeight.SemiBold)
            if (live && agent) Text("Live-Ausschnitt · bis zum History-Abgleich möglicherweise unvollständig",
                style = MaterialTheme.typography.labelSmall)
            if (message.text.isNotBlank()) SelectionContainer { Text(message.text, style = MaterialTheme.typography.bodyLarge) }
            if (message.images > 0) Text("▧ ${message.images} Bild${if (message.images == 1) "" else "er"} angehängt",
                style = MaterialTheme.typography.labelMedium)
        }
    }
}

@Composable
private fun ErrorText(text: String) {
    Text(text, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
}

@Composable
private fun ConnectionSettingsScreen(vm: PadViewModel) {
    var url by remember(vm.serverUrl) { mutableStateOf(vm.serverUrl) }
    // Intentionally never saveable: no token in Bundle, SavedStateHandle or restored UI text.
    var enteredToken by remember { mutableStateOf("") }
    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
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

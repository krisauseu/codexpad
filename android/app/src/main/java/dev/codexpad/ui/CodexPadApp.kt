package dev.codexpad.ui

import androidx.activity.compose.BackHandler
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
                        ThreadDetail(vm, session)
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
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    Text(vm.threadId.orEmpty(), style = MaterialTheme.typography.labelSmall)
    Text(state.connection, style = MaterialTheme.typography.labelLarge)
    state.timeline.thread?.let { Text("Threadstatus: ${it.status}", style = MaterialTheme.typography.labelMedium) }
    if (!state.connected) LinearProgressIndicator(Modifier.fillMaxWidth())
    state.error?.let { ErrorText(it) }
    // Capture the reader's intent while scrolling, before incoming content changes the layout.
    var followTail by remember { mutableStateOf(true) }
    val tailIndex = turns.sumOf { 1 + it.items.size + if (it.error == null) 0 else 1 }
    LaunchedEffect(listState) {
        snapshotFlow { listState.isScrollInProgress to listState.canScrollForward }.collect { (scrolling, more) ->
            if (scrolling) followTail = !more
        }
    }
    LaunchedEffect(turns) {
        if (followTail && turns.isNotEmpty()) listState.scrollToItem(tailIndex)
    }
    LazyColumn(state = listState, modifier = Modifier.weight(1f).fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(20.dp), contentPadding = PaddingValues(vertical = 12.dp)) {
        if (turns.isEmpty()) item { Text(if (state.connected) "Noch keine Nachrichten." else "Verlauf wird geladen …") }
        turns.forEach { turn ->
            item(key = "turn:${turn.id}") {
                Text("Turn ${turn.id.take(8)} · ${turn.status}", style = MaterialTheme.typography.labelMedium)
            }
            items(turn.items, key = { "item:${turn.id}:${it.id}" }) { message ->
                    val live = state.timeline.live[turn.id]?.items?.any { it.id == message.id } == true
                    MessageCard(message, live && !turn.terminal)
            }
            turn.error?.let { error -> item(key = "error:${turn.id}") { ErrorText(error) } }
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
    val count = vm.draft.codePointCount(0, vm.draft.length)
    OutlinedTextField(value = vm.draft, onValueChange = vm::editDraft, modifier = Modifier.fillMaxWidth(),
        label = { Text("Nachricht an Codex") }, minLines = 2, maxLines = 5,
        enabled = !vm.sending, isError = count > 4096,
        supportingText = { Text("$count / 4096 Zeichen") })
    Button(onClick = vm::send, modifier = Modifier.align(Alignment.End).padding(bottom = 8.dp),
        enabled = state.connected && !state.timeline.busy && !vm.sending && !vm.uncertain &&
            vm.draft.isNotBlank() && count <= 4096) {
        Text(if (vm.sending) "Wird gesendet …" else if (state.timeline.busy) "Turn läuft …" else "Senden")
    }
}

@Composable
private fun MessageCard(message: Message, live: Boolean) {
    val user = message.type == "userMessage"
    val agent = message.type == "agentMessage"
    Surface(color = if (user) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer,
        shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(if (user) "Du" else if (agent) "Codex" else "Servereintrag", fontWeight = FontWeight.SemiBold)
            if (live && agent) Text("Live-Ausschnitt · bis zum History-Abgleich möglicherweise unvollständig",
                style = MaterialTheme.typography.labelSmall)
            SelectionContainer { Text(message.text, style = MaterialTheme.typography.bodyLarge) }
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

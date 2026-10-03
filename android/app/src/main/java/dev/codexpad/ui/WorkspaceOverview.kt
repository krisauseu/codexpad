package dev.codexpad.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.codexpad.model.Workspace
import dev.codexpad.model.workspaceNameError

@Composable
internal fun ColumnScope.WorkspaceOverview(vm: PadViewModel) {
    var action by remember { mutableStateOf<String?>(null) }
    var selected by remember { mutableStateOf<Workspace?>(null) }
    var name by remember { mutableStateOf("") }
    fun start(kind: String, ws: Workspace? = null) {
        selected = ws
        name = ws?.name.orEmpty()
        action = kind
        if (kind == "delete") vm.inspectWorkspace(ws!!) else vm.beginWorkspaceAction()
    }
    Button(onClick = { start("create") }, enabled = !vm.loading && !vm.workspaceBusy,
        modifier = Modifier.heightIn(min = 48.dp)) { Text("Neuer Workspace") }
    vm.workspaceNotice?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
    if (vm.workspaceBusy) LinearProgressIndicator(Modifier.fillMaxWidth())
    if (!vm.loading && vm.error == null && vm.workspaces.isEmpty() && !vm.workspaceBusy)
        Text("Noch keine Workspaces. Lege mit „Neuer Workspace“ einen Arbeitsbereich an.")
    LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        items(vm.workspaces, key = { it.id }) { ws ->
            var menu by remember { mutableStateOf(false) }
            Card(onClick = { vm.selectWorkspace(ws) }, enabled = !vm.workspaceBusy,
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f).padding(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(ws.name, style = MaterialTheme.typography.titleMedium)
                        Text("Threads ansehen →", style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary)
                    }
                    Box {
                        TextButton(onClick = { menu = true }, enabled = !vm.workspaceBusy,
                            modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                                .semantics { contentDescription = "Aktionen für ${ws.name}" }) { Text("⋮") }
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                            DropdownMenuItem(text = { Text("Umbenennen") },
                                onClick = { menu = false; start("rename", ws) })
                            DropdownMenuItem(text = { Text("Löschen") },
                                onClick = { menu = false; start("delete", ws) })
                        }
                    }
                }
            }
        }
    }
    if (action != null) {
        val deleting = action == "delete"
        val validation = workspaceNameError(name)
        val inspection = vm.deleteInspection
        AlertDialog(onDismissRequest = { if (!vm.workspaceBusy) action = null },
            title = { Text(when (action) {
                "create" -> "Workspace anlegen"
                "rename" -> "Workspace umbenennen"
                else -> "Workspace löschen?"
            }) },
            text = { Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (deleting) {
                    Text("Der Workspace-Ordner „${selected?.name}“ und alle darin enthaltenen Dateien werden dauerhaft gelöscht.")
                    if (inspection == null && vm.workspaceActionError == null) Text("Inhalt und Threads werden geprüft …")
                    inspection?.let {
                        Text("${it.files} Dateien/Verknüpfungen · ${it.directories} Unterordner · ${it.threads} Threads")
                        if (it.threads > 0) Text("Löschen gesperrt: Vorhandene oder archivierte Threads benötigen diesen Ordner. Ihre Sessiondaten bleiben erhalten.",
                            color = MaterialTheme.colorScheme.error)
                    }
                } else {
                    OutlinedTextField(value = name, onValueChange = { name = it },
                        label = { Text("Workspace-Name") }, singleLine = true,
                        enabled = !vm.workspaceBusy, isError = validation != null && name.isNotEmpty())
                    Text(validation ?: "Der Name wird als Ordnername auf dem Server verwendet.",
                        style = MaterialTheme.typography.bodySmall)
                    if (action == "rename") Text("Workspaces mit vorhandenen oder archivierten Threads können zum Schutz ihrer gespeicherten Pfade nicht umbenannt werden.",
                        style = MaterialTheme.typography.bodySmall)
                }
                vm.workspaceActionError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                if (vm.workspaceBusy) LinearProgressIndicator(Modifier.fillMaxWidth())
            } },
            confirmButton = {
                TextButton(enabled = !vm.workspaceBusy && if (deleting)
                    inspection != null && inspection.threads == 0 else validation == null,
                    onClick = { vm.changeWorkspace(selected, name, deleting) { action = null } }) {
                    Text(if (deleting) "Endgültig löschen" else "Speichern")
                }
            },
            dismissButton = { TextButton(onClick = { action = null }, enabled = !vm.workspaceBusy) { Text("Abbrechen") } })
    }
}

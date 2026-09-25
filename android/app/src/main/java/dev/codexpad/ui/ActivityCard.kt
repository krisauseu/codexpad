package dev.codexpad.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.codexpad.model.Activity
import dev.codexpad.model.Message

@Composable
internal fun ActivityCard(message: Message, turnTerminal: Boolean = false) {
    val activity = message.activity ?: return
    var expanded by rememberSaveable(message.id) { mutableStateOf(false) }
    val title = when (activity) {
        is Activity.Command -> "Command · ${activity.command ?: "unbekannt"}"
        is Activity.Mcp -> "MCP · ${listOfNotNull(activity.server, activity.tool).joinToString(" / ").ifEmpty { "Tool unbekannt" }}"
        is Activity.Dynamic -> "Tool · ${listOfNotNull(activity.namespace, activity.tool).joinToString(" / ").ifEmpty { "unbekannt" }}"
        is Activity.Files -> "Dateiänderung · ${activity.changes.size} Pfad(e) · ${activity.changes.firstOrNull()?.path.orEmpty()}"
    }
    val failure = activity.status in setOf("failed", "declined", "interrupted") ||
        (activity is Activity.Command && activity.exitCode != null && activity.exitCode != 0) ||
        (activity is Activity.Mcp && activity.error != null) || (activity is Activity.Dynamic && activity.success == false)
    val running = activity.status == "inProgress" && !message.completedEvent
    val status = when {
        failure -> if (activity.status == "declined") "Abgelehnt" else "Fehler / abgebrochen"
        running && turnTerminal -> "Turn beendet · Itemabschluss unbestätigt"
        running -> "Läuft"
        activity is Activity.Mcp && activity.status == "completed" && activity.result == null -> "Abgeschlossen · Ergebnis fehlt"
        activity is Activity.Dynamic && activity.status == "completed" && activity.success == null -> "Abgeschlossen · Erfolg unbekannt"
        activity.status == "completed" -> "Abgeschlossen"
        message.completedEvent -> "Beendet · Status unbekannt"
        else -> "Status: ${activity.status}"
    }
    Surface(color = MaterialTheme.colorScheme.surfaceContainer, shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth()) {
        Column {
            TextButton(onClick = { expanded = !expanded }, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(title, maxLines = 2, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold)
                    Text("${if (expanded) "▾" else "▸"} $status · ${if (expanded) "Details schließen" else "Details öffnen"}",
                        color = when { failure -> MaterialTheme.colorScheme.error; running -> MaterialTheme.colorScheme.primary
                            else -> MaterialTheme.colorScheme.onSurfaceVariant }, style = MaterialTheme.typography.labelMedium)
                }
            }
            if (expanded) Column(Modifier.fillMaxWidth().heightIn(max = 360.dp)
                .verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                when (activity) {
                    is Activity.Command -> {
                        Detail("Command", activity.command)
                        activity.cwd?.let { Detail("Arbeitsverzeichnis", it) }
                        Text("Exit-Code: ${activity.exitCode ?: "unbekannt"}" +
                            (activity.durationMs?.let { " · Dauer: $it ms" } ?: ""))
                        if (activity.liveOutput != null && activity.output != null)
                            Detail("Letzter Snapshot · kann sich mit Live-Ausgabe überschneiden", activity.output)
                        Detail(if (activity.liveOutput != null) "Live-Ausgabe · möglicherweise unvollständiger Ausschnitt"
                            else "Aggregierte Ausgabe", activity.liveOutput ?: activity.output)
                    }
                    is Activity.Mcp -> {
                        Detail("Argumente", activity.arguments)
                        activity.error?.let { Detail("Fehler", it) }
                        Detail("Ergebnis", activity.result)
                        RawDetails(activity.rawResult)
                    }
                    is Activity.Dynamic -> {
                        Text("Erfolg: ${when (activity.success) { true -> "ja"; false -> "nein"; null -> "unbekannt" }}")
                        Detail("Argumente", activity.arguments)
                        Detail("Tool-Inhalt", activity.content)
                        RawDetails(activity.rawResult)
                    }
                    is Activity.Files -> {
                        Text("Historischer Item-Diff · kein aktueller Git-Arbeitsbaum", style = MaterialTheme.typography.labelSmall)
                        if (running) Text("Änderung läuft · noch nicht als angewandt bestätigt")
                        if (activity.changes.isEmpty()) Text("Keine Pfaddetails vorhanden")
                        // Bound both the number of composed paths and each diff, including large history items.
                        var page by rememberSaveable(message.id) { mutableIntStateOf(0) }
                        val pages = ((activity.changes.size + 9) / 10).coerceAtLeast(1)
                        val current = page.coerceAtMost(pages - 1)
                        activity.changes.drop(current * 10).take(10).forEach { change ->
                            key(change.path) {
                                Detail(when (change.kind) { "add" -> "Hinzufügen"; "delete" -> "Löschen"; "update" -> "Änderung"; else -> change.kind },
                                    change.path + (change.movePath?.let { " → $it" } ?: ""))
                                Detail("Diff / Patch", change.diff)
                            }
                        }
                        if (pages > 1) PageControls(current, pages) { page = it }
                    }
                }
            }
        }
    }
}

@Composable
private fun RawDetails(raw: String?) {
    if (raw == null) return
    var show by rememberSaveable { mutableStateOf(false) }
    TextButton(onClick = { show = !show }) { Text(if (show) "Rohdetails schließen" else "Rohdetails öffnen") }
    if (show) Detail("Rohdetails", raw)
}

@Composable
private fun Detail(label: String, value: String?) {
    Text(label, style = MaterialTheme.typography.labelMedium)
    // Paging avoids measuring megabytes of output in a single Compose Text node.
    var page by rememberSaveable(label) { mutableIntStateOf(0) }
    val content = value ?: "Nicht vorhanden"
    val pages = ((content.length + 7999) / 8000).coerceAtLeast(1)
    val current = page.coerceAtMost(pages - 1)
    SelectionContainer {
        Text(if (content.isEmpty()) "Leer" else content.substring(current * 8000, minOf(content.length, (current + 1) * 8000)),
            fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall)
    }
    if (pages > 1) PageControls(current, pages) { page = it }
}

@Composable
private fun PageControls(page: Int, pages: Int, change: (Int) -> Unit) {
    Row {
        TextButton(onClick = { change(page - 1) }, enabled = page > 0) { Text("Zurück") }
        TextButton(onClick = { change(page + 1) }, enabled = page + 1 < pages) { Text("Weiter (${page + 1}/$pages)") }
    }
}

package dev.codexpad.ui

import android.content.ClipData
import android.content.Intent
import android.widget.Toast
import android.text.format.Formatter
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import dev.codexpad.model.Artifact
import dev.codexpad.model.TransferPolicy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@Composable
fun ArtifactCard(artifact: Artifact, threadId: String, vm: PadViewModel) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var pendingPath by rememberSaveable { mutableStateOf<String?>(null) }
    val save = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(artifact.mimeType)) { uri ->
        val path = pendingPath
        pendingPath = null
        if (uri != null && path != null) scope.launch {
            busy = true
            try {
                withContext(Dispatchers.IO) {
                    context.contentResolver.openOutputStream(uri, "wt")?.use { output ->
                        File(path).inputStream().use { it.copyTo(output) }
                    } ?: error("Ziel nicht verfügbar")
                }
                Toast.makeText(context, "Datei gespeichert", Toast.LENGTH_SHORT).show()
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { error = "Speichern fehlgeschlagen. Ziel erneut wählen." }
            finally { busy = false; File(path).delete() }
        } else path?.let { File(it).delete() }
    }
    fun download(open: Boolean) {
        if (busy) return
        busy = true
        error = null
        scope.launch {
            try {
                val file = vm.downloadArtifact(threadId, artifact)
                if (open) {
                    val uri = FileProvider.getUriForFile(context, "${context.packageName}.artifacts", file)
                    val intent = Intent(Intent.ACTION_VIEW).setDataAndType(uri,
                        TransferPolicy.openMimeType(artifact.mimeType))
                        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    intent.clipData = ClipData.newRawUri(artifact.name, uri)
                    try { context.startActivity(Intent.createChooser(intent, "Datei öffnen")) }
                    catch (_: android.content.ActivityNotFoundException) { error = "Keine passende App installiert. Datei über Speichern ablegen." }
                } else {
                    pendingPath = file.absolutePath
                    save.launch(artifact.name)
                }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { error = "Download fehlgeschlagen. Verbindung prüfen und Verlauf neu laden." }
            finally { busy = false }
        }
    }
    OutlinedCard(Modifier.fillMaxWidth(), colors = CardDefaults.outlinedCardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            val type = when {
                artifact.mimeType.startsWith("image/") -> "Bild"
                artifact.mimeType == "application/pdf" -> "PDF"
                artifact.mimeType == "text/markdown" -> "Markdown"
                else -> "Text"
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Surface(shape = MaterialTheme.shapes.small, color = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer) {
                    Box(Modifier.size(56.dp), contentAlignment = Alignment.Center) {
                        Text(if (type == "Markdown") "MD" else type, style = MaterialTheme.typography.labelLarge)
                    }
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(artifact.name, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text("$type · ${Formatter.formatFileSize(context, artifact.size)}", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                FilledTonalButton(onClick = { download(true) }, modifier = Modifier.heightIn(min = 48.dp), enabled = !busy && pendingPath == null) { Text("Öffnen") }
                OutlinedButton(onClick = { download(false) }, modifier = Modifier.heightIn(min = 48.dp), enabled = !busy && pendingPath == null) { Text("Speichern") }
            }
            if (busy) {
                LinearProgressIndicator(Modifier.fillMaxWidth())
                Text("Datei wird geladen …", style = MaterialTheme.typography.labelMedium)
            }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        }
    }
}

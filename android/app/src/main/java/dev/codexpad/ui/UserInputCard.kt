package dev.codexpad.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import dev.codexpad.model.InputAnswer
import dev.codexpad.model.InputRequest

@Composable
fun UserInputCard(request: InputRequest, connected: Boolean, attempted: Boolean, sending: Boolean,
    error: String?, onAnswer: (Map<String, InputAnswer>) -> Unit, onReview: () -> Unit) {
    // Drafts live only in composition; secret answers are never saved to instance state.
    var answers by remember(request.id) { mutableStateOf(emptyMap<String, InputAnswer>()) }
    val editable = connected && request.status == "pending" && !attempted
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f))) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Codex braucht eine Angabe", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            Text(if (request.isBlocking) "Wähle eine Option oder schreibe deine Antwort. Danach arbeitet Codex weiter."
                else "Deine Antwort hilft Codex bei der laufenden Aufgabe.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            request.questions.forEach { question ->
                Text(question.question, style = MaterialTheme.typography.titleMedium)
                question.options.forEach { option ->
                    val selected = answers[question.id]?.let { it.isOption && it.value == option.label } == true
                    Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).clip(MaterialTheme.shapes.small)
                        .background(if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerLow).selectable(selected = selected,
                        enabled = editable, role = Role.RadioButton,
                        onClick = { answers = answers + (question.id to InputAnswer(option.label, true)) }).padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = selected, enabled = editable, onClick = null)
                        Column(Modifier.weight(1f).padding(start = 12.dp, top = 4.dp, bottom = 4.dp)) {
                            Text(option.label)
                            Text(option.description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                if (question.options.isEmpty() || question.isOther) {
                    OutlinedTextField(value = answers[question.id]?.takeUnless { it.isOption }?.value.orEmpty(),
                        onValueChange = { if (it.codePointCount(0, it.length) <= 4096) answers = answers + (question.id to InputAnswer(it)) },
                        shape = MaterialTheme.shapes.small,
                        label = { Text(if (question.options.isEmpty()) "Deine Antwort" else "Eigene Antwort") },
                        enabled = editable, modifier = Modifier.fillMaxWidth(), minLines = 1, maxLines = 4,
                        visualTransformation = if (question.isSecret) PasswordVisualTransformation() else VisualTransformation.None)
                }
            }
            error?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error) }
            if (!connected) Text("Verbindung wird wiederhergestellt. Deine Auswahl bleibt erhalten.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (attempted || request.status == "answering") {
                Text(if (sending) "Antwort wird gesendet …" else "Antwort übermittelt oder unbestätigt · Zustand wird abgeglichen")
                TextButton(onClick = onReview, enabled = connected && !sending) {
                    Text("Status prüfen / offene Antwort erneut bearbeiten")
                }
            } else {
                Button(onClick = { onAnswer(answers) }, modifier = Modifier.heightIn(min = 48.dp).align(Alignment.End), enabled = editable && request.questions.all {
                    answers[it.id]?.value?.isNotBlank() == true
                }) { Text("Antworten") }
            }
        }
    }
}

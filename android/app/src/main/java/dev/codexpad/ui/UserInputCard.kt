package dev.codexpad.ui

import androidx.compose.foundation.layout.*
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
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Codex braucht eine Angabe", style = MaterialTheme.typography.titleMedium)
            Text(if (request.isBlocking) "Der laufende Turn wartet auf deine Antwort."
                else "Rückfrage im laufenden Turn · deine Antwort geht an diesen Turn.", style = MaterialTheme.typography.bodySmall)
            request.questions.forEach { question ->
                Text(question.question)
                question.options.forEach { option ->
                    val selected = answers[question.id]?.let { it.isOption && it.value == option.label } == true
                    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).selectable(selected = selected,
                        enabled = editable, role = Role.RadioButton,
                        onClick = { answers = answers + (question.id to InputAnswer(option.label, true)) }),
                        verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = selected, enabled = editable, onClick = null)
                        Column(Modifier.weight(1f).padding(start = 12.dp, top = 4.dp, bottom = 4.dp)) {
                            Text(option.label)
                            Text(option.description, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
                if (question.options.isEmpty() || question.isOther) {
                    OutlinedTextField(value = answers[question.id]?.takeUnless { it.isOption }?.value.orEmpty(),
                        onValueChange = { if (it.codePointCount(0, it.length) <= 4096) answers = answers + (question.id to InputAnswer(it)) },
                        label = { Text(if (question.options.isEmpty()) "Deine Antwort" else "Eigene Antwort") },
                        enabled = editable, modifier = Modifier.fillMaxWidth(), minLines = 1, maxLines = 4,
                        visualTransformation = if (question.isSecret) PasswordVisualTransformation() else VisualTransformation.None)
                }
            }
            error?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
            if (attempted || request.status == "answering") {
                Text(if (sending) "Antwort wird gesendet …" else "Antwort übermittelt oder unbestätigt · Zustand wird abgeglichen")
                TextButton(onClick = onReview, enabled = connected && !sending) {
                    Text("Status prüfen / offene Antwort erneut bearbeiten")
                }
            } else {
                Button(onClick = { onAnswer(answers) }, enabled = editable && request.questions.all {
                    answers[it.id]?.value?.isNotBlank() == true
                }) { Text("Antworten") }
            }
        }
    }
}

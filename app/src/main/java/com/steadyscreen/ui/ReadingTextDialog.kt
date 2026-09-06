package com.steadyscreen.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier

// Keep pasted documents manageable for the editor and local storage.
private const val MaxReadingCharacters = 100_000

@Composable
internal fun ReadingTextDialog(text: String, onApply: (String) -> Unit, onDismiss: () -> Unit) {
    var draft by remember { mutableStateOf(text) }
    val tooLong = draft.length > MaxReadingCharacters
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Reading text") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                OutlinedTextField(
                    value = draft,
                    onValueChange = { draft = it },
                    label = { Text("Paste your text") },
                    minLines = 4,
                    maxLines = 8,
                    isError = tooLong,
                    supportingText = { Text("${draft.length} / $MaxReadingCharacters characters") },
                    modifier = Modifier.fillMaxWidth(),
                )
                Text("Use text saves your reading material on this device for the next launch.")
                TextButton(onClick = { onApply("") }) { Text("Use original sample") }
            }
        },
        confirmButton = {
            TextButton(onClick = { onApply(draft.trim()) }, enabled = draft.isNotBlank() && !tooLong) {
                Text("Use text")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

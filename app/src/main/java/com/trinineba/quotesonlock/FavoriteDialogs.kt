package com.trinineba.quotesonlock

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** Write or change your note on a favourite: why it matters, where you met it, what it reminds you of. */
@Composable
fun FavoriteNoteDialog(quote: Quote, initial: String, onSave: (String) -> Unit, onDismiss: () -> Unit) {
    var text by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Your note") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    quote.title ?: quote.shownText.take(140) + if (quote.shownText.length > 140) "…" else "",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    placeholder = { Text("Why this one matters to you…") },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 120.dp),
                )
            }
        },
        confirmButton = { TextButton(onClick = { onSave(text) }) { Text("Save") } },
        dismissButton = {
            Row {
                if (initial.isNotBlank()) TextButton(onClick = { onSave("") }) { Text("Delete") }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        },
    )
}

/** Put a favourite into your collections, or start a new one. */
@Composable
fun FavoriteCollectionsDialog(
    all: List<String>,
    selected: List<String>,
    onSave: (chosen: List<String>, created: List<String>) -> Unit,
    onDismiss: () -> Unit,
) {
    var chosen by remember { mutableStateOf(selected.toSet()) }
    var names by remember { mutableStateOf(all) }
    var created by remember { mutableStateOf(emptyList<String>()) }
    var newName by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Collections") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                if (names.isEmpty()) {
                    Text(
                        "Group favourites your own way, for example \"For hard days\", \"Work\" or \"Read again\".",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                names.forEach { name ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = name in chosen, onCheckedChange = { on -> chosen = if (on) chosen + name else chosen - name })
                        Text(name)
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = newName,
                        onValueChange = { newName = it.take(40) },
                        placeholder = { Text("New collection") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = {
                        val n = newName.trim()
                        if (n.isNotEmpty() && n !in names) {
                            names = names + n
                            created = created + n
                            chosen = chosen + n
                        }
                        newName = ""
                    }) { Text("Add") }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onSave(names.filter { it in chosen }, created) }) { Text("Done") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

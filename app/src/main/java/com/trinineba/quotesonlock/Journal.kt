package com.trinineba.quotesonlock

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import org.json.JSONArray
import org.json.JSONObject

/** One reflection: what you wrote, the question you answered, and the quote it was about. */
data class JournalEntry(val time: Long, val quote: String, val author: String?, val prompt: String, val text: String) {
    fun toJson(): JSONObject = JSONObject().put("time", time).put("quote", quote).put("prompt", prompt).put("text", text)
        .apply { author?.let { put("author", it) } }

    companion object {
        fun fromJson(o: JSONObject) = JournalEntry(
            o.optLong("time"), o.optString("quote"), o.optString("author").ifBlank { null }, o.optString("prompt"), o.optString("text"),
        )
    }
}

/** The private journal: reflections on quotes, kept only on this phone (and in your backups). */
object Journal {
    /** Gentle questions to think with; one is chosen for each quote. */
    val PROMPTS = listOf(
        "Where does this show up in your life right now?",
        "Do you agree with it? What makes you say so?",
        "Who comes to mind when you read this?",
        "What would change if you lived by this for a day?",
        "What feeling does this stir in you?",
        "When have you seen this to be true?",
        "What would you say back to the person who wrote it?",
        "Is there something this asks you to let go of?",
        "What small thing could you do today in its spirit?",
        "Which word or line stays with you, and why?",
        "How would you put this in your own words?",
        "What would you tell a friend who needed to hear this?",
        "Is there a part of this you resist?",
        "What does this make you grateful for?",
        "If this were advice to your younger self, would it help?",
        "What question does this leave you with?",
    )

    /** The same quote always gets the same question, so the home screen doesn't keep changing its mind. */
    fun promptFor(quote: Quote): String = PROMPTS[Math.floorMod(quote.text.hashCode(), PROMPTS.size)]

    fun entries(prefs: QuotePrefs): List<JournalEntry> = prefs.journalJson?.let { raw ->
        runCatching { JSONArray(raw).let { a -> (0 until a.length()).map { JournalEntry.fromJson(a.getJSONObject(it)) } } }.getOrNull()
    }.orEmpty().sortedByDescending { it.time }

    private fun save(prefs: QuotePrefs, list: List<JournalEntry>) {
        prefs.journalJson = JSONArray().apply { list.sortedBy { it.time }.forEach { put(it.toJson()) } }.toString()
    }

    fun add(prefs: QuotePrefs, quote: Quote, prompt: String, text: String) {
        if (text.isBlank()) return
        save(prefs, entries(prefs) + JournalEntry(System.currentTimeMillis(), quote.text, quote.author, prompt, text.trim()))
    }

    fun update(prefs: QuotePrefs, time: Long, text: String) {
        save(prefs, entries(prefs).map { if (it.time == time) it.copy(text = text.trim()) else it }.filter { it.text.isNotBlank() })
    }

    fun delete(prefs: QuotePrefs, time: Long) = save(prefs, entries(prefs).filter { it.time != time })

    /** Adds entries from a backup that aren't here yet (matched by when they were written); returns how many. */
    fun merge(prefs: QuotePrefs, incoming: List<JournalEntry>): Int {
        val have = entries(prefs)
        val times = have.map { it.time }.toSet()
        val new = incoming.filter { it.time !in times && it.text.isNotBlank() }
        if (new.isNotEmpty()) save(prefs, have + new)
        return new.size
    }
}

/** Writing (or editing) a reflection. */
@Composable
fun JournalDialog(quoteText: String, prompt: String, initial: String, onSave: (String) -> Unit, onDelete: (() -> Unit)?, onDismiss: () -> Unit) {
    var text by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(prompt, style = MaterialTheme.typography.titleMedium) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    quoteText.take(160) + if (quoteText.length > 160) "…" else "",
                    style = MaterialTheme.typography.bodySmall,
                    fontStyle = FontStyle.Italic,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    placeholder = { Text("A few honest lines…") },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 140.dp),
                )
                Text("Kept only on this phone.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        confirmButton = { TextButton(onClick = { onSave(text) }) { Text("Save") } },
        dismissButton = {
            androidx.compose.foundation.layout.Row {
                onDelete?.let { TextButton(onClick = it) { Text("Delete") } }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        },
    )
}

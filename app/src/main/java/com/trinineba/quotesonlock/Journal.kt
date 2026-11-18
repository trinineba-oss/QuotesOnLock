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

/** One reflection as the old journal stored it (and older backups still hold); see [Journal.moveIntoNotes]. */
data class JournalEntry(val time: Long, val quote: String, val author: String?, val prompt: String, val text: String) {
    fun toJson(): JSONObject = JSONObject().put("time", time).put("quote", quote).put("prompt", prompt).put("text", text)
        .apply { author?.let { put("author", it) } }

    companion object {
        fun fromJson(o: JSONObject) = JournalEntry(
            o.optLong("time"), o.optString("quote"), o.optString("author").ifBlank { null }, o.optString("prompt"), o.optString("text"),
        )
    }
}

/** The journal: questions to reflect on quotes with. Reflections are saved as notes (see [Notes]), kept only on this phone. */
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

    /** Saves a reflection into the notebook, as a note titled with its question and showing the quote it is about. */
    fun add(prefs: QuotePrefs, quote: Quote?, prompt: String, text: String) {
        if (text.isBlank()) return
        val now = System.currentTimeMillis()
        Notes.put(prefs, Note(now, prompt, text.trim(), emptyList(), false, now, Note.KIND_REFLECTION, quote?.let(::aboutOf)))
    }

    private fun aboutOf(quote: Quote): String = buildString {
        quote.title?.let { append(it).append(": ") }
        append(quote.shownText)
        quote.byline?.let { append(" \u2014 ").append(it) }
    }

    private fun JournalEntry.toNote(): Note = Note(
        time, prompt, text, emptyList(), false, time, Note.KIND_REFLECTION,
        quote.takeIf { it.isNotBlank() }?.let { "\u201C$it\u201D" + (author?.let { a -> " \u2014 $a" } ?: "") },
    )

    private fun parse(raw: String?): List<JournalEntry> = raw?.let {
        runCatching { JSONArray(it).let { a -> (0 until a.length()).map { i -> JournalEntry.fromJson(a.getJSONObject(i)) } } }.getOrNull()
    }.orEmpty().filter { it.text.isNotBlank() }

    /**
     * The journal used to be kept apart; its entries now live in the notebook. Moves any old entries over once
     * (each keeps its date as its id, so doing it twice adds nothing).
     */
    fun moveIntoNotes(prefs: QuotePrefs) {
        val raw = prefs.journalJson ?: return
        prefs.journalJson = null
        val old = parse(raw)
        if (old.isNotEmpty()) Notes.merge(prefs, old.map { it.toNote() })
    }

    /** Adds the reflections from an older backup's journal (as notes) that aren't here yet; returns how many. */
    fun merge(prefs: QuotePrefs, incoming: List<JournalEntry>): Int =
        Notes.merge(prefs, incoming.filter { it.text.isNotBlank() }.map { it.toNote() })
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

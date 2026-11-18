package com.trinineba.quotesonlock

import android.content.Context
import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.json.JSONArray
import org.json.JSONObject
import java.text.DateFormat
import java.util.Date

/**
 * One note in your notebook. [refs] are the quotes (by their exact text) and the ideas or myths
 * ("idea:" and their title) tucked into it, in the order they were added.
 */
data class Note(
    val id: Long,
    val title: String,
    val body: String,
    val refs: List<String>,
    val pinned: Boolean,
    val edited: Long,
) {
    fun toJson(): JSONObject = JSONObject().put("id", id).put("title", title).put("body", body)
        .put("refs", JSONArray(refs)).put("pinned", pinned).put("edited", edited)

    val isEmpty: Boolean get() = title.isBlank() && body.isBlank() && refs.isEmpty()

    companion object {
        fun fromJson(o: JSONObject) = Note(
            o.optLong("id"), o.optString("title"), o.optString("body"),
            o.optJSONArray("refs")?.let { a -> (0 until a.length()).map { a.optString(it) }.filter { it.isNotBlank() } }.orEmpty(),
            o.optBoolean("pinned"), o.optLong("edited"),
        )
    }
}

/** The notebook in Yours: your own notes, kept only on this phone (and in your backups). */
object Notes {
    fun all(prefs: QuotePrefs): List<Note> = prefs.notesJson?.let { raw ->
        runCatching { JSONArray(raw).let { a -> (0 until a.length()).map { Note.fromJson(a.getJSONObject(it)) } } }.getOrNull()
    }.orEmpty().sortedWith(compareByDescending<Note> { it.pinned }.thenByDescending { it.edited })

    private fun save(prefs: QuotePrefs, list: List<Note>) {
        prefs.notesJson = JSONArray().apply { list.filterNot { it.isEmpty }.forEach { put(it.toJson()) } }.toString()
    }

    fun get(prefs: QuotePrefs, id: Long): Note? = all(prefs).firstOrNull { it.id == id }

    /** Saves [note] (adding it if new); an empty note is dropped. */
    fun put(prefs: QuotePrefs, note: Note) = save(prefs, all(prefs).filter { it.id != note.id } + note)

    fun delete(prefs: QuotePrefs, id: Long) = save(prefs, all(prefs).filter { it.id != id })

    fun newId(): Long = System.currentTimeMillis()

    /** The reference stored for a quote, idea or myth. */
    fun refOf(quote: Quote): String = quote.title?.let { "idea:$it" } ?: quote.text

    fun resolve(bundled: List<Quote>, ref: String): Quote? =
        if (ref.startsWith("idea:")) ref.removePrefix("idea:").let { t -> bundled.firstOrNull { it.title == t } }
        else bundled.firstOrNull { it.title == null && it.text == ref }

    /** A short label for a reference: the idea's title, or the quote's author. */
    fun label(bundled: List<Quote>, ref: String): String =
        if (ref.startsWith("idea:")) ref.removePrefix("idea:")
        else resolve(bundled, ref)?.let { authorName(it.byline) ?: it.byline } ?: "Quote"

    /** Adds the backup's notes that aren't here yet (matched by id); returns how many. */
    fun merge(prefs: QuotePrefs, incoming: List<Note>): Int {
        val have = all(prefs)
        val ids = have.map { it.id }.toSet()
        val new = incoming.filter { it.id !in ids && !it.isEmpty }
        if (new.isNotEmpty()) save(prefs, have + new)
        return new.size
    }

    fun shareText(bundled: List<Quote>, note: Note): String = buildString {
        if (note.title.isNotBlank()) append(note.title.trim()).append("\n\n")
        if (note.body.isNotBlank()) append(note.body.trim()).append("\n")
        note.refs.forEach { ref ->
            val q = resolve(bundled, ref) ?: return@forEach
            append("\n")
            q.title?.let { append(it).append(": ") }
            append(if (q.title != null) q.text else "“${q.text}”")
            q.byline?.let { append("\n— ").append(it) }
            append("\n")
        }
    }.trim()
}

private fun whenText(time: Long): String {
    val today = System.currentTimeMillis() / 86_400_000L
    val day = time / 86_400_000L
    return when (today - day) {
        0L -> "Edited today"
        1L -> "Yesterday"
        else -> DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(time))
    }
}

/** The notebook: search, pinned notes first, a button for a new one. */
@Composable
fun NotesScreen(onBack: () -> Unit, onOpenAuthor: (String) -> Unit) {
    val context = LocalContext.current
    val prefs = remember { QuotePrefs(context) }
    val bundled = remember { QuoteRepository(context).bundled() }
    var refresh by remember { mutableLongStateOf(0L) }
    var open by rememberSaveable { mutableStateOf<Long?>(null) }
    open?.let { id ->
        NoteEditor(id, prefs, bundled, onBack = { open = null; refresh++ }, onOpenAuthor = onOpenAuthor)
        return
    }
    BackHandler(onBack = onBack)
    var query by rememberSaveable { mutableStateOf("") }
    val notes = remember(refresh) { Notes.all(prefs) }
    val shown = notes.filter { n ->
        val q = query.trim()
        q.isEmpty() || n.title.contains(q, true) || n.body.contains(q, true) || n.refs.any { Notes.label(bundled, it).contains(q, true) }
    }
    Scaffold(
        floatingActionButton = {
            ExtendedFloatingActionButton(onClick = { open = Notes.newId() }) { Text("+  New note") }
        },
    ) { inner ->
        LazyColumn(
            Modifier.fillMaxSize().padding(inner),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    BackArrow(onBack)
                    Text("Notes", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(start = 14.dp))
                }
            }
            if (notes.isNotEmpty()) item {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text("Search your notes") },
                    singleLine = true,
                    shape = RoundedCornerShape(24.dp),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            if (notes.isEmpty()) item {
                Text(
                    "Your own notebook: thoughts, lists, plans, questions for someone. Tuck in a quote or an idea from the " +
                        "collection as you write. Notes stay on this phone and are included in your backups.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            val pinned = shown.filter { it.pinned }
            if (pinned.isNotEmpty()) {
                item { NotesLabel("Pinned") }
                items(pinned, key = { "p${it.id}" }) { NoteCard(it, bundled) { open = it.id } }
                item { NotesLabel("All notes") }
            }
            items(shown.filter { !it.pinned }, key = { it.id }) { NoteCard(it, bundled) { open = it.id } }
            if (notes.isNotEmpty() && shown.isEmpty()) item {
                Text("No notes match \"${query.trim()}\".", style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
private fun NotesLabel(text: String) {
    Text(text.uppercase(), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary, letterSpacing = 1.2.sp)
}

@Composable
private fun NoteCard(note: Note, bundled: List<Quote>, onClick: () -> Unit) {
    SoftCard(Modifier.fillMaxWidth(), onClick = onClick) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                (if (note.pinned) "📌 " else "") + note.title.ifBlank { note.body.lineSequence().firstOrNull { it.isNotBlank() }?.take(40) ?: "Untitled" },
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (note.body.isNotBlank()) Text(
                note.body.replace('\n', ' '),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(whenText(note.edited), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                note.refs.take(2).forEach { RefChip(Notes.label(bundled, it)) }
                if (note.refs.size > 2) Text("+${note.refs.size - 2}", style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

@Composable
private fun RefChip(text: String) {
    Surface(shape = RoundedCornerShape(10.dp), color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)) {
        Text(text, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary,
            maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp))
    }
}

/** Writing a note. Everything is saved as you type; an empty note is simply not kept. */
@Composable
private fun NoteEditor(id: Long, prefs: QuotePrefs, bundled: List<Quote>, onBack: () -> Unit, onOpenAuthor: (String) -> Unit) {
    val context = LocalContext.current
    val start = remember(id) { Notes.get(prefs, id) ?: Note(id, "", "", emptyList(), false, System.currentTimeMillis()) }
    var title by rememberSaveable(id) { mutableStateOf(start.title) }
    var body by remember(id) { mutableStateOf(TextFieldValue(start.body, TextRange(start.body.length))) }
    var refs by rememberSaveable(id) { mutableStateOf(start.refs) }
    var pinned by rememberSaveable(id) { mutableStateOf(start.pinned) }
    var picking by remember { mutableStateOf<Boolean?>(null) } // true: ideas and myths, false: quotes
    var opened by remember { mutableStateOf<Quote?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }

    fun persist() {
        val n = Note(id, title.trim(), body.text.trimEnd(), refs, pinned, System.currentTimeMillis())
        val before = Notes.get(prefs, id)
        if (before == null || before.title != n.title || before.body != n.body || before.refs != n.refs || before.pinned != n.pinned) {
            if (n.isEmpty) Notes.delete(prefs, id) else Notes.put(prefs, n)
        }
    }
    // Saved a moment after each change, so nothing is lost if the app is closed mid-sentence.
    androidx.compose.runtime.LaunchedEffect(title, body.text, refs, pinned) {
        kotlinx.coroutines.delay(700)
        persist()
    }
    val leave = { persist(); onBack() }
    BackHandler(onBack = leave)

    opened?.let { q -> QuotePageSheet(quote = q, onDismiss = { opened = null }, onOpenAuthor = { opened = null; persist(); onOpenAuthor(it) }) }
    picking?.let { ideas ->
        CollectionPicker(ideas, bundled, onPick = { q -> val r = Notes.refOf(q); if (r !in refs) refs = refs + r; picking = null; persist() }, onDismiss = { picking = null })
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete this note?") },
            confirmButton = { TextButton(onClick = { confirmDelete = false; Notes.delete(prefs, id); onBack() }) { Text("Delete") } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Keep it") } },
        )
    }

    Scaffold(
        bottomBar = {
            Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 4.dp), horizontalArrangement = Arrangement.SpaceAround) {
                    TextButton(onClick = { picking = false }) { Text("❝ Add a quote") }
                    TextButton(onClick = { picking = true }) { Text("Add an idea") }
                    TextButton(onClick = {
                        val t = body.text
                        val add = if (t.isEmpty() || t.endsWith("\n")) "• " else "\n• "
                        val s = t + add
                        body = TextFieldValue(s, TextRange(s.length))
                    }) { Text("• List") }
                    TextButton(onClick = {
                        persist()
                        val text = Notes.shareText(bundled, Note(id, title, body.text, refs, pinned, 0))
                        if (text.isNotBlank()) context.startActivity(
                            Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text), "Share note")
                        )
                    }) { Text("Share") }
                }
            }
        },
    ) { inner ->
        LazyColumn(
            Modifier.fillMaxSize().padding(inner),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    BackArrow(leave)
                    Box(Modifier.weight(1f))
                    TextButton(onClick = { pinned = !pinned; persist() }) { Text(if (pinned) "📌 Pinned" else "Pin") }
                    TextButton(onClick = { confirmDelete = true }) { Text("Delete") }
                    TextButton(onClick = leave) { Text("Done") }
                }
            }
            item {
                TextField(
                    value = title,
                    onValueChange = { title = it },
                    placeholder = { Text("Title", fontFamily = FontFamily.Serif, fontSize = 22.sp) },
                    textStyle = MaterialTheme.typography.headlineSmall.copy(fontFamily = FontFamily.Serif),
                    singleLine = true,
                    colors = clearFieldColors(),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            item {
                TextField(
                    value = body,
                    onValueChange = { body = it },
                    placeholder = { Text("Write anything…") },
                    textStyle = MaterialTheme.typography.bodyLarge,
                    colors = clearFieldColors(),
                    modifier = Modifier.fillMaxWidth().heightIn(min = 160.dp),
                )
            }
            items(refs, key = { it }) { ref ->
                val q = remember(ref) { Notes.resolve(bundled, ref) }
                Surface(
                    onClick = { if (q != null) opened = q },
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHighest,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(verticalAlignment = Alignment.Top) {
                        Column(Modifier.weight(1f).padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            if (q == null) {
                                Text("This line is no longer in the collection.", style = MaterialTheme.typography.bodySmall)
                            } else {
                                q.title?.let { Text(it, fontFamily = FontFamily.Serif, fontWeight = FontWeight.SemiBold) }
                                Text(
                                    if (q.title != null) q.text else "“${q.text}”",
                                    fontFamily = FontFamily.Serif,
                                    style = MaterialTheme.typography.bodyMedium,
                                    maxLines = 6,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                q.byline?.let { Text("— $it ›", fontFamily = FontFamily.Serif, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary) }
                            }
                        }
                        TextButton(onClick = { refs = refs - ref; persist() }) { Text("✕") }
                    }
                }
            }
        }
    }
}

@Composable
private fun clearFieldColors() = TextFieldDefaults.colors(
    focusedContainerColor = Color.Transparent,
    unfocusedContainerColor = Color.Transparent,
    focusedIndicatorColor = Color.Transparent,
    unfocusedIndicatorColor = Color.Transparent,
)

/** Search the collection for a quote (or an idea or myth) to tuck into a note. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CollectionPicker(ideas: Boolean, bundled: List<Quote>, onPick: (Quote) -> Unit, onDismiss: () -> Unit) {
    var query by remember { mutableStateOf("") }
    val pool = remember(ideas) { bundled.filter { it.isIdea == ideas } }
    val found = remember(query, pool) {
        val q = query.trim()
        if (q.length < 2) emptyList()
        else pool.asSequence().filter {
            it.text.contains(q, true) || it.byline?.contains(q, true) == true || it.title?.contains(q, true) == true
        }.take(40).toList()
    }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(if (ideas) "Add an idea or myth" else "Add a quote", style = MaterialTheme.typography.titleMedium)
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text(if (ideas) "Search by name or words" else "Search by words or author") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            LazyColumn(Modifier.heightIn(max = 420.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                items(found, key = { it.title ?: it.text }) { q ->
                    Surface(onClick = { onPick(q) }, shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f), modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(12.dp)) {
                            q.title?.let { Text(it, fontWeight = FontWeight.SemiBold) }
                            Text(q.text, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium)
                            q.byline?.let { Text("— $it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary, maxLines = 1) }
                        }
                    }
                }
            }
            if (query.trim().length < 2) Text("Type a few letters to search.", style = MaterialTheme.typography.bodySmall)
        }
    }
}

/** From a quote page: put this quote (or idea) into a new note or one you already have. */
@Composable
internal fun AddToNoteDialog(quote: Quote, onDone: (String) -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val prefs = remember { QuotePrefs(context) }
    val notes = remember { Notes.all(prefs) }
    val ref = Notes.refOf(quote)
    fun addTo(note: Note) {
        Notes.put(prefs, note.copy(refs = if (ref in note.refs) note.refs else note.refs + ref, edited = System.currentTimeMillis()))
        onDone(note.title.ifBlank { "your note" })
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add to a note") },
        text = {
            LazyColumn(Modifier.heightIn(max = 360.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                item {
                    TextButton(onClick = {
                        addTo(Note(Notes.newId(), quote.title ?: "", "", emptyList(), false, System.currentTimeMillis()))
                    }) { Text("+  A new note") }
                }
                items(notes, key = { it.id }) { n ->
                    TextButton(onClick = { addTo(n) }, modifier = Modifier.fillMaxWidth()) {
                        Text(
                            n.title.ifBlank { n.body.take(40).ifBlank { "Untitled" } },
                            maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/** How many notes there are, for the Yours page. */
internal fun noteCount(context: Context): Int = Notes.all(QuotePrefs(context)).size

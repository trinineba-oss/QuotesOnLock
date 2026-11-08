package com.trinineba.quotesonlock

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// ---- Read it in context -------------------------------------------------------------------------

/**
 * The passages around the quotes, cut from the public-domain texts they come from (res/raw/context.txt).
 * Only the keys are kept in memory; a passage is read from the file when asked for.
 */
object QuoteContext {
    class Passage(val source: String, val before: String, val quote: String, val after: String)

    @Volatile private var index: Map<String, Int>? = null

    fun key(text: String): String = QuoteData.wordsKey(text).take(80)

    private fun index(context: Context): Map<String, Int> = index ?: synchronized(this) {
        index ?: context.applicationContext.resources.openRawResource(R.raw.context).bufferedReader().useLines { lines ->
            val map = HashMap<String, Int>()
            lines.forEachIndexed { i, line ->
                if (!line.startsWith("#")) map.putIfAbsent(line.substringBefore('\t'), i)
            }
            map
        }.also { index = it }
    }

    /** Loads the index ahead of time (called from the background preload). */
    fun warmUp(context: Context) {
        runCatching { index(context) }
    }

    fun has(context: Context, quote: Quote): Boolean = !quote.explained && index(context).containsKey(key(quote.text))

    fun get(context: Context, quote: Quote): Passage? {
        val at = index(context)[key(quote.text)] ?: return null
        val line = context.applicationContext.resources.openRawResource(R.raw.context).bufferedReader().useLines { lines ->
            lines.drop(at).firstOrNull()
        } ?: return null
        val f = line.split('\t')
        if (f.size < 5) return null
        fun un(s: String) = s.replace("\\n", "\n")
        return Passage(f[1], un(f[2]), un(f[3]), un(f[4]))
    }
}

/** The surrounding passage, the quote highlighted and its source named (in the Context sheet and the quote page). */
@Composable
fun ContextPassage(quote: Quote) {
    val context = LocalContext.current
    val passage = remember(quote.text) { QuoteContext.get(context, quote) }
    if (passage == null) {
        Text("The surrounding passage isn't available for this quote.")
        return
    }
    val mark = MaterialTheme.colorScheme.primary.copy(alpha = 0.22f)
    val strong = MaterialTheme.colorScheme.onSurface
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            buildAnnotatedString {
                if (passage.before.isNotBlank()) append("…" + passage.before.trimStart())
                withStyle(SpanStyle(background = mark, color = strong, fontWeight = FontWeight.Medium)) { append(passage.quote) }
                if (passage.after.isNotBlank()) append(passage.after.trimEnd() + "…")
            },
            fontFamily = FontFamily.Serif,
            fontSize = 17.sp,
            lineHeight = 26.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        val from = listOfNotNull(quote.byline, passage.source.ifBlank { null }).joinToString(" · ")
        Text(
            "$from · public-domain text",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** The surrounding passage in a sheet from the bottom. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContextSheet(quote: Quote, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 28.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("IN CONTEXT", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary, letterSpacing = 1.2.sp)
            ContextPassage(quote)
        }
    }
}

/** The home card's "Context" button: shown only for quotes whose passage is available. */
@Composable
fun ContextAction(quote: Quote?) {
    val context = LocalContext.current
    var open by remember { mutableStateOf(false) }
    val available = remember(quote?.text) { quote != null && QuoteContext.has(context, quote) }
    QuoteAction(R.drawable.ic_menu_book, "Context", enabled = available) { open = true }
    if (open && quote != null) ContextSheet(quote) { open = false }
}

/** The same, as a small icon in quote lists (nothing is shown when no passage is available). */
@Composable
fun ContextListAction(quote: Quote) {
    val context = LocalContext.current
    var open by remember { mutableStateOf(false) }
    val available = remember(quote.text) { QuoteContext.has(context, quote) }
    if (!available) return
    ListAction(R.drawable.ic_menu_book, "Read it in context") { open = true }
    if (open) ContextSheet(quote) { open = false }
}

// ---- Copy and share in one button ---------------------------------------------------------------

internal fun shareText(context: Context, quote: Quote) {
    val text = if (quote.isIdea) quote.fullText() else "“${quote.text}”" + (quote.byline?.let { " — $it" } ?: "")
    val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
    context.startActivity(Intent.createChooser(send, "Share quote").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}

@Composable
internal fun ShareMenu(quote: Quote, expanded: Boolean, onDismiss: () -> Unit) {
    val context = LocalContext.current
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        DropdownMenuItem(text = { Text("Copy text") }, onClick = { onDismiss(); QuoteClipboard.copy(context, quote) })
        DropdownMenuItem(text = { Text("Share as a picture") }, onClick = { onDismiss(); QuoteShare.share(context, quote) })
        DropdownMenuItem(text = { Text("Share as text") }, onClick = { onDismiss(); shareText(context, quote) })
    }
}

/** The home card's "Share" button, which offers copying, a picture or plain text. */
@Composable
fun ShareAction(quote: Quote?) {
    var open by remember { mutableStateOf(false) }
    Box {
        QuoteAction(R.drawable.ic_share, "Share", enabled = quote != null) { open = true }
        if (quote != null) ShareMenu(quote, open) { open = false }
    }
}

/** The same in quote lists. */
@Composable
fun ShareListAction(quote: Quote) {
    var open by remember { mutableStateOf(false) }
    Box {
        ListAction(R.drawable.ic_share, "Copy or share") { open = true }
        ShareMenu(quote, open) { open = false }
    }
}

// ---- Dialogues across time ----------------------------------------------------------------------

class Dialogue(
    val theme: String, val textA: String, val authorA: String, val yearA: String,
    val textB: String, val authorB: String, val yearB: String,
    val bridge: String, val meet: String, val part: String,
)

object Dialogues {
    @Volatile private var all: List<Dialogue>? = null

    fun all(context: Context): List<Dialogue> = all ?: context.applicationContext.resources.openRawResource(R.raw.dialogues)
        .bufferedReader().useLines { lines ->
            lines.filter { it.isNotBlank() && !it.startsWith("#") }.mapNotNull { l ->
                val f = l.split('|')
                if (f.size < 10) null else Dialogue(
                    f[0], f[1].replace("\\n", "\n"), f[2], f[3], f[4].replace("\\n", "\n"), f[5], f[6], f[7], f[8], f[9],
                )
            }.toList()
        }.also { all = it }
}

@Composable
fun DialoguesScreen(onBack: () -> Unit, onOpenAuthor: (String) -> Unit) {
    val context = LocalContext.current
    val all = remember { Dialogues.all(context) }
    val themes = remember(all) { all.map { it.theme }.distinct() }
    var theme by rememberSaveable { mutableStateOf<String?>(null) }
    val shown = all.filter { theme == null || it.theme == theme }
    var at by rememberSaveable(theme) { mutableIntStateOf(0) }
    val d = shown.getOrNull(at.coerceIn(0, (shown.size - 1).coerceAtLeast(0)))
    ToolScreen("Dialogues", onBack) {
        item {
            Text(
                "Two voices across the centuries, answering each other.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        item {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = theme == null, onClick = { theme = null }, label = { Text("All") })
                themes.forEach { t -> FilterChip(selected = theme == t, onClick = { theme = if (theme == t) null else t }, label = { Text(t) }) }
            }
        }
        if (d != null) {
            item { Voice(d.authorA, d.yearA, d.textA, onOpenAuthor) }
            item {
                Text(
                    d.bridge.uppercase(),
                    style = MaterialTheme.typography.labelMedium,
                    letterSpacing = 1.5.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
            }
            item { Voice(d.authorB, d.yearB, d.textB, onOpenAuthor) }
            item {
                Column(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)).padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(buildAnnotatedString {
                        withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) { append("Where they meet: ") }
                        append(d.meet)
                    }, style = MaterialTheme.typography.bodyMedium)
                    Text(buildAnnotatedString {
                        withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) { append("Where they part: ") }
                        append(d.part)
                    }, style = MaterialTheme.typography.bodyMedium)
                }
            }
            item {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = { at = (at - 1 + shown.size) % shown.size }, enabled = shown.size > 1) { Text("‹ Previous") }
                    Spacer(Modifier.weight(1f))
                    Text("${at + 1} of ${shown.size}", style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = { at = (at + 1) % shown.size }, enabled = shown.size > 1) { Text("Next ›") }
                }
            }
            item {
                Text(
                    "The notes on where they meet and part are written for the app.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun Voice(author: String, year: String, text: String, onOpenAuthor: (String) -> Unit) {
    val name = authorName(author)
    SoftCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                ((name ?: author) + " · " + year).uppercase(),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                letterSpacing = 1.sp,
                modifier = if (name != null) Modifier.clickable(onClickLabel = "All quotes by $name") { onOpenAuthor(name) } else Modifier,
            )
            Text("“$text”", fontFamily = FontFamily.Serif, fontSize = 18.sp, lineHeight = 26.sp)
            if (author != name) Text(author, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

// ---- Who was alive together ---------------------------------------------------------------------

class Life(val name: String, val page: String, val born: Int, val died: Int?, val group: String, val approx: Boolean)
class TimelineNote(val year: Int, val text: String)

object Timeline {
    @Volatile private var lives: List<Life>? = null
    @Volatile private var notes: List<TimelineNote>? = null

    fun lives(context: Context): List<Life> = lives ?: context.applicationContext.resources.openRawResource(R.raw.timeline)
        .bufferedReader().useLines { lines ->
            lines.filter { it.isNotBlank() && !it.startsWith("#") }.mapNotNull { l ->
                val f = l.split('|')
                val born = f.getOrNull(2)?.toIntOrNull() ?: return@mapNotNull null
                Life(f[0], f[1], born, f.getOrNull(3)?.toIntOrNull(), f.getOrElse(4) { "thinkers" }, f.getOrNull(5) == "c")
            }.sortedBy { it.born }.toList()
        }.also { lives = it }

    fun notes(context: Context): List<TimelineNote> = notes ?: context.applicationContext.resources.openRawResource(R.raw.timeline_notes)
        .bufferedReader().useLines { lines ->
            lines.filter { it.isNotBlank() && !it.startsWith("#") }.mapNotNull { l ->
                l.substringBefore('|').toIntOrNull()?.let { TimelineNote(it, l.substringAfter('|')) }
            }.toList()
        }.also { notes = it }

    fun year(y: Int): String = if (y < 0) "${-y} BC" else "$y"

    fun span(l: Life): String {
        val c = if (l.approx) "c. " else ""
        return when {
            l.died == null -> "born ${year(l.born)}"
            l.born < 0 && l.died < 0 -> "$c${-l.born}–${-l.died} BC"
            l.born < 0 -> "$c${-l.born} BC–${l.died} AD"
            else -> "$c${l.born}–${l.died}"
        }
    }

    private fun ordinal(n: Int): String = n.toString() + when {
        n % 100 in 11..13 -> "th"
        n % 10 == 1 -> "st"
        n % 10 == 2 -> "nd"
        n % 10 == 3 -> "rd"
        else -> "th"
    }

    /** The century a year falls in, as a heading. */
    fun era(y: Int): String = if (y < 0) "${ordinal((-y - 1) / 100 + 1)} century BC" else "${ordinal(y / 100 + 1)} century"
}

@Composable
fun TimelineScreen(onBack: () -> Unit, onOpenAuthor: (String) -> Unit) {
    val context = LocalContext.current
    val lives = remember { Timeline.lives(context) }
    val notes = remember { Timeline.notes(context) }
    var group by rememberSaveable { mutableStateOf<String?>(null) }
    val shown = lives.filter { group == null || it.group == group }
    // Life-span bars share one scale: a hundred years fills the width.
    ToolScreen("Timeline", onBack) {
        item {
            Text(
                "Everyone in the app, in order of birth. Tap a name to read them.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        item {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(null to "All", "thinkers" to "Thinkers", "poets" to "Poets", "writers" to "Writers", "mystics" to "Mystics & scripture")
                    .forEach { (key, label) -> FilterChip(selected = group == key, onClick = { group = key }, label = { Text(label) }) }
            }
        }
        var lastEra: String? = null
        var noteAt = 0
        val sortedNotes = notes.sortedBy { it.year }
        shown.forEachIndexed { i, l ->
            // Notes go in where the timeline passes their year.
            while (noteAt < sortedNotes.size && sortedNotes[noteAt].year <= l.born) {
                val n = sortedNotes[noteAt]
                if (group == null) item(key = "note-${n.year}") { NoteCard(n.text) }
                noteAt++
            }
            val era = Timeline.era(l.born)
            if (era != lastEra) {
                lastEra = era
                item(key = "era-$era-$i") {
                    Text(era, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 8.dp))
                }
            }
            item(key = "life-${l.name}-$i") { LifeRow(l) { onOpenAuthor(l.page) } }
        }
        if (group == null) sortedNotes.drop(noteAt).forEach { n -> item(key = "note-${n.year}") { NoteCard(n.text) } }
    }
}

@Composable
private fun LifeRow(l: Life, onClick: () -> Unit) {
    SoftCard(Modifier.fillMaxWidth(), onClick = onClick) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(l.name, style = MaterialTheme.typography.titleSmall)
                Text("  " + Timeline.span(l), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            val years = ((l.died ?: 2026) - l.born).coerceIn(1, 100)
            Box(
                Modifier.padding(top = 6.dp).fillMaxWidth(years / 100f).height(5.dp).clip(RoundedCornerShape(3.dp))
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = if (l.approx) 0.45f else 0.8f)),
            )
        }
    }
}

@Composable
private fun NoteCard(text: String) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(androidx.compose.ui.graphics.Color(0x33FFD68C)).padding(12.dp),
    ) {
        Text("✦ ", color = androidx.compose.ui.graphics.Color(0xFFE6B566))
        Text(text, style = MaterialTheme.typography.bodyMedium, fontStyle = FontStyle.Italic)
        Spacer(Modifier.width(2.dp))
    }
}

package com.trinineba.quotesonlock

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.flow.MutableStateFlow

// ---- The word behind the word -------------------------------------------------------------------

/** A word with a richer original behind its English (res/raw/words.txt), and where to mark it. */
class Word(
    val id: Int,
    /** The English forms to mark, each with the quote types it is limited to (empty: anywhere). */
    val forms: List<Pair<String, Set<String>>>,
    val original: String,
    val romanised: String,
    val language: String,
    val meaning: String,
    val note: String,
    val kin: String,
)

object Words {
    @Volatile private var all: List<Word>? = null

    /** The word whose sheet is open, wherever it was tapped (shown by [WordSheetHost]). */
    val opened = MutableStateFlow<Word?>(null)

    fun parse(lines: Sequence<String>): List<Word> = lines
        .filter { it.isNotBlank() && !it.startsWith("#") }
        .mapIndexedNotNull { i, line ->
            val f = line.split('|')
            if (f.size < 8) return@mapIndexedNotNull null
            val scope = f[1].split(',').map { it.trim() }.filter { it.isNotEmpty() }.toSet()
            val forms = f[0].split(',').map { it.trim() }.filter { it.isNotEmpty() }.map { form ->
                if ('@' in form) form.substringBefore('@') to form.substringAfter('@').split('+').toSet() else form to scope
            }
            Word(i, forms, f[2], f[3], f[4], f[5], f[6], f[7])
        }
        .toList()

    fun all(context: Context): List<Word> = all ?: synchronized(this) {
        all ?: context.applicationContext.resources.openRawResource(R.raw.words).bufferedReader()
            .useLines { parse(it) }.also { all = it }
    }

    /** Where each word appears in [text] (first time only, never overlapping), for a quote of [category]. */
    fun find(words: List<Word>, text: String, category: String?): List<Triple<Int, Int, Word>> {
        val found = mutableListOf<Triple<Int, Int, Word>>()
        for (w in words) {
            var best: Pair<Int, Int>? = null
            for ((form, scope) in w.forms) {
                if (scope.isNotEmpty() && category !in scope) continue
                var at = text.indexOf(form)
                while (at >= 0) {
                    val end = at + form.length
                    val before = at == 0 || !text[at - 1].isLetterOrDigit()
                    val after = end == text.length || !text[end].isLetterOrDigit()
                    if (before && after) break
                    at = text.indexOf(form, at + 1)
                }
                if (at >= 0 && (best == null || at < best.first)) best = at to at + form.length
            }
            val b = best ?: continue
            if (found.none { b.first < it.second && it.first < b.second }) found += Triple(b.first, b.second, w)
        }
        return found.sortedBy { it.first }
    }

    /** The quotes, ideas and myths that use [word]. */
    fun uses(context: Context, word: Word): List<Quote> {
        val one = listOf(word)
        return QuoteRepository(context).bundled().filter { find(one, it.shownText, it.category?.key).isNotEmpty() }
    }
}

/** The sheet for a tapped word; placed once, at the root of the app. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WordSheetHost() {
    val word by Words.opened.collectAsState()
    val w = word ?: return
    val context = LocalContext.current
    var showing by remember(w.id) { mutableStateOf(false) }
    val uses = remember(w.id) { Words.uses(context, w) }
    ModalBottomSheet(onDismissRequest = { Words.opened.value = null }) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 28.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text("THE WORD BEHIND THE WORD", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary, letterSpacing = 1.2.sp)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(w.original, fontSize = if (w.original.length <= 4) 44.sp else 28.sp, fontFamily = FontFamily.Serif)
                Column {
                    if (w.romanised != w.original) Text(w.romanised, fontFamily = FontFamily.Serif, fontStyle = FontStyle.Italic, fontSize = 17.sp)
                    Text("${w.language} · ${w.meaning}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
                }
            }
            Text(w.note, style = MaterialTheme.typography.bodyLarge, lineHeight = 24.sp)
            if (w.kin.isNotBlank()) {
                Text("Close in other traditions: ${w.kin}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (uses.size > 1) {
                TextButton(onClick = { showing = !showing }, contentPadding = PaddingValues(0.dp)) {
                    Text(if (showing) "Hide the ${uses.size} quotes" else "See all ${uses.size} quotes that use the word ›")
                }
                if (showing) uses.forEach { q ->
                    Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            q.title?.let { Text(it, fontWeight = FontWeight.SemiBold) }
                            Text(q.shownText, fontFamily = FontFamily.Serif, fontSize = 15.sp)
                            q.byline?.let { Text("— $it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary) }
                        }
                    }
                }
            }
            Text("Written for the app", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

// ---- One idea, many hands -----------------------------------------------------------------------

class ThemeVoice(val era: String, val place: String, val text: String, val byline: String)
class IdeaTheme(val name: String, val note: String, val voices: List<ThemeVoice>)

object IdeaThemes {
    @Volatile private var all: List<IdeaTheme>? = null

    fun parse(lines: Sequence<String>): List<IdeaTheme> {
        val out = mutableListOf<IdeaTheme>()
        var name = ""
        var note = ""
        var voices = mutableListOf<ThemeVoice>()
        lines.filter { it.isNotBlank() && !it.startsWith("#") }.forEach { line ->
            val f = line.split('|')
            when (f[0]) {
                "T" -> {
                    if (name.isNotEmpty()) out += IdeaTheme(name, note, voices)
                    name = f.getOrElse(1) { "" }; note = f.getOrElse(2) { "" }; voices = mutableListOf()
                }
                "V" -> if (f.size >= 5) voices += ThemeVoice(f[1], f[2], f[3].replace("\\n", "\n"), f[4])
            }
        }
        if (name.isNotEmpty()) out += IdeaTheme(name, note, voices)
        return out
    }

    fun all(context: Context): List<IdeaTheme> = all ?: context.applicationContext.resources.openRawResource(R.raw.themes)
        .bufferedReader().useLines { parse(it) }.also { all = it }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ThemesScreen(onBack: () -> Unit, onOpenAuthor: (String) -> Unit) {
    BackHandler(onBack = onBack)
    val context = LocalContext.current
    val themes = remember { IdeaThemes.all(context) }
    var chosen by rememberSaveable { mutableStateOf(themes.firstOrNull()?.name.orEmpty()) }
    val theme = themes.firstOrNull { it.name == chosen } ?: return
    Scaffold { inner ->
        LazyColumn(
            Modifier.fillMaxSize().padding(inner),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item { BackArrow(onBack) }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("One idea, many hands", style = MaterialTheme.typography.headlineSmall)
                    Text(
                        "How people have thought about one great theme across the centuries and the continents, in order of time.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
            item {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    themes.forEach { t -> FilterChip(selected = t.name == chosen, onClick = { chosen = t.name }, label = { Text(t.name) }) }
                }
            }
            items(theme.voices, key = { theme.name + it.text.hashCode() }) { v ->
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        "${v.era} · ${v.place}".uppercase(),
                        style = MaterialTheme.typography.labelSmall,
                        letterSpacing = 1.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    VoiceCard(v.text, v.byline, onOpenAuthor)
                }
            }
            item {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)),
                ) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("HOW THE IDEA CHANGED", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, letterSpacing = 1.2.sp)
                        Text(theme.note, style = MaterialTheme.typography.bodyMedium, lineHeight = 22.sp)
                        Text("Written for the app", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

/** A quote on its own card, its author tappable. */
@Composable
private fun VoiceCard(text: String, byline: String, onOpenAuthor: (String) -> Unit) {
    SoftCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("“$text”", fontFamily = FontFamily.Serif, fontSize = 16.sp, lineHeight = 23.sp)
            val name = authorName(byline)
            Text(
                "— $byline",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = if (name != null) Modifier.clickable(onClickLabel = "All quotes by $name") { onOpenAuthor(name) } else Modifier,
            )
        }
    }
}

// ---- Influences ---------------------------------------------------------------------------------

class InfluenceNode(val author: String, val whenText: String, val text: String, val byline: String)

/** A thread of who read whom: nodes in order, [links] between each pair. */
class InfluenceThread(val title: String, val subtitle: String, val nodes: List<InfluenceNode>, val links: List<String>)

object Influences {
    @Volatile private var all: List<InfluenceThread>? = null

    fun parse(lines: Sequence<String>): List<InfluenceThread> {
        val out = mutableListOf<InfluenceThread>()
        var title = ""
        var sub = ""
        var nodes = mutableListOf<InfluenceNode>()
        var links = mutableListOf<String>()
        fun flush() { if (title.isNotEmpty() && nodes.size >= 2) out += InfluenceThread(title, sub, nodes, links) }
        lines.filter { it.isNotBlank() && !it.startsWith("#") }.forEach { line ->
            val f = line.split('|')
            when (f[0]) {
                "T" -> { flush(); title = f.getOrElse(1) { "" }; sub = f.getOrElse(2) { "" }; nodes = mutableListOf(); links = mutableListOf() }
                "N" -> if (f.size >= 5) nodes += InfluenceNode(f[1], f[2], f[3].replace("\\n", "\n"), f[4])
                "E" -> links += f.getOrElse(1) { "" }
            }
        }
        flush()
        return out
    }

    fun all(context: Context): List<InfluenceThread> = all ?: context.applicationContext.resources.openRawResource(R.raw.influences)
        .bufferedReader().useLines { parse(it) }.also { all = it }

    /** Who [author] read, and who read them, with the thread each link comes from. */
    fun around(context: Context, author: String): Pair<List<Pair<String, Int>>, List<Pair<String, Int>>> {
        val before = mutableListOf<Pair<String, Int>>()
        val after = mutableListOf<Pair<String, Int>>()
        all(context).forEachIndexed { t, thread ->
            thread.nodes.forEachIndexed { i, n ->
                if (n.author != author) return@forEachIndexed
                thread.nodes.getOrNull(i - 1)?.let { p -> if (before.none { it.first == p.author }) before += p.author to t }
                thread.nodes.getOrNull(i + 1)?.let { p -> if (after.none { it.first == p.author }) after += p.author to t }
            }
        }
        return before to after
    }
}

/** On an author's page: who they read and who read them; each name opens that thread. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AuthorInfluences(author: String, onOpenThread: (Int) -> Unit) {
    val context = LocalContext.current
    val (before, after) = remember(author) { Influences.around(context, author) }
    if (before.isEmpty() && after.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.padding(bottom = 6.dp)) {
        InfluenceLine("Influenced by", before, onOpenThread)
        InfluenceLine("Influenced", after, onOpenThread)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun InfluenceLine(label: String, names: List<Pair<String, Int>>, onOpenThread: (Int) -> Unit) {
    if (names.isEmpty()) return
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp))
        names.forEach { (name, t) ->
            Surface(onClick = { onOpenThread(t) }, shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)) {
                Text("$name ›", style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp))
            }
        }
    }
}

@Composable
fun InfluencesScreen(start: Int?, onBack: () -> Unit, onOpenAuthor: (String) -> Unit) {
    val context = LocalContext.current
    val threads = remember { Influences.all(context) }
    var open by rememberSaveable { mutableStateOf(start ?: -1) }
    val thread = threads.getOrNull(open)
    BackHandler { if (thread != null && start == null) open = -1 else onBack() }
    Scaffold { inner ->
        LazyColumn(
            Modifier.fillMaxSize().padding(inner),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item { BackArrow { if (thread != null && start == null) open = -1 else onBack() } }
            if (thread == null) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Influences", style = MaterialTheme.typography.headlineSmall)
                        Text("Threads of who read whom: each step with the reason, and a line from both sides.", style = MaterialTheme.typography.bodyMedium)
                    }
                }
                items(threads.indices.toList()) { i ->
                    val t = threads[i]
                    SoftCard(Modifier.fillMaxWidth(), onClick = { open = i }) {
                        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Text(t.title, style = MaterialTheme.typography.titleMedium)
                            Text(t.nodes.joinToString(" → ") { it.author }, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                            if (t.subtitle.isNotBlank()) Text(t.subtitle, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            } else {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(thread.title, style = MaterialTheme.typography.headlineSmall)
                        if (thread.subtitle.isNotBlank()) Text(thread.subtitle, style = MaterialTheme.typography.bodyMedium)
                    }
                }
                thread.nodes.forEachIndexed { i, n ->
                    item(key = "n$i") {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                "${n.author} · ${n.whenText}".uppercase(),
                                style = MaterialTheme.typography.labelSmall,
                                letterSpacing = 1.sp,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.clickable(onClickLabel = "All quotes by ${n.author}") { onOpenAuthor(n.author) },
                            )
                            VoiceCard(n.text, n.byline, onOpenAuthor)
                        }
                    }
                    thread.links.getOrNull(i)?.takeIf { i < thread.nodes.lastIndex }?.let { link ->
                        item(key = "e$i") {
                            Row(Modifier.padding(start = 18.dp)) {
                                Surface(
                                    modifier = Modifier.fillMaxWidth(),
                                    color = Color.Transparent,
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)),
                                    shape = RoundedCornerShape(10.dp),
                                ) {
                                    Text(
                                        "↓ $link",
                                        style = MaterialTheme.typography.bodySmall,
                                        lineHeight = 19.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                    )
                                }
                            }
                        }
                    }
                }
                item { Text("Notes written for the app", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
        }
    }
}

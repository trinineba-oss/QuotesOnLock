package com.trinineba.quotesonlock

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** One highlight: Richter's passage [number], a title and note written for the app, his words, and riddle answers. */
class LeoHighlight(
    val id: String,
    val number: Int,
    val title: String,
    val note: String,
    val paras: List<String>,
    val answers: List<String>,
    val theme: String,
)

class LeoTheme(val title: String, val tagline: String, val tint: Long, val items: List<LeoHighlight>)

class LeoBook(val themes: List<LeoTheme>) {
    val all: List<LeoHighlight> get() = themes.flatMap { it.items }
}

/** Leonardo's notebooks (res/raw/leonardo.txt), in J. P. Richter's public-domain translation (1883). */
object Leonardo {
    @Volatile private var book: LeoBook? = null

    fun parse(lines: Sequence<String>): LeoBook {
        val themes = mutableListOf<LeoTheme>()
        var theme: List<String>? = null
        var items = mutableListOf<LeoHighlight>()
        var head: List<String>? = null
        var paras = mutableListOf<String>()
        var answers = mutableListOf<String>()
        val seen = mutableMapOf<Int, Int>()
        fun flushItem() {
            head?.let { h ->
                val n = h[1].toIntOrNull() ?: 0
                val k = (seen[n] ?: 0) + 1
                seen[n] = k
                if (paras.isNotEmpty()) items += LeoHighlight(
                    if (k == 1) "$n" else "$n.$k", n, h[2], h.getOrElse(4) { "" },
                    paras, answers, theme?.getOrNull(1) ?: "",
                )
            }
            head = null; paras = mutableListOf(); answers = mutableListOf()
        }
        fun flushTheme() {
            flushItem()
            theme?.let { t -> if (items.isNotEmpty()) themes += LeoTheme(t[1], t.getOrElse(2) { "" }, 0xFF000000 or (t.getOrNull(3)?.toLongOrNull(16) ?: 0x9A5A1E), items) }
            theme = null; items = mutableListOf()
        }
        lines.filter { it.isNotBlank() && !it.startsWith("#") }.forEach { line ->
            val f = line.split('|')
            when (f[0]) {
                "G" -> { flushTheme(); theme = f }
                "H" -> { flushItem(); head = f }
                "P" -> paras += line.substringAfter('|')
                "A" -> answers += line.substringAfter('|')
            }
        }
        flushTheme()
        return LeoBook(themes)
    }

    fun book(context: Context): LeoBook = book ?: synchronized(this) {
        book ?: context.applicationContext.resources.openRawResource(R.raw.leonardo).bufferedReader().useLines { parse(it) }.also { book = it }
    }

    /** A heading in his own words, set in capitals by Richter, such as "OF THE MOON." */
    fun isHeading(p: String) = p.length < 140 && p.any { it.isLetter() } && p.filter { it.isLetter() }.all { it.isUpperCase() }

    /** His lines that are also in the collection, found word for word in [para]. */
    fun linesIn(bundled: List<Quote>, para: String): List<Triple<Int, Int, Quote>> =
        bundled.asSequence()
            .filter { it.title == null && it.author?.startsWith("Leonardo da Vinci") == true && it.text.length > 24 }
            .mapNotNull { q -> para.indexOf(q.text).takeIf { it >= 0 }?.let { Triple(it, it + q.text.length, q) } }
            .sortedBy { it.first }
            .fold(mutableListOf<Triple<Int, Int, Quote>>()) { acc, t -> if (acc.isEmpty() || t.first >= acc.last().second) acc += t; acc }
}

private val Accent: Color @Composable get() = MaterialTheme.colorScheme.primary
private val Soft: Color @Composable get() = MaterialTheme.colorScheme.onSurfaceVariant

@Composable
private fun Label(text: String, modifier: Modifier = Modifier) {
    Text(text.uppercase(), style = MaterialTheme.typography.labelMedium, letterSpacing = 1.2.sp, color = Accent, modifier = modifier)
}

/** The notebooks: what he studied, as a grid of themes, and today's page. */
@Composable
fun LeonardoScreen(onBack: () -> Unit, onOpenAuthor: (String) -> Unit, start: String? = null) {
    val context = LocalContext.current
    val book = remember { Leonardo.book(context) }
    var theme by rememberSaveable { mutableStateOf<String?>(null) }
    var open by rememberSaveable { mutableStateOf(start) }
    book.all.firstOrNull { it.id == open }?.let { h ->
        val list = book.themes.firstOrNull { it.title == h.theme }?.items ?: listOf(h)
        androidx.compose.runtime.key(h.id) {
            LeoPage(h, list, onGo = { open = it.id }, onBack = { if (start != null) onBack() else open = null }, onOpenAuthor = onOpenAuthor)
        }
        return
    }
    book.themes.firstOrNull { it.title == theme }?.let { t ->
        LeoThemeScreen(t, onOpen = { open = it.id }, onBack = { theme = null })
        return
    }
    val today = remember { book.all.let { all -> all[(Learn.today().toInt()).mod(all.size)] } }
    ToolScreen("Leonardo's notebooks", onBack) {
        item {
            Text(
                "Some 7,000 pages survive. ${book.all.size} highlights in his own words (tr. J. P. Richter, 1883), each with a note on what he was getting at.",
                style = MaterialTheme.typography.bodyMedium, color = Soft,
            )
        }
        item { Label("What he studied") }
        items(book.themes.chunked(2)) { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { t ->
                    Column(
                        Modifier.weight(1f).heightIn(min = 82.dp).clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.surfaceContainerHigh)
                            .clickable(onClickLabel = "Open ${t.title}") { theme = t.title }.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        Text(t.title, style = MaterialTheme.typography.titleSmall)
                        Text("${t.tagline} · ${t.items.size}", style = MaterialTheme.typography.bodySmall, color = Soft)
                    }
                }
                if (row.size == 1) Box(Modifier.weight(1f))
            }
        }
        item { Label("Today's page") }
        item {
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.surfaceContainerHigh)
                    .clickable(onClickLabel = "Read ${today.title}") { open = today.id }.padding(14.dp),
            ) {
                Text(today.title, fontFamily = BookSerif, fontSize = 17.sp)
                Text("${today.theme} · No. ${today.number}", style = MaterialTheme.typography.bodySmall, color = Soft)
            }
        }
        item {
            Text(
                "His words are from Jean Paul Richter's translation (1883), which numbers every passage; the titles and notes are written for the app.",
                style = MaterialTheme.typography.bodySmall, color = Soft,
            )
        }
    }
}

@Composable
private fun LeoThemeScreen(theme: LeoTheme, onOpen: (LeoHighlight) -> Unit, onBack: () -> Unit) {
    ToolScreen(theme.title, onBack) {
        item { Text(theme.tagline, style = MaterialTheme.typography.bodyMedium, color = Soft) }
        items(theme.items, key = { it.id }) { h ->
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.surfaceContainerHigh)
                    .clickable(onClickLabel = "Read ${h.title}") { onOpen(h) }.padding(horizontal = 14.dp, vertical = 11.dp),
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                Text(h.title, fontFamily = BookSerif, fontSize = 16.sp)
                Text(
                    h.paras.firstOrNull { !Leonardo.isHeading(it) } ?: "",
                    style = MaterialTheme.typography.bodySmall, color = Soft, maxLines = 2, overflow = TextOverflow.Ellipsis,
                )
                Text("No. ${h.number}", style = MaterialTheme.typography.labelSmall, color = Accent)
            }
        }
    }
}

/** One highlight: his words, the note, and previous and next in the theme. */
@Composable
private fun LeoPage(
    h: LeoHighlight,
    list: List<LeoHighlight>,
    onGo: (LeoHighlight) -> Unit,
    onBack: () -> Unit,
    onOpenAuthor: (String) -> Unit,
) {
    BackHandler(onBack = onBack)
    val context = LocalContext.current
    val rprefs = remember { QuotePrefs(context) }
    LaunchedEffect(h.id) {
        Reading.load(rprefs)
        val all = Leonardo.book(context).all
        Reading.record(rprefs, Reading.Place("leonardo", h.id, "${h.theme} · ${h.title}", "Leonardo's notebooks", "leonardo:${h.id}", all.indexOfFirst { it.id == h.id }, all.size, System.currentTimeMillis()))
    }
    val bundled = remember { QuoteRepository(context).bundled() }
    var answerShown by rememberSaveable(h.id) { mutableStateOf(false) }
    var opened by remember { mutableStateOf<Quote?>(null) }
    opened?.let { q -> QuotePageSheet(quote = q, onDismiss = { opened = null }, onOpenAuthor = { opened = null; onOpenAuthor(it) }) }
    val i = list.indexOf(h)
    val accent = Accent
    Scaffold(
        bottomBar = {
            val bkey = Reading.bookmarkKey("leonardo", h.id)
            var marked by remember(bkey) { mutableStateOf(bkey in rprefs.readerBookmarks) }
            ReaderBar(
                prevLabel = list.getOrNull(i - 1)?.title, nextLabel = list.getOrNull(i + 1)?.title,
                onPrev = { list.getOrNull(i - 1)?.let(onGo) }, onNext = { list.getOrNull(i + 1)?.let(onGo) },
                listenKey = "leo-${h.id}", listenTitle = h.title,
                paragraphs = h.paras.filter { !Leonardo.isHeading(it) },
                bookmarked = marked,
                onBookmark = { rprefs.readerBookmarks = if (marked) rprefs.readerBookmarks - bkey else rprefs.readerBookmarks + bkey; marked = !marked },
            )
        },
    ) { inner ->
        PassageSelection("Leonardo da Vinci, Notebooks (tr. J. P. Richter)", "Leonardo's notebooks") {
        LazyColumn(
            Modifier.fillMaxSize().padding(inner),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item { BackArrow(onBack) }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Label("${h.theme} · No. ${h.number}")
                    Text(h.title, fontFamily = BookSerif, fontSize = 26.sp, lineHeight = 32.sp)
                }
            }
            items(h.paras) { p ->
                when {
                    p == "…" -> Text("…", fontFamily = BookSerif, fontSize = 18.sp, color = Soft)
                    Leonardo.isHeading(p) -> Text(p, style = MaterialTheme.typography.labelMedium, letterSpacing = 1.sp, color = accent)
                    else -> {
                        val lines = remember(p) { Leonardo.linesIn(bundled, p) }
                        val text = remember(p, lines, accent) {
                            buildAnnotatedString {
                                append(p)
                                lines.forEach { (a, b, q) ->
                                    addLink(
                                        LinkAnnotation.Clickable(
                                            tag = "line-${q.id}",
                                            styles = TextLinkStyles(style = SpanStyle(textDecoration = TextDecoration.Underline, color = accent)),
                                            linkInteractionListener = { opened = q },
                                        ),
                                        a, b,
                                    )
                                }
                            }
                        }
                        Text(text, fontFamily = BookSerif, fontSize = readerSize(17f), lineHeight = readerSize(27f))
                    }
                }
            }
            if (h.answers.isNotEmpty()) item {
                if (answerShown) {
                    Text("Answer: " + h.answers.joinToString(" "), fontFamily = BookSerif, fontStyle = FontStyle.Italic, fontSize = 16.sp, color = accent)
                } else {
                    Box(
                        Modifier.clip(RoundedCornerShape(18.dp)).border(1.dp, accent.copy(alpha = 0.6f), RoundedCornerShape(18.dp))
                            .clickable(onClickLabel = "Show the answer") { answerShown = true }.padding(horizontal = 14.dp, vertical = 7.dp),
                    ) { Text("What is it? Tap for the answer", style = MaterialTheme.typography.labelLarge, color = accent) }
                }
            }
            item { ChapterIdeas("leonardo", h.id) { opened = it } }
            if (h.note.isNotEmpty()) item {
                Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min).clip(RoundedCornerShape(12.dp)).background(accent.copy(alpha = 0.10f))) {
                    Box(Modifier.width(3.dp).fillMaxHeight().background(accent))
                    Text(
                        buildAnnotatedString {
                            pushStyle(SpanStyle(fontWeight = FontWeight.SemiBold)); append("What he was getting at. "); pop()
                            append(h.note)
                        },
                        style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(12.dp),
                    )
                }
            }
        }
        }
    }
}

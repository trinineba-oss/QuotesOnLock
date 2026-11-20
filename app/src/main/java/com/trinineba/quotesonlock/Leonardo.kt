package com.trinineba.quotesonlock

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
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

/** One of his drawings, with its caption. */
class LeoDrawing(val key: String, val title: String, val date: String, val medium: String, val source: String)

/** One highlight: Richter's passage [number], a title and note written for the app, his words, and riddle answers. */
class LeoHighlight(
    val id: String,
    val number: Int,
    val title: String,
    val drawing: String?,
    val note: String,
    val paras: List<String>,
    val answers: List<String>,
    val theme: String,
)

class LeoTheme(val title: String, val tagline: String, val tint: Long, val items: List<LeoHighlight>)

class LeoBook(val drawings: Map<String, LeoDrawing>, val themes: List<LeoTheme>) {
    val all: List<LeoHighlight> get() = themes.flatMap { it.items }
}

/** Leonardo's notebooks (res/raw/leonardo.txt), in J. P. Richter's public-domain translation (1883). */
object Leonardo {
    @Volatile private var book: LeoBook? = null

    fun parse(lines: Sequence<String>): LeoBook {
        val drawings = linkedMapOf<String, LeoDrawing>()
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
                    if (k == 1) "$n" else "$n.$k", n, h[2], h.getOrNull(3)?.ifBlank { null }, h.getOrElse(4) { "" },
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
                "D" -> if (f.size >= 6) drawings[f[1]] = LeoDrawing(f[1], f[2], f[3], f[4], f[5])
                "G" -> { flushTheme(); theme = f }
                "H" -> { flushItem(); head = f }
                "P" -> paras += line.substringAfter('|')
                "A" -> answers += line.substringAfter('|')
            }
        }
        flushTheme()
        return LeoBook(drawings, themes)
    }

    fun book(context: Context): LeoBook = book ?: synchronized(this) {
        book ?: context.applicationContext.resources.openRawResource(R.raw.leonardo).bufferedReader().useLines { parse(it) }.also { book = it }
    }

    @androidx.annotation.DrawableRes
    fun drawingRes(key: String): Int = when (key) {
        "bear" -> R.drawable.leo_bear
        "grotesque" -> R.drawable.leo_grotesque
        "profile" -> R.drawable.leo_profile
        "virgin" -> R.drawable.leo_virgin
        "lizard" -> R.drawable.leo_lizard
        "sketches" -> R.drawable.leo_sketches
        else -> 0
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

// A sheet of old paper, whatever the app's theme.
private val Ink = Color(0xFF2E2012)
private val Sepia = Color(0xFF9A5A1E)
private val Faded = Color(0xFF6B5236)
private val Card = Color(0xCCFBF4E3)
private val PaperBrush = Brush.linearGradient(listOf(Color(0xFFF6ECD6), Color(0xFFEADBB8), Color(0xFFDCC495)))

@Composable
private fun Paper(content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize().background(PaperBrush).statusBarsPadding().navigationBarsPadding()) { content() }
}

@Composable
private fun PaperBack(onBack: () -> Unit) {
    Box(
        Modifier.clip(RoundedCornerShape(24.dp)).clickable(onClickLabel = "Back", onClick = onBack).padding(10.dp),
    ) { Icon(painterResource(R.drawable.ic_arrow_back), contentDescription = "Back", tint = Ink) }
}

@Composable
private fun PaperLabel(text: String, modifier: Modifier = Modifier) {
    Text(text.uppercase(), fontSize = 11.sp, letterSpacing = 1.4.sp, color = Sepia, modifier = modifier)
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
            LeoPage(book, h, list, onGo = { open = it.id }, onBack = { if (start != null) onBack() else open = null }, onOpenAuthor = onOpenAuthor)
        }
        return
    }
    book.themes.firstOrNull { it.title == theme }?.let { t ->
        LeoThemeScreen(t, onOpen = { open = it.id }, onBack = { theme = null })
        return
    }
    BackHandler(onBack = onBack)
    val today = remember { book.all.let { all -> all[(Learn.today().toInt()).mod(all.size)] } }
    Paper {
        LazyColumn(contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 8.dp, bottom = 28.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item { PaperBack(onBack) }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Leonardo's notebooks", fontFamily = FontFamily.Serif, fontSize = 28.sp, color = Ink)
                    Text(
                        "Some 7,000 pages survive, written right to left in mirror script. ${book.all.size} highlights in his own words " +
                            "(tr. J. P. Richter, 1883), each with a note on what he was getting at.",
                        fontSize = 14.sp, lineHeight = 20.sp, color = Faded,
                    )
                    Text(
                        "Leonardo da Vinci",
                        fontFamily = FontFamily.Cursive, fontSize = 18.sp, color = Sepia.copy(alpha = 0.6f),
                        modifier = Modifier.graphicsLayer(scaleX = -1f),
                    )
                }
            }
            item { PaperLabel("What he studied", Modifier.padding(top = 6.dp)) }
            items(book.themes.chunked(2)) { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    row.forEach { t ->
                        Column(
                            Modifier.weight(1f).heightIn(min = 82.dp).clip(RoundedCornerShape(14.dp)).background(Card)
                                .border(1.dp, Color(t.tint).copy(alpha = 0.35f), RoundedCornerShape(14.dp))
                                .clickable(onClickLabel = "Open ${t.title}") { theme = t.title }.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(2.dp),
                        ) {
                            Text(t.title, fontFamily = FontFamily.Serif, fontSize = 16.sp, color = Ink)
                            Text("${t.tagline} · ${t.items.size}", fontSize = 12.sp, lineHeight = 16.sp, color = Faded)
                        }
                    }
                    if (row.size == 1) Box(Modifier.weight(1f))
                }
            }
            item { PaperLabel("Today's page", Modifier.padding(top = 6.dp)) }
            item {
                Column(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Card)
                        .clickable(onClickLabel = "Read ${today.title}") { open = today.id }.padding(14.dp),
                ) {
                    Text(today.title, fontFamily = FontFamily.Serif, fontSize = 17.sp, color = Ink)
                    Text("${today.theme} · No. ${today.number}", fontSize = 12.sp, color = Faded)
                }
            }
            item {
                Text(
                    "His words are from Jean Paul Richter's translation (1883), which numbers every passage; the notes are written for the app. " +
                        "The drawings are in the public domain, from The Metropolitan Museum of Art.",
                    fontSize = 12.sp, lineHeight = 17.sp, color = Faded, modifier = Modifier.padding(top = 8.dp),
                )
            }
        }
    }
}

@Composable
private fun LeoThemeScreen(theme: LeoTheme, onOpen: (LeoHighlight) -> Unit, onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    Paper {
        LazyColumn(contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 8.dp, bottom = 28.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item { PaperBack(onBack) }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(theme.title, fontFamily = FontFamily.Serif, fontSize = 26.sp, color = Ink)
                    Text(theme.tagline, fontSize = 14.sp, color = Faded)
                }
            }
            items(theme.items, key = { it.id }) { h ->
                Column(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Card)
                        .clickable(onClickLabel = "Read ${h.title}") { onOpen(h) }.padding(horizontal = 14.dp, vertical = 11.dp),
                    verticalArrangement = Arrangement.spacedBy(3.dp),
                ) {
                    Text(h.title, fontFamily = FontFamily.Serif, fontSize = 16.sp, color = Ink)
                    Text(
                        h.paras.firstOrNull { !Leonardo.isHeading(it) } ?: "",
                        fontFamily = FontFamily.Serif, fontStyle = FontStyle.Italic, fontSize = 13.sp, lineHeight = 18.sp,
                        color = Faded, maxLines = 2, overflow = TextOverflow.Ellipsis,
                    )
                    Text("No. ${h.number}" + if (h.drawing != null) " · with a drawing" else "", fontSize = 11.sp, color = Sepia)
                }
            }
        }
    }
}

/** One highlight: his words (with a mirror view), a drawing where there is one, the note, and previous and next in the theme. */
@Composable
private fun LeoPage(
    book: LeoBook,
    h: LeoHighlight,
    list: List<LeoHighlight>,
    onGo: (LeoHighlight) -> Unit,
    onBack: () -> Unit,
    onOpenAuthor: (String) -> Unit,
) {
    BackHandler(onBack = onBack)
    val context = LocalContext.current
    val bundled = remember { QuoteRepository(context).bundled() }
    var mirrored by rememberSaveable(h.id) { mutableStateOf(false) }
    var answerShown by rememberSaveable(h.id) { mutableStateOf(false) }
    var opened by remember { mutableStateOf<Quote?>(null) }
    opened?.let { q -> QuotePageSheet(quote = q, onDismiss = { opened = null }, onOpenAuthor = { opened = null; onOpenAuthor(it) }) }
    val i = list.indexOf(h)
    Paper {
        Column(Modifier.fillMaxSize()) {
            LazyColumn(
                Modifier.weight(1f),
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item { PaperBack(onBack) }
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        PaperLabel("${h.theme} · No. ${h.number}")
                        Text(h.title, fontFamily = FontFamily.Serif, fontSize = 24.sp, lineHeight = 30.sp, color = Ink)
                    }
                }
                items(h.paras) { p ->
                    when {
                        p == "…" -> Text("…", fontFamily = FontFamily.Serif, fontSize = 18.sp, color = Faded)
                        Leonardo.isHeading(p) -> Text(p, fontSize = 12.sp, letterSpacing = 1.sp, color = Sepia, fontWeight = FontWeight.Medium)
                        mirrored -> Text(
                            p, fontFamily = FontFamily.Cursive, fontStyle = FontStyle.Italic, fontSize = 19.sp, lineHeight = 28.sp,
                            color = Color(0xFF5A3A1A), modifier = Modifier.fillMaxWidth().graphicsLayer(scaleX = -1f),
                        )
                        else -> {
                            val lines = remember(p) { Leonardo.linesIn(bundled, p) }
                            val text = remember(p, lines) {
                                buildAnnotatedString {
                                    append(p)
                                    lines.forEach { (a, b, q) ->
                                        addLink(
                                            LinkAnnotation.Clickable(
                                                tag = "line-${q.id}",
                                                styles = TextLinkStyles(style = SpanStyle(textDecoration = TextDecoration.Underline, color = Sepia)),
                                                linkInteractionListener = { opened = q },
                                            ),
                                            a, b,
                                        )
                                    }
                                }
                            }
                            Text(text, fontFamily = FontFamily.Serif, fontSize = 17.sp, lineHeight = 27.sp, color = Ink)
                        }
                    }
                }
                if (h.answers.isNotEmpty()) item {
                    if (answerShown) {
                        Text(
                            "Answer: " + h.answers.joinToString(" "),
                            fontFamily = FontFamily.Serif, fontStyle = FontStyle.Italic, fontSize = 16.sp, color = Sepia,
                        )
                    } else {
                        Box(
                            Modifier.clip(RoundedCornerShape(18.dp)).border(1.dp, Sepia.copy(alpha = 0.6f), RoundedCornerShape(18.dp))
                                .clickable(onClickLabel = "Show the answer") { answerShown = true }.padding(horizontal = 14.dp, vertical = 7.dp),
                        ) { Text("What is it? Tap for the answer", fontSize = 13.sp, color = Sepia) }
                    }
                }
                h.drawing?.let { key -> book.drawings[key]?.let { d -> item { LeoDrawingCard(d) } } }
                if (h.note.isNotEmpty()) item {
                    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min).clip(RoundedCornerShape(10.dp)).background(Sepia.copy(alpha = 0.1f))) {
                        Box(Modifier.width(3.dp).fillMaxHeight().background(Sepia))
                        Text(
                            buildAnnotatedString {
                                pushStyle(SpanStyle(fontWeight = FontWeight.SemiBold)); append("What he was getting at. "); pop()
                                append(h.note)
                            },
                            fontSize = 14.sp, lineHeight = 20.sp, color = Color(0xFF4A3420), modifier = Modifier.padding(12.dp),
                        )
                    }
                }
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Box(
                            Modifier.clip(RoundedCornerShape(18.dp)).border(1.dp, Sepia.copy(alpha = 0.6f), RoundedCornerShape(18.dp))
                                .clickable(onClickLabel = if (mirrored) "Back to reading" else "See it as he wrote it") { mirrored = !mirrored }
                                .padding(horizontal = 14.dp, vertical = 7.dp),
                        ) { Text(if (mirrored) "⟲ Back to reading" else "⟲ See it as he wrote it", fontSize = 13.sp, color = Sepia) }
                    }
                }
                if (mirrored) item {
                    Text(
                        "Leonardo wrote from right to left, in mirror image, all his life, probably because he was left-handed and it kept the ink from smudging. " +
                            "Hold your phone up to a mirror to read it. The words here are Richter's English, not his Italian.",
                        fontSize = 13.sp, lineHeight = 19.sp, color = Faded,
                    )
                }
            }
            Row(Modifier.fillMaxWidth().background(Color(0xFFE2CFA5)).padding(horizontal = 6.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { list.getOrNull(i - 1)?.let(onGo) }, enabled = i > 0, modifier = Modifier.weight(1f)) {
                    Text("‹ " + (list.getOrNull(i - 1)?.title ?: ""), maxLines = 1, overflow = TextOverflow.Ellipsis, color = if (i > 0) Sepia else Faded.copy(alpha = 0.4f))
                }
                Text("${i + 1} / ${list.size}", fontSize = 12.sp, color = Faded)
                TextButton(onClick = { list.getOrNull(i + 1)?.let(onGo) }, enabled = i < list.size - 1, modifier = Modifier.weight(1f)) {
                    Text((list.getOrNull(i + 1)?.title ?: "") + " ›", maxLines = 1, overflow = TextOverflow.Ellipsis, color = if (i < list.size - 1) Sepia else Faded.copy(alpha = 0.4f))
                }
            }
        }
    }
}

@Composable
private fun LeoDrawingCard(d: LeoDrawing) {
    var big by remember { mutableStateOf(false) }
    val res = Leonardo.drawingRes(d.key)
    if (res == 0) return
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Image(
            painterResource(res),
            contentDescription = d.title,
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxWidth().heightIn(max = if (big) 640.dp else 300.dp).clip(RoundedCornerShape(10.dp))
                .clickable(onClickLabel = if (big) "Smaller" else "Larger") { big = !big },
        )
        Text("${d.title}, ${d.date}, ${d.medium.replaceFirstChar { it.lowercase() }} · ${d.source}", fontSize = 11.sp, lineHeight = 15.sp, color = Faded)
    }
}


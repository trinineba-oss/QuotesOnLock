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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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

/** One letter, or a passage from one: [id] is stable (kept for progress), [note] is written for the app. */
class Letter(
    val id: String,
    val title: String,
    val heading: String,
    val source: String,
    val note: String,
    val paras: List<String>,
) {
    val words: Int get() = paras.sumOf { it.split(' ').size }
    val minutes: Int get() = (words / 200).coerceAtLeast(1)
}

class LetterGroup(val title: String, val years: String, val letters: List<Letter>)

/**
 * Van Gogh's letters (res/raw/letters.txt): full letters to Theo in Jo van Gogh-Bonger's translation (1927),
 * and passages from the later letters in Anthony M. Ludovici's (1913), both in the public domain.
 */
object Letters {
    @Volatile private var groups: List<LetterGroup>? = null

    const val SOURCE_BONGER = "B"
    const val SOURCE_LUDOVICI = "L"

    fun sourceName(key: String): String = when (key) {
        SOURCE_LUDOVICI -> "tr. Anthony M. Ludovici, 1913"
        else -> "tr. Jo van Gogh-Bonger, 1927"
    }

    fun parse(lines: Sequence<String>): List<LetterGroup> {
        val out = mutableListOf<LetterGroup>()
        var group: List<String>? = null
        var letters = mutableListOf<Letter>()
        var head: List<String>? = null
        var paras = mutableListOf<String>()
        fun flushLetter() {
            head?.let { h -> if (paras.isNotEmpty()) letters += Letter(h[1], h[2], h[3], h[4], h.getOrElse(5) { "" }, paras) }
            head = null; paras = mutableListOf()
        }
        fun flushGroup() {
            flushLetter()
            group?.let { g -> if (letters.isNotEmpty()) out += LetterGroup(g[1], g.getOrElse(2) { "" }, letters) }
            group = null; letters = mutableListOf()
        }
        lines.filter { it.isNotBlank() && !it.startsWith("#") }.forEach { line ->
            when (line.substringBefore('|')) {
                "G" -> { flushGroup(); group = line.split('|') }
                "L" -> { flushLetter(); head = line.split('|') }
                "P" -> paras += line.substringAfter('|')
            }
        }
        flushGroup()
        return out
    }

    fun groups(context: Context): List<LetterGroup> = groups ?: synchronized(this) {
        groups ?: context.applicationContext.resources.openRawResource(R.raw.letters).bufferedReader().useLines { parse(it) }.also { groups = it }
    }

    /** Van Gogh's lines in the collection that appear word for word in [para], with where they start and end. */
    fun linesIn(bundled: List<Quote>, para: String): List<Triple<Int, Int, Quote>> =
        bundled.asSequence()
            .filter { it.title == null && it.author?.startsWith("Vincent van Gogh") == true && it.text.length > 20 }
            .mapNotNull { q -> para.indexOf(q.text).takeIf { it >= 0 }?.let { Triple(it, it + q.text.length, q) } }
            .sortedBy { it.first }
            .fold(mutableListOf<Triple<Int, Int, Quote>>()) { acc, t -> if (acc.isEmpty() || t.first >= acc.last().second) acc += t; acc }
}

private val Gold = Color(0xFFD4A72C)

/** The letters, grouped by where he lived, with "Continue" to pick up where you left off. */
@Composable
fun LettersScreen(onBack: () -> Unit, onOpenAuthor: (String) -> Unit) {
    val context = LocalContext.current
    val prefs = remember { QuotePrefs(context) }
    val groups = remember { Letters.groups(context) }
    val all = remember(groups) { groups.flatMap { it.letters } }
    var open by rememberSaveable { mutableStateOf<String?>(null) }
    var refresh by remember { mutableIntStateOf(0) }
    all.indexOfFirst { it.id == open }.takeIf { it >= 0 }?.let { i ->
        androidx.compose.runtime.key(i) {
            LetterScreen(all, i, prefs, onGo = { open = all[it].id }, onBack = { open = null; refresh++ }, onOpenAuthor = onOpenAuthor)
        }
        return
    }
    val read = remember(refresh) { prefs.lettersRead }
    val last = remember(refresh) { prefs.lastLetter?.let { id -> all.firstOrNull { it.id == id } } }
    ToolScreen("Van Gogh's letters", onBack) {
        item {
            Text(
                "Most of what we know of his inner life is in his letters to his brother Theo. ${all.size} letters and passages, " +
                    "from the young art dealer's clerk in The Hague to the painter in Arles and Saint-Rémy, " +
                    "in the translations of Jo van Gogh-Bonger, Theo's widow, and Anthony M. Ludovici." +
                    if (read.isNotEmpty()) " You've read ${read.count { id -> all.any { it.id == id } }}." else "",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        last?.let { l ->
            item {
                Box(
                    Modifier.clip(RoundedCornerShape(20.dp)).border(1.dp, Gold.copy(alpha = 0.6f), RoundedCornerShape(20.dp))
                        .clickable(onClickLabel = "Continue reading") { open = l.id }.padding(horizontal = 14.dp, vertical = 8.dp),
                ) { Text("Continue: ${l.title}", style = MaterialTheme.typography.labelLarge, color = Gold) }
            }
        }
        groups.forEach { g ->
            item(key = "g-" + g.title) {
                Text(
                    (g.title + if (g.years.isNotEmpty()) " · " + g.years else "").uppercase(),
                    style = MaterialTheme.typography.labelMedium,
                    color = Gold,
                    letterSpacing = 1.2.sp,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
            g.letters.forEach { l ->
                item(key = "l-" + l.id) {
                    Column(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.surfaceContainerHigh)
                            .clickable(onClickLabel = "Read ${l.title}") { open = l.id }.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(3.dp),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(l.title, fontFamily = FontFamily.Serif, fontSize = 17.sp, modifier = Modifier.weight(1f))
                            if (l.id in read) Text("✓", color = Gold, style = MaterialTheme.typography.labelLarge)
                        }
                        Text(
                            "${l.heading} · ${l.minutes} min",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        if (l.note.isNotEmpty()) {
                            Text(
                                l.note,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
        }
        item {
            Text(
                "The letters are in the public domain, in the translations named on each. Underlined lines are in the collection: tap one to open it.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}

/** One letter: a note on where he was, the letter itself in a reading serif with the collection's lines underlined, and previous and next. */
@Composable
private fun LetterScreen(all: List<Letter>, index: Int, prefs: QuotePrefs, onGo: (Int) -> Unit, onBack: () -> Unit, onOpenAuthor: (String) -> Unit) {
    BackHandler(onBack = onBack)
    val context = LocalContext.current
    val letter = all[index]
    val bundled = remember { QuoteRepository(context).bundled() }
    var opened by remember { mutableStateOf<Quote?>(null) }
    opened?.let { q -> QuotePageSheet(quote = q, onDismiss = { opened = null }, onOpenAuthor = { opened = null; onOpenAuthor(it) }) }
    val state = rememberLazyListState()
    LaunchedEffect(letter.id) { prefs.lastLetter = letter.id }
    // Counted as read once the end comes into view.
    LaunchedEffect(state, letter.id) {
        androidx.compose.runtime.snapshotFlow { state.layoutInfo.visibleItemsInfo.lastOrNull()?.index }.collect { last ->
            if (last != null && last >= state.layoutInfo.totalItemsCount - 1) prefs.lettersRead = prefs.lettersRead + letter.id
        }
    }
    Scaffold(
        bottomBar = {
            Row(
                Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceContainer).padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = { onGo(index - 1) }, enabled = index > 0, modifier = Modifier.weight(1f)) {
                    Text("‹ " + (all.getOrNull(index - 1)?.title ?: ""), maxLines = 1, overflow = TextOverflow.Ellipsis, color = if (index > 0) Gold else Color.Unspecified)
                }
                Text("${index + 1} / ${all.size}", style = MaterialTheme.typography.bodySmall)
                TextButton(onClick = { onGo(index + 1) }, enabled = index < all.size - 1, modifier = Modifier.weight(1f)) {
                    Text((all.getOrNull(index + 1)?.title ?: "") + " ›", maxLines = 1, overflow = TextOverflow.Ellipsis, color = if (index < all.size - 1) Gold else Color.Unspecified)
                }
            }
        },
    ) { inner ->
        LazyColumn(
            Modifier.fillMaxSize().padding(inner),
            state = state,
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item { BackArrow(onBack) }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(letter.heading.uppercase(), style = MaterialTheme.typography.labelMedium, color = Gold, letterSpacing = 1.2.sp)
                    Text(letter.title, fontFamily = FontFamily.Serif, fontSize = 26.sp, lineHeight = 32.sp)
                    Text(
                        "${letter.minutes} min · ${Letters.sourceName(letter.source)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (letter.note.isNotEmpty()) item {
                Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min).clip(RoundedCornerShape(12.dp)).background(Gold.copy(alpha = 0.12f))) {
                    Box(Modifier.width(3.dp).fillMaxHeight().background(Gold))
                    Text(
                        buildAnnotatedString {
                            pushStyle(SpanStyle(fontWeight = FontWeight.SemiBold)); append("Where he was. "); pop()
                            append(letter.note)
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(12.dp),
                    )
                }
            }
            itemsIndexed(letter.paras) { _, para ->
                val lines = remember(para) { Letters.linesIn(bundled, para) }
                val text = remember(para, lines) {
                    buildAnnotatedString {
                        append(para)
                        lines.forEach { (start, end, q) ->
                            addLink(
                                LinkAnnotation.Clickable(
                                    tag = "line-${q.id}",
                                    styles = TextLinkStyles(style = SpanStyle(textDecoration = TextDecoration.Underline, color = Gold)),
                                    linkInteractionListener = { opened = q },
                                ),
                                start, end,
                            )
                        }
                    }
                }
                Text(text, fontFamily = FontFamily.Serif, fontSize = 17.sp, lineHeight = 27.sp)
            }
            item {
                Text(
                    "From ${if (letter.source == Letters.SOURCE_LUDOVICI) "The Letters of a Post-Impressionist (1913)" else "The Letters of Vincent van Gogh to His Brother, 1872–1886 (1927)"}, ${Letters.sourceName(letter.source)}." +
                        if (letter.source == Letters.SOURCE_LUDOVICI) " Ludovici gave passages rather than whole letters, without dates." else "",
                    style = MaterialTheme.typography.bodySmall,
                    fontStyle = FontStyle.Italic,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            item { Spacer(Modifier.height(8.dp)) }
        }
    }
}

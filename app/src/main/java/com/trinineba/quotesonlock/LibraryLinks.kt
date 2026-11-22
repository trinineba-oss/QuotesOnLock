package com.trinineba.quotesonlock

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.withContext

/**
 * Where the collection's quotes and great ideas live in the Library: which room, which chapter. Lets an idea's
 * page open the chapter it comes from, and a chapter show its ideas and underline the lines in the collection.
 */
object LibraryLinks {
    /** A place in a room: [route] opens it; [room] is "plato", "letters", "leonardo" or a book's id; [item] its chapter. */
    class Spot(val route: String, val label: String, val room: String, val item: String)

    private class Passage(val spot: Spot, val owns: (String) -> Boolean, val key: String)

    private class Index(val byKey: Map<String, Spot>, val byRoom: Map<String, List<Pair<Quote, Spot>>>)

    @Volatile private var index: Index? = null

    /** A request from anywhere (an idea's page) to open a Library route; MainActivity takes it. */
    val open = MutableStateFlow<String?>(null)

    /** The route of the reader on screen, so "Read it at length" is not offered for the page you are on. */
    val here = MutableStateFlow<String?>(null)

    private fun key(paras: List<String>) = QuoteData.wordsKey(paras.joinToString(" "))

    private fun passages(context: Context): List<Passage> {
        val out = mutableListOf<Passage>()
        runCatching {
            Plato.dialogues(context).flatMap { it.scenes }.forEach { s ->
                out += Passage(Spot("plato:${s.id}", "${s.dialogue} · ${s.title}", "plato", s.id), { it.startsWith("Plato") }, key(s.paras))
            }
        }
        runCatching {
            Letters.groups(context).flatMap { it.letters }.forEach { l ->
                out += Passage(Spot("letters:${l.id}", "Van Gogh's letters · ${l.title}", "letters", l.id), { it.startsWith("Vincent van Gogh") }, key(l.paras))
            }
        }
        runCatching {
            Leonardo.book(context).all.forEach { h ->
                out += Passage(Spot("leonardo:${h.id}", "Leonardo's notebooks · ${h.title}", "leonardo", h.id), { it.startsWith("Leonardo") }, key(h.paras))
            }
        }
        runCatching {
            Books.all(context).forEach { b ->
                b.chapters.forEach { c ->
                    out += Passage(Spot("book:${b.id}/${c.id}", "${b.title} · ${c.title}", b.id, c.id), { it.startsWith(b.author) }, key(c.paras))
                }
            }
        }
        return out
    }

    private fun build(context: Context): Index {
        val all = passages(context)
        val byKey = HashMap<String, Spot>()
        val byRoom = HashMap<String, MutableList<Pair<Quote, Spot>>>()
        QuoteRepository(context).bundled().forEach { q ->
            val author = q.author ?: return@forEach
            val k = QuoteData.wordsKey(q.text)
            if (k.length < 20) return@forEach
            val p = all.firstOrNull { it.owns(author) && it.key.contains(k) } ?: return@forEach
            byKey[k] = p.spot
            byRoom.getOrPut(p.spot.room) { mutableListOf() } += q to p.spot
        }
        return Index(byKey, byRoom)
    }

    private fun index(context: Context): Index = index ?: synchronized(this) { index ?: build(context.applicationContext).also { index = it } }

    /** Where [quote] is in the Library, if it is there. */
    fun spot(context: Context, quote: Quote): Spot? = index(context).byKey[QuoteData.wordsKey(quote.text)]

    /** The quotes and ideas found in [room], with where each is. */
    fun inRoom(context: Context, room: String): List<Pair<Quote, Spot>> = index(context).byRoom[room].orEmpty()

    /**
     * Where each of [quotes] appears in [para], ignoring case and punctuation, as (start, end, quote) without overlaps;
     * a quote that runs over two paragraphs is not underlined.
     */
    fun linesIn(quotes: List<Quote>, para: String): List<Triple<Int, Int, Quote>> {
        val keyed = StringBuilder()
        val at = IntArray(para.length)
        para.forEachIndexed { i, c -> if (c.isLetterOrDigit()) { at[keyed.length] = i; keyed.append(c.lowercaseChar()) } }
        val k = keyed.toString()
        return quotes.mapNotNull { q ->
            val qk = QuoteData.wordsKey(q.text)
            val start = if (qk.isEmpty()) -1 else k.indexOf(qk)
            if (start < 0) null else Triple(at[start], at[start + qk.length - 1] + 1, q)
        }
            .sortedBy { it.first }
            .fold(mutableListOf<Triple<Int, Int, Quote>>()) { acc, t -> if (acc.isEmpty() || t.first >= acc.last().second) acc += t; acc }
    }
}

/** The quotes and ideas in [room], worked out off the main thread (empty until then). */
@Composable
fun roomLines(room: String): List<Pair<Quote, LibraryLinks.Spot>> {
    val context = LocalContext.current
    val lines by produceState(emptyList<Pair<Quote, LibraryLinks.Spot>>(), room) {
        value = withContext(Dispatchers.Default) { runCatching { LibraryLinks.inRoom(context, room) }.getOrDefault(emptyList()) }
    }
    return lines
}

/** A gold card for a great idea, with its brief. */
@Composable
fun IdeaCard(label: String, idea: Quote, onClick: () -> Unit) {
    val accent = MaterialTheme.colorScheme.primary
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(accent.copy(alpha = 0.10f))
            .border(1.dp, accent.copy(alpha = 0.45f), RoundedCornerShape(14.dp))
            .clickable(onClickLabel = "Open ${idea.title}", onClick = onClick).padding(horizontal = 14.dp, vertical = 11.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Text(label.uppercase(), style = MaterialTheme.typography.labelSmall, letterSpacing = 1.2.sp, color = accent)
        Text("${idea.title} ›", fontFamily = FontFamily.Serif, fontSize = 17.sp)
        idea.brief?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}

/** The great ideas found in one chapter of [room], as cards that open each idea. */
@Composable
fun ChapterIdeas(room: String, item: String, onOpen: (Quote) -> Unit) {
    val ideas = roomLines(room).filter { (q, s) -> q.isIdea && s.item == item }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        ideas.forEach { (q, _) -> IdeaCard(if (ideas.size == 1) "The great idea in this chapter" else "A great idea in this chapter", q) { onOpen(q) } }
    }
}

/** "Read it at length": on a quote's or idea's page, opens the Library page it comes from. */
@Composable
fun ReadAtLength(quote: Quote, onGo: () -> Unit = {}) {
    val context = LocalContext.current
    val here by LibraryLinks.here.collectAsState()
    val spot by produceState<LibraryLinks.Spot?>(null, quote.text) {
        value = withContext(Dispatchers.Default) { runCatching { LibraryLinks.spot(context, quote) }.getOrNull() }
    }
    val s = spot ?: return
    if (s.route == here) return
    val accent = MaterialTheme.colorScheme.primary
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(accent.copy(alpha = 0.10f))
            .border(1.dp, accent.copy(alpha = 0.45f), RoundedCornerShape(14.dp))
            .clickable(onClickLabel = "Read it in the Library") { onGo(); LibraryLinks.open.value = s.route }
            .padding(horizontal = 14.dp, vertical = 11.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Text("READ IT AT LENGTH", style = MaterialTheme.typography.labelSmall, letterSpacing = 1.2.sp, color = accent)
        Text("${s.label} ›", fontFamily = FontFamily.Serif, fontSize = 16.sp)
        Text("In the Library, with its guide", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

package com.trinineba.quotesonlock

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** One chapter (or poem, sermon, entry) of a Library book; [note] is written for the app and may be empty. */
class BookChapter(val id: String, val title: String, val note: String, val paras: List<String>) {
    val minutes: Int get() = (paras.sumOf { it.split(' ', '\n').size } / 200).coerceAtLeast(1)
}

/** A whole public-domain book in the Library, with an introduction and a guide written for the app. */
class Book(
    val id: String,
    val title: String,
    val author: String,
    val translation: String,
    val tagline: String,
    val tint: Long,
    val shelfLabel: String,
    val intro: List<String>,
    val guide: PlatoAnalysis?,
    val chapters: List<BookChapter>,
) {
    /** How a saved passage is credited. */
    val credit: String get() = "$author, $title" + if (translation.isNotEmpty()) " (${translation.substringBefore(',')})" else ""
}

/** The Library's books (res/raw/book_*.txt). */
object Books {
    @Volatile private var all: List<Book>? = null

    /** Every book, in shelf order. */
    val files: List<Int> get() = listOf(R.raw.book_tao, R.raw.book_meditations, R.raw.book_enchiridion, R.raw.book_prophet, R.raw.book_gitanjali)

    fun parse(lines: Sequence<String>): Book? {
        var head: List<String>? = null
        val intro = mutableListOf<String>()
        val sections = mutableListOf<Pair<String, MutableList<String>>>()
        val questions = mutableListOf<String>()
        var key: List<String>? = null
        val chapters = mutableListOf<BookChapter>()
        var ch: List<String>? = null
        var paras = mutableListOf<String>()
        fun flush() { ch?.let { c -> if (paras.isNotEmpty()) chapters += BookChapter(c[1], c.getOrElse(2) { "" }, c.getOrElse(3) { "" }, paras) }; ch = null; paras = mutableListOf() }
        lines.filter { it.isNotBlank() && !it.startsWith("#") }.forEach { line ->
            when (line.substringBefore('|')) {
                "B" -> head = line.split('|')
                "I" -> intro += line.substringAfter('|')
                "A" -> line.split('|', limit = 3).takeIf { it.size == 3 }?.let { f ->
                    if (sections.lastOrNull()?.first == f[1]) sections.last().second += f[2] else sections += f[1] to mutableListOf(f[2])
                }
                "K" -> questions += line.substringAfter('|')
                "X" -> key = line.split('|')
                "C" -> { flush(); ch = line.split('|') }
                "T" -> paras += line.substringAfter('|').replace("\\n", "\n")
            }
        }
        flush()
        val h = head ?: return null
        val guide = if (sections.isEmpty()) null else PlatoAnalysis(sections.map { it.first to it.second.toList() }, questions, key?.getOrNull(1) ?: "", key?.getOrNull(2) ?: "")
        return Book(
            h[1], h[2], h.getOrElse(3) { "" }, h.getOrElse(4) { "" }, h.getOrElse(5) { "" },
            0xFF000000 or (h.getOrNull(6)?.toLongOrNull(16) ?: 0x4A6A8C), h.getOrElse(7) { "" }, intro, guide, chapters,
        )
    }

    fun all(context: Context): List<Book> = all ?: synchronized(this) {
        all ?: files.mapNotNull { id -> context.applicationContext.resources.openRawResource(id).bufferedReader().useLines { parse(it) } }.also { all = it }
    }
}

/** A shelf in the Library: one room, which opens at [route]. */
private class Shelf(val title: String, val label: String, val tint: Long, val route: String)

/** The Library: Continue reading, bookmarks, and every room on the shelves. */
@Composable
fun LibraryScreen(onBack: () -> Unit, onOpen: (String) -> Unit) {
    val context = LocalContext.current
    val prefs = remember { QuotePrefs(context) }
    var refresh by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) { refresh++ }
    val recent = remember(refresh) { Reading.recent(prefs) }
    val bookmarks = remember(refresh) { prefs.readerBookmarks }
    val shelves = remember {
        val built = listOf(
            Shelf("Plato's dialogues", "${runCatching { Plato.dialogues(context).sumOf { it.scenes.size } }.getOrDefault(0)} scenes", 0xFF4A6A8C, "plato"),
            Shelf("Van Gogh's letters", "${runCatching { Letters.groups(context).sumOf { it.letters.size } }.getOrDefault(0)} letters", 0xFF2B4C8C, "letters"),
            Shelf("Leonardo's notebooks", "${runCatching { Leonardo.book(context).all.size }.getOrDefault(0)} pages", 0xFF9A5A1E, "leonardo"),
        )
        built + Books.all(context).map { Shelf(it.title, it.shelfLabel, it.tint, "book:${it.id}") }
    }
    val places = remember(recent) { recent.associateBy { it.room } }
    ToolScreen("Library", onBack) {
        item {
            Text(
                "Great books and letters to read at length, each with notes written for the app. Select any passage to save it, note it or share it.",
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (recent.isNotEmpty()) {
            item { LibLabel("Continue reading") }
            items(recent.take(3), key = { "r-" + it.room }) { p ->
                Column(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.surfaceContainerHigh)
                        .clickable(onClickLabel = "Continue ${p.title}") { onOpen(p.route) }.padding(horizontal = 14.dp, vertical = 11.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(p.title, fontFamily = BookSerif, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(p.subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (p.total > 1) {
                        val f = ((p.index + 1).toFloat() / p.total).coerceIn(0.02f, 1f)
                        Box(Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)).background(MaterialTheme.colorScheme.outlineVariant)) {
                            Box(Modifier.fillMaxWidth(f).height(4.dp).background(MaterialTheme.colorScheme.primary))
                        }
                    }
                }
            }
        }
        item { LibLabel("The shelves") }
        items(shelves.chunked(3), key = { row -> "s-" + row.first().route }) { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { s ->
                    Column(
                        Modifier.weight(1f).height(110.dp).clip(RoundedCornerShape(12.dp)).background(Color(s.tint))
                            .clickable(onClickLabel = "Open ${s.title}") { onOpen(s.route) }.padding(10.dp),
                        verticalArrangement = Arrangement.Bottom,
                    ) {
                        Text(s.title, fontFamily = BookSerif, fontSize = 14.sp, lineHeight = 17.sp, color = Color.White, maxLines = 3, overflow = TextOverflow.Ellipsis)
                        val where = places[s.route.removePrefix("book:")]
                        Text(where?.let { "${it.index + 1} of ${it.total}" } ?: s.label, fontSize = 11.sp, color = Color.White.copy(alpha = 0.85f))
                    }
                }
                repeat(3 - row.size) { Box(Modifier.weight(1f)) }
            }
        }
        if (bookmarks.isNotEmpty()) {
            item { LibLabel("Bookmarks") }
            val all = recentTitles(context, bookmarks)
            items(all, key = { "b-" + it.first }) { (k, label, route) ->
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable(onClickLabel = "Open $label") { onOpen(route) }.padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("★", color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(end = 10.dp))
                    Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f), maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

@Composable
private fun LibLabel(text: String) {
    Text(text.uppercase(), style = MaterialTheme.typography.labelMedium, letterSpacing = 1.2.sp, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 6.dp))
}

/** Bookmarks as (key, label, route), resolved against the rooms' contents. */
private fun recentTitles(context: Context, keys: Set<String>): List<Triple<String, String, String>> = keys.mapNotNull { k ->
    val room = k.substringBefore('|'); val item = k.substringAfter('|')
    when {
        room == "plato" -> Plato.dialogues(context).flatMap { it.scenes }.firstOrNull { it.id == item }?.let { Triple(k, "${it.dialogue} · ${it.title}", "plato:$item") }
        room == "letters" -> Letters.groups(context).flatMap { it.letters }.firstOrNull { it.id == item }?.let { Triple(k, "Van Gogh · ${it.title}", "letters:$item") }
        room == "leonardo" -> Leonardo.book(context).all.firstOrNull { it.id == item }?.let { Triple(k, "Leonardo · ${it.title}", "leonardo:$item") }
        else -> Books.all(context).firstOrNull { it.id == room }?.let { b ->
            b.chapters.firstOrNull { it.id == item }?.let { Triple(k, "${b.title} · ${it.title}", "book:${b.id}/$item") }
        }
    }
}.sortedBy { it.second }

/** One book: its introduction, the guide, and the chapters; [start] ("chapterId") opens a chapter. */
@Composable
fun BookScreen(bookId: String, onBack: () -> Unit, onOpenEssay: (String) -> Unit, start: String? = null) {
    val context = LocalContext.current
    val prefs = remember { QuotePrefs(context) }
    val book = remember(bookId) { Books.all(context).firstOrNull { it.id == bookId } }
    if (book == null) { ToolScreen("Library", onBack) { item { Text("This book isn't available.") } }; return }
    var open by rememberSaveable { mutableStateOf(start) }
    var guide by rememberSaveable { mutableStateOf(false) }
    var refresh by remember { mutableIntStateOf(0) }
    book.chapters.indexOfFirst { it.id == open }.takeIf { it >= 0 }?.let { i ->
        androidx.compose.runtime.key(i) {
            BookChapterScreen(book, i, onGo = { open = book.chapters[it].id }, onBack = { if (start != null) onBack() else { open = null; refresh++ } })
        }
        return
    }
    var lines by rememberSaveable { mutableStateOf(false) }
    if (lines) {
        BookLinesScreen(book, onBack = { lines = false }, onOpenChapter = { id -> open = id; lines = false })
        return
    }
    if (guide && book.guide != null) {
        BookGuideScreen(
            book, book.guide, onBack = { guide = false },
            onOpenChapter = { t -> book.chapters.firstOrNull { it.title == t }?.let { open = it.id; guide = false } },
            onOpenEssay = onOpenEssay,
            onOpenLines = { lines = true },
        )
        return
    }
    val done = remember(refresh) { prefs.readerDone }
    val here = remember(refresh) { Reading.recent(prefs).firstOrNull { it.room == book.id }?.item }
    ToolScreen(book.title, onBack) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("${book.author} · ${book.translation}".uppercase(), style = MaterialTheme.typography.labelMedium, letterSpacing = 1.2.sp, color = MaterialTheme.colorScheme.primary)
                book.intro.forEach { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
        }
        book.guide?.let { g ->
            item {
                Column(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
                        .background(androidx.compose.ui.graphics.Brush.linearGradient(listOf(Color(book.tint).copy(alpha = 0.35f), MaterialTheme.colorScheme.surfaceContainerHigh)))
                        .border(1.dp, Color(book.tint).copy(alpha = 0.5f), RoundedCornerShape(16.dp))
                        .clickable(onClickLabel = "Read the guide") { guide = true }.padding(14.dp),
                ) {
                    Text("Reading the ${book.title}", fontFamily = BookSerif, fontSize = 17.sp)
                    Text("A guide, written for the app · ${g.minutes} min", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        item { LibLabel("Contents") }
        items(book.chapters, key = { it.id }) { c ->
            val read = Reading.bookmarkKey(book.id, c.id) in done
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.surfaceContainerHigh)
                    .clickable(onClickLabel = "Read ${c.title}") { open = c.id }.padding(horizontal = 14.dp, vertical = 10.dp),
            ) {
                Text(c.title, fontFamily = BookSerif, fontSize = 15.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(
                    when { c.id == here -> "Continue here"; read -> "✓ read"; else -> "${c.minutes} min" },
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        item {
            Text(
                "${book.title}, ${book.translation}, in the public domain. The introduction, guide and notes are written for the app.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** One chapter in the shared reader. */
@Composable
private fun BookChapterScreen(book: Book, index: Int, onGo: (Int) -> Unit, onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    val context = LocalContext.current
    val prefs = remember { QuotePrefs(context) }
    LaunchedEffect(Unit) { Reading.load(prefs) }
    val c = book.chapters[index]
    val accent = MaterialTheme.colorScheme.primary
    val inBook = roomLines(book.id)
    val here = remember(inBook, c.id) { inBook.filter { it.second.item == c.id }.map { it.first } }
    val underlined = remember(here) { c.paras.flatMap { p -> LibraryLinks.linesIn(here, p).map { it.third } }.distinct().size }
    var opened by remember { mutableStateOf<Quote?>(null) }
    opened?.let { q -> QuotePageSheet(quote = q, onDismiss = { opened = null }) }
    val key = Reading.bookmarkKey(book.id, c.id)
    var marked by remember(key) { mutableStateOf(key in prefs.readerBookmarks) }
    LaunchedEffect(key) {
        Reading.record(prefs, Reading.Place(book.id, c.id, "${book.title} · ${c.title}", book.author, "book:${book.id}/${c.id}", index, book.chapters.size, System.currentTimeMillis()))
        prefs.readerDone = prefs.readerDone + key
    }
    Scaffold(
        bottomBar = {
            ReaderBar(
                prevLabel = book.chapters.getOrNull(index - 1)?.title,
                nextLabel = book.chapters.getOrNull(index + 1)?.title,
                onPrev = { if (index > 0) onGo(index - 1) },
                onNext = { if (index < book.chapters.size - 1) onGo(index + 1) },
                listenKey = "book-$key",
                listenTitle = c.title,
                paragraphs = c.paras,
                bookmarked = marked,
                onBookmark = {
                    prefs.readerBookmarks = if (marked) prefs.readerBookmarks - key else prefs.readerBookmarks + key
                    marked = !marked
                },
            )
        },
    ) { inner ->
        PassageSelection(book.credit, book.title) {
            LazyColumn(
                Modifier.fillMaxSize().padding(inner),
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                item { BackArrow(onBack) }
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(book.title.uppercase(), style = MaterialTheme.typography.labelMedium, letterSpacing = 1.2.sp, color = MaterialTheme.colorScheme.primary)
                        Text(c.title, fontFamily = BookSerif, fontSize = 24.sp, lineHeight = 30.sp)
                    }
                }
                itemsIndexed(c.paras) { _, p ->
                    val verse = p.contains('\n')
                    val marks = remember(p, here) { LibraryLinks.linesIn(here, p) }
                    val text = remember(p, marks, accent) {
                        buildAnnotatedString {
                            append(p)
                            marks.forEach { (a, b, q) ->
                                addLink(
                                    LinkAnnotation.Clickable(
                                        tag = "line-${q.text.hashCode()}",
                                        styles = TextLinkStyles(style = SpanStyle(textDecoration = TextDecoration.Underline, color = accent)),
                                        linkInteractionListener = { opened = q },
                                    ),
                                    a, b,
                                )
                            }
                        }
                    }
                    Text(
                        text, fontFamily = BookSerif, fontSize = readerSize(17f), lineHeight = readerSize(if (verse) 25f else 27f),
                        fontStyle = if (verse) FontStyle.Italic else FontStyle.Normal,
                        modifier = if (verse) Modifier.padding(start = 14.dp) else Modifier,
                    )
                }
                if (c.note.isNotEmpty()) item {
                    Text(c.note, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                item { ChapterIdeas(book.id, c.id) { opened = it } }
                if (underlined > 0) item {
                    Text(
                        if (underlined == 1) "1 line here is in your collection · tap it to open it" else "$underlined lines here are in your collection · tap one to open it",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                item { Text("${book.author} · ${book.translation}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
        }
    }
}

/** A book's guide, laid out like the Plato guides. */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun BookGuideScreen(book: Book, g: PlatoAnalysis, onBack: () -> Unit, onOpenChapter: (String) -> Unit, onOpenEssay: (String) -> Unit, onOpenLines: () -> Unit) {
    val found = roomLines(book.id)
    val order = remember(book) { book.chapters.withIndex().associate { (i, c) -> c.id to i } }
    val ideas = remember(found) { found.filter { it.first.isIdea }.sortedBy { order[it.second.item] ?: 0 } }
    val quoted = remember(found) { found.filter { !it.first.isIdea } }
    var opened by remember { mutableStateOf<Quote?>(null) }
    opened?.let { q -> QuotePageSheet(quote = q, onDismiss = { opened = null }) }
    ToolScreen("", onBack) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                LibLabel("${book.title} · A guide")
                Text("Reading the ${book.title}", fontFamily = BookSerif, fontSize = 28.sp, lineHeight = 34.sp)
                Text("${g.minutes} min · written for the app", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        g.sections.forEach { (heading, paras) ->
            item(key = "h-$heading") { Text(heading, fontFamily = BookSerif, fontSize = 20.sp, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 8.dp)) }
            itemsIndexed(paras, key = { i, _ -> "p-$heading-$i" }) { _, p -> Text(p, fontFamily = BookSerif, fontSize = 17.sp, lineHeight = 27.sp) }
        }
        if (g.questions.isNotEmpty()) {
            item(key = "qh") { Text("Questions to take with you", fontFamily = BookSerif, fontSize = 20.sp, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 8.dp)) }
            itemsIndexed(g.questions, key = { i, _ -> "q-$i" }) { _, q ->
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("·", fontSize = 20.sp, color = MaterialTheme.colorScheme.primary)
                    Text(q, fontFamily = BookSerif, fontSize = 16.sp, lineHeight = 24.sp, modifier = Modifier.weight(1f))
                }
            }
        }
        if (ideas.isNotEmpty()) {
            item(key = "ih") { LibLabel("Great ideas in this book") }
            item(key = "ideas") {
                androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    ideas.forEach { (q, spot) ->
                        val ch = book.chapters.firstOrNull { it.id == spot.item }
                        Text(
                            q.title + (ch?.let { " · " + it.title.substringBefore(" · ") } ?: ""),
                            fontFamily = BookSerif, fontSize = 14.sp,
                            modifier = Modifier.clip(RoundedCornerShape(18.dp)).border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f), RoundedCornerShape(18.dp))
                                .clickable(onClickLabel = "Open ${q.title}") { opened = q }.padding(horizontal = 12.dp, vertical = 7.dp),
                        )
                    }
                }
            }
        }
        if (quoted.isNotEmpty()) {
            item(key = "lh") { LibLabel("From this book in the collection") }
            item(key = "lines") {
                Column(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.surfaceContainerHigh)
                        .clickable(onClickLabel = "See the lines from this book", onClick = onOpenLines).padding(14.dp),
                ) {
                    val chapters = quoted.map { it.second.item }.toSet().size
                    Text("${quoted.size} ${if (quoted.size == 1) "line" else "lines"} across $chapters ${if (chapters == 1) "chapter" else "chapters"} ›", fontFamily = BookSerif, fontSize = 16.sp)
                    Text("The book's lines that are in your collection, in order", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        item(key = "next") { LibLabel("Read next") }
        if (g.keyScene.isNotEmpty()) item(key = "ch") {
            Text(
                g.keyScene + " ›", fontFamily = BookSerif, fontSize = 16.sp,
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surfaceContainerHigh)
                    .clickable { onOpenChapter(g.keyScene) }.padding(14.dp),
            )
        }
        if (g.essay.isNotEmpty()) item(key = "es") {
            Text(
                "Essay: ${g.essay} ›", fontFamily = BookSerif, fontSize = 16.sp,
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surfaceContainerHigh)
                    .clickable { onOpenEssay(g.essay) }.padding(14.dp),
            )
        }
    }
}

/** The book's lines that are in the collection, chapter by chapter; tap a line to open it, a heading to read the chapter. */
@Composable
private fun BookLinesScreen(book: Book, onBack: () -> Unit, onOpenChapter: (String) -> Unit) {
    BackHandler(onBack = onBack)
    val found = roomLines(book.id)
    val groups = remember(found) {
        val order = book.chapters.withIndex().associate { (i, c) -> c.id to i }
        found.filter { !it.first.isIdea }.groupBy { it.second.item }.toList().sortedBy { order[it.first] ?: 0 }
    }
    var opened by remember { mutableStateOf<Quote?>(null) }
    opened?.let { q -> QuotePageSheet(quote = q, onDismiss = { opened = null }) }
    ToolScreen(book.title, onBack) {
        item { Text("Lines from ${book.title} that are in your collection. Tap one to open it, or a chapter to read it whole.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        groups.forEach { (chapterId, list) ->
            val ch = book.chapters.firstOrNull { it.id == chapterId }
            item(key = "c-$chapterId") {
                Text(
                    (ch?.title ?: chapterId) + " ›", fontFamily = BookSerif, fontSize = 15.sp, color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 8.dp).clickable(onClickLabel = "Read this chapter") { onOpenChapter(chapterId) },
                )
            }
            items(list, key = { "q-" + it.first.text.hashCode() }) { (q, _) ->
                Text(
                    "“${q.text}”", fontFamily = BookSerif, fontSize = 16.sp, lineHeight = 24.sp,
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surfaceContainerHigh)
                        .clickable(onClickLabel = "Open this line") { opened = q }.padding(12.dp),
                )
            }
        }
    }
}

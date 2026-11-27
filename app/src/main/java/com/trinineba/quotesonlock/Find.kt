package com.trinineba.quotesonlock

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/** One search result, of any kind the app holds. */
sealed class Hit {
    class EssayHit(val essay: Essay, val snippet: String?) : Hit()
    class LetterHit(val letter: Letter, val snippet: String?) : Hit()
    class LeoHit(val item: LeoHighlight, val snippet: String?) : Hit()
    class PlatoHit(val scene: PlatoScene, val snippet: String?) : Hit()
    /** A great idea, myth or folktale. */
    class ItemHit(val quote: Quote) : Hit()
    class ThingHit(val thing: Thing, val section: String) : Hit()
    class PathHit(val path: ReadingPath) : Hit()
    class QuoteHit(val quote: Quote, val viaSubject: Subject?) : Hit()
    class AuthorHit(val name: String, val count: Int) : Hit()
    class SubjectHit(val subject: Subject, val count: Int) : Hit()
    class FeelingHit(val feeling: Feeling) : Hit()
}

class FindResults(
    val essays: List<Hit.EssayHit> = emptyList(),
    val letters: List<Hit.LetterHit> = emptyList(),
    val leonardo: List<Hit.LeoHit> = emptyList(),
    val plato: List<Hit.PlatoHit> = emptyList(),
    val items: List<Hit.ItemHit> = emptyList(),
    val things: List<Hit.ThingHit> = emptyList(),
    val paths: List<Hit.PathHit> = emptyList(),
    val quotes: List<Hit.QuoteHit> = emptyList(),
    val authors: List<Hit.AuthorHit> = emptyList(),
    val subjects: List<Hit.SubjectHit> = emptyList(),
    val feelings: List<Hit.FeelingHit> = emptyList(),
    /** Quotes close in meaning to the best matches (from the same index as "More like this"). */
    val related: List<Quote> = emptyList(),
    /** The query as it was read, when a misspelt word was corrected ("nietzsche" for "neitzsche"). */
    val corrected: String? = null,
) {
    val total: Int get() = essays.size + letters.size + leonardo.size + plato.size + items.size + things.size + paths.size + quotes.size + authors.size + subjects.size + feelings.size
}

/** Search across everything: quotes, essays, letters, ideas, myths and tales, how things work, reading paths, authors and subjects. */
object Find {
    /** Everyday words that should find a subject whose label doesn't use them. */
    private val synonyms = mapOf(
        "grief" to Subject.SUFFERING, "sorrow" to Subject.SUFFERING, "sadness" to Subject.SUFFERING, "loss" to Subject.SUFFERING, "pain" to Subject.SUFFERING,
        "dying" to Subject.DEATH, "mortality" to Subject.DEATH, "god" to Subject.SACRED, "faith" to Subject.SACRED, "prayer" to Subject.SACRED,
        "happiness" to Subject.JOY, "kindness" to Subject.FRIENDSHIP, "home" to Subject.JOURNEY, "travel" to Subject.JOURNEY,
        "age" to Subject.YOUTH, "old" to Subject.YOUTH, "aging" to Subject.YOUTH, "ageing" to Subject.YOUTH, "imagination" to Subject.DREAMS,
        "silence" to Subject.SOLITUDE, "alone" to Subject.SOLITUDE, "loneliness" to Subject.SOLITUDE, "beauty" to Subject.ART,
        "reading" to Subject.BOOKS, "writing" to Subject.BOOKS, "past" to Subject.MEMORY, "war" to Subject.SOCIETY, "power" to Subject.SOCIETY,
        "craft" to Subject.WORK, "humor" to Subject.HUMOUR, "funny" to Subject.HUMOUR, "jokes" to Subject.HUMOUR, "wit" to Subject.HUMOUR, "laughter" to Subject.HUMOUR, "discipline" to Subject.WORK, "change" to Subject.TIME, "absurd" to Subject.MEANING,
    )

    fun subjectFor(query: String): Subject? {
        val q = query.trim().lowercase()
        if (q.length < 3) return null
        synonyms[q]?.let { return it }
        return Subject.entries.firstOrNull { s -> q == s.key || s.label.lowercase().split(' ', ',', '&').any { it == q } }
    }

    /** About [width] characters around the first place [q] appears in [text], cut at word boundaries. */
    fun snippet(text: String, q: String, width: Int = 110): String? {
        val i = text.indexOf(q, ignoreCase = true)
        if (i < 0) return null
        var a = (i - width / 2).coerceAtLeast(0)
        var b = (i + q.length + width / 2).coerceAtMost(text.length)
        if (a > 0) a = text.indexOf(' ', a).let { if (it in a until i) it + 1 else a }
        if (b < text.length) b = text.lastIndexOf(' ', b).let { if (it > i + q.length) it else b }
        return (if (a > 0) "…" else "") + text.substring(a, b).trim() + if (b < text.length) "…" else ""
    }

    // The words the app knows, for correcting typos, and each quote's text ready to match; built once.
    @Volatile private var vocab: Smart.Vocabulary? = null
    @Volatile private var prepared: List<String>? = null
    @Volatile private var preparedFor: List<Quote>? = null

    private fun prepare(bundled: List<Quote>) {
        if (preparedFor === bundled && vocab != null && prepared != null) return
        vocab = Smart.Vocabulary(bundled.asSequence().flatMap { sequenceOf(it.text, it.author.orEmpty(), it.title.orEmpty()) })
        prepared = bundled.map { Smart.normalise(it.text + " — " + it.author.orEmpty() + " " + it.title.orEmpty() + " " + it.brief.orEmpty()) }
        preparedFor = bundled
    }

    /** The feeling a query word points to, if any. */
    private fun feelingFor(word: String): Feeling? {
        val w = Smart.stem(word)
        return when {
            listOf("anxi", "worr", "fear", "afraid", "panic", "nerv").any { w.startsWith(it) } -> Feeling.ANXIOUS
            listOf("sad", "sorrow", "depress", "unhapp", "down", "blue").any { w.startsWith(it) } -> Feeling.SAD
            listOf("grie", "mourn", "loss", "bereave").any { w.startsWith(it) } -> Feeling.GRIEVING
            listOf("lone", "alone", "isolat").any { w.startsWith(it) } -> Feeling.LONELY
            listOf("overwhelm", "stress", "burnout", "busy").any { w.startsWith(it) } -> Feeling.OVERWHELMED
            listOf("anger", "angr", "rage", "furious", "mad").any { w.startsWith(it) } -> Feeling.ANGRY
            listOf("numb", "empty", "hollow").any { w.startsWith(it) } -> Feeling.NUMB
            listOf("sleep", "insomnia", "awake", "restless").any { w.startsWith(it) } -> Feeling.SLEEP
            else -> null
        }
    }

    fun search(context: Context, bundled: List<Quote>, query: String): FindResults {
        if (query.trim().length < 2) return FindResults()
        prepare(bundled)
        val sq = Smart.parse(query, vocab)
        if (sq.isEmpty) return FindResults()
        fun sc(vararg parts: String?) = Smart.score(parts.filterNotNull().joinToString(" · "), sq)
        /** The best-matching paragraph of [paras], with its score. */
        fun bestOf(paras: List<String>): Pair<String, Float>? = paras.map { it to Smart.score(it, sq) }.filter { it.second > 0f }.maxByOrNull { it.second }

        val essays = runCatching { Essays.all(context) }.getOrDefault(emptyList()).mapNotNull { e ->
            val head = sc(e.title, e.voices) * 1.5f
            val para = bestOf(e.parts.mapNotNull { (it as? EssayPart.Para)?.text })
            val line = e.parts.maxOfOrNull { p -> (p as? EssayPart.Line)?.text?.let { Smart.score(it, sq) } ?: (p as? EssayPart.Idea)?.title?.let { Smart.score(it, sq) } ?: 0f } ?: 0f
            val best = maxOf(head, para?.second ?: 0f, line * 0.8f)
            if (best <= 0f) null else best to Hit.EssayHit(
                e,
                when {
                    head > 0f -> e.voices
                    para != null -> Smart.snippet(para.first, sq) ?: e.voices
                    else -> "Quotes a line about " + sq.terms.joinToString(" and ") { it.word }
                },
            )
        }.sortedByDescending { it.first }.map { it.second }
        val letters = runCatching { Letters.groups(context) }.getOrDefault(emptyList()).flatMap { it.letters }.mapNotNull { l ->
            val head = sc(l.title, l.note) * 1.5f
            val para = bestOf(l.paras)
            val best = maxOf(head, para?.second ?: 0f)
            if (best <= 0f) null else best to Hit.LetterHit(l, if (head > 0f) l.note else para?.first?.let { Smart.snippet(it, sq) })
        }.sortedByDescending { it.first }.map { it.second }
        val leonardo = runCatching { Leonardo.book(context).all }.getOrDefault(emptyList()).mapNotNull { h ->
            val head = sc(h.title, h.note) * 1.5f
            val para = bestOf(h.paras)
            val best = maxOf(head, para?.second ?: 0f)
            if (best <= 0f) null else best to Hit.LeoHit(h, if (head > 0f) h.paras.firstOrNull { !Leonardo.isHeading(it) } else para?.first?.let { Smart.snippet(it, sq) })
        }.sortedByDescending { it.first }.map { it.second }
        val plato = runCatching { Plato.dialogues(context).flatMap { it.scenes } }.getOrDefault(emptyList()).mapNotNull { sc0 ->
            val head = sc(sc0.title, sc0.note, sc0.dialogue) * 1.5f
            val para = bestOf(sc0.paras)
            val best = maxOf(head, para?.second ?: 0f)
            if (best <= 0f) null else best to Hit.PlatoHit(sc0, if (head > 0f) sc0.note else para?.first?.let { Smart.snippet(it, sq) })
        }.sortedByDescending { it.first }.map { it.second }
        val norm = prepared!!
        // Each quote's score, once: great ideas and quotes are both drawn from it.
        val scores = FloatArray(bundled.size) { i -> Smart.scoreNormalised(norm[i], sq) }
        val items = bundled.indices.filter { bundled[it].isIdea && scores[it] > 0f }
            .sortedByDescending { scores[it] + if (Smart.score(bundled[it].title, sq) > 0f) 5f else 0f }
            .map { Hit.ItemHit(bundled[it]) }
        val things = runCatching { Things.sections(context) }.getOrDefault(emptyList()).flatMap { s ->
            s.things.map { it to sc(it.label, it.ref) }.filter { it.second > 0f }.sortedByDescending { it.second }.map { Hit.ThingHit(it.first, s.title) }
        }
        val paths = runCatching { ReadingPaths.all(context) }.getOrDefault(emptyList())
            .map { p -> p to maxOf(sc(p.title, p.subtitle) * 1.5f, p.steps.maxOfOrNull { sc(it.label, it.note) } ?: 0f) }
            .filter { it.second > 0f }.sortedByDescending { it.second }.map { Hit.PathHit(it.first) }
        val subject = sq.terms.firstNotNullOfOrNull { subjectFor(it.word) } ?: subjectFor(sq.phrase)
        val direct = bundled.indices.filter { !bundled[it].isIdea && scores[it] > 0f }
            .sortedByDescending { i ->
                val q = bundled[i]
                scores[i] + (if (subject != null && subject.key in q.subjects) 1.5f else 0f) +
                    (if (Smart.score(q.author, sq) > 0f) 2f else 0f) + (if (q.text.length < 220) 0.3f else 0f)
            }
        val directSet = direct.toHashSet()
        val bySubject = if (subject == null) emptyList() else bundled.indices.filter { !bundled[it].isIdea && it !in directSet && subject.key in bundled[it].subjects }
        val quotes = direct.map { Hit.QuoteHit(bundled[it], null) } + bySubject.map { Hit.QuoteHit(bundled[it], subject) }
        val authors = bundled.mapNotNull { authorName(it.author) }.groupingBy { it }.eachCount()
            .filter { Smart.direct(it.key, sq) }
            .map { Hit.AuthorHit(it.key, it.value) }.sortedByDescending { it.count }
        val subjects = Subject.entries.filter { Smart.direct(it.label, sq) || it == subject }
            .map { s -> Hit.SubjectHit(s, bundled.count { s.key in it.subjects }) }
        val feelings = (Feeling.entries.filter { Smart.direct(it.label, sq) } + sq.terms.mapNotNull { feelingFor(it.word) })
            .distinct().map { Hit.FeelingHit(it) }
        // Close in meaning: what the best few matches sit near, that the words alone didn't find.
        val shownKeys = quotes.take(60).map { it.quote.text }.toHashSet()
        val related = runCatching {
            quotes.take(4).flatMap { h -> MoreLikeThis.of(context, h.quote) }
                .filter { it.text !in shownKeys && !it.isIdea }.distinctBy { it.text }.take(8)
        }.getOrDefault(emptyList())
        return FindResults(essays, letters, leonardo, plato, items, things, paths, quotes, authors, subjects, feelings, related, sq.corrected)
    }
}

private enum class FindKind(val label: String) { ALL("All"), QUOTES("Quotes"), ESSAYS("Essays"), LETTERS("Letters"), LEONARDO("Leonardo"), PLATO("Plato"), IDEAS("Ideas, myths & tales"), THINGS("How things work"), PATHS("Paths"), AUTHORS("Authors") }

private val Gold = Color(0xFFD4A72C)

/**
 * Search everything. On a phone a result opens its own screen (or a quote's page); on a wide screen essays and
 * letters open beside the results.
 */
@Composable
fun FindScreen(
    onBack: () -> Unit,
    onOpenEssay: (String) -> Unit,
    onOpenLetter: (String) -> Unit,
    onOpenThing: (String) -> Unit,
    onOpenPath: (String) -> Unit,
    onOpenAuthor: (String) -> Unit,
    onOpenPage: (String) -> Unit,
    onShow: (Quote) -> Unit,
) {
    val context = LocalContext.current
    val prefs = remember { QuotePrefs(context) }
    val bundled = remember { QuoteRepository(context).bundled() }
    var query by rememberSaveable { mutableStateOf("") }
    var kind by rememberSaveable { mutableStateOf(FindKind.ALL) }
    var results by remember { mutableStateOf(FindResults()) }
    var searchedFor by remember { mutableStateOf("") }
    var opened by remember { mutableStateOf<Quote?>(null) }
    // On a wide screen: the essay title or letter id open in the right-hand pane.
    var paneEssay by rememberSaveable { mutableStateOf<String?>(null) }
    var paneLetter by rememberSaveable { mutableStateOf<String?>(null) }
    val focus = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    BackHandler(onBack = onBack)
    LaunchedEffect(Unit) { if (query.isEmpty()) runCatching { focus.requestFocus() } }
    LaunchedEffect(query) {
        delay(250)
        val q = query
        results = withContext(Dispatchers.Default) { Find.search(context, bundled, q) }
        searchedFor = q.trim()
    }
    opened?.let { q -> QuotePageSheet(quote = q, onDismiss = { opened = null }, onShowOnLock = onShow, onOpenAuthor = { opened = null; onOpenAuthor(it) }) }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val wide = maxWidth >= 840.dp
        val openEssay: (String) -> Unit = { t -> if (wide) { paneLetter = null; paneEssay = t } else onOpenEssay(t) }
        val openLetter: (String) -> Unit = { id -> if (wide) { paneEssay = null; paneLetter = id } else onOpenLetter(id) }
        Row(Modifier.fillMaxSize()) {
            Scaffold(modifier = if (wide) Modifier.width(420.dp).fillMaxHeight() else Modifier.fillMaxSize()) { inner ->
                LazyColumn(
                    Modifier.fillMaxSize().padding(inner),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    item(key = "f-box") {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            BackArrow(onBack)
                            OutlinedTextField(
                                value = query,
                                onValueChange = { query = it; kind = FindKind.ALL },
                                singleLine = true,
                                placeholder = { Text("Quotes, essays, letters, ideas…") },
                                leadingIcon = { Icon(painterResource(R.drawable.ic_search), contentDescription = null) },
                                trailingIcon = {
                                    if (query.isNotEmpty()) TextButton(onClick = { query = "" }) { Text("Clear") }
                                },
                                shape = RoundedCornerShape(28.dp),
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                                keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() }),
                                modifier = Modifier.weight(1f).padding(start = 8.dp).focusRequester(focus),
                            )
                        }
                    }
                    if (searchedFor.length < 2) {
                        findSuggestions(onPick = { query = it }, onOpenPage = onOpenPage)
                    } else {
                        findResults(
                            results, searchedFor, kind, onKind = { kind = it },
                            selected = paneEssay ?: paneLetter,
                            onEssay = openEssay, onLetter = openLetter, onQuote = { opened = it }, onThing = onOpenThing,
                            onPath = onOpenPath, onAuthor = onOpenAuthor, onOpenPage = onOpenPage,
                        )
                    }
                }
            }
            if (wide) {
                Box(Modifier.weight(1f).fillMaxHeight().background(MaterialTheme.colorScheme.surfaceContainerLow)) {
                    val essay = paneEssay?.let { t -> remember(t) { Essays.all(context).firstOrNull { it.title == t } } }
                    val letters = remember { Letters.groups(context).flatMap { it.letters } }
                    val li = paneLetter?.let { id -> letters.indexOfFirst { it.id == id } }?.takeIf { it >= 0 }
                    when {
                        essay != null -> androidx.compose.runtime.key(essay.title) {
                            EssayScreen(essay, onBack = { paneEssay = null }, onOpenAuthor = onOpenAuthor, onOpenPath = { onOpenPage(if (it == "letters" || it == "leonardo" || it == "plato") it else "paths:$it") })
                        }
                        li != null -> androidx.compose.runtime.key(li) {
                            LetterScreen(letters, li, prefs, onGo = { paneLetter = letters[it].id }, onBack = { paneLetter = null }, onOpenAuthor = onOpenAuthor)
                        }
                        else -> Text(
                            "Essays and letters you tap open here, beside your results.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.align(Alignment.Center).padding(32.dp),
                        )
                    }
                }
            }
        }
    }
}

/** Before typing: a few things to try, and the ways in by subject, author and feeling. */
private fun LazyListScope.findSuggestions(onPick: (String) -> Unit, onOpenPage: (String) -> Unit) {
    item(key = "f-try") { FindLabel("TRY") }
    item(key = "f-try-chips") {
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf("grief", "the sea", "hope", "stars", "Seneca", "Carnival", "friendship", "how a plane flies").forEach { s ->
                FindChip(s, false) { onPick(s) }
            }
        }
    }
    item(key = "f-browse") { FindLabel("OR BROWSE") }
    item(key = "f-browse-row") {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            FindRow("Subjects and authors A–Z", "Love, time, nature… and every voice in the app") { onOpenPage(BrowsePage.SEARCH) }
            FindRow("Gentle words for a feeling", "Anxious, sad, grieving, lonely, overwhelmed…") { onOpenPage(BrowsePage.feeling(Feeling.ANXIOUS)) }
        }
    }
}

private fun LazyListScope.findResults(
    r: FindResults,
    typed: String,
    kind: FindKind,
    onKind: (FindKind) -> Unit,
    selected: String?,
    onEssay: (String) -> Unit,
    onLetter: (String) -> Unit,
    onQuote: (Quote) -> Unit,
    onThing: (String) -> Unit,
    onPath: (String) -> Unit,
    onAuthor: (String) -> Unit,
    onOpenPage: (String) -> Unit,
) {
    val counts = listOf(
        FindKind.ALL to r.total, FindKind.QUOTES to r.quotes.size, FindKind.ESSAYS to r.essays.size, FindKind.LETTERS to r.letters.size, FindKind.LEONARDO to r.leonardo.size, FindKind.PLATO to r.plato.size,
        FindKind.IDEAS to r.items.size, FindKind.THINGS to r.things.size, FindKind.PATHS to r.paths.size, FindKind.AUTHORS to r.authors.size,
    ).filter { it.second > 0 || it.first == FindKind.ALL }
    // Highlight what the search actually looked for (the corrected spelling, if any).
    val q = r.corrected ?: typed
    r.corrected?.let { c ->
        item(key = "f-corrected") {
            Text(
                "Showing results for “$c”",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
    item(key = "f-chips") {
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            counts.forEach { (k, n) -> FindChip("${k.label} ${"%,d".format(n)}", k == kind) { onKind(k) } }
        }
    }
    if (r.total == 0) {
        item(key = "f-none") {
            Text(
                "Nothing found for “$typed”. Try fewer words, an author's surname, or a subject such as love or time.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        return
    }
    val all = kind == FindKind.ALL
    // In All, each kind shows its first few, with a link to see the rest.
    fun <T> cap(list: List<T>, n: Int) = if (all) list.take(n) else list
    fun section(k: FindKind, label: String, size: Int, n: Int) {
        item(key = "f-h-$label") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                FindLabel(label.uppercase(), Modifier.weight(1f))
                if (all && size > n) Text(
                    "See all ${"%,d".format(size)} ›",
                    style = MaterialTheme.typography.labelMedium,
                    color = Gold,
                    modifier = Modifier.clickable { onKind(k) }.padding(4.dp),
                )
            }
        }
    }
    if ((all || kind == FindKind.ESSAYS) && r.essays.isNotEmpty()) {
        section(FindKind.ESSAYS, "Essays", r.essays.size, 3)
        items(cap(r.essays, 3), key = { "e-" + it.essay.title }) { h ->
            FindCard(h.essay.title, "Essay · ${h.essay.minutes} min", h.snippet, q, selected == h.essay.title) { onEssay(h.essay.title) }
        }
    }
    if (all) {
        val extra = r.subjects.map { s -> Triple("Subject: ${s.subject.label}", "${"%,d".format(s.count)} quotes") { onOpenPage(BrowsePage.subject(s.subject)) } } +
            r.feelings.map { f -> Triple("When it's hard: ${f.feeling.label.lowercase()}", "Gentle words, hand-picked") { onOpenPage(BrowsePage.feeling(f.feeling)) } }
        if (extra.isNotEmpty()) {
            item(key = "f-h-also") { FindLabel("ALSO") }
            items(extra, key = { "x-" + it.first }) { (t, s, go) -> FindRow(t, s, go) }
        }
    }
    if ((all || kind == FindKind.QUOTES) && r.quotes.isNotEmpty()) {
        section(FindKind.QUOTES, "Quotes", r.quotes.size, 4)
        items(cap(r.quotes, 4).let { if (all) it else it.take(300) }) { h ->
            FindQuote(h.quote, h.viaSubject, q) { onQuote(h.quote) }
        }
        if (all && r.related.isNotEmpty()) {
            item(key = "f-h-related") { FindLabel("CLOSE IN MEANING") }
            items(r.related.take(6), key = { "rel-" + it.text.hashCode() }) { rq -> FindQuote(rq, null, "") { onQuote(rq) } }
        }
        if (!all && r.quotes.size > 300) item(key = "f-q-more") {
            FindRow("Show all ${"%,d".format(r.quotes.size)} in the quote browser", "With favourites, copying and the lock screen") { onOpenPage(BrowsePage.SEARCH + q) }
        }
    }
    if ((all || kind == FindKind.LETTERS) && r.letters.isNotEmpty()) {
        section(FindKind.LETTERS, "Van Gogh's letters", r.letters.size, 3)
        items(cap(r.letters, 3), key = { "l-" + it.letter.id }) { h ->
            FindCard(h.letter.title, h.letter.heading + " · ${h.letter.minutes} min", h.snippet, q, selected == h.letter.id) { onLetter(h.letter.id) }
        }
    }
    if ((all || kind == FindKind.LEONARDO) && r.leonardo.isNotEmpty()) {
        section(FindKind.LEONARDO, "Leonardo's notebooks", r.leonardo.size, 3)
        items(cap(r.leonardo, 3), key = { "v-" + it.item.id }) { h ->
            FindCard(h.item.title, "${h.item.theme} · No. ${h.item.number}", h.snippet, q, false) { onOpenPage("leonardo:" + h.item.id) }
        }
    }
    if ((all || kind == FindKind.PLATO) && r.plato.isNotEmpty()) {
        section(FindKind.PLATO, "Plato's dialogues", r.plato.size, 3)
        items(cap(r.plato, 3), key = { "pl-" + it.scene.id }) { h ->
            FindCard(h.scene.title, "${h.scene.dialogue} · ${h.scene.minutes} min", h.snippet, q, false) { onOpenPage("plato:" + h.scene.id) }
        }
    }
    if ((all || kind == FindKind.IDEAS) && r.items.isNotEmpty()) {
        section(FindKind.IDEAS, "Ideas, myths & tales", r.items.size, 3)
        items(cap(r.items, 3)) { h ->
            FindCard(h.quote.title ?: "", h.quote.category?.label ?: "", h.quote.brief ?: h.quote.text.take(140), q, false) { onQuote(h.quote) }
        }
    }
    if ((all || kind == FindKind.THINGS) && r.things.isNotEmpty()) {
        section(FindKind.THINGS, "How things work", r.things.size, 3)
        items(cap(r.things, 3), key = { "t-" + it.thing.ref }) { h ->
            FindCard(h.thing.emoji + "  " + h.thing.label, h.section, null, q, false) { onThing(h.thing.ref) }
        }
    }
    if ((all || kind == FindKind.PATHS) && r.paths.isNotEmpty()) {
        section(FindKind.PATHS, "Reading paths", r.paths.size, 2)
        items(cap(r.paths, 2), key = { "p-" + it.path.title }) { h ->
            FindCard(h.path.title, "Reading path · ${h.path.steps.size} steps", h.path.subtitle, q, false) { onPath(h.path.title) }
        }
    }
    if ((all || kind == FindKind.AUTHORS) && r.authors.isNotEmpty()) {
        section(FindKind.AUTHORS, "Authors", r.authors.size, 4)
        items(cap(r.authors, 4)) { h ->
            FindRow(h.name, "${"%,d".format(h.count)} quote" + if (h.count == 1) "" else "s") { onAuthor(h.name) }
        }
    }
}

@Composable
private fun FindLabel(text: String, modifier: Modifier = Modifier) {
    Text(text, style = MaterialTheme.typography.labelMedium, color = Gold, letterSpacing = 1.2.sp, modifier = modifier.padding(top = 6.dp))
}

@Composable
private fun FindChip(text: String, on: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.clip(RoundedCornerShape(16.dp))
            .then(if (on) Modifier.background(Gold) else Modifier.border(1.dp, Gold.copy(alpha = 0.5f), RoundedCornerShape(16.dp)))
            .clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 6.dp),
    ) { Text(text, style = MaterialTheme.typography.labelLarge, color = if (on) Color(0xFF14204A) else Gold) }
}

@Composable
private fun FindRow(title: String, subtitle: String, onClick: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 10.dp),
    ) {
        Text("$title ›", style = MaterialTheme.typography.bodyLarge)
        Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** The text with every place the query's words appear in gold. */
private fun marked(text: String, q: String) = androidx.compose.ui.text.buildAnnotatedString {
    append(text)
    if (q.isNotBlank()) {
        val lower = text.lowercase()
        val taken = BooleanArray(text.length)
        for (h in Smart.parse(q).highlights) {
            var i = lower.indexOf(h)
            while (i >= 0) {
                // only where it counts as the word, carried to the word's end ("forgiv" lights all of "forgiveness")
                val end = Smart.wordEnd(lower, i, h)
                if (end >= 0 && (i until i + h.length).none { taken[it] }) {
                    addStyle(androidx.compose.ui.text.SpanStyle(color = Gold), i, end)
                    for (k in i until end) taken[k] = true
                }
                i = lower.indexOf(h, i + h.length)
            }
        }
    }
}

@Composable
private fun FindCard(title: String, meta: String, snippet: String?, q: String, selected: Boolean, onClick: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .then(if (selected) Modifier.border(2.dp, Gold, RoundedCornerShape(14.dp)) else Modifier)
            .clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(marked(title, q), fontFamily = BookSerif, fontSize = 16.sp)
        if (meta.isNotEmpty()) Text(meta, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        snippet?.let {
            Text(marked(it, q), fontFamily = BookSerif, fontStyle = FontStyle.Italic, fontSize = 14.sp, lineHeight = 20.sp, maxLines = 3, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun FindQuote(quote: Quote, via: Subject?, q: String, onClick: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        val text = quote.shownText.replace("\\n", " ").replace('\n', ' ')
        val shown = if (text.length > 260) (Smart.snippet(text, Smart.parse(q), 240) ?: text.take(240) + "…") else text
        Text(marked(shown, q), fontFamily = BookSerif, fontStyle = FontStyle.Italic, fontSize = 15.sp, lineHeight = 21.sp)
        Text(
            (quote.byline ?: "") + (via?.let { " · matches the subject ${it.label.lowercase()}" } ?: ""),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

// ---------- Start here ----------

/** Where to begin: a few moods, each pointing somewhere fitting; shown once after the welcome, and from Discover. */
@Composable
fun StartHereScreen(
    onBack: () -> Unit,
    onGo: (String) -> Unit,
    onCalm: () -> Unit,
    onOpenAuthor: (String) -> Unit,
) {
    var view by rememberSaveable { mutableStateOf("") }
    when (view) {
        "read" -> { SomethingToRead(onBack = { view = "" }, onGo = onGo, onOpenAuthor = onOpenAuthor); return }
        "tour" -> { AppTour(onBack = { view = "" }); return }
    }
    ToolScreen("Start here", onBack) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Where would you like to begin?", fontFamily = BookSerif, fontSize = 24.sp, lineHeight = 30.sp)
                Text(
                    "Pick whatever fits right now. You can come back any time from Discover.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        item { MoodCard("☕", "I have two minutes", "One small idea to keep, and a question to check yourself", Color(0xFF2B4C8C)) { onGo("learn") } }
        item { MoodCard("📖", "I want something to read", "An essay, a letter, a story: five to twenty minutes", Color(0xFF5A6B8C)) { view = "read" } }
        item { MoodCard("🧭", "Guide me through a subject", "The Stoics, existentialism, the universe, myths of love…", Color(0xFF2E8B74)) { onGo("paths") } }
        item { MoodCard("💡", "I'm curious how things work", "Rainbows, GPS, the fridge, your body", Color(0xFF8A6A3A)) { onGo("things") } }
        item { MoodCard("🌧", "I'm going through something", "Gentle words, a breathing space, voices for hard times", Color(0xFF7A4A6A)) { onCalm() } }
        item { FindLabel("OR JUST SEE WHAT'S HERE") }
        item {
            Text(
                "Today · Discover · Yours · Solace: a one-minute look at the four places along the bottom ›",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.clickable { view = "tour" }.padding(vertical = 4.dp),
            )
        }
    }
}

@Composable
private fun MoodCard(icon: String, title: String, subtitle: String, tint: Color, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(tint.copy(alpha = 0.85f))
            .clickable(onClickLabel = title, onClick = onClick).padding(horizontal = 14.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(icon, fontSize = 24.sp, modifier = Modifier.width(36.dp))
        Column(Modifier.padding(start = 6.dp)) {
            Text(title, fontFamily = BookSerif, fontSize = 18.sp, color = Color.White)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.88f))
        }
    }
}

/** A short list chosen for today: an essay, a letter, a story and a path, with "Show me others". */
@Composable
private fun SomethingToRead(onBack: () -> Unit, onGo: (String) -> Unit, onOpenAuthor: (String) -> Unit) {
    val context = LocalContext.current
    val prefs = remember { QuotePrefs(context) }
    val bundled = remember { QuoteRepository(context).bundled() }
    val essays = remember { Essays.all(context) }
    val letters = remember { Letters.groups(context).flatMap { it.letters }.filter { it.words in 150..2600 } }
    val tales = remember(bundled) { bundled.filter { it.category?.group == QuoteGroup.FOLKLORE } }
    val paths = remember { ReadingPaths.all(context) }
    var shift by rememberSaveable { mutableIntStateOf(0) }
    var opened by remember { mutableStateOf<Quote?>(null) }
    opened?.let { q -> QuotePageSheet(quote = q, onDismiss = { opened = null }, onOpenAuthor = { opened = null; onOpenAuthor(it) }) }
    val day = (Learn.today() + shift).toInt().coerceAtLeast(0)
    val read = remember { prefs.lettersRead }
    fun <T> pick(list: List<T>, salt: Int): T? = list.takeIf { it.isNotEmpty() }?.let { it[(day * 7 + salt).mod(it.size)] }
    val essay = pick(essays, 0)
    val letter = pick(letters.filter { it.id !in read }.ifEmpty { letters }, 3)
    val tale = pick(tales, 5)
    val path = pick(paths, 2)
    ToolScreen("Something to read", onBack) {
        item {
            Text(
                "A few to start with, chosen for today. Everything else is in Discover.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        essay?.let { e ->
            item { FindLabel("AN ESSAY · ${e.minutes} MIN") }
            item { FindCard(e.title, "", e.voices, "", false) { onGo("essays:" + e.title) } }
        }
        letter?.let { l ->
            item { FindLabel("A LETTER · ${l.minutes} MIN") }
            item { FindCard(l.title, "Van Gogh to " + l.heading.removePrefix("To ").substringBefore(" ·"), l.note, "", false) { onGo("letters:" + l.id) } }
        }
        tale?.let { t ->
            item { FindLabel("A STORY") }
            item { FindCard(t.title ?: "", t.category?.label ?: "", t.brief, "", false) { opened = t } }
        }
        path?.let { p ->
            item { FindLabel("A PATH · ${p.steps.size} STEPS") }
            item { FindCard(p.title, "", p.subtitle, "", false) { onGo("paths:" + p.title) } }
        }
        item {
            Text(
                "Show me others ›",
                style = MaterialTheme.typography.labelLarge,
                color = Gold,
                modifier = Modifier.clickable { shift++ }.padding(vertical = 8.dp),
            )
        }
    }
}

/** The four places along the bottom, in a minute. */
@Composable
private fun AppTour(onBack: () -> Unit) {
    ToolScreen("A quick look around", onBack) {
        listOf(
            Triple("Today", "Your quote for now", "Swipe for the next one; tap it to open its page with its context, more like it and your note. Save, share or put it on the lock screen."),
            Triple("Discover", "Everything to explore", "Search everything at the top. Then Learn, reading paths, essays, Van Gogh's letters, how things work, great ideas, myths and folklore."),
            Triple("Yours", "What you keep", "Favourites and your collections, Notes & journal (with a reading pane beside your writing), and quotes of your own."),
            Triple("Solace", "For when it's too much", "A check-in, breathing, guided sessions read aloud, calming sounds, and gentle words for a feeling."),
        ).forEach { (name, line, text) ->
            item {
                Column(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.surfaceContainerHigh).padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(3.dp),
                ) {
                    Text(name, fontFamily = BookSerif, fontSize = 19.sp)
                    Text(line, style = MaterialTheme.typography.labelLarge, color = Gold)
                    Text(text, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
        item { Spacer(Modifier.height(4.dp)) }
        item { TextButton(onClick = onBack) { Text("Got it") } }
    }
}

/** A compact journey tile for Discover's grid. */
@Composable
internal fun JourneyTile(title: String, subtitle: String, tint: Color, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Column(
        modifier.height(84.dp).clip(RoundedCornerShape(16.dp))
            .background(androidx.compose.ui.graphics.Brush.linearGradient(listOf(tint.copy(alpha = 0.55f), MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.85f))))
            .clickable(onClickLabel = "Open $title", onClick = onClick).padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

/** Discover's "Start here" card. */
@Composable
internal fun StartHereCard(onClick: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp))
            .background(androidx.compose.ui.graphics.Brush.linearGradient(listOf(Gold.copy(alpha = 0.22f), Color(0xFF2B4C8C).copy(alpha = 0.5f))))
            .border(1.dp, Gold.copy(alpha = 0.4f), RoundedCornerShape(18.dp))
            .clickable(onClickLabel = "Start here", onClick = onClick).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Text("Start here ›", fontFamily = BookSerif, fontSize = 18.sp)
        Text(
            "New to the app, or not sure where to go? Say what you're in the mood for and we'll point the way.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
internal fun ResumeRow(title: String, meta: String, onClick: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .clickable(onClickLabel = "Continue $title", onClick = onClick).padding(horizontal = 14.dp, vertical = 10.dp),
    ) {
        Text(title, fontFamily = BookSerif, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(meta, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}


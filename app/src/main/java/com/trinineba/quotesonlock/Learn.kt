package com.trinineba.quotesonlock

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.json.JSONObject
import kotlin.random.Random

/** A two-minute lesson on one idea or myth: a takeaway and one question to check it (res/raw/lessons.txt). */
class Lesson(val ref: String, val takeaway: String, val question: String, val answer: String, val wrong: List<String>, val why: String) {
    /** The three choices in a fixed order for this lesson, so the right one isn't always in the same place. */
    val choices: List<String> get() = (wrong + answer).shuffled(Random(ref.hashCode()))
}

/** A topic to start from: a short introduction and a sequence of ideas, myths and quotes (res/raw/topics.txt). */
class TopicStep(val isIdea: Boolean, val ref: String)
class Topic(val title: String, val subtitle: String, val tint: Long, val intro: String, val steps: List<TopicStep>, val course: Boolean = false)

/** Where an idea is in its reviews: [box] 1–4 come back after 1, 3, 7 and 21 days; 5 is learned. */
class LearnItem(val box: Int, val due: Long, val seen: Long)

/**
 * Learn: today's lesson, gentle spaced review and topics. Everything is kept on this phone (and in
 * backups). There are no scores or streaks: a wrong answer only brings the idea back sooner.
 */
object Learn {
    private val INTERVALS = intArrayOf(1, 1, 3, 7, 21)
    const val LEARNED = 5

    @Volatile private var lessons: List<Lesson>? = null
    @Volatile private var topics: List<Topic>? = null

    fun today(): Long = (System.currentTimeMillis() + java.util.TimeZone.getDefault().getOffset(System.currentTimeMillis())) / 86_400_000L

    fun parseLessons(lines: Sequence<String>): List<Lesson> = lines.filter { it.isNotBlank() && !it.startsWith("#") }.mapNotNull { l ->
        val f = l.split('|')
        if (f.size < 7) null else Lesson(f[0], f[1], f[2], f[3], listOf(f[4], f[5]), f[6])
    }.toList()

    fun parseTopics(lines: Sequence<String>): List<Topic> {
        val out = mutableListOf<Topic>()
        var head: List<String>? = null
        var steps = mutableListOf<TopicStep>()
        fun flush() { head?.let { h -> if (steps.isNotEmpty()) out += Topic(h[1], h.getOrElse(2) { "" }, 0xFF000000 or (h.getOrNull(3)?.toLongOrNull(16) ?: 0x8A6A3A), h.getOrElse(4) { "" }, steps, h.getOrNull(5) == "course") } }
        lines.filter { it.isNotBlank() && !it.startsWith("#") }.forEach { l ->
            val f = l.split('|')
            when (f[0]) {
                "T" -> { flush(); head = f; steps = mutableListOf() }
                "S" -> if (f.size >= 3) steps += TopicStep(f[1] == "i", f[2].replace("\\n", "\n"))
            }
        }
        flush()
        return out
    }

    fun lessons(context: Context): List<Lesson> = lessons ?: context.applicationContext.resources.openRawResource(R.raw.lessons)
        .bufferedReader().useLines { parseLessons(it) }.also { lessons = it }

    fun topics(context: Context): List<Topic> = topics ?: context.applicationContext.resources.openRawResource(R.raw.topics)
        .bufferedReader().useLines { parseTopics(it) }.also { topics = it }

    fun lessonFor(context: Context, title: String?): Lesson? = title?.let { t -> lessons(context).firstOrNull { it.ref == t } }

    fun idea(bundled: List<Quote>, title: String): Quote? = bundled.firstOrNull { it.title == title }

    fun resolve(bundled: List<Quote>, step: TopicStep): Quote? =
        if (step.isIdea) idea(bundled, step.ref) else bundled.firstOrNull { it.title == null && it.text == step.ref }

    // ---- progress ----------------------------------------------------------------------------

    private fun state(prefs: QuotePrefs): JSONObject = prefs.learnJson?.let { runCatching { JSONObject(it) }.getOrNull() } ?: JSONObject()

    private fun save(prefs: QuotePrefs, o: JSONObject) { prefs.learnJson = o.toString() }

    fun items(prefs: QuotePrefs): Map<String, LearnItem> {
        val o = state(prefs).optJSONObject("items") ?: return emptyMap()
        return o.keys().asSequence().associateWith { k ->
            val i = o.getJSONObject(k)
            LearnItem(i.optInt("box"), i.optLong("due"), i.optLong("seen"))
        }
    }

    /** Records an answer: right moves the idea on to its next, longer gap; wrong brings it back tomorrow. */
    fun answer(prefs: QuotePrefs, ref: String, right: Boolean) {
        val o = state(prefs)
        val items = o.optJSONObject("items") ?: JSONObject().also { o.put("items", it) }
        val day = today()
        // Answering the same idea again on the same day (today's lesson twice, then a topic) counts only once.
        if (items.optJSONObject(ref)?.optLong("seen") == day) return
        val before = items.optJSONObject(ref)?.optInt("box") ?: 0
        val box = if (right) (before + 1).coerceAtMost(LEARNED) else 1
        items.put(ref, JSONObject().put("box", box).put("due", if (box >= LEARNED) Long.MAX_VALUE / 2 else day + INTERVALS[box]).put("seen", day))
        save(prefs, o)
    }

    fun due(prefs: QuotePrefs, context: Context): List<String> {
        val have = lessons(context).map { it.ref }.toSet()
        val day = today()
        return items(prefs).filter { (k, v) -> v.box < LEARNED && v.due <= day && k in have }.entries.sortedBy { it.value.due }.map { it.key }
    }

    /** Today's lesson: kept for the whole day; the next one not yet seen, in the order the lessons are listed. */
    fun todays(prefs: QuotePrefs, context: Context): Lesson? {
        val o = state(prefs)
        val day = today()
        val t = o.optJSONObject("today")
        val all = lessons(context)
        if (t != null && t.optLong("day") == day) all.firstOrNull { it.ref == t.optString("ref") }?.let { return it }
        val seen = items(prefs).keys
        val next = all.firstOrNull { it.ref !in seen } ?: return null
        o.put("today", JSONObject().put("day", day).put("ref", next.ref))
        save(prefs, o)
        return next
    }

    fun todayDone(prefs: QuotePrefs): Boolean {
        val t = state(prefs).optJSONObject("today") ?: return false
        return t.optLong("day") == today() && items(prefs).containsKey(t.optString("ref"))
    }

    fun topicStep(prefs: QuotePrefs, title: String): Int = state(prefs).optJSONObject("topics")?.optInt(title) ?: 0

    fun setTopicStep(prefs: QuotePrefs, title: String, step: Int) {
        val o = state(prefs)
        (o.optJSONObject("topics") ?: JSONObject().also { o.put("topics", it) }).put(title, step)
        save(prefs, o)
    }

    /** Takes a backup's progress, keeping whichever is further along for each idea and topic. */
    fun merge(prefs: QuotePrefs, incoming: JSONObject) {
        val o = state(prefs)
        val mine = o.optJSONObject("items") ?: JSONObject().also { o.put("items", it) }
        incoming.optJSONObject("items")?.let { theirs ->
            theirs.keys().forEach { k ->
                val t = theirs.optJSONObject(k) ?: return@forEach
                val m = mine.optJSONObject(k)
                if (m == null || t.optInt("box") > m.optInt("box")) mine.put(k, t)
            }
        }
        val myTopics = o.optJSONObject("topics") ?: JSONObject().also { o.put("topics", it) }
        incoming.optJSONObject("topics")?.let { theirs ->
            theirs.keys().forEach { k -> if (theirs.optInt(k) > myTopics.optInt(k)) myTopics.put(k, theirs.optInt(k)) }
        }
        save(prefs, o)
    }
}

// ---- screens ------------------------------------------------------------------------------------

private sealed class LearnView {
    data object Hub : LearnView()
    data class Lesson(val ref: String, val today: Boolean = true) : LearnView()
    data object Review : LearnView()
    data object Feed : LearnView()
    data class TopicPage(val title: String) : LearnView()
}

/** Discover › Learn: today's lesson, review, browsing, topics and what you have learned. */
@Composable
fun LearnScreen(onBack: () -> Unit, onOpenAuthor: (String) -> Unit, onOpenPaths: () -> Unit) {
    val context = LocalContext.current
    val prefs = remember { QuotePrefs(context) }
    val bundled = remember { QuoteRepository(context).bundled() }
    var view by remember { mutableStateOf<LearnView>(LearnView.Hub) }
    var refresh by remember { mutableIntStateOf(0) }
    val toHub: () -> Unit = { view = LearnView.Hub; refresh++ }
    when (val v = view) {
        is LearnView.Lesson -> { LessonScreen(v.ref, prefs, bundled, v.today, onBack = toHub, onOpenAuthor = onOpenAuthor); return }
        LearnView.Review -> { ReviewScreen(prefs, bundled, onBack = toHub); return }
        LearnView.Feed -> { FeedScreen(prefs, bundled, onBack = toHub, onLearn = { view = LearnView.Lesson(it, today = false) }, onOpenAuthor = onOpenAuthor); return }
        is LearnView.TopicPage -> { TopicScreen(v.title, prefs, bundled, onBack = toHub, onOpenAuthor = onOpenAuthor); return }
        LearnView.Hub -> Unit
    }
    BackHandler(onBack = onBack)
    val progress = remember(refresh) { Learn.items(prefs) }
    val todays = remember(refresh) { Learn.todays(prefs, context) }
    val done = remember(refresh) { Learn.todayDone(prefs) }
    val due = remember(refresh) { Learn.due(prefs, context) }
    val allTopics = remember { Learn.topics(context) }
    val topics = allTopics.filter { !it.course }
    val courses = allTopics.filter { it.course }
    val learned = progress.count { it.value.box >= Learn.LEARNED }
    ToolScreen("Learn", onBack) {
        item {
            Text(
                "Two minutes a day: one idea, one question, and gentle reviews that come back over the weeks. No scores, no streaks.",
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Stat("${progress.size}", if (progress.size == 1) "idea met" else "ideas met", Modifier.weight(1f))
                Stat("${due.size}", "to review today", Modifier.weight(1f))
                Stat("$learned", "learned", Modifier.weight(1f))
            }
        }
        item {
            if (todays != null) {
                Button(onClick = { view = LearnView.Lesson(todays.ref) }, modifier = Modifier.fillMaxWidth()) {
                    Text((if (done) "Today's lesson again · " else "Today's lesson · ") + todays.ref + " ›", maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            } else {
                Text("You have met every lesson so far. More are on the way; reviews carry on.", style = MaterialTheme.typography.bodyMedium)
            }
        }
        if (due.isNotEmpty()) item {
            OutlinedButton(onClick = { view = LearnView.Review }, modifier = Modifier.fillMaxWidth()) {
                Text("Quick review (${due.size.coerceAtMost(REVIEW_MAX)}) · about a minute")
            }
        }
        item {
            OutlinedButton(onClick = { view = LearnView.Feed }, modifier = Modifier.fillMaxWidth()) { Text("Browse ideas · swipe through ›") }
        }
        item { LearnLabel("Start with what you need") }
        topics.chunked(2).forEach { pair ->
            item(key = "topics-" + pair.first().title) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    pair.forEach { t ->
                        val step = Learn.topicStep(prefs, t.title)
                        Column(
                            Modifier.weight(1f).clip(RoundedCornerShape(16.dp)).background(Color(t.tint))
                                .clickable { view = LearnView.TopicPage(t.title) }.padding(12.dp),
                        ) {
                            Text(t.title, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                            Text(
                                if (step >= t.steps.size) "✓ done" else if (step > 0) "step ${step + 1} of ${t.steps.size}" else t.subtitle,
                                color = Color.White.copy(alpha = 0.85f), fontSize = 11.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                    if (pair.size == 1) Box(Modifier.weight(1f))
                }
            }
        }
        if (courses.isNotEmpty()) item { LearnLabel("Courses") }
        items(courses, key = { "c-" + it.title }) { t ->
            val step = Learn.topicStep(prefs, t.title)
            SoftCard(Modifier.fillMaxWidth(), onClick = { view = LearnView.TopicPage(t.title) }) {
                Row(Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.padding(end = 12.dp).height(34.dp).clip(RoundedCornerShape(3.dp)).background(Color(t.tint)).padding(horizontal = 2.dp)) {}
                    Text(t.title, Modifier.weight(1f), fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        when {
                            step >= t.steps.size -> "✓ done"
                            step > 0 -> "step ${step + 1} of ${t.steps.size}"
                            else -> "${t.steps.size} steps"
                        },
                        style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
        item {
            OutlinedButton(onClick = onOpenPaths, modifier = Modifier.fillMaxWidth()) {
                Text("Reading paths ›")
            }
        }
        if (progress.isNotEmpty()) {
            item { LearnLabel("What I've learned") }
            val day = Learn.today()
            items(progress.entries.sortedByDescending { it.value.seen }.take(40), key = { "l-" + it.key }) { (ref, it) ->
                SoftCard(Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(horizontal = 14.dp, vertical = 11.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(ref, Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            when {
                                it.box >= Learn.LEARNED -> "learned ✓"
                                it.due <= day -> "review today"
                                it.due - day == 1L -> "back tomorrow"
                                else -> "back in ${it.due - day} days"
                            },
                            style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }
        }
    }
}

private const val REVIEW_MAX = 5

@Composable
private fun Stat(value: String, label: String, modifier: Modifier) {
    SoftCard(modifier) {
        Column(Modifier.fillMaxWidth().padding(vertical = 10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(value, fontFamily = FontFamily.Serif, fontSize = 24.sp)
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        }
    }
}

@Composable
private fun LearnLabel(text: String) {
    Text(text.uppercase(), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary, letterSpacing = 1.2.sp)
}

/** The idea or myth itself, as on its page: title, words, originator. */
@Composable
internal fun IdeaCard(q: Quote, onOpen: () -> Unit) {
    SoftCard(Modifier.fillMaxWidth(), onClick = onOpen) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            q.title?.let { Text(it, fontFamily = FontFamily.Serif, fontSize = 23.sp) }
            Text(q.shownText, fontFamily = FontFamily.Serif, fontSize = 16.5.sp, lineHeight = 24.sp)
            q.byline?.let { Text("— $it ›", fontFamily = FontFamily.Serif, fontSize = 13.5.sp, color = MaterialTheme.colorScheme.primary) }
        }
    }
}

@Composable
private fun Takeaway(text: String) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text("TAKEAWAY", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary, letterSpacing = 1.2.sp)
        Text(text, style = MaterialTheme.typography.bodyMedium, lineHeight = 21.sp)
    }
}

/**
 * One question with its three choices. After a choice the right answer is marked and the explanation
 * shown; [onAnswered] is told whether it was right (once).
 */
@Composable
internal fun QuestionBlock(lesson: Lesson, onAnswered: (Boolean) -> Unit) {
    var picked by remember(lesson.ref) { mutableStateOf<String?>(null) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(lesson.question, fontFamily = FontFamily.Serif, fontSize = 18.sp, lineHeight = 26.sp)
        lesson.choices.forEach { c ->
            val isRight = c == lesson.answer
            val shown = picked != null
            val border = when {
                shown && isRight -> Color(0xFF4F8A5B)
                shown && c == picked -> MaterialTheme.colorScheme.outline
                else -> MaterialTheme.colorScheme.outlineVariant
            }
            Box(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
                    .border(if (shown && isRight) 2.dp else 1.dp, border, RoundedCornerShape(14.dp))
                    .background(if (shown && isRight) Color(0xFF4F8A5B).copy(alpha = 0.10f) else Color.Transparent)
                    .clickable(enabled = !shown, onClickLabel = "Choose this answer") { picked = c; onAnswered(isRight) }
                    .padding(horizontal = 14.dp, vertical = 12.dp),
            ) { Text((if (shown && isRight) "✓  " else "") + c, style = MaterialTheme.typography.bodyLarge) }
        }
        picked?.let { p ->
            Text(
                buildAnnotatedString {
                    withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) { append(if (p == lesson.answer) "Yes. " else "Not quite. ") }
                    append(lesson.why)
                },
                style = MaterialTheme.typography.bodyMedium, lineHeight = 21.sp,
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Color(0xFF4F8A5B).copy(alpha = 0.10f)).padding(14.dp),
            )
        }
    }
}

/** Today's lesson (or any lesson): read the idea and its takeaway, then one question. */
@Composable
private fun LessonScreen(ref: String, prefs: QuotePrefs, bundled: List<Quote>, today: Boolean, onBack: () -> Unit, onOpenAuthor: (String) -> Unit) {
    val context = LocalContext.current
    val lesson = remember(ref) { Learn.lessonFor(context, ref) }
    val idea = remember(ref) { Learn.idea(bundled, ref) }
    var stage by rememberSaveable(ref) { mutableIntStateOf(0) }
    var right by remember(ref) { mutableStateOf<Boolean?>(null) }
    var opened by remember { mutableStateOf<Quote?>(null) }
    opened?.let { q -> QuotePageSheet(quote = q, onDismiss = { opened = null }, onOpenAuthor = { opened = null; onOpenAuthor(it) }) }
    val kind = when (idea?.category?.group) { QuoteGroup.MYTHS -> "myth"; QuoteGroup.FOLKLORE -> "tale"; else -> "lesson" }
    ToolScreen(if (today) "Today's $kind" else "A $kind", onBack) {
        if (lesson == null || idea == null) {
            item { Text("This lesson isn't available.") }
            return@ToolScreen
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                repeat(3) { i ->
                    Box(Modifier.weight(1f).height(5.dp).clip(RoundedCornerShape(3.dp))
                        .background(if (i <= stage) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant))
                }
            }
        }
        when (stage) {
            0 -> {
                item { LearnLabel("2 minutes · " + (idea.category?.label ?: "")) }
                item { IdeaCard(idea) { opened = idea } }
                if (Diagrams.of(ref) != null) item { LessonDiagram(ref) }
                item { Takeaway(lesson.takeaway) }
                item { OutlinedButton(onClick = { opened = idea }, modifier = Modifier.fillMaxWidth()) { Text("More about this") } }
                item { Button(onClick = { stage = 1 }, modifier = Modifier.fillMaxWidth()) { Text("I've read it — check myself ›") } }
            }
            1 -> {
                item { LearnLabel("Question") }
                item {
                    QuestionBlock(lesson) { ok ->
                        right = ok
                        Learn.answer(prefs, ref, ok)
                    }
                }
                if (right != null) item { Button(onClick = { stage = 2 }, modifier = Modifier.fillMaxWidth()) { Text("Next ›") } }
                item {
                    Text("No marks, no streaks. A wrong answer just shows the explanation and brings the idea back sooner.",
                        style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            else -> {
                item { Text(idea.title ?: "", fontFamily = FontFamily.Serif, fontSize = 23.sp) }
                item {
                    Text(
                        if (right == true) "Added to what you've learned. It will come back for a quick review tomorrow, then after 3, 7 and 21 days."
                        else "Added to what you've learned. It will come back tomorrow for another look.",
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
                item { Takeaway(lesson.takeaway) }
                item { Button(onClick = onBack, modifier = Modifier.fillMaxWidth()) { Text("Done") } }
            }
        }
    }
}

/** A minute of review: the ideas that are due, one question each. */
@Composable
private fun ReviewScreen(prefs: QuotePrefs, bundled: List<Quote>, onBack: () -> Unit) {
    val context = LocalContext.current
    val queue = remember { Learn.due(prefs, context).take(REVIEW_MAX) }
    var at by rememberSaveable { mutableIntStateOf(0) }
    var answered by remember { mutableStateOf(false) }
    var showIdea by remember { mutableStateOf(false) }
    ToolScreen("Quick review", onBack) {
        if (at >= queue.size) {
            item { Text(if (queue.isEmpty()) "Nothing to review today." else "All done for today. Each idea will come back when it's due.", style = MaterialTheme.typography.bodyLarge) }
            item { Button(onClick = onBack, modifier = Modifier.fillMaxWidth()) { Text("Done") } }
            return@ToolScreen
        }
        val ref = queue[at]
        val lesson = Learn.lessonFor(context, ref) ?: return@ToolScreen
        item { LinearProgressIndicator(progress = { at / queue.size.toFloat() }, modifier = Modifier.fillMaxWidth()) }
        item { LearnLabel("${at + 1} of ${queue.size} · $ref") }
        item(key = "q-$ref") { QuestionBlock(lesson) { ok -> answered = true; Learn.answer(prefs, ref, ok) } }
        item {
            TextButton(onClick = { showIdea = !showIdea }) { Text(if (showIdea) "Hide the idea" else "Remind me of the idea") }
        }
        if (showIdea) Learn.idea(bundled, ref)?.let { q -> item { IdeaCard(q) {} } }
        if (answered) item {
            Button(onClick = { at++; answered = false; showIdea = false }, modifier = Modifier.fillMaxWidth()) {
                Text(if (at + 1 < queue.size) "Next ›" else "Finish")
            }
        }
    }
}

/** Browse ideas: one card at a time, swiping up for the next. Ideas, myths and quotes, freshly mixed each day. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FeedScreen(prefs: QuotePrefs, bundled: List<Quote>, onBack: () -> Unit, onLearn: (String) -> Unit, onOpenAuthor: (String) -> Unit) {
    BackHandler(onBack = onBack)
    val context = LocalContext.current
    val repo = remember { QuoteRepository(context) }
    val lessonRefs = remember { Learn.lessons(context).map { it.ref }.toSet() }
    val cards = remember {
        val r = Random(Learn.today())
        val ideas = bundled.filter { it.isIdea }.shuffled(r)
        val quotes = bundled.filter { !it.isIdea && it.category !in Category.MODERN && it.text.length in 40..260 }.shuffled(r)
        // Two ideas or myths, then a quote, and so on.
        buildList {
            ideas.forEachIndexed { i, idea ->
                add(idea)
                if (i % 2 == 1) quotes.getOrNull(i / 2)?.let { add(it) }
            }
        }
    }
    val pager = rememberPagerState { cards.size }
    var opened by remember { mutableStateOf<Quote?>(null) }
    var listsFor by remember { mutableStateOf<Quote?>(null) }
    var savedTick by remember { mutableIntStateOf(0) }
    opened?.let { q -> QuotePageSheet(quote = q, onDismiss = { opened = null }, onOpenAuthor = { opened = null; onOpenAuthor(it) }) }
    listsFor?.let { q ->
        val info = remember(q) { repo.favoriteInfo() }
        FavoriteCollectionsDialog(
            all = repo.collections(),
            selected = info[QuoteData.wordsKey(q.text)]?.collections.orEmpty(),
            onSave = { chosen, created ->
                if (!repo.isFavorite(q)) repo.toggleFavorite(q)
                created.forEach { repo.addCollection(it) }
                repo.setFavoriteCollections(q, chosen)
                listsFor = null
                savedTick++
            },
            onDismiss = { listsFor = null },
        )
    }
    Scaffold { inner ->
        Column(Modifier.fillMaxSize().padding(inner).padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                BackArrow(onBack)
                Text("Browse ideas", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(start = 14.dp))
            }
            Spacer(Modifier.height(12.dp))
            VerticalPager(pager, Modifier.weight(1f), pageSpacing = 16.dp) { page ->
                val q = cards[page]
                val saved = remember(q, savedTick) { repo.isFavorite(q) }
                Column(
                    Modifier.fillMaxSize().clip(RoundedCornerShape(22.dp))
                        .background(MaterialTheme.colorScheme.primaryContainer)
                        .clickable { opened = q }.padding(22.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(
                        (if (q.isIdea) q.category?.label ?: "" else "Quote") + (q.era?.let { " · $it" } ?: ""),
                        style = MaterialTheme.typography.labelMedium, letterSpacing = 1.2.sp, color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
                    )
                    q.title?.let { Text(it, fontFamily = FontFamily.Serif, fontSize = 26.sp, color = MaterialTheme.colorScheme.onPrimaryContainer) }
                    Text(
                        q.shownText,
                        fontFamily = FontFamily.Serif, fontSize = 18.sp, lineHeight = 27.sp, color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.weight(1f, fill = false), overflow = TextOverflow.Ellipsis,
                    )
                    q.byline?.let { Text("— $it", fontFamily = FontFamily.Serif, fontSize = 14.sp, color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f)) }
                    Spacer(Modifier.weight(1f))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = { repo.toggleFavorite(q); savedTick++ }) { Text(if (saved) "♥ Saved" else "♡ Save") }
                        TextButton(onClick = { listsFor = q }) { Text("+ Collection") }
                        if (q.title in lessonRefs) TextButton(onClick = { onLearn(q.title!!) }) { Text("Learn this ›") }
                    }
                }
            }
            Text(
                "Swipe up for the next · tap a card for more about it",
                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth().padding(top = 10.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
        }
    }
}

/** A topic: its introduction and steps; walking it shows each step with its question, if it has one. */
@Composable
private fun TopicScreen(title: String, prefs: QuotePrefs, bundled: List<Quote>, onBack: () -> Unit, onOpenAuthor: (String) -> Unit) {
    val context = LocalContext.current
    val topic = remember(title) { Learn.topics(context).firstOrNull { it.title == title } } ?: run { onBack(); return }
    var walking by rememberSaveable(title) { mutableStateOf(false) }
    var at by rememberSaveable(title) { mutableIntStateOf(Learn.topicStep(prefs, title).coerceAtMost(topic.steps.size - 1)) }
    var opened by remember { mutableStateOf<Quote?>(null) }
    var refresh by remember { mutableIntStateOf(0) }
    opened?.let { q -> QuotePageSheet(quote = q, onDismiss = { opened = null }, onOpenAuthor = { opened = null; onOpenAuthor(it) }) }
    if (walking) {
        BackHandler { walking = false; refresh++ }
        val step = topic.steps[at]
        val q = remember(title, at) { Learn.resolve(bundled, step) }
        val lesson = remember(title, at) { if (step.isIdea) Learn.lessonFor(context, step.ref) else null }
        var answered by remember(title, at) { mutableStateOf(lesson == null) }
        ToolScreen(topic.title, { walking = false; refresh++ }) {
            item { LinearProgressIndicator(progress = { (at + 1) / topic.steps.size.toFloat() }, modifier = Modifier.fillMaxWidth()) }
            item { LearnLabel("Step ${at + 1} of ${topic.steps.size}") }
            if (q != null) item { IdeaCard(q) { opened = q } }
            if (step.isIdea && Diagrams.of(step.ref) != null) item(key = "d-$title-$at") { LessonDiagram(step.ref) }
            if (lesson != null) {
                item { Takeaway(lesson.takeaway) }
                item(key = "q-$title-$at") { QuestionBlock(lesson) { ok -> answered = true; Learn.answer(prefs, step.ref, ok) } }
            }
            if (answered) item {
                Button(onClick = {
                    val next = at + 1
                    Learn.setTopicStep(prefs, title, next)
                    if (next >= topic.steps.size) { walking = false; refresh++ } else at = next
                }, modifier = Modifier.fillMaxWidth()) { Text(if (at + 1 < topic.steps.size) "Next ›" else "Finish ✓") }
            }
        }
        return
    }
    val reached = remember(refresh) { Learn.topicStep(prefs, title) }
    val progress = remember(refresh) { Learn.items(prefs) }
    ToolScreen(topic.title, onBack) {
        item { Text(topic.intro, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        item {
            Button(onClick = { at = if (reached >= topic.steps.size) 0 else reached; walking = true }, modifier = Modifier.fillMaxWidth()) {
                Text(when {
                    reached >= topic.steps.size -> "Walk it again ›"
                    reached > 0 -> "Continue · step ${reached + 1} of ${topic.steps.size} ›"
                    else -> "Start · step 1 of ${topic.steps.size} ›"
                })
            }
        }
        items(topic.steps.indices.toList(), key = { "s-$it" }) { i ->
            val step = topic.steps[i]
            val q = Learn.resolve(bundled, step)
            SoftCard(Modifier.fillMaxWidth(), onClick = { if (q != null) opened = q }) {
                Column(Modifier.padding(horizontal = 14.dp, vertical = 11.dp)) {
                    Text(
                        (if (q?.isIdea == true) (when (q.category?.group) { QuoteGroup.MYTHS -> "MYTH · "; QuoteGroup.FOLKLORE -> "TALE · "; else -> "IDEA · " }) + (q.category?.label ?: "").uppercase()
                        else "QUOTE · " + (authorName(q?.byline) ?: "").uppercase()),
                        style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, letterSpacing = 1.sp,
                    )
                    Text(
                        (q?.title ?: q?.shownText ?: step.ref) + if (step.isIdea && (progress[step.ref]?.box ?: 0) > 0) "  ✓" else "",
                        maxLines = 2, overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

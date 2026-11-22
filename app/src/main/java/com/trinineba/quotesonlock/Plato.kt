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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
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

/** One scene: a stretch of a dialogue in Jowett's words, with a title and note written for the app. */
class PlatoScene(val id: String, val dialogue: String, val title: String, val note: String, val paras: List<String>) {
    val words: Int get() = paras.sumOf { it.split(' ').size }
    val minutes: Int get() = (words / 200).coerceAtLeast(1)
}

/** A guide to one dialogue, written for the app: sections of (heading, paragraphs), questions to take away, its key scene and a related essay. */
class PlatoAnalysis(val sections: List<Pair<String, List<String>>>, val questions: List<String>, val keyScene: String, val essay: String) {
    val minutes: Int get() = (sections.sumOf { s -> s.second.sumOf { it.split(' ').size } } / 200).coerceAtLeast(1)
}

class PlatoDialogue(val title: String, val tagline: String, val setting: String, val who: String, val scenes: List<PlatoScene>, val analysis: PlatoAnalysis? = null)

/** Plato's dialogues (res/raw/plato.txt), in Benjamin Jowett's public-domain translation. */
object Plato {
    @Volatile private var all: List<PlatoDialogue>? = null

    fun parse(lines: Sequence<String>): List<PlatoDialogue> {
        val out = mutableListOf<PlatoDialogue>()
        var head: List<String>? = null
        var scenes = mutableListOf<PlatoScene>()
        var scene: List<String>? = null
        var paras = mutableListOf<String>()
        var sections = mutableListOf<Pair<String, MutableList<String>>>()
        var questions = mutableListOf<String>()
        var next: List<String>? = null
        fun flushScene() {
            val h = head
            scene?.let { s -> if (h != null && paras.isNotEmpty()) scenes += PlatoScene("${h[1]}/${scenes.size + 1}", h[1], s[1], s.getOrElse(2) { "" }, paras) }
            scene = null; paras = mutableListOf()
        }
        fun flushDialogue() {
            flushScene()
            val analysis = if (sections.isEmpty()) null else PlatoAnalysis(sections.map { it.first to it.second.toList() }, questions, next?.getOrNull(1) ?: "", next?.getOrNull(2) ?: "")
            head?.let { h -> if (scenes.isNotEmpty()) out += PlatoDialogue(h[1], h.getOrElse(2) { "" }, h.getOrElse(3) { "" }, h.getOrElse(4) { "" }, scenes, analysis) }
            head = null; scenes = mutableListOf(); sections = mutableListOf(); questions = mutableListOf(); next = null
        }
        lines.filter { it.isNotBlank() && !it.startsWith("#") }.forEach { line ->
            when (line.substringBefore('|')) {
                "D" -> { flushDialogue(); head = line.split('|') }
                "S" -> { flushScene(); scene = line.split('|') }
                "T" -> paras += line.substringAfter('|')
                "A" -> {
                    val f = line.split('|', limit = 3)
                    if (f.size == 3) {
                        if (sections.lastOrNull()?.first == f[1]) sections.last().second += f[2] else sections += f[1] to mutableListOf(f[2])
                    }
                }
                "K" -> questions += line.substringAfter('|')
                "X" -> next = line.split('|')
            }
        }
        flushDialogue()
        return out
    }

    fun dialogues(context: Context): List<PlatoDialogue> = all ?: synchronized(this) {
        all ?: context.applicationContext.resources.openRawResource(R.raw.plato).bufferedReader().useLines { parse(it) }.also { all = it }
    }

    private val turn = Regex("^([A-Z][A-Z ]{1,30}[A-Z]):\\s*")

    /** A turn spoken by a named character ("SOCRATES: …"), as the speaker and the words; null for narration. */
    fun speaker(p: String): Pair<String, String>? = turn.find(p)?.let { m -> m.groupValues[1] to p.substring(m.range.last + 1) }

    /** Plato's lines in the collection that appear word for word in [para]. */
    fun linesIn(bundled: List<Quote>, para: String): List<Triple<Int, Int, Quote>> =
        bundled.asSequence()
            .filter { it.title == null && it.author?.startsWith("Plato") == true && it.text.length > 24 }
            .mapNotNull { q -> para.indexOf(q.text).takeIf { it >= 0 }?.let { Triple(it, it + q.text.length, q) } }
            .sortedBy { it.first }
            .fold(mutableListOf<Triple<Int, Int, Quote>>()) { acc, t -> if (acc.isEmpty() || t.first >= acc.last().second) acc += t; acc }
}

private val PAccent: Color @Composable get() = MaterialTheme.colorScheme.primary
private val PSoft: Color @Composable get() = MaterialTheme.colorScheme.onSurfaceVariant

@Composable
private fun PLabel(text: String, modifier: Modifier = Modifier) {
    Text(text.uppercase(), style = MaterialTheme.typography.labelMedium, letterSpacing = 1.2.sp, color = PAccent, modifier = modifier)
}

@Composable
private fun PNote(label: String, text: String) {
    val accent = PAccent
    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min).clip(RoundedCornerShape(12.dp)).background(accent.copy(alpha = 0.10f))) {
        Box(Modifier.width(3.dp).fillMaxHeight().background(accent))
        Text(
            buildAnnotatedString {
                pushStyle(SpanStyle(fontWeight = FontWeight.SemiBold)); append("$label "); pop()
                append(text)
            },
            style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(12.dp),
        )
    }
}

@Composable
private fun PCard(title: String, meta: String, sub: String? = null, onClick: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .clickable(onClickLabel = "Open $title", onClick = onClick).padding(horizontal = 14.dp, vertical = 11.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Text(title, fontFamily = FontFamily.Serif, fontSize = 16.sp)
        Text(meta, style = MaterialTheme.typography.bodySmall, color = PSoft)
        sub?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = PSoft, maxLines = 2, overflow = TextOverflow.Ellipsis) }
    }
}

/** The dialogues, a scene to start with, then each dialogue's scenes; [start] opens a scene by id ("Republic/5"). */
@Composable
fun PlatoScreen(onBack: () -> Unit, onOpenAuthor: (String) -> Unit, start: String? = null, onOpenEssay: (String) -> Unit = {}) {
    val context = LocalContext.current
    val dialogues = remember { Plato.dialogues(context) }
    val scenes = remember(dialogues) { dialogues.flatMap { it.scenes } }
    var dialogue by rememberSaveable { mutableStateOf<String?>(null) }
    var open by rememberSaveable { mutableStateOf(start) }
    var reading by rememberSaveable { mutableStateOf<String?>(null) }
    dialogues.firstOrNull { it.title == reading }?.let { d ->
        d.analysis?.let { a ->
            PlatoAnalysisScreen(d, a, onBack = { reading = null }, onOpenScene = { title -> d.scenes.firstOrNull { it.title == title }?.let { open = it.id } }, onOpenEssay = onOpenEssay)
            return
        }
    }
    scenes.firstOrNull { it.id == open }?.let { s ->
        val list = dialogues.firstOrNull { it.title == s.dialogue }?.scenes ?: listOf(s)
        androidx.compose.runtime.key(s.id) {
            PlatoSceneScreen(s, list, onGo = { open = it.id }, onBack = { if (start != null) onBack() else open = null }, onOpenAuthor = onOpenAuthor)
        }
        return
    }
    dialogues.firstOrNull { it.title == dialogue }?.let { d ->
        ToolScreen(d.title, { dialogue = null }) {
            item { Text(d.setting, style = MaterialTheme.typography.bodyMedium, color = PSoft) }
            item { PNote("Who's there.", d.who) }
            d.analysis?.let { a ->
                item {
                    val accent = PAccent
                    Column(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
                            .background(androidx.compose.ui.graphics.Brush.linearGradient(listOf(accent.copy(alpha = 0.22f), MaterialTheme.colorScheme.surfaceContainerHigh)))
                            .border(1.dp, accent.copy(alpha = 0.4f), RoundedCornerShape(16.dp))
                            .clickable(onClickLabel = "Read the introduction to ${d.title}") { reading = d.title }.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(3.dp),
                    ) {
                        Text("Reading the ${d.title}", fontFamily = FontFamily.Serif, fontSize = 17.sp)
                        Text(
                            "An introduction: the question, the argument, why it still matters, and questions to take with you · ${a.minutes} min",
                            style = MaterialTheme.typography.bodySmall, color = PSoft,
                        )
                    }
                }
            }
            item { PLabel("Scenes") }
            items(d.scenes, key = { it.id }) { s ->
                PCard(s.title, "${s.minutes} min", s.note) { open = s.id }
            }
        }
        return
    }
    val cave = remember(scenes) { scenes.firstOrNull { it.title.contains("cave", ignoreCase = true) } }
    ToolScreen("Plato's dialogues", onBack) {
        item {
            Text(
                "Conversations written some 2,400 years ago, with Socrates asking the questions. ${scenes.size} great scenes from " +
                    "${dialogues.size} dialogues in Benjamin Jowett's translation, each with a note on what's at stake.",
                style = MaterialTheme.typography.bodyMedium, color = PSoft,
            )
        }
        cave?.let { c ->
            item { PLabel("Start with") }
            item { PCard(c.title, "${c.dialogue} · ${c.minutes} min") { open = c.id } }
        }
        item { PLabel("The dialogues") }
        items(dialogues, key = { it.title }) { d ->
            PCard(d.title, "${d.tagline} · ${d.scenes.size} scenes") { dialogue = d.title }
        }
        item {
            Text(
                "The text is Benjamin Jowett's translation (1871–1892), in the public domain. Scene titles, introductions and notes are written for the app.",
                style = MaterialTheme.typography.bodySmall, color = PSoft,
            )
        }
    }
}

/** One scene, set out as a conversation: each speaker's name above their words; narration and stage directions as they are. */
@Composable
private fun PlatoSceneScreen(s: PlatoScene, list: List<PlatoScene>, onGo: (PlatoScene) -> Unit, onBack: () -> Unit, onOpenAuthor: (String) -> Unit) {
    BackHandler(onBack = onBack)
    val context = LocalContext.current
    val rprefs = remember { QuotePrefs(context) }
    LaunchedEffect(s.id) {
        Reading.load(rprefs)
        val all = Plato.dialogues(context).flatMap { it.scenes }
        Reading.record(rprefs, Reading.Place("plato", s.id, "${s.dialogue} · ${s.title}", "Plato's dialogues", "plato:${s.id}", all.indexOfFirst { it.id == s.id }, all.size, System.currentTimeMillis()))
    }
    val bundled = remember { QuoteRepository(context).bundled() }
    var opened by remember { mutableStateOf<Quote?>(null) }
    opened?.let { q -> QuotePageSheet(quote = q, onDismiss = { opened = null }, onOpenAuthor = { opened = null; onOpenAuthor(it) }) }
    val i = list.indexOf(s)
    val accent = PAccent
    Scaffold(
        bottomBar = {
            val bkey = Reading.bookmarkKey("plato", s.id)
            var marked by remember(bkey) { mutableStateOf(bkey in rprefs.readerBookmarks) }
            ReaderBar(
                prevLabel = list.getOrNull(i - 1)?.title, nextLabel = list.getOrNull(i + 1)?.title,
                onPrev = { list.getOrNull(i - 1)?.let(onGo) }, onNext = { list.getOrNull(i + 1)?.let(onGo) },
                listenKey = "plato-${s.id}", listenTitle = "${s.dialogue}. ${s.title}",
                paragraphs = s.paras.map { p -> Plato.speaker(p)?.let { (who, w) -> if (who == "SCENE") w else "${who.lowercase().replaceFirstChar { it.uppercase() }}: $w" } ?: p },
                bookmarked = marked,
                onBookmark = { rprefs.readerBookmarks = if (marked) rprefs.readerBookmarks - bkey else rprefs.readerBookmarks + bkey; marked = !marked },
            )
        },
    ) { inner ->
        PassageSelection("Plato, ${s.dialogue} (tr. Benjamin Jowett)", s.dialogue) {
        LazyColumn(
            Modifier.fillMaxSize().padding(inner),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { BackArrow(onBack) }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    PLabel("${s.dialogue} · ${s.minutes} min")
                    Text(s.title, fontFamily = FontFamily.Serif, fontSize = 26.sp, lineHeight = 32.sp)
                }
            }
            itemsIndexed(s.paras) { _, p ->
                val turn = remember(p) { Plato.speaker(p) }
                val words = turn?.second ?: p
                if (turn?.first == "SCENE") {
                    Text(words, fontFamily = FontFamily.Serif, fontStyle = FontStyle.Italic, fontSize = 15.sp, color = PSoft)
                    return@itemsIndexed
                }
                val lines = remember(words) { Plato.linesIn(bundled, words) }
                val text = remember(words, lines, accent) {
                    buildAnnotatedString {
                        append(words)
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
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    turn?.let { Text(it.first, style = MaterialTheme.typography.labelMedium, letterSpacing = 1.2.sp, color = accent) }
                    Text(text, fontFamily = FontFamily.Serif, fontSize = readerSize(17f), lineHeight = readerSize(27f))
                }
            }
            if (s.note.isNotEmpty()) item { PNote("What's at stake.", s.note) }
            item { ChapterIdeas("plato", s.id) { opened = it } }
            item {
                Text("Tr. Benjamin Jowett · Underlined lines are in the collection: tap one to open it.", style = MaterialTheme.typography.bodySmall, color = PSoft)
            }
        }
        }
    }
}

/** The guide to one dialogue: short sections, questions to take away, then its key scene and a related essay. */
@Composable
private fun PlatoAnalysisScreen(d: PlatoDialogue, a: PlatoAnalysis, onBack: () -> Unit, onOpenScene: (String) -> Unit, onOpenEssay: (String) -> Unit) {
    ToolScreen("", onBack) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                PLabel("${d.title} · An introduction")
                Text("Reading the ${d.title}", fontFamily = FontFamily.Serif, fontSize = 28.sp, lineHeight = 34.sp)
                Text("${a.minutes} min · written for the app", style = MaterialTheme.typography.bodySmall, color = PSoft)
            }
        }
        a.sections.forEach { (heading, paras) ->
            item(key = "h-$heading") {
                Text(heading, fontFamily = FontFamily.Serif, fontSize = 20.sp, color = PAccent, modifier = Modifier.padding(top = 8.dp))
            }
            itemsIndexed(paras, key = { i, _ -> "p-$heading-$i" }) { _, p ->
                Text(p, fontFamily = FontFamily.Serif, fontSize = 17.sp, lineHeight = 27.sp)
            }
        }
        if (a.questions.isNotEmpty()) {
            item(key = "q-h") { Text("Questions to take with you", fontFamily = FontFamily.Serif, fontSize = 20.sp, color = PAccent, modifier = Modifier.padding(top = 8.dp)) }
            itemsIndexed(a.questions, key = { i, _ -> "q-$i" }) { _, q ->
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("·", fontFamily = FontFamily.Serif, fontSize = 20.sp, color = PAccent)
                    Text(q, fontFamily = FontFamily.Serif, fontSize = 16.sp, lineHeight = 24.sp, modifier = Modifier.weight(1f))
                }
            }
        }
        item(key = "next") { PLabel("Read next", Modifier.padding(top = 8.dp)) }
        d.scenes.firstOrNull { it.title == a.keyScene }?.let { s ->
            item(key = "next-scene") { PCard(s.title, "${d.title} · ${s.minutes} min") { onOpenScene(s.title) } }
        }
        if (a.essay.isNotEmpty()) item(key = "next-essay") { PCard(a.essay, "Essay") { onOpenEssay(a.essay) } }
    }
}

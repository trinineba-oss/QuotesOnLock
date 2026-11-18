package com.trinineba.quotesonlock

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** One step of a reading path: a quote (by its exact text) or an idea or myth (by its title). */
class PathStep(val isIdea: Boolean, val ref: String, val label: String, val note: String)
class ReadingPath(val title: String, val subtitle: String, val tint: Long, val steps: List<PathStep>)

/** Short guided sequences through the collection (res/raw/paths.txt). */
object ReadingPaths {
    @Volatile private var all: List<ReadingPath>? = null

    fun parse(lines: Sequence<String>): List<ReadingPath> {
        val out = mutableListOf<ReadingPath>()
        var title = ""; var subtitle = ""; var tint = 0L; var steps = mutableListOf<PathStep>()
        fun flush() { if (title.isNotEmpty() && steps.isNotEmpty()) out += ReadingPath(title, subtitle, tint, steps) }
        lines.filter { it.isNotBlank() && !it.startsWith("#") }.forEach { line ->
            val f = line.split('|')
            when (f[0]) {
                "P" -> {
                    flush()
                    title = f[1]; subtitle = f.getOrElse(2) { "" }
                    tint = 0xFF000000 or (f.getOrNull(3)?.toLongOrNull(16) ?: 0x7A5ADC)
                    steps = mutableListOf()
                }
                "S" -> if (f.size >= 5) steps += PathStep(f[1] == "i", f[2].replace("\\n", "\n"), f[3], f[4])
            }
        }
        flush()
        return out
    }

    fun all(context: Context): List<ReadingPath> = all ?: context.applicationContext.resources
        .openRawResource(R.raw.paths).bufferedReader().useLines { parse(it) }.also { all = it }

    /** Finds the quote, idea or myth a step points to. */
    fun resolve(bundled: List<Quote>, step: PathStep): Quote? =
        if (step.isIdea) bundled.firstOrNull { it.isIdea && it.title == step.ref }
        else bundled.firstOrNull { !it.isIdea && it.text == step.ref }
}

@Composable
fun ReadingPathsScreen(onBack: () -> Unit, onOpenAuthor: (String) -> Unit) {
    val context = LocalContext.current
    val prefs = remember { QuotePrefs(context) }
    val paths = remember { ReadingPaths.all(context) }
    var open by rememberSaveable { mutableStateOf<String?>(null) }
    var refresh by remember { mutableIntStateOf(0) }
    paths.firstOrNull { it.title == open }?.let { path ->
        PathScreen(path, prefs, onBack = { open = null; refresh++ }, onOpenAuthor = onOpenAuthor)
        return
    }
    BackHandler(onBack = onBack)
    val done = remember(refresh) { prefs.pathsDone }
    val last = remember(refresh) { prefs.lastPath?.let { t -> paths.firstOrNull { it.title == t && t !in done } } }
    Scaffold { inner ->
        LazyColumn(
            Modifier.fillMaxSize().padding(inner),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { BackArrow(onBack) }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Reading paths", style = MaterialTheme.typography.headlineSmall)
                    Text(
                        "Short guided journeys, a few minutes a day. Each step is a quote or an idea with a note on why it comes next.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
            if (last != null) {
                item { SectionLabel("Continue") }
                item(key = "continue") { PathCard(last, prefs.pathStep(last.title), false) { open = last.title } }
            }
            item { SectionLabel("All paths") }
            items(paths.filter { it !== last }, key = { it.title }) { p ->
                PathCard(p, if (p.title in done) -1 else prefs.pathStep(p.title), p.title in done) { open = p.title }
            }
            item {
                Text(
                    "The notes on each step are written for the app.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(text.uppercase(), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary, letterSpacing = 1.2.sp)
}

@Composable
private fun PathCard(path: ReadingPath, step: Int, finished: Boolean, onClick: () -> Unit) {
    val tint = Color(path.tint)
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(tint).clickable(onClick = onClick).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(path.title, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 17.sp)
        Text(path.subtitle, color = Color.White.copy(alpha = 0.9f), style = MaterialTheme.typography.bodyMedium)
        val started = !finished && step > 0
        if (started) {
            Spacer(Modifier.height(4.dp))
            LinearProgressIndicator(
                progress = { (step.toFloat() / path.steps.size).coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth(),
                color = Color.White,
                trackColor = Color.White.copy(alpha = 0.35f),
            )
        }
        Text(
            when {
                finished -> "${path.steps.size} steps · ✓ finished"
                started -> "Step ${step + 1} of ${path.steps.size}"
                else -> "${path.steps.size} steps"
            },
            color = Color.White.copy(alpha = 0.85f),
            style = MaterialTheme.typography.labelMedium,
        )
    }
}

@Composable
private fun PathScreen(path: ReadingPath, prefs: QuotePrefs, onBack: () -> Unit, onOpenAuthor: (String) -> Unit) {
    BackHandler(onBack = onBack)
    val context = LocalContext.current
    val bundled = remember { QuoteRepository(context).bundled() }
    val count = path.steps.size
    var at by rememberSaveable(path.title) { mutableIntStateOf(prefs.pathStep(path.title).coerceIn(0, count - 1)) }
    var opened by remember { mutableStateOf<Quote?>(null) }
    fun go(i: Int) {
        at = i.coerceIn(0, count - 1)
        prefs.setPathStep(path.title, at)
        prefs.lastPath = path.title
    }
    LaunchedEffect(path.title) { prefs.lastPath = path.title }
    val step = path.steps[at]
    val quote = remember(path.title, at) { ReadingPaths.resolve(bundled, step) }
    opened?.let { q ->
        QuotePageSheet(quote = q, onDismiss = { opened = null }, onOpenAuthor = { opened = null; onOpenAuthor(it) })
    }
    Scaffold { inner ->
        LazyColumn(
            Modifier.fillMaxSize().padding(inner),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { BackArrow(onBack) }
            item { Text(path.title, style = MaterialTheme.typography.headlineSmall) }
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally)) {
                    repeat(count) { i ->
                        Box(
                            Modifier.size(8.dp).clip(CircleShape)
                                .background(if (i <= at) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant)
                                .clickable(onClickLabel = "Step ${i + 1}") { go(i) }
                        )
                    }
                }
            }
            item { SectionLabel("Step ${at + 1} · ${step.label}") }
            item {
                SoftCard(Modifier.fillMaxWidth(), onClick = if (quote != null) ({ opened = quote }) else null) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        quote?.title?.let { Text(it, fontFamily = FontFamily.Serif, fontSize = 21.sp) }
                        Text(
                            when {
                                quote == null -> step.ref
                                quote.isIdea -> quote.text
                                else -> "“${quote.text}”"
                            },
                            fontFamily = FontFamily.Serif,
                            fontSize = if (quote?.isIdea == true) 16.sp else 18.sp,
                            lineHeight = if (quote?.isIdea == true) 23.sp else 26.sp,
                        )
                        val by = quote?.byline
                        val name = authorName(by)
                        if (by != null) Text(
                            if (name != null) "— $by ›" else "— $by",
                            fontFamily = FontFamily.Serif,
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = if (name != null) Modifier.clickable { onOpenAuthor(name) } else Modifier,
                        )
                    }
                }
            }
            item {
                Text(
                    buildAnnotatedString {
                        withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) { append("Why this step. ") }
                        append(step.note)
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    lineHeight = 21.sp,
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)).padding(14.dp),
                )
            }
            // Ideas and myths with a lesson in Learn can be checked here too; it counts towards your reviews.
            val lesson = if (step.isIdea) Learn.lessonFor(context, step.ref) else null
            if (lesson != null) item(key = "check-${path.title}-$at") {
                var checking by remember(path.title, at) { mutableStateOf(false) }
                if (!checking) {
                    TextButton(onClick = { checking = true }) { Text("Check myself ›") }
                } else {
                    QuestionBlock(lesson) { ok -> Learn.answer(prefs, step.ref, ok) }
                }
            }
            if (quote != null) item {
                OutlinedButton(onClick = { opened = quote }, modifier = Modifier.fillMaxWidth()) {
                    Text(if (quote.isIdea) "Open this ${when (quote.category?.group) { QuoteGroup.MYTHS -> "myth"; QuoteGroup.FOLKLORE -> "tale"; else -> "idea" }}" else "Open quote page (About, Context…)")
                }
            }
            item {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = { go(at - 1) }, enabled = at > 0) { Text("‹ Previous") }
                    Spacer(Modifier.weight(1f))
                    Text("${at + 1} of $count", style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.weight(1f))
                    if (at < count - 1) {
                        TextButton(onClick = { go(at + 1) }) { Text("Next ›") }
                    } else {
                        TextButton(onClick = {
                            prefs.pathsDone = prefs.pathsDone + path.title
                            prefs.setPathStep(path.title, 0)
                            onBack()
                        }) { Text("Finish ✓") }
                    }
                }
            }
        }
    }
}

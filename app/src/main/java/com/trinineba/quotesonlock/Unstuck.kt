package com.trinineba.quotesonlock

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import java.util.Locale

/** One kind of creative block: how it feels, why it happens, a line from someone who knew it, and things to try. */
class Block(
    val key: String,
    val emoji: String,
    val name: String,
    val feels: String,
    val why: String,
    val line: String,
    val lineBy: String,
    val tries: List<Pair<String, String>>,
    val ideas: List<String>,
)

/** The data in res/raw/unstuck.txt. */
class UnstuckData(val blocks: List<Block>, val finish: Pair<String, String>?, val prompts: Map<String, List<String>>)

object Unstuck {
    @Volatile private var cached: UnstuckData? = null

    /** Great ideas shown under each block. */
    private val IDEAS = mapOf(
        "blank" to listOf("Morning Pages", "Negative Capability"),
        "critic" to listOf("The Inner Censor", "Negative Capability"),
        "empty" to listOf("Filling the Well", "The Artist Date"),
        "big" to listOf("Writer's Block", "Flow"),
        "later" to listOf("Writer's Block", "Morning Pages"),
        "joy" to listOf("Flow", "Shadow Artists"),
    )

    val MEDIA = listOf("writing" to "Writing", "art" to "Art", "music" to "Music", "any" to "Anything")

    const val ESSAY = "Getting something down"
    const val PATH = "Through creative block"

    fun parse(lines: Sequence<String>): UnstuckData {
        val blocks = mutableListOf<Block>()
        var head: List<String>? = null
        var tries = mutableListOf<Pair<String, String>>()
        var finish: Pair<String, String>? = null
        val prompts = mutableMapOf<String, MutableList<String>>()
        fun flush() {
            head?.let { h -> blocks += Block(h[1], h[2], h[3], h[4], h[5], h[6], h[7], tries, IDEAS[h[1]].orEmpty()) }
            head = null; tries = mutableListOf()
        }
        lines.filter { it.isNotBlank() && !it.startsWith("#") }.forEach { line ->
            val f = line.split('|')
            when (f[0]) {
                "B" -> { flush(); if (f.size >= 8) head = f }
                "T" -> if (f.size >= 3) tries += f[1] to f[2]
                "D" -> if (f.size >= 3) finish = f[1] to f[2]
                "P" -> if (f.size >= 3) prompts.getOrPut(f[1]) { mutableListOf() } += f[2]
            }
        }
        flush()
        return UnstuckData(blocks, finish, prompts)
    }

    fun data(context: Context): UnstuckData = cached ?: synchronized(this) {
        cached ?: context.applicationContext.resources.openRawResource(R.raw.unstuck).bufferedReader().useLines { parse(it) }.also { cached = it }
    }

    /** A prompt for the chosen medium; "Anything" draws from all of them. */
    fun prompt(data: UnstuckData, medium: String, avoid: String?): String? {
        val pool = if (medium == "any") data.prompts.values.flatten() else data.prompts[medium].orEmpty()
        return pool.filter { it != avoid }.randomOrNull() ?: pool.firstOrNull()
    }
}

/** The card on Calm, under the SOS button. */
@Composable
fun UnstuckCard(onClick: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp))
            .background(Brush.linearGradient(listOf(Color(0xFFB0703A), Color(0xFF7A3F5C))))
            .clickable(onClickLabel = "Get unstuck", onClick = onClick).padding(horizontal = 16.dp, vertical = 14.dp),
    ) {
        Text("Stuck on something you're making? ›", color = Color.White, fontFamily = BookSerif, fontSize = 18.sp)
        Text("Writing, drawing, music, anything. Ten minutes to get moving again.", color = Color.White.copy(alpha = 0.88f), style = MaterialTheme.typography.bodySmall)
    }
}

private enum class Stage { PICK, BLOCK, DECK, TIMER, DONE }

/**
 * Unstuck, for writer's and creative block: name the block, read a line from someone who knew it and why it happens,
 * then try one small thing against a quiet timer. Nothing is saved unless you write a note at the end.
 * [onTool] opens another Calm tool ("practice:pages", "look:…"); [onRoute] opens a page in Discover (an essay or path).
 */
@Composable
fun UnstuckScreen(prefs: QuotePrefs, onBack: () -> Unit, onTool: (String) -> Unit, onRoute: (String) -> Unit, onFinished: () -> Unit) {
    val context = LocalContext.current
    val data = remember { runCatching { Unstuck.data(context) }.getOrDefault(UnstuckData(emptyList(), null, emptyMap())) }
    val bundled = remember { QuoteRepository(context).bundled() }
    var stage by rememberSaveable { mutableStateOf(Stage.PICK) }
    var blockKey by rememberSaveable { mutableStateOf<String?>(null) }
    var medium by rememberSaveable { mutableStateOf(prefs.raw.getString("unstuck_medium", "writing") ?: "writing") }
    var tryIndex by rememberSaveable { mutableIntStateOf(0) }
    var task by rememberSaveable { mutableStateOf("") }
    var card by rememberSaveable { mutableStateOf<String?>(null) }
    var minutes by rememberSaveable { mutableIntStateOf(10) }
    var made by rememberSaveable { mutableIntStateOf(0) }
    var opened by remember { mutableStateOf<Quote?>(null) }
    val block = data.blocks.firstOrNull { it.key == blockKey }

    fun setMedium(m: String) { medium = m; prefs.raw.edit().putString("unstuck_medium", m).apply() }
    fun start(text: String, m: Int) { task = text; minutes = m; stage = Stage.TIMER }

    opened?.let { q -> QuotePageSheet(quote = q, onDismiss = { opened = null }) }

    when (stage) {
        Stage.PICK -> ToolScreen("Unstuck", onBack) {
            item {
                Text("What does the block feel like?", fontFamily = BookSerif, fontSize = 24.sp)
                Text("There's no wrong answer. Most blocks are one of these.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            data.blocks.forEach { b ->
                item(key = "b-${b.key}") {
                    ChoiceCard(b.emoji, b.name, b.feels) { blockKey = b.key; tryIndex = 0; stage = Stage.BLOCK }
                }
            }
            item { MediumChips(medium, label = "I'm making:") { setMedium(it) } }
            item {
                Text(
                    "Based on what helps working writers and artists, and on Julia Cameron's The Artist's Way. Nothing is saved unless you choose to write a note.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Stage.BLOCK -> {
            val b = block ?: run { stage = Stage.PICK; return }
            BackHandler { stage = Stage.PICK }
            ToolScreen(b.name, onBack = { stage = Stage.PICK }) {
                item { LineCard(b.line, b.lineBy) }
                item { Text(b.why, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                item {
                    val t = b.tries.getOrNull(tryIndex % b.tries.size.coerceAtLeast(1))
                    if (t != null) TryCard(t.first, t.second, onStart = { start(t.first, it) })
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (b.tries.size > 1) TextButton(onClick = { tryIndex++ }) { Text("↻ Another idea") }
                        if (b.key == "blank") TextButton(onClick = { card = Unstuck.prompt(data, medium, null); stage = Stage.DECK }) { Text("Draw a prompt card ›") }
                    }
                }
                item {
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        b.ideas.forEach { title ->
                            Learn.idea(bundled, title)?.let { idea -> OutlinedButton(onClick = { opened = idea }) { Text(title) } }
                        }
                        OutlinedButton(onClick = { onRoute("essays:${Unstuck.ESSAY}") }) { Text("Essay: ${Unstuck.ESSAY}") }
                    }
                }
            }
        }

        Stage.DECK -> {
            BackHandler { stage = Stage.BLOCK }
            ToolScreen("Draw a card", onBack = { stage = Stage.BLOCK }) {
                item { Text("One small starting point. Take it anywhere.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                item {
                    Box(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Color(0xFFF4EAD6)).padding(horizontal = 18.dp, vertical = 26.dp),
                    ) { Text(card ?: "", color = Color(0xFF2E2012), fontFamily = BookSerif, fontSize = 20.sp, lineHeight = 29.sp) }
                }
                item { MediumChips(medium, label = null) { setMedium(it); card = Unstuck.prompt(data, it, card) } }
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { card?.let { start(it, 10) } }) { Text("Use this · 10 min") }
                        OutlinedButton(onClick = { card = Unstuck.prompt(data, medium, card) }) { Text("↻ Another") }
                    }
                }
                item { Text("MORE FOR THE BLANK PAGE", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary, letterSpacing = 1.2.sp) }
                item { ChoiceCard("✍️", "Morning pages", "Three pages, first thing, about anything") { onTool("practice:pages") } }
                item {
                    Looking.all(context).firstOrNull()?.let { sc -> ChoiceCard("🔍", "Slow looking", "A few quiet minutes with one painting") { onTool("look:${sc.key}") } }
                }
            }
        }

        Stage.TIMER -> TimerStage(
            label = listOfNotNull(block?.name, Unstuck.MEDIA.firstOrNull { it.first == medium }?.second).joinToString(" · "),
            task = task,
            minutes = minutes,
            onStop = { made++; stage = Stage.DONE },
        )

        Stage.DONE -> DoneStage(
            prefs = prefs,
            finish = data.finish,
            bundled = bundled,
            title = "Unstuck" + (block?.let { " · ${it.name}" } ?: ""),
            onMore = { start(task, 10) },
            onPath = { onRoute("paths:${Unstuck.PATH}") },
            onDone = onFinished,
        )
    }
}

@Composable
private fun ChoiceCard(emoji: String, title: String, sub: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .clickable(onClickLabel = title, onClick = onClick).padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(emoji, fontSize = 22.sp, modifier = Modifier.padding(end = 12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun MediumChips(medium: String, label: String?, onPick: (String) -> Unit) {
    Row(Modifier.horizontalScroll(rememberScrollState()), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        label?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
        Unstuck.MEDIA.forEach { (k, l) -> FilterChip(selected = medium == k, onClick = { onPick(k) }, label = { Text(l) }) }
    }
}

@Composable
private fun LineCard(text: String, by: String) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.surfaceContainerHigh).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text("“$text”", fontFamily = BookSerif, fontSize = 18.sp, lineHeight = 27.sp)
        Text(by, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun TryCard(text: String, why: String, onStart: (Int) -> Unit) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
            .background(Brush.linearGradient(listOf(Color(0xFF2E4A3E), Color(0xFF24384A)))).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text("TRY THIS NOW", color = Color(0xFFBFE3CF), style = MaterialTheme.typography.labelMedium, letterSpacing = 1.2.sp)
        Text(text, color = Color.White, fontFamily = BookSerif, fontSize = 19.sp, lineHeight = 26.sp)
        Text(why, color = Color.White.copy(alpha = 0.8f), style = MaterialTheme.typography.bodySmall)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Button(onClick = { onStart(10) }) { Text("Start · 10 min") }
            OutlinedButton(onClick = { onStart(5) }) { Text("5", color = Color.White) }
            OutlinedButton(onClick = { onStart(15) }) { Text("15", color = Color.White) }
        }
    }
}

/** The quiet timer: a ring that empties, the time left, and the task; the screen stays on. */
@Composable
private fun TimerStage(label: String, task: String, minutes: Int, onStop: () -> Unit) {
    val view = LocalView.current
    DisposableEffect(Unit) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }
    val total = minutes * 60
    var left by rememberSaveable(task, minutes) { mutableIntStateOf(total) }
    var running by rememberSaveable { mutableStateOf(true) }
    BackHandler(onBack = onStop)
    LaunchedEffect(running, task, minutes) {
        while (running && left > 0) { delay(1000); left-- }
        if (left <= 0) onStop()
    }
    val ring = MaterialTheme.colorScheme.primary
    val track = MaterialTheme.colorScheme.surfaceContainerHighest
    Scaffold { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            if (label.isNotBlank()) Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Box(Modifier.padding(vertical = 24.dp).size(230.dp), contentAlignment = Alignment.Center) {
                Canvas(Modifier.fillMaxSize()) {
                    val w = 8.dp.toPx()
                    val s = Size(size.width - w, size.height - w)
                    val o = Offset(w / 2, w / 2)
                    drawArc(track, 0f, 360f, false, o, s, style = Stroke(w))
                    drawArc(ring, -90f, 360f * left / total.coerceAtLeast(1), false, o, s, style = Stroke(w, cap = StrokeCap.Round))
                }
                Text(String.format(Locale.US, "%d:%02d", left / 60, left % 60), fontFamily = BookSerif, fontSize = 48.sp)
            }
            Text("Keep the pen moving.\nNo going back.", fontFamily = BookSerif, fontSize = 20.sp, textAlign = TextAlign.Center, lineHeight = 28.sp)
            if (task.isNotBlank()) Text(task, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 14.dp))
            Spacer(Modifier.height(28.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(onClick = { running = !running }) { Text(if (running) "Pause" else "Carry on") }
                OutlinedButton(onClick = onStop) { Text("I'm done") }
            }
            Text("The screen stays on while the timer runs.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 18.dp))
        }
    }
}

/** The gentle finish: no score, a line, and the choice to keep going or note how it went. */
@Composable
private fun DoneStage(
    prefs: QuotePrefs,
    finish: Pair<String, String>?,
    bundled: List<Quote>,
    title: String,
    onMore: () -> Unit,
    onPath: () -> Unit,
    onDone: () -> Unit,
) {
    var noting by remember { mutableStateOf(false) }
    var note by rememberSaveable { mutableStateOf("") }
    var saved by rememberSaveable { mutableStateOf(false) }
    BackHandler(onBack = onDone)
    if (noting) {
        AlertDialog(
            onDismissRequest = { noting = false },
            title = { Text("How did it go?") },
            text = { OutlinedTextField(note, { note = it }, minLines = 3, modifier = Modifier.fillMaxWidth(), placeholder = { Text("A few words, for you only") }) },
            confirmButton = {
                TextButton(enabled = note.isNotBlank(), onClick = {
                    val q = finish?.let { (t, _) -> bundled.firstOrNull { it.text == t } }
                    Journal.add(prefs, q, title, note)
                    saved = true; noting = false
                }) { Text("Save to Notes") }
            },
            dismissButton = { TextButton(onClick = { noting = false }) { Text("Cancel") } },
        )
    }
    Scaffold { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text("🌱", fontSize = 44.sp)
            Text("You made something.", fontFamily = BookSerif, fontSize = 26.sp, modifier = Modifier.padding(top = 8.dp))
            Text(
                "That's the whole job today. Tomorrow it will be a little easier to begin.",
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 6.dp, bottom = 18.dp),
            )
            finish?.let { (t, by) -> LineCard(t, by) }
            Spacer(Modifier.height(18.dp))
            Button(onClick = onMore) { Text("Keep going · 10 more min") }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
                OutlinedButton(onClick = { noting = true }, enabled = !saved) { Text(if (saved) "Saved" else "Note how it went") }
                OutlinedButton(onClick = onDone) { Text("Done") }
            }
            TextButton(onClick = onPath, modifier = Modifier.padding(top = 6.dp)) { Text("A week through creative block ›") }
        }
    }
}

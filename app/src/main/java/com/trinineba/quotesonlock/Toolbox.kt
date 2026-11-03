package com.trinineba.quotesonlock

import android.view.HapticFeedbackConstants
import androidx.activity.compose.BackHandler
import androidx.annotation.DrawableRes
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import org.json.JSONArray

/** The feelings offered in "When it's hard", each opening a hand-picked set of gentle quotes. */
enum class Feeling(val key: String, val label: String, val heading: String, val intro: String) {
    ANXIOUS("anxious", "Anxious", "For when you feel anxious", "Lines about the present moment, fear and calm. Read slowly; there is no hurry."),
    SAD("sad", "Sad", "For when you feel sad", "Sorrow is part of a full heart. These lines sit with it, and look for the light."),
    GRIEVING("grieving", "Grieving", "For when you are grieving", "On love, loss and remembering. Grief is love with nowhere to go."),
    LONELY("lonely", "Lonely", "For when you feel lonely", "On friendship, belonging and the company of the world."),
    OVERWHELMED("overwhelmed", "Overwhelmed", "For when it's all too much", "One thing at a time. Small steps, and permission to rest."),
    ANGRY("angry", "Angry", "For when you feel angry", "Anger is real and has its reasons. These lines are about letting it cool."),
    NUMB("numb", "Numb", "For when you feel numb", "Small beauties to notice: light, spring, music, a sky."),
    SLEEP("sleep", "Can't sleep", "For when you can't sleep", "Quiet lines for the night: dark, stars, stillness and rest.");

    companion object {
        fun fromKey(key: String?): Feeling? = entries.firstOrNull { it.key == key }
    }
}

/** The hand-picked comfort sets (res/raw/comfort.txt: `feeling|quote text` or `feeling|idea:<title>`). */
object Comfort {
    @Volatile private var lines: List<Pair<String, String>>? = null

    fun quotes(context: android.content.Context, repo: QuoteRepository, feeling: Feeling): List<Quote> {
        val all = lines ?: context.applicationContext.resources.openRawResource(R.raw.comfort).bufferedReader().useLines { seq ->
            seq.filter { it.isNotBlank() && !it.startsWith("#") && '|' in it }
                .map { it.substringBefore('|') to it.substringAfter('|').replace("\\n", "\n") }
                .toList()
        }.also { lines = it }
        val bundled = repo.bundled()
        val byText = bundled.associateBy { it.text }
        val byTitle = bundled.filter { it.isIdea }.associateBy { it.title }
        return all.filter { it.first == feeling.key }.mapNotNull { (_, entry) ->
            if (entry.startsWith("idea:")) byTitle[entry.removePrefix("idea:")] else byText[entry]
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FeelingChips(selected: Feeling? = null, onPick: (Feeling) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Feeling.entries.forEach { f ->
            FilterChip(selected = f == selected, onClick = { onPick(f) }, label = { Text(f.label) })
        }
    }
}

/** One row of the toolbox: an icon, a title and a line saying what it is. */
@Composable
fun ToolRow(@DrawableRes icon: Int, title: String, subtitle: String, onClick: () -> Unit) {
    SoftCard(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(painterResource(icon), contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(26.dp))
            Column(Modifier.padding(start = 14.dp)) {
                Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/** The shared frame of the tool screens: back arrow, title, then the content in a scrolling column. */
@Composable
private fun ToolScreen(title: String, onBack: () -> Unit, content: androidx.compose.foundation.lazy.LazyListScope.() -> Unit) {
    BackHandler(onBack = onBack)
    Scaffold { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                            .clickable(onClickLabel = "Back", onClick = onBack),
                        contentAlignment = Alignment.Center,
                    ) { Icon(painterResource(R.drawable.ic_arrow_back), contentDescription = null) }
                    Text(title, style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(start = 14.dp))
                }
            }
            content()
        }
    }
}

private enum class Phase(val label: String) { IN("Breathe in…"), TOP_UP("A little more…"), HOLD("Hold…"), OUT("Breathe out…"), REST("Rest…") }

private class BreathPattern(val label: String, val about: String, val steps: List<Pair<Phase, Int>>)

private val PATTERNS = listOf(
    BreathPattern("Box 4-4-4-4", "In for 4, hold for 4, out for 4, rest for 4. Steady and even.",
        listOf(Phase.IN to 4, Phase.HOLD to 4, Phase.OUT to 4, Phase.REST to 4)),
    BreathPattern("4-7-8", "In for 4, hold for 7, a long breath out for 8. Good before sleep.",
        listOf(Phase.IN to 4, Phase.HOLD to 7, Phase.OUT to 8)),
    BreathPattern("Long sigh", "Two breaths in through the nose, the second a small top-up, then a long, slow breath out.",
        listOf(Phase.IN to 2, Phase.TOP_UP to 1, Phase.OUT to 6)),
)

/**
 * A breathing guide: a soft circle that grows on the in-breath and shrinks on the out-breath. One
 * frame clock drives the circle, the words and the countdown together, so they can't drift apart.
 */
@Composable
fun BreatheScreen(repo: QuoteRepository, onBack: () -> Unit) {
    val context = LocalContext.current
    val view = LocalView.current
    var patternIndex by rememberSaveable { mutableIntStateOf(0) }
    var minutes by rememberSaveable { mutableIntStateOf(3) }
    var vibrate by rememberSaveable { mutableStateOf(true) }
    var running by remember { mutableStateOf(false) }
    var finished by remember { mutableStateOf(false) }
    var phase by remember { mutableStateOf<Phase?>(null) }
    var phaseSeconds by remember { mutableIntStateOf(0) }
    var secondsLeft by remember { mutableIntStateOf(0) }
    val scale = remember { androidx.compose.runtime.mutableFloatStateOf(0.45f) }
    val quote = remember { Comfort.quotes(context, repo, Feeling.ANXIOUS).filter { !it.isIdea && it.text.length < 200 }.randomOrNull() }
    val pattern = PATTERNS[patternIndex]
    val color = MaterialTheme.colorScheme.primary

    DisposableEffect(running) {
        view.keepScreenOn = running
        onDispose { view.keepScreenOn = false }
    }
    LaunchedEffect(running, patternIndex, minutes) {
        if (!running) {
            phase = null
            scale.floatValue = 0.45f
            return@LaunchedEffect
        }
        finished = false
        val steps = pattern.steps
        val cycleMs = steps.sumOf { it.second } * 1000L
        val totalMs = minutes * 60_000L
        val topUp = steps.any { it.first == Phase.TOP_UP }
        var shown = -1
        var from = scale.floatValue
        val start = androidx.compose.runtime.withFrameNanos { it }
        while (true) {
            val now = androidx.compose.runtime.withFrameNanos { it }
            val elapsed = (now - start) / 1_000_000L
            if (elapsed >= totalMs) break
            secondsLeft = ((totalMs - elapsed + 999) / 1000).toInt()
            val inCycle = elapsed % cycleMs
            var index = 0
            var phaseStart = 0L
            while (inCycle >= phaseStart + steps[index].second * 1000L) {
                phaseStart += steps[index].second * 1000L
                index++
            }
            val (p, secs) = steps[index]
            val duration = secs * 1000L
            if (index != shown) {
                shown = index
                phase = p
                from = scale.floatValue
                if (vibrate) view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
            }
            phaseSeconds = ((duration - (inCycle - phaseStart) + 999) / 1000).toInt()
            val target = when (p) {
                Phase.IN -> if (topUp) 0.9f else 1f
                Phase.TOP_UP -> 1f
                Phase.OUT -> 0.45f
                else -> from
            }
            val progress = ((inCycle - phaseStart).toFloat() / duration).coerceIn(0f, 1f)
            scale.floatValue = from + (target - from) * FastOutSlowInEasing.transform(progress)
        }
        running = false
        finished = true
    }

    ToolScreen("Breathe", onBack) {
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PATTERNS.forEachIndexed { i, p ->
                    FilterChip(selected = i == patternIndex, onClick = { if (!running) patternIndex = i }, label = { Text(p.label) })
                }
            }
        }
        item { Text(pattern.about, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        item {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.fillMaxWidth().height(260.dp), contentAlignment = Alignment.Center) {
                    Canvas(Modifier.size(250.dp)) {
                        val r = size.minDimension / 2f * scale.floatValue
                        drawCircle(
                            brush = Brush.radialGradient(
                                listOf(color.copy(alpha = 0.5f), color.copy(alpha = 0.16f), Color.Transparent),
                                center = Offset(size.width / 2f, size.height / 2f),
                                radius = r.coerceAtLeast(1f),
                            ),
                            radius = r,
                        )
                    }
                    if (running && phase != null) {
                        Text("$phaseSeconds", style = MaterialTheme.typography.displaySmall, color = MaterialTheme.colorScheme.onSurface)
                    }
                }
                // The words sit below the circle, where the glow can't wash them out.
                Text(
                    when {
                        running && phase != null -> phase!!.label
                        finished -> "Well done. Notice how you feel now."
                        else -> "Tap Start when you're ready"
                    },
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.primary,
                    textAlign = TextAlign.Center,
                )
            }
        }
        item {
            Text(
                if (running) "%d:%02d left".format(secondsLeft / 60, secondsLeft % 60)
                else "The circle grows as you breathe in and shrinks as you breathe out." +
                    if (vibrate) " A soft vibration marks each change, so you can close your eyes." else "",
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                listOf(1, 3, 5).forEach { m ->
                    FilterChip(selected = m == minutes, onClick = { if (!running) minutes = m }, label = { Text("$m min") })
                }
                Spacer(Modifier.weight(1f))
                Text("Vibrate", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(end = 6.dp))
                Switch(checked = vibrate, onCheckedChange = { vibrate = it })
            }
        }
        item {
            Button(
                onClick = {
                    finished = false
                    running = !running
                },
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) { Text(if (running) "Stop" else "Start") }
        }
        quote?.let { q ->
            item {
                SoftCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(18.dp)) {
                        Text(q.shownText, style = MaterialTheme.typography.bodyLarge)
                        q.byline?.let { Text("— $it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 6.dp)) }
                    }
                }
            }
        }
    }
}

private val GROUND_STEPS = listOf(
    5 to "things you can see",
    4 to "things you can touch or feel",
    3 to "things you can hear",
    2 to "things you can smell",
    1 to "thing you can taste",
)

/** 5-4-3-2-1 grounding: name things around you, one sense at a time. */
@Composable
fun GroundScreen(onBack: () -> Unit, onBreathe: () -> Unit) {
    var step by rememberSaveable { mutableIntStateOf(0) }
    var named by rememberSaveable { mutableIntStateOf(0) }
    ToolScreen("Ground yourself", onBack) {
        item {
            Text(
                "Look around you and name each thing, silently or out loud. Tap once for each one.",
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        if (step < GROUND_STEPS.size) {
            val (count, what) = GROUND_STEPS[step]
            item {
                SoftCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(22.dp).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        Text("$count", style = MaterialTheme.typography.displayLarge, color = MaterialTheme.colorScheme.primary)
                        Text("Name $count $what", style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            repeat(count) { i ->
                                Box(
                                    Modifier.size(22.dp).clip(CircleShape).background(
                                        if (i < named) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
                                    )
                                )
                            }
                        }
                        Button(onClick = {
                            named++
                            if (named >= count) {
                                step++
                                named = 0
                            }
                        }) { Text(if (named + 1 >= count) "Named it · next sense" else "Named one") }
                    }
                }
            }
        } else {
            item {
                SoftCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(22.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("You're here, in this moment.", style = MaterialTheme.typography.titleMedium)
                        Text("Take one slow breath, and notice your feet on the ground.", style = MaterialTheme.typography.bodyMedium)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = { step = 0; named = 0 }) { Text("Again") }
                            Button(onClick = onBreathe) { Text("Breathe") }
                        }
                    }
                }
            }
        }
    }
}

/** Look at a thought again: a gentle version of the cognitive-therapy thought record. */
@Composable
fun ReframeScreen(prefs: QuotePrefs, onBack: () -> Unit) {
    val prompts = listOf(
        "What's the thought?" to "e.g. \"I always get things wrong\"",
        "What makes it feel true?" to "",
        "What doesn't fit it?" to "Times it wasn't so, things you'd be leaving out",
        "If a friend had this thought, what would you say to them?" to "",
        "A kinder, truer way to put it" to "",
    )
    val answers = remember { mutableStateOf(List(prompts.size) { "" }) }
    var kept by remember { mutableStateOf(false) }
    ToolScreen("Look at a thought again", onBack) {
        item {
            SoftCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text("“Men are disturbed not by things, but by the views which they take of things.”", style = MaterialTheme.typography.bodyLarge)
                    Text("— Epictetus", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 6.dp))
                }
            }
        }
        item { Text("Take each question slowly. Nothing here is saved unless you tap Keep.", style = MaterialTheme.typography.bodySmall) }
        prompts.forEachIndexed { i, (q, hint) ->
            item {
                OutlinedTextField(
                    value = answers.value[i],
                    onValueChange = { v -> answers.value = answers.value.toMutableList().also { it[i] = v }; kept = false },
                    label = { Text(q) },
                    placeholder = { if (hint.isNotEmpty()) Text(hint) },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp),
                )
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = {
                        prefs.addLifelineNote(answers.value.last().trim())
                        kept = true
                    },
                    enabled = answers.value.last().isNotBlank() && !kept,
                ) { Text(if (kept) "Kept in My lifeline" else "Keep the kinder thought") }
                TextButton(onClick = { answers.value = List(prompts.size) { "" }; kept = false }) { Text("Clear") }
            }
        }
    }
}

/** My lifeline: a letter to yourself, kept thoughts, what helps, and a few of your favourites. */
@Composable
fun LifelineScreen(prefs: QuotePrefs, repo: QuoteRepository, onBack: () -> Unit) {
    var letter by rememberSaveable { mutableStateOf(prefs.lifelineLetter) }
    var helps by rememberSaveable { mutableStateOf(prefs.lifelineHelps) }
    var people by rememberSaveable { mutableStateOf(prefs.lifelinePeople) }
    var places by rememberSaveable { mutableStateOf(prefs.lifelinePlaces) }
    var reasons by rememberSaveable { mutableStateOf(prefs.lifelineReasons) }
    var notes by remember { mutableStateOf(prefs.lifelineNotes) }
    var saved by remember { mutableStateOf(false) }
    val favourites = remember { repo.favorites().shuffled().take(3) }
    ToolScreen("My lifeline", onBack) {
        item { Text("Things to hold on to, in your own words. They stay on this phone.", style = MaterialTheme.typography.bodyMedium) }
        item {
            SettingsBlock("A letter to myself") {
                Text("Write it on a good day, for a hard one.", style = MaterialTheme.typography.bodySmall)
                OutlinedTextField(value = letter, onValueChange = { letter = it; saved = false }, modifier = Modifier.fillMaxWidth().heightIn(min = 120.dp))
            }
        }
        item {
            SettingsBlock("What helps me") {
                OutlinedTextField(value = helps, onValueChange = { helps = it; saved = false }, label = { Text("Things that help me feel better") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = people, onValueChange = { people = it; saved = false }, label = { Text("People I can reach out to") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = places, onValueChange = { places = it; saved = false }, label = { Text("Places that calm me") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = reasons, onValueChange = { reasons = it; saved = false }, label = { Text("What keeps me going") }, modifier = Modifier.fillMaxWidth())
            }
        }
        item {
            Button(onClick = {
                prefs.lifelineLetter = letter
                prefs.lifelineHelps = helps
                prefs.lifelinePeople = people
                prefs.lifelinePlaces = places
                prefs.lifelineReasons = reasons
                saved = true
            }, enabled = !saved, modifier = Modifier.fillMaxWidth()) { Text(if (saved) "Saved" else "Save") }
        }
        if (notes.isNotEmpty()) {
            item { Text("Thoughts I kept", style = MaterialTheme.typography.titleMedium) }
            notes.forEachIndexed { i, note ->
                item(key = "note-$i-${note.hashCode()}") {
                    SoftCard(Modifier.fillMaxWidth()) {
                        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(note, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                            TextButton(onClick = { prefs.removeLifelineNote(i); notes = prefs.lifelineNotes }) { Text("Remove") }
                        }
                    }
                }
            }
        }
        if (favourites.isNotEmpty()) {
            item { Text("From your favourites", style = MaterialTheme.typography.titleMedium) }
            favourites.forEach { q ->
                item {
                    SoftCard(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp)) {
                            q.title?.let { Text(it, fontWeight = FontWeight.SemiBold) }
                            Text(q.shownText)
                            q.byline?.let { Text("— $it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsBlock(title: String, content: @Composable () -> Unit) {
    SoftCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(title.uppercase(), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            content()
        }
    }
}

internal fun notesFromJson(raw: String?): List<String> = try {
    val a = JSONArray(raw ?: "[]")
    (0 until a.length()).map { a.getString(it) }
} catch (e: Exception) {
    emptyList()
}

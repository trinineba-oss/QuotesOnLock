package com.trinineba.quotesonlock

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.json.JSONArray
import org.json.JSONObject

/** One step of a guided session: words to say, then seconds of quiet. [longOnly] steps are skipped in the short version. */
data class GuideStep(val text: String, val pause: Int, val longOnly: Boolean)

/** A guided session from res/raw/guided.txt. */
data class Guide(
    val key: String,
    val title: String,
    val subtitle: String,
    val shortMinutes: Int,
    val longMinutes: Int,
    val sound: Soundscape?,
    val steps: List<GuideStep>,
) {
    /** The steps of the short or long version; the long one doubles every pause. */
    fun steps(long: Boolean): List<GuideStep> =
        if (long) steps.map { it.copy(pause = it.pause * 2) } else steps.filter { !it.longOnly }
}

object Guides {
    @Volatile private var cache: List<Guide>? = null

    fun parse(lines: Sequence<String>): List<Guide> {
        val out = mutableListOf<Guide>()
        var head: List<String>? = null
        val steps = mutableListOf<GuideStep>()
        fun flush() {
            val h = head ?: return
            out += Guide(h[1], h[2], h[3], h[4].toIntOrNull() ?: 5, h[5].toIntOrNull() ?: 10, Soundscape.fromKey(h.getOrNull(6)), steps.toList())
            steps.clear()
        }
        lines.map { it.trim() }.filter { it.isNotEmpty() && !it.startsWith("#") }.forEach { line ->
            if (line.startsWith("S|")) {
                flush()
                head = line.split('|')
            } else {
                val pause = line.substringBefore('|')
                val long = pause.startsWith("L:")
                steps += GuideStep(line.substringAfter('|'), pause.removePrefix("L:").toIntOrNull() ?: 5, long)
            }
        }
        flush()
        return out
    }

    fun all(context: Context): List<Guide> = cache ?: context.applicationContext.resources.openRawResource(R.raw.guided)
        .bufferedReader().useLines { parse(it) }.also { cache = it }

    fun byKey(context: Context, key: String): Guide? = all(context).firstOrNull { it.key == key }
}

/**
 * What the user found helpful: an optional "better / same / worse" after a Calm tool, kept on the phone (and in
 * backups), so the check-in can suggest what works for this person first.
 */
object CalmLog {
    data class Entry(val time: Long, val tool: String, val feeling: String?, val result: Int)

    /** The feeling picked in the check-in, remembered for half an hour so the next outcome can be linked to it. */
    var feeling: Feeling? = null
        private set
    private var feelingAt = 0L

    fun pick(feeling: Feeling?) {
        this.feeling = feeling
        feelingAt = System.currentTimeMillis()
    }

    private fun currentFeeling(): String? =
        feeling?.takeIf { System.currentTimeMillis() - feelingAt < 30 * 60_000L }?.key

    fun entries(context: Context): List<Entry> = runCatching {
        val arr = JSONArray(QuotePrefs(context).raw.getString("calm_log", "[]"))
        (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            Entry(o.optLong("t"), o.optString("tool"), o.optString("f").ifBlank { null }, o.optInt("r"))
        }
    }.getOrDefault(emptyList())

    /** [result]: 1 better, 0 the same, -1 worse. */
    fun record(context: Context, tool: String, result: Int) {
        val kept = entries(context).takeLast(499) + Entry(System.currentTimeMillis(), tool, currentFeeling(), result)
        val arr = JSONArray()
        kept.forEach { e -> arr.put(JSONObject().put("t", e.time).put("tool", e.tool).put("f", e.feeling ?: "").put("r", e.result)) }
        QuotePrefs(context).raw.edit().putString("calm_log", arr.toString()).apply()
    }

    /** A tool's name for the screen, from its key ("guide:bodyscan", "breathe", …). */
    fun name(context: Context, tool: String): String = when {
        tool.startsWith("guide:") -> Guides.byKey(context, tool.removePrefix("guide:"))?.title ?: "A guided session"
        tool.startsWith("practice:") -> Practices.byKey(tool.removePrefix("practice:"))?.title ?: "A practice"
        else -> when (tool) {
            "breathe" -> "Breathe"
            "ground" -> "Ground yourself"
            "reframe" -> "Look at a thought"
            "sounds" -> "Sounds"
            "winddown" -> "Wind down"
            "lifeline" -> "My lifeline"
            else -> tool
        }
    }

    /** Tools that ask "how do you feel now?" when you leave them. */
    fun rated(tool: String): Boolean = tool.startsWith("guide:") || tool.startsWith("practice:") ||
        tool in setOf("breathe", "ground", "reframe", "winddown")

    /** For each tool, how often it helped and how often it was rated, over the last [days]. */
    fun helped(context: Context, days: Int = 30): List<Triple<String, Int, Int>> {
        val since = System.currentTimeMillis() - days * 86_400_000L
        return entries(context).filter { it.time >= since }.groupBy { it.tool }
            .map { (tool, list) -> Triple(tool, list.count { it.result > 0 }, list.size) }
            .sortedWith(compareByDescending<Triple<String, Int, Int>> { (it.second + 1f) / (it.third + 2f) }.thenByDescending { it.third })
    }

    /** What to suggest for a feeling: a sensible order, re-ranked by what has helped this person before. */
    fun suggestions(context: Context, feeling: Feeling?): List<String> {
        val base = when (feeling) {
            Feeling.ANXIOUS -> listOf("guide:breathing", "breathe", "guide:wave", "guide:safeplace", "ground", "guide:tension")
            Feeling.SAD -> listOf("guide:compassion", "guide:kindness", "guide:gratitude", "words", "guide:breathing", "sounds")
            Feeling.GRIEVING -> listOf("words", "guide:compassion", "guide:kindness", "sounds", "guide:breathing")
            Feeling.LONELY -> listOf("guide:kindness", "guide:compassion", "words", "lifeline", "sounds")
            Feeling.OVERWHELMED -> listOf("guide:breathing", "guide:wave", "guide:mountain", "ground", "guide:leaves", "guide:tension", "breathe")
            Feeling.ANGRY -> listOf("breathe", "guide:wave", "guide:tension", "guide:walking", "reframe")
            Feeling.NUMB -> listOf("ground", "guide:walking", "guide:bodyscan", "sounds", "words")
            Feeling.SLEEP -> listOf("winddown", "guide:sleep", "guide:backtosleep", "guide:bodyscan", "sounds")
            null -> listOf("sounds", "guide:morning", "guide:gratitude", "guide:bodyscan", "guide:leaves", "guide:breathing", "winddown")
        }
        val log = entries(context).filter { feeling == null || it.feeling == feeling.key }
        return base.withIndex().sortedByDescending { (i, tool) ->
            val mine = log.filter { it.tool == tool || it.tool.startsWith("$tool:") }
            val score = (mine.count { it.result > 0 } + 1f) / (mine.size + 2f)
            score - i * 0.04f
        }.map { it.value }
    }

    /** "Helped you 4 of 5 times", once a tool has been rated at least twice for this feeling. */
    fun helpedLine(context: Context, tool: String, feeling: Feeling?): String? {
        val mine = entries(context).filter { (it.tool == tool || it.tool.startsWith("$tool:")) && (feeling == null || it.feeling == feeling.key) }
        if (mine.size < 2) return null
        return "Helped you ${mine.count { it.result > 0 }} of ${mine.size} times"
    }
}

/** The check-in at the top of Calm: how are you, and a suggestion of what might help. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CheckInCard(onOpen: (String) -> Unit, onWords: (Feeling) -> Unit) {
    val context = LocalContext.current
    var feeling by remember { mutableStateOf(CalmLog.feeling) }
    var quiet by remember { mutableStateOf(false) }
    SettingsCard("How are you right now?") {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Feeling.entries.forEach { f ->
                FilterChip(
                    selected = f == feeling,
                    onClick = { feeling = if (feeling == f) null else f; quiet = false; CalmLog.pick(feeling) },
                    label = { Text(f.label) },
                )
            }
            FilterChip(selected = quiet, onClick = { quiet = !quiet; feeling = null; CalmLog.pick(null) }, label = { Text("Just want quiet") })
        }
        if (feeling != null || quiet) {
            val tools = remember(feeling, quiet) { CalmLog.suggestions(context, feeling) }
            val first = tools.first()
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.weight(1f)) {
                    Text("Try ${suggestionName(context, first, feeling)}", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    Text(
                        CalmLog.helpedLine(context, first, feeling) ?: suggestionWhy(first),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Button(onClick = { if (first == "words") feeling?.let(onWords) else onOpen(first) }) { Text("Start") }
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.Center) {
                Text("Or:", style = MaterialTheme.typography.bodySmall, modifier = Modifier.align(Alignment.CenterVertically))
                tools.drop(1).forEach { t ->
                    TextButton(onClick = { if (t == "words") feeling?.let(onWords) else onOpen(t) }) { Text(suggestionName(context, t, feeling)) }
                }
            }
        }
    }
}

private fun suggestionName(context: Context, tool: String, feeling: Feeling?): String =
    if (tool == "words") "Gentle words" + (feeling?.let { "" } ?: "") else CalmLog.name(context, tool)

private fun suggestionWhy(tool: String): String = when (tool) {
    "guide:breathing" -> "Three minutes, read aloud"
    "guide:bodyscan" -> "Resting attention through the body"
    "guide:tension" -> "Tense, then let go"
    "guide:kindness" -> "Kind wishes, for you and others"
    "guide:sleep" -> "A slow wind-down to drift off with"
    "guide:morning" -> "Arriving in the day, kindly"
    "guide:compassion" -> "Kindness towards yourself, on a hard day"
    "guide:leaves" -> "Let thoughts float by on a stream"
    "guide:wave" -> "Let a strong feeling rise and pass"
    "guide:backtosleep" -> "For waking in the night"
    "guide:walking" -> "A slow walk, step by step"
    "guide:mountain" -> "Steady as a mountain, whatever the weather"
    "guide:gratitude" -> "A few quiet minutes of thanks"
    "guide:safeplace" -> "Rest in a calm place of your own"
    "breathe" -> "A calm circle to breathe with"
    "ground" -> "Five things you can see, four you can touch…"
    "reframe" -> "What would you tell a friend?"
    "sounds" -> "Rain, waves, fire, piano"
    "winddown" -> "A dim screen, slow reading, sounds that fade"
    "words" -> "Lines chosen for this feeling"
    "lifeline" -> "Your letter, kept thoughts, what helps"
    else -> ""
}

/** The guided sessions, as a row of cards on the Calm page. */
@Composable
fun GuideRow(onOpen: (String) -> Unit) {
    val context = LocalContext.current
    val guides = remember { Guides.all(context) }
    LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        items(guides.size, key = { guides[it].key }) { i ->
            val g = guides[i]
            SoftCard(onClick = { onOpen("guide:" + g.key) }, modifier = Modifier.width(150.dp).height(108.dp)) {
                Column(Modifier.padding(14.dp)) {
                    Text(g.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    Text(g.subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
                    Spacer(Modifier.weight(1f))
                    Text("${g.shortMinutes} or ${g.longMinutes} min", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                }
            }
        }
    }
}

/** The sounds offered under a guided session or Wind down. */
private val UNDER = listOf(Soundscape.RAIN, Soundscape.WAVES, Soundscape.STREAM, Soundscape.FIRE, Soundscape.FOREST, Soundscape.BOWL, Soundscape.BROWN)

/**
 * A guided session read aloud by the phone's voice, a little slower than usual, with the step being spoken shown
 * large and a calm sound underneath at its own volume. It can be paused (it resumes from the same step).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun GuideScreen(guide: Guide, onBack: () -> Unit, onFinished: () -> Unit) {
    val context = LocalContext.current
    var long by rememberSaveable { mutableStateOf(false) }
    var started by rememberSaveable { mutableStateOf(false) }
    var paused by rememberSaveable { mutableStateOf(false) }
    var from by rememberSaveable { mutableIntStateOf(0) }
    var under by rememberSaveable { mutableStateOf(guide.sound?.key ?: "") }
    val speaking by QuoteVoice.speaking.collectAsState()
    val reading by QuoteVoice.reading.collectAsState()
    val sound by SoundPlayer.state.collectAsState()
    val steps = remember(long) { guide.steps(long) }
    val key = "guide:${guide.key}"
    // Where each step starts in the whole script, so the voice's position tells us the step.
    val starts = remember(steps) { steps.runningFold(0) { acc, s -> acc + s.text.length + 1 }.dropLast(1) }
    val current = reading?.first?.let { pos -> starts.indexOfLast { it <= pos }.coerceAtLeast(0) } ?: from
    var startedSound by remember { mutableStateOf(false) }
    // The furthest step the voice has reached (the highlight clears when it finishes).
    var reached by remember { mutableIntStateOf(0) }
    LaunchedEffect(current) { if (started) reached = maxOf(reached, current) }

    fun speakFrom(index: Int) {
        val parts = steps.withIndex().drop(index).map { (i, s) ->
            QuoteVoice.Segment(s.text, IntArray(s.text.length) { starts[i] + it }, s.pause * 1000L)
        }
        QuoteVoice.speakParts(context, key, parts, slower = 0.88f, duckSounds = false)
    }

    fun start() {
        reached = from
        started = true
        paused = false
        val snd = Soundscape.fromKey(under)
        if (snd != null && SoundPlayer.state.value.layers.isEmpty()) {
            SoundPlayer.playMix(context, listOf(SoundLayer(snd, 0.45f)), minutes = 0)
            startedSound = true
        }
        speakFrom(from)
    }

    // The session ends when the voice has said the last step.
    LaunchedEffect(speaking) {
        if (started && !paused && speaking == null && reached >= steps.lastIndex) {
            started = false
            from = 0
            if (startedSound) SoundPlayer.stop(context)
            onFinished()
        }
    }
    DisposableEffect(Unit) {
        onDispose {
            if (QuoteVoice.speaking.value == key) QuoteVoice.stop()
            if (startedSound) SoundPlayer.stop(context)
        }
    }
    BackHandler { QuoteVoice.stop(); onBack() }

    ToolScreen(guide.title, { QuoteVoice.stop(); onBack() }) {
        item { Text(guide.subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        if (!started) {
            item {
                SettingsCard("Length") {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(selected = !long, onClick = { long = false; from = 0 }, label = { Text("About ${guide.shortMinutes} min") })
                        FilterChip(selected = long, onClick = { long = true; from = 0 }, label = { Text("About ${guide.longMinutes} min") })
                    }
                }
            }
            item {
                SettingsCard("Sound underneath") {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        FilterChip(selected = under.isEmpty(), onClick = { under = "" }, label = { Text("None") })
                        UNDER.forEach { s -> FilterChip(selected = under == s.key, onClick = { under = s.key }, label = { Text(s.label) }) }
                    }
                    if (sound.layers.isNotEmpty() && !sound.forBreathe) {
                        Text("Your sounds are already playing; they'll carry on underneath.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            item {
                Text(
                    "Read aloud by your phone's own voice, a little slower than usual. Find a comfortable place; you can close your eyes once it begins.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            item { Button(onClick = { start() }, modifier = Modifier.fillMaxWidth().height(52.dp)) { Text(if (from > 0) "Carry on" else "Begin") } }
        } else {
            item { Spacer(Modifier.height(40.dp)) }
            item {
                Text(
                    steps.getOrNull(current)?.text.orEmpty(),
                    fontFamily = FontFamily.Serif,
                    fontSize = 22.sp,
                    lineHeight = 32.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                )
            }
            item { Spacer(Modifier.height(24.dp)) }
            item {
                LinearProgressIndicator(progress = { (current + 1f) / steps.size }, modifier = Modifier.fillMaxWidth())
            }
            sound.layers.firstOrNull()?.takeIf { !sound.forBreathe }?.let { layer ->
                item {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(layer.sound.label, style = MaterialTheme.typography.bodySmall, modifier = Modifier.width(110.dp))
                        Slider(value = layer.volume, onValueChange = { SoundPlayer.setLayerVolume(context, layer.sound, it) }, valueRange = 0.05f..1f, modifier = Modifier.weight(1f))
                    }
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    Button(onClick = {
                        if (paused) { paused = false; speakFrom(from) } else { paused = true; from = current; QuoteVoice.stop() }
                    }, modifier = Modifier.weight(1f)) { Text(if (paused) "Carry on" else "Pause") }
                    OutlinedButton(onClick = { QuoteVoice.stop(); started = false; from = 0; if (startedSound) SoundPlayer.stop(context); onBack() }, modifier = Modifier.weight(1f)) { Text("End") }
                }
            }
        }
    }
}

/** The passages read in Wind down (by their opening words; the text itself comes from the collection, verbatim). */
internal val WIND_DOWN = listOf(
    "Gradually the village murmur subsided",
    "It has always been a happy thought to me that the creek runs on all night",
    "On tranquil nights I gaze upon the moon",
    "And must we always look on the moon and the blossoms",
    "Far up the Pilot Peak Ridge",
    "O there is blessing in this gentle breeze",
    "When once the sun sinks in the west",
    "I have the happiness of the passing moment",
    "If the day and the night are such that you greet them with joy",
    "And so this memorable month ends",
    "When I get tired of reciting prayers",
    "Of the flowing river the flood ever changeth",
    "Among a thousand clouds and ten thousand streams",
    "It is the autumn season now, a silence in the woods",
    "Hush'd on the hill",
    "Ne'er saw I, never felt, a calm so deep",
    "It was kind of solemn, drifting down the big, still river",
    "The stars are ranged in rows, the night bright and deep",
    "Peace flows into me",
    "Alone in the night\nOn a dark hill",
    "A thing of beauty is a joy for ever",
    "Yet still to lounge with friends in the soft grass",
    "The sea is calm to-night",
    "To fling my arms wide",
    "Rises to vision the majesty of gods",
    "Now a breeze comes too and stirs the treetops",
    "There is a way of looking at our life daily as an escape",
    "Beyond a wholesome discipline",
    "When you are old and gray and full of sleep",
    "\"And with joy the stars perform their shining",
    "A setting sun, a rising moon?",
    "Our birth is but a sleep and a forgetting",
)

/**
 * Wind down for sleep: the screen goes very dim, peaceful passages are read slowly, one after another, and the
 * sounds fade out at the end of the timer.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun WindDownScreen(repo: QuoteRepository, onBack: () -> Unit) {
    val context = LocalContext.current
    val prefs = remember { QuotePrefs(context) }
    val passages = remember {
        val all = repo.bundled()
        WIND_DOWN.mapNotNull { start -> all.firstOrNull { it.text.startsWith(start) } }
    }
    var minutes by rememberSaveable { mutableIntStateOf(prefs.raw.getInt("winddown_minutes", 30)) }
    var reading by rememberSaveable { mutableStateOf(true) }
    var under by rememberSaveable { mutableStateOf(prefs.raw.getString("winddown_sound", Soundscape.RAIN.key) ?: Soundscape.RAIN.key) }
    var started by rememberSaveable { mutableStateOf(false) }
    val speaking by QuoteVoice.speaking.collectAsState()
    val range by QuoteVoice.reading.collectAsState()
    val sound by SoundPlayer.state.collectAsState()
    val key = "winddown"
    val starts = remember(passages) { passages.runningFold(0) { acc, q -> acc + q.text.length + 1 }.dropLast(1) }
    val current = range?.first?.let { pos -> starts.indexOfLast { it <= pos }.coerceAtLeast(0) }
    var now by remember { androidx.compose.runtime.mutableLongStateOf(android.os.SystemClock.elapsedRealtime()) }
    LaunchedEffect(started) {
        while (started) {
            now = android.os.SystemClock.elapsedRealtime()
            kotlinx.coroutines.delay(1000)
        }
    }

    fun begin() {
        started = true
        prefs.raw.edit().putInt("winddown_minutes", minutes).putString("winddown_sound", under).apply()
        val snd = Soundscape.fromKey(under)
        when {
            sound.layers.isNotEmpty() && !sound.forBreathe -> SoundPlayer.playMix(context, sound.layers, minutes = minutes)
            snd != null -> SoundPlayer.playMix(context, listOf(SoundLayer(snd, 0.6f)), minutes = minutes)
        }
        if (reading && passages.isNotEmpty()) {
            val parts = passages.mapIndexed { i, q ->
                val text = q.text.replace("\n", " ")
                QuoteVoice.Segment(text, IntArray(text.length) { starts[i] + it }, 9_000L)
            }
            QuoteVoice.speakParts(context, key, parts, slower = 0.8f, duckSounds = false)
        }
    }
    DisposableEffect(Unit) { onDispose { if (QuoteVoice.speaking.value == key) QuoteVoice.stop() } }
    BackHandler { QuoteVoice.stop(); onBack() }

    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = if (started) 0.78f else 0.45f))) {
        ToolScreen("Wind down", { QuoteVoice.stop(); onBack() }) {
            if (!started) {
                item {
                    Text(
                        "The screen goes dim, a few peaceful passages are read slowly, and the sounds fade away when the time is up.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                item {
                    SettingsCard("Sounds fade out after") {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(15, 30, 45, 60, 90).forEach { m -> FilterChip(selected = minutes == m, onClick = { minutes = m }, label = { Text("$m min") }) }
                        }
                    }
                }
                item {
                    SettingsCard("Sound") {
                        if (sound.layers.isNotEmpty() && !sound.forBreathe) {
                            Text("Your mix carries on: ${SoundMix.label(sound.layers)}", style = MaterialTheme.typography.bodySmall)
                        } else {
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                FilterChip(selected = under.isEmpty(), onClick = { under = "" }, label = { Text("None") })
                                (UNDER + Soundscape.TENT + Soundscape.SATIE).forEach { s ->
                                    FilterChip(selected = under == s.key, onClick = { under = s.key }, label = { Text(s.label) })
                                }
                            }
                        }
                    }
                }
                item {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().clickable { reading = !reading }) {
                        Text("Read passages aloud", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                        androidx.compose.material3.Switch(checked = reading, onCheckedChange = { reading = it })
                    }
                }
                item { Button(onClick = { begin() }, modifier = Modifier.fillMaxWidth().height(52.dp)) { Text("Begin") } }
            } else {
                item { Spacer(Modifier.height(60.dp)) }
                val q = current?.let { passages.getOrNull(it) }
                if (q != null && speaking == key) {
                    item {
                        Text(
                            "“" + q.text.replace("\\n", "\n") + "”",
                            fontFamily = FontFamily.Serif,
                            fontSize = 19.sp,
                            lineHeight = 29.sp,
                            color = Color(0xFFE6DFCB),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    item { Text("— " + (q.author ?: ""), fontStyle = FontStyle.Italic, color = Color(0xFFB8B09C), style = MaterialTheme.typography.bodyMedium) }
                } else {
                    item {
                        Text(
                            if (sound.layers.isEmpty()) "Goodnight." else "Let the sounds carry you.",
                            fontFamily = FontFamily.Serif,
                            fontSize = 22.sp,
                            color = Color(0xFFE6DFCB),
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
                item { Spacer(Modifier.height(30.dp)) }
                item {
                    Text(
                        soundTimeLeft(sound, now)?.let { "Sounds fade out · $it" } ?: "",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF9C968A),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                        OutlinedButton(onClick = { QuoteVoice.stop(); SoundPlayer.stop(context); onBack() }, modifier = Modifier.weight(1f)) { Text("Stop") }
                        OutlinedButton(onClick = { SoundPlayer.extend(context) }, modifier = Modifier.weight(1f)) { Text("+15 min") }
                    }
                }
            }
        }
    }
}

/** "How do you feel now?" after a Calm tool: one optional tap. */
@Composable
fun FeelNowDialog(tool: String, onDone: () -> Unit) {
    val context = LocalContext.current
    AlertDialog(
        onDismissRequest = onDone,
        title = { Text("How do you feel now?") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("After ${CalmLog.name(context, tool)}. Optional; it helps Calm suggest what works for you.", style = MaterialTheme.typography.bodyMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(1 to "Better", 0 to "The same", -1 to "Worse").forEach { (r, label) ->
                        OutlinedButton(onClick = { CalmLog.record(context, tool, r); onDone() }) { Text(label) }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDone) { Text("Skip") } },
    )
}

/** What has helped, over the last month, once there's enough to show. */
@Composable
fun WhatHelpsCard() {
    val context = LocalContext.current
    val rows = remember { CalmLog.helped(context) }
    if (rows.sumOf { it.third } < 3) return
    SettingsCard("What helps you") {
        Text("Over the last 30 days. Kept only on this phone, and in your backups.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        rows.take(5).forEach { (tool, better, all) ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(CalmLog.name(context, tool), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                Box(Modifier.width(90.dp).height(6.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant)) {
                    Box(Modifier.fillMaxWidth(better.toFloat() / all).height(6.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary))
                }
                Text("  $better of $all", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

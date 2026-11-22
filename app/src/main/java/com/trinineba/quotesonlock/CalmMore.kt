package com.trinineba.quotesonlock

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.view.HapticFeedbackConstants
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import kotlinx.coroutines.delay
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

private val Night = Color(0xFF0A1024)
private val Mist = Color(0xFFB9C6E6)
private val Glow = Color(0xFF9FB8FF)

// ---------------------------------------------------------------------------------------------
// It's too much right now: one tap into slow breathing, no choices to make.
// ---------------------------------------------------------------------------------------------

/**
 * A dark screen with a slowly growing and shrinking circle: in for four counts, out for six (a longer out-breath
 * calms the body). A soft vibration marks each change so it works with the eyes closed. After about a minute it
 * offers, never forces, a next step: [onTool] opens "breathe", "ground", "lifeline" or "sounds".
 */
@Composable
fun SosScreen(onBack: () -> Unit, onTool: (String) -> Unit) {
    BackHandler(onBack = onBack)
    val view = LocalView.current
    DisposableEffect(Unit) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }
    var round by rememberSaveable { mutableIntStateOf(0) }
    var breathing by rememberSaveable { mutableStateOf(true) }
    var inhale by remember { mutableStateOf(true) }
    var count by remember { mutableIntStateOf(1) }
    LaunchedEffect(breathing, round) {
        if (!breathing) return@LaunchedEffect
        repeat(6) {
            inhale = true
            view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
            for (c in 1..4) { count = c; delay(1000) }
            inhale = false
            view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
            for (c in 1..6) { count = c; delay(1000) }
        }
        breathing = false
    }
    val scale by animateFloatAsState(
        targetValue = if (inhale) 1f else 0.55f,
        animationSpec = tween(if (inhale) 4000 else 6000, easing = LinearEasing),
        label = "breath",
    )
    Box(
        Modifier.fillMaxSize().background(Night)
            .then(if (breathing) Modifier.clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClickLabel = "Stop") { onBack() } else Modifier)
            .statusBarsPadding().navigationBarsPadding().padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        if (breathing) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("You're safe to just breathe. I'll count.", color = Mist, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
                Spacer(Modifier.height(36.dp))
                Box(Modifier.size(240.dp), contentAlignment = Alignment.Center) {
                    Box(
                        Modifier.size(240.dp).scale(scale).clip(CircleShape)
                            .background(Brush.radialGradient(listOf(Glow.copy(alpha = 0.35f), Glow.copy(alpha = 0.08f))))
                            .border(2.dp, Glow.copy(alpha = 0.45f), CircleShape),
                    )
                    Text(if (inhale) "Breathe in…" else "Breathe out…", color = Color.White, fontFamily = FontFamily.Serif, fontSize = 28.sp)
                }
                Spacer(Modifier.height(24.dp))
                Text("$count", color = Color.White, fontFamily = FontFamily.Serif, fontSize = 44.sp)
                Spacer(Modifier.height(24.dp))
                Text("The phone gives a soft buzz as each breath changes.\nKeep your eyes closed if you like.", color = Mist, style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center)
            }
            Text("Tap anywhere to stop", color = Mist.copy(alpha = 0.7f), style = MaterialTheme.typography.labelMedium, modifier = Modifier.align(Alignment.BottomCenter))
        } else {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("That's one minute.", color = Color.White, fontFamily = FontFamily.Serif, fontSize = 26.sp)
                Text("However you feel now is all right.", color = Mist, style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(10.dp))
                Button(onClick = { round++; breathing = true }) { Text("Keep breathing") }
                OutlinedButton(onClick = { onTool("ground") }) { Text("Ground yourself: 5 things you see") }
                OutlinedButton(onClick = { onTool("lifeline") }) { Text("Open my lifeline") }
                OutlinedButton(onClick = { onTool("sounds") }) { Text("Some quiet sound") }
                TextButton(onClick = onBack) { Text("Back to Calm") }
                Spacer(Modifier.height(16.dp))
                Text(
                    "If you might hurt yourself, please call your local emergency number or someone you trust now.",
                    color = Mist, style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center,
                )
            }
        }
    }
}

/** The plain button at the top of Calm. */
@Composable
fun SosButton(onClick: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp))
            .background(Brush.linearGradient(listOf(Color(0xFF3B5BA8), Color(0xFF5A3F8C))))
            .clickable(onClickLabel = "Breathe now", onClick = onClick).padding(horizontal = 16.dp, vertical = 14.dp),
    ) {
        Text("It's too much right now ›", color = Color.White, fontFamily = FontFamily.Serif, fontSize = 19.sp)
        Text("One tap. Nothing to choose.", color = Color.White.copy(alpha = 0.85f), style = MaterialTheme.typography.bodySmall)
    }
}

// ---------------------------------------------------------------------------------------------
// Slow looking: a few quiet minutes with one painting of a nature scene.
// ---------------------------------------------------------------------------------------------

/** One painted scene and its steps; [quote] and [quoteBy] close the session. */
class LookScene(val key: String, val title: String, val place: String, val credit: String, val quote: String, val quoteBy: String, val steps: List<Pair<String, String>>) {
    val minutes: Int get() = (steps.sumOf { it.second.split(' ').size } / 60 + steps.size / 2).coerceAtLeast(3)
}

object Looking {
    @Volatile private var all: List<LookScene>? = null

    fun parse(lines: Sequence<String>): List<LookScene> {
        val out = mutableListOf<LookScene>()
        var head: List<String>? = null
        var steps = mutableListOf<Pair<String, String>>()
        fun flush() { head?.let { h -> out += LookScene(h[1], h[2], h[3], h[4], h[5], h[6], steps) }; head = null; steps = mutableListOf() }
        lines.filter { it.isNotBlank() && !it.startsWith("#") }.forEach { line ->
            val f = line.split('|')
            when (f[0]) {
                "V" -> { flush(); head = f }
                "S" -> if (f.size >= 3) steps += f[1] to f[2]
            }
        }
        flush()
        return out
    }

    fun all(context: Context): List<LookScene> = all ?: synchronized(this) {
        all ?: context.applicationContext.resources.openRawResource(R.raw.looking).bufferedReader().useLines { parse(it) }.also { all = it }
    }

    fun image(key: String): Int = when (key) {
        "tetons" -> R.drawable.look_tetons
        "maroon" -> R.drawable.look_maroon
        "bigsur" -> R.drawable.look_bigsur
        else -> R.drawable.look_monument
    }
}

/** Calm's "Slow looking" rows: a small picture, the title and where it is. */
@Composable
fun LookingRows(onOpen: (String) -> Unit) {
    val context = LocalContext.current
    val scenes = remember { runCatching { Looking.all(context) }.getOrDefault(emptyList()) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        scenes.forEach { s ->
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.surfaceContainerHigh)
                    .clickable(onClickLabel = "Look at ${s.title}") { onOpen(s.key) }.padding(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Image(
                    painterResource(Looking.image(s.key)), contentDescription = null, contentScale = ContentScale.Crop,
                    modifier = Modifier.width(72.dp).height(54.dp).clip(RoundedCornerShape(10.dp)),
                )
                Column(Modifier.padding(start = 12.dp).weight(1f)) {
                    Text(s.title, style = MaterialTheme.typography.titleSmall)
                    Text("${s.place} · ${s.minutes} min", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

/**
 * The painting fills the screen and drifts very slowly from side to side; one step at a time is shown and read
 * aloud. The last step adds a line from the collection about such places.
 */
@Composable
fun LookScreen(scene: LookScene, onBack: () -> Unit, onFinished: () -> Unit) {
    BackHandler(onBack = onBack)
    val context = LocalContext.current
    val view = LocalView.current
    var step by rememberSaveable(scene.key) { mutableIntStateOf(0) }
    var voice by rememberSaveable { mutableStateOf(true) }
    val drift = rememberInfiniteTransition(label = "drift")
    val bias by drift.animateFloat(-1f, 1f, infiniteRepeatable(tween(60_000, easing = LinearEasing), RepeatMode.Reverse), label = "bias")
    val (label, text) = scene.steps[step]
    val last = step == scene.steps.lastIndex
    val voiceKey = "look-${scene.key}"
    DisposableEffect(Unit) {
        view.keepScreenOn = true
        onDispose {
            view.keepScreenOn = false
            if (QuoteVoice.speaking.value == voiceKey) QuoteVoice.stop()
        }
    }
    LaunchedEffect(step, voice) {
        if (voice) {
            val parts = listOf(QuoteVoice.Segment(text, null, 900)) +
                if (last) listOf(QuoteVoice.Segment(scene.quote, null, 0)) else emptyList()
            QuoteVoice.speakParts(context, voiceKey, parts, slower = 0.9f)
        } else if (QuoteVoice.speaking.value == voiceKey) QuoteVoice.stop()
    }
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        Image(
            painterResource(Looking.image(scene.key)), contentDescription = scene.title,
            contentScale = ContentScale.Crop, alignment = BiasAlignment(bias, 0f), modifier = Modifier.fillMaxSize(),
        )
        Box(
            Modifier.align(Alignment.TopStart).statusBarsPadding().padding(12.dp).size(44.dp).clip(CircleShape)
                .background(Color.Black.copy(alpha = 0.35f)).clickable(onClickLabel = "Back", onClick = onBack),
            contentAlignment = Alignment.Center,
        ) { Text("←", color = Color.White, fontSize = 22.sp) }
        Column(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                .background(Brush.verticalGradient(listOf(Color.Transparent, Night.copy(alpha = 0.92f), Night)))
                .navigationBarsPadding().padding(start = 20.dp, end = 20.dp, top = 48.dp, bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("STEP ${step + 1} OF ${scene.steps.size} · ${label.uppercase()}", color = Color(0xFFF2C94C), style = MaterialTheme.typography.labelSmall, letterSpacing = 1.3.sp)
            Text(text, color = Color.White, fontFamily = FontFamily.Serif, fontSize = 18.sp, lineHeight = 27.sp)
            if (last) {
                Text("“${scene.quote}”", color = Color(0xFFF6E2A0), fontFamily = FontFamily.Serif, fontStyle = FontStyle.Italic, fontSize = 15.sp, lineHeight = 22.sp)
                Text("— ${scene.quoteBy}", color = Mist, style = MaterialTheme.typography.bodySmall)
                Text("${scene.place} · ${scene.credit}", color = Mist.copy(alpha = 0.7f), style = MaterialTheme.typography.labelSmall)
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { if (step > 0) step-- else onBack() }) { Text(if (step > 0) "‹ Back" else "‹ Leave", color = Mist) }
                TextButton(onClick = { voice = !voice }) { Text(if (voice) "■ Quiet" else "▶ Read aloud", color = Mist) }
                TextButton(onClick = { if (last) onFinished() else step++ }) { Text(if (last) "Finish" else "Next ›", color = Color(0xFFF6E2A0)) }
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Put the worry down: write worries down, and come back to them at a worry time.
// ---------------------------------------------------------------------------------------------

class Worry(val id: Long, val text: String)

/** The worries kept for later, and the optional daily reminder at the worry time. Nothing leaves the phone. */
object Worries {
    private const val WORK_NAME = "worry-time"
    private const val CHANNEL_ID = "worry"
    private const val NOTIFICATION_ID = 4312

    fun all(prefs: QuotePrefs): List<Worry> = runCatching {
        val a = JSONArray(prefs.worries ?: "[]")
        (0 until a.length()).map { i -> a.getJSONObject(i).let { Worry(it.getLong("id"), it.getString("text")) } }
    }.getOrDefault(emptyList())

    private fun save(prefs: QuotePrefs, list: List<Worry>) {
        val a = JSONArray()
        list.forEach { a.put(JSONObject().put("id", it.id).put("text", it.text)) }
        prefs.worries = a.toString()
    }

    fun add(prefs: QuotePrefs, text: String) { if (text.isNotBlank()) save(prefs, all(prefs) + Worry(System.currentTimeMillis(), text.trim())) }

    fun remove(prefs: QuotePrefs, id: Long) = save(prefs, all(prefs).filter { it.id != id })

    /** Schedules the next reminder at the worry time, or cancels it when there is none. Safe to call often. */
    fun schedule(context: Context) {
        val app = context.applicationContext
        val prefs = QuotePrefs(app)
        val work = WorkManager.getInstance(app)
        if (prefs.worryHour < 0) { work.cancelUniqueWork(WORK_NAME); return }
        val request = OneTimeWorkRequestBuilder<WorryWorker>()
            .setInitialDelay(DailyQuote.millisUntil(prefs.worryHour, prefs.worryMinute), TimeUnit.MILLISECONDS)
            .build()
        work.enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.REPLACE, request)
    }

    /** The gentle reminder, only when something is waiting. */
    fun remind(context: Context) {
        val app = context.applicationContext
        val n = all(QuotePrefs(app)).size
        if (n == 0) return
        val channel = NotificationChannel(CHANNEL_ID, "Worry time", NotificationManager.IMPORTANCE_DEFAULT).apply {
            description = "A gentle reminder at the worry time you chose"
        }
        app.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        val open = PendingIntent.getActivity(
            app, 9,
            Intent(app, MainActivity::class.java).putExtra(MainActivity.EXTRA_OPEN_WORRY, true)
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val body = (if (n == 1) "1 thing is waiting." else "$n things are waiting.") + " Fifteen minutes, then let them rest."
        val notification = NotificationCompat.Builder(app, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_quote)
            .setContentTitle("Your worry time")
            .setContentText(body)
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        runCatching { NotificationManagerCompat.from(app).notify(NOTIFICATION_ID, notification) }
    }
}

class WorryWorker(context: Context, params: WorkerParameters) : Worker(context, params) {
    override fun doWork(): Result {
        Worries.remind(applicationContext)
        Worries.schedule(applicationContext)
        return Result.success()
    }
}

/** Write a worry down; see what is kept; choose the worry time; or start it now. */
@Composable
fun WorryScreen(prefs: QuotePrefs, onBack: () -> Unit, onStart: () -> Unit, askNotifications: () -> Unit) {
    val context = LocalContext.current
    var list by remember { mutableStateOf(Worries.all(prefs)) }
    var draft by rememberSaveable { mutableStateOf("") }
    var hour by remember { mutableIntStateOf(prefs.worryHour) }
    var minute by remember { mutableIntStateOf(prefs.worryMinute) }
    ToolScreen("Put the worry down", onBack) {
        item {
            Text(
                "Write down what's on your mind. You don't have to solve it now: it will be kept here until your worry time, when you can look at it properly.",
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        item {
            OutlinedTextField(value = draft, onValueChange = { draft = it }, modifier = Modifier.fillMaxWidth(), placeholder = { Text("What's worrying you?") }, minLines = 2)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(enabled = draft.isNotBlank(), onClick = { Worries.add(prefs, draft); draft = ""; list = Worries.all(prefs) }) { Text("+ Put it down") }
            }
        }
        if (list.isNotEmpty()) {
            item { Text("KEPT FOR LATER · ${list.size}", style = MaterialTheme.typography.labelMedium, letterSpacing = 1.2.sp, color = MaterialTheme.colorScheme.primary) }
            list.forEach { w ->
                item(key = "w-${w.id}") {
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surfaceContainerHigh).padding(start = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(w.text, fontFamily = FontFamily.Serif, fontSize = 15.sp, modifier = Modifier.weight(1f).padding(vertical = 10.dp))
                        TextButton(onClick = { Worries.remove(prefs, w.id); list = Worries.all(prefs) }) { Text("✕") }
                    }
                }
            }
            item { Button(onClick = onStart, modifier = Modifier.fillMaxWidth()) { Text("Look at them now") } }
        }
        item { Text("MY WORRY TIME", style = MaterialTheme.typography.labelMedium, letterSpacing = 1.2.sp, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 8.dp)) }
        item {
            SettingsCard("A gentle reminder") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "Once a day, if anything is waiting. Fifteen minutes is enough; then let them rest until tomorrow.",
                        style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f),
                    )
                    Switch(checked = hour >= 0, onCheckedChange = { on ->
                        if (on) { hour = 18; minute = 0; askNotifications() } else hour = -1
                        prefs.worryHour = hour; prefs.worryMinute = minute
                        Worries.schedule(context)
                    })
                }
                if (hour >= 0) {
                    OutlinedButton(onClick = {
                        android.app.TimePickerDialog(context, { _, h, m ->
                            hour = h; minute = m
                            prefs.worryHour = h; prefs.worryMinute = m
                            Worries.schedule(context)
                        }, hour, minute, android.text.format.DateFormat.is24HourFormat(context)).show()
                    }) {
                        val cal = java.util.Calendar.getInstance().apply { set(java.util.Calendar.HOUR_OF_DAY, hour); set(java.util.Calendar.MINUTE, minute) }
                        Text("Every day at " + android.text.format.DateFormat.getTimeFormat(context).format(cal.time))
                    }
                }
            }
        }
        item {
            Text(
                "Setting worries aside until a set time is a technique from cognitive behavioural therapy. Nothing you write here leaves the phone.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Worry time: one worry at a time. Does it still matter? Is there one small thing to do? Then let it go, or keep it. */
@Composable
fun WorryTimeScreen(prefs: QuotePrefs, onBack: () -> Unit) {
    val worries = remember { Worries.all(prefs) }
    var index by rememberSaveable { mutableIntStateOf(0) }
    var feeling by rememberSaveable(index) { mutableStateOf<String?>(null) }
    var action by rememberSaveable(index) { mutableStateOf("") }
    var toNotes by rememberSaveable(index) { mutableStateOf(false) }
    val seneca = "We suffer more often in imagination than in reality."
    fun next(letGo: Boolean) {
        val w = worries[index]
        if (toNotes && action.isNotBlank()) {
            val now = System.currentTimeMillis()
            Notes.put(prefs, Note(now, "To do", "☐ ${action.trim()}\n\n(From worry time: ${w.text})", emptyList(), false, now))
        }
        if (letGo) Worries.remove(prefs, w.id)
        index++
    }
    ToolScreen("Worry time", onBack) {
        if (worries.isEmpty() || index >= worries.size) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(top = 24.dp)) {
                    Text(if (worries.isEmpty()) "Nothing is waiting." else "That's all for today.", fontFamily = FontFamily.Serif, fontSize = 24.sp)
                    Text("Let them rest now. Whatever is still there can wait until tomorrow's worry time.", style = MaterialTheme.typography.bodyMedium)
                    Text("“$seneca”", fontFamily = FontFamily.Serif, fontStyle = FontStyle.Italic, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 8.dp))
                    Text("— Seneca", style = MaterialTheme.typography.bodySmall)
                    Button(onClick = onBack, modifier = Modifier.padding(top = 12.dp)) { Text("Done") }
                }
            }
            return@ToolScreen
        }
        val w = worries[index]
        item { Text("${index + 1} of ${worries.size} · about 15 minutes in all", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        item {
            Text(
                w.text, fontFamily = FontFamily.Serif, fontSize = 19.sp,
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.surfaceContainerHigh).padding(14.dp),
            )
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Does it still worry you?", style = MaterialTheme.typography.titleSmall)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("Not really", "A bit", "A lot").forEach { f ->
                        androidx.compose.material3.FilterChip(selected = feeling == f, onClick = { feeling = f }, label = { Text(f) })
                    }
                }
                if (feeling == "Not really") Text("Then perhaps it can go. Worries often shrink when we look at them in daylight.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Is there one small thing you can do about it?", style = MaterialTheme.typography.titleSmall)
                OutlinedTextField(value = action, onValueChange = { action = it }, modifier = Modifier.fillMaxWidth(), placeholder = { Text("One small step, or leave empty") })
                if (action.isNotBlank()) Row(verticalAlignment = Alignment.CenterVertically) {
                    androidx.compose.material3.Checkbox(checked = toNotes, onCheckedChange = { toNotes = it })
                    Text("Add to Notes as a to-do", style = MaterialTheme.typography.bodyMedium)
                }
                if (feeling == "A lot" && action.isBlank()) Text(
                    "If it can't be solved, it may help to write about it in your journal, or to tell someone you trust.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(top = 6.dp)) {
                Button(onClick = { next(letGo = true) }) { Text("Let it go ✓") }
                OutlinedButton(onClick = { next(letGo = false) }) { Text("Keep it for next time") }
            }
        }
    }
}

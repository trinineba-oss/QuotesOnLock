package com.trinineba.quotesonlock

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontFamily
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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONArray
import java.util.Calendar
import java.util.Locale
import java.util.concurrent.TimeUnit

/** One affirmation: a first-person line from the collection, word for word, with its credit. */
class Affirmation(val text: String, val by: String)

/** A set of affirmations for one need. */
class AffirmSet(val key: String, val emoji: String, val name: String, val forWhat: String, val lines: List<Affirmation>)

/**
 * Words to stand on: affirmations drawn from real lines (res/raw/affirmations.txt), the ones you keep, and your own,
 * which are saved as your own quotes so they can appear on the lock screen. A morning affirmation is optional.
 */
object Affirm {
    const val MINE = "My affirmation"
    private const val WORK_NAME = "affirm_morning"
    private const val CHANNEL_ID = "affirm_morning"
    private const val NOTIFICATION_ID = 4711
    @Volatile private var cached: List<AffirmSet>? = null

    fun parse(lines: Sequence<String>): List<AffirmSet> {
        val out = mutableListOf<AffirmSet>()
        var head: List<String>? = null
        var items = mutableListOf<Affirmation>()
        fun flush() {
            head?.let { h -> out += AffirmSet(h[1], h[2], h[3], h[4], items) }
            head = null; items = mutableListOf()
        }
        lines.filter { it.isNotBlank() && !it.startsWith("#") }.forEach { line ->
            val f = line.split('|')
            when (f[0]) {
                "S" -> { flush(); if (f.size >= 5) head = f }
                "L" -> if (f.size >= 3) items += Affirmation(f[1], f[2])
            }
        }
        flush()
        return out
    }

    fun sets(context: Context): List<AffirmSet> = cached ?: synchronized(this) {
        cached ?: context.applicationContext.resources.openRawResource(R.raw.affirmations).bufferedReader().useLines { parse(it) }.also { cached = it }
    }

    /** How the line looks on screen: the collection writes line breaks as \n. */
    fun shown(text: String): String = text.replace("\\n", "\n")

    // The lines you keep from the sets, by their text.
    fun kept(prefs: QuotePrefs): List<String> = runCatching {
        val a = JSONArray(prefs.raw.getString("affirm_kept", "[]"))
        (0 until a.length()).map { a.getString(it) }
    }.getOrDefault(emptyList())

    fun toggleKeep(prefs: QuotePrefs, text: String) {
        val now = kept(prefs).let { if (text in it) it - text else it + text }
        prefs.raw.edit().putString("affirm_kept", JSONArray(now).toString()).apply()
    }

    /** Your own: the lines you kept, then the ones you wrote (your own quotes credited "My affirmation"). */
    fun mine(context: Context, prefs: QuotePrefs): List<Affirmation> {
        val all = sets(context).flatMap { it.lines }
        val keptLines = kept(prefs).mapNotNull { t -> all.firstOrNull { it.text == t } }
        val written = QuoteRepository(context).custom().filter { it.author == MINE }.map { Affirmation(it.text, "Written by you") }
        return keptLines + written
    }

    fun write(context: Context, text: String) {
        if (text.isBlank()) return
        QuoteRepository(context).addCustom(text.trim(), MINE)
    }

    /** Whether your own quotes (and so your written affirmations) can appear on the lock screen. */
    fun onLock(prefs: QuotePrefs): Boolean = Category.CUSTOM in prefs.enabledCategories

    fun setOnLock(prefs: QuotePrefs, on: Boolean) {
        prefs.enabledCategories = if (on) prefs.enabledCategories + Category.CUSTOM else prefs.enabledCategories - Category.CUSTOM
    }

    /** Today's line, the same all day, from all the sets. */
    fun today(context: Context, cal: Calendar = Calendar.getInstance()): Affirmation? {
        val all = sets(context).flatMap { it.lines }
        if (all.isEmpty()) return null
        val day = cal.get(Calendar.YEAR) * 400 + cal.get(Calendar.DAY_OF_YEAR)
        return all[Math.floorMod(day * 7919, all.size)]
    }

    // The morning affirmation, off by default (hour -1).
    fun morningHour(prefs: QuotePrefs): Int = prefs.raw.getInt("affirm_hour", -1)
    fun morningMinute(prefs: QuotePrefs): Int = prefs.raw.getInt("affirm_minute", 30)

    fun setMorning(context: Context, hour: Int, minute: Int) {
        QuotePrefs(context).raw.edit().putInt("affirm_hour", hour).putInt("affirm_minute", minute).apply()
        schedule(context)
    }

    fun schedule(context: Context) {
        val app = context.applicationContext
        val prefs = QuotePrefs(app)
        val work = WorkManager.getInstance(app)
        val hour = morningHour(prefs)
        if (hour < 0) { work.cancelUniqueWork(WORK_NAME); return }
        val request = OneTimeWorkRequestBuilder<AffirmWorker>()
            .setInitialDelay(DailyQuote.millisUntil(hour, morningMinute(prefs)), TimeUnit.MILLISECONDS)
            .build()
        work.enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.REPLACE, request)
    }

    fun notifyToday(context: Context) {
        val app = context.applicationContext
        val line = today(app) ?: return
        val channel = NotificationChannel(CHANNEL_ID, "Morning affirmation", NotificationManager.IMPORTANCE_DEFAULT).apply {
            description = "A quiet line to start the day, at the time you chose"
        }
        app.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        val open = PendingIntent.getActivity(
            app, 11,
            Intent(app, MainActivity::class.java).putExtra(MainActivity.EXTRA_OPEN_AFFIRM, true)
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val text = "“" + QuoteVoice.normal(line.text) + "” — " + line.by
        val notification = NotificationCompat.Builder(app, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_quote)
            .setContentTitle("Words to stand on")
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText("$text\nTap to say it with me."))
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        runCatching { NotificationManagerCompat.from(app).notify(NOTIFICATION_ID, notification) }
    }
}

class AffirmWorker(context: Context, params: WorkerParameters) : Worker(context, params) {
    override fun doWork(): Result {
        Affirm.notifyToday(applicationContext)
        Affirm.schedule(applicationContext)
        return Result.success()
    }
}

/** The card in Solace, under Unstuck. */
@Composable
fun AffirmCard(onClick: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp))
            .background(Brush.linearGradient(listOf(Color(0xFFC08A3A), Color(0xFF8A4A6A))))
            .clickable(onClickLabel = "Open Words to stand on", onClick = onClick).padding(horizontal = 16.dp, vertical = 14.dp),
    ) {
        Text("Words to stand on ›", color = Color.White, fontFamily = BookSerif, fontSize = 18.sp)
        Text("Affirmations from people who lived them. Say one, keep one, make your own.", color = Color.White.copy(alpha = 0.88f), style = MaterialTheme.typography.bodySmall)
    }
}

/** The optional line on Today. */
@Composable
fun AffirmTodayCard(onOpen: () -> Unit) {
    val context = LocalContext.current
    val line = remember { runCatching { Affirm.today(context) }.getOrNull() } ?: return
    val shown = Affirm.shown(line.text)
    FoldingCard("Today's affirmation", "fold_affirm", shown, "Say it with me", onOpen) {
        Text(shown, fontFamily = BookSerif, fontSize = 17.sp, maxLines = 4, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
        Text("${line.by} · say it with me ›", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private enum class AffirmStage { SETS, LINES, SAY, WRITE }

/**
 * Words to stand on: choose a need, read one line at a time, say it with the voice three times, keep it,
 * or rewrite it in your own words. [startWithToday] opens straight into saying today's line (from the notification).
 */
@Composable
fun AffirmScreen(prefs: QuotePrefs, onBack: () -> Unit, onFinished: () -> Unit, startWithToday: Boolean = false, askNotifications: () -> Unit = {}) {
    val context = LocalContext.current
    val sets = remember { runCatching { Affirm.sets(context) }.getOrDefault(emptyList()) }
    var stage by rememberSaveable { mutableStateOf(if (startWithToday) AffirmStage.SAY else AffirmStage.SETS) }
    var setKey by rememberSaveable { mutableStateOf<String?>(null) }
    var index by rememberSaveable { mutableIntStateOf(0) }
    var sayText by rememberSaveable { mutableStateOf(if (startWithToday) Affirm.today(context)?.text ?: "" else "") }
    var sayBy by rememberSaveable { mutableStateOf(if (startWithToday) Affirm.today(context)?.by ?: "" else "") }
    var refresh by remember { mutableIntStateOf(0) }
    val mine = remember(refresh) { Affirm.mine(context, prefs) }
    val lines: List<Affirmation> = if (setKey == "mine") mine else sets.firstOrNull { it.key == setKey }?.lines.orEmpty()
    val setName = if (setKey == "mine") "My own" else sets.firstOrNull { it.key == setKey }?.name ?: ""

    when (stage) {
        AffirmStage.SETS -> ToolScreen("Words to stand on", onBack) {
            item {
                Text("What do you need today?", fontFamily = BookSerif, fontSize = 24.sp)
                Text("Each line was first said by someone who needed it too.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            sets.forEach { s ->
                item(key = "s-${s.key}") { NeedCard(s.emoji, s.name, s.forWhat) { setKey = s.key; index = 0; stage = AffirmStage.LINES } }
            }
            item(key = "s-mine") {
                NeedCard("💛", "My own", if (mine.isEmpty()) "the ones you keep or write" else "the ones you've kept or written · ${mine.size}") {
                    if (mine.isEmpty()) { sayText = ""; sayBy = ""; stage = AffirmStage.WRITE } else { setKey = "mine"; index = 0; stage = AffirmStage.LINES }
                }
            }
            item { TextButton(onClick = { sayText = ""; sayBy = ""; stage = AffirmStage.WRITE }) { Text("✎ Write my own") } }
            item { AffirmSettings(prefs, askNotifications) }
        }

        AffirmStage.LINES -> {
            BackHandler { stage = AffirmStage.SETS }
            val pager = rememberPagerState(initialPage = index.coerceIn(0, (lines.size - 1).coerceAtLeast(0))) { lines.size }
            LaunchedEffect(pager.currentPage) { index = pager.currentPage }
            ToolScreen("$setName · ${if (lines.isEmpty()) 0 else pager.currentPage + 1} of ${lines.size}", onBack = { stage = AffirmStage.SETS }) {
                item {
                    HorizontalPager(pager, Modifier.fillMaxWidth().heightIn(min = 300.dp)) { p ->
                        val a = lines[p]
                        Column(
                            Modifier.fillMaxWidth().padding(horizontal = 2.dp).clip(RoundedCornerShape(22.dp))
                                .background(Brush.radialGradient(listOf(Color(0xFF3B4F8C), Color(0xFF1B2140))))
                                .heightIn(min = 300.dp).padding(horizontal = 22.dp, vertical = 30.dp),
                            verticalArrangement = Arrangement.Center,
                        ) {
                            Text("“${Affirm.shown(a.text)}”", color = Color.White, fontFamily = BookSerif, fontSize = if (a.text.length > 160) 20.sp else 25.sp, lineHeight = if (a.text.length > 160) 29.sp else 34.sp)
                            Text(a.by, color = Color.White.copy(alpha = 0.75f), style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 12.dp))
                        }
                    }
                }
                item {
                    val a = lines.getOrNull(pager.currentPage)
                    if (a != null) {
                        val isKept = remember(refresh, a.text) { a.text in Affirm.kept(prefs) }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Button(onClick = { sayText = a.text; sayBy = a.by; stage = AffirmStage.SAY }) { Text("▶ Say it with me") }
                            if (a.by != "Written by you") OutlinedButton(onClick = { Affirm.toggleKeep(prefs, a.text); refresh++ }) { Text(if (isKept) "♥ Kept" else "♡ Keep") }
                        }
                        TextButton(onClick = { sayText = a.text; sayBy = a.by; stage = AffirmStage.WRITE }) { Text("✎ Make it mine") }
                    }
                }
                item {
                    Text(
                        "Swipe for the next. Saying it aloud, slowly, three times, is enough. Notice which word you lean on.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        AffirmStage.SAY -> SayWithMe(
            text = sayText,
            by = sayBy,
            onDone = { if (startWithToday && setKey == null) onFinished() else stage = if (setKey == null) AffirmStage.SETS else AffirmStage.LINES },
        )

        AffirmStage.WRITE -> {
            BackHandler { stage = if (setKey == null) AffirmStage.SETS else AffirmStage.LINES }
            var draft by rememberSaveable(sayText) { mutableStateOf("") }
            var saved by remember { mutableStateOf(false) }
            ToolScreen("Make it mine", onBack = { stage = if (setKey == null) AffirmStage.SETS else AffirmStage.LINES }) {
                if (sayText.isNotBlank()) item {
                    Text("From ${sayBy.substringBefore(',')}:", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("“${Affirm.shown(sayText)}”", fontFamily = BookSerif, fontSize = 16.sp, modifier = Modifier.padding(top = 4.dp))
                }
                item {
                    Text("In your own words, as true for you today:", style = MaterialTheme.typography.bodyMedium)
                    OutlinedTextField(draft, { draft = it; saved = false }, modifier = Modifier.fillMaxWidth().padding(top = 6.dp), minLines = 3, placeholder = { Text("I …") })
                    Text("Start with “I”, keep it in the present tense, and make it something you can believe.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 6.dp))
                }
                item {
                    Button(enabled = draft.isNotBlank() && !saved, onClick = { Affirm.write(context, draft); saved = true; refresh++ }) { Text(if (saved) "Saved to My own" else "Save to My own") }
                }
                item { AffirmSettings(prefs, askNotifications) }
            }
        }
    }
}

@Composable
private fun NeedCard(emoji: String, title: String, sub: String, onClick: () -> Unit) {
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

/** Lock screen, morning notification and the line on Today. */
@Composable
private fun AffirmSettings(prefs: QuotePrefs, askNotifications: () -> Unit) {
    val context = LocalContext.current
    var lock by remember { mutableStateOf(Affirm.onLock(prefs)) }
    var hour by remember { mutableIntStateOf(Affirm.morningHour(prefs)) }
    var minute by remember { mutableIntStateOf(Affirm.morningMinute(prefs)) }
    var onToday by remember { mutableStateOf(prefs.raw.getBoolean("affirm_today", false)) }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 8.dp)) {
        Text("USE MY AFFIRMATIONS", style = MaterialTheme.typography.labelMedium, letterSpacing = 1.2.sp, color = MaterialTheme.colorScheme.primary)
        SwitchRow("Show on the lock screen", "The ones you write join your own quotes, in with the rest", lock) { lock = it; Affirm.setOnLock(prefs, it) }
        SwitchRow("A morning affirmation", if (hour >= 0) String.format(Locale.US, "A quiet notification at %d:%02d", hour, minute) else "A quiet notification with today's line", hour >= 0) { on ->
            hour = if (on) 7 else -1; minute = 30
            if (on) askNotifications()
            Affirm.setMorning(context, hour, minute)
        }
        if (hour >= 0) Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf(6 to 30, 7 to 30, 8 to 30, 9 to 30).forEach { (h, m) ->
                OutlinedButton(onClick = { hour = h; minute = m; Affirm.setMorning(context, h, m) }) {
                    Text(String.format(Locale.US, "%d:%02d", h, m), color = if (h == hour) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
                }
            }
        }
        SwitchRow("Today's affirmation on Today", "A small card under the quote", onToday) { onToday = it; prefs.raw.edit().putBoolean("affirm_today", it).apply() }
    }
}

@Composable
private fun SwitchRow(title: String, sub: String, on: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .clickable { onChange(!on) }.padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = on, onCheckedChange = onChange)
    }
}

/** Say it with me: the voice reads the line, then a breathing ring gives you time to say it aloud; three rounds. */
@Composable
private fun SayWithMe(text: String, by: String, onDone: () -> Unit) {
    val context = LocalContext.current
    val view = LocalView.current
    DisposableEffect(Unit) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false; QuoteVoice.stop() }
    }
    BackHandler(onBack = onDone)
    var round by remember { mutableIntStateOf(1) }
    var yourTurn by remember { mutableStateOf(false) }
    var paused by remember { mutableStateOf(false) }
    var finished by remember { mutableStateOf(false) }
    val key = "affirm:" + text.hashCode()
    LaunchedEffect(paused, text) {
        if (paused || text.isBlank()) return@LaunchedEffect
        while (round <= 3) {
            yourTurn = false
            QuoteVoice.speakParts(context, key, listOf(QuoteVoice.Segment(text, null, 0L)), slower = 0.9f)
            withTimeoutOrNull(4_000) { QuoteVoice.speaking.first { it == key } }
            withTimeoutOrNull(60_000) { QuoteVoice.speaking.first { it != key } }
            yourTurn = true
            delay((2_500L + 75L * QuoteVoice.normal(text).length).coerceAtMost(14_000L))
            if (round == 3) break
            round++
        }
        finished = true
    }
    val pulse = rememberInfiniteTransition(label = "say")
    val s by pulse.animateFloat(0.9f, 1.06f, infiniteRepeatable(tween(3200), RepeatMode.Reverse), label = "s")
    val accent = MaterialTheme.colorScheme.primary
    Scaffold { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            if (finished) {
                Text("🌱", fontSize = 40.sp)
                Text("Carry it with you today.", fontFamily = BookSerif, fontSize = 22.sp, modifier = Modifier.padding(top = 8.dp, bottom = 18.dp))
            } else {
                Text(
                    if (yourTurn) "Round $round of 3 · your turn" else "Round $round of 3 · listen",
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Box(
                    Modifier.padding(vertical = 22.dp).size(190.dp).scale(if (yourTurn && !paused) s else 1f).clip(CircleShape)
                        .border(3.dp, accent.copy(alpha = 0.6f), CircleShape)
                        .background(Brush.radialGradient(listOf(accent.copy(alpha = 0.18f), Color.Transparent))),
                    contentAlignment = Alignment.Center,
                ) { Text(if (yourTurn) "say it aloud" else "listen", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
            Text(Affirm.shown(text), fontFamily = BookSerif, fontSize = 21.sp, lineHeight = 30.sp, textAlign = TextAlign.Center)
            if (by.isNotBlank()) Text(by, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp))
            Spacer(Modifier.height(24.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (!finished) OutlinedButton(onClick = { paused = !paused; if (paused) QuoteVoice.stop() }) { Text(if (paused) "Carry on" else "Pause") }
                Button(onClick = onDone) { Text("Done") }
            }
        }
    }
}

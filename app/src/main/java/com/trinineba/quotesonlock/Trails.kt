package com.trinineba.quotesonlock

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.json.JSONObject
import java.util.Calendar

// ---------------------------------------------------------------------------------------------
// Curiosity trails: hand-made paths from idea to idea, each step joined by one sentence.
// ---------------------------------------------------------------------------------------------

/** One trail: its stops are great ideas (by title), each with the thread that leads to it from the stop before. */
class Trail(val id: String, val title: String, val subtitle: String, val tint: Long, val stops: List<Pair<String, String>>) {
    val minutes: Int get() = (stops.size * 1.4f).toInt().coerceAtLeast(3)
}

object Trails {
    @Volatile private var all: List<Trail>? = null

    fun parse(lines: Sequence<String>): List<Trail> {
        val out = mutableListOf<Trail>()
        var head: List<String>? = null
        var stops = mutableListOf<Pair<String, String>>()
        fun flush() {
            head?.let { h -> out += Trail(h[1], h[2], h[3], 0xFF000000 or (h.getOrNull(4)?.toLongOrNull(16) ?: 0x5A6B8C), stops) }
            head = null; stops = mutableListOf()
        }
        lines.filter { it.isNotBlank() && !it.startsWith("#") }.forEach { line ->
            val f = line.split('|')
            when (f[0]) {
                "R" -> { flush(); head = f }
                "S" -> if (f.size >= 3) stops += f[1] to f[2]
            }
        }
        flush()
        return out
    }

    fun all(context: Context): List<Trail> = all ?: synchronized(this) {
        all ?: context.applicationContext.resources.openRawResource(R.raw.trails).bufferedReader().useLines { parse(it) }.also { all = it }
    }

    /** Every place an idea appears: (trail, index of the stop). */
    fun places(context: Context, ideaTitle: String): List<Pair<Trail, Int>> =
        all(context).flatMap { t -> t.stops.withIndex().filter { it.value.first == ideaTitle }.map { t to it.index } }

    /** How far you have got in each trail (the furthest stop seen), kept in prefs as JSON. */
    fun progress(prefs: QuotePrefs): Map<String, Int> = runCatching {
        val o = JSONObject(prefs.raw.getString("trail_progress", "{}") ?: "{}")
        o.keys().asSequence().associateWith { o.getInt(it) }
    }.getOrDefault(emptyMap())

    fun reached(prefs: QuotePrefs, id: String, index: Int) {
        val o = runCatching { JSONObject(prefs.raw.getString("trail_progress", "{}") ?: "{}") }.getOrDefault(JSONObject())
        if (o.optInt(id, -1) < index) {
            o.put(id, index)
            prefs.raw.edit().putString("trail_progress", o.toString()).apply()
        }
    }
}

@Composable
private fun TrailLabel(text: String, modifier: Modifier = Modifier) {
    Text(text.uppercase(), style = MaterialTheme.typography.labelMedium, letterSpacing = 1.2.sp, color = MaterialTheme.colorScheme.primary, modifier = modifier)
}

/** A card for a step: the idea's title and the sentence that leads to it. */
@Composable
private fun StepCard(title: String, bridge: String, onClick: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .clickable(onClickLabel = "Go to $title", onClick = onClick).padding(horizontal = 14.dp, vertical = 11.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Text("$title ›", fontFamily = BookSerif, fontSize = 16.sp)
        Text(bridge, fontFamily = BookSerif, fontStyle = FontStyle.Italic, fontSize = 13.sp, lineHeight = 19.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** "Where next?" at the end of an idea: the next stop in each trail it belongs to, and the trails themselves. */
@Composable
fun WhereNext(quote: Quote, onGo: () -> Unit = {}) {
    val title = quote.title ?: return
    val context = LocalContext.current
    val places = remember(title) { runCatching { Trails.places(context, title) }.getOrDefault(emptyList()) }
    if (places.isEmpty()) return
    val next = places.filter { (t, i) -> i < t.stops.size - 1 }.distinctBy { (t, i) -> t.stops[i + 1].first }.take(3)
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (next.isNotEmpty()) {
            TrailLabel("Where next?")
            next.forEach { (t, i) ->
                val (nextTitle, bridge) = t.stops[i + 1]
                StepCard(nextTitle, bridge) { onGo(); LibraryLinks.open.value = "trail:${t.id}/${i + 1}" }
            }
        }
        TrailLabel("Or follow a trail")
        places.distinctBy { it.first.id }.take(2).forEach { (t, i) ->
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Color(t.tint).copy(alpha = 0.35f))
                    .clickable(onClickLabel = "Follow ${t.title}") { onGo(); LibraryLinks.open.value = "trail:${t.id}/0" }
                    .padding(horizontal = 14.dp, vertical = 11.dp),
            ) {
                Text("${t.title} ›", fontFamily = BookSerif, fontSize = 16.sp)
                Text("${t.stops.size} stops · this is stop ${i + 1} · ${t.subtitle}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/** All the trails: the ones under way first, then the rest. */
@Composable
fun TrailsScreen(onBack: () -> Unit, onOpen: (String) -> Unit) {
    val context = LocalContext.current
    val prefs = remember { QuotePrefs(context) }
    val trails = remember { Trails.all(context) }
    var refresh by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) { refresh++ }
    val progress = remember(refresh) { Trails.progress(prefs) }
    val going = trails.filter { (progress[it.id] ?: -1) in 0 until it.stops.size - 1 }
    ToolScreen("Curiosity trails", onBack) {
        item {
            Text(
                "Follow one thread from idea to idea, across philosophy, science, myth and art. Each step says why it leads to the next.",
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (going.isNotEmpty()) {
            item { TrailLabel("Continue") }
            going.forEach { t ->
                val at = progress[t.id] ?: 0
                item(key = "c-${t.id}") { TrailRow(t, "${at + 1} of ${t.stops.size} · ${t.subtitle}", (at + 1f) / t.stops.size) { onOpen("trail:${t.id}/$at") } }
            }
        }
        item { TrailLabel(if (going.isEmpty()) "Start a trail" else "All trails") }
        trails.forEach { t ->
            val done = (progress[t.id] ?: -1) >= t.stops.size - 1
            item(key = "t-${t.id}") {
                TrailRow(t, (if (done) "✓ followed · " else "") + "${t.stops.size} stops · about ${t.minutes} min · ${t.subtitle}", null) { onOpen("trail:${t.id}/0") }
            }
        }
        item { Text("Or tap \"Where next?\" at the end of any great idea.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}

@Composable
private fun TrailRow(t: Trail, meta: String, fraction: Float?, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .clickable(onClickLabel = "Open ${t.title}", onClick = onClick),
    ) {
        Box(Modifier.width(6.dp).height(76.dp).background(Color(t.tint)))
        Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp).weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(t.title, fontFamily = BookSerif, fontSize = 16.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(meta, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
            fraction?.let { f ->
                Box(Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)).background(MaterialTheme.colorScheme.outlineVariant)) {
                    Box(Modifier.fillMaxWidth(f.coerceIn(0.05f, 1f)).height(4.dp).background(MaterialTheme.colorScheme.primary))
                }
            }
        }
    }
}

/** One trail, one stop at a time: the thread that led here, the idea itself, and the next stop. */
@Composable
fun TrailScreen(trailId: String, start: Int, onBack: () -> Unit, onOpenAuthor: (String) -> Unit, onOpen: (String) -> Unit) {
    val context = LocalContext.current
    val prefs = remember { QuotePrefs(context) }
    val trail = remember(trailId) { Trails.all(context).firstOrNull { it.id == trailId } }
    if (trail == null) { ToolScreen("Curiosity trails", onBack) { item { Text("This trail isn't available.") } }; return }
    val bundled = remember { QuoteRepository(context).bundled() }
    var at by rememberSaveable(trailId) { mutableIntStateOf(start.coerceIn(0, trail.stops.size - 1)) }
    val (title, bridge) = trail.stops[at]
    val idea = remember(title) { Learn.idea(bundled, title) }
    val prevIdea = remember(at) { trail.stops.getOrNull(at - 1)?.let { Learn.idea(bundled, it.first) } }
    LaunchedEffect(trailId, at) {
        Trails.reached(prefs, trailId, at)
        Reading.record(prefs, Reading.Place("trails", trailId, trail.title, "Curiosity trail · $title", "trail:$trailId/$at", at, trail.stops.size, System.currentTimeMillis()))
    }
    ToolScreen("", onBack) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                TrailLabel("Trail · ${trail.title}")
                Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    trail.stops.indices.forEach { i ->
                        Box(
                            Modifier.weight(1f).height(4.dp).clip(RoundedCornerShape(2.dp))
                                .background(if (i <= at) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant)
                                .clickable(onClickLabel = "Stop ${i + 1}") { at = i },
                        )
                    }
                }
                val from = prevIdea?.category?.group?.title
                val to = idea?.category?.group?.title
                Text(
                    "Stop ${at + 1} of ${trail.stops.size}" + if (from != null && to != null && from != to) " · $from → $to" else "",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        item { Text(bridge, fontFamily = BookSerif, fontStyle = FontStyle.Italic, fontSize = 16.sp, lineHeight = 24.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        item { Text(title, fontFamily = BookSerif, fontSize = 26.sp, lineHeight = 32.sp) }
        if (idea != null) {
            item { Text(idea.shownText, fontFamily = if (idea.explained) null else BookSerif, fontSize = 17.sp, lineHeight = 26.sp) }
            idea.byline?.let { by ->
                item {
                    val name = authorName(by)
                    Text(
                        "— $by" + if (name != null) " ›" else "", fontFamily = BookSerif, fontSize = 14.sp, color = MaterialTheme.colorScheme.primary,
                        modifier = if (name != null) Modifier.clickable { onOpenAuthor(name) } else Modifier,
                    )
                }
            }
            idea.brief?.let { item { Text("In brief: $it", style = MaterialTheme.typography.bodyMedium) } }
            idea.more?.let { item { MoreAbout(idea.text, it) } }
        }
        if (at < trail.stops.size - 1) {
            val (nextTitle, nextBridge) = trail.stops[at + 1]
            item { TrailLabel("Next stop", Modifier.padding(top = 8.dp)) }
            item { StepCard(nextTitle, nextBridge) { at++ } }
        } else {
            item {
                Column(
                    Modifier.fillMaxWidth().padding(top = 8.dp).clip(RoundedCornerShape(14.dp))
                        .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.45f), RoundedCornerShape(14.dp)).padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text("The end of the trail", fontFamily = BookSerif, fontSize = 18.sp)
                    Text("From ${trail.stops.first().first} to $title, in ${trail.stops.size} steps.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            val others = Trails.all(context).filter { it.id != trailId }.shuffled(java.util.Random((trailId.hashCode() + Calendar.getInstance().get(Calendar.DAY_OF_YEAR)).toLong())).take(2)
            item { TrailLabel("Another trail", Modifier.padding(top = 8.dp)) }
            others.forEach { t -> item(key = "o-${t.id}") { TrailRow(t, "${t.stops.size} stops · ${t.subtitle}", null) { onOpen("trail:${t.id}/0") } } }
            item { TextButton(onClick = onBack) { Text("Back") } }
        }
    }
}

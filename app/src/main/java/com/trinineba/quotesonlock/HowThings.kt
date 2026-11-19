package com.trinineba.quotesonlock

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** One everyday thing: [ref] is the title of its explanation in ideas.txt (category ideahow). */
class Thing(val ref: String, val label: String, val emoji: String)

class ThingSection(val title: String, val tint: Long, val things: List<Thing>)

/** How things work: everyday things explained, in sections (res/raw/things.txt). */
object Things {
    @Volatile private var sections: List<ThingSection>? = null

    fun parse(lines: Sequence<String>): List<ThingSection> {
        val out = mutableListOf<ThingSection>()
        var head: List<String>? = null
        var things = mutableListOf<Thing>()
        fun flush() { head?.let { h -> if (things.isNotEmpty()) out += ThingSection(h[1], 0xFF000000 or (h.getOrNull(2)?.toLongOrNull(16) ?: 0x8A6A3A), things) } }
        lines.filter { it.isNotBlank() && !it.startsWith("#") }.forEach { line ->
            val f = line.split('|')
            when (f[0]) {
                "G" -> { flush(); head = f; things = mutableListOf() }
                "S" -> if (f.size >= 3) things += Thing(f[1], f[2], f.getOrElse(3) { "" })
            }
        }
        flush()
        return out
    }

    fun sections(context: Context): List<ThingSection> = sections ?: synchronized(this) {
        sections ?: context.applicationContext.resources.openRawResource(R.raw.things).bufferedReader().useLines { parse(it) }.also { sections = it }
    }
}

/** The sections of everyday things, each a row of tiles; a tile opens the thing's page. */
@Composable
fun HowThingsScreen(onBack: () -> Unit, onOpenAuthor: (String) -> Unit, start: String? = null) {
    val context = LocalContext.current
    val prefs = remember { QuotePrefs(context) }
    val bundled = remember { QuoteRepository(context).bundled() }
    val sections = remember { Things.sections(context) }
    val all = remember(sections) { sections.flatMap { s -> s.things.map { s to it } } }
    var open by rememberSaveable { mutableStateOf(start?.let { r -> all.indexOfFirst { it.second.ref == r }.takeIf { it >= 0 } }) }
    var refresh by remember { mutableIntStateOf(0) }
    open?.let { i ->
        // A fresh screen for each thing, so it opens at the top.
        androidx.compose.runtime.key(i) {
            ThingScreen(all, i, prefs, bundled, onGo = { open = it }, onBack = { if (start != null) onBack() else { open = null; refresh++ } }, onOpenAuthor = onOpenAuthor)
        }
        return
    }
    val checked = remember(refresh) { Learn.items(prefs).keys }
    ToolScreen("How things work", onBack) {
        item {
            Text(
                "${all.size} everyday things, each explained in a few minutes, with who worked it out, a diagram and a question to check yourself." +
                    if (all.any { it.second.ref in checked }) " You've checked ${all.count { it.second.ref in checked }}." else "",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        sections.forEach { section ->
            item(key = "s-" + section.title) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(section.title.uppercase(), style = MaterialTheme.typography.labelMedium, color = Color(section.tint), letterSpacing = 1.2.sp)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(section.things, key = { it.ref }) { t ->
                            val done = t.ref in checked
                            Column(
                                Modifier.width(112.dp).height(104.dp).clip(RoundedCornerShape(16.dp))
                                    .background(Color(section.tint).copy(alpha = 0.12f))
                                    .then(if (done) Modifier.border(2.dp, Color(section.tint), RoundedCornerShape(16.dp)) else Modifier)
                                    .clickable(onClickLabel = "Open ${t.label}") { open = all.indexOfFirst { it.second.ref == t.ref } }
                                    .padding(12.dp),
                                verticalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Text(t.emoji, fontSize = 24.sp)
                                Text(
                                    t.label + if (done) " ✓" else "",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                }
            }
        }
        item {
            Text(
                "The explanations are written for the app. They also come up among the great ideas, and in Learn's lessons and reviews.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** One thing: the explanation, who and when, its diagram, the fuller story, and a question; previous and next. */
@Composable
private fun ThingScreen(
    all: List<Pair<ThingSection, Thing>>,
    index: Int,
    prefs: QuotePrefs,
    bundled: List<Quote>,
    onGo: (Int) -> Unit,
    onBack: () -> Unit,
    onOpenAuthor: (String) -> Unit,
) {
    val context = LocalContext.current
    val (section, thing) = all[index.coerceIn(0, all.size - 1)]
    val q = remember(thing.ref) { Learn.idea(bundled, thing.ref) }
    val lesson = remember(thing.ref) { Learn.lessonFor(context, thing.ref) }
    val tint = Color(section.tint)
    ToolScreen(section.title, onBack) {
        if (q == null) {
            item { Text("This page isn't available.") }
            return@ToolScreen
        }
        item(key = "t-" + thing.ref) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(thing.emoji + "  " + thing.ref, fontFamily = FontFamily.Serif, fontSize = 26.sp, lineHeight = 32.sp)
                q.byline?.let { by ->
                    val name = authorName(by)
                    Text(
                        if (name != null) "$by ›" else by,
                        fontFamily = FontFamily.Serif,
                        fontSize = 14.sp,
                        color = tint,
                        modifier = if (name != null) Modifier.clickable { onOpenAuthor(name) } else Modifier,
                    )
                }
            }
        }
        item(key = "x-" + thing.ref) { Text(q.text, style = MaterialTheme.typography.bodyLarge, lineHeight = 25.sp) }
        item(key = "d-" + thing.ref) { LessonDiagram(thing.ref) }
        q.more?.let { more ->
            item(key = "m-" + thing.ref) {
                var shown by rememberSaveable(thing.ref) { mutableStateOf(false) }
                if (!shown) {
                    TextButton(onClick = { shown = true }) { Text("More about this ›") }
                } else {
                    Text(more, style = MaterialTheme.typography.bodyMedium, lineHeight = 23.sp)
                }
            }
        }
        if (lesson != null) item(key = "q-" + thing.ref) {
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(tint.copy(alpha = 0.08f)).padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text("Check myself", style = MaterialTheme.typography.titleSmall, color = tint)
                QuestionBlock(lesson) { ok -> Learn.answer(prefs, thing.ref, ok) }
            }
        }
        item(key = "n-" + thing.ref) {
            Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { onGo(index - 1) }, enabled = index > 0) {
                    Text("‹ " + (all.getOrNull(index - 1)?.second?.label ?: ""), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Spacer(Modifier.weight(1f))
                Text("${index + 1} / ${all.size}", style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.weight(1f))
                TextButton(onClick = { onGo(index + 1) }, enabled = index < all.size - 1) {
                    Text((all.getOrNull(index + 1)?.second?.label ?: "") + " ›", maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

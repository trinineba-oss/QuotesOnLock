package com.trinineba.quotesonlock

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** One part of an essay: a paragraph written for the app, a quote from the collection, or an idea or myth. */
sealed class EssayPart {
    class Para(val text: String) : EssayPart()
    class Line(val text: String) : EssayPart()
    class Idea(val title: String) : EssayPart()
}

class Essay(val title: String, val voices: String, val tint: Long, val path: String, val parts: List<EssayPart>) {
    /** Reading time at about 200 words a minute, quotes included. */
    val minutes: Int get() = (parts.sumOf { p ->
        when (p) { is EssayPart.Para -> p.text.split(' ').size; is EssayPart.Line -> p.text.split(' ').size; is EssayPart.Idea -> 40 }
    } / 200).coerceAtLeast(1)
}

/** Essays (res/raw/essays.txt): short essays written for the app that weave the collection's lines together. */
object Essays {
    @Volatile private var all: List<Essay>? = null

    fun parse(lines: Sequence<String>): List<Essay> {
        val out = mutableListOf<Essay>()
        var head: List<String>? = null
        var parts = mutableListOf<EssayPart>()
        fun flush() { head?.let { h -> if (parts.isNotEmpty()) out += Essay(h[1], h.getOrElse(2) { "" }, 0xFF000000 or (h.getOrNull(3)?.toLongOrNull(16) ?: 0x8A6A3A), h.getOrElse(4) { "" }, parts) } }
        lines.filter { it.isNotBlank() && !it.startsWith("#") }.forEach { line ->
            val kind = line.substringBefore('|')
            val rest = line.substringAfter('|')
            when (kind) {
                "E" -> { flush(); head = line.split('|'); parts = mutableListOf() }
                "P" -> parts += EssayPart.Para(rest)
                "Q" -> parts += EssayPart.Line(rest.replace("\\n", "\n"))
                "I" -> parts += EssayPart.Idea(rest)
            }
        }
        flush()
        return out
    }

    fun all(context: Context): List<Essay> = all ?: synchronized(this) {
        all ?: context.applicationContext.resources.openRawResource(R.raw.essays).bufferedReader().useLines { parse(it) }.also { all = it }
    }

    fun resolve(bundled: List<Quote>, part: EssayPart): Quote? = when (part) {
        is EssayPart.Line -> bundled.firstOrNull { it.title == null && it.text == part.text }
        is EssayPart.Idea -> Learn.idea(bundled, part.title)
        is EssayPart.Para -> null
    }
}

/** The list of essays; tapping one opens it to read. */
@Composable
fun EssaysScreen(onBack: () -> Unit, onOpenAuthor: (String) -> Unit, onOpenPath: (String) -> Unit) {
    val context = LocalContext.current
    val essays = remember { Essays.all(context) }
    var open by rememberSaveable { mutableStateOf<String?>(null) }
    essays.firstOrNull { it.title == open }?.let { e ->
        EssayScreen(e, onBack = { open = null }, onOpenAuthor = onOpenAuthor, onOpenPath = onOpenPath)
        return
    }
    BackHandler(onBack = onBack)
    Scaffold { inner ->
        LazyColumn(
            Modifier.fillMaxSize().padding(inner),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { BackArrow(onBack) }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Essays", style = MaterialTheme.typography.headlineSmall)
                    Text(
                        "One question, many voices: short essays written for the app that bring the collection's lines into conversation across the centuries.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            itemsIndexed(essays, key = { _, e -> e.title }) { _, e ->
                Column(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Color(e.tint))
                        .clickable(onClickLabel = "Read ${e.title}") { open = e.title }.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(e.title, fontFamily = FontFamily.Serif, fontSize = 20.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
                    Text(e.voices, style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.9f))
                    Text("${e.minutes} min read", style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.85f))
                }
            }
        }
    }
}

/** One essay: paragraphs in a reading serif, the collection's lines as tappable cards, then the voices and where to go next. */
@Composable
private fun EssayScreen(essay: Essay, onBack: () -> Unit, onOpenAuthor: (String) -> Unit, onOpenPath: (String) -> Unit) {
    BackHandler(onBack = onBack)
    val context = LocalContext.current
    val bundled = remember { QuoteRepository(context).bundled() }
    var opened by remember { mutableStateOf<Quote?>(null) }
    opened?.let { q -> QuotePageSheet(quote = q, onDismiss = { opened = null }, onOpenAuthor = { opened = null; onOpenAuthor(it) }) }
    val tint = Color(essay.tint)
    val voices = remember(essay.title) {
        essay.parts.mapNotNull { p -> Essays.resolve(bundled, p) }.mapNotNull { q ->
            if (q.isIdea) q.title?.let { it to null } else q.byline?.let { by -> by to authorName(by) }
        }.distinctBy { (label, name) -> name ?: label }
    }
    Scaffold { inner ->
        LazyColumn(
            Modifier.fillMaxSize().padding(inner),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item { BackArrow(onBack) }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("ESSAY", style = MaterialTheme.typography.labelMedium, color = tint, letterSpacing = 1.2.sp)
                    Text(essay.title, fontFamily = FontFamily.Serif, fontSize = 28.sp, lineHeight = 34.sp)
                    Text(
                        "${essay.minutes} min · ${voices.size} voices · written for the app",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            itemsIndexed(essay.parts) { _, part ->
                when (part) {
                    is EssayPart.Para -> Text(part.text, fontFamily = FontFamily.Serif, fontSize = 17.sp, lineHeight = 27.sp)
                    else -> {
                        val q = Essays.resolve(bundled, part)
                        Row(
                            Modifier.fillMaxWidth().height(IntrinsicSize.Min).clip(RoundedCornerShape(12.dp))
                                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                                .clickable(enabled = q != null, onClickLabel = "Open this line") { opened = q },
                        ) {
                            Box(Modifier.width(4.dp).fillMaxHeight().background(tint))
                            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                if (q == null) {
                                    Text("This line is no longer in the collection.", style = MaterialTheme.typography.bodySmall)
                                } else {
                                    q.title?.let { Text(it, fontFamily = FontFamily.Serif, fontWeight = FontWeight.SemiBold, fontSize = 17.sp) }
                                    Text(
                                        if (q.isIdea) (q.brief ?: q.text) else q.shownText,
                                        fontFamily = FontFamily.Serif,
                                        fontStyle = if (q.isIdea) FontStyle.Normal else FontStyle.Italic,
                                        fontSize = 16.sp,
                                        lineHeight = 24.sp,
                                    )
                                    q.byline?.let { Text("$it ›", fontFamily = FontFamily.Serif, fontSize = 13.sp, color = tint) }
                                }
                            }
                        }
                    }
                }
            }
            item {
                Text("Voices in this essay", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp))
            }
            itemsIndexed(voices) { _, (label, name) ->
                Row(
                    Modifier.fillMaxWidth().clickable(enabled = name != null) { name?.let(onOpenAuthor) }.padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.size(30.dp).clip(CircleShape).background(tint.copy(alpha = 0.18f)), contentAlignment = Alignment.Center) {
                        Text(
                            label.split(' ', ',').filter { it.isNotBlank() && it.first().isUpperCase() }.take(2).joinToString("") { it.take(1) },
                            style = MaterialTheme.typography.labelSmall,
                            color = tint,
                        )
                    }
                    Text(label + if (name != null) " ›" else "", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(start = 10.dp))
                }
            }
            if (essay.path.isNotEmpty()) item {
                OutlinedButton(onClick = { onOpenPath(essay.path) }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                    Text("Keep going: the reading path ${essay.path}")
                }
            }
        }
    }
}

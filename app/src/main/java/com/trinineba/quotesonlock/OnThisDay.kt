package com.trinineba.quotesonlock

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Calendar
import java.util.Locale

/**
 * One thing that happened on a day of the year: [kind] "e" for an event, "b" born, "d" died. [author] names an author
 * page and [idea] a great idea, either may be empty.
 */
class DayEntry(val monthDay: String, val year: Int, val kind: String, val title: String, val text: String, val author: String, val idea: String) {
    val yearLabel: String get() = if (year < 0) "${-year} BC" else "$year"
}

object OnThisDay {
    @Volatile private var byDay: Map<String, List<DayEntry>>? = null

    fun parse(lines: Sequence<String>): List<DayEntry> = lines
        .filter { it.startsWith("D|") }
        .mapNotNull { l ->
            val f = l.split('|')
            if (f.size < 8) null else DayEntry(f[1], f[2].toIntOrNull() ?: return@mapNotNull null, f[3], f[4], f[5], f[6], f[7])
        }.toList()

    private fun index(context: Context): Map<String, List<DayEntry>> = byDay ?: synchronized(this) {
        byDay ?: context.applicationContext.resources.openRawResource(R.raw.onthisday).bufferedReader().useLines { parse(it) }
            .groupBy { it.monthDay }
            .mapValues { (_, list) -> list.sortedWith(compareBy({ "ebd".indexOf(it.kind) }, { it.year })) }
            .also { byDay = it }
    }

    fun key(cal: Calendar): String = String.format(Locale.US, "%02d-%02d", cal.get(Calendar.MONTH) + 1, cal.get(Calendar.DAY_OF_MONTH))

    fun on(context: Context, cal: Calendar = Calendar.getInstance()): List<DayEntry> = index(context)[key(cal)].orEmpty()

    fun label(cal: Calendar): String = "${cal.get(Calendar.DAY_OF_MONTH)} " + cal.getDisplayName(Calendar.MONTH, Calendar.LONG, Locale.UK)
}

/** The small card on Today, under the quote: the day's first entry, and how many more there are. */
@Composable
fun OnThisDayCard(onOpen: () -> Unit) {
    val context = LocalContext.current
    // Worked out afresh each time Today is shown, so the card turns over at midnight.
    val today = Calendar.getInstance()
    val day = OnThisDay.key(today)
    val entries = remember(day) { runCatching { OnThisDay.on(context, today) }.getOrDefault(emptyList()) }
    val first = entries.firstOrNull() ?: return
    val accent = MaterialTheme.colorScheme.primary
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(accent.copy(alpha = 0.08f))
            .border(1.dp, accent.copy(alpha = 0.35f), RoundedCornerShape(16.dp))
            .clickable(onClickLabel = "Open On this day", onClick = onOpen).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text("ON THIS DAY · ${OnThisDay.label(today).uppercase()}", style = MaterialTheme.typography.labelSmall, letterSpacing = 1.2.sp, color = accent)
        Text("${first.yearLabel}: ${first.title}", fontFamily = BookSerif, fontSize = 17.sp)
        Text(first.text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 3, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
        Text(if (entries.size > 1) "And ${entries.size - 1} more ›" else "More ›", style = MaterialTheme.typography.labelLarge, color = accent)
    }
}

/** The whole day: every entry, with its author's page and its idea; ‹ and › move a day at a time. */
@Composable
fun OnThisDayScreen(onBack: () -> Unit, onOpenAuthor: (String) -> Unit) {
    val context = LocalContext.current
    var shift by rememberSaveable { mutableIntStateOf(0) }
    val cal = remember(shift) { Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, shift) } }
    val entries = remember(shift) { runCatching { OnThisDay.on(context, cal) }.getOrDefault(emptyList()) }
    val bundled = remember { QuoteRepository(context).bundled() }
    var opened by remember { mutableStateOf<Quote?>(null) }
    opened?.let { q -> QuotePageSheet(quote = q, onDismiss = { opened = null }, onOpenAuthor = { opened = null; onOpenAuthor(it) }) }
    ToolScreen("On this day", onBack) {
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { shift-- }) { Text("‹") }
                Text(
                    OnThisDay.label(cal) + if (shift == 0) " · today" else "",
                    fontFamily = BookSerif, fontSize = 20.sp, modifier = Modifier.weight(1f),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
                TextButton(onClick = { shift++ }) { Text("›") }
            }
        }
        if (entries.isEmpty()) item {
            Text("Nothing from the collection on this day. Try the day before or after.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        entries.forEach { e ->
            item(key = "e-${e.monthDay}-${e.year}-${e.title}") {
                Column(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.surfaceContainerHigh).padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(5.dp),
                ) {
                    Text(
                        when (e.kind) { "b" -> "BORN · ${e.yearLabel}"; "d" -> "DIED · ${e.yearLabel}"; else -> e.yearLabel },
                        style = MaterialTheme.typography.labelSmall, letterSpacing = 1.2.sp, color = MaterialTheme.colorScheme.primary,
                    )
                    Text(e.title, fontFamily = BookSerif, fontSize = 17.sp)
                    Text(e.text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        if (e.author.isNotEmpty()) TextButton(onClick = { onOpenAuthor(e.author) }) { Text("${e.author} ›") }
                        if (e.idea.isNotEmpty()) Learn.idea(bundled, e.idea)?.let { idea -> TextButton(onClick = { opened = idea }) { Text("${e.idea} ›") } }
                    }
                }
            }
        }
        item {
            Text(
                "Dates of birth and death are checked against Wikidata and the app's timeline; the notes are written for the app.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

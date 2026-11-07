package com.trinineba.quotesonlock

import android.content.Context
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * "More like this": for each quote, idea and myth, the lines in the collection that speak to the same thing, each by
 * a different author. The matches were worked out once, when the app was built, by comparing the meaning of every
 * line (res/raw/similar.txt), so nothing runs or downloads on the phone. Lines from authors switched off with the
 * "modern authors" switch are left out, and the matches you have saved as favourites come first.
 */
object MoreLikeThis {
    private const val SHOWN = 6

    @Volatile private var matches: Map<Int, IntArray>? = null
    @Volatile private var byKey: Map<Int, Quote>? = null

    fun key(text: String): Int = QuoteData.wordsKey(text).hashCode()

    private fun matches(context: Context): Map<Int, IntArray> = matches ?: synchronized(this) {
        matches ?: context.applicationContext.resources.openRawResource(R.raw.similar).bufferedReader().useLines { lines ->
            val map = HashMap<Int, IntArray>(16_000)
            lines.forEach { line ->
                if (line.isBlank() || line.startsWith("#")) return@forEach
                val parts = line.trim().split(' ')
                val keys = parts.mapNotNull { it.toIntOrNull(36) }
                if (keys.size > 1) map.putIfAbsent(keys[0], keys.drop(1).toIntArray())
            }
            map
        }.also { matches = it }
    }

    private fun byKey(context: Context): Map<Int, Quote> = byKey ?: synchronized(this) {
        byKey ?: HashMap<Int, Quote>(16_000).apply {
            QuoteRepository(context).bundled().forEach { putIfAbsent(key(it.text), it) }
        }.also { byKey = it }
    }

    /** Loads the matches ahead of time (called from the background preload). */
    fun warmUp(context: Context) {
        runCatching { matches(context); byKey(context) }
    }

    fun has(context: Context, quote: Quote): Boolean =
        quote.category != Category.CUSTOM && matches(context).containsKey(key(quote.text))

    fun of(context: Context, quote: Quote): List<Quote> {
        val keys = matches(context)[key(quote.text)] ?: return emptyList()
        val all = byKey(context)
        val on = QuotePrefs(context).enabledCategories
        val hidden = Category.MODERN.filter { it !in on }.toSet()
        val favorites = QuoteRepository(context).favorites().map { QuoteData.wordsKey(it.text) }.toSet()
        val found = keys.mapNotNull { all[it] }.filter { it.category !in hidden }
        val (saved, rest) = found.partition { QuoteData.wordsKey(it.text) in favorites }
        return (saved + rest).take(SHOWN)
    }
}

/** The opening of a match, short enough for a card. */
private fun cardText(quote: Quote): String {
    val text = quote.title?.let { "$it. ${quote.text}" } ?: quote.text
    return if (quote.title != null) text else "“$text”"
}

@Composable
private fun MatchCard(quote: Quote, modifier: Modifier = Modifier, lines: Int = 4, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
        modifier = modifier,
    ) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 11.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(
                cardText(quote),
                fontFamily = FontFamily.Serif,
                fontSize = 15.sp,
                lineHeight = 21.sp,
                maxLines = lines,
                overflow = TextOverflow.Ellipsis,
            )
            quote.byline?.let {
                Text(
                    "— $it",
                    fontFamily = FontFamily.Serif,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/**
 * The matches for [start] in a sheet. Tapping one makes it the new centre, with its own matches; the trail at the
 * top shows the way you have come, and tapping a step in it goes back there.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MoreLikeThisSheet(start: Quote, onDismiss: () -> Unit, onFavoritesChanged: () -> Unit = {}, showStart: Boolean = false) {
    val context = LocalContext.current
    val repo = remember { QuoteRepository(context) }
    val trail = remember(start.text) { mutableStateListOf(start) }
    val current = trail.last()
    val found = remember(current.text) { MoreLikeThis.of(context, current) }
    var favorites by remember { mutableStateOf(repo.favorites().map { it.text }.toSet()) }
    val scroll = rememberScrollState()

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 18.dp).padding(bottom = 28.dp).verticalScroll(scroll),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text("MORE LIKE THIS", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary, letterSpacing = 1.2.sp)
            if (trail.size > 1 || showStart) {
                // The way you have come: tap a step to go back to it.
                if (trail.size > 1) Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    trail.forEachIndexed { i, q ->
                        if (i > 0) Text("›", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        val here = i == trail.lastIndex
                        Surface(
                            onClick = { while (trail.size > i + 1) trail.removeAt(trail.lastIndex) },
                            enabled = !here,
                            shape = RoundedCornerShape(12.dp),
                            color = if (here) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
                        ) {
                            Text(
                                authorName(q.byline) ?: q.title ?: "…",
                                style = MaterialTheme.typography.labelMedium,
                                color = if (here) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                            )
                        }
                    }
                }
                // The line you are on now, in full, with its own actions.
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)),
                ) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        current.title?.let { Text(it, style = MaterialTheme.typography.titleSmall) }
                        Text(readingText(current), fontFamily = FontFamily.Serif, fontSize = 16.sp, lineHeight = 23.sp)
                        current.byline?.let { Text("— $it", fontFamily = FontFamily.Serif, fontSize = 13.sp, color = MaterialTheme.colorScheme.primary) }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                            ReadAloudButton(current)
                            ContextListAction(current)
                            ShareListAction(current)
                            val saved = current.text in favorites
                            ListAction(
                                if (saved) R.drawable.ic_favorite else R.drawable.ic_favorite_border,
                                if (saved) "Remove from favourites" else "Add to favourites",
                                tint = if (saved) Color(0xFFE0566B) else null,
                            ) {
                                repo.toggleFavorite(current)
                                favorites = repo.favorites().map { it.text }.toSet()
                                onFavoritesChanged()
                            }
                        }
                    }
                }
                Text("AND THESE SPEAK TO IT", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, letterSpacing = 1.2.sp)
            }
            if (found.isEmpty()) {
                Text("No close matches for this one.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            found.forEach { match ->
                MatchCard(match, Modifier.fillMaxWidth()) { trail.add(match) }
            }
            if (found.isNotEmpty()) {
                Text(
                    "Tap one to follow it",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                )
            }
        }
    }
}

/** The home card's "More like this" button. */
@Composable
fun MoreLikeThisAction(quote: Quote?, onFavoritesChanged: () -> Unit = {}) {
    val context = LocalContext.current
    var open by remember { mutableStateOf(false) }
    val available = remember(quote?.text) { quote != null && MoreLikeThis.has(context, quote) }
    QuoteAction(R.drawable.ic_hub, "More like this", enabled = available) { open = true }
    if (open && quote != null) MoreLikeThisSheet(quote, { open = false }, onFavoritesChanged)
}

/** In quote lists: a small row of matches under the quote, opened and closed by its icon. */
@Composable
fun MoreLikeThisListAction(quote: Quote, open: Boolean, onToggle: () -> Unit) {
    val context = LocalContext.current
    val available = remember(quote.text) { MoreLikeThis.has(context, quote) }
    if (!available) return
    ListAction(R.drawable.ic_hub, if (open) "Hide more like this" else "More like this", tint = if (open) MaterialTheme.colorScheme.tertiary else null, onClick = onToggle)
}

/** The row itself, shown under a quote in a list while its "More like this" icon is on. */
@Composable
fun MoreLikeThisRow(quote: Quote, onFavoritesChanged: () -> Unit = {}) {
    val context = LocalContext.current
    val found = remember(quote.text) { MoreLikeThis.of(context, quote) }
    var opened by remember { mutableStateOf<Quote?>(null) }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("MORE LIKE THIS", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, letterSpacing = 1.2.sp)
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            found.forEach { match ->
                MatchCard(match, Modifier.width(230.dp), lines = 5) { opened = match }
            }
        }
    }
    opened?.let { MoreLikeThisSheet(it, { opened = null }, onFavoritesChanged, showStart = true) }
}

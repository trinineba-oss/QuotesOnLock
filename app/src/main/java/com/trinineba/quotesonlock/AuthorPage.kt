package com.trinineba.quotesonlock

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** The fuller parts of an author's page: their books, where to start reading, and where else they are in the app. */
object AuthorPages {
    @Volatile private var starts: Map<String, String>? = null

    /** "Where to start" for the major authors (res/raw/authorstart.txt). */
    fun start(context: Context, name: String): String? = (starts ?: synchronized(this) {
        starts ?: context.applicationContext.resources.openRawResource(R.raw.authorstart).bufferedReader().useLines { lines ->
            lines.filter { !it.startsWith("#") && '|' in it }.associate { it.substringBefore('|') to it.substringAfter('|') }
        }.also { starts = it }
    })[name]

    /** The book a byline names, without the translator: "Rabindranath Tagore, Stray Birds" gives "Stray Birds". */
    fun bookOf(byline: String?, author: String): String? {
        val b = byline ?: return null
        if (!b.startsWith(author)) return null
        return b.removePrefix(author).removePrefix(",").substringBefore(" (tr.").substringBefore(" (").trim().ifBlank { null }
    }

    /** The Library room for an author, as a route. */
    fun libraryRoute(author: String): Pair<String, String>? = when (author) {
        "Plato" -> "plato" to "Plato's dialogues"
        "Vincent van Gogh" -> "letters" to "Van Gogh's letters"
        "Leonardo da Vinci" -> "leonardo" to "Leonardo's notebooks"
        "Lao Tzu" -> "book:tao" to "Tao Te Ching"
        "Marcus Aurelius" -> "book:meditations" to "Meditations"
        "Kahlil Gibran" -> "book:prophet" to "The Prophet"
        "Rabindranath Tagore" -> "book:gitanjali" to "Gitanjali"
        else -> null
    }

    /** Essays in which the author is one of the voices. */
    fun essays(context: Context, author: String): List<String> {
        val last = author.substringAfterLast(' ')
        return runCatching { Essays.all(context) }.getOrDefault(emptyList())
            .filter { e -> e.voices.contains(author) || (last.length > 3 && Regex("\\b${Regex.escape(last)}\\b").containsMatchIn(e.voices)) }
            .map { it.title }
    }
}

@Composable
private fun PageLabel(text: String) {
    Text(text.uppercase(), style = MaterialTheme.typography.labelSmall, letterSpacing = 1.2.sp, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 8.dp, bottom = 2.dp))
}

/**
 * Under an author's bio: where to start reading, their books (tap one to show only its lines), and where else they
 * appear in the app.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AuthorExtras(author: String, all: List<Quote>, book: String?, onBook: (String?) -> Unit, onOpenPage: (String) -> Unit) {
    val context = LocalContext.current
    val start = remember(author) { AuthorPages.start(context, author) }
    val books = remember(all) {
        all.mapNotNull { AuthorPages.bookOf(it.author, author) }.groupingBy { it }.eachCount().toList().sortedByDescending { it.second }
    }
    val room = remember(author) { AuthorPages.libraryRoute(author) }
    val essays = remember(author) { AuthorPages.essays(context, author) }
    Column {
        start?.let {
            PageLabel("Where to start")
            Text(
                it, style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surfaceContainerHigh).padding(12.dp),
            )
        }
        if (room != null || essays.isNotEmpty()) {
            PageLabel("In the app")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                room?.let { (route, label) -> SuggestionChip(onClick = { onOpenPage(route) }, label = { Text("Library: $label") }) }
                essays.take(4).forEach { title -> SuggestionChip(onClick = { onOpenPage("essays:$title") }, label = { Text("Essay: $title") }) }
            }
        }
        if (books.size > 1 || (books.size == 1 && books[0].second < all.size)) {
            PageLabel("Their books here")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                books.take(12).forEach { (title, n) ->
                    FilterChip(selected = book == title, onClick = { onBook(if (book == title) null else title) }, label = { Text("$title · $n") })
                }
            }
        }
    }
}

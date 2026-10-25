package com.trinineba.quotesonlock

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

/**
 * A page in the quote browser, saved as a string so it survives rotation:
 * `c:<category key>` for one quote type, `a:<author name>` for one author.
 */
object BrowsePage {
    fun category(category: Category) = "c:${category.key}"
    fun author(name: String) = "a:$name"

    /** Every quote, searchable; lists all authors A–Z until you type. */
    const val SEARCH = "s:"
}

/** The person behind a label such as "Leo Tolstoy, War and Peace", "Hafiz (tr. Gertrude Bell)" or "The Qur'an (2:255)". */
fun authorName(label: String?): String? =
    label?.substringBefore(", ")?.substringBefore(" (")?.trim()?.ifBlank { null }

@Composable
fun QuoteBrowser(
    page: String,
    repo: QuoteRepository,
    onBack: () -> Unit,
    onShow: (Quote) -> Unit,
    onOpenAuthor: (String) -> Unit,
) {
    val context = LocalContext.current
    BackHandler(onBack = onBack)

    val category = if (page.startsWith("c:")) Category.fromKey(page.removePrefix("c:")) else null
    val author = if (page.startsWith("a:")) page.removePrefix("a:") else null
    val searching = page == BrowsePage.SEARCH
    val all = remember(page) {
        when {
            searching -> repo.custom() + repo.bundled()
            category == Category.CUSTOM -> repo.custom()
            category != null -> repo.bundled().filter { it.category == category }
            else -> (repo.custom() + repo.bundled()).filter { authorName(it.author) == author }
        }
    }
    var query by rememberSaveable(page) { mutableStateOf("") }
    var favorites by remember { mutableStateOf(repo.favorites().map { it.text }.toSet()) }
    val shown = remember(all, query) {
        val q = query.trim()
        when {
            q.isEmpty() && searching -> emptyList()
            q.isEmpty() -> all
            else -> all.filter {
                it.text.contains(q, ignoreCase = true) || it.author?.contains(q, ignoreCase = true) == true
            }
        }
    }
    // Authors (with how many quotes each) for the search page.
    val authors = remember(all, searching) {
        if (!searching) emptyList()
        else all.mapNotNull { authorName(it.author) }.groupingBy { it }.eachCount().toList().sortedBy { it.first.lowercase() }
    }
    val matchingAuthors = remember(authors, query) {
        val q = query.trim()
        if (q.isEmpty()) authors else authors.filter { it.first.contains(q, ignoreCase = true) }
    }

    Scaffold { innerPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(innerPadding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { TextButton(onClick = onBack) { Text("← Back") } }
            item {
                Column {
                    Text(
                        if (searching) "Browse & search" else category?.label ?: author.orEmpty(),
                        style = MaterialTheme.typography.headlineSmall,
                    )
                    Text(
                        when {
                            searching && query.isBlank() -> "${all.size} quotes by ${authors.size} authors. Type a word, a phrase or a name."
                            query.isBlank() -> "${all.size} quotes"
                            else -> "${shown.size} of ${all.size} quotes"
                        },
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
            item {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text(if (searching) "Search all quotes and authors" else "Search these quotes") },
                )
            }
            if (searching) {
                // Authors first: all of them A–Z before typing, then the ones whose name matches.
                items(if (query.isBlank()) matchingAuthors else matchingAuthors.take(12)) { (name, count) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(onClickLabel = "Open $name") { onOpenAuthor(name) }
                            .padding(vertical = 10.dp),
                    ) {
                        Column {
                            Text(name, style = MaterialTheme.typography.titleMedium)
                            Text(
                                "$count quotes \u00B7 View \u203A",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                }
            }
            items(shown) { quote ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("“${quote.text}”")
                        quote.author?.let { label ->
                            val name = authorName(label)
                            // In a quote type's list, tapping the author opens everything by them.
                            val linked = author == null && name != null
                            Text(
                                "— $label",
                                style = MaterialTheme.typography.bodySmall,
                                color = if (linked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = if (linked) Modifier.clickable { onOpenAuthor(name!!) } else Modifier,
                            )
                        }
                        Row {
                            TextButton(onClick = { onShow(quote) }) { Text("Show now") }
                            TextButton(onClick = { QuoteClipboard.copy(context, quote) }) { Text("Copy") }
                            val favorite = quote.text in favorites
                            TextButton(onClick = {
                                repo.toggleFavorite(quote)
                                favorites = repo.favorites().map { it.text }.toSet()
                            }) { Text(if (favorite) "♥ Favourite" else "♡ Favourite") }
                        }
                    }
                }
            }
        }
    }
}

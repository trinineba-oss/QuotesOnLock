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
    val all = remember(page) {
        when {
            category == Category.CUSTOM -> repo.custom()
            category != null -> repo.bundled().filter { it.category == category }
            else -> (repo.custom() + repo.bundled()).filter { authorName(it.author) == author }
        }
    }
    var query by rememberSaveable(page) { mutableStateOf("") }
    val shown = remember(all, query) {
        val q = query.trim()
        if (q.isEmpty()) all else all.filter {
            it.text.contains(q, ignoreCase = true) || it.author?.contains(q, ignoreCase = true) == true
        }
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
                    Text(category?.label ?: author.orEmpty(), style = MaterialTheme.typography.headlineSmall)
                    Text(
                        if (query.isBlank()) "${all.size} quotes" else "${shown.size} of ${all.size} quotes",
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
                    label = { Text("Search these quotes") },
                )
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
                        }
                    }
                }
            }
        }
    }
}

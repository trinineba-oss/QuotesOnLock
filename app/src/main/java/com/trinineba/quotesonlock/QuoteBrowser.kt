package com.trinineba.quotesonlock

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.layout.size
import androidx.annotation.DrawableRes
import androidx.compose.ui.res.painterResource
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.SuggestionChip
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
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
    fun subject(subject: Subject) = "t:${subject.key}"

    /** Every quote, searchable; lists all authors A–Z until you type. */
    const val SEARCH = "s:"
}

/** A small round icon button for a quote in a list. */
@Composable
internal fun ListAction(@DrawableRes icon: Int, description: String, tint: Color? = null, onClick: () -> Unit) {
    IconButton(onClick = onClick, modifier = Modifier.size(42.dp)) {
        Icon(
            painterResource(icon),
            contentDescription = description,
            tint = tint ?: MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(20.dp),
        )
    }
}

/** The person behind a label such as "Leo Tolstoy, War and Peace", "Hafiz (tr. Gertrude Bell)" or "The Qur'an (2:255)". */
fun authorName(label: String?): String? = when {
    label == null -> null
    label.startsWith("Alfred, Lord Tennyson") -> "Alfred, Lord Tennyson"
    else -> label.substringBefore(", ").substringBefore(" (").trim().ifBlank { null }
}

/** Short biographies of the authors, shown at the top of their page (res/raw/bios.txt: `name|bio`). */
object AuthorBios {
    @Volatile private var bios: Map<String, String>? = null

    fun of(context: android.content.Context, name: String?): String? {
        if (name == null) return null
        val map = bios ?: context.applicationContext.resources.openRawResource(R.raw.bios).bufferedReader().useLines { lines ->
            lines.filter { it.isNotBlank() && !it.startsWith("#") && '|' in it }
                .associate { it.substringBefore('|').trim() to it.substringAfter('|').trim() }
        }.also { bios = it }
        return map[name]
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun QuoteBrowser(
    page: String,
    repo: QuoteRepository,
    onBack: () -> Unit,
    onShow: (Quote) -> Unit,
    onOpenAuthor: (String) -> Unit,
    onOpenSubject: (Subject) -> Unit = {},
) {
    val context = LocalContext.current
    BackHandler(onBack = onBack)

    val category = if (page.startsWith("c:")) Category.fromKey(page.removePrefix("c:")) else null
    val author = if (page.startsWith("a:")) page.removePrefix("a:") else null
    val subject = if (page.startsWith("t:")) Subject.fromKey(page.removePrefix("t:")) else null
    val searching = page == BrowsePage.SEARCH
    val all = remember(page) {
        when {
            searching -> repo.custom() + repo.bundled()
            category == Category.CUSTOM -> repo.custom()
            category != null -> repo.bundled().filter { it.category == category }
            subject != null -> repo.bundled().filter { subject.key in it.subjects }
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
                it.text.contains(q, ignoreCase = true) || it.author?.contains(q, ignoreCase = true) == true ||
                    it.title?.contains(q, ignoreCase = true) == true
            }
        }
    }
    // Authors (with how many quotes each) for the search page.
    val authors = remember(all, searching) {
        if (!searching) emptyList()
        else all.mapNotNull { authorName(it.author) }.groupingBy { it }.eachCount().toList().sortedBy { it.first.lowercase() }
    }
    val subjectCounts = remember(all, searching) {
        if (!searching) emptyMap() else all.flatMap { it.subjects }.groupingBy { it }.eachCount()
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
                        if (searching) "Explore" else category?.label ?: subject?.label ?: author.orEmpty(),
                        style = MaterialTheme.typography.headlineSmall,
                    )
                    val context = androidx.compose.ui.platform.LocalContext.current
                    val bio = remember(page) { if (searching) null else AuthorBios.of(context, author ?: category?.label) }
                    bio?.let {
                        Text(
                            it,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(top = 6.dp, bottom = 6.dp),
                        )
                    }
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
            if (searching && query.isBlank()) {
                // Before typing: every subject, then every author A–Z.
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Subjects", style = MaterialTheme.typography.titleMedium)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Subject.entries.filter { (subjectCounts[it.key] ?: 0) > 0 }.forEach { s ->
                                SuggestionChip(
                                    onClick = { onOpenSubject(s) },
                                    label = { Text("${s.label} \u00B7 ${subjectCounts[s.key]}") },
                                )
                            }
                        }
                        Text("Authors A–Z", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp))
                    }
                }
            }
            if (searching) {
                // Authors: all of them A–Z before typing, then the ones whose name matches.
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
                SoftCard(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        quote.title?.let { Text(it, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold) }
                        Text(quote.shownText)
                        quote.brief?.let { Text("In brief: $it", style = MaterialTheme.typography.bodySmall) }
                        quote.byline?.let { label ->
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
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                            ListAction(R.drawable.ic_lock, "Show on the lock screen now") { onShow(quote) }
                            ListAction(R.drawable.ic_content_copy, "Copy") { QuoteClipboard.copy(context, quote) }
                            ListAction(R.drawable.ic_share, "Share as image") { QuoteShare.share(context, quote) }
                            val favorite = quote.text in favorites
                            ListAction(
                                if (favorite) R.drawable.ic_favorite else R.drawable.ic_favorite_border,
                                if (favorite) "Remove from favourites" else "Add to favourites",
                                tint = if (favorite) Color(0xFFE0566B) else null,
                            ) {
                                repo.toggleFavorite(quote)
                                favorites = repo.favorites().map { it.text }.toSet()
                            }
                        }
                    }
                }
            }
        }
    }
}

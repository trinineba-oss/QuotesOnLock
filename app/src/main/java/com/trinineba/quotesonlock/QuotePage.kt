package com.trinineba.quotesonlock

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Everything about one quote in one place: the full text, then its context, its matches, the words behind
 * its words and your own note in tabs, and every action along the bottom. It opens from Today, from any
 * list and from Favourites, so a quote always behaves the same way.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuotePageSheet(
    quote: Quote,
    onDismiss: () -> Unit,
    /** Puts the quote on the lock screen now; null hides the button. */
    onShowOnLock: ((Quote) -> Unit)? = null,
    /** Called after a favourite, note or journal entry changes, so the screen behind can refresh. */
    onChanged: () -> Unit = {},
    onOpenAuthor: ((String) -> Unit)? = null,
    startTab: String? = null,
) {
    val context = LocalContext.current
    val repo = remember { QuoteRepository(context) }
    val prefs = remember { QuotePrefs(context) }
    val hasContext = remember(quote.text) { QuoteContext.has(context, quote) }
    val hasMatches = remember(quote.text) { MoreLikeThis.has(context, quote) }
    val words = remember(quote.text) { runCatching { Words.find(Words.all(context), quote.shownText, quote.category?.key) }.getOrDefault(emptyList()) }
    val tabs = buildList {
        if (hasContext) add("Context")
        if (hasMatches) add("More like this")
        if (words.isNotEmpty()) add("Words")
        add("My note")
    }
    var tab by remember(quote.text) { mutableStateOf(startTab?.takeIf { it in tabs } ?: tabs.first()) }
    var saved by remember(quote.text) { mutableStateOf(repo.isFavorite(quote)) }
    var note by remember(quote.text) { mutableStateOf(repo.favoriteInfo()[QuoteData.wordsKey(quote.text)]?.note.orEmpty()) }
    var editingNote by remember { mutableStateOf(false) }
    var writing by remember { mutableStateOf(false) }
    var sharing by remember { mutableStateOf(false) }
    var following by remember { mutableStateOf<Quote?>(null) }
    val speaking by QuoteVoice.speaking.collectAsState()

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 28.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            quote.title?.let { Text(it, style = MaterialTheme.typography.titleLarge, fontFamily = FontFamily.Serif) }
            Text(readingText(quote), fontFamily = if (quote.explained) null else FontFamily.Serif, fontSize = 19.sp, lineHeight = 28.sp)
            quote.byline?.let { by ->
                val name = authorName(by)
                Text(
                    if (name != null && onOpenAuthor != null) "— $by ›" else "— $by",
                    fontFamily = FontFamily.Serif,
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = if (name != null && onOpenAuthor != null) Modifier.clickable { onDismiss(); onOpenAuthor(name) } else Modifier,
                )
            }
            quote.brief?.let { Text("In brief: $it", style = MaterialTheme.typography.bodyMedium) }
            quote.more?.let { MoreAbout(quote.text, it) }

            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                tabs.forEach { t -> FilterChip(selected = t == tab, onClick = { tab = t }, label = { Text(t) }) }
            }
            when (tab) {
                "Context" -> ContextPassage(quote)
                "More like this" -> {
                    val found = remember(quote.text) { MoreLikeThis.of(context, quote) }
                    found.forEach { m ->
                        Surface(
                            onClick = { following = m },
                            shape = RoundedCornerShape(14.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(m.title?.let { "$it. ${m.text}" } ?: "“${m.text}”", fontFamily = FontFamily.Serif, fontSize = 15.sp, lineHeight = 21.sp, maxLines = 4, overflow = TextOverflow.Ellipsis)
                                m.byline?.let { Text("— $it", fontSize = 13.sp, fontFamily = FontFamily.Serif, color = MaterialTheme.colorScheme.primary, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                            }
                        }
                    }
                }
                "Words" -> words.forEach { (_, _, w) ->
                    Surface(
                        onClick = { Words.opened.value = w },
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                Text(w.original, fontSize = 24.sp, fontFamily = FontFamily.Serif)
                                Column {
                                    if (w.romanised != w.original) Text(w.romanised, fontStyle = FontStyle.Italic, fontFamily = FontFamily.Serif)
                                    Text("${w.language} · ${w.meaning}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                                }
                            }
                            Text(w.note, style = MaterialTheme.typography.bodyMedium, maxLines = 4, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
                else -> Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    when {
                        !saved -> Text(
                            "Save this quote to keep a note of your own with it.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        note.isBlank() -> TextButton(onClick = { editingNote = true }) { Text("Write a note") }
                        else -> {
                            Text(note, style = MaterialTheme.typography.bodyLarge, fontStyle = FontStyle.Italic)
                            TextButton(onClick = { editingNote = true }) { Text("Edit your note") }
                        }
                    }
                }
            }

            // Every action, along the bottom.
            Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.SpaceAround) {
                QuoteAction(
                    if (saved) R.drawable.ic_favorite else R.drawable.ic_favorite_border,
                    if (saved) "Saved" else "Save",
                    tint = if (saved) Color(0xFFE0566B) else null,
                ) {
                    saved = repo.toggleFavorite(quote)
                    onChanged()
                }
                QuoteAction(
                    if (speaking == quote.text) R.drawable.ic_stop else R.drawable.ic_volume_up,
                    if (speaking == quote.text) "Stop" else "Listen",
                ) { QuoteVoice.toggle(context, quote) }
                QuoteAction(R.drawable.ic_edit_note, "Journal") { writing = true }
                Box {
                    QuoteAction(R.drawable.ic_share, "Share") { sharing = true }
                    ShareMenu(quote, sharing) { sharing = false }
                }
                onShowOnLock?.let { show -> QuoteAction(R.drawable.ic_lock, "Lock screen") { show(quote); onDismiss() } }
            }
        }
    }

    if (editingNote) {
        FavoriteNoteDialog(
            quote = quote,
            initial = note,
            onSave = { text -> repo.setFavoriteNote(quote, text); note = text.trim(); editingNote = false; onChanged() },
            onDismiss = { editingNote = false },
        )
    }
    if (writing) {
        val prompt = Journal.promptFor(quote)
        JournalDialog(
            quoteText = quote.shownText,
            prompt = prompt,
            initial = "",
            onSave = { text -> Journal.add(prefs, quote, prompt, text); writing = false; onChanged() },
            onDelete = null,
            onDismiss = { writing = false },
        )
    }
    following?.let { m -> MoreLikeThisSheet(m, { following = null }, onChanged, showStart = true) }
}

/** Marks a quote's text as tappable, opening its quote page. */
internal fun Modifier.opensQuote(onClick: () -> Unit): Modifier = this.clickable(onClickLabel = "Open this quote", onClick = onClick)

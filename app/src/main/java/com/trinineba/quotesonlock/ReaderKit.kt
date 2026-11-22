package com.trinineba.quotesonlock

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalTextToolbar
import androidx.compose.ui.platform.TextToolbar
import androidx.compose.ui.platform.TextToolbarStatus
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import kotlinx.coroutines.flow.MutableStateFlow
import org.json.JSONArray
import org.json.JSONObject

/**
 * What all the Library's readers share: the text size, where you are in each room (Continue reading),
 * bookmarks, reading aloud, and saving a selected passage.
 */
object Reading {
    /** Text size as a multiple of the normal size; shared by every room. */
    val scale = MutableStateFlow(1f)
    private var loaded = false

    fun load(prefs: QuotePrefs) {
        if (!loaded) { scale.value = prefs.readerScale; loaded = true }
    }

    fun setScale(prefs: QuotePrefs, value: Float) {
        val v = value.coerceIn(0.8f, 1.6f)
        scale.value = v
        prefs.readerScale = v
    }

    /** One place in one room: [route] opens it again; [index] of [total] gives the progress bar. */
    class Place(val room: String, val item: String, val title: String, val subtitle: String, val route: String, val index: Int, val total: Int, val at: Long)

    fun recent(prefs: QuotePrefs): List<Place> = runCatching {
        val a = JSONArray(prefs.readerRecent ?: "[]")
        (0 until a.length()).map { i ->
            val o = a.getJSONObject(i)
            Place(o.getString("room"), o.getString("item"), o.getString("title"), o.optString("subtitle"), o.getString("route"), o.optInt("index"), o.optInt("total", 1), o.optLong("at"))
        }
    }.getOrDefault(emptyList())

    /** Remembers [place] as the latest in its room (one place per room, newest first). */
    fun record(prefs: QuotePrefs, place: Place) {
        val list = listOf(place) + recent(prefs).filter { it.room != place.room }
        val a = JSONArray()
        list.take(12).forEach { p ->
            a.put(JSONObject().put("room", p.room).put("item", p.item).put("title", p.title).put("subtitle", p.subtitle)
                .put("route", p.route).put("index", p.index).put("total", p.total).put("at", p.at))
        }
        prefs.readerRecent = a.toString()
        LibraryLinks.here.value = place.route
    }

    fun bookmarkKey(room: String, item: String) = "$room|$item"

    /** Reads [paragraphs] aloud with the phone's voice under [key]; reading the same key again stops it. */
    fun toggleListen(context: Context, key: String, title: String, paragraphs: List<String>) {
        if (QuoteVoice.speaking.value == key) { QuoteVoice.stop(); return }
        val parts = listOf(QuoteVoice.Segment(title, null, 700)) +
            paragraphs.filter { it.isNotBlank() && it != "…" }.map { QuoteVoice.Segment(it.replace('\n', ' '), null, 450) }
        QuoteVoice.speakParts(context, key, parts)
    }

    /** Saves a passage to Favourites (if it is not there already). */
    fun savePassage(context: Context, text: String, source: String): Boolean {
        val repo = QuoteRepository(context)
        val q = Quote(text.trim(), source)
        val key = QuoteData.wordsKey(q.text)
        if (repo.favorites().any { QuoteData.wordsKey(it.text) == key }) return false
        repo.toggleFavorite(q)
        return true
    }

    /** Starts a note in Notes & journal holding the passage and where it is from. */
    fun notePassage(context: Context, text: String, source: String, bookTitle: String) {
        val prefs = QuotePrefs(context)
        val now = System.currentTimeMillis()
        Notes.put(prefs, Note(now, bookTitle, "“${text.trim()}”\n— $source\n\n", emptyList(), false, now))
    }

    fun sharePassage(context: Context, text: String, source: String) {
        val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, "“${text.trim()}”\n— $source")
        context.startActivity(Intent.createChooser(send, "Share passage").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}

/** Reading-sized type: [base] sp times the reader's text size. */
@Composable
fun readerSize(base: Float): TextUnit {
    val scale by Reading.scale.collectAsState()
    return (base * scale).sp
}

/**
 * Makes the text inside selectable; selecting a passage offers Save, Note, Copy and Share instead of the
 * system's menu. [source] is how the passage is credited ("Lao Tzu, Tao Te Ching (tr. James Legge)").
 */
@Composable
fun PassageSelection(source: String, bookTitle: String, content: @Composable () -> Unit) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    var menu by remember { mutableStateOf<Pair<Rect, () -> Unit>?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    val toolbar = remember {
        object : TextToolbar {
            override var status: TextToolbarStatus = TextToolbarStatus.Hidden
                private set

            override fun showMenu(
                rect: Rect,
                onCopyRequested: (() -> Unit)?,
                onPasteRequested: (() -> Unit)?,
                onCutRequested: (() -> Unit)?,
                onSelectAllRequested: (() -> Unit)?,
            ) {
                status = TextToolbarStatus.Shown
                menu = rect to (onCopyRequested ?: {})
            }

            override fun hide() {
                status = TextToolbarStatus.Hidden
                menu = null
            }
        }
    }
    CompositionLocalProvider(LocalTextToolbar provides toolbar) {
        SelectionContainer { content() }
    }
    fun selected(copy: () -> Unit): String? {
        copy()
        return clipboard.getText()?.text?.takeIf { it.isNotBlank() }
    }
    menu?.let { (rect, copy) ->
        Popup(offset = IntOffset(rect.left.toInt().coerceAtLeast(0), (rect.top - 140).toInt().coerceAtLeast(0)), onDismissRequest = { menu = null }) {
            Surface(shape = RoundedCornerShape(14.dp), tonalElevation = 6.dp, shadowElevation = 8.dp) {
                Row(Modifier.padding(4.dp)) {
                    TextButton(onClick = {
                        selected(copy)?.let { message = if (Reading.savePassage(context, it, source)) "Saved to Favourites" else "Already in Favourites" }
                        menu = null
                    }) { Text("♡ Save") }
                    TextButton(onClick = {
                        selected(copy)?.let { Reading.notePassage(context, it, source, bookTitle); message = "Added to Notes" }
                        menu = null
                    }) { Text("✎ Note") }
                    TextButton(onClick = { copy(); message = "Copied"; menu = null }) { Text("Copy") }
                    TextButton(onClick = { selected(copy)?.let { Reading.sharePassage(context, it, source) }; menu = null }) { Text("Share") }
                }
            }
        }
    }
    message?.let { m ->
        androidx.compose.runtime.LaunchedEffect(m) { kotlinx.coroutines.delay(1800); message = null }
        Popup(alignment = Alignment.BottomCenter, offset = IntOffset(0, -260)) {
            Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.inverseSurface) {
                Text(m, color = MaterialTheme.colorScheme.inverseOnSurface, modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp))
            }
        }
    }
}

/**
 * The reader's bar: previous and next, text size, read aloud and a bookmark. [listenKey] identifies what is
 * being read so the button can show Stop.
 */
@Composable
fun ReaderBar(
    prevLabel: String?,
    nextLabel: String?,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    listenKey: String,
    listenTitle: String,
    paragraphs: List<String>,
    bookmarked: Boolean,
    onBookmark: () -> Unit,
) {
    val context = LocalContext.current
    val prefs = remember { QuotePrefs(context) }
    val speaking by QuoteVoice.speaking.collectAsState()
    val scale by Reading.scale.collectAsState()
    androidx.compose.runtime.DisposableEffect(listenKey) {
        onDispose { if (QuoteVoice.speaking.value == listenKey) QuoteVoice.stop(); LibraryLinks.here.value = null }
    }
    Row(
        Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceContainer).navigationBarsPadding().padding(horizontal = 4.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        TextButton(onClick = onPrev, enabled = prevLabel != null, modifier = Modifier.weight(1f)) {
            Text("‹ " + (prevLabel ?: ""), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        BarIcon("A−", "Smaller text") { Reading.setScale(prefs, scale - 0.1f) }
        BarIcon("A+", "Larger text") { Reading.setScale(prefs, scale + 0.1f) }
        BarIcon(if (speaking == listenKey) "■" else "▶", if (speaking == listenKey) "Stop reading aloud" else "Read aloud") {
            Reading.toggleListen(context, listenKey, listenTitle, paragraphs)
        }
        BarIcon(if (bookmarked) "★" else "☆", if (bookmarked) "Remove bookmark" else "Bookmark this page", onClick = onBookmark)
        TextButton(onClick = onNext, enabled = nextLabel != null, modifier = Modifier.weight(1f)) {
            Text((nextLabel ?: "") + " ›", maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun BarIcon(label: String, description: String, onClick: () -> Unit) {
    Box(
        Modifier.clip(RoundedCornerShape(18.dp)).clickable(onClickLabel = description, onClick = onClick).padding(horizontal = 9.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
    }
}

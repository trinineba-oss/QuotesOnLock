package com.trinineba.quotesonlock

import android.content.Context
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.util.UUID

class QuoteRepository(
    context: Context,
    private val prefs: QuotePrefs = QuotePrefs(context),
) {
    private val appContext = context.applicationContext

    fun bundled(): List<Quote> = bundledCache ?: loadBundled().also { bundledCache = it }

    /** Lines look like `category|text|author`; a literal `\n` in the text is a line break. */
    private fun loadBundled(): List<Quote> =
        appContext.resources.openRawResource(R.raw.quotes).bufferedReader().useLines { lines ->
            lines
                .map { it.trim() }
                .filter { it.isNotEmpty() && !it.startsWith("#") }
                .mapNotNull { line ->
                    val category = Category.fromKey(line.substringBefore('|').trim())
                    val rest = line.substringAfter('|', "")
                    if (category == null || !rest.contains('|')) return@mapNotNull null
                    Quote(
                        text = rest.substringBeforeLast('|').trim().replace("\\n", "\n"),
                        author = rest.substringAfterLast('|').trim().ifBlank { null },
                        category = category,
                    )
                }
                .toList()
        }

    fun custom(): List<Quote> {
        val raw = prefs.customJson ?: return emptyList()
        return try {
            val array = JSONArray(raw)
            (0 until array.length()).map { i ->
                val obj = array.getJSONObject(i)
                Quote(
                    text = obj.getString("text"),
                    author = obj.optString("author").ifBlank { null },
                    category = Category.CUSTOM,
                    id = obj.getString("id"),
                )
            }
        } catch (e: JSONException) {
            emptyList()
        }
    }

    fun addCustom(text: String, author: String?) {
        val quotes = custom() + Quote(text, author, Category.CUSTOM, UUID.randomUUID().toString())
        saveCustom(quotes)
    }

    fun removeCustom(id: String) = saveCustom(custom().filterNot { it.id == id })

    private fun saveCustom(quotes: List<Quote>) {
        val array = JSONArray()
        quotes.forEach { q ->
            array.put(
                JSONObject().apply {
                    put("id", q.id)
                    put("text", q.text)
                    if (q.author != null) put("author", q.author)
                }
            )
        }
        prefs.customJson = array.toString()
    }

    fun favorites(): List<Quote> {
        val raw = prefs.favoritesJson ?: return emptyList()
        return try {
            val array = JSONArray(raw)
            (0 until array.length()).map { i ->
                val obj = array.getJSONObject(i)
                Quote(obj.getString("text"), obj.optString("author").ifBlank { null })
            }
        } catch (e: JSONException) {
            emptyList()
        }
    }

    fun isFavorite(quote: Quote): Boolean = favorites().any { it.text == quote.text }

    /** Adds or removes [quote]; returns whether it is now a favourite. */
    fun toggleFavorite(quote: Quote): Boolean {
        val current = favorites()
        val nowFavorite = current.none { it.text == quote.text }
        val updated = if (nowFavorite) current + Quote(quote.text, quote.author) else current.filterNot { it.text == quote.text }
        val array = JSONArray()
        updated.forEach { q ->
            array.put(JSONObject().apply {
                put("text", q.text)
                if (q.author != null) put("author", q.author)
            })
        }
        prefs.favoritesJson = array.toString()
        return nowFavorite
    }

    /** The quotes eligible for rotation, based on the categories the user turned on. */
    fun pool(): List<Quote> {
        if (prefs.favoritesOnly) favorites().takeIf { it.isNotEmpty() }?.let { return it }
        val enabled = prefs.enabledCategories
        val mine = if (Category.CUSTOM in enabled) custom() else emptyList()
        return mine + bundled().filter { q -> q.category?.let { it in enabled } == true }
    }

    /** How many quotes a category holds, whether or not it is turned on. */
    fun count(category: Category): Int =
        if (category == Category.CUSTOM) custom().size else bundled().count { it.category == category }

    fun current(): Quote? = prefs.currentText?.let { Quote(it, prefs.currentAuthor) }

    private fun history(): List<Quote> {
        val raw = prefs.historyJson ?: return emptyList()
        return try {
            val array = JSONArray(raw)
            (0 until array.length()).map { i ->
                val obj = array.getJSONObject(i)
                Quote(obj.getString("text"), obj.optString("author").ifBlank { null })
            }
        } catch (e: JSONException) {
            emptyList()
        }
    }

    private fun saveHistory(quotes: List<Quote>) {
        val array = JSONArray()
        quotes.takeLast(HISTORY_SIZE).forEach { q ->
            array.put(
                JSONObject().apply {
                    put("text", q.text)
                    if (q.author != null) put("author", q.author)
                }
            )
        }
        prefs.historyJson = array.toString()
    }

    fun hasPrevious(): Boolean = history().isNotEmpty()

    /** Goes back to the quote shown before the current one, if there is one. */
    fun previous(): Quote? {
        val past = history()
        val last = past.lastOrNull() ?: return null
        saveHistory(past.dropLast(1))
        prefs.currentText = last.text
        prefs.currentAuthor = last.author
        return last
    }

    /**
     * Picks a random quote that hasn't been shown yet in this pass through the pool and stores it.
     * Once every quote in the pool has been shown, the pass starts over, so nothing repeats until
     * the whole pool has been seen (and never twice in a row).
     */
    fun advance(): Quote? {
        val pool = pool()
        if (pool.isEmpty()) return null
        val currentText = prefs.currentText
        var seen = prefs.seen
        var candidates = pool.filter { it.key() !in seen && it.text != currentText }
        if (candidates.isEmpty()) {
            seen = emptySet()
            candidates = pool.filter { it.text != currentText }.ifEmpty { pool }
        }
        val next = candidates.random()
        current()?.let { saveHistory(history() + it) }
        prefs.seen = seen + next.key()
        prefs.currentText = next.text
        prefs.currentAuthor = next.author
        return next
    }

    /** Makes [quote] the current quote (e.g. picked from the quote browser), keeping it in history. */
    fun show(quote: Quote): Quote {
        if (prefs.currentText == quote.text) return quote
        current()?.let { saveHistory(history() + it) }
        prefs.seen = prefs.seen + quote.key()
        prefs.currentText = quote.text
        prefs.currentAuthor = quote.author
        return quote
    }

    private fun Quote.key() = "$text|$author".hashCode().toString()

    private companion object {
        const val HISTORY_SIZE = 50

        @Volatile
        var bundledCache: List<Quote>? = null
    }
}

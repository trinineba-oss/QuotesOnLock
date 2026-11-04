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

    /** Every built-in quote, idea and myth, read once per process (the first call can take a moment). */
    fun bundled(): List<Quote> = bundledCache ?: synchronized(lock) {
        bundledCache ?: (loadBundled() + loadIdeas()).also { bundledCache = it }
    }

    /** Reads the built-in quotes on a background thread, so the first screen doesn't wait for them. */
    fun preload() {
        if (bundledCache == null) Thread({ runCatching { bundled(); counts() } }, "quotes-preload").start()
    }

    private fun counts(): Map<Category?, Int> = countCache ?: bundled().groupingBy { it.category }.eachCount().also { countCache = it }

    private fun loadIdeas(): List<Quote> =
        appContext.resources.openRawResource(R.raw.ideas).bufferedReader().useLines { QuoteData.parseIdeas(it) }

    /**
     * Quotes kept in storage only as text and author (favourites, history, the current quote) get back their
     * full form: an idea's title and explanation, a quote's type and subjects, and the collection's current
     * wording if its punctuation has been tidied since it was saved.
     */
    private fun full(quote: Quote): Quote {
        if (quote.isIdea || quote.category == Category.CUSTOM) return quote
        val index = wordsIndex ?: bundled().associateBy { QuoteData.wordsKey(it.text) }.also { wordsIndex = it }
        return index[QuoteData.wordsKey(quote.text)] ?: quote
    }

    private fun loadBundled(): List<Quote> =
        appContext.resources.openRawResource(R.raw.quotes).bufferedReader().useLines { QuoteData.parseQuotes(it) }

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
                full(Quote(obj.getString("text"), obj.optString("author").ifBlank { null }))
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
        QuoteWidgets.refresh(appContext)
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
        if (category == Category.CUSTOM) custom().size else counts()[category] ?: 0

    fun current(): Quote? = prefs.currentText?.let { full(Quote(it, prefs.currentAuthor)) }

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
        QuoteWidgets.refresh(appContext)
        return full(last)
    }

    /**
     * Picks a random quote that hasn't been shown yet in this pass through the pool and stores it.
     * Once every quote in the pool has been shown, the pass starts over, so nothing repeats until
     * the whole pool has been seen (and never twice in a row). With repeats allowed, any quote but
     * the current one can come up.
     */
    fun advance(): Quote? {
        val all = pool()
        // The mix: great ideas and myths can come up more often than their share of the pool.
        val pool = QuoteData.mixedPick(all, prefs.ideasMix, prefs.mythsMix, kotlin.random.Random.nextFloat())
        val pick = QuoteData.next(all, pool, prefs.seen, prefs.currentText, prefs.currentAuthor, prefs.allowRepeats) ?: return null
        val next = pick.quote
        current()?.let { saveHistory(history() + it) }
        prefs.seen = pick.seen
        prefs.currentText = next.text
        prefs.currentAuthor = next.author
        QuoteWidgets.refresh(appContext)
        return next
    }

    /** Makes [quote] the current quote (e.g. picked from the quote browser), keeping it in history. */
    fun show(quote: Quote): Quote {
        if (prefs.currentText == quote.text) return quote
        current()?.let { saveHistory(history() + it) }
        prefs.seen = prefs.seen + QuoteData.key(quote)
        prefs.currentText = quote.text
        prefs.currentAuthor = quote.author
        QuoteWidgets.refresh(appContext)
        return quote
    }

    private companion object {
        const val HISTORY_SIZE = 50

        @Volatile
        var bundledCache: List<Quote>? = null

        @Volatile
        var wordsIndex: Map<String, Quote>? = null

        @Volatile
        var countCache: Map<Category?, Int>? = null

        val lock = Any()
    }
}

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

    /** Lines look like `category|text|author`. */
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
                        text = rest.substringBeforeLast('|').trim(),
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

    /** The quotes eligible for rotation, based on the categories the user turned on. */
    fun pool(): List<Quote> {
        val enabled = prefs.enabledCategories
        val mine = if (Category.CUSTOM in enabled) custom() else emptyList()
        return mine + bundled().filter { q -> q.category?.let { it in enabled } == true }
    }

    /** How many quotes a category holds, whether or not it is turned on. */
    fun count(category: Category): Int =
        if (category == Category.CUSTOM) custom().size else bundled().count { it.category == category }

    fun current(): Quote? = prefs.currentText?.let { Quote(it, prefs.currentAuthor) }

    /** Picks a random quote from the pool (never the current one if there is a choice) and stores it. */
    fun advance(): Quote? {
        val pool = pool()
        val currentText = prefs.currentText
        val next = pool.filter { it.text != currentText }.randomOrNull() ?: pool.firstOrNull()
        if (next != null) {
            prefs.currentText = next.text
            prefs.currentAuthor = next.author
        }
        return next
    }

    private companion object {
        @Volatile
        var bundledCache: List<Quote>? = null
    }
}

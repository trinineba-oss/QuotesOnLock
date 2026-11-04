package com.trinineba.quotesonlock

import kotlin.random.Random

/**
 * The plain rules behind the quote files and the rotation, kept free of Android so they can be tested
 * on their own: parsing the bundled files, picking the next quote, and matching saved quotes back to
 * the current wording of the collection.
 */
internal object QuoteData {

    /** `category|text|author|subjects`; a literal `\n` in the text is a line break, subjects are optional. */
    fun parseQuotes(lines: Sequence<String>): List<Quote> = lines
        .map { it.trim() }
        .filter { it.isNotEmpty() && !it.startsWith("#") }
        .mapNotNull { line ->
            // Quotes never contain '|', so the fields split cleanly.
            val parts = line.split('|')
            val category = Category.fromKey(parts[0].trim())
            if (category == null || parts.size < 3) return@mapNotNull null
            Quote(
                text = parts[1].trim().replace("\\n", "\n"),
                author = parts[2].trim().ifBlank { null },
                category = category,
                subjects = parts.getOrNull(3)?.split(',')?.map { it.trim() }?.filter { it.isNotEmpty() } ?: emptyList(),
            )
        }
        .toList()

    /**
     * Great ideas and myths: `category|title|text|author|brief|kind|era|more`, where kind `q` is the idea in
     * its originator's words (with a brief explanation) and `e` an explanation only (the text is the explanation).
     */
    fun parseIdeas(lines: Sequence<String>): List<Quote> = lines
        .map { it.trim() }
        .filter { it.isNotEmpty() && !it.startsWith("#") }
        .mapNotNull { line ->
            val f = line.split('|')
            val category = Category.fromKey(f[0].trim())
            if (category == null || f.size < 7) return@mapNotNull null
            Quote(
                text = f[2].trim().replace("\\n", "\n"),
                author = f[3].trim().ifBlank { null },
                category = category,
                title = f[1].trim(),
                brief = f[4].trim().ifBlank { null },
                explained = f[5].trim() == "e",
                era = f[6].trim().ifBlank { null },
                more = f.getOrNull(7)?.trim()?.ifBlank { null },
            )
        }
        .toList()

    /** The key a quote is remembered by in the "already shown" set. */
    fun key(quote: Quote): String = "${quote.text}|${quote.author}".hashCode().toString()

    /**
     * A quote's words alone, ignoring case, spacing and punctuation, so a favourite saved before a
     * typographic fix (a full stop added, a capital letter) still finds its quote.
     */
    fun wordsKey(text: String): String = text.lowercase().filter { it.isLetterOrDigit() }

    /**
     * Which part of the pool to pick from this time. With a mix set (e.g. ideas 1 in 5), ideas are chosen
     * with that probability and the rest of the time something else is; with none set, everything has its
     * natural share. [roll] is a random number in 0..1.
     */
    fun mixedPick(all: List<Quote>, ideasMix: Int, mythsMix: Int, roll: Float): List<Quote> {
        val ideas = all.filter { it.category?.group == QuoteGroup.IDEAS }
        val myths = all.filter { it.category?.group == QuoteGroup.MYTHS }
        val pIdeas = ideasMix.takeIf { it > 0 && ideas.isNotEmpty() }?.let { 1f / it } ?: 0f
        val pMyths = mythsMix.takeIf { it > 0 && myths.isNotEmpty() }?.let { 1f / it } ?: 0f
        if (pIdeas == 0f && pMyths == 0f) return all
        return when {
            roll < pIdeas -> ideas
            roll < pIdeas + pMyths -> myths
            else -> all.filter {
                (pIdeas == 0f || it.category?.group != QuoteGroup.IDEAS) && (pMyths == 0f || it.category?.group != QuoteGroup.MYTHS)
            }.ifEmpty { all }
        }
    }

    /** The next quote and the updated "already shown" set. */
    class Pick(val quote: Quote, val seen: Set<String>)

    /**
     * Picks a quote from [pool] (the part of the mix chosen this time; [all] is everything switched on)
     * that hasn't been shown in this pass and isn't the current one. Once the pool has all been shown,
     * its pass starts over. The same author never comes twice in a row when there's a choice.
     */
    fun next(
        all: List<Quote>,
        pool: List<Quote>,
        seen: Set<String>,
        currentText: String?,
        currentAuthor: String?,
        allowRepeats: Boolean,
        random: Random = Random,
    ): Pick? {
        if (all.isEmpty()) return null
        var shown = seen
        var candidates = if (allowRepeats) {
            pool.filter { it.text != currentText }
        } else {
            pool.filter { key(it) !in shown && it.text != currentText }
        }
        if (candidates.isEmpty()) {
            // Everything in this part of the mix has been shown: start it over.
            shown = shown - pool.map { key(it) }.toSet()
            candidates = pool.filter { it.text != currentText }.ifEmpty { all.filter { it.text != currentText }.ifEmpty { all } }
        }
        val lastAuthor = authorName(currentAuthor)
        if (lastAuthor != null) {
            candidates.filter { authorName(it.author) != lastAuthor }.takeIf { it.isNotEmpty() }?.let { candidates = it }
        }
        val quote = candidates.random(random)
        return Pick(quote, shown + key(quote))
    }
}

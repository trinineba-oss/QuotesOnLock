package com.trinineba.quotesonlock

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class SmartSearchTest {
    private val quotes by lazy { File("src/main/res/raw/quotes.txt").useLines { QuoteData.parseQuotes(it) } }
    private val vocab by lazy { Smart.Vocabulary(quotes.asSequence().flatMap { sequenceOf(it.text, it.author.orEmpty()) }) }

    /** The quotes that answer [query], best first, as Search ranks them by text and author. */
    private fun find(query: String): List<Quote> {
        val q = Smart.parse(query, vocab)
        return quotes.map { it to Smart.score(it.text + " — " + it.author.orEmpty(), q) }.filter { it.second > 0f }.sortedByDescending { it.second }.map { it.first }
    }

    @Test fun wordsAreWords() {
        assertTrue(Smart.hasWord("the wars of men", "war"))
        assertFalse(Smart.hasWord("a warm hearth", "war"))
        assertFalse(Smart.hasWord("in god we trust", "go"))
        assertTrue(Smart.hasWord("let it go", "go"))
        assertTrue(Smart.hasWord("i have loved", "love"))
        assertTrue(Smart.hasWord("forgiveness is", Smart.stem("forgiving")))
    }

    @Test fun stems() {
        assertEquals("let", Smart.stem("letting"))
        assertEquals("worry", Smart.stem("worries"))
        assertEquals("forgiv", Smart.stem("forgiving"))
        assertEquals("kind", Smart.stem("kindness"))
        assertEquals("happy", Smart.stem("happiness"))
    }

    @Test fun fillerIsIgnored() {
        val q = Smart.parse("quotes about letting go of fear")
        assertEquals(listOf("letting", "go", "fear"), q.terms.map { it.word })
        assertEquals(listOf("sleep"), Smart.parse("something for when I can't sleep").terms.map { it.word })
    }

    @Test fun typosAreForgiven() {
        val q = Smart.parse("Neitzsche", vocab)
        assertEquals("nietzsche", q.corrected)
        assertTrue(find("Neitzsche").isNotEmpty())
        assertTrue(find("Neitzsche").first().author.orEmpty().contains("Nietzsche"))
    }

    @Test fun wordsInAnyOrder() {
        // Before, only the exact phrase "letting go of fear" matched (none do).
        assertTrue(find("letting go of fear").isNotEmpty())
        assertTrue(find("fear of letting go").isNotEmpty())
        assertTrue(find("forgiving").any { it.text.contains("forgive", ignoreCase = true) })
    }

    @Test fun meaningWordsHelp() {
        val sleep = find("something for when I can't sleep")
        assertTrue(sleep.size > 20)
        val starting = find("quotes about starting over")
        assertTrue(starting.isNotEmpty())
    }

    @Test fun exactPhraseComesFirst() {
        val hits = find("to be or not to be")
        assertTrue(hits.isNotEmpty())
        assertTrue(hits.first().text.lowercase().contains("to be, or not to be") || hits.first().text.lowercase().contains("to be or not to be"))
    }

    @Test fun quickEnough() {
        val norm = quotes.map { Smart.normalise(it.text + " — " + it.author.orEmpty()) }
        val q = Smart.parse("courage in the face of fear", vocab)
        val start = System.nanoTime()
        repeat(5) { norm.count { Smart.scoreNormalised(it, q) > 0f } }
        val ms = (System.nanoTime() - start) / 5_000_000
        println("one search over ${norm.size} quotes: $ms ms")
        assertTrue("search took $ms ms", ms < 400)
    }
}

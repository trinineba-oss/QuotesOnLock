package com.trinineba.quotesonlock

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** The bundled quote, idea and bio files: every line must load, and nothing may be lost or doubled. */
class QuoteFilesTest {
    private fun raw(name: String) = File("src/main/res/raw/$name")
    private fun dataLines(name: String) = raw(name).readLines().filter { it.isNotBlank() && !it.startsWith("#") }

    private val quotes by lazy { raw("quotes.txt").useLines { QuoteData.parseQuotes(it) } }
    private val ideas by lazy { raw("ideas.txt").useLines { QuoteData.parseIdeas(it) } }

    @Test fun everyQuoteLineLoads() {
        assertEquals("lines dropped by the parser (unknown category or missing field)", dataLines("quotes.txt").size, quotes.size)
        assertTrue("suspiciously few quotes: ${quotes.size}", quotes.size > 10_000)
    }

    @Test fun everyIdeaLineLoadsWithItsExplanations() {
        assertEquals(dataLines("ideas.txt").size, ideas.size)
        ideas.forEach { idea ->
            assertTrue("${idea.title}: no fuller explanation", !idea.more.isNullOrBlank())
            if (!idea.explained) assertTrue("${idea.title}: quoted idea without a brief", !idea.brief.isNullOrBlank())
            assertTrue("${idea.title}: not in an idea or myth group", idea.category?.group == QuoteGroup.IDEAS || idea.category?.group == QuoteGroup.MYTHS)
        }
    }

    @Test fun noQuoteIsThereTwice() {
        val dup = quotes.groupBy { QuoteData.wordsKey(it.text) }.filterValues { it.size > 1 }.keys
        assertTrue("duplicates: ${dup.take(5)}", dup.isEmpty())
        val titles = ideas.groupBy { it.title }.filterValues { it.size > 1 }.keys
        assertTrue("duplicate idea titles: $titles", titles.isEmpty())
    }

    @Test fun quotesAreTidy() {
        quotes.forEach { q ->
            assertTrue("empty quote by ${q.author}", q.text.isNotBlank())
            assertTrue("starts in lower case: ${q.text.take(40)}", !q.text.first().isLowerCase())
            assertTrue("unbalanced curly quotes: ${q.text.take(60)}", q.text.count { it == '“' } >= q.text.count { it == '”' } - 1)
        }
    }

    @Test fun everyBookCategoryHasQuotes() {
        val counts = (quotes + ideas).groupingBy { it.category }.eachCount()
        val empty = Category.entries.filter { it != Category.CUSTOM && (counts[it] ?: 0) == 0 }
            // Ghalib is listed for later; no public-domain translation has been found yet.
            .filter { it.key != "ghalib" }
        assertTrue("categories with nothing in them: $empty", empty.isEmpty())
    }

    @Test fun biosAreWellFormed() {
        dataLines("bios.txt").forEach { line ->
            val (name, bio) = line.split('|', limit = 2).let { it[0] to it.getOrElse(1) { "" } }
            assertTrue("bio without text: $name", bio.isNotBlank())
        }
    }
}

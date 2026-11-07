package com.trinineba.quotesonlock

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** The data behind Context, Dialogues and the Timeline. */
class DiscoverFilesTest {
    private fun raw(name: String) = File("src/main/res/raw/$name")
    private val quotes by lazy { raw("quotes.txt").useLines { QuoteData.parseQuotes(it) } }

    @Test fun contextPassagesAreWellFormedAndFindTheirQuotes() {
        val lines = raw("context.txt").readLines().filter { it.isNotBlank() && !it.startsWith("#") }
        assertTrue("too few passages: ${lines.size}", lines.size > 5000)
        lines.forEach { assertEquals("bad line: ${it.take(60)}", 5, it.split('\t').size) }
        // Most bundled public-domain quotes should find their passage by the app's own key.
        val keys = lines.map { it.substringBefore('\t') }.toSet()
        val found = quotes.count { QuoteContext.key(it.text) in keys }
        assertTrue("only $found quotes find a passage", found > 5000)
    }

    @Test fun moreLikeThisCoversTheCollectionAndPointsIntoIt() {
        val ideas = raw("ideas.txt").useLines { QuoteData.parseIdeas(it) }
        val all = quotes + ideas
        val lines = raw("similar.txt").readLines().filter { it.isNotBlank() && !it.startsWith("#") }
        val rows = lines.associate { line ->
            val keys = line.trim().split(' ').map { it.toInt(36) }
            keys.first() to keys.drop(1)
        }
        // The app's key (made in Kotlin) must match the one the build script wrote (made in Python).
        val known = all.map { MoreLikeThis.key(it.text) }.toSet()
        val covered = all.count { MoreLikeThis.key(it.text) in rows }
        assertTrue("only $covered of ${all.size} lines have matches", covered >= all.size * 99 / 100)
        val dangling = rows.values.flatten().count { it !in known }
        assertTrue("$dangling matches point at nothing", dangling == 0)
        assertTrue(rows.values.all { it.size >= 3 })
    }

    @Test fun everyDialogueQuotesTheCollectionExactly() {
        val texts = quotes.map { it.text }.toSet()
        val lines = raw("dialogues.txt").readLines().filter { it.isNotBlank() && !it.startsWith("#") }
        assertTrue(lines.size >= 20)
        lines.forEach { l ->
            val f = l.split('|')
            assertEquals(10, f.size)
            assertTrue("not in the collection: ${f[1].take(40)}", f[1].replace("\\n", "\n") in texts)
            assertTrue("not in the collection: ${f[4].take(40)}", f[4].replace("\\n", "\n") in texts)
        }
    }

    @Test fun timelineLivesAreSensible() {
        val lines = raw("timeline.txt").readLines().filter { it.isNotBlank() && !it.startsWith("#") }
        assertTrue(lines.size > 250)
        lines.forEach { l ->
            val f = l.split('|')
            val born = f[2].toInt()
            f[3].toIntOrNull()?.let { died -> assertTrue("$l", died > born && died - born < 110) }
            assertTrue(f[4] in setOf("thinkers", "poets", "writers", "mystics"))
        }
        assertEquals("5th century BC", Timeline.era(-470))
        assertEquals("19th century", Timeline.era(1855))
        assertEquals("21st century", Timeline.era(2001))
    }
}

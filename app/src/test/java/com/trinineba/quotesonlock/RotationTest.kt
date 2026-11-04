package com.trinineba.quotesonlock

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/** The rotation: every quote once before any repeats, never the same twice running, and the mix. */
class RotationTest {
    private fun q(text: String, author: String, category: Category = Category.STOIC) = Quote(text, author, category)

    private val pool = (1..30).map { q("Quote $it", "Author ${it % 6}") }

    @Test fun showsEveryQuoteOnceBeforeRepeating() {
        var seen = emptySet<String>()
        var current: Quote? = null
        val shown = mutableListOf<String>()
        repeat(pool.size) {
            val pick = QuoteData.next(pool, pool, seen, current?.text, current?.author, allowRepeats = false, random = Random(it))!!
            shown += pick.quote.text
            seen = pick.seen
            current = pick.quote
        }
        assertEquals("a quote came back before the pass was over", pool.size, shown.toSet().size)
    }

    @Test fun startsOverAfterAFullPassWithoutRepeatingTheLastOne() {
        val seen = pool.map { QuoteData.key(it) }.toSet()
        val last = pool.last()
        val pick = QuoteData.next(pool, pool, seen, last.text, last.author, allowRepeats = false, random = Random(1))!!
        assertNotEquals(last.text, pick.quote.text)
        assertEquals("the new pass should start fresh", setOf(QuoteData.key(pick.quote)), pick.seen)
    }

    @Test fun avoidsTheSameAuthorTwiceInARow() {
        repeat(200) { seed ->
            val current = pool[seed % pool.size]
            val pick = QuoteData.next(pool, pool, emptySet(), current.text, current.author, allowRepeats = true, random = Random(seed))!!
            assertNotEquals(authorName(current.author), authorName(pick.quote.author))
        }
    }

    @Test fun aSingleQuoteStillWorks() {
        val one = listOf(q("Only", "Someone"))
        val pick = QuoteData.next(one, one, emptySet(), "Only", "Someone", allowRepeats = false)
        assertEquals("Only", pick!!.quote.text)
        assertNull(QuoteData.next(emptyList(), emptyList(), emptySet(), null, null, allowRepeats = false))
    }

    @Test fun theMixFavoursIdeasAndMythsAsSet() {
        val ideas = (1..5).map { q("Idea $it", "Thinker", Category.IDEA_PHILOSOPHY) }
        val myths = (1..5).map { q("Myth $it", "Teller", Category.MYTH_GREEK) }
        val all = pool + ideas + myths
        // Ideas 1 in 5, myths 1 in 5: a roll under 0.2 gives ideas, under 0.4 myths, otherwise neither.
        assertTrue(QuoteData.mixedPick(all, 5, 5, 0.1f).all { it in ideas })
        assertTrue(QuoteData.mixedPick(all, 5, 5, 0.3f).all { it in myths })
        assertTrue(QuoteData.mixedPick(all, 5, 5, 0.9f).none { it in ideas || it in myths })
        assertEquals("no mix set: everything has its natural share", all, QuoteData.mixedPick(all, 0, 0, 0.1f))
    }

    @Test fun favouritesMatchDespiteTidiedPunctuation() {
        assertEquals(QuoteData.wordsKey("The habit of despair is worse than despair itself."), QuoteData.wordsKey("the habit of despair is worse than despair itself"))
        assertNotEquals(QuoteData.wordsKey("Know thyself."), QuoteData.wordsKey("Know yourself."))
    }

    @Test fun authorNamesGroupWorksAndTranslations() {
        assertEquals("Leo Tolstoy", authorName("Leo Tolstoy, War and Peace"))
        assertEquals("Hafiz", authorName("Hafiz (tr. Gertrude Bell)"))
        assertEquals("Alfred, Lord Tennyson", authorName("Alfred, Lord Tennyson, In Memoriam"))
        assertNull(authorName(null))
    }

    @Test fun readAloudLeavesOutTranslatorsAndPausesAtLineBreaks() {
        val poem = Quote("I wandered lonely as a cloud\nThat floats on high", "Rumi (tr. Someone)")
        val spoken = QuoteVoice.spokenText(poem)
        assertTrue(spoken, spoken.contains("cloud, That floats"))
        assertTrue(spoken, spoken.trim().endsWith("Rumi."))
    }

    @Test fun readingHighlightPointsBackIntoTheShownText() {
        val shown = "\u201CFirst line\nsecond line. Then more.\u201D"
        val segs = QuoteVoice.bodySegments(shown)
        assertEquals(2, segs.size)
        for (seg in segs) {
            val map = seg.map!!
            assertEquals(seg.text.length, map.size)
            seg.text.forEachIndexed { i, c -> if (c != ',' && c != ' ') assertEquals(c, shown[map[i]]) }
        }
        assertTrue(segs[0].text, segs[0].text.contains("line, second"))
    }
}

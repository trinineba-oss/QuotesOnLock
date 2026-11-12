package com.trinineba.quotesonlock

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class CalmContentTest {
    private fun raw(name: String) = File("src/main/res/raw/$name")
    private val quotes by lazy { raw("quotes.txt").useLines { QuoteData.parseQuotes(it) } }

    @Test fun guidedSessionsParse() {
        val guides = raw("guided.txt").useLines { Guides.parse(it) }
        assertEquals(setOf("breathing", "bodyscan", "tension", "kindness", "sleep", "morning", "compassion", "leaves", "wave", "backtosleep", "walking"), guides.map { it.key }.toSet())
        guides.forEach { g ->
            assertTrue("${g.key} is empty", g.steps(false).size >= 5)
            assertTrue("${g.key}: long version not longer", g.steps(true).sumOf { it.pause } > g.steps(false).sumOf { it.pause })
            assertTrue("${g.key}: lengths", g.shortMinutes in 1..g.longMinutes)
            assertNotNull("${g.key}: sound", g.sound)
        }
    }

    @Test fun windDownPassagesAreInTheCollection() {
        WIND_DOWN.forEach { start -> assertTrue("missing: $start", quotes.any { it.text.startsWith(start) }) }
    }

    @Test fun practicesQuoteTheCollectionOrASource() {
        // Seneca's evening review is checked against Stewart's translation (Gutenberg #64576) when it is written;
        // the other lines come from the collection, word for word.
        Practices.all.filter { it.quote != null && it.key != "evening" }.forEach { p ->
            assertTrue("${p.key}: not in the collection", quotes.any { it.text.startsWith(p.quote!!.trimEnd('.')) })
        }
        assertEquals(Practices.all.size, Practices.all.map { it.key }.toSet().size)
    }

    @Test fun calmLinesAreRecordedInTheNaturalVoice() {
        // A changed line falls back to the phone's voice, so re-record (voice_index.txt) when the words change.
        fun normal(t: String) = t.replace("\\n", " ").replace(Regex("\\s+"), " ").trim()
        val index = raw("voice_index.txt").readLines().filter { it.isNotBlank() && !it.startsWith("#") }
            .associate { normal(it.substringAfter('|')) to it.substringBefore('|') }
        index.values.forEach { assertTrue("missing file $it", raw("$it.ogg").exists()) }
        val lines = mutableListOf<String>()
        raw("guided.txt").useLines { Guides.parse(it) }.forEach { g -> g.steps(true).forEach { lines += it.text } }
        Practices.all.forEach { p -> p.quote?.let { lines += it }; lines += p.spoken }
        WIND_DOWN.forEach { start -> lines += quotes.first { it.text.startsWith(start) }.text }
        lines.forEach { assertTrue("not recorded: ${it.take(60)}", normal(it) in index) }
    }
}

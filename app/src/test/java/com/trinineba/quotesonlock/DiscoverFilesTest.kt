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

    @Test fun everyReadingPathStepIsInTheCollection() {
        val paths = raw("paths.txt").useLines { ReadingPaths.parse(it) }
        assertTrue("too few paths: ${paths.size}", paths.size >= 8)
        val all = quotes + raw("ideas.txt").useLines { QuoteData.parseIdeas(it) }
        paths.forEach { p ->
            assertTrue("${p.title}: too few steps", p.steps.size >= 5)
            p.steps.forEach { s -> assertTrue("${p.title}: step not found: ${s.ref.take(50)}", ReadingPaths.resolve(all, s) != null) }
        }
    }

    @Test fun everyLessonAndTopicPointsIntoTheCollection() {
        val ideas = raw("ideas.txt").useLines { QuoteData.parseIdeas(it) }
        val all = quotes + ideas
        val lessons = raw("lessons.txt").useLines { Learn.parseLessons(it) }
        assertTrue("too few lessons: ${lessons.size}", lessons.size >= 300)
        assertEquals("a lesson is listed twice", lessons.size, lessons.map { it.ref }.toSet().size)
        lessons.forEach { l ->
            assertTrue("lesson for an unknown idea: ${l.ref}", Learn.idea(all, l.ref) != null)
            assertEquals("${l.ref}: three different choices", 3, l.choices.toSet().size)
            assertTrue("${l.ref}: the answer is among the choices", l.answer in l.choices)
            assertTrue("${l.ref}: missing text", l.takeaway.isNotBlank() && l.question.isNotBlank() && l.why.isNotBlank())
        }
        val topics = raw("topics.txt").useLines { Learn.parseTopics(it) }
        assertTrue("too few topics: ${topics.size}", topics.size >= 6)
        assertTrue("too few courses", topics.count { it.course } >= 6)
        val refs = lessons.map { it.ref }.toSet()
        topics.forEach { t ->
            t.steps.forEach { s ->
                assertTrue("${t.title}: step not found: ${s.ref.take(50)}", Learn.resolve(all, s) != null)
                if (s.isIdea) assertTrue("${t.title}: no lesson for ${s.ref}", s.ref in refs)
            }
        }
    }

    @Test fun everyDiagramIllustratesAnIdea() {
        val ideas = raw("ideas.txt").useLines { QuoteData.parseIdeas(it) }.mapNotNull { it.title }.toSet()
        assertTrue("too few diagrams: ${Diagrams.titles.size}", Diagrams.titles.size >= 80)
        Diagrams.titles.forEach { assertTrue("diagram for an unknown idea: $it", it in ideas) }
    }

    @Test fun everyNoteBelongsToAQuote() {
        val lines = raw("notes.txt").readLines().filter { it.isNotBlank() && !it.startsWith("#") }
        assertTrue("too few notes: ${lines.size}", lines.size >= 70)
        val keys = quotes.map { QuoteContext.key(it.text) }.toSet()
        lines.forEach { line ->
            assertEquals("bad note line: ${line.take(60)}", 2, line.split('\t').size)
            assertTrue("note matches no quote: ${line.take(60)}", line.substringBefore('\t') in keys)
            assertTrue("note too long: ${line.take(60)}", line.length < 1200)
        }
    }

    @Test fun themesAndInfluencesQuoteTheCollectionExactly() {
        val texts = quotes.map { it.text }.toSet()
        val themes = raw("themes.txt").useLines { IdeaThemes.parse(it) }
        assertTrue("too few themes: ${themes.size}", themes.size >= 15)
        themes.forEach { t ->
            assertTrue("${t.name}: too few voices", t.voices.size >= 6)
            assertTrue("${t.name}: no note", t.note.isNotBlank())
            t.voices.forEach { v -> assertTrue("${t.name}: not in the collection: ${v.text.take(40)}", v.text in texts) }
        }
        val threads = raw("influences.txt").useLines { Influences.parse(it) }
        assertTrue("too few threads: ${threads.size}", threads.size >= 25)
        threads.forEach { th ->
            assertEquals("${th.title}: one link between each pair", th.nodes.size - 1, th.links.size)
            th.nodes.forEach { n ->
                assertTrue("${th.title}: not in the collection: ${n.text.take(40)}", n.text in texts)
                assertEquals("${th.title}: byline and author disagree", n.author, authorName(n.byline))
            }
        }
    }

    @Test fun everyWordMarksSomething() {
        val ideas = raw("ideas.txt").useLines { QuoteData.parseIdeas(it) }
        val all = quotes + ideas
        val words = raw("words.txt").useLines { Words.parse(it) }
        assertTrue("too few words: ${words.size}", words.size >= 50)
        words.forEach { w ->
            assertTrue("${w.original}: incomplete", w.note.isNotBlank() && w.meaning.isNotBlank() && w.language.isNotBlank())
            val one = listOf(w)
            assertTrue("${w.original} (${w.forms.first().first}) marks nothing", all.any { Words.find(one, it.shownText, it.category?.key).isNotEmpty() })
        }
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

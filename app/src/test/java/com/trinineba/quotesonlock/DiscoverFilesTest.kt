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

    @Test fun everyEssayLinePointsIntoTheCollection() {
        val ideas = raw("ideas.txt").useLines { QuoteData.parseIdeas(it) }
        val all = quotes + ideas
        val essays = raw("essays.txt").useLines { Essays.parse(it) }
        val paths = raw("paths.txt").useLines { ReadingPaths.parse(it) }.map { it.title }.toSet()
        assertTrue("too few essays: ${essays.size}", essays.size >= 4)
        essays.forEach { e ->
            assertTrue("${e.title}: too short", e.parts.count { it is EssayPart.Para } >= 8)
            assertTrue("${e.title}: too few voices", e.parts.count { it !is EssayPart.Para } >= 6)
            assertTrue("${e.title}: unknown path ${e.path}", e.path.isEmpty() || e.path == "letters" || e.path == "leonardo" || e.path == "plato" || e.path in paths)
            e.parts.filter { it !is EssayPart.Para }.forEach { p ->
                assertTrue("${e.title}: line not found: ${(p as? EssayPart.Line)?.text?.take(50) ?: (p as EssayPart.Idea).title}", Essays.resolve(all, p) != null)
            }
        }
    }

    @Test fun vanGoghLettersAreWholeAndUnderlineTheCollection() {
        val groups = raw("letters.txt").useLines { Letters.parse(it) }
        val letters = groups.flatMap { it.letters }
        assertTrue("too few letters: ${letters.size}", letters.size >= 40)
        assertEquals("a letter id is used twice", letters.size, letters.map { it.id }.toSet().size)
        letters.forEach { l ->
            assertTrue("${l.id}: no note", l.note.length > 40)
            assertTrue("${l.id}: unknown source", l.source == Letters.SOURCE_BONGER || l.source == Letters.SOURCE_LUDOVICI)
            assertTrue("${l.id}: empty", l.paras.isNotEmpty() && l.paras.all { it.isNotBlank() })
            l.paras.forEach { p -> assertTrue("${l.id}: stray characters in: ${p.take(60)}", p.none { it in "|_@{}[]<>#*~°¢" }) }
        }
        val underlined = letters.sumOf { l -> l.paras.sumOf { Letters.linesIn(quotes, it).size } }
        assertTrue("too few of his lines underlined: $underlined", underlined >= 60)
    }

    @Test fun platoScenesAreCompleteAndUnderlineTheCollection() {
        val dialogues = raw("plato.txt").useLines { Plato.parse(it) }
        val scenes = dialogues.flatMap { it.scenes }
        assertTrue("too few dialogues: ${dialogues.size}", dialogues.size >= 7)
        assertTrue("too few scenes: ${scenes.size}", scenes.size >= 25)
        assertEquals("a scene id is used twice", scenes.size, scenes.map { it.id }.toSet().size)
        dialogues.forEach { d -> assertTrue("${d.title}: no introduction", d.setting.isNotBlank() && d.who.length > 30) }
        scenes.forEach { s ->
            assertTrue("${s.id}: no note", s.note.length > 30)
            assertTrue("${s.id}: empty", s.paras.isNotEmpty())
            s.paras.forEach { p -> assertTrue("${s.id}: stray characters in: ${p.take(60)}", p.none { it in "|_{}<>#*~" }) }
        }
        assertTrue("the cave is missing", scenes.any { it.title.contains("cave") })
        val essays = raw("essays.txt").useLines { Essays.parse(it) }.map { it.title }.toSet()
        dialogues.forEach { d ->
            val a = d.analysis
            assertTrue("${d.title}: no analysis", a != null && a.sections.size >= 4 && a.questions.size >= 2)
            assertTrue("${d.title}: key scene ${a!!.keyScene} not found", d.scenes.any { it.title == a.keyScene })
            assertTrue("${d.title}: essay ${a.essay} not found", a.essay in essays)
        }
        assertTrue("speakers are not recognised", scenes.flatMap { it.paras }.count { Plato.speaker(it) != null } > 100)
        val underlined = scenes.sumOf { s -> s.paras.sumOf { Plato.linesIn(quotes, it).size } }
        assertTrue("too few of his lines underlined: $underlined", underlined >= 20)
    }

    @Test fun leonardoHighlightsAreCompleteAndUnderlineTheCollection() {
        val book = raw("leonardo.txt").useLines { Leonardo.parse(it) }
        val all = book.all
        assertTrue("too few themes: ${book.themes.size}", book.themes.size >= 8)
        assertTrue("too few highlights: ${all.size}", all.size >= 80)
        assertEquals("a highlight id is used twice", all.size, all.map { it.id }.toSet().size)
        all.forEach { h ->
            assertTrue("${h.id}: no note", h.note.length > 15)
            assertTrue("${h.id}: no text", h.paras.any { !Leonardo.isHeading(it) && it != "…" })
            h.paras.forEach { p -> assertTrue("${h.id}: stray characters in: ${p.take(60)}", p.none { it in "|_{}<>#*~" }) }
        }
        val underlined = all.sumOf { h -> h.paras.sumOf { Leonardo.linesIn(quotes, it).size } }
        assertTrue("too few of his lines underlined: $underlined", underlined >= 20)
    }

    @Test fun everyEverydayThingIsExplainedTaughtAndDrawn() {
        val ideas = raw("ideas.txt").useLines { QuoteData.parseIdeas(it) }
        val lessons = raw("lessons.txt").useLines { Learn.parseLessons(it) }.map { it.ref }.toSet()
        val sections = raw("things.txt").useLines { Things.parse(it) }
        assertTrue("too few sections: ${sections.size}", sections.size >= 6)
        val things = sections.flatMap { it.things }
        assertEquals("a thing is listed twice", things.size, things.map { it.ref }.toSet().size)
        things.forEach { t ->
            val idea = ideas.firstOrNull { it.title == t.ref }
            assertTrue("no explanation for ${t.ref}", idea != null)
            assertEquals("${t.ref}: not in How things work", Category.IDEA_THINGS, idea?.category)
            assertTrue("${t.ref}: no fuller explanation", !idea?.more.isNullOrBlank())
            assertTrue("${t.ref}: no lesson", t.ref in lessons)
            assertTrue("${t.ref}: no diagram", Diagrams.of(t.ref) != null)
        }
        assertEquals("an explanation missing from things.txt", ideas.count { it.category == Category.IDEA_THINGS }, things.size)
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

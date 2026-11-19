package com.trinineba.quotesonlock

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FindTest {
    @Test fun everydayWordsFindTheirSubject() {
        assertEquals(Subject.SUFFERING, Find.subjectFor("grief"))
        assertEquals(Subject.LOVE, Find.subjectFor("Love"))
        assertEquals(Subject.NATURE, Find.subjectFor("seasons"))
        assertNull(Find.subjectFor("xyzzy"))
        assertNull(Find.subjectFor("a"))
    }

    @Test fun snippetCentresOnTheMatchAndCutsAtWords() {
        val text = "It was a long day by the shore, and then the sea came in under a grey sky, slowly, as it always had and always will."
        val s = Find.snippet(text, "sea", 40)!!
        assertTrue(s, s.contains("sea"))
        assertTrue(s, s.startsWith("…") && s.endsWith("…"))
        assertNull(Find.snippet(text, "mountain"))
    }
}

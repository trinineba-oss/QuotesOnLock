package com.trinineba.quotesonlock

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

/** The daily quote's timing and the journal's questions. */
class FeaturesTest {
    private fun at(hour: Int, minute: Int) = Calendar.getInstance().apply {
        set(2026, Calendar.MARCH, 10, hour, minute, 0)
        set(Calendar.MILLISECOND, 0)
    }

    @Test fun dailyQuoteComesLaterToday() {
        assertEquals(60 * 60_000L, DailyQuote.millisUntil(9, 0, at(8, 0)))
    }

    @Test fun dailyQuoteWaitsForTomorrowOnceTheTimeHasPassed() {
        assertEquals(23 * 60 * 60_000L, DailyQuote.millisUntil(8, 0, at(9, 0)))
        assertEquals("exactly now means tomorrow, not twice today", 24 * 60 * 60_000L, DailyQuote.millisUntil(8, 0, at(8, 0)))
    }

    @Test fun eachQuoteKeepsItsReflectionQuestion() {
        val q = Quote("The unexamined life is not worth living.", "Plato")
        assertEquals(Journal.promptFor(q), Journal.promptFor(q.copy()))
        assertTrue(Journal.promptFor(q) in Journal.PROMPTS)
    }
}

package io.github.wkoller.glucosetracker.domain.schedule

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalTime

class CheckTimesTest {
    private fun times(vararg hhmm: String) = hhmm.map(LocalTime::parse)

    @Test
    fun `checks that follow each other through the day are in order`() {
        assertTrue(CheckTimes.inOrder(times("07:30", "12:00", "17:30", "21:00")))
        assertTrue(CheckTimes.inOrder(times("07:30", "17:30")))
        assertTrue(CheckTimes.inOrder(times("07:30")))
        assertTrue(CheckTimes.inOrder(emptyList()))
    }

    @Test
    fun `bedtime may be after midnight`() {
        assertTrue(CheckTimes.inOrder(times("07:30", "12:00", "17:30", "00:30")))
        // A night-shift day: breakfast in the afternoon and bedtime in the morning.
        assertTrue(CheckTimes.inOrder(times("14:00", "18:00", "23:00", "06:00")))
    }

    @Test
    fun `two checks at the same time are not in order`() {
        assertFalse(CheckTimes.inOrder(times("07:30", "12:00", "12:00", "21:00")))
    }

    @Test
    fun `a bedtime set to the morning by mistake is not in order`() {
        assertFalse(CheckTimes.inOrder(times("07:30", "12:00", "17:30", "09:00")))
    }
}

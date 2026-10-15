package io.github.wkoller.glucosetracker.domain.progress

import io.github.wkoller.glucosetracker.domain.Fixtures
import io.github.wkoller.glucosetracker.domain.model.GlucoseTargets
import io.github.wkoller.glucosetracker.domain.model.MeterFlag
import io.github.wkoller.glucosetracker.domain.model.Slot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LowPatternTest {
    private val targets = GlucoseTargets.DEFAULT
    private val today = Fixtures.day1

    private fun low(daysAgo: Long, hour: Int, tenths: Int = 35) =
        Fixtures.reading(Slot.LUNCH, tenths, date = today.minusDays(daysAgo), hour = hour)

    @Test
    fun `one low is not a pattern`() {
        assertNull(LowPatterns.recent(listOf(low(1, 12)), targets, today, null))
    }

    @Test
    fun `two separate lows in a week are a pattern`() {
        val pattern = LowPatterns.recent(listOf(low(1, 12), low(4, 8)), targets, today, null)!!
        assertEquals(2, pattern.episodes)
        assertEquals(low(1, 12).takenAt, pattern.latest)
    }

    @Test
    fun `a low and its recheck count as one episode`() {
        val low = low(1, 12)
        val recheck = low.copy(takenAt = low.takenAt.plusSeconds(15 * 60), tenths = 38)
        assertNull(LowPatterns.recent(listOf(low, recheck), targets, today, null))
    }

    @Test
    fun `a meter LO reading counts as low`() {
        val lo = low(2, 21, tenths = 11).copy(meterFlag = MeterFlag.LO)
        assertEquals(2, LowPatterns.recent(listOf(lo, low(5, 8)), targets, today, null)!!.episodes)
    }

    @Test
    fun `readings below target but not low do not count`() {
        assertNull(LowPatterns.recent(listOf(low(1, 12, tenths = 40), low(3, 12, tenths = 42)), targets, today, null))
    }

    @Test
    fun `lows more than a week ago do not count`() {
        assertNull(LowPatterns.recent(listOf(low(1, 12), low(7, 12)), targets, today, null))
        assertEquals(2, LowPatterns.recent(listOf(low(1, 12), low(6, 12)), targets, today, null)!!.episodes)
    }

    @Test
    fun `the notice stays away after it is acknowledged until a new low`() {
        val readings = listOf(low(1, 12), low(4, 8))
        val seen = low(1, 12).takenAt.plusSeconds(60)
        assertNull(LowPatterns.recent(readings, targets, today, seen))
        assertEquals(3, LowPatterns.recent(readings + low(0, 8), targets, today, seen)!!.episodes)
    }
}

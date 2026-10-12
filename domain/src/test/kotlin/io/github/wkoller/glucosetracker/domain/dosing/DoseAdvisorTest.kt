package io.github.wkoller.glucosetracker.domain.dosing

import io.github.wkoller.glucosetracker.domain.Fixtures
import io.github.wkoller.glucosetracker.domain.Fixtures.day1
import io.github.wkoller.glucosetracker.domain.model.MeterFlag
import io.github.wkoller.glucosetracker.domain.model.Slot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DoseAdvisorTest {
    private val plan = Fixtures.plan()
    private val now = Fixtures.at(day1, 12, 5)

    private fun advise(slot: Slot, tenths: Int, flag: MeterFlag? = null, earlier: List<io.github.wkoller.glucosetracker.domain.model.DoseRecord> = emptyList()) =
        DoseAdvisor.advise(plan, slot, tenths, flag, now, day1, earlier)

    @Test
    fun `a low reading gives no dose and a hypo notice`() {
        val advice = advise(Slot.LUNCH, 39)
        assertTrue(advice.isHypo)
        assertEquals(0, advice.units)
        assertNull(advice.bandIndex)
        assertEquals(GlucoseStatus.LOW, advice.status)
    }

    @Test
    fun `a reading of exactly 4 is not low`() {
        assertFalse(advise(Slot.LUNCH, 40).isHypo)
    }

    @Test
    fun `LO on the meter is treated as low`() {
        assertTrue(advise(Slot.LUNCH, 11, MeterFlag.LO).isHypo)
    }

    @Test
    fun `HI on the meter gives the top band with a notice`() {
        val advice = advise(Slot.LUNCH, 333, MeterFlag.HI)
        assertEquals(10, advice.units)
        assertTrue(DoseNotice.MeterHigh in advice.notices)
    }

    @Test
    fun `a reading above 20 is flagged as the top of the scale`() {
        val advice = advise(Slot.LUNCH, 201)
        assertEquals(10, advice.units)
        assertTrue(DoseNotice.TopOfScale in advice.notices)
        assertFalse(DoseNotice.TopOfScale in advise(Slot.LUNCH, 200).notices)
    }

    @Test
    fun `a bedtime dose is shown with a notice when the order lists meals only`() {
        val advice = advise(Slot.BEDTIME, 144)
        assertEquals(6, advice.units)
        assertTrue(DoseNotice.NotPrescribedTime in advice.notices)
        assertFalse(DoseNotice.NotPrescribedTime in advise(Slot.LUNCH, 144).notices)
    }

    @Test
    fun `no notices when the scale gives no dose`() {
        val advice = advise(Slot.BEDTIME, 78)
        assertEquals(0, advice.units)
        assertTrue(advice.notices.isEmpty())
        assertFalse(advice.needsDose)
    }

    @Test
    fun `a dose already recorded for the same time today is flagged`() {
        val earlier = listOf(Fixtures.correction(Slot.LUNCH, 4.0, Fixtures.at(day1, 9, 0)))
        val notice = advise(Slot.LUNCH, 137, earlier = earlier).alreadyDosed
        assertEquals(4.0, notice!!.amount, 0.0)
    }

    @Test
    fun `a dose in the last two hours is flagged whatever slot it was saved under`() {
        val earlier = listOf(Fixtures.correction(Slot.BREAKFAST, 2.0, Fixtures.at(day1, 10, 30)))
        assertTrue(advise(Slot.LUNCH, 137, earlier = earlier).alreadyDosed != null)
        val older = listOf(Fixtures.correction(Slot.BREAKFAST, 2.0, Fixtures.at(day1, 7, 30)))
        assertNull(advise(Slot.LUNCH, 137, earlier = older).alreadyDosed)
    }

    @Test
    fun `a dose on the same slot yesterday is not flagged`() {
        val yesterday = day1.minusDays(1)
        val earlier = listOf(Fixtures.correction(Slot.LUNCH, 4.0, Fixtures.at(yesterday, 12, 0), yesterday))
        assertNull(advise(Slot.LUNCH, 137, earlier = earlier).alreadyDosed)
    }
}

package io.github.wkoller.glucosetracker.domain.records

import io.github.wkoller.glucosetracker.domain.Fixtures
import io.github.wkoller.glucosetracker.domain.Fixtures.day1
import io.github.wkoller.glucosetracker.domain.dosing.GlucoseStatus
import io.github.wkoller.glucosetracker.domain.model.BodyMeasurement
import io.github.wkoller.glucosetracker.domain.model.DoseRecord
import io.github.wkoller.glucosetracker.domain.model.GlucoseReading
import io.github.wkoller.glucosetracker.domain.model.GlucoseTargets
import io.github.wkoller.glucosetracker.domain.model.MeterFlag
import io.github.wkoller.glucosetracker.domain.model.Slot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LogbookTest {
    private val targets = GlucoseTargets.DEFAULT

    private fun day(readings: List<GlucoseReading>, doses: List<DoseRecord> = emptyList(), measurements: List<BodyMeasurement> = emptyList()) =
        DailyLog.build(readings, doses, measurements).single()

    @Test
    fun `each reading carries the doses recorded with it`() {
        val breakfast = Fixtures.reading(Slot.BREAKFAST, 137, hour = 7, id = 1)
        val lunch = Fixtures.reading(Slot.LUNCH, 62, hour = 12, id = 2)
        val doses = listOf(
            Fixtures.correction(Slot.BREAKFAST, 4.0, Fixtures.at(day1, 7, 5)).copy(id = 10, readingId = 1),
            Fixtures.fixed(Fixtures.premix, Slot.BREAKFAST).copy(id = 11, readingId = 1),
        )
        val entries = Logbook.entries(day(listOf(lunch, breakfast), doses), targets)

        val checks = entries.filterIsInstance<CheckEntry>()
        assertEquals(listOf(Slot.BREAKFAST, Slot.LUNCH), checks.map { it.slot })
        assertEquals(listOf(10L, 11L), checks[0].doses.map { it.id })
        assertEquals(GlucoseStatus.ABOVE_TARGET, checks[0].status)
        assertEquals(emptyList<DoseRecord>(), checks[1].doses)
        assertEquals(GlucoseStatus.IN_TARGET, checks[1].status)
    }

    @Test
    fun `doses without a reading of their own form a separate entry`() {
        val breakfast = Fixtures.reading(Slot.BREAKFAST, 80, hour = 7, id = 1)
        val tablet = Fixtures.fixed(Fixtures.tablet, Slot.BREAKFAST).copy(id = 20, takenAt = Fixtures.at(day1, 9))
        val entries = Logbook.entries(day(listOf(breakfast), listOf(tablet)), targets)

        assertEquals(2, entries.size)
        val dosesOnly = entries[1] as CheckEntry
        assertNull(dosesOnly.reading)
        assertNull(dosesOnly.status)
        assertEquals(listOf(20L), dosesOnly.doses.map { it.id })
        assertEquals(Fixtures.at(day1, 9), dosesOnly.takenAt)
        assertEquals(emptyList<DoseRecord>(), (entries[0] as CheckEntry).doses)
    }

    @Test
    fun `entries are in the order they happened, with weights among the checks`() {
        val readings = listOf(
            Fixtures.reading(Slot.BEDTIME, 90, hour = 21, id = 3),
            Fixtures.reading(Slot.BREAKFAST, 80, hour = 6, id = 1),
        )
        val entries = Logbook.entries(day(readings, measurements = listOf(Fixtures.weight(day1, 116_000))), targets)

        assertEquals(listOf("reading-1", "measurement-0", "reading-3"), entries.map { it.key })
    }

    @Test
    fun `a meter that shows LO or HI is judged as low or above the range`() {
        val lo = Fixtures.reading(Slot.BREAKFAST, 11, hour = 7, id = 1).copy(meterFlag = MeterFlag.LO)
        val hi = Fixtures.reading(Slot.DINNER, 333, hour = 17, id = 2).copy(meterFlag = MeterFlag.HI)
        val statuses = Logbook.entries(day(listOf(lo, hi)), targets).map { (it as CheckEntry).status }

        assertEquals(listOf(GlucoseStatus.LOW, GlucoseStatus.ABOVE_TARGET), statuses)
    }

    @Test
    fun `notes come from the reading and its doses`() {
        val reading = Fixtures.reading(Slot.LUNCH, 80, hour = 12, id = 1).copy(note = "Did not feel like eating")
        val dose = Fixtures.correction(Slot.LUNCH, 2.0, Fixtures.at(day1, 12, 5)).copy(id = 5, readingId = 1, note = "Gave it late")
        val check = Logbook.entries(day(listOf(reading), listOf(dose)), targets).single() as CheckEntry

        assertEquals(listOf("Did not feel like eating", "Gave it late"), check.notes)
    }

    @Test
    fun `chart points cover every reading, oldest first`() {
        val day2 = day1.plusDays(1)
        val log = DailyLog.build(
            listOf(
                Fixtures.reading(Slot.BREAKFAST, 80, day1, hour = 7, id = 1),
                Fixtures.reading(Slot.BREAKFAST, 120, day2, hour = 7, id = 2),
                Fixtures.reading(Slot.LUNCH, 35, day2, hour = 12, id = 3),
            ),
            emptyList(),
            listOf(Fixtures.weight(day2, 116_000)),
        )
        val days = Logbook.days(log, targets)
        val points = Logbook.points(days)

        assertEquals(listOf(day2, day1), days.map { it.date })
        assertEquals(listOf(80, 120, 35), points.map { it.value })
        assertEquals(listOf(GlucoseStatus.IN_TARGET, GlucoseStatus.ABOVE_TARGET, GlucoseStatus.LOW), points.map { it.status })
        assertEquals(2, days[0].readings.size)
    }
}

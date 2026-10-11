package io.github.wkoller.glucosetracker.domain.dosing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SlidingScaleTest {
    private val scale = SlidingScale.COMMON

    @Test
    fun `band edges match the paper order`() {
        val expected = mapOf(
            0 to 0, 99 to 0, 100 to 0,
            101 to 2, 120 to 2,
            121 to 4, 140 to 4,
            141 to 6, 170 to 6,
            171 to 8, 200 to 8,
            201 to 10, 333 to 10,
        )
        expected.forEach { (tenths, units) -> assertEquals("reading $tenths", units, scale.unitsFor(tenths)) }
    }

    @Test
    fun `readings from the family spreadsheet give the same doses as its formula`() {
        val rows = mapOf(137 to 4, 185 to 8, 155 to 6, 144 to 6, 182 to 8, 78 to 0, 149 to 6, 150 to 6)
        rows.forEach { (tenths, units) -> assertEquals("reading $tenths", units, scale.unitsFor(tenths)) }
    }

    @Test
    fun `lower limits follow the previous band`() {
        assertEquals(listOf(0, 101, 121, 141, 171, 201), scale.bands.indices.map(scale::lowerTenths))
    }

    @Test
    fun `the common scale is valid`() {
        assertTrue(scale.isValid)
    }

    @Test
    fun `problems are reported for a badly formed scale`() {
        val bad = SlidingScale(listOf(ScaleBand(120, 2), ScaleBand(100, 0), ScaleBand(null, 60), ScaleBand(200, 8)))
        val problems = bad.problems()
        assertTrue(ScaleProblem.NotAscending(1) in problems)
        assertTrue(ScaleProblem.OpenBandNotLast(2) in problems)
        assertTrue(ScaleProblem.UnitsOutOfRange(2) in problems)
        assertTrue(ScaleProblem.LastBandNotOpen in problems)
        assertEquals(listOf(ScaleProblem.Empty), SlidingScale(emptyList()).problems())
    }
}

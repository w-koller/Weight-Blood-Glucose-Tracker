package io.github.wkoller.glucosetracker.domain.model

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GlucoseTargetsTest {

    @Test
    fun `the low level may be at or below the bottom of the target range`() {
        assertTrue(GlucoseTargets.DEFAULT.hypoAtOrBelowRange)
        assertTrue(GlucoseTargets(lowTenths = 50, highTenths = 100, hypoTenths = 40).hypoAtOrBelowRange)
    }

    @Test
    fun `a low level above the target range is refused`() {
        // 14 typed for 4 would call every reading under 14 low.
        assertFalse(GlucoseTargets(lowTenths = 40, highTenths = 100, hypoTenths = 140).hypoAtOrBelowRange)
    }
}

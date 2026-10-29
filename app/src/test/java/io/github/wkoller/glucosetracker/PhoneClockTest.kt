package io.github.wkoller.glucosetracker

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Clock
import java.time.ZoneId
import java.util.TimeZone

class PhoneClockTest {
    private val original: TimeZone = TimeZone.getDefault()

    @After
    fun restoreTimeZone() = TimeZone.setDefault(original)

    @Test
    fun `the clock follows the phone to a new time zone`() {
        val brisbane = ZoneId.of("Australia/Brisbane")
        val sydney = ZoneId.of("Australia/Sydney")
        TimeZone.setDefault(TimeZone.getTimeZone(brisbane))
        val startUpClock = Clock.systemDefaultZone()
        assertEquals(brisbane, PhoneClock.zone)

        // Crossing from Queensland into New South Wales in summer.
        TimeZone.setDefault(TimeZone.getTimeZone(sydney))
        assertEquals(sydney, PhoneClock.zone)
        // The clock the app used before kept the zone it started in.
        assertEquals(brisbane, startUpClock.zone)
    }
}

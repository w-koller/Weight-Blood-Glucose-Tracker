package io.github.wkoller.glucosetracker.domain.schedule

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalTime

class GreetingTest {
    @Test
    fun `greetings change at the agreed times`() {
        val cases = mapOf(
            LocalTime.of(0, 0) to Greeting.HELLO,
            LocalTime.of(4, 59) to Greeting.HELLO,
            LocalTime.of(5, 0) to Greeting.MORNING,
            LocalTime.of(11, 59) to Greeting.MORNING,
            LocalTime.of(12, 0) to Greeting.AFTERNOON,
            LocalTime.of(16, 59) to Greeting.AFTERNOON,
            LocalTime.of(17, 0) to Greeting.EVENING,
            LocalTime.of(21, 59) to Greeting.EVENING,
            LocalTime.of(23, 59) to Greeting.EVENING,
        )
        cases.forEach { (time, greeting) -> assertEquals("at $time", greeting, Greetings.at(time)) }
    }
}

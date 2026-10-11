package io.github.wkoller.glucosetracker.domain.schedule

import java.time.LocalTime

enum class Greeting { MORNING, AFTERNOON, EVENING, HELLO }

object Greetings {
    /** 5:00 to 11:59 morning, 12:00 to 16:59 afternoon, 17:00 to 23:59 evening, and "Hello" after midnight. */
    fun at(time: LocalTime): Greeting = when (time.hour) {
        in 5..11 -> Greeting.MORNING
        in 12..16 -> Greeting.AFTERNOON
        in 17..23 -> Greeting.EVENING
        else -> Greeting.HELLO
    }
}

package io.github.wkoller.glucosetracker

import java.time.Clock
import java.time.Instant
import java.time.ZoneId

/**
 * The phone's clock, in whatever time zone the phone is set to now. Clock.systemDefaultZone() keeps the zone the
 * app started in, so after travelling to another time zone, reminders and the dates of new records would follow
 * the old zone until the app was next started.
 */
object PhoneClock : Clock() {
    override fun getZone(): ZoneId = ZoneId.systemDefault()

    override fun withZone(zone: ZoneId): Clock = Clock.system(zone)

    override fun instant(): Instant = Instant.now()

    override fun millis(): Long = System.currentTimeMillis()
}

package io.github.wkoller.glucosetracker.domain.progress

import io.github.wkoller.glucosetracker.domain.dosing.DoseAdvisor
import io.github.wkoller.glucosetracker.domain.dosing.GlucoseStatus
import io.github.wkoller.glucosetracker.domain.model.GlucoseReading
import io.github.wkoller.glucosetracker.domain.model.GlucoseTargets
import java.time.Duration
import java.time.Instant
import java.time.LocalDate

/** Low readings that keep happening, which the person's doctor or diabetes educator should hear about. */
data class LowPattern(val episodes: Int, val days: Int, val latest: Instant)

/**
 * Finds repeated lows. A low and the recheck readings after it are one episode, so a single low
 * that takes a while to treat is not counted twice. The app only suggests telling the doctor; it
 * never changes or suggests a dose.
 */
object LowPatterns {
    const val DAYS = 7
    const val MIN_EPISODES = 2

    /** Low readings closer together than this belong to the same episode. */
    val EPISODE_GAP: Duration = Duration.ofHours(2)

    /**
     * The lows in the [DAYS] days up to and including [today], when there were at least
     * [MIN_EPISODES] episodes and the newest low came after [acknowledgedAt]. Null otherwise.
     */
    fun recent(readings: List<GlucoseReading>, targets: GlucoseTargets, today: LocalDate, acknowledgedAt: Instant?): LowPattern? {
        val from = today.minusDays(DAYS - 1L)
        val lows = readings
            .filter { !it.date.isBefore(from) && !it.date.isAfter(today) }
            .filter { DoseAdvisor.statusOf(it.tenths, it.meterFlag, targets) == GlucoseStatus.LOW }
            .map { it.takenAt }
            .sorted()
        if (lows.isEmpty()) return null
        val episodes = 1 + lows.zipWithNext().count { (earlier, later) -> Duration.between(earlier, later) >= EPISODE_GAP }
        val latest = lows.last()
        if (episodes < MIN_EPISODES) return null
        if (acknowledgedAt != null && !latest.isAfter(acknowledgedAt)) return null
        return LowPattern(episodes, DAYS, latest)
    }
}

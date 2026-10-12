package io.github.wkoller.glucosetracker.domain.progress

import io.github.wkoller.glucosetracker.domain.model.BodyMeasurement
import io.github.wkoller.glucosetracker.domain.model.Goals
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.ceil

/**
 * Progress towards a weight or waist goal, in grams or millimetres. A goal below the start means the
 * aim is to lose; a goal above the start means the aim is to gain.
 */
data class Progress(
    val start: Int?,
    val current: Int?,
    val currentDate: LocalDate?,
    val previous: Int?,
    val previousDate: LocalDate?,
    val goal: Int?,
) {
    val changeSinceStart: Int? get() = if (start != null && current != null) current - start else null
    val changeSincePrevious: Int? get() = if (previous != null && current != null) current - previous else null

    private val aimsToGain: Boolean get() = start != null && goal != null && goal > start

    /** How far is left to go. Zero or less means the goal has been reached. */
    val remaining: Int?
        get() = if (current != null && goal != null) {
            if (aimsToGain) goal - current else current - goal
        } else {
            null
        }

    val goalReached: Boolean get() = remaining?.let { it <= 0 } ?: false

    /** The share of the distance from start to goal covered so far, from 0 to 1. */
    val fraction: Double?
        get() {
            if (start == null || current == null || goal == null || start == goal) return null
            val covered = (start - current).toDouble() / (start - goal)
            return covered.coerceIn(0.0, 1.0)
        }

    /** The change needed each week to reach the goal by [targetDate], or null when it no longer applies. */
    fun perWeekNeeded(today: LocalDate, targetDate: LocalDate?): Int? {
        val left = remaining ?: return null
        if (targetDate == null || left <= 0 || !targetDate.isAfter(today)) return null
        val weeks = ChronoUnit.DAYS.between(today, targetDate) / 7.0
        return ceil(left / weeks).toInt()
    }
}

object ProgressCalculator {

    fun weight(goals: Goals, measurements: List<BodyMeasurement>): Progress =
        build(goals.startWeightGrams, goals.goalWeightGrams, measurements) { it.weightGrams }

    fun waist(goals: Goals, measurements: List<BodyMeasurement>): Progress =
        build(goals.startWaistMm, goals.goalWaistMm, measurements) { it.waistMm }

    private fun build(start: Int?, goal: Int?, measurements: List<BodyMeasurement>, value: (BodyMeasurement) -> Int?): Progress {
        val entries = measurements.filter { value(it) != null }.sortedBy { it.takenAt }
        val current = entries.lastOrNull()
        val previous = entries.getOrNull(entries.size - 2)
        return Progress(
            start = start ?: entries.firstOrNull()?.let(value),
            current = current?.let(value),
            currentDate = current?.date,
            previous = previous?.let(value),
            previousDate = previous?.date,
            goal = goal,
        )
    }
}

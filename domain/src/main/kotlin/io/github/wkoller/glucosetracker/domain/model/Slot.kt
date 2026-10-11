package io.github.wkoller.glucosetracker.domain.model

/** The times of day a reading or dose belongs to. [OTHER] covers extra checks, such as a recheck after a low reading. */
enum class Slot {
    BREAKFAST,
    LUNCH,
    DINNER,
    BEDTIME,
    OTHER;

    val isMeal: Boolean get() = this == BREAKFAST || this == LUNCH || this == DINNER

    companion object {
        val scheduled: List<Slot> = listOf(BREAKFAST, LUNCH, DINNER, BEDTIME)
    }
}

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

/**
 * What the app calls the four check times. The times themselves come from the plan; this only
 * changes the words, for people whose checks do not line up with meals.
 */
enum class SlotNaming {
    /** Breakfast, lunch, dinner and bedtime. */
    MEALS,

    /** Morning, afternoon, evening and night. */
    TIMES_OF_DAY,
}

package io.github.wkoller.glucosetracker.domain.model

import io.github.wkoller.glucosetracker.domain.dosing.SlidingScale
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime

/** What the prescription says for one time of day. */
data class SlotSchedule(
    val slot: Slot,
    val checkBgl: Boolean,
    val correctionPrescribed: Boolean,
    val time: LocalTime,
    val reminderOn: Boolean,
)

enum class MedicineKind { INSULIN, TABLET, OTHER }

/**
 * A medicine taken at fixed times, such as a premixed insulin or a tablet.
 * [key] stays the same across plan versions so history can be followed when a dose changes.
 */
data class ScheduledMedicine(
    val key: String,
    val name: String,
    val strength: String,
    val kind: MedicineKind,
    val unit: String,
    val doses: Map<Slot, Double>,
) {
    val displayName: String get() = if (strength.isBlank()) name else "$name $strength"
}

enum class Frequency { DAILY, WEEKLY, OFF }

data class Goals(
    val startDate: LocalDate,
    val startWeightGrams: Int?,
    val startWaistMm: Int?,
    val goalWeightGrams: Int?,
    val goalWaistMm: Int?,
    val targetDate: LocalDate?,
    val weighEvery: Frequency,
    val waistEvery: Frequency,
)

/** Glucose thresholds in tenths of mmol/L. */
data class GlucoseTargets(
    val lowTenths: Int,
    val highTenths: Int,
    val hypoTenths: Int,
) {
    /** See [hypoAtOrBelowRange] in the companion. */
    val hypoAtOrBelowRange: Boolean get() = hypoAtOrBelowRange(lowTenths, hypoTenths)

    companion object {
        /**
         * The low level must not be above the bottom of the target range. If it were, a reading could be called
         * low while it is in range, and the low-reading advice would be shown for a normal reading.
         */
        fun hypoAtOrBelowRange(lowTenths: Int, hypoTenths: Int): Boolean = hypoTenths <= lowTenths

        val DEFAULT = GlucoseTargets(lowTenths = 40, highTenths = 100, hypoTenths = 40)
    }
}

/**
 * One version of a user's plan. Every change creates a new version with the same [cycleId],
 * and readings keep the id of the version that was in place when they were taken.
 */
data class Plan(
    val id: Long,
    val cycleId: String,
    val version: Int,
    val effectiveFrom: Instant,
    val correctionInsulin: String,
    val prescriber: String,
    val orderDate: LocalDate?,
    val scale: SlidingScale,
    val slots: List<SlotSchedule>,
    val medicines: List<ScheduledMedicine>,
    val goals: Goals,
    val targets: GlucoseTargets,
) {
    fun schedule(slot: Slot): SlotSchedule? = slots.firstOrNull { it.slot == slot }

    fun isCorrectionPrescribed(slot: Slot): Boolean = schedule(slot)?.correctionPrescribed == true

    fun medicinesAt(slot: Slot): List<ScheduledMedicine> = medicines.filter { slot in it.doses }

    /** Slots with a glucose check or a medicine, in time order. */
    val activeSlots: List<SlotSchedule>
        get() = slots
            .filter { it.slot != Slot.OTHER && (it.checkBgl || medicinesAt(it.slot).isNotEmpty()) }
            .sortedBy { it.time }
}

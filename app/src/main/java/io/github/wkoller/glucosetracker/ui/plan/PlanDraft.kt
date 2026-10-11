package io.github.wkoller.glucosetracker.ui.plan

import io.github.wkoller.glucosetracker.domain.dosing.ScaleBand
import io.github.wkoller.glucosetracker.domain.dosing.ScaleProblem
import io.github.wkoller.glucosetracker.domain.dosing.SlidingScale
import io.github.wkoller.glucosetracker.domain.format.Measures
import io.github.wkoller.glucosetracker.domain.model.Frequency
import io.github.wkoller.glucosetracker.domain.model.GlucoseTargets
import io.github.wkoller.glucosetracker.domain.model.Goals
import io.github.wkoller.glucosetracker.domain.model.MedicineKind
import io.github.wkoller.glucosetracker.domain.model.Plan
import io.github.wkoller.glucosetracker.domain.model.ScheduledMedicine
import io.github.wkoller.glucosetracker.domain.model.Slot
import io.github.wkoller.glucosetracker.domain.model.SlotSchedule
import io.github.wkoller.glucosetracker.domain.validation.InputValidator
import io.github.wkoller.glucosetracker.domain.validation.NumberInput
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.util.UUID

/** A row of the scale as typed. [upper] is blank for the open top band. */
data class BandDraft(val upper: String, val units: String)

data class SlotDraft(
    val slot: Slot,
    val checkBgl: Boolean,
    val correctionPrescribed: Boolean,
    val time: LocalTime,
    val reminderOn: Boolean,
)

data class MedicineDraft(
    val key: String = UUID.randomUUID().toString(),
    val name: String = "",
    val strength: String = "",
    val kind: MedicineKind = MedicineKind.TABLET,
    val unit: String = "",
    val amounts: Map<Slot, String> = emptyMap(),
)

/** Everything the user enters while setting up or changing a plan, kept as text until it is saved. */
data class PlanDraft(
    val cycleId: String = UUID.randomUUID().toString(),
    val correctionInsulin: String = "",
    val prescriber: String = "",
    val orderDate: LocalDate? = null,
    val bands: List<BandDraft> = listOf(BandDraft("", "")),
    val scaleChecked: Boolean = false,
    val slots: List<SlotDraft> = DEFAULT_SLOTS,
    val medicines: List<MedicineDraft> = emptyList(),
    val startDate: LocalDate = LocalDate.now(),
    val startWeight: String = "",
    val startWaist: String = "",
    val goalWeight: String = "",
    val goalWaist: String = "",
    val targetDate: LocalDate? = null,
    val weighEvery: Frequency = Frequency.DAILY,
    val waistEvery: Frequency = Frequency.WEEKLY,
    val targetLow: String = Measures.formatTenths(GlucoseTargets.DEFAULT.lowTenths),
    val targetHigh: String = Measures.formatTenths(GlucoseTargets.DEFAULT.highTenths),
    val hypo: String = Measures.formatTenths(GlucoseTargets.DEFAULT.hypoTenths),
) {
    companion object {
        const val INSULIN_UNIT = "units"
        const val TABLET_UNIT = "tablet"

        fun unitFor(draft: MedicineDraft): String = when (draft.kind) {
            MedicineKind.INSULIN -> INSULIN_UNIT
            MedicineKind.TABLET -> TABLET_UNIT
            MedicineKind.OTHER -> draft.unit.trim()
        }

        val DEFAULT_SLOTS = listOf(
            SlotDraft(Slot.BREAKFAST, checkBgl = true, correctionPrescribed = true, time = LocalTime.of(7, 30), reminderOn = true),
            SlotDraft(Slot.LUNCH, checkBgl = true, correctionPrescribed = true, time = LocalTime.of(12, 0), reminderOn = true),
            SlotDraft(Slot.DINNER, checkBgl = true, correctionPrescribed = true, time = LocalTime.of(17, 30), reminderOn = true),
            SlotDraft(Slot.BEDTIME, checkBgl = true, correctionPrescribed = false, time = LocalTime.of(21, 0), reminderOn = true),
        )

        fun commonBands(): List<BandDraft> = SlidingScale.COMMON.bands.map {
            BandDraft(it.upperTenths?.let(Measures::formatTenthsShort) ?: "", it.units.toString())
        }

        fun from(plan: Plan): PlanDraft = PlanDraft(
            cycleId = plan.cycleId,
            correctionInsulin = plan.correctionInsulin,
            prescriber = plan.prescriber,
            orderDate = plan.orderDate,
            bands = plan.scale.bands.map { BandDraft(it.upperTenths?.let(Measures::formatTenthsShort) ?: "", it.units.toString()) },
            scaleChecked = true,
            slots = Slot.scheduled.map { slot ->
                plan.schedule(slot)?.let { SlotDraft(it.slot, it.checkBgl, it.correctionPrescribed, it.time, it.reminderOn) }
                    ?: DEFAULT_SLOTS.first { it.slot == slot }.copy(checkBgl = false, correctionPrescribed = false, reminderOn = false)
            },
            medicines = plan.medicines.map { medicine ->
                MedicineDraft(
                    key = medicine.key,
                    name = medicine.name,
                    strength = medicine.strength,
                    kind = medicine.kind,
                    unit = medicine.unit,
                    amounts = medicine.doses.mapValues { Measures.formatAmount(it.value) },
                )
            },
            startDate = plan.goals.startDate,
            startWeight = plan.goals.startWeightGrams?.let(Measures::formatKg) ?: "",
            startWaist = plan.goals.startWaistMm?.let(Measures::formatCm) ?: "",
            goalWeight = plan.goals.goalWeightGrams?.let(Measures::formatKg) ?: "",
            goalWaist = plan.goals.goalWaistMm?.let(Measures::formatCm) ?: "",
            targetDate = plan.goals.targetDate,
            weighEvery = plan.goals.weighEvery,
            waistEvery = plan.goals.waistEvery,
            targetLow = Measures.formatTenths(plan.targets.lowTenths),
            targetHigh = Measures.formatTenths(plan.targets.highTenths),
            hypo = Measures.formatTenths(plan.targets.hypoTenths),
        )
    }

    /** The scale as typed, or null if a number cannot be read. */
    fun parsedScale(): SlidingScale? {
        val bands = bands.mapIndexed { index, band ->
            val units = band.units.trim().toIntOrNull() ?: return null
            val upper = if (index == bands.lastIndex) {
                null
            } else {
                Measures.parseScaled(band.upper, 1)?.toInt() ?: return null
            }
            ScaleBand(upper, units)
        }
        return SlidingScale(bands)
    }

    /** The lowest reading of each band as text, worked out from the row above. */
    fun lowerBoundText(index: Int): String? {
        if (index == 0) return "0"
        val previous = bands.getOrNull(index - 1)?.upper ?: return null
        val tenths = Measures.parseScaled(previous, 1) ?: return null
        return Measures.formatTenthsShort(tenths.toInt() + 1)
    }

    fun toMedicine(draft: MedicineDraft): ScheduledMedicine = ScheduledMedicine(
        key = draft.key,
        name = draft.name.trim(),
        strength = draft.strength.trim(),
        kind = draft.kind,
        unit = unitFor(draft),
        doses = draft.amounts
            .filterValues { it.isNotBlank() }
            .mapValues { (_, text) -> (InputValidator.amount(text) as NumberInput.Valid).value },
    )

    /** Builds the plan. Only call this once every step has passed its checks. */
    fun toPlan(): Plan = Plan(
        id = 0,
        cycleId = cycleId,
        version = 0,
        effectiveFrom = Instant.EPOCH,
        correctionInsulin = correctionInsulin.trim(),
        prescriber = prescriber.trim(),
        orderDate = orderDate,
        scale = parsedScale()!!,
        slots = slots.map { SlotSchedule(it.slot, it.checkBgl, it.correctionPrescribed, it.time, it.reminderOn) },
        medicines = medicines.map(::toMedicine),
        goals = Goals(
            startDate = startDate,
            startWeightGrams = optionalValue(InputValidator.weight(startWeight)),
            startWaistMm = optionalValue(InputValidator.waist(startWaist)),
            goalWeightGrams = optionalValue(InputValidator.weight(goalWeight)),
            goalWaistMm = optionalValue(InputValidator.waist(goalWaist)),
            targetDate = targetDate,
            weighEvery = weighEvery,
            waistEvery = waistEvery,
        ),
        targets = GlucoseTargets(
            lowTenths = (InputValidator.bgl(targetLow) as NumberInput.Valid).value,
            highTenths = (InputValidator.bgl(targetHigh) as NumberInput.Valid).value,
            hypoTenths = (InputValidator.bgl(hypo) as NumberInput.Valid).value,
        ),
    )
}

private fun <T> optionalValue(input: NumberInput<T>): T? = (input as? NumberInput.Valid)?.value

/** Problems the user must fix before leaving a step. Each maps to a sentence in strings.xml. */
sealed interface DraftError {
    data object InsulinNameMissing : DraftError
    data class BandUpperInvalid(val row: Int) : DraftError
    data class BandUnitsInvalid(val row: Int) : DraftError
    data class BandNotAscending(val row: Int) : DraftError
    data class BandUpperOutOfRange(val row: Int) : DraftError
    data class BandUnitsOutOfRange(val row: Int) : DraftError
    data object ScaleNotChecked : DraftError
    data object NoCheckTimes : DraftError
    data object MedicineNameMissing : DraftError
    data object MedicineNoTimes : DraftError
    data object MedicineUnitMissing : DraftError
    data class MedicineAmountInvalid(val slot: Slot) : DraftError
    data object StartWeightInvalid : DraftError
    data object GoalWeightInvalid : DraftError
    data object StartWaistInvalid : DraftError
    data object GoalWaistInvalid : DraftError
    data object TargetDateBeforeStart : DraftError
    data object TargetLowInvalid : DraftError
    data object TargetHighInvalid : DraftError
    data object HypoInvalid : DraftError
    data object TargetRangeOrder : DraftError
}

object DraftValidator {

    fun insulin(draft: PlanDraft): List<DraftError> =
        if (draft.correctionInsulin.isBlank()) listOf(DraftError.InsulinNameMissing) else emptyList()

    fun scale(draft: PlanDraft): List<DraftError> {
        val errors = mutableListOf<DraftError>()
        draft.bands.forEachIndexed { index, band ->
            val row = index + 1
            if (index != draft.bands.lastIndex && Measures.parseScaled(band.upper, 1) == null) errors += DraftError.BandUpperInvalid(row)
            if (band.units.trim().toIntOrNull() == null) errors += DraftError.BandUnitsInvalid(row)
        }
        if (errors.isNotEmpty()) return errors
        val scale = draft.parsedScale() ?: return errors
        scale.problems().forEach { problem ->
            when (problem) {
                is ScaleProblem.NotAscending -> errors += DraftError.BandNotAscending(problem.index + 1)
                is ScaleProblem.UpperOutOfRange -> errors += DraftError.BandUpperOutOfRange(problem.index + 1)
                is ScaleProblem.UnitsOutOfRange -> errors += DraftError.BandUnitsOutOfRange(problem.index + 1)
                // The editor always keeps exactly one open band at the bottom, so these cannot happen.
                ScaleProblem.Empty, ScaleProblem.LastBandNotOpen, is ScaleProblem.OpenBandNotLast -> Unit
            }
        }
        return errors
    }

    fun scaleCheck(draft: PlanDraft): List<DraftError> =
        if (draft.scaleChecked) emptyList() else listOf(DraftError.ScaleNotChecked)

    fun times(draft: PlanDraft): List<DraftError> =
        if (draft.slots.none { it.checkBgl }) listOf(DraftError.NoCheckTimes) else emptyList()

    fun medicine(draft: MedicineDraft): List<DraftError> {
        val errors = mutableListOf<DraftError>()
        if (draft.name.isBlank()) errors += DraftError.MedicineNameMissing
        if (draft.kind == MedicineKind.OTHER && draft.unit.isBlank()) errors += DraftError.MedicineUnitMissing
        val filled = draft.amounts.filterValues { it.isNotBlank() }
        if (filled.isEmpty()) errors += DraftError.MedicineNoTimes
        filled.forEach { (slot, text) -> if (InputValidator.amount(text) !is NumberInput.Valid) errors += DraftError.MedicineAmountInvalid(slot) }
        return errors
    }

    fun goals(draft: PlanDraft): List<DraftError> {
        val errors = mutableListOf<DraftError>()
        if (!optionalOk(InputValidator.weight(draft.startWeight))) errors += DraftError.StartWeightInvalid
        if (!optionalOk(InputValidator.weight(draft.goalWeight))) errors += DraftError.GoalWeightInvalid
        if (!optionalOk(InputValidator.waist(draft.startWaist))) errors += DraftError.StartWaistInvalid
        if (!optionalOk(InputValidator.waist(draft.goalWaist))) errors += DraftError.GoalWaistInvalid
        if (draft.targetDate != null && !draft.targetDate.isAfter(draft.startDate)) errors += DraftError.TargetDateBeforeStart
        return errors
    }

    fun targets(draft: PlanDraft): List<DraftError> {
        val errors = mutableListOf<DraftError>()
        val low = InputValidator.bgl(draft.targetLow)
        val high = InputValidator.bgl(draft.targetHigh)
        val hypo = InputValidator.bgl(draft.hypo)
        if (low !is NumberInput.Valid) errors += DraftError.TargetLowInvalid
        if (high !is NumberInput.Valid) errors += DraftError.TargetHighInvalid
        if (hypo !is NumberInput.Valid) errors += DraftError.HypoInvalid
        if (low is NumberInput.Valid && high is NumberInput.Valid && low.value >= high.value) errors += DraftError.TargetRangeOrder
        return errors
    }

    private fun optionalOk(input: NumberInput<*>): Boolean = input is NumberInput.Valid || input is NumberInput.Empty
}

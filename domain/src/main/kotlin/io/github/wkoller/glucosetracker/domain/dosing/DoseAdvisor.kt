package io.github.wkoller.glucosetracker.domain.dosing

import io.github.wkoller.glucosetracker.domain.model.DoseKind
import io.github.wkoller.glucosetracker.domain.model.DoseRecord
import io.github.wkoller.glucosetracker.domain.model.DoseStatus
import io.github.wkoller.glucosetracker.domain.model.GlucoseTargets
import io.github.wkoller.glucosetracker.domain.model.MeterFlag
import io.github.wkoller.glucosetracker.domain.model.Plan
import io.github.wkoller.glucosetracker.domain.model.Slot
import java.time.Duration
import java.time.Instant
import java.time.LocalDate

enum class GlucoseStatus { LOW, BELOW_TARGET, IN_TARGET, ABOVE_TARGET }

sealed interface DoseNotice {
    /** The reading is below the plan's low threshold, so no correction insulin is shown. */
    data object Hypo : DoseNotice

    /** The scale gives a dose, but the prescription does not list the correction insulin at this time. */
    data object NotPrescribedTime : DoseNotice

    /** The reading is in the open top band of the scale. */
    data object TopOfScale : DoseNotice

    /** The meter showed HI, which is above the range it can measure. */
    data object MeterHigh : DoseNotice

    /** Correction insulin was already recorded for this time today, or very recently. */
    data class AlreadyDosed(val at: Instant, val amount: Double) : DoseNotice
}

data class DoseAdvice(
    val tenths: Int,
    val meterFlag: MeterFlag?,
    val status: GlucoseStatus,
    val units: Int,
    val bandIndex: Int?,
    val notices: List<DoseNotice>,
) {
    val isHypo: Boolean get() = DoseNotice.Hypo in notices
    val needsDose: Boolean get() = !isHypo && units > 0
    val alreadyDosed: DoseNotice.AlreadyDosed? get() = notices.filterIsInstance<DoseNotice.AlreadyDosed>().firstOrNull()
}

object DoseAdvisor {
    /** A correction dose recorded this recently counts as "already given", whatever slot it was saved under. */
    val RECENT_DOSE_WINDOW: Duration = Duration.ofHours(2)

    fun statusOf(tenths: Int, meterFlag: MeterFlag?, targets: GlucoseTargets): GlucoseStatus = when {
        meterFlag == MeterFlag.LO || tenths < targets.hypoTenths -> GlucoseStatus.LOW
        meterFlag == MeterFlag.HI || tenths > targets.highTenths -> GlucoseStatus.ABOVE_TARGET
        tenths < targets.lowTenths -> GlucoseStatus.BELOW_TARGET
        else -> GlucoseStatus.IN_TARGET
    }

    fun advise(
        plan: Plan,
        slot: Slot,
        tenths: Int,
        meterFlag: MeterFlag?,
        now: Instant,
        today: LocalDate,
        earlierDoses: List<DoseRecord>,
    ): DoseAdvice {
        val status = statusOf(tenths, meterFlag, plan.targets)
        if (status == GlucoseStatus.LOW) {
            return DoseAdvice(tenths, meterFlag, status, units = 0, bandIndex = null, notices = listOf(DoseNotice.Hypo))
        }

        val scale = plan.scale
        val bandIndex = if (meterFlag == MeterFlag.HI) scale.bands.lastIndex else scale.bandIndexFor(tenths)
        val units = scale.bands[bandIndex].units
        val notices = mutableListOf<DoseNotice>()

        if (meterFlag == MeterFlag.HI) {
            notices += DoseNotice.MeterHigh
        } else if (scale.isTopBand(bandIndex) && units > 0) {
            notices += DoseNotice.TopOfScale
        }
        if (units > 0) {
            if (!plan.isCorrectionPrescribed(slot)) notices += DoseNotice.NotPrescribedTime
            latestCorrectionDose(slot, now, today, earlierDoses)?.let {
                notices += DoseNotice.AlreadyDosed(it.takenAt, it.givenAmount ?: 0.0)
            }
        }
        return DoseAdvice(tenths, meterFlag, status, units, bandIndex, notices)
    }

    private fun latestCorrectionDose(
        slot: Slot,
        now: Instant,
        today: LocalDate,
        earlierDoses: List<DoseRecord>,
    ): DoseRecord? = earlierDoses
        .filter { it.kind == DoseKind.CORRECTION && it.status == DoseStatus.GIVEN && (it.givenAmount ?: 0.0) > 0.0 }
        .filter { dose ->
            val sameSlotToday = slot != Slot.OTHER && dose.slot == slot && dose.date == today
            val sinceDose = Duration.between(dose.takenAt, now)
            val recent = !sinceDose.isNegative && sinceDose <= RECENT_DOSE_WINDOW
            sameSlotToday || recent
        }
        .maxByOrNull { it.takenAt }
}

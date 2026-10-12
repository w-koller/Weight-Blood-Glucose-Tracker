package io.github.wkoller.glucosetracker.domain.progress

import io.github.wkoller.glucosetracker.domain.dosing.DoseAdvisor
import io.github.wkoller.glucosetracker.domain.dosing.GlucoseStatus
import io.github.wkoller.glucosetracker.domain.model.DoseKind
import io.github.wkoller.glucosetracker.domain.model.DoseRecord
import io.github.wkoller.glucosetracker.domain.model.DoseStatus
import io.github.wkoller.glucosetracker.domain.model.GlucoseReading
import io.github.wkoller.glucosetracker.domain.model.GlucoseTargets
import io.github.wkoller.glucosetracker.domain.model.MedicineKind
import io.github.wkoller.glucosetracker.domain.model.Slot
import kotlin.math.roundToInt

data class SlotSummary(
    val slot: Slot,
    val count: Int,
    val averageTenths: Int?,
    val lowestTenths: Int?,
    val highestTenths: Int?,
)

data class GlucoseSummary(
    val count: Int,
    val averageTenths: Int?,
    val lowest: GlucoseReading?,
    val highest: GlucoseReading?,
    val lowCount: Int,
    val belowTargetCount: Int,
    val inTargetCount: Int,
    val aboveTargetCount: Int,
    val perSlot: List<SlotSummary>,
) {
    /** Whole-number percentage of readings in the target range, or null with no readings. */
    val inTargetPercent: Int? get() = if (count == 0) null else (inTargetCount * 100.0 / count).roundToInt()
}

data class DoseSummary(
    val medicineName: String,
    val kind: DoseKind,
    val medicineKind: MedicineKind,
    val unit: String,
    val givenCount: Int,
    val skippedCount: Int,
    val differentCount: Int,
    val totalGiven: Double,
)

object GlucoseStats {

    fun summarise(readings: List<GlucoseReading>, targets: GlucoseTargets): GlucoseSummary {
        val statuses = readings.map { DoseAdvisor.statusOf(it.tenths, it.meterFlag, targets) }
        return GlucoseSummary(
            count = readings.size,
            averageTenths = readings.map { it.tenths }.averageOrNull(),
            lowest = readings.minByOrNull { it.tenths },
            highest = readings.maxByOrNull { it.tenths },
            lowCount = statuses.count { it == GlucoseStatus.LOW },
            belowTargetCount = statuses.count { it == GlucoseStatus.BELOW_TARGET },
            inTargetCount = statuses.count { it == GlucoseStatus.IN_TARGET },
            aboveTargetCount = statuses.count { it == GlucoseStatus.ABOVE_TARGET },
            perSlot = Slot.entries.mapNotNull { slot ->
                val values = readings.filter { it.slot == slot }.map { it.tenths }
                if (values.isEmpty()) null else SlotSummary(slot, values.size, values.averageOrNull(), values.min(), values.max())
            },
        )
    }

    fun summariseDoses(doses: List<DoseRecord>): List<DoseSummary> = doses
        .filter { it.status != DoseStatus.NOT_NEEDED }
        .groupBy { it.medicineKey ?: it.medicineName }
        .map { (_, group) ->
            val first = group.first()
            val given = group.filter { it.status == DoseStatus.GIVEN }
            DoseSummary(
                medicineName = group.maxBy { it.takenAt }.medicineName,
                kind = first.kind,
                medicineKind = first.medicineKind,
                unit = first.unit,
                givenCount = given.size,
                skippedCount = group.count { it.status == DoseStatus.SKIPPED },
                differentCount = group.count { it.differsFromPrescription },
                totalGiven = given.sumOf { it.givenAmount ?: 0.0 },
            )
        }
        .sortedWith(compareBy({ it.kind != DoseKind.CORRECTION }, { it.medicineName }))

    private fun List<Int>.averageOrNull(): Int? = if (isEmpty()) null else average().roundToInt()
}

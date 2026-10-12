package io.github.wkoller.glucosetracker.data.db

import io.github.wkoller.glucosetracker.domain.dosing.ScaleBand
import io.github.wkoller.glucosetracker.domain.dosing.SlidingScale
import io.github.wkoller.glucosetracker.domain.model.BodyMeasurement
import io.github.wkoller.glucosetracker.domain.model.DoseKind
import io.github.wkoller.glucosetracker.domain.model.DoseRecord
import io.github.wkoller.glucosetracker.domain.model.DoseStatus
import io.github.wkoller.glucosetracker.domain.model.Frequency
import io.github.wkoller.glucosetracker.domain.model.GlucoseReading
import io.github.wkoller.glucosetracker.domain.model.GlucoseTargets
import io.github.wkoller.glucosetracker.domain.model.Goals
import io.github.wkoller.glucosetracker.domain.model.MedicineKind
import io.github.wkoller.glucosetracker.domain.model.MeterFlag
import io.github.wkoller.glucosetracker.domain.model.Plan
import io.github.wkoller.glucosetracker.domain.model.ScheduledMedicine
import io.github.wkoller.glucosetracker.domain.model.SkipReason
import io.github.wkoller.glucosetracker.domain.model.Slot
import io.github.wkoller.glucosetracker.domain.model.SlotSchedule
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime

fun PlanWithDetails.toDomain(): Plan = Plan(
    id = plan.id,
    cycleId = plan.cycleId,
    version = plan.version,
    effectiveFrom = Instant.ofEpochMilli(plan.effectiveFrom),
    correctionInsulin = plan.correctionInsulin,
    prescriber = plan.prescriber,
    orderDate = plan.orderDate?.let(LocalDate::parse),
    scale = SlidingScale(bands.sortedBy { it.position }.map { ScaleBand(it.upperTenths, it.units) }),
    slots = slots.map {
        SlotSchedule(
            slot = Slot.valueOf(it.slot),
            checkBgl = it.checkBgl,
            correctionPrescribed = it.correctionPrescribed,
            time = LocalTime.of(it.minuteOfDay / 60, it.minuteOfDay % 60),
            reminderOn = it.reminderOn,
        )
    }.sortedBy { it.slot.ordinal },
    medicines = medicines.groupBy { it.medicineKey }.values
        .sortedBy { rows -> rows.minOf { it.position } }
        .map { rows ->
            val first = rows.first()
            ScheduledMedicine(
                key = first.medicineKey,
                name = first.name,
                strength = first.strength,
                kind = MedicineKind.valueOf(first.kind),
                unit = first.unit,
                doses = rows.sortedBy { Slot.valueOf(it.slot).ordinal }.associate { Slot.valueOf(it.slot) to it.amount },
            )
        },
    goals = Goals(
        startDate = LocalDate.parse(plan.startDate),
        startWeightGrams = plan.startWeightGrams,
        startWaistMm = plan.startWaistMm,
        goalWeightGrams = plan.goalWeightGrams,
        goalWaistMm = plan.goalWaistMm,
        targetDate = plan.targetDate?.let(LocalDate::parse),
        weighEvery = Frequency.valueOf(plan.weighEvery),
        waistEvery = Frequency.valueOf(plan.waistEvery),
    ),
    targets = GlucoseTargets(plan.targetLowTenths, plan.targetHighTenths, plan.hypoTenths),
)

/** The entities for a new version of [plan]. Ids are filled in by [PlanDao.insertFull]. */
fun Plan.toEntities(version: Int, effectiveFrom: Instant): PlanRows {
    val entity = PlanEntity(
        cycleId = cycleId,
        version = version,
        effectiveFrom = effectiveFrom.toEpochMilli(),
        correctionInsulin = correctionInsulin,
        prescriber = prescriber,
        orderDate = orderDate?.toString(),
        startDate = goals.startDate.toString(),
        startWeightGrams = goals.startWeightGrams,
        startWaistMm = goals.startWaistMm,
        goalWeightGrams = goals.goalWeightGrams,
        goalWaistMm = goals.goalWaistMm,
        targetDate = goals.targetDate?.toString(),
        weighEvery = goals.weighEvery.name,
        waistEvery = goals.waistEvery.name,
        targetLowTenths = targets.lowTenths,
        targetHighTenths = targets.highTenths,
        hypoTenths = targets.hypoTenths,
    )
    val bands = scale.bands.mapIndexed { index, band -> ScaleBandEntity(planId = 0, position = index, upperTenths = band.upperTenths, units = band.units) }
    val slotRows = slots.map {
        PlanSlotEntity(
            planId = 0,
            slot = it.slot.name,
            checkBgl = it.checkBgl,
            correctionPrescribed = it.correctionPrescribed,
            minuteOfDay = it.time.hour * 60 + it.time.minute,
            reminderOn = it.reminderOn,
        )
    }
    val medicineRows = medicines.flatMapIndexed { index, medicine ->
        medicine.doses.map { (slot, amount) ->
            PlanMedicineEntity(
                planId = 0,
                medicineKey = medicine.key,
                position = index,
                name = medicine.name,
                strength = medicine.strength,
                kind = medicine.kind.name,
                unit = medicine.unit,
                slot = slot.name,
                amount = amount,
            )
        }
    }
    return PlanRows(entity, bands, slotRows, medicineRows)
}

data class PlanRows(
    val plan: PlanEntity,
    val bands: List<ScaleBandEntity>,
    val slots: List<PlanSlotEntity>,
    val medicines: List<PlanMedicineEntity>,
)

fun GlucoseReadingEntity.toDomain() = GlucoseReading(
    id = id,
    planId = planId,
    takenAt = Instant.ofEpochMilli(takenAt),
    date = LocalDate.parse(date),
    slot = Slot.valueOf(slot),
    tenths = tenths,
    meterFlag = meterFlag?.let(MeterFlag::valueOf),
    note = note,
)

fun GlucoseReading.toEntity() = GlucoseReadingEntity(
    id = id,
    planId = planId,
    takenAt = takenAt.toEpochMilli(),
    date = date.toString(),
    slot = slot.name,
    tenths = tenths,
    meterFlag = meterFlag?.name,
    note = note,
)

fun DoseLogEntity.toDomain() = DoseRecord(
    id = id,
    planId = planId,
    readingId = readingId,
    takenAt = Instant.ofEpochMilli(takenAt),
    date = LocalDate.parse(date),
    slot = Slot.valueOf(slot),
    kind = DoseKind.valueOf(kind),
    medicineKey = medicineKey,
    medicineName = medicineName,
    medicineKind = MedicineKind.valueOf(medicineKind),
    prescribedAmount = prescribedAmount,
    givenAmount = givenAmount,
    unit = unit,
    status = DoseStatus.valueOf(status),
    reason = reason?.let(SkipReason::valueOf),
    note = note,
)

fun DoseRecord.toEntity() = DoseLogEntity(
    id = id,
    planId = planId,
    readingId = readingId,
    takenAt = takenAt.toEpochMilli(),
    date = date.toString(),
    slot = slot.name,
    kind = kind.name,
    medicineKey = medicineKey,
    medicineName = medicineName,
    medicineKind = medicineKind.name,
    prescribedAmount = prescribedAmount,
    givenAmount = givenAmount,
    unit = unit,
    status = status.name,
    reason = reason?.name,
    note = note,
)

fun BodyMeasurementEntity.toDomain() = BodyMeasurement(
    id = id,
    takenAt = Instant.ofEpochMilli(takenAt),
    date = LocalDate.parse(date),
    weightGrams = weightGrams,
    waistMm = waistMm,
    note = note,
)

fun BodyMeasurement.toEntity() = BodyMeasurementEntity(
    id = id,
    takenAt = takenAt.toEpochMilli(),
    date = date.toString(),
    weightGrams = weightGrams,
    waistMm = waistMm,
    note = note,
)

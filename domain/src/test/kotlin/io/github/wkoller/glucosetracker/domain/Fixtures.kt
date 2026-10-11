package io.github.wkoller.glucosetracker.domain

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
import io.github.wkoller.glucosetracker.domain.model.Plan
import io.github.wkoller.glucosetracker.domain.model.ScheduledMedicine
import io.github.wkoller.glucosetracker.domain.model.Slot
import io.github.wkoller.glucosetracker.domain.model.SlotSchedule
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

/** Test data shaped like the example prescription: correction insulin before meals only, a premixed insulin and a tablet. */
object Fixtures {
    val zone: ZoneId = ZoneId.of("Australia/Brisbane")
    val day1: LocalDate = LocalDate.of(2026, 10, 9)

    val premix = ScheduledMedicine(
        key = "premix",
        name = "NovoMix 30",
        strength = "",
        kind = MedicineKind.INSULIN,
        unit = "units",
        doses = mapOf(Slot.BREAKFAST to 30.0, Slot.DINNER to 26.0),
    )
    val tablet = ScheduledMedicine(
        key = "tablet",
        name = "Spironolactone",
        strength = "25 mg",
        kind = MedicineKind.TABLET,
        unit = "tablet",
        doses = mapOf(Slot.BREAKFAST to 1.0),
    )

    fun plan(
        medicines: List<ScheduledMedicine> = listOf(premix, tablet),
        weighEvery: Frequency = Frequency.OFF,
        waistEvery: Frequency = Frequency.OFF,
    ) = Plan(
        id = 1,
        cycleId = "cycle",
        version = 1,
        effectiveFrom = Instant.parse("2026-10-07T00:00:00Z"),
        correctionInsulin = "NovoRapid",
        prescriber = "",
        orderDate = LocalDate.of(2026, 10, 7),
        scale = SlidingScale.COMMON,
        slots = listOf(
            SlotSchedule(Slot.BREAKFAST, checkBgl = true, correctionPrescribed = true, time = LocalTime.of(7, 30), reminderOn = true),
            SlotSchedule(Slot.LUNCH, checkBgl = true, correctionPrescribed = true, time = LocalTime.of(12, 0), reminderOn = true),
            SlotSchedule(Slot.DINNER, checkBgl = true, correctionPrescribed = true, time = LocalTime.of(17, 30), reminderOn = true),
            SlotSchedule(Slot.BEDTIME, checkBgl = true, correctionPrescribed = false, time = LocalTime.of(21, 0), reminderOn = true),
        ),
        medicines = medicines,
        goals = Goals(
            startDate = LocalDate.of(2026, 10, 8),
            startWeightGrams = 115_000,
            startWaistMm = null,
            goalWeightGrams = 100_000,
            goalWaistMm = null,
            targetDate = null,
            weighEvery = weighEvery,
            waistEvery = waistEvery,
        ),
        targets = GlucoseTargets.DEFAULT,
    )

    fun at(date: LocalDate, hour: Int, minute: Int = 0): Instant = LocalDateTime.of(date, LocalTime.of(hour, minute)).atZone(zone).toInstant()

    fun reading(slot: Slot, tenths: Int, date: LocalDate = day1, hour: Int = 8, id: Long = 0) =
        GlucoseReading(id, planId = 1, takenAt = at(date, hour), date = date, slot = slot, tenths = tenths, meterFlag = null, note = null)

    fun correction(slot: Slot, units: Double, takenAt: Instant, date: LocalDate = day1) = DoseRecord(
        id = 0, planId = 1, readingId = null, takenAt = takenAt, date = date, slot = slot, kind = DoseKind.CORRECTION,
        medicineKey = null, medicineName = "NovoRapid", medicineKind = MedicineKind.INSULIN, prescribedAmount = units,
        givenAmount = units, unit = "units", status = DoseStatus.GIVEN, reason = null, note = null,
    )

    fun fixed(medicine: ScheduledMedicine, slot: Slot, status: DoseStatus = DoseStatus.GIVEN, date: LocalDate = day1) = DoseRecord(
        id = 0, planId = 1, readingId = null, takenAt = at(date, 8), date = date, slot = slot, kind = DoseKind.FIXED,
        medicineKey = medicine.key, medicineName = medicine.displayName, medicineKind = medicine.kind,
        prescribedAmount = medicine.doses[slot], givenAmount = if (status == DoseStatus.GIVEN) medicine.doses[slot] else null,
        unit = medicine.unit, status = status, reason = null, note = null,
    )

    fun weight(date: LocalDate, grams: Int) = BodyMeasurement(0, at(date, 7), date, weightGrams = grams, waistMm = null, note = null)
}

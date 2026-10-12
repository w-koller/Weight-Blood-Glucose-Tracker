package io.github.wkoller.glucosetracker.domain.model

import java.time.Instant
import java.time.LocalDate

/** LO and HI are what a glucose meter shows when a reading is outside the range it can measure. */
enum class MeterFlag { LO, HI }

data class GlucoseReading(
    val id: Long,
    val planId: Long,
    val takenAt: Instant,
    val date: LocalDate,
    val slot: Slot,
    val tenths: Int,
    val meterFlag: MeterFlag?,
    val note: String?,
)

/** CORRECTION is the sliding-scale insulin; FIXED is a medicine from the plan's list. */
enum class DoseKind { CORRECTION, FIXED }

enum class DoseStatus { GIVEN, SKIPPED, NOT_NEEDED }

enum class SkipReason { NOT_EATING, DOCTOR_ADVICE, LOW_READING, OTHER }

data class DoseRecord(
    val id: Long,
    val planId: Long,
    val readingId: Long?,
    val takenAt: Instant,
    val date: LocalDate,
    val slot: Slot,
    val kind: DoseKind,
    val medicineKey: String?,
    val medicineName: String,
    val medicineKind: MedicineKind,
    val prescribedAmount: Double?,
    val givenAmount: Double?,
    val unit: String,
    val status: DoseStatus,
    val reason: SkipReason?,
    val note: String?,
) {
    val differsFromPrescription: Boolean
        get() = status == DoseStatus.GIVEN && prescribedAmount != null && givenAmount != prescribedAmount
}

data class BodyMeasurement(
    val id: Long,
    val takenAt: Instant,
    val date: LocalDate,
    val weightGrams: Int?,
    val waistMm: Int?,
    val note: String?,
)

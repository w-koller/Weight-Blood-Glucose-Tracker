package io.github.wkoller.glucosetracker

import io.github.wkoller.glucosetracker.data.TrackerRepository
import io.github.wkoller.glucosetracker.domain.model.BodyMeasurement
import io.github.wkoller.glucosetracker.domain.model.DoseKind
import io.github.wkoller.glucosetracker.domain.model.DoseRecord
import io.github.wkoller.glucosetracker.domain.model.DoseStatus
import io.github.wkoller.glucosetracker.domain.model.GlucoseReading
import io.github.wkoller.glucosetracker.domain.model.MedicineKind
import io.github.wkoller.glucosetracker.domain.model.MeterFlag
import io.github.wkoller.glucosetracker.domain.model.Slot
import java.time.LocalDate
import java.time.ZoneId

/** Four days of checks, doses, a low with a note and a weight, for the logbook list. Used only in tests. */
object TestRecords {

    suspend fun seedLogbook(repository: TrackerRepository, today: LocalDate) {
        val planId = repository.currentPlan()!!.id
        val zone = ZoneId.systemDefault()

        suspend fun reading(date: LocalDate, slot: Slot, hour: Int, minute: Int, tenths: Int, flag: MeterFlag? = null, note: String? = null): Long =
            repository.addReading(GlucoseReading(0, planId, date.atTime(hour, minute).atZone(zone).toInstant(), date, slot, tenths, flag, note))

        suspend fun dose(readingId: Long, date: LocalDate, slot: Slot, hour: Int, minute: Int, name: String, kind: MedicineKind, amount: Double, status: DoseStatus = DoseStatus.GIVEN) {
            val correction = name == "NovoRapid"
            repository.addDose(
                DoseRecord(
                    id = 0, planId = planId, readingId = readingId, takenAt = date.atTime(hour, minute).atZone(zone).toInstant(), date = date, slot = slot,
                    kind = if (correction) DoseKind.CORRECTION else DoseKind.FIXED, medicineKey = if (correction) null else name.lowercase(),
                    medicineName = name, medicineKind = kind, prescribedAmount = amount, givenAmount = if (status == DoseStatus.GIVEN) amount else null,
                    unit = if (kind == MedicineKind.TABLET) "tablet" else "units", status = status, reason = null, note = null,
                ),
            )
        }

        suspend fun breakfast(date: LocalDate, tenths: Int, units: Double) {
            val id = reading(date, Slot.BREAKFAST, 7, 40, tenths)
            dose(id, date, Slot.BREAKFAST, 7, 45, "NovoRapid", MedicineKind.INSULIN, units, if (units > 0) DoseStatus.GIVEN else DoseStatus.NOT_NEEDED)
            dose(id, date, Slot.BREAKFAST, 7, 46, "NovoMix 30", MedicineKind.INSULIN, 30.0)
            dose(id, date, Slot.BREAKFAST, 7, 47, "Spironolactone 25 mg", MedicineKind.TABLET, 1.0)
            dose(id, date, Slot.BREAKFAST, 7, 47, "Furosemide 40 mg", MedicineKind.TABLET, 1.0)
        }

        breakfast(today, 137, 4.0)
        reading(today, Slot.LUNCH, 12, 10, 62)

        val yesterday = today.minusDays(1)
        breakfast(yesterday, 84, 0.0)
        reading(yesterday, Slot.LUNCH, 12, 5, 11, MeterFlag.LO, note = "Had a glass of juice and some jellybeans")
        reading(yesterday, Slot.LUNCH, 12, 25, 56)
        val dinner = reading(yesterday, Slot.DINNER, 17, 50, 152)
        dose(dinner, yesterday, Slot.DINNER, 17, 55, "NovoRapid", MedicineKind.INSULIN, 6.0)
        dose(dinner, yesterday, Slot.DINNER, 17, 56, "NovoMix 30", MedicineKind.INSULIN, 26.0)
        reading(yesterday, Slot.BEDTIME, 21, 30, 110)

        val twoDaysAgo = today.minusDays(2)
        repository.addMeasurement(BodyMeasurement(0, twoDaysAgo.atTime(7, 0).atZone(zone).toInstant(), twoDaysAgo, 116_400, null, null))
        breakfast(twoDaysAgo, 91, 0.0)
        reading(twoDaysAgo, Slot.DINNER, 18, 5, 187)
        reading(twoDaysAgo, Slot.BEDTIME, 21, 10, 333, MeterFlag.HI)

        val threeDaysAgo = today.minusDays(3)
        breakfast(threeDaysAgo, 72, 0.0)
        reading(threeDaysAgo, Slot.LUNCH, 12, 0, 101)
        reading(threeDaysAgo, Slot.DINNER, 17, 40, 129)
        reading(threeDaysAgo, Slot.BEDTIME, 21, 15, 98)
    }

    /** Four readings a day for the [days] days before the ones above, varied but the same on every run, so the full-screen chart has days to compare with. */
    suspend fun seedHistory(repository: TrackerRepository, today: LocalDate, days: Int = 30) {
        val planId = repository.currentPlan()!!.id
        val zone = ZoneId.systemDefault()
        val checks = listOf(Slot.BREAKFAST to 7, Slot.LUNCH to 12, Slot.DINNER to 17, Slot.BEDTIME to 21)
        (4 until 4 + days).forEach { back ->
            val date = today.minusDays(back.toLong())
            checks.forEachIndexed { index, (slot, hour) ->
                val tenths = 60 + (back * 37 + index * 53) % 110
                val at = date.atTime(hour, (back * 7 + index * 11) % 60).atZone(zone).toInstant()
                repository.addReading(GlucoseReading(0, planId, at, date, slot, tenths, null, null))
            }
        }
    }
}

package io.github.wkoller.glucosetracker

import io.github.wkoller.glucosetracker.domain.dosing.SlidingScale
import io.github.wkoller.glucosetracker.domain.model.Frequency
import io.github.wkoller.glucosetracker.domain.model.GlucoseTargets
import io.github.wkoller.glucosetracker.domain.model.Goals
import io.github.wkoller.glucosetracker.domain.model.MedicineKind
import io.github.wkoller.glucosetracker.domain.model.Plan
import io.github.wkoller.glucosetracker.domain.model.ScheduledMedicine
import io.github.wkoller.glucosetracker.domain.model.Slot
import io.github.wkoller.glucosetracker.domain.model.SlotSchedule
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime

/** A plan shaped like the example prescription, used only in tests. */
object TestPlans {
    fun example(cycleId: String = "cycle-1") = Plan(
        id = 0,
        cycleId = cycleId,
        version = 0,
        effectiveFrom = Instant.EPOCH,
        correctionInsulin = "NovoRapid",
        prescriber = "",
        orderDate = LocalDate.of(2026, 10, 7),
        scale = SlidingScale.COMMON,
        slots = listOf(
            SlotSchedule(Slot.BREAKFAST, true, true, LocalTime.of(7, 30), true),
            SlotSchedule(Slot.LUNCH, true, true, LocalTime.of(12, 0), true),
            SlotSchedule(Slot.DINNER, true, true, LocalTime.of(17, 30), true),
            SlotSchedule(Slot.BEDTIME, true, false, LocalTime.of(21, 0), true),
        ),
        medicines = listOf(
            ScheduledMedicine("premix", "NovoMix 30", "", MedicineKind.INSULIN, "units", mapOf(Slot.BREAKFAST to 30.0, Slot.DINNER to 26.0)),
            ScheduledMedicine("spiro", "Spironolactone", "25 mg", MedicineKind.TABLET, "tablet", mapOf(Slot.BREAKFAST to 1.0)),
            ScheduledMedicine("furo", "Furosemide", "40 mg", MedicineKind.TABLET, "tablet", mapOf(Slot.BREAKFAST to 1.0)),
        ),
        goals = Goals(LocalDate.of(2026, 10, 8), 115_000, null, 100_000, null, null, Frequency.DAILY, Frequency.WEEKLY),
        targets = GlucoseTargets.DEFAULT,
    )
}

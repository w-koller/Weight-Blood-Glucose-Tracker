package io.github.wkoller.glucosetracker.ui.components

import androidx.annotation.StringRes
import io.github.wkoller.glucosetracker.R
import io.github.wkoller.glucosetracker.domain.dosing.GlucoseStatus
import io.github.wkoller.glucosetracker.domain.model.Frequency
import io.github.wkoller.glucosetracker.domain.model.MedicineKind
import io.github.wkoller.glucosetracker.domain.model.SkipReason
import io.github.wkoller.glucosetracker.domain.model.Slot

/** "Breakfast", "Bedtime" and so on, for headings and lists. */
@StringRes
fun Slot.nameRes(): Int = when (this) {
    Slot.BREAKFAST -> R.string.slot_breakfast
    Slot.LUNCH -> R.string.slot_lunch
    Slot.DINNER -> R.string.slot_dinner
    Slot.BEDTIME -> R.string.slot_bedtime
    Slot.OTHER -> R.string.slot_other
}

/** "before breakfast", "at bedtime" and so on, for use inside a sentence. */
@StringRes
fun Slot.phraseRes(): Int = when (this) {
    Slot.BREAKFAST -> R.string.slot_phrase_breakfast
    Slot.LUNCH -> R.string.slot_phrase_lunch
    Slot.DINNER -> R.string.slot_phrase_dinner
    Slot.BEDTIME -> R.string.slot_phrase_bedtime
    Slot.OTHER -> R.string.slot_phrase_other
}

/** The column names used in the family's spreadsheet, kept for the export. */
@StringRes
fun Slot.exportRes(): Int = when (this) {
    Slot.BREAKFAST -> R.string.export_slot_morning
    Slot.LUNCH -> R.string.export_slot_midday
    Slot.DINNER -> R.string.export_slot_evening
    Slot.BEDTIME -> R.string.export_slot_bedtime
    Slot.OTHER -> R.string.export_slot_other
}

@StringRes
fun GlucoseStatus.labelRes(): Int = when (this) {
    GlucoseStatus.LOW -> R.string.status_low
    GlucoseStatus.BELOW_TARGET -> R.string.status_below_target
    GlucoseStatus.IN_TARGET -> R.string.status_in_target
    GlucoseStatus.ABOVE_TARGET -> R.string.status_above_target
}

@StringRes
fun MedicineKind.labelRes(): Int = when (this) {
    MedicineKind.INSULIN -> R.string.medicine_kind_insulin
    MedicineKind.TABLET -> R.string.medicine_kind_tablet
    MedicineKind.OTHER -> R.string.medicine_kind_other
}

@StringRes
fun Frequency.labelRes(): Int = when (this) {
    Frequency.DAILY -> R.string.frequency_daily
    Frequency.WEEKLY -> R.string.frequency_weekly
    Frequency.OFF -> R.string.frequency_off
}

@StringRes
fun SkipReason.labelRes(): Int = when (this) {
    SkipReason.NOT_EATING -> R.string.skip_not_eating
    SkipReason.DOCTOR_ADVICE -> R.string.skip_doctor
    SkipReason.LOW_READING -> R.string.skip_low
    SkipReason.OTHER -> R.string.skip_other
}

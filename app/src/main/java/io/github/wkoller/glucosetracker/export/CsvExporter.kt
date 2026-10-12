package io.github.wkoller.glucosetracker.export

import android.content.Context
import io.github.wkoller.glucosetracker.R
import io.github.wkoller.glucosetracker.domain.format.Measures
import io.github.wkoller.glucosetracker.domain.model.DoseKind
import io.github.wkoller.glucosetracker.domain.model.DoseRecord
import io.github.wkoller.glucosetracker.domain.model.DoseStatus
import io.github.wkoller.glucosetracker.domain.model.MedicineKind
import io.github.wkoller.glucosetracker.domain.model.MeterFlag
import io.github.wkoller.glucosetracker.domain.model.Plan
import io.github.wkoller.glucosetracker.domain.model.Slot
import io.github.wkoller.glucosetracker.domain.records.DayLog
import io.github.wkoller.glucosetracker.ui.UiFormat
import io.github.wkoller.glucosetracker.ui.components.exportRes

/**
 * A spreadsheet with one row per day, laid out like the family's tracker: the weight columns first,
 * then for each time of day the BGL followed by each insulin.
 */
class CsvExporter(private val context: Context) {

    fun build(plan: Plan, days: List<DayLog>): String {
        val ascending = days.sortedBy { it.date }
        val allDoses = ascending.flatMap { day -> day.slots.flatMap { it.doses } }
        val fixedInsulins = (plan.medicines.filter { it.kind == MedicineKind.INSULIN }.map { it.displayName } +
            allDoses.filter { it.kind == DoseKind.FIXED && it.medicineKind == MedicineKind.INSULIN }.map { it.medicineName }).distinct()
        val corrections = (listOf(plan.correctionInsulin) + allDoses.filter { it.kind == DoseKind.CORRECTION }.map { it.medicineName }).distinct()
        val insulins = fixedInsulins + corrections
        val slots = Slot.scheduled + if (ascending.any { it.slot(Slot.OTHER) != null }) listOf(Slot.OTHER) else emptyList()

        val header = buildList {
            add(context.getString(R.string.export_col_date))
            add(context.getString(R.string.export_col_weight))
            add(context.getString(R.string.export_col_change))
            add(context.getString(R.string.export_col_waist))
            slots.forEach { slot ->
                val name = context.getString(slot.exportRes())
                add(context.getString(R.string.export_col_bgl, name))
                insulins.forEach { add(context.getString(R.string.export_col_dose, name, it)) }
            }
            add(context.getString(R.string.export_col_other))
            add(context.getString(R.string.export_col_notes))
        }

        val rows = ascending.map { day ->
            buildList {
                add(UiFormat.dateNumeric(day.date))
                add(day.weightGrams?.let(Measures::formatKg) ?: "")
                val start = plan.goals.startWeightGrams
                add(if (start != null && day.weightGrams != null) signedKg(day.weightGrams!! - start) else "")
                add(day.waistMm?.let(Measures::formatCm) ?: "")
                slots.forEach { slot ->
                    val entry = day.slot(slot)
                    add(entry?.readings?.joinToString(" / ") { reading ->
                        when (reading.meterFlag) {
                            MeterFlag.LO -> "LO"
                            MeterFlag.HI -> "HI"
                            null -> Measures.formatTenths(reading.tenths)
                        }
                    } ?: "")
                    insulins.forEach { name -> add(doseCell(entry?.doses.orEmpty().filter { it.medicineName == name })) }
                }
                add(
                    day.slots.flatMap { it.fixedDoses }
                        .filter { it.medicineKind != MedicineKind.INSULIN }
                        .joinToString("; ") { dose ->
                            context.getString(if (dose.status == DoseStatus.GIVEN) R.string.export_taken else R.string.export_not_taken, dose.medicineName)
                        },
                )
                add(day.notes.joinToString("; "))
            }
        }
        // The byte-order mark lets Excel recognise the file as UTF-8.
        return BYTE_ORDER_MARK + (listOf(header) + rows).joinToString("\r\n") { row -> row.joinToString(",") { escape(it) } } + "\r\n"
    }

    private fun doseCell(doses: List<DoseRecord>): String = doses.joinToString(" / ") { dose ->
        when (dose.status) {
            DoseStatus.GIVEN -> Measures.formatAmount(dose.givenAmount ?: 0.0)
            DoseStatus.NOT_NEEDED -> "0"
            DoseStatus.SKIPPED -> context.getString(R.string.export_not_given)
        }
    }

    private fun signedKg(grams: Int): String = when {
        grams > 0 -> "+" + Measures.formatKgChange(grams)
        grams < 0 -> "-" + Measures.formatKgChange(grams)
        else -> "0"
    }

    private companion object {
        const val BYTE_ORDER_MARK = "\uFEFF"
    }

    private fun escape(value: String): String =
        if (value.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) "\"" + value.replace("\"", "\"\"") + "\"" else value
}

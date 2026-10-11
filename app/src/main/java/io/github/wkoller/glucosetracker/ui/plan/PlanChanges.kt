package io.github.wkoller.glucosetracker.ui.plan

import android.content.Context
import io.github.wkoller.glucosetracker.R
import io.github.wkoller.glucosetracker.domain.dosing.SlidingScale
import io.github.wkoller.glucosetracker.domain.format.Measures
import io.github.wkoller.glucosetracker.domain.model.Plan
import io.github.wkoller.glucosetracker.domain.model.Slot
import io.github.wkoller.glucosetracker.ui.UiFormat
import io.github.wkoller.glucosetracker.ui.components.phraseRes

/** Plain sentences describing what changed between two versions of a plan, shown before the change is saved. */
object PlanChanges {

    fun describe(context: Context, old: Plan, new: Plan): List<String> {
        val lines = mutableListOf<String>()
        fun add(res: Int, vararg args: Any) { lines += context.getString(res, *args) }
        fun slotPhrase(slot: Slot) = context.getString(slot.phraseRes())

        if (old.correctionInsulin != new.correctionInsulin) add(R.string.change_insulin, old.correctionInsulin, new.correctionInsulin)
        if (old.prescriber != new.prescriber) add(R.string.change_prescriber, new.prescriber.ifBlank { context.getString(R.string.not_set) })
        if (old.orderDate != new.orderDate) add(R.string.change_order_date, new.orderDate?.let(UiFormat::dateNumeric) ?: context.getString(R.string.not_set))

        val oldBands = bandMap(old.scale)
        val newBands = bandMap(new.scale)
        newBands.forEach { (range, units) ->
            val before = oldBands[range]
            when {
                before == null -> add(R.string.change_band_added, rangeText(context, range), units)
                before != units -> add(R.string.change_band_units, rangeText(context, range), before, units)
            }
        }
        oldBands.keys.filter { it !in newBands }.forEach { add(R.string.change_band_removed, rangeText(context, it)) }

        Slot.scheduled.forEach { slot ->
            val a = old.schedule(slot)
            val b = new.schedule(slot)
            if ((a?.checkBgl ?: false) != (b?.checkBgl ?: false)) {
                add(if (b?.checkBgl == true) R.string.change_check_on else R.string.change_check_off, slotPhrase(slot))
            }
            if ((a?.correctionPrescribed ?: false) != (b?.correctionPrescribed ?: false)) {
                add(if (b?.correctionPrescribed == true) R.string.change_prescribed_on else R.string.change_prescribed_off, new.correctionInsulin, slotPhrase(slot))
            }
            if (a != null && b != null && a.time != b.time) {
                add(R.string.change_slot_time, slotPhrase(slot), UiFormat.time(context, a.time), UiFormat.time(context, b.time))
            }
        }

        val oldMeds = old.medicines.associateBy { it.key }
        val newMeds = new.medicines.associateBy { it.key }
        newMeds.values.filter { it.key !in oldMeds }.forEach { add(R.string.change_medicine_added, it.displayName) }
        oldMeds.values.filter { it.key !in newMeds }.forEach { add(R.string.change_medicine_removed, it.displayName) }
        newMeds.values.filter { it.key in oldMeds }.forEach { medicine ->
            val before = oldMeds.getValue(medicine.key)
            if (before.displayName != medicine.displayName) add(R.string.change_medicine_renamed, before.displayName, medicine.displayName)
            (before.doses.keys + medicine.doses.keys).sortedBy { it.ordinal }.forEach { slot ->
                val was = before.doses[slot]
                val now = medicine.doses[slot]
                val dose = { amount: Double -> UiFormat.dose(context, amount, medicine.kind, medicine.unit) }
                when {
                    was == null && now != null -> add(R.string.change_medicine_time_added, medicine.displayName, slotPhrase(slot), dose(now))
                    was != null && now == null -> add(R.string.change_medicine_time_removed, medicine.displayName, slotPhrase(slot))
                    was != null && now != null && was != now -> add(R.string.change_medicine_amount, medicine.displayName, slotPhrase(slot), dose(was), dose(now))
                }
            }
        }

        val g0 = old.goals
        val g1 = new.goals
        if (g0.startDate != g1.startDate) add(R.string.change_start_date, UiFormat.dateNumeric(g1.startDate))
        if (g0.startWeightGrams != g1.startWeightGrams) add(R.string.change_start_weight, kg(context, g1.startWeightGrams))
        if (g0.goalWeightGrams != g1.goalWeightGrams) add(R.string.change_goal_weight, kg(context, g1.goalWeightGrams))
        if (g0.startWaistMm != g1.startWaistMm) add(R.string.change_start_waist, cm(context, g1.startWaistMm))
        if (g0.goalWaistMm != g1.goalWaistMm) add(R.string.change_goal_waist, cm(context, g1.goalWaistMm))
        if (g0.targetDate != g1.targetDate) add(R.string.change_target_date, g1.targetDate?.let(UiFormat::dateNumeric) ?: context.getString(R.string.not_set))
        if (g0.weighEvery != g1.weighEvery || g0.waistEvery != g1.waistEvery) add(R.string.change_measure_frequency)

        val t0 = old.targets
        val t1 = new.targets
        if (t0.lowTenths != t1.lowTenths || t0.highTenths != t1.highTenths) {
            add(R.string.change_target_range, Measures.formatTenths(t1.lowTenths), Measures.formatTenths(t1.highTenths))
        }
        if (t0.hypoTenths != t1.hypoTenths) add(R.string.change_hypo, Measures.formatTenths(t1.hypoTenths))
        return lines
    }

    private fun bandMap(scale: SlidingScale): Map<Pair<Int, Int?>, Int> =
        scale.bands.indices.associate { (scale.lowerTenths(it) to scale.bands[it].upperTenths) to scale.bands[it].units }

    private fun rangeText(context: Context, range: Pair<Int, Int?>): String {
        val (lower, upper) = range
        return if (upper == null) {
            context.getString(R.string.scale_above, Measures.formatTenthsShort(lower - 1))
        } else {
            context.getString(R.string.scale_range, Measures.formatTenthsShort(lower), Measures.formatTenthsShort(upper))
        }
    }

    private fun kg(context: Context, grams: Int?) =
        grams?.let { context.getString(R.string.kg_amount, Measures.formatKg(it)) } ?: context.getString(R.string.not_set)

    private fun cm(context: Context, mm: Int?) =
        mm?.let { context.getString(R.string.cm_amount, Measures.formatCm(it)) } ?: context.getString(R.string.not_set)
}

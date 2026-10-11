package io.github.wkoller.glucosetracker.domain.assistant

import io.github.wkoller.glucosetracker.domain.format.Measures
import io.github.wkoller.glucosetracker.domain.model.DoseKind
import io.github.wkoller.glucosetracker.domain.model.DoseRecord
import io.github.wkoller.glucosetracker.domain.model.DoseStatus
import io.github.wkoller.glucosetracker.domain.model.GlucoseReading
import io.github.wkoller.glucosetracker.domain.model.MeterFlag
import io.github.wkoller.glucosetracker.domain.model.Plan
import io.github.wkoller.glucosetracker.domain.model.Slot
import io.github.wkoller.glucosetracker.domain.progress.Progress
import io.github.wkoller.glucosetracker.domain.readout.ReadoutFacts
import io.github.wkoller.glucosetracker.domain.readout.ReadoutPeriod
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Builds the text given to the on-device language model. None of this is shown to the user. */
object PromptBuilder {

    private val dayFormat = DateTimeFormatter.ofPattern("EEE d MMM", Locale.forLanguageTag("en-AU"))

    fun systemPrompt(plan: Plan): String = """
        You are a friendly helper inside a weight and blood glucose tracking app for an older adult in Australia.
        You only use the facts given to you. If the facts do not answer the question, say that you do not know.
        You never work out, suggest or change an insulin dose or any other medicine. Doses come only from the
        app's check-in, which follows the sliding scale from the person's doctor. If asked about a dose, say that
        the check-in shows the dose from their prescription and that their doctor can answer questions about it.
        You do not give medical advice. For anything about their health or treatment, suggest they speak with
        their doctor or diabetes educator. Write short, complete sentences in plain Australian English, with
        Australian spelling. Blood glucose level is written as BGL after you have spelled it out once, and is
        measured in mmol/L. The person's sliding-scale insulin is ${plan.correctionInsulin}.
    """.trimIndent()

    fun summaryRequest(facts: ReadoutFacts): String {
        val span = if (facts.period == ReadoutPeriod.TODAY) "today" else "the last 7 days"
        return """
            Using only the facts above, write a short, warm summary of $span in 3 to 5 sentences. Mention the
            blood glucose readings, any low readings, the medicines recorded and the weight. Do not mention any
            insulin amount that is not in the facts, and do not give advice about doses.
        """.trimIndent()
    }

    fun factsBlock(
        plan: Plan,
        week: ReadoutFacts,
        today: LocalDate,
        readings: List<GlucoseReading>,
        doses: List<DoseRecord>,
    ): String = buildString {
        appendLine("FACTS (today is ${today.format(dayFormat)})")
        appendLine("Target BGL range: ${Measures.formatTenths(plan.targets.lowTenths)} to ${Measures.formatTenths(plan.targets.highTenths)} mmol/L. Low is below ${Measures.formatTenths(plan.targets.hypoTenths)} mmol/L.")
        appendLine("Sliding scale for ${plan.correctionInsulin}:")
        plan.scale.bands.forEachIndexed { index, band ->
            val lower = Measures.formatTenths(plan.scale.lowerTenths(index))
            val range = band.upperTenths?.let { "$lower to ${Measures.formatTenths(it)}" } ?: "above ${Measures.formatTenths(plan.scale.lowerTenths(index) - 1)}"
            appendLine("- $range mmol/L: ${band.units} units")
        }
        val prescribedTimes = plan.slots.filter { it.correctionPrescribed }.joinToString { slotName(it.slot) }
        appendLine("The prescription lists ${plan.correctionInsulin} at: ${prescribedTimes.ifBlank { "no set times" }}.")
        if (plan.medicines.isNotEmpty()) {
            appendLine("Other medicines:")
            plan.medicines.forEach { medicine ->
                val times = medicine.doses.entries.joinToString { (slot, amount) -> "${Measures.formatAmount(amount)} ${medicine.unit} at ${slotName(slot)}" }
                appendLine("- ${medicine.displayName}: $times")
            }
        }
        appendWeight("Weight", week.weight, "kg") { Measures.formatKg(it) }
        appendWeight("Waist", week.waist, "cm") { Measures.formatCm(it) }
        week.weightChangeInPeriod?.let { appendLine("Weight change over the last 7 days: ${signed(it)} kg.") }
        val glucose = week.glucose
        appendLine("Last 7 days: ${glucose.count} BGL readings, ${glucose.inTargetCount} in the target range, ${glucose.lowCount} low, ${glucose.aboveTargetCount} above the target range.")
        glucose.averageTenths?.let { appendLine("Average BGL over the last 7 days: ${Measures.formatTenths(it)} mmol/L.") }
        glucose.perSlot.forEach { appendLine("Average ${slotName(it.slot)} BGL: ${Measures.formatTenths(it.averageTenths ?: 0)} mmol/L from ${it.count} readings.") }
        appendLine("Daily records for the last 14 days:")
        val from = today.minusDays(13)
        generateSequence(today) { it.minusDays(1) }.takeWhile { !it.isBefore(from) }.forEach { date ->
            val line = dayLine(plan, date, readings.filter { it.date == date }, doses.filter { it.date == date })
            if (line != null) appendLine("- ${date.format(dayFormat)}: $line")
        }
    }

    /** Every insulin amount the model may mention: the scale, the plan's medicines and recorded doses. */
    fun allowedAmounts(plan: Plan, doses: List<DoseRecord>): Set<Double> = buildSet {
        plan.scale.bands.forEach { add(it.units.toDouble()) }
        plan.medicines.forEach { addAll(it.doses.values) }
        doses.forEach { dose ->
            dose.givenAmount?.let(::add)
            dose.prescribedAmount?.let(::add)
        }
        // Totals for a day or a week are fair to mention too.
        doses.filter { it.kind == DoseKind.CORRECTION && it.status == DoseStatus.GIVEN }
            .groupBy { it.date }.values.forEach { day -> add(day.sumOf { it.givenAmount ?: 0.0 }) }
        add(doses.filter { it.kind == DoseKind.CORRECTION && it.status == DoseStatus.GIVEN }.sumOf { it.givenAmount ?: 0.0 })
    }

    private fun dayLine(plan: Plan, date: LocalDate, readings: List<GlucoseReading>, doses: List<DoseRecord>): String? {
        if (readings.isEmpty() && doses.isEmpty()) return null
        val parts = Slot.entries.mapNotNull { slot ->
            val slotReadings = readings.filter { it.slot == slot }.sortedBy { it.takenAt }
            val slotDoses = doses.filter { it.slot == slot }.sortedBy { it.takenAt }
            if (slotReadings.isEmpty() && slotDoses.isEmpty()) return@mapNotNull null
            val bgl = slotReadings.joinToString(" then ") { readingText(it) }
            val doseText = slotDoses.joinToString("; ") { doseText(it) }
            "${slotName(slot)} ${listOf(bgl, doseText).filter { it.isNotBlank() }.joinToString(", ")}"
        }
        return parts.joinToString(" | ").ifBlank { null }
    }

    private fun readingText(reading: GlucoseReading): String = when (reading.meterFlag) {
        MeterFlag.LO -> "BGL LO (below 1.1 mmol/L)"
        MeterFlag.HI -> "BGL HI (above 33.3 mmol/L)"
        null -> "BGL ${Measures.formatTenths(reading.tenths)}"
    }

    private fun doseText(dose: DoseRecord): String = when (dose.status) {
        DoseStatus.GIVEN -> "${dose.medicineName} ${Measures.formatAmount(dose.givenAmount ?: 0.0)} ${dose.unit} given"
        DoseStatus.SKIPPED -> "${dose.medicineName} not given"
        DoseStatus.NOT_NEEDED -> "${dose.medicineName} not needed"
    }

    private inline fun StringBuilder.appendWeight(label: String, progress: Progress, unit: String, format: (Int) -> String) {
        val current = progress.current ?: return
        append("$label: now ${format(current)} $unit")
        progress.start?.let { append(", started at ${format(it)} $unit") }
        progress.goal?.let { append(", goal ${format(it)} $unit") }
        appendLine(".")
    }

    private fun signed(grams: Int): String = (if (grams > 0) "+" else if (grams < 0) "-" else "") + Measures.formatKgChange(grams)

    private fun slotName(slot: Slot): String = when (slot) {
        Slot.BREAKFAST -> "before breakfast"
        Slot.LUNCH -> "before lunch"
        Slot.DINNER -> "before dinner"
        Slot.BEDTIME -> "bedtime"
        Slot.OTHER -> "other times"
    }
}

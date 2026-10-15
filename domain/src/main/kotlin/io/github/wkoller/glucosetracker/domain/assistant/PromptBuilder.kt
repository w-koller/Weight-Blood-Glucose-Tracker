package io.github.wkoller.glucosetracker.domain.assistant

import io.github.wkoller.glucosetracker.domain.format.Measures
import io.github.wkoller.glucosetracker.domain.model.DoseKind
import io.github.wkoller.glucosetracker.domain.model.DoseRecord
import io.github.wkoller.glucosetracker.domain.model.DoseStatus
import io.github.wkoller.glucosetracker.domain.model.GlucoseReading
import io.github.wkoller.glucosetracker.domain.model.MeterFlag
import io.github.wkoller.glucosetracker.domain.model.Plan
import io.github.wkoller.glucosetracker.domain.model.Slot
import io.github.wkoller.glucosetracker.domain.model.SlotNaming
import io.github.wkoller.glucosetracker.domain.progress.DoseSummary
import io.github.wkoller.glucosetracker.domain.progress.Progress
import io.github.wkoller.glucosetracker.domain.progress.ProgressModel
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Builds the text given to the on-device language model. None of this is shown to the user. */
object PromptBuilder {

    private val dayFormat = DateTimeFormatter.ofPattern("EEE d MMM", Locale.forLanguageTag("en-AU"))

    /** Days with a line of their own. Longer periods also get a line for each week. */
    private const val DAILY_DAYS = 14

    private const val MAX_LOWS = 10

    fun systemPrompt(plan: Plan, firstName: String? = null): String = """
        You are a friendly helper inside a weight and blood glucose tracking app for an older adult in Australia.
        You only use the facts given to you. If the facts do not answer the question, say that you do not know.
        You never work out, suggest or change an insulin dose or any other medicine. Doses come only from the
        app's check-in, which follows the sliding scale from the person's doctor. If asked about a dose, say that
        the check-in shows the dose from their prescription and that their doctor can answer questions about it.
        You do not give medical advice. For anything about their health or treatment, suggest they speak with
        their doctor or diabetes educator. Write short, complete sentences in plain Australian English, with
        Australian spelling. Blood glucose level is written as BGL after you have spelled it out once, and is
        measured in mmol/L. The person's sliding-scale insulin is ${plan.correctionInsulin}.
    """.trimIndent() + (firstName?.let { "\nThe person's first name is $it. You may use it when you speak to them." } ?: "")

    fun summaryRequest(days: Int): String {
        val span = if (days == 1) "today" else "the last $days days"
        return """
            Using only the facts above, write a short, warm summary of $span in 3 to 5 sentences. Mention the
            blood glucose readings, any low readings, the medicines recorded and the weight. Do not mention any
            insulin amount that is not in the facts, and do not give advice about doses.
        """.trimIndent()
    }

    /**
     * The facts for the period in [progress]. Every figure is worked out here rather than by the model:
     * the period as a whole, week by week when it is longer than two weeks, by time of day, the low
     * readings, and each day of the last 14 days.
     */
    fun factsBlock(
        plan: Plan,
        progress: ProgressModel,
        today: LocalDate,
        readings: List<GlucoseReading>,
        doses: List<DoseRecord>,
        naming: SlotNaming = SlotNaming.MEALS,
    ): String = buildString {
        fun slotName(slot: Slot) = slotName(slot, naming)
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
        val span = "the last ${progress.days} days"
        appendLine("The period is $span, from ${progress.from.format(dayFormat)} to ${progress.to.format(dayFormat)}.")
        appendWeight("Weight", progress.weight, "kg") { Measures.formatKg(it) }
        appendWeight("Waist", progress.waist, "cm") { Measures.formatCm(it) }
        val weights = progress.weightSeries.points
        if (weights.size >= 2) {
            appendLine("Weight in $span: ${Measures.formatKg(weights.first().value)} kg on ${weights.first().date.format(dayFormat)}, then ${Measures.formatKg(weights.last().value)} kg on ${weights.last().date.format(dayFormat)}.")
        }
        progress.weightChangeInPeriod?.let { appendLine("Weight change over $span: ${signed(it)} kg.") }
        val glucose = progress.glucose
        appendLine("${span.replaceFirstChar { it.uppercase() }}: ${glucose.count} BGL readings, ${glucose.inTargetCount} in the target range, ${glucose.lowCount} low, ${glucose.aboveTargetCount} above the target range.")
        glucose.averageTenths?.let { appendLine("Average BGL over $span: ${Measures.formatTenths(it)} mmol/L.") }
        glucose.highest?.let { appendLine("Highest BGL in $span: ${readingValue(it.tenths, it.meterFlag)} ${slotName(it.slot)} on ${it.date.format(dayFormat)}.") }
        glucose.lowest?.let { appendLine("Lowest BGL in $span: ${readingValue(it.tenths, it.meterFlag)} ${slotName(it.slot)} on ${it.date.format(dayFormat)}.") }
        glucose.perSlot.forEach {
            appendLine(
                "Average ${slotName(it.slot)} BGL: ${Measures.formatTenths(it.averageTenths ?: 0)} mmol/L from ${it.count} readings, " +
                    "lowest ${Measures.formatTenths(it.lowestTenths ?: 0)}, highest ${Measures.formatTenths(it.highestTenths ?: 0)}.",
            )
        }
        val lows = progress.glucoseSeries.lows.reversed()
        if (lows.isEmpty()) {
            appendLine("There were no low BGL readings in $span.")
        } else {
            appendLine("Low BGL readings in $span, most recent first:")
            lows.take(MAX_LOWS).forEach { appendLine("- ${it.date.format(dayFormat)} ${it.slot?.let(::slotName).orEmpty()}: ${readingValue(it.value, it.meterFlag)}") }
            if (lows.size > MAX_LOWS) appendLine("- and ${lows.size - MAX_LOWS} earlier low readings.")
        }
        if (progress.doses.isNotEmpty()) {
            appendLine("Doses recorded in $span:")
            progress.doses.forEach { dose ->
                val total = if (dose.kind == DoseKind.CORRECTION) ", ${Measures.formatAmount(dose.totalGiven)} ${dose.unit} in total" else ""
                appendLine("- ${dose.medicineName}: given ${times(dose.givenCount)}$total, not given ${times(dose.skippedCount)}.")
            }
        }
        if (progress.days > DAILY_DAYS) {
            appendLine("Week by week:")
            progress.weeks.forEach { week ->
                val g = week.glucose
                val parts = mutableListOf("${g.count} BGL readings")
                g.averageTenths?.let { parts += "average ${Measures.formatTenths(it)} mmol/L" }
                if (g.count > 0) parts += "${g.inTargetCount} in the target range, ${g.lowCount} low"
                g.highest?.let { parts += "highest ${readingValue(it.tenths, it.meterFlag)}" }
                week.lastWeight?.let { parts += "weight ${Measures.formatKg(it.value)} kg" }
                appendLine("- ${week.from.format(dayFormat)} to ${week.to.format(dayFormat)}: ${parts.joinToString(", ")}.")
            }
        }
        appendLine("Daily records for the last $DAILY_DAYS days:")
        val from = today.minusDays(DAILY_DAYS - 1L)
        generateSequence(today) { it.minusDays(1) }.takeWhile { !it.isBefore(from) }.forEach { date ->
            val line = dayLine(date, readings.filter { it.date == date }, doses.filter { it.date == date }, naming)
            if (line != null) appendLine("- ${date.format(dayFormat)}: $line")
        }
    }

    /** Every insulin amount the model may mention: the scale, the plan's medicines, recorded doses and the totals in [summaries]. */
    fun allowedAmounts(plan: Plan, doses: List<DoseRecord>, summaries: List<DoseSummary> = emptyList()): Set<Double> = buildSet {
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
        summaries.forEach { add(it.totalGiven) }
    }

    private fun dayLine(date: LocalDate, readings: List<GlucoseReading>, doses: List<DoseRecord>, naming: SlotNaming): String? {
        if (readings.isEmpty() && doses.isEmpty()) return null
        val parts = Slot.entries.mapNotNull { slot ->
            val slotReadings = readings.filter { it.slot == slot }.sortedBy { it.takenAt }
            val slotDoses = doses.filter { it.slot == slot }.sortedBy { it.takenAt }
            if (slotReadings.isEmpty() && slotDoses.isEmpty()) return@mapNotNull null
            val bgl = slotReadings.joinToString(" then ") { readingText(it) }
            val doseText = slotDoses.joinToString("; ") { doseText(it) }
            "${slotName(slot, naming)} ${listOf(bgl, doseText).filter { it.isNotBlank() }.joinToString(", ")}"
        }
        return parts.joinToString(" | ").ifBlank { null }
    }

    private fun readingValue(tenths: Int, meterFlag: MeterFlag?): String = when (meterFlag) {
        MeterFlag.LO -> "LO (below 1.1 mmol/L)"
        MeterFlag.HI -> "HI (above 33.3 mmol/L)"
        null -> "${Measures.formatTenths(tenths)} mmol/L"
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

    private fun times(count: Int): String = if (count == 1) "1 time" else "$count times"

    private fun signed(grams: Int): String = (if (grams > 0) "+" else if (grams < 0) "-" else "") + Measures.formatKgChange(grams)

    /** The same words the person sees in the app, so the model's answers match the screens. */
    private fun slotName(slot: Slot, naming: SlotNaming): String = when (naming) {
        SlotNaming.MEALS -> when (slot) {
            Slot.BREAKFAST -> "before breakfast"
            Slot.LUNCH -> "before lunch"
            Slot.DINNER -> "before dinner"
            Slot.BEDTIME -> "bedtime"
            Slot.OTHER -> "other times"
        }
        SlotNaming.TIMES_OF_DAY -> when (slot) {
            Slot.BREAKFAST -> "morning"
            Slot.LUNCH -> "afternoon"
            Slot.DINNER -> "evening"
            Slot.BEDTIME -> "night"
            Slot.OTHER -> "other times"
        }
    }
}

package io.github.wkoller.glucosetracker.domain.assistant

import io.github.wkoller.glucosetracker.domain.Fixtures
import io.github.wkoller.glucosetracker.domain.model.DoseRecord
import io.github.wkoller.glucosetracker.domain.model.GlucoseReading
import io.github.wkoller.glucosetracker.domain.model.Slot
import io.github.wkoller.glucosetracker.domain.model.SlotNaming
import io.github.wkoller.glucosetracker.domain.progress.ProgressModelBuilder
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PromptBuilderTest {
    private val plan = Fixtures.plan()
    private val today = Fixtures.day1
    private val readings = listOf(Fixtures.reading(Slot.BREAKFAST, 137), Fixtures.reading(Slot.BEDTIME, 92, hour = 21))
    private val doses = listOf(Fixtures.correction(Slot.BREAKFAST, 4.0, Fixtures.at(today, 8)))

    private fun facts(
        naming: SlotNaming = SlotNaming.MEALS,
        days: Int = 7,
        readings: List<GlucoseReading> = this.readings,
        doses: List<DoseRecord> = this.doses,
    ): String {
        val progress = ProgressModelBuilder.build(plan, today.minusDays(days - 1L), today, readings, doses, emptyList())
        return PromptBuilder.factsBlock(plan, progress, today, readings, doses, naming)
    }

    /** Four readings and three doses a day, with a low reading every fifth day. */
    private fun busyDays(days: Int): Pair<List<GlucoseReading>, List<DoseRecord>> {
        val dates = (0 until days).map { today.minusDays(it.toLong()) }
        val readings = dates.flatMapIndexed { index, date ->
            listOf(
                Fixtures.reading(Slot.BREAKFAST, if (index % 5 == 0) 36 else 112 + index % 30, date = date, hour = 7),
                Fixtures.reading(Slot.LUNCH, 84 + index % 20, date = date, hour = 12),
                Fixtures.reading(Slot.DINNER, 131 + index % 25, date = date, hour = 17),
                Fixtures.reading(Slot.BEDTIME, 97 + index % 15, date = date, hour = 21),
            )
        }
        val doses = dates.flatMap { date ->
            listOf(
                Fixtures.correction(Slot.BREAKFAST, 4.0, Fixtures.at(date, 7), date = date),
                Fixtures.fixed(Fixtures.premix, Slot.BREAKFAST, date = date),
                Fixtures.fixed(Fixtures.tablet, Slot.BREAKFAST, date = date),
            )
        }
        return readings to doses
    }

    @Test
    fun `meal names are used by default`() {
        val text = facts(SlotNaming.MEALS)
        assertTrue(text, "before breakfast" in text)
        assertTrue(text, "bedtime" in text)
        assertFalse(text, "morning" in text)
    }

    @Test
    fun `a week gives the period's figures and each day, without a week-by-week list`() {
        val text = facts()
        assertTrue(text, "The period is the last 7 days" in text)
        assertTrue(text, "The last 7 days: 2 BGL readings" in text)
        assertTrue(text, "Highest BGL in the last 7 days: 13.7 mmol/L before breakfast" in text)
        assertTrue(text, "There were no low BGL readings" in text)
        assertTrue(text, "NovoRapid: given 1 time, 4 units in total" in text)
        assertTrue(text, "Daily records for the last 14 days:" in text)
        assertFalse(text, "Week by week:" in text)
    }

    @Test
    fun `a longer period adds a line for each week and lists the low readings`() {
        val (readings, doses) = busyDays(30)
        val text = facts(days = 30, readings = readings, doses = doses)
        assertTrue(text, "Week by week:" in text)
        assertTrue(text, "- ${Fixtures.day1.minusDays(6).format(java.time.format.DateTimeFormatter.ofPattern("EEE d MMM", java.util.Locale.forLanguageTag("en-AU")))} to" in text)
        assertTrue(text, "Low BGL readings in the last 30 days, most recent first:" in text)
        assertTrue(text, "3.6 mmol/L" in text)
    }

    @Test
    fun `ninety busy days still fit in the model's context`() {
        // The model has 4,096 tokens for the system prompt, the facts, two earlier exchanges and the answer.
        // Figures cost about one token for every three characters, so the facts must stay well under that.
        val (readings, doses) = busyDays(90)
        val text = facts(days = 90, readings = readings, doses = doses)
        assertTrue("The facts are ${text.length} characters", text.length < 8_000)
    }

    @Test
    fun `the total for the period is an amount the model may mention`() {
        val (readings, doses) = busyDays(30)
        val progress = ProgressModelBuilder.build(plan, today.minusDays(29), today, readings, doses, emptyList())
        val allowed = PromptBuilder.allowedAmounts(plan, doses.filter { !it.date.isBefore(today.minusDays(13)) }, progress.doses)
        assertTrue(120.0 in allowed)
        assertTrue(AssistantGuard.hasUnsupportedAmounts("You had 121 units in total.", allowed))
        assertFalse(AssistantGuard.hasUnsupportedAmounts("You had 120 units in total.", allowed))
    }

    @Test
    fun `the sliding scale is not given to the model`() {
        val text = facts()
        assertFalse(text, "Sliding scale" in text)
        assertFalse(text, Regex("""mmol/L: \d+ units""").containsMatchIn(text))
        // The prescribed times are still there, without any amounts.
        assertTrue(text, "The prescription lists NovoRapid at: before breakfast, before lunch, before dinner." in text)
    }

    @Test
    fun `times of day replace the meal names when chosen`() {
        val text = facts(SlotNaming.TIMES_OF_DAY)
        assertTrue(text, "morning" in text)
        assertTrue(text, "night" in text)
        assertFalse(text, "breakfast" in text)
        assertFalse(text, "bedtime" in text)
    }
}

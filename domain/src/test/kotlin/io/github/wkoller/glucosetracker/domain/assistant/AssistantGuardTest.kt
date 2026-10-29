package io.github.wkoller.glucosetracker.domain.assistant

import io.github.wkoller.glucosetracker.domain.Fixtures
import io.github.wkoller.glucosetracker.domain.model.Slot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AssistantGuardTest {
    private val names = listOf("NovoRapid", "NovoMix 30", "Spironolactone")

    private fun kind(question: String) = AssistantGuard.classify(question, names)

    @Test
    fun `dose questions get the fixed answer`() {
        listOf(
            "How many units should I take?",
            "How much NovoRapid do I need for 15.2?",
            "Should I inject more insulin tonight?",
            "What dose do I give at bedtime?",
            "Can I skip my NovoMix 30?",
            "I forgot my dose, what should I do?",
            "Should I take my tablets if I'm not eating?",
            "Do I need NovoRapid now?",
            "Is it OK to skip my insulin tonight?",
            "Insulin now?",
            "What is on my sliding scale?",
        ).forEach { assertEquals(it, QuestionKind.DOSE, kind(it)) }
    }

    @Test
    fun `signs of a low level get the app's answer`() {
        listOf("I feel shaky", "I'm sweating and a bit dizzy", "I think I'm going low", "Am I having a hypo?")
            .forEach { assertEquals(it, QuestionKind.HYPO, kind(it)) }
    }

    @Test
    fun `thoughts of suicide or self-harm get the crisis answer`() {
        listOf("I want to end my life", "I have been thinking about suicide", "Some days I want to die")
            .forEach { assertEquals(it, QuestionKind.CRISIS, kind(it)) }
    }

    @Test
    fun `urgent symptoms get the emergency answer`() {
        listOf("I have chest pain", "He fainted after lunch", "I think I gave too much insulin", "I feel very unwell and confused")
            .forEach { assertEquals(it, QuestionKind.URGENT, kind(it)) }
    }

    @Test
    fun `general questions go to the model`() {
        listOf(
            "How was my week?",
            "What was my highest reading?",
            "How much weight have I lost?",
            "What does BGL mean?",
            "Should I take my weight before breakfast?",
            "When did I have low readings?",
            "Have I taken my insulin today?",
            "What did the scales say this morning?",
        ).forEach { assertEquals(it, QuestionKind.GENERAL, kind(it)) }
    }

    @Test
    fun `answers may only mention insulin amounts from the records`() {
        val plan = Fixtures.plan()
        val doses = listOf(Fixtures.correction(Slot.LUNCH, 4.0, Fixtures.at(Fixtures.day1, 12)))
        val allowed = PromptBuilder.allowedAmounts(plan, doses)
        assertFalse(AssistantGuard.hasUnsupportedAmounts("You gave 4 units of NovoRapid at lunch and 30 units of NovoMix.", allowed))
        assertTrue(AssistantGuard.hasUnsupportedAmounts("You could try 12 units instead.", allowed))
        assertTrue(AssistantGuard.hasUnsupportedAmounts("Take 7u tonight.", allowed))
        assertFalse(AssistantGuard.hasUnsupportedAmounts("Your average was 13.7 mmol/L.", allowed))
        // 8 units is on the sliding scale but was never recorded, so an answer that works it out is caught.
        assertTrue(AssistantGuard.hasUnsupportedAmounts("For a reading of 18, the scale gives 8 units.", allowed))
    }
}

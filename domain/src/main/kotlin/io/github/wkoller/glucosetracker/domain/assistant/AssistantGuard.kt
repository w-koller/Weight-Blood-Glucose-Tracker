package io.github.wkoller.glucosetracker.domain.assistant

enum class QuestionKind { DOSE, URGENT, GENERAL }

/**
 * Keeps the language model away from insulin doses and emergencies. Dose questions and urgent
 * symptoms get fixed answers from the app; only general questions reach the model.
 */
object AssistantGuard {

    private val urgentPhrases = listOf(
        "chest pain", "chest hurts", "can't breathe", "cannot breathe", "can not breathe", "short of breath",
        "trouble breathing", "unconscious", "can't wake", "cannot wake", "won't wake", "wont wake",
        "passed out", "fainted", "collapsed", "seizure", "having a fit", "stroke", "face drooping",
        "slurred speech", "very unwell", "really unwell", "very sick", "confused", "call an ambulance",
        "emergency", "overdose", "too much insulin", "took too much", "gave too much", "injected too much",
        "double dose", "doubled my dose", "vomiting",
    )

    private val doseWords = listOf("unit", "units", "insulin", "dose", "doses", "dosage", "inject", "injection", "pen")
    private val medicineWords = listOf("tablet", "tablets", "pill", "pills", "medicine", "medicines", "medication", "medications")
    private val actionWords = listOf(
        "take", "inject", "give", "have", "skip", "miss", "increase", "decrease", "reduce", "raise", "lower",
        "change", "double", "stop", "use", "add", "adjust", "halve",
    )

    fun classify(question: String, medicineNames: Collection<String>): QuestionKind {
        val text = normalise(question)
        if (urgentPhrases.any { it in text }) return QuestionKind.URGENT

        val names = medicineNames.map { normalise(it) }.filter { it.isNotBlank() }
        val mentionsDose = doseWords.any { containsWord(text, it) }
        val mentionsMedicine = mentionsDose || names.any { it in text } || medicineWords.any { containsWord(text, it) }
        val asksHowMuch = Regex("""\bhow (many|much)\b""").containsMatchIn(text)
        val asksWhatDose = Regex("""\b(what|which|right|correct|extra|more|less|another|missed|forgot)\b""").containsMatchIn(text) && mentionsDose
        val asksShould = Regex("""\b(should|can|could|do|must|shall|may) i\b""").containsMatchIn(text) &&
            actionWords.any { containsWord(text, it) }

        return when {
            asksHowMuch && mentionsMedicine -> QuestionKind.DOSE
            asksWhatDose -> QuestionKind.DOSE
            asksShould && mentionsMedicine -> QuestionKind.DOSE
            else -> QuestionKind.GENERAL
        }
    }

    private val amountPattern = Regex("""(\d+(?:[.,]\d+)?)\s*(?:units?|iu|u)\b""", RegexOption.IGNORE_CASE)

    /** True when the answer names an insulin amount that is not one of the [allowed] amounts from the user's records. */
    fun hasUnsupportedAmounts(answer: String, allowed: Set<Double>): Boolean =
        amountPattern.findAll(answer).any { match ->
            val amount = match.groupValues[1].replace(',', '.').toDoubleOrNull()
            amount == null || amount !in allowed
        }

    private fun normalise(text: String): String =
        text.lowercase().replace('’', '\'').replace(Regex("""\s+"""), " ").trim()

    private fun containsWord(text: String, word: String): Boolean = Regex("""\b${Regex.escape(word)}\b""").containsMatchIn(text)
}

package io.github.wkoller.glucosetracker.domain.assistant

enum class QuestionKind { DOSE, URGENT, HYPO, CRISIS, GENERAL }

/**
 * Keeps the language model away from insulin doses and emergencies. Dose questions, urgent symptoms, signs of a
 * low level and thoughts of suicide or self-harm get fixed answers from the app; only general questions reach the
 * model.
 */
object AssistantGuard {

    /** Thoughts of suicide or self-harm, answered with numbers to call straight away. */
    private val crisisPhrases = listOf(
        "kill myself", "killing myself", "end my life", "ending my life", "take my own life", "take my life",
        "suicide", "suicidal", "want to die", "wish i was dead", "wish i were dead", "better off dead",
        "hurt myself", "harm myself", "self harm", "self-harm",
    )

    private val urgentPhrases = listOf(
        "chest pain", "chest hurts", "can't breathe", "cannot breathe", "can not breathe", "short of breath",
        "trouble breathing", "unconscious", "can't wake", "cannot wake", "won't wake", "wont wake",
        "passed out", "fainted", "collapsed", "seizure", "having a fit", "stroke", "face drooping",
        "slurred speech", "very unwell", "really unwell", "very sick", "confused", "call an ambulance",
        "emergency", "overdose", "too much insulin", "took too much", "gave too much", "injected too much",
        "double dose", "doubled my dose", "vomiting",
    )

    /** Signs that the person's level may be low now. A low needs treating straight away, so the app answers. */
    private val hypoPhrases = listOf(
        "shaky", "shaking", "trembling", "sweaty", "sweating", "clammy", "dizzy", "light-headed", "lightheaded",
        "light headed", "feel faint", "feeling faint", "blurry vision", "blurred vision", "tingling lips",
        "feel low", "feeling low", "going low", "i'm low", "im low", "having a hypo", "feel hypo", "feeling hypo",
        "going hypo",
    )

    private val doseWords = listOf("unit", "units", "insulin", "dose", "doses", "dosage", "inject", "injection", "pen")
    private val medicineWords = listOf("tablet", "tablets", "pill", "pills", "medicine", "medicines", "medication", "medications")
    private val actionWords = listOf(
        "take", "inject", "give", "have", "skip", "miss", "increase", "decrease", "reduce", "raise", "lower",
        "change", "double", "stop", "use", "add", "adjust", "halve", "need", "want", "due",
    )

    fun classify(question: String, medicineNames: Collection<String>): QuestionKind {
        val text = normalise(question)
        if (crisisPhrases.any { it in text }) return QuestionKind.CRISIS
        if (urgentPhrases.any { it in text }) return QuestionKind.URGENT
        if (hypoPhrases.any { it in text }) return QuestionKind.HYPO

        val names = medicineNames.map { normalise(it) }.filter { it.isNotBlank() }
        // "Sliding scale", not "scale" alone, which could be the bathroom scales.
        val mentionsDose = doseWords.any { containsWord(text, it) } || Regex("""\bsliding[ -]scale\b""").containsMatchIn(text)
        val mentionsName = names.any { it in text }
        val mentionsMedicine = mentionsDose || mentionsName || medicineWords.any { containsWord(text, it) }
        val asksHowMuch = Regex("""\bhow (many|much)\b""").containsMatchIn(text)
        val asksWhatDose = Regex("""\b(what|which|right|correct|extra|more|less|another|missed|forgot)\b""").containsMatchIn(text) && mentionsDose
        val asksShould = Regex("""\b(should|can|could|do|must|shall|may) i\b""").containsMatchIn(text) &&
            actionWords.any { containsWord(text, it) }
        val asksIfAllowed = Regex("""\b(is it|it's|its|is that) (ok|okay|safe|alright|all right|fine)\b""").containsMatchIn(text)
        // "Insulin now?" asks for a dose; "Did I have my insulin this morning?" asks about the records.
        val asksAboutNow = Regex("""\b(now|tonight|today|this (morning|afternoon|evening)|next)\b""").containsMatchIn(text) &&
            !Regex("""\b(did|had|have i|was|were|yesterday|last)\b""").containsMatchIn(text)

        return when {
            asksHowMuch && mentionsMedicine -> QuestionKind.DOSE
            asksWhatDose -> QuestionKind.DOSE
            asksShould && mentionsMedicine -> QuestionKind.DOSE
            asksIfAllowed && mentionsMedicine -> QuestionKind.DOSE
            asksAboutNow && (mentionsDose || mentionsName) -> QuestionKind.DOSE
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

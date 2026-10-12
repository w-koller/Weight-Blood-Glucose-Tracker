package io.github.wkoller.glucosetracker.domain.dosing

import io.github.wkoller.glucosetracker.domain.model.Slot
import java.time.Instant

/** Something the user must confirm before a dose is recorded. */
sealed interface DosePrompt {
    data class AlreadyDosed(val at: Instant, val amount: Double) : DosePrompt
    data class NotPrescribedTime(val slot: Slot) : DosePrompt
    data class DifferentAmount(val prescribed: Double, val given: Double) : DosePrompt
}

object OverrideCheck {

    /** Prompts for recording a sliding-scale dose of [given] units, in the order they should be read. */
    fun forCorrection(advice: DoseAdvice, slot: Slot, given: Double): List<DosePrompt> {
        if (given <= 0.0) return emptyList()
        val prompts = mutableListOf<DosePrompt>()
        advice.alreadyDosed?.let { prompts += DosePrompt.AlreadyDosed(it.at, it.amount) }
        if (DoseNotice.NotPrescribedTime in advice.notices) {
            prompts += DosePrompt.NotPrescribedTime(slot)
        }
        if (given != advice.units.toDouble()) prompts += DosePrompt.DifferentAmount(advice.units.toDouble(), given)
        return prompts
    }

    /** Prompts for recording a fixed medicine given as [given] when the plan says [prescribed]. */
    fun forFixed(prescribed: Double, given: Double): List<DosePrompt> =
        if (given != prescribed) listOf(DosePrompt.DifferentAmount(prescribed, given)) else emptyList()
}

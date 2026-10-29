package io.github.wkoller.glucosetracker.ui.checkin

import androidx.annotation.StringRes
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.wkoller.glucosetracker.AppContainer
import io.github.wkoller.glucosetracker.R
import io.github.wkoller.glucosetracker.domain.dosing.DoseAdvice
import io.github.wkoller.glucosetracker.domain.dosing.DoseAdvisor
import io.github.wkoller.glucosetracker.domain.dosing.DosePrompt
import io.github.wkoller.glucosetracker.domain.dosing.OverrideCheck
import io.github.wkoller.glucosetracker.domain.model.DoseKind
import io.github.wkoller.glucosetracker.domain.model.DoseRecord
import io.github.wkoller.glucosetracker.domain.model.DoseStatus
import io.github.wkoller.glucosetracker.domain.model.GlucoseReading
import io.github.wkoller.glucosetracker.domain.model.MedicineKind
import io.github.wkoller.glucosetracker.domain.model.MeterFlag
import io.github.wkoller.glucosetracker.domain.model.Plan
import io.github.wkoller.glucosetracker.domain.model.ScheduledMedicine
import io.github.wkoller.glucosetracker.domain.model.SkipReason
import io.github.wkoller.glucosetracker.domain.model.Slot
import io.github.wkoller.glucosetracker.domain.schedule.NextStep
import io.github.wkoller.glucosetracker.domain.schedule.NextStepEngine
import io.github.wkoller.glucosetracker.domain.schedule.SlotResolver
import io.github.wkoller.glucosetracker.domain.validation.InputValidator
import io.github.wkoller.glucosetracker.domain.validation.NumberInput
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.Instant
import java.time.LocalDate

enum class CheckInStep { BGL, LOW, RESULT, CORRECTION_AMOUNT, SKIP_REASON, MEDICINE, MEDICINE_AMOUNT, TABLETS, DONE, NOTE }

/** What the user had to bring a low level up. It is saved in the low reading's note for the doctor. */
enum class LowTreatment(@StringRes val labelRes: Int, @StringRes val sentenceRes: Int) {
    JUICE(R.string.low_treatment_juice, R.string.low_treated_juice),
    JELLYBEANS(R.string.low_treatment_jellybeans, R.string.low_treated_jellybeans),
    GLUCOSE(R.string.low_treatment_glucose, R.string.low_treated_glucose),
    OTHER(R.string.low_treatment_other, R.string.low_treated_other),
}

data class DueMedicine(val medicine: ScheduledMedicine, val amount: Double)

/** What a dose amount, a confirmation or a reason for not giving a dose applies to. */
sealed interface DoseTarget {
    data object Correction : DoseTarget
    data class Fixed(val due: DueMedicine) : DoseTarget
}

data class PendingConfirm(val prompts: List<DosePrompt>, val amount: Double, val target: DoseTarget)

data class CheckInUi(
    val loading: Boolean = true,
    val plan: Plan? = null,
    val slot: Slot = Slot.OTHER,
    val date: LocalDate = LocalDate.now(),
    val step: CheckInStep = CheckInStep.BGL,
    val bglText: String = "",
    /** LO or HI when the meter shows a word instead of a number. Typing a digit clears it. */
    val bglFlag: MeterFlag? = null,
    val bglError: NumberInput<Int>? = null,
    val reading: GlucoseReading? = null,
    val advice: DoseAdvice? = null,
    val amountText: String = "",
    val amountError: NumberInput<Double>? = null,
    val confirm: PendingConfirm? = null,
    val skipTarget: DoseTarget? = null,
    val dueInsulins: List<DueMedicine> = emptyList(),
    val dueOthers: List<DueMedicine> = emptyList(),
    val tabletsSeparately: Boolean = false,
    val tabletAnswers: Map<String, Boolean> = emptyMap(),
    val recorded: List<DoseRecord> = emptyList(),
    val readings: List<GlucoseReading> = emptyList(),
    val lowThisCheck: Boolean = false,
    val recheckAt: Instant? = null,
    val nextStep: NextStep? = null,
    val busy: Boolean = false,
    /** What was had for each low reading in this check-in, by reading id. */
    val treatments: Map<Long, LowTreatment> = emptyMap(),
    /** The note saved at the end of the check-in, and the text being typed for it. */
    val note: String = "",
    val noteText: String = "",
    val noteReadingId: Long? = null,
) {
    val currentInsulin: DueMedicine? get() = dueInsulins.firstOrNull()
}

class CheckInViewModel(private val container: AppContainer, private val handle: SavedStateHandle) : ViewModel() {
    private val repository = container.repository
    private val requestedSlot = handle.get<String>("slot")?.let { runCatching { Slot.valueOf(it) }.getOrNull() }
    private val medicinesOnly = handle.get<Boolean>("medsOnly") ?: false

    private val _ui = MutableStateFlow(CheckInUi())
    val ui: StateFlow<CheckInUi> = _ui.asStateFlow()

    init {
        viewModelScope.launch {
            val plan = repository.currentPlan()
            if (plan == null) {
                // Without a plan there is no scale to give a dose from, so the screen says so instead of waiting.
                _ui.update { it.copy(loading = false) }
                return@launch
            }
            val saved = handle.get<String>(KEY_SAVED)?.let(CheckInStateJson::decode)
            if (saved != null) {
                restore(plan, saved)
            } else {
                val slot = requestedSlot ?: SlotResolver.resolve(plan, repository.now().toLocalTime())
                _ui.update { it.copy(loading = false, plan = plan, slot = slot, date = repository.logicalToday(plan)) }
                if (medicinesOnly) goToMedicines()
            }
            // Keep where the check-in has got to, so it carries on if Android closes the app in the background.
            _ui.map { CheckInStateJson.encode(SavedCheckIn.of(it)) }.distinctUntilChanged().collect { handle[KEY_SAVED] = it }
        }
    }

    /**
     * Puts back a check-in that Android closed in the background. Its readings and doses are read from the database,
     * and the advice is worked out again as it was at the time of the reading, without this check-in's own doses.
     */
    private suspend fun restore(plan: Plan, saved: SavedCheckIn) {
        val readings = repository.readingsBetween(saved.date, saved.date).filter { it.id in saved.readingIds }
        val recorded = repository.dosesBetween(saved.date, saved.date).filter { it.id in saved.doseIds }
        val reading = readings.firstOrNull { it.id == saved.readingId }
        val earlier = repository.dosesBetween(saved.date.minusDays(1), saved.date).filter { it.id !in saved.doseIds }
        val advice = reading?.let { DoseAdvisor.advise(plan, saved.slot, it.tenths, it.meterFlag, it.takenAt, saved.date, earlier) }
        _ui.update {
            it.copy(
                loading = false, plan = plan, slot = saved.slot, date = saved.date, reading = reading, readings = readings,
                recorded = recorded, advice = advice, lowThisCheck = saved.lowThisCheck, recheckAt = saved.recheckAt,
                treatments = saved.treatments, note = saved.note, noteText = saved.note, noteReadingId = saved.noteReadingId,
            )
        }
        when (saved.step) {
            CheckInStep.BGL -> _ui.update { it.copy(step = CheckInStep.BGL) }
            CheckInStep.DONE, CheckInStep.NOTE -> finish()
            else -> {
                // Before the sliding-scale dose is recorded the result is shown again; after it, the medicines carry on.
                val correctionDone = reading == null || recorded.any { it.kind == DoseKind.CORRECTION && it.readingId == reading.id }
                if (correctionDone) {
                    goToMedicinesNow()
                } else {
                    _ui.update { it.copy(step = if (advice?.isHypo == true) CheckInStep.LOW else CheckInStep.RESULT) }
                }
            }
        }
    }

    fun setSlot(slot: Slot) = _ui.update { it.copy(slot = slot) }

    fun setBglText(text: String) = _ui.update { it.copy(bglText = text, bglFlag = null, bglError = null) }

    /** The meter shows LO or HI. Like a typed number, it is saved only when the user presses Next, so a stray tap can be put right. */
    fun meterShows(flag: MeterFlag) = _ui.update { it.copy(bglText = "", bglFlag = flag, bglError = null) }

    fun submitBgl() {
        val state = _ui.value
        if (state.bglFlag != null) {
            saveReading(if (state.bglFlag == MeterFlag.LO) InputValidator.METER_MIN_TENTHS else InputValidator.METER_MAX_TENTHS, state.bglFlag)
            return
        }
        when (val input = InputValidator.bgl(state.bglText)) {
            is NumberInput.Valid -> saveReading(input.value, null)
            else -> _ui.update { it.copy(bglError = input) }
        }
    }

    private fun saveReading(tenths: Int, flag: MeterFlag?) = work {
        val state = _ui.value
        val plan = state.plan ?: return@work
        val now = repository.instant()
        val date = repository.logicalToday(plan)
        val existing = state.reading
        val reading = if (existing != null) {
            existing.copy(tenths = tenths, meterFlag = flag, slot = state.slot).also { repository.updateReading(it) }
        } else {
            val draft = GlucoseReading(0, plan.id, now, date, state.slot, tenths, flag, null)
            draft.copy(id = repository.addReading(draft))
        }
        val earlier = repository.dosesBetween(date.minusDays(1), date)
        val advice = DoseAdvisor.advise(plan, state.slot, tenths, flag, now, date, earlier)
        _ui.update {
            it.copy(
                date = date,
                reading = reading,
                readings = it.readings.filterNot { r -> r.id == reading.id } + reading,
                advice = advice,
                lowThisCheck = it.lowThisCheck || advice.isHypo,
                step = if (advice.isHypo) CheckInStep.LOW else CheckInStep.RESULT,
            )
        }
    }

    // Sliding-scale dose

    fun giveSuggested() {
        val advice = _ui.value.advice ?: return
        requestDose(DoseTarget.Correction, advice.units.toDouble())
    }

    fun continueWithoutDose() = recordCorrection(DoseStatus.NOT_NEEDED, given = null, reason = null)

    fun enterOtherAmount() = _ui.update {
        it.copy(step = if (it.step == CheckInStep.MEDICINE) CheckInStep.MEDICINE_AMOUNT else CheckInStep.CORRECTION_AMOUNT, amountText = "", amountError = null)
    }

    fun setAmountText(text: String) = _ui.update { it.copy(amountText = text, amountError = null) }

    fun submitAmount() {
        val state = _ui.value
        val target = if (state.step == CheckInStep.MEDICINE_AMOUNT) state.currentInsulin?.let { DoseTarget.Fixed(it) } else DoseTarget.Correction
        target ?: return
        when (val input = InputValidator.doseUnits(state.amountText)) {
            is NumberInput.Valid -> if (input.value == 0.0) notGiven(target) else requestDose(target, input.value)
            else -> _ui.update { it.copy(amountError = input) }
        }
    }

    fun notGivenCorrection() = notGiven(DoseTarget.Correction)

    fun notGivenMedicine() = _ui.value.currentInsulin?.let { notGiven(DoseTarget.Fixed(it)) }

    private fun notGiven(target: DoseTarget) = _ui.update { it.copy(step = CheckInStep.SKIP_REASON, skipTarget = target) }

    fun chooseReason(reason: SkipReason) {
        when (val target = _ui.value.skipTarget) {
            DoseTarget.Correction -> recordCorrection(DoseStatus.SKIPPED, given = null, reason = reason)
            is DoseTarget.Fixed -> recordFixed(target.due, DoseStatus.SKIPPED, given = null, reason = reason)
            null -> Unit
        }
    }

    private fun requestDose(target: DoseTarget, amount: Double) {
        val state = _ui.value
        val prompts = when (target) {
            DoseTarget.Correction -> OverrideCheck.forCorrection(state.advice ?: return, state.slot, amount)
            is DoseTarget.Fixed -> OverrideCheck.forFixed(target.due.amount, amount)
        }
        if (prompts.isEmpty()) {
            record(target, amount)
        } else {
            _ui.update { it.copy(confirm = PendingConfirm(prompts, amount, target)) }
        }
    }

    fun confirmDose() {
        val pending = _ui.value.confirm ?: return
        _ui.update { it.copy(confirm = null) }
        record(pending.target, pending.amount)
    }

    fun cancelConfirm() = _ui.update { it.copy(confirm = null) }

    private fun record(target: DoseTarget, amount: Double) = when (target) {
        DoseTarget.Correction -> recordCorrection(DoseStatus.GIVEN, amount, null)
        is DoseTarget.Fixed -> recordFixed(target.due, DoseStatus.GIVEN, amount, null)
    }

    private fun recordCorrection(status: DoseStatus, given: Double?, reason: SkipReason?) = work {
        val state = _ui.value
        val plan = state.plan ?: return@work
        val dose = DoseRecord(
            id = 0,
            planId = plan.id,
            readingId = state.reading?.id,
            takenAt = repository.instant(),
            date = state.date,
            slot = state.slot,
            kind = DoseKind.CORRECTION,
            medicineKey = null,
            medicineName = plan.correctionInsulin,
            medicineKind = MedicineKind.INSULIN,
            prescribedAmount = state.advice?.units?.toDouble() ?: 0.0,
            givenAmount = given,
            unit = UNITS,
            status = status,
            reason = reason,
            note = null,
        )
        val saved = dose.copy(id = repository.addDose(dose))
        _ui.update { it.copy(recorded = it.recorded + saved) }
        goToMedicinesNow()
    }

    // Low reading

    fun startRecheckTimer() {
        val at = repository.instant().plus(RECHECK)
        _ui.update { it.copy(recheckAt = at) }
        viewModelScope.launch { container.onRecheckRequested(at) }
    }

    /** Records that no sliding-scale dose was given because of the low reading, then starts a new reading. */
    fun enterNewReading() = work {
        recordLowSkip()
        _ui.update { it.copy(step = CheckInStep.BGL, bglText = "", bglFlag = null, bglError = null, reading = null, advice = null) }
    }

    fun continueAfterLow() = work {
        recordLowSkip()
        goToMedicinesNow()
    }

    private suspend fun recordLowSkip() {
        val state = _ui.value
        val plan = state.plan ?: return
        val reading = state.reading ?: return
        if (state.recorded.any { it.readingId == reading.id }) return
        val dose = DoseRecord(
            0, plan.id, reading.id, repository.instant(), state.date, state.slot, DoseKind.CORRECTION, null,
            plan.correctionInsulin, MedicineKind.INSULIN, 0.0, null, UNITS, DoseStatus.SKIPPED, SkipReason.LOW_READING, null,
        )
        val saved = dose.copy(id = repository.addDose(dose))
        _ui.update { it.copy(recorded = it.recorded + saved) }
    }

    // Fixed medicines

    private fun goToMedicines() = work { goToMedicinesNow() }

    private suspend fun goToMedicinesNow() {
        val state = _ui.value
        val plan = state.plan ?: return
        val alreadyRecorded = repository.dosesBetween(state.date, state.date)
            .filter { it.slot == state.slot && it.kind == DoseKind.FIXED }
            .mapNotNull { it.medicineKey }
            .toSet()
        val due = if (state.slot == Slot.OTHER) {
            emptyList()
        } else {
            plan.medicinesAt(state.slot)
                .filter { it.key !in alreadyRecorded }
                .map { DueMedicine(it, it.doses.getValue(state.slot)) }
        }
        val insulins = due.filter { it.medicine.kind == MedicineKind.INSULIN }
        val others = due.filter { it.medicine.kind != MedicineKind.INSULIN }
        _ui.update { it.copy(dueInsulins = insulins, dueOthers = others, tabletAnswers = emptyMap(), tabletsSeparately = false) }
        advanceMedicines()
    }

    private suspend fun advanceMedicines() {
        val state = _ui.value
        when {
            state.dueInsulins.isNotEmpty() -> _ui.update { it.copy(step = CheckInStep.MEDICINE, amountText = "", amountError = null) }
            state.dueOthers.isNotEmpty() -> _ui.update { it.copy(step = CheckInStep.TABLETS) }
            else -> finish()
        }
    }

    fun giveMedicine() {
        val due = _ui.value.currentInsulin ?: return
        requestDose(DoseTarget.Fixed(due), due.amount)
    }

    private fun recordFixed(due: DueMedicine, status: DoseStatus, given: Double?, reason: SkipReason?) = work {
        saveFixed(due, status, given, reason)
        _ui.update { it.copy(dueInsulins = it.dueInsulins.filterNot { d -> d.medicine.key == due.medicine.key }) }
        advanceMedicines()
    }

    private suspend fun saveFixed(due: DueMedicine, status: DoseStatus, given: Double?, reason: SkipReason?) {
        val state = _ui.value
        val plan = state.plan ?: return
        val dose = DoseRecord(
            id = 0,
            planId = plan.id,
            readingId = state.reading?.id,
            takenAt = repository.instant(),
            date = state.date,
            slot = state.slot,
            kind = DoseKind.FIXED,
            medicineKey = due.medicine.key,
            medicineName = due.medicine.displayName,
            medicineKind = due.medicine.kind,
            prescribedAmount = due.amount,
            givenAmount = given,
            unit = due.medicine.unit,
            status = status,
            reason = reason,
            note = null,
        )
        val saved = dose.copy(id = repository.addDose(dose))
        _ui.update { it.copy(recorded = it.recorded + saved) }
    }

    fun tookAllOthers() = work {
        _ui.value.dueOthers.forEach { saveFixed(it, DoseStatus.GIVEN, it.amount, null) }
        _ui.update { it.copy(dueOthers = emptyList()) }
        finish()
    }

    fun answerSeparately() = _ui.update { it.copy(tabletsSeparately = true) }

    fun setTablet(key: String, taken: Boolean) = _ui.update { it.copy(tabletAnswers = it.tabletAnswers + (key to taken)) }

    fun saveTablets() = work {
        val state = _ui.value
        if (state.dueOthers.any { it.medicine.key !in state.tabletAnswers }) return@work
        state.dueOthers.forEach { due ->
            if (state.tabletAnswers.getValue(due.medicine.key)) {
                saveFixed(due, DoseStatus.GIVEN, due.amount, null)
            } else {
                saveFixed(due, DoseStatus.SKIPPED, null, null)
            }
        }
        _ui.update { it.copy(dueOthers = emptyList()) }
        finish()
    }

    private suspend fun finish() {
        val plan = _ui.value.plan ?: return
        val date = repository.logicalToday(plan)
        val next = NextStepEngine.next(plan, repository.now(), repository.dayRecords(date))
        _ui.update { it.copy(step = CheckInStep.DONE, nextStep = next) }
    }

    // Notes

    /** Saves what the user had for the low reading. Choosing again replaces the earlier choice. */
    fun chooseTreatment(treatment: LowTreatment) = work {
        val reading = _ui.value.reading ?: return@work
        _ui.update { it.copy(treatments = it.treatments + (reading.id to treatment)) }
        writeReadingNote(reading.id)
    }

    fun startNote() = _ui.update { it.copy(step = CheckInStep.NOTE, noteText = it.note) }

    fun setNoteText(text: String) = _ui.update { it.copy(noteText = text) }

    /** Saves the note with this check-in's latest reading, or with its first dose when there was no reading. */
    fun saveNote() = work {
        val state = _ui.value
        val text = state.noteText.trim()
        val readingId = state.readings.lastOrNull()?.id
        _ui.update { it.copy(note = text, noteReadingId = readingId, step = CheckInStep.DONE) }
        if (readingId != null) {
            writeReadingNote(readingId)
        } else {
            val dose = state.recorded.firstOrNull() ?: return@work
            val saved = dose.copy(note = text.ifBlank { null })
            repository.updateDose(saved)
            _ui.update { it.copy(recorded = it.recorded.map { d -> if (d.id == saved.id) saved else d }) }
        }
    }

    /** A reading's note is what was had for a low, followed by anything the user typed. */
    private suspend fun writeReadingNote(readingId: Long) {
        val state = _ui.value
        val reading = state.readings.firstOrNull { it.id == readingId } ?: return
        val parts = listOfNotNull(
            state.treatments[readingId]?.let { container.appContext.getString(it.sentenceRes) },
            state.note.takeIf { state.noteReadingId == readingId && it.isNotBlank() },
        )
        val updated = reading.copy(note = parts.joinToString(" ").ifBlank { null })
        repository.updateReading(updated)
        _ui.update {
            it.copy(
                readings = it.readings.map { r -> if (r.id == readingId) updated else r },
                reading = if (it.reading?.id == readingId) updated else it.reading,
            )
        }
    }

    /** Returns false when the screen should close instead. */
    fun back(): Boolean {
        val state = _ui.value
        val previous = when (state.step) {
            CheckInStep.RESULT, CheckInStep.LOW -> CheckInStep.BGL
            CheckInStep.CORRECTION_AMOUNT -> CheckInStep.RESULT
            CheckInStep.MEDICINE_AMOUNT -> CheckInStep.MEDICINE
            CheckInStep.SKIP_REASON -> if (state.skipTarget is DoseTarget.Fixed) CheckInStep.MEDICINE else CheckInStep.RESULT
            CheckInStep.NOTE -> CheckInStep.DONE
            // Once a dose is saved, going back could record it twice, so Back leaves the check-in instead.
            CheckInStep.BGL, CheckInStep.MEDICINE, CheckInStep.TABLETS, CheckInStep.DONE -> null
        } ?: return false
        _ui.update { it.copy(step = previous, confirm = null) }
        return true
    }

    private fun work(block: suspend () -> Unit) {
        if (_ui.value.busy) return
        _ui.update { it.copy(busy = true) }
        viewModelScope.launch {
            try {
                block()
            } finally {
                _ui.update { it.copy(busy = false) }
            }
        }
    }

    companion object {
        const val UNITS = "units"
        val RECHECK: Duration = Duration.ofMinutes(15)

        /** Where the check-in in progress is kept, so it survives Android closing the app in the background. */
        private const val KEY_SAVED = "checkin"
    }
}

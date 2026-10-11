package io.github.wkoller.glucosetracker.ui.plan

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.wkoller.glucosetracker.AppContainer
import io.github.wkoller.glucosetracker.data.prefs.PinHasher
import io.github.wkoller.glucosetracker.domain.format.Measures
import io.github.wkoller.glucosetracker.domain.model.Plan
import io.github.wkoller.glucosetracker.domain.model.Slot
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime
import java.util.UUID

enum class SetupMode { FIRST, EDIT, NEW }

enum class SetupStep { WELCOME, INSULIN, SCALE, SCALE_CHECK, TIMES, MEDICINES, GOALS, TARGETS, PIN, PERMISSIONS, REVIEW }

enum class PinError { INVALID, MISMATCH }

data class SetupUi(
    val loading: Boolean = true,
    val mode: SetupMode = SetupMode.FIRST,
    val steps: List<SetupStep> = emptyList(),
    val step: SetupStep = SetupStep.WELCOME,
    val draft: PlanDraft = PlanDraft(),
    val errors: List<DraftError> = emptyList(),
    val returnToReview: Boolean = false,
    val editingMedicine: MedicineDraft? = null,
    val medicineErrors: List<DraftError> = emptyList(),
    val pin: String = "",
    val pinConfirm: String = "",
    val pinError: PinError? = null,
    val chosenPin: String? = null,
    val changes: List<String>? = null,
    val saving: Boolean = false,
    val finished: Boolean = false,
) {
    val stepNumber: Int get() = steps.indexOf(step) + 1
}

class PlanSetupViewModel(private val container: AppContainer, handle: SavedStateHandle) : ViewModel() {
    private val mode = handle.get<String>("mode")?.let { runCatching { SetupMode.valueOf(it) }.getOrNull() } ?: SetupMode.FIRST
    private var original: Plan? = null

    private val _ui = MutableStateFlow(SetupUi(mode = mode))
    val ui: StateFlow<SetupUi> = _ui.asStateFlow()

    init {
        viewModelScope.launch {
            val current = container.repository.currentPlan()
            original = current
            val measurements = container.repository.allMeasurements()
            val latestWeight = measurements.lastOrNull { it.weightGrams != null }?.weightGrams
            val latestWaist = measurements.lastOrNull { it.waistMm != null }?.waistMm
            val draft = when {
                mode == SetupMode.EDIT && current != null -> PlanDraft.from(current)
                mode == SetupMode.NEW && current != null -> PlanDraft.from(current).copy(
                    cycleId = UUID.randomUUID().toString(),
                    startDate = LocalDate.now(container.clock),
                    startWeight = latestWeight?.let(Measures::formatKg) ?: "",
                    startWaist = latestWaist?.let(Measures::formatCm) ?: "",
                    targetDate = null,
                )
                else -> PlanDraft(startDate = LocalDate.now(container.clock))
            }
            val steps = when (mode) {
                SetupMode.FIRST -> SetupStep.entries.toList()
                else -> SetupStep.entries.filter { it != SetupStep.WELCOME && it != SetupStep.PIN && it != SetupStep.PERMISSIONS }
            }
            _ui.update {
                it.copy(
                    loading = false,
                    steps = steps,
                    step = if (mode == SetupMode.EDIT) SetupStep.REVIEW else steps.first(),
                    draft = draft,
                    returnToReview = mode == SetupMode.EDIT,
                )
            }
        }
    }

    fun edit(transform: (PlanDraft) -> PlanDraft) = _ui.update { it.copy(draft = transform(it.draft), errors = emptyList()) }

    fun editBands(transform: (List<BandDraft>) -> List<BandDraft>) =
        edit { it.copy(bands = transform(it.bands), scaleChecked = false) }

    fun useCommonScale() = editBands { PlanDraft.commonBands() }

    fun addBand() = editBands { bands -> bands.dropLast(1) + BandDraft("", "") + bands.last() }

    fun removeBand() = editBands { bands -> if (bands.size > 1) bands.dropLast(2) + bands.last() else bands }

    fun setBand(index: Int, band: BandDraft) = editBands { bands -> bands.toMutableList().also { it[index] = band } }

    fun setSlot(slot: Slot, transform: (SlotDraft) -> SlotDraft) =
        edit { draft -> draft.copy(slots = draft.slots.map { if (it.slot == slot) transform(it) else it }) }

    fun setSlotTime(slot: Slot, time: LocalTime) = setSlot(slot) { it.copy(time = time) }

    // Medicines are edited one at a time on their own form.
    fun startMedicine(existing: MedicineDraft?) = _ui.update { it.copy(editingMedicine = existing ?: MedicineDraft(), medicineErrors = emptyList()) }

    fun editMedicine(transform: (MedicineDraft) -> MedicineDraft) =
        _ui.update { state -> state.copy(editingMedicine = state.editingMedicine?.let(transform), medicineErrors = emptyList()) }

    fun cancelMedicine() = _ui.update { it.copy(editingMedicine = null, medicineErrors = emptyList()) }

    fun saveMedicine() {
        val medicine = _ui.value.editingMedicine ?: return
        val errors = DraftValidator.medicine(medicine)
        if (errors.isNotEmpty()) {
            _ui.update { it.copy(medicineErrors = errors) }
            return
        }
        edit { draft ->
            val exists = draft.medicines.any { it.key == medicine.key }
            draft.copy(medicines = if (exists) draft.medicines.map { if (it.key == medicine.key) medicine else it } else draft.medicines + medicine)
        }
        _ui.update { it.copy(editingMedicine = null) }
    }

    fun removeMedicine(key: String) = edit { draft -> draft.copy(medicines = draft.medicines.filterNot { it.key == key }) }

    fun setPin(pin: String) = _ui.update { it.copy(pin = pin.filter(Char::isDigit).take(4), pinError = null) }

    fun setPinConfirm(pin: String) = _ui.update { it.copy(pinConfirm = pin.filter(Char::isDigit).take(4), pinError = null) }

    fun choosePin() {
        val state = _ui.value
        when {
            !PinHasher.isValidPin(state.pin) -> _ui.update { it.copy(pinError = PinError.INVALID) }
            state.pin != state.pinConfirm -> _ui.update { it.copy(pinError = PinError.MISMATCH) }
            else -> {
                _ui.update { it.copy(chosenPin = state.pin) }
                next()
            }
        }
    }

    fun skipPin() {
        _ui.update { it.copy(chosenPin = null, pin = "", pinConfirm = "") }
        next()
    }

    fun goTo(step: SetupStep) = _ui.update { it.copy(step = step, errors = emptyList(), returnToReview = true) }

    fun next() {
        val state = _ui.value
        val errors = when (state.step) {
            SetupStep.INSULIN -> DraftValidator.insulin(state.draft)
            SetupStep.SCALE -> DraftValidator.scale(state.draft)
            SetupStep.SCALE_CHECK -> DraftValidator.scaleCheck(state.draft)
            SetupStep.TIMES -> DraftValidator.times(state.draft)
            SetupStep.GOALS -> DraftValidator.goals(state.draft)
            SetupStep.TARGETS -> DraftValidator.targets(state.draft)
            else -> emptyList()
        }
        if (errors.isNotEmpty()) {
            _ui.update { it.copy(errors = errors) }
            return
        }
        val nextStep = when {
            state.step == SetupStep.SCALE -> SetupStep.SCALE_CHECK
            state.returnToReview -> SetupStep.REVIEW
            else -> state.steps.getOrNull(state.steps.indexOf(state.step) + 1) ?: SetupStep.REVIEW
        }
        _ui.update { it.copy(step = nextStep, errors = emptyList()) }
    }

    /** Returns false when there is no earlier step, so the screen can close. */
    fun back(): Boolean {
        val state = _ui.value
        if (state.editingMedicine != null) {
            cancelMedicine()
            return true
        }
        if (state.step == SetupStep.REVIEW && state.mode == SetupMode.EDIT) return false
        if (state.returnToReview && state.step != SetupStep.REVIEW) {
            _ui.update { it.copy(step = if (state.step == SetupStep.SCALE_CHECK) SetupStep.SCALE else SetupStep.REVIEW, errors = emptyList()) }
            return true
        }
        val index = state.steps.indexOf(state.step)
        if (index <= 0) return false
        _ui.update { it.copy(step = state.steps[index - 1], errors = emptyList()) }
        return true
    }

    /** Checks every step again, then saves straight away or, for a change, shows the summary first. */
    fun finish() {
        val draft = _ui.value.draft
        val firstProblem = listOf(
            SetupStep.INSULIN to DraftValidator.insulin(draft),
            SetupStep.SCALE to DraftValidator.scale(draft),
            SetupStep.SCALE_CHECK to DraftValidator.scaleCheck(draft),
            SetupStep.TIMES to DraftValidator.times(draft),
            SetupStep.GOALS to DraftValidator.goals(draft),
            SetupStep.TARGETS to DraftValidator.targets(draft),
        ).firstOrNull { it.second.isNotEmpty() }
        if (firstProblem != null) {
            _ui.update { it.copy(step = firstProblem.first, errors = firstProblem.second, returnToReview = true) }
            return
        }
        val old = original
        if (mode == SetupMode.EDIT && old != null) {
            _ui.update { it.copy(changes = PlanChanges.describe(container.appContext, old, draft.toPlan())) }
        } else {
            save()
        }
    }

    fun dismissChanges() = _ui.update { it.copy(changes = null) }

    fun save() {
        val state = _ui.value
        if (state.saving) return
        _ui.update { it.copy(saving = true, changes = null) }
        viewModelScope.launch {
            container.repository.savePlanVersion(state.draft.toPlan())
            container.settingsStore.update { settings ->
                val pin = state.chosenPin
                val withPin = if (pin != null) {
                    val salt = PinHasher.newSalt()
                    settings.copy(pinSalt = salt, pinHash = PinHasher.hash(pin, salt))
                } else {
                    settings
                }
                withPin.copy(setupDone = true)
            }
            container.onPlanChanged()
            _ui.update { it.copy(saving = false, finished = true) }
        }
    }
}

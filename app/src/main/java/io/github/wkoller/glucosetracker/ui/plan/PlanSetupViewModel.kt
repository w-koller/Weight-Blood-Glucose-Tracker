package io.github.wkoller.glucosetracker.ui.plan

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.wkoller.glucosetracker.AppContainer
import io.github.wkoller.glucosetracker.CrashReporter
import io.github.wkoller.glucosetracker.data.prefs.PinHasher
import io.github.wkoller.glucosetracker.domain.dosing.SlidingScale
import io.github.wkoller.glucosetracker.domain.format.Measures
import io.github.wkoller.glucosetracker.domain.model.Plan
import io.github.wkoller.glucosetracker.domain.model.Slot
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime
import java.util.UUID

enum class SetupMode { FIRST, EDIT, NEW }

enum class SetupStep { WELCOME, ABOUT_YOU, INSULIN, SCALE, SCALE_CHECK, TIMES, MEDICINES, GOALS, TARGETS, PIN, PERMISSIONS, ASSISTANT, VOICES, REVIEW }

/** Steps that belong to the first setup only. Changing a plan or starting a new one skips them. */
private val FIRST_RUN_ONLY = setOf(SetupStep.WELCOME, SetupStep.ABOUT_YOU, SetupStep.PIN, SetupStep.PERMISSIONS, SetupStep.ASSISTANT, SetupStep.VOICES)

enum class PinError { INVALID, MISMATCH }

enum class BandField { UPPER, UNITS }

/** A problem with the one number on the scale keypad, shown beside the keys. */
sealed interface BandProblem {
    data object Missing : BandProblem
    data class NotAbove(val previous: String) : BandProblem
    data object UpperOutOfRange : BandProblem
    data object UnitsOutOfRange : BandProblem
}

/**
 * One number of the sliding scale being typed on the keypad. When [guided], Next moves on through
 * the rest of the table, as when the user starts from an empty table; otherwise it changes one row.
 */
data class BandEdit(val index: Int, val field: BandField, val text: String, val guided: Boolean, val problem: BandProblem? = null)

data class SetupUi(
    val loading: Boolean = true,
    val mode: SetupMode = SetupMode.FIRST,
    val steps: List<SetupStep> = emptyList(),
    val step: SetupStep = SetupStep.WELCOME,
    val draft: PlanDraft = PlanDraft(),
    val errors: List<DraftError> = emptyList(),
    val returnToReview: Boolean = false,
    val editingMedicine: MedicineDraft? = null,
    val editingBand: BandEdit? = null,
    val medicineErrors: List<DraftError> = emptyList(),
    val pin: String = "",
    val pinConfirm: String = "",
    val pinError: PinError? = null,
    /** The PIN chosen during setup, kept only as its salt and hash until the plan is saved. */
    val chosenPinSalt: String? = null,
    val chosenPinHash: String? = null,
    val firstName: String = "",
    val familyName: String = "",
    val supportName: String = "",
    val supportPhone: String = "",
    /** Whether to download the assistant model after setup. Null until the user chooses. */
    val downloadModel: Boolean? = null,
    /** True when the user agreed to download the model over mobile data. */
    val modelOverMobile: Boolean = false,
    /** Whether to download the natural voices after setup. Null until the user chooses. */
    val downloadVoices: Boolean? = null,
    val voicesOverMobile: Boolean = false,
    val changes: List<String>? = null,
    val saving: Boolean = false,
    /** True when saving the plan failed, so the review can say so and the user can try again. */
    val saveFailed: Boolean = false,
    val finished: Boolean = false,
) {
    val stepNumber: Int get() = steps.indexOf(step) + 1
}

class PlanSetupViewModel(private val container: AppContainer, private val handle: SavedStateHandle) : ViewModel() {
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
                else -> SetupStep.entries.filter { it !in FIRST_RUN_ONLY }
            }
            val settings = container.settingsStore.current()
            // A setup that Android closed in the background carries on where it was.
            val saved = handle.get<String>(KEY_SAVED)?.let(SetupStateJson::decode)
            _ui.update {
                val fresh = it.copy(
                    loading = false,
                    firstName = settings.firstName,
                    familyName = settings.familyName,
                    supportName = settings.supportName,
                    supportPhone = settings.supportPhone,
                    steps = steps,
                    step = if (mode == SetupMode.EDIT) SetupStep.REVIEW else steps.first(),
                    draft = draft,
                    returnToReview = mode == SetupMode.EDIT,
                )
                saved?.applyTo(fresh) ?: fresh
            }
            _ui.map { SavedSetup.of(it) }.distinctUntilChanged().collect { handle[KEY_SAVED] = SetupStateJson.encode(it) }
        }
    }

    fun edit(transform: (PlanDraft) -> PlanDraft) = _ui.update { it.copy(draft = transform(it.draft), errors = emptyList()) }

    fun editBands(transform: (List<BandDraft>) -> List<BandDraft>) =
        edit { it.copy(bands = transform(it.bands), scaleChecked = false) }

    fun useCommonScale() = editBands { PlanDraft.commonBands() }

    /** Starts an empty table and walks through it on the keypad, one number at a time. */
    fun startEmptyTable() {
        editBands { PlanDraft.emptyBands() }
        _ui.update { it.copy(editingBand = BandEdit(0, BandField.UPPER, "", guided = true)) }
    }

    /** Adds a range just above the open top row, and opens the keypad for it. */
    fun addBand() {
        editBands { bands -> if (bands.isEmpty()) PlanDraft.emptyBands() else bands.dropLast(1) + BandDraft("", "") + bands.last() }
        startBand(_ui.value.draft.bands.size - 2)
    }

    /** Opens the keypad for one row: the top of its range, then its units. The open top row has units only. */
    fun startBand(index: Int) = _ui.update { state ->
        val band = state.draft.bands.getOrNull(index) ?: return@update state
        val field = if (index == state.draft.bands.lastIndex) BandField.UNITS else BandField.UPPER
        state.copy(editingBand = BandEdit(index, field, if (field == BandField.UPPER) band.upper else band.units, guided = false), errors = emptyList())
    }

    fun setBandText(text: String) = _ui.update { it.copy(editingBand = it.editingBand?.copy(text = text, problem = null)) }

    /** Back to the table. Numbers already accepted with Next stay in it. */
    fun closeBand() = _ui.update { it.copy(editingBand = null) }

    fun nextBand() {
        val state = _ui.value
        val edit = state.editingBand ?: return
        val problem = bandProblem(state.draft, edit)
        if (problem != null) {
            _ui.update { it.copy(editingBand = edit.copy(problem = problem)) }
            return
        }
        val band = state.draft.bands[edit.index]
        setBand(edit.index, if (edit.field == BandField.UPPER) band.copy(upper = edit.text.trimEnd('.')) else band.copy(units = edit.text))
        val bands = _ui.value.draft.bands
        val following = when {
            edit.field == BandField.UPPER -> BandEdit(edit.index, BandField.UNITS, bands[edit.index].units, edit.guided)
            edit.guided && edit.index < bands.lastIndex -> {
                val index = edit.index + 1
                if (index == bands.lastIndex) {
                    BandEdit(index, BandField.UNITS, bands[index].units, guided = true)
                } else {
                    BandEdit(index, BandField.UPPER, bands[index].upper, guided = true)
                }
            }
            else -> null
        }
        _ui.update { it.copy(editingBand = following) }
    }

    /** Checks one number as it is entered, so a mistake is caught beside the keypad rather than later in the table. */
    private fun bandProblem(draft: PlanDraft, edit: BandEdit): BandProblem? = when (edit.field) {
        BandField.UPPER -> {
            val tenths = Measures.parseScaled(edit.text, 1)?.toInt()
            val previous = draft.bands.getOrNull(edit.index - 1)?.upper?.let { Measures.parseScaled(it, 1)?.toInt() }
            when {
                tenths == null -> BandProblem.Missing
                tenths !in 1..SlidingScale.MAX_UPPER_TENTHS -> BandProblem.UpperOutOfRange
                previous != null && tenths <= previous -> BandProblem.NotAbove(Measures.formatTenthsShort(previous))
                else -> null
            }
        }
        BandField.UNITS -> {
            val units = edit.text.toIntOrNull()
            when {
                units == null -> BandProblem.Missing
                units !in 0..SlidingScale.MAX_UNITS -> BandProblem.UnitsOutOfRange
                else -> null
            }
        }
    }

    /** Removes the last range before the open top row, keeping at least one range. */
    fun removeBand() = editBands { bands -> if (bands.size > 2) bands.dropLast(2) + bands.last() else bands }

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

    fun setFirstName(name: String) = _ui.update { it.copy(firstName = name) }

    fun setFamilyName(name: String) = _ui.update { it.copy(familyName = name) }

    fun setSupportName(name: String) = _ui.update { it.copy(supportName = name) }

    fun setSupportPhone(phone: String) = _ui.update { it.copy(supportPhone = phone) }

    fun chooseAssistant(download: Boolean, overMobile: Boolean = false) {
        _ui.update { it.copy(downloadModel = download, modelOverMobile = download && overMobile) }
        next()
    }

    fun chooseVoices(download: Boolean, overMobile: Boolean = false) {
        _ui.update { it.copy(downloadVoices = download, voicesOverMobile = download && overMobile) }
        next()
    }

    fun setPin(pin: String) = _ui.update { it.copy(pin = pin.filter(Char::isDigit).take(4), pinError = null) }

    fun setPinConfirm(pin: String) = _ui.update { it.copy(pinConfirm = pin.filter(Char::isDigit).take(4), pinError = null) }

    fun choosePin() {
        val state = _ui.value
        when {
            !PinHasher.isValidPin(state.pin) -> _ui.update { it.copy(pinError = PinError.INVALID) }
            state.pin != state.pinConfirm -> _ui.update { it.copy(pinError = PinError.MISMATCH) }
            else -> {
                val salt = PinHasher.newSalt()
                _ui.update { it.copy(chosenPinSalt = salt, chosenPinHash = PinHasher.hash(state.pin, salt)) }
                next()
            }
        }
    }

    fun skipPin() {
        _ui.update { it.copy(chosenPinSalt = null, chosenPinHash = null, pin = "", pinConfirm = "") }
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
        if (state.editingBand != null) {
            closeBand()
            return true
        }
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
            viewModelScope.launch {
                val naming = container.settingsStore.current().slotNaming
                _ui.update { it.copy(changes = PlanChanges.describe(container.appContext, old, draft.toPlan(), naming)) }
            }
        } else {
            save()
        }
    }

    fun dismissChanges() = _ui.update { it.copy(changes = null) }

    fun save() {
        val state = _ui.value
        if (state.saving) return
        _ui.update { it.copy(saving = true, saveFailed = false, changes = null) }
        viewModelScope.launch {
            try {
                savePlan(state)
                _ui.update { it.copy(saving = false, finished = true) }
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                CrashReporter.record(container.appContext, error)
                _ui.update { it.copy(saving = false, saveFailed = true) }
            }
        }
    }

    private suspend fun savePlan(state: SetupUi) {
        container.repository.savePlanVersion(state.draft.toPlan())
        val downloadModel = state.mode == SetupMode.FIRST && state.downloadModel == true
        container.settingsStore.update { settings ->
            val salt = state.chosenPinSalt
            val hash = state.chosenPinHash
            val withPin = if (salt != null && hash != null) settings.copy(pinSalt = salt, pinHash = hash) else settings
            val withName = if (state.mode == SetupMode.FIRST) {
                withPin.copy(
                    firstName = state.firstName.trim(),
                    familyName = state.familyName.trim(),
                    supportName = state.supportName.trim(),
                    supportPhone = state.supportPhone.trim(),
                )
            } else {
                withPin
            }
            withName.copy(setupDone = true, assistantEnabled = withName.assistantEnabled || downloadModel)
        }
        container.onPlanChanged()
        if (downloadModel) container.models.startDownload(state.modelOverMobile)
        if (state.mode == SetupMode.FIRST && state.downloadVoices == true) container.naturalVoices.start(state.voicesOverMobile)
    }

    private companion object {
        /** Where the setup in progress is kept, so it survives Android closing the app in the background. */
        const val KEY_SAVED = "setup"
    }
}

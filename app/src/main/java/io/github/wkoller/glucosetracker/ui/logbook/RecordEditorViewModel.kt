package io.github.wkoller.glucosetracker.ui.logbook

import androidx.annotation.StringRes
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.wkoller.glucosetracker.AppContainer
import io.github.wkoller.glucosetracker.R
import io.github.wkoller.glucosetracker.domain.dosing.DoseAdvisor
import io.github.wkoller.glucosetracker.domain.format.Measures
import io.github.wkoller.glucosetracker.domain.model.BodyMeasurement
import io.github.wkoller.glucosetracker.domain.model.DoseKind
import io.github.wkoller.glucosetracker.domain.model.DoseRecord
import io.github.wkoller.glucosetracker.domain.model.DoseStatus
import io.github.wkoller.glucosetracker.domain.model.GlucoseReading
import io.github.wkoller.glucosetracker.domain.model.MedicineKind
import io.github.wkoller.glucosetracker.domain.model.MeterFlag
import io.github.wkoller.glucosetracker.domain.model.Plan
import io.github.wkoller.glucosetracker.domain.model.Slot
import io.github.wkoller.glucosetracker.domain.schedule.SlotResolver
import io.github.wkoller.glucosetracker.domain.validation.InputValidator
import io.github.wkoller.glucosetracker.domain.validation.NumberInput
import io.github.wkoller.glucosetracker.ui.checkin.CheckInViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime

enum class RecordType { READING, DOSE, MEASUREMENT }

/** The questions the editor asks, one per screen, and the summary that ties them together. */
enum class EditorStep { CHOOSE_TYPE, MEDICINE, SLOT, BGL, DOSE_STATUS, AMOUNT, WEIGHT, WAIST, TIME, NOTE, SUMMARY }

/** A medicine that can be recorded: the sliding-scale insulin when [key] is null, otherwise one from the plan. */
data class MedicineChoice(val key: String?, val name: String, val kind: MedicineKind, val unit: String)

/** An amount that differs from the prescription and is waiting for the user to confirm it. */
data class AmountCheck(val prescribed: Double, val amount: Double, val readingTenths: Int?)

data class EditorUi(
    val loading: Boolean = true,
    val adding: Boolean = false,
    val date: LocalDate = LocalDate.now(),
    val plan: Plan? = null,
    val type: RecordType? = null,
    val step: EditorStep = EditorStep.SUMMARY,
    /** Questions still to come while adding a record. */
    val queue: List<EditorStep> = emptyList(),
    /** Where Back goes from each question. */
    val history: List<EditorStep> = emptyList(),
    val slot: Slot = Slot.OTHER,
    val time: LocalTime = LocalTime.of(8, 0),
    val note: String = "",
    val bglText: String = "",
    val meterFlag: MeterFlag? = null,
    val medicine: MedicineChoice? = null,
    val given: Boolean = true,
    val amountText: String = "",
    val weightText: String = "",
    val waistText: String = "",
    @StringRes val error: Int? = null,
    val amountCheck: AmountCheck? = null,
    val busy: Boolean = false,
    val finished: Boolean = false,
) {
    val medicineChoices: List<MedicineChoice>
        get() = plan?.let { p ->
            listOf(MedicineChoice(null, p.correctionInsulin, MedicineKind.INSULIN, CheckInViewModel.UNITS)) +
                p.medicines.map { MedicineChoice(it.key, it.displayName, it.kind, it.unit) }
        }.orEmpty()
}

/**
 * Changes a record in the logbook, or adds one the user forgot to record. Each detail is asked on
 * its own screen with the same large keypad as a check-in, and a summary shows everything before
 * it is saved. Adding a record never shows dose advice: the user records what was given.
 */
class RecordEditorViewModel(private val container: AppContainer, handle: SavedStateHandle) : ViewModel() {
    private val repository = container.repository
    private val date: LocalDate = LocalDate.parse(handle.get<String>("date"))
    private val kind: String? = handle.get<String>("kind")?.ifBlank { null }
    private val id: Long = handle.get<String>("id")?.toLongOrNull() ?: 0

    private var reading: GlucoseReading? = null
    private var dose: DoseRecord? = null
    private var measurement: BodyMeasurement? = null

    private val _ui = MutableStateFlow(EditorUi(date = date))
    val ui: StateFlow<EditorUi> = _ui.asStateFlow()

    init {
        viewModelScope.launch {
            when (kind) {
                KIND_READING -> repository.readingsBetween(date, date).firstOrNull { it.id == id }?.let { r ->
                    reading = r
                    _ui.update {
                        it.copy(
                            type = RecordType.READING, slot = r.slot, time = localTime(r.takenAt), note = r.note.orEmpty(),
                            bglText = if (r.meterFlag == null) Measures.formatTenths(r.tenths) else "", meterFlag = r.meterFlag,
                        )
                    }
                }
                KIND_DOSE -> repository.dosesBetween(date, date).firstOrNull { it.id == id }?.let { d ->
                    dose = d
                    _ui.update {
                        it.copy(
                            type = RecordType.DOSE, slot = d.slot, time = localTime(d.takenAt), note = d.note.orEmpty(),
                            medicine = MedicineChoice(d.medicineKey, d.medicineName, d.medicineKind, d.unit),
                            given = d.status == DoseStatus.GIVEN,
                            amountText = (d.givenAmount ?: d.prescribedAmount)?.let(Measures::formatAmount).orEmpty(),
                        )
                    }
                }
                KIND_MEASUREMENT -> repository.allMeasurements().firstOrNull { it.id == id }?.let { m ->
                    measurement = m
                    _ui.update {
                        it.copy(
                            type = RecordType.MEASUREMENT, time = localTime(m.takenAt), note = m.note.orEmpty(),
                            weightText = m.weightGrams?.let(Measures::formatKg).orEmpty(),
                            waistText = m.waistMm?.let(Measures::formatCm).orEmpty(),
                        )
                    }
                }
            }
            val adding = kind == null
            val plan = repository.planAt(date.atTime(LocalTime.NOON).atZone(container.clock.zone).toInstant()) ?: repository.currentPlan()
            _ui.update {
                it.copy(
                    loading = false,
                    adding = adding,
                    plan = plan,
                    step = if (adding) EditorStep.CHOOSE_TYPE else EditorStep.SUMMARY,
                    finished = !adding && it.type == null,
                )
            }
        }
    }

    // Moving between questions

    fun chooseType(type: RecordType, firstMeasure: EditorStep = EditorStep.WEIGHT) {
        val steps = when (type) {
            RecordType.READING -> listOf(EditorStep.SLOT, EditorStep.BGL, EditorStep.TIME)
            RecordType.DOSE -> listOf(EditorStep.MEDICINE, EditorStep.SLOT, EditorStep.DOSE_STATUS, EditorStep.AMOUNT, EditorStep.TIME)
            RecordType.MEASUREMENT -> listOf(firstMeasure, EditorStep.TIME)
        }
        _ui.update {
            it.copy(
                type = type, step = steps.first(), queue = steps.drop(1), history = listOf(EditorStep.CHOOSE_TYPE), error = null,
                time = if (type == RecordType.MEASUREMENT) defaultMeasureTime(it.plan) else it.time,
            )
        }
    }

    /** Opens one question from the summary. Next brings the user back to the summary. */
    fun change(step: EditorStep) = _ui.update { it.copy(step = step, queue = emptyList(), history = listOf(EditorStep.SUMMARY), error = null) }

    fun back(): Boolean {
        val state = _ui.value
        val previous = state.history.lastOrNull() ?: return false
        _ui.update {
            it.copy(
                step = previous,
                // Going back while adding puts the current question in front of the ones still to come.
                queue = if (state.adding && previous != EditorStep.SUMMARY && previous != EditorStep.CHOOSE_TYPE) listOf(state.step) + state.queue else state.queue,
                history = state.history.dropLast(1),
                error = null,
            )
        }
        return true
    }

    fun next() {
        val state = _ui.value
        val error = validate(state)
        if (error != null) {
            _ui.update { it.copy(error = error) }
            return
        }
        var queue = state.queue
        if (state.step == EditorStep.DOSE_STATUS) {
            queue = if (!state.given) queue - EditorStep.AMOUNT else if (EditorStep.AMOUNT !in queue) listOf(EditorStep.AMOUNT) + queue else queue
        }
        val following = queue.firstOrNull()
        _ui.update {
            if (following == null) {
                it.copy(step = EditorStep.SUMMARY, queue = emptyList(), history = emptyList(), error = null)
            } else {
                it.copy(step = following, queue = queue.drop(1), history = it.history + it.step, error = null)
            }
        }
    }

    private fun validate(state: EditorUi): Int? = when (state.step) {
        EditorStep.BGL -> if (state.meterFlag == null && InputValidator.bgl(state.bglText) !is NumberInput.Valid) R.string.edit_bgl_invalid else null
        EditorStep.AMOUNT -> if (InputValidator.amount(state.amountText) !is NumberInput.Valid) R.string.edit_amount_invalid else null
        EditorStep.WEIGHT -> weightError(state.weightText, required = state.waistText.isBlank())
        EditorStep.WAIST -> waistError(state.waistText, required = state.weightText.isBlank())
        EditorStep.MEDICINE -> if (state.medicine == null) R.string.editor_choose_one else null
        EditorStep.TIME -> if (takenAt(state).isAfter(repository.instant())) R.string.editor_time_future else null
        else -> null
    }

    private fun weightError(text: String, required: Boolean): Int? = when (InputValidator.weight(text)) {
        is NumberInput.Valid -> null
        NumberInput.Empty -> if (required) R.string.measure_empty else null
        else -> R.string.measure_weight_range
    }

    private fun waistError(text: String, required: Boolean): Int? = when (InputValidator.waist(text)) {
        is NumberInput.Valid -> null
        NumberInput.Empty -> if (required) R.string.measure_empty else null
        else -> R.string.measure_waist_range
    }

    // Answers

    fun setSlot(slot: Slot) = _ui.update {
        // While adding, the planned time for that check is a good first guess at when it happened.
        val planned = it.plan?.schedule(slot)?.time
        it.copy(slot = slot, time = if (it.adding && planned != null) planned else it.time)
    }

    fun setTime(time: LocalTime) = _ui.update { it.copy(time = time) }
    fun setNote(note: String) = _ui.update { it.copy(note = note) }
    fun setBglText(text: String) = _ui.update { it.copy(bglText = text, meterFlag = null, error = null) }

    fun meterShows(flag: MeterFlag) {
        _ui.update { it.copy(bglText = "", meterFlag = flag, error = null) }
        next()
    }

    fun setMedicine(choice: MedicineChoice) = _ui.update { it.copy(medicine = choice, error = null) }

    fun setGiven(given: Boolean) = _ui.update {
        val prescribed = prescribedFor(it)
        it.copy(given = given, amountText = if (given && it.amountText.isBlank() && prescribed != null) Measures.formatAmount(prescribed) else it.amountText)
    }

    fun setAmountText(text: String) = _ui.update { it.copy(amountText = text, error = null) }
    fun setWeightText(text: String) = _ui.update { it.copy(weightText = text, error = null) }
    fun setWaistText(text: String) = _ui.update { it.copy(waistText = text, error = null) }

    // Saving

    fun save() {
        val state = _ui.value
        if (state.busy) return
        if (state.type == RecordType.DOSE && state.given) {
            viewModelScope.launch {
                val amount = (InputValidator.amount(state.amountText) as? NumberInput.Valid)?.value ?: return@launch
                val (prescribed, readingTenths) = prescribedAmount(state)
                // Only an amount changed in this edit is checked, so saving a note does not ask again.
                val changed = dose?.givenAmount != amount
                if (changed && prescribed != null && prescribed != amount) {
                    _ui.update { it.copy(amountCheck = AmountCheck(prescribed, amount, readingTenths)) }
                } else {
                    write()
                }
            }
        } else {
            write()
        }
    }

    fun confirmAmount() {
        _ui.update { it.copy(amountCheck = null) }
        write()
    }

    fun cancelAmount() = _ui.update { it.copy(amountCheck = null) }

    private fun write() {
        val state = _ui.value
        if (state.busy) return
        _ui.update { it.copy(busy = true) }
        viewModelScope.launch {
            try {
                when (state.type) {
                    RecordType.READING -> saveReading(state)
                    RecordType.DOSE -> saveDose(state)
                    RecordType.MEASUREMENT -> saveMeasurement(state)
                    null -> Unit
                }
                _ui.update { it.copy(finished = true) }
            } finally {
                _ui.update { it.copy(busy = false) }
            }
        }
    }

    private suspend fun saveReading(state: EditorUi) {
        val takenAt = takenAt(state)
        val tenths = when (state.meterFlag) {
            MeterFlag.LO -> InputValidator.METER_MIN_TENTHS
            MeterFlag.HI -> InputValidator.METER_MAX_TENTHS
            null -> (InputValidator.bgl(state.bglText) as? NumberInput.Valid)?.value ?: return
        }
        val note = state.note.trim().ifBlank { null }
        val existing = reading
        if (existing != null) {
            repository.updateReading(existing.copy(tenths = tenths, meterFlag = state.meterFlag, slot = state.slot, takenAt = takenAt, note = note))
        } else {
            val plan = repository.planAt(takenAt) ?: return
            repository.addReading(GlucoseReading(0, plan.id, takenAt, date, state.slot, tenths, state.meterFlag, note))
        }
    }

    private suspend fun saveDose(state: EditorUi) {
        val takenAt = takenAt(state)
        val given = if (state.given) (InputValidator.amount(state.amountText) as? NumberInput.Valid)?.value ?: return else null
        val status = if (state.given) DoseStatus.GIVEN else DoseStatus.SKIPPED
        val note = state.note.trim().ifBlank { null }
        val existing = dose
        if (existing != null) {
            repository.updateDose(
                existing.copy(
                    status = status, givenAmount = given, takenAt = takenAt, note = note,
                    reason = if (state.given) null else existing.reason,
                ),
            )
            return
        }
        val plan = repository.planAt(takenAt) ?: return
        val medicine = state.medicine ?: return
        val (prescribed, _) = prescribedAmount(state)
        val readingId = repository.dayRecords(date).readings.filter { it.slot == state.slot }.maxByOrNull { it.takenAt }?.id
        repository.addDose(
            DoseRecord(
                id = 0, planId = plan.id, readingId = readingId, takenAt = takenAt, date = date, slot = state.slot,
                kind = if (medicine.key == null) DoseKind.CORRECTION else DoseKind.FIXED,
                medicineKey = medicine.key, medicineName = medicine.name, medicineKind = medicine.kind,
                prescribedAmount = prescribed, givenAmount = given, unit = medicine.unit, status = status, reason = null, note = note,
            ),
        )
    }

    private suspend fun saveMeasurement(state: EditorUi) {
        val takenAt = takenAt(state)
        val weight = (InputValidator.weight(state.weightText) as? NumberInput.Valid)?.value
        val waist = (InputValidator.waist(state.waistText) as? NumberInput.Valid)?.value
        if (weight == null && waist == null) return
        val note = state.note.trim().ifBlank { null }
        val existing = measurement
        if (existing != null) {
            repository.updateMeasurement(existing.copy(weightGrams = weight, waistMm = waist, takenAt = takenAt, note = note))
        } else {
            repository.addMeasurement(BodyMeasurement(0, takenAt, date, weight, waist, note))
        }
    }

    /**
     * What the prescription said for this dose. For the sliding-scale insulin, that is the scale's
     * amount for the reading taken at the same time of day, worked out by [DoseAdvisor] as in a
     * check-in. With no reading there is nothing to compare with.
     */
    private suspend fun prescribedAmount(state: EditorUi): Pair<Double?, Int?> {
        dose?.let { return it.prescribedAmount to null }
        val medicine = state.medicine ?: return null to null
        if (medicine.key != null) return prescribedFor(state) to null
        val plan = state.plan ?: return null to null
        val reading = repository.dayRecords(date).readings.filter { it.slot == state.slot }.maxByOrNull { it.takenAt } ?: return null to null
        val advice = DoseAdvisor.advise(plan, state.slot, reading.tenths, reading.meterFlag, reading.takenAt, date, emptyList())
        return if (advice.isHypo) null to null else advice.units.toDouble() to reading.tenths
    }

    private fun prescribedFor(state: EditorUi): Double? {
        dose?.let { return it.prescribedAmount }
        val key = state.medicine?.key ?: return null
        return state.plan?.medicines?.firstOrNull { it.key == key }?.doses?.get(state.slot)
    }

    private fun takenAt(state: EditorUi): Instant {
        val plan = state.plan
        val moment = if (plan != null) SlotResolver.momentOn(plan, date, state.time) else date.atTime(state.time)
        return moment.atZone(container.clock.zone).toInstant()
    }

    private fun localTime(at: Instant): LocalTime = at.atZone(container.clock.zone).toLocalTime().withSecond(0).withNano(0)

    private fun defaultMeasureTime(plan: Plan?): LocalTime =
        plan?.activeSlots?.firstOrNull()?.time ?: LocalTime.of(8, 0)

    companion object {
        const val KIND_READING = "reading"
        const val KIND_DOSE = "dose"
        const val KIND_MEASUREMENT = "measurement"
    }
}

package io.github.wkoller.glucosetracker.ui.measure

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.wkoller.glucosetracker.AppContainer
import io.github.wkoller.glucosetracker.domain.model.BodyMeasurement
import io.github.wkoller.glucosetracker.domain.model.Frequency
import io.github.wkoller.glucosetracker.domain.model.Plan
import io.github.wkoller.glucosetracker.domain.progress.Progress
import io.github.wkoller.glucosetracker.domain.progress.ProgressCalculator
import io.github.wkoller.glucosetracker.domain.schedule.NextStep
import io.github.wkoller.glucosetracker.domain.schedule.NextStepEngine
import io.github.wkoller.glucosetracker.domain.validation.InputValidator
import io.github.wkoller.glucosetracker.domain.validation.NumberInput
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate

enum class MeasureStep { WEIGHT, WAIST_OFFER, WAIST, DONE }

data class MeasureUi(
    val loading: Boolean = true,
    val plan: Plan? = null,
    val step: MeasureStep = MeasureStep.WEIGHT,
    val text: String = "",
    val error: NumberInput<Int>? = null,
    val lastWeight: BodyMeasurement? = null,
    val lastWaist: BodyMeasurement? = null,
    val confirmLargeChange: Int? = null,
    val saved: BodyMeasurement? = null,
    val weight: Progress? = null,
    val waist: Progress? = null,
    val today: LocalDate = LocalDate.now(),
    val nextStep: NextStep? = null,
    val busy: Boolean = false,
)

class MeasureViewModel(private val container: AppContainer, handle: SavedStateHandle) : ViewModel() {
    private val repository = container.repository
    private val startWithWaist = handle.get<String>("what") == "waist"

    private val _ui = MutableStateFlow(MeasureUi(step = if (startWithWaist) MeasureStep.WAIST else MeasureStep.WEIGHT))
    val ui: StateFlow<MeasureUi> = _ui.asStateFlow()

    init {
        viewModelScope.launch {
            val plan = repository.currentPlan()
            val measurements = repository.allMeasurements()
            _ui.update {
                it.copy(
                    loading = false,
                    plan = plan,
                    today = repository.logicalToday(plan),
                    lastWeight = measurements.lastOrNull { m -> m.weightGrams != null },
                    lastWaist = measurements.lastOrNull { m -> m.waistMm != null },
                )
            }
        }
    }

    fun setText(text: String) = _ui.update { it.copy(text = text, error = null) }

    fun submit() {
        val state = _ui.value
        when (state.step) {
            MeasureStep.WEIGHT -> when (val input = InputValidator.weight(state.text)) {
                is NumberInput.Valid -> {
                    if (InputValidator.isLargeWeightChange(input.value, state.lastWeight?.weightGrams)) {
                        _ui.update { it.copy(confirmLargeChange = input.value) }
                    } else {
                        saveWeight(input.value)
                    }
                }
                else -> _ui.update { it.copy(error = input) }
            }
            MeasureStep.WAIST -> when (val input = InputValidator.waist(state.text)) {
                is NumberInput.Valid -> saveWaist(input.value)
                else -> _ui.update { it.copy(error = input) }
            }
            else -> Unit
        }
    }

    fun confirmLargeChange() {
        val grams = _ui.value.confirmLargeChange ?: return
        _ui.update { it.copy(confirmLargeChange = null) }
        saveWeight(grams)
    }

    fun cancelLargeChange() = _ui.update { it.copy(confirmLargeChange = null) }

    private fun saveWeight(grams: Int) = work {
        val measurement = BodyMeasurement(0, repository.instant(), _ui.value.today, weightGrams = grams, waistMm = null, note = null)
        val saved = measurement.copy(id = repository.addMeasurement(measurement))
        _ui.update { it.copy(saved = saved, text = "") }
        val plan = _ui.value.plan
        val waistDue = plan != null && plan.goals.waistEvery != Frequency.OFF &&
            NextStepEngine.next(plan, repository.now(), repository.dayRecords(_ui.value.today)) == NextStep.MeasureWaist
        if (waistDue) _ui.update { it.copy(step = MeasureStep.WAIST_OFFER) } else finish()
    }

    fun measureWaist() = _ui.update { it.copy(step = MeasureStep.WAIST, text = "", error = null) }

    fun skipWaist() = work { finish() }

    private fun saveWaist(mm: Int) = work {
        val existing = _ui.value.saved
        if (existing != null) {
            val updated = existing.copy(waistMm = mm)
            repository.updateMeasurement(updated)
            _ui.update { it.copy(saved = updated) }
        } else {
            val measurement = BodyMeasurement(0, repository.instant(), _ui.value.today, weightGrams = null, waistMm = mm, note = null)
            _ui.update { it.copy(saved = measurement.copy(id = repository.addMeasurement(measurement))) }
        }
        finish()
    }

    private suspend fun finish() {
        val plan = _ui.value.plan
        val measurements = repository.allMeasurements()
        val next = plan?.let { NextStepEngine.next(it, repository.now(), repository.dayRecords(_ui.value.today)) }
        _ui.update {
            it.copy(
                step = MeasureStep.DONE,
                weight = plan?.let { p -> ProgressCalculator.weight(p.goals, measurements) },
                waist = plan?.let { p -> ProgressCalculator.waist(p.goals, measurements) },
                nextStep = next,
            )
        }
    }

    fun back(): Boolean {
        val state = _ui.value
        if (state.step == MeasureStep.WAIST && state.saved != null) {
            _ui.update { it.copy(step = MeasureStep.WAIST_OFFER) }
            return true
        }
        return false
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
}

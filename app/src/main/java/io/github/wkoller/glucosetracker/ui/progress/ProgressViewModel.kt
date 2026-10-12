package io.github.wkoller.glucosetracker.ui.progress

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.wkoller.glucosetracker.AppContainer
import io.github.wkoller.glucosetracker.domain.model.BodyMeasurement
import io.github.wkoller.glucosetracker.domain.model.GlucoseReading
import io.github.wkoller.glucosetracker.domain.model.Plan
import io.github.wkoller.glucosetracker.domain.progress.DoseSummary
import io.github.wkoller.glucosetracker.domain.progress.GlucoseStats
import io.github.wkoller.glucosetracker.domain.progress.GlucoseSummary
import io.github.wkoller.glucosetracker.domain.progress.Progress
import io.github.wkoller.glucosetracker.domain.progress.ProgressCalculator
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate

data class ProgressUi(
    val loading: Boolean = true,
    val days: Int = 14,
    val today: LocalDate = LocalDate.now(),
    val plan: Plan? = null,
    val weight: Progress? = null,
    val waist: Progress? = null,
    val measurements: List<BodyMeasurement> = emptyList(),
    val readings: List<GlucoseReading> = emptyList(),
    val glucose: GlucoseSummary? = null,
    val doses: List<DoseSummary> = emptyList(),
)

@OptIn(ExperimentalCoroutinesApi::class)
class ProgressViewModel(container: AppContainer) : ViewModel() {
    private val repository = container.repository
    private val today = LocalDate.now(container.clock)
    private val days = MutableStateFlow(14)

    val ui: StateFlow<ProgressUi> = days.flatMapLatest { span ->
        val from = today.minusDays(span - 1L)
        combine(repository.activePlan, repository.readings(from, today), repository.doses(from, today), repository.measurements) { plan, readings, doses, measurements ->
            ProgressUi(
                loading = false,
                days = span,
                today = today,
                plan = plan,
                weight = plan?.let { ProgressCalculator.weight(it.goals, measurements) },
                waist = plan?.let { ProgressCalculator.waist(it.goals, measurements) },
                measurements = measurements.filter { !it.date.isBefore(from) },
                readings = readings,
                glucose = plan?.let { GlucoseStats.summarise(readings, it.targets) },
                doses = GlucoseStats.summariseDoses(doses),
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ProgressUi())

    fun setDays(value: Int) {
        days.value = value
    }
}

package io.github.wkoller.glucosetracker.ui.progress

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.wkoller.glucosetracker.AppContainer
import io.github.wkoller.glucosetracker.ai.ModelState
import io.github.wkoller.glucosetracker.domain.progress.ProgressModel
import io.github.wkoller.glucosetracker.domain.progress.ProgressModelBuilder
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

/** [model] is null until there is a plan. [canAsk] is true when the assistant is downloaded and switched on. */
data class ProgressUi(
    val loading: Boolean = true,
    val days: Int = 14,
    val model: ProgressModel? = null,
    val canAsk: Boolean = false,
)

@OptIn(ExperimentalCoroutinesApi::class)
class ProgressViewModel(container: AppContainer) : ViewModel() {
    private val repository = container.repository
    private val today = LocalDate.now(container.clock)
    private val days = MutableStateFlow(14)

    private val model = days.flatMapLatest { span ->
        val from = today.minusDays(span - 1L)
        combine(repository.activePlan, repository.readings(from, today), repository.doses(from, today), repository.measurements) { plan, readings, doses, measurements ->
            span to plan?.let { ProgressModelBuilder.build(it, from, today, readings, doses, measurements) }
        }
    }

    private val canAsk = combine(container.models.state, container.settingsStore.settings) { state, settings ->
        settings.assistantEnabled && state == ModelState.Ready
    }

    val ui: StateFlow<ProgressUi> = combine(model, canAsk) { (span, model), canAsk ->
        ProgressUi(loading = false, days = span, model = model, canAsk = canAsk)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ProgressUi())

    init {
        viewModelScope.launch { container.models.refresh() }
    }

    fun setDays(value: Int) {
        days.value = value
    }
}

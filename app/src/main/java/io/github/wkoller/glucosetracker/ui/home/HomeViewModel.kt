package io.github.wkoller.glucosetracker.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.wkoller.glucosetracker.AppContainer
import io.github.wkoller.glucosetracker.domain.model.Plan
import io.github.wkoller.glucosetracker.domain.progress.LowPattern
import io.github.wkoller.glucosetracker.domain.progress.LowPatterns
import io.github.wkoller.glucosetracker.domain.progress.Progress
import io.github.wkoller.glucosetracker.domain.progress.ProgressCalculator
import io.github.wkoller.glucosetracker.domain.schedule.NextStep
import io.github.wkoller.glucosetracker.domain.schedule.NextStepEngine
import io.github.wkoller.glucosetracker.domain.schedule.SlotProgress
import io.github.wkoller.glucosetracker.domain.schedule.SlotResolver
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime

data class HomeUi(
    val loading: Boolean = true,
    val plan: Plan? = null,
    val now: LocalDateTime = LocalDateTime.now(),
    val today: LocalDate = LocalDate.now(),
    val nextStep: NextStep? = null,
    val slots: List<SlotProgress> = emptyList(),
    val weight: Progress? = null,
    val firstName: String = "",
    /** Lows that keep happening and have not been acknowledged yet. */
    val lowPattern: LowPattern? = null,
)

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModel(private val container: AppContainer) : ViewModel() {
    private val repository = container.repository

    // The next step depends on the time of day, so the screen refreshes every half minute.
    private val minutes = flow {
        while (true) {
            emit(repository.now().withSecond(0).withNano(0))
            delay(30_000)
        }
    }.distinctUntilChanged()

    private val person = container.settingsStore.settings.map { it.firstName to it.lowNoticeAckAt }.distinctUntilChanged()

    val ui: StateFlow<HomeUi> = combine(repository.activePlan, minutes, person) { plan, now, person -> Triple(plan, now, person) }
        .flatMapLatest { (plan, now, person) ->
            val (name, lowsSeenAt) = person
            if (plan == null) {
                flowOf(HomeUi(loading = false, now = now, firstName = name))
            } else {
                val today = SlotResolver.logicalDate(plan, now)
                val week = repository.readings(today.minusDays(LowPatterns.DAYS - 1L), today)
                combine(repository.day(today), repository.measurements, week) { day, measurements, readings ->
                    HomeUi(
                        loading = false,
                        plan = plan,
                        now = now,
                        today = today,
                        nextStep = NextStepEngine.next(plan, now, day),
                        slots = NextStepEngine.progress(plan, now, day),
                        weight = ProgressCalculator.weight(plan.goals, measurements).takeIf { it.current != null },
                        firstName = name,
                        lowPattern = LowPatterns.recent(readings, plan.targets, today, lowsSeenAt.takeIf { it > 0 }?.let(Instant::ofEpochMilli)),
                    )
                }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUi())

    /** Hides the repeated-lows notice until there is a newer low. */
    fun acknowledgeLows() {
        val latest = ui.value.lowPattern?.latest ?: return
        viewModelScope.launch { container.settingsStore.update { it.copy(lowNoticeAckAt = latest.toEpochMilli()) } }
    }
}

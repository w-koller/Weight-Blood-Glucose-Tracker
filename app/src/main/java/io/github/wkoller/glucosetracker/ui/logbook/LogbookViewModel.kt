package io.github.wkoller.glucosetracker.ui.logbook

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.wkoller.glucosetracker.AppContainer
import io.github.wkoller.glucosetracker.domain.model.BodyMeasurement
import io.github.wkoller.glucosetracker.domain.model.DoseRecord
import io.github.wkoller.glucosetracker.domain.model.GlucoseReading
import io.github.wkoller.glucosetracker.domain.model.Plan
import io.github.wkoller.glucosetracker.domain.records.DailyLog
import io.github.wkoller.glucosetracker.domain.records.DayCompletion
import io.github.wkoller.glucosetracker.domain.records.DayStatus
import io.github.wkoller.glucosetracker.domain.records.PlanTimeline
import io.github.wkoller.glucosetracker.domain.records.DayLog
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

data class CalendarUi(
    val month: YearMonth = YearMonth.now(),
    val statuses: Map<LocalDate, DayStatus> = emptyMap(),
    val today: LocalDate = LocalDate.now(),
    val canGoLater: Boolean = false,
)

data class LogbookUi(
    val loading: Boolean = true,
    val plan: Plan? = null,
    val days: List<DayLog> = emptyList(),
    val from: LocalDate = LocalDate.now(),
    val hasEarlier: Boolean = false,
)

@OptIn(ExperimentalCoroutinesApi::class)
class LogbookViewModel(container: AppContainer) : ViewModel() {
    private val repository = container.repository
    private val today = LocalDate.now(container.clock)
    private val from = MutableStateFlow(today.minusDays(PAGE_DAYS - 1))
    private val firstDate = MutableStateFlow<LocalDate?>(null)

    init {
        viewModelScope.launch { firstDate.value = repository.firstReadingDate() }
    }

    val ui: StateFlow<LogbookUi> = from.flatMapLatest { start ->
        combine(
            repository.activePlan,
            repository.readings(start, today),
            repository.doses(start, today),
            repository.measurements,
            firstDate,
        ) { plan, readings, doses, measurements, first ->
            LogbookUi(
                loading = false,
                plan = plan,
                days = DailyLog.build(readings, doses, measurements.filter { !it.date.isBefore(start) && !it.date.isAfter(today) }),
                from = start,
                hasEarlier = (first != null && first.isBefore(start)) || measurements.any { it.date.isBefore(start) },
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LogbookUi())

    fun showEarlier() {
        from.value = from.value.minusDays(PAGE_DAYS)
    }

    private val month = MutableStateFlow(YearMonth.from(today))

    /** Each day of the shown month, judged against the plan that was in force that day. */
    val calendar: StateFlow<CalendarUi> = month.flatMapLatest { shown ->
        combine(
            repository.readings(shown.atDay(1), shown.atEndOfMonth()),
            repository.doses(shown.atDay(1), shown.atEndOfMonth()),
            repository.planHistory,
        ) { readings, doses, plans ->
            val logicalToday = repository.logicalToday(plans.firstOrNull())
            val now = repository.now().toLocalTime()
            val zone = ZoneId.systemDefault()
            CalendarUi(
                month = shown,
                statuses = (1..shown.lengthOfMonth()).associate { day ->
                    val date = shown.atDay(day)
                    date to DayCompletion.status(PlanTimeline.planOn(date, plans, zone), date, readings, doses, logicalToday, now)
                },
                today = logicalToday,
                canGoLater = shown < YearMonth.from(logicalToday),
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CalendarUi(month = YearMonth.from(today), today = today))

    fun earlierMonth() {
        month.value = month.value.minusMonths(1)
    }

    fun laterMonth() {
        month.value = month.value.plusMonths(1)
    }

    companion object {
        const val PAGE_DAYS = 14L
    }
}

/** One day's records, with editing and deleting. Deleted records are kept in memory so they can be put back. */
@OptIn(ExperimentalCoroutinesApi::class)
class DayViewModel(container: AppContainer, handle: SavedStateHandle) : ViewModel() {
    private val repository = container.repository
    val date: LocalDate = LocalDate.parse(handle.get<String>("date"))

    val day: StateFlow<DayLog?> = combine(
        repository.readings(date, date),
        repository.doses(date, date),
        repository.measurements,
    ) { readings, doses, measurements ->
        DailyLog.build(readings, doses, measurements.filter { it.date == date }).firstOrNull()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val plan: StateFlow<Plan?> = repository.activePlan.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _lastDeleted = MutableStateFlow<Any?>(null)

    /** The record deleted last, which Undo puts back. It stays until the user leaves the day. */
    val lastDeleted: StateFlow<Any?> = _lastDeleted.asStateFlow()

    fun delete(record: Any) = viewModelScope.launch {
        when (record) {
            is GlucoseReading -> repository.deleteReading(record)
            is DoseRecord -> repository.deleteDose(record)
            is BodyMeasurement -> repository.deleteMeasurement(record)
        }
        _lastDeleted.value = record
    }

    fun undoDelete() = viewModelScope.launch {
        when (val record = _lastDeleted.value) {
            is GlucoseReading -> repository.addReading(record)
            is DoseRecord -> repository.addDose(record)
            is BodyMeasurement -> repository.addMeasurement(record)
        }
        _lastDeleted.value = null
    }
}

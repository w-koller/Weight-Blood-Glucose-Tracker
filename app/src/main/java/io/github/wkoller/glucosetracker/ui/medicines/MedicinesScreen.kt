package io.github.wkoller.glucosetracker.ui.medicines

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import io.github.wkoller.glucosetracker.AppContainer
import io.github.wkoller.glucosetracker.R
import io.github.wkoller.glucosetracker.domain.model.DoseKind
import io.github.wkoller.glucosetracker.domain.model.DoseRecord
import io.github.wkoller.glucosetracker.domain.model.DoseStatus
import io.github.wkoller.glucosetracker.domain.model.Plan
import io.github.wkoller.glucosetracker.domain.model.Slot
import io.github.wkoller.glucosetracker.domain.records.Adherence
import io.github.wkoller.glucosetracker.domain.records.AdherenceCalculator
import io.github.wkoller.glucosetracker.ui.UiFormat
import io.github.wkoller.glucosetracker.ui.appViewModel
import io.github.wkoller.glucosetracker.ui.components.BigButton
import io.github.wkoller.glucosetracker.ui.components.ButtonStyle
import io.github.wkoller.glucosetracker.ui.components.FactRow
import io.github.wkoller.glucosetracker.ui.components.ScreenScaffold
import io.github.wkoller.glucosetracker.ui.components.SectionCard
import io.github.wkoller.glucosetracker.ui.components.nameRes
import io.github.wkoller.glucosetracker.ui.components.phraseRes
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate

data class MedicinesUi(
    val plan: Plan? = null,
    val today: List<DoseRecord> = emptyList(),
    val week: List<Adherence> = emptyList(),
    val month: List<Adherence> = emptyList(),
)

class MedicinesViewModel(container: AppContainer) : ViewModel() {
    private val repository = container.repository
    private val todayDate = LocalDate.now(container.clock)

    val ui: StateFlow<MedicinesUi> = combine(
        repository.activePlan,
        repository.doses(todayDate.minusDays(29), todayDate.plusDays(1)),
    ) { plan, doses ->
        if (plan == null) return@combine MedicinesUi()
        val today = repository.logicalToday(plan)
        val now = repository.now()
        MedicinesUi(
            plan = plan,
            today = doses.filter { it.date == today && it.kind == DoseKind.FIXED },
            week = AdherenceCalculator.calculate(plan, today.minusDays(6), now, today, doses),
            month = AdherenceCalculator.calculate(plan, today.minusDays(29), now, today, doses),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MedicinesUi())
}

@Composable
fun MedicinesScreen(onBack: () -> Unit, onRecord: (Slot) -> Unit) {
    val vm = appViewModel { container, _ -> MedicinesViewModel(container) }
    val ui by vm.ui.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val plan = ui.plan
    ScreenScaffold(title = stringResource(R.string.medicines_title), onBack = onBack) {
        if (plan == null || plan.medicines.isEmpty()) {
            Text(stringResource(R.string.medicines_none), style = MaterialTheme.typography.titleMedium)
            return@ScreenScaffold
        }
        plan.activeSlots.filter { plan.medicinesAt(it.slot).isNotEmpty() }.forEach { schedule ->
            val slot = schedule.slot
            SectionCard(stringResource(R.string.home_slot_line, stringResource(slot.nameRes()), UiFormat.time(context, schedule.time))) {
                var anyMissing = false
                plan.medicinesAt(slot).forEach { medicine ->
                    val amount = UiFormat.dose(context, medicine.doses.getValue(slot), medicine.kind, medicine.unit)
                    val record = ui.today.lastOrNull { it.slot == slot && it.medicineKey == medicine.key }
                    val status = when (record?.status) {
                        DoseStatus.GIVEN -> stringResource(R.string.medicines_status_given, UiFormat.dose(context, record.givenAmount ?: 0.0, medicine.kind, medicine.unit))
                        DoseStatus.SKIPPED -> stringResource(R.string.dose_not_given)
                        DoseStatus.NOT_NEEDED -> stringResource(R.string.dose_not_needed)
                        null -> stringResource(R.string.medicines_status_not_recorded).also { anyMissing = true }
                    }
                    FactRow(stringResource(R.string.medicines_line, medicine.displayName, amount), status)
                }
                if (anyMissing) {
                    BigButton(
                        stringResource(R.string.medicines_record, stringResource(slot.phraseRes())),
                        onClick = { onRecord(slot) },
                        icon = R.drawable.ic_medication,
                        style = ButtonStyle.SECONDARY,
                    )
                }
            }
        }
        SectionCard(stringResource(R.string.medicines_week_title)) {
            ui.week.forEach { FactRow(it.medicineName, pluralStringResource(R.plurals.medicines_adherence, it.scheduled, it.given, it.scheduled)) }
        }
        SectionCard(stringResource(R.string.medicines_month_title)) {
            ui.month.forEach { FactRow(it.medicineName, pluralStringResource(R.plurals.medicines_adherence, it.scheduled, it.given, it.scheduled)) }
        }
    }
}

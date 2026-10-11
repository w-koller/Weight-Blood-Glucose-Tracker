package io.github.wkoller.glucosetracker.ui.plan

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.wkoller.glucosetracker.R
import io.github.wkoller.glucosetracker.data.prefs.PinHasher
import io.github.wkoller.glucosetracker.data.prefs.Settings
import io.github.wkoller.glucosetracker.domain.format.Measures
import io.github.wkoller.glucosetracker.domain.model.Plan
import io.github.wkoller.glucosetracker.ui.UiFormat
import io.github.wkoller.glucosetracker.ui.appContainer
import io.github.wkoller.glucosetracker.ui.components.BigButton
import io.github.wkoller.glucosetracker.ui.components.ButtonStyle
import io.github.wkoller.glucosetracker.ui.components.ConfirmDialog
import io.github.wkoller.glucosetracker.ui.components.FactRow
import io.github.wkoller.glucosetracker.ui.components.NoticeCard
import io.github.wkoller.glucosetracker.ui.components.NoticeKind
import io.github.wkoller.glucosetracker.ui.components.NumberPad
import io.github.wkoller.glucosetracker.ui.components.ScaleTable
import io.github.wkoller.glucosetracker.ui.components.ScreenScaffold
import io.github.wkoller.glucosetracker.ui.components.SectionCard
import io.github.wkoller.glucosetracker.ui.components.StepScaffold
import io.github.wkoller.glucosetracker.ui.components.ValueDisplay
import io.github.wkoller.glucosetracker.ui.components.labelRes
import io.github.wkoller.glucosetracker.ui.components.nameRes
import kotlinx.coroutines.launch

/** The current plan, read-only, with the ways to change it. */
@Composable
fun PlanScreen(onBack: () -> Unit, onEdit: () -> Unit, onNew: () -> Unit, onHistory: () -> Unit, onPin: () -> Unit) {
    val container = appContainer()
    val plan by container.repository.activePlan.collectAsStateWithLifecycle(null)
    val settings by container.settingsStore.settings.collectAsStateWithLifecycle(Settings())
    ScreenScaffold(title = stringResource(R.string.settings_plan), onBack = onBack) {
        Text(stringResource(R.string.plan_body), style = MaterialTheme.typography.bodyLarge)
        BigButton(stringResource(R.string.plan_change), onClick = onEdit, icon = R.drawable.ic_edit, helper = stringResource(R.string.plan_change_helper))
        BigButton(stringResource(R.string.plan_new), onClick = onNew, icon = R.drawable.ic_add, helper = stringResource(R.string.plan_new_helper), style = ButtonStyle.SECONDARY)
        BigButton(stringResource(R.string.plan_history), onClick = onHistory, icon = R.drawable.ic_history, style = ButtonStyle.SECONDARY)
        BigButton(
            stringResource(if (settings.hasPin) R.string.pin_change_or_remove else R.string.pin_set),
            onClick = onPin,
            icon = R.drawable.ic_lock,
            style = ButtonStyle.SECONDARY,
        )
        plan?.let { PlanSummary(it) }
    }
}

@Composable
fun ColumnScope.PlanSummary(plan: Plan) {
    val context = LocalContext.current
    val notSet = stringResource(R.string.not_set)
    SectionCard(stringResource(R.string.setup_insulin_title)) {
        FactRow(stringResource(R.string.setup_insulin_name), plan.correctionInsulin)
        FactRow(stringResource(R.string.setup_prescriber), plan.prescriber.ifBlank { notSet })
        FactRow(stringResource(R.string.setup_order_date), plan.orderDate?.let(UiFormat::dayLongYear) ?: notSet)
        FactRow(stringResource(R.string.plan_in_use_since), UiFormat.dayLongYear(plan.effectiveFrom.atZone(java.time.ZoneId.systemDefault()).toLocalDate()))
    }
    ScaleTable(plan.scale, plan.correctionInsulin, highlightIndex = null)
    SectionCard(stringResource(R.string.setup_times_title)) {
        plan.slots.forEach { slot ->
            val parts = listOf(
                UiFormat.time(context, slot.time),
                stringResource(if (slot.checkBgl) R.string.review_check_on else R.string.review_check_off),
                stringResource(if (slot.correctionPrescribed) R.string.review_prescribed_on else R.string.review_prescribed_off, plan.correctionInsulin),
                stringResource(if (slot.reminderOn) R.string.review_reminder_on else R.string.review_reminder_off),
            )
            FactRow(stringResource(slot.slot.nameRes()), parts.joinToString("\n"))
        }
    }
    SectionCard(stringResource(R.string.setup_medicines_title)) {
        if (plan.medicines.isEmpty()) Text(stringResource(R.string.setup_medicines_none), style = MaterialTheme.typography.bodyLarge)
        plan.medicines.forEach { medicine ->
            val lines = medicine.doses.map { (slot, amount) ->
                stringResource(R.string.review_medicine_line, stringResource(slot.nameRes()), UiFormat.dose(context, amount, medicine.kind, medicine.unit))
            }
            FactRow(medicine.displayName, lines.joinToString("\n"))
        }
    }
    SectionCard(stringResource(R.string.setup_goals_title)) {
        val goals = plan.goals
        FactRow(stringResource(R.string.setup_start_date), UiFormat.dayLongYear(goals.startDate))
        FactRow(stringResource(R.string.setup_start_weight), goals.startWeightGrams?.let { stringResource(R.string.kg_amount, Measures.formatKg(it)) } ?: notSet)
        FactRow(stringResource(R.string.setup_goal_weight), goals.goalWeightGrams?.let { stringResource(R.string.kg_amount, Measures.formatKg(it)) } ?: notSet)
        FactRow(stringResource(R.string.setup_start_waist), goals.startWaistMm?.let { stringResource(R.string.cm_amount, Measures.formatCm(it)) } ?: notSet)
        FactRow(stringResource(R.string.setup_goal_waist), goals.goalWaistMm?.let { stringResource(R.string.cm_amount, Measures.formatCm(it)) } ?: notSet)
        FactRow(stringResource(R.string.setup_target_date), goals.targetDate?.let(UiFormat::dayLongYear) ?: notSet)
        FactRow(stringResource(R.string.setup_weigh_every), stringResource(goals.weighEvery.labelRes()))
        FactRow(stringResource(R.string.setup_waist_every), stringResource(goals.waistEvery.labelRes()))
    }
    SectionCard(stringResource(R.string.setup_targets_title)) {
        val mmol = stringResource(R.string.unit_mmol)
        FactRow(
            stringResource(R.string.review_target_range),
            stringResource(R.string.review_range_value, Measures.formatTenths(plan.targets.lowTenths), Measures.formatTenths(plan.targets.highTenths), mmol),
        )
        FactRow(stringResource(R.string.setup_hypo), "${Measures.formatTenths(plan.targets.hypoTenths)} $mmol")
    }
}

/** Every saved version, newest first, with what changed from the version before it. */
@Composable
fun PlanHistoryScreen(onBack: () -> Unit) {
    val container = appContainer()
    val context = LocalContext.current
    val plans by container.repository.planHistory.collectAsStateWithLifecycle(emptyList())
    ScreenScaffold(title = stringResource(R.string.plan_history), onBack = onBack) {
        Text(stringResource(R.string.plan_history_body), style = MaterialTheme.typography.bodyLarge)
        plans.forEachIndexed { index, plan ->
            val previous = plans.getOrNull(index + 1)?.takeIf { it.cycleId == plan.cycleId }
            val from = UiFormat.dayLongYear(plan.effectiveFrom.atZone(java.time.ZoneId.systemDefault()).toLocalDate())
            SectionCard(stringResource(if (index == 0) R.string.plan_history_current else R.string.plan_history_version, from)) {
                if (previous == null) {
                    Text(stringResource(R.string.plan_history_started), style = MaterialTheme.typography.bodyLarge)
                } else {
                    PlanChanges.describe(context, previous, plan).ifEmpty { listOf(stringResource(R.string.change_none_body)) }
                        .forEach { Text(it, style = MaterialTheme.typography.bodyLarge) }
                }
            }
        }
    }
}

/**
 * Stands between the user and a plan change. With a PIN, the PIN is asked for; without one, the user
 * confirms that their doctor has changed the prescription.
 */
@Composable
fun UnlockScreen(onBack: () -> Unit, onUnlocked: () -> Unit) {
    val container = appContainer()
    val settings by container.settingsStore.settings.collectAsStateWithLifecycle(null)
    val scope = rememberCoroutineScope()
    var pin by remember { mutableStateOf("") }
    var wrong by remember { mutableStateOf(false) }
    var resetting by remember { mutableStateOf(false) }
    val current = settings ?: return

    if (!current.hasPin) {
        StepScaffold(
            step = 0,
            stepCount = 0,
            title = stringResource(R.string.unlock_title),
            onBack = onBack,
            actions = {
                BigButton(stringResource(R.string.continue_label), onClick = onUnlocked)
                BigButton(stringResource(R.string.cancel), onClick = onBack, style = ButtonStyle.SECONDARY)
            },
        ) {
            Text(stringResource(R.string.unlock_no_pin_body), style = MaterialTheme.typography.bodyLarge)
        }
        return
    }

    StepScaffold(
        step = 0,
        stepCount = 0,
        title = stringResource(R.string.unlock_pin_title),
        onBack = onBack,
        actions = {
            BigButton(stringResource(R.string.unlock_button), onClick = {
                if (PinHasher.matches(pin, current.pinSalt!!, current.pinHash!!)) {
                    onUnlocked()
                } else {
                    wrong = true
                    pin = ""
                }
            })
            BigButton(stringResource(R.string.pin_forgot), onClick = { resetting = true }, style = ButtonStyle.SECONDARY)
        },
    ) {
        Text(stringResource(R.string.unlock_pin_body), style = MaterialTheme.typography.bodyLarge)
        if (wrong) NoticeCard(NoticeKind.DANGER, stringResource(R.string.pin_wrong_title), stringResource(R.string.pin_wrong))
        ValueDisplay("●".repeat(pin.length), "")
        NumberPad(pin, { pin = it.take(4); wrong = false }, maxDecimals = 0, maxIntegerDigits = 4)
    }
    if (resetting) {
        ConfirmDialog(
            title = stringResource(R.string.pin_reset_title),
            messages = listOf(stringResource(R.string.pin_reset_body)),
            confirmText = stringResource(R.string.pin_reset_confirm),
            dismissText = stringResource(R.string.cancel),
            onConfirm = {
                resetting = false
                scope.launch { container.settingsStore.update { it.copy(pinHash = null, pinSalt = null) } }
            },
            onDismiss = { resetting = false },
        )
    }
}

/** Sets, changes or removes the plan PIN. Changing or removing an existing PIN asks for it first. */
@Composable
fun PinSettingsScreen(onBack: () -> Unit) {
    val container = appContainer()
    val settings by container.settingsStore.settings.collectAsStateWithLifecycle(null)
    val scope = rememberCoroutineScope()
    var unlocked by remember { mutableStateOf(false) }
    var pin by remember { mutableStateOf("") }
    var again by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<Int?>(null) }
    var saved by remember { mutableStateOf<Int?>(null) }
    val current = settings ?: return

    if (current.hasPin && !unlocked) {
        UnlockScreen(onBack = onBack, onUnlocked = { unlocked = true })
        return
    }
    ScreenScaffold(title = stringResource(if (current.hasPin) R.string.pin_change_or_remove else R.string.pin_set), onBack = onBack) {
        Text(stringResource(R.string.setup_pin_body), style = MaterialTheme.typography.bodyLarge)
        saved?.let { NoticeCard(NoticeKind.INFO, stringResource(it), null) }
        error?.let { NoticeCard(NoticeKind.DANGER, stringResource(R.string.setup_errors_title), stringResource(it)) }
        PinField(stringResource(R.string.setup_pin_choose), pin) { pin = it.filter(Char::isDigit).take(4); error = null }
        PinField(stringResource(R.string.setup_pin_again), again) { again = it.filter(Char::isDigit).take(4); error = null }
        BigButton(stringResource(R.string.setup_pin_set), onClick = {
            when {
                !PinHasher.isValidPin(pin) -> error = R.string.pin_error_invalid
                pin != again -> error = R.string.pin_error_mismatch
                else -> {
                    val salt = PinHasher.newSalt()
                    val hash = PinHasher.hash(pin, salt)
                    scope.launch { container.settingsStore.update { it.copy(pinSalt = salt, pinHash = hash) } }
                    pin = ""
                    again = ""
                    saved = R.string.pin_saved
                }
            }
        })
        if (current.hasPin) {
            BigButton(stringResource(R.string.pin_remove), onClick = {
                scope.launch { container.settingsStore.update { it.copy(pinSalt = null, pinHash = null) } }
                saved = R.string.pin_removed
            }, style = ButtonStyle.SECONDARY)
        }
    }
}

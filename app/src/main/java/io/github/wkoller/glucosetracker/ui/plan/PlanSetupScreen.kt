package io.github.wkoller.glucosetracker.ui.plan

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.wkoller.glucosetracker.R
import io.github.wkoller.glucosetracker.ui.appContainer
import io.github.wkoller.glucosetracker.ui.appViewModel
import io.github.wkoller.glucosetracker.ui.components.BigButton
import io.github.wkoller.glucosetracker.ui.components.ButtonStyle
import io.github.wkoller.glucosetracker.ui.components.ConfirmDialog
import io.github.wkoller.glucosetracker.ui.components.StepScaffold

@Composable
fun PlanSetupScreen(onClose: () -> Unit, onFinished: () -> Unit) {
    val vm = appViewModel { container, handle -> PlanSetupViewModel(container, handle) }
    val ui by vm.ui.collectAsStateWithLifecycle()

    LaunchedEffect(ui.finished) { if (ui.finished) onFinished() }
    BackHandler { if (!vm.back()) onClose() }

    if (ui.loading) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        return
    }

    val onBack: (() -> Unit)? = { if (!vm.back()) onClose() }
    val medicine = ui.editingMedicine
    if (medicine != null) {
        MedicineEditor(ui, medicine, vm)
        return
    }

    val titleRes = when (ui.step) {
        SetupStep.WELCOME -> R.string.setup_welcome_title
        SetupStep.ABOUT_YOU -> R.string.setup_about_title
        SetupStep.ASSISTANT -> R.string.setup_assistant_title
        SetupStep.INSULIN -> R.string.setup_insulin_title
        SetupStep.SCALE -> R.string.setup_scale_title
        SetupStep.SCALE_CHECK -> R.string.setup_scale_check_title
        SetupStep.TIMES -> R.string.setup_times_title
        SetupStep.MEDICINES -> R.string.setup_medicines_title
        SetupStep.GOALS -> R.string.setup_goals_title
        SetupStep.TARGETS -> R.string.setup_targets_title
        SetupStep.PIN -> R.string.setup_pin_title
        SetupStep.PERMISSIONS -> R.string.setup_permissions_title
        SetupStep.REVIEW -> if (ui.mode == SetupMode.EDIT) R.string.setup_review_title_edit else R.string.setup_review_title
    }

    StepScaffold(
        step = ui.stepNumber,
        stepCount = if (ui.mode == SetupMode.EDIT) 0 else ui.steps.size,
        title = stringResource(titleRes),
        onBack = if (ui.step == SetupStep.WELCOME) null else onBack,
        actions = {
            when (ui.step) {
                SetupStep.PIN -> {
                    BigButton(stringResource(R.string.setup_pin_set), onClick = vm::choosePin)
                    BigButton(stringResource(R.string.setup_pin_skip), onClick = vm::skipPin, style = ButtonStyle.SECONDARY)
                }
                SetupStep.ASSISTANT -> {
                    // Without room for the model, downloading would only fail, so "Not now" is the only choice.
                    val container = appContainer()
                    val hasRoom = remember { container.models.hasRoomForModel() }
                    if (hasRoom) {
                        BigButton(stringResource(R.string.setup_assistant_yes), onClick = { vm.chooseAssistant(true) }, icon = R.drawable.ic_download)
                    }
                    BigButton(
                        stringResource(R.string.setup_assistant_no),
                        onClick = { vm.chooseAssistant(false) },
                        style = if (hasRoom) ButtonStyle.SECONDARY else ButtonStyle.PRIMARY,
                    )
                }
                SetupStep.MEDICINES -> {
                    BigButton(stringResource(R.string.setup_medicine_add), onClick = { vm.startMedicine(null) }, style = ButtonStyle.SECONDARY)
                    BigButton(stringResource(if (ui.returnToReview) R.string.done else R.string.next), onClick = vm::next)
                }
                SetupStep.REVIEW -> BigButton(
                    stringResource(
                        when (ui.mode) {
                            SetupMode.FIRST -> R.string.setup_start_plan
                            SetupMode.NEW -> R.string.setup_start_new_plan
                            SetupMode.EDIT -> R.string.save_changes
                        },
                    ),
                    onClick = vm::finish,
                    enabled = !ui.saving,
                )
                else -> BigButton(
                    stringResource(if (ui.returnToReview && ui.step != SetupStep.SCALE) R.string.done else R.string.next),
                    onClick = vm::next,
                )
            }
        },
    ) {
        when (ui.step) {
            SetupStep.WELCOME -> WelcomeStep()
            SetupStep.ABOUT_YOU -> AboutYouStep(ui, vm)
            SetupStep.ASSISTANT -> AssistantStep(ui)
            SetupStep.INSULIN -> InsulinStep(ui, vm)
            SetupStep.SCALE -> ScaleStep(ui, vm)
            SetupStep.SCALE_CHECK -> ScaleCheckStep(ui, vm)
            SetupStep.TIMES -> TimesStep(ui, vm)
            SetupStep.MEDICINES -> MedicinesStep(ui, vm)
            SetupStep.GOALS -> GoalsStep(ui, vm)
            SetupStep.TARGETS -> TargetsStep(ui, vm)
            SetupStep.PIN -> PinStep(ui, vm)
            SetupStep.PERMISSIONS -> PermissionsStep()
            SetupStep.REVIEW -> ReviewStep(ui, vm)
        }
    }

    ui.changes?.let { changes ->
        if (changes.isEmpty()) {
            ConfirmDialog(
                title = stringResource(R.string.change_none_title),
                messages = listOf(stringResource(R.string.change_none_body)),
                confirmText = stringResource(R.string.close),
                dismissText = stringResource(R.string.keep_editing),
                onConfirm = { vm.dismissChanges(); onClose() },
                onDismiss = vm::dismissChanges,
            )
        } else {
            ConfirmDialog(
                title = stringResource(R.string.change_confirm_title),
                messages = changes + stringResource(R.string.change_confirm_footer),
                confirmText = stringResource(R.string.save_changes),
                dismissText = stringResource(R.string.go_back),
                onConfirm = vm::save,
                onDismiss = vm::dismissChanges,
            )
        }
    }
}

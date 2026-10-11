package io.github.wkoller.glucosetracker.ui.nav

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import io.github.wkoller.glucosetracker.domain.model.Slot
import io.github.wkoller.glucosetracker.ui.assistant.AssistantScreen
import io.github.wkoller.glucosetracker.ui.assistant.AssistantSettingsScreen
import io.github.wkoller.glucosetracker.ui.checkin.CheckInScreen
import io.github.wkoller.glucosetracker.ui.export.ExportScreen
import io.github.wkoller.glucosetracker.ui.home.HomeActions
import io.github.wkoller.glucosetracker.ui.home.HomeScreen
import io.github.wkoller.glucosetracker.ui.logbook.DayScreen
import io.github.wkoller.glucosetracker.ui.logbook.LogbookScreen
import io.github.wkoller.glucosetracker.ui.measure.MeasureScreen
import io.github.wkoller.glucosetracker.ui.medicines.MedicinesScreen
import io.github.wkoller.glucosetracker.ui.plan.PinSettingsScreen
import io.github.wkoller.glucosetracker.ui.plan.PlanHistoryScreen
import io.github.wkoller.glucosetracker.ui.plan.PlanScreen
import io.github.wkoller.glucosetracker.ui.plan.PlanSetupScreen
import io.github.wkoller.glucosetracker.ui.plan.SetupMode
import io.github.wkoller.glucosetracker.ui.plan.UnlockScreen
import io.github.wkoller.glucosetracker.ui.progress.ProgressScreen
import io.github.wkoller.glucosetracker.ui.reminders.RemindersScreen
import io.github.wkoller.glucosetracker.ui.settings.AboutScreen
import io.github.wkoller.glucosetracker.ui.settings.DisplaySettingsScreen
import io.github.wkoller.glucosetracker.ui.settings.SettingsActions
import io.github.wkoller.glucosetracker.ui.settings.SettingsScreen

object Routes {
    const val HOME = "home"
    const val SETUP = "setup?mode={mode}"
    const val CHECKIN = "checkin?slot={slot}&medsOnly={medsOnly}"
    const val MEASURE = "measure?what={what}"
    const val LOGBOOK = "logbook"
    const val DAY = "day/{date}"
    const val PROGRESS = "progress"
    const val MEDICINES = "medicines"
    const val ASSISTANT = "assistant"
    const val SETTINGS = "settings"
    const val DISPLAY = "settings/display"
    const val PLAN = "settings/plan"
    const val PLAN_HISTORY = "settings/plan/history"
    const val PIN = "settings/pin"
    const val UNLOCK = "unlock?next={next}"
    const val REMINDERS = "settings/reminders"
    const val ASSISTANT_SETTINGS = "settings/assistant"
    const val EXPORT = "settings/export"
    const val ABOUT = "settings/about"

    fun setup(mode: SetupMode) = "setup?mode=${mode.name}"
    fun checkIn(slot: Slot?, medicinesOnly: Boolean = false) = "checkin?slot=${slot?.name ?: ""}&medsOnly=$medicinesOnly"
    fun measure(what: String) = "measure?what=$what"
    fun day(date: String) = "day/$date"
    fun unlock(next: SetupMode) = "unlock?next=${next.name}"
}

@Composable
fun AppNavHost(startWithSetup: Boolean, openSlot: Slot?, onSlotOpened: () -> Unit, navController: NavHostController = rememberNavController()) {
    LaunchedEffect(openSlot) {
        if (openSlot != null && !startWithSetup) {
            navController.navigate(Routes.checkIn(openSlot)) { launchSingleTop = true }
            onSlotOpened()
        }
    }
    val goHome: () -> Unit = {
        navController.navigate(Routes.HOME) { popUpTo(Routes.HOME) { inclusive = false }; launchSingleTop = true }
    }
    val back: () -> Unit = { if (!navController.popBackStack()) goHome() }

    NavHost(navController, startDestination = if (startWithSetup) Routes.setup(SetupMode.FIRST) else Routes.HOME) {
        composable(Routes.HOME) {
            HomeScreen(
                HomeActions(
                    checkIn = { slot, medicinesOnly -> navController.navigate(Routes.checkIn(slot, medicinesOnly)) },
                    measure = { navController.navigate(Routes.measure(it)) },
                    medicines = { navController.navigate(Routes.MEDICINES) },
                    logbook = { navController.navigate(Routes.LOGBOOK) },
                    progress = { navController.navigate(Routes.PROGRESS) },
                    assistant = { navController.navigate(Routes.ASSISTANT) },
                    settings = { navController.navigate(Routes.SETTINGS) },
                ),
            )
        }
        composable(Routes.SETUP, arguments = listOf(navArgument("mode") { type = NavType.StringType; defaultValue = SetupMode.FIRST.name })) { entry ->
            val first = entry.arguments?.getString("mode") == SetupMode.FIRST.name
            PlanSetupScreen(
                onClose = back,
                onFinished = {
                    if (first) {
                        navController.navigate(Routes.HOME) { popUpTo(0) { inclusive = true } }
                    } else {
                        navController.popBackStack(Routes.PLAN, inclusive = false)
                    }
                },
            )
        }
        composable(
            Routes.CHECKIN,
            arguments = listOf(
                navArgument("slot") { type = NavType.StringType; nullable = true; defaultValue = null },
                navArgument("medsOnly") { type = NavType.BoolType; defaultValue = false },
            ),
        ) {
            CheckInScreen(
                onClose = goHome,
                onMeasure = { navController.navigate(Routes.measure(it)) { popUpTo(Routes.HOME) } },
                onCheckIn = { navController.navigate(Routes.checkIn(it)) { popUpTo(Routes.HOME) } },
            )
        }
        composable(Routes.MEASURE, arguments = listOf(navArgument("what") { type = NavType.StringType; defaultValue = "weight" })) {
            MeasureScreen(
                onClose = goHome,
                onCheckIn = { navController.navigate(Routes.checkIn(it)) { popUpTo(Routes.HOME) } },
            )
        }
        composable(Routes.LOGBOOK) { LogbookScreen(onBack = back, onDay = { navController.navigate(Routes.day(it.toString())) }) }
        composable(Routes.DAY, arguments = listOf(navArgument("date") { type = NavType.StringType })) { DayScreen(onBack = back) }
        composable(Routes.PROGRESS) { ProgressScreen(onBack = back) }
        composable(Routes.MEDICINES) {
            MedicinesScreen(onBack = back, onRecord = { navController.navigate(Routes.checkIn(it, medicinesOnly = true)) })
        }
        composable(Routes.ASSISTANT) {
            AssistantScreen(
                onBack = back,
                onSetUp = { navController.navigate(Routes.ASSISTANT_SETTINGS) },
                onCheckIn = { navController.navigate(Routes.checkIn(null)) },
            )
        }
        composable(Routes.SETTINGS) {
            SettingsScreen(
                onBack = back,
                actions = SettingsActions(
                    display = { navController.navigate(Routes.DISPLAY) },
                    plan = { navController.navigate(Routes.PLAN) },
                    reminders = { navController.navigate(Routes.REMINDERS) },
                    assistant = { navController.navigate(Routes.ASSISTANT_SETTINGS) },
                    export = { navController.navigate(Routes.EXPORT) },
                    about = { navController.navigate(Routes.ABOUT) },
                ),
            )
        }
        composable(Routes.DISPLAY) { DisplaySettingsScreen(onBack = back) }
        composable(Routes.PLAN) {
            PlanScreen(
                onBack = back,
                onEdit = { navController.navigate(Routes.unlock(SetupMode.EDIT)) },
                onNew = { navController.navigate(Routes.unlock(SetupMode.NEW)) },
                onHistory = { navController.navigate(Routes.PLAN_HISTORY) },
                onPin = { navController.navigate(Routes.PIN) },
            )
        }
        composable(Routes.UNLOCK, arguments = listOf(navArgument("next") { type = NavType.StringType; defaultValue = SetupMode.EDIT.name })) { entry ->
            val next = entry.arguments?.getString("next")?.let { SetupMode.valueOf(it) } ?: SetupMode.EDIT
            UnlockScreen(
                onBack = back,
                onUnlocked = { navController.navigate(Routes.setup(next)) { popUpTo(Routes.PLAN) } },
            )
        }
        composable(Routes.PLAN_HISTORY) { PlanHistoryScreen(onBack = back) }
        composable(Routes.PIN) { PinSettingsScreen(onBack = back) }
        composable(Routes.REMINDERS) { RemindersScreen(onBack = back) }
        composable(Routes.ASSISTANT_SETTINGS) { AssistantSettingsScreen(onBack = back) }
        composable(Routes.EXPORT) { ExportScreen(onBack = back) }
        composable(Routes.ABOUT) { AboutScreen(onBack = back) }
    }
}

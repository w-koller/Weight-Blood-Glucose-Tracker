package io.github.wkoller.glucosetracker.ui.logbook

import androidx.compose.runtime.Composable
import io.github.wkoller.glucosetracker.ui.components.ScreenScaffold

@Composable
fun LogbookScreen(onBack: () -> Unit, onDay: (java.time.LocalDate) -> Unit) {
    ScreenScaffold(title = "Placeholder", onBack = onBack) {}
}

@Composable
fun DayScreen(onBack: () -> Unit) {
    ScreenScaffold(title = "Placeholder", onBack = onBack) {}
}


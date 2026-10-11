package io.github.wkoller.glucosetracker.ui.assistant

import androidx.compose.runtime.Composable
import io.github.wkoller.glucosetracker.ui.components.ScreenScaffold

@Composable
fun AssistantScreen(onBack: () -> Unit, onSetUp: () -> Unit, onCheckIn: () -> Unit) {
    ScreenScaffold(title = "Placeholder", onBack = onBack) {}
}

@Composable
fun AssistantSettingsScreen(onBack: () -> Unit) {
    ScreenScaffold(title = "Placeholder", onBack = onBack) {}
}


package io.github.wkoller.glucosetracker.ui.medicines

import androidx.compose.runtime.Composable
import io.github.wkoller.glucosetracker.ui.components.ScreenScaffold

@Composable
fun MedicinesScreen(onBack: () -> Unit, onRecord: (io.github.wkoller.glucosetracker.domain.model.Slot) -> Unit) {
    ScreenScaffold(title = "Placeholder", onBack = onBack) {}
}


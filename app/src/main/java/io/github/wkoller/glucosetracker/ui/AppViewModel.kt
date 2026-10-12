package io.github.wkoller.glucosetracker.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.wkoller.glucosetracker.AppContainer
import io.github.wkoller.glucosetracker.TrackerApp

@Composable
fun appContainer(): AppContainer = (LocalContext.current.applicationContext as TrackerApp).container

/** Creates a screen's view model with access to the app's shared objects and the screen's arguments. */
@Composable
inline fun <reified VM : ViewModel> appViewModel(crossinline create: (AppContainer, SavedStateHandle) -> VM): VM {
    val container = appContainer()
    return viewModel(factory = viewModelFactory { initializer { create(container, createSavedStateHandle()) } })
}

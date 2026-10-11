package io.github.wkoller.glucosetracker.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.wkoller.glucosetracker.R
import io.github.wkoller.glucosetracker.ui.theme.LocalUiPrefs

/** A labelled Back button. Arrows on their own are easy to miss, so the word is always shown. */
@Composable
fun BackButton(onBack: () -> Unit) {
    TextButton(onClick = onBack, modifier = Modifier.heightIn(min = 56.dp)) {
        if (LocalUiPrefs.current.showIcons) {
            Icon(painterResource(R.drawable.ic_arrow_back), contentDescription = null, modifier = Modifier.size(28.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(stringResource(R.string.back), style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
fun ScreenTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.headlineMedium,
        modifier = modifier.semantics { heading() },
    )
}

/** A standard screen: Back, a heading, and scrolling content. */
@Composable
fun ScreenScaffold(
    title: String,
    onBack: (() -> Unit)?,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
    bottomBar: @Composable (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = { bottomBar?.let { BottomActions(it) } },
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (onBack != null) BackButton(onBack)
            ScreenTitle(title)
            content()
            Spacer(Modifier.heightIn(min = 24.dp))
        }
    }
}

/**
 * One step of a guided journey: "Step 2 of 4", a heading, the content, and the main actions fixed
 * at the bottom so they are always in the same place.
 */
@Composable
fun StepScaffold(
    step: Int,
    stepCount: Int,
    title: String,
    onBack: (() -> Unit)?,
    actions: @Composable ColumnScope.() -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    Scaffold(bottomBar = { BottomActions { Column(verticalArrangement = Arrangement.spacedBy(12.dp)) { actions() } } }) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                if (onBack != null) BackButton(onBack) else Spacer(Modifier.heightIn(min = 56.dp))
                Spacer(Modifier.weight(1f))
                if (stepCount > 1) {
                    Text(stringResource(R.string.step_of, step, stepCount), style = MaterialTheme.typography.titleSmall)
                }
            }
            if (stepCount > 1) {
                LinearProgressIndicator(progress = { step / stepCount.toFloat() }, modifier = Modifier.fillMaxWidth().heightIn(min = 8.dp))
            }
            ScreenTitle(title)
            content()
            Spacer(Modifier.heightIn(min = 16.dp))
        }
    }
}

@Composable
private fun BottomActions(content: @Composable () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.surface, tonalElevation = 3.dp) {
        Column {
            HorizontalDivider()
            Column(Modifier.navigationBarsPadding().padding(horizontal = 20.dp, vertical = 12.dp).fillMaxWidth()) { content() }
        }
    }
}

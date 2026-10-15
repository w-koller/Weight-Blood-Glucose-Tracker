package io.github.wkoller.glucosetracker.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import io.github.wkoller.glucosetracker.R
import io.github.wkoller.glucosetracker.ui.theme.LocalStatusColors
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
        bottomBar = { bottomBar?.let { BottomActions(content = it) } },
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
 *
 * [moreActions] sit under [actions] when there is room. On a short screen or with very large text,
 * where all the buttons together would cover more than about a third of the screen, they move to
 * the end of the content instead, so the content is not squeezed into a small strip.
 */
@Composable
fun StepScaffold(
    step: Int,
    stepCount: Int,
    title: String,
    onBack: (() -> Unit)?,
    actions: @Composable ColumnScope.() -> Unit,
    moreActions: (@Composable ColumnScope.() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    BoxWithConstraints {
        val limitPx = with(LocalDensity.current) { (maxHeight * PINNED_SHARE).roundToPx() }
        // Worked out again when the screen size or text size changes, so turning the phone can pin them again.
        var inlineMore by remember(maxHeight, LocalDensity.current.fontScale) { mutableStateOf(false) }
        Scaffold(
            bottomBar = {
                BottomActions(
                    Modifier.onSizeChanged { size -> if (moreActions != null && !inlineMore && size.height > limitPx) inlineMore = true },
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        actions()
                        if (!inlineMore) moreActions?.invoke(this)
                    }
                }
            },
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
                StepHeader(step, stepCount, title, onBack)
                content()
                if (inlineMore) moreActions?.invoke(this)
                Spacer(Modifier.heightIn(min = 16.dp))
            }
        }
    }
}

@Composable
private fun StepHeader(step: Int, stepCount: Int, title: String, onBack: (() -> Unit)?) {
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
}

/**
 * A step that asks for one number. Like a phone keyboard, the value, the keypad and the main button
 * stay fixed at the bottom, so the keys never need scrolling. Only the heading and anything in
 * [header] scroll. On a short, wide screen, such as a phone turned sideways, the keypad sits on the
 * right instead.
 */
@Composable
fun NumberEntryScaffold(
    title: String,
    onBack: (() -> Unit)?,
    value: String,
    unit: String,
    onValueChange: (String) -> Unit,
    action: @Composable () -> Unit,
    display: String = value,
    error: String? = null,
    maxDecimals: Int = 1,
    maxIntegerDigits: Int = 3,
    header: @Composable ColumnScope.() -> Unit = {},
) {
    BoxWithConstraints {
        val wide = maxWidth > maxHeight && maxHeight < WIDE_MAX_HEIGHT
        if (!wide) {
            val keyHeight = ((maxHeight * KEYPAD_SHARE - KEY_GAP * 3) / 4).coerceIn(MIN_KEY, MAX_KEY)
            Scaffold(
                bottomBar = {
                    BottomActions {
                        // Capped so the heading always keeps some room. When an error appears, the keys shrink to make space for it.
                        Column(Modifier.heightIn(max = maxHeight * KEYPAD_AREA_MAX), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            EntryError(error)
                            ValueDisplay(display, unit)
                            NumberPad(
                                value,
                                onValueChange,
                                modifier = Modifier.weight(1f, fill = false),
                                maxDecimals = maxDecimals,
                                maxIntegerDigits = maxIntegerDigits,
                                keyHeight = keyHeight,
                            )
                            action()
                        }
                    }
                },
            ) { padding ->
                Column(
                    Modifier
                        .padding(padding)
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 20.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    StepHeader(0, 0, title, onBack)
                    header()
                    Spacer(Modifier.heightIn(min = 16.dp))
                }
            }
        } else {
            val keyHeight = ((maxHeight - KEY_GAP * 3 - 48.dp) / 4).coerceIn(MIN_KEY_WIDE, MAX_KEY_WIDE)
            Scaffold { padding ->
                Row(Modifier.padding(padding).fillMaxSize()) {
                    Column(Modifier.weight(1f).fillMaxHeight()) {
                        Column(
                            Modifier
                                .weight(1f)
                                .verticalScroll(rememberScrollState())
                                .padding(horizontal = 20.dp, vertical = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp),
                        ) {
                            StepHeader(0, 0, title, onBack)
                            header()
                        }
                        Column(Modifier.padding(horizontal = 20.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            EntryError(error)
                            ValueDisplay(display, unit)
                            action()
                        }
                    }
                    NumberPad(
                        value,
                        onValueChange,
                        modifier = Modifier.weight(1f).align(Alignment.CenterVertically).padding(horizontal = 16.dp, vertical = 12.dp),
                        maxDecimals = maxDecimals,
                        maxIntegerDigits = maxIntegerDigits,
                        keyHeight = keyHeight,
                    )
                }
            }
        }
    }
}

/** A short error next to the keypad, so it is seen without scrolling. The icon carries the meaning, not just the colour. */
@Composable
private fun EntryError(error: String?) {
    error ?: return
    val status = LocalStatusColors.current
    Surface(
        color = status.dangerContainer,
        contentColor = status.onDangerContainer,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(2.dp, status.onDangerContainer),
        modifier = Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Assertive },
    ) {
        // Kept to a modest size so the keys keep their room. The screen also shows the full explanation above.
        val density = LocalDensity.current
        CompositionLocalProvider(LocalDensity provides Density(density.density, density.fontScale.coerceAtMost(ERROR_FONT_SCALE))) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)) {
                Icon(painterResource(R.drawable.ic_error), contentDescription = null, modifier = Modifier.size(24.dp))
                Spacer(Modifier.width(10.dp))
                Text(error, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

/** Pinned buttons may cover at most this share of the screen before the extra ones move into the content. */
private const val PINNED_SHARE = 0.35f

/** The keypad takes about this share of a tall screen. */
private const val KEYPAD_SHARE = 0.40f

/** The error beside the keypad grows with the text size only up to this point. */
private const val ERROR_FONT_SCALE = 1.15f

/** The value, keypad and button together never take more than this share, so the heading stays in view. */
private const val KEYPAD_AREA_MAX = 0.80f
private val KEY_GAP = 10.dp
private val MIN_KEY = 56.dp
private val MAX_KEY = 76.dp
private val MIN_KEY_WIDE = 52.dp
private val MAX_KEY_WIDE = 72.dp

/** Below this height a screen wider than it is tall puts the keypad beside the heading. */
private val WIDE_MAX_HEIGHT = 480.dp

@Composable
private fun BottomActions(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.surface, tonalElevation = 3.dp, modifier = modifier) {
        Column {
            HorizontalDivider()
            Column(Modifier.navigationBarsPadding().padding(horizontal = 20.dp, vertical = 12.dp).fillMaxWidth()) { content() }
        }
    }
}

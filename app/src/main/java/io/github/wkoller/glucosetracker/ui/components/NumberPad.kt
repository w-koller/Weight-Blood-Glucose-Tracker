package io.github.wkoller.glucosetracker.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import io.github.wkoller.glucosetracker.R
import io.github.wkoller.glucosetracker.ui.theme.LocalUiPrefs

/**
 * The number being typed, shown very large with its unit. It stays on one line: the text shrinks to
 * fit rather than wrapping, because it is already larger than the largest text size.
 */
@Composable
fun ValueDisplay(value: String, unit: String, modifier: Modifier = Modifier) {
    val spoken = if (value.isEmpty()) stringResource(R.string.number_empty) else "$value $unit"
    val density = LocalDensity.current
    // Sizes are fixed in dp, so the text-size setting does not push the number past the edges.
    val largest = with(density) { 52.dp.toSp() }
    val smallest = with(density) { 22.dp.toSp() }
    val text = buildAnnotatedString {
        append(value.ifEmpty { "–" })
        if (unit.isNotEmpty()) withStyle(SpanStyle(fontSize = 0.45.em, fontWeight = FontWeight.SemiBold)) { append(" $unit") }
    }
    Surface(
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(3.dp, MaterialTheme.colorScheme.primary),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = modifier
            .fillMaxWidth()
            .semantics {
                contentDescription = spoken
                liveRegion = LiveRegionMode.Polite
            },
    ) {
        Text(
            text,
            style = MaterialTheme.typography.displayMedium.copy(lineHeight = 1.15.em),
            textAlign = TextAlign.Center,
            maxLines = 1,
            autoSize = TextAutoSize.StepBased(minFontSize = smallest, maxFontSize = largest),
            modifier = Modifier.fillMaxWidth().height(64.dp).padding(horizontal = 16.dp, vertical = 4.dp).wrapContentHeight(),
        )
    }
}

/** What a key press does to the text on the keypad, kept apart from the keys so it can be tested on its own. */
internal object NumberPadInput {
    /**
     * The text after pressing [key], which is a digit, "." or "back". A number drops a leading zero, as
     * on a calculator. A PIN is a row of digits rather than a number, so with [keepLeadingZeros] its
     * zeros stay.
     */
    fun next(value: String, key: String, maxDecimals: Int, maxIntegerDigits: Int, keepLeadingZeros: Boolean = false): String = when (key) {
        "back" -> value.dropLast(1)
        "." -> if (maxDecimals == 0 || '.' in value) value else (value.ifEmpty { "0" } + ".")
        else -> {
            val parts = (value + key).split('.')
            val integer = if (keepLeadingZeros) parts[0] else parts[0].trimStart('0')
            val integerOk = integer.length <= maxIntegerDigits
            val decimalOk = parts.size < 2 || parts[1].length <= maxDecimals
            when {
                !integerOk || !decimalOk -> value
                value == "0" && !keepLeadingZeros -> key
                else -> value + key
            }
        }
    }
}

/**
 * A large on-screen keypad. It avoids the phone keyboard, whose keys are small and whose layout
 * changes between phones.
 */
@Composable
fun NumberPad(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    maxDecimals: Int = 1,
    maxIntegerDigits: Int = 3,
    keepLeadingZeros: Boolean = false,
    keyHeight: Dp = 68.dp,
) {
    val haptics = LocalHapticFeedback.current
    val prefs = LocalUiPrefs.current
    fun press(key: String) {
        if (prefs.haptics) haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        onValueChange(NumberPadInput.next(value, key, maxDecimals, maxIntegerDigits, keepLeadingZeros))
    }
    val pointLabel = stringResource(R.string.number_point)
    // The digits are sized from the key, not the text-size setting, so they always fit inside it.
    val labelStyle = MaterialTheme.typography.headlineLarge.copy(
        fontSize = with(LocalDensity.current) { (keyHeight * 0.45f).toSp() },
        lineHeight = 1.1.em,
    )
    val rows = listOf(listOf("1", "2", "3"), listOf("4", "5", "6"), listOf("7", "8", "9"), listOf(".", "0", "back"))
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        rows.forEach { row ->
            // A row is given less than a full key only when the space above it is needed, for example for an error.
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.weight(1f, fill = false)) {
                row.forEach { key ->
                    val enabled = !(key == "." && maxDecimals == 0)
                    OutlinedButton(
                        onClick = { press(key) },
                        enabled = enabled,
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(2.dp, MaterialTheme.colorScheme.outline),
                        contentPadding = PaddingValues(0.dp),
                        modifier = Modifier.weight(1f).height(keyHeight).testTag("key_$key"),
                    ) {
                        when (key) {
                            "back" -> Icon(
                                painterResource(R.drawable.ic_backspace),
                                contentDescription = stringResource(R.string.number_delete),
                                modifier = Modifier.size(keyHeight * 0.45f),
                            )
                            "." -> Text(
                                ".",
                                style = labelStyle,
                                modifier = Modifier.semantics { contentDescription = pointLabel },
                            )
                            else -> Text(key, style = labelStyle)
                        }
                    }
                }
            }
        }
    }
}

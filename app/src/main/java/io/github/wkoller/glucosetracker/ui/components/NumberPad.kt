package io.github.wkoller.glucosetracker.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.wkoller.glucosetracker.R
import io.github.wkoller.glucosetracker.ui.theme.LocalUiPrefs

/** The number being typed, shown very large with its unit. */
@Composable
fun ValueDisplay(value: String, unit: String, modifier: Modifier = Modifier) {
    val spoken = if (value.isEmpty()) stringResource(R.string.number_empty) else "$value $unit"
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
        Row(
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier.padding(vertical = 12.dp, horizontal = 16.dp),
        ) {
            Text(
                value.ifEmpty { "–" },
                style = MaterialTheme.typography.displayMedium,
                textAlign = TextAlign.Center,
            )
            Text(
                " $unit",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(bottom = 8.dp),
            )
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
) {
    val haptics = LocalHapticFeedback.current
    val prefs = LocalUiPrefs.current
    fun press(key: String) {
        if (prefs.haptics) haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        val next = when (key) {
            "back" -> value.dropLast(1)
            "." -> if (maxDecimals == 0 || '.' in value) value else (value.ifEmpty { "0" } + ".")
            else -> {
                val parts = (value + key).split('.')
                val integerOk = parts[0].trimStart('0').length <= maxIntegerDigits
                val decimalOk = parts.size < 2 || parts[1].length <= maxDecimals
                if (integerOk && decimalOk) (if (value == "0") key else value + key) else value
            }
        }
        onValueChange(next)
    }
    val pointLabel = stringResource(R.string.number_point)
    val rows = listOf(listOf("1", "2", "3"), listOf("4", "5", "6"), listOf("7", "8", "9"), listOf(".", "0", "back"))
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        rows.forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { key ->
                    val enabled = !(key == "." && maxDecimals == 0)
                    OutlinedButton(
                        onClick = { press(key) },
                        enabled = enabled,
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(2.dp, MaterialTheme.colorScheme.outline),
                        modifier = Modifier.weight(1f).heightIn(min = 68.dp).testTag("key_$key"),
                    ) {
                        when (key) {
                            "back" -> Icon(
                                painterResource(R.drawable.ic_backspace),
                                contentDescription = stringResource(R.string.number_delete),
                                modifier = Modifier.size(32.dp),
                            )
                            "." -> Text(
                                ".",
                                style = MaterialTheme.typography.headlineLarge,
                                modifier = Modifier.semantics { contentDescription = pointLabel },
                            )
                            else -> Text(key, style = MaterialTheme.typography.headlineLarge)
                        }
                    }
                }
            }
        }
    }
}

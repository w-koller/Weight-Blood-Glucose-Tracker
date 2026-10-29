package io.github.wkoller.glucosetracker.ui.components

import androidx.annotation.StringRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.wkoller.glucosetracker.R
import io.github.wkoller.glucosetracker.domain.dosing.GlucoseStatus
import io.github.wkoller.glucosetracker.domain.dosing.SlidingScale
import io.github.wkoller.glucosetracker.domain.format.Measures
import io.github.wkoller.glucosetracker.domain.model.MeterFlag
import io.github.wkoller.glucosetracker.ui.theme.LocalStatusColors

/** The background, text colour and icon for a reading's status, shared by the chip, the pill and the logbook chart. */
@Composable
fun statusStyle(status: GlucoseStatus): Triple<Color, Color, Int> {
    val colors = LocalStatusColors.current
    return when (status) {
        GlucoseStatus.LOW -> Triple(colors.low, colors.onLow, R.drawable.ic_warning)
        GlucoseStatus.BELOW_TARGET -> Triple(colors.above, colors.onAbove, R.drawable.ic_arrow_downward)
        GlucoseStatus.IN_TARGET -> Triple(colors.inTarget, colors.onInTarget, R.drawable.ic_check_circle)
        GlucoseStatus.ABOVE_TARGET -> Triple(colors.above, colors.onAbove, R.drawable.ic_arrow_upward)
    }
}

/** The status of a reading as an icon and a word on a coloured background. */
@Composable
fun StatusChip(status: GlucoseStatus, modifier: Modifier = Modifier) {
    val (background, content, icon) = statusStyle(status)
    Surface(color = background, contentColor = content, shape = RoundedCornerShape(50), modifier = modifier) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)) {
            Icon(painterResource(icon), contentDescription = null, modifier = Modifier.size(24.dp))
            Spacer(Modifier.width(6.dp))
            Text(stringResource(status.labelRes()), style = MaterialTheme.typography.labelLarge)
        }
    }
}

/** "0 – 10", "10.1 – 12" or "Above 20", matching the paper order. */
@Composable
fun bandRangeText(scale: SlidingScale, index: Int): String {
    val lower = scale.lowerTenths(index)
    val upper = scale.bands[index].upperTenths
    return if (upper == null) {
        stringResource(R.string.scale_above, Measures.formatTenthsShort(lower - 1))
    } else {
        stringResource(R.string.scale_range, Measures.formatTenthsShort(lower), Measures.formatTenthsShort(upper))
    }
}

/** The sliding scale as a table, with the row for the current reading highlighted. */
@Composable
fun ScaleTable(scale: SlidingScale, insulin: String, highlightIndex: Int?, modifier: Modifier = Modifier) {
    val colors = LocalStatusColors.current
    Surface(
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(vertical = 8.dp)) {
            Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                Text(stringResource(R.string.scale_header_bgl), style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1.4f))
                Text(stringResource(R.string.scale_header_units, insulin), style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            }
            HorizontalDivider()
            scale.bands.forEachIndexed { index, band ->
                val highlighted = index == highlightIndex
                val range = bandRangeText(scale, index)
                val unitsText = pluralStringResource(R.plurals.units_plural, band.units, band.units.toString())
                val description = stringResource(
                    if (highlighted) R.string.scale_row_description_current else R.string.scale_row_description,
                    range,
                    unitsText,
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 52.dp)
                        .padding(horizontal = 8.dp, vertical = 2.dp)
                        .then(
                            if (highlighted) {
                                Modifier
                                    .background(colors.highlight, RoundedCornerShape(10.dp))
                                    .border(3.dp, MaterialTheme.colorScheme.onSurface, RoundedCornerShape(10.dp))
                            } else {
                                Modifier
                            },
                        )
                        .padding(horizontal = 8.dp, vertical = 6.dp)
                        .clearAndSetSemantics { contentDescription = description },
                ) {
                    val color = if (highlighted) colors.onHighlight else MaterialTheme.colorScheme.onSurface
                    val weight = if (highlighted) FontWeight.Bold else FontWeight.Normal
                    Column(Modifier.weight(1.4f)) {
                        Text(range, style = MaterialTheme.typography.titleMedium, fontWeight = weight, color = color)
                        if (highlighted) Text(stringResource(R.string.scale_your_reading), style = MaterialTheme.typography.bodyMedium, color = color)
                    }
                    Text(unitsText, style = MaterialTheme.typography.titleMedium, fontWeight = weight, color = color, modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
fun BglValue(tenths: Int, modifier: Modifier = Modifier) {
    Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.Start, modifier = modifier) {
        Text(Measures.formatTenths(tenths), style = MaterialTheme.typography.displayMedium)
        Text(" " + stringResource(R.string.unit_mmol), style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(bottom = 8.dp))
    }
}

/**
 * HI and LO keys for the keypad, for when the meter shows a word instead of a number. HI sits above LO,
 * as high sits above low. A key only fills in the reading, like a digit, so the user still presses Next.
 */
@Composable
fun meterKeys(selected: MeterFlag?, onPress: (MeterFlag) -> Unit): List<WordKey> = listOf(MeterFlag.HI, MeterFlag.LO).map { flag ->
    WordKey(
        label = stringResource(flag.wordRes()),
        description = stringResource(flag.meterShowsRes()),
        selected = selected == flag,
        tag = "key_${flag.name.lowercase()}",
        onPress = { onPress(flag) },
    )
}

/** "LO" or "HI", the word the meter shows. */
@StringRes
fun MeterFlag.wordRes(): Int = when (this) {
    MeterFlag.LO -> R.string.meter_lo
    MeterFlag.HI -> R.string.meter_hi
}

/** "My meter shows LO", read by TalkBack for the key and for the reading once it is entered. */
@StringRes
fun MeterFlag.meterShowsRes(): Int = when (this) {
    MeterFlag.LO -> R.string.checkin_meter_lo
    MeterFlag.HI -> R.string.checkin_meter_hi
}

/**
 * A reading as a coloured pill: the status icon, then the number and its unit, or LO or HI when the meter
 * could not measure the level. The icon carries the status as well as the colour does.
 */
@Composable
fun ReadingPill(tenths: Int, meterFlag: MeterFlag?, status: GlucoseStatus, modifier: Modifier = Modifier) {
    val (background, content, icon) = statusStyle(status)
    Surface(color = background, contentColor = content, shape = RoundedCornerShape(50), modifier = modifier) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 12.dp, end = 16.dp, top = 6.dp, bottom = 6.dp)) {
            Icon(painterResource(icon), contentDescription = null, modifier = Modifier.size(24.dp))
            Spacer(Modifier.width(8.dp))
            Text(meterFlag?.let { stringResource(it.wordRes()) } ?: Measures.formatTenths(tenths), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            if (meterFlag == null) {
                Spacer(Modifier.width(4.dp))
                Text(stringResource(R.string.unit_mmol), style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

/** A measured value, such as a weight, as a pill like [ReadingPill] but without a status. */
@Composable
fun MeasurePill(value: String, unit: String, modifier: Modifier = Modifier) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = MaterialTheme.colorScheme.onSurface,
        shape = RoundedCornerShape(50),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = modifier,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
            Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Spacer(Modifier.width(4.dp))
            Text(unit, style = MaterialTheme.typography.labelSmall)
        }
    }
}

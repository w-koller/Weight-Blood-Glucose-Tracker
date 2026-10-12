package io.github.wkoller.glucosetracker.ui.components

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
import io.github.wkoller.glucosetracker.ui.theme.LocalStatusColors

/** The status of a reading as an icon and a word on a coloured background. */
@Composable
fun StatusChip(status: GlucoseStatus, modifier: Modifier = Modifier) {
    val colors = LocalStatusColors.current
    val (background, content, icon) = when (status) {
        GlucoseStatus.LOW -> Triple(colors.low, colors.onLow, R.drawable.ic_warning)
        GlucoseStatus.BELOW_TARGET -> Triple(colors.above, colors.onAbove, R.drawable.ic_arrow_downward)
        GlucoseStatus.IN_TARGET -> Triple(colors.inTarget, colors.onInTarget, R.drawable.ic_check_circle)
        GlucoseStatus.ABOVE_TARGET -> Triple(colors.above, colors.onAbove, R.drawable.ic_arrow_upward)
    }
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

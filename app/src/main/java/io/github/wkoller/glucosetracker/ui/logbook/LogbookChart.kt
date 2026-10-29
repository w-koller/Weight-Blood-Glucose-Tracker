package io.github.wkoller.glucosetracker.ui.logbook

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.wkoller.glucosetracker.R
import io.github.wkoller.glucosetracker.domain.dosing.GlucoseStatus
import io.github.wkoller.glucosetracker.domain.format.Measures
import io.github.wkoller.glucosetracker.domain.model.GlucoseTargets
import io.github.wkoller.glucosetracker.domain.model.MeterFlag
import io.github.wkoller.glucosetracker.domain.progress.TrendPoint
import io.github.wkoller.glucosetracker.ui.UiFormat
import io.github.wkoller.glucosetracker.ui.components.drawPoint
import io.github.wkoller.glucosetracker.ui.components.pointShape
import io.github.wkoller.glucosetracker.ui.components.wordRes
import io.github.wkoller.glucosetracker.ui.theme.LocalStatusColors
import io.github.wkoller.glucosetracker.ui.theme.LocalUiPrefs
import io.github.wkoller.glucosetracker.ui.theme.StatusColors
import java.time.Duration
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.floor

/** The chart shows this many days side by side, so each day has room for its readings. */
const val CHART_DAYS = 3

/**
 * The first of the [CHART_DAYS] days the chart shows: the days either side of [focus], kept within the days
 * that have records so the chart never shows empty days past [last] when there are earlier ones to show.
 */
fun chartStart(focus: LocalDate, first: LocalDate, last: LocalDate): LocalDate {
    val latest = last.minusDays(CHART_DAYS - 1L)
    val earliest = if (first.isBefore(latest)) first else latest
    val start = focus.minusDays(1)
    return when {
        start.isAfter(latest) -> latest
        start.isBefore(earliest) -> earliest
        else -> start
    }
}

/**
 * A chart of the readings on the days around the top of the logbook list, like a window that follows the list
 * as it scrolls. The day at the top of the list is highlighted and [focusPoint], the reading at the top, is
 * ringed with its value. Pressing a day calls [onDay] to open that day on the full-screen chart. TalkBack reads
 * each day from [describeDay].
 */
@Composable
fun LogbookChart(
    points: List<TrendPoint>,
    focus: LocalDate,
    focusPoint: TrendPoint?,
    first: LocalDate,
    last: LocalDate,
    targets: GlucoseTargets,
    height: Dp,
    describeDay: (LocalDate) -> String,
    onDay: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
) {
    val prefs = LocalUiPrefs.current
    val status = LocalStatusColors.current
    val colors = MaterialTheme.colorScheme
    val measurer = rememberTextMeasurer()
    val labelStyle = MaterialTheme.typography.labelMedium.copy(color = colors.onSurfaceVariant)
    val focusLabelStyle = labelStyle.copy(color = colors.onSurface, fontWeight = FontWeight.Bold)
    val bubbleStyle = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)
    val start = chartStart(focus, first, last)
    // The window slides to its new place, or jumps there when motion is reduced.
    val animatedStart by animateFloatAsState(
        targetValue = start.toEpochDay().toFloat(),
        animationSpec = if (prefs.reduceMotion) snap() else tween(durationMillis = 350),
        label = "chart window",
    )
    val focusWord = focusPoint?.meterFlag?.let { stringResource(it.wordRes()) }
    val zone = ZoneId.systemDefault()
    // LO and HI sit at the bottom and top edges rather than stretching the chart to the meter's limits.
    val topTenths = maxOf(Y_TOP_TENTHS, ((points.filter { it.meterFlag == null }.maxOfOrNull { it.value } ?: 0) + 49) / 50 * 50).coerceAtMost(Y_MAX_TENTHS)

    Surface(color = colors.surface, modifier = modifier.fillMaxWidth()) {
        Column {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(height)
                    .semantics { isTraversalGroup = true },
            ) {
                Canvas(Modifier.fillMaxWidth().height(height).clipToBounds()) {
                    val columnWidth = size.width / CHART_DAYS
                    val labelHeight = measurer.measure("0", labelStyle).size.height.toFloat()
                    val plotTop = labelHeight + 14.dp.toPx()
                    val plotBottom = size.height - 10.dp.toPx()
                    fun xOf(date: LocalDate, fraction: Float) = (date.toEpochDay() + fraction - animatedStart) * columnWidth
                    fun yOf(tenths: Int) = plotBottom - (plotBottom - plotTop) * (tenths.coerceIn(0, topTenths).toFloat() / topTenths)
                fun yOf(point: TrendPoint) = when (point.meterFlag) {
                    MeterFlag.HI -> plotTop
                    MeterFlag.LO -> plotBottom
                    null -> yOf(point.value)
                }
                    fun fractionOf(point: TrendPoint): Float {
                        // Readings after midnight that belong to the day before stay at the end of that day.
                        val sinceMidnight = Duration.between(point.date.atStartOfDay(zone).toInstant(), point.takenAt).toMinutes() / MINUTES_PER_DAY
                        return sinceMidnight.coerceIn(0.03f, 0.97f)
                    }

                    val firstDay = floor(animatedStart).toLong()
                    for (day in firstDay - 1..firstDay + CHART_DAYS + 1) {
                        val date = LocalDate.ofEpochDay(day)
                        val left = xOf(date, 0f)
                        val background = when {
                            date == focus -> colors.primaryContainer
                            day % 2 == 0L -> colors.surfaceContainer
                            else -> colors.surfaceContainerHigh
                        }
                        drawRect(background, topLeft = Offset(left, 0f), size = Size(columnWidth, size.height))
                        val label = measurer.measure(UiFormat.weekdayShort(date), if (date == focus) focusLabelStyle else labelStyle)
                        drawText(label, topLeft = Offset(left + (columnWidth - label.size.width) / 2, 6.dp.toPx()))
                    }

                    val bandTop = yOf(targets.highTenths)
                    drawRect(status.inTarget.copy(alpha = 0.18f), topLeft = Offset(0f, bandTop), size = Size(size.width, yOf(targets.lowTenths) - bandTop))

                    val placed = points.map { point -> point to Offset(xOf(point.date, fractionOf(point)), yOf(point)) }
                    if (placed.size > 1) {
                        val path = Path().apply {
                            moveTo(placed[0].second.x, placed[0].second.y)
                            placed.drop(1).forEach { lineTo(it.second.x, it.second.y) }
                        }
                        drawPath(path, colors.onSurfaceVariant.copy(alpha = 0.55f), style = Stroke(width = 2.dp.toPx()))
                    }
                    placed.forEach { (point, at) ->
                        if (at.x < -columnWidth || at.x > size.width + columnWidth) return@forEach
                        drawPoint(at, pointShape(point.status), pointColor(point.status, status), 6.dp.toPx(), haloColor = colors.surface)
                    }

                    // The reading at the top of the list, ringed, with its value in a bubble above or below it.
                    val focused = placed.firstOrNull { it.first == focusPoint } ?: return@Canvas
                    val center = focused.second
                    drawCircle(colors.onSurface, radius = 11.dp.toPx(), center = center, style = Stroke(width = 3.dp.toPx()))
                    val text = measurer.measure(focusWord ?: Measures.formatTenths(focused.first.value), bubbleStyle.copy(color = onPointColor(focused.first.status, status)))
                    val padX = 8.dp.toPx()
                    val padY = 3.dp.toPx()
                    val bubble = Size(text.size.width + padX * 2, text.size.height + padY * 2)
                    val gap = 15.dp.toPx()
                    val margin = 2.dp.toPx()
                    val aboveTop = center.y - gap - bubble.height
                    val belowTop = center.y + gap
                    val centred = (center.x - bubble.width / 2).coerceAtMost(size.width - bubble.width - margin).coerceAtLeast(margin)
                    // Above the point if it clears the day names, else below it, else beside it.
                    val (left, top) = when {
                        aboveTop >= plotTop - 4.dp.toPx() -> centred to aboveTop
                        belowTop + bubble.height <= size.height - margin -> centred to belowTop
                        else -> {
                            val right = center.x + gap
                            val side = if (right + bubble.width <= size.width - margin) right else center.x - gap - bubble.width
                            side to (center.y - bubble.height / 2).coerceAtMost(size.height - bubble.height - margin).coerceAtLeast(plotTop)
                        }
                    }
                    drawRoundRect(colors.surface, topLeft = Offset(left - 2.dp.toPx(), top - 2.dp.toPx()), size = Size(bubble.width + 4.dp.toPx(), bubble.height + 4.dp.toPx()), cornerRadius = CornerRadius(bubble.height))
                    drawRoundRect(pointColor(focused.first.status, status), topLeft = Offset(left, top), size = bubble, cornerRadius = CornerRadius(bubble.height))
                    drawText(text, topLeft = Offset(left + padX, top + padY))
                }

                // One target per day for touch and TalkBack. They follow the chart's resting place, not the slide.
                val choose = stringResource(R.string.logbook_chart_show_day)
                Row(Modifier.fillMaxWidth().height(height)) {
                    repeat(CHART_DAYS) { index ->
                        val date = start.plusDays(index.toLong())
                        val description = describeDay(date)
                        Box(
                            Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .semantics { contentDescription = description }
                                .clickable(onClickLabel = choose) { onDay(date) }
                                .testTag("chart_day_$date"),
                        )
                    }
                }
            }
            HorizontalDivider()
        }
    }
}

/** The colour of a point in its status, shared with the full-screen chart. */
internal fun pointColor(status: GlucoseStatus?, colors: StatusColors) = when (status) {
    GlucoseStatus.LOW -> colors.low
    GlucoseStatus.BELOW_TARGET, GlucoseStatus.ABOVE_TARGET -> colors.above
    GlucoseStatus.IN_TARGET, null -> colors.inTarget
}

private fun onPointColor(status: GlucoseStatus?, colors: StatusColors) = when (status) {
    GlucoseStatus.LOW -> colors.onLow
    GlucoseStatus.BELOW_TARGET, GlucoseStatus.ABOVE_TARGET -> colors.onAbove
    GlucoseStatus.IN_TARGET, null -> colors.onInTarget
}

private const val MINUTES_PER_DAY = 24f * 60f

/** The chart always reaches at least 20 mmol/L, and grows in steps of 5 for higher readings up to 35. */
private const val Y_TOP_TENTHS = 200
private const val Y_MAX_TENTHS = 350

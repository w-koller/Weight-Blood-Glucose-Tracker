package io.github.wkoller.glucosetracker.ui.logbook

import android.content.pm.ActivityInfo
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.annotation.DrawableRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.wkoller.glucosetracker.R
import io.github.wkoller.glucosetracker.domain.format.Measures
import io.github.wkoller.glucosetracker.domain.model.DoseStatus
import io.github.wkoller.glucosetracker.domain.model.GlucoseTargets
import io.github.wkoller.glucosetracker.domain.model.MedicineKind
import io.github.wkoller.glucosetracker.domain.model.MeterFlag
import io.github.wkoller.glucosetracker.domain.progress.TrendPoint
import io.github.wkoller.glucosetracker.domain.records.CheckEntry
import io.github.wkoller.glucosetracker.domain.records.DayChart
import io.github.wkoller.glucosetracker.domain.records.DayCharts
import io.github.wkoller.glucosetracker.domain.records.LogbookDay
import io.github.wkoller.glucosetracker.ui.UiFormat
import io.github.wkoller.glucosetracker.ui.appViewModel
import io.github.wkoller.glucosetracker.ui.components.BackButton
import io.github.wkoller.glucosetracker.ui.components.ReadingPill
import io.github.wkoller.glucosetracker.ui.components.drawPoint
import io.github.wkoller.glucosetracker.ui.components.label
import io.github.wkoller.glucosetracker.ui.components.pointShape
import io.github.wkoller.glucosetracker.ui.components.statusStyle
import io.github.wkoller.glucosetracker.ui.components.wordRes
import io.github.wkoller.glucosetracker.ui.theme.LocalStatusColors
import io.github.wkoller.glucosetracker.ui.theme.LocalUiPrefs
import java.time.LocalDate
import java.time.LocalTime
import kotlin.math.roundToInt

/**
 * One day of the logbook on a full-screen chart, turned sideways, like the chart of a meter app. The day's
 * readings are large values joined by a line, by time of day, over small dots for the readings of the 30 days
 * before, so the day can be compared with what is usual at each time. Pressing a value shows its details.
 * Earlier day and Later day change the day, and Back calls [onClose] with the day shown, so the list can open
 * at that day.
 */
@Composable
fun DayChartScreen(onClose: (LocalDate) -> Unit) {
    val vm = appViewModel { container, handle -> DayChartViewModel(container, handle) }
    val ui by vm.ui.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val prefs = LocalUiPrefs.current
    var openKey by rememberSaveable { mutableStateOf<String?>(null) }
    SidewaysWhileShown()
    BackHandler { onClose(ui.date) }

    Scaffold { padding ->
        BoxWithConstraints(Modifier.padding(padding).fillMaxSize()) {
            // The key under the chart is left out on a short screen or with very large text, so the chart keeps its room.
            val showKey = prefs.showHelperText && maxHeight >= KEY_MIN_HEIGHT && LocalDensity.current.fontScale <= KEY_MAX_FONT_SCALE
            Column(
                Modifier.fillMaxSize().padding(start = 12.dp, end = 16.dp, top = 4.dp, bottom = 8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    itemVerticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    BackButton { onClose(ui.date) }
                    // With very large text the date is shortened, such as "Wed 28 Oct", so it stays on one line. TalkBack hears it in full.
                    val longDate = UiFormat.dayLong(ui.date)
                    Text(
                        if (LocalDensity.current.fontScale > SHORT_DATE_FONT_SCALE) UiFormat.dayShort(ui.date) else longDate,
                        style = MaterialTheme.typography.headlineSmall,
                        modifier = Modifier.weight(1f).semantics {
                            heading()
                            contentDescription = longDate
                        },
                    )
                    DayStepButton(stringResource(R.string.day_chart_earlier), R.drawable.ic_chevron_left, iconFirst = true, enabled = ui.canGoEarlier, onClick = vm::earlier)
                    DayStepButton(stringResource(R.string.day_chart_later), R.drawable.ic_chevron_right, iconFirst = false, enabled = ui.canGoLater, onClick = vm::later)
                }
                val chart = ui.chart
                Box(Modifier.weight(1f).fillMaxWidth()) {
                    if (chart != null) {
                        val history = if (chart.others.isEmpty()) null else pluralStringResource(R.plurals.day_chart_history, chart.others.size, chart.others.size)
                        val summary = listOfNotNull(describeChartDay(context, chart.date, LogbookDay(chart.date, chart.entries)), history).joinToString(" ")
                        DayChartPlot(
                            chart,
                            ui.targets,
                            summary,
                            onOpen = { openKey = it.key },
                            onEarlier = { if (ui.canGoEarlier) vm.earlier() },
                            onLater = { if (ui.canGoLater) vm.later() },
                        )
                        if (chart.points.isEmpty()) {
                            Surface(color = MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(12.dp), modifier = Modifier.align(Alignment.Center)) {
                                Text(stringResource(R.string.day_chart_none), style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
                            }
                        }
                    }
                }
                if (showKey) {
                    Text(
                        pluralStringResource(R.plurals.day_chart_key, DayCharts.HISTORY_DAYS, DayCharts.HISTORY_DAYS),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }

    ui.chart?.entries?.firstOrNull { it.key == openKey }?.let { entry -> CheckDetailsDialog(entry, onDismiss = { openKey = null }) }
}

/**
 * Turns the screen sideways while the chart is shown, and back as it was when the chart closes. Turning the
 * screen starts the activity again, with the chart still open, so the first orientation is kept in saved state
 * and the turn back waits until the chart really closes.
 */
@Composable
private fun SidewaysWhileShown() {
    val activity = LocalActivity.current ?: return
    val before = rememberSaveable { activity.requestedOrientation }
    DisposableEffect(activity) {
        activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        onDispose { if (!activity.isChangingConfigurations) activity.requestedOrientation = before }
    }
}

/** Earlier day or Later day, compact enough to sit beside the date in the bar above the chart. */
@Composable
private fun DayStepButton(text: String, @DrawableRes icon: Int, iconFirst: Boolean, enabled: Boolean, onClick: () -> Unit) {
    val prefs = LocalUiPrefs.current
    val haptics = LocalHapticFeedback.current
    OutlinedButton(
        onClick = {
            if (prefs.haptics) haptics.performHapticFeedback(HapticFeedbackType.LongPress)
            onClick()
        },
        enabled = enabled,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(2.dp, if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.primary),
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
        modifier = Modifier.heightIn(min = 56.dp),
    ) {
        if (iconFirst && prefs.showIcons) {
            Icon(painterResource(icon), contentDescription = null, modifier = Modifier.size(28.dp))
            Spacer(Modifier.width(6.dp))
        }
        Text(text, style = MaterialTheme.typography.labelLarge)
        if (!iconFirst && prefs.showIcons) {
            Spacer(Modifier.width(6.dp))
            Icon(painterResource(icon), contentDescription = null, modifier = Modifier.size(28.dp))
        }
    }
}

/**
 * The plot: time of day along the bottom, from the start of the day, and the level up the side. The day's values
 * are placed over the drawing as buttons, so each can be pressed and TalkBack reads each one after [summary].
 * Holding a finger on the chart and sliding it sideways moves through the days, a day for each fifth of the
 * width: to the right for the days before, as on the list's chart, and to the left for the days after.
 */
@Composable
private fun DayChartPlot(
    chart: DayChart,
    targets: GlucoseTargets,
    summary: String,
    onOpen: (CheckEntry) -> Unit,
    onEarlier: () -> Unit,
    onLater: () -> Unit,
) {
    val context = LocalContext.current
    val measurer = rememberTextMeasurer()
    val colors = MaterialTheme.colorScheme
    val status = LocalStatusColors.current
    val labelStyle = MaterialTheme.typography.bodySmall.copy(color = colors.onSurfaceVariant)
    val density = LocalDensity.current
    val hourLabels = remember(chart.dayStartMinute) { hourTicks(chart.dayStartMinute) }.map { (minute, time) -> minute to UiFormat.hour(context, time) }
    val prefs = LocalUiPrefs.current
    val haptics = LocalHapticFeedback.current
    val earlier by rememberUpdatedState(onEarlier)
    val later by rememberUpdatedState(onLater)
    val drag = Modifier.pointerInput(Unit) {
        var pulled = 0f
        detectHorizontalDragGestures(onDragStart = { pulled = 0f }) { change, dragAmount ->
            change.consume()
            pulled += dragAmount
            val step = size.width / DRAG_STEPS
            while (pulled >= step || pulled <= -step) {
                if (prefs.haptics) haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                if (pulled > 0) {
                    pulled -= step
                    earlier()
                } else {
                    pulled += step
                    later()
                }
            }
        }
    }
    BoxWithConstraints(Modifier.fillMaxSize().then(drag).testTag("day_chart")) {
        val geometry = DayGeometry.of(chart, constraints.maxWidth.toFloat(), constraints.maxHeight.toFloat(), measurer, labelStyle, density)
        Canvas(Modifier.matchParentSize().semantics { contentDescription = summary }) {
            val g = geometry
            drawRect(colors.surfaceContainer, topLeft = Offset(g.left, g.top), size = Size(g.right - g.left, g.bottom - g.top))
            val bandTop = g.yOf(targets.highTenths)
            drawRect(status.inTarget.copy(alpha = 0.18f), topLeft = Offset(g.left, bandTop), size = Size(g.right - g.left, g.yOf(targets.lowTenths) - bandTop))

            // Levels every 5 mmol/L, with their numbers on the left.
            g.levelTicks.forEach { tenths ->
                val y = g.yOf(tenths)
                drawLine(colors.outline.copy(alpha = 0.3f), Offset(g.left, y), Offset(g.right, y), strokeWidth = 1.dp.toPx())
                val text = measurer.measure(Measures.formatTenthsShort(tenths), labelStyle)
                drawText(text, topLeft = Offset(g.left - 8.dp.toPx() - text.size.width, y - text.size.height / 2))
            }

            // Times of day every 3 hours, skipping a label that would run into the one before.
            val dash = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 6.dp.toPx()))
            var lastRight = Float.NEGATIVE_INFINITY
            hourLabels.forEach { (minute, label) ->
                val x = g.xOf(minute)
                drawLine(colors.outline.copy(alpha = 0.35f), Offset(x, g.top), Offset(x, g.bottom), strokeWidth = 1.dp.toPx(), pathEffect = dash)
                val text = measurer.measure(label, labelStyle)
                val left = (x - text.size.width / 2).coerceIn(g.left, size.width - text.size.width)
                if (left > lastRight + 8.dp.toPx()) {
                    drawText(text, topLeft = Offset(left, g.bottom + 6.dp.toPx()))
                    lastRight = left + text.size.width
                }
            }

            // The days before, as small dots in their status's shape and colour.
            chart.others.forEach { other ->
                drawPoint(
                    Offset(g.xOf(other.minute), g.yOf(other.point)),
                    pointShape(other.point.status),
                    pointColor(other.point.status, status).copy(alpha = 0.45f),
                    4.dp.toPx(),
                    haloColor = Color.Transparent,
                )
            }

            if (chart.points.size > 1) {
                val path = Path().apply {
                    moveTo(g.xOf(chart.points[0].minute), g.yOf(chart.points[0].point))
                    chart.points.drop(1).forEach { lineTo(g.xOf(it.minute), g.yOf(it.point)) }
                }
                drawPath(path, colors.onSurface.copy(alpha = 0.6f), style = Stroke(width = 3.dp.toPx()))
            }
        }

        // The day's values, each centred on its time and level, and kept inside the chart.
        Layout(
            content = { chart.entries.forEach { entry -> ValueBubble(entry, onOpen = { onOpen(entry) }) } },
            modifier = Modifier.matchParentSize(),
        ) { measurables, constraints ->
            val placeables = measurables.map { it.measure(Constraints()) }
            layout(constraints.maxWidth, constraints.maxHeight) {
                placeables.forEachIndexed { index, placeable ->
                    val at = chart.points.getOrNull(index) ?: return@forEachIndexed
                    val x = (geometry.xOf(at.minute) - placeable.width / 2f).roundToInt().coerceIn(0, maxOf(0, constraints.maxWidth - placeable.width))
                    val y = (geometry.yOf(at.point) - placeable.height / 2f).roundToInt().coerceIn(0, maxOf(0, constraints.maxHeight - placeable.height))
                    placeable.place(x, y)
                }
            }
        }
    }
}

/**
 * One of the day's readings as a value in a coloured bubble, with its status icon, like the pills in the list.
 * A small medicine badge shows that insulin was given with it.
 */
@Composable
private fun ValueBubble(entry: CheckEntry, onOpen: () -> Unit) {
    val reading = entry.reading ?: return
    val status = entry.status ?: return
    val (background, content, icon) = statusStyle(status)
    val description = checkDescription(entry)
    val actionLabel = stringResource(R.string.logbook_show_details)
    val insulin = entry.doses.any { it.medicineKind == MedicineKind.INSULIN && it.status == DoseStatus.GIVEN }
    // The values grow with the text size only so far, so the day's values do not cover each other.
    val density = LocalDensity.current
    CompositionLocalProvider(LocalDensity provides Density(density.density, density.fontScale.coerceAtMost(VALUE_MAX_FONT_SCALE))) {
        Box(
            Modifier.clearAndSetSemantics {
                contentDescription = description
                role = Role.Button
                testTag = "chart_value_${reading.id}"
                onClick(label = actionLabel) {
                    onOpen()
                    true
                }
            },
        ) {
            Surface(
                onClick = onOpen,
                color = background,
                contentColor = content,
                shape = RoundedCornerShape(50),
                border = BorderStroke(2.dp, MaterialTheme.colorScheme.surface),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 8.dp, end = 12.dp, top = 4.dp, bottom = 4.dp)) {
                    Icon(painterResource(icon), contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(
                        reading.meterFlag?.let { stringResource(it.wordRes()) } ?: Measures.formatTenths(reading.tenths),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
            if (insulin) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surface,
                    contentColor = MaterialTheme.colorScheme.onSurface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    modifier = Modifier.align(Alignment.BottomEnd).offset(x = 4.dp, y = 2.dp).size(24.dp),
                ) {
                    Icon(painterResource(R.drawable.ic_medication), contentDescription = null, modifier = Modifier.padding(3.dp))
                }
            }
        }
    }
}

/** A reading's details, shown when its value on the chart is pressed. */
@Composable
private fun CheckDetailsDialog(entry: CheckEntry, onDismiss: () -> Unit) {
    val context = LocalContext.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                stringResource(R.string.day_chart_detail_title, UiFormat.time(context, entry.takenAt), entry.slot.label()),
                style = MaterialTheme.typography.headlineSmall,
            )
        },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                val reading = entry.reading
                val status = entry.status
                if (reading != null && status != null) ReadingPill(reading.tenths, reading.meterFlag, status)
                CheckFacts(entry)
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.heightIn(min = 56.dp)) {
                Text(stringResource(R.string.close), style = MaterialTheme.typography.labelLarge)
            }
        },
    )
}

/** Every 3 hours on the clock through the day that starts [dayStartMinute] after midnight, as minutes into that day. */
private fun hourTicks(dayStartMinute: Int): List<Pair<Int, LocalTime>> {
    val first = (TICK_MINUTES - dayStartMinute % TICK_MINUTES) % TICK_MINUTES
    return (first until DayCharts.MINUTES_PER_DAY step TICK_MINUTES).map { minute ->
        minute to LocalTime.ofSecondOfDay(((dayStartMinute + minute) % DayCharts.MINUTES_PER_DAY) * 60L)
    }
}

/** Where the plot sits in the chart, shared by the drawing and the values placed over it. */
private class DayGeometry(
    val left: Float,
    val right: Float,
    val top: Float,
    val bottom: Float,
    private val topTenths: Int,
    val levelTicks: List<Int>,
    private val lowEdge: Float,
) {
    fun xOf(minute: Int) = left + (right - left) * minute / DayCharts.MINUTES_PER_DAY.toFloat()

    fun yOf(tenths: Int) = bottom - (bottom - top) * (tenths.coerceIn(0, topTenths).toFloat() / topTenths)

    /**
     * LO and HI sit at the bottom and top edges rather than stretching the chart to the meter's limits. LO sits a
     * little above the bottom, so its value does not cover the times along the bottom.
     */
    fun yOf(point: TrendPoint) = when (point.meterFlag) {
        MeterFlag.HI -> top
        MeterFlag.LO -> lowEdge
        null -> yOf(point.value)
    }

    companion object {
        fun of(chart: DayChart, width: Float, height: Float, measurer: TextMeasurer, labelStyle: TextStyle, density: Density): DayGeometry {
            val measured = (chart.points + chart.others).filter { it.point.meterFlag == null }.maxOfOrNull { it.point.value } ?: 0
            val topTenths = maxOf(TOP_TENTHS, (measured + 49) / 50 * 50).coerceAtMost(MAX_TENTHS)
            val ticks = (LEVEL_STEP..topTenths step LEVEL_STEP).toList()
            val labelWidth = ticks.maxOf { measurer.measure(Measures.formatTenthsShort(it), labelStyle).size.width }.toFloat()
            val labelHeight = measurer.measure("0", labelStyle).size.height.toFloat()
            return with(density) {
                val bottom = height - labelHeight - 12.dp.toPx()
                DayGeometry(
                    left = labelWidth + 14.dp.toPx(),
                    right = width - 20.dp.toPx(),
                    top = 26.dp.toPx(),
                    bottom = bottom,
                    topTenths = topTenths,
                    levelTicks = ticks,
                    lowEdge = bottom - LOW_LIFT.toPx(),
                )
            }
        }
    }
}

private val KEY_MIN_HEIGHT = 400.dp
private val LOW_LIFT = 22.dp
private const val SHORT_DATE_FONT_SCALE = 1.5f
private const val VALUE_MAX_FONT_SCALE = 1.3f
private const val KEY_MAX_FONT_SCALE = 1.5f
/** Sliding a finger across this share of the chart's width moves it by one day. */
private const val DRAG_STEPS = 5f
private const val TICK_MINUTES = 180
private const val LEVEL_STEP = 50
private const val TOP_TENTHS = 200
private const val MAX_TENTHS = 350

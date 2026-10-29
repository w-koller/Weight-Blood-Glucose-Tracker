package io.github.wkoller.glucosetracker.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.CollectionInfo
import androidx.compose.ui.semantics.CollectionItemInfo
import androidx.compose.ui.semantics.collectionInfo
import androidx.compose.ui.semantics.collectionItemInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.traversalIndex
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import io.github.wkoller.glucosetracker.domain.dosing.GlucoseStatus
import io.github.wkoller.glucosetracker.domain.progress.Measure
import io.github.wkoller.glucosetracker.domain.progress.TrendDay
import io.github.wkoller.glucosetracker.domain.progress.TrendSeries
import io.github.wkoller.glucosetracker.ui.theme.LocalStatusColors
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.roundToInt

/** How a point is drawn. The shape shows a reading's status as well as its colour does. */
enum class PointShape { CIRCLE, SQUARE, TRIANGLE }

/** Round for in the target range, square for above or below it, and a triangle pointing down for low. */
fun pointShape(status: GlucoseStatus?): PointShape = when (status) {
    GlucoseStatus.LOW -> PointShape.TRIANGLE
    GlucoseStatus.BELOW_TARGET, GlucoseStatus.ABOVE_TARGET -> PointShape.SQUARE
    GlucoseStatus.IN_TARGET, null -> PointShape.CIRCLE
}

/** What one stored value is in the chart's unit: tenths of mmol/L to mmol/L, grams to kilograms. */
private val Measure.divisor: Float
    get() = when (this) {
        Measure.GLUCOSE -> 10f
        Measure.WEIGHT -> 1000f
    }

/**
 * A simple chart drawn by the app rather than a charting library, so the labels can be as large as
 * the rest of the text. TalkBack reads [summary] for the whole chart, then each day in date order
 * from [describeDay]. Touching the chart over a day reads that day.
 */
@Composable
fun TrendChart(
    series: TrendSeries,
    summary: String,
    describeDay: (TrendDay) -> String,
    yFormat: (Float) -> String,
    xFormat: (Long) -> String,
    modifier: Modifier = Modifier,
    goalLabel: String? = null,
) {
    val measurer = rememberTextMeasurer()
    val labelStyle = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurface)
    val lineColor = MaterialTheme.colorScheme.primary
    val gridColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
    val goalColor = MaterialTheme.colorScheme.secondary
    val status = LocalStatusColors.current
    val bandColor = status.inTarget.copy(alpha = 0.15f)
    val divisor = series.measure.divisor
    val days = series.days

    BoxWithConstraints(
        modifier
            .fillMaxWidth()
            .height(CHART_HEIGHT)
            .semantics {
                isTraversalGroup = true
                collectionInfo = CollectionInfo(rowCount = days.size, columnCount = 1)
            },
    ) {
        val density = LocalDensity.current
        val geometry = ChartGeometry.of(series, constraints.maxWidth.toFloat(), constraints.maxHeight.toFloat(), measurer, labelStyle, yFormat, density)

        Canvas(
            Modifier
                .matchParentSize()
                .semantics {
                    contentDescription = summary
                    traversalIndex = -1f
                },
        ) {
            geometry ?: return@Canvas
            val g = geometry
            val targetLow = series.targetLow
            val targetHigh = series.targetHigh
            if (targetLow != null && targetHigh != null) {
                val high = g.yOf(targetHigh / divisor)
                drawRect(bandColor, topLeft = Offset(g.left, high), size = Size(g.right - g.left, g.yOf(targetLow / divisor) - high))
            }
            g.ticks.forEach { tick ->
                val y = g.yOf(tick)
                drawLine(gridColor, Offset(g.left, y), Offset(g.right, y), strokeWidth = 1.dp.toPx())
                val text = measurer.measure(yFormat(tick), labelStyle)
                drawText(text, topLeft = Offset(g.left - 8.dp.toPx() - text.size.width, y - text.size.height / 2))
            }
            val dash = PathEffect.dashPathEffect(floatArrayOf(14.dp.toPx(), 8.dp.toPx()))
            series.lowLine?.let { drawLine(status.low, Offset(g.left, g.yOf(it / divisor)), Offset(g.right, g.yOf(it / divisor)), strokeWidth = 2.dp.toPx(), pathEffect = dash) }
            series.goal?.let { goal ->
                val y = g.yOf(goal / divisor)
                drawLine(goalColor, Offset(g.left, y), Offset(g.right, y), strokeWidth = 3.dp.toPx(), pathEffect = dash)
                goalLabel?.let { label ->
                    val text = measurer.measure(label, labelStyle.copy(color = goalColor))
                    drawText(text, topLeft = Offset(g.right - text.size.width, y - text.size.height - 2.dp.toPx()))
                }
            }

            val points = series.points
            if (series.measure == Measure.WEIGHT && points.size > 1) {
                val path = Path().apply {
                    moveTo(g.xOf(points[0].takenAt.toEpochMilli()), g.yOf(points[0].value / divisor))
                    points.drop(1).forEach { lineTo(g.xOf(it.takenAt.toEpochMilli()), g.yOf(it.value / divisor)) }
                }
                drawPath(path, lineColor, style = Stroke(width = 3.dp.toPx()))
            }
            points.forEach { point ->
                val color = when (point.status) {
                    null -> lineColor
                    GlucoseStatus.LOW -> status.low
                    GlucoseStatus.IN_TARGET -> status.inTarget
                    GlucoseStatus.BELOW_TARGET, GlucoseStatus.ABOVE_TARGET -> status.above
                }
                drawPoint(Offset(g.xOf(point.takenAt.toEpochMilli()), g.yOf(point.value / divisor)), pointShape(point.status), color, 6.dp.toPx())
            }

            val firstLabel = measurer.measure(xFormat(g.xMin), labelStyle)
            val lastLabel = measurer.measure(xFormat(g.xMax), labelStyle)
            drawText(firstLabel, topLeft = Offset(g.left, g.bottom + 6.dp.toPx()))
            if (abs(g.xMax - g.xMin) > HALF_DAY * 2) {
                drawText(lastLabel, topLeft = Offset(g.right - lastLabel.size.width, g.bottom + 6.dp.toPx()))
            }
        }

        // One strip per day, from halfway to the day before to halfway to the day after. They draw nothing.
        geometry?.let { g ->
            val firsts = days.map { g.xOf(it.points.first().takenAt.toEpochMilli()) }
            val lasts = days.map { g.xOf(it.points.last().takenAt.toEpochMilli()) }
            days.forEachIndexed { index, day ->
                val start = if (index == 0) g.left else (lasts[index - 1] + firsts[index]) / 2
                val end = if (index == days.lastIndex) g.right else (lasts[index] + firsts[index + 1]) / 2
                val description = describeDay(day)
                Box(
                    Modifier
                        .offset { IntOffset(start.roundToInt(), 0) }
                        .width(with(density) { (end - start).coerceAtLeast(1f).toDp() })
                        .fillMaxHeight()
                        .semantics {
                            contentDescription = description
                            collectionItemInfo = CollectionItemInfo(rowIndex = index, rowSpan = 1, columnIndex = 0, columnSpan = 1)
                        },
                )
            }
        }
    }
}

/** Draws one point in its [shape], with a thin [haloColor] edge so it stands out from lines and bands behind it. */
internal fun DrawScope.drawPoint(center: Offset, shape: PointShape, color: Color, radius: Float, haloColor: Color = Color.White) {
    val halo = 1.dp.toPx()
    when (shape) {
        PointShape.CIRCLE -> {
            drawCircle(haloColor, radius = radius + halo, center = center)
            drawCircle(color, radius = radius, center = center)
        }
        PointShape.SQUARE -> {
            val side = radius * 0.9f
            drawRect(haloColor, topLeft = center - Offset(side + halo, side + halo), size = Size(2 * (side + halo), 2 * (side + halo)))
            drawRect(color, topLeft = center - Offset(side, side), size = Size(2 * side, 2 * side))
        }
        PointShape.TRIANGLE -> {
            drawPath(downTriangle(center, radius * 1.3f + halo * 2), haloColor)
            drawPath(downTriangle(center, radius * 1.3f), color)
        }
    }
}

private fun downTriangle(center: Offset, size: Float) = Path().apply {
    moveTo(center.x - size, center.y - size * 0.75f)
    lineTo(center.x + size, center.y - size * 0.75f)
    lineTo(center.x, center.y + size)
    close()
}

/** Where the plot sits inside the chart, shared by the drawing and the strips TalkBack reads. */
private class ChartGeometry(
    val left: Float,
    val right: Float,
    val top: Float,
    val bottom: Float,
    val xMin: Long,
    val xMax: Long,
    val yMin: Float,
    val yMax: Float,
    val ticks: List<Float>,
) {
    fun xOf(x: Long) = left + (right - left) * ((x - xMin).toFloat() / (xMax - xMin))
    fun yOf(y: Float) = bottom - (bottom - top) * ((y - yMin) / (yMax - yMin))

    companion object {
        fun of(
            series: TrendSeries,
            width: Float,
            height: Float,
            measurer: TextMeasurer,
            labelStyle: TextStyle,
            yFormat: (Float) -> String,
            density: Density,
        ): ChartGeometry? {
            if (series.points.isEmpty()) return null
            val divisor = series.measure.divisor
            val values = (series.points.map { it.value } + listOfNotNull(series.goal, series.lowLine, series.targetLow, series.targetHigh)).map { it / divisor }
            var yMin = values.min()
            var yMax = values.max()
            if (yMax - yMin < 1f) { yMin -= 1f; yMax += 1f }
            val pad = (yMax - yMin) * 0.08f
            yMin -= pad
            yMax += pad
            val step = niceStep((yMax - yMin) / 4f)
            val ticks = generateSequence(ceil(yMin / step) * step) { it + step }.takeWhile { it <= yMax }.toList()

            var xMin = series.points.minOf { it.takenAt.toEpochMilli() }
            var xMax = series.points.maxOf { it.takenAt.toEpochMilli() }
            if (xMax == xMin) { xMin -= HALF_DAY; xMax += HALF_DAY }

            val labelWidth = ticks.maxOfOrNull { measurer.measure(yFormat(it), labelStyle).size.width }?.toFloat() ?: 0f
            val labelHeight = measurer.measure("0", labelStyle).size.height.toFloat()
            return with(density) {
                ChartGeometry(
                    left = labelWidth + 12.dp.toPx(),
                    right = width - 12.dp.toPx(),
                    top = labelHeight / 2,
                    bottom = height - labelHeight - 10.dp.toPx(),
                    xMin = xMin,
                    xMax = xMax,
                    yMin = yMin,
                    yMax = yMax,
                    ticks = ticks,
                )
            }
        }
    }
}

private val CHART_HEIGHT = 280.dp
private const val HALF_DAY = 12L * 60 * 60 * 1000

private fun niceStep(raw: Float): Float {
    if (raw <= 0f) return 1f
    val magnitude = 10.0.pow(floor(log10(raw.toDouble()))).toFloat()
    val fraction = raw / magnitude
    val nice = when {
        fraction <= 1f -> 1f
        fraction <= 2f -> 2f
        fraction <= 5f -> 5f
        else -> 10f
    }
    return nice * magnitude
}

package io.github.wkoller.glucosetracker.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.pow

data class ChartPoint(val x: Long, val y: Float, val color: Color? = null)

/**
 * A simple chart drawn by the app rather than a charting library, so the labels can be as large as
 * the rest of the text. The [description] is read out by TalkBack in place of the picture.
 */
@Composable
fun TrendChart(
    points: List<ChartPoint>,
    description: String,
    yFormat: (Float) -> String,
    xFormat: (Long) -> String,
    modifier: Modifier = Modifier,
    connect: Boolean = true,
    goal: Float? = null,
    goalLabel: String? = null,
    band: ClosedFloatingPointRange<Float>? = null,
    lowLine: Float? = null,
) {
    val measurer = rememberTextMeasurer()
    val labelStyle = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurface)
    val lineColor = MaterialTheme.colorScheme.primary
    val gridColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
    val goalColor = MaterialTheme.colorScheme.secondary
    val status = io.github.wkoller.glucosetracker.ui.theme.LocalStatusColors.current
    val bandColor = status.inTarget.copy(alpha = 0.15f)
    val lowColor = status.low

    Canvas(
        modifier
            .fillMaxWidth()
            .height(280.dp)
            .semantics { contentDescription = description },
    ) {
        if (points.isEmpty()) return@Canvas
        val values = points.map { it.y } + listOfNotNull(goal, lowLine, band?.start, band?.endInclusive)
        var yMin = values.min()
        var yMax = values.max()
        if (yMax - yMin < 1f) { yMin -= 1f; yMax += 1f }
        val pad = (yMax - yMin) * 0.08f
        yMin -= pad
        yMax += pad
        val step = niceStep((yMax - yMin) / 4f)
        val ticks = generateSequence(ceil(yMin / step) * step) { it + step }.takeWhile { it <= yMax }.toList()

        var xMin = points.minOf { it.x }
        var xMax = points.maxOf { it.x }
        if (xMax == xMin) { xMin -= HALF_DAY; xMax += HALF_DAY }

        val labelWidth = ticks.maxOf { measurer.measure(yFormat(it), labelStyle).size.width }.toFloat()
        val labelHeight = measurer.measure("0", labelStyle).size.height.toFloat()
        val left = labelWidth + 12.dp.toPx()
        val right = size.width - 12.dp.toPx()
        val top = labelHeight / 2
        val bottom = size.height - labelHeight - 10.dp.toPx()

        fun xOf(x: Long) = left + (right - left) * ((x - xMin).toFloat() / (xMax - xMin))
        fun yOf(y: Float) = bottom - (bottom - top) * ((y - yMin) / (yMax - yMin))

        band?.let {
            drawRect(bandColor, topLeft = Offset(left, yOf(it.endInclusive)), size = Size(right - left, yOf(it.start) - yOf(it.endInclusive)))
        }
        ticks.forEach { tick ->
            val y = yOf(tick)
            drawLine(gridColor, Offset(left, y), Offset(right, y), strokeWidth = 1.dp.toPx())
            val text = measurer.measure(yFormat(tick), labelStyle)
            drawText(text, topLeft = Offset(left - 8.dp.toPx() - text.size.width, y - text.size.height / 2))
        }
        val dash = PathEffect.dashPathEffect(floatArrayOf(14.dp.toPx(), 8.dp.toPx()))
        lowLine?.let { drawLine(lowColor, Offset(left, yOf(it)), Offset(right, yOf(it)), strokeWidth = 2.dp.toPx(), pathEffect = dash) }
        goal?.let {
            drawLine(goalColor, Offset(left, yOf(it)), Offset(right, yOf(it)), strokeWidth = 3.dp.toPx(), pathEffect = dash)
            goalLabel?.let { label ->
                val text = measurer.measure(label, labelStyle.copy(color = goalColor))
                drawText(text, topLeft = Offset(right - text.size.width, yOf(it) - text.size.height - 2.dp.toPx()))
            }
        }

        val sorted = points.sortedBy { it.x }
        if (connect && sorted.size > 1) {
            val path = Path().apply {
                moveTo(xOf(sorted[0].x), yOf(sorted[0].y))
                sorted.drop(1).forEach { lineTo(xOf(it.x), yOf(it.y)) }
            }
            drawPath(path, lineColor, style = Stroke(width = 3.dp.toPx()))
        }
        sorted.forEach { point ->
            val center = Offset(xOf(point.x), yOf(point.y))
            drawCircle(Color.White, radius = 7.dp.toPx(), center = center)
            drawCircle(point.color ?: lineColor, radius = 6.dp.toPx(), center = center)
        }

        val firstLabel = measurer.measure(xFormat(xMin), labelStyle)
        val lastLabel = measurer.measure(xFormat(xMax), labelStyle)
        drawText(firstLabel, topLeft = Offset(left, bottom + 6.dp.toPx()))
        if (abs(xMax - xMin) > HALF_DAY * 2) {
            drawText(lastLabel, topLeft = Offset(right - lastLabel.size.width, bottom + 6.dp.toPx()))
        }
    }
}

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

package io.github.wkoller.glucosetracker.export

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import io.github.wkoller.glucosetracker.R
import io.github.wkoller.glucosetracker.domain.dosing.GlucoseStatus
import io.github.wkoller.glucosetracker.domain.format.Measures
import io.github.wkoller.glucosetracker.domain.model.DoseKind
import io.github.wkoller.glucosetracker.domain.model.DoseStatus
import io.github.wkoller.glucosetracker.domain.model.MedicineKind
import io.github.wkoller.glucosetracker.domain.model.MeterFlag
import io.github.wkoller.glucosetracker.domain.model.Plan
import io.github.wkoller.glucosetracker.domain.model.Slot
import io.github.wkoller.glucosetracker.domain.model.SlotNaming
import io.github.wkoller.glucosetracker.domain.progress.ProgressModelBuilder
import io.github.wkoller.glucosetracker.domain.progress.TrendSeries
import io.github.wkoller.glucosetracker.domain.records.DayLog
import io.github.wkoller.glucosetracker.domain.records.SlotEntry
import io.github.wkoller.glucosetracker.ui.UiFormat
import io.github.wkoller.glucosetracker.ui.components.PointShape
import io.github.wkoller.glucosetracker.ui.components.labelRes
import io.github.wkoller.glucosetracker.ui.components.nameRes
import io.github.wkoller.glucosetracker.ui.components.phraseRes
import io.github.wkoller.glucosetracker.ui.components.pointShape
import java.io.File
import java.time.LocalDate
import kotlin.math.max

/** Where report pages are drawn: a PDF file in the app, or images in tests. */
interface PageSink {
    fun begin(number: Int, width: Int, height: Int): Canvas
    fun end()
}

private class PdfSink(private val document: PdfDocument) : PageSink {
    private var page: PdfDocument.Page? = null

    override fun begin(number: Int, width: Int, height: Int): Canvas =
        document.startPage(PdfDocument.PageInfo.Builder(width, height, number).create()).also { page = it }.canvas

    override fun end() {
        page?.let(document::finishPage)
        page = null
    }
}

private const val CHART_HEIGHT = 170f
private const val POINT_RADIUS = 2.6f

/** A printable A4 report for the doctor: the plan, a summary, a glucose chart and the daily records. */
class PdfReportWriter(private val context: Context, private val naming: SlotNaming = SlotNaming.MEALS) {

    fun write(file: File, plan: Plan, from: LocalDate, to: LocalDate, days: List<DayLog>, preparedOn: LocalDate, patientName: String? = null) {
        val document = PdfDocument()
        render(PdfSink(document), plan, from, to, days, preparedOn, patientName)
        file.outputStream().use { document.writeTo(it) }
        document.close()
    }

    fun render(sink: PageSink, plan: Plan, from: LocalDate, to: LocalDate, days: List<DayLog>, preparedOn: LocalDate, patientName: String? = null) {
        val writer = PageWriter(sink, context.getString(R.string.report_footer))
        val readings = days.flatMap { day -> day.slots.flatMap { it.readings } }
        val doses = days.flatMap { day -> day.slots.flatMap { it.doses } }
        val measurements = days.flatMap { it.measurements }
        val model = ProgressModelBuilder.build(plan, from, to, readings, doses, measurements)

        writer.text(context.getString(R.string.report_title), writer.title)
        patientName?.let { writer.text(context.getString(R.string.report_prepared_for, it), writer.heading) }
        writer.text(context.getString(R.string.report_period, UiFormat.dayLongYear(from), UiFormat.dayLongYear(to)), writer.body)
        writer.text(context.getString(R.string.report_prepared, UiFormat.dayLongYear(preparedOn)), writer.body)
        writer.gap()

        writer.text(context.getString(R.string.report_plan_heading), writer.heading)
        writer.text(context.getString(R.string.report_insulin, plan.correctionInsulin), writer.body)
        plan.orderDate?.let { writer.text(context.getString(R.string.report_order_date, UiFormat.dateNumeric(it)), writer.body) }
        writer.table(
            listOf(0.5f, 0.5f),
            listOf(context.getString(R.string.scale_header_bgl), context.getString(R.string.scale_header_units, plan.correctionInsulin)),
            plan.scale.bands.indices.map { index ->
                val band = plan.scale.bands[index]
                val lower = plan.scale.lowerTenths(index)
                val range = band.upperTenths?.let { context.getString(R.string.scale_range, Measures.formatTenthsShort(lower), Measures.formatTenthsShort(it)) }
                    ?: context.getString(R.string.scale_above, Measures.formatTenthsShort(lower - 1))
                listOf(range, UiFormat.units(context, band.units.toDouble()))
            },
        )
        val prescribed = plan.slots.filter { it.correctionPrescribed }.map { context.getString(it.slot.phraseRes(naming)) }
        writer.text(context.getString(R.string.report_prescribed_times, plan.correctionInsulin, UiFormat.joinWithAnd(context, prescribed).ifBlank { context.getString(R.string.not_set) }), writer.body)
        plan.medicines.forEach { medicine ->
            val times = medicine.doses.entries.joinToString(", ") { (slot, amount) ->
                context.getString(R.string.report_medicine_time, UiFormat.dose(context, amount, medicine.kind, medicine.unit), context.getString(slot.phraseRes(naming)))
            }
            writer.text(context.getString(R.string.report_medicine, medicine.displayName, times), writer.body)
        }
        writer.text(
            context.getString(
                R.string.report_targets,
                Measures.formatTenths(plan.targets.lowTenths),
                Measures.formatTenths(plan.targets.highTenths),
                Measures.formatTenths(plan.targets.hypoTenths),
            ),
            writer.body,
        )
        writer.gap()

        writer.text(context.getString(R.string.report_summary_heading), writer.heading)
        val glucose = model.glucose
        if (glucose.count == 0) {
            writer.text(context.getString(R.string.progress_no_readings), writer.body)
        } else {
            writer.text(context.getString(R.string.report_glucose_counts, UiFormat.readings(context, glucose.count), glucose.inTargetCount, glucose.lowCount, glucose.aboveTargetCount), writer.body)
            glucose.averageTenths?.let { writer.text(context.getString(R.string.report_average, Measures.formatTenths(it)), writer.body) }
            glucose.perSlot.forEach { slot ->
                writer.text(
                    context.getString(R.string.report_slot_average, context.getString(slot.slot.nameRes(naming)), Measures.formatTenths(slot.averageTenths ?: 0), UiFormat.readings(context, slot.count)),
                    writer.body,
                )
            }
        }
        val weight = model.weight
        weight.current?.let { current ->
            val start = weight.start?.let { Measures.formatKg(it) } ?: context.getString(R.string.not_set)
            val goal = weight.goal?.let { Measures.formatKg(it) } ?: context.getString(R.string.not_set)
            writer.text(context.getString(R.string.report_weight, Measures.formatKg(current), start, goal), writer.body)
        }
        model.doses.forEach { summary ->
            val given = UiFormat.times(context, summary.givenCount)
            val skipped = UiFormat.times(context, summary.skippedCount)
            val text = if (summary.medicineKind == MedicineKind.INSULIN) {
                context.getString(R.string.report_doses, summary.medicineName, given, UiFormat.dose(context, summary.totalGiven, summary.medicineKind, summary.unit), skipped)
            } else {
                context.getString(R.string.report_doses_fixed, summary.medicineName, given, skipped)
            }
            writer.text(text, writer.body)
        }
        if (model.glucoseSeries.points.size >= 2) {
            writer.gap()
            // Keeps the heading on the same page as its chart.
            writer.keepTogether(CHART_HEIGHT + 60f)
            writer.text(context.getString(R.string.report_chart_heading), writer.heading)
            writer.chart(model.glucoseSeries, from, to)
            writer.text(context.getString(R.string.report_chart_key), writer.small)
        }
        writer.gap()

        writer.keepTogether(80f)
        writer.text(context.getString(R.string.report_daily_heading), writer.heading)
        val slots = Slot.scheduled + if (days.any { it.slot(Slot.OTHER) != null }) listOf(Slot.OTHER) else emptyList()
        val dateShare = 0.13f
        val weightShare = 0.1f
        val slotShare = (1f - dateShare - weightShare) / slots.size
        writer.table(
            listOf(dateShare) + slots.map { slotShare } + listOf(weightShare),
            listOf(context.getString(R.string.export_col_date)) + slots.map { context.getString(it.nameRes(naming)) } + listOf(context.getString(R.string.export_col_weight)),
            days.sortedBy { it.date }.map { day ->
                listOf(UiFormat.dayShort(day.date)) + slots.map { cell(day.slot(it)) } + listOf(day.weightGrams?.let(Measures::formatKg) ?: "")
            },
            small = true,
        )

        val notes = days.sortedBy { it.date }.flatMap { day ->
            day.slots.flatMap { entry ->
                entry.readings.mapNotNull { it.note } + entry.doses.mapNotNull { it.note }
            }.map { note -> day.date to note } + day.measurements.mapNotNull { it.note }.map { day.date to it }
        }
        if (notes.isNotEmpty()) {
            writer.gap()
            writer.keepTogether(60f)
            writer.text(context.getString(R.string.report_notes_heading), writer.heading)
            notes.forEach { (date, note) -> writer.text(context.getString(R.string.report_note, UiFormat.dayShort(date), note), writer.body) }
        }

        writer.finish()
    }

    private fun cell(entry: SlotEntry?): String {
        entry ?: return ""
        val lines = mutableListOf<String>()
        entry.readings.forEach { reading ->
            lines += when (reading.meterFlag) {
                MeterFlag.LO -> "BGL LO"
                MeterFlag.HI -> "BGL HI"
                null -> "BGL ${Measures.formatTenths(reading.tenths)}"
            }
        }
        entry.doses.filter { it.medicineKind == MedicineKind.INSULIN }.forEach { dose ->
            lines += when (dose.status) {
                DoseStatus.GIVEN -> "${dose.medicineName} ${Measures.formatAmount(dose.givenAmount ?: 0.0)}"
                DoseStatus.NOT_NEEDED -> "${dose.medicineName} 0"
                DoseStatus.SKIPPED -> dose.reason?.let { context.getString(R.string.logbook_dose_skipped_reason, dose.medicineName, context.getString(it.labelRes())) }
                    ?: context.getString(R.string.logbook_dose_skipped, dose.medicineName)
            }
        }
        val others = entry.doses.filter { it.kind == DoseKind.FIXED && it.medicineKind != MedicineKind.INSULIN }
        if (others.isNotEmpty()) {
            lines += context.getString(R.string.report_tablets, others.count { it.status == DoseStatus.GIVEN }, others.size)
        }
        return lines.joinToString("\n")
    }

    /** Lays text and tables down the page, starting a new page when one is full. */
    private class PageWriter(private val sink: PageSink, private val footer: String) {
        val width = 595
        val height = 842
        private val margin = 40f
        private val contentWidth = width - 2 * margin
        private val bottom = height - margin - 24f
        private var pageNumber = 0
        private var open = false
        private lateinit var canvas: Canvas
        private var y = margin

        val title = paint(18f, bold = true)
        val heading = paint(13f, bold = true)
        val body = paint(10f)
        val small = paint(8.5f)
        private val smallBold = paint(8.5f, bold = true)
        private val bodyBold = paint(10f, bold = true)
        private val line = Paint().apply { color = Color.rgb(120, 120, 120); strokeWidth = 0.6f }

        init {
            newPage()
        }

        private fun paint(size: Float, bold: Boolean = false) = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = size
            color = Color.rgb(20, 20, 20)
            typeface = if (bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
        }

        private fun newPage() {
            if (open) finishPage()
            pageNumber++
            canvas = sink.begin(pageNumber, width, height)
            open = true
            y = margin
        }

        private fun finishPage() {
            val footerLayout = layout("$footer  ·  $pageNumber", small, contentWidth.toInt())
            canvas.save()
            canvas.translate(margin, height - margin)
            footerLayout.draw(canvas)
            canvas.restore()
            sink.end()
            open = false
        }

        fun finish() {
            if (open) finishPage()
        }

        fun gap() {
            y += 10f
        }

        fun keepTogether(space: Float) = ensure(space)

        private fun ensure(space: Float) {
            if (y + space > bottom) newPage()
        }

        private fun layout(text: String, paint: TextPaint, width: Int): StaticLayout =
            StaticLayout.Builder.obtain(text, 0, text.length, paint, width).setAlignment(Layout.Alignment.ALIGN_NORMAL).build()

        fun text(text: String, paint: TextPaint) {
            val layout = layout(text, paint, contentWidth.toInt())
            ensure(layout.height.toFloat())
            canvas.save()
            canvas.translate(margin, y)
            layout.draw(canvas)
            canvas.restore()
            y += layout.height + 4f
        }

        fun table(shares: List<Float>, header: List<String>, rows: List<List<String>>, small: Boolean = false) {
            val textPaint = if (small) this.small else body
            val headerPaint = if (small) smallBold else bodyBold
            val widths = shares.map { it * contentWidth }
            fun drawRow(cells: List<String>, paint: TextPaint) {
                val layouts = cells.mapIndexed { i, text -> layout(text, paint, (widths[i] - 6f).toInt().coerceAtLeast(10)) }
                val rowHeight = max(layouts.maxOf { it.height }.toFloat(), paint.textSize) + 6f
                ensure(rowHeight)
                var x = margin
                layouts.forEachIndexed { i, layout ->
                    canvas.save()
                    canvas.translate(x + 3f, y + 3f)
                    layout.draw(canvas)
                    canvas.restore()
                    x += widths[i]
                }
                y += rowHeight
                canvas.drawLine(margin, y, margin + contentWidth, y, line)
            }
            drawRow(header, headerPaint)
            rows.forEach { drawRow(it, textPaint) }
            y += 6f
        }

        fun chart(series: TrendSeries, from: LocalDate, to: LocalDate) {
            val chartHeight = CHART_HEIGHT
            ensure(chartHeight + 20f)
            val left = margin + 24f
            val right = margin + contentWidth
            val top = y
            val bottomY = y + chartHeight
            val zone = java.time.ZoneId.systemDefault()
            val xMin = from.atStartOfDay(zone).toInstant().toEpochMilli()
            val xMax = to.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
            val yMax = max(series.points.maxOf { it.value }, series.targetHigh ?: 0) / 10f + 2f
            fun xOf(x: Long) = left + (right - left) * ((x - xMin).toFloat() / (xMax - xMin))
            fun yOf(v: Float) = bottomY - (bottomY - top) * (v / yMax)
            val band = Paint().apply { color = Color.argb(40, 11, 87, 208) }
            val targetLow = series.targetLow
            val targetHigh = series.targetHigh
            if (targetLow != null && targetHigh != null) canvas.drawRect(left, yOf(targetHigh / 10f), right, yOf(targetLow / 10f), band)
            val axis = Paint().apply { color = Color.rgb(90, 90, 90); strokeWidth = 0.8f }
            canvas.drawLine(left, top, left, bottomY, axis)
            canvas.drawLine(left, bottomY, right, bottomY, axis)
            var tick = 0f
            while (tick <= yMax) {
                canvas.drawText(tick.toInt().toString(), margin, yOf(tick) + 3f, small)
                canvas.drawLine(left, yOf(tick), right, yOf(tick), line.apply { alpha = 60 })
                tick += 5f
            }
            line.alpha = 255
            val low = Paint().apply { color = Color.rgb(164, 22, 26); strokeWidth = 1f; pathEffect = DashPathEffect(floatArrayOf(5f, 4f), 0f) }
            series.lowLine?.let { canvas.drawLine(left, yOf(it / 10f), right, yOf(it / 10f), low) }
            series.points.forEach { point ->
                val dot = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = when (point.status) {
                        GlucoseStatus.LOW -> Color.rgb(164, 22, 26)
                        GlucoseStatus.IN_TARGET, null -> Color.rgb(11, 87, 208)
                        GlucoseStatus.BELOW_TARGET, GlucoseStatus.ABOVE_TARGET -> Color.rgb(138, 67, 0)
                    }
                }
                drawPoint(xOf(point.takenAt.toEpochMilli()), yOf(point.value / 10f), pointShape(point.status), dot)
            }
            canvas.drawText(UiFormat.dayShort(from), left, bottomY + 11f, small)
            val endLabel = UiFormat.dayShort(to)
            canvas.drawText(endLabel, right - small.measureText(endLabel), bottomY + 11f, small)
            y = bottomY + 18f
        }

        /** The same shapes as the chart in the app, so the status does not rely on colour. */
        private fun drawPoint(x: Float, y: Float, shape: PointShape, paint: Paint) {
            val r = POINT_RADIUS
            when (shape) {
                PointShape.CIRCLE -> canvas.drawCircle(x, y, r, paint)
                PointShape.SQUARE -> canvas.drawRect(x - r * 0.9f, y - r * 0.9f, x + r * 0.9f, y + r * 0.9f, paint)
                PointShape.TRIANGLE -> {
                    val size = r * 1.3f
                    val path = android.graphics.Path().apply {
                        moveTo(x - size, y - size * 0.75f)
                        lineTo(x + size, y - size * 0.75f)
                        lineTo(x, y + size)
                        close()
                    }
                    canvas.drawPath(path, paint)
                }
            }
        }
    }
}

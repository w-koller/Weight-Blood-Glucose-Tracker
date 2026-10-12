package io.github.wkoller.glucosetracker

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Canvas
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.wkoller.glucosetracker.domain.model.BodyMeasurement
import io.github.wkoller.glucosetracker.domain.model.DoseKind
import io.github.wkoller.glucosetracker.domain.model.DoseRecord
import io.github.wkoller.glucosetracker.domain.model.DoseStatus
import io.github.wkoller.glucosetracker.domain.model.GlucoseReading
import io.github.wkoller.glucosetracker.domain.model.MedicineKind
import io.github.wkoller.glucosetracker.domain.model.Plan
import io.github.wkoller.glucosetracker.domain.model.SkipReason
import io.github.wkoller.glucosetracker.domain.model.Slot
import io.github.wkoller.glucosetracker.domain.records.DailyLog
import io.github.wkoller.glucosetracker.domain.records.DayLog
import io.github.wkoller.glucosetracker.export.CsvExporter
import io.github.wkoller.glucosetracker.export.PageSink
import io.github.wkoller.glucosetracker.export.PdfReportWriter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.time.LocalDate
import java.time.ZoneId

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ExportTest {
    private val context = ApplicationProvider.getApplicationContext<TrackerApp>()
    private val plan: Plan = TestPlans.example().copy(id = 1)
    private val day1 = LocalDate.of(2026, 10, 9)

    private fun at(date: LocalDate, hour: Int) = date.atTime(hour, 0).atZone(ZoneId.of("Australia/Brisbane")).toInstant()

    private fun days(): List<DayLog> {
        val day2 = day1.plusDays(1)
        val readings = listOf(
            GlucoseReading(1, 1, at(day1, 7), day1, Slot.BREAKFAST, 137, null, null),
            GlucoseReading(2, 1, at(day1, 12), day1, Slot.LUNCH, 185, null, null),
            GlucoseReading(3, 1, at(day1, 21), day1, Slot.BEDTIME, 144, null, null),
            GlucoseReading(4, 1, at(day2, 7), day2, Slot.BREAKFAST, 155, null, null),
            GlucoseReading(5, 1, at(day2, 12), day2, Slot.LUNCH, 182, null, "Didn't have midday meal"),
        )
        fun correction(slot: Slot, date: LocalDate, prescribed: Double, given: Double?, status: DoseStatus, reason: SkipReason? = null) =
            DoseRecord(0, 1, null, at(date, 8), date, slot, DoseKind.CORRECTION, null, "NovoRapid", MedicineKind.INSULIN, prescribed, given, "units", status, reason, null)
        fun fixed(key: String, name: String, kind: MedicineKind, slot: Slot, date: LocalDate, amount: Double, status: DoseStatus = DoseStatus.GIVEN) =
            DoseRecord(0, 1, null, at(date, 8), date, slot, DoseKind.FIXED, key, name, kind, amount, if (status == DoseStatus.GIVEN) amount else null, if (kind == MedicineKind.INSULIN) "units" else "tablet", status, null, null)
        val doses = listOf(
            correction(Slot.BREAKFAST, day1, 4.0, 4.0, DoseStatus.GIVEN),
            fixed("premix", "NovoMix 30", MedicineKind.INSULIN, Slot.BREAKFAST, day1, 30.0),
            fixed("spiro", "Spironolactone 25 mg", MedicineKind.TABLET, Slot.BREAKFAST, day1, 1.0),
            fixed("furo", "Furosemide 40 mg", MedicineKind.TABLET, Slot.BREAKFAST, day1, 1.0, DoseStatus.SKIPPED),
            correction(Slot.LUNCH, day1, 8.0, 8.0, DoseStatus.GIVEN),
            correction(Slot.BEDTIME, day1, 6.0, 6.0, DoseStatus.GIVEN),
            correction(Slot.BREAKFAST, day2, 6.0, 6.0, DoseStatus.GIVEN),
            correction(Slot.LUNCH, day2, 8.0, null, DoseStatus.SKIPPED, SkipReason.NOT_EATING),
        )
        val measurements = listOf(
            BodyMeasurement(1, at(day1, 6), day1, 116_300, 1_150, null),
            BodyMeasurement(2, at(day2, 6), day2, 116_510, null, null),
        )
        return DailyLog.build(readings, doses, measurements)
    }

    @Test
    fun `the spreadsheet has one row per day in date order`() {
        val csv = CsvExporter(context).build(plan, days())
        val lines = csv.removePrefix("\uFEFF").trim().lines()
        assertEquals(3, lines.size)
        val header = lines[0].split(",")
        assertEquals("Date", header[0])
        assertTrue(header.contains("Morning BGL (mmol/L)"))
        assertTrue(header.contains("Morning NovoMix 30 (units)"))
        assertTrue(header.contains("Bedtime NovoRapid (units)"))
        val first = lines[1].split(",")
        assertEquals("9/10/2026", first[0])
        assertEquals("116.3", first[1])
        assertEquals("+1.3", first[2])
        assertEquals("115", first[3])
        assertEquals("13.7", first[header.indexOf("Morning BGL (mmol/L)")])
        assertEquals("4", first[header.indexOf("Morning NovoRapid (units)")])
        assertEquals("30", first[header.indexOf("Morning NovoMix 30 (units)")])
        assertEquals("6", first[header.indexOf("Bedtime NovoRapid (units)")])
        assertTrue(lines[1].contains("Spironolactone 25 mg taken; Furosemide 40 mg not taken"))
        val second = lines[2]
        assertTrue(second.contains("Not given"))
        assertTrue(second.contains("\"Didn't have midday meal\"") || second.contains("Didn't have midday meal"))
        File(System.getProperty("screenshots.dir") ?: "build/screenshots").apply { mkdirs() }.resolve("export.csv").writeText(csv)
    }

    @Test
    fun `the report pages lay out the plan, summary, chart and daily records`() {
        val dir = File(System.getProperty("screenshots.dir") ?: "build/screenshots").apply { mkdirs() }
        var pages = 0
        val sink = object : PageSink {
            private lateinit var bitmap: Bitmap

            override fun begin(number: Int, width: Int, height: Int): Canvas {
                pages = number
                bitmap = Bitmap.createBitmap(width * 2, height * 2, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.WHITE) }
                return Canvas(bitmap).apply { scale(2f, 2f) }
            }

            override fun end() {
                File(dir, "report_page_$pages.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            }
        }
        PdfReportWriter(context).render(sink, plan, day1, day1.plusDays(1), days(), day1.plusDays(2), "Mary Smith")
        assertTrue(pages >= 1)
    }
}

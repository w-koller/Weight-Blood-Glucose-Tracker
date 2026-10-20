package io.github.wkoller.glucosetracker.ui

import android.content.Context
import android.text.format.DateFormat
import io.github.wkoller.glucosetracker.R
import io.github.wkoller.glucosetracker.domain.format.Measures
import io.github.wkoller.glucosetracker.domain.model.MedicineKind
import io.github.wkoller.glucosetracker.domain.model.MeterFlag
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Dates and times in the phone's language, written out in full where there is room. */
object UiFormat {
    private val locale: Locale get() = Locale.getDefault()

    /** "Friday 9 October" */
    fun dayLong(date: LocalDate): String = date.format(DateTimeFormatter.ofPattern("EEEE d MMMM", locale))

    /** "Friday 9 October 2026" */
    fun dayLongYear(date: LocalDate): String = date.format(DateTimeFormatter.ofPattern("EEEE d MMMM yyyy", locale))

    /** "Fri 9 Oct" */
    fun dayShort(date: LocalDate): String = date.format(DateTimeFormatter.ofPattern("EEE d MMM", locale))

    /** "9/10/2026", the Australian short date. */
    fun dateNumeric(date: LocalDate): String = date.format(DateTimeFormatter.ofPattern("d/M/yyyy", locale))

    /** "7:30 am" or "07:30", following the phone's 12- or 24-hour setting. */
    fun time(context: Context, time: LocalTime): String {
        val pattern = if (DateFormat.is24HourFormat(context)) "HH:mm" else "h:mm a"
        return time.format(DateTimeFormatter.ofPattern(pattern, locale)).lowercase(locale)
    }

    fun time(context: Context, instant: Instant): String = time(context, instant.atZone(ZoneId.systemDefault()).toLocalTime())

    /** "4 units", "1 tablet", "0.5 tablets" or "2 puffs" for a medicine amount. */
    fun dose(context: Context, amount: Double, kind: MedicineKind, unit: String): String {
        val number = Measures.formatAmount(amount)
        val count = if (amount == 1.0) 1 else 2
        return when (kind) {
            MedicineKind.INSULIN -> context.resources.getQuantityString(R.plurals.units_plural, count, number)
            MedicineKind.TABLET -> context.resources.getQuantityString(R.plurals.tablets_plural, count, number)
            MedicineKind.OTHER -> "$number $unit"
        }
    }

    fun units(context: Context, amount: Double): String = dose(context, amount, MedicineKind.INSULIN, "")

    /** "13.7 mmol/L", or LO or HI with what it means, when the meter could not measure the level. */
    fun reading(context: Context, tenths: Int, meterFlag: MeterFlag?): String = when (meterFlag) {
        MeterFlag.LO -> context.getString(R.string.checkin_reading_lo)
        MeterFlag.HI -> context.getString(R.string.checkin_reading_hi)
        null -> context.getString(R.string.bgl_amount, Measures.formatTenths(tenths))
    }

    /** "once", "2 times" */
    fun times(context: Context, count: Int): String = context.resources.getQuantityString(R.plurals.times_plural, count, count)

    /** "1 reading", "2 readings" */
    fun readings(context: Context, count: Int): String = context.resources.getQuantityString(R.plurals.readings_plural, count, count)

    /** "a", "a and b", "a, b and c" */
    fun joinWithAnd(context: Context, items: List<String>): String = when (items.size) {
        0 -> ""
        1 -> items[0]
        else -> context.getString(R.string.list_and, items.dropLast(1).joinToString(", "), items.last())
    }
}

package io.github.wkoller.glucosetracker.domain.format

import java.math.BigDecimal
import java.math.RoundingMode
import kotlin.math.abs

/**
 * Values are stored as whole numbers to avoid rounding surprises:
 * glucose in tenths of mmol/L, weight in grams and waist in millimetres.
 */
object Measures {

    fun formatTenths(tenths: Int): String {
        val sign = if (tenths < 0) "-" else ""
        val value = abs(tenths)
        return "$sign${value / 10}.${value % 10}"
    }

    /** 116510 g → "116.51", 116300 g → "116.3", 100000 g → "100". */
    fun formatKg(grams: Int): String = stripZeros(BigDecimal(grams).divide(BigDecimal(1000)).setScale(2, RoundingMode.HALF_UP))

    /** A weight change rounded to one decimal place, without a sign: 1510 g → "1.5". */
    fun formatKgChange(grams: Int): String =
        BigDecimal(abs(grams)).divide(BigDecimal(1000)).setScale(1, RoundingMode.HALF_UP).toPlainString()

    /** 1055 mm → "105.5", 1050 mm → "105". */
    fun formatCm(mm: Int): String = stripZeros(BigDecimal(mm).divide(BigDecimal(10)).setScale(1, RoundingMode.HALF_UP))

    fun formatCmChange(mm: Int): String = formatCm(abs(mm))

    /** 4.0 → "4", 0.5 → "0.5". */
    fun formatAmount(amount: Double): String = stripZeros(BigDecimal.valueOf(amount).setScale(2, RoundingMode.HALF_UP))

    /** Parses a decimal number with at most [maxDecimals] digits after the point into a scaled whole number. */
    fun parseScaled(text: String, maxDecimals: Int): Long? {
        val trimmed = text.trim()
        if (!Regex("""\d{1,4}(\.\d{0,$maxDecimals})?""").matches(trimmed)) return null
        return BigDecimal(trimmed.trimEnd('.')).movePointRight(maxDecimals).longValueExact()
    }

    private fun stripZeros(value: BigDecimal): String {
        val stripped = value.stripTrailingZeros()
        return if (stripped.scale() < 0) stripped.setScale(0).toPlainString() else stripped.toPlainString()
    }
}

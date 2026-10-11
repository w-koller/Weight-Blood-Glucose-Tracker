package io.github.wkoller.glucosetracker.domain.validation

import io.github.wkoller.glucosetracker.domain.format.Measures
import kotlin.math.abs

sealed interface NumberInput<out T> {
    data class Valid<T>(val value: T) : NumberInput<T>
    data object Empty : NumberInput<Nothing>
    data object NotANumber : NumberInput<Nothing>
    data object TooLow : NumberInput<Nothing>
    data object TooHigh : NumberInput<Nothing>
}

object InputValidator {
    /** Glucose meters show readings from 1.1 to 33.3 mmol/L and show LO or HI outside that range. */
    const val METER_MIN_TENTHS = 11
    const val METER_MAX_TENTHS = 333

    const val MIN_WEIGHT_GRAMS = 30_000
    const val MAX_WEIGHT_GRAMS = 300_000
    const val MIN_WAIST_MM = 400
    const val MAX_WAIST_MM = 2_500
    const val MAX_DOSE_UNITS = 50.0

    /** A weight this far from the last one is more likely a typing slip than a real change. */
    const val LARGE_WEIGHT_CHANGE_GRAMS = 2_000

    fun bgl(text: String): NumberInput<Int> = parse(text, decimals = 1) { tenths ->
        when {
            tenths < METER_MIN_TENTHS -> NumberInput.TooLow
            tenths > METER_MAX_TENTHS -> NumberInput.TooHigh
            else -> NumberInput.Valid(tenths.toInt())
        }
    }

    fun weight(text: String): NumberInput<Int> = parse(text, decimals = 2) { hundredths ->
        val grams = (hundredths * 10).toInt()
        when {
            grams < MIN_WEIGHT_GRAMS -> NumberInput.TooLow
            grams > MAX_WEIGHT_GRAMS -> NumberInput.TooHigh
            else -> NumberInput.Valid(grams)
        }
    }

    fun waist(text: String): NumberInput<Int> = parse(text, decimals = 1) { mm ->
        when {
            mm < MIN_WAIST_MM -> NumberInput.TooLow
            mm > MAX_WAIST_MM -> NumberInput.TooHigh
            else -> NumberInput.Valid(mm.toInt())
        }
    }

    fun doseUnits(text: String): NumberInput<Double> = parse(text, decimals = 1) { tenths ->
        val units = tenths / 10.0
        if (units > MAX_DOSE_UNITS) NumberInput.TooHigh else NumberInput.Valid(units)
    }

    /** Any amount of a fixed medicine, such as 1 or 0.5 tablets. */
    fun amount(text: String): NumberInput<Double> = parse(text, decimals = 2) { hundredths ->
        val amount = hundredths / 100.0
        when {
            amount <= 0.0 -> NumberInput.TooLow
            amount > MAX_DOSE_UNITS -> NumberInput.TooHigh
            else -> NumberInput.Valid(amount)
        }
    }

    fun isLargeWeightChange(newGrams: Int, lastGrams: Int?): Boolean =
        lastGrams != null && abs(newGrams - lastGrams) > LARGE_WEIGHT_CHANGE_GRAMS

    private inline fun <T> parse(text: String, decimals: Int, check: (Long) -> NumberInput<T>): NumberInput<T> {
        if (text.isBlank()) return NumberInput.Empty
        val scaled = Measures.parseScaled(text, decimals) ?: return NumberInput.NotANumber
        return check(scaled)
    }
}

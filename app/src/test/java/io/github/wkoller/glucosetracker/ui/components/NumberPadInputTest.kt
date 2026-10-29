package io.github.wkoller.glucosetracker.ui.components

import org.junit.Assert.assertEquals
import org.junit.Test

class NumberPadInputTest {

    /** Presses each key in turn, starting from an empty keypad. */
    private fun press(vararg keys: String, maxDecimals: Int = 1, maxIntegerDigits: Int = 2, keepLeadingZeros: Boolean = false) =
        keys.fold("") { value, key -> NumberPadInput.next(value, key, maxDecimals, maxIntegerDigits, keepLeadingZeros) }

    @Test
    fun `a number is typed as on a calculator`() {
        assertEquals("13.7", press("1", "3", ".", "7"))
        assertEquals("5", press("0", "5"))
        assertEquals("0.5", press("0", ".", "5"))
        assertEquals("0.5", press(".", "5"))
        assertEquals("1", press("1", "3", "back"))
    }

    @Test
    fun `digits beyond the limits are ignored`() {
        assertEquals("13", press("1", "3", "7"))
        assertEquals("1.2", press("1", ".", "2", "3"))
        assertEquals("12", press("1", "2", ".", maxDecimals = 0))
    }

    @Test
    fun `a PIN keeps its leading zeros`() {
        assertEquals("0412", press("0", "4", "1", "2", maxDecimals = 0, maxIntegerDigits = 4, keepLeadingZeros = true))
        assertEquals("0000", press("0", "0", "0", "0", "1", maxDecimals = 0, maxIntegerDigits = 4, keepLeadingZeros = true))
        assertEquals("04", press("0", "4", "1", "back", maxDecimals = 0, maxIntegerDigits = 4, keepLeadingZeros = true))
    }
}

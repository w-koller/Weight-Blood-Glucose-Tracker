package io.github.wkoller.glucosetracker.domain.validation

import io.github.wkoller.glucosetracker.domain.format.Measures
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InputValidatorTest {

    @Test
    fun `glucose readings`() {
        assertEquals(NumberInput.Valid(137), InputValidator.bgl("13.7"))
        assertEquals(NumberInput.Valid(130), InputValidator.bgl("13"))
        assertEquals(NumberInput.Valid(130), InputValidator.bgl("13."))
        assertEquals(NumberInput.NotANumber, InputValidator.bgl("13.75"))
        assertEquals(NumberInput.NotANumber, InputValidator.bgl("abc"))
        assertEquals(NumberInput.Empty, InputValidator.bgl(" "))
        assertEquals(NumberInput.TooLow, InputValidator.bgl("1.0"))
        assertEquals(NumberInput.TooHigh, InputValidator.bgl("33.4"))
    }

    @Test
    fun `weights and waists`() {
        assertEquals(NumberInput.Valid(116_510), InputValidator.weight("116.51"))
        assertEquals(NumberInput.TooLow, InputValidator.weight("11.6"))
        assertEquals(NumberInput.Valid(1_055), InputValidator.waist("105.5"))
        assertTrue(InputValidator.isLargeWeightChange(118_600, 116_300))
        assertFalse(InputValidator.isLargeWeightChange(116_510, 116_300))
        assertFalse(InputValidator.isLargeWeightChange(116_510, null))
    }

    @Test
    fun `dose amounts`() {
        assertEquals(NumberInput.Valid(4.0), InputValidator.doseUnits("4"))
        assertEquals(NumberInput.Valid(4.5), InputValidator.doseUnits("4.5"))
        assertEquals(NumberInput.TooHigh, InputValidator.doseUnits("51"))
        assertEquals(NumberInput.Valid(0.5), InputValidator.amount("0.5"))
        assertEquals(NumberInput.TooLow, InputValidator.amount("0"))
    }

    @Test
    fun `formatting`() {
        assertEquals("13.7", Measures.formatTenths(137))
        assertEquals("4.0", Measures.formatTenths(40))
        assertEquals("116.51", Measures.formatKg(116_510))
        assertEquals("116.3", Measures.formatKg(116_300))
        assertEquals("100", Measures.formatKg(100_000))
        assertEquals("105.5", Measures.formatCm(1_055))
        assertEquals("105", Measures.formatCm(1_050))
        assertEquals("4", Measures.formatAmount(4.0))
        assertEquals("0.5", Measures.formatAmount(0.5))
    }
}

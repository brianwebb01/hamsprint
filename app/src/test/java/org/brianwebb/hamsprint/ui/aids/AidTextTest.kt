package org.brianwebb.hamsprint.ui.aids

import org.brianwebb.hamsprint.domain.Band
import org.brianwebb.hamsprint.domain.BandPlanAid
import org.brianwebb.hamsprint.domain.FrequencyUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AidTextTest {
    @Test
    fun `values read naturally with engineering prefixes`() {
        assertEquals("500 mA", formatSi(0.5, "A"))
        assertEquals("500 \u00b5A", formatSi(0.0005, "A"))
        assertEquals("1.5 kΩ", formatSi(1500.0, "Ω"))
        assertEquals("3.56 MHz", formatSi(3.56e6, "Hz"))
        assertEquals("0.707", formatNumber(0.70710678))
    }

    @Test
    fun `frequencies carry exactly one unit, kHz below 1 MHz`() {
        assertEquals("14.000 to 14.350 MHz", formatRange(14.0, 14.35))
        assertEquals("472 to 479 kHz", formatRange(0.472, 0.479))
        assertEquals("146.52 MHz", formatFrequency(146.52))
        assertEquals("135.7 kHz", formatFrequency(0.1357))
        val spoken = describe(BandPlanAid(Band.B630M, null, null))
        assertTrue(spoken, spoken.startsWith("630 meters, 472 to 479 kHz."))
        assertFalse(spoken, spoken.contains("kHz MHz"))
    }

    @Test
    fun `a band plan follows its question's unit everywhere`() {
        assertEquals("21300 kHz", formatFrequency(21.3, FrequencyUnit.KHZ))
        assertEquals("21000 to 21450 kHz", formatRange(21.0, 21.45, FrequencyUnit.KHZ))
        assertEquals("21275", formatFrequencyNumber(21.275, FrequencyUnit.KHZ))
        val spoken = describe(BandPlanAid(Band.B15M, 21.3..21.3, null, FrequencyUnit.KHZ))
        assertTrue(spoken, spoken.startsWith("15 meters, 21000 to 21450 kHz."))
        assertTrue(spoken, spoken.endsWith("Marked: 21300 kHz."))
        assertFalse(spoken, "MHz" in spoken)
    }

    @Test
    fun `MHz keeps the precision the pools print`() {
        assertEquals("14.000", formatFrequencyNumber(14.0))
        assertEquals("14.1472", formatFrequencyNumber(14.1472))
        assertEquals("7.1765 MHz", formatFrequency(7.1765))
        assertEquals("146.52 MHz", formatFrequency(146.52))
        assertEquals("7.125 to 7.175 MHz", formatMark(7.125..7.175, FrequencyUnit.MHZ))
    }
}

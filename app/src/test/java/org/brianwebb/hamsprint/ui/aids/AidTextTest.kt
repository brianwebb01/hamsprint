package org.brianwebb.hamsprint.ui.aids

import org.brianwebb.hamsprint.domain.Band
import org.brianwebb.hamsprint.domain.BandPlanAid
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
}

package org.brianwebb.hamsprint.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class VisualAidTest {
    private fun privileges(band: Band, p: Privilege) = band.segments(p).map { Triple(it.lowMHz, it.highMHz, it.mode) }

    @Test
    fun `band plan matches 97 301 for the segments the exams ask about`() {
        assertEquals(
            listOf(Triple(14.025, 14.15, SegmentMode.CW_DATA), Triple(14.225, 14.35, SegmentMode.PHONE)),
            privileges(Band.B20M, Privilege.GENERAL),
        )
        assertEquals(listOf(Triple(28.0, 28.3, SegmentMode.CW_DATA), Triple(28.3, 28.5, SegmentMode.PHONE)), privileges(Band.B10M, Privilege.TECHNICIAN))
        assertEquals(7.175, Band.B40M.segments(Privilege.GENERAL).last().lowMHz, 0.0)
        assertEquals(3.8, Band.B80M.segments(Privilege.GENERAL).last().lowMHz, 0.0)
        assertEquals(21.275, Band.B15M.segments(Privilege.GENERAL).last().lowMHz, 0.0)
        assertTrue(Band.B30M.segments(Privilege.GENERAL).all { it.mode == SegmentMode.CW_DATA })
        assertTrue(Band.B20M.segments(Privilege.TECHNICIAN).isEmpty())
        assertEquals(SegmentMode.CW_ONLY, Band.B2M.segments(Privilege.TECHNICIAN).first().mode)
        assertEquals(144.1, Band.B2M.segments(Privilege.TECHNICIAN).first().highMHz, 0.0)
    }

    @Test
    fun `every segment sits inside its band and segments never overlap`() {
        for (band in Band.entries) for (p in Privilege.entries) {
            val segs = band.segments(p)
            segs.forEach { assertTrue("$band $p", it.lowMHz >= band.lowMHz && it.highMHz <= band.highMHz + 1e-9 && it.lowMHz < it.highMHz) }
            segs.zipWithNext().forEach { (a, b) -> assertTrue("$band $p overlap", a.highMHz <= b.lowMHz + 1e-9) }
        }
    }

    @Test
    fun `zones and wavelength follow the ITU and 300 over f`() {
        assertEquals(Zone.HF, Band.B10M.zone)
        assertEquals(Zone.VHF, Band.B6M.zone)
        assertEquals(Zone.UHF, Band.B70CM.zone)
        assertEquals(Zone.MF, Band.B160M.zone)
        assertEquals(2.05, WavelengthAid(146.0, false, null).meters, 0.01)
        assertEquals(Band.B2M, Band.containing(146.52))
        assertNull(Band.containing(100.0))
    }

    @Test
    fun `electrical aids compute the textbook answers`() {
        assertEquals(48.0, OhmsAid(Quantity.R, mapOf(Quantity.E to 12.0, Quantity.I to 0.25), null).result!!, 1e-9)
        assertNull(OhmsAid(Quantity.I, emptyMap(), null).result)
        assertEquals(138.0, PowerAid(Quantity.P, mapOf(Quantity.E to 13.8, Quantity.I to 10.0), null).result!!, 1e-9)
        assertEquals(10.0, PowerAid(Quantity.I, mapOf(Quantity.P to 120.0, Quantity.E to 12.0), null).result!!, 1e-9)
        assertEquals(5.0, PowerAid(Quantity.E, mapOf(Quantity.P to 0.5, Quantity.R to 50.0), null).result!!, 1e-9)
        assertEquals(400.0, PowerAid(Quantity.R, mapOf(Quantity.P to 100.0, Quantity.I to 0.5), null).result!!, 1e-9)
        assertEquals(50.0, CombineAid(PartKind.RESISTOR, parallel = true, listOf(100.0, 100.0), null).total, 1e-9)
        assertEquals(200.0, CombineAid(PartKind.RESISTOR, parallel = false, listOf(100.0, 100.0), null).total, 1e-9)
        assertEquals(20e-6, CombineAid(PartKind.CAPACITOR, parallel = true, listOf(10e-6, 10e-6), null).total, 1e-12)
        assertEquals(5e-6, CombineAid(PartKind.CAPACITOR, parallel = false, listOf(10e-6, 10e-6), null).total, 1e-12)
        assertEquals(2.0, DecibelAid(3.0103, false, null, null).ratio, 1e-3)
        assertEquals(200.0, DecibelAid(3.0103, false, 100.0, null).resultWatts!!, 0.1)
        assertEquals(-6.02, DecibelAid.ofRatio(0.25, null, null).db, 0.01)
        assertEquals(1.5, PrefixAid(1500.0, SiPrefix.KILO, SiPrefix.MEGA, null).result, 1e-9)
        assertEquals(1.0, PrefixAid(1_000_000.0, SiPrefix.PICO, SiPrefix.MICRO, null).result, 1e-9)
        assertEquals(169.7, WaveformAid(WaveMeasure.RMS, 120.0, WaveMeasure.PEAK, null, null).result, 0.1)
        assertEquals(100.0, WaveformAid(WaveMeasure.PP, 200.0, WaveMeasure.PEP, 50.0, null).result, 0.1)
        assertEquals(4.0, SwrAid.ofImpedances(50.0, 200.0, null).swr, 1e-9)
        assertEquals(3.56e6, ReactanceAid(ReactanceSolve.F0, null, 50e-6, 40e-12, null).result!!, 0.01e6)
        assertEquals(10.0, TimeConstantAid(1e6, 10e-6, null, false, null).seconds!!, 1e-9)
        assertEquals(0.632, TimeConstantAid(null, null, 1, false, null).level(1.0), 1e-3)
        assertEquals(12.0, TransformerAid(2250.0, 500.0, 54.0, null).let { it.secondaryVolts!! }, 1e-9)
        assertEquals(9.34, AntennaAid(AntennaType.DIPOLE, 50.1, null, null).lengthFeet!!, 0.01)
    }

    @Test
    fun `logic gates, call signs and phonetics`() {
        fun out(g: Gate) = LogicAid(g, null).truthTable.map { it.second }
        assertEquals(listOf(false, false, false, true), out(Gate.AND))
        assertEquals(listOf(true, true, true, false), out(Gate.NAND))
        assertEquals(listOf(false, true, true, false), out(Gate.XOR))
        assertEquals(listOf(true, false, false, true), out(Gate.XNOR))
        assertEquals(listOf(true, false), out(Gate.NOT))
        val call = CallSignAid("KF1XXX", null)
        assertEquals(Triple("KF", "1", "XXX"), Triple(call.prefix, call.digit, call.suffix))
        assertEquals("2x3", call.format)
        assertEquals(listOf("Whiskey", "One", "Alfa", "Whiskey"), PhoneticAid("W1AW", null).words.map { it.second })
    }

    @Test
    fun `invalid aids are rejected where they are built`() {
        assertThrows(IllegalArgumentException::class.java) { BandPlanAid(Band.B20M, 7.1..7.1, null) }
        assertThrows(IllegalArgumentException::class.java) { OhmsAid(Quantity.R, mapOf(Quantity.R to 1.0, Quantity.E to 2.0), null) }
        assertThrows(IllegalArgumentException::class.java) { CallSignAid("HELLO", null) }
        assertThrows(IllegalArgumentException::class.java) { CodesAid(CodeSet.Q, "QQQ", null) }
        assertThrows(IllegalArgumentException::class.java) { WaveformAid(WaveMeasure.PEAK, 1.0, WaveMeasure.PEP, null, null) }
        assertFalse(IonosphereAid(Layer.D, night = true, null).layers.contains(Layer.D))
    }
}

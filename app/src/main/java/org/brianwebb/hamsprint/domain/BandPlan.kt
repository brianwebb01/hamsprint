package org.brianwebb.hamsprint.domain

import kotlin.math.log10

/** How a slice of a band may be used. Every mode a slice allows is also allowed in the slices above it. */
enum class SegmentMode(val label: String) {
    CW_ONLY("CW only"),
    CW_DATA("CW, RTTY, data"),
    PHONE("Phone, image, CW"),
    ALL("All modes"),
}

data class Segment(val lowMHz: Double, val highMHz: Double, val mode: SegmentMode)

/** The license classes a band plan shows. Advanced is no longer issued, so it is left out. */
enum class Privilege(val label: String) { EXTRA("Extra"), GENERAL("General"), TECHNICIAN("Tech") }

/**
 * US amateur bands and the privileges each license class holds on them
 * (47 CFR 97.301 and 97.305). This is the single source of band facts the
 * visual aids draw from.
 */
enum class Band(
    val id: String,
    val label: String,
    val lowMHz: Double,
    val highMHz: Double,
    private val extra: List<Segment>,
    private val general: List<Segment> = extra,
    private val technician: List<Segment> = emptyList(),
) {
    B2200M("2200m", "2200 meters", 0.1357, 0.1378, all(0.1357, 0.1378)),
    B630M("630m", "630 meters", 0.472, 0.479, all(0.472, 0.479)),
    B160M("160m", "160 meters", 1.8, 2.0, all(1.8, 2.0)),
    B80M(
        "80m", "80 meters", 3.5, 4.0,
        extra = split(3.5, 3.6, 4.0),
        general = listOf(Segment(3.525, 3.6, SegmentMode.CW_DATA), Segment(3.8, 4.0, SegmentMode.PHONE)),
        technician = listOf(Segment(3.525, 3.6, SegmentMode.CW_ONLY)),
    ),
    B60M("60m", "60 meters", 5.3305, 5.4063, SIXTY_METER_CHANNELS.map { Segment(it, it + 0.0028, SegmentMode.PHONE) }),
    B40M(
        "40m", "40 meters", 7.0, 7.3,
        extra = split(7.0, 7.125, 7.3),
        general = listOf(Segment(7.025, 7.125, SegmentMode.CW_DATA), Segment(7.175, 7.3, SegmentMode.PHONE)),
        technician = listOf(Segment(7.025, 7.125, SegmentMode.CW_ONLY)),
    ),
    B30M("30m", "30 meters", 10.1, 10.15, listOf(Segment(10.1, 10.15, SegmentMode.CW_DATA))),
    B20M(
        "20m", "20 meters", 14.0, 14.35,
        extra = split(14.0, 14.15, 14.35),
        general = listOf(Segment(14.025, 14.15, SegmentMode.CW_DATA), Segment(14.225, 14.35, SegmentMode.PHONE)),
    ),
    B17M("17m", "17 meters", 18.068, 18.168, split(18.068, 18.11, 18.168)),
    B15M(
        "15m", "15 meters", 21.0, 21.45,
        extra = split(21.0, 21.2, 21.45),
        general = listOf(Segment(21.025, 21.2, SegmentMode.CW_DATA), Segment(21.275, 21.45, SegmentMode.PHONE)),
        technician = listOf(Segment(21.025, 21.2, SegmentMode.CW_ONLY)),
    ),
    B12M("12m", "12 meters", 24.89, 24.99, split(24.89, 24.93, 24.99)),
    B10M(
        "10m", "10 meters", 28.0, 29.7,
        extra = split(28.0, 28.3, 29.7),
        technician = split(28.0, 28.3, 28.5),
    ),
    B6M("6m", "6 meters", 50.0, 54.0, vhf(50.0, 50.1, 54.0), technician = vhf(50.0, 50.1, 54.0)),
    B2M("2m", "2 meters", 144.0, 148.0, vhf(144.0, 144.1, 148.0), technician = vhf(144.0, 144.1, 148.0)),
    B125M("1.25m", "1.25 meters", 219.0, 225.0, ONE_POINT_25, technician = ONE_POINT_25),
    B70CM("70cm", "70 centimeters", 420.0, 450.0, all(420.0, 450.0), technician = all(420.0, 450.0)),
    B33CM("33cm", "33 centimeters", 902.0, 928.0, all(902.0, 928.0), technician = all(902.0, 928.0)),
    B23CM("23cm", "23 centimeters", 1240.0, 1300.0, all(1240.0, 1300.0), technician = all(1240.0, 1300.0)),
    B13CM("13cm", "13 centimeters", 2300.0, 2450.0, THIRTEEN_CM, technician = THIRTEEN_CM);

    fun segments(privilege: Privilege): List<Segment> = when (privilege) {
        Privilege.EXTRA -> extra
        Privilege.GENERAL -> general
        Privilege.TECHNICIAN -> technician
    }

    operator fun contains(mhz: Double): Boolean = mhz in lowMHz..highMHz

    val zone: Zone get() = Zone.of(lowMHz)

    companion object {
        fun fromId(id: String): Band? = entries.firstOrNull { it.id == id }
        fun containing(mhz: Double): Band? = entries.firstOrNull { mhz in it }
    }
}

/** ITU frequency designations, used to group the bands on the spectrum aid. */
enum class Zone(val label: String, val lowMHz: Double, val highMHz: Double) {
    LF("LF", 0.03, 0.3),
    MF("MF", 0.3, 3.0),
    HF("HF", 3.0, 30.0),
    VHF("VHF", 30.0, 300.0),
    UHF("UHF", 300.0, 3000.0);

    companion object {
        fun of(mhz: Double): Zone = entries.first { mhz < it.highMHz }
    }
}

/**
 * How a frequency is written. The question pools write operating frequencies
 * in MHz from 1 MHz up and in kHz below it, so that is the [conventional]
 * choice; an aid overrides it only when its question is written the other way.
 */
enum class FrequencyUnit(val symbol: String) {
    KHZ("kHz"),
    MHZ("MHz");

    companion object {
        fun conventional(mhz: Double): FrequencyUnit = if (mhz < 1) KHZ else MHZ
        fun fromSymbol(symbol: String): FrequencyUnit? = entries.firstOrNull { it.symbol.equals(symbol, ignoreCase = true) }
    }
}

/** Wavelength in meters for a frequency in MHz: 300 / f. */
fun wavelengthMeters(mhz: Double): Double = 300.0 / mhz

/** Position of [mhz] on a log axis spanning [lowMHz] to [highMHz], from 0 to 1. */
fun logPosition(mhz: Double, lowMHz: Double, highMHz: Double): Float =
    ((log10(mhz) - log10(lowMHz)) / (log10(highMHz) - log10(lowMHz))).toFloat().coerceIn(0f, 1f)

// The five 60 m channels (USB dial frequency, MHz), each 2.8 kHz wide.
private val SIXTY_METER_CHANNELS get() = listOf(5.3305, 5.3465, 5.357, 5.3715, 5.4035)

private val ONE_POINT_25 get() = listOf(Segment(219.0, 220.0, SegmentMode.CW_DATA), Segment(222.0, 225.0, SegmentMode.ALL))

private val THIRTEEN_CM get() = listOf(Segment(2300.0, 2310.0, SegmentMode.ALL), Segment(2390.0, 2450.0, SegmentMode.ALL))

private fun all(low: Double, high: Double) = listOf(Segment(low, high, SegmentMode.ALL))

private fun split(low: Double, phoneFrom: Double, high: Double) =
    listOf(Segment(low, phoneFrom, SegmentMode.CW_DATA), Segment(phoneFrom, high, SegmentMode.PHONE))

private fun vhf(low: Double, allFrom: Double, high: Double) =
    listOf(Segment(low, allFrom, SegmentMode.CW_ONLY), Segment(allFrom, high, SegmentMode.ALL))

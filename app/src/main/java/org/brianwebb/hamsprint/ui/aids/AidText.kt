package org.brianwebb.hamsprint.ui.aids

import org.brianwebb.hamsprint.domain.AntennaAid
import org.brianwebb.hamsprint.domain.BandPlanAid
import org.brianwebb.hamsprint.domain.CallSignAid
import org.brianwebb.hamsprint.domain.CodesAid
import org.brianwebb.hamsprint.domain.CombineAid
import org.brianwebb.hamsprint.domain.ComponentAid
import org.brianwebb.hamsprint.domain.DecibelAid
import org.brianwebb.hamsprint.domain.FactAid
import org.brianwebb.hamsprint.domain.FilterAid
import org.brianwebb.hamsprint.domain.FrequencyUnit
import org.brianwebb.hamsprint.domain.IonosphereAid
import org.brianwebb.hamsprint.domain.LogicAid
import org.brianwebb.hamsprint.domain.MeterAid
import org.brianwebb.hamsprint.domain.ModesAid
import org.brianwebb.hamsprint.domain.OhmsAid
import org.brianwebb.hamsprint.domain.PhoneticAid
import org.brianwebb.hamsprint.domain.PowerAid
import org.brianwebb.hamsprint.domain.PrefixAid
import org.brianwebb.hamsprint.domain.Privilege
import org.brianwebb.hamsprint.domain.ReactanceAid
import org.brianwebb.hamsprint.domain.ReactanceSolve
import org.brianwebb.hamsprint.domain.RepeaterAid
import org.brianwebb.hamsprint.domain.SpectrumAid
import org.brianwebb.hamsprint.domain.SwrAid
import org.brianwebb.hamsprint.domain.TimeConstantAid
import org.brianwebb.hamsprint.domain.TransformerAid
import org.brianwebb.hamsprint.domain.VisualAid
import org.brianwebb.hamsprint.domain.WaveformAid
import org.brianwebb.hamsprint.domain.WavelengthAid
import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.log10

private val PREFIXES = listOf(-12 to "p", -9 to "n", -6 to "µ", -3 to "m", 0 to "", 3 to "k", 6 to "M", 9 to "G")

/** A number to three significant figures with no trailing zeros: 3.56, 1500, 0.707. */
fun formatNumber(value: Double, digits: Int = 3): String {
    if (value == 0.0) return "0"
    return BigDecimal(value).round(MathContext(digits)).stripTrailingZeros().toPlainString()
}

/** A value with an engineering prefix so the number reads naturally: 0.5 A is "500 mA". */
fun formatSi(value: Double, unit: String): String {
    if (value == 0.0) return "0 $unit"
    val exp = floor(log10(abs(value)) / 3).toInt() * 3
    val (e, p) = PREFIXES.lastOrNull { it.first <= exp } ?: PREFIXES.first()
    return "${formatNumber(value / Math.pow(10.0, e.toDouble()))} $p$unit"
}

/** A frequency with its unit: "14.000 MHz", "21300 kHz". Defaults to the pool convention, kHz below 1 MHz. */
fun formatFrequency(mhz: Double, unit: FrequencyUnit = FrequencyUnit.conventional(mhz)): String =
    "${formatFrequencyNumber(mhz, unit)} ${unit.symbol}"

/** A frequency range with one unit: "14.000 to 14.350 MHz", "472 to 479 kHz". */
fun formatRange(lowMHz: Double, highMHz: Double, unit: FrequencyUnit = FrequencyUnit.conventional(highMHz)): String =
    "${formatFrequencyNumber(lowMHz, unit)} to ${formatFrequencyNumber(highMHz, unit)} ${unit.symbol}"

/**
 * A frequency as a bare number in [unit], written the way the pools write it:
 * MHz below 100 keeps at least three decimals (14.000, 14.1472), MHz above
 * drops trailing zeros (146.52), and kHz is a plain number (21300, 135.7).
 */
fun formatFrequencyNumber(mhz: Double, unit: FrequencyUnit = FrequencyUnit.conventional(mhz)): String = when {
    unit == FrequencyUnit.KHZ -> BigDecimal(mhz * 1000).setScale(3, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString()
    mhz < 100 -> BigDecimal(mhz).setScale(6, RoundingMode.HALF_UP).stripTrailingZeros()
        .let { if (it.scale() < 3) it.setScale(3) else it }.toPlainString()
    else -> formatNumber(mhz, 6)
}

/** Text a screen reader speaks in place of the picture. */
fun describe(aid: VisualAid): String = when (aid) {
    is BandPlanAid -> buildString {
        append("${aid.band.label}, ${formatRange(aid.band.lowMHz, aid.band.highMHz, aid.unit)}. ")
        Privilege.entries.forEach { p ->
            val segs = aid.band.segments(p)
            append("${p.label}: ")
            append(if (segs.isEmpty()) "none" else segs.joinToString { "${formatRange(it.lowMHz, it.highMHz, aid.unit)} ${it.mode.label}" })
            append(". ")
        }
        aid.mark?.let { append("Marked: ${formatMark(it, aid.unit)}.") }
    }
    is SpectrumAid -> "Amateur bands from MF to UHF. Highlighted: " +
        aid.highlighted.joinToString { "${it.label}, ${it.zone.label}" }.ifEmpty { "none" } +
        (aid.markMHz?.let { ". Marked: ${formatFrequency(it, aid.markUnit)}" } ?: "")
    is WavelengthAid -> "Wavelength equals 300 divided by ${formatNumber(aid.freqMHz)} megahertz, about ${formatNumber(aid.meters)} meters."
    is RepeaterAid -> "${aid.repeater.band.label} repeaters use a ${formatNumber(aid.repeater.offsetMHz * 1000)} kilohertz offset." +
        if (aid.tone) " A sub-audible CTCSS tone opens the repeater." else ""
    is ModesAid -> "Bandwidths: " + org.brianwebb.hamsprint.domain.Mode.entries.joinToString { "${it.label} ${it.bandwidthLabel}" } +
        ". Highlighted: ${aid.highlight.label}."
    is IonosphereAid -> "Layers: " + aid.layers.joinToString { "${it.label}, ${it.lowKm} to ${it.highKm} km" } +
        ". Highlighted: ${aid.layer.label}, ${aid.layer.role}."
    is AntennaAid -> title(aid) + (aid.lengthFeet?.let { ", about ${formatNumber(it)} feet long" } ?: "") +
        (aid.highlight?.let { ", ${it.name.lowercase()} highlighted" } ?: "")
    is CallSignAid -> "${aid.call}: prefix ${aid.prefix}, region digit ${aid.digit}, suffix ${aid.suffix}. Format ${aid.format}."
    is PhoneticAid -> aid.words.joinToString { it.second }
    is CodesAid -> "${aid.set.title}. ${aid.highlight}: ${aid.set.codes.first { it.first == aid.highlight }.second}."
    is OhmsAid -> "E equals I times R." + (aid.result?.let { " ${aid.solve.symbol} is ${formatSi(it, aid.solve.unit)}." } ?: "")
    is PowerAid -> "P equals E times I." + (aid.result?.let { " ${aid.solve.symbol} is ${formatSi(it, aid.solve.unit)}." } ?: "")
    is CombineAid -> "${title(aid)}: total ${formatSi(aid.total, aid.part.unit)}."
    is DecibelAid -> "${formatNumber(aid.db, 2)} dB is a power ratio of ${formatNumber(aid.ratio)}." +
        (aid.resultWatts?.let { " ${formatNumber(aid.watts!!)} watts becomes ${formatNumber(it)} watts." } ?: "")
    is PrefixAid -> "${formatNumber(aid.value)} ${aid.from.word} units is ${formatNumber(aid.result)} ${aid.to.word} units."
    is WaveformAid -> "Peak ${formatNumber(aid.peak)}, RMS ${formatNumber(aid.rms)}, peak to peak ${formatNumber(aid.peak * 2)}. ${aid.solve.label}: ${formatNumber(aid.result)}."
    is SwrAid -> "SWR ${formatNumber(aid.swr)} to 1."
    is ReactanceAid -> title(aid) + (aid.result?.let { ": ${formatSi(it, if (aid.solve == ReactanceSolve.F0) "Hz" else "Ω")}" } ?: "")
    is TimeConstantAid -> "After one time constant a capacitor reaches 63.2 percent of full charge." +
        (aid.seconds?.let { " Time constant is ${formatSi(it, "s")}." } ?: "")
    is TransformerAid -> "Turns ratio ${formatNumber(aid.primaryTurns)} to ${formatNumber(aid.secondaryTurns)}." +
        (aid.secondaryVolts?.let { " Secondary ${formatNumber(it)} volts." } ?: "")
    is ComponentAid -> "${aid.symbol.label}: ${aid.symbol.role}."
    is MeterAid -> if (aid.ammeter) "An ammeter goes in series, in line with the current." else "A voltmeter goes in parallel, across the component."
    is FilterAid -> "${aid.type.label} filter response."
    is LogicAid -> "${aid.gate} truth table: " + aid.truthTable.joinToString { (ins, out) ->
        ins.joinToString(" ") { if (it) "1" else "0" } + " gives " + if (out) "1" else "0"
    }
    is FactAid -> "${aid.label}: ${aid.value}${aid.unit?.let { " $it" } ?: ""}."
}

/** A marked point or slice of a band: "21300 kHz", "7.125 to 7.175 MHz". */
fun formatMark(mark: ClosedFloatingPointRange<Double>, unit: FrequencyUnit): String =
    if (mark.start == mark.endInclusive) formatFrequency(mark.start, unit) else formatRange(mark.start, mark.endInclusive, unit)

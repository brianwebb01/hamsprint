package org.brianwebb.hamsprint.domain

import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * A picture that helps a learner remember one question's answer. Aids carry
 * only what to show; every fact and every computed result comes from here,
 * never from the aid files.
 */
sealed interface VisualAid {
    /** A short memory hook for the correct answer, or null. */
    val caption: String?
}

// ---- Bands and operating ----

/** [unit] is how the band's frequencies are written: its question's unit, else the pool convention. */
data class BandPlanAid(
    val band: Band,
    val mark: ClosedFloatingPointRange<Double>?,
    override val caption: String?,
    val unit: FrequencyUnit = FrequencyUnit.conventional(band.lowMHz),
) : VisualAid {
    init {
        require(mark == null || (mark.start in band && mark.endInclusive in band)) { "mark outside ${band.id}" }
    }
}

/** [markUnit] is how the marked frequency is written: its question's unit, else the pool convention. */
data class SpectrumAid(
    val bands: List<Band>,
    val markMHz: Double?,
    override val caption: String?,
    val markUnit: FrequencyUnit = FrequencyUnit.conventional(markMHz ?: 1.0),
) : VisualAid {
    /** The bands to emphasize: those named, else the one holding the mark. */
    val highlighted: List<Band> = bands.ifEmpty { listOfNotNull(markMHz?.let(Band::containing)) }
}

/** [example] marks a worked example for a formula question, not the question's own numbers. */
data class WavelengthAid(
    val freqMHz: Double,
    val fromMeters: Boolean,
    override val caption: String?,
    val example: Boolean = false,
) : VisualAid {
    init {
        require(freqMHz > 0) { "frequency must be positive" }
    }

    val meters: Double get() = wavelengthMeters(freqMHz)
    val band: Band? get() = Band.containing(freqMHz)
}

/**
 * Standard repeater offsets for the bands a Technician uses, with a typical
 * output frequency and the direction its input usually sits.
 */
enum class RepeaterBand(val band: Band, val offsetMHz: Double, val sampleOutputMHz: Double, private val inputBelow: Boolean) {
    SIX(Band.B6M, 1.0, 53.01, inputBelow = true),
    TWO(Band.B2M, 0.6, 146.94, inputBelow = true),
    ONE_POINT_25(Band.B125M, 1.6, 224.5, inputBelow = true),
    SEVENTY(Band.B70CM, 5.0, 444.1, inputBelow = false);

    /** Where you transmit to reach the sample repeater. */
    val sampleInputMHz: Double get() = if (inputBelow) sampleOutputMHz - offsetMHz else sampleOutputMHz + offsetMHz
}

data class RepeaterAid(val repeater: RepeaterBand, val tone: Boolean, override val caption: String?) : VisualAid

/** Typical occupied bandwidth of each mode, as the question pools state them. */
enum class Mode(val label: String, val bandwidthHz: Double, val bandwidthLabel: String) {
    FT8("FT8", 50.0, "50 Hz"),
    PSK31("PSK31", 60.0, "60 Hz"),
    CW("CW", 150.0, "150 Hz"),
    RTTY("RTTY", 250.0, "250 Hz"),
    SSB("SSB voice", 3_000.0, "2.4 to 3 kHz"),
    AM("AM voice", 6_000.0, "6 kHz"),
    FM("FM voice", 15_000.0, "10 to 15 kHz"),
    ATV("Fast scan TV", 6_000_000.0, "6 MHz"),
}

data class ModesAid(val highlight: Mode, override val caption: String?) : VisualAid

enum class Layer(val label: String, val lowKm: Int, val highKm: Int, val role: String, val dayOnly: Boolean) {
    D("D layer", 60, 90, "Absorbs low HF by day", dayOnly = true),
    E("E layer", 90, 150, "Sporadic E, short skip", dayOnly = true),
    F1("F1 layer", 150, 220, "Daytime, merges at night", dayOnly = true),
    F2("F2 layer", 220, 400, "Highest, longest skip", dayOnly = false),
    F("F layer", 200, 400, "F1 and F2 merged at night", dayOnly = false),
}

data class IonosphereAid(val layer: Layer, val night: Boolean, override val caption: String?) : VisualAid {
    /** Layers present at this time of day. At night D, E and F1 fade and the F layers merge. */
    val layers: List<Layer>
        get() = if (night) listOf(Layer.F) else listOf(Layer.D, Layer.E, Layer.F1, Layer.F2)
}

enum class AntennaType { DIPOLE, VERTICAL, YAGI, LOOP }
enum class AntennaPart { REFLECTOR, DRIVEN, DIRECTOR, RADIALS }

data class AntennaAid(
    val type: AntennaType,
    val freqMHz: Double?,
    val highlight: AntennaPart?,
    override val caption: String?,
) : VisualAid {
    /** Practical length in feet: 468 / f for a half wave dipole, 234 / f for a quarter wave vertical. */
    val lengthFeet: Double?
        get() = freqMHz?.let {
            when (type) {
                AntennaType.DIPOLE -> 468.0 / it
                AntennaType.VERTICAL -> 234.0 / it
                else -> null
            }
        }
}

data class CallSignAid(val call: String, override val caption: String?) : VisualAid {
    private val match = requireNotNull(CALL.matchEntire(call)) { "$call is not a US call sign" }
    val prefix: String get() = match.groupValues[1]
    val digit: String get() = match.groupValues[2]
    val suffix: String get() = match.groupValues[3]

    /** The call sign format, such as "1x3" or "2x2". */
    val format: String get() = "${prefix.length}x${suffix.length}"

    private companion object {
        val CALL = Regex("([AKNW][A-Z]?)(\\d)([A-Z]{1,3})")
    }
}

data class PhoneticAid(val text: String, override val caption: String?) : VisualAid {
    val words: List<Pair<Char, String>>
        get() = text.uppercase().filter { !it.isWhitespace() }.map { it to (PHONETIC[it] ?: it.toString()) }

    private companion object {
        val PHONETIC = mapOf(
            'A' to "Alfa", 'B' to "Bravo", 'C' to "Charlie", 'D' to "Delta", 'E' to "Echo", 'F' to "Foxtrot",
            'G' to "Golf", 'H' to "Hotel", 'I' to "India", 'J' to "Juliett", 'K' to "Kilo", 'L' to "Lima",
            'M' to "Mike", 'N' to "November", 'O' to "Oscar", 'P' to "Papa", 'Q' to "Quebec", 'R' to "Romeo",
            'S' to "Sierra", 'T' to "Tango", 'U' to "Uniform", 'V' to "Victor", 'W' to "Whiskey",
            'X' to "X-ray", 'Y' to "Yankee", 'Z' to "Zulu", '0' to "Zero", '1' to "One", '2' to "Two",
            '3' to "Three", '4' to "Four", '5' to "Five", '6' to "Six", '7' to "Seven", '8' to "Eight", '9' to "Nine",
        )
    }
}

enum class CodeSet(val title: String, val codes: List<Pair<String, String>>) {
    Q(
        "Q signals",
        listOf(
            "QRM" to "Man-made interference", "QRN" to "Static, natural noise", "QSY" to "Change frequency",
            "QSL" to "I acknowledge receipt", "QRP" to "Low power", "QRO" to "High power",
            "QTH" to "My location", "QRZ" to "Who is calling me?", "QRL" to "Is this frequency busy?",
            "QRS" to "Send more slowly", "QRQ" to "Send faster", "QRT" to "Stop sending",
            "QRV" to "I am ready", "QSO" to "A contact", "QSB" to "Signal fading", "QRX" to "Wait, stand by",
        ),
    ),
    PROSIGN(
        "Prosigns",
        listOf(
            "CQ" to "Calling any station", "DE" to "From, this is", "K" to "Go ahead, any station",
            "KN" to "Go ahead, named station only", "SK" to "End of contact", "AR" to "End of message",
            "BK" to "Break, back to you", "73" to "Best regards",
        ),
    ),
    RST(
        "Signal report",
        listOf("R" to "Readability, 1 to 5", "S" to "Strength, 1 to 9", "T" to "Tone (CW), 1 to 9"),
    ),
}

data class CodesAid(val set: CodeSet, val highlight: String, override val caption: String?) : VisualAid {
    init {
        require(set.codes.any { it.first == highlight }) { "$highlight is not in ${set.name}" }
    }
}

// ---- Electronics (computed) ----

/** A quantity in an Ohm's law or power calculation. */
enum class Quantity(val symbol: String, val unit: String) { P("P", "W"), E("E", "V"), I("I", "A"), R("R", "Ω") }

/** Ohm's law: solves [solve] from the two other values, or shows the formula when no values are given. */
data class OhmsAid(val solve: Quantity, val given: Map<Quantity, Double>, override val caption: String?) : VisualAid {
    init {
        require(solve != Quantity.P) { "Ohm's law has no P" }
        require(given.isEmpty() || (given.size == 2 && solve !in given && Quantity.P !in given)) { "give the other two of E, I, R" }
    }

    val result: Double?
        get() {
            if (given.isEmpty()) return null
            val (e, i, r) = Triple(given[Quantity.E], given[Quantity.I], given[Quantity.R])
            return when (solve) {
                Quantity.E -> i!! * r!!
                Quantity.I -> e!! / r!!
                else -> e!! / i!!
            }
        }
}

/** Power: solves any one of P, E, I, R from any two others. */
data class PowerAid(val solve: Quantity, val given: Map<Quantity, Double>, override val caption: String?) : VisualAid {
    init {
        require(given.isEmpty() || (given.size == 2 && solve !in given)) { "give exactly two other quantities" }
    }

    /** All four quantities once two are known, or null when only the formula is shown. */
    val solved: Map<Quantity, Double>?
        get() {
            if (given.isEmpty()) return null
            var p = given[Quantity.P]
            var e = given[Quantity.E]
            var i = given[Quantity.I]
            var r = given[Quantity.R]
            // With any two known, each pass fills in more; two passes always finish.
            repeat(2) {
                if (e == null) e = when { i != null && r != null -> i!! * r!!; p != null && i != null -> p!! / i!!; p != null && r != null -> sqrt(p!! * r!!); else -> null }
                if (i == null) i = when { e != null && r != null -> e!! / r!!; p != null && e != null -> p!! / e!!; p != null && r != null -> sqrt(p!! / r!!); else -> null }
                if (r == null) r = if (e != null && i != null) e!! / i!! else null
                if (p == null) p = if (e != null && i != null) e!! * i!! else null
            }
            return mapOf(Quantity.P to p!!, Quantity.E to e!!, Quantity.I to i!!, Quantity.R to r!!)
        }

    val result: Double? get() = solved?.get(solve)
}

enum class PartKind(val unit: String) { RESISTOR("Ω"), CAPACITOR("F"), INDUCTOR("H") }

data class CombineAid(val part: PartKind, val parallel: Boolean, val values: List<Double>, override val caption: String?) : VisualAid {
    init {
        require(values.size in 2..6 && values.all { it > 0 }) { "2 to 6 positive values" }
    }

    /** Capacitors add in parallel; resistors and inductors add in series. */
    val total: Double
        get() = if ((part == PartKind.CAPACITOR) == parallel) values.sum() else 1.0 / values.sumOf { 1.0 / it }
}

data class DecibelAid(val db: Double, val fromRatio: Boolean, val watts: Double?, override val caption: String?) : VisualAid {
    val ratio: Double get() = 10.0.pow(db / 10.0)
    val resultWatts: Double? get() = watts?.let { it * ratio }

    companion object {
        fun ofRatio(ratio: Double, watts: Double?, caption: String?): DecibelAid {
            require(ratio > 0) { "ratio must be positive" }
            return DecibelAid(10 * log10(ratio), fromRatio = true, watts = watts, caption = caption)
        }
    }
}

enum class SiPrefix(val symbol: String, val word: String, val exponent: Int) {
    PICO("p", "pico", -12), NANO("n", "nano", -9), MICRO("µ", "micro", -6), MILLI("m", "milli", -3),
    NONE("", "base", 0), KILO("k", "kilo", 3), MEGA("M", "mega", 6), GIGA("G", "giga", 9);

    companion object {
        /** Parses the aid file spelling, where micro is written "u". */
        fun fromCode(code: String): SiPrefix? = if (code == "u") MICRO else entries.firstOrNull { it.symbol == code }
    }
}

data class PrefixAid(val value: Double, val from: SiPrefix, val to: SiPrefix, override val caption: String?) : VisualAid {
    init {
        require(from != to) { "from and to must differ" }
    }

    val result: Double get() = value * 10.0.pow(from.exponent - to.exponent)
}

enum class WaveMeasure(val label: String) { PEAK("Peak"), RMS("RMS"), PP("Peak to peak"), PEP("PEP") }

data class WaveformAid(
    val given: WaveMeasure,
    val value: Double,
    val solve: WaveMeasure,
    val ohms: Double?,
    override val caption: String?,
) : VisualAid {
    init {
        require(given != WaveMeasure.PEP) { "PEP can't be the given value" }
        require(solve != WaveMeasure.PEP || (ohms != null && ohms > 0)) { "PEP needs a load in ohms" }
    }

    val peak: Double
        get() = when (given) {
            WaveMeasure.PEAK -> value
            WaveMeasure.RMS -> value * sqrt(2.0)
            else -> value / 2
        }
    val rms: Double get() = peak / sqrt(2.0)
    val result: Double
        get() = when (solve) {
            WaveMeasure.PEAK -> peak
            WaveMeasure.RMS -> rms
            WaveMeasure.PP -> 2 * peak
            WaveMeasure.PEP -> rms * rms / ohms!!
        }
}

data class SwrAid(val swr: Double, val impedances: Pair<Double, Double>?, override val caption: String?) : VisualAid {
    init {
        require(swr >= 1) { "SWR is at least 1" }
    }

    companion object {
        fun ofImpedances(z1: Double, z2: Double, caption: String?): SwrAid {
            require(z1 > 0 && z2 > 0) { "impedances must be positive" }
            return SwrAid(max(z1, z2) / min(z1, z2), z1 to z2, caption)
        }
    }
}

enum class ReactanceSolve { XL, XC, F0 }

data class ReactanceAid(
    val solve: ReactanceSolve,
    val hertz: Double?,
    val henries: Double?,
    val farads: Double?,
    override val caption: String?,
) : VisualAid {
    val result: Double?
        get() = when (solve) {
            ReactanceSolve.XL -> if (hertz != null && henries != null) 2 * PI * hertz * henries else null
            ReactanceSolve.XC -> if (hertz != null && farads != null) 1 / (2 * PI * hertz * farads) else null
            ReactanceSolve.F0 -> if (henries != null && farads != null) 1 / (2 * PI * sqrt(henries * farads)) else null
        }
}

data class TimeConstantAid(
    val ohms: Double?,
    val farads: Double?,
    val tau: Int?,
    val discharge: Boolean,
    override val caption: String?,
) : VisualAid {
    init {
        require((ohms == null) == (farads == null)) { "R and C go together" }
        require(tau == null || tau in 1..5) { "tau must be 1 to 5" }
    }

    val seconds: Double? get() = if (ohms != null && farads != null) ohms * farads else null

    /** Share of the full voltage after [n] time constants: 63.2%, 86.5%, ... when charging. */
    fun level(n: Double): Double = if (discharge) exp(-n) else 1 - exp(-n)
}

data class TransformerAid(val primaryTurns: Double, val secondaryTurns: Double, val primaryVolts: Double?, override val caption: String?) : VisualAid {
    init {
        require(primaryTurns > 0 && secondaryTurns > 0) { "turns must be positive" }
    }

    val secondaryVolts: Double? get() = primaryVolts?.let { it * secondaryTurns / primaryTurns }
}

// ---- Components and circuits ----

enum class ComponentSymbol(val label: String, val unit: String?, val role: String) {
    RESISTOR("Resistor", "ohm", "Opposes current"),
    POTENTIOMETER("Potentiometer", "ohm", "Adjustable resistance, volume control"),
    CAPACITOR("Capacitor", "farad", "Stores energy in an electric field"),
    VARIABLE_CAPACITOR("Variable capacitor", "farad", "Adjustable capacitance for tuning"),
    INDUCTOR("Inductor", "henry", "Stores energy in a magnetic field"),
    TRANSFORMER("Transformer", null, "Changes AC voltage by turns ratio"),
    DIODE("Diode", null, "Current flows one way, anode to cathode"),
    ZENER("Zener diode", null, "Holds a steady voltage"),
    LED("LED", null, "Emits light when forward biased"),
    NPN("NPN transistor", null, "Amplifies or switches; base, collector, emitter"),
    PNP("PNP transistor", null, "Amplifies or switches; arrow points in"),
    JFET("JFET", null, "Field effect: gate, drain, source"),
    MOSFET("MOSFET", null, "Insulated gate field effect transistor"),
    BATTERY("Battery", "volt", "Stores chemical energy"),
    FUSE("Fuse", "amp", "Opens on too much current"),
    SWITCH("Switch", null, "Opens or closes a circuit"),
    RELAY("Relay", null, "Electrically operated switch"),
    LAMP("Lamp", null, "Lights when current flows"),
    ANTENNA("Antenna", null, "Radiates and receives RF"),
    GROUND("Ground", null, "Common reference point"),
    CRYSTAL("Quartz crystal", null, "Sets a precise frequency"),
    SPEAKER("Speaker", null, "Turns audio current into sound"),
}

data class ComponentAid(val symbol: ComponentSymbol, override val caption: String?) : VisualAid

data class MeterAid(val ammeter: Boolean, override val caption: String?) : VisualAid

enum class FilterType(val label: String) { LOWPASS("Low pass"), HIGHPASS("High pass"), BANDPASS("Band pass"), NOTCH("Notch") }

data class FilterAid(val type: FilterType, override val caption: String?) : VisualAid {
    /** Relative response (0 to 1) at [x] across the plot, 0 to 1 left to right. */
    fun response(x: Double): Double {
        fun step(v: Double) = 1 / (1 + exp(-v * 22))
        return when (type) {
            FilterType.LOWPASS -> 1 - step(x - 0.5)
            FilterType.HIGHPASS -> step(x - 0.5)
            FilterType.BANDPASS -> step(x - 0.35) * (1 - step(x - 0.65))
            FilterType.NOTCH -> 1 - step(x - 0.44) * (1 - step(x - 0.56))
        }
    }
}

enum class Gate { AND, OR, NAND, NOR, XOR, XNOR, NOT }

data class LogicAid(val gate: Gate, override val caption: String?) : VisualAid {
    /** Rows of inputs to output. NOT has one input. */
    val truthTable: List<Pair<List<Boolean>, Boolean>>
        get() {
            if (gate == Gate.NOT) return listOf(listOf(false) to true, listOf(true) to false)
            return listOf(false to false, false to true, true to false, true to true).map { (a, b) ->
                listOf(a, b) to when (gate) {
                    Gate.AND -> a && b
                    Gate.OR -> a || b
                    Gate.NAND -> !(a && b)
                    Gate.NOR -> !(a || b)
                    Gate.XOR -> a != b
                    else -> a == b
                }
            }
        }
}

// ---- Key number ----

data class FactAid(val value: String, val unit: String?, val label: String, override val caption: String?) : VisualAid

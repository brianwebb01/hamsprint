package org.brianwebb.hamsprint.data

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.brianwebb.hamsprint.domain.AntennaAid
import org.brianwebb.hamsprint.domain.AntennaPart
import org.brianwebb.hamsprint.domain.AntennaType
import org.brianwebb.hamsprint.domain.Band
import org.brianwebb.hamsprint.domain.BandPlanAid
import org.brianwebb.hamsprint.domain.CallSignAid
import org.brianwebb.hamsprint.domain.CodeSet
import org.brianwebb.hamsprint.domain.CodesAid
import org.brianwebb.hamsprint.domain.CombineAid
import org.brianwebb.hamsprint.domain.ComponentAid
import org.brianwebb.hamsprint.domain.ComponentSymbol
import org.brianwebb.hamsprint.domain.DecibelAid
import org.brianwebb.hamsprint.domain.FactAid
import org.brianwebb.hamsprint.domain.FilterAid
import org.brianwebb.hamsprint.domain.FilterType
import org.brianwebb.hamsprint.domain.Gate
import org.brianwebb.hamsprint.domain.IonosphereAid
import org.brianwebb.hamsprint.domain.Layer
import org.brianwebb.hamsprint.domain.LicenseClass
import org.brianwebb.hamsprint.domain.LogicAid
import org.brianwebb.hamsprint.domain.MeterAid
import org.brianwebb.hamsprint.domain.Mode
import org.brianwebb.hamsprint.domain.ModesAid
import org.brianwebb.hamsprint.domain.OhmsAid
import org.brianwebb.hamsprint.domain.PartKind
import org.brianwebb.hamsprint.domain.PhoneticAid
import org.brianwebb.hamsprint.domain.PowerAid
import org.brianwebb.hamsprint.domain.PrefixAid
import org.brianwebb.hamsprint.domain.Quantity
import org.brianwebb.hamsprint.domain.ReactanceAid
import org.brianwebb.hamsprint.domain.ReactanceSolve
import org.brianwebb.hamsprint.domain.RepeaterAid
import org.brianwebb.hamsprint.domain.RepeaterBand
import org.brianwebb.hamsprint.domain.SiPrefix
import org.brianwebb.hamsprint.domain.SpectrumAid
import org.brianwebb.hamsprint.domain.SwrAid
import org.brianwebb.hamsprint.domain.TimeConstantAid
import org.brianwebb.hamsprint.domain.TransformerAid
import org.brianwebb.hamsprint.domain.VisualAid
import org.brianwebb.hamsprint.domain.WaveMeasure
import org.brianwebb.hamsprint.domain.WaveformAid
import org.brianwebb.hamsprint.domain.WavelengthAid
import java.io.InputStream
import java.util.concurrent.ConcurrentHashMap

/**
 * Reads the bundled visual aids (assets/aids/{pool}.json, see docs/VISUAL_AIDS.md)
 * into domain aids keyed by question id. Parsing is strict: a malformed entry
 * fails the whole file with the question id in the message, so bad data is
 * caught by tests instead of drawing a wrong picture.
 */
class AidLoader(private val open: (path: String) -> InputStream) {
    private val cache = ConcurrentHashMap<LicenseClass, Map<String, VisualAid>>()

    fun load(license: LicenseClass): Map<String, VisualAid> = cache.getOrPut(license) {
        val text = open("aids/${license.poolId}.json").use { it.readBytes().decodeToString() }
        parse(text)
    }

    internal fun parse(text: String): Map<String, VisualAid> {
        val entries = Json.parseToJsonElement(text).jsonArray
        val out = LinkedHashMap<String, VisualAid>(entries.size)
        for (element in entries) {
            val e = Entry(element.jsonObject)
            val aid = try {
                e.toAid()
            } catch (ex: IllegalArgumentException) {
                throw IllegalArgumentException("Aid for ${e.question}: ${ex.message}", ex)
            }
            require(out.put(e.question, aid) == null) { "More than one aid for ${e.question}" }
        }
        return out
    }

    private class Entry(private val o: JsonObject) {
        val question: String = requireNotNull(o["q"]?.jsonPrimitive?.content) { "Aid without a question id" }
        private val caption: String? = string("caption")

        fun toAid(): VisualAid = when (val kind = string("kind")) {
            "bandplan" -> BandPlanAid(band("band"), mark(), caption)
            "spectrum" -> SpectrumAid(list("bands").map { parseBand(it.jsonPrimitive.content) }, number("mark"), caption)
            "wavelength" -> number("freqMHz")?.let { WavelengthAid(it, fromMeters = false, caption, bool("example")) }
                ?: WavelengthAid(300.0 / need<Double>("meters"), fromMeters = true, caption, bool("example"))
            "repeater" -> RepeaterAid(
                RepeaterBand.entries.firstOrNull { it.band == band("band") } ?: error("band has no repeater offset"),
                bool("tone"), caption,
            )
            "modes" -> ModesAid(enumOf<Mode>("highlight"), caption)
            "ionosphere" -> IonosphereAid(
                enumOf<Layer>("layer"),
                when (string("time")) { "day" -> false; "night" -> true; else -> error("time must be day or night") },
                caption,
            )
            "antenna" -> AntennaAid(enumOf<AntennaType>("type"), number("freqMHz"), optionalEnum<AntennaPart>("highlight"), caption)
            "callsign" -> CallSignAid(need<String>("call"), caption)
            "phonetic" -> PhoneticAid(need<String>("text"), caption)
            "codes" -> CodesAid(
                when (string("set")) { "q" -> CodeSet.Q; "prosign" -> CodeSet.PROSIGN; "rst" -> CodeSet.RST; else -> error("unknown set") },
                need("highlight"), caption,
            )
            "ohms" -> OhmsAid(enumOf("solve"), quantities(), caption)
            "power" -> PowerAid(enumOf("solve"), quantities(), caption)
            "combine" -> CombineAid(
                enumOf<PartKind>("part"),
                when (string("arrangement")) { "series" -> false; "parallel" -> true; else -> error("arrangement must be series or parallel") },
                list("values").map { it.jsonPrimitive.doubleOrNull ?: error("values must be numbers") },
                caption,
            )
            "decibel" -> number("db")?.let { DecibelAid(it, fromRatio = false, watts = number("watts"), caption = caption) }
                ?: DecibelAid.ofRatio(need("ratio"), number("watts"), caption)
            "prefix" -> PrefixAid(need("value"), prefix("from"), prefix("to"), caption)
            "waveform" -> WaveformAid(enumOf("given"), need("value"), enumOf("solve"), number("ohms"), caption)
            "swr" -> number("swr")?.let { SwrAid(it, null, caption) } ?: SwrAid.ofImpedances(need("z1"), need("z2"), caption)
            "reactance" -> ReactanceAid(
                enumOf<ReactanceSolve>("solve"),
                number("f"), number("L"), number("C"), caption,
            )
            "timeconstant" -> TimeConstantAid(
                number("R"), number("C"), number("tau")?.toInt(),
                discharge = (string("show") ?: "charge") == "discharge",
                caption = caption,
            )
            "transformer" -> TransformerAid(need("np"), need("ns"), number("vp"), caption)
            "component" -> ComponentAid(enumOf<ComponentSymbol>("symbol"), caption)
            "meter" -> MeterAid(
                when (string("meter")) { "ammeter" -> true; "voltmeter" -> false; else -> error("meter must be ammeter or voltmeter") },
                caption,
            )
            "filter" -> FilterAid(enumOf<FilterType>("type"), caption)
            "logic" -> LogicAid(enumOf<Gate>("gate"), caption)
            "fact" -> FactAid(need("value"), string("unit"), need("label"), caption)
            else -> error("unknown kind $kind")
        }

        private fun string(key: String): String? = (o[key] as? JsonPrimitive)?.takeIf { it.isString }?.content
        private fun number(key: String): Double? = (o[key] as? JsonPrimitive)?.takeIf { !it.isString }?.doubleOrNull
        private fun bool(key: String): Boolean = (o[key] as? JsonPrimitive)?.booleanOrNull ?: false
        private fun list(key: String): JsonArray = (o[key] as? JsonArray) ?: JsonArray(emptyList())

        private inline fun <reified T> need(key: String): T {
            val value: Any? = when (T::class) {
                String::class -> string(key)
                Double::class -> number(key)
                else -> error("unsupported type")
            }
            return requireNotNull(value as T?) { "missing $key" }
        }

        private fun band(key: String): Band = parseBand(need(key))
        private fun parseBand(id: String): Band = requireNotNull(Band.fromId(id)) { "unknown band $id" }

        private fun mark(): ClosedFloatingPointRange<Double>? = when (val m = o["mark"]) {
            null -> null
            is JsonArray -> {
                require(m.size == 2) { "mark range needs two numbers" }
                val (lo, hi) = m.map { it.jsonPrimitive.doubleOrNull ?: error("mark must be numbers") }
                lo..hi
            }
            else -> m.jsonPrimitive.doubleOrNull?.let { it..it } ?: error("mark must be a number")
        }

        private fun quantities(): Map<Quantity, Double> =
            Quantity.entries.mapNotNull { q -> number(q.name)?.let { q to it } }.toMap()

        private fun prefix(key: String): SiPrefix = requireNotNull(SiPrefix.fromCode(need(key))) { "unknown prefix" }

        private inline fun <reified T : Enum<T>> optionalEnum(key: String): T? = string(key)?.let { raw ->
            enumValues<T>().firstOrNull { it.name.equals(raw, ignoreCase = true) } ?: error("unknown $key $raw")
        }

        private inline fun <reified T : Enum<T>> enumOf(key: String): T = requireNotNull(optionalEnum<T>(key)) { "missing $key" }

        private fun error(message: String): Nothing = throw IllegalArgumentException(message)
    }
}

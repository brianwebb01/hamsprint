package org.brianwebb.hamsprint.data

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.brianwebb.hamsprint.domain.Curriculum
import org.brianwebb.hamsprint.domain.Foundation
import org.brianwebb.hamsprint.domain.FoundationPoint
import org.brianwebb.hamsprint.domain.LicenseClass
import java.io.InputStream
import java.util.concurrent.ConcurrentHashMap

/**
 * Reads a pool's foundations from `assets/foundations/{pool}.json` (see
 * docs/FOUNDATIONS.md). Parsing is strict: a malformed foundation fails the
 * whole file with its id in the message, so bad data is caught by tests.
 */
class FoundationLoader(private val open: (path: String) -> InputStream) {
    private val cache = ConcurrentHashMap<LicenseClass, Curriculum>()

    fun load(license: LicenseClass): Curriculum = cache.getOrPut(license) {
        val text = open("foundations/${license.poolId}.json").use { it.readBytes().decodeToString() }
        parse(text)
    }

    internal fun parse(text: String): Curriculum =
        Curriculum(Json.parseToJsonElement(text).jsonArray.map { foundation(it.jsonObject) })

    private fun foundation(o: JsonObject): Foundation {
        val id = requireNotNull(o.string("id")) { "Foundation without an id" }
        return try {
            Foundation(
                id = id,
                title = o.need("title"),
                summary = o.need("summary"),
                points = o.list("points").map { element ->
                    val p = element.jsonObject
                    FoundationPoint(p.need("label"), p.need("value"), p.list("q").map { it.jsonPrimitive.content })
                },
                hook = o.string("hook"),
                questionIds = o.list("questions").map { it.jsonPrimitive.content },
                aid = (o["aid"] as? JsonObject)?.let(AidLoader::parseAid),
            )
        } catch (ex: IllegalArgumentException) {
            throw IllegalArgumentException("Foundation $id: ${ex.message}", ex)
        }
    }

    private fun JsonObject.string(key: String): String? = (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.content
    private fun JsonObject.need(key: String): String = requireNotNull(string(key)?.takeIf { it.isNotBlank() }) { "missing $key" }
    private fun JsonObject.list(key: String): JsonArray = (this[key] as? JsonArray) ?: JsonArray(emptyList())
}

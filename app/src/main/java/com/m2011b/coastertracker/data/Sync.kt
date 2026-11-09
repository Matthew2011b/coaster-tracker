package com.m2011b.coastertracker.data

import kotlinx.coroutines.delay
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.time.LocalDate

/**
 * Pulls every coaster and park from the Coasterpedia API with the user's own key and merges it in.
 * Rules: make/model never change once stored (only blanks get filled); status, park (relocations),
 * dates and stats follow Coasterpedia; the SBNO clock keeps its start date while a coaster stays SBNO.
 */
class Sync(private val db: Db, private val norm: Normalizer) {
    class Failed(msg: String) : Exception(msg)

    data class Summary(val added: Int, val updated: Int, val relocated: Int)

    private val statusMap = mapOf(
        "operating" to "operating", "under_refurbishment" to "operating", "standing_but_not_operating" to "sbno",
        "defunct" to "defunct", "in_storage" to "defunct", "under_construction" to "upcoming", "planned" to "upcoming",
        "unknown" to "unknown",
    )

    suspend fun run(key: String, progress: (String) -> Unit): Summary {
        progress("Downloading parks")
        val parks = pullAll(key, "parks", emptyMap()) { n -> progress("Downloading parks ($n)") }
        progress("Downloading coasters")
        val coasters = pullAll(key, "attractions", mapOf("type" to "coaster")) { n -> progress("Downloading coasters ($n)") }
        progress("Saving")
        return merge(parks, coasters, LocalDate.now()).also { db.setMeta("data_as_of", LocalDate.now().toString()) }
    }

    fun merge(parks: List<JSONObject>, coasters: List<JSONObject>, today: LocalDate): Summary = db.tx { tx ->
        parks.forEach { p ->
            val co = p.optJSONObject("coordinates")
            db.upsertPark(tx, Park(p.getLong("id"), p.getString("name"), p.optJSONObject("country")?.optString("code"),
                co?.optDoubleOrNull("lat"), co?.optDoubleOrNull("lon")))
        }
        var added = 0; var updated = 0; var moved = 0
        coasters.forEach { a ->
            val status = statusMap[a.optString("status")] ?: return@forEach
            val id = a.getLong("id")
            val park = a.optJSONObject("park")
            val closed = a.optJSONObject("closed")?.optStringOrNull("latest")
            val stats = a.optJSONObject("stats")
            val old = db.storedCoaster(tx, id)
            val rawMake = a.optJSONArray("manufacturers")?.optJSONObject(0)?.optStringOrNull("name")
            val rawModel = a.optJSONArray("products")?.optJSONObject(0)?.optJSONObject("product")?.optStringOrNull("name")
            val newMake = norm.make(rawMake)
            val sbnoSince = when {
                status != "sbno" -> null
                old?.status == "sbno" && old.sbnoSince != null -> old.sbnoSince
                else -> closed ?: today.toString()
            }
            val make = old?.make ?: newMake
            val model = old?.model ?: norm.model(make, rawModel)
            val c = Coaster(
                id, a.getString("name"), park?.optLongOrNull("id"), park?.optStringOrNull("name"),
                a.optJSONObject("country")?.optStringOrNull("code"), a.optStringOrNull("location_kind"), status, sbnoSince,
                a.optJSONObject("opened")?.optStringOrNull("earliest"), closed, make, model,
                stats?.optJSONObject("height")?.optDoubleOrNull("value"), stats?.optJSONObject("speed")?.optDoubleOrNull("value"),
            )
            if (old == null) added++ else if (old != c) { updated++; if (old.parkId != c.parkId) moved++ }
            db.upsertCoaster(tx, c)
        }
        Summary(added, updated, moved)
    }

    private suspend fun pullAll(key: String, path: String, q: Map<String, String>, onPage: (Int) -> Unit): List<JSONObject> {
        val out = mutableListOf<JSONObject>()
        var cursor: String? = null
        do {
            val params = q + mapOf("limit" to "100") + (cursor?.let { mapOf("cursor" to it) } ?: emptyMap())
            val body = get(key, "https://api.coasterpedia.net/v1/$path?" +
                params.entries.joinToString("&") { "${it.key}=${URLEncoder.encode(it.value, "UTF-8")}" })
            val o = JSONObject(body)
            val data: JSONArray = o.getJSONArray("data")
            for (i in 0 until data.length()) out += data.getJSONObject(i)
            cursor = o.optJSONObject("meta")?.optStringOrNull("next_cursor")
            onPage(out.size)
            if (cursor != null) delay(1100)
        } while (cursor != null)
        return out
    }

    private suspend fun get(key: String, url: String): String {
        repeat(4) {
            val conn = URL(url).openConnection() as HttpURLConnection
            conn.setRequestProperty("Authorization", "Bearer $key")
            conn.setRequestProperty("User-Agent", "CoasterTracker-Android/1.0")
            conn.connectTimeout = 20000; conn.readTimeout = 60000
            try {
                when (val code = conn.responseCode) {
                    200 -> return conn.inputStream.bufferedReader().use { it.readText() }
                    401, 403 -> throw Failed("Coasterpedia rejected the API key")
                    429 -> delay(((conn.getHeaderField("Retry-After")?.toLongOrNull() ?: 10) * 1000).coerceAtMost(60000))
                    else -> throw Failed("Coasterpedia error $code")
                }
            } finally { conn.disconnect() }
        }
        throw Failed("Coasterpedia is rate limiting, try again in a minute")
    }
}

private fun JSONObject.optStringOrNull(k: String): String? = if (isNull(k)) null else optString(k).ifEmpty { null }
private fun JSONObject.optDoubleOrNull(k: String): Double? = if (isNull(k)) null else optDouble(k).takeIf { !it.isNaN() }
private fun JSONObject.optLongOrNull(k: String): Long? = if (isNull(k)) null else optLong(k)

package com.m2011b.coastertracker.data

import org.json.JSONArray
import org.json.JSONObject

/** The backup file: your rides, edits, coasters you added, stars, settings and theme. Bundled coaster data isn't in it. */
object Backup {
    const val VERSION = 1

    class Data(
        val rides: Map<Long, String?>,
        val edits: Map<Long, Pair<String?, String?>>,
        val custom: List<Coaster>,
        val wishes: Wishes,
        val prefs: Map<String, Boolean>,
        val theme: String?,
        val savedAt: Long = System.currentTimeMillis(),
    )

    /** What a restore did: rides it added, rides in the backup, and the theme code to apply. */
    data class Restored(val added: Int, val inBackup: Int, val theme: String?)

    fun write(d: Data): String = JSONObject().apply {
        put("app", "lift-hill"); put("version", VERSION); put("saved_at", d.savedAt)
        put("rides", JSONArray().apply { d.rides.forEach { (id, date) -> put(JSONObject().put("id", id).put("date", date ?: JSONObject.NULL)) } })
        put("edits", JSONArray().apply { d.edits.forEach { (id, mm) -> put(JSONObject().put("id", id).put("make", mm.first ?: "").put("model", mm.second ?: "")) } })
        put("custom", JSONArray().apply { d.custom.forEach { c ->
            put(JSONObject().put("id", c.id).put("name", c.name).put("park_id", c.parkId ?: JSONObject.NULL).put("park", c.parkName ?: "")
                .put("country", c.country ?: "").put("make", c.make ?: "").put("model", c.model ?: ""))
        } })
        put("wishes", JSONObject().put("coasters", JSONArray(d.wishes.coasters.toList())).put("models", JSONArray(d.wishes.models.toList()))
            .put("auto", JSONArray(d.wishes.auto.toList())))
        put("prefs", JSONObject().apply { d.prefs.forEach { (k, v) -> put(k, v) } })
        put("theme", d.theme ?: JSONObject.NULL)
    }.toString()

    /** Throws IllegalArgumentException if this isn't a Lift Hill backup. */
    fun read(text: String): Data {
        val o = try { JSONObject(text) } catch (e: Exception) { throw IllegalArgumentException("Not a Lift Hill backup") }
        if (o.optString("app") != "lift-hill") throw IllegalArgumentException("Not a Lift Hill backup")
        fun JSONObject.s(k: String) = if (isNull(k)) null else optString(k).ifEmpty { null }
        fun arr(k: String) = o.optJSONArray(k) ?: JSONArray()
        fun <T> JSONArray.map(f: (Int) -> T) = (0 until length()).map(f)
        val w = o.optJSONObject("wishes") ?: JSONObject()
        fun longs(k: String) = (w.optJSONArray(k) ?: JSONArray()).map { w.getJSONArray(k).getLong(it) }.toSet()
        return Data(
            rides = arr("rides").map { arr("rides").getJSONObject(it) }.associate { it.getLong("id") to it.s("date") },
            edits = arr("edits").map { arr("edits").getJSONObject(it) }.associate { it.getLong("id") to (it.s("make") to it.s("model")) },
            custom = arr("custom").map { arr("custom").getJSONObject(it) }.map {
                Coaster(it.getLong("id"), it.getString("name"), if (it.isNull("park_id")) null else it.getLong("park_id"), it.s("park"), it.s("country"),
                    "park", "operating", null, null, null, it.s("make"), it.s("model"), null, null, custom = true)
            },
            wishes = Wishes(longs("coasters"), (w.optJSONArray("models") ?: JSONArray()).let { a -> a.map { a.getString(it) }.toSet() }, longs("auto")),
            prefs = (o.optJSONObject("prefs") ?: JSONObject()).let { p -> p.keys().asSequence().associateWith { p.getBoolean(it) } },
            theme = o.s("theme"),
            savedAt = o.optLong("saved_at"),
        )
    }

    /** Ride count and save time, for the restore prompt, without merging anything. */
    fun peek(text: String): Pair<Int, Long> = read(text).let { it.rides.size to it.savedAt }
}

package com.m2011b.coastertracker.data

import com.m2011b.coastertracker.Units
import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.time.LocalDate

/** App state holder: loads the database, exposes [stats], and applies every change. */
class Repo(context: Context, dbName: String = "coasters.db", prefsName: String = "coaster_prefs", private val today: () -> LocalDate = { LocalDate.now() }) {
    private val db = Db(context, dbName).also { it.mergeBundled(today()) }  // fresher data shipped in an app update
    private val prefs = context.getSharedPreferences(prefsName, Context.MODE_PRIVATE)
    val norm = Normalizer(context.assets.open("normalize.json").bufferedReader().use { it.readText() })

    // Carnival was part of the kiddie switch before 0.12, so it starts from that setting.
    var filters by mutableStateOf(Filters(
        includeDefunct = prefs.getBoolean("inc_defunct", false), includeKiddie = prefs.getBoolean("inc_kiddie", false),
        usOnly = prefs.getBoolean("us_only", false),
        includeCarnival = prefs.getBoolean("inc_carnival", prefs.getBoolean("inc_kiddie", false)),
        includeAlpine = prefs.getBoolean("inc_alpine", false),
    ))
        private set
    var metric by mutableStateOf(prefs.getBoolean("metric", false).also { Units.metric = it })
        private set
    var apiKey by mutableStateOf(prefs.getString("api_key", "") ?: "")
        private set
    var lastParkId by mutableStateOf(prefs.getLong("last_park", -1L).takeIf { it >= 0 })
        private set
    var dataAsOf by mutableStateOf(db.meta("data_as_of"))
        private set

    private var coasters = db.loadCoasters()
    private var parks = db.loadParks().associateBy { it.id }
    private var rides = db.loadRides()

    var stats by mutableStateOf(build())
        private set

    private fun build() = Stats(coasters, parks, rides, filters, today(), norm.kiddie, norm.alpine)
    private fun refresh(reload: Boolean = false) {
        if (reload) { coasters = db.loadCoasters(); parks = db.loadParks().associateBy { it.id } }
        rides = db.loadRides()
        stats = build()
    }

    val allParks: Collection<Park> get() = parks.values
    fun park(id: Long?) = id?.let { parks[it] }

    fun log(id: Long) { db.addRide(id, today().toString()); refresh() }
    fun unlog(id: Long) { db.removeRide(id); refresh() }

    fun edit(id: Long, make: String, model: String) {
        db.setOverride(id, norm.make(make.trim()), model.trim().ifEmpty { null }); refresh(reload = true)
    }

    fun addCustom(name: String, parkId: Long?, make: String, model: String): Long {
        val id = db.addCustom(name.trim(), park(parkId), norm.make(make.trim()), model.trim().ifEmpty { null })
        refresh(reload = true); return id
    }

    fun updateMetric(on: Boolean) { metric = on; Units.metric = on; prefs.edit().putBoolean("metric", on).apply() }

    fun updateFilters(f: Filters) {
        prefs.edit().putBoolean("inc_defunct", f.includeDefunct).putBoolean("inc_kiddie", f.includeKiddie).putBoolean("us_only", f.usOnly)
            .putBoolean("inc_carnival", f.includeCarnival).putBoolean("inc_alpine", f.includeAlpine).apply()
        filters = f; stats = build()
    }

    fun setKey(k: String) { apiKey = k.trim(); prefs.edit().putString("api_key", apiKey).apply() }
    fun setLastPark(id: Long) { lastParkId = id; prefs.edit().putLong("last_park", id).apply() }

    fun exportCsv(): String = Csv.export(coasters, rides)

    /** Adds every matched ride (existing ones are kept). Returns the rows that matched nothing. */
    fun importCsv(text: String): Csv.Result {
        val r = Csv.match(Csv.parse(text), coasters)
        db.tx { r.matched.forEach { (id, d) -> db.addRide(id, d) } }
        refresh(); return r
    }

    suspend fun sync(progress: (String) -> Unit): Sync.Summary {
        val s = Sync(db, norm).run(apiKey, progress)
        dataAsOf = db.meta("data_as_of"); refresh(reload = true); return s
    }
}

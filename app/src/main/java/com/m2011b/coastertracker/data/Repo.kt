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

    /** Starred coasters and models, kept in prefs as "c:<id>" and "m:<make|model>". */
    var wishes by mutableStateOf(readWishes())
        private set
    private fun readWishes(): Wishes {
        val raw = prefs.getStringSet("wish", emptySet()).orEmpty()
        fun ids(tag: String) = raw.filter { it.startsWith(tag) }.mapNotNull { it.drop(2).toLongOrNull() }.toSet()
        return Wishes(ids("c:"), raw.filter { it.startsWith("m:") }.map { it.drop(2) }.toSet(), ids("a:"))
    }
    private fun saveWishes(w: Wishes) {
        wishes = w; touch()
        prefs.edit().putStringSet("wish", w.coasters.map { "c:$it" }.toSet() + w.models.map { "m:$it" } + w.auto.map { "a:$it" }).apply()
    }

    /** Star or unstar one coaster. Either way it is now the user's own choice, not one the model star made. */
    fun toggleWishCoaster(id: Long) = wishes.let { w ->
        saveWishes(if (id in w.coasters) w.copy(coasters = w.coasters - id, auto = w.auto - id) else w.copy(coasters = w.coasters + id, auto = w.auto - id))
    }

    /**
     * Star a model: also stars every installation, remembering which ones it starred. Unstarring the model
     * unstars only those, so coasters starred on their own before stay starred.
     */
    fun toggleWishModel(key: String) = wishes.let { w ->
        val ids = coasters.filter { it.modelKey == key }.map { it.id }.toSet()
        saveWishes(if (key in w.models) {
            val mine = w.auto intersect ids
            w.copy(models = w.models - key, coasters = w.coasters - mine, auto = w.auto - mine)
        } else {
            val added = ids - w.coasters
            w.copy(models = w.models + key, coasters = w.coasters + added, auto = w.auto + added)
        })
    }

    /** Log a Ride shows the quick tile list instead of the picker. */
    var quickLog by mutableStateOf(prefs.getBoolean("quick_log", false))
        private set
    fun updateQuickLog(on: Boolean) { quickLog = on; prefs.edit().putBoolean("quick_log", on).apply(); touch() }

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

    /** Logs a ride today and returns anything it finished (park, model, manufacturer). */
    fun log(id: Long): List<Milestone> {
        val before = stats
        db.addRide(id, today().toString()); refresh(); touch()
        val c = coasters.firstOrNull { it.id == id } ?: return emptyList()
        return milestones(before, stats, c).also { celebrations = celebrations + it }
    }

    /** Completion cards waiting to be shown, oldest first. */
    var celebrations by mutableStateOf<List<Milestone>>(emptyList())
        private set
    fun dismissCelebration() { celebrations = celebrations.drop(1) }
    fun unlog(id: Long) { db.removeRide(id); refresh(); touch() }

    fun edit(id: Long, make: String, model: String) {
        db.setOverride(id, norm.make(make.trim()), model.trim().ifEmpty { null }); refresh(reload = true); touch()
    }

    fun addCustom(name: String, parkId: Long?, make: String, model: String): Long {
        val id = db.addCustom(name.trim(), park(parkId), norm.make(make.trim()), model.trim().ifEmpty { null })
        refresh(reload = true); touch(); return id
    }

    fun updateMetric(on: Boolean) { metric = on; Units.metric = on; prefs.edit().putBoolean("metric", on).apply(); touch() }

    fun updateFilters(f: Filters) {
        prefs.edit().putBoolean("inc_defunct", f.includeDefunct).putBoolean("inc_kiddie", f.includeKiddie).putBoolean("us_only", f.usOnly)
            .putBoolean("inc_carnival", f.includeCarnival).putBoolean("inc_alpine", f.includeAlpine).apply()
        filters = f; stats = build(); touch()
    }

    fun setKey(k: String) { apiKey = k.trim(); prefs.edit().putString("api_key", apiKey).apply() }
    fun setLastPark(id: Long) { lastParkId = id; prefs.edit().putLong("last_park", id).apply() }

    /** When rides, edits, stars or settings last changed, so automatic backup knows whether there's anything new. */
    var changedAt by mutableStateOf(prefs.getLong("changed_at", 0L))
        private set
    private fun touch() { changedAt = System.currentTimeMillis(); prefs.edit().putLong("changed_at", changedAt).apply() }

    /** Everything that's yours (not the bundled coaster data), as JSON. [themeCode] comes from the ThemeStore. */
    fun exportBackup(themeCode: String?): String = Backup.write(Backup.Data(
        rides = rides, edits = db.loadOverrides(), custom = coasters.filter { it.custom }, wishes = wishes,
        prefs = mapOf("inc_defunct" to filters.includeDefunct, "inc_kiddie" to filters.includeKiddie, "us_only" to filters.usOnly,
            "inc_carnival" to filters.includeCarnival, "inc_alpine" to filters.includeAlpine, "metric" to metric, "quick_log" to quickLog),
        theme = themeCode,
    ))

    /**
     * Merges a backup into this phone: rides from both are kept (earliest date wins), edits and coasters you added
     * are added where this phone has none, stars are combined, and the backup's settings are applied.
     * Returns what changed and the backup's theme code for the caller to apply.
     */
    fun restoreBackup(json: String): Backup.Restored {
        val b = Backup.read(json)
        var newRides = 0
        db.tx {
            // Coasters added by hand have ids made on each phone, so match them by name and park, not id.
            val mine = coasters.filter { it.custom }
            val idMap = HashMap<Long, Long>()
            for (c in b.custom) {
                val same = mine.firstOrNull { it.name.equals(c.name, true) && it.parkId == c.parkId }
                val park = c.parkId?.let { parks[it] ?: Park(it, c.parkName ?: "", c.country, null, null) }
                idMap[c.id] = same?.id ?: db.addCustom(c.name, park, c.make, c.model)
            }
            fun map(id: Long) = idMap[id] ?: id
            for ((id, date) in b.rides) {
                val to = map(id); val have = rides[to]
                if (to !in rides) { db.addRide(to, date); newRides++ }
                else if (date != null && (have == null || date < have)) { db.removeRide(to); db.addRide(to, date) }
            }
            val edits = db.loadOverrides()
            for ((id, mm) in b.edits) if (map(id) !in edits) db.setOverride(map(id), mm.first, mm.second)
            saveWishes(Wishes(wishes.coasters + b.wishes.coasters.map(::map), wishes.models + b.wishes.models, wishes.auto + b.wishes.auto.map(::map)))
        }
        val bp = b.prefs
        updateFilters(Filters(bp["inc_defunct"] ?: filters.includeDefunct, bp["inc_kiddie"] ?: filters.includeKiddie, bp["us_only"] ?: filters.usOnly,
            bp["inc_carnival"] ?: filters.includeCarnival, bp["inc_alpine"] ?: filters.includeAlpine))
        bp["metric"]?.let { updateMetric(it) }
        bp["quick_log"]?.let { updateQuickLog(it) }
        refresh(reload = true)
        return Backup.Restored(newRides, b.rides.size, b.theme)
    }

    fun exportCsv(): String = Csv.export(coasters, rides)

    /** Adds every matched ride (existing ones are kept). Returns the rows that matched nothing. */
    fun importCsv(text: String): Csv.Result {
        val r = Csv.match(Csv.parse(text), coasters)
        db.tx { r.matched.forEach { (id, d) -> db.addRide(id, d) } }
        refresh(); touch(); return r
    }

    suspend fun sync(progress: (String) -> Unit): Sync.Summary {
        val s = Sync(db, norm).run(apiKey, progress)
        dataAsOf = db.meta("data_as_of"); refresh(reload = true); return s
    }
}

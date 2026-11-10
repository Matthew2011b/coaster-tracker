package com.m2011b.coastertracker.data

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import org.json.JSONObject

/**
 * SQLite storage. Schema v2:
 *  parks(id, name, country, lat, lon, city, state)    (v2 added city, state)
 *  coasters(id, name, park_id, park_name, country, location_kind, status, sbno_since, opened, closed,
 *           make, model, height_m, speed_kmh, custom)
 *  rides(coaster_id, ridden_on)      one credit per coaster, date optional
 *  overrides(coaster_id, make, model) user edits, applied over the coaster's make/model
 *  meta(k, v)
 * The coaster tables are seeded from assets/snapshot.json on first launch.
 * Any schema change needs an onUpgrade step plus a test that starts from the old schema.
 */
class Db(private val context: Context, name: String = "coasters.db") : SQLiteOpenHelper(context, name, null, VERSION) {
    companion object { const val VERSION = 2 }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE parks(id INTEGER PRIMARY KEY, name TEXT NOT NULL, country TEXT, lat REAL, lon REAL, city TEXT, state TEXT)")
        db.execSQL(
            "CREATE TABLE coasters(id INTEGER PRIMARY KEY, name TEXT NOT NULL, park_id INTEGER, park_name TEXT, country TEXT, " +
                "location_kind TEXT, status TEXT NOT NULL, sbno_since TEXT, opened TEXT, closed TEXT, make TEXT, model TEXT, " +
                "height_m REAL, speed_kmh REAL, custom INTEGER NOT NULL DEFAULT 0)"
        )
        db.execSQL("CREATE INDEX coasters_park ON coasters(park_id)")
        db.execSQL("CREATE TABLE rides(coaster_id INTEGER PRIMARY KEY, ridden_on TEXT)")
        db.execSQL("CREATE TABLE overrides(coaster_id INTEGER PRIMARY KEY, make TEXT, model TEXT)")
        db.execSQL("CREATE TABLE meta(k TEXT PRIMARY KEY, v TEXT)")
        seed(db)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // One step per version, never dropping user tables.
        if (oldVersion < 2) {
            db.execSQL("ALTER TABLE parks ADD COLUMN city TEXT")
            db.execSQL("ALTER TABLE parks ADD COLUMN state TEXT")
            val parks = snapshot().getJSONArray("parks")
            for (i in 0 until parks.length()) {
                val p = parks.getJSONObject(i).toPark()
                if (p.city == null && p.state == null) continue
                db.update("parks", ContentValues().apply { put("city", p.city); put("state", p.state) }, "id=?", arrayOf(p.id.toString()))
            }
        }
    }

    private fun snapshot() = JSONObject(context.assets.open("snapshot.json").bufferedReader().use { it.readText() })

    private fun seed(db: SQLiteDatabase) {
        val o = snapshot()
        val parks = o.getJSONArray("parks")
        for (i in 0 until parks.length()) upsertPark(db, parks.getJSONObject(i).toPark())
        val cs = o.getJSONArray("coasters")
        for (i in 0 until cs.length()) upsertCoaster(db, cs.getJSONObject(i).toCoaster())
        setMeta(db, "data_as_of", o.optString("generated"))
    }

    fun loadParks(): List<Park> = readableDatabase.rawQuery("SELECT id,name,country,lat,lon,city,state FROM parks", null).use { c ->
        buildList { while (c.moveToNext()) add(Park(c.getLong(0), c.getString(1), c.str(2), c.dbl(3), c.dbl(4), c.str(5), c.str(6))) }
    }

    fun storedPark(db: SQLiteDatabase, id: Long): Park? =
        db.rawQuery("SELECT id,name,country,lat,lon,city,state FROM parks WHERE id=?", arrayOf(id.toString())).use { c ->
            if (c.moveToFirst()) Park(c.getLong(0), c.getString(1), c.str(2), c.dbl(3), c.dbl(4), c.str(5), c.str(6)) else null
        }

    fun loadCoasters(): List<Coaster> = readableDatabase.rawQuery(
        "SELECT c.id,c.name,c.park_id,c.park_name,c.country,c.location_kind,c.status,c.sbno_since,c.opened,c.closed," +
            "COALESCE(o.make,c.make),COALESCE(o.model,c.model),c.height_m,c.speed_kmh,c.custom " +
            "FROM coasters c LEFT JOIN overrides o ON o.coaster_id=c.id", null
    ).use { c ->
        buildList {
            while (c.moveToNext()) add(
                Coaster(
                    c.getLong(0), c.getString(1), c.lng(2), c.str(3), c.str(4), c.str(5), c.getString(6), c.str(7),
                    c.str(8), c.str(9), c.str(10)?.ifBlank { null }, c.str(11)?.ifBlank { null }, c.dbl(12), c.dbl(13), c.getInt(14) == 1,
                )
            )
        }
    }

    fun loadRides(): Map<Long, String?> = readableDatabase.rawQuery("SELECT coaster_id, ridden_on FROM rides", null).use { c ->
        buildMap { while (c.moveToNext()) put(c.getLong(0), c.str(1)) }
    }

    fun addRide(id: Long, date: String?) {
        writableDatabase.insertWithOnConflict("rides", null, ContentValues().apply { put("coaster_id", id); put("ridden_on", date) },
            SQLiteDatabase.CONFLICT_IGNORE)
    }

    fun removeRide(id: Long) { writableDatabase.delete("rides", "coaster_id=?", arrayOf(id.toString())) }

    fun setOverride(id: Long, make: String?, model: String?) {
        writableDatabase.insertWithOnConflict("overrides", null,
            ContentValues().apply { put("coaster_id", id); put("make", make ?: ""); put("model", model ?: "") },
            SQLiteDatabase.CONFLICT_REPLACE)
    }

    fun addCustom(name: String, park: Park?, make: String?, model: String?): Long {
        val db = writableDatabase
        val id = db.rawQuery("SELECT MIN(id) FROM coasters", null).use { c -> c.moveToFirst(); minOf(c.getLong(0), 0L) - 1 }
        upsertCoaster(db, Coaster(id, name, park?.id, park?.name, park?.country, "park", "operating", null, null, null,
            make, model, null, null, custom = true))
        return id
    }

    fun meta(k: String): String? = readableDatabase.rawQuery("SELECT v FROM meta WHERE k=?", arrayOf(k)).use { c ->
        if (c.moveToFirst()) c.str(0) else null
    }

    fun setMeta(k: String, v: String?) = setMeta(writableDatabase, k, v)
    private fun setMeta(db: SQLiteDatabase, k: String, v: String?) {
        db.insertWithOnConflict("meta", null, ContentValues().apply { put("k", k); put("v", v) }, SQLiteDatabase.CONFLICT_REPLACE)
    }

    fun <T> tx(block: (SQLiteDatabase) -> T): T {
        val db = writableDatabase
        db.beginTransaction()
        try { return block(db).also { db.setTransactionSuccessful() } } finally { db.endTransaction() }
    }

    fun upsertPark(db: SQLiteDatabase, p: Park) {
        db.insertWithOnConflict("parks", null, ContentValues().apply {
            put("id", p.id); put("name", p.name); put("country", p.country); put("lat", p.lat); put("lon", p.lon)
            put("city", p.city); put("state", p.state)
        }, SQLiteDatabase.CONFLICT_REPLACE)
    }

    fun upsertCoaster(db: SQLiteDatabase, c: Coaster) {
        db.insertWithOnConflict("coasters", null, ContentValues().apply {
            put("id", c.id); put("name", c.name); put("park_id", c.parkId); put("park_name", c.parkName); put("country", c.country)
            put("location_kind", c.locationKind); put("status", c.status); put("sbno_since", c.sbnoSince); put("opened", c.opened)
            put("closed", c.closed); put("make", c.make); put("model", c.model); put("height_m", c.heightM); put("speed_kmh", c.speedKmh)
            put("custom", if (c.custom) 1 else 0)
        }, SQLiteDatabase.CONFLICT_REPLACE)
    }

    /** Raw stored row (no override applied), used by updates so make/model stay locked. */
    fun storedCoaster(db: SQLiteDatabase, id: Long): Coaster? = db.rawQuery(
        "SELECT id,name,park_id,park_name,country,location_kind,status,sbno_since,opened,closed,make,model,height_m,speed_kmh,custom " +
            "FROM coasters WHERE id=?", arrayOf(id.toString())
    ).use { c ->
        if (!c.moveToFirst()) null else Coaster(
            c.getLong(0), c.getString(1), c.lng(2), c.str(3), c.str(4), c.str(5), c.getString(6), c.str(7), c.str(8), c.str(9),
            c.str(10), c.str(11), c.dbl(12), c.dbl(13), c.getInt(14) == 1,
        )
    }
}

private fun Cursor.str(i: Int): String? = if (isNull(i)) null else getString(i)
private fun Cursor.dbl(i: Int): Double? = if (isNull(i)) null else getDouble(i)
private fun Cursor.lng(i: Int): Long? = if (isNull(i)) null else getLong(i)

private fun JSONObject.s(k: String): String? = if (isNull(k) || !has(k)) null else getString(k)
private fun JSONObject.d(k: String): Double? = if (isNull(k) || !has(k)) null else getDouble(k)

internal fun JSONObject.toPark() = Park(getLong("id"), getString("name"), s("country"), d("lat"), d("lon"), s("city"), s("state"))
internal fun JSONObject.toCoaster() = Coaster(
    getLong("id"), getString("name"), if (isNull("parkId")) null else getLong("parkId"), s("parkName"), s("country"),
    s("locationKind"), getString("status"), s("sbnoSince"), s("opened"), s("closed"), s("make"), s("model"), d("heightM"), d("speedKmh"),
)

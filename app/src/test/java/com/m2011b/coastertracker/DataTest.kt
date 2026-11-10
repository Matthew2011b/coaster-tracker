package com.m2011b.coastertracker

import androidx.test.core.app.ApplicationProvider
import com.m2011b.coastertracker.data.*
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DataTest {
    private val today = LocalDate.of(2026, 11, 9)
    private fun repo(tag: String) = Repo(ApplicationProvider.getApplicationContext(), "t_$tag.db", "p_$tag") { today }
    private fun res(name: String) = javaClass.classLoader!!.getResource(name).readText()
    private fun c(status: String, since: String? = null, h: Double? = 30.0, make: String? = "Vekoma", model: String? = "Boomerang", kind: String = "park") =
        Coaster(1, "X", 1, "P", "US", kind, status, since, null, null, make, model, h, 80.0)

    @Test fun sbnoClockNeedsNineMonths() {
        assertEquals(Life.Operating, Rules.life(c("sbno", "2026-10-01"), today))
        assertEquals(Life.Operating, Rules.life(c("sbno", "2026-02-20"), today))
        assertEquals(Life.Sbno, Rules.life(c("sbno", "2026-02-12"), today))
        assertEquals(Life.Sbno, Rules.life(c("sbno", "2023-01-01"), today))
    }

    @Test fun filtersHideDefunctAndKiddieButNeverLoggedRides() {
        val k = ModelSet(setOf("Zamperla" to "Family Coaster", "Wisdom Rides" to "*"))
        val f = Filters()
        val kiddie = c("operating", make = "Zamperla", model = "Family Coaster")
        assertTrue(Rules.isKiddie(kiddie, k))
        assertTrue(Rules.isKiddie(c("operating", make = "Wisdom Rides", model = null), k))
        assertTrue(Rules.isKiddie(c("operating", h = 2.4).copy(speedKmh = null), k))
        assertFalse(Rules.isKiddie(c("operating", h = 2.4), k))  // short but fast is not kiddie
        assertTrue(Rules.isKiddie(c("operating", kind = "travelling"), k))
        assertFalse(Rules.isKiddie(c("operating", h = 13.0), k))
        assertFalse(Rules.counts(kiddie, false, f, today, k))
        assertTrue(Rules.counts(kiddie, true, f, today, k))
        assertTrue(Rules.counts(kiddie, false, f.copy(includeKiddie = true), today, k))
        assertFalse(Rules.counts(c("defunct"), false, f, today, k))
        assertTrue(Rules.counts(c("defunct"), true, f, today, k))
        assertTrue(Rules.counts(c("defunct"), false, f.copy(includeDefunct = true), today, k))
    }

    @Test fun normalizerMatchesSnapshotCleanup() {
        val r = repo("norm")
        assertEquals("Arrow Dynamics", r.norm.make("Arrow Development"))
        assertEquals("Philadelphia Toboggan Coasters", r.norm.make("Philidelphia Toboggan Coasters"))
        assertEquals("MK-1200", r.norm.model("Vekoma", "Corkscrew with Bayerncurve"))
        assertEquals("Zyklon", r.norm.model("Pinfari", "Zyklon Z64"))
        assertEquals("Twister Coaster", r.norm.model("Zamperla", "Twister Coaster 420STD"))
        assertEquals("Triangle Coaster", r.norm.model("SBF Visa Group", "Triangle Coaster (MX610)"))
        assertNull(r.norm.model("Philadelphia Toboggan Coasters", "Wooden Coaster"))
    }

    @Test fun importsBothCsvFormatsAndStatsMatchTheSpreadsheet() {
        val r = repo("csv")
        val a = r.importCsv(res("matthew-rides.csv"))
        assertEquals(42, a.matched.size); assertTrue(a.unmatched.isEmpty())
        assertEquals(42, r.stats.credits)
        // Spreadsheet export with RCDB-style names and no ids lands on the same 42.
        val r2 = repo("sheet")
        val b = r2.importCsv(res("spreadsheet-rides.csv"))
        assertEquals(b.unmatched.joinToString { it.coaster }, 42, b.matched.size)
        assertEquals(r.stats.rides.keys, r2.stats.rides.keys)
        // Twin of the same name at the same park: the operating Bat, not the 1981 one.
        assertTrue(764L in r2.stats.rides && 5140L !in r2.stats.rides)
        // Diamondback and Orion stay operating despite Coasterpedia's SBNO.
        val db = r.stats.coasters.first { it.id == 760L }
        assertEquals(Life.Operating, r.stats.life(db))
        val vsfc = r.stats.model("Vekoma|Suspended Family Coaster")!!
        assertEquals(2, vsfc.ridden)
        // Export round-trips.
        val r3 = repo("round")
        assertEquals(42, r3.importCsv(r.exportCsv()).matched.size)
    }

    @Test fun overridesAndCustomCoasters() {
        val r = repo("edit")
        r.edit(2458, "Great Coasters International", "Millennium Flyer")
        assertEquals("Millennium Flyer", r.stats.coasters.first { it.id == 2458L }.model)
        val id = r.addCustom("Backyard Coaster", 716, "In-house", "")
        r.log(id)
        assertTrue(r.stats.ridden(r.stats.coasters.first { it.id == id }))
        assertTrue(id < 0)
    }

    @Test fun updateKeepsMakeModelFollowsMovesAndRunsTheClock() {
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        val db = Db(ctx, "t_sync.db")
        val norm = Normalizer(ctx.assets.open("normalize.json").bufferedReader().readText())
        fun coaster(id: Long, status: String, park: Long, make: String, model: String?, closed: String? = null) = JSONObject(
            """{"id":$id,"name":"T$id","status":"$status","park":{"id":$park,"name":"P$park"},"country":{"code":"US"},
               "manufacturers":[{"name":"$make"}],"products":${if (model == null) "[]" else """[{"product":{"name":"$model"}}]"""},
               "closed":${if (closed == null) "null" else """{"latest":"$closed"}"""}}""")
        val s = Sync(db, norm)
        // Siren's Curse (59161): Coasterpedia now says Intamin at another park. Model stays, park moves.
        val sum = s.merge(listOf(JSONObject("""{"id":99999,"name":"New Park","country":{"code":"US"},"coordinates":{"lat":40.0,"lon":-83.0}}""")),
            listOf(
                coaster(59161, "operating", 99999, "Intamin", "Giga Coaster"),
                coaster(1L shl 40, "operating", 99999, "Arrow Development", "Corkscrew"),
                coaster(764, "standing_but_not_operating", 716, "Arrow Dynamics", "Suspended Coaster"),
                coaster(5140, "standing_but_not_operating", 716, "Arrow Dynamics", "Suspended Coaster", closed = "1983-09-01"),
            ), today)
        val moved = db.storedCoaster(db.readableDatabase, 59161)!!
        assertEquals(99999L, moved.parkId); assertEquals("Vekoma", moved.make); assertEquals("Tilt Coaster", moved.model)
        assertEquals("Arrow Dynamics", db.storedCoaster(db.readableDatabase, 1L shl 40)!!.make)
        assertEquals(today.toString(), db.storedCoaster(db.readableDatabase, 764)!!.sbnoSince)
        assertEquals(1, sum.added); assertEquals(1, sum.relocated)
        // A later update keeps the clock start.
        s.merge(emptyList(), listOf(coaster(764, "standing_but_not_operating", 716, "Arrow Dynamics", "Suspended Coaster")), today.plusDays(300))
        assertEquals(today.toString(), db.storedCoaster(db.readableDatabase, 764)!!.sbnoSince)
    }

    @Test fun alpineCoastersNeverCountUnlessLogged() {
        val a = ModelSet(setOf("Wiegand" to "Alpine Coaster", "Brandauer" to "*"))
        val f = Filters(includeDefunct = true, includeKiddie = true)
        val alpine = c("operating", make = "Wiegand", model = "Alpine Coaster")
        assertTrue(Rules.isAlpine(alpine, a))
        assertTrue(Rules.isAlpine(c("operating", make = "Brandauer", model = null), a))
        assertTrue(Rules.isAlpine(c("operating", make = "Wiegand", model = null).copy(name = "Rodelbahn"), a))
        assertFalse(Rules.isAlpine(c("operating", make = "Wiegand", model = null).copy(name = "BRIO Metro"), a))
        assertTrue(Rules.isAlpine(c("operating", make = null, model = null).copy(name = "Smoky Mountain Alpine Coaster"), a))
        assertFalse(Rules.isAlpine(c("operating", make = "Chance Rides", model = "Toboggan").copy(name = "Toboggan"), a))
        assertFalse(Rules.isAlpine(c("operating", make = "Mack Rides", model = "Bobsled Coaster"), a))
        assertFalse(Rules.counts(alpine, false, f, today, ModelSet.EMPTY, a))
        assertTrue(Rules.counts(alpine, true, f, today, ModelSet.EMPTY, a))
    }

    @Test fun logScreenFollowsTheKiddieFilter() {
        val r = repo("loggable")
        val s = r.stats
        // Every coaster the log screen offers either counts or was logged; no alpine coasters.
        val shown = s.coasters.filter { s.loggable(it) }
        assertTrue(shown.none { !s.ridden(it) && Rules.isKiddie(it, r.norm.kiddie) })
        assertTrue(shown.none { !s.ridden(it) && Rules.isAlpine(it, r.norm.alpine) })
        // A park with only kiddie rides is hidden, and comes back when kiddie rides are included.
        val kiddieOnly = s.coasters.filter { it.parkId != null }.groupBy { it.parkId!! }
            .filterValues { cs -> cs.all { Rules.isKiddie(it, r.norm.kiddie) } && cs.any { s.life(it) == Life.Operating } }.keys
        assertTrue(kiddieOnly.isNotEmpty())
        assertTrue(kiddieOnly.none { it in s.loggableParkIds })
        r.updateFilters(r.filters.copy(includeKiddie = true))
        assertTrue(kiddieOnly.any { it in r.stats.loggableParkIds })
    }

    @Test fun usParksShowCityAndState() {
        val r = repo("place")
        assertEquals("Mason, OH", r.allParks.first { it.name == "Kings Island" }.place)
        assertEquals("Sandusky, OH", r.allParks.first { it.name == "Cedar Point" }.place)
        assertEquals("DE", Park(1, "X", "DE", null, null, "Rust", null).place)
        assertEquals("OH", Park(1, "X", "US", null, null, null, "Ohio").place)
    }

    @Test fun upgradeFromV1KeepsRidesAndAddsCityState() {
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        val name = "t_v1.db"
        ctx.deleteDatabase(name)
        // Build a v1 database by hand, the way 0.1 left it on the phone.
        android.database.sqlite.SQLiteDatabase.openOrCreateDatabase(ctx.getDatabasePath(name).apply { parentFile?.mkdirs() }, null).use { db ->
            db.execSQL("CREATE TABLE parks(id INTEGER PRIMARY KEY, name TEXT NOT NULL, country TEXT, lat REAL, lon REAL)")
            db.execSQL("CREATE TABLE coasters(id INTEGER PRIMARY KEY, name TEXT NOT NULL, park_id INTEGER, park_name TEXT, country TEXT, " +
                "location_kind TEXT, status TEXT NOT NULL, sbno_since TEXT, opened TEXT, closed TEXT, make TEXT, model TEXT, " +
                "height_m REAL, speed_kmh REAL, custom INTEGER NOT NULL DEFAULT 0)")
            db.execSQL("CREATE TABLE rides(coaster_id INTEGER PRIMARY KEY, ridden_on TEXT)")
            db.execSQL("CREATE TABLE overrides(coaster_id INTEGER PRIMARY KEY, make TEXT, model TEXT)")
            db.execSQL("CREATE TABLE meta(k TEXT PRIMARY KEY, v TEXT)")
            db.execSQL("INSERT INTO parks VALUES(716,'Kings Island','US',39.34,-84.27)")
            db.execSQL("INSERT INTO coasters(id,name,park_id,park_name,country,status,make,model) VALUES(5,'Banshee',716,'Kings Island','US','operating','Bolliger & Mabillard','Inverted Coaster')")
            db.execSQL("INSERT INTO rides VALUES(5,'2019-07-01')")
            db.version = 1
        }
        val db = Db(ctx, name)
        assertEquals(mapOf(5L to "2019-07-01"), db.loadRides())
        assertEquals("Mason, OH", db.loadParks().single { it.id == 716L }.place)
        assertEquals(2, db.readableDatabase.version)
    }

    @Test fun usOnlyDropsOtherCountriesButKeepsLoggedRides() {
        val us = c("operating"); val de = c("operating").copy(country = "DE")
        val f = Filters(usOnly = true)
        assertTrue(Rules.counts(us, false, f, today, ModelSet.EMPTY))
        assertFalse(Rules.counts(de, false, f, today, ModelSet.EMPTY))
        assertTrue(Rules.counts(de, true, f, today, ModelSet.EMPTY))
        val r = repo("usonly")
        r.updateFilters(r.filters.copy(usOnly = true))
        assertTrue(r.stats.parkStats(usOnly = false).all { it.park.country == "US" })
    }
}

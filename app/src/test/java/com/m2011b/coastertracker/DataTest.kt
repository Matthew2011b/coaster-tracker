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
        val k = KiddieModels(setOf("Zamperla" to "Family Coaster", "Wisdom Rides" to "*"))
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
}

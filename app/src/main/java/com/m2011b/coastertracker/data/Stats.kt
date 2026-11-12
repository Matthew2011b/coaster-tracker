package com.m2011b.coastertracker.data

import java.time.LocalDate

data class ModelStat(val make: String, val model: String, val ridden: Int, val total: Int) {
    val key get() = "$make|$model"
    val ratio get() = if (total == 0) 0.0 else ridden.toDouble() / total
}

data class MakeStat(val make: String, val modelsRidden: Int, val models: Int, val coastersRidden: Int, val coasters: Int) {
    val ratio get() = if (models == 0) 0.0 else modelsRidden.toDouble() / models
}

data class ParkStat(val park: Park, val newModels: Int, val unridden: Int, val ridden: Int)

data class NearModel(val stat: ModelStat, val coaster: Coaster, val park: Park, val miles: Double)

enum class SortBy(val label: String) {
    MostComplete("Most complete"), LeastComplete("Least complete"), Alphabetical("Alphabetical"),
    MostRidden("Most ridden"), MostInstalled("Most installations"),
}

/** Everything the screens show, computed from one consistent set of inputs. */
class Stats(
    val coasters: List<Coaster>,
    val parks: Map<Long, Park>,
    val rides: Map<Long, String?>,
    val filters: Filters,
    val today: LocalDate,
    val kiddie: ModelSet,
    val alpine: ModelSet = ModelSet.EMPTY,
) {
    fun ridden(c: Coaster) = c.id in rides
    fun counts(c: Coaster) = Rules.counts(c, ridden(c), filters, today, kiddie, alpine)
    fun life(c: Coaster) = Rules.life(c, today)

    val counted: List<Coaster> by lazy { coasters.filter { counts(it) } }

    val models: List<ModelStat> by lazy {
        counted.filter { it.modelKey != null }.groupBy { it.make!! to it.model!! }
            .map { (k, cs) -> ModelStat(k.first, k.second, cs.count { ridden(it) }, cs.size) }
    }
    private val modelByKey by lazy { models.associateBy { it.key } }
    fun model(key: String) = modelByKey[key]

    val makes: List<MakeStat> by lazy {
        val byMake = counted.filter { it.make != null }.groupBy { it.make!! }
        byMake.map { (mk, cs) ->
            val ms = models.filter { it.make == mk }
            MakeStat(mk, ms.count { it.ridden > 0 }, ms.size, cs.count { ridden(it) }, cs.size)
        }
    }

    val credits get() = rides.size
    val modelsRidden get() = models.count { it.ridden > 0 }
    val modelsNever get() = models.count { it.ridden == 0 }

    fun sortedModels(by: SortBy): List<ModelStat> = when (by) {
        SortBy.MostComplete -> models.sortedWith(compareByDescending<ModelStat> { it.ratio }.thenByDescending { it.ridden }.thenBy { it.model })
        SortBy.LeastComplete -> models.sortedWith(compareBy<ModelStat> { it.ratio }.thenByDescending { it.total }.thenBy { it.model })
        SortBy.Alphabetical -> models.sortedWith(compareBy({ it.make.lowercase() }, { it.model.lowercase() }))
        SortBy.MostRidden -> models.sortedWith(compareByDescending<ModelStat> { it.ridden }.thenByDescending { it.ratio }.thenBy { it.model })
        SortBy.MostInstalled -> models.sortedWith(compareByDescending<ModelStat> { it.total }.thenBy { it.model })
    }

    fun sortedMakes(by: SortBy): List<MakeStat> = when (by) {
        SortBy.MostComplete -> makes.sortedWith(compareByDescending<MakeStat> { it.ratio }.thenByDescending { it.modelsRidden }.thenBy { it.make })
        SortBy.LeastComplete -> makes.sortedWith(compareBy<MakeStat> { it.ratio }.thenByDescending { it.models }.thenBy { it.make })
        SortBy.Alphabetical -> makes.sortedBy { it.make.lowercase() }
        SortBy.MostRidden -> makes.sortedWith(compareByDescending<MakeStat> { it.coastersRidden }.thenBy { it.make })
        SortBy.MostInstalled -> makes.sortedWith(compareByDescending<MakeStat> { it.coasters }.thenBy { it.make })
    }

    /** Installations of a model, ridden first, then nearest (or by park name without a location). */
    fun installations(key: String, here: Pair<Double, Double>?): List<Pair<Coaster, Double?>> =
        counted.filter { it.modelKey == key }.map { it to distance(it, here) }
            .sortedWith(compareBy<Pair<Coaster, Double?>> { !ridden(it.first) }.thenBy { it.second ?: Double.MAX_VALUE }
                .thenBy { it.first.parkName ?: "" })

    fun distance(c: Coaster, here: Pair<Double, Double>?): Double? {
        val p = c.parkId?.let { parks[it] } ?: return null
        if (here == null || p.lat == null || p.lon == null) return null
        return Rules.miles(here.first, here.second, p.lat, p.lon)
    }

    /** Parks with something left to ride; [includeDone] also returns parks where everything counted is ridden. */
    fun parkStats(usOnly: Boolean, includeDone: Boolean = false): List<ParkStat> {
        val never = models.filter { it.ridden == 0 }.map { it.key }.toSet()
        return counted.filter { it.parkId != null }.groupBy { it.parkId!! }.mapNotNull { (pid, cs) ->
            val park = parks[pid] ?: return@mapNotNull null
            if (usOnly && park.country != "US") return@mapNotNull null
            val open = cs.filter { !ridden(it) && life(it) == Life.Operating }
            ParkStat(park, open.mapNotNull { it.modelKey }.filter { it in never }.toSet().size, open.size, cs.count { ridden(it) })
        }.filter { it.unridden > 0 || (includeDone && it.ridden > 0) }
    }

    /** For each never-ridden model, the closest operating installation. */
    fun nearestNewModels(here: Pair<Double, Double>): List<NearModel> {
        val never = models.filter { it.ridden == 0 }.associateBy { it.key }
        return counted.filter { life(it) == Life.Operating && it.modelKey in never }.mapNotNull { c ->
            val p = c.parkId?.let { parks[it] } ?: return@mapNotNull null
            val d = distance(c, here) ?: return@mapNotNull null
            NearModel(never.getValue(c.modelKey!!), c, p, d)
        }.groupBy { it.stat.key }.map { (_, l) -> l.minBy { it.miles } }.sortedBy { it.miles }
    }

    /**
     * Whether a coaster shows on the log screen: anything logged, plus anything standing that the
     * filters allow (kiddie rides only when they're included, never alpine coasters).
     */
    fun loggable(c: Coaster): Boolean = ridden(c) || (
        (life(c) == Life.Operating || life(c) == Life.Sbno) && !Rules.filteredOut(c, filters, kiddie, alpine))

    /** Parks with at least one loggable coaster. */
    val loggableParkIds: Set<Long> by lazy { coasters.filter { it.parkId != null && loggable(it) }.map { it.parkId!! }.toSet() }

    fun atPark(parkId: Long): List<Coaster> = coasters.filter { it.parkId == parkId && loggable(it) }
        .sortedWith(compareBy({ ridden(it) }, { it.name.lowercase() }))

    fun isNewModel(c: Coaster) = c.modelKey != null && !ridden(c) && (model(c.modelKey!!)?.ridden ?: 1) == 0

    /** Parks with an installation matching [match] (operating, or ridden), for the model and maker maps. */
    fun installParks(match: (Coaster) -> Boolean): List<ParkStat> =
        counted.filter { it.parkId != null && match(it) && (ridden(it) || life(it) == Life.Operating) }.groupBy { it.parkId!! }
            .mapNotNull { (pid, cs) ->
                val park = parks[pid] ?: return@mapNotNull null
                ParkStat(park, 0, cs.count { !ridden(it) }, cs.count { ridden(it) })
            }

    // ---------- Search ----------

    data class Found(val coasters: List<Coaster>, val parks: List<ParkStat>, val models: List<ModelStat>, val makes: List<MakeStat>) {
        val empty get() = coasters.isEmpty() && parks.isEmpty() && models.isEmpty() && makes.isEmpty()
    }

    /**
     * Everything with a word in its name starting with [q] (ignoring case and punctuation): names starting with it
     * first, then ridden coasters and nearer places. Up to [max] per group.
     */
    fun search(q: String, here: Pair<Double, Double>?, max: Int = 25): Found {
        val n = Rules.norm(q)
        if (n.isEmpty()) return Found(emptyList(), emptyList(), emptyList(), emptyList())
        // A match starts a word ("ban" finds Banshee and Wonderla Bangalore, not Lisebergbanan), or the whole name.
        fun hit(name: String?) = name != null &&
            (Rules.norm(name).startsWith(n) || name.split(Regex("[^\\p{L}\\p{N}]+")).any { Rules.norm(it).startsWith(n) })
        fun starts(name: String) = if (Rules.norm(name).startsWith(n)) 0 else 1
        val cs = coasters.filter { hit(it.name) && loggable(it) }
            .sortedWith(compareBy<Coaster>({ starts(it.name) }, { !ridden(it) }, { distance(it, here) ?: Double.MAX_VALUE }, { it.name.lowercase() }))
            .take(max)
        val ps = parkStats(usOnly = filters.usOnly, includeDone = true).filter { hit(it.park.name) }
            .sortedWith(compareBy<ParkStat>({ starts(it.park.name) }, { p ->
                if (here == null || p.park.lat == null || p.park.lon == null) Double.MAX_VALUE else Rules.miles(here.first, here.second, p.park.lat, p.park.lon)
            })).take(max)
        val ms = models.filter { hit(it.model) || hit("${it.make} ${it.model}") }
            .sortedWith(compareBy<ModelStat>({ starts(it.model) }, { -it.total })).take(max)
        val mk = makes.filter { hit(it.make) }.sortedWith(compareBy<MakeStat>({ starts(it.make) }, { -it.coasters })).take(max)
        return Found(cs, ps, ms, mk)
    }

    // ---------- Completion ----------

    /** Coasters you could still ride or already have: operating ones plus anything logged. */
    private fun rideable(c: Coaster) = ridden(c) || life(c) == Life.Operating

    private fun scope(cs: List<Coaster>, us: Boolean) = cs.filter { rideable(it) && (!us || it.country == "US") }

    fun modelSet(key: String, us: Boolean) = scope(counted.filter { it.modelKey == key }, us)
    fun makeSet(make: String, us: Boolean) = scope(counted.filter { it.make == make }, us)
    fun parkSet(parkId: Long) = atPark(parkId).filter { rideable(it) }

    /** A set counts as done once every one is ridden. Models and makers need at least two to be worth a card. */
    fun done(set: List<Coaster>, min: Int = 2) = set.size >= min && set.all { ridden(it) }

    /** Parks to highlight on the map: somewhere you can still ride a starred coaster or a starred model. */
    fun wishedParkIds(wishes: Wishes): Set<Long> = counted.filter {
        it.parkId != null && !ridden(it) && life(it) == Life.Operating &&
            (wishes.coaster(it.id) || it.modelKey?.let(wishes::model) == true)
    }.map { it.parkId!! }.toSet()
}

/** Starred coasters (by id) and models (by make|model key). */
/** [auto] = coasters starred only because their model was starred (unstarring the model unstars these). */
data class Wishes(val coasters: Set<Long> = emptySet(), val models: Set<String> = emptySet(), val auto: Set<Long> = emptySet()) {
    fun coaster(id: Long) = id in coasters
    fun model(key: String) = key in models
}

/** Something just finished by logging a ride, shown as a popup card. */
sealed interface Milestone {
    val creditNo: Int
    data class ParkDone(val park: Park, val coasters: Int, val models: Int, val year: Int, override val creditNo: Int) : Milestone
    data class ModelDone(val make: String, val model: String, val world: Boolean, val count: Int, val date: LocalDate, override val creditNo: Int) : Milestone
    data class MakeDone(val make: String, val world: Boolean, val coasters: Int, val models: Int, val places: Int, override val creditNo: Int, val date: LocalDate) : Milestone
}

/** What logging [c] finished, comparing the stats from before and after the ride. */
fun milestones(before: Stats, after: Stats, c: Coaster): List<Milestone> {
    val out = ArrayList<Milestone>()
    val n = after.credits
    val usOnly = after.filters.usOnly
    c.parkId?.let { pid ->
        val set = after.parkSet(pid)
        if (after.done(set, 1) && !before.done(before.parkSet(pid), 1)) after.parks[pid]?.let { pk ->
            out += Milestone.ParkDone(pk, set.size, set.mapNotNull { it.modelKey }.toSet().size, after.today.year, n)
        }
    }
    // World beats US when both finish at once (or when every one is in the US anyway).
    fun scoped(get: Stats.(Boolean) -> List<Coaster>, make: (Boolean, List<Coaster>) -> Milestone) {
        val worldNow = !usOnly && after.done(after.get(false)) && !before.done(before.get(false))
        val usNow = c.country == "US" && after.done(after.get(true)) && !before.done(before.get(true))
        when {
            worldNow -> out += make(true, after.get(false))
            usNow -> out += make(false, after.get(true))
        }
    }
    c.modelKey?.let { key ->
        scoped({ modelSet(key, it) }) { w, set -> Milestone.ModelDone(c.make!!, c.model!!, w, set.size, after.today, n) }
    }
    c.make?.let { mk ->
        scoped({ makeSet(mk, it) }) { w, set ->
            val places = if (w) set.mapNotNull { it.country }.toSet().size else set.mapNotNull { it.parkId }.toSet().size
            Milestone.MakeDone(mk, w, set.size, set.mapNotNull { it.modelKey }.toSet().size, places, n, after.today)
        }
    }
    return out
}


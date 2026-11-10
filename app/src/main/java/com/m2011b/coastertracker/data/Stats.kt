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

    fun parkStats(usOnly: Boolean): List<ParkStat> {
        val never = models.filter { it.ridden == 0 }.map { it.key }.toSet()
        return counted.filter { it.parkId != null }.groupBy { it.parkId!! }.mapNotNull { (pid, cs) ->
            val park = parks[pid] ?: return@mapNotNull null
            if (usOnly && park.country != "US") return@mapNotNull null
            val open = cs.filter { !ridden(it) && life(it) == Life.Operating }
            ParkStat(park, open.mapNotNull { it.modelKey }.filter { it in never }.toSet().size, open.size, cs.count { ridden(it) })
        }.filter { it.unridden > 0 }
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
        (life(c) == Life.Operating || life(c) == Life.Sbno) && !Rules.isAlpine(c, alpine) &&
            (!filters.usOnly || c.country == "US") &&
            (filters.includeKiddie || !Rules.isKiddie(c, kiddie)))

    /** Parks with at least one loggable coaster. */
    val loggableParkIds: Set<Long> by lazy { coasters.filter { it.parkId != null && loggable(it) }.map { it.parkId!! }.toSet() }

    fun atPark(parkId: Long): List<Coaster> = coasters.filter { it.parkId == parkId && loggable(it) }
        .sortedWith(compareBy({ ridden(it) }, { it.name.lowercase() }))

    fun isNewModel(c: Coaster) = c.modelKey != null && !ridden(c) && (model(c.modelKey!!)?.ridden ?: 1) == 0
}

package com.m2011b.coastertracker.data

import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.*

data class Park(val id: Long, val name: String, val country: String?, val lat: Double?, val lon: Double?)

/**
 * One coaster. [status] is the stored Coasterpedia status mapped to
 * operating / sbno / defunct / upcoming / unknown. [make] and [model] are the cleaned values and never
 * change on updates; a user edit lives in the overrides table and is applied by the repo.
 */
data class Coaster(
    val id: Long,
    val name: String,
    val parkId: Long?,
    val parkName: String?,
    val country: String?,
    val locationKind: String?,
    val status: String,
    val sbnoSince: String?,
    val opened: String?,
    val closed: String?,
    val make: String?,
    val model: String?,
    val heightM: Double?,
    val speedKmh: Double?,
    val custom: Boolean = false,
) {
    val modelKey: String? get() = if (make != null && model != null) "$make|$model" else null
}

enum class Life { Operating, Sbno, Defunct, Upcoming, Unknown }

data class Filters(val includeDefunct: Boolean = false, val includeKiddie: Boolean = false)

object Rules {
    /** A coaster only counts as SBNO once it has been standing for this long; before that it's "operating". */
    const val SBNO_DAYS = 270L

    fun life(c: Coaster, today: LocalDate): Life = when (c.status) {
        "operating" -> Life.Operating
        "sbno" -> {
            val since = c.sbnoSince?.let { runCatching { LocalDate.parse(it.take(10)) }.getOrNull() }
            if (since != null && ChronoUnit.DAYS.between(since, today) >= SBNO_DAYS) Life.Sbno else Life.Operating
        }
        "defunct" -> Life.Defunct
        "upcoming" -> Life.Upcoming
        else -> Life.Unknown
    }

    /** Kiddie or carnival: travelling, a known kiddie/carnival model, or tiny and slow. */
    fun isKiddie(c: Coaster, kiddieModels: KiddieModels): Boolean {
        if (c.locationKind == "travelling") return true
        if (kiddieModels.matches(c.make, c.model)) return true
        val h = c.heightM ?: return false
        return h < 6.1 && (c.speedKmh == null || c.speedKmh < 40.0)
    }

    /** Whether a coaster counts in stats. Anything logged always counts. */
    fun counts(c: Coaster, ridden: Boolean, f: Filters, today: LocalDate, kiddie: KiddieModels): Boolean {
        if (ridden) return true
        val kidOk = f.includeKiddie || !isKiddie(c, kiddie)
        return when (life(c, today)) {
            Life.Operating -> kidOk
            Life.Sbno, Life.Defunct -> f.includeDefunct && kidOk
            else -> false
        }
    }

    fun miles(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r = 3958.8
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2).pow(2) + cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2).pow(2)
        return 2 * r * asin(sqrt(a))
    }

    /** Names compared loosely: case, punctuation and a leading "the" ignored. */
    fun norm(s: String?): String =
        (s ?: "").lowercase().replace(Regex("^the\\s+"), "").replace(Regex("[^a-z0-9]"), "")
}

class KiddieModels(private val pairs: Set<Pair<String, String>>) {
    fun matches(make: String?, model: String?): Boolean {
        if (make == null) return false
        if ((make to "*") in pairs) return true
        return model != null && (make to model) in pairs
    }
}

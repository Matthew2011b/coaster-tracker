package com.m2011b.coastertracker.data

import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.*

data class Park(
    val id: Long, val name: String, val country: String?, val lat: Double?, val lon: Double?,
    val city: String? = null, val state: String? = null,
) {
    /** Where the park is, for list rows: "Mason, OH" in the US, the country code elsewhere. */
    val place: String? get() = if (country == "US") {
        listOfNotNull(city, state?.let { US_STATES[it] ?: it }).joinToString(", ").ifEmpty { "US" }
    } else country
}

val US_STATES = mapOf(
    "Alabama" to "AL", "Alaska" to "AK", "Arizona" to "AZ", "Arkansas" to "AR", "California" to "CA", "Colorado" to "CO",
    "Connecticut" to "CT", "Delaware" to "DE", "District of Columbia" to "DC", "Florida" to "FL", "Georgia" to "GA",
    "Hawaii" to "HI", "Idaho" to "ID", "Illinois" to "IL", "Indiana" to "IN", "Iowa" to "IA", "Kansas" to "KS",
    "Kentucky" to "KY", "Louisiana" to "LA", "Maine" to "ME", "Maryland" to "MD", "Massachusetts" to "MA",
    "Michigan" to "MI", "Minnesota" to "MN", "Mississippi" to "MS", "Missouri" to "MO", "Montana" to "MT",
    "Nebraska" to "NE", "Nevada" to "NV", "New Hampshire" to "NH", "New Jersey" to "NJ", "New Mexico" to "NM",
    "New York" to "NY", "North Carolina" to "NC", "North Dakota" to "ND", "Ohio" to "OH", "Oklahoma" to "OK",
    "Oregon" to "OR", "Pennsylvania" to "PA", "Rhode Island" to "RI", "South Carolina" to "SC", "South Dakota" to "SD",
    "Tennessee" to "TN", "Texas" to "TX", "Utah" to "UT", "Vermont" to "VT", "Virginia" to "VA", "Washington" to "WA",
    "West Virginia" to "WV", "Wisconsin" to "WI", "Wyoming" to "WY", "Puerto Rico" to "PR",
)

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

data class Filters(val includeDefunct: Boolean = false, val includeKiddie: Boolean = false, val usOnly: Boolean = false)

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
    fun isKiddie(c: Coaster, kiddieModels: ModelSet): Boolean {
        if (c.locationKind == "travelling") return true
        if (kiddieModels.matches(c.make, c.model)) return true
        val h = c.heightM ?: return false
        return h < 6.1 && (c.speedKmh == null || c.speedKmh < 40.0)
    }

    private val ALPINE_NAME = Regex("luge|rodel|alpine slide|alpine coaster|mountain coaster|toboggan run", RegexOption.IGNORE_CASE)

    /** Alpine/mountain coasters and summer toboggans: never counted unless logged. */
    fun isAlpine(c: Coaster, alpine: ModelSet): Boolean {
        if (alpine.matches(c.make, c.model)) return true
        if (c.model != null) return false
        if (c.make == "Wiegand") return !c.name.contains("metro", ignoreCase = true)  // Wiegand's unnamed tracks are all alpine
        return ALPINE_NAME.containsMatchIn(c.name)
    }

    /** Whether a coaster counts in stats. Anything logged always counts. */
    fun counts(c: Coaster, ridden: Boolean, f: Filters, today: LocalDate, kiddie: ModelSet, alpine: ModelSet = ModelSet.EMPTY): Boolean {
        if (ridden) return true
        if (isAlpine(c, alpine)) return false
        if (f.usOnly && c.country != "US") return false
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

/** A set of make/model pairs from normalize.json; model "*" matches every model of that make. */
class ModelSet(private val pairs: Set<Pair<String, String>>) {
    companion object { val EMPTY = ModelSet(emptySet()) }
    fun matches(make: String?, model: String?): Boolean {
        if (make == null) return false
        if ((make to "*") in pairs) return true
        return model != null && (make to model) in pairs
    }
}

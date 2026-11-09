package com.m2011b.coastertracker.data

/** Ride log CSV. Export: coaster_id,coaster,park,make,model,date. Import accepts that or any file with Coaster and Park columns. */
object Csv {
    const val HEADER = "coaster_id,coaster,park,make,model,date"

    fun export(coasters: List<Coaster>, rides: Map<Long, String?>): String {
        val byId = coasters.associateBy { it.id }
        val rows = rides.entries.mapNotNull { (id, date) -> byId[id]?.let { it to date } }
            .sortedWith(compareBy({ it.first.parkName ?: "" }, { it.first.name }))
        return buildString {
            appendLine(HEADER)
            rows.forEach { (c, d) ->
                appendLine(listOf(c.id.toString(), c.name, c.parkName ?: "", c.make ?: "", c.model ?: "", d ?: "").joinToString(",") { q(it) })
            }
        }
    }

    data class ImportRow(val id: Long?, val coaster: String, val park: String, val date: String?)
    data class Result(val matched: List<Pair<Long, String?>>, val unmatched: List<ImportRow>)

    fun parse(text: String): List<ImportRow> {
        val lines = parseLines(text.removePrefix("﻿")).filter { row -> row.any { it.isNotBlank() } }
        if (lines.isEmpty()) return emptyList()
        val head = lines.first().map { it.trim().lowercase() }
        fun col(vararg names: String) = head.indexOfFirst { it in names }
        val iId = col("coaster_id", "id")
        val iName = col("coaster", "name", "coaster name")
        val iPark = col("park", "amusement park")
        val iDate = col("date", "ridden", "ridden_on", "first ride")
        if (iName < 0) return emptyList()
        return lines.drop(1).mapNotNull { r ->
            val name = r.getOrNull(iName)?.trim().orEmpty()
            if (name.isEmpty()) null else ImportRow(
                r.getOrNull(iId)?.trim()?.toLongOrNull(), name, r.getOrNull(iPark)?.trim().orEmpty(),
                r.getOrNull(iDate)?.trim()?.ifBlank { null },
            )
        }
    }

    /** Match by id first, then by coaster name + park name, then by a name that is unique worldwide. */
    fun match(rows: List<ImportRow>, coasters: List<Coaster>): Result {
        val byId = coasters.associateBy { it.id }
        val byNamePark = coasters.groupBy { Rules.norm(it.name) + "@" + Rules.norm(it.parkName) }
        val byName = coasters.groupBy { Rules.norm(it.name) }
        val matched = mutableListOf<Pair<Long, String?>>()
        val unmatched = mutableListOf<ImportRow>()
        rows.forEach { r ->
            val hit = r.id?.let { byId[it] }
                ?: byNamePark[Rules.norm(r.coaster) + "@" + Rules.norm(r.park)]?.let { pickLive(it) }
                ?: byName[Rules.norm(r.coaster)]?.takeIf { it.size == 1 }?.first()
            if (hit != null) matched += hit.id to r.date else unmatched += r
        }
        return Result(matched, unmatched)
    }

    /** Same name at the same park (an old and a new version): prefer the operating one. */
    private fun pickLive(cs: List<Coaster>) = cs.firstOrNull { it.status == "operating" } ?: cs.firstOrNull { it.status == "sbno" } ?: cs.first()

    private fun q(s: String) = if (s.any { it == ',' || it == '"' || it == '\n' }) "\"" + s.replace("\"", "\"\"") + "\"" else s

    private fun parseLines(text: String): List<List<String>> {
        val out = mutableListOf<List<String>>()
        var row = mutableListOf<String>()
        val cur = StringBuilder()
        var inQ = false
        var i = 0
        while (i < text.length) {
            val ch = text[i]
            when {
                inQ && ch == '"' && i + 1 < text.length && text[i + 1] == '"' -> { cur.append('"'); i++ }
                ch == '"' -> inQ = !inQ
                !inQ && ch == ',' -> { row.add(cur.toString()); cur.clear() }
                !inQ && (ch == '\n' || ch == '\r') -> {
                    if (ch == '\r' && i + 1 < text.length && text[i + 1] == '\n') i++
                    row.add(cur.toString()); cur.clear(); out.add(row); row = mutableListOf()
                }
                else -> cur.append(ch)
            }
            i++
        }
        if (cur.isNotEmpty() || row.isNotEmpty()) { row.add(cur.toString()); out.add(row) }
        return out
    }
}

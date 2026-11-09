package com.m2011b.coastertracker.data

import org.json.JSONObject

/**
 * Cleans manufacturer and model names from Coasterpedia the same way the bundled snapshot was
 * cleaned (assets/normalize.json), so coasters added by an update land in the same buckets.
 */
class Normalizer(json: String) {
    private val aliases: Map<String, String>
    private val modelMap: Map<String, Map<String, String>>
    private val prefixMap: Map<String, List<Pair<Regex, String>>>
    private val ptc: String
    val kiddie: KiddieModels

    init {
        val o = JSONObject(json)
        val a = o.getJSONObject("aliases")
        aliases = a.keys().asSequence().associateWith { a.getString(it) }
        val mm = o.getJSONObject("modelMap")
        modelMap = mm.keys().asSequence().associateWith { mk ->
            val m = mm.getJSONObject(mk); m.keys().asSequence().associateWith { m.getString(it) }
        }
        val pm = o.getJSONObject("prefixMap")
        prefixMap = pm.keys().asSequence().associateWith { mk ->
            val arr = pm.getJSONArray(mk)
            (0 until arr.length()).map { i -> val p = arr.getJSONArray(i); Regex(p.getString(0)) to p.getString(1) }
        }
        ptc = o.getString("ptcCustom")
        val k = o.getJSONArray("kiddieModels")
        kiddie = KiddieModels((0 until k.length()).map { i -> val p = k.getJSONArray(i); p.getString(0) to p.getString(1) }.toSet())
    }

    fun make(raw: String?): String? = raw?.trim()?.takeIf { it.isNotEmpty() }?.let { aliases[it] ?: it }

    fun model(make: String?, raw: String?): String? {
        var m = raw?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        if (make == ptc && m == "Wooden Coaster") return null
        val map = modelMap[make].orEmpty()
        m = map[m] ?: m
        prefixMap[make]?.forEach { (re, fam) -> if (re.containsMatchIn(m) && re.find(m)?.range?.first == 0) return fam }
        m = m.replace(Regex("\\s*\\((product)\\)$"), "")
        m = m.replace(Regex("\\s*\\([A-Z]{1,4}[- ]?\\d[\\w/.-]*\\)$"), "")
        m = map[m] ?: m
        return m.trim().ifEmpty { null }
    }
}

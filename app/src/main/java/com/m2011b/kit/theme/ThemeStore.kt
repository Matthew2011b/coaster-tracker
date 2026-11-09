package com.m2011b.kit.theme

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import java.util.Locale

enum class ThemeMode { System, Light, Dark }

/**
 * Persisted, observable palette: one color per token for light mode and one for dark mode,
 * plus the System/Light/Dark choice. Changes recompose the whole app immediately.
 */
class ThemeStore(context: Context, val tokens: List<ColorToken>, prefsName: String = "kit_theme") {
    private val prefs = context.getSharedPreferences(prefsName, Context.MODE_PRIVATE)

    init {
        require(tokens.map { it.key }.toSet().size == tokens.size) { "Duplicate ColorToken keys" }
    }

    private val light = mutableStateMapOf<String, Color>().apply { tokens.forEach { put(it.key, load(false, it)) } }
    private val dark = mutableStateMapOf<String, Color>().apply { tokens.forEach { put(it.key, load(true, it)) } }

    var mode by mutableStateOf(runCatching { ThemeMode.valueOf(prefs.getString("mode", null)!!) }.getOrDefault(ThemeMode.System))
        private set

    private fun prefKey(dark: Boolean, key: String) = "${if (dark) "d" else "l"}_$key"
    private fun default(dark: Boolean, t: ColorToken) = if (dark) t.dark else t.light
    private fun load(dark: Boolean, t: ColorToken): Color {
        val k = prefKey(dark, t.key)
        return if (prefs.contains(k)) Color(prefs.getInt(k, 0)) else default(dark, t)
    }
    private fun map(dark: Boolean) = if (dark) this.dark else light

    fun get(dark: Boolean, t: ColorToken): Color = map(dark)[t.key] ?: default(dark, t)
    fun snapshot(dark: Boolean): Map<String, Color> = map(dark).toMap()

    fun set(dark: Boolean, t: ColorToken, c: Color) {
        map(dark)[t.key] = c
        prefs.edit().putInt(prefKey(dark, t.key), c.toArgb()).apply()
    }

    fun isDefault(dark: Boolean, t: ColorToken) = get(dark, t) == default(dark, t)

    fun reset(dark: Boolean) {
        val e = prefs.edit()
        tokens.forEach { map(dark)[it.key] = default(dark, it); e.remove(prefKey(dark, it.key)) }
        e.apply()
    }

    fun changeMode(m: ThemeMode) { mode = m; prefs.edit().putString("mode", m.name).apply() }

    // ---------------------------------------------------------------- theme codes

    /**
     * A shareable text form of the whole theme, e.g.
     * `THEME1|System|L:Accent=1F5C99,Text=25282A|D:Accent=5B9BD5,Text=F3F3F3`.
     * Paste it into another app built on this kit to copy the look; unknown keys are ignored.
     */
    fun exportCode(): String {
        fun side(d: Boolean) = tokens.joinToString(",") { "${it.key}=${hex6(get(d, it))}" }
        return "THEME1|${mode.name}|L:${side(false)}|D:${side(true)}"
    }

    /** Applies a theme code. Returns how many colors were applied, or null if the code isn't valid. */
    fun importCode(code: String): Int? {
        val parts = code.trim().split("|")
        if (parts.size < 3 || parts[0] != "THEME1") return null
        val byKey = tokens.associateBy { it.key }
        var applied = 0
        for (part in parts.drop(2)) {
            val d = when { part.startsWith("L:") -> false; part.startsWith("D:") -> true; else -> continue }
            for (pair in part.substring(2).split(",")) {
                val (k, v) = pair.split("=").takeIf { it.size == 2 } ?: continue
                val t = byKey[k] ?: continue
                val rgb = v.takeIf { it.length == 6 }?.toLongOrNull(16) ?: continue
                set(d, t, Color(0xFF000000 or rgb)); applied++
            }
        }
        runCatching { ThemeMode.valueOf(parts[1]) }.getOrNull()?.let { changeMode(it) }
        return applied
    }

    companion object {
        fun hex6(c: Color): String = String.format(Locale.US, "%06X", c.toArgb() and 0xFFFFFF)
    }
}

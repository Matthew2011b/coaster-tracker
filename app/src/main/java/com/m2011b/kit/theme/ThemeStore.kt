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
 * Persisted, observable palette: core colors for light and dark mode, the System/Light/Dark
 * choice, and an "advanced" switch. With advanced off, derived tokens are always worked out from
 * the core colors. With it on, a derived token uses the user's own pick if they set one.
 * Turning advanced off keeps those picks, so turning it back on restores them.
 */
class ThemeStore(context: Context, val tokens: List<ColorToken>, prefsName: String = "kit_theme") {
    private val prefs = context.getSharedPreferences(prefsName, Context.MODE_PRIVATE)

    init {
        require(tokens.map { it.key }.toSet().size == tokens.size) { "Duplicate ColorToken keys" }
    }

    // Only colors the user actually set are kept here; anything missing falls back to default/derived.
    private val light = mutableStateMapOf<String, Color>().apply { tokens.forEach { t -> load(false, t)?.let { put(t.key, it) } } }
    private val dark = mutableStateMapOf<String, Color>().apply { tokens.forEach { t -> load(true, t)?.let { put(t.key, it) } } }

    var mode by mutableStateOf(runCatching { ThemeMode.valueOf(prefs.getString("mode", null)!!) }.getOrDefault(ThemeMode.System))
        private set

    var advanced by mutableStateOf(prefs.getBoolean("advanced", false))
        private set

    val core: List<ColorToken> get() = tokens.filter { !it.derived }
    val derivedTokens: List<ColorToken> get() = tokens.filter { it.derived }

    private fun prefKey(dark: Boolean, key: String) = "${if (dark) "d" else "l"}_$key"
    private fun load(dark: Boolean, t: ColorToken): Color? {
        val k = prefKey(dark, t.key)
        return if (prefs.contains(k)) Color(prefs.getInt(k, 0)) else null
    }
    private fun map(dark: Boolean) = if (dark) this.dark else light

    /** The color in use for [t], after derivation. */
    fun get(dark: Boolean, t: ColorToken): Color = resolve(dark, t, HashMap())

    private fun resolve(dark: Boolean, t: ColorToken, memo: HashMap<String, Color>): Color = memo[t.key] ?: run {
        val own = map(dark)[t.key]
        val d = t.derive
        val c = when {
            d == null -> own ?: if (dark) t.dark else t.light
            advanced && own != null -> own
            else -> d({ resolve(dark, it, memo) }, dark)
        }
        memo[t.key] = c
        c
    }

    fun snapshot(dark: Boolean): Map<String, Color> {
        val memo = HashMap<String, Color>()
        return tokens.associate { it.key to resolve(dark, it, memo) }
    }

    fun set(dark: Boolean, t: ColorToken, c: Color) {
        map(dark)[t.key] = c
        prefs.edit().putInt(prefKey(dark, t.key), c.toArgb()).apply()
    }

    /** Drops the user's pick for [t]: core tokens go back to default, derived ones back to automatic. */
    fun clear(dark: Boolean, t: ColorToken) {
        map(dark).remove(t.key)
        prefs.edit().remove(prefKey(dark, t.key)).apply()
    }

    /** True when [t] isn't using a user pick (default for core, automatic for derived). */
    fun isDefault(dark: Boolean, t: ColorToken) = map(dark)[t.key] == null || (t.derived && !advanced)

    fun reset(dark: Boolean) {
        val e = prefs.edit()
        tokens.forEach { map(dark).remove(it.key); e.remove(prefKey(dark, it.key)) }
        e.apply()
    }

    fun changeMode(m: ThemeMode) { mode = m; prefs.edit().putString("mode", m.name).apply() }
    fun changeAdvanced(on: Boolean) { advanced = on; prefs.edit().putBoolean("advanced", on).apply() }

    // ---------------------------------------------------------------- theme codes

    /**
     * A shareable text form of the theme, e.g.
     * `THEME1|System|L:Accent=1F5C99,Text=25282A|D:Accent=5B9BD5,Text=F3F3F3`.
     * Carries every core color, plus advanced picks when advanced colors are on.
     * Paste it into another app built on this kit to copy the look; unknown keys are ignored.
     */
    fun exportCode(): String {
        fun side(d: Boolean) = tokens.filter { !it.derived || (advanced && map(d)[it.key] != null) }
            .joinToString(",") { "${it.key}=${hex6(get(d, it))}" }
        return "THEME1|${mode.name}|L:${side(false)}|D:${side(true)}"
    }

    /** Applies a theme code. Returns how many colors were applied, or null if the code isn't valid. */
    fun importCode(code: String): Int? {
        val parts = code.trim().split("|")
        if (parts.size < 3 || parts[0] != "THEME1") return null
        val byKey = tokens.associateBy { it.key }
        var applied = 0
        var gotDerived = false
        for (part in parts.drop(2)) {
            val d = when { part.startsWith("L:") -> false; part.startsWith("D:") -> true; else -> continue }
            for (pair in part.substring(2).split(",")) {
                val (k, v) = pair.split("=").takeIf { it.size == 2 } ?: continue
                val t = byKey[k] ?: continue
                val rgb = v.takeIf { it.length == 6 }?.toLongOrNull(16) ?: continue
                set(d, t, Color(0xFF000000 or rgb)); applied++
                if (t.derived) gotDerived = true
            }
        }
        if (gotDerived) changeAdvanced(true)
        runCatching { ThemeMode.valueOf(parts[1]) }.getOrNull()?.let { changeMode(it) }
        return applied
    }

    companion object {
        fun hex6(c: Color): String = String.format(Locale.US, "%06X", c.toArgb() and 0xFFFFFF)
    }
}

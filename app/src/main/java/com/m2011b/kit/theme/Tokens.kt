package com.m2011b.kit.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance

/**
 * One color. [key] is the stable id used for saving and theme codes, so never rename it once an
 * app ships (users' custom colors are stored under it). [label] is what the theme menu shows.
 *
 * A token with [derive] is worked out from other tokens, so users only see it under
 * "Advanced colors". Its light/dark values are only fallbacks. [derive] gets a resolver for other
 * tokens and whether the dark palette is being built.
 */
class ColorToken(
    val key: String,
    val label: String,
    private val lightArgb: Long,
    private val darkArgb: Long,
    val derive: ((get: (ColorToken) -> Color, dark: Boolean) -> Color)? = null,
) {
    val light: Color get() = Color(lightArgb)
    val dark: Color get() = Color(darkArgb)
    val derived: Boolean get() = derive != null
}

/** Helpers for derived tokens. */
object Mix {
    /** [a] moved [t] of the way toward [b]. */
    fun toward(a: Color, b: Color, t: Float): Color = lerp(a, b, t)

    private fun contrast(a: Color, b: Color): Float {
        val (hi, lo) = listOf(a.luminance(), b.luminance()).sortedDescending()
        return (hi + 0.05f) / (lo + 0.05f)
    }

    /** White or near-black, whichever reads better on [bg]. */
    fun onColor(bg: Color): Color {
        val white = Color.White
        val black = Color(0xFF14181B)
        return if (contrast(bg, white) >= contrast(bg, black)) white else black
    }
}

/**
 * The standard tokens every kit component uses. Apps pass `Std.all + theirOwnTokens` to ThemeStore.
 * Six are core (what users edit); the rest derive from them unless "Advanced colors" is on.
 */
object Std {
    val Background = ColorToken("Background", "Page background", 0xFFF3F3F3, 0xFF141617)
    val Surface = ColorToken("Surface", "Boxes, lists & nav bar", 0xFFFFFFFF, 0xFF1F2224)
    val Text = ColorToken("Text", "Text & icons", 0xFF25282A, 0xFFF3F3F3)
    val Header = ColorToken("Header", "Top bar", 0xFF25282A, 0xFF000000)
    val Accent = ColorToken("Accent", "Accent (buttons, selected tab)", 0xFF1F5C99, 0xFF5B9BD5)
    val Ok = ColorToken("Ok", "Ridden & complete", 0xFF1E7B46, 0xFF5CCB8A)

    val Muted = ColorToken("Muted", "Labels & secondary text", 0xFF707372, 0xFFA3A6A8,
        derive = { g, _ -> Mix.toward(g(Text), g(Surface), 0.35f) })
    val Line = ColorToken("Line", "Borders", 0xFFD3D3D3, 0xFF3A3E41,
        derive = { g, _ -> Mix.toward(g(Text), g(Surface), 0.82f) })
    val HeaderText = ColorToken("HeaderText", "Top bar text", 0xFFFFFFFF, 0xFFFFFFFF,
        derive = { g, _ -> Mix.onColor(g(Header)) })
    val ButtonText = ColorToken("ButtonText", "Button text", 0xFFFFFFFF, 0xFF0E1A26,
        derive = { g, _ -> Mix.onColor(g(Accent)) })
    val AccentSoft = ColorToken("AccentSoft", "Selected tab highlight", 0xFFDCE8F4, 0xFF263A4F,
        derive = { g, d -> Mix.toward(g(Accent), g(Surface), if (d) 0.75f else 0.85f) })
    val Err = ColorToken("Err", "Error / warning", 0xFFB3261E, 0xFFFF8A80,
        derive = { _, d -> Color(if (d) 0xFFFF8A80 else 0xFFB3261E) })

    val core = listOf(Background, Surface, Text, Header, Accent, Ok)
    val all = core + listOf(Muted, Line, HeaderText, ButtonText, AccentSoft, Err)
}

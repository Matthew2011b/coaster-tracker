package com.m2011b.kit.theme

import androidx.compose.ui.graphics.Color

/**
 * One editable color. [key] is the stable id used for saving and theme codes, so never rename it
 * once an app ships (users' custom colors are stored under it). [label] is what the theme menu shows.
 */
data class ColorToken(val key: String, val label: String, private val lightArgb: Long, private val darkArgb: Long) {
    val light: Color get() = Color(lightArgb)
    val dark: Color get() = Color(darkArgb)
}

/**
 * The standard tokens every kit component uses. Apps pass `Std.all + theirOwnTokens` to ThemeStore.
 * Defaults: charcoal header, blue accent, IBM-style greys. Tuned for 4.5:1 text contrast.
 */
object Std {
    val Background = ColorToken("Background", "Page background", 0xFFF3F3F3, 0xFF141617)
    val Surface = ColorToken("Surface", "Boxes, cards & popups", 0xFFFFFFFF, 0xFF1F2224)
    val Text = ColorToken("Text", "Text", 0xFF25282A, 0xFFF3F3F3)
    val Muted = ColorToken("Muted", "Labels & secondary text", 0xFF707372, 0xFFA3A6A8)
    val Line = ColorToken("Line", "Borders (idle)", 0xFFD3D3D3, 0xFF3A3E41)
    val Header = ColorToken("Header", "Top bar", 0xFF25282A, 0xFF000000)
    val HeaderText = ColorToken("HeaderText", "Top bar text & icon", 0xFFFFFFFF, 0xFFFFFFFF)
    val Accent = ColorToken("Accent", "Accent (buttons, active box)", 0xFF1F5C99, 0xFF5B9BD5)
    val ButtonText = ColorToken("ButtonText", "Button text", 0xFFFFFFFF, 0xFF0E1A26)
    val Ok = ColorToken("Ok", "Valid / good", 0xFF1E7B46, 0xFF5CCB8A)
    val Err = ColorToken("Err", "Error / warning", 0xFFB3261E, 0xFFFF8A80)
    val Menu = ColorToken("Menu", "Side menu background", 0xFFFFFFFF, 0xFF1A1D1F)
    val MenuSelected = ColorToken("MenuSelected", "Side menu selected item", 0xFFE4E4E4, 0xFF33373A)

    val all = listOf(Background, Surface, Text, Muted, Line, Header, HeaderText, Accent, ButtonText, Ok, Err, Menu, MenuSelected)
}

package com.m2011b.coastertracker

import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import com.m2011b.kit.theme.ColorToken
import com.m2011b.kit.theme.KitFonts
import com.m2011b.kit.theme.Std

/** This app's colors: the kit's standard tokens plus the ones below. All show up in the theme menu. */
object AppTokens {
    val NewBg = ColorToken("NewBg", "New model highlight", 0xFFFFF4E5, 0xFF3A2C17)
    val NewInk = ColorToken("NewInk", "New model text", 0xFF8A4B00, 0xFFFFC27A)
    val Track = ColorToken("Track", "Progress bar track", 0xFFE4E6E7, 0xFF33373A)

    val all = Std.all + listOf(NewBg, NewInk, Track)
}

val AppFonts = KitFonts(
    sans = FontFamily(Font(R.font.plex_sans_regular, FontWeight.Normal), Font(R.font.plex_sans_semibold, FontWeight.SemiBold)),
    mono = FontFamily(Font(R.font.plex_mono_regular, FontWeight.Normal), Font(R.font.plex_mono_semibold, FontWeight.SemiBold)),
)

package com.m2011b.coastertracker

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import com.m2011b.kit.theme.ColorToken
import com.m2011b.kit.theme.KitFonts
import com.m2011b.kit.theme.Mix
import com.m2011b.kit.theme.Std

/** This app's colors. All derive from the core colors unless "Advanced colors" is on. */
object AppTokens {
    /** The track orange with some of its color taken out, the base for the new-model highlight. */
    private fun mutedOrange(g: (ColorToken) -> Color): Color {
        val o = g(Std.HeaderTrack)
        val grey = Color(o.luminance(), o.luminance(), o.luminance())
        return Mix.toward(o, grey, 0.4f)
    }
    val NewBg = ColorToken("NewBg", "New model highlight", 0xFFF6EDE3, 0xFF3A3129,
        derive = { g, d -> Mix.toward(mutedOrange(g), g(Std.Surface), if (d) 0.78f else 0.82f) })
    val NewInk = ColorToken("NewInk", "New model text", 0xFF8A5A2E, 0xFFE0B58A,
        derive = { g, d -> Mix.toward(mutedOrange(g), g(Std.Text), if (d) 0.2f else 0.5f) })
    val Track = ColorToken("Track", "Progress bar track", 0xFFE4E6E7, 0xFF33373A,
        derive = { g, _ -> Mix.toward(g(Std.Text), g(Std.Surface), 0.88f) })

    /** Ridden tiles on the quick log list. */
    val OkSoft = ColorToken("OkSoft", "Ridden tile", 0xFFE3F3E6, 0xFF1F3A26,
        derive = { g, d -> Mix.toward(g(Std.Ok), g(Std.Surface), if (d) 0.78f else 0.86f) })
    /** Wishlist stars and the twinkle on wishlist parks. */
    val Wish = ColorToken("Wish", "Wishlist star", 0xFFFFC93C, 0xFFFFC93C,
        derive = { _, _ -> Color(0xFFFFC93C) })

    // Parks map: page color for land, plus these three.
    val MapWater = ColorToken("MapWater", "Map water", 0xFFC4D8E8, 0xFF1E344A,
        derive = { g, d -> Mix.toward(Color(0xFF4F86B8), g(Std.Background), if (d) 0.62f else 0.68f) })
    val MapRoad = ColorToken("MapRoad", "Map interstates", 0xFFBEC2C5, 0xFF464C52,
        derive = { g, d -> Mix.toward(g(Std.Text), g(Std.Background), if (d) 0.72f else 0.7f) })
    val MapInk = ColorToken("MapInk", "Map towns & borders", 0xFF6E7275, 0xFFA0A5AA,
        derive = { g, d -> Mix.toward(g(Std.Text), g(Std.Background), if (d) 0.35f else 0.45f) })

    val all = Std.all + listOf(NewBg, NewInk, Track, OkSoft, Wish, MapWater, MapRoad, MapInk)
}

val AppFonts = KitFonts(
    sans = FontFamily(Font(R.font.plex_sans_regular, FontWeight.Normal), Font(R.font.plex_sans_semibold, FontWeight.SemiBold)),
    mono = FontFamily(Font(R.font.plex_mono_regular, FontWeight.Normal), Font(R.font.plex_mono_semibold, FontWeight.SemiBold)),
)

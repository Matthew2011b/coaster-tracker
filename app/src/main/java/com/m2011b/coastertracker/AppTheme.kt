package com.m2011b.coastertracker

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import com.m2011b.kit.theme.ColorToken
import com.m2011b.kit.theme.KitFonts
import com.m2011b.kit.theme.Mix
import com.m2011b.kit.theme.Std

/** This app's colors. All derive from the core colors unless "Advanced colors" is on. */
object AppTokens {
    val NewBg = ColorToken("NewBg", "New model highlight", 0xFFFFF4E5, 0xFF3A2C17,
        derive = { g, d -> Mix.toward(g(Std.Accent), g(Std.Surface), if (d) 0.8f else 0.9f) })
    val NewInk = ColorToken("NewInk", "New model text", 0xFF8A4B00, 0xFFFFC27A,
        derive = { g, _ -> g(Std.Accent) })
    val Track = ColorToken("Track", "Progress bar track", 0xFFE4E6E7, 0xFF33373A,
        derive = { g, _ -> Mix.toward(g(Std.Text), g(Std.Surface), 0.88f) })

    // Parks map: page color for land, plus these three.
    val MapWater = ColorToken("MapWater", "Map water", 0xFFC4D8E8, 0xFF1E344A,
        derive = { g, d -> Mix.toward(Color(0xFF4F86B8), g(Std.Background), if (d) 0.62f else 0.68f) })
    val MapRoad = ColorToken("MapRoad", "Map interstates", 0xFFBEC2C5, 0xFF464C52,
        derive = { g, d -> Mix.toward(g(Std.Text), g(Std.Background), if (d) 0.72f else 0.7f) })
    val MapInk = ColorToken("MapInk", "Map towns & borders", 0xFF6E7275, 0xFFA0A5AA,
        derive = { g, d -> Mix.toward(g(Std.Text), g(Std.Background), if (d) 0.35f else 0.45f) })

    val all = Std.all + listOf(NewBg, NewInk, Track, MapWater, MapRoad, MapInk)
}

val AppFonts = KitFonts(
    sans = FontFamily(Font(R.font.plex_sans_regular, FontWeight.Normal), Font(R.font.plex_sans_semibold, FontWeight.SemiBold)),
    mono = FontFamily(Font(R.font.plex_mono_regular, FontWeight.Normal), Font(R.font.plex_mono_semibold, FontWeight.SemiBold)),
)

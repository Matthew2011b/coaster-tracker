package com.m2011b.kit.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.core.view.WindowCompat

/** The resolved colors for the current light/dark state. Read it with `LocalPalette.current`. */
class Palette(private val colors: Map<String, Color>, val isDark: Boolean) {
    operator fun get(t: ColorToken): Color = colors[t.key] ?: Color.Magenta  // magenta = token missing from ThemeStore
    val bg get() = this[Std.Background]
    val surface get() = this[Std.Surface]
    val ink get() = this[Std.Text]
    val muted get() = this[Std.Muted]
    val line get() = this[Std.Line]
    val header get() = this[Std.Header]
    val headerInk get() = this[Std.HeaderText]
    val accent get() = this[Std.Accent]
    val btnInk get() = this[Std.ButtonText]
    val ok get() = this[Std.Ok]
    val err get() = this[Std.Err]
    val menu get() = this[Std.Menu]
    val menuSel get() = this[Std.MenuSelected]
}

/** Fonts the kit uses. Apps pass their bundled fonts; defaults are the system fonts. */
data class KitFonts(val sans: FontFamily = FontFamily.Default, val mono: FontFamily = FontFamily.Monospace)

val LocalPalette = staticCompositionLocalOf<Palette> { error("Wrap the UI in KitTheme") }
val LocalKitFonts = staticCompositionLocalOf { KitFonts() }

/**
 * Applies the ThemeStore's colors to the whole app, including stock Material components
 * (dialogs, menus, sliders), and keeps the status/nav bar icons readable.
 */
@Composable
fun KitTheme(store: ThemeStore, fonts: KitFonts = KitFonts(), content: @Composable () -> Unit) {
    val dark = when (store.mode) {
        ThemeMode.System -> isSystemInDarkTheme()
        ThemeMode.Light -> false
        ThemeMode.Dark -> true
    }
    val p = Palette(store.snapshot(dark), dark)

    val base = if (dark) darkColorScheme() else lightColorScheme()
    val scheme = base.copy(
        primary = p.accent, onPrimary = p.btnInk, primaryContainer = p.accent, onPrimaryContainer = p.btnInk,
        inversePrimary = p.accent,
        secondary = p.accent, onSecondary = p.btnInk, secondaryContainer = p.menuSel, onSecondaryContainer = p.ink,
        tertiary = p.accent, onTertiary = p.btnInk, tertiaryContainer = p.menuSel, onTertiaryContainer = p.ink,
        background = p.bg, onBackground = p.ink,
        surface = p.surface, onSurface = p.ink, surfaceVariant = p.surface, onSurfaceVariant = p.muted,
        surfaceTint = Color.Transparent, inverseSurface = p.ink, inverseOnSurface = p.bg,
        error = p.err, onError = p.surface, errorContainer = p.surface, onErrorContainer = p.err,
        outline = p.line, outlineVariant = p.line,
        surfaceBright = p.surface, surfaceDim = p.surface, surfaceContainer = p.surface,
        surfaceContainerHigh = p.surface, surfaceContainerHighest = p.surface,
        surfaceContainerLow = p.surface, surfaceContainerLowest = p.surface,
    )
    val t = Typography()
    fun TextStyle.f() = copy(fontFamily = fonts.sans)
    val type = Typography(
        displayLarge = t.displayLarge.f(), displayMedium = t.displayMedium.f(), displaySmall = t.displaySmall.f(),
        headlineLarge = t.headlineLarge.f(), headlineMedium = t.headlineMedium.f(), headlineSmall = t.headlineSmall.f(),
        titleLarge = t.titleLarge.f(), titleMedium = t.titleMedium.f(), titleSmall = t.titleSmall.f(),
        bodyLarge = t.bodyLarge.f(), bodyMedium = t.bodyMedium.f(), bodySmall = t.bodySmall.f(),
        labelLarge = t.labelLarge.f(), labelMedium = t.labelMedium.f(), labelSmall = t.labelSmall.f(),
    )

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = p.header.luminance() > 0.5f
                isAppearanceLightNavigationBars = p.bg.luminance() > 0.5f
            }
        }
    }

    CompositionLocalProvider(LocalPalette provides p, LocalKitFonts provides fonts) {
        MaterialTheme(colorScheme = scheme, typography = type, content = content)
    }
}

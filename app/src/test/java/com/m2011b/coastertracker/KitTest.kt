package com.m2011b.coastertracker

import android.content.Context
import androidx.compose.ui.graphics.Color
import androidx.test.core.app.ApplicationProvider
import com.m2011b.kit.theme.ColorToken
import com.m2011b.kit.theme.Std
import com.m2011b.kit.theme.ThemeMode
import com.m2011b.kit.theme.ThemeStore
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class KitTest {
    private val ctx = ApplicationProvider.getApplicationContext<Context>()

    @Test fun themeCodeRoundTripsBetweenApps() {
        val a = ThemeStore(ctx, AppTokens.all, "a")
        a.set(false, Std.Accent, Color(0xFFCF1F2F))
        a.set(true, AppTokens.Track, Color(0xFF0B7A75))
        a.changeMode(ThemeMode.Dark)
        val code = a.exportCode()
        assertTrue(code.startsWith("THEME1|Dark|L:"))

        // An app with fewer tokens takes what it knows and ignores the rest.
        val b = ThemeStore(ctx, Std.all, "b")
        val applied = b.importCode(code)
        assertEquals(Std.core.size * 2, applied)  // core colors only: advanced picks stay home while advanced is off
        assertEquals(Color(0xFFCF1F2F), b.get(false, Std.Accent))
        assertEquals(ThemeMode.Dark, b.mode)

        // Persists across a restart.
        val b2 = ThemeStore(ctx, Std.all, "b")
        assertEquals(Color(0xFFCF1F2F), b2.get(false, Std.Accent))
        assertFalse(b2.isDefault(false, Std.Accent))

        b2.reset(false)
        assertTrue(b2.isDefault(false, Std.Accent))
        assertEquals(Std.Accent.light, ThemeStore(ctx, Std.all, "b").get(false, Std.Accent))
    }

    @Test fun badCodesAreRejected() {
        val s = ThemeStore(ctx, Std.all, "c")
        assertNull(s.importCode("hello"))
        assertEquals(0, s.importCode("THEME1|System|L:Nope=123456,Accent=XYZ"))
        assertTrue(s.isDefault(false, Std.Accent))
    }

    @Test(expected = IllegalArgumentException::class)
    fun duplicateKeysFailFast() {
        ThemeStore(ctx, Std.all + ColorToken("Accent", "dup", 0xFF000000, 0xFF000000), "d")
    }

    @Test fun derivedColorsFollowCoreUntilAdvancedIsOn() {
        val s = ThemeStore(ctx, AppTokens.all, "e")
        val autoMuted = s.get(false, Std.Muted)
        s.set(false, Std.Text, Color(0xFF000000))
        assertNotEquals(autoMuted, s.get(false, Std.Muted))  // follows Text
        assertEquals(Color.White, s.get(false, Std.HeaderText))  // white on the charcoal header
        s.set(false, Std.Header, Color(0xFFFFFFFF))
        assertNotEquals(Color.White, s.get(false, Std.HeaderText))  // dark text on a white header

        // An advanced pick is ignored until advanced is on, and kept when it goes off again.
        s.set(false, AppTokens.Track, Color(0xFF0B7A75))
        assertNotEquals(Color(0xFF0B7A75), s.get(false, AppTokens.Track))
        s.changeAdvanced(true)
        assertEquals(Color(0xFF0B7A75), s.get(false, AppTokens.Track))
        assertTrue(s.exportCode().contains("Track=0B7A75"))
        s.changeAdvanced(false)
        assertNotEquals(Color(0xFF0B7A75), s.get(false, AppTokens.Track))
        s.changeAdvanced(true)
        s.clear(false, AppTokens.Track)
        assertTrue(s.isDefault(false, AppTokens.Track))
    }
}

package com.m2011b.coastertracker

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import com.github.takahirom.roborazzi.captureRoboImage
import com.m2011b.coastertracker.data.Repo
import com.m2011b.kit.theme.KitTheme
import com.m2011b.kit.theme.ThemeMode
import com.m2011b.kit.theme.ThemeStore
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.LocalDate

/**
 * Renders every screen at phone size with Matthew's 42 credits and a Kings Island location,
 * saving PNGs to app/build/test-out/shots (CI uploads them).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w400dp-h860dp-xxhdpi")
class ScreenshotTest {
    @get:Rule val rule = createComposeRule()
    private val out = System.getProperty("shotsDir") ?: "build/test-out/shots"
    private fun shot(name: String) { rule.waitForIdle(); rule.onRoot().captureRoboImage("$out/$name.png") }
    private fun dialog(name: String) { rule.waitForIdle(); rule.onAllNodes(isRoot()).onLast().captureRoboImage("$out/$name.png") }
    private val kingsIsland = 39.3447 to -84.2686

    private fun start(tag: String, screen: String = "log", sub: Sub? = null, prefs: String = tag): Pair<ThemeStore, Repo> {
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        val theme = ThemeStore(ctx, AppTokens.all, "theme_$prefs")
        val repo = Repo(ctx, "shot_$tag.db", "prefs_$tag") { LocalDate.of(2026, 11, 9) }
        repo.importCsv(javaClass.classLoader!!.getResource("matthew-rides.csv").readText())
        rule.setContent { KitTheme(theme, AppFonts) { App(theme, repo, FixedLocator(kingsIsland), screen, sub) } }
        return theme to repo
    }
    private fun go(title: String) {
        rule.onNodeWithContentDescription("Menu").performClick()
        rule.onNodeWithText(title).performClick()
    }

    @Test fun walkthrough() {
        val (theme, _) = start("walk")
        shot("01_log")
        rule.onNodeWithText("Kings Island").performClick()
        dialog("02_park_picker")
        rule.onNodeWithText("Close").performClick()
        rule.onNodeWithText("Banshee").performTouchInput { longClick() }
        dialog("03_edit_coaster")
        rule.onNodeWithText("Cancel").performClick()

        go("Stats"); shot("04_stats_models")
        rule.onNodeWithText("Manufacturers").performClick(); shot("05_stats_makers")
        rule.onNodeWithText("Models").performClick()
        rule.onNodeWithText("Most complete", substring = true).performClick()
        dialog("06_sort_menu")
        rule.onNodeWithText("Most installations").performClick()
        shot("07_stats_most_installations")

        go("Parks"); shot("08_parks")
        go("Nearest new models"); shot("09_nearest")
        go("Settings"); shot("10_settings")

        rule.runOnUiThread { theme.changeMode(ThemeMode.Dark) }
        go("Stats"); shot("11_stats_dark")
    }

    @Test fun modelDetail() {
        start("model", "stats", Sub.Model("Vekoma|Suspended Family Coaster"))
        shot("12_model_detail")
    }

    @Test fun parkDetail() {
        start("park", "parks", Sub.ParkDetail(182L))
        shot("13_park_detail")
    }

    @Test @Config(sdk = [34], qualifiers = "w360dp-h640dp-xhdpi")
    fun smallPhone() {
        start("small")
        shot("14_small_log")
        go("Stats"); shot("15_small_stats")
        go("Settings"); shot("16_small_settings")
    }
}

package com.m2011b.coastertracker

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import com.github.takahirom.roborazzi.captureRoboImage
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.m2011b.coastertracker.data.Milestone
import com.m2011b.coastertracker.data.Park
import com.m2011b.coastertracker.data.Repo
import org.junit.Assert.assertTrue
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
    private fun go(tab: String) { rule.onNodeWithContentDescription(tab).performClick() }

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
        rule.onNodeWithText("Bolliger & Mabillard").performClick(); shot("05b_stats_one_maker")
        rule.onNodeWithContentDescription("Back").performClick()
        rule.onNodeWithText("Models").performClick()
        rule.onNodeWithText("Most complete", substring = true).performClick()
        dialog("06_sort_menu")
        rule.onNodeWithText("Most installations").performClick()
        shot("07_stats_most_installations")

        go("Parks"); shot("08_parks")
        go("Nearest"); shot("09_nearest")
        go("Settings"); shot("10_settings")

        rule.runOnUiThread { theme.changeMode(ThemeMode.Dark) }
        go("Stats"); shot("11_stats_dark")
    }

    @Test fun modelDetail() {
        start("model", "stats", Sub.Model("Vekoma|Suspended Family Coaster"))
        shot("12_model_detail")
    }

    /** Back retraces the exact path: Manufacturers > Vekoma > Boomerang, then back, back. */
    @Test fun backRetracesThePath() {
        start("back", "stats")
        rule.onNodeWithText("Manufacturers").performClick()
        rule.onAllNodes(hasScrollToNodeAction()).onFirst().performScrollToNode(hasText("Vekoma"))
        rule.onNodeWithText("Vekoma").performClick()
        rule.onNodeWithText("Boomerang").performClick()
        rule.onNodeWithText("Installations", ignoreCase = true).assertExists()
        rule.onNodeWithContentDescription("Back").performClick()
        rule.onNodeWithText("Installations", ignoreCase = true).assertDoesNotExist()
        rule.onNodeWithText("Manufacturers").assertDoesNotExist()  // on the Vekoma page, which has no view toggle
        rule.onAllNodesWithText("Vekoma").onFirst().assertExists()
        androidx.test.espresso.Espresso.pressBack()
        rule.onNodeWithText("Manufacturers").assertIsSelected()
        rule.onNodeWithText("136 manufacturers", substring = true, ignoreCase = true).assertExists()
        go("Parks"); androidx.test.espresso.Espresso.pressBack()
        rule.onNodeWithText("Manufacturers").assertIsSelected()  // back from another tab lands on the same view
    }

    @Test fun parksMap() {
        start("map", "parks")
        Basemap.get(ApplicationProvider.getApplicationContext())  // warm the bundled map
        MapConfig.pulse = false
        rule.onNodeWithContentDescription("Map view").performClick()
        rule.onNodeWithContentDescription("List view").assertExists()
        Thread.sleep(300); rule.waitForIdle()
        shot("21_parks_map")
        rule.onNodeWithText("Worldwide").performClick()
        Thread.sleep(200); rule.waitForIdle()
        shot("22_parks_map_world")
        rule.onNodeWithContentDescription("List view").performClick()
        rule.onNodeWithText("Six Flags Great America").assertExists()
    }

    @Test fun usOnlyHidesTheRegionToggle() {
        val (_, repo) = start("usonly", "parks")
        rule.onNodeWithText("Worldwide").assertExists()
        rule.runOnUiThread { repo.updateFilters(repo.filters.copy(usOnly = true)) }
        rule.onNodeWithText("Worldwide").assertDoesNotExist()
        shot("19_parks_us_only")
    }

    /** Accent candidates side by side (light and dark), for picking a default. */
    @Test fun accentOptions() {
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        val opts = listOf("blue" to (0xFF1F5C99 to 0xFF5B9BD5), "teal" to (0xFF16706B to 0xFF4FB3AB), "charcoal" to (0xFF3A3F44 to 0xFFC9CDD1))
        val theme = ThemeStore(ctx, AppTokens.all, "theme_accent")
        val repo = Repo(ctx, "shot_accent.db", "prefs_accent") { LocalDate.of(2026, 11, 9) }
        repo.importCsv(javaClass.classLoader!!.getResource("matthew-rides.csv").readText())
        rule.setContent { KitTheme(theme, AppFonts) { App(theme, repo, FixedLocator(kingsIsland), "stats", null) } }
        for ((name, c) in opts) for (dark in listOf(false, true)) {
            rule.runOnUiThread {
                theme.set(false, com.m2011b.kit.theme.Std.Accent, androidx.compose.ui.graphics.Color(c.first))
                theme.set(true, com.m2011b.kit.theme.Std.Accent, androidx.compose.ui.graphics.Color(c.second))
                theme.changeMode(if (dark) ThemeMode.Dark else ThemeMode.Light)
            }
            shot("accent_${name}_${if (dark) "dark" else "light"}")
        }
    }

    @Test fun customColors() {
        val (theme, _) = start("colors", "settings", Sub.Theme)
        shot("17_custom_colors")
        rule.runOnUiThread { theme.changeAdvanced(true) }
        shot("18_custom_colors_advanced")
    }

    @Test @Config(sdk = [34], qualifiers = "w360dp-h640dp-xhdpi")
    fun smallThemeButtons() {
        start("smallcolors", "settings", Sub.Theme)
        rule.onNodeWithText("COPY THEME CODE").performScrollTo()
        shot("20_small_theme_buttons")
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

    @Test fun quickLog() {
        val (_, repo) = start("quick")
        rule.onNodeWithContentDescription("Quick list").performClick()
        shot("23_quick_log")
        assertTrue(repo.quickLog)
        rule.onNodeWithContentDescription("Picker view").performClick()
        rule.onNodeWithText("LOG", substring = true).assertExists()
    }

    @Test @Config(sdk = [34], qualifiers = "w360dp-h640dp-xhdpi")
    fun smallQuickLog() {
        val (_, repo) = start("smallquick")
        rule.runOnUiThread { repo.updateQuickLog(true) }
        shot("24_small_quick_log")
    }

    @Test fun modelAndMakerMaps() {
        Basemap.get(ApplicationProvider.getApplicationContext()); MapConfig.pulse = false
        val (_, repo) = start("modelmap", "stats", Sub.Model("Vekoma|Boomerang"))
        rule.onAllNodesWithContentDescription("Add", substring = true).onFirst().performClick()  // star the model
        assertTrue(repo.wishes.model("Vekoma|Boomerang"))
        shot("25_model_starred")
        rule.onNodeWithContentDescription("Map view").performClick()
        Thread.sleep(300); rule.waitForIdle()
        shot("26_model_map")
    }

    @Test fun makerMap() {
        Basemap.get(ApplicationProvider.getApplicationContext()); MapConfig.pulse = false
        start("makermap", "stats", Sub.Make("Bolliger & Mabillard"))
        rule.onNodeWithContentDescription("Map view").performClick()
        Thread.sleep(300); rule.waitForIdle()
        shot("27_maker_map")
    }

    @Test fun wishlistOnParksMap() {
        Basemap.get(ApplicationProvider.getApplicationContext()); MapConfig.pulse = false
        val (_, repo) = start("wishmap", "parks")
        rule.runOnUiThread { repo.toggleWishModel("Vekoma|Boomerang"); repo.toggleWishModel("Bolliger & Mabillard|Wing Coaster") }
        rule.onNodeWithContentDescription("Map view").performClick()
        Thread.sleep(300); rule.waitForIdle()
        shot("28_parks_map_wishlist")
    }

    @Test fun completionCards() {
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        val theme = ThemeStore(ctx, AppTokens.all, "theme_cards")
        val day = LocalDate.of(2026, 11, 9)
        val cards = listOf(
            "29_card_park" to Milestone.ParkDone(Park(1, "Cedar Point", "US", null, null, "Sandusky", "Ohio"), 17, 12, 2026, 42),
            "30_card_maker_us" to Milestone.MakeDone("Bolliger & Mabillard", false, 28, 9, 14, 42, day),
            "31_card_maker_world" to Milestone.MakeDone("Bolliger & Mabillard", true, 133, 11, 19, 42, day),
            "32_card_model_us" to Milestone.ModelDone("Bolliger & Mabillard", "Wing Coaster", false, 3, day, 42),
            "33_card_model_world" to Milestone.ModelDone("Bolliger & Mabillard", "Wing Coaster", true, 7, day, 42),
        )
        var shown by mutableStateOf(cards.first().second as Milestone)
        rule.setContent { KitTheme(theme, AppFonts) { CelebrationDialog(shown) {} } }
        for ((name, m) in cards) { rule.runOnUiThread { shown = m }; dialog(name) }
        rule.runOnUiThread { theme.changeMode(ThemeMode.Dark); shown = cards.first().second }
        dialog("34_card_park_dark")
    }
}

package com.m2011b.coastertracker

import android.Manifest
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import com.m2011b.coastertracker.data.Repo
import com.m2011b.kit.theme.KitTheme
import com.m2011b.kit.theme.ThemeEditorScreen
import com.m2011b.kit.theme.ThemeStore
import com.m2011b.kit.ui.KitAppShell
import com.m2011b.kit.ui.NavItem

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.auto(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT),
        )
        val theme = ThemeStore(applicationContext, AppTokens.all)
        val repo = Repo(applicationContext)
        val locator = AndroidLocator(applicationContext)
        setContent { KitTheme(theme, AppFonts) { App(theme, repo, locator) } }
    }
}

private val NAV = listOf(
    NavItem("log", "Log a ride"), NavItem("stats", "Stats"), NavItem("parks", "Parks"),
    NavItem("nearest", "Nearest new models"), NavItem("settings", "Settings"),
)

/** A screen opened on top of a menu item. */
sealed interface Sub {
    data class Model(val key: String) : Sub
    data class ParkDetail(val id: Long) : Sub
    data object Theme : Sub
}

@Composable
internal fun App(theme: ThemeStore, repo: Repo, locator: Locator, startScreen: String = NAV.first().id, startSub: Sub? = null) {
    var screen by rememberSaveable { mutableStateOf(startScreen) }
    var sub by remember { mutableStateOf(startSub) }
    var here by remember { mutableStateOf<LatLon?>(null) }
    val askPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        if (ok) locator.locate { here = it }
    }
    val refreshLocation = { if (locator.hasPermission()) locator.locate { here = it } else askPermission.launch(Manifest.permission.ACCESS_COARSE_LOCATION) }
    LaunchedEffect(Unit) { refreshLocation() }

    CompositionLocalProvider(LocalHere provides here) {
        KitAppShell(appName = "Coaster Tracker", items = NAV, current = screen, onNavigate = { screen = it; sub = null }, themeStore = theme) { id ->
            val open = sub
            if (open != null) {
                BackHandler { sub = null }
                when (open) {
                    is Sub.Model -> ModelDetailScreen(repo, open.key)
                    is Sub.ParkDetail -> ParkDetailScreen(repo, open.id) { sub = Sub.Model(it) }
                    Sub.Theme -> ThemeEditorScreen(theme)
                }
            } else when (id) {
                "log" -> LogScreen(repo)
                "stats" -> StatsScreen(repo) { sub = Sub.Model(it) }
                "parks" -> ParksScreen(repo) { sub = Sub.ParkDetail(it) }
                "nearest" -> NearestScreen(repo, refreshLocation) { sub = Sub.Model(it) }
                else -> SettingsScreen(repo, theme) { sub = Sub.Theme }
            }
        }
    }
}

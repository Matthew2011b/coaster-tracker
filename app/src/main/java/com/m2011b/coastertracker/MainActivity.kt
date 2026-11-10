package com.m2011b.coastertracker

import android.Manifest
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
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
    NavItem("log", "Log a ride", "Log", "M12 3a9 9 0 1 0 0 18a9 9 0 1 0 0-18 M12 8v8 M8 12h8"),
    NavItem("stats", "Stats", "Stats", "M4 20h16 M7 16v-5 M12 16V6 M17 16v-8"),
    NavItem("parks", "Parks", "Parks", "M2 21h20 M7 10a4 4 0 1 0 0 8a4 4 0 1 0 0-8 M7 18v3 M13 21V4 M12 4h2 M16 21c1-5 2.5-8 3.5-8s1.5 2 2.5 5"),
    NavItem("nearest", "Nearest new models", "Nearest", "M12 3a9 9 0 1 0 0 18a9 9 0 1 0 0-18 M15.5 8.5l-2 5-5 2 2-5z"),
    NavItem("settings", "Settings", "Settings", "M4 6h10 M18 6h2 M16 4v4 M4 12h4 M12 12h8 M10 10v4 M4 18h12 M18 16v4"),
)

/** A screen opened on top of a tab. */
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

    val open = sub
    val subTitle = when (open) {
        is Sub.Model -> "Model"
        is Sub.ParkDetail -> "Park"
        Sub.Theme -> "Custom colors"
        null -> null
    }
    CompositionLocalProvider(LocalHere provides here) {
        KitAppShell(items = NAV, current = screen, onNavigate = { screen = it; sub = null },
            subTitle = subTitle, onBack = if (open != null) ({ sub = null }) else null) { id ->
            if (open != null) when (open) {
                is Sub.Model -> ModelDetailScreen(repo, open.key)
                is Sub.ParkDetail -> ParkDetailScreen(repo, open.id) { sub = Sub.Model(it) }
                Sub.Theme -> ThemeEditorScreen(theme)
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

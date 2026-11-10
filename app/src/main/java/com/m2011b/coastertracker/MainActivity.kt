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
import androidx.compose.animation.*
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.unit.IntOffset
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
    data class Make(val make: String) : Sub
    data class ParkDetail(val id: Long) : Sub
    data object Theme : Sub
}

/** One page in the back history: a tab, plus the screen open on top of it if any. */
internal data class Entry(val id: Int, val tab: String, val sub: Sub?) {
    /** Tab roots share saved state (toggles, sort, scroll) across visits; opened screens get their own. */
    val stateKey: String get() = if (sub == null) "root-$tab" else "sub-$id"
}

private fun tabIndex(tab: String) = NAV.indexOfFirst { it.id == tab }

@Composable
internal fun App(theme: ThemeStore, repo: Repo, locator: Locator, startScreen: String = NAV.first().id, startSub: Sub? = null) {
    var history by remember {
        mutableStateOf(listOfNotNull(Entry(0, startScreen, null), startSub?.let { Entry(1, startScreen, it) }))
    }
    var nextId by remember { mutableIntStateOf(2) }
    var goingBack by remember { mutableStateOf(false) }
    val saved = rememberSaveableStateHolder()
    var here by remember { mutableStateOf<LatLon?>(null) }
    val askPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        if (ok) locator.locate { here = it }
    }
    val refreshLocation = { if (locator.hasPermission()) locator.locate { here = it } else askPermission.launch(Manifest.permission.ACCESS_COARSE_LOCATION) }
    LaunchedEffect(Unit) { refreshLocation() }

    val cur = history.last()
    fun push(tab: String, sub: Sub?) {
        goingBack = false
        history = (history + Entry(nextId++, tab, sub)).takeLast(60)
    }
    fun open(sub: Sub) = push(cur.tab, sub)
    fun back() {
        goingBack = true
        if (history.size > 1) {
            val gone = history.last()
            history = history.dropLast(1)
            if (gone.sub != null && history.none { it.id == gone.id }) saved.removeState(gone.stateKey)
        } else history = listOf(Entry(nextId++, NAV.first().id, null))
    }
    val canGoBack = history.size > 1 || cur.tab != NAV.first().id || cur.sub != null

    val subTitle = when (val o = cur.sub) {
        is Sub.Model -> "Model"
        is Sub.Make -> o.make
        is Sub.ParkDetail -> "Park"
        Sub.Theme -> "Custom colors"
        null -> null
    }
    CompositionLocalProvider(LocalHere provides here) {
        KitAppShell(
            items = NAV, current = cur.tab,
            onNavigate = { tab -> if (tab != cur.tab || cur.sub != null) push(tab, null) },
            subTitle = subTitle, showBackArrow = cur.sub != null,
            onBack = if (canGoBack) ({ back() }) else null,
        ) {
            AnimatedContent(
                targetState = cur,
                transitionSpec = {
                    val ti = tabIndex(targetState.tab); val ii = tabIndex(initialState.tab)
                    val dir = when {
                        ti != ii -> if (ti > ii) 1 else -1
                        goingBack -> -1
                        else -> 1
                    }
                    val spec = tween<IntOffset>(260, easing = FastOutSlowInEasing)
                    (slideInHorizontally(spec) { it * dir } + fadeIn(tween(200))) togetherWith
                        (slideOutHorizontally(spec) { -it * dir / 3 } + fadeOut(tween(160)))
                },
                contentKey = { it.id },
                label = "page",
            ) { e ->
                saved.SaveableStateProvider(e.stateKey) {
                    when (val o = e.sub) {
                        is Sub.Model -> ModelDetailScreen(repo, o.key)
                        is Sub.Make -> StatsScreen(repo, o.make, onModel = { open(Sub.Model(it)) }, onMake = {})
                        is Sub.ParkDetail -> ParkDetailScreen(repo, o.id) { open(Sub.Model(it)) }
                        Sub.Theme -> ThemeEditorScreen(theme)
                        null -> when (e.tab) {
                            "log" -> LogScreen(repo)
                            "stats" -> StatsScreen(repo, null, onModel = { open(Sub.Model(it)) }, onMake = { open(Sub.Make(it)) })
                            "parks" -> ParksScreen(repo) { open(Sub.ParkDetail(it)) }
                            "nearest" -> NearestScreen(repo, refreshLocation) { open(Sub.Model(it)) }
                            else -> SettingsScreen(repo, theme) { open(Sub.Theme) }
                        }
                    }
                }
            }
        }
    }
}

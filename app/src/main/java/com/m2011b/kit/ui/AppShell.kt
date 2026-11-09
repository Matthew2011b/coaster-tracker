package com.m2011b.kit.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.m2011b.kit.theme.LocalPalette
import com.m2011b.kit.theme.ThemeEditorScreen
import com.m2011b.kit.theme.ThemeStore
import com.m2011b.kit.theme.secretTaps
import kotlinx.coroutines.launch

/** One entry in the side menu. [id] is what KitAppShell hands back to the app's content lambda. */
data class NavItem(val id: String, val title: String)

/**
 * The standard app frame: charcoal top bar with a hamburger, an accent rule under it, a side menu
 * of [items], and the hidden theme menu wired to [secretTapCount] quick taps on the empty space at
 * the bottom of the side menu. Back closes the menu, then the theme screen, then returns to the
 * first item. [content] draws the screen for the current item id.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KitAppShell(
    appName: String,
    items: List<NavItem>,
    current: String,
    onNavigate: (String) -> Unit,
    themeStore: ThemeStore,
    secretTapCount: Int = 5,
    content: @Composable (id: String) -> Unit,
) {
    val p = LocalPalette.current
    val drawer = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    var themeOpen by rememberSaveable { mutableStateOf(false) }
    fun go(id: String) { themeOpen = false; onNavigate(id); scope.launch { drawer.close() } }

    BackHandler(enabled = drawer.isOpen || themeOpen || current != items.first().id) {
        when {
            drawer.isOpen -> scope.launch { drawer.close() }
            themeOpen -> themeOpen = false
            else -> onNavigate(items.first().id)
        }
    }

    ModalNavigationDrawer(
        drawerState = drawer,
        drawerContent = {
            ModalDrawerSheet(drawerContainerColor = p.menu, drawerContentColor = p.ink) {
                Column(Modifier.fillMaxHeight()) {
                    Text(appName, fontSize = 22.sp, fontWeight = FontWeight.SemiBold, color = p.ink,
                        modifier = Modifier.padding(start = 28.dp, top = 24.dp, bottom = 16.dp))
                    items.forEach { item ->
                        val on = !themeOpen && item.id == current
                        NavigationDrawerItem(
                            label = { Text(item.title, fontSize = 16.sp, fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal) },
                            selected = on,
                            onClick = { go(item.id) },
                            colors = NavigationDrawerItemDefaults.colors(
                                selectedContainerColor = p.menuSel, unselectedContainerColor = Color.Transparent,
                                selectedTextColor = p.ink, unselectedTextColor = p.ink,
                            ),
                            modifier = Modifier.padding(horizontal = 12.dp),
                        )
                    }
                    Box(Modifier.weight(1f).fillMaxWidth().testTag("kit_secret").secretTaps(secretTapCount) {
                        themeOpen = true; scope.launch { drawer.close() }
                    })
                }
            }
        },
    ) {
        Scaffold(
            containerColor = p.bg,
            topBar = {
                Column {
                    TopAppBar(
                        title = { Text(if (themeOpen) "Theme" else items.firstOrNull { it.id == current }?.title ?: appName, fontWeight = FontWeight.SemiBold) },
                        navigationIcon = {
                            IconButton(onClick = { scope.launch { drawer.open() } }) { Icon(Icons.Filled.Menu, contentDescription = "Menu") }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = p.header, titleContentColor = p.headerInk, navigationIconContentColor = p.headerInk,
                        ),
                    )
                    Box(Modifier.fillMaxWidth().height(3.dp).background(p.accent))
                }
            },
        ) { pad ->
            Box(Modifier.padding(pad).fillMaxSize().imePadding()) {
                if (themeOpen) ThemeEditorScreen(themeStore) else content(current)
            }
        }
    }
}

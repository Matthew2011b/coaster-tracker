package com.m2011b.kit.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.m2011b.kit.theme.LocalPalette

/**
 * One bottom-nav destination. [title] shows in the top bar, [label] under the icon, and [icon] is
 * SVG path data on a 24x24 grid, drawn as a 2-unit outline (same format as Lucide/Feather icons).
 */
data class NavItem(val id: String, val title: String, val label: String = title, val icon: String)

/** Builds an outline icon from SVG path data on a 24x24 grid. */
fun outlineIcon(name: String, pathData: String): ImageVector =
    ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f).apply {
        addPath(
            pathData = PathParser().parsePathString(pathData).toNodes(),
            fill = null, stroke = SolidColor(Color.Black), strokeLineWidth = 2f,
            strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round,
        )
    }.build()

private const val BACK_ICON = "M15 5l-7 7 7 7"

/**
 * The standard app frame: a compact top bar with a centered title and an accent rule under it,
 * and a bottom bar with one icon per [items] entry. Pass [subTitle] and [onBack] while a screen is
 * open on top of a tab: the bar shows that title and a back arrow. System back closes the open
 * screen first, then returns to the first tab. [content] draws the screen for the current tab id.
 */
@Composable
fun KitAppShell(
    items: List<NavItem>,
    current: String,
    onNavigate: (String) -> Unit,
    subTitle: String? = null,
    onBack: (() -> Unit)? = null,
    content: @Composable (id: String) -> Unit,
) {
    val p = LocalPalette.current
    val icons = remember(items) { items.associate { it.id to outlineIcon(it.id, it.icon) } }
    val back = remember { outlineIcon("back", BACK_ICON) }

    BackHandler(enabled = onBack != null || current != items.first().id) {
        if (onBack != null) onBack() else onNavigate(items.first().id)
    }

    Scaffold(
        containerColor = p.bg,
        contentWindowInsets = WindowInsets(0),
        topBar = {
            Column(Modifier.background(p.header)) {
                Box(Modifier.statusBarsPadding().fillMaxWidth().height(48.dp), contentAlignment = Alignment.Center) {
                    val title = subTitle ?: items.firstOrNull { it.id == current }?.title.orEmpty()
                    Text(title, color = p.headerInk, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, maxLines = 1,
                        overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center, modifier = Modifier.padding(horizontal = 56.dp))
                    if (onBack != null) {
                        Box(
                            Modifier.align(Alignment.CenterStart).padding(start = 4.dp).size(48.dp).clip(RoundedCornerShape(24.dp))
                                .clickable(onClick = onBack).semantics { contentDescription = "Back" },
                            contentAlignment = Alignment.Center,
                        ) { Icon(back, contentDescription = null, tint = p.headerInk) }
                    }
                }
                Box(Modifier.fillMaxWidth().height(3.dp).background(p.accent))
            }
        },
        bottomBar = {
            Column(Modifier.background(p.surface)) {
                Box(Modifier.fillMaxWidth().height(1.dp).background(p.line))
                Row(Modifier.fillMaxWidth().height(64.dp)) {
                    items.forEach { item ->
                        val on = item.id == current
                        val color = if (on) p.accent else p.muted
                        Column(
                            Modifier.weight(1f).fillMaxHeight()
                                .clickable(role = Role.Tab) { onNavigate(item.id) }
                                .semantics { selected = on; contentDescription = item.label },
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                        ) {
                            Box(
                                Modifier.size(width = 56.dp, height = 30.dp).clip(RoundedCornerShape(15.dp))
                                    .background(if (on) p.accentSoft else Color.Transparent),
                                contentAlignment = Alignment.Center,
                            ) { Icon(icons.getValue(item.id), contentDescription = null, tint = color) }
                            Spacer(Modifier.height(4.dp))
                            Text(item.label, color = color, fontSize = 11.sp, fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal, maxLines = 1)
                        }
                    }
                }
                Spacer(Modifier.navigationBarsPadding())
            }
        },
    ) { pad ->
        Box(Modifier.padding(pad).consumeWindowInsets(pad).fillMaxSize().imePadding()) { content(current) }
    }
}

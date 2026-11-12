package com.m2011b.coastertracker

import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.m2011b.coastertracker.data.Coaster
import com.m2011b.kit.theme.LocalKitFonts
import com.m2011b.kit.theme.LocalPalette
import com.m2011b.kit.ui.BoxShape
import com.m2011b.kit.ui.outlineIcon
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import java.util.Locale

/** Bordered box whose list scrolls inside it, so the screen itself never scrolls. */
@Composable
fun ListBox(modifier: Modifier = Modifier, content: LazyListScope.() -> Unit) {
    val p = LocalPalette.current
    LazyColumn(modifier.fillMaxWidth().clip(BoxShape).background(p.surface).border(1.dp, p.line, BoxShape), content = content)
}

@Composable
fun RowDivider() = HorizontalDivider(color = LocalPalette.current.line.copy(alpha = 0.5f))

@Composable
fun ProgressBar(fraction: Double, complete: Boolean, modifier: Modifier = Modifier, height: Int = 8) {
    val p = LocalPalette.current
    Box(modifier.fillMaxWidth().height(height.dp).clip(RoundedCornerShape(height.dp)).background(p[AppTokens.Track])) {
        Box(Modifier.fillMaxWidth(fraction.toFloat().coerceIn(0f, 1f)).fillMaxHeight().clip(RoundedCornerShape(height.dp))
            .background(if (complete) p.ok else p.accent))
    }
}

@Composable
fun Mono(text: String, size: Int = 13, bold: Boolean = false, modifier: Modifier = Modifier, muted: Boolean = true) {
    val p = LocalPalette.current
    Text(text, color = if (muted) p.muted else p.ink, fontFamily = LocalKitFonts.current.mono, fontSize = size.sp,
        fontWeight = if (bold) FontWeight.SemiBold else FontWeight.Normal, maxLines = 1, modifier = modifier)
}

@Composable
fun TwoLine(title: String, sub: String?, modifier: Modifier = Modifier, bold: Boolean = true) {
    val p = LocalPalette.current
    Column(modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(title, color = p.ink, fontSize = 15.sp, fontWeight = if (bold) FontWeight.SemiBold else FontWeight.Normal,
            maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (!sub.isNullOrBlank()) Text(sub, color = p.muted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
fun RiddenMark() {
    val p = LocalPalette.current
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Icon(Icons.Filled.Check, contentDescription = null, tint = p.ok, modifier = Modifier.size(18.dp))
        Text("Ridden", color = p.ok, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
    }
}

fun modelLine(c: Coaster): String = when {
    c.model != null -> "${shortMake(c.make)} ${c.model}"
    c.make != null -> "${c.make} · custom"
    else -> "Custom"
}

/** Long manufacturer names shortened for one-line labels. */
fun shortMake(make: String?): String = when (make) {
    null -> ""
    "Bolliger & Mabillard" -> "B&M"
    "Great Coasters International" -> "GCI"
    "Rocky Mountain Construction" -> "RMC"
    "Philadelphia Toboggan Coasters" -> "PTC"
    "Custom Coasters International, Inc." -> "CCI"
    else -> make
}

/** Distance display units, set from Settings. Compose state, so text using [miles] updates on change. */
object Units { var metric by mutableStateOf(false) }

/** A distance given in miles, shown in miles or km per the Settings choice. */
fun miles(d: Double?): String {
    if (d == null) return ""
    val (v, u) = if (Units.metric) d * 1.609344 to "km" else d to "mi"
    return if (v < 10) String.format(Locale.US, "%.1f %s", v, u) else String.format(Locale.US, "%,d %s", v.toLong(), u)
}

private const val STAR = "M12 3.2l2.7 5.6 6.1.8-4.5 4.2 1.1 6.1L12 17l-5.4 2.9 1.1-6.1-4.5-4.2 6.1-.8z"
private val starOff = outlineIcon("Star", STAR)
private val starOn = androidx.compose.ui.graphics.vector.ImageVector.Builder("Starred", 24.dp, 24.dp, 24f, 24f).apply {
    addPath(androidx.compose.ui.graphics.vector.PathParser().parsePathString(STAR).toNodes(),
        fill = androidx.compose.ui.graphics.SolidColor(androidx.compose.ui.graphics.Color.Black),
        stroke = androidx.compose.ui.graphics.SolidColor(androidx.compose.ui.graphics.Color.Black), strokeLineWidth = 2f,
        strokeLineJoin = androidx.compose.ui.graphics.StrokeJoin.Round)
}.build()

/** Wishlist star: outline when off, filled yellow when on. */
@Composable
fun WishStar(on: Boolean, label: String, size: Int = 22, onToggle: () -> Unit) {
    val p = LocalPalette.current
    Box(
        Modifier.size(40.dp).clip(androidx.compose.foundation.shape.CircleShape).clickable(onClick = onToggle)
            .semantics { contentDescription = if (on) "Remove $label from wishlist" else "Add $label to wishlist" },
        contentAlignment = Alignment.Center,
    ) { Icon(if (on) starOn else starOff, contentDescription = null, tint = if (on) p[AppTokens.Wish] else p.muted, modifier = Modifier.size(size.dp)) }
}

/** Coaster row used on the log and park screens: tap selects, long-press edits, optional wishlist star. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun CoasterRow(c: Coaster, ridden: Boolean, newModel: Boolean, onClick: () -> Unit, onLongClick: () -> Unit,
               wished: Boolean? = null, onWish: () -> Unit = {}) {
    val p = LocalPalette.current
    Row(
        Modifier.fillMaxWidth().background(if (newModel) p[AppTokens.NewBg] else p.surface)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(start = 14.dp, end = if (wished != null) 4.dp else 14.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TwoLine(c.name, modelLine(c), Modifier.weight(1f), bold = !ridden)
        Spacer(Modifier.width(8.dp))
        when {
            ridden -> RiddenMark()
            newModel -> Text("New model", color = p[AppTokens.NewInk], fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            else -> Text("Not ridden", color = p.muted, fontSize = 12.sp)
        }
        if (wished != null) { Spacer(Modifier.width(2.dp)); WishStar(wished, c.name, onToggle = onWish) }
    }
}

@Composable
fun SectionHead(left: String, right: String? = null, modifier: Modifier = Modifier) {
    val p = LocalPalette.current
    Row(modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(left.uppercase(Locale.US), color = p.muted, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.sp,
            modifier = Modifier.weight(1f))
        if (right != null) Mono(right)
    }
}

/** Plain clickable row with a trailing value, for lists of parks and models. */
@Composable
fun ValueRow(title: String, sub: String?, value: String, onClick: (() -> Unit)?, valueBig: Boolean = false) {
    Row(
        Modifier.fillMaxWidth().let { if (onClick != null) it.clickable(onClick = onClick) else it }.padding(horizontal = 14.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TwoLine(title, sub, Modifier.weight(1f))
        Spacer(Modifier.width(8.dp))
        Mono(value, size = if (valueBig) 18 else 14, bold = true, muted = !valueBig)
    }
}

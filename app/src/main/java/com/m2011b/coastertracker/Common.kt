package com.m2011b.coastertracker

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

fun miles(d: Double?): String = when {
    d == null -> ""
    d < 10 -> String.format(Locale.US, "%.1f mi", d)
    else -> String.format(Locale.US, "%,d mi", d.toLong())
}

/** Coaster row used on the log and park screens: tap selects, long-press edits. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun CoasterRow(c: Coaster, ridden: Boolean, newModel: Boolean, onClick: () -> Unit, onLongClick: () -> Unit) {
    val p = LocalPalette.current
    Row(
        Modifier.fillMaxWidth().background(if (newModel) p[AppTokens.NewBg] else p.surface)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TwoLine(c.name, modelLine(c), Modifier.weight(1f), bold = !ridden)
        Spacer(Modifier.width(8.dp))
        when {
            ridden -> RiddenMark()
            newModel -> Text("New model", color = p[AppTokens.NewInk], fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            else -> Text("Not ridden", color = p.muted, fontSize = 12.sp)
        }
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

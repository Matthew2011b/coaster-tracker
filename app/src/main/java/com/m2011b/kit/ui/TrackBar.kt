package com.m2011b.kit.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotateRad
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.m2011b.kit.theme.LocalPalette
import kotlin.math.atan2
import kotlin.math.tan

/** A button that sits in the middle of the loop, e.g. the map toggle on Parks. */
data class BarAction(val label: String, val icon: String, val onClick: () -> Unit)

val TRACK_BAR_HEIGHT: Dp = 56.dp

/**
 * Track geometry in dp on a bar of width [w]. Features at the left (run-in, dip, bunny hill, and a
 * lift hill climbing at the logo's 49 degrees), straight track under the title, and a round loop
 * anchored to the right edge. The line enters and leaves at the same height.
 */
internal class TrackGeometry(val w: Float) {
    val b = 50f                 // rail height
    val r = 21f                 // loop radius
    val lc = w - 46f            // loop center x
    val cy = b - r              // loop center y
    private val slopeX0 = 70f
    private val slopeY0 = b - 3f
    private val slopeY1 = b - 25f
    val slopeX1 = slopeX0 + (slopeY0 - slopeY1) / tan(Math.toRadians(49.0)).toFloat()
    val straightStart = slopeX1 + 32f
    val straightEnd = lc - 30f

    fun path(scale: Float): Path {
        val k = 0.5523f * r
        fun s(v: Float) = v * scale
        return Path().apply {
            moveTo(0f, s(b)); lineTo(s(12f), s(b))
            cubicTo(s(18f), s(b), s(20f), s(b + 3), s(25f), s(b + 3))                       // little dip
            cubicTo(s(32f), s(b + 3), s(35f), s(b - 10), s(43f), s(b - 10))                 // bunny hill
            cubicTo(s(51f), s(b - 10), s(54f), s(b), s(62f), s(b))
            cubicTo(s(66f), s(b), s(68f), s(b - 1), s(slopeX0), s(slopeY0))                // lift hill: straight climb
            lineTo(s(slopeX1), s(slopeY1))
            cubicTo(s(slopeX1 + 4), s(b - 30), s(slopeX1 + 10), s(b - 30), s(slopeX1 + 13), s(b - 24))
            cubicTo(s(slopeX1 + 18), s(b - 14), s(slopeX1 + 21), s(b), s(straightStart), s(b))  // drop
            lineTo(s(straightEnd), s(b))                                                    // under the title
            cubicTo(s(lc - 12), s(b), s(lc), s(b), s(lc + 0.01f), s(b))
            cubicTo(s(lc + k), s(b), s(lc + r), s(cy + k), s(lc + r), s(cy))                // loop
            cubicTo(s(lc + r), s(cy - k), s(lc + k), s(cy - r), s(lc), s(cy - r))
            cubicTo(s(lc - k), s(cy - r), s(lc - r), s(cy - k), s(lc - r), s(cy))
            cubicTo(s(lc - r), s(cy + k), s(lc - k), s(b + 2.5f), s(lc + 4), s(b + 2.5f))
            cubicTo(s(lc + 14), s(b + 2.5f), s(lc + 20), s(b), s(lc + 34), s(b))
            lineTo(s(w), s(b))
        }
    }
}

/** Where the train's front car stops for each of [count] tabs, as distances along [m] (px). */
internal fun trainStops(g: TrackGeometry, m: PathMeasure, scale: Float, count: Int): List<Float> {
    val len = m.length
    fun atX(xDp: Float): Float {
        val x = xDp * scale
        var lo = 0f; var hi = len
        repeat(40) { val mid = (lo + hi) / 2; if (m.getPosition(mid).x < x) lo = mid else hi = mid }
        return lo
    }
    var loopTop = 0f; var best = Float.MAX_VALUE
    var d = atX(g.straightEnd)
    while (d < len) {
        val p = m.getPosition(d)
        if (kotlin.math.abs(p.x - g.lc * scale) < 2 * scale && p.y < best) { best = p.y; loopTop = d }
        d += 0.5f * scale
    }
    if (count <= 1) return listOf(atX(g.slopeX1 - 2))
    // Middle tabs spread evenly along the straight under the title.
    val from = g.straightStart + 34f; val to = g.straightEnd - 6f
    val middle = (1 until count - 1).map { i -> atX(from + (to - from) * (i - 1).toFloat() / (count - 3).coerceAtLeast(1)) }
    return listOf(atX(g.slopeX1 - 2)) + middle + (loopTop + 11.2f * scale)  // middle car at the top of the loop
}

/**
 * The top bar: track drawn along the bottom edge, a three-car train that rolls to [stop] (the
 * current tab's index) when it changes, the [title] centered above the straight, an optional back
 * arrow at the left, and an optional [action] in the middle of the loop.
 */
@Composable
fun TrackBar(title: String, stop: Int, stops: Int, onBack: (() -> Unit)?, action: BarAction?, backIcon: androidx.compose.ui.graphics.vector.ImageVector) {
    val p = LocalPalette.current
    val density = LocalDensity.current
    BoxWithConstraints(Modifier.fillMaxWidth().height(TRACK_BAR_HEIGHT)) {
        val wDp = maxWidth.value
        val scale = density.density
        val g = remember(wDp) { TrackGeometry(wDp) }
        val path = remember(wDp, scale) { g.path(scale) }
        val measure = remember(path) { PathMeasure().apply { setPath(path, false) } }
        val stopList = remember(measure, stops) { trainStops(g, measure, scale, stops) }
        val target = stopList[stop.coerceIn(0, stopList.lastIndex)]
        val front by animateFloatAsState(target, tween(650, easing = FastOutSlowInEasing), label = "train")

        Canvas(Modifier.fillMaxSize()) {
            drawPath(path, p.headerTrack, style = Stroke(2.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
            val car = 9.dp.toPx(); val gap = 2.2.dp.toPx(); val h = 6.5.dp.toPx(); val lift = 1.2.dp.toPx()
            repeat(3) { i ->
                val d = (front - i * (car + gap) - car / 2).coerceIn(0f, measure.length - 1f)
                val a = measure.getPosition(d); val b2 = measure.getPosition(d + 0.5f)
                val ang = atan2(b2.y - a.y, b2.x - a.x)
                translate(a.x, a.y) {
                    rotateRad(ang, pivot = Offset.Zero) {
                        drawRoundRect(p.headerInk, Offset(-car / 2, -h - lift), Size(car, h), CornerRadius(1.8.dp.toPx()))
                    }
                }
            }
        }

        // Title: centered over the straight between the lift hill and the loop, stepping the size
        // down until it fits.
        var size by remember(title, wDp) { mutableFloatStateOf(17f) }
        Box(
            Modifier.fillMaxWidth().padding(start = (g.slopeX1 + 19f).dp, end = (wDp - (g.lc - g.r) + 8f).dp, top = 14.dp),
            contentAlignment = Alignment.TopCenter,
        ) {
            Text(
                title, color = p.headerInk, fontSize = size.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.1.sp,
                maxLines = 1, softWrap = false, textAlign = TextAlign.Center,
                onTextLayout = { if (it.hasVisualOverflow && size > 12f) size -= 1f },
            )
        }

        if (onBack != null) {
            Box(
                Modifier.padding(start = 4.dp, top = 6.dp).size(40.dp).clip(CircleShape)
                    .clickable(onClick = onBack).semantics { contentDescription = "Back" },
                contentAlignment = Alignment.Center,
            ) { Icon(backIcon, contentDescription = null, tint = p.headerInk) }
        }

        if (action != null) {
            val icon = remember(action.icon) { outlineIcon(action.label, action.icon) }
            Box(
                Modifier.offset(x = (g.lc - 18f).dp, y = (g.cy - 18f).dp).size(36.dp).clip(CircleShape)
                    .clickable(onClick = action.onClick).semantics { contentDescription = action.label },
                contentAlignment = Alignment.Center,
            ) { Icon(icon, contentDescription = null, tint = p.headerInk, modifier = Modifier.size(20.dp)) }
        }
    }
}

package com.m2011b.coastertracker

import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.m2011b.coastertracker.data.ParkStat
import com.m2011b.kit.theme.LocalKitFonts
import com.m2011b.kit.theme.LocalPalette
import com.m2011b.kit.ui.BoxShape
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.hypot
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

private const val MIN_ZOOM = 2f
private const val MAX_ZOOM = 11f

/**
 * Parks on the bundled map: land, water, interstates, state and country borders and town names,
 * drawn from assets/basemap.bin (Natural Earth) in theme colors, so it works offline with no
 * outside servers. Pins show new models or unridden coasters (per [byModels]); finished parks are
 * small dots in the ridden color. Pinch or double-tap to zoom, drag to pan, tap a pin for its card.
 */
@Composable
fun ParksMap(
    rows: List<ParkStat>, done: List<ParkStat>, byModels: Boolean, onPark: (Long) -> Unit, modifier: Modifier = Modifier,
    wished: Set<Long> = emptySet(), fitAll: Boolean = false, cardSub: ((ParkStat) -> String)? = null,
) {
    val p = LocalPalette.current
    val ctx = LocalContext.current
    val here = LocalHere.current
    val density = LocalDensity.current.density
    val measurer = rememberTextMeasurer()
    val sans = LocalKitFonts.current.sans
    val pulse = if (MapConfig.pulse) {
        rememberInfiniteTransition(label = "you").animateFloat(
            0f, 1f, infiniteRepeatable(tween(2000, easing = LinearOutSlowInEasing)), label = "pulse",
        ).value
    } else 0f
    // Wishlist parks: a small yellow star on the pin that slowly twinkles (never a moving ring, which means "you").
    val twinkle = if (MapConfig.pulse && wished.isNotEmpty()) {
        rememberInfiniteTransition(label = "wish").animateFloat(
            0.25f, 1f, infiniteRepeatable(tween(1300), RepeatMode.Reverse), label = "twinkle",
        ).value
    } else 1f
    val map by produceState<Basemap?>(null) { value = withContext(Dispatchers.Default) { Basemap.get(ctx) } }

    // View: world point at the center and zoom (256 dp tiles at zoom 0), saved across visits.
    var cx by rememberSaveable { mutableDoubleStateOf(Double.NaN) }
    var cy by rememberSaveable { mutableDoubleStateOf(Double.NaN) }
    var zoom by rememberSaveable { mutableFloatStateOf(7f) }
    var size by remember { mutableStateOf(IntSize.Zero) }
    var pickedId by rememberSaveable { mutableStateOf<Long?>(null) }

    data class Pin(val stat: ParkStat, val x: Double, val y: Double, val n: Int, val done: Boolean)
    val pins = remember(rows, done, byModels) {
        val open = rows.mapNotNull { r ->
            val la = r.park.lat ?: return@mapNotNull null; val lo = r.park.lon ?: return@mapNotNull null
            Pin(r, Basemap.x(lo), Basemap.y(la), if (byModels) r.newModels else r.unridden, false)
        }.sortedBy { it.n }  // bigger numbers drawn on top
        done.mapNotNull { r ->
            val la = r.park.lat ?: return@mapNotNull null; val lo = r.park.lon ?: return@mapNotNull null
            Pin(r, Basemap.x(lo), Basemap.y(la), 0, true)
        } + open
    }
    val picked = pins.firstOrNull { it.stat.park.id == pickedId }?.stat

    // First view: around you, or fit all the pins.
    LaunchedEffect(size, pins.isNotEmpty()) {
        if (!cx.isNaN() || size == IntSize.Zero) return@LaunchedEffect
        val h = here
        if (h != null && !fitAll) { cx = Basemap.x(h.second); cy = Basemap.y(h.first); zoom = 7f; return@LaunchedEffect }
        // Model and maker maps: frame the closest few installations around you, not the whole world.
        val open = if (fitAll) {
            if (h != null) pins.sortedBy { hypot(it.x - Basemap.x(h.second), it.y - Basemap.y(h.first)) }.take(6) +
                Pin(pins.firstOrNull()?.stat ?: return@LaunchedEffect, Basemap.x(h.second), Basemap.y(h.first), 0, true)
            else pins
        } else pins.filter { !it.done }.ifEmpty { pins }
        if (open.isEmpty()) { cx = Basemap.x(-96.0); cy = Basemap.y(39.0); zoom = 3.5f; return@LaunchedEffect }
        val l = open.minOf { it.x }; val r = open.maxOf { it.x }; val t = open.minOf { it.y }; val b = open.maxOf { it.y }
        cx = (l + r) / 2; cy = (t + b) / 2
        if (r - l < 1e-9 && b - t < 1e-9) { zoom = 8f; return@LaunchedEffect }
        val fit = min(size.width * 0.85 / max(r - l, 1.0), size.height * 0.85 / max(b - t, 1.0))  // px per world unit
        zoom = log2(fit * Basemap.WORLD / (256 * density)).toFloat().coerceIn(MIN_ZOOM, 9f)
    }

    fun scale() = 256.0 * density * 2.0.pow(zoom.toDouble()) / Basemap.WORLD  // px per world unit
    fun toScreen(x: Double, y: Double): Offset {
        val s = scale(); return Offset(((x - cx) * s + size.width / 2).toFloat(), ((y - cy) * s + size.height / 2).toFloat())
    }

    val land = p.bg; val water = p[AppTokens.MapWater]; val road = p[AppTokens.MapRoad]; val ink = p[AppTokens.MapInk]
    val pinNew = p.accent; val pinOther = p.accent;  // pins stay accent so they never read as the orange you-are-here dot
    val okC = p.ok; val you = p.headerTrack; val wish = p[AppTokens.Wish]
    val labelStyle = TextStyle(color = ink, fontSize = 11.sp, fontFamily = sans)
    val pinStyle = TextStyle(color = p.btnInk, fontSize = 11.sp, fontWeight = FontWeight.Bold, fontFamily = sans)

    Box(modifier.clip(BoxShape).border(1.dp, p.line, BoxShape)) {
        Canvas(
            Modifier.fillMaxSize().clipToBounds().onSizeChanged { size = it }
                .pointerInput(Unit) {
                    detectTransformGestures { centroid, pan, gestureZoom, _ ->
                        if (cx.isNaN()) return@detectTransformGestures
                        val s0 = scale()
                        // world point under the fingers stays put while zooming
                        val wx = cx + (centroid.x - size.width / 2) / s0
                        val wy = cy + (centroid.y - size.height / 2) / s0
                        zoom = (zoom + log2(gestureZoom.toDouble()).toFloat()).coerceIn(MIN_ZOOM, MAX_ZOOM)
                        val s1 = scale()
                        cx = wx - (centroid.x - size.width / 2) / s1 - pan.x / s1
                        cy = (wy - (centroid.y - size.height / 2) / s1 - pan.y / s1).coerceIn(0.0, Basemap.WORLD)
                    }
                }
                .pointerInput(pins) {
                    detectTapGestures(
                        onDoubleTap = { at ->
                            val s0 = scale()
                            val wx = cx + (at.x - size.width / 2) / s0; val wy = cy + (at.y - size.height / 2) / s0
                            zoom = (zoom + 1f).coerceIn(MIN_ZOOM, MAX_ZOOM)
                            val s1 = scale()
                            cx = wx - (at.x - size.width / 2) / s1; cy = wy - (at.y - size.height / 2) / s1
                        },
                        onTap = { at ->
                            val hit = pins.asReversed().minByOrNull { (toScreen(it.x, it.y) - at).getDistance() }
                            pickedId = hit?.takeIf { (toScreen(it.x, it.y) - at).getDistance() < 24 * density }?.stat?.park?.id
                        },
                    )
                },
        ) {
            drawRect(water)
            val m = map ?: return@Canvas
            if (cx.isNaN()) return@Canvas
            val s = scale().toFloat()
            val layers = if (zoom < 6f) m.coarse else m.fine
            val view = Rect(
                (cx - this.size.width / 2 / s).toFloat(), (cy - this.size.height / 2 / s).toFloat(),
                (cx + this.size.width / 2 / s).toFloat(), (cy + this.size.height / 2 / s).toFloat(),
            )
            withTransform({
                translate(this@Canvas.size.width / 2, this@Canvas.size.height / 2)
                scale(s, s, Offset.Zero)
                translate(-cx.toFloat(), -cy.toFloat())
            }) {
                fun DrawScope.fill(list: List<Basemap.Feature>, c: Color) = list.forEach { f ->
                    if (zoom >= f.minZoom && f.right >= view.left && f.left <= view.right && f.bottom >= view.top && f.top <= view.bottom)
                        drawPath(f.path, c, style = Fill)
                }
                fun DrawScope.line(list: List<Basemap.Feature>, c: Color, widthDp: Float, minZoomBias: Float = 0f) {
                    val st = Stroke(widthDp * density / s, cap = StrokeCap.Round, join = StrokeJoin.Round)
                    list.forEach { f ->
                        if (zoom + minZoomBias >= f.minZoom && f.right >= view.left && f.left <= view.right && f.bottom >= view.top && f.top <= view.bottom)
                            drawPath(f.path, c, style = st)
                    }
                }
                fill(layers.land, land)
                fill(layers.lakes, water)
                line(layers.rivers, water, 1.2f)
                line(layers.roads, road, if (zoom >= 8) 1.8f else 1.2f, 1f)
                line(layers.states, ink, 0.8f)
                line(layers.countries, ink, 1.4f)
            }

            // Town names, biggest first, skipping any that would overlap each other or a pin.
            val pinR = 13 * density
            val placed = ArrayList<Rect>()
            for (pin in pins) if (!pin.done) toScreen(pin.x, pin.y).let { placed += Rect(it.x - pinR, it.y - pinR, it.x + pinR, it.y + pinR) }
            val pinCount = placed.size
            val dot = 2.2f * density
            for (pl in m.places) {
                if (zoom + 1.2f < pl.minZoom) continue
                val at = toScreen(pl.x.toDouble(), pl.y.toDouble())
                if (at.x < -50 || at.y < -20 || at.x > this.size.width + 10 || at.y > this.size.height + 20) continue
                val layout = measurer.measure(pl.name, labelStyle)
                val box = Rect(at.x - dot, at.y - layout.size.height / 2f, at.x + dot * 2 + layout.size.width, at.y + layout.size.height / 2f)
                if (placed.any { it.overlaps(box) }) continue
                placed += box
                drawCircle(ink, dot, at)
                drawText(layout, topLeft = Offset(at.x + dot * 2, at.y - layout.size.height / 2f))
                if (placed.size - pinCount > 70) break
            }

            // Pins on top.
            val r = 13 * density; val small = 6 * density; val ring = 1.5f * density
            for (pin in pins) {
                val at = toScreen(pin.x, pin.y)
                if (at.x < -r || at.y < -r || at.x > this.size.width + r || at.y > this.size.height + r) continue
                if (pin.done) {
                    drawCircle(p.surface, small, at); drawCircle(okC, small - ring, at)
                    continue
                }
                val starred = pin.stat.park.id in wished
                drawCircle(if (starred) wish else p.surface, if (starred) r + ring else r, at)
                drawCircle(if (!byModels) pinOther else if (pin.n == 0) p.muted else pinNew, r - ring, at)
                val t = measurer.measure("${pin.n}", pinStyle)
                drawText(t, topLeft = Offset(at.x - t.size.width / 2f, at.y - t.size.height / 2f))
                if (starred) {
                    val c = at + Offset(r * 0.8f, -r * 0.8f)
                    drawPath(starPath(c, 7.5f * density), p.surface)
                    drawPath(starPath(c, 6f * density), wish.copy(alpha = twinkle))
                }
            }

            // You are here: brand-orange dot with a soft halo, on top of everything.
            here?.let { h ->
                val at = toScreen(Basemap.x(h.second), Basemap.y(h.first))
                drawCircle(you.copy(alpha = 0.18f), 11 * density, at)
                // slow pulse: a ring that grows outward and fades, every 2 s
                drawCircle(you.copy(alpha = 0.45f * (1 - pulse)), (7 + 17 * pulse) * density, at, style = Stroke(2 * density))
                drawCircle(p.surface, 7 * density, at)
                drawCircle(you, 5.2f * density, at)
            }
        }

        picked?.let { r ->
            Row(
                Modifier.align(Alignment.BottomCenter).padding(10.dp).fillMaxWidth().clip(BoxShape).background(p.surface)
                    .border(1.dp, p.line, BoxShape).clickable { onPark(r.park.id) }.padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(r.park.name, color = p.ink, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                    val sub = cardSub?.invoke(r) ?: if (r.unridden == 0) "All ridden" else "${r.newModels} new models · ${r.unridden} unridden coasters"
                    Text(listOfNotNull(r.park.place, sub).joinToString(" · "), color = p.muted, fontSize = 12.sp)
                }
                Text("Open ›", color = p.accent, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

/** Five-point star centered on [c] with outer radius [r]. */
private fun starPath(c: Offset, r: Float) = androidx.compose.ui.graphics.Path().apply {
    for (i in 0 until 10) {
        val a = Math.PI / 5 * i - Math.PI / 2
        val rr = if (i % 2 == 0) r else r * 0.45f
        val x = c.x + (rr * kotlin.math.cos(a)).toFloat(); val y = c.y + (rr * kotlin.math.sin(a)).toFloat()
        if (i == 0) moveTo(x, y) else lineTo(x, y)
    }
    close()
}

/** Test hook: screenshot tests turn the never-ending location pulse off so the UI can go idle. */
internal object MapConfig { @Volatile var pulse = true }

private fun log2(v: Double) = ln(v) / ln(2.0)

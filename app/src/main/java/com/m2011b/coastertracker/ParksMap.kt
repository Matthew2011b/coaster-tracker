package com.m2011b.coastertracker

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.drawable.BitmapDrawable
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.m2011b.coastertracker.data.ParkStat
import com.m2011b.kit.theme.LocalPalette
import com.m2011b.kit.ui.BoxShape
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.CopyrightOverlay
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.TilesOverlay
import java.io.File

/** One-time osmdroid setup: identify the app to the tile servers and keep the tile cache in app storage. */
private fun setupOsm(ctx: Context) {
    val c = Configuration.getInstance()
    if (c.userAgentValue == ctx.packageName) return
    c.userAgentValue = ctx.packageName
    c.osmdroidBasePath = File(ctx.filesDir, "osmdroid")
    c.osmdroidTileCache = File(ctx.cacheDir, "osm-tiles")
}

/** Round pin with a number, drawn once per (color, number) and cached. */
private class PinFactory(private val ctx: Context) {
    private val cache = HashMap<Pair<Int, String>, BitmapDrawable>()
    fun get(fill: Int, ink: Int, text: String, small: Boolean): BitmapDrawable = cache.getOrPut(fill to "$text|$small") {
        val d = ctx.resources.displayMetrics.density
        val size = ((if (small) 14 else 26) * d).toInt()
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val cv = Canvas(bmp)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        p.color = android.graphics.Color.WHITE
        cv.drawCircle(size / 2f, size / 2f, size / 2f, p)
        p.color = fill
        cv.drawCircle(size / 2f, size / 2f, size / 2f - 1.5f * d, p)
        if (text.isNotEmpty()) {
            p.color = ink
            p.textSize = (if (text.length > 2) 9.5f else 11.5f) * d
            p.typeface = Typeface.DEFAULT_BOLD
            p.textAlign = Paint.Align.CENTER
            cv.drawText(text, size / 2f, size / 2f - (p.descent() + p.ascent()) / 2, p)
        }
        BitmapDrawable(ctx.resources, bmp)
    }
}

/**
 * Parks on an OpenStreetMap map. Pins show [byModels] ? new models : unridden coasters; parks you've
 * finished show as small dots in the ridden color. Tapping a pin shows a card; tapping the card opens the park.
 */
@Composable
fun ParksMap(rows: List<ParkStat>, done: List<ParkStat>, byModels: Boolean, onPark: (Long) -> Unit, modifier: Modifier = Modifier) {
    val p = LocalPalette.current
    val ctx = LocalContext.current
    val here = LocalHere.current
    val pins = remember(ctx) { PinFactory(ctx) }
    var picked by remember { mutableStateOf<ParkStat?>(null) }
    var framed by remember { mutableStateOf(false) }
    val accent = p.accent.toArgb(); val btnInk = p.btnInk.toArgb(); val newInk = p[AppTokens.NewInk].toArgb()
    val ok = p.ok.toArgb(); val dark = p.isDark

    Box(modifier.clip(BoxShape).border(1.dp, p.line, BoxShape)) {
        AndroidView(
            factory = { c ->
                setupOsm(c)
                MapView(c).apply {
                    setTileSource(TileSourceFactory.MAPNIK)
                    setMultiTouchControls(true)
                    zoomController.setVisibility(org.osmdroid.views.CustomZoomButtonsController.Visibility.NEVER)
                    minZoomLevel = 3.0
                    isTilesScaledToDpi = true
                }
            },
            modifier = Modifier.fillMaxSize(),
            update = { map ->
                map.overlayManager.tilesOverlay.setColorFilter(if (dark) TilesOverlay.INVERT_COLORS else null)
                map.overlays.clear()
                done.forEach { r ->
                    val lat = r.park.lat ?: return@forEach; val lon = r.park.lon ?: return@forEach
                    map.overlays.add(Marker(map).apply {
                        position = GeoPoint(lat, lon); setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                        icon = pins.get(ok, btnInk, "", small = true); title = r.park.name
                        setOnMarkerClickListener { _, _ -> picked = r; true }
                    })
                }
                rows.sortedBy { if (byModels) it.newModels else it.unridden }.forEach { r ->  // bigger numbers drawn on top
                    val lat = r.park.lat ?: return@forEach; val lon = r.park.lon ?: return@forEach
                    val n = if (byModels) r.newModels else r.unridden
                    val fill = if (byModels && n == 0) android.graphics.Color.GRAY else if (byModels) newInk else accent
                    map.overlays.add(Marker(map).apply {
                        position = GeoPoint(lat, lon); setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                        icon = pins.get(fill, android.graphics.Color.WHITE, "$n", small = false); title = r.park.name
                        setOnMarkerClickListener { _, _ -> picked = r; true }
                    })
                }
                map.overlays.add(CopyrightOverlay(map.context))
                if (!framed) {
                    framed = true
                    val h = here
                    if (h != null) { map.controller.setZoom(7.0); map.controller.setCenter(GeoPoint(h.first, h.second)) }
                    else {
                        val pts = rows.mapNotNull { r -> r.park.lat?.let { la -> r.park.lon?.let { lo -> GeoPoint(la, lo) } } }
                        if (pts.size > 1) map.post { map.zoomToBoundingBox(BoundingBox.fromGeoPoints(pts), false, 40) }
                        else { map.controller.setZoom(4.0); map.controller.setCenter(GeoPoint(39.5, -96.0)) }
                    }
                }
                map.invalidate()
            },
            onRelease = { it.onDetach() },
        )

        picked?.let { r ->
            Row(
                Modifier.align(Alignment.BottomCenter).padding(10.dp).fillMaxWidth().clip(BoxShape).background(p.surface)
                    .border(1.dp, p.line, BoxShape).clickable { onPark(r.park.id) }.padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(r.park.name, color = p.ink, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                    val sub = if (r.unridden == 0) "All ridden" else "${r.newModels} new models · ${r.unridden} unridden coasters"
                    Text(listOfNotNull(r.park.place, sub).joinToString(" · "), color = p.muted, fontSize = 12.sp)
                }
                Text("Open ›", color = p.accent, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

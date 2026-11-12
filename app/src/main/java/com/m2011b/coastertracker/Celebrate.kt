package com.m2011b.coastertracker

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.FileProvider
import com.m2011b.coastertracker.data.Milestone
import com.m2011b.kit.theme.LocalKitFonts
import com.m2011b.kit.theme.LocalPalette
import com.m2011b.kit.theme.Mix
import com.m2011b.kit.ui.PrimaryButton
import com.m2011b.kit.ui.SecondaryButton
import com.m2011b.kit.ui.TrackGeometry
import kotlinx.coroutines.launch
import java.io.File
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * The completion popup: a ticket stub for a park, a builder's plate for a manufacturer, a blueprint
 * for a model (US and worldwide versions of the last two). Share sends the card as an image.
 */
@Composable
fun CelebrationDialog(m: Milestone, onClose: () -> Unit) {
    val ctx = LocalContext.current
    val p = LocalPalette.current
    val scope = rememberCoroutineScope()
    val layer = rememberGraphicsLayer()
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(Modifier.padding(22.dp).widthIn(max = 360.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Box(Modifier.drawWithContent { layer.record { this@drawWithContent.drawContent() }; drawLayer(layer) }) {
                when (m) {
                    is Milestone.ParkDone -> TicketCard(m)
                    is Milestone.MakeDone -> PlateCard(m)
                    is Milestone.ModelDone -> BlueprintCard(m)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                PrimaryButton("SHARE", Modifier.weight(1.1f)) { shareLink(ctx, ShareLinks.message(m)) }
                SecondaryButton("Share image", Modifier.weight(1f)) {
                    scope.launch { share(ctx, { layer.toImageBitmap() }, 0xFFDADAD7.toInt(), "${ShareLinks.title(m)} ${ShareLinks.link(m)}") }
                }
                SecondaryButton("Close", Modifier.width(76.dp), onClick = onClose)
            }
        }
    }
}

/** Shares just the line and link: chat apps turn the link into one message with the card as its preview. */
private fun shareLink(ctx: Context, text: String) {
    val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
    try {
        ctx.startActivity(Intent.createChooser(send, "Share").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (e: Exception) {
        android.util.Log.e("LiftHill", "share failed", e)
        android.widget.Toast.makeText(ctx, "Couldn't share", android.widget.Toast.LENGTH_SHORT).show()
    }
}

/** Saves the card on a margin of page color (so notches and corners don't show as black) and opens the share sheet. */
private suspend fun share(ctx: Context, img: suspend () -> ImageBitmap, bg: Int, text: String) = try {
    shareImage(ctx, img(), bg, text)
} catch (e: Exception) {
    android.util.Log.e("LiftHill", "share failed", e)
    android.widget.Toast.makeText(ctx, "Couldn't share the card", android.widget.Toast.LENGTH_SHORT).show()
}

internal fun shareImage(ctx: Context, img: ImageBitmap, bg: Int, text: String) {
    // The captured card is a hardware bitmap on real phones, which a software canvas can't draw: copy it first.
    val raw = img.asAndroidBitmap()
    val card = if (android.os.Build.VERSION.SDK_INT >= 26 && raw.config == Bitmap.Config.HARDWARE) raw.copy(Bitmap.Config.ARGB_8888, false) else raw
    val pad = card.width / 14
    val foot = card.width / 6  // the Lift Hill strip under the card, so the name travels with the image
    val out = Bitmap.createBitmap(card.width + 2 * pad, card.height + 2 * pad + foot, Bitmap.Config.ARGB_8888)
    android.graphics.Canvas(out).apply {
        drawColor(bg); drawBitmap(card, pad.toFloat(), pad.toFloat(), null)
        drawFooter(ctx, this, pad.toFloat(), (pad + card.height).toFloat() + foot * 0.2f, card.width.toFloat(), foot * 0.6f)
    }
    val dir = File(ctx.cacheDir, "shares").apply { mkdirs() }
    val f = File(dir, "lift-hill.png")
    f.outputStream().use { out.compress(Bitmap.CompressFormat.PNG, 100, it) }
    val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.files", f)
    val send = Intent(Intent.ACTION_SEND).setType("image/png").putExtra(Intent.EXTRA_STREAM, uri).putExtra(Intent.EXTRA_TEXT, text)
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    send.clipData = android.content.ClipData.newRawUri(null, uri)  // lets the share sheet preview the image
    ctx.startActivity(Intent.createChooser(send, "Share").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}

/** App icon, "Lift Hill" and "Track your coaster credits", in a row [h] tall. */
private fun drawFooter(ctx: Context, c: android.graphics.Canvas, x: Float, y: Float, w: Float, h: Float) {
    val icon = androidx.core.content.ContextCompat.getDrawable(ctx, R.drawable.ic_launcher_fg)
    val bgPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF25282A.toInt() }
    c.drawRoundRect(x, y, x + h, y + h, h * 0.22f, h * 0.22f, bgPaint)
    icon?.setBounds((x - h * 0.25f).toInt(), (y - h * 0.25f).toInt(), (x + h * 1.25f).toInt(), (y + h * 1.25f).toInt())
    icon?.draw(c)
    val bold = androidx.core.content.res.ResourcesCompat.getFont(ctx, R.font.plex_sans_semibold)
    val regular = androidx.core.content.res.ResourcesCompat.getFont(ctx, R.font.plex_sans_regular)
    val t1 = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF25282A.toInt(); textSize = h * 0.42f; typeface = bold }
    val t2 = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF5C5F62.toInt(); textSize = h * 0.32f; typeface = regular }
    c.drawText("Lift Hill", x + h * 1.3f, y + h * 0.45f, t1)
    c.drawText("Track your coaster credits", x + h * 1.3f, y + h * 0.92f, t2)
}

private val MONTH = DateTimeFormatter.ofPattern("MMM yyyy", Locale.US)
private fun serial(n: Int) = "LH-%04d".format(n)

@Composable
private fun StatCell(value: String, label: String, color: Color, modifier: Modifier, shadow: Shadow? = null) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, color = color, fontFamily = LocalKitFonts.current.mono, fontSize = 20.sp, fontWeight = FontWeight.SemiBold,
            style = TextStyle(shadow = shadow))
        Text(label, color = color.copy(alpha = 0.75f), fontSize = 9.5.sp, letterSpacing = 1.2.sp, style = TextStyle(shadow = shadow))
    }
}

/**
 * Card textures, made once from random noise (no image files): paper grain for the ticket and
 * blueprint, horizontal brushed-metal streaks for the plates. Fixed seeds, so they look the same every time.
 */
private object Grain {
    val paper: Brush by lazy {
        val n = 160; val r = java.util.Random(3)
        val px = IntArray(n * n) { (r.nextInt(150) shl 24) or 0x736147 }
        ShaderBrush(ImageShader(Bitmap.createBitmap(px, n, n, Bitmap.Config.ARGB_8888).asImageBitmap(), TileMode.Repeated, TileMode.Repeated))
    }
    val brushed: Brush by lazy {
        val w = 900; val h = 240; val r = java.util.Random(7)
        val rows = FloatArray(h) { r.nextFloat() }
        val px = IntArray(w * h)
        for (y in 0 until h) {
            var v = rows[y]
            for (x in 0 until w) {
                v = (v + (r.nextFloat() - 0.5f) * 0.04f).coerceIn(0f, 1f)  // drifts slowly along the row
                val a = (v * 0.55f + r.nextFloat() * 0.08f).coerceIn(0f, 1f)
                px[y * w + x] = ((a * 255).toInt() shl 24) or 0xFFFFFF
            }
        }
        ShaderBrush(ImageShader(Bitmap.createBitmap(px, w, h, Bitmap.Config.ARGB_8888).asImageBitmap(), TileMode.Repeated, TileMode.Repeated))
    }
}

// ---------- Park: ticket stub ----------

/** Fixed artwork colors: the ticket looks the same whatever the theme. */
private object Ticket {
    val band = Color(0xFFF0A04B)
    val bandInk = Color(0xFF2A1A08)
    val paper = Color(0xFFFFFFFF)
    val ink = Color(0xFF25282A)
    val muted = Color(0xFF6E7174)
    val line = Color(0xFFD1D1D2)
}

/** Rounded card with a half-circle notch on each side, [fromBottom] above the bottom edge. */
private class TicketShape(val corner: Float, val notch: Float, val fromBottom: Float) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val body = Path().apply { addRoundRect(RoundRect(0f, 0f, size.width, size.height, CornerRadius(corner))) }
        val y = size.height - fromBottom
        val holes = Path().apply {
            addOval(androidx.compose.ui.geometry.Rect(Offset(0f, y), notch))
            addOval(androidx.compose.ui.geometry.Rect(Offset(size.width, y), notch))
        }
        return Outline.Generic(Path.combine(PathOperation.Difference, body, holes))
    }
}

@Composable
private fun TicketCard(m: Milestone.ParkDone) {
    val d = LocalDensity.current
    val stub = 70.dp
    val shape = remember(d) { with(d) { TicketShape(14.dp.toPx(), 11.dp.toPx(), stub.toPx()) } }
    Column(Modifier.fillMaxWidth().clip(shape).background(Ticket.paper)
        .drawWithContent { drawContent(); drawRect(Grain.paper, alpha = 0.45f, blendMode = BlendMode.Multiply) }
        .semantics { contentDescription = "Park complete" }) {
        Row(Modifier.fillMaxWidth().background(Ticket.band).padding(horizontal = 18.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("PARK COMPLETE", color = Ticket.bandInk, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 2.sp, modifier = Modifier.weight(1f))
            Text("No. %04d".format(m.creditNo), color = Ticket.bandInk, fontFamily = LocalKitFonts.current.mono, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
        }
        Column(Modifier.fillMaxWidth().padding(start = 18.dp, end = 18.dp, top = 20.dp, bottom = 16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(m.park.name, color = Ticket.ink, fontSize = 26.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center, lineHeight = 29.sp)
            Text("Every coaster ridden", color = Ticket.muted, fontSize = 13.sp)
        }
        Canvas(Modifier.fillMaxWidth().height(2.dp).padding(horizontal = 14.dp)) {
            drawLine(Ticket.line, Offset(0f, size.height / 2), Offset(size.width, size.height / 2), 2.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx())))
        }
        Row(Modifier.fillMaxWidth().height(stub - 1.dp).padding(horizontal = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            StatCell("${m.coasters}", "COASTERS", Ticket.ink, Modifier.weight(1f))
            StatCell("${m.models}", "MODELS", Ticket.ink, Modifier.weight(1f))
            StatCell("${m.year}", "FINISHED", Ticket.ink, Modifier.weight(1f))
        }
    }
}

// ---------- Manufacturer: builder's plate ----------

/** Fixed artwork colors (like the logo): steel for the US plate, brass for worldwide. */
private object Plate {
    val steel = listOf(Color(0xFFE4E7EA), Color(0xFFAEB4BA), Color(0xFFD3D7DB), Color(0xFF8E959C))
    val brass = listOf(Color(0xFFF1D89A), Color(0xFFC9A24E), Color(0xFFE6C77E), Color(0xFFA88434))
    val steelInk = Color(0xFF2B3036)
    val brassInk = Color(0xFF4A3510)
    val steelHi = Color(0x99FFFFFF)
    val brassHi = Color(0x99FFF8DC)
}

@Composable
private fun PlateCard(m: Milestone.MakeDone) {
    val stops = if (m.world) Plate.brass else Plate.steel
    val ink = if (m.world) Plate.brassInk else Plate.steelInk
    val hi = if (m.world) Plate.brassHi else Plate.steelHi
    val engraved = Shadow(hi, Offset(0f, 2f), 0f)
    val shape = RoundedCornerShape(8.dp)
    Box(
        Modifier.fillMaxWidth().shadow(8.dp, shape).clip(shape)
            .drawBehind {
                drawRect(Brush.linearGradient(stops, Offset.Zero, Offset(size.width * 0.75f, size.height * 1.4f)))
                drawRect(Grain.brushed, alpha = 0.9f, blendMode = BlendMode.Overlay)
                val inset = 4.dp.toPx()
                drawRoundRect(hi, Offset(inset, inset), Size(size.width - 2 * inset, size.height - 2 * inset), CornerRadius(5.dp.toPx()), style = Stroke(1.5.dp.toPx()))
                drawRect(Color(0x40000000), style = Stroke(1.5.dp.toPx()))
                val r = 5.dp.toPx(); val e = 14.dp.toPx()
                for (c in listOf(Offset(e, e), Offset(size.width - e, e), Offset(e, size.height - e), Offset(size.width - e, size.height - e))) {
                    drawCircle(Brush.radialGradient(listOf(hi, ink), c - Offset(r / 3, r / 3), r * 1.6f), r, c)
                }
            }
            .padding(horizontal = 24.dp, vertical = 18.dp)
            .semantics { contentDescription = "Manufacturer complete" },
    ) {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("MANUFACTURER COMPLETE", color = ink, fontSize = 10.5.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 2.sp, style = TextStyle(shadow = engraved))
            Text(m.make, color = ink, fontSize = if (m.make.length > 22) 21.sp else 25.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center,
                lineHeight = 28.sp, modifier = Modifier.padding(top = 8.dp, bottom = 2.dp), style = TextStyle(shadow = engraved))
            Row(Modifier.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f).height(1.5.dp).background(ink.copy(alpha = 0.35f)))
                Text(if (m.world) "WORLDWIDE" else "UNITED STATES", color = ink, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 2.sp,
                    modifier = Modifier.padding(horizontal = 8.dp), style = TextStyle(shadow = engraved))
                Box(Modifier.weight(1f).height(1.5.dp).background(ink.copy(alpha = 0.35f)))
            }
            Row(Modifier.fillMaxWidth()) {
                StatCell("${m.coasters}", "COASTERS", ink, Modifier.weight(1f), engraved)
                StatCell("${m.models}", "MODELS", ink, Modifier.weight(1f), engraved)
                StatCell("${m.places}", if (m.world) "COUNTRIES" else "PARKS", ink, Modifier.weight(1f), engraved)
            }
            Text("SERIAL ${serial(m.creditNo)} · ${MONTH.format(m.date).uppercase(Locale.US)}", color = ink.copy(alpha = 0.75f),
                fontFamily = LocalKitFonts.current.mono, fontSize = 10.sp, letterSpacing = 1.sp, modifier = Modifier.padding(top = 10.dp))
        }
    }
}

// ---------- Model: blueprint ----------

private object Blueprint {
    val paperUs = Color(0xFF163F73)
    val paperWorld = Color(0xFF0F2E55)
    val grid = Color(0x12FFFFFF)
    val lineC = Color(0xB3FFFFFF)
}

@Composable
private fun BlueprintCard(m: Milestone.ModelDone) {
    val p = LocalPalette.current
    val mono = LocalKitFonts.current.mono
    val stamp = if (m.world) Color(0xFFFFC93C) else Color(0xFFF0A04B)  // fixed, like the rest of the card
    val shape = RoundedCornerShape(6.dp)
    Box(
        Modifier.fillMaxWidth().shadow(8.dp, shape).clip(shape).background(if (m.world) Blueprint.paperWorld else Blueprint.paperUs)
            .drawBehind {
                val step = 16.dp.toPx()
                var x = 0f; while (x < size.width) { drawLine(Blueprint.grid, Offset(x, 0f), Offset(x, size.height), 1f); x += step }
                var y = 0f; while (y < size.height) { drawLine(Blueprint.grid, Offset(0f, y), Offset(size.width, y), 1f); y += step }
                drawRect(Grain.paper, alpha = 0.3f, blendMode = BlendMode.Multiply)
            }
            .padding(12.dp)
            .semantics { contentDescription = "Model complete" },
    ) {
        Column(Modifier.fillMaxWidth().border(1.5.dp, Blueprint.lineC)) {
            Box(Modifier.fillMaxWidth().padding(top = 16.dp, start = 10.dp, end = 10.dp)) {
                Canvas(Modifier.fillMaxWidth().height(42.dp)) {
                    val k = size.height / (56.dp.toPx())
                    val g = TrackGeometry(size.width / (density * k))
                    drawPath(g.path(density * k), Color.White, style = Stroke(1.6.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
                }
                Text(
                    if (m.world) "WORLD COMPLETE" else "US COMPLETE", color = stamp, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.5.sp,
                    modifier = Modifier.align(Alignment.TopCenter).offset(y = (-8).dp).rotate(-8f)
                        .border(2.5.dp, stamp, RoundedCornerShape(4.dp)).padding(horizontal = 8.dp, vertical = 3.dp),
                )
            }
            Column(Modifier.padding(horizontal = 10.dp, vertical = 10.dp)) {
                Text(m.model, color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.SemiBold, lineHeight = 27.sp)
                Text("By ${m.make}", color = Color.White, fontSize = 14.sp)
                Text("Model complete · ${if (m.world) "every one on Earth" else "every one in the US"}", color = Color.White.copy(alpha = 0.7f),
                    fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
            }
            @Composable fun Cell(k: String, v: String, mod: Modifier) = Column(mod.border(0.75.dp, Blueprint.lineC).padding(horizontal = 6.dp, vertical = 4.dp)) {
                Text(k, color = Color.White.copy(alpha = 0.7f), fontSize = 8.sp, letterSpacing = 1.sp)
                Text(v, color = Color.White, fontFamily = mono, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
            }
            Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
                Cell("SCOPE", if (m.world) "WORLDWIDE" else "UNITED STATES", Modifier.weight(1.4f).fillMaxHeight())
                Cell("QTY RIDDEN", "${m.count} OF ${m.count}", Modifier.weight(1f).fillMaxHeight())
            }
            Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
                Cell("DATE", m.date.toString(), Modifier.weight(1.4f).fillMaxHeight())
                Cell("DWG NO.", serial(m.creditNo), Modifier.weight(1f).fillMaxHeight())
            }
        }
    }
}

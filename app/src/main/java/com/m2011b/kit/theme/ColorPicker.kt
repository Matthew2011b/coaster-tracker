package com.m2011b.kit.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.m2011b.kit.ui.BoxShape
import com.m2011b.kit.ui.FormDialog
import java.util.Locale

private val PRESETS = listOf(
    0xFF000000, 0xFF25282A, 0xFF707372, 0xFFD3D3D3, 0xFFF3F3F3, 0xFFFFFFFF,
    0xFFB3261E, 0xFFCF1F2F, 0xFFE8710A, 0xFFF2A900, 0xFF1E7B46, 0xFF5CCB8A,
    0xFF0B7A75, 0xFF1F5C99, 0xFF5B9BD5, 0xFF3F51B5, 0xFF6A1B9A, 0xFFD81B60,
).map { Color(it) }

/** HSV sliders, hex entry and preset swatches, with the old color shown for comparison. */
@Composable
fun ColorPickerDialog(title: String, initial: Color, onDismiss: () -> Unit, onPick: (Color) -> Unit) {
    val p = LocalPalette.current
    val mono = LocalKitFonts.current.mono
    val hsv0 = remember { FloatArray(3).also { android.graphics.Color.colorToHSV(initial.toArgb(), it) } }
    var h by remember { mutableFloatStateOf(hsv0[0]) }
    var s by remember { mutableFloatStateOf(hsv0[1]) }
    var v by remember { mutableFloatStateOf(hsv0[2]) }
    val color = Color(android.graphics.Color.HSVToColor(floatArrayOf(h, s, v)))
    var hexText by remember { mutableStateOf(ThemeStore.hex6(initial)) }
    val hexInteraction = remember { MutableInteractionSource() }
    val hexFocused by hexInteraction.collectIsFocusedAsState()
    LaunchedEffect(hexFocused) { if (hexFocused) hexText = ThemeStore.hex6(color) }

    fun setFrom(c: Color) {
        val a = FloatArray(3); android.graphics.Color.colorToHSV(c.toArgb(), a)
        h = a[0]; s = a[1]; v = a[2]
    }

    FormDialog(title = title, onDismiss = onDismiss, confirm = "Use color" to { onPick(color) }, dismiss = listOf("Cancel" to onDismiss)) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(Modifier.size(56.dp).background(color, BoxShape).border(1.dp, p.line, BoxShape))
                Box(Modifier.size(28.dp).background(initial, BoxShape).border(1.dp, p.line, BoxShape))
                Text("was", color = p.muted, fontSize = 12.sp)
                Spacer(Modifier.weight(1f))
                Text("#", color = p.muted, fontFamily = mono, fontSize = 18.sp)
                val shown = if (hexFocused) hexText else ThemeStore.hex6(color)
                BasicTextField(
                    value = shown,
                    onValueChange = { raw ->
                        val t = raw.uppercase(Locale.US).filter { it in "0123456789ABCDEF" }.take(6)
                        hexText = t
                        if (t.length == 6) setFrom(Color(0xFF000000 or t.toLong(16)))
                    },
                    singleLine = true,
                    interactionSource = hexInteraction,
                    textStyle = TextStyle(fontFamily = mono, fontSize = 18.sp, color = p.ink),
                    cursorBrush = SolidColor(p.accent),
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
                    modifier = Modifier.width(96.dp).background(p.bg, BoxShape)
                        .border(2.dp, if (shown.length == 6) p.line else p.err, BoxShape)
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                )
            }
            GradientSlider("Hue", h, 0f..360f, Brush.horizontalGradient((0..6).map { Color.hsv(it * 60f % 360f, 1f, 1f) })) { h = it }
            GradientSlider("Saturation", s, 0f..1f, Brush.horizontalGradient(listOf(Color.hsv(h, 0f, v), Color.hsv(h, 1f, v)))) { s = it }
            GradientSlider("Brightness", v, 0f..1f, Brush.horizontalGradient(listOf(Color.Black, Color.hsv(h, s, 1f)))) { v = it }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                PRESETS.take(9).forEach { Swatch(it, Modifier.weight(1f)) { setFrom(it) } }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                PRESETS.drop(9).forEach { Swatch(it, Modifier.weight(1f)) { setFrom(it) } }
            }
        }
    }
}

@Composable
private fun Swatch(c: Color, modifier: Modifier, onClick: () -> Unit) {
    Box(modifier.aspectRatio(1f).clip(CircleShape).background(c).border(1.dp, LocalPalette.current.line, CircleShape).clickable(onClick = onClick))
}

@Composable
private fun GradientSlider(label: String, value: Float, range: ClosedFloatingPointRange<Float>, track: Brush, onChange: (Float) -> Unit) {
    val p = LocalPalette.current
    Column {
        Text(label, color = p.muted, fontSize = 12.sp)
        Box(contentAlignment = Alignment.Center) {
            Box(Modifier.fillMaxWidth().padding(horizontal = 10.dp).height(12.dp).clip(RoundedCornerShape(6.dp)).background(track))
            Slider(
                value = value, onValueChange = onChange, valueRange = range,
                colors = SliderDefaults.colors(thumbColor = p.ink, activeTrackColor = Color.Transparent, inactiveTrackColor = Color.Transparent),
            )
        }
    }
}

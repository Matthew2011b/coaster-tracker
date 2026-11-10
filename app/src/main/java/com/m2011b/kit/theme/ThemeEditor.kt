package com.m2011b.kit.theme

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.m2011b.kit.ui.*

/**
 * Custom colors: the core colors for the light and dark palettes, an "Advanced colors" switch that
 * exposes the derived ones, reset, and theme codes to copy a look between apps.
 */
@Composable
fun ThemeEditorScreen(store: ThemeStore, modifier: Modifier = Modifier) {
    val p = LocalPalette.current
    val ctx = LocalContext.current
    var editDark by rememberSaveable { mutableStateOf(p.isDark) }
    var editing by remember { mutableStateOf<ColorToken?>(null) }
    var confirmReset by remember { mutableStateOf(false) }
    var pasteOpen by remember { mutableStateOf(false) }

    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Segmented(listOf("Light colors", "Dark colors"), if (editDark) 1 else 0) { editDark = it == 1 }
        if (editDark != p.isDark) {
            Text(
                "The ${if (editDark) "dark" else "light"} colors aren't showing right now, so changes won't be visible until the theme switches.",
                color = p.muted, fontSize = 12.sp,
            )
        }
        Panel(Modifier.fillMaxWidth()) {
            store.core.forEach { t -> TokenRow(store, t, editDark) { editing = t } }
        }
        Panel(Modifier.fillMaxWidth()) {
            Row(
                Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable { store.changeAdvanced(!store.advanced) },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Advanced colors", color = p.ink, fontSize = 15.sp)
                    if (!store.advanced) Text("Worked out from the colors above", color = p.muted, fontSize = 12.sp)
                }
                Switch(
                    store.advanced, onCheckedChange = { store.changeAdvanced(it) },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = p.btnInk, checkedTrackColor = p.accent, uncheckedThumbColor = p.muted,
                        uncheckedTrackColor = p.surface, uncheckedBorderColor = p.line,
                    ),
                )
            }
            if (store.advanced) store.derivedTokens.forEach { t -> TokenRow(store, t, editDark) { editing = t } }
        }
        SecondaryButton("RESET ${if (editDark) "DARK" else "LIGHT"} COLORS") { confirmReset = true }

        FieldLabel("Share between apps")
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            SecondaryButton("COPY THEME CODE", Modifier.weight(1f)) {
                val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                cm.setPrimaryClip(ClipData.newPlainText("Theme code", store.exportCode()))
                Toast.makeText(ctx, "Theme code copied", Toast.LENGTH_SHORT).show()
            }
            SecondaryButton("PASTE CODE", Modifier.weight(1f)) { pasteOpen = true }
        }
    }

    editing?.let { t ->
        ColorPickerDialog(t.label, store.get(editDark, t), onDismiss = { editing = null }) {
            store.set(editDark, t, it); editing = null
        }
    }
    if (confirmReset) {
        KitDialog(
            "Reset colors?", onDismiss = { confirmReset = false },
            confirm = "Reset" to { store.reset(editDark); confirmReset = false },
            dismiss = listOf("Cancel" to { confirmReset = false }),
        ) { Text("Every ${if (editDark) "dark" else "light"} color goes back to its default.") }
    }
    if (pasteOpen) PasteCodeDialog(store) { pasteOpen = false }
}

@Composable
private fun TokenRow(store: ThemeStore, t: ColorToken, dark: Boolean, onClick: () -> Unit) {
    val p = LocalPalette.current
    val c = store.get(dark, t)
    val own = !store.isDefault(dark, t)
    Row(
        Modifier.fillMaxWidth().clip(BoxShape).clickable(onClick = onClick).padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(36.dp).background(c, BoxShape).border(1.dp, p.line, BoxShape))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(t.label, color = p.ink, fontSize = 15.sp)
            val note = when { !own && t.derived -> "  auto"; own && !t.derived -> "  changed"; else -> "" }
            Text("#" + ThemeStore.hex6(c) + note, color = p.muted, fontFamily = LocalKitFonts.current.mono, fontSize = 12.sp)
        }
        if (own && t.derived) {
            Text("AUTO", color = p.accent, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                modifier = Modifier.clip(BoxShape).clickable { store.clear(dark, t) }.padding(horizontal = 10.dp, vertical = 8.dp))
        }
    }
}

@Composable
private fun PasteCodeDialog(store: ThemeStore, onDone: () -> Unit) {
    val ctx = LocalContext.current
    val clip = remember {
        (ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).primaryClip
            ?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(ctx)?.toString().orEmpty()
    }
    var code by remember { mutableStateOf(if (clip.startsWith("THEME1|")) clip else "") }
    var error by remember { mutableStateOf<String?>(null) }
    FormDialog(
        "Paste theme code", onDismiss = onDone,
        confirm = "Apply" to {
            val n = store.importCode(code)
            if (n == null) error = "That isn't a theme code."
            else { Toast.makeText(ctx, "Applied $n colors", Toast.LENGTH_SHORT).show(); onDone() }
        },
        dismiss = listOf("Cancel" to onDone),
    ) {
        FieldBox(
            label = "Theme code", value = code, onValueChange = { code = it; error = null },
            status = when { error != null -> FieldStatus.Invalid; code.startsWith("THEME1|") -> FieldStatus.Valid; else -> FieldStatus.Empty },
            hint = error ?: "Starts with THEME1|", minLines = 3, placeholder = "THEME1|System|L:...",
        )
    }
}

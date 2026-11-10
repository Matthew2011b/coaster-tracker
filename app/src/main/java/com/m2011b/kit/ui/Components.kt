package com.m2011b.kit.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.m2011b.kit.theme.LocalKitFonts
import com.m2011b.kit.theme.LocalPalette
import java.util.Locale

val BoxShape = RoundedCornerShape(10.dp)

/** Drives a FieldBox's border and hint color. */
enum class FieldStatus { Empty, Typing, Valid, Invalid }

/**
 * Labeled input box: rounded surface with a 2dp border that is grey when idle, accent while
 * focused or typing, green when valid, red when invalid, and a status line underneath.
 * [numeric] switches to a big monospace style and a decimal keyboard.
 */
@Composable
fun FieldBox(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    status: FieldStatus,
    hint: String,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    numeric: Boolean = false,
    minLines: Int = 1,
    capitalize: Boolean = false,
    keyboardType: KeyboardType? = null,
) {
    val p = LocalPalette.current
    val fonts = LocalKitFonts.current
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val stroke = when (status) {
        FieldStatus.Invalid -> p.err
        FieldStatus.Valid -> p.ok
        FieldStatus.Typing -> p.accent
        FieldStatus.Empty -> if (focused) p.accent else p.line
    }
    val hintColor = when (status) {
        FieldStatus.Invalid -> p.err
        FieldStatus.Valid -> p.ok
        else -> p.muted
    }
    val textStyle = if (numeric)
        TextStyle(fontFamily = fonts.mono, fontWeight = FontWeight.SemiBold, fontSize = 26.sp, color = p.ink, letterSpacing = 1.sp)
    else
        TextStyle(fontFamily = fonts.sans, fontSize = 17.sp, color = p.ink, lineHeight = 24.sp)

    Column(modifier.fillMaxWidth()) {
        FieldLabel(label)
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = minLines == 1,
            minLines = minLines,
            textStyle = textStyle,
            cursorBrush = SolidColor(p.accent),
            interactionSource = interaction,
            keyboardOptions = KeyboardOptions(
                keyboardType = keyboardType ?: if (numeric) KeyboardType.Decimal else KeyboardType.Text,
                capitalization = if (capitalize) KeyboardCapitalization.Words else KeyboardCapitalization.Sentences,
            ),
            modifier = Modifier.fillMaxWidth(),
            decorationBox = { inner ->
                Box(
                    Modifier.fillMaxWidth()
                        .background(p.surface, BoxShape)
                        .border(2.dp, stroke, BoxShape)
                        .padding(horizontal = 16.dp, vertical = if (numeric) 12.dp else 14.dp),
                ) {
                    if (value.isEmpty() && placeholder.isNotEmpty()) Text(placeholder, style = textStyle.copy(color = p.muted.copy(alpha = 0.6f)))
                    inner()
                }
            },
        )
        Text(hint, color = hintColor, fontSize = 13.sp, modifier = Modifier.padding(start = 4.dp, top = 6.dp))
    }
}

/** Small uppercase label used above boxes and sections. */
@Composable
fun FieldLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text.uppercase(Locale.US),
        color = LocalPalette.current.muted,
        fontSize = 12.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.sp,
        modifier = modifier.padding(start = 4.dp, bottom = 6.dp),
    )
}

/** Dropdown picker drawn in the same box style as FieldBox. */
@Composable
fun <T> SelectBox(
    label: String,
    items: List<T>,
    selected: T?,
    itemLabel: (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "Select",
) {
    val p = LocalPalette.current
    var open by remember { mutableStateOf(false) }
    Column(modifier.fillMaxWidth()) {
        FieldLabel(label)
        Box {
            Row(
                Modifier.fillMaxWidth()
                    .clip(BoxShape)
                    .background(p.surface)
                    .border(2.dp, if (open) p.accent else p.line, BoxShape)
                    .clickable { open = true }
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(selected?.let(itemLabel) ?: placeholder, color = if (selected == null) p.muted else p.ink,
                    fontSize = 19.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                Text(if (open) "▲" else "▼", color = p.muted, fontSize = 14.sp)
            }
            DropdownMenu(
                expanded = open,
                onDismissRequest = { open = false },
                containerColor = p.surface,
                modifier = Modifier.border(1.dp, p.line, RoundedCornerShape(4.dp)),
            ) {
                items.forEach { item ->
                    DropdownMenuItem(
                        text = { Text(itemLabel(item), color = p.ink, fontSize = 17.sp,
                            fontWeight = if (item == selected) FontWeight.SemiBold else FontWeight.Normal) },
                        onClick = { onSelect(item); open = false },
                    )
                }
            }
        }
    }
}

/** Full-width 56dp accent button. */
@Composable
fun PrimaryButton(text: String, modifier: Modifier = Modifier, enabled: Boolean = true, onClick: () -> Unit) {
    val p = LocalPalette.current
    Button(
        onClick = onClick,
        enabled = enabled,
        shape = BoxShape,
        colors = ButtonDefaults.buttonColors(
            containerColor = p.accent, contentColor = p.btnInk,
            disabledContainerColor = p.line, disabledContentColor = p.muted,
        ),
        modifier = modifier.fillMaxWidth().height(56.dp),
    ) { Text(text, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.sp) }
}

/** Full-width outlined button for secondary actions. */
@Composable
fun SecondaryButton(text: String, modifier: Modifier = Modifier, enabled: Boolean = true, onClick: () -> Unit) {
    val p = LocalPalette.current
    OutlinedButton(
        onClick = onClick, enabled = enabled, shape = BoxShape,
        border = androidx.compose.foundation.BorderStroke(2.dp, p.line),
        contentPadding = PaddingValues(horizontal = 8.dp),
        modifier = modifier.fillMaxWidth().height(52.dp),
    ) { Text(text, color = p.ink, fontWeight = FontWeight.SemiBold, maxLines = 1, softWrap = false, textAlign = androidx.compose.ui.text.style.TextAlign.Center) }
}

/** Flat surface card with a 1dp border. */
@Composable
fun Panel(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val p = LocalPalette.current
    Column(modifier.background(p.surface, BoxShape).border(1.dp, p.line, BoxShape).padding(16.dp), content = content)
}

/** Compact number tile. The label reserves two lines so values line up across a row. */
@Composable
fun StatTile(label: String, value: String, modifier: Modifier = Modifier) {
    val p = LocalPalette.current
    Column(modifier.background(p.surface, BoxShape).border(1.dp, p.line, BoxShape).padding(10.dp)) {
        Text(label, color = p.muted, fontSize = 10.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.5.sp,
            lineHeight = 13.sp, minLines = 2, maxLines = 2)
        Spacer(Modifier.height(4.dp))
        Text(value, color = p.ink, fontFamily = LocalKitFonts.current.mono, fontWeight = FontWeight.SemiBold, fontSize = 22.sp, maxLines = 1)
    }
}

/** Row of mutually exclusive options, like a segmented control. */
@Composable
fun Segmented(options: List<String>, selected: Int, modifier: Modifier = Modifier, onSelect: (Int) -> Unit) {
    val p = LocalPalette.current
    Row(modifier.fillMaxWidth().background(p.surface, BoxShape).border(2.dp, p.line, BoxShape).padding(4.dp)) {
        options.forEachIndexed { i, o ->
            val on = i == selected
            Box(
                Modifier.weight(1f).clip(RoundedCornerShape(7.dp))
                    .background(if (on) p.accent else Color.Transparent)
                    .selectable(selected = on, role = androidx.compose.ui.semantics.Role.Tab) { onSelect(i) }.padding(vertical = 10.dp),
                contentAlignment = Alignment.Center,
            ) { Text(o, color = if (on) p.btnInk else p.ink, fontWeight = FontWeight.SemiBold) }
        }
    }
}

/**
 * Message dialog (text only). For anything with input boxes use [FormDialog]:
 * AlertDialog plus full-width fields can lock up layout at real phone widths.
 */
@Composable
fun KitDialog(
    title: String,
    onDismiss: () -> Unit,
    confirm: Pair<String, () -> Unit>,
    dismiss: List<Pair<String, () -> Unit>> = emptyList(),
    body: @Composable () -> Unit,
) {
    val p = LocalPalette.current
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = p.surface, titleContentColor = p.ink, textContentColor = p.ink,
        shape = RoundedCornerShape(14.dp),
        title = { Text(title, fontWeight = FontWeight.SemiBold) },
        text = body,
        confirmButton = { TextButton(onClick = confirm.second) { Text(confirm.first, color = p.accent, fontWeight = FontWeight.SemiBold) } },
        dismissButton = if (dismiss.isEmpty()) null else {
            { Row { dismiss.forEach { (t, f) -> TextButton(onClick = f) { Text(t, color = p.muted) } } } }
        },
    )
}

/** Fixed-width dialog for forms, pickers and anything with input boxes. */
@Composable
fun FormDialog(
    title: String,
    onDismiss: () -> Unit,
    confirm: Pair<String, () -> Unit>,
    dismiss: List<Pair<String, () -> Unit>> = emptyList(),
    content: @Composable ColumnScope.() -> Unit,
) {
    val p = LocalPalette.current
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            Modifier.fillMaxWidth(0.9f).widthIn(max = 480.dp)
                .background(p.surface, RoundedCornerShape(14.dp))
                .padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 8.dp),
        ) {
            Text(title, color = p.ink, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(16.dp))
            content()
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.End) {
                dismiss.forEach { (t, f) -> TextButton(onClick = f) { Text(t, color = p.muted) } }
                TextButton(onClick = confirm.second) { Text(confirm.first, color = p.accent, fontWeight = FontWeight.SemiBold) }
            }
        }
    }
}

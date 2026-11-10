package com.m2011b.coastertracker

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.m2011b.coastertracker.data.Repo
import com.m2011b.coastertracker.data.Sync
import com.m2011b.kit.theme.LocalPalette
import com.m2011b.kit.theme.Std
import com.m2011b.kit.theme.ThemeMode
import com.m2011b.kit.theme.ThemeStore
import com.m2011b.kit.ui.*
import kotlinx.coroutines.launch

private data class Preset(val name: String, val light: Long, val dark: Long)
private val PRESETS = listOf(
    Preset("Blue", 0xFF1F5C99, 0xFF5B9BD5), Preset("Orange", 0xFFB4471B, 0xFFF08A5D),
    Preset("Green", 0xFF1E7B46, 0xFF5CCB8A), Preset("Purple", 0xFF5B3A99, 0xFFA98BE0),
)

@Composable
fun SettingsScreen(repo: Repo, theme: ThemeStore, onCustomColors: () -> Unit) {
    val p = LocalPalette.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var key by remember { mutableStateOf(repo.apiKey) }
    var syncing by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf<String?>(null) }
    var message by remember { mutableStateOf<Pair<String, String>?>(null) }

    val exporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        if (uri != null) runCatching {
            context.contentResolver.openOutputStream(uri)!!.bufferedWriter().use { it.write(repo.exportCsv()) }
        }.onSuccess { message = "Exported" to "${repo.stats.credits} rides saved." }
            .onFailure { message = "Export failed" to (it.message ?: "Could not write the file.") }
    }
    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) runCatching {
            val text = context.contentResolver.openInputStream(uri)!!.bufferedReader().use { it.readText() }
            repo.importCsv(text)
        }.onSuccess { r ->
            val miss = r.unmatched.take(8).joinToString("\n") { "${it.coaster}, ${it.park}" }
            message = "Imported" to ("${r.matched.size} rides matched." +
                if (r.unmatched.isEmpty()) "" else "\n${r.unmatched.size} not found:\n$miss" + if (r.unmatched.size > 8) "\n..." else "")
        }.onFailure { message = "Import failed" to (it.message ?: "Could not read the file.") }
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Panel(Modifier.fillMaxWidth()) {
            FieldLabel("Colors")
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                val dark = p.isDark
                PRESETS.forEach { pr ->
                    val c = Color(if (dark) pr.dark else pr.light)
                    val on = p.accent == c
                    Box(Modifier.size(44.dp).clip(CircleShape).background(c).border(if (on) 3.dp else 0.dp, p.ink, CircleShape)
                        .semantics { contentDescription = "${pr.name} colors" }
                        .clickable { theme.set(false, Std.Accent, Color(pr.light)); theme.set(true, Std.Accent, Color(pr.dark)) })
                }
                Box(Modifier.size(44.dp).clip(CircleShape).background(p.surface).border(1.dp, p.muted, CircleShape)
                    .semantics { contentDescription = "Custom colors" }.clickable(onClick = onCustomColors),
                    contentAlignment = Alignment.Center) { Text("+", color = p.ink, fontSize = 22.sp) }
            }
            Spacer(Modifier.height(12.dp))
            Segmented(listOf("System", "Light", "Dark"), theme.mode.ordinal) { theme.changeMode(ThemeMode.entries[it]) }
        }

        Panel(Modifier.fillMaxWidth()) {
            ToggleRow("Include defunct coasters", repo.filters.includeDefunct) { repo.updateFilters(repo.filters.copy(includeDefunct = it)) }
            Spacer(Modifier.height(4.dp))
            ToggleRow("Include kiddie and carnival", repo.filters.includeKiddie) { repo.updateFilters(repo.filters.copy(includeKiddie = it)) }
            Spacer(Modifier.height(4.dp))
            ToggleRow("US parks only", repo.filters.usOnly) { repo.updateFilters(repo.filters.copy(usOnly = it)) }
        }

        Panel(Modifier.fillMaxWidth()) {
            FieldBox("Coasterpedia API key", key, { key = it; repo.setKey(it) }, if (key.isBlank()) FieldStatus.Empty else FieldStatus.Typing,
                "Free key at developer.coasterpedia.net", placeholder = "cpk_...")
            Spacer(Modifier.height(8.dp))
            PrimaryButton(if (syncing) "UPDATING..." else "UPDATE COASTER DATA", enabled = key.isNotBlank() && !syncing) {
                syncing = true
                scope.launch {
                    status = runCatching { repo.sync { status = it } }.fold(
                        { "Updated: ${it.added} new, ${it.updated} changed" },
                        { (it as? Sync.Failed)?.message ?: "Update failed: ${it.message ?: "no connection"}" },
                    )
                    syncing = false
                }
            }
            Spacer(Modifier.height(6.dp))
            Text(status ?: "Data as of ${repo.dataAsOf ?: "unknown"}", color = p.muted, fontSize = 13.sp)
        }

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            SecondaryButton("Import CSV", Modifier.weight(1f)) { importer.launch(arrayOf("text/*", "application/csv", "application/octet-stream")) }
            SecondaryButton("Export CSV", Modifier.weight(1f)) { exporter.launch("coaster-rides.csv") }
        }

        Text("Coaster data from Coasterpedia contributors, CC BY-SA 4.0.", color = p.muted, fontSize = 12.sp)
    }

    message?.let { (t, b) -> KitDialog(t, onDismiss = { message = null }, confirm = "OK" to { message = null }) { Text(b) } }
}

@Composable
private fun ToggleRow(label: String, on: Boolean, onChange: (Boolean) -> Unit) {
    val p = LocalPalette.current
    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable { onChange(!on) }, verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = p.ink, fontSize = 15.sp, modifier = Modifier.weight(1f))
        Switch(on, onCheckedChange = onChange, colors = SwitchDefaults.colors(
            checkedThumbColor = p.btnInk, checkedTrackColor = p.accent, uncheckedThumbColor = p.muted, uncheckedTrackColor = p.surface,
            uncheckedBorderColor = p.line))
    }
}

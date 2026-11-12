package com.m2011b.coastertracker

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.style.TextAlign
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
    Preset("Teal", 0xFF16706B, 0xFF4FB3AB), Preset("Blue", 0xFF1F5C99, 0xFF5B9BD5),
    Preset("Charcoal", 0xFF3A3F44, 0xFFC9CDD1), Preset("Orange", 0xFFB85E14, 0xFFF0A04B),  // the logo orange; deeper in light mode for white text
)

/** The preset whose accent matches the current one, or null for custom colors. */
@Composable
internal fun presetName(): String? {
    val p = LocalPalette.current
    return PRESETS.firstOrNull { Color(if (p.isDark) it.dark else it.light) == p.accent }?.name
}

@Composable
fun SettingsScreen(repo: Repo, theme: ThemeStore, drive: DriveBackup, onColors: () -> Unit, onBackup: () -> Unit) {
    val p = LocalPalette.current
    val uri = LocalUriHandler.current
    val scope = rememberCoroutineScope()
    var key by remember { mutableStateOf(repo.apiKey) }
    var syncing by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf<String?>(null) }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Panel(Modifier.fillMaxWidth(), PaddingValues(horizontal = 14.dp, vertical = 2.dp)) {
            NavRow("Colors & theme", "${presetName() ?: "Custom"} · ${theme.mode.name}", onColors) {
                Box(Modifier.size(28.dp).clip(CircleShape).background(p.accent).border(1.dp, p.line, CircleShape))
            }
            RowDivider()
            NavRow("Backup & data", backupLine(drive, repo), onBackup,
                trail = { if (drive.email != null && !drive.needsBackup) Text("✓", color = p.ok, fontWeight = FontWeight.Bold, fontSize = 16.sp) }) {
                Box(Modifier.size(28.dp).clip(CircleShape).background(p.accentSoft), contentAlignment = Alignment.Center) {
                    Icon(CLOUD, null, tint = p.accent, modifier = Modifier.size(18.dp))
                }
            }
        }

        Panel(Modifier.fillMaxWidth(), PaddingValues(start = 16.dp, end = 12.dp, top = 4.dp, bottom = 4.dp)) {
            ToggleRow("Include defunct coasters", repo.filters.includeDefunct) { repo.updateFilters(repo.filters.copy(includeDefunct = it)) }
            ToggleRow("Include kiddie coasters", repo.filters.includeKiddie) { repo.updateFilters(repo.filters.copy(includeKiddie = it)) }
            ToggleRow("Include carnival coasters", repo.filters.includeCarnival) { repo.updateFilters(repo.filters.copy(includeCarnival = it)) }
            ToggleRow("Include mountain coasters", repo.filters.includeAlpine) { repo.updateFilters(repo.filters.copy(includeAlpine = it)) }
            ToggleRow("US parks only", repo.filters.usOnly) { repo.updateFilters(repo.filters.copy(usOnly = it)) }
            ToggleRow("Distances in kilometers", repo.metric) { repo.updateMetric(it) }
        }

        Panel(Modifier.fillMaxWidth()) {
            // Key and button share a row: the key is set once, so it doesn't need a full-width button under it.
            FieldLabel("Coasterpedia API key")
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                FieldBox("", key, { key = it; repo.setKey(it) }, if (key.isBlank()) FieldStatus.Empty else FieldStatus.Typing,
                    null, Modifier.weight(1f), placeholder = "cpk_...")
                PrimaryButton(if (syncing) "Updating" else "Update data", Modifier.width(128.dp), enabled = key.isNotBlank() && !syncing, compact = true) {
                    syncing = true
                    scope.launch {
                        status = runCatching { repo.sync { status = it } }.fold(
                            { "Updated: ${it.added} new, ${it.updated} changed" },
                            { (it as? Sync.Failed)?.message ?: "Update failed: ${it.message ?: "no connection"}" },
                        )
                        syncing = false
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("Get a free key", color = p.accent, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.clickable { uri.openUri("https://developer.coasterpedia.net") }.padding(vertical = 4.dp))
                Spacer(Modifier.width(12.dp))
                Text(status ?: "Data as of ${repo.dataAsOf ?: "unknown"}", color = p.muted, fontSize = 13.sp,
                    textAlign = TextAlign.End, modifier = Modifier.weight(1f))
            }
        }

        Text("Data: Coasterpedia contributors (CC BY-SA 4.0), OpenStreetMap contributors (ODbL), Wikidata, Natural Earth.", color = p.muted, fontSize = 11.sp, lineHeight = 15.sp)
    }
}

/** "Google Drive: backed up today, 2:41 PM" and friends. */
internal fun backupLine(drive: DriveBackup, repo: Repo): String = when {
    drive.email == null -> "Google Drive backup is off"
    drive.backupAt == 0L -> "Not backed up yet"
    drive.needsBackup -> "Changes not backed up yet"
    else -> "Backed up ${whenText(drive.backupAt)}"
}

internal fun whenText(ms: Long, now: Long = System.currentTimeMillis()): String {
    val z = java.time.ZoneId.systemDefault()
    val t = java.time.Instant.ofEpochMilli(ms).atZone(z)
    val days = java.time.temporal.ChronoUnit.DAYS.between(t.toLocalDate(), java.time.Instant.ofEpochMilli(now).atZone(z).toLocalDate())
    val time = t.format(java.time.format.DateTimeFormatter.ofPattern("h:mm a"))
    return when (days) { 0L -> "today, $time"; 1L -> "yesterday, $time"; else -> t.format(java.time.format.DateTimeFormatter.ofPattern("MMM d, h:mm a")) }
}

internal val CLOUD = outlineIcon("cloud", "M7 18a4.5 4.5 0 0 1-.6-8.96A6 6 0 0 1 18 10a4 4 0 0 1 0 8z")

@Composable
private fun NavRow(title: String, sub: String, onClick: () -> Unit, trail: @Composable () -> Unit = {}, lead: @Composable () -> Unit) {
    val p = LocalPalette.current
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        lead()
        Column(Modifier.weight(1f)) {
            Text(title, color = p.ink, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            Text(sub, color = p.muted, fontSize = 12.sp)
        }
        trail()
        Text("›", color = p.muted, fontSize = 22.sp)
    }
}

/** Colors & Theme: the four presets, Custom (the full editor) and System/Light/Dark. */
@Composable
fun ColorsScreen(theme: ThemeStore, onCustom: () -> Unit) {
    val p = LocalPalette.current
    val current = presetName()
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Panel(Modifier.fillMaxWidth()) {
            FieldLabel("Colors")
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                PRESETS.forEach { pr ->
                    val c = Color(if (p.isDark) pr.dark else pr.light)
                    Swatch(pr.name, current == pr.name, { theme.set(false, Std.Accent, Color(pr.light)); theme.set(true, Std.Accent, Color(pr.dark)) }) {
                        Box(Modifier.fillMaxSize().clip(CircleShape).background(c))
                    }
                }
                Swatch("Custom", current == null, onCustom) {
                    Box(Modifier.fillMaxSize().clip(CircleShape).border(1.5.dp, p.muted, CircleShape), contentAlignment = Alignment.Center) {
                        Text("+", color = p.ink, fontSize = 22.sp)
                    }
                }
            }
        }
        Panel(Modifier.fillMaxWidth()) {
            FieldLabel("Appearance")
            Segmented(listOf("System", "Light", "Dark"), theme.mode.ordinal) { theme.changeMode(ThemeMode.entries[it]) }
        }
    }
}

@Composable
private fun Swatch(name: String, on: Boolean, onClick: () -> Unit, dot: @Composable () -> Unit) {
    val p = LocalPalette.current
    Column(Modifier.clickable(onClick = onClick).semantics { contentDescription = "$name colors" }, horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(56.dp).border(if (on) 3.dp else 0.dp, if (on) p.ink else Color.Transparent, CircleShape).padding(5.dp)) { dot() }
        Spacer(Modifier.height(4.dp))
        Text(name, color = p.ink, fontSize = 12.sp)
    }
}

@Composable
private fun ToggleRow(label: String, on: Boolean, onChange: (Boolean) -> Unit) {
    val p = LocalPalette.current
    Row(Modifier.fillMaxWidth().heightIn(min = 44.dp).clickable { onChange(!on) }, verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = p.ink, fontSize = 15.sp, modifier = Modifier.weight(1f))
        Switch(on, onCheckedChange = onChange, colors = SwitchDefaults.colors(
            checkedThumbColor = p.btnInk, checkedTrackColor = p.accent, uncheckedThumbColor = p.muted, uncheckedTrackColor = p.surface,
            uncheckedBorderColor = p.line))
    }
}

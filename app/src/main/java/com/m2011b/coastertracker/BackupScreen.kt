package com.m2011b.coastertracker

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.m2011b.coastertracker.data.Repo
import com.m2011b.kit.theme.LocalPalette
import com.m2011b.kit.ui.*
import kotlinx.coroutines.launch

/** Backup & Data: Google Drive backup and restore, plus CSV import and export. */
@Composable
fun BackupScreen(repo: Repo, drive: DriveBackup) {
    val p = LocalPalette.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var message by remember { mutableStateOf<Pair<String, String>?>(null) }
    var found by remember { mutableStateOf<DriveBackup.Found?>(null) }    // offered right after signing in
    var confirm by remember { mutableStateOf<DriveBackup.Found?>(null) }  // the Restore button
    fun fail(t: Throwable, what: String) { message = what to (t.message ?: "No connection.") }

    fun finish(r: com.google.android.gms.auth.api.identity.AuthorizationResult) = scope.launch {
        runCatching { drive.finishSignIn(r) }.onSuccess { found = it }.onFailure { fail(it, "Sign-in failed") }
    }
    val consent = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { res ->
        if (res.resultCode == Activity.RESULT_OK) runCatching { drive.resultFrom(res.data) }.onSuccess { finish(it) }.onFailure { fail(it, "Sign-in failed") }
    }
    fun signIn() = scope.launch {
        runCatching { drive.startSignIn() }.onSuccess { r ->
            if (r.hasResolution()) consent.launch(IntentSenderRequest.Builder(r.pendingIntent!!.intentSender).build()) else finish(r)
        }.onFailure { fail(it, "Sign-in failed") }
    }

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
            val email = drive.email
            Row(verticalAlignment = Alignment.CenterVertically) {
                FieldLabel("Google Drive", Modifier.weight(1f))
                if (email != null && !drive.needsBackup && drive.backupAt > 0) Text("✓ Backed up", color = p.ok, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            }
            Spacer(Modifier.height(8.dp))
            if (email == null) {
                Text("Keeps your rides, wishlist, edits, settings and colors in a private Lift Hill folder in your Google Drive. Only this app can see it.",
                    color = p.muted, fontSize = 13.sp, lineHeight = 19.sp)
                Spacer(Modifier.height(14.dp))
                GoogleButton(enabled = !drive.busy) { signIn() }
            } else {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Box(Modifier.size(34.dp).clip(CircleShape).background(p.accentSoft), contentAlignment = Alignment.Center) {
                        Text(email.take(1).uppercase(), color = p.accent, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    }
                    Column(Modifier.weight(1f)) {
                        Text(email, color = p.ink, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, softWrap = false,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                        Text(when {
                            drive.busy -> "Working..."
                            drive.backupAt == 0L -> "Not backed up yet"
                            else -> "Backed up ${whenText(drive.backupAt)}"
                        }, color = p.muted, fontSize = 12.sp)
                    }
                    Text("Sign out", color = p.accent, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.clickable(enabled = !drive.busy) { scope.launch { drive.signOut() } }.padding(6.dp))
                }
                Spacer(Modifier.height(6.dp))
                Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable { drive.updateAuto(!drive.auto) }, verticalAlignment = Alignment.CenterVertically) {
                    Text("Back up automatically", color = p.ink, fontSize = 15.sp, modifier = Modifier.weight(1f))
                    Switch(drive.auto, onCheckedChange = { drive.updateAuto(it) }, colors = SwitchDefaults.colors(
                        checkedThumbColor = p.btnInk, checkedTrackColor = p.accent, uncheckedThumbColor = p.muted, uncheckedTrackColor = p.surface,
                        uncheckedBorderColor = p.line))
                }
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    PrimaryButton("BACK UP NOW", Modifier.weight(1.8f), enabled = !drive.busy) {
                        scope.launch { runCatching { drive.backUpNow() }.onFailure { fail(it, "Backup failed") } }
                    }
                    SecondaryButton("Restore", Modifier.weight(1f), enabled = !drive.busy) {
                        scope.launch {
                            runCatching { drive.fetch() }.onSuccess { f ->
                                if (f == null) message = "No backup yet" to "There's no Lift Hill backup in this Google account yet." else confirm = f
                            }.onFailure { fail(it, "Restore failed") }
                        }
                    }
                }
            }
        }

        Panel(Modifier.fillMaxWidth()) {
            FieldLabel("Spreadsheet")
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SecondaryButton("Import CSV", Modifier.weight(1f)) { importer.launch(arrayOf("text/*", "application/csv", "application/octet-stream")) }
                SecondaryButton("Export CSV", Modifier.weight(1f)) { exporter.launch("coaster-rides.csv") }
            }
            Spacer(Modifier.height(8.dp))
            Text("Rides only, as coaster, park and date. Import adds to what's here.", color = p.muted, fontSize = 12.sp)
        }
    }

    fun restore(f: DriveBackup.Found) {
        runCatching { drive.restore(f) }.onSuccess { n ->
            message = "Restored" to (if (n == 0) "Nothing new: every ride in the backup is already on this phone." else "$n ${if (n == 1) "ride" else "rides"} added from the backup.") +
                " Stars, edits, settings and colors are restored too."
        }.onFailure { fail(it, "Restore failed") }
    }
    found?.let { f ->
        KitDialog("Found a backup", onDismiss = { found = null }, confirm = "RESTORE" to { found = null; restore(f) },
            dismiss = listOf("Not now" to { found = null })) {
            Text("${drive.email} has a Lift Hill backup from ${whenText(f.savedAt)} with ${f.rides} rides. Restore it to this phone?\n\n" +
                "Rides already on this phone are kept. If you skip this, the next backup replaces it.")
        }
    }
    confirm?.let { f ->
        KitDialog("Restore from Google Drive?", onDismiss = { confirm = null }, confirm = "RESTORE" to { confirm = null; restore(f) },
            dismiss = listOf("Cancel" to { confirm = null })) {
            Text("Backup from ${whenText(f.savedAt)}: ${f.rides} rides.\n\nRides on this phone are kept and the backup's are added. " +
                "Stars and edits are combined; settings and colors come from the backup.")
        }
    }
    message?.let { (t, b) -> KitDialog(t, onDismiss = { message = null }, confirm = "OK" to { message = null }) { Text(b) } }
}

/** Google's standard "Sign in with Google" button (light theme). Its colors are Google's brand rules, not app tokens. */
@Composable
fun GoogleButton(enabled: Boolean = true, onClick: () -> Unit) {
    val shape = RoundedCornerShape(50)
    Row(Modifier.fillMaxWidth().height(44.dp).clip(shape).background(Color.White).border(1.dp, Color(0xFF747775), shape)
        .clickable(enabled = enabled, role = Role.Button, onClick = onClick),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
        Image(G_LOGO, null, Modifier.size(18.dp))
        Spacer(Modifier.width(10.dp))
        Text("Sign in with Google", color = Color(0xFF1F1F1F), fontSize = 14.sp, fontWeight = FontWeight.Medium)
    }
}

private val G_LOGO: ImageVector = ImageVector.Builder("g", 18.dp, 18.dp, 48f, 48f).apply {
    listOf(
        0xFFEA4335 to "M24 9.5c3.54 0 6.71 1.22 9.21 3.6l6.85-6.85C35.9 2.38 30.47 0 24 0 14.62 0 6.51 5.38 2.56 13.22l7.98 6.19C12.43 13.72 17.74 9.5 24 9.5z",
        0xFF4285F4 to "M46.98 24.55c0-1.57-.15-3.09-.38-4.55H24v9.02h12.94c-.58 2.96-2.26 5.48-4.78 7.18l7.73 6c4.51-4.18 7.09-10.36 7.09-17.65z",
        0xFFFBBC05 to "M10.53 28.59c-.48-1.45-.76-2.99-.76-4.59s.27-3.14.76-4.59l-7.98-6.19C.92 16.46 0 20.12 0 24c0 3.88.92 7.54 2.56 10.78l7.97-6.19z",
        0xFF34A853 to "M24 48c6.48 0 11.93-2.13 15.89-5.81l-7.73-6c-2.15 1.45-4.92 2.3-8.16 2.3-6.26 0-11.57-4.22-13.47-9.91l-7.98 6.19C6.51 42.62 14.62 48 24 48z",
    ).forEach { (c, d) -> addPath(PathParser().parsePathString(d).toNodes(), fill = SolidColor(Color(c))) }
}.build()

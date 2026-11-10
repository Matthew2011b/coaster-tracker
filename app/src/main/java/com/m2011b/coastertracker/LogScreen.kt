package com.m2011b.coastertracker

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.m2011b.coastertracker.data.Coaster
import com.m2011b.coastertracker.data.Life
import com.m2011b.coastertracker.data.Park
import com.m2011b.coastertracker.data.Repo
import com.m2011b.coastertracker.data.Rules
import com.m2011b.kit.theme.LocalPalette
import com.m2011b.kit.ui.*

@Composable
fun LogScreen(repo: Repo) {
    val p = LocalPalette.current
    val stats = repo.stats
    val here = LocalHere.current

    // Parks worth picking: anywhere with a coaster you could log under the current filters, or already logged.
    val parkIds = stats.loggableParkIds
    val parks = remember(parkIds, here) {
        val list = parkIds.mapNotNull { repo.park(it) }
        if (here != null) list.sortedBy { it.distanceFrom(here) ?: Double.MAX_VALUE } else list.sortedBy { it.name.lowercase() }
    }
    var parkId by remember { mutableStateOf<Long?>(null) }
    var pickedByHand by remember { mutableStateOf(false) }
    // Auto-pick until the user chooses: a park within 10 miles, else the last park used, else the nearest.
    LaunchedEffect(here, parks) {
        if (!pickedByHand || parkId !in parkIds) {
            val nearest = here?.let { parks.firstOrNull() }
            val close = nearest?.takeIf { (it.distanceFrom(here) ?: 999.0) < 10 }
            parkId = close?.id ?: repo.lastParkId?.takeIf { it in parkIds } ?: nearest?.id ?: parks.firstOrNull()?.id
        }
    }
    val park = repo.park(parkId)
    val atPark = remember(stats, parkId) { parkId?.let { stats.atPark(it) }.orEmpty() }
    var selected by remember(parkId) { mutableStateOf<Long?>(null) }
    val selCoaster = atPark.firstOrNull { it.id == selected } ?: atPark.firstOrNull { !stats.ridden(it) }

    var pickPark by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<Coaster?>(null) }
    var adding by remember { mutableStateOf(false) }
    var justLogged by remember { mutableStateOf<String?>(null) }

    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Column {
            FieldLabel("Park")
            PickerBox(park?.name ?: "Pick a park", park?.let { parkSub(it, here) }) { pickPark = true }
        }
        SelectBox(
            "Coaster", atPark.map { Choice.Pick(it) } + Choice.Add, selCoaster?.let { Choice.Pick(it) },
            { if (it is Choice.Pick) it.c.name else "+ Add a coaster not listed" },
            { if (it is Choice.Pick) selected = it.c.id else adding = true },
            placeholder = if (atPark.isEmpty()) "No coasters here" else "All ridden here",
        )
        val already = selCoaster != null && stats.ridden(selCoaster)
        PrimaryButton(if (already) "ALREADY LOGGED" else "LOG", enabled = selCoaster != null && !already) {
            selCoaster?.let { repo.log(it.id); park?.let { pk -> repo.setLastPark(pk.id) }; justLogged = it.name }
        }
        SectionHead(justLogged?.let { "Logged $it" } ?: "At this park", "${atPark.count { stats.ridden(it) }} / ${atPark.size} ridden")
        ListBox(Modifier.weight(1f)) {
            items(atPark, key = { it.id }) { c ->
                CoasterRow(c, stats.ridden(c), stats.isNewModel(c), onClick = { selected = c.id }, onLongClick = { editing = c })
                RowDivider()
            }
        }
        Text("Long-press a ride to edit or remove it.", color = p.muted, fontSize = 12.sp, modifier = Modifier.padding(start = 4.dp))
    }

    if (pickPark) ParkPicker(parks, here, onDismiss = { pickPark = false }) { parkId = it.id; pickedByHand = true; repo.setLastPark(it.id); pickPark = false }
    editing?.let { c -> EditCoasterDialog(repo, c) { editing = null } }
    if (adding) AddCoasterDialog(repo, park) { id -> adding = false; if (id != null) selected = id }
}

private sealed interface Choice {
    data class Pick(val c: Coaster) : Choice
    data object Add : Choice
}

fun Park.distanceFrom(here: LatLon?): Double? =
    if (here == null || lat == null || lon == null) null else Rules.miles(here.first, here.second, lat, lon)

fun parkSub(pk: Park, here: LatLon?): String =
    listOfNotNull(pk.place, pk.distanceFrom(here)?.let { miles(it) }).joinToString(" · ")

/** Box that looks like the kit's SelectBox but opens a searchable dialog (for long lists). */
@Composable
fun PickerBox(title: String, sub: String?, onClick: () -> Unit) {
    val p = LocalPalette.current
    Row(
        Modifier.fillMaxWidth().clip(BoxShape).background(p.surface).border(2.dp, p.line, BoxShape).clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, color = p.ink, fontSize = 19.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (!sub.isNullOrBlank()) Text(sub, color = p.muted, fontSize = 12.sp)
        }
        Text("▼", color = p.muted, fontSize = 14.sp)
    }
}

@Composable
fun ParkPicker(parks: List<Park>, here: LatLon?, onDismiss: () -> Unit, onPick: (Park) -> Unit) {
    var q by remember { mutableStateOf("") }
    val shown = remember(q, parks) {
        val n = Rules.norm(q)
        (if (n.isEmpty()) parks else parks.filter { Rules.norm(it.name).contains(n) }).take(200)
    }
    FormDialog("Pick a park", onDismiss = onDismiss, confirm = "Close" to onDismiss) {
        FieldBox("Search", q, { q = it }, if (q.isBlank()) FieldStatus.Empty else FieldStatus.Typing,
            if (here != null) "Nearest first" else "A to Z", placeholder = "Park name", capitalize = true)
        Spacer(Modifier.height(8.dp))
        ListBox(Modifier.heightIn(max = 340.dp)) {
            items(shown, key = { it.id }) { pk ->
                ValueRow(pk.name, pk.place, pk.distanceFrom(here)?.let { miles(it) } ?: "", onClick = { onPick(pk) })
                RowDivider()
            }
        }
    }
}

@Composable
fun EditCoasterDialog(repo: Repo, c: Coaster, onDone: () -> Unit) {
    var make by remember { mutableStateOf(c.make ?: "") }
    var model by remember { mutableStateOf(c.model ?: "") }
    val ridden = repo.stats.ridden(c)
    FormDialog(
        c.name, onDismiss = onDone,
        confirm = "Save" to { repo.edit(c.id, make, model); onDone() },
        dismiss = listOf(
            (if (ridden) "Remove ride" else "Mark ridden") to { if (ridden) repo.unlog(c.id) else repo.log(c.id); onDone() },
            "Cancel" to onDone,
        ),
    ) {
        FieldBox("Manufacturer", make, { make = it }, if (make.isBlank()) FieldStatus.Empty else FieldStatus.Typing,
            "Leave blank if unknown", capitalize = true)
        Spacer(Modifier.height(4.dp))
        FieldBox("Model", model, { model = it }, if (model.isBlank()) FieldStatus.Empty else FieldStatus.Typing,
            "Blank means a one-off custom build", capitalize = true)
    }
}

@Composable
fun AddCoasterDialog(repo: Repo, park: Park?, onDone: (Long?) -> Unit) {
    var name by remember { mutableStateOf("") }
    var make by remember { mutableStateOf("") }
    var model by remember { mutableStateOf("") }
    FormDialog(
        "Add a coaster", onDismiss = { onDone(null) },
        confirm = "Add" to { if (name.isNotBlank()) onDone(repo.addCustom(name, park?.id, make, model)) },
        dismiss = listOf("Cancel" to { onDone(null) }),
    ) {
        FieldBox("Name", name, { name = it }, if (name.isBlank()) FieldStatus.Empty else FieldStatus.Valid,
            park?.let { "At ${it.name}" } ?: "No park", capitalize = true)
        Spacer(Modifier.height(4.dp))
        FieldBox("Manufacturer", make, { make = it }, if (make.isBlank()) FieldStatus.Empty else FieldStatus.Typing, "Optional", capitalize = true)
        Spacer(Modifier.height(4.dp))
        FieldBox("Model", model, { model = it }, if (model.isBlank()) FieldStatus.Empty else FieldStatus.Typing, "Optional", capitalize = true)
    }
}

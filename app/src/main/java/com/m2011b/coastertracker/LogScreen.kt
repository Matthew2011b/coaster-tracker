package com.m2011b.coastertracker

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.ui.unit.Dp
import kotlinx.coroutines.delay
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
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
fun LogScreen(repo: Repo, quick: Boolean = repo.quickLog) {
    val p = LocalPalette.current
    val stats = repo.stats
    val here = LocalHere.current

    // Parks worth picking: anywhere with a coaster you could log under the current filters, or already logged.
    val parkIds = stats.loggableParkIds
    val parks = remember(parkIds, here) {
        val list = parkIds.mapNotNull { repo.park(it) }
        if (here != null) list.sortedBy { it.distanceFrom(here) ?: Double.MAX_VALUE } else list.sortedBy { it.name.lowercase() }
    }
    var parkId by rememberSaveable { mutableStateOf<Long?>(null) }
    var pickedByHand by rememberSaveable { mutableStateOf(false) }
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

    if (quick) {
        QuickLog(repo, park, atPark, here, onPickPark = { pickPark = true }, onEdit = { editing = it }, onAdd = { adding = true })
    } else Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
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

/**
 * Quick log: every coaster at the park as a tile (name, manufacturer, model), sized so even the
 * biggest parks fit without scrolling. Tap an unridden tile to log it, with an Undo bar; long-press
 * to edit.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun QuickLog(repo: Repo, park: Park?, atPark: List<Coaster>, here: LatLon?, onPickPark: () -> Unit, onEdit: (Coaster) -> Unit, onAdd: () -> Unit) {
    val p = LocalPalette.current
    val stats = repo.stats
    var undo by remember { mutableStateOf<Coaster?>(null) }
    LaunchedEffect(undo) { if (undo != null) { delay(4500); undo = null } }
    // Same order as the picker list, but fixed while you tap so tiles don't jump around.
    val order = remember(park?.id, atPark.size) { atPark.sortedBy { it.name.lowercase() }.map { it.id } }
    val tiles = order.mapNotNull { id -> atPark.firstOrNull { it.id == id } }

    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            Modifier.fillMaxWidth().height(46.dp).clip(BoxShape).background(p.surface).border(2.dp, p.line, BoxShape)
                .clickable(onClick = onPickPark).padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(park?.name ?: "Pick a park", color = p.ink, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, maxLines = 1,
                overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
            park?.let { Text("  " + parkSub(it, here), color = p.muted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false)) }
            Spacer(Modifier.weight(0.01f))
            Mono("${atPark.count { stats.ridden(it) }} / ${atPark.size}", modifier = Modifier.padding(start = 8.dp))
            Text("  ▼", color = p.muted, fontSize = 12.sp)
        }
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
            val gap = 6.dp
            val cells = tiles.size + 1  // plus the Add tile
            val rows = (cells + 1) / 2
            val tileH = ((maxHeight - gap * (rows - 1)) / rows).coerceIn(60.dp, 68.dp)
            LazyVerticalGrid(GridCells.Fixed(2), horizontalArrangement = Arrangement.spacedBy(gap), verticalArrangement = Arrangement.spacedBy(gap),
                modifier = Modifier.fillMaxSize()) {
                items(tiles, key = { it.id }) { c ->
                    val ridden = stats.ridden(c)
                    QuickTile(c, ridden, stats.isNewModel(c), tileH,
                        onClick = { if (!ridden) { repo.log(c.id); park?.let { repo.setLastPark(it.id) }; undo = c } },
                        onLongClick = { onEdit(c) })
                }
                item(key = "add") {
                    Box(Modifier.height(tileH).clip(TileShape).border(1.dp, p.line, TileShape).clickable(onClick = onAdd), contentAlignment = Alignment.Center) {
                        Text("+ Add a coaster", color = p.muted, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
            undo?.let { c ->
                Row(
                    Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(bottom = 4.dp).clip(BoxShape).background(p.ink)
                        .padding(start = 14.dp), verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Logged ${c.name}", color = p.bg, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    Text("UNDO", color = p.headerTrack, fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.clickable { repo.unlog(c.id); undo = null }.padding(horizontal = 16.dp, vertical = 14.dp))
                }
            }
        }
    }
}

private val TileShape = RoundedCornerShape(9.dp)

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun QuickTile(c: Coaster, ridden: Boolean, newModel: Boolean, height: Dp, onClick: () -> Unit, onLongClick: () -> Unit) {
    val p = LocalPalette.current
    val bg = when { ridden -> p[AppTokens.OkSoft]; newModel -> p[AppTokens.NewBg]; else -> p.surface }
    val edge = when { ridden -> p.ok; newModel -> p[AppTokens.NewInk]; else -> p.line }
    Box(
        Modifier.height(height).clip(TileShape).background(bg).border(1.dp, edge, TileShape)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick).padding(horizontal = 9.dp, vertical = 5.dp),
    ) {
        Column(Modifier.align(Alignment.CenterStart).fillMaxWidth()) {
            Text(c.name, color = p.ink, fontSize = 13.5.sp, lineHeight = 16.sp, fontWeight = FontWeight.SemiBold, maxLines = 1,
                overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(end = if (newModel) 30.dp else 20.dp))
            Text(c.make ?: "Unknown maker", color = p.ink.copy(alpha = 0.8f), fontSize = 11.5.sp, lineHeight = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(c.model ?: "Custom", color = p.muted, fontSize = 11.5.sp, lineHeight = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Box(Modifier.align(Alignment.TopEnd)) {
            when {
                ridden -> Icon(Icons.Filled.Check, contentDescription = "Ridden", tint = p.ok, modifier = Modifier.size(16.dp))
                newModel -> Text("NEW", color = p[AppTokens.NewInk], fontSize = 9.5.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.6.sp)
                else -> Box(Modifier.padding(top = 1.dp).size(14.dp).border(2.dp, p.line, CircleShape))
            }
        }
    }
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

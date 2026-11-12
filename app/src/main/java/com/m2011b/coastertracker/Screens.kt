package com.m2011b.coastertracker

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.m2011b.coastertracker.data.Coaster
import com.m2011b.coastertracker.data.Repo
import com.m2011b.coastertracker.data.SortBy
import com.m2011b.kit.theme.LocalPalette
import com.m2011b.kit.ui.*
import java.util.Locale

// ---------- Stats ----------

/** Stats for everything, or (with [make]) for one manufacturer's models, opened from the Manufacturers view. */
@Composable
fun StatsScreen(repo: Repo, make: String?, onModel: (String) -> Unit, onMake: (String) -> Unit, showMap: Boolean = false, onPark: (Long) -> Unit = {}) {
    val p = LocalPalette.current
    val s = repo.stats
    var view by rememberSaveable { mutableIntStateOf(0) }
    var sort by rememberSaveable { mutableStateOf(SortBy.MostComplete) }
    var sortOpen by remember { mutableStateOf(false) }
    val showModels = make != null || view == 0
    val models = remember(s, sort, make) { s.sortedModels(sort).filter { make == null || it.make == make } }

    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (make == null) Segmented(listOf("Models", "Manufacturers"), view) { view = it }
        if (make != null && showMap) {
            InstallMap(repo, onPark, Modifier.weight(1f)) { it.make == make }
            return@Column
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (make != null) {
                val mk = s.makes.firstOrNull { it.make == make }
                StatTile("CREDITS", "${mk?.coastersRidden ?: 0}", Modifier.weight(1f))
                StatTile("MODELS RIDDEN", "${mk?.modelsRidden ?: 0}", Modifier.weight(1f))
                StatTile("NEVER RIDDEN", "${(mk?.models ?: 0) - (mk?.modelsRidden ?: 0)}", Modifier.weight(1f))
            } else if (view == 0) {
                StatTile("CREDITS", "${s.credits}", Modifier.weight(1f))
                StatTile("MODELS RIDDEN", "${s.modelsRidden}", Modifier.weight(1f))
                StatTile("NEVER RIDDEN", "${s.modelsNever}", Modifier.weight(1f))
            } else {
                StatTile("MAKERS RIDDEN", "${s.makes.count { it.modelsRidden > 0 }}", Modifier.weight(1f))
                StatTile("MANUFACTURERS", "${s.makes.size}", Modifier.weight(1f))
                StatTile("CREDITS", "${s.credits}", Modifier.weight(1f))
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            SectionHead(if (showModels) "${models.size} models" else "${s.makes.size} manufacturers", modifier = Modifier.weight(1f))
            Box {
                Row(
                    Modifier.clip(CircleShape).border(1.dp, p.line, CircleShape).background(p.surface).clickable { sortOpen = true }
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                ) { Text("⇅  ${sort.label}", color = p.ink, fontSize = 14.sp) }
                DropdownMenu(sortOpen, onDismissRequest = { sortOpen = false }, containerColor = p.surface) {
                    SortBy.entries.forEach { o ->
                        DropdownMenuItem(text = { Text(o.label, color = p.ink, fontWeight = if (o == sort) FontWeight.SemiBold else FontWeight.Normal) },
                            onClick = { sort = o; sortOpen = false })
                    }
                }
            }
        }
        ListBox(Modifier.weight(1f)) {
            if (showModels) {
                items(models, key = { it.key }) { m ->
                    ProgressRow(m.model, if (make != null) "${m.total} coasters" else m.make, m.ridden, m.total) { onModel(m.key) }
                    RowDivider()
                }
            } else {
                items(s.sortedMakes(sort), key = { it.make }) { mk ->
                    ProgressRow(mk.make, "${mk.coastersRidden} of ${mk.coasters} coasters ridden", mk.modelsRidden, mk.models, unit = "models") {
                        onMake(mk.make)
                    }
                    RowDivider()
                }
            }
        }
    }
}

@Composable
private fun ProgressRow(title: String, sub: String, done: Int, total: Int, unit: String? = null, onClick: () -> Unit) {
    Column(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TwoLine(title, sub, Modifier.weight(1f))
            Spacer(Modifier.width(8.dp))
            Mono("$done / $total" + (unit?.let { " $it" } ?: ""))
        }
        ProgressBar(if (total == 0) 0.0 else done.toDouble() / total, done >= total && total > 0)
    }
}

// ---------- Model detail ----------

@Composable
fun ModelDetailScreen(repo: Repo, key: String, showMap: Boolean = false, onPark: (Long) -> Unit = {}) {
    val s = repo.stats
    val here = LocalHere.current
    val m = s.model(key)
    val (make, model) = key.split("|", limit = 2).let { it[0] to it.getOrElse(1) { "" } }
    val rows = remember(s, here, key) { s.installations(key, here) }
    val ridden = m?.ridden ?: 0
    val total = m?.total ?: rows.size

    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Panel(Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TwoLine(model, make, Modifier.weight(1f))
                WishStar(repo.wishes.model(key), "$make $model", size = 24) { repo.toggleWishModel(key) }
            }
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                Mono("$ridden / $total", size = 22, bold = true, muted = false, modifier = Modifier.weight(1f))
                val pct = if (total == 0) 0 else (100 * ridden / total)
                Text("$pct% · ${total - ridden} to go", color = LocalPalette.current.muted, fontSize = 13.sp)
            }
            Spacer(Modifier.height(8.dp))
            ProgressBar(if (total == 0) 0.0 else ridden.toDouble() / total, ridden >= total && total > 0, height = 10)
        }
        if (showMap) {
            InstallMap(repo, onPark, Modifier.weight(1f)) { it.modelKey == key }
            return@Column
        }
        SectionHead("Installations", if (here != null) "Ridden first, then nearest" else "Ridden first")
        ListBox(Modifier.weight(1f)) {
            items(rows) { (c, d) ->  // no keys: the order changes once location arrives
                Row(Modifier.fillMaxWidth().padding(start = 14.dp, end = 4.dp, top = 4.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    TwoLine(c.name, parkLine(c, repo.park(c.parkId)), Modifier.weight(1f))
                    Spacer(Modifier.width(8.dp))
                    if (s.ridden(c)) RiddenMark() else Mono(miles(d).ifEmpty { c.country ?: "" })
                    WishStar(repo.wishes.coaster(c.id), c.name) { repo.toggleWishCoaster(c.id) }
                }
                RowDivider()
            }
        }
    }
}

/** Map of the parks with installations matching [match]: pins count the unridden ones, green dots are done. */
@Composable
fun InstallMap(repo: Repo, onPark: (Long) -> Unit, modifier: Modifier, match: (com.m2011b.coastertracker.data.Coaster) -> Boolean) {
    val s = repo.stats
    val all = remember(s) { s.installParks(match) }
    ParksMap(all.filter { it.unridden > 0 }, all.filter { it.unridden == 0 }, byModels = false, onPark = onPark,
        modifier = modifier.fillMaxWidth(), wished = remember(s, repo.wishes) { s.wishedParkIds(repo.wishes) }, fitAll = true,
        cardSub = { r -> if (r.unridden == 0) "All ridden" else if (r.ridden == 0) "${r.unridden} to ride" else "${r.ridden} ridden · ${r.unridden} to ride" })
}

private fun parkLine(c: Coaster, park: com.m2011b.coastertracker.data.Park?) =
    listOfNotNull(c.parkName, park?.place ?: c.country).joinToString(" · ")

// ---------- Parks ----------

@Composable
fun ParksScreen(repo: Repo, showMap: Boolean = false, onPark: (Long) -> Unit) {
    val s = repo.stats
    var byModels by rememberSaveable { mutableIntStateOf(0) }
    var region by rememberSaveable { mutableIntStateOf(0) }
    val usLocked = repo.filters.usOnly
    val rows = remember(s, byModels, region, usLocked) {
        val list = s.parkStats(usOnly = usLocked || region == 0)
        if (byModels == 0) list.sortedWith(compareByDescending<com.m2011b.coastertracker.data.ParkStat> { it.newModels }.thenByDescending { it.unridden })
        else list.sortedWith(compareByDescending<com.m2011b.coastertracker.data.ParkStat> { it.unridden }.thenByDescending { it.newModels })
    }.take(150)
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Segmented(listOf("Unridden models", "Unridden coasters"), byModels) { byModels = it }
        if (!usLocked) Segmented(listOf("United States", "Worldwide"), region) { region = it }
        if (showMap) {
            val all = remember(s, region, usLocked) { s.parkStats(usOnly = usLocked || region == 0, includeDone = true) }
            ParksMap(all.filter { it.unridden > 0 }, all.filter { it.unridden == 0 }, byModels == 0, onPark, Modifier.weight(1f).fillMaxWidth(),
                wished = remember(s, repo.wishes) { s.wishedParkIds(repo.wishes) })
            return@Column
        }
        ListBox(Modifier.weight(1f)) {
            itemsIndexed(rows, key = { _, r -> r.park.id }) { i, r ->
                val main = if (byModels == 0) r.newModels else r.unridden
                val sub = if (byModels == 0) "${r.unridden} unridden coasters" else "${r.newModels} new models"
                Row(Modifier.fillMaxWidth().clickable { onPark(r.park.id) }.padding(horizontal = 14.dp, vertical = 11.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Mono("${i + 1}", modifier = Modifier.width(30.dp))
                    TwoLine(r.park.name, listOfNotNull(r.park.place, sub).joinToString(" · "), Modifier.weight(1f))
                    Column(horizontalAlignment = Alignment.End) {
                        Mono("$main", size = 20, bold = true, muted = false)
                        Text(if (byModels == 0) "new models" else "unridden", color = LocalPalette.current.muted, fontSize = 11.sp)
                    }
                }
                RowDivider()
            }
        }
    }
}

@Composable
fun ParkDetailScreen(repo: Repo, parkId: Long, onModel: (String) -> Unit) {
    val s = repo.stats
    val park = repo.park(parkId)
    val here = LocalHere.current
    val list = remember(s, parkId) { s.atPark(parkId) }
    var editing by remember { mutableStateOf<Coaster?>(null) }
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Panel(Modifier.fillMaxWidth()) { TwoLine(park?.name ?: "Park", park?.let { parkSub(it, here) }) }
        SectionHead("Coasters", "${list.count { s.ridden(it) }} / ${list.size} ridden")
        ListBox(Modifier.weight(1f)) {
            items(list, key = { it.id }) { c ->
                CoasterRow(c, s.ridden(c), s.isNewModel(c), onClick = { c.modelKey?.let(onModel) }, onLongClick = { editing = c },
                    wished = repo.wishes.coaster(c.id), onWish = { repo.toggleWishCoaster(c.id) })
                RowDivider()
            }
        }
    }
    editing?.let { c -> EditCoasterDialog(repo, c) { editing = null } }
}

// ---------- Nearest ----------

@Composable
fun NearestScreen(repo: Repo, refreshLocation: () -> Unit, onModel: (String) -> Unit) {
    val p = LocalPalette.current
    val here = LocalHere.current
    val s = repo.stats
    val wishes = repo.wishes
    val rows = remember(s, here, wishes) {
        here?.let { h -> s.nearestNewModels(h).sortedBy { !wishes.model(it.stat.key) }.take(100) }.orEmpty()
    }
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(Modifier.fillMaxWidth().clip(BoxShape).background(p.surface).border(1.dp, p.line, BoxShape).padding(start = 14.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Text(if (here != null) "Using your location" else "Location not available", color = p.ink, fontSize = 14.sp, modifier = Modifier.weight(1f))
            TextButton(onClick = refreshLocation) { Text("Refresh", color = p.accent, fontWeight = FontWeight.SemiBold) }
        }
        SectionHead("Closest place to ride each model you haven't")
        ListBox(Modifier.weight(1f)) {
            items(rows, key = { it.stat.key }) { r ->
                ValueRow((if (wishes.model(r.stat.key)) "★ " else "") + "${shortMake(r.stat.make)} ${r.stat.model}", "${r.coaster.name} · ${r.park.name}",
                    miles(r.miles), onClick = { onModel(r.stat.key) })
                RowDivider()
            }
        }
    }
}


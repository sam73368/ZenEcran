package com.zenecran.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.zenecran.data.InstalledApp
import com.zenecran.data.Schedule
import com.zenecran.data.Store
import com.zenecran.data.fmtClock

private val dayLetters = listOf("L", "M", "M", "J", "V", "S", "D")
private val dayNames = listOf("lun", "mar", "mer", "jeu", "ven", "sam", "dim")

private fun daysSummary(days: Set<Int>): String = when {
    days.size == 7 -> "Tous les jours"
    days == setOf(1, 2, 3, 4, 5) -> "Lun – Ven"
    days == setOf(6, 7) -> "Week-end"
    days.isEmpty() -> "Aucun jour"
    else -> days.sorted().joinToString(" ") { dayNames[it - 1] }
}

private fun windowSummary(s: Schedule): String =
    if (s.startMin == s.endMin) "Toute la journée" else "${fmtClock(s.startMin)} → ${fmtClock(s.endMin)}"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SchedulesScreen(vm: MainViewModel, padding: PaddingValues) {
    val ui by vm.ui.collectAsState()
    val cfg by Store.config.collectAsState()
    var editing by remember { mutableStateOf<Schedule?>(null) }
    val locked = cfg.isStrict()
    val labelOf = remember(ui.installed) { ui.installed.associate { it.pkg to it.label } }

    LazyColumn(
        Modifier
            .fillMaxSize()
            .padding(padding),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Column {
                Text("Horaires", color = Z.textHi, fontSize = 30.sp, fontWeight = FontWeight.Bold)
                Text(
                    "Choisis des applis et une plage horaire : pendant ce temps, elles sont fermées dès que tu les ouvres.",
                    color = Z.textLo, fontSize = 14.sp,
                )
            }
        }
        if (!ui.hasAccessibility) item { AccessibilityNotice() }

        if (cfg.schedules.isEmpty()) {
            item {
                ZenCard {
                    Text("Modèles rapides", color = Z.textHi, fontWeight = FontWeight.SemiBold)
                    Text("Un tap, puis tu coches les applis.", color = Z.textLo, fontSize = 13.sp)
                    Spacer(Modifier.height(12.dp))
                    Row(
                        Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Button(
                            onClick = {
                                editing = Schedule(
                                    id = vm.newScheduleId(), name = "Nuit",
                                    startMin = 22 * 60, endMin = 7 * 60,
                                )
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Z.cardHi, contentColor = Z.textHi),
                        ) { Text("Nuit 22:00 → 07:00") }
                        Button(
                            onClick = {
                                editing = Schedule(
                                    id = vm.newScheduleId(), name = "École / Travail",
                                    days = setOf(1, 2, 3, 4, 5), startMin = 8 * 60, endMin = 16 * 60,
                                )
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Z.cardHi, contentColor = Z.textHi),
                        ) { Text("Cours 08:00 → 16:00") }
                    }
                }
            }
        }

        items(cfg.schedules, key = { it.id }) { s ->
            ZenCard(onClick = { editing = s }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(s.name, color = Z.textHi, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                        Text(windowSummary(s), color = Z.teal, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                        Text(daysSummary(s.days), color = Z.textLo, fontSize = 13.sp)
                    }
                    if (locked) {
                        Icon(Icons.Rounded.Lock, null, tint = Z.textLo, modifier = Modifier.padding(end = 10.dp))
                    }
                    Switch(
                        checked = s.enabled,
                        onCheckedChange = { vm.toggleSchedule(s.id, it) },
                        colors = zenSwitchColors(),
                    )
                }
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    s.pkgs.take(6).forEach { AppIcon(it, size = 32.dp) }
                    if (s.pkgs.size > 6) {
                        Text("  +${s.pkgs.size - 6}", color = Z.textLo, fontSize = 13.sp)
                    }
                    if (s.pkgs.isEmpty()) Text("Aucune appli cochée", color = Z.amber, fontSize = 13.sp)
                }
                if (s.pkgs.isNotEmpty()) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        s.pkgs.mapNotNull { labelOf[it] }.sorted().joinToString(", "),
                        color = Z.textLo, fontSize = 12.sp, maxLines = 2,
                    )
                }
            }
        }

        item {
            Button(
                onClick = { editing = Schedule(id = vm.newScheduleId(), name = "Nouvelle plage") },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Z.teal, contentColor = Z.onTeal),
            ) {
                Icon(Icons.Rounded.Add, null)
                Spacer(Modifier.width(8.dp))
                Text("Nouvelle plage horaire", fontWeight = FontWeight.SemiBold)
            }
        }
        item {
            Text(
                "Jamais bloqués : Téléphone, Réglages, l'écran d'accueil et Zen Écran, pour que tu ne sois jamais coincé.",
                color = Z.textLo, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 4.dp),
            )
        }
        item { Spacer(Modifier.height(8.dp)) }
    }

    editing?.let { initial ->
        val exists = cfg.schedules.any { it.id == initial.id }
        ModalBottomSheet(
            onDismissRequest = { editing = null },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = Z.card,
        ) {
            ScheduleEditor(
                initial = initial,
                exists = exists,
                locked = locked && exists,
                installed = ui.installed,
                onSave = { vm.saveSchedule(it); editing = null },
                onDelete = { vm.deleteSchedule(initial.id); editing = null },
                onWarn = { vm.say(it) },
            )
        }
    }
}

@Composable
private fun ScheduleEditor(
    initial: Schedule,
    exists: Boolean,
    locked: Boolean,
    installed: List<InstalledApp>,
    onSave: (Schedule) -> Unit,
    onDelete: () -> Unit,
    onWarn: (String) -> Unit,
) {
    var name by remember { mutableStateOf(initial.name) }
    var start by remember { mutableStateOf(initial.startMin) }
    var end by remember { mutableStateOf(initial.endMin) }
    var days by remember { mutableStateOf(initial.days) }
    var pkgs by remember { mutableStateOf(initial.pkgs) }
    var query by remember { mutableStateOf("") }
    var pickStart by remember { mutableStateOf(false) }
    var pickEnd by remember { mutableStateOf(false) }
    val shown = installed.filter { it.label.contains(query, ignoreCase = true) }

    LazyColumn(
        Modifier
            .fillMaxWidth()
            .fillMaxHeight(0.92f),
        contentPadding = PaddingValues(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Text(if (exists) "Modifier la plage" else "Nouvelle plage", color = Z.textHi, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        }
        if (locked) {
            item {
                Text(
                    "Mode engagement actif : cette plage ne peut pas être modifiée ni supprimée pour le moment.",
                    color = Z.amber, fontSize = 13.sp,
                )
            }
        }
        item {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Nom") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
            )
        }
        item {
            Column {
                Text("Interdit de … à …", color = Z.textLo, fontSize = 13.sp)
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    TimeBox(fmtClock(start)) { pickStart = true }
                    Text("→", color = Z.textLo, fontSize = 20.sp)
                    TimeBox(fmtClock(end)) { pickEnd = true }
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    when {
                        start == end -> "Début = fin : bloqué toute la journée."
                        start > end -> "Passe minuit : jusqu'au lendemain ${fmtClock(end)}."
                        else -> "Dans la même journée."
                    },
                    color = Z.textLo, fontSize = 12.sp,
                )
            }
        }
        item {
            Column {
                Text("Jours", color = Z.textLo, fontSize = 13.sp)
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    for (d in 1..7) {
                        val on = d in days
                        Box(
                            Modifier
                                .weight(1f)
                                .height(40.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (on) Z.teal else Z.cardHi)
                                .clickable { days = if (on) days - d else days + d },
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(dayLetters[d - 1], color = if (on) Z.onTeal else Z.textHi, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
                Row {
                    TextButton(onClick = { days = (1..7).toSet() }) { Text("Tous", fontSize = 12.sp) }
                    TextButton(onClick = { days = setOf(1, 2, 3, 4, 5) }) { Text("Semaine", fontSize = 12.sp) }
                    TextButton(onClick = { days = setOf(6, 7) }) { Text("Week-end", fontSize = 12.sp) }
                }
            }
        }
        item {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Applis interdites (${pkgs.size})", color = Z.textHi, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                    TextButton(onClick = { pkgs = pkgs + shown.map { it.pkg } }) { Text("Tout cocher", fontSize = 12.sp) }
                    TextButton(onClick = { pkgs = pkgs - shown.map { it.pkg }.toSet() }) { Text("Décocher", fontSize = 12.sp) }
                }
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    placeholder = { Text("Rechercher une appli") },
                    leadingIcon = { Icon(Icons.Rounded.Search, null) },
                    shape = RoundedCornerShape(16.dp),
                )
            }
        }
        items(shown, key = { it.pkg }) { app ->
            val checked = app.pkg in pkgs
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .clickable { pkgs = if (checked) pkgs - app.pkg else pkgs + app.pkg }
                    .padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AppIcon(app.pkg, size = 40.dp)
                Spacer(Modifier.width(14.dp))
                Text(app.label, color = Z.textHi, modifier = Modifier.weight(1f))
                Checkbox(
                    checked = checked,
                    onCheckedChange = { pkgs = if (it) pkgs + app.pkg else pkgs - app.pkg },
                    colors = CheckboxDefaults.colors(checkedColor = Z.teal, checkmarkColor = Z.onTeal),
                )
            }
        }
        item {
            Column {
                Spacer(Modifier.height(8.dp))
                Button(
                    enabled = !locked,
                    onClick = {
                        when {
                            pkgs.isEmpty() -> onWarn("Coche au moins une appli.")
                            days.isEmpty() -> onWarn("Choisis au moins un jour.")
                            else -> onSave(
                                initial.copy(
                                    name = name.trim().ifEmpty { "Plage" },
                                    startMin = start, endMin = end, days = days, pkgs = pkgs, enabled = true,
                                )
                            )
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Z.teal, contentColor = Z.onTeal),
                ) { Text("Enregistrer", fontWeight = FontWeight.SemiBold) }
                if (exists) {
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(
                        enabled = !locked,
                        onClick = onDelete,
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("Supprimer cette plage", color = if (locked) Z.textLo else Z.red) }
                }
                Spacer(Modifier.height(28.dp))
            }
        }
    }

    if (pickStart) TimeDialog(start, { pickStart = false }) { start = it; pickStart = false }
    if (pickEnd) TimeDialog(end, { pickEnd = false }) { end = it; pickEnd = false }
}

@Composable
private fun TimeBox(text: String, onClick: () -> Unit) {
    Box(
        Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(Z.cardHi)
            .clickable(onClick = onClick)
            .padding(horizontal = 22.dp, vertical = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = Z.textHi, fontSize = 26.sp, fontWeight = FontWeight.Bold)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimeDialog(initial: Int, onDismiss: () -> Unit, onConfirm: (Int) -> Unit) {
    val st = rememberTimePickerState(initialHour = initial / 60, initialMinute = initial % 60, is24Hour = true)
    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = RoundedCornerShape(28.dp), color = Z.card) {
            Column(Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                TimePicker(state = st)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text("Annuler") }
                    TextButton(onClick = { onConfirm(st.hour * 60 + st.minute) }) { Text("OK") }
                }
            }
        }
    }
}

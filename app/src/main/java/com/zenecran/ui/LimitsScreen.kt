package com.zenecran.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zenecran.data.InstalledApp
import com.zenecran.data.Presets
import com.zenecran.data.Store
import com.zenecran.data.fmtMin
import com.zenecran.data.fmtMs

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun LimitsScreen(vm: MainViewModel, padding: PaddingValues) {
    val ui by vm.ui.collectAsState()
    val cfg by Store.config.collectAsState()
    var showSheet by remember { mutableStateOf(false) }
    var expanded by remember { mutableStateOf<String?>(null) }
    val locked = cfg.isStrict()
    val labelOf = remember(ui.installed) { ui.installed.associate { it.pkg to it.label } }
    val installedPkgs = remember(ui.installed) { ui.installed.map { it.pkg }.toSet() }

    val monitored = cfg.limits.keys
        .sortedByDescending { ui.perApp[it] ?: 0L }
    val suggestions = Presets.apps.filter { it.pkg in installedPkgs && it.pkg !in cfg.limits }.take(9)

    LazyColumn(
        Modifier
            .fillMaxSize()
            .padding(padding),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Column {
                Text("Limites", color = Z.textHi, fontSize = 30.sp, fontWeight = FontWeight.Bold)
                Text(
                    "Une limite quotidienne par appli. Une fois atteinte, l'appli est fermée jusqu'à demain.",
                    color = Z.textLo, fontSize = 14.sp,
                )
            }
        }
        if (!ui.hasAccessibility) item { AccessibilityNotice() }

        if (suggestions.isNotEmpty()) {
            item {
                ZenCard {
                    Text("Suggestions", color = Z.textHi, fontWeight = FontWeight.SemiBold)
                    Text("Les applis qui aspirent le plus de temps. Un tap = limite de 30 min.", color = Z.textLo, fontSize = 13.sp)
                    Spacer(Modifier.height(12.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        suggestions.forEach { app ->
                            Button(
                                onClick = { vm.addApp(app.pkg) },
                                colors = ButtonDefaults.buttonColors(containerColor = Z.cardHi, contentColor = Z.textHi),
                            ) { Text("+ ${app.label}", fontSize = 13.sp) }
                        }
                    }
                }
            }
        }

        items(monitored, key = { it }) { pkg ->
            val limit = cfg.limits[pkg] ?: 0
            val used = ui.perApp[pkg] ?: 0L
            val open = expanded == pkg
            ZenCard(onClick = { expanded = if (open) null else pkg }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    AppIcon(pkg, size = 46.dp)
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(labelOf[pkg] ?: pkg, color = Z.textHi, fontWeight = FontWeight.SemiBold)
                        Text("Aujourd'hui : ${fmtMs(used)}", color = Z.textLo, fontSize = 13.sp)
                    }
                    Pill(
                        if (limit == 0) "Pas de limite" else fmtMin(limit),
                        Z.cardHi,
                        if (limit == 0) Z.textLo else Z.teal,
                    )
                }
                if (limit > 0) {
                    Spacer(Modifier.height(12.dp))
                    val p = (used.toFloat() / (limit * 60_000f)).coerceIn(0f, 1f)
                    LinearProgressIndicator(
                        progress = { p },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(RoundedCornerShape(4.dp)),
                        color = if (p < 0.7f) Z.teal else if (p < 1f) Z.amber else Z.red,
                        trackColor = Z.cardHi,
                    )
                }
                AnimatedVisibility(open) {
                    Column {
                        Spacer(Modifier.height(14.dp))
                        LimitSlider(limit = limit, locked = locked) { vm.setLimit(pkg, it) }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "Pas de limite = l'appli reste surveillée par les plages horaires et la concentration.",
                                color = Z.textLo, fontSize = 12.sp, modifier = Modifier.weight(1f),
                            )
                            TextButton(onClick = { vm.removeApp(pkg) }) {
                                if (locked) Icon(Icons.Rounded.Lock, null, tint = Z.textLo, modifier = Modifier.padding(end = 4.dp))
                                Text("Retirer", color = if (locked) Z.textLo else Z.red)
                            }
                        }
                    }
                }
            }
        }

        item {
            Button(
                onClick = { showSheet = true },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Z.teal, contentColor = Z.onTeal),
            ) {
                Icon(Icons.Rounded.Add, null)
                Spacer(Modifier.width(8.dp))
                Text("Ajouter une appli", fontWeight = FontWeight.SemiBold)
            }
        }
        item { Spacer(Modifier.height(8.dp)) }
    }

    if (showSheet) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { showSheet = false },
            sheetState = sheetState,
            containerColor = Z.card,
        ) {
            var query by remember { mutableStateOf("") }
            val list = ui.installed.filter { it.pkg !in cfg.limits && it.label.contains(query, ignoreCase = true) }
            Column(Modifier.padding(horizontal = 20.dp)) {
                Text("Quelle appli surveiller ?", color = Z.textHi, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    placeholder = { Text("Rechercher") },
                    leadingIcon = { Icon(Icons.Rounded.Search, null) },
                    shape = RoundedCornerShape(16.dp),
                )
                Spacer(Modifier.height(8.dp))
                LazyColumn(Modifier.heightIn(max = 480.dp)) {
                    items(list, key = { it.pkg }) { app: InstalledApp ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable {
                                    vm.addApp(app.pkg)
                                    showSheet = false
                                }
                                .padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            AppIcon(app.pkg, size = 40.dp)
                            Spacer(Modifier.width(14.dp))
                            Text(app.label, color = Z.textHi)
                        }
                    }
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

/** Curseur 5–240 min (0 = pas de limite). En mode engagement, on ne peut que réduire. */
@Composable
private fun LimitSlider(limit: Int, locked: Boolean, onCommit: (Int) -> Unit) {
    var tmp by remember(limit) { mutableFloatStateOf(limit.toFloat()) }
    val restrict = locked && limit > 0
    val minV = if (restrict) 5f else 0f
    val maxV = if (restrict) limit.toFloat() else 240f
    val usable = maxV > minV
    val steps = (((maxV - minV) / 5f).toInt() - 1).coerceAtLeast(0)
    Column {
        Text(
            if (tmp.toInt() == 0) "Pas de limite" else "Limite : ${fmtMin(tmp.toInt())} par jour",
            color = Z.textHi, fontSize = 14.sp,
        )
        Slider(
            value = tmp.coerceIn(minV, maxV),
            onValueChange = { tmp = (Math.round(it / 5f) * 5).toFloat() },
            onValueChangeFinished = { onCommit(tmp.toInt()) },
            valueRange = minV..maxV,
            steps = steps,
            enabled = usable,
            colors = SliderDefaults.colors(thumbColor = Z.teal, activeTrackColor = Z.teal, inactiveTrackColor = Z.cardHi),
        )
    }
}

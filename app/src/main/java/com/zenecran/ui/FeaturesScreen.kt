package com.zenecran.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
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
import com.zenecran.data.CustomRule
import com.zenecran.data.FeatureDef
import com.zenecran.data.InstalledApp
import com.zenecran.data.Presets
import com.zenecran.data.RuleConfig
import com.zenecran.data.Store

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FeaturesScreen(vm: MainViewModel, padding: PaddingValues) {
    val ui by vm.ui.collectAsState()
    val cfg by Store.config.collectAsState()
    val daily by Store.daily.collectAsState()
    val locked = cfg.isStrict()
    val installedPkgs = remember(ui.installed) { ui.installed.map { it.pkg }.toSet() }
    val labelOf = remember(ui.installed) { ui.installed.associate { it.pkg to it.label } }
    var showEditor by remember { mutableStateOf(false) }

    val feats = cfg.allFeatures()
    val customIds = cfg.customRules.map { it.id }.toSet()
    val order = Presets.apps.map { it.pkg }
    val pkgs = feats.map { it.pkg }.distinct()
        .sortedBy { p -> order.indexOf(p).let { if (it < 0) 999 else it } }

    LazyColumn(
        Modifier
            .fillMaxSize()
            .padding(padding),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Column {
                Text("Contenu", color = Z.textHi, fontSize = 30.sp, fontWeight = FontWeight.Bold)
                Text(
                    "Bloque uniquement la partie qui t'aspire (Reels, Stories, Spotlight, Shorts) sans toucher au reste de l'appli.",
                    color = Z.textLo, fontSize = 14.sp,
                )
            }
        }
        if (!ui.hasAccessibility) item { AccessibilityNotice() }

        for (pkg in pkgs) {
            item(key = pkg) {
                ZenCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        AppIcon(pkg, size = 44.dp)
                        Spacer(Modifier.width(14.dp))
                        Text(
                            Presets.appLabel(pkg) ?: labelOf[pkg] ?: pkg,
                            color = Z.textHi, fontSize = 18.sp, fontWeight = FontWeight.Bold,
                            modifier = Modifier.weight(1f),
                        )
                        if (pkg !in installedPkgs) Pill("non installée", Z.cardHi, Z.textLo)
                    }
                    feats.filter { it.pkg == pkg }.forEach { f ->
                        FeatureRow(
                            f = f,
                            rc = cfg.rules[f.id] ?: RuleConfig(),
                            usedSec = daily.featureSec[f.id] ?: 0,
                            locked = locked,
                            vm = vm,
                            onDelete = if (f.id in customIds) ({ vm.deleteCustomRule(f.id) }) else null,
                        )
                    }
                }
            }
        }

        item {
            Button(
                onClick = { showEditor = true },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Z.cardHi, contentColor = Z.textHi),
            ) {
                Icon(Icons.Rounded.Add, null)
                Spacer(Modifier.width(8.dp))
                Text("Règle personnalisée (autre appli)", fontWeight = FontWeight.SemiBold)
            }
        }

        item {
            ZenCard {
                Text("À savoir", color = Z.textHi, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(6.dp))
                Text(
                    "• La détection lit l'écran de l'appli. Quand une appli change son interface, " +
                        "un blocage peut cesser de marcher : le « Mode diagnostic » (Réglages) montre ce que voit Zen Écran, " +
                        "et une règle personnalisée permet de corriger ou d'ajouter n'importe quelle appli.\n" +
                        "• Les stories et Snapchat sont les plus capricieux. La limite quotidienne de l'appli entière (onglet Limites) marche toujours.\n" +
                        "• TikTok n'a pas de sous-section : utilise Limites ou Horaires.",
                    color = Z.textLo, fontSize = 13.sp,
                )
            }
        }
        item { Spacer(Modifier.height(8.dp)) }
    }

    if (showEditor) {
        ModalBottomSheet(
            onDismissRequest = { showEditor = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = Z.card,
        ) {
            CustomRuleEditor(vm, ui.installed) { showEditor = false }
        }
    }
}

@Composable
private fun CustomRuleEditor(vm: MainViewModel, installed: List<InstalledApp>, onClose: () -> Unit) {
    var pkg by remember { mutableStateOf<String?>(null) }
    var title by remember { mutableStateOf("") }
    var labels by remember { mutableStateOf("") }
    var ids by remember { mutableStateOf("") }
    var acts by remember { mutableStateOf("") }
    var query by remember { mutableStateOf("") }
    val shown = installed.filter { it.label.contains(query, ignoreCase = true) }
    val chosen = installed.firstOrNull { it.pkg == pkg }

    LazyColumn(
        Modifier
            .fillMaxWidth()
            .fillMaxHeight(0.92f),
        contentPadding = PaddingValues(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Column {
                Text("Règle personnalisée", color = Z.textHi, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(6.dp))
                Text(
                    "Bloque un onglet ou un écran d'une appli. Pour trouver les bons mots ou identifiants, " +
                        "active le Mode diagnostic dans les Réglages, ouvre l'appli sur l'écran voulu, puis copie la capture.",
                    color = Z.textLo, fontSize = 13.sp,
                )
            }
        }
        item {
            OutlinedTextField(
                value = title, onValueChange = { title = it },
                label = { Text("Nom (ex. Stories)") }, singleLine = true,
                modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp),
            )
        }
        item {
            OutlinedTextField(
                value = labels, onValueChange = { labels = it },
                label = { Text("Onglet actif contient… (séparés par des virgules)") },
                placeholder = { Text("ex. reels, vidéos") },
                modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp),
            )
        }
        item {
            OutlinedTextField(
                value = ids, onValueChange = { ids = it },
                label = { Text("Identifiants d'éléments (optionnel)") },
                placeholder = { Text("ex. reel_recycler, clips_tab") },
                modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp),
            )
        }
        item {
            OutlinedTextField(
                value = acts, onValueChange = { acts = it },
                label = { Text("Nom de l'écran (activité) contient… (optionnel)") },
                placeholder = { Text("ex. StoryViewerActivity") },
                modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp),
            )
        }
        item {
            Column {
                Text(
                    if (chosen == null) "Choisis l'appli concernée" else "Appli : ${chosen.label}",
                    color = Z.textHi, fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = query, onValueChange = { query = it },
                    placeholder = { Text("Rechercher une appli") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp),
                )
            }
        }
        items(shown, key = { it.pkg }) { app ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .clickable { pkg = app.pkg }
                    .padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AppIcon(app.pkg, size = 40.dp)
                Spacer(Modifier.width(14.dp))
                Text(app.label, color = Z.textHi, modifier = Modifier.weight(1f))
                RadioButton(
                    selected = pkg == app.pkg,
                    onClick = { pkg = app.pkg },
                    colors = RadioButtonDefaults.colors(selectedColor = Z.teal),
                )
            }
        }
        item {
            Column {
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = {
                        val l = labels.split(',').map { it.trim().lowercase() }.filter { it.isNotEmpty() }
                        val i = ids.split(',').map { it.trim() }.filter { it.isNotEmpty() }
                        val a = acts.split(',').map { it.trim() }.filter { it.isNotEmpty() }
                        val p = pkg
                        when {
                            p == null -> vm.say("Choisis une appli.")
                            title.isBlank() -> vm.say("Donne un nom à la règle.")
                            l.isEmpty() && i.isEmpty() && a.isEmpty() -> vm.say("Indique au moins un mot, un identifiant ou un écran.")
                            else -> {
                                vm.addCustomRule(CustomRule(vm.newScheduleId(), p, title.trim(), l, i, a))
                                vm.say("Règle ajoutée : active-la dans la liste.")
                                onClose()
                            }
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Z.teal, contentColor = Z.onTeal),
                ) { Text("Ajouter la règle", fontWeight = FontWeight.SemiBold) }
                Spacer(Modifier.height(28.dp))
            }
        }
    }
}

@Composable
private fun FeatureRow(
    f: FeatureDef,
    rc: RuleConfig,
    usedSec: Int,
    locked: Boolean,
    vm: MainViewModel,
    onDelete: (() -> Unit)?,
) {
    Column(Modifier.padding(top = 16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(f.title, color = Z.textHi, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                    if (f.experimental) {
                        Spacer(Modifier.width(8.dp))
                        Pill("expérimental", Z.cardHi, Z.amber)
                    }
                }
                Text(f.subtitle, color = Z.textLo, fontSize = 13.sp)
            }
            Switch(
                checked = rc.enabled,
                onCheckedChange = { vm.setRule(f.id, it, rc.budgetMin) },
                enabled = !(locked && rc.enabled),
                colors = SwitchDefaults.colors(checkedTrackColor = Z.teal, checkedThumbColor = Z.onTeal),
            )
        }
        if (rc.enabled) {
            Spacer(Modifier.height(10.dp))
            Row(
                Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                listOf(0, 5, 10, 15, 30).forEach { b ->
                    FilterChip(
                        selected = rc.budgetMin == b,
                        onClick = { vm.setRule(f.id, true, b) },
                        enabled = !locked || b <= rc.budgetMin,
                        label = { Text(if (b == 0) "Bloqué" else "$b min/jour") },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = Z.teal,
                            selectedLabelColor = Z.onTeal,
                        ),
                    )
                }
            }
            if (rc.budgetMin > 0) {
                Spacer(Modifier.height(10.dp))
                val p = (usedSec / (rc.budgetMin * 60f)).coerceIn(0f, 1f)
                LinearProgressIndicator(
                    progress = { p },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp)),
                    color = if (p < 1f) Z.violet else Z.red,
                    trackColor = Z.cardHi,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "${usedSec / 60} / ${rc.budgetMin} min aujourd'hui",
                    color = Z.textLo, fontSize = 12.sp,
                )
            }
        }
        if (onDelete != null) {
            TextButton(onClick = onDelete) { Text("Supprimer cette règle", color = Z.red, fontSize = 12.sp) }
        }
    }
}

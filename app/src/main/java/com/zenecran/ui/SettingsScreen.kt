package com.zenecran.ui

import androidx.compose.foundation.background
import androidx.compose.material3.OutlinedTextField
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zenecran.data.Store
import com.zenecran.data.fmtMin
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun SettingsScreen(vm: MainViewModel, padding: PaddingValues) {
    val cfg by Store.config.collectAsState()
    val ui by vm.ui.collectAsState()
    val dumps by Store.dumps.collectAsState()
    var dumpPkg by remember { mutableStateOf<String?>(null) }
    val clipboard = LocalClipboardManager.current
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val labelOf = remember(ui.installed) { ui.installed.associate { it.pkg to it.label } }
    var strictChoice by remember { mutableIntStateOf(0) }
    var confirmStrict by remember { mutableStateOf(false) }
    val strictOn = cfg.isStrict()

    LazyColumn(
        Modifier
            .fillMaxSize()
            .padding(padding),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item { Text("Réglages", color = Z.textHi, fontSize = 30.sp, fontWeight = FontWeight.Bold) }
        item { StatusCard(ui, alwaysShow = true) }
        item { UpdateCard(vm, onlyWhenAvailable = false) }

        // Objectif
        item {
            ZenCard {
                Text("Objectif quotidien", color = Z.textHi, fontWeight = FontWeight.SemiBold)
                Text("Utilisé pour l'anneau et le graphique de l'accueil.", color = Z.textLo, fontSize = 13.sp)
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { vm.setGoal(cfg.goalMin - 15) }) { Icon(Icons.Rounded.Remove, null, tint = Z.textHi) }
                    Text(fmtMin(cfg.goalMin), color = Z.teal, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                    IconButton(onClick = { vm.setGoal(cfg.goalMin + 15) }) { Icon(Icons.Rounded.Add, null, tint = Z.textHi) }
                }
            }
        }

        // Apparence
        item {
            ZenCard {
                Text("Apparence", color = Z.textHi, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(12.dp))
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Z.accents.forEachIndexed { i, c ->
                        Box(
                            Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(c)
                                .then(if (cfg.accent == i) Modifier.border(3.dp, Z.textHi, CircleShape) else Modifier)
                                .clickable { vm.setAccent(i) }
                        )
                    }
                }
                Spacer(Modifier.height(6.dp))
                Text(Z.accentNames[cfg.accent.coerceIn(0, Z.accentNames.size - 1)], color = Z.textLo, fontSize = 12.sp)
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Noir AMOLED", color = Z.textHi)
                        Text("Fond noir pur, économise la batterie sur écran OLED.", color = Z.textLo, fontSize = 12.sp)
                    }
                    Switch(
                        checked = cfg.amoled,
                        onCheckedChange = { vm.setAmoled(it) },
                        colors = zenSwitchColors(),
                    )
                }
            }
        }

        // Écran de blocage
        item {
            ZenCard {
                Text("Écran de blocage", color = Z.textHi, fontWeight = FontWeight.SemiBold)
                Text("Ce que tu vois quand une appli est bloquée.", color = Z.textLo, fontSize = 13.sp)
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = cfg.quote,
                    onValueChange = { vm.setQuote(it) },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Ton message (vide = phrases au hasard)") },
                    shape = RoundedCornerShape(16.dp),
                    maxLines = 3,
                )
                Spacer(Modifier.height(14.dp))
                Text("Bouton « + minutes »", color = Z.textHi, fontSize = 14.sp)
                if (strictOn) Text("Verrouillé par le mode engagement.", color = Z.amber, fontSize = 12.sp)
                Spacer(Modifier.height(6.dp))
                Text("Attente avant de pouvoir l'utiliser", color = Z.textLo, fontSize = 12.sp)
                ChipRow(listOf("Aucune" to 0, "15 s" to 15, "30 s" to 30, "60 s" to 60), cfg.extWaitSec, !strictOn) { vm.setExt(waitSec = it) }
                Text("Minutes accordées", color = Z.textLo, fontSize = 12.sp)
                ChipRow(listOf("5 min" to 5, "10 min" to 10, "15 min" to 15), cfg.extMinutes, !strictOn) { vm.setExt(minutes = it) }
                Text("Maximum par jour et par appli", color = Z.textLo, fontSize = 12.sp)
                ChipRow(listOf("Désactivé" to 0, "1" to 1, "2" to 2, "3" to 3, "5" to 5), cfg.extMax, !strictOn) { vm.setExt(max = it) }
            }
        }

        // Comportement
        item {
            ZenCard {
                Text("Comportement", color = Z.textHi, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(10.dp))
                Text("Prévenir avant la limite", color = Z.textLo, fontSize = 12.sp)
                ChipRow(listOf("Non" to 0, "5 min avant" to 5, "10 min avant" to 10), cfg.warnMin, true) { vm.setWarn(it) }
                Text("Quand un Reel / une story est bloqué", color = Z.textLo, fontSize = 12.sp)
                ChipRow(listOf("Retour" to 1, "Accueil" to 0), if (cfg.featureBack) 1 else 0, true) { vm.setFeatureBack(it == 1) }
            }
        }

        // Engagement
        item {
            ZenCard {
                Text("Mode engagement", color = Z.textHi, fontWeight = FontWeight.SemiBold)
                Text(
                    "Pour te protéger de toi-même : pendant la durée choisie, tu ne peux plus assouplir tes règles " +
                        "(augmenter une limite, retirer une appli, désactiver un blocage ou une plage, couper la concentration) " +
                        "et le bouton « +5 minutes » disparaît. Tu peux toujours être plus strict.",
                    color = Z.textLo, fontSize = 13.sp,
                )
                Spacer(Modifier.height(12.dp))
                if (strictOn) {
                    val until = SimpleDateFormat("EEEE d MMM 'à' HH:mm", Locale.getDefault()).format(Date(cfg.strictUntil))
                    Text("Actif jusqu'à $until", color = Z.teal, fontWeight = FontWeight.SemiBold)
                } else {
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("Jusqu'à minuit", "24 h", "7 jours").forEachIndexed { i, label ->
                            FilterChip(
                                selected = strictChoice == i,
                                onClick = { strictChoice = i },
                                label = { Text(label) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = Z.teal,
                                    selectedLabelColor = Z.onTeal,
                                ),
                            )
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    Button(
                        onClick = { confirmStrict = true },
                        colors = ButtonDefaults.buttonColors(containerColor = Z.violet, contentColor = Z.textHi),
                    ) { Text("Activer l'engagement") }
                }
            }
        }

        // Diagnostic
        item {
            ZenCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Mode diagnostic", color = Z.textHi, fontWeight = FontWeight.SemiBold)
                        Text(
                            "Pour réparer ou créer un blocage : active-le, ouvre l'appli sur l'écran voulu " +
                                "(ex. une story) 3 secondes, reviens ici, choisis l'appli et partage la capture.",
                            color = Z.textLo, fontSize = 13.sp,
                        )
                    }
                    Switch(
                        checked = cfg.diagnostic,
                        onCheckedChange = { vm.setDiagnostic(it) },
                        colors = zenSwitchColors(),
                    )
                }
                if (cfg.diagnostic) {
                    Spacer(Modifier.height(12.dp))
                    if (dumps.isEmpty()) {
                        Text("Rien de capturé pour l'instant. Ouvre une appli quelques secondes puis reviens.", color = Z.amber, fontSize = 13.sp)
                    } else {
                        val sel = dumpPkg?.takeIf { it in dumps } ?: dumps.keys.last()
                        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            dumps.keys.forEach { p ->
                                FilterChip(
                                    selected = p == sel,
                                    onClick = { dumpPkg = p },
                                    label = { Text(labelOf[p] ?: p) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = Z.teal,
                                        selectedLabelColor = Z.onTeal,
                                    ),
                                )
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        val dump = dumps[sel].orEmpty()
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .heightIn(max = 260.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(Z.bg)
                                .padding(10.dp)
                        ) {
                            SelectionContainer {
                                Text(
                                    dump,
                                    color = Z.textLo,
                                    fontSize = 10.sp,
                                    fontFamily = FontFamily.Monospace,
                                    modifier = Modifier.verticalScroll(rememberScrollState()),
                                )
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = {
                                clipboard.setText(AnnotatedString(dump))
                                vm.say("Capture copiée.")
                            }) { Text("Copier") }
                            Button(
                                onClick = {
                                    val send = android.content.Intent(android.content.Intent.ACTION_SEND)
                                        .setType("text/plain")
                                        .putExtra(android.content.Intent.EXTRA_TEXT, dump)
                                    ctx.startActivity(android.content.Intent.createChooser(send, "Partager la capture"))
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Z.teal, contentColor = Z.onTeal),
                            ) { Text("Partager") }
                        }
                    }
                }
            }
        }

        // Confidentialité
        item {
            ZenCard {
                Text("Confidentialité", color = Z.textHi, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(6.dp))
                Text(
                    "Zen Écran n'utilise Internet que pour vérifier et télécharger ses mises à jour sur GitHub : aucune donnée ne quitte ton téléphone. " +
                        "Le service d'accessibilité sert uniquement à savoir quelle appli est ouverte et à repérer les onglets que tu as choisi de bloquer.\n\n" +
                        "Limites honnêtes : si tu désactives le service d'accessibilité ou désinstalles l'appli, plus rien n'est bloqué. " +
                        "Pour un vrai verrou, demande à quelqu'un de confiance de garder le code de déverrouillage de ton téléphone, ou utilise Family Link.",
                    color = Z.textLo, fontSize = 13.sp,
                )
            }
        }
        item { Spacer(Modifier.height(8.dp)) }
    }

    if (confirmStrict) {
        StrictDialog(
            onDismiss = { confirmStrict = false },
            onConfirm = {
                vm.enableStrict(listOf(0, 24, 168)[strictChoice])
                confirmStrict = false
            },
        )
    }
}

@Composable
private fun ChipRow(options: List<Pair<String, Int>>, selected: Int, enabled: Boolean, onSelect: (Int) -> Unit) {
    Row(
        Modifier
            .horizontalScroll(rememberScrollState())
            .padding(bottom = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        options.forEach { (label, value) ->
            FilterChip(
                selected = selected == value,
                onClick = { onSelect(value) },
                enabled = enabled,
                label = { Text(label) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = Z.teal,
                    selectedLabelColor = Z.onTeal,
                ),
            )
        }
    }
}

@Composable
private fun StrictDialog(onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Z.card,
        title = { Text("Activer l'engagement ?") },
        text = { Text("Tu ne pourras pas l'annuler avant la fin de la durée choisie. Tes règles actuelles seront verrouillées.") },
        confirmButton = { TextButton(onClick = onConfirm) { Text("Oui, je m'engage") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annuler") } },
    )
}

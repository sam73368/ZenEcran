package com.zenecran.ui

import androidx.compose.foundation.background
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
    val dump by Store.dump.collectAsState()
    val clipboard = LocalClipboardManager.current
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
                            "Pour réparer un blocage de Reels/Spotlight : active-le, ouvre l'appli sur l'écran voulu, " +
                                "reviens ici, copie le texte et envoie-le-moi.",
                            color = Z.textLo, fontSize = 13.sp,
                        )
                    }
                    Switch(
                        checked = cfg.diagnostic,
                        onCheckedChange = { vm.setDiagnostic(it) },
                        colors = SwitchDefaults.colors(checkedTrackColor = Z.teal, checkedThumbColor = Z.onTeal),
                    )
                }
                if (cfg.diagnostic) {
                    Spacer(Modifier.height(12.dp))
                    if (dump.isEmpty()) {
                        Text("Rien de capturé pour l'instant. Ouvre une appli surveillée quelques secondes.", color = Z.amber, fontSize = 13.sp)
                    } else {
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .heightIn(max = 240.dp)
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
                        OutlinedButton(onClick = {
                            clipboard.setText(AnnotatedString(dump))
                            vm.say("Capture copiée.")
                        }) { Text("Copier la capture") }
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
                    "Zen Écran n'a aucune permission Internet : rien ne quitte ton téléphone. " +
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
        AlertDialog(
            onDismissRequest = { confirmStrict = false },
            containerColor = Z.card,
            title = { Text("Activer l'engagement ?") },
            text = { Text("Tu ne pourras pas l'annuler avant la fin de la durée choisie. Tes règles actuelles seront verrouillées.") },
            confirmButton = {
                TextButton(onClick = {
                    vm.enableStrict(listOf(0, 24, 168)[strictChoice])
                    confirmStrict = false
                }) { Text("Oui, je m'engage") }
            },
            dismissButton = { TextButton(onClick = { confirmStrict = false }) { Text("Annuler") } },
        )
    }
}

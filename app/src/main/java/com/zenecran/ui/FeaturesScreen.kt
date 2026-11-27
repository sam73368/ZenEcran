package com.zenecran.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zenecran.data.FeatureDef
import com.zenecran.data.Presets
import com.zenecran.data.RuleConfig
import com.zenecran.data.Store

@Composable
fun FeaturesScreen(vm: MainViewModel, padding: PaddingValues) {
    val ui by vm.ui.collectAsState()
    val cfg by Store.config.collectAsState()
    val daily by Store.daily.collectAsState()
    val locked = cfg.isStrict()
    val installedPkgs = remember(ui.installed) { ui.installed.map { it.pkg }.toSet() }
    val apps = Presets.apps.filter { app -> Presets.features.any { it.pkg == app.pkg } }

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
                    "Bloque uniquement la partie qui t'aspire (Reels, Spotlight, Shorts) sans toucher au reste de l'appli.",
                    color = Z.textLo, fontSize = 14.sp,
                )
            }
        }
        if (!ui.hasAccessibility) item { AccessibilityNotice() }

        for (app in apps) {
            item(key = app.pkg) {
                ZenCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        AppIcon(app.pkg, size = 44.dp)
                        Spacer(Modifier.width(14.dp))
                        Text(app.label, color = Z.textHi, fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                        if (app.pkg !in installedPkgs) Pill("non installée", Z.cardHi, Z.textLo)
                    }
                    Presets.features.filter { it.pkg == app.pkg }.forEach { f ->
                        FeatureRow(
                            f = f,
                            rc = cfg.rules[f.id] ?: RuleConfig(),
                            usedSec = daily.featureSec[f.id] ?: 0,
                            locked = locked,
                            vm = vm,
                        )
                    }
                }
            }
        }

        item {
            ZenCard {
                Text("À savoir", color = Z.textHi, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(6.dp))
                Text(
                    "• La détection lit l'écran de l'appli. Quand Facebook, Snapchat, etc. changent leur interface, " +
                        "un blocage peut cesser de marcher : le « Mode diagnostic » (Réglages) permet de le corriger.\n" +
                        "• Snapchat est le plus capricieux. Dans tous les cas, la limite quotidienne de l'appli entière (onglet Limites) marche toujours.\n" +
                        "• TikTok n'a pas de sous-section : utilise Limites ou Horaires.",
                    color = Z.textLo, fontSize = 13.sp,
                )
            }
        }
        item { Spacer(Modifier.height(8.dp)) }
    }
}

@Composable
private fun FeatureRow(f: FeatureDef, rc: RuleConfig, usedSec: Int, locked: Boolean, vm: MainViewModel) {
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
    }
}

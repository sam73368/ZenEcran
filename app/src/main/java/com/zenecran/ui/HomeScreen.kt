package com.zenecran.ui

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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zenecran.data.Store
import com.zenecran.data.fmtMin
import com.zenecran.data.fmtMs
import kotlinx.coroutines.delay
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Calendar
import java.util.Locale

@Composable
fun HomeScreen(vm: MainViewModel, padding: PaddingValues) {
    val ui by vm.ui.collectAsState()
    val cfg by Store.config.collectAsState()
    val ctx = LocalContext.current
    val now by produceState(System.currentTimeMillis()) {
        while (true) {
            delay(15_000)
            value = System.currentTimeMillis()
        }
    }

    val hour = remember { Calendar.getInstance().get(Calendar.HOUR_OF_DAY) }
    val greeting = when {
        hour < 5 -> "Bonne nuit"
        hour < 12 -> "Bonjour"
        hour < 18 -> "Bon après-midi"
        else -> "Bonsoir"
    }
    val labels = remember {
        val today = LocalDate.now()
        (6 downTo 0).map {
            today.minusDays(it.toLong()).dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault())
                .take(1).uppercase()
        }
    }
    val goalMs = cfg.goalMin * 60_000L
    val total = ui.total
    val labelOf = remember(ui.installed) { ui.installed.associate { it.pkg to it.label } }
    val top = ui.perApp.entries
        .filter { it.key in labelOf && it.value >= 60_000L }
        .sortedByDescending { it.value }
        .take(6)
    val topMax = (top.firstOrNull()?.value ?: 1L).coerceAtLeast(1L)

    LazyColumn(
        Modifier
            .fillMaxSize()
            .padding(padding),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Column {
                Text(greeting, color = Z.textLo, fontSize = 15.sp)
                Text("Ton temps d'écran", color = Z.textHi, fontSize = 30.sp, fontWeight = FontWeight.Bold)
            }
        }

        if (ui.loaded) item { StatusCard(ui, alwaysShow = false) }

        item {
            ZenCard {
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    UsageRing(
                        progress = if (goalMs > 0) total.toFloat() / goalMs else 0f,
                        center = fmtMs(total),
                        caption = "sur ${fmtMin(cfg.goalMin)} d'objectif",
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                        Stat("Hier", fmtMs(ui.week.getOrElse(5) { 0L }))
                        Stat("Moyenne 6 j", fmtMs(ui.week.take(6).average().toLong()))
                    }
                }
            }
        }

        item { FocusCard(vm, now) }

        item {
            ZenCard {
                Text("7 derniers jours", color = Z.textHi, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                Text("Ligne pointillée : ton objectif", color = Z.textLo, fontSize = 12.sp)
                Spacer(Modifier.height(14.dp))
                WeekChart(ui.week, labels, goalMs)
            }
        }

        if (top.isNotEmpty()) {
            item { SectionTitle("APPLIS LES PLUS UTILISÉES AUJOURD'HUI") }
            items(top, key = { it.key }) { (pkg, ms) ->
                ZenCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        AppIcon(pkg, size = 44.dp)
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text(labelOf[pkg] ?: pkg, color = Z.textHi, fontWeight = FontWeight.SemiBold)
                            Text(fmtMs(ms), color = Z.textLo, fontSize = 13.sp)
                        }
                        val lim = cfg.limits[pkg] ?: 0
                        if (lim > 0) Pill("limite ${fmtMin(lim)}", Z.cardHi, Z.teal)
                    }
                    Spacer(Modifier.height(12.dp))
                    LinearProgressIndicator(
                        progress = { ms.toFloat() / topMax },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp)),
                        color = Z.violet,
                        trackColor = Z.cardHi,
                    )
                }
            }
        }
        item { Spacer(Modifier.height(8.dp)) }
    }
}

@Composable
private fun Stat(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, color = Z.textHi, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
        Text(label, color = Z.textLo, fontSize = 12.sp)
    }
}

@Composable
fun AccessibilityNotice() {
    val ctx = LocalContext.current
    Column {
        NoticeCard(
            title = "Active le service Zen Écran",
            body = "C'est lui qui bloque les applis et les Reels/Spotlight. Réglages d'accessibilité → Zen Écran → activer. " +
                "Si l'option est grisée (Android 13+) : « Infos de l'appli » → menu ⋮ → « Autoriser les paramètres restreints », puis réessaie.",
            action = "Ouvrir l'accessibilité",
            onClick = { openAccessibility(ctx) },
        )
        Spacer(Modifier.height(8.dp))
        OutlinedButton(onClick = { openAppInfo(ctx) }) { Text("Infos de l'appli (paramètres restreints)") }
    }
}

@Composable
private fun FocusCard(vm: MainViewModel, now: Long) {
    val cfg by Store.config.collectAsState()
    ZenCard {
        Text("Mode concentration", color = Z.textHi, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        Text(
            "Bloque toutes tes applis surveillées (Limites + Horaires) pendant un moment.",
            color = Z.textLo, fontSize = 13.sp,
        )
        Spacer(Modifier.height(12.dp))
        if (cfg.isFocus(now)) {
            val left = ((cfg.focusUntil - now) / 60_000L).toInt() + 1
            Text("Actif : encore ${fmtMin(left)}", color = Z.teal, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(10.dp))
            OutlinedButton(onClick = { vm.stopFocus() }) { Text("Arrêter") }
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(30, 60, 120).forEach { m ->
                    Button(
                        onClick = { vm.startFocus(m) },
                        colors = ButtonDefaults.buttonColors(containerColor = Z.cardHi, contentColor = Z.textHi),
                    ) { Text(fmtMin(m)) }
                }
            }
        }
    }
}

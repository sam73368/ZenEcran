package com.zenecran.ui

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zenecran.update.Updater

/** onlyWhenAvailable = true : n'apparaît que si une nouvelle version existe (accueil). */
@Composable
fun UpdateCard(vm: MainViewModel, onlyWhenAvailable: Boolean) {
    val st by vm.upd.collectAsState()
    val available = st is UpdState.Available || st is UpdState.Downloading
    if (onlyWhenAvailable && !available) return
    ZenCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Mises à jour", color = Z.textHi, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            Text("version 1.${Updater.currentBuild}", color = Z.textLo, fontSize = 12.sp)
        }
        Spacer(Modifier.height(8.dp))
        when (val s = st) {
            UpdState.Idle -> Text("Vérifie s'il existe une version plus récente.", color = Z.textLo, fontSize = 13.sp)
            UpdState.Checking -> Text("Vérification…", color = Z.textLo, fontSize = 13.sp)
            UpdState.UpToDate -> Text("Tu as la dernière version.", color = Z.teal, fontSize = 13.sp)
            is UpdState.Error -> Text(s.msg, color = Z.amber, fontSize = 13.sp)
            is UpdState.Available -> {
                Text("Nouvelle version disponible : ${s.info.name}", color = Z.teal, fontWeight = FontWeight.SemiBold)
                if (s.info.notes.isNotBlank()) Text(s.info.notes.take(300), color = Z.textLo, fontSize = 12.sp)
            }
            is UpdState.Downloading -> {
                Text("Téléchargement… ${(s.progress * 100).toInt()} %", color = Z.textLo, fontSize = 13.sp)
                Spacer(Modifier.height(6.dp))
                LinearProgressIndicator(
                    progress = { s.progress },
                    modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                    color = Z.teal,
                    trackColor = Z.cardHi,
                )
            }
        }
        Spacer(Modifier.height(10.dp))
        Row {
            when (val s = st) {
                is UpdState.Available -> Button(
                    onClick = { vm.installUpdate(s.info) },
                    colors = ButtonDefaults.buttonColors(containerColor = Z.teal, contentColor = Z.onTeal),
                ) { Text("Installer") }
                is UpdState.Downloading, UpdState.Checking -> Unit
                else -> OutlinedButton(onClick = { vm.checkUpdate() }) { Text("Vérifier maintenant") }
            }
            Spacer(Modifier.width(8.dp))
        }
    }
}

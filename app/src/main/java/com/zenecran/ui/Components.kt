package com.zenecran.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun ZenCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val base = modifier
        .fillMaxWidth()
        .clip(RoundedCornerShape(24.dp))
        .background(Z.card)
    Column(
        modifier = (if (onClick != null) base.clickable(onClick = onClick) else base).padding(18.dp),
        content = content,
    )
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        modifier = modifier.padding(start = 4.dp, top = 4.dp),
        color = Z.textLo,
        fontSize = 13.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 0.8.sp,
    )
}

@Composable
fun Pill(text: String, color: Color = Z.cardHi, textColor: Color = Z.textHi, modifier: Modifier = Modifier) {
    Box(
        modifier
            .clip(RoundedCornerShape(50))
            .background(color)
            .padding(horizontal = 10.dp, vertical = 4.dp)
    ) {
        Text(text, color = textColor, fontSize = 12.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
fun AppIcon(pkg: String, modifier: Modifier = Modifier, size: Dp = 44.dp) {
    val ctx = LocalContext.current
    val bmp by produceState<ImageBitmap?>(null, pkg) {
        value = withContext(Dispatchers.IO) {
            try {
                ctx.packageManager.getApplicationIcon(pkg).toBitmap(96, 96).asImageBitmap()
            } catch (e: Exception) {
                null
            }
        }
    }
    Box(
        modifier
            .size(size)
            .clip(RoundedCornerShape(size * 0.28f))
            .background(Z.cardHi),
        contentAlignment = Alignment.Center,
    ) {
        bmp?.let { Image(bitmap = it, contentDescription = null, modifier = Modifier.fillMaxSize()) }
    }
}

/** Bandeau d'avertissement avec un bouton d'action. */
@Composable
fun NoticeCard(title: String, body: String, action: String, onClick: () -> Unit, accent: Color = Z.amber) {
    ZenCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .width(4.dp)
                    .height(40.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(accent)
            )
            Spacer(Modifier.width(12.dp))
            Column {
                Text(title, color = Z.textHi, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(body, color = Z.textLo, fontSize = 13.sp)
        Spacer(Modifier.height(12.dp))
        Button(
            onClick = onClick,
            colors = ButtonDefaults.buttonColors(containerColor = accent, contentColor = Color(0xFF1A1200)),
        ) { Text(action) }
    }
}

@Composable
fun UsageRing(progress: Float, center: String, caption: String, modifier: Modifier = Modifier) {
    val anim by animateFloatAsState(progress.coerceIn(0f, 1f), tween(900), label = "ring")
    val color = when {
        progress < 0.7f -> Z.teal
        progress < 1f -> Z.amber
        else -> Z.red
    }
    Box(modifier.size(220.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = 22.dp.toPx()
            val inset = stroke / 2
            val arc = Size(size.width - stroke, size.height - stroke)
            drawArc(
                color = Z.cardHi,
                startAngle = 135f,
                sweepAngle = 270f,
                useCenter = false,
                topLeft = Offset(inset, inset),
                size = arc,
                style = Stroke(stroke, cap = StrokeCap.Round),
            )
            if (anim > 0.005f) {
                drawArc(
                    color = color,
                    startAngle = 135f,
                    sweepAngle = 270f * anim,
                    useCenter = false,
                    topLeft = Offset(inset, inset),
                    size = arc,
                    style = Stroke(stroke, cap = StrokeCap.Round),
                )
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(center, color = Z.textHi, fontSize = 38.sp, fontWeight = FontWeight.Bold)
            Text(caption, color = Z.textLo, fontSize = 14.sp)
        }
    }
}

@Composable
fun WeekChart(values: List<Long>, labels: List<String>, goalMs: Long, modifier: Modifier = Modifier) {
    val maxV = maxOf(values.maxOrNull() ?: 0L, goalMs, 1L).toFloat()
    Column(modifier) {
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(140.dp)
        ) {
            val n = values.size
            val gap = 12.dp.toPx()
            val barW = (size.width - gap * (n - 1)) / n
            val goalY = size.height - size.height * (goalMs / maxV)
            drawLine(
                color = Z.textLo.copy(alpha = 0.5f),
                start = Offset(0f, goalY),
                end = Offset(size.width, goalY),
                strokeWidth = 1.5.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(14f, 12f)),
            )
            values.forEachIndexed { i, v ->
                val h = maxOf(size.height * (v / maxV), 4.dp.toPx())
                val c = if (i == n - 1) Z.teal else Z.violet.copy(alpha = 0.55f)
                drawRoundRect(
                    color = c,
                    topLeft = Offset(i * (barW + gap), size.height - h),
                    size = Size(barW, h),
                    cornerRadius = CornerRadius(10.dp.toPx()),
                )
            }
        }
        Row(
            Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            labels.forEach {
                Text(
                    it,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                    color = Z.textLo,
                    fontSize = 12.sp,
                )
            }
        }
    }
}

// ---------------------------------------------------------------- intents système

fun openUsageAccess(ctx: Context) {
    try {
        ctx.startActivity(
            Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS, Uri.parse("package:${ctx.packageName}"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    } catch (e: Exception) {
        ctx.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}

fun openAccessibility(ctx: Context) {
    ctx.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}

fun openAppInfo(ctx: Context) {
    ctx.startActivity(
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${ctx.packageName}"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    )
}

fun openBatterySettings(ctx: Context) {
    try {
        @android.annotation.SuppressLint("BatteryLife")
        val i = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${ctx.packageName}"))
        ctx.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (e: Exception) {
        ctx.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}

@Composable
private fun StatusRow(label: String, ok: Boolean, action: String, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(22.dp)
                .clip(androidx.compose.foundation.shape.CircleShape)
                .background(if (ok) Z.teal else Z.red),
            contentAlignment = Alignment.Center,
        ) {
            Text(if (ok) "✓" else "!", color = Z.onTeal, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.width(12.dp))
        Text(label, color = Z.textHi, fontSize = 14.sp, modifier = Modifier.weight(1f))
        if (!ok) {
            androidx.compose.material3.TextButton(onClick = onClick) { Text(action, color = Z.teal) }
        }
    }
}

/**
 * État réel de l'appli : autorisations et service qui tourne vraiment (il « bat » chaque seconde).
 * alwaysShow = false : n'apparaît que s'il manque quelque chose.
 */
@Composable
fun StatusCard(ui: UiState, alwaysShow: Boolean) {
    val ctx = LocalContext.current
    val beat by com.zenecran.data.Store.beat.collectAsState()
    val now by produceState(android.os.SystemClock.elapsedRealtime()) {
        while (true) {
            kotlinx.coroutines.delay(1000)
            value = android.os.SystemClock.elapsedRealtime()
        }
    }
    val alive = beat > 0 && now - beat < 5000
    val allOk = ui.hasUsage && ui.hasAccessibility && alive && ui.batteryOk
    if (!alwaysShow && allOk) return
    ZenCard {
        Text(
            if (allOk) "Tout fonctionne" else "À configurer",
            color = if (allOk) Z.teal else Z.amber, fontSize = 16.sp, fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(4.dp))
        StatusRow("Accès aux statistiques d'utilisation", ui.hasUsage, "Autoriser") { openUsageAccess(ctx) }
        StatusRow(
            when {
                !ui.hasAccessibility -> "Service de blocage désactivé"
                !alive -> "Service activé mais ne répond pas"
                else -> "Service de blocage actif"
            },
            ui.hasAccessibility && alive,
            "Activer",
        ) { openAccessibility(ctx) }
        StatusRow("Batterie sans restriction", ui.batteryOk, "Autoriser") { openBatterySettings(ctx) }
        if (!ui.hasAccessibility) {
            Spacer(Modifier.height(4.dp))
            Text(
                "Réglages → Accessibilité → Applis installées → Zen Écran → activer. " +
                    "Si c'est grisé (Android 13+) : « Infos de l'appli » → menu ⋮ en haut à droite → " +
                    "« Autoriser les paramètres restreints », puis réessaie.",
                color = Z.textLo, fontSize = 12.sp,
            )
            androidx.compose.material3.TextButton(onClick = { openAppInfo(ctx) }) {
                Text("Ouvrir les infos de l'appli", color = Z.teal)
            }
        } else if (!alive) {
            Spacer(Modifier.height(4.dp))
            Text(
                "Android a arrêté le service. Désactive puis réactive Zen Écran dans Accessibilité, " +
                    "et autorise la batterie sans restriction pour éviter que ça recommence.",
                color = Z.textLo, fontSize = 12.sp,
            )
        }
    }
}

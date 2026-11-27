package com.zenecran.service

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.graphics.PixelFormat
import android.view.WindowManager
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.zenecran.ui.AppIcon
import com.zenecran.ui.Z
import com.zenecran.ui.ZenTheme
import kotlinx.coroutines.delay

data class BlockInfo(
    val appLabel: String,
    val pkg: String,
    val title: String,
    val detail: String,
    val usedLine: String,
    val canExtend: Boolean,
    val onExtend: () -> Unit,
)

/** Fenêtre plein écran affichée par-dessus tout quand une appli est bloquée. */
class BlockOverlay(private val service: AccessibilityService) {
    private val wm = service.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private var view: ComposeView? = null
    private var owner: OverlayOwner? = null

    val isShowing: Boolean get() = view != null

    fun show(info: BlockInfo) {
        hide()
        val o = OverlayOwner()
        o.resume()
        val v = ComposeView(service)
        v.setViewTreeLifecycleOwner(o)
        v.setViewTreeSavedStateRegistryOwner(o)
        v.setContent {
            ZenTheme {
                BlockScreen(info = info, onClose = { hide() })
            }
        }
        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT,
        )
        try {
            wm.addView(v, lp)
            view = v
            owner = o
        } catch (e: Exception) {
            o.destroy()
        }
    }

    fun hide() {
        view?.let {
            try {
                wm.removeView(it)
            } catch (_: Exception) {
            }
        }
        owner?.destroy()
        view = null
        owner = null
    }
}

private class OverlayOwner : LifecycleOwner, SavedStateRegistryOwner {
    private val registry = LifecycleRegistry(this)
    private val controller = SavedStateRegistryController.create(this)

    override val lifecycle: Lifecycle get() = registry
    override val savedStateRegistry: SavedStateRegistry get() = controller.savedStateRegistry

    init {
        controller.performRestore(null)
        registry.currentState = Lifecycle.State.CREATED
    }

    fun resume() {
        registry.currentState = Lifecycle.State.RESUMED
    }

    fun destroy() {
        registry.currentState = Lifecycle.State.DESTROYED
    }
}

private val quotes = listOf(
    "Qu'est-ce que tu voulais vraiment faire ?",
    "Le fil sera toujours là. Ton temps, non.",
    "Respire. Bois un verre d'eau. Étire-toi.",
    "Dans 5 minutes, tu seras content de ne pas avoir cédé.",
    "Un bon moment pour appeler quelqu'un ou sortir dehors.",
    "Tu as mieux à faire que de défiler.",
)

@androidx.compose.runtime.Composable
fun BlockScreen(info: BlockInfo, onClose: () -> Unit) {
    val quote = remember { quotes.random() }
    val pulse = rememberInfiniteTransition(label = "pulse")
    val scale by pulse.animateFloat(
        initialValue = 0.92f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(tween(1800, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "scale",
    )
    var left by remember { mutableIntStateOf(15) }
    LaunchedEffect(Unit) {
        while (left > 0) {
            delay(1000)
            left--
        }
    }
    // Sécurité : la fenêtre se ferme toute seule après 45 s.
    LaunchedEffect(Unit) {
        delay(45_000)
        onClose()
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFF1F1654), Z.bg))),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier.padding(horizontal = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Box(contentAlignment = Alignment.Center) {
                Box(
                    Modifier
                        .size(150.dp)
                        .scale(scale)
                        .clip(CircleShape)
                        .background(Z.violet.copy(alpha = 0.18f))
                )
                Box(
                    Modifier
                        .size(104.dp)
                        .clip(CircleShape)
                        .background(Z.card),
                    contentAlignment = Alignment.Center,
                ) {
                    AppIcon(pkg = info.pkg, size = 56.dp)
                }
                Box(
                    Modifier
                        .align(Alignment.BottomEnd)
                        .padding(end = 18.dp, bottom = 10.dp)
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(Z.teal),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Rounded.Lock, contentDescription = null, tint = Z.onTeal, modifier = Modifier.size(18.dp))
                }
            }
            Spacer(Modifier.height(28.dp))
            Text(info.title, color = Z.textHi, fontSize = 28.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
            Spacer(Modifier.height(10.dp))
            Text(info.detail, color = Z.textLo, fontSize = 16.sp, textAlign = TextAlign.Center)
            if (info.usedLine.isNotEmpty()) {
                Spacer(Modifier.height(6.dp))
                Text(info.usedLine, color = Z.textLo, fontSize = 14.sp, textAlign = TextAlign.Center)
            }
            Spacer(Modifier.height(22.dp))
            Text(quote, color = Z.teal, fontSize = 15.sp, textAlign = TextAlign.Center, fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(36.dp))
            Button(
                onClick = onClose,
                modifier = Modifier.fillMaxWidth().height(54.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Z.teal, contentColor = Z.onTeal),
            ) {
                Text("Retour à l'accueil", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            }
            if (info.canExtend) {
                Spacer(Modifier.height(10.dp))
                OutlinedButton(
                    onClick = {
                        info.onExtend()
                        onClose()
                    },
                    enabled = left == 0,
                    modifier = Modifier.fillMaxWidth().height(50.dp),
                ) {
                    Text(if (left == 0) "+5 minutes (dernière chance)" else "+5 minutes dans $left s…")
                }
            }
        }
    }
}

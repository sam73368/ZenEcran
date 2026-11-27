package com.zenecran.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.zenecran.data.Config
import com.zenecran.data.Store

/** Palette de l'appli. La couleur d'accent et le mode AMOLED se règlent dans les Réglages. */
object Z {
    var accentIdx by mutableIntStateOf(0)
    var amoled by mutableStateOf(false)

    val accents = listOf(
        Color(0xFF3DDBB0), Color(0xFF8B7CFF), Color(0xFFFFB84D),
        Color(0xFFFF7EB6), Color(0xFF5CA8FF), Color(0xFFC6F24E),
    )
    val accentNames = listOf("Menthe", "Violet", "Ambre", "Rose", "Bleu", "Citron")

    val bg: Color get() = if (amoled) Color(0xFF000000) else Color(0xFF0B0F1A)
    val card: Color get() = if (amoled) Color(0xFF0F0F14) else Color(0xFF141A2B)
    val cardHi: Color get() = if (amoled) Color(0xFF1B1B24) else Color(0xFF1E2640)
    val teal: Color get() = accents[accentIdx.coerceIn(0, accents.size - 1)]
    val onTeal = Color(0xFF0B0F1A)
    val violet = Color(0xFF8B7CFF)
    val amber = Color(0xFFFFB84D)
    val red = Color(0xFFFF6B6B)
    val textHi = Color(0xFFF1F4FF)
    val textLo = Color(0xFF8E97B5)

    fun apply(c: Config) {
        accentIdx = c.accent
        amoled = c.amoled
    }
}

private val type = Typography(
    titleLarge = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.Bold),
    titleMedium = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.SemiBold),
    bodyMedium = TextStyle(fontSize = 14.sp),
    bodySmall = TextStyle(fontSize = 12.sp),
    labelLarge = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Medium),
)

@Composable
fun ZenTheme(content: @Composable () -> Unit) {
    val cfg by Store.config.collectAsState()
    SideEffect { Z.apply(cfg) }
    val scheme = remember(Z.accentIdx, Z.amoled) {
        darkColorScheme(
            primary = Z.teal,
            onPrimary = Z.onTeal,
            secondary = Z.violet,
            background = Z.bg,
            onBackground = Z.textHi,
            surface = Z.card,
            onSurface = Z.textHi,
            surfaceVariant = Z.cardHi,
            onSurfaceVariant = Z.textLo,
            error = Z.red,
            outline = Color(0xFF2B3452),
            surfaceContainer = Z.card,
            surfaceContainerHigh = Z.cardHi,
        )
    }
    MaterialTheme(colorScheme = scheme, typography = type, content = content)
}

package com.zenecran.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/** Palette de l'appli. */
object Z {
    val bg = Color(0xFF0B0F1A)
    val card = Color(0xFF141A2B)
    val cardHi = Color(0xFF1E2640)
    val teal = Color(0xFF3DDBB0)
    val onTeal = Color(0xFF00382A)
    val violet = Color(0xFF8B7CFF)
    val amber = Color(0xFFFFB84D)
    val red = Color(0xFFFF6B6B)
    val textHi = Color(0xFFF1F4FF)
    val textLo = Color(0xFF8E97B5)
}

private val scheme = darkColorScheme(
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

private val type = Typography(
    titleLarge = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.Bold),
    titleMedium = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.SemiBold),
    bodyMedium = TextStyle(fontSize = 14.sp),
    bodySmall = TextStyle(fontSize = 12.sp),
    labelLarge = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Medium),
)

@Composable
fun ZenTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = scheme, typography = type, content = content)
}

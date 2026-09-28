package com.eddigits.eddido.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val Brand = Color(0xFFDC4C3E)

val P1 = Color(0xFFD1453B)
val P2 = Color(0xFFEB8909)
val P3 = Color(0xFF246FE0)
val P4 = Color(0xFF8A8A8A)

val DateOverdue = Color(0xFFD1453B)
val DateToday = Color(0xFF25B84C)
val DateTomorrow = Color(0xFFE0A030)
val DateWeek = Color(0xFFA970FF)
val DateLater = Color(0xFF9E9E9E)

fun priorityColor(p: Int): Color = when (p) {
    1 -> P1
    2 -> P2
    3 -> P3
    else -> P4
}

private val Dark = darkColorScheme(
    primary = Brand,
    onPrimary = Color.White,
    secondary = Brand,
    background = Color(0xFF141414),
    onBackground = Color(0xFFEDEDED),
    surface = Color(0xFF1E1E1E),
    onSurface = Color(0xFFEDEDED),
    surfaceVariant = Color(0xFF282828),
    onSurfaceVariant = Color(0xFFA8A8A8),
    surfaceContainer = Color(0xFF1E1E1E),
    surfaceContainerHigh = Color(0xFF262626),
    surfaceContainerLow = Color(0xFF1A1A1A),
    outline = Color(0xFF3D3D3D),
    outlineVariant = Color(0xFF2E2E2E),
)

private val Light = lightColorScheme(
    primary = Brand,
    onPrimary = Color.White,
    secondary = Brand,
    background = Color(0xFFFFFFFF),
    surface = Color(0xFFFFFFFF),
    surfaceVariant = Color(0xFFF3F3F3),
    onSurfaceVariant = Color(0xFF666666),
    surfaceContainer = Color(0xFFFAFAFA),
    surfaceContainerHigh = Color(0xFFF3F3F3),
    surfaceContainerLow = Color(0xFFFCFCFC),
    outline = Color(0xFFDDDDDD),
    outlineVariant = Color(0xFFEEEEEE),
)

@Composable
fun EddiDoTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (isSystemInDarkTheme()) Dark else Light, content = content)
}

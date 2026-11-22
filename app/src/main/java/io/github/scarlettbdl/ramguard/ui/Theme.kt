package io.github.scarlettbdl.ramguard.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

object AppColors {
    val bg = Color(0xFF080B0F)
    val surface = Color(0xFF0F1620)
    val surfaceHi = Color(0xFF16202C)
    val border = Color(0xFF1E2A38)
    val accent = Color(0xFF6EE7A8)
    val accentDim = Color(0xFF2E5B45)
    val warn = Color(0xFFFFC46B)
    val danger = Color(0xFFFF7A7A)
    val blue = Color(0xFF7DB3FF)
    val text = Color(0xFFE6EDF3)
    val muted = Color(0xFF8A9BAC)
    val track = Color(0xFF131C26)
    val cached = Color(0xFF3C4A5A)
}

private val scheme = darkColorScheme(
    primary = AppColors.accent,
    onPrimary = Color(0xFF04150D),
    primaryContainer = AppColors.accentDim,
    onPrimaryContainer = AppColors.text,
    secondary = AppColors.blue,
    onSecondary = Color(0xFF04121F),
    tertiary = AppColors.warn,
    onTertiary = Color(0xFF1A1204),
    background = AppColors.bg,
    onBackground = AppColors.text,
    surface = AppColors.surface,
    onSurface = AppColors.text,
    surfaceVariant = AppColors.surfaceHi,
    onSurfaceVariant = AppColors.muted,
    outline = AppColors.border,
    outlineVariant = AppColors.border,
    error = AppColors.danger,
    onError = Color(0xFF1A0505)
)

@Composable
fun RamGuardTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = scheme, content = content)
}

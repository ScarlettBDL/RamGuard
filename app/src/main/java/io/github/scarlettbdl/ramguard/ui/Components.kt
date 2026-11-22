@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package io.github.scarlettbdl.ramguard.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.scarlettbdl.ramguard.model.PrivilegeMode
import io.github.scarlettbdl.ramguard.model.ProcState
import io.github.scarlettbdl.ramguard.model.RamStats

internal val Mono = FontFamily.Monospace

@Composable
internal fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text.uppercase(),
        modifier = modifier,
        color = AppColors.muted,
        fontSize = 10.sp,
        fontWeight = FontWeight.Medium,
        letterSpacing = 1.6.sp
    )
}

internal fun stateLabel(state: ProcState): String = when (state) {
    ProcState.FOREGROUND -> "EN USO"
    ProcState.SERVICE -> "SERVICIO"
    ProcState.BACKGROUND -> "2DO PLANO"
    ProcState.CACHED -> "EN CACHE"
    ProcState.UNKNOWN -> "ESTIMADO"
}

internal fun stateColor(state: ProcState): Color = when (state) {
    ProcState.FOREGROUND -> AppColors.accent
    ProcState.SERVICE -> AppColors.blue
    ProcState.BACKGROUND -> AppColors.warn
    ProcState.CACHED -> AppColors.muted
    ProcState.UNKNOWN -> AppColors.muted
}

internal fun modeLabel(mode: PrivilegeMode): String = when (mode) {
    PrivilegeMode.ROOT -> "ROOT"
    PrivilegeMode.SHIZUKU -> "SHIZUKU"
    PrivilegeMode.NONE -> "SIN PRIVILEGIOS"
}

@Composable
internal fun Pill(text: String, color: Color, modifier: Modifier = Modifier) {
    Text(
        text = text,
        modifier = modifier
            .clip(RoundedCornerShape(4.dp))
            .background(color.copy(alpha = 0.14f))
            .padding(horizontal = 6.dp, vertical = 2.dp),
        color = color,
        fontSize = 9.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 0.8.sp
    )
}

@Composable
internal fun RamBar(stats: RamStats?, modifier: Modifier = Modifier) {
    val total = (stats?.totalBytes ?: 0L).coerceAtLeast(1L)
    val used = ((stats?.usedBytes ?: 0L).toDouble() / total.toDouble()).toFloat().coerceIn(0f, 1f)
    val cachedRaw = ((stats?.cachedBytes ?: 0L).toDouble() / total.toDouble()).toFloat()
    val cached = cachedRaw.coerceIn(0f, (1f - used).coerceAtLeast(0f))
    val free = (1f - used - cached).coerceIn(0f, 1f)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(12.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(AppColors.track)
    ) {
        if (used > 0.002f) {
            Box(
                Modifier
                    .weight(used)
                    .fillMaxHeight()
                    .background(if (used > 0.85f) AppColors.danger else AppColors.accent)
            )
        }
        if (cached > 0.002f) {
            Box(Modifier.weight(cached).fillMaxHeight().background(AppColors.cached))
        }
        if (free > 0.002f) {
            Box(Modifier.weight(free).fillMaxHeight())
        }
    }
}

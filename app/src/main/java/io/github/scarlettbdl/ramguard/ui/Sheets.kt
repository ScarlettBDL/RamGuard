@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package io.github.scarlettbdl.ramguard.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.scarlettbdl.ramguard.model.PrivilegeMode
import io.github.scarlettbdl.ramguard.vm.DiagItem
import io.github.scarlettbdl.ramguard.vm.UiState

enum class SheetKind { NONE, SETUP, SETTINGS }

@Composable
internal fun SetupSheet(
    state: UiState,
    shizukuInstalled: Boolean,
    onDismiss: () -> Unit,
    onEnableRoot: () -> Unit,
    onDisableRoot: () -> Unit,
    onRequestShizuku: () -> Unit,
    onOpenShizuku: () -> Unit,
    onRunDiagnostics: () -> Unit,
    onCopyDiagnostics: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = AppColors.surface,
        contentColor = AppColors.text,
        dragHandle = { BottomSheetDefaults.DragHandle(color = AppColors.border) }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp)
                .padding(bottom = 32.dp)
        ) {
            Text("Privilegios", fontSize = 19.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
            Text(
                text = "Android no deja que una app normal cierre otras apps. Para el \"forzar detencion\" real hacen falta privilegios de shell.",
                color = AppColors.muted,
                fontSize = 12.sp
            )
            Spacer(Modifier.height(16.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Modo actual", color = AppColors.text, fontSize = 13.sp, modifier = Modifier.weight(1f))
                Pill(
                    text = modeLabel(state.mode),
                    color = when (state.mode) {
                        PrivilegeMode.ROOT -> AppColors.accent
                        PrivilegeMode.SHIZUKU -> AppColors.blue
                        PrivilegeMode.NONE -> AppColors.warn
                    }
                )
            }

            Spacer(Modifier.height(18.dp))
            PrivilegeBlock(
                title = "Root (Magisk / KernelSU / APatch)",
                body = "Ejecuta am force-stop como uid 0. Es el modo mas completo y no necesita PC.",
                actionLabel = if (state.mode == PrivilegeMode.ROOT) "Desactivar root" else "Activar root",
                primary = true,
                onAction = if (state.mode == PrivilegeMode.ROOT) onDisableRoot else onEnableRoot
            )

            Spacer(Modifier.height(12.dp))
            PrivilegeBlock(
                title = "Shizuku (sin root)",
                body = if (shizukuInstalled) {
                    "Abre Shizuku, inicialo con depuracion inalambrica y despues autoriza a RamGuard. Corre con permisos de shell (uid 2000)."
                } else {
                    "Shizuku no esta instalado. Se consigue en Play Store o F-Droid y se inicia con depuracion inalambrica."
                },
                actionLabel = "Autorizar Shizuku",
                primary = false,
                onAction = onRequestShizuku
            )
            if (shizukuInstalled) {
                TextButton(onClick = onOpenShizuku) {
                    Text("Abrir Shizuku", color = AppColors.accent, fontSize = 12.sp)
                }
            }

            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Diagnostico", color = AppColors.text, fontSize = 13.sp)
                    Text(
                        text = "Comprueba que los comandos con privilegios se ejecutan y devuelven su codigo de salida.",
                        color = AppColors.muted,
                        fontSize = 11.sp
                    )
                }
                OutlinedButton(
                    onClick = onRunDiagnostics,
                    enabled = !state.diagRunning,
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text(
                        text = if (state.diagRunning) "Probando..." else "Probar",
                        fontSize = 12.sp,
                        color = AppColors.text
                    )
                }
            }
            state.diagnostics?.let { items ->
                Spacer(Modifier.height(10.dp))
                DiagBox(items)
                TextButton(onClick = onCopyDiagnostics) {
                    Text("Copiar diagnostico", color = AppColors.accent, fontSize = 12.sp)
                }
            }

            Spacer(Modifier.height(18.dp))
            HorizontalDivider(color = AppColors.border)
            Spacer(Modifier.height(14.dp))
            Text(
                text = "Sin privilegios la app solo puede pedirle a Android que mate procesos en segundo plano (killBackgroundProcesses). El sistema suele ignorarlo, y no se puede leer la memoria real de otras apps.",
                color = AppColors.muted,
                fontSize = 11.sp
            )
        }
    }
}

@Composable
private fun PrivilegeBlock(
    title: String,
    body: String,
    actionLabel: String,
    primary: Boolean,
    onAction: () -> Unit
) {
    val shape = RoundedCornerShape(14.dp)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(AppColors.surfaceHi)
            .padding(14.dp)
    ) {
        Text(title, color = AppColors.text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(4.dp))
        Text(body, color = AppColors.muted, fontSize = 11.sp)
        Spacer(Modifier.height(10.dp))
        if (primary) {
            Button(
                onClick = onAction,
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = AppColors.accent,
                    contentColor = androidx.compose.ui.graphics.Color(0xFF04150D)
                )
            ) {
                Text(actionLabel, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        } else {
            OutlinedButton(onClick = onAction, shape = RoundedCornerShape(10.dp)) {
                Text(actionLabel, fontSize = 12.sp, color = AppColors.text)
            }
        }
    }
}

@Composable
internal fun SettingsSheet(
    state: UiState,
    onDismiss: () -> Unit,
    onIncludeSystem: (Boolean) -> Unit,
    onIncludeServices: (Boolean) -> Unit,
    onAutoRefresh: (Boolean) -> Unit,
    onIdleHours: (Int) -> Unit,
    onMinRss: (Int) -> Unit,
    onClearExclusions: () -> Unit,
    onOpenUsageAccess: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = AppColors.surface,
        contentColor = AppColors.text,
        dragHandle = { BottomSheetDefaults.DragHandle(color = AppColors.border) }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp)
                .padding(bottom = 32.dp)
        ) {
            Text("Ajustes", fontSize = 19.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(14.dp))

            SwitchRow(
                title = "Incluir apps del sistema",
                subtitle = "Puede romper funciones del telefono. Solo si sabes lo que haces.",
                checked = state.settings.includeSystem,
                onCheckedChange = onIncludeSystem
            )
            SwitchRow(
                title = "Incluir servicios activos",
                subtitle = "Apps con servicios en segundo plano o notificaciones persistentes.",
                checked = state.settings.includeServices,
                onCheckedChange = onIncludeServices
            )
            SwitchRow(
                title = "Actualizar automaticamente",
                subtitle = "Cada 20 segundos mientras la app este abierta.",
                checked = state.settings.autoRefresh,
                onCheckedChange = onAutoRefresh
            )

            Spacer(Modifier.height(8.dp))
            HorizontalDivider(color = AppColors.border)
            Spacer(Modifier.height(12.dp))

            SliderRow(
                title = "Ignorar procesos menores a",
                valueText = "${state.settings.minRssMb} MB",
                value = state.settings.minRssMb.toFloat(),
                range = 0f..512f,
                steps = 31,
                onCommit = onMinRss
            )
            SliderRow(
                title = "Considerar inactiva despues de",
                valueText = "${state.settings.idleHours} h",
                value = state.settings.idleHours.toFloat(),
                range = 1f..48f,
                steps = 46,
                onCommit = onIdleHours
            )

            Spacer(Modifier.height(8.dp))
            HorizontalDivider(color = AppColors.border)
            Spacer(Modifier.height(12.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Exclusiones", color = AppColors.text, fontSize = 13.sp)
                    Text(
                        text = "${state.exclusions.size} apps protegidas",
                        color = AppColors.muted,
                        fontSize = 11.sp
                    )
                }
                TextButton(onClick = onClearExclusions, enabled = state.exclusions.isNotEmpty()) {
                    Text("Borrar", color = AppColors.muted, fontSize = 12.sp)
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Acceso a datos de uso", color = AppColors.text, fontSize = 13.sp)
                    Text(
                        text = when {
                            state.mode != PrivilegeMode.NONE -> "No necesario: Shizuku/root ve los procesos"
                            state.hasUsageAccess -> "Concedido"
                            else -> "Sin conceder (solo modo limitado)"
                        },
                        color = if (state.mode != PrivilegeMode.NONE || state.hasUsageAccess) {
                            AppColors.accent
                        } else {
                            AppColors.warn
                        },
                        fontSize = 11.sp
                    )
                }
                TextButton(onClick = onOpenUsageAccess) {
                    Text("Abrir", color = AppColors.accent, fontSize = 12.sp)
                }
            }
        }
    }
}

@Composable
private fun DiagBox(items: List<DiagItem>) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(AppColors.bg)
            .padding(10.dp)
    ) {
        for (item in items) {
            Text(
                text = item.label,
                color = AppColors.accent,
                fontSize = 11.sp,
                fontFamily = Mono,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = item.detail,
                color = AppColors.text,
                fontSize = 11.sp,
                fontFamily = Mono
            )
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun SwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, color = AppColors.text, fontSize = 13.sp)
            Spacer(Modifier.height(2.dp))
            Text(subtitle, color = AppColors.muted, fontSize = 11.sp)
        }
        Spacer(Modifier.width(10.dp))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = AppColors.accent,
                checkedTrackColor = AppColors.accentDim,
                uncheckedThumbColor = AppColors.muted,
                uncheckedTrackColor = AppColors.track,
                uncheckedBorderColor = AppColors.border
            )
        )
    }
}

@Composable
private fun SliderRow(
    title: String,
    valueText: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    steps: Int,
    onCommit: (Int) -> Unit
) {
    var local by remember(value) { mutableStateOf(value) }

    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
            Text(title, color = AppColors.text, fontSize = 13.sp)
            Text(valueText, color = AppColors.accent, fontSize = 12.sp, fontWeight = FontWeight.Medium)
        }
        Slider(
            value = local,
            onValueChange = { local = it },
            valueRange = range,
            steps = steps,
            onValueChangeFinished = { onCommit(local.toInt()) }
        )
    }
}

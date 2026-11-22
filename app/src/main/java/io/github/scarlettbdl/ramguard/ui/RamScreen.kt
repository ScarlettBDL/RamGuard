@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package io.github.scarlettbdl.ramguard.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import io.github.scarlettbdl.ramguard.model.KillReport
import io.github.scarlettbdl.ramguard.model.ProcEntry
import io.github.scarlettbdl.ramguard.model.PrivilegeMode
import io.github.scarlettbdl.ramguard.util.formatBytes
import io.github.scarlettbdl.ramguard.util.formatRelative
import io.github.scarlettbdl.ramguard.vm.RamViewModel
import io.github.scarlettbdl.ramguard.vm.UiState
import kotlinx.coroutines.delay

@Composable
fun RamScreen(vm: RamViewModel) {
    val state by vm.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var sheet by remember { mutableStateOf(SheetKind.NONE) }
    var confirmKillAll by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        vm.events.collect { message -> snackbarHostState.showSnackbar(message) }
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(state.settings.autoRefresh, lifecycleOwner) {
        if (!state.settings.autoRefresh) return@LaunchedEffect
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            while (true) {
                delay(20_000L)
                if (!vm.state.value.busy) vm.refresh()
            }
        }
    }

    // Al volver de los ajustes de sistema, re-evaluar el permiso de uso.
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) vm.refreshUsageAccess()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Scaffold(
        containerColor = AppColors.bg,
        contentWindowInsets = WindowInsets.safeDrawing,
        snackbarHost = {
            SnackbarHost(snackbarHostState) { data ->
                SnackbarCard(
                    message = data.visuals.message,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                )
            }
        }
    ) { inner ->
        Box(Modifier.fillMaxSize().padding(inner)) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = 14.dp,
                    end = 14.dp,
                    top = 8.dp,
                    bottom = 130.dp
                ),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                item {
                    TopRow(
                        state = state,
                        onRefresh = vm::refresh,
                        onSettings = { sheet = SheetKind.SETTINGS }
                    )
                }
                item { RamCard(state) }

                if (state.mode == PrivilegeMode.NONE) {
                    item {
                        NoticeCard(
                            icon = Icons.Filled.Warning,
                            title = "Modo limitado",
                            text = "Android no permite que una app normal fuerce el cierre de otras apps. Activa root o Shizuku para que funcione de verdad.",
                            actionLabel = "Configurar",
                            onAction = { sheet = SheetKind.SETUP }
                        )
                    }
                }
                if (state.mode == PrivilegeMode.NONE && !state.hasUsageAccess) {
                    item {
                        NoticeCard(
                            icon = Icons.Filled.Info,
                            title = "Falta acceso de uso",
                            text = "Sin Shizuku ni root Android limita la lista. Activa Shizuku para ver todos los procesos, o concede el acceso en Ajustes > Acceso a datos de uso.",
                            actionLabel = "Abrir Ajustes",
                            onAction = { vm.openUsageAccessSettings() }
                        )
                    }
                }

                item {
                    ActionRow(
                        state = state,
                        onKillAll = { confirmKillAll = true },
                        onSelect = vm::selectCandidates
                    )
                }

                state.report?.let { report ->
                    item { ReportCard(report = report, onDismiss = vm::dismissReport) }
                }

                item { ListHeader(state) }

                items(items = state.processes, key = { it.packageName }) { entry ->
                    ProcessCard(
                        entry = entry,
                        selected = entry.packageName in state.selected,
                        onToggleSelect = { vm.toggleSelected(entry.packageName) },
                        onToggleLock = { vm.toggleExclusion(entry.packageName) },
                        onOpenInfo = { vm.openAppInfo(entry.packageName) }
                    )
                }

                if (!state.loading && state.processes.isEmpty()) {
                    item { EmptyCard(state.mode) }
                }
            }

            if (state.selected.isNotEmpty()) {
                KillBar(
                    count = state.selected.size,
                    busy = state.busy,
                    onKill = vm::forceStopSelected,
                    onClear = vm::clearSelection,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .windowInsetsPadding(WindowInsets.navigationBars)
                        .padding(horizontal = 14.dp, vertical = 12.dp)
                )
            }
        }
    }

    if (confirmKillAll) {
        AlertDialog(
            onDismissRequest = { confirmKillAll = false },
            title = { Text("Cerrar procesos", color = AppColors.text) },
            text = {
                Text(
                    text = "Se forzara la detencion de todos los procesos en segundo plano de la lista. Las apps excluidas y las protegidas no se tocan.",
                    color = AppColors.muted
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmKillAll = false
                    vm.forceStopAllCandidates()
                }) {
                    Text("Cerrar", color = AppColors.danger)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmKillAll = false }) {
                    Text("Cancelar", color = AppColors.muted)
                }
            },
            containerColor = AppColors.surface,
            titleContentColor = AppColors.text,
            textContentColor = AppColors.muted
        )
    }

    when (sheet) {
        SheetKind.SETUP -> SetupSheet(
            state = state,
            shizukuInstalled = vm.shizukuInstalled(),
            onDismiss = { sheet = SheetKind.NONE },
            onEnableRoot = vm::enableRoot,
            onDisableRoot = vm::disableRoot,
            onRequestShizuku = vm::requestShizuku,
            onOpenShizuku = vm::openShizuku,
            onRunDiagnostics = vm::runDiagnostics,
            onCopyDiagnostics = vm::copyDiagnostics
        )

        SheetKind.SETTINGS -> SettingsSheet(
            state = state,
            onDismiss = { sheet = SheetKind.NONE },
            onIncludeSystem = vm::setIncludeSystem,
            onIncludeServices = vm::setIncludeServices,
            onAutoRefresh = vm::setAutoRefresh,
            onIdleHours = vm::setIdleHours,
            onMinRss = vm::setMinRssMb,
            onClearExclusions = vm::clearExclusions,
            onOpenUsageAccess = { vm.openUsageAccessSettings() }
        )

        SheetKind.NONE -> Unit
    }
}

@Composable
private fun TopRow(state: UiState, onRefresh: () -> Unit, onSettings: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 6.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = "RAMGUARD",
                color = AppColors.text,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 4.sp
            )
            Text(
                text = "monitor de memoria del dispositivo",
                color = AppColors.muted,
                fontSize = 11.sp
            )
        }
        Pill(
            text = modeLabel(state.mode),
            color = when (state.mode) {
                PrivilegeMode.ROOT -> AppColors.accent
                PrivilegeMode.SHIZUKU -> AppColors.blue
                PrivilegeMode.NONE -> AppColors.warn
            }
        )
        IconButton(onClick = onRefresh, enabled = !state.loading) {
            Icon(Icons.Filled.Refresh, contentDescription = "Analizar", tint = AppColors.text)
        }
        IconButton(onClick = onSettings) {
            Icon(Icons.Filled.Settings, contentDescription = "Ajustes", tint = AppColors.text)
        }
    }
}

@Composable
private fun RamCard(state: UiState) {
    val ram = state.ram
    val shape = RoundedCornerShape(18.dp)
    Card(
        colors = CardDefaults.cardColors(containerColor = AppColors.surface),
        shape = shape,
        modifier = Modifier.fillMaxWidth().border(1.dp, AppColors.border, shape)
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.Bottom) {
                Column(Modifier.weight(1f)) {
                    SectionLabel("disponible")
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = formatBytes(ram?.availBytes ?: 0L),
                        color = if ((ram?.usedFraction ?: 0f) > 0.85f) AppColors.danger else AppColors.accent,
                        fontSize = 34.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = Mono
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    SectionLabel("total")
                    Text(
                        text = formatBytes(ram?.totalBytes ?: 0L),
                        color = AppColors.text,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium,
                        fontFamily = Mono
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            RamBar(ram)
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth()) {
                StatColumn("usada", formatBytes(ram?.usedBytes ?: 0L), Modifier.weight(1f))
                StatColumn("cache", formatBytes(ram?.cachedBytes ?: 0L), Modifier.weight(1f))
                StatColumn(
                    "swap",
                    ram?.swapUsedBytes?.let { formatBytes(it) } ?: "n/d",
                    Modifier.weight(1f)
                )
                StatColumn(
                    "presion",
                    if (ram?.lowMemory == true) "alta" else "normal",
                    Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun StatColumn(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(
            text = label.uppercase(),
            color = AppColors.muted,
            fontSize = 9.sp,
            letterSpacing = 1.2.sp
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = value,
            color = AppColors.text,
            fontSize = 13.sp,
            fontFamily = Mono,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun ActionRow(state: UiState, onKillAll: () -> Unit, onSelect: () -> Unit) {
    val hasTargets = state.processes.any { !it.isProtected && !it.isExcluded }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Button(
            onClick = onKillAll,
            enabled = !state.busy && hasTargets,
            modifier = Modifier.weight(1f).height(48.dp),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = AppColors.accent,
                contentColor = Color(0xFF04150D)
            )
        ) {
            Text(
                text = if (state.busy) "Cerrando..." else "Liberar todo",
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp
            )
        }
        OutlinedButton(
            onClick = onSelect,
            enabled = !state.busy,
            modifier = Modifier.height(48.dp),
            shape = RoundedCornerShape(12.dp),
            border = BorderStroke(1.dp, AppColors.border)
        ) {
            Text("Elegir", color = AppColors.text, fontSize = 14.sp)
        }
    }
}

@Composable
private fun ListHeader(state: UiState) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        SectionLabel("procesos", Modifier.weight(1f))
        Text(
            text = "${state.processes.size} · ${formatBytes(state.processes.sumOf { it.rssBytes })}",
            color = AppColors.muted,
            fontSize = 10.sp,
            fontFamily = Mono
        )
    }
}

@Composable
private fun ProcessCard(
    entry: ProcEntry,
    selected: Boolean,
    onToggleSelect: () -> Unit,
    onToggleLock: () -> Unit,
    onOpenInfo: () -> Unit
) {
    val shape = RoundedCornerShape(14.dp)
    val borderColor = when {
        entry.isExcluded -> AppColors.accentDim
        selected -> AppColors.accent
        else -> AppColors.border
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(if (selected) AppColors.surfaceHi else AppColors.surface)
            .border(1.dp, borderColor, shape)
            .clickable(enabled = entry.canKill && !entry.isExcluded) { onToggleSelect() }
            .padding(start = 2.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(
            checked = selected,
            onCheckedChange = { onToggleSelect() },
            enabled = entry.canKill && !entry.isExcluded,
            colors = CheckboxDefaults.colors(
                checkedColor = AppColors.accent,
                uncheckedColor = AppColors.muted,
                checkmarkColor = Color(0xFF04150D)
            )
        )
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = entry.label,
                    color = AppColors.text,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                if (entry.isSystemApp) {
                    Spacer(Modifier.width(6.dp))
                    Pill("SISTEMA", AppColors.muted)
                }
            }
            Spacer(Modifier.height(2.dp))
            Text(
                text = entry.packageName,
                color = AppColors.muted,
                fontSize = 10.sp,
                fontFamily = Mono,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Pill(stateLabel(entry.state), stateColor(entry.state))
                Spacer(Modifier.width(6.dp))
                Text(formatRelative(entry.lastUsedMs), color = AppColors.muted, fontSize = 10.sp)
                if (entry.isProtected) {
                    Spacer(Modifier.width(6.dp))
                    Text("protegida", color = AppColors.warn, fontSize = 10.sp)
                }
            }
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = if (entry.hasSize) formatBytes(entry.rssBytes) else "—",
                color = AppColors.text,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                fontFamily = Mono
            )
            if (entry.oomAdj != null) {
                Text(
                    text = "adj ${entry.oomAdj}",
                    color = AppColors.muted,
                    fontSize = 9.sp,
                    fontFamily = Mono
                )
            }
        }
        IconButton(onClick = onToggleLock, enabled = entry.canKill) {
            Icon(
                Icons.Filled.Lock,
                contentDescription = "Excluir",
                tint = if (entry.isExcluded) AppColors.accent else AppColors.border
            )
        }
        IconButton(onClick = onOpenInfo) {
            Icon(Icons.Filled.MoreVert, contentDescription = "Info", tint = AppColors.muted)
        }
    }
}

@Composable
private fun KillBar(
    count: Int,
    busy: Boolean,
    onKill: () -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(16.dp)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(AppColors.surfaceHi)
            .border(1.dp, AppColors.accentDim, shape)
            .padding(start = 14.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = "$count seleccionados",
                color = AppColors.text,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium
            )
            Text("se forzara la detencion", color = AppColors.muted, fontSize = 10.sp)
        }
        TextButton(onClick = onClear) {
            Text("Limpiar", color = AppColors.muted)
        }
        Button(
            onClick = onKill,
            enabled = !busy,
            shape = RoundedCornerShape(10.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = AppColors.danger,
                contentColor = Color(0xFF1A0505)
            )
        ) {
            Text(
                text = if (busy) "..." else "Detener",
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun NoticeCard(
    icon: ImageVector,
    title: String,
    text: String,
    actionLabel: String,
    onAction: () -> Unit
) {
    val shape = RoundedCornerShape(14.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(AppColors.surface)
            .border(1.dp, AppColors.border, shape)
            .padding(14.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = AppColors.warn,
            modifier = Modifier.size(18.dp)
        )
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = AppColors.text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(2.dp))
            Text(text, color = AppColors.muted, fontSize = 11.sp)
            TextButton(onClick = onAction, contentPadding = PaddingValues(0.dp)) {
                Text(actionLabel, color = AppColors.accent, fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun ReportCard(report: KillReport, onDismiss: () -> Unit) {
    val shape = RoundedCornerShape(14.dp)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(AppColors.surface)
            .border(1.dp, AppColors.border, shape)
            .padding(start = 14.dp, end = 6.dp, top = 8.dp, bottom = 12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "Resultado",
                color = AppColors.text,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = onDismiss) {
                Icon(Icons.Filled.Close, contentDescription = "Cerrar", tint = AppColors.muted)
            }
        }
        if (report.mode == PrivilegeMode.NONE) {
            Text(
                text = "Intentos: ${report.requested}. Android puede ignorar el cierre en segundo plano.",
                color = AppColors.warn,
                fontSize = 11.sp
            )
        } else {
            Text(
                text = "${report.succeeded} cerradas · ${report.failed} fallidas",
                color = AppColors.muted,
                fontSize = 11.sp
            )
            if (report.estimatedFreedBytes > 0L) {
                Text(
                    text = "~${formatBytes(report.estimatedFreedBytes)} liberados (estimado)",
                    color = AppColors.accent,
                    fontSize = 11.sp
                )
            }
            if (report.measuredFreedBytes > 0L) {
                Text(
                    text = "${formatBytes(report.measuredFreedBytes)} recuperados segun el sistema",
                    color = AppColors.muted,
                    fontSize = 11.sp
                )
            }
            if (report.failures.isNotEmpty()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "Fallaron: ${report.failures.take(4).joinToString(", ")}",
                    color = AppColors.warn,
                    fontSize = 10.sp
                )
            }
        }
    }
}

@Composable
private fun EmptyCard(mode: PrivilegeMode) {
    val shape = RoundedCornerShape(14.dp)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(AppColors.surface)
            .border(1.dp, AppColors.border, shape)
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = Icons.Filled.CheckCircle,
            contentDescription = null,
            tint = AppColors.accent,
            modifier = Modifier.size(28.dp)
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "Nada para cerrar",
            color = AppColors.text,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = if (mode == PrivilegeMode.NONE) {
                "En modo limitado la lista es aproximada. Con root o Shizuku ves todos los procesos reales."
            } else {
                "No hay procesos, o el shell no devolvio salida. Revisa Configurar > Diagnostico y los filtros."
            },
            color = AppColors.muted,
            fontSize = 11.sp
        )
    }
}

@Composable
internal fun SnackbarCard(message: String, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(10.dp)
    Text(
        text = message,
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(AppColors.surfaceHi)
            .border(1.dp, AppColors.border, shape)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        color = AppColors.text,
        fontSize = 12.sp
    )
}

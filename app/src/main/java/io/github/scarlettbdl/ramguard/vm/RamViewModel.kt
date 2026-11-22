package io.github.scarlettbdl.ramguard.vm

import android.app.Application
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings as AndroidSettings
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.scarlettbdl.ramguard.RamGuardApp
import io.github.scarlettbdl.ramguard.model.KillReport
import io.github.scarlettbdl.ramguard.model.PrivilegeMode
import io.github.scarlettbdl.ramguard.model.ProcEntry
import io.github.scarlettbdl.ramguard.model.ProcState
import io.github.scarlettbdl.ramguard.model.RamStats
import io.github.scarlettbdl.ramguard.model.Settings
import io.github.scarlettbdl.ramguard.util.formatBytes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import rikka.shizuku.Shizuku

data class UiState(
    val loading: Boolean = true,
    val busy: Boolean = false,
    val ram: RamStats? = null,
    val processes: List<ProcEntry> = emptyList(),
    val mode: PrivilegeMode = PrivilegeMode.NONE,
    val exclusions: Set<String> = emptySet(),
    val selected: Set<String> = emptySet(),
    val settings: Settings = Settings(),
    val report: KillReport? = null,
    val hasUsageAccess: Boolean = false,
    val diagnostics: List<DiagItem>? = null,
    val diagRunning: Boolean = false
)

/** Resultado de una prueba de los comandos con privilegios. */
data class DiagItem(val label: String, val detail: String)

class RamViewModel(application: Application) : AndroidViewModel(application) {

    private val container = RamGuardApp.container(application)

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    private val _events = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val events: SharedFlow<String> = _events.asSharedFlow()

    private var refreshJob: Job? = null

    private data class ScanResult(
        val ram: RamStats,
        val processes: List<ProcEntry>,
        val mode: PrivilegeMode,
        val usageAccess: Boolean
    )

    init {
        viewModelScope.launch {
            container.exclusions.excluded.collect { set ->
                _state.update { current ->
                    current.copy(
                        exclusions = set,
                        processes = current.processes.map {
                            it.copy(isExcluded = it.packageName in set)
                        }
                    )
                }
            }
        }
        viewModelScope.launch {
            container.prefs.settings.collect { settings ->
                _state.update { it.copy(settings = settings) }
            }
        }
        refresh()
    }

    fun refresh() {
        if (refreshJob?.isActive == true) return
        refreshJob = viewModelScope.launch {
            _state.update { it.copy(loading = true) }
            val result = withContext(Dispatchers.IO) {
                container.privileges.refresh()
                val shell = container.privileges.currentShell()
                val settings = container.prefs.settings.value
                val exclusions = container.exclusions.excluded.value
                ScanResult(
                    ram = container.memoryReader.read(),
                    processes = container.scanner.scan(shell, exclusions, settings),
                    mode = container.privileges.mode.value,
                    usageAccess = container.scanner.hasUsageAccess()
                )
            }
            _state.update { current ->
                val alive = result.processes.map { it.packageName }.toSet()
                current.copy(
                    loading = false,
                    ram = result.ram,
                    processes = result.processes,
                    mode = result.mode,
                    hasUsageAccess = result.usageAccess,
                    selected = current.selected.intersect(alive)
                )
            }
        }
    }

    /** Re-evalua el estado del permiso de uso (p.ej. al volver de ajustes). */
    fun refreshUsageAccess() {
        viewModelScope.launch {
            val allowed = withContext(Dispatchers.IO) { container.scanner.hasUsageAccess() }
            _state.update { it.copy(hasUsageAccess = allowed) }
        }
    }

    fun toggleSelected(pkg: String) {
        _state.update { current ->
            val next = current.selected.toMutableSet()
            if (!next.add(pkg)) next.remove(pkg)
            current.copy(selected = next)
        }
    }

    fun clearSelection() = _state.update { it.copy(selected = emptySet()) }

    fun selectCandidates() {
        val current = _state.value
        val targets = current.processes
            .filter { isCandidate(it, current.settings) }
            .map { it.packageName }
            .toSet()
        if (targets.isEmpty()) {
            toast("No hay candidatos para cerrar")
            return
        }
        _state.update { it.copy(selected = targets) }
    }

    fun toggleExclusion(pkg: String) {
        val label = _state.value.processes.firstOrNull { it.packageName == pkg }?.label ?: pkg
        val nowExcluded = container.exclusions.toggle(pkg)
        toast(if (nowExcluded) "$label queda protegida" else "$label vuelve a ser candidata")
    }

    fun forceStopSelected() {
        val current = _state.value
        val targets = current.processes.filter {
            it.packageName in current.selected && !it.isProtected
        }
        if (targets.isEmpty()) {
            toast("Selecciona al menos una app")
            return
        }
        executeKill(targets)
    }

    fun forceStopAllCandidates() {
        val current = _state.value
        val targets = current.processes.filter { isCandidate(it, current.settings) }
        if (targets.isEmpty()) {
            toast("No hay procesos para cerrar")
            return
        }
        executeKill(targets)
    }

    private fun executeKill(targets: List<ProcEntry>) {
        viewModelScope.launch {
            _state.update { it.copy(busy = true) }
            val shell = container.privileges.currentShell()
            val report = if (shell.mode == PrivilegeMode.NONE) {
                container.killer.softKill(targets)
            } else {
                container.killer.forceStop(shell, targets)
            }
            _state.update { it.copy(busy = false, report = report, selected = emptySet()) }
            toast(summary(report))
            refresh()
        }
    }

    private fun summary(report: KillReport): String = when (report.mode) {
        PrivilegeMode.NONE ->
            "Best effort: ${report.succeeded}/${report.requested}. Android bloqueo el resto."
        else ->
            "Cerradas ${report.succeeded}/${report.requested} · ~${formatBytes(report.estimatedFreedBytes)}"
    }

    fun dismissReport() = _state.update { it.copy(report = null) }

    /** Corre comandos de prueba y muestra codigo de salida y salida cruda. */
    fun runDiagnostics() {
        if (_state.value.diagRunning) return
        viewModelScope.launch {
            _state.update { it.copy(diagRunning = true, diagnostics = null) }
            val items = withContext(Dispatchers.IO) {
                val shell = container.privileges.currentShell()
                val out = ArrayList<DiagItem>(DIAGNOSTICS.size + 2)
                out += DiagItem("modo", shell.mode.name.lowercase())
                out += DiagItem("shizuku", shizukuInfo())
                for ((label, command) in DIAGNOSTICS) {
                    val result = shell.exec(command, 10_000L)
                    out += DiagItem(
                        label,
                        "exit=${result.exitCode}\n" + result.combined.ifBlank { "(sin salida)" }
                    )
                }
                out
            }
            _state.update { it.copy(diagRunning = false, diagnostics = items) }
        }
    }

    fun copyDiagnostics() {
        val items = _state.value.diagnostics ?: return
        val text = items.joinToString("\n\n") { "${it.label}:\n${it.detail}" }
        runCatching {
            val clipboard = getApplication<Application>()
                .getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText("RamGuard diagnostico", text))
            toast("Diagnostico copiado")
        }.onFailure { toast("No se pudo copiar") }
    }

    private fun shizukuInfo(): String = runCatching {
        if (!Shizuku.pingBinder()) return@runCatching "binder no disponible"
        val granted = Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
        "uid=${Shizuku.getUid()} · v${Shizuku.getVersion()} · permiso=${if (granted) "concedido" else "denegado"}"
    }.getOrDefault("no disponible")

    fun setIncludeSystem(value: Boolean) {
        container.prefs.setIncludeSystem(value)
        refresh()
    }

    fun setIncludeServices(value: Boolean) {
        container.prefs.setIncludeServices(value)
        refresh()
    }

    fun setAutoRefresh(value: Boolean) = container.prefs.setAutoRefresh(value)

    fun setIdleHours(value: Int) {
        container.prefs.setIdleHours(value)
        refresh()
    }

    fun setMinRssMb(value: Int) {
        container.prefs.setMinRssMb(value)
        refresh()
    }

    fun clearExclusions() {
        container.exclusions.clear()
        toast("Exclusiones borradas")
    }

    fun enableRoot() {
        viewModelScope.launch {
            _state.update { it.copy(busy = true) }
            val ok = container.privileges.enableRoot()
            _state.update { it.copy(busy = false, mode = container.privileges.mode.value) }
            toast(if (ok) "Root activo" else "No se pudo obtener root")
            if (ok) refresh()
        }
    }

    fun disableRoot() {
        container.privileges.disableRoot()
        _state.update { it.copy(mode = container.privileges.mode.value) }
        refresh()
    }

    fun requestShizuku() {
        val running = runCatching { Shizuku.pingBinder() }.getOrDefault(false)
        if (!running) {
            toast("Shizuku no esta corriendo. Inicialo y reintenta.")
            return
        }
        if (runCatching { Shizuku.isPreV11() }.getOrDefault(false)) {
            toast("Actualiza Shizuku a la version 11 o superior")
            return
        }
        val granted = runCatching { Shizuku.checkSelfPermission() }
            .getOrDefault(PackageManager.PERMISSION_DENIED) == PackageManager.PERMISSION_GRANTED
        if (granted) {
            container.privileges.refresh()
            _state.update { it.copy(mode = container.privileges.mode.value) }
            toast("Shizuku ya esta autorizado")
            refresh()
            return
        }
        runCatching { Shizuku.requestPermission(SHIZUKU_REQUEST_CODE) }
            .onFailure { toast("No se pudo pedir permiso a Shizuku") }
    }

    fun onShizukuPermissionResult(granted: Boolean) {
        toast(if (granted) "Shizuku autorizado" else "Permiso de Shizuku denegado")
        if (granted) refresh()
    }

    fun onShizukuBinderChanged() {
        container.privileges.refresh()
        _state.update { it.copy(mode = container.privileges.mode.value) }
        refresh()
    }

    fun shizukuInstalled(): Boolean = runCatching {
        getApplication<Application>().packageManager
            .getLaunchIntentForPackage(SHIZUKU_PACKAGE) != null
    }.getOrDefault(false)

    fun openShizuku() {
        val pm = getApplication<Application>().packageManager
        val intent = pm.getLaunchIntentForPackage(SHIZUKU_PACKAGE)
        if (intent == null) {
            toast("Shizuku no esta instalado")
            return
        }
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { getApplication<Application>().startActivity(intent) }
            .onFailure { toast("No se pudo abrir Shizuku") }
    }

    fun openUsageAccessSettings(pkg: String? = null) {
        val target = pkg ?: getApplication<Application>().packageName
        val intent = Intent(AndroidSettings.ACTION_USAGE_ACCESS_SETTINGS)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            .setData(Uri.fromParts("package", target, null))
        runCatching { getApplication<Application>().startActivity(intent) }
            .onFailure { toast("No se pudo abrir Ajustes") }
    }

    fun openAppInfo(pkg: String) {
        val intent = Intent(
            AndroidSettings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.fromParts("package", pkg, null)
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { getApplication<Application>().startActivity(intent) }
            .onFailure { toast("No se pudo abrir la info de la app") }
    }

    fun toast(text: String) {
        _events.tryEmit(text)
    }

    private fun isCandidate(entry: ProcEntry, settings: Settings): Boolean {
        if (entry.isProtected || entry.isExcluded) return false
        return when (entry.state) {
            ProcState.CACHED -> true
            ProcState.BACKGROUND -> true
            ProcState.SERVICE -> settings.includeServices
            ProcState.UNKNOWN -> true
            ProcState.FOREGROUND -> false
        }
    }

    companion object {
        const val SHIZUKU_REQUEST_CODE = 4213
        const val SHIZUKU_PACKAGE = "moe.shizuku.privileged.api"

        private val DIAGNOSTICS = listOf(
            "id" to "id",
            "am" to "am get-current-user",
            "ps" to "ps -A -o PID,RSS,ARGS | head -n 3",
            "pm" to "pm list packages | head -n 3",
            "echo" to "echo ramguard-ok"
        )
    }
}

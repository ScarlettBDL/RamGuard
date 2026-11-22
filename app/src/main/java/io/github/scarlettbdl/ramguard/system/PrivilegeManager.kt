package io.github.scarlettbdl.ramguard.system

import io.github.scarlettbdl.ramguard.data.PrefsStore
import io.github.scarlettbdl.ramguard.model.PrivilegeMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

class PrivilegeManager(
    private val prefs: PrefsStore,
    private val rootShell: RootShell,
    private val shizukuShell: ShizukuShell
) {
    private val _mode = MutableStateFlow(PrivilegeMode.NONE)
    val mode: StateFlow<PrivilegeMode> = _mode.asStateFlow()

    /**
     * No se consulta root automaticamente para no disparar el pedido de
     * superusuario en cada arranque: solo si el usuario lo activo antes.
     */
    fun refresh() {
        val next = when {
            prefs.rootOptIn && rootShell.isAvailable() -> PrivilegeMode.ROOT
            shizukuShell.isAvailable() -> PrivilegeMode.SHIZUKU
            else -> PrivilegeMode.NONE
        }
        _mode.value = next
    }

    fun currentShell(): Shell = when (_mode.value) {
        PrivilegeMode.ROOT -> rootShell
        PrivilegeMode.SHIZUKU -> shizukuShell
        PrivilegeMode.NONE -> NoShell
    }

    suspend fun enableRoot(): Boolean = withContext(Dispatchers.IO) {
        val available = rootShell.isAvailable(force = true)
        if (available) {
            prefs.setRootOptIn(true)
        }
        refresh()
        available
    }

    fun disableRoot() {
        prefs.setRootOptIn(false)
        refresh()
    }
}

package io.github.scarlettbdl.ramguard.model

/** Como se ejecutan los comandos con privilegios. */
enum class PrivilegeMode {
    /** App normal: Android no permite forzar detenciones ajenas. */
    NONE,

    /** `su` disponible (Magisk / KernelSU / APatch). */
    ROOT,

    /** Shizuku activo y autorizado: corre como uid 2000 (shell). */
    SHIZUKU
}

/** Importancia estimada de un proceso, derivada de `oom_score_adj`. */
enum class ProcState {
    FOREGROUND,
    SERVICE,
    BACKGROUND,
    CACHED,
    UNKNOWN
}

data class RamStats(
    val totalBytes: Long,
    val availBytes: Long,
    val thresholdBytes: Long,
    val lowMemory: Boolean,
    val cachedBytes: Long?,
    val swapTotalBytes: Long?,
    val swapFreeBytes: Long?
) {
    val usedBytes: Long get() = (totalBytes - availBytes).coerceAtLeast(0L)

    val usedFraction: Float
        get() = if (totalBytes <= 0L) {
            0f
        } else {
            (usedBytes.toDouble() / totalBytes.toDouble()).toFloat().coerceIn(0f, 1f)
        }

    val swapUsedBytes: Long?
        get() {
            val total = swapTotalBytes ?: return null
            val free = swapFreeBytes ?: return null
            return (total - free).coerceAtLeast(0L)
        }
}

data class ProcEntry(
    val packageName: String,
    val label: String,
    val pids: List<Int>,
    val processNames: List<String>,
    val rssBytes: Long,
    val oomAdj: Int?,
    val state: ProcState,
    val lastUsedMs: Long?,
    val isSystemApp: Boolean,
    /** Nunca se puede tocar: sistema, launcher, teclado, accesibilidad, etc. */
    val isProtected: Boolean,
    /** Esta en la lista de exclusiones del usuario. */
    val isExcluded: Boolean
) {
    val hasSize: Boolean get() = rssBytes > 0L
    val canKill: Boolean get() = !isProtected
}

data class KillReport(
    val mode: PrivilegeMode,
    val requested: Int,
    val succeeded: Int,
    val failed: Int,
    val estimatedFreedBytes: Long,
    val measuredFreedBytes: Long,
    val failures: List<String>
)

data class Settings(
    val includeSystem: Boolean = false,
    val includeServices: Boolean = false,
    val idleHours: Int = 6,
    val autoRefresh: Boolean = true,
    val rootOptIn: Boolean = false,
    val minRssMb: Int = 0
)

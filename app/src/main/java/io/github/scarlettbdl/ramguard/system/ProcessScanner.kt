package io.github.scarlettbdl.ramguard.system

import android.app.ActivityManager
import android.app.AppOpsManager
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import android.os.Process
import io.github.scarlettbdl.ramguard.model.ProcEntry
import io.github.scarlettbdl.ramguard.model.ProcState
import io.github.scarlettbdl.ramguard.model.PrivilegeMode
import io.github.scarlettbdl.ramguard.model.Settings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

class ProcessScanner(context: Context) {

    private val appContext = context.applicationContext
    private val packageManager: PackageManager = appContext.packageManager
    private val activityManager =
        appContext.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
    private val usageStatsManager =
        appContext.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
    private val safety = Safety(appContext)

    private val labelCache = ConcurrentHashMap<String, String>()
    private val systemCache = ConcurrentHashMap<String, Boolean>()

    private val oomCommand: String =
        "for p in /proc/[0-9]*; do a=\$(cat \"\$p/oom_score_adj\" 2>/dev/null) && " +
            "echo \"\${p#/proc/} \$a\"; done"

    /**
     * Solo se usa en el modo sin privilegios. Con root/Shizuku la lista de
     * procesos y de paquetes instalados sale del shell, no de esta API.
     */
    fun hasUsageAccess(): Boolean = runCatching {
        val appOps = appContext.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            appOps.unsafeCheckOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                appContext.packageName
            )
        } else {
            @Suppress("DEPRECATION")
            appOps.checkOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                appContext.packageName
            )
        }
        mode == AppOpsManager.MODE_ALLOWED
    }.getOrDefault(false)

    suspend fun scan(
        shell: Shell,
        exclusions: Set<String>,
        settings: Settings
    ): List<ProcEntry> = withContext(Dispatchers.IO) {
        val entries = if (shell.mode == PrivilegeMode.NONE) {
            scanWithoutPrivileges(lastUsedMap(), exclusions, settings)
        } else {
            scanWithPrivileges(shell, exclusions, settings)
        }
        entries.sortedWith(
            compareByDescending<ProcEntry> { it.rssBytes }.thenBy { it.label.lowercase() }
        )
    }

    /**
     * Modo root/Shizuku: la fuente de verdad son los comandos del shell.
     *
     * No se usa UsageStatsManager (permiso de "acceso a datos de uso", que
     * devuelve apenas unas pocas apps) ni PackageManager.getPackageInfo()
     * (limitado por QUERY_ALL_PACKAGES) para decidir qué existe. En su lugar:
     *   - `ps` para los procesos y su RSS,
     *   - `/proc/<pid>/oom_score_adj` para la clasificación,
     *   - `pm list packages` para saber qué paquetes están instalados y
     *     cuáles son del sistema.
     *
     * Shizuku corre como uid 2000 (shell), así que tiene acceso a todo esto
     * sin permisos especiales de Android.
     */
    private fun scanWithPrivileges(
        shell: Shell,
        exclusions: Set<String>,
        settings: Settings
    ): List<ProcEntry> {
        val ps = shell.exec("ps -A -o PID,RSS,ARGS", 20_000L)
        var raw = PsParser.parse(ps.stdout)
        if (raw.isEmpty()) {
            raw = PsParser.parse(shell.exec("ps -A", 20_000L).stdout)
        }
        // Si el shell no devuelve procesos, no caemos al modo limitado: esa
        // lista es la que oculta casi todas las apps. Mejor mostrar nada y
        // dejar que el diagnostico explique que Shizuku no corrio `ps`.
        if (raw.isEmpty()) return emptyList()

        val oomAdj = PsParser.parseOomAdj(shell.exec(oomCommand, 20_000L).stdout)
        val installed = PsParser.parsePackages(shell.exec("pm list packages", 20_000L).stdout)
            .toSet()
        val systemPackages = PsParser.parsePackages(shell.exec("pm list packages -s", 20_000L).stdout)
            .toSet()
            .takeIf { it.isNotEmpty() }

        val grouped = LinkedHashMap<String, MutableList<PsParser.RawProcess>>()
        for (process in raw) {
            val pkg = PsParser.packageOf(process.name) ?: continue
            grouped.getOrPut(pkg) { ArrayList() }.add(process)
        }

        val result = ArrayList<ProcEntry>(grouped.size)
        for ((pkg, processes) in grouped) {
            if (pkg == appContext.packageName) continue
            // Solo filtramos por instalados si el shell contesto; si `pm`
            // fallo, mostramos igual todo lo que `ps` encontro.
            if (installed.isNotEmpty() && pkg !in installed) continue

            val rssBytes = processes.sumOf { it.rssKb } * 1024L
            if (settings.minRssMb > 0 && rssBytes < settings.minRssMb.toLong() * 1024L * 1024L) {
                continue
            }

            // El proceso mas importante define la importancia del paquete.
            val adj = processes.mapNotNull { oomAdj[it.pid] }.minOrNull()
            val state = classify(adj)
            val systemApp = systemPackages?.let { pkg in it } ?: isSystem(pkg)

            if (state == ProcState.FOREGROUND) continue
            if (state == ProcState.SERVICE && !settings.includeServices) continue
            if (systemApp && !settings.includeSystem) continue

            result.add(
                ProcEntry(
                    packageName = pkg,
                    label = labelOf(pkg),
                    pids = processes.map { it.pid },
                    processNames = processes.map { it.name },
                    rssBytes = rssBytes,
                    oomAdj = adj,
                    state = state,
                    lastUsedMs = null,
                    isSystemApp = systemApp,
                    isProtected = safety.isProtected(pkg),
                    isExcluded = pkg in exclusions
                )
            )
        }
        return result
    }

    /**
     * Sin privilegios Android no deja ver procesos ni memoria de otras apps.
     * Se ofrece una lista aproximada de apps usadas recientemente para poder
     * matar sus procesos en segundo plano con killBackgroundProcesses().
     */
    private fun scanWithoutPrivileges(
        lastUsed: Map<String, Long>,
        exclusions: Set<String>,
        settings: Settings
    ): List<ProcEntry> {
        val now = System.currentTimeMillis()
        val cutoff = now - settings.idleHours * 3_600_000L
        val candidates = LinkedHashMap<String, Long>()

        runCatching { activityManager.runningAppProcesses }.getOrNull()?.forEach { info ->
            val pkg = PsParser.packageOf(info.processName) ?: return@forEach
            candidates.putIfAbsent(pkg, lastUsed[pkg] ?: 0L)
        }
        for ((pkg, last) in lastUsed) {
            if (last >= cutoff) candidates.putIfAbsent(pkg, last)
        }

        val result = ArrayList<ProcEntry>()
        for ((pkg, last) in candidates) {
            if (pkg == appContext.packageName) continue
            if (!isInstalled(pkg)) continue
            if (runCatching { packageManager.getLaunchIntentForPackage(pkg) }.getOrNull() == null) continue

            val systemApp = isSystem(pkg)
            if (systemApp && !settings.includeSystem) continue

            result.add(
                ProcEntry(
                    packageName = pkg,
                    label = labelOf(pkg),
                    pids = emptyList(),
                    processNames = emptyList(),
                    rssBytes = 0L,
                    oomAdj = null,
                    state = if (last >= cutoff) ProcState.BACKGROUND else ProcState.CACHED,
                    lastUsedMs = last.takeIf { it > 0L },
                    isSystemApp = systemApp,
                    isProtected = safety.isProtected(pkg),
                    isExcluded = pkg in exclusions
                )
            )
        }
        return result
    }

    /** Clasificacion basada unicamente en oom_score_adj (lo que usa Android). */
    private fun classify(adj: Int?): ProcState = when {
        adj == null -> ProcState.UNKNOWN
        adj < 200 -> ProcState.FOREGROUND
        adj < 300 -> ProcState.SERVICE
        adj < 900 -> ProcState.BACKGROUND
        else -> ProcState.CACHED
    }

    private fun lastUsedMap(days: Int = 7): Map<String, Long> {
        val manager = usageStatsManager ?: return emptyMap()
        val now = System.currentTimeMillis()
        val stats = runCatching {
            manager.queryUsageStats(
                UsageStatsManager.INTERVAL_DAILY,
                now - days * 86_400_000L,
                now
            )
        }.getOrNull() ?: return emptyMap()

        val map = HashMap<String, Long>()
        for (stat in stats) {
            if (stat.lastTimeUsed <= 0L) continue
            val previous = map[stat.packageName]
            if (previous == null || stat.lastTimeUsed > previous) {
                map[stat.packageName] = stat.lastTimeUsed
            }
        }
        return map
    }

    private fun labelOf(pkg: String): String = labelCache.getOrPut(pkg) {
        runCatching {
            val info = packageManager.getApplicationInfo(pkg, 0)
            packageManager.getApplicationLabel(info).toString()
        }.getOrDefault(pkg)
    }

    private fun isSystem(pkg: String): Boolean = systemCache.getOrPut(pkg) {
        runCatching {
            val info = packageManager.getApplicationInfo(pkg, 0)
            (info.flags and ApplicationInfo.FLAG_SYSTEM) != 0
        }.getOrDefault(false)
    }

    private fun isInstalled(pkg: String): Boolean = runCatching {
        packageManager.getPackageInfo(pkg, 0) != null
    }.getOrDefault(false)
}

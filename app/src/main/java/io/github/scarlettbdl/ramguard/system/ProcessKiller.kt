package io.github.scarlettbdl.ramguard.system

import android.app.ActivityManager
import android.content.Context
import io.github.scarlettbdl.ramguard.model.KillReport
import io.github.scarlettbdl.ramguard.model.PrivilegeMode
import io.github.scarlettbdl.ramguard.model.ProcEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

class ProcessKiller(context: Context, private val memoryReader: MemoryReader) {

    private val activityManager =
        context.applicationContext.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager

    /** Forzar detencion real: `am force-stop` como shell (Shizuku) o root. */
    suspend fun forceStop(shell: Shell, entries: List<ProcEntry>): KillReport =
        withContext(Dispatchers.IO) {
            val before = memoryReader.read().availBytes
            var succeeded = 0
            var failed = 0
            var estimated = 0L
            val failures = ArrayList<String>()

            for (entry in entries) {
                if (entry.isProtected) {
                    failed++
                    failures.add("${entry.label}: protegida")
                    continue
                }
                if (!isSafePackageName(entry.packageName)) {
                    failed++
                    failures.add("${entry.label}: nombre invalido")
                    continue
                }

                val result = shell.exec("am force-stop ${entry.packageName}", 15_000L)
                val text = result.combined
                val success = result.ok &&
                    !text.contains("Error", ignoreCase = true) &&
                    !text.contains("Exception", ignoreCase = true) &&
                    !text.contains("not found", ignoreCase = true)

                if (success) {
                    succeeded++
                    estimated += entry.rssBytes
                } else {
                    failed++
                    val reason = result.firstLine?.take(70)
                    failures.add(
                        if (reason != null) "${entry.label}: $reason"
                        else "${entry.label} (exit ${result.exitCode})"
                    )
                }
            }

            if (succeeded > 0) delay(1_500L)
            val after = memoryReader.read().availBytes

            KillReport(
                mode = shell.mode,
                requested = entries.size,
                succeeded = succeeded,
                failed = failed,
                estimatedFreedBytes = estimated,
                measuredFreedBytes = (after - before).coerceAtLeast(0L),
                failures = failures
            )
        }

    /**
     * Sin privilegios solo queda `killBackgroundProcesses`, que Android aplica
     * a procesos en segundo plano y puede ignorar. Se reporta como best effort.
     */
    suspend fun softKill(entries: List<ProcEntry>): KillReport = withContext(Dispatchers.IO) {
        val before = memoryReader.read().availBytes
        var succeeded = 0
        var failed = 0
        val failures = ArrayList<String>()

        for (entry in entries) {
            if (entry.isProtected) {
                failed++
                continue
            }
            val attempted = runCatching {
                activityManager.killBackgroundProcesses(entry.packageName)
                true
            }.getOrDefault(false)
            if (attempted) succeeded++ else failures.add(entry.label)
        }

        if (succeeded > 0) delay(1_200L)
        val after = memoryReader.read().availBytes

        KillReport(
            mode = PrivilegeMode.NONE,
            requested = entries.size,
            succeeded = succeeded,
            failed = failed,
            estimatedFreedBytes = 0L,
            measuredFreedBytes = (after - before).coerceAtLeast(0L),
            failures = failures
        )
    }
}

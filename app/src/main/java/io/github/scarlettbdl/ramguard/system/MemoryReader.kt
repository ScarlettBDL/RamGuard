package io.github.scarlettbdl.ramguard.system

import android.app.ActivityManager
import android.content.Context
import io.github.scarlettbdl.ramguard.model.RamStats
import java.io.File

class MemoryReader(context: Context) {

    private val activityManager =
        context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager

    fun read(): RamStats {
        val info = ActivityManager.MemoryInfo()
        runCatching { activityManager.getMemoryInfo(info) }

        val meminfo = readProcMeminfo()

        return RamStats(
            totalBytes = info.totalMem,
            availBytes = info.availMem,
            thresholdBytes = info.threshold,
            lowMemory = info.lowMemory,
            cachedBytes = meminfo["Cached"]?.times(1024L),
            swapTotalBytes = meminfo["SwapTotal"]?.times(1024L),
            swapFreeBytes = meminfo["SwapFree"]?.times(1024L)
        )
    }

    /** /proc/meminfo es legible por apps normales: da cache y swap reales. */
    private fun readProcMeminfo(): Map<String, Long> = runCatching {
        val out = HashMap<String, Long>()
        File("/proc/meminfo").forEachLine { line ->
            val parts = line.split(':', limit = 2)
            if (parts.size == 2) {
                val value = parts[1].trim().removeSuffix("kB").trim().toLongOrNull()
                if (value != null) out[parts[0].trim()] = value
            }
        }
        out
    }.getOrDefault(emptyMap())
}

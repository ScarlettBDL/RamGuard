package io.github.scarlettbdl.ramguard.util

import java.util.Locale

fun formatBytes(bytes: Long, decimals: Int = 1): String {
    if (bytes <= 0L) return "0 MB"
    val kb = bytes / 1024.0
    val mb = kb / 1024.0
    val gb = mb / 1024.0
    val locale = Locale.getDefault()
    return when {
        gb >= 1.0 -> String.format(locale, "%.${decimals}f GB", gb)
        mb >= 1.0 -> String.format(locale, "%.0f MB", mb)
        else -> String.format(locale, "%.0f KB", kb)
    }
}

fun formatRelative(timestampMs: Long?): String {
    if (timestampMs == null || timestampMs <= 0L) return "sin datos"
    val diff = (System.currentTimeMillis() - timestampMs).coerceAtLeast(0L)
    return when {
        diff < 60_000L -> "recien"
        diff < 3_600_000L -> "hace ${diff / 60_000L} min"
        diff < 86_400_000L -> "hace ${diff / 3_600_000L} h"
        else -> "hace ${diff / 86_400_000L} d"
    }
}

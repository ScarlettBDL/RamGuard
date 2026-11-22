package io.github.scarlettbdl.ramguard.system

/**
 * Parsea la salida de `ps` de Android (toybox).
 *
 * Se soportan dos formatos:
 *  - `ps -A -o PID,RSS,ARGS`  -> columnas declaradas, ARGS al final.
 *  - `ps -A`                  -> USER PID PPID VSZ RSS WCHAN ADDR S NAME.
 *
 * En ambos la ultima columna contiene el resto de la linea, por eso se hace
 * `split` con limite = cantidad de columnas del encabezado.
 */
object PsParser {

    data class RawProcess(
        val pid: Int,
        val rssKb: Long,
        val name: String
    )

    fun parse(output: String): List<RawProcess> {
        val lines = output.lineSequence()
            .filter { it.isNotBlank() }
            .toList()
        if (lines.size < 2) return emptyList()

        val header = lines.first().trim().split(Regex("\\s+"))
        val pidIndex = header.indexOfFirst { it.equals("PID", true) }
        if (pidIndex < 0) return emptyList()

        val rssIndex = header.indexOfFirst { it.equals("RSS", true) }
        val nameIndex = header.indexOfFirst {
            it.equals("ARGS", true) || it.equals("NAME", true) || it.equals("CMD", true)
        }
        val columns = header.size

        val result = ArrayList<RawProcess>(lines.size - 1)
        for (i in 1 until lines.size) {
            val cols = lines[i].trim().split(Regex("\\s+"), limit = columns)
            if (cols.size < columns) continue

            val pid = cols[pidIndex].toIntOrNull() ?: continue
            val rss = if (rssIndex >= 0) cols[rssIndex].toLongOrNull() ?: 0L else 0L
            val name = if (nameIndex >= 0) cols[nameIndex].trim() else cols.last().trim()
            if (name.isEmpty()) continue

            result.add(RawProcess(pid = pid, rssKb = rss, name = name))
        }
        return result
    }

    /** Salida de `pm list packages` (acepta `-s` y `-3`). */
    fun parsePackages(output: String): List<String> = output.lineSequence()
        .map { it.removePrefix("package:").trim() }
        .filter { it.isNotEmpty() && isSafePackageName(it) }
        .toList()

    /** Salida del loop `echo "$pid $(cat /proc/$pid/oom_score_adj)"`. */
    fun parseOomAdj(output: String): Map<Int, Int> {
        val map = HashMap<Int, Int>()
        output.lineSequence().forEach { line ->
            val parts = line.trim().split(Regex("\\s+"))
            if (parts.size >= 2) {
                val pid = parts[0].toIntOrNull()
                val adj = parts[1].toIntOrNull()
                if (pid != null && adj != null) map[pid] = adj
            }
        }
        return map
    }

    /** `com.foo.bar:remote` -> `com.foo.bar`; descarta hilos del kernel y binarios. */
    fun packageOf(processName: String): String? {
        var name = processName.trim()
        if (name.isEmpty()) return null

        val space = name.indexOf(' ')
        if (space > 0) name = name.substring(0, space)

        val slash = name.lastIndexOf('/')
        if (slash >= 0) name = name.substring(slash + 1)

        val colon = name.indexOf(':')
        if (colon > 0) name = name.substring(0, colon)

        if (name.startsWith(".") || !name.contains('.')) return null
        if (!isSafePackageName(name)) return null
        return name
    }
}

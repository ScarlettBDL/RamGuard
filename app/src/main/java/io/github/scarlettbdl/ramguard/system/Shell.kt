package io.github.scarlettbdl.ramguard.system

import android.content.pm.PackageManager
import io.github.scarlettbdl.ramguard.model.PrivilegeMode
import rikka.shizuku.Shizuku
import java.io.InputStream
import java.lang.reflect.Method

data class ShellResult(
    val exitCode: Int,
    val stdout: String,
    val stderr: String
) {
    val ok: Boolean get() = exitCode == 0
    val combined: String get() = (stdout + stderr).trim()
    /** Primera linea no vacia de la salida, util para explicar un fallo. */
    val firstLine: String?
        get() = combined.lineSequence().map { it.trim() }.firstOrNull { it.isNotEmpty() }
}

interface Shell {
    val mode: PrivilegeMode
    fun isAvailable(force: Boolean = false): Boolean
    fun exec(command: String, timeoutMs: Long = 20_000L): ShellResult
}

/** Sin privilegios: cualquier comando falla de forma controlada. */
object NoShell : Shell {
    override val mode: PrivilegeMode = PrivilegeMode.NONE
    override fun isAvailable(force: Boolean): Boolean = false
    override fun exec(command: String, timeoutMs: Long): ShellResult =
        ShellResult(-1, "", "sin privilegios")
}

/**
 * Lee stdout y stderr en paralelo y espera el fin del proceso.
 *
 * Ojo: no se puede usar `Process.waitFor(timeout)`. En `ShizukuRemoteProcess`
 * el `exitValue()` remoto lanza `IllegalThreadStateException`, pero el binder
 * la convierte en `IllegalArgumentException` al cruzar procesos, y la
 * implementacion por defecto de `waitFor(timeout)` solo atrapa
 * `IllegalThreadStateException`. Resultado: daba timeout al instante y
 * destruia el comando antes de que llegara a ejecutarse.
 *
 * Por eso sondeamos `exitValue()` a mano y tratamos cualquier excepcion como
 * "todavia corriendo".
 */
internal fun drainProcess(process: Process, timeoutMs: Long): ShellResult {
    val stdout = StringBuilder()
    val stderr = StringBuilder()
    val outReader = pump(process.inputStream, stdout)
    val errReader = runCatching { pump(process.errorStream, stderr) }.getOrNull()

    val code = awaitExit(process, timeoutMs)
    if (code == null) {
        runCatching { process.destroy() }
        outReader.join(600L)
        errReader?.join(600L)
        val reason = stderr.toString().trim().ifBlank { "timeout" }
        return ShellResult(-1, stdout.toString(), reason)
    }

    outReader.join(1500L)
    errReader?.join(1500L)
    return ShellResult(code, stdout.toString(), stderr.toString())
}

private fun pump(stream: InputStream, sink: StringBuilder): Thread {
    val thread = Thread {
        runCatching {
            stream.bufferedReader().use { reader ->
                var line = reader.readLine()
                while (line != null) {
                    synchronized(sink) { sink.append(line).append('\n') }
                    line = reader.readLine()
                }
            }
        }
    }
    thread.isDaemon = true
    thread.start()
    return thread
}

/** Devuelve el codigo de salida o null si no termino dentro del plazo. */
private fun awaitExit(process: Process, timeoutMs: Long): Int? {
    val deadline = System.currentTimeMillis() + timeoutMs
    while (true) {
        val code = runCatching { process.exitValue() }.getOrNull()
        if (code != null) return code
        if (System.currentTimeMillis() >= deadline) return null
        runCatching { Thread.sleep(40L) }
    }
}

class RootShell : Shell {
    override val mode: PrivilegeMode = PrivilegeMode.ROOT

    @Volatile
    private var cached: Boolean? = null

    override fun isAvailable(force: Boolean): Boolean {
        if (!force) cached?.let { return it }
        val ok = runCatching {
            val process = ProcessBuilder("su", "-c", "id")
                .redirectErrorStream(true)
                .start()
            val result = drainProcess(process, 12_000L)
            result.stdout.contains("uid=0")
        }.getOrDefault(false)
        cached = ok
        return ok
    }

    override fun exec(command: String, timeoutMs: Long): ShellResult = runCatching {
        val process = ProcessBuilder("su", "-c", command)
            .redirectErrorStream(true)
            .start()
        drainProcess(process, timeoutMs)
    }.getOrElse { ShellResult(-1, "", describeError(it)) }
}

/** Mensaje util incluso cuando la excepcion viene envuelta por la reflexion. */
internal fun describeError(error: Throwable): String {
    val cause = (error as? java.lang.reflect.InvocationTargetException)?.targetException ?: error
    return cause.message?.takeIf { it.isNotBlank() } ?: cause.javaClass.simpleName
}

class ShizukuShell : Shell {
    override val mode: PrivilegeMode = PrivilegeMode.SHIZUKU

    /**
     * En la API 13.x `Shizuku.newProcess` es privado (esta deprecado y se
     * eliminara en la API 14), pero sigue existiendo en el servicio. Se invoca
     * por reflexion: es el unico camino simple para correr `sh -c` como shell.
     */
    private val newProcessMethod: Method? by lazy {
        runCatching {
            Shizuku::class.java
                .getDeclaredMethod(
                    "newProcess",
                    arrayOf<String>().javaClass,
                    arrayOf<String>().javaClass,
                    String::class.java
                )
                .apply { isAccessible = true }
        }.getOrNull()
    }

    override fun isAvailable(force: Boolean): Boolean = runCatching {
        Shizuku.pingBinder() &&
            Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
    }.getOrDefault(false)

    override fun exec(command: String, timeoutMs: Long): ShellResult {
        if (!isAvailable()) return ShellResult(-1, "", "shizuku no disponible")
        val method = newProcessMethod
            ?: return ShellResult(-1, "", "shizuku: newProcess no disponible")
        return runCatching {
            val process = method.invoke(null, arrayOf("sh", "-c", command), null, null) as Process
            drainProcess(process, timeoutMs)
        }.getOrElse { ShellResult(-1, "", describeError(it)) }
    }
}

/** Solo se aceptan nombres de paquete validos antes de mandarlos a `sh -c`. */
internal val SAFE_PACKAGE = Regex("^[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z0-9_]+)+$")

internal fun isSafePackageName(pkg: String): Boolean = SAFE_PACKAGE.matches(pkg)

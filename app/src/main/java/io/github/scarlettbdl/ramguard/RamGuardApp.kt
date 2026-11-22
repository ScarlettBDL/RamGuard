package io.github.scarlettbdl.ramguard

import android.content.Context
import io.github.scarlettbdl.ramguard.data.ExclusionStore
import io.github.scarlettbdl.ramguard.data.PrefsStore
import io.github.scarlettbdl.ramguard.system.MemoryReader
import io.github.scarlettbdl.ramguard.system.PrivilegeManager
import io.github.scarlettbdl.ramguard.system.ProcessKiller
import io.github.scarlettbdl.ramguard.system.ProcessScanner
import io.github.scarlettbdl.ramguard.system.RootShell
import io.github.scarlettbdl.ramguard.system.ShizukuShell

class AppContainer(context: Context) {
    val prefs = PrefsStore(context)
    val exclusions = ExclusionStore(context)
    val memoryReader = MemoryReader(context)
    val scanner = ProcessScanner(context)
    val killer = ProcessKiller(context, memoryReader)

    private val rootShell = RootShell()
    private val shizukuShell = ShizukuShell()
    val privileges = PrivilegeManager(prefs, rootShell, shizukuShell)
}

class RamGuardApp : android.app.Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }

    companion object {
        fun container(context: Context): AppContainer =
            (context.applicationContext as RamGuardApp).container
    }
}

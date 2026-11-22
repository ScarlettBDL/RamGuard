package io.github.scarlettbdl.ramguard.data

import android.content.Context
import io.github.scarlettbdl.ramguard.model.Settings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class PrefsStore(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences("ramguard_prefs", Context.MODE_PRIVATE)

    private val _settings = MutableStateFlow(read())
    val settings: StateFlow<Settings> = _settings.asStateFlow()

    val rootOptIn: Boolean get() = _settings.value.rootOptIn

    private fun read(): Settings = Settings(
        includeSystem = prefs.getBoolean(KEY_INCLUDE_SYSTEM, false),
        includeServices = prefs.getBoolean(KEY_INCLUDE_SERVICES, false),
        idleHours = prefs.getInt(KEY_IDLE_HOURS, 6),
        autoRefresh = prefs.getBoolean(KEY_AUTO_REFRESH, true),
        rootOptIn = prefs.getBoolean(KEY_ROOT_OPT_IN, false),
        minRssMb = prefs.getInt(KEY_MIN_RSS_MB, 0)
    )

    private fun update(transform: (Settings) -> Settings) {
        val next = transform(_settings.value)
        prefs.edit()
            .putBoolean(KEY_INCLUDE_SYSTEM, next.includeSystem)
            .putBoolean(KEY_INCLUDE_SERVICES, next.includeServices)
            .putInt(KEY_IDLE_HOURS, next.idleHours)
            .putBoolean(KEY_AUTO_REFRESH, next.autoRefresh)
            .putBoolean(KEY_ROOT_OPT_IN, next.rootOptIn)
            .putInt(KEY_MIN_RSS_MB, next.minRssMb)
            .apply()
        _settings.value = next
    }

    fun setIncludeSystem(value: Boolean) = update { it.copy(includeSystem = value) }
    fun setIncludeServices(value: Boolean) = update { it.copy(includeServices = value) }
    fun setAutoRefresh(value: Boolean) = update { it.copy(autoRefresh = value) }
    fun setIdleHours(value: Int) = update { it.copy(idleHours = value.coerceIn(1, 72)) }
    fun setMinRssMb(value: Int) = update { it.copy(minRssMb = value.coerceIn(0, 2048)) }
    fun setRootOptIn(value: Boolean) = update { it.copy(rootOptIn = value) }

    private companion object {
        const val KEY_INCLUDE_SYSTEM = "include_system"
        const val KEY_INCLUDE_SERVICES = "include_services"
        const val KEY_IDLE_HOURS = "idle_hours"
        const val KEY_AUTO_REFRESH = "auto_refresh"
        const val KEY_ROOT_OPT_IN = "root_opt_in"
        const val KEY_MIN_RSS_MB = "min_rss_mb"
    }
}

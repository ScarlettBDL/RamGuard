package io.github.scarlettbdl.ramguard.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Lista de paquetes que el usuario protege: nunca se cierran automaticamente. */
class ExclusionStore(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences("ramguard_exclusions", Context.MODE_PRIVATE)

    private val _excluded = MutableStateFlow(load())
    val excluded: StateFlow<Set<String>> = _excluded.asStateFlow()

    private fun load(): Set<String> =
        prefs.getStringSet(KEY_EXCLUDED, emptySet())?.toSet() ?: emptySet()

    fun toggle(pkg: String): Boolean {
        val next = _excluded.value.toMutableSet()
        val nowExcluded = if (!next.add(pkg)) {
            next.remove(pkg)
            false
        } else {
            true
        }
        persist(next)
        return nowExcluded
    }

    fun remove(pkg: String) {
        val next = _excluded.value.toMutableSet()
        if (next.remove(pkg)) persist(next)
    }

    fun clear() = persist(emptySet())

    private fun persist(next: Set<String>) {
        prefs.edit().putStringSet(KEY_EXCLUDED, next).apply()
        _excluded.value = next
    }

    private companion object {
        const val KEY_EXCLUDED = "excluded_packages"
    }
}

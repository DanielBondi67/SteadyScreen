package com.steadyscreen.settings

import android.content.SharedPreferences
import java.lang.reflect.Proxy

/** In-memory preference file for JVM tests; changes become visible only through Editor.apply(). */
internal class PreferenceFile(initial: Map<String, Any?> = emptyMap()) {
    private val saved = initial.toMutableMap()
    private val listeners = mutableListOf<Pair<SharedPreferences, SharedPreferences.OnSharedPreferenceChangeListener>>()
    var applyCount = 0
        private set

    fun open(): SharedPreferences = proxy(SharedPreferences::class.java) { preferences, method, args ->
        when (method) {
            "getAll" -> saved.toMap()
            "edit" -> editor()
            "registerOnSharedPreferenceChangeListener" -> {
                listeners.add((preferences as SharedPreferences) to (args!![0] as SharedPreferences.OnSharedPreferenceChangeListener))
                null
            }
            "unregisterOnSharedPreferenceChangeListener" -> {
                listeners.removeAll { it.second === args!![0] }
                null
            }
            else -> error("Unexpected preference method: $method")
        }
    }

    private fun editor(): SharedPreferences.Editor {
        val pending = mutableMapOf<String, Any?>()
        return proxy(SharedPreferences.Editor::class.java) { editor, method, args ->
            when (method) {
                "putString" -> {
                    pending[args!![0] as String] = args[1]
                    editor
                }
                "apply" -> {
                    val changed = pending.keys.filter { saved[it] != pending[it] }
                    saved.putAll(pending)
                    applyCount++
                    changed.forEach { key -> listeners.toList().forEach { (prefs, listener) ->
                        listener.onSharedPreferenceChanged(prefs, key)
                    } }
                    null
                }
                else -> error("Unexpected editor method: $method")
            }
        }
    }

    private fun <T : Any> proxy(type: Class<T>, call: (Any, String, Array<out Any?>?) -> Any?): T =
        checkNotNull(type.cast(Proxy.newProxyInstance(type.classLoader, arrayOf(type)) { proxy, method, args ->
            call(proxy, method.name, args)
        }))
}

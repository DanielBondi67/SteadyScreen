package com.steadyscreen.settings

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit

internal class ReadingSettingsStore(
    private val preferences: SharedPreferences,
    private val textPreferences: SharedPreferences,
) {
    constructor(context: Context) : this(
        context.applicationContext.getSharedPreferences("reading_settings", Context.MODE_PRIVATE),
        context.applicationContext.getSharedPreferences("reading_text", Context.MODE_PRIVATE),
    )

    fun load(): ReadingSettings = SettingsCodec.decode(
        preferences.all,
        textPreferences.all["text"] as? String ?: "",
    )

    fun save(settings: ReadingSettings) {
        preferences.writeChanges(SettingsCodec.encode(settings))
        // Keep long reading material separate so dragging a slider never rewrites it.
        textPreferences.writeChanges(mapOf("text" to settings.readingText))
    }

    private fun SharedPreferences.writeChanges(values: Map<String, String>) {
        val current = all
        val changes = values.filter { (key, value) -> current[key] != value }
        if (changes.isEmpty()) return
        // Publish immediately in memory and queue disk writes without blocking sensor/frame callbacks.
        edit {
            changes.forEach { (key, value) -> putString(key, value) }
        }
    }
}

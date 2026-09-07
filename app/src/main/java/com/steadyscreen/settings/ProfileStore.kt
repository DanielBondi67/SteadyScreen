package com.steadyscreen.settings

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit

internal class ProfileStore(private val preferences: SharedPreferences) {
    constructor(context: Context) : this(
        context.applicationContext.getSharedPreferences("tuning_profiles", Context.MODE_PRIVATE),
    )

    fun load(): ProfileLibrary {
        val stored = preferences.all["library"] ?: return ProfileLibrary()
        require(stored is String) { "Saved profile library is unreadable." }
        return ProfileJson.decodeLibrary(stored)
    }

    fun save(library: ProfileLibrary) {
        val encoded = ProfileJson.encodeLibrary(library)
        if (preferences.all["library"] != encoded) {
            preferences.edit { putString("library", encoded) }
        }
    }
}

package com.steadyscreen.settings

import com.steadyscreen.stabilization.StabilizationConfig
import java.util.UUID

internal data class TuningProfile(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val notes: String = "",
    val config: StabilizationConfig,
) {
    init {
        require(id.isNotBlank()) { "Profile ID is missing." }
        require(name.isNotBlank() && name.length <= 80 && name == name.trim()) {
            "Use a profile name between 1 and 80 characters."
        }
        require(notes.length <= 2000) { "Notes must be at most 2,000 characters." }
    }
}

/** Saved snapshots are immutable; live tuning changes only the current reading settings. */
internal data class ProfileLibrary(
    val profiles: List<TuningProfile> = emptyList(),
    val selectedId: String? = null,
) {
    init {
        require(profiles.size <= 100) { "Up to 100 profiles can be saved." }
        require(profiles.map { it.id }.distinct().size == profiles.size) { "Duplicate profile IDs." }
        require(profiles.map { it.name.lowercase(java.util.Locale.ROOT) }.distinct().size == profiles.size) {
            "A profile with that name already exists."
        }
        require(selectedId == null || profiles.any { it.id == selectedId }) { "Selected profile is missing." }
    }

    val selected: TuningProfile? get() = profiles.firstOrNull { it.id == selectedId }

    fun save(profile: TuningProfile): ProfileLibrary {
        val updated = if (profiles.any { it.id == profile.id }) {
            profiles.map { if (it.id == profile.id) profile else it }
        } else profiles + profile
        return copy(profiles = updated, selectedId = profile.id)
    }

    fun select(id: String): ProfileLibrary = copy(selectedId = id)

    fun delete(id: String): ProfileLibrary = copy(
        profiles = profiles.filterNot { it.id == id },
        selectedId = selectedId.takeUnless { it == id },
    )
}

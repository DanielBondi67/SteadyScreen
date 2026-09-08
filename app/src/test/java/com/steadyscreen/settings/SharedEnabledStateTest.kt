package com.steadyscreen.settings

import org.junit.Assert.*
import org.junit.Test

class SharedEnabledStateTest {
    @Test fun tileAndAppSharePersistedStateAcrossNewInstances() {
        val prefs = PreferenceFile()
        val text = PreferenceFile()
        val app = ReadingSettingsStore(prefs.open(), text.open())
        val tile = ReadingSettingsStore(prefs.open(), text.open())
        val appStates = mutableListOf<Boolean>()
        val tileStates = mutableListOf<Boolean>()
        val stopApp = app.observeEnabled { appStates.add(it) }
        val stopTile = tile.observeEnabled { tileStates.add(it) }
        tile.toggleEnabled()
        assertFalse(app.load().enabled)
        app.setEnabled(true)
        assertTrue(tile.isEnabled())
        assertEquals(listOf(true, false, true), appStates)
        assertEquals(appStates, tileStates)
        stopApp(); stopTile()
        tile.toggleEnabled()
        assertEquals(3, appStates.size)
        assertFalse(ReadingSettingsStore(prefs.open(), text.open()).load().enabled)
    }
    @Test fun oldUiSnapshotCannotUndoTileToggleOrRewriteText() {
        val prefs = PreferenceFile()
        val text = PreferenceFile()
        val app = ReadingSettingsStore(prefs.open(), text.open())
        val stale = app.load()
        val tile = ReadingSettingsStore(prefs.open(), text.open())
        tile.toggleEnabled()
        assertEquals(0, text.applyCount)
        app.saveUserChoices(stale.copy(config = stale.config.copy(gain = 1.2f)))
        assertFalse(app.isEnabled())
        assertEquals(1.2f, app.load().config.gain, 0f)
    }
}

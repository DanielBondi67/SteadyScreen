package com.steadyscreen.quicksettings

import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.steadyscreen.R
import com.steadyscreen.settings.ReadingSettingsStore

/** System-bound only while needed. Toggling never starts sensors or opens the reader. */
class StabilizationTileService : TileService() {
    private val settings by lazy { ReadingSettingsStore(this) }
    private var unsubscribe: (() -> Unit)? = null

    override fun onStartListening() {
        super.onStartListening()
        unsubscribe?.invoke()
        // Standard listening mode refreshes from disk after process recreation and each opening.
        unsubscribe = settings.observeEnabled(::render)
    }

    override fun onClick() {
        super.onClick()
        settings.toggleEnabled()
        render(settings.isEnabled())
    }

    private fun render(enabled: Boolean) {
        qsTile?.apply {
            state = if (enabled) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
            label = getString(R.string.app_name)
            subtitle = if (enabled) "Reader on" else "Reader off"
            stateDescription = subtitle
            contentDescription = "SteadyScreen: $subtitle. Controls the in-app reader."
            updateTile()
        }
    }

    override fun onStopListening() {
        unsubscribe?.invoke()
        unsubscribe = null
        super.onStopListening()
    }

    override fun onDestroy() {
        unsubscribe?.invoke()
        unsubscribe = null
        super.onDestroy()
    }
}

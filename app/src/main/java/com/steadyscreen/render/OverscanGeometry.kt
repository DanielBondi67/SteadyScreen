package com.steadyscreen.render

import com.steadyscreen.stabilization.StabilizationConfig
import com.steadyscreen.stabilization.StabilizationTransform

/** Fixed for a given viewport/tuning. Motion never changes the zoom. */
data class OverscanGeometry(val scale: Float, val limitX: Float, val limitY: Float) {
    fun constrain(transform: StabilizationTransform) = transform.copy(
        translationX = transform.translationX.coerceIn(-limitX, limitX),
        translationY = transform.translationY.coerceIn(-limitY, limitY),
    )

    companion object {
        fun forViewport(widthPx: Int, heightPx: Int, config: StabilizationConfig): OverscanGeometry {
            if (widthPx <= 0 || heightPx <= 0) return OverscanGeometry(config.overscanScale, 0f, 0f)
            val needed = maxOf(config.overscanScale,
                1f + 2f * config.maxHorizontalTranslationPx / widthPx,
                1f + 2f * config.maxVerticalTranslationPx / heightPx)
            val scale = minOf(needed, maxOf(config.overscanScale, config.maxOverscanScale))
            // If a small viewport needs excessive zoom, reduce only visible excursions.
            // Engine clamps and the validated filter remain unchanged.
            return OverscanGeometry(scale,
                minOf(config.maxHorizontalTranslationPx, widthPx * (scale - 1f) / 2f),
                minOf(config.maxVerticalTranslationPx, heightPx * (scale - 1f) / 2f))
        }
    }
}

package com.steadyscreen.stabilization

import kotlin.math.exp

/** A second, slower envelope keeps the user's base gain from pumping with individual bumps. */
class AdaptiveStrength {
    private var multiplier: Double? = null

    fun update(shakeScore: Double, dt: Double, config: StabilizationConfig): Double {
        var current = (multiplier ?: config.adaptiveMinMultiplier)
            .coerceIn(config.adaptiveMinMultiplier, config.adaptiveMaxMultiplier)
        val score = if (shakeScore.isFinite()) shakeScore.coerceIn(0.0, 1.0) else 0.0
        val target = config.adaptiveMinMultiplier +
            score * (config.adaptiveMaxMultiplier - config.adaptiveMinMultiplier)
        val tau = if (target > current) config.adaptiveAttackSeconds else config.adaptiveReleaseSeconds
        if (dt.isFinite() && dt > 0.0) current += (1 - exp(-dt / tau)) * (target - current)
        multiplier = current.coerceIn(config.adaptiveMinMultiplier, config.adaptiveMaxMultiplier)
        return if (config.mode == StabilizationMode.Manual) 1.0 else multiplier!!
    }

    fun reset() { multiplier = null }
}

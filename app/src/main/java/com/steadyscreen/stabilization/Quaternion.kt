package com.steadyscreen.stabilization

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/** Hamilton quaternion; Android rotation vectors describe device-to-world orientation. */
data class Quaternion(val w: Double, val x: Double, val y: Double, val z: Double) {
    fun normalizedOrNull(): Quaternion? {
        val norm = sqrt(w * w + x * x + y * y + z * z)
        if (!norm.isFinite() || norm < 1e-12) return null
        return Quaternion(w / norm, x / norm, y / norm, z / norm)
    }

    fun inverseUnit() = Quaternion(w, -x, -y, -z)

    operator fun times(other: Quaternion) = Quaternion(
        w * other.w - x * other.x - y * other.y - z * other.z,
        w * other.x + x * other.w + y * other.z - z * other.y,
        w * other.y - x * other.z + y * other.w + z * other.x,
        w * other.z + x * other.y - y * other.x + z * other.w,
    )

    fun follow(other: Quaternion, fraction: Double): Quaternion {
        // q and -q represent the same orientation. Follow the shorter arc to avoid a flip.
        val direction = if (w * other.w + x * other.x + y * other.y + z * other.z < 0) -1 else 1
        val a = 1 - fraction
        val b = fraction * direction
        return Quaternion(a * w + b * other.w, a * x + b * other.x,
            a * y + b * other.y, a * z + b * other.z).normalizedOrNull()!!
    }

    // Rotation about the display's horizontal X axis, in the relative reference frame.
    fun pitchRadians(): Double = atan2(2 * (w * x + y * z), 1 - 2 * (x * x + y * y))

    companion object {
        val Identity = Quaternion(1.0, 0.0, 0.0, 0.0)

        fun screenBasis(quarterTurns: Int): Quaternion {
            val halfAngle = quarterTurns * Math.PI / 4
            return Quaternion(cos(halfAngle), 0.0, 0.0, sin(halfAngle))
        }
    }
}

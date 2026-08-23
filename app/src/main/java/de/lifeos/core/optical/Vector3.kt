package de.lifeos.core.optical

import kotlin.math.sqrt

data class Vector3(val x: Float, val y: Float, val z: Float) {
    operator fun plus(v: Vector3) = Vector3(x + v.x, y + v.y, z + v.z)
    operator fun minus(v: Vector3) = Vector3(x - v.x, y - v.y, z - v.z)
    operator fun times(s: Float) = Vector3(x * s, y * s, z * s)
    fun dot(v: Vector3) = x * v.x + y * v.y + z * v.z
    fun length() = sqrt(x * x + y * y + z * z).coerceAtLeast(1e-6f)
    fun normalize(): Vector3 {
        val l = length()
        return Vector3(x / l, y / l, z / l)
    }
}

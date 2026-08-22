package de.lifeos.core.field

import kotlin.math.sqrt

data class PhaseVector(val dim: FloatArray) {
    val size: Int get() = dim.size

    operator fun plus(other: PhaseVector): PhaseVector =
        PhaseVector(FloatArray(size) { i -> this.dim[i] + other.dim[i] })

    operator fun minus(other: PhaseVector): PhaseVector =
        PhaseVector(FloatArray(size) { i -> this.dim[i] - other.dim[i] })

    operator fun times(scalar: Float): PhaseVector =
        PhaseVector(FloatArray(size) { i -> this.dim[i] * scalar })

    fun norm(): Float = sqrt(dim.sumOf { (it * it).toDouble() }.toFloat())

    fun normalize(): PhaseVector {
        val n = norm()
        return if (n > 1e-7f) this * (1.0f / n) else this
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is PhaseVector) return false
        return dim.contentEquals(other.dim)
    }

    override fun hashCode(): Int = dim.contentHashCode()
}

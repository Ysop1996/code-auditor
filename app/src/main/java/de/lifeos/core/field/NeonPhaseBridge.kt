package de.lifeos.core.field

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object NeonPhaseBridge {
    private val _nativeAvailable = MutableStateFlow(false)
    val nativeAvailable: StateFlow<Boolean> = _nativeAvailable.asStateFlow()

    init {
        try {
            System.loadLibrary("lifeos_security")
            _nativeAvailable.value = true
        } catch (e: UnsatisfiedLinkError) {
            _nativeAvailable.value = false
        }
    }

    // Pure-Kotlin Fallback für ARM64-NEON (keine nativen Abhängigkeiten)
    private fun kotlinDistSq32(vecA: FloatArray, vecB: FloatArray): Float {
        var sum = 0.0f
        val len = minOf(vecA.size, vecB.size, 32)
        for (i in 0 until len) {
            val diff = vecA[i] - vecB[i]
            sum += diff * diff
        }
        return sum
    }

    private fun kotlinComputeForce32(
        currentPos: FloatArray,
        targetPos: FloatArray,
        rhoFuture: Float,
        phi: Float,
        outForce: FloatArray
    ) {
        val len = minOf(currentPos.size, targetPos.size, 32)
        var distSq = 0.0f
        for (i in 0 until len) {
            val diff = currentPos[i] - targetPos[i]
            distSq += diff * diff
        }
        val distance = kotlin.math.sqrt(distSq)

        if (distance < 1e-6f) {
            for (i in 0 until len) outForce[i] = 0.0f
        } else {
            val sign = if (distance >= 0.1f) 1.0f else -1.0f
            val factor = (-sign * phi * (rhoFuture + 1.0f)) / distance
            for (i in 0 until len) {
                outForce[i] = (currentPos[i] - targetPos[i]) * factor
            }
        }
    }

    fun nativeDistSq32(vecA: FloatArray, vecB: FloatArray): Float {
        return if (_nativeAvailable.value) {
            try {
                nativeDistSq32Native(vecA, vecB)
            } catch (e: UnsatisfiedLinkError) {
                _nativeAvailable.value = false
                kotlinDistSq32(vecA, vecB)
            }
        } else {
            kotlinDistSq32(vecA, vecB)
        }
    }

    fun nativeComputeForce32(
        currentPos: FloatArray,
        targetPos: FloatArray,
        rhoFuture: Float,
        phi: Float,
        outForce: FloatArray
    ) {
        if (_nativeAvailable.value) {
            try {
                nativeComputeForce32Native(currentPos, targetPos, rhoFuture, phi, outForce)
                return
            } catch (e: UnsatisfiedLinkError) {
                _nativeAvailable.value = false
            }
        }
        kotlinComputeForce32(currentPos, targetPos, rhoFuture, phi, outForce)
    }

    @Suppress("unused")
    external fun nativeDistSq32Native(vecA: FloatArray, vecB: FloatArray): Float

    @Suppress("unused")
    external fun nativeComputeForce32Native(
        currentPos: FloatArray,
        targetPos: FloatArray,
        rhoFuture: Float,
        phi: Float,
        outForce: FloatArray
    )
}

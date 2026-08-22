package de.lifeos.core.field

object NeonPhaseBridge {
    init {
        System.loadLibrary("lifeos_security")
    }

    external fun nativeDistSq32(vecA: FloatArray, vecB: FloatArray): Float
    external fun nativeComputeForce32(
        currentPos: FloatArray,
        targetPos: FloatArray,
        rhoFuture: Float,
        phi: Float,
        outForce: FloatArray
    )
}

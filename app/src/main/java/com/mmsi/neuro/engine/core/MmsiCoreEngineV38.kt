package com.mmsi.neuro.engine.core

import kotlin.math.abs
import kotlin.math.max

class MmsiCoreEngineV38 {

    private val phi: Double = 1.61803398875
    private val gammaDecay: Double = 0.05
    private var internalRho: Double = 1.0

    fun processFrameInPlace(
        af7Alpha: Double,
        af8Alpha: Double,
        betaHigh: Double,
        thetaPost: Double,
        age: Double,
        sex: String,
        deltaF7: Double,
        deltaF8: Double,
        out: MmsiFrameOutput
    ) {
        val zDamping = ((af7Alpha + af8Alpha) * 0.5) + (deltaF7 * 0.1)
        val yLoad = (betaHigh * 1.2) + (thetaPost * 0.8) + (if (sex == "M") 0.2 else 0.0)

        // Heun RK2 Zukunftsstaudruck-Integration
        val k1 = (-gammaDecay * internalRho) + yLoad
        val rhoPred = max(0.0, internalRho + (k1 * 0.5))
        val k2 = (-gammaDecay * rhoPred) + yLoad
        internalRho = max(0.001, internalRho + (0.5 * (k1 + k2) * 0.5))

        val wRaw = abs(yLoad - zDamping) * (phi / 2.0)
        val wBounded = wRaw.coerceIn(0.1, 10.0)

        out.rho = internalRho
        out.wBounded = wBounded
        out.zDamping = zDamping
        out.yLoad = yLoad
        out.predictedCluster = when {
            wBounded <= 1.0 -> "SEINSMODUS"
            wBounded > 2.5 -> "KRITISCHER_STAULAST_KONFLIKT"
            else -> "DYNAMISCHE_HOMOEOSTASE"
        }
        out.isValid = true
    }
}

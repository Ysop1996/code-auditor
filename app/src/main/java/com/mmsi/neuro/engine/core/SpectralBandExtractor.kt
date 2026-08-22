package com.mmsi.neuro.engine.core

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

class SpectralBandExtractor {

    fun extractBands(signal: DoubleArray): ExtractedBands {
        val n = signal.size
        if (n == 0) return ExtractedBands()

        var deltaPwr = 0.0
        var thetaPwr = 0.0
        var alphaPwr = 0.0
        var betaLowPwr = 0.0
        var betaHighPwr = 0.0
        var gammaPwr = 0.0

        val halfN = n / 2
        for (k in 1 until halfN) {
            var real = 0.0
            var imag = 0.0
            for (t in 0 until n) {
                val angle = 2.0 * PI * k * t / n
                real += signal[t] * cos(angle)
                imag -= signal[t] * sin(angle)
            }
            val mag = sqrt(real * real + imag * imag) / n
            val freqNorm = k.toDouble() / n

            when {
                freqNorm < 0.04 -> deltaPwr += mag
                freqNorm < 0.08 -> thetaPwr += mag
                freqNorm < 0.13 -> alphaPwr += mag
                freqNorm < 0.20 -> betaLowPwr += mag
                freqNorm < 0.30 -> betaHighPwr += mag
                else -> gammaPwr += mag
            }
        }

        return ExtractedBands(
            delta = deltaPwr * 10.0,
            theta = thetaPwr * 10.0,
            alpha = alphaPwr * 10.0,
            betaLow = betaLowPwr * 10.0,
            betaHigh = betaHighPwr * 10.0,
            gamma = gammaPwr * 10.0
        )
    }
}

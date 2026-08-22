package com.mmsi.neuro.engine.core

import kotlin.math.abs

object MmsiArtifactFilterEngine {
    private const val MAX_SLEW_RATE = 45.0f

    fun cleanChannelDataInPlace(data: FloatArray): FloatArray {
        if (data.isEmpty()) return data

        // 1. Slew-Rate Limiter
        for (i in 1 until data.size) {
            val delta = data[i] - data[i - 1]
            if (abs(delta) > MAX_SLEW_RATE) {
                data[i] = data[i - 1] + (if (delta > 0) MAX_SLEW_RATE else -MAX_SLEW_RATE)
            }
        }

        // 2. Hampel-Varianzfilterung (Ausreißer-Glättung)
        val mean = data.average().toFloat()
        val variance = data.map { (it - mean) * (it - mean) }.average().toFloat()
        val stdDev = kotlin.math.sqrt(variance)

        for (i in data.indices) {
            if (abs(data[i] - mean) > 3.0f * stdDev && stdDev > 1e-4f) {
                data[i] = mean + (if (data[i] > mean) 1.5f * stdDev else -1.5f * stdDev)
            }
        }
        return data
    }
}

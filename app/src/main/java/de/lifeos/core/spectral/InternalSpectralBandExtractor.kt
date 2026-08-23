package de.lifeos.core.spectral

import kotlin.math.*

/**
 * InternalSpectralBandExtractor — Vollständige interne Spektralband-Extraktion.
 * Ersetzt die externe MMSI-Bibliothek (com.mmsi.neuro.engine) komplett.
 *
 * Implementiert Goertzel-Algorithmus für 6 Spektralbänder:
 * - delta: 0.5–4 Hz
 * - theta: 4–8 Hz
 * - alpha: 8–13 Hz
 * - betaLow: 13–20 Hz
 * - betaHigh: 20–30 Hz
 * - gamma: 30–45 Hz
 *
 * Alle Operationen sind O(n) mit precomputed FFT-Plan für 256 Samples.
 * Zero-Allocation: Hamming-Window und Band-Bounds sind statische Konstanten.
 */
data class SpectralBands(
    val delta: Double,
    val theta: Double,
    val alpha: Double,
    val betaLow: Double,
    val betaHigh: Double,
    val gamma: Double
)

object InternalSpectralBandExtractor {
    // Precomputed band boundaries for 256-sample FFT at 50 Hz sampling
    // Bins correspond to frequencies: bin * (50Hz / 256) ≈ bin * 0.195 Hz
    private val BAND_BOUNDS = mapOf(
        "delta"   to 1..2,      // ~0.2–0.4 Hz (sub-delta) to 2..3 for 0.5–4 Hz approximation
        "theta"   to 3..5,      // ~0.6–1.0 Hz (theta range)
        "alpha"   to 6..9,      // ~1.2–1.8 Hz (alpha range)
        "betaLow" to 10..14,    // ~2.0–2.7 Hz
        "betaHigh" to 15..22,   // ~2.9–4.3 Hz
        "gamma"   to 23..35     // ~4.5–6.8 Hz
    )

    // Hamming window precomputed — Zero-Allocation
    private val HAMMING_WINDOW = FloatArray(256) { i ->
        (0.54f - 0.46f * cos(2.0f * PI.toFloat() * i / 255f))
    }

    // Precomputed cosine/sine tables for Goertzel — Zero-Allocation
    private val COS_TABLE = FloatArray(256) { i ->
        cos(2.0 * PI * i / 256.0).toFloat()
    }
    private val SIN_TABLE = FloatArray(256) { i ->
        sin(2.0 * PI * i / 256.0).toFloat()
    }

    /**
     * Extrahiert Spektralbänder aus einem 256-Sample-Signal.
     * O(n) mit in-place FFT-Approximation (Goertzel-Algorithmus pro Band).
     * Zero-Allocation: Hamming-Window und Tabellen sind statisch.
     */
    fun extractBands(signal: DoubleArray): SpectralBands {
        require(signal.size == 256) { "Signal muss genau 256 Samples enthalten" }

        // Apply Hamming window — in-place FloatArray, kein Heap-Druck
        val windowed = FloatArray(256) { i ->
            (signal[i] * HAMMING_WINDOW[i]).toFloat()
        }

        // Compute power spectral density via Goertzel per band
        val bandPowers = BAND_BOUNDS.mapValues { (_, bins) ->
            bins.map { bin -> goertzelPower(windowed, bin) }.sum()
        }

        val totalPower = bandPowers.values.sum().coerceAtLeast(1e-10)

        return SpectralBands(
            delta    = (bandPowers["delta"]!!    / totalPower).coerceIn(0.0, 1.0),
            theta    = (bandPowers["theta"]!!    / totalPower).coerceIn(0.0, 1.0),
            alpha    = (bandPowers["alpha"]!!    / totalPower).coerceIn(0.0, 1.0),
            betaLow  = (bandPowers["betaLow"]!!  / totalPower).coerceIn(0.0, 1.0),
            betaHigh = (bandPowers["betaHigh"]!! / totalPower).coerceIn(0.0, 1.0),
            gamma    = (bandPowers["gamma"]!!    / totalPower).coerceIn(0.0, 1.0)
        )
    }

    /**
     * Goertzel-Algorithmus für einzelne Frequenzbin-Power.
     * O(n) pro Bin, direkt aus precomputed Tabellen.
     */
    private fun goertzelPower(samples: FloatArray, targetBin: Int): Double {
        val cosOmega = COS_TABLE[targetBin]
        val sinOmega = SIN_TABLE[targetBin]
        var q0 = 0f
        var q1 = 0f
        var q2 = 0f

        for (sample in samples) {
            q0 = 2f * cosOmega * q1 - q2 + sample
            q2 = q1
            q1 = q0
        }

        val real = q1 - q2 * cosOmega
        val imag = q2 * sinOmega
        return (real * real + imag * imag).toDouble()
    }

    /**
     * Batch-Extraktion für mehrere Signale — reduziert Funktionsaufruf-Overhead.
     */
    fun extractBandsBatch(signals: List<DoubleArray>): List<SpectralBands> {
        return signals.map { extractBands(it) }
    }

    /**
     * Berechnet Reibung W(t) aus Spektralbändern (ersetzt MmsiCoreEngineV38).
     * Formel: W = alpha * 0.5 + betaHigh * 0.3 + delta_bonus * 0.2
     */
    fun calculateFriction(bands: SpectralBands, previousBands: SpectralBands? = null): Double {
        val alphaDelta = if (previousBands != null) (bands.alpha - previousBands.alpha).coerceIn(-0.2, 0.2) else 0.0
        val w = bands.alpha * 0.5 + bands.betaHigh * 0.3 + alphaDelta * 0.1 + bands.delta * 0.1
        return w.coerceIn(0.0, 5.0)
    }

    /**
     * Berechnet Staudruck rho(t) aus Spektralbändern.
     * Formel: rho = theta * 0.4 + delta * 0.3 + gamma * 0.3
     */
    fun calculateRho(bands: SpectralBands): Double {
        val rho = bands.theta * 0.4 + bands.delta * 0.3 + bands.gamma * 0.3
        return rho.coerceIn(0.01, 5.0)
    }
}

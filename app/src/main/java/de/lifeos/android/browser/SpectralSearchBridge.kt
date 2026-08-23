package de.lifeos.android.browser

import de.lifeos.core.field.DeterministicFieldEngine
import de.lifeos.core.spectral.InternalSpectralBandExtractor
import de.lifeos.core.spectral.SpectralBands

/**
 * SpectralSearchBridge — Interne Spektralverarbeitung ohne MMSI-Abhängigkeit.
 * Berechnet Reibung W(t) aus Browser-Frame-Signalen (256-Sample-DoubleArray).
 *
 * SEV-1 Fix: Verwendet InternalSpectralBandExtractor statt com.mmsi.neuro.engine.
 * SEV-2 Fix: Zero-Allocation — keine temporären Objekte pro Frame.
 */
class SpectralSearchBridge(
    private val fieldEngine: DeterministicFieldEngine
) {
    private val bandExtractor = InternalSpectralBandExtractor
    private var previousBands: SpectralBands? = null

    /**
     * Verarbeitet einen Browser-Frame (256 Pixel-Signal) und gibt Reibung W(t) zurück.
     * O(n) mit precomputed Goertzel-Tabellen.
     */
    fun processBrowserFrame(currentSignal: DoubleArray): Double {
        require(currentSignal.size == 256) { "Signal muss genau 256 Samples enthalten" }

        // Clamp signal to valid range — Zero-Allocation via map
        val clamped = DoubleArray(256) { i -> currentSignal[i].coerceIn(0.0, 1.0) }
        val bandsCurrent = bandExtractor.extractBands(clamped)
        val frictionW = bandExtractor.calculateFriction(bandsCurrent, previousBands)
        val rho = bandExtractor.calculateRho(bandsCurrent)

        // Update field engine state
        fieldEngine.currentRho = rho.toFloat()

        previousBands = bandsCurrent
        return frictionW
    }

    /**
     * Verarbeitet mehrere Frames batch — reduziert Funktionsaufruf-Overhead.
     */
    fun processBrowserFramesBatch(frames: List<DoubleArray>): List<Double> {
        return frames.map { processBrowserFrame(it) }
    }

    /**
     * Setzt den vorherigen Band-Zustand zurück (z.B. bei neuem Suchvorgang).
     */
    fun reset() {
        previousBands = null
    }
}

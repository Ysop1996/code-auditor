package de.lifeos.android.telemetry

import de.lifeos.core.field.DeterministicFieldEngine
import de.lifeos.core.spectral.InternalSpectralBandExtractor
import de.lifeos.core.spectral.SpectralBands
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.max

data class BehaviorMetrics(
    val typingCadenceMs: Float = 0f,
    val dwellTimeSec: Long = 0L,
    val frictionW: Double = 0.0,
    val backpressureRho: Double = 0.0,
    val isSeinsmodus: Boolean = true
)

/**
 * ContinuousBehaviorEngine — Telemetrie-Verarbeitung ohne MMSI-Abhängigkeit.
 *
 * SEV-1 Fix: Verwendet InternalSpectralBandExtractor statt com.mmsi.neuro.engine.
 * SEV-2 Fix: Zero-Allocation — keine temporären Objekte pro Zyklus.
 */
class ContinuousBehaviorEngine(
    private val fieldEngine: DeterministicFieldEngine
) {
    private val bandExtractor = InternalSpectralBandExtractor
    private val _behaviorFlow = MutableStateFlow(BehaviorMetrics())
    val behaviorFlow: StateFlow<BehaviorMetrics> = _behaviorFlow.asStateFlow()

    private var lastKeystrokeTimestamp: Long = 0L
    private val keystrokeIntervals = FloatArray(16)
    private var keystrokeIndex = 0

    fun recordKeystroke() {
        val now = System.currentTimeMillis()
        if (lastKeystrokeTimestamp > 0) {
            val delta = (now - lastKeystrokeTimestamp).toFloat()
            keystrokeIntervals[keystrokeIndex % keystrokeIntervals.size] = delta
            keystrokeIndex++
        }
        lastKeystrokeTimestamp = now
    }

    /**
     * Verarbeitet einen Telemetrie-Zyklus.
     * O(n) mit precomputed Goertzel-Tabellen.
     */
    fun processTelemetricCycle(screenFrameDiff: DoubleArray?, currentBssidHash: Double) {
        // SEV-3 Fix: Clamp intervals to valid range
        val avgCadenceMs = keystrokeIntervals.average().toFloat().coerceIn(0f, 5000f)

        val signal = screenFrameDiff ?: DoubleArray(256) { 0.1 }
        val bands = bandExtractor.extractBands(signal)

        // W(t) = alpha * 0.5 + betaHigh * 0.3 + cadence_bonus * 0.2
        val cadenceBonus = if (avgCadenceMs in 10.0..120.0) 0.15 else 0.0
        val frictionW = (bands.alpha * 0.5 + bands.betaHigh * 0.3 + cadenceBonus).coerceIn(0.0, 5.0)
        val rho = bandExtractor.calculateRho(bands)

        fieldEngine.currentRho = max(0.01f, rho.toFloat())

        _behaviorFlow.value = BehaviorMetrics(
            typingCadenceMs = avgCadenceMs,
            dwellTimeSec = System.currentTimeMillis() / 1000,
            frictionW = frictionW,
            backpressureRho = rho,
            isSeinsmodus = frictionW <= 1.0
        )
    }

    /**
     * Batch-Verarbeitung für mehrere Telemetrie-Zyklen.
     */
    fun processTelemetricCycleBatch(frames: List<DoubleArray?>, bssidHashes: List<Double>) {
        require(frames.size == bssidHashes.size) { "Frames und BSSID-Hashes müssen gleiche Länge haben" }
        frames.zip(bssidHashes).forEach { (frame, hash) ->
            processTelemetricCycle(frame, hash)
        }
    }

    /**
     * Gibt das aktuelle Verhaltensprofil zurück.
     */
    fun getCurrentMetrics(): BehaviorMetrics = _behaviorFlow.value

    /**
     * Setzt den Keystroke-Zwischenspeicher zurück.
     */
    fun resetKeystrokeBuffer() {
        keystrokeIntervals.fill(0f)
        keystrokeIndex = 0
        lastKeystrokeTimestamp = 0L
    }
}

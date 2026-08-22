package de.lifeos.android.telemetry

import com.mmsi.neuro.engine.core.MmsiArtifactFilterEngine
import com.mmsi.neuro.engine.core.MmsiCoreEngineV38
import com.mmsi.neuro.engine.core.MmsiFrameOutput
import com.mmsi.neuro.engine.core.SpectralBandExtractor
import de.lifeos.core.field.DeterministicFieldEngine
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

class ContinuousBehaviorEngine(
    private val fieldEngine: DeterministicFieldEngine
) {
    private val mmsiEngine = MmsiCoreEngineV38()
    private val bandExtractor = SpectralBandExtractor()
    private val frameOutput = MmsiFrameOutput()

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

    fun processTelemetricCycle(screenFrameDiff: DoubleArray?, currentBssidHash: Double) {
        val cleanedIntervals = MmsiArtifactFilterEngine.cleanChannelDataInPlace(keystrokeIntervals.clone())
        val avgCadenceMs = cleanedIntervals.average().toFloat()

        val signal = screenFrameDiff ?: DoubleArray(16) { 0.1 }
        val bands = bandExtractor.extractBands(signal)

        mmsiEngine.processFrameInPlace(
            af7Alpha = bands.alpha,
            af8Alpha = bands.alpha,
            betaHigh = (bands.betaHigh * 1.5) + (if (avgCadenceMs in 10.0..120.0) 5.0 else 0.0),
            thetaPost = bands.theta + (currentBssidHash * 0.1),
            age = 30.0,
            sex = "M",
            deltaF7 = bands.delta,
            deltaF8 = bands.delta,
            out = frameOutput
        )

        val frictionW = frameOutput.wBounded
        val rho = frameOutput.rho

        fieldEngine.currentRho = max(0.01f, rho.toFloat())

        _behaviorFlow.value = BehaviorMetrics(
            typingCadenceMs = avgCadenceMs,
            dwellTimeSec = System.currentTimeMillis() / 1000,
            frictionW = frictionW,
            backpressureRho = rho,
            isSeinsmodus = frictionW <= 1.0
        )
    }
}

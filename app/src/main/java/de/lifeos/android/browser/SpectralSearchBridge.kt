package de.lifeos.android.browser

import com.mmsi.neuro.engine.core.MmsiArtifactFilterEngine
import com.mmsi.neuro.engine.core.MmsiCoreEngineV38
import com.mmsi.neuro.engine.core.MmsiFrameOutput
import com.mmsi.neuro.engine.core.SpectralBandExtractor
import de.lifeos.core.field.DeterministicFieldEngine

class SpectralSearchBridge(
    private val fieldEngine: DeterministicFieldEngine
) {
    private val bandExtractor = SpectralBandExtractor()
    private val mmsiEngine = MmsiCoreEngineV38()
    private val frameOutput = MmsiFrameOutput()
    private var previousSignal = DoubleArray(256) { 0.5 }

    fun processBrowserFrame(currentSignal: DoubleArray): Double {
        val floatData = FloatArray(currentSignal.size) { currentSignal[it].toFloat() }
        val cleaned = MmsiArtifactFilterEngine.cleanChannelDataInPlace(floatData)
        val cleanedDouble = DoubleArray(cleaned.size) { cleaned[it].toDouble() }

        val bandsCurrent = bandExtractor.extractBands(cleanedDouble)
        val bandsPrev = bandExtractor.extractBands(previousSignal)
        previousSignal = cleanedDouble.clone()

        mmsiEngine.processFrameInPlace(
            af7Alpha = bandsCurrent.alpha,
            af8Alpha = bandsPrev.alpha,
            betaHigh = bandsCurrent.betaHigh,
            thetaPost = bandsCurrent.theta,
            age = 30.0,
            sex = "M",
            deltaF7 = bandsCurrent.delta,
            deltaF8 = bandsPrev.delta,
            out = frameOutput
        )

        return frameOutput.wBounded
    }
}

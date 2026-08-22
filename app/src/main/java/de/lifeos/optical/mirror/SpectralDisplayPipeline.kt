package de.lifeos.optical.mirror

import com.mmsi.neuro.engine.core.ExtractedBands
import com.mmsi.neuro.engine.core.MmsiArtifactFilterEngine
import com.mmsi.neuro.engine.core.MmsiCoreEngineV38
import com.mmsi.neuro.engine.core.MmsiFrameOutput
import com.mmsi.neuro.engine.core.SpectralBandExtractor

data class DisplayBlockUpdate(
    val blockId: Int,
    val isUpdated: Boolean,
    val rawHarmonics: ExtractedBands
)

class SpectralDisplayPipeline(
    private val bandExtractor: SpectralBandExtractor = SpectralBandExtractor(),
    private val mmsiEngine: MmsiCoreEngineV38 = MmsiCoreEngineV38()
) {
    private val frameOutput = MmsiFrameOutput()

    fun processBlockSignal(
        blockId: Int,
        currentSignal: DoubleArray,
        previousSignal: DoubleArray
    ): DisplayBlockUpdate {
        val floatData = FloatArray(currentSignal.size) { currentSignal[it].toFloat() }
        val cleaned = MmsiArtifactFilterEngine.cleanChannelDataInPlace(floatData)
        val cleanedDouble = DoubleArray(cleaned.size) { cleaned[it].toDouble() }

        val bandsCurrent = bandExtractor.extractBands(cleanedDouble)
        val bandsPrev = bandExtractor.extractBands(previousSignal)

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

        return DisplayBlockUpdate(
            blockId = blockId,
            isUpdated = frameOutput.wBounded > 1.0,
            rawHarmonics = bandsCurrent
        )
    }
}

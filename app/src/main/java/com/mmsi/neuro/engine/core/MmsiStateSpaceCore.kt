package com.mmsi.neuro.engine.core

class MmsiStateSpaceCore {
    private val filter = MmsiArtifactFilterEngine
    private val extractor = SpectralBandExtractor()
    private val coreEngine = MmsiCoreEngineV38()
    private val frameOutput = MmsiFrameOutput()

    fun processSignalPipeline(rawSignal: FloatArray): MmsiFrameOutput {
        val cleaned = filter.cleanChannelDataInPlace(rawSignal)
        val doubleSignal = DoubleArray(cleaned.size) { cleaned[it].toDouble() }
        val bands = extractor.extractBands(doubleSignal)

        coreEngine.processFrameInPlace(
            af7Alpha = bands.alpha,
            af8Alpha = bands.alpha,
            betaHigh = bands.betaHigh,
            thetaPost = bands.theta,
            age = 30.0,
            sex = "M",
            deltaF7 = bands.delta,
            deltaF8 = bands.delta,
            out = frameOutput
        )
        return frameOutput
    }
}

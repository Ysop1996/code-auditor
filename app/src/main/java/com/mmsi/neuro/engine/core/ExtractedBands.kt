package com.mmsi.neuro.engine.core

data class ExtractedBands(
    val delta: Double = 0.0,
    val theta: Double = 0.0,
    val alpha: Double = 0.0,
    val betaLow: Double = 0.0,
    val betaHigh: Double = 0.0,
    val gamma: Double = 0.0
)

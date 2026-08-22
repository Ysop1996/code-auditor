package com.mmsi.neuro.engine.core

class MmsiFrameOutput {
    var rho: Double = 0.0
    var wBounded: Double = 1.0
    var zDamping: Double = 0.0
    var yLoad: Double = 0.0
    var predictedCluster: String = "SEINSMODUS"
    var isValid: Boolean = true

    fun reset() {
        rho = 0.0
        wBounded = 1.0
        zDamping = 0.0
        yLoad = 0.0
        predictedCluster = "SEINSMODUS"
        isValid = true
    }
}

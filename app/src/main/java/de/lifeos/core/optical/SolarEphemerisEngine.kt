package de.lifeos.core.optical

import kotlin.math.*

data class SolarState(
    val sunDirection: Vector3,
    val directIrradiance: FloatArray, // Spektrale Intensität der 6 MMSI-Wellenlängen
    val skyAmbient: FloatArray
)

object SolarEphemerisEngine {

    fun computeSolarState(
        epochMs: Long,
        latitudeDeg: Double = 47.9959, // Standard: Freiburg
        longitudeDeg: Double = 7.8522
    ): SolarState {
        val daysSince2000 = (epochMs - 946728000000L) / 86400000.0

        // Mittlere Anomalie & Ekliptik
        val M = Math.toRadians((357.529 + 0.98560028 * daysSince2000) % 360.0)
        val L = Math.toRadians((280.459 + 0.98564736 * daysSince2000) % 360.0 + 1.915 * sin(M) + 0.020 * sin(2 * M))

        // Deklination & Zeitgleichung
        val sinDec = sin(Math.toRadians(23.439)) * sin(L)
        val dec = asin(sinDec)

        // Stundenwinkel
        val utcHour = ((epochMs % 86400000L) / 3600000.0)
        val solarTime = (utcHour + longitudeDeg / 15.0) % 24.0
        val hourAngle = Math.toRadians((solarTime - 12.0) * 15.0)

        val latRad = Math.toRadians(latitudeDeg)
        val elevation = asin(sin(latRad) * sin(dec) + cos(latRad) * cos(dec) * cos(hourAngle))
        val azimuth = atan2(-sin(hourAngle), tan(dec) * cos(latRad) - sin(latRad) * cos(hourAngle))

        val sunDir = Vector3(
            (cos(elevation) * sin(azimuth)).toFloat(),
            sin(elevation).toFloat(),
            (cos(elevation) * cos(azimuth)).toFloat()
        ).normalize()

        // Rayleigh-Atmosphärenfilterung nach Wellenlängen [400nm, 460nm, 500nm, 535nm, 580nm, 680nm]
        val direct = FloatArray(6)
        val ambient = FloatArray(6)
        val airMass = (1.0f / max(0.05f, sin(elevation.toFloat()))).coerceIn(1.0f, 20.0f)
        val wavelengths = floatArrayOf(400e-9f, 460e-9f, 500e-9f, 535e-9f, 580e-9f, 680e-9f)

        for (i in wavelengths.indices) {
            val rayleighCoeff = (8.0f * PI.toFloat().pow(3) * (1.0003f.pow(2) - 1).pow(2)) / (3 * 2.547e25f * wavelengths[i].pow(4))
            val transmittance = exp(-rayleighCoeff * airMass * 8400f)
            direct[i] = transmittance * 1.2f
            ambient[i] = (1.0f - transmittance) * 0.35f
        }

        return SolarState(sunDir, direct, ambient)
    }
}

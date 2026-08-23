package de.lifeos.android.telemetry

import android.app.ActivityManager
import android.content.Context
import android.os.BatteryManager
import android.os.Build
import android.os.PowerManager

data class HardwareTelemetryMetrics(
    val thermalStatus: Int,
    val dischargeCurrentMa: Float,
    val availableRamPercent: Float,
    val hardwareLoadFactorY: Double,
    val shouldForceThrottle: Boolean
)

class HardwareLoadCouplingEngine(private val context: Context) {
    private val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
    private val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
    private val batteryManager = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
    private var currentThermalStatus = PowerManager.THERMAL_STATUS_NONE

    init {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            powerManager.addThermalStatusListener { status -> currentThermalStatus = status }
        }
    }

    fun sampleHardwareState(): HardwareTelemetryMetrics {
        val currentNowMicroAmp = batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW)
        val dischargeMa = (currentNowMicroAmp / 1000f).coerceAtLeast(0f)

        val memInfo = ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(memInfo)
        val availableRamPercent = (memInfo.availMem.toDouble() / memInfo.totalMem.toDouble() * 100.0).toFloat()

        var hwLoad = 0.0
        hwLoad += when (currentThermalStatus) {
            PowerManager.THERMAL_STATUS_LIGHT -> 1.5
            PowerManager.THERMAL_STATUS_MODERATE -> 3.5
            PowerManager.THERMAL_STATUS_SEVERE -> 7.0
            PowerManager.THERMAL_STATUS_CRITICAL,
            PowerManager.THERMAL_STATUS_EMERGENCY,
            PowerManager.THERMAL_STATUS_SHUTDOWN -> 12.0
            else -> 0.0
        }

        if (dischargeMa > 500f) hwLoad += ((dischargeMa - 500f) / 200f).toDouble().coerceAtMost(5.0)
        if (availableRamPercent < 15f) hwLoad += ((15f - availableRamPercent) * 0.5).coerceAtMost(6.0)

        val forceThrottle = currentThermalStatus >= PowerManager.THERMAL_STATUS_MODERATE || availableRamPercent < 10f

        return HardwareTelemetryMetrics(
            thermalStatus = currentThermalStatus,
            dischargeCurrentMa = dischargeMa,
            availableRamPercent = availableRamPercent,
            hardwareLoadFactorY = hwLoad,
            shouldForceThrottle = forceThrottle
        )
    }
}

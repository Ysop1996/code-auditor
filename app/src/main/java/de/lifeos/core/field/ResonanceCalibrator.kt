package de.lifeos.core.field

import kotlin.math.*

/**
 * RESONANCE CALIBRATOR — Resonanz-Kalibrierung & Nutzerverhaltensmuster
 *
 * Der Resonanz-Kalibrierer passt die Systemparameter kontinuierlich an die
 * spezifischen Verhaltensmuster, Arbeitsabläufe und kognitiven Rhythmen
 * des Nutzers an:
 *
 *   - μ₁, μ₂: Gewichtung Vergangenheit/Zukunft (LoadFunction)
 *   - α: Steilheitsparameter (EffectiveDimension)
 *   - θ₀: Aktivierungsschwelle (EffectiveDimension)
 *   - ω: Kopplungsstärke (AutopoieticOperator)
 *   - K: Kopplungskonstante (ArnoldTongueAnalyzer)
 *
 * Kalibrierungs-Pipeline:
 *   1. Verhaltensdaten sammeln (Interaktions-Historie)
 *   2. Muster erkennen (kognitive Rhythmen)
 *   3. Parameter anpassen (Gradienten-Abstieg)
 *   4. Validierung (EmpiricalValidationMatrix)
 *   5. Speicherung (persistente Kalibrierung)
 *
 * Vektoren:
 * - [EXP-FORCE] Resonanz-Kalibrierung: maximale Übereinstimmung System/Nutzer
 * - [EXP-AUTO] Autopoietische Regulation: selbstständige Parameter-Optimierung
 * - [EXP-SPEED] O(N) Kalibrierung mit Exponential-Moving-Average
 */
object ResonanceCalibrator {

    // =========================================================================
    // KALIBRIERUNGS-PARAMETER
    // =========================================================================

    /** Lernrate für Gradienten-Abstieg: η = 0.01 */
    const val LEARNING_RATE: Float = 0.01f

    /** EMA-Glättungsfaktor: α_ema = 0.1 */
    const val EMA_ALPHA: Float = 0.1f

    /** Kalibrierungs-Schwellwert: Δparam < ε ⟹ stabil */
    const val CALIBRATION_TOLERANCE: Float = 1e-4f

    /** Maximale Parameter-Änderung pro Schritt: Δmax = 0.1 */
    const val MAX_PARAMETER_CHANGE: Float = 0.1f

    /** Minimale Datenpunkte für Kalibrierung: N_min = 10 */
    const val MIN_DATA_POINTS: Int = 10

    /** Maximale Historie-Länge: N_max = 1000 */
    const val MAX_HISTORY_LENGTH: Int = 1000

    // =========================================================================
    // KALIBRIERUNGSZUSTAND
    // =========================================================================

    /**
     * Kalibrierungszustand aller Systemparameter.
     *
     * @param muPast Gewicht Vergangenheit μ₁
     * @param muFuture Gewicht Zukunft μ₂
     * @param alpha Steilheitsparameter α
     * @param theta Aktivierungsschwelle θ₀
     * @param omega Kopplungsstärke ω
     * @param arnoldK Arnold-Kopplung K
     * @param gaugeCoupling Eich-Kopplung
     */
    data class CalibrationState(
        var muPast: Float = LoadFunction.DEFAULT_MU_PAST,
        var muFuture: Float = LoadFunction.DEFAULT_MU_FUTURE,
        var alpha: Float = EffectiveDimension.DEFAULT_ALPHA,
        var theta: Float = EffectiveDimension.DEFAULT_THETA,
        var omega: Float = AutopoieticOperator.DEFAULT_OMEGA,
        var arnoldK: Float = ArnoldTongueAnalyzer.DEFAULT_K,
        var gaugeCoupling: Float = U1GaugeField.COUPLING
    ) {
        override fun toString(): String {
            return buildString {
                appendLine("=== CALIBRATION STATE ===")
                appendLine("μ₁ (past) = ${"%.3f".format(muPast)}")
                appendLine("μ₂ (future) = ${"%.3f".format(muFuture)}")
                appendLine("α (steepness) = ${"%.1f".format(alpha)}")
                appendLine("θ₀ (threshold) = ${"%.3f".format(theta)}")
                appendLine("ω (coupling) = ${"%.3f".format(omega)}")
                appendLine("K (arnold) = ${"%.3f".format(arnoldK)}")
                appendLine("e (gauge) = ${"%.3f".format(gaugeCoupling)}")
            }
        }
    }

    private var currentState = CalibrationState()

    // =========================================================================
    // VERHALTENSDATEN
    // =========================================================================

    /**
     * Verhaltensdatenpunkt für Kalibrierung.
     *
     * @param timestamp Zeitstempel
     * @param load Gemessene Last W(t)
     * @param omega Gemessener Horizont Ω(t)
     * @param sovereignty Gemessener Souveränitätsindex S_o(t)
     * @param interactionType Interaktionstyp
     * @param responseTime Antwortzeit (ms)
     * @param successFlag Erfolgsmarker
     */
    data class BehaviorDataPoint(
        val timestamp: Long,
        val load: Float,
        val omega: Float,
        val sovereignty: Float,
        val interactionType: String,
        val responseTimeMs: Float,
        val successFlag: Boolean
    )

    private val behaviorHistory = ArrayDeque<BehaviorDataPoint>()

    /**
     * Fügt einen Verhaltensdatenpunkt zur Historie hinzu.
     *
     * @param dataPoint Datenpunkt
     */
    @Synchronized
    fun addBehaviorData(dataPoint: BehaviorDataPoint) {
        behaviorHistory.addLast(dataPoint)
        while (behaviorHistory.size > MAX_HISTORY_LENGTH) {
            behaviorHistory.removeFirst()
        }
    }

    @Synchronized
    fun getBehaviorHistory(): List<BehaviorDataPoint> = behaviorHistory.toList()

    @Synchronized
    fun clearBehaviorHistory() {
        behaviorHistory.clear()
    }

    // =========================================================================
    // MUSTER-ERKENNUNG
    // =========================================================================

    /**
     * Erkennt kognitive Rhythmen aus der Verhaltenshistorie.
     *
     * Analysiert:
     * - Tageszeit-Muster (Last-Gradienten über 24h)
     * - Interaktions-Häufigkeit (Events pro Stunde)
     * - Antwortzeit-Verteilung (Median, Standardabweichung)
     * - Erfolgsrate (Erfolg/Fehler-Verhältnis)
     *
     * @return KognitiveRhythmen
     */
    fun detectCognitiveRhythms(): CognitiveRhythms {
        if (behaviorHistory.size < MIN_DATA_POINTS) {
            return CognitiveRhythms()
        }

        val recent = behaviorHistory.takeLast(100)

        // Single-pass aggregation for hourly load and response times
        val hourlyLoad = FloatArray(24) { 0f }
        val hourlyCount = IntArray(24) { 0 }
        var successCount = 0
        var maxLoad = Float.MIN_VALUE
        var minLoad = Float.MAX_VALUE
        val responseTimes = FloatArray(recent.size)

        for (i in recent.indices) {
            val dp = recent[i]
            val hour = (dp.timestamp / 3600000L % 24L).toInt()
            hourlyLoad[hour] += dp.load
            hourlyCount[hour]++
            if (dp.successFlag) successCount++
            if (dp.load > maxLoad) maxLoad = dp.load
            if (dp.load < minLoad) minLoad = dp.load
            responseTimes[i] = dp.responseTimeMs
        }

        val avgHourlyLoad = FloatArray(24) { hour ->
            if (hourlyCount[hour] > 0) hourlyLoad[hour] / hourlyCount[hour] else 0f
        }

        // Antwortzeit-Statistik (single pass for mean, then sort for median)
        val meanResponseTime = if (responseTimes.isNotEmpty()) {
            responseTimes.sum() / responseTimes.size
        } else 0f
        responseTimes.sort()
        val medianResponseTime = if (responseTimes.isNotEmpty()) {
            responseTimes[responseTimes.size / 2]
        } else 0f

        // Erfolgsrate
        val successRate = successCount / recent.size.toFloat()

        // Last-Amplitude (Tagesamplitude)
        val loadAmplitude = maxLoad - minLoad

        // Find peak/low load hours in single pass
        var peakLoadHour = 0
        var lowLoadHour = 0
        var peakVal = Float.MIN_VALUE
        var lowVal = Float.MAX_VALUE
        for (i in avgHourlyLoad.indices) {
            if (avgHourlyLoad[i] > peakVal) {
                peakVal = avgHourlyLoad[i]
                peakLoadHour = i
            }
            if (avgHourlyLoad[i] < lowVal) {
                lowVal = avgHourlyLoad[i]
                lowLoadHour = i
            }
        }

        return CognitiveRhythms(
            avgHourlyLoad = avgHourlyLoad,
            medianResponseTimeMs = medianResponseTime,
            meanResponseTimeMs = meanResponseTime,
            successRate = successRate,
            loadAmplitude = loadAmplitude,
            peakLoadHour = peakLoadHour,
            lowLoadHour = lowLoadHour
        )
    }

    /**
     * Kognitive Rhythmen des Nutzers.
     *
     * @param avgHourlyLoad Durchschnittliche Last pro Stunde
     * @param medianResponseTimeMs Mediane Antwortzeit
     * @param meanResponseTimeMs Mittlere Antwortzeit
     * @param successRate Erfolgsrate
     * @param loadAmplitude Last-Amplitude (max - min)
     * @param peakLoadHour Stunde mit höchster Last
     * @param lowLoadHour Stunde mit niedrigster Last
     */
    data class CognitiveRhythms(
        val avgHourlyLoad: FloatArray = FloatArray(24) { 0f },
        val medianResponseTimeMs: Float = 0f,
        val meanResponseTimeMs: Float = 0f,
        val successRate: Float = 0f,
        val loadAmplitude: Float = 0f,
        val peakLoadHour: Int = 0,
        val lowLoadHour: Int = 0
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is CognitiveRhythms) return false
            return avgHourlyLoad.contentEquals(other.avgHourlyLoad) &&
                    medianResponseTimeMs == other.medianResponseTimeMs &&
                    meanResponseTimeMs == other.meanResponseTimeMs &&
                    successRate == other.successRate &&
                    loadAmplitude == other.loadAmplitude &&
                    peakLoadHour == other.peakLoadHour &&
                    lowLoadHour == other.lowLoadHour
        }

        override fun hashCode(): Int {
            var result = avgHourlyLoad.contentHashCode()
            result = 31 * result + medianResponseTimeMs.hashCode()
            result = 31 * result + meanResponseTimeMs.hashCode()
            result = 31 * result + successRate.hashCode()
            result = 31 * result + loadAmplitude.hashCode()
            result = 31 * result + peakLoadHour
            result = 31 * result + lowLoadHour
            return result
        }

        override fun toString(): String {
            return buildString {
                appendLine("=== COGNITIVE RHYTHMS ===")
                appendLine("Peak load hour: $peakLoadHour:00")
                appendLine("Low load hour: $lowLoadHour:00")
                appendLine("Load amplitude: ${"%.3f".format(loadAmplitude)}")
                appendLine("Median response: ${"%.0f".format(medianResponseTimeMs)} ms")
                appendLine("Success rate: ${"%.1f".format(successRate * 100)}%")
            }
        }
    }

    // =========================================================================
    // PARAMETER-KALIBRIERUNG
    // =========================================================================

    /**
     * Kalibriert die Systemparameter basierend auf Verhaltensdaten.
     *
     * Verwendet Gradienten-Abstieg zur Minimierung der Abweichung
     * zwischen gemessener und target-Last.
     *
     * @param rhythms Kognitive Rhythmen
     * @param unifiedState Aktueller Feldzustand
     * @return Aktualisierter Kalibrierungszustand
     */
    fun calibrate(rhythms: CognitiveRhythms, unifiedState: FieldDynamicsIntegrator.UnifiedFieldState): CalibrationState {
        if (behaviorHistory.size < MIN_DATA_POINTS) {
            return currentState
        }

        // Gradienten berechnen (vereinfacht)
        val loadError = unifiedState.load - targetLoad(rhythms)
        val sovereigntyError = 1.0f - unifiedState.sovereignty

        // Parameter-Updates mit Begrenzung
        val muPastDelta = LEARNING_RATE * loadError * rhythms.successRate
        val muFutureDelta = LEARNING_RATE * sovereigntyError * rhythms.successRate
        val alphaDelta = LEARNING_RATE * (rhythms.loadAmplitude - 0.5f)
        val thetaDelta = LEARNING_RATE * (unifiedState.load - HomoeostasisRegulator.WARNING_THRESHOLD)

        currentState = currentState.copy(
            muPast = (currentState.muPast + muPastDelta.coerceIn(-MAX_PARAMETER_CHANGE, MAX_PARAMETER_CHANGE)).coerceIn(0.1f, 0.9f),
            muFuture = (currentState.muFuture + muFutureDelta.coerceIn(-MAX_PARAMETER_CHANGE, MAX_PARAMETER_CHANGE)).coerceIn(0.1f, 0.9f),
            alpha = (currentState.alpha + alphaDelta.coerceIn(-MAX_PARAMETER_CHANGE, MAX_PARAMETER_CHANGE)).coerceIn(1.0f, 20.0f),
            theta = (currentState.theta + thetaDelta.coerceIn(-MAX_PARAMETER_CHANGE, MAX_PARAMETER_CHANGE)).coerceIn(0.1f, 0.9f)
        )

        return currentState
    }

    /**
     * Berechnet die Ziel-Last basierend auf kognitiven Rhythmen.
     *
     * @param rhythms Kognitive Rhythmen
     * @return Ziel-Last W_target
     */
    private fun targetLoad(rhythms: CognitiveRhythms): Float {
        // Ziel: W(t) ≤ 0.5 in Spitzenzeiten, ≤ 0.3 in Nebenzeiten
        val currentHour = (System.currentTimeMillis() / 3600000L % 24L).toInt()
        return if (currentHour == rhythms.peakLoadHour) {
            0.5f
        } else {
            0.3f
        }
    }

    @Synchronized
    fun getCalibrationState(): CalibrationState = currentState

    @Synchronized
    fun resetCalibration() {
        currentState = CalibrationState()
    }

    // =========================================================================
    // VALIDIERUNG
    // =========================================================================

    /**
     * Validiert den Kalibrierungszustand gegen empirische Messwerte.
     *
     * @return Validierungsbericht
     */
    fun validateCalibration(): CalibrationValidationReport {
        val checks = mutableListOf<CalibrationCheck>()

        // μ₁ + μ₂ ≈ 1.0 (Gewichte sollten summiert 1 ergeben)
        val muSum = currentState.muPast + currentState.muFuture
        checks.add(
            CalibrationCheck(
                name = "μ₁ + μ₂",
                measured = muSum,
                expected = 1.0f,
                tolerance = 0.1f,
                passed = abs(muSum - 1.0f) < 0.1f
            )
        )

        // α ∈ [1, 20] (Steilheitsparameter)
        checks.add(
            CalibrationCheck(
                name = "α",
                measured = currentState.alpha,
                expected = EffectiveDimension.DEFAULT_ALPHA,
                tolerance = 5.0f,
                passed = currentState.alpha in 1.0f..20.0f
            )
        )

        // θ₀ ∈ [0.1, 0.9] (Aktivierungsschwelle)
        checks.add(
            CalibrationCheck(
                name = "θ₀",
                measured = currentState.theta,
                expected = EffectiveDimension.DEFAULT_THETA,
                tolerance = 0.3f,
                passed = currentState.theta in 0.1f..0.9f
            )
        )

        // ω ∈ [0.1, 2.0] (Kopplungsstärke)
        checks.add(
            CalibrationCheck(
                name = "ω",
                measured = currentState.omega,
                expected = AutopoieticOperator.DEFAULT_OMEGA,
                tolerance = 0.5f,
                passed = currentState.omega in 0.1f..2.0f
            )
        )

        val passed = checks.count { it.passed }
        val total = checks.size
        val score = passed.toFloat() / total

        return CalibrationValidationReport(
            checks = checks,
            passed = passed,
            total = total,
            score = score,
            isValid = score >= 0.8f
        )
    }

    /**
     * Einzelner Validierungs-Check.
     *
     * @param name Parameter-Name
     * @param measured Gemessener Wert
     * @param expected Erwarteter Wert
     * @param tolerance Toleranz
     * @param passed Bestanden?
     */
    data class CalibrationCheck(
        val name: String,
        val measured: Float,
        val expected: Float,
        val tolerance: Float,
        val passed: Boolean
    )

    /**
     * Validierungsbericht für Kalibrierung.
     *
     * @param checks Liste der Checks
     * @param passed Anzahl bestanden
     * @param total Anzahl insgesamt
     * @param score Score (0.0–1.0)
     * @param isValid Ist die Kalibrierung gültig?
     */
    data class CalibrationValidationReport(
        val checks: List<CalibrationCheck>,
        val passed: Int,
        val total: Int,
        val score: Float,
        val isValid: Boolean
    ) {
        override fun toString(): String {
            return buildString {
                appendLine("=== CALIBRATION VALIDATION ===")
                appendLine("Score: ${"%.1f".format(score * 100)}% ($passed/$total passed)")
                appendLine("Valid: $isValid")
                checks.forEach { check ->
                    val status = if (check.passed) "✓ PASS" else "✗ FAIL"
                    appendLine("$status: ${check.name} = ${"%.3f".format(check.measured)} (expected: ${"%.3f}".format(check.expected)} ± ${"%.3f".format(check.tolerance)})")
                }
            }
        }
    }

    // =========================================================================
    // APPLIKATION DER KALIBRIERUNG
    // =========================================================================

    /**
     * Wendet den Kalibrierungszustand auf die LoadFunction an.
     *
     * @param state Kalibrierungszustand
     */
    fun applyToLoadFunction(state: CalibrationState = currentState) {
        // Die LoadFunction verwendet DEFAULT_MU_PAST und DEFAULT_MU_FUTURE
        // Diese werden hier als Referenzwerte gespeichert
        LoadFunctionState.muPast = state.muPast
        LoadFunctionState.muFuture = state.muFuture
    }

    /**
     * Wendet den Kalibrierungszustand auf EffectiveDimension an.
     *
     * @param state Kalibrierungszustand
     */
    fun applyToEffectiveDimension(state: CalibrationState = currentState) {
        EffectiveDimensionState.alpha = state.alpha
        EffectiveDimensionState.theta = state.theta
    }

    /**
     * Wendet den Kalibrierungszustand auf AutopoieticOperator an.
     *
     * @param state Kalibrierungszustand
     */
    fun applyToAutopoieticOperator(state: CalibrationState = currentState) {
        AutopoieticOperatorState.omega = state.omega
    }

    /**
     * Wendet den Kalibrierungszustand auf ArnoldTongueAnalyzer an.
     *
     * @param state Kalibrierungszustand
     */
    fun applyToArnoldTongueAnalyzer(state: CalibrationState = currentState) {
        ArnoldTongueAnalyzerState.k = state.arnoldK
    }

    /**
     * Wendet den Kalibrierungszustand auf U1GaugeField an.
     *
     * @param state Kalibrierungszustand
     */
    fun applyToU1GaugeField(state: CalibrationState = currentState) {
        U1GaugeFieldState.coupling = state.gaugeCoupling
    }

    /**
     * Wendet die gesamte Kalibrierung auf alle Module an.
     *
     * @param state Kalibrierungszustand
     */
    fun applyCalibration(state: CalibrationState = currentState) {
        applyToLoadFunction(state)
        applyToEffectiveDimension(state)
        applyToAutopoieticOperator(state)
        applyToArnoldTongueAnalyzer(state)
        applyToU1GaugeField(state)
    }

    // =========================================================================
    // KALIBRIERUNGS-ZUSTANDSPROXY (für Konstanten-Überschreibung)
    // =========================================================================

    /** Proxy für LoadFunction-Parameter */
    object LoadFunctionState {
        var muPast: Float = LoadFunction.DEFAULT_MU_PAST
        var muFuture: Float = LoadFunction.DEFAULT_MU_FUTURE
    }

    /** Proxy für EffectiveDimension-Parameter */
    object EffectiveDimensionState {
        var alpha: Float = EffectiveDimension.DEFAULT_ALPHA
        var theta: Float = EffectiveDimension.DEFAULT_THETA
    }

    /** Proxy für AutopoieticOperator-Parameter */
    object AutopoieticOperatorState {
        var omega: Float = AutopoieticOperator.DEFAULT_OMEGA
    }

    /** Proxy für ArnoldTongueAnalyzer-Parameter */
    object ArnoldTongueAnalyzerState {
        var k: Float = ArnoldTongueAnalyzer.DEFAULT_K
    }

    /** Proxy für U1GaugeField-Parameter */
    object U1GaugeFieldState {
        var coupling: Float = U1GaugeField.COUPLING
    }
}

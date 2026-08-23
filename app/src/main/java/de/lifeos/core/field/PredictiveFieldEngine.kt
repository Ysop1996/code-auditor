package de.lifeos.core.field

import kotlin.math.*

/**
 * PREDICTIVE FIELD ENGINE — Vorhersage-Engine
 *
 * Die PredictiveFieldEngine nutzt die Feld-Dynamik, um zukünftige Zustände
 * vorherzusagen. Sie analysiert Trends, erkennt Anomalien und berechnet
 * Konfidenzintervalle für Vorhersagen.
 *
 * Methoden:
 * 1. Trend-Analyse: Extrapolation der aktuellen Trajektorie
 * 2. Muster-Erkennung: Wiederkehrende Dynamiken in der Zeitreihe
 * 3. Anomalie-Detektion: Abweichungen vom erwarteten Verlauf
 * 4. Konfidenz-Berechnung: Statistische Sicherheit der Vorhersage
 * 5. Szenario-Simulation: "Was-wäre-wenn"-Analysen
 *
 * Vektoren:
 * - [EXP-FORCE] Vorhersage: Proaktive Reaktion auf zukünftige Zustände
 * - [EXP-AUTO] Autopoietische Antizipation: Selbst-Schutz vor Staudruck
 * - [EXP-SPEED] O(n) Trend-Extrapolation, O(1) Anomalie-Erkennung
 */
object PredictiveFieldEngine {

    // =========================================================================
    // PREDICTION PARAMETERS
    // =========================================================================

    /** Minimale Datenpunkte für Trend-Analyse */
    const val MIN_DATA_POINTS: Int = 5

    /** Maximale Vorhersage-Horizonte (in Schritten) */
    const val MAX_PREDICTION_HORIZON: Int = 30

    /** Standard-Konfidenz-Schwellwert */
    const val DEFAULT_CONFIDENCE_THRESHOLD: Float = 0.7f

    /** Anomalie-Schwellwert (Standardabweichungen) */
    const val ANOMALY_STD_DEV_THRESHOLD: Float = 2.5f

    /** Trend-Extrapolationsfaktor */
    const val TREND_EXTRAPOLATION_FACTOR: Float = 0.8f

    // =========================================================================
    // PREDICTION TYPES
    // =========================================================================

    /**
     * Vorhersage-Horizont.
     */
    enum class PredictionHorizon {
        /** Sehr kurzfristig (1-5 Schritte) */
        IMMEDIATE,

        /** Kurzfristig (6-15 Schritte) */
        SHORT_TERM,

        /** Mittelfristig (16-30 Schritte) */
        MEDIUM_TERM,

        /** Langfristig (>30 Schritte) */
        LONG_TERM
    }

    /**
     * Vorhersage-Richtung.
     */
    enum class PredictionDirection {
        /** Steigend */
        RISING,

        /** Fallend */
        FALLING,

        /** Stabil */
        STABLE,

        /** Unbestimmt */
        UNCERTAIN
    }

    // =========================================================================
    // PREDICTION RESULT
    // =========================================================================

    /**
     * Vorhersage-Ergebnis.
     *
     * @param predictedValue Vorhergesagter Wert
     * @param confidence Konfidenz [0, 1]
     * @param direction Richtung
     * @param horizon Horizont
     * @param lowerBound Untere Konfidenzgrenze
     * @param upperBound Obere Konfidenzgrenze
     * @param timestamp Zeitstempel der Vorhersage
     * @param metadata Zusätzliche Metadaten
     */
    data class Prediction(
        val predictedValue: Float,
        val confidence: Float,
        val direction: PredictionDirection,
        val horizon: PredictionHorizon,
        val lowerBound: Float,
        val upperBound: Float,
        val timestamp: Long,
        val metadata: Map<String, String> = emptyMap()
    ) {
        val isHighConfidence: Boolean
            get() = confidence >= DEFAULT_CONFIDENCE_THRESHOLD

        val confidenceInterval: Float
            get() = upperBound - lowerBound

        override fun toString(): String {
            return buildString {
                appendLine("=== PREDICTION ===")
                appendLine("Value: ${"%.3f".format(predictedValue)}")
                appendLine("Confidence: ${"%.1f".format(confidence * 100)}%")
                appendLine("Direction: $direction")
                appendLine("Horizon: $horizon")
                appendLine("CI: [${"%.3f".format(lowerBound)}, ${"%.3f".format(upperBound)}]")
            }
        }
    }

    // =========================================================================
    // TIME SERIES
    // =========================================================================

    /**
     * Zeitreihen-Datenpunkt.
     *
     * @param timestamp Zeitstempel
     * @param value Wert
     * @param metadata Zusätzliche Daten
     */
    data class TimeSeriesPoint(
        val timestamp: Long,
        val value: Float,
        val metadata: Map<String, String> = emptyMap()
    )

    /**
     * Zeitreihe für Vorhersagen.
     *
     * @param name Name der Zeitreihe
     * @param data Datenpunkte
     */
    data class TimeSeries(
        val name: String,
        val data: MutableList<TimeSeriesPoint> = mutableListOf()
    ) {
        /**
         * Fügt einen Datenpunkt hinzu.
         */
        fun addPoint(timestamp: Long, value: Float, metadata: Map<String, String> = emptyMap()) {
            data.add(TimeSeriesPoint(timestamp, value, metadata))
        }

        /**
         * Gibt die letzten N Datenpunkte zurück.
         */
        fun getLast(n: Int): List<TimeSeriesPoint> {
            return data.takeLast(n)
        }

        /**
         * Gibt den neuesten Wert zurück.
         */
        val latestValue: Float?
            get() = data.lastOrNull()?.value

        /**
         * Gibt den Durchschnittswert zurück.
         */
        val average: Float
            get() = if (data.isEmpty()) 0f else data.map { it.value }.average().toFloat()

        /**
         * Gibt die Standardabweichung zurück.
         */
        val stdDev: Float
            get() {
                if (data.size < 2) return 0f
                var sum = 0f
                var sumSq = 0f
                for (point in data) {
                    sum += point.value
                    sumSq += point.value * point.value
                }
                val mean = sum / data.size
                val variance = (sumSq / data.size) - (mean * mean)
                return sqrt(max(0f, variance))
            }

        val size: Int
            get() = data.size
    }

    // =========================================================================
    // TREND ANALYZER
    // =========================================================================

    /**
     * Analysiert den Trend einer Zeitreihe.
     *
     * @param series Zeitreihe
     * @param window Fenstergröße
     * @return Trend-Richtung und Stärke
     */
    fun analyzeTrend(series: TimeSeries, window: Int = MIN_DATA_POINTS): TrendAnalysis {
        if (series.data.size < window) {
            return TrendAnalysis(PredictionDirection.UNCERTAIN, 0f, 0f)
        }

        val recent = series.getLast(window)
        val values = recent.map { it.value }

        // Lineare Regression für Trend
        val n = values.size.toFloat()
        val xMean = (0 until values.size).average().toFloat()
        val yMean = values.average().toFloat()

        var numerator = 0f
        var denominator = 0f

        for (i in values.indices) {
            val x = i.toFloat() - xMean
            val y = values[i] - yMean
            numerator += x * y
            denominator += x * x
        }

        val slope = if (denominator != 0f) numerator / denominator else 0f
        val strength = min(abs(slope) * 10f, 1.0f)

        val direction = when {
            slope > 0.01f -> PredictionDirection.RISING
            slope < -0.01f -> PredictionDirection.FALLING
            else -> PredictionDirection.STABLE
        }

        return TrendAnalysis(direction, strength, slope)
    }

    /**
     * Trend-Analyse-Ergebnis.
     *
     * @param direction Richtung
     * @param strength Stärke [0, 1]
     * @param slope Steigung
     */
    data class TrendAnalysis(
        val direction: PredictionDirection,
        val strength: Float,
        val slope: Float
    )

    // =========================================================================
    // PREDICTION ENGINE
    // =========================================================================

    /**
     * Erstellt eine Vorhersage für eine Zeitreihe.
     *
     * @param series Zeitreihe
     * @param horizon Vorhersage-Horizont
     * @return Vorhersage
     */
    fun predict(series: TimeSeries, horizon: PredictionHorizon = PredictionHorizon.SHORT_TERM): Prediction {
        if (series.data.size < MIN_DATA_POINTS) {
            return createUncertainPrediction(series, horizon)
        }

        val trend = analyzeTrend(series)
        val lastValue = series.latestValue ?: 0f

        // Extrapolation basierend auf Trend
        val steps = when (horizon) {
            PredictionHorizon.IMMEDIATE -> 3
            PredictionHorizon.SHORT_TERM -> 10
            PredictionHorizon.MEDIUM_TERM -> 20
            PredictionHorizon.LONG_TERM -> 40
        }

        val predictedValue = lastValue + trend.slope * steps * TREND_EXTRAPOLATION_FACTOR
        val stdDev = series.stdDev
        val confidence = (trend.strength * (1.0f - stdDev / (abs(lastValue) + 1f))).coerceIn(0.0f, 1.0f)

        val margin = stdDev * 1.5f
        val lowerBound = (predictedValue - margin).coerceAtLeast(0f)
        val upperBound = predictedValue + margin

        return Prediction(
            predictedValue = predictedValue,
            confidence = confidence,
            direction = trend.direction,
            horizon = horizon,
            lowerBound = lowerBound,
            upperBound = upperBound,
            timestamp = System.currentTimeMillis(),
            metadata = mapOf(
                "series" to series.name,
                "slope" to trend.slope.toString(),
                "steps" to steps.toString()
            )
        )
    }

    /**
     * Erstellt eine unsichere Vorhersage.
     */
    private fun createUncertainPrediction(series: TimeSeries, horizon: PredictionHorizon): Prediction {
        val lastValue = series.latestValue ?: 0f
        return Prediction(
            predictedValue = lastValue,
            confidence = 0.1f,
            direction = PredictionDirection.UNCERTAIN,
            horizon = horizon,
            lowerBound = lastValue * 0.5f,
            upperBound = lastValue * 1.5f,
            timestamp = System.currentTimeMillis(),
            metadata = mapOf("reason" to "insufficient_data")
        )
    }

    // =========================================================================
    // ANOMALY DETECTOR
    // =========================================================================

    /**
     * Erkennt Anomalien in einer Zeitreihe.
     *
     * @param series Zeitreihe
     * @return Liste von Anomalien
     */
    fun detectAnomalies(series: TimeSeries): List<Anomaly> {
        if (series.data.size < MIN_DATA_POINTS) return emptyList()

        val mean = series.average
        val stdDev = series.stdDev
        val anomalies = mutableListOf<Anomaly>()

        for (point in series.data) {
            val zScore = abs(point.value - mean) / (stdDev + 0.001f)
            if (zScore > ANOMALY_STD_DEV_THRESHOLD) {
                anomalies.add(
                    Anomaly(
                        timestamp = point.timestamp,
                        value = point.value,
                        expectedValue = mean,
                        zScore = zScore,
                        severity = when {
                            zScore > 4.0f -> AnomalySeverity.CRITICAL
                            zScore > 3.0f -> AnomalySeverity.HIGH
                            zScore > 2.5f -> AnomalySeverity.MEDIUM
                            else -> AnomalySeverity.LOW
                        }
                    )
                )
            }
        }

        return anomalies
    }

    /**
     * Anomalie in einer Zeitreihe.
     *
     * @param timestamp Zeitstempel
     * @param value Tatsächlicher Wert
     * @param expectedValue Erwarteter Wert
     * @param zScore Z-Score
     * @param severity Schweregrad
     */
    data class Anomaly(
        val timestamp: Long,
        val value: Float,
        val expectedValue: Float,
        val zScore: Float,
        val severity: AnomalySeverity
    ) {
        override fun toString(): String {
            return "Anomaly[$severity] at $timestamp: value=$value, expected=$expectedValue, z=$zScore"
        }
    }

    /**
     * Anomalie-Schweregrad.
     */
    enum class AnomalySeverity {
        /** Niedrig */
        LOW,

        /** Mittel */
        MEDIUM,

        /** Hoch */
        HIGH,

        /** Kritisch */
        CRITICAL
    }

    // =========================================================================
    // SCENARIO SIMULATOR
    // =========================================================================

    /**
     * Simuliert ein "Was-wäre-wenn"-Szenario.
     *
     * @param series Zeitreihe
     * @param perturbation Störung (Wert, der hinzugefügt wird)
     * @param steps Anzahl Simulationsschritte
     * @return Simulationsergebnis
     */
    fun simulateScenario(
        series: TimeSeries,
        perturbation: Float,
        steps: Int = 10
    ): ScenarioResult {
        if (series.data.isEmpty()) {
            return ScenarioResult(emptyList(), 0f, 0f)
        }

        val lastValue = series.latestValue ?: 0f
        val trend = analyzeTrend(series)
        val simulation = mutableListOf<Float>()

        var currentValue = lastValue + perturbation
        for (i in 0 until steps) {
            simulation.add(currentValue)
            currentValue += trend.slope * TREND_EXTRAPOLATION_FACTOR
        }

        val finalValue = simulation.lastOrNull() ?: lastValue
        val impact = abs(finalValue - lastValue) / (abs(lastValue) + 0.001f)

        return ScenarioResult(
            trajectory = simulation,
            impact = impact.coerceIn(0.0f, 1.0f),
            finalValue = finalValue
        )
    }

    /**
     * Szenario-Simulationsergebnis.
     *
     * @param trajectory Simulierte Trajektorie
     * @param impact Auswirkung [0, 1]
     * @param finalValue Endwert
     */
    data class ScenarioResult(
        val trajectory: List<Float>,
        val impact: Float,
        val finalValue: Float
    ) {
        override fun toString(): String {
            return buildString {
                appendLine("=== SCENARIO RESULT ===")
                appendLine("Impact: ${"%.1f".format(impact * 100)}%")
                appendLine("Final Value: ${"%.3f".format(finalValue)}")
                appendLine("Trajectory: ${trajectory.take(5).joinToString { "%.2f".format(it) }}...")
            }
        }
    }

    // =========================================================================
    // PREDICTION HISTORY
    // =========================================================================

    private val predictionHistory = ArrayDeque<Prediction>()

    /**
     * Speichert eine Vorhersage in der Historie.
     */
    fun recordPrediction(prediction: Prediction) {
        predictionHistory.addLast(prediction)
        while (predictionHistory.size > 500) {
            predictionHistory.removeFirst()
        }
    }

    /**
     * Gibt die Vorhersage-Historie zurück.
     */
    fun getPredictionHistory(): List<Prediction> = predictionHistory.toList()

    /**
     * Berechnet die durchschnittliche Konfidenz der letzten Vorhersagen.
     */
    fun computeAverageConfidence(): Float {
        if (predictionHistory.isEmpty()) return 0f
        return predictionHistory.map { it.confidence }.average().toFloat()
    }

    /**
     * Löscht die Vorhersage-Historie.
     */
    fun clearHistory() {
        predictionHistory.clear()
    }
}

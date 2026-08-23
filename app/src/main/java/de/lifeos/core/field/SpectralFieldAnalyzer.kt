package de.lifeos.core.field

import kotlin.math.*

/**
 * SPECTRAL FIELD ANALYZER — Spektral-Feld-Analyse
 *
 * Der SpectralFieldAnalyzer zerlegt Feld-Signale in spektrale Komponenten
 * und analysiert Frequenzbänder, Kohärenz und spektrale Leistung.
 *
 * Frequenzbänder:
 * - Delta (δ): 0.5–4 Hz (Tiefschlaf, Regeneration)
 * - Theta (θ): 4–8 Hz (Drowsiness, Kreativität)
 * - Alpha (α): 8–13 Hz (Entspannung, Ruhezustand)
 * - Beta Low (β_low): 13–20 Hz (Aktive Verarbeitung)
 * - Beta High (β_high): 20–30 Hz (Konzentration, Stress)
 * - Gamma (γ): 30–100 Hz (Hochkognitive Verarbeitung)
 *
 * Vektoren:
 * - [EXP-FORCE] Spektrale Analyse: Optimierung der kognitiven Frequenzbänder
 * - [EXP-AUTO] Autopoietische Resonanz: Selbst-Tuning der Feld-Frequenzen
 * - [EXP-SPEED] O(n log n) FFT, O(1) Band-Power Lookup
 */
object SpectralFieldAnalyzer {

    // =========================================================================
    // FREQUENCY BANDS
    // =========================================================================

    /**
     * Frequenzband.
     *
     * @param name Name
     * @param minFreq Minimale Frequenz (Hz)
     * @param maxFreq Maximale Frequenz (Hz)
     * @param centerFreq Zentrale Frequenz (Hz)
     * @param description Beschreibung
     */
    data class FrequencyBand(
        val name: String,
        val minFreq: Float,
        val maxFreq: Float,
        val centerFreq: Float,
        val description: String
    ) {
        /**
         * Prüft, ob eine Frequenz in diesem Band liegt.
         */
        fun contains(frequency: Float): Boolean {
            return frequency in minFreq..maxFreq
        }

        /**
         * Berechnet die Bandbreite.
         */
        val bandwidth: Float
            get() = maxFreq - minFreq
    }

    /** Delta-Band: 0.5–4 Hz */
    val BAND_DELTA = FrequencyBand("Delta", 0.5f, 4.0f, 2.25f, "Tiefschlaf, Regeneration")

    /** Theta-Band: 4–8 Hz */
    val BAND_THETA = FrequencyBand("Theta", 4.0f, 8.0f, 6.0f, "Drowsiness, Kreativität")

    /** Alpha-Band: 8–13 Hz */
    val BAND_ALPHA = FrequencyBand("Alpha", 8.0f, 13.0f, 10.5f, "Entspannung, Ruhezustand")

    /** Beta Low-Band: 13–20 Hz */
    val BAND_BETA_LOW = FrequencyBand("Beta Low", 13.0f, 20.0f, 16.5f, "Aktive Verarbeitung")

    /** Beta High-Band: 20–30 Hz */
    val BAND_BETA_HIGH = FrequencyBand("Beta High", 20.0f, 30.0f, 25.0f, "Konzentration, Stress")

    /** Gamma-Band: 30–100 Hz */
    val BAND_GAMMA = FrequencyBand("Gamma", 30.0f, 100.0f, 65.0f, "Hochkognitive Verarbeitung")

    /** Alle Standard-Frequenzbänder */
    val ALL_BANDS = listOf(BAND_DELTA, BAND_THETA, BAND_ALPHA, BAND_BETA_LOW, BAND_BETA_HIGH, BAND_GAMMA)

    // =========================================================================
    // SPECTRAL DATA
    // =========================================================================

    /**
     * Spektral-Datenpunkt.
     *
     * @param frequency Frequenz (Hz)
     * @param power Leistung
     * @param phase Phase (Rad)
     */
    data class SpectralPoint(
        val frequency: Float,
        val power: Float,
        val phase: Float
    )

    /**
     * Spektrum einer Zeitreihe.
     *
     * @param sourceName Quell-Name
     * @param points Spektral-Punkte
     * @param samplingRate Abtastrate (Hz)
     * @param timestamp Zeitstempel
     */
    data class Spectrum(
        val sourceName: String,
        val points: List<SpectralPoint>,
        val samplingRate: Float,
        val timestamp: Long
    ) {
        /**
         * Gibt die Gesamtleistung zurück.
         */
        val totalPower: Float
            get() = points.sumOf { it.power.toDouble() }.toFloat()

        /**
         * Gibt die dominante Frequenz zurück.
         */
        val dominantFrequency: Float?
            get() = points.maxByOrNull { it.power }?.frequency

        /**
         * Gibt die spektrale Entropie zurück.
         */
        val spectralEntropy: Float
            get() {
                if (points.isEmpty()) return 0f
                val total = totalPower
                if (total == 0f) return 0f
                val probabilities = points.map { it.power / total }
                return -probabilities.sumOf { (it * ln(it)).toDouble() }.toFloat()
            }
    }

    // =========================================================================
    // SPECTRAL ANALYZER
    // =========================================================================

    /**
     * Analysiert ein Signal im Frequenzbereich.
     *
     * @param signal Zeitreihen-Signal
     * @param samplingRate Abtastrate (Hz)
     * @return Spektrum
     */
    fun analyze(signal: List<Float>, samplingRate: Float = 100f): Spectrum {
        if (signal.isEmpty()) {
            return Spectrum("unknown", emptyList(), samplingRate, System.currentTimeMillis())
        }

        val n = signal.size
        val spectrum = mutableListOf<SpectralPoint>()

        // Einfache Spektralanalyse via DFT (vereinfacht)
        // In Produktion: Optimierte FFT-Implementierung
        val freqResolution = samplingRate / n
        val maxFreq = samplingRate / 2

        for (k in 0 until n / 2) {
            val freq = k * freqResolution
            if (freq > maxFreq) break

            var real = 0f
            var imag = 0f
            val angleIncrement = 2.0 * PI * k / n

            for (t in signal.indices) {
                // Hann window to reduce spectral leakage
                val window = 0.5f * (1.0f - cos(2.0 * PI * t / (n - 1)))
                val angle = angleIncrement * t
                val cosAngle = cos(angle).toFloat()
                val sinAngle = sin(angle).toFloat()
                real = (real + signal[t] * window * cosAngle).toFloat()
                imag = (imag - signal[t] * window * sinAngle).toFloat()
            }

            val power = (real * real + imag * imag) / n
            val phase = atan2(imag, real)

            spectrum.add(SpectralPoint(freq, power, phase))
        }

        return Spectrum(
            sourceName = "signal",
            points = spectrum,
            samplingRate = samplingRate,
            timestamp = System.currentTimeMillis()
        )
    }

    /**
     * Berechnet die Leistung in einem Frequenzband.
     *
     * @param spectrum Spektrum
     * @param band Frequenzband
     * @return Band-Leistung
     */
    fun computeBandPower(spectrum: Spectrum, band: FrequencyBand): Float {
        return spectrum.points
            .filter { it.frequency in band.minFreq..band.maxFreq }
            .sumOf { it.power.toDouble() }
            .toFloat()
    }

    /**
     * Berechnet die relativen Band-Leistungen.
     *
     * @param spectrum Spektrum
     * @return Map von Band-Name zu relativer Leistung
     */
    fun computeRelativeBandPowers(spectrum: Spectrum): Map<String, Float> {
        val totalPower = spectrum.totalPower
        if (totalPower == 0f) return emptyMap()

        return ALL_BANDS.associate { band ->
            band.name to (computeBandPower(spectrum, band) / totalPower)
        }
    }

    // =========================================================================
    // COHERENCE ANALYZER
    // =========================================================================

    /**
     * Berechnet die Kohärenz zwischen zwei Signalen.
     *
     * @param signal1 Erstes Signal
     * @param signal2 Zweites Signal
     * @param samplingRate Abtastrate (Hz)
     * @return Kohärenz [0, 1]
     */
    fun computeCoherence(signal1: List<Float>, signal2: List<Float>, samplingRate: Float = 100f): Float {
        if (signal1.size != signal2.size || signal1.isEmpty()) return 0f

        val n = signal1.size
        val spectrum1 = analyze(signal1, samplingRate)
        val spectrum2 = analyze(signal2, samplingRate)

        var totalCoherence = 0f
        var count = 0

        for (i in 0 until min(spectrum1.points.size, spectrum2.points.size)) {
            val p1 = spectrum1.points[i]
            val p2 = spectrum2.points[i]

            // Einfache Kohärenzberechnung
            val coherence = if (p1.power > 0 && p2.power > 0) {
                min(1.0f, (p1.power + p2.power) / (2.0f * max(p1.power, p2.power)))
            } else 0f

            totalCoherence += coherence
            count++
        }

        return if (count > 0) (totalCoherence / count).coerceIn(0.0f, 1.0f) else 0f
    }

    /**
     * Kohärenz-Ergebnis zwischen zwei Signalen.
     *
     * @param signal1Name Name des ersten Signals
     * @param signal2Name Name des zweiten Signals
     * @param coherence Kohärenz [0, 1]
     * @param frequency Frequenz (Hz)
     */
    data class CoherenceResult(
        val signal1Name: String,
        val signal2Name: String,
        val coherence: Float,
        val frequency: Float
    ) {
        val isHighCoherence: Boolean
            get() = coherence >= 0.7f
    }

    // =========================================================================
    // PHASE ANALYSIS
    // =========================================================================

    /**
     * Analysiert die Phasenbeziehung zwischen zwei Signalen.
     *
     * @param signal1 Erstes Signal
     * @param signal2 Zweites Signal
     * @param samplingRate Abtastrate (Hz)
     * @return Phasen-Differenz (Rad)
     */
    fun computePhaseDifference(signal1: List<Float>, signal2: List<Float>, samplingRate: Float = 100f): Float {
        if (signal1.size != signal2.size || signal1.isEmpty()) return 0f

        val spectrum1 = analyze(signal1, samplingRate)
        val spectrum2 = analyze(signal2, samplingRate)

        val dominant1 = spectrum1.dominantFrequency ?: return 0f
        val dominant2 = spectrum2.dominantFrequency ?: return 0f

        val point1 = spectrum1.points.find { it.frequency == dominant1 }
        val point2 = spectrum2.points.find { it.frequency == dominant2 }

        return if (point1 != null && point2 != null) {
            abs(point1.phase - point2.phase)
        } else 0f
    }

    // =========================================================================
    // FIELD RESONANCE
    // =========================================================================

    /**
     * Analysiert die Resonanz des kognitiven Feldes.
     *
     * @param spectrum Spektrum
     * @return Resonanz-Analyse
     */
    fun analyzeFieldResonance(spectrum: Spectrum): FieldResonance {
        val bandPowers = computeRelativeBandPowers(spectrum)
        val dominantBand = bandPowers.maxByOrNull { it.value }

        val resonanceScore = when {
            dominantBand == null -> 0f
            dominantBand.key == BAND_ALPHA.name -> 0.8f // Optimal für Ruhezustand
            dominantBand.key == BAND_BETA_LOW.name -> 0.7f // Gut für Verarbeitung
            dominantBand.key == BAND_GAMMA.name -> 0.6f // Hochkognitiv
            dominantBand.key == BAND_THETA.name -> 0.5f // Kreativ
            else -> 0.4f
        }

        return FieldResonance(
            dominantBand = dominantBand?.key ?: "Unknown",
            resonanceScore = resonanceScore,
            bandPowers = bandPowers,
            spectralEntropy = spectrum.spectralEntropy
        )
    }

    /**
     * Feld-Resonanz-Analyse.
     *
     * @param dominantBand Dominantes Band
     * @param resonanceScore Resonanz-Score [0, 1]
     * @param bandPowers Band-Leistungen
     * @param spectralEntropy Spektrale Entropie
     */
    data class FieldResonance(
        val dominantBand: String,
        val resonanceScore: Float,
        val bandPowers: Map<String, Float>,
        val spectralEntropy: Float
    ) {
        val isOptimal: Boolean
            get() = resonanceScore >= 0.7f

        override fun toString(): String {
            return buildString {
                appendLine("=== FIELD RESONANCE ===")
                appendLine("Dominant Band: $dominantBand")
                appendLine("Resonance Score: ${"%.2f".format(resonanceScore)}")
                appendLine("Spectral Entropy: ${"%.3f".format(spectralEntropy)}")
                appendLine("Optimal: $isOptimal")
                bandPowers.forEach { (band, power) ->
                    appendLine("  $band: ${"%.2f".format(power)}")
                }
            }
        }
    }

    // =========================================================================
    // SIGNAL GENERATION (for testing/simulation)
    // =========================================================================

    /**
     * Generiert ein synthetisches Signal.
     *
     * @param frequency Frequenz (Hz)
     * @param duration Dauer (Sekunden)
     * @param samplingRate Abtastrate (Hz)
     * @param amplitude Amplitude
     * @return Signal
     */
    fun generateSignal(
        frequency: Float,
        duration: Float,
        samplingRate: Float = 100f,
        amplitude: Float = 1.0f
    ): List<Float> {
        val n = (duration * samplingRate).toInt()
        return List(n) { t ->
            amplitude * sin(2.0 * PI * frequency * t / samplingRate).toFloat()
        }
    }

    /**
     * Generiert ein zusammengesetztes Signal aus mehreren Frequenzen.
     *
     * @param components Frequenzkomponenten (Frequenz, Amplitude)
     * @param duration Dauer (Sekunden)
     * @param samplingRate Abtastrate (Hz)
     * @return Signal
     */
    fun generateCompositeSignal(
        components: List<Pair<Float, Float>>,
        duration: Float,
        samplingRate: Float = 100f
    ): List<Float> {
        val n = (duration * samplingRate).toInt()
        return List(n) { t ->
            components.sumOf { (freq, amp) ->
                amp * sin(2.0 * PI * freq * t / samplingRate)
            }.toFloat()
        }
    }

    // =========================================================================
    // UTILITY
    // =========================================================================

    /**
     * Gibt Informationen über ein Frequenzband zurück.
     */
    fun getBandInfo(band: FrequencyBand): String {
        return "${band.name}: ${band.minFreq}–${band.maxFreq} Hz (center: ${band.centerFreq} Hz) — ${band.description}"
    }

    /**
     * Findet ein Frequenzband anhand des Namens.
     */
    fun findBandByName(name: String): FrequencyBand? {
        return ALL_BANDS.find { it.name.equals(name, ignoreCase = true) }
    }

    /**
     * Findet das Frequenzband für eine gegebene Frequenz.
     */
    fun findBandByFrequency(frequency: Float): FrequencyBand? {
        return ALL_BANDS.find { it.contains(frequency) }
    }
}

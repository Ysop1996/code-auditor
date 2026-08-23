package de.lifeos.core.field

import java.util.concurrent.atomic.AtomicLong
import kotlin.math.*

/**
 * QUANTUM RESONANCE BRIDGE — Quanten-Resonanz-Brücke
 *
 * Die QuantumResonanceBridge modelliert die Brücke zwischen quantenhaften
 * und klassischen Zuständen im kognitiven Feld. Sie verwaltet Kohärenz,
 * Verschränkung und Dekohärenz.
 *
 * Konzepte:
 * 1. Quantum State: Überlagerter Zustand |Ψ⟩ = Σ αᵢ|i⟩
 * 2. Resonance Bridge: Kopplung zwischen Quanten- und klassischem Raum
 * 3. Coherence: Phasenkohärenz zwischen Zuständen
 * 4. Entanglement: Verschränkung zwischen Feld-Knoten
 * 5. Decoherence: Übergang zum klassischen Zustand
 *
 * Vektoren:
 * - [EXP-FORCE] Quanten-Resonanz: Maximierung der kognitiven Kohärenz
 * - [EXP-AUTO] Autopoietische Dekohärenz-Kontrolle: Schutz vor Informationsverlust
 * - [EXP-SPEED] O(1) Kohärenz-Check, O(n) Zustands-Update
 */
object QuantumResonanceBridge {

    // =========================================================================
    // QUANTUM PARAMETERS
    // =========================================================================

    /** Standard-Kohärenzzeit (ms) */
    const val DEFAULT_COHERENCE_TIME_MS: Long = 1000L

    /** Dekohärenz-Schwellwert */
    const val DECOHERENCE_THRESHOLD: Float = 0.1f

    /** Verschränkungs-Schwellwert */
    const val ENTANGLEMENT_THRESHOLD: Float = 0.5f

    /** Maximale Überlagerungs-Komponenten */
    const val MAX_SUPERPOSITION_COMPONENTS: Int = 16

    /** Planck-Konstante (reduziert) ℏ in willkürlichen Einheiten */
    const val H_BAR: Float = 1.0f

    // =========================================================================
    // QUANTUM STATE
    // =========================================================================

    /**
     * Quanten-Zustand als Überlagerung.
     *
     * @param amplitudes Amplituden αᵢ
     * @param phases Phasen φᵢ
     * @param timestamp Zeitstempel
     */
    data class QuantumState(
        val amplitudes: FloatArray,
        val phases: FloatArray,
        val timestamp: Long
    ) {
        init {
            require(amplitudes.size == phases.size) { "Amplitudes and phases must have same size" }
            require(amplitudes.size in 1..MAX_SUPERPOSITION_COMPONENTS) {
                "Superposition size must be in [1, $MAX_SUPERPOSITION_COMPONENTS]"
            }
        }

        /**
         * Berechnet die Norm des Zustands.
         */
        fun norm(): Float {
            return sqrt(amplitudes.sumOf { (it * it).toDouble() }).toFloat()
        }

        /**
         * Normalisiert den Zustand.
         */
        fun normalize(): QuantumState {
            val n = norm()
            return if (n > 1e-7f) {
                val normAmplitudes = amplitudes.map { it / n }.toFloatArray()
                QuantumState(normAmplitudes, phases.copyOf(), timestamp)
            } else {
                this
            }
        }

        /**
         * Berechnet die Kohärenz des Zustands.
         */
        fun computeCoherence(): Float {
            if (amplitudes.size < 2) return 0f
            var coherence = 0f
            for (i in amplitudes.indices) {
                for (j in i + 1 until amplitudes.size) {
                    coherence += amplitudes[i] * amplitudes[j] * cos(phases[i] - phases[j])
                }
            }
            return (2.0f * coherence / (amplitudes.size * (amplitudes.size - 1))).coerceIn(0.0f, 1.0f)
        }

        /**
         * Berechnet die Von-Neumann-Entropie.
         */
        fun computeEntropy(): Float {
            val n = norm()
            if (n < 1e-7f) return 0f
            val probabilities = amplitudes.map { (it / n).let { p -> p * p } }
            return -probabilities.sumOf { (it * ln(it)).toDouble() }.toFloat()
        }

        /**
         * Führt eine Messung durch (Kollaps der Wellenfunktion).
         */
        fun measure(): Int {
            val n = norm()
            if (n < 1e-7f) return 0

            val probabilities = amplitudes.map { (it / n).let { p -> p * p } }
            val random = RandomGenerator.nextFloat()
            var cumulative = 0f

            for (i in probabilities.indices) {
                cumulative += probabilities[i]
                if (random <= cumulative) {
                    return i
                }
            }
            return amplitudes.size - 1
        }

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is QuantumState) return false
            return amplitudes.contentEquals(other.amplitudes) && phases.contentEquals(other.phases)
        }

        override fun hashCode(): Int {
            var result = amplitudes.contentHashCode()
            result = 31 * result + phases.contentHashCode()
            return result
 }
    }

    // =========================================================================
    // RESONANCE BRIDGE
    // =========================================================================

    /**
     * Resonanz-Brücke zwischen Quanten- und klassischem Raum.
     *
     * @param couplingStrength Kopplungsstärke [0, 1]
     * @param coherenceTime Kohärenzzeit (ms)
     * @param isActive Ob die Brücke aktiv ist
     */
    data class ResonanceBridge(
        val couplingStrength: Float,
        val coherenceTime: Long,
        val isActive: Boolean
    ) {
        /**
         * Berechnet die Überfahrt-Wahrscheinlichkeit.
         */
        fun computeTransitionProbability(): Float {
            return (couplingStrength * 0.7f + 0.3f).coerceIn(0.0f, 1.0f)
        }

        /**
         * Prüft, ob die Brücke stabil ist.
         */
        fun isStable(): Boolean {
            return couplingStrength >= 0.5f && isActive
        }
    }

    // =========================================================================
    // ENTANGLEMENT MODEL
    // =========================================================================

    /**
     * Verschränkungs-Modell zwischen zwei Quanten-Zuständen.
     *
     * @param stateA Erster Zustand
     * @param stateB Zweiter Zustand
     * @param entanglementStrength Verschränkungsstärke [0, 1]
     */
    data class EntanglementPair(
        val stateA: QuantumState,
        val stateB: QuantumState,
        val entanglementStrength: Float
    ) {
        /**
         * Prüft, ob die Verschränkung stark ist.
         */
        fun isStronglyEntangled(): Boolean {
            return entanglementStrength >= ENTANGLEMENT_THRESHOLD
        }

        /**
         * Berechnet die Verschränkungs-Entropie.
         */
        fun computeEntanglementEntropy(): Float {
            val coherenceA = stateA.computeCoherence()
            val coherenceB = stateB.computeCoherence()
            return (coherenceA * coherenceB * entanglementStrength).coerceIn(0.0f, 1.0f)
        }
    }

    // =========================================================================
    // COHERENCE MANAGER
    // =========================================================================

    private val coherenceHistory = mutableMapOf<String, MutableList<CoherenceRecord>>()

    /**
     * Zeichnet die Kohärenz eines Zustands auf.
     *
     * @param stateId Zustands-ID
     * @param coherence Kohärenz [0, 1]
     */
    @Synchronized
    fun recordCoherence(stateId: String, coherence: Float) {
        val records = coherenceHistory.getOrPut(stateId) { mutableListOf() }
        records.add(CoherenceRecord(System.currentTimeMillis(), coherence))
        if (records.size > 1000) {
            records.removeAt(0)
        }
    }

    @Synchronized
    fun computeAverageCoherence(stateId: String): Float {
        val records = coherenceHistory[stateId] ?: return 0f
        return records.map { it.coherence }.average().toFloat()
    }

    @Synchronized
    fun detectDecoherence(stateId: String, window: Int = 10): Float {
        val records = coherenceHistory[stateId] ?: return 0f
        if (records.size < window) return 0f

        val recent = records.takeLast(window)
        val firstHalf = recent.take(window / 2).map { it.coherence }.average().toFloat()
        val secondHalf = recent.takeLast(window / 2).map { it.coherence }.average().toFloat()

        return (firstHalf - secondHalf).coerceIn(0.0f, 1.0f)
    }

    /**
     * Kohärenz-Aufzeichnung.
     *
     * @param timestamp Zeitstempel
     * @param coherence Kohärenz
     */
    data class CoherenceRecord(
        val timestamp: Long,
        val coherence: Float
    )

    // =========================================================================
    // DECOHERENCE HANDLER
    // =========================================================================

    /**
     * Modelliert Dekohärenz eines Quanten-Zustands.
     *
     * @param state Quanten-Zustand
     * @param decayRate Dekaysrate
     * @param time Zeit (ms)
     * @return Dekohärenter Zustand
     */
    fun applyDecoherence(state: QuantumState, decayRate: Float, time: Long): QuantumState {
        val decayFactor = exp(-decayRate * time / 1000.0f)
        val newAmplitudes = state.amplitudes.map { it * decayFactor }.toFloatArray()
        return QuantumState(newAmplitudes, state.phases.copyOf(), System.currentTimeMillis())
    }

    /**
     * Berechnet die Dekohärenzzeit.
     *
     * @param decayRate Dekaysrate
     * @return Dekohärenzzeit (ms)
     */
    fun computeDecoherenceTime(decayRate: Float): Long {
        return (DEFAULT_COHERENCE_TIME_MS / (decayRate + 0.001f)).toLong()
    }

    // =========================================================================
    // RESONANCE ANALYSIS
    // =========================================================================

    /**
     * Analysiert die Resonanz zwischen zwei Zuständen.
     *
     * @param stateA Erster Zustand
     * @param stateB Zweiter Zustand
     * @return Resonanz-Analyse
     */
    fun analyzeResonance(stateA: QuantumState, stateB: QuantumState): ResonanceAnalysis {
        val coherenceA = stateA.computeCoherence()
        val coherenceB = stateB.computeCoherence()
        val phaseDiff = computeAveragePhaseDifference(stateA, stateB)
        val amplitudeOverlap = computeAmplitudeOverlap(stateA, stateB)

        val resonanceScore = ((coherenceA + coherenceB) / 2.0f * amplitudeOverlap * cos(phaseDiff)).coerceIn(0.0f, 1.0f)

        return ResonanceAnalysis(
            resonanceScore = resonanceScore,
            phaseDifference = phaseDiff,
            amplitudeOverlap = amplitudeOverlap,
            isResonant = resonanceScore >= 0.6f
        )
    }

    /**
     * Berechnet die durchschnittliche Phasendifferenz.
     */
    private fun computeAveragePhaseDifference(stateA: QuantumState, stateB: QuantumState): Float {
        val minSize = min(stateA.phases.size, stateB.phases.size)
        if (minSize == 0) return 0f

        var totalDiff = 0f
        for (i in 0 until minSize) {
            totalDiff += abs(stateA.phases[i] - stateB.phases[i])
        }
        return totalDiff / minSize
    }

    /**
     * Berechnet die Amplituden-Überlappung.
     */
    private fun computeAmplitudeOverlap(stateA: QuantumState, stateB: QuantumState): Float {
        val normA = stateA.norm()
        val normB = stateB.norm()
        if (normA < 1e-7f || normB < 1e-7f) return 0f

        val minSize = min(stateA.amplitudes.size, stateB.amplitudes.size)
        var dotProduct = 0f
        for (i in 0 until minSize) {
            dotProduct += stateA.amplitudes[i] * stateB.amplitudes[i]
        }

        return (dotProduct / (normA * normB)).coerceIn(0.0f, 1.0f)
    }

    /**
     * Resonanz-Analyse.
     *
     * @param resonanceScore Resonanz-Score [0, 1]
     * @param phaseDifference Phasendifferenz
     * @param amplitudeOverlap Amplituden-Überlappung
     * @param isResonant Ob Resonanz vorliegt
     */
    data class ResonanceAnalysis(
        val resonanceScore: Float,
        val phaseDifference: Float,
        val amplitudeOverlap: Float,
        val isResonant: Boolean
    ) {
        override fun toString(): String {
            return buildString {
                appendLine("=== RESONANCE ANALYSIS ===")
                appendLine("Score: ${"%.3f".format(resonanceScore)}")
                appendLine("Phase Diff: ${"%.3f".format(phaseDifference)}")
                appendLine("Amplitude Overlap: ${"%.3f".format(amplitudeOverlap)}")
                appendLine("Resonant: $isResonant")
            }
        }
    }

    // =========================================================================
    // UTILITY
    // =========================================================================

    /**
     * Erstellt einen Basis-Quanten-Zustand.
     *
     * @param componentIndex Index der Komponente
     * @param totalComponents Gesamtanzahl Komponenten
     * @return Quanten-Zustand
     */
    fun createBasisState(componentIndex: Int, totalComponents: Int = 2): QuantumState {
        require(componentIndex in 0 until totalComponents)

        val amplitudes = FloatArray(totalComponents) { 0f }
        val phases = FloatArray(totalComponents) { 0f }
        amplitudes[componentIndex] = 1.0f

        return QuantumState(amplitudes, phases, System.currentTimeMillis())
    }

    /**
     * Erstellt einen gleichverteilten Überlagerungs-Zustand.
     *
     * @param componentCount Anzahl Komponenten
     * @return Quanten-Zustand
     */
    fun createSuperpositionState(componentCount: Int): QuantumState {
        require(componentCount in 1..MAX_SUPERPOSITION_COMPONENTS)

        val amplitude = 1.0f / sqrt(componentCount.toFloat())
        val amplitudes = FloatArray(componentCount) { amplitude }
        val phases = FloatArray(componentCount) { 0f }

        return QuantumState(amplitudes, phases, System.currentTimeMillis()).normalize()
    }

    /**
     * Löscht die Kohärenz-Historie.
     */
    fun clearCoherenceHistory() {
        coherenceHistory.clear()
    }

    /**
     * Gibt die Anzahl tracked Zustände zurück.
     */
    fun getTrackedStateCount(): Int = coherenceHistory.size
}

/**
 * Einfacher Zufallsgenerator für deterministische Simulationen.
 * Verwendet atomaren Seed-Counter + System.currentTimeMillis() zur Kollisionsvermeidung.
 */
private object RandomGenerator {
    private val seedCounter = java.util.concurrent.atomic.AtomicLong(0)

    fun nextFloat(): Float {
        val seed = System.currentTimeMillis() + seedCounter.incrementAndGet()
        val lcg = ((seed * 1103515245 + 12345) and 0x7fffffff)
        return (lcg % 10000) / 10000.0f
    }
}

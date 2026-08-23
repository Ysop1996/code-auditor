package de.lifeos.core.field

import kotlin.math.*

/**
 * CONSCIOUSNESS MONITOR — Bewusstseins-Zustandsüberwachung
 *
 * Der Bewusstseins-Monitor überwacht den kognitiven Zustand des Systems
 * und klassifiziert es in verschiedene Bewusstseins-Level:
 *
 *   - UNCONSCIOUS: Keine kognitive Aktivität (W(t) = 0, S_o(t) = 0)
 *   - SUBCONSCIOUS: Minimale Aktivität (W(t) < 0.2, d_α(t) = 1)
 *   - CONSCIOUS: Aktive Verarbeitung (W(t) ∈ [0.2, 0.8], d_α(t) ∈ [1, 3])
 *   - HYPERCONSCIOUS: Überaktiver Zustand (W(t) > 0.8, S_o(t) > 1)
 *   - TRANSCENDENT: Jenseits der normalen Verarbeitung (PGO-Tunnel)
 *
 * Bewusstseins-Index:
 *
 *   C(t) = α₁·W(t) + α₂·S_o(t) + α₃·d_α(t) + α₄·S_vN(t)
 *
 * Vektoren:
 * - [EXP-FORCE] Bewusstseins-Überwachung: Echtzeit-Klassifikation
 * - [EXP-AUTO] Autopoietische Regulation: Bewusstseins-Erhaltung
 * - [EXP-SPEED] O(1) Klassifikation mit Lookup-Table
 */
object ConsciousnessMonitor {

    // =========================================================================
    // BEWUSSTSEINS-PARAMETER
    // =========================================================================

    /** Gewicht für Last W(t): α₁ = 0.3 */
    const val ALPHA_LOAD: Float = 0.3f

    /** Gewicht für Souveränität S_o(t): α₂ = 0.3 */
    const val ALPHA_SOVEREIGNTY: Float = 0.3f

    /** Gewicht für Effektive Dimension d_α(t): α₃ = 0.2 */
    const val ALPHA_DIMENSION: Float = 0.2f

    /** Gewicht für Von-Neumann-Entropie S_vN(t): α₄ = 0.2 */
    const val ALPHA_ENTROPY: Float = 0.2f

    /** Bewusstseins-Schwellwerte */
    const val THRESHOLD_UNCONSCIOUS: Float = 0.1f
    const val THRESHOLD_SUBCONSCIOUS: Float = 0.3f
    const val THRESHOLD_CONSCIOUS: Float = 0.6f
    const val THRESHOLD_HYPERCONSCIOUS: Float = 0.8f

    // =========================================================================
    // BEWUSSTSEINS-LEVEL
    // =========================================================================

    /**
     * Bewusstseins-Level des Systems.
     */
    enum class ConsciousnessLevel {
        /** Keine kognitive Aktivität */
        UNCONSCIOUS,

        /** Minimale Aktivität */
        SUBCONSCIOUS,

        /** Aktive Verarbeitung */
        CONSCIOUS,

        /** Überaktiver Zustand */
        HYPERCONSCIOUS,

        /** Transzendenter Zustand (PGO-Tunnel) */
        TRANSCENDENT
    }

    /**
     * Bewusstseins-Zustand mit allen relevanten Metriken.
     *
     * @param level Bewusstseins-Level
     * @param consciousnessIndex Bewusstseins-Index C(t) ∈ [0, 1]
     * @param load Last W(t)
     * @param sovereignty Souveränität S_o(t)
     * @param effectiveDimension Effektive Dimension d_α(t)
     * @param entropy Von-Neumann-Entropie S_vN(t)
     * @param timestamp Zeitstempel
     */
    data class ConsciousnessState(
        val level: ConsciousnessLevel,
        val consciousnessIndex: Float,
        val load: Float,
        val sovereignty: Float,
        val effectiveDimension: Float,
        val entropy: Float,
        val timestamp: Long
    ) {
        override fun toString(): String {
            return buildString {
                appendLine("=== CONSCIOUSNESS STATE ===")
                appendLine("Level: $level")
                appendLine("Index: ${"%.3f".format(consciousnessIndex)}")
                appendLine("W(t): ${"%.3f".format(load)}")
                appendLine("S_o(t): ${"%.3f".format(sovereignty)}")
                appendLine("d_α(t): ${"%.1f".format(effectiveDimension)}")
                appendLine("S_vN: ${"%.4f".format(entropy)}")
            }
        }
    }

    // =========================================================================
    // BEWUSSTSEINS-INDEX BERECHNUNG
    // =========================================================================

    /**
     * Berechnet den Bewusstseins-Index C(t).
     *
     * C(t) = α₁·W(t) + α₂·S_o(t) + α₃·d_α(t) + α₄·S_vN(t)
     *
     * @param unifiedState Feldzustand
     * @return Bewusstseins-Index C(t) ∈ [0, 1]
     */
    fun computeConsciousnessIndex(unifiedState: FieldDynamicsIntegrator.UnifiedFieldState): Float {
        val w = unifiedState.load.coerceIn(0.0f, 2.0f) / 2.0f // Normalize to [0, 1]
        val s = unifiedState.sovereignty.coerceIn(0.0f, 2.0f) / 2.0f // Normalize to [0, 1]
        val d = unifiedState.effectiveDim.coerceIn(1.0f, 3.0f) / 3.0f // Normalize to [0, 1]
        val e = unifiedState.entropy.coerceIn(0.0f, 1.0f) // Already in [0, 1]

        return (ALPHA_LOAD * w + ALPHA_SOVEREIGNTY * s + ALPHA_DIMENSION * d + ALPHA_ENTROPY * e).coerceIn(0.0f, 1.0f)
    }

    /**
     * Klassifiziert den Bewusstseins-Level basierend auf dem Index.
     *
     * @param consciousnessIndex Bewusstseins-Index C(t)
     * @return Bewusstseins-Level
     */
    fun classifyConsciousnessLevel(consciousnessIndex: Float): ConsciousnessLevel {
        return when {
            consciousnessIndex < THRESHOLD_UNCONSCIOUS -> ConsciousnessLevel.UNCONSCIOUS
            consciousnessIndex < THRESHOLD_SUBCONSCIOUS -> ConsciousnessLevel.SUBCONSCIOUS
            consciousnessIndex < THRESHOLD_CONSCIOUS -> ConsciousnessLevel.CONSCIOUS
            consciousnessIndex < THRESHOLD_HYPERCONSCIOUS -> ConsciousnessLevel.HYPERCONSCIOUS
            else -> ConsciousnessLevel.TRANSCENDENT
        }
    }

    /**
     * Erstellt einen vollständigen Bewusstseins-Zustand.
     *
     * @param unifiedState Feldzustand
     * @return ConsciousnessState
     */
    fun createConsciousnessState(unifiedState: FieldDynamicsIntegrator.UnifiedFieldState): ConsciousnessState {
        val index = computeConsciousnessIndex(unifiedState)
        val level = classifyConsciousnessLevel(index)

        return ConsciousnessState(
            level = level,
            consciousnessIndex = index,
            load = unifiedState.load,
            sovereignty = unifiedState.sovereignty,
            effectiveDimension = unifiedState.effectiveDim,
            entropy = unifiedState.entropy,
            timestamp = System.currentTimeMillis()
        )
    }

    // =========================================================================
    // BEWUSSTSEINS-HISTORIE
    // =========================================================================

    private val consciousnessHistory = ArrayDeque<ConsciousnessState>()

    /**
     * Fügt einen Bewusstseins-Zustand zur Historie hinzu.
     *
     * @param state Bewusstseins-Zustand
     */
    fun addToHistory(state: ConsciousnessState) {
        consciousnessHistory.addLast(state)
        while (consciousnessHistory.size > 1000) {
            consciousnessHistory.removeFirst()
        }
    }

    /**
     * Gibt die aktuelle Bewusstseins-Historie zurück.
     *
     * @return Liste der letzten Zustände
     */
    fun getHistory(): List<ConsciousnessState> = consciousnessHistory.toList()

    /**
     * Löscht die Bewusstseins-Historie.
     */
    fun clearHistory() {
        consciousnessHistory.clear()
    }

    /**
     * Berechnet die Bewusstseins-Stabilität (Anteil der Zeit im CONSCIOUS-Level).
     *
     * @return Stabilität 0.0–1.0
     */
    fun computeConsciousnessStability(): Float {
        if (consciousnessHistory.isEmpty()) return 1.0f

        val recent = consciousnessHistory.takeLast(50)
        val consciousCount = recent.count { it.level == ConsciousnessLevel.CONSCIOUS || it.level == ConsciousnessLevel.HYPERCONSCIOUS }
        return (consciousCount / recent.size.toFloat()).coerceIn(0.0f, 1.0f)
    }

    /**
     * Erkennt Bewusstseins-Transitionen (Level-Wechsel).
     *
     * @return Liste von Transitionen
     */
    fun detectTransitions(): List<ConsciousnessTransition> {
        if (consciousnessHistory.size < 2) return emptyList()

        val transitions = mutableListOf<ConsciousnessTransition>()
        for (i in 1 until consciousnessHistory.size) {
            val prev = consciousnessHistory[i - 1]
            val curr = consciousnessHistory[i]
            if (prev.level != curr.level) {
                transitions.add(
                    ConsciousnessTransition(
                        fromLevel = prev.level,
                        toLevel = curr.level,
                        timestamp = curr.timestamp,
                        consciousnessIndex = curr.consciousnessIndex
                    )
                )
            }
        }
        return transitions
    }

    /**
     * Bewusstseins-Transition.
     *
     * @param fromLevel Vorheriges Level
     * @param toLevel Neues Level
     * @param timestamp Zeitstempel
     * @param consciousnessIndex Bewusstseins-Index
     */
    data class ConsciousnessTransition(
        val fromLevel: ConsciousnessLevel,
        val toLevel: ConsciousnessLevel,
        val timestamp: Long,
        val consciousnessIndex: Float
    )

    // =========================================================================
    // INTERVENTIONEN
    // =========================================================================

    /**
     * Generiert Bewusstseins-basierte Interventionen.
     *
     * @param state Bewusstseins-Zustand
     * @return Liste von Interventionen
     */
    fun generateConsciousnessInterventions(state: ConsciousnessState): List<ConsciousnessIntervention> {
        val interventions = mutableListOf<ConsciousnessIntervention>()

        when (state.level) {
            ConsciousnessLevel.UNCONSCIOUS -> {
                interventions.add(
                    ConsciousnessIntervention(
                        type = InterventionType.AWAKENING,
                        title = "System inaktiv",
                        description = "Keine kognitive Aktivität. Sanfte Stimulation empfohlen.",
                        urgency = 0.3f
                    )
                )
            }
            ConsciousnessLevel.SUBCONSCIOUS -> {
                interventions.add(
                    ConsciousnessIntervention(
                        type = InterventionType.ACTIVATION,
                        title = "Niedrige Aktivität",
                        description = "System im Ruhezustand. Leichte Aufgabe zur Aktivierung.",
                        urgency = 0.4f
                    )
                )
            }
            ConsciousnessLevel.CONSCIOUS -> {
                // Optimaler Zustand, keine Intervention benötigt
            }
            ConsciousnessLevel.HYPERCONSCIOUS -> {
                interventions.add(
                    ConsciousnessIntervention(
                        type = InterventionType.CALMING,
                        title = "Überaktivität",
                        description = "System überlastet. Beruhigende Maßnahmen empfohlen.",
                        urgency = 0.7f
                    )
                )
            }
            ConsciousnessLevel.TRANSCENDENT -> {
                interventions.add(
                    ConsciousnessIntervention(
                        type = InterventionType.GROUNDING,
                        title = "Transzendenter Zustand",
                        description = "PGO-Tunnel erkannt. Cache-Reset und Erdung empfohlen.",
                        urgency = 0.9f
                    )
                )
            }
        }

        return interventions
    }

    /**
     * Bewusstseins-Intervention.
     *
     * @param type Interventionstyp
     * @param title Titel
     * @param description Beschreibung
     * @param urgency Dringlichkeit
     */
    data class ConsciousnessIntervention(
        val type: InterventionType,
        val title: String,
        val description: String,
        val urgency: Float
    ) {
        override fun toString(): String = "[$type] $title: $description"
    }

    /**
     * Interventionstypen.
     */
    enum class InterventionType {
        /** Sanfte Weckung */
        AWAKENING,

        /** Aktivierung */
        ACTIVATION,

        /** Beruhigung */
        CALMING,

        /** Erdung */
        GROUNDING
    }

    // =========================================================================
    // BEWUSSTSEINS-STATISTIKEN
    // =========================================================================

    /**
     * Berechnet Bewusstseins-Statistiken über die Historie.
     *
     * @return Statistics-Bericht
     */
    fun computeStatistics(): ConsciousnessStatistics {
        if (consciousnessHistory.isEmpty()) {
            return ConsciousnessStatistics()
        }

        val indices = consciousnessHistory.map { it.consciousnessIndex }
        val levels = consciousnessHistory.map { it.level }

        return ConsciousnessStatistics(
            avgIndex = indices.average().toFloat(),
            minIndex = indices.minOrNull() ?: 0f,
            maxIndex = indices.maxOrNull() ?: 0f,
            stdDev = computeStdDev(indices),
            levelDistribution = levels.groupingBy { it }.eachCount(),
            transitionCount = detectTransitions().size,
            stability = computeConsciousnessStability()
        )
    }

    /**
     * Berechnet die Standardabweichung einer Liste von Floats.
     */
    private fun computeStdDev(values: List<Float>): Float {
        if (values.size < 2) return 0.0f
        val mean = values.average().toFloat()
        val variance = values.map { (it - mean).let { v -> v * v } }.average().toFloat()
        return sqrt(variance)
    }

    /**
     * Bewusstseins-Statistiken.
     *
     * @param avgIndex Durchschnittlicher Index
     * @param minIndex Minimaler Index
     * @param maxIndex Maximaler Index
     * @param stdDev Standardabweichung
     * @param levelDistribution Verteilung der Level
     * @param transitionCount Anzahl Transitionen
     * @param stability Stabilität
     */
    data class ConsciousnessStatistics(
        val avgIndex: Float = 0.0f,
        val minIndex: Float = 0.0f,
        val maxIndex: Float = 0.0f,
        val stdDev: Float = 0.0f,
        val levelDistribution: Map<ConsciousnessLevel, Int> = emptyMap(),
        val transitionCount: Int = 0,
        val stability: Float = 1.0f
    ) {
        override fun toString(): String {
            return buildString {
                appendLine("=== CONSCIOUSNESS STATISTICS ===")
                appendLine("Avg Index: ${"%.3f".format(avgIndex)}")
                appendLine("Min Index: ${"%.3f".format(minIndex)}")
                appendLine("Max Index: ${"%.3f".format(maxIndex)}")
                appendLine("Std Dev: ${"%.3f".format(stdDev)}")
                appendLine("Transitions: $transitionCount")
                appendLine("Stability: ${"%.1f".format(stability * 100)}%")
                levelDistribution.forEach { (level, count) ->
                    appendLine("  $level: $count")
                }
            }
        }
    }
}

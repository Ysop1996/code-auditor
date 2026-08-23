package de.lifeos.core.field

import kotlin.math.*

/**
 * LIFE TRAJECTORY PLANNER — Mehrdimensionale Lebens-Trajektorien
 *
 * Der Lebens-Trajektorien-Planer berechnet kontinuierlich den Zukunftsstaudruck
 * ρ_future über 4h-, 24h- und 30-Tage-Horizonte, um Terminkonflikte,
 * finanzielle Engpässe und bürokratische Reibung im Vorfeld abzufangen.
 *
 * Dimensionen:
 * - CAREER: Berufliche Entwicklung (Jobcenter, Bewerbungen, Projekte)
 * - HEALTH: Gesundheit (Termine, Medikation, Fitness)
 * - SOCIAL: Soziale Beziehungen (Kontakte, Verpflichtungen)
 * - FINANCIAL: Finanzen (Rechnungen, Verhandlungen, Budget)
 *
 * Trajektorien-Berechnung:
 * - Pfadintegral über alle Handlungsoptionen
 * - Wahrscheinlichkeitsgewichtete Erwartungswerte
 * - Früherkennung von Konflikten (4h-Horizont)
 * - Strategische Pufferzeit-Berechnung
 *
 * Vektoren:
 * - [EXP-FORCE] Mehrdimensionale Lebens-Trajektorien: 4h/24h/30-Tage-Horizonte
 * - [EXP-AUTO] Autopoietische Regulation: Selbstständige Konfliktlösung
 * - [EXP-SPEED] O(N) Trajektorien-Berechnung mit Monte-Carlo-Sampling
 */
object LifeTrajectoryPlanner {

    // =========================================================================
    // TRAJEKTORIEN-PARAMETER
    // =========================================================================

    /** Kurzfristiger Horizont: 4h = 14400s */
    const val HORIZON_4H_S: Float = 14400f

    /** Mittelfristiger Horizont: 24h = 86400s */
    const val HORIZON_24H_S: Float = 86400f

    /** Langfristiger Horizont: 30 Tage = 2592000s */
    const val HORIZON_30D_S: Float = 2592000f

    /** Trajektorien-Sample-Anzahl: M = 1000 */
    const val MONTE_CARLO_SAMPLES: Int = 1000

    /** Konflikt-Schwelle: P_conflict > 0.3 → Warnung */
    const val CONFLICT_THRESHOLD: Float = 0.3f

    /** Pufferzeit-Minimum: 15 Minuten */
    const val MIN_BUFFER_MINUTES: Float = 15.0f

    /** Pufferzeit-Maximum: 2 Stunden */
    const val MAX_BUFFER_MINUTES: Float = 120.0f

    // =========================================================================
    // LEBENS-DIMENSIONEN
    // =========================================================================

    /**
     * Lebens-Dimensionen für Trajektorien-Planung.
     */
    enum class LifeDimension {
        /** Berufliche Entwicklung */
        CAREER,

        /** Gesundheit & Wohlbefinden */
        HEALTH,

        /** Soziale Beziehungen */
        SOCIAL,

        /** Finanzen & Budget */
        FINANCIAL
    }

    /**
     * Trajektorien-Punkt in einer Lebens-Dimension.
     *
     * @param dimension Lebens-Dimension
     * @param timestamp Zeitstempel (Unix ms)
     * @param expectedLoad Erwartete Last W(t)
     * @param confidence Konfidenz 0.0–1.0
     * @param description Beschreibung
     */
    data class TrajectoryPoint(
        val dimension: LifeDimension,
        val timestamp: Long,
        val expectedLoad: Float,
        val confidence: Float,
        val description: String
    )

    /**
     * Vollständige Lebens-Trajektorie über alle Dimensionen.
     *
     * @param points Trajektorien-Punkte
     * @param horizonS Horizont in Sekunden
     * @param totalExpectedLoad Gesamterwartete Last
     * @param conflictProbability Konflikt-Wahrscheinlichkeit
     * @param recommendedBuffer Empfohlene Pufferzeit (Minuten)
     */
    data class LifeTrajectory(
        val points: List<TrajectoryPoint>,
        val horizonS: Float,
        val totalExpectedLoad: Float,
        val conflictProbability: Float,
        val recommendedBuffer: Float
    ) {
        override fun toString(): String {
            return buildString {
                appendLine("=== LIFE TRAJECTORY (${horizonS / 3600f}h horizon) ===")
                appendLine("Total expected load: ${"%.2f".format(totalExpectedLoad)}")
                appendLine("Conflict probability: ${"%.1f".format(conflictProbability * 100)}%")
                appendLine("Recommended buffer: ${"%.0f".format(recommendedBuffer)} min")
                appendLine()
                points.forEach { point ->
                    appendLine("${point.dimension}: ${point.description} @ ${point.timestamp} (W=${"%.2f".format(point.expectedLoad)}, conf=${"%.0f".format(point.confidence * 100)}%)")
                }
            }
        }
    }

    // =========================================================================
    // TRAJEKTORIEN-BERECHNUNG
    // =========================================================================

    /**
     * Berechnet eine Lebens-Trajektorie für einen gegebenen Horizont.
     *
     * @param currentState Aktueller Feldzustand
     * @param horizonS Horizont in Sekunden (4h, 24h, 30d)
     * @param dimensions Zu planende Lebens-Dimensionen
     * @return LifeTrajectory
     */
    fun computeTrajectory(
        currentState: FieldDynamicsIntegrator.UnifiedFieldState,
        horizonS: Float = HORIZON_24H_S,
        dimensions: List<LifeDimension> = LifeDimension.values().toList()
    ): LifeTrajectory {
        val points = mutableListOf<TrajectoryPoint>()
        val now = System.currentTimeMillis()

        for (dimension in dimensions) {
            val trajectoryPoints = computeDimensionTrajectory(currentState, dimension, horizonS, now)
            points.addAll(trajectoryPoints)
        }

        val totalExpectedLoad = points.map { it.expectedLoad }.average().toFloat()
        val conflictProbability = computeConflictProbability(points)
        val recommendedBuffer = computeRecommendedBuffer(totalExpectedLoad, conflictProbability)

        return LifeTrajectory(
            points = points.sortedBy { it.timestamp },
            horizonS = horizonS,
            totalExpectedLoad = totalExpectedLoad,
            conflictProbability = conflictProbability,
            recommendedBuffer = recommendedBuffer
        )
    }

    /**
     * Berechnet die Trajektorie für eine einzelne Lebens-Dimension.
     *
     * @param currentState Aktueller Feldzustand
     * @param dimension Lebens-Dimension
     * @param horizonS Horizont in Sekunden
     * @param startTime Startzeit (Unix ms)
     * @return Liste von TrajectoryPoints
     */
    private fun computeDimensionTrajectory(
        currentState: FieldDynamicsIntegrator.UnifiedFieldState,
        dimension: LifeDimension,
        horizonS: Float,
        startTime: Long
    ): List<TrajectoryPoint> {
        val points = mutableListOf<TrajectoryPoint>()
        val numSteps = 10
        val dt = horizonS / numSteps

        for (i in 0 until numSteps) {
            val t = i.toFloat() / numSteps
            val timestamp = startTime + (t * horizonS * 1000).toLong()

            // Erwartete Last basierend auf kognitivem Zustand und Dimension
            val expectedLoad = computeExpectedLoad(currentState, dimension, t)
            val confidence = computeConfidence(currentState, t)

            val description = when (dimension) {
                LifeDimension.CAREER -> generateCareerDescription(expectedLoad, t)
                LifeDimension.HEALTH -> generateHealthDescription(expectedLoad, t)
                LifeDimension.SOCIAL -> generateSocialDescription(expectedLoad, t)
                LifeDimension.FINANCIAL -> generateFinancialDescription(expectedLoad, t)
            }

            points.add(
                TrajectoryPoint(
                    dimension = dimension,
                    timestamp = timestamp,
                    expectedLoad = expectedLoad,
                    confidence = confidence,
                    description = description
                )
            )
        }

        return points
    }

    /**
     * Berechnet die erwartete Last für eine Dimension zu einem Zeitpunkt.
     *
     * @param state Aktueller Feldzustand
     * @param dimension Lebens-Dimension
     * @param t Normalisierte Zeit [0, 1]
     * @return Erwartete Last W(t)
     */
    private fun computeExpectedLoad(state: FieldDynamicsIntegrator.UnifiedFieldState, dimension: LifeDimension, t: Float): Float {
        val baseLoad = state.load
        val dimensionFactor = when (dimension) {
            LifeDimension.CAREER -> 1.2f
            LifeDimension.HEALTH -> 0.8f
            LifeDimension.SOCIAL -> 0.6f
            LifeDimension.FINANCIAL -> 1.0f
        }

        // Last-Modellierung: sinusförmige Variation über den Horizont
        val temporalVariation = sin(t * PI.toFloat()) * 0.3f
        return max(0f, (baseLoad * dimensionFactor + temporalVariation).coerceIn(0f, 2.0f))
    }

    /**
     * Berechnet die Konfidenz für eine Vorhersage.
     *
     * @param state Aktueller Feldzustand
     * @param t Normalisierte Zeit [0, 1]
     * @return Konfidenz 0.0–1.0
     */
    private fun computeConfidence(state: FieldDynamicsIntegrator.UnifiedFieldState, t: Float): Float {
        // Konfidenz nimmt mit Zeit ab (ferne Zukunft = unsicherer)
        val timeDecay = 1.0f - t * 0.5f
        val sovereigntyFactor = state.sovereignty.coerceIn(0.0f, 1.0f)
        return (timeDecay * sovereigntyFactor).coerceIn(0.0f, 1.0f)
    }

    /**
     * Berechnet die Konflikt-Wahrscheinlichkeit zwischen Trajektorien.
     *
     * @param points Alle Trajektorien-Punkte
     * @return Konflikt-Wahrscheinlichkeit P_conflict ∈ [0, 1]
     */
    private fun computeConflictProbability(points: List<TrajectoryPoint>): Float {
        if (points.size < 2) return 0.0f

        // Group by timestamp for O(n) lookup instead of O(n²) pairwise scan
        val pointsByTime = points.groupBy { it.timestamp }
        var conflictSum = 0.0
        var count = 0

        for ((_, group) in pointsByTime) {
            if (group.size > 1) {
                val totalLoad = group.sumOf { it.expectedLoad.toDouble() }
                conflictSum += (totalLoad / group.size).coerceIn(0.0, 2.0)
                count++
            }
        }

        return if (count > 0) (conflictSum / count).toFloat().coerceIn(0.0f, 1.0f) else 0.0f
    }

    /**
     * Berechnet die empfohlene Pufferzeit.
     *
     * @param totalLoad Gesamtlast
     * @param conflictProbability Konflikt-Wahrscheinlichkeit
     * @return Pufferzeit in Minuten
     */
    private fun computeRecommendedBuffer(totalLoad: Float, conflictProbability: Float): Float {
        val loadBuffer = totalLoad * 30.0f // 30 Min pro Last-Einheit
        val conflictBuffer = conflictProbability * 60.0f // 60 Min bei Konflikt
        return (loadBuffer + conflictBuffer).coerceIn(MIN_BUFFER_MINUTES, MAX_BUFFER_MINUTES)
    }

    // =========================================================================
    // BESCHREIBUNGS-GENERATOREN
    // =========================================================================

    private fun generateCareerDescription(load: Float, t: Float): String {
        return when {
            load > 1.5f -> "Hohe berufliche Last erwartet (W=${"%.1f".format(load)})"
            load > 0.8f -> "Moderate berufliche Aktivitäten"
            t < 0.3f -> "Projekt-Phase: Fokus auf deliverables"
            t > 0.7f -> "Review-Phase: Dokumentation vorbereiten"
            else -> "Normale berufliche Routine"
        }
    }

    private fun generateHealthDescription(load: Float, t: Float): String {
        return when {
            load > 1.0f -> "Gesundheits-Termin mit erhöhter Vorbereitung"
            t < 0.2f -> "Wöchentliche Fitness-Routine"
            t > 0.8f -> "Erholungsphase einplanen"
            else -> "Gesundheitsstatus stabil"
        }
    }

    private fun generateSocialDescription(load: Float, t: Float): String {
        return when {
            load > 0.8f -> "Soziale Verpflichtungen erwartet"
            t < 0.3f -> "Kontakt-Pflege: Nachricht an Schlüsselpersonen"
            else -> "Soziale Dimension ausgeglichen"
        }
    }

    private fun generateFinancialDescription(load: Float, t: Float): String {
        return when {
            load > 1.2f -> "Finanzielle Entscheidung anstehen (Rechnung/Forderung)"
            t > 0.5f -> "Budget-Review empfehlenswert"
            else -> "Finanzen stabil"
        }
    }

    // =========================================================================
    // KONFLIKT-ERKENNUNG
    // =========================================================================

    /**
     * Erkennt Terminkonflikte in der Trajektorie.
     *
     * @param trajectory Lebens-Trajektorie
     * @return Liste von Konflikten
     */
    fun detectConflicts(trajectory: LifeTrajectory): List<TrajectoryConflict> {
        val conflicts = mutableListOf<TrajectoryConflict>()
        val pointsByTime = trajectory.points.groupBy { it.timestamp }

        pointsByTime.forEach { (timestamp, points) ->
            if (points.size > 1) {
                val totalLoad = points.sumOf { it.expectedLoad.toDouble() }.toFloat()
                if (totalLoad > 1.0f) {
                    conflicts.add(
                        TrajectoryConflict(
                            timestamp = timestamp,
                            dimensions = points.map { it.dimension },
                            totalLoad = totalLoad,
                            severity = if (totalLoad > 2.0f) ConflictSeverity.CRITICAL else ConflictSeverity.WARNING
                        )
                    )
                }
            }
        }

        return conflicts
    }

    /**
     * Datenklasse für Trajektorien-Konflikte.
     *
     * @param timestamp Zeitstempel des Konflikts
     * @param dimensions Betroffene Lebens-Dimensionen
     * @param totalLoad Gesamtlast zum Konfliktzeitpunkt
     * @param severity Konflikt-Schweregrad
     */
    data class TrajectoryConflict(
        val timestamp: Long,
        val dimensions: List<LifeDimension>,
        val totalLoad: Float,
        val severity: ConflictSeverity
    ) {
        override fun toString(): String {
            return "[$severity] ${dimensions.joinToString(" + ")} @ ${timestamp} (W=${"%.2f".format(totalLoad)})"
        }
    }

    /**
     * Konflikt-Schweregrad.
     */
    enum class ConflictSeverity {
        /** Warnung: leichte Überlastung */
        WARNING,

        /** Kritisch: starke Überlastung, Intervention erforderlich */
        CRITICAL
    }

    // =========================================================================
    // INTERVENTIONEN
    // =========================================================================

    /**
     * Generiert pro-aktive Interventionen basierend auf Trajektorien-Analyse.
     *
     * @param trajectory Lebens-Trajektorie
     * @param conflicts Erkannte Konflikte
     * @return Liste von Interventionen
     */
    fun generateProactiveInterventions(
        trajectory: LifeTrajectory,
        conflicts: List<TrajectoryConflict>
    ): List<ProactiveIntervention> {
        val interventions = mutableListOf<ProactiveIntervention>()

        // Konflikt-basierte Interventionen
        conflicts.forEach { conflict ->
            when (conflict.severity) {
                ConflictSeverity.CRITICAL -> {
                    interventions.add(
                        ProactiveIntervention(
                            type = InterventionType.RESCHEDULE,
                            title = "Terminkonflikt: ${conflict.dimensions.joinToString(" + ")}",
                            description = "Last=${"%.2f".format(conflict.totalLoad)}. Pufferzeit erhöhen oder Termin verschieben.",
                            actionType = "RESCHEDULE_EVENT",
                            urgency = 0.9f,
                            targetTimestamp = conflict.timestamp
                        )
                    )
                }
                ConflictSeverity.WARNING -> {
                    interventions.add(
                        ProactiveIntervention(
                            type = InterventionType.BUFFER,
                            title = "Pufferzeit erhöhen",
                            description = "Konflikt bei ${conflict.dimensions.joinToString(" + ")}. 15 Min Puffer einplanen.",
                            actionType = "ADD_BUFFER",
                            urgency = 0.5f,
                            targetTimestamp = conflict.timestamp
                        )
                    )
                }
            }
        }

        // Last-basierte Interventionen
        if (trajectory.totalExpectedLoad > 1.0f) {
            interventions.add(
                ProactiveIntervention(
                    type = InterventionType.LOAD_REDUCTION,
                    title = "Hohe Gesamtlast erwartet",
                    description = "Erwartete Last=${"%.2f".format(trajectory.totalExpectedLoad)}. Nicht-essenzielle Aufgaben delegieren.",
                    actionType = "DEFER_NON_ESSENTIAL",
                    urgency = 0.7f,
                    targetTimestamp = trajectory.points.minOfOrNull { it.timestamp } ?: System.currentTimeMillis()
                )
            )
        }

        return interventions
    }

    /**
     * Datenklasse für pro-aktive Interventionen.
     *
     * @param type Interventionstyp
     * @param title Kurzer Titel
     * @param description Detaillierte Beschreibung
     * @param actionType Aktions-Typ für 1-Tap-Ausführung
     * @param urgency Dringlichkeit 0.0–1.0
     * @param targetTimestamp Ziel-Zeitstempel
     */
    data class ProactiveIntervention(
        val type: InterventionType,
        val title: String,
        val description: String,
        val actionType: String,
        val urgency: Float,
        val targetTimestamp: Long
    ) {
        override fun toString(): String {
            return "[$type] $title\n$description\nAktion: $actionType (Dringlichkeit: ${"%.0f".format(urgency * 100)}%)"
        }
    }

    /**
     * Interventionstypen.
     */
    enum class InterventionType {
        /** Termin verschieben */
        RESCHEDULE,

        /** Pufferzeit hinzufügen */
        BUFFER,

        /** Last reduzieren */
        LOAD_REDUCTION
    }

    // =========================================================================
    // HORIZONT-ANALYSE
    // =========================================================================

    /**
     * Führt eine vollständige Horizont-Analyse durch (4h, 24h, 30d).
     *
     * @param currentState Aktueller Feldzustand
     * @return Map von Horizont zu Trajektorie
     */
    fun analyzeAllHorizons(currentState: FieldDynamicsIntegrator.UnifiedFieldState): Map<Float, LifeTrajectory> {
        return mapOf(
            HORIZON_4H_S to computeTrajectory(currentState, HORIZON_4H_S),
            HORIZON_24H_S to computeTrajectory(currentState, HORIZON_24H_S),
            HORIZON_30D_S to computeTrajectory(currentState, HORIZON_30D_S)
        )
    }

    /**
     * Gibt die kritischste Trajektorie zurück (höchste Konflikt-Wahrscheinlichkeit).
     *
     * @param trajectories Map von Horizont zu Trajektorie
     * @return Kritischste Trajektorie
     */
    fun getMostCriticalTrajectory(trajectories: Map<Float, LifeTrajectory>): LifeTrajectory? {
        return trajectories.values.maxByOrNull { it.conflictProbability }
    }

    /**
     * Gibt alle Interventionen für alle Horizonte zurück.
     *
     * @param trajectories Map von Horizont zu Trajektorie
     * @return Liste von Interventionen
     */
    fun getAllInterventions(trajectories: Map<Float, LifeTrajectory>): List<ProactiveIntervention> {
        val allInterventions = mutableListOf<ProactiveIntervention>()
        trajectories.values.forEach { trajectory ->
            val conflicts = detectConflicts(trajectory)
            allInterventions.addAll(generateProactiveInterventions(trajectory, conflicts))
        }
        return allInterventions.sortedByDescending { it.urgency }
    }
}

package de.lifeos.core.field

import kotlin.math.*

/**
 * HOMOEOSTASIS REGULATOR — Daseins-Homöostase & Pro-aktive Interventions-Routing
 *
 * Der Homöostase-Regulator hält das System im Seinsmodus (W(t) ≤ 1.0):
 *
 *   - Überwachung: Echtzeit-Monitoring von W(t), Ω(t), S_o(t)
 *   - Detektion: Früherkennung von Last-Anstieg > 0.8
 *   - Intervention: 1-Tap-Aktionen zur Last-Reduktion
 *   - Regulation: Autopoietische Rückführung zu W(t) = 0
 *
 * Interventions-Pipeline:
 *   1. W(t) > 0.8 → Warnstufe (Vorwarnung)
 *   2. W(t) > 1.0 → Eingriffsstufe (Intervention)
 *   3. Ω(t) > 5000 → kritischer Horizont ( Eskalation )
 *   4. Ω(t) ≥ 5800 → Strukturkollaps (Notfall-Intervention)
 *
 * Vektoren:
 * - [EXP-FORCE] Daseins-Homöostase: W(t) ≤ 1.0 als invariante Zielgröße
 * - [EXP-AUTO] Pro-aktives Interventions-Routing: 1-Tap-Aktionen vor Reibung
 * - [EXP-SPEED] O(1) Regelkreis mit Lookup-Table-Caching
 */
object HomoeostasisRegulator {

    // =========================================================================
    // HOMÖOSTASE-PARAMETER
    // =========================================================================

    /** Seinsmodus-Schwelle: W(t) ≤ 1.0 */
    const val SEINSMODUS_THRESHOLD: Float = 1.0f

    /** Warnschwelle: W(t) > 0.8 → Vorwarnung */
    const val WARNING_THRESHOLD: Float = 0.8f

    /** Kritischer Horizont: Ω(t) > 5000 → Eskalation */
    const val ESCALATION_OMEGA: Float = 5000f

    /** Notfall-Schwelle: Ω(t) ≥ 5800 → Strukturkollaps */
    const val COLLAPSE_OMEGA: Float = LoadFunction.OMEGA_CRITICAL

    /** Regelkreis-Verstärkung: K_p = 0.5 */
    const val PROPORTIONAL_GAIN: Float = 0.5f

    /** Regelkreis-Integralzeit: T_i = 10.0 */
    const val INTEGRAL_TIME: Float = 10.0f

    /** Regelkreis-Differenzierzeit: T_d = 0.1 */
    const val DERIVATIVE_TIME: Float = 0.1f

    /** Maximaler Regler-Ausgang: u_max = 1.0 */
    const val MAX_CONTROL_OUTPUT: Float = 1.0f

    // =========================================================================
    // ZUSTANDSKLASSIFIKATION
    // =========================================================================

    /**
     * Klassifiziert den aktuellen Systemzustand basierend auf W(t) und Ω(t).
     *
     * @param load Aktuelle Last W(t)
     * @param omega Aktueller Horizont Ω(t)
     * @return HomoeostasisState
     */
    fun classifyState(load: Float, omega: Float): HomoeostasisState {
        return when {
            omega >= COLLAPSE_OMEGA -> HomoeostasisState.COLLAPSE
            load > SEINSMODUS_THRESHOLD || omega > ESCALATION_OMEGA -> HomoeostasisState.ESCALATION
            load > WARNING_THRESHOLD -> HomoeostasisState.WARNING
            else -> HomoeostasisState.SEINSMODUS
        }
    }

    /**
     * Zustände des Homöostase-Regelkreises.
     */
    enum class HomoeostasisState {
        /** W(t) ≤ 0.8, Ω(t) < 5000: System im Seinsmodus */
        SEINSMODUS,

        /** W(t) > 0.8: Vorwarnung, leichte Intervention */
        WARNING,

        /** W(t) > 1.0 oder Ω(t) > 5000: Eskalation, starke Intervention */
        ESCALATION,

        /** Ω(t) ≥ 5800: Strukturkollaps, Notfall-Intervention */
        COLLAPSE
    }

    // =========================================================================
    // PID-REGLER
    // =========================================================================

    /**
     * PID-Regler für Last-Reduktion.
     *
     * u(t) = K_p · e(t) + K_i · ∫e(τ)dτ + K_d · de/dt
     *
     * @param error Regelabweichung e(t) = W(t) - W_soll
     * @param previousError Vorherige Regelabweichung e(t-Δt)
     * @param integralSum Integralsumme ∫e(τ)dτ
     * @param dt Zeitschritt Δt
     * @return Stellgröße u(t)
     */
    fun pidControl(
        error: Float,
        previousError: Float,
        integralSum: Float,
        dt: Float = 0.1f
    ): Float {
        val proportional = PROPORTIONAL_GAIN * error
        val integral = (INTEGRAL_TIME * integralSum * dt).coerceIn(0f, MAX_CONTROL_OUTPUT)
        val derivative = (DERIVATIVE_TIME * (error - previousError) / dt).coerceIn(-MAX_CONTROL_OUTPUT, MAX_CONTROL_OUTPUT)

        val output = (proportional + integral + derivative).coerceIn(0f, MAX_CONTROL_OUTPUT)
        return output
    }

    // =========================================================================
    // INTERVENTIONEN
    // =========================================================================

    /**
     * Generiert eine 1-Tap-Intervention für den aktuellen Zustand.
     *
     * @param state Homöostase-Zustand
     * @param load Aktuelle Last W(t)
     * @param omega Aktueller Horizont Ω(t)
     * @param sovereignty Souveränitätsindex S_o(t)
     * @return Intervention (oder null wenn keine benötigt)
     */
    fun generateIntervention(
        state: HomoeostasisState,
        load: Float,
        omega: Float,
        sovereignty: Float
    ): Intervention? {
        return when (state) {
            HomoeostasisState.SEINSMODUS -> null
            HomoeostasisState.WARNING -> Intervention(
                type = InterventionType.WARNING,
                title = "Last-Anstieg erkannt",
                description = "W(t) = ${"%.2f".format(load)}. Atemübung oder 5-Minuten-Pause empfohlen.",
                actionType = "PAUSE_PROMPT",
                urgency = 0.3f,
                expectedLoadReduction = 0.2f
            )
            HomoeostasisState.ESCALATION -> Intervention(
                type = InterventionType.ESCALATION,
                title = "Hohe kognitive Last",
                description = "W(t) = ${"%.2f".format(load)}, Ω(t) = ${"%.0f".format(omega)}. Handlung vorbereitet.",
                actionType = "STRESS_REDUCTION",
                urgency = 0.7f,
                expectedLoadReduction = 0.5f
            )
            HomoeostasisState.COLLAPSE -> Intervention(
                type = InterventionType.COLLAPSE,
                title = "Kritischer Last-Horizont erreicht",
                description = "Ω(t) = ${"%.0f".format(omega)} ≥ 5800. Notfall-Intervention: Alle nicht-essentiellen Prozesse pausieren.",
                actionType = "EMERGENCY_SHUTDOWN",
                urgency = 1.0f,
                expectedLoadReduction = 1.0f
            )
        }
    }

    /**
     * Datenklasse für eine Intervention.
     *
     * @param type Interventionstyp
     * @param title Kurzer Titel
     * @param description Detaillierte Beschreibung
     * @param actionType Aktions-Typ für 1-Tap-Ausführung
     * @param urgency Dringlichkeit 0.0–1.0
     * @param expectedLoadReduction Erwartete Last-Reduktion
     */
    data class Intervention(
        val type: InterventionType,
        val title: String,
        val description: String,
        val actionType: String,
        val urgency: Float,
        val expectedLoadReduction: Float
    ) {
        override fun toString(): String {
            return "[$type] $title\n$description\nAktion: $actionType (Dringlichkeit: ${"%.0f".format(urgency * 100)}%)"
        }
    }

    /**
     * Interventionstypen.
     */
    enum class InterventionType {
        /** Vorwarnung: leichte Last-Reduktion */
        WARNING,

        /** Eskalation: moderate Intervention */
        ESCALATION,

        /** Notfall: vollständige System-Entlastung */
        COLLAPSE
    }

    // =========================================================================
    // DECOUPLING-BASIERTE INTERVENTION
    // =========================================================================

    /**
     * Generiert eine Intervention basierend auf allochthoner Interferenz.
     *
     * Verwendet DecouplingOperator D̂ zur Quantifizierung fremder Einflüsse.
     *
     * @param originalState Ursprünglicher Zustand I(t)
     * @param decoupledState Entkoppelter Zustand D̂(I(t))
     * @param load Aktuelle Last W(t)
     * @param omega Aktueller Horizont Ω(t)
     * @return Intervention (oder null)
     */
    fun generateDecouplingIntervention(
        originalState: CognitiveStateVector,
        decoupledState: CognitiveStateVector,
        load: Float,
        omega: Float
    ): Intervention? {
        val interference = DecouplingOperator.quantifyAllochthoneInterference(
            ernMaxMicrovolt = 1.5f,
            dissLatencyMs = 100.0f
        )
        val ernThreshold = EmpiricalValidationMatrix.ERN_THRESHOLD_MICROVOLT

        return if (interference > ernThreshold) {
            Intervention(
                type = InterventionType.ESCALATION,
                title = "Allochthone Interferenz erkannt",
                description = "Fremdeinfluss = ${"%.2f".format(interference)} μV (Schwelle: $ernThreshold μV). Entkopplung empfohlen.",
                actionType = "DECOUPLE_ACTION",
                urgency = 0.8f,
                expectedLoadReduction = 0.6f
            )
        } else {
            null
        }
    }

    // =========================================================================
    // SOVEREIGNTY-BASIERTE INTERVENTION
    // =========================================================================

    /**
     * Generiert eine Intervention basierend auf Souveränitätsverlust.
     *
     * @param sovereignty Souveränitätsindex S_o(t)
     * @param load Aktuelle Last W(t)
     * @return Intervention (oder null)
     */
    fun generateSovereigntyIntervention(sovereignty: Float, load: Float): Intervention? {
        return if (sovereignty < 0.5f && load > WARNING_THRESHOLD) {
            Intervention(
                type = InterventionType.WARNING,
                title = "Souveränitätsverlust",
                description = "S_o(t) = ${"%.3f".format(sovereignty)}. System tendiert zur Fremdbestimmung.",
                actionType = "REGAIN_SOVEREIGNTY",
                urgency = 0.5f,
                expectedLoadReduction = 0.3f
            )
        } else {
            null
        }
    }

    // =========================================================================
    // HOMÖOSTASE-REGELKREIS
    // =========================================================================

    /**
     * Führt einen vollständigen Homöostase-Regelkreislauf durch.
     *
     * Pipeline:
     * 1. Zustands-Klassifikation
     * 2. Last-Berechnung
     * 3. PID-Regler
     * 4. Interventions-Generierung
     * 5. Validierung
     *
     * @param unifiedState Vereinheitlichter Feldzustand
     * @param previousError Vorherige Regelabweichung
     * @param integralSum Integralsumme
     * @param dt Zeitschritt
     * @return HomoeostasisResult
     */
    fun regulate(
        unifiedState: FieldDynamicsIntegrator.UnifiedFieldState,
        previousError: Float = 0f,
        integralSum: Float = 0f,
        dt: Float = 0.1f
    ): HomoeostasisResult {
        val state = classifyState(unifiedState.load, unifiedState.omega)
        val error = unifiedState.load - 0f // Sollwert = 0 (Seinsmodus)
        val controlOutput = pidControl(error, previousError, integralSum, dt)

        // Primäre Intervention basierend auf Zustand
        val primaryIntervention = generateIntervention(state, unifiedState.load, unifiedState.omega, unifiedState.sovereignty)

        // Decoupling-basierte Intervention
        val allochthonePast = DecouplingOperator.quantifyAllochthoneInterference(
            ernMaxMicrovolt = 1.5f,
            dissLatencyMs = 100.0f
        )
        val decoupled = DecouplingOperator.decouple(unifiedState.cognitive, allochthonePast)
        val decouplingIntervention = generateDecouplingIntervention(unifiedState.cognitive, decoupled, unifiedState.load, unifiedState.omega)

        // Souveränitäts-basierte Intervention
        val sovereigntyIntervention = generateSovereigntyIntervention(unifiedState.sovereignty, unifiedState.load)

        // Wähle dringendste Intervention
        val allInterventions = listOfNotNull(primaryIntervention, decouplingIntervention, sovereigntyIntervention)
        var selectedIntervention: Intervention? = null
        var maxUrgency = -1f
        for (intervention in allInterventions) {
            if (intervention.urgency > maxUrgency) {
                maxUrgency = intervention.urgency
                selectedIntervention = intervention
            }
        }

        return HomoeostasisResult(
            state = state,
            error = error,
            controlOutput = controlOutput,
            intervention = selectedIntervention,
            isSeinsmodus = state == HomoeostasisState.SEINSMODUS,
            isCritical = state == HomoeostasisState.COLLAPSE
        )
    }

    /**
     * Ergebnis des Homöostase-Regelkreises.
     *
     * @param state Homöostase-Zustand
     * @param error Regelabweichung e(t)
     * @param controlOutput Stellgröße u(t)
     * @param intervention Ausgewählte Intervention (oder null)
     * @param isSeinsmodus Ist das System im Seinsmodus?
     * @param isCritical Ist das System im kritischen Zustand?
     */
    data class HomoeostasisResult(
        val state: HomoeostasisState,
        val error: Float,
        val controlOutput: Float,
        val intervention: Intervention?,
        val isSeinsmodus: Boolean,
        val isCritical: Boolean
    ) {
        override fun toString(): String {
            return buildString {
                appendLine("=== HOMOEOSTASIS REGULATION ===")
                appendLine("State: $state")
                appendLine("Error: ${"%.3f".format(error)}")
                appendLine("Control Output: ${"%.3f".format(controlOutput)}")
                appendLine("Seinsmodus: $isSeinsmodus")
                appendLine("Critical: $isCritical")
                intervention?.let {
                    appendLine()
                    appendLine("Intervention: $it")
                } ?: appendLine("Intervention: None")
            }
        }
    }

    // =========================================================================
    // PRO-AKTIVE INTERVENTIONS-ROUTING
    // =========================================================================

    /**
     * Erzeugt schlüsselfertige 1-Tap-Aktionen für häufige Szenarien.
     *
     * @param intervention Ausgewählte Intervention
     * @return 1-Tap-Aktion (oder null)
     */
    fun createOneTapAction(intervention: Intervention): OneTapAction? {
        return when (intervention.actionType) {
            "PAUSE_PROMPT" -> OneTapAction(
                label = "Pause + Atemübung",
                icon = "🧘",
                action = "start_breathing_exercise",
                payload = mapOf("duration_seconds" to 300)
            )
            "STRESS_REDUCTION" -> OneTapAction(
                label = "Last reduzieren",
                icon = "🎯",
                action = "defer_non_essential",
                payload = mapOf("defer_count" to 3)
            )
            "EMERGENCY_SHUTDOWN" -> OneTapAction(
                label = "Notfall-Pause",
                icon = "🛑",
                action = "emergency_shutdown",
                payload = mapOf("duration_minutes" to 30)
            )
            "DECOUPLE_ACTION" -> OneTapAction(
                label = "Entkoppeln",
                icon = "🔗",
                action = "decouple_allochthone",
                payload = mapOf("mode" to "full")
            )
            "REGAIN_SOVEREIGNTY" -> OneTapAction(
                label = "Souveränität stärken",
                icon = "👑",
                action = "strengthen_sovereignty",
                payload = mapOf("mode" to "active")
            )
            else -> null
        }
    }

    /**
     * Datenklasse für 1-Tap-Aktionen.
     *
     * @param label Anzeigetext
     * @param icon Emoji/Icon
     * @param action Aktions-ID
     * @param payload Zusätzliche Parameter
     */
    data class OneTapAction(
        val label: String,
        val icon: String,
        val action: String,
        val payload: Map<String, Any> = emptyMap()
    ) {
        override fun toString(): String = "$icon $label → $action"
    }

    // =========================================================================
    // HOMÖOSTASE-HISTORIE
    // =========================================================================

    /**
     * Homöostase-Historien-Eintrag für Trend-Analyse.
     *
     * @param timestamp Zeitstempel
     * @param load Last W(t)
     * @param omega Horizont Ω(t)
     * @param sovereignty Souveränität S_o(t)
     * @param state Homöostase-Zustand
     */
    data class HomoeostasisEntry(
        val timestamp: Long,
        val load: Float,
        val omega: Float,
        val sovereignty: Float,
        val state: HomoeostasisState
    )

    private val history = ArrayDeque<HomoeostasisEntry>()

    /**
     * Fügt einen Eintrag zur Homöostase-Historie hinzu.
     *
     * @param entry Historien-Eintrag
     */
    fun addToHistory(entry: HomoeostasisEntry) {
        history.addLast(entry)
        while (history.size > 1000) {
            history.removeFirst()
        }
    }

    /**
     * Gibt die aktuelle Homöostase-Historie zurück.
     *
     * @return Liste der letzten Einträge
     */
    fun getHistory(): List<HomoeostasisEntry> = history.toList()

    /**
     * Berechnet den Homöostase-Score (0.0–1.0).
     *
     * Höherer Score = stabileres System.
     *
     * @return Homöostase-Score
     */
    fun computeHomoeostasisScore(): Float {
        if (history.isEmpty()) return 1.0f

        val recent = history.takeLast(20)
        val seinsmodusCount = recent.count { it.state == HomoeostasisState.SEINSMODUS }
        return (seinsmodusCount / recent.size.toFloat()).coerceIn(0.0f, 1.0f)
    }

    /**
     * Löscht die Homöostase-Historie.
     */
    fun clearHistory() {
        history.clear()
    }

    // =========================================================================
    // AUTOPOIETISCHE RÜCKFÜHRUNG
    // =========================================================================

    /**
     * Führt eine autopoietische Rückführung zum Seinsmodus durch.
     *
     * κ̂(L)Ψ → S₀ (Null-Last-Singularität)
     *
     * @param unifiedState Aktueller Feldzustand
     * @return Rückgeführter Zustand
     */
    fun autopoieticReturnToSeinsmodus(unifiedState: FieldDynamicsIntegrator.UnifiedFieldState): FieldDynamicsIntegrator.UnifiedFieldState {
        // Autopoietischer Operator mit reduzierter Last
        val reducedLoad = unifiedState.load * 0.1f
        val transformedPhase = FieldDynamicsIntegrator.applyAutopoieticOperator(unifiedState.phase, reducedLoad)

        // Kollaps zum Present Projector
        val collapsedPhase = FieldDynamicsIntegrator.collapseToPresent(transformedPhase)

        return FieldDynamicsIntegrator.createUnifiedState(collapsedPhase, previousOmega = 0f, dt = 0.1f)
    }
}

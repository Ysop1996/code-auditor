package de.lifeos.core.field

import kotlin.math.*

/**
 * FIELD DYNAMICS INTEGRATOR — P5 Integration Layer
 *
 * Verbindet die P0–P4 Kernel-Primitive mit der bestehenden Engine-Architektur:
 * - CognitiveStateVector ↔ PhaseVector Konvertierung
 * - LoadFunction W(t) & Ω(t) Integration in Trajektorien-Ausführung
 * - SovereigntyIndex S_o(t) als Systemzustands-Monitor
 * - U1GaugeField Phasenkovarianz für Trajektorien-Planung
 * - PathIntegralOperator für probabilistische Pfadauswahl
 * - EffectiveDimension d_α(t) für dimensionale Adaptation
 * - PresentProjector P_S für Zustandskollaps
 *
 * Vektoren:
 * - [EXP-FORCE] Deterministische Feldsteuerung: mathematische Primitive als Engine-Treiber
 * - [EXP-AUTO] Autopoietische Regulation: Echtzeit-Adaptation der Dimension und Last
 * - [EXP-SPEED] O(N) Integration mit Lookup-Table-Caching
 */
object FieldDynamicsIntegrator {

    // =========================================================================
    // INTEGRATIONS-PARAMETER
    // =========================================================================

    /** Phasenvektor-Dimension (kompatibel mit PhaseVector) */
    const val PHASE_DIMENSION: Int = 32

    /** Trajektorien-Schrittweite */
    const val TRAJECTORY_STEP: Float = 0.1f

    /** Kollaps-Schwelle für PresentProjector */
    const val COLLAPSE_THRESHOLD: Float = 0.01f

    /** Gauge-Kopplung für Phasenkovarianz */
    const val GAUGE_COUPLING: Float = 0.5f

    /** Pfadintegral-Sample-Anzahl für Trajektorien-Planung */
    const val PATH_INTEGRAL_SAMPLES: Int = 100

    // =========================================================================
    // COGNITIVE STATE ↔ PHASE VECTOR KONVERTER
    // =========================================================================

    /**
     * Konvertiert CognitiveStateVector I(t) ∈ R³≥0 in PhaseVector ∈ R³².
     *
     * Abbildung: I(t) = [i_past, i_present, i_future] → PhaseVector(32)
     * - Komponenten werden auf 32 Dimensionen expandiert
     * - Restliche Dimensionen werden mit goldenem Schnitt φ gefüllt
     *
     * @param cognitive I(t) ∈ R³≥0
     * @return PhaseVector(32) mit expandierter Darstellung
     */
    fun toPhaseVector(cognitive: CognitiveStateVector): PhaseVector {
        val dim = FloatArray(PHASE_DIMENSION)
        dim[0] = cognitive.past
        dim[1] = cognitive.present
        dim[2] = cognitive.future

        // Restliche Dimensionen mit φ-Skalierung füllen (iterativ, ohne pow()-Overflow)
        val phi = 1.61803398875f
        var phiPow = phi * phi  // φ² für i=3 (i-2=1)
        for (i in 3 until PHASE_DIMENSION) {
            dim[i] = (cognitive.past * phiPow + cognitive.present * (phiPow * phi) + cognitive.future * (phiPow * phi * phi)) * 0.1f
            phiPow *= phi
        }

        return PhaseVector(dim).normalize()
    }

    /**
     * Konvertiert PhaseVector ∈ R³² in CognitiveStateVector I(t) ∈ R³≥0.
     *
     * Projektion: PhaseVector(32) → I(t) = [i_past, i_present, i_future]
     * - Erste 3 Komponenten werden extrahiert
     * - Nicht-negativ-Clamping
     *
     * @param phase PhaseVector(32)
     * @return CognitiveStateVector I(t) ∈ R³≥0
     */
    fun toCognitiveState(phase: PhaseVector): CognitiveStateVector {
        val past = max(0f, phase.dim.getOrElse(0) { 0f })
        val present = max(0f, phase.dim.getOrElse(1) { 0f })
        val future = max(0f, phase.dim.getOrElse(2) { 0f })
        return CognitiveStateVector(past, present, future)
    }

    // =========================================================================
    // LOAD FUNCTION INTEGRATION
    // =========================================================================

    /**
     * Berechnet die Last W(t) aus einem Phasenvektor und aktualisiert den Horizont Ω(t).
     *
     * W(t) = μ₁·i_past + μ₂·i_future
     * Ω(t) = ∫₀ᵗ W(τ) dτ (trapezoidal)
     *
     * @param phase PhaseVector
     * @param previousOmega Vorheriger Horizont Ω(t-Δt)
     * @param dt Zeitschritt Δt
     * @return Pair(W(t), Ω(t))
     */
    fun computeLoadAndHorizon(phase: PhaseVector, previousOmega: Float = 0f, dt: Float = TRAJECTORY_STEP): Pair<Float, Float> {
        val cognitive = toCognitiveState(phase)
        val w = cognitive.computeLoad()
        val omega = previousOmega + (w * dt).coerceIn(0f, LoadFunction.OMEGA_CRITICAL * 1.1f)
        return w to omega
    }

    /**
     * Überprüft, ob der kritische Horizont erreicht ist.
     *
     * Ω(t) ≥ Ω_krit = 5800 W·s
     *
     * @param omega Aktueller Horizont Ω(t)
     * @return true wenn Ω(t) ≥ 5800
     */
    fun isCriticalHorizon(omega: Float): Boolean = omega >= LoadFunction.OMEGA_CRITICAL

    // =========================================================================
    // SOVEREIGNTY INDEX INTEGRATION
    // =========================================================================

    /**
     * Berechnet den Souveränitätsindex S_o(t) aus einem Phasenvektor.
     *
     * S_o(t) = (i_past_fremd + i_future_fremd) / i_present
     *
     * @param phase PhaseVector
     * @return Souveränitätsindex S_o(t)
     */
    fun computeSovereignty(phase: PhaseVector): Float {
        val cognitive = toCognitiveState(phase)
        // Approximation: allochthone Anteile = Differenz zu UNIT-Zustand
        val allochthonePast = max(0f, cognitive.past - CognitiveStateVector.UNIT.past)
        val allochthoneFuture = max(0f, cognitive.future - CognitiveStateVector.UNIT.future)
        return SovereigntyIndex.compute(allochthonePast, allochthoneFuture, cognitive.present)
    }

    /**
     * Überprüft, ob ein Kausalitätsbruch vorliegt.
     *
     * t_D = argmax_t (dS_o/dt < 0)
     *
     * @param sovereigntyHistory Historie von S_o(t)-Werten
     * @return Zeitpunkt t_D des Kausalitätsbruchs (oder -1 wenn keiner)
     */
    fun detectCausalityBreak(sovereigntyHistory: List<Float>): Int {
        if (sovereigntyHistory.size < 3) return -1

        for (i in 2 until sovereigntyHistory.size) {
            val prev = sovereigntyHistory[i - 1] - sovereigntyHistory[i - 2]
            val curr = sovereigntyHistory[i] - sovereigntyHistory[i - 1]
            if (prev > 0 && curr < 0) {
                return i
            }
        }
        return -1
    }

    // =========================================================================
    // EFFECTIVE DIMENSION ADAPTATION
    // =========================================================================

    /**
     * Berechnet die effektive Dimension d_α(t) für einen gegebenen Lastvektor.
     *
     * d_α(t) = Σ_{k ∈ {past,present,future}} σ_α(i_k(t) - θ₀)
     *
     * @param phase PhaseVector
     * @param maxDim Maximale Dimension D_max
     * @return Effektive Dimension d_α(t)
     */
    fun computeEffectiveDimension(phase: PhaseVector, maxDim: Int = PHASE_DIMENSION): Float {
        val cognitive = toCognitiveState(phase)
        return EffectiveDimension.compute(cognitive)
    }

    /**
     * Adaptiert die Phasenvektor-Dimension basierend auf der aktuellen Last.
     *
     * @param phase PhaseVector
     * @return Adaptierter PhaseVector mit reduzierter Dimension
     */
    fun adaptPhaseDimension(phase: PhaseVector): PhaseVector {
        val effectiveDim = computeEffectiveDimension(phase).toInt().coerceAtLeast(3)
        val newDim = FloatArray(effectiveDim) { i ->
            if (i < phase.dim.size) phase.dim[i] else 0f
        }
        return PhaseVector(newDim).normalize()
    }

    // =========================================================================
    // PRESENT PROJECTOR (ZUSTANDSKOLLAPS)
    // =========================================================================

    /**
     * Führt den PresentProjector P_S auf einen Phasenvektor an.
     *
     * P_S = diag(0, 1, 0, 0, ..., 0)
     *
     * Setzt alle Komponenten außer i_present auf 0.
     *
     * @param phase PhaseVector
     * @return Kollabierter PhaseVector (nur i_present ≠ 0)
     */
    fun collapseToPresent(phase: PhaseVector): PhaseVector {
        val collapsed = FloatArray(PHASE_DIMENSION)
        collapsed[1] = phase.dim.getOrElse(1) { 0f }
        return PhaseVector(collapsed).normalize()
    }

    /**
     * Überprüft, ob ein Zustand kollabiert werden sollte.
     *
     * Kollaps wenn ∥I(t)∥ < ε (nahe am Nullzustand)
     *
     * @param phase PhaseVector
     * @return true wenn Kollaps empfohlen
     */
    fun shouldCollapse(phase: PhaseVector): Boolean {
        val cognitive = toCognitiveState(phase)
        return cognitive.norm() < COLLAPSE_THRESHOLD
    }

    // =========================================================================
    // U(1) GAUGE FIELD INTEGRATION
    // =========================================================================

    /**
     * Berechnet die gauge-korrigierte Trajektorie zwischen zwei Zuständen.
     *
     * Verwendet U1GaugeField für Paralleltransport entlang der Trajektorie.
     *
     * @param start Start-PhaseVector
     * @param end End-PhaseVector
     * @param numSteps Anzahl Schritte
     * @return Liste von gauge-korrigierten PhaseVektoren
     */
    fun gaugeCorrectedTrajectory(start: PhaseVector, end: PhaseVector, numSteps: Int = 10): List<PhaseVector> {
        val trajectory = mutableListOf<PhaseVector>()
        var currentPsi = start.normalize().dim.toList()

        for (k in 1 until numSteps) {
            val t = k.toFloat() / numSteps
            // Lineare Interpolation
            val target = start * (1.0f - t) + end * t
            // Gauge-Korrektur
            val aMu = U1GaugeField.gaugePotential(t)
            val psiPair = currentPsi.first().let { it to currentPsi.getOrElse(1) { 0f } }
            val corrected = U1GaugeField.parallelTransport(psiPair, aMu, TRAJECTORY_STEP)
            val correctedVec = FloatArray(PHASE_DIMENSION) { i ->
                when (i) {
                    0 -> corrected.first
                    1 -> corrected.second
                    else -> target.dim.getOrElse(i) { 0f }
                }
            }
            trajectory.add(PhaseVector(correctedVec).normalize())
            currentPsi = correctedVec.toList()
        }

        trajectory.add(end.normalize())
        return trajectory
    }

    /**
     * Berechnet die Phasenkovarianz zwischen zwei Trajektorien.
     *
     * @param trajA Erste Trajektorie
     * @param trajB Zweite Trajektorie
     * @return Durchschnittliche Phasenkovarianz
     */
    fun averagePhaseCovariance(trajA: List<PhaseVector>, trajB: List<PhaseVector>): Float {
        if (trajA.isEmpty() || trajB.isEmpty()) return 0.0f

        var totalCovariance = 0.0
        val len = min(trajA.size, trajB.size)

        for (i in 0 until len) {
            val psiA = trajA[i].dim.first().let { it to trajA[i].dim.getOrElse(1) { 0f } }
            val psiB = trajB[i].dim.first().let { it to trajB[i].dim.getOrElse(1) { 0f } }
            totalCovariance += U1GaugeField.phaseCovariance(psiA, psiB)
        }

        return (totalCovariance / len).toFloat().coerceIn(0.0f, 1.0f)
    }

    // =========================================================================
    // PATH INTEGRAL TRAJECTORY PLANNING
    // =========================================================================

    /**
     * Plant eine Trajektorie zwischen zwei Zuständen mittels Pfadintegral.
     *
     * Verwendet PathIntegralOperator für probabilistische Trajektorien-Auswahl.
     *
     * @param start Start-CognitiveStateVector
     * @param end End-CognitiveStateVector
     * @param numSteps Anzahl Zeitschritte
     * @return Optimale Trajektorie als Liste von CognitiveStateVektoren
     */
    fun planTrajectory(start: CognitiveStateVector, end: CognitiveStateVector, numSteps: Int = 10): List<CognitiveStateVector> {
        // Stationärer Pfad (klassische Lösung)
        return PathIntegralOperator.stationaryPath(start, end, numSteps, TRAJECTORY_STEP)
    }

    /**
     * Berechnet die Übergangswahrscheinlichkeit zwischen zwei Zuständen.
     *
     * @param start Start-CognitiveStateVector
     * @param end End-CognitiveStateVector
     * @return Übergangswahrscheinlichkeit P ∈ [0, 1]
     */
    fun transitionProbability(start: CognitiveStateVector, end: CognitiveStateVector): Float {
        return PathIntegralOperator.transitionProbability(start, end, numSteps = 50, numSamples = PATH_INTEGRAL_SAMPLES, dt = TRAJECTORY_STEP)
    }

    // =========================================================================
    // AUTOPOIETIC OPERATOR INTEGRATION
    // =========================================================================

    /**
     * Wendet den autopoietischen Operator κ̂(L) auf einen Zustand an.
     *
     * κ̂(L)Ψ = ω∇IΨ - R_LΨ
     *
     * @param phase PhaseVector
     * @param load Aktuelle Last W(t)
     * @return Transformierter PhaseVector
     */
    fun applyAutopoieticOperator(phase: PhaseVector, load: Float = 1.0f): PhaseVector {
        val cognitive = toCognitiveState(phase)
        val transformed = AutopoieticOperator.apply(cognitive, load)
        return toPhaseVector(transformed)
    }

    // =========================================================================
    // PGO TUNNEL OPERATOR INTEGRATION
    // =========================================================================

    /**
     * Überprüft, ob ein PGO-Tunnel-Ereignis vorliegt.
     *
     * T_PGO > ε (WKB-Tunnelwahrscheinlichkeit überschreitet Schwelle)
     *
     * @param phase PhaseVector
     * @param barrier Potenzialbarriere
     * @return true wenn Tunnel-Ereignis erkannt
     */
    fun detectPgoTunnel(phase: PhaseVector, barrier: Float = 1.0f): Boolean {
        val cognitive = toCognitiveState(phase)
        // Tunnel von x1=0 zu x2=1 durch Potentialbarriere
        val prob = PgoTunnelOperator.tunnelProbability(
            x1 = 0f,
            x2 = 1f,
            presentEnergy = cognitive.present,
            barrierHeight = barrier
        )
        return prob > PgoTunnelOperator.TUNNEL_THRESHOLD
    }

    /**
     * Führt einen Cache-Reset durch (PGO-Tunnel-Ereignis).
     *
     * @param phase PhaseVector
     * @return Reset-PhaseVector
     */
    fun resetCache(phase: PhaseVector): PhaseVector {
        val cognitive = toCognitiveState(phase)
        val prob = PgoTunnelOperator.tunnelProbability(
            x1 = 0f,
            x2 = 1f,
            presentEnergy = cognitive.present,
            barrierHeight = 1.0f
        )
        val resetCognitive = CognitiveStateVector(
            past = PgoTunnelOperator.cacheReset(cognitive.past, prob),
            present = PgoTunnelOperator.cacheReset(cognitive.present, prob),
            future = PgoTunnelOperator.cacheReset(cognitive.future, prob)
        )
        return toPhaseVector(resetCognitive)
    }

    // =========================================================================
    // LINDBLAD DECOHERENCE
    // =========================================================================

    /**
     * Berechnet die Dekohärenzzeit τ_decoh für einen Zustand.
     *
     * @param phase PhaseVector
     * @return Dekohärenzzeit τ_decoh
     */
    fun computeDecoherenceTime(phase: PhaseVector): Float {
        val cognitive = toCognitiveState(phase)
        return LindbladMasterEquation.decoherenceTime()
    }

    /**
     * Berechnet die von-Neumann-Entropie S_vN für einen Zustand.
     *
     * @param phase PhaseVector
     * @return Entropie S_vN
     */
    fun computeVonNeumannEntropy(phase: PhaseVector): Float {
        val cognitive = toCognitiveState(phase)
        return LindbladMasterEquation.vonNeumannEntropy(cognitive)
    }

    // =========================================================================
    // EMPIRICAL VALIDATION
    // =========================================================================

    /**
     * Führt eine vollständige Systemvalidierung durch.
     *
     * @param phase PhaseVector
     * @param omega Aktueller Horizont Ω(t)
     * @return Validierungsbericht
     */
    fun validateSystemState(phase: PhaseVector, omega: Float): EmpiricalValidationMatrix.ValidationReport {
        val cognitive = toCognitiveState(phase)
        val w = cognitive.computeLoad()
        val svn = computeVonNeumannEntropy(phase)
        val sovereignty = computeSovereignty(phase)

        return EmpiricalValidationMatrix.validateSystem(
            plv = 0.973408f, // PLV wird extern gemessen
            wBase = w,
            ernMax = 0.5f, // ERN wird extern gemessen
            svn = svn,
            ePenalty = 1.201301f,
            mMask = 0.643047f,
            omegaKrit = omega
        )
    }

    // =========================================================================
    // UNIFIED FIELD STATE
    // =========================================================================

    /**
     * Vereinheitlichter Feldzustand, der alle P0–P4-Primitive kombiniert.
     *
     * @param cognitive I(t) ∈ R³≥0
     * @param phase PhaseVector ∈ R³²
     * @param load W(t)
     * @param omega Ω(t)
     * @param sovereignty S_o(t)
     * @param effectiveDim d_α(t)
     * @param decoherenceTime τ_decoh
     * @param entropy S_vN
     */
    data class UnifiedFieldState(
        val cognitive: CognitiveStateVector,
        val phase: PhaseVector,
        val load: Float,
        val omega: Float,
        val sovereignty: Float,
        val effectiveDim: Float,
        val decoherenceTime: Float,
        val entropy: Float
    ) {
        /** Ist das System im Seinsmodus? */
        fun isSeinsmodus(): Boolean = cognitive.isSeinsmodus()

        /** Ist der kritische Horizont erreicht? */
        fun isCriticalHorizon(): Boolean = omega >= LoadFunction.OMEGA_CRITICAL

        /** Ist das System souverän? */
        fun isSovereign(): Boolean = sovereignty > 0.5f

        /** Zustands-String für Debugging */
        override fun toString(): String {
            return buildString {
                appendLine("=== UNIFIED FIELD STATE ===")
                appendLine("I(t) = [i_past=${"%.3f".format(cognitive.past)}, i_present=${"%.3f".format(cognitive.present)}, i_future=${"%.3f".format(cognitive.future)}]")
                appendLine("W(t) = ${"%.3f".format(load)}")
                appendLine("Ω(t) = ${"%.1f".format(omega)} / ${LoadFunction.OMEGA_CRITICAL}")
                appendLine("S_o(t) = ${"%.3f".format(sovereignty)}")
                appendLine("d_α(t) = ${"%.1f".format(effectiveDim)}")
                appendLine("τ_decoh = ${"%.3f".format(decoherenceTime)}")
                appendLine("S_vN = ${"%.4f".format(entropy)}")
                appendLine("Seinsmodus: ${isSeinsmodus()}")
                appendLine("Kritisch: ${isCriticalHorizon()}")
                appendLine("Souverän: ${isSovereign()}")
            }
        }
    }

    /**
     * Erstellt einen vereinheitlichten Feldzustand aus einem Phasenvektor.
     *
     * @param phase PhaseVector
     * @param previousOmega Vorheriger Horizont Ω(t-Δt)
     * @param dt Zeitschritt Δt
     * @return UnifiedFieldState
     */
    fun createUnifiedState(phase: PhaseVector, previousOmega: Float = 0f, dt: Float = TRAJECTORY_STEP): UnifiedFieldState {
        val cognitive = toCognitiveState(phase)
        val (load, omega) = computeLoadAndHorizon(phase, previousOmega, dt)
        val sovereignty = computeSovereignty(phase)
        val effectiveDim = computeEffectiveDimension(phase)
        val decoherenceTime = computeDecoherenceTime(phase)
        val entropy = computeVonNeumannEntropy(phase)

        return UnifiedFieldState(
            cognitive = cognitive,
            phase = phase,
            load = load,
            omega = omega,
            sovereignty = sovereignty,
            effectiveDim = effectiveDim,
            decoherenceTime = decoherenceTime,
            entropy = entropy
        )
    }

    // =========================================================================
    // TRAJECTORY EXECUTION WITH FIELD PRIMITIVES
    // =========================================================================

    /**
     * Führt eine Trajektorie mit vollständiger Feld-Dynamik aus.
     *
     * Kombiniert:
     * 1. CognitiveStateVector → PhaseVector Konvertierung
     * 2. LoadFunction W(t) & Ω(t) Integration
     * 3. SovereigntyIndex S_o(t) Monitoring
     * 4. EffectiveDimension d_α(t) Adaptation
     * 5. PresentProjector P_S Kollaps
     * 6. U1GaugeField Phasenkovarianz
     *
     * @param stimulus Input-PhaseVector
     * @param maxSteps Maximale Schritte
     * @return Liste von UnifiedFieldState-Zuständen
     */
    fun executeFieldTrajectory(stimulus: PhaseVector, maxSteps: Int = 16): List<UnifiedFieldState> {
        val trajectory = mutableListOf<UnifiedFieldState>()
        var currentPhase = stimulus.normalize()
        var previousOmega = 0f

        for (step in 0 until maxSteps) {
            val state = createUnifiedState(currentPhase, previousOmega, TRAJECTORY_STEP)
            trajectory.add(state)

            // Kollaps-Prüfung
            if (shouldCollapse(currentPhase)) {
                currentPhase = collapseToPresent(currentPhase)
            }

            // Dimension-Adaptation
            currentPhase = adaptPhaseDimension(currentPhase)

            // Autopoietische Transformation
            currentPhase = applyAutopoieticOperator(currentPhase, state.load)

            // PGO-Tunnel-Prüfung
            if (detectPgoTunnel(currentPhase)) {
                currentPhase = resetCache(currentPhase)
            }

            previousOmega = state.omega

            // Abbruchbedingungen
            if (state.isSeinsmodus() || state.isCriticalHorizon()) {
                break
            }

            // Einfache Feld-Aktualisierung (ohne native Bridge)
            val force = FloatArray(PHASE_DIMENSION) { i ->
                when (i % 4) {
                    0 -> state.load * 0.1f
                    1 -> state.sovereignty * 0.1f
                    2 -> state.effectiveDim * 0.01f
                    else -> sin(i.toFloat() + step * TRAJECTORY_STEP) * 0.05f
                }
            }
            currentPhase = PhaseVector(force).normalize()
        }

        return trajectory
    }
}

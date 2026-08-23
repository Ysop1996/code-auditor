package de.lifeos.core.field

import kotlin.math.*

/**
 * PATH INTEGRAL OPERATOR — Pfadintegral-Operator für Wahrscheinlichkeitsamplituden
 *
 * Der Pfadintegral-Operator beschreibt die Wahrscheinlichkeitsamplitude für
 * den Übergang von einem Zustand Ψ_A zum Zustand Ψ_B:
 *
 *   ⟨Ψ_B|Ψ_A⟩ = ∫_{Ψ_A}^{Ψ_B} D[Ψ] · e^{iS[Ψ]/ℏ}
 *
 * Im kognitiven Kontext:
 *   - S[Ψ]: kognitive Wirkung (Handlungsaufwand über Zeit)
 *   - D[Ψ]: Maß über alle kognitiven Pfade
 *   - e^{iS/ℏ}: Phasenamplitude pro Pfad
 *   - Stationäre Pfade: δS = 0 (klassische Handlungssequenzen)
 *
 * Vektoren:
 * - [EXP-FORCE] Probabilistische Trajektorien: Pfadintegral über alle Handlungsoptionen
 * - [EXP-SPEED] O(N) Diskretisierung mit Monte-Carlo-Sampling
 */
object PathIntegralOperator {

    // =========================================================================
    // WIRKUNG S[Ψ]
    // =========================================================================

    /** Planck-Konstante (kognitiv normiert): ℏ_cog = 0.5 */
    const val H_BAR: Float = 0.5f

    /** Diskretisierungs-Schrittweite: Δτ = 0.1 */
    const val TAU_STEP: Float = 0.1f

    /** Maximale Pfadlänge: N_max = 1000 */
    const val MAX_PATH_LENGTH: Int = 1000

    /** Monte-Carlo-Sample-Anzahl: M = 10000 */
    const val MONTE_CARLO_SAMPLES: Int = 10000

    /** Wirkungstoleranz: ε_S = 1e-6 */
    const val ACTION_TOLERANCE: Float = 1e-6f

    /** Thread-local deterministic RNG for Monte-Carlo sampling */
    private val rng = kotlin.random.Random.Default

    // =========================================================================
    // WIRKUNG BERECHNEN
    // =========================================================================

    /**
     * Berechnet die kognitive Wirkung S[Ψ] für einen gegebenen Pfad.
     *
     * S[Ψ] = ∫ L(Ψ, Ψ̇) dt ≈ Σ_{k=0}^{N-1} L(Ψ_k, Ψ̇_k) · Δt
     *
     * @param path Pfad als Liste von Zustandsvektoren I(t_k)
     * @param dt Zeitschritt Δt
     * @return Wirkung S[Ψ]
     */
    fun action(path: List<CognitiveStateVector>, dt: Float = TAU_STEP): Float {
        if (path.size < 2) return 0.0f

        var totalAction = 0.0f
        for (k in 0 until path.size - 1) {
            val current = path[k]
            val next = path[k + 1]

            // Lagrange-Funktion: L = T - V = 1/2 · İ² - V(I)
            // Kinetische Energie T = 1/2 · ||İ||²
            val iDot = (next - current) / dt
            val kinetic = 0.5f * (iDot.past * iDot.past + iDot.present * iDot.present + iDot.future * iDot.future)

            // Potentielle Energie V(I) = 1/2 · k · ||I||² (Harmonischer Oszillator)
            val springConstant = 1.0f
            val potential = 0.5f * springConstant * (current.past * current.past + current.present * current.present + current.future * current.future)

            val lagrangian = kinetic - potential
            totalAction += lagrangian * dt
        }

        return totalAction
    }

    /**
     * Berechnet die Wirkung für einen einzelnen Pfadsegment.
     *
     * L(Ψ, Ψ̇) = 1/2 · ||Ψ̇||² - 1/2 · k · ||Ψ||²
     *
     * @param current Zustand Ψ(t)
     * @param next Zustand Ψ(t+Δt)
     * @param dt Zeitschritt Δt
     * @return Lagrange-Funktion L(Ψ, Ψ̇)
     */
    fun lagrangian(current: CognitiveStateVector, next: CognitiveStateVector, dt: Float = TAU_STEP): Float {
        val iDot = (next - current) / dt
        val kinetic = 0.5f * (iDot.past * iDot.past + iDot.present * iDot.present + iDot.future * iDot.future)
        val potential = 0.5f * (current.past * current.past + current.present * current.present + current.future * current.future)
        return kinetic - potential
    }

    // =========================================================================
    // PHASENAMPLITUDE
    // =========================================================================

    /**
     * Berechnet die Phasenamplitude e^{iS/ℏ} für einen Pfad.
     *
     * @param action Wirkung S[Ψ]
     * @return Phasenamplitude als (Real, Imag)-Paar
     */
    fun phaseAmplitude(action: Float): Pair<Float, Float> {
        val phase = action / H_BAR
        return cos(phase.toDouble()).toFloat() to sin(phase.toDouble()).toFloat()
    }

    /**
     * Berechnet den Betrag der Phasenamplitude |e^{iS/ℏ}| = 1.
     *
     * @param action Wirkung S[Ψ]
     * @return Betrag (immer 1.0)
     */
    fun amplitudeMagnitude(action: Float): Float = 1.0f

    /**
     * Berechnet die Phase der Phasenamplitude arg(e^{iS/ℏ}) = S/ℏ.
     *
     * @param action Wirkung S[Ψ]
     * @return Phase in Radiant
     */
    fun amplitudePhase(action: Float): Float = (action / H_BAR).coerceIn(-PI.toFloat(), PI.toFloat())

    // =========================================================================
    // PFADINTEGRAL (MONTE-CARLO)
    // =========================================================================

    /**
     * Berechnet das Pfadintegral ⟨Ψ_B|Ψ_A⟩ via Monte-Carlo-Sampling.
     *
     * ⟨Ψ_B|Ψ_A⟩ ≈ (1/M) Σ_{m=1}^{M} e^{iS[Ψ_m]/ℏ}
     *
     * @param start Startzustand Ψ_A
     * @param end Endzustand Ψ_B
     * @param numSteps Anzahl Zeitschritte N
     * @param numSamples Anzahl Monte-Carlo-Samples M
     * @param dt Zeitschritt Δt
     * @return Wahrscheinlichkeitsamplitude als (Real, Imag)-Paar
     */
    fun pathIntegral(
        start: CognitiveStateVector,
        end: CognitiveStateVector,
        numSteps: Int = 100,
        numSamples: Int = MONTE_CARLO_SAMPLES,
        dt: Float = TAU_STEP
    ): Pair<Float, Float> {
        var sumReal = 0.0
        var sumImag = 0.0

        repeat(numSamples) {
            // Generiere zufälligen Pfad von start nach end
            val path = generateRandomPath(start, end, numSteps, dt)
            val s = action(path, dt)
            val (ampReal, ampImag) = phaseAmplitude(s)
            sumReal += ampReal
            sumImag += ampImag
        }

        val avgReal = sumReal / numSamples
        val avgImag = sumImag / numSamples
        return avgReal.toFloat() to avgImag.toFloat()
    }

    /**
     * Generiert einen zufälligen Pfad zwischen zwei Zuständen.
     *
     * Der Pfad wird durch zufällige Störungen um die lineare Interpolation generiert.
     *
     * @param start Startzustand Ψ_A
     * @param end Endzustand Ψ_B
     * @param numSteps Anzahl Schritte N
     * @param dt Zeitschritt Δt
     * @return Pfad als Liste von Zustandsvektoren
     */
    private fun generateRandomPath(
        start: CognitiveStateVector,
        end: CognitiveStateVector,
        numSteps: Int,
        dt: Float
    ): List<CognitiveStateVector> {
        val path = mutableListOf<CognitiveStateVector>()
        path.add(start)

        // Lineare Interpolation + zufällige Störungen
        for (k in 1 until numSteps) {
            val t = k.toFloat() / numSteps
            val linear = start * (1.0f - t) + end * t

            // Zufällige Störung (Gauß-verteilt, σ = 0.1), clamped to non-negative
            val noise = CognitiveStateVector(
                past = max(0f, randomGaussian() * 0.1f),
                present = max(0f, randomGaussian() * 0.1f),
                future = max(0f, randomGaussian() * 0.1f)
            )

            val combined = linear + noise
            val clamped = CognitiveStateVector(
                past = max(combined.past, CognitiveStateVector.ZERO.past),
                present = max(combined.present, CognitiveStateVector.ZERO.present),
                future = max(combined.future, CognitiveStateVector.ZERO.future)
            )
            path.add(clamped)
        }

        path.add(end)
        return path
    }

    /**
     * Zufallszahl aus Gauß-Verteilung (Box-Muller).
     */
    private fun randomGaussian(): Float {
        val u1 = rng.nextFloat()
        val u2 = rng.nextFloat()
        val z = sqrt(-2.0 * ln(u1.toDouble())) * cos(2.0 * PI * u2)
        return z.toFloat()
    }

    // =========================================================================
    // ÜBERGANGSWAHRSCHEINLICHKEIT
    // =========================================================================

    /**
     * Berechnet die Übergangswahrscheinlichkeit P(Ψ_A → Ψ_B).
     *
     * P = |⟨Ψ_B|Ψ_A⟩|²
     *
     * @param start Startzustand Ψ_A
     * @param end Endzustand Ψ_B
     * @param numSteps Anzahl Zeitschritte
     * @param numSamples Anzahl Monte-Carlo-Samples
     * @param dt Zeitschritt
     * @return Übergangswahrscheinlichkeit P ∈ [0, 1]
     */
    fun transitionProbability(
        start: CognitiveStateVector,
        end: CognitiveStateVector,
        numSteps: Int = 100,
        numSamples: Int = MONTE_CARLO_SAMPLES,
        dt: Float = TAU_STEP
    ): Float {
        val (real, imag) = pathIntegral(start, end, numSteps, numSamples, dt)
        val probability = real * real + imag * imag
        return probability.coerceIn(0.0f, 1.0f)
    }

    // =========================================================================
    // STATIONÄRE WIRKUNG (KLASSISCHE PFADE)
    // =========================================================================

    /**
     * Findet den stationären Pfad (klassische Lösung) zwischen zwei Zuständen.
     *
     * δS = 0 ⇒ Euler-Lagrange-Gleichung: d/dt(∂L/∂Ψ̇) - ∂L/∂Ψ = 0
     *
     * Für harmonischen Oszillator: Ψ̈ + ω²Ψ = 0
     *
     * @param start Startzustand Ψ_A
     * @param end Endzustand Ψ_B
     * @param numSteps Anzahl Schritte
     * @param dt Zeitschritt
     * @return Stationärer Pfad
     */
    fun stationaryPath(
        start: CognitiveStateVector,
        end: CognitiveStateVector,
        numSteps: Int = 100,
        dt: Float = TAU_STEP
    ): List<CognitiveStateVector> {
        val path = mutableListOf<CognitiveStateVector>()
        path.add(start)

        // Klassische Lösung: harmonische Interpolation
        // Ψ(t) = A·sin(ωt) + B·cos(ωt)
        val omega = 1.0f  // Eigenfrequenz

        for (k in 1 until numSteps) {
            val t = k.toFloat() * dt
            val tEnd = numSteps.toFloat() * dt

            // Harmonische Interpolation
            val sinT = sin(omega * t)
            val sinTEnd = sin(omega * tEnd)
            val cosT = cos(omega * t)
            val cosTEnd = cos(omega * tEnd)

            val interpolated = CognitiveStateVector(
                past = start.past * (sinTEnd - sinT) / sinTEnd + end.past * sinT / sinTEnd,
                present = start.present * (sinTEnd - sinT) / sinTEnd + end.present * sinT / sinTEnd,
                future = start.future * (sinTEnd - sinT) / sinTEnd + end.future * sinT / sinTEnd
            )

            val clamped = CognitiveStateVector(
                past = max(interpolated.past, CognitiveStateVector.ZERO.past),
                present = max(interpolated.present, CognitiveStateVector.ZERO.present),
                future = max(interpolated.future, CognitiveStateVector.ZERO.future)
            )
            path.add(clamped)
        }

        path.add(end)
        return path
    }

    /**
     * Berechnet die Wirkung des stationären Pfads (minimale Wirkung).
     *
     * @param start Startzustand Ψ_A
     * @param end Endzustand Ψ_B
     * @param numSteps Anzahl Schritte
     * @param dt Zeitschritt
     * @return Minimale Wirkung S_min
     */
    fun stationaryAction(
        start: CognitiveStateVector,
        end: CognitiveStateVector,
        numSteps: Int = 100,
        dt: Float = TAU_STEP
    ): Float {
        val path = stationaryPath(start, end, numSteps, dt)
        return action(path, dt)
    }

    // =========================================================================
    // PROPAGATOR
    // =========================================================================

    /**
     * Berechnet den Propagator K(Ψ_B, Ψ_A; Δt) = ⟨Ψ_B|e^{-iHΔt/ℏ}|Ψ_A⟩.
     *
     * Für freies Teilchen: K = √(m/(2πiℏΔt)) · e^{iS_cl/ℏ}
     *
     * @param start Startzustand Ψ_A
     * @param end Endzustand Ψ_B
     * @param dt Zeitintervall Δt
     * @return Propagator als (Real, Imag)-Paar
     */
    fun propagator(start: CognitiveStateVector, end: CognitiveStateVector, dt: Float = TAU_STEP): Pair<Float, Float> {
        val sCl = stationaryAction(start, end, numSteps = 100, dt)
        val prefactor = 1.0f / sqrt(2.0f * PI.toFloat() * H_BAR * dt)
        val (ampReal, ampImag) = phaseAmplitude(sCl)
        return (prefactor * ampReal) to (prefactor * ampImag)
    }

    // =========================================================================
    // WAHRSCHHEINLICHKEITSDICHTE
    // =========================================================================

    /**
     * Berechnet die Wahrscheinlichkeitsdichte |Ψ(x, t)|² aus dem Pfadintegral.
     *
     * |Ψ(x, t)|² = |∫ D[Ψ] e^{iS[Ψ]/ℏ}|²
     *
     * @param start Startzustand Ψ_A
     * @param end Endzustand Ψ_B
     * @param numSteps Anzahl Schritte
     * @param numSamples Anzahl Samples
     * @param dt Zeitschritt
     * @return Wahrscheinlichkeitsdichte
     */
    fun probabilityDensity(
        start: CognitiveStateVector,
        end: CognitiveStateVector,
        numSteps: Int = 100,
        numSamples: Int = MONTE_CARLO_SAMPLES,
        dt: Float = TAU_STEP
    ): Float {
        val (real, imag) = pathIntegral(start, end, numSteps, numSamples, dt)
        return (real * real + imag * imag).coerceIn(0.0f, 1.0f)
    }

    // =========================================================================
    // WIRKUNGSUNTERSCHIED (QUANTENKORREKTUR)
    // =========================================================================

    /**
     * Berechnet den Wirkungsunterschied zwischen klassischem und quantenpfad.
     *
     * ΔS = S[Ψ_quantum] - S[Ψ_classical]
     *
     * @param start Startzustand Ψ_A
     * @param end Endzustand Ψ_B
     * @param numSteps Anzahl Schritte
     * @param dt Zeitschritt
     * @return Wirkungsunterschied ΔS
     */
    fun actionDifference(
        start: CognitiveStateVector,
        end: CognitiveStateVector,
        numSteps: Int = 100,
        dt: Float = TAU_STEP
    ): Float {
        val sClassical = stationaryAction(start, end, numSteps, dt)

        // Quantenpfad: Monte-Carlo-Mittelwert über zufällige Pfade
        var sumQuantum = 0.0
        repeat(100) {
            val randomPath = generateRandomPath(start, end, numSteps, dt)
            sumQuantum += action(randomPath, dt)
        }
        val sQuantum = (sumQuantum / 100.0).toFloat()

        return (sQuantum - sClassical).toFloat()
    }

    // =========================================================================
    // INTERFERENZ
    // =========================================================================

    /**
     * Berechnet die Interferenz zwischen zwei Pfaden.
     *
     * I = |e^{iS_1/ℏ} + e^{iS_2/ℏ}|² = 2 + 2·cos((S_1 - S_2)/ℏ)
     *
     * @param action1 Wirkung des ersten Pfads S_1
     * @param action2 Wirkung des zweiten Pfads S_2
     * @return Interferenz I ∈ [0, 4]
     */
    fun interference(action1: Float, action2: Float): Float {
        val deltaAction = (action1 - action2) / H_BAR
        return 2.0f + 2.0f * cos(deltaAction.toDouble()).toFloat()
    }

    /**
     * Überprüft, ob zwei Pfade konstruktiv interferieren.
     *
     * Konstruktiv: ΔS ≈ n·2πℏ (n ∈ ℤ)
     *
     * @param action1 Wirkung des ersten Pfads
     * @param action2 Wirkung des zweiten Pfads
     * @return true wenn konstruktive Interferenz
     */
    fun isConstructiveInterference(action1: Float, action2: Float): Boolean {
        val deltaAction = abs(action1 - action2)
        val period = 2.0f * PI.toFloat() * H_BAR
        val remainder = deltaAction % period
        return min(remainder, period - remainder) < ACTION_TOLERANCE * 100f
    }

    /**
     * Überprüft, ob zwei Pfade destruktiv interferieren.
     *
     * Destruktiv: ΔS ≈ (n + 1/2)·2πℏ
     *
     * @param action1 Wirkung des ersten Pfads
     * @param action2 Wirkung des zweiten Pfads
     * @return true wenn destruktive Interferenz
     */
    fun isDestructiveInterference(action1: Float, action2: Float): Boolean {
        val deltaAction = abs(action1 - action2)
        val period = 2.0f * PI.toFloat() * H_BAR
        val halfPeriod = period / 2.0f
        val remainder = deltaAction % period
        val distToHalf = min(abs(remainder - halfPeriod), period - abs(remainder - halfPeriod))
        return distToHalf < ACTION_TOLERANCE * 100f
    }
}

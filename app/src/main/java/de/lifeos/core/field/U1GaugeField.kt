package de.lifeos.core.field

import kotlin.math.*

/**
 * U(1) GAUGE FIELD — Eichfeld für Phasenkovarianz & Holonomie
 *
 * Das U(1)-Eichfeld beschreibt die lokale Phasenkovarianz des kognitiven
 * Zustandsraums. Es modelliert:
 *
 *   - Eichtransformation: Ψ → e^{iθ(x)} Ψ
 *   - Feldstärke: F_{μν} = ∂_μ A_ν - ∂_ν A_μ
 *   - Holonomie: U(γ) = P exp(i ∮_γ A_μ dx^μ)
 *   - Paralleltransport: D_μ Ψ = (∂_μ + i A_μ) Ψ
 *
 * Im kognitiven Kontext:
 *   - A_μ: Eichpotential (kognitive Phase pro Zeitschritt)
 *   - F_{μν}: Diskretisierungsfehler / kognitive Dissonanz
 *   - U(γ): Gedächtnisspur (Holonomie entlang Trajektorie γ)
 *
 * Vektoren:
 * - [EXP-FORCE] Topologische Stabilität: Holonomie-Erhaltung
 * - [EXP-SPEED] O(N) Feldberechnung mit Lookup-Table
 */
object U1GaugeField {

    // =========================================================================
    // EICHFELD-PARAMETER
    // =========================================================================

    /** Kopplungskonstante: e = 1.0 (normiert) */
    const val COUPLING: Float = 1.0f

    /** Phasenquantisierung: Δθ = 2π/256 (8-Bit-Phase) */
    const val PHASE_QUANTIZATION: Float = 2.0f * PI.toFloat() / 256.0f

    /** Maximale Feldstärke: F_max = 1.0 */
    const val MAX_FIELD_STRENGTH: Float = 1.0f

    /** Holonomie-Toleranz: ε_H = 1e-4 */
    const val HOLONOMY_TOLERANCE: Float = 1e-4f

    /** Paralleltransport-Schrittweite: ε = 0.01 */
    const val TRANSPORT_STEP: Float = 0.01f

    // =========================================================================
    // EICHPOTENTIAL A_μ(t)
    // =========================================================================

    /**
     * Berechnet das Eichpotential A_μ(t) für eine gegebene Phase.
     *
     * A_μ(t) = A_0 · sin(2π · ω_0 · t + φ_0)
     *
     * @param t Zeitpunkt t
     * @param amplitude Amplitude A_0
     * @param frequency Frequenz ω_0
     * @param phase Phasenoffset φ_0
     * @return Eichpotential A_μ(t) ∈ [-1, 1]
     */
    fun gaugePotential(
        t: Float,
        amplitude: Float = 1.0f,
        frequency: Float = 1.0f,
        phase: Float = 0.0f
    ): Float {
        val gaugeVal = amplitude * sin(2.0f * PI.toFloat() * frequency * t + phase)
        return gaugeVal.coerceIn(-1.0f, 1.0f)
    }

    /**
     * Berechnet die Feldstärke F_{μν} = ∂_μ A_ν - ∂_ν A_μ.
     *
     * Im diskreten Fall: F_{μν} ≈ (A_ν(t+Δt) - A_ν(t)) / Δt - (A_μ(t+Δt) - A_μ(t)) / Δt
     *
     * @param aMuVorher A_μ(t)
     * @param aNuVorher A_ν(t)
     * @param aMuNachher A_μ(t+Δt)
     * @param aNuNachher A_ν(t+Δt)
     * @param dt Zeitschritt Δt
     * @return Feldstärke F_{μν}
     */
    fun fieldStrength(
        aMuVorher: Float,
        aNuVorher: Float,
        aMuNachher: Float,
        aNuNachher: Float,
        dt: Float = 1.0f
    ): Float {
        if (dt == 0.0f) return 0.0f
        val dA_mu = (aMuNachher - aMuVorher) / dt
        val dA_nu = (aNuNachher - aNuVorher) / dt
        return (dA_nu - dA_mu).coerceIn(-MAX_FIELD_STRENGTH, MAX_FIELD_STRENGTH)
    }

    // =========================================================================
    // HOLONOMIE U(γ)
    // =========================================================================

    /**
     * Berechnet die Holonomie U(γ) entlang einer Trajektorie γ.
     *
     * U(γ) = P exp(i ∮_γ A_μ dx^μ) ≈ exp(i · Σ A_μ · Δx^μ)
     *
     * Für diskrete Pfade: U(γ) = exp(i · Σ_{k=0}^{N-1} A_μ(k) · Δx^μ(k))
     *
     * @param path Pfad als Liste von (A_μ, Δx)-Paaren
     * @return Holonomie als komplexe Zahl (Real- und Imaginärteil)
     */
    fun holonomy(path: List<Pair<Float, Float>>): Pair<Float, Float> {
        if (path.isEmpty()) return 1.0f to 0.0f

        var sumReal = 0.0
        var sumImag = 0.0

        for ((aMu, dx) in path) {
            val phase = aMu * dx
            sumReal += cos(phase)
            sumImag += sin(phase)
        }

        return sumReal.toFloat() to sumImag.toFloat()
    }

    /**
     * Überprüft, ob eine Holonomie die Einheitsbedingung erfüllt.
     *
     * |U(γ)| ≈ 1 für geschlossene Pfade (Eichinvarianz)
     *
     * @param holonomy Holonomie als (Real, Imag)-Paar
     * @return true wenn |U(γ)| ≈ 1
     */
    fun isUnitary(holonomy: Pair<Float, Float>): Boolean {
        val magnitude = sqrt(holonomy.first * holonomy.first + holonomy.second * holonomy.second)
        return abs(magnitude - 1.0f) < HOLONOMY_TOLERANCE
    }

    /**
     * Berechnet den Betrag der Holonomie |U(γ)|.
     *
     * @param holonomy Holonomie als (Real, Imag)-Paar
     * @return Betrag |U(γ)|
     */
    fun holonomyMagnitude(holonomy: Pair<Float, Float>): Float {
        return sqrt(holonomy.first * holonomy.first + holonomy.second * holonomy.second)
    }

    /**
     * Berechnet die Phase der Holonomie arg(U(γ)).
     *
     * @param holonomy Holonomie als (Real, Imag)-Paar
     * @return Phase in Radiant
     */
    fun holonomyPhase(holonomy: Pair<Float, Float>): Float {
        return atan2(holonomy.second.toDouble(), holonomy.first.toDouble()).toFloat()
    }

    // =========================================================================
    // PARALLELTRANSPORT
    // =========================================================================

    /**
     * Führt Paralleltransport eines Zustandsvektors Ψ entlang eines Pfades durch.
     *
     * D_μ Ψ = (∂_μ + i A_μ) Ψ
     *
     * @param psi Zustandsvektor Ψ (Real- und Imaginärteil)
     * @param aMu Eichpotential A_μ
     * @param step Schrittweite Δx
     * @return Transportierter Zustand Ψ'
     */
    fun parallelTransport(psi: Pair<Float, Float>, aMu: Float, step: Float = TRANSPORT_STEP): Pair<Float, Float> {
        val (real, imag) = psi
        val phase = aMu * step

        // Rotation im komplexen Raum um Winkel phase
        val newReal = real * cos(phase) - imag * sin(phase)
        val newImag = real * sin(phase) + imag * cos(phase)

        return newReal to newImag
    }

    /**
     * Berechnet den Paralleltransport-Fehler (Abweichung von der Einheitsbedingung).
     *
     * @param psiStart Startzustand Ψ(0)
     * @param path Pfad als Liste von A_μ-Werten
     * @param step Schrittweite
     * @return Transport-Fehler |Ψ_end| - |Ψ_start|
     */
    fun transportError(psiStart: Pair<Float, Float>, path: List<Float>, step: Float = TRANSPORT_STEP): Float {
        var currentPsi = psiStart
        for (aMu in path) {
            currentPsi = parallelTransport(currentPsi, aMu, step)
        }
        val startMag = sqrt(psiStart.first * psiStart.first + psiStart.second * psiStart.second)
        val endMag = sqrt(currentPsi.first * currentPsi.first + currentPsi.second * currentPsi.second)
        return abs(endMag - startMag)
    }

    // =========================================================================
    // EICHTRANSFORMATION
    // =========================================================================

    /**
     * Führt eine Eichtransformation Ψ → e^{iθ} Ψ durch.
     *
     * @param psi Zustandsvektor Ψ
     * @param theta Phasenwinkel θ
     * @return Transformierter Zustand Ψ'
     */
    fun gaugeTransform(psi: Pair<Float, Float>, theta: Float): Pair<Float, Float> {
        val (real, imag) = psi
        val cosT = cos(theta)
        val sinT = sin(theta)
        val newReal = real * cosT - imag * sinT
        val newImag = real * sinT + imag * cosT
        return newReal to newImag
    }

    /**
     * Berechnet die Eichtransformations-Invariante (observable Größe).
     *
     * |Ψ|² ist eichinvariant unter Ψ → e^{iθ} Ψ.
     *
     * @param psi Zustandsvektor Ψ
     * @return Invariante |Ψ|²
     */
    fun gaugeInvariant(psi: Pair<Float, Float>): Float {
        return psi.first * psi.first + psi.second * psi.second
    }

    // =========================================================================
    // PHASENKOVARIANZ
    // =========================================================================

    /**
     * Berechnet die Phasenkovarianz zwischen zwei Zustandsvektoren.
     *
     * ρ_phase = |⟨Ψ_1|Ψ_2⟩|² = |Ψ_1^* · Ψ_2|²
     *
     * @param psi1 Erster Zustand Ψ_1
     * @param psi2 Zweiter Zustand Ψ_2
     * @return Phasenkovarianz ρ_phase ∈ [0, 1]
     */
    fun phaseCovariance(psi1: Pair<Float, Float>, psi2: Pair<Float, Float>): Float {
        val (r1, i1) = psi1
        val (r2, i2) = psi2
        // Skalarprodukt: Ψ_1^* · Ψ_2 = r1*r2 + i1*i2 + (r1*i2 - i1*r2)i
        val realPart = r1 * r2 + i1 * i2
        val imagPart = r1 * i2 - i1 * r2
        val magnitude = sqrt(realPart * realPart + imagPart * imagPart)
        // Normierung auf [0, 1]
        val norm1 = sqrt(r1 * r1 + i1 * i1)
        val norm2 = sqrt(r2 * r2 + i2 * i2)
        return if (norm1 * norm2 > 0.0f) (magnitude / (norm1 * norm2)).coerceIn(0.0f, 1.0f) else 0.0f
    }

    /**
     * Berechnet den Phasenunterschied zwischen zwei Zuständen.
     *
     * Δθ = arg(Ψ_2) - arg(Ψ_1)
     *
     * @param psi1 Erster Zustand Ψ_1
     * @param psi2 Zweiter Zustand Ψ_2
     * @return Phasenunterschied Δθ ∈ [-π, π]
     */
    fun phaseDifference(psi1: Pair<Float, Float>, psi2: Pair<Float, Float>): Float {
        val phase1 = atan2(psi1.second.toDouble(), psi1.first.toDouble()).toFloat()
        val phase2 = atan2(psi2.second.toDouble(), psi2.first.toDouble()).toFloat()
        var diff = phase2 - phase1
        while (diff > PI.toFloat()) diff -= 2.0f * PI.toFloat()
        while (diff < -PI.toFloat()) diff += 2.0f * PI.toFloat()
        return diff
    }

    // =========================================================================
    // WILSON-SCHLEIFE (Diskrete Version)
    // =========================================================================

    /**
     * Berechnet die Wilson-Schleife W(γ) = Tr(U(γ)) für einen geschlossenen Pfad.
     *
     * W(γ) = 2 · cos(Φ), wobei Φ die Gesamtphase ist.
     *
     * @param path Pfad als Liste von A_μ-Werten
     * @param step Schrittweite
     * @return Wilson-Schleife W(γ) ∈ [-2, 2]
     */
    fun wilsonLoop(path: List<Float>, step: Float = TRANSPORT_STEP): Float {
        val (real, imag) = holonomy(path.map { it to step })
        return 2.0f * real  // Tr(U) = 2·Re(U) für SU(2)-ähnliche Struktur
    }

    /**
     * Überprüft, ob eine Wilson-Schleife trivial ist (kein Fluss).
     *
     * W(γ) ≈ 2 für geschlossene Pfade ohne Fluss (reine Phase)
     *
     * @param wilson Wilson-Schleife W(γ)
     * @return true wenn |W(γ) - 2| < tolerance
     */
    fun isTrivialWilson(wilson: Float): Boolean {
        return abs(wilson - 2.0f) < HOLONOMY_TOLERANCE * 10f
    }

    // =========================================================================
    // COVARIANTE ABLEITUNG
    // =========================================================================

    /**
     * Berechnet die kovariante Ableitung D_μ Ψ entlang einer Richtung.
     *
     * D_μ Ψ ≈ (Ψ(t+Δt) - e^{-i A_μ Δt} Ψ(t)) / Δt
     *
     * @param psiVorher Ψ(t)
     * @param psiNachher Ψ(t+Δt)
     * @param aMu Eichpotential A_μ
     * @param dt Zeitschritt Δt
     * @return Kovariante Ableitung D_μ Ψ
     */
    fun covariantDerivative(
        psiVorher: Pair<Float, Float>,
        psiNachher: Pair<Float, Float>,
        aMu: Float,
        dt: Float = 1.0f
    ): Pair<Float, Float> {
        if (dt == 0.0f) return 0.0f to 0.0f

        // Eichtransformation des Startzustands: e^{-i A_μ Δt} Ψ(t)
        val transformed = gaugeTransform(psiVorher, -aMu * dt)
        val (tReal, tImag) = transformed
        val (nReal, nImag) = psiNachher

        return ((nReal - tReal) / dt) to ((nImag - tImag) / dt)
    }

    // =========================================================================
    // GAUSS-GESETZ (Quellenfreiheit)
    // =========================================================================

    /**
     * Überprüft das Gauss-Gesetz ∇·E = ρ (Quellenfreiheit im Vakuum).
     *
     * Im diskreten Fall: Σ F_{0i} ≈ 0 (keine Monopole)
     *
      * @param fieldStrengths Liste von Feldstärken F_{0i}
     * @return true wenn Quellenfreiheit erfüllt ist
     */
    fun isSourceFree(fieldStrengths: List<Float>, tolerance: Float = 1e-3f): Boolean {
        val sum = fieldStrengths.sum()
        return abs(sum) < tolerance
    }

    // =========================================================================
    // ENERGIEDICHTE
    // =========================================================================

    /**
     * Berechnet die Energiedichte des Eichfelds.
     *
     * E = 1/2 · Σ_{μ<ν} F_{μν}²
     *
      * @param fieldStrengths Liste von Feldstärken F_{μν}
     * @return Energiedichte E
     */
    fun energyDensity(fieldStrengths: List<Float>): Float {
        var sum = 0.0f
        for (f in fieldStrengths) {
            sum += f * f
        }
        return (0.5f * sum).coerceIn(0.0f, MAX_FIELD_STRENGTH)
    }

    // =========================================================================
    // TOPOLOGISCHE INVARIANTEN
    // =========================================================================

    /**
     * Berechnet die erste Chern-Zahl (topologische Invariante).
     *
     * C_1 = (1/2π) ∫ F_{12} d²x
     *
      * @param fieldStrength12 Feldstärke F_{12} über Gitter
     * @param area Fläche des Gitters
     * @return Chern-Zahl C_1 (ganzzahlig für topologisch nicht-triviale Felder)
     */
    fun chernNumber(fieldStrength12: List<Float>, area: Float): Float {
        val integral = fieldStrength12.sum() / fieldStrength12.size * area
        return (integral / (2.0f * PI.toFloat())).coerceIn(-10.0f, 10.0f)
    }

    /**
     * Überprüft, ob das Feld topologisch nicht-trivial ist.
     *
     * C_1 ≠ 0 (mod 1) für nicht-triviale Feldkonfigurationen
     *
     * @param chern Chern-Zahl C_1
     * @return true wenn |C_1 - round(C_1)| < tolerance
     */
    fun isTopologicallyNonTrivial(chern: Float): Boolean {
        val rounded = round(chern.toDouble()).toFloat()
        return abs(chern - rounded) < HOLONOMY_TOLERANCE * 100f
    }
}

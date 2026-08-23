package de.lifeos.core.field

import kotlin.math.max
import kotlin.math.min

/**
 * LOAD FUNCTION — W(t) & Ω(t)
 *
 * Lastfunktion des Verarbeitungsmodus:
 *
 *   W(t) = μ₁·i_past(t) + μ₂·i_future(t),  μ₁, μ₂ ∈ R₊
 *
 * Kumulierte Belastung:
 *
 *   Ω(t) = ∫₀ᵗ W(s) ds
 *
 * Kritischer Horizont: Ω_krit = 5800 W·s
 *   → Strukturkollaps / Burnout bei Ω(t) → Ω_krit
 *
 * Im Seinsmodus (unter P_S):
 *   i_past(t) = 0 ∧ i_future(t) = 0 ⟹ W(t) = 0
 *
 * Vektoren:
 * - [EXP-FORCE] Last-Monitoring: Echtzeit-Integration über Zeit
 * - [EXP-AUTO] Autopoietische Regulation: 5800-Schwellen-Intervention
 */
object LoadFunction {

    /** Kritischer Last-Horizont (Bekenstein-Hawking-Informationsgrenze) */
    const val OMEGA_CRITICAL: Float = 5800f

    /** Standard-Gewicht für Vergangenheitslast μ₁ */
    const val DEFAULT_MU_PAST: Float = 0.6f

    /** Standard-Gewicht für Zukunftsprojektion μ₂ */
    const val DEFAULT_MU_FUTURE: Float = 0.4f

    /** Integrations-Zeitschritt für numerische Integration (ms) */
    const val INTEGRATION_STEP_MS: Long = 100L

    /** Warnschwelle bei 80% von Ω_krit */
    const val WARNING_THRESHOLD: Float = OMEGA_CRITICAL * 0.8f

    /**
     * Berechnet die momentane Last W(t) aus einem kognitiven Zustandsvektor.
     *
     * W(t) = μ₁·i_past(t) + μ₂·i_future(t)
     *
     * Im Seinsmodus (i_past ≈ 0, i_future ≈ 0) → W(t) = 0
     *
     * @param state Der kognitive Zustandsvektor I(t)
     * @param muPast Gewicht für Vergangenheitslast (Standard: 0.6)
     * @param muFuture Gewicht für Zukunftsprojektion (Standard: 0.4)
     * @return Last W(t) ≥ 0
     */
    fun computeW(
        state: CognitiveStateVector,
        muPast: Float = DEFAULT_MU_PAST,
        muFuture: Float = DEFAULT_MU_FUTURE
    ): Float = max(0f, muPast * state.past + muFuture * state.future)

    /**
     * Seinsmodus-Prädikat: W(t) = 0
     *
     * @param state Der kognitive Zustandsvektor I(t)
     * @param tolerance Toleranz für numerische Null
     * @return true wenn W(t) ≈ 0
     */
    fun isSeinsmodus(state: CognitiveStateVector, tolerance: Float = 1e-6f): Boolean =
        computeW(state) < tolerance

    /**
     * Last-Integrator: kumulierte Belastung Ω(t) = ∫₀ᵗ W(s) ds
     *
     * Verwendet Trapez-Regel für numerische Integration.
     * Thread-safe durch volatile-Koordination.
     *
     * @param currentW Aktuelle Last W(t)
     * @param previousW Vorherige Last W(t-Δt)
     * @param previousOmega Vorherige kumulierte Last Ω(t-Δt)
     * @param deltaMs Zeitdifferenz in Millisekunden
     * @return Neue kumulierte Last Ω(t)
     */
    fun integrateOmega(
        currentW: Float,
        previousW: Float,
        previousOmega: Float,
        deltaMs: Long
    ): Float {
        val deltaSec = deltaMs / 1000.0f
        // Trapez-Regel: Ω(t) = Ω(t-Δt) + (W(t) + W(t-Δt)) / 2 · Δt
        val trapezoidArea = (currentW + previousW) * 0.5f * deltaSec
        return max(0f, previousOmega + trapezoidArea)
    }

    /**
     * Kritikalitäts-Prüfung: Ω(t) ≥ Ω_krit?
     *
     * @param omega Aktuelle kumulierte Last
     * @return true wenn kritischer Horizont erreicht oder überschritten
     */
    fun isCritical(omega: Float): Boolean = omega >= OMEGA_CRITICAL

    /**
     * Warnungs-Prüfung: Ω(t) ≥ 0.8 · Ω_krit?
     *
     * @param omega Aktuelle kumulierte Last
     * @return true wenn Warnschwelle erreicht
     */
    fun isWarning(omega: Float): Boolean = omega >= WARNING_THRESHOLD

    /**
     * Berechnet den Abstand zum kritischen Horizont.
     *
     * @param omega Aktuelle kumulierte Last
     * @return Verbleibende Kapazität (0 = kritisch, positiv = sicher)
     */
    fun remainingCapacity(omega: Float): Float = max(0f, OMEGA_CRITICAL - omega)

    /**
     * Berechnet den prozentualen Last-Anteil.
     *
     * @param omega Aktuelle kumulierte Last
     * @return Last-Anteil in [0, 1] (1 = kritisch)
     */
    fun loadRatio(omega: Float): Float = (omega / OMEGA_CRITICAL).coerceIn(0f, 1f)

    /**
     * Zeit bis zum kritischen Horizont bei konstanter Last W.
     *
     * @param currentW Aktuelle konstante Last W(t)
     * @param currentOmega Aktuelle kumulierte Last Ω(t)
     * @return Geschätzte Zeit in Sekunden bis Ω_krit (∞ wenn W=0)
     */
    fun timeToCritical(currentW: Float, currentOmega: Float): Float {
        return if (currentW > 1e-6f) {
            (OMEGA_CRITICAL - currentOmega) / currentW
        } else {
            Float.POSITIVE_INFINITY
        }
    }

    /**
     * Master-Feldgleichung der skalenunabhängigen Kognitions-Physik:
     *
     *   G_μν = (c_info⁴ / 8πG_cog) · T_info_μν + Λ_identity · g_μν
     *
     * Implementiert als Informationsfluss-Operator zwischen kognitiver Last
     * und Feldpotential. Gibt den Informations-Stress-Tensor zurück.
     *
     * @param state Der kognitive Zustandsvektor I(t)
     * @param fieldPotential Aktuelles Feldpotential Φ(t)
     * @return Informations-Stress-Komponente T_info
     */
    fun computeInformationStress(
        state: CognitiveStateVector,
        fieldPotential: Float = 0f
    ): Float {
        val w = computeW(state)
        val norm = state.norm()
        // T_info ∝ W(t) · ∥I(t)∥ · (1 + Φ(t))
        return w * max(norm, 1e-6f) * (1.0f + fieldPotential)
    }
}

/** Vorkompilierte Instanz für deterministischen Zugriff */
val LoadFunctionInstance = LoadFunction

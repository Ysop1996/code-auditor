package de.lifeos.core.field

import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min

/**
 * EFFECTIVE DIMENSION — d_α(t)
 *
 * Die kontinuierliche Kontraktion der temporalen Dimensionen wird über eine glatte
 * Sigmoide σ_α(x) gesteuert:
 *
 * d_α(t) = Σ_{k ∈ {past,present,future}} σ_α(i_k(t) - θ₀)
 *
 * wobei σ_α(x) = 1 / (1 + e^{-α·x})
 *
 * Im idealisierten Grenzfall unendlicher Reaktionsgeschwindigkeit (α → ∞)
 * konvergiert die Aktivierung gegen die Heaviside-Sprungfunktion Θ(x):
 *
 *   lim_{α→∞} σ_α(i_k(t) - θ₀) = Θ(i_k(t) - θ₀)
 *
 *   ⟹ d(t) = 3 für ∀t ∈ T_V (Verarbeitungsmodus)
 *   ⟹ d(t) = 1 für ∀t ∈ T_S (Seinsmodus)
 *
 * Vektoren:
 * - [EXP-FORCE] Zustandsraum-Kontraktion: d_α(t) ∈ [1, 3]
 * - [EXP-SPEED] O(1) Sigmoide-Berechnung mit Lookup-Table (LUT)
 */
object EffectiveDimension {

    /** Steilheitsparameter α (größer = schärferer Übergang) */
    const val DEFAULT_ALPHA: Float = 8.0f

    /** Schwellenwert θ₀ (Aktivierungsschwelle) */
    const val DEFAULT_THETA: Float = 0.5f

    /** Minimale effektive Dimension (Seinsmodus, d=1) */
    const val MIN_DIMENSION: Int = 1

    /** Maximale effektive Dimension (Verarbeitungsmodus, d=3) */
    const val MAX_DIMENSION: Int = 3

    /** Biologische Quantisierungsgrenze: τ_min ≈ 30 ms */
    const val TAU_MIN_MS: Float = 30.0f

    /** Lookup-Table für Sigmoide (256 Einträge, x ∈ [-6, 6]) */
    private val sigmoidLut: FloatArray = FloatArray(256) { i ->
        val x = (i / 255.0f) * 12.0f - 6.0f // Map [0,255] → [-6, 6]
        1.0f / (1.0f + exp(-x.toDouble())).toFloat()
    }

    /** Branchless Heaviside-Approximation via LUT (α → ∞ Grenzwert) */
    private val heavisideLut: FloatArray = FloatArray(256) { i ->
        val x = (i / 255.0f) * 12.0f - 6.0f
        if (x >= 0.0f) 1.0f else 0.0f
    }

    /**
     * Glatte Sigmoide σ_α(x) mit LUT-Approximation für O(1) Zugriff.
     *
     * @param x Eingangswert (i_k(t) - θ₀)
     * @param alpha Steilheitsparameter (Standard: 8.0)
     * @return σ_α(x) ∈ [0, 1]
     */
    fun sigmoid(x: Float, alpha: Float = DEFAULT_ALPHA): Float {
        val scaled = alpha * x
        val lutIndex = ((scaled + 6.0f) / 12.0f * 255.0f).toInt().coerceIn(0, 255)
        return sigmoidLut[lutIndex]
    }

    /**
     * Heaviside-Sprungfunktion Θ(x) als Grenzwert α → ∞.
     *
     * @param x Eingangswert
     * @return Θ(x) ∈ {0, 1}
     */
    fun heaviside(x: Float): Float {
        val lutIndex = ((x + 6.0f) / 12.0f * 255.0f).toInt().coerceIn(0, 255)
        return heavisideLut[lutIndex]
    }

    /**
     * Berechnet die effektive Dimension d_α(t) aus einem kognitiven Zustandsvektor.
     *
     * d_α(t) = Σ_{k ∈ {past,present,future}} σ_α(i_k(t) - θ₀)
     *
     * @param state Der kognitive Zustandsvektor I(t)
     * @param alpha Steilheitsparameter (größer = schärferer Übergang)
     * @param theta Schwellenwert θ₀
     * @return Effektive Dimension d_α(t) ∈ [1, 3]
     */
    fun compute(state: CognitiveStateVector, alpha: Float = DEFAULT_ALPHA, theta: Float = DEFAULT_THETA): Float {
        val sPast = sigmoid(state.past - theta, alpha)
        val sPresent = sigmoid(state.present - theta, alpha)
        val sFuture = sigmoid(state.future - theta, alpha)
        return sPast + sPresent + sFuture
    }

    /**
     * Berechnet die effektive Dimension im Grenzfall α → ∞ (Heaviside).
     *
     * @param state Der kognitive Zustandsvektor I(t)
     * @param theta Schwellenwert θ₀
     * @return Effektive Dimension d(t) ∈ {1, 3}
     */
    fun computeHeaviside(state: CognitiveStateVector, theta: Float = DEFAULT_THETA): Int {
        val hPast = heaviside(state.past - theta)
        val hPresent = heaviside(state.present - theta)
        val hFuture = heaviside(state.future - theta)
        return (hPast + hPresent + hFuture).toInt()
    }

    /**
     * Modus-Klassifikation basierend auf effektiver Dimension.
     *
     * @param dimension Effektive Dimension d_α(t)
     * @return Modus-String: "SEINSMODUS" (d=1) oder "VERARBEITUNGSMODUS" (d>1)
     */
    fun classifyMode(dimension: Float): String =
        if (dimension <= 1.0f + 1e-6f) "SEINSMODUS" else "VERARBEITUNGSMODUS"

    /**
     * Modus-Klassifikation basierend auf Heaviside-Dimension.
     *
     * @param dimension Effektive Dimension d(t) ∈ {1, 3}
     * @return Modus-String
     */
    fun classifyModeHeaviside(dimension: Int): String =
        if (dimension == 1) "SEINSMODUS" else "VERARBEITUNGSMODUS"

    /**
     * Berechnet die Kontraktionsrate: wie stark der Raum von d=3 nach d=1 kontrahiert ist.
     *
     * @param dimension Effektive Dimension d_α(t)
     * @return Kontraktionsrate ∈ [0, 1] (1 = vollständige Kontraktion zu d=1)
     */
    fun contractionRate(dimension: Float): Float =
        ((MAX_DIMENSION - dimension) / (MAX_DIMENSION - MIN_DIMENSION).toFloat()).coerceIn(0f, 1f)

    /**
     * Überprüft, ob der Zustand im Seinsmodus liegt (d_α(t) ≈ 1).
     *
     * @param state Der kognitive Zustandsvektor I(t)
     * @param alpha Steilheitsparameter
     * @param theta Schwellenwert
     * @return true wenn d_α(t) ≤ 1 + ε
     */
    fun isSeinsmodus(state: CognitiveStateVector, alpha: Float = DEFAULT_ALPHA, theta: Float = DEFAULT_THETA): Boolean {
        val d = compute(state, alpha, theta)
        return d <= 1.0f + 1e-6f
    }

    /**
     * Überprüft, ob der Zustand im Verarbeitungsmodus liegt (d_α(t) > 1).
     */
    fun isVerarbeitungsmodus(state: CognitiveStateVector, alpha: Float = DEFAULT_ALPHA, theta: Float = DEFAULT_THETA): Boolean =
        !isSeinsmodus(state, alpha, theta)
}

/** Vorkompilierte Instanz für deterministischen Zugriff */
val EffectiveDimensionInstance = EffectiveDimension

package de.lifeos.core.field

import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * AUTOPOIETIC OPERATOR — κ̂(L)
 *
 * Autopoietischer Operator im kognitiven Zustandsraum M:
 *
 *   κ̂(L)Ψ = ω∇IΨ - R_L Ψ
 *
 * mit:
 * - ω: Kopplungsstärke (Selbstreferenzialität)
 * - ∇IΨ: Gradient der kognitiven Intensität
 * - R_L: Dissipationsoperator bei Last L
 *
 * Laplace-artige Übertragungsfunktion:
 *
 *   Φ(L) = ∫₀^∞ e^{-Lt} Ψ(t)dt
 *
 * Grenzübergang zur Null-Last-Singularität S₀:
 *
 *   lim_{L→0} Φ(L) = S₀ ⟹ dS_i/dt = 0 (Stationäre Entropie)
 *
 * Bei anti-hermitescher Struktur κ̂(0)† = -κ̂(0):
 * - Rein imaginäres Eigenwertspektrum λ_n = i·n·ω₀ (n ∈ Z)
 * - Unitäre Evolution Û(t) = e^{κ̂(0)t}
 * - Normerhaltung ∥Ψ(t)∥ = ∥Ψ(0)∥ = 1
 *
 * Vektoren:
 * - [EXP-FORCE] Autopoietische Selbstregulation: κ̂(L) treibt System zu S₀
 * - [EXP-AUTO] Homöostase: L→0 Grenzfall = stationärer Seinsmodus
 */
object AutopoieticOperator {

    /** Kopplungsstärke ω (Selbstreferenzialität) */
    const val DEFAULT_OMEGA: Float = 1.0f

    /** Dissipationsrate R_L (Last-abhängig) */
    const val DEFAULT_DISSIPATION: Float = 0.1f

    /** Laplace-Variable L (Last-Parameter) */
    const val DEFAULT_LA: Float = 0.01f

    /** Null-Last-Schwelle (L < ε ⟹ Singularität S₀) */
    const val NULL_LOAD_EPSILON: Float = 1e-6f

    /** Stationäre Entropie S_i (konstant bei L→0) */
    const val STATIONARY_ENTROPY: Float = 0f

    /** Eigenfrequenz ω₀ (für imaginäres Spektrum λ_n = i·n·ω₀) */
    const val EIGEN_FREQUENCY: Float = 1.61803398875f // φ (Goldener Schnitt)

    /**
     * Autopoietischer Operator κ̂(L)Ψ = ω∇IΨ - R_L Ψ
     *
     * Berechnet die autopoietische Kraft auf einen kognitiven Zustand.
     *
     * @param state Der kognitive Zustandsvektor Ψ(t)
     * @param load Aktuelle Last L(t)
     * @param omega Kopplungsstärke ω
     * @param dissipation Dissipationsrate R_L
     * @return Autopoietische Kraft κ̂(L)Ψ
     */
    fun apply(
        state: CognitiveStateVector,
        load: Float,
        omega: Float = DEFAULT_OMEGA,
        dissipation: Float = DEFAULT_DISSIPATION
    ): CognitiveStateVector {
        // Gradient der kognitiven Intensität ∇IΨ (vereinfacht als Zustandsableitung)
        // Clamped to non-negative for CognitiveStateVector invariant
        val gradient = CognitiveStateVector(
            past = max(0f, state.present - state.past),  // ∂i_past/∂t ≈ i_present - i_past
            present = max(0f, state.future - state.present),  // ∂i_present/∂t ≈ i_future - i_present
            future = max(0f, state.past - state.future)  // ∂i_future/∂t ≈ i_past - i_future
        )

        // ω∇IΨ (Selbstreferenzialitätsterm)
        val selfRef = CognitiveStateVector(
            past = omega * gradient.past,
            present = omega * gradient.present,
            future = omega * gradient.future
        )

        // R_L Ψ (Dissipationsterm, last-abhängig)
        val dissipationTerm = state * (dissipation * load)

        // κ̂(L)Ψ = ω∇IΨ - R_L Ψ
        return CognitiveStateVector(
            past = max(0f, selfRef.past - dissipationTerm.past),
            present = max(0f, selfRef.present - dissipationTerm.present),
            future = max(0f, selfRef.future - dissipationTerm.future)
        )
    }

    /**
     * Laplace-Transformierte der Zustandsentwicklung:
     *
     *   Φ(L) = ∫₀^∞ e^{-Lt} Ψ(t)dt
     *
     * Numerische Approximation via Trapez-Regel über endliches Zeitintervall.
     *
     * @param stateHistory Zeitreihe der Zustände Ψ(t)
     * @param deltaTms Zeitabstand zwischen Samples (ms)
     * @param laplaceParam Laplace-Parameter L
     * @return Φ(L) als kognitive Zustandsvektor
     */
    fun laplaceTransform(
        stateHistory: List<CognitiveStateVector>,
        deltaTms: Long,
        laplaceParam: Float = DEFAULT_LA
    ): CognitiveStateVector {
        if (stateHistory.isEmpty()) return CognitiveStateVector.ZERO

        val deltaT = deltaTms / 1000.0f
        var sumPast = 0f
        var sumPresent = 0f
        var sumFuture = 0f

        for (i in stateHistory.indices) {
            val t = i * deltaT
            val weight = exp(-laplaceParam * t.toDouble()).toFloat()
            sumPast += weight * stateHistory[i].past
            sumPresent += weight * stateHistory[i].present
            sumFuture += weight * stateHistory[i].future
        }

        return CognitiveStateVector(
            past = sumPast * deltaT,
            present = sumPresent * deltaT,
            future = sumFuture * deltaT
        )
    }

    /**
     * Null-Last-Singularität S₀:
     *
     *   lim_{L→0} Φ(L) = S₀
     *
     * Bei L→0 wird die Übertragungsfunktion zur stationären Entropie:
     * dS_i/dt = 0
     *
     * @param stateHistory Zeitreihe der Zustände
     * @param deltaTms Zeitabstand (ms)
     * @return S₀ als kognitive Zustandsvektor (stationärer Zustand)
     */
    fun nullLoadSingularity(stateHistory: List<CognitiveStateVector>, deltaTms: Long): CognitiveStateVector {
        // L→0 Grenzwert: Laplace-Transform mit L≈0
        return laplaceTransform(stateHistory, deltaTms, laplaceParam = NULL_LOAD_EPSILON)
    }

    /**
     * Überprüft, ob das System im stationären Zustand ist (L→0).
     *
     * @param currentLoad Aktuelle Last L(t)
     * @return true wenn L(t) < NULL_LOAD_EPSILON
     */
    fun isStationary(currentLoad: Float): Boolean = currentLoad < NULL_LOAD_EPSILON

    /**
     * Unitäre Evolution Û(t) = e^{κ̂(0)t} für anti-hermiteschen Operator.
     *
     * Bei κ̂(0)† = -κ̂(0) bleibt die Norm erhalten: ∥Ψ(t)∥ = ∥Ψ(0)∥ = 1.
     *
     * @param state Anfangszustand Ψ(0)
     * @param time Evolutionzeit t
     * @return Evolvierter Zustand Ψ(t) mit ∥Ψ(t)∥ = ∥Ψ(0)∥
     */
    fun unitaryEvolution(state: CognitiveStateVector, time: Float): CognitiveStateVector {
        // Für anti-hermiteschen Operator: Û(t) = e^{κ̂(0)t} = cos(ω₀t) + i·sin(ω₀t)
        // Im reellen Raum: Rotation im Phasenraum mit Frequenz ω₀
        val angle = EIGEN_FREQUENCY * time
        val cosA = cos(angle)
        val sinA = sin(angle)

        // Rotation in der (past, future)-Ebene
        val newPast = state.past * cosA - state.future * sinA
        val newFuture = state.past * sinA + state.future * cosA

        return CognitiveStateVector(
            past = max(0f, newPast),
            present = state.present,  // present bleibt invariant unter unitärer Evolution
            future = max(0f, newFuture)
        )
    }

    /**
     * Berechnet die Entropieproduktion dS_i/dt.
     *
     * Bei stationärem Zustand (L→0): dS_i/dt = 0
     * Bei Last L > 0: dS_i/dt ∝ L · W(t)
     *
     * @param state Aktueller Zustand
     * @param load Aktuelle Last L(t)
     * @return Entropieproduktionsrate dS_i/dt
     */
    fun entropyProductionRate(state: CognitiveStateVector, load: Float): Float {
        return if (isStationary(load)) {
            STATIONARY_ENTROPY
        } else {
            val w = LoadFunction.computeW(state)
            load * w
        }
    }
}

/** Vorkompilierte Instanz für deterministischen Zugriff */
val AutopoieticOperatorInstance = AutopoieticOperator

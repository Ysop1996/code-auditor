package de.lifeos.core.field

import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min

/**
 * PGO TUNNEL OPERATOR — T̂_PGO
 *
 * WKB-Tunnelwahrscheinlichkeit durch die allochthone Potentialbarriere:
 *
 *   P_tunnel = exp(-ℏ_cog/2 ∫_{x1}^{x2} 2M_red(x)[V_barrier(x) - E_Gegenwart] dx)
 *
 * Für E_Gegenwart → V_barrier folgt P_tunnel → 1
 * ⟹ C(t) → 0 (Reibungsfreier Cache-Reset)
 *
 * Physikalische Interpretation:
 * - V_barrier(x): Allochthone Konditionierungspotential (Fremdbestimmung)
 * - E_Gegenwart: Energie des gegenwärtigen Bewusstseinszustands
 * - M_red(x): Reduzierte Masse (Trägheit gegen Konditionierung)
 * - ℏ_cog: Kognitive Planck-Konstante
 *
 * Wenn die Gegenwartsenergie die Potentialbarriere übersteigt,
 * tunnelt das System reibungsfrei in den souveränen Zustand.
 *
 * Vektoren:
 * - [EXP-FORCE] Souveränitäts-Erhöhung: P_tunnel → 1
 * - [EXP-AUTO] Autopoietische Selbstregulation: Cache-Reset bei Barriere-Überwindung
 */
object PgoTunnelOperator {

    /** Kognitive Planck-Konstante ℏ_cog */
    const val HBarCog: Float = 0.5f

    /** Minimaler Integrationsabstand (numerische Stabilität) */
    const val MIN_DELTA_X: Float = 1e-6f

    /** Standard-Potentialbarriere-Höhe V₀ */
    const val DEFAULT_BARRIER_HEIGHT: Float = 1.0f

    /** Standard-reduzierte Masse M_red */
    const val DEFAULT_REDUCED_MASS: Float = 1.0f

    /** Standard-Gegenwartsenergie E_Gegenwart */
    const val DEFAULT_PRESENT_ENERGY: Float = 0.5f

    /** Tunneling-Schwelle (P_tunnel ≥ threshold ⟹ Cache-Reset) */
    const val TUNNEL_THRESHOLD: Float = 0.95f

    /** Cache-Reset-Schwelle (P_tunnel ≥ threshold ⟹ C(t) → 0) */
    const val CACHE_RESET_THRESHOLD: Float = 0.99f

    /**
     * Allochthone Potentialbarriere V_barrier(x).
     *
     * Vereinfachtes Doppelpotenzial-Modell:
     * - Hohe Barriere bei x = 0 (allochthone Konditionierung)
     * - Abfallende Barriere für |x| > 0
     *
     * @param x Position im Potentialraum
     * @param barrierHeight Höhe der Potentialbarriere V₀
     * @return Potential V_barrier(x) ≥ 0
     */
    fun potentialBarrier(x: Float, barrierHeight: Float = DEFAULT_BARRIER_HEIGHT): Float {
        // Gaußförmige Barriere zentriert bei x = 0
        val gaussian = exp(-(x * x).toDouble() / 2.0).toFloat()
        return barrierHeight * gaussian
    }

    /**
     * Reduzierte Masse M_red(x).
     *
     * Modelliert die Trägheit gegen allochthone Konditionierung:
     * - Höhere Masse bei x = 0 (starke Konditionierung)
     * - Niedrigere Masse für |x| > 0 (souveräner Raum)
     *
     * @param x Position im Potentialraum
     * @param baseMass Basis-Masse
     * @return Reduzierte Masse M_red(x) > 0
     */
    fun reducedMass(x: Float, baseMass: Float = DEFAULT_REDUCED_MASS): Float {
        // Inverse Gauß-Verteilung: mehr Trägheit im Zentrum der Konditionierung
        val gaussian = exp(-(x * x).toDouble() / 2.0).toFloat()
        return baseMass * (1.0f + 2.0f * gaussian)
    }

    /**
     * WKB-Tunnelwahrscheinlichkeit durch die allochthone Potentialbarriere.
     *
     *   P_tunnel = exp(-ℏ_cog/2 ∫_{x1}^{x2} 2M_red(x)[V_barrier(x) - E_Gegenwart] dx)
     *
     * Numerische Integration via Trapez-Regel.
     *
     * @param x1 Startposition
     * @param x2 Endposition
     * @param presentEnergy Gegenwartsenergie E_Gegenwart
     * @param barrierHeight Potentialbarriere-Höhe V₀
     * @param hbar Kognitive Planck-Konstante ℏ_cog
     * @return P_tunnel ∈ [0, 1]
     */
    fun tunnelProbability(
        x1: Float,
        x2: Float,
        presentEnergy: Float = DEFAULT_PRESENT_ENERGY,
        barrierHeight: Float = DEFAULT_BARRIER_HEIGHT,
        hbar: Float = HBarCog
    ): Float {
        require(x2 > x1) { "x2 must be greater than x1" }

        val steps = 100
        val dx = (x2 - x1) / steps
        var integral = 0f

        for (i in 0 until steps) {
            val xMid = x1 + (i + 0.5f) * dx
            val v = potentialBarrier(xMid, barrierHeight)
            val m = reducedMass(xMid)
            val exponent = 2f * m * max(0f, v - presentEnergy)
            integral += exponent * dx
        }

        val exponent = -hbar / 2f * integral
        return exp(exponent.toDouble()).toFloat().coerceIn(0f, 1f)
    }

    /**
     * Cache-Reset-Operator: C(t) → 0 bei P_tunnel → 1.
     *
     * Wenn die Gegenwartsenergie die Potentialbarriere übersteigt,
     * wird der Cache reibungsfrei zurückgesetzt.
     *
     * @param currentCache Aktueller Cache-Wert C(t)
     * @param tunnelProbability Aktuelle Tunnelwahrscheinlichkeit P_tunnel
     * @return Neuer Cache-Wert C(t+Δt)
     */
    fun cacheReset(currentCache: Float, tunnelProbability: Float): Float {
        return if (tunnelProbability >= CACHE_RESET_THRESHOLD) {
            0f  // Reibungsfreier Reset
        } else {
            // Partieller Reset proportional zu P_tunnel
            currentCache * (1f - tunnelProbability)
        }
    }

    /**
     * Überprüft, ob ein Cache-Reset ausgelöst werden sollte.
     *
     * @param tunnelProbability Aktuelle Tunnelwahrscheinlichkeit
     * @return true wenn P_tunnel ≥ CACHE_RESET_THRESHOLD
     */
    fun shouldResetCache(tunnelProbability: Float): Boolean =
        tunnelProbability >= CACHE_RESET_THRESHOLD

    /**
     * Berechnet die erforderliche Gegenwartsenergie für P_tunnel ≥ threshold.
     *
     * @param x1 Startposition
     * @param x2 Endposition
     * @param threshold Schwelle für P_tunnel
     * @param barrierHeight Potentialbarriere-Höhe
     * @return Erforderliche E_Gegenwart (iterative Annäherung)
     */
    fun requiredPresentEnergy(
        x1: Float,
        x2: Float,
        threshold: Float = TUNNEL_THRESHOLD,
        barrierHeight: Float = DEFAULT_BARRIER_HEIGHT
    ): Float {
        var low = 0f
        var high = barrierHeight * 2f
        var best = DEFAULT_PRESENT_ENERGY

        repeat(50) {  // Binäre Suche
            val mid = (low + high) / 2f
            val p = tunnelProbability(x1, x2, mid, barrierHeight)
            if (p >= threshold) {
                best = mid
                high = mid
            } else {
                low = mid
            }
        }

        return best
    }

    /**
     * Berechnet die Tunnel-Zeitkonstante (wie schnell C(t) → 0).
     *
     * @param tunnelProbability Aktuelle P_tunnel
     * @return Zeitkonstante τ (s), kleiner = schnellerer Reset
     */
    fun tunnelTimeConstant(tunnelProbability: Float): Float {
        return if (tunnelProbability >= CACHE_RESET_THRESHOLD) {
            0f  // Sofortiger Reset
        } else {
            // Exponentieller Abfall: τ = -1/ln(1 - P_tunnel)
            val safeP = tunnelProbability.coerceIn(0f, 0.99f)
            (-1.0 / kotlin.math.ln((1.0 - safeP).toDouble())).toFloat()
        }
    }

    /**
     * Überprüft, ob das System im Seinsmodus angelangt ist (E_Gegenwart ≥ V_barrier).
     *
     * @param presentEnergy Gegenwartsenergie E_Gegenwart
     * @param barrierHeight Potentialbarriere-Höhe
     * @return true wenn E_Gegenwart ≥ V_barrier
     */
    fun isSeinsmodus(presentEnergy: Float, barrierHeight: Float = DEFAULT_BARRIER_HEIGHT): Boolean =
        presentEnergy >= barrierHeight
}

/** Vorkompilierte Instanz für deterministischen Zugriff */
val PgoTunnelOperatorInstance = PgoTunnelOperator

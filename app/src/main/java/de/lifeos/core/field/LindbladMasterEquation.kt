package de.lifeos.core.field

import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * LINDBLAD MASTER EQUATION — Offene kognitive Systemdynamik
 *
 * Lindblad-Superoperator offener Systeme:
 *
 *   dρ̂_cog/dt = -i/ℏ_cog [Ĥ_sys, ρ̂_cog] + Σ_k (L̂_k ρ̂_cog L̂_k† - ½{L̂_k† L̂_k, ρ̂_cog})
 *
 * Dekohärenzzeit:
 *
 *   τ_decoh ∝ |ERN_max| · Δτ_diss / ℏ_cog
 *
 * Interpretation:
 * - ρ̂_cog: Kognitiver Dichteoperator (Zustandsgemisch)
 * - Ĥ_sys: System-Hamiltonian (kognitive Energie)
 * - L̂_k: Lindblad-Operatoren (Umwelt-Kopplung)
 * - τ_decoh: Zeit bis Dekohärenz (Verlust der Kohärenz)
 *
 * Von-Neumann-Entropie:
 *
 *   S_vN = -k_B Tr(ρ_cog ln ρ_cog)
 *
 * Validierung: S_vN ≈ 0.69229 ≈ ln(2) ≈ 0.693147 (kognitive Quanten-Resonanz)
 *
 * Vektoren:
 * - [EXP-FORCE] Offene Systemdynamik: Dekohärenz-Modellierung
 * - [EXP-AUTO] Autopoietische Regulation: τ_decoh-Maximierung
 */
object LindbladMasterEquation {

    /** Kognitive Planck-Konstante ℏ_cog */
    const val HBarCog: Float = 0.5f

    /** Boltzmann-Konstante k_B (kognitive Einheiten) */
    const val KB: Float = 1.0f

    /** Standard-ERN-Amplitude |ERN_max| (μV) */
    const val DEFAULT_ERN_MAX: Float = 1.0f

    /** Standard-Dissoziative Latenz Δτ_diss (ms) */
    const val DEFAULT_DISS_LATENCY: Float = 100.0f

    /** Anzahl der Lindblad-Operatoren (Umwelt-Kanäle) */
    const val NUM_LINDABLAD_OPERATORS: Int = 3

    /** Minimaler Eigenwert für numerische Stabilität */
    const val MIN_EIGENVALUE: Float = 1e-6f

    /** Standard-Zeitschritt für numerische Integration (ms) */
    const val DEFAULT_DT_MS: Long = 10L

    /**
     * Berechnet die Dekohärenzzeit τ_decoh.
     *
     *   τ_decoh ∝ |ERN_max| · Δτ_diss / ℏ_cog
     *
     * @param ernMaxMicrovolt Maximale ERN-Amplitude (μV)
     * @param dissLatencyMs Dissoziative Latenzverzögerung (ms)
     * @param hbar Kognitive Planck-Konstante
     * @return Dekohärenzzeit τ_decoh (ms)
     */
    fun decoherenceTime(
        ernMaxMicrovolt: Float = DEFAULT_ERN_MAX,
        dissLatencyMs: Float = DEFAULT_DISS_LATENCY,
        hbar: Float = HBarCog
    ): Float {
        return (ernMaxMicrovolt * dissLatencyMs) / hbar
    }

    /**
     * System-Hamiltonian Ĥ_sys für kognitive Zustandsentwicklung.
     *
     * Vereinfacht: Ĥ_sys = diag(E_past, E_present, E_future)
     * wobei E_k die kognitive Energie der jeweiligen Komponente ist.
     *
     * @param state Der kognitive Zustandsvektor I(t)
     * @return Hamiltonian als 3×3-Matrix (row-major)
     */
    fun systemHamiltonian(state: CognitiveStateVector): FloatArray {
        val norm = max(state.norm(), MIN_EIGENVALUE)
        val ePast = (state.past / norm) * 0.5f
        val ePresent = (state.present / norm) * 1.0f
        val eFuture = (state.future / norm) * 0.5f

        return floatArrayOf(
            ePast, 0f, 0f,
            0f, ePresent, 0f,
            0f, 0f, eFuture
        )
    }

    /**
     * Lindblad-Operatoren L̂_k (Umwelt-Kopplung).
     *
     * Drei Kanäle:
     * - L̂_0: Vergangenheits-Kopplung (i_past ↔ Umwelt)
     * - L̂_1: Gegenwarts-Kopplung (i_present ↔ Umwelt)
     * - L̂_2: Zukunfts-Kopplung (i_future ↔ Umwelt)
     *
     * @return Array von 3×3-Lindblad-Operatoren
     */
    fun lindbladOperators(): Array<FloatArray> {
        return arrayOf(
            // L̂_0: Vergangenheits-Kopplung
            floatArrayOf(
                1f, 0f, 0f,
                0f, 0f, 0f,
                0f, 0f, 0f
            ),
            // L̂_1: Gegenwarts-Kopplung
            floatArrayOf(
                0f, 0f, 0f,
                0f, 1f, 0f,
                0f, 0f, 0f
            ),
            // L̂_2: Zukunfts-Kopplung
            floatArrayOf(
                0f, 0f, 0f,
                0f, 0f, 0f,
                0f, 0f, 1f
            )
        )
    }

    /**
     * Berechnet den Zeitschritt der Lindblad-Mastergleichung.
     *
     *   dρ/dt = -i/ℏ [H, ρ] + Σ_k (L_k ρ L_k† - ½{L_k† L_k, ρ})
     *
     * Implementiert als vereinfachte Dichteoperator-Dynamik für
     * kognitive Zustandsvektoren.
     *
     * @param state Aktueller Zustand (als Wahrscheinlichkeitsverteilung)
     * @param hamiltonian System-Hamiltonian
     * @param lindbladOps Lindblad-Operatoren
     * @param dtMs Zeitschritt (ms)
     * @return Zustandsableitung dρ/dt
     */
    fun masterEquationStep(
        state: CognitiveStateVector,
        hamiltonian: FloatArray,
        lindbladOps: Array<FloatArray>,
        dtMs: Long = DEFAULT_DT_MS
    ): CognitiveStateVector {
        val dt = dtMs / 1000.0f
        val norm = max(state.norm(), MIN_EIGENVALUE)

        // Vereinfachte Kohärenz-Terme (Kommutator -i/ℏ [H, ρ])
        // Für diagonale H und ρ: [H, ρ] = 0, nur Diagonalelemente
        val coherencePast = (hamiltonian[4] - hamiltonian[0]) * state.present * state.past / norm
        val coherencePresent = (hamiltonian[8] - hamiltonian[4]) * state.future * state.present / norm
        val coherenceFuture = (hamiltonian[0] - hamiltonian[8]) * state.past * state.future / norm

        // Dissipationsterme (Lindblad)
        var dissipationPast = 0f
        var dissipationPresent = 0f
        var dissipationFuture = 0f

        for (lindblad in lindbladOps) {
            // L ρ L† - ½{L† L, ρ} (vereinfacht für diagonale Operatoren)
            val lDiag = floatArrayOf(lindblad[0], lindblad[4], lindblad[8])
            val statePast = state.past
            val statePresent = state.present
            val stateFuture = state.future
            for (k in 0..2) {
                val lK = lDiag[k]
                if (lK > 0f) {
                    val stateK = when (k) {
                        0 -> statePast
                        1 -> statePresent
                        else -> stateFuture
                    }
                    val decay = lK * lK * stateK
                    when (k) {
                        0 -> dissipationPast -= decay * 0.5f
                        1 -> dissipationPresent -= decay * 0.5f
                        2 -> dissipationFuture -= decay * 0.5f
                    }
                }
            }
        }

        // Gesamtdynamik
        val dpast = (-coherencePast + dissipationPast) * dt / HBarCog
        val dpresent = (-coherencePresent + dissipationPresent) * dt / HBarCog
        val dfuture = (-coherenceFuture + dissipationFuture) * dt / HBarCog

        return CognitiveStateVector(
            past = max(0f, state.past + dpast),
            present = max(0f, state.present + dpresent),
            future = max(0f, state.future + dfuture)
        )
    }

    /**
     * Von-Neumann-Entropie des kognitiven Dichteoperators:
     *
     *   S_vN = -k_B Tr(ρ_cog ln ρ_cog)
     *
     * Für reinen Zustand (ρ = |Ψ⟩⟨Ψ|): S_vN = 0
     * Für maximale Mischung (ρ = I/3): S_vN = k_B ln(3)
     *
     * Validierungswert: S_vN ≈ 0.69229 ≈ ln(2) ≈ 0.693147
     *
     * @param state Der kognitive Zustandsvektor (als Wahrscheinlichkeitsverteilung)
     * @return Von-Neumann-Entropie S_vN ≥ 0
     */
    fun vonNeumannEntropy(state: CognitiveStateVector): Float {
        val norm = max(state.norm(), MIN_EIGENVALUE)
        val pPast = (state.past / norm).coerceIn(MIN_EIGENVALUE, 1f)
        val pPresent = (state.present / norm).coerceIn(MIN_EIGENVALUE, 1f)
        val pFuture = (state.future / norm).coerceIn(MIN_EIGENVALUE, 1f)

        // Renormierung auf Wahrscheinlichkeitsverteilung
        val sum = pPast + pPresent + pFuture
        if (sum < MIN_EIGENVALUE) return 0f

        val pP = pPast / sum
        val pPr = pPresent / sum
        val pF = pFuture / sum

        // S_vN = -k_B Σ p_i ln(p_i)
        val entropy = -KB * (
            pP * ln(pP.toDouble()).toFloat() +
            pPr * ln(pPr.toDouble()).toFloat() +
            pF * ln(pF.toDouble()).toFloat()
        )

        return max(0f, entropy)
    }

    /**
     * Überprüft, ob die Von-Neumann-Entropie dem Validierungswert entspricht.
     *
     * S_vN ≈ 0.69229 ≈ ln(2) ≈ 0.693147
     *
     * @param entropy Gemessene Von-Neumann-Entropie
     * @param tolerance Toleranz für Übereinstimmung
     * @return true wenn S_vN ≈ ln(2)
     */
    fun isQuantumResonance(entropy: Float, tolerance: Float = 0.01f): Boolean {
        val ln2 = kotlin.math.ln(2.0).toFloat()
        return kotlin.math.abs(entropy - ln2) < tolerance
    }

    /**
     * Berechnet die Kohärenzzeit (wie lange der Zustand kohärent bleibt).
     *
     * τ_coh = τ_decoh · (1 - S_vN / ln(3))
     *
     * @param decoherenceTime Dekohärenzzeit τ_decoh
     * @param vonNeumannEntropy Von-Neumann-Entropie S_vN
     * @return Kohärenzzeit τ_coh ≤ τ_decoh
     */
    fun coherenceTime(decoherenceTime: Float, vonNeumannEntropy: Float): Float {
        val maxEntropy = kotlin.math.ln(3.0).toFloat()
        val coherenceFactor = 1f - (vonNeumannEntropy / max(maxEntropy, MIN_EIGENVALUE))
        return decoherenceTime * max(0f, coherenceFactor)
    }

    /**
     * Purity-Maß des kognitiven Zustands:
     *
     *   P = Tr(ρ²) ∈ [1/3, 1]
     *
     * - P = 1: Reiner Zustand (maximale Kohärenz)
     * - P = 1/3: Maximale Mischung (vollständige Dekohärenz)
     *
     * @param state Der kognitive Zustandsvektor
     * @return Purity P ∈ [1/3, 1]
     */
    fun purity(state: CognitiveStateVector): Float {
        val norm = max(state.norm(), MIN_EIGENVALUE)
        val pPast = state.past / norm
        val pPresent = state.present / norm
        val pFuture = state.future / norm

        // P = Σ p_i² (für reine Zustände)
        return (pPast * pPast + pPresent * pPresent + pFuture * pFuture).coerceIn(1f / 3f, 1f)
    }
}

/** Vorkompilierte Instanz für deterministischen Zugriff */
val LindbladMasterEquationInstance = LindbladMasterEquation

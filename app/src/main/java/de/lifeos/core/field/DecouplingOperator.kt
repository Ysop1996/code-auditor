package de.lifeos.core.field

import kotlin.math.max
import kotlin.math.min

/**
 * DECOUPLING OPERATOR — D̂
 *
 * Entkopplungs-Operator zur Beseitigung allochthoner Fremdkonditionierungen.
 * Zerlegt die Vergangenheitsachse additiv:
 *
 *   i_past(t) = i_past_eigen(t) + i_past_fremd(t)
 *
 * Operator D̂ (Schnitt):
 *
 *   D̂(I(t)) = (i_past_eigen(t), i_present(t), u(t))^T
 *
 * wobei u(t) der souveräne Zustand nach Entkopplung ist.
 *
 * Neurophysiologische Quantifizierung von i_past_fremd(t):
 *
 *   i_past_fremd(t) = μ · |ERN_max| · Δτ_diss
 *
 * wobei |ERN_max| die maximale Amplitudenauslenkung der Error-Related Negativity
 * im ACC (Anterior Cingulate Cortex) ist und Δτ_diss die Latenzverzögerung
 * unter Compliance misst.
 *
 * Falsifikationsgrenze: |ERN_max| < 1,5 μV ⟹ i_past_fremd(t) → 0
 *
 * Vektoren:
 * - [EXP-FORCE] Souveränitäts-Erhöhung: Eliminierung allochthoner Konditionierungen
 * - [EXP-AUTO] Autopoietische Selbstregulation: Echtzeit-Entkopplung
 */
object DecouplingOperator {

    /** Falsifikationsgrenze für ERN_max (μV) */
    const val ERN_THRESHOLD_MICROVOLT: Float = 1.5f

    /** Standard-Gewicht μ für ERN-Amplitude */
    const val DEFAULT_MU_ERN: Float = 0.8f

    /** Minimaler Δτ_diss (ms) für messbare Fremdkonditionierung */
    const val MIN_DISS_MS: Float = 10.0f

    /** Maximaler Δτ_diss (ms) für Sättigung */
    const val MAX_DISS_MS: Float = 500.0f

    /** Skalierungsfaktor für Δτ_diss → normierter Raum */
    const val DISS_SCALE: Float = 1.0f / (MAX_DISS_MS - MIN_DISS_MS)

    /**
     * Quantifiziert allochthone Fremdkonditionierung i_past_fremd(t).
     *
     * i_past_fremd(t) = μ · |ERN_max| · Δτ_diss
     *
     * @param ernMaxMicrovolt Maximale ERN-Amplitude (μV)
     * @param dissLatencyMs Dissociative Latenzverzögerung (ms)
     * @param mu Gewichtungsfaktor (Standard: 0.8)
     * @return i_past_fremd(t) ≥ 0
     */
    fun quantifyAllochthoneInterference(
        ernMaxMicrovolt: Float,
        dissLatencyMs: Float,
        mu: Float = DEFAULT_MU_ERN
    ): Float {
        val clampedErn = max(0f, ernMaxMicrovolt)
        val clampedDiss = dissLatencyMs.coerceIn(MIN_DISS_MS, MAX_DISS_MS)
        return mu * clampedErn * clampedDiss
    }

    /**
     * Falsifikations-Prüfung: |ERN_max| < 1,5 μV ⟹ i_past_fremd → 0
     *
     * @param ernMaxMicrovolt Maximale ERN-Amplitude (μV)
     * @return true wenn keine messbare Fremdkonditionierung vorliegt
     */
    fun isFalsified(ernMaxMicrovolt: Float): Boolean =
        ernMaxMicrovolt < ERN_THRESHOLD_MICROVOLT

    /**
     * Entkoppelt einen kognitiven Zustandsvektor D̂(I(t)).
     *
     * Zerlegt i_past in i_past_eigen + i_past_fremd und entfernt den
     * allochthonen Anteil. Der souveräne Zustand u(t) wird als
     * i_present + i_past_eigen gebildet.
     *
     * D̂(I(t)) = (i_past_eigen(t), i_present(t), u(t))^T
     *
     * @param state Der kognitive Zustandsvektor I(t)
     * @param allochthonePast Der allochthone Anteil i_past_fremd(t)
     * @return Entkoppelter Zustand D̂(I(t))
     */
    fun decouple(
        state: CognitiveStateVector,
        allochthonePast: Float
    ): CognitiveStateVector {
        val eigenPast = max(0f, state.past - allochthonePast)
        val sovereign = state.present + eigenPast
        return CognitiveStateVector(
            past = eigenPast,
            present = state.present,
            future = max(0f, state.future - allochthonePast * 0.5f)
        )
    }

    /**
     * Berechnet den Souveränitäts-Zuwachs durch Entkopplung.
     *
     * ΔS_o = i_past_fremd_entfernt / i_present
     *
     * @param originalState Ursprünglicher Zustand I(t)
     * @param decoupledState Entkoppelter Zustand D̂(I(t))
     * @return Souveränitäts-Zuwachs ≥ 0
     */
    fun sovereigntyGain(originalState: CognitiveStateVector, decoupledState: CognitiveStateVector): Float {
        val removed = originalState.past - decoupledState.past
        return if (decoupledState.present > 1e-6f) {
            (removed / decoupledState.present).coerceAtLeast(0f)
        } else {
            0f
        }
    }

    /**
     * Überprüft, ob Souveränität erreicht ist: S_o(t) ≥ 1
     *
     * @param sovereigntyIndex Der aktuelle Souveränitätsindex S_o(t)
     * @return true wenn S_o(t) ≥ 1
     */
    fun isSovereign(sovereigntyIndex: Float): Boolean = sovereigntyIndex >= 1.0f
}

/** Vorkompilierte Instanz für deterministischen Zugriff */
val DecouplingOperatorInstance = DecouplingOperator

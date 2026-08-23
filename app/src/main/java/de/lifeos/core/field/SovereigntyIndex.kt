package de.lifeos.core.field

import kotlin.math.max
import kotlin.math.min

/**
 * SOVEREIGNTY INDEX — S_o(t) & Kausalitätsbruch t_D
 *
 * Souveränitäts-Index:
 *
 *   S_o(t) = (i_past_fremd(t) + i_future_fremd(t)) / i_present(t)
 *
 * Kausalitätsbruch-Zeit:
 *
 *   t_D = inf{t ∈ T | S_o(t) ≥ 1}
 *
 * Interpretation:
 * - S_o(t) < 1: System ist nicht-souverän (fremdbestimmt)
 * - S_o(t) ≥ 1: System ist souverän (Selbstbestimmung erreicht)
 * - t_D: Zeitpunkt des Kausalitätsbruchs (Übergang zur Souveränität)
 *
 * Vektoren:
 * - [EXP-FORCE] Souveränitäts-Maximierung: S_o(t) → ∞
 * - [EXP-AUTO] Autopoietische Selbstregulation: t_D-Minimierung
 */
object SovereigntyIndex {

    /** Souveränitäts-Schwelle: S_o(t) ≥ 1 */
    const val SOVEREIGNTY_THRESHOLD: Float = 1.0f

    /** Minimaler i_present für Division (numerische Stabilität) */
    const val MIN_PRESENT: Float = 1e-6f

    /** Maximale S_o(t) für Clamping (verhindert Overflow) */
    const val MAX_SOVEREIGNTY: Float = 100f

    /**
     * Berechnet den Souveränitäts-Index S_o(t).
     *
     * S_o(t) = (i_past_fremd(t) + i_future_fremd(t)) / i_present(t)
     *
     * @param allochthonePast Allochthoner Vergangenheitsanteil i_past_fremd(t)
     * @param allochthoneFuture Allochthoner Zukunftsanteil i_future_fremd(t)
     * @param present Gegenwartsanteil i_present(t)
     * @return S_o(t) ≥ 0
     */
    fun compute(
        allochthonePast: Float,
        allochthoneFuture: Float,
        present: Float
    ): Float {
        val denominator = max(present, MIN_PRESENT)
        val numerator = max(0f, allochthonePast) + max(0f, allochthoneFuture)
        return (numerator / denominator).coerceIn(0f, MAX_SOVEREIGNTY)
    }

    /**
     * Berechnet S_o(t) aus einem kognitiven Zustandsvektor und
     * einem Entkopplungszustand.
     *
     * @param originalState Ursprünglicher Zustand I(t)
     * @param decoupledState Entkoppelter Zustand D̂(I(t))
     * @return S_o(t) ≥ 0
     */
    fun computeFromStates(
        originalState: CognitiveStateVector,
        decoupledState: CognitiveStateVector
    ): Float {
        val allochthonePast = originalState.past - decoupledState.past
        val allochthoneFuture = originalState.future - decoupledState.future
        return compute(allochthonePast, allochthoneFuture, decoupledState.present)
    }

    /**
     * Prüft, ob Souveränität erreicht ist: S_o(t) ≥ 1
     *
     * @param sovereigntyIndex Der Souveränitäts-Index S_o(t)
     * @return true wenn souverän
     */
    fun isSovereign(sovereigntyIndex: Float): Boolean = sovereigntyIndex >= SOVEREIGNTY_THRESHOLD

    /**
     * Prüft, ob Fremdbestimmung vorliegt: S_o(t) < 1
     *
     * @param sovereigntyIndex Der Souveränitäts-Index S_o(t)
     * @return true wenn nicht-souverän
     */
    fun isHeteronomous(sovereigntyIndex: Float): Boolean = sovereigntyIndex < SOVEREIGNTY_THRESHOLD

    /**
     * Berechnet den Souveränitäts-Zuwachs durch Entkopplung.
     *
     * ΔS_o = S_o(nach) - S_o(vor)
     *
     * @param beforeSovereignty S_o(t) vor Entkopplung
     * @param afterSovereignty S_o(t) nach Entkopplung
     * @return Zuwachs ΔS_o (kann negativ sein bei Verschlechterung)
     */
    fun sovereigntyDelta(beforeSovereignty: Float, afterSovereignty: Float): Float =
        afterSovereignty - beforeSovereignty

    /**
     * Kausalitätsbruch-Erkennung: t_D = inf{t ∈ T | S_o(t) ≥ 1}
     *
     * @param sovereigntyHistory Zeitreihe der S_o(t)-Werte
     * @param timestampsMs Entsprechende Zeitstempel (ms)
     * @return t_D in ms, oder null wenn kein Bruch aufgetreten ist
     */
    fun detectCausalityBreak(
        sovereigntyHistory: List<Float>,
        timestampsMs: List<Long>
    ): Long? {
        require(sovereigntyHistory.size == timestampsMs.size) {
            "History and timestamps must have same size"
        }
        for (i in sovereigntyHistory.indices) {
            if (isSovereign(sovereigntyHistory[i])) {
                return timestampsMs[i]
            }
        }
        return null
    }

    /**
     * Berechnet die Zeit bis zum Kausalitätsbruch bei linearer Verbesserung.
     *
     * @param currentSovereignty Aktueller S_o(t)
     * @param improvementRate Rate der S_o-Verbesserung pro Sekunde
     * @return Geschätzte Zeit in Sekunden bis S_o ≥ 1 (∞ wenn bereits souverän)
     */
    fun timeToCausalityBreak(currentSovereignty: Float, improvementRate: Float): Float {
        return if (isSovereign(currentSovereignty)) {
            0f
        } else if (improvementRate > 1e-6f) {
            ((SOVEREIGNTY_THRESHOLD - currentSovereignty) / improvementRate).coerceAtLeast(0f)
        } else {
            Float.POSITIVE_INFINITY
        }
    }

    /**
     * Klassifiziert den Souveränitäts-Status.
     *
     * @param sovereigntyIndex Der Souveränitäts-Index S_o(t)
     * @return Status-String: "SOVERÄN", "TRANSITION", "FREMDBESTIMMT"
     */
    fun classifyStatus(sovereigntyIndex: Float): String = when {
        isSovereign(sovereigntyIndex) -> "SOVERÄN"
        sovereigntyIndex >= 0.5f -> "TRANSITION"
        else -> "FREMDBESTIMMT"
    }
}

/** Vorkompilierte Instanz für deterministischen Zugriff */
val SovereigntyIndexInstance = SovereigntyIndex

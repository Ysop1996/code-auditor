package de.lifeos.core.field

import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min

/**
 * COGNITIVE STATE VECTOR — I(t) ∈ R³≥0
 *
 * Der kognitive Zustandsvektor operiert im glatten, nicht-negativen reellen Vektorraum:
 *
 * I(t) = [i_past(t), i_present(t), i_future(t)]^T ∈ R³≥0
 *
 * Komponenten:
 * - i_past: Historische Reevaluierung (Vergangenheitslast)
 * - i_present: Umweltbewältigung / reines Jetzt
 * - i_future: Zukunftsprojektion (Antizipationspotential)
 *
 * Normierung: ∥I(t)∥ = sqrt(i_past² + i_present² + i_future²)
 *
 * Vektoren:
 * - [EXP-FORCE] Zustandsraum-Dynamik: 3-D-Kernrepräsentation der kognitiven Last
 * - [EXP-AUTO] Autopoietische Regulation: Echtzeit-Normierung auf nicht-negative Halbachse
 */
data class CognitiveStateVector(
    val past: Float = 0f,
    val present: Float = 0f,
    val future: Float = 0f
) {
    init {
        require(past >= 0f) { "i_past must be non-negative" }
        require(present >= 0f) { "i_present must be non-negative" }
        require(future >= 0f) { "i_future must be non-negative" }
    }

    /** Rohvektor als FloatArray für Kompatibilität mit PhaseVector */
    fun toFloatArray(): FloatArray = floatArrayOf(past, present, future)

    /** Euklidische Norm ∥I(t)∥ */
    fun norm(): Float = kotlin.math.sqrt(past * past + present * present + future * future)

    /** Normalisierter Zustandsvektor (L2-Norm = 1) */
    fun normalize(): CognitiveStateVector {
        val n = norm()
        return if (n > 1e-7f) {
            CognitiveStateVector(past / n, present / n, future / n)
        } else {
            this
        }
    }

    /** Elementweise Addition */
    operator fun plus(other: CognitiveStateVector): CognitiveStateVector =
        CognitiveStateVector(
            past = max(0f, this.past + other.past),
            present = max(0f, this.present + other.present),
            future = max(0f, this.future + other.future)
        )

    /** Elementweise Subtraktion (clamped to non-negative) */
    operator fun minus(other: CognitiveStateVector): CognitiveStateVector =
        CognitiveStateVector(
            past = max(0f, this.past - other.past),
            present = max(0f, this.present - other.present),
            future = max(0f, this.future - other.future)
        )

    /** Skalare Division (clamped to non-negative) */
    operator fun div(scalar: Float): CognitiveStateVector =
        CognitiveStateVector(
            past = if (scalar != 0.0f) max(0f, past / scalar) else 0f,
            present = if (scalar != 0.0f) max(0f, present / scalar) else 0f,
            future = if (scalar != 0.0f) max(0f, future / scalar) else 0f
        )

    /** Skalare Multiplikation (scalar must be non-negative to preserve I(t) ∈ R³≥0) */
    operator fun times(scalar: Float): CognitiveStateVector {
        require(scalar >= 0f) { "Scalar must be non-negative to preserve CognitiveStateVector ∈ R³≥0, got $scalar" }
        return CognitiveStateVector(
            past = past * scalar,
            present = present * scalar,
            future = future * scalar
        )
    }

    /** Lastfunktion W(t) = μ₁·i_past + μ₂·i_future (Verarbeitungsmodus-Last) */
    fun computeLoad(muPast: Float = 0.6f, muFuture: Float = 0.4f): Float =
        muPast * past + muFuture * future

    /** Seinsmodus-Prädikat: W(t) = 0 ⟹ i_past ≈ 0 ∧ i_future ≈ 0 */
    fun isSeinsmodus(threshold: Float = 0.01f): Boolean =
        past < threshold && future < threshold && present > 0f

    /** Verarbeitungsmodus-Prädikat: W(t) > 0 */
    fun isVerarbeitungsmodus(threshold: Float = 0.01f): Boolean =
        !isSeinsmodus(threshold)

    companion object {
        /** Nullzustand: vollständige Entropie / Reset */
        val ZERO = CognitiveStateVector(0f, 0f, 0f)

        /** Einheitszustand: maximale Gegenwartspräsenz */
        val UNIT = CognitiveStateVector(0f, 1f, 0f)

        /** Standard-Initialisierung aus Rohsensordaten */
        fun fromRaw(past: Float, present: Float, future: Float): CognitiveStateVector =
            CognitiveStateVector(
                past = max(0f, past),
                present = max(0f, present),
                future = max(0f, future)
            )
    }
}

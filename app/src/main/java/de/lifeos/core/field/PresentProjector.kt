package de.lifeos.core.field

import kotlin.math.sqrt

/**
 * PRESENT PROJECTOR — P_S
 *
 * Gegenwartsprojektor als idempotente 3×3-Matrix:
 *
 * P_S = [[0, 0, 0],
 *        [0, 1, 0],
 *        [0, 0, 0]]
 *
 * Eigenschaften:
 * - P_S² = P_S (Idempotenz)
 * - I_coll(t) = P_S · I(t) = [0, i_present(t), 0]^T
 * - ⟨P_S⟩ = 1 (Rang = 1, Spur = 1)
 *
 * Anwendung: Kollaps des fragmentierten Zustandsraums T_V (d=3) auf den
 * singulären Eigenraum der Gegenwart T_S (d=1).
 *
 * Vektoren:
 * - [EXP-FORCE] Moduspartition: T_V ⊔ T_S mit P_S als Projektor
 * - [EXP-AUTO] Autopoietische Regulation: W(t) = 0 unter P_S
 */
object PresentProjector {

    /** 3×3 Projektionsmatrix als flaches FloatArray (row-major) — immutable copy on access */
    private val matrixInternal: FloatArray = floatArrayOf(
        0f, 0f, 0f,  // Zeile 0: i_past → 0
        0f, 1f, 0f,  // Zeile 1: i_present → i_present
        0f, 0f, 0f   // Zeile 2: i_future → 0
    )

    /** Returns an immutable copy of the 3×3 projection matrix (row-major) */
    fun matrix(): FloatArray = matrixInternal.copyOf()

    /** Projektionsoperator auf 1D-Gegenwartsunterraum */
    fun project(state: CognitiveStateVector): CognitiveStateVector =
        CognitiveStateVector(
            past = 0f,
            present = state.present,
            future = 0f
        )

    /** Idempotenz-Beweis: P_S(P_S(I)) = P_S(I) */
    fun verifyIdempotency(state: CognitiveStateVector): Boolean {
        val once = project(state)
        val twice = project(once)
        return once.present == twice.present &&
               once.past == twice.past &&
               once.future == twice.future
    }

    /** Rang der Projektionsmatrix (immer 1 für P_S) */
    fun rank(): Int = 1

    /** Spur der Projektionsmatrix (immer 1 für P_S) */
    fun trace(): Float = 1f

    /** Überprüft, ob der Zustand im Seinsmodus- Unterraum liegt */
    fun isInSeinsmodusSubspace(state: CognitiveStateVector, tolerance: Float = 1e-6f): Boolean =
        kotlin.math.abs(state.past) < tolerance &&
        kotlin.math.abs(state.future) < tolerance &&
        state.present >= 0f

    /** Kollaps-Operator: I_coll(t) = P_S · I(t) */
    fun collapse(state: CognitiveStateVector): CognitiveStateVector = project(state)
}

/** Vorkompilierte Instanz für deterministischen Zugriff */
val PresentProjectorInstance = PresentProjector

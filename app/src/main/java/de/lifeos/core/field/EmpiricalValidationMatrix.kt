package de.lifeos.core.field

import kotlin.math.*

/**
 * EMPIRICAL VALIDATION MATRIX — Messparameter & Validierungswerte
 *
 * Stellt die empirischen Messwerte aus der MMSI V3.8-Spezifikation bereit
 * und validiert das System gegen diese Referenzwerte.
 *
 * Parameter:
 * - τ_min ≤ 30 ms ⟹ V_max ≈ 33.3 Hz (Hardware-Quantisierungsgrenze)
 * - PLV_Hilbert = 0.973408 (Phasenstarre Kondensation im Seinsmodus)
 * - W_base = 0.7557, ΔW = +0.6690 (Reibungs-Baseline)
 * - |ERN_max| < 1.5 μV (Falsifikationsgrenze für allochthone Interferenz)
 * - S_vN = 0.69229 ≈ ln(2) ≈ 0.693147 (Quanten-Resonanz)
 * - E_penalty = 1.201301 (+20.13%) (Viskose Restitutions-Barriere)
 * - m_mask = 0.643047 (Anderson-Higgs-Masse blockierter Skripte)
 * - Ω_krit = 5800 W·s (Bekenstein-Hawking-Informationsgrenze)
 *
 * Vektoren:
 * - [EXP-FORCE] Wissenschaftliche Validierung: Messparameter als Qualitätsmetrik
 * - [EXP-AUTO] Autopoietische Selbstkalibrierung: System validiert sich selbst
 */
object EmpiricalValidationMatrix {

    // =========================================================================
    // EMPIRISCHE MESSWERTE (AUS SPEZIFIKATION)
    // =========================================================================

    /** Pöppel-Zeitfenster: τ_min ≤ 30 ms */
    const val TAU_MIN_MS: Float = 30.0f

    /** Maximale Verarbeitungsfrequenz: V_max ≈ 33.3 Hz */
    val V_MAX_HZ: Float = 1.0f / (TAU_MIN_MS / 1000.0f)

    /** Phase-Locking-Value (Hilbert-Transform): PLV_Hilbert = 0.973408 */
    const val PLV_HILBERT: Float = 0.973408f

    /** Reibungs-Baseline: W_base = 0.7557 */
    const val W_BASE: Float = 0.7557f

    /** Reibungs-Variation: ΔW = +0.6690 */
    const val DW: Float = 0.6690f

    /** Allochthone ERN-Schwelle: |ERN_max| < 1.5 μV */
    const val ERN_THRESHOLD_MICROVOLT: Float = 1.5f

    /** Von-Neumann-Entropie: S_vN = 0.69229 */
    const val S_VN: Float = 0.69229f

    /** Natürlicher Logarithmus von 2: ln(2) ≈ 0.693147 */
    val LN2: Float = kotlin.math.ln(2.0).toFloat()

    /** Memory Energy Penalty: E_penalty = 1.201301 */
    const val E_PENALTY: Float = 1.201301f

    /** Metabolischer Mehrbedarf: +20.13% */
    const val METABOLIC_OVERHEAD_PERCENT: Float = 20.13f

    /** Anderson-Higgs-Masse: m_mask = 0.643047 */
    const val M_MASK: Float = 0.643047f

    /** Kritischer Last-Horizont: Ω_krit = 5800 W·s */
    const val OMEGA_CRITICAL: Float = 5800f

    /** Toleranz für Validierungsprüfungen */
    const val VALIDATION_TOLERANCE: Float = 0.01f

    // =========================================================================
    // VALIDIERUNGSFUNKTIONEN
    // =========================================================================

    /**
     * Validiert das Pöppel-Zeitfenster: τ_min ≤ 30 ms.
     *
     * @param tauMin Gemessenes Zeitfenster (ms)
     * @return true wenn τ_min ≤ 30 ms
     */
    fun validateTauMin(tauMin: Float): Boolean = tauMin <= TAU_MIN_MS + VALIDATION_TOLERANCE

    /**
     * Validiert die Phase-Locking-Value: PLV ≈ 0.973408.
     *
     * @param plv Gemessener PLV
     * @return true wenn |PLV - 0.973408| < tolerance
     */
    fun validatePLV(plv: Float): Boolean =
        abs(plv - PLV_HILBERT) < VALIDATION_TOLERANCE

    /**
     * Validiert die Reibungs-Baseline: W_base ≈ 0.7557.
     *
     * @param wBase Gemessene Baseline
     * @return true wenn |W_base - 0.7557| < tolerance
     */
    fun validateWBase(wBase: Float): Boolean =
        abs(wBase - W_BASE) < VALIDATION_TOLERANCE

    /**
     * Validiert die ERN-Schwelle: |ERN_max| < 1.5 μV.
     *
     * @param ernMax Gemessene maximale ERN-Amplitude (μV)
     * @return true wenn |ERN_max| < 1.5 μV
     */
    fun validateERN(ernMax: Float): Boolean =
        abs(ernMax) < ERN_THRESHOLD_MICROVOLT

    /**
     * Validiert die Von-Neumann-Entropie: S_vN ≈ ln(2).
     *
     * @param svn Gemessene Von-Neumann-Entropie
     * @return true wenn |S_vN - ln(2)| < tolerance
     */
    fun validateVonNeumannEntropy(svn: Float): Boolean =
        abs(svn - LN2) < VALIDATION_TOLERANCE

    /**
     * Validiert den Memory Energy Penalty: E_penalty ≈ 1.201301.
     *
     * @param ePenalty Gemessener Energy Penalty
     * @return true wenn |E_penalty - 1.201301| < tolerance
     */
    fun validateEnergyPenalty(ePenalty: Float): Boolean =
        abs(ePenalty - E_PENALTY) < VALIDATION_TOLERANCE

    /**
     * Validiert die Anderson-Higgs-Masse: m_mask ≈ 0.643047.
     *
     * @param mMask Gemessene Masse
     * @return true wenn |m_mask - 0.643047| < tolerance
     */
    fun validateMaskMass(mMask: Float): Boolean =
        abs(mMask - M_MASK) < VALIDATION_TOLERANCE

    /**
     * Validiert den kritischen Last-Horizont: Ω_krit = 5800 W·s.
     *
     * @param omegaKrit Gemessener kritischer Horizont
     * @return true wenn |Ω_krit - 5800| < tolerance
     */
    fun validateOmegaCritical(omegaKrit: Float): Boolean =
        abs(omegaKrit - OMEGA_CRITICAL) < VALIDATION_TOLERANCE * 100f

    // =========================================================================
    // KOGNITIVE KONSTANTEN
    // =========================================================================

    /** Biologische Quantisierungsgrenze: τ_min ≈ 30 ms */
    const val BIOLOGICAL_QUANTUM_LIMIT_MS: Float = 30.0f

    /** Kognitive Grundfrequenz: f_cog = 1/τ_min ≈ 33.3 Hz */
    const val COGNITIVE_BASE_FREQUENCY_HZ: Float = 33.3f

    /** Kognitive Temperatur (normiert): T_cog ∝ E_biol */
    const val COGNITIVE_TEMPERATURE: Float = 1.0f

    /** Kognitive Boltzmann-Konstante: k_cog = 1.0 */
    const val KB_COG: Float = 1.0f

    /** Kognitive Planck-Konstante: ℏ_cog = 0.5 */
    const val H_BAR_COG: Float = 0.5f

    /** Goldener Schnitt: φ = 1.61803398875... */
    const val PHI: Float = 1.61803398875f

    /** Euler-Zahl: e = 2.71828182846... */
    const val EULER_NUMBER: Float = 2.71828182846f

    // =========================================================================
    // SYSTEM-VALIDIERUNG
    // =========================================================================

    /**
     * Führt eine vollständige Systemvalidierung durch.
     *
     * Prüft alle Parameter gegen die empirischen Messwerte.
     *
     * @param plv Gemessener Phase-Locking-Value
     * @param wBase Gemessene Reibungs-Baseline
     * @param ernMax Gemessene ERN-Amplitude (μV)
     * @param svn Gemessene Von-Neumann-Entropie
     * @param ePenalty Gemessener Energy Penalty
     * @param mMask Gemessene Anderson-Higgs-Masse
     * @param omegaKrit Gemessener kritischer Last-Horizont
     * @return Validierungsbericht
     */
    fun validateSystem(
        plv: Float,
        wBase: Float,
        ernMax: Float,
        svn: Float,
        ePenalty: Float,
        mMask: Float,
        omegaKrit: Float
    ): ValidationReport {
        val checks = listOf(
            ValidationCheck("PLV_Hilbert", plv, PLV_HILBERT, validatePLV(plv)),
            ValidationCheck("W_base", wBase, W_BASE, validateWBase(wBase)),
            ValidationCheck("ERN_max", ernMax, ERN_THRESHOLD_MICROVOLT, validateERN(ernMax)),
            ValidationCheck("S_vN", svn, LN2, validateVonNeumannEntropy(svn)),
            ValidationCheck("E_penalty", ePenalty, E_PENALTY, validateEnergyPenalty(ePenalty)),
            ValidationCheck("m_mask", mMask, M_MASK, validateMaskMass(mMask)),
            ValidationCheck("Ω_krit", omegaKrit, OMEGA_CRITICAL.toFloat(), validateOmegaCritical(omegaKrit))
        )

        val passed = checks.count { it.passed }
        val total = checks.size
        val score = passed.toFloat() / total

        return ValidationReport(
            checks = checks,
            passed = passed,
            total = total,
            score = score,
            isValid = score >= 0.85f  // 85% der Checks müssen bestehen
        )
    }

    /**
     * Datenklasse für einen einzelnen Validierungs-Check.
     */
    data class ValidationCheck(
        val name: String,
        val measured: Float,
        val expected: Float,
        val passed: Boolean
    )

    /**
     * Datenklasse für den Validierungsbericht.
     */
    data class ValidationReport(
        val checks: List<ValidationCheck>,
        val passed: Int,
        val total: Int,
        val score: Float,
        val isValid: Boolean
    ) {
        override fun toString(): String {
            val sb = StringBuilder()
            sb.appendLine("=== EMPIRICAL VALIDATION REPORT ===")
            sb.appendLine("Score: ${"%.2f".format(score * 100)}% ($passed/$total passed)")
            sb.appendLine("Valid: $isValid")
            sb.appendLine()
            checks.forEach { check ->
                val status = if (check.passed) "✓ PASS" else "✗ FAIL"
                sb.appendLine("$status: ${check.name} = ${"%.4f".format(check.measured)} (expected: ${"%.4f".format(check.expected)})")
            }
            return sb.toString()
        }
    }
}

/** Vorkompilierte Instanz für deterministischen Zugriff */
val EmpiricalValidationMatrixInstance = EmpiricalValidationMatrix

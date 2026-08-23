package de.lifeos.core.social

import net.sqlcipher.database.SQLiteDatabase

/**
 * Relational Power Dynamics Engine — Bewertet die Machtasymmetrie in
 * sozialen Beziehungen und moduliert die Tonlage entsprechend.
 *
 * Dimensionen:
 * - powerAsymmetry: -1.0 (ich unterlegen) bis +1.0 (ich überlegen)
 * - emotionalBond: 0.0 (distanziert) bis 1.0 (eng verbunden)
 * - conflictTension: 0.0 (harmonisch) bis 1.0 (konfrontativ)
 *
 * FIXED:
 * - Precompiled Regex-Patterns
 * - SQL Injection Prevention via LIKE-Escaping
 */
data class RelationalVector(
    val powerAsymmetry: Float,
    val emotionalBond: Float,
    val conflictTension: Float
)

enum class PowerStrategy {
    ASSERTIVE_DIRECT,
    DIPLOMATIC_NEGOTIATION,
    DEFERENTIAL_COMPLIANCE,
    STRATEGIC_RESISTANCE,
    SUPPORTIVE_NURTURING,
    NEUTRAL_PROFESSIONAL
}

class RelationalPowerDynamicsEngine(private val vaultDb: SQLiteDatabase) {

    // SEV-2 Fix: Precompiled regex patterns
    private companion object {
        val AUTHORITY_PATTERN = Regex("jobcenter|arbeitsagentur|amt|behörde|finanzamt|gericht|polizei|stadt|gemeinde")
        val CREDITOR_PATTERN = Regex("inkasso|gläubiger|kreditanstalt|bank|fordert|mahnung")
        val LAWYER_PATTERN = Regex("anwalt|kanzlei|rechtsanwalt|vertretung")
        val EMPLOYER_PATTERN = Regex("arbeitgeber|chef|firma|unternehmen|hr|personal")
        val FAMILY_PATTERN = Regex("mama|papa|oma|opa|freund|freundin|bruder|schwester")
        val CONFLICT_PATTERN = Regex("(widerspruch|klage|mahnung|frist|ankündigung|sofort|dringend)")
        val MULTI_SPACE = Regex("\\s+")
        val HEDGING = Regex("vielleicht|könnte man|eventuell", RegexOption.IGNORE_CASE)
        val SUPPORTIVE = Regex("gerne|helfe|unterstütze", RegexOption.IGNORE_CASE)
    }

    /**
     * Bewertet die Beziehung zu einem Kommunikationspartner.
     */
    fun evaluateRelation(contactIdOrName: String): RelationalVector {
        val institutionalPower = detectInstitutionalPower(contactIdOrName)
        val vaultData = analyzeVaultCommunication(contactIdOrName)

        return RelationalVector(
            powerAsymmetry = (institutionalPower.powerAsymmetry * 0.6f + vaultData.powerAsymmetry * 0.4f)
                .coerceIn(-1.0f, 1.0f),
            emotionalBond = (institutionalPower.emotionalBond * 0.3f + vaultData.emotionalBond * 0.7f)
                .coerceIn(0.0f, 1.0f),
            conflictTension = (institutionalPower.conflictTension * 0.5f + vaultData.conflictTension * 0.5f)
                .coerceIn(0.0f, 1.0f)
        )
    }

    /**
     * Moduliert die Tonlage basierend auf dem RelationalVector.
     */
    fun modulateTone(vector: RelationalVector, baseText: String): String {
        val strategy = deriveStrategy(vector)
        return applyStrategy(strategy, baseText)
    }

    /**
     * Leitet die Kommunikationsstrategie aus dem Vektor ab.
     */
    fun deriveStrategy(vector: RelationalVector): PowerStrategy {
        return when {
            vector.conflictTension > 0.6f && vector.powerAsymmetry < -0.3f -> PowerStrategy.STRATEGIC_RESISTANCE
            vector.conflictTension > 0.6f && vector.powerAsymmetry > 0.3f -> PowerStrategy.ASSERTIVE_DIRECT
            vector.emotionalBond > 0.6f && vector.conflictTension < 0.3f -> PowerStrategy.SUPPORTIVE_NURTURING
            vector.powerAsymmetry < -0.4f && vector.conflictTension < 0.4f -> PowerStrategy.DEFERENTIAL_COMPLIANCE
            vector.powerAsymmetry > 0.4f && vector.conflictTension < 0.4f -> PowerStrategy.DIPLOMATIC_NEGOTIATION
            else -> PowerStrategy.NEUTRAL_PROFESSIONAL
        }
    }

    /**
     * Wendet die Strategie auf den Text an.
     */
    private fun applyStrategy(strategy: PowerStrategy, text: String): String {
        return when (strategy) {
            PowerStrategy.ASSERTIVE_DIRECT -> {
                val result = text.replace(HEDGING, "").replace(MULTI_SPACE, " ").trim()
                if (!result.contains("Ich fordere", ignoreCase = true) && !result.contains("Ich verlange", ignoreCase = true)) {
                    if (!result.endsWith(".") && !result.endsWith("!")) "$result." else result
                } else result
            }

            PowerStrategy.DIPLOMATIC_NEGOTIATION -> {
                if (!text.contains("vorschlag", ignoreCase = true)) {
                    "Ich schlage vor: ${text.trim()}"
                } else text
            }

            PowerStrategy.DEFERENTIAL_COMPLIANCE -> {
                val result = "Sehr geehrte/r, ${text.trim()}"
                if (!result.contains("Mit freundlichen Grüßen", ignoreCase = true)) {
                    "$result\n\nMit freundlichen Grüßen"
                } else result
            }

            PowerStrategy.STRATEGIC_RESISTANCE -> {
                if (!text.contains("Rechtsgrundlage", ignoreCase = true) && !text.contains("§", ignoreCase = true)) {
                    "${text.trim()}\n\nIch behalte mir vor, rechtliche Schritte prüfen zu lassen."
                } else text
            }

            PowerStrategy.SUPPORTIVE_NURTURING -> {
                if (!SUPPORTIVE.containsMatchIn(text)) {
                    "Gerne! ${text.trim()}"
                } else text
            }

            PowerStrategy.NEUTRAL_PROFESSIONAL -> {
                val result = text.trim().replaceFirstChar { it.uppercase() }
                if (!result.endsWith(".") && !result.endsWith("!") && !result.endsWith("?")) "$result." else result
            }
        }
    }

    // =========================================================================
    // INSTITUTIONELLE MACHTANALYSE
    // =========================================================================

    private fun detectInstitutionalPower(contactIdOrName: String): RelationalVector {
        val lower = contactIdOrName.lowercase()

        return when {
            AUTHORITY_PATTERN.containsMatchIn(lower) -> RelationalVector(-0.7f, 0.05f, 0.3f)
            CREDITOR_PATTERN.containsMatchIn(lower) -> RelationalVector(-0.5f, 0.05f, 0.6f)
            LAWYER_PATTERN.containsMatchIn(lower) -> RelationalVector(-0.4f, 0.1f, 0.4f)
            EMPLOYER_PATTERN.containsMatchIn(lower) -> RelationalVector(-0.6f, 0.2f, 0.3f)
            FAMILY_PATTERN.containsMatchIn(lower) -> RelationalVector(0.0f, 0.8f, 0.1f)
            else -> RelationalVector(0.0f, 0.3f, 0.2f)
        }
    }

    // =========================================================================
    // TRESOR-KOMMUNIKATIONSANALYSE
    // =========================================================================

    private fun analyzeVaultCommunication(contactIdOrName: String): RelationalVector {
        var totalInteractions = 0
        var outboundCount = 0
        var inboundCount = 0
        var avgFriction = 0.0f
        var conflictKeywords = 0

        // SEV-3 Fix: Escape LIKE wildcards to prevent SQL injection pattern manipulation
        val escapedContact = contactIdOrName
            .replace("\\", "\\\\")
            .replace("%", "\\%")
            .replace("_", "\\_")

        runCatching {
            vaultDb.rawQuery(
                """
                SELECT direction, friction_delta, payload 
                FROM communication_events 
                WHERE contact_id = ? OR contact_id LIKE ? ESCAPE '\\'
                ORDER BY timestamp_epoch DESC LIMIT 100
                """.trimIndent(),
                arrayOf(escapedContact, "%$escapedContact%")
            ).use { cursor ->
                while (cursor.moveToNext()) {
                    totalInteractions++
                    val direction = cursor.getString(0)
                    if (direction == "OUTBOUND") outboundCount++ else inboundCount++
                    avgFriction += cursor.getFloat(1)
                    val payload = cursor.getString(2) ?: ""
                    if (CONFLICT_PATTERN.containsMatchIn(payload)) {
                        conflictKeywords++
                    }
                }
            }
        }

        if (totalInteractions == 0) {
            return RelationalVector(0.0f, 0.3f, 0.2f)
        }

        avgFriction /= totalInteractions

        val powerAsymmetry = (outboundCount - inboundCount).toFloat() / totalInteractions
        val emotionalBond = (totalInteractions / 50.0f).coerceIn(0.0f, 1.0f)
        val conflictTension = ((avgFriction * 0.5f) + (conflictKeywords.toFloat() / totalInteractions * 0.5f))
            .coerceIn(0.0f, 1.0f)

        return RelationalVector(
            powerAsymmetry = powerAsymmetry.coerceIn(-1.0f, 1.0f),
            emotionalBond = emotionalBond,
            conflictTension = conflictTension
        )
    }
}

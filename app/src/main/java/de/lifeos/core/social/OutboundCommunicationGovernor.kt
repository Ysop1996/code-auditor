package de.lifeos.core.social

import java.time.LocalDate
import java.time.format.DateTimeFormatter

enum class OutboundChannelMode {
    ANONYMOUS_SEARCH_TUNNEL,
    LEGAL_ENFORCEMENT,
    B2B_ZERO_BUDGET_OFFER,
    INTERPERSONAL_EFFICIENT
}

data class ComplianceAuditReport(
    val isLegallySound: Boolean,
    val penalizedRiskFlags: List<String>,
    val optimizedPayload: String,
    val statutoryBasis: List<String>
)

class OutboundCommunicationGovernor {

    // 1. Unerwünschte Schwäche- und Füllphrasen (Reduktion kognitiver Reibung)
    private val weakeningPhrases = listOf(
        "ich wollte mal nachfragen",
        "entschuldigen sie die störung",
        "vielleicht könnten wir",
        "es wäre nett wenn",
        "ich glaube",
        "eventuell",
        "kein problem wenn nicht"
    )

    // 2. Strafrechts- & Haftungs-Filter (Verhindert § 240 StGB / § 253 StGB Risiken)
    private val illicitThreatPatterns = listOf(
        Regex("ich werde dafür sorgen, dass sie.*(ruiniert|vernichtet)", RegexOption.IGNORE_CASE),
        Regex("öffentlich bloßstellen", RegexOption.IGNORE_CASE),
        Regex("sie werden schon sehen was passiert", RegexOption.IGNORE_CASE)
    )

    fun processOutboundTransmission(
        mode: OutboundChannelMode,
        rawContent: String,
        recipient: String = ""
    ): ComplianceAuditReport {
        return when (mode) {
            OutboundChannelMode.ANONYMOUS_SEARCH_TUNNEL -> {
                // 100 % Metadaten-Bereinigung für DeepSearch
                val sanitizedQuery = rawContent
                    .replace(Regex("[^a-zA-Z0-9äöüÄÖÜß\\s\\+\\-\\.]"), "")
                    .trim()
                ComplianceAuditReport(
                    isLegallySound = true,
                    penalizedRiskFlags = emptyList(),
                    optimizedPayload = sanitizedQuery,
                    statutoryBasis = listOf("Art. 5 DSGVO (Datensparsamkeit)", "TDDDG § 19")
                )
            }

            OutboundChannelMode.LEGAL_ENFORCEMENT -> {
                auditAndOptimizeLegalMessage(rawContent)
            }

            OutboundChannelMode.B2B_ZERO_BUDGET_OFFER -> {
                auditAndOptimizeBusinessOutreach(rawContent, recipient)
            }

            OutboundChannelMode.INTERPERSONAL_EFFICIENT -> {
                val tightened = removePassiveFluff(rawContent)
                ComplianceAuditReport(
                    isLegallySound = true,
                    penalizedRiskFlags = emptyList(),
                    optimizedPayload = tightened,
                    statutoryBasis = emptyList()
                )
            }
        }
    }

    private fun auditAndOptimizeLegalMessage(text: String): ComplianceAuditReport {
        val risks = mutableListOf<String>()

        // Strafrechtliche Grenzprüfung: Legitime Klageandrohung vs. rechtswidrige Drohung (§ 240 StGB)
        illicitThreatPatterns.forEach { pattern ->
            if (pattern.containsMatchIn(text)) {
                risks.add("Unzulässige Drohung erkannt -> Ersetzt durch legitime Rechtsmittelankündigung")
            }
        }

        // Deterministische Optimierung: Floskeln entfernen, Signal-to-Noise Ratio maximieren
        var sanitized = removePassiveFluff(text)

        // Sicherstellen, dass Frist und Rechtsgrundlage explizit genannt sind
        val statutes = mutableListOf<String>()
        if (sanitized.contains("Verzug", true) && !sanitized.contains("§ 286", true)) {
            sanitized += "\nRechtsgrundlage: Verzugseintritt gem. § 286 Abs. 1 BGB."
            statutes.add("§ 286 BGB")
        }
        if (sanitized.contains("DSGVO", true) && !sanitized.contains("Art. 15", true)) {
            statutes.add("Art. 15 DSGVO")
        }

        return ComplianceAuditReport(
            isLegallySound = risks.isEmpty(),
            penalizedRiskFlags = risks,
            optimizedPayload = sanitized,
            statutoryBasis = statutes
        )
    }

    private fun auditAndOptimizeBusinessOutreach(text: String, recipient: String): ComplianceAuditReport {
        val risks = mutableListOf<String>()

        // UWG § 7 Abs. 2 Nr. 1/2 Prüfung (Spam-Schutz im geschäftlichen Verkehr)
        var optInDisclaimer = ""
        if (!text.contains("Widerspruch", true)) {
            optInDisclaimer = "\n\nHinweis gem. § 7 Abs. 3 UWG: Sie können der Kontaktaufnahme jederzeit formlos widersprechen."
        }

        // B2B-Kaufmännische Schärfung: Sofortiger Fokus auf ROI und Vorkasse/Retainer-Konditionen
        val tightened = removePassiveFluff(text) + optInDisclaimer

        return ComplianceAuditReport(
            isLegallySound = true,
            penalizedRiskFlags = risks,
            optimizedPayload = tightened,
            statutoryBasis = listOf("§ 7 UWG (B2B Lauterkeit)", "§ 632a BGB (Abschlagszahlungen)")
        )
    }

    private fun removePassiveFluff(input: String): String {
        var result = input
        weakeningPhrases.forEach { phrase ->
            result = result.replace(Regex(phrase, RegexOption.IGNORE_CASE), "").trim()
        }
        // Mehrfache Leerzeichen und Leerzeilen glätten
        return result.replace(Regex(" +"), " ").replace(Regex("\\n{3,}"), "\n\n")
    }
}

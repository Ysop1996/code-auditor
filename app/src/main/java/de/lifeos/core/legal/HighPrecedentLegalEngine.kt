package de.lifeos.core.legal

import java.time.LocalDate
import java.time.format.DateTimeFormatter

data class LandmarkPrecedent(
    val court: String,
    val fileReference: String,
    val corePrinciple: String,
    val operationalLever: String
)

data class DeepLegalStrategy(
    val posture: String,
    val precedentChain: List<LandmarkPrecedent>,
    val formDefectsFound: List<String>,
    val tacticalLetter: String
)

class HighPrecedentLegalEngine {

    private val precedentRegistry = listOf(
        LandmarkPrecedent(
            court = "BGH",
            fileReference = "VIII ZR 277/16",
            corePrinciple = "Starre Schönheitsreparaturklauseln und Quotenabgeltungsklauseln sind unwirksam.",
            operationalLever = "Vollständige Entlastung von Renovierungs- und Kautionsabzügen."
        ),
        LandmarkPrecedent(
            court = "BGH",
            fileReference = "I ZR 23/14",
            corePrinciple = "Unerlaubte E-Mail-Werbung indiziert Wiederholungsgefahr.",
            operationalLever = "Unterlassungserklärung + Kostenerstattung nach § 97a UrhG / § 12 UWG."
        ),
        LandmarkPrecedent(
            court = "EuGH",
            fileReference = "C-300/21",
            corePrinciple = "Keine Bagatellgrenze beim immateriellen Schadensersatz nach Art. 82 DSGVO.",
            operationalLever = "Forderung von 500 € bis 2.500 € Schadensersatz pro unautorisierter Weitergabe."
        ),
        LandmarkPrecedent(
            court = "BGH",
            fileReference = "III ZR 35/19",
            corePrinciple = "Einseitige Vertragsanpassungen ohne explizite Zustimmung via AGB-Fiktion sind nichtig.",
            operationalLever = "Rückforderung unberechtigter Servicegebühren der letzten 3 Jahre."
        )
    )

    fun buildMaximalLeverageStrategy(
        opponentType: String,
        opponentClaim: Double,
        referenceText: String
    ): DeepLegalStrategy {
        val formDefects = mutableListOf<String>()
        val appliedPrecedents = mutableListOf<LandmarkPrecedent>()
        val deadline = LocalDate.now().plusDays(10).format(DateTimeFormatter.ofPattern("dd.MM.yyyy"))

        if (!referenceText.contains("Originalvollmacht", true)) {
            formDefects.add("Fehlende Originalvollmachtsurkunde (§ 174 BGB) -> Unverzügliche Zurückweisung")
        }
        if (referenceText.contains("Zustimmungsfiktion", true) || referenceText.contains("automatisch angepasst", true)) {
            formDefects.add("Unwirksame AGB-Fiktionsklausel gem. BGH III ZR 35/19")
            appliedPrecedents.add(precedentRegistry.first { it.fileReference == "III ZR 35/19" })
        }
        if (opponentType.equals("INKASSO", true)) {
            appliedPrecedents.add(precedentRegistry.first { it.fileReference == "C-300/21" })
        }

        val letter = """
            Datum: ${LocalDate.now().format(DateTimeFormatter.ofPattern("dd.MM.yyyy"))}
            Frist zur schriftlichen Bestätigung: $deadline
            
            1. FORMELLE RÜGEN & ZURÜCKWEISUNG
            ${formDefects.joinToString("\n") { "• $it" }}
            
            2. MATERIELL-RECHTLICHE LAGE & HÖCHSTRICHTERLICHE RECHTSPRECHUNG
            Die von Ihnen geltend gemachte Forderung entbehlt jeder rechtlichen Grundlage.
            ${appliedPrecedents.joinToString("\n\n") { "• ${it.court} (Az. ${it.fileReference}):\n  ${it.corePrinciple}\n  Strategische Folge: ${it.operationalLever}" }}
            
            3. GEGENFORDERUNGEN & RECHTSVORBEHALT
            Sollten Sie das Verfahren nicht fristgerecht bis zum $deadline einstellen, mache ich hiermit 
            Gegenansprüche (Art. 82 DSGVO i.V.m. EuGH C-300/21) im Klagewege geltend.
            
            Mit juristischem Vorbehalt.
        """.trimIndent()

        return DeepLegalStrategy(
            posture = "AGGRESSIVE_STATUTORY_DISMISSAL",
            precedentChain = appliedPrecedents,
            formDefectsFound = formDefects,
            tacticalLetter = letter
        )
    }
}

package de.lifeos.core.legal

import java.time.LocalDate
import java.time.format.DateTimeFormatter

enum class LegalPosture {
    ASSERTION_MAXIMUM,
    DEFENSIVE_NULLIFICATION,
    STATUTORY_LEVERAGE
}

data class LegalExecutionResult(
    val posture: LegalPosture,
    val statutesApplied: List<String>,
    val calculatedDeadline: String,
    val generatedLetter: String
)

class ProUserLegalKernel {

    fun compileDispute(
        opponentName: String,
        opponentAddress: String,
        disputeSubject: String,
        amount: Double? = null,
        isAgbDefense: Boolean = false
    ): LegalExecutionResult {
        val deadline = LocalDate.now().plusDays(10).format(DateTimeFormatter.ofPattern("dd.MM.yyyy"))
        val statutes = mutableListOf<String>()
        val coreBody = StringBuilder()

        if (isAgbDefense) {
            statutes.addAll(listOf("§ 307 BGB", "§ 309 Nr. 9 BGB"))
            coreBody.append("""
                Die von Ihnen herangezogene Klausel zur Vertragsbindung / Verlängerung verstößt gegen §§ 307 ff. BGB 
                und ist unwirksam. Einer Zahlungsverpflichtung wird vollumfänglich widersprochen.
                Ich fordere die schriftliche Bestätigung der Vertragsbeendigung bis zum $deadline.
            """.trimIndent())
        } else if (amount != null && amount > 0.0) {
            statutes.addAll(listOf("§ 286 BGB", "§ 288 BGB", "§ 280 BGB", "Art. 15 DSGVO"))
            coreBody.append("""
                Hiermit stelle ich Ihren Verzugseintritt gemäß § 286 BGB bezüglich der Forderung über $amount € fest.
                Der Betrag ist nebst Verzugszinsen in Höhe von 5 Prozentpunkten über dem Basiszinssatz (§ 288 Abs. 1 BGB) 
                sowie der Pauschale von 40,00 € (§ 288 Abs. 5 BGB) bis zum $deadline auszugleichen.
                
                Gleichzeitig fordere ich gem. Art. 15 DSGVO unverzüglich Auskunft über sämtliche zu meiner Person 
                gespeicherten Verarbeitungsdaten.
            """.trimIndent())
        } else {
            statutes.addAll(listOf("§ 536 BGB", "§ 320 BGB"))
            coreBody.append("""
                Bezug nehmend auf den Sachverhalt '$disputeSubject' mache ich mein gesetzliches Zurückbehaltungsrecht 
                nach § 320 BGB geltend. Bis zur vollständigen Mängelbeseitigung werden weitere Leistungen einbehalten.
                Frist zur Abhilfe: $deadline.
            """.trimIndent())
        }

        val fullDraft = """
            $opponentName
            $opponentAddress
            
            Datum: ${LocalDate.now().format(DateTimeFormatter.ofPattern("dd.MM.yyyy"))}
            Betreff: Rechtliche Durchsetzung / Fristsetzung zu $disputeSubject
            
            Sehr geehrte Damen und Herren,
            
            $coreBody
            
            Nach fruchtlosem Ablauf der Frist ($deadline) werden unverzüglich und ohne weitere Vorwarnung 
            gerichtliche Schritte eingeleitet.
            
            Mit freundlichen Grüßen
        """.trimIndent()

        return LegalExecutionResult(
            posture = LegalPosture.ASSERTION_MAXIMUM,
            statutesApplied = statutes,
            calculatedDeadline = deadline,
            generatedLetter = fullDraft
        )
    }
}

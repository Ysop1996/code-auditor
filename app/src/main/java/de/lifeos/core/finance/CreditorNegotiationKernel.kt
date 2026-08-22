package de.lifeos.core.finance

import java.time.LocalDate
import java.time.format.DateTimeFormatter

enum class DebtStrategy {
    DISPUTE_FEES,
    STATUTE_BARRED_DEFENSE,
    SETTLEMENT_OFFER,
    DEMAND_PROOF_ART15
}

data class DebtNegotiationDraft(
    val creditorId: String,
    val subject: String,
    val deadline: String,
    val letterBody: String,
    val statutoryReferences: List<String>
)

class CreditorNegotiationKernel {

    fun generateCreditorLetter(
        creditorName: String,
        creditorAddress: String,
        fileReference: String,
        totalClaim: Double,
        strategy: DebtStrategy,
        offeredRate: Double? = null,
        oneTimeSettlementAmount: Double? = null
    ): DebtNegotiationDraft {
        val deadline = LocalDate.now().plusDays(14).format(DateTimeFormatter.ofPattern("dd.MM.yyyy"))
        val today = LocalDate.now().format(DateTimeFormatter.ofPattern("dd.MM.yyyy"))
        val statutes = mutableListOf<String>()
        val bodyBuilder = StringBuilder()

        when (strategy) {
            DebtStrategy.DISPUTE_FEES -> {
                statutes.addAll(listOf("§ 13e RDG", "§ 254 BGB", "§ 367 Abs. 2 BGB"))
                bodyBuilder.append("""
                    In vorbezeichneter Angelegenheit weise ich die geltend gemachten Inkassogebühren und Nebenforderungen 
                    als unbegründet zurück. Gemäß § 13e RDG i.V.m. § 254 BGB sind überhöhte Inkassovergütungen nicht erstattungsfähig.
                    
                    Ich fordere Sie hiermit auf, bis zum $deadline eine nachvollziehbare, aufgeschlüsselte Forderungsaufstellung vorzulegen.
                    Etwaige künftige Zahlungen erfolgen ausdrücklich mit der Tilgungsbestimmung (§ 367 Abs. 2 BGB) 
                    ausschließlich auf die reine Hauptforderung.
                """.trimIndent())
            }
            DebtStrategy.STATUTE_BARRED_DEFENSE -> {
                statutes.addAll(listOf("§ 195 BGB", "§ 199 BGB", "§ 214 Abs. 1 BGB"))
                bodyBuilder.append("""
                    Bezüglich der von Ihnen geltend gemachten Forderung erhebe ich hiermit ausdrücklich die 
                    EINREDE DER VERJÄHRUNG gemäß § 214 Abs. 1 BGB i.V.m. § 195 BGB.
                    Die regelmäßige Verjährungsfrist ist abgelaufen. Zahlungen werden dauerhaft verweigert. 
                    Bestätigen Sie mir die Erledigung bis zum $deadline.
                """.trimIndent())
            }
            DebtStrategy.SETTLEMENT_OFFER -> {
                statutes.addAll(listOf("§ 779 BGB", "§ 367 Abs. 2 BGB"))
                if (oneTimeSettlementAmount != null) {
                    bodyBuilder.append("""
                        Zur endgültigen Bereinigung unterbreite ich folgenden Vergleichsvorschlag (§ 779 BGB):
                        Gegen Zahlung eines einmaligen Abgeltungsbetrages von ${"%.2f".format(oneTimeSettlementAmount)} € 
                        erlassen Sie mir die Restforderung nebst Zinsen und Kosten. Frist: $deadline.
                    """.trimIndent())
                } else if (offeredRate != null) {
                    bodyBuilder.append("""
                        Ich biete monatliche Raten von ${"%.2f".format(offeredRate)} € an, zahlbar zum 1. eines Monats, 
                        unter der zwingenden Bedingung des sofortigen Zins- und Kostenstopps. Verrechnung erfolgt nach § 367 Abs. 2 BGB.
                    """.trimIndent())
                }
            }
            DebtStrategy.DEMAND_PROOF_ART15 -> {
                statutes.addAll(listOf("§ 174 BGB", "Art. 15 DSGVO"))
                bodyBuilder.append("""
                    Ich rüge das Fehlen einer Originalvollmacht gemäß § 174 BGB.
                    Des Weiteren mache ich mein Recht auf Auskunft gemäß Art. 15 DSGVO geltend. Stellen Sie mir 
                    binnen gesetzlicher Frist (bis zum $deadline) eine vollständige Datenaufstellung zur Verfügung.
                """.trimIndent())
            }
        }

        val fullText = """
            $creditorName
            $creditorAddress
            
            Datum: $today
            Aktenzeichen / Referenz: $fileReference
            Forderungssumme: ${"%.2f".format(totalClaim)} €
            
            Sehr geehrte Damen und Herren,
            
            $bodyBuilder
            
            Mit freundlichen Grüßen
        """.trimIndent()

        return DebtNegotiationDraft(
            creditorId = creditorName,
            subject = "Aktenzeichen $fileReference - Rechtliche Stellungnahme",
            deadline = deadline,
            letterBody = fullText,
            statutoryReferences = statutes
        )
    }
}

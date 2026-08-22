package de.lifeos.core.legal

object BitmaskLegalDecisionTree {
    const val FLAG_CLAIM_OLDER_3_YEARS      = 1 shl 0
    const val FLAG_NO_ORIGINAL_POWER_OF_ATT = 1 shl 1
    const val FLAG_EXCESSIVE_COLLECTION_FEE = 1 shl 2
    const val FLAG_CONSUMER_STATUS          = 1 shl 3
    const val FLAG_AUTOMATIC_RENEWAL_CLAUSE = 1 shl 4
    const val FLAG_DEBTOR_DEFAULT_ESTABLISH = 1 shl 5

    data class DefenseInstruction(
        val maskMatch: Int,
        val primaryStatute: String,
        val proceduralAction: String,
        val instantDismissal: Boolean
    )

    private val decisionMatrix = listOf(
        DefenseInstruction(
            maskMatch = FLAG_CLAIM_OLDER_3_YEARS,
            primaryStatute = "§ 214 Abs. 1 i.V.m. § 195 BGB",
            proceduralAction = "Einrede der Verjährung erheben. Zahlungsverweigerung erklären.",
            instantDismissal = true
        ),
        DefenseInstruction(
            maskMatch = FLAG_NO_ORIGINAL_POWER_OF_ATT,
            primaryStatute = "§ 174 Satz 1 BGB",
            proceduralAction = "Forderungsschreiben mangels Vollmachtsurkunde unverzüglich zurückweisen.",
            instantDismissal = false
        ),
        DefenseInstruction(
            maskMatch = FLAG_EXCESSIVE_COLLECTION_FEE or FLAG_CONSUMER_STATUS,
            primaryStatute = "§ 13e RDG i.V.m. § 254 BGB",
            proceduralAction = "Inkassogebühren auf Basisgebühr kappen. Hauptforderung nach § 367 Abs. 2 BGB tilgen.",
            instantDismissal = false
        ),
        DefenseInstruction(
            maskMatch = FLAG_AUTOMATIC_RENEWAL_CLAUSE or FLAG_CONSUMER_STATUS,
            primaryStatute = "§ 309 Nr. 9 lit. b BGB",
            proceduralAction = "Vertragsverlängerung wegen AGB-Nichtigkeit widersprechen. Kündigung bestätigen.",
            instantDismissal = true
        )
    )

    fun evaluateBitmask(factMask: Int): List<DefenseInstruction> {
        return decisionMatrix.filter { (factMask and it.maskMatch) == it.maskMatch }
    }
}

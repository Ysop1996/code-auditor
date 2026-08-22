package de.lifeos.core.cognition

import de.lifeos.core.field.DeterministicFieldEngine

data class InformationPrimitive(
    val key: String,
    val isAvailableLocally: Boolean,
    val payload: String? = null
)

data class PartitionResult(
    val canSynthesizeLocally: Boolean,
    val synthesizedData: String?,
    val missingPrimitives: List<String>
)

class InformationPartitionEngine(
    private val fieldEngine: DeterministicFieldEngine,
    private val localKnowledgeMap: Map<String, String>
) {
    private val decompositionRules = mapOf(
        "VERTRAGS_KUENDIGUNG" to listOf("VERTRAGS_START", "BGB_§309", "VERTRAGS_TYP"),
        "STEUER_VORSTEUER" to listOf("BETRAG_BRUTTO", "UST_SATZ", "UST_ID_STATUS"),
        "TRANSIT_ROUTE" to listOf("ABFAHRTSZEIT", "DISTANZ_KM", "SPEED_PROFILE")
    )

    fun evaluateInformationDeficit(targetConcept: String): PartitionResult {
        val required = decompositionRules[targetConcept]
            ?: return PartitionResult(canSynthesizeLocally = false, synthesizedData = null, missingPrimitives = listOf(targetConcept))

        val resolved = mutableListOf<InformationPrimitive>()
        val missing = mutableListOf<String>()

        for (key in required) {
            if (localKnowledgeMap.containsKey(key)) {
                resolved.add(InformationPrimitive(key, true, localKnowledgeMap[key]))
            } else {
                resolved.add(InformationPrimitive(key, false, null))
                missing.add(key)
            }
        }

        if (missing.isEmpty()) {
            val synthesis = resolved.associate { it.key to (it.payload ?: "") }
            val output = "Lokal synthetisiert: [${synthesis.entries.joinToString(" | ") { "${it.key}: ${it.value}" }}]"
            return PartitionResult(canSynthesizeLocally = true, synthesizedData = output, missingPrimitives = emptyList())
        }

        return PartitionResult(canSynthesizeLocally = false, synthesizedData = null, missingPrimitives = missing)
    }
}

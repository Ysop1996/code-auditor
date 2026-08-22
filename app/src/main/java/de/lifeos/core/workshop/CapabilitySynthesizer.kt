package de.lifeos.core.workshop

import de.lifeos.core.field.AttractorNode
import de.lifeos.core.field.DeterministicFieldEngine
import de.lifeos.core.field.PhaseVector

data class SynthesizedTool(
    val toolName: String,
    val executionLambda: (Map<String, String>) -> String
)

class CapabilitySynthesizer(
    private val fieldEngine: DeterministicFieldEngine
) {
    private val activeToolRegistry = mutableMapOf<String, SynthesizedTool>()

    fun synthesizeToolForRequirement(requirement: String): SynthesizedTool {
        val tool = when {
            requirement.contains("CSV_EXTRACT", true) -> {
                SynthesizedTool("CsvPrimitiveExtractor") { inputs ->
                    val raw = inputs["raw"] ?: ""
                    raw.lines().filter { it.contains(",") }.joinToString("\n") { line ->
                        line.split(",").take(3).joinToString(" | ")
                    }
                }
            }
            requirement.contains("TAX_CALC", true) -> {
                SynthesizedTool("DeterministicTaxDeductor") { inputs ->
                    val gross = inputs["gross"]?.toDoubleOrNull() ?: 0.0
                    val vat = gross * (19.0 / 119.0)
                    "Netto: ${"%.2f".format(gross - vat)} € | USt (19%): ${"%.2f".format(vat)} €"
                }
            }
            else -> {
                SynthesizedTool("PassThroughIdentity") { inputs ->
                    inputs.values.joinToString(";")
                }
            }
        }

        activeToolRegistry[requirement] = tool

        fieldEngine.registerNode(
            AttractorNode(
                id = "TOOL_${tool.toolName}",
                payload = requirement,
                position = PhaseVector(FloatArray(32) { 0.1f }).normalize(),
                mass = 3.0f,
                isTerminal = true
            )
        )

        return tool
    }

    fun getTool(name: String): SynthesizedTool? = activeToolRegistry[name]
}

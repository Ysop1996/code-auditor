package de.lifeos.core.field

import de.lifeos.core.social.ChatMessage
import de.lifeos.core.social.PersonalityStylingEngine
import de.lifeos.core.social.StylingDomain
import net.sqlcipher.database.SQLiteDatabase
import kotlin.math.sqrt

sealed class KineticDischarge {
    data class ExecutableAction(val actionType: String, val payload: String, val targetEntity: String) : KineticDischarge()
    data class LinguisticImpulse(val plainText: String, val momentum: Float) : KineticDischarge()
    data class DualDischarge(val speech: String, val executableAction: ExecutableAction) : KineticDischarge()
}

class KineticStateSpaceOperator(
    private val vaultDb: SQLiteDatabase,
    private val fieldEngine: DeterministicFieldEngine,
    private val stylingEngine: PersonalityStylingEngine
) {

    /**
     * Wendet den Operator Ô auf das Eingabesignal an und transformiert
     * potenziellen Informationsstaudruck in kinetische Energie.
     */
    fun applyOperator(stimulusInput: String): KineticDischarge {
        return try {
            applyOperatorInternal(stimulusInput)
        } catch (e: Exception) {
            KineticDischarge.LinguisticImpulse(
                "Feldoperator temporär nicht verfügbar. Fallback-Antwort: System stabil, kein kinetischer Impuls erforderlich.",
                0.0f
            )
        }
    }

    private fun applyOperatorInternal(stimulusInput: String): KineticDischarge {
        // 1. Phasenvektor berechnen & Gradient bestimmen
        val stimulus = PhaseVector(FloatArray(32) { i ->
            val hash = stimulusInput.hashCode()
            ((hash shr (i % 16)) and 0xFF) / 255.0f * 2.0f - 1.0f
        }).normalize()

        val activeAttractors = fieldEngine.executeTrajectory(stimulus)
        val primaryAttractor = activeAttractors.firstOrNull()

        // 2. Potenzialberechnung: U = W * rho
        val potentialU = fieldEngine.currentRho * 1.5f
        val mass = primaryAttractor?.mass ?: 1.0f

        // 3. Kinetische Impulsableitung: p = sqrt(2 * m * U)
        val kineticMomentum = sqrt(2.0f * mass * potentialU).coerceAtLeast(0.1f)

        // 4. Operator-Zweig: Handlungsfähige Transformation
        return when {
            // Hoher Staudruck + Aktenbezug -> Kinetische Sofort-Aktion
            isCriticalInterventionRequired(stimulusInput, potentialU) -> {
                val action = synthesizeKineticAction(stimulusInput, primaryAttractor)
                val speech = stylingEngine.synthesizeInUserVoice(
                    "Staudruck erkannt (U=${"%.2f".format(potentialU)}). Handlung vorbereitet: ${action.actionType}.",
                    StylingDomain.BUSINESS_ASSERTIVE
                )
                KineticDischarge.DualDischarge(speech, action)
            }

            // Personenbezogene Informationsabfrage -> Kondensierter Sprachimpuls
            isEntityQuery(stimulusInput) -> {
                val resolvedSpeech = collapseEntityPotentialToSpeech(stimulusInput, kineticMomentum)
                KineticDischarge.LinguisticImpulse(resolvedSpeech, kineticMomentum)
            }

            // Grundzustand -> Seinsmodus-Resonanz
            else -> {
                val calmSpeech = stylingEngine.synthesizeInUserVoice(
                    "Feld kohärent. Kein kinetischer Impuls erforderlich. W(t) ≤ 1.0.",
                    StylingDomain.PEER_CASUAL
                )
                KineticDischarge.LinguisticImpulse(calmSpeech, 0.0f)
            }
        }
    }

    private fun isCriticalInterventionRequired(input: String, potential: Float): Boolean {
        return potential > 1.2f || input.contains(Regex("(jobcenter|widerspruch|sanktion|frist|mahnung)", RegexOption.IGNORE_CASE))
    }

    private fun isEntityQuery(input: String): Boolean {
        return input.contains(Regex("(neues von|status|wer|wo|was macht|info zu|stand)", RegexOption.IGNORE_CASE))
    }

    private fun synthesizeKineticAction(input: String, attractor: AttractorNode?): KineticDischarge.ExecutableAction {
        val target = attractor?.id ?: "SYSTEM_CORE"
        return when {
            input.contains("widerspruch", true) || input.contains("sanktion", true) -> {
                KineticDischarge.ExecutableAction(
                    actionType = "RECHTS_SCHRIFTSATZ_DISPATCH",
                    payload = "Widerspruch gem. § 84 SGG / § 86b SGG zur Fristwahrung",
                    targetEntity = target
                )
            }
            else -> {
                KineticDischarge.ExecutableAction(
                    actionType = "VAULT_DELTA_SYNC",
                    payload = "Führe atomare Konsolidierung durch",
                    targetEntity = target
                )
            }
        }
    }

    private fun collapseEntityPotentialToSpeech(input: String, momentum: Float): String {
        val cleanName = input
            .replace(Regex("(gibts|gibt's|was|neues|von|status|zu|über)", RegexOption.IGNORE_CASE), "")
            .replace("?", "")
            .trim()

        // Escape LIKE wildcards to prevent unintended pattern matching
        val escapedName = cleanName.lowercase().replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")
        val likePattern = "%$escapedName%"

        val cursor = vaultDb.rawQuery(
            "SELECT payload, timestamp FROM communication_events WHERE lower(sender) LIKE ? ESCAPE '\\' ORDER BY timestamp DESC LIMIT 1",
            arrayOf(likePattern)
        )

        var lastEvent: String? = null
        cursor.use {
            if (it.moveToFirst()) lastEvent = it.getString(0)
        }

        return if (lastEvent != null) {
            "Lage zu $cleanName: Letzte Meldung liegt vor (\"${lastEvent?.take(80)}...\"). Impuls p=${"%.2f".format(momentum)} stabil. Kein Gegensteuerungsbedarf."
        } else {
            "Zu $cleanName liegt kein Reibungspunkt im Feld. Potenzial im Ruhezustand."
        }
    }
}

package de.lifeos.core.sensory

import de.lifeos.core.field.DeterministicFieldEngine
import de.lifeos.core.field.PhaseVector
import java.security.MessageDigest

data class SensoryImpulse(
    val modality: SensoryModality,
    val sourceApp: String,
    val rawContent: String,
    val urgencyScore: Float,
    val detectedEntities: List<String>,
    val timestamp: Long = System.currentTimeMillis()
)

enum class SensoryModality {
    SCREEN_SURFACE,       // Aktiver Bildschirminhalt (Lesen, Tippen, Formulare)
    INCOMING_NOTIFICATION,// E-Mail, WhatsApp, SMS
    DOCUMENT_INGESTION    // Neu heruntergeladene PDF/DOCX-Dateien
}

object PerceptualSensoryHub {

    private var latestSensoryContext: SensoryImpulse? = null
    private var lastScreenHash: String = ""

    /**
     * Registriert Bildschirminhalte außerhalb der Life-OS App (Latenz < 2 ms)
     */
    fun onScreenPerception(packageName: String, visibleText: String, fieldEngine: DeterministicFieldEngine) {
        if (visibleText.length < 8) return

        val hash = sha256(visibleText)
        if (hash == lastScreenHash) return
        lastScreenHash = hash

        val urgency = evaluateUrgency(visibleText)
        val entities = extractNamedEntities(visibleText)

        val impulse = SensoryImpulse(
            modality = SensoryModality.SCREEN_SURFACE,
            sourceApp = packageName,
            rawContent = visibleText,
            urgencyScore = urgency,
            detectedEntities = entities
        )

        latestSensoryContext = impulse
        injectSensoryPotentialIntoField(impulse, fieldEngine)
    }

    /**
     * Registriert eingehende E-Mails, Nachrichten oder Bescheid-Pings
     */
    fun onNotificationPerception(sourcePackage: String, title: String, text: String, fieldEngine: DeterministicFieldEngine) {
        val combined = "$title: $text"
        val urgency = evaluateUrgency(combined)
        val entities = extractNamedEntities(combined)

        val impulse = SensoryImpulse(
            modality = SensoryModality.INCOMING_NOTIFICATION,
            sourceApp = sourcePackage,
            rawContent = combined,
            urgencyScore = urgency,
            detectedEntities = entities
        )

        latestSensoryContext = impulse
        injectSensoryPotentialIntoField(impulse, fieldEngine)
    }

    /**
     * Registriert neu heruntergeladene Dokumente
     */
    fun onDocumentIngestion(fileName: String, fileType: String, fieldEngine: DeterministicFieldEngine) {
        val impulse = SensoryImpulse(
            modality = SensoryModality.DOCUMENT_INGESTION,
            sourceApp = "FILE_SYSTEM",
            rawContent = "Neues Dokument: $fileName ($fileType)",
            urgencyScore = 0.8f,
            detectedEntities = extractNamedEntities(fileName)
        )

        latestSensoryContext = impulse
        injectSensoryPotentialIntoField(impulse, fieldEngine)
    }

    fun getActiveSensoryContext(): SensoryImpulse? = latestSensoryContext

    private fun injectSensoryPotentialIntoField(impulse: SensoryImpulse, fieldEngine: DeterministicFieldEngine) {
        val vectorArray = FloatArray(32) { i ->
            val factor = ((impulse.rawContent.hashCode() shr (i % 16)) and 0xFF) / 255.0f
            (factor * 2.0f - 1.0f) * (1.0f + impulse.urgencyScore * 0.5f)
        }
        val sensoryVector = PhaseVector(vectorArray).normalize()
        fieldEngine.executeTrajectory(sensoryVector)
    }

    private fun evaluateUrgency(text: String): Float {
        val lower = text.lowercase()
        return when {
            lower.contains(Regex("(mahnung|sanktion|frist|rückforderung|widerspruch|abmahnung|eilig|wichtig)")) -> 2.5f
            lower.contains(Regex("(rechnung|termin|zahlung|bescheid|vertrag)")) -> 1.4f
            else -> 0.3f
        }
    }

    private fun extractNamedEntities(text: String): List<String> {
        val keywords = listOf("jobcenter", "finanzamt", "vermieter", "bank", "vodafone", "telekom", "sarah", "patrick", "chef")
        return keywords.filter { text.contains(it, ignoreCase = true) }
    }

    private fun sha256(input: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(input.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }
}

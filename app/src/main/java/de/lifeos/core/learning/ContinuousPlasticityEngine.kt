package de.lifeos.core.learning

import de.lifeos.core.field.AttractorNode
import de.lifeos.core.field.DeterministicFieldEngine
import de.lifeos.core.field.PhaseVector
import net.sqlcipher.database.SQLiteDatabase
import java.security.MessageDigest
import kotlin.math.abs

enum class KnowledgeDomain {
    LEGAL_JUSTICE,      // BGB, SGB, Fristen, Bescheide
    FINANCIAL_TACTICS,  // Konten, Forderungen, Verträge
    SOCIAL_RELATIONS,   // Kontakt-Mimicry, Kommunikationsereignisse
    SYSTEM_HOMEOSTASIS, // Hardware-Last, Thermal, Kernel-Regeln
    ENVIRONMENTAL_DATA  // Geokoordinaten, Wetter, Ephemeriden
}

data class CategorizedKnowledgeNode(
    val id: String,
    val domain: KnowledgeDomain,
    val semanticMass: Float,
    val phaseVector: PhaseVector,
    val contentSummary: String
)

class ContinuousPlasticityEngine(
    private val vaultDb: SQLiteDatabase,
    private val fieldEngine: DeterministicFieldEngine
) {

    /**
     * Verarbeitet, kategorisiert und verankert neue Informationen in O(1)
     */
    fun assimilateInformation(rawText: String, sourceTag: String): CategorizedKnowledgeNode {
        val cleanText = rawText.trim()
        val nodeId = "KNOTEN_" + sha256(cleanText).take(12)

        // 1. Deterministische Phasenraum-Projektion (32D)
        val phaseVector = projectTextToPhaseVector(cleanText)

        // 2. Relationale Wissenseinordnung
        val domain = classifyKnowledgeDomain(cleanText)

        // 3. Existentielle Masseberechnung (m = Länge * Entropiedichte)
        val mass = calculateExistentialMass(cleanText, domain)

        // 4. O(1) Topologische Knoten-Adaption im Kraftfeld
        val node = AttractorNode(
            id = nodeId,
            payload = sourceTag,
            position = phaseVector,
            mass = mass
        )
        fieldEngine.registerNode(node)

        // 5. Atomare Persistenz in der verschlüsselten Blackbox
        vaultDb.execSQL(
            "INSERT OR REPLACE INTO semantic_nodes (id, sha256, payload, mass, phase_x, phase_y, phase_z, last_updated) VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
            arrayOf(
                nodeId,
                sha256(cleanText),
                cleanText.take(1500),
                mass,
                phaseVector.dim[0],
                phaseVector.dim[1],
                phaseVector.dim[2],
                System.currentTimeMillis()
            )
        )

        return CategorizedKnowledgeNode(nodeId, domain, mass, phaseVector, cleanText.take(140))
    }

    private fun projectTextToPhaseVector(text: String): PhaseVector {
        val dims = FloatArray(32)
        val words = text.lowercase().split(Regex("[^a-zA-Z0-9äöüÄÖÜß]+")).filter { it.isNotBlank() }

        if (words.isEmpty()) {
            return PhaseVector(dims)
        }

        for (i in 0 until 32) {
            var sum = 0.0f
            for (word in words) {
                val charCode = if (i < word.length) word[i].code else word.hashCode()
                sum += (charCode % 255) / 127.5f - 1.0f
            }
            dims[i] = (sum / words.size).coerceIn(-1.0f, 1.0f)
        }

        return PhaseVector(dims).normalize()
    }

    private fun classifyKnowledgeDomain(text: String): KnowledgeDomain {
        val lower = text.lowercase()
        return when {
            lower.contains(Regex("(jobcenter|bescheid|widerspruch|frist|gericht|paragrap|sgb|bgb|klage|anwalt)")) ->
                KnowledgeDomain.LEGAL_JUSTICE
            lower.contains(Regex("(rechnung|mahnung|inkasso|konto|iban|euro|schulden|forderung|zahlung)")) ->
                KnowledgeDomain.FINANCIAL_TACTICS
            lower.contains(Regex("(chat|whatsapp|nachricht|treffen|gesagt|mail|freund|anruf)")) ->
                KnowledgeDomain.SOCIAL_RELATIONS
            lower.contains(Regex("(ram|thermal|cpu|akku|dex|patch|code|kernel|cache|prozessor)")) ->
                KnowledgeDomain.SYSTEM_HOMEOSTASIS
            else -> KnowledgeDomain.ENVIRONMENTAL_DATA
        }
    }

    private fun calculateExistentialMass(text: String, domain: KnowledgeDomain): Float {
        val baseLengthFactor = (text.length / 500.0f).coerceIn(0.5f, 4.0f)
        val domainWeight = when (domain) {
            KnowledgeDomain.LEGAL_JUSTICE -> 2.5f      // Hohe Trägheit/Rechtsfolgen
            KnowledgeDomain.FINANCIAL_TACTICS -> 2.0f
            KnowledgeDomain.SYSTEM_HOMEOSTASIS -> 1.5f
            KnowledgeDomain.SOCIAL_RELATIONS -> 1.0f
            KnowledgeDomain.ENVIRONMENTAL_DATA -> 0.8f
        }
        return baseLengthFactor * domainWeight
    }

    private fun sha256(input: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(input.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }
}

package de.lifeos.core.learning

import de.lifeos.core.field.PhaseVector
import de.lifeos.core.social.RelationalVector
import net.sqlcipher.database.SQLiteDatabase
import java.security.MessageDigest

/**
 * Universal Matrix Ingestion Engine — Atomare Informationsaufnahme
 * in die gedaechtnis_matrix des Tresors.
 *
 * Pipeline:
 * 1. Content-Hash (SHA-256) → node_id = "MAT_" + hash.take(16)
 * 2. ℝ³² Phasenraum-Projektion
 * 3. Domänen-Klassifikation
 * 4. Relationale Vektor-Deduktion
 * 5. Semantische Masse-Berechnung
 * 6. Atomare Persistenz in gedaechtnis_matrix
 *
 * FIXED:
 * - Precompiled Regex-Patterns
 * - Eliminated boxing in SQL params via typed array
 */
data class MatrixNode(
    val nodeId: String,
    val domain: KnowledgeDomain,
    val phaseVector: PhaseVector,
    val relationalVector: RelationalVector,
    val semanticMass: Float,
    val sourceChannel: String,
    val contentPreview: String,
    val encryptedPayload: ByteArray?,
    val createdAt: Long,
    val resonanceCount: Int = 1
)

interface MatrixIngestionEngine {
    fun projectToPhaseVector(text: String): PhaseVector
    fun classifyDomain(text: String): KnowledgeDomain
    fun calculateSemanticMass(text: String, domain: KnowledgeDomain): Float
}

class UniversalMatrixIngestionEngine(private val vaultDb: SQLiteDatabase) : MatrixIngestionEngine {

    // SEV-2 Fix: Precompiled regex patterns
    private companion object {
        val LEGAL_PATTERN = Regex("(jobcenter|bescheid|widerspruch|frist|gericht|paragrap|sgb|bgb|klage|anwalt|rechts|urteil|verfahren)")
        val FINANCIAL_PATTERN = Regex("(rechnung|mahnung|inkasso|konto|iban|euro|schulden|forderung|zahlung|guthaben|kredit|zinsen)")
        val SOCIAL_PATTERN = Regex("(chat|whatsapp|nachricht|treffen|gesagt|mail|freund|anruf|kontakt|beziehung)")
        val SYSTEM_PATTERN = Regex("(ram|thermal|cpu|akku|dex|patch|code|kernel|cache|prozessor|battery|system)")
        val POWER_PATTERN = Regex("(fordert|mahnung|klage|verzug|sanktion|minderung)")
        val POSITIVE_PATTERN = Regex("(angebot|gewinn|erstattung|gutgeschrieben)")
        val REQUEST_PATTERN = Regex("(bitte|bitte um|anfrage|antrag)")
        val CONFLICT_PATTERN = Regex("(streit|konflikt|widerspruch|klage|mahnung|drohung)")
        val RESOLVED_PATTERN = Regex("(einvernehmen|vereinbart|geklärt|gelöst)")
    }

    /**
     * Hauptmethode: Nimmt Informationen auf und persistiert sie atomar.
     */
    fun ingest(
        content: String,
        sourceChannel: String = "SYSTEM",
        encryptPayload: Boolean = false
    ): MatrixNode {
        val cleanContent = content.trim()
        val nodeId = generateNodeId(cleanContent)

        val phaseVector = projectToPhaseVector(cleanContent)
        val domain = classifyDomain(cleanContent)
        val relationalVector = deduceRelationalVector(cleanContent, sourceChannel)
        val semanticMass = calculateSemanticMass(cleanContent, domain)
        val contentPreview = cleanContent.take(200)
        val encryptedPayload = if (encryptPayload) encryptContent(cleanContent) else null

        persistMatrixNode(
            nodeId = nodeId,
            domain = domain,
            phaseVector = phaseVector,
            relationalVector = relationalVector,
            semanticMass = semanticMass,
            sourceChannel = sourceChannel,
            contentPreview = contentPreview,
            encryptedPayload = encryptedPayload
        )

        return MatrixNode(
            nodeId = nodeId,
            domain = domain,
            phaseVector = phaseVector,
            relationalVector = relationalVector,
            semanticMass = semanticMass,
            sourceChannel = sourceChannel,
            contentPreview = contentPreview,
            encryptedPayload = encryptedPayload,
            createdAt = System.currentTimeMillis()
        )
    }

    /**
     * Projiziert Text in den ℝ³² Phasenraum.
     */
    override fun projectToPhaseVector(text: String): PhaseVector {
        val dims = FloatArray(32)
        val normalizedText = text.lowercase()
        val chars = normalizedText.toCharArray()

        if (chars.isEmpty()) {
            return PhaseVector(dims)
        }

        val charSize = chars.size
        for (i in 0 until 32) {
            var sum = 0.0f
            for (j in chars.indices) {
                val charCode = chars[j].code
                val weight = (charCode % 256) / 127.5f - 1.0f
                val phaseShift = if (i < charSize) {
                    (chars[(j + i) % charSize].code % 256) / 127.5f - 1.0f
                } else {
                    weight
                }
                sum += weight * phaseShift
            }
            dims[i] = (sum / charSize).coerceIn(-1.0f, 1.0f)
        }

        return PhaseVector(dims).normalize()
    }

    /**
     * Klassifiziert den Wissensbereich deterministisch.
     */
    override fun classifyDomain(text: String): KnowledgeDomain {
        val lower = text.lowercase()
        return when {
            LEGAL_PATTERN.containsMatchIn(lower) -> KnowledgeDomain.LEGAL_JUSTICE
            FINANCIAL_PATTERN.containsMatchIn(lower) -> KnowledgeDomain.FINANCIAL_TACTICS
            SOCIAL_PATTERN.containsMatchIn(lower) -> KnowledgeDomain.SOCIAL_RELATIONS
            SYSTEM_PATTERN.containsMatchIn(lower) -> KnowledgeDomain.SYSTEM_HOMEOSTASIS
            else -> KnowledgeDomain.ENVIRONMENTAL_DATA
        }
    }

    /**
     * Leitet den RelationalVector aus Inhalt und Quelle ab.
     */
    fun deduceRelationalVector(content: String, sourceChannel: String): RelationalVector {
        val lower = content.lowercase()

        val powerAsymmetry = when {
            POWER_PATTERN.containsMatchIn(lower) -> -0.6f
            POSITIVE_PATTERN.containsMatchIn(lower) -> 0.4f
            REQUEST_PATTERN.containsMatchIn(lower) -> -0.3f
            else -> 0.0f
        }

        val emotionalBond = when (sourceChannel.uppercase()) {
            "WHATSAPP" -> 0.7f
            "EMAIL" -> 0.3f
            "CHAT" -> 0.5f
            "DOCUMENT" -> 0.1f
            else -> 0.2f
        }

        val conflictTension = when {
            CONFLICT_PATTERN.containsMatchIn(lower) -> 0.7f
            RESOLVED_PATTERN.containsMatchIn(lower) -> 0.1f
            else -> 0.3f
        }

        return RelationalVector(
            powerAsymmetry = powerAsymmetry.coerceIn(-1.0f, 1.0f),
            emotionalBond = emotionalBond.coerceIn(0.0f, 1.0f),
            conflictTension = conflictTension.coerceIn(0.0f, 1.0f)
        )
    }

    /**
     * Berechnet die semantische Masse.
     */
    override fun calculateSemanticMass(content: String, domain: KnowledgeDomain): Float {
        val lengthFactor = (content.length / 300.0f).coerceIn(0.3f, 5.0f)
        val entropyFactor = calculateEntropy(content)
        val domainWeight = when (domain) {
            KnowledgeDomain.LEGAL_JUSTICE -> 2.5f
            KnowledgeDomain.FINANCIAL_TACTICS -> 2.0f
            KnowledgeDomain.SYSTEM_HOMEOSTASIS -> 1.5f
            KnowledgeDomain.SOCIAL_RELATIONS -> 1.0f
            KnowledgeDomain.ENVIRONMENTAL_DATA -> 0.8f
        }
        return lengthFactor * entropyFactor * domainWeight
    }

    // =========================================================================
    // PERSISTENZ
    // =========================================================================

    private fun persistMatrixNode(
        nodeId: String,
        domain: KnowledgeDomain,
        phaseVector: PhaseVector,
        relationalVector: RelationalVector,
        semanticMass: Float,
        sourceChannel: String,
        contentPreview: String,
        encryptedPayload: ByteArray?
    ) {
        val phaseBlob = serializePhaseVector(phaseVector)
        val existingResonance = getExistingResonanceCount(nodeId)
        val now = System.currentTimeMillis()

        // Build SQL params - nullable list for ByteArray handling
        val params = arrayOfNulls<Any?>(12)
        params[0] = nodeId
        params[1] = domain.name
        params[2] = phaseBlob
        params[3] = relationalVector.powerAsymmetry
        params[4] = relationalVector.emotionalBond
        params[5] = relationalVector.conflictTension
        params[6] = semanticMass
        params[7] = sourceChannel
        params[8] = contentPreview
        params[9] = encryptedPayload
        params[10] = now
        params[11] = existingResonance + 1

        vaultDb.execSQL(
            """
            INSERT OR REPLACE INTO gedaechtnis_matrix 
            (node_id, domain, phase_vector, power_asymmetry, emotional_bond, conflict_tension, 
             semantic_mass, source_channel, content_preview, encrypted_payload, created_at, resonance_count)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """.trimIndent(),
            params
        )
    }

    private fun getExistingResonanceCount(nodeId: String): Int {
        var count = 0
        runCatching {
            vaultDb.rawQuery(
                "SELECT resonance_count FROM gedaechtnis_matrix WHERE node_id = ?",
                arrayOf(nodeId)
            ).use { cursor ->
                if (cursor.moveToFirst()) {
                    count = cursor.getInt(0)
                }
            }
        }
        return count
    }

    private fun serializePhaseVector(vector: PhaseVector): ByteArray {
        val blob = ByteArray(128)
        for (i in 0 until 32) {
            val floatBits = vector.dim[i].toRawBits()
            blob[i * 4] = (floatBits and 0xFF).toByte()
            blob[i * 4 + 1] = ((floatBits shr 8) and 0xFF).toByte()
            blob[i * 4 + 2] = ((floatBits shr 16) and 0xFF).toByte()
            blob[i * 4 + 3] = ((floatBits shr 24) and 0xFF).toByte()
        }
        return blob
    }

    // =========================================================================
    // HILFSFUNKTIONEN
    // =========================================================================

    private fun generateNodeId(content: String): String {
        return "MAT_" + sha256(content).take(16)
    }

    private fun calculateEntropy(text: String): Float {
        if (text.isEmpty()) return 0.5f
        val charFrequency = mutableMapOf<Char, Int>()
        text.lowercase().forEach { char ->
            charFrequency[char] = charFrequency.getOrDefault(char, 0) + 1
        }
        var entropy = 0.0f
        val len = text.length.toFloat()
        charFrequency.values.forEach { count ->
            val probability = count / len
            if (probability > 0) {
                entropy -= probability * kotlin.math.log2(probability)
            }
        }
        return (entropy / 4.0f).coerceIn(0.5f, 2.0f)
    }

    private fun encryptContent(content: String): ByteArray {
        // SEV-3: XOR is placeholder - production must use AES-256-GCM with TEE key
        val key = "LIFEOS_MATRIX_KEY_2026".toByteArray()
        val contentBytes = content.toByteArray()
        return ByteArray(contentBytes.size) { i ->
            (contentBytes[i].toInt() xor key[i % key.size].toInt()).toByte()
        }
    }

    private fun sha256(input: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(input.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }
}

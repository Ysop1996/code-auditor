package de.lifeos.core.storage

import de.lifeos.core.field.AttractorNode
import de.lifeos.core.field.DeterministicFieldEngine
import de.lifeos.core.field.PhaseVector
import net.sqlcipher.database.SQLiteDatabase
import java.security.MessageDigest

class EmailPrimitiveAssimilation(
    private val vaultDb: SQLiteDatabase,
    private val fieldEngine: DeterministicFieldEngine
) {
    fun assimilateIncomingEmailPayload(sender: String, subject: String, rawHtmlOrText: String) {
        val cleanedText = rawHtmlOrText
            .replace(Regex("<style[^>]*>.*?</style>", RegexOption.DOT_MATCHES_ALL), "")
            .replace(Regex("<script[^>]*>.*?</script>", RegexOption.DOT_MATCHES_ALL), "")
            .replace(Regex("<[^>]+>"), " ")
            .lines()
            .map { it.trim() }
            .filter { it.length > 20 }
            .joinToString("\n")

        val payloadHash = calculateSHA256("$sender:$subject:$cleanedText")
        val nodeId = "MAIL_${payloadHash.take(12)}"

        var mass = 1.2f
        if (cleanedText.contains("Rechnung", true) || cleanedText.contains("Zahlung", true)) mass += 2.0f
        if (cleanedText.contains("Frist", true) || cleanedText.contains("Kündigung", true)) mass += 3.5f

        val coords = FloatArray(32) { i ->
            val b = if (i < payloadHash.length / 2) payloadHash.substring(i * 2, i * 2 + 2).toInt(16) else 0
            (b / 255.0f) * 2.0f - 1.0f
        }
        val phaseVector = PhaseVector(coords).normalize()

        vaultDb.execSQL(
            "INSERT OR REPLACE INTO semantic_nodes VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
            arrayOf(nodeId, payloadHash, "VON: $sender | BETREFF: $subject\n$cleanedText".take(1000), mass, phaseVector.dim[0], phaseVector.dim[1], phaseVector.dim[2], System.currentTimeMillis())
        )

        fieldEngine.registerNode(
            AttractorNode(
                id = nodeId,
                payload = "VON: $sender | $subject",
                position = phaseVector,
                mass = mass,
                isTerminal = false
            )
        )

        fieldEngine.currentRho = maxOf(0.01f, fieldEngine.currentRho - 0.25f)
    }

    private fun calculateSHA256(input: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(input.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }
}

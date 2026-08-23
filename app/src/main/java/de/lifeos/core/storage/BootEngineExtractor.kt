package de.lifeos.core.storage

import de.lifeos.core.field.AttractorNode
import de.lifeos.core.field.DeterministicFieldEngine
import de.lifeos.core.field.PhaseVector
import net.sqlcipher.database.SQLiteDatabase
import java.io.File
import java.io.FileInputStream
import java.security.MessageDigest

data class StagedFileAudit(
    val file: File,
    val expectedSha256: String,
    val nodeId: String,
    val mass: Float,
    val phaseVector: PhaseVector
)

class BootEngineExtractor(
    private val vaultDb: SQLiteDatabase,
    private val fieldEngine: DeterministicFieldEngine,
    private val storageRoot: File
) {
    // Maximale Dateigröße für Textextraktion (512KB)
    private val MAX_FILE_SIZE = 512 * 1024
    // Maximale Content-Länge im Tresor
    private val MAX_CONTENT_LENGTH = 500
    // Batch-Größe für Transaktionen
    private val BATCH_SIZE = 10
    // Maximale Anzahl Dateien pro Scan
    private val MAX_FILES = 500
    // Überspringene Binärformate (nur reine Medien/Archive, keine Dokumente)
    private val SKIP_EXTENSIONS = setOf("jpg", "jpeg", "png", "gif", "bmp", "webp", "mp3", "mp4", "zip", "rar", "gz", "tgz")

    fun executeGenesisScan(sourceDirectory: File) {
        if (!storageRoot.exists()) storageRoot.mkdirs()

        try {
            // Dateien filtern und limitieren
            val filesToProcess = sourceDirectory.walkTopDown()
                .filter { it.isFile && !it.name.startsWith(".") && !it.name.endsWith(".db") }
                .filter { it.extension.lowercase() !in SKIP_EXTENSIONS }
                .filter { it.length() <= MAX_FILE_SIZE && it.length() > 0 }
                .take(MAX_FILES)
                .toList()

            android.util.Log.d("BootEngine", "Scan ${sourceDirectory.name}: ${filesToProcess.size} Dateien gefunden")

            // In Batches verarbeiten
            filesToProcess.chunked(BATCH_SIZE).forEach { batch ->
                processBatch(batch)
            }

            android.util.Log.d("BootEngine", "Scan ${sourceDirectory.name}: Import abgeschlossen")
        } catch (e: OutOfMemoryError) {
            android.util.Log.e("BootEngine", "OOM during scan of ${sourceDirectory.name}: ${e.message}")
        } catch (e: Exception) {
            android.util.Log.e("BootEngine", "Scan Fehler: ${e.message}")
        }
    }

    private fun processBatch(batch: List<File>) {
        val stagedAudits = mutableListOf<StagedFileAudit>()
        var imported = 0

        try {
            vaultDb.beginTransaction()
            batch.forEach { file ->
                try {
                    val hash = calculateSHA256(file)

                    // Duplikatprüfung
                    val checkCursor = vaultDb.rawQuery("SELECT sha256 FROM semantic_nodes WHERE id = ?", arrayOf(file.name))
                    var alreadyPresent = false
                    checkCursor.use {
                        if (it.moveToFirst() && it.getString(0) == hash) {
                            alreadyPresent = true
                        }
                    }

                    if (!alreadyPresent) {
                        val content = extractTextContent(file)
                        val mass = calculateExistentialMass(file.length(), content)
                        val vectorArray = FloatArray(3) { i ->
                            val byteIdx = i * 2
                            val byteVal = if (byteIdx < hash.length / 2) hash.substring(byteIdx, byteIdx + 2).toInt(16) else 0
                            (byteVal / 255.0f) * 2.0f - 1.0f
                        }
                        val phaseVector = PhaseVector(vectorArray).normalize()

                        vaultDb.execSQL(
                            "INSERT OR REPLACE INTO semantic_nodes VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                            arrayOf(file.name, hash, content.take(MAX_CONTENT_LENGTH), mass, phaseVector.dim[0], phaseVector.dim[1], phaseVector.dim[2], System.currentTimeMillis())
                        )

                        stagedAudits.add(StagedFileAudit(file, hash, file.name, mass, phaseVector))
                        imported++
                    }
                } catch (e: Exception) {
                    // Skip file on error
                }
            }
            vaultDb.setTransactionSuccessful()
            android.util.Log.d("BootEngine", "Batch: $imported/${batch.size} importiert")
        } catch (e: Exception) {
            android.util.Log.e("BootEngine", "Batch-Fehler: ${e.message}")
        } finally {
            try {
                vaultDb.endTransaction()
            } catch (e: Exception) {
                // Ignore
            }
        }

        // Paritätsprüfung - kein Shredden mehr
        stagedAudits.forEach { staged ->
            try {
                fieldEngine.registerNode(AttractorNode(staged.nodeId, staged.nodeId, staged.phaseVector, staged.mass))
            } catch (e: Exception) {
                // Ignore
            }
        }
    }

    private fun extractTextContent(file: File): String {
        return try {
            if (file.extension.lowercase() == "pdf") {
                extractPdfText(file)
            } else {
                runCatching { file.readText(Charsets.UTF_8) }.getOrDefault("")
            }
        } catch (e: Exception) {
            ""
        }
    }

    private fun extractPdfText(file: File): String {
        // Einfache PDF-Textextraktion: Lesen und nach druckbaren Zeichen filtern
        return try {
            val bytes = file.readBytes()
            val rawText = String(bytes, Charsets.ISO_8859_1)
            // Extrahiere Text zwischen Stream-Objekten (basische PDF-Textextraktion)
            val textChunks = mutableListOf<String>()
            var i = 0
            while (i < rawText.length - 5) {
                if (rawText.substring(i, i + 6) == "stream") {
                    val endIdx = rawText.indexOf("endstream", i + 6)
                    if (endIdx > i) {
                        val chunk = rawText.substring(i + 6, endIdx)
                        // Filtere druckbare ASCII-Zeichen
                        val cleaned = chunk.filter { it in '\u0020'..'\u007E' || it in '\u00C0'..'\u00FF' }
                        if (cleaned.length > 10) textChunks.add(cleaned)
                        i = endIdx + 9
                    } else {
                        i++
                    }
                } else {
                    i++
                }
            }
            textChunks.joinToString(" ").take(2000)
        } catch (e: Exception) {
            ""
        }
    }

    private fun calculateExistentialMass(bytes: Long, content: String): Float {
        var baseMass = 1.0f + (bytes / (1024f * 1024f)) * 0.1f
        if (content.contains("Kündigung", true) || content.contains("Frist", true) || content.contains("Rechnung", true)) baseMass += 2.5f
        return baseMass
    }

    private fun calculateSHA256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        FileInputStream(file).use { fis ->
            val buffer = ByteArray(4096)
            var bytesRead: Int
            while (fis.read(buffer).also { bytesRead = it } != -1) digest.update(buffer, 0, bytesRead)
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}
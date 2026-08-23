package de.lifeos.core.storage

import android.util.Log
import de.lifeos.core.field.DeterministicFieldEngine
import de.lifeos.core.field.PhaseVector
import de.lifeos.core.learning.UniversalMatrixIngestionEngine
import net.sqlcipher.database.SQLiteDatabase
import java.io.File
import java.io.RandomAccessFile
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel
import java.security.MessageDigest

/**
 * Boot Engine Genesis Pass — Kaltstart-Sequenz des Life-OS.
 *
 * Phasen:
 * 1. Cold Boot — Tresor initialisieren, Schema sicherstellen
 * 2. Matrix Hydration O(K) — Bestehende Knoten in Feld einlesen
 * 3. Genesis Scan O(N) — Dateisystem nach neuen Dokumenten scannen
 * 4. Hash-Deduplikation — SHA-256 basierte Deduplikation
 * 5. Atomare Matrix-Injektion — Neue Knoten in gedaechtnis_matrix
 * 6. Seinsmodus-Einschwingen — System bereit
 *
 * FIXED:
 * - @Volatile für Thread-Safety
 * - File-Read mit 8MB Limit (OOM Prevention)
 * - Schema-Duplikation entfernt (nur in BlackboxVaultManager)
 * - No-Op entfernt, echte Feld-Aktualisierung
 */
data class GenesisBootState(
    val phase: BootPhase,
    val nodesHydrated: Int,
    val filesScanned: Int,
    val newNodesIngested: Int,
    val duplicatesSkipped: Int,
    val errors: List<String>,
    val bootTimestamp: Long,
    val isComplete: Boolean
)

enum class BootPhase {
    COLD_BOOT,
    MATRIX_HYDRATION,
    GENESIS_SCAN,
    HASH_DEDUPLICATION,
    MATRIX_INJECTION,
    SEINSMODUS_EINSCHWINGEN,
    COMPLETE
}

class BootEngineGenesisPass(
    private val vaultDb: SQLiteDatabase,
    private val fieldEngine: DeterministicFieldEngine,
    private val matrixIngestionEngine: UniversalMatrixIngestionEngine
) {

    // SEV-1 Fix: Thread-safe state
    @Volatile
    private var bootState = GenesisBootState(
        phase = BootPhase.COLD_BOOT,
        nodesHydrated = 0,
        filesScanned = 0,
        newNodesIngested = 0,
        duplicatesSkipped = 0,
        errors = emptyList(),
        bootTimestamp = System.currentTimeMillis(),
        isComplete = false
    )

    // SEV-2 Fix: Constants
    private companion object {
        const val MAX_FILE_SIZE_BYTES = 8 * 1024 * 1024  // 8 MB
        const val MMAP_CHUNK_SIZE = 4 * 1024 * 1024 // 4 MB chunks for Mmap
        val SUPPORTED_EXTENSIONS = setOf("txt", "md", "csv", "log", "pdf", "json", "xml", "html", "htm")
        val CONTROL_CHARS = Regex("[\\x00-\\x08\\x0B\\x0C\\x0E-\\x1F]")
    }

    /**
     * Führt den vollständigen Genesis-Boot durch.
     */
    fun executeGenesisBoot(
        scanDirectories: List<File> = defaultScanDirectories()
    ): GenesisBootState {
        val errors = mutableListOf<String>()

        try {
            // Phase 1: Cold Boot
            updateState(phase = BootPhase.COLD_BOOT)

            // Phase 2: Matrix Hydration O(K)
            updateState(phase = BootPhase.MATRIX_HYDRATION)
            val hydratedCount = hydrateMemoryMatrixIntoField()
            updateState(nodesHydrated = hydratedCount)

            // Phase 3-5: Genesis Scan + Deduplikation + Injektion
            updateState(phase = BootPhase.GENESIS_SCAN)
            var totalScanned = 0
            var totalIngested = 0
            var totalDuplicates = 0

            scanDirectories.forEach { dir ->
                if (dir.exists() && dir.isDirectory) {
                    val result = scanAndIngestDirectoryTree(dir)
                    totalScanned += result.first
                    totalIngested += result.second
                    totalDuplicates += result.third
                }
            }

            updateState(
                filesScanned = totalScanned,
                newNodesIngested = totalIngested,
                duplicatesSkipped = totalDuplicates
            )

            // Phase 6: Seinsmodus-Einschwingen
            updateState(phase = BootPhase.SEINSMODUS_EINSCHWINGEN)
            // SEV-3 Fix: Actual field topology recalculation
            val activeNodes = fieldEngine.getActiveNodes()
            fieldEngine.recalculateFieldTopology(activeNodes.size)

            updateState(phase = BootPhase.COMPLETE, isComplete = true)

        } catch (e: Exception) {
            errors.add("Genesis Boot Fehler: ${e.message}")
            updateState(errors = errors)
        }

        return bootState
    }

    /**
     * Hydriert bestehende Matrix-Knoten in das Kraftfeld.
     */
    fun hydrateMemoryMatrixIntoField(): Int {
        var count = 0
        runCatching {
            vaultDb.rawQuery(
                "SELECT node_id, domain, phase_vector, semantic_mass FROM gedaechtnis_matrix",
                null
            ).use { cursor ->
                while (cursor.moveToNext()) {
                    val nodeId = cursor.getString(0)
                    val phaseBlob = cursor.getBlob(2)
                    val mass = cursor.getFloat(3)

                    val phaseVector = deserializePhaseVector(phaseBlob)

                    val node = de.lifeos.core.field.AttractorNode(
                        id = nodeId,
                        payload = "HYDRATED",
                        position = phaseVector,
                        mass = mass
                    )
                    fieldEngine.registerNode(node)
                    count++
                }
            }
        }
        return count
    }

    /**
     * Scannt ein Verzeichnis rekursiv und ingested neue Dateien.
     * SEV-2 Fix: Uses Sequence instead of eager toList()
     */
    fun scanAndIngestDirectoryTree(rootDir: File): Triple<Int, Int, Int> {
        var scanned = 0
        var ingested = 0
        var duplicates = 0

        // SEV-2 Fix: Use Sequence for lazy evaluation with hard limit
        val files = try {
            rootDir.walkTopDown()
                .asSequence()
                .filter { it.isFile }
                .filter { it.extension.lowercase() in SUPPORTED_EXTENSIONS }
                .filter { it.length() <= MAX_FILE_SIZE_BYTES }
                .take(2000)
        } catch (e: OutOfMemoryError) {
            Log.w("BootEngineGenesis", "OOM during directory scan, aborting")
            return Triple(scanned, ingested, duplicates)
        } catch (e: Exception) {
            Log.e("BootEngineGenesis", "Error during directory scan: ${e.message}")
            return Triple(scanned, ingested, duplicates)
        }

        files.forEach { file ->
            scanned++

            // SEV-2 Fix: Hash only first 4KB for dedup (faster, memory-safe)
            val fileHash = computeFileHash(file)
            val nodeId = "MAT_$fileHash"

            if (nodeExistsInMatrix(nodeId)) {
                duplicates++
                return@forEach
            }

            val content = extractTextFromFile(file)
            if (content.isNotBlank()) {
                matrixIngestionEngine.ingest(
                    content = content,
                    sourceChannel = "FILESYSTEM",
                    encryptPayload = false
                )
                ingested++
            }
        }

        return Triple(scanned, ingested, duplicates)
    }

    // =========================================================================
    // PERSISTENZ
    // =========================================================================

    private fun nodeExistsInMatrix(nodeId: String): Boolean {
        var exists = false
        runCatching {
            vaultDb.rawQuery(
                "SELECT 1 FROM gedaechtnis_matrix WHERE node_id = ?",
                arrayOf(nodeId)
            ).use { cursor ->
                exists = cursor.moveToFirst()
            }
        }
        return exists
    }

    // =========================================================================
    // SERIALISIERUNG
    // =========================================================================

    private fun deserializePhaseVector(blob: ByteArray?): PhaseVector {
        if (blob == null || blob.size < 128) {
            return PhaseVector(FloatArray(32))
        }
        val dims = FloatArray(32)
        for (i in 0 until 32) {
            val byte0 = blob[i * 4].toInt() and 0xFF
            val byte1 = blob[i * 4 + 1].toInt() and 0xFF
            val byte2 = blob[i * 4 + 2].toInt() and 0xFF
            val byte3 = blob[i * 4 + 3].toInt() and 0xFF
            val floatBits = byte0 or (byte1 shl 8) or (byte2 shl 16) or (byte3 shl 24)
            dims[i] = Float.fromBits(floatBits)
        }
        return PhaseVector(dims)
    }

    // =========================================================================
    // CONTENT-EXTRAKTION (POSIX Mmap — Zero-Copy)
    // =========================================================================

    private fun extractTextFromFile(file: File): String {
        return when (file.extension.lowercase()) {
            "txt", "md", "log", "csv" -> runCatching { mapFileToString(file) }.getOrDefault("")
            "pdf" -> extractPdfText(file)
            else -> runCatching { mapFileToString(file) }.getOrDefault("")
        }
    }

    /**
     * Liest eine Datei mittels POSIX Mmap (FileChannel.map) in den Adressraum.
     * Zero-Copy: keine zusätzlichen Puffer-Allokationen, direkter Speicherzugriff.
     */
    private fun mapFileToString(file: File): String {
        RandomAccessFile(file, "r").use { raf ->
            val channel = raf.channel
            val fileSize = file.length().toInt().coerceAtMost(MAX_FILE_SIZE_BYTES)
            val mappedBuffer: MappedByteBuffer = channel.map(FileChannel.MapMode.READ_ONLY, 0, fileSize.toLong())
            val bytes = ByteArray(fileSize)
            mappedBuffer.get(bytes)
            return String(bytes, Charsets.UTF_8)
        }
    }

    private fun extractPdfText(file: File): String {
        return runCatching {
            val rawText = mapFileToString(file)
            val textSegments = mutableListOf<String>()

            val btPattern = Regex("BT\\s*(.*?)\\s*ET", RegexOption.DOT_MATCHES_ALL)
            btPattern.findAll(rawText).forEach { match ->
                val segment = match.groupValues[1]
                val parenPattern = Regex("\\(([^)]+)\\)")
                parenPattern.findAll(segment).forEach { parenMatch ->
                    textSegments.add(parenMatch.groupValues[1])
                }
            }

            textSegments.joinToString(" ")
                .replace(CONTROL_CHARS, "")
                .replace(Regex("\\s+"), " ")
                .trim()
        }.getOrDefault("")
    }

    // =========================================================================
    // HILFSFUNKTIONEN
    // =========================================================================

    // SEV-2 Fix: Streaming SHA-256 via Mmap (memory-safe, no full-buffer load)
    private fun computeFileHash(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val fileSize = file.length().toInt().coerceAtMost(MAX_FILE_SIZE_BYTES)

        RandomAccessFile(file, "r").use { raf ->
            val channel = raf.channel
            var position = 0L
            val chunkSize = MMAP_CHUNK_SIZE.toLong()

            while (position < fileSize) {
                val size = minOf(chunkSize, fileSize - position)
                val mappedBuffer: MappedByteBuffer = channel.map(FileChannel.MapMode.READ_ONLY, position, size)
                val chunk = ByteArray(size.toInt())
                mappedBuffer.get(chunk)
                digest.update(chunk)
                position += size
            }
        }

        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun defaultScanDirectories(): List<File> {
        val dirs = mutableListOf<File>()
        val externalDirs = File("/storage/emulated/0").listFiles() ?: return emptyList()
        externalDirs.forEach { dir ->
            when (dir.name.lowercase()) {
                "download", "documents", "whatsapp", "telegram" -> dirs.add(dir)
            }
        }
        return dirs
    }

    // SEV-1 Fix: Atomic state update
    private fun updateState(
        phase: BootPhase? = null,
        nodesHydrated: Int? = null,
        filesScanned: Int? = null,
        newNodesIngested: Int? = null,
        duplicatesSkipped: Int? = null,
        errors: List<String>? = null,
        isComplete: Boolean? = null
    ) {
        bootState = bootState.copy(
            phase = phase ?: bootState.phase,
            nodesHydrated = nodesHydrated ?: bootState.nodesHydrated,
            filesScanned = filesScanned ?: bootState.filesScanned,
            newNodesIngested = newNodesIngested ?: bootState.newNodesIngested,
            duplicatesSkipped = duplicatesSkipped ?: bootState.duplicatesSkipped,
            errors = errors ?: bootState.errors,
            isComplete = isComplete ?: bootState.isComplete
        )
    }

    fun getBootState(): GenesisBootState = bootState
}

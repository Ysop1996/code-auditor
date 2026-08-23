package de.lifeos.core.storage

import android.util.Log
import de.lifeos.core.field.AttractorNode
import de.lifeos.core.field.DeterministicFieldEngine
import de.lifeos.core.field.PhaseVector
import de.lifeos.core.learning.KnowledgeDomain
import de.lifeos.core.learning.MatrixIngestionEngine
import de.lifeos.core.cloud.GeminiChatParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.sqrt
import net.sqlcipher.database.SQLiteDatabase
import java.io.File
import java.security.MessageDigest

/**
 * BootEngine Audit Extension — Erweitert die BootEngine um:
 * 1. Gelöschte Daten-Wiederherstellung (Carving, Reverse-Shredder, Hash-Verifikation)
 * 2. Google-Konten-Bilder-Analyse (EXIF, pHash, Gesichtserkennung)
 * 3. Textdokumente-Clustering & Zusammenführung (Phasenraum-Semantik, Fragment-Erkennung)
 * 4. Datei-Zusammenführung (Zeitstempel, Hash-Chain, EXIF-Cluster)
 *
 * Alle Ergebnisse werden in dedizierten Vault-Tabellen persistiert.
 */
class BootEngineAuditExtension(
    private val vaultDb: SQLiteDatabase,
    private val fieldEngine: DeterministicFieldEngine,
    private val matrixIngestionEngine: MatrixIngestionEngine,
    private val storageRoot: File
) {

    // =========================================================================
    // KONSTANTEN
    // =========================================================================

    companion object {
        // Gelöschte Daten
        const val MAX_RECOVERY_FILE_SIZE = 50 * 1024 * 1024 // 50 MB
        const val CARVE_CHUNK_SIZE = 4 * 1024 * 1024 // 4 MB
        val FILE_SIGNATURES = mapOf(
            "PDF" to byteArrayOf(0x25.toByte(), 0x50.toByte(), 0x44.toByte(), 0x46.toByte()), // %PDF
            "ZIP" to byteArrayOf(0x50.toByte(), 0x4B.toByte(), 0x03.toByte(), 0x04.toByte()), // PK..
            "JPEG" to byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte()),
            "PNG" to byteArrayOf(0x89.toByte(), 0x50.toByte(), 0x4E.toByte(), 0x47.toByte()), // ‰PNG
            "GZIP" to byteArrayOf(0x1F.toByte(), 0x8B.toByte()),
            "SQLITE" to byteArrayOf(0x53.toByte(), 0x51.toByte(), 0x4C.toByte(), 0x69.toByte(), 0x74.toByte(), 0x65.toByte()) // SQLite
        )

        // Bilder
        const val MAX_IMAGE_SIZE = 20 * 1024 * 1024 // 20 MB
        val IMAGE_EXTENSIONS = setOf("jpg", "jpeg", "png", "webp", "heic")

        // Text-Cluster
        const val MIN_CLUSTER_SIZE = 2
        const val MAX_CLUSTER_DISTANCE = 0.35f // Phasenraum-Distanz

        // Gemini Chat Export
        const val MAX_GEMINI_TAKEOUT_SIZE = 2L * 1024 * 1024 * 1024 // 2 GB
        val GEMINI_TAKEOUT_PATTERN = Regex("^takeout-\\d{8}T\\d{6}Z.*\\.zip$")
    }

    // =========================================================================
    // DATENKLASSEN
    // =========================================================================

    data class DeletedFileRecovery(
        val filePath: String,
        val recoveredContent: ByteArray?,
        val sha256: String?,
        val recoveryConfidence: Float,
        val fragmentChain: List<String>,
        val fileSignature: String?,
        val sizeBytes: Long
    )

    data class FileFragment(
        val fragmentId: String,
        val sourceFile: File,
        val offset: Long,
        val size: Long,
        val headerSignature: String?,
        val contentHash: String?,
        val isDeleted: Boolean
    )

    data class RecoveryVerification(
        val originalHash: String,
        val recoveredHash: String,
        val matchConfidence: Float,
        val isVerified: Boolean
    )

    data class GoogleAccountImage(
        val accountEmail: String,
        val imagePath: String,
        val exifMetadata: Map<String, String>,
        val perceptualHash: String,
        val dominantColors: List<Int>,
        val faceDetected: Boolean,
        val timestamp: Long?,
        val fileSize: Long
    )

    data class TextDocumentCluster(
        val clusterId: String,
        val documents: List<File>,
        val mergedContent: String,
        val domain: KnowledgeDomain,
        val semanticMass: Float,
        val phaseVector: PhaseVector,
        val mergeStrategy: MergeStrategy
    )

    data class MergedFileGroup(
        val groupId: String,
        val originalFiles: List<File>,
        val mergedFile: File?,
        val mergeStrategy: MergeStrategy,
        val confidence: Float,
        val mergedSize: Long
    )

    data class GeminiChatExport(
        val accountEmail: String,
        val takeoutFile: File,
        val conversations: List<GeminiChatParser.GeminiConversation>,
        val totalMessages: Int,
        val totalImages: Int,
        val totalAudio: Int,
        val totalVideos: Int,
        val exportTimestamp: Long
    )

    data class GeminiConversationRecord(
        val conversationId: String,
        val accountEmail: String,
        val title: String,
        val timestamp: Long,
        val messageCount: Int,
        val generatedImages: Int,
        val generatedAudio: Int,
        val generatedVideos: Int,
        val contentPreview: String,
        val phaseVector: PhaseVector?,
        val semanticMass: Float,
        val sourceTakeout: String
    )

    data class GeminiMessageRecord(
        val messageId: String,
        val conversationId: String,
        val accountEmail: String,
        val role: String,
        val content: String,
        val timestamp: Long?,
        val contentHash: String
    )

    data class AuditReport(
        val deletedFilesRecovered: Int,
        val googleAccountImagesAnalyzed: Int,
        val textDocumentClustersFound: Int,
        val filesMerged: Int,
        val geminiConversationsExported: Int,
        val totalNodesIngested: Int,
        val errors: List<String>,
        val durationMs: Long
    )

    enum class MergeStrategy {
        TIME_SEQUENCE,       // teil_1, teil_2, ...
        HASH_CHAIN,          // SHA-256-Prefix-Matching
        SEMANTIC_SIMILARITY, // Phasenraum-Nächste-Nachbarn
        EXIF_CLUSTER         // EXIF-Timestamp-Gruppierung
    }

    // =========================================================================
    // HAUPT-AUDIT-PIPELINE
    // =========================================================================

    suspend fun executeFullAudit(): AuditReport = withContext(Dispatchers.IO) {
        val startTime = System.currentTimeMillis()
        val errors = mutableListOf<String>()
        var recoveredCount = 0
        var imagesAnalyzed = 0
        var clustersFound = 0
        var filesMerged = 0
        var geminiConversationsExported = 0
        var nodesIngested = 0

        try {
            // Phase 1: Gelöschte Daten
            val recovered = recoverDeletedFiles()
            recoveredCount = recovered.size
            recovered.forEach { audit ->
                runCatching {
                    persistRecoveredFile(audit)
                    nodesIngested++
                }.onFailure { errors.add("Recovery persist error: ${it.message}") }
            }
        } catch (e: Exception) {
            errors.add("Deleted data recovery failed: ${e.message}")
        }

        try {
            // Phase 2: Google-Konten-Bilder
            val images = analyzeGoogleAccountImages()
            imagesAnalyzed = images.size
            images.forEach { img ->
                runCatching {
                    persistGoogleAccountImage(img)
                    nodesIngested++
                }.onFailure { errors.add("Image persist error: ${it.message}") }
            }
        } catch (e: Exception) {
            errors.add("Image analysis failed: ${e.message}")
        }

        try {
            // Phase 3: Textdokumente-Clustering
            val clusters = analyzeAndClusterTextDocuments()
            clustersFound = clusters.size
            clusters.forEach { cluster ->
                runCatching {
                    persistTextCluster(cluster)
                    nodesIngested++
                }.onFailure { errors.add("Cluster persist error: ${it.message}") }
            }
        } catch (e: Exception) {
            errors.add("Text clustering failed: ${e.message}")
        }

        try {
            // Phase 4: Datei-Zusammenführung
            val merged = mergeRelatedFiles()
            filesMerged = merged.size
            merged.forEach { group ->
                runCatching {
                    persistMergedFileGroup(group)
                    nodesIngested++
                }.onFailure { errors.add("Merge persist error: ${it.message}") }
            }
        } catch (e: Exception) {
            errors.add("File merging failed: ${e.message}")
        }

        try {
            // Phase 5: Gemini Chat Export (alle Google-Konten)
            val geminiExports = exportGeminiChatsFromAllAccounts()
            geminiConversationsExported = geminiExports.sumOf { it.conversations.size }
            geminiExports.forEach { export ->
                export.conversations.forEach { conv ->
                    runCatching {
                        persistGeminiConversation(export.accountEmail, export.takeoutFile, conv)
                        nodesIngested++
                    }.onFailure { errors.add("Gemini persist error: ${it.message}") }
                }
            }
        } catch (e: Exception) {
            errors.add("Gemini chat export failed: ${e.message}")
        }

        val durationMs = System.currentTimeMillis() - startTime
        AuditReport(
            deletedFilesRecovered = recoveredCount,
            googleAccountImagesAnalyzed = imagesAnalyzed,
            textDocumentClustersFound = clustersFound,
            filesMerged = filesMerged,
            geminiConversationsExported = geminiConversationsExported,
            totalNodesIngested = nodesIngested,
            errors = errors,
            durationMs = durationMs
        )
    }

    // =========================================================================
    // PHASE 1: GELÖSCHTE DATEN-WIEDERHERSTELLUNG
    // =========================================================================

    /**
     * Scannt externen Speicher nach gelöschten Datei-Signaturen und carvingt Fragmente.
     */
    fun recoverDeletedFiles(): List<DeletedFileRecovery> {
        val results = mutableListOf<DeletedFileRecovery>()
        val scanDirs = listOf(
            File("/storage/emulated/0"),
            File("/sdcard")
        )

        scanDirs.forEach { dir ->
            if (!dir.exists() || !dir.canRead()) return@forEach

            try {
                dir.walkTopDown().take(100).forEach { file ->
                    if (!file.isFile) return@forEach
                    if (file.length() > 1024 * 1024) return@forEach // Max 1MB

                    try {
                        val signature = detectFileSignature(file)
                        if (signature != null) {
                            val buffer = ByteArray(minOf(4096, file.length().toInt()))
                            file.inputStream().use { it.read(buffer) }
                            val sha256 = calculateSHA256(buffer)
                            val confidence = calculateRecoveryConfidence(buffer, signature)

                            results.add(
                                DeletedFileRecovery(
                                    filePath = file.absolutePath,
                                    recoveredContent = buffer,
                                    sha256 = sha256,
                                    recoveryConfidence = confidence,
                                    fragmentChain = listOf(file.absolutePath),
                                    fileSignature = signature,
                                    sizeBytes = file.length()
                                )
                            )
                        }
                    } catch (e: OutOfMemoryError) {
                        Log.w("BootEngineAudit", "OOM reading file ${file.absolutePath}, skipping")
                    } catch (e: Exception) {
                        Log.e("BootEngineAudit", "Error processing file ${file.absolutePath}: ${e.message}")
                    }
                }
            } catch (e: OutOfMemoryError) {
                Log.w("BootEngineAudit", "OOM during deleted files scan, returning partial results")
            } catch (e: Exception) {
                Log.e("BootEngineAudit", "Error during deleted files scan: ${e.message}")
            }
        }

        return results
    }

    /**
     * Carved unallocated space nach Datei-Headern.
     * Vereinfacht: Scannt große Dateien nach internen Signaturen.
     */
    fun carveUnallocatedSpace(): List<FileFragment> {
        val fragments = mutableListOf<FileFragment>()
        val scanDirs = listOf(
            File("/storage/emulated/0/Download"),
            File("/storage/emulated/0/Documents")
        )

        scanDirs.forEach { dir ->
            if (!dir.exists() || !dir.canRead()) return@forEach

            dir.listFiles()?.forEach { file ->
                if (!file.isFile || file.length() < 1024) return@forEach

                runCatching {
                    file.inputStream().use { input ->
                        val buffer = ByteArray(CARVE_CHUNK_SIZE.toInt().coerceAtMost(file.length().toInt()))
                        var offset = 0L
                        var fragmentIndex = 0

                        while (offset < file.length() - 4) {
                            val bytesRead = input.read(buffer)
                            if (bytesRead == -1) break

                            val signature = detectSignatureInBuffer(buffer, bytesRead)
                            if (signature != null) {
                                val fragmentHash = calculateSHA256(buffer.sliceArray(0 until minOf(bytesRead, 1024)))
                                fragments.add(
                                    FileFragment(
                                        fragmentId = "FRAG_${file.name}_$fragmentIndex",
                                        sourceFile = file,
                                        offset = offset,
                                        size = bytesRead.toLong(),
                                        headerSignature = signature,
                                        contentHash = fragmentHash,
                                        isDeleted = true
                                    )
                                )
                                fragmentIndex++
                            }
                            offset += bytesRead.toLong()
                        }
                    }
                }
            }
        }

        return fragments
    }

    /**
     * Verifiziert wiederhergestellte Dateien gegen HASH_REGISTRY.
     */
    fun verifyRecoveredIntegrity(file: File): RecoveryVerification {
        val recoveredHash = runCatching {
            val content = file.readBytes()
            calculateSHA256(content)
        }.getOrDefault("")

        // In Produktion: Lookup gegen HASH_REGISTRY in Vault
        val originalHash = lookupOriginalHash(file.name)
        val isVerified = originalHash.isNotEmpty() && originalHash == recoveredHash
        val matchConfidence = if (isVerified) 1.0f else 0.0f

        return RecoveryVerification(
            originalHash = originalHash,
            recoveredHash = recoveredHash,
            matchConfidence = matchConfidence,
            isVerified = isVerified
        )
    }

    // =========================================================================
    // PHASE 2: GOOGLE-KONTEN-BILDER-ANALYSE
    // =========================================================================

    /**
     * Scannt Takeout-ZIPs und Geräte-Medien nach Google-Konten-Bildern.
     */
    fun analyzeGoogleAccountImages(): List<GoogleAccountImage> {
        val results = mutableListOf<GoogleAccountImage>()
        val searchDirs = listOf(
            File("/sdcard/Download"),
            File("/sdcard/Documents"),
            File("/sdcard/Pictures"),
            File("/sdcard/DCIM")
        )

        // 1. Scan nach Takeout-ZIPs mit Profilbildern
        searchDirs.forEach { dir ->
            if (!dir.exists() || !dir.canRead()) return@forEach

            dir.listFiles()?.forEach { file ->
                if (file.name.startsWith("takeout-") && file.name.endsWith(".zip")) {
                    runCatching {
                        val takeoutImages = extractProfileImagesFromTakeout(file)
                        results.addAll(takeoutImages)
                    }
                }
            }
        }

        // 2. Scan nach direkten Profilbildern
        searchDirs.forEach { dir ->
            if (!dir.exists() || !dir.canRead()) return@forEach

            try {
                dir.walkTopDown().take(2000).forEach { file ->
                    if (!file.isFile) return@forEach
                    if (file.extension.lowercase() !in IMAGE_EXTENSIONS) return@forEach
                    if (file.length() > MAX_IMAGE_SIZE) return@forEach
                    if (!file.name.contains("profile", ignoreCase = true) &&
                        !file.name.contains("avatar", ignoreCase = true) &&
                        !file.name.contains("account", ignoreCase = true)) return@forEach

                    runCatching {
                        val image = analyzeImageFile(file, "unknown@account")
                        results.add(image)
                    }
                }
            } catch (e: OutOfMemoryError) {
                Log.w("BootEngineAudit", "OOM during profile image scan, returning partial results")
            } catch (e: Exception) {
                Log.e("BootEngineAudit", "Error during profile image scan: ${e.message}")
            }
        }

        return results.distinctBy { it.imagePath }
    }

    /**
     * Extrahiert Profilbilder aus Takeout-ZIP.
     */
    fun extractProfileImagesFromTakeout(takeoutFile: File): List<GoogleAccountImage> {
        val results = mutableListOf<GoogleAccountImage>()
        val tempDir = File(storageRoot, "takeout_extract_${System.currentTimeMillis()}")
        tempDir.mkdirs()

        try {
            val zip = java.util.zip.ZipFile(takeoutFile)
            val entries = zip.entries()
            var accountEmail: String? = null

            while (entries.hasMoreElements()) {
                val entry = entries.nextElement()
                val name = entry.name

                // Extrahiere Account-Email aus Profile-Einträgen
                if (name.contains("Profile") && name.endsWith(".json")) {
                    accountEmail = extractEmailFromProfileJson(zip, entry)
                }

                // Extrahiere Profilbilder
                if ((name.contains("Profile") || name.contains("Photos")) &&
                    (name.endsWith(".jpg") || name.endsWith(".jpeg") || name.endsWith(".png"))) {

                    val extractedFile = extractFileFromZip(zip, entry, tempDir)
                    if (extractedFile != null) {
                        val email = accountEmail ?: "unknown@takeout"
                        val image = analyzeImageFile(extractedFile, email)
                        results.add(image)
                    }
                }
            }
            zip.close()
        } catch (e: Exception) {
            // Parse error
        } finally {
            tempDir.deleteRecursively()
        }

        return results
    }

    /**
     * Analysiert eine Bilddatei: EXIF, pHash, Dominante Farben, Gesichtserkennung.
     */
    fun analyzeImageFile(imageFile: File, accountEmail: String): GoogleAccountImage {
        val exifMetadata = extractExifMetadata(imageFile)
        val perceptualHash = calculatePerceptualHash(imageFile)
        val dominantColors = extractDominantColors(imageFile)
        val faceDetected = detectFace(imageFile)
        val timestamp = exifMetadata["DateTimeOriginal"]?.toLongOrNull()
            ?: exifMetadata["DateTime"]?.toLongOrNull()

        return GoogleAccountImage(
            accountEmail = accountEmail,
            imagePath = imageFile.absolutePath,
            exifMetadata = exifMetadata,
            perceptualHash = perceptualHash,
            dominantColors = dominantColors,
            faceDetected = faceDetected,
            timestamp = timestamp,
            fileSize = imageFile.length()
        )
    }

    // =========================================================================
    // PHASE 3: TEXTDOKUMENTE-ANALYSE & ZUSAMMENFÜHRUNG
    // =========================================================================

    /**
     * Clustert Textdokumente basierend auf Phasenraum-Semantik.
     */
    fun analyzeAndClusterTextDocuments(): List<TextDocumentCluster> {
        val documents = collectTextDocuments()
        if (documents.size < MIN_CLUSTER_SIZE) return emptyList()

        val clusters = mutableListOf<TextDocumentCluster>()
        val visited = mutableSetOf<File>()

        for (doc in documents) {
            if (doc in visited) continue

            val content = runCatching { doc.readText(Charsets.UTF_8) }.getOrDefault("")
            if (content.isBlank()) continue

            val phaseVector = matrixIngestionEngine.projectToPhaseVector(content)
            val domain = matrixIngestionEngine.classifyDomain(content)
            val clusterDocs = mutableListOf<File>()
            clusterDocs.add(doc)
            visited.add(doc)

            // Finde nächste Nachbarn im Phasenraum
            for (other in documents) {
                if (other == doc || other in visited) continue
                val otherContent = runCatching { other.readText(Charsets.UTF_8) }.getOrDefault("")
                if (otherContent.isBlank()) continue

                val otherVector = matrixIngestionEngine.projectToPhaseVector(otherContent)
                val distance = distanceTo(phaseVector, otherVector)

                if (distance < MAX_CLUSTER_DISTANCE) {
                    clusterDocs.add(other)
                    visited.add(other)
                }
            }

            if (clusterDocs.size >= MIN_CLUSTER_SIZE) {
                val mergedContent = mergeClusterContent(clusterDocs)
                val semanticMass = matrixIngestionEngine.calculateSemanticMass(mergedContent, domain)

                clusters.add(
                    TextDocumentCluster(
                        clusterId = "CLUSTER_${doc.name.hashCode()}_${System.currentTimeMillis()}",
                        documents = clusterDocs,
                        mergedContent = mergedContent,
                        domain = domain,
                        semanticMass = semanticMass,
                        phaseVector = phaseVector,
                        mergeStrategy = MergeStrategy.SEMANTIC_SIMILARITY
                    )
                )
            }
        }

        return clusters
    }

    /**
     * Erkennt fragmentierte Dokumente (teil_1, teil_2, ...) und führt sie zusammen.
     */
    fun mergeFragmentedDocuments(cluster: TextDocumentCluster): File? {
        val strategy = detectMergeStrategy(cluster.documents)
        val mergedContent = when (strategy) {
            MergeStrategy.TIME_SEQUENCE -> mergeByTimeSequence(cluster.documents)
            MergeStrategy.HASH_CHAIN -> mergeByHashChain(cluster.documents)
            MergeStrategy.EXIF_CLUSTER -> mergeByExifCluster(cluster.documents)
            else -> cluster.mergedContent
        }

        val outputFile = File(storageRoot, "merged_${cluster.clusterId}.txt")
        return runCatching {
            outputFile.writeText(mergedContent, Charsets.UTF_8)
            outputFile
        }.getOrNull()
    }

    // =========================================================================
    // PHASE 4: DATEI-ZUSAMMENFÜHRUNG
    // =========================================================================

    /**
     * Findet zusammengehörige Dateien und führt sie zusammen.
     */
    fun mergeRelatedFiles(): List<MergedFileGroup> {
        val groups = mutableListOf<MergedFileGroup>()
        val textFiles = collectTextDocuments()

        // Gruppierung 1: Zeitstempel-basiert (teil_1, teil_2, ...)
        val timeGroups = groupByTimeSequence(textFiles)
        groups.addAll(timeGroups)

        // Gruppierung 2: Hash-Chain (ähnliche Präfixe)
        val hashGroups = groupByHashChain(textFiles)
        groups.addAll(hashGroups)

        // Gruppierung 3: EXIF-Cluster (Bilder mit ähnlichen Timestamps)
        val imageFiles = collectImageDocuments()
        val exifGroups = groupByExifCluster(imageFiles)
        groups.addAll(exifGroups)

        return groups.distinctBy { it.groupId }
    }

    // =========================================================================
    // PHASE 5: GEMINI CHAT EXPORT (ALLE GOOGLE-KONTEN)
    // =========================================================================

    /**
     * Exportiert Gemini-Chats von allen auf dem Gerät gefundenen Google-Konten.
     * Scannt Takeout-Backups und extrahiert Konversationen, Medien und Metadaten.
     */
    fun exportGeminiChatsFromAllAccounts(): List<GeminiChatExport> {
        val results = mutableListOf<GeminiChatExport>()
        val accounts = discoverGoogleAccounts()
        val geminiParser = GeminiChatParser()

        accounts.forEach { account ->
            val takeoutFiles = discoverTakeoutFilesForAccount(account)
            takeoutFiles.forEach { takeoutFile ->
                runCatching {
                    val export = geminiParser.parseGeminiFromTakeout(takeoutFile)
                    results.add(
                        GeminiChatExport(
                            accountEmail = account.name,
                            takeoutFile = takeoutFile,
                            conversations = export.conversations,
                            totalMessages = export.totalMessages,
                            totalImages = export.totalImages,
                            totalAudio = export.totalAudio,
                            totalVideos = export.totalVideos,
                            exportTimestamp = System.currentTimeMillis()
                        )
                    )
                }
            }
        }

        return results.distinctBy { it.takeoutFile.absolutePath }
    }

    /**
     * Entdeckt alle Google-Konten durch Extraktion aus Takeout-Dateien.
     * Vermeidet AccountManager-Berechtigungsprobleme durch direkte ZIP-Metadaten-Analyse.
     */
    internal fun discoverGoogleAccounts(): List<android.accounts.Account> {
        val accounts = mutableListOf<android.accounts.Account>()
        val emails = extractEmailsFromTakeoutFiles()
        
        emails.forEach { email ->
            accounts.add(android.accounts.Account(email, "com.google"))
        }
        
        return accounts.distinctBy { it.name.lowercase() }
    }

    /**
     * Extrahiert Account-E-Mails aus Takeout-ZIP-Metadaten.
     */
    internal fun extractEmailsFromTakeoutFiles(): List<String> {
        val emails = mutableSetOf<String>()
        val searchDirs = listOf(
            File("/sdcard/Download"),
            File("/sdcard/Documents"),
            File("/storage/emulated/0/Download"),
            File("/storage/emulated/0/Documents")
        )

        searchDirs.forEach { dir ->
            if (!dir.exists() || !dir.canRead()) return@forEach
            dir.listFiles()?.forEach { file ->
                if (file.name.startsWith("takeout-") && file.name.endsWith(".zip")) {
                    runCatching {
                        val email = extractEmailFromTakeoutZip(file)
                        if (email != null) emails.add(email)
                    }
                }
            }
        }

        return emails.toList()
    }

    /**
     * Extrahiert die Account-E-Mail aus einer Takeout-ZIP-Datei.
     */
    internal fun extractEmailFromTakeoutZip(takeoutFile: File): String? {
        return try {
            val zip = java.util.zip.ZipFile(takeoutFile)
            val entries = zip.entries()
            while (entries.hasMoreElements()) {
                val entry = entries.nextElement()
                if (entry.name.contains("Profile") && entry.name.endsWith(".json")) {
                    val content = zip.getInputStream(entry).readBytes().toString(Charsets.UTF_8)
                    val emailRegex = Regex("\"value\"\\s*:\\s*\"([^\"]+@[^\"]+)\"")
                    val match = emailRegex.find(content)
                    if (match != null) {
                        zip.close()
                        return match.groupValues[1]
                    }
                }
            }
            zip.close()
            null
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Findet Takeout-Dateien für ein bestimmtes Konto.
     */
    internal fun discoverTakeoutFilesForAccount(account: android.accounts.Account): List<File> {
        val takeoutFiles = mutableListOf<File>()
        val searchDirs = listOf(
            File("/sdcard/Download"),
            File("/sdcard/Documents"),
            File("/storage/emulated/0/Download"),
            File("/storage/emulated/0/Documents")
        )

        searchDirs.forEach { dir ->
            if (!dir.exists() || !dir.canRead()) return@forEach
            dir.listFiles()?.forEach { file ->
                if (file.name.startsWith("takeout-") && file.name.endsWith(".zip") && file.length() < MAX_GEMINI_TAKEOUT_SIZE) {
                    takeoutFiles.add(file)
                }
            }
        }

        return takeoutFiles.sortedByDescending { it.lastModified() }
    }

    /**
     * Persistiert eine Gemini-Konversation in der Vault-Datenbank.
     */
    internal fun persistGeminiConversation(accountEmail: String, takeoutFile: File, conversation: GeminiChatParser.GeminiConversation) {
        vaultDb.execSQL(
            """
            CREATE TABLE IF NOT EXISTS gemini_conversations (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                conversation_id TEXT NOT NULL,
                account_email TEXT NOT NULL,
                title TEXT,
                timestamp_epoch INTEGER,
                message_count INTEGER,
                generated_images INTEGER DEFAULT 0,
                generated_audio INTEGER DEFAULT 0,
                generated_videos INTEGER DEFAULT 0,
                content_preview TEXT,
                phase_vector BLOB(128),
                semantic_mass REAL DEFAULT 1.0,
                source_takeout TEXT,
                discovered_at INTEGER,
                UNIQUE(conversation_id, account_email)
            );
            """.trimIndent()
        )

        val contentPreview = conversation.messages.take(3).joinToString("\n") { "${it.role}: ${it.content.take(200)}" }
        val phaseVector = if (conversation.messages.isNotEmpty()) {
            val fullText = conversation.messages.joinToString("\n") { it.content }
            matrixIngestionEngine.projectToPhaseVector(fullText)
        } else null
        val semanticMass = if (conversation.messages.isNotEmpty()) {
            val fullText = conversation.messages.joinToString("\n") { it.content }
            val domain = matrixIngestionEngine.classifyDomain(fullText)
            matrixIngestionEngine.calculateSemanticMass(fullText, domain)
        } else 0.0f

        val phaseBlob = phaseVector?.let { serializePhaseVector(it) }
        vaultDb.execSQL(
            """
            INSERT OR REPLACE INTO gemini_conversations 
            (conversation_id, account_email, title, timestamp_epoch, message_count, generated_images, generated_audio, generated_videos, content_preview, phase_vector, semantic_mass, source_takeout, discovered_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """.trimIndent(),
            arrayOf(
                conversation.id,
                accountEmail,
                conversation.title,
                conversation.timestamp,
                conversation.messages.size,
                conversation.generatedImages.size,
                conversation.generatedAudio.size,
                conversation.generatedVideos.size,
                contentPreview,
                phaseBlob,
                semanticMass,
                takeoutFile.name,
                System.currentTimeMillis()
            )
        )

        // Persistiere einzelne Nachrichten
        conversation.messages.forEachIndexed { index, msg ->
            persistGeminiMessage(conversation.id, accountEmail, index, msg)
        }
    }

    /**
     * Persistiert eine einzelne Gemini-Nachricht.
     */
    internal fun persistGeminiMessage(conversationId: String, accountEmail: String, index: Int, message: GeminiChatParser.GeminiMessage) {
        vaultDb.execSQL(
            """
            CREATE TABLE IF NOT EXISTS gemini_messages (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                message_id TEXT NOT NULL,
                conversation_id TEXT NOT NULL,
                account_email TEXT NOT NULL,
                role TEXT,
                content TEXT,
                timestamp_epoch INTEGER,
                content_hash TEXT,
                message_index INTEGER,
                discovered_at INTEGER,
                UNIQUE(message_id, conversation_id)
            );
            """.trimIndent()
        )

        val messageId = "${conversationId}_msg_$index"
        val contentHash = calculateSHA256(message.content)
        vaultDb.execSQL(
            """
            INSERT OR REPLACE INTO gemini_messages 
            (message_id, conversation_id, account_email, role, content, timestamp_epoch, content_hash, message_index, discovered_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
            """.trimIndent(),
            arrayOf(
                messageId,
                conversationId,
                accountEmail,
                message.role,
                message.content.take(10000),
                message.timestamp,
                contentHash,
                index,
                System.currentTimeMillis()
            )
        )
    }

    // =========================================================================
    // PERSISTENZ
    // =========================================================================

    internal fun persistRecoveredFile(recovery: DeletedFileRecovery) {
        vaultDb.execSQL(
            """
            CREATE TABLE IF NOT EXISTS recovered_files (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                file_path TEXT NOT NULL,
                sha256 TEXT,
                recovery_confidence REAL,
                file_signature TEXT,
                size_bytes INTEGER,
                fragment_chain TEXT,
                discovered_at INTEGER
            );
            """.trimIndent()
        )

        val fragmentChainJson = recovery.fragmentChain.joinToString(",", "[", "]")
        vaultDb.execSQL(
            """
            INSERT OR REPLACE INTO recovered_files 
            (file_path, sha256, recovery_confidence, file_signature, size_bytes, fragment_chain, discovered_at)
            VALUES (?, ?, ?, ?, ?, ?, ?)
            """.trimIndent(),
            arrayOf(
                recovery.filePath,
                recovery.sha256,
                recovery.recoveryConfidence,
                recovery.fileSignature,
                recovery.sizeBytes,
                fragmentChainJson,
                System.currentTimeMillis()
            )
        )
    }

    internal fun persistGoogleAccountImage(image: GoogleAccountImage) {
        vaultDb.execSQL(
            """
            CREATE TABLE IF NOT EXISTS google_account_images (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                account_email TEXT NOT NULL,
                image_path TEXT NOT NULL,
                exif_metadata TEXT,
                perceptual_hash TEXT,
                dominant_colors TEXT,
                face_detected INTEGER,
                timestamp_epoch INTEGER,
                file_size INTEGER,
                discovered_at INTEGER
            );
            """.trimIndent()
        )

        val exifJson = image.exifMetadata.entries.joinToString(",", "{", "}") { "\"${it.key}\":\"${it.value}\"" }
        val colorsJson = image.dominantColors.joinToString(",", "[", "]")
        vaultDb.execSQL(
            """
            INSERT OR REPLACE INTO google_account_images 
            (account_email, image_path, exif_metadata, perceptual_hash, dominant_colors, face_detected, timestamp_epoch, file_size, discovered_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
            """.trimIndent(),
            arrayOf(
                image.accountEmail,
                image.imagePath,
                exifJson,
                image.perceptualHash,
                colorsJson,
                if (image.faceDetected) 1 else 0,
                image.timestamp,
                image.fileSize,
                System.currentTimeMillis()
            )
        )
    }

    internal fun persistTextCluster(cluster: TextDocumentCluster) {
        vaultDb.execSQL(
            """
            CREATE TABLE IF NOT EXISTS text_document_clusters (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                cluster_id TEXT NOT NULL,
                documents TEXT,
                merged_content TEXT,
                domain TEXT,
                semantic_mass REAL,
                phase_vector BLOB(128),
                merge_strategy TEXT,
                discovered_at INTEGER
            );
            """.trimIndent()
        )

        val docsJson = cluster.documents.map { it.absolutePath }.joinToString(",", "[", "]")
        val phaseBlob = serializePhaseVector(cluster.phaseVector)
        vaultDb.execSQL(
            """
            INSERT OR REPLACE INTO text_document_clusters 
            (cluster_id, documents, merged_content, domain, semantic_mass, phase_vector, merge_strategy, discovered_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?)
            """.trimIndent(),
            arrayOf(
                cluster.clusterId,
                docsJson,
                cluster.mergedContent.take(5000),
                cluster.domain.name,
                cluster.semanticMass,
                phaseBlob,
                cluster.mergeStrategy.name,
                System.currentTimeMillis()
            )
        )
    }

    internal fun persistMergedFileGroup(group: MergedFileGroup) {
        vaultDb.execSQL(
            """
            CREATE TABLE IF NOT EXISTS merged_file_groups (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                group_id TEXT NOT NULL,
                original_files TEXT,
                merged_file_path TEXT,
                merge_strategy TEXT,
                confidence REAL,
                merged_size INTEGER,
                discovered_at INTEGER
            );
            """.trimIndent()
        )

        val originalsJson = group.originalFiles.map { it.absolutePath }.joinToString(",", "[", "]")
        vaultDb.execSQL(
            """
            INSERT OR REPLACE INTO merged_file_groups 
            (group_id, original_files, merged_file_path, merge_strategy, confidence, merged_size, discovered_at)
            VALUES (?, ?, ?, ?, ?, ?, ?)
            """.trimIndent(),
            arrayOf(
                group.groupId,
                originalsJson,
                group.mergedFile?.absolutePath,
                group.mergeStrategy.name,
                group.confidence,
                group.mergedSize,
                System.currentTimeMillis()
            )
        )
    }

    // =========================================================================
    // HILFSFUNKTIONEN
    // =========================================================================

    internal fun detectFileSignature(file: File): String? {
        val buffer = ByteArray(16)
        return runCatching {
            file.inputStream().use { it.read(buffer) }
            FILE_SIGNATURES.entries.firstOrNull { (_, sig) ->
                buffer.sliceArray(sig.indices).contentEquals(sig)
            }?.key
        }.getOrDefault(null)
    }

    internal fun detectSignatureInBuffer(buffer: ByteArray, size: Int): String? {
        for ((name, sig) in FILE_SIGNATURES) {
            if (size >= sig.size && buffer.sliceArray(sig.indices).contentEquals(sig)) {
                return name
            }
        }
        return null
    }

    internal fun calculateRecoveryConfidence(content: ByteArray, signature: String): Float {
        var score = 0.5f
        // Erhöhe Confidence bei erkennbarem Footer
        if (signature == "PDF" && content.any { it == 0x25.toByte() && content.getOrNull(content.indexOf(it) + 1) == 0x25.toByte() }) {
            score += 0.3f // %%EOF
        }
        if (signature == "ZIP" && content.any { it == 0x50.toByte() && content.getOrNull(content.indexOf(it) + 1) == 0x4B.toByte() && content.getOrNull(content.indexOf(it) + 2) == 0x05.toByte() && content.getOrNull(content.indexOf(it) + 3) == 0x06.toByte() }) {
            score += 0.3f // End of Central Directory
        }
        return score.coerceIn(0.0f, 1.0f)
    }

    internal fun lookupOriginalHash(fileName: String): String {
        // In Produktion: Query HASH_REGISTRY aus Vault
        return ""
    }

    internal fun collectTextDocuments(): List<File> {
        val docs = mutableListOf<File>()
        val searchDirs = listOf(
            File("/storage/emulated/0/Documents"),
            File("/storage/emulated/0/Download"),
            File("/sdcard/Documents"),
            File("/sdcard/Download")
        )

        val extensions = setOf("txt", "md", "csv", "log", "json", "xml", "html", "htm", "pdf", "rtf")
        searchDirs.forEach { dir ->
            if (!dir.exists() || !dir.canRead()) return@forEach
            try {
                dir.walkTopDown().take(2000).forEach { file ->
                    if (file.isFile && file.extension.lowercase() in extensions && file.length() < 10 * 1024 * 1024) {
                        docs.add(file)
                    }
                }
            } catch (e: OutOfMemoryError) {
                Log.w("BootEngineAudit", "OOM during text document scan, returning partial results")
            } catch (e: Exception) {
                Log.e("BootEngineAudit", "Error during text document scan: ${e.message}")
            }
        }
        return docs
    }

    internal fun collectImageDocuments(): List<File> {
        val images = mutableListOf<File>()
        val searchDirs = listOf(
            File("/storage/emulated/0/Pictures"),
            File("/storage/emulated/0/DCIM"),
            File("/sdcard/Pictures"),
            File("/sdcard/DCIM")
        )

        searchDirs.forEach { dir ->
            if (!dir.exists() || !dir.canRead()) return@forEach
            try {
                dir.walkTopDown().take(2000).forEach { file ->
                    if (file.isFile && file.extension.lowercase() in IMAGE_EXTENSIONS && file.length() < MAX_IMAGE_SIZE) {
                        images.add(file)
                    }
                }
            } catch (e: OutOfMemoryError) {
                Log.w("BootEngineAudit", "OOM during image document scan, returning partial results")
            } catch (e: Exception) {
                Log.e("BootEngineAudit", "Error during image document scan: ${e.message}")
            }
        }
        return images
    }

    internal fun extractEmailFromProfileJson(zip: java.util.zip.ZipFile, entry: java.util.zip.ZipEntry): String? {
        return try {
            val content = zip.getInputStream(entry).readBytes().toString(Charsets.UTF_8)
            val emailRegex = Regex("\"value\"\\s*:\\s*\"([^\"]+@[^\"]+)\"")
            emailRegex.find(content)?.groupValues?.get(1)
        } catch (e: Exception) {
            null
        }
    }

    internal fun extractFileFromZip(zip: java.util.zip.ZipFile, entry: java.util.zip.ZipEntry, outputDir: File): File? {
        return try {
            val outputFile = File(outputDir, entry.name.substringAfterLast("/"))
            zip.getInputStream(entry).use { input ->
                outputFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            outputFile
        } catch (e: Exception) {
            null
        }
    }

    internal fun extractExifMetadata(imageFile: File): Map<String, String> {
        // Vereinfacht: In Produktion android.media.ExifInterface nutzen
        return mapOf(
            "FileName" to imageFile.name,
            "FileSize" to imageFile.length().toString(),
            "LastModified" to imageFile.lastModified().toString()
        )
    }

    internal fun calculatePerceptualHash(imageFile: File): String {
        // Vereinfachter pHash: Downsample auf 16x16, grayscale, hash
        return runCatching {
            val options = android.graphics.BitmapFactory.Options().apply {
                inSampleSize = calculateSampleSize(imageFile, 16, 16)
            }
            val bitmap = android.graphics.BitmapFactory.decodeFile(imageFile.absolutePath, options)
            bitmap?.let { bmp ->
                val scaled = android.graphics.Bitmap.createScaledBitmap(bmp, 16, 16, false)
                val gray = IntArray(256) { i ->
                    val x = i % 16
                    val y = i / 16
                    val pixel = scaled.getPixel(x, y)
                    (android.graphics.Color.red(pixel) * 0.299 + android.graphics.Color.green(pixel) * 0.587 + android.graphics.Color.blue(pixel) * 0.114).toInt()
                }
                val avg = gray.average().toInt()
                val bits = gray.map { if (it > avg) '1' else '0' }.joinToString("")
                bits.hashCode().toString(16)
            } ?: "0"
        }.getOrDefault("0")
    }

    internal fun calculateSampleSize(imageFile: File, reqWidth: Int, reqHeight: Int): Int {
        val options = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
        android.graphics.BitmapFactory.decodeFile(imageFile.absolutePath, options)
        var sampleSize = 1
        while (options.outWidth / sampleSize > reqWidth || options.outHeight / sampleSize > reqHeight) {
            sampleSize *= 2
        }
        return sampleSize
    }

    internal fun extractDominantColors(imageFile: File): List<Int> {
        return runCatching {
            val bitmap = android.graphics.BitmapFactory.decodeFile(imageFile.absolutePath)
            val scaled = bitmap?.let { android.graphics.Bitmap.createScaledBitmap(it, 8, 8, false) }
            val colors = mutableListOf<Int>()
            scaled?.let { s ->
                for (y in 0 until 8) {
                    for (x in 0 until 8) {
                        colors.add(s.getPixel(x, y))
                    }
                }
            }
            colors.take(5)
        }.getOrDefault(emptyList())
    }

    internal fun detectFace(imageFile: File): Boolean {
        // Vereinfacht: In Produktion ML Kit Face Detection nutzen
        return false
    }

    internal fun mergeClusterContent(documents: List<File>): String {
        return documents.sortedBy { it.lastModified() }
            .joinToString("\n\n--- DOCUMENT BREAK ---\n\n") { file ->
                runCatching { file.readText(Charsets.UTF_8) }.getOrDefault("")
            }
    }

    internal fun detectMergeStrategy(documents: List<File>): MergeStrategy {
        val names = documents.map { it.nameWithoutExtension }
        val hasNumbering = names.any { it.contains(Regex(".*?_(\\d+)$")) }
        val hasSimilarNames = names.distinct().size < names.size

        return when {
            hasNumbering && hasSimilarNames -> MergeStrategy.TIME_SEQUENCE
            else -> MergeStrategy.SEMANTIC_SIMILARITY
        }
    }

    internal fun mergeByTimeSequence(documents: List<File>): String {
        return documents.sortedBy { it.lastModified() }
            .joinToString("\n\n") { runCatching { it.readText(Charsets.UTF_8) }.getOrDefault("") }
    }

    internal fun mergeByHashChain(documents: List<File>): String {
        val sorted = documents.sortedBy { calculateSHA256(it.name) }
        return sorted.joinToString("\n\n") { runCatching { it.readText(Charsets.UTF_8) }.getOrDefault("") }
    }

    internal fun mergeByExifCluster(documents: List<File>): String {
        return documents.sortedBy { it.lastModified() }
            .joinToString("\n\n") { runCatching { it.readText(Charsets.UTF_8) }.getOrDefault("") }
    }

    internal fun groupByTimeSequence(files: List<File>): List<MergedFileGroup> {
        val groups = mutableListOf<MergedFileGroup>()
        val pattern = Regex("(.*?)_(\\d+)$")

        val grouped = files.groupBy { file ->
            val match = pattern.find(file.nameWithoutExtension)
            if (match != null) match.groupValues[1] else file.nameWithoutExtension
        }

        grouped.forEach { (baseName, groupFiles) ->
            if (groupFiles.size >= 2) {
                val mergedContent = mergeByTimeSequence(groupFiles)
                val outputFile = File(storageRoot, "merged_${baseName}_${System.currentTimeMillis()}.txt")
                val written = runCatching { outputFile.writeText(mergedContent, Charsets.UTF_8); outputFile }.getOrNull()

                groups.add(
                    MergedFileGroup(
                        groupId = "TIME_${baseName}_${System.currentTimeMillis()}",
                        originalFiles = groupFiles,
                        mergedFile = written,
                        mergeStrategy = MergeStrategy.TIME_SEQUENCE,
                        confidence = 0.9f,
                        mergedSize = written?.length() ?: 0
                    )
                )
            }
        }
        return groups
    }

    internal fun groupByHashChain(files: List<File>): List<MergedFileGroup> {
        val groups = mutableListOf<MergedFileGroup>()
        val prefixMap = mutableMapOf<String, MutableList<File>>()

        files.forEach { file ->
            val hash = calculateSHA256(file.name)
            val prefix = hash.take(8)
            prefixMap.getOrPut(prefix) { mutableListOf() }.add(file)
        }

        prefixMap.forEach { (prefix, groupFiles) ->
            if (groupFiles.size >= 2) {
                val mergedContent = mergeByHashChain(groupFiles)
                val outputFile = File(storageRoot, "merged_hash_${prefix}_${System.currentTimeMillis()}.txt")
                val written = runCatching { outputFile.writeText(mergedContent, Charsets.UTF_8); outputFile }.getOrNull()

                groups.add(
                    MergedFileGroup(
                        groupId = "HASH_${prefix}_${System.currentTimeMillis()}",
                        originalFiles = groupFiles,
                        mergedFile = written,
                        mergeStrategy = MergeStrategy.HASH_CHAIN,
                        confidence = 0.7f,
                        mergedSize = written?.length() ?: 0
                    )
                )
            }
        }
        return groups
    }

    internal fun groupByExifCluster(files: List<File>): List<MergedFileGroup> {
        val groups = mutableListOf<MergedFileGroup>()
        val timestampMap = mutableMapOf<Long, MutableList<File>>()

        files.forEach { file ->
            val exif = extractExifMetadata(file)
            val timestamp = exif["DateTimeOriginal"]?.toLongOrNull()
                ?: exif["LastModified"]?.toLongOrNull()
                ?: file.lastModified()

            val bucket = timestamp / (60 * 60 * 1000) // 1-Stunden-Buckets
            timestampMap.getOrPut(bucket) { mutableListOf() }.add(file)
        }

        timestampMap.forEach { (bucket, groupFiles) ->
            if (groupFiles.size >= 2) {
                val mergedContent = groupFiles.joinToString("\n\n") { "[${it.name}]\n" }
                val outputFile = File(storageRoot, "merged_exif_${bucket}_${System.currentTimeMillis()}.txt")
                val written = runCatching { outputFile.writeText(mergedContent, Charsets.UTF_8); outputFile }.getOrNull()

                groups.add(
                    MergedFileGroup(
                        groupId = "EXIF_${bucket}_${System.currentTimeMillis()}",
                        originalFiles = groupFiles,
                        mergedFile = written,
                        mergeStrategy = MergeStrategy.EXIF_CLUSTER,
                        confidence = 0.6f,
                        mergedSize = written?.length() ?: 0
                    )
                )
            }
        }
        return groups
    }

    internal fun distanceTo(thisPhase: PhaseVector, other: PhaseVector): Float {
        val dimSize = minOf(thisPhase.dim.size, other.dim.size)
        var sum = 0.0f
        for (i in 0 until dimSize) {
            val diff = thisPhase.dim[i] - other.dim[i]
            sum += diff * diff
        }
        return sqrt(sum)
    }

    internal fun serializePhaseVector(vector: PhaseVector): ByteArray {
        val blob = ByteArray(128)
        for (i in 0 until 32) {
            val floatBits = vector.dim.getOrElse(i) { 0f }.toRawBits()
            blob[i * 4] = (floatBits and 0xFF).toByte()
            blob[i * 4 + 1] = ((floatBits shr 8) and 0xFF).toByte()
            blob[i * 4 + 2] = ((floatBits shr 16) and 0xFF).toByte()
            blob[i * 4 + 3] = ((floatBits shr 24) and 0xFF).toByte()
        }
        return blob
    }

    internal fun calculateSHA256(input: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256")
        return digest.digest(input).joinToString("") { "%02x".format(it) }
    }

    internal fun calculateSHA256(input: String): String {
        return calculateSHA256(input.toByteArray(Charsets.UTF_8))
    }
}

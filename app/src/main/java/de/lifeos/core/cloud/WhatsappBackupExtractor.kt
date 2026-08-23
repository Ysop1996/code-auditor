package de.lifeos.core.cloud

import java.io.File

/**
 * WhatsApp Backup Extractor for LifeOS MMSI V3.8
 * 
 * Extracts and catalogs WhatsApp backup data:
 * - Message databases (msgstore.db.crypt14)
 * - Media files (Images, Videos, Documents, Audio)
 * - Backup settings
 * - Contact profiles
 */
class WhatsappBackupExtractor {

    data class WhatsappData(
        val databaseFile: File?,
        val mediaFolders: Map<String, MediaFolder>,
        val backupFiles: List<BackupFile>,
        val totalMediaSize: Long,
        val messageCount: Int?
    )

    data class MediaFolder(
        val name: String,
        val fileCount: Int,
        val totalSize: Long,
        val files: List<File>
    )

    data class BackupFile(
        val name: String,
        val size: Long,
        val type: String
    )

    /**
     * Scan for WhatsApp backup data on device
     */
    fun scanWhatsappBackups(): WhatsappData {
        val whatsappDirs = listOf(
            File("/sdcard/Android/media/com.whatsapp/WhatsApp"),
            File("/sdcard/WhatsApp")
        )

        var databaseFile: File? = null
        val mediaFolders = mutableMapOf<String, MediaFolder>()
        val backupFiles = mutableListOf<BackupFile>()
        var totalMediaSize = 0L

        whatsappDirs.forEach { dir ->
            if (dir.exists()) {
                // Find main database
                val dbDir = File(dir, "Databases")
                if (dbDir.exists()) {
                    databaseFile = dbDir.listFiles()?.firstOrNull {
                        it.name == "msgstore.db.crypt14"
                    } ?: databaseFile

                    // Also find incremental backups
                    dbDir.listFiles()?.forEach { dbFile ->
                        if (dbFile.name.startsWith("msgstore-")) {
                            backupFiles.add(BackupFile(
                                name = dbFile.name,
                                size = dbFile.length(),
                                type = "incremental_db"
                            ))
                        }
                    }
                }

                // Scan media folders
                val mediaDir = File(dir, "Media")
                if (mediaDir.exists()) {
                    mediaDir.listFiles()?.forEach { subDir ->
                        if (subDir.isDirectory) {
                            val folderData = scanMediaFolder(subDir)
                            mediaFolders[subDir.name] = folderData
                            totalMediaSize += folderData.totalSize
                        }
                    }
                }

                // Scan backup settings
                val backupDir = File(dir, "Backups")
                if (backupDir.exists()) {
                    backupDir.listFiles()?.forEach { backupFile ->
                        backupFiles.add(BackupFile(
                            name = backupFile.name,
                            size = backupFile.length(),
                            type = categorizeBackupFile(backupFile.name)
                        ))
                    }
                }
            }
        }

        return WhatsappData(
            databaseFile = databaseFile,
            mediaFolders = mediaFolders,
            backupFiles = backupFiles,
            totalMediaSize = totalMediaSize,
            messageCount = null // Requires decryption
        )
    }

    /**
     * Scan a media folder and collect metadata
     */
    private fun scanMediaFolder(folder: File): MediaFolder {
        val files = folder.listFiles()?.filter { it.isFile } ?: emptyList()
        val totalSize = files.sumOf { it.length() }
        return MediaFolder(
            name = folder.name,
            fileCount = files.size,
            totalSize = totalSize,
            files = files
        )
    }

    /**
     * Categorize backup file by name
     */
    private fun categorizeBackupFile(filename: String): String {
        return when {
            filename.contains("msgstore") -> "database"
            filename.contains("chatsettings") -> "settings"
            filename.contains("status") -> "status"
            filename.contains("wa") -> "contacts"
            filename.contains("backup_settings") -> "backup_config"
            filename.contains("commerce") -> "commerce"
            filename.contains("stickers") -> "stickers"
            filename.contains("offloaded") -> "offloaded_media"
            else -> "other"
        }
    }

    /**
     * Get WhatsApp media statistics
     */
    fun getMediaStatistics(data: WhatsappData): MediaStatistics {
        var images = 0
        var videos = 0
        var documents = 0
        var audio = 0
        var gifs = 0
        var voiceNotes = 0

        data.mediaFolders.forEach { (name, folder) ->
            when (name) {
                "WhatsApp Images" -> images += folder.fileCount
                "WhatsApp Video" -> videos += folder.fileCount
                "WhatsApp Documents" -> documents += folder.fileCount
                "WhatsApp Audio" -> audio += folder.fileCount
                "WhatsApp Animated Gifs" -> gifs += folder.fileCount
                "WhatsApp Voice Notes" -> voiceNotes += folder.fileCount
            }
        }

        return MediaStatistics(
            images = images,
            videos = videos,
            documents = documents,
            audio = audio,
            gifs = gifs,
            voiceNotes = voiceNotes,
            totalSize = data.totalMediaSize
        )
    }

    data class MediaStatistics(
        val images: Int,
        val videos: Int,
        val documents: Int,
        val audio: Int,
        val gifs: Int,
        val voiceNotes: Int,
        val totalSize: Long
    )

    /**
     * Format bytes to human-readable string
     */
    fun formatSize(bytes: Long): String {
        val units = arrayOf("B", "KB", "MB", "GB", "TB")
        var size = bytes.toDouble()
        var unitIndex = 0
        while (size >= 1024 && unitIndex < units.size - 1) {
            size /= 1024
            unitIndex++
        }
        return "%.1f %s".format(size, units[unitIndex])
    }
}

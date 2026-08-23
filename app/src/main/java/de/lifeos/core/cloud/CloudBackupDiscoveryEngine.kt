package de.lifeos.core.cloud

import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.util.Log
import de.lifeos.android.security.BlackboxVaultManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Cloud Backup Discovery Engine for LifeOS MMSI V3.8
 * 
 * Scans device for:
 * - Google accounts
 * - Google Takeout backups
 * - WhatsApp backups
 * - Password exports (Google Passwords CSV)
 * - GitHub recovery codes
 * - Contact backups (VCF)
 * - Gemini chat data
 * - Media files (Images, Videos, Documents)
 */
class CloudBackupDiscoveryEngine(
    private val context: Context
) {
    data class DiscoveryResult(
        val googleAccounts: List<GoogleAccount>,
        val takeoutBackups: List<TakeoutBackup>,
        val whatsappBackups: List<WhatsappBackup>,
        val passwordFiles: List<PasswordFile>,
        val githubData: GitHubData?,
        val contactBackups: List<ContactBackup>,
        val geminiData: GeminiData?,
        val mediaIndex: MediaIndex
    )

    data class GoogleAccount(
        val email: String,
        val type: String,
        val isPrimary: Boolean = false
    )

    data class TakeoutBackup(
        val file: File,
        val size: Long,
        val categories: List<String>,
        val accountEmail: String? = null
    )

    data class WhatsappBackup(
        val databaseFile: File,
        val mediaFolder: File,
        val mediaSize: Long,
        val backupFiles: List<File>
    )

    data class PasswordFile(
        val file: File,
        val entryCount: Int,
        val accounts: List<String>
    )

    data class GitHubData(
        val recoveryCodesFile: File?,
        val accountUsername: String?,
        val clientSecrets: List<File>
    )

    data class ContactBackup(
        val file: File,
        val contactCount: Int
    )

    data class GeminiData(
        val conversationCount: Int,
        val imageCount: Int,
        val audioCount: Int,
        val videoCount: Int,
        val takeoutFiles: List<File>
    )

    data class MediaIndex(
        val imagesCount: Int,
        val videosCount: Int,
        val documentsCount: Int,
        val audioCount: Int,
        val totalSize: Long
    )

    /**
     * Execute full cloud backup discovery scan
     */
    suspend fun executeFullDiscovery(): DiscoveryResult = withContext(Dispatchers.IO) {
        Log.d("CloudDiscovery", "Starting full cloud backup discovery...")
        val googleAccounts = scanGoogleAccounts()
        Log.d("CloudDiscovery", "Found ${googleAccounts.size} Google accounts")
        val takeoutBackups = scanTakeoutBackups()
        Log.d("CloudDiscovery", "Found ${takeoutBackups.size} Takeout backups")
        val whatsappBackups = scanWhatsappBackups()
        Log.d("CloudDiscovery", "Found ${whatsappBackups.size} WhatsApp backups")
        val passwordFiles = scanPasswordFiles()
        Log.d("CloudDiscovery", "Found ${passwordFiles.size} password files")
        val githubData = scanGitHubData()
        val contactBackups = scanContactBackups()
        val geminiData = scanGeminiData()
        val mediaIndex = scanMediaIndex()

        DiscoveryResult(
            googleAccounts = googleAccounts,
            takeoutBackups = takeoutBackups,
            whatsappBackups = whatsappBackups,
            passwordFiles = passwordFiles,
            githubData = githubData,
            contactBackups = contactBackups,
            geminiData = geminiData,
            mediaIndex = mediaIndex
        )
    }

    /**
     * Scan for configured Google accounts on device
     */
    private fun scanGoogleAccounts(): List<GoogleAccount> {
        val accounts = mutableListOf<GoogleAccount>()
        try {
            val cursor: Cursor? = context.contentResolver.query(
                Uri.parse("content://com.google.android.gms.auth.accounts/"),
                arrayOf("account_name", "account_type"),
                null, null, null
            )
            cursor?.use { c ->
                while (c.moveToNext()) {
                    val email = c.getString(0) ?: continue
                    val type = c.getString(1) ?: "google"
                    accounts.add(GoogleAccount(email = email, type = type))
                }
            }
        } catch (e: Exception) {
            // Fallback: scan from AccountManager
            try {
                val accountManager = android.accounts.AccountManager.get(context)
                val googleAccountsList = accountManager.getAccountsByType("com.google")
                googleAccountsList.forEach { account ->
                    accounts.add(GoogleAccount(
                        email = account.name,
                        type = account.type,
                        isPrimary = account == googleAccountsList.firstOrNull()
                    ))
                }
            } catch (e2: Exception) {
                // Permission denied or no accounts
            }
        }
        return accounts.distinctBy { it.email.lowercase() }
    }

    /**
     * Scan for Google Takeout backup files
     */
    private fun scanTakeoutBackups(): List<TakeoutBackup> {
        val takeoutFiles = mutableListOf<TakeoutBackup>()
        val externalDir: java.io.File? = context.getExternalFilesDir(null)
        val searchDirs = mutableListOf<java.io.File>()
        searchDirs.add(java.io.File("/sdcard/Download"))
        searchDirs.add(java.io.File("/sdcard/Documents"))
        externalDir?.let { searchDirs.add(it) }

        searchDirs.forEach { dir ->
            dir?.listFiles()?.forEach { file ->
                if (file.name.startsWith("takeout-") && file.name.endsWith(".zip")) {
                    val categories = analyzeTakeoutCategories(file)
                    takeoutFiles.add(TakeoutBackup(
                        file = file,
                        size = file.length(),
                        categories = categories
                    ))
                }
            }
        }
        return takeoutFiles
    }

    /**
     * Analyze Takeout backup to determine contained categories
     */
    private fun analyzeTakeoutCategories(file: File): List<String> {
        val categories = mutableListOf<String>()
        try {
            val zip = java.util.zip.ZipFile(file)
            val entries = zip.entries()
            while (entries.hasMoreElements()) {
                val entry = entries.nextElement()
                val name = entry.name
                when {
                    name.contains("Gmail") || name.contains("Mail") -> categories.add("Gmail")
                    name.contains("Contacts") -> categories.add("Contacts")
                    name.contains("My Activity") -> categories.add("Activity")
                    name.contains("Drive") -> categories.add("Drive")
                    name.contains("Photos") -> categories.add("Photos")
                    name.contains("Chrome") -> categories.add("Chrome")
                    name.contains("Maps") -> categories.add("Maps")
                    name.contains("YouTube") -> categories.add("YouTube")
                    name.contains("Play Store") -> categories.add("PlayStore")
                }
            }
            zip.close()
        } catch (e: Exception) {
            // Corrupted or unreadable
        }
        return categories.distinct()
    }

    /**
     * Scan for WhatsApp backups
     */
    private fun scanWhatsappBackups(): List<WhatsappBackup> {
        val backups = mutableListOf<WhatsappBackup>()
        
        // WhatsApp database locations
        val whatsappDirs = listOf(
            File("/sdcard/Android/media/com.whatsapp/WhatsApp"),
            File("/sdcard/WhatsApp")
        )

        whatsappDirs.forEach { dir ->
            if (dir.exists()) {
                val dbDir = File(dir, "Databases")
                val mediaDir = File(dir, "Media")
                val backupDir = File(dir, "Backups")

                val dbFile = dbDir.listFiles()?.firstOrNull { 
                    it.name == "msgstore.db.crypt14" 
                }
                
                if (dbFile != null) {
                    backups.add(WhatsappBackup(
                        databaseFile = dbFile,
                        mediaFolder = mediaDir,
                        mediaSize = calculateDirSize(mediaDir),
                        backupFiles = backupDir.listFiles()?.toList() ?: emptyList()
                    ))
                }
            }
        }
        return backups
    }

    /**
     * Scan for password export files
     */
    private fun scanPasswordFiles(): List<PasswordFile> {
        val passwordFiles = mutableListOf<PasswordFile>()
        val searchDirs = listOf(
            File("/sdcard/Download"),
            File("/sdcard/Documents")
        )

        searchDirs.forEach { dir ->
            dir?.listFiles()?.forEach { file ->
                if (file.name.contains("Password", ignoreCase = true) && 
                    (file.name.endsWith(".csv") || file.name.endsWith(".json"))) {
                    val (count, accounts) = parsePasswordFile(file)
                    passwordFiles.add(PasswordFile(
                        file = file,
                        entryCount = count,
                        accounts = accounts
                    ))
                }
            }
        }
        return passwordFiles
    }

    /**
     * Parse Google Passwords CSV file
     */
    private fun parsePasswordFile(file: File): Pair<Int, List<String>> {
        var count = 0
        val accounts = mutableSetOf<String>()
        try {
            file.readLines().drop(1).forEach { line ->
                val parts = line.split(",")
                if (parts.size >= 4) {
                    count++
                    val username = parts[2].trim()
                    if (username.contains("@")) {
                        accounts.add(username.lowercase())
                    }
                }
            }
        } catch (e: Exception) {
            // Parse error
        }
        return Pair(count, accounts.toList().sorted())
    }

    /**
     * Scan for GitHub-related data
     */
    private fun scanGitHubData(): GitHubData? {
        val downloadDir = File("/sdcard/Download")
        val recoveryFile = downloadDir.listFiles()?.firstOrNull {
            it.name.contains("github-recovery", ignoreCase = true)
        }
        val clientSecrets = downloadDir.listFiles()?.filter {
            it.name.contains("client_secret", ignoreCase = true)
        } ?: emptyList()

        return GitHubData(
            recoveryCodesFile = recoveryFile,
            accountUsername = extractGitHubUsername(recoveryFile),
            clientSecrets = clientSecrets
        )
    }

    /**
     * Extract GitHub username from recovery codes file or emails
     */
    private fun extractGitHubUsername(recoveryFile: File?): String? {
        // Try to find GitHub username from email patterns
        return try {
            val content = recoveryFile?.readText() ?: ""
            // GitHub usernames often appear in emails
            null // Will be populated from email scan
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Scan for contact backup files
     */
    private fun scanContactBackups(): List<ContactBackup> {
        val contacts = mutableListOf<ContactBackup>()
        val searchDirs = listOf(
            File("/sdcard/Download"),
            File("/sdcard/Documents")
        )

        searchDirs.forEach { dir ->
            dir?.listFiles()?.forEach { file ->
                if (file.name.endsWith(".vcf") || file.name.contains("contacts", ignoreCase = true)) {
                    val count = countVcfContacts(file)
                    contacts.add(ContactBackup(file = file, contactCount = count))
                }
            }
        }
        return contacts
    }

    /**
     * Count contacts in VCF file
     */
    private fun countVcfContacts(file: File): Int {
        return try {
            file.readLines().count { it.startsWith("BEGIN:VCARD") }
        } catch (e: Exception) {
            0
        }
    }

    /**
     * Scan for Gemini chat data in Takeout backups
     */
    private fun scanGeminiData(): GeminiData? {
        val geminiParser = GeminiChatParser()
        var totalConversations = 0
        var totalImages = 0
        var totalAudio = 0
        var totalVideos = 0
        val takeoutFiles = mutableListOf<File>()

        val searchDirs = listOf(
            File("/sdcard/Download"),
            File("/sdcard/Documents")
        )

        searchDirs.forEach { dir ->
            dir?.listFiles()?.forEach { file ->
                if (file.name.startsWith("takeout-") && file.name.endsWith(".zip")) {
                    val stats = geminiParser.getGeminiStats(file)
                    if (stats.conversationCount > 0 || stats.imageCount > 0 || 
                        stats.audioCount > 0 || stats.videoCount > 0) {
                        totalConversations += stats.conversationCount
                        totalImages += stats.imageCount
                        totalAudio += stats.audioCount
                        totalVideos += stats.videoCount
                        takeoutFiles.add(file)
                    }
                }
            }
        }

        return if (takeoutFiles.isNotEmpty()) {
            GeminiData(
                conversationCount = totalConversations,
                imageCount = totalImages,
                audioCount = totalAudio,
                videoCount = totalVideos,
                takeoutFiles = takeoutFiles
            )
        } else null
    }

    /**
     * Scan media files index
     */
    private fun scanMediaIndex(): MediaIndex {
        var images = 0
        var videos = 0
        var documents = 0
        var audio = 0
        var totalSize = 0L
        val MAX_FILES = 2000

        val mediaDirs = listOf(
            File("/sdcard/Pictures"),
            File("/sdcard/Movies"),
            File("/sdcard/Music"),
            File("/sdcard/Documents"),
            File("/sdcard/DCIM")
        )

        try {
            mediaDirs.forEach { dir ->
                if (dir.exists()) {
                    dir.walkTopDown().take(MAX_FILES).forEach { file ->
                        if (file.isFile) {
                            totalSize += file.length()
                            when {
                                file.name.endsWith(".jpg") || file.name.endsWith(".png") || 
                                file.name.endsWith(".jpeg") || file.name.endsWith(".gif") -> images++
                                file.name.endsWith(".mp4") || file.name.endsWith(".mov") || 
                                file.name.endsWith(".avi") -> videos++
                                file.name.endsWith(".pdf") || file.name.endsWith(".doc") || 
                                file.name.endsWith(".docx") || file.name.endsWith(".xlsx") -> documents++
                                file.name.endsWith(".mp3") || file.name.endsWith(".wav") || 
                                file.name.endsWith(".ogg") -> audio++
                            }
                        }
                    }
                }
            }
        } catch (e: OutOfMemoryError) {
            Log.w("CloudDiscovery", "OOM during media scan, returning partial results")
        } catch (e: Exception) {
            Log.e("CloudDiscovery", "Error during media scan: ${e.message}")
        }

        return MediaIndex(
            imagesCount = images,
            videosCount = videos,
            documentsCount = documents,
            audioCount = audio,
            totalSize = totalSize
        )
    }

    /**
     * Calculate directory size recursively
     */
    private fun calculateDirSize(dir: File): Long {
        var size = 0L
        val MAX_FILES = 2000
        var scanned = 0
        if (dir.exists()) {
            try {
                dir.walkTopDown().take(MAX_FILES).forEach { file ->
                    scanned++
                    if (file.isFile) size += file.length()
                }
            } catch (e: OutOfMemoryError) {
                Log.w("CloudDiscovery", "OOM during dir size calc for ${dir.absolutePath}, returning partial: $scanned files")
            } catch (e: Exception) {
                Log.e("CloudDiscovery", "Error calculating dir size: ${e.message}")
            }
        }
        return size
    }

    /**
     * Import discovered data into vault
     */
    suspend fun importToVault(result: DiscoveryResult): ImportStats = withContext(Dispatchers.IO) {
        Log.d("CloudDiscovery", "Importing ${result.googleAccounts.size} accounts, ${result.takeoutBackups.size} backups...")
        var imported = 0
        var errors = 0

        // Import Google accounts
        result.googleAccounts.forEach { account ->
            try {
                BlackboxVaultManager.insertCloudAccount(
                    service = "google",
                    email = account.email,
                    metadata = mapOf("type" to account.type, "isPrimary" to account.isPrimary.toString())
                )
                imported++
            } catch (e: Exception) {
                Log.e("CloudDiscovery", "Error importing account: ${e.message}")
                errors++
            }
        }

        // Import password entries
        result.passwordFiles.forEach { pwFile ->
            try {
                pwFile.accounts.forEach { account ->
                    BlackboxVaultManager.insertCloudAccount(
                        service = "password_export",
                        email = account,
                        metadata = mapOf("source" to pwFile.file.name)
                    )
                }
                imported += pwFile.accounts.size
            } catch (e: Exception) {
                Log.e("CloudDiscovery", "Error importing passwords: ${e.message}")
                errors++
            }
        }

        // Import Takeout metadata
        result.takeoutBackups.forEach { backup ->
            try {
                BlackboxVaultManager.insertCloudBackup(
                    serviceName = "google_takeout",
                    fileName = backup.file.name,
                    fileSize = backup.size,
                    categories = backup.categories.joinToString(",")
                )
                imported++
            } catch (e: Exception) {
                Log.e("CloudDiscovery", "Error importing takeout: ${e.message}")
                errors++
            }
        }

        // Import WhatsApp metadata
        result.whatsappBackups.forEach { wa ->
            try {
                BlackboxVaultManager.insertCloudBackup(
                    serviceName = "whatsapp",
                    fileName = wa.databaseFile.name,
                    fileSize = wa.mediaSize,
                    categories = "messages,media,backups"
                )
                imported++
            } catch (e: Exception) {
                Log.e("CloudDiscovery", "Error importing whatsapp: ${e.message}")
                errors++
            }
        }

        Log.d("CloudDiscovery", "Import complete: $imported imported, $errors errors")
        ImportStats(imported = imported, errors = errors)
    }

    data class ImportStats(val imported: Int, val errors: Int)
}

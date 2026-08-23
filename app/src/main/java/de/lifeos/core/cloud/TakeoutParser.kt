package de.lifeos.core.cloud

import net.sqlcipher.database.SQLiteDatabase
import java.io.File
import java.util.zip.ZipFile

/**
 * Google Takeout Parser for LifeOS MMSI V3.8
 * 
 * Parses Google Takeout backup archives and extracts:
 * - Gmail messages (MBOX format)
 * - Contacts
 * - Chrome bookmarks/history
 * - Google Activity
 * - Play Store data
 * - YouTube data
 */
class TakeoutParser {

    data class ParsedTakeout(
        val categories: List<String>,
        val emailCount: Int,
        val contactCount: Int,
        val activityEntries: Int,
        val geminiFiles: Int,
        val accountEmail: String?,
        val backupDate: String?
    )

    /**
     * Parse a Takeout backup ZIP file
     */
    fun parseTakeoutFile(file: File): ParsedTakeout {
        val categories = mutableSetOf<String>()
        var emailCount = 0
        var contactCount = 0
        var activityEntries = 0
        var geminiFiles = 0
        var accountEmail: String? = null
        var backupDate: String? = null

        try {
            val zip = ZipFile(file)
            val entries = zip.entries()

            while (entries.hasMoreElements()) {
                val entry = entries.nextElement()
                val name = entry.name

                when {
                    // Gmail MBOX
                    name.contains("Gmail") || name.contains("Mail") -> {
                        categories.add("Gmail")
                        if (name.endsWith(".mbox")) {
                            emailCount = estimateMboxEmailCount(zip, entry)
                        }
                    }
                    // Contacts
                    name.contains("Contacts") || name.contains("Kontakte") -> {
                        categories.add("Contacts")
                        if (name.endsWith(".vcf")) {
                            contactCount++
                        }
                    }
                    // Chrome
                    name.contains("Chrome") -> categories.add("Chrome")
                    // My Activity
                    name.contains("My Activity") || name.contains("Meine Aktivit") -> {
                        categories.add("Activity")
                        activityEntries++
                    }
                    // Drive
                    name.contains("Drive") -> categories.add("Drive")
                    // Photos
                    name.contains("Photos") -> categories.add("Photos")
                    // Maps
                    name.contains("Maps") -> categories.add("Maps")
                    // YouTube
                    name.contains("YouTube") -> categories.add("YouTube")
                    // Play Store
                    name.contains("Play Store") || name.contains("Play Store") -> categories.add("PlayStore")
                    // Profile
                    name.contains("Profile") -> {
                        categories.add("Profile")
                        accountEmail = extractProfileEmail(zip, entry)
                    }
                    // Gemini Apps (conversations, images, audio)
                    name.contains("Gemini") || name.contains("Gemini Apps") -> {
                        categories.add("Gemini")
                        if (name.endsWith(".jpg") || name.endsWith(".png") || 
                            name.endsWith(".mp4") || name.endsWith(".wav") ||
                            name.endsWith(".zip") || name.endsWith(".html")) {
                            geminiFiles++
                        }
                    }
                }

                // Extract backup date from filename
                if (backupDate == null) {
                    backupDate = extractDateFromFilename(file.name)
                }
            }
            zip.close()
        } catch (e: Exception) {
            // Parse error
        }

        return ParsedTakeout(
            categories = categories.toList(),
            emailCount = emailCount,
            contactCount = contactCount,
            activityEntries = activityEntries,
            geminiFiles = geminiFiles,
            accountEmail = accountEmail,
            backupDate = backupDate
        )
    }

    /**
     * Estimate email count in MBOX file
     */
    private fun estimateMboxEmailCount(zip: ZipFile, entry: java.util.zip.ZipEntry): Int {
        return try {
            val inputStream = zip.getInputStream(entry)
            val reader = inputStream.bufferedReader()
            var count = 0
            var line: String?
            // Sample first 10000 lines to estimate
            var linesRead = 0
            while (reader.readLine().also { line = it } != null && linesRead < 10000) {
                if (line?.startsWith("From ") == true) {
                    count++
                }
                linesRead++
            }
            reader.close()
            // Extrapolate based on file size
            val fileSize = entry.size
            val avgEmailSize = if (count > 0) (linesRead / count) * 100L else 0L
            if (avgEmailSize > 0) {
                (fileSize / avgEmailSize).toInt().coerceAtLeast(count)
            } else {
                count
            }
        } catch (e: Exception) {
            0
        }
    }

    /**
     * Extract profile email from Takeout
     */
    private fun extractProfileEmail(zip: ZipFile, entry: java.util.zip.ZipEntry): String? {
        return try {
            val inputStream = zip.getInputStream(entry)
            val content = inputStream.readBytes().toString(Charsets.UTF_8)
            val emailRegex = Regex("\"value\"\\s*:\\s*\"([^\"]+@[^\"]+)\"")
            emailRegex.find(content)?.groupValues?.get(1)
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Extract date from Takeout filename
     */
    private fun extractDateFromFilename(filename: String): String? {
        val dateRegex = Regex("takeout-(\\d{8})T")
        return dateRegex.find(filename)?.groupValues?.get(1)?.let { dateStr ->
            "${dateStr.substring(0, 4)}-${dateStr.substring(4, 6)}-${dateStr.substring(6, 8)}"
        }
    }

    /**
     * Extract Gmail MBOX from Takeout ZIP
     */
    fun extractGmailMbox(zipFile: File, outputDir: File): File? {
        try {
            val zip = ZipFile(zipFile)
            val entries = zip.entries()
            var mboxFile: File? = null

            while (entries.hasMoreElements()) {
                val entry = entries.nextElement()
                if (entry.name.contains("Gmail") || entry.name.contains("Mail")) {
                    if (entry.name.endsWith(".mbox")) {
                        val outputFile = File(outputDir, "gmail_export.mbox")
                        val inputStream = zip.getInputStream(entry)
                        outputFile.outputStream().use { output ->
                            inputStream.copyTo(output)
                        }
                        mboxFile = outputFile
                        break
                    }
                }
            }
            zip.close()
            return mboxFile
        } catch (e: Exception) {
            return null
        }
    }

    /**
     * Extract contacts VCF from Takeout ZIP
     */
    fun extractContacts(zipFile: File, outputDir: File): List<File> {
        val contacts = mutableListOf<File>()
        try {
            val zip = ZipFile(zipFile)
            val entries = zip.entries()

            while (entries.hasMoreElements()) {
                val entry = entries.nextElement()
                if (entry.name.contains("Contacts") && entry.name.endsWith(".vcf")) {
                    val outputFile = File(outputDir, entry.name.substringAfterLast("/"))
                    val inputStream = zip.getInputStream(entry)
                    outputFile.outputStream().use { output ->
                        inputStream.copyTo(output)
                    }
                    contacts.add(outputFile)
                }
            }
            zip.close()
        } catch (e: Exception) {
            // Extract error
        }
        return contacts
    }

    /**
     * Parse MBOX file and extract email metadata
     */
    fun parseMboxMetadata(mboxFile: File): List<EmailMetadata> {
        val emails = mutableListOf<EmailMetadata>()
        try {
            val reader = mboxFile.bufferedReader()
            var currentFrom: String? = null
            var currentSubject: String? = null
            var currentDate: String? = null

            reader.forEachLine { line ->
                when {
                    line.startsWith("From ") -> {
                        // Save previous email if complete
                        if (currentFrom != null) {
                            emails.add(EmailMetadata(
                                from = currentFrom ?: "",
                                subject = currentSubject ?: "",
                                date = currentDate ?: ""
                            ))
                        }
                        currentFrom = null
                        currentSubject = null
                        currentDate = null
                    }
                    line.startsWith("From:") -> currentFrom = line.substringAfter("From:").trim()
                    line.startsWith("Subject:") -> currentSubject = line.substringAfter("Subject:").trim()
                    line.startsWith("Date:") -> currentDate = line.substringAfter("Date:").trim()
                }
            }
            reader.close()
        } catch (e: Exception) {
            // Parse error
        }
        return emails
    }

    data class EmailMetadata(
        val from: String,
        val subject: String,
        val date: String
    )
}

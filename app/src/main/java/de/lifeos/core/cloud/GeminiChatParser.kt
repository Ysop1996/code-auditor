package de.lifeos.core.cloud

import java.io.File
import java.util.zip.ZipFile

/**
 * Google Gemini Chat Parser for LifeOS MMSI V3.8
 * 
 * Extracts Gemini conversations from Google Takeout backups.
 * Gemini data includes:
 * - Text conversations (HTML exports)
 * - Generated images (JPG/PNG)
 * - Generated audio (WAV)
 - Generated videos (MP4)
 * - Conversation archives (ZIP)
 */
class GeminiChatParser {

    data class GeminiConversation(
        val id: String,
        val title: String,
        val timestamp: Long,
        val messages: List<GeminiMessage>,
        val generatedImages: List<File>,
        val generatedAudio: List<File>,
        val generatedVideos: List<File>
    )

    data class GeminiMessage(
        val role: String, // "user" or "assistant"
        val content: String,
        val timestamp: Long?
    )

    data class GeminiExport(
        val conversations: List<GeminiConversation>,
        val totalImages: Int,
        val totalAudio: Int,
        val totalVideos: Int,
        val totalMessages: Int
    )

    /**
     * Parse Gemini data from Takeout ZIP
     */
    fun parseGeminiFromTakeout(takeoutFile: File): GeminiExport {
        val conversations = mutableListOf<GeminiConversation>()
        val images = mutableListOf<File>()
        val audio = mutableListOf<File>()
        val videos = mutableListOf<File>()
        var totalMessages = 0

        try {
            val zip = ZipFile(takeoutFile)
            val entries = zip.entries()
            val tempDir = File(System.getProperty("java.io.tmpdir"), "gemini_extract")
            tempDir.mkdirs()

            while (entries.hasMoreElements()) {
                val entry = entries.nextElement()
                if (entry.name.contains("Gemini")) {
                    when {
                        entry.name.endsWith(".html") -> {
                            val conv = parseGeminiHtml(zip, entry)
                            if (conv != null) {
                                conversations.add(conv)
                                totalMessages += conv.messages.size
                            }
                        }
                        entry.name.endsWith(".jpg") || entry.name.endsWith(".png") -> {
                            val extracted = extractFile(zip, entry, tempDir)
                            if (extracted != null) images.add(extracted)
                        }
                        entry.name.endsWith(".wav") -> {
                            val extracted = extractFile(zip, entry, tempDir)
                            if (extracted != null) audio.add(extracted)
                        }
                        entry.name.endsWith(".mp4") -> {
                            val extracted = extractFile(zip, entry, tempDir)
                            if (extracted != null) videos.add(extracted)
                        }
                    }
                }
            }
            zip.close()
        } catch (e: Exception) {
            // Parse error
        }

        return GeminiExport(
            conversations = conversations,
            totalImages = images.size,
            totalAudio = audio.size,
            totalVideos = videos.size,
            totalMessages = totalMessages
        )
    }

    /**
     * Parse Gemini HTML conversation export
     */
    private fun parseGeminiHtml(zip: ZipFile, entry: java.util.zip.ZipEntry): GeminiConversation? {
        return try {
            val inputStream = zip.getInputStream(entry)
            val content = inputStream.readBytes().toString(Charsets.UTF_8)
            
            // Extract conversation title
            val titleRegex = Regex("<title>([^<]+)</title>")
            val title = titleRegex.find(content)?.groupValues?.get(1) ?: "Gemini Chat"
            
            // Extract messages from HTML
            val messages = mutableListOf<GeminiMessage>()
            
            // Pattern for user messages
            val userPattern = Regex("""class="user[^"]*"[^>]*>(.*?)</div>""", RegexOption.DOT_MATCHES_ALL)
            val assistantPattern = Regex("""class="assistant[^"]*"[^>]*>(.*?)</div>""", RegexOption.DOT_MATCHES_ALL)
            
            userPattern.findAll(content).forEach { match ->
                messages.add(GeminiMessage(
                    role = "user",
                    content = match.groupValues[1].trim().replace(Regex("<[^>]+>"), ""),
                    timestamp = null
                ))
            }
            
            assistantPattern.findAll(content).forEach { match ->
                messages.add(GeminiMessage(
                    role = "assistant",
                    content = match.groupValues[1].trim().replace(Regex("<[^>]+>"), ""),
                    timestamp = null
                ))
            }

            GeminiConversation(
                id = entry.name.hashCode().toString(),
                title = title,
                timestamp = System.currentTimeMillis(),
                messages = messages,
                generatedImages = emptyList(),
                generatedAudio = emptyList(),
                generatedVideos = emptyList()
            )
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Extract file from ZIP to temp directory
     */
    private fun extractFile(zip: ZipFile, entry: java.util.zip.ZipEntry, outputDir: File): File? {
        return try {
            val outputFile = File(outputDir, entry.name.substringAfterLast("/"))
            val inputStream = zip.getInputStream(entry)
            outputFile.outputStream().use { output ->
                inputStream.copyTo(output)
            }
            outputFile
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Get Gemini media statistics from Takeout
     */
    fun getGeminiStats(takeoutFile: File): GeminiStats {
        var images = 0
        var audio = 0
        var videos = 0
        var conversations = 0

        try {
            val zip = ZipFile(takeoutFile)
            val entries = zip.entries()

            while (entries.hasMoreElements()) {
                val entry = entries.nextElement()
                if (entry.name.contains("Gemini")) {
                    when {
                        entry.name.endsWith(".html") -> conversations++
                        entry.name.endsWith(".jpg") || entry.name.endsWith(".png") -> images++
                        entry.name.endsWith(".wav") -> audio++
                        entry.name.endsWith(".mp4") -> videos++
                    }
                }
            }
            zip.close()
        } catch (e: Exception) {
            // Parse error
        }

        return GeminiStats(
            conversationCount = conversations,
            imageCount = images,
            audioCount = audio,
            videoCount = videos
        )
    }

    data class GeminiStats(
        val conversationCount: Int,
        val imageCount: Int,
        val audioCount: Int,
        val videoCount: Int
    )
}

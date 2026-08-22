package de.lifeos.core.social

import net.sqlcipher.database.SQLiteDatabase
import java.io.File
import java.security.MessageDigest
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

class WhatsAppTranscriptParser(private val vaultDb: SQLiteDatabase) {

    private val lineRegex = Regex("""^(\d{2}\.\d{2}\.\d{2,4}),?\s+(\d{1,2}:\d{2}(?::\d{2})?)\s+-\s+([^:]+):\s+(.*)$""")
    private val dateFormatterShort = DateTimeFormatter.ofPattern("dd.MM.yy HH:mm")
    private val dateFormatterLong = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm")

    fun parseAndIngestChatExport(chatFile: File, defaultContactCategory: String = "PERSONAL") {
        if (!chatFile.exists()) return

        var currentSender = "UNKNOWN"
        chatFile.forEachLine { line ->
            val match = lineRegex.find(line)
            if (match != null) {
                val (dateStr, timeStr, sender, message) = match.destructured
                currentSender = sender.trim()
                val cleanTime = timeStr.take(5)
                
                val epoch = runCatching {
                    val dtStr = "$dateStr $cleanTime"
                    val formatter = if (dateStr.length == 8) dateFormatterShort else dateFormatterLong
                    LocalDateTime.parse(dtStr, formatter).toEpochSecond(ZoneOffset.UTC)
                }.getOrDefault(System.currentTimeMillis() / 1000)

                if (!message.contains("Nachrichten und Anrufe sind end-to-end-verschlüsselt", true)) {
                    ingestChatMessage(currentSender, message.trim(), epoch, defaultContactCategory)
                }
            }
        }
    }

    private fun ingestChatMessage(sender: String, message: String, epoch: Long, category: String) {
        val contactId = "WA_${sender.replace("\\s+".toRegex(), "_")}"
        
        vaultDb.execSQL(
            """
            INSERT OR IGNORE INTO contacts (contact_id, display_name, category, last_interaction_epoch)
            VALUES (?, ?, ?, ?)
            """.trimIndent(),
            arrayOf(contactId, sender, category, epoch)
        )

        val isUnresolved = if (message.endsWith("?") || message.contains("Frist", true) || message.contains("Rechnung", true)) 1 else 0

        val eventHash = MessageDigest.getInstance("SHA-256")
            .digest("$contactId:$epoch:$message".toByteArray())
            .joinToString("") { "%02x".format(it) }

        vaultDb.execSQL(
            "INSERT OR IGNORE INTO communication_events VALUES (?, ?, 'WHATSAPP', 'INBOUND', ?, 0, ?, ?)",
            arrayOf("WA_$eventHash", contactId, epoch, message.take(500), isUnresolved)
        )
    }
}

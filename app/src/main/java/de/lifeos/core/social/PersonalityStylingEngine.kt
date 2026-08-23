package de.lifeos.core.social

import net.sqlcipher.database.SQLiteDatabase
import net.sqlcipher.database.SQLiteCursor

enum class StylingDomain {
    PEER_CASUAL,
    BUSINESS_ASSERTIVE,
    LEGAL_ENFORCEMENT,
    INTIMATE_DIRECT
}

class PersonalityStylingEngine(private val vaultDb: SQLiteDatabase) {

    private val userVoicePatterns = mutableListOf<String>()
    private var isTrained = false

    fun trainProfileFromOutboundHistory() {
        runCatching {
            val cursor = vaultDb.rawQuery(
                "SELECT payload FROM communication_events WHERE direction = 'OUTBOUND' ORDER BY timestamp_epoch DESC LIMIT 100",
                null
            )
            cursor.use {
                while (it.moveToNext()) {
                    it.getString(0)?.let { payload -> userVoicePatterns.add(payload) }
                }
            }
        }
        isTrained = true
    }

    fun synthesizeInUserVoice(rawDraft: String, domain: StylingDomain, targetRecipientName: String = ""): String {
        val trimmed = rawDraft.trim()
        return when (domain) {
            StylingDomain.PEER_CASUAL -> applyPeerCasualStyle(trimmed)
            StylingDomain.BUSINESS_ASSERTIVE -> applyBusinessStyle(trimmed)
            StylingDomain.LEGAL_ENFORCEMENT -> applyLegalStyle(trimmed)
            StylingDomain.INTIMATE_DIRECT -> applyIntimateStyle(trimmed)
        }
    }

    private fun applyPeerCasualStyle(text: String): String {
        var result = text
        if (!result.endsWith(".") && !result.endsWith("!") && !result.endsWith("?")) {
            result += "."
        }
        return result
    }

    private fun applyBusinessStyle(text: String): String {
        var result = text
        result = result.replace(Regex("hi ", RegexOption.IGNORE_CASE), "Guten Tag ")
        result = result.replace(Regex("lg|grüße", RegexOption.IGNORE_CASE), "Mit freundlichen Grüßen")
        if (!result.contains("Mit freundlichen Grüßen", ignoreCase = true)) {
            result += "\n\nMit freundlichen Grüßen"
        }
        return result
    }

    private fun applyLegalStyle(text: String): String {
        var result = text
        if (!result.contains("Rechtsgrundlage", ignoreCase = true) && !result.contains("§", ignoreCase = true)) {
            result += "\n\nRechtsgrundlage: §§ 307 ff. BGB, § 286 BGB."
        }
        return result
    }

    private fun applyIntimateStyle(text: String): String {
        return text.trim()
    }
}

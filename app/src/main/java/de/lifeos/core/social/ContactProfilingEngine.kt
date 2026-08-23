package de.lifeos.core.social

import net.sqlcipher.database.SQLiteDatabase

data class ContactProfile(
    val contactId: String,
    val displayName: String,
    val relationshipCategory: String,
    val communicationStyle: String,
    val avgResponseDelaySec: Int,
    val sentimentBaseline: Double
)

class ContactProfilingEngine(private val vaultDb: SQLiteDatabase) {

    init {
        runCatching {
            ContactIntelligenceSchema.applySchema(vaultDb)
        }
    }

    fun getProfile(contactIdentifier: String): ContactProfile? {
        val cursor = vaultDb.rawQuery(
            "SELECT contact_id, display_name, relationship_category, communication_style, avg_response_delay_sec, sentiment_baseline FROM contact_profiles WHERE contact_id = ? OR display_name LIKE ?",
            arrayOf(contactIdentifier, "%$contactIdentifier%")
        )
        return cursor.use {
            if (it.moveToFirst()) {
                ContactProfile(
                    contactId = it.getString(0),
                    displayName = it.getString(1),
                    relationshipCategory = it.getString(2) ?: "ACQUAINTANCE",
                    communicationStyle = it.getString(3) ?: "BALANCED",
                    avgResponseDelaySec = it.getInt(4),
                    sentimentBaseline = it.getDouble(5)
                )
            } else null
        }
    }

    fun upsertProfile(profile: ContactProfile) {
        vaultDb.execSQL(
            "INSERT OR REPLACE INTO contact_profiles (contact_id, display_name, relationship_category, communication_style, avg_response_delay_sec, sentiment_baseline, last_enriched_epoch) VALUES (?, ?, ?, ?, ?, ?, ?)",
            arrayOf(profile.contactId, profile.displayName, profile.relationshipCategory, profile.communicationStyle, profile.avgResponseDelaySec, profile.sentimentBaseline, System.currentTimeMillis())
        )
    }

    fun classifyRelationship(contactId: String, interactionCount: Int, avgSentiment: Double): String {
        return when {
            interactionCount > 50 && avgSentiment > 0.6 -> "CLOSE_PEER"
            interactionCount > 20 && avgSentiment > 0.3 -> "REGULAR_PEER"
            interactionCount > 5 -> "ACQUAINTANCE"
            avgSentiment < -0.3 -> "ADVERSARIAL"
            else -> "BUSINESS_OR_LEGAL"
        }
    }
}

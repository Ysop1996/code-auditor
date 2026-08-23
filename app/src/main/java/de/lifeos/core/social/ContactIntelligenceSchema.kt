package de.lifeos.core.social

import net.sqlcipher.database.SQLiteDatabase

object ContactIntelligenceSchema {
    fun applySchema(db: SQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS contact_profiles (
                contact_id TEXT PRIMARY KEY,
                display_name TEXT NOT NULL,
                relationship_category TEXT DEFAULT 'ACQUAINTANCE',
                communication_style TEXT DEFAULT 'BALANCED',
                avg_response_delay_sec INTEGER DEFAULT 0,
                sentiment_baseline REAL DEFAULT 0.0,
                psychological_profile TEXT,
                osint_dossier TEXT,
                last_enriched_epoch INTEGER DEFAULT 0
            );
        """.trimIndent())
    }
}

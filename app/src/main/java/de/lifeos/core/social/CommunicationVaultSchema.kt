package de.lifeos.core.social

import net.sqlcipher.database.SQLiteDatabase

object CommunicationVaultSchema {
    fun applySchema(db: SQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS contacts (
                contact_id TEXT PRIMARY KEY,
                display_name TEXT NOT NULL,
                primary_phone TEXT,
                email TEXT,
                category TEXT DEFAULT 'PERSONAL',
                relationship_weight REAL DEFAULT 1.0,
                last_interaction_epoch INTEGER DEFAULT 0,
                unresolved_events_count INTEGER DEFAULT 0
            );
        """.trimIndent())

        db.execSQL("""
            CREATE TABLE IF NOT EXISTS communication_events (
                event_id TEXT PRIMARY KEY,
                contact_id TEXT NOT NULL,
                channel TEXT NOT NULL,
                direction TEXT NOT NULL,
                timestamp_epoch INTEGER NOT NULL,
                duration_seconds INTEGER DEFAULT 0,
                content_payload TEXT,
                is_unresolved INTEGER DEFAULT 0,
                FOREIGN KEY (contact_id) REFERENCES contacts(contact_id)
            );
        """.trimIndent())

        db.execSQL("CREATE INDEX IF NOT EXISTS idx_comm_timestamp ON communication_events(timestamp_epoch);")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_comm_contact ON communication_events(contact_id);")
    }
}

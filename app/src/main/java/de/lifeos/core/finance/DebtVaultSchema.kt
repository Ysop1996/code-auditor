package de.lifeos.core.finance

import net.sqlcipher.database.SQLiteDatabase

object DebtVaultSchema {
    fun applySchema(db: SQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS debt_ledger (
                creditor_id TEXT PRIMARY KEY,
                creditor_name TEXT NOT NULL,
                file_reference TEXT,
                original_claim REAL NOT NULL,
                current_claim REAL NOT NULL,
                is_titled INTEGER DEFAULT 0,
                statute_barred_epoch INTEGER,
                interest_rate REAL DEFAULT 0.0,
                last_contact_epoch INTEGER,
                strategy_status TEXT DEFAULT 'AUDIT_PENDING'
            );
        """.trimIndent())

        db.execSQL("""
            CREATE TABLE IF NOT EXISTS payment_transactions (
                tx_id TEXT PRIMARY KEY,
                creditor_id TEXT,
                amount REAL NOT NULL,
                timestamp INTEGER NOT NULL,
                allocation_target TEXT DEFAULT 'PRINCIPAL_CLAIM',
                FOREIGN KEY (creditor_id) REFERENCES debt_ledger(creditor_id)
            );
        """.trimIndent())
    }
}

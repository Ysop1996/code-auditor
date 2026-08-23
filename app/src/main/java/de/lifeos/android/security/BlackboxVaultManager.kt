package de.lifeos.android.security

import android.content.Context
import net.sqlcipher.database.SQLiteDatabase
import java.io.File

object BlackboxVaultManager {

    private const val VAULT_DIR_NAME = ".lifeos_vault"
    private const val VAULT_FILE_NAME = "lifeos_blackbox.db"

    fun getPersistentVaultFile(context: Context): File {
        // App-spezifischer externer Speicher (übersteht Updates, bei hasFragileUserData="true" auch Deinstallationen)
        val baseDir = context.getExternalFilesDir(null) ?: context.filesDir
        val vaultDir = File(baseDir, VAULT_DIR_NAME)
        if (!vaultDir.exists()) {
            vaultDir.mkdirs()
            // .nomedia Datei anlegen, damit Android MediaStore den Ordner ignoriert
            File(vaultDir, ".nomedia").createNewFile()
        }
        return File(vaultDir, VAULT_FILE_NAME)
    }

    fun openEncryptedVault(context: Context, masterSecret: ByteArray): SQLiteDatabase {
        SQLiteDatabase.loadLibs(context)
        val vaultFile = getPersistentVaultFile(context)

        // Falls DB korrumpiert oder inkompatibel: löschen und neu erstellen
        if (vaultFile.exists()) {
            runCatching {
                val testDb = SQLiteDatabase.openOrCreateDatabase(vaultFile.absolutePath, masterSecret, null, null)
                testDb.rawQuery("SELECT count(*) FROM sqlite_master", null).close()
                testDb.close()
            }.onFailure {
                vaultFile.delete()
            }
        }

        val database = SQLiteDatabase.openOrCreateDatabase(
            vaultFile.absolutePath,
            masterSecret,
            null,
            null
        )

        return try {
            database.apply {
                applySchema(this)
                applyPragmas(this)
            }
        } catch (e: Exception) {
            // Falls Schema-Erstellung fehlschlägt: DB löschen und neu versuchen
            database.close()
            vaultFile.delete()
            val retryDb = SQLiteDatabase.openOrCreateDatabase(
                vaultFile.absolutePath,
                masterSecret,
                null,
                null
            )
            retryDb.apply {
                applySchema(this)
                applyPragmas(this)
            }
            retryDb
        }
    }

    private fun applySchema(db: SQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS semantic_nodes (
                id TEXT PRIMARY KEY,
                sha256 TEXT UNIQUE,
                payload TEXT,
                mass REAL,
                phase_x REAL,
                phase_y REAL,
                phase_z REAL,
                last_updated INTEGER
            );
        """.trimIndent())

        db.execSQL("""
            CREATE TABLE IF NOT EXISTS legal_cases (
                case_id TEXT PRIMARY KEY,
                opponent TEXT,
                statutes TEXT,
                amount REAL,
                deadline_epoch INTEGER,
                status TEXT
            );
        """.trimIndent())

        db.execSQL("""
            CREATE TABLE IF NOT EXISTS communication_events (
                event_id TEXT PRIMARY KEY,
                contact_id TEXT,
                channel TEXT,
                direction TEXT,
                timestamp_epoch INTEGER,
                friction_delta REAL,
                payload TEXT,
                delivery_status INTEGER
            );
        """.trimIndent())

        // Cloud Backup Discovery Tables
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS cloud_accounts (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                service TEXT NOT NULL,
                email TEXT NOT NULL,
                metadata TEXT,
                discovered_at INTEGER,
                UNIQUE(service, email)
            );
        """.trimIndent())

        db.execSQL("""
            CREATE TABLE IF NOT EXISTS cloud_backups (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                service_name TEXT NOT NULL,
                file_name TEXT,
                file_size INTEGER,
                categories TEXT,
                import_status TEXT DEFAULT 'pending',
                discovered_at INTEGER
            );
        """.trimIndent())

        db.execSQL("""
            CREATE TABLE IF NOT EXISTS password_vault (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                service_url TEXT,
                username TEXT,
                password_encrypted TEXT,
                note TEXT,
                source_file TEXT,
                discovered_at INTEGER
            );
        """.trimIndent())

        db.execSQL("""
            CREATE TABLE IF NOT EXISTS github_data (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                username TEXT,
                recovery_codes TEXT,
                client_secrets TEXT,
                discovered_at INTEGER
            );
        """.trimIndent())

        // Gedächtnis-Matrix: Universelle Wissensrepräsentation
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS gedaechtnis_matrix (
                node_id TEXT PRIMARY KEY,
                domain TEXT NOT NULL,
                phase_vector BLOB,
                power_asymmetry REAL DEFAULT 0.0,
                emotional_bond REAL DEFAULT 0.0,
                conflict_tension REAL DEFAULT 0.0,
                semantic_mass REAL DEFAULT 1.0,
                source_channel TEXT DEFAULT 'SYSTEM',
                content_preview TEXT,
                encrypted_payload BLOB,
                created_at INTEGER,
                resonance_count INTEGER DEFAULT 1
            );
        """.trimIndent())

        // Finance & Debt Tables
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

        db.execSQL("""
            CREATE TABLE IF NOT EXISTS business_pipeline (
                deal_id TEXT PRIMARY KEY,
                client_name TEXT,
                contract_value_eur REAL NOT NULL,
                is_prepaid INTEGER DEFAULT 0,
                is_delivered INTEGER DEFAULT 0,
                expected_close_epoch INTEGER,
                notes TEXT
            );
        """.trimIndent())

        // Jobcenter Cases
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS jobcenter_cases (
                case_id TEXT PRIMARY KEY,
                client_name TEXT,
                benefit_type TEXT,
                monthly_amount REAL,
                start_date TEXT,
                jobcenter_name TEXT,
                status TEXT DEFAULT 'REGISTERED',
                notes TEXT
            );
        """.trimIndent())

        // Indizes für Matrix-Abfragen
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_matrix_domain ON gedaechtnis_matrix(domain);")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_matrix_created ON gedaechtnis_matrix(created_at DESC);")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_matrix_mass ON gedaechtnis_matrix(semantic_mass DESC);")

        // Calendar & Task Tables
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS calendar_events (
                event_id TEXT PRIMARY KEY,
                title TEXT NOT NULL,
                description TEXT,
                event_type TEXT DEFAULT 'APPOINTMENT',
                start_epoch INTEGER NOT NULL,
                end_epoch INTEGER,
                location TEXT,
                is_all_day INTEGER DEFAULT 0,
                is_recurring INTEGER DEFAULT 0,
                recurrence_rule TEXT,
                priority INTEGER DEFAULT 0,
                status TEXT DEFAULT 'PENDING',
                created_at INTEGER,
                updated_at INTEGER
            );
        """.trimIndent())

        db.execSQL("""
            CREATE TABLE IF NOT EXISTS calendar_tasks (
                task_id TEXT PRIMARY KEY,
                event_id TEXT,
                title TEXT NOT NULL,
                description TEXT,
                task_type TEXT DEFAULT 'ACTION',
                due_epoch INTEGER,
                is_completed INTEGER DEFAULT 0,
                completed_epoch INTEGER,
                priority INTEGER DEFAULT 0,
                trigger_type TEXT DEFAULT 'MANUAL',
                trigger_payload TEXT,
                created_at INTEGER,
                updated_at INTEGER,
                FOREIGN KEY (event_id) REFERENCES calendar_events(event_id)
            );
        """.trimIndent())

        db.execSQL("CREATE INDEX IF NOT EXISTS idx_calendar_events_start ON calendar_events(start_epoch);")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_calendar_tasks_due ON calendar_tasks(due_epoch);")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_calendar_tasks_completed ON calendar_tasks(is_completed);")

        // Document Viewer Tables
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS documents (
                doc_id TEXT PRIMARY KEY,
                title TEXT NOT NULL,
                doc_type TEXT DEFAULT 'GENERATED',
                content_uri TEXT,
                content_text TEXT,
                mime_type TEXT,
                file_size INTEGER,
                source_engine TEXT,
                metadata TEXT,
                created_at INTEGER,
                updated_at INTEGER
            );
        """.trimIndent())

        db.execSQL("""
            CREATE TABLE IF NOT EXISTS document_tags (
                tag_id TEXT PRIMARY KEY,
                doc_id TEXT NOT NULL,
                tag TEXT NOT NULL,
                created_at INTEGER,
                FOREIGN KEY (doc_id) REFERENCES documents(doc_id)
            );
        """.trimIndent())

        db.execSQL("CREATE INDEX IF NOT EXISTS idx_documents_type ON documents(doc_type);")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_documents_created ON documents(created_at DESC);")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_document_tags_doc ON document_tags(doc_id);")
    }

    private fun applyPragmas(db: SQLiteDatabase) {
        db.rawQuery("PRAGMA journal_mode = WAL;", null).close()
        db.execSQL("PRAGMA synchronous = NORMAL;")
    }

    /**
     * Insert discovered cloud account into vault
     */
    fun insertCloudAccount(service: String, email: String, metadata: Map<String, String>) {
        val db = getDb() ?: return
        val metaJson = metadata.entries.joinToString(",", "{", "}") { "\"${it.key}\":\"${it.value}\"" }
        db.execSQL(
            "INSERT OR REPLACE INTO cloud_accounts (service, email, metadata, discovered_at) VALUES (?, ?, ?, ?)",
            arrayOf(service, email, metaJson, System.currentTimeMillis())
        )
    }

    /**
     * Insert cloud backup metadata into vault
     */
    fun insertCloudBackup(serviceName: String, fileName: String, fileSize: Long, categories: String) {
        val db = getDb() ?: return
        db.execSQL(
            "INSERT INTO cloud_backups (service_name, file_name, file_size, categories, discovered_at) VALUES (?, ?, ?, ?, ?)",
            arrayOf(serviceName, fileName, fileSize, categories, System.currentTimeMillis())
        )
    }

    /**
     * Get all discovered cloud accounts
     */
    fun getCloudAccounts(): List<Map<String, Any?>> {
        val db = getDb() ?: return emptyList()
        val cursor = db.rawQuery("SELECT service, email, metadata FROM cloud_accounts ORDER BY service, email", null)
        val results = mutableListOf<Map<String, Any?>>()
        cursor.use {
            while (it.moveToNext()) {
                results.add(mapOf(
                    "service" to it.getString(0),
                    "email" to it.getString(1),
                    "metadata" to it.getString(2)
                ))
            }
        }
        return results
    }

    /**
     * Get all cloud backups
     */
    fun getCloudBackups(): List<Map<String, Any?>> {
        val db = getDb() ?: return emptyList()
        val cursor = db.rawQuery("SELECT service_name, file_name, file_size, categories FROM cloud_backups", null)
        val results = mutableListOf<Map<String, Any?>>()
        cursor.use {
            while (it.moveToNext()) {
                results.add(mapOf(
                    "service" to it.getString(0),
                    "file" to it.getString(1),
                    "size" to it.getLong(2),
                    "categories" to it.getString(3)
                ))
            }
        }
        return results
    }

    // =========================================================================
    // CALENDAR & TASK OPERATIONS
    // =========================================================================

    fun insertCalendarEvent(
        eventId: String,
        title: String,
        description: String?,
        eventType: String,
        startEpoch: Long,
        endEpoch: Long?,
        location: String?,
        isAllDay: Boolean,
        isRecurring: Boolean,
        recurrenceRule: String?,
        priority: Int,
        status: String
    ) {
        val db = getDb() ?: return
        val now = System.currentTimeMillis()
        db.execSQL(
            """INSERT OR REPLACE INTO calendar_events 
                (event_id, title, description, event_type, start_epoch, end_epoch, location, 
                 is_all_day, is_recurring, recurrence_rule, priority, status, created_at, updated_at) 
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)""",
            arrayOf(
                eventId, title, description, eventType, startEpoch, endEpoch, location,
                if (isAllDay) 1 else 0, if (isRecurring) 1 else 0, recurrenceRule, priority, status, now, now
            )
        )
    }

    fun getCalendarEvents(startEpoch: Long, endEpoch: Long): List<Map<String, Any?>> {
        val db = getDb() ?: return emptyList()
        val cursor = db.rawQuery(
            """SELECT event_id, title, description, event_type, start_epoch, end_epoch, 
                      location, is_all_day, is_recurring, recurrence_rule, priority, status 
               FROM calendar_events 
               WHERE start_epoch >= ? AND start_epoch <= ? 
               ORDER BY start_epoch ASC""",
            arrayOf(startEpoch.toString(), endEpoch.toString())
        )
        val results = mutableListOf<Map<String, Any?>>()
        cursor.use {
            while (it.moveToNext()) {
                results.add(mapOf(
                    "event_id" to it.getString(0),
                    "title" to it.getString(1),
                    "description" to it.getString(2),
                    "event_type" to it.getString(3),
                    "start_epoch" to it.getLong(4),
                    "end_epoch" to it.getLong(5),
                    "location" to it.getString(6),
                    "is_all_day" to (it.getInt(7) == 1),
                    "is_recurring" to (it.getInt(8) == 1),
                    "recurrence_rule" to it.getString(9),
                    "priority" to it.getInt(10),
                    "status" to it.getString(11)
                ))
            }
        }
        return results
    }

    fun insertCalendarTask(
        taskId: String,
        eventId: String?,
        title: String,
        description: String?,
        taskType: String,
        dueEpoch: Long?,
        isCompleted: Boolean,
        completedEpoch: Long?,
        priority: Int,
        triggerType: String,
        triggerPayload: String?
    ) {
        val db = getDb() ?: return
        val now = System.currentTimeMillis()
        db.execSQL(
            """INSERT OR REPLACE INTO calendar_tasks 
                (task_id, event_id, title, description, task_type, due_epoch, is_completed, 
                 completed_epoch, priority, trigger_type, trigger_payload, created_at, updated_at) 
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)""",
            arrayOf(
                taskId, eventId, title, description, taskType, dueEpoch,
                if (isCompleted) 1 else 0, completedEpoch, priority, triggerType, triggerPayload, now, now
            )
        )
    }

    fun getPendingTasks(limit: Int = 100): List<Map<String, Any?>> {
        val db = getDb() ?: return emptyList()
        val cursor = db.rawQuery(
            """SELECT task_id, event_id, title, description, task_type, due_epoch, 
                      is_completed, completed_epoch, priority, trigger_type, trigger_payload 
               FROM calendar_tasks 
               WHERE is_completed = 0 
               ORDER BY priority DESC, due_epoch ASC 
               LIMIT ?""",
            arrayOf(limit.toString())
        )
        val results = mutableListOf<Map<String, Any?>>()
        cursor.use {
            while (it.moveToNext()) {
                results.add(mapOf(
                    "task_id" to it.getString(0),
                    "event_id" to it.getString(1),
                    "title" to it.getString(2),
                    "description" to it.getString(3),
                    "task_type" to it.getString(4),
                    "due_epoch" to it.getLong(5),
                    "is_completed" to (it.getInt(6) == 1),
                    "completed_epoch" to it.getLong(7),
                    "priority" to it.getInt(8),
                    "trigger_type" to it.getString(9),
                    "trigger_payload" to it.getString(10)
                ))
            }
        }
        return results
    }

    fun completeTask(taskId: String) {
        val db = getDb() ?: return
        val now = System.currentTimeMillis()
        db.execSQL(
            "UPDATE calendar_tasks SET is_completed = 1, completed_epoch = ?, updated_at = ? WHERE task_id = ?",
            arrayOf(now, now, taskId)
        )
    }

    // =========================================================================
    // DOCUMENT OPERATIONS
    // =========================================================================

    fun insertDocument(
        docId: String,
        title: String,
        docType: String,
        contentUri: String?,
        contentText: String?,
        mimeType: String?,
        fileSize: Long,
        sourceEngine: String?,
        metadata: String?
    ) {
        val db = getDb() ?: return
        val now = System.currentTimeMillis()
        db.execSQL(
            """INSERT OR REPLACE INTO documents 
                (doc_id, title, doc_type, content_uri, content_text, mime_type, 
                 file_size, source_engine, metadata, created_at, updated_at) 
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)""",
            arrayOf(docId, title, docType, contentUri, contentText, mimeType, fileSize, sourceEngine, metadata, now, now)
        )
    }

    fun getAllDocuments(): List<Map<String, Any?>> {
        val db = getDb() ?: return emptyList()
        val cursor = db.rawQuery(
            """SELECT doc_id, title, doc_type, content_uri, content_text, mime_type, 
                      file_size, source_engine, metadata, created_at 
               FROM documents 
               ORDER BY created_at DESC""",
            null
        )
        val results = mutableListOf<Map<String, Any?>>()
        cursor.use {
            while (it.moveToNext()) {
                results.add(mapOf(
                    "doc_id" to it.getString(0),
                    "title" to it.getString(1),
                    "doc_type" to it.getString(2),
                    "content_uri" to it.getString(3),
                    "content_text" to it.getString(4),
                    "mime_type" to it.getString(5),
                    "file_size" to it.getLong(6),
                    "source_engine" to it.getString(7),
                    "metadata" to it.getString(8),
                    "created_at" to it.getLong(9)
                ))
            }
        }
        return results
    }

    fun getDocumentById(docId: String): Map<String, Any?>? {
        val db = getDb() ?: return null
        val cursor = db.rawQuery(
            "SELECT doc_id, title, doc_type, content_uri, content_text, mime_type, file_size, source_engine, metadata, created_at FROM documents WHERE doc_id = ?",
            arrayOf(docId)
        )
        cursor.use {
            if (it.moveToNext()) {
                return mapOf(
                    "doc_id" to it.getString(0),
                    "title" to it.getString(1),
                    "doc_type" to it.getString(2),
                    "content_uri" to it.getString(3),
                    "content_text" to it.getString(4),
                    "mime_type" to it.getString(5),
                    "file_size" to it.getLong(6),
                    "source_engine" to it.getString(7),
                    "metadata" to it.getString(8),
                    "created_at" to it.getLong(9)
                )
            }
        }
        return null
    }

    fun insertDocumentTag(docId: String, tag: String) {
        val db = getDb() ?: return
        val tagId = "${docId}_${tag.hashCode()}"
        db.execSQL(
            "INSERT OR REPLACE INTO document_tags (tag_id, doc_id, tag, created_at) VALUES (?, ?, ?, ?)",
            arrayOf(tagId, docId, tag, System.currentTimeMillis())
        )
    }

    fun getDocumentTags(docId: String): List<String> {
        val db = getDb() ?: return emptyList()
        val cursor = db.rawQuery(
            "SELECT tag FROM document_tags WHERE doc_id = ? ORDER BY tag",
            arrayOf(docId)
        )
        val tags = mutableListOf<String>()
        cursor.use {
            while (it.moveToNext()) {
                tags.add(it.getString(0))
            }
        }
        return tags
    }

    // Reference to active database instance
    private var activeDb: SQLiteDatabase? = null

    fun setDb(db: SQLiteDatabase) {
        activeDb = db
    }

    private fun getDb(): SQLiteDatabase? = activeDb
}

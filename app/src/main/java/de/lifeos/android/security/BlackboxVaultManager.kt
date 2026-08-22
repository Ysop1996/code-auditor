package de.lifeos.android.security

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import net.sqlcipher.database.SQLiteDatabase
import java.io.File
import java.security.KeyStore
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey

object BlackboxVaultManager {
    private const val ANDROID_KEYSTORE = "AndroidKeyStore"
    private const val KEY_ALIAS = "LifeOS_Master_TEE_Key"

    fun getOrCreateMasterSecret(): ByteArray {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        if (!keyStore.containsAlias(KEY_ALIAS)) {
            val keyGenerator = KeyGenerator.getInstance(
                KeyProperties.KEY_ALGORITHM_AES,
                ANDROID_KEYSTORE
            )
            val spec = KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .setUserAuthenticationRequired(false)
                .setRandomizedEncryptionRequired(true)
                .build()

            keyGenerator.init(spec)
            keyGenerator.generateKey()
        }

        val secretKey = keyStore.getKey(KEY_ALIAS, null) as SecretKey
        return secretKey.encoded ?: ByteArray(32) { 0x4B }
    }

    fun openEncryptedVault(context: Context, key: ByteArray): SQLiteDatabase {
        SQLiteDatabase.loadLibs(context)
        val dbFile = File(context.noBackupFilesDir, "lifeos_blackbox.db")
        val db = SQLiteDatabase.openOrCreateDatabase(dbFile, key, null)
        db.rawExecSQL("PRAGMA cipher_page_size = 4096;")
        db.rawExecSQL("PRAGMA kdf_iter = 256000;")
        db.rawExecSQL("PRAGMA cipher_hmac_algorithm = HMAC_SHA512;")
        db.rawExecSQL("PRAGMA cipher_default_kdf_algorithm = PBKDF2_HMAC_SHA512;")
        db.rawExecSQL("PRAGMA journal_mode = WAL;")

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

        return db
    }
}

package de.lifeos.core.storage

import android.content.Context
import android.os.Environment
import android.util.Log
import de.lifeos.core.cloud.CloudBackupDiscoveryEngine
import de.lifeos.core.field.DeterministicFieldEngine
import de.lifeos.core.learning.MatrixIngestionEngine
import de.lifeos.core.learning.UniversalMatrixIngestionEngine
import de.lifeos.core.social.CommunicationVaultSchema
import de.lifeos.core.social.ContactIntelligenceSchema
import de.lifeos.core.storage.BootEngineAuditExtension
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import net.sqlcipher.database.SQLiteDatabase
import java.io.File

class AutonomousGenesisAutoBoot(
    private val context: Context,
    private val vaultDb: SQLiteDatabase,
    private val fieldEngine: DeterministicFieldEngine,
    private val matrixIngestionEngine: MatrixIngestionEngine = UniversalMatrixIngestionEngine(vaultDb)
) {
    private val bootScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun executeAutoBootSequence(onBootComplete: () -> Unit) {
        // Schema-Initialisierung
        CommunicationVaultSchema.applySchema(vaultDb)
        ContactIntelligenceSchema.applySchema(vaultDb)

        // Genesis-Scan: Alle relevanten Quellordner auf dem Gerät
        val extractor = BootEngineExtractor(vaultDb, fieldEngine, File(context.filesDir, "vault_storage"))

        // 1. App-interner Genesis-Drop (falls vorhanden)
        val genesisDir = File(context.filesDir, "genesis_drop")
        if (genesisDir.exists()) {
            extractor.executeGenesisScan(genesisDir)
        }

        // 2. Download-Ordner (1400+ Dateien)
        val downloadDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        if (downloadDir.exists() && downloadDir.canRead()) {
            extractor.executeGenesisScan(downloadDir)
        }

        // 3. Documents-Ordner (330+ Dateien)
        val documentsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS)
        if (documentsDir.exists() && documentsDir.canRead()) {
            extractor.executeGenesisScan(documentsDir)
        }

        // 4. Cloud Backup Discovery (asynchron, non-blocking)
        Log.d("CloudDiscovery", "Launching cloud discovery coroutine...")
        bootScope.launch {
            try {
                Log.d("CloudDiscovery", "Cloud discovery started")
                val cloudDiscovery = CloudBackupDiscoveryEngine(context)
                val result = cloudDiscovery.executeFullDiscovery()
                cloudDiscovery.importToVault(result)
                Log.d("CloudDiscovery", "Cloud discovery completed")
            } catch (e: Exception) {
                Log.e("CloudDiscovery", "Cloud discovery failed: ${e.message}")
            }
        }

        // 5. BootEngine Audit Extension (asynchron, non-blocking)
        Log.d("BootEngineAudit", "Launching audit extension coroutine...")
        bootScope.launch {
            try {
                Log.d("BootEngineAudit", "Audit extension started")
                val auditExtension = BootEngineAuditExtension(
                    vaultDb = vaultDb,
                    fieldEngine = fieldEngine,
                    matrixIngestionEngine = matrixIngestionEngine,
                    storageRoot = File(context.filesDir, "vault_storage")
                )
                val auditReport = auditExtension.executeFullAudit()
                Log.d("BootEngineAudit", "Audit completed: ${auditReport.geminiConversationsExported} Gemini conversations exported, ${auditReport.deletedFilesRecovered} files recovered")
            } catch (e: Exception) {
                Log.e("BootEngineAudit", "Audit extension failed: ${e.message}")
            }
        }

        // Boot als abgeschlossen markieren -> Schutzfunktionen werden aktiviert
        BootStateTracker.markBootComplete()
        onBootComplete()
    }
}

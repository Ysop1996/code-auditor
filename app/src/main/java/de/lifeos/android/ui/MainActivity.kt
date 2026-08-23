package de.lifeos.android.ui

import android.Manifest
import android.os.Bundle
import android.os.Build
import android.os.Environment
import android.util.Log
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import de.lifeos.android.LifeOsApplication
import de.lifeos.android.security.BiometricAuthGate
import de.lifeos.android.security.BlackboxVaultManager
import de.lifeos.android.security.DeterministicKeyDerivation
import de.lifeos.android.security.TeeIntegrityGuard
import de.lifeos.android.telemetry.ContinuousBehaviorEngine
import de.lifeos.core.automation.AutonomousAutomationDaemon
import de.lifeos.core.cloud.CloudBackupDiscoveryEngine
import de.lifeos.core.field.DeterministicFieldEngine
import de.lifeos.core.field.FieldDynamicsService
import de.lifeos.core.legal.ProUserLegalKernel
import de.lifeos.core.legal.HighPrecedentLegalEngine
import de.lifeos.android.browser.DeepSearchOrchestrator
import de.lifeos.core.social.*
import de.lifeos.core.storage.AutonomousGenesisAutoBoot
import de.lifeos.core.workshop.AdaptiveWorkshopSynthesizer
import de.lifeos.core.sentinel.ProactiveSentinelEngine
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import net.sqlcipher.database.SQLiteDatabase
import kotlin.system.exitProcess

class MainActivity : FragmentActivity() {
    private val fieldEngine = DeterministicFieldEngine()
    private val behaviorEngine by lazy { ContinuousBehaviorEngine(fieldEngine) }
    private val legalKernel = ProUserLegalKernel()
    private val outboundGovernor = OutboundCommunicationGovernor()
    private lateinit var fieldDynamicsService: FieldDynamicsService
    private var isUnlocked by mutableStateOf(false)
    private var chatEngine: InteractiveLifeChatEngine? = null
    private var coreServicesStarted = false
    private var cloudDiscoveryResult by mutableStateOf<CloudBackupDiscoveryEngine.DiscoveryResult?>(null)
    private var showBrowser by mutableStateOf(false)
    private var workshopSynthesizer: AdaptiveWorkshopSynthesizer? = null
    private lateinit var encryptedDb: net.sqlcipher.database.SQLiteDatabase

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { startCoreServices() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Field Dynamics Service VOR setContent initialisieren (vermeidet lateinit-Crash)
        fieldDynamicsService = FieldDynamicsService(lifecycleScope)
        fieldDynamicsService.startAll()

        setContent {
            val metrics by behaviorEngine.behaviorFlow.collectAsState()
            val uiMorphState by (workshopSynthesizer?.uiMorphState ?: flowOf(AdaptiveWorkshopSynthesizer.UiMorphState.Default)).collectAsState(initial = AdaptiveWorkshopSynthesizer.UiMorphState.Default)
            val unifiedState by fieldDynamicsService.unifiedState.collectAsState()
            val homoeostasisResult by fieldDynamicsService.homoeostasisResult.collectAsState()
            val interventions by fieldDynamicsService.interventions.collectAsState()
            val homoeostasisScore by fieldDynamicsService.homoeostasisScore.collectAsState()
            val activeAttractors by fieldEngine.activeNodes.collectAsState()

            if (showBrowser) {
                JobcenterBrowserScreen(
                    onBack = { showBrowser = false }
                )
            } else {
                LifeOSMainScreen(
                    metrics = metrics,
                    uiMorphState = uiMorphState,
                    activeAttractors = activeAttractors,
                    unifiedState = unifiedState,
                    homoeostasisResult = homoeostasisResult,
                    interventions = interventions,
                    homoeostasisScore = homoeostasisScore,
                    onExecuteAction = { },
                    onNavigateToChat = {
                        chatEngine?.let { engine ->
                            setContent {
                                InAppChatScreen(
                                    chatEngine = engine,
                                    onBackToDashboard = { recreate() }
                                )
                            }
                        }
                    },
                    onNavigateToBrowser = {
                        showBrowser = true
                    },
                    vaultDb = encryptedDb
                )
            }
        }

        // Permissions werden direkt angefordert
        isUnlocked = true
        permissionLauncher.launch(
            arrayOf(
                Manifest.permission.READ_CONTACTS,
                Manifest.permission.READ_CALL_LOG,
                Manifest.permission.READ_SMS
            )
        )
    }

    override fun onResume() {
        super.onResume()
        if (!coreServicesStarted) {
            startCoreServices()
        }
    }

    private fun startCoreServices() {
        if (coreServicesStarted) return
        coreServicesStarted = true

        // Field Dynamics Service wird bereits in onCreate initialisiert
        // fieldDynamicsService.startAll() bereits dort aufgerufen

        // Hardware-gebundene KDF-Schlüsselableitung (überlebt Neuinstallation)
        val masterSecret = DeterministicKeyDerivation.deriveMasterKey(applicationContext)
        encryptedDb = BlackboxVaultManager.openEncryptedVault(applicationContext, masterSecret)

        // Zuerst BootEngine ausführt (Schema wird erstellt)
        AutonomousGenesisAutoBoot(applicationContext, encryptedDb, fieldEngine).executeAutoBootSequence {
            behaviorEngine.processTelemetricCycle(null, 0.0)
        }

        // Workshop Synthesizer initialisieren (UI-Morphing + Werkzeug-Synthese)
        val legalKernel = ProUserLegalKernel()
        val precedentEngine = HighPrecedentLegalEngine()
        workshopSynthesizer = AdaptiveWorkshopSynthesizer(
            context = applicationContext,
            fieldEngine = fieldEngine,
            sentinelEngine = ProactiveSentinelEngine(encryptedDb, fieldEngine, legalKernel, precedentEngine),
            vaultDb = encryptedDb
        )

        // BOOT ABGESCHLOSSEN: Schutzfunktionen aktivieren
        activatePostBootProtections(encryptedDb)

        // Danach Styling-Engine trainieren (benötigt communication_events Tabelle)
        val stylingEngine = PersonalityStylingEngine(encryptedDb).apply { trainProfileFromOutboundHistory() }
        val profilingEngine = ContactProfilingEngine(encryptedDb)
        val deepSearchOrchestrator = DeepSearchOrchestrator(applicationContext, fieldEngine, encryptedDb)
        chatEngine = InteractiveLifeChatEngine(
            encryptedDb,
            fieldEngine,
            stylingEngine,
            profilingEngine,
            outboundGovernor,
            deepSearchOrchestrator,
            cacheDir
        )

        val automationDaemon = AutonomousAutomationDaemon(encryptedDb, fieldEngine, legalKernel)

        lifecycleScope.launch {
            while (isActive) {
                behaviorEngine.processTelemetricCycle(null, 0.42)
                automationDaemon.executeAutomationCycle()
                delay(2000)
            }
        }
    }

    /**
     * Aktiviert Schutzfunktionen NUR nach BootEngine-Abschluss.
     * Wird von startCoreServices aufgerufen nach BootStateTracker.markBootComplete().
     */
    private fun activatePostBootProtections(encryptedDb: SQLiteDatabase) {
        // TeeIntegrityGuard aktivieren
        TeeIntegrityGuard.verifySystemStateOrWipe(applicationContext, LifeOsApplication.volatileKeyBuffer, encryptedDb)
    }
}
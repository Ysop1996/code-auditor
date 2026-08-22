package de.lifeos.android.ui

import android.Manifest
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.lifecycle.lifecycleScope
import de.lifeos.android.security.BlackboxVaultManager
import de.lifeos.android.telemetry.ContinuousBehaviorEngine
import de.lifeos.core.field.AttractorNode
import de.lifeos.core.field.DeterministicFieldEngine
import de.lifeos.core.field.PhaseVector
import de.lifeos.core.legal.ProUserLegalKernel
import de.lifeos.core.storage.AutonomousGenesisAutoBoot
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val fieldEngine = DeterministicFieldEngine()
    private val behaviorEngine by lazy { ContinuousBehaviorEngine(fieldEngine) }
    private val legalKernel = ProUserLegalKernel()
    private lateinit var displayGating: ChoreographerDisplayGating
    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.values.all { it }
        if (allGranted) {
            unlockAndStartSystem()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        checkAndRequestPermissions()
    }

    private fun checkAndRequestPermissions() {
        val requiredPermissions = arrayOf(
            Manifest.permission.READ_CONTACTS,
            Manifest.permission.READ_CALL_LOG,
            Manifest.permission.READ_SMS
        )
        permissionLauncher.launch(requiredPermissions)
    }

    private fun unlockAndStartSystem() {
        val masterSecret = BlackboxVaultManager.getOrCreateMasterSecret()
        val encryptedDb = BlackboxVaultManager.openEncryptedVault(applicationContext, masterSecret)

        val defaultDispute = legalKernel.compileDispute(
            opponentName = "Muster Inkasso / Dienstleister",
            opponentAddress = "Musterstraße 1, 10115 Berlin",
            disputeSubject = "Unberechtigte Vertragsverlängerung",
            isAgbDefense = true
        )
        fieldEngine.registerNode(
            AttractorNode(
                id = "BGB_§307_KLAUSELABWEHR",
                payload = defaultDispute.generatedLetter,
                position = PhaseVector(FloatArray(32) { 0.2f }),
                mass = 3.2f,
                isTerminal = true
            )
        )

        displayGating = ChoreographerDisplayGating {
            // Frame-Tick: synchronisiert mit Display-Refresh-Rate
            // Bei hoher Reibung wird ein Sofort-Telemetry-Zyklus ausgelöst
            val metrics = behaviorEngine.behaviorFlow.value
            if (metrics.frictionW > 1.0) {
                behaviorEngine.processTelemetricCycle(null, 0.42)
            }
        }
        displayGating.start()

        val autoBoot = AutonomousGenesisAutoBoot(applicationContext, encryptedDb, fieldEngine)
        autoBoot.executeAutoBootSequence {
            behaviorEngine.processTelemetricCycle(null, 0.0)
        }

        lifecycleScope.launch {
            while (isActive) {
                behaviorEngine.processTelemetricCycle(null, 0.42)
                displayGating.updateGating(behaviorEngine.behaviorFlow.value)
                delay(1500)
            }
        }

        setContent {
            val metrics by behaviorEngine.behaviorFlow.collectAsState()
            var activeNodes by remember { mutableStateOf(listOf<AttractorNode>()) }

            LaunchedEffect(metrics.frictionW) {
                val stimulus = PhaseVector(FloatArray(32) { metrics.frictionW.toFloat() * 0.1f })
                activeNodes = fieldEngine.executeTrajectory(stimulus)
            }

            WeltformelDashboard(
                metrics = metrics,
                activeAttractors = activeNodes,
                onExecuteAction = { node ->
                    fieldEngine.currentRho = maxOf(0.01f, fieldEngine.currentRho - 0.5f)
                    behaviorEngine.processTelemetricCycle(null, 0.0)
                }
            )
        }
    }

    override fun onDestroy() {
        displayGating.stop()
        super.onDestroy()
    }
}

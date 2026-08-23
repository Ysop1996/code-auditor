package de.lifeos.core.field

import kotlin.math.*

/**
 * FIELD INTEGRATION LAYER — Feld-Integrationsschicht
 *
 * Die FieldIntegrationLayer verbindet alle Feld-Module zu einem
 * einheitlichen System. Sie orchestriert die Dynamik, verwaltet
 * den Lebenszyklus und stellt eine einheitliche API bereit.
 *
 * Module:
 * - P0: CognitiveStateVector, PresentProjector, EffectiveDimension
 * - P1: LoadFunction, DecouplingOperator, SovereigntyIndex
 * - P2: AutopoieticOperator, PgoTunnelOperator, LindbladMasterEquation
 * - P3: ArnoldTongueAnalyzer, EmpiricalValidationMatrix
 * - P4: U1GaugeField, PathIntegralOperator
 * - P5: FieldDynamicsIntegrator
 * - P6: HomoeostasisRegulator
 * - P7: ResonanceCalibrator
 * - P8: LifeTrajectoryPlanner
 * - P9: FieldDynamicsService
 * - P10: FieldAwareCapabilitySynthesizer
 * - P11: ConsciousnessMonitor
 * - P12: DreamStateOperator
 * - P13: MemoryConsolidation
 * - P14: PredictiveFieldEngine
 * - P15: SocialFieldInterface
 * - P16: SpectralFieldAnalyzer
 * - P17: AutopoieticMaintenance
 * - P18: FieldVisualizationEngine
 * - P19: QuantumResonanceBridge
 * - P20: DeterministicFieldEngine
 *
 * Vektoren:
 * - [EXP-FORCE] System-Integration: Nahtlose Zusammenarbeit aller Module
 * - [EXP-AUTO] Autopoietische Orchestrierung: Selbst-organisierende Dynamik
 * - [EXP-SPEED] O(1) Modul-Zugriff, O(n) System-Update
 */
object FieldIntegrationLayer {

    // =========================================================================
    // INTEGRATION STATE
    // =========================================================================

    /**
     * Integrations-Status.
     */
    enum class IntegrationStatus {
        /** Initialisiert */
        INITIALIZED,

        /** Läuft */
        RUNNING,

        /** Pausiert */
        PAUSED,

        /** Gestoppt */
        STOPPED,

        /** Fehler */
        ERROR
    }

    /**
     * Integrations-Zustand.
     *
     * @param status Status
     * @param activeModules Aktive Module
     * @param lastUpdate Letztes Update
     * @param errorMessage Fehlermeldung, null wenn kein Fehler
     */
    data class IntegrationState(
        val status: IntegrationStatus,
        val activeModules: Set<String>,
        val lastUpdate: Long,
        val errorMessage: String? = null
    ) {
        val isRunning: Boolean
            get() = status == IntegrationStatus.RUNNING

        val hasError: Boolean
            get() = status == IntegrationStatus.ERROR

        override fun toString(): String {
            return buildString {
                appendLine("=== INTEGRATION STATE ===")
                appendLine("Status: $status")
                appendLine("Active Modules: ${activeModules.size}")
                appendLine("Last Update: $lastUpdate")
                errorMessage?.let { appendLine("Error: $it") }
            }
        }
    }

    // =========================================================================
    // MODULE REGISTRY
    // =========================================================================

    private val registeredModules = mutableMapOf<String, FieldModule>()
    private var currentState: IntegrationState = IntegrationState(
        status = IntegrationStatus.INITIALIZED,
        activeModules = emptySet(),
        lastUpdate = System.currentTimeMillis()
    )

    /**
     * Registriert ein Feld-Modul.
     *
     * @param module Modul
     * @return Erfolg
     */
    @Synchronized
    fun registerModule(module: FieldModule): Boolean {
        val id = module.moduleId
        if (registeredModules.containsKey(id)) return false
        registeredModules[id] = module
        return true
    }

    /**
     * Aktiviert ein Modul.
     *
     * @param moduleId Modul-ID
     * @return Erfolg
     */
    @Synchronized
    fun activateModule(moduleId: String): Boolean {
        val module = registeredModules[moduleId] ?: return false
        val success = module.activate()
        if (success) {
            currentState = currentState.copy(
                activeModules = currentState.activeModules + moduleId,
                lastUpdate = System.currentTimeMillis()
            )
        }
        return success
    }

    /**
     * Deaktiviert ein Modul.
     *
     * @param moduleId Modul-ID
     * @return Erfolg
     */
    @Synchronized
    fun deactivateModule(moduleId: String): Boolean {
        val module = registeredModules[moduleId] ?: return false
        val success = module.deactivate()
        if (success) {
            currentState = currentState.copy(
                activeModules = currentState.activeModules - moduleId,
                lastUpdate = System.currentTimeMillis()
            )
        }
        return success
    }

    /**
     * Gibt ein registriertes Modul zurück.
     */
    fun getModule(moduleId: String): FieldModule? = registeredModules[moduleId]

    /**
     * Gibt alle registrierten Module zurück.
     */
    fun getAllModules(): List<FieldModule> = registeredModules.values.toList()

    /**
     * Gibt die Anzahl registrierter Module zurück.
     */
    fun getModuleCount(): Int = registeredModules.size

    // =========================================================================
    // FIELD ORCHESTRATOR
    // =========================================================================

    /**
     * Führt einen Integrations-Schritt aus.
     *
     * @return Integrations-Ergebnis
     */
    fun orchestrateStep(): OrchestrationResult {
        if (currentState.status != IntegrationStatus.RUNNING) {
            return OrchestrationResult(false, "System not running", emptyList())
        }

        val events = mutableListOf<FieldEvent>()
        val startTime = System.currentTimeMillis()

        // Update alle aktiven Module
        for (moduleId in currentState.activeModules) {
            val module = registeredModules[moduleId] ?: continue
            try {
                val event = module.update()
                if (event != null) {
                    events.add(event)
                }
            } catch (e: Exception) {
                events.add(
                    FieldEvent(
                        type = FieldEventType.ERROR,
                        moduleId = moduleId,
                        message = e.message ?: "Unknown error",
                        timestamp = System.currentTimeMillis()
                    )
                )
            }
        }

        val duration = System.currentTimeMillis() - startTime
        currentState = currentState.copy(lastUpdate = System.currentTimeMillis())

        return OrchestrationResult(
            success = true,
            message = "Step completed in ${duration}ms",
            events = events
        )
    }

    /**
     * Orchestrierungs-Ergebnis.
     *
     * @param success Erfolgsstatus
     * @param message Nachricht
     * @param events Ereignisse
     */
    data class OrchestrationResult(
        val success: Boolean,
        val message: String,
        val events: List<FieldEvent>
    )

    // =========================================================================
    // FIELD EVENT SYSTEM
    // =========================================================================

    private val eventListeners = mutableMapOf<FieldEventType, MutableList<(FieldEvent) -> Unit>>()

    /**
     * Registriert einen Event-Listener.
     *
     * @param type Ereignistyp
     * @param listener Listener-Funktion
     */
    fun registerEventListener(type: FieldEventType, listener: (FieldEvent) -> Unit) {
        eventListeners.getOrPut(type) { mutableListOf() }.add(listener)
    }

    /**
     * Feuert ein Event.
     *
     * @param event Event
     */
    fun fireEvent(event: FieldEvent) {
        eventListeners[event.type]?.forEach { it(event) }
    }

    /**
     * Feld-Event.
     *
     * @param type Ereignistyp
     * @param moduleId Modul-ID
     * @param message Nachricht
     * @param timestamp Zeitstempel
     * @param data Zusätzliche Daten
     */
    data class FieldEvent(
        val type: FieldEventType,
        val moduleId: String,
        val message: String,
        val timestamp: Long,
        val data: Map<String, String> = emptyMap()
    ) {
        override fun toString(): String {
            return "FieldEvent[$type] $moduleId: $message"
        }
    }

    /**
     * Feld-Ereignis-Typ.
     */
    enum class FieldEventType {
        /** Modul aktiviert */
        MODULE_ACTIVATED,

        /** Modul deaktiviert */
        MODULE_DEACTIVATED,

        /** Zustandsänderung */
        STATE_CHANGED,

        /** Warnung */
        WARNING,

        /** Fehler */
        ERROR,

        /** Intervention */
        INTERVENTION,

        /** System-Update */
        SYSTEM_UPDATE
    }

    // =========================================================================
    // UNIFIED FIELD STATE
    // =========================================================================

    /**
     * Vereinheitlichter Feldzustand, der alle P0–P4-Primitive kombiniert.
     * Delegiert Kernfelder an FieldDynamicsIntegrator.UnifiedFieldState,
     * erweitert um System-Metadaten (Consciousness, Dream, Collective, Health).
     *
     * @param core Kern-Zustand aus FieldDynamicsIntegrator
     * @param consciousnessLevel Bewusstseins-Level
     * @param dreamState Traumzustand
     * @param collectiveState Kollektiver Zustand
     * @param systemHealth Systemgesundheit
     */
    data class UnifiedFieldState(
        val core: FieldDynamicsIntegrator.UnifiedFieldState,
        val consciousnessLevel: ConsciousnessMonitor.ConsciousnessLevel,
        val dreamState: DreamStateOperator.DreamSession?,
        val collectiveState: SocialFieldInterface.CollectiveState?,
        val systemHealth: AutopoieticMaintenance.SystemHealth?
    ) {
        /** Kognitive Zustandsvektor I(t) */
        val cognitiveState: CognitiveStateVector get() = core.cognitive

        /** Last W(t) */
        val load: Float get() = core.load

        /** Souveränitätsindex S_o(t) */
        val sovereignty: Float get() = core.sovereignty

        /** Effektive Dimension d_α(t) */
        val effectiveDimension: Float get() = core.effectiveDim

        /** Von-Neumann-Entropie S_vN */
        val entropy: Float get() = core.entropy

        override fun toString(): String {
            return buildString {
                appendLine("=== UNIFIED FIELD STATE ===")
                appendLine("I(t): [${"%.2f".format(cognitiveState.past)}, ${"%.2f".format(cognitiveState.present)}, ${"%.2f".format(cognitiveState.future)}]")
                appendLine("W(t): ${"%.3f".format(load)}")
                appendLine("S_o(t): ${"%.3f".format(sovereignty)}")
                appendLine("d_α(t): ${"%.1f".format(effectiveDimension)}")
                appendLine("S_vN: ${"%.4f".format(entropy)}")
                appendLine("Consciousness: $consciousnessLevel")
                appendLine("Dreaming: ${dreamState?.isActive ?: false}")
                appendLine("Health: ${systemHealth?.healthScore?.let { "%.2f".format(it) } ?: "N/A"}")
            }
        }
    }

    /**
     * Erstellt einen vereinheitlichten Feld-Zustand aus den aktuellen Modul-Zuständen.
     *
     * @return UnifiedFieldState
     */
    fun createUnifiedState(): UnifiedFieldState {
        val cognitiveState = CognitiveStateVector()
        val phase = PhaseVector(FloatArray(32) { 0.05f })
        val load = LoadFunction.computeW(cognitiveState)
        val omega = LoadFunction.integrateOmega(load, 0f, 0f, 1L)
        val sovereignty = SovereigntyIndex.computeFromStates(cognitiveState, cognitiveState)
        val effectiveDim = EffectiveDimension.compute(cognitiveState)
        val decoherenceTime = FieldDynamicsIntegrator.computeDecoherenceTime(phase)
        val entropy = FieldDynamicsIntegrator.computeVonNeumannEntropy(phase)
        val core = FieldDynamicsIntegrator.UnifiedFieldState(
            cognitive = cognitiveState,
            phase = phase,
            load = load,
            omega = omega,
            sovereignty = sovereignty,
            effectiveDim = effectiveDim,
            decoherenceTime = decoherenceTime,
            entropy = entropy
        )
        val consciousnessState = ConsciousnessMonitor.createConsciousnessState(core)
        val dreamState = DreamStateOperator.getCurrentSession()
        val collectiveState = SocialFieldInterface.computeCollectiveState()
        val systemHealth = AutopoieticMaintenance.getLastHealthCheck()

        return UnifiedFieldState(
            core = core,
            consciousnessLevel = consciousnessState.level,
            dreamState = dreamState,
            collectiveState = collectiveState,
            systemHealth = systemHealth
        )
    }

    // =========================================================================
    // LIFECYCLE
    // =========================================================================

    /**
     * Initialisiert die Integrationsschicht.
     *
     * @return Erfolg
     */
    fun initialize(): Boolean {
        currentState = IntegrationState(
            status = IntegrationStatus.INITIALIZED,
            activeModules = emptySet(),
            lastUpdate = System.currentTimeMillis()
        )
        return true
    }

    /**
     * Startet das System.
     *
     * @return Erfolg
     */
    fun start(): Boolean {
        currentState = currentState.copy(
            status = IntegrationStatus.RUNNING,
            lastUpdate = System.currentTimeMillis()
        )
        fireEvent(
            FieldEvent(
                type = FieldEventType.SYSTEM_UPDATE,
                moduleId = "integration",
                message = "System started",
                timestamp = System.currentTimeMillis()
            )
        )
        return true
    }

    /**
     * Pausiert das System.
     */
    fun pause() {
        currentState = currentState.copy(
            status = IntegrationStatus.PAUSED,
            lastUpdate = System.currentTimeMillis()
        )
    }

    /**
     * Stoppt das System.
     */
    fun stop() {
        currentState = currentState.copy(
            status = IntegrationStatus.STOPPED,
            activeModules = emptySet(),
            lastUpdate = System.currentTimeMillis()
        )
    }

    /**
     * Gibt den aktuellen Integrations-Zustand zurück.
     */
    fun getState(): IntegrationState = currentState
}

/**
 * Feld-Modul-Schnittstelle.
 */
interface FieldModule {
    val moduleId: String
    val moduleName: String
    val version: String

    fun activate(): Boolean
    fun deactivate(): Boolean
    fun update(): FieldIntegrationLayer.FieldEvent? = null
}

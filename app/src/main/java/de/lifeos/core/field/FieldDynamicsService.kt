package de.lifeos.core.field

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

/**
 * FIELD DYNAMICS SERVICE — Android-Service-Wrapper für Feld-Primitive
 *
 * Kapselt alle P0–P8 Kernel-Primitive in einen Android-kompatiblen Service:
 * - Coroutine-basierte Echtzeit-Überwachung
 * - StateFlow für reaktive UI-Updates
 * - Homöostase-Regelkreis im Hintergrund
 * - Trajektorien-Planung on-demand
 * - Resonanz-Kalibrierung periodisch
 *
 * Vektoren:
 * - [EXP-FORCE] Android-Integration: Feld-Primitive als reaktiver Service
 * - [EXP-AUTO] Autopoietische Regulation: Hintergrund-Überwachung
 * - [EXP-SPEED] O(1) StateFlow-Updates mit Coroutine-Isolation
 */
class FieldDynamicsService(
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
) {

    // =========================================================================
    // REAKTIVE ZUSTANDSFLÜSSE
    // =========================================================================

    /** Vereinheitlichter Feldzustand als StateFlow */
    private val _unifiedState = MutableStateFlow<FieldDynamicsIntegrator.UnifiedFieldState?>(null)
    val unifiedState: StateFlow<FieldDynamicsIntegrator.UnifiedFieldState?> = _unifiedState.asStateFlow()

    /** Homöostase-Ergebnis als StateFlow */
    private val _homoeostasisResult = MutableStateFlow<HomoeostasisRegulator.HomoeostasisResult?>(null)
    val homoeostasisResult: StateFlow<HomoeostasisRegulator.HomoeostasisResult?> = _homoeostasisResult.asStateFlow()

    /** Lebens-Trajektorie als StateFlow */
    private val _lifeTrajectory = MutableStateFlow<LifeTrajectoryPlanner.LifeTrajectory?>(null)
    val lifeTrajectory: StateFlow<LifeTrajectoryPlanner.LifeTrajectory?> = _lifeTrajectory.asStateFlow()

    /** Interventionen als StateFlow */
    private val _interventions = MutableStateFlow<List<HomoeostasisRegulator.Intervention>>(emptyList())
    val interventions: StateFlow<List<HomoeostasisRegulator.Intervention>> = _interventions.asStateFlow()

    /** Kalibrierungszustand als StateFlow */
    private val _calibrationState = MutableStateFlow<ResonanceCalibrator.CalibrationState>(ResonanceCalibrator.CalibrationState())
    val calibrationState: StateFlow<ResonanceCalibrator.CalibrationState> = _calibrationState.asStateFlow()

    /** Homöostase-Score als StateFlow */
    private val _homoeostasisScore = MutableStateFlow(1.0f)
    val homoeostasisScore: StateFlow<Float> = _homoeostasisScore.asStateFlow()

    // =========================================================================
    // HINTERGRUND-JOBS
    // =========================================================================

    private var monitoringJob: Job? = null
    private var calibrationJob: Job? = null
    private var trajectoryJob: Job? = null

    /** Startet den Echtzeit-Monitoring-Dienst */
    fun startMonitoring(initialPhase: PhaseVector = PhaseVector(FloatArray(32) { 0.1f }).normalize()) {
        stopMonitoring()
        monitoringJob = scope.launch {
            var previousOmega = 0f
            var previousError = 0f
            var integralSum = 0f

            while (isActive) {
                val unifiedState = FieldDynamicsIntegrator.createUnifiedState(
                    phase = initialPhase,
                    previousOmega = previousOmega,
                    dt = 0.1f
                )

                _unifiedState.value = unifiedState

                // Homöostase-Regelkreis
                val result = HomoeostasisRegulator.regulate(
                    unifiedState = unifiedState,
                    previousError = previousError,
                    integralSum = integralSum,
                    dt = 0.1f
                )
                _homoeostasisResult.value = result

                // Interventionen sammeln
                val interventions = mutableListOf<HomoeostasisRegulator.Intervention>()
                result.intervention?.let { interventions.add(it) }

                // 1-Tap-Aktionen erzeugen
                val oneTapActions = interventions.mapNotNull { HomoeostasisRegulator.createOneTapAction(it) }
                _interventions.value = interventions

                // Homöostase-Score aktualisieren
                _homoeostasisScore.value = HomoeostasisRegulator.computeHomoeostasisScore()

                // Zustand für nächste Iteration speichern
                previousOmega = unifiedState.omega
                previousError = result.error
                integralSum += result.error * 0.1f

                delay(1000) // 1 Hz Update-Rate
            }
        }
    }

    /** Stoppt den Echtzeit-Monitoring-Dienst */
    fun stopMonitoring() {
        monitoringJob?.cancel()
        monitoringJob = null
    }

    /** Startet die periodische Kalibrierung */
    fun startCalibration(intervalMs: Long = 60000L) {
        stopCalibration()
        calibrationJob = scope.launch {
            while (isActive) {
                val rhythms = ResonanceCalibrator.detectCognitiveRhythms()
                val currentState = _unifiedState.value ?: FieldDynamicsIntegrator.UnifiedFieldState(
                    cognitive = CognitiveStateVector.ZERO,
                    phase = PhaseVector(FloatArray(32) { 0f }),
                    load = 0f,
                    omega = 0f,
                    sovereignty = 0f,
                    effectiveDim = 1f,
                    decoherenceTime = 0f,
                    entropy = 0f
                )

                ResonanceCalibrator.calibrate(rhythms, currentState)
                ResonanceCalibrator.applyCalibration()

                _calibrationState.value = ResonanceCalibrator.getCalibrationState()
                delay(intervalMs)
            }
        }
    }

    /** Stoppt die periodische Kalibrierung */
    fun stopCalibration() {
        calibrationJob?.cancel()
        calibrationJob = null
    }

    /** Startet die periodische Trajektorien-Planung */
    fun startTrajectoryPlanning(intervalMs: Long = 300000L) {
        stopTrajectoryPlanning()
        trajectoryJob = scope.launch {
            while (isActive) {
                val currentState = _unifiedState.value ?: return@launch
                val trajectories = LifeTrajectoryPlanner.analyzeAllHorizons(currentState)
                val mostCritical = LifeTrajectoryPlanner.getMostCriticalTrajectory(trajectories)
                _lifeTrajectory.value = mostCritical

                delay(intervalMs)
            }
        }
    }

    /** Stoppt die periodische Trajektorien-Planung */
    fun stopTrajectoryPlanning() {
        trajectoryJob?.cancel()
        trajectoryJob = null
    }

    // =========================================================================
    // MANUELLE TRIGGER
    // =========================================================================

    /** Manuelle Aktualisierung des Feldzustands */
    fun updateFieldState(phase: PhaseVector) {
        val previousOmega = _unifiedState.value?.omega ?: 0f
        val state = FieldDynamicsIntegrator.createUnifiedState(phase, previousOmega, dt = 0.1f)
        _unifiedState.value = state

        // Homöostase-Regelkreis
        val result = HomoeostasisRegulator.regulate(state)
        _homoeostasisResult.value = result
        _interventions.value = listOfNotNull(result.intervention)
        _homoeostasisScore.value = HomoeostasisRegulator.computeHomoeostasisScore()
    }

    /** Manuelle Trajektorien-Berechnung */
    fun computeTrajectory(horizonS: Float = LifeTrajectoryPlanner.HORIZON_24H_S) {
        val currentState = _unifiedState.value ?: return
        val trajectory = LifeTrajectoryPlanner.computeTrajectory(currentState, horizonS)
        _lifeTrajectory.value = trajectory
    }

    /** Manuelle Kalibrierung */
    fun calibrate() {
        val rhythms = ResonanceCalibrator.detectCognitiveRhythms()
        val currentState = _unifiedState.value ?: return
        ResonanceCalibrator.calibrate(rhythms, currentState)
        ResonanceCalibrator.applyCalibration()
        _calibrationState.value = ResonanceCalibrator.getCalibrationState()
    }

    // =========================================================================
    // LEBENSZYKLUS
    // =========================================================================

    /** Startet alle Dienste */
    fun startAll(initialPhase: PhaseVector = PhaseVector(FloatArray(32) { 0.1f }).normalize()) {
        startMonitoring(initialPhase)
        startCalibration()
        startTrajectoryPlanning()
    }

    /** Stoppt alle Dienste */
    fun stopAll() {
        stopMonitoring()
        stopCalibration()
        stopTrajectoryPlanning()
    }

    /** Gibt den aktuellen vereinheitlichten Feldzustand zurück */
    fun getCurrentState(): FieldDynamicsIntegrator.UnifiedFieldState? = _unifiedState.value

    /** Gibt die aktuellen Interventionen zurück */
    fun getCurrentInterventions(): List<HomoeostasisRegulator.Intervention> = _interventions.value

    /** Gibt die aktuelle Lebens-Trajektorie zurück */
    fun getCurrentTrajectory(): LifeTrajectoryPlanner.LifeTrajectory? = _lifeTrajectory.value

    /** Gibt den aktuellen Homöostase-Score zurück */
    fun getHomoeostasisScore(): Float = _homoeostasisScore.value

    /** Gibt den aktuellen Kalibrierungszustand zurück */
    fun getCalibrationState(): ResonanceCalibrator.CalibrationState = _calibrationState.value
}

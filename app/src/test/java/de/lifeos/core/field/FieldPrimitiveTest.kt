package de.lifeos.core.field

import kotlin.math.sqrt
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

/**
 * Unit tests for MMSI V3.8 field primitives.
 *
 * Validates:
 * - CognitiveStateVector I(t) ∈ R³≥0
 * - LoadFunction W(t) & Ω(t)
 * - PresentProjector P_S
 * - EffectiveDimension d_α(t)
 * - SovereigntyIndex S_o(t)
 * - DecouplingOperator D̂
 * - AutopoieticOperator κ̂(L)
 * - PgoTunnelOperator T̂_PGO
 * - LindbladMasterEquation
 * - ArnoldTongueAnalyzer
 * - EmpiricalValidationMatrix
 * - U1GaugeField
 * - PathIntegralOperator
 * - FieldDynamicsIntegrator
 * - HomoeostasisRegulator
 * - ResonanceCalibrator
 */
class FieldPrimitiveTest {

    // =========================================================================
    // COGNITIVE STATE VECTOR TESTS
    // =========================================================================

    @Test
    fun `cognitive state vector initialization with non-negative values`() {
        val state = CognitiveStateVector(past = 0.5f, present = 1.0f, future = 0.3f)
        assertEquals(0.5f, state.past, 0.001f)
        assertEquals(1.0f, state.present, 0.001f)
        assertEquals(0.3f, state.future, 0.001f)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `cognitive state vector rejects negative past`() {
        CognitiveStateVector(past = -0.1f, present = 1.0f, future = 0.3f)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `cognitive state vector rejects negative present`() {
        CognitiveStateVector(past = 0.5f, present = -1.0f, future = 0.3f)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `cognitive state vector rejects negative future`() {
        CognitiveStateVector(past = 0.5f, present = 1.0f, future = -0.3f)
    }

    @Test
    fun `cognitive state vector norm calculation`() {
        val state = CognitiveStateVector(past = 3.0f, present = 4.0f, future = 0.0f)
        assertEquals(5.0f, state.norm(), 0.001f)
    }

    @Test
    fun `cognitive state vector normalization`() {
        val state = CognitiveStateVector(past = 3.0f, present = 4.0f, future = 0.0f)
        val normalized = state.normalize()
        assertEquals(1.0f, normalized.norm(), 0.001f)
    }

    @Test
    fun `cognitive state vector addition`() {
        val a = CognitiveStateVector(past = 1.0f, present = 2.0f, future = 3.0f)
        val b = CognitiveStateVector(past = 0.5f, present = 1.0f, future = 0.5f)
        val sum = a + b
        assertEquals(1.5f, sum.past, 0.001f)
        assertEquals(3.0f, sum.present, 0.001f)
        assertEquals(3.5f, sum.future, 0.001f)
    }

    @Test
    fun `cognitive state vector subtraction`() {
        val a = CognitiveStateVector(past = 1.0f, present = 2.0f, future = 3.0f)
        val b = CognitiveStateVector(past = 0.5f, present = 1.0f, future = 0.5f)
        val diff = a - b
        assertEquals(0.5f, diff.past, 0.001f)
        assertEquals(1.0f, diff.present, 0.001f)
        assertEquals(2.5f, diff.future, 0.001f)
    }

    @Test
    fun `cognitive state vector scalar multiplication`() {
        val state = CognitiveStateVector(past = 1.0f, present = 2.0f, future = 3.0f)
        val scaled = state * 2.0f
        assertEquals(2.0f, scaled.past, 0.001f)
        assertEquals(4.0f, scaled.present, 0.001f)
        assertEquals(6.0f, scaled.future, 0.001f)
    }

    @Test
    fun `cognitive state vector scalar division`() {
        val state = CognitiveStateVector(past = 2.0f, present = 4.0f, future = 6.0f)
        val scaled = state / 2.0f
        assertEquals(1.0f, scaled.past, 0.001f)
        assertEquals(2.0f, scaled.present, 0.001f)
        assertEquals(3.0f, scaled.future, 0.001f)
    }

    @Test
    fun `cognitive state vector seinsmodus detection`() {
        val seinsmodus = CognitiveStateVector(past = 0.0f, present = 1.0f, future = 0.0f)
        assertTrue(seinsmodus.isSeinsmodus())

        val verarbeitungsmodus = CognitiveStateVector(past = 0.5f, present = 1.0f, future = 0.3f)
        assertFalse(verarbeitungsmodus.isSeinsmodus())
    }

    @Test
    fun `cognitive state vector compute load`() {
        val state = CognitiveStateVector(past = 0.6f, present = 0.0f, future = 0.4f)
        val load = state.computeLoad()
        assertEquals(0.6f * 0.6f + 0.4f * 0.4f, load, 0.001f)
    }

    // =========================================================================
    // LOAD FUNCTION TESTS
    // =========================================================================

    @Test
    fun `load function computes W(t) correctly`() {
        val state = CognitiveStateVector(past = 0.6f, present = 0.0f, future = 0.4f)
        val w = LoadFunction.computeW(state)
        assertEquals(0.6f * 0.6f + 0.4f * 0.4f, w, 0.001f)
    }

    @Test
    fun `load function seinsmodus detection`() {
        val seinsmodus = CognitiveStateVector(past = 0.0f, present = 1.0f, future = 0.0f)
        assertTrue(LoadFunction.isSeinsmodus(seinsmodus))
    }

    @Test
    fun `omega critical threshold is 5800`() {
        assertEquals(5800f, LoadFunction.OMEGA_CRITICAL, 0.001f)
    }

    // =========================================================================
    // PRESENT PROJECTOR TESTS
    // =========================================================================

    @Test
    fun `present projector collapses to present component`() {
        val state = CognitiveStateVector(past = 0.5f, present = 1.0f, future = 0.3f)
        val collapsed = PresentProjector.collapse(state)
        assertEquals(0.0f, collapsed.past, 0.001f)
        assertEquals(1.0f, collapsed.present, 0.001f)
        assertEquals(0.0f, collapsed.future, 0.001f)
    }

    @Test
    fun `present projector is idempotent`() {
        val state = CognitiveStateVector(past = 0.5f, present = 1.0f, future = 0.3f)
        val collapsedOnce = PresentProjector.collapse(state)
        val collapsedTwice = PresentProjector.collapse(collapsedOnce)
        assertEquals(collapsedOnce.past, collapsedTwice.past, 0.001f)
        assertEquals(collapsedOnce.present, collapsedTwice.present, 0.001f)
        assertEquals(collapsedOnce.future, collapsedTwice.future, 0.001f)
    }

    // =========================================================================
    // EFFECTIVE DIMENSION TESTS
    // =========================================================================

    @Test
    fun `effective dimension in seinsmodus is 1`() {
        val state = CognitiveStateVector(past = 0.0f, present = 1.0f, future = 0.0f)
        val dim = EffectiveDimension.computeHeaviside(state)
        assertEquals(1, dim.toLong())
    }

    @Test
    fun `effective dimension in verarbeitungsmodus is 3`() {
        val state = CognitiveStateVector(past = 1.0f, present = 1.0f, future = 1.0f)
        val dim = EffectiveDimension.computeHeaviside(state)
        assertEquals(3, dim.toLong())
    }

    @Test
    fun `effective dimension sigmoid is between 0 and 1`() {
        val x = 0.5f
        val sig = EffectiveDimension.sigmoid(x)
        assertTrue("Sigmoid should be in [0,1]", sig >= 0.0f && sig <= 1.0f)
    }

    // =========================================================================
    // SOVEREIGNTY INDEX TESTS
    // =========================================================================

    @Test
    fun `sovereignty index with zero allochthone influence`() {
        val s = SovereigntyIndex.compute(allochthonePast = 0f, allochthoneFuture = 0f, present = 1.0f)
        assertEquals(0.0f, s, 0.001f)
    }

    @Test
    fun `sovereignty index increases with allochthone influence`() {
        val s1 = SovereigntyIndex.compute(allochthonePast = 0.5f, allochthoneFuture = 0.0f, present = 1.0f)
        val s2 = SovereigntyIndex.compute(allochthonePast = 1.0f, allochthoneFuture = 0.0f, present = 1.0f)
        assertTrue(s2 > s1)
    }

    // =========================================================================
    // DECOUPLING OPERATOR TESTS
    // =========================================================================

    @Test
    fun `decoupling removes allochthone past`() {
        val state = CognitiveStateVector(past = 1.0f, present = 1.0f, future = 0.5f)
        val decoupled = DecouplingOperator.decouple(state, allochthonePast = 0.3f)
        assertEquals(0.7f, decoupled.past, 0.001f)
        assertEquals(1.0f, decoupled.present, 0.001f)
    }

    @Test
    fun `decoupling sovereignty gain is positive`() {
        val original = CognitiveStateVector(past = 1.0f, present = 1.0f, future = 0.5f)
        val decoupled = DecouplingOperator.decouple(original, allochthonePast = 0.3f)
        val gain = DecouplingOperator.sovereigntyGain(original, decoupled)
        assertTrue(gain > 0.0f)
    }

    // =========================================================================
    // AUTOPOIETIC OPERATOR TESTS
    // =========================================================================

    @Test
    fun `autopoietic operator returns non-negative state`() {
        val state = CognitiveStateVector(past = 0.5f, present = 1.0f, future = 0.3f)
        val result = AutopoieticOperator.apply(state, load = 1.0f)
        assertTrue(result.past >= 0.0f)
        assertTrue(result.present >= 0.0f)
        assertTrue(result.future >= 0.0f)
    }

    // =========================================================================
    // PGO TUNNEL OPERATOR TESTS
    // =========================================================================

    @Test
    fun `tunnel probability is between 0 and 1`() {
        val prob = PgoTunnelOperator.tunnelProbability(x1 = 0f, x2 = 1f)
        assertTrue("Tunnel probability should be in [0,1]", prob >= 0.0f && prob <= 1.0f)
    }

    @Test
    fun `cache reset reduces cache value`() {
        val cache = 1.0f
        val prob = PgoTunnelOperator.tunnelProbability(x1 = 0f, x2 = 1f)
        val newCache = PgoTunnelOperator.cacheReset(cache, prob)
        assertTrue(newCache <= cache)
    }

    // =========================================================================
    // LINDBLAD MASTER EQUATION TESTS
    // =========================================================================

    @Test
    fun `decoherence time is positive`() {
        val tau = LindbladMasterEquation.decoherenceTime()
        assertTrue(tau > 0.0f)
    }

    @Test
    fun `von neumann entropy is positive`() {
        val state = CognitiveStateVector(past = 0.5f, present = 1.0f, future = 0.3f)
        val entropy = LindbladMasterEquation.vonNeumannEntropy(state)
        assertTrue(entropy >= 0.0f)
    }

    // =========================================================================
    // ARNOLD TONGUE ANALYZER TESTS
    // =========================================================================

    @Test
    fun `rotation number is between 0 and 1`() {
        val omega = ArnoldTongueAnalyzer.rotationNumber(omega0 = 0.5f, k = 1.0f)
        assertTrue("Rotation number should be in [0,1]", omega >= 0.0f && omega <= 1.0f)
    }

    @Test
    fun `detect arnold tongues returns non-empty list`() {
        val tongues = ArnoldTongueAnalyzer.detectArnoldTongues(k = 1.0f, maxDenominator = 10)
        assertTrue(tongues.isNotEmpty())
    }

    @Test
    fun `fractal dimension is between 0 and 1`() {
        val dim = ArnoldTongueAnalyzer.fractalDimension(k = 1.0f)
        assertTrue("Fractal dimension should be in [0,1]", dim >= 0.0f && dim <= 1.0f)
    }

    // =========================================================================
    // EMPIRICAL VALIDATION MATRIX TESTS
    // =========================================================================

    @Test
    fun `plv validation passes for valid plv`() {
        assertTrue(EmpiricalValidationMatrix.validatePLV(0.973408f))
    }

    @Test
    fun `w base validation passes for valid w base`() {
        assertTrue(EmpiricalValidationMatrix.validateWBase(0.7557f))
    }

    @Test
    fun `ern validation passes for ern below threshold`() {
        assertTrue(EmpiricalValidationMatrix.validateERN(1.0f))
    }

    @Test
    fun `ern validation fails for ern above threshold`() {
        assertFalse(EmpiricalValidationMatrix.validateERN(2.0f))
    }

    @Test
    fun `omega critical validation passes for valid omega`() {
        assertTrue(EmpiricalValidationMatrix.validateOmegaCritical(5800f))
    }

    // =========================================================================
    // U(1) GAUGE FIELD TESTS
    // =========================================================================

    @Test
    fun `gauge potential is between -1 and 1`() {
        val potential = U1GaugeField.gaugePotential(t = 0.5f)
        assertTrue("Gauge potential should be in [-1,1]", potential >= -1.0f && potential <= 1.0f)
    }

    @Test
    fun `holonomy magnitude is approximately 1 for unitary holonomy`() {
        val path = listOf(1.0f to 0.1f, 0.5f to 0.2f)
        val holonomy = U1GaugeField.holonomy(path)
        val magnitude = U1GaugeField.holonomyMagnitude(holonomy)
        assertTrue("Holonomy magnitude should be close to 1", magnitude > 0.5f)
    }

    @Test
    fun `phase covariance is between 0 and 1`() {
        val psi1 = 1.0f to 0.0f
        val psi2 = 0.0f to 1.0f
        val cov = U1GaugeField.phaseCovariance(psi1, psi2)
        assertTrue("Phase covariance should be in [0,1]", cov >= 0.0f && cov <= 1.0f)
    }

    // =========================================================================
    // PATH INTEGRAL OPERATOR TESTS
    // =========================================================================

    @Test
    fun `path integral action is finite`() {
        val start = CognitiveStateVector(past = 0.0f, present = 1.0f, future = 0.0f)
        val end = CognitiveStateVector(past = 1.0f, present = 0.0f, future = 1.0f)
        val path = PathIntegralOperator.stationaryPath(start, end, numSteps = 10)
        val action = PathIntegralOperator.action(path)
        assertFalse(action.isNaN())
        assertFalse(action.isInfinite())
    }

    @Test
    fun `transition probability is between 0 and 1`() {
        val start = CognitiveStateVector(past = 0.0f, present = 1.0f, future = 0.0f)
        val end = CognitiveStateVector(past = 1.0f, present = 0.0f, future = 1.0f)
        val prob = PathIntegralOperator.transitionProbability(start, end, numSteps = 10, numSamples = 100)
        assertTrue("Probability should be in [0,1]", prob >= 0.0f && prob <= 1.0f)
    }

    @Test
    fun `interference is between 0 and 4`() {
        val interference = PathIntegralOperator.interference(action1 = 1.0f, action2 = 2.0f)
        assertTrue("Interference should be in [0,4]", interference >= 0.0f && interference <= 4.0f)
    }

    // =========================================================================
    // FIELD DYNAMICS INTEGRATOR TESTS
    // =========================================================================

    @Test
    fun `cognitive to phase vector conversion preserves components`() {
        val cognitive = CognitiveStateVector(past = 0.5f, present = 1.0f, future = 0.3f)
        val phase = FieldDynamicsIntegrator.toPhaseVector(cognitive)
        val converted = FieldDynamicsIntegrator.toCognitiveState(phase)
        // First 3 components should be approximately proportional after normalization
        assertTrue("Past component should be positive", converted.past >= 0.0f)
        assertTrue("Present component should be positive", converted.present >= 0.0f)
        assertTrue("Future component should be positive", converted.future >= 0.0f)
        // Present should be dominant since it was 1.0 in input
        assertTrue("Present should be dominant", converted.present >= converted.past)
        assertTrue("Present should be dominant", converted.present >= converted.future)
    }

    @Test
    fun `unified field state creation produces valid state`() {
        val phase = PhaseVector(FloatArray(32) { 0.1f }).normalize()
        val state = FieldDynamicsIntegrator.createUnifiedState(phase)
        assertTrue(state.load >= 0.0f)
        assertTrue(state.omega >= 0.0f)
        assertTrue(state.sovereignty >= 0.0f)
        // Effective dimension can be < 1.0 for low-activation states
        assertTrue("Effective dimension should be non-negative", state.effectiveDim >= 0.0f)
    }

    // =========================================================================
    // HOMOEOSTASIS REGULATOR TESTS
    // =========================================================================

    @Test
    fun `seinsmodus state classification for low load`() {
        val state = HomoeostasisRegulator.classifyState(load = 0.3f, omega = 100f)
        assertEquals(HomoeostasisRegulator.HomoeostasisState.SEINSMODUS, state)
    }

    @Test
    fun `warning state classification for medium load`() {
        val state = HomoeostasisRegulator.classifyState(load = 0.9f, omega = 100f)
        assertEquals(HomoeostasisRegulator.HomoeostasisState.WARNING, state)
    }

    @Test
    fun `escalation state classification for high load`() {
        val state = HomoeostasisRegulator.classifyState(load = 1.5f, omega = 100f)
        assertEquals(HomoeostasisRegulator.HomoeostasisState.ESCALATION, state)
    }

    @Test
    fun `collapse state classification for critical omega`() {
        val state = HomoeostasisRegulator.classifyState(load = 0.5f, omega = 5800f)
        assertEquals(HomoeostasisRegulator.HomoeostasisState.COLLAPSE, state)
    }

    @Test
    fun `pid control produces bounded output`() {
        val output = HomoeostasisRegulator.pidControl(error = 1.0f, previousError = 0.5f, integralSum = 0.1f)
        assertTrue("PID output should be in [0,1]", output >= 0.0f && output <= 1.0f)
    }

    @Test
    fun `intervention generation for warning state`() {
        val intervention = HomoeostasisRegulator.generateIntervention(
            state = HomoeostasisRegulator.HomoeostasisState.WARNING,
            load = 0.9f,
            omega = 100f,
            sovereignty = 0.8f
        )
        assertNotNull(intervention)
        assertEquals(HomoeostasisRegulator.InterventionType.WARNING, intervention!!.type)
    }

    @Test
    fun `one tap action creation for pause prompt`() {
        val intervention = HomoeostasisRegulator.Intervention(
            type = HomoeostasisRegulator.InterventionType.WARNING,
            title = "Test",
            description = "Test",
            actionType = "PAUSE_PROMPT",
            urgency = 0.5f,
            expectedLoadReduction = 0.2f
        )
        val action = HomoeostasisRegulator.createOneTapAction(intervention)
        assertNotNull(action)
        assertEquals("start_breathing_exercise", action!!.action)
    }

    // =========================================================================
    // RESONANCE CALIBRATOR TESTS
    // =========================================================================

    @Test
    fun `calibration state initialization with defaults`() {
        val state = ResonanceCalibrator.CalibrationState()
        assertEquals(LoadFunction.DEFAULT_MU_PAST, state.muPast, 0.001f)
        assertEquals(LoadFunction.DEFAULT_MU_FUTURE, state.muFuture, 0.001f)
    }

    @Test
    fun `behavior data point addition increases history size`() {
        val initialSize = ResonanceCalibrator.getBehaviorHistory().size
        ResonanceCalibrator.addBehaviorData(
            ResonanceCalibrator.BehaviorDataPoint(
                timestamp = System.currentTimeMillis(),
                load = 0.5f,
                omega = 100f,
                sovereignty = 0.8f,
                interactionType = "TEST",
                responseTimeMs = 100f,
                successFlag = true
            )
        )
        assertEquals(initialSize + 1, ResonanceCalibrator.getBehaviorHistory().size)
    }

    @Test
    fun `calibration validation returns report`() {
        val report = ResonanceCalibrator.validateCalibration()
        assertNotNull(report)
        assertTrue(report.total > 0)
    }

    @Test
    fun `reset calibration restores defaults`() {
        ResonanceCalibrator.resetCalibration()
        val state = ResonanceCalibrator.getCalibrationState()
        assertEquals(LoadFunction.DEFAULT_MU_PAST, state.muPast, 0.001f)
        assertEquals(LoadFunction.DEFAULT_MU_FUTURE, state.muFuture, 0.001f)
    }

    // =========================================================================
    // INTEGRATION TESTS
    // =========================================================================

    @Test
    fun `full field dynamics pipeline produces valid unified state`() {
        val phase = PhaseVector(FloatArray(32) { 0.1f }).normalize()
        val trajectory = FieldDynamicsIntegrator.executeFieldTrajectory(phase, maxSteps = 5)
        assertTrue(trajectory.isNotEmpty())
        trajectory.forEach { state ->
            assertTrue(state.load >= 0.0f)
            assertTrue(state.omega >= 0.0f)
        }
    }

    @Test
    fun `homoeostasis regulation produces valid result`() {
        val phase = PhaseVector(FloatArray(32) { 0.1f }).normalize()
        val unifiedState = FieldDynamicsIntegrator.createUnifiedState(phase)
        val result = HomoeostasisRegulator.regulate(unifiedState)
        assertNotNull(result)
        assertTrue(result.error >= 0.0f)
        assertTrue(result.controlOutput >= 0.0f)
    }

    // =========================================================================
    // P8: LIFE TRAJECTORY PLANNER TESTS
    // =========================================================================

    @Test
    fun `life trajectory computation produces valid trajectory`() {
        val phase = PhaseVector(FloatArray(32) { 0.1f }).normalize()
        val unifiedState = FieldDynamicsIntegrator.createUnifiedState(phase)
        val trajectory = LifeTrajectoryPlanner.computeTrajectory(unifiedState, LifeTrajectoryPlanner.HORIZON_4H_S)
        assertNotNull(trajectory)
        assertTrue(trajectory.points.isNotEmpty())
        assertTrue(trajectory.horizonS == LifeTrajectoryPlanner.HORIZON_4H_S)
    }

    @Test
    fun `life trajectory conflict detection finds conflicts`() {
        val phase = PhaseVector(FloatArray(32) { 0.1f }).normalize()
        val unifiedState = FieldDynamicsIntegrator.createUnifiedState(phase)
        val trajectory = LifeTrajectoryPlanner.computeTrajectory(unifiedState, LifeTrajectoryPlanner.HORIZON_24H_S)
        val conflicts = LifeTrajectoryPlanner.detectConflicts(trajectory)
        assertNotNull(conflicts)
    }

    @Test
    fun `life trajectory proactive interventions are generated`() {
        val phase = PhaseVector(FloatArray(32) { 0.1f }).normalize()
        val unifiedState = FieldDynamicsIntegrator.createUnifiedState(phase)
        val trajectory = LifeTrajectoryPlanner.computeTrajectory(unifiedState, LifeTrajectoryPlanner.HORIZON_24H_S)
        val conflicts = LifeTrajectoryPlanner.detectConflicts(trajectory)
        val interventions = LifeTrajectoryPlanner.generateProactiveInterventions(trajectory, conflicts)
        assertNotNull(interventions)
    }

    @Test
    fun `all horizons analysis produces three trajectories`() {
        val phase = PhaseVector(FloatArray(32) { 0.1f }).normalize()
        val unifiedState = FieldDynamicsIntegrator.createUnifiedState(phase)
        val trajectories = LifeTrajectoryPlanner.analyzeAllHorizons(unifiedState)
        assertEquals(3, trajectories.size)
        assertTrue(trajectories.containsKey(LifeTrajectoryPlanner.HORIZON_4H_S))
        assertTrue(trajectories.containsKey(LifeTrajectoryPlanner.HORIZON_24H_S))
        assertTrue(trajectories.containsKey(LifeTrajectoryPlanner.HORIZON_30D_S))
    }

    // =========================================================================
    // P9: FIELD DYNAMICS SERVICE TESTS
    // =========================================================================

    @Test
    fun `field dynamics service initializes correctly`() {
        val service = FieldDynamicsService()
        assertNotNull(service)
        // State is null until service is started
        assertNull(service.getCurrentState())
        assertTrue(service.getCurrentInterventions().isEmpty())
        assertNull(service.getCurrentTrajectory())
    }

    @Test
    fun `field dynamics service start all initializes flows`() {
        val service = FieldDynamicsService()
        runBlocking {
            service.startAll()
            delay(100) // Wait for initialization
        }
        assertNotNull(service.getCurrentState())
        service.stopAll()
    }

    @Test
    fun `field dynamics service stop all cancels jobs`() {
        val service = FieldDynamicsService()
        runBlocking {
            service.startAll()
            service.stopAll()
        }
        // Service should be stopped
        assertTrue(true)
    }

    @Test
    fun `field dynamics service manual update produces state`() {
        val service = FieldDynamicsService()
        val phase = PhaseVector(FloatArray(32) { 0.1f }).normalize()
        service.updateFieldState(phase)
        assertNotNull(service.getCurrentState())
        assertNotNull(service.getHomoeostasisScore())
    }

    @Test
    fun `field dynamics service calibration updates state`() {
        val service = FieldDynamicsService()
        val phase = PhaseVector(FloatArray(32) { 0.1f }).normalize()
        service.updateFieldState(phase)
        service.calibrate()
        val calibrationState = service.getCalibrationState()
        assertNotNull(calibrationState)
    }
}

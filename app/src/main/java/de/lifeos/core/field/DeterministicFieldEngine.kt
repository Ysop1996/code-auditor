package de.lifeos.core.field

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.abs
import kotlin.math.max

class DeterministicFieldEngine(
    private val dimension: Int = 32,
    private val phi: Float = 1.61803398875f,
    private val gammaDecay: Float = 0.05f
) {
    private val attractorGraph = mutableMapOf<String, AttractorNode>()
    var currentRho: Float = 1.0f

    private val _activeNodes = MutableStateFlow<List<AttractorNode>>(emptyList())
    val activeNodes: StateFlow<List<AttractorNode>> = _activeNodes.asStateFlow()

    init {
        registerNode(
            AttractorNode(
                id = "SYS_HOMEOSTASE",
                payload = "System-Homöostase überwachen",
                position = PhaseVector(FloatArray(dimension) { 0.1f }).normalize(),
                mass = 1.5f
            )
        )
        registerNode(
            AttractorNode(
                id = "SYS_DATEN",
                payload = "Datenintegrität im Tresor prüfen",
                position = PhaseVector(FloatArray(dimension) { i -> (i % 5) * 0.1f }).normalize(),
                mass = 1.2f
            )
        )
        registerNode(
            AttractorNode(
                id = "SYS_RECHTSKERNEL",
                payload = "Rechtskernel-Status aktualisieren",
                position = PhaseVector(FloatArray(dimension) { i -> (i % 7) * 0.08f }).normalize(),
                mass = 1.0f
            )
        )
    }

    fun executeTrajectory(stimulus: PhaseVector, maxSteps: Int = 16): List<AttractorNode> {
        val trajectory = mutableListOf<AttractorNode>()
        var currentPos = PhaseVector(FloatArray(dimension) { 0.05f })
        var requestLoadY = stimulus.norm()
        var step = 0

        while (step < maxSteps) {
            step++
            val k1 = (-gammaDecay * currentRho) + requestLoadY
            val rhoPred = max(0.0f, currentRho + (k1 * 0.5f))
            val k2 = (-gammaDecay * rhoPred) + requestLoadY
            currentRho = max(0.001f, currentRho + (0.5f * (k1 + k2) * 0.5f))

            val forceArray = FloatArray(dimension)
            NeonPhaseBridge.nativeComputeForce32(
                currentPos.dim,
                stimulus.dim,
                currentRho,
                phi,
                forceArray
            )

            currentPos += PhaseVector(forceArray).normalize() * 0.382f

            val nearest = attractorGraph.values
                .filter { it !in trajectory }
                .minByOrNull { NeonPhaseBridge.nativeDistSq32(it.position.dim, currentPos.dim) } ?: break

            trajectory.add(nearest)
            requestLoadY = max(0.0f, requestLoadY - (nearest.mass * 0.25f))
            val frictionW = abs(requestLoadY - 1.0f) * phi
            if (frictionW <= 1.0f || nearest.isTerminal) break
        }

        reinforceAttractors(trajectory)
        return trajectory
    }

    private fun reinforceAttractors(path: List<AttractorNode>) {
        for (i in 0 until path.size - 1) {
            val n1 = path[i]
            val n2 = path[i + 1]
            val delta = (n2.position - n1.position) * 0.05f
            n1.position = (n1.position + delta).normalize()
            n1.mass += 0.01f
        }
    }

    fun registerNode(node: AttractorNode) {
        attractorGraph[node.id] = node
        _activeNodes.value = attractorGraph.values.toList()
    }

    fun getActiveNodes(): List<AttractorNode> = attractorGraph.values.toList()

    fun getNodeCount(): Int = attractorGraph.size

    /**
     * Recalculates field topology after hydration.
     * Re-normalizes attractor positions and updates mass distribution.
     */
    fun recalculateFieldTopology(activeNodeCount: Int) {
        if (activeNodeCount == 0) return

        // Re-normalize all attractor positions to maintain unit sphere
        attractorGraph.values.forEach { node ->
            node.position = node.position.normalize()
        }

        // Update global rho based on field density
        currentRho = (1.0f / (1.0f + activeNodeCount * 0.01f)).coerceIn(0.1f, 2.0f)
        _activeNodes.value = attractorGraph.values.toList()
    }
}

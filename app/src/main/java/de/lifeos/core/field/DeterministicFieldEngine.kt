package de.lifeos.core.field

import kotlin.math.abs
import kotlin.math.max

class DeterministicFieldEngine(
    private val dimension: Int = 32,
    private val phi: Float = 1.61803398875f,
    private val gammaDecay: Float = 0.05f
) {
    private val attractorGraph = mutableMapOf<String, AttractorNode>()
    var currentRho: Float = 1.0f

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
    }
}

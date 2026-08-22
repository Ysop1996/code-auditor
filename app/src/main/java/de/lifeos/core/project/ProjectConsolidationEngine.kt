package de.lifeos.core.project

import com.mmsi.neuro.engine.core.MmsiCoreEngineV38
import com.mmsi.neuro.engine.core.MmsiFrameOutput
import de.lifeos.core.field.AttractorNode
import de.lifeos.core.field.PhaseVector
import kotlin.math.max

data class ProjectEvaluation(
    val projectId: String,
    val totalMass: Float,
    val frictionW: Double,
    val backpressureRho: Double,
    val healthStatus: String,
    val actionableItems: List<String>,
    val centroid: PhaseVector
)

class ProjectConsolidationEngine {

    private val mmsiEngine = MmsiCoreEngineV38()
    private val frameOutput = MmsiFrameOutput()

    fun consolidateAndEvaluateProject(
        projectId: String,
        associatedNodes: List<AttractorNode>,
        openDeadlinesCount: Int,
        financialRiskEuro: Double
    ): ProjectEvaluation {
        if (associatedNodes.isEmpty()) {
            return ProjectEvaluation(
                projectId = projectId,
                totalMass = 0f,
                frictionW = 0.0,
                backpressureRho = 0.0,
                healthStatus = "Leer / Inaktiv",
                actionableItems = emptyList(),
                centroid = PhaseVector(FloatArray(32))
            )
        }

        val dimSize = associatedNodes.first().position.dim.size
        val totalMass = associatedNodes.sumOf { it.mass.toDouble() }.toFloat()
        val centroidCoords = FloatArray(dimSize)

        for (node in associatedNodes) {
            for (d in 0 until dimSize) {
                centroidCoords[d] += node.position.dim[d] * node.mass
            }
        }
        for (d in 0 until dimSize) {
            centroidCoords[d] /= max(0.001f, totalMass)
        }
        val projectCentroid = PhaseVector(centroidCoords).normalize()

        val zSubstanz = totalMass * 1.5
        val yLast = (openDeadlinesCount * 3.5) + (financialRiskEuro * 0.002) + (associatedNodes.size * 0.4)

        mmsiEngine.processFrameInPlace(
            af7Alpha = zSubstanz.coerceIn(5.0, 30.0),
            af8Alpha = zSubstanz.coerceIn(5.0, 30.0),
            betaHigh = yLast.coerceIn(2.0, 40.0),
            thetaPost = openDeadlinesCount * 2.0,
            age = 30.0,
            sex = "M",
            deltaF7 = 10.0,
            deltaF8 = 10.0,
            out = frameOutput
        )

        val actions = mutableListOf<String>()
        if (openDeadlinesCount > 0) actions.add("Fristüberwachung aktiv ($openDeadlinesCount offen)")
        if (financialRiskEuro > 0.0) actions.add("Finanzielles Exposure: ${"%.2f".format(financialRiskEuro)} €")
        if (frameOutput.wBounded > 1.8) actions.add("Kritischer Staudruck: Sofortige 1-Tap Entlastung nötig")

        return ProjectEvaluation(
            projectId = projectId,
            totalMass = totalMass,
            frictionW = frameOutput.wBounded,
            backpressureRho = frameOutput.rho,
            healthStatus = frameOutput.predictedCluster,
            actionableItems = actions,
            centroid = projectCentroid
        )
    }
}

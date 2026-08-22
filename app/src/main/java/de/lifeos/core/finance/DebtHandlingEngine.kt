package de.lifeos.core.finance

import com.mmsi.neuro.engine.core.MmsiCoreEngineV38
import com.mmsi.neuro.engine.core.MmsiFrameOutput
import de.lifeos.core.field.AttractorNode
import de.lifeos.core.field.DeterministicFieldEngine
import de.lifeos.core.field.PhaseVector

data class DebtRecord(
    val creditorId: String,
    val name: String,
    val amount: Double,
    val isTitled: Boolean,
    val isStatuteBarred: Boolean,
    val fileReference: String
)

data class DebtEvaluationResult(
    val totalDebtEuro: Double,
    val activeCreditorsCount: Int,
    val urgentThreatsCount: Int,
    val frictionW: Double,
    val priorityActions: List<String>
)

class DebtHandlingEngine(
    private val fieldEngine: DeterministicFieldEngine,
    private val negotiationKernel: CreditorNegotiationKernel
) {
    private val mmsiEngine = MmsiCoreEngineV38()
    private val frameOutput = MmsiFrameOutput()
    private val debtRegistry = mutableMapOf<String, DebtRecord>()

    fun registerDebt(debt: DebtRecord) {
        debtRegistry[debt.creditorId] = debt

        var mass = 1.0f + (debt.amount.toFloat() * 0.001f)
        if (debt.isTitled) mass += 4.0f
        if (debt.isStatuteBarred) mass = 0.5f

        val coords = FloatArray(32) { idx ->
            if (idx == 0) (debt.amount / 10000.0).toFloat().coerceIn(-1f, 1f) else 0.05f
        }

        fieldEngine.registerNode(
            AttractorNode(
                id = "DEBT_${debt.creditorId}",
                payload = "GLÄUBIGER: ${debt.name} | BETRAG: ${debt.amount} € | TITEL: ${debt.isTitled}",
                position = PhaseVector(coords).normalize(),
                mass = mass,
                isTerminal = false
            )
        )
    }

    fun evaluateFinancialFriction(): DebtEvaluationResult {
        val totalDebt = debtRegistry.values.sumOf { it.amount }
        val titledDebts = debtRegistry.values.filter { it.isTitled }
        val statuteBarred = debtRegistry.values.filter { it.isStatuteBarred }

        val yLoad = (totalDebt * 0.001) + (titledDebts.size * 5.0) - (statuteBarred.size * 2.0)
        val zDamping = 10.0

        mmsiEngine.processFrameInPlace(
            af7Alpha = zDamping,
            af8Alpha = zDamping,
            betaHigh = yLoad.coerceIn(1.0, 50.0),
            thetaPost = titledDebts.size * 3.0,
            age = 30.0,
            sex = "M",
            deltaF7 = 10.0,
            deltaF8 = 10.0,
            out = frameOutput
        )

        val actions = mutableListOf<String>()
        statuteBarred.forEach { actions.add("Verjährungseinrede (§ 195 BGB) erheben für: ${it.name}") }
        titledDebts.forEach { actions.add("Titulierte Forderung (${it.name}): Vergleich/Ratenstopp anbieten") }

        return DebtEvaluationResult(
            totalDebtEuro = totalDebt,
            activeCreditorsCount = debtRegistry.size,
            urgentThreatsCount = titledDebts.size,
            frictionW = frameOutput.wBounded,
            priorityActions = actions
        )
    }
}

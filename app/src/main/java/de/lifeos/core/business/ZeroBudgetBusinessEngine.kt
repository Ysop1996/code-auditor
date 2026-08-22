package de.lifeos.core.business

import com.mmsi.neuro.engine.core.MmsiCoreEngineV38
import com.mmsi.neuro.engine.core.MmsiFrameOutput
import de.lifeos.core.field.AttractorNode
import de.lifeos.core.field.DeterministicFieldEngine
import de.lifeos.core.field.PhaseVector

data class BusinessPipelineStage(
    val leadId: String,
    val clientName: String,
    val contractValueEur: Double,
    val isPrepaid: Boolean,
    val isDelivered: Boolean
)

data class BusinessTelemetry(
    val monthlyRevenueEur: Double,
    val outstandingCashEur: Double,
    val cashConversionVelocity: Double,
    val frictionW: Double,
    val runwayDays: Int,
    val actionablePriority: String
)

class ZeroBudgetBusinessEngine(
    private val fieldEngine: DeterministicFieldEngine
) {
    private val mmsiEngine = MmsiCoreEngineV38()
    private val frameOutput = MmsiFrameOutput()
    private val pipeline = mutableMapOf<String, BusinessPipelineStage>()

    fun registerClientDeal(deal: BusinessPipelineStage) {
        pipeline[deal.leadId] = deal

        val mass = if (deal.isPrepaid) 3.5f else 1.2f
        val coords = FloatArray(32) { idx ->
            if (idx == 1) (deal.contractValueEur / 5000.0).toFloat().coerceIn(-1f, 1f) else 0.0f
        }

        fieldEngine.registerNode(
            AttractorNode(
                id = "DEAL_${deal.leadId}",
                payload = "KUNDE: ${deal.clientName} | WERT: ${deal.contractValueEur} € | PREPAID: ${deal.isPrepaid}",
                position = PhaseVector(coords).normalize(),
                mass = mass,
                isTerminal = deal.isDelivered
            )
        )
    }

    fun evaluateBusinessState(monthlyFixedCostsEur: Double = 0.0): BusinessTelemetry {
        val totalRevenue = pipeline.values.filter { it.isPrepaid }.sumOf { it.contractValueEur }
        val outstanding = pipeline.values.filter { !it.isPrepaid }.sumOf { it.contractValueEur }
        val undelivered = pipeline.values.filter { it.isPrepaid && !it.isDelivered }

        val yLoad = (outstanding * 0.002) + (undelivered.size * 4.0) + (monthlyFixedCostsEur * 0.01)
        val zDamping = (totalRevenue * 0.005).coerceIn(5.0, 30.0)

        mmsiEngine.processFrameInPlace(
            af7Alpha = zDamping,
            af8Alpha = zDamping,
            betaHigh = yLoad.coerceIn(1.0, 40.0),
            thetaPost = undelivered.size * 2.0,
            age = 30.0,
            sex = "M",
            deltaF7 = 10.0,
            deltaF8 = 10.0,
            out = frameOutput
        )

        val priorityAction = when {
            outstanding > 0.0 -> "Offene Rechnungen fällig stellen (Sofortiger Cash-Inflow via § 286 BGB)"
            undelivered.isNotEmpty() -> "Prepaid-Aufträge finalisieren (${undelivered.size} offen) -> Reibung abbauen"
            totalRevenue == 0.0 -> "Zero-Budget Akquise: B2B-Direktangebote mit 100% Vorkasse platzieren"
            else -> "System homöostatisch (Cashflow stabil, W <= 1.0)"
        }

        return BusinessTelemetry(
            monthlyRevenueEur = totalRevenue,
            outstandingCashEur = outstanding,
            cashConversionVelocity = if (totalRevenue > 0) 1.0 else 14.0,
            frictionW = frameOutput.wBounded,
            runwayDays = if (monthlyFixedCostsEur <= 0) 999 else ((totalRevenue / monthlyFixedCostsEur) * 30).toInt(),
            actionablePriority = priorityAction
        )
    }
}

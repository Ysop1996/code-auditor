package de.lifeos.core.sentinel

import de.lifeos.core.field.AttractorNode
import de.lifeos.core.field.DeterministicFieldEngine
import de.lifeos.core.field.PhaseVector
import de.lifeos.core.legal.ProUserLegalKernel
import de.lifeos.core.legal.HighPrecedentLegalEngine
import de.lifeos.core.legal.LegalExecutionResult
import de.lifeos.core.legal.DeepLegalStrategy
import net.sqlcipher.database.SQLiteDatabase
import kotlin.math.max
import kotlin.math.min
import kotlin.math.abs
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

/**
 * PROACTIVE SENTINEL ENGINE — Multi-Horizon Trajectory Prediction & 1-Tap Action Synthesis
 *
 * Transforms LIFE-OS from reactive to proactive. Continuously evaluates 4h, 24h, and 30-day horizons
 * for financial, legal, and temporal homeostasis. Synthesizes executable 1-tap interventions before
 * acute friction W(t) materializes.
 *
 * Vektoren:
 * - [EXP-AUTO] Autonomous self-steering: closes manual monitoring steps into closed proactive loops
 * - [EXP-FORCE] Strategic leverage: pre-emptive legal/financial interventions
 * - [EXP-SPEED] Latency multiplier: O(1) horizon evaluation via field engine reuse
 */
class ProactiveSentinelEngine(
    private val vaultDb: SQLiteDatabase,
    private val fieldEngine: DeterministicFieldEngine,
    private val legalKernel: ProUserLegalKernel,
    private val precedentEngine: HighPrecedentLegalEngine,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
) {
    private val horizonEvaluators = mutableMapOf<Horizon, HorizonEvaluation>()
    private val interventionQueue = MutableSharedFlow<ProactiveIntervention>(extraBufferCapacity = 64)
    private val _activeInterventions = MutableStateFlow<List<ProactiveIntervention>>(emptyList())
    val activeInterventions: StateFlow<List<ProactiveIntervention>> = _activeInterventions.asStateFlow()

    enum class Horizon(val label: String, val millis: Long, val weight: Float) {
        HORIZON_4H("4H", 4 * 60 * 60 * 1000L, 0.15f),
        HORIZON_24H("24H", 24 * 60 * 60 * 1000L, 0.35f),
        HORIZON_30D("30D", 30L * 24 * 60 * 60 * 1000L, 0.50f)
    }

    data class ProactiveIntervention(
        val id: String,
        val horizon: Horizon,
        val category: InterventionCategory,
        val urgencyScore: Float,
        val title: String,
        val description: String,
        val oneTapAction: OneTapAction,
        val predictedFrictionDelta: Float,
        val createdAt: Long = System.currentTimeMillis()
    )

    data class OneTapAction(
        val actionType: ActionType,
        val payload: Map<String, String>,
        val preconditions: List<String> = emptyList()
    )

    enum class ActionType {
        LEGAL_LETTER_GENERATE,
        DEBT_DISPUTE_SEND,
        PAYMENT_RELEASE,
        APPOINTMENT_BUFFER,
        DSGVO_REQUEST_SEND,
        WITHDRAWAL_RIGHT_ACTIVATE,
        SETTLEMENT_OFFER_GENERATE,
        NOTIFICATION_DISMISS
    }

    enum class InterventionCategory {
        LEGAL_DEADLINE,
        FINANCIAL_RUNWAY,
        DEBT_ESCALATION,
        APPOINTMENT_CONFLICT,
        DSGVO_COMPLIANCE,
        CONTRACT_RENEWAL
    }

    data class HorizonEvaluation(
        val horizon: Horizon,
        val financialRunwayDays: Int,
        val legalDeadlinesDue: Int,
        val debtPressure: Float,
        val appointmentConflicts: Int,
        val compositeRiskScore: Float
    )

    init {
        startContinuousEvaluation()
    }

    private fun startContinuousEvaluation() {
        scope.launch {
            while (isActive) {
                evaluateAllHorizons()
                delay(30_000L) // Re-evaluate every 30 seconds
            }
        }
    }

    /**
     * Evaluates all three horizons and synthesizes interventions.
     * O(1) per horizon via cached field state.
     */
    fun evaluateAllHorizons() {
        val evaluations = Horizon.values().map { horizon ->
            evaluateHorizon(horizon)
        }

        val interventions = mutableListOf<ProactiveIntervention>()
        evaluations.forEach { eval ->
            interventions.addAll(synthesizeInterventions(eval))
        }

        _activeInterventions.value = interventions.sortedByDescending { it.urgencyScore }
        interventions.forEach { interventionQueue.tryEmit(it) }
    }

    private fun evaluateHorizon(horizon: Horizon): HorizonEvaluation {
        val cutoff = System.currentTimeMillis() + horizon.millis

        // Legal deadlines within horizon
        val legalCursor = vaultDb.rawQuery(
            """SELECT count(*) FROM legal_cases 
               WHERE status IN ('PENDING', 'ACTIVE') 
               AND deadline_epoch < ? AND deadline_epoch > ?""",
            arrayOf(cutoff.toString(), System.currentTimeMillis().toString())
        )
        val legalDeadlines = legalCursor.use { if (it.moveToFirst()) it.getInt(0) else 0 }

        // Debt pressure within horizon
        val debtCursor = vaultDb.rawQuery(
            """SELECT sum(current_claim) FROM debt_ledger 
               WHERE strategy_status IN ('PENDING', 'OVERDUE') 
               AND statute_barred_epoch < ?""",
            arrayOf(cutoff.toString())
        )
        val debtPressure = debtCursor.use {
            if (it.moveToFirst()) it.getDouble(0).toFloat() else 0.0f
        }

        // Appointment conflicts (simplified heuristic)
        val appointmentConflicts = detectAppointmentConflicts(cutoff)

        // Financial runway via field engine
        val financialRunway = calculateFinancialRunwayDays()

        // Composite risk score: weighted sum normalized to [0, 10]
        val rawScore = (
            (legalDeadlines * 2.5f * horizon.weight) +
            (min(debtPressure / 1000.0f, 5.0f) * horizon.weight) +
            (appointmentConflicts * 3.0f * horizon.weight) +
            (max(0f, (30 - financialRunway).toFloat()) * 0.2f * horizon.weight)
        )
        val compositeRisk = min(rawScore.coerceIn(0.0f, 10.0f), 10.0f)

        return HorizonEvaluation(
            horizon = horizon,
            financialRunwayDays = financialRunway,
            legalDeadlinesDue = legalDeadlines,
            debtPressure = debtPressure,
            appointmentConflicts = appointmentConflicts,
            compositeRiskScore = compositeRisk
        )
    }

    private fun detectAppointmentConflicts(cutoff: Long): Int {
        // Placeholder: integrate with calendar provider when available
        // Currently returns 0, but structure supports calendar fusion
        return 0
    }

    private fun calculateFinancialRunwayDays(): Int {
        val cursor = vaultDb.rawQuery(
            """SELECT sum(current_claim) FROM debt_ledger 
               WHERE strategy_status IN ('PENDING', 'OVERDUE')""",
            null
        )
        val totalDebt = cursor.use { if (it.moveToFirst()) it.getDouble(0) else 0.0 }

        val revenueCursor = vaultDb.rawQuery(
            """SELECT sum(contract_value_eur) FROM business_pipeline 
               WHERE is_prepaid = 1 AND is_delivered = 0""",
            null
        )
        val outstandingRevenue = revenueCursor.use { if (it.moveToFirst()) it.getDouble(0) else 0.0 }

        val monthlyFixed = 1200.0 // Placeholder: integrate with budget engine
        val runway = if (monthlyFixed > 0) ((outstandingRevenue - totalDebt) / monthlyFixed * 30).toInt() else 999
        return max(runway, 0)
    }

    private fun synthesizeInterventions(eval: HorizonEvaluation): List<ProactiveIntervention> {
        val interventions = mutableListOf<ProactiveIntervention>()

        // Legal deadline intervention
        if (eval.legalDeadlinesDue > 0 && eval.compositeRiskScore > 2.0f) {
            val letter = legalKernel.compileDispute(
                opponentName = "AUTO_GENERATED",
                opponentAddress = "SYSTEM",
                disputeSubject = "Proaktive Fristwahrung (Horizon ${eval.horizon.label})",
                amount = eval.debtPressure.toDouble(),
                isAgbDefense = false
            )
            interventions.add(
                ProactiveIntervention(
                    id = "INT_LEGAL_${System.currentTimeMillis()}",
                    horizon = eval.horizon,
                    category = InterventionCategory.LEGAL_DEADLINE,
                    urgencyScore = min(eval.compositeRiskScore * 1.2f, 10.0f),
                    title = "Fristwahrung: ${eval.legalDeadlinesDue} offene Termine",
                    description = "Automatische Schriftsatzerzeugung für ${eval.legalDeadlinesDue} anstehende Fristen im ${eval.horizon.label}-Horizont.",
                    oneTapAction = OneTapAction(
                        actionType = ActionType.LEGAL_LETTER_GENERATE,
                        payload = mapOf(
                            "letter_draft" to letter.generatedLetter,
                            "statutes" to letter.statutesApplied.joinToString(", "),
                            "deadline" to letter.calculatedDeadline
                        )
                    ),
                    predictedFrictionDelta = -1.5f
                )
            )
        }

        // Financial runway intervention
        if (eval.financialRunwayDays < 14 && eval.financialRunwayDays >= 0) {
            interventions.add(
                ProactiveIntervention(
                    id = "INT_FIN_${System.currentTimeMillis()}",
                    horizon = eval.horizon,
                    category = InterventionCategory.FINANCIAL_RUNWAY,
                    urgencyScore = min((14 - eval.financialRunwayDays).toFloat() * 0.5f, 10.0f),
                    title = "Cashflow-Alarm: ${eval.financialRunwayDays} Tage Restlaufzeit",
                    description = "Finanzielle Homöostase gefährdet. Sofortige Maßnahmen: Offene Forderungen einziehen, Skonto-Optimierung prüfen.",
                    oneTapAction = OneTapAction(
                        actionType = ActionType.PAYMENT_RELEASE,
                        payload = mapOf(
                            "runway_days" to eval.financialRunwayDays.toString(),
                            "recommended_action" to "MAHNU_OFFENE_FORDERUNGEN"
                        )
                    ),
                    predictedFrictionDelta = -2.0f
                )
            )
        }

        // Debt escalation intervention
        if (eval.debtPressure > 500.0f && eval.compositeRiskScore > 3.0f) {
            val strategy = precedentEngine.buildMaximalLeverageStrategy(
                opponentType = "GLAEUBIGER",
                opponentClaim = eval.debtPressure.toDouble(),
                referenceText = "AUTO_DETECTED_DEBT_PRESSURE"
            )
            interventions.add(
                ProactiveIntervention(
                    id = "INT_DEBT_${System.currentTimeMillis()}",
                    horizon = eval.horizon,
                    category = InterventionCategory.DEBT_ESCALATION,
                    urgencyScore = min(eval.compositeRiskScore * 1.1f, 10.0f),
                    title = "Schulden-Druck: ${eval.debtPressure.toInt()} € akkumuliert",
                    description = "Strategische Gegenvorstellung mit DSGVO-Schadensersatz und AGB-Nichtigkeitsprüfung vorbereitet.",
                    oneTapAction = OneTapAction(
                        actionType = ActionType.DEBT_DISPUTE_SEND,
                        payload = mapOf(
                            "strategy_letter" to strategy.tacticalLetter,
                            "precedents" to strategy.precedentChain.joinToString { "${it.court} ${it.fileReference}" }
                        )
                    ),
                    predictedFrictionDelta = -1.8f
                )
            )
        }

        // DSGVO compliance intervention
        if (eval.compositeRiskScore > 4.0f) {
            interventions.add(
                ProactiveIntervention(
                    id = "INT_DSGVO_${System.currentTimeMillis()}",
                    horizon = eval.horizon,
                    category = InterventionCategory.DSGVO_COMPLIANCE,
                    urgencyScore = min(eval.compositeRiskScore * 0.9f, 10.0f),
                    title = "DSGVO-Auskunft: Art. 15 / Art. 82 Druckmittel aktivieren",
                    description = "Automatisches Auskunftsersuchen an bekannte Datenverarbeiter zur Stärkung der Verhandlungsposition.",
                    oneTapAction = OneTapAction(
                        actionType = ActionType.DSGVO_REQUEST_SEND,
                        payload = mapOf(
                            "legal_basis" to "Art. 15 DSGVO, Art. 82 DSGVO",
                            "deadline" to "30 Tage",
                            "template" to "DSGVO_ART15_ART82_TEMPLATE"
                        )
                    ),
                    predictedFrictionDelta = -0.8f
                )
            )
        }

        return interventions
    }

    /**
     * Returns the highest-priority 1-tap action for the current system state.
     * O(1) lookup from active interventions.
     */
    fun getTopPriorityAction(): ProactiveIntervention? {
        return _activeInterventions.value.firstOrNull()
    }

    /**
     * Registers a proactive intervention as an AttractorNode in the field engine
     * for trajectory-based prioritization.
     */
    fun registerInterventionAsAttractor(intervention: ProactiveIntervention) {
        val coords = FloatArray(32) { idx ->
            val factor = when (idx % 4) {
                0 -> intervention.urgencyScore / 10.0f
                1 -> intervention.predictedFrictionDelta * -1.0f
                2 -> intervention.horizon.weight * 10.0f
                else -> (intervention.category.ordinal + 1).toFloat() / 6.0f
            }
            (factor * 2.0f - 1.0f).coerceIn(-1.0f, 1.0f)
        }
        fieldEngine.registerNode(
            AttractorNode(
                id = "SENTINEL_${intervention.id}",
                payload = "[${intervention.horizon.label}] ${intervention.title}",
                position = PhaseVector(coords).normalize(),
                mass = intervention.urgencyScore * 0.5f,
                isTerminal = false
            )
        )
    }

    /**
     * Executes a 1-tap intervention and returns the result payload.
     * Human-in-the-loop: returns draft for confirmation, does not auto-send.
     */
    fun executeOneTapAction(intervention: ProactiveIntervention): ActionExecutionResult {
        return when (intervention.oneTapAction.actionType) {
            ActionType.LEGAL_LETTER_GENERATE -> {
                val letter = intervention.oneTapAction.payload["letter_draft"] ?: ""
                ActionExecutionResult(
                    success = true,
                    payload = mapOf("generated_letter" to letter),
                    humanConfirmationRequired = true,
                    confirmationPrompt = "Schriftsatz generieren und in Zwischenablage legen?"
                )
            }
            ActionType.DEBT_DISPUTE_SEND -> {
                val strategyLetter = intervention.oneTapAction.payload["strategy_letter"] ?: ""
                ActionExecutionResult(
                    success = true,
                    payload = mapOf("dispute_letter" to strategyLetter),
                    humanConfirmationRequired = true,
                    confirmationPrompt = "Gegenvorstellung mit DSGVO-Druckmittel versenden?"
                )
            }
            ActionType.PAYMENT_RELEASE -> {
                ActionExecutionResult(
                    success = true,
                    payload = mapOf("status" to "MAHNUNG_VORBEREITET"),
                    humanConfirmationRequired = true,
                    confirmationPrompt = "Mahnung für offene Forderungen vorbereiten?"
                )
            }
            ActionType.DSGVO_REQUEST_SEND -> {
                ActionExecutionResult(
                    success = true,
                    payload = mapOf("template" to "DSGVO_ART15_ART82_TEMPLATE"),
                    humanConfirmationRequired = true,
                    confirmationPrompt = "DSGVO-Auskunftsersuchen an Verarbeiter senden?"
                )
            }
            else -> ActionExecutionResult(success = false, payload = emptyMap(), humanConfirmationRequired = false)
        }
    }

    data class ActionExecutionResult(
        val success: Boolean,
        val payload: Map<String, String>,
        val humanConfirmationRequired: Boolean,
        val confirmationPrompt: String = ""
    )

    fun getInterventionFlow(): SharedFlow<ProactiveIntervention> = interventionQueue.asSharedFlow()

    fun shutdown() {
        scope.coroutineContext.cancelChildren()
    }
}

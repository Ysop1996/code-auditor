package de.lifeos.core.jobcenter

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
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.UUID

/**
 * JOBCENTER CASE ENGINE — SGB-II/SGB-XII Fallverwaltung, Sanktionsanalyse & Widerspruchsgenerator
 *
 * Spezialisiertes Modul für die Verwaltung von Jobcenter-Fällen:
 * - Fallregistrierung mit SGB-II/SGB-XII-spezifischen Attributen
 * - Sanktionsverfolgung und Minderungsanalyse (max. 30% nach BVerfG)
 * - Widerspruchs- und Überprüfungsantragsgenerierung
 * - Integriert in MMSI V3.8 Feldengine für Reibungsberechnung
 * - BVerfG-Härtefall-Prüfung und §86b SGG Vollzugsaussetzung
 *
 * Vektoren:
 * - [EXP-FORCE] Strategischer Rechtshebel: BVerfG-Prüfung, Härtefallanträge, Widersprüche
 * - [EXP-AUTO] Autonome Selbststeuerung: Fristüberwachung, automatische Antragsgenerierung
 * - [EXP-SENSE] Wahrnehmungserweiterung: Portal-Navigation, Bescheid-Erkennung
 */
class JobcenterCaseEngine(
    private val vaultDb: SQLiteDatabase,
    private val fieldEngine: DeterministicFieldEngine,
    private val legalKernel: ProUserLegalKernel,
    private val precedentEngine: HighPrecedentLegalEngine,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
) {
    private val caseRegistry = mutableMapOf<String, JobcenterCase>()
    private val _activeCases = MutableStateFlow<List<JobcenterCase>>(emptyList())
    val activeCases: StateFlow<List<JobcenterCase>> = _activeCases.asStateFlow()

    private val _sanctionAlerts = MutableStateFlow<List<SanctionAlert>>(emptyList())
    val sanctionAlerts: StateFlow<List<SanctionAlert>> = _sanctionAlerts.asStateFlow()

    private val dateFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy")

    enum class CaseStatus { REGISTERED, PENDING_REVIEW, APPEAL_FILED, OVERDUE, RESOLVED }
    enum class BenefitType { SGB_II, SGB_XII, SGB_III, KINDERGELD, WOHNGELD }
    enum class SanctionType { MELDEPFLICHT, ANGEBOT_ABLEHNUNG, SONSTIGE_PFLICHTVERLETZUNG }

    data class JobcenterCase(
        val caseId: String,
        val clientName: String,
        val benefitType: BenefitType,
        val monthlyAmount: Double,
        val startDate: String,
        val jobcenterName: String,
        val status: CaseStatus = CaseStatus.REGISTERED,
        val sanctions: List<SanctionRecord> = emptyList(),
        val appeals: List<AppealRecord> = emptyList(),
        val notes: String = ""
    )

    data class SanctionRecord(
        val sanctionId: String,
        val caseId: String,
        val type: SanctionType,
        val percentage: Int,
        val startDate: String,
        val endDate: String?,
        val reason: String,
        val isConstitutional: Boolean = false
    )

    data class AppealRecord(
        val appealId: String,
        val caseId: String,
        val type: AppealType,
        val filingDate: String,
        val deadline: String,
        val status: AppealStatus,
        val documentReference: String? = null
    )

    enum class AppealType { WIDERSPRUCH, ANTRAG_AUF_ANHOERUNG, KLAGE, BVERFG_BESCHWERDE }
    enum class AppealStatus { DRAFTED, FILED, PENDING, DECIDED, WITHDRAWN }

    data class SanctionAlert(
        val caseId: String,
        val clientName: String,
        val sanctionPercentage: Int,
        val isConstitutionalViolation: Boolean,
        val recommendedAction: String,
        val urgencyScore: Float
    )

    init {
        startContinuousMonitoring()
    }

    private fun startContinuousMonitoring() {
        scope.launch {
            while (isActive) {
                evaluateCaseFriction()
                delay(60_000L) // Check every minute
            }
        }
    }

    fun registerCase(
        clientName: String,
        benefitType: BenefitType,
        monthlyAmount: Double,
        startDate: String,
        jobcenterName: String,
        notes: String = ""
    ): JobcenterCase {
        val caseId = "JC_${UUID.randomUUID().toString().take(8)}"
        val case = JobcenterCase(
            caseId = caseId,
            clientName = clientName,
            benefitType = benefitType,
            monthlyAmount = monthlyAmount,
            startDate = startDate,
            jobcenterName = jobcenterName,
            notes = notes
        )

        caseRegistry[caseId] = case

        // Register as AttractorNode in field engine
        val coords = FloatArray(32) { idx ->
            val factor = when (idx % 4) {
                0 -> (monthlyAmount / 1000.0).toFloat().coerceIn(-1f, 1f)
                1 -> (benefitType.ordinal + 1).toFloat() / 5.0f
                2 -> 0.5f
                else -> 0.3f
            }
            factor.coerceIn(-1f, 1f)
        }
        fieldEngine.registerNode(
            AttractorNode(
                id = "JC_$caseId",
                payload = "JOBCENTER: $clientName | ${benefitType.name} | ${monthlyAmount}€",
                position = PhaseVector(coords).normalize(),
                mass = 1.0f + (monthlyAmount / 500.0).toFloat().coerceIn(0.0f, 5.0f),
                isTerminal = false
            )
        )

        persistCase(case)
        updateActiveCases()
        return case
    }

    fun addSanction(
        caseId: String,
        type: SanctionType,
        percentage: Int,
        startDate: String,
        endDate: String?,
        reason: String
    ): SanctionRecord {
        val case = caseRegistry[caseId] ?: throw IllegalArgumentException("Case $caseId not found")

        // BVerfG check: sanctions > 30% are unconstitutional
        val isConstitutional = percentage > 30

        val sanction = SanctionRecord(
            sanctionId = "SAN_${UUID.randomUUID().toString().take(8)}",
            caseId = caseId,
            type = type,
            percentage = percentage,
            startDate = startDate,
            endDate = endDate,
            reason = reason,
            isConstitutional = isConstitutional
        )

        val updatedCase = case.copy(
            sanctions = case.sanctions + sanction,
            status = if (isConstitutional) CaseStatus.PENDING_REVIEW else case.status
        )
        caseRegistry[caseId] = updatedCase

        // Generate alert if unconstitutional
        if (isConstitutional) {
            val alert = SanctionAlert(
                caseId = caseId,
                clientName = case.clientName,
                sanctionPercentage = percentage,
                isConstitutionalViolation = true,
                recommendedAction = "SOFORTIGE_VOLLZUGSAUSSETZUNG_NACH_86b_SGG",
                urgencyScore = 10.0f
            )
            _sanctionAlerts.value = (_sanctionAlerts.value + alert).sortedByDescending { it.urgencyScore }
        }

        persistCase(updatedCase)
        updateActiveCases()
        return sanction
    }

    fun fileAppeal(
        caseId: String,
        type: AppealType,
        deadline: String,
        documentReference: String? = null
    ): AppealRecord {
        val case = caseRegistry[caseId] ?: throw IllegalArgumentException("Case $caseId not found")

        val appeal = AppealRecord(
            appealId = "APP_${UUID.randomUUID().toString().take(8)}",
            caseId = caseId,
            type = type,
            filingDate = LocalDate.now().format(dateFormatter),
            deadline = deadline,
            status = AppealStatus.FILED,
            documentReference = documentReference
        )

        val updatedCase = case.copy(
            appeals = case.appeals + appeal,
            status = CaseStatus.APPEAL_FILED
        )
        caseRegistry[caseId] = updatedCase

        persistCase(updatedCase)
        updateActiveCases()
        return appeal
    }

    /**
     * Generates a comprehensive hardship application for unconstitutional sanctions.
     * Combines §86b SGG suspension with BVerfG constitutional complaint.
     */
    fun generateHardshipApplication(caseId: String): HardshipApplicationResult {
        val case = caseRegistry[caseId] ?: throw IllegalArgumentException("Case $caseId not found")
        val unconstitutionalSanctions = case.sanctions.filter { it.isConstitutional }

        if (unconstitutionalSanctions.isEmpty()) {
            return HardshipApplicationResult(
                caseId = caseId,
                applicationText = "KEINE_VERFASSUNGSWIDRIGEN_SANKTIONEN_FESTGESTELLT",
                isUrgent = false,
                recommendedActions = emptyList()
            )
        }

        val worstSanction = unconstitutionalSanctions.maxByOrNull { it.percentage }!!
        val monthlyLoss = case.monthlyAmount * (worstSanction.percentage / 100.0)

        val applicationText = """
            |ANTRAG AUF SOFORTIGE VOLLZUGSAUSSETZUNG NACH § 86b SGG
            |UND FESTSTELLUNGSKLAGE NACH BVERFG 1 BvR 1858/20
            |
            |1. SACHVERHALT
            |Gegen mich verhängte Sanktion von ${worstSanction.percentage}% verstößt gegen die verfassungsrechtlichen
            |Grundsätze der Existenzsicherung (BVerfG 1 BvR 1858/20). Die Minderung übersteigt die
            |verfassungsrechtlich gebotene Höchstgrenze von 30%.
            |
            |2. EXISTENZIELLE BEDRÄNGNIS
            |Monatlicher Regelsatz: ${case.monthlyAmount}€
            |Sanktionsbedingter Verlust: ${"%.2f".format(monthlyLoss)}€
            |Verbleibender Betrag: ${"%.2f".format(case.monthlyAmount - monthlyLoss)}€
            |
            |3. RECHTSGRUNDLAGEN
            |• § 86b SGG: Sofortige Vollzugsaussetzung bei schwerwiegenden Zweifeln
            |• BVerfG 1 BvR 1858/20: Verfassungswidrigkeit von Sanktionen über 30%
            |• Art. 1 GG: Menschenwürde als höchstes Verfassungsprinzip
            |
            |4. GERICHTLICHE GELTENDMACHUNG
            |Ich beantrage die sofortige Vollzugsaussetzung und die Feststellung
            |der Verfassungswidrigkeit der Sanktionspraxis.
            |
            |Mit freundlichen Grüßen
        """.trimMargin()

        return HardshipApplicationResult(
            caseId = caseId,
            applicationText = applicationText,
            isUrgent = true,
            recommendedActions = listOf(
                "SOFORTIGE_VOLLZUGSAUSSETZUNG_NACH_86b_SGG",
                "BVERFG_BESCHWERDE_VORBEREITEN",
                "ANTRAG_AUF_ANHOERUNG_NACH_24_SGB_X"
            )
        )
    }

    /**
     * Generates a Widerspruch (objection) against a Bescheid (notice).
     */
    fun generateWiderspruch(caseId: String, bescheidReference: String): LegalExecutionResult {
        val case = caseRegistry[caseId] ?: throw IllegalArgumentException("Case $caseId not found")
        val deadline = LocalDate.now().plusWeeks(1).format(dateFormatter)

        val letter = """
            |${case.jobcenterName}
            |${case.clientName}
            |
            |Datum: ${LocalDate.now().format(dateFormatter)}
            |Betreff: Widerspruch gegen Bescheid $bescheidReference
            |Aktenzeichen: $caseId
            |
            |Sehr geehrte Damen und Herren,
            |
            |hiermit lege ich Widerspruch gegen den oben genannten Bescheid vom ${LocalDate.now().minusDays(7).format(dateFormatter)} ein.
            |
            |BEGRÜNDUNG:
            |Der Bescheid ist aus folgenden Gründen rechtswidrig:
            |
            |1. FORMALE MÄNGEL
            |• Unzureichende Begründung der Entscheidung
            |• Fehlende Rechtsbehelfsbelehrung
            |
            |2. MATERIELLE RECHTSWIDRIGKEIT
            |• Verstoß gegen § 24 SGB X (Anhörungsrecht)
            |• Unzureichende Ermittlung des Sachverhalts
            |
            |3. EXISTENZIELLE BEDRÄNGNIS
            |Die geplante Maßnahme gefährdet meine Existenzsicherung.
            |
            |Ich bitte um Überprüfung und Aufhebung des Bescheids.
            |
            |Frist zur Stellungnahme: $deadline
            |
            |Mit freundlichen Grüßen
        """.trimMargin()

        return LegalExecutionResult(
            posture = de.lifeos.core.legal.LegalPosture.ASSERTION_MAXIMUM,
            statutesApplied = listOf("§ 24 SGB X", "§ 86b SGG", "BVerfG 1 BvR 1858/20"),
            calculatedDeadline = deadline,
            generatedLetter = letter
        )
    }

    fun getCase(caseId: String): JobcenterCase? = caseRegistry[caseId]
    fun getAllCases(): List<JobcenterCase> = caseRegistry.values.toList()

    private fun persistCase(case: JobcenterCase) {
        runCatching {
            vaultDb.execSQL(
                """INSERT OR REPLACE INTO jobcenter_cases 
                   (case_id, client_name, benefit_type, monthly_amount, start_date, 
                    jobcenter_name, status, notes) VALUES (?, ?, ?, ?, ?, ?, ?, ?)""",
                arrayOf(
                    case.caseId,
                    case.clientName,
                    case.benefitType.name,
                    case.monthlyAmount,
                    case.startDate,
                    case.jobcenterName,
                    case.status.name,
                    case.notes
                )
            )
        }
    }

    private fun updateActiveCases() {
        _activeCases.value = caseRegistry.values.sortedByDescending { it.monthlyAmount }
    }

    fun evaluateCaseFriction(): CaseFrictionResult {
        val totalMonthly = caseRegistry.values.sumOf { it.monthlyAmount }
        val activeSanctions = caseRegistry.values.flatMap { it.sanctions }
        val unconstitutionalCount = activeSanctions.count { it.isConstitutional }

        val friction = if (totalMonthly > 0) {
            (unconstitutionalCount * 3.0 + activeSanctions.size * 0.5).coerceIn(0.1, 10.0)
        } else 0.1

        return CaseFrictionResult(
            totalCases = caseRegistry.size,
            totalMonthlyBenefits = totalMonthly,
            activeSanctions = activeSanctions.size,
            unconstitutionalSanctions = unconstitutionalCount,
            frictionW = friction,
            priorityActions = buildList {
                if (unconstitutionalCount > 0) add("Härtefallanträge nach §86b SGG für $unconstitutionalCount Fälle")
                if (activeSanctions.isNotEmpty()) add("Widersprüche gegen ${activeSanctions.size} Sanktionen prüfen")
                if (totalMonthly > 0) add("Regelbedarfsprüfung für ${caseRegistry.size} Fälle")
            }
        )
    }

    data class CaseFrictionResult(
        val totalCases: Int,
        val totalMonthlyBenefits: Double,
        val activeSanctions: Int,
        val unconstitutionalSanctions: Int,
        val frictionW: Double,
        val priorityActions: List<String>
    )

    data class HardshipApplicationResult(
        val caseId: String,
        val applicationText: String,
        val isUrgent: Boolean,
        val recommendedActions: List<String>
    )

    fun shutdown() {
        scope.coroutineContext.cancelChildren()
    }
}

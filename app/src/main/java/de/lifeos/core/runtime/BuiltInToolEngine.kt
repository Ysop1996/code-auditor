package de.lifeos.core.runtime

import de.lifeos.core.workshop.ToolType
import net.sqlcipher.database.SQLiteDatabase

/**
 * BUILT-IN TOOL ENGINE — Verifizierte, vorcompilierte Werkzeug-Implementierungen
 * für die Adaptive Workshop Synthesizer.
 *
 * Jedes Tool implementiert DynamicPluginModule und wird deterministisch
 * aus den Anforderungs-Primitiven assembliert. Zero-Disk-Trace: alle
 * Berechnungen erfolgen im RAM.
 *
 * Vektoren:
 * - [EXP-SYNTH] Selbsterweiternde Werkzeugsynthese ohne Netzzugriff
 * - [SEC-RAM] Zero-Disk-Trace: keine temporären Dateien
 */
class BuiltInToolEngine(private val vaultDb: SQLiteDatabase) {

    // =========================================================================
    // INTEREST CALCULATOR — §288 BGB Verzugszinsen + 5/9 Punkte + 40€ Pauschale
    // =========================================================================

    class InterestCalculatorModule : DynamicPluginModule {
        override fun executeAction(params: Map<String, Any?>): Map<String, Any?> {
            val principal = (params["principal"] as? Number)?.toDouble() ?: 0.0
            val days = (params["days"] as? Number)?.toInt() ?: 0
            val baseRate = (params["baseRate"] as? Number)?.toDouble() ?: 9.0 // aktueller Basiszinssatz
            val margin = 5.0 / 9.0 // §288 Abs. 2 BGB: 5 Prozentpunkte über Basiszinssatz
            val lumpSum = 40.0 // §288 Abs. 5 BGB: Pauschale

            val annualRate = baseRate + margin
            val interest = principal * (annualRate / 100.0) * (days / 365.0)
            val total = principal + interest + lumpSum

            return mapOf(
                "status" to "calculated",
                "principal" to principal,
                "days" to days,
                "annualRate" to String.format("%.2f", annualRate),
                "interest" to String.format("%.2f", interest),
                "lumpSum" to lumpSum,
                "totalClaim" to String.format("%.2f", total),
                "legalBasis" to "§288 BGB i.V.m. §291 BGB"
            )
        }
    }

    // =========================================================================
    // BESCHEID EXTRACTOR — Extrahiert strukturierte Daten aus Bescheid-Text
    // =========================================================================

    class BescheidExtractorModule : DynamicPluginModule {
        override fun executeAction(params: Map<String, Any?>): Map<String, Any?> {
            val rawText = (params["text"] as? String) ?: ""
            val extracted = mutableMapOf<String, String>()

            // Behörden-Identifikation
            val authorityPattern = Regex("(Jobcenter|Agentur für Arbeit|Finanzamt|Stadt|Kreis|Landkreis)\\s+([A-Za-zäöüÄÖÜß\\s]+)")
            authorityPattern.find(rawText)?.let { match ->
                extracted["authority"] = match.groupValues[1].trim()
                extracted["location"] = match.groupValues[2].trim()
            }

            // Bescheid-Nummer
            val noticePattern = Regex("(Bescheid|Aktenzeichen|Az\\.?|Aktenz\\.?)[:\\s]+([A-Za-z0-9\\-/]+)", RegexOption.IGNORE_CASE)
            noticePattern.find(rawText)?.let { match ->
                extracted["noticeNumber"] = match.groupValues[2].trim()
            }

            // Datum
            val datePattern = Regex("(\\d{1,2}\\.\\d{1,2}\\.\\d{4}|\\d{4}-\\d{2}-\\d{2})")
            val dates = datePattern.findAll(rawText).map { it.value }.toList()
            if (dates.isNotEmpty()) {
                extracted["date"] = dates.first()
                if (dates.size > 1) extracted["date2"] = dates[1]
            }

            // Betrag
            val amountPattern = Regex("(\\d{1,3}(?:\\.\\d{3})*,\\d{2})\\s*€?", RegexOption.IGNORE_CASE)
            amountPattern.find(rawText)?.let { match ->
                extracted["amount"] = match.groupValues[1]
            }

            // Sanktion
            val sanctionPattern = Regex("(Sanktion|Minderung|Kürzung)[:\\s]+(\\d+)\\s*%", RegexOption.IGNORE_CASE)
            sanctionPattern.find(rawText)?.let { match ->
                extracted["sanctionType"] = match.groupValues[1]
                extracted["sanctionPercent"] = match.groupValues[2]
            }

            // Frist
            val deadlinePattern = Regex("(Widerspruch|Einspruch|Klage)[^\\d]*(\\d{1,2}\\s*(?:Wochen|Monate|Tage))", RegexOption.IGNORE_CASE)
            deadlinePattern.find(rawText)?.let { match ->
                extracted["deadlineType"] = match.groupValues[1]
                extracted["deadlinePeriod"] = match.groupValues[2]
            }

            return mapOf(
                "status" to "extracted",
                "fields" to extracted.size,
                "data" to extracted
            )
        }
    }

    // =========================================================================
    // DSGVO REQUEST GENERATOR — Art. 15/82 DSGVO Auskunftsersuchen
    // =========================================================================

    class DSGVORequestGeneratorModule : DynamicPluginModule {
        override fun executeAction(params: Map<String, Any?>): Map<String, Any?> {
            val recipient = (params["recipient"] as? String) ?: "Verantwortlicher"
            val requesterName = (params["name"] as? String) ?: "Antragsteller"
            val specificData = (params["specificData"] as? String) ?: "alle personenbezogenen Daten"

            val letter = """
                DSGVO-AUSKUNFTSERSUCHEN — Art. 15 DSGVO

                Sehr geehrte Damen und Herren,

                ich bitte Sie um Auskunft über die Sie mich betreffenden personenbezogenen Daten gemäß Art. 15 DSGVO.

                Betreff: Auskunft über $specificData

                Ich bitte um Mitteilung:
                1. der mich betreffenden personenbezogenen Daten;
                2. der Verarbeitungszwecke;
                3. der Kategorien personenbezogener Daten;
                4. der Empfänger oder Kategorien von Empfängern;
                5. der geplanten Speicherdauer;
                6. des Bestehens eines Rechts auf Berichtigung oder Löschung;
                7. des Bestehens eines Beschwerderechts bei einer Aufsichtsbehörde.

                Gemäß Art. 12 Abs. 3 DSGVO bitte ich um unverzügliche Auskunft, spätestens innerhalb eines Monats nach Eingang dieses Antrags.

                Sollte ich weitere Informationen benötigen, werde ich mich an Sie wenden.

                Mit freundlichen Grüßen,
                $requesterName
            """.trimIndent()

            return mapOf(
                "status" to "generated",
                "type" to "DSGVO_AUSKUNFT",
                "recipient" to recipient,
                "letter" to letter,
                "legalBasis" to "Art. 15 DSGVO, Art. 82 DSGVO"
            )
        }
    }

    // =========================================================================
    // CONTRACT ANALYZER — AGB-Nichtigkeitsprüfung §§307-309 BGB
    // =========================================================================

    class ContractAnalyzerModule : DynamicPluginModule {
        override fun executeAction(params: Map<String, Any?>): Map<String, Any?> {
            val contractText = (params["text"] as? String) ?: ""
            val findings = mutableListOf<String>()

            // §307 BGB: Unangemessene Benachteiligung
            val unfairPatterns = listOf(
                Regex("(Haftungsausschluss|Haftungsbefreiung|Keine Haftung)", RegexOption.IGNORE_CASE) to "§307 BGB: Verdacht auf unangemessenen Haftungsausschluss",
                Regex("(Vertragsänderung|Änderung vorbehalten|Einseitige Änderung)", RegexOption.IGNORE_CASE) to "§307 BGB: Verdacht auf einseitige Vertragsänderungsvorbehalt",
                Regex("(Verjährung|Verjährt|Ausschlussfrist).{0,30}(\\d+)\\s*(Monate|Wochen|Tage)", RegexOption.IGNORE_CASE) to "§309 Nr. 9 BGB: Verjährungsfrist möglicherweise zu kurz",
                Regex("(Pauschalierter Schadenersatz|Pauschale).{0,50}(unverhältnismäßig|angemessen)", RegexOption.IGNORE_CASE) to "§309 Nr. 6 BGB: Pauschalierte Schadenersatzklausel prüfen",
                Regex("(Gerichtsstand|Gerichtsstandvereinbarung|Ausschließlicher Gerichtsstand)", RegexOption.IGNORE_CASE) to "§309 Nr. 7a BGB: Gerichtsstandsvereinbarung möglicherweise unwirksam"
            )

            unfairPatterns.forEach { (pattern, finding) ->
                if (pattern.containsMatchIn(contractText)) {
                    findings.add(finding)
                }
            }

            // §308 BGB: Klauselverbote ohne Wertungsmöglichkeit
            val clausePatterns = listOf(
                Regex("(Nachfrist|Fristsetzung).{0,30}(unangemessen kurz|angemessen)", RegexOption.IGNORE_CASE) to "§308 BGB: Nachfrist möglicherweise unangemessen kurz",
                Regex("(Leistungsverweigerung|Zurückbehaltung).{0,30}(ausgeschlossen|unmöglich)", RegexOption.IGNORE_CASE) to "§308 BGB: Leistungsverweigerungsrecht möglicherweise ausgeschlossen"
            )

            clausePatterns.forEach { (pattern, finding) ->
                if (pattern.containsMatchIn(contractText)) {
                    findings.add(finding)
                }
            }

            val riskLevel = when {
                findings.size >= 3 -> "HOCH"
                findings.size >= 1 -> "MITTEL"
                else -> "NIEDRIG"
            }

            return mapOf(
                "status" to "analyzed",
                "riskLevel" to riskLevel,
                "findingsCount" to findings.size,
                "findings" to findings,
                "recommendation" to if (findings.isNotEmpty()) "Anwalt zur Prüfung empfehlen" else "Keine offensichtlichen Unwirksamkeitsmerkmale erkannt"
            )
        }
    }

    // =========================================================================
    // TAX DEDUCTOR — Steuerliche Absetzbarkeit prüfen
    // =========================================================================

    class TaxDeductorModule : DynamicPluginModule {
        override fun executeAction(params: Map<String, Any?>): Map<String, Any?> {
            val amount = (params["amount"] as? Number)?.toDouble() ?: 0.0
            val category = (params["category"] as? String) ?: "ALLGEMEIN"
            val year = (params["year"] as? Number)?.toInt() ?: 2026

            val deductionRules = mapOf(
                "ARBEITSMITTEL" to Pair(0.0, "bis 900€ Arbeitsmittel direkt absetzbar (§6 Abs. 1 Nr. 1 EStG)"),
                "FACHBÜCHER" to Pair(0.0, "Fachliteratur direkt absetzbar"),
                "FORTBILDUNG" to Pair(0.0, "Fortbildungskosten direkt absetzbar"),
                "BEWERBUNGSKOSTEN" to Pair(0.0, "Bewerbungskosten direkt absetzbar"),
                "ENTFERNUNGSFAHRTEN" to Pair(0.3, "Entfernungspauschale 0,30€/km"),
                "HAUSHALTSHILFE" to Pair(0.2, "Haushaltshilfe bis 20% der Einnahmen"),
                "SPENDEN" to Pair(1.0, "Spenden bis 20% des Einkommens")
            )

            val rule = deductionRules[category.uppercase()]
            val maxDeductible = if (rule != null && rule.first > 0) amount * rule.first else amount
            val legalBasis = rule?.second ?: "Allgemeine Betriebsausgaben (§4 Abs. 4 EStG)"

            return mapOf(
                "status" to "calculated",
                "category" to category,
                "amount" to amount,
                "maxDeductible" to String.format("%.2f", maxDeductible),
                "legalBasis" to legalBasis,
                "year" to year
            )
        }
    }

    // =========================================================================
    // CSV EXTRACTOR — Strukturierte Extraktion aus CSV-Daten
    // =========================================================================

    class CSVExtractorModule : DynamicPluginModule {
        override fun executeAction(params: Map<String, Any?>): Map<String, Any?> {
            val csvText = (params["csvText"] as? String) ?: ""
            val delimiter = (params["delimiter"] as? String) ?: ","
            val extractColumn = (params["column"] as? String) ?: ""

            val lines = csvText.lines().filter { it.isNotBlank() }
            if (lines.isEmpty()) {
                return mapOf("status" to "error", "message" to "Keine CSV-Daten gefunden")
            }

            val headers = lines.first().split(delimiter).map { it.trim().removeSurrounding("\"") }
            val dataRows = lines.drop(1)

            val columnIndex = if (extractColumn.isNotBlank()) {
                headers.indexOfFirst { it.equals(extractColumn, ignoreCase = true) }
            } else -1

            val extractedValues = mutableListOf<String>()
            dataRows.forEach { row ->
                val cells = row.split(delimiter).map { it.trim().removeSurrounding("\"") }
                if (columnIndex >= 0 && columnIndex < cells.size) {
                    extractedValues.add(cells[columnIndex])
                } else if (columnIndex == -1) {
                    extractedValues.add(cells.joinToString(delimiter))
                }
            }

            return mapOf(
                "status" to "extracted",
                "rowCount" to dataRows.size,
                "columnCount" to headers.size,
                "headers" to headers,
                "values" to extractedValues.take(100),
                "column" to (if (extractColumn.isNotBlank()) extractColumn else "ALL")
            )
        }
    }

    // =========================================================================
    // FINANCIAL RUNWAY CALCULATOR — Finanzielle Laufzeit berechnen
    // =========================================================================

    class FinancialRunwayCalculatorModule : DynamicPluginModule {
        override fun executeAction(params: Map<String, Any?>): Map<String, Any?> {
            val balance = (params["balance"] as? Number)?.toDouble() ?: 0.0
            val monthlyExpenses = (params["monthlyExpenses"] as? Number)?.toDouble() ?: 0.0
            val monthlyIncome = (params["monthlyIncome"] as? Number)?.toDouble() ?: 0.0
            val expectedIncome = (params["expectedIncome"] as? Number)?.toDouble() ?: 0.0

            val netMonthly = monthlyIncome - monthlyExpenses
            val monthsRemaining = if (netMonthly > 0) {
                Double.POSITIVE_INFINITY
            } else if (monthlyExpenses > 0) {
                balance / monthlyExpenses
            } else {
                Double.POSITIVE_INFINITY
            }

            val runwayWithExpected = if (expectedIncome > 0) {
                val adjustedNet = (monthlyIncome + expectedIncome) - monthlyExpenses
                if (adjustedNet > 0) Double.POSITIVE_INFINITY else balance / monthlyExpenses
            } else monthsRemaining

            val riskLevel = when {
                monthsRemaining < 1.0 -> "KRITISCH"
                monthsRemaining < 3.0 -> "HOCH"
                monthsRemaining < 6.0 -> "MITTEL"
                else -> "STABIL"
            }

            return mapOf(
                "status" to "calculated",
                "balance" to balance,
                "monthlyExpenses" to monthlyExpenses,
                "monthlyIncome" to monthlyIncome,
                "netMonthly" to netMonthly,
                "monthsRemaining" to if (monthsRemaining == Double.POSITIVE_INFINITY) "∞" else String.format("%.1f", monthsRemaining),
                "runwayWithExpected" to if (runwayWithExpected == Double.POSITIVE_INFINITY) "∞" else String.format("%.1f", runwayWithExpected),
                "riskLevel" to riskLevel
            )
        }
    }

    // =========================================================================
    // FACTORY
    // =========================================================================

    fun createModule(toolType: ToolType): DynamicPluginModule? {
        return when (toolType) {
            ToolType.INTEREST_CALCULATOR -> InterestCalculatorModule()
            ToolType.BESHEID_EXTRACTOR -> BescheidExtractorModule()
            ToolType.DSGVO_REQUEST_GENERATOR -> DSGVORequestGeneratorModule()
            ToolType.CONTRACT_ANALYZER -> ContractAnalyzerModule()
            ToolType.TAX_DEDUCTOR -> TaxDeductorModule()
            ToolType.CSV_EXTRACTOR -> CSVExtractorModule()
            ToolType.FINANCIAL_RUNWAY_CALCULATOR -> FinancialRunwayCalculatorModule()
        }
    }
}

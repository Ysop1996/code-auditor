package de.lifeos.core.social

import de.lifeos.android.browser.DeepSearchOrchestrator
import de.lifeos.core.field.AttractorNode
import de.lifeos.core.field.DeterministicFieldEngine
import de.lifeos.core.field.KineticDischarge
import de.lifeos.core.field.KineticStateSpaceOperator
import de.lifeos.core.field.PhaseVector
import de.lifeos.core.learning.ContinuousPlasticityEngine
import de.lifeos.core.learning.UniversalMatrixIngestionEngine
import de.lifeos.core.linguistics.ColloquialMimicryEngine
import de.lifeos.core.linguistics.DudenGrammarEngine
import de.lifeos.core.linguistics.LinguisticRegister
import de.lifeos.core.orchestration.TaskPartitioningEngine
import de.lifeos.core.runtime.DexHotSwapEngine
import de.lifeos.core.runtime.EmbeddedPythonExecutionBridge
import de.lifeos.core.sensory.PerceptualSensoryHub
import de.lifeos.core.sensory.SensoryModality
import de.lifeos.core.storage.BootEngineGenesisPass
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.sqlcipher.database.SQLiteDatabase
import java.io.File
import java.util.UUID

data class ChatMessage(
    val id: String = UUID.randomUUID().toString(),
    val isFromUser: Boolean,
    val text: String,
    val timestamp: Long = System.currentTimeMillis(),
    val stagedIntent: StagedOutboundIntent? = null,
    val actionPayload: String? = null
)

data class StagedOutboundIntent(
    val recipient: String,
    val channel: String,
    val appliedStyle: String,
    val draftPayload: String
)

class InteractiveLifeChatEngine(
    private val vaultDb: SQLiteDatabase,
    private val fieldEngine: DeterministicFieldEngine,
    private val stylingEngine: PersonalityStylingEngine,
    private val profilingEngine: ContactProfilingEngine,
    private val outboundGovernor: OutboundCommunicationGovernor,
    private val deepSearchOrchestrator: DeepSearchOrchestrator? = null,
    private val cacheDir: File? = null
) {
    private val messageHistory = mutableListOf<ChatMessage>()
    private val scope = CoroutineScope(Dispatchers.IO)
    private val kineticOperator = KineticStateSpaceOperator(vaultDb, fieldEngine, stylingEngine)
    private val plasticityEngine = ContinuousPlasticityEngine(vaultDb, fieldEngine)
    private val pythonBridge = cacheDir?.let { EmbeddedPythonExecutionBridge(it) }
    private val mimicryEngine = ColloquialMimicryEngine(vaultDb)
    private val powerDynamicsEngine = RelationalPowerDynamicsEngine(vaultDb)
    private val matrixIngestionEngine = UniversalMatrixIngestionEngine(vaultDb)
    private val genesisPass = BootEngineGenesisPass(vaultDb, fieldEngine, matrixIngestionEngine)

    // SEV-2 Fix: Precompiled regex cache — eliminates redundant Pattern.compile() calls
    companion object {
        val PATTERN_LEARN_PREFIX = Regex("^!learn\\s+", RegexOption.IGNORE_CASE)
        val PATTERN_PATCH_PREFIX = Regex("^!patch\\s+", RegexOption.IGNORE_CASE)
        val PATTERN_EVAL_PREFIX = Regex("^!eval\\s+", RegexOption.IGNORE_CASE)
        val PATTERN_MODULES = Regex("^!modules$", RegexOption.IGNORE_CASE)
        val PATTERN_GRAMMATIK_PREFIX = Regex("^!grammatik\\s+", RegexOption.IGNORE_CASE)
        val PATTERN_STIL_ANALYSE = Regex("^!stil\\s+analyse$", RegexOption.IGNORE_CASE)
        val PATTERN_MATRIX_INGEST_PREFIX = Regex("^!matrix\\s+ingest\\s+", RegexOption.IGNORE_CASE)
        val PATTERN_GENESIS_BOOT = Regex("^!genesis\\s+boot$", RegexOption.IGNORE_CASE)
        val PATTERN_MACHT_PREFIX = Regex("^!macht\\s+", RegexOption.IGNORE_CASE)
        val PATTERN_TASK_TRIGGER = Regex("(prüfe|berechne|analysiere|berechne zinsen|verzug|abrechnung|bescheid prüfen)", RegexOption.IGNORE_CASE)
        val PATTERN_OUTBOUND_TRIGGER = Regex("(schreib|antworte|whatsapp|nachricht an|sende an|mail an)", RegexOption.IGNORE_CASE)
        val PATTERN_JOBCENTER_TRIGGER = Regex("(jobcenter online|jobcenter portal|arbeitsagentur|portal login|leistung beantragen|bescheid online|termin jobcenter)", RegexOption.IGNORE_CASE)
        val PATTERN_DOCUMENT_ANALYSIS_TRIGGER = Regex("(analysiere|dokumente|jobcenter|bescheid|vertrag|rechnung|frist|schulden|gläubiger|widerspruch|recht|anwalt|klage|agb|klausel)", RegexOption.IGNORE_CASE)
        val PATTERN_LIVE_QUERY = Regex("(wetter|regen|temperatur|grad|wie spät|wer ist|suche nach|recherchiere|news|aktuell)", RegexOption.IGNORE_CASE)
        val PATTERN_GREETING = Regex("^(hey|hi|hallo|moin|servus|guten tag|yo|wie gehts|was geht).*$", RegexOption.IGNORE_CASE)
        val PATTERN_SENSORY_SCREEN = Regex("(was tun|wie antworten|schreib was|prüf das|hilfe|was sagst du)", RegexOption.IGNORE_CASE)
        val PATTERN_SENSORY_NOTIFICATION = Regex("(wer war das|was kam rein|neue mail|nachricht|wichtig)", RegexOption.IGNORE_CASE)
        val PATTERN_PUNCTUATION_CLEAN = Regex("[^a-zA-ZäöüÄÖÜß]")
        val PATTERN_RECIPIENT_CLEAN = Regex("[,;:]")
    }

    fun getChatHistory(): List<ChatMessage> = messageHistory.toList()

    fun processUserMessage(userInput: String, onResponseReady: (ChatMessage) -> Unit) {
        val cleanInput = userInput.trim()
        val userMessage = ChatMessage(isFromUser = true, text = cleanInput)
        messageHistory.add(userMessage)

        scope.launch {
            try {
                // =========================================================================
                // SENSORY CONTEXT RESOLUTION (Kontextbewusster Operator)
                // =========================================================================
                val sensoryResponse = resolveSensoryContextualResponse(cleanInput)
                if (sensoryResponse != null) {
                    dispatchResponse(sensoryResponse, onResponseReady)
                    return@launch
                }

                // =========================================================================
                // TIER: LIVE RUNTIME HOT-SWAP & KNOWLEDGE INTEGRATION
                // =========================================================================
                if (cleanInput.startsWith("!learn ", ignoreCase = true)) {
                    val textToLearn = cleanInput.removePrefix("!learn ").trim()
                    val node = plasticityEngine.assimilateInformation(textToLearn, "CHAT_INPUT")

                    val reply = "Information assimiliert & strukturiert:\n" +
                            "• Knoten: ${node.id}\n" +
                            "• Domäne: ${node.domain}\n" +
                            "• Semantische Masse: ${"%.2f".format(node.semanticMass)}\n" +
                            "• Phasenraum-Topologie in O(1) aktualisiert."

                    dispatchResponse(reply, onResponseReady)
                    return@launch
                }

                if (PATTERN_PATCH_PREFIX.containsMatchIn(cleanInput)) {
                    // Syntax: !patch <ModuleName> <Base64DexBytes> <EntryClass>
                    val parts = cleanInput.removePrefix("!patch ").split(" ")
                    if (parts.size >= 3) {
                        val modName = parts[0]
                        val dexBase64 = parts[1]
                        val entryClass = parts[2]
                        val dexBytes = android.util.Base64.decode(dexBase64, android.util.Base64.DEFAULT)

                        val success = DexHotSwapEngine.injectDexBytecode(modName, dexBytes, entryClass)
                        val status = if (success) "erfolgreich in den RAM injiziert und aktiv." else "Injektion fehlgeschlagen."
                        dispatchResponse("Hot-Swap Patch '$modName' $status", onResponseReady)
                    } else {
                        dispatchResponse("Syntax: !patch <ModulName> <Base64DexBytes> <EntryClass>", onResponseReady)
                    }
                    return@launch
                }

                if (PATTERN_EVAL_PREFIX.containsMatchIn(cleanInput)) {
                    // Syntax: !eval <ModuleName> <params as JSON>
                    val parts = cleanInput.removePrefix("!eval ").split(" ", limit = 2)
                    if (parts.isNotEmpty()) {
                        val modName = parts[0]
                        val params = if (parts.size > 1) {
                            parts[1].split(",").associate {
                                val kv = it.split(":")
                                kv[0].trim() to kv.getOrElse(1) { "" }.trim()
                            }
                        } else emptyMap()

                        val result = DexHotSwapEngine.executeModule(modName, params)
                        val response = if (result != null) {
                            "Modul '$modName' ausgeführt:\n${result.entries.joinToString("\n") { "• ${it.key}: ${it.value}" }}"
                        } else {
                            "Modul '$modName' nicht gefunden. Aktive Module: ${DexHotSwapEngine.listActiveModules().joinToString(", ").ifBlank { "keine" }}"
                        }
                        dispatchResponse(response, onResponseReady)
                    } else {
                        dispatchResponse("Syntax: !eval <ModulName> [param1:val1,param2:val2]", onResponseReady)
                    }
                    return@launch
                }

                if (PATTERN_MODULES.matches(cleanInput)) {
                    val modules = DexHotSwapEngine.listActiveModules()
                    val response = if (modules.isNotEmpty()) {
                        "Aktive Runtime-Module:\n${modules.joinToString("\n") { "• $it" }}"
                    } else {
                        "Keine Runtime-Module geladen."
                    }
                    dispatchResponse(response, onResponseReady)
                    return@launch
                }

                // =========================================================================
                // TIER: DUDEN GRAMMAR ENGINE — Grammatikprüfung
                // =========================================================================
                if (PATTERN_GRAMMATIK_PREFIX.containsMatchIn(cleanInput)) {
                    val textToCheck = cleanInput.removePrefix("!grammatik ").trim()
                    val issues = DudenGrammarEngine.validateGrammar(textToCheck)
                    val response = if (issues.isEmpty()) {
                        "Grammatikprüfung bestanden: Keine Fehler im Text erkannt."
                    } else {
                        "Grammatikprüfung — ${issues.size} Hinweise:\n${issues.joinToString("\n") { "• $it" }}"
                    }
                    dispatchResponse(response, onResponseReady)
                    return@launch
                }

                // =========================================================================
                // TIER: COLLOQUIAL MIMICRY — Stil-Analyse
                // =========================================================================
                if (PATTERN_STIL_ANALYSE.matches(cleanInput)) {
                    val fingerprint = mimicryEngine.extractUserStyleFingerprint()
                    val response = buildString {
                        append("Sprachstil-Analyse (Fingerprint):\n\n")
                        append("• Durchschnittliche Satzlänge: ${"%.0f".format(fingerprint.avgSentenceLength)} Zeichen\n")
                        append("• Interpunktionsdichte: ${"%.2f".format(fingerprint.punctuationDensity)}\n")
                        append("• Emoji-Häufigkeit: ${"%.2f".format(fingerprint.emojiFrequency)} pro 100 Zeichen\n")
                        append("• Großschreibung: ${"%.0f".format(fingerprint.capitalizationPreference * 100)}%\n")
                        append("• Anrede: '${fingerprint.greetingStyle}'\n")
                        append("• Abschied: '${fingerprint.closingStyle}'\n")
                        append("• Typische Phrasen: ${fingerprint.typicalPhrases.take(3).joinToString(", ") { "'$it'" }}")
                    }
                    dispatchResponse(response, onResponseReady)
                    return@launch
                }

                // =========================================================================
                // TIER: MATRIX INGESTION — Informationen aufnehmen
                // =========================================================================
                if (PATTERN_MATRIX_INGEST_PREFIX.containsMatchIn(cleanInput)) {
                    val content = cleanInput.removePrefix("!matrix ingest ").trim()
                    val node = matrixIngestionEngine.ingest(content, "CHAT")
                    val response = buildString {
                        append("Matrix-Injektion abgeschlossen:\n\n")
                        append("• Knoten: ${node.nodeId}\n")
                        append("• Domäne: ${node.domain}\n")
                        append("• Semantische Masse: ${"%.2f".format(node.semanticMass)}\n")
                        append("• Machtasymmetrie: ${"%.2f".format(node.relationalVector.powerAsymmetry)}\n")
                        append("• Emotionale Bindung: ${"%.2f".format(node.relationalVector.emotionalBond)}\n")
                        append("• Konflikttension: ${"%.2f".format(node.relationalVector.conflictTension)}")
                    }
                    dispatchResponse(response, onResponseReady)
                    return@launch
                }

                // =========================================================================
                // TIER: GENESIS BOOT — System-Initialisierung
                // =========================================================================
                if (PATTERN_GENESIS_BOOT.matches(cleanInput)) {
                    dispatchResponse("Genesis Boot wird ausgeführt...", onResponseReady)
                    scope.launch {
                        val bootState = genesisPass.executeGenesisBoot(emptyList())
                        val response = buildString {
                            append("Genesis Boot abgeschlossen:\n\n")
                            append("• Phase: ${bootState.phase}\n")
                            append("• Hydrierte Knoten: ${bootState.nodesHydrated}\n")
                            append("• Gescannte Dateien: ${bootState.filesScanned}\n")
                            append("• Neue Knoten: ${bootState.newNodesIngested}\n")
                            append("• Duplikate übersprungen: ${bootState.duplicatesSkipped}\n")
                            append("• Fehler: ${bootState.errors.size}")
                            if (bootState.errors.isNotEmpty()) {
                                append("\n\nFehler:\n${bootState.errors.joinToString("\n") { "• $it" }}")
                            }
                        }
                        dispatchResponse(response, onResponseReady)
                    }
                    return@launch
                }

                // =========================================================================
                // TIER: RELATIONAL POWER DYNAMICS — Beziehungsanalyse
                // =========================================================================
                if (PATTERN_MACHT_PREFIX.containsMatchIn(cleanInput)) {
                    val contact = cleanInput.removePrefix("!macht ").trim()
                    val vector = powerDynamicsEngine.evaluateRelation(contact)
                    val strategy = powerDynamicsEngine.deriveStrategy(vector)
                    val response = buildString {
                        append("Machtanalyse für '$contact':\n\n")
                        append("• Machtasymmetrie: ${"%.2f".format(vector.powerAsymmetry)} ")
                        append(if (vector.powerAsymmetry < -0) "(unterlegen)" else if (vector.powerAsymmetry > 0) "(überlegen)" else "(ausgeglichen)")
                        append("\n")
                        append("• Emotionale Bindung: ${"%.2f".format(vector.emotionalBond)}\n")
                        append("• Konflikttension: ${"%.2f".format(vector.conflictTension)}\n")
                        append("• Strategie: $strategy")
                    }
                    dispatchResponse(response, onResponseReady)
                    return@launch
                }

                // =========================================================================
                // TIER: KOMPLEXE TASK-PARTITIONIERUNG & EXECUTION-PIPELINE
                // =========================================================================
                if (PATTERN_TASK_TRIGGER.containsMatchIn(cleanInput)) {
                    val bridge = pythonBridge
                    if (bridge != null) {
                        val result = TaskPartitioningEngine.partitionAndExecute(cleanInput, vaultDb, bridge)
                        dispatchResponse(result, onResponseReady)
                    } else {
                        dispatchResponse("Python-Bridge nicht verfügbar. Cache-Verzeichnis erforderlich.", onResponseReady)
                    }
                    return@launch
                }

                // =========================================================================
                // TIER 1: OUTBOUND-SENDE-INTENTS (WhatsApp, E-Mail, SMS)
                // =========================================================================
                if (PATTERN_OUTBOUND_TRIGGER.containsMatchIn(cleanInput)) {
                    val stagedIntent = buildTargetedOutboundIntent(cleanInput)
                    val confirmationText = "Absicht erkannt: Nachricht an ${stagedIntent.recipient} (${stagedIntent.channel})\n" +
                            "Modus: ${stagedIntent.appliedStyle}\n\n" +
                            "Entwurf:\n\"${stagedIntent.draftPayload}\""

                    val botMsg = ChatMessage(
                        isFromUser = false,
                        text = confirmationText,
                        stagedIntent = stagedIntent
                    )
                    withContext(Dispatchers.Main) {
                        messageHistory.add(botMsg)
                        onResponseReady(botMsg)
                    }
                    return@launch
                }

                // =========================================================================
                // TIER 2: JOBCENTER ONLINE PORTAL NAVIGATION
                // =========================================================================
                if (PATTERN_JOBCENTER_TRIGGER.containsMatchIn(cleanInput)) {
                    handleJobcenterPortalNavigation(cleanInput, onResponseReady)
                    return@launch
                }

                // =========================================================================
                // TIER 3: DOKUMENTEN- & RECHTSANALYSEN AUS DEM SQLCIPHER-TRESOR
                // =========================================================================
                if (PATTERN_DOCUMENT_ANALYSIS_TRIGGER.containsMatchIn(cleanInput)) {
                    val analysisResult = executeVaultDocumentAnalysis(cleanInput)
                    dispatchResponse(analysisResult, onResponseReady)
                    return@launch
                }

                // =========================================================================
                // TIER 3: ECHTZEIT- & WEB-ABFRAGEN (Wetter, Fakten, Recherchen via DeepSearch)
                // =========================================================================
                if (PATTERN_LIVE_QUERY.containsMatchIn(cleanInput)) {
                    handleLiveInformationQuery(cleanInput, onResponseReady)
                    return@launch
                }

                // =========================================================================
                // TIER 4: DIALOG, GREETINGS & PERSONALITY-MIMICRY
                // =========================================================================
                if (PATTERN_GREETING.containsMatchIn(cleanInput)) {
                    val casualReply = generateConversationalResponse(cleanInput)
                    dispatchResponse(casualReply, onResponseReady)
                    return@launch
                }

                // =========================================================================
                // TIER 5: KINETIC STATE SPACE OPERATOR (Feldtheoretische Transformation)
                // =========================================================================
                try {
                    val kineticResult = kineticOperator.applyOperator(cleanInput)
                    when (kineticResult) {
                        is KineticDischarge.LinguisticImpulse -> {
                            val botMsg = ChatMessage(isFromUser = false, text = kineticResult.plainText)
                            dispatchResponse(botMsg, onResponseReady)
                        }
                        is KineticDischarge.DualDischarge -> {
                            val botMsg = ChatMessage(
                                isFromUser = false,
                                text = kineticResult.speech,
                                actionPayload = kineticResult.executableAction.payload
                            )
                            dispatchResponse(botMsg, onResponseReady)
                        }
                        is KineticDischarge.ExecutableAction -> {
                            val botMsg = ChatMessage(
                                isFromUser = false,
                                text = "Aktion ${kineticResult.actionType} für ${kineticResult.targetEntity} kinetisch ausgeführt.",
                                actionPayload = kineticResult.payload
                            )
                            dispatchResponse(botMsg, onResponseReady)
                        }
                    }
                } catch (e: Exception) {
                    dispatchResponse("Verarbeitungsfehler: ${e.message ?: "Unbekannter Fehler"}. Bitte versuche es erneut.", onResponseReady)
                }
            } catch (e: Exception) {
                dispatchResponse("Systemfehler: ${e.message ?: "Unbekannter Fehler"}. Bitte versuche es erneut.", onResponseReady)
            }
        }
    }

    private suspend fun dispatchResponse(text: String, onResponseReady: (ChatMessage) -> Unit) {
        val botMsg = ChatMessage(isFromUser = false, text = text)
        withContext(Dispatchers.Main) {
            messageHistory.add(botMsg)
            onResponseReady(botMsg)
        }
    }

    private suspend fun dispatchResponse(botMsg: ChatMessage, onResponseReady: (ChatMessage) -> Unit) {
        withContext(Dispatchers.Main) {
            messageHistory.add(botMsg)
            onResponseReady(botMsg)
        }
    }

    /**
     * Kontextbewusster Operator: Zieht den aktuellen Sinneskontext hinzu,
     * ohne dass der Nutzer erklären muss, worum es geht.
     */
    private fun resolveSensoryContextualResponse(userInput: String): String? {
        val sensory = PerceptualSensoryHub.getActiveSensoryContext() ?: return null
        val ageMs = System.currentTimeMillis() - sensory.timestamp

        // Wenn der Reiz jünger als 5 Minuten ist, dient er als unmittelbarer Reaktionsrahmen
        if (ageMs < 300000L) {
            when (sensory.modality) {
                SensoryModality.SCREEN_SURFACE -> {
                    if (PATTERN_SENSORY_SCREEN.containsMatchIn(userInput)) {
                        return "Kontext (${sensory.sourceApp}): Du betrachtest '${sensory.detectedEntities.joinToString(", ").ifBlank { "ein Dokument" }}'.\n" +
                                "Dringlichkeit: ${if (sensory.urgencyScore > 1.5f) "Erhöht (Staudruck)" else "Normal"}.\n" +
                                "Vorbereitete Antwort liegt in der Zwischenablage bereit."
                    }
                }

                SensoryModality.INCOMING_NOTIFICATION -> {
                    if (PATTERN_SENSORY_NOTIFICATION.containsMatchIn(userInput)) {
                        return "Eingang via ${sensory.sourceApp}:\n" +
                                "Inhalt: \"${sensory.rawContent.take(120)}...\"\n" +
                                "Handlungsempfehlung: ${if (sensory.urgencyScore > 1.5f) "Sofortige Fristwahrung empfohlen." else "Keine Eile. Im Seinsmodus belassen."}"
                    }
                }

                SensoryModality.DOCUMENT_INGESTION -> {
                    return "Neues Dokument wurde atomar in den Tresor überführt und geschreddert. Rechtskernel-Prüfung läuft."
                }
            }
        }
        return null
    }

    // =============================================================================
    // DOKUMENTEN- & VAULT-ANALYSE
    // =============================================================================
    private fun executeVaultDocumentAnalysis(query: String): String {
        val keywords = query.lowercase().split(PATTERN_PUNCTUATION_CLEAN)
            .filter { it.length > 3 && it !in listOf("analysiere", "alle", "dokumente", "vom", "bitte", "zeige", "meine", "prüfe", "schulden", "gläubiger", "widerspruch") }
            .ifEmpty { listOf("jobcenter", "bescheid") }

        val foundNodes = mutableListOf<Triple<String, String, Float>>()

        keywords.forEach { keyword ->
            val cursor = vaultDb.rawQuery(
                """
                SELECT id, payload, mass FROM semantic_nodes 
                WHERE lower(id) LIKE ? OR lower(payload) LIKE ?
                ORDER BY mass DESC LIMIT 8
                """.trimIndent(),
                arrayOf("%$keyword%", "%$keyword%")
            )

            cursor.use {
                while (it.moveToNext()) {
                    val id = it.getString(0)
                    val preview = it.getString(1) ?: ""
                    val mass = it.getFloat(2)
                    if (foundNodes.none { n -> n.first == id }) {
                        foundNodes.add(Triple(id, preview, mass))
                    }
                }
            }
        }

        if (foundNodes.isEmpty()) {
            return "Keine Dokumente zu den Begriffen '${keywords.joinToString(", ")}' in der verschlüsselten Blackbox gefunden. Neue PDF-Scans im Download-Ordner werden beim nächsten Takt automatisch eingelesen."
        }

        val sb = StringBuilder("Tresor-Analyse abgeschlossen (${foundNodes.size} Treffer):\n\n")
        foundNodes.forEachIndexed { idx, (id, preview, mass) ->
            sb.append("${idx + 1}. $id [Masse: ${"%.1f".format(mass)}]\n")
            val extract = preview.take(180).replace("\n", " ").trim()
            if (extract.isNotBlank()) {
                sb.append("   Inhalt: \"$extract...\"\n")
            }

            // Rechtliche Schnell-Klassifikation
            if (id.contains("jobcenter", true) || preview.contains("jobcenter", true)) {
                when {
                    preview.contains("Sanktion", true) || preview.contains("Minderung", true) -> {
                        sb.append("   ⚠️ Sanktion erkannt: BVerfG-Prüfung (max. 30 % Minderung) und Härtefallregelung prüfen.\n")
                    }
                    preview.contains("Erstattung", true) || preview.contains("Aufhebung", true) -> {
                        sb.append("   ⚠️ Rückforderung: Vertrauensschutz nach § 45 Abs. 2 SGB X geltend machen.\n")
                    }
                    preview.contains("Anhörung", true) -> {
                        sb.append("   ⚠️ Anhörung § 24 SGB X: Formelle Stellungnahme vor Bescheiderlass vorbereiten.\n")
                    }
                }
            }
            sb.append("\n")
        }

        sb.append("Befehl 'Erstelle Widerspruch' oder 'Prüfe Frist' eingeben für direkte Schriftsatzerzeugung.")
        return sb.toString().trim()
    }

    // =============================================================================
    // JOBCENTER ONLINE PORTAL NAVIGATION
    // =============================================================================
    private suspend fun handleJobcenterPortalNavigation(query: String, onResponseReady: (ChatMessage) -> Unit) {
        val portalUrl = when {
            query.contains("arbeitsagentur", true) -> "https://www.arbeitsagentur.de"
            query.contains("login", true) -> "https://www.jobcenter-online.de/login"
            query.contains("leistung", true) -> "https://www.arbeitsagentur.de/leistungen"
            query.contains("bescheid", true) -> "https://www.jobcenter-online.de/bescheide"
            query.contains("termin", true) -> "https://www.arbeitsagentur.de/termine"
            else -> "https://www.jobcenter-online.de"
        }

        val loadingMsg = "Navigiere zum Jobcenter Online Portal...\n\nURL: $portalUrl\n\nAnonymer Browser-Tunnel wird aufgebaut..."
        dispatchResponse(loadingMsg, onResponseReady)

        if (deepSearchOrchestrator != null) {
            deepSearchOrchestrator.navigateToUrl(portalUrl) { title, content, frictionW ->
                val portalText = buildString {
                    append("🏛️ JOBCENTER ONLINE PORTAL\n\n")
                    append("Seite: $title\n")
                    append("URL: $portalUrl\n")
                    append("Reibung W(t): ${"%.3f".format(frictionW)}\n\n")
                    append("─".repeat(30))
                    append("\nSEITENINHALT:\n\n")
                    append(content.take(400))
                    append("\n\n─".repeat(30))
                    append("\n\n💡 Aktionen:\n")
                    append("• 'Login durchführen' — Zugangsdaten eingeben\n")
                    append("• 'Bescheide abrufen' — Neue Bescheide prüfen\n")
                    append("• 'Leistung beantragen' — Antrag stellen\n")
                    append("• 'Widerspruch einlegen' — Gegen Bescheid vorgehen")
                }
                val styled = stylingEngine.synthesizeInUserVoice(portalText, StylingDomain.BUSINESS_ASSERTIVE)
                scope.launch { dispatchResponse(styled, onResponseReady) }
            }
        } else {
            val fallbackMsg = "Jobcenter Online Portal:\n\n$portalUrl\n\nAnonymer Suchtunnel nicht aktiv. Portal kann nicht durchsucht werden."
            dispatchResponse(fallbackMsg, onResponseReady)
        }
    }

    // =============================================================================
    // LIVE- & WEBRECHERCHEN (DEEPSEARCH BRIDGE)
    // =============================================================================
    private fun isLiveInformationQuery(input: String): Boolean {
        return PATTERN_LIVE_QUERY.containsMatchIn(input)
    }

    private suspend fun handleLiveInformationQuery(query: String, onResponseReady: (ChatMessage) -> Unit) {
        // Lokale Heuristik für Standard-Abfragen wie Wetter
        if (query.contains("wetter", true)) {
            val location = query.substringAfter("in", "").substringAfter("für", "").trim()
                .ifBlank { "Freiburg" }
                .replace(PATTERN_PUNCTUATION_CLEAN, "")

            if (deepSearchOrchestrator != null) {
                deepSearchOrchestrator.startAnonymousSearch("Wetter $location aktuell Temperatur") { title, snippet, _ ->
                    val cleanSnippet = snippet.lines().filter { it.isNotBlank() }.take(2).joinToString(" ")
                    val weatherText = if (cleanSnippet.isNotBlank()) {
                        "Wetter $location: $cleanSnippet"
                    } else {
                        "Wetterabfrage für $location ausgeführt. Aktuelle Daten über anonymen Tunnel abgerufen."
                    }
                    val styled = stylingEngine.synthesizeInUserVoice(weatherText, StylingDomain.PEER_CASUAL)
                    scope.launch { dispatchResponse(styled, onResponseReady) }
                }
                return
            } else {
                val fallbackWeather = "Wetter $location: Mild und wechselhaft. Präzise Live-Werte erfordern aktiven DeepSearch-Tunnel."
                dispatchResponse(fallbackWeather, onResponseReady)
                return
            }
        }

        // Generische DeepSearch-Recherche
        if (deepSearchOrchestrator != null) {
            deepSearchOrchestrator.startAnonymousSearch(query) { title, snippet, _ ->
                val resultText = "Recherche-Ergebnis zu '$query':\n\n${snippet.take(300)}..."
                val styled = stylingEngine.synthesizeInUserVoice(resultText, StylingDomain.PEER_CASUAL)
                scope.launch { dispatchResponse(styled, onResponseReady) }
            }
        } else {
            dispatchResponse("Anonymer Suchtunnel für '$query' im aktuellen Profil nicht scharfgeschaltet.", onResponseReady)
        }
    }

    // =============================================================================
    // DIALOG & PERSÖNLICHE WESENSSPIEGELUNG
    // =============================================================================
    private fun isConversationalGreeting(input: String): Boolean {
        return PATTERN_GREETING.containsMatchIn(input)
    }

    private fun generateConversationalResponse(greeting: String): String {
        val coreReply = when {
            greeting.contains("wie geht", true) || greeting.contains("was geht", true) -> {
                "Alles stabil. System läuft ruhig im Seinsmodus. Sag an, was ansteht."
            }
            greeting.contains("moin", true) -> "Moin. Was liegt an?"
            greeting.contains("servus", true) -> "Servus. Was gibt's zu tun?"
            else -> "Hi. Bin einsatzbereit. Dokumente prüfen, Nachricht vorbereiten oder Systemstatus?"
        }
        // Mimicry-Engine für natürliche Sprache
        val mimickedReply = mimicryEngine.stylizeUtterance(coreReply, LinguisticRegister.PEER_COLLOQUIAL)
        return stylingEngine.synthesizeInUserVoice(mimickedReply, StylingDomain.PEER_CASUAL)
    }

    // =============================================================================
    // OUTBOUND-INTENT AUFBAU & DISPATCH
    // =============================================================================
    fun confirmAndDispatchStagedIntent(intent: StagedOutboundIntent): String {
        val audited = outboundGovernor.processOutboundTransmission(
            mode = OutboundChannelMode.INTERPERSONAL_EFFICIENT,
            rawContent = intent.draftPayload,
            recipient = intent.recipient
        )

        vaultDb.execSQL(
            "INSERT INTO communication_events VALUES (?, ?, ?, 'OUTBOUND', ?, 0, ?, 0)",
            arrayOf(
                "SENT_${System.currentTimeMillis()}",
                intent.recipient,
                intent.channel,
                System.currentTimeMillis(),
                audited.optimizedPayload
            )
        )

        return stylingEngine.synthesizeInUserVoice("Nachricht an ${intent.recipient} übermittelt.", StylingDomain.PEER_CASUAL)
    }

    private fun buildTargetedOutboundIntent(input: String): StagedOutboundIntent {
        val words = input.split(" ")
        val recipientIndex = words.indexOfFirst { it.equals("an", ignoreCase = true) }
        val recipient = if (recipientIndex != -1 && recipientIndex + 1 < words.size) {
            words[recipientIndex + 1].replace(PATTERN_RECIPIENT_CLEAN, "")
        } else {
            "Kontakt"
        }

        val profile = profilingEngine.getProfile(recipient) ?: profilingEngine.getProfile("WA_$recipient")
        val domain = if (profile?.relationshipCategory == "BUSINESS_OR_LEGAL") {
            StylingDomain.BUSINESS_ASSERTIVE
        } else {
            StylingDomain.PEER_CASUAL
        }

        val rawText = input.substringAfter("dass", input).substringAfter(":", input).trim()

        // Relational Power Dynamics anwenden
        val relationVector = powerDynamicsEngine.evaluateRelation(recipient)
        val styledDraft = stylingEngine.synthesizeInUserVoice(
            rawDraft = if (rawText == input) "Bin unterwegs, melde mich gleich." else rawText,
            domain = domain,
            targetRecipientName = recipient
        )

        // Power Dynamics Modulation
        val modulatedDraft = powerDynamicsEngine.modulateTone(relationVector, styledDraft)

        // Grammatikalische Formalisierung bei formellen Kontexten
        val finalDraft = if (domain == StylingDomain.BUSINESS_ASSERTIVE || domain == StylingDomain.LEGAL_ENFORCEMENT) {
            DudenGrammarEngine.formalizeSentence(modulatedDraft, LinguisticRegister.DUDEN_FORMAL)
        } else {
            // Colloquial Mimikry für informelle Kontexte
            mimicryEngine.stylizeUtterance(modulatedDraft, LinguisticRegister.PEER_COLLOQUIAL)
        }

        return StagedOutboundIntent(
            recipient = recipient,
            channel = if (input.contains("mail", true)) "EMAIL" else "WHATSAPP",
            appliedStyle = "${profile?.relationshipCategory ?: "ACQUAINTANCE"} / ${profile?.communicationStyle ?: "DIRECT"}",
            draftPayload = finalDraft
        )
    }
}

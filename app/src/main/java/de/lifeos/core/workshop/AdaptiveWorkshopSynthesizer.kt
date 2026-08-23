package de.lifeos.core.workshop

import android.content.Context
import android.view.WindowManager
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import de.lifeos.core.field.AttractorNode
import de.lifeos.core.field.DeterministicFieldEngine
import de.lifeos.core.field.PhaseVector
import de.lifeos.core.runtime.BuiltInToolEngine
import de.lifeos.core.runtime.DexTemplateAssembler
import de.lifeos.core.runtime.DynamicPluginModule
import de.lifeos.core.runtime.DexHotSwapEngine
import de.lifeos.core.sentinel.ProactiveSentinelEngine
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlin.math.*

/**
 * ADAPTIVE WORKSHOP SYNTHESIZER — Dynamische Werkzeug-Synthese & Kontext-UI-Morphing
 *
 * Befähigt das System, neue Funktionen zur Laufzeit ohne Neustart autonom zu generieren:
 * - On-the-fly Bytecode-Generierung für ephemere Werkzeuge
 * - Selbstmorphierende Interfaces basierend auf aktuellem Staudruck ρ(t)
 * - Kontextuelle Floating-Widgets als 2D-Projektion aktiver Attraktoren
 * - HUD-Overlay-Generierung für 1-Tap-Aktionen
 *
 * Vektoren:
 * - [EXP-SYNTH] Selbsterweiternde Werkzeugsynthese: neue Module ohne Disk-Trace
 * - [EXP-AUTO] Autonome Selbststeuerung: UI passt sich Staudruck ρ automatisch an
 * - [EXP-SENSE] Wahrnehmungserweiterung: kontextuelle Widgets projizieren Feldzustand
 */
class AdaptiveWorkshopSynthesizer(
    private val context: Context,
    private val fieldEngine: DeterministicFieldEngine,
    private val sentinelEngine: ProactiveSentinelEngine,
    private val vaultDb: net.sqlcipher.database.SQLiteDatabase? = null,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
) {
    private val synthesizedTools = mutableMapOf<String, SynthesizedTool>()
    private val _activeHudWidgets = MutableStateFlow<List<HudWidget>>(emptyList())
    val activeHudWidgets: StateFlow<List<HudWidget>> = _activeHudWidgets.asStateFlow()

    private val _uiMorphState = MutableStateFlow(UiMorphState.Default)
    val uiMorphState: StateFlow<UiMorphState> = _uiMorphState.asStateFlow()

    // Built-in tool engine for verified, pre-compiled implementations
    private val builtInEngine = vaultDb?.let { BuiltInToolEngine(it) }

    data class SynthesizedTool(
        val id: String,
        val name: String,
        val toolType: ToolType,
        val bytecode: ByteArray? = null,
        val params: Map<String, String>,
        val createdAt: Long = System.currentTimeMillis()
    )

    data class HudWidget(
        val id: String,
        val type: WidgetType,
        val position: WidgetPosition,
        val size: WidgetSize,
        val priority: Float,
        val payload: Map<String, String>,
        val color: Color
    )

    enum class WidgetType {
        SENTINEL_ALERT,
        FIELD_METRIC,
        QUICK_ACTION,
        FINANCIAL_RUNWAY,
        LEGAL_DEADLINE
    }

    data class WidgetPosition(val x: Float, val y: Float) // 0.0–1.0 normalized
    data class WidgetSize(val width: Float, val height: Float) // 0.0–1.0 normalized

    enum class UiMorphState {
        Default,           // Standard 5-Tab Dashboard
        Seinsmodus,        // Minimal: 1 FPS, nur essentielle Widgets
        Homoeostase,       // Balanced: 60 FPS, Standard-Widgets
        Kritisch,          // 120 FPS, alle HUDs, maximale Informationsdichte
        LegalFocus,        // Rechtskernel-Fokus: Briefe, Fristen, DSGVO
        FinancialFocus     // Finanz-Fokus: Runway, Zinsen, Forderungen
    }

    init {
        startAdaptiveMorphing()
        startHudProjection()
    }

    // =========================================================================
    // ON-THE-FLY TOOL SYNTHESIS
    // =========================================================================

    /**
     * Synthesizes a new tool from a natural language requirement.
     * Uses verified built-in implementations by default, falls back to
     * DEX hot-swap for custom modules. Zero disk trace: all generation
     * happens in RAM.
     */
    fun synthesizeToolFromRequirement(requirement: String): SynthesizedTool {
        val toolType = classifyToolRequirement(requirement)
        val toolId = "TOOL_${toolType.name}_${System.currentTimeMillis()}"

        val entryClass = "de.lifeos.generated.${toolType.name}Module"

        // Try built-in engine first (production-ready, verified implementations)
        val builtInModule = builtInEngine?.createModule(toolType)
        val injected = if (builtInModule != null) {
            // Register built-in module in DexHotSwapEngine for unified access
            DexHotSwapEngine.registerModule(toolId, builtInModule)
            true
        } else {
            // Fallback: generate minimal DEX bytecode for the tool
            val dexBytes = generateToolDex(toolType, entryClass)
            DexHotSwapEngine.injectDexBytecode(toolId, dexBytes, entryClass)
        }

        val tool = SynthesizedTool(
            id = toolId,
            name = toolType.name.replace("_", " ").lowercase().replaceFirstChar { it.uppercase() },
            toolType = toolType,
            bytecode = null, // Built-in tools have no DEX bytes
            params = mapOf("requirement" to requirement, "injected" to injected.toString())
        )

        synthesizedTools[toolId] = tool
        return tool
    }

    // SEV-2 Fix: Precompiled regex patterns for tool classification and action synthesis
    private companion object {
        val PATTERN_INTEREST = Regex("(zins|verzug|interest|calc)", RegexOption.IGNORE_CASE)
        val PATTERN_BESCHEID = Regex("(bescheid|extract|auszug)", RegexOption.IGNORE_CASE)
        val PATTERN_DSGVO = Regex("(dsgvo|request|auskunft)", RegexOption.IGNORE_CASE)
        val PATTERN_CONTRACT = Regex("(vertrag|contract|analyse|agb|klausel)", RegexOption.IGNORE_CASE)
        val PATTERN_TAX = Regex("(steuer|tax|deduct|absetz)", RegexOption.IGNORE_CASE)
        val PATTERN_CSV = Regex("(csv|extract|daten)", RegexOption.IGNORE_CASE)
        val PATTERN_RUNWAY = Regex("(runway|cash|finanzen|laufzeit)", RegexOption.IGNORE_CASE)
        val PATTERN_FRIST = Regex("(frist|deadline|termin)", RegexOption.IGNORE_CASE)
        val PATTERN_ZAHLUNG = Regex("(zahlung|rechnung|forderung)", RegexOption.IGNORE_CASE)
        val PATTERN_JOBCENTER = Regex("(jobcenter|bescheid|sanktion)", RegexOption.IGNORE_CASE)
    }

    private fun classifyToolRequirement(requirement: String): ToolType {
        val lower = requirement.lowercase()
        return when {
            PATTERN_INTEREST.containsMatchIn(lower) -> ToolType.INTEREST_CALCULATOR
            PATTERN_BESCHEID.containsMatchIn(lower) -> ToolType.BESHEID_EXTRACTOR
            PATTERN_DSGVO.containsMatchIn(lower) -> ToolType.DSGVO_REQUEST_GENERATOR
            PATTERN_CONTRACT.containsMatchIn(lower) -> ToolType.CONTRACT_ANALYZER
            PATTERN_TAX.containsMatchIn(lower) -> ToolType.TAX_DEDUCTOR
            PATTERN_CSV.containsMatchIn(lower) -> ToolType.CSV_EXTRACTOR
            PATTERN_RUNWAY.containsMatchIn(lower) -> ToolType.FINANCIAL_RUNWAY_CALCULATOR
            else -> ToolType.CONTRACT_ANALYZER
        }
    }

    // =========================================================================
    // DEX BYTECODE GENERATION (Template-based assembly for custom tools)
    // =========================================================================

    /**
     * Generates minimal valid DEX bytecode for a tool module.
     * Uses template-based assembly with pre-verified class structure.
     */
    private fun generateToolDex(toolType: ToolType, entryClass: String): ByteArray {
        return DexTemplateAssembler.assembleModuleDex(entryClass)
    }

    // =========================================================================
    // ADAPTIVE UI MORPHING
    // =========================================================================

    private fun startAdaptiveMorphing() {
        scope.launch {
            while (isActive) {
                val rho = fieldEngine.currentRho
                val morphState = determineMorphState(rho)
                _uiMorphState.value = morphState
                delay(2000L)
            }
        }
    }

    private fun determineMorphState(rho: Float): UiMorphState {
        return when {
            rho <= 1.0f -> UiMorphState.Seinsmodus
            rho > 3.0f -> UiMorphState.Kritisch
            else -> {
                // Check sentinel interventions for domain focus
                val topIntervention = sentinelEngine.getTopPriorityAction()
                when {
                    topIntervention?.category == ProactiveSentinelEngine.InterventionCategory.LEGAL_DEADLINE ||
                    topIntervention?.category == ProactiveSentinelEngine.InterventionCategory.DSGVO_COMPLIANCE ->
                        UiMorphState.LegalFocus
                    topIntervention?.category == ProactiveSentinelEngine.InterventionCategory.FINANCIAL_RUNWAY ||
                    topIntervention?.category == ProactiveSentinelEngine.InterventionCategory.DEBT_ESCALATION ->
                        UiMorphState.FinancialFocus
                    else -> UiMorphState.Homoeostase
                }
            }
        }
    }

    // =========================================================================
    // HUD PROJECTION (2D-Attraktor-Projektion)
    // =========================================================================

    private fun startHudProjection() {
        scope.launch {
            while (isActive) {
                projectActiveAttractorsToHud()
                delay(1000L)
            }
        }
    }

    private fun projectActiveAttractorsToHud() {
        val nodes = fieldEngine.getActiveNodes()
        val rho = fieldEngine.currentRho

        val widgets = nodes.take(6).mapIndexed { index, node ->
            val angle = (index.toFloat() / nodes.size) * 2 * PI.toFloat()
            val radius = 0.3f + node.mass * 0.1f

            HudWidget(
                id = "HUD_${node.id}",
                type = classifyNodeToWidgetType(node),
                position = WidgetPosition(
                    x = (0.5f + cos(angle) * radius).coerceIn(0.1f, 0.9f),
                    y = (0.5f + sin(angle) * radius).coerceIn(0.1f, 0.9f)
                ),
                size = WidgetSize(
                    width = (0.15f + node.mass * 0.05f).coerceIn(0.1f, 0.3f),
                    height = (0.08f + node.mass * 0.03f).coerceIn(0.05f, 0.15f)
                ),
                priority = node.mass,
                payload = mapOf("node_id" to node.id, "mass" to node.mass.toString()),
                color = when {
                    node.mass > 2.0f -> Color(0xFFFF1744) // Red: high priority
                    node.mass > 1.0f -> Color(0xFFFFD600) // Yellow: medium
                    else -> Color(0xFF00E676)            // Green: low
                }
            )
        }

        _activeHudWidgets.value = widgets
    }

    private fun classifyNodeToWidgetType(node: AttractorNode): WidgetType {
        val id = node.id.lowercase()
        return when {
            id.contains("legal") || id.contains("frist") || id.contains("widerspruch") -> WidgetType.LEGAL_DEADLINE
            id.contains("sentinel") && node.mass > 2.0f -> WidgetType.SENTINEL_ALERT
            id.contains("deal") || id.contains("finanz") -> WidgetType.FINANCIAL_RUNWAY
            id.contains("feld") || id.contains("rho") -> WidgetType.FIELD_METRIC
            else -> WidgetType.QUICK_ACTION
        }
    }

    // =========================================================================
    // CONTEXTUAL ACTION SYNTHESIS
    // =========================================================================

    /**
     * Synthesizes a 1-tap action button configuration based on current field state.
     */
    fun synthesizeOneTapAction(node: AttractorNode): OneTapActionConfig {
        val actionType = when {
            node.payload.contains(PATTERN_FRIST) ->
                OneTapActionType.GENERATE_LEGAL_LETTER
            node.payload.contains(PATTERN_ZAHLUNG) ->
                OneTapActionType.RELEASE_PAYMENT
            node.payload.contains(PATTERN_DSGVO) ->
                OneTapActionType.SEND_DSGVO_REQUEST
            node.payload.contains(PATTERN_JOBCENTER) ->
                OneTapActionType.OPEN_PORTAL
            else -> OneTapActionType.GENERIC_ACTION
        }

        return OneTapActionConfig(
            actionType = actionType,
            label = generateActionLabel(actionType, node),
            color = when (node.mass) {
                in 0.0f..1.0f -> Color(0xFF00E676)
                in 1.0f..2.5f -> Color(0xFFFFD600)
                else -> Color(0xFFFF1744)
            },
            payload = mapOf("node_id" to node.id, "mass" to node.mass.toString())
        )
    }

    private fun generateActionLabel(type: OneTapActionType, node: AttractorNode): String {
        return when (type) {
            OneTapActionType.GENERATE_LEGAL_LETTER -> "Schriftsatz generieren"
            OneTapActionType.RELEASE_PAYMENT -> "Zahlung freigeben"
            OneTapActionType.SEND_DSGVO_REQUEST -> "DSGVO-Auskunft senden"
            OneTapActionType.OPEN_PORTAL -> "Portal öffnen"
            OneTapActionType.GENERIC_ACTION -> "Aktion ausführen"
        }
    }

    enum class OneTapActionType {
        GENERATE_LEGAL_LETTER,
        RELEASE_PAYMENT,
        SEND_DSGVO_REQUEST,
        OPEN_PORTAL,
        GENERIC_ACTION
    }

    data class OneTapActionConfig(
        val actionType: OneTapActionType,
        val label: String,
        val color: Color,
        val payload: Map<String, String>
    )

    fun getSynthesizedTools(): List<SynthesizedTool> = synthesizedTools.values.toList()

    fun shutdown() {
        scope.coroutineContext.cancelChildren()
    }
}

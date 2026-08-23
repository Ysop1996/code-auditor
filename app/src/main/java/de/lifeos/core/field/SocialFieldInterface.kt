package de.lifeos.core.field

import kotlin.math.*

/**
 * SOCIAL FIELD INTERFACE — Soziale Feld-Schnittstelle
 *
 * Die SocialFieldInterface modelliert soziale Interaktionen als Feld-Dynamik
 * zwischen Agenten. Sie berechnet Einflüsse, Beziehungsstärken und
 * kollektive Zustände.
 *
 * Konzepte:
 * 1. Agent: Knoten im sozialen Feld mit eigenem Zustandsvektor
 * 2. Relationship: Kante zwischen Agenten mit Stärke und Richtung
 * 3. SocialField: Gesamtes soziales Feld als Adjazenzmatrix
 * 4. Influence: Einfluss eines Agenten auf einen anderen
 * 5. CollectiveState: Kollektiver Zustand des sozialen Feldes
 *
 * Vektoren:
 * - [EXP-FORCE] Soziale Kohärenz: Maximierung des kollektiven Nutzens
 * - [EXP-AUTO] Autopoietische Sozialregulation: Selbstorganisation
 * - [EXP-SPEED] O(1) Lookup, O(n²) Feld-Update
 */
object SocialFieldInterface {

    // =========================================================================
    // SOCIAL PARAMETERS
    // =========================================================================

    /** Standard-Einflussstärke */
    const val DEFAULT_INFLUENCE: Float = 0.5f

    /** Maximale Beziehungsstärke */
    const val MAX_RELATIONSHIP_STRENGTH: Float = 1.0f

    /** Minimale Beziehungsstärke */
    const val MIN_RELATIONSHIP_STRENGTH: Float = 0.0f

    /** Sozialer Kohärenz-Schwellwert */
    const val COHERENCE_THRESHOLD: Float = 0.6f

    /** Maximale Anzahl Agenten */
    const val MAX_AGENTS: Int = 50

    // =========================================================================
    // AGENT
    // =========================================================================

    /**
     * Sozialer Agent.
     *
     * @param id Eindeutige ID
     * @param name Name
     * @param cognitiveState Kognitiver Zustand I(t)
     * @param load Last W(t)
     * @param sovereignty Souveränität S_o(t)
     * @param influenceRadius Einflussradius
     * @param tags Tags für Kategorisierung
     */
    data class Agent(
        val id: String,
        val name: String,
        val cognitiveState: CognitiveStateVector,
        val load: Float,
        val sovereignty: Float,
        val influenceRadius: Float = 1.0f,
        val tags: List<String> = emptyList()
    ) {
        /**
         * Berechnet den sozialen Einfluss dieses Agenten.
         */
        fun computeInfluence(): Float {
            return (load * 0.3f + sovereignty * 0.5f + influenceRadius * 0.2f).coerceIn(0.0f, 1.0f)
        }

        override fun toString(): String {
            return "Agent[$id] $name (W=${"%.2f".format(load)}, S_o=${"%.2f".format(sovereignty)})"
        }
    }

    // =========================================================================
    // RELATIONSHIP
    // =========================================================================

    /**
     * Beziehung zwischen zwei Agenten.
     *
     * @param sourceId Quell-Agent-ID
     * @param targetId Ziel-Agent-ID
     * @param strength Beziehungsstärke [0, 1]
     * @param type Beziehungstyp
     * @param trust Vertrauen [0, 1]
     * @param reciprocity Reziprozität [0, 1]
     */
    data class Relationship(
        val sourceId: String,
        val targetId: String,
        var strength: Float,
        val type: RelationshipType,
        var trust: Float = 0.5f,
        var reciprocity: Float = 0.5f
    ) {
        init {
            strength = strength.coerceIn(MIN_RELATIONSHIP_STRENGTH, MAX_RELATIONSHIP_STRENGTH)
        }

        /**
         * Aktualisiert die Beziehungsstärke.
         */
        fun updateStrength(delta: Float) {
            strength = (strength + delta).coerceIn(MIN_RELATIONSHIP_STRENGTH, MAX_RELATIONSHIP_STRENGTH)
        }

        /**
         * Prüft, ob die Beziehung stark ist.
         */
        fun isStrong(): Boolean = strength >= 0.7f

        /**
         * Prüft, ob die Beziehung schwach ist.
         */
        fun isWeak(): Boolean = strength <= 0.3f

        override fun toString(): String {
            return "Relationship[$sourceId → $targetId] strength=${"%.2f".format(strength)}, trust=${"%.2f".format(trust)}"
        }
    }

    /**
     * Beziehungstyp.
     */
    enum class RelationshipType {
        /** Familie */
        FAMILY,

        /** Freundschaft */
        FRIENDSHIP,

        /** Beruflich */
        PROFESSIONAL,

        /** Romantisch */
        ROMANTIC,

        /** Neutral */
        NEUTRAL,

        /** Konfliktreich */
        CONFLICTED
    }

    // =========================================================================
    // SOCIAL FIELD
    // =========================================================================

    /** Agenten-Speicher */
    private val agents = mutableMapOf<String, Agent>()

    /** Beziehungs-Speicher */
    private val relationships = mutableListOf<Relationship>()

    /**
     * Registriert einen Agenten im sozialen Feld.
     *
     * @param agent Agent
     * @return Erfolg
     */
    fun registerAgent(agent: Agent): Boolean {
        if (agents.size >= MAX_AGENTS) return false
        agents[agent.id] = agent
        return true
    }

    /**
     * Entfernt einen Agenten aus dem sozialen Feld.
     *
     * @param agentId Agenten-ID
     */
    fun unregisterAgent(agentId: String) {
        agents.remove(agentId)
        relationships.removeAll { it.sourceId == agentId || it.targetId == agentId }
    }

    /**
     * Erstellt eine Beziehung zwischen zwei Agenten.
     *
     * @param sourceId Quell-Agent-ID
     * @param targetId Ziel-Agent-ID
     * @param strength Anfangsstärke
     * @param type Beziehungstyp
     * @return Erstellte Beziehung
     */
    fun createRelationship(
        sourceId: String,
        targetId: String,
        strength: Float = DEFAULT_INFLUENCE,
        type: RelationshipType = RelationshipType.NEUTRAL
    ): Relationship? {
        if (!agents.containsKey(sourceId) || !agents.containsKey(targetId)) return null
        if (sourceId == targetId) return null

        val relationship = Relationship(sourceId, targetId, strength, type)
        relationships.add(relationship)
        return relationship
    }

    /**
     * Aktualisiert eine bestehende Beziehung.
     *
     * @param sourceId Quell-Agent-ID
     * @param targetId Ziel-Agent-ID
     * @param delta Stärkeänderung
     */
    fun updateRelationship(sourceId: String, targetId: String, delta: Float) {
        val relationship = relationships.find { it.sourceId == sourceId && it.targetId == targetId }
        relationship?.updateStrength(delta)
    }

    /**
     * Gibt alle Beziehungen eines Agenten zurück.
     */
    fun getRelationships(agentId: String): List<Relationship> {
        return relationships.filter { it.sourceId == agentId || it.targetId == agentId }
    }

    /**
     * Gibt einen Agenten zurück.
     */
    fun getAgent(agentId: String): Agent? = agents[agentId]

    /**
     * Gibt alle Agenten zurück.
     */
    fun getAllAgents(): List<Agent> = agents.values.toList()

    /**
     * Gibt alle Beziehungen zurück.
     */
    fun getAllRelationships(): List<Relationship> = relationships.toList()

    // =========================================================================
    // INFLUENCE MODEL
    // =========================================================================

    /**
     * Berechnet den Einfluss eines Agenten auf einen anderen.
     *
     * @param sourceId Quell-Agent-ID
     * @param targetId Ziel-Agent-ID
     * @return Einfluss [0, 1]
     */
    fun computeInfluence(sourceId: String, targetId: String): Float {
        val source = agents[sourceId] ?: return 0f
        val relationship = relationships.find { it.sourceId == sourceId && it.targetId == targetId }

        val baseInfluence = source.computeInfluence()
        val relationshipFactor = relationship?.strength ?: 0f

        return (baseInfluence * 0.6f + relationshipFactor * 0.4f).coerceIn(0.0f, 1.0f)
    }

    /**
     * Berechnet den kollektiven Einfluss auf einen Agenten.
     *
     * @param targetId Ziel-Agent-ID
     * @return Kollektiver Einfluss [0, 1]
     */
    fun computeCollectiveInfluence(targetId: String): Float {
        val target = agents[targetId] ?: return 0f

        var totalInfluence = 0f
        var count = 0

        for (agent in agents.values) {
            if (agent.id != targetId) {
                totalInfluence += computeInfluence(agent.id, targetId)
                count++
            }
        }

        return if (count > 0) (totalInfluence / count).coerceIn(0.0f, 1.0f) else 0f
    }

    // =========================================================================
    // COLLECTIVE STATE
    // =========================================================================

    /**
     * Berechnet den kollektiven Zustand des sozialen Feldes.
     *
     * @return Kollektiver Zustand
     */
    fun computeCollectiveState(): CollectiveState {
        if (agents.isEmpty()) {
            return CollectiveState(0f, 0f, 0f, 0f, 0, 0)
        }

        val avgLoad = agents.values.map { it.load }.average().toFloat()
        val avgSovereignty = agents.values.map { it.sovereignty }.average().toFloat()
        val avgInfluence = agents.values.map { it.computeInfluence() }.average().toFloat()
        val coherence = computeSocialCoherence()

        return CollectiveState(
            avgLoad = avgLoad,
            avgSovereignty = avgSovereignty,
            avgInfluence = avgInfluence,
            coherence = coherence,
            agentCount = agents.size,
            relationshipCount = relationships.size
        )
    }

    /**
     * Berechnet die soziale Kohärenz.
     */
    private fun computeSocialCoherence(): Float {
        if (relationships.isEmpty()) return 1.0f

        val avgStrength = relationships.map { it.strength }.average().toFloat()
        val avgTrust = relationships.map { it.trust }.average().toFloat()

        return ((avgStrength + avgTrust) / 2.0f).coerceIn(0.0f, 1.0f)
    }

    /**
     * Kollektiver Zustand des sozialen Feldes.
     *
     * @param avgLoad Durchschnittliche Last
     * @param avgSovereignty Durchschnittliche Souveränität
     * @param avgInfluence Durchschnittlicher Einfluss
     * @param coherence Soziale Kohärenz
     * @param agentCount Anzahl Agenten
     * @param relationshipCount Anzahl Beziehungen
     */
    data class CollectiveState(
        val avgLoad: Float,
        val avgSovereignty: Float,
        val avgInfluence: Float,
        val coherence: Float,
        val agentCount: Int,
        val relationshipCount: Int
    ) {
        val isCoherent: Boolean
            get() = coherence >= COHERENCE_THRESHOLD

        override fun toString(): String {
            return buildString {
                appendLine("=== COLLECTIVE STATE ===")
                appendLine("Agents: $agentCount")
                appendLine("Relationships: $relationshipCount")
                appendLine("Avg Load: ${"%.2f".format(avgLoad)}")
                appendLine("Avg Sovereignty: ${"%.2f".format(avgSovereignty)}")
                appendLine("Avg Influence: ${"%.2f".format(avgInfluence)}")
                appendLine("Coherence: ${"%.2f".format(coherence)}")
                appendLine("Coherent: $isCoherent")
            }
        }
    }

    // =========================================================================
    // SOCIAL DYNAMICS
    // =========================================================================

    /**
     * Simuliert einen Zeitschritt der sozialen Dynamik.
     */
    fun simulateStep(): SocialDynamicsResult {
        if (agents.isEmpty()) {
            return SocialDynamicsResult(emptyMap(), emptyList())
        }

        val stateChanges = mutableMapOf<String, AgentStateChange>()
        val events = mutableListOf<SocialEvent>()

        for (agent in agents.values) {
            val collectiveInfluence = computeCollectiveInfluence(agent.id)
            val loadChange = collectiveInfluence * 0.1f
            val sovereigntyChange = -loadChange * 0.5f

            stateChanges[agent.id] = AgentStateChange(
                agentId = agent.id,
                loadDelta = loadChange,
                sovereigntyDelta = sovereigntyChange,
                timestamp = System.currentTimeMillis()
            )

            if (abs(loadChange) > 0.1f) {
                events.add(
                    SocialEvent(
                        type = if (loadChange > 0) SocialEventType.LOAD_INCREASE else SocialEventType.LOAD_DECREASE,
                        agentId = agent.id,
                        magnitude = abs(loadChange),
                        timestamp = System.currentTimeMillis()
                    )
                )
            }
        }

        return SocialDynamicsResult(stateChanges, events)
    }

    /**
     * Agent-Zustandsänderung.
     *
     * @param agentId Agenten-ID
     * @param loadDelta Last-Änderung
     * @param sovereigntyDelta Souveränitäts-Änderung
     * @param timestamp Zeitstempel
     */
    data class AgentStateChange(
        val agentId: String,
        val loadDelta: Float,
        val sovereigntyDelta: Float,
        val timestamp: Long
    )

    /**
     * Soziales Ereignis.
     *
     * @param type Ereignistyp
     * @param agentId Agenten-ID
     * @param magnitude Stärke
     * @param timestamp Zeitstempel
     */
    data class SocialEvent(
        val type: SocialEventType,
        val agentId: String,
        val magnitude: Float,
        val timestamp: Long
    ) {
        override fun toString(): String {
            return "SocialEvent[$type] Agent=$agentId magnitude=${"%.2f".format(magnitude)}"
        }
    }

    /**
     * Soziales Ereignis-Typ.
     */
    enum class SocialEventType {
        /** Last-Zunahme */
        LOAD_INCREASE,

        /** Last-Abnahme */
        LOAD_DECREASE,

        /** Beziehungs-Upgrade */
        RELATIONSHIP_UPGRADE,

        /** Beziehungs-Downgrade */
        RELATIONSHIP_DOWNGRADE,

        /** Neue Verbindung */
        NEW_CONNECTION,

        /** Getrennte Verbindung */
        DISCONNECTION
    }

    /**
     * Ergebnis einer Sozialdynamik-Simulation.
     *
     * @param stateChanges Zustandsänderungen
     * @param events Ereignisse
     */
    data class SocialDynamicsResult(
        val stateChanges: Map<String, AgentStateChange>,
        val events: List<SocialEvent>
    )

    // =========================================================================
    // SOCIAL NETWORK ANALYSIS
    // =========================================================================

    /**
     * Analysiert das soziale Netzwerk.
     *
     * @return Netzwerk-Analyse
     */
    fun analyzeNetwork(): NetworkAnalysis {
        if (agents.isEmpty()) {
            return NetworkAnalysis(0, 0, 0f, 0f, 0f, emptyList())
        }

        val density = if (agents.size > 1) {
            (2.0f * relationships.size / (agents.size * (agents.size - 1))).coerceIn(0.0f, 1.0f)
        } else 0f

        val avgDegree = if (agents.isNotEmpty()) {
            (2.0f * relationships.size / agents.size).coerceIn(0.0f, 1.0f)
        } else 0f

        val clustering = computeClusteringCoefficient()

        val centralAgents = agents.values
            .map { agent -> agent.id to computeCollectiveInfluence(agent.id) }
            .sortedByDescending { it.second }
            .take(5)

        return NetworkAnalysis(
            agentCount = agents.size,
            relationshipCount = relationships.size,
            density = density,
            avgDegree = avgDegree,
            clusteringCoefficient = clustering,
            centralAgents = centralAgents
        )
    }

    /**
     * Berechnet den Clustering-Koeffizienten.
     */
    private fun computeClusteringCoefficient(): Float {
        if (agents.size < 3) return 0f

        // Build adjacency lookup for O(1) edge existence checks
        val adjacency = mutableSetOf<Pair<String, String>>()
        for (rel in relationships) {
            adjacency.add(rel.sourceId to rel.targetId)
            adjacency.add(rel.targetId to rel.sourceId)
        }

        var totalClustering = 0f
        var count = 0

        for (agent in agents.values) {
            val neighbors = mutableSetOf<String>()
            for (rel in relationships) {
                if (rel.sourceId == agent.id) neighbors.add(rel.targetId)
                else if (rel.targetId == agent.id) neighbors.add(rel.sourceId)
            }

            if (neighbors.size < 2) continue

            var triangles = 0
            var possibleTriangles = 0
            val neighborList = neighbors.toList()

            for (i in neighborList.indices) {
                for (j in i + 1 until neighborList.size) {
                    possibleTriangles++
                    val neighborA = neighborList[i]
                    val neighborB = neighborList[j]
                    if (adjacency.contains(neighborA to neighborB) || adjacency.contains(neighborB to neighborA)) {
                        triangles++
                    }
                }
            }

            if (possibleTriangles > 0) {
                totalClustering += triangles.toFloat() / possibleTriangles
                count++
            }
        }

        return if (count > 0) (totalClustering / count).coerceIn(0.0f, 1.0f) else 0f
    }

    /**
     * Netzwerk-Analyse.
     *
     * @param agentCount Anzahl Agenten
     * @param relationshipCount Anzahl Beziehungen
     * @param density Netzwerk-Dichte
     * @param avgDegree Durchschnittlicher Grad
     * @param clusteringCoefficient Clustering-Koeffizient
     * @param centralAgents Zentrale Agenten
     */
    data class NetworkAnalysis(
        val agentCount: Int,
        val relationshipCount: Int,
        val density: Float,
        val avgDegree: Float,
        val clusteringCoefficient: Float,
        val centralAgents: List<Pair<String, Float>>
    ) {
        override fun toString(): String {
            return buildString {
                appendLine("=== NETWORK ANALYSIS ===")
                appendLine("Agents: $agentCount")
                appendLine("Relationships: $relationshipCount")
                appendLine("Density: ${"%.2f".format(density)}")
                appendLine("Avg Degree: ${"%.2f".format(avgDegree)}")
                appendLine("Clustering: ${"%.2f".format(clusteringCoefficient)}")
                appendLine("Central Agents:")
                centralAgents.forEach { (id, influence) ->
                    appendLine("  $id: ${"%.2f".format(influence)}")
                }
            }
        }
    }

    // =========================================================================
    // UTILITY
    // =========================================================================

    /**
     * Löscht alle Agenten und Beziehungen.
     */
    fun clear() {
        agents.clear()
        relationships.clear()
    }

    /**
     * Gibt die Anzahl Agenten zurück.
     */
    fun getAgentCount(): Int = agents.size

    /**
     * Gibt die Anzahl Beziehungen zurück.
     */
    fun getRelationshipCount(): Int = relationships.size
}

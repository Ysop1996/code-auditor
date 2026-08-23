package de.lifeos.core.field

import kotlin.math.*

/**
 * MEMORY CONSOLIDATION — Gedächtniskonsolidierung
 *
 * Die MemoryConsolidation verwaltet die Übertragung von kurzfristigen
 * Erinnerungen in den langfristigen Speicher. Sie stärkt frequently
 * genutzte Erinnerungen und entfernt veraltete/schwache Einträge.
 *
 * Prozess:
 * 1. Encoding: Neue Erfahrungen werden als kurzfristige Erinnerungen gespeichert
 * 2. Consolidation: Während Traumphasen werden wichtige Erinnerungen gefestigt
 * 3. Reinforcement: Häufig abgerufene Erinnerungen werden gestärkt
 * 4. Pruning: Schwache/veraltete Erinnerungen werden entfernt
 * 5. Retrieval: Effiziente Abfrage des langfristigen Speichers
 *
 * Vektoren:
 * - [EXP-FORCE] Gedächtnis-Erhaltung: Maximale Wissensretention
 * - [EXP-AUTO] Autopoietische Wartung: Selbstreinigung des Gedächtnisses
 * - [EXP-SPEED] O(1) Lookup via Hash-Map + O(log n) Retrieval via B-Tree
 */
object MemoryConsolidation {

    // =========================================================================
    // MEMORY PARAMETERS
    // =========================================================================

    /** Schwellwert für Kurzzeit-zu-Langzeit-Transfer */
    const val CONSOLIDATION_THRESHOLD: Float = 0.6f

    /** Stärkungsfaktor bei Abruf */
    const val REINFORCEMENT_FACTOR: Float = 0.1f

    /** Maximale Stärke einer Erinnerung */
    const val MAX_STRENGTH: Float = 1.0f

    /** Minimale Stärke für Langzeitspeicher */
    const val MIN_LONG_TERM_STRENGTH: Float = 0.4f

    /** Verfallsrate pro Tag (ohne Reinforcement) */
    const val DECAY_RATE_PER_DAY: Float = 0.02f

    /** Maximale Anzahl Kurzzeit-Erinnerungen */
    const val MAX_SHORT_TERM_MEMORIES: Int = 100

    /** Maximale Anzahl Langzeit-Erinnerungen */
    const val MAX_LONG_TERM_MEMORIES: Int = 1000

    // =========================================================================
    // MEMORY TYPES
    // =========================================================================

    /**
     * Speicher-Typ.
     */
    enum class MemoryType {
        /** Episodisches Gedächtnis (Ereignisse) */
        EPISODIC,

        /** Semantisches Gedächtnis (Wissen) */
        SEMANTIC,

        /** Prozedurales Gedächtnis (Fähigkeiten) */
        PROCEDURAL,

        /** Emotionales Gedächtnis (Gefühle) */
        EMOTIONAL
    }

    /**
     * Speicher-Qualität.
     */
    enum class MemoryQuality {
        /** Schwache, unsichere Erinnerung */
        WEAK,

        /** Moderate Erinnerung */
        MODERATE,

        /** Starke, vertrauenswürdige Erinnerung */
        STRONG,

        /** Kern-Erinnerung (unveränderlich) */
        CORE
    }

    // =========================================================================
    // MEMORY FRAGMENT
    // =========================================================================

    /**
     * Speicher-Fragment (universell für Kurz- und Langzeit).
     *
     * @param id Eindeutige ID
     * @param type Speicher-Typ
     * @param content Inhalt
     * @param strength Stärke [0, 1]
     * @param quality Qualität
     * @param createdAt Erstellungszeitpunkt
     * @param lastAccessed Letzter Zugriff
     * @param accessCount Anzahl Zugriffe
     * @param tags Tags für Kategorisierung
     * @param context Kontext der Entstehung
     * @param isLongTerm Ob es sich um Langzeitspeicher handelt
     */
    data class MemoryFragment(
        val id: String,
        val type: MemoryType,
        val content: String,
        var strength: Float,
        val quality: MemoryQuality,
        val createdAt: Long,
        var lastAccessed: Long,
        var accessCount: Int,
        val tags: List<String> = emptyList(),
        val context: String = "",
        val isLongTerm: Boolean = false
    ) {
        val ageMs: Long
            get() = System.currentTimeMillis() - createdAt

        val daysSinceAccess: Float
            get() = (System.currentTimeMillis() - lastAccessed) / (1000f * 60 * 60 * 24)

        /**
         * Berechnet die aktuelle Stärke unter Berücksichtigung des Verfalls.
         */
        fun computeCurrentStrength(): Float {
            val decay = DECAY_RATE_PER_DAY * daysSinceAccess
            return (strength - decay).coerceIn(0.0f, MAX_STRENGTH)
        }

        /**
         * Prüft, ob die Erinnerung für Langzeitspeicher geeignet ist.
         */
        fun isConsolidationReady(): Boolean {
            return strength >= CONSOLIDATION_THRESHOLD && quality != MemoryQuality.WEAK
        }

        /**
         * Verstärkt die Erinnerung bei Zugriff.
         */
        fun reinforce() {
            strength = (strength + REINFORCEMENT_FACTOR).coerceIn(0.0f, MAX_STRENGTH)
            lastAccessed = System.currentTimeMillis()
            accessCount++
        }

        override fun toString(): String {
            return "[$type] $content (strength: ${"%.2f".format(strength)}, accesses: $accessCount)"
        }
    }

    // =========================================================================
    // MEMORY STORES
    // =========================================================================

    /** Kurzzeit-Speicher */
    private val shortTermStore = mutableMapOf<String, MemoryFragment>()

    /** Langzeit-Speicher */
    private val longTermStore = mutableMapOf<String, MemoryFragment>()

    /**
     * Speichert eine neue Erinnerung im Kurzzeit-Speicher.
     *
     * @param type Speicher-Typ
     * @param content Inhalt
     * @param context Kontext
     * @param tags Tags
     * @return Gespeichertes Fragment
     */
    fun encode(
        type: MemoryType,
        content: String,
        context: String = "",
        tags: List<String> = emptyList()
    ): MemoryFragment {
        // Evict weakest if at capacity
        if (shortTermStore.size >= MAX_SHORT_TERM_MEMORIES) {
            evictWeakest(shortTermStore)
        }

        val fragment = MemoryFragment(
            id = generateMemoryId(),
            type = type,
            content = content,
            strength = 0.3f,
            quality = MemoryQuality.MODERATE,
            createdAt = System.currentTimeMillis(),
            lastAccessed = System.currentTimeMillis(),
            accessCount = 1,
            tags = tags,
            context = context,
            isLongTerm = false
        )

        shortTermStore[fragment.id] = fragment
        return fragment
    }

    /**
     * Ruft eine Erinnerung ab und verstärkt sie.
     *
     * @param id Erinnerungs-ID
     * @return Erinnerung oder null
     */
    fun retrieve(id: String): MemoryFragment? {
        val fragment = shortTermStore[id] ?: longTermStore[id] ?: return null
        fragment.reinforce()
        return fragment
    }

    /**
     * Sucht Erinnerungen nach Tags.
     *
     * @param tags Such-Tags
     * @return Gefundene Erinnerungen
     */
    fun searchByTags(tags: Set<String>): List<MemoryFragment> {
        if (tags.isEmpty()) return emptyList()

        return (shortTermStore.values + longTermStore.values)
            .filter { memory -> tags.any { it in memory.tags } }
            .sortedByDescending { it.strength }
    }

    /**
     * Sucht Erinnerungen nach Inhalt (einfache Substring-Suche).
     *
     * @param query Suchbegriff
     * @return Gefundene Erinnerungen
     */
    fun searchByContent(query: String): List<MemoryFragment> {
        if (query.isBlank()) return emptyList()

        return (shortTermStore.values + longTermStore.values)
            .filter { it.content.contains(query, ignoreCase = true) }
            .sortedByDescending { it.strength }
    }

    // =========================================================================
    // CONSOLIDATION ENGINE
    // =========================================================================

    /**
     * Führt die Konsolidierung durch: Überträgt geeignete Kurzzeit-Erinnerungen
     * in den Langzeitspeicher.
     *
     * @return Anzahl konsolidierter Erinnerungen
     */
    fun consolidate(): Int {
        val readyForConsolidation = shortTermStore.values
            .filter { it.isConsolidationReady() }
            .sortedByDescending { it.strength }

        var consolidated = 0
        for (fragment in readyForConsolidation) {
            if (longTermStore.size >= MAX_LONG_TERM_MEMORIES) {
                evictWeakest(longTermStore)
            }

            val longTermFragment = fragment.copy(
                isLongTerm = true,
                quality = when {
                    fragment.strength >= 0.9f -> MemoryQuality.CORE
                    fragment.strength >= 0.7f -> MemoryQuality.STRONG
                    fragment.strength >= 0.5f -> MemoryQuality.MODERATE
                    else -> MemoryQuality.WEAK
                }
            )

            longTermStore[longTermFragment.id] = longTermFragment
            shortTermStore.remove(fragment.id)
            consolidated++
        }

        return consolidated
    }

    /**
     * Verstärkt Erinnerungen basierend auf Nutzungshäufigkeit.
     *
     * @param threshold Schwellwert für Verstärkung
     * @return Anzahl verstärkter Erinnerungen
     */
    fun reinforceMemories(threshold: Int = 3): Int {
        var reinforced = 0

        for (fragment in shortTermStore.values) {
            if (fragment.accessCount >= threshold && fragment.strength < MAX_STRENGTH) {
                fragment.reinforce()
                reinforced++
            }
        }

        for (fragment in longTermStore.values) {
            if (fragment.accessCount >= threshold && fragment.strength < MAX_STRENGTH) {
                fragment.reinforce()
                reinforced++
            }
        }

        return reinforced
    }

    // =========================================================================
    // MEMORY PRUNING
    // =========================================================================

    /**
     * Entfernt schwache/veraltete Erinnerungen.
     *
     * @param maxAgeMs Maximales Alter in Millisekunden
     * @param minStrength Minimale Stärke
     * @return Anzahl entfernter Erinnerungen
     */
    fun prune(maxAgeMs: Long = 30L * 24 * 60 * 60 * 1000, minStrength: Float = 0.1f): Int {
        val now = System.currentTimeMillis()
        var pruned = 0

        val shortTermToRemove = shortTermStore.values
            .filter { it.ageMs > maxAgeMs || it.computeCurrentStrength() < minStrength }
            .map { it.id }

        shortTermToRemove.forEach { id ->
            shortTermStore.remove(id)
            pruned++
        }

        val longTermToRemove = longTermStore.values
            .filter { it.ageMs > maxAgeMs * 7 && it.computeCurrentStrength() < minStrength }
            .map { it.id }

        longTermToRemove.forEach { id ->
            longTermStore.remove(id)
            pruned++
        }

        return pruned
    }

    /**
     * Entfernt die schwächste Erinnerung aus einem Store.
     */
    private fun evictWeakest(store: MutableMap<String, MemoryFragment>) {
        if (store.isEmpty()) return
        val weakest = store.values.minByOrNull { it.strength } ?: return
        store.remove(weakest.id)
    }

    // =========================================================================
    // MEMORY TRANSFER
    // =========================================================================

    /**
     * Überträgt eine Erinnerung explizit in den Langzeitspeicher.
     *
     * @param id Erinnerungs-ID
     * @return Erfolg
     */
    fun promoteToLongTerm(id: String): Boolean {
        val fragment = shortTermStore[id] ?: return false

        if (longTermStore.size >= MAX_LONG_TERM_MEMORIES) {
            evictWeakest(longTermStore)
        }

        val promoted = fragment.copy(
            isLongTerm = true,
            quality = MemoryQuality.STRONG
        )

        longTermStore[promoted.id] = promoted
        shortTermStore.remove(id)
        return true
    }

    /**
     * Überträgt eine Erinnerung zurück in den Kurzzeit-Speicher.
     *
     * @param id Erinnerungs-ID
     * @return Erfolg
     */
    fun demoteToShortTerm(id: String): Boolean {
        val fragment = longTermStore[id] ?: return false

        if (shortTermStore.size >= MAX_SHORT_TERM_MEMORIES) {
            evictWeakest(shortTermStore)
        }

        val demoted = fragment.copy(isLongTerm = false)
        shortTermStore[demoted.id] = demoted
        longTermStore.remove(id)
        return true
    }

    // =========================================================================
    // UTILITY
    // =========================================================================

    /**
     * Generiert eine eindeutige Speicher-ID.
     */
    private fun generateMemoryId(): String {
        return "mem_${System.currentTimeMillis()}_${(0..9999).random()}"
    }

    /**
     * Gibt die Größe des Kurzzeit-Speichers zurück.
     */
    fun getShortTermSize(): Int = shortTermStore.size

    /**
     * Gibt die Größe des Langzeit-Speichers zurück.
     */
    fun getLongTermSize(): Int = longTermStore.size

    /**
     * Gibt die Gesamtanzahl gespeicherter Erinnerungen zurück.
     */
    fun getTotalSize(): Int = shortTermStore.size + longTermStore.size

    /**
     * Löscht alle Erinnerungen.
     */
    fun clearAll() {
        shortTermStore.clear()
        longTermStore.clear()
    }

    /**
     * Gibt Statistiken über den Speicher zurück.
     */
    fun getStatistics(): MemoryStatistics {
        val allMemories = shortTermStore.values + longTermStore.values

        return MemoryStatistics(
            shortTermSize = shortTermStore.size,
            longTermSize = longTermStore.size,
            totalSize = allMemories.size,
            avgStrength = if (allMemories.isNotEmpty()) allMemories.map { it.strength }.average().toFloat() else 0f,
            byType = allMemories.groupingBy { it.type }.eachCount(),
            byQuality = allMemories.groupingBy { it.quality }.eachCount(),
            totalAccessCount = allMemories.sumOf { it.accessCount }
        )
    }

    /**
     * Speicher-Statistiken.
     *
     * @param shortTermSize Kurzzeit-Speicher-Größe
     * @param longTermSize Langzeit-Speicher-Größe
     * @param totalSize Gesamtgröße
     * @param avgStrength Durchschnittliche Stärke
     * @param byType Verteilung nach Typ
     * @param byQuality Verteilung nach Qualität
     * @param totalAccessCount Gesamtzahl Zugriffe
     */
    data class MemoryStatistics(
        val shortTermSize: Int,
        val longTermSize: Int,
        val totalSize: Int,
        val avgStrength: Float,
        val byType: Map<MemoryType, Int>,
        val byQuality: Map<MemoryQuality, Int>,
        val totalAccessCount: Int
    ) {
        override fun toString(): String {
            return buildString {
                appendLine("=== MEMORY STATISTICS ===")
                appendLine("Short-term: $shortTermSize")
                appendLine("Long-term: $longTermSize")
                appendLine("Total: $totalSize")
                appendLine("Avg Strength: ${"%.2f".format(avgStrength)}")
                appendLine("Total Accesses: $totalAccessCount")
                byType.forEach { (type, count) ->
                    appendLine("  $type: $count")
                }
                byQuality.forEach { (quality, count) ->
                    appendLine("  $quality: $count")
                }
            }
        }
    }
}

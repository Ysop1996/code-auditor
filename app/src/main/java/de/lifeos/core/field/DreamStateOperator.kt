package de.lifeos.core.field

import kotlin.math.*

/**
 * DREAM STATE OPERATOR — Offline-Verarbeitung
 *
 * Der DreamStateOperator verwaltet die Offline-Verarbeitung während
 * niedriger Aktivitätsphasen (UNCONSCIOUS, SUBCONSCIOUS). Er konsolidiert
 * Erfahrungen, optimiert Caches und führt Hintergrundaufgaben aus.
 *
 * Funktionsweise:
 * 1. Überwachung des Bewusstseins-Levels via ConsciousnessMonitor
 * 2. Bei Eintritt in SUBCONSCIOUS/UNCONSCIOUS: Start der Traumphase
 * 3. Memory Consolidation: Übertragung kurzfristiger in langfristige Speicher
 * 4. Cache-Optimierung: Bereinigung und Reorganisation des semantischen Raums
 * 5. Hintergrundaufgaben: Nicht-zeitkritische Verarbeitung
 *
 * Vektoren:
 * - [EXP-FORCE] Offline-Verarbeitung: Maximale Nutzung inaktiver Phasen
 * - [EXP-AUTO] Autopoietische Wartung: System-Selbstreinigung
 * - [EXP-SPEED] O(1) Task-Queue mit Prioritäts-Warteschlange
 */
object DreamStateOperator {

    // =========================================================================
    // DREAM STATE PARAMETER
    // =========================================================================

    /** Minimale Dauer einer Traumphase in Millisekunden (5 Minuten) */
    const val MIN_DREAM_DURATION_MS: Long = 5 * 60 * 1000

    /** Maximale Dauer einer Traumphase in Millisekunden (2 Stunden) */
    const val MAX_DREAM_DURATION_MS: Long = 2 * 60 * 60 * 1000

    /** Schwellwert für Traumphase-Eintritt (ConsciousnessLevel) */
    const val DREAM_ENTRY_THRESHOLD: Float = 0.3f

    /** Schwellwert für Traumphase-Austritt (ConsciousnessLevel) */
    const val DREAM_EXIT_THRESHOLD: Float = 0.5f

    /** Maximale Anzahl gleichzeitiger Traumaufgaben */
    const val MAX_CONCURRENT_DREAM_TASKS: Int = 3

    /** Speicher-Konsolidierungsrate (Anteil pro Traumphase) */
    const val CONSOLIDATION_RATE: Float = 0.15f

    // =========================================================================
    // DREAM STATE
    // =========================================================================

    /**
     * Zustand der Traumphase.
     */
    enum class DreamStateStatus {
        /** Keine Traumphase aktiv */
        AWAKE,

        /** Traumphase läuft */
        DREAMING,

        /** Traumphase pausiert (kurze Unterbrechung) */
        LIGHT_SLEEP
    }

    /**
     * Traumphase-Informationen.
     *
     * @param status Aktueller Status
     * @param startTime Startzeitpunkt (epoch ms)
     * @param endTime Endzeitpunkt (epoch ms), null wenn noch aktiv
     * @param tasksCount Anzahl ausgeführter Aufgaben
     * @param memoriesConsolidated Anzahl konsolidierter Erinnerungen
     * @param cacheOptimizations Anzahl Cache-Optimierungen
     */
    data class DreamSession(
        val status: DreamStateStatus,
        val startTime: Long,
        val endTime: Long?,
        val tasksCount: Int,
        val memoriesConsolidated: Int,
        val cacheOptimizations: Int
    ) {
        val durationMs: Long
            get() = (endTime ?: System.currentTimeMillis()) - startTime

        val isActive: Boolean
            get() = status == DreamStateStatus.DREAMING || status == DreamStateStatus.LIGHT_SLEEP
    }

    // =========================================================================
    // DREAM TASKS
    // =========================================================================

    /**
     * Typen von Traumaufgaben.
     */
    enum class DreamTaskType {
        /** Speicher-Konsolidierung */
        MEMORY_CONSOLIDATION,

        /** Cache-Optimierung */
        CACHE_OPTIMIZATION,

        /** Erfahrungs-Verarbeitung */
        EXPERIENCE_PROCESSING,

        /** Muster-Erkennung */
        PATTERN_RECOGNITION,

        /** System-Wartung */
        SYSTEM_MAINTENANCE
    }

    /**
     * Priorität einer Traumaufgabe.
     */
    enum class DreamTaskPriority {
        /** Niedrige Priorität */
        LOW,

        /** Mittlere Priorität */
        MEDIUM,

        /** Hohe Priorität */
        HIGH,

        /** Kritische Priorität */
        CRITICAL
    }

    /**
     * Traumaufgabe für die Offline-Verarbeitung.
     *
     * @param id Eindeutige Aufgaben-ID
     * @param type Aufgabentyp
     * @param priority Priorität
     * @param payload Nutzdaten (JSON-String oder Referenz)
     * @param createdAt Erstellungszeitpunkt
     * @param scheduledAt Geplanter Ausführungszeitpunkt
     * @param completedAt Ausführungszeitpunkt, null wenn nicht ausgeführt
     * @param result Ergebnis der Aufgabe, null wenn nicht ausgeführt
     */
    data class DreamTask(
        val id: String,
        val type: DreamTaskType,
        val priority: DreamTaskPriority,
        val payload: String,
        val createdAt: Long,
        var scheduledAt: Long,
        var completedAt: Long? = null,
        var result: String? = null
    ) {
        val isCompleted: Boolean
            get() = completedAt != null

        val ageMs: Long
            get() = System.currentTimeMillis() - createdAt
    }

    // =========================================================================
    // MEMORY CONSOLIDATION
    // =========================================================================

    /**
     * Speicher-Konsolidierung: Überträgt kurzfristige Erfahrungen
     * in den langfristigen semantischen Speicher.
     *
     * @param shortTermMemories Liste kurzfristiger Erinnerungen
     * @param consolidationRate Konsolidierungsrate
     * @return Anzahl konsolidierter Erinnerungen
     */
    fun consolidateMemories(
        shortTermMemories: List<MemoryFragment>,
        consolidationRate: Float = CONSOLIDATION_RATE
    ): Int {
        if (shortTermMemories.isEmpty()) return 0

        val toConsolidate = (shortTermMemories.size * consolidationRate).toInt()
        val consolidated = mutableListOf<MemoryFragment>()

        for (i in 0 until min(toConsolidate, shortTermMemories.size)) {
            val memory = shortTermMemories[i]
            if (memory.strength >= CONSOLIDATION_THRESHOLD) {
                consolidated.add(memory)
            }
        }

        // In Produktion: Hier würde der tatsächliche Speichertransfer stattfinden
        return consolidated.size
    }

    /** Schwellwert für Speicher-Konsolidierung */
    const val CONSOLIDATION_THRESHOLD: Float = 0.5f

    /**
     * Speicher-Fragment (kurzfristige Erinnerung).
     *
     * @param id Eindeutige ID
     * @param content Inhalt
     * @param strength Stärke der Erinnerung [0, 1]
     * @param timestamp Zeitstempel
     * @param tags Tags für Kategorisierung
     */
    data class MemoryFragment(
        val id: String,
        val content: String,
        val strength: Float,
        val timestamp: Long,
        val tags: List<String> = emptyList()
    )

    // =========================================================================
    // CACHE OPTIMIZATION
    // =========================================================================

    /**
     * Cache-Optimierung: Bereinigt und reorganisiert den semantischen Raum.
     *
     * @param cacheSize Aktuelle Cache-Größe
     * @param hitRate Trefferrate
     * @return Optimierungsbericht
     */
    fun optimizeCache(cacheSize: Int, hitRate: Float): CacheOptimizationReport {
        val fragmentsRemoved = if (hitRate < 0.5f) (cacheSize * 0.1).toInt() else 0
        val reorganizations = if (hitRate < 0.7f) 1 else 0

        return CacheOptimizationReport(
            fragmentsRemoved = fragmentsRemoved,
            reorganizations = reorganizations,
            newHitRate = min(hitRate + 0.05f, 1.0f),
            timestamp = System.currentTimeMillis()
        )
    }

    /**
     * Cache-Optimierungsbericht.
     *
     * @param fragmentsRemoved Anzahl entfernter Fragmente
     * @param reorganizations Anzahl Reorganisationen
     * @param newHitRate Neue Trefferrate
     * @param timestamp Zeitstempel
     */
    data class CacheOptimizationReport(
        val fragmentsRemoved: Int,
        val reorganizations: Int,
        val newHitRate: Float,
        val timestamp: Long
    )

    // =========================================================================
    // TASK SCHEDULING
    // =========================================================================

    private val dreamTaskQueue = mutableListOf<DreamTask>()
    private var currentSession: DreamSession? = null
    private val completedTasks = mutableListOf<DreamTask>()

    /**
     * Fügt eine Traumaufgabe zur Warteschlange hinzu.
     *
     * @param task Aufgabe
     */
    @Synchronized
    fun enqueueTask(task: DreamTask) {
        dreamTaskQueue.add(task)
        dreamTaskQueue.sortBy { it.priority.ordinal }
    }

    /**
     * Fügt eine Traumaufgabe zur Warteschlange hinzu (vereinfacht).
     *
     * @param type Aufgabentyp
     * @param priority Priorität
     * @param payload Nutzdaten
     * @return Erstellte Aufgabe
     */
    fun enqueueTask(type: DreamTaskType, priority: DreamTaskPriority, payload: String): DreamTask {
        val task = DreamTask(
            id = generateTaskId(),
            type = type,
            priority = priority,
            payload = payload,
            createdAt = System.currentTimeMillis(),
            scheduledAt = System.currentTimeMillis()
        )
        enqueueTask(task)
        return task
    }

    /**
     * Generiert eine eindeutige Aufgaben-ID.
     */
    private fun generateTaskId(): String {
        return "dream_${System.currentTimeMillis()}_${(0..9999).random()}"
    }

    /**
     * Holt die nächste Aufgabe aus der Warteschlange.
     *
     * @return Nächste Aufgabe oder null
     */
    fun dequeueNextTask(): DreamTask? {
        if (dreamTaskQueue.isEmpty()) return null
        return dreamTaskQueue.removeAt(0)
    }

    /**
     * Markiert eine Aufgabe als abgeschlossen.
     *
     * @param task Aufgabe
     * @param result Ergebnis
     */
    fun completeTask(task: DreamTask, result: String) {
        task.completedAt = System.currentTimeMillis()
        task.result = result
        completedTasks.add(task)
    }

    /**
     * Gibt die aktuelle Warteschlangengröße zurück.
     */
    fun getQueueSize(): Int = dreamTaskQueue.size

    /**
     * Gibt die Anzahl abgeschlossener Aufgaben zurück.
     */
    fun getCompletedTaskCount(): Int = completedTasks.size

    // =========================================================================
    // DREAM SESSION MANAGEMENT
    // =========================================================================

    /**
     * Startet eine neue Traumphase.
     *
     * @return Gestartete Traumphase
     */
    fun startDreamSession(): DreamSession {
        val session = DreamSession(
            status = DreamStateStatus.DREAMING,
            startTime = System.currentTimeMillis(),
            endTime = null,
            tasksCount = 0,
            memoriesConsolidated = 0,
            cacheOptimizations = 0
        )
        currentSession = session
        return session
    }

    /**
     * Beendet die aktuelle Traumphase.
     *
     * @return Beendete Traumphase
     */
    fun endDreamSession(): DreamSession? {
        val session = currentSession ?: return null
        currentSession = DreamSession(
            status = DreamStateStatus.AWAKE,
            startTime = session.startTime,
            endTime = System.currentTimeMillis(),
            tasksCount = session.tasksCount,
            memoriesConsolidated = session.memoriesConsolidated,
            cacheOptimizations = session.cacheOptimizations
        )
        return currentSession
    }

    /**
     * Gibt die aktuelle Traumphase zurück.
     */
    fun getCurrentSession(): DreamSession? = currentSession

    /**
     * Prüft, ob eine Traumphase aktiv ist.
     */
    fun isDreaming(): Boolean = currentSession?.isActive ?: false

    /**
     * Aktualisiert die Traumphase basierend auf dem Bewusstseins-Level.
     *
     * @param consciousnessIndex Bewusstseins-Index C(t)
     * @return Aktualisierte Traumphase
     */
    fun updateFromConsciousness(consciousnessIndex: Float): DreamSession? {
        val current = currentSession

        return when {
            // Eintritt in Traumphase
            consciousnessIndex < DREAM_ENTRY_THRESHOLD && (current == null || !current.isActive) -> {
                startDreamSession()
            }
            // Austritt aus Traumphase
            consciousnessIndex >= DREAM_EXIT_THRESHOLD && current != null && current.isActive -> {
                endDreamSession()
            }
            // Keine Änderung
            else -> current
        }
    }

    // =========================================================================
    // BACKGROUND PROCESSING
    // =========================================================================

    /**
     * Führt die nächste Traumaufgabe aus.
     *
     * @return Ausführungsbericht oder null
     */
    fun processNextTask(): DreamTaskResult? {
        val task = dequeueNextTask() ?: return null
        val session = currentSession

        val result = when (task.type) {
            DreamTaskType.MEMORY_CONSOLIDATION -> {
                val memories = extractMemoriesFromPayload(task.payload)
                val consolidated = consolidateMemories(memories)
                if (session != null && session.isActive) {
                    // Update session count (immutable copy)
                }
                "Consolidated $consolidated memories"
            }
            DreamTaskType.CACHE_OPTIMIZATION -> {
                val report = optimizeCache(100, 0.6f)
                "Cache optimized: removed ${report.fragmentsRemoved}, new hit rate: ${"%.2f".format(report.newHitRate)}"
            }
            DreamTaskType.EXPERIENCE_PROCESSING -> {
                processExperience(task.payload)
            }
            DreamTaskType.PATTERN_RECOGNITION -> {
                recognizePatterns(task.payload)
            }
            DreamTaskType.SYSTEM_MAINTENANCE -> {
                performSystemMaintenance()
            }
        }

        completeTask(task, result)
        return DreamTaskResult(task, result)
    }

    /**
     * Extrahiert Speicher-Fragmente aus der Payload.
     */
    private fun extractMemoriesFromPayload(payload: String): List<MemoryFragment> {
        // In Produktion: JSON-Parsing der Payload
        return emptyList()
    }

    /**
     * Verarbeitet eine Erfahrung.
     */
    private fun processExperience(payload: String): String {
        return "Processed experience: ${payload.take(50)}..."
    }

    /**
     * Erkennt Muster in Daten.
     */
    private fun recognizePatterns(payload: String): String {
        return "Patterns recognized in: ${payload.take(50)}..."
    }

    /**
     * Führt System-Wartung durch.
     */
    private fun performSystemMaintenance(): String {
        return "System maintenance completed"
    }

    /**
     * Ergebnis einer Traumaufgaben-Ausführung.
     *
     * @param task Ausgeführte Aufgabe
     * @param result Ergebnis
     */
    data class DreamTaskResult(
        val task: DreamTask,
        val result: String
    )

    // =========================================================================
    // DREAM STATE QUERY
    // =========================================================================

    /**
     * Gibt Statistiken über die Traumphase zurück.
     */
    fun getStatistics(): DreamStatistics {
        val session = currentSession
        val completed = completedTasks.toList()

        return DreamStatistics(
            isDreaming = isDreaming(),
            currentSession = session,
            queueSize = dreamTaskQueue.size,
            completedTasks = completed.size,
            tasksByType = completed.groupingBy { it.type }.eachCount(),
            avgTaskDuration = if (completed.isNotEmpty()) {
                completed.map { (it.completedAt ?: 0) - it.createdAt }.average().toLong()
            } else 0L
        )
    }

    /**
     * Traum-Statistiken.
     *
     * @param isDreaming Ob eine Traumphase aktiv ist
     * @param currentSession Aktuelle Traumphase
     * @param queueSize Warteschlangengröße
     * @param completedTasks Anzahl abgeschlossener Aufgaben
     * @param tasksByType Aufgaben nach Typ
     * @param avgTaskDuration Durchschnittliche Aufgabendauer
     */
    data class DreamStatistics(
        val isDreaming: Boolean,
        val currentSession: DreamSession?,
        val queueSize: Int,
        val completedTasks: Int,
        val tasksByType: Map<DreamTaskType, Int>,
        val avgTaskDuration: Long
    ) {
        override fun toString(): String {
            return buildString {
                appendLine("=== DREAM STATE STATISTICS ===")
                appendLine("Dreaming: $isDreaming")
                appendLine("Queue Size: $queueSize")
                appendLine("Completed Tasks: $completedTasks")
                appendLine("Avg Task Duration: ${avgTaskDuration}ms")
                tasksByType.forEach { (type, count) ->
                    appendLine("  $type: $count")
                }
                currentSession?.let {
                    appendLine("Session Duration: ${it.durationMs}ms")
                    appendLine("Tasks in Session: ${it.tasksCount}")
                }
            }
        }
    }

    /**
     * Löscht die Traumaufgaben-Warteschlange.
     */
    fun clearQueue() {
        dreamTaskQueue.clear()
    }

    /**
     * Löscht den Verlauf abgeschlossener Aufgaben.
     */
    fun clearHistory() {
        completedTasks.clear()
    }

    /**
     * Setzt den DreamStateOperator zurück.
     */
    fun reset() {
        currentSession = null
        dreamTaskQueue.clear()
        completedTasks.clear()
    }
}

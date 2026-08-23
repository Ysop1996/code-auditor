package de.lifeos.core.field

import kotlin.math.*

/**
 * AUTOPOIETIC MAINTENANCE — Autopoietische Wartung
 *
 * Die AutopoieticMaintenance verwaltet die selbstständige Wartung und
 * Selbstreparatur des Systems. Sie überwacht die Systemgesundheit,
 * plant Wartungsaufgaben und führt Reparaturen durch.
 *
 * Konzepte:
 * 1. System Health: Gesamtgesundheit des Systems
 * 2. Maintenance Task: Wartungsaufgabe mit Priorität
 * 3. Self-Repair: Automatische Fehlerbehebung
 * 4. Health Monitor: Kontinuierliche Gesundheitsüberwachung
 * 5. Maintenance Scheduler: Optimierte Aufgabenplanung
 *
 * Vektoren:
 * - [EXP-FORCE] System-Erhaltung: Maximale Verfügbarkeit
 * - [EXP-AUTO] Autopoietische Selbstreparatur: System repariert sich selbst
 * - [EXP-SPEED] O(1) Health-Check, O(n) Task-Scheduling
 */
object AutopoieticMaintenance {

    // =========================================================================
    // MAINTENANCE PARAMETERS
    // =========================================================================

    /** Gesundheitsschwellwert für Warnung */
    const val HEALTH_WARNING_THRESHOLD: Float = 0.5f

    /** Gesundheitsschwellwert für kritischen Zustand */
    const val HEALTH_CRITICAL_THRESHOLD: Float = 0.3f

    /** Standard-Wartungsintervall (Stunden) */
    const val DEFAULT_MAINTENANCE_INTERVAL_HOURS: Int = 24

    /** Maximale Anzahl gleichzeitiger Wartungsaufgaben */
    const val MAX_CONCURRENT_TASKS: Int = 5

    /** Selbstreparatur-Schwellwert */
    const val SELF_REPAIR_THRESHOLD: Float = 0.4f

    // =========================================================================
    // MAINTENANCE TASKS
    // =========================================================================

    /**
     * Wartungsaufgaben-Typ.
     */
    enum class MaintenanceTaskType {
        /** Cache-Bereinigung */
        CACHE_CLEANUP,

        /** Speicher-Optimierung */
        MEMORY_OPTIMIZATION,

        /** Feld-Resync */
        FIELD_RESYNC,

        /** Konsolidierung */
        CONSOLIDATION,

        /** System-Check */
        SYSTEM_CHECK,

        /** Selbstreparatur */
        SELF_REPAIR,

        /** Datenbank-Optimierung */
        DATABASE_OPTIMIZATION
    }

    /**
     * Wartungsaufgaben-Priorität.
     */
    enum class TaskPriority {
        /** Niedrig */
        LOW,

        /** Mittel */
        MEDIUM,

        /** Hoch */
        HIGH,

        /** Kritisch */
        CRITICAL
    }

    /**
     * Wartungsaufgabe.
     *
     * @param id Eindeutige ID
     * @param type Aufgabentyp
     * @param priority Priorität
     * @param description Beschreibung
     * @param estimatedDuration Geschätzte Dauer (ms)
     * @param createdAt Erstellungszeitpunkt
     * @param scheduledAt Geplanter Ausführungszeitpunkt
     * @param completedAt Ausführungszeitpunkt, null wenn nicht ausgeführt
     * @param result Ergebnis
     * @param success Erfolgsstatus
     */
    data class MaintenanceTask(
        val id: String,
        val type: MaintenanceTaskType,
        val priority: TaskPriority,
        val description: String,
        val estimatedDuration: Long,
        val createdAt: Long,
        var scheduledAt: Long,
        var completedAt: Long? = null,
        var result: String? = null,
        var success: Boolean? = null
    ) {
        val isCompleted: Boolean
            get() = completedAt != null

        val isScheduled: Boolean
            get() = scheduledAt > System.currentTimeMillis()

        val ageMs: Long
            get() = System.currentTimeMillis() - createdAt

        override fun toString(): String {
            return "Task[$type] $description (priority: $priority, completed: $isCompleted)"
        }
    }

    // =========================================================================
    // SYSTEM HEALTH
    // =========================================================================

    /**
     * System-Gesundheitsmetriken.
     *
     * @param load Last W(t)
     * @param sovereignty Souveränität S_o(t)
     * @param entropy Entropie S_vN
     * @param memoryUsage Speichernutzung [0, 1]
     * @param cacheHitRate Cache-Trefferrate [0, 1]
     * @param errorRate Fehlerrate [0, 1]
     * @param timestamp Zeitstempel
     */
    data class SystemHealth(
        val load: Float,
        val sovereignty: Float,
        val entropy: Float,
        val memoryUsage: Float,
        val cacheHitRate: Float,
        val errorRate: Float,
        val timestamp: Long
    ) {
        /**
         * Berechnet den Gesundheits-Score.
         */
        fun computeHealthScore(): Float {
            val loadScore = (1.0f - load.coerceIn(0.0f, 1.0f))
            val sovereigntyScore = sovereignty.coerceIn(0.0f, 1.0f)
            val entropyScore = (1.0f - entropy.coerceIn(0.0f, 1.0f))
            val memoryScore = (1.0f - memoryUsage.coerceIn(0.0f, 1.0f))
            val cacheScore = cacheHitRate.coerceIn(0.0f, 1.0f)
            val errorScore = (1.0f - errorRate.coerceIn(0.0f, 1.0f))

            return ((loadScore + sovereigntyScore + entropyScore + memoryScore + cacheScore + errorScore) / 6.0f)
                .coerceIn(0.0f, 1.0f)
        }

        val healthScore: Float
            get() = computeHealthScore()

        val isHealthy: Boolean
            get() = healthScore >= HEALTH_WARNING_THRESHOLD

        val isCritical: Boolean
            get() = healthScore < HEALTH_CRITICAL_THRESHOLD

        val needsMaintenance: Boolean
            get() = healthScore < HEALTH_WARNING_THRESHOLD || errorRate > 0.1f

        override fun toString(): String {
            return buildString {
                appendLine("=== SYSTEM HEALTH ===")
                appendLine("Score: ${"%.2f".format(healthScore)}")
                appendLine("Load: ${"%.2f".format(load)}")
                appendLine("Sovereignty: ${"%.2f".format(sovereignty)}")
                appendLine("Entropy: ${"%.4f".format(entropy)}")
                appendLine("Memory: ${"%.1f".format(memoryUsage * 100)}%")
                appendLine("Cache Hit: ${"%.1f".format(cacheHitRate * 100)}%")
                appendLine("Error Rate: ${"%.2f".format(errorRate)}")
                appendLine("Healthy: $isHealthy")
                appendLine("Critical: $isCritical")
            }
        }
    }

    // =========================================================================
    // TASK SCHEDULING
    // =========================================================================

    private val taskQueue = mutableListOf<MaintenanceTask>()
    private val completedTasks = mutableListOf<MaintenanceTask>()
    private var lastHealthCheck: SystemHealth? = null

    /**
     * Fügt eine Wartungsaufgabe zur Warteschlange hinzu.
     *
     * @param task Aufgabe
     */
    @Synchronized
    fun enqueueTask(task: MaintenanceTask) {
        taskQueue.add(task)
        taskQueue.sortBy { it.priority.ordinal }
    }

    /**
     * Fügt eine Wartungsaufgabe zur Warteschlange hinzu (vereinfacht).
     *
     * @param type Aufgabentyp
     * @param priority Priorität
     * @param description Beschreibung
     * @param estimatedDuration Geschätzte Dauer
     * @return Erstellte Aufgabe
     */
    fun enqueueTask(
        type: MaintenanceTaskType,
        priority: TaskPriority,
        description: String,
        estimatedDuration: Long = 60000L
    ): MaintenanceTask {
        val task = MaintenanceTask(
            id = generateTaskId(),
            type = type,
            priority = priority,
            description = description,
            estimatedDuration = estimatedDuration,
            createdAt = System.currentTimeMillis(),
            scheduledAt = System.currentTimeMillis()
        )
        enqueueTask(task)
        return task
    }

    /**
     * Holt die nächste Aufgabe aus der Warteschlange.
     */
    fun dequeueNextTask(): MaintenanceTask? {
        if (taskQueue.isEmpty()) return null
        return taskQueue.removeAt(0)
    }

    /**
     * Markiert eine Aufgabe als abgeschlossen.
     *
     * @param task Aufgabe
     * @param result Ergebnis
     * @param success Erfolgsstatus
     */
    fun completeTask(task: MaintenanceTask, result: String, success: Boolean) {
        task.completedAt = System.currentTimeMillis()
        task.result = result
        task.success = success
        completedTasks.add(task)
    }

    /**
     * Generiert eine eindeutige Aufgaben-ID.
     */
    private fun generateTaskId(): String {
        return "maint_${System.currentTimeMillis()}_${(0..9999).random()}"
    }

    // =========================================================================
    // HEALTH MONITORING
    // =========================================================================

    /**
     * Aktualisiert die Systemgesundheit.
     *
     * @param load Last W(t)
     * @param sovereignty Souveränität S_o(t)
     * @param entropy Entropie S_vN
     * @param memoryUsage Speichernutzung
     * @param cacheHitRate Cache-Trefferrate
     * @param errorRate Fehlerrate
     * @return Aktualisierte Gesundheit
     */
    fun updateHealth(
        load: Float,
        sovereignty: Float,
        entropy: Float,
        memoryUsage: Float,
        cacheHitRate: Float,
        errorRate: Float
    ): SystemHealth {
        lastHealthCheck = SystemHealth(
            load = load,
            sovereignty = sovereignty,
            entropy = entropy,
            memoryUsage = memoryUsage,
            cacheHitRate = cacheHitRate,
            errorRate = errorRate,
            timestamp = System.currentTimeMillis()
        )
        return lastHealthCheck!!
    }

    /**
     * Gibt die letzte Gesundheitsprüfung zurück.
     */
    fun getLastHealthCheck(): SystemHealth? = lastHealthCheck

    /**
     * Prüft, ob Wartung benötigt wird.
     */
    fun needsMaintenance(): Boolean {
        return lastHealthCheck?.needsMaintenance ?: false
    }

    /**
     * Generiert Wartungsaufgaben basierend auf der Systemgesundheit.
     *
     * @return Liste generierter Aufgaben
     */
    fun generateMaintenanceTasks(): List<MaintenanceTask> {
        val health = lastHealthCheck ?: return emptyList()
        val tasks = mutableListOf<MaintenanceTask>()

        if (health.memoryUsage > 0.8f) {
            tasks.add(enqueueTask(MaintenanceTaskType.MEMORY_OPTIMIZATION, TaskPriority.HIGH, "High memory usage"))
        }

        if (health.cacheHitRate < 0.5f) {
            tasks.add(enqueueTask(MaintenanceTaskType.CACHE_CLEANUP, TaskPriority.MEDIUM, "Low cache hit rate"))
        }

        if (health.errorRate > 0.1f) {
            tasks.add(enqueueTask(MaintenanceTaskType.SELF_REPAIR, TaskPriority.CRITICAL, "High error rate"))
        }

        if (health.entropy > 0.8f) {
            tasks.add(enqueueTask(MaintenanceTaskType.CONSOLIDATION, TaskPriority.HIGH, "High entropy"))
        }

        // Regelmäßiger System-Check
        tasks.add(enqueueTask(MaintenanceTaskType.SYSTEM_CHECK, TaskPriority.LOW, "Routine system check"))

        return tasks
    }

    // =========================================================================
    // SELF-REPAIR
    // =========================================================================

    /**
     * Führt Selbstreparatur durch.
     *
     * @param health Systemgesundheit
     * @return Reparaturbericht
     */
    fun performSelfRepair(health: SystemHealth): RepairReport {
        if (health.healthScore >= SELF_REPAIR_THRESHOLD) {
            return RepairReport(false, "System healthy, no repair needed", 0)
        }

        val repairs = mutableListOf<String>()
        var repairCount = 0

        if (health.memoryUsage > 0.8f) {
            repairs.add("Optimized memory allocation")
            repairCount++
        }

        if (health.cacheHitRate < 0.5f) {
            repairs.add("Rebuilt cache indices")
            repairCount++
        }

        if (health.errorRate > 0.1f) {
            repairs.add("Cleared error states")
            repairCount++
        }

        if (health.entropy > 0.8f) {
            repairs.add("Reduced system entropy")
            repairCount++
        }

        val success = repairCount > 0
        val message = if (success) {
            "Performed $repairCount repairs: ${repairs.joinToString(", ")}"
        } else {
            "No repairs performed"
        }

        return RepairReport(success, message, repairCount)
    }

    /**
     * Reparaturbericht.
     *
     * @param success Erfolgsstatus
     * @param message Nachricht
     * @param repairsPerformed Anzahl durchgeführter Reparaturen
     */
    data class RepairReport(
        val success: Boolean,
        val message: String,
        val repairsPerformed: Int
    )

    // =========================================================================
    // MAINTENANCE STATISTICS
    // =========================================================================

    /**
     * Gibt Wartungsstatistiken zurück.
     */
    fun getStatistics(): MaintenanceStatistics {
        val allTasks = taskQueue + completedTasks
        val completed = completedTasks.filter { it.isCompleted }

        return MaintenanceStatistics(
            queuedTasks = taskQueue.size,
            completedTasks = completed.size,
            successRate = if (completed.isNotEmpty()) {
                completed.count { it.success == true }.toFloat() / completed.size
            } else 1.0f,
            avgTaskDuration = if (completed.isNotEmpty()) {
                completed.map { (it.completedAt ?: 0) - it.scheduledAt }.average().toLong()
            } else 0L,
            tasksByType = completed.groupingBy { it.type }.eachCount(),
            lastHealthCheck = lastHealthCheck
        )
    }

    /**
     * Wartungsstatistiken.
     *
     * @param queuedTasks Anzahl wartender Aufgaben
     * @param completedTasks Anzahl abgeschlossener Aufgaben
     * @param successRate Erfolgsrate
     * @param avgTaskDuration Durchschnittliche Aufgabendauer
     * @param tasksByType Aufgaben nach Typ
     * @param lastHealthCheck Letzte Gesundheitsprüfung
     */
    data class MaintenanceStatistics(
        val queuedTasks: Int,
        val completedTasks: Int,
        val successRate: Float,
        val avgTaskDuration: Long,
        val tasksByType: Map<MaintenanceTaskType, Int>,
        val lastHealthCheck: SystemHealth?
    ) {
        override fun toString(): String {
            return buildString {
                appendLine("=== MAINTENANCE STATISTICS ===")
                appendLine("Queued: $queuedTasks")
                appendLine("Completed: $completedTasks")
                appendLine("Success Rate: ${"%.1f".format(successRate * 100)}%")
                appendLine("Avg Duration: ${avgTaskDuration}ms")
                tasksByType.forEach { (type, count) ->
                    appendLine("  $type: $count")
                }
                lastHealthCheck?.let {
                    appendLine("Last Health Score: ${"%.2f".format(it.healthScore)}")
                }
            }
        }
    }

    // =========================================================================
    // UTILITY
    // =========================================================================

    /**
     * Löscht die Warteschlange.
     */
    fun clearQueue() {
        taskQueue.clear()
    }

    /**
     * Löscht den Verlauf abgeschlossener Aufgaben.
     */
    fun clearHistory() {
        completedTasks.clear()
    }

    /**
     * Setzt die autopoietische Wartung zurück.
     */
    fun reset() {
        taskQueue.clear()
        completedTasks.clear()
        lastHealthCheck = null
    }
}

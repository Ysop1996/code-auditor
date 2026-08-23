package de.lifeos.core.storage

/**
 * Zentraler Boot-State-Tracker.
 * Schutzfunktionen (BiometricGate, TeeIntegrityGuard) werden ERST nach
 * Abschluss des BootEngine aktiviert.
 */
object BootStateTracker {
    @Volatile
    var isBootComplete: Boolean = false
        private set

    fun markBootComplete() {
        isBootComplete = true
    }
}

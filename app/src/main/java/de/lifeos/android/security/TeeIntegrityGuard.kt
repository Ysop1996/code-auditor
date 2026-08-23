package de.lifeos.android.security

import android.content.Context
import de.lifeos.core.storage.BootStateTracker
import net.sqlcipher.database.SQLiteDatabase
import java.io.File
import java.nio.ByteBuffer

object TeeIntegrityGuard {
    init {
        System.loadLibrary("lifeos_security")
    }

    private external fun nativeCheckIntegrity(): Boolean
    private external fun nativeEmergencyPanicWipe(buffer: ByteBuffer?, capacity: Long)

    /**
     * Wird ERST nach BootEngine-Abschluss aufgerufen.
     * Prüft Systemintegrität und löscht bei Manipulation.
     */
    fun verifySystemStateOrWipe(context: Context, activeKeyBuffer: ByteBuffer?, db: SQLiteDatabase?) {
        if (!BootStateTracker.isBootComplete) {
            // Boot noch nicht abgeschlossen - Integritütsprüfung überspringen
            return
        }

        val isDebugged = !nativeCheckIntegrity()
        val hasRootMarkers = File("/system/app/Superuser.apk").exists() || File("/system/xbin/su").exists()

        if (isDebugged || hasRootMarkers) {
            db?.close()
            nativeEmergencyPanicWipe(activeKeyBuffer, activeKeyBuffer?.capacity()?.toLong() ?: 0L)
        }
    }
}

package de.lifeos.android.security

import android.content.Context
import net.sqlcipher.database.SQLiteDatabase
import java.io.File
import java.nio.ByteBuffer

object TeeIntegrityGuard {
    init {
        System.loadLibrary("lifeos_security")
    }

    private external fun nativeCheckIntegrity(): Boolean
    private external fun nativeEmergencyPanicWipe(buffer: ByteBuffer?, capacity: Long)

    fun verifySystemStateOrWipe(context: Context, activeKeyBuffer: ByteBuffer?, db: SQLiteDatabase?) {
        val isDebugged = !nativeCheckIntegrity()
        val hasRootMarkers = File("/system/app/Superuser.apk").exists() || File("/system/xbin/su").exists()

        if (isDebugged || hasRootMarkers) {
            db?.close()
            nativeEmergencyPanicWipe(activeKeyBuffer, activeKeyBuffer?.capacity()?.toLong() ?: 0L)
        }
    }
}

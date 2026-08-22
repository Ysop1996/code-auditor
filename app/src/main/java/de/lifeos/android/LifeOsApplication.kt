package de.lifeos.android

import android.app.Application
import android.content.ComponentCallbacks2
import de.lifeos.android.security.BlackboxMemoryBridge
import de.lifeos.android.security.TeeIntegrityGuard
import net.sqlcipher.database.SQLiteDatabase
import java.nio.ByteBuffer

class LifeOsApplication : Application() {

    companion object {
        lateinit var instance: LifeOsApplication
            private set
        var volatileKeyBuffer: ByteBuffer? = null
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        SQLiteDatabase.loadLibs(this)
        TeeIntegrityGuard.verifySystemStateOrWipe(this, volatileKeyBuffer, null)
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (level >= ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN) {
            volatileKeyBuffer?.let { buf ->
                BlackboxMemoryBridge.nativeSecureWipe(buf, buf.capacity().toLong())
            }
        }
    }
}

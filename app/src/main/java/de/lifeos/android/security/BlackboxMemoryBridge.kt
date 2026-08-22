package de.lifeos.android.security

import java.nio.ByteBuffer

object BlackboxMemoryBridge {
    init {
        System.loadLibrary("lifeos_security")
    }

    external fun nativeSecureWipe(buffer: ByteBuffer, capacity: Long)
}

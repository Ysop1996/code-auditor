package de.lifeos.core.storage

import net.sqlcipher.database.SQLiteDatabase
import java.nio.ByteBuffer
import java.nio.ByteOrder

class ZeroCopyRingBuffer(
    private val capacity: Int = 128,
    private val vectorDim: Int = 32,
    private val vaultDb: SQLiteDatabase
) {
    private val directBuffer: ByteBuffer = ByteBuffer.allocateDirect(capacity * vectorDim * 4)
        .order(ByteOrder.nativeOrder())
    
    private var writeIndex = 0
    private var pendingCount = 0

    @Synchronized
    fun pushDeltaVector(vector: FloatArray, frictionW: Double) {
        if (vector.size != vectorDim) return
        val offset = (writeIndex % capacity) * vectorDim * 4
        directBuffer.position(offset)
        for (value in vector) {
            directBuffer.putFloat(value)
        }

        writeIndex++
        pendingCount = minOf(pendingCount + 1, capacity)

        if (frictionW > 1.8 || pendingCount >= capacity) {
            flushToVault()
        }
    }

    @Synchronized
    fun flushToVault() {
        if (pendingCount == 0) return

        vaultDb.beginTransaction()
        try {
            val stmt = vaultDb.compileStatement(
                "INSERT INTO semantic_nodes (id, sha256, payload, mass, phase_x, phase_y, phase_z, last_updated) VALUES (?, ?, ?, ?, ?, ?, ?, ?)"
            )
            val now = System.currentTimeMillis()

            for (i in 0 until pendingCount) {
                val idx = (writeIndex - pendingCount + i + capacity) % capacity
                val offset = idx * vectorDim * 4
                directBuffer.position(offset)

                val x = directBuffer.float
                val y = directBuffer.float
                val z = directBuffer.float

                stmt.bindString(1, "RING_DELTA_${now}_$i")
                stmt.bindString(2, "HASH_${idx}_$now")
                stmt.bindString(3, "EPHEMERAL_STREAM_DATA")
                stmt.bindDouble(4, 0.5)
                stmt.bindDouble(5, x.toDouble())
                stmt.bindDouble(6, y.toDouble())
                stmt.bindDouble(7, z.toDouble())
                stmt.bindLong(8, now)
                stmt.executeInsert()
                stmt.clearBindings()
            }

            vaultDb.setTransactionSuccessful()
            pendingCount = 0
        } finally {
            vaultDb.endTransaction()
        }
    }
}

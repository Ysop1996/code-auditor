package de.lifeos.core.storage

import java.io.File
import java.io.RandomAccessFile
import java.security.SecureRandom

object SecureFileShredder {
    private val random = SecureRandom()

    fun shredAndUnlink(file: File): Boolean {
        if (!file.exists() || !file.isFile) return false
        return runCatching {
            val length = file.length()
            if (length > 0) {
                RandomAccessFile(file, "rws").use { raf ->
                    val buffer = ByteArray(8192)

                    // Pass 1: Nullung
                    raf.seek(0)
                    var written = 0L
                    while (written < length) {
                        val toWrite = minOf(buffer.size.toLong(), length - written).toInt()
                        raf.write(buffer, 0, toWrite)
                        written += toWrite
                    }

                    // Pass 2: Kryptografisches Rauschen
                    raf.seek(0)
                    written = 0L
                    while (written < length) {
                        random.nextBytes(buffer)
                        val toWrite = minOf(buffer.size.toLong(), length - written).toInt()
                        raf.write(buffer, 0, toWrite)
                        written += toWrite
                    }

                    // Pass 3: Finale Nullung & Truncate
                    val zeros = ByteArray(8192)
                    raf.seek(0)
                    written = 0L
                    while (written < length) {
                        val toWrite = minOf(zeros.size.toLong(), length - written).toInt()
                        raf.write(zeros, 0, toWrite)
                        written += toWrite
                    }
                    raf.setLength(0)
                }
            }
            val renamed = File(file.parentFile, "wiped_${System.currentTimeMillis()}_${random.nextInt(9999)}")
            file.renameTo(renamed)
            renamed.delete()
        }.getOrDefault(false)
    }
}

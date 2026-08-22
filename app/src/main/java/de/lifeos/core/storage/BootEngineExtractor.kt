package de.lifeos.core.storage

import de.lifeos.core.field.AttractorNode
import de.lifeos.core.field.DeterministicFieldEngine
import de.lifeos.core.field.PhaseVector
import net.sqlcipher.database.SQLiteDatabase
import java.io.File
import java.io.FileInputStream
import java.nio.file.Files
import java.security.MessageDigest

class BootEngineExtractor(
    private val vaultDb: SQLiteDatabase,
    private val fieldEngine: DeterministicFieldEngine,
    private val storageRoot: File
) {
    private val fileHashMap = mutableMapOf<String, File>()

    fun executeGenesisScan(sourceDirectory: File) {
        if (!storageRoot.exists()) storageRoot.mkdirs()
        sourceDirectory.walkTopDown()
            .filter { it.isFile && !it.name.startsWith(".") }
            .forEach { file -> processIncomingFile(file) }
    }

    private fun processIncomingFile(file: File) {
        val hash = calculateSHA256(file)

        if (fileHashMap.containsKey(hash)) {
            val original = fileHashMap[hash]!!
            val linkDestination = File(storageRoot, "Link_${file.name}")
            if (!linkDestination.exists()) {
                try {
                    Files.createLink(linkDestination.toPath(), original.toPath())
                } catch (e: Exception) {
                    linkDestination.writeBytes(original.readBytes())
                }
            }
            return
        }

        val content = runCatching { file.readText() }.getOrDefault("")
        val mass = calculateExistentialMass(file.length(), content)

        val targetPath = File(storageRoot, file.name)
        try {
            Files.copy(file.toPath(), targetPath.toPath())
        } catch (e: Exception) {
            targetPath.writeBytes(file.readBytes())
        }
        fileHashMap[hash] = targetPath

        val vectorArray = FloatArray(32) { i ->
            val byteVal = if (i < hash.length / 2) hash.substring(i * 2, i * 2 + 2).toInt(16) else 0
            (byteVal / 255.0f) * 2.0f - 1.0f
        }
        val phaseVector = PhaseVector(vectorArray).normalize()

        vaultDb.execSQL(
            "INSERT OR REPLACE INTO semantic_nodes VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
            arrayOf(file.name, hash, content.take(500), mass, phaseVector.dim[0], phaseVector.dim[1], phaseVector.dim[2], System.currentTimeMillis())
        )

        fieldEngine.registerNode(
            AttractorNode(
                id = file.name,
                payload = content.take(500),
                position = phaseVector,
                mass = mass
            )
        )
    }

    private fun calculateExistentialMass(bytes: Long, content: String): Float {
        var baseMass = 1.0f + (bytes / (1024f * 1024f)) * 0.1f
        if (content.contains("Kündigung", true) || content.contains("Frist", true) || content.contains("Rechnung", true)) {
            baseMass += 2.5f
        }
        return baseMass
    }

    private fun calculateSHA256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        FileInputStream(file).use { fis ->
            val buffer = ByteArray(8192)
            var bytesRead: Int
            while (fis.read(buffer).also { bytesRead = it } != -1) {
                digest.update(buffer, 0, bytesRead)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}

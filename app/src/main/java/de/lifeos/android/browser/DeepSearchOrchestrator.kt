package de.lifeos.android.browser

import android.content.Context
import de.lifeos.core.field.AttractorNode
import de.lifeos.core.field.DeterministicFieldEngine
import de.lifeos.core.field.PhaseVector
import net.sqlcipher.database.SQLiteDatabase
import java.security.MessageDigest

class DeepSearchOrchestrator(
    private val context: Context,
    private val fieldEngine: DeterministicFieldEngine,
    private val vaultDb: SQLiteDatabase
) {
    private val browserSandbox = AnonymousBrowserSandbox(context)
    private val spectralBridge = SpectralSearchBridge(fieldEngine)

    fun startAnonymousSearch(
        query: String,
        onPreviewReady: (title: String, snippet: String, frictionW: Double) -> Unit
    ) {
        browserSandbox.initializeIsolatedSession(useTorProxy = false)
        browserSandbox.executeAnonymousSearch(
            query = query,
            onPageLoaded = { title, _ ->
                browserSandbox.extractCleanedDomText { cleanedContent ->
                    browserSandbox.captureFrameSignal { frameSignal ->
                        val frictionW = spectralBridge.processBrowserFrame(frameSignal)
                        onPreviewReady(title, cleanedContent.take(300), frictionW)
                    }
                }
            },
            onFrameRendered = { frameSignal -> spectralBridge.processBrowserFrame(frameSignal) }
        )
    }

    fun confirmAndAssimilate(queryTitle: String, finalContent: String) {
        val hash = MessageDigest.getInstance("SHA-256")
            .digest(finalContent.toByteArray())
            .joinToString("") { "%02x".format(it) }

        val nodeId = "SEARCH_${hash.take(12)}"
        val coords = FloatArray(32) { idx ->
            if (idx < hash.length / 2) (hash.substring(idx * 2, idx * 2 + 2).toInt(16) / 255.0f) * 2.0f - 1.0f else 0.0f
        }
        val phaseVector = PhaseVector(coords).normalize()

        vaultDb.execSQL(
            "INSERT OR REPLACE INTO semantic_nodes VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
            arrayOf(nodeId, hash, finalContent, 2.0, phaseVector.dim[0], phaseVector.dim[1], phaseVector.dim[2], System.currentTimeMillis())
        )

        fieldEngine.registerNode(
            AttractorNode(
                id = nodeId,
                payload = "$queryTitle: $finalContent",
                position = phaseVector,
                mass = 2.0f,
                isTerminal = false
            )
        )

        fieldEngine.currentRho = maxOf(0.01f, fieldEngine.currentRho - 0.35f)
        browserSandbox.destroyAndWipeSession()
    }

    fun abortSearch() {
        browserSandbox.destroyAndWipeSession()
    }
}

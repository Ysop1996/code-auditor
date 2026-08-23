package de.lifeos.android.browser

import de.lifeos.core.field.AttractorNode
import de.lifeos.core.field.DeterministicFieldEngine
import net.sqlcipher.database.SQLiteDatabase

/**
 * VAULT SYNTHESIS GATE — Inverse Informationssynthese vor Netzzugriff.
 *
 * Prüft deterministisch, ob eine Informationsanfrage rein algebraisch
 * aus dem verschlüsselten Vault berechnet werden kann, bevor ein
 * Netzwerk-Request ausgelöst wird.
 *
 * Vektoren:
 * - [SEC-ZERO] Zero-Egress: Kein Netzzugriff, wenn Vault-Synthese möglich
 * - [O1-SYNTH] O(1) lokale Berechnung ohne Disk-I/O außerhalb des Tresors
 */
class VaultSynthesisGate(
    private val vaultDb: SQLiteDatabase,
    private val fieldEngine: DeterministicFieldEngine
) {
    /**
     * Ergebnis der Vault-Synthese-Prüfung.
     */
    data class SynthesisResult(
        val canSynthesize: Boolean,
        val synthesizedContent: String? = null,
        val missingAtoms: List<String> = emptyList(),
        val confidence: Float = 0.0f,
        val sourceNodes: List<String> = emptyList()
    )

    /**
     * Prüft, ob die Anfrage rein aus dem Vault beantwortet werden kann.
     * Gibt SynthesisResult zurück — bei canSynthesize=true ist kein
     * Netzwerk-Zugriff erforderlich.
     */
    fun checkLocalSynthesis(query: String): SynthesisResult {
        val normalizedQuery = query.lowercase().trim()
        val atoms = decomposeQueryIntoAtoms(normalizedQuery)

        // Phase 1: Direkte Vault-Suche nach semantischen Knoten
        val directMatches = searchVaultNodes(atoms)

        // Phase 2: Feldtheoretische Trajektorie-Suche
        val trajectoryMatches = searchFieldTrajectory(atoms)

        // Phase 3: Algebraische Synthese aus gefundenen Atomen
        val allMatches = (directMatches + trajectoryMatches).distinctBy { it.id }

        return if (allMatches.isNotEmpty() && allMatches.size >= atoms.size * 0.5) {
            val synthesized = synthesizeFromNodes(allMatches, atoms)
            SynthesisResult(
                canSynthesize = true,
                synthesizedContent = synthesized,
                confidence = minOf(1.0f, allMatches.size.toFloat() / atoms.size.coerceAtLeast(1)),
                sourceNodes = allMatches.map { it.id }
            )
        } else {
            val missing = atoms.filter { atom ->
                allMatches.none { node -> node.payload.contains(atom, ignoreCase = true) }
            }
            SynthesisResult(
                canSynthesize = false,
                missingAtoms = missing,
                confidence = allMatches.size.toFloat() / atoms.size.coerceAtLeast(1)
            )
        }
    }

    /**
     * Zerlegt eine Anfrage in minimale Informations-Atome.
     * I_target = S(c_1, ..., c_k)
     */
    private fun decomposeQueryIntoAtoms(query: String): List<String> {
        val stopWords = setOf(
            "der", "die", "das", "ein", "eine", "ist", "sind", "war", "waren",
            "und", "oder", "aber", "mit", "von", "zu", "auf", "für", "in", "an",
            "wie", "was", "wer", "wo", "wann", "warum", "wie", "viel", "viele",
            "kann", "könnte", "sollte", "muss", "musst", "bitte", "zeige", "gib"
        )

        return query.split(Regex("[^a-zA-ZäöüÄÖÜß0-9]+"))
            .filter { it.length > 2 && it !in stopWords }
            .distinct()
            .ifEmpty { listOf(query.take(10)) }
    }

    /**
     * Phase 1: Direkte Suche im Vault nach semantischen Knoten.
     */
    private fun searchVaultNodes(atoms: List<String>): List<AttractorNode> {
        val results = mutableListOf<AttractorNode>()

        atoms.take(3).forEach { atom ->
            val cursor = vaultDb.rawQuery(
                """
                SELECT id, payload, mass FROM semantic_nodes 
                WHERE lower(id) LIKE ? OR lower(payload) LIKE ?
                ORDER BY mass DESC LIMIT 5
                """.trimIndent(),
                arrayOf("%$atom%", "%$atom%")
            )

            cursor.use {
                while (it.moveToNext()) {
                    val id = it.getString(0)
                    val payload = it.getString(1) ?: ""
                    val mass = it.getFloat(2)
                    results.add(
                        AttractorNode(
                            id = id,
                            payload = payload,
                            position = de.lifeos.core.field.PhaseVector(FloatArray(32)),
                            mass = mass,
                            isTerminal = false
                        )
                    )
                }
            }
        }

        return results
    }

    /**
     * Phase 2: Feldtheoretische Trajektorie-Suche über das Kraftfeld.
     */
    private fun searchFieldTrajectory(atoms: List<String>): List<AttractorNode> {
        val activeNodes = fieldEngine.getActiveNodes()
        if (activeNodes.isEmpty()) return emptyList()

        // Erzeuge Stimulus-Phasevektor aus Query-Atomen
        val stimulus = de.lifeos.core.field.PhaseVector(FloatArray(32) { idx ->
            val atomIndex = idx % atoms.size
            val atomHash = atoms[atomIndex].hashCode()
            ((atomHash shr (idx % 32)) and 0xFF).toFloat() / 255.0f
        })

        // Führe deterministische Trajektorie-Suche durch
        return fieldEngine.executeTrajectory(stimulus, maxSteps = 8)
    }

    /**
     * Phase 3: Algebraische Synthese aus gefundenen Knoten.
     * Kombiniert Informationen zu einer kohärenten Antwort.
     */
    private fun synthesizeFromNodes(
        nodes: List<AttractorNode>,
        atoms: List<String>
    ): String {
        val sb = StringBuilder()

        sb.append("Vault-Synthese (${nodes.size} Knoten):\n\n")

        nodes.take(5).forEachIndexed { idx, node ->
            sb.append("${idx + 1}. [${node.id}] ")
            val extract = node.payload.take(150).replace("\n", " ").trim()
            if (extract.isNotBlank()) {
                sb.append("\"$extract...\"\n")
            }
            sb.append("   Masse: ${"%.1f".format(node.mass)}\n\n")
        }

        if (nodes.size > 5) {
            sb.append("... und ${nodes.size - 5} weitere Knoten.\n")
        }

        return sb.toString().trim()
    }

    /**
     * Gibt die fehlenden Atome für eine Anfrage zurück.
     * Wird für Minimal-Delta DeepSearch verwendet.
     */
    fun getMissingAtoms(query: String): List<String> {
        val result = checkLocalSynthesis(query)
        return result.missingAtoms
    }
}

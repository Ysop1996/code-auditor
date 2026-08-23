package de.lifeos.core.linguistics

import net.sqlcipher.database.SQLiteDatabase
import java.security.MessageDigest

/**
 * Colloquial Mimicry Engine — Extrahiert und reproduziert den individuellen
 * Sprachstil des Nutzers aus Kommunikationsereignissen im Tresor.
 *
 * Analysiert: Satzlänge, Interpunktion, Emoji-Nutzung, typische Phrasen,
 * Anredeformen, Abschiedsformeln.
 *
 * FIXED:
 * - @Volatile für Thread-Safety
 * - Deterministische Phrase-Auswahl via Hash (kein random())
 * - Precompiled Regex-Patterns
 * - Korrigierte Closing-Erkennung
 */
data class ChatStyleFingerprint(
    val avgSentenceLength: Float,
    val punctuationDensity: Float,
    val emojiFrequency: Float,
    val typicalPhrases: List<String>,
    val greetingStyle: String,
    val closingStyle: String,
    val capitalizationPreference: Float
)

class ColloquialMimicryEngine(private val vaultDb: SQLiteDatabase) {

    // SEV-1 Fix: Thread-safe cache
    @Volatile
    private var cachedFingerprint: ChatStyleFingerprint? = null

    // SEV-2 Fix: Precompiled regex patterns
    private companion object {
        // SEV-3 Fix: Comprehensive emoji coverage
        val EMOJI_PATTERN = Regex(
            "[\\uD83C-\\uDBFF\\uDC00-\\uDFFF" +  // Surrogate Pairs
            "\\u2600-\\u26FF" +                    // Misc Symbols
            "\\u2700-\\u27BF" +                    // Dingbats
            "\\uFE00-\\uFE0F" +                    // Variation Selectors
            "]+"
        )
        val SENTENCE_SPLIT = Regex("[.!?]+")
        val WORD_SPLIT = Regex("\\s+")
        val MULTI_SPACE = Regex("\\s+")

        // Particle frequency learning — track colloquial particles
        val PARTICLE_PATTERN = Regex(
            "\\b(ja|nein|doch|halt|eigentlich|irgendwie|quasi|sowieso|halt|genau|naja|tja|also|grad|gerade|vielleicht|wahrscheinlich|eher|eigentlich|irgendwie|sowieso|halt|doch|ja|nein|nö|jo|klar|okay|ok|super|mega|total|voll|echt|wirklich|bestimmt|sicher|vielleicht|wahrscheinlich|eher|eigentlich|irgendwie|sowieso|halt|doch|ja|nein)\\b",
            RegexOption.IGNORE_CASE
        )
    }

    // Particle frequency state — tracks usage counts per particle
    @Volatile
    private var particleFrequencies = mutableMapOf<String, Int>()

    /**
     * Extrahiert den Stil-Fingerprint aus OUTBOUND-Kommunikationsereignissen.
     */
    fun extractUserStyleFingerprint(): ChatStyleFingerprint {
        cachedFingerprint?.let { return it }

        val payloads = mutableListOf<String>()

        runCatching {
            vaultDb.rawQuery(
                "SELECT payload FROM communication_events WHERE direction = 'OUTBOUND' AND payload IS NOT NULL ORDER BY timestamp_epoch DESC LIMIT 200",
                null
            ).use { cursor ->
                while (cursor.moveToNext()) {
                    cursor.getString(0)?.let { payloads.add(it) }
                }
            }
        }

        val fingerprint = if (payloads.isEmpty()) {
            ChatStyleFingerprint(
                avgSentenceLength = 80f,
                punctuationDensity = 0.05f,
                emojiFrequency = 0.02f,
                typicalPhrases = listOf("Bin unterwegs", "Melde mich", "Bis bald"),
                greetingStyle = "Hey",
                closingStyle = "Lg",
                capitalizationPreference = 0.7f
            )
        } else {
            ChatStyleFingerprint(
                avgSentenceLength = calculateAvgSentenceLength(payloads),
                punctuationDensity = calculatePunctuationDensity(payloads),
                emojiFrequency = calculateEmojiFrequency(payloads),
                typicalPhrases = extractTypicalPhrases(payloads),
                greetingStyle = extractGreetingStyle(payloads),
                closingStyle = extractClosingStyle(payloads),
                capitalizationPreference = calculateCapitalizationPreference(payloads)
            )
        }

        cachedFingerprint = fingerprint
        return fingerprint
    }

    /**
     * Formt einen Text in den Stil des Nutzers um.
     */
    fun stylizeUtterance(
        rawText: String,
        targetRegister: LinguisticRegister = LinguisticRegister.NATURAL_STANDARD
    ): String {
        val fingerprint = extractUserStyleFingerprint()
        var result = rawText.trim()

        when (targetRegister) {
            LinguisticRegister.DUDEN_FORMAL -> {
                result = result.replace(EMOJI_PATTERN, "")
                result = result.replaceFirstChar { it.uppercase() }
            }
            LinguisticRegister.NATURAL_STANDARD -> {
                result = if (fingerprint.capitalizationPreference < 0.5f) {
                    result.lowercase()
                } else {
                    result.replaceFirstChar { it.uppercase() }
                }
            }
            LinguisticRegister.PEER_COLLOQUIAL -> {
                if (fingerprint.capitalizationPreference < 0.3f) {
                    result = result.lowercase()
                }
                // SEV-1 Fix: Deterministic phrase selection via hash (not random)
                if (fingerprint.typicalPhrases.isNotEmpty() && result.length < 50) {
                    val phraseIndex = (result.hashCode() and 0x7FFFFFFF) % fingerprint.typicalPhrases.size
                    val phrase = fingerprint.typicalPhrases[phraseIndex]
                    if (!result.contains(phrase, ignoreCase = true)) {
                        result = "$result $phrase"
                    }
                }
                // Particle frequency learning — apply learned colloquial particles
                result = applyParticleFrequency(result)
            }
        }

        return result
    }

    /**
     * Formalisiert einen umgangssprachlichen Text in Standardsprache.
     */
    fun formalizeUtterance(colloquialText: String): String {
        var result = colloquialText.trim()

        val colloquialReplacements = mapOf(
            "gibts" to "gibt es",
            "ham" to "haben",
            "willste" to "willst du",
            "kannste" to "kannst du",
            "mussste" to "musst du",
            "gehste" to "gehst du",
            "haste" to "hast du",
            "warda" to "wurdest du",
            "würdste" to "würdest du",
            "biste" to "bist du",
            "solls" to "soll es",
            "kommste" to "kommst du",
            "wirste" to "wirst du",
            "is" to "ist",
            "hamwa" to "haben wir",
            "gehts" to "geht es",
            "wieso" to "wie soll",
            "weshalb" to "warum",
            "weswegen" to "warum"
        )

        colloquialReplacements.forEach { (colloquial, formal) ->
            result = result.replace(Regex("\\b$colloquial\\b", RegexOption.IGNORE_CASE), formal)
        }

        result = result.replaceFirstChar { it.uppercase() }
        result = result.replace(MULTI_SPACE, " ")

        return result
    }

    // =========================================================================
    // PARTICLE FREQUENCY LEARNING — Partikel-Häufigkeit aus Kommunikation lernen
    // =========================================================================

    /**
     * Lernt Partikel-Häufigkeiten aus OUTBOUND-Kommunikationsereignissen.
     * Wird periodisch vom Styling-Engine aufgerufen.
     */
    fun learnParticleFrequencies() {
        val frequencies = mutableMapOf<String, Int>()

        runCatching {
            vaultDb.rawQuery(
                "SELECT payload FROM communication_events WHERE direction = 'OUTBOUND' AND payload IS NOT NULL ORDER BY timestamp_epoch DESC LIMIT 500",
                null
            ).use { cursor ->
                while (cursor.moveToNext()) {
                    cursor.getString(0)?.let { payload ->
                        PARTICLE_PATTERN.findAll(payload).forEach { match ->
                            val particle = match.value.lowercase()
                            frequencies[particle] = frequencies.getOrDefault(particle, 0) + 1
                        }
                    }
                }
            }
        }

        particleFrequencies = frequencies
    }

    /**
     * Gibt die Top-N Partikel nach Häufigkeit zurück.
     */
    fun getTopParticles(limit: Int = 10): List<Pair<String, Int>> {
        return particleFrequencies.entries
            .sortedByDescending { it.value }
            .take(limit)
            .map { it.key to it.value }
    }

    /**
     * Wendet gelernte Partikel-Häufigkeit auf einen Text an.
     * Fügt häufige Partikel natürlich in den Text ein.
     */
    fun applyParticleFrequency(text: String): String {
        if (particleFrequencies.isEmpty() || text.length > 100) return text

        val topParticles = getTopParticles(5)
        if (topParticles.isEmpty()) return text

        // Wähle das häufigste Partikel basierend auf Hash (deterministisch)
        val particleIndex = (text.hashCode() and 0x7FFFFFFF) % topParticles.size
        val particle = topParticles[particleIndex].first

        // Füge Partikel am Satzende ein, falls nicht bereits vorhanden
        return if (!text.lowercase().contains(particle)) {
            "$text $particle"
        } else {
            text
        }
    }

    // =========================================================================
    // PRIVATE ANALYSE-METHODEN
    // =========================================================================

    private fun calculateAvgSentenceLength(payloads: List<String>): Float {
        val totalChars = payloads.sumOf { it.length }
        val totalSentences = payloads.sumOf { maxOf(1, it.count { c -> c in ".!?" }) }
        return if (totalSentences > 0) totalChars.toFloat() / totalSentences else 80f
    }

    private fun calculatePunctuationDensity(payloads: List<String>): Float {
        val totalChars = payloads.sumOf { it.length }.coerceAtLeast(1)
        val punctCount = payloads.sumOf { it.count { c -> c in ".,;:!?" } }
        return punctCount.toFloat() / totalChars
    }

    private fun calculateEmojiFrequency(payloads: List<String>): Float {
        val totalChars = payloads.sumOf { it.length }.coerceAtLeast(1)
        val emojiCount = payloads.sumOf { EMOJI_PATTERN.findAll(it).count() }
        return (emojiCount.toFloat() / totalChars) * 100f
    }

    private fun extractTypicalPhrases(payloads: List<String>): List<String> {
        val phraseFrequency = mutableMapOf<String, Int>()

        payloads.forEach { payload ->
            val words = payload.split(WORD_SPLIT).filter { it.length > 2 }
            for (i in 0 until words.size - 1) {
                val bigram = "${words[i]} ${words[i + 1]}"
                phraseFrequency[bigram] = phraseFrequency.getOrDefault(bigram, 0) + 1
            }
        }

        return phraseFrequency.entries
            .filter { it.value >= 2 }
            .sortedByDescending { it.value }
            .take(5)
            .map { it.key }
    }

    private fun extractGreetingStyle(payloads: List<String>): String {
        val greetings = listOf("hey", "hallo", "hi", "moin", "servus", "guten tag", "liebe grüße")
        val foundGreetings = mutableMapOf<String, Int>()

        payloads.forEach { payload ->
            val lower = payload.lowercase()
            greetings.forEach { greeting ->
                if (lower.startsWith(greeting)) {
                    foundGreetings[greeting] = foundGreetings.getOrDefault(greeting, 0) + 1
                }
            }
        }

        return foundGreetings.maxByOrNull { it.value }?.key ?: "Hey"
    }

    // SEV-3 Fix: Corrected closing detection - only check endsWith
    private fun extractClosingStyle(payloads: List<String>): String {
        val closings = listOf("lg", "grüße", "liebe grüße", "viele grüße", "bis bald", "ciao", "tschüss", "mach's gut")
        val foundClosings = mutableMapOf<String, Int>()

        payloads.forEach { payload ->
            val lower = payload.lowercase().trim()
            closings.forEach { closing ->
                if (lower.endsWith(closing)) {
                    foundClosings[closing] = foundClosings.getOrDefault(closing, 0) + 1
                }
            }
        }

        return foundClosings.maxByOrNull { it.value }?.key ?: "Lg"
    }

    private fun calculateCapitalizationPreference(payloads: List<String>): Float {
        if (payloads.isEmpty()) return 0.7f

        var capitalizedSentences = 0
        var totalSentences = 0

        payloads.forEach { payload ->
            val sentences = payload.split(SENTENCE_SPLIT).filter { it.trim().isNotEmpty() }
            sentences.forEach { sentence ->
                totalSentences++
                if (sentence.trim().firstOrNull()?.isUpperCase() == true) {
                    capitalizedSentences++
                }
            }
        }

        return if (totalSentences > 0) capitalizedSentences.toFloat() / totalSentences else 0.7f
    }
}

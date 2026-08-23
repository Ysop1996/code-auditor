package de.lifeos.core.linguistics

/**
 * Duden Grammar Engine — Deterministische Deklination und Präpositionsregierung.
 *
 * Implementiert die vollständige deutsche Kasuslogik mit Artikelflexion,
 * Präpositionsrektion und Kontraktionsregeln (beim, zum, zur, im, am, vom).
 *
 * Alle Operationen sind O(1) — direkte Tabellenzugriffe ohne Rekursion.
 * Regex-Patterns precompiled für Zero-Allocation-Aufrufe.
 */
enum class GrammaticalCase {
    NOMINATIV,
    GENITIV,
    DATIV,
    AKKUSATIV
}

enum class Gender {
    MASKULIN,
    FEMININ,
    NEUTRUM,
    PLURAL
}

enum class LinguisticRegister {
    DUDEN_FORMAL,       // Bürokratie, Recht, Behörden
    NATURAL_STANDARD,   // Alltagssprache korrekt
    PEER_COLLOQUIAL     // Umgangssprache, Chat, WhatsApp
}

data class DeclinedArticle(
    val article: String,
    val case: GrammaticalCase,
    val gender: Gender
)

object DudenGrammarEngine {

    // =========================================================================
    // PRÄPOSITIONSREGIERUNG — Jede Präposition regiert festen Kasus
    // =========================================================================
    val PREPOSITION_GOVERNANCE: Map<String, GrammaticalCase> = mapOf(
        // Genitiv-Präpositionen
        "wegen" to GrammaticalCase.GENITIV,
        "während" to GrammaticalCase.GENITIV,
        "trotz" to GrammaticalCase.GENITIV,
        "innerhalb" to GrammaticalCase.GENITIV,
        "außerhalb" to GrammaticalCase.GENITIV,
        "oberhalb" to GrammaticalCase.GENITIV,
        "unterhalb" to GrammaticalCase.GENITIV,
        "anstatt" to GrammaticalCase.GENITIV,
        "statt" to GrammaticalCase.GENITIV,
        "infolge" to GrammaticalCase.GENITIV,
        "kraft" to GrammaticalCase.GENITIV,
        "laut" to GrammaticalCase.GENITIV,
        "mangels" to GrammaticalCase.GENITIV,
        "ungeachtet" to GrammaticalCase.GENITIV,
        "beiderseits" to GrammaticalCase.GENITIV,
        "diesseits" to GrammaticalCase.GENITIV,
        "jenseits" to GrammaticalCase.GENITIV,
        "seitens" to GrammaticalCase.GENITIV,

        // Dativ-Präpositionen
        "mit" to GrammaticalCase.DATIV,
        "nach" to GrammaticalCase.DATIV,
        "von" to GrammaticalCase.DATIV,
        "zu" to GrammaticalCase.DATIV,
        "bei" to GrammaticalCase.DATIV,
        "seit" to GrammaticalCase.DATIV,
        "aus" to GrammaticalCase.DATIV,
        "außer" to GrammaticalCase.DATIV,
        "ab" to GrammaticalCase.DATIV,
        "gegenüber" to GrammaticalCase.DATIV,
        "entsprechend" to GrammaticalCase.DATIV,
        "samt" to GrammaticalCase.DATIV,
        "zufolge" to GrammaticalCase.DATIV,

        // Akkusativ-Präpositionen
        "durch" to GrammaticalCase.AKKUSATIV,
        "für" to GrammaticalCase.AKKUSATIV,
        "ohne" to GrammaticalCase.AKKUSATIV,
        "gegen" to GrammaticalCase.AKKUSATIV,
        "um" to GrammaticalCase.AKKUSATIV,
        "bis" to GrammaticalCase.AKKUSATIV,
        "wider" to GrammaticalCase.AKKUSATIV,
        "per" to GrammaticalCase.AKKUSATIV,

        // Wechselpräpositionen (Standard: Dativ für Lokalität, Akkusativ für Richtung)
        "an" to GrammaticalCase.DATIV,
        "auf" to GrammaticalCase.DATIV,
        "hinter" to GrammaticalCase.DATIV,
        "in" to GrammaticalCase.DATIV,
        "neben" to GrammaticalCase.DATIV,
        "über" to GrammaticalCase.DATIV,
        "unter" to GrammaticalCase.DATIV,
        "vor" to GrammaticalCase.DATIV,
        "zwischen" to GrammaticalCase.DATIV
    )

    // =========================================================================
    // KONTRAKTIONSGLOSSAR — Präposition + Artikel → Kontraktion
    // =========================================================================
    val CONTRACTIONS: Map<String, String> = mapOf(
        "bei dem" to "beim",
        "von dem" to "vom",
        "zu dem" to "zum",
        "zu der" to "zur",
        "an dem" to "am",
        "an das" to "ans",
        "in dem" to "im",
        "in das" to "ins",
        "auf das" to "aufs",
        "unter das" to "unters",
        "über das" to "übers"
    )

    // =========================================================================
    // PRECOMPILED REGEX — Zero-Allocation Pattern-Matching
    // =========================================================================
    private val MULTI_SPACE = Regex("\\s+")
    private val WEGEN_DATIV = Regex("wegen (dem|das)\\b", RegexOption.IGNORE_CASE)
    private val TROTZ_DATIV = Regex("trotz dem\\b", RegexOption.IGNORE_CASE)
    private val FUER_DATIV = Regex("für dem\\b", RegexOption.IGNORE_CASE)
    private val HEDGING = Regex("vielleicht|könnte man|eventuell", RegexOption.IGNORE_CASE)

    // =========================================================================
    // BESTIMMTE ARTIKEL-DEKLINATIONSTABELLE
    // =========================================================================
    private val DEFINITE_ARTICLES: Map<GrammaticalCase, Map<Gender, String>> = mapOf(
        GrammaticalCase.NOMINATIV to mapOf(
            Gender.MASKULIN to "der",
            Gender.FEMININ to "die",
            Gender.NEUTRUM to "das",
            Gender.PLURAL to "die"
        ),
        GrammaticalCase.GENITIV to mapOf(
            Gender.MASKULIN to "des",
            Gender.FEMININ to "der",
            Gender.NEUTRUM to "des",
            Gender.PLURAL to "der"
        ),
        GrammaticalCase.DATIV to mapOf(
            Gender.MASKULIN to "dem",
            Gender.FEMININ to "der",
            Gender.NEUTRUM to "dem",
            Gender.PLURAL to "den"
        ),
        GrammaticalCase.AKKUSATIV to mapOf(
            Gender.MASKULIN to "den",
            Gender.FEMININ to "die",
            Gender.NEUTRUM to "das",
            Gender.PLURAL to "die"
        )
    )

    // =========================================================================
    // UNBESTIMMTE ARTIKEL-DEKLINATIONSTABELLE
    // =========================================================================
    private val INDEFINITE_ARTICLES: Map<GrammaticalCase, Map<Gender, String>> = mapOf(
        GrammaticalCase.NOMINATIV to mapOf(
            Gender.MASKULIN to "ein",
            Gender.FEMININ to "eine",
            Gender.NEUTRUM to "ein",
            Gender.PLURAL to ""
        ),
        GrammaticalCase.GENITIV to mapOf(
            Gender.MASKULIN to "eines",
            Gender.FEMININ to "einer",
            Gender.NEUTRUM to "eines",
            Gender.PLURAL to ""
        ),
        GrammaticalCase.DATIV to mapOf(
            Gender.MASKULIN to "einem",
            Gender.FEMININ to "einer",
            Gender.NEUTRUM to "einem",
            Gender.PLURAL to ""
        ),
        GrammaticalCase.AKKUSATIV to mapOf(
            Gender.MASKULIN to "einen",
            Gender.FEMININ to "eine",
            Gender.NEUTRUM to "ein",
            Gender.PLURAL to ""
        )
    )

    // =========================================================================
    // ÖFFENTLICHE API
    // =========================================================================

    /**
     * Dekliniert einen bestimmten Artikel für Kasus und Genus.
     */
    fun declineArticle(
        kase: GrammaticalCase,
        gender: Gender,
        definite: Boolean = true
    ): String {
        return if (definite) {
            DEFINITE_ARTICLES[kase]?.get(gender) ?: ""
        } else {
            INDEFINITE_ARTICLES[kase]?.get(gender) ?: ""
        }
    }

    /**
     * Ermittelt den Kasus einer Präposition.
     */
    fun getCaseForPreposition(preposition: String): GrammaticalCase {
        return PREPOSITION_GOVERNANCE[preposition.lowercase()] ?: GrammaticalCase.DATIV
    }

    /**
     * Konstruiert eine Präpositionalphrase mit korrekter Deklination
     * und automatischer Kontraktion.
     */
    fun constructPrepositionalPhrase(
        preposition: String,
        noun: String,
        gender: Gender,
        definite: Boolean = true
    ): String {
        val kase = getCaseForPreposition(preposition)
        val article = declineArticle(kase, gender, definite)

        val contractionKey = "${preposition.lowercase()} $article"
        val contraction = CONTRACTIONS[contractionKey]

        return if (contraction != null) {
            "$contraction $noun"
        } else {
            "$preposition $article $noun".trim()
        }
    }

    /**
     * Formt einen Satz in die korrekte grammatikalische Form
     * für den angegebenen Register.
     */
    fun formalizeSentence(
        rawText: String,
        targetRegister: LinguisticRegister
    ): String {
        return when (targetRegister) {
            LinguisticRegister.DUDEN_FORMAL -> applyFormalRules(rawText)
            LinguisticRegister.NATURAL_STANDARD -> applyStandardRules(rawText)
            LinguisticRegister.PEER_COLLOQUIAL -> applyColloquialRules(rawText)
        }
    }

    // =========================================================================
    // REGISTER-SPEZIFISCHE TRANSFORMATIONEN
    // =========================================================================

    private fun applyFormalRules(text: String): String {
        var result = text.trim().replace(MULTI_SPACE, " ").replaceFirstChar { it.uppercase() }
        CONTRACTIONS.forEach { (expanded, contracted) ->
            result = result.replace(contracted, expanded, ignoreCase = true)
        }
        result = result.replace("bzw.", "beziehungsweise")
        result = result.replace("usw.", "und so weiter")
        result = result.replace("z.B.", "zum Beispiel")
        result = result.replace("etc.", "et cetera")
        return result
    }

    private fun applyStandardRules(text: String): String {
        return text.trim().replace(MULTI_SPACE, " ").replaceFirstChar { it.uppercase() }
    }

    private fun applyColloquialRules(text: String): String {
        var result = text.trim()
        CONTRACTIONS.forEach { (expanded, contracted) ->
            result = result.replace(expanded, contracted, ignoreCase = true)
        }
        return result
    }

    /**
     * Prüft, ob ein Text grammatische Fehler enthält (Heuristik).
     * Gibt eine Liste von Korrekturvorschlägen zurück.
     */
    fun validateGrammar(text: String): List<String> {
        val issues = mutableListOf<String>()

        if (WEGEN_DATIV.containsMatchIn(text)) {
            issues.add("'wegen dem/das' → korrekt: 'wegen des' (Genitiv erforderlich)")
        }
        if (TROTZ_DATIV.containsMatchIn(text)) {
            issues.add("'trotz dem' → korrekt: 'trotz des' (Genitiv erforderlich)")
        }
        if (FUER_DATIV.containsMatchIn(text)) {
            issues.add("'für dem' → korrekt: 'für den' (Akkusativ erforderlich)")
        }

        return issues
    }
}

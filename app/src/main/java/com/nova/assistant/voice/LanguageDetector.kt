package com.nova.assistant.voice

import com.nova.assistant.domain.model.DetectedLanguage
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Lightweight heuristic detector: real Bangla Unicode range vs Latin script vs a mix, plus a
 * small dictionary of common Banglish tokens (kholo, koro, pathao, dao...) so "YouTube kholo"
 * is recognized as Banglish rather than plain English. This is intentionally simple and fast —
 * true NLU/intent understanding happens in the backend LLM, not here.
 */
@Singleton
class LanguageDetector @Inject constructor() {

    private val banglaRange = '\u0980'..'\u09FF'
    private val banglishWords = setOf(
        "kholo", "koro", "pathao", "dao", "de", "ke", "theke", "korbe", "korte",
        "khule", "diye", "niye", "hobe", "acche", "ache", "kotha", "bolo"
    )

    fun detect(text: String): DetectedLanguage {
        if (text.isBlank()) return DetectedLanguage.UNKNOWN

        val hasBanglaScript = text.any { it in banglaRange }
        val words = text.lowercase().split(Regex("\\s+")).map { it.trim(',', '.', '?', '!') }
        val hasBanglishWord = words.any { it in banglishWords }
        val hasLatinWord = words.any { it.isNotEmpty() && it[0].isLetter() && it[0].code < 128 }

        return when {
            hasBanglaScript && (hasLatinWord || hasBanglishWord) -> DetectedLanguage.MIXED
            hasBanglaScript -> DetectedLanguage.BANGLA
            hasBanglishWord -> DetectedLanguage.BANGLISH
            hasLatinWord -> DetectedLanguage.ENGLISH
            else -> DetectedLanguage.UNKNOWN
        }
    }
}

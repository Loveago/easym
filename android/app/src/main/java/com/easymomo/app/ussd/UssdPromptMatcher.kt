package com.easymomo.app.ussd

import com.easymomo.app.ussd.model.PromptMatchMode
import com.easymomo.app.ussd.model.UssdFlowStep

/**
 * Compares observed USSD prompt text against expected prompt variants.
 * Handles case, extra whitespace, line breaks, currency formatting, and minor punctuation.
 * Ignores transient "USSD code running..." loading indicators.
 */
class UssdPromptMatcher {

    sealed interface MatchResult {
        data class Matched(val matchedPattern: String) : MatchResult
        data class ProviderError(val reason: String) : MatchResult
        data object SessionTerminated : MatchResult
        data object TransientLoading : MatchResult
        data class UnexpectedPrompt(val observed: String) : MatchResult
    }

    private val transientLoadingIndicators = listOf(
        "ussd code running",
        "please wait",
        "connecting..."
    )

    private val providerErrorIndicators = listOf(
        "connection problem",
        "invalid mmi code",
        "service unavailable",
        "system error",
        "try again later",
        "temporarily unavailable",
        "invalid option",
        "invalid mobile number",
        "invalid number",
        "invalid phone",
        "invalid amount",
        "invalid operational",
        "invalid operator",
        "invalid reference",
        "wrong selection",
        "account holder not found",
        "merchant not found",
        "number not found",
        "not registered",
        "does not exist",
        "invalid account",
        "invalid merchant",
        "invalid recipient",
        "invalid pin",
        "wrong pin",
        "incorrect pin",
        "pin blocked",
        "insufficient balance",
        "insufficient funds",
        "exceeded limit",
        "transaction failed",
        "request failed"
    )

    private val terminationIndicators = listOf(
        "session cancelled",
        "session terminated",
        "session expired",
        "timed out",
        "thank you for using"
    )

    fun normalize(text: String): String {
        return text
            .replace('\u00A0', ' ')
            .replace('’', '\'')
            .replace('‘', '\'')
            .replace('“', '"')
            .replace('”', '"')
            .replace(Regex("[\\r\\n]+"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
            .lowercase()
    }

    fun matchStep(observedRaw: String, step: UssdFlowStep): MatchResult {
        val observedNorm = normalize(observedRaw)
        if (observedNorm.isEmpty()) {
            return MatchResult.UnexpectedPrompt(observedRaw)
        }

        if (transientLoadingIndicators.any { observedNorm == it || observedNorm.startsWith(it) && observedNorm.length < 30 }) {
            return MatchResult.TransientLoading
        }

        if (providerErrorIndicators.any { observedNorm.contains(it) }) {
            return MatchResult.ProviderError(observedRaw.trim())
        }

        if (terminationIndicators.any { observedNorm.contains(it) }) {
            return MatchResult.SessionTerminated
        }

        for (pattern in (step.expectedPrompts as List<String>?).orEmpty()) {
            if (pattern == "VERIFY_FROM_AGENT_SIM") continue
            val mode = (step.matchMode as PromptMatchMode?) ?: PromptMatchMode.NORMALIZED_TEXT
            val isMatch = when (mode) {
                PromptMatchMode.EXACT -> observedRaw.trim() == pattern.trim()
                PromptMatchMode.NORMALIZED_TEXT -> matchesNormalizedPattern(observedNorm, pattern)
                PromptMatchMode.REGEX -> runCatching {
                    Regex(pattern, setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
                        .containsMatchIn(observedRaw)
                }.getOrDefault(false)
            }
            if (isMatch) {
                return MatchResult.Matched(pattern)
            }
        }

        return MatchResult.UnexpectedPrompt(observedRaw.trim())
    }

    private fun matchesNormalizedPattern(observedNorm: String, patternRaw: String): Boolean {
        val patternNorm = normalize(patternRaw)
        if (patternNorm.isEmpty()) return false
        if (NUMERIC_MENU_PREFIX_REGEX.matches(patternNorm)) {
            val regex = Regex("""(?:^|\s)${Regex.escape(patternNorm)}(?!\d)""")
            return regex.containsMatchIn(observedNorm)
        }
        if (patternNorm.length <= 3 && patternNorm.all { it.isLetterOrDigit() }) {
            val regex = Regex("""\b${Regex.escape(patternNorm)}\b""")
            return regex.containsMatchIn(observedNorm)
        }
        return observedNorm.contains(patternNorm)
    }

    private companion object {
        val NUMERIC_MENU_PREFIX_REGEX = Regex("""^\d+[.)]$""")
    }
}

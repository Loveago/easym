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
        "wrong selection"
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

        for (pattern in (step.expectedPrompts as List<String>?).orEmpty()) {
            if (pattern == "VERIFY_FROM_AGENT_SIM") continue
            val mode = (step.matchMode as PromptMatchMode?) ?: PromptMatchMode.NORMALIZED_TEXT
            val isMatch = when (mode) {
                PromptMatchMode.EXACT -> observedRaw.trim() == pattern.trim()
                PromptMatchMode.NORMALIZED_TEXT -> observedNorm.contains(normalize(pattern))
                PromptMatchMode.REGEX -> runCatching {
                    Regex(pattern, setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
                        .containsMatchIn(observedRaw)
                }.getOrDefault(false)
            }
            if (isMatch) {
                return MatchResult.Matched(pattern)
            }
        }

        if (terminationIndicators.any { observedNorm.contains(it) }) {
            return MatchResult.SessionTerminated
        }

        return MatchResult.UnexpectedPrompt(observedRaw.trim())
    }
}

package com.easymomo.app.ussd

import com.easymomo.app.ussd.model.PromptMatchMode
import com.easymomo.app.ussd.model.ResponseType
import com.easymomo.app.ussd.model.StepAction
import com.easymomo.app.ussd.model.UssdFlowStep

/**
 * Detects confirmation and PIN-authorization checkpoints.
 * Never bypasses provider confirmation requirements and never stores or transmits PINs.
 */
class UssdConfirmationHandler(
    private val promptMatcher: UssdPromptMatcher = UssdPromptMatcher()
) {

    private val explicitPinPhrases = listOf(
        "4-digit pin",
        "secret code",
        "secret pin",
        "enter otp",
        "enter passcode"
    )

    private val pinEntryVerbRegex = Regex(
        """\b(?:enter|input|type|provide|re-enter|reenter|confirm|verify)\b[^.\n]{0,35}\b(?:pin|secret\s+code|otp|passcode)\b""",
        RegexOption.IGNORE_CASE
    )

    private val pinRetryEntryRegex = Regex(
        """\b(?:enter|input|type|provide|re-enter|reenter)\b[^.\n]{0,30}\b(?:pin|code)\b""",
        RegexOption.IGNORE_CASE
    )

    private val pinSuffixRegex = Regex(
        """(?:^|[:.\-]\s*|\s)(?:momo\s+pin|mm\s+pin|secret\s+code|pin)\s*:?$"""
    )

    private val menuPinActionRegex = Regex(
        """\b(?:change|reset|manage|forgot|create|set|unblock|modify|update)\s+(?:your\s+)?(?:momo\s+|mm\s+|agent\s+|wallet\s+)?(?:pin|secret\s+code)\b""",
        RegexOption.IGNORE_CASE
    )

    private val nonPinOutcomeMarkers = listOf(
        "successfully",
        "successful",
        "completed",
        "recharged",
        "activated",
        "awaiting",
        "pending",
        "customer to",
        "customer must",
        "tell customer",
        "ask customer",
        "customer approval",
        "pending approval",
        "awaiting approval",
        "initiated",
        "insufficient",
        "cancelled",
        "declined",
        "rejected",
        "terminated",
        "blocked",
        "unsuccessful",
        "failed"
    )

    private val pinErrorPhrases = listOf(
        "wrong pin",
        "invalid pin",
        "incorrect pin",
        "incorrect mm pin",
        "incorrect momo pin"
    )

    private val confirmationKeywords = listOf(
        "confirm",
        "approve",
        "press 1 to",
        "reply 1 to",
        "yes/no"
    )

    private fun isNonPinPrompt(observedPrompt: String, norm: String): Boolean {
        if (norm.isEmpty()) return true
        if (nonPinOutcomeMarkers.any { norm.contains(it) }) return true
        val rawLower = observedPrompt.lowercase()
        if (pinErrorPhrases.any { norm.contains(it) } && !pinRetryEntryRegex.containsMatchIn(rawLower)) {
            return true
        }
        if (menuPinActionRegex.containsMatchIn(rawLower)) {
            val stripped = menuPinActionRegex.replace(rawLower, " ")
            if (!pinRetryEntryRegex.containsMatchIn(stripped)) {
                return true
            }
        }
        return false
    }

    fun isPinKeywordPrompt(observedPrompt: String): Boolean {
        val norm = promptMatcher.normalize(observedPrompt)
        if (isNonPinPrompt(observedPrompt, norm)) return false
        val rawLower = observedPrompt.lowercase()
        return pinEntryVerbRegex.containsMatchIn(rawLower) ||
            pinEntryVerbRegex.containsMatchIn(norm) ||
            explicitPinPhrases.any { norm.contains(it) } ||
            pinSuffixRegex.containsMatchIn(norm)
    }

    fun requiresPinAuthorization(observedPrompt: String, step: UssdFlowStep): Boolean {
        val norm = promptMatcher.normalize(observedPrompt)
        if (isNonPinPrompt(observedPrompt, norm)) return false
        if (isPinKeywordPrompt(observedPrompt)) return true
        if (step.action == StepAction.PAUSE_FOR_PIN || step.responseType == ResponseType.MANUAL_SENSITIVE_INPUT) {
            val rawPrompts = (step.expectedPrompts as List<String>?).orEmpty()
            val specificPrompts = rawPrompts.filter { pattern ->
                val pn = promptMatcher.normalize(pattern)
                pn != "pin" && pn != "momo pin" && pn != "mm pin"
            }
            if (specificPrompts.isNotEmpty()) {
                val filteredStep = UssdFlowStep(
                    id = step.id,
                    type = step.type,
                    expectedPrompts = specificPrompts,
                    matchMode = (step.matchMode as PromptMatchMode?) ?: PromptMatchMode.NORMALIZED_TEXT
                )
                return promptMatcher.matchStep(observedPrompt, filteredStep) is UssdPromptMatcher.MatchResult.Matched
            }
        }
        return false
    }

    fun isConfirmationPrompt(observedPrompt: String, step: UssdFlowStep): Boolean {
        if (step.action == StepAction.REQUIRE_CONFIRMATION) return true
        val norm = promptMatcher.normalize(observedPrompt)
        return confirmationKeywords.any { norm.contains(it) }
    }

    fun buildConfirmationSummary(
        variables: UssdInputHandler.TransactionVariables,
        observedPrompt: String
    ): String {
        return buildString {
            append("Network: ${variables.network.displayName}\n")
            if (variables.customerPhone.isNotBlank()) {
                append("Recipient/Customer: ${variables.customerPhone}\n")
            }
            if (variables.amount.isNotBlank()) {
                append("Amount: GHS ${variables.amount}\n")
            }
            append("\nProvider prompt:\n${observedPrompt.trim()}")
        }
    }
}

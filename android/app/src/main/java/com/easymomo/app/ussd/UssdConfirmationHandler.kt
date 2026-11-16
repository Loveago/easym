package com.easymomo.app.ussd

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

    private val pinPromptKeywords = listOf(
        "enter pin",
        "enter your pin",
        "momo pin",
        "secret pin",
        "4-digit pin",
        "enter otp",
        "authorize"
    )

    private val confirmationKeywords = listOf(
        "confirm",
        "approve",
        "press 1 to",
        "reply 1 to",
        "yes/no"
    )

    fun isPinKeywordPrompt(observedPrompt: String): Boolean {
        val norm = promptMatcher.normalize(observedPrompt)
        return pinPromptKeywords.any { norm.contains(it) }
    }

    fun requiresPinAuthorization(observedPrompt: String, step: UssdFlowStep): Boolean {
        if (isPinKeywordPrompt(observedPrompt)) return true
        if (step.action == StepAction.PAUSE_FOR_PIN || step.responseType == ResponseType.MANUAL_SENSITIVE_INPUT) {
            return promptMatcher.matchStep(observedPrompt, step) is UssdPromptMatcher.MatchResult.Matched
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

package com.easymomo.app.ussd

import com.easymomo.app.ussd.model.OutcomeCategory
import com.easymomo.app.ussd.model.UssdFlowStep
import com.easymomo.app.ussd.model.UssdOutcome

/**
 * Classifies final or terminal USSD responses into verified outcome categories
 * and extracts provider transaction references where present.
 */
class UssdResultDetector(
    private val promptMatcher: UssdPromptMatcher = UssdPromptMatcher()
) {

    private val referenceRegex = Regex(
        """(?:ref(?:erence)?|tx\s*id|txn\s*id|trans(?:action)?\s*id|id)\s*[:#-]?\s*([A-Z0-9-]{5,24})""",
        RegexOption.IGNORE_CASE
    )

    private val insufficientBalanceKeywords = listOf(
        "insufficient balance",
        "insufficient funds",
        "not enough balance",
        "low balance",
        "exceeds your balance"
    )

    private val invalidRecipientKeywords = listOf(
        "invalid recipient",
        "invalid number",
        "not registered",
        "does not exist",
        "invalid account",
        "invalid mobile",
        "account holder not found",
        "merchant not found",
        "number not found",
        "not found"
    )

    private val pendingKeywords = listOf(
        "being processed",
        "pending",
        "awaiting",
        "customer must",
        "customer to",
        "subscriber to approve",
        "tell customer",
        "ask customer",
        "you will receive an sms",
        "confirmation sms"
    )

    private val failureKeywords = listOf(
        "failed",
        "unsuccessful",
        "declined",
        "rejected",
        "cancelled",
        "wrong pin",
        "invalid pin",
        "incorrect pin",
        "incorrect mm pin",
        "incorrect momo pin",
        "pin blocked",
        "account blocked",
        "not allowed",
        "unauthorized",
        "exceeded limit"
    )

    private val successKeywords = listOf(
        "successfully",
        "successful",
        "completed",
        "confirmed",
        "you have sent",
        "you have received",
        "cash in of",
        "cash out of"
    )

    fun detect(rawResponse: String, finalStep: UssdFlowStep? = null): UssdOutcome {
        val norm = promptMatcher.normalize(rawResponse)
        val ref = extractReference(rawResponse)

        // 1. Check specific failure indicators first so generic nouns in successPrompts never mask errors
        return when {
            insufficientBalanceKeywords.any { norm.contains(it) } ->
                UssdOutcome(OutcomeCategory.INSUFFICIENT_BALANCE, rawResponse, ref, rawResponse.trim())
            invalidRecipientKeywords.any { norm.contains(it) } ->
                UssdOutcome(OutcomeCategory.INVALID_RECIPIENT, rawResponse, ref, rawResponse.trim())
            failureKeywords.any { norm.contains(it) } ->
                UssdOutcome(OutcomeCategory.FAILED, rawResponse, ref, rawResponse.trim())
            finalStep != null && matchesAny(norm, finalStep.failurePrompts) ->
                UssdOutcome(OutcomeCategory.FAILED, rawResponse, ref, rawResponse.trim())
            pendingKeywords.any { norm.contains(it) } ->
                UssdOutcome(OutcomeCategory.PENDING, rawResponse, ref, rawResponse.trim())
            finalStep != null && matchesAny(norm, finalStep.pendingPrompts) ->
                UssdOutcome(OutcomeCategory.PENDING, rawResponse, ref, rawResponse.trim())
            finalStep != null && matchesAny(norm, finalStep.successPrompts) ->
                UssdOutcome(OutcomeCategory.SUCCESSFUL, rawResponse, ref, rawResponse.trim())
            successKeywords.any { norm.contains(it) } ->
                UssdOutcome(OutcomeCategory.SUCCESSFUL, rawResponse, ref, rawResponse.trim())
            else ->
                UssdOutcome(OutcomeCategory.UNEXPECTED, rawResponse, ref, rawResponse.trim())
        }
    }

    fun extractReference(text: String): String? {
        return referenceRegex.find(text)?.groupValues?.getOrNull(1)
    }

    private fun matchesAny(normalizedObserved: String, patterns: List<String>?): Boolean {
        return patterns.orEmpty()
            .filter { it.isNotBlank() && it != "VERIFY_FROM_AGENT_SIM" }
            .any { normalizedObserved.contains(promptMatcher.normalize(it)) }
    }
}

package com.easymomo.app.ussd

import com.easymomo.app.domain.model.TransactionStatus

/**
 * Handles timeouts, network interruptions, unexpected menu changes, and app backgrounding.
 * Preserves uncertain transactions as UNKNOWN and NEVER auto-retries financial operations.
 */
class UssdRecoveryManager {

    data class RecoveryDecision(
        val targetStatus: TransactionStatus,
        val userMessage: String,
        val allowAutomaticRetry: Boolean = false
    )

    fun onTimeout(hadSentFinancialStep: Boolean): RecoveryDecision {
        return if (hadSentFinancialStep) {
            RecoveryDecision(
                targetStatus = TransactionStatus.UNKNOWN,
                userMessage = "USSD session timed out after financial input. Check provider SMS/balance before retrying.",
                allowAutomaticRetry = false
            )
        } else {
            RecoveryDecision(
                targetStatus = TransactionStatus.FAILED,
                userMessage = "USSD session timed out before transaction input was sent.",
                allowAutomaticRetry = false
            )
        }
    }

    fun onUnexpectedMenu(observedPrompt: String): RecoveryDecision {
        return RecoveryDecision(
            targetStatus = TransactionStatus.UNKNOWN,
            userMessage = "Unexpected menu received. Automated input stopped to protect your funds: \"$observedPrompt\"",
            allowAutomaticRetry = false
        )
    }

    fun onUserCancelled(hadSentFinancialStep: Boolean): RecoveryDecision {
        return if (hadSentFinancialStep) {
            RecoveryDecision(
                targetStatus = TransactionStatus.UNKNOWN,
                userMessage = "Session cancelled after input was sent. Verify final status via provider SMS.",
                allowAutomaticRetry = false
            )
        } else {
            RecoveryDecision(
                targetStatus = TransactionStatus.CANCELLED,
                userMessage = "Transaction cancelled safely before submission.",
                allowAutomaticRetry = false
            )
        }
    }

    fun onNetworkOrAppInterruption(hadSentFinancialStep: Boolean): RecoveryDecision {
        return if (hadSentFinancialStep) {
            RecoveryDecision(
                targetStatus = TransactionStatus.UNKNOWN,
                userMessage = "Session interrupted. Status is UNKNOWN; do not automatically repeat.",
                allowAutomaticRetry = false
            )
        } else {
            RecoveryDecision(
                targetStatus = TransactionStatus.FAILED,
                userMessage = "USSD session could not complete due to interruption.",
                allowAutomaticRetry = false
            )
        }
    }
}

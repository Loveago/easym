package com.easymomo.app.domain

import com.easymomo.app.domain.model.TransactionStatus

/**
 * Enforces Section 17 Transaction Lifecycle state transitions.
 * Invalid transitions are rejected; UNKNOWN never auto-transitions to SUCCESSFUL.
 */
object TransactionLifecycleManager {

    private val allowedTransitions: Map<TransactionStatus, Set<TransactionStatus>> = mapOf(
        TransactionStatus.DRAFT to setOf(
            TransactionStatus.PROCESSING,
            TransactionStatus.AWAITING_CUSTOMER,
            TransactionStatus.CANCELLED
        ),
        TransactionStatus.PROCESSING to setOf(
            TransactionStatus.AWAITING_VERIFICATION,
            TransactionStatus.SUCCESSFUL,
            TransactionStatus.FAILED,
            TransactionStatus.UNKNOWN,
            TransactionStatus.CANCELLED
        ),
        TransactionStatus.AWAITING_CUSTOMER to setOf(
            TransactionStatus.AWAITING_VERIFICATION,
            TransactionStatus.FAILED,
            TransactionStatus.CANCELLED
        ),
        TransactionStatus.AWAITING_VERIFICATION to setOf(
            TransactionStatus.SUCCESSFUL,
            TransactionStatus.FAILED,
            TransactionStatus.UNKNOWN
        ),
        TransactionStatus.UNKNOWN to setOf(
            TransactionStatus.FAILED
        ),
        TransactionStatus.SUCCESSFUL to emptySet(),
        TransactionStatus.FAILED to emptySet(),
        TransactionStatus.CANCELLED to emptySet()
    )

    fun canTransition(
        from: TransactionStatus,
        to: TransactionStatus,
        isExplicitAgentVerification: Boolean = false
    ): Boolean {
        if (from == to) return false
        if (from == TransactionStatus.UNKNOWN && to == TransactionStatus.SUCCESSFUL) {
            return isExplicitAgentVerification
        }
        return allowedTransitions[from]?.contains(to) == true
    }

    fun requireValidTransition(
        from: TransactionStatus,
        to: TransactionStatus,
        isExplicitAgentVerification: Boolean = false
    ) {
        require(canTransition(from, to, isExplicitAgentVerification)) {
            "Invalid transaction state transition from $from to $to"
        }
    }
}

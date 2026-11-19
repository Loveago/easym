package com.easymomo.app

import com.easymomo.app.domain.TransactionLifecycleManager
import com.easymomo.app.domain.model.TransactionStatus
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TransactionLifecycleManagerTest {

    @Test
    fun `allows valid Section 17 transitions`() {
        assertTrue(TransactionLifecycleManager.canTransition(TransactionStatus.DRAFT, TransactionStatus.PROCESSING))
        assertTrue(TransactionLifecycleManager.canTransition(TransactionStatus.PROCESSING, TransactionStatus.AWAITING_VERIFICATION))
        assertTrue(TransactionLifecycleManager.canTransition(TransactionStatus.PROCESSING, TransactionStatus.SUCCESSFUL))
        assertTrue(TransactionLifecycleManager.canTransition(TransactionStatus.PROCESSING, TransactionStatus.UNKNOWN))
        assertTrue(TransactionLifecycleManager.canTransition(TransactionStatus.AWAITING_CUSTOMER, TransactionStatus.AWAITING_VERIFICATION))
        assertTrue(TransactionLifecycleManager.canTransition(TransactionStatus.AWAITING_VERIFICATION, TransactionStatus.SUCCESSFUL))
    }

    @Test
    fun `blocks invalid and automatic UNKNOWN to SUCCESSFUL transitions`() {
        assertFalse(TransactionLifecycleManager.canTransition(TransactionStatus.UNKNOWN, TransactionStatus.SUCCESSFUL))
        assertTrue(
            TransactionLifecycleManager.canTransition(
                TransactionStatus.UNKNOWN,
                TransactionStatus.SUCCESSFUL,
                isExplicitAgentVerification = true
            )
        )
        assertFalse(TransactionLifecycleManager.canTransition(TransactionStatus.SUCCESSFUL, TransactionStatus.FAILED))
        assertFalse(TransactionLifecycleManager.canTransition(TransactionStatus.FAILED, TransactionStatus.SUCCESSFUL))
    }
}

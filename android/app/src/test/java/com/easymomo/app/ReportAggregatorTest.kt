package com.easymomo.app

import com.easymomo.app.data.local.TransactionEntity
import com.easymomo.app.data.repository.ReportAggregator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ReportAggregatorTest {

    @Test
    fun `aggregates counts, successful value, and keeps commission null when unverified`() {
        val now = System.currentTimeMillis()
        val list = listOf(
            TransactionEntity(
                localId = "1", network = "MTN", transactionType = "CASH_IN",
                processingMethod = "AGENT_USSD", recipientNumber = "0244123456",
                amount = 100.0, status = "SUCCESSFUL", createdAt = now, updatedAt = now
            ),
            TransactionEntity(
                localId = "2", network = "AT", transactionType = "CASH_OUT",
                processingMethod = "CUSTOMER_LED", recipientNumber = "0271234567",
                amount = 50.0, status = "FAILED", createdAt = now, updatedAt = now
            ),
            TransactionEntity(
                localId = "3", network = "TELECEL", transactionType = "AIRTIME",
                processingMethod = "AGENT_USSD", recipientNumber = "0201234567",
                amount = 20.0, status = "UNKNOWN", createdAt = now, updatedAt = now
            )
        )

        val summary = ReportAggregator.aggregate(list)
        assertEquals(3, summary.totalAttempts)
        assertEquals(1, summary.successfulCount)
        assertEquals(1, summary.failedCount)
        assertEquals(1, summary.unknownCount)
        assertEquals(100.0, summary.totalSuccessfulValueGhs, 0.001)
        assertNull(summary.verifiedCommissionGhs)
        assertEquals(3, summary.networkBreakdown.size)
    }
}

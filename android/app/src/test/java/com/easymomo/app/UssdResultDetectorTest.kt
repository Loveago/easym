package com.easymomo.app

import com.easymomo.app.ussd.UssdResultDetector
import com.easymomo.app.ussd.model.OutcomeCategory
import org.junit.Assert.assertEquals
import org.junit.Test

class UssdResultDetectorTest {

    private val detector = UssdResultDetector()

    @Test
    fun `detects success and extracts provider reference`() {
        val outcome = detector.detect("Cash In of GHS 100.00 completed successfully. TxID: GH99283741.")
        assertEquals(OutcomeCategory.SUCCESSFUL, outcome.category)
        assertEquals("GH99283741", outcome.providerReference)
    }

    @Test
    fun `detects insufficient balance`() {
        val outcome = detector.detect("Transaction failed: Insufficient balance in agent wallet.")
        assertEquals(OutcomeCategory.INSUFFICIENT_BALANCE, outcome.category)
    }

    @Test
    fun `detects invalid recipient`() {
        val outcome = detector.detect("Invalid recipient number or wallet not registered.")
        assertEquals(OutcomeCategory.INVALID_RECIPIENT, outcome.category)
    }

    @Test
    fun `detects pending response`() {
        val outcome = detector.detect("Your request is being processed. You will receive an SMS confirmation.")
        assertEquals(OutcomeCategory.PENDING, outcome.category)
    }
}

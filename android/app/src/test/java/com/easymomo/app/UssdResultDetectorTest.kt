package com.easymomo.app

import com.easymomo.app.ussd.UssdResultDetector
import com.easymomo.app.ussd.model.OutcomeCategory
import com.easymomo.app.ussd.model.StepType
import com.easymomo.app.ussd.model.UssdFlowStep
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

    @Test
    fun `detects account holder not found as invalid recipient`() {
        val outcome = detector.detect("Account Holder not found.")
        assertEquals(OutcomeCategory.INVALID_RECIPIENT, outcome.category)
    }

    @Test
    fun `failure keywords win over generic nouns in finalStep successPrompts`() {
        val finalStep = UssdFlowStep(
            id = "FINAL_RESULT",
            type = StepType.MATCH_RESULT,
            successPrompts = listOf("success", "activated", "completed", "bundle", "airtime"),
            failurePrompts = listOf("failed", "invalid", "insufficient")
        )

        val insufficient = detector.detect("Bundle purchase could not be completed. Insufficient balance.", finalStep)
        assertEquals(OutcomeCategory.INSUFFICIENT_BALANCE, insufficient.category)

        val wrongPin = detector.detect("Airtime recharge unsuccessful. Wrong PIN.", finalStep)
        assertEquals(OutcomeCategory.FAILED, wrongPin.category)

        val invalidRecipient = detector.detect("Bundle request rejected: Invalid recipient number.", finalStep)
        assertEquals(OutcomeCategory.INVALID_RECIPIENT, invalidRecipient.category)

        val incorrectPinBalance = detector.detect(
            "Balance enquiry: Incorrect PIN",
            UssdFlowStep(id = "FINAL_RESULT", type = StepType.MATCH_RESULT, successPrompts = listOf("balance", "ghs"))
        )
        assertEquals(OutcomeCategory.FAILED, incorrectPinBalance.category)

        val incorrectMmPinBundle = detector.detect("Bundle error: Incorrect MM PIN", finalStep)
        assertEquals(OutcomeCategory.FAILED, incorrectMmPinBundle.category)
    }
}

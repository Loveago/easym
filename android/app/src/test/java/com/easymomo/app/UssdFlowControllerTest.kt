package com.easymomo.app

import com.easymomo.app.domain.model.NetworkType
import com.easymomo.app.ussd.UssdFlowController
import com.easymomo.app.ussd.UssdInputHandler
import com.easymomo.app.ussd.UssdSessionManager
import com.easymomo.app.ussd.model.OutcomeCategory
import com.easymomo.app.ussd.model.PromptMatchMode
import com.easymomo.app.ussd.model.StepAction
import com.easymomo.app.ussd.model.StepType
import com.easymomo.app.ussd.model.UssdFlowProfile
import com.easymomo.app.ussd.model.UssdFlowStep
import com.easymomo.app.ussd.model.UssdSessionState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UssdFlowControllerTest {

    private fun verifiedTestProfile(): UssdFlowProfile {
        return UssdFlowProfile(
            flowId = "MTN_CASH_IN",
            network = "MTN",
            transactionType = "CASH_IN",
            version = 1,
            entryCode = "*171#",
            enabled = true,
            verifiedWithSim = true,
            steps = listOf(
                UssdFlowStep(id = "START", type = StepType.INITIATE_USSD, nextStepId = "MENU"),
                UssdFlowStep(
                    id = "MENU",
                    type = StepType.MATCH_PROMPT,
                    expectedPrompts = listOf("1. Cash In"),
                    matchMode = PromptMatchMode.NORMALIZED_TEXT,
                    action = StepAction.SEND_MENU_OPTION,
                    menuOption = "1",
                    nextStepId = "PHONE"
                ),
                UssdFlowStep(
                    id = "PHONE",
                    type = StepType.MATCH_PROMPT,
                    expectedPrompts = listOf("Enter customer number"),
                    matchMode = PromptMatchMode.NORMALIZED_TEXT,
                    action = StepAction.SEND_VARIABLE,
                    variable = "customer_phone",
                    nextStepId = "AMOUNT"
                ),
                UssdFlowStep(
                    id = "AMOUNT",
                    type = StepType.MATCH_PROMPT,
                    expectedPrompts = listOf("Enter amount"),
                    matchMode = PromptMatchMode.NORMALIZED_TEXT,
                    action = StepAction.SEND_VARIABLE,
                    variable = "amount",
                    nextStepId = "PIN"
                ),
                UssdFlowStep(
                    id = "PIN",
                    type = StepType.REQUIRE_AUTHORIZATION,
                    expectedPrompts = listOf("Enter PIN"),
                    action = StepAction.PAUSE_FOR_PIN,
                    nextStepId = "RESULT"
                ),
                UssdFlowStep(id = "RESULT", type = StepType.MATCH_RESULT)
            )
        )
    }

    @Test
    fun `blocks unverified flow with ConfigurationRequired`() {
        val controller = UssdFlowController()
        val unverified = verifiedTestProfile().copy(enabled = false, verifiedWithSim = false)
        val res = controller.startFlow(
            "tx-1",
            unverified,
            UssdInputHandler.TransactionVariables(customerPhone = "0244123456", amount = "50", network = NetworkType.MTN)
        )
        assertTrue(res.isFailure)
        assertTrue(controller.sessionState.value is UssdSessionState.ConfigurationRequired)
    }

    @Test
    fun `executes verified flow and pauses at PIN before completing`() {
        val sentReplies = mutableListOf<String>()
        val controller = UssdFlowController(responseSender = { reply ->
            sentReplies.add(reply)
            true
        })
        val vars = UssdInputHandler.TransactionVariables(
            customerPhone = "0244123456",
            amount = "100",
            network = NetworkType.MTN
        )

        assertTrue(controller.startFlow("tx-1", verifiedTestProfile(), vars).isSuccess)
        controller.onPromptObserved("Welcome: 1. Cash In 2. Cash Out")
        controller.onPromptObserved("Enter customer number:")
        controller.onPromptObserved("Enter amount in GHS:")
        controller.onPromptObserved("Confirm & Enter PIN:")

        assertEquals(listOf("1", "0244123456", "100"), sentReplies)
        assertTrue(controller.sessionState.value is UssdSessionState.PausedForPinAuthorization)

        controller.resumeAfterManualPin("Cash In of GHS 100.00 completed successfully. Ref: TX12345")
        val finalState = controller.sessionState.value as UssdSessionState.Completed
        assertEquals(OutcomeCategory.SUCCESSFUL, finalState.outcome.category)
        assertEquals("TX12345", finalState.outcome.providerReference)
    }

    @Test
    fun `proceeds with configured menu option even when menu wording differs`() {
        val sentReplies = mutableListOf<String>()
        val controller = UssdFlowController(responseSender = { reply ->
            sentReplies.add(reply)
            true
        })
        val vars = UssdInputHandler.TransactionVariables(
            customerPhone = "0244123456",
            amount = "100",
            network = NetworkType.MTN
        )
        controller.startFlow("tx-2", verifiedTestProfile(), vars)
        controller.onPromptObserved("1. New Promo Menu 2. Exit")

        assertEquals(listOf("1"), sentReplies)
        assertTrue(controller.sessionState.value is UssdSessionState.AwaitingPrompt)
    }

    @Test
    fun `prevents overlapping concurrent sessions`() {
        val sessionManager = UssdSessionManager()
        val first = sessionManager.startSession("tx-a", "MTN_CASH_IN")
        val second = sessionManager.startSession("tx-b", "MTN_CASH_OUT")
        assertTrue(first != null)
        assertEquals(null, second)
    }

    private fun mtnCashFlowProfile(transactionType: String): UssdFlowProfile {
        val isCashOut = transactionType == "CASH_OUT"
        return UssdFlowProfile(
            flowId = if (isCashOut) "MTN_CASH_OUT" else "MTN_CASH_IN",
            network = "MTN",
            transactionType = transactionType,
            version = 3,
            entryCode = "*171#",
            enabled = true,
            verifiedWithSim = true,
            steps = listOf(
                UssdFlowStep(id = "START", type = StepType.INITIATE_USSD, nextStepId = "SELECT_SERVICE"),
                UssdFlowStep(
                    id = "SELECT_SERVICE",
                    type = StepType.MATCH_PROMPT,
                    expectedPrompts = listOf(if (isCashOut) "Pay Cash Out" else "Receive Cash In"),
                    matchMode = PromptMatchMode.NORMALIZED_TEXT,
                    action = StepAction.SEND_MENU_OPTION,
                    menuOption = if (isCashOut) "2" else "3",
                    nextStepId = "SELECT_USER_TYPE"
                ),
                UssdFlowStep(
                    id = "SELECT_USER_TYPE",
                    type = StepType.MATCH_PROMPT,
                    expectedPrompts = listOf("Mobile Money User"),
                    matchMode = PromptMatchMode.NORMALIZED_TEXT,
                    action = StepAction.SEND_MENU_OPTION,
                    menuOption = "1",
                    nextStepId = "ENTER_CUSTOMER_NUMBER"
                ),
                UssdFlowStep(
                    id = "ENTER_CUSTOMER_NUMBER",
                    type = StepType.MATCH_PROMPT,
                    expectedPrompts = listOf("Enter number", "Enter phone number"),
                    matchMode = PromptMatchMode.NORMALIZED_TEXT,
                    action = StepAction.SEND_VARIABLE,
                    variable = "customer_phone",
                    nextStepId = "CONFIRM_CUSTOMER_NUMBER"
                ),
                UssdFlowStep(
                    id = "CONFIRM_CUSTOMER_NUMBER",
                    type = StepType.MATCH_PROMPT,
                    expectedPrompts = listOf("Re-enter number", "Confirm number"),
                    matchMode = PromptMatchMode.NORMALIZED_TEXT,
                    action = StepAction.SEND_VARIABLE,
                    variable = "customer_phone",
                    nextStepId = "ENTER_AMOUNT",
                    optional = true,
                    skipToStepId = "ENTER_AMOUNT"
                ),
                UssdFlowStep(
                    id = "ENTER_AMOUNT",
                    type = StepType.MATCH_PROMPT,
                    expectedPrompts = listOf("Enter amount"),
                    matchMode = PromptMatchMode.NORMALIZED_TEXT,
                    action = StepAction.SEND_VARIABLE,
                    variable = "amount",
                    nextStepId = "ENTER_REFERENCE"
                ),
                UssdFlowStep(
                    id = "ENTER_REFERENCE",
                    type = StepType.MATCH_PROMPT,
                    expectedPrompts = listOf("Enter reference"),
                    matchMode = PromptMatchMode.NORMALIZED_TEXT,
                    action = StepAction.SEND_VARIABLE,
                    variable = "reference",
                    nextStepId = "ENTER_AGENT_PIN",
                    optional = true,
                    skipToStepId = "ENTER_AGENT_PIN"
                ),
                UssdFlowStep(
                    id = "ENTER_AGENT_PIN",
                    type = StepType.REQUIRE_AUTHORIZATION,
                    expectedPrompts = listOf("Enter PIN"),
                    matchMode = PromptMatchMode.NORMALIZED_TEXT,
                    action = StepAction.PAUSE_FOR_PIN,
                    nextStepId = "FINAL_RESULT"
                ),
                UssdFlowStep(id = "FINAL_RESULT", type = StepType.MATCH_RESULT)
            )
        )
    }

    @Test
    fun `mtn cash in executes with optional confirm number and reference`() {
        val sentReplies = mutableListOf<String>()
        val controller = UssdFlowController(responseSender = { reply ->
            sentReplies.add(reply)
            true
        })
        val vars = UssdInputHandler.TransactionVariables(
            customerPhone = "0244123456",
            amount = "50",
            network = NetworkType.MTN,
            reference = "AMA123456"
        )

        assertTrue(controller.startFlow("tx-ci-1", mtnCashFlowProfile("CASH_IN"), vars).isSuccess)
        controller.onPromptObserved("1. Transfer 2. Pay Cash Out 3. Receive Cash In")
        controller.onPromptObserved("1. Mobile Money User 2. Non MoMo")
        controller.onPromptObserved("Enter number:")
        controller.onPromptObserved("Re-enter number:")
        controller.onPromptObserved("Enter amount:")
        controller.onPromptObserved("Enter reference:")
        controller.onPromptObserved("Enter PIN to confirm:")

        assertEquals(
            listOf("3", "1", "0244123456", "0244123456", "50", "AMA123456"),
            sentReplies
        )
        assertTrue(controller.sessionState.value is UssdSessionState.PausedForPinAuthorization)

        controller.resumeAfterManualPin("Cash In of GHS 50.00 completed successfully. Ref: CI9988")
        val finalState = controller.sessionState.value as UssdSessionState.Completed
        assertEquals(OutcomeCategory.SUCCESSFUL, finalState.outcome.category)
        assertEquals("CI9988", finalState.outcome.providerReference)
    }

    @Test
    fun `mtn cash in skips optional confirm number and reference when omitted by network`() {
        val sentReplies = mutableListOf<String>()
        val controller = UssdFlowController(responseSender = { reply ->
            sentReplies.add(reply)
            true
        })
        val vars = UssdInputHandler.TransactionVariables(
            customerPhone = "0244123456",
            amount = "50",
            network = NetworkType.MTN,
            reference = "AMA123456"
        )

        assertTrue(controller.startFlow("tx-ci-2", mtnCashFlowProfile("CASH_IN"), vars).isSuccess)
        controller.onPromptObserved("3. Receive Cash In")
        controller.onPromptObserved("1. Mobile Money User")
        controller.onPromptObserved("Enter number:")
        // Network skips CONFIRM_CUSTOMER_NUMBER and asks directly for amount
        controller.onPromptObserved("Enter amount:")
        // Network skips ENTER_REFERENCE and asks directly for PIN
        controller.onPromptObserved("Enter PIN:")

        assertEquals(listOf("3", "1", "0244123456", "50"), sentReplies)
        assertTrue(controller.sessionState.value is UssdSessionState.PausedForPinAuthorization)

        controller.resumeAfterManualPin("Transaction failed due to insufficient balance")
        val finalState = controller.sessionState.value as UssdSessionState.Completed
        assertEquals(OutcomeCategory.INSUFFICIENT_BALANCE, finalState.outcome.category)
    }

    @Test
    fun `mtn cash in proceeds on configured step after optional skip check`() {
        val sentReplies = mutableListOf<String>()
        val controller = UssdFlowController(responseSender = { reply ->
            sentReplies.add(reply)
            true
        })
        val vars = UssdInputHandler.TransactionVariables(
            customerPhone = "0244123456",
            amount = "50",
            network = NetworkType.MTN
        )

        assertTrue(controller.startFlow("tx-ci-3", mtnCashFlowProfile("CASH_IN"), vars).isSuccess)
        controller.onPromptObserved("3. Receive Cash In")
        controller.onPromptObserved("1. Mobile Money User")
        controller.onPromptObserved("Enter number:")
        controller.onPromptObserved("Please confirm mobile number:")

        assertEquals(listOf("3", "1", "0244123456", "0244123456"), sentReplies)
        assertTrue(controller.sessionState.value is UssdSessionState.AwaitingPrompt)
    }

    @Test
    fun `mtn cash out emits WaitingForCustomerApproval on pending approval and handles terminal states`() {
        val sentReplies = mutableListOf<String>()
        val controller = UssdFlowController(responseSender = { reply ->
            sentReplies.add(reply)
            true
        })
        val vars = UssdInputHandler.TransactionVariables(
            customerPhone = "0541234567",
            amount = "120",
            network = NetworkType.MTN,
            reference = "AMA777"
        )

        assertTrue(controller.startFlow("tx-co-1", mtnCashFlowProfile("CASH_OUT"), vars).isSuccess)
        controller.onPromptObserved("2. Pay Cash Out")
        controller.onPromptObserved("1. Mobile Money User")
        controller.onPromptObserved("Enter number:")
        controller.onPromptObserved("Enter amount:")
        controller.onPromptObserved("Enter reference:")
        controller.onPromptObserved("Enter PIN:")

        assertEquals(listOf("2", "1", "0541234567", "120", "AMA777"), sentReplies)
        assertTrue(controller.sessionState.value is UssdSessionState.PausedForPinAuthorization)

        controller.resumeAfterManualPin("Request sent. Awaiting customer approval on their phone.")
        assertTrue(controller.sessionState.value is UssdSessionState.WaitingForCustomerApproval)

        // Declined outcome in a separate Cash Out session
        val controllerDeclined = UssdFlowController(responseSender = { true })
        assertTrue(controllerDeclined.startFlow("tx-co-2", mtnCashFlowProfile("CASH_OUT"), vars).isSuccess)
        controllerDeclined.onPromptObserved("2. Pay Cash Out")
        controllerDeclined.onPromptObserved("1. Mobile Money User")
        controllerDeclined.onPromptObserved("Enter number:")
        controllerDeclined.onPromptObserved("Enter amount:")
        controllerDeclined.onPromptObserved("Enter PIN:")
        controllerDeclined.resumeAfterManualPin("Transaction declined by customer")
        val declinedState = controllerDeclined.sessionState.value as UssdSessionState.Completed
        assertEquals(OutcomeCategory.FAILED, declinedState.outcome.category)

        // Unknown/unexpected outcome in a separate Cash Out session
        val controllerUnknown = UssdFlowController(responseSender = { true })
        assertTrue(controllerUnknown.startFlow("tx-co-3", mtnCashFlowProfile("CASH_OUT"), vars).isSuccess)
        controllerUnknown.onPromptObserved("2. Pay Cash Out")
        controllerUnknown.onPromptObserved("1. Mobile Money User")
        controllerUnknown.onPromptObserved("Enter number:")
        controllerUnknown.onPromptObserved("Enter amount:")
        controllerUnknown.onPromptObserved("Enter PIN:")
        controllerUnknown.resumeAfterManualPin("Service response unclear")
        val unknownState = controllerUnknown.sessionState.value as UssdSessionState.Completed
        assertEquals(OutcomeCategory.UNEXPECTED, unknownState.outcome.category)
    }

    @Test
    fun `resolveReference returns sanitized alphanumeric or generated non-one reference`() {
        val handler = UssdInputHandler()
        val custom = handler.resolveVariable(
            "reference",
            UssdInputHandler.TransactionVariables(reference = "TX-9988")
        ) as UssdInputHandler.ValidationResult.Valid
        assertEquals("TX-9988", custom.value)

        val generated = handler.resolveVariable(
            "reference",
            UssdInputHandler.TransactionVariables(reference = "")
        ) as UssdInputHandler.ValidationResult.Valid
        assertTrue(generated.value != "1")
        assertTrue(generated.value.startsWith("AMA"))
        assertTrue(generated.value.length >= 6)

        val controlRejected = handler.resolveVariable(
            "reference",
            UssdInputHandler.TransactionVariables(reference = "BAD\nREF")
        )
        assertTrue(controlRejected is UssdInputHandler.ValidationResult.Invalid)
    }
}

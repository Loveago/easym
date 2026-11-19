package com.easymomo.app

import com.easymomo.app.domain.TransactionLifecycleManager
import com.easymomo.app.domain.model.NetworkType
import com.easymomo.app.domain.model.TransactionStatus
import com.easymomo.app.ussd.UssdFlowController
import com.easymomo.app.ussd.UssdInputHandler
import com.easymomo.app.ussd.model.OutcomeCategory
import com.easymomo.app.ussd.model.UssdFlowProfile
import com.easymomo.app.ussd.model.UssdSessionState
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class AdministratorUssdFlowsTest {

    private data class BundledFlowsWrapper(
        @SerializedName("flows") val flows: List<UssdFlowProfile> = emptyList()
    )

    private fun loadBundledFlows(): Map<String, UssdFlowProfile> {
        val candidates = listOf(
            File("src/main/assets/ussd_flows.json"),
            File("app/src/main/assets/ussd_flows.json")
        )
        val file = candidates.firstOrNull { it.exists() }
            ?: error("Could not locate ussd_flows.json from ${File(".").absolutePath}")
        val wrapper = Gson().fromJson(file.readText(), BundledFlowsWrapper::class.java)
        return wrapper.flows.associateBy { it.flowId }
    }

    @Test
    fun `mtn cash in and cash out v4 have no reference step and stop if reference prompt appears`() {
        val flows = loadBundledFlows()
        val cashIn = flows.getValue("MTN_CASH_IN")
        val cashOut = flows.getValue("MTN_CASH_OUT")

        assertTrue(cashIn.version >= 9)
        assertTrue(cashOut.version >= 11)
        assertFalse(cashIn.steps.any { it.id.contains("REFERENCE") || it.variable == "reference" })
        assertFalse(cashOut.steps.any { it.id.contains("REFERENCE") || it.variable == "reference" })

        val sent = mutableListOf<String>()
        val controller = UssdFlowController(responseSender = { sent.add(it); true })
        val vars = UssdInputHandler.TransactionVariables(
            customerPhone = "0244123456",
            amount = "50",
            network = NetworkType.MTN
        )

        assertTrue(controller.startFlow("tx-ci", cashIn, vars).isSuccess)
        controller.onPromptObserved("3. Receive Cash In")
        controller.onPromptObserved("1. Mobile Money User")
        controller.onPromptObserved("Enter phone number:")
        controller.onPromptObserved("Re-enter phone number:")
        controller.onPromptObserved("Enter amount:")
        // At AUTHORIZE_PIN, no reference "1" is ever sent
        controller.onPromptObserved("Enter MoMo PIN:")

        assertEquals(listOf("3", "1", "0244123456", "0244123456", "50"), sent)
        assertTrue(controller.sessionState.value is UssdSessionState.PausedForPinAuthorization)
    }

    @Test
    fun `mtn bundle purchase executes supplied sequence 5-2-1-2-phone-confirm-1-amount-pin`() {
        val profile = loadBundledFlows().getValue("MTN_DATA")
        val sent = mutableListOf<String>()
        val controller = UssdFlowController(responseSender = { sent.add(it); true })
        val vars = UssdInputHandler.TransactionVariables(
            customerPhone = "0244123456",
            amount = "20",
            network = NetworkType.MTN
        )

        assertTrue(controller.startFlow("tx-mtn-bundle", profile, vars).isSuccess)
        controller.onPromptObserved("5. Airtime & Bundles")
        controller.onPromptObserved("2. Data Bundle")
        controller.onPromptObserved("1. Buy Bundle")
        controller.onPromptObserved("2. Other Number")
        controller.onPromptObserved("Enter customer number:")
        controller.onPromptObserved("Re-enter customer number:")
        controller.onPromptObserved("1. Specify Amount")
        controller.onPromptObserved("Enter amount:")
        controller.onPromptObserved("Enter MoMo PIN:")

        assertEquals(listOf("5", "2", "1", "2", "0244123456", "0244123456", "1", "20"), sent)
        assertTrue(controller.sessionState.value is UssdSessionState.PausedForPinAuthorization)

        controller.onPromptObserved("Bundle purchase of GHS 20.00 completed successfully.")
        val done = controller.sessionState.value as UssdSessionState.Completed
        assertEquals(OutcomeCategory.SUCCESSFUL, done.outcome.category)
    }

    @Test
    fun `mtn evd bundle purchase executes supplied sequence 5-1-phone-confirm-1-amount-pin`() {
        val profile = loadBundledFlows().getValue("MTN_EVD_BUNDLE")
        assertEquals(13, profile.version)
        val sent = mutableListOf<String>()
        val controller = UssdFlowController(responseSender = { sent.add(it); true })
        val vars = UssdInputHandler.TransactionVariables(
            customerPhone = "0244123456",
            amount = "20",
            network = NetworkType.MTN
        )

        assertTrue(controller.startFlow("tx-mtn-evd-bundle", profile, vars).isSuccess)
        controller.onPromptObserved("5. Airtime & Bundles")
        controller.onPromptObserved("1. Data Bundle")
        controller.onPromptObserved("Enter customer number:")
        controller.onPromptObserved("Re-enter customer number:")
        controller.onPromptObserved("1. Specify Amount")
        controller.onPromptObserved("Enter amount:")
        controller.onPromptObserved("Enter MoMo PIN:")

        assertEquals(listOf("5", "1", "0244123456", "0244123456", "1", "20"), sent)
        assertTrue(controller.sessionState.value is UssdSessionState.PausedForPinAuthorization)

        controller.onPromptObserved("Bundle purchase of GHS 20.00 completed successfully.")
        val done = controller.sessionState.value as UssdSessionState.Completed
        assertEquals(OutcomeCategory.SUCCESSFUL, done.outcome.category)

        // Also verify optional skip if carrier jumps directly from confirmation to Enter amount
        val sentSkipped = mutableListOf<String>()
        val controllerSkipped = UssdFlowController(responseSender = { sentSkipped.add(it); true })
        assertTrue(controllerSkipped.startFlow("tx-mtn-evd-bundle-skip", profile, vars).isSuccess)
        controllerSkipped.onPromptObserved("5. Airtime & Bundles")
        controllerSkipped.onPromptObserved("1. Data Bundle")
        controllerSkipped.onPromptObserved("Enter customer number:")
        controllerSkipped.onPromptObserved("Enter amount:")
        controllerSkipped.onPromptObserved("Enter MM PIN:")
        assertEquals(listOf("5", "1", "0244123456", "20"), sentSkipped)
        assertTrue(controllerSkipped.sessionState.value is UssdSessionState.PausedForPinAuthorization)
    }

    @Test
    fun `telecel bundle purchase pauses for manual operational id and optional pin`() {
        val profile = loadBundledFlows().getValue("TELECEL_DATA")
        val sent = mutableListOf<String>()
        val controller = UssdFlowController(responseSender = { sent.add(it); true })
        val vars = UssdInputHandler.TransactionVariables(
            customerPhone = "0201234567",
            amount = "15",
            network = NetworkType.TELECEL
        )

        assertTrue(controller.startFlow("tx-tel-bundle", profile, vars).isSuccess)
        controller.onPromptObserved("3. Airtime & Bundles")
        controller.onPromptObserved("2. Data Bundle")
        controller.onPromptObserved("1. Buy Bundle")
        controller.onPromptObserved("Enter amount:")
        controller.onPromptObserved("2. Other number")
        controller.onPromptObserved("Enter recipient phone number:")
        controller.onPromptObserved("Enter Operational ID:")

        assertEquals(listOf("3", "2", "1", "15", "2", "0201234567"), sent)
        val opState = controller.sessionState.value as UssdSessionState.PausedForOperationalId
        assertEquals("Please enter the Telecel operational ID in the USSD window.", opState.instruction)

        // Duplicate operational ID prompt keeps it paused without auto-entering anything
        controller.onPromptObserved("Enter Operational ID:")
        assertTrue(controller.sessionState.value is UssdSessionState.PausedForOperationalId)

        // Agent submits operational ID -> network asks for PIN
        controller.onPromptObserved("Enter PIN:")
        assertTrue(controller.sessionState.value is UssdSessionState.PausedForPinAuthorization)

        // Agent submits PIN -> final response
        controller.onPromptObserved("Bundle activated successfully.")
        val done = controller.sessionState.value as UssdSessionState.Completed
        assertEquals(OutcomeCategory.SUCCESSFUL, done.outcome.category)
        assertEquals(6, sent.size)
    }

    @Test
    fun `telecel cash in executes 1-phone-amount then pauses for operational id and pin`() {
        val profile = loadBundledFlows().getValue("TELECEL_CASH_IN")
        val sent = mutableListOf<String>()
        val controller = UssdFlowController(responseSender = { sent.add(it); true })
        val vars = UssdInputHandler.TransactionVariables(
            customerPhone = "0201234567",
            amount = "100",
            network = NetworkType.TELECEL
        )

        assertTrue(controller.startFlow("tx-tel-ci", profile, vars).isSuccess)
        controller.onPromptObserved("1. Cash In")
        controller.onPromptObserved("Enter customer phone number:")
        controller.onPromptObserved("Enter amount:")
        controller.onPromptObserved("Enter Operational ID:")

        assertEquals(listOf("1", "0201234567", "100"), sent)
        assertTrue(controller.sessionState.value is UssdSessionState.PausedForOperationalId)

        controller.onPromptObserved("Enter PIN:")
        assertTrue(controller.sessionState.value is UssdSessionState.PausedForPinAuthorization)

        controller.onPromptObserved("Cash In completed successfully.")
        assertEquals(OutcomeCategory.SUCCESSFUL, (controller.sessionState.value as UssdSessionState.Completed).outcome.category)
    }

    @Test
    fun `telecel cash in auto-enters operational id when toggle is on and id is saved`() {
        val profile = loadBundledFlows().getValue("TELECEL_CASH_IN")
        val sent = mutableListOf<String>()
        val controller = UssdFlowController(responseSender = { sent.add(it); true })
        val vars = UssdInputHandler.TransactionVariables(
            customerPhone = "0201234567",
            amount = "100",
            network = NetworkType.TELECEL,
            operationalId = "123456"
        )

        assertTrue(controller.startFlow("tx-tel-ci-auto", profile, vars).isSuccess)
        controller.onPromptObserved("1. Cash In")
        controller.onPromptObserved("Enter customer phone number:")
        controller.onPromptObserved("Enter amount:")
        controller.onPromptObserved("Enter Operational ID:")
        controller.onPromptObserved("Enter PIN:")

        assertEquals(listOf("1", "0201234567", "100", "123456"), sent)
        assertTrue(controller.sessionState.value is UssdSessionState.PausedForPinAuthorization)

        controller.onPromptObserved("Cash In completed successfully.")
        assertEquals(OutcomeCategory.SUCCESSFUL, (controller.sessionState.value as UssdSessionState.Completed).outcome.category)
    }

    @Test
    fun `mtn airtime executes 5-1-5-amount-phone-confirm-pin`() {
        val profile = loadBundledFlows().getValue("MTN_AIRTIME")
        val sent = mutableListOf<String>()
        val controller = UssdFlowController(responseSender = { sent.add(it); true })
        val vars = UssdInputHandler.TransactionVariables(
            customerPhone = "0244123456",
            amount = "10",
            network = NetworkType.MTN
        )

        assertTrue(controller.startFlow("tx-mtn-air", profile, vars).isSuccess)
        controller.onPromptObserved("5. Airtime & Bundles")
        controller.onPromptObserved("1. Buy Airtime")
        controller.onPromptObserved("5. Other Number")
        controller.onPromptObserved("Enter amount:")
        controller.onPromptObserved("Enter phone number:")
        controller.onPromptObserved("Confirm number:")
        controller.onPromptObserved("Enter MoMo PIN:")

        assertEquals(listOf("5", "1", "5", "10", "0244123456", "0244123456"), sent)
        assertTrue(controller.sessionState.value is UssdSessionState.PausedForPinAuthorization)

        controller.onPromptObserved("Airtime recharged successfully.")
        assertEquals(OutcomeCategory.SUCCESSFUL, (controller.sessionState.value as UssdSessionState.Completed).outcome.category)
    }

    @Test
    fun `mtn evd airtime executes 2-2-amount-phone-confirm-pin`() {
        val profile = loadBundledFlows().getValue("MTN_EVD_AIRTIME")
        val sent = mutableListOf<String>()
        val controller = UssdFlowController(responseSender = { sent.add(it); true })
        val vars = UssdInputHandler.TransactionVariables(
            customerPhone = "0244123456",
            amount = "10",
            network = NetworkType.MTN
        )

        assertTrue(controller.startFlow("tx-mtn-evd-air", profile, vars).isSuccess)
        controller.onPromptObserved("2. Airtime & Bundles")
        controller.onPromptObserved("2. Buy Airtime")
        controller.onPromptObserved("Enter amount:")
        controller.onPromptObserved("Enter phone number:")
        controller.onPromptObserved("Confirm number:")
        controller.onPromptObserved("Enter MoMo PIN:")

        assertEquals(listOf("2", "2", "10", "0244123456", "0244123456"), sent)
        assertTrue(controller.sessionState.value is UssdSessionState.PausedForPinAuthorization)

        controller.onPromptObserved("Airtime recharged successfully.")
        assertEquals(OutcomeCategory.SUCCESSFUL, (controller.sessionState.value as UssdSessionState.Completed).outcome.category)
    }

    @Test
    fun `telecel airtime executes 7-1-operational-id-pin`() {
        val profile = loadBundledFlows().getValue("TELECEL_AIRTIME")
        val sent = mutableListOf<String>()
        val controller = UssdFlowController(responseSender = { sent.add(it); true })
        val vars = UssdInputHandler.TransactionVariables(
            customerPhone = "0201234567",
            amount = "10",
            network = NetworkType.TELECEL
        )

        assertTrue(controller.startFlow("tx-tel-air", profile, vars).isSuccess)
        controller.onPromptObserved("7. My Account")
        controller.onPromptObserved("1. Airtime")
        controller.onPromptObserved("Enter Operational ID:")

        assertEquals(listOf("7", "1"), sent)
        assertTrue(controller.sessionState.value is UssdSessionState.PausedForOperationalId)

        controller.onPromptObserved("Enter PIN:")
        assertTrue(controller.sessionState.value is UssdSessionState.PausedForPinAuthorization)

        controller.onPromptObserved("Airtime recharged successfully.")
        assertEquals(OutcomeCategory.SUCCESSFUL, (controller.sessionState.value as UssdSessionState.Completed).outcome.category)
    }

    @Test
    fun `gig flows are enabled and incomplete statement flows remain disabled`() {
        val flows = loadBundledFlows()
        val mtnGig = flows.getValue("MTN_GIG")
        val telecelGig = flows.getValue("TELECEL_GIG")
        val mtnStatement = flows.getValue("MTN_STATEMENT")
        val telecelStatement = flows.getValue("TELECEL_STATEMENT")

        assertFalse(mtnGig.requiresSimVerification())
        assertFalse(telecelGig.requiresSimVerification())
        assertTrue(mtnStatement.requiresSimVerification())
        assertTrue(telecelStatement.requiresSimVerification())

        // Verify MTN_GIG (4 -> phone -> manual PIN)
        val sentGig = mutableListOf<String>()
        val gigController = UssdFlowController(responseSender = { sentGig.add(it); true })
        assertTrue(
            gigController.startFlow(
                "tx-gig",
                mtnGig,
                UssdInputHandler.TransactionVariables(customerPhone = "0244123456", network = NetworkType.MTN)
            ).isSuccess
        )
        gigController.onPromptObserved("4. Gig")
        gigController.onPromptObserved("Enter phone number:")
        gigController.onPromptObserved("Enter MoMo PIN:")
        assertEquals(listOf("4", "0244123456"), sentGig)
        assertTrue(gigController.sessionState.value is UssdSessionState.PausedForPinAuthorization)

        // Verify TELECEL_GIG (3 -> 2 -> manual Operational ID)
        val sentTelGig = mutableListOf<String>()
        val telGigController = UssdFlowController(responseSender = { sentTelGig.add(it); true })
        assertTrue(
            telGigController.startFlow(
                "tx-tel-gig",
                telecelGig,
                UssdInputHandler.TransactionVariables(network = NetworkType.TELECEL)
            ).isSuccess
        )
        telGigController.onPromptObserved("3. Airtime & Bundles")
        telGigController.onPromptObserved("2. Gig Bundle")
        telGigController.onPromptObserved("Enter Operational ID:")
        assertEquals(listOf("3", "2"), sentTelGig)
        assertTrue(telGigController.sessionState.value is UssdSessionState.PausedForOperationalId)

        // Verify MTN_STATEMENT prefix (7 -> 4 -> 1 -> PIN)
        val sentMtnStmt = mutableListOf<String>()
        val mtnStmtController = UssdFlowController(responseSender = { sentMtnStmt.add(it); true })
        assertTrue(
            mtnStmtController.startFlow(
                "tx-mtn-stmt",
                mtnStatement.copy(enabled = true, verifiedWithSim = true),
                UssdInputHandler.TransactionVariables(network = NetworkType.MTN)
            ).isSuccess
        )
        mtnStmtController.onPromptObserved("7. My Wallet")
        mtnStmtController.onPromptObserved("4. Statement")
        mtnStmtController.onPromptObserved("1. Mini Statement")
        mtnStmtController.onPromptObserved("Enter MoMo PIN:")
        assertEquals(listOf("7", "4", "1"), sentMtnStmt)
        assertTrue(mtnStmtController.sessionState.value is UssdSessionState.PausedForPinAuthorization)

        // Verify TELECEL_STATEMENT prefix (7 -> 3 -> Operational ID -> WAIT_FOR_PROMPT)
        val sentTelStmt = mutableListOf<String>()
        val telStmtController = UssdFlowController(responseSender = { sentTelStmt.add(it); true })
        assertTrue(
            telStmtController.startFlow(
                "tx-tel-stmt",
                telecelStatement.copy(enabled = true, verifiedWithSim = true),
                UssdInputHandler.TransactionVariables(network = NetworkType.TELECEL)
            ).isSuccess
        )
        telStmtController.onPromptObserved("7. My Account")
        telStmtController.onPromptObserved("3. Statement")
        telStmtController.onPromptObserved("Enter Operational ID:")
        assertEquals(listOf("7", "3"), sentTelStmt)
        assertTrue(telStmtController.sessionState.value is UssdSessionState.PausedForOperationalId)
        telStmtController.onPromptObserved("Select statement period:")
        assertTrue(telStmtController.sessionState.value is UssdSessionState.ManualInterventionRequired)
    }

    @Test
    fun `telecel flows pause for manual operational id and keep pin manual`() {
        val flows = loadBundledFlows()
        val vars = UssdInputHandler.TransactionVariables(
            customerPhone = "0201234567",
            amount = "25",
            network = NetworkType.TELECEL
        )

        // Telecel Cash In pauses for manual Operational ID, then PIN
        val sentCashIn = mutableListOf<String>()
        val cashInController = UssdFlowController(responseSender = { sentCashIn.add(it); true })
        assertTrue(cashInController.startFlow("tx-tci-op", flows.getValue("TELECEL_CASH_IN"), vars).isSuccess)
        cashInController.onPromptObserved("1. Cash In")
        cashInController.onPromptObserved("Enter customer phone number:")
        cashInController.onPromptObserved("Enter amount:")
        cashInController.onPromptObserved("Enter Operational ID:")
        assertTrue(cashInController.sessionState.value is UssdSessionState.PausedForOperationalId)
        cashInController.onPromptObserved("Enter PIN:")
        assertEquals(listOf("1", "0201234567", "25"), sentCashIn)
        assertTrue(cashInController.sessionState.value is UssdSessionState.PausedForPinAuthorization)

        // Telecel Data pauses for manual Operational ID, then PIN
        val sentData = mutableListOf<String>()
        val dataController = UssdFlowController(responseSender = { sentData.add(it); true })
        assertTrue(dataController.startFlow("tx-tdata-op", flows.getValue("TELECEL_DATA"), vars).isSuccess)
        dataController.onPromptObserved("3. Airtime & Bundles")
        dataController.onPromptObserved("2. Data Bundle")
        dataController.onPromptObserved("1. Buy Bundle")
        dataController.onPromptObserved("Enter amount:")
        dataController.onPromptObserved("2. Other number")
        dataController.onPromptObserved("Enter phone number:")
        dataController.onPromptObserved("Enter Operational ID:")
        assertTrue(dataController.sessionState.value is UssdSessionState.PausedForOperationalId)
        dataController.onPromptObserved("Enter PIN:")
        assertEquals(listOf("3", "2", "1", "25", "2", "0201234567"), sentData)
        assertTrue(dataController.sessionState.value is UssdSessionState.PausedForPinAuthorization)

        // Telecel Airtime with manual Operational ID (7 -> 1 -> Operational ID -> PIN)
        val sentAir = mutableListOf<String>()
        val airController = UssdFlowController(responseSender = { sentAir.add(it); true })
        assertTrue(airController.startFlow("tx-tair-op", flows.getValue("TELECEL_AIRTIME"), vars).isSuccess)
        airController.onPromptObserved("7. My Account")
        airController.onPromptObserved("1. Airtime")
        airController.onPromptObserved("Enter Operational ID:")
        assertTrue(airController.sessionState.value is UssdSessionState.PausedForOperationalId)
        airController.onPromptObserved("Enter PIN:")
        assertEquals(listOf("7", "1"), sentAir)
        assertTrue(airController.sessionState.value is UssdSessionState.PausedForPinAuthorization)

        // Blank operational_id fails validation in UssdInputHandler
        val validation = UssdInputHandler().resolveVariable(
            "operational_id",
            UssdInputHandler.TransactionVariables(operationalId = "")
        )
        assertTrue(validation is UssdInputHandler.ValidationResult.Invalid)
    }

    @Test
    fun `mtn cash out executes end-to-end and skips optional steps when omitted`() {
        val cashOut = loadBundledFlows().getValue("MTN_CASH_OUT")
        val sent = mutableListOf<String>()
        val controller = UssdFlowController(responseSender = { sent.add(it); true })
        val vars = UssdInputHandler.TransactionVariables(
            customerPhone = "0244123456",
            amount = "1.00",
            network = NetworkType.MTN
        )

        assertTrue(controller.startFlow("tx-co-e2e", cashOut, vars).isSuccess)
        controller.onPromptObserved("1. Transfer Money\n2. Pay Cash Out")
        // Network skips SELECT_USER_TYPE and goes straight to ENTER_CUSTOMER_NUMBER
        controller.onPromptObserved("Enter mobile number:")
        // Network skips CONFIRM_CUSTOMER_NUMBER and goes straight to ENTER_AMOUNT
        controller.onPromptObserved("Enter amount (min 1.00):")
        controller.onPromptObserved("Enter MM PIN:")
        // Subsequent Accessibility event on the same PIN dialog must keep it paused
        controller.onPromptObserved("Enter secret code:")

        assertEquals(listOf("2", "0244123456", "1"), sent)
        assertTrue(controller.sessionState.value is UssdSessionState.PausedForPinAuthorization)

        controller.onPromptObserved("Cash Out of GHS 1.00 approved. Awaiting customer approval.")
        assertTrue(controller.sessionState.value is UssdSessionState.WaitingForCustomerApproval)
    }

    @Test
    fun `mtn data executes end-to-end when confirm number is skipped and pauses on mm pin`() {
        val mtnData = loadBundledFlows().getValue("MTN_DATA")
        val sent = mutableListOf<String>()
        val controller = UssdFlowController(responseSender = { sent.add(it); true })
        val vars = UssdInputHandler.TransactionVariables(
            customerPhone = "0244123456",
            amount = "1.00",
            network = NetworkType.MTN
        )

        assertTrue(controller.startFlow("tx-data-skip", mtnData, vars).isSuccess)
        controller.onPromptObserved("5. Airtime & Bundles")
        controller.onPromptObserved("2. Data Bundle")
        controller.onPromptObserved("1. Buy Bundle")
        controller.onPromptObserved("2. Other Number")
        controller.onPromptObserved("Enter recipient number:")
        // CONFIRM_CUSTOMER_NUMBER skipped by network -> SELECT_OPTION_1_AMOUNT
        controller.onPromptObserved("1. Specify Amount")
        controller.onPromptObserved("Enter amount (GHS 1.00 - 500.00):")
        controller.onPromptObserved("Enter MM PIN:")

        assertEquals(listOf("5", "2", "1", "2", "0244123456", "1", "1"), sent)
        assertTrue(controller.sessionState.value is UssdSessionState.PausedForPinAuthorization)

        controller.onPromptObserved("Bundle purchase failed: Insufficient balance.")
        val done = controller.sessionState.value as UssdSessionState.Completed
        assertEquals(OutcomeCategory.INSUFFICIENT_BALANCE, done.outcome.category)
    }

    @Test
    fun `mtn merchant pay executes 1-1-phone-confirm-amount and ends for manual completion`() {
        val merchantPay = loadBundledFlows().getValue("MTN_MERCHANT_PAY")
        assertEquals(14, merchantPay.version)
        val sent = mutableListOf<String>()
        val controller = UssdFlowController(responseSender = { sent.add(it); true })
        val vars = UssdInputHandler.TransactionVariables(
            merchantCode = "0598516996",
            amount = "25",
            network = NetworkType.MTN
        )

        assertTrue(controller.startFlow("tx-mp-1", merchantPay, vars).isSuccess)
        controller.onPromptObserved("1. Pay To")
        controller.onPromptObserved("1. Merchant")
        controller.onPromptObserved("Enter merchant number:")
        controller.onPromptObserved("Confirm merchant number:")
        controller.onPromptObserved("Enter amount:")

        assertEquals(listOf("1", "1", "0598516996", "0598516996", "25"), sent)
        val state = controller.sessionState.value as UssdSessionState.Completed
        assertEquals(OutcomeCategory.PENDING, state.outcome.category)

        // Subsequent manual prompts (reference / PIN) must not trigger any further auto-submissions
        controller.onPromptObserved("Enter reference:")
        controller.onPromptObserved("Enter MM PIN:")
        assertEquals(listOf("1", "1", "0598516996", "0598516996", "25"), sent)
    }

    @Test
    fun `mtn cash out completes to pin authorization with and without optional user type and confirm number`() {
        val cashOut = loadBundledFlows().getValue("MTN_CASH_OUT")
        val vars = UssdInputHandler.TransactionVariables(
            customerPhone = "0244123456",
            amount = "50",
            network = NetworkType.MTN
        )

        // 1. WITH both SELECT_USER_TYPE (including generic numbered menu) + CONFIRM_CUSTOMER_NUMBER
        val sentBoth = mutableListOf<String>()
        val ctrlBoth = UssdFlowController(responseSender = { sentBoth.add(it); true })
        assertTrue(ctrlBoth.startFlow("tx-co-both", cashOut, vars).isSuccess)
        ctrlBoth.onPromptObserved("1. Transfer Money\n2. Pay Cash Out")
        ctrlBoth.onPromptObserved("1. Registered Customer\n2. Token")
        ctrlBoth.onPromptObserved("Enter Mobile Number:")
        ctrlBoth.onPromptObserved("Repeat Mobile Number:")
        ctrlBoth.onPromptObserved("Enter Amount:")
        ctrlBoth.onPromptObserved("Enter MM PIN:")
        assertEquals(listOf("2", "1", "0244123456", "0244123456", "50"), sentBoth)
        assertTrue(ctrlBoth.sessionState.value is UssdSessionState.PausedForPinAuthorization)

        // 2. Skipping SELECT_USER_TYPE only (keeps CONFIRM_CUSTOMER_NUMBER)
        val sentNoUserType = mutableListOf<String>()
        val ctrlNoUserType = UssdFlowController(responseSender = { sentNoUserType.add(it); true })
        assertTrue(ctrlNoUserType.startFlow("tx-co-no-ut", cashOut, vars).isSuccess)
        ctrlNoUserType.onPromptObserved("1. Transfer Money\n2. Pay Cash Out")
        ctrlNoUserType.onPromptObserved("Enter Mobile Number:")
        ctrlNoUserType.onPromptObserved("Re-enter Mobile Number:")
        ctrlNoUserType.onPromptObserved("Enter Amount:")
        ctrlNoUserType.onPromptObserved("Enter MM PIN:")
        assertEquals(listOf("2", "0244123456", "0244123456", "50"), sentNoUserType)
        assertTrue(ctrlNoUserType.sessionState.value is UssdSessionState.PausedForPinAuthorization)

        // 3. Skipping CONFIRM_CUSTOMER_NUMBER only (keeps SELECT_USER_TYPE)
        val sentNoConfirm = mutableListOf<String>()
        val ctrlNoConfirm = UssdFlowController(responseSender = { sentNoConfirm.add(it); true })
        assertTrue(ctrlNoConfirm.startFlow("tx-co-no-conf", cashOut, vars).isSuccess)
        ctrlNoConfirm.onPromptObserved("1. Transfer Money\n2. Pay Cash Out")
        ctrlNoConfirm.onPromptObserved("1. Mobile Money User\n2. Non-Mobile Money")
        ctrlNoConfirm.onPromptObserved("Enter Mobile Number:")
        ctrlNoConfirm.onPromptObserved("Enter Amount:")
        ctrlNoConfirm.onPromptObserved("Enter MM PIN:")
        assertEquals(listOf("2", "1", "0244123456", "50"), sentNoConfirm)
        assertTrue(ctrlNoConfirm.sessionState.value is UssdSessionState.PausedForPinAuthorization)

        // 4. Skipping BOTH SELECT_USER_TYPE and CONFIRM_CUSTOMER_NUMBER
        val sentNeither = mutableListOf<String>()
        val ctrlNeither = UssdFlowController(responseSender = { sentNeither.add(it); true })
        assertTrue(ctrlNeither.startFlow("tx-co-neither", cashOut, vars).isSuccess)
        ctrlNeither.onPromptObserved("1. Transfer Money\n2. Pay Cash Out")
        ctrlNeither.onPromptObserved("Enter Mobile Number:")
        ctrlNeither.onPromptObserved("Enter Amount:")
        ctrlNeither.onPromptObserved("Enter MM PIN:")
        assertEquals(listOf("2", "0244123456", "50"), sentNeither)
        assertTrue(ctrlNeither.sessionState.value is UssdSessionState.PausedForPinAuthorization)
    }

    @Test
    fun `mtn data completes to pin authorization both with specify amount and when carrier skips directly to enter amount`() {
        val mtnData = loadBundledFlows().getValue("MTN_DATA")
        val vars = UssdInputHandler.TransactionVariables(
            customerPhone = "0244123456",
            amount = "25",
            network = NetworkType.MTN
        )

        // 1. WITH 1. Specify Amount
        val sentWithSpecify = mutableListOf<String>()
        val ctrlWithSpecify = UssdFlowController(responseSender = { sentWithSpecify.add(it); true })
        assertTrue(ctrlWithSpecify.startFlow("tx-data-with-spec", mtnData, vars).isSuccess)
        ctrlWithSpecify.onPromptObserved("5. Airtime & Bundles")
        ctrlWithSpecify.onPromptObserved("2. Data Bundle")
        ctrlWithSpecify.onPromptObserved("1. Buy Bundle")
        ctrlWithSpecify.onPromptObserved("2. Other Number")
        ctrlWithSpecify.onPromptObserved("Enter customer number:")
        ctrlWithSpecify.onPromptObserved("Re-enter customer number:")
        ctrlWithSpecify.onPromptObserved("1. Specify Amount")
        ctrlWithSpecify.onPromptObserved("Enter Amount:")
        ctrlWithSpecify.onPromptObserved("Enter MM PIN:")
        assertEquals(listOf("5", "2", "1", "2", "0244123456", "0244123456", "1", "25"), sentWithSpecify)
        assertTrue(ctrlWithSpecify.sessionState.value is UssdSessionState.PausedForPinAuthorization)

        // 2. Carrier skips 1. Specify Amount directly to Enter Amount: (after confirm number)
        val sentDirectAfterConfirm = mutableListOf<String>()
        val ctrlDirectAfterConfirm = UssdFlowController(responseSender = { sentDirectAfterConfirm.add(it); true })
        assertTrue(ctrlDirectAfterConfirm.startFlow("tx-data-direct-1", mtnData, vars).isSuccess)
        ctrlDirectAfterConfirm.onPromptObserved("5. Airtime & Bundles")
        ctrlDirectAfterConfirm.onPromptObserved("2. Data Bundle")
        ctrlDirectAfterConfirm.onPromptObserved("1. Buy Bundle")
        ctrlDirectAfterConfirm.onPromptObserved("2. Other Number")
        ctrlDirectAfterConfirm.onPromptObserved("Enter customer number:")
        ctrlDirectAfterConfirm.onPromptObserved("Re-enter customer number:")
        ctrlDirectAfterConfirm.onPromptObserved("Enter Amount:")
        ctrlDirectAfterConfirm.onPromptObserved("Enter MM PIN:")
        assertEquals(listOf("5", "2", "1", "2", "0244123456", "0244123456", "25"), sentDirectAfterConfirm)
        assertTrue(ctrlDirectAfterConfirm.sessionState.value is UssdSessionState.PausedForPinAuthorization)

        // 3. Carrier skips BOTH confirm number AND 1. Specify Amount directly to Enter Amount:
        val sentDirectBothSkipped = mutableListOf<String>()
        val ctrlDirectBothSkipped = UssdFlowController(responseSender = { sentDirectBothSkipped.add(it); true })
        assertTrue(ctrlDirectBothSkipped.startFlow("tx-data-direct-2", mtnData, vars).isSuccess)
        ctrlDirectBothSkipped.onPromptObserved("5. Airtime & Bundles")
        ctrlDirectBothSkipped.onPromptObserved("2. Data Bundle")
        ctrlDirectBothSkipped.onPromptObserved("1. Buy Bundle")
        ctrlDirectBothSkipped.onPromptObserved("2. Other Number")
        ctrlDirectBothSkipped.onPromptObserved("Enter customer number:")
        ctrlDirectBothSkipped.onPromptObserved("Enter Amount (GHS 1.00 - 500.00):")
        ctrlDirectBothSkipped.onPromptObserved("Enter MM PIN:")
        assertEquals(listOf("5", "2", "1", "2", "0244123456", "25"), sentDirectBothSkipped)
        assertTrue(ctrlDirectBothSkipped.sessionState.value is UssdSessionState.PausedForPinAuthorization)
    }

    @Test
    fun `bundled mtn flows ignore stale duplicate enter customer number without consuming confirm step`() {
        val flows = loadBundledFlows()
        val vars = UssdInputHandler.TransactionVariables(
            customerPhone = "0244123456",
            amount = "30",
            network = NetworkType.MTN
        )

        // MTN_CASH_OUT with stale duplicate "Enter customer number:"
        val sentCashOut = mutableListOf<String>()
        val ctrlCashOut = UssdFlowController(responseSender = { sentCashOut.add(it); true })
        assertTrue(ctrlCashOut.startFlow("tx-co-stale", flows.getValue("MTN_CASH_OUT"), vars).isSuccess)
        ctrlCashOut.onPromptObserved("2. Pay Cash Out")
        ctrlCashOut.onPromptObserved("Enter customer number:")
        ctrlCashOut.onPromptObserved("Enter customer number:") // stale duplicate ignored
        ctrlCashOut.onPromptObserved("Re-enter customer number:")
        ctrlCashOut.onPromptObserved("Enter amount:")
        ctrlCashOut.onPromptObserved("Enter MM PIN:")
        assertEquals(listOf("2", "0244123456", "0244123456", "30"), sentCashOut)
        assertTrue(ctrlCashOut.sessionState.value is UssdSessionState.PausedForPinAuthorization)

        // MTN_DATA with stale duplicate "Enter customer number:"
        val sentData = mutableListOf<String>()
        val ctrlData = UssdFlowController(responseSender = { sentData.add(it); true })
        assertTrue(ctrlData.startFlow("tx-data-stale", flows.getValue("MTN_DATA"), vars).isSuccess)
        ctrlData.onPromptObserved("5. Airtime & Bundles")
        ctrlData.onPromptObserved("2. Data Bundle")
        ctrlData.onPromptObserved("1. Buy Bundle")
        ctrlData.onPromptObserved("2. Other Number")
        ctrlData.onPromptObserved("Enter customer number:")
        ctrlData.onPromptObserved("Enter customer number:") // stale duplicate ignored
        ctrlData.onPromptObserved("Confirm customer number:")
        ctrlData.onPromptObserved("Enter Amount:")
        ctrlData.onPromptObserved("Enter MM PIN:")
        assertEquals(listOf("5", "2", "1", "2", "0244123456", "0244123456", "30"), sentData)
        assertTrue(ctrlData.sessionState.value is UssdSessionState.PausedForPinAuthorization)
    }

    @Test
    fun `mtn balance and commission do not false-pause on change momo pin menu item`() {
        val flows = loadBundledFlows()
        val mtnVars = UssdInputHandler.TransactionVariables(network = NetworkType.MTN)

        // MTN_BALANCE
        val sentBal = mutableListOf<String>()
        val ctrlBal = UssdFlowController(responseSender = { sentBal.add(it); true })
        assertTrue(ctrlBal.startFlow("tx-mtn-bal", flows.getValue("MTN_BALANCE"), mtnVars).isSuccess)
        ctrlBal.onPromptObserved("1. Transfer Money\n7. My Wallet")
        ctrlBal.onPromptObserved("1. Check Balance\n2. Check Commission\n3. Change MoMo PIN\n4. Reset PIN")
        assertEquals(listOf("7", "1"), sentBal)
        assertTrue(ctrlBal.sessionState.value is UssdSessionState.AwaitingPrompt)
        ctrlBal.onPromptObserved("Enter PIN to check available balance:")
        assertTrue(ctrlBal.sessionState.value is UssdSessionState.PausedForPinAuthorization)
        // Second slightly reworded PIN event must stay in PausedForPinAuthorization
        ctrlBal.onPromptObserved("Please enter PIN to check available balance:")
        assertTrue(ctrlBal.sessionState.value is UssdSessionState.PausedForPinAuthorization)
        ctrlBal.onPromptObserved("Current Balance: GHS 1,250.00. Available Balance: GHS 1,250.00.")
        val balDone = ctrlBal.sessionState.value as UssdSessionState.Completed
        assertEquals(OutcomeCategory.SUCCESSFUL, balDone.outcome.category)

        // MTN_COMMISSION
        val sentComm = mutableListOf<String>()
        val ctrlComm = UssdFlowController(responseSender = { sentComm.add(it); true })
        assertTrue(ctrlComm.startFlow("tx-mtn-comm", flows.getValue("MTN_COMMISSION"), mtnVars).isSuccess)
        ctrlComm.onPromptObserved("7. My Wallet")
        ctrlComm.onPromptObserved("1. Check Balance\n2. Check Commission\n3. Change MoMo PIN\n4. Reset MoMo PIN")
        assertEquals(listOf("7", "2"), sentComm)
        assertTrue(ctrlComm.sessionState.value is UssdSessionState.AwaitingPrompt)
        ctrlComm.onPromptObserved("Enter MM PIN:")
        assertTrue(ctrlComm.sessionState.value is UssdSessionState.PausedForPinAuthorization)
        ctrlComm.onPromptObserved("Commission balance: GHS 45.50")
        val commDone = ctrlComm.sessionState.value as UssdSessionState.Completed
        assertEquals(OutcomeCategory.SUCCESSFUL, commDone.outcome.category)

        // TELECEL_BALANCE
        val sentTelBal = mutableListOf<String>()
        val ctrlTelBal = UssdFlowController(responseSender = { sentTelBal.add(it); true })
        assertTrue(
            ctrlTelBal.startFlow(
                "tx-tel-bal",
                flows.getValue("TELECEL_BALANCE"),
                UssdInputHandler.TransactionVariables(network = NetworkType.TELECEL)
            ).isSuccess
        )
        ctrlTelBal.onPromptObserved("6. My Account")
        ctrlTelBal.onPromptObserved("1. Check Balance\n2. Change PIN")
        assertEquals(listOf("6", "1"), sentTelBal)
        ctrlTelBal.onPromptObserved("Enter PIN:")
        assertTrue(ctrlTelBal.sessionState.value is UssdSessionState.PausedForPinAuthorization)
    }

    @Test
    fun `mtn cash out skips user type on enter subscriber number and classifies customer must enter momo pin as pending approval`() {
        val cashOut = loadBundledFlows().getValue("MTN_CASH_OUT")
        val sent = mutableListOf<String>()
        val controller = UssdFlowController(responseSender = { sent.add(it); true })
        val vars = UssdInputHandler.TransactionVariables(
            customerPhone = "0244123456",
            amount = "50",
            network = NetworkType.MTN
        )

        assertTrue(controller.startFlow("tx-co-sub", cashOut, vars).isSuccess)
        controller.onPromptObserved("1. Transfer Money\n2. Pay Cash Out")
        // Carrier skips SELECT_USER_TYPE and directly prompts "Enter Subscriber Number:"
        controller.onPromptObserved("Enter Subscriber Number:")
        controller.onPromptObserved("Repeat Subscriber Number:")
        controller.onPromptObserved("Enter Amount:")
        controller.onPromptObserved("Enter MoMo PIN to approve:")
        assertEquals(listOf("2", "0244123456", "0244123456", "50"), sent)
        assertTrue(controller.sessionState.value is UssdSessionState.PausedForPinAuthorization)
        // Subsequent PIN dialog wording variants with "to approve" / "approval" stay paused
        controller.onPromptObserved("Please enter MoMo PIN to approve:")
        assertTrue(controller.sessionState.value is UssdSessionState.PausedForPinAuthorization)
        controller.onPromptObserved("Enter PIN for approval:")
        assertTrue(controller.sessionState.value is UssdSessionState.PausedForPinAuthorization)

        // Post-PIN message mentions "enter their MoMo PIN to approve" + "initiated"
        controller.onPromptObserved("Cash Out of GHS 50.00 initiated. Customer must enter their MoMo PIN to approve.")
        assertTrue(
            "Expected WaitingForCustomerApproval, got ${controller.sessionState.value}",
            controller.sessionState.value is UssdSessionState.WaitingForCustomerApproval
        )
        assertTrue(
            TransactionLifecycleManager.canTransition(
                TransactionStatus.PROCESSING,
                TransactionStatus.AWAITING_VERIFICATION
            )
        )
    }

    @Test
    fun `post-pin wrong pin and incorrect pin responses complete as failed instead of staying paused`() {
        val flows = loadBundledFlows()

        // 1. "Airtime recharge unsuccessful. Wrong PIN." from PausedForPinAuthorization
        val sentAir = mutableListOf<String>()
        val ctrlAir = UssdFlowController(responseSender = { sentAir.add(it); true })
        val airVars = UssdInputHandler.TransactionVariables(
            customerPhone = "0244123456",
            amount = "10",
            network = NetworkType.MTN
        )
        assertTrue(ctrlAir.startFlow("tx-air-wrongpin", flows.getValue("MTN_AIRTIME"), airVars).isSuccess)
        ctrlAir.onPromptObserved("5. Airtime & Bundles")
        ctrlAir.onPromptObserved("1. Buy Airtime")
        ctrlAir.onPromptObserved("5. Other Number")
        ctrlAir.onPromptObserved("Enter amount:")
        ctrlAir.onPromptObserved("Enter recipient number:")
        ctrlAir.onPromptObserved("Enter MoMo PIN:")
        assertTrue(ctrlAir.sessionState.value is UssdSessionState.PausedForPinAuthorization)

        ctrlAir.onPromptObserved("Airtime recharge unsuccessful. Wrong PIN.")
        val airDone = ctrlAir.sessionState.value as UssdSessionState.Completed
        assertEquals(OutcomeCategory.FAILED, airDone.outcome.category)

        // 2. "Balance enquiry: Incorrect PIN" from PausedForPinAuthorization
        val sentBal = mutableListOf<String>()
        val ctrlBal = UssdFlowController(responseSender = { sentBal.add(it); true })
        assertTrue(
            ctrlBal.startFlow(
                "tx-bal-badpin",
                flows.getValue("MTN_BALANCE"),
                UssdInputHandler.TransactionVariables(network = NetworkType.MTN)
            ).isSuccess
        )
        ctrlBal.onPromptObserved("7. My Wallet")
        ctrlBal.onPromptObserved("1. Check Balance")
        ctrlBal.onPromptObserved("Enter MoMo PIN:")
        assertTrue(ctrlBal.sessionState.value is UssdSessionState.PausedForPinAuthorization)

        ctrlBal.onPromptObserved("Balance enquiry: Incorrect PIN")
        val balDone = ctrlBal.sessionState.value as UssdSessionState.Completed
        assertEquals(OutcomeCategory.FAILED, balDone.outcome.category)
    }

    @Test
    fun `stale duplicate main menu on mtn data is ignored at select option 2 and merchant pay skips optional reference`() {
        val flows = loadBundledFlows()

        // Stale duplicate Main Menu on MTN_DATA ignored at SELECT_OPTION_2
        val sentData = mutableListOf<String>()
        val ctrlData = UssdFlowController(responseSender = { sentData.add(it); true })
        val dataVars = UssdInputHandler.TransactionVariables(
            customerPhone = "0244123456",
            amount = "20",
            network = NetworkType.MTN
        )
        assertTrue(ctrlData.startFlow("tx-data-menudup", flows.getValue("MTN_DATA"), dataVars).isSuccess)
        val mainMenu = "1. Transfer Money\n2. MoMoPay & Pay Bill\n5. Airtime & Bundles"
        ctrlData.onPromptObserved(mainMenu)
        // Stale duplicate of Main Menu (contains "bundle" and "2.") must NOT fire SELECT_OPTION_2
        ctrlData.onPromptObserved(mainMenu)
        assertEquals(listOf("5"), sentData)

        val subMenu2 = "1. Airtime\n2. Data Bundle"
        ctrlData.onPromptObserved(subMenu2)
        // Stale duplicate of subMenu2 (contains "bundle", "data", "1.") must NOT fire SELECT_OPTION_1
        ctrlData.onPromptObserved(subMenu2)
        assertEquals(listOf("5", "2"), sentData)

        ctrlData.onPromptObserved("1. Buy Data Bundle")
        ctrlData.onPromptObserved("2. Buy for Other")
        ctrlData.onPromptObserved("Enter recipient number:")
        ctrlData.onPromptObserved("Enter Amount:")
        ctrlData.onPromptObserved("Enter MM PIN:")
        assertEquals(listOf("5", "2", "1", "2", "0244123456", "20"), sentData)
        assertTrue(ctrlData.sessionState.value is UssdSessionState.PausedForPinAuthorization)

        // MTN_MERCHANT_PAY skips optional CONFIRM_MERCHANT_NUMBER directly to ENTER_AMOUNT and ends cleanly after amount
        val sentMp = mutableListOf<String>()
        val ctrlMp = UssdFlowController(responseSender = { sentMp.add(it); true })
        val mpVars = UssdInputHandler.TransactionVariables(
            merchantCode = "0598516996",
            amount = "40",
            network = NetworkType.MTN
        )
        assertTrue(ctrlMp.startFlow("tx-mp-skipconf", flows.getValue("MTN_MERCHANT_PAY"), mpVars).isSuccess)
        ctrlMp.onPromptObserved("1. Pay To")
        ctrlMp.onPromptObserved("1. Merchant")
        ctrlMp.onPromptObserved("Enter Merchant Number:")
        ctrlMp.onPromptObserved("Enter Amount:")
        assertEquals(listOf("1", "1", "0598516996", "40"), sentMp)
        val mpDone = ctrlMp.sessionState.value as UssdSessionState.Completed
        assertEquals(OutcomeCategory.PENDING, mpDone.outcome.category)
    }
}

package com.easymomo.app

import com.easymomo.app.domain.model.NetworkType
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

        assertEquals(6, cashIn.version)
        assertEquals(6, cashOut.version)
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
    fun `mtn evd bundle purchase executes supplied sequence 2-3-1-phone-confirm-amount-pin`() {
        val profile = loadBundledFlows().getValue("MTN_EVD_BUNDLE")
        val sent = mutableListOf<String>()
        val controller = UssdFlowController(responseSender = { sent.add(it); true })
        val vars = UssdInputHandler.TransactionVariables(
            customerPhone = "0244123456",
            amount = "20",
            network = NetworkType.MTN
        )

        assertTrue(controller.startFlow("tx-mtn-evd-bundle", profile, vars).isSuccess)
        controller.onPromptObserved("2. Airtime & Bundles")
        controller.onPromptObserved("3. Data Bundle")
        controller.onPromptObserved("1. Buy Bundle")
        controller.onPromptObserved("Enter customer number:")
        controller.onPromptObserved("Re-enter customer number:")
        controller.onPromptObserved("Enter amount:")
        controller.onPromptObserved("Enter MoMo PIN:")

        assertEquals(listOf("2", "3", "1", "0244123456", "0244123456", "20"), sent)
        assertTrue(controller.sessionState.value is UssdSessionState.PausedForPinAuthorization)

        controller.onPromptObserved("Bundle purchase of GHS 20.00 completed successfully.")
        val done = controller.sessionState.value as UssdSessionState.Completed
        assertEquals(OutcomeCategory.SUCCESSFUL, done.outcome.category)
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
    fun `telecel airtime executes 3-1-phone-confirm-amount-operational-id-pin`() {
        val profile = loadBundledFlows().getValue("TELECEL_AIRTIME")
        val sent = mutableListOf<String>()
        val controller = UssdFlowController(responseSender = { sent.add(it); true })
        val vars = UssdInputHandler.TransactionVariables(
            customerPhone = "0201234567",
            amount = "10",
            network = NetworkType.TELECEL
        )

        assertTrue(controller.startFlow("tx-tel-air", profile, vars).isSuccess)
        controller.onPromptObserved("3. Airtime & Bundles")
        controller.onPromptObserved("1. Buy Airtime")
        controller.onPromptObserved("Enter phone number:")
        controller.onPromptObserved("Repeat number:")
        controller.onPromptObserved("Enter amount:")
        controller.onPromptObserved("Enter Operational ID:")

        assertEquals(listOf("3", "1", "0201234567", "0201234567", "10"), sent)
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

        // Telecel Airtime with saved Operational ID and repeat customer phone
        val sentAir = mutableListOf<String>()
        val airController = UssdFlowController(responseSender = { sentAir.add(it); true })
        assertTrue(airController.startFlow("tx-tair-op", flows.getValue("TELECEL_AIRTIME"), vars).isSuccess)
        airController.onPromptObserved("3. Airtime & Bundles")
        airController.onPromptObserved("1. Buy Airtime")
        airController.onPromptObserved("Enter phone number:")
        airController.onPromptObserved("Repeat phone number:")
        airController.onPromptObserved("Enter amount:")
        airController.onPromptObserved("Enter Operational ID:")
        assertTrue(airController.sessionState.value is UssdSessionState.PausedForOperationalId)
        airController.onPromptObserved("Enter PIN:")
        assertEquals(listOf("3", "1", "0201234567", "0201234567", "25"), sentAir)
        assertTrue(airController.sessionState.value is UssdSessionState.PausedForPinAuthorization)

        // Blank operational_id fails validation in UssdInputHandler
        val validation = UssdInputHandler().resolveVariable(
            "operational_id",
            UssdInputHandler.TransactionVariables(operationalId = "")
        )
        assertTrue(validation is UssdInputHandler.ValidationResult.Invalid)
    }
}

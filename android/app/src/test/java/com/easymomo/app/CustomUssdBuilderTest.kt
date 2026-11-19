package com.easymomo.app

import com.easymomo.app.data.local.CustomUssdDao
import com.easymomo.app.data.local.CustomUssdFlowEntity
import com.easymomo.app.data.local.UssdCaptureEventEntity
import com.easymomo.app.data.local.UssdCaptureSessionEntity
import com.easymomo.app.data.repository.CustomUssdRepository
import com.easymomo.app.domain.model.NetworkType
import com.easymomo.app.ussd.UssdFlowController
import com.easymomo.app.ussd.UssdInputHandler
import com.easymomo.app.ussd.capture.CustomUssdFlowBuilder
import com.easymomo.app.ussd.capture.CustomUssdOperationType
import com.easymomo.app.ussd.capture.SimpleCustomUssdFlow
import com.easymomo.app.ussd.model.OutcomeCategory
import com.easymomo.app.ussd.model.UssdSessionState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CustomUssdBuilderTest {

    private class InMemoryCustomUssdDao : CustomUssdDao {
        val flows = MutableStateFlow<List<CustomUssdFlowEntity>>(emptyList())
        override suspend fun insertCaptureSession(session: UssdCaptureSessionEntity) {}
        override suspend fun updateCaptureSession(session: UssdCaptureSessionEntity) {}
        override suspend fun getCaptureSessionById(sessionId: String): UssdCaptureSessionEntity? = null
        override fun observeCaptureSessions(): Flow<List<UssdCaptureSessionEntity>> = MutableStateFlow(emptyList())
        override suspend fun getActiveOrLingeringSessions(): List<UssdCaptureSessionEntity> = emptyList()
        override suspend fun updateSessionStatus(sessionId: String, status: String, finishedAt: Long?) {}
        override suspend fun renameCaptureSession(sessionId: String, flowName: String) {}
        override suspend fun deleteCaptureSession(sessionId: String) {}
        override suspend fun insertCaptureEvent(event: UssdCaptureEventEntity) {}
        override suspend fun getCaptureEventsForSession(sessionId: String): List<UssdCaptureEventEntity> = emptyList()
        override fun observeCaptureEventsForSession(sessionId: String): Flow<List<UssdCaptureEventEntity>> =
            MutableStateFlow(emptyList())

        override suspend fun insertCustomFlow(flow: CustomUssdFlowEntity) {
            flows.value = flows.value.filterNot { it.id == flow.id } + flow
        }

        override suspend fun updateCustomFlow(flow: CustomUssdFlowEntity) {
            insertCustomFlow(flow)
        }

        override fun observeAllCustomFlows(): Flow<List<CustomUssdFlowEntity>> =
            flows.map { it.sortedByDescending { f -> f.updatedAt } }

        override suspend fun getCustomFlowById(flowId: String): CustomUssdFlowEntity? =
            flows.value.firstOrNull { it.id == flowId }

        override suspend fun deleteCustomFlow(flowId: String) {
            flows.value = flows.value.filterNot { it.id == flowId }
        }
    }

    @Test
    fun `formatPreview and validate cover step-by-step, one-time, and PIN rejection`() {
        assertEquals(
            "None",
            CustomUssdFlowBuilder.formatPreview(CustomUssdOperationType.STEP_BY_STEP, emptyList(), "")
        )
        assertEquals(
            "*171# , 1 , <phoneNumber> , <phoneNumber> , <amount>",
            CustomUssdFlowBuilder.formatPreview(
                CustomUssdOperationType.STEP_BY_STEP,
                listOf("*171#", "1", "<phoneNumber>", "<phoneNumber>", "<amount>"),
                ""
            )
        )
        assertEquals(
            "*171*1*<phoneNumber>*<amount>#",
            CustomUssdFlowBuilder.formatPreview(
                CustomUssdOperationType.ONE_TIME,
                emptyList(),
                "*171*1*<phoneNumber>*<amount>#"
            )
        )

        assertNotNull(
            CustomUssdFlowBuilder.validate("", CustomUssdOperationType.STEP_BY_STEP, listOf("*171#"), "")
        )
        assertNotNull(
            CustomUssdFlowBuilder.validate("Bad Step 1", CustomUssdOperationType.STEP_BY_STEP, listOf("1", "2"), "")
        )
        assertNotNull(
            CustomUssdFlowBuilder.validate(
                "Unsafe PIN",
                CustomUssdOperationType.STEP_BY_STEP,
                listOf("*171#", "PIN 1234"),
                ""
            )
        )
        assertNotNull(
            CustomUssdFlowBuilder.validate(
                "Unsafe One-Time",
                CustomUssdOperationType.ONE_TIME,
                emptyList(),
                "*171*pin#"
            )
        )

        assertNull(
            CustomUssdFlowBuilder.validate(
                "MTN Airtime",
                CustomUssdOperationType.STEP_BY_STEP,
                listOf("*171#", "1", "<phoneNumber>", "<phoneNumber>", "<amount>"),
                ""
            )
        )
        assertNull(
            CustomUssdFlowBuilder.validate(
                "Check Balance",
                CustomUssdOperationType.ONE_TIME,
                emptyList(),
                "*124#"
            )
        )
    }

    @Test
    fun `resolveOneTimeCode substitutes phoneNumber and amount placeholders`() {
        val resolved = CustomUssdFlowBuilder.resolveOneTimeCode(
            "*171*1*<phoneNumber>*<amount>#",
            "0244123456",
            "100"
        )
        assertEquals("*171*1*0244123456*100#", resolved)
    }

    @Test
    fun `buildStepByStepProfile executes through UssdFlowController and stops cleanly after amount`() {
        val flow = SimpleCustomUssdFlow(
            id = "custom-1",
            name = "Mtn airtime",
            operationType = CustomUssdOperationType.STEP_BY_STEP,
            steps = listOf("*171#", "1", "<phoneNumber>", "<phoneNumber>", "<amount>"),
            oneTimeCode = "",
            simSlotIndex = 0,
            formulaPreview = "*171# , 1 , <phoneNumber> , <phoneNumber> , <amount>"
        )
        val profile = CustomUssdFlowBuilder.buildStepByStepProfile(flow)
        val sent = mutableListOf<String>()
        val controller = UssdFlowController(responseSender = { sent.add(it); true })
        val vars = UssdInputHandler.TransactionVariables(
            customerPhone = "0244123456",
            amount = "100",
            network = NetworkType.MTN
        )

        assertTrue(controller.startFlow("tx-custom-1", profile, vars).isSuccess)
        controller.onPromptObserved("1. Transfer Money\n2. MoMoPay")
        controller.onPromptObserved("Enter customer number:")
        controller.onPromptObserved("Re-enter customer number:")
        controller.onPromptObserved("Enter amount:")

        assertEquals(listOf("1", "0244123456", "0244123456", "100"), sent)
        val completed = controller.sessionState.value as UssdSessionState.Completed
        assertEquals(OutcomeCategory.PENDING, completed.outcome.category)

        // Subsequent manual prompts (e.g. PIN) must not trigger any auto-entry
        controller.onPromptObserved("Enter MoMo PIN:")
        assertEquals(listOf("1", "0244123456", "0244123456", "100"), sent)
    }

    @Test
    fun `repository saves, loads, and updates step-by-step and one-time custom USSD flows`() = runBlocking {
        val dao = InMemoryCustomUssdDao()
        val repo = CustomUssdRepository(dao)

        val stepEntity = repo.saveSimpleCustomUssd(
            existingId = null,
            flowName = "Mtn airtime",
            operationType = CustomUssdOperationType.STEP_BY_STEP,
            steps = listOf("*171#", "1", "<phoneNumber>", "<phoneNumber>", "<amount>"),
            oneTimeCode = "",
            simSlotIndex = 1
        )
        assertTrue(stepEntity.enabled)

        val oneTimeEntity = repo.saveSimpleCustomUssd(
            existingId = null,
            flowName = "Quick Check",
            operationType = CustomUssdOperationType.ONE_TIME,
            steps = emptyList(),
            oneTimeCode = "*124#",
            simSlotIndex = 0
        )

        // Update the step-by-step flow
        repo.saveSimpleCustomUssd(
            existingId = stepEntity.id,
            flowName = "Mtn airtime v2",
            operationType = CustomUssdOperationType.STEP_BY_STEP,
            steps = listOf("*171#", "5", "<phoneNumber>", "<amount>"),
            oneTimeCode = "",
            simSlotIndex = 0
        )

        val loaded = repo.observeSimpleCustomFlows().first()
        assertEquals(2, loaded.size)

        val updatedStepFlow = loaded.first { it.id == stepEntity.id }
        assertEquals("Mtn airtime v2", updatedStepFlow.name)
        assertEquals(CustomUssdOperationType.STEP_BY_STEP, updatedStepFlow.operationType)
        assertEquals(listOf("*171#", "5", "<phoneNumber>", "<amount>"), updatedStepFlow.steps)
        assertEquals(0, updatedStepFlow.simSlotIndex)

        val loadedOneTime = loaded.first { it.id == oneTimeEntity.id }
        assertEquals(CustomUssdOperationType.ONE_TIME, loadedOneTime.operationType)
        assertEquals("*124#", loadedOneTime.oneTimeCode)
        assertEquals(0, loadedOneTime.simSlotIndex)
    }
}

package com.easymomo.app

import com.easymomo.app.data.local.EasyMoMoDatabase
import com.easymomo.app.data.local.CustomUssdDao
import com.easymomo.app.data.local.CustomUssdFlowEntity
import com.easymomo.app.data.local.UssdCaptureEventEntity
import com.easymomo.app.data.local.UssdCaptureSessionEntity
import com.easymomo.app.data.repository.CustomUssdRepository
import com.easymomo.app.ussd.capture.CaptureConfidence
import com.easymomo.app.ussd.capture.CaptureEventType
import com.easymomo.app.ussd.capture.CaptureSessionStatus
import com.easymomo.app.ussd.capture.CapturedNodeSnapshot
import com.easymomo.app.ussd.capture.CustomFlowStatus
import com.easymomo.app.ussd.capture.CustomFlowStepDraft
import com.easymomo.app.ussd.capture.CustomStepPlaceholderType
import com.easymomo.app.ussd.capture.UssdCaptureRecorder
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CustomUssdCaptureTest {

    private class FakeCustomUssdDao : CustomUssdDao {
        val sessions = MutableStateFlow<List<UssdCaptureSessionEntity>>(emptyList())
        val events = MutableStateFlow<List<UssdCaptureEventEntity>>(emptyList())
        val flows = MutableStateFlow<List<CustomUssdFlowEntity>>(emptyList())

        override suspend fun insertCaptureSession(session: UssdCaptureSessionEntity) {
            sessions.value = sessions.value.filterNot { it.id == session.id } + session
        }

        override suspend fun updateCaptureSession(session: UssdCaptureSessionEntity) {
            insertCaptureSession(session)
        }

        override suspend fun getCaptureSessionById(sessionId: String): UssdCaptureSessionEntity? =
            sessions.value.firstOrNull { it.id == sessionId }

        override fun observeCaptureSessions(): Flow<List<UssdCaptureSessionEntity>> =
            sessions.map { it.sortedByDescending { s -> s.startedAt } }

        override suspend fun getActiveOrLingeringSessions(): List<UssdCaptureSessionEntity> =
            sessions.value.filter {
                it.status == CaptureSessionStatus.RECORDING.name ||
                    it.status == CaptureSessionStatus.PAUSED.name
            }

        override suspend fun updateSessionStatus(sessionId: String, status: String, finishedAt: Long?) {
            sessions.value = sessions.value.map {
                if (it.id == sessionId) it.copy(status = status, finishedAt = finishedAt) else it
            }
        }

        override suspend fun renameCaptureSession(sessionId: String, flowName: String) {
            sessions.value = sessions.value.map {
                if (it.id == sessionId) it.copy(flowName = flowName) else it
            }
        }

        override suspend fun deleteCaptureSession(sessionId: String) {
            sessions.value = sessions.value.filterNot { it.id == sessionId }
            events.value = events.value.filterNot { it.sessionId == sessionId }
        }

        override suspend fun insertCaptureEvent(event: UssdCaptureEventEntity) {
            events.value = events.value.filterNot { it.id == event.id } + event
        }

        override suspend fun getCaptureEventsForSession(sessionId: String): List<UssdCaptureEventEntity> =
            events.value.filter { it.sessionId == sessionId }.sortedBy { it.sequenceNumber }

        override fun observeCaptureEventsForSession(sessionId: String): Flow<List<UssdCaptureEventEntity>> =
            events.map { list -> list.filter { it.sessionId == sessionId }.sortedBy { it.sequenceNumber } }

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
    fun `recorder classifies menu, input, confirmation, result, and excludes sensitive PIN`() {
        val persistedEvents = mutableListOf<UssdCaptureEventEntity>()
        val recorder = UssdCaptureRecorder()
        recorder.setPersistenceCallbacks(
            persistSession = {},
            persistEvent = { persistedEvents.add(it) }
        )

        val session = recorder.startSession(
            flowName = "Merchant Pay",
            network = "MTN",
            entryCode = "*171#",
            subscriptionId = 1
        )
        assertEquals(CaptureSessionStatus.RECORDING.name, session.status)

        // 1. Menu prompt
        recorder.onAccessibilitySnapshot(
            CapturedNodeSnapshot(
                packageName = "com.android.phone",
                promptText = "1) Allow Cash Out\n2) Payment\n3) Transfer Money",
                visibleButtons = listOf("Cancel", "Send"),
                hasEditableInput = true,
                timestampMs = 1000L
            )
        )
        // 2. Input prompt
        recorder.onAccessibilitySnapshot(
            CapturedNodeSnapshot(
                packageName = "com.android.phone",
                promptText = "Enter Merchant ID",
                visibleButtons = listOf("Cancel", "Send"),
                hasEditableInput = true,
                timestampMs = 2000L
            )
        )
        // 3. Sensitive PIN prompt
        recorder.onAccessibilitySnapshot(
            CapturedNodeSnapshot(
                packageName = "com.android.phone",
                promptText = "Enter your MoMo PIN to confirm payment of GHS 50",
                visibleButtons = listOf("Cancel", "Send"),
                hasEditableInput = true,
                isPasswordInput = true,
                timestampMs = 3000L
            )
        )
        // 4. Result prompt
        recorder.onAccessibilitySnapshot(
            CapturedNodeSnapshot(
                packageName = "com.android.phone",
                promptText = "Transaction successful. Reference ID: 987654",
                visibleButtons = listOf("OK"),
                hasEditableInput = false,
                timestampMs = 4000L
            )
        )

        val finished = recorder.finishCapture(unexpectedTermination = false)
        assertNotNull(finished)
        assertEquals(CaptureSessionStatus.COMPLETED.name, finished?.status)

        // Event 1 = SESSION_STARTED, 2 = MENU_PROMPT, 3 = INPUT_PROMPT, 4 = MANUAL_AUTHORIZATION, 5 = RESULT_PROMPT, 6 = SESSION_ENDED
        val types = persistedEvents.map { it.eventType }
        assertEquals(
            listOf(
                CaptureEventType.SESSION_STARTED.name,
                CaptureEventType.MENU_PROMPT.name,
                CaptureEventType.INPUT_PROMPT.name,
                CaptureEventType.MANUAL_AUTHORIZATION.name,
                CaptureEventType.RESULT_PROMPT.name,
                CaptureEventType.SESSION_ENDED.name
            ),
            types
        )

        val sensitiveEvent = persistedEvents[3]
        assertTrue(sensitiveEvent.isSensitive)
        assertEquals(CaptureConfidence.SENSITIVE.name, sensitiveEvent.confidence)
        assertNull(sensitiveEvent.selectedAction)
        assertEquals("PROTECTED_INPUT", sensitiveEvent.inputControlType)
    }

    @Test
    fun `recorder handles pause, resume, inaccessible dialog, and cancel`() {
        val persistedEvents = mutableListOf<UssdCaptureEventEntity>()
        val recorder = UssdCaptureRecorder()
        recorder.setPersistenceCallbacks(
            persistSession = {},
            persistEvent = { persistedEvents.add(it) }
        )

        recorder.startSession("Test Flow", "TELECEL", "*110#", null)
        recorder.pauseCapture()
        assertEquals(CaptureSessionStatus.PAUSED.name, recorder.activeSession.value?.status)

        // Ignored while paused
        recorder.onAccessibilitySnapshot(
            CapturedNodeSnapshot(
                packageName = "com.android.phone",
                promptText = "1) Cash In",
                hasEditableInput = true
            )
        )
        assertEquals(1, persistedEvents.size) // Only SESSION_STARTED

        recorder.resumeCapture()
        // Inaccessible blank dialog
        recorder.onAccessibilitySnapshot(
            CapturedNodeSnapshot(
                packageName = "com.android.phone",
                promptText = "",
                visibleButtons = emptyList(),
                hasEditableInput = false
            )
        )
        assertEquals(CaptureEventType.INACCESSIBLE_DIALOG.name, persistedEvents.last().eventType)
        assertEquals(CaptureConfidence.LOW.name, persistedEvents.last().confidence)

        val cancelled = recorder.cancelCapture()
        assertEquals(CaptureSessionStatus.CANCELLED.name, cancelled?.status)
        recorder.clearActiveSession()
        assertNull(recorder.activeSession.value)
    }

    @Test
    fun `repository recovers interrupted sessions, saves drafts, and enforces enable guard`() = runBlocking {
        val dao = FakeCustomUssdDao()
        val repo = CustomUssdRepository(dao)

        // Seed a lingering RECORDING session
        dao.insertCaptureSession(
            UssdCaptureSessionEntity(
                id = "sess-1",
                flowName = "Interrupted Flow",
                network = "MTN",
                entryCode = "*171#",
                subscriptionId = 1,
                status = CaptureSessionStatus.RECORDING.name,
                startedAt = 1000L
            )
        )
        dao.insertCaptureEvent(
            UssdCaptureEventEntity(
                id = "ev-1",
                sessionId = "sess-1",
                sequenceNumber = 1,
                eventType = CaptureEventType.MENU_PROMPT.name,
                promptText = "1) Pay Bill\n2) Transfer",
                visibleOptionsJson = "[\"1) Pay Bill\",\"2) Transfer\"]",
                selectedAction = null,
                inputControlType = "EDIT_TEXT",
                observedNodeClass = null,
                nodeMetadataSummary = "",
                observedTimestamp = 1500L,
                confidence = CaptureConfidence.HIGH.name,
                isSensitive = false,
                isComplete = true
            )
        )

        val recovered = repo.recoverInterruptedSessions()
        assertEquals(1, recovered.size)
        assertEquals(CaptureSessionStatus.INTERRUPTED.name, dao.getCaptureSessionById("sess-1")?.status)

        val savedFlow = repo.saveCaptureAsDraftFlow("sess-1", "Pay Bill Draft")
        assertNotNull(savedFlow)
        assertEquals(CustomFlowStatus.INCOMPLETE.name, savedFlow?.status)
        assertEquals(1, savedFlow?.stepCount)
        assertFalse(savedFlow!!.enabled)

        // Enable guard blocks DRAFT flows
        val enableDraft = repo.toggleCustomFlowEnabled(savedFlow.id, true)
        assertTrue(enableDraft.isFailure)

        // Manual draft creation + duplicate + edit + delete
        val manual = repo.createManualDraftFlow(
            flowName = "Manual Service",
            network = "TELECEL",
            entryCode = "*110#",
            serviceDescription = "Agent utility",
            transactionCategory = "AGENT_SERVICE",
            steps = listOf(
                CustomFlowStepDraft(1, CustomStepPlaceholderType.MENU_SELECTION, "Select 1"),
                CustomFlowStepDraft(2, CustomStepPlaceholderType.MANUAL_AUTHORIZATION, "Enter PIN")
            )
        )
        assertNull(manual.sourceCaptureSessionId)
        assertEquals(2, manual.stepCount)

        val dup = repo.duplicateCustomFlow(manual.id)
        assertNotNull(dup)
        assertTrue(dup!!.flowName.endsWith("(Copy)"))

        repo.updateCustomFlowMetadata(manual.id, "Manual Service Updated", "New desc", "PAYMENT")
        assertEquals("Manual Service Updated", dao.getCustomFlowById(manual.id)?.flowName)

        // Verified flow with steps can be enabled
        dao.updateCustomFlow(manual.copy(status = CustomFlowStatus.VERIFIED.name))
        assertTrue(repo.toggleCustomFlowEnabled(manual.id, true).isSuccess)
        assertTrue(dao.getCustomFlowById(manual.id)!!.enabled)

        repo.deleteCustomFlow(dup.id)
        assertNull(dao.getCustomFlowById(dup.id))
    }

    @Test
    fun `database migration 2 to 3 has expected version bounds`() {
        assertEquals(2, EasyMoMoDatabase.MIGRATION_2_3.startVersion)
        assertEquals(3, EasyMoMoDatabase.MIGRATION_2_3.endVersion)
    }
}

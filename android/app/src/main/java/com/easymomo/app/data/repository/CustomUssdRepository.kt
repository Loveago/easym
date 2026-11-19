package com.easymomo.app.data.repository

import com.easymomo.app.data.local.CustomUssdDao
import com.easymomo.app.data.local.CustomUssdFlowEntity
import com.easymomo.app.data.local.UssdCaptureEventEntity
import com.easymomo.app.data.local.UssdCaptureSessionEntity
import com.easymomo.app.ussd.capture.CaptureEventType
import com.easymomo.app.ussd.capture.CaptureSessionStatus
import com.easymomo.app.ussd.capture.CustomFlowStatus
import com.easymomo.app.ussd.capture.CustomFlowStepDraft
import com.easymomo.app.ussd.capture.CustomStepPlaceholderType
import com.easymomo.app.ussd.capture.CustomUssdFlowBuilder
import com.easymomo.app.ussd.capture.CustomUssdOperationType
import com.easymomo.app.ussd.capture.SimpleCustomUssdFlow
import com.easymomo.app.ussd.capture.UssdCaptureBridge
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.util.UUID

class CustomUssdRepository(
    private val customUssdDao: CustomUssdDao,
    private val gson: Gson = Gson()
) {
    private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    init {
        UssdCaptureBridge.recorder.setPersistenceCallbacks(
            persistSession = { session ->
                ioScope.launch { customUssdDao.insertCaptureSession(session) }
            },
            persistEvent = { event ->
                ioScope.launch { customUssdDao.insertCaptureEvent(event) }
            }
        )
    }

    fun observeCaptureSessions(): Flow<List<UssdCaptureSessionEntity>> =
        customUssdDao.observeCaptureSessions()

    fun observeCaptureEvents(sessionId: String): Flow<List<UssdCaptureEventEntity>> =
        customUssdDao.observeCaptureEventsForSession(sessionId)

    fun observeAllCustomFlows(): Flow<List<CustomUssdFlowEntity>> =
        customUssdDao.observeAllCustomFlows()

    suspend fun getCaptureSession(sessionId: String): UssdCaptureSessionEntity? =
        customUssdDao.getCaptureSessionById(sessionId)

    suspend fun getCaptureEvents(sessionId: String): List<UssdCaptureEventEntity> =
        customUssdDao.getCaptureEventsForSession(sessionId)

    suspend fun recoverInterruptedSessions(): List<UssdCaptureSessionEntity> {
        val lingering = customUssdDao.getActiveOrLingeringSessions()
        val now = System.currentTimeMillis()
        lingering.forEach { session ->
            customUssdDao.updateSessionStatus(
                sessionId = session.id,
                status = CaptureSessionStatus.INTERRUPTED.name,
                finishedAt = now
            )
        }
        return customUssdDao.getActiveOrLingeringSessions().ifEmpty {
            lingering.map { it.copy(status = CaptureSessionStatus.INTERRUPTED.name, finishedAt = now) }
        }
    }

    suspend fun renameCaptureSession(sessionId: String, newName: String) {
        val trimmed = newName.trim()
        if (trimmed.isNotEmpty()) {
            customUssdDao.renameCaptureSession(sessionId, trimmed)
        }
    }

    suspend fun discardCaptureSession(sessionId: String) {
        customUssdDao.deleteCaptureSession(sessionId)
    }

    suspend fun saveCaptureAsDraftFlow(
        sessionId: String,
        flowNameOverride: String? = null
    ): CustomUssdFlowEntity? {
        val session = customUssdDao.getCaptureSessionById(sessionId) ?: return null
        val events = customUssdDao.getCaptureEventsForSession(sessionId)
        val steps = events.mapNotNull { event ->
            when (event.eventType) {
                CaptureEventType.MENU_PROMPT.name -> CustomFlowStepDraft(
                    sequence = event.sequenceNumber,
                    stepType = CustomStepPlaceholderType.MENU_SELECTION,
                    promptPreview = event.promptText.take(120)
                )
                CaptureEventType.INPUT_PROMPT.name -> CustomFlowStepDraft(
                    sequence = event.sequenceNumber,
                    stepType = CustomStepPlaceholderType.CUSTOMER_NUMBER_PROMPT,
                    label = "Input Prompt (Needs Mapping)",
                    promptPreview = event.promptText.take(120)
                )
                CaptureEventType.CONFIRMATION_PROMPT.name -> CustomFlowStepDraft(
                    sequence = event.sequenceNumber,
                    stepType = CustomStepPlaceholderType.CONFIRMATION_PROMPT,
                    promptPreview = event.promptText.take(120)
                )
                CaptureEventType.MANUAL_AUTHORIZATION.name -> CustomFlowStepDraft(
                    sequence = event.sequenceNumber,
                    stepType = CustomStepPlaceholderType.MANUAL_AUTHORIZATION,
                    promptPreview = event.promptText.take(120),
                    isSensitive = true
                )
                CaptureEventType.RESULT_PROMPT.name -> CustomFlowStepDraft(
                    sequence = event.sequenceNumber,
                    stepType = CustomStepPlaceholderType.RESULT_PROMPT,
                    promptPreview = event.promptText.take(120)
                )
                else -> null
            }
        }
        val now = System.currentTimeMillis()
        val status = if (session.status == CaptureSessionStatus.COMPLETED.name && steps.isNotEmpty()) {
            CustomFlowStatus.DRAFT.name
        } else {
            CustomFlowStatus.INCOMPLETE.name
        }
        val draft = CustomUssdFlowEntity(
            id = UUID.randomUUID().toString(),
            flowName = (flowNameOverride ?: session.flowName).trim(),
            network = session.network,
            entryCode = session.entryCode,
            serviceDescription = "Captured from ${session.entryCode} (${session.status})",
            transactionCategory = "CUSTOM",
            status = status,
            enabled = false, // Never auto-enable captured drafts in Stage 1-2
            sourceCaptureSessionId = session.id,
            stepsJson = gson.toJson(steps),
            stepCount = steps.size,
            flowVersion = 1,
            createdAt = now,
            updatedAt = now
        )
        customUssdDao.insertCustomFlow(draft)
        return draft
    }

    suspend fun createManualDraftFlow(
        flowName: String,
        network: String,
        entryCode: String,
        serviceDescription: String,
        transactionCategory: String,
        steps: List<CustomFlowStepDraft>
    ): CustomUssdFlowEntity {
        val now = System.currentTimeMillis()
        val indexedSteps = steps.mapIndexed { idx, step -> step.copy(sequence = idx + 1) }
        val status = if (indexedSteps.isEmpty()) CustomFlowStatus.INCOMPLETE.name else CustomFlowStatus.DRAFT.name
        val entity = CustomUssdFlowEntity(
            id = UUID.randomUUID().toString(),
            flowName = flowName.trim(),
            network = network,
            entryCode = entryCode.trim(),
            serviceDescription = serviceDescription.trim(),
            transactionCategory = transactionCategory.trim().ifEmpty { "CUSTOM" },
            status = status,
            enabled = false, // Stage 1-2 drafts are never activated for normal execution
            sourceCaptureSessionId = null,
            stepsJson = gson.toJson(indexedSteps),
            stepCount = indexedSteps.size,
            flowVersion = 1,
            createdAt = now,
            updatedAt = now
        )
        customUssdDao.insertCustomFlow(entity)
        return entity
    }

    suspend fun updateCustomFlowMetadata(
        flowId: String,
        flowName: String,
        serviceDescription: String,
        transactionCategory: String
    ) {
        val existing = customUssdDao.getCustomFlowById(flowId) ?: return
        customUssdDao.updateCustomFlow(
            existing.copy(
                flowName = flowName.trim().ifEmpty { existing.flowName },
                serviceDescription = serviceDescription.trim(),
                transactionCategory = transactionCategory.trim().ifEmpty { existing.transactionCategory },
                updatedAt = System.currentTimeMillis()
            )
        )
    }

    suspend fun duplicateCustomFlow(flowId: String): CustomUssdFlowEntity? {
        val existing = customUssdDao.getCustomFlowById(flowId) ?: return null
        val now = System.currentTimeMillis()
        val copy = existing.copy(
            id = UUID.randomUUID().toString(),
            flowName = "${existing.flowName} (Copy)",
            status = CustomFlowStatus.DRAFT.name,
            enabled = false,
            createdAt = now,
            updatedAt = now
        )
        customUssdDao.insertCustomFlow(copy)
        return copy
    }

    suspend fun toggleCustomFlowEnabled(flowId: String, enable: Boolean): Result<Unit> {
        val existing = customUssdDao.getCustomFlowById(flowId)
            ?: return Result.failure(IllegalArgumentException("Flow not found"))
        if (enable && (existing.status == CustomFlowStatus.DRAFT.name ||
                existing.status == CustomFlowStatus.INCOMPLETE.name ||
                existing.stepCount == 0)
        ) {
            return Result.failure(
                IllegalStateException("Untested or incomplete custom flows cannot be enabled for normal transactions.")
            )
        }
        customUssdDao.updateCustomFlow(
            existing.copy(enabled = enable, updatedAt = System.currentTimeMillis())
        )
        return Result.success(Unit)
    }

    suspend fun deleteCustomFlow(flowId: String) {
        customUssdDao.deleteCustomFlow(flowId)
    }

    fun observeSimpleCustomFlows(): Flow<List<SimpleCustomUssdFlow>> =
        observeAllCustomFlows().map { list -> list.map(::toSimpleFlow) }

    suspend fun saveSimpleCustomUssd(
        existingId: String?,
        flowName: String,
        operationType: CustomUssdOperationType,
        steps: List<String>,
        oneTimeCode: String,
        simSlotIndex: Int
    ): CustomUssdFlowEntity {
        val now = System.currentTimeMillis()
        val cleanSteps = steps.map { it.trim() }.filter { it.isNotEmpty() }
        val cleanOneTime = oneTimeCode.trim()
        val entryCode = when (operationType) {
            CustomUssdOperationType.STEP_BY_STEP -> cleanSteps.firstOrNull().orEmpty()
            CustomUssdOperationType.ONE_TIME -> cleanOneTime
        }
        val stepDrafts = when (operationType) {
            CustomUssdOperationType.STEP_BY_STEP -> cleanSteps.mapIndexed { idx, token ->
                val stepType = when {
                    token.equals(CustomUssdFlowBuilder.VAR_PHONE, ignoreCase = true) ->
                        CustomStepPlaceholderType.CUSTOMER_NUMBER_PROMPT
                    token.equals(CustomUssdFlowBuilder.VAR_AMOUNT, ignoreCase = true) ->
                        CustomStepPlaceholderType.AMOUNT_PROMPT
                    else -> CustomStepPlaceholderType.MENU_SELECTION
                }
                CustomFlowStepDraft(
                    sequence = idx + 1,
                    stepType = stepType,
                    label = token,
                    promptPreview = token,
                    isSensitive = false,
                    value = token
                )
            }
            CustomUssdOperationType.ONE_TIME -> listOf(
                CustomFlowStepDraft(
                    sequence = 1,
                    stepType = CustomStepPlaceholderType.MENU_SELECTION,
                    label = cleanOneTime,
                    promptPreview = cleanOneTime,
                    isSensitive = false,
                    value = cleanOneTime
                )
            )
        }
        val preview = CustomUssdFlowBuilder.formatPreview(operationType, cleanSteps, cleanOneTime)
        val existing = existingId?.let { customUssdDao.getCustomFlowById(it) }
        val entity = if (existing != null) {
            existing.copy(
                flowName = flowName.trim(),
                network = "SIM_${simSlotIndex + 1}",
                entryCode = entryCode,
                serviceDescription = preview,
                transactionCategory = operationType.name,
                status = CustomFlowStatus.VERIFIED.name,
                enabled = true,
                stepsJson = gson.toJson(stepDrafts),
                stepCount = stepDrafts.size,
                flowVersion = existing.flowVersion + 1,
                updatedAt = now
            )
        } else {
            CustomUssdFlowEntity(
                id = UUID.randomUUID().toString(),
                flowName = flowName.trim(),
                network = "SIM_${simSlotIndex + 1}",
                entryCode = entryCode,
                serviceDescription = preview,
                transactionCategory = operationType.name,
                status = CustomFlowStatus.VERIFIED.name,
                enabled = true,
                sourceCaptureSessionId = null,
                stepsJson = gson.toJson(stepDrafts),
                stepCount = stepDrafts.size,
                flowVersion = 1,
                createdAt = now,
                updatedAt = now
            )
        }
        customUssdDao.insertCustomFlow(entity)
        return entity
    }

    fun toSimpleFlow(entity: CustomUssdFlowEntity): SimpleCustomUssdFlow {
        val opType = if (entity.transactionCategory == CustomUssdOperationType.ONE_TIME.name) {
            CustomUssdOperationType.ONE_TIME
        } else {
            CustomUssdOperationType.STEP_BY_STEP
        }
        val slotIndex = when (entity.network.uppercase()) {
            "SIM_2" -> 1
            else -> 0
        }
        val parsedSteps = parseSteps(entity.stepsJson)
            .map { it.value.ifBlank { it.label }.trim() }
            .filter { it.isNotEmpty() }
            .let { tokens ->
                if (opType == CustomUssdOperationType.STEP_BY_STEP &&
                    entity.entryCode.isNotBlank() &&
                    tokens.firstOrNull() != entity.entryCode
                ) {
                    listOf(entity.entryCode) + tokens
                } else {
                    tokens
                }
            }
        val oneTime = if (opType == CustomUssdOperationType.ONE_TIME) {
            entity.entryCode.ifBlank { parsedSteps.firstOrNull().orEmpty() }
        } else {
            ""
        }
        val preview = CustomUssdFlowBuilder.formatPreview(opType, parsedSteps, oneTime)
        return SimpleCustomUssdFlow(
            id = entity.id,
            name = entity.flowName,
            operationType = opType,
            steps = parsedSteps,
            oneTimeCode = oneTime,
            simSlotIndex = slotIndex,
            formulaPreview = preview
        )
    }

    fun parseSteps(stepsJson: String): List<CustomFlowStepDraft> {
        val type = object : TypeToken<List<CustomFlowStepDraft>>() {}.type
        return runCatching { gson.fromJson<List<CustomFlowStepDraft>>(stepsJson, type) }.getOrNull().orEmpty()
    }
}

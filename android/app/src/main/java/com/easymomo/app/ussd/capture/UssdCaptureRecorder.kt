package com.easymomo.app.ussd.capture

import com.easymomo.app.data.local.UssdCaptureEventEntity
import com.easymomo.app.data.local.UssdCaptureSessionEntity
import com.google.gson.Gson
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

/**
 * Pure Kotlin USSD Capture Engine.
 * Observes accessibility snapshots only during an active capture session,
 * classifies prompts vs interactions conservatively, and strictly excludes PINs/credentials.
 */
class UssdCaptureRecorder(
    private val gson: Gson = Gson()
) {
    private val _activeSession = MutableStateFlow<UssdCaptureSessionEntity?>(null)
    val activeSession: StateFlow<UssdCaptureSessionEntity?> = _activeSession.asStateFlow()

    private val _capturedEvents = MutableStateFlow<List<UssdCaptureEventEntity>>(emptyList())
    val capturedEvents: StateFlow<List<UssdCaptureEventEntity>> = _capturedEvents.asStateFlow()

    private var onPersistSession: ((UssdCaptureSessionEntity) -> Unit)? = null
    private var onPersistEvent: ((UssdCaptureEventEntity) -> Unit)? = null

    private var lastEventFingerprint: String? = null
    private var sequenceCounter: Int = 0

    fun setPersistenceCallbacks(
        persistSession: (UssdCaptureSessionEntity) -> Unit,
        persistEvent: (UssdCaptureEventEntity) -> Unit
    ) {
        onPersistSession = persistSession
        onPersistEvent = persistEvent
    }

    fun isCaptureActive(): Boolean {
        val status = _activeSession.value?.status ?: return false
        return status == CaptureSessionStatus.RECORDING.name || status == CaptureSessionStatus.PAUSED.name
    }

    fun isRecording(): Boolean = _activeSession.value?.status == CaptureSessionStatus.RECORDING.name

    fun startSession(
        flowName: String,
        network: String,
        entryCode: String,
        subscriptionId: Int?
    ): UssdCaptureSessionEntity {
        val now = System.currentTimeMillis()
        val session = UssdCaptureSessionEntity(
            id = UUID.randomUUID().toString(),
            flowName = flowName.trim(),
            network = network,
            entryCode = entryCode.trim(),
            subscriptionId = subscriptionId,
            startedAt = now,
            finishedAt = null,
            status = CaptureSessionStatus.RECORDING.name,
            capturedEventCount = 0,
            captureVersion = 1
        )
        sequenceCounter = 0
        lastEventFingerprint = null
        _capturedEvents.value = emptyList()
        _activeSession.value = session
        onPersistSession?.invoke(session)

        appendEvent(
            session = session,
            eventType = CaptureEventType.SESSION_STARTED,
            promptText = "Dialed $entryCode",
            visibleOptions = emptyList(),
            selectedAction = null,
            inputControlType = null,
            observedNodeClass = null,
            nodeMetadataSummary = "Network=$network",
            timestampMs = now,
            confidence = CaptureConfidence.HIGH,
            isSensitive = false,
            isComplete = true
        )
        return _activeSession.value!!
    }

    fun pauseCapture() {
        val cur = _activeSession.value ?: return
        if (cur.status != CaptureSessionStatus.RECORDING.name) return
        val updated = cur.copy(status = CaptureSessionStatus.PAUSED.name)
        _activeSession.value = updated
        onPersistSession?.invoke(updated)
    }

    fun resumeCapture() {
        val cur = _activeSession.value ?: return
        if (cur.status != CaptureSessionStatus.PAUSED.name) return
        val updated = cur.copy(status = CaptureSessionStatus.RECORDING.name)
        _activeSession.value = updated
        onPersistSession?.invoke(updated)
    }

    fun finishCapture(unexpectedTermination: Boolean = false): UssdCaptureSessionEntity? {
        val cur = _activeSession.value ?: return null
        val now = System.currentTimeMillis()
        val hasResult = _capturedEvents.value.any { it.eventType == CaptureEventType.RESULT_PROMPT.name }
        val hasInaccessible = _capturedEvents.value.any { it.eventType == CaptureEventType.INACCESSIBLE_DIALOG.name }
        val finalStatus = when {
            unexpectedTermination || !hasResult || hasInaccessible -> CaptureSessionStatus.INCOMPLETE.name
            else -> CaptureSessionStatus.COMPLETED.name
        }
        appendEvent(
            session = cur,
            eventType = CaptureEventType.SESSION_ENDED,
            promptText = if (unexpectedTermination) "USSD session ended unexpectedly" else "Capture finished by agent",
            visibleOptions = emptyList(),
            selectedAction = null,
            inputControlType = null,
            observedNodeClass = null,
            nodeMetadataSummary = "FinalStatus=$finalStatus",
            timestampMs = now,
            confidence = if (finalStatus == CaptureSessionStatus.COMPLETED.name) CaptureConfidence.HIGH else CaptureConfidence.LOW,
            isSensitive = false,
            isComplete = finalStatus == CaptureSessionStatus.COMPLETED.name
        )
        val updated = (_activeSession.value ?: cur).copy(
            status = finalStatus,
            finishedAt = now
        )
        _activeSession.value = updated
        onPersistSession?.invoke(updated)
        return updated
    }

    fun cancelCapture(): UssdCaptureSessionEntity? {
        val cur = _activeSession.value ?: return null
        val now = System.currentTimeMillis()
        val updated = cur.copy(
            status = CaptureSessionStatus.CANCELLED.name,
            finishedAt = now
        )
        _activeSession.value = updated
        onPersistSession?.invoke(updated)
        return updated
    }

    fun clearActiveSession() {
        _activeSession.value = null
        _capturedEvents.value = emptyList()
        lastEventFingerprint = null
        sequenceCounter = 0
    }

    fun onAccessibilitySnapshot(snapshot: CapturedNodeSnapshot) {
        val session = _activeSession.value ?: return
        if (session.status != CaptureSessionStatus.RECORDING.name) return

        val promptNorm = snapshot.promptText.trim().replace(Regex("\\s+"), " ")

        // 1. Sensitive input protection: NEVER store PIN/OTP/password values or contents
        if (isSensitiveSnapshot(snapshot, promptNorm)) {
            val fingerprint = "SENSITIVE|${promptNorm.lowercase()}"
            if (fingerprint == lastEventFingerprint) return
            lastEventFingerprint = fingerprint
            appendEvent(
                session = session,
                eventType = CaptureEventType.MANUAL_AUTHORIZATION,
                promptText = if (promptNorm.isNotBlank()) promptNorm else "Sensitive authorization prompt detected",
                visibleOptions = emptyList(),
                selectedAction = null, // Never record or store credential values
                inputControlType = if (snapshot.hasEditableInput) "PROTECTED_INPUT" else null,
                observedNodeClass = snapshot.inputNodeClass ?: snapshot.rootNodeClass,
                nodeMetadataSummary = "Sensitive prompt excluded; manual agent authorization required",
                timestampMs = snapshot.timestampMs,
                confidence = CaptureConfidence.SENSITIVE,
                isSensitive = true,
                isComplete = true
            )
            return
        }

        // 2. Inaccessible dialog detection
        if (promptNorm.isBlank() && !snapshot.hasEditableInput && snapshot.visibleButtons.isEmpty()) {
            val fingerprint = "INACCESSIBLE|${snapshot.packageName}"
            if (fingerprint == lastEventFingerprint) return
            lastEventFingerprint = fingerprint
            appendEvent(
                session = session,
                eventType = CaptureEventType.INACCESSIBLE_DIALOG,
                promptText = "USSD dialogue did not expose readable accessibility text",
                visibleOptions = emptyList(),
                selectedAction = null,
                inputControlType = null,
                observedNodeClass = snapshot.rootNodeClass,
                nodeMetadataSummary = snapshot.nodeMetadataSummary,
                timestampMs = snapshot.timestampMs,
                confidence = CaptureConfidence.LOW,
                isSensitive = false,
                isComplete = false
            )
            return
        }

        // 3. Button click / interaction observation
        val clicked = snapshot.clickedButtonLabel?.trim()?.takeIf { it.isNotEmpty() }
        if (clicked != null) {
            val lowerClick = clicked.lowercase()
            val eventType = when {
                lowerClick in CANCEL_BUTTONS -> CaptureEventType.SESSION_ENDED
                snapshot.hasEditableInput -> CaptureEventType.INPUT_SUBMISSION
                else -> CaptureEventType.MENU_SELECTION
            }
            val fingerprint = "${eventType.name}|$clicked|${promptNorm.lowercase()}"
            if (fingerprint == lastEventFingerprint) return
            lastEventFingerprint = fingerprint

            appendEvent(
                session = session,
                eventType = eventType,
                promptText = promptNorm,
                visibleOptions = extractMenuOptions(snapshot.promptText),
                selectedAction = "Clicked button '$clicked' (entered value not assumed)",
                inputControlType = if (snapshot.hasEditableInput) "EDIT_TEXT" else "BUTTON",
                observedNodeClass = snapshot.rootNodeClass,
                nodeMetadataSummary = snapshot.nodeMetadataSummary,
                timestampMs = snapshot.timestampMs,
                confidence = if (snapshot.hasEditableInput) CaptureConfidence.MEDIUM else CaptureConfidence.HIGH,
                isSensitive = false,
                isComplete = eventType != CaptureEventType.SESSION_ENDED
            )
            if (lowerClick in CANCEL_BUTTONS) {
                finishCapture(unexpectedTermination = true)
            }
            return
        }

        // 4. Prompt classification
        val visibleOptions = extractMenuOptions(snapshot.promptText)
        val eventType = classifyPromptType(promptNorm, visibleOptions, snapshot.hasEditableInput)
        val fingerprint = "${eventType.name}|${promptNorm.lowercase()}"
        if (fingerprint == lastEventFingerprint) return
        lastEventFingerprint = fingerprint

        val confidence = when (eventType) {
            CaptureEventType.MENU_PROMPT,
            CaptureEventType.CONFIRMATION_PROMPT,
            CaptureEventType.RESULT_PROMPT -> CaptureConfidence.HIGH
            CaptureEventType.INPUT_PROMPT -> CaptureConfidence.MEDIUM
            else -> CaptureConfidence.LOW
        }

        appendEvent(
            session = session,
            eventType = eventType,
            promptText = promptNorm,
            visibleOptions = visibleOptions,
            selectedAction = null,
            inputControlType = if (snapshot.hasEditableInput) "EDIT_TEXT" else null,
            observedNodeClass = snapshot.inputNodeClass ?: snapshot.rootNodeClass,
            nodeMetadataSummary = snapshot.nodeMetadataSummary,
            timestampMs = snapshot.timestampMs,
            confidence = confidence,
            isSensitive = false,
            isComplete = eventType != CaptureEventType.UNKNOWN_EVENT
        )
    }

    private fun appendEvent(
        session: UssdCaptureSessionEntity,
        eventType: CaptureEventType,
        promptText: String,
        visibleOptions: List<String>,
        selectedAction: String?,
        inputControlType: String?,
        observedNodeClass: String?,
        nodeMetadataSummary: String?,
        timestampMs: Long,
        confidence: CaptureConfidence,
        isSensitive: Boolean,
        isComplete: Boolean
    ) {
        sequenceCounter += 1
        val event = UssdCaptureEventEntity(
            id = UUID.randomUUID().toString(),
            sessionId = session.id,
            sequenceNumber = sequenceCounter,
            eventType = eventType.name,
            promptText = promptText,
            visibleOptionsJson = gson.toJson(visibleOptions),
            selectedAction = selectedAction,
            inputControlType = inputControlType,
            observedNodeClass = observedNodeClass,
            nodeMetadataSummary = nodeMetadataSummary,
            observedTimestamp = timestampMs,
            confidence = confidence.name,
            isSensitive = isSensitive,
            isComplete = isComplete
        )
        val updatedEvents = _capturedEvents.value + event
        _capturedEvents.value = updatedEvents
        val updatedSession = (_activeSession.value ?: session).copy(
            capturedEventCount = updatedEvents.size
        )
        _activeSession.value = updatedSession
        onPersistEvent?.invoke(event)
        onPersistSession?.invoke(updatedSession)
    }

    private fun isSensitiveSnapshot(snapshot: CapturedNodeSnapshot, promptNorm: String): Boolean {
        if (snapshot.isPasswordInput) return true
        val lower = promptNorm.lowercase()
        return SENSITIVE_KEYWORDS.any { lower.contains(it) }
    }

    private fun extractMenuOptions(rawText: String): List<String> {
        val optionRegex = Regex("""^\s*\d+\s*[).:\-]\s*.+$""")
        return rawText.lines()
            .map { it.trim() }
            .filter { optionRegex.matches(it) }
    }

    private fun classifyPromptType(
        promptNorm: String,
        visibleOptions: List<String>,
        hasEditableInput: Boolean
    ): CaptureEventType {
        val lower = promptNorm.lowercase()
        return when {
            RESULT_KEYWORDS.any { lower.contains(it) } && !hasEditableInput -> CaptureEventType.RESULT_PROMPT
            CONFIRM_KEYWORDS.any { lower.contains(it) } -> CaptureEventType.CONFIRMATION_PROMPT
            visibleOptions.isNotEmpty() -> CaptureEventType.MENU_PROMPT
            hasEditableInput -> CaptureEventType.INPUT_PROMPT
            else -> CaptureEventType.UNKNOWN_EVENT
        }
    }

    companion object {
        private val SENSITIVE_KEYWORDS = listOf(
            "enter pin",
            "enter your pin",
            "momo pin",
            "secret code",
            "secret pin",
            "4-digit pin",
            "passcode",
            "password",
            "enter otp"
        )
        private val CONFIRM_KEYWORDS = listOf(
            "confirm",
            "approve",
            "press 1 to",
            "reply 1 to",
            "yes/no"
        )
        private val RESULT_KEYWORDS = listOf(
            "successfully",
            "successful",
            "completed",
            "failed",
            "insufficient",
            "your balance",
            "transaction id",
            "thank you for using"
        )
        private val CANCEL_BUTTONS = setOf("cancel", "dismiss", "close")
    }
}

/**
 * Process-wide bridge connecting EasyMoMoAccessibilityService with the active UssdCaptureRecorder
 * without introducing a second AccessibilityService.
 */
object UssdCaptureBridge {
    val recorder = UssdCaptureRecorder()

    fun isCaptureActive(): Boolean = recorder.isCaptureActive()

    fun onSnapshot(snapshot: CapturedNodeSnapshot) {
        recorder.onAccessibilitySnapshot(snapshot)
    }
}

package com.easymomo.app.ui.customussd

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.easymomo.app.data.local.CustomUssdFlowEntity
import com.easymomo.app.data.local.UssdCaptureEventEntity
import com.easymomo.app.data.local.UssdCaptureSessionEntity
import com.easymomo.app.data.repository.CustomUssdRepository
import com.easymomo.app.domain.model.NetworkType
import com.easymomo.app.ussd.PermissionCapabilityManager
import com.easymomo.app.ussd.SimManager
import com.easymomo.app.ussd.SimResolutionStatus
import com.easymomo.app.ussd.UssdLaunchResult
import com.easymomo.app.ussd.UssdLauncher
import com.easymomo.app.ussd.capture.CaptureSessionStatus
import com.easymomo.app.ussd.capture.CustomFlowStepDraft
import com.easymomo.app.ussd.capture.CustomStepPlaceholderType
import com.easymomo.app.ussd.capture.UssdCaptureBridge
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class CustomUssdSubScreen {
    HUB,
    CAPTURE_SETUP,
    LIVE_CAPTURE,
    CAPTURE_REVIEW,
    MANUAL_BUILDER,
    MY_FLOWS
}

data class CustomUssdUiState(
    val subScreen: CustomUssdSubScreen = CustomUssdSubScreen.HUB,
    val captureSessions: List<UssdCaptureSessionEntity> = emptyList(),
    val recoveredInterruptedSessions: List<UssdCaptureSessionEntity> = emptyList(),
    val customFlows: List<CustomUssdFlowEntity> = emptyList(),

    // Capture Setup
    val setupFlowName: String = "",
    val setupNetwork: String = "MTN",
    val setupEntryCode: String = "",
    val setupError: String? = null,
    val accessibilityEnabled: Boolean = false,
    val resolvedSimLabel: String = "Using Settings SIM",
    val resolvedSubscriptionId: Int? = null,
    val simWarning: String? = null,

    // Live Capture
    val activeSession: UssdCaptureSessionEntity? = null,
    val liveEvents: List<UssdCaptureEventEntity> = emptyList(),
    val elapsedSeconds: Long = 0L,

    // Capture Review
    val reviewSession: UssdCaptureSessionEntity? = null,
    val reviewEvents: List<UssdCaptureEventEntity> = emptyList(),
    val reviewFlowName: String = "",

    // Manual Builder
    val manualFlowName: String = "",
    val manualNetwork: String = "MTN",
    val manualEntryCode: String = "",
    val manualDescription: String = "",
    val manualCategory: String = "AGENT_SERVICE",
    val manualSteps: List<CustomFlowStepDraft> = listOf(
        CustomFlowStepDraft(1, CustomStepPlaceholderType.MENU_SELECTION, "Select menu option"),
        CustomFlowStepDraft(2, CustomStepPlaceholderType.MANUAL_AUTHORIZATION, "Agent enters MoMo PIN manually")
    ),
    val manualError: String? = null,

    // Feedback
    val statusMessage: String? = null,
    val errorMessage: String? = null
)

class CustomUssdViewModel(
    private val repository: CustomUssdRepository,
    private val simManager: SimManager,
    private val ussdLauncher: UssdLauncher,
    private val permissionManager: PermissionCapabilityManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(CustomUssdUiState())
    val uiState: StateFlow<CustomUssdUiState> = _uiState.asStateFlow()

    private var timerJob: Job? = null
    private var liveEventsJob: Job? = null
    private var reviewEventsJob: Job? = null

    private val entryCodeRegex = Regex("^\\*[0-9*#]+#$")

    init {
        viewModelScope.launch {
            val recovered = repository.recoverInterruptedSessions()
            if (recovered.isNotEmpty()) {
                _uiState.update { it.copy(recoveredInterruptedSessions = recovered) }
            }
        }
        viewModelScope.launch {
            repository.observeCaptureSessions().collectLatest { sessions ->
                _uiState.update { it.copy(captureSessions = sessions) }
            }
        }
        viewModelScope.launch {
            repository.observeAllCustomFlows().collectLatest { flows ->
                _uiState.update { it.copy(customFlows = flows) }
            }
        }
        viewModelScope.launch {
            UssdCaptureBridge.recorder.activeSession.collectLatest { session ->
                _uiState.update { it.copy(activeSession = session) }
                if (session != null && session.status == CaptureSessionStatus.RECORDING.name) {
                    startTimer(session.startedAt)
                } else {
                    timerJob?.cancel()
                }
            }
        }
        refreshCapturePrerequisites()
    }

    fun navigateTo(screen: CustomUssdSubScreen) {
        _uiState.update {
            it.copy(
                subScreen = screen,
                setupError = null,
                manualError = null,
                errorMessage = null,
                statusMessage = null
            )
        }
        if (screen == CustomUssdSubScreen.CAPTURE_SETUP) {
            refreshCapturePrerequisites()
        }
    }

    fun refreshCapturePrerequisites() {
        viewModelScope.launch {
            val accessibilityOk = permissionManager.isAccessibilityServiceEnabled()
            val network = toNetworkType(_uiState.value.setupNetwork)
            val simResolution = if (network != NetworkType.UNKNOWN) {
                simManager.resolveForNetwork(network)
            } else {
                null
            }
            val activeSims = simManager.getActiveSims()
            val resolvedSim = simResolution?.resolvedSim ?: activeSims.firstOrNull()
            val warning = when {
                activeSims.isEmpty() -> "No active SIM detected. Check Settings → SIM Management."
                simResolution != null &&
                    simResolution.status != SimResolutionStatus.READY &&
                    simResolution.status != SimResolutionStatus.NETWORK_MISMATCH_WARNING ->
                    simResolution.message
                else -> null
            }
            _uiState.update {
                it.copy(
                    accessibilityEnabled = accessibilityOk,
                    resolvedSimLabel = resolvedSim?.label ?: "No active SIM resolved",
                    resolvedSubscriptionId = resolvedSim?.subscriptionId,
                    simWarning = warning
                )
            }
        }
    }

    fun updateSetupFlowName(value: String) {
        _uiState.update { it.copy(setupFlowName = value, setupError = null) }
    }

    fun updateSetupNetwork(value: String) {
        _uiState.update { it.copy(setupNetwork = value, setupError = null) }
        refreshCapturePrerequisites()
    }

    fun updateSetupEntryCode(value: String) {
        _uiState.update { it.copy(setupEntryCode = value, setupError = null) }
    }

    fun startCapture() {
        val state = _uiState.value
        val name = state.setupFlowName.trim()
        val entryCode = state.setupEntryCode.trim()
        if (name.isEmpty()) {
            _uiState.update { it.copy(setupError = "Enter a flow name before starting capture.") }
            return
        }
        if (!entryCodeRegex.matches(entryCode)) {
            _uiState.update {
                it.copy(setupError = "Enter a valid USSD entry code (e.g. *171# or *110#).")
            }
            return
        }
        if (!permissionManager.isAccessibilityServiceEnabled()) {
            _uiState.update {
                it.copy(
                    accessibilityEnabled = false,
                    setupError = "Enable the EasyMoMo Accessibility Service in Android Settings before capturing."
                )
            }
            return
        }

        viewModelScope.launch {
            val activeSims = simManager.getActiveSims()
            if (activeSims.isEmpty()) {
                _uiState.update {
                    it.copy(setupError = "No active SIM card detected. Configure SIMs in Settings.")
                }
                return@launch
            }
            val networkType = toNetworkType(state.setupNetwork)
            val subId = if (networkType != NetworkType.UNKNOWN) {
                simManager.resolveForNetwork(networkType).resolvedSim?.subscriptionId
                    ?: activeSims.firstOrNull()?.subscriptionId
            } else {
                activeSims.firstOrNull()?.subscriptionId
            }

            val session = UssdCaptureBridge.recorder.startSession(
                flowName = name,
                network = state.setupNetwork,
                entryCode = entryCode,
                subscriptionId = subId
            )
            observeLiveEvents(session.id)
            _uiState.update {
                it.copy(
                    subScreen = CustomUssdSubScreen.LIVE_CAPTURE,
                    activeSession = session,
                    elapsedSeconds = 0L,
                    setupError = null,
                    statusMessage = "Dialing $entryCode for capture..."
                )
            }

            val launchResult = ussdLauncher.launchUssd(
                ussdCode = entryCode,
                subscriptionId = subId,
                forceInteractiveDialog = true,
                onTelephonyResponse = { _ -> },
                onTelephonyFailure = { errorCode ->
                    _uiState.update {
                        it.copy(errorMessage = "USSD launch returned telephony error code $errorCode.")
                    }
                }
            )
            if (launchResult is UssdLaunchResult.Error) {
                _uiState.update { it.copy(errorMessage = launchResult.message) }
            }
        }
    }

    fun pauseCapture() {
        UssdCaptureBridge.recorder.pauseCapture()
        _uiState.update { it.copy(statusMessage = "Capture paused.") }
    }

    fun resumeCapture() {
        UssdCaptureBridge.recorder.resumeCapture()
        _uiState.update { it.copy(statusMessage = "Capture resumed.") }
    }

    fun finishCapture() {
        val finished = UssdCaptureBridge.recorder.finishCapture(unexpectedTermination = false)
        timerJob?.cancel()
        if (finished != null) {
            openCaptureReview(finished)
        } else {
            _uiState.update { it.copy(subScreen = CustomUssdSubScreen.HUB) }
        }
    }

    fun cancelCapture() {
        val cancelled = UssdCaptureBridge.recorder.cancelCapture()
        timerJob?.cancel()
        viewModelScope.launch {
            if (cancelled != null) {
                repository.discardCaptureSession(cancelled.id)
            }
            _uiState.update {
                it.copy(
                    subScreen = CustomUssdSubScreen.HUB,
                    activeSession = null,
                    liveEvents = emptyList(),
                    statusMessage = "Capture session cancelled."
                )
            }
        }
    }

    fun openCaptureReview(session: UssdCaptureSessionEntity) {
        reviewEventsJob?.cancel()
        reviewEventsJob = viewModelScope.launch {
            repository.observeCaptureEvents(session.id).collectLatest { events ->
                _uiState.update { it.copy(reviewEvents = events) }
            }
        }
        _uiState.update {
            it.copy(
                subScreen = CustomUssdSubScreen.CAPTURE_REVIEW,
                reviewSession = session,
                reviewFlowName = session.flowName,
                recoveredInterruptedSessions = it.recoveredInterruptedSessions.filterNot { s -> s.id == session.id }
            )
        }
    }

    fun updateReviewFlowName(name: String) {
        _uiState.update { it.copy(reviewFlowName = name) }
    }

    fun saveReviewedCaptureAsDraft() {
        val session = _uiState.value.reviewSession ?: return
        val nameOverride = _uiState.value.reviewFlowName.trim().ifEmpty { session.flowName }
        viewModelScope.launch {
            val saved = repository.saveCaptureAsDraftFlow(session.id, nameOverride)
            if (saved != null) {
                _uiState.update {
                    it.copy(
                        subScreen = CustomUssdSubScreen.MY_FLOWS,
                        statusMessage = "Saved '${saved.flowName}' as draft custom flow."
                    )
                }
            } else {
                _uiState.update { it.copy(errorMessage = "Could not save capture as draft.") }
            }
        }
    }

    fun discardReviewedCapture() {
        val session = _uiState.value.reviewSession ?: return
        viewModelScope.launch {
            repository.discardCaptureSession(session.id)
            _uiState.update {
                it.copy(
                    subScreen = CustomUssdSubScreen.HUB,
                    reviewSession = null,
                    reviewEvents = emptyList(),
                    statusMessage = "Capture session discarded."
                )
            }
        }
    }

    fun dismissInterruptedBanner(sessionId: String) {
        _uiState.update {
            it.copy(
                recoveredInterruptedSessions = it.recoveredInterruptedSessions.filterNot { s -> s.id == sessionId }
            )
        }
    }

    // Manual Builder
    fun updateManualFlowName(v: String) = _uiState.update { it.copy(manualFlowName = v, manualError = null) }
    fun updateManualNetwork(v: String) = _uiState.update { it.copy(manualNetwork = v, manualError = null) }
    fun updateManualEntryCode(v: String) = _uiState.update { it.copy(manualEntryCode = v, manualError = null) }
    fun updateManualDescription(v: String) = _uiState.update { it.copy(manualDescription = v) }
    fun updateManualCategory(v: String) = _uiState.update { it.copy(manualCategory = v) }

    fun addManualPlaceholderStep(type: CustomStepPlaceholderType, label: String) {
        val cleanLabel = label.trim().ifEmpty { type.displayName }
        _uiState.update { state ->
            val nextIndex = (state.manualSteps.maxOfOrNull { it.sequence } ?: 0) + 1
            state.copy(
                manualSteps = state.manualSteps + CustomFlowStepDraft(
                    sequence = nextIndex,
                    stepType = type,
                    label = cleanLabel,
                    isSensitive = type == CustomStepPlaceholderType.MANUAL_AUTHORIZATION
                )
            )
        }
    }

    fun removeManualStep(sequence: Int) {
        _uiState.update { state ->
            val filtered = state.manualSteps
                .filterNot { it.sequence == sequence }
                .mapIndexed { idx, step -> step.copy(sequence = idx + 1) }
            state.copy(manualSteps = filtered)
        }
    }

    fun saveManualDraft() {
        val state = _uiState.value
        val name = state.manualFlowName.trim()
        val entryCode = state.manualEntryCode.trim()
        if (name.isEmpty()) {
            _uiState.update { it.copy(manualError = "Enter a flow name.") }
            return
        }
        if (!entryCodeRegex.matches(entryCode)) {
            _uiState.update { it.copy(manualError = "Enter a valid USSD entry code (e.g. *171#).") }
            return
        }
        viewModelScope.launch {
            val saved = repository.createManualDraftFlow(
                flowName = name,
                network = state.manualNetwork,
                entryCode = entryCode,
                serviceDescription = state.manualDescription,
                transactionCategory = state.manualCategory,
                steps = state.manualSteps
            )
            _uiState.update {
                it.copy(
                    subScreen = CustomUssdSubScreen.MY_FLOWS,
                    manualFlowName = "",
                    manualEntryCode = "",
                    manualDescription = "",
                    manualError = null,
                    statusMessage = "Created draft custom flow '${saved.flowName}'."
                )
            }
        }
    }

    // My Custom Flows CRUD
    fun updateCustomFlowMetadata(flowId: String, name: String, description: String, category: String) {
        viewModelScope.launch {
            repository.updateCustomFlowMetadata(flowId, name, description, category)
            _uiState.update { it.copy(statusMessage = "Updated custom flow metadata.") }
        }
    }

    fun duplicateCustomFlow(flowId: String) {
        viewModelScope.launch {
            val copy = repository.duplicateCustomFlow(flowId)
            if (copy != null) {
                _uiState.update { it.copy(statusMessage = "Duplicated as '${copy.flowName}'.") }
            }
        }
    }

    fun toggleCustomFlowEnabled(flowId: String, enable: Boolean) {
        viewModelScope.launch {
            val result = repository.toggleCustomFlowEnabled(flowId, enable)
            result.onFailure { err ->
                _uiState.update { it.copy(errorMessage = err.message) }
            }.onSuccess {
                _uiState.update {
                    it.copy(statusMessage = if (enable) "Custom flow enabled." else "Custom flow disabled.")
                }
            }
        }
    }

    fun deleteCustomFlow(flowId: String) {
        viewModelScope.launch {
            repository.deleteCustomFlow(flowId)
            _uiState.update { it.copy(statusMessage = "Custom flow deleted.") }
        }
    }

    fun clearMessages() {
        _uiState.update { it.copy(statusMessage = null, errorMessage = null) }
    }

    private fun observeLiveEvents(sessionId: String) {
        liveEventsJob?.cancel()
        liveEventsJob = viewModelScope.launch {
            repository.observeCaptureEvents(sessionId).collectLatest { events ->
                _uiState.update { it.copy(liveEvents = events) }
            }
        }
    }

    private fun startTimer(startedAtMs: Long) {
        timerJob?.cancel()
        timerJob = viewModelScope.launch {
            while (true) {
                val secs = ((System.currentTimeMillis() - startedAtMs) / 1000L).coerceAtLeast(0L)
                _uiState.update { it.copy(elapsedSeconds = secs) }
                delay(1000L)
            }
        }
    }

    private fun toNetworkType(network: String): NetworkType = when (network.uppercase()) {
        "MTN" -> NetworkType.MTN
        "TELECEL" -> NetworkType.TELECEL
        "AT" -> NetworkType.AT
        else -> NetworkType.UNKNOWN
    }

    class Factory(
        private val repository: CustomUssdRepository,
        private val simManager: SimManager,
        private val ussdLauncher: UssdLauncher,
        private val permissionManager: PermissionCapabilityManager
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return CustomUssdViewModel(repository, simManager, ussdLauncher, permissionManager) as T
        }
    }
}

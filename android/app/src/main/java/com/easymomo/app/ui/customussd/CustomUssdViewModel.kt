package com.easymomo.app.ui.customussd

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.easymomo.app.data.local.CustomUssdFlowEntity
import com.easymomo.app.data.local.UssdCaptureEventEntity
import com.easymomo.app.data.local.UssdCaptureSessionEntity
import com.easymomo.app.data.repository.CustomUssdRepository
import com.easymomo.app.domain.model.NetworkType
import com.easymomo.app.ussd.ActiveSimInfo
import com.easymomo.app.ussd.PermissionCapabilityManager
import com.easymomo.app.ussd.SimManager
import com.easymomo.app.ussd.SimResolutionStatus
import com.easymomo.app.ussd.UssdLaunchResult
import com.easymomo.app.ussd.UssdLauncher
import com.easymomo.app.ussd.capture.CaptureSessionStatus
import com.easymomo.app.ussd.capture.CustomFlowStepDraft
import com.easymomo.app.ussd.capture.CustomStepPlaceholderType
import com.easymomo.app.ussd.capture.CustomUssdFlowBuilder
import com.easymomo.app.ussd.capture.CustomUssdOperationType
import com.easymomo.app.ussd.capture.SimpleCustomUssdFlow
import com.easymomo.app.ussd.capture.UssdCaptureBridge
import kotlinx.coroutines.Dispatchers
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

    // Simplified Custom USSD Builder & Saved List
    val simpleFlows: List<SimpleCustomUssdFlow> = emptyList(),
    val activeSims: List<ActiveSimInfo> = emptyList(),
    val editingFlowId: String? = null,
    val builderName: String = "",
    val builderOperationType: CustomUssdOperationType = CustomUssdOperationType.STEP_BY_STEP,
    val builderSteps: List<String> = emptyList(),
    val builderOneTimeCode: String = "",
    val builderSimSlotIndex: Int = 0,
    val builderError: String? = null,

    // Feedback
    val statusMessage: String? = null,
    val errorMessage: String? = null
)

class CustomUssdViewModel(
    private val repository: CustomUssdRepository,
    private val simManager: SimManager,
    private val ussdLauncher: UssdLauncher,
    private val permissionManager: PermissionCapabilityManager,
    private val hasActiveAccess: () -> Boolean = { true }
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
            repository.observeSimpleCustomFlows().collectLatest { flows ->
                _uiState.update { it.copy(simpleFlows = flows) }
            }
        }
        refreshActiveSims()
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
        viewModelScope.launch(Dispatchers.IO) {
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
        if (!hasActiveAccess()) {
            _uiState.update {
                it.copy(setupError = "An active subscription or free trial is required. Open Settings → User Profile to subscribe.")
            }
            return
        }
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
        if (!hasActiveAccess()) {
            _uiState.update {
                it.copy(manualError = "An active subscription or free trial is required. Open Settings → User Profile to subscribe.")
            }
            return
        }
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

    // Simplified Custom USSD Builder Actions
    fun refreshActiveSims() {
        viewModelScope.launch(Dispatchers.IO) {
            val sims = simManager.getActiveSims()
            _uiState.update { state ->
                val validSlot = if (sims.any { it.slotIndex == state.builderSimSlotIndex }) {
                    state.builderSimSlotIndex
                } else {
                    sims.firstOrNull()?.slotIndex ?: 0
                }
                state.copy(activeSims = sims, builderSimSlotIndex = validSlot)
            }
        }
    }

    fun updateBuilderName(value: String) {
        _uiState.update { it.copy(builderName = value, builderError = null) }
    }

    fun updateBuilderOperationType(type: CustomUssdOperationType) {
        _uiState.update { it.copy(builderOperationType = type, builderError = null) }
    }

    fun addBuilderStep(raw: String): Boolean {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) {
            _uiState.update { it.copy(builderError = "Enter a USSD step or select a quick variable.") }
            return false
        }
        if (CustomUssdFlowBuilder.containsSensitivePinHint(trimmed)) {
            _uiState.update {
                it.copy(builderError = "For security reasons, do not include PIN codes in custom USSD steps.")
            }
            return false
        }
        _uiState.update { state ->
            state.copy(
                builderSteps = state.builderSteps + trimmed,
                builderError = null
            )
        }
        return true
    }

    fun moveBuilderStepUp(index: Int) {
        _uiState.update { state ->
            if (index !in 1 until state.builderSteps.size) return@update state
            val mutable = state.builderSteps.toMutableList()
            val item = mutable.removeAt(index)
            mutable.add(index - 1, item)
            state.copy(builderSteps = mutable, builderError = null)
        }
    }

    fun moveBuilderStepDown(index: Int) {
        _uiState.update { state ->
            if (index !in 0 until (state.builderSteps.size - 1)) return@update state
            val mutable = state.builderSteps.toMutableList()
            val item = mutable.removeAt(index)
            mutable.add(index + 1, item)
            state.copy(builderSteps = mutable, builderError = null)
        }
    }

    fun removeBuilderStep(index: Int) {
        _uiState.update { state ->
            if (index !in state.builderSteps.indices) return@update state
            val mutable = state.builderSteps.toMutableList()
            mutable.removeAt(index)
            state.copy(builderSteps = mutable, builderError = null)
        }
    }

    fun clearBuilderSteps() {
        _uiState.update { it.copy(builderSteps = emptyList(), builderError = null) }
    }

    fun updateBuilderOneTimeCode(value: String) {
        _uiState.update { it.copy(builderOneTimeCode = value, builderError = null) }
    }

    fun appendOneTimeVariable(variable: String) {
        _uiState.update { state ->
            val current = state.builderOneTimeCode.trimEnd()
            val updated = when {
                current.isEmpty() -> variable
                current.endsWith("*") -> "$current$variable"
                current.endsWith("#") -> current.dropLast(1) + "*$variable#"
                else -> "$current*$variable"
            }
            state.copy(builderOneTimeCode = updated, builderError = null)
        }
    }

    fun selectBuilderSimSlot(slotIndex: Int) {
        _uiState.update { it.copy(builderSimSlotIndex = slotIndex, builderError = null) }
    }

    fun startEditingSimpleFlow(flow: SimpleCustomUssdFlow) {
        _uiState.update {
            it.copy(
                editingFlowId = flow.id,
                builderName = flow.name,
                builderOperationType = flow.operationType,
                builderSteps = flow.steps,
                builderOneTimeCode = flow.oneTimeCode,
                builderSimSlotIndex = flow.simSlotIndex,
                builderError = null,
                statusMessage = null
            )
        }
    }

    fun resetBuilder() {
        _uiState.update { state ->
            state.copy(
                editingFlowId = null,
                builderName = "",
                builderOperationType = CustomUssdOperationType.STEP_BY_STEP,
                builderSteps = emptyList(),
                builderOneTimeCode = "",
                builderSimSlotIndex = state.activeSims.firstOrNull()?.slotIndex ?: 0,
                builderError = null,
                statusMessage = null
            )
        }
    }

    fun saveBuilderFlow(onSaved: () -> Unit = {}) {
        if (!hasActiveAccess()) {
            _uiState.update {
                it.copy(builderError = "An active subscription or free trial is required. Open Settings → User Profile to subscribe.")
            }
            return
        }
        val state = _uiState.value
        val validationError = CustomUssdFlowBuilder.validate(
            name = state.builderName,
            operationType = state.builderOperationType,
            steps = state.builderSteps,
            oneTimeCode = state.builderOneTimeCode
        )
        if (validationError != null) {
            _uiState.update { it.copy(builderError = validationError) }
            return
        }
        val wasEditing = state.editingFlowId != null
        viewModelScope.launch {
            repository.saveSimpleCustomUssd(
                existingId = state.editingFlowId,
                flowName = state.builderName,
                operationType = state.builderOperationType,
                steps = state.builderSteps,
                oneTimeCode = state.builderOneTimeCode,
                simSlotIndex = state.builderSimSlotIndex
            )
            _uiState.update {
                it.copy(
                    editingFlowId = null,
                    builderName = "",
                    builderOperationType = CustomUssdOperationType.STEP_BY_STEP,
                    builderSteps = emptyList(),
                    builderOneTimeCode = "",
                    builderError = null,
                    statusMessage = if (wasEditing) "Custom USSD updated!" else "Custom USSD saved!"
                )
            }
            onSaved()
        }
    }

    fun deleteSimpleFlow(flowId: String) {
        viewModelScope.launch {
            repository.deleteCustomFlow(flowId)
            _uiState.update { state ->
                if (state.editingFlowId == flowId) {
                    state.copy(
                        editingFlowId = null,
                        builderName = "",
                        builderSteps = emptyList(),
                        builderOneTimeCode = "",
                        statusMessage = "Custom USSD deleted."
                    )
                } else {
                    state.copy(statusMessage = "Custom USSD deleted.")
                }
            }
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
        private val permissionManager: PermissionCapabilityManager,
        private val hasActiveAccess: () -> Boolean = { true }
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return CustomUssdViewModel(repository, simManager, ussdLauncher, permissionManager, hasActiveAccess) as T
        }
    }
}

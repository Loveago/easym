package com.easymomo.app.ui.terminal

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.easymomo.app.data.local.AgentSettings
import com.easymomo.app.data.local.CustomerNumberEntity
import com.easymomo.app.data.local.DataBundleEntity
import com.easymomo.app.data.local.SavedSimSelection
import com.easymomo.app.data.local.TransactionEntity
import com.easymomo.app.data.local.UssdFlowConfigEntity
import com.easymomo.app.data.repository.AuthSubscriptionRepository
import com.easymomo.app.data.repository.CustomerSyncManager
import com.easymomo.app.data.repository.CustomerSyncStatusSnapshot
import com.easymomo.app.data.repository.ReportAggregator
import com.easymomo.app.data.repository.ReportSummary
import com.easymomo.app.data.repository.TransactionRepository
import com.easymomo.app.domain.model.NetworkPrefixDetector
import com.easymomo.app.domain.model.NetworkType
import com.easymomo.app.domain.model.ProcessingMethod
import com.easymomo.app.domain.model.SubscriptionStatus
import com.easymomo.app.domain.model.TransactionStatus
import com.easymomo.app.domain.model.TransactionType
import com.easymomo.app.ussd.ActiveSimInfo
import com.easymomo.app.ussd.CustomUssdRepeatCoordinator
import com.easymomo.app.ussd.CustomUssdRepeatState
import com.easymomo.app.ussd.EasyMoMoAccessibilityService
import com.easymomo.app.ussd.DiagnosticLogEntry
import com.easymomo.app.ussd.NetworkSimResolution
import com.easymomo.app.ussd.PermissionCapabilityManager
import com.easymomo.app.ussd.PermissionReport
import com.easymomo.app.ussd.SimManager
import com.easymomo.app.ussd.SimResolutionStatus
import com.easymomo.app.ussd.SimSlotInfo
import com.easymomo.app.ussd.UssdDiagnosticLog
import com.easymomo.app.ussd.UssdFlowController
import com.easymomo.app.ussd.UssdInputHandler
import com.easymomo.app.ussd.UssdLaunchResult
import com.easymomo.app.ussd.UssdLauncher
import com.easymomo.app.ussd.UssdPromptObserver
import com.easymomo.app.ussd.UssdResponseSender
import com.easymomo.app.ussd.UssdSessionManager
import com.easymomo.app.ussd.capture.CustomUssdFlowBuilder
import com.easymomo.app.ussd.capture.CustomUssdOperationType
import com.easymomo.app.ussd.capture.SimpleCustomUssdFlow
import com.easymomo.app.ussd.model.PresetUssdFlow
import com.easymomo.app.ussd.model.OutcomeCategory
import com.easymomo.app.ussd.model.UssdFlowProfile
import com.easymomo.app.ussd.model.UssdOutcome
import com.easymomo.app.ussd.model.UssdSessionState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.withContext
import java.math.BigDecimal
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Calendar

data class TerminalUiState(
    val amountText: String = "",
    val phoneText: String = "",
    val isPorted: Boolean = false,
    val manualNetwork: NetworkType = NetworkType.UNKNOWN,
    val detectedNetwork: NetworkType = NetworkType.UNKNOWN,
    val availableSims: List<SimSlotInfo> = emptyList(),
    val selectedSimId: Int? = null,
    val hasCallPermission: Boolean = false,
    val isAccessibilityEnabled: Boolean = false,
    val validationError: String? = null,
    val subscriptionGateVisible: Boolean = false,
    val activeDraft: TransactionEntity? = null,
    val activeTransaction: TransactionEntity? = null,
    val selectedBundle: DataBundleEntity? = null,
    val merchantCode: String = "",
    val customUssdCode: String = "",
    val inlineUssdVisible: Boolean = false,
    val activeSims: List<ActiveSimInfo> = emptyList(),
    val simResolutions: Map<NetworkType, NetworkSimResolution> = emptyMap(),
    val permissionReport: PermissionReport? = null,
    val selectedSimLabel: String? = null,
    val telecelOperationalId: String = "",
    val agentSettings: AgentSettings = AgentSettings(),
    val featureSimSelections: Map<Pair<NetworkType, TransactionType>, SavedSimSelection> = emptyMap(),
    val phoneSuggestions: List<CustomerNumberEntity> = emptyList(),
    val diagnosticLogs: List<DiagnosticLogEntry> = emptyList(),
    val testModeLastAction: String? = null,
    val isSavingSimSettings: Boolean = false,
    val simSettingsSavedMessage: String? = null,
    val repeatCount: Int = 1
)

class TerminalViewModel(
    private val txRepo: TransactionRepository,
    private val authRepo: AuthSubscriptionRepository,
    private val ussdLauncher: UssdLauncher,
    private val simManager: SimManager,
    private val permissionManager: PermissionCapabilityManager,
    private val customerSyncManager: CustomerSyncManager? = null
) : ViewModel() {

    private val sessionManager = UssdSessionManager()
    private val inputHandler = UssdInputHandler()
    private val flowController = UssdFlowController(
        sessionManager = sessionManager,
        inputHandler = inputHandler,
        responseSender = UssdResponseSender { reply ->
            EasyMoMoAccessibilityService.instance?.sendUssdReply(reply) ?: false
        }
    )

    private val _uiState = MutableStateFlow(TerminalUiState())
    val uiState: StateFlow<TerminalUiState> = _uiState.asStateFlow()

    val ussdState: StateFlow<UssdSessionState> = flowController.sessionState
    val subscription = authRepo.subscription
    val trialNotice = authRepo.trialNotice
    fun clearTrialNotice() = authRepo.clearTrialNotice()
    val recentTransactions = txRepo.observeRecentTransactions(5)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allUssdFlows: StateFlow<List<UssdFlowConfigEntity>> = txRepo.observeAllUssdFlows()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val savedCustomerNumbers: StateFlow<List<CustomerNumberEntity>> = txRepo.observeCustomerNumbers()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val customerSyncStatus: StateFlow<CustomerSyncStatusSnapshot> =
        (customerSyncManager?.statusFlow ?: kotlinx.coroutines.flow.flowOf(CustomerSyncStatusSnapshot()))
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), CustomerSyncStatusSnapshot())

    val todaySummary: StateFlow<ReportSummary> = run {
        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        txRepo.observeTransactionsBetween(cal.timeInMillis, Long.MAX_VALUE)
            .map { ReportAggregator.aggregate(it) }
            .flowOn(Dispatchers.Default)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ReportSummary())
    }

    private sealed interface RepeatTarget {
        data class Standard(
            val type: TransactionType,
            val overrideNetwork: NetworkType?
        ) : RepeatTarget

        data class Preset(
            val preset: PresetUssdFlow,
            val overrideAmount: String?,
            val overrideCodeOrTill: String?,
            val overrideSimId: Int?
        ) : RepeatTarget

        data class Custom(
            val flow: SimpleCustomUssdFlow
        ) : RepeatTarget
    }

    private val repeatCoordinator = CustomUssdRepeatCoordinator()
    val customUssdRepeatState: StateFlow<CustomUssdRepeatState?> = repeatCoordinator.state

    private var timeoutJob: Job? = null
    private var autocompleteJob: Job? = null
    private var repeatCountdownJob: Job? = null
    private var activeRepeatTarget: RepeatTarget? = null
    private var lastCustomRunPhone: String = ""
    private var lastCustomRunAmountValue: Double = 0.0
    private var lastCustomRunFormattedAmount: String = ""
    @Volatile
    private var lastDeviceRefreshMs: Long = 0L
    @Volatile
    private var lastSubscriptionRefreshMs: Long = 0L
    @Volatile
    private var lastAutoCustomerSyncMs: Long = 0L

    fun hasActiveSubscriptionAccess(): Boolean = subscription.value.hasActiveAccess()

    fun requireActiveAccessOrShowGate(): Boolean {
        if (!subscription.value.hasActiveAccess()) {
            _uiState.update { it.copy(subscriptionGateVisible = true) }
            return false
        }
        return true
    }

    fun refreshSubscriptionStatus(force: Boolean = false) {
        val now = System.currentTimeMillis()
        if (!force && (now - lastSubscriptionRefreshMs) < SUBSCRIPTION_REFRESH_THROTTLE_MS) {
            return
        }
        lastSubscriptionRefreshMs = now
        viewModelScope.launch(Dispatchers.IO) {
            authRepo.refreshProfileAndSubscription()
        }
    }

    fun triggerAutoCustomerSyncIfNeeded(forceFullRestore: Boolean = false) {
        if (!_uiState.value.agentSettings.customerCloudSyncEnabled) return
        if (!subscription.value.hasActiveAccess()) return
        val now = System.currentTimeMillis()
        if (!forceFullRestore && (now - lastAutoCustomerSyncMs) < AUTO_CUSTOMER_SYNC_THROTTLE_MS) {
            return
        }
        lastAutoCustomerSyncMs = now
        viewModelScope.launch(Dispatchers.IO) {
            customerSyncManager?.syncNow(forceFullRestore = forceFullRestore)
        }
    }

    init {
        refreshDeviceStatus()
        observeUssdPrompts()
        viewModelScope.launch {
            simManager.telecelOperationalIdFlow.collect { opId ->
                _uiState.update { it.copy(telecelOperationalId = opId) }
            }
        }
        viewModelScope.launch {
            simManager.agentSettingsFlow.collect { settings ->
                _uiState.update { state ->
                    state.copy(
                        agentSettings = settings,
                        phoneSuggestions = if (settings.saveCustomerNumbersEnabled) state.phoneSuggestions else emptyList()
                    )
                }
            }
        }
        viewModelScope.launch {
            simManager.allFeatureSelectionsFlow.collect { map ->
                _uiState.update { it.copy(featureSimSelections = map) }
            }
        }
    }

    fun assignSimToFeature(network: NetworkType, type: TransactionType, sim: ActiveSimInfo) {
        viewModelScope.launch {
            simManager.assignSimToFeature(network, type, sim)
            UssdDiagnosticLog.log("SIM", "Assigned ${sim.label} to ${network.displayName} ${type.displayName}")
        }
    }

    fun clearFeatureSimAssignment(network: NetworkType, type: TransactionType) {
        viewModelScope.launch {
            simManager.clearFeatureAssignment(network, type)
            UssdDiagnosticLog.log("SIM", "Cleared SIM override for ${network.displayName} ${type.displayName}")
        }
    }

    fun assignSimToAllFeatures(network: NetworkType, sim: ActiveSimInfo) {
        viewModelScope.launch {
            simManager.assignSimToAllFeatures(network, sim)
            UssdDiagnosticLog.log("SIM", "Assigned ${sim.label} to all ${network.displayName} features")
        }
    }

    fun clearAllFeatureSimAssignments(network: NetworkType) {
        viewModelScope.launch {
            simManager.clearAllFeatureAssignments(network)
            UssdDiagnosticLog.log("SIM", "Cleared all feature SIM selections for ${network.displayName}")
        }
    }

    fun setDefaultNetworkMode(mode: String) {
        viewModelScope.launch {
            simManager.updateAgentSettings { it.copy(defaultNetworkMode = mode) }
        }
    }

    fun setAutoClearFormAfterTx(enabled: Boolean) {
        viewModelScope.launch {
            simManager.updateAgentSettings { it.copy(autoClearFormAfterTx = enabled) }
        }
    }

    fun setSaveCustomerNumbersEnabled(enabled: Boolean) {
        viewModelScope.launch {
            simManager.updateAgentSettings { it.copy(saveCustomerNumbersEnabled = enabled) }
        }
    }

    fun setHideTodayTotalsOnHome(hidden: Boolean) {
        viewModelScope.launch {
            simManager.updateAgentSettings { it.copy(hideTodayTotalsOnHome = hidden) }
        }
    }

    fun setCustomerCloudSyncEnabled(enabled: Boolean) {
        viewModelScope.launch {
            simManager.updateAgentSettings { it.copy(customerCloudSyncEnabled = enabled) }
            if (enabled) {
                customerSyncManager?.scheduleAutoSyncIfEnabled(true)
            }
        }
    }

    fun setDarkModeEnabled(enabled: Boolean) {
        viewModelScope.launch {
            simManager.updateAgentSettings { it.copy(darkModeEnabled = enabled) }
        }
    }

    fun syncCustomersNow() {
        if (!requireActiveAccessOrShowGate()) return
        viewModelScope.launch(Dispatchers.IO) {
            customerSyncManager?.syncNow(forceFullRestore = true)
        }
    }

    fun addCustomerNumberManually(
        rawPhone: String,
        nickname: String?,
        onResult: (errorMessage: String?) -> Unit
    ) {
        if (!requireActiveAccessOrShowGate()) {
            onResult("Active subscription or trial required.")
            return
        }
        viewModelScope.launch {
            txRepo.saveCustomerNumberManually(rawPhone, nickname)
                .onSuccess {
                    onResult(null)
                    if (_uiState.value.agentSettings.customerCloudSyncEnabled) {
                        launch(Dispatchers.IO) {
                            customerSyncManager?.syncNow(forceFullRestore = false)
                        }
                    }
                }
                .onFailure { err ->
                    onResult(err.message ?: "Could not save customer number.")
                }
        }
    }

    fun saveTelecelOperationalId(operationalId: String) {
        viewModelScope.launch {
            simManager.saveTelecelOperationalId(operationalId)
        }
    }

    fun setAutoEnterTelecelOperationalId(enabled: Boolean) {
        viewModelScope.launch {
            simManager.updateAgentSettings { it.copy(autoEnterTelecelOperationalId = enabled) }
        }
    }

    private fun observeUssdPrompts() {
        viewModelScope.launch(Dispatchers.Default) {
            UssdPromptObserver.prompts.collect { observed ->
                flowController.onPromptObserved(observed.text)
            }
        }
        viewModelScope.launch {
            UssdPromptObserver.windowEvents.collect { event ->
                when (event) {
                    UssdPromptObserver.UssdWindowEvent.OPENED -> {
                        repeatCountdownJob?.cancel()
                        repeatCoordinator.cancelCountdown()
                    }
                    UssdPromptObserver.UssdWindowEvent.CLOSED -> {
                        val batch = repeatCoordinator.state.value
                        if (batch != null &&
                            batch.awaitingDialogClose &&
                            !batch.pausedOnError &&
                            batch.hasMoreRuns
                        ) {
                            repeatCountdownJob?.cancel()
                            repeatCountdownJob = viewModelScope.launch {
                                for (sec in CustomUssdRepeatCoordinator.NEXT_RUN_DELAY_SECONDS downTo 1) {
                                    val cur = repeatCoordinator.state.value
                                    if (cur == null ||
                                        !cur.awaitingDialogClose ||
                                        cur.pausedOnError ||
                                        !cur.hasMoreRuns
                                    ) {
                                        return@launch
                                    }
                                    repeatCoordinator.setCountdown(sec)
                                    delay(1000L)
                                }
                                val latest = repeatCoordinator.state.value
                                if (latest != null &&
                                    latest.awaitingDialogClose &&
                                    !latest.pausedOnError &&
                                    latest.hasMoreRuns
                                ) {
                                    triggerNextCustomUssdRepeatRun()
                                }
                            }
                        }
                    }
                }
            }
        }
        viewModelScope.launch {
            flowController.sessionState.collect { state ->
                val activeId = _uiState.value.activeTransaction?.localId
                when (state) {
                    is UssdSessionState.WaitingForCustomerApproval -> {
                        timeoutJob?.cancel()
                        if (activeId != null) {
                            txRepo.transitionStatusSafely(
                                localId = activeId,
                                newStatus = TransactionStatus.AWAITING_VERIFICATION,
                                confirmationSource = "CUSTOMER_APPROVAL_PENDING",
                                providerResponse = state.observedPrompt
                            )?.let { updated -> _uiState.update { it.copy(activeTransaction = updated) } }
                        }
                    }
                    is UssdSessionState.Completed -> {
                        timeoutJob?.cancel()
                        if (activeId != null) {
                            val targetStatus = when (state.outcome.category) {
                                OutcomeCategory.SUCCESSFUL -> TransactionStatus.SUCCESSFUL
                                OutcomeCategory.FAILED,
                                OutcomeCategory.INSUFFICIENT_BALANCE,
                                OutcomeCategory.INVALID_RECIPIENT -> TransactionStatus.FAILED
                                OutcomeCategory.PENDING,
                                OutcomeCategory.TIMEOUT,
                                OutcomeCategory.UNEXPECTED -> TransactionStatus.UNKNOWN
                            }
                            txRepo.transitionStatusSafely(
                                localId = activeId,
                                newStatus = targetStatus,
                                providerReference = state.outcome.providerReference,
                                confirmationSource = "AGENT_USSD",
                                providerResponse = state.outcome.message
                            )?.let { updated -> _uiState.update { it.copy(activeTransaction = updated) } }
                        }
                        if (repeatCoordinator.state.value != null) {
                            when (state.outcome.category) {
                                OutcomeCategory.FAILED,
                                OutcomeCategory.INSUFFICIENT_BALANCE,
                                OutcomeCategory.INVALID_RECIPIENT -> {
                                    repeatCountdownJob?.cancel()
                                    repeatCoordinator.markPausedOnError()
                                }
                                else -> {
                                    repeatCoordinator.markRunAwaitingClose()
                                }
                            }
                        }
                    }
                    is UssdSessionState.Cancelled -> {
                        timeoutJob?.cancel()
                        if (activeId != null) {
                            val cancelStatus = if (sessionManager.hasSentFinancialInput()) {
                                TransactionStatus.UNKNOWN
                            } else {
                                TransactionStatus.CANCELLED
                            }
                            txRepo.transitionStatusSafely(
                                localId = activeId,
                                newStatus = cancelStatus,
                                confirmationSource = "USER_CANCELLED",
                                providerResponse = state.reason
                            )?.let { updated -> _uiState.update { it.copy(activeTransaction = updated) } }
                        }
                        stopCustomUssdRepeatBatch()
                    }
                    is UssdSessionState.TimedOut -> {
                        if (activeId != null) {
                            val timeoutStatus = if (sessionManager.hasSentFinancialInput()) {
                                TransactionStatus.UNKNOWN
                            } else {
                                TransactionStatus.FAILED
                            }
                            txRepo.transitionStatusSafely(
                                localId = activeId,
                                newStatus = timeoutStatus,
                                confirmationSource = "TIMEOUT",
                                providerResponse = "Session timed out at step ${state.lastStepId}"
                            )?.let { updated -> _uiState.update { it.copy(activeTransaction = updated) } }
                        }
                        if (repeatCoordinator.state.value != null) {
                            repeatCountdownJob?.cancel()
                            repeatCoordinator.markPausedOnError()
                        }
                    }
                    is UssdSessionState.ManualInterventionRequired,
                    is UssdSessionState.ConfigurationRequired -> {
                        timeoutJob?.cancel()
                        if (repeatCoordinator.state.value != null) {
                            repeatCountdownJob?.cancel()
                            repeatCoordinator.markPausedOnError()
                        }
                    }
                    else -> Unit
                }
            }
        }
    }

    fun refreshDeviceStatus(force: Boolean = false) {
        val now = System.currentTimeMillis()
        if (!force && (now - lastDeviceRefreshMs) < DEVICE_REFRESH_THROTTLE_MS) {
            return
        }
        lastDeviceRefreshMs = now
        viewModelScope.launch(Dispatchers.IO) {
            val report = permissionManager.checkAll()
            val activeSims = simManager.getActiveSims()
            val sims = activeSims.map {
                SimSlotInfo(
                    subscriptionId = it.subscriptionId,
                    slotIndex = it.slotIndex,
                    carrierName = it.carrierName,
                    displayName = it.displayName
                )
            }
            val hasCall = report.callPhone == com.easymomo.app.ussd.CapabilityStatus.GRANTED
            val accessibilityEnabled = report.accessibilityService == com.easymomo.app.ussd.CapabilityStatus.GRANTED
            val logs = UssdDiagnosticLog.getEntries()
            val resolutions = simManager.reconcileAllNetworks(activeSims, report)
            _uiState.update {
                it.copy(
                    availableSims = sims,
                    activeSims = activeSims,
                    permissionReport = report,
                    selectedSimId = it.selectedSimId ?: sims.firstOrNull()?.subscriptionId,
                    hasCallPermission = hasCall,
                    isAccessibilityEnabled = accessibilityEnabled,
                    diagnosticLogs = logs,
                    simResolutions = resolutions
                )
            }
        }
    }

    fun assignSimToNetwork(network: NetworkType, sim: ActiveSimInfo) {
        viewModelScope.launch(Dispatchers.IO) {
            simManager.assignSimToNetwork(network, sim)
            val resolutions = simManager.reconcileAllNetworks()
            _uiState.update { it.copy(simResolutions = resolutions) }
            UssdDiagnosticLog.log("SIM", "Assigned ${sim.label} to ${network.displayName}")
        }
    }

    fun clearSimSettingsSavedMessage() {
        _uiState.update { it.copy(simSettingsSavedMessage = null) }
    }

    fun saveSimConfiguration(
        networkSelections: Map<NetworkType, ActiveSimInfo?>,
        featureSelections: Map<Pair<NetworkType, TransactionType>, ActiveSimInfo?>,
        customUssdFeatureSims: Map<String, Int> = _uiState.value.agentSettings.customUssdFeatureSims
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.update { it.copy(isSavingSimSettings = true, simSettingsSavedMessage = null) }
            try {
                simManager.saveAllSimConfiguration(networkSelections, featureSelections)
                simManager.updateAgentSettings {
                    it.copy(customUssdFeatureSims = customUssdFeatureSims)
                }
                val resolutions = simManager.reconcileAllNetworks()
                val updatedFeatures = simManager.allFeatureSelectionsFlow.first()
                val firstResolvedSimId = resolutions.values.firstNotNullOfOrNull { it.resolvedSim?.subscriptionId }
                UssdDiagnosticLog.log(
                    "SIM",
                    "Saved SIM configuration (networks=${networkSelections.count { it.value != null }}, featureOverrides=${featureSelections.count { it.value != null }})"
                )
                _uiState.update { state ->
                    state.copy(
                        simResolutions = resolutions,
                        featureSimSelections = updatedFeatures,
                        selectedSimId = firstResolvedSimId ?: state.selectedSimId,
                        diagnosticLogs = UssdDiagnosticLog.getEntries(),
                        isSavingSimSettings = false,
                        simSettingsSavedMessage = "SIM settings saved!"
                    )
                }
            } catch (e: Exception) {
                UssdDiagnosticLog.log("SIM", "Failed to save SIM configuration: ${e.message}")
                _uiState.update {
                    it.copy(
                        isSavingSimSettings = false,
                        simSettingsSavedMessage = null
                    )
                }
            }
        }
    }

    fun onAmountChange(value: String) {
        val filtered = value.filter { it.isDigit() || it == '.' }
        _uiState.update { it.copy(amountText = filtered, validationError = null) }
    }

    fun onPhoneChange(value: String) {
        val sanitized = sanitizeDashboardPhoneInput(value)
        val detected = NetworkPrefixDetector.detectNetwork(
            rawPhone = sanitized,
            isPorted = _uiState.value.isPorted,
            manualOverride = _uiState.value.manualNetwork
        )
        _uiState.update {
            it.copy(phoneText = sanitized, detectedNetwork = detected, validationError = null)
        }
        autocompleteJob?.cancel()
        if (!_uiState.value.agentSettings.saveCustomerNumbersEnabled) {
            if (_uiState.value.phoneSuggestions.isNotEmpty()) {
                _uiState.update { it.copy(phoneSuggestions = emptyList()) }
            }
            return
        }
        val digitCount = sanitized.count { it.isDigit() }
        if (digitCount < 2 && sanitized.trim().length < 2) {
            if (_uiState.value.phoneSuggestions.isNotEmpty()) {
                _uiState.update { it.copy(phoneSuggestions = emptyList()) }
            }
            return
        }
        autocompleteJob = viewModelScope.launch(Dispatchers.IO) {
            delay(80L)
            val matches = txRepo.searchCustomerNumberSuggestions(sanitized, limit = 5)
            // Hide dropdown if user already typed the exact single full number
            val normalizedInput = NetworkPrefixDetector.normalizeGhanaPhone(sanitized)
            val filtered = if (matches.size == 1 && matches.first().normalizedPhoneNumber == normalizedInput && sanitized.length >= 10) {
                emptyList()
            } else {
                matches
            }
            if (filtered != _uiState.value.phoneSuggestions) {
                _uiState.update { it.copy(phoneSuggestions = filtered) }
            }
        }
    }

    fun onPhoneSuggestionSelected(suggestion: CustomerNumberEntity) {
        autocompleteJob?.cancel()
        val sanitized = sanitizeDashboardPhoneInput(suggestion.phoneNumber)
        val detected = NetworkPrefixDetector.detectNetwork(
            rawPhone = sanitized,
            isPorted = _uiState.value.isPorted,
            manualOverride = _uiState.value.manualNetwork
        )
        _uiState.update {
            it.copy(
                phoneText = sanitized,
                detectedNetwork = detected,
                phoneSuggestions = emptyList(),
                validationError = null
            )
        }
    }

    fun dismissPhoneSuggestions() {
        autocompleteJob?.cancel()
        _uiState.update { it.copy(phoneSuggestions = emptyList()) }
    }

    fun updateCustomerNickname(id: Long, nickname: String?) {
        viewModelScope.launch {
            txRepo.updateCustomerNickname(id, nickname)
        }
    }

    fun deleteCustomerNumber(id: Long) {
        viewModelScope.launch {
            txRepo.deleteCustomerNumber(id)
        }
    }

    fun clearCustomerNumbers() {
        viewModelScope.launch {
            txRepo.clearCustomerNumbers()
        }
    }

    fun onPortedChange(ported: Boolean) {
        val detected = NetworkPrefixDetector.detectNetwork(
            rawPhone = _uiState.value.phoneText,
            isPorted = ported,
            manualOverride = _uiState.value.manualNetwork
        )
        _uiState.update { it.copy(isPorted = ported, detectedNetwork = detected, validationError = null) }
    }

    fun onManualNetworkSelect(network: NetworkType) {
        val detected = NetworkPrefixDetector.detectNetwork(
            rawPhone = _uiState.value.phoneText,
            isPorted = _uiState.value.isPorted,
            manualOverride = network
        )
        _uiState.update { it.copy(manualNetwork = network, detectedNetwork = detected, validationError = null) }
    }

    fun selectSim(subscriptionId: Int) {
        _uiState.update { it.copy(selectedSimId = subscriptionId) }
    }

    fun onRepeatCountChange(count: Int) {
        val clamped = CustomUssdRepeatCoordinator.clampRepeatCount(count)
        _uiState.update { it.copy(repeatCount = clamped) }
    }

    fun clearForm() {
        _uiState.update {
            it.copy(
                amountText = "",
                phoneText = "",
                isPorted = false,
                manualNetwork = NetworkType.UNKNOWN,
                detectedNetwork = NetworkType.UNKNOWN,
                validationError = null,
                phoneSuggestions = emptyList(),
                selectedBundle = null,
                merchantCode = "",
                customUssdCode = "",
                repeatCount = 1
            )
        }
    }

    fun dismissSubscriptionGate() {
        _uiState.update { it.copy(subscriptionGateVisible = false) }
    }

    fun clearValidationError() {
        _uiState.update { it.copy(validationError = null) }
    }

    fun dismissInlineUssd() {
        timeoutJob?.cancel()
        stopCustomUssdRepeatBatch()
        if (sessionManager.getActiveSessionId() != null) {
            flowController.resetSession()
        }
        _uiState.update { state ->
            if (state.agentSettings.autoClearFormAfterTx) {
                state.copy(
                    inlineUssdVisible = false,
                    amountText = "",
                    phoneText = "",
                    isPorted = false,
                    manualNetwork = NetworkType.UNKNOWN,
                    detectedNetwork = NetworkType.UNKNOWN,
                    validationError = null,
                    phoneSuggestions = emptyList()
                )
            } else {
                state.copy(inlineUssdVisible = false)
            }
        }
    }

    fun prepareAndRunInlineUssd(
        type: TransactionType,
        overrideNetwork: NetworkType? = null,
        bundle: DataBundleEntity? = null,
        isRepeatContinuation: Boolean = false,
        onCustomerLedRequired: (localId: String) -> Unit
    ) {
        val effectiveRepeat = if (isRepeatContinuation) 1 else _uiState.value.repeatCount
        prepareTransaction(
            type = type,
            overrideNetwork = overrideNetwork,
            bundle = bundle,
            onReady = { localId, isCustomerLed ->
                if (isCustomerLed) {
                    onCustomerLedRequired(localId)
                } else {
                    if (!isRepeatContinuation) {
                        if (effectiveRepeat > 1) {
                            repeatCountdownJob?.cancel()
                            val descriptor = SimpleCustomUssdFlow(
                                id = "std_${type.code}",
                                name = type.displayName,
                                operationType = CustomUssdOperationType.STEP_BY_STEP,
                                steps = emptyList(),
                                oneTimeCode = "",
                                simSlotIndex = 0,
                                formulaPreview = type.displayName
                            )
                            activeRepeatTarget = RepeatTarget.Standard(type, overrideNetwork)
                            repeatCoordinator.startBatch(descriptor, effectiveRepeat)
                        } else {
                            activeRepeatTarget = null
                            repeatCoordinator.clear()
                        }
                    }
                    _uiState.update { it.copy(inlineUssdVisible = true) }
                    executeAgentUssd(localId)
                }
            }
        )
    }

    fun bundlesForNetwork(network: NetworkType) = txRepo.observeActiveBundles(network)

    /**
     * Validates inputs & subscription entitlement, creates a local DRAFT transaction,
     * and invokes [onReady] with the draft localId and whether it's customer-led.
     */
    fun prepareTransaction(
        type: TransactionType,
        overrideNetwork: NetworkType? = null,
        bundle: DataBundleEntity? = null,
        merchantCode: String = "",
        customUssdCode: String = "",
        onReady: (localId: String, isCustomerLed: Boolean) -> Unit
    ) {
        if (!requireActiveAccessOrShowGate()) return

        val state = _uiState.value
        if (sessionManager.getActiveSessionId() != null || state.inlineUssdVisible) {
            return
        }
        val rawNetwork = overrideNetwork ?: state.detectedNetwork
        val network = if (rawNetwork == NetworkType.UNKNOWN &&
            (type == TransactionType.BALANCE || type == TransactionType.COMMISSION || type == TransactionType.STATEMENT)
        ) {
            NetworkType.MTN
        } else {
            rawNetwork
        }

        // Validate network & inputs depending on transaction type
        val requiresPhone = when {
            type == TransactionType.CUSTOM_USSD ||
                type == TransactionType.BALANCE ||
                type == TransactionType.STATEMENT ||
                type == TransactionType.COMMISSION ||
                type == TransactionType.MERCHANT_PAY -> false
            type == TransactionType.GIG -> network != NetworkType.MTN
            else -> true
        }
        if (requiresPhone) {
            if (network == NetworkType.UNKNOWN) {
                _uiState.update {
                    it.copy(validationError = "Enter a valid Ghana phone number or check 'Number is ported' and select a network.")
                }
                return
            }
            val phoneCheck = inputHandler.validatePhoneForNetwork(state.phoneText, network, state.isPorted)
            if (phoneCheck is UssdInputHandler.ValidationResult.Invalid) {
                _uiState.update { it.copy(validationError = phoneCheck.reason) }
                return
            }
        }

        val effectiveMerchantCode = merchantCode.trim().ifBlank { state.phoneText.trim() }
        if (type == TransactionType.MERCHANT_PAY && effectiveMerchantCode.isBlank()) {
            _uiState.update {
                it.copy(validationError = "Enter the merchant number in the Phone Number field and an amount.")
            }
            return
        }

        val amountValue = when (type) {
            TransactionType.DATA -> {
                if (bundle != null) {
                    bundle.price
                } else {
                    when (val amtCheck = inputHandler.formatAmount(state.amountText)) {
                        is UssdInputHandler.ValidationResult.Invalid -> {
                            _uiState.update { it.copy(validationError = amtCheck.reason) }
                            return
                        }
                        is UssdInputHandler.ValidationResult.Valid -> amtCheck.value.toDouble()
                    }
                }
            }
            TransactionType.BALANCE, TransactionType.STATEMENT, TransactionType.COMMISSION -> 0.0
            TransactionType.GIG, TransactionType.CUSTOM_USSD ->
                state.amountText.toDoubleOrNull() ?: 0.0
            else -> {
                when (val amtCheck = inputHandler.formatAmount(state.amountText)) {
                    is UssdInputHandler.ValidationResult.Invalid -> {
                        _uiState.update { it.copy(validationError = amtCheck.reason) }
                        return
                    }
                    is UssdInputHandler.ValidationResult.Valid -> amtCheck.value.toDouble()
                }
            }
        }

        val isCustomerLed = type == TransactionType.CASH_OUT &&
            (network == NetworkType.AT || network == NetworkType.TELECEL)

        val method = when {
            isCustomerLed -> ProcessingMethod.CUSTOMER_LED
            type == TransactionType.CUSTOM_USSD -> ProcessingMethod.MANUAL_USSD
            else -> ProcessingMethod.AGENT_USSD
        }

        val isSelfEnquiry = type == TransactionType.BALANCE ||
            type == TransactionType.COMMISSION ||
            type == TransactionType.STATEMENT
        val normalizedPhone = if (isSelfEnquiry) {
            "SELF"
        } else if (state.phoneText.isNotBlank()) {
            NetworkPrefixDetector.normalizeGhanaPhone(state.phoneText)
        } else {
            merchantCode.ifBlank { "SELF" }
        }

        viewModelScope.launch {
            val draft = txRepo.createDraftTransaction(
                network = network,
                type = type,
                processingMethod = method,
                recipientNumber = normalizedPhone,
                amount = amountValue,
                bundleId = bundle?.bundleId,
                bundleName = bundle?.displayName
            )
            _uiState.update {
                it.copy(
                    activeDraft = draft,
                    activeTransaction = draft,
                    selectedBundle = bundle,
                    merchantCode = effectiveMerchantCode,
                    customUssdCode = customUssdCode
                )
            }
            onReady(draft.localId, isCustomerLed)
        }
    }

    fun executeAgentUssd(txLocalId: String) {
        if (!requireActiveAccessOrShowGate()) return
        viewModelScope.launch {
            val tx = _uiState.value.activeTransaction ?: return@launch
            val networkType = NetworkType.fromCode(tx.network)
            val txType = runCatching { TransactionType.valueOf(tx.transactionType) }.getOrNull()
            val simResolution = withContext(Dispatchers.IO) {
                if (txType != null) {
                    simManager.resolveForNetworkAndFeature(networkType, txType)
                } else {
                    simManager.resolveForNetwork(networkType)
                }
            }
            if (simResolution.status == SimResolutionStatus.REQUIRES_SELECTION ||
                simResolution.status == SimResolutionStatus.SIM_UNAVAILABLE_ATTENTION_REQUIRED ||
                simResolution.status == SimResolutionStatus.NO_ACTIVE_SIM ||
                simResolution.status == SimResolutionStatus.PERMISSION_REQUIRED
            ) {
                UssdDiagnosticLog.log("SIM", "Blocked USSD for ${networkType.code}: ${simResolution.message}")
                withContext(Dispatchers.IO) {
                    txRepo.transitionStatusSafely(
                        localId = txLocalId,
                        newStatus = TransactionStatus.CANCELLED,
                        confirmationSource = "SIM_NOT_SELECTED",
                        providerResponse = simResolution.message
                    )
                }?.let { updated -> _uiState.update { it.copy(activeTransaction = updated) } }
                sessionManager.updateState(
                    UssdSessionState.ManualInterventionRequired(txLocalId, simResolution.message, null)
                )
                refreshDeviceStatus()
                return@launch
            }
            val flowId = tx.bundleId?.takeIf { it.startsWith("TELECEL_GIG_") }
                ?: "${tx.network}_${tx.transactionType}"
            val (transitioned, profile) = withContext(Dispatchers.IO) {
                val updatedTx = txRepo.transitionStatus(
                    localId = txLocalId,
                    newStatus = TransactionStatus.PROCESSING,
                    details = "Starting agent USSD workflow"
                )
                val loadedProfile = txRepo.getUssdFlowProfile(flowId)
                updatedTx to loadedProfile
            }
            _uiState.update {
                it.copy(
                    activeTransaction = transitioned,
                    selectedSimLabel = simResolution.resolvedSim?.label
                )
            }
            val resolvedSimId = simResolution.resolvedSim?.subscriptionId ?: _uiState.value.selectedSimId
            if (profile == null || profile.requiresSimVerification()) {
                sessionManager.updateState(UssdSessionState.ConfigurationRequired(flowId))
                return@launch
            }

            val isSelfEnquiry = txType == TransactionType.BALANCE ||
                txType == TransactionType.COMMISSION ||
                txType == TransactionType.STATEMENT
            val vars = if (isSelfEnquiry) {
                UssdInputHandler.TransactionVariables(
                    customerPhone = "",
                    recipientPhone = "",
                    amount = "",
                    bundleCode = "",
                    bundlePrice = "",
                    network = networkType,
                    merchantCode = "",
                    customUssdCode = _uiState.value.customUssdCode,
                    reference = "",
                    operationalId = if (_uiState.value.agentSettings.autoEnterTelecelOperationalId) {
                        _uiState.value.telecelOperationalId.trim()
                    } else {
                        ""
                    },
                    isPortedNumber = false
                )
            } else {
                val formattedAmount = formatUssdAmount(tx.amount)
                UssdInputHandler.TransactionVariables(
                    customerPhone = tx.recipientNumber,
                    recipientPhone = tx.recipientNumber,
                    amount = formattedAmount,
                    bundleCode = tx.bundleId.orEmpty(),
                    bundlePrice = formattedAmount,
                    network = networkType,
                    merchantCode = _uiState.value.merchantCode,
                    customUssdCode = _uiState.value.customUssdCode,
                    reference = if (flowId == "MTN_CASH_IN" || flowId == "MTN_CASH_OUT") {
                        ""
                    } else {
                        "AMA${tx.localId.take(6).uppercase()}"
                    },
                    operationalId = if (_uiState.value.agentSettings.autoEnterTelecelOperationalId) {
                        _uiState.value.telecelOperationalId.trim()
                    } else {
                        ""
                    },
                    isPortedNumber = _uiState.value.isPorted
                )
            }

            val startResult = flowController.startFlow(txLocalId, profile, vars)
            if (startResult.isFailure) return@launch

            EasyMoMoAccessibilityService.instance?.resetSessionObservation()
            val launchResult = ussdLauncher.launchUssd(
                ussdCode = profile.entryCode,
                subscriptionId = resolvedSimId,
                forceInteractiveDialog = true,
                onTelephonyResponse = { response -> flowController.onPromptObserved(response) },
                onTelephonyFailure = { _ ->
                    flowController.cancelSession()
                }
            )
            if (launchResult is UssdLaunchResult.Error) {
                txRepo.transitionStatusSafely(
                    localId = txLocalId,
                    newStatus = TransactionStatus.FAILED,
                    confirmationSource = "LAUNCHER_ERROR",
                    providerResponse = launchResult.message
                )?.let { updated -> _uiState.update { it.copy(activeTransaction = updated) } }
                sessionManager.endSession(
                    UssdSessionState.ManualInterventionRequired(
                        sessionId = txLocalId,
                        reason = launchResult.message,
                        observedPrompt = null
                    )
                )
                return@launch
            }

            timeoutJob?.cancel()
            timeoutJob = viewModelScope.launch {
                delay(profile.timeoutSeconds * 1000L)
                flowController.onTimeout()
            }
        }
    }

    fun runCustomUssdFlow(flow: SimpleCustomUssdFlow, repeatCount: Int = 1) {
        if (!requireActiveAccessOrShowGate()) return
        val state = _uiState.value
        if (sessionManager.getActiveSessionId() != null || state.inlineUssdVisible) {
            return
        }

        val needsPhone = CustomUssdFlowBuilder.requiresPhone(flow)
        val needsAmount = CustomUssdFlowBuilder.requiresAmount(flow)

        var normalizedPhone = ""
        if (needsPhone) {
            val phoneCheck = inputHandler.validatePhoneForNetwork(state.phoneText, NetworkType.MTN, isPorted = true)
            if (phoneCheck is UssdInputHandler.ValidationResult.Invalid) {
                _uiState.update { it.copy(validationError = phoneCheck.reason) }
                return
            }
            normalizedPhone = NetworkPrefixDetector.normalizeGhanaPhone(state.phoneText)
        } else if (state.phoneText.isNotBlank()) {
            normalizedPhone = NetworkPrefixDetector.normalizeGhanaPhone(state.phoneText)
        }

        var amountValue = 0.0
        var formattedAmount = ""
        if (needsAmount) {
            when (val amtCheck = inputHandler.formatAmount(state.amountText)) {
                is UssdInputHandler.ValidationResult.Invalid -> {
                    _uiState.update { it.copy(validationError = amtCheck.reason) }
                    return
                }
                is UssdInputHandler.ValidationResult.Valid -> {
                    amountValue = amtCheck.value.toDouble()
                    formattedAmount = formatUssdAmount(amountValue)
                }
            }
        } else {
            val parsed = state.amountText.toDoubleOrNull()
            if (parsed != null && parsed > 0.0) {
                amountValue = parsed
                formattedAmount = formatUssdAmount(parsed)
            }
        }

        lastCustomRunPhone = normalizedPhone
        lastCustomRunAmountValue = amountValue
        lastCustomRunFormattedAmount = formattedAmount
        repeatCountdownJob?.cancel()
        val effectiveRepeat = CustomUssdRepeatCoordinator.clampRepeatCount(repeatCount)
        activeRepeatTarget = RepeatTarget.Custom(flow)
        repeatCoordinator.startBatch(flow, effectiveRepeat)

        viewModelScope.launch {
            launchSingleCustomUssdRun(
                flow = flow,
                normalizedPhone = normalizedPhone,
                amountValue = amountValue,
                formattedAmount = formattedAmount
            )
        }
    }

    fun runPresetUssdFlow(
        preset: PresetUssdFlow,
        overrideAmount: String? = null,
        overrideCodeOrTill: String? = null,
        overrideSimId: Int? = null,
        repeatCount: Int = _uiState.value.repeatCount,
        isRepeatContinuation: Boolean = false
    ): String? {
        if (!requireActiveAccessOrShowGate()) return null
        val state = _uiState.value
        if (sessionManager.getActiveSessionId() != null || state.inlineUssdVisible) {
            return "Another USSD session is already running."
        }

        var normalizedPhone = ""
        if (preset.requiresPhone) {
            val phoneCheck = inputHandler.validatePhoneForNetwork(state.phoneText, NetworkType.MTN, isPorted = true)
            if (phoneCheck is UssdInputHandler.ValidationResult.Invalid) {
                _uiState.update { it.copy(validationError = phoneCheck.reason) }
                return phoneCheck.reason
            }
            normalizedPhone = NetworkPrefixDetector.normalizeGhanaPhone(state.phoneText)
        }

        val cleanCode = (overrideCodeOrTill ?: "").trim()
        if (preset.requiresCodeOrTill) {
            if (cleanCode.isBlank()) {
                val msg = "Enter the Agent Code or Till Number."
                _uiState.update { it.copy(validationError = msg) }
                return msg
            }
            if (preset.id == "BUS_WITHDRAWAL_TILL_NUMBER" && !cleanCode.all { it.isDigit() }) {
                val msg = "Till Number must contain numbers only."
                _uiState.update { it.copy(validationError = msg) }
                return msg
            }
            if (!cleanCode.all { it.isLetterOrDigit() }) {
                val msg = "Code must contain letters and numbers only."
                _uiState.update { it.copy(validationError = msg) }
                return msg
            }
        }

        var amountValue = 0.0
        var formattedAmount = ""
        val rawAmount = overrideAmount ?: state.amountText
        if (preset.requiresAmount) {
            when (val amtCheck = inputHandler.formatAmount(rawAmount)) {
                is UssdInputHandler.ValidationResult.Invalid -> {
                    _uiState.update { it.copy(validationError = amtCheck.reason) }
                    return amtCheck.reason
                }
                is UssdInputHandler.ValidationResult.Valid -> {
                    amountValue = amtCheck.value.toDouble()
                    formattedAmount = formatUssdAmount(amountValue)
                }
            }
        }

        _uiState.update { it.copy(validationError = null) }

        if (!isRepeatContinuation) {
            val effectiveRepeat = CustomUssdRepeatCoordinator.clampRepeatCount(repeatCount)
            if (effectiveRepeat > 1) {
                repeatCountdownJob?.cancel()
                val descriptor = SimpleCustomUssdFlow(
                    id = preset.id,
                    name = preset.title,
                    operationType = CustomUssdOperationType.STEP_BY_STEP,
                    steps = emptyList(),
                    oneTimeCode = "",
                    simSlotIndex = 0,
                    formulaPreview = preset.subtitle
                )
                activeRepeatTarget = RepeatTarget.Preset(
                    preset = preset,
                    overrideAmount = overrideAmount,
                    overrideCodeOrTill = overrideCodeOrTill,
                    overrideSimId = overrideSimId
                )
                repeatCoordinator.startBatch(descriptor, effectiveRepeat)
            } else {
                activeRepeatTarget = null
                repeatCoordinator.clear()
            }
        }

        viewModelScope.launch {
            val activeSims = withContext(Dispatchers.IO) { simManager.getActiveSims() }
            val effectiveSimId = overrideSimId
                ?: state.agentSettings.customUssdFeatureSims[preset.featureKey.key]
            val explicitSim = effectiveSimId?.let { id ->
                activeSims.firstOrNull { it.subscriptionId == id }
            }
            val resolvedSim = if (explicitSim != null) {
                explicitSim
            } else if (preset.network != NetworkType.UNKNOWN) {
                val res = withContext(Dispatchers.IO) {
                    simManager.resolveForNetworkAndFeature(preset.network, preset.transactionType)
                }
                if (res.status == SimResolutionStatus.REQUIRES_SELECTION ||
                    res.status == SimResolutionStatus.SIM_UNAVAILABLE_ATTENTION_REQUIRED ||
                    res.status == SimResolutionStatus.NO_ACTIVE_SIM ||
                    res.status == SimResolutionStatus.PERMISSION_REQUIRED
                ) {
                    val draft = withContext(Dispatchers.IO) {
                        txRepo.createDraftTransaction(
                            network = preset.network,
                            type = preset.transactionType,
                            processingMethod = ProcessingMethod.AGENT_USSD,
                            recipientNumber = normalizedPhone.ifBlank { cleanCode.ifBlank { "SELF" } },
                            amount = amountValue,
                            bundleId = preset.id,
                            bundleName = preset.title
                        )
                    }
                    withContext(Dispatchers.IO) {
                        txRepo.transitionStatusSafely(
                            localId = draft.localId,
                            newStatus = TransactionStatus.CANCELLED,
                            confirmationSource = "SIM_NOT_SELECTED",
                            providerResponse = res.message
                        )
                    }?.let { updated -> _uiState.update { it.copy(activeTransaction = updated) } }
                        ?: _uiState.update { it.copy(activeTransaction = draft) }
                    _uiState.update {
                        it.copy(
                            activeDraft = draft,
                            inlineUssdVisible = true,
                            selectedSimLabel = preset.network.displayName,
                            customUssdCode = preset.subtitle
                        )
                    }
                    sessionManager.updateState(
                        UssdSessionState.ManualInterventionRequired(draft.localId, res.message, null)
                    )
                    refreshDeviceStatus(force = true)
                    return@launch
                }
                res.resolvedSim ?: activeSims.firstOrNull { it.subscriptionId == state.selectedSimId }
                    ?: activeSims.firstOrNull()
            } else {
                activeSims.firstOrNull { it.subscriptionId == state.selectedSimId }
                    ?: activeSims.firstOrNull()
            }

            val txNetwork = if (preset.network != NetworkType.UNKNOWN) {
                preset.network
            } else {
                resolvedSim?.detectedCarrierNetwork?.takeIf { it != NetworkType.UNKNOWN } ?: NetworkType.MTN
            }

            val draft = withContext(Dispatchers.IO) {
                txRepo.createDraftTransaction(
                    network = txNetwork,
                    type = preset.transactionType,
                    processingMethod = ProcessingMethod.AGENT_USSD,
                    recipientNumber = normalizedPhone.ifBlank { cleanCode.ifBlank { "SELF" } },
                    amount = amountValue,
                    bundleId = preset.id,
                    bundleName = preset.title
                )
            }

            if (resolvedSim == null) {
                val msg = "No active SIM card detected. Insert a SIM and check Settings → SIM Management."
                withContext(Dispatchers.IO) {
                    txRepo.transitionStatusSafely(
                        localId = draft.localId,
                        newStatus = TransactionStatus.CANCELLED,
                        confirmationSource = "SIM_NOT_SELECTED",
                        providerResponse = msg
                    )
                }?.let { updated -> _uiState.update { it.copy(activeTransaction = updated) } }
                    ?: _uiState.update { it.copy(activeTransaction = draft) }
                _uiState.update {
                    it.copy(
                        activeDraft = draft,
                        inlineUssdVisible = true,
                        customUssdCode = preset.subtitle
                    )
                }
                sessionManager.updateState(
                    UssdSessionState.ManualInterventionRequired(draft.localId, msg, null)
                )
                return@launch
            }

            val transitioned = withContext(Dispatchers.IO) {
                txRepo.transitionStatus(
                    localId = draft.localId,
                    newStatus = TransactionStatus.PROCESSING,
                    details = "Starting ${preset.title}"
                )
            }
            _uiState.update {
                it.copy(
                    activeDraft = draft,
                    activeTransaction = transitioned,
                    selectedSimLabel = resolvedSim.label,
                    inlineUssdVisible = true,
                    merchantCode = cleanCode,
                    customUssdCode = preset.subtitle
                )
            }

            val vars = UssdInputHandler.TransactionVariables(
                customerPhone = normalizedPhone,
                recipientPhone = normalizedPhone,
                amount = formattedAmount,
                bundleCode = "",
                bundlePrice = formattedAmount,
                network = if (preset.network == NetworkType.UNKNOWN) NetworkType.UNKNOWN else txNetwork,
                merchantCode = cleanCode,
                customUssdCode = preset.subtitle,
                operationalId = if (_uiState.value.agentSettings.autoEnterTelecelOperationalId) {
                    _uiState.value.telecelOperationalId.trim()
                } else {
                    ""
                },
                isPortedNumber = if (preset.network == NetworkType.UNKNOWN) true else _uiState.value.isPorted
            )

            val startResult = flowController.startFlow(draft.localId, preset.profile, vars)
            if (startResult.isFailure) return@launch

            EasyMoMoAccessibilityService.instance?.resetSessionObservation()
            val launchResult = ussdLauncher.launchUssd(
                ussdCode = preset.profile.entryCode,
                subscriptionId = resolvedSim.subscriptionId,
                forceInteractiveDialog = true,
                onTelephonyResponse = { response -> flowController.onPromptObserved(response) },
                onTelephonyFailure = { _ -> flowController.cancelSession() }
            )
            if (launchResult is UssdLaunchResult.Error) {
                withContext(Dispatchers.IO) {
                    txRepo.transitionStatusSafely(
                        localId = draft.localId,
                        newStatus = TransactionStatus.FAILED,
                        confirmationSource = "LAUNCHER_ERROR",
                        providerResponse = launchResult.message
                    )
                }?.let { updated -> _uiState.update { it.copy(activeTransaction = updated) } }
                sessionManager.endSession(
                    UssdSessionState.ManualInterventionRequired(draft.localId, launchResult.message, null)
                )
                return@launch
            }

            timeoutJob?.cancel()
            timeoutJob = viewModelScope.launch {
                delay(preset.profile.timeoutSeconds * 1000L)
                flowController.onTimeout()
            }
        }
        return null
    }

    private suspend fun launchSingleCustomUssdRun(
        flow: SimpleCustomUssdFlow,
        normalizedPhone: String,
        amountValue: Double,
        formattedAmount: String
    ) {
        val activeSims = withContext(Dispatchers.IO) { simManager.getActiveSims() }
        val resolvedSim = activeSims.firstOrNull { it.slotIndex == flow.simSlotIndex }
            ?: if (activeSims.size == 1) activeSims.first() else null

        val txNetwork = resolvedSim?.detectedCarrierNetwork
            ?.takeIf { it != NetworkType.UNKNOWN }
            ?: NetworkType.MTN

        val draft = withContext(Dispatchers.IO) {
            txRepo.createDraftTransaction(
                network = txNetwork,
                type = TransactionType.CUSTOM_USSD,
                processingMethod = ProcessingMethod.AGENT_USSD,
                recipientNumber = normalizedPhone.ifBlank { "SELF" },
                amount = amountValue,
                bundleId = flow.id,
                bundleName = flow.name
            )
        }

        if (resolvedSim == null) {
            val msg = if (activeSims.isEmpty()) {
                "No active SIM card detected. Insert a SIM and check Settings → SIM Management."
            } else {
                "SIM ${flow.simSlotIndex + 1} is not active on this device. Edit the Custom USSD SIM slot or check Settings → SIM Management."
            }
            txRepo.transitionStatusSafely(
                localId = draft.localId,
                newStatus = TransactionStatus.CANCELLED,
                confirmationSource = "SIM_NOT_SELECTED",
                providerResponse = msg
            )?.let { updated -> _uiState.update { it.copy(activeTransaction = updated) } }
                ?: _uiState.update { it.copy(activeTransaction = draft) }
            _uiState.update {
                it.copy(
                    activeDraft = draft,
                    inlineUssdVisible = true,
                    selectedSimLabel = "SIM ${flow.simSlotIndex + 1}",
                    customUssdCode = flow.formulaPreview
                )
            }
            repeatCoordinator.markPausedOnError()
            sessionManager.updateState(
                UssdSessionState.ManualInterventionRequired("custom-${flow.id}", msg, null)
            )
            refreshDeviceStatus(force = true)
            return
        }

        val transitioned = txRepo.transitionStatus(
            localId = draft.localId,
            newStatus = TransactionStatus.PROCESSING,
            details = "Starting custom USSD '${flow.name}'"
        )
        _uiState.update {
            it.copy(
                activeDraft = draft,
                activeTransaction = transitioned,
                selectedSimLabel = resolvedSim.label,
                inlineUssdVisible = true,
                customUssdCode = flow.formulaPreview
            )
        }

        if (flow.operationType == CustomUssdOperationType.ONE_TIME) {
            val dialCode = CustomUssdFlowBuilder.resolveOneTimeCode(
                flow.oneTimeCode,
                normalizedPhone,
                formattedAmount
            )
            EasyMoMoAccessibilityService.instance?.resetSessionObservation()
            val launchResult = ussdLauncher.launchUssd(
                ussdCode = dialCode,
                subscriptionId = resolvedSim.subscriptionId,
                forceInteractiveDialog = true,
                onTelephonyResponse = {},
                onTelephonyFailure = {}
            )
            if (launchResult is UssdLaunchResult.Error) {
                txRepo.transitionStatusSafely(
                    localId = draft.localId,
                    newStatus = TransactionStatus.FAILED,
                    confirmationSource = "LAUNCHER_ERROR",
                    providerResponse = launchResult.message
                )?.let { updated -> _uiState.update { it.copy(activeTransaction = updated) } }
                repeatCoordinator.markPausedOnError()
                sessionManager.endSession(
                    UssdSessionState.ManualInterventionRequired(draft.localId, launchResult.message, null)
                )
            } else {
                repeatCoordinator.markRunAwaitingClose()
                sessionManager.endSession(
                    UssdSessionState.Completed(
                        draft.localId,
                        UssdOutcome(
                            category = OutcomeCategory.PENDING,
                            rawResponse = "",
                            providerReference = null,
                            message = "Dialed $dialCode on ${resolvedSim.label}. Complete any remaining prompt in the USSD dialog."
                        )
                    )
                )
            }
            return
        }

        val profile = CustomUssdFlowBuilder.buildStepByStepProfile(flow)
        val vars = UssdInputHandler.TransactionVariables(
            customerPhone = normalizedPhone,
            recipientPhone = normalizedPhone,
            amount = formattedAmount,
            bundlePrice = formattedAmount,
            network = txNetwork,
            customUssdCode = flow.formulaPreview,
            operationalId = if (_uiState.value.agentSettings.autoEnterTelecelOperationalId) {
                _uiState.value.telecelOperationalId.trim()
            } else {
                ""
            },
            isPortedNumber = true
        )

        val startResult = flowController.startFlow(draft.localId, profile, vars)
        if (startResult.isFailure) {
            repeatCoordinator.markPausedOnError()
            return
        }

        EasyMoMoAccessibilityService.instance?.resetSessionObservation()
        val launchResult = ussdLauncher.launchUssd(
            ussdCode = profile.entryCode,
            subscriptionId = resolvedSim.subscriptionId,
            forceInteractiveDialog = true,
            onTelephonyResponse = { response -> flowController.onPromptObserved(response) },
            onTelephonyFailure = { _ -> flowController.cancelSession() }
        )
        if (launchResult is UssdLaunchResult.Error) {
            txRepo.transitionStatusSafely(
                localId = draft.localId,
                newStatus = TransactionStatus.FAILED,
                confirmationSource = "LAUNCHER_ERROR",
                providerResponse = launchResult.message
            )?.let { updated -> _uiState.update { it.copy(activeTransaction = updated) } }
            repeatCoordinator.markPausedOnError()
            sessionManager.endSession(
                UssdSessionState.ManualInterventionRequired(draft.localId, launchResult.message, null)
            )
            return
        }

        timeoutJob?.cancel()
        timeoutJob = viewModelScope.launch {
            delay(profile.timeoutSeconds * 1000L)
            flowController.onTimeout()
        }
    }

    fun runNextCustomUssdRepeatNow() {
        if (!requireActiveAccessOrShowGate()) return
        val batch = repeatCoordinator.state.value ?: return
        if (!batch.hasMoreRuns || (!batch.awaitingDialogClose && !batch.pausedOnError)) return
        repeatCountdownJob?.cancel()
        repeatCoordinator.cancelCountdown()
        triggerNextCustomUssdRepeatRun()
    }

    fun stopCustomUssdRepeatBatch() {
        repeatCountdownJob?.cancel()
        repeatCountdownJob = null
        activeRepeatTarget = null
        repeatCoordinator.clear()
        lastCustomRunPhone = ""
        lastCustomRunAmountValue = 0.0
        lastCustomRunFormattedAmount = ""
    }

    private fun triggerNextCustomUssdRepeatRun() {
        val next = repeatCoordinator.advanceToNextRun() ?: return
        timeoutJob?.cancel()
        flowController.resetSession()
        when (val target = activeRepeatTarget) {
            is RepeatTarget.Standard -> {
                _uiState.update { it.copy(inlineUssdVisible = false) }
                prepareAndRunInlineUssd(
                    type = target.type,
                    overrideNetwork = target.overrideNetwork,
                    isRepeatContinuation = true,
                    onCustomerLedRequired = {}
                )
            }
            is RepeatTarget.Preset -> {
                _uiState.update { it.copy(inlineUssdVisible = false) }
                runPresetUssdFlow(
                    preset = target.preset,
                    overrideAmount = target.overrideAmount,
                    overrideCodeOrTill = target.overrideCodeOrTill,
                    overrideSimId = target.overrideSimId,
                    repeatCount = 1,
                    isRepeatContinuation = true
                )
            }
            is RepeatTarget.Custom, null -> {
                viewModelScope.launch {
                    launchSingleCustomUssdRun(
                        flow = next.flow,
                        normalizedPhone = lastCustomRunPhone,
                        amountValue = lastCustomRunAmountValue,
                        formattedAmount = lastCustomRunFormattedAmount
                    )
                }
            }
        }
    }

    private fun formatUssdAmount(amount: Double): String {
        val bd = BigDecimal.valueOf(amount).stripTrailingZeros()
        return if (bd.scale() <= 0) {
            bd.setScale(0).toPlainString()
        } else {
            bd.toPlainString()
        }
    }

    fun confirmUssdStep() = flowController.confirmPendingStep()

    fun cancelUssdSession() {
        timeoutJob?.cancel()
        stopCustomUssdRepeatBatch()
        if (sessionManager.getActiveSessionId() != null) {
            flowController.cancelSession()
        } else {
            // System USSD dialog may have already ended the session before user tapped Cancel in-app
            val activeId = _uiState.value.activeTransaction?.localId
            if (activeId != null && !sessionManager.hasSentFinancialInput()) {
                viewModelScope.launch {
                    txRepo.transitionStatusSafely(
                        localId = activeId,
                        newStatus = TransactionStatus.CANCELLED,
                        confirmationSource = "USER_CANCELLED",
                        providerResponse = "Cancelled in app after system USSD dismissal"
                    )?.let { updated -> _uiState.update { it.copy(activeTransaction = updated) } }
                }
            }
            sessionManager.resetToIdle()
        }
        _uiState.update { it.copy(inlineUssdVisible = false) }
    }

    fun resetUssdSession() {
        timeoutJob?.cancel()
        flowController.resetSession()
    }

    fun markManualOutcome(
        txLocalId: String,
        status: TransactionStatus,
        providerReference: String?,
        notes: String?
    ) {
        viewModelScope.launch {
            val updated = txRepo.transitionStatusSafely(
                localId = txLocalId,
                newStatus = status,
                providerReference = providerReference?.takeIf { it.isNotBlank() },
                confirmationSource = "AGENT_VERIFIED",
                providerResponse = notes?.takeIf { it.isNotBlank() } ?: "Verified by agent",
                isExplicitAgentVerification = true
            )
            if (updated != null) {
                _uiState.update { it.copy(activeTransaction = updated) }
            }
            sessionManager.resetToIdle()
        }
    }

    fun advanceCustomerLedToVerification(txLocalId: String) {
        viewModelScope.launch {
            val current = _uiState.value.activeTransaction ?: return@launch
            val step1 = if (current.status == TransactionStatus.DRAFT.code) {
                txRepo.transitionStatus(
                    localId = txLocalId,
                    newStatus = TransactionStatus.AWAITING_CUSTOMER,
                    details = "Customer instructed on self-service cash-out"
                )
            } else current

            val step2 = txRepo.transitionStatus(
                localId = txLocalId,
                newStatus = TransactionStatus.AWAITING_VERIFICATION,
                details = "Awaiting agent verification checklist"
            )
            _uiState.update { it.copy(activeTransaction = step2) }
        }
    }

    fun saveVerifiedUssdFlow(profile: UssdFlowProfile) {
        viewModelScope.launch {
            txRepo.saveCustomOrVerifiedFlow(profile)
        }
    }

    // Safe Test Mode Simulator (never dials real USSD or creates real financial records)
    private var simTestController: UssdFlowController? = null

    fun startSimulatedMtnCashIn() {
        viewModelScope.launch {
            val profile = txRepo.getUssdFlowProfile("MTN_CASH_IN") ?: return@launch
            val simulatedSender = UssdResponseSender { reply ->
                _uiState.update {
                    it.copy(
                        testModeLastAction = "Simulated auto-reply sent: '$reply'",
                        diagnosticLogs = UssdDiagnosticLog.getEntries()
                    )
                }
                true
            }
            val controller = UssdFlowController(
                sessionManager = sessionManager,
                inputHandler = inputHandler,
                responseSender = simulatedSender
            )
            simTestController = controller
            sessionManager.resetToIdle()
            controller.startFlow(
                transactionLocalId = "sim-test-001",
                profile = profile,
                variables = UssdInputHandler.TransactionVariables(
                    customerPhone = "0535308873",
                    recipientPhone = "0535308873",
                    amount = "10.00",
                    network = NetworkType.MTN
                )
            )
            _uiState.update {
                it.copy(
                    testModeLastAction = "Started simulated MTN_CASH_IN (${profile.entryCode})",
                    diagnosticLogs = UssdDiagnosticLog.getEntries()
                )
            }
        }
    }

    fun simulatePrompt(promptText: String) {
        val controller = simTestController ?: flowController
        controller.onPromptObserved(promptText)
        _uiState.update { it.copy(diagnosticLogs = UssdDiagnosticLog.getEntries()) }
    }

    fun simulateTimeout() {
        val controller = simTestController ?: flowController
        controller.onTimeout()
        _uiState.update { it.copy(diagnosticLogs = UssdDiagnosticLog.getEntries()) }
    }

    fun clearDiagnosticLogs() {
        UssdDiagnosticLog.clear()
        _uiState.update { it.copy(diagnosticLogs = emptyList()) }
    }

    class Factory(
        private val txRepo: TransactionRepository,
        private val authRepo: AuthSubscriptionRepository,
        private val ussdLauncher: UssdLauncher,
        private val simManager: SimManager,
        private val permissionManager: PermissionCapabilityManager,
        private val customerSyncManager: CustomerSyncManager? = null
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return TerminalViewModel(
                txRepo,
                authRepo,
                ussdLauncher,
                simManager,
                permissionManager,
                customerSyncManager
            ) as T
        }
    }

    companion object {
        private const val DEVICE_REFRESH_THROTTLE_MS = 5_000L
        private const val SUBSCRIPTION_REFRESH_THROTTLE_MS = 60_000L
        private const val AUTO_CUSTOMER_SYNC_THROTTLE_MS = 30_000L

        internal fun sanitizeDashboardPhoneInput(raw: String): String {
            val digitsOnly = raw.filter { it.isDigit() }
            if ((raw.trim().startsWith("+233") || digitsOnly.startsWith("233")) && digitsOnly.length >= 12) {
                return NetworkPrefixDetector.normalizeGhanaPhone(digitsOnly)
                    .filter { it.isDigit() }
                    .take(10)
            }
            return digitsOnly.take(10)
        }
    }
}

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
import com.easymomo.app.ussd.model.OutcomeCategory
import com.easymomo.app.ussd.model.UssdFlowProfile
import com.easymomo.app.ussd.model.UssdSessionState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
    val testModeLastAction: String? = null
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

    private var timeoutJob: Job? = null
    private var autocompleteJob: Job? = null
    @Volatile
    private var lastDeviceRefreshMs: Long = 0L

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
        viewModelScope.launch(Dispatchers.IO) {
            customerSyncManager?.syncNow()
        }
    }

    fun saveTelecelOperationalId(operationalId: String) {
        viewModelScope.launch {
            simManager.saveTelecelOperationalId(operationalId)
        }
    }

    private fun observeUssdPrompts() {
        viewModelScope.launch(Dispatchers.Default) {
            UssdPromptObserver.prompts.collect { observed ->
                flowController.onPromptObserved(observed.text)
            }
        }
        viewModelScope.launch {
            flowController.sessionState.collect { state ->
                val activeId = _uiState.value.activeTransaction?.localId ?: return@collect
                when (state) {
                    is UssdSessionState.WaitingForCustomerApproval -> {
                        timeoutJob?.cancel()
                        txRepo.transitionStatusSafely(
                            localId = activeId,
                            newStatus = TransactionStatus.AWAITING_VERIFICATION,
                            confirmationSource = "CUSTOMER_APPROVAL_PENDING",
                            providerResponse = state.observedPrompt
                        )?.let { updated -> _uiState.update { it.copy(activeTransaction = updated) } }
                    }
                    is UssdSessionState.Completed -> {
                        timeoutJob?.cancel()
                        val targetStatus = when (state.outcome.category) {
                            OutcomeCategory.SUCCESSFUL -> TransactionStatus.SUCCESSFUL
                            OutcomeCategory.FAILED,
                            OutcomeCategory.INSUFFICIENT_BALANCE,
                            OutcomeCategory.INVALID_RECIPIENT -> TransactionStatus.FAILED
                            OutcomeCategory.PENDING,
                            OutcomeCategory.TIMEOUT,
                            OutcomeCategory.UNEXPECTED -> TransactionStatus.UNKNOWN
                        }
                        if (targetStatus != TransactionStatus.UNKNOWN) {
                            txRepo.transitionStatusSafely(
                                localId = activeId,
                                newStatus = targetStatus,
                                providerReference = state.outcome.providerReference,
                                confirmationSource = "AGENT_USSD",
                                providerResponse = state.outcome.message
                            )?.let { updated -> _uiState.update { it.copy(activeTransaction = updated) } }
                        }
                    }
                    is UssdSessionState.Cancelled -> {
                        timeoutJob?.cancel()
                        if (!sessionManager.hasSentFinancialInput()) {
                            txRepo.transitionStatusSafely(
                                localId = activeId,
                                newStatus = TransactionStatus.CANCELLED,
                                confirmationSource = "USER_CANCELLED",
                                providerResponse = state.reason
                            )?.let { updated -> _uiState.update { it.copy(activeTransaction = updated) } }
                        }
                    }
                    is UssdSessionState.TimedOut -> {
                        if (!sessionManager.hasSentFinancialInput()) {
                            txRepo.transitionStatusSafely(
                                localId = activeId,
                                newStatus = TransactionStatus.FAILED,
                                confirmationSource = "TIMEOUT",
                                providerResponse = "Session timed out at step ${state.lastStepId}"
                            )?.let { updated -> _uiState.update { it.copy(activeTransaction = updated) } }
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
            val sims = ussdLauncher.getAvailableSims()
            val report = permissionManager.checkAll()
            val activeSims = simManager.getActiveSims()
            val hasCall = ussdLauncher.hasCallPermission()
            val accessibilityEnabled = permissionManager.isAccessibilityServiceEnabled()
            val logs = UssdDiagnosticLog.getEntries()
            val resolutions = simManager.reconcileAllNetworks()
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
        viewModelScope.launch {
            simManager.assignSimToNetwork(network, sim)
            val resolutions = simManager.reconcileAllNetworks()
            _uiState.update { it.copy(simResolutions = resolutions) }
            UssdDiagnosticLog.log("SIM", "Assigned ${sim.label} to ${network.displayName}")
        }
    }

    fun onAmountChange(value: String) {
        val filtered = value.filter { it.isDigit() || it == '.' }
        _uiState.update { it.copy(amountText = filtered, validationError = null) }
    }

    fun onPhoneChange(value: String) {
        val detected = NetworkPrefixDetector.detectNetwork(
            rawPhone = value,
            isPorted = _uiState.value.isPorted,
            manualOverride = _uiState.value.manualNetwork
        )
        _uiState.update {
            it.copy(phoneText = value, detectedNetwork = detected, validationError = null)
        }
        autocompleteJob?.cancel()
        val digitCount = value.count { it.isDigit() }
        if (digitCount < 2 && value.trim().length < 2) {
            _uiState.update { it.copy(phoneSuggestions = emptyList()) }
            return
        }
        autocompleteJob = viewModelScope.launch(Dispatchers.IO) {
            delay(80L)
            val matches = txRepo.searchCustomerNumberSuggestions(value, limit = 5)
            // Hide dropdown if user already typed the exact single full number
            val normalizedInput = NetworkPrefixDetector.normalizeGhanaPhone(value)
            val filtered = if (matches.size == 1 && matches.first().normalizedPhoneNumber == normalizedInput && value.length >= 10) {
                emptyList()
            } else {
                matches
            }
            _uiState.update { it.copy(phoneSuggestions = filtered) }
        }
    }

    fun onPhoneSuggestionSelected(suggestion: CustomerNumberEntity) {
        autocompleteJob?.cancel()
        val detected = NetworkPrefixDetector.detectNetwork(
            rawPhone = suggestion.phoneNumber,
            isPorted = _uiState.value.isPorted,
            manualOverride = _uiState.value.manualNetwork
        )
        _uiState.update {
            it.copy(
                phoneText = suggestion.phoneNumber,
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
                customUssdCode = ""
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
        onCustomerLedRequired: (localId: String) -> Unit
    ) {
        prepareTransaction(
            type = type,
            overrideNetwork = overrideNetwork,
            onReady = { localId, isCustomerLed ->
                if (isCustomerLed) {
                    onCustomerLedRequired(localId)
                } else {
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
        if (subscription.value.status == SubscriptionStatus.EXPIRED) {
            _uiState.update { it.copy(subscriptionGateVisible = true) }
            return
        }

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
            type == TransactionType.GIG -> network == NetworkType.MTN || state.phoneText.isNotBlank()
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
        viewModelScope.launch {
            val tx = _uiState.value.activeTransaction ?: return@launch
            val transitioned = txRepo.transitionStatus(
                localId = txLocalId,
                newStatus = TransactionStatus.PROCESSING,
                details = "Starting agent USSD workflow"
            )
            _uiState.update { it.copy(activeTransaction = transitioned) }

            val networkType = NetworkType.fromCode(tx.network)
            val txType = runCatching { TransactionType.valueOf(tx.transactionType) }.getOrNull()
            val simResolution = if (txType != null) {
                simManager.resolveForNetworkAndFeature(networkType, txType)
            } else {
                simManager.resolveForNetwork(networkType)
            }
            if (simResolution.status == SimResolutionStatus.REQUIRES_SELECTION ||
                simResolution.status == SimResolutionStatus.SIM_UNAVAILABLE_ATTENTION_REQUIRED ||
                simResolution.status == SimResolutionStatus.NO_ACTIVE_SIM ||
                simResolution.status == SimResolutionStatus.PERMISSION_REQUIRED
            ) {
                UssdDiagnosticLog.log("SIM", "Blocked USSD for ${networkType.code}: ${simResolution.message}")
                sessionManager.updateState(
                    UssdSessionState.ManualInterventionRequired(txLocalId, simResolution.message, null)
                )
                refreshDeviceStatus()
                return@launch
            }
            val resolvedSimId = simResolution.resolvedSim?.subscriptionId ?: _uiState.value.selectedSimId
            _uiState.update { it.copy(selectedSimLabel = simResolution.resolvedSim?.label) }

            val flowId = "${tx.network}_${tx.transactionType}"
            val profile = txRepo.getUssdFlowProfile(flowId)
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
                    operationalId = "",
                    isPortedNumber = false
                )
            } else {
                UssdInputHandler.TransactionVariables(
                    customerPhone = tx.recipientNumber,
                    recipientPhone = tx.recipientNumber,
                    amount = tx.amount.toString(),
                    bundleCode = tx.bundleId.orEmpty(),
                    bundlePrice = tx.amount.toString(),
                    network = networkType,
                    merchantCode = _uiState.value.merchantCode,
                    customUssdCode = _uiState.value.customUssdCode,
                    reference = if (flowId == "MTN_CASH_IN" || flowId == "MTN_CASH_OUT") {
                        ""
                    } else {
                        "AMA${tx.localId.take(6).uppercase()}"
                    },
                    operationalId = "",
                    isPortedNumber = _uiState.value.isPorted
                )
            }

            val startResult = flowController.startFlow(txLocalId, profile, vars)
            if (startResult.isFailure) return@launch

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

            val defaultSuccessTx = txRepo.transitionStatusSafely(
                localId = txLocalId,
                newStatus = TransactionStatus.SUCCESSFUL,
                confirmationSource = "DEFAULT_SUCCESS",
                details = "Marked successful by default; agent can mark failed/cancelled manually"
            )
            if (defaultSuccessTx != null) {
                _uiState.update { it.copy(activeTransaction = defaultSuccessTx) }
            }

            timeoutJob?.cancel()
            timeoutJob = viewModelScope.launch {
                delay(profile.timeoutSeconds * 1000L)
                flowController.onTimeout()
            }
        }
    }

    fun confirmUssdStep() = flowController.confirmPendingStep()

    fun cancelUssdSession() {
        timeoutJob?.cancel()
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
        sessionManager.resetToIdle()
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
    }
}

package com.easymomo.app.ui.terminal

import android.Manifest
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Chat
import androidx.compose.material.icons.automirrored.outlined.ExitToApp
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.ArrowDownward
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material.icons.outlined.Assessment
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.Dialpad
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material.icons.outlined.Payments
import androidx.compose.material.icons.outlined.People
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.material.icons.outlined.PointOfSale
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.SimCard
import androidx.compose.material.icons.outlined.Storefront
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.easymomo.app.R
import com.easymomo.app.data.local.CustomerNumberEntity
import com.easymomo.app.data.local.TransactionEntity
import com.easymomo.app.domain.model.NetworkType
import com.easymomo.app.domain.model.TransactionStatus
import com.easymomo.app.domain.model.TransactionType
import com.easymomo.app.ui.customussd.CustomUssdViewModel
import com.easymomo.app.ussd.ActiveSimInfo
import com.easymomo.app.ussd.CustomUssdRepeatCoordinator
import com.easymomo.app.ussd.PermissionCapabilityManager
import com.easymomo.app.ussd.SimSlotInfo
import com.easymomo.app.ussd.capture.CustomUssdOperationType
import com.easymomo.app.ussd.capture.SimpleCustomUssdFlow
import com.easymomo.app.ussd.model.PresetUssdFlow
import com.easymomo.app.ussd.model.PresetUssdFlows
import com.easymomo.app.ussd.model.UssdSessionState
import com.easymomo.app.ui.theme.EasyMoMoCard
import com.easymomo.app.ui.theme.AmberAccent
import com.easymomo.app.ui.theme.EmeraldPrimary
import com.easymomo.app.ui.theme.EmeraldPrimaryContainer
import com.easymomo.app.ui.theme.NetworkBadge
import com.easymomo.app.ui.theme.OnEmeraldPrimaryContainer
import com.easymomo.app.ui.theme.StatusError
import com.easymomo.app.ui.theme.StatusErrorBg
import com.easymomo.app.ui.theme.StatusInfo
import com.easymomo.app.ui.theme.StatusInfoBg
import com.easymomo.app.ui.theme.StatusWarning
import com.easymomo.app.ui.theme.StatusWarningBg
import com.easymomo.app.ui.theme.TabularAmountStyle
import com.easymomo.app.ui.theme.TabularPhoneStyle
import com.easymomo.app.ui.theme.TerminalActionButton
import com.easymomo.app.ui.theme.TerminalBackground
import com.easymomo.app.ui.theme.TerminalBorder
import com.easymomo.app.ui.theme.TerminalSurface
import com.easymomo.app.ui.theme.TerminalSurfaceVariant
import com.easymomo.app.ui.theme.TextMuted
import com.easymomo.app.ui.theme.TextPrimary
import com.easymomo.app.ui.theme.TextSecondary
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale

private val PORTED_NETWORK_OPTIONS = listOf(NetworkType.MTN, NetworkType.AT, NetworkType.TELECEL)
private val RoundedShape10 = RoundedCornerShape(10.dp)
private val RoundedShape8 = RoundedCornerShape(8.dp)
private val RoundedShape6 = RoundedCornerShape(6.dp)
private val MtnBorderStroke = BorderStroke(1.dp, Color(0xFFFFCC00))
private val TelecelBorderStroke = BorderStroke(1.dp, Color(0xFFE60000))

@Composable
fun DashboardScreen(
    terminalViewModel: TerminalViewModel,
    customUssdViewModel: CustomUssdViewModel,
    onOpenHelp: () -> Unit,
    onOpenHistory: () -> Unit,
    onOpenCustomers: () -> Unit,
    onOpenMerchant: () -> Unit,
    onOpenReports: () -> Unit,
    onOpenUserProfileInSettings: () -> Unit,
    onOpenUssdDevMode: () -> Unit,
    onOpenSimAndDiagnostics: () -> Unit,
    onOpenDataBundles: (NetworkType) -> Unit,
    onOpenCustomUssd: () -> Unit,
    onOpenBusWithdrawal: () -> Unit = {},
    onNavigateToReview: (localId: String, isCustomerLed: Boolean) -> Unit,
    onLogout: () -> Unit
) {
    val context = LocalContext.current
    val uiState by terminalViewModel.uiState.collectAsStateWithLifecycle()
    val customUssdState by customUssdViewModel.uiState.collectAsStateWithLifecycle()
    val trialNotice by terminalViewModel.trialNotice.collectAsStateWithLifecycle()
    var pendingNetworkSelectionType by remember { mutableStateOf<TransactionType?>(null) }
    var pendingPermissionAction by remember { mutableStateOf<(() -> Unit)?>(null) }
    var pendingAccessibilityAction by remember { mutableStateOf<(() -> Unit)?>(null) }
    var showCustomUssdPopup by remember { mutableStateOf(false) }
    val permissionHelper = remember(context) { PermissionCapabilityManager(context) }
    val uiPromptPrefs = remember(context) {
        context.getSharedPreferences("easymomo_ui_prompts", Context.MODE_PRIVATE)
    }
    var showSmsCapturePrompt by remember {
        mutableStateOf(
            !permissionHelper.isNotificationListenerEnabled() &&
                !uiPromptPrefs.getBoolean("dismissed_sms_capture_prompt", false)
        )
    }
    val amountFmt = remember { DecimalFormat("0.00", DecimalFormatSymbols(Locale.US)) }

    val runIfEntitled = remember(terminalViewModel) {
        { action: () -> Unit ->
            if (terminalViewModel.requireActiveAccessOrShowGate()) {
                action()
            }
        }
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                terminalViewModel.refreshDeviceStatus()
                terminalViewModel.refreshSubscriptionStatus()
                terminalViewModel.triggerAutoCustomerSyncIfNeeded()
                if (permissionHelper.isNotificationListenerEnabled()) {
                    showSmsCapturePrompt = false
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        terminalViewModel.refreshDeviceStatus()
        if (results[Manifest.permission.CALL_PHONE] == true) {
            val action = pendingPermissionAction
            pendingPermissionAction = null
            if (action != null) {
                if (terminalViewModel.uiState.value.isAccessibilityEnabled) {
                    action()
                } else {
                    pendingAccessibilityAction = action
                }
            }
        } else {
            pendingPermissionAction = null
        }
    }

    val runWithCallPermission = remember(permissionLauncher, terminalViewModel) {
        { action: () -> Unit ->
            val currentState = terminalViewModel.uiState.value
            if (!currentState.hasCallPermission) {
                pendingPermissionAction = action
                permissionLauncher.launch(
                    arrayOf(Manifest.permission.CALL_PHONE, Manifest.permission.READ_PHONE_STATE)
                )
            } else if (!currentState.isAccessibilityEnabled) {
                pendingAccessibilityAction = action
            } else {
                action()
            }
        }
    }

    val onShareApp = remember(context) {
        {
            val sendIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(
                    Intent.EXTRA_TEXT,
                    "EasyMoMo — Multi-Network Mobile Money Agent Terminal for Ghana (MTN, AT Money, Telecel)."
                )
            }
            context.startActivity(Intent.createChooser(sendIntent, "Share EasyMoMo"))
        }
    }

    val onJoinWhatsApp = remember(context) {
        {
            runCatching {
                context.startActivity(
                    Intent(
                        Intent.ACTION_VIEW,
                        Uri.parse("https://chat.whatsapp.com/GaPJviYYIPF976M7spXybb?s=cl&p=a&iam=2")
                    )
                )
            }
            Unit
        }
    }

    Scaffold(
        containerColor = TerminalBackground,
        topBar = {
            DashboardTopBar(
                darkModeEnabled = uiState.agentSettings.darkModeEnabled,
                onToggleDarkMode = terminalViewModel::setDarkModeEnabled,
                onJoinWhatsApp = onJoinWhatsApp,
                onOpenHelp = onOpenHelp,
                onOpenHistory = { runIfEntitled(onOpenHistory) },
                onOpenCustomers = { runIfEntitled(onOpenCustomers) },
                onOpenMerchant = { runIfEntitled(onOpenMerchant) },
                onOpenReports = { runIfEntitled(onOpenReports) },
                onShareApp = onShareApp,
                onOpenUssdDevMode = { runIfEntitled(onOpenUssdDevMode) },
                onOpenSimAndDiagnostics = onOpenSimAndDiagnostics,
                onLogout = onLogout
            )
        }
    ) { padding ->
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .navigationBarsPadding()
        ) {
            val minContentHeight = maxHeight
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = minContentHeight)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // 1. SIM Status Banner
                    SimStatusBanner(
                        availableSims = uiState.availableSims,
                        onClick = onOpenSimAndDiagnostics
                    )

                    // 2. Side-by-side Amount (GHS) & Phone Number with auto-network detection
                    AmountPhoneInputCard(
                        amountText = uiState.amountText,
                        phoneText = uiState.phoneText,
                        phoneSuggestions = uiState.phoneSuggestions,
                        detectedNetwork = uiState.detectedNetwork,
                        isPorted = uiState.isPorted,
                        manualNetwork = uiState.manualNetwork,
                        repeatCount = uiState.repeatCount,
                        onAmountChange = terminalViewModel::onAmountChange,
                        onPhoneChange = terminalViewModel::onPhoneChange,
                        onDismissPhoneSuggestions = terminalViewModel::dismissPhoneSuggestions,
                        onPhoneSuggestionSelected = terminalViewModel::onPhoneSuggestionSelected,
                        onOpenCustomers = { runIfEntitled(onOpenCustomers) },
                        onPortedChange = terminalViewModel::onPortedChange,
                        onRepeatCountChange = terminalViewModel::onRepeatCountChange,
                        onClearForm = terminalViewModel::clearForm,
                        onManualNetworkSelect = terminalViewModel::onManualNetworkSelect
                    )

                    // Validation error banner
                    uiState.validationError?.let { err ->
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedShape8)
                                .background(StatusErrorBg)
                                .padding(10.dp)
                        ) {
                            Text(text = err, color = StatusError, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                val launchOrSelectNetwork: (TransactionType) -> Unit = { txType ->
                    runIfEntitled {
                        val detected = uiState.detectedNetwork
                        val mode = uiState.agentSettings.defaultNetworkMode
                        val fixedNet = if (mode != "ASK") NetworkType.fromCode(mode) else NetworkType.UNKNOWN
                        val resolvedNetwork = when {
                            detected != NetworkType.UNKNOWN -> detected
                            fixedNet != NetworkType.UNKNOWN -> fixedNet
                            else -> NetworkType.UNKNOWN
                        }
                        if (resolvedNetwork != NetworkType.UNKNOWN) {
                            runWithCallPermission {
                                terminalViewModel.prepareAndRunInlineUssd(
                                    type = txType,
                                    overrideNetwork = resolvedNetwork,
                                    onCustomerLedRequired = { localId -> onNavigateToReview(localId, true) }
                                )
                            }
                        } else if (
                            (txType == TransactionType.CASH_IN ||
                                txType == TransactionType.CASH_OUT ||
                                txType == TransactionType.DATA ||
                                txType == TransactionType.AIRTIME) &&
                            uiState.phoneText.count { it.isDigit() } < 10
                        ) {
                            terminalViewModel.prepareAndRunInlineUssd(
                                type = txType,
                                overrideNetwork = NetworkType.UNKNOWN,
                                onCustomerLedRequired = { localId -> onNavigateToReview(localId, true) }
                            )
                        } else {
                            pendingNetworkSelectionType = txType
                        }
                    }
                }

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // 4. Action Grid
                    DashboardActionGrid(
                        onCashIn = { launchOrSelectNetwork(TransactionType.CASH_IN) },
                        onCashOut = {
                            runIfEntitled {
                                val targetNet = if (uiState.detectedNetwork != NetworkType.UNKNOWN) {
                                    uiState.detectedNetwork
                                } else {
                                    NetworkType.MTN
                                }
                                runWithCallPermission {
                                    terminalViewModel.prepareAndRunInlineUssd(
                                        type = TransactionType.CASH_OUT,
                                        overrideNetwork = targetNet,
                                        onCustomerLedRequired = { localId -> onNavigateToReview(localId, true) }
                                    )
                                }
                            }
                        },
                        onData = { launchOrSelectNetwork(TransactionType.DATA) },
                        onAirtime = { launchOrSelectNetwork(TransactionType.AIRTIME) },
                        onEvdBundle = {
                            runIfEntitled {
                                runWithCallPermission {
                                    terminalViewModel.prepareAndRunInlineUssd(
                                        type = TransactionType.EVD_BUNDLE,
                                        overrideNetwork = NetworkType.MTN,
                                        onCustomerLedRequired = { localId -> onNavigateToReview(localId, true) }
                                    )
                                }
                            }
                        },
                        onEvdAirtime = { launchOrSelectNetwork(TransactionType.EVD_AIRTIME) },
                        onOpenCustomUssd = {
                            runIfEntitled {
                                showCustomUssdPopup = true
                            }
                        },
                        onOpenMerchant = {
                            runIfEntitled {
                                runWithCallPermission {
                                    terminalViewModel.prepareAndRunInlineUssd(
                                        type = TransactionType.MERCHANT_PAY,
                                        overrideNetwork = NetworkType.MTN,
                                        onCustomerLedRequired = { localId -> onNavigateToReview(localId, true) }
                                    )
                                }
                            }
                        },
                        onGig = { launchOrSelectNetwork(TransactionType.GIG) },
                        onCommission = {
                            runIfEntitled {
                                runWithCallPermission {
                                    terminalViewModel.prepareAndRunInlineUssd(
                                        type = TransactionType.COMMISSION,
                                        overrideNetwork = NetworkType.MTN,
                                        onCustomerLedRequired = { localId -> onNavigateToReview(localId, true) }
                                    )
                                }
                            }
                        },
                        onBalance = {
                            runIfEntitled {
                                val detected = uiState.detectedNetwork
                                val mode = uiState.agentSettings.defaultNetworkMode
                                val fixedNet = if (mode != "ASK") NetworkType.fromCode(mode) else NetworkType.UNKNOWN
                                val assignedNets = uiState.simResolutions.values
                                    .filter { it.resolvedSim != null }
                                    .map { it.network }
                                val targetNet = when {
                                    detected != NetworkType.UNKNOWN -> detected
                                    fixedNet != NetworkType.UNKNOWN -> fixedNet
                                    assignedNets.size == 1 -> assignedNets.first()
                                    else -> NetworkType.UNKNOWN
                                }
                                if (targetNet != NetworkType.UNKNOWN) {
                                    runWithCallPermission {
                                        terminalViewModel.prepareAndRunInlineUssd(
                                            type = TransactionType.BALANCE,
                                            overrideNetwork = targetNet,
                                            onCustomerLedRequired = { localId -> onNavigateToReview(localId, true) }
                                        )
                                    }
                                } else {
                                    pendingNetworkSelectionType = TransactionType.BALANCE
                                }
                            }
                        }
                    )

                    // Compact Today's Summary strip (isolated flow collection)
                    TodaySummaryStrip(
                        terminalViewModel = terminalViewModel,
                        amountFmt = amountFmt,
                        hideTotals = uiState.agentSettings.hideTodayTotalsOnHome,
                        onOpenHistory = { runIfEntitled(onOpenHistory) }
                    )

                    // Footer
                    Text(
                        text = "EasyMoMo • Ghana Multi-Network Agent Terminal",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextMuted,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp, bottom = 2.dp)
                    )
                }
            }
        }
    }

    pendingNetworkSelectionType?.let { selectedType ->
        NetworkSelectionDialog(
            selectedType = selectedType,
            onDismiss = { pendingNetworkSelectionType = null },
            onSelectNetwork = { network ->
                pendingNetworkSelectionType = null
                if (uiState.phoneText.isNotBlank() && uiState.detectedNetwork != network) {
                    terminalViewModel.onPortedChange(true)
                    terminalViewModel.onManualNetworkSelect(network)
                }
                runWithCallPermission {
                    terminalViewModel.prepareAndRunInlineUssd(
                        type = selectedType,
                        overrideNetwork = network,
                        onCustomerLedRequired = { localId -> onNavigateToReview(localId, true) }
                    )
                }
            }
        )
    }

    if (uiState.subscriptionGateVisible) {
        SubscriptionGateDialog(
            onDismiss = terminalViewModel::dismissSubscriptionGate,
            onRenew = {
                terminalViewModel.dismissSubscriptionGate()
                onOpenUserProfileInSettings()
            }
        )
    }

    if (uiState.inlineUssdVisible) {
        InlineUssdDialog(
            terminalViewModel = terminalViewModel,
            activeTransaction = uiState.activeTransaction,
            selectedSimLabel = uiState.selectedSimLabel,
            isAccessibilityEnabled = uiState.isAccessibilityEnabled,
            amountFmt = amountFmt,
            onOpenSimAndDiagnostics = onOpenSimAndDiagnostics
        )
    }

    if (showCustomUssdPopup) {
        CustomUssdPickerDialog(
            flows = customUssdState.simpleFlows,
            activeSims = uiState.activeSims,
            initialRepeatCount = uiState.repeatCount,
            onDismiss = { showCustomUssdPopup = false },
            onRunPresetFlow = { preset, chosenSimId, repeatCount ->
                showCustomUssdPopup = false
                runWithCallPermission {
                    terminalViewModel.runPresetUssdFlow(
                        preset = preset,
                        overrideSimId = chosenSimId,
                        repeatCount = repeatCount
                    )
                }
            },
            onOpenBusWithdrawal = {
                showCustomUssdPopup = false
                runIfEntitled {
                    onOpenBusWithdrawal()
                }
            },
            onRunFlow = { flow, repeatCount ->
                showCustomUssdPopup = false
                runWithCallPermission {
                    terminalViewModel.runCustomUssdFlow(flow, repeatCount)
                }
            },
            onEditFlow = { flow ->
                showCustomUssdPopup = false
                runIfEntitled {
                    customUssdViewModel.startEditingSimpleFlow(flow)
                    onOpenCustomUssd()
                }
            },
            onCreateNew = {
                showCustomUssdPopup = false
                runIfEntitled {
                    customUssdViewModel.resetBuilder()
                    onOpenCustomUssd()
                }
            }
        )
    }

    pendingAccessibilityAction?.let { actionToRun ->
        AccessibilityPromptDialog(
            onDismiss = { pendingAccessibilityAction = null },
            onEnableAccessibility = {
                pendingAccessibilityAction = null
                context.startActivity(
                    Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS)
                )
            },
            onDialManually = {
                pendingAccessibilityAction = null
                actionToRun()
            }
        )
    }

    if (showSmsCapturePrompt) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = {
                uiPromptPrefs.edit().putBoolean("dismissed_sms_capture_prompt", true).apply()
                showSmsCapturePrompt = false
            },
            title = {
                Text(
                    text = "Enable Live MoMo SMS Capture?",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    text = "Turn on Notification Access so EasyMoMo can automatically confirm pending transactions and record incoming MTN MoMo, Telecel Cash, and AT Money SMS alerts.\n\n" +
                        "• Does not require READ_SMS.\n" +
                        "• Only transactional MoMo notifications are parsed locally; personal chats & OTPs are ignored.",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        uiPromptPrefs.edit().putBoolean("dismissed_sms_capture_prompt", true).apply()
                        showSmsCapturePrompt = false
                        context.startActivity(permissionHelper.createNotificationListenerSettingsIntent())
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = EmeraldPrimary)
                ) {
                    Text("Enable in Settings")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        uiPromptPrefs.edit().putBoolean("dismissed_sms_capture_prompt", true).apply()
                        showSmsCapturePrompt = false
                    }
                ) {
                    Text("Not Now")
                }
            }
        )
    }

    trialNotice?.let { notice ->
        androidx.compose.material3.AlertDialog(
            onDismissRequest = terminalViewModel::clearTrialNotice,
            title = {
                Text(
                    text = "Free Trial Already Used",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    text = notice,
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        terminalViewModel.clearTrialNotice()
                        onOpenUserProfileInSettings()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = EmeraldPrimary)
                ) {
                    Text("Subscribe Now")
                }
            },
            dismissButton = {
                TextButton(onClick = terminalViewModel::clearTrialNotice) {
                    Text("OK")
                }
            }
        )
    }
}


@Composable
private fun DashboardTopBar(
    darkModeEnabled: Boolean,
    onToggleDarkMode: (Boolean) -> Unit,
    onJoinWhatsApp: () -> Unit,
    onOpenHelp: () -> Unit,
    onOpenHistory: () -> Unit,
    onOpenCustomers: () -> Unit,
    onOpenMerchant: () -> Unit,
    onOpenReports: () -> Unit,
    onShareApp: () -> Unit,
    onOpenUssdDevMode: () -> Unit,
    onOpenSimAndDiagnostics: () -> Unit,
    onLogout: () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }
    val borderColor = TerminalBorder
    val borderStroke = remember(borderColor) { BorderStroke(1.dp, borderColor) }

    Surface(
        color = TerminalSurface,
        border = borderStroke,
        modifier = Modifier.statusBarsPadding()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Image(
                    painter = painterResource(id = R.drawable.easymomo_app_logo),
                    contentDescription = "EasyMoMo",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedShape10)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "EasyMoMo",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = EmeraldPrimary
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = onJoinWhatsApp,
                    modifier = Modifier.size(38.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(9.dp),
                        color = Color(0xFF25D366),
                        modifier = Modifier.size(30.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_whatsapp),
                                contentDescription = "Join WhatsApp Group",
                                tint = Color.White,
                                modifier = Modifier.size(19.dp)
                            )
                        }
                    }
                }
                IconButton(
                    onClick = { onToggleDarkMode(!darkModeEnabled) },
                    modifier = Modifier.size(38.dp)
                ) {
                    Icon(
                        imageVector = if (darkModeEnabled) Icons.Outlined.LightMode else Icons.Outlined.DarkMode,
                        contentDescription = if (darkModeEnabled) "Switch to light mode" else "Switch to dark mode",
                        tint = if (darkModeEnabled) EmeraldPrimary else TextPrimary,
                        modifier = Modifier.size(21.dp)
                    )
                }
                IconButton(
                    onClick = { showMenu = true },
                    modifier = Modifier.size(38.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Menu,
                        contentDescription = "Menu",
                        tint = TextPrimary,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
        }
    }

    if (showMenu) {
        DashboardMenuSheet(
            onDismiss = { showMenu = false },
            onOpenHistory = { showMenu = false; onOpenHistory() },
            onOpenCustomers = { showMenu = false; onOpenCustomers() },
            onOpenReports = { showMenu = false; onOpenReports() },
            onOpenHelp = { showMenu = false; onOpenHelp() },
            onOpenSimAndDiagnostics = { showMenu = false; onOpenSimAndDiagnostics() },
            onOpenUssdDevMode = { showMenu = false; onOpenUssdDevMode() },
            onShareApp = { showMenu = false; onShareApp() },
            onLogout = { showMenu = false; onLogout() }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DashboardMenuSheet(
    onDismiss: () -> Unit,
    onOpenHistory: () -> Unit,
    onOpenCustomers: () -> Unit,
    onOpenReports: () -> Unit,
    onOpenHelp: () -> Unit,
    onOpenSimAndDiagnostics: () -> Unit,
    onOpenUssdDevMode: () -> Unit,
    onShareApp: () -> Unit,
    onLogout: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val borderColor = TerminalBorder
    val sectionBorderStroke = remember(borderColor) { BorderStroke(1.dp, borderColor) }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = TerminalSurface,
        dragHandle = null
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Image(
                        painter = painterResource(id = R.drawable.easymomo_app_logo),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(32.dp)
                            .clip(RoundedShape10)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "EasyMoMo Menu",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                        Text(
                            text = "Agent tools, reports & settings",
                            style = MaterialTheme.typography.labelSmall,
                            color = TextSecondary
                        )
                    }
                }
                IconButton(onClick = onDismiss, modifier = Modifier.size(34.dp)) {
                    Icon(
                        imageVector = Icons.Outlined.Close,
                        contentDescription = "Close menu",
                        tint = TextSecondary
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))
            MenuSectionHeader("OPERATIONS")
            Spacer(modifier = Modifier.height(6.dp))
            Surface(
                shape = RoundedShape10,
                color = TerminalBackground,
                border = sectionBorderStroke
            ) {
                Column {
                    DashboardMenuItemRow(
                        icon = Icons.AutoMirrored.Outlined.ReceiptLong,
                        title = "Transaction History",
                        subtitle = "View recent receipts & statuses",
                        onClick = onOpenHistory
                    )
                    HorizontalDivider(color = TerminalBorder)
                    DashboardMenuItemRow(
                        icon = Icons.Outlined.People,
                        title = "Customers",
                        subtitle = "Saved customer numbers",
                        onClick = onOpenCustomers
                    )
                    HorizontalDivider(color = TerminalBorder)
                    DashboardMenuItemRow(
                        icon = Icons.Outlined.Assessment,
                        title = "Reports & Float",
                        subtitle = "Daily totals & CSV exports",
                        onClick = onOpenReports
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))
            MenuSectionHeader("SETUP & SETTINGS")
            Spacer(modifier = Modifier.height(6.dp))
            Surface(
                shape = RoundedShape10,
                color = TerminalBackground,
                border = sectionBorderStroke
            ) {
                Column {
                    DashboardMenuItemRow(
                        icon = Icons.AutoMirrored.Outlined.HelpOutline,
                        title = "SIM & Accessibility Setup",
                        subtitle = "Permissions & USSD assistant guide",
                        iconTint = StatusError,
                        onClick = onOpenHelp
                    )
                    HorizontalDivider(color = TerminalBorder)
                    DashboardMenuItemRow(
                        icon = Icons.Outlined.SimCard,
                        title = "Settings & User Profile",
                        subtitle = "Profile, subscription, SIM slots & diagnostics",
                        onClick = onOpenSimAndDiagnostics
                    )
                    HorizontalDivider(color = TerminalBorder)
                    DashboardMenuItemRow(
                        icon = Icons.Outlined.Tune,
                        title = "USSD Config & Prompt Capture",
                        subtitle = "Manage USSD flow configurations",
                        onClick = onOpenUssdDevMode
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))
            MenuSectionHeader("MORE")
            Spacer(modifier = Modifier.height(6.dp))
            Surface(
                shape = RoundedShape10,
                color = TerminalBackground,
                border = sectionBorderStroke
            ) {
                Column {
                    DashboardMenuItemRow(
                        icon = Icons.Outlined.Share,
                        title = "Share EasyMoMo",
                        subtitle = "Send app link to other agents",
                        onClick = onShareApp
                    )
                    HorizontalDivider(color = TerminalBorder)
                    DashboardMenuItemRow(
                        icon = Icons.AutoMirrored.Outlined.ExitToApp,
                        title = "Sign Out",
                        subtitle = "Lock session and switch agent",
                        iconTint = StatusError,
                        titleColor = StatusError,
                        showChevron = false,
                        onClick = onLogout
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

@Composable
private fun MenuSectionHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.Bold,
        color = TextSecondary,
        modifier = Modifier.padding(horizontal = 4.dp)
    )
}

@Composable
private fun DashboardMenuItemRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    iconTint: Color = EmeraldPrimary,
    titleColor: Color = TextPrimary,
    showChevron: Boolean = true,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(RoundedShape10)
                .background(iconTint.copy(alpha = 0.1f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.size(18.dp)
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = titleColor
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = TextSecondary
            )
        }
        if (showChevron) {
            Icon(
                imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                contentDescription = null,
                tint = TextSecondary,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

@Composable
private fun SimStatusBanner(
    availableSims: List<SimSlotInfo>,
    onClick: () -> Unit
) {
    val simCount = availableSims.size
    val simTint = if (simCount > 0) StatusInfo else StatusWarning
    val simBg = if (simCount > 0) StatusInfoBg else StatusWarningBg
    val simText = remember(availableSims) {
        if (availableSims.isNotEmpty()) {
            "Agent SIMs: ${availableSims.joinToString { it.carrierName.ifBlank { it.displayName } }}"
        } else {
            "Agent / EVD SIM required for USSD"
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedShape10)
            .background(simBg)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Icon(
            imageVector = Icons.Outlined.SimCard,
            contentDescription = null,
            tint = simTint,
            modifier = Modifier.size(18.dp)
        )
        Text(
            text = simText,
            style = MaterialTheme.typography.bodyMedium,
            color = simTint,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        Box(
            modifier = Modifier
                .clip(RoundedShape6)
                .background(simTint.copy(alpha = 0.14f))
                .padding(horizontal = 10.dp, vertical = 4.dp)
        ) {
            Text(
                text = "Setup",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = simTint
            )
        }
    }
}

@Composable
private fun AmountPhoneInputCard(
    amountText: String,
    phoneText: String,
    phoneSuggestions: List<CustomerNumberEntity>,
    detectedNetwork: NetworkType,
    isPorted: Boolean,
    manualNetwork: NetworkType?,
    repeatCount: Int,
    onAmountChange: (String) -> Unit,
    onPhoneChange: (String) -> Unit,
    onDismissPhoneSuggestions: () -> Unit,
    onPhoneSuggestionSelected: (CustomerNumberEntity) -> Unit,
    onOpenCustomers: () -> Unit,
    onPortedChange: (Boolean) -> Unit,
    onRepeatCountChange: (Int) -> Unit,
    onClearForm: () -> Unit,
    onManualNetworkSelect: (NetworkType) -> Unit
) {
    var showRepeatPicker by remember { mutableStateOf(false) }
    val textPrimary = TextPrimary
    val amountTextStyle = remember(textPrimary) {
        TabularAmountStyle.copy(color = textPrimary, textAlign = TextAlign.Center)
    }
    val phoneTextStyle = remember(textPrimary) {
        TabularPhoneStyle.copy(color = textPrimary, textAlign = TextAlign.Center)
    }
    val amountFocusRequester = remember { FocusRequester() }
    val phoneFocusRequester = remember { FocusRequester() }
    val amountKeyboardOptions = remember {
        KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done)
    }
    val phoneKeyboardOptions = remember {
        KeyboardOptions(keyboardType = KeyboardType.Phone, imeAction = ImeAction.Done)
    }
    val amountKeyboardActions = remember(phoneFocusRequester) {
        KeyboardActions(
            onDone = { phoneFocusRequester.requestFocus() },
            onNext = { phoneFocusRequester.requestFocus() }
        )
    }
    val phoneKeyboardActions = remember(amountFocusRequester) {
        KeyboardActions(
            onDone = { amountFocusRequester.requestFocus() },
            onNext = { amountFocusRequester.requestFocus() }
        )
    }
    val cursorBrush = remember { SolidColor(EmeraldPrimary) }

    EasyMoMoCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.Top
        ) {
            // Left: Amount in GHS (~38%)
            Column(
                modifier = Modifier.weight(0.38f),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Amount",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = EmeraldPrimary
                )
                Spacer(modifier = Modifier.height(6.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 68.dp)
                        .clip(RoundedShape10)
                        .background(TerminalSurfaceVariant)
                        .border(1.5.dp, TerminalBorder, RoundedShape10)
                        .padding(horizontal = 12.dp, vertical = 16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    BasicTextField(
                        value = amountText,
                        onValueChange = onAmountChange,
                        singleLine = true,
                        keyboardOptions = amountKeyboardOptions,
                        keyboardActions = amountKeyboardActions,
                        textStyle = amountTextStyle,
                        cursorBrush = cursorBrush,
                        decorationBox = { inner ->
                            if (amountText.isEmpty()) {
                                Text(
                                    text = "0.00",
                                    style = TabularAmountStyle,
                                    color = TextMuted,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                            inner()
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(amountFocusRequester)
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "In GHS",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSecondary
                )
            }

            // Right: Phone Number (~62%)
            Column(
                modifier = Modifier.weight(0.62f),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Phone Number",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = EmeraldPrimary
                )
                Spacer(modifier = Modifier.height(6.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 68.dp)
                        .clip(RoundedShape10)
                        .background(TerminalSurfaceVariant)
                        .border(1.5.dp, TerminalBorder, RoundedShape10)
                        .padding(horizontal = 12.dp, vertical = 16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    BasicTextField(
                        value = phoneText,
                        onValueChange = onPhoneChange,
                        singleLine = true,
                        keyboardOptions = phoneKeyboardOptions,
                        keyboardActions = phoneKeyboardActions,
                        textStyle = phoneTextStyle,
                        cursorBrush = cursorBrush,
                        decorationBox = { inner ->
                            if (phoneText.isEmpty()) {
                                Text(
                                    text = "0244123456",
                                    style = TabularPhoneStyle,
                                    color = TextMuted,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                            inner()
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(phoneFocusRequester)
                            .onFocusChanged { focusState ->
                                if (!focusState.isFocused) {
                                    onDismissPhoneSuggestions()
                                }
                            }
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))
                if (detectedNetwork != NetworkType.UNKNOWN) {
                    NetworkBadge(network = detectedNetwork)
                } else {
                    Text(
                        text = "Network will be detected if not ported.",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextSecondary,
                        textAlign = TextAlign.Center
                    )
                }
                TextButton(
                    onClick = onOpenCustomers,
                    modifier = Modifier.height(30.dp)
                ) {
                    Text(
                        text = "Select from recent customers",
                        style = MaterialTheme.typography.labelSmall,
                        color = StatusError
                    )
                }
            }
        }

        // Inline phone suggestions strip (rendered below inputs so it never covers the phone field)
        if (phoneSuggestions.isNotEmpty()) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Suggested Numbers",
                style = MaterialTheme.typography.labelSmall,
                color = TextSecondary
            )
            Spacer(modifier = Modifier.height(4.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                phoneSuggestions.forEach { suggestion ->
                    key(suggestion.phoneNumber) {
                        PhoneSuggestionItem(
                            suggestion = suggestion,
                            onClick = { onPhoneSuggestionSelected(suggestion) }
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))
        HorizontalDivider(color = TerminalBorder)
        Spacer(modifier = Modifier.height(4.dp))

        // Ported number checkbox + subtle Repeat pill + Clear
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.clickable { onPortedChange(!isPorted) }
            ) {
                Checkbox(
                    checked = isPorted,
                    onCheckedChange = onPortedChange,
                    colors = CheckboxDefaults.colors(checkedColor = EmeraldPrimary)
                )
                Text(
                    text = "Ported",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextPrimary
                )
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                SubtleRepeatPill(
                    repeatCount = repeatCount,
                    expanded = showRepeatPicker,
                    onClick = { showRepeatPicker = !showRepeatPicker }
                )
                TextButton(onClick = onClearForm) {
                    Text(
                        text = "Clear",
                        style = MaterialTheme.typography.labelLarge,
                        color = StatusError
                    )
                }
            }
        }

        if (showRepeatPicker) {
            Spacer(modifier = Modifier.height(6.dp))
            CompactRepeatSelectorStrip(
                repeatCount = repeatCount,
                onRepeatCountChange = onRepeatCountChange
            )
        }

        // Manual network selector when ported
        if (isPorted) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                PORTED_NETWORK_OPTIONS.forEach { net ->
                    FilterChip(
                        selected = manualNetwork == net,
                        onClick = { onManualNetworkSelect(net) },
                        label = { Text(net.shortName) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = EmeraldPrimaryContainer,
                            selectedLabelColor = OnEmeraldPrimaryContainer
                        )
                    )
                }
            }
        }
    }
}

@Composable
private fun PhoneSuggestionItem(
    suggestion: CustomerNumberEntity,
    onClick: () -> Unit
) {
    val meta = remember(suggestion.nickname, suggestion.usageCount) {
        buildString {
            suggestion.nickname?.takeIf { it.isNotBlank() }?.let {
                append(it)
                append(" • ")
            }
            append("Used ${suggestion.usageCount}x")
        }
    }
    val borderColor = TerminalBorder
    val borderStroke = remember(borderColor) { BorderStroke(1.dp, borderColor) }

    Surface(
        shape = RoundedShape8,
        color = TerminalSurfaceVariant,
        border = borderStroke,
        modifier = Modifier
            .clip(RoundedShape8)
            .clickable(onClick = onClick)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
        ) {
            Text(
                text = suggestion.phoneNumber,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = TextPrimary
            )
            Text(
                text = meta,
                style = MaterialTheme.typography.labelSmall,
                color = TextSecondary
            )
        }
    }
}

@Composable
private fun DashboardActionGrid(
    onCashIn: () -> Unit,
    onCashOut: () -> Unit,
    onData: () -> Unit,
    onAirtime: () -> Unit,
    onEvdBundle: () -> Unit,
    onEvdAirtime: () -> Unit,
    onOpenCustomUssd: () -> Unit,
    onOpenMerchant: () -> Unit,
    onGig: () -> Unit,
    onCommission: () -> Unit,
    onBalance: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        TerminalActionButton(
            label = "CASH IN",
            icon = Icons.Outlined.ArrowDownward,
            onClick = onCashIn,
            modifier = Modifier.weight(1f)
        )
        TerminalActionButton(
            label = "CASH OUT",
            icon = Icons.Outlined.ArrowUpward,
            onClick = onCashOut,
            modifier = Modifier.weight(1f)
        )
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        TerminalActionButton(
            label = "MoMo Data",
            icon = Icons.Outlined.Wifi,
            onClick = onData,
            modifier = Modifier.weight(1f)
        )
        TerminalActionButton(
            label = "MoMo Airtime",
            icon = Icons.Outlined.PhoneAndroid,
            onClick = onAirtime,
            modifier = Modifier.weight(1f)
        )
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        TerminalActionButton(
            label = "EVD BUNDLE",
            icon = Icons.Outlined.Wifi,
            onClick = onEvdBundle,
            modifier = Modifier.weight(1f)
        )
        TerminalActionButton(
            label = "EVD AIRTIME",
            icon = Icons.Outlined.PhoneAndroid,
            onClick = onEvdAirtime,
            modifier = Modifier.weight(1f)
        )
    }

    TerminalActionButton(
        label = "CUSTOM USSD",
        icon = Icons.Outlined.Dialpad,
        onClick = onOpenCustomUssd,
        modifier = Modifier.fillMaxWidth()
    )

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        TerminalActionButton(
            label = "PAY TO MERCHANT",
            icon = Icons.Outlined.Storefront,
            onClick = onOpenMerchant,
            modifier = Modifier.weight(1f)
        )
        TerminalActionButton(
            label = "GIG",
            icon = Icons.Outlined.SimCard,
            onClick = onGig,
            modifier = Modifier.weight(1f)
        )
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        TerminalActionButton(
            label = "COMMISSION",
            icon = Icons.Outlined.Payments,
            containerColor = AmberAccent,
            onClick = onCommission,
            modifier = Modifier.weight(1f)
        )
        TerminalActionButton(
            label = "BALANCE",
            icon = Icons.Outlined.AccountBalanceWallet,
            onClick = onBalance,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun TodaySummaryStrip(
    terminalViewModel: TerminalViewModel,
    amountFmt: DecimalFormat,
    hideTotals: Boolean,
    onOpenHistory: () -> Unit
) {
    val todaySummary by terminalViewModel.todaySummary.collectAsStateWithLifecycle()
    val summaryLabel = remember(todaySummary.totalAttempts, todaySummary.successfulCount, hideTotals) {
        if (hideTotals) {
            "Tap to view history"
        } else {
            val attemptWord = if (todaySummary.totalAttempts == 1) "attempt" else "attempts"
            "${todaySummary.totalAttempts} $attemptWord • ${todaySummary.successfulCount} successful"
        }
    }
    val formattedValue = remember(todaySummary.totalSuccessfulValueGhs, hideTotals) {
        if (hideTotals) "••••••" else "GHS ${amountFmt.format(todaySummary.totalSuccessfulValueGhs)}"
    }

    EasyMoMoCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpenHistory)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text("Today's Activity", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                Text(
                    text = summaryLabel,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = TextPrimary
                )
            }
            Text(
                text = formattedValue,
                style = MaterialTheme.typography.titleMedium,
                color = EmeraldPrimary
            )
        }
    }
}

@Composable
private fun NetworkSelectionDialog(
    selectedType: TransactionType,
    onDismiss: () -> Unit,
    onSelectNetwork: (NetworkType) -> Unit
) {
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Select Network",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "Choose network for ${selectedType.displayName}:",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSelectNetwork(NetworkType.MTN) },
                    shape = RoundedShape10,
                    color = Color(0xFFFFF8E1),
                    border = MtnBorderStroke
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(12.dp)
                                    .clip(RoundedShape6)
                                    .background(Color(0xFFFFCC00))
                            )
                            Text(
                                text = "MTN",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF1A1A1A)
                            )
                        }
                        Text(
                            text = "MTN MoMo • *171#",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFF5A4A00)
                        )
                    }
                }

                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSelectNetwork(NetworkType.TELECEL) },
                    shape = RoundedShape10,
                    color = Color(0xFFFFEBEE),
                    border = TelecelBorderStroke
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(12.dp)
                                    .clip(RoundedShape6)
                                    .background(Color(0xFFE60000))
                            )
                            Text(
                                text = "Telecel",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFB71C1C)
                            )
                        }
                        Text(
                            text = "Telecel Cash • *110#",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFF8E0000)
                        )
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
private fun SubscriptionGateDialog(
    onDismiss: () -> Unit,
    onRenew: () -> Unit
) {
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Active Subscription Required") },
        text = {
            Text(
                "An active subscription or free trial is required to use EasyMoMo features. Open your User Profile in Settings to check your status and subscribe or renew."
            )
        },
        confirmButton = {
            TextButton(onClick = onRenew) { Text("Open User Profile") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        }
    )
}

@Composable
internal fun InlineUssdDialog(
    terminalViewModel: TerminalViewModel,
    activeTransaction: TransactionEntity?,
    selectedSimLabel: String?,
    isAccessibilityEnabled: Boolean,
    amountFmt: DecimalFormat,
    onOpenSimAndDiagnostics: () -> Unit
) {
    val ussdState by terminalViewModel.ussdState.collectAsStateWithLifecycle()
    val repeatState by terminalViewModel.customUssdRepeatState.collectAsStateWithLifecycle()
    val batch = repeatState
    val canShowBatchNextControls = ussdState is UssdSessionState.Completed ||
        ussdState is UssdSessionState.ManualInterventionRequired ||
        ussdState is UssdSessionState.TimedOut
    val activeNextBatch = batch?.takeIf { it.isBatch && it.hasMoreRuns && canShowBatchNextControls }
    val txHeader = remember(activeTransaction, selectedSimLabel, isAccessibilityEnabled) {
        activeTransaction?.let { tx ->
            buildString {
                append("To: ${tx.recipientNumber}")
                if (tx.amount > 0) append(" • GHS ${amountFmt.format(tx.amount)}")
                selectedSimLabel?.let { append("\nSIM: $it") }
                append("\nAuto-Entry: ${if (isAccessibilityEnabled) "Active" else "Manual (Accessibility Off)"}")
            }
        }
    }

    androidx.compose.material3.AlertDialog(
        onDismissRequest = {
            if (batch?.countdownSeconds != null) {
                return@AlertDialog
            }
            when (ussdState) {
                is UssdSessionState.Completed,
                is UssdSessionState.Cancelled,
                is UssdSessionState.TimedOut,
                is UssdSessionState.ManualInterventionRequired,
                is UssdSessionState.ConfigurationRequired,
                is UssdSessionState.PausedForPinAuthorization,
                UssdSessionState.Idle -> terminalViewModel.dismissInlineUssd()
                else -> Unit
            }
        },
        title = {
            val txLabel = activeTransaction?.let { "${it.network} ${it.transactionType}" } ?: "USSD Transaction"
            Text(txLabel)
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (batch != null && batch.isBatch) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedShape8)
                            .background(EmeraldPrimaryContainer)
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(999.dp))
                                .background(EmeraldPrimary)
                                .padding(horizontal = 10.dp, vertical = 3.dp)
                        ) {
                            Text(
                                text = "Run ${batch.currentRun} of ${batch.totalRuns}",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }
                        val batchStatusText = when {
                            batch.countdownSeconds != null ->
                                "Starting Run ${batch.nextRunNumber} of ${batch.totalRuns} in ${batch.countdownSeconds}s..."
                            batch.pausedOnError ->
                                "Batch paused after error on Run ${batch.currentRun} of ${batch.totalRuns}."
                            batch.awaitingDialogClose && batch.hasMoreRuns ->
                                "Finish this USSD dialog — Run ${batch.nextRunNumber} of ${batch.totalRuns} will start automatically when it closes, or tap Run Next."
                            !batch.hasMoreRuns ->
                                "Final run (${batch.currentRun} of ${batch.totalRuns}) in progress."
                            else ->
                                "Running ${batch.currentRun} of ${batch.totalRuns}..."
                        }
                        Text(
                            text = batchStatusText,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Medium,
                            color = if (batch.pausedOnError) StatusError else OnEmeraldPrimaryContainer
                        )
                    }
                }
                txHeader?.let { header ->
                    Text(
                        text = header,
                        style = MaterialTheme.typography.labelSmall,
                        color = TextSecondary
                    )
                }
                when (val state = ussdState) {
                    is UssdSessionState.Initiating,
                    is UssdSessionState.AwaitingPrompt -> {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(22.dp),
                                color = EmeraldPrimary,
                                strokeWidth = 2.5.dp
                            )
                            Text(
                                text = (state as? UssdSessionState.AwaitingPrompt)?.statusMessage
                                    ?: "Dialing USSD menu...",
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                        Text(
                            text = "Complete the USSD prompt and enter your agent PIN at the final step.",
                            style = MaterialTheme.typography.labelSmall,
                            color = TextSecondary
                        )
                    }
                    is UssdSessionState.WaitingUserConfirmation -> {
                        Text(state.summaryText, style = MaterialTheme.typography.bodyMedium)
                    }
                    is UssdSessionState.PausedForOperationalId -> {
                        Text(
                            state.instruction,
                            style = MaterialTheme.typography.bodyMedium,
                            color = StatusWarning,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    is UssdSessionState.PausedForPinAuthorization -> {
                        Text(
                            "Please complete the PIN entry in the network's USSD window. Your PIN is not accessed or stored by EasyMoMo.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = EmeraldPrimary,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    is UssdSessionState.WaitingForCustomerApproval -> {
                        Text(
                            "Waiting for customer approval. The customer must approve the transaction on their phone using their own MoMo PIN. Do not hand over cash until the transaction is confirmed successful.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = StatusWarning,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = state.observedPrompt,
                            style = MaterialTheme.typography.labelSmall,
                            color = TextSecondary
                        )
                    }
                    is UssdSessionState.Completed -> {
                        Text(state.outcome.message, style = MaterialTheme.typography.bodyMedium)
                    }
                    is UssdSessionState.ManualInterventionRequired -> {
                        Text(state.reason, style = MaterialTheme.typography.bodyMedium, color = StatusError)
                    }
                    is UssdSessionState.ConfigurationRequired -> {
                        Text(state.message, style = MaterialTheme.typography.bodyMedium, color = StatusWarning)
                    }
                    is UssdSessionState.Cancelled -> {
                        Text("Cancelled: ${state.reason}", style = MaterialTheme.typography.bodyMedium)
                    }
                    is UssdSessionState.TimedOut -> {
                        Text("USSD session timed out. Check your SIM balance or history before retrying.", style = MaterialTheme.typography.bodyMedium, color = StatusWarning)
                    }
                    UssdSessionState.Idle -> {
                        Text("USSD session launched.", style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        },
        confirmButton = {
            when (ussdState) {
                is UssdSessionState.WaitingUserConfirmation -> {
                    TextButton(onClick = terminalViewModel::confirmUssdStep) {
                        Text("Confirm & Continue")
                    }
                }
                is UssdSessionState.WaitingForCustomerApproval -> {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        TextButton(
                            onClick = {
                                val txId = activeTransaction?.localId
                                if (txId != null) {
                                    terminalViewModel.markManualOutcome(
                                        txLocalId = txId,
                                        status = TransactionStatus.SUCCESSFUL,
                                        providerReference = null,
                                        notes = "Agent confirmed customer approval completed"
                                    )
                                }
                                terminalViewModel.dismissInlineUssd()
                            }
                        ) {
                            Text("Confirmed Success", color = EmeraldPrimary)
                        }
                        TextButton(
                            onClick = {
                                val txId = activeTransaction?.localId
                                if (txId != null) {
                                    terminalViewModel.markManualOutcome(
                                        txLocalId = txId,
                                        status = TransactionStatus.FAILED,
                                        providerReference = null,
                                        notes = "Customer declined or approval failed"
                                    )
                                }
                                terminalViewModel.dismissInlineUssd()
                            }
                        ) {
                            Text("Declined", color = StatusError)
                        }
                    }
                }
                is UssdSessionState.ManualInterventionRequired -> {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        TextButton(
                            onClick = {
                                terminalViewModel.dismissInlineUssd()
                                onOpenSimAndDiagnostics()
                            }
                        ) {
                            val manualState = ussdState as? UssdSessionState.ManualInterventionRequired
                            val label = if (manualState?.reason?.contains("Operational ID", ignoreCase = true) == true) {
                                "Open Settings"
                            } else {
                                "SIM Settings"
                            }
                            Text(label)
                        }
                        if (activeNextBatch != null) {
                            TextButton(onClick = terminalViewModel::runNextCustomUssdRepeatNow) {
                                Text(
                                    text = "Run Next (${activeNextBatch.nextRunNumber}/${activeNextBatch.totalRuns})",
                                    color = EmeraldPrimary,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        } else {
                            TextButton(onClick = terminalViewModel::dismissInlineUssd) {
                                Text("Done")
                            }
                        }
                    }
                }
                is UssdSessionState.Completed,
                is UssdSessionState.TimedOut -> {
                    if (activeNextBatch != null) {
                        TextButton(onClick = terminalViewModel::runNextCustomUssdRepeatNow) {
                            Text(
                                text = "Run Next (${activeNextBatch.nextRunNumber}/${activeNextBatch.totalRuns})",
                                color = EmeraldPrimary,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    } else {
                        TextButton(onClick = terminalViewModel::dismissInlineUssd) {
                            Text("Done")
                        }
                    }
                }
                is UssdSessionState.Cancelled,
                is UssdSessionState.ConfigurationRequired,
                is UssdSessionState.PausedForPinAuthorization,
                UssdSessionState.Idle -> {
                    TextButton(onClick = terminalViewModel::dismissInlineUssd) {
                        Text("Done")
                    }
                }
                else -> Unit
            }
        },
        dismissButton = {
            if (activeNextBatch != null) {
                TextButton(onClick = terminalViewModel::dismissInlineUssd) {
                    Text("Stop Batch", color = StatusError)
                }
            } else {
                when (ussdState) {
                    is UssdSessionState.WaitingForCustomerApproval -> {
                        TextButton(onClick = terminalViewModel::dismissInlineUssd) {
                            Text("Keep Pending")
                        }
                    }
                    is UssdSessionState.Initiating,
                    is UssdSessionState.AwaitingPrompt,
                    is UssdSessionState.PausedForOperationalId,
                    is UssdSessionState.WaitingUserConfirmation -> {
                        TextButton(
                            onClick = {
                                terminalViewModel.cancelUssdSession()
                                terminalViewModel.dismissInlineUssd()
                            }
                        ) {
                            Text("Cancel", color = StatusError)
                        }
                    }
                    else -> Unit
                }
            }
        }
    )
}

@Composable
private fun AccessibilityPromptDialog(
    onDismiss: () -> Unit,
    onEnableAccessibility: () -> Unit,
    onDialManually: () -> Unit
) {
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Enable USSD Auto-Entry?") },
        text = {
            Text(
                "To automatically enter the customer phone number, amount, and menu options into the USSD prompt, Android requires 'EasyMoMo USSD Assistant' to be enabled in Accessibility Settings.\n\n" +
                    "Your agent MoMo PIN is NEVER read, stored, or entered automatically—you will enter your PIN manually at the final step."
            )
        },
        confirmButton = {
            TextButton(onClick = onEnableAccessibility) {
                Text("Enable Accessibility")
            }
        },
        dismissButton = {
            TextButton(onClick = onDialManually) {
                Text("Dial Manually")
            }
        }
    )
}

@Composable
private fun SubtleRepeatPill(
    repeatCount: Int,
    expanded: Boolean,
    onClick: () -> Unit
) {
    val isActive = repeatCount > 1
    Surface(
        modifier = Modifier
            .clip(RoundedShape8)
            .clickable(onClick = onClick),
        shape = RoundedShape8,
        color = if (isActive || expanded) EmeraldPrimaryContainer else TerminalSurfaceVariant,
        border = BorderStroke(1.dp, if (isActive || expanded) EmeraldPrimary else TerminalBorder)
    ) {
        Text(
            text = if (isActive) "Repeat ${repeatCount}x" else "1x",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = if (isActive) FontWeight.Bold else FontWeight.Medium,
            color = if (isActive || expanded) OnEmeraldPrimaryContainer else TextSecondary,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
        )
    }
}

@Composable
private fun CompactRepeatSelectorStrip(
    repeatCount: Int,
    onRepeatCountChange: (Int) -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedShape8,
        color = TerminalBackground,
        border = BorderStroke(1.dp, TerminalBorder)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = "Runs:",
                style = MaterialTheme.typography.labelSmall,
                color = TextSecondary,
                fontWeight = FontWeight.Medium
            )
            listOf(1, 2, 3, 5).forEach { count ->
                val selected = repeatCount == count
                Surface(
                    modifier = Modifier
                        .clip(RoundedShape6)
                        .clickable { onRepeatCountChange(count) },
                    shape = RoundedShape6,
                    color = if (selected) EmeraldPrimaryContainer else TerminalSurface,
                    border = BorderStroke(1.dp, if (selected) EmeraldPrimary else TerminalBorder)
                ) {
                    Text(
                        text = "${count}x",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                        color = if (selected) OnEmeraldPrimaryContainer else TextSecondary,
                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp)
                    )
                }
            }
            val canDec = repeatCount > CustomUssdRepeatCoordinator.MIN_REPEAT_COUNT
            Surface(
                modifier = Modifier
                    .clip(RoundedShape6)
                    .clickable(enabled = canDec) {
                        onRepeatCountChange(repeatCount - 1)
                    },
                shape = RoundedShape6,
                color = TerminalSurface,
                border = BorderStroke(1.dp, TerminalBorder)
            ) {
                Text(
                    text = "−",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (canDec) TextPrimary else TextMuted,
                    modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp)
                )
            }
            Text(
                text = "${repeatCount}x",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = EmeraldPrimary,
                modifier = Modifier.padding(horizontal = 2.dp)
            )
            val canInc = repeatCount < CustomUssdRepeatCoordinator.MAX_REPEAT_COUNT
            Surface(
                modifier = Modifier
                    .clip(RoundedShape6)
                    .clickable(enabled = canInc) {
                        onRepeatCountChange(repeatCount + 1)
                    },
                shape = RoundedShape6,
                color = TerminalSurface,
                border = BorderStroke(1.dp, TerminalBorder)
            ) {
                Text(
                    text = "+",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (canInc) TextPrimary else TextMuted,
                    modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp)
                )
            }
        }
    }
}

@Composable
private fun CustomUssdPickerDialog(
    flows: List<SimpleCustomUssdFlow>,
    activeSims: List<ActiveSimInfo>,
    initialRepeatCount: Int,
    onDismiss: () -> Unit,
    onRunPresetFlow: (PresetUssdFlow, Int?, Int) -> Unit,
    onOpenBusWithdrawal: () -> Unit,
    onRunFlow: (SimpleCustomUssdFlow, Int) -> Unit,
    onEditFlow: (SimpleCustomUssdFlow) -> Unit,
    onCreateNew: () -> Unit
) {
    var repeatCount by remember { mutableStateOf(CustomUssdRepeatCoordinator.clampRepeatCount(initialRepeatCount)) }
    var showRepeatStrip by remember { mutableStateOf(initialRepeatCount > 1) }
    var chosenSimId by remember { mutableStateOf<Int?>(null) }
    var activeGroupTitle by remember { mutableStateOf<String?>(null) }
    var activeGroupFlows by remember { mutableStateOf<List<PresetUssdFlow>>(emptyList()) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 24.dp)
                .heightIn(max = 640.dp),
            shape = RoundedCornerShape(16.dp),
            color = TerminalSurface,
            border = BorderStroke(1.dp, TerminalBorder)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Custom USSD & Quick Flows",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                        Text(
                            text = "Flows that don't need a phone number run on their own.",
                            style = MaterialTheme.typography.labelSmall,
                            color = TextSecondary
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(
                            imageVector = Icons.Outlined.Close,
                            contentDescription = "Close",
                            tint = TextSecondary
                        )
                    }
                }

                // SIM selector + subtle Repeat pill for Quick Flows & Custom USSD
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = if (activeSims.isNotEmpty()) "Run with SIM:" else "Options:",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = TextSecondary
                        )
                        SubtleRepeatPill(
                            repeatCount = repeatCount,
                            expanded = showRepeatStrip,
                            onClick = { showRepeatStrip = !showRepeatStrip }
                        )
                    }
                    if (showRepeatStrip) {
                        CompactRepeatSelectorStrip(
                            repeatCount = repeatCount,
                            onRepeatCountChange = { repeatCount = CustomUssdRepeatCoordinator.clampRepeatCount(it) }
                        )
                    }
                    if (activeSims.isNotEmpty()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            FilterChip(
                                selected = chosenSimId == null,
                                onClick = { chosenSimId = null },
                                label = { Text("Auto (Per-Feature Settings)") },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = EmeraldPrimaryContainer,
                                    selectedLabelColor = OnEmeraldPrimaryContainer
                                )
                            )
                            activeSims.forEach { sim ->
                                FilterChip(
                                    selected = chosenSimId == sim.subscriptionId,
                                    onClick = { chosenSimId = sim.subscriptionId },
                                    label = { Text(sim.label) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = EmeraldPrimaryContainer,
                                        selectedLabelColor = OnEmeraldPrimaryContainer
                                    )
                                )
                            }
                        }
                    }
                }

                // Default Quick Flows (responsive 2-column on standard/large screens, 1-column on compact screens)
                BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                    val useSingleColumn = maxWidth < 330.dp
                    val buttonPairs = listOf(
                        Triple("TELECEL AGENT", Icons.Outlined.SimCard, EmeraldPrimary) to {
                            activeGroupTitle = "Telecel Agent"
                            activeGroupFlows = PresetUssdFlows.TELECEL_AGENT_FLOWS
                        },
                        Triple("ONE4ALL", Icons.Outlined.PhoneAndroid, EmeraldPrimary) to {
                            activeGroupTitle = "One4All"
                            activeGroupFlows = PresetUssdFlows.ONE4ALL_FLOWS
                        },
                        Triple("EVD BALANCE", Icons.Outlined.AccountBalanceWallet, EmeraldPrimary) to {
                            onRunPresetFlow(PresetUssdFlows.EVD_BALANCE, chosenSimId, repeatCount)
                        },
                        Triple("EVD COMMISSION", Icons.Outlined.Payments, AmberAccent) to {
                            onRunPresetFlow(PresetUssdFlows.EVD_COMMISSION, chosenSimId, repeatCount)
                        },
                        Triple("MOMO STATEMENT", Icons.AutoMirrored.Outlined.ReceiptLong, EmeraldPrimary) to {
                            onRunPresetFlow(PresetUssdFlows.MOMO_STATEMENT, chosenSimId, repeatCount)
                        },
                        Triple("BUS WITHDRAW", Icons.Outlined.AccountBalanceWallet, EmeraldPrimary) to {
                            onOpenBusWithdrawal()
                        }
                    )

                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        if (useSingleColumn) {
                            buttonPairs.forEach { (meta, action) ->
                                TerminalActionButton(
                                    label = meta.first,
                                    icon = meta.second,
                                    containerColor = meta.third,
                                    onClick = action,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        } else {
                            buttonPairs.chunked(2).forEach { rowItems ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    rowItems.forEach { (meta, action) ->
                                        TerminalActionButton(
                                            label = meta.first,
                                            icon = meta.second,
                                            containerColor = meta.third,
                                            onClick = action,
                                            modifier = Modifier.weight(1f)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                TerminalActionButton(
                    label = "CREATE CUSTOM USSD",
                    icon = Icons.Outlined.Dialpad,
                    onClick = onCreateNew,
                    modifier = Modifier.fillMaxWidth()
                )

                if (flows.isNotEmpty()) {
                    HorizontalDivider(color = TerminalBorder)
                    Text(
                        text = "Saved Custom USSDs (${flows.size})",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )

                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        flows.forEach { flow ->
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onRunFlow(flow, repeatCount) },
                                shape = RoundedShape8,
                                color = TerminalBackground,
                                border = BorderStroke(1.dp, TerminalBorder)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(
                                        modifier = Modifier.weight(1f),
                                        verticalArrangement = Arrangement.spacedBy(2.dp)
                                    ) {
                                        Text(
                                            text = flow.name,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = TextPrimary,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = "${flow.operationType.label} • SIM ${flow.simSlotIndex + 1}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = EmeraldPrimary,
                                            fontWeight = FontWeight.Medium
                                        )
                                        Text(
                                            text = flow.formulaPreview,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = TextSecondary,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    IconButton(onClick = { onEditFlow(flow) }) {
                                        Icon(
                                            imageVector = Icons.Outlined.Edit,
                                            contentDescription = "Edit ${flow.name}",
                                            tint = TextSecondary,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    activeGroupTitle?.let { groupTitle ->
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { activeGroupTitle = null },
            title = {
                Text(
                    text = groupTitle,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    activeGroupFlows.forEach { preset ->
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedShape8)
                                .clickable {
                                    activeGroupTitle = null
                                    onRunPresetFlow(preset, chosenSimId, repeatCount)
                                },
                            shape = RoundedShape8,
                            color = TerminalBackground,
                            border = BorderStroke(1.dp, TerminalBorder)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 14.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    text = preset.title,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = EmeraldPrimary
                                )
                                Text(
                                    text = preset.subtitle,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextSecondary
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { activeGroupTitle = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}

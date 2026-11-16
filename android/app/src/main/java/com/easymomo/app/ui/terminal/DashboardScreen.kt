package com.easymomo.app.ui.terminal

import android.Manifest
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ExitToApp
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.ArrowDownward
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material.icons.outlined.Assessment
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.CreditCard
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.Dialpad
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
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.easymomo.app.data.local.CustomerNumberEntity
import com.easymomo.app.data.local.TransactionEntity
import com.easymomo.app.domain.model.NetworkType
import com.easymomo.app.domain.model.TransactionStatus
import com.easymomo.app.domain.model.TransactionType
import com.easymomo.app.ussd.SimSlotInfo
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
import com.easymomo.app.ui.theme.TrialSubscriptionBanner
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
    onOpenHelp: () -> Unit,
    onOpenHistory: () -> Unit,
    onOpenCustomers: () -> Unit,
    onOpenMerchant: () -> Unit,
    onOpenReports: () -> Unit,
    onOpenSubscription: () -> Unit,
    onOpenUssdDevMode: () -> Unit,
    onOpenSimAndDiagnostics: () -> Unit,
    onOpenDataBundles: (NetworkType) -> Unit,
    onOpenCustomUssd: () -> Unit,
    onNavigateToReview: (localId: String, isCustomerLed: Boolean) -> Unit,
    onLogout: () -> Unit
) {
    val context = LocalContext.current
    val uiState by terminalViewModel.uiState.collectAsStateWithLifecycle()
    var pendingNetworkSelectionType by remember { mutableStateOf<TransactionType?>(null) }
    var pendingPermissionAction by remember { mutableStateOf<(() -> Unit)?>(null) }
    var pendingAccessibilityAction by remember { mutableStateOf<(() -> Unit)?>(null) }
    val amountFmt = remember { DecimalFormat("0.00", DecimalFormatSymbols(Locale.US)) }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                terminalViewModel.refreshDeviceStatus()
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

    Scaffold(
        containerColor = TerminalBackground,
        topBar = {
            DashboardTopBar(
                darkModeEnabled = uiState.agentSettings.darkModeEnabled,
                onToggleDarkMode = terminalViewModel::setDarkModeEnabled,
                onOpenHelp = onOpenHelp,
                onOpenHistory = onOpenHistory,
                onOpenCustomers = onOpenCustomers,
                onOpenMerchant = onOpenMerchant,
                onOpenReports = onOpenReports,
                onShareApp = onShareApp,
                onOpenSubscription = onOpenSubscription,
                onOpenUssdDevMode = onOpenUssdDevMode,
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

                    // 2. 7-Day Trial / Subscription Banner (isolated flow collection)
                    TrialBannerSection(
                        terminalViewModel = terminalViewModel,
                        onOpenSubscription = onOpenSubscription
                    )

                    // 3. Side-by-side Amount (GHS) & Phone Number with auto-network detection
                    AmountPhoneInputCard(
                        amountText = uiState.amountText,
                        phoneText = uiState.phoneText,
                        phoneSuggestions = uiState.phoneSuggestions,
                        detectedNetwork = uiState.detectedNetwork,
                        isPorted = uiState.isPorted,
                        manualNetwork = uiState.manualNetwork,
                        onAmountChange = terminalViewModel::onAmountChange,
                        onPhoneChange = terminalViewModel::onPhoneChange,
                        onDismissPhoneSuggestions = terminalViewModel::dismissPhoneSuggestions,
                        onPhoneSuggestionSelected = terminalViewModel::onPhoneSuggestionSelected,
                        onOpenCustomers = onOpenCustomers,
                        onPortedChange = terminalViewModel::onPortedChange,
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
                    val mode = uiState.agentSettings.defaultNetworkMode
                    val fixedNet = if (mode != "ASK") NetworkType.fromCode(mode) else NetworkType.UNKNOWN
                    if (fixedNet != NetworkType.UNKNOWN) {
                        runWithCallPermission {
                            terminalViewModel.prepareAndRunInlineUssd(
                                type = txType,
                                overrideNetwork = fixedNet,
                                onCustomerLedRequired = { localId -> onNavigateToReview(localId, true) }
                            )
                        }
                    } else {
                        pendingNetworkSelectionType = txType
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
                            runWithCallPermission {
                                terminalViewModel.prepareAndRunInlineUssd(
                                    type = TransactionType.CASH_OUT,
                                    overrideNetwork = NetworkType.MTN,
                                    onCustomerLedRequired = { localId -> onNavigateToReview(localId, true) }
                                )
                            }
                        },
                        onData = { launchOrSelectNetwork(TransactionType.DATA) },
                        onAirtime = { launchOrSelectNetwork(TransactionType.AIRTIME) },
                        onEvdBundle = {
                            runWithCallPermission {
                                terminalViewModel.prepareAndRunInlineUssd(
                                    type = TransactionType.EVD_BUNDLE,
                                    overrideNetwork = NetworkType.MTN,
                                    onCustomerLedRequired = { localId -> onNavigateToReview(localId, true) }
                                )
                            }
                        },
                        onEvdAirtime = {
                            runWithCallPermission {
                                terminalViewModel.prepareAndRunInlineUssd(
                                    type = TransactionType.EVD_AIRTIME,
                                    overrideNetwork = NetworkType.MTN,
                                    onCustomerLedRequired = { localId -> onNavigateToReview(localId, true) }
                                )
                            }
                        },
                        onOpenCustomUssd = onOpenCustomUssd,
                        onOpenMerchant = {
                            runWithCallPermission {
                                terminalViewModel.prepareAndRunInlineUssd(
                                    type = TransactionType.MERCHANT_PAY,
                                    overrideNetwork = NetworkType.MTN,
                                    onCustomerLedRequired = { localId -> onNavigateToReview(localId, true) }
                                )
                            }
                        },
                        onGig = { launchOrSelectNetwork(TransactionType.GIG) },
                        onCommission = {
                            runWithCallPermission {
                                terminalViewModel.prepareAndRunInlineUssd(
                                    type = TransactionType.COMMISSION,
                                    overrideNetwork = NetworkType.MTN,
                                    onCustomerLedRequired = { localId -> onNavigateToReview(localId, true) }
                                )
                            }
                        },
                        onBalance = {
                            val mode = uiState.agentSettings.defaultNetworkMode
                            val fixedNet = if (mode != "ASK") NetworkType.fromCode(mode) else NetworkType.UNKNOWN
                            val net = if (fixedNet != NetworkType.UNKNOWN) fixedNet else NetworkType.MTN
                            runWithCallPermission {
                                terminalViewModel.prepareAndRunInlineUssd(
                                    type = TransactionType.BALANCE,
                                    overrideNetwork = net,
                                    onCustomerLedRequired = { localId -> onNavigateToReview(localId, true) }
                                )
                            }
                        }
                    )

                    // Compact Today's Summary strip (isolated flow collection)
                    TodaySummaryStrip(
                        terminalViewModel = terminalViewModel,
                        amountFmt = amountFmt,
                        hideTotals = uiState.agentSettings.hideTodayTotalsOnHome,
                        onOpenHistory = onOpenHistory
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
                onOpenSubscription()
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
}


@Composable
private fun DashboardTopBar(
    darkModeEnabled: Boolean,
    onToggleDarkMode: (Boolean) -> Unit,
    onOpenHelp: () -> Unit,
    onOpenHistory: () -> Unit,
    onOpenCustomers: () -> Unit,
    onOpenMerchant: () -> Unit,
    onOpenReports: () -> Unit,
    onShareApp: () -> Unit,
    onOpenSubscription: () -> Unit,
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
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedShape10)
                        .background(EmeraldPrimary),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Outlined.PointOfSale,
                        contentDescription = "EasyMoMo",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }
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
            onOpenSubscription = { showMenu = false; onOpenSubscription() },
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
    onOpenSubscription: () -> Unit,
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
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(RoundedShape10)
                            .background(EmeraldPrimary),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.PointOfSale,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                    }
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
                        title = "SIM Management & Diagnostics",
                        subtitle = "Assign SIM slots & view USSD logs",
                        onClick = onOpenSimAndDiagnostics
                    )
                    HorizontalDivider(color = TerminalBorder)
                    DashboardMenuItemRow(
                        icon = Icons.Outlined.Tune,
                        title = "USSD Config & Prompt Capture",
                        subtitle = "Manage USSD flow configurations",
                        onClick = onOpenUssdDevMode
                    )
                    HorizontalDivider(color = TerminalBorder)
                    DashboardMenuItemRow(
                        icon = Icons.Outlined.CreditCard,
                        title = "Subscription & Paystack",
                        subtitle = "Manage plan & billing",
                        onClick = onOpenSubscription
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
private fun TrialBannerSection(
    terminalViewModel: TerminalViewModel,
    onOpenSubscription: () -> Unit
) {
    val subscription by terminalViewModel.subscription.collectAsStateWithLifecycle()
    TrialSubscriptionBanner(
        subscription = subscription,
        onClick = onOpenSubscription
    )
}

@Composable
private fun AmountPhoneInputCard(
    amountText: String,
    phoneText: String,
    phoneSuggestions: List<CustomerNumberEntity>,
    detectedNetwork: NetworkType,
    isPorted: Boolean,
    manualNetwork: NetworkType?,
    onAmountChange: (String) -> Unit,
    onPhoneChange: (String) -> Unit,
    onDismissPhoneSuggestions: () -> Unit,
    onPhoneSuggestionSelected: (CustomerNumberEntity) -> Unit,
    onOpenCustomers: () -> Unit,
    onPortedChange: (Boolean) -> Unit,
    onClearForm: () -> Unit,
    onManualNetworkSelect: (NetworkType) -> Unit
) {
    val textPrimary = TextPrimary
    val amountTextStyle = remember(textPrimary) {
        TabularAmountStyle.copy(color = textPrimary, textAlign = TextAlign.Center)
    }
    val phoneTextStyle = remember(textPrimary) {
        TabularPhoneStyle.copy(color = textPrimary, textAlign = TextAlign.Center)
    }
    val amountKeyboardOptions = remember { KeyboardOptions(keyboardType = KeyboardType.Decimal) }
    val phoneKeyboardOptions = remember { KeyboardOptions(keyboardType = KeyboardType.Phone) }
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
                        .clip(RoundedShape10)
                        .background(TerminalSurfaceVariant)
                        .border(1.dp, TerminalBorder, RoundedShape10)
                        .padding(horizontal = 8.dp, vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    BasicTextField(
                        value = amountText,
                        onValueChange = onAmountChange,
                        singleLine = true,
                        keyboardOptions = amountKeyboardOptions,
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
                        modifier = Modifier.fillMaxWidth()
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
                        .clip(RoundedShape10)
                        .background(TerminalSurfaceVariant)
                        .border(1.dp, TerminalBorder, RoundedShape10)
                        .padding(horizontal = 10.dp, vertical = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    BasicTextField(
                        value = phoneText,
                        onValueChange = onPhoneChange,
                        singleLine = true,
                        keyboardOptions = phoneKeyboardOptions,
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

        // Ported number checkbox + Clear Form Fields
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
                    text = "Number is ported",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextPrimary
                )
            }
            TextButton(onClick = onClearForm) {
                Text(
                    text = "Clear Form Fields",
                    style = MaterialTheme.typography.labelLarge,
                    color = StatusError
                )
            }
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
            label = "DATA",
            icon = Icons.Outlined.Wifi,
            onClick = onData,
            modifier = Modifier.weight(1f)
        )
        TerminalActionButton(
            label = "AIRTIME",
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
        title = { Text("Subscription Required") },
        text = {
            Text(
                "Your 7-day free trial or subscription has expired. Your transaction history and reports remain accessible offline, and you can renew anytime via Paystack."
            )
        },
        confirmButton = {
            TextButton(onClick = onRenew) { Text("Renew Subscription") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Later") }
        }
    )
}

@Composable
private fun InlineUssdDialog(
    terminalViewModel: TerminalViewModel,
    activeTransaction: TransactionEntity?,
    selectedSimLabel: String?,
    isAccessibilityEnabled: Boolean,
    amountFmt: DecimalFormat,
    onOpenSimAndDiagnostics: () -> Unit
) {
    val ussdState by terminalViewModel.ussdState.collectAsStateWithLifecycle()
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
                        TextButton(onClick = terminalViewModel::dismissInlineUssd) {
                            Text("Done")
                        }
                    }
                }
                is UssdSessionState.Completed,
                is UssdSessionState.Cancelled,
                is UssdSessionState.TimedOut,
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

package com.easymomo.app.ui.settings

import android.os.Build
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.AccessibilityNew
import androidx.compose.material.icons.outlined.BugReport
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.SimCard
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.easymomo.app.data.local.SavedSimSelection
import com.easymomo.app.data.local.SimPreferencesStore
import com.easymomo.app.data.repository.CustomerCloudSyncState
import com.easymomo.app.domain.model.NetworkType
import com.easymomo.app.domain.model.TransactionType
import com.easymomo.app.ui.auth.AuthViewModel
import com.easymomo.app.ui.terminal.TerminalViewModel
import com.easymomo.app.ui.theme.EasyMoMoCard
import com.easymomo.app.ui.theme.EmeraldPrimary
import com.easymomo.app.ui.theme.EmeraldPrimaryContainer
import com.easymomo.app.ui.theme.OnEmeraldPrimaryContainer
import com.easymomo.app.ui.theme.StatusError
import com.easymomo.app.ui.theme.StatusErrorBg
import com.easymomo.app.ui.theme.StatusSuccess
import com.easymomo.app.ui.theme.StatusSuccessBg
import com.easymomo.app.ui.theme.StatusWarning
import com.easymomo.app.ui.theme.StatusWarningBg
import com.easymomo.app.ui.theme.TerminalBackground
import com.easymomo.app.ui.theme.TerminalBorder
import com.easymomo.app.ui.theme.TerminalSurface
import com.easymomo.app.ui.theme.TextMuted
import com.easymomo.app.ui.theme.TextPrimary
import com.easymomo.app.ui.theme.TextSecondary
import com.easymomo.app.ussd.ActiveSimInfo
import com.easymomo.app.ussd.CapabilityStatus
import com.easymomo.app.ussd.SimResolutionStatus

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SimAndDiagnosticsScreen(
    authViewModel: AuthViewModel,
    terminalViewModel: TerminalViewModel,
    initialExpandBilling: Boolean = false,
    onRequestPermissions: () -> Unit,
    onOpenAccessibilitySettings: () -> Unit,
    onOpenAppSettings: () -> Unit,
    onBack: () -> Unit
) {
    val uiState by terminalViewModel.uiState.collectAsState()
    val syncStatus by terminalViewModel.customerSyncStatus.collectAsState()
    val flows by terminalViewModel.allUssdFlows.collectAsState()
    val simState by terminalViewModel.ussdState.collectAsState()
    val lifecycleOwner = LocalLifecycleOwner.current

    // Refresh permissions & SIM status automatically when returning from Android Settings
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                terminalViewModel.refreshDeviceStatus(force = true)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    var isSimDraftDirty by remember { mutableStateOf(false) }
    var draftNetworkSims by remember { mutableStateOf<Map<NetworkType, ActiveSimInfo?>>(emptyMap()) }
    var draftFeatureSims by remember {
        mutableStateOf<Map<Pair<NetworkType, TransactionType>, ActiveSimInfo?>>(emptyMap())
    }

    LaunchedEffect(
        uiState.activeSims,
        uiState.simResolutions,
        uiState.featureSimSelections,
        isSimDraftDirty
    ) {
        if (!isSimDraftDirty) {
            draftNetworkSims = SimPreferencesStore.SUPPORTED_NETWORKS.associateWith { net ->
                uiState.simResolutions[net]?.resolvedSim
            }
            draftFeatureSims = buildMap {
                SimPreferencesStore.SUPPORTED_NETWORKS.forEach { net ->
                    SimPreferencesStore.featuresForNetwork(net).forEach { feature ->
                        val saved = uiState.featureSimSelections[net to feature]
                        val matched = if (saved == null) {
                            null
                        } else {
                            uiState.activeSims.firstOrNull { sim ->
                                sim.subscriptionId == saved.subscriptionId ||
                                    (saved.iccIdHash.isNotBlank() && sim.iccIdHash == saved.iccIdHash)
                            }
                        }
                        put(net to feature, matched)
                    }
                }
            }
        }
    }

    Scaffold(
        containerColor = TerminalBackground,
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = {
                        terminalViewModel.refreshDeviceStatus(force = true)
                        authViewModel.loadSubscriptionDetails()
                    }) {
                        Icon(Icons.Outlined.Refresh, contentDescription = "Refresh")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = TerminalSurface)
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // 0. User Profile & Subscription Details
            UserProfileSubscriptionSection(
                authViewModel = authViewModel,
                initialExpandBilling = initialExpandBilling
            )

            // 1. SIM Management — Detected SIMs
            EasyMoMoCard(modifier = Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.SimCard, contentDescription = null, tint = EmeraldPrimary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        "SIM Management",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))
                if (uiState.activeSims.isEmpty()) {
                    Text(
                        text = if (uiState.permissionReport?.readPhoneState == CapabilityStatus.GRANTED) {
                            "No active SIM subscriptions detected on this device."
                        } else {
                            "Grant Phone State permission to detect active SIM slots and carriers."
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = StatusWarning
                    )
                } else {
                    uiState.activeSims.forEach { sim ->
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            shape = RoundedCornerShape(8.dp),
                            color = TerminalBackground,
                            border = BorderStroke(1.dp, TerminalBorder)
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = if (sim.isEmbedded) "eSIM • ${sim.displayName}" else "SIM ${sim.slotIndex + 1} • ${sim.displayName}",
                                        fontWeight = FontWeight.SemiBold,
                                        color = TextPrimary
                                    )
                                    Text(
                                        text = "Active",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = StatusSuccess,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                Text(
                                    text = "Carrier: ${sim.carrierName.ifBlank { "Unknown" }} • SubId: ${sim.subscriptionId}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextSecondary
                                )
                                sim.phoneNumber?.let { num ->
                                    Text(
                                        text = "Number: $num",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = TextSecondary
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))
                HorizontalDivider(color = TerminalBorder)
                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    "Per-Network SIM Assignment",
                    style = MaterialTheme.typography.labelLarge,
                    color = EmeraldPrimary,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    "Select a SIM for each network, optionally customize Per-Feature SIMs, then tap Save SIM Settings.",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSecondary
                )
                Spacer(modifier = Modifier.height(8.dp))

                SimPreferencesStore.SUPPORTED_NETWORKS.forEach { network ->
                    val resolution = uiState.simResolutions[network]
                    val draftNetworkSim = draftNetworkSims[network]
                    val networkFeatures = remember(network) { SimPreferencesStore.featuresForNetwork(network) }
                    val overrideCount = networkFeatures.count { feature ->
                        draftFeatureSims[network to feature] != null
                    }
                    var showFeatureOverrides by remember(network) { mutableStateOf(false) }
                    Column(modifier = Modifier.padding(vertical = 4.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = network.displayName,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            TextButton(onClick = { showFeatureOverrides = !showFeatureOverrides }) {
                                Text(
                                    if (showFeatureOverrides) {
                                        "Hide Feature SIMs"
                                    } else {
                                        "Per-Feature SIMs ($overrideCount/${networkFeatures.size})"
                                    },
                                    style = MaterialTheme.typography.labelSmall
                                )
                            }
                        }
                        if (resolution != null && !isSimDraftDirty) {
                            val isWarning = resolution.status == SimResolutionStatus.SIM_UNAVAILABLE_ATTENTION_REQUIRED ||
                                resolution.status == SimResolutionStatus.NETWORK_MISMATCH_WARNING ||
                                resolution.status == SimResolutionStatus.REQUIRES_SELECTION
                            Text(
                                text = resolution.message,
                                style = MaterialTheme.typography.labelSmall,
                                color = if (isWarning) StatusWarning else TextSecondary
                            )
                        }
                        Text(
                            text = when {
                                draftNetworkSim != null && overrideCount > 0 ->
                                    "Ready via ${draftNetworkSim.label} ($overrideCount custom feature override(s))"
                                draftNetworkSim != null ->
                                    "All ${networkFeatures.size} operations ready via ${draftNetworkSim.label}"
                                overrideCount == networkFeatures.size ->
                                    "All $overrideCount/${networkFeatures.size} feature SIMs assigned"
                                else ->
                                    "Select a SIM above to unlock ${network.displayName} operations ($overrideCount/${networkFeatures.size} feature overrides)"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = if (draftNetworkSim != null || overrideCount == networkFeatures.size) StatusSuccess else StatusWarning,
                            fontWeight = FontWeight.Medium
                        )
                        if (uiState.activeSims.isNotEmpty()) {
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.padding(top = 4.dp)
                            ) {
                                uiState.activeSims.forEach { sim ->
                                    val isSelected = draftNetworkSim?.subscriptionId == sim.subscriptionId
                                    FilterChip(
                                        selected = isSelected,
                                        onClick = {
                                            draftNetworkSims = draftNetworkSims + (network to sim)
                                            isSimDraftDirty = true
                                            terminalViewModel.clearSimSettingsSavedMessage()
                                        },
                                        label = { Text(sim.label) },
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = EmeraldPrimaryContainer,
                                            selectedLabelColor = OnEmeraldPrimaryContainer
                                        )
                                    )
                                }
                            }
                        }
                        if (showFeatureOverrides && uiState.activeSims.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(6.dp))
                            Surface(
                                color = TerminalBackground,
                                shape = RoundedCornerShape(8.dp),
                                border = BorderStroke(1.dp, TerminalBorder),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text(
                                        text = "${network.displayName} Core Feature SIMs",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        color = EmeraldPrimary
                                    )
                                    Text(
                                        text = "Quick assign all ${network.displayName} operations:",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = TextSecondary
                                    )
                                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        uiState.activeSims.forEach { sim ->
                                            val shortSlot = if (sim.isEmbedded) "eSIM" else "SIM ${sim.slotIndex + 1}"
                                            val allMatchThisSim = networkFeatures.all { feature ->
                                                draftFeatureSims[network to feature]?.subscriptionId == sim.subscriptionId
                                            }
                                            FilterChip(
                                                selected = allMatchThisSim,
                                                onClick = {
                                                    val updated = draftFeatureSims.toMutableMap()
                                                    networkFeatures.forEach { feature ->
                                                        updated[network to feature] = sim
                                                    }
                                                    draftFeatureSims = updated
                                                    isSimDraftDirty = true
                                                    terminalViewModel.clearSimSettingsSavedMessage()
                                                },
                                                label = { Text("Use $shortSlot for all", style = MaterialTheme.typography.labelSmall) },
                                                colors = FilterChipDefaults.filterChipColors(
                                                    selectedContainerColor = EmeraldPrimaryContainer,
                                                    selectedLabelColor = OnEmeraldPrimaryContainer
                                                )
                                            )
                                        }
                                        if (overrideCount > 0) {
                                            FilterChip(
                                                selected = false,
                                                onClick = {
                                                    val updated = draftFeatureSims.toMutableMap()
                                                    networkFeatures.forEach { feature ->
                                                        updated[network to feature] = null
                                                    }
                                                    draftFeatureSims = updated
                                                    isSimDraftDirty = true
                                                    terminalViewModel.clearSimSettingsSavedMessage()
                                                },
                                                label = { Text("Use Network Default", style = MaterialTheme.typography.labelSmall) }
                                            )
                                        }
                                    }
                                    HorizontalDivider(color = TerminalBorder)
                                    networkFeatures.forEach { feature ->
                                        val featureSim = draftFeatureSims[network to feature]
                                        Column {
                                            Text(
                                                text = when {
                                                    featureSim != null -> "${feature.displayName} • ${featureSim.label}"
                                                    draftNetworkSim != null -> "${feature.displayName} • Using ${network.displayName} SIM (${draftNetworkSim.label})"
                                                    else -> "${feature.displayName} • Not selected (locked)"
                                                },
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.Medium,
                                                color = when {
                                                    featureSim != null -> TextPrimary
                                                    draftNetworkSim != null -> TextSecondary
                                                    else -> StatusWarning
                                                }
                                            )
                                            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                                uiState.activeSims.forEach { sim ->
                                                    val isFeatureSelected = featureSim?.subscriptionId == sim.subscriptionId
                                                    FilterChip(
                                                        selected = isFeatureSelected,
                                                        onClick = {
                                                            draftFeatureSims = draftFeatureSims + ((network to feature) to sim)
                                                            isSimDraftDirty = true
                                                            terminalViewModel.clearSimSettingsSavedMessage()
                                                        },
                                                        label = { Text(sim.label, style = MaterialTheme.typography.labelSmall) },
                                                        colors = FilterChipDefaults.filterChipColors(
                                                            selectedContainerColor = EmeraldPrimaryContainer,
                                                            selectedLabelColor = OnEmeraldPrimaryContainer
                                                        )
                                                    )
                                                }
                                                if (featureSim != null) {
                                                    FilterChip(
                                                        selected = false,
                                                        onClick = {
                                                            draftFeatureSims = draftFeatureSims + ((network to feature) to null)
                                                            isSimDraftDirty = true
                                                            terminalViewModel.clearSimSettingsSavedMessage()
                                                        },
                                                        label = { Text("Use Network Default", style = MaterialTheme.typography.labelSmall) }
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

                Spacer(modifier = Modifier.height(10.dp))
                HorizontalDivider(color = TerminalBorder)
                Spacer(modifier = Modifier.height(10.dp))

                if (isSimDraftDirty) {
                    Text(
                        text = "Unsaved changes — tap Save SIM Settings",
                        style = MaterialTheme.typography.labelSmall,
                        color = StatusWarning,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                } else if (uiState.simSettingsSavedMessage != null) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            Icons.Outlined.CheckCircle,
                            contentDescription = null,
                            tint = StatusSuccess,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = uiState.simSettingsSavedMessage!!,
                            style = MaterialTheme.typography.labelSmall,
                            color = StatusSuccess,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                }

                Button(
                    onClick = {
                        isSimDraftDirty = false
                        terminalViewModel.saveSimConfiguration(
                            networkSelections = draftNetworkSims,
                            featureSelections = draftFeatureSims
                        )
                    },
                    enabled = uiState.activeSims.isNotEmpty() && !uiState.isSavingSimSettings,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = EmeraldPrimary,
                        contentColor = Color.White
                    )
                ) {
                    Text(if (uiState.isSavingSimSettings) "Saving..." else "Save SIM Settings")
                }
            }

            // 1b. Transaction Settings
            EasyMoMoCard(modifier = Modifier.fillMaxWidth()) {
                val settings = uiState.agentSettings
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.Tune, contentDescription = null, tint = EmeraldPrimary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        "Transaction Settings",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    "Default Network Mode",
                    style = MaterialTheme.typography.labelLarge,
                    color = EmeraldPrimary,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    "Choose 'MTN Only' (or another network) to skip the network selection popup when tapping Cash In, Data, Airtime, or GIG.",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSecondary
                )
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf(
                        "ASK" to "Ask Every Time",
                        "MTN" to "MTN Only",
                        "TELECEL" to "Telecel",
                        "AT" to "AT"
                    ).forEach { (code, label) ->
                        FilterChip(
                            selected = settings.defaultNetworkMode == code,
                            onClick = { terminalViewModel.setDefaultNetworkMode(code) },
                            label = { Text(label, style = MaterialTheme.typography.labelSmall) }
                        )
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp), color = TerminalBorder)

                SettingToggleRow(
                    title = "Auto-Clear Form After Transaction",
                    subtitle = "Clear amount and phone number when a USSD session finishes.",
                    checked = settings.autoClearFormAfterTx,
                    onCheckedChange = terminalViewModel::setAutoClearFormAfterTx
                )

                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = TerminalBorder)

                SettingToggleRow(
                    title = "Customer Number Autocomplete",
                    subtitle = "Suggest previously entered customer numbers on the Home screen.",
                    checked = settings.saveCustomerNumbersEnabled,
                    onCheckedChange = terminalViewModel::setSaveCustomerNumbersEnabled
                )

                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = TerminalBorder)

                SettingToggleRow(
                    title = "Hide Daily Totals on Home Screen",
                    subtitle = "Mask Today's Activity totals on the main terminal for counter privacy.",
                    checked = settings.hideTodayTotalsOnHome,
                    onCheckedChange = terminalViewModel::setHideTodayTotalsOnHome
                )

                HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp), color = TerminalBorder)

                SettingToggleRow(
                    title = "Auto-Enter Telecel Operational ID",
                    subtitle = if (settings.autoEnterTelecelOperationalId) {
                        "Automatically enters your saved Operational ID during Telecel transactions."
                    } else {
                        "Off by default. Agents enter the Telecel Operational ID manually in the USSD prompt."
                    },
                    checked = settings.autoEnterTelecelOperationalId,
                    onCheckedChange = terminalViewModel::setAutoEnterTelecelOperationalId
                )

                if (settings.autoEnterTelecelOperationalId) {
                    var opIdDraft by remember(uiState.telecelOperationalId) {
                        mutableStateOf(uiState.telecelOperationalId)
                    }
                    var opIdSavedHint by remember { mutableStateOf(false) }

                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = opIdDraft,
                            onValueChange = {
                                opIdDraft = it
                                opIdSavedHint = false
                            },
                            label = { Text("Saved Telecel Operational ID") },
                            placeholder = { Text("e.g. 123456") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        Button(
                            onClick = {
                                terminalViewModel.saveTelecelOperationalId(opIdDraft)
                                opIdSavedHint = true
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = EmeraldPrimary,
                                contentColor = Color.White
                            )
                        ) {
                            Text("Save")
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = when {
                            opIdSavedHint -> "Saved! Operational ID will be entered automatically."
                            uiState.telecelOperationalId.isBlank() -> "Enter and save your Operational ID above; otherwise EasyMoMo will pause for manual entry."
                            else -> "Active Operational ID: ${uiState.telecelOperationalId}"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = if (opIdSavedHint) StatusSuccess else TextSecondary
                    )
                }
            }

            // 1c. Customer Cloud Sync
            EasyMoMoCard(modifier = Modifier.fillMaxWidth()) {
                val statusColor = when (syncStatus.state) {
                    CustomerCloudSyncState.SYNCED -> StatusSuccess
                    CustomerCloudSyncState.SYNCING -> EmeraldPrimary
                    CustomerCloudSyncState.PENDING -> StatusWarning
                    CustomerCloudSyncState.OFFLINE -> TextSecondary
                    CustomerCloudSyncState.SYNC_FAILED -> StatusError
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Customer Cloud Sync",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Surface(
                        color = statusColor.copy(alpha = 0.12f),
                        shape = RoundedCornerShape(999.dp)
                    ) {
                        Text(
                            text = syncStatus.state.label,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = statusColor,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(6.dp))
                SettingToggleRow(
                    title = "Automatic Cloud Backup",
                    subtitle = "Automatically back up and restore saved customer numbers across your devices.",
                    checked = syncStatus.autoSyncEnabled,
                    onCheckedChange = terminalViewModel::setCustomerCloudSyncEnabled
                )
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = TerminalBorder)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Locally saved: ${syncStatus.localCount}", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                    Text("Synced: ${syncStatus.syncedCount}", style = MaterialTheme.typography.labelSmall, color = StatusSuccess)
                    Text("Pending: ${syncStatus.pendingCount}", style = MaterialTheme.typography.labelSmall, color = if (syncStatus.pendingCount > 0) StatusWarning else TextSecondary)
                }
                Spacer(modifier = Modifier.height(6.dp))
                val lastSyncLabel = remember(syncStatus.lastSuccessAt) {
                    if (syncStatus.lastSuccessAt <= 0L) "Never"
                    else java.text.DateFormat.getDateTimeInstance(java.text.DateFormat.SHORT, java.text.DateFormat.SHORT)
                        .format(java.util.Date(syncStatus.lastSuccessAt))
                }
                Text(
                    text = "Last successful sync: $lastSyncLabel",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSecondary
                )
                syncStatus.lastError?.takeIf { it.isNotBlank() }?.let { err ->
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Last sync error: $err",
                        style = MaterialTheme.typography.labelSmall,
                        color = StatusError
                    )
                }
                Spacer(modifier = Modifier.height(10.dp))
                Button(
                    onClick = { terminalViewModel.syncCustomersNow() },
                    enabled = syncStatus.state != CustomerCloudSyncState.SYNCING,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(if (syncStatus.state == CustomerCloudSyncState.SYNCING) "Syncing..." else "Sync Now")
                }
            }

            // 2. Permissions & Accessibility Service
            EasyMoMoCard(modifier = Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.Security, contentDescription = null, tint = EmeraldPrimary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        "Permissions & USSD Automation Capability",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))

                uiState.permissionReport?.toDiagnosticsList()?.forEach { diag ->
                    val isOk = diag.status == CapabilityStatus.GRANTED
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(diag.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                            Text(diag.reason, style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(if (isOk) StatusSuccessBg else StatusWarningBg)
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = diag.status.name.replace('_', ' '),
                                style = MaterialTheme.typography.labelSmall,
                                color = if (isOk) StatusSuccess else StatusWarning,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = "Why Accessibility is required: Android's CALL_PHONE and TelephonyManager.sendUssdRequest() cannot enter subsequent responses into interactive multi-step USSD dialogs. Enabling 'EasyMoMo USSD Assistant' lets the app read USSD menus and enter only the customer number, amount, and verified menu options—never your PIN.",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSecondary
                )
                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = onRequestPermissions,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Grant Phone Perms")
                    }
                    Button(
                        onClick = onOpenAccessibilitySettings,
                        colors = ButtonDefaults.buttonColors(containerColor = EmeraldPrimary),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Outlined.AccessibilityNew, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Accessibility")
                    }
                }
                TextButton(onClick = onOpenAppSettings, modifier = Modifier.align(Alignment.End)) {
                    Text("Open Android App Settings")
                }
            }

            // 3. Device & USSD Flow Diagnostics
            EasyMoMoCard(modifier = Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.BugReport, contentDescription = null, tint = EmeraldPrimary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        "Device & Flow Diagnostics",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text("Device: ${Build.MANUFACTURER} ${Build.MODEL} (Android ${Build.VERSION.RELEASE}, API ${Build.VERSION.SDK_INT})", style = MaterialTheme.typography.bodySmall)
                Text(
                    "Interactive Auto-USSD: ${if (uiState.isAccessibilityEnabled) "Available (Accessibility Active)" else "Manual Fallback Only (Accessibility Disabled)"}",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (uiState.isAccessibilityEnabled) StatusSuccess else StatusWarning
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text("Configured Flows (${flows.size}):", style = MaterialTheme.typography.labelLarge)
                flows.forEach { flow ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 2.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("${flow.flowId} (v${flow.version})", style = MaterialTheme.typography.labelSmall)
                        Text(
                            text = if (flow.enabled && !flow.configJson.contains("VERIFY_FROM_AGENT_SIM")) "ACTIVE" else "UNVERIFIED",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (flow.enabled && !flow.configJson.contains("VERIFY_FROM_AGENT_SIM")) StatusSuccess else StatusError,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            // 4. Safe Test Mode Simulator (Never dials real USSD)
            EasyMoMoCard(modifier = Modifier.fillMaxWidth()) {
                Text(
                    "Safe USSD Test Mode (Simulated)",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = EmeraldPrimary
                )
                Text(
                    "Simulates USSD prompts through UssdFlowController without dialing or touching real money.",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSecondary
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Simulated State: ${simState::class.simpleName}",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold
                )
                uiState.testModeLastAction?.let {
                    Text(text = it, style = MaterialTheme.typography.labelSmall, color = EmeraldPrimary)
                }
                Spacer(modifier = Modifier.height(8.dp))

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    OutlinedButton(onClick = { terminalViewModel.startSimulatedMtnCashIn() }) {
                        Text("1. Start MTN Cash-In")
                    }
                    OutlinedButton(onClick = { terminalViewModel.simulatePrompt("Welcome to MTN MoMo Agent\n1. Transfer\n2. Pay Cash Out\n3. Cash In") }) {
                        Text("2. Main Menu")
                    }
                    OutlinedButton(onClick = { terminalViewModel.simulatePrompt("Select user type:\n1. Mobile Money User") }) {
                        Text("3. User Type")
                    }
                    OutlinedButton(onClick = { terminalViewModel.simulatePrompt("Enter customer mobile number:") }) {
                        Text("4. Phone Prompt")
                    }
                    OutlinedButton(onClick = { terminalViewModel.simulatePrompt("Enter amount in GHS:") }) {
                        Text("5. Amount Prompt")
                    }
                    OutlinedButton(onClick = { terminalViewModel.simulatePrompt("Confirm Cash In GHS 10.00 to 0535308873?") }) {
                        Text("6. Confirm Prompt")
                    }
                    OutlinedButton(onClick = { terminalViewModel.simulatePrompt("Enter MoMo PIN to authorize:") }) {
                        Text("7. PIN Pause")
                    }
                    OutlinedButton(onClick = { terminalViewModel.simulatePrompt("Cash In completed successfully. Ref: SIM123456") }) {
                        Text("8. Success")
                    }
                    OutlinedButton(onClick = { terminalViewModel.simulatePrompt("Unexpected promo menu: Press 9") }) {
                        Text("Unexpected")
                    }
                    OutlinedButton(onClick = { terminalViewModel.simulateTimeout() }) {
                        Text("Timeout")
                    }
                }
            }

            // 5. Sanitized Diagnostic Logs
            EasyMoMoCard(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Sanitized Diagnostic Log",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    TextButton(onClick = { terminalViewModel.clearDiagnosticLogs() }) {
                        Text("Clear")
                    }
                }
                if (uiState.diagnosticLogs.isEmpty()) {
                    Text("No diagnostic events recorded yet.", style = MaterialTheme.typography.bodySmall, color = TextMuted)
                } else {
                    uiState.diagnosticLogs.take(20).forEach { entry ->
                        Text(
                            text = "[${entry.timestamp}] ${entry.stage}: ${entry.message}",
                            style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                            color = TextSecondary,
                            modifier = Modifier.padding(vertical = 2.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = TextPrimary
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = TextSecondary
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange
        )
    }
}

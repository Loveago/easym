package com.easymomo.app.ui.terminal

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.DataUsage
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.People
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.easymomo.app.domain.model.NetworkType
import com.easymomo.app.domain.model.TransactionType
import com.easymomo.app.ui.theme.EasyMoMoCard
import com.easymomo.app.ui.theme.EmeraldPrimary
import com.easymomo.app.ui.theme.EmptyStateView
import com.easymomo.app.ui.theme.NetworkBadge
import com.easymomo.app.ui.theme.StatusError
import com.easymomo.app.ui.theme.TerminalActionButton
import com.easymomo.app.ui.theme.TerminalBackground
import com.easymomo.app.ui.theme.TerminalOutlinedButton
import com.easymomo.app.ui.theme.TextPrimary
import com.easymomo.app.ui.theme.TextSecondary
import com.easymomo.app.ussd.model.PromptMatchMode
import com.easymomo.app.ussd.model.StepAction
import com.easymomo.app.ussd.model.StepType
import com.easymomo.app.ussd.model.UssdFlowProfile
import com.easymomo.app.ussd.model.UssdFlowStep

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DataBundlesScreen(
    initialNetwork: NetworkType,
    terminalViewModel: TerminalViewModel,
    onSelectBundleAndReview: (localId: String, isCustomerLed: Boolean) -> Unit,
    onBack: () -> Unit
) {
    var selectedNet by remember { mutableStateOf(if (initialNetwork == NetworkType.UNKNOWN) NetworkType.MTN else initialNetwork) }
    val bundles by terminalViewModel.bundlesForNetwork(selectedNet).collectAsState(initial = emptyList())

    Scaffold(
        containerColor = TerminalBackground,
        topBar = {
            TopAppBar(
                title = { Text("Data Bundles") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(NetworkType.MTN, NetworkType.AT, NetworkType.TELECEL).forEach { net ->
                    FilterChip(
                        selected = selectedNet == net,
                        onClick = { selectedNet = net },
                        label = { Text(net.displayName) }
                    )
                }
            }

            if (bundles.isEmpty()) {
                EmptyStateView(
                    icon = Icons.Outlined.DataUsage,
                    title = "No Verified Bundles Cached",
                    subtitle = "Data bundle prices and menu options are loaded from verified backend configuration for ${selectedNet.displayName}."
                )
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(bundles) { bundle ->
                        EasyMoMoCard(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    terminalViewModel.prepareTransaction(
                                        type = TransactionType.DATA,
                                        overrideNetwork = selectedNet,
                                        bundle = bundle,
                                        onReady = onSelectBundleAndReview
                                    )
                                }
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(bundle.displayName, style = MaterialTheme.typography.titleMedium)
                                    Text("${bundle.allowance} • ${bundle.validity}", style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
                                }
                                Text(
                                    "GHS ${String.format("%.2f", bundle.price)}",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = EmeraldPrimary
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomUssdScreen(
    terminalViewModel: TerminalViewModel,
    onNavigateToReview: (localId: String, isCustomerLed: Boolean) -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val uiState by terminalViewModel.uiState.collectAsState()
    var ussdCode by remember { mutableStateOf("") }

    Scaffold(
        containerColor = TerminalBackground,
        topBar = {
            TopAppBar(
                title = { Text("Custom USSD Transaction") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            EasyMoMoCard(modifier = Modifier.fillMaxWidth()) {
                Text("Dial Verified USSD Shortcode", style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = ussdCode,
                    onValueChange = { ussdCode = it },
                    label = { Text("USSD Code (e.g. *170#)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(12.dp))
                TerminalActionButton(
                    label = "Open in Phone Dialer",
                    enabled = ussdCode.isNotBlank(),
                    onClick = {
                        val encoded = Uri.encode("#")
                        val formatted = ussdCode.trim().replace("#", encoded)
                        context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$formatted")))
                    },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                TerminalOutlinedButton(
                    label = "Log as Local Transaction",
                    enabled = ussdCode.isNotBlank(),
                    onClick = {
                        terminalViewModel.prepareTransaction(
                            type = TransactionType.CUSTOM_USSD,
                            overrideNetwork = if (uiState.detectedNetwork == NetworkType.UNKNOWN) NetworkType.MTN else uiState.detectedNetwork,
                            customUssdCode = ussdCode.trim(),
                            onReady = onNavigateToReview
                        )
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PayMerchantScreen(
    terminalViewModel: TerminalViewModel,
    onNavigateToReview: (localId: String, isCustomerLed: Boolean) -> Unit,
    onBack: () -> Unit
) {
    var merchantCode by remember { mutableStateOf("") }
    var selectedNet by remember { mutableStateOf(NetworkType.MTN) }

    Scaffold(
        containerColor = TerminalBackground,
        topBar = {
            TopAppBar(
                title = { Text("Pay to Merchant") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(NetworkType.MTN, NetworkType.AT, NetworkType.TELECEL).forEach { net ->
                    FilterChip(
                        selected = selectedNet == net,
                        onClick = { selectedNet = net },
                        label = { Text(net.shortName) }
                    )
                }
            }
            EasyMoMoCard(modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = merchantCode,
                    onValueChange = { merchantCode = it },
                    label = { Text("Merchant ID / Till Number") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(12.dp))
                TerminalActionButton(
                    label = "Review Merchant Payment",
                    enabled = merchantCode.isNotBlank(),
                    onClick = {
                        terminalViewModel.prepareTransaction(
                            type = TransactionType.MERCHANT_PAY,
                            overrideNetwork = selectedNet,
                            merchantCode = merchantCode.trim(),
                            onReady = onNavigateToReview
                        )
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UssdDevConfigScreen(
    terminalViewModel: TerminalViewModel,
    onBack: () -> Unit
) {
    val flows by terminalViewModel.allUssdFlows.collectAsState()
    var selectedNet by remember { mutableStateOf(NetworkType.MTN) }
    var selectedType by remember { mutableStateOf(TransactionType.CASH_IN) }
    var entryCode by remember { mutableStateOf("") }
    var initialPrompt by remember { mutableStateOf("") }
    var menuOption by remember { mutableStateOf("") }
    var markVerified by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = TerminalBackground,
        topBar = {
            TopAppBar(
                title = { Text("USSD Prompt Capture & Config") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            EasyMoMoCard(modifier = Modifier.fillMaxWidth()) {
                Text("Section 8: Capture & Verify Real Agent SIM Prompts", style = MaterialTheme.typography.titleMedium, color = EmeraldPrimary)
                Spacer(modifier = Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(NetworkType.MTN, NetworkType.AT, NetworkType.TELECEL).forEach { net ->
                        FilterChip(selected = selectedNet == net, onClick = { selectedNet = net }, label = { Text(net.shortName) })
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(TransactionType.CASH_IN, TransactionType.CASH_OUT, TransactionType.AIRTIME).forEach { t ->
                        FilterChip(selected = selectedType == t, onClick = { selectedType = t }, label = { Text(t.displayName) })
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = entryCode,
                    onValueChange = { entryCode = it },
                    label = { Text("Verified USSD Entry Code (e.g. *171#)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = initialPrompt,
                    onValueChange = { initialPrompt = it },
                    label = { Text("Observed Initial Menu Prompt") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = menuOption,
                    onValueChange = { menuOption = it },
                    label = { Text("Verified Menu Option") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Tested on authorized Agent SIM (Enable)", style = MaterialTheme.typography.bodyMedium)
                    Switch(checked = markVerified, onCheckedChange = { markVerified = it })
                }
                Spacer(modifier = Modifier.height(10.dp))
                TerminalActionButton(
                    label = "Save Draft / Verified Flow",
                    enabled = entryCode.isNotBlank(),
                    onClick = {
                        val flowId = "${selectedNet.code}_${selectedType.code}"
                        val profile = UssdFlowProfile(
                            flowId = flowId,
                            network = selectedNet.code,
                            transactionType = selectedType.code,
                            version = 2,
                            entryCode = entryCode.trim(),
                            enabled = markVerified,
                            verifiedWithSim = markVerified,
                            steps = listOf(
                                UssdFlowStep(id = "START", type = StepType.INITIATE_USSD, nextStepId = "MAIN_MENU"),
                                UssdFlowStep(
                                    id = "MAIN_MENU",
                                    type = StepType.MATCH_PROMPT,
                                    expectedPrompts = listOf(initialPrompt.trim().ifBlank { "VERIFY_FROM_AGENT_SIM" }),
                                    matchMode = PromptMatchMode.NORMALIZED_TEXT,
                                    action = StepAction.SEND_MENU_OPTION,
                                    menuOption = menuOption.trim().ifBlank { "1" },
                                    nextStepId = "FINAL_RESULT"
                                ),
                                UssdFlowStep(id = "FINAL_RESULT", type = StepType.MATCH_RESULT)
                            )
                        )
                        terminalViewModel.saveVerifiedUssdFlow(profile)
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Text("Configured Flows (${flows.size})", style = MaterialTheme.typography.titleMedium)
            flows.forEach { f ->
                EasyMoMoCard(modifier = Modifier.fillMaxWidth()) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(f.flowId, style = MaterialTheme.typography.titleMedium)
                        Text(
                            if (f.enabled) "ENABLED (v${f.version})" else "REQUIRES SIM VERIFICATION",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (f.enabled) EmeraldPrimary else TextSecondary,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomersScreen(
    terminalViewModel: TerminalViewModel,
    onPickCustomer: (String) -> Unit,
    onBack: () -> Unit
) {
    val savedCustomers by terminalViewModel.savedCustomerNumbers.collectAsState()
    var searchQuery by remember { mutableStateOf("") }
    var showClearConfirm by remember { mutableStateOf(false) }
    var editingCustomer by remember { mutableStateOf<com.easymomo.app.data.local.CustomerNumberEntity?>(null) }
    var nicknameDraft by remember { mutableStateOf("") }

    val dateFormatter = remember {
        java.text.SimpleDateFormat("MMM d, yyyy • HH:mm", java.util.Locale.US)
    }

    val filteredCustomers = remember(savedCustomers, searchQuery) {
        val normDigits = com.easymomo.app.domain.model.NetworkPrefixDetector.normalizeSearchQuery(searchQuery)
        val rawLower = searchQuery.trim().lowercase()
        if (rawLower.isEmpty()) {
            savedCustomers
        } else {
            savedCustomers.filter { item ->
                (normDigits.isNotEmpty() && item.normalizedPhoneNumber.contains(normDigits)) ||
                    item.phoneNumber.contains(rawLower) ||
                    item.nickname?.lowercase()?.contains(rawLower) == true
            }
        }
    }

    Scaffold(
        containerColor = TerminalBackground,
        topBar = {
            TopAppBar(
                title = { Text("Customer Numbers") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (savedCustomers.isNotEmpty()) {
                        TextButton(onClick = { showClearConfirm = true }) {
                            Text("Clear All", color = StatusError)
                        }
                    }
                }
            )
        }
    ) { padding ->
        if (savedCustomers.isEmpty()) {
            EmptyStateView(
                icon = Icons.Outlined.People,
                title = "No Saved Customer Numbers",
                subtitle = "Valid customer numbers entered on the home screen are saved offline automatically when you initiate a transaction.",
                modifier = Modifier.padding(padding)
            )
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    label = { Text("Search number or nickname") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                if (filteredCustomers.isEmpty()) {
                    Text(
                        text = "No matching saved numbers.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary
                    )
                } else {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(filteredCustomers, key = { it.id }) { customer ->
                            val detectedNet = com.easymomo.app.domain.model.NetworkPrefixDetector
                                .detectNetwork(customer.phoneNumber, isPorted = false)
                            EasyMoMoCard(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        terminalViewModel.onPhoneChange(customer.phoneNumber)
                                        terminalViewModel.dismissPhoneSuggestions()
                                        onPickCustomer(customer.phoneNumber)
                                    }
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Text(customer.phoneNumber, style = MaterialTheme.typography.titleMedium)
                                            if (detectedNet != NetworkType.UNKNOWN) {
                                                NetworkBadge(detectedNet)
                                            }
                                        }
                                        customer.nickname?.takeIf { it.isNotBlank() }?.let { nick ->
                                            Text(nick, style = MaterialTheme.typography.bodySmall, color = EmeraldPrimary)
                                        }
                                        Text(
                                            text = "Used ${customer.usageCount}x • Last: ${dateFormatter.format(java.util.Date(customer.lastUsedAt))}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = TextSecondary
                                        )
                                    }
                                    Row {
                                        IconButton(
                                            onClick = {
                                                editingCustomer = customer
                                                nicknameDraft = customer.nickname.orEmpty()
                                            }
                                        ) {
                                            Icon(Icons.Outlined.Edit, contentDescription = "Edit nickname", tint = TextSecondary)
                                        }
                                        IconButton(
                                            onClick = { terminalViewModel.deleteCustomerNumber(customer.id) }
                                        ) {
                                            Icon(Icons.Outlined.Delete, contentDescription = "Delete number", tint = StatusError)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showClearConfirm) {
        AlertDialog(
            onDismissRequest = { showClearConfirm = false },
            title = { Text("Clear Customer Numbers?") },
            text = { Text("This removes all locally saved customer numbers. Existing transaction history will not be affected.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        terminalViewModel.clearCustomerNumbers()
                        showClearConfirm = false
                    }
                ) { Text("Clear All", color = StatusError) }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirm = false }) { Text("Cancel") }
            }
        )
    }

    editingCustomer?.let { target ->
        AlertDialog(
            onDismissRequest = { editingCustomer = null },
            title = { Text("Optional Nickname") },
            text = {
                OutlinedTextField(
                    value = nicknameDraft,
                    onValueChange = { nicknameDraft = it },
                    label = { Text("Nickname for ${target.phoneNumber}") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        terminalViewModel.updateCustomerNickname(target.id, nicknameDraft)
                        editingCustomer = null
                    }
                ) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { editingCustomer = null }) { Text("Cancel") }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HelpSetupScreen(
    terminalViewModel: TerminalViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val uiState by terminalViewModel.uiState.collectAsState()

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        terminalViewModel.refreshDeviceStatus()
    }

    Scaffold(
        containerColor = TerminalBackground,
        topBar = {
            TopAppBar(
                title = { Text("SIM & USSD Setup Guide") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            EasyMoMoCard(modifier = Modifier.fillMaxWidth()) {
                Text("1. Telephony & SIM Permissions", style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    "Grant Phone & Call permissions so EasyMoMo can detect your MTN, AT, and Telecel Agent SIMs and launch USSD sessions.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary
                )
                Spacer(modifier = Modifier.height(10.dp))
                TerminalActionButton(
                    label = if (uiState.hasCallPermission) "Permissions Granted" else "Grant Phone Permissions",
                    enabled = !uiState.hasCallPermission,
                    onClick = {
                        permissionLauncher.launch(
                            arrayOf(
                                Manifest.permission.CALL_PHONE,
                                Manifest.permission.READ_PHONE_STATE
                            )
                        )
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            }

            EasyMoMoCard(modifier = Modifier.fillMaxWidth()) {
                Text("2. Optional USSD Accessibility Assistant", style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    "EasyMoMo uses Android Accessibility solely to read system USSD prompts and fill verified non-PIN fields during active sessions. PINs are never read, stored, or sent to any server.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary
                )
                Spacer(modifier = Modifier.height(10.dp))
                TerminalOutlinedButton(
                    label = if (uiState.isAccessibilityEnabled) "Accessibility Assistant Enabled" else "Open Accessibility Settings",
                    onClick = {
                        context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

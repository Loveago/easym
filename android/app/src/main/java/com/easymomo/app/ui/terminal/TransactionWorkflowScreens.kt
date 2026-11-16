package com.easymomo.app.ui.terminal

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.easymomo.app.domain.model.NetworkType
import com.easymomo.app.domain.model.ProcessingMethod
import com.easymomo.app.domain.model.TransactionStatus
import com.easymomo.app.domain.model.TransactionType
import com.easymomo.app.ui.theme.EasyMoMoCard
import com.easymomo.app.ui.theme.EmeraldPrimary
import com.easymomo.app.ui.theme.NetworkBadge
import com.easymomo.app.ui.theme.StatusError
import com.easymomo.app.ui.theme.StatusInfo
import com.easymomo.app.ui.theme.StatusInfoBg
import com.easymomo.app.ui.theme.StatusPill
import com.easymomo.app.ui.theme.StatusWarning
import com.easymomo.app.ui.theme.StatusWarningBg
import com.easymomo.app.ui.theme.TerminalActionButton
import com.easymomo.app.ui.theme.TerminalBackground
import com.easymomo.app.ui.theme.TerminalOutlinedButton
import com.easymomo.app.ui.theme.TextPrimary
import com.easymomo.app.ui.theme.TextSecondary
import com.easymomo.app.ussd.model.UssdSessionState
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionReviewScreen(
    terminalViewModel: TerminalViewModel,
    onConfirmAgentUssd: (localId: String) -> Unit,
    onConfirmCustomerLed: (localId: String) -> Unit,
    onCancel: () -> Unit
) {
    val uiState by terminalViewModel.uiState.collectAsState()
    val tx = uiState.activeTransaction

    Scaffold(
        containerColor = TerminalBackground,
        topBar = {
            TopAppBar(
                title = { Text("Review Transaction") },
                navigationIcon = {
                    IconButton(onClick = onCancel) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        if (tx == null) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text("No active transaction.")
            }
            return@Scaffold
        }

        val network = NetworkType.fromCode(tx.network)
        val type = TransactionType.fromCode(tx.transactionType)
        val isCustomerLed = tx.processingMethod == ProcessingMethod.CUSTOMER_LED.code

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                EasyMoMoCard(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        NetworkBadge(network = network)
                        Text(type.displayName, style = MaterialTheme.typography.titleMedium, color = EmeraldPrimary)
                    }
                    Spacer(modifier = Modifier.height(14.dp))
                    ReviewRow("Recipient / Customer", tx.recipientNumber)
                    ReviewRow("Amount", "GHS ${String.format(Locale.US, "%.2f", tx.amount)}")
                    tx.bundleName?.let { ReviewRow("Data Bundle", it) }
                    ReviewRow(
                        "Workflow",
                        if (isCustomerLed) "Customer-Led Cash-Out (Agent Verified)" else "Agent-Side USSD"
                    )
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(StatusInfoBg)
                        .padding(12.dp)
                ) {
                    Text(
                        text = if (isCustomerLed) {
                            "${network.displayName} Cash-Out is customer-led. You will guide the customer on their own phone and never collect their PIN."
                        } else {
                            "Confirm details carefully. EasyMoMo will verify each USSD prompt before proceeding and pause when your Agent PIN is required."
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = StatusInfo
                    )
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                TerminalActionButton(
                    label = if (isCustomerLed) "Start Customer-Led Guide" else "Confirm & Start USSD",
                    onClick = {
                        if (isCustomerLed) onConfirmCustomerLed(tx.localId) else onConfirmAgentUssd(tx.localId)
                    },
                    modifier = Modifier.fillMaxWidth()
                )
                TerminalOutlinedButton(
                    label = "Cancel",
                    onClick = onCancel,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

@Composable
private fun ReviewRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = TextPrimary)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UssdProcessingScreen(
    terminalViewModel: TerminalViewModel,
    onOpenUssdConfig: () -> Unit,
    onNavigateToResult: () -> Unit,
    onCancelBack: () -> Unit
) {
    val uiState by terminalViewModel.uiState.collectAsState()
    val ussdState by terminalViewModel.ussdState.collectAsState()
    val tx = uiState.activeTransaction

    var manualRef by remember { mutableStateOf("") }

    Scaffold(
        containerColor = TerminalBackground,
        topBar = {
            TopAppBar(title = { Text("USSD Processing") })
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            tx?.let {
                EasyMoMoCard(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        NetworkBadge(NetworkType.fromCode(it.network))
                        StatusPill(TransactionStatus.fromCode(it.status))
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    ReviewRow("Type", TransactionType.fromCode(it.transactionType).displayName)
                    ReviewRow("Number", it.recipientNumber)
                    ReviewRow("Amount", "GHS ${String.format(Locale.US, "%.2f", it.amount)}")
                }
            }

            when (val state = ussdState) {
                is UssdSessionState.Idle,
                is UssdSessionState.Initiating,
                is UssdSessionState.AwaitingPrompt -> {
                    EasyMoMoCard(modifier = Modifier.fillMaxWidth()) {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            CircularProgressIndicator(color = EmeraldPrimary)
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = (state as? UssdSessionState.AwaitingPrompt)?.statusMessage
                                    ?: "Initiating verified USSD session...",
                                style = MaterialTheme.typography.bodyLarge,
                                color = TextPrimary
                            )
                        }
                    }
                    TerminalOutlinedButton(
                        label = "Cancel Safely",
                        onClick = {
                            terminalViewModel.cancelUssdSession()
                            onCancelBack()
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                is UssdSessionState.WaitingUserConfirmation -> {
                    EasyMoMoCard(modifier = Modifier.fillMaxWidth()) {
                        Text("Confirm Provider Prompt", style = MaterialTheme.typography.titleMedium, color = EmeraldPrimary)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(state.summaryText, style = MaterialTheme.typography.bodyMedium, color = TextPrimary)
                        Spacer(modifier = Modifier.height(12.dp))
                        TerminalActionButton(
                            label = "Approve & Continue",
                            onClick = terminalViewModel::confirmUssdStep,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }

                is UssdSessionState.PausedForOperationalId -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(StatusWarningBg)
                            .padding(14.dp)
                    ) {
                        Column {
                            Text("Operational ID Required", style = MaterialTheme.typography.titleMedium, color = StatusWarning)
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = state.instruction,
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextPrimary
                            )
                        }
                    }
                }

                is UssdSessionState.PausedForPinAuthorization -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(StatusWarningBg)
                            .padding(14.dp)
                    ) {
                        Column {
                            Text("Agent Authorization Required", style = MaterialTheme.typography.titleMedium, color = StatusWarning)
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "Enter your Agent PIN directly in the system USSD dialog. EasyMoMo never reads, stores, or transmits PINs.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextPrimary
                            )
                        }
                    }
                }

                is UssdSessionState.WaitingForCustomerApproval -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(StatusWarningBg)
                            .padding(14.dp)
                    ) {
                        Column {
                            Text("Awaiting Customer Approval", style = MaterialTheme.typography.titleMedium, color = StatusWarning)
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "Ask customer to approve Cash Out on their phone, then verify the SMS/confirmation before paying out cash.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextPrimary
                            )
                        }
                    }
                }

                is UssdSessionState.ConfigurationRequired -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(StatusWarningBg)
                            .padding(14.dp)
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(state.message, style = MaterialTheme.typography.titleMedium, color = StatusWarning)
                            Text(
                                text = "This flow (${state.flowId}) has not been verified on an authorized agent SIM yet. Use USSD Prompt Capture Mode to verify it, or record a manual provider result below after dialing.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextPrimary
                            )
                            TerminalOutlinedButton(
                                label = "Open USSD Prompt Capture Mode",
                                onClick = onOpenUssdConfig,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }

                    if (tx != null) {
                        ManualOutcomeRecorderCard(
                            manualRef = manualRef,
                            onManualRefChange = { manualRef = it },
                            onMarkSuccess = {
                                terminalViewModel.markManualOutcome(
                                    tx.localId,
                                    TransactionStatus.SUCCESSFUL,
                                    manualRef,
                                    "Agent verified manual USSD completion"
                                )
                                onNavigateToResult()
                            },
                            onMarkFailed = {
                                terminalViewModel.markManualOutcome(
                                    tx.localId,
                                    TransactionStatus.FAILED,
                                    manualRef,
                                    "Agent marked failed"
                                )
                                onNavigateToResult()
                            },
                            onMarkUnknown = {
                                terminalViewModel.markManualOutcome(
                                    tx.localId,
                                    TransactionStatus.UNKNOWN,
                                    manualRef,
                                    "Status uncertain; awaiting SMS check"
                                )
                                onNavigateToResult()
                            }
                        )
                    }
                }

                is UssdSessionState.ManualInterventionRequired -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(StatusWarningBg)
                            .padding(14.dp)
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("Manual Intervention Required", style = MaterialTheme.typography.titleMedium, color = StatusWarning)
                            Text(state.reason, style = MaterialTheme.typography.bodyMedium, color = TextPrimary)
                            state.observedPrompt?.let {
                                Text("Observed: $it", style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
                            }
                        }
                    }
                    if (tx != null) {
                        ManualOutcomeRecorderCard(
                            manualRef = manualRef,
                            onManualRefChange = { manualRef = it },
                            onMarkSuccess = {
                                terminalViewModel.markManualOutcome(tx.localId, TransactionStatus.SUCCESSFUL, manualRef, state.reason)
                                onNavigateToResult()
                            },
                            onMarkFailed = {
                                terminalViewModel.markManualOutcome(tx.localId, TransactionStatus.FAILED, manualRef, state.reason)
                                onNavigateToResult()
                            },
                            onMarkUnknown = {
                                terminalViewModel.markManualOutcome(tx.localId, TransactionStatus.UNKNOWN, manualRef, state.reason)
                                onNavigateToResult()
                            }
                        )
                    }
                }

                is UssdSessionState.Completed,
                is UssdSessionState.Cancelled,
                is UssdSessionState.TimedOut -> {
                    TerminalActionButton(
                        label = "View Transaction Result",
                        onClick = onNavigateToResult,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}

@Composable
private fun ManualOutcomeRecorderCard(
    manualRef: String,
    onManualRefChange: (String) -> Unit,
    onMarkSuccess: () -> Unit,
    onMarkFailed: () -> Unit,
    onMarkUnknown: () -> Unit
) {
    EasyMoMoCard(modifier = Modifier.fillMaxWidth()) {
        Text("Record Verified Outcome", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(
            value = manualRef,
            onValueChange = onManualRefChange,
            label = { Text("Provider Reference (optional)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(10.dp))
        TerminalActionButton(label = "Mark Verified Successful", onClick = onMarkSuccess, modifier = Modifier.fillMaxWidth())
        Spacer(modifier = Modifier.height(8.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TerminalOutlinedButton(label = "Mark Failed", onClick = onMarkFailed, modifier = Modifier.weight(1f))
            TerminalOutlinedButton(label = "Keep Unknown", onClick = onMarkUnknown, modifier = Modifier.weight(1f))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomerLedCashOutScreen(
    terminalViewModel: TerminalViewModel,
    onNavigateToResult: () -> Unit,
    onCancel: () -> Unit
) {
    val uiState by terminalViewModel.uiState.collectAsState()
    val tx = uiState.activeTransaction

    var check1 by remember { mutableStateOf(false) }
    var check2 by remember { mutableStateOf(false) }
    var check3 by remember { mutableStateOf(false) }
    var check4 by remember { mutableStateOf(false) }
    var check5 by remember { mutableStateOf(false) }
    var providerRef by remember { mutableStateOf("") }

    val allChecked = check1 && check2 && check3 && check4 && check5

    Scaffold(
        containerColor = TerminalBackground,
        topBar = {
            TopAppBar(
                title = { Text("Customer-Led Cash-Out") },
                navigationIcon = {
                    IconButton(onClick = onCancel) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        if (tx == null) return@Scaffold
        val network = NetworkType.fromCode(tx.network)

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            EasyMoMoCard(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    NetworkBadge(network)
                    Text("Customer-Led Workflow", style = MaterialTheme.typography.labelSmall, color = EmeraldPrimary)
                }
                Spacer(modifier = Modifier.height(10.dp))
                ReviewRow("Customer Number", tx.recipientNumber)
                ReviewRow("Withdrawal Amount", "GHS ${String.format(Locale.US, "%.2f", tx.amount)}")
            }

            // Network-specific instructions (AT Money vs Telecel kept strictly separate)
            EasyMoMoCard(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = if (network == NetworkType.AT) {
                        "AT Money Customer Instructions"
                    } else {
                        "Telecel Cash Customer Instructions"
                    },
                    style = MaterialTheme.typography.titleMedium,
                    color = EmeraldPrimary
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = if (network == NetworkType.AT) {
                        "1. Ask the customer to initiate AT Money Cash-Out on their own phone.\n" +
                            "2. Provide your verified AT Agent Code / Till Number.\n" +
                            "3. The customer enters the withdrawal amount and their own PIN on their device.\n" +
                            "4. Wait for the AT Money confirmation SMS before handing over cash."
                    } else {
                        "1. Ask the customer to initiate Telecel Cash withdrawal / voucher on their own phone.\n" +
                            "2. Provide your verified Telecel Agent ID where required.\n" +
                            "3. The customer authorizes the withdrawal with their own PIN on their device.\n" +
                            "4. Wait for the Telecel Cash confirmation SMS before handing over cash."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextPrimary
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Security Rule: Never ask for or enter the customer's PIN.",
                    style = MaterialTheme.typography.labelSmall,
                    color = StatusError,
                    fontWeight = FontWeight.Bold
                )
            }

            // 5-item Verification Checklist
            EasyMoMoCard(modifier = Modifier.fillMaxWidth()) {
                Text("Agent Verification Checklist", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                Spacer(modifier = Modifier.height(6.dp))
                ChecklistRow("Customer initiated the withdrawal", check1) { check1 = it }
                ChecklistRow("Customer completed authorization on own phone", check2) { check2 = it }
                ChecklistRow("Agent received or observed provider confirmation", check3) { check3 = it }
                ChecklistRow("Amount verified (GHS ${String.format(Locale.US, "%.2f", tx.amount)})", check4) { check4 = it }
                ChecklistRow("Recipient details verified (${tx.recipientNumber})", check5) { check5 = it }

                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = providerRef,
                    onValueChange = { providerRef = it },
                    label = { Text("Provider Reference / SMS ID (where available)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            TerminalActionButton(
                label = "Mark Verified Successful",
                enabled = allChecked,
                onClick = {
                    terminalViewModel.advanceCustomerLedToVerification(tx.localId)
                    terminalViewModel.markManualOutcome(
                        txLocalId = tx.localId,
                        status = TransactionStatus.SUCCESSFUL,
                        providerReference = providerRef,
                        notes = "Agent-verified customer-led ${network.displayName} cash-out"
                    )
                    onNavigateToResult()
                },
                modifier = Modifier.fillMaxWidth()
            )
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TerminalOutlinedButton(
                    label = "Mark Failed",
                    onClick = {
                        terminalViewModel.advanceCustomerLedToVerification(tx.localId)
                        terminalViewModel.markManualOutcome(
                            txLocalId = tx.localId,
                            status = TransactionStatus.FAILED,
                            providerReference = providerRef,
                            notes = "Customer-led cash-out failed"
                        )
                        onNavigateToResult()
                    },
                    modifier = Modifier.weight(1f)
                )
                TerminalOutlinedButton(
                    label = "Keep Awaiting",
                    onClick = {
                        terminalViewModel.advanceCustomerLedToVerification(tx.localId)
                        onNavigateToResult()
                    },
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun ChecklistRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(checked = checked, onCheckedChange = onCheckedChange)
        Text(label, style = MaterialTheme.typography.bodyMedium, color = TextPrimary)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionResultScreen(
    terminalViewModel: TerminalViewModel,
    onReturnToDashboard: () -> Unit,
    onViewHistory: () -> Unit,
    onStartAnother: () -> Unit
) {
    val uiState by terminalViewModel.uiState.collectAsState()
    val tx = uiState.activeTransaction
    val dateFmt = remember { SimpleDateFormat("dd MMM yyyy, HH:mm:ss", Locale.US) }

    Scaffold(
        containerColor = TerminalBackground,
        topBar = { TopAppBar(title = { Text("Transaction Result") }) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            if (tx == null) {
                Text("No transaction result available.")
            } else {
                EasyMoMoCard(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        NetworkBadge(NetworkType.fromCode(tx.network))
                        StatusPill(TransactionStatus.fromCode(tx.status))
                    }
                    Spacer(modifier = Modifier.height(14.dp))
                    ReviewRow("Transaction Type", TransactionType.fromCode(tx.transactionType).displayName)
                    ReviewRow("Recipient", tx.recipientNumber)
                    ReviewRow("Amount", "GHS ${String.format(Locale.US, "%.2f", tx.amount)}")
                    tx.providerReference?.let { ReviewRow("Provider Ref", it) }
                    ReviewRow("Confirmation Source", tx.confirmationSource ?: tx.processingMethod)
                    ReviewRow("Date & Time", dateFmt.format(Date(tx.updatedAt)))
                    tx.providerResponse?.let {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Provider Details:", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                        Text(it, style = MaterialTheme.typography.bodyMedium, color = TextPrimary)
                    }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                TerminalActionButton(
                    label = "Start Another Transaction",
                    onClick = {
                        terminalViewModel.resetUssdSession()
                        terminalViewModel.clearForm()
                        onStartAnother()
                    },
                    modifier = Modifier.fillMaxWidth()
                )
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TerminalOutlinedButton(
                        label = "View History",
                        onClick = {
                            terminalViewModel.resetUssdSession()
                            onViewHistory()
                        },
                        modifier = Modifier.weight(1f)
                    )
                    TerminalOutlinedButton(
                        label = "Dashboard",
                        onClick = {
                            terminalViewModel.resetUssdSession()
                            onReturnToDashboard()
                        },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

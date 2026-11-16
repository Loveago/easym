package com.easymomo.app.ui.customussd

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.easymomo.app.data.local.CustomUssdFlowEntity
import com.easymomo.app.data.local.UssdCaptureEventEntity
import com.easymomo.app.data.local.UssdCaptureSessionEntity
import com.easymomo.app.ussd.capture.CaptureConfidence
import com.easymomo.app.ussd.capture.CaptureSessionStatus
import com.easymomo.app.ussd.capture.CustomStepPlaceholderType

private val CUSTOM_NETWORKS = listOf("MTN", "TELECEL", "OTHER")
private val QUICK_USSD_CODES = listOf("*171#", "*110#", "*124#")
private val MANUAL_CATEGORIES = listOf("AGENT_SERVICE", "PAYMENT", "TRANSFER", "OTHER")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomUssdHubScreen(
    viewModel: CustomUssdViewModel,
    onBack: () -> Unit
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    val title = when (state.subScreen) {
        CustomUssdSubScreen.HUB -> "Custom USSD"
        CustomUssdSubScreen.CAPTURE_SETUP -> "Capture Setup"
        CustomUssdSubScreen.LIVE_CAPTURE -> "Live USSD Capture"
        CustomUssdSubScreen.CAPTURE_REVIEW -> "Capture Review"
        CustomUssdSubScreen.MANUAL_BUILDER -> "Build Manually"
        CustomUssdSubScreen.MY_FLOWS -> "My Custom Flows"
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title, fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = {
                        if (state.subScreen == CustomUssdSubScreen.HUB) {
                            onBack()
                        } else if (state.subScreen == CustomUssdSubScreen.LIVE_CAPTURE) {
                            // Keep live capture explicit via Finish/Cancel, or allow minimizing back to Hub
                            viewModel.navigateTo(CustomUssdSubScreen.HUB)
                        } else {
                            viewModel.navigateTo(CustomUssdSubScreen.HUB)
                        }
                    }) {
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
                .padding(horizontal = 16.dp)
        ) {
            state.statusMessage?.let { msg ->
                BannerCard(
                    text = msg,
                    containerColor = Color(0xFFE8F5E9),
                    contentColor = Color(0xFF1B5E20),
                    onDismiss = { viewModel.clearMessages() }
                )
                Spacer(Modifier.height(8.dp))
            }
            state.errorMessage?.let { err ->
                BannerCard(
                    text = err,
                    containerColor = Color(0xFFFFEBEE),
                    contentColor = Color(0xFFB71C1C),
                    onDismiss = { viewModel.clearMessages() }
                )
                Spacer(Modifier.height(8.dp))
            }

            when (state.subScreen) {
                CustomUssdSubScreen.HUB -> HubContent(
                    state = state,
                    onCaptureUssd = { viewModel.navigateTo(CustomUssdSubScreen.CAPTURE_SETUP) },
                    onBuildManually = { viewModel.navigateTo(CustomUssdSubScreen.MANUAL_BUILDER) },
                    onMyFlows = { viewModel.navigateTo(CustomUssdSubScreen.MY_FLOWS) },
                    onReviewSession = { viewModel.openCaptureReview(it) },
                    onDismissInterrupted = { viewModel.dismissInterruptedBanner(it) },
                    onResumeLive = { viewModel.navigateTo(CustomUssdSubScreen.LIVE_CAPTURE) }
                )

                CustomUssdSubScreen.CAPTURE_SETUP -> CaptureSetupContent(
                    state = state,
                    onFlowNameChange = viewModel::updateSetupFlowName,
                    onNetworkChange = viewModel::updateSetupNetwork,
                    onEntryCodeChange = viewModel::updateSetupEntryCode,
                    onOpenAccessibilitySettings = {
                        context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                    },
                    onRefreshChecks = viewModel::refreshCapturePrerequisites,
                    onStartCapture = viewModel::startCapture
                )

                CustomUssdSubScreen.LIVE_CAPTURE -> LiveCaptureContent(
                    state = state,
                    onPause = viewModel::pauseCapture,
                    onResume = viewModel::resumeCapture,
                    onFinish = viewModel::finishCapture,
                    onCancel = viewModel::cancelCapture
                )

                CustomUssdSubScreen.CAPTURE_REVIEW -> CaptureReviewContent(
                    state = state,
                    onFlowNameChange = viewModel::updateReviewFlowName,
                    onSaveDraft = viewModel::saveReviewedCaptureAsDraft,
                    onDiscard = viewModel::discardReviewedCapture
                )

                CustomUssdSubScreen.MANUAL_BUILDER -> ManualBuilderContent(
                    state = state,
                    onFlowNameChange = viewModel::updateManualFlowName,
                    onNetworkChange = viewModel::updateManualNetwork,
                    onEntryCodeChange = viewModel::updateManualEntryCode,
                    onDescriptionChange = viewModel::updateManualDescription,
                    onCategoryChange = viewModel::updateManualCategory,
                    onAddStep = viewModel::addManualPlaceholderStep,
                    onRemoveStep = viewModel::removeManualStep,
                    onSaveDraft = viewModel::saveManualDraft
                )

                CustomUssdSubScreen.MY_FLOWS -> MyCustomFlowsContent(
                    flows = state.customFlows,
                    onEditMetadata = viewModel::updateCustomFlowMetadata,
                    onDuplicate = viewModel::duplicateCustomFlow,
                    onToggleEnabled = viewModel::toggleCustomFlowEnabled,
                    onDelete = viewModel::deleteCustomFlow
                )
            }
        }
    }
}

@Composable
private fun HubContent(
    state: CustomUssdUiState,
    onCaptureUssd: () -> Unit,
    onBuildManually: () -> Unit,
    onMyFlows: () -> Unit,
    onReviewSession: (UssdCaptureSessionEntity) -> Unit,
    onDismissInterrupted: (String) -> Unit,
    onResumeLive: () -> Unit
) {
    val recentSessions = remember(state.captureSessions) { state.captureSessions.take(10) }

    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(vertical = 12.dp)
    ) {
        if (state.activeSession != null) {
            item(key = "active_session", contentType = "banner") {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFE3F2FD)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Capture in Progress", fontWeight = FontWeight.Bold)
                            Text(
                                "${state.activeSession.flowName} (${state.activeSession.entryCode})",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                        Button(onClick = onResumeLive) {
                            Text("Open")
                        }
                    }
                }
            }
        }

        if (state.recoveredInterruptedSessions.isNotEmpty()) {
            items(
                state.recoveredInterruptedSessions,
                key = { "rec_${it.id}" },
                contentType = { "recovered" }
            ) { session ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF8E1)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text(
                            "Recovered Interrupted Capture",
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFE65100)
                        )
                        Spacer(Modifier.height(4.dp))
                        Text("${session.flowName} • ${session.entryCode} (${session.capturedEventCount} events)")
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = { onReviewSession(session) }) {
                                Text("Review")
                            }
                            TextButton(onClick = { onDismissInterrupted(session.id) }) {
                                Text("Dismiss")
                            }
                        }
                    }
                }
            }
        }

        item(key = "action_capture", contentType = "action") {
            HubActionCard(
                title = "Capture USSD",
                subtitle = "Dial a real USSD code and automatically record prompts and menu steps.",
                icon = Icons.Default.FiberManualRecord,
                accentColor = Color(0xFF0B6E4F),
                onClick = onCaptureUssd
            )
        }

        item(key = "action_manual", contentType = "action") {
            HubActionCard(
                title = "Build Manually",
                subtitle = "Create a draft custom USSD flow structure with placeholder steps.",
                icon = Icons.Default.Build,
                accentColor = Color(0xFF1565C0),
                onClick = onBuildManually
            )
        }

        item(key = "action_flows", contentType = "action") {
            HubActionCard(
                title = "My Custom Flows (${state.customFlows.size})",
                subtitle = "View, organize, rename, duplicate, or manage your saved custom flows.",
                icon = Icons.AutoMirrored.Filled.List,
                accentColor = Color(0xFF4A148C),
                onClick = onMyFlows
            )
        }

        if (recentSessions.isNotEmpty()) {
            item(key = "recent_header", contentType = "header") {
                Spacer(Modifier.height(8.dp))
                Text(
                    "Recent Capture Sessions",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }
            items(recentSessions, key = { it.id }, contentType = { "session" }) { session ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onReviewSession(session) }
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(session.flowName, fontWeight = FontWeight.SemiBold)
                            Text(
                                "${session.network} • ${session.entryCode} • ${session.capturedEventCount} events",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        StatusChip(session.status)
                    }
                }
            }
        }
    }
}

@Composable
private fun HubActionCard(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    accentColor: Color,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .background(accentColor.copy(alpha = 0.12f), RoundedCornerShape(10.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = accentColor)
            }
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(2.dp))
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null)
        }
    }
}

@Composable
private fun CaptureSetupContent(
    state: CustomUssdUiState,
    onFlowNameChange: (String) -> Unit,
    onNetworkChange: (String) -> Unit,
    onEntryCodeChange: (String) -> Unit,
    onOpenAccessibilitySettings: () -> Unit,
    onRefreshChecks: () -> Unit,
    onStartCapture: () -> Unit
) {
    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(vertical = 12.dp)
    ) {
        item {
            OutlinedTextField(
                value = state.setupFlowName,
                onValueChange = onFlowNameChange,
                label = { Text("Flow Name") },
                placeholder = { Text("e.g. Merchant Payment or Bank Push") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        }

        item {
            Text("Network", fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CUSTOM_NETWORKS.forEach { net ->
                    FilterChip(
                        selected = state.setupNetwork == net,
                        onClick = { onNetworkChange(net) },
                        label = { Text(net) }
                    )
                }
            }
        }

        item {
            OutlinedTextField(
                value = state.setupEntryCode,
                onValueChange = onEntryCodeChange,
                label = { Text("USSD Entry Code") },
                placeholder = { Text("*171#") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                QUICK_USSD_CODES.forEach { quickCode ->
                    AssistChip(
                        onClick = { onEntryCodeChange(quickCode) },
                        label = { Text(quickCode) }
                    )
                }
            }
        }

        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("SIM & Accessibility Readiness", fontWeight = FontWeight.SemiBold)
                    Text("SIM (from Settings): ${state.resolvedSimLabel}", style = MaterialTheme.typography.bodySmall)
                    state.simWarning?.let {
                        Text(it, style = MaterialTheme.typography.bodySmall, color = Color(0xFFB71C1C))
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            if (state.accessibilityEnabled) "Accessibility Service: Enabled"
                            else "Accessibility Service: Disabled",
                            color = if (state.accessibilityEnabled) Color(0xFF1B5E20) else Color(0xFFB71C1C),
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.weight(1f)
                        )
                        if (!state.accessibilityEnabled) {
                            TextButton(onClick = onOpenAccessibilitySettings) {
                                Text("Enable")
                            }
                        }
                        IconButton(onClick = onRefreshChecks) {
                            Icon(Icons.Default.Refresh, contentDescription = "Refresh checks")
                        }
                    }
                }
            }
        }

        state.setupError?.let { err ->
            item {
                Text(err, color = Color(0xFFB71C1C), style = MaterialTheme.typography.bodySmall)
            }
        }

        item {
            Button(
                onClick = onStartCapture,
                modifier = Modifier.fillMaxWidth().height(50.dp)
            ) {
                Icon(Icons.Default.Phone, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Start Capture & Dial USSD")
            }
        }
    }
}

@Composable
private fun LiveCaptureContent(
    state: CustomUssdUiState,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onFinish: () -> Unit,
    onCancel: () -> Unit
) {
    val session = state.activeSession
    val isPaused = session?.status == CaptureSessionStatus.PAUSED.name
    val formattedTime = remember(state.elapsedSeconds) {
        val mins = state.elapsedSeconds / 60
        val secs = state.elapsedSeconds % 60
        "%02d:%02d".format(mins, secs)
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(session?.flowName ?: "Capture Session", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text("${session?.network ?: ""} • ${session?.entryCode ?: ""}", style = MaterialTheme.typography.bodySmall)
                    }
                    StatusChip(session?.status ?: CaptureSessionStatus.RECORDING.name)
                }
                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                    Text("Duration: $formattedTime", fontWeight = FontWeight.Medium)
                    Text("Events: ${state.liveEvents.size}", fontWeight = FontWeight.Medium)
                }
                Text(
                    "PINs, OTPs, and passwords are automatically excluded from capture.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            OutlinedButton(
                onClick = if (isPaused) onResume else onPause,
                modifier = Modifier.weight(1f)
            ) {
                Text(if (isPaused) "Resume" else "Pause")
            }
            Button(
                onClick = onFinish,
                modifier = Modifier.weight(1f)
            ) {
                Text("Finish Capture")
            }
        }

        TextButton(
            onClick = onCancel,
            modifier = Modifier.align(Alignment.End)
        ) {
            Text("Cancel & Discard", color = Color(0xFFB71C1C))
        }

        Text("Captured Timeline", fontWeight = FontWeight.SemiBold)
        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(state.liveEvents, key = { it.id }, contentType = { "capture_event" }) { event ->
                CaptureEventCard(event)
            }
        }
    }
}

@Composable
private fun CaptureReviewContent(
    state: CustomUssdUiState,
    onFlowNameChange: (String) -> Unit,
    onSaveDraft: () -> Unit,
    onDiscard: () -> Unit
) {
    val session = state.reviewSession
    Column(
        modifier = Modifier.fillMaxSize().padding(vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        OutlinedTextField(
            value = state.reviewFlowName,
            onValueChange = onFlowNameChange,
            label = { Text("Flow Name") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        if (session != null) {
            Text(
                "${session.network} • ${session.entryCode} • Status: ${session.status} • ${state.reviewEvents.size} events",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(state.reviewEvents, key = { it.id }, contentType = { "capture_event" }) { event ->
                CaptureEventCard(event)
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            OutlinedButton(onClick = onDiscard, modifier = Modifier.weight(1f)) {
                Text("Discard", color = Color(0xFFB71C1C))
            }
            Button(onClick = onSaveDraft, modifier = Modifier.weight(1f)) {
                Text("Save as Draft Flow")
            }
        }
    }
}

@Composable
private fun CaptureEventCard(event: UssdCaptureEventEntity) {
    val confidenceColor = when (event.confidence) {
        CaptureConfidence.HIGH.name -> Color(0xFF1B5E20)
        CaptureConfidence.MEDIUM.name -> Color(0xFFE65100)
        CaptureConfidence.SENSITIVE.name -> Color(0xFF6A1B9A)
        else -> Color(0xFF455A64)
    }
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "#${event.sequenceNumber} • ${event.eventType}",
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    event.confidence,
                    color = confidenceColor,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold
                )
            }
            if (event.isSensitive) {
                Text(
                    "[Sensitive input excluded — manual agent authorization]",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF6A1B9A)
                )
            } else if (event.promptText.isNotBlank()) {
                Text(event.promptText, style = MaterialTheme.typography.bodySmall)
            }
            event.selectedAction?.takeIf { it.isNotBlank() }?.let { action ->
                Text("Action: $action", style = MaterialTheme.typography.labelSmall, color = Color(0xFF0B6E4F))
            }
        }
    }
}

@Composable
private fun ManualBuilderContent(
    state: CustomUssdUiState,
    onFlowNameChange: (String) -> Unit,
    onNetworkChange: (String) -> Unit,
    onEntryCodeChange: (String) -> Unit,
    onDescriptionChange: (String) -> Unit,
    onCategoryChange: (String) -> Unit,
    onAddStep: (CustomStepPlaceholderType, String) -> Unit,
    onRemoveStep: (Int) -> Unit,
    onSaveDraft: () -> Unit
) {
    var newStepLabel by remember { mutableStateOf("") }

    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = PaddingValues(vertical = 12.dp)
    ) {
        item {
            OutlinedTextField(
                value = state.manualFlowName,
                onValueChange = onFlowNameChange,
                label = { Text("Flow Name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CUSTOM_NETWORKS.forEach { net ->
                    FilterChip(
                        selected = state.manualNetwork == net,
                        onClick = { onNetworkChange(net) },
                        label = { Text(net) }
                    )
                }
            }
        }
        item {
            OutlinedTextField(
                value = state.manualEntryCode,
                onValueChange = onEntryCodeChange,
                label = { Text("USSD Entry Code") },
                placeholder = { Text("*171#") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        }
        item {
            OutlinedTextField(
                value = state.manualDescription,
                onValueChange = onDescriptionChange,
                label = { Text("Service Description") },
                modifier = Modifier.fillMaxWidth()
            )
        }
        item {
            Text("Category", fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MANUAL_CATEGORIES.forEach { cat ->
                    FilterChip(
                        selected = state.manualCategory == cat,
                        onClick = { onCategoryChange(cat) },
                        label = { Text(cat) }
                    )
                }
            }
        }
        item {
            Text("Placeholder Steps (${state.manualSteps.size})", fontWeight = FontWeight.SemiBold)
        }
        items(state.manualSteps, key = { it.sequence }, contentType = { "manual_step" }) { step ->
            Card(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Step ${step.sequence}: ${step.stepType.name}", fontWeight = FontWeight.SemiBold)
                        Text(step.label, style = MaterialTheme.typography.bodySmall)
                    }
                    IconButton(onClick = { onRemoveStep(step.sequence) }) {
                        Icon(Icons.Default.Delete, contentDescription = "Remove step")
                    }
                }
            }
        }
        item {
            OutlinedTextField(
                value = newStepLabel,
                onValueChange = { newStepLabel = it },
                label = { Text("New Placeholder Step Label") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = {
                    onAddStep(CustomStepPlaceholderType.MENU_SELECTION, newStepLabel)
                    newStepLabel = ""
                }) { Text("+ Menu") }
                OutlinedButton(onClick = {
                    onAddStep(CustomStepPlaceholderType.CUSTOMER_NUMBER_PROMPT, newStepLabel)
                    newStepLabel = ""
                }) { Text("+ Input") }
                OutlinedButton(onClick = {
                    onAddStep(CustomStepPlaceholderType.MANUAL_AUTHORIZATION, newStepLabel)
                    newStepLabel = ""
                }) { Text("+ PIN/Auth") }
            }
        }
        state.manualError?.let { err ->
            item { Text(err, color = Color(0xFFB71C1C), style = MaterialTheme.typography.bodySmall) }
        }
        item {
            Button(onClick = onSaveDraft, modifier = Modifier.fillMaxWidth().height(50.dp)) {
                Text("Save Draft Flow")
            }
        }
    }
}

@Composable
private fun MyCustomFlowsContent(
    flows: List<CustomUssdFlowEntity>,
    onEditMetadata: (String, String, String, String) -> Unit,
    onDuplicate: (String) -> Unit,
    onToggleEnabled: (String, Boolean) -> Unit,
    onDelete: (String) -> Unit
) {
    var editingFlow by remember { mutableStateOf<CustomUssdFlowEntity?>(null) }

    if (flows.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                "No custom flows yet. Use Capture USSD or Build Manually to create one.",
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    } else {
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(vertical = 12.dp)
        ) {
            items(flows, key = { it.id }, contentType = { "custom_flow" }) { flow ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(flow.flowName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                val origin = if (flow.sourceCaptureSessionId != null) "CAPTURED" else "MANUAL"
                                Text(
                                    "${flow.network} • ${flow.entryCode} • $origin • ${flow.stepCount} steps",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = flow.enabled,
                                onCheckedChange = { onToggleEnabled(flow.id, it) }
                            )
                        }
                        if (flow.serviceDescription.isNotBlank()) {
                            Text(flow.serviceDescription, style = MaterialTheme.typography.bodySmall)
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            StatusChip(flow.status)
                            Spacer(Modifier.weight(1f))
                            TextButton(onClick = { editingFlow = flow }) { Text("Edit") }
                            TextButton(onClick = { onDuplicate(flow.id) }) { Text("Duplicate") }
                            TextButton(onClick = { onDelete(flow.id) }) {
                                Text("Delete", color = Color(0xFFB71C1C))
                            }
                        }
                    }
                }
            }
        }
    }

    editingFlow?.let { flow ->
        var name by remember(flow.id) { mutableStateOf(flow.flowName) }
        var desc by remember(flow.id) { mutableStateOf(flow.serviceDescription) }
        var category by remember(flow.id) { mutableStateOf(flow.transactionCategory) }

        AlertDialog(
            onDismissRequest = { editingFlow = null },
            title = { Text("Edit Custom Flow") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Flow Name") })
                    OutlinedTextField(value = desc, onValueChange = { desc = it }, label = { Text("Description") })
                    OutlinedTextField(value = category, onValueChange = { category = it }, label = { Text("Category") })
                }
            },
            confirmButton = {
                Button(onClick = {
                    onEditMetadata(flow.id, name, desc, category)
                    editingFlow = null
                }) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { editingFlow = null }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun StatusChip(status: String) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.secondaryContainer
    ) {
        Text(
            text = status,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun BannerCard(
    text: String,
    containerColor: Color,
    contentColor: Color,
    onDismiss: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = containerColor),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text, color = contentColor, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
            IconButton(onClick = onDismiss, modifier = Modifier.size(24.dp)) {
                Icon(Icons.Default.Close, contentDescription = "Dismiss", tint = contentColor)
            }
        }
    }
}

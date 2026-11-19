package com.easymomo.app.ui.customussd

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.easymomo.app.ui.theme.EasyMoMoCard
import com.easymomo.app.ui.theme.EmeraldPrimary
import com.easymomo.app.ui.theme.EmeraldPrimaryContainer
import com.easymomo.app.ui.theme.OnEmeraldPrimaryContainer
import com.easymomo.app.ui.theme.StatusError
import com.easymomo.app.ui.theme.StatusErrorBg
import com.easymomo.app.ui.theme.StatusSuccess
import com.easymomo.app.ui.theme.StatusSuccessBg
import com.easymomo.app.ui.theme.TerminalBackground
import com.easymomo.app.ui.theme.TerminalBorder
import com.easymomo.app.ui.theme.TerminalSurface
import com.easymomo.app.ui.theme.TerminalSurfaceVariant
import com.easymomo.app.ui.theme.TextMuted
import com.easymomo.app.ui.theme.TextPrimary
import com.easymomo.app.ui.theme.TextSecondary
import com.easymomo.app.ussd.capture.CustomUssdFlowBuilder
import com.easymomo.app.ussd.capture.CustomUssdOperationType
import com.easymomo.app.ussd.capture.SimpleCustomUssdFlow
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun CustomUssdHubScreen(
    viewModel: CustomUssdViewModel,
    onBack: () -> Unit
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val scrollState = rememberScrollState()
    val scope = rememberCoroutineScope()
    var showAddStepDialog by remember { mutableStateOf(false) }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.refreshActiveSims()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Scaffold(
        containerColor = TerminalBackground,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Custom USSD",
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = Color.White
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = EmeraldPrimary,
                    titleContentColor = Color.White
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .navigationBarsPadding()
                .verticalScroll(scrollState)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = "Create a custom USSD automation that simplifies your work.",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
                Text(
                    text = "For security reasons, do not create custom USSDs containing sensitive PIN codes.",
                    style = MaterialTheme.typography.bodySmall,
                    color = StatusError,
                    fontWeight = FontWeight.Medium
                )
            }

            state.statusMessage?.let { msg ->
                BannerMessageCard(
                    text = msg,
                    backgroundColor = StatusSuccessBg,
                    textColor = StatusSuccess,
                    onDismiss = viewModel::clearMessages
                )
            }

            (state.builderError ?: state.errorMessage)?.let { err ->
                BannerMessageCard(
                    text = err,
                    backgroundColor = StatusErrorBg,
                    textColor = StatusError,
                    onDismiss = {
                        viewModel.clearMessages()
                    }
                )
            }

            // Automation Name
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = "Automation Name:",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = TextPrimary
                )
                OutlinedTextField(
                    value = state.builderName,
                    onValueChange = viewModel::updateBuilderName,
                    placeholder = { Text("e.g. MTN EVD or Telecel Cashout") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // Operation Type
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = "Operation Type:",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = TextPrimary
                )
                Text(
                    text = "Choose how the USSD operates. Step by Step responds to menu prompts, while One time dials a single formatted code.",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSecondary
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CustomUssdOperationType.entries.forEach { opType ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .weight(1f)
                                .clickable { viewModel.updateBuilderOperationType(opType) }
                                .padding(vertical = 4.dp)
                        ) {
                            RadioButton(
                                selected = state.builderOperationType == opType,
                                onClick = { viewModel.updateBuilderOperationType(opType) },
                                colors = RadioButtonDefaults.colors(selectedColor = EmeraldPrimary)
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                text = opType.label,
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextPrimary
                            )
                        }
                    }
                }
            }

            if (state.builderOperationType == CustomUssdOperationType.STEP_BY_STEP) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "USSD Step Builder:",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = TextPrimary
                    )
                    Text(
                        text = "Add your USSD steps in order (start with a USSD code like *171#). Use ▲ and ▼ to reorder steps, or ✕ to remove.",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextSecondary
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            onClick = { showAddStepDialog = true },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = EmeraldPrimary,
                                contentColor = Color.White
                            )
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Add Step", fontWeight = FontWeight.SemiBold)
                        }
                        OutlinedButton(
                            onClick = viewModel::clearBuilderSteps,
                            enabled = state.builderSteps.isNotEmpty(),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("Clear All", color = if (state.builderSteps.isNotEmpty()) StatusError else TextMuted)
                        }
                    }

                    if (state.builderSteps.isEmpty()) {
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(8.dp),
                            color = TerminalSurfaceVariant,
                            border = BorderStroke(1.dp, TerminalBorder)
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 18.dp, horizontal = 12.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "No steps added yet. Tap '+ Add Step' to begin.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextMuted
                                )
                            }
                        }
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            state.builderSteps.forEachIndexed { index, stepToken ->
                                StepRowCard(
                                    stepNumber = index + 1,
                                    token = stepToken,
                                    canMoveUp = index > 0,
                                    canMoveDown = index < state.builderSteps.lastIndex,
                                    onMoveUp = { viewModel.moveBuilderStepUp(index) },
                                    onMoveDown = { viewModel.moveBuilderStepDown(index) },
                                    onRemove = { viewModel.removeBuilderStep(index) }
                                )
                            }
                        }
                    }
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "One-Time USSD Code:",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = TextPrimary
                    )
                    Text(
                        text = "Tap a quick variable chip below to insert customer phone number or amount into your USSD code.",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextSecondary
                    )
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        CustomUssdFlowBuilder.QUICK_VARIABLES.forEach { variable ->
                            OutlinedButton(
                                onClick = { viewModel.appendOneTimeVariable(variable) },
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text(variable, color = EmeraldPrimary, fontWeight = FontWeight.Medium)
                            }
                        }
                    }
                    OutlinedTextField(
                        value = state.builderOneTimeCode,
                        onValueChange = viewModel::updateBuilderOneTimeCode,
                        placeholder = { Text("e.g. *124# or *171*1*<phoneNumber>*<amount>#") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            // Combined USSD Formula Preview
            val previewText = CustomUssdFlowBuilder.formatPreview(
                operationType = state.builderOperationType,
                steps = state.builderSteps,
                oneTimeCode = state.builderOneTimeCode
            )
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp),
                color = EmeraldPrimaryContainer.copy(alpha = 0.55f),
                border = BorderStroke(1.dp, EmeraldPrimary.copy(alpha = 0.35f))
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = "Combined USSD Formula Preview:",
                        style = MaterialTheme.typography.labelSmall,
                        color = EmeraldPrimary,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = previewText,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = OnEmeraldPrimaryContainer
                    )
                }
            }

            // SIM Slot Selector
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = "SIM Slot:",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = TextPrimary
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    listOf(0, 1).forEach { slotIdx ->
                        val activeSim = state.activeSims.firstOrNull { it.slotIndex == slotIdx }
                        val slotLabel = if (activeSim != null) {
                            val carrier = activeSim.carrierName.ifBlank { activeSim.displayName }
                            "SIM ${slotIdx + 1} ($carrier)"
                        } else {
                            "SIM ${slotIdx + 1}"
                        }
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .weight(1f)
                                .clickable { viewModel.selectBuilderSimSlot(slotIdx) }
                                .padding(vertical = 4.dp)
                        ) {
                            RadioButton(
                                selected = state.builderSimSlotIndex == slotIdx,
                                onClick = { viewModel.selectBuilderSimSlot(slotIdx) },
                                colors = RadioButtonDefaults.colors(selectedColor = EmeraldPrimary)
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                text = slotLabel,
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextPrimary
                            )
                        }
                    }
                }
            }

            // Save / Update button
            Button(
                onClick = { viewModel.saveBuilderFlow() },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = EmeraldPrimary,
                    contentColor = Color.White
                )
            ) {
                Text(
                    text = if (state.editingFlowId != null) "UPDATE CUSTOM USSD" else "SAVE CUSTOM USSD",
                    fontWeight = FontWeight.Bold
                )
            }

            if (state.editingFlowId != null) {
                TextButton(
                    onClick = viewModel::resetBuilder,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                ) {
                    Text("Cancel Edit")
                }
            }

            // Saved Custom USSDs list
            if (state.simpleFlows.isNotEmpty()) {
                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp), color = TerminalBorder)
                Text(
                    text = "Saved Custom USSDs (${state.simpleFlows.size})",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
                state.simpleFlows.forEach { saved ->
                    SavedCustomUssdCard(
                        flow = saved,
                        onEdit = {
                            viewModel.startEditingSimpleFlow(saved)
                            scope.launch { scrollState.animateScrollTo(0) }
                        },
                        onDelete = { viewModel.deleteSimpleFlow(saved.id) }
                    )
                }
            }
        }
    }

    if (showAddStepDialog) {
        AddUssdStepDialog(
            onDismiss = { showAddStepDialog = false },
            onAddStep = { stepValue ->
                if (viewModel.addBuilderStep(stepValue)) {
                    showAddStepDialog = false
                }
            }
        )
    }
}

@Composable
private fun StepRowCard(
    stepNumber: Int,
    token: String,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onRemove: () -> Unit
) {
    val isVariable = token.equals(CustomUssdFlowBuilder.VAR_PHONE, ignoreCase = true) ||
        token.equals(CustomUssdFlowBuilder.VAR_AMOUNT, ignoreCase = true)

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        color = TerminalSurface,
        border = BorderStroke(1.dp, TerminalBorder)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = RoundedCornerShape(6.dp),
                color = EmeraldPrimaryContainer
            ) {
                Text(
                    text = "Step $stepNumber",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = OnEmeraldPrimaryContainer,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
            Spacer(Modifier.width(10.dp))
            Box(modifier = Modifier.weight(1f)) {
                if (isVariable) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = EmeraldPrimaryContainer.copy(alpha = 0.65f)
                    ) {
                        Text(
                            text = token,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            color = EmeraldPrimary,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                } else {
                    Text(
                        text = token,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = TextPrimary
                    )
                }
            }
            IconButton(onClick = onMoveUp, enabled = canMoveUp, modifier = Modifier.size(32.dp)) {
                Icon(
                    imageVector = Icons.Default.KeyboardArrowUp,
                    contentDescription = "Move up",
                    tint = if (canMoveUp) EmeraldPrimary else TextMuted
                )
            }
            IconButton(onClick = onMoveDown, enabled = canMoveDown, modifier = Modifier.size(32.dp)) {
                Icon(
                    imageVector = Icons.Default.KeyboardArrowDown,
                    contentDescription = "Move down",
                    tint = if (canMoveDown) EmeraldPrimary else TextMuted
                )
            }
            IconButton(onClick = onRemove, modifier = Modifier.size(32.dp)) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Remove step",
                    tint = StatusError
                )
            }
        }
    }
}

@Composable
private fun AddUssdStepDialog(
    onDismiss: () -> Unit,
    onAddStep: (String) -> Unit
) {
    var customInput by remember { mutableStateOf("") }
    var localError by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Add USSD Step",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = EmeraldPrimary
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "Tap a preset variable chip below OR type a USSD code/response number.",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )

                Text(
                    text = "Quick Variable Chips:",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = TextPrimary
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    CustomUssdFlowBuilder.QUICK_VARIABLES.forEach { variable ->
                        OutlinedButton(
                            onClick = { onAddStep(variable) },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(
                                text = variable,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = EmeraldPrimary
                            )
                        }
                    }
                }

                Text(
                    text = "Or Type Custom Step:",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = TextPrimary
                )
                OutlinedTextField(
                    value = customInput,
                    onValueChange = {
                        customInput = it
                        localError = null
                    },
                    placeholder = { Text("e.g. *171# or 1 or 2") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                localError?.let { err ->
                    Text(
                        text = err,
                        style = MaterialTheme.typography.labelSmall,
                        color = StatusError
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val trimmed = customInput.trim()
                    when {
                        trimmed.isEmpty() ->
                            localError = "Enter a step value or tap a quick variable chip."
                        CustomUssdFlowBuilder.containsSensitivePinHint(trimmed) ->
                            localError = "For security reasons, do not enter PIN codes in custom USSD steps."
                        else -> onAddStep(trimmed)
                    }
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = EmeraldPrimary,
                    contentColor = Color.White
                )
            ) {
                Text("ADD STEP", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
private fun SavedCustomUssdCard(
    flow: SimpleCustomUssdFlow,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    EasyMoMoCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
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
                    color = TextPrimary
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
                    color = TextSecondary
                )
            }
            Row {
                IconButton(onClick = onEdit) {
                    Icon(Icons.Default.Edit, contentDescription = "Edit", tint = EmeraldPrimary)
                }
                IconButton(onClick = onDelete) {
                    Icon(Icons.Default.Delete, contentDescription = "Delete", tint = StatusError)
                }
            }
        }
    }
}

@Composable
private fun BannerMessageCard(
    text: String,
    backgroundColor: Color,
    textColor: Color,
    onDismiss: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        color = backgroundColor
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.bodySmall,
                color = textColor,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                Icon(Icons.Default.Close, contentDescription = "Dismiss", tint = textColor, modifier = Modifier.size(16.dp))
            }
        }
    }
}

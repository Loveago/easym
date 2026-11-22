package com.easymomo.app.ui.terminal

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Badge
import androidx.compose.material.icons.outlined.Storefront
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.easymomo.app.ui.theme.AmberAccent
import com.easymomo.app.ui.theme.EasyMoMoCard
import com.easymomo.app.ui.theme.EmeraldPrimary
import com.easymomo.app.ui.theme.EmeraldPrimaryContainer
import com.easymomo.app.ui.theme.OnEmeraldPrimaryContainer
import com.easymomo.app.ui.theme.StatusError
import com.easymomo.app.ui.theme.StatusErrorBg
import com.easymomo.app.ui.theme.TabularAmountStyle
import com.easymomo.app.ui.theme.TabularPhoneStyle
import com.easymomo.app.ui.theme.TerminalActionButton
import com.easymomo.app.ui.theme.TerminalBackground
import com.easymomo.app.ui.theme.TerminalBorder
import com.easymomo.app.ui.theme.TerminalSurface
import com.easymomo.app.ui.theme.TextPrimary
import com.easymomo.app.ui.theme.TextSecondary
import com.easymomo.app.ussd.model.PresetUssdFlow
import com.easymomo.app.ussd.model.PresetUssdFlows
import java.text.DecimalFormat

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BusWithdrawalScreen(
    terminalViewModel: TerminalViewModel,
    onOpenSimAndDiagnostics: () -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val uiState by terminalViewModel.uiState.collectAsState()
    val amountFmt = remember { DecimalFormat("#,##0.00") }

    var amountText by rememberSaveable { mutableStateOf(uiState.amountText) }
    var codeOrTillText by rememberSaveable { mutableStateOf("") }
    var chosenSimId by rememberSaveable { mutableStateOf(uiState.selectedSimId) }
    var localError by remember { mutableStateOf<String?>(null) }
    var pendingCallAction by remember { mutableStateOf<(() -> Unit)?>(null) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            pendingCallAction?.invoke()
        } else {
            localError = "CALL_PHONE permission is required to dial USSD codes."
        }
        pendingCallAction = null
    }

    val runWithCallPermission: (() -> Unit) -> Unit = { action ->
        val granted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.CALL_PHONE
        ) == PackageManager.PERMISSION_GRANTED
        if (granted) {
            action()
        } else {
            pendingCallAction = action
            permissionLauncher.launch(Manifest.permission.CALL_PHONE)
        }
    }

    val launchBusWithdrawal: (PresetUssdFlow) -> Unit = { flow ->
        val err = terminalViewModel.runPresetUssdFlow(
            preset = flow,
            overrideAmount = amountText,
            overrideCodeOrTill = codeOrTillText,
            overrideSimId = chosenSimId
        )
        localError = err
    }

    Scaffold(
        containerColor = TerminalBackground,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Bus Withdrawal",
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
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                text = "Telecel Business Withdrawal (*110#)",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )
            Text(
                text = "Enter the withdrawal amount and Agent Code or Till Number, then choose Agent Code or Till Number below.",
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary
            )

            localError?.let { err ->
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(StatusErrorBg)
                        .padding(12.dp)
                ) {
                    Text(
                        text = err,
                        color = StatusError,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }

            EasyMoMoCard(modifier = Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = "Amount (GHS)",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = EmeraldPrimary
                        )
                        OutlinedTextField(
                            value = amountText,
                            onValueChange = { raw ->
                                amountText = raw.filter { it.isDigit() || it == '.' }
                                localError = null
                            },
                            placeholder = { Text("0.00", style = TabularAmountStyle) },
                            singleLine = true,
                            textStyle = TabularAmountStyle.copy(color = TextPrimary),
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Decimal,
                                imeAction = ImeAction.Next
                            ),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = EmeraldPrimary,
                                unfocusedBorderColor = TerminalBorder,
                                focusedContainerColor = TerminalSurface,
                                unfocusedContainerColor = TerminalSurface
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = "Code or Till Number",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = EmeraldPrimary
                        )
                        OutlinedTextField(
                            value = codeOrTillText,
                            onValueChange = { raw ->
                                codeOrTillText = raw.filter { it.isLetterOrDigit() }
                                localError = null
                            },
                            placeholder = { Text("Enter Agent Code or Till Number") },
                            singleLine = true,
                            textStyle = TabularPhoneStyle.copy(color = TextPrimary),
                            keyboardOptions = KeyboardOptions(
                                capitalization = KeyboardCapitalization.Characters,
                                keyboardType = KeyboardType.Text,
                                imeAction = ImeAction.Done
                            ),
                            supportingText = {
                                Text(
                                    text = "Agent Code: letters & numbers • Till Number: numbers only",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = TextSecondary
                                )
                            },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = EmeraldPrimary,
                                unfocusedBorderColor = TerminalBorder,
                                focusedContainerColor = TerminalSurface,
                                unfocusedContainerColor = TerminalSurface
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    if (uiState.activeSims.isNotEmpty()) {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                text = "Run with SIM",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = EmeraldPrimary
                            )
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                FilterChip(
                                    selected = chosenSimId == null,
                                    onClick = { chosenSimId = null },
                                    label = { Text("Auto (Telecel SIM)") },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = EmeraldPrimaryContainer,
                                        selectedLabelColor = OnEmeraldPrimaryContainer
                                    )
                                )
                                uiState.activeSims.forEach { sim ->
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
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                TerminalActionButton(
                    label = "AGENT CODE",
                    icon = Icons.Outlined.Badge,
                    onClick = {
                        runWithCallPermission {
                            launchBusWithdrawal(PresetUssdFlows.BUS_WITHDRAWAL_AGENT_CODE)
                        }
                    },
                    modifier = Modifier.weight(1f)
                )
                TerminalActionButton(
                    label = "TILL NUMBER",
                    icon = Icons.Outlined.Storefront,
                    containerColor = AmberAccent,
                    onClick = {
                        runWithCallPermission {
                            launchBusWithdrawal(PresetUssdFlows.BUS_WITHDRAWAL_TILL_NUMBER)
                        }
                    },
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(8.dp))
        }
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
}

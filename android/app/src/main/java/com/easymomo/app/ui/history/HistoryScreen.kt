package com.easymomo.app.ui.history

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.ReceiptLong
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.easymomo.app.data.local.TransactionEntity
import com.easymomo.app.domain.model.NetworkType
import com.easymomo.app.domain.model.TransactionStatus
import com.easymomo.app.domain.model.TransactionType
import com.easymomo.app.ui.theme.EasyMoMoCard
import com.easymomo.app.ui.theme.EmeraldPrimary
import com.easymomo.app.ui.theme.EmeraldPrimaryContainer
import com.easymomo.app.ui.theme.EmptyStateView
import com.easymomo.app.ui.theme.NetworkBadge
import com.easymomo.app.ui.theme.OnEmeraldPrimaryContainer
import com.easymomo.app.ui.theme.StatusError
import com.easymomo.app.ui.theme.StatusPill
import com.easymomo.app.ui.theme.StatusSuccess
import com.easymomo.app.ui.theme.StatusWarning
import com.easymomo.app.ui.theme.TerminalBackground
import com.easymomo.app.ui.theme.TerminalBorder
import com.easymomo.app.ui.theme.TextPrimary
import com.easymomo.app.ui.theme.TextSecondary
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val NETWORK_FILTERS = listOf(NetworkType.MTN, NetworkType.AT, NetworkType.TELECEL)
private val STATUS_FILTERS = listOf(
    TransactionStatus.SUCCESSFUL,
    TransactionStatus.FAILED,
    TransactionStatus.UNKNOWN,
    TransactionStatus.AWAITING_VERIFICATION
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    viewModel: HistoryReportsViewModel,
    onBack: () -> Unit
) {
    val filter by viewModel.filter.collectAsStateWithLifecycle()
    val transactions by viewModel.filteredTransactions.collectAsStateWithLifecycle()
    val pendingSync by viewModel.pendingSyncCount.collectAsStateWithLifecycle()
    val syncMsg by viewModel.syncMessage.collectAsStateWithLifecycle()
    val todayBreakdown by viewModel.todayBreakdown.collectAsStateWithLifecycle()

    var selectedTx by remember { mutableStateOf<TransactionEntity?>(null) }
    var showTodaySummary by remember { mutableStateOf(true) }
    val dateFmt = remember { SimpleDateFormat("dd MMM, HH:mm", Locale.US) }
    val amountFmt = remember { DecimalFormat("#,##0.00", DecimalFormatSymbols(Locale.US)) }

    Scaffold(
        containerColor = TerminalBackground,
        topBar = {
            TopAppBar(
                title = { Text("Transaction History") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = viewModel::syncNow) {
                        Icon(Icons.Outlined.Sync, contentDescription = "Sync ($pendingSync pending)")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 14.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            syncMsg?.let { msg ->
                Text(msg, style = MaterialTheme.typography.labelSmall, color = EmeraldPrimary)
            }

            // Smart Today's Summary (MTN, Telecel, etc.)
            EasyMoMoCard(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Today's Summary",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                        Text(
                            text = "GHS ${amountFmt.format(todayBreakdown.totalAmount)} • ${todayBreakdown.successfulCount} successful" +
                                if (todayBreakdown.pendingCount > 0) " • ${todayBreakdown.pendingCount} pending" else "" +
                                    if (todayBreakdown.failedCount > 0) " • ${todayBreakdown.failedCount} failed" else "",
                            style = MaterialTheme.typography.labelSmall,
                            color = EmeraldPrimary,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    TextButton(onClick = { showTodaySummary = !showTodaySummary }) {
                        Text(
                            text = if (showTodaySummary) "Hide" else "Show",
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                }

                if (showTodaySummary && todayBreakdown.networks.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        todayBreakdown.networks.forEach { netSummary ->
                            val isFiltered = filter.network == netSummary.network.code
                            Surface(
                                modifier = Modifier
                                    .width(172.dp)
                                    .clickable {
                                        viewModel.updateNetworkFilter(
                                            if (isFiltered) null else netSummary.network.code
                                        )
                                    },
                                shape = RoundedCornerShape(10.dp),
                                color = if (isFiltered) EmeraldPrimaryContainer else TerminalBackground,
                                border = BorderStroke(
                                    1.dp,
                                    if (isFiltered) EmeraldPrimary else TerminalBorder
                                )
                            ) {
                                Column(
                                    modifier = Modifier.padding(10.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        NetworkBadge(network = netSummary.network)
                                        Text(
                                            text = "${netSummary.totalCount} tx",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = if (isFiltered) OnEmeraldPrimaryContainer else TextSecondary
                                        )
                                    }
                                    Text(
                                        text = "GHS ${amountFmt.format(netSummary.totalAmount)}",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isFiltered) OnEmeraldPrimaryContainer else TextPrimary
                                    )
                                    HorizontalDivider(color = TerminalBorder)
                                    SummaryMetricRow(
                                        label = "Cash In",
                                        value = "GHS ${amountFmt.format(netSummary.cashInAmount)} (${netSummary.cashInCount})"
                                    )
                                    SummaryMetricRow(
                                        label = "Cash Out",
                                        value = "GHS ${amountFmt.format(netSummary.cashOutAmount)} (${netSummary.cashOutCount})"
                                    )
                                    SummaryMetricRow(
                                        label = "Data/Air",
                                        value = "GHS ${amountFmt.format(netSummary.airtimeDataAmount)} (${netSummary.airtimeDataCount})"
                                    )
                                }
                            }
                        }
                    }
                }
            }

            OutlinedTextField(
                value = filter.query,
                onValueChange = viewModel::updateQuery,
                leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                placeholder = { Text("Search phone, reference, or ID...") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            // Network filter chips
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                FilterChip(
                    selected = filter.network == null,
                    onClick = { viewModel.updateNetworkFilter(null) },
                    label = { Text("All Networks") }
                )
                NETWORK_FILTERS.forEach { net ->
                    FilterChip(
                        selected = filter.network == net.code,
                        onClick = { viewModel.updateNetworkFilter(if (filter.network == net.code) null else net.code) },
                        label = { Text(net.shortName) }
                    )
                }
            }

            // Status filter chips
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                STATUS_FILTERS.forEach { st ->
                    FilterChip(
                        selected = filter.status == st.code,
                        onClick = { viewModel.updateStatusFilter(if (filter.status == st.code) null else st.code) },
                        label = { Text(st.displayName) }
                    )
                }
            }

            if (transactions.isEmpty()) {
                EmptyStateView(
                    icon = Icons.Outlined.ReceiptLong,
                    title = "No Transactions Found",
                    subtitle = "Transactions are stored locally offline and retained even if your subscription expires."
                )
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(
                        transactions,
                        key = { it.localId },
                        contentType = { "transaction" }
                    ) { tx ->
                        HistoryTransactionCard(
                            tx = tx,
                            dateFmt = dateFmt,
                            amountFmt = amountFmt,
                            onClick = { selectedTx = tx }
                        )
                    }
                }
            }
        }
    }

    selectedTx?.let { tx ->
        val eventsFlow = remember(tx.localId) { viewModel.observeEvents(tx.localId) }
        val events by eventsFlow.collectAsStateWithLifecycle(initialValue = emptyList())
        ModalBottomSheet(onDismissRequest = { selectedTx = null }) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text("Transaction Audit Trail", style = MaterialTheme.typography.titleLarge)
                Text("Local ID: ${tx.localId}", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                Text("Method: ${tx.processingMethod}", style = MaterialTheme.typography.bodyMedium)
                tx.providerResponse?.let {
                    Text("Provider Response: $it", style = MaterialTheme.typography.bodyMedium)
                }
                HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp))
                Text("Update Status", style = MaterialTheme.typography.titleMedium)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (tx.status != TransactionStatus.FAILED.name) {
                        Button(
                            onClick = {
                                viewModel.markTransactionStatus(tx.localId, TransactionStatus.FAILED)
                                selectedTx = null
                            },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFFDC2626),
                                contentColor = Color.White
                            )
                        ) {
                            Text("Mark Failed", maxLines = 1)
                        }
                    }
                    if (tx.status != TransactionStatus.CANCELLED.name) {
                        OutlinedButton(
                            onClick = {
                                viewModel.markTransactionStatus(tx.localId, TransactionStatus.CANCELLED)
                                selectedTx = null
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Mark Cancelled", maxLines = 1)
                        }
                    }
                    if (tx.status != TransactionStatus.SUCCESSFUL.name) {
                        Button(
                            onClick = {
                                viewModel.markTransactionStatus(tx.localId, TransactionStatus.SUCCESSFUL)
                                selectedTx = null
                            },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = EmeraldPrimary,
                                contentColor = Color.White
                            )
                        ) {
                            Text("Mark Successful", maxLines = 1)
                        }
                    }
                }
                HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp))
                Text("Events", style = MaterialTheme.typography.titleMedium)
                events.forEach { ev ->
                    val evTime = remember(ev.createdAt) { dateFmt.format(Date(ev.createdAt)) }
                    Text(
                        text = "• ${ev.newStatus} ($evTime): ${ev.details.orEmpty()}",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )
                }
                Spacer(modifier = Modifier.height(20.dp))
            }
        }
    }
}

@Composable
private fun SummaryMetricRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = TextSecondary
        )
        Text(
            text = value,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Medium,
            color = TextPrimary
        )
    }
}

@Composable
private fun HistoryTransactionCard(
    tx: TransactionEntity,
    dateFmt: SimpleDateFormat,
    amountFmt: DecimalFormat,
    onClick: () -> Unit
) {
    val networkType = remember(tx.network) { NetworkType.fromCode(tx.network) }
    val txTypeName = remember(tx.transactionType) { TransactionType.fromCode(tx.transactionType).displayName }
    val txStatus = remember(tx.status) { TransactionStatus.fromCode(tx.status) }
    val formattedAmount = remember(tx.amount) { "GHS ${amountFmt.format(tx.amount)}" }
    val formattedMeta = remember(tx.createdAt, tx.providerReference) {
        "${dateFmt.format(Date(tx.createdAt))} • Ref: ${tx.providerReference ?: "—"}"
    }

    EasyMoMoCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                NetworkBadge(networkType)
                Text(
                    txTypeName,
                    style = MaterialTheme.typography.titleMedium
                )
            }
            StatusPill(txStatus)
        }
        Spacer(modifier = Modifier.height(6.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(tx.recipientNumber, style = MaterialTheme.typography.bodyMedium, color = TextPrimary)
            Text(
                formattedAmount,
                style = MaterialTheme.typography.titleMedium,
                color = EmeraldPrimary
            )
        }
        Text(
            text = formattedMeta,
            style = MaterialTheme.typography.labelSmall,
            color = TextSecondary
        )
    }
}

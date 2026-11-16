package com.easymomo.app.ui.history

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.easymomo.app.ui.theme.EasyMoMoCard
import com.easymomo.app.ui.theme.EmeraldPrimary
import com.easymomo.app.ui.theme.NetworkBadge
import com.easymomo.app.ui.theme.TerminalActionButton
import com.easymomo.app.ui.theme.TerminalBackground
import com.easymomo.app.ui.theme.TerminalOutlinedButton
import com.easymomo.app.ui.theme.TextPrimary
import com.easymomo.app.ui.theme.TextSecondary
import kotlinx.coroutines.launch
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportsScreen(
    viewModel: HistoryReportsViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val selectedPeriod by viewModel.reportPeriod.collectAsStateWithLifecycle()
    val summary by viewModel.reportSummary.collectAsStateWithLifecycle()
    val amountFmt = remember { DecimalFormat("0.00", DecimalFormatSymbols(Locale.US)) }

    Scaffold(
        containerColor = TerminalBackground,
        topBar = {
            TopAppBar(
                title = { Text("Reports & Analytics") },
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
            TabRow(selectedTabIndex = selectedPeriod.ordinal) {
                ReportPeriod.entries.forEach { period ->
                    Tab(
                        selected = selectedPeriod == period,
                        onClick = { viewModel.selectPeriod(period) },
                        text = { Text(period.label) }
                    )
                }
            }

            // Key Metrics
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                MetricCard("Total Attempts", summary.totalAttempts.toString(), Modifier.weight(1f))
                MetricCard("Successful", summary.successfulCount.toString(), Modifier.weight(1f))
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                MetricCard("Failed / Cancelled", summary.failedCount.toString(), Modifier.weight(1f))
                MetricCard("Pending / Unknown", (summary.pendingCount + summary.unknownCount).toString(), Modifier.weight(1f))
            }

            val formattedTotalValue = remember(summary.totalSuccessfulValueGhs) {
                "GHS ${amountFmt.format(summary.totalSuccessfulValueGhs)}"
            }
            val formattedCommission = remember(summary.verifiedCommissionGhs) {
                val comm = summary.verifiedCommissionGhs
                if (comm != null) {
                    "Verified Commission: GHS ${amountFmt.format(comm)}"
                } else {
                    "Verified Commission: Not recorded (transaction value and commission are tracked separately)"
                }
            }

            EasyMoMoCard(modifier = Modifier.fillMaxWidth()) {
                Text("Total Successful Value", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                Text(
                    text = formattedTotalValue,
                    style = MaterialTheme.typography.headlineMedium,
                    color = EmeraldPrimary
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = formattedCommission,
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary
                )
            }

            // Network Breakdown
            EasyMoMoCard(modifier = Modifier.fillMaxWidth()) {
                Text("Network Breakdown", style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(8.dp))
                if (summary.networkBreakdown.isEmpty()) {
                    Text("No transactions in this period.", style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
                } else {
                    summary.networkBreakdown.forEach { item ->
                        key(item.network) {
                            val rowText = remember(item.count, item.successfulValueGhs) {
                                "${item.count} txns • GHS ${amountFmt.format(item.successfulValueGhs)}"
                            }
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                NetworkBadge(item.network)
                                Text(
                                    rowText,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                }
            }

            // Transaction Type Breakdown
            EasyMoMoCard(modifier = Modifier.fillMaxWidth()) {
                Text("Transaction Type Breakdown", style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(8.dp))
                if (summary.typeBreakdown.isEmpty()) {
                    Text("No transactions in this period.", style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
                } else {
                    summary.typeBreakdown.forEach { item ->
                        key(item.type) {
                            val rowText = remember(item.count, item.successfulValueGhs) {
                                "${item.count} txns • GHS ${amountFmt.format(item.successfulValueGhs)}"
                            }
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(item.type.displayName, style = MaterialTheme.typography.bodyMedium, color = TextPrimary)
                                Text(
                                    rowText,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                }
            }

            // Export CSV & JSON
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                TerminalActionButton(
                    label = "Export CSV",
                    icon = Icons.Outlined.Download,
                    onClick = {
                        scope.launch {
                            val uri = viewModel.exportCurrentPeriodCsv(context)
                            val share = Intent(Intent.ACTION_SEND).apply {
                                type = "text/csv"
                                putExtra(Intent.EXTRA_STREAM, uri)
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                            context.startActivity(Intent.createChooser(share, "Export CSV Report"))
                        }
                    },
                    modifier = Modifier.weight(1f)
                )
                TerminalOutlinedButton(
                    label = "Export JSON",
                    icon = Icons.Outlined.Download,
                    onClick = {
                        scope.launch {
                            val uri = viewModel.exportCurrentPeriodJson(context)
                            val share = Intent(Intent.ACTION_SEND).apply {
                                type = "application/json"
                                putExtra(Intent.EXTRA_STREAM, uri)
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                            context.startActivity(Intent.createChooser(share, "Export JSON Report"))
                        }
                    },
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun MetricCard(label: String, value: String, modifier: Modifier = Modifier) {
    EasyMoMoCard(modifier = modifier) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = TextSecondary)
        Spacer(modifier = Modifier.height(4.dp))
        Text(value, style = MaterialTheme.typography.headlineMedium, color = TextPrimary)
    }
}

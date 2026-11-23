package com.easymomo.app.ui.history

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.easymomo.app.data.local.TransactionEntity
import com.easymomo.app.data.local.TransactionEventEntity
import com.easymomo.app.data.repository.ReportAggregator
import com.easymomo.app.data.repository.ReportExporter
import com.easymomo.app.data.repository.ReportSummary
import com.easymomo.app.data.repository.TransactionRepository
import com.easymomo.app.domain.model.NetworkType
import com.easymomo.app.domain.model.TransactionStatus
import com.easymomo.app.domain.model.TransactionType
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Calendar

enum class ReportPeriod(val label: String) {
    DAILY("Daily"),
    WEEKLY("Weekly"),
    MONTHLY("Monthly")
}

data class HistoryFilterState(
    val query: String = "",
    val network: String? = null,
    val type: String? = null,
    val status: String? = null
)

data class NetworkDailySummary(
    val network: NetworkType,
    val totalAmount: Double = 0.0,
    val totalCount: Int = 0,
    val cashInAmount: Double = 0.0,
    val cashInCount: Int = 0,
    val cashOutAmount: Double = 0.0,
    val cashOutCount: Int = 0,
    val airtimeDataAmount: Double = 0.0,
    val airtimeDataCount: Int = 0
)

data class DailyNetworkBreakdown(
    val totalAmount: Double = 0.0,
    val totalFees: Double = 0.0,
    val successfulCount: Int = 0,
    val pendingCount: Int = 0,
    val failedCount: Int = 0,
    val networks: List<NetworkDailySummary> = emptyList()
)

class HistoryReportsViewModel(
    private val txRepo: TransactionRepository,
    private val hasActiveAccess: () -> Boolean = { true }
) : ViewModel() {

    private val _filter = MutableStateFlow(HistoryFilterState())
    val filter: StateFlow<HistoryFilterState> = _filter.asStateFlow()

    private val _reportPeriod = MutableStateFlow(ReportPeriod.DAILY)
    val reportPeriod: StateFlow<ReportPeriod> = _reportPeriod.asStateFlow()

    private val _syncMessage = MutableStateFlow<String?>(null)
    val syncMessage: StateFlow<String?> = _syncMessage.asStateFlow()

    val pendingSyncCount: StateFlow<Int> = txRepo.observePendingSyncCount()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    @OptIn(ExperimentalCoroutinesApi::class)
    val filteredTransactions: StateFlow<List<TransactionEntity>> = _filter
        .flatMapLatest { f ->
            txRepo.observeFilteredTransactions(f.network, f.type, f.status, f.query)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    @OptIn(ExperimentalCoroutinesApi::class)
    val reportSummary: StateFlow<ReportSummary> = _reportPeriod
        .flatMapLatest { period ->
            val (start, end) = periodBounds(period)
            txRepo.observeTransactionsBetween(start, end).map { ReportAggregator.aggregate(it) }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ReportSummary())

    val todayBreakdown: StateFlow<DailyNetworkBreakdown> = run {
        val (todayStart, todayEnd) = periodBounds(ReportPeriod.DAILY)
        txRepo.observeTransactionsBetween(todayStart, todayEnd)
            .map { buildTodayBreakdown(it) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), DailyNetworkBreakdown())
    }

    private fun buildTodayBreakdown(txs: List<TransactionEntity>): DailyNetworkBreakdown {
        var totalAmount = 0.0
        var totalFees = 0.0
        var successCount = 0
        var pendingCount = 0
        var failedCount = 0

        val byNet = mutableMapOf<NetworkType, NetworkDailySummary>()
        listOf(NetworkType.MTN, NetworkType.TELECEL, NetworkType.AT).forEach { net ->
            byNet[net] = NetworkDailySummary(network = net)
        }

        txs.forEach { tx ->
            val status = TransactionStatus.fromCode(tx.status)
            when (status) {
                TransactionStatus.SUCCESSFUL -> successCount++
                TransactionStatus.FAILED, TransactionStatus.CANCELLED -> failedCount++
                else -> pendingCount++
            }
            if (status != TransactionStatus.SUCCESSFUL) return@forEach

            totalAmount += tx.amount
            val net = NetworkType.fromCode(tx.network)
            if (net !in byNet) return@forEach
            val cur = byNet.getValue(net)
            val txType = runCatching { TransactionType.valueOf(tx.transactionType) }.getOrNull()
            val updated = when (txType) {
                TransactionType.CASH_IN -> cur.copy(
                    totalAmount = cur.totalAmount + tx.amount,
                    totalCount = cur.totalCount + 1,
                    cashInAmount = cur.cashInAmount + tx.amount,
                    cashInCount = cur.cashInCount + 1
                )
                TransactionType.CASH_OUT -> cur.copy(
                    totalAmount = cur.totalAmount + tx.amount,
                    totalCount = cur.totalCount + 1,
                    cashOutAmount = cur.cashOutAmount + tx.amount,
                    cashOutCount = cur.cashOutCount + 1
                )
                TransactionType.DATA,
                TransactionType.AIRTIME,
                TransactionType.EVD_BUNDLE,
                TransactionType.EVD_AIRTIME -> cur.copy(
                    totalAmount = cur.totalAmount + tx.amount,
                    totalCount = cur.totalCount + 1,
                    airtimeDataAmount = cur.airtimeDataAmount + tx.amount,
                    airtimeDataCount = cur.airtimeDataCount + 1
                )
                else -> cur.copy(
                    totalAmount = cur.totalAmount + tx.amount,
                    totalCount = cur.totalCount + 1
                )
            }
            byNet[net] = updated
        }

        // Always show MTN and Telecel; include AT when it has activity today
        val orderedNetworks = listOfNotNull(
            byNet[NetworkType.MTN],
            byNet[NetworkType.TELECEL],
            byNet[NetworkType.AT]?.takeIf { it.totalCount > 0 }
        )

        return DailyNetworkBreakdown(
            totalAmount = totalAmount,
            totalFees = totalFees,
            successfulCount = successCount,
            pendingCount = pendingCount,
            failedCount = failedCount,
            networks = orderedNetworks
        )
    }

    fun observeEvents(localId: String): Flow<List<TransactionEventEntity>> =
        txRepo.observeEvents(localId)

    fun updateQuery(q: String) = _filter.update { it.copy(query = q) }
    fun updateNetworkFilter(n: String?) = _filter.update { it.copy(network = n) }
    fun updateTypeFilter(t: String?) = _filter.update { it.copy(type = t) }
    fun updateStatusFilter(s: String?) = _filter.update { it.copy(status = s) }
    fun selectPeriod(period: ReportPeriod) {
        _reportPeriod.value = period
    }

    fun syncNow() {
        if (!hasActiveAccess()) {
            _syncMessage.value = "Active subscription or trial required to sync."
            return
        }
        viewModelScope.launch {
            txRepo.syncPendingTransactions()
                .onSuccess { count ->
                    _syncMessage.value = if (count == 0) "All transactions already synced." else "Synced $count transaction(s)."
                }
                .onFailure { err ->
                    _syncMessage.value = "Sync deferred (offline/unavailable): ${err.message}"
                }
        }
    }

    fun clearSyncMessage() {
        _syncMessage.value = null
    }

    fun markTransactionStatus(localId: String, status: TransactionStatus) {
        viewModelScope.launch {
            txRepo.transitionStatus(
                localId = localId,
                newStatus = status,
                confirmationSource = "MANUAL_AGENT_OVERRIDE",
                details = "Manually marked ${status.name.lowercase()} by agent",
                isExplicitAgentVerification = true
            )
        }
    }

    suspend fun exportCurrentPeriodCsv(context: Context): Uri {
        val (start, end) = periodBounds(_reportPeriod.value)
        val txs = txRepo.getTransactionsBetween(start, end)
        return ReportExporter.exportCsvFile(context, txs, "easymomo_${_reportPeriod.value.name.lowercase()}")
    }

    suspend fun exportCurrentPeriodJson(context: Context): Uri {
        val (start, end) = periodBounds(_reportPeriod.value)
        val txs = txRepo.getTransactionsBetween(start, end)
        return ReportExporter.exportJsonFile(context, txs, "easymomo_${_reportPeriod.value.name.lowercase()}")
    }

    private fun periodBounds(period: ReportPeriod): Pair<Long, Long> {
        val now = System.currentTimeMillis()
        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        when (period) {
            ReportPeriod.DAILY -> Unit
            ReportPeriod.WEEKLY -> cal.add(Calendar.DAY_OF_YEAR, -6)
            ReportPeriod.MONTHLY -> cal.set(Calendar.DAY_OF_MONTH, 1)
        }
        return cal.timeInMillis to now
    }

    class Factory(
        private val txRepo: TransactionRepository,
        private val hasActiveAccess: () -> Boolean = { true }
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            HistoryReportsViewModel(txRepo, hasActiveAccess) as T
    }
}

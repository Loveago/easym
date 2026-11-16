package com.easymomo.app.data.repository

import com.easymomo.app.data.local.TransactionEntity
import com.easymomo.app.domain.model.NetworkType
import com.easymomo.app.domain.model.TransactionStatus
import com.easymomo.app.domain.model.TransactionType

data class NetworkBreakdownItem(
    val network: NetworkType,
    val count: Int,
    val successfulValueGhs: Double
)

data class TypeBreakdownItem(
    val type: TransactionType,
    val count: Int,
    val successfulValueGhs: Double
)

data class ReportSummary(
    val totalAttempts: Int = 0,
    val successfulCount: Int = 0,
    val failedCount: Int = 0,
    val pendingCount: Int = 0,
    val unknownCount: Int = 0,
    val totalSuccessfulValueGhs: Double = 0.0,
    val verifiedCommissionGhs: Double? = null,
    val networkBreakdown: List<NetworkBreakdownItem> = emptyList(),
    val typeBreakdown: List<TypeBreakdownItem> = emptyList()
)

object ReportAggregator {

    fun aggregate(transactions: List<TransactionEntity>): ReportSummary {
        if (transactions.isEmpty()) return ReportSummary()

        var successCount = 0
        var failCount = 0
        var pendingCount = 0
        var unknownCount = 0
        var successValue = 0.0
        var verifiedCommissionSum = 0.0
        var hasVerifiedCommission = false

        for (tx in transactions) {
            when (TransactionStatus.fromCode(tx.status)) {
                TransactionStatus.SUCCESSFUL -> {
                    successCount++
                    successValue += tx.amount
                    if (tx.commissionAmount != null) {
                        hasVerifiedCommission = true
                        verifiedCommissionSum += tx.commissionAmount
                    }
                }
                TransactionStatus.FAILED, TransactionStatus.CANCELLED -> failCount++
                TransactionStatus.PROCESSING,
                TransactionStatus.AWAITING_CUSTOMER,
                TransactionStatus.AWAITING_VERIFICATION,
                TransactionStatus.DRAFT -> pendingCount++
                TransactionStatus.UNKNOWN -> unknownCount++
            }
        }

        val netBreakdown = transactions
            .groupBy { NetworkType.fromCode(it.network) }
            .map { (net, list) ->
                val valSum = list
                    .filter { it.status == TransactionStatus.SUCCESSFUL.code }
                    .sumOf { it.amount }
                NetworkBreakdownItem(net, list.size, valSum)
            }
            .sortedByDescending { it.count }

        val typeBreakdown = transactions
            .groupBy { TransactionType.fromCode(it.transactionType) }
            .map { (type, list) ->
                val valSum = list
                    .filter { it.status == TransactionStatus.SUCCESSFUL.code }
                    .sumOf { it.amount }
                TypeBreakdownItem(type, list.size, valSum)
            }
            .sortedByDescending { it.count }

        return ReportSummary(
            totalAttempts = transactions.size,
            successfulCount = successCount,
            failedCount = failCount,
            pendingCount = pendingCount,
            unknownCount = unknownCount,
            totalSuccessfulValueGhs = successValue,
            verifiedCommissionGhs = if (hasVerifiedCommission) verifiedCommissionSum else null,
            networkBreakdown = netBreakdown,
            typeBreakdown = typeBreakdown
        )
    }
}

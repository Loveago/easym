package com.easymomo.app.data.repository

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import com.easymomo.app.data.local.TransactionEntity
import com.google.gson.GsonBuilder
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object ReportExporter {

    private val isoFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US)

    fun buildCsvContent(transactions: List<TransactionEntity>): String {
        val sb = StringBuilder()
        sb.appendLine("local_id,provider_reference,network,transaction_type,processing_method,recipient_number,amount_ghs,bundle_name,status,confirmation_source,created_at,updated_at")
        for (tx in transactions) {
            sb.appendLine(
                listOf(
                    csvEscape(tx.localId),
                    csvEscape(tx.providerReference.orEmpty()),
                    csvEscape(tx.network),
                    csvEscape(tx.transactionType),
                    csvEscape(tx.processingMethod),
                    csvEscape(tx.recipientNumber),
                    String.format(Locale.US, "%.2f", tx.amount),
                    csvEscape(tx.bundleName.orEmpty()),
                    csvEscape(tx.status),
                    csvEscape(tx.confirmationSource.orEmpty()),
                    csvEscape(isoFormat.format(Date(tx.createdAt))),
                    csvEscape(isoFormat.format(Date(tx.updatedAt)))
                ).joinToString(",")
            )
        }
        return sb.toString()
    }

    fun buildJsonContent(transactions: List<TransactionEntity>): String {
        val gson = GsonBuilder().setPrettyPrinting().create()
        return gson.toJson(transactions)
    }

    fun exportCsvFile(context: Context, transactions: List<TransactionEntity>, filePrefix: String): Uri {
        val dir = File(context.cacheDir, "exports").apply { mkdirs() }
        val file = File(dir, "${filePrefix}_${System.currentTimeMillis()}.csv")
        file.writeText(buildCsvContent(transactions))
        return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    }

    fun exportJsonFile(context: Context, transactions: List<TransactionEntity>, filePrefix: String): Uri {
        val dir = File(context.cacheDir, "exports").apply { mkdirs() }
        val file = File(dir, "${filePrefix}_${System.currentTimeMillis()}.json")
        file.writeText(buildJsonContent(transactions))
        return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    }

    private fun csvEscape(value: String): String {
        val escaped = value.replace("\"", "\"\"")
        return "\"$escaped\""
    }
}

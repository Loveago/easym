package com.easymomo.app.ussd.sms

import com.easymomo.app.domain.model.NetworkPrefixDetector
import com.easymomo.app.domain.model.NetworkType
import com.easymomo.app.domain.model.TransactionType

data class ParsedMoMoSms(
    val network: NetworkType,
    val transactionType: TransactionType,
    val amount: Double,
    val fee: Double,
    val customerName: String,
    val phoneNumber: String,
    val providerReference: String,
    val rawText: String
)

object MoMoSmsParser {

    private val knownSenders = listOf(
        "mobilemoney", "mtn momo", "mtnmomo", "mtn",
        "telecelcash", "telecel cash", "telecel", "vodafonecash", "vodafone cash",
        "atmoney", "at money", "airteltigo", "tigo cash"
    )

    private val otpOrPromoIndicators = listOf(
        "verification code", "one time password", "one-time password", "your otp",
        "otp is", "do not share this code", "pin reset"
    )

    private val txBodyIndicators = listOf(
        "financial transaction id", "transaction id", "trans id", "txn id",
        "current balance", "available balance", "new balance", "cash in", "cash out",
        "confirmed.", "deposited", "withdrawn"
    )

    private val amountRegex = Regex(
        """(?:ghs|ghc|gh₵)\s*([0-9][0-9,]*(?:\.[0-9]{1,2})?)""",
        RegexOption.IGNORE_CASE
    )
    private val feeRegex = Regex(
        """fee\s*(?:charged|is|:)?\s*(?:ghs|ghc|gh₵)?\s*([0-9][0-9,]*(?:\.[0-9]{1,2})?)""",
        RegexOption.IGNORE_CASE
    )
    private val refRegex = Regex(
        """(?:financial\s+transaction\s+id|transaction\s+id|trans(?:action)?\s*id|txn\s*id|ref(?:erence)?)\s*[:#-]?\s*([A-Z0-9-]{6,30})""",
        RegexOption.IGNORE_CASE
    )
    private val telecelLeadingRefRegex = Regex(
        """^([0-9]{10,20})\s+confirmed\b""",
        RegexOption.IGNORE_CASE
    )
    private val phoneRegex = Regex("""(?:\+?233|0)[235][0-9]{8}""")
    private val nameBeforePhoneRegex = Regex(
        """\b(?:to|from|for)\s+([A-Za-z][A-Za-z .'-]{1,40}?)\s*\((?:\+?233|0)[235][0-9]{8}\)""",
        RegexOption.IGNORE_CASE
    )
    private val nameStandaloneRegex = Regex(
        """\b(?:to|from|for)\s+([A-Za-z][A-Za-z .'-]{2,40}?)(?=\s*(?:\.|,|;|\(|on\s+\d|with\s+(?:id|ref|tx)|financial\s+transaction|transaction\s+id|trans\s+id|ref\s*:|fee\s*:|$))""",
        RegexOption.IGNORE_CASE
    )

    fun parse(senderTitle: String, rawBody: String): ParsedMoMoSms? {
        val titleNorm = senderTitle.trim().lowercase()
        val body = rawBody.trim()
        if (body.isEmpty()) return null
        val bodyNorm = body.lowercase()

        if (otpOrPromoIndicators.any { bodyNorm.contains(it) }) {
            return null
        }

        val senderMatches = knownSenders.any { titleNorm.contains(it) }
        val bodyMatches = txBodyIndicators.any { bodyNorm.contains(it) }
        if (!senderMatches && !bodyMatches) {
            return null
        }
        // Require at least a currency amount AND either a tx indicator or known MoMo sender
        val amountMatch = amountRegex.find(body) ?: return null
        val amount = amountMatch.groupValues[1].replace(",", "").toDoubleOrNull() ?: return null
        if (amount <= 0.0) return null

        if (!bodyMatches && !bodyNorm.contains("sent") && !bodyNorm.contains("received") &&
            !bodyNorm.contains("purchased") && !bodyNorm.contains("paid")
        ) {
            return null
        }

        val network = detectNetwork(titleNorm, bodyNorm)
        val txType = detectTransactionType(bodyNorm)
        val fee = feeRegex.find(body)?.groupValues?.getOrNull(1)
            ?.replace(",", "")?.toDoubleOrNull() ?: 0.0

        val providerRef = refRegex.find(body)?.groupValues?.getOrNull(1)?.trim()
            ?: telecelLeadingRefRegex.find(body)?.groupValues?.getOrNull(1)?.trim()
            ?: ""

        val rawPhone = phoneRegex.find(body)?.value.orEmpty()
        val normalizedPhone = if (rawPhone.isNotBlank()) {
            NetworkPrefixDetector.normalizeGhanaPhone(rawPhone)
        } else {
            ""
        }
        val customerName = extractCustomerName(body)

        return ParsedMoMoSms(
            network = network,
            transactionType = txType,
            amount = amount,
            fee = fee,
            customerName = customerName,
            phoneNumber = normalizedPhone,
            providerReference = providerRef,
            rawText = body.take(500)
        )
    }

    private fun extractCustomerName(body: String): String {
        val candidate = nameBeforePhoneRegex.find(body)?.groupValues?.getOrNull(1)
            ?: nameStandaloneRegex.find(body)?.groupValues?.getOrNull(1)
            ?: return ""
        val cleaned = candidate.trim().replace(Regex("""\s+"""), " ")
        val lower = cleaned.lowercase()
        if (lower.startsWith("your ") || lower.startsWith("the ") ||
            lower == "self" || lower == "momo" || lower == "account" ||
            lower.contains("ghs") || lower.contains("ghc")
        ) {
            return ""
        }
        return cleaned.take(45)
    }

    private fun detectNetwork(titleNorm: String, bodyNorm: String): NetworkType {
        return when {
            titleNorm.contains("telecel") || titleNorm.contains("vodafone") ||
                bodyNorm.contains("telecel") || bodyNorm.contains("vodafone cash") -> NetworkType.TELECEL
            titleNorm.contains("atmoney") || titleNorm.contains("at money") ||
                titleNorm.contains("airteltigo") || bodyNorm.contains("airteltigo") ||
                bodyNorm.contains("at money") -> NetworkType.AT
            titleNorm.contains("mobilemoney") || titleNorm.contains("mtn") ||
                bodyNorm.contains("mtn") || bodyNorm.contains("momo") ||
                bodyNorm.contains("financial transaction id") -> NetworkType.MTN
            else -> NetworkType.MTN
        }
    }

    private fun detectTransactionType(bodyNorm: String): TransactionType {
        return when {
            bodyNorm.contains("cash out") || bodyNorm.contains("cash-out") ||
                bodyNorm.contains("withdrawn") || bodyNorm.contains("withdrawal") ||
                bodyNorm.contains("you have received") -> TransactionType.CASH_OUT
            bodyNorm.contains("cash in") || bodyNorm.contains("cash-in") ||
                bodyNorm.contains("deposited") || bodyNorm.contains("you have sent") -> TransactionType.CASH_IN
            bodyNorm.contains("bundle") || bodyNorm.contains("data") -> TransactionType.DATA
            bodyNorm.contains("airtime") || bodyNorm.contains("recharge") ||
                bodyNorm.contains("topup") || bodyNorm.contains("top-up") -> TransactionType.AIRTIME
            bodyNorm.contains("merchant") || bodyNorm.contains("momopay") ||
                bodyNorm.contains("paid to") -> TransactionType.MERCHANT_PAY
            else -> TransactionType.CASH_IN
        }
    }
}

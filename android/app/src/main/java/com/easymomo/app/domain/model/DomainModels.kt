package com.easymomo.app.domain.model

enum class NetworkType(
    val code: String,
    val displayName: String,
    val shortName: String
) {
    MTN("MTN", "MTN MoMo", "MTN"),
    AT("AT", "AT Money", "AT"),
    TELECEL("TELECEL", "Telecel Cash", "Telecel"),
    UNKNOWN("UNKNOWN", "Unknown Network", "?");

    companion object {
        fun fromCode(code: String?): NetworkType {
            return entries.firstOrNull { it.code.equals(code, ignoreCase = true) } ?: UNKNOWN
        }
    }
}

enum class TransactionType(val code: String, val displayName: String) {
    CASH_IN("CASH_IN", "Cash In"),
    CASH_OUT("CASH_OUT", "Cash Out"),
    AIRTIME("AIRTIME", "MoMo Airtime"),
    DATA("DATA", "MoMo Data"),
    EVD_AIRTIME("EVD_AIRTIME", "EVD Airtime"),
    EVD_BUNDLE("EVD_BUNDLE", "EVD Data"),
    GIG("GIG", "Gig Purchase"),
    MERCHANT_PAY("MERCHANT_PAY", "Pay to Merchant"),
    CUSTOM_USSD("CUSTOM_USSD", "Custom USSD"),
    BALANCE("BALANCE", "Balance Enquiry"),
    STATEMENT("STATEMENT", "MoMo Statement"),
    COMMISSION("COMMISSION", "Commission");

    companion object {
        fun fromCode(code: String?): TransactionType {
            return entries.firstOrNull { it.code.equals(code, ignoreCase = true) } ?: CASH_IN
        }
    }
}

enum class ProcessingMethod(val code: String, val displayName: String) {
    AGENT_USSD("AGENT_USSD", "Agent USSD"),
    CUSTOMER_LED("CUSTOMER_LED", "Customer-Led"),
    MANUAL_USSD("MANUAL_USSD", "Agent-Verified / Manual");

    companion object {
        fun fromCode(code: String?): ProcessingMethod {
            return entries.firstOrNull { it.code.equals(code, ignoreCase = true) } ?: AGENT_USSD
        }
    }
}

enum class TransactionStatus(val code: String, val displayName: String) {
    DRAFT("DRAFT", "Draft"),
    PROCESSING("PROCESSING", "Processing"),
    AWAITING_CUSTOMER("AWAITING_CUSTOMER", "Awaiting Customer"),
    AWAITING_VERIFICATION("AWAITING_VERIFICATION", "Awaiting Verification"),
    SUCCESSFUL("SUCCESSFUL", "Successful"),
    FAILED("FAILED", "Failed"),
    CANCELLED("CANCELLED", "Cancelled"),
    UNKNOWN("UNKNOWN", "Unknown");

    val isTerminal: Boolean
        get() = this == SUCCESSFUL || this == FAILED || this == CANCELLED

    companion object {
        fun fromCode(code: String?): TransactionStatus {
            return entries.firstOrNull { it.code.equals(code, ignoreCase = true) } ?: UNKNOWN
        }
    }
}

enum class SubscriptionStatus(val code: String) {
    TRIAL("TRIAL"),
    ACTIVE("ACTIVE"),
    EXPIRED("EXPIRED"),
    CANCELLED("CANCELLED"),
    NONE("NONE");

    val hasPaidAccess: Boolean
        get() = this == TRIAL || this == ACTIVE

    companion object {
        fun fromCode(code: String?): SubscriptionStatus {
            return entries.firstOrNull { it.code.equals(code, ignoreCase = true) } ?: NONE
        }
    }
}

data class SubscriptionInfo(
    val status: SubscriptionStatus = SubscriptionStatus.NONE,
    val trialStartedAt: String? = null,
    val trialExpiresAt: String? = null,
    val paidUntil: String? = null,
    val currentPlanId: String? = null,
    val currentPlanName: String? = null,
    val remainingDays: Int = 0,
    val recurringAuthorized: Boolean = false
) {
    fun effectiveStatus(nowMs: Long = System.currentTimeMillis()): SubscriptionStatus = when (status) {
        SubscriptionStatus.TRIAL -> {
            val exp = parseIsoEpochMs(trialExpiresAt)
            if (exp != null) {
                if (nowMs >= exp) SubscriptionStatus.EXPIRED else SubscriptionStatus.TRIAL
            } else if (remainingDays <= 0) SubscriptionStatus.EXPIRED else SubscriptionStatus.TRIAL
        }
        SubscriptionStatus.ACTIVE -> {
            val exp = parseIsoEpochMs(paidUntil)
            if (exp != null) {
                if (nowMs >= exp) SubscriptionStatus.EXPIRED else SubscriptionStatus.ACTIVE
            } else if (remainingDays <= 0) SubscriptionStatus.EXPIRED else SubscriptionStatus.ACTIVE
        }
        else -> status
    }

    fun hasActiveAccess(nowMs: Long = System.currentTimeMillis()): Boolean {
        val eff = effectiveStatus(nowMs)
        return eff == SubscriptionStatus.TRIAL || eff == SubscriptionStatus.ACTIVE
    }

    fun effectiveRemainingDays(nowMs: Long = System.currentTimeMillis()): Int {
        if (!hasActiveAccess(nowMs)) return 0
        val exp = when (effectiveStatus(nowMs)) {
            SubscriptionStatus.TRIAL -> parseIsoEpochMs(trialExpiresAt)
            SubscriptionStatus.ACTIVE -> parseIsoEpochMs(paidUntil)
            else -> null
        }
        return if (exp != null) {
            kotlin.math.ceil((exp - nowMs).coerceAtLeast(0L).toDouble() / MS_PER_DAY.toDouble())
                .toInt().coerceAtLeast(0)
        } else {
            remainingDays.coerceAtLeast(0)
        }
    }

    companion object {
        const val DEFAULT_TRIAL_DAYS = 15
        private const val MS_PER_DAY = 24L * 60L * 60L * 1000L

        private fun parseIsoEpochMs(iso: String?): Long? {
            if (iso.isNullOrBlank()) return null
            return runCatching { java.time.Instant.parse(iso).toEpochMilli() }.getOrNull()
        }
    }
}

data class AgentUser(
    val id: String,
    val fullName: String,
    val email: String,
    val phone: String,
    val accountStatus: String = "ACTIVE",
    val createdAt: String? = null
)

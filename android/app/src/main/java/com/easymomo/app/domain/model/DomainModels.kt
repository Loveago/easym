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
    AIRTIME("AIRTIME", "Airtime"),
    DATA("DATA", "Data Bundle"),
    EVD_AIRTIME("EVD_AIRTIME", "EVD Airtime"),
    EVD_BUNDLE("EVD_BUNDLE", "EVD Bundle"),
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
    val remainingDays: Int = 0
)

data class AgentUser(
    val id: String,
    val fullName: String,
    val email: String,
    val phone: String,
    val accountStatus: String = "ACTIVE"
)

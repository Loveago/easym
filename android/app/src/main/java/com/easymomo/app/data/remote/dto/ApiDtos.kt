package com.easymomo.app.data.remote.dto

import com.google.gson.JsonElement

data class RegisterRequest(
    val fullName: String,
    val phone: String,
    val email: String,
    val password: String
)

data class LoginRequest(
    val identifier: String,
    val password: String
)

data class AuthResponse(
    val token: String,
    val user: UserDto,
    val entitlement: SubscriptionStatusDto?
)

data class UserDto(
    val id: String,
    val fullName: String,
    val email: String,
    val phone: String,
    val accountStatus: String = "ACTIVE"
)

data class SubscriptionStatusDto(
    val status: String,
    val trialStartedAt: String? = null,
    val trialExpiresAt: String? = null,
    val paidUntil: String? = null,
    val currentPlanId: String? = null,
    val currentPlanName: String? = null,
    val remainingDays: Int = 0
)

data class SubscriptionStatusWrapper(
    val entitlement: SubscriptionStatusDto
)

data class SubscriptionPlanDto(
    val id: String,
    val code: String? = null,
    val name: String,
    val price: Double,
    val currency: String,
    val billingInterval: String,
    val active: Boolean
)

data class PlansResponse(
    val plans: List<SubscriptionPlanDto>
)

data class InitializePaymentRequest(
    val planId: String
)

data class InitializePaymentResponse(
    val authorizationUrl: String,
    val accessCode: String? = null,
    val reference: String
)

data class PaymentPlanSummaryDto(
    val id: String,
    val code: String? = null,
    val name: String,
    val billingInterval: String
)

data class PaymentHistoryDto(
    val id: String,
    val reference: String,
    val amount: Double,
    val currency: String,
    val channel: String? = null,
    val status: String,
    val paidAt: String? = null,
    val plan: PaymentPlanSummaryDto? = null
)

data class PaymentsResponse(
    val payments: List<PaymentHistoryDto>
)

data class VerifyPaymentResponse(
    val alreadyProcessed: Boolean = false,
    val payment: PaymentHistoryDto? = null,
    val entitlement: SubscriptionStatusDto? = null
)

data class SyncTransactionItemDto(
    val clientTransactionId: String,
    val providerReference: String? = null,
    val network: String,
    val transactionType: String,
    val processingType: String,
    val recipientNumber: String,
    val amount: Double,
    val bundleId: String? = null,
    val bundleName: String? = null,
    val status: String,
    val confirmationSource: String? = null,
    val providerResponse: String? = null,
    val createdAt: String,
    val updatedAt: String
)

data class SyncTransactionsRequest(
    val transactions: List<SyncTransactionItemDto>
)

data class SyncedRecordDto(
    val clientTransactionId: String,
    val serverId: String? = null,
    val outcome: String,
    val status: String? = null,
    val reason: String? = null
)

data class SyncTransactionsResponse(
    val results: List<SyncedRecordDto>
)

data class UssdFlowProfileDto(
    val id: String,
    val flowId: String? = null,
    val network: String,
    val transactionType: String,
    val version: Int,
    val configuration: JsonElement,
    val enabled: Boolean,
    val lastVerifiedAt: String? = null
)

data class UssdFlowsResponse(
    val flows: List<UssdFlowProfileDto>
)

data class DataBundleDto(
    val id: String,
    val network: String,
    val name: String,
    val allowance: String,
    val price: Double,
    val validity: String,
    val providerOption: String,
    val active: Boolean,
    val lastVerifiedAt: String? = null
)

data class DataBundlesResponse(
    val bundles: List<DataBundleDto>
)

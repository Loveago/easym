package com.easymomo.app.data.repository

import com.easymomo.app.BuildConfig
import com.easymomo.app.data.local.SessionStore
import com.easymomo.app.data.remote.EasyMoMoApiService
import com.easymomo.app.data.remote.dto.InitializePaymentRequest
import com.easymomo.app.data.remote.dto.InitializePaymentResponse
import com.easymomo.app.data.remote.dto.LoginRequest
import com.easymomo.app.data.remote.dto.PaymentHistoryDto
import com.easymomo.app.data.remote.dto.RegisterRequest
import com.easymomo.app.data.remote.dto.SubscriptionPlanDto
import com.easymomo.app.data.remote.dto.SubscriptionStatusDto
import com.easymomo.app.domain.model.AgentUser
import com.easymomo.app.domain.model.SubscriptionInfo
import com.easymomo.app.domain.model.SubscriptionStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class AuthSubscriptionRepository(
    private val api: EasyMoMoApiService,
    private val sessionStore: SessionStore,
    private val onUserAuthenticated: (suspend (userId: String) -> Unit)? = null,
    private val onUserLoggedOut: (suspend (previousUserId: String?) -> Unit)? = null
) {
    private val backgroundScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _currentUser = MutableStateFlow(sessionStore.getUser())
    val currentUser: StateFlow<AgentUser?> = _currentUser.asStateFlow()

    private val _subscription = MutableStateFlow(sessionStore.getSubscription())
    val subscription: StateFlow<SubscriptionInfo> = _subscription.asStateFlow()

    fun isLoggedIn(): Boolean = !sessionStore.authToken.isNullOrBlank() && sessionStore.getUser() != null

    fun isOnboardingCompleted(): Boolean = sessionStore.onboardingCompleted

    fun setOnboardingCompleted() {
        sessionStore.onboardingCompleted = true
    }

    /**
     * Debug-only automatic guest session so developers/testers can enter the
     * terminal immediately without a live backend. No-op in release builds.
     */
    fun ensureDebugGuestSession() {
        if (!BuildConfig.DEBUG || isLoggedIn()) return
        sessionStore.onboardingCompleted = true
        sessionStore.authToken = "debug-guest-token"
        val guestUser = AgentUser(
            id = "debug-guest-agent",
            fullName = "Guest Agent (Debug)",
            email = "guest@easymomo.local",
            phone = "0244000000",
            accountStatus = "ACTIVE"
        )
        sessionStore.saveUser(guestUser)
        _currentUser.value = guestUser

        val guestTrial = SubscriptionInfo(
            status = SubscriptionStatus.TRIAL,
            currentPlanName = "7-Day Free Trial (Guest)",
            remainingDays = 7
        )
        sessionStore.saveSubscription(guestTrial)
        _subscription.value = guestTrial
    }

    suspend fun register(
        fullName: String,
        phone: String,
        email: String,
        password: String
    ): Result<AgentUser> {
        return runCatching {
            val response = api.register(RegisterRequest(fullName.trim(), phone.trim(), email.trim(), password))
            if (!response.isSuccessful) {
                throw IllegalStateException("Registration failed (${response.code()}): ${response.errorBody()?.string().orEmpty()}")
            }
            val body = response.body() ?: throw IllegalStateException("Empty registration response")
            sessionStore.authToken = body.token
            val user = AgentUser(
                id = body.user.id,
                fullName = body.user.fullName,
                email = body.user.email,
                phone = body.user.phone,
                accountStatus = body.user.accountStatus
            )
            sessionStore.saveUser(user)
            _currentUser.value = user

            body.entitlement?.toDomain()?.let { sub ->
                sessionStore.saveSubscription(sub)
                _subscription.value = sub
            }
            backgroundScope.launch {
                runCatching { onUserAuthenticated?.invoke(user.id) }
            }
            user
        }
    }

    suspend fun login(identifier: String, password: String): Result<AgentUser> {
        return runCatching {
            val response = api.login(LoginRequest(identifier.trim(), password))
            if (!response.isSuccessful) {
                throw IllegalStateException("Invalid credentials (${response.code()})")
            }
            val body = response.body() ?: throw IllegalStateException("Empty login response")
            sessionStore.authToken = body.token
            val user = AgentUser(
                id = body.user.id,
                fullName = body.user.fullName,
                email = body.user.email,
                phone = body.user.phone,
                accountStatus = body.user.accountStatus
            )
            sessionStore.saveUser(user)
            _currentUser.value = user

            body.entitlement?.toDomain()?.let { sub ->
                sessionStore.saveSubscription(sub)
                _subscription.value = sub
            }
            backgroundScope.launch {
                runCatching { onUserAuthenticated?.invoke(user.id) }
            }
            user
        }
    }

    suspend fun refreshSubscriptionStatus(): Result<SubscriptionInfo> {
        return runCatching {
            val response = api.getSubscriptionStatus()
            if (!response.isSuccessful) {
                throw IllegalStateException("Unable to refresh subscription (${response.code()})")
            }
            val wrapper = response.body() ?: throw IllegalStateException("Empty subscription response")
            val info = wrapper.entitlement.toDomain()
            sessionStore.saveSubscription(info)
            _subscription.value = info
            info
        }
    }

    suspend fun getPlans(): Result<List<SubscriptionPlanDto>> {
        return runCatching {
            val response = api.getSubscriptionPlans()
            if (!response.isSuccessful) {
                throw IllegalStateException("Could not load plans (${response.code()})")
            }
            response.body()?.plans.orEmpty()
        }
    }

    suspend fun initializePaystackCheckout(planId: String): Result<InitializePaymentResponse> {
        return runCatching {
            val response = api.initializePayment(InitializePaymentRequest(planId = planId))
            if (!response.isSuccessful) {
                throw IllegalStateException("Could not initialize Paystack checkout (${response.code()})")
            }
            response.body() ?: throw IllegalStateException("Empty Paystack initialization response")
        }
    }

    suspend fun verifyPaystackPayment(reference: String): Result<SubscriptionInfo> {
        return runCatching {
            val response = api.verifyPayment(reference)
            if (!response.isSuccessful) {
                throw IllegalStateException("Payment verification failed (${response.code()})")
            }
            val body = response.body() ?: throw IllegalStateException("Empty verification response")
            val paymentStatus = body.payment?.status
            if (paymentStatus != null && !paymentStatus.equals("SUCCESS", ignoreCase = true) && !paymentStatus.equals("SUCCESSFUL", ignoreCase = true)) {
                throw IllegalStateException("Payment status is $paymentStatus")
            }
            val info = body.entitlement?.toDomain()
                ?: throw IllegalStateException("Missing subscription entitlement in verification response")
            sessionStore.saveSubscription(info)
            _subscription.value = info
            info
        }
    }

    suspend fun getPaymentHistory(): Result<List<PaymentHistoryDto>> {
        return runCatching {
            val response = api.getPaymentHistory()
            if (!response.isSuccessful) {
                throw IllegalStateException("Could not load payment history (${response.code()})")
            }
            response.body()?.payments.orEmpty()
        }
    }

    fun logout() {
        val previousUserId = _currentUser.value?.id
        sessionStore.clearSession()
        _currentUser.value = null
        _subscription.value = SubscriptionInfo()
        backgroundScope.launch {
            runCatching { onUserLoggedOut?.invoke(previousUserId) }
        }
    }

    private fun SubscriptionStatusDto.toDomain(): SubscriptionInfo {
        return SubscriptionInfo(
            status = SubscriptionStatus.fromCode(status),
            trialStartedAt = trialStartedAt,
            trialExpiresAt = trialExpiresAt,
            paidUntil = paidUntil,
            currentPlanId = currentPlanId,
            currentPlanName = currentPlanName,
            remainingDays = remainingDays
        )
    }
}

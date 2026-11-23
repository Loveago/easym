package com.easymomo.app.data.repository

import com.easymomo.app.BuildConfig
import com.easymomo.app.data.local.SessionStore
import com.easymomo.app.data.remote.EasyMoMoApiService
import com.easymomo.app.data.remote.dto.GoogleAuthConfigResponse
import com.easymomo.app.data.remote.dto.GoogleAuthRequest
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

    fun isDebugGuestSession(): Boolean = sessionStore.authToken == DEBUG_GUEST_TOKEN

    fun isLoggedIn(): Boolean = !sessionStore.authToken.isNullOrBlank() && sessionStore.getUser() != null

    fun isOnboardingCompleted(): Boolean = sessionStore.onboardingCompleted

    private val _trialNotice = MutableStateFlow<String?>(null)
    val trialNotice: StateFlow<String?> = _trialNotice.asStateFlow()

    fun clearTrialNotice() {
        _trialNotice.value = null
    }

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
        sessionStore.authToken = DEBUG_GUEST_TOKEN
        val guestUser = AgentUser(
            id = "debug-guest-agent",
            fullName = "Guest Agent (Debug)",
            email = "guest@easymomo.local",
            phone = "0244000000",
            accountStatus = "ACTIVE",
            createdAt = java.time.Instant.now().toString()
        )
        sessionStore.saveUser(guestUser)
        _currentUser.value = guestUser

        val trialStart = java.time.Instant.now()
        val trialEnd = trialStart.plusSeconds(SubscriptionInfo.DEFAULT_TRIAL_DAYS * 24L * 60L * 60L)
        val guestTrial = SubscriptionInfo(
            status = SubscriptionStatus.TRIAL,
            trialStartedAt = trialStart.toString(),
            trialExpiresAt = trialEnd.toString(),
            currentPlanName = "15-Day Free Trial (Guest)",
            remainingDays = SubscriptionInfo.DEFAULT_TRIAL_DAYS
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
            val response = api.register(
                RegisterRequest(
                    fullName = fullName.trim(),
                    phone = phone.trim(),
                    email = email.trim(),
                    password = password,
                    deviceId = sessionStore.deviceId,
                    deviceName = sessionStore.deviceName
                )
            )
            if (!response.isSuccessful) {
                val errMsg = extractBackendErrorMessage(
                    response.errorBody()?.string(),
                    "Registration failed (${response.code()})"
                )
                throw IllegalStateException(errMsg)
            }
            val body = response.body() ?: throw IllegalStateException("Empty registration response")
            sessionStore.authToken = body.token
            val user = AgentUser(
                id = body.user.id,
                fullName = body.user.fullName,
                email = body.user.email,
                phone = body.user.phone,
                accountStatus = body.user.accountStatus,
                createdAt = body.user.createdAt
            )
            sessionStore.saveUser(user)
            _currentUser.value = user

            val sub = body.entitlement?.toDomain() ?: SubscriptionInfo(status = SubscriptionStatus.NONE)
            sessionStore.saveSubscription(sub)
            _subscription.value = sub
            _trialNotice.value = body.trialNotice?.takeIf { it.isNotBlank() }
            _trialNotice.value = body.trialNotice?.takeIf { it.isNotBlank() }
            backgroundScope.launch {
                runCatching { onUserAuthenticated?.invoke(user.id) }
            }
            user
        }
    }

    fun getCachedGoogleAuthConfig(): GoogleAuthConfigResponse {
        val clientId = sessionStore.googleClientId.trim()
        return GoogleAuthConfigResponse(
            enabled = sessionStore.googleAuthEnabled && clientId.isNotBlank(),
            clientId = clientId
        )
    }

    suspend fun getGoogleAuthConfig(): Result<GoogleAuthConfigResponse> {
        return runCatching {
            val response = api.getGoogleAuthConfig()
            if (!response.isSuccessful) {
                throw IllegalStateException("Could not load Google Sign-In config (${response.code()})")
            }
            val cfg = response.body() ?: GoogleAuthConfigResponse()
            sessionStore.saveGoogleAuthConfig(cfg.enabled, cfg.clientId)
            cfg
        }
    }

    suspend fun signInWithGoogle(idToken: String): Result<AgentUser> {
        return runCatching {
            val response = api.googleAuth(
                GoogleAuthRequest(
                    idToken = idToken,
                    deviceId = sessionStore.deviceId,
                    deviceName = sessionStore.deviceName
                )
            )
            if (!response.isSuccessful) {
                val fallback = if (response.code() == 403) {
                    "Maximum of 2 active devices reached for this account. Please sign out on one of your active devices first."
                } else {
                    "Google Sign-In failed (${response.code()})"
                }
                val errMsg = extractBackendErrorMessage(response.errorBody()?.string(), fallback)
                throw IllegalStateException(errMsg)
            }
            val body = response.body() ?: throw IllegalStateException("Empty Google Sign-In response")
            sessionStore.authToken = body.token
            val user = AgentUser(
                id = body.user.id,
                fullName = body.user.fullName,
                email = body.user.email,
                phone = body.user.phone,
                accountStatus = body.user.accountStatus,
                createdAt = body.user.createdAt
            )
            sessionStore.saveUser(user)
            _currentUser.value = user

            val sub = body.entitlement?.toDomain() ?: SubscriptionInfo(status = SubscriptionStatus.NONE)
            sessionStore.saveSubscription(sub)
            _subscription.value = sub
            _trialNotice.value = body.trialNotice?.takeIf { it.isNotBlank() }
            _trialNotice.value = body.trialNotice?.takeIf { it.isNotBlank() }
            backgroundScope.launch {
                runCatching { onUserAuthenticated?.invoke(user.id) }
            }
            user
        }
    }

    suspend fun login(identifier: String, password: String): Result<AgentUser> {
        return runCatching {
            val response = api.login(
                LoginRequest(
                    identifier = identifier.trim(),
                    password = password,
                    deviceId = sessionStore.deviceId,
                    deviceName = sessionStore.deviceName
                )
            )
            if (!response.isSuccessful) {
                val fallback = if (response.code() == 403) {
                    "Maximum of 2 active devices reached for this account. Please sign out on one of your active devices first."
                } else {
                    "Invalid credentials (${response.code()})"
                }
                val errMsg = extractBackendErrorMessage(response.errorBody()?.string(), fallback)
                throw IllegalStateException(errMsg)
            }
            val body = response.body() ?: throw IllegalStateException("Empty login response")
            sessionStore.authToken = body.token
            val user = AgentUser(
                id = body.user.id,
                fullName = body.user.fullName,
                email = body.user.email,
                phone = body.user.phone,
                accountStatus = body.user.accountStatus,
                createdAt = body.user.createdAt
            )
            sessionStore.saveUser(user)
            _currentUser.value = user

            val sub = body.entitlement?.toDomain() ?: SubscriptionInfo(status = SubscriptionStatus.NONE)
            sessionStore.saveSubscription(sub)
            _subscription.value = sub
            _trialNotice.value = body.trialNotice?.takeIf { it.isNotBlank() }
            _trialNotice.value = body.trialNotice?.takeIf { it.isNotBlank() }
            backgroundScope.launch {
                runCatching { onUserAuthenticated?.invoke(user.id) }
            }
            user
        }
    }

    suspend fun refreshProfileAndSubscription(): Result<SubscriptionInfo> {
        if (!isLoggedIn() || isDebugGuestSession()) return Result.success(_subscription.value)
        val profile = runCatching {
            val response = api.getProfile()
            val body = response.body()
            if (!response.isSuccessful || body == null) {
                throw IllegalStateException("Unable to refresh profile (${response.code()})")
            }
            val user = AgentUser(
                id = body.user.id,
                fullName = body.user.fullName,
                email = body.user.email,
                phone = body.user.phone,
                accountStatus = body.user.accountStatus,
                createdAt = body.user.createdAt
            )
            val info = body.entitlement.toDomain()
            sessionStore.saveUser(user)
            _currentUser.value = user
            sessionStore.saveSubscription(info)
            _subscription.value = info
            info
        }
        return if (profile.isSuccess) profile else refreshSubscriptionStatus()
    }

    suspend fun refreshSubscriptionStatus(): Result<SubscriptionInfo> {
        if (!isLoggedIn() || isDebugGuestSession()) return Result.success(_subscription.value)
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
        val wasGuest = isDebugGuestSession()
        val activeToken = sessionStore.authToken
        backgroundScope.launch {
            if (!wasGuest && !activeToken.isNullOrBlank()) {
                runCatching { api.logout() }
            }
            sessionStore.clearSession()
            runCatching { onUserLoggedOut?.invoke(previousUserId) }
        }
        _currentUser.value = null
        _subscription.value = SubscriptionInfo()
    }

    private fun extractBackendErrorMessage(rawBody: String?, fallback: String): String {
        if (rawBody.isNullOrBlank()) return fallback
        val match = Regex("\"message\"\\s*:\\s*\"([^\"]+)\"").find(rawBody)
        return match?.groupValues?.getOrNull(1)?.takeIf { it.isNotBlank() } ?: fallback
    }

    private fun SubscriptionStatusDto.toDomain(): SubscriptionInfo {
        return SubscriptionInfo(
            status = SubscriptionStatus.fromCode(status),
            trialStartedAt = trialStartedAt,
            trialExpiresAt = trialExpiresAt,
            paidUntil = paidUntil,
            currentPlanId = currentPlanId,
            currentPlanName = currentPlanName,
            remainingDays = remainingDays,
            recurringAuthorized = recurringAuthorized
        )
    }

    companion object {
        const val DEBUG_GUEST_TOKEN = "debug-guest-token"
    }
}

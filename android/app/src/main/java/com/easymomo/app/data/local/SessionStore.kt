package com.easymomo.app.data.local

import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import android.provider.Settings
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.easymomo.app.domain.model.AgentUser
import com.easymomo.app.domain.model.SubscriptionInfo
import com.easymomo.app.domain.model.SubscriptionStatus
import java.util.UUID

/**
 * Securely persists JWT token, stable device identifier, cached user info,
 * and cached subscription entitlement with fast in-memory read caching so
 * main-thread UI and HTTP interceptors never stall on repeated AES-GCM decryption.
 * NEVER stores financial PINs or Paystack secret keys.
 */
class SessionStore(private val context: Context) {

    private val prefs: SharedPreferences by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        createPreferences(context)
    }

    @Volatile
    private var tokenLoaded = false
    @Volatile
    private var cachedToken: String? = null

    @Volatile
    private var onboardingLoaded = false
    @Volatile
    private var cachedOnboarding: Boolean = false

    @Volatile
    private var userLoaded = false
    @Volatile
    private var cachedUser: AgentUser? = null

    @Volatile
    private var subLoaded = false
    @Volatile
    private var cachedSubscription: SubscriptionInfo? = null

    @Volatile
    private var cachedDeviceId: String? = null

    val deviceName: String by lazy(LazyThreadSafetyMode.PUBLICATION) {
        "${Build.MANUFACTURER} ${Build.MODEL}".trim().ifBlank { "Android Device" }
    }

    val deviceId: String
        get() {
            cachedDeviceId?.let { return it }
            synchronized(this) {
                cachedDeviceId?.let { return it }
                val existing = prefs.getString(KEY_DEVICE_ID, null)?.takeIf { it.isNotBlank() }
                if (existing != null) {
                    cachedDeviceId = existing
                    return existing
                }
                val resolved = resolveHardwareOrRandomDeviceId()
                prefs.edit().putString(KEY_DEVICE_ID, resolved).apply()
                cachedDeviceId = resolved
                return resolved
            }
        }

    fun warmUp() {
        prefs
        authToken
        onboardingCompleted
        getUser()
        getSubscription()
        deviceId
    }

    @SuppressLint("HardwareIds")
    private fun resolveHardwareOrRandomDeviceId(): String {
        val androidId = runCatching {
            Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
        }.getOrNull()?.trim().orEmpty()
        return if (androidId.length >= 8 && androidId != "9774d56d682e549c") {
            "and-$androidId"
        } else {
            "dev-${UUID.randomUUID()}"
        }
    }

    private fun createPreferences(context: Context): SharedPreferences {
        return try {
            val masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
            EncryptedSharedPreferences.create(
                context,
                "easymomo_secure_session",
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        } catch (_: Exception) {
            context.getSharedPreferences("easymomo_session_fallback", Context.MODE_PRIVATE)
        }
    }

    var authToken: String?
        get() {
            if (tokenLoaded) return cachedToken
            val loaded = prefs.getString(KEY_TOKEN, null)
            cachedToken = loaded
            tokenLoaded = true
            return loaded
        }
        set(value) {
            cachedToken = value
            tokenLoaded = true
            prefs.edit().putString(KEY_TOKEN, value).apply()
        }

    var onboardingCompleted: Boolean
        get() {
            if (onboardingLoaded) return cachedOnboarding
            val loaded = prefs.getBoolean(KEY_ONBOARDING_DONE, false)
            cachedOnboarding = loaded
            onboardingLoaded = true
            return loaded
        }
        set(value) {
            cachedOnboarding = value
            onboardingLoaded = true
            prefs.edit().putBoolean(KEY_ONBOARDING_DONE, value).apply()
        }

    fun saveUser(user: AgentUser) {
        cachedUser = user
        userLoaded = true
        prefs.edit()
            .putString(KEY_USER_ID, user.id)
            .putString(KEY_USER_NAME, user.fullName)
            .putString(KEY_USER_EMAIL, user.email)
            .putString(KEY_USER_PHONE, user.phone)
            .putString(KEY_USER_STATUS, user.accountStatus)
            .putString(KEY_USER_CREATED_AT, user.createdAt)
            .apply()
    }

    fun getUser(): AgentUser? {
        if (userLoaded) return cachedUser
        val id = prefs.getString(KEY_USER_ID, null)
        if (id == null) {
            cachedUser = null
            userLoaded = true
            return null
        }
        val user = AgentUser(
            id = id,
            fullName = prefs.getString(KEY_USER_NAME, "") ?: "",
            email = prefs.getString(KEY_USER_EMAIL, "") ?: "",
            phone = prefs.getString(KEY_USER_PHONE, "") ?: "",
            accountStatus = prefs.getString(KEY_USER_STATUS, "ACTIVE") ?: "ACTIVE",
            createdAt = prefs.getString(KEY_USER_CREATED_AT, null)
        )
        cachedUser = user
        userLoaded = true
        return user
    }

    fun saveSubscription(info: SubscriptionInfo) {
        cachedSubscription = info
        subLoaded = true
        prefs.edit()
            .putString(KEY_SUB_STATUS, info.status.code)
            .putString(KEY_SUB_TRIAL_START, info.trialStartedAt)
            .putString(KEY_SUB_TRIAL_END, info.trialExpiresAt)
            .putString(KEY_SUB_PAID_UNTIL, info.paidUntil)
            .putString(KEY_SUB_PLAN_ID, info.currentPlanId)
            .putString(KEY_SUB_PLAN_NAME, info.currentPlanName)
            .putInt(KEY_SUB_REMAINING_DAYS, info.remainingDays)
            .putBoolean(KEY_SUB_RECURRING, info.recurringAuthorized)
            .apply()
    }

    fun getSubscription(): SubscriptionInfo {
        if (subLoaded) {
            cachedSubscription?.let { return it }
        }
        val info = SubscriptionInfo(
            status = SubscriptionStatus.fromCode(prefs.getString(KEY_SUB_STATUS, "NONE")),
            trialStartedAt = prefs.getString(KEY_SUB_TRIAL_START, null),
            trialExpiresAt = prefs.getString(KEY_SUB_TRIAL_END, null),
            paidUntil = prefs.getString(KEY_SUB_PAID_UNTIL, null),
            currentPlanId = prefs.getString(KEY_SUB_PLAN_ID, null),
            currentPlanName = prefs.getString(KEY_SUB_PLAN_NAME, null),
            remainingDays = prefs.getInt(KEY_SUB_REMAINING_DAYS, 0),
            recurringAuthorized = prefs.getBoolean(KEY_SUB_RECURRING, false)
        )
        cachedSubscription = info
        subLoaded = true
        return info
    }

    fun clearSession() {
        val onboarding = onboardingCompleted
        val preservedDeviceId = deviceId
        cachedToken = null
        tokenLoaded = true
        cachedUser = null
        userLoaded = true
        cachedSubscription = SubscriptionInfo()
        subLoaded = true
        cachedOnboarding = onboarding
        onboardingLoaded = true
        cachedDeviceId = preservedDeviceId
        prefs.edit()
            .clear()
            .putBoolean(KEY_ONBOARDING_DONE, onboarding)
            .putString(KEY_DEVICE_ID, preservedDeviceId)
            .apply()
    }

    companion object {
        private const val KEY_TOKEN = "auth_token"
        private const val KEY_ONBOARDING_DONE = "onboarding_done"
        private const val KEY_DEVICE_ID = "stable_device_id"
        private const val KEY_USER_ID = "user_id"
        private const val KEY_USER_NAME = "user_name"
        private const val KEY_USER_EMAIL = "user_email"
        private const val KEY_USER_PHONE = "user_phone"
        private const val KEY_USER_STATUS = "user_status"
        private const val KEY_USER_CREATED_AT = "user_created_at"
        private const val KEY_SUB_RECURRING = "sub_recurring_authorized"
        private const val KEY_SUB_STATUS = "sub_status"
        private const val KEY_SUB_TRIAL_START = "sub_trial_start"
        private const val KEY_SUB_TRIAL_END = "sub_trial_end"
        private const val KEY_SUB_PAID_UNTIL = "sub_paid_until"
        private const val KEY_SUB_PLAN_ID = "sub_plan_id"
        private const val KEY_SUB_PLAN_NAME = "sub_plan_name"
        private const val KEY_SUB_REMAINING_DAYS = "sub_remaining_days"
    }
}

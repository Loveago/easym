package com.easymomo.app.data.local

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.easymomo.app.domain.model.AgentUser
import com.easymomo.app.domain.model.SubscriptionInfo
import com.easymomo.app.domain.model.SubscriptionStatus

/**
 * Securely persists JWT token, cached user info, and cached subscription entitlement.
 * NEVER stores financial PINs or Paystack secret keys.
 */
class SessionStore(private val context: Context) {

    private val prefs: SharedPreferences by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        createPreferences(context)
    }

    fun warmUp() {
        prefs
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
        get() = prefs.getString(KEY_TOKEN, null)
        set(value) = prefs.edit().putString(KEY_TOKEN, value).apply()

    var onboardingCompleted: Boolean
        get() = prefs.getBoolean(KEY_ONBOARDING_DONE, false)
        set(value) = prefs.edit().putBoolean(KEY_ONBOARDING_DONE, value).apply()

    fun saveUser(user: AgentUser) {
        prefs.edit()
            .putString(KEY_USER_ID, user.id)
            .putString(KEY_USER_NAME, user.fullName)
            .putString(KEY_USER_EMAIL, user.email)
            .putString(KEY_USER_PHONE, user.phone)
            .putString(KEY_USER_STATUS, user.accountStatus)
            .apply()
    }

    fun getUser(): AgentUser? {
        val id = prefs.getString(KEY_USER_ID, null) ?: return null
        return AgentUser(
            id = id,
            fullName = prefs.getString(KEY_USER_NAME, "") ?: "",
            email = prefs.getString(KEY_USER_EMAIL, "") ?: "",
            phone = prefs.getString(KEY_USER_PHONE, "") ?: "",
            accountStatus = prefs.getString(KEY_USER_STATUS, "ACTIVE") ?: "ACTIVE"
        )
    }

    fun saveSubscription(info: SubscriptionInfo) {
        prefs.edit()
            .putString(KEY_SUB_STATUS, info.status.code)
            .putString(KEY_SUB_TRIAL_START, info.trialStartedAt)
            .putString(KEY_SUB_TRIAL_END, info.trialExpiresAt)
            .putString(KEY_SUB_PAID_UNTIL, info.paidUntil)
            .putString(KEY_SUB_PLAN_ID, info.currentPlanId)
            .putString(KEY_SUB_PLAN_NAME, info.currentPlanName)
            .putInt(KEY_SUB_REMAINING_DAYS, info.remainingDays)
            .apply()
    }

    fun getSubscription(): SubscriptionInfo {
        return SubscriptionInfo(
            status = SubscriptionStatus.fromCode(prefs.getString(KEY_SUB_STATUS, "NONE")),
            trialStartedAt = prefs.getString(KEY_SUB_TRIAL_START, null),
            trialExpiresAt = prefs.getString(KEY_SUB_TRIAL_END, null),
            paidUntil = prefs.getString(KEY_SUB_PAID_UNTIL, null),
            currentPlanId = prefs.getString(KEY_SUB_PLAN_ID, null),
            currentPlanName = prefs.getString(KEY_SUB_PLAN_NAME, null),
            remainingDays = prefs.getInt(KEY_SUB_REMAINING_DAYS, 0)
        )
    }

    fun clearSession() {
        val onboarding = onboardingCompleted
        prefs.edit().clear().apply()
        onboardingCompleted = onboarding
    }

    companion object {
        private const val KEY_TOKEN = "auth_token"
        private const val KEY_ONBOARDING_DONE = "onboarding_done"
        private const val KEY_USER_ID = "user_id"
        private const val KEY_USER_NAME = "user_name"
        private const val KEY_USER_EMAIL = "user_email"
        private const val KEY_USER_PHONE = "user_phone"
        private const val KEY_USER_STATUS = "user_status"
        private const val KEY_SUB_STATUS = "sub_status"
        private const val KEY_SUB_TRIAL_START = "sub_trial_start"
        private const val KEY_SUB_TRIAL_END = "sub_trial_end"
        private const val KEY_SUB_PAID_UNTIL = "sub_paid_until"
        private const val KEY_SUB_PLAN_ID = "sub_plan_id"
        private const val KEY_SUB_PLAN_NAME = "sub_plan_name"
        private const val KEY_SUB_REMAINING_DAYS = "sub_remaining_days"
    }
}

package com.easymomo.app.ussd

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.text.TextUtils
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

enum class CapabilityStatus {
    GRANTED,
    DENIED,
    PERMANENTLY_DENIED,
    DISABLED_IN_SETTINGS,
    UNAVAILABLE
}

data class CapabilityDiagnostic(
    val name: String,
    val status: CapabilityStatus,
    val reason: String,
    val isRequiredForAutoUssd: Boolean
)

data class PermissionReport(
    val callPhone: CapabilityStatus,
    val readPhoneState: CapabilityStatus,
    val readPhoneNumbers: CapabilityStatus,
    val accessibilityService: CapabilityStatus,
    val notifications: CapabilityStatus,
    val notificationListener: CapabilityStatus = CapabilityStatus.DISABLED_IN_SETTINGS,
    val hasTelephonyHardware: Boolean
) {
    val canLaunchUssd: Boolean
        get() = hasTelephonyHardware && callPhone == CapabilityStatus.GRANTED

    val canDetectSims: Boolean
        get() = hasTelephonyHardware && readPhoneState == CapabilityStatus.GRANTED

    val canAutomateInteractiveUssd: Boolean
        get() = canLaunchUssd && accessibilityService == CapabilityStatus.GRANTED

    fun toDiagnosticsList(): List<CapabilityDiagnostic> = listOf(
        CapabilityDiagnostic(
            name = "CALL_PHONE",
            status = callPhone,
            reason = "Required to initiate USSD calls via Android's dialer/telephony API.",
            isRequiredForAutoUssd = true
        ),
        CapabilityDiagnostic(
            name = "READ_PHONE_STATE",
            status = readPhoneState,
            reason = "Required by SubscriptionManager to detect active SIM slots and carriers.",
            isRequiredForAutoUssd = true
        ),
        CapabilityDiagnostic(
            name = "READ_PHONE_NUMBERS",
            status = readPhoneNumbers,
            reason = "Optional: allows displaying SIM phone numbers in SIM Management where carrier exposes it.",
            isRequiredForAutoUssd = false
        ),
        CapabilityDiagnostic(
            name = "EasyMoMo USSD Accessibility Service",
            status = accessibilityService,
            reason = "Required for reading USSD prompts and auto-entering customer phone, amount, and menu options.",
            isRequiredForAutoUssd = true
        ),
        CapabilityDiagnostic(
            name = "Live MoMo SMS Capture (Notification Access)",
            status = notificationListener,
            reason = "Optional: reads incoming MoMo/Telecel/AT SMS notifications to auto-confirm pending transactions (no READ_SMS).",
            isRequiredForAutoUssd = false
        ),
        CapabilityDiagnostic(
            name = "POST_NOTIFICATIONS",
            status = notifications,
            reason = "Optional: displays non-sensitive transaction progress alerts.",
            isRequiredForAutoUssd = false
        )
    )
}

/**
 * Dedicated permission and capability checker for USSD execution, SIM detection,
 * and AccessibilityService status. Never bypasses Android settings or prompts repeatedly.
 */
class PermissionCapabilityManager(private val context: Context) {

    fun checkAll(activity: Activity? = null): PermissionReport {
        val hasTelephony = context.packageManager.hasSystemFeature(PackageManager.FEATURE_TELEPHONY)
        return PermissionReport(
            callPhone = checkRuntimePermission(Manifest.permission.CALL_PHONE, activity),
            readPhoneState = checkRuntimePermission(Manifest.permission.READ_PHONE_STATE, activity),
            readPhoneNumbers = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                checkRuntimePermission(Manifest.permission.READ_PHONE_NUMBERS, activity)
            } else {
                CapabilityStatus.UNAVAILABLE
            },
            accessibilityService = if (isAccessibilityServiceEnabled()) {
                CapabilityStatus.GRANTED
            } else {
                CapabilityStatus.DISABLED_IN_SETTINGS
            },
            notifications = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                checkRuntimePermission(Manifest.permission.POST_NOTIFICATIONS, activity)
            } else {
                CapabilityStatus.GRANTED
            },
            notificationListener = if (isNotificationListenerEnabled()) {
                CapabilityStatus.GRANTED
            } else {
                CapabilityStatus.DISABLED_IN_SETTINGS
            },
            hasTelephonyHardware = hasTelephony
        )
    }

    private fun checkRuntimePermission(permission: String, activity: Activity?): CapabilityStatus {
        val granted = ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
        if (granted) return CapabilityStatus.GRANTED
        if (activity != null) {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val wasRequested = prefs.getBoolean("req_$permission", false)
            val shouldShow = ActivityCompat.shouldShowRequestPermissionRationale(activity, permission)
            if (wasRequested && !shouldShow) {
                return CapabilityStatus.PERMANENTLY_DENIED
            }
        }
        return CapabilityStatus.DENIED
    }

    fun markPermissionRequested(permissions: Array<String>) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().apply {
            permissions.forEach { putBoolean("req_$it", true) }
        }.apply()
    }

    fun isAccessibilityServiceEnabled(): Boolean {
        val enabledServices = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false
        val splitter = TextUtils.SimpleStringSplitter(':')
        splitter.setString(enabledServices)
        val fullName = "${context.packageName}/${EasyMoMoAccessibilityService::class.java.name}"
        val shortName = "${context.packageName}/.ussd.EasyMoMoAccessibilityService"
        while (splitter.hasNext()) {
            val component = splitter.next()
            if (component.equals(fullName, ignoreCase = true) ||
                component.equals(shortName, ignoreCase = true)
            ) {
                return true
            }
        }
        return false
    }

    fun createAccessibilitySettingsIntent(): Intent {
        return Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    fun isNotificationListenerEnabled(): Boolean {
        val flat = Settings.Secure.getString(
            context.contentResolver,
            "enabled_notification_listeners"
        ) ?: return false
        val fullName = "${context.packageName}/com.easymomo.app.ussd.sms.MoMoNotificationListenerService"
        val shortName = "${context.packageName}/.ussd.sms.MoMoNotificationListenerService"
        return flat.split(":").any {
            it.equals(fullName, ignoreCase = true) || it.equals(shortName, ignoreCase = true)
        }
    }

    fun createNotificationListenerSettingsIntent(): Intent {
        return Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    fun createAppSettingsIntent(): Intent {
        return Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", context.packageName, null)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    fun requiredUssdPermissions(): Array<String> {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            arrayOf(
                Manifest.permission.CALL_PHONE,
                Manifest.permission.READ_PHONE_STATE,
                Manifest.permission.READ_PHONE_NUMBERS
            )
        } else {
            arrayOf(
                Manifest.permission.CALL_PHONE,
                Manifest.permission.READ_PHONE_STATE
            )
        }
    }

    companion object {
        private const val PREFS_NAME = "easymomo_perm_state"
    }
}

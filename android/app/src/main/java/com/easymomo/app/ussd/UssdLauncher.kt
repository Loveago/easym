package com.easymomo.app.ussd

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.telecom.PhoneAccountHandle
import android.telecom.TelecomManager
import android.telephony.SubscriptionInfo as AndroidSubscriptionInfo
import android.telephony.SubscriptionManager
import android.telephony.TelephonyManager
import android.text.TextUtils
import androidx.core.content.ContextCompat

data class SimSlotInfo(
    val subscriptionId: Int,
    val slotIndex: Int,
    val carrierName: String,
    val displayName: String
)

sealed interface UssdLaunchResult {
    data class LaunchedViaTelephony(val simSlot: SimSlotInfo?) : UssdLaunchResult
    data object LaunchedViaDialer : UssdLaunchResult
    data class Error(
        val message: String,
        val requiresPermission: Boolean = false,
        val requiresSimSettings: Boolean = false
    ) : UssdLaunchResult
}

/**
 * Checks SIM availability, permissions, and initiates USSD sessions using approved Android APIs.
 */
class UssdLauncher(private val context: Context) {

    fun hasCallPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.CALL_PHONE
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun hasPhoneStatePermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_PHONE_STATE
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun isAccessibilityAssistantEnabled(): Boolean {
        val enabledServices = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false
        val colonSplitter = TextUtils.SimpleStringSplitter(':')
        colonSplitter.setString(enabledServices)
        val expectedComponent = "${context.packageName}/${EasyMoMoAccessibilityService::class.java.name}"
        val shortComponent = "${context.packageName}/.ussd.EasyMoMoAccessibilityService"
        while (colonSplitter.hasNext()) {
            val component = colonSplitter.next()
            if (component.equals(expectedComponent, ignoreCase = true) ||
                component.equals(shortComponent, ignoreCase = true)
            ) {
                return true
            }
        }
        return false
    }

    @SuppressLint("MissingPermission")
    fun getAvailableSims(): List<SimSlotInfo> {
        if (!hasPhoneStatePermission()) return emptyList()
        val subManager = context.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE) as? SubscriptionManager
            ?: return emptyList()
        val list: List<AndroidSubscriptionInfo> = subManager.activeSubscriptionInfoList ?: emptyList()
        return list.map { info ->
            SimSlotInfo(
                subscriptionId = info.subscriptionId,
                slotIndex = info.simSlotIndex,
                carrierName = info.carrierName?.toString().orEmpty(),
                displayName = info.displayName?.toString().orEmpty().ifBlank { "SIM ${info.simSlotIndex + 1}" }
            )
        }
    }

    @SuppressLint("MissingPermission")
    fun launchUssd(
        ussdCode: String,
        subscriptionId: Int? = null,
        forceInteractiveDialog: Boolean = true,
        onTelephonyResponse: (String) -> Unit,
        onTelephonyFailure: (Int) -> Unit
    ): UssdLaunchResult {
        if (ussdCode.isBlank() || ussdCode == "VERIFY_FROM_AGENT_SIM") {
            return UssdLaunchResult.Error("USSD CONFIGURATION REQUIRED — VERIFY WITH AUTHORIZED AGENT SIM")
        }
        if (!hasCallPermission()) {
            return UssdLaunchResult.Error(
                "Phone call permission is required to initiate USSD sessions.",
                requiresPermission = true
            )
        }

        val baseTelephony = context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
            ?: return UssdLaunchResult.Error("Telephony service unavailable on this device.")

        val telephony = if (subscriptionId != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            baseTelephony.createForSubscriptionId(subscriptionId)
        } else {
            baseTelephony
        }

        // Prefer interactive ACTION_CALL for multi-step USSD menus so the system USSD prompt
        // opens and the agent can enter their PIN at the final step; use sendUssdRequest only
        // for single-shot non-interactive queries when AccessibilityService is off.
        return try {
            if (!forceInteractiveDialog && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !isAccessibilityAssistantEnabled()) {
                telephony.sendUssdRequest(
                    ussdCode,
                    object : TelephonyManager.UssdResponseCallback() {
                        override fun onReceiveUssdResponse(
                            telephonyManager: TelephonyManager,
                            request: String,
                            response: CharSequence
                        ) {
                            val text = response.toString()
                            UssdPromptObserver.onPromptObserved(text, UssdPromptObserver.PromptSource.TELEPHONY_CALLBACK)
                            onTelephonyResponse(text)
                        }

                        override fun onReceiveUssdResponseFailed(
                            telephonyManager: TelephonyManager,
                            request: String,
                            failureCode: Int
                        ) {
                            onTelephonyFailure(failureCode)
                        }
                    },
                    Handler(Looper.getMainLooper())
                )
                UssdLaunchResult.LaunchedViaTelephony(getAvailableSims().firstOrNull {
                    subscriptionId == null || it.subscriptionId == subscriptionId
                })
            } else {
                // Interactive multi-step USSD via ACTION_CALL with TelecomManager.EXTRA_PHONE_ACCOUNT_HANDLE
                val availableSims = getAvailableSims()
                var phoneAccountHandle: PhoneAccountHandle? = null
                if (subscriptionId != null) {
                    phoneAccountHandle = resolvePhoneAccountHandle(subscriptionId)
                    if (availableSims.size > 1 && phoneAccountHandle == null) {
                        UssdDiagnosticLog.log(
                            "SIM",
                            "Dual-SIM routing unavailable for subId=$subscriptionId; refusing silent fallback"
                        )
                        return UssdLaunchResult.Error(
                            "Could not route USSD to the assigned SIM on this dual-SIM device. Please verify your SIM assignment in Settings → SIM Management.",
                            requiresSimSettings = true
                        )
                    }
                }

                val encodedHash = Uri.encode("#")
                val formatted = ussdCode.replace("#", encodedHash)
                val intent = Intent(Intent.ACTION_CALL, Uri.parse("tel:$formatted")).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    if (phoneAccountHandle != null) {
                        putExtra(TelecomManager.EXTRA_PHONE_ACCOUNT_HANDLE, phoneAccountHandle)
                    }
                }
                UssdDiagnosticLog.log(
                    "LAUNCH",
                    "Dialing $ussdCode via ACTION_CALL (subId=$subscriptionId, handleMatched=${phoneAccountHandle != null})"
                )
                context.startActivity(intent)
                UssdLaunchResult.LaunchedViaDialer
            }
        } catch (e: SecurityException) {
            UssdLaunchResult.Error("Permission denied when launching USSD: ${e.message}", requiresPermission = true)
        } catch (e: Exception) {
            UssdLaunchResult.Error("Could not launch USSD: ${e.message}")
        }
    }

    @SuppressLint("MissingPermission")
    private fun resolvePhoneAccountHandle(subscriptionId: Int): PhoneAccountHandle? {
        if (!hasPhoneStatePermission()) return null
        val telecomManager = context.getSystemService(Context.TELECOM_SERVICE) as? TelecomManager ?: return null
        val baseTelephony = context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager ?: return null

        val accounts = runCatching { telecomManager.callCapablePhoneAccounts }.getOrNull().orEmpty()
        for (handle in accounts) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val handleSubId = runCatching { baseTelephony.getSubscriptionId(handle) }.getOrNull()
                if (handleSubId == subscriptionId) return handle
            }
            if (handle.id == subscriptionId.toString()) {
                return handle
            }
        }
        return null
    }
}

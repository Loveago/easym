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
import android.telecom.PhoneAccount
import android.telecom.PhoneAccountHandle
import android.telecom.TelecomManager
import android.telephony.SubscriptionInfo as AndroidSubscriptionInfo
import android.telephony.SubscriptionManager
import android.telephony.TelephonyManager
import android.text.TextUtils
import androidx.core.content.ContextCompat

internal data class DualSimAccountCandidate(
    val id: String,
    val subscriptionIdFromApi: Int? = null,
    val extraSubscriptionId: Int? = null,
    val sortOrder: Int? = null,
    val label: String = "",
    val shortDescription: String = "",
    val addressDigits: String = "",
    val isSimAccount: Boolean = true
)

internal data class DualSimSubscriptionDescriptor(
    val subscriptionId: Int,
    val slotIndex: Int,
    val iccId: String = "",
    val displayName: String = "",
    val carrierName: String = "",
    val numberDigits: String = ""
)

private fun normalizeIccId(raw: String): String {
    return raw
        .trim()
        .uppercase()
        .replace(Regex("[^A-Z0-9]"), "")
        .trimEnd('F')
}

private fun extractDigits(raw: String?): String =
    raw.orEmpty().filter { it.isDigit() }

/**
 * Correlates a target SubscriptionManager subscriptionId to the matching Telecom PhoneAccount
 * index across AOSP and OEM dual-SIM implementations (including devices with blank ICCIDs or
 * custom PhoneAccountHandle IDs).
 */
internal fun matchDualSimAccountIndex(
    targetSubscriptionId: Int,
    activeSubsSortedBySlot: List<DualSimSubscriptionDescriptor>,
    candidates: List<DualSimAccountCandidate>
): Int? {
    if (candidates.isEmpty()) return null
    val simIndexed = candidates.withIndex()
        .filter { it.value.isSimAccount }
        .ifEmpty { candidates.withIndex().toList() }

    // 1. Official Android R+ TelephonyManager.getSubscriptionId(PhoneAccountHandle)
    simIndexed.firstOrNull { it.value.subscriptionIdFromApi == targetSubscriptionId }
        ?.let { return it.index }

    // 2. OEM Telecom PhoneAccount extras subscription ID
    simIndexed.firstOrNull { it.value.extraSubscriptionId == targetSubscriptionId }
        ?.let { return it.index }

    // 3. Exact PhoneAccountHandle.id == subscriptionId
    simIndexed.firstOrNull { it.value.id == targetSubscriptionId.toString() }
        ?.let { return it.index }

    val targetSub = activeSubsSortedBySlot.firstOrNull { it.subscriptionId == targetSubscriptionId }

    // 4. Normalized ICCID match (stripping non-alphanumeric chars and trailing 'F' padding)
    val targetIcc = targetSub?.iccId?.let(::normalizeIccId).orEmpty()
    if (targetIcc.length >= 6) {
        val iccMatches = simIndexed.filter { (_, c) ->
            val candIcc = normalizeIccId(c.id)
            candIcc.isNotEmpty() && (candIcc == targetIcc || candIcc.contains(targetIcc) || targetIcc.contains(candIcc))
        }
        if (iccMatches.size == 1) return iccMatches.first().index
    }

    // 5. Phone number suffix match (when unique)
    val targetNum = targetSub?.numberDigits.orEmpty()
    if (targetNum.length >= 7) {
        val suffix = targetNum.takeLast(minOf(9, targetNum.length))
        val numMatches = simIndexed.filter { (_, c) ->
            c.addressDigits.length >= suffix.length && c.addressDigits.endsWith(suffix)
        }
        if (numMatches.size == 1) return numMatches.first().index
    }

    // 6. Unique label/shortDescription match (never match if another active SIM shares that text)
    if (targetSub != null) {
        val otherLabels = activeSubsSortedBySlot
            .filter { it.subscriptionId != targetSubscriptionId }
            .flatMap { listOf(it.displayName.trim(), it.carrierName.trim()) }
            .filter { it.isNotBlank() }
            .map { it.lowercase() }
            .toSet()

        val uniqueTargetLabels = listOf(targetSub.displayName.trim(), targetSub.carrierName.trim())
            .filter { it.isNotBlank() && it.lowercase() !in otherLabels }

        for (label in uniqueTargetLabels) {
            val labelMatches = simIndexed.filter { (_, c) ->
                val cLabel = c.label.trim()
                val cDesc = c.shortDescription.trim()
                cLabel.equals(label, ignoreCase = true) ||
                    cDesc.equals(label, ignoreCase = true) ||
                    (cLabel.isNotBlank() && (cLabel.contains(label, ignoreCase = true) || label.contains(cLabel, ignoreCase = true))) ||
                    (cDesc.isNotBlank() && (cDesc.contains(label, ignoreCase = true) || label.contains(cDesc, ignoreCase = true)))
            }
            if (labelMatches.size == 1) return labelMatches.first().index
        }
    }

    // 7. PhoneAccount EXTRA_SORT_ORDER matching slotIndex
    if (targetSub != null) {
        val sortMatches = simIndexed.filter { it.value.sortOrder == targetSub.slotIndex }
        if (sortMatches.size == 1) return sortMatches.first().index
    }

    // 8. Slot token inside PhoneAccountHandle.id (e.g. "slot0", "slot_1", "sim1", "sim_2", "0", "1")
    if (targetSub != null) {
        val slot = targetSub.slotIndex
        val slotTokens = setOf("slot$slot", "slot_$slot", "sim${slot + 1}", "sim_${slot + 1}", slot.toString())
        val tokenMatches = simIndexed.filter { (_, c) ->
            val low = c.id.trim().lowercase()
            low in slotTokens || Regex("""\b(?:slot[_-]?$slot|sim[_-]?${slot + 1})\b""").containsMatchIn(low)
        }
        if (tokenMatches.size == 1) return tokenMatches.first().index
    }

    // 9. Ordered slot-index correlation among SIM PhoneAccounts (sorted by sortOrder, ties keep Telecom order)
    if (targetSub != null) {
        val sortedSimIndexed = simIndexed.withIndex().sortedWith(
            compareBy<IndexedValue<IndexedValue<DualSimAccountCandidate>>>(
                { it.value.value.sortOrder ?: Int.MAX_VALUE },
                { it.index }
            )
        ).map { it.value }

        val orderIdx = activeSubsSortedBySlot.indexOfFirst { it.subscriptionId == targetSubscriptionId }
        if (sortedSimIndexed.size == activeSubsSortedBySlot.size && orderIdx in sortedSimIndexed.indices) {
            return sortedSimIndexed[orderIdx].index
        }
        if (targetSub.slotIndex in sortedSimIndexed.indices) {
            return sortedSimIndexed[targetSub.slotIndex].index
        }
    }

    return null
}

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
                // plus OEM slot/subscription extras for dual-SIM compatibility
                val availableSims = getAvailableSims()
                val targetSlotIndex = if (subscriptionId != null) {
                    availableSims.firstOrNull { it.subscriptionId == subscriptionId }?.slotIndex
                } else {
                    null
                }
                var phoneAccountHandle: PhoneAccountHandle? = null
                if (subscriptionId != null) {
                    phoneAccountHandle = resolvePhoneAccountHandle(subscriptionId)
                    if (availableSims.size > 1 && phoneAccountHandle == null) {
                        if (targetSlotIndex == null) {
                            UssdDiagnosticLog.log(
                                "SIM",
                                "Dual-SIM routing unavailable for subId=$subscriptionId; refusing silent fallback"
                            )
                            return UssdLaunchResult.Error(
                                "Could not route USSD to the assigned SIM on this dual-SIM device. Please verify your SIM assignment in Settings → SIM Management.",
                                requiresSimSettings = true
                            )
                        }
                        UssdDiagnosticLog.log(
                            "SIM",
                            "TelecomManager returned no matching handle for subId=$subscriptionId; using OEM slot extras for slot=$targetSlotIndex"
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
                    if (targetSlotIndex != null && subscriptionId != null) {
                        putExtra("com.android.phone.force.slot", true)
                        putExtra("com.android.phone.extra.slot", targetSlotIndex)
                        putExtra("simSlot", targetSlotIndex)
                        putExtra("slot", targetSlotIndex)
                        putExtra("subscription", subscriptionId)
                        putExtra("android.telephony.extra.SUBSCRIPTION_INDEX", subscriptionId)
                    }
                }
                UssdDiagnosticLog.log(
                    "LAUNCH",
                    "Dialing $ussdCode via ACTION_CALL (subId=$subscriptionId, slot=$targetSlotIndex, handleMatched=${phoneAccountHandle != null})"
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
    @Suppress("DEPRECATION")
    private fun resolvePhoneAccountHandle(subscriptionId: Int): PhoneAccountHandle? {
        if (!hasPhoneStatePermission()) return null
        val telecomManager = context.getSystemService(Context.TELECOM_SERVICE) as? TelecomManager ?: return null
        val baseTelephony = context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager ?: return null
        val subManager = context.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE) as? SubscriptionManager

        val activeSubsSorted = subManager?.activeSubscriptionInfoList
            .orEmpty()
            .sortedBy { it.simSlotIndex }
            .map { info ->
                val rawNum = runCatching {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        subManager?.getPhoneNumber(info.subscriptionId)
                    } else {
                        info.number
                    }
                }.getOrNull()
                DualSimSubscriptionDescriptor(
                    subscriptionId = info.subscriptionId,
                    slotIndex = info.simSlotIndex,
                    iccId = info.iccId.orEmpty(),
                    displayName = info.displayName?.toString().orEmpty(),
                    carrierName = info.carrierName?.toString().orEmpty(),
                    numberDigits = extractDigits(rawNum)
                )
            }

        val accounts = runCatching { telecomManager.callCapablePhoneAccounts }.getOrNull().orEmpty()
        if (accounts.isEmpty()) return null

        val sortOrderKey = "android.telecom.extra.SORT_ORDER"

        val candidates = accounts.map { handle ->
            val phoneAccount = runCatching { telecomManager.getPhoneAccount(handle) }.getOrNull()
            val extras = phoneAccount?.extras
            val apiSubId = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                runCatching { baseTelephony.getSubscriptionId(handle) }
                    .getOrNull()
                    ?.takeIf { it >= 0 }
            } else {
                null
            }
            val extraSubId = listOf(
                "android.telephony.extra.SUBSCRIPTION_INDEX",
                "android.telecom.extra.SUBSCRIPTION_ID",
                "subscription",
                "sub_id"
            ).firstNotNullOfOrNull { key ->
                if (extras != null && extras.containsKey(key)) {
                    extras.getInt(key, -1).takeIf { it >= 0 }
                        ?: extras.getString(key)?.toIntOrNull()?.takeIf { it >= 0 }
                } else {
                    null
                }
            }
            val sortOrder = if (extras != null && extras.containsKey(sortOrderKey)) {
                extras.getInt(sortOrderKey, -1).takeIf { it >= 0 }
                    ?: extras.getString(sortOrderKey)?.toIntOrNull()?.takeIf { it >= 0 }
            } else {
                null
            }
            val compPkg = handle.componentName.packageName.lowercase()
            val compCls = handle.componentName.className.lowercase()
            val isSimAccount = phoneAccount?.hasCapabilities(PhoneAccount.CAPABILITY_SIM_SUBSCRIPTION) == true ||
                compPkg.contains("com.android.phone") ||
                compCls.contains("telephony") ||
                compPkg.contains("telecom")

            DualSimAccountCandidate(
                id = handle.id.orEmpty(),
                subscriptionIdFromApi = apiSubId,
                extraSubscriptionId = extraSubId,
                sortOrder = sortOrder,
                label = phoneAccount?.label?.toString().orEmpty(),
                shortDescription = phoneAccount?.shortDescription?.toString().orEmpty(),
                addressDigits = extractDigits(phoneAccount?.address?.schemeSpecificPart),
                isSimAccount = isSimAccount
            )
        }

        val matchedIndex = matchDualSimAccountIndex(subscriptionId, activeSubsSorted, candidates)
        return matchedIndex?.let { accounts.getOrNull(it) }
    }
}

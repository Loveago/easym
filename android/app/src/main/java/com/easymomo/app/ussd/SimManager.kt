package com.easymomo.app.ussd

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import android.telephony.SubscriptionInfo
import android.telephony.SubscriptionManager
import com.easymomo.app.data.local.AgentSettings
import com.easymomo.app.data.local.SavedSimSelection
import com.easymomo.app.data.local.SimPreferencesStore
import com.easymomo.app.data.local.SimSelectionStore
import com.easymomo.app.domain.model.NetworkType
import com.easymomo.app.domain.model.TransactionType
import java.security.MessageDigest

data class ActiveSimInfo(
    val subscriptionId: Int,
    val slotIndex: Int,
    val carrierName: String,
    val displayName: String,
    val phoneNumber: String?,
    val iccIdHash: String,
    val isEmbedded: Boolean,
    val detectedCarrierNetwork: NetworkType
) {
    val label: String
        get() {
            val slot = if (isEmbedded) "eSIM" else "SIM ${slotIndex + 1}"
            val carrier = carrierName.ifBlank { displayName.ifBlank { "Unknown Carrier" } }
            return "$slot • $carrier"
        }
}

enum class SimResolutionStatus {
    READY,
    REQUIRES_SELECTION,
    SIM_UNAVAILABLE_ATTENTION_REQUIRED,
    NETWORK_MISMATCH_WARNING,
    NO_ACTIVE_SIM,
    PERMISSION_REQUIRED
}

data class NetworkSimResolution(
    val network: NetworkType,
    val status: SimResolutionStatus,
    val resolvedSim: ActiveSimInfo?,
    val savedSelection: SavedSimSelection?,
    val message: String
)

/**
 * Manages active SIM / eSIM detection via SubscriptionManager and reconciles
 * per-network DataStore preferences before every USSD session.
 * Never silently switches to another SIM on dual-SIM devices.
 */
class SimManager(
    private val context: Context,
    private val simPreferencesStore: SimSelectionStore,
    private val permissionManager: PermissionCapabilityManager
) {

    @SuppressLint("MissingPermission")
    fun getActiveSims(): List<ActiveSimInfo> {
        val perms = permissionManager.checkAll()
        if (perms.readPhoneState != CapabilityStatus.GRANTED) return emptyList()

        val subManager = context.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE) as? SubscriptionManager
            ?: return emptyList()
        val list: List<SubscriptionInfo> = subManager.activeSubscriptionInfoList ?: emptyList()

        return list.map { info ->
            val phoneNum = readPhoneNumberSafely(subManager, info, perms)
            val carrier = info.carrierName?.toString().orEmpty()
            val display = info.displayName?.toString().orEmpty().ifBlank { "SIM ${info.simSlotIndex + 1}" }
            val iccRaw = info.iccId.orEmpty().ifBlank { "sub_${info.subscriptionId}" }
            ActiveSimInfo(
                subscriptionId = info.subscriptionId,
                slotIndex = info.simSlotIndex,
                carrierName = carrier,
                displayName = display,
                phoneNumber = phoneNum?.takeIf { it.isNotBlank() },
                iccIdHash = sha256Short(iccRaw),
                isEmbedded = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) info.isEmbedded else false,
                detectedCarrierNetwork = inferNetworkFromCarrier("$carrier $display")
            )
        }.sortedBy { it.slotIndex }
    }

    @SuppressLint("MissingPermission")
    @Suppress("DEPRECATION")
    private fun readPhoneNumberSafely(
        subManager: SubscriptionManager,
        info: SubscriptionInfo,
        perms: PermissionReport
    ): String? {
        if (perms.readPhoneNumbers != CapabilityStatus.GRANTED &&
            perms.readPhoneState != CapabilityStatus.GRANTED
        ) {
            return null
        }
        return runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                if (perms.readPhoneNumbers == CapabilityStatus.GRANTED) {
                    subManager.getPhoneNumber(info.subscriptionId)
                } else {
                    null
                }
            } else {
                info.number
            }
        }.getOrNull()
    }

    /**
     * Reconciles all 3 network assignments against currently active SIMs.
     * - Single SIM: automatically assigns the only active SIM.
     * - Dual SIM: requires explicit assignment per network; detects removed/replaced SIMs.
     */
    suspend fun reconcileAllNetworks(
        activeSimsOverride: List<ActiveSimInfo>? = null,
        permsOverride: PermissionReport? = null
    ): Map<NetworkType, NetworkSimResolution> {
        val perms = permsOverride ?: permissionManager.checkAll()
        val activeSims = activeSimsOverride ?: getActiveSims()
        val networks = listOf(NetworkType.MTN, NetworkType.AT, NetworkType.TELECEL)

        return networks.associateWith { net ->
            resolveForNetwork(net, activeSims, perms)
        }
    }

    suspend fun resolveForNetwork(network: NetworkType): NetworkSimResolution {
        val perms = permissionManager.checkAll()
        val activeSims = getActiveSims()
        return resolveForNetwork(network, activeSims, perms)
    }

    suspend fun resolveForNetworkAndFeature(
        network: NetworkType,
        transactionType: TransactionType
    ): NetworkSimResolution {
        val perms = permissionManager.checkAll()
        val activeSims = getActiveSims()
        return resolveForNetworkAndFeature(network, transactionType, activeSims, perms)
    }

    internal suspend fun resolveForNetworkAndFeature(
        network: NetworkType,
        transactionType: TransactionType,
        activeSims: List<ActiveSimInfo>,
        perms: PermissionReport
    ): NetworkSimResolution {
        if (perms.readPhoneState != CapabilityStatus.GRANTED || activeSims.isEmpty()) {
            return resolveForNetwork(network, activeSims, perms)
        }
        val featureSaved = simPreferencesStore.getFeatureSelection(network, transactionType)
        if (featureSaved == null) {
            // Fall back to the assigned per-network SIM (or single-SIM auto-selection / dual-SIM prompt).
            return resolveForNetwork(network, activeSims, perms)
        }

        val matched = activeSims.firstOrNull { matchesSaved(it, featureSaved) }
            ?: return NetworkSimResolution(
                network = network,
                status = SimResolutionStatus.SIM_UNAVAILABLE_ATTENTION_REQUIRED,
                resolvedSim = null,
                savedSelection = featureSaved,
                message = "Saved SIM for ${network.displayName} ${transactionType.displayName} (${featureSaved.displayName}) is unavailable. Re-select in Settings → SIM Management."
            )

        val mismatch = matched.detectedCarrierNetwork != NetworkType.UNKNOWN &&
            matched.detectedCarrierNetwork != network
        return NetworkSimResolution(
            network = network,
            status = if (mismatch) SimResolutionStatus.NETWORK_MISMATCH_WARNING else SimResolutionStatus.READY,
            resolvedSim = matched,
            savedSelection = featureSaved,
            message = if (mismatch) {
                "${matched.label} appears to be ${matched.detectedCarrierNetwork.displayName}, not ${network.displayName}."
            } else {
                "Using ${matched.label} (${transactionType.displayName})"
            }
        )
    }

    private suspend fun resolveForNetwork(
        network: NetworkType,
        activeSims: List<ActiveSimInfo>,
        perms: PermissionReport
    ): NetworkSimResolution {
        if (perms.readPhoneState != CapabilityStatus.GRANTED) {
            return NetworkSimResolution(
                network = network,
                status = SimResolutionStatus.PERMISSION_REQUIRED,
                resolvedSim = null,
                savedSelection = simPreferencesStore.getSelection(network),
                message = "READ_PHONE_STATE permission is required to inspect active SIMs."
            )
        }
        if (activeSims.isEmpty()) {
            return NetworkSimResolution(
                network = network,
                status = SimResolutionStatus.NO_ACTIVE_SIM,
                resolvedSim = null,
                savedSelection = simPreferencesStore.getSelection(network),
                message = "No active SIM card detected. Insert an authorized Agent / EVD SIM."
            )
        }

        val saved = simPreferencesStore.getSelection(network)

        // Single-SIM device: automatically use the single active SIM unless a different saved SIM was removed
        if (activeSims.size == 1) {
            val onlySim = activeSims.first()
            if (saved == null || matchesSaved(onlySim, saved)) {
                if (saved == null) {
                    assignSimToNetwork(network, onlySim)
                }
                val mismatch = onlySim.detectedCarrierNetwork != NetworkType.UNKNOWN &&
                    onlySim.detectedCarrierNetwork != network
                return NetworkSimResolution(
                    network = network,
                    status = if (mismatch) SimResolutionStatus.NETWORK_MISMATCH_WARNING else SimResolutionStatus.READY,
                    resolvedSim = onlySim,
                    savedSelection = simPreferencesStore.getSelection(network),
                    message = if (mismatch) {
                        "${onlySim.label} appears to be ${onlySim.detectedCarrierNetwork.displayName}, not ${network.displayName}."
                    } else {
                        "Using ${onlySim.label}"
                    }
                )
            } else {
                return NetworkSimResolution(
                    network = network,
                    status = SimResolutionStatus.SIM_UNAVAILABLE_ATTENTION_REQUIRED,
                    resolvedSim = null,
                    savedSelection = saved,
                    message = "Previously selected SIM (${saved.displayName}) was removed or replaced. Please confirm SIM selection in Settings."
                )
            }
        }

        // Dual-SIM / Multi-SIM device: never silently fall back or guess
        if (saved == null) {
            return NetworkSimResolution(
                network = network,
                status = SimResolutionStatus.REQUIRES_SELECTION,
                resolvedSim = null,
                savedSelection = null,
                message = "Multiple SIMs detected. Assign a SIM for ${network.displayName} in Settings → SIM Management."
            )
        }

        val matched = activeSims.firstOrNull { matchesSaved(it, saved) }
        if (matched == null) {
            return NetworkSimResolution(
                network = network,
                status = SimResolutionStatus.SIM_UNAVAILABLE_ATTENTION_REQUIRED,
                resolvedSim = null,
                savedSelection = saved,
                message = "Saved SIM for ${network.displayName} (${saved.displayName}) is unavailable or moved. Re-select in Settings → SIM Management."
            )
        }

        val mismatch = matched.detectedCarrierNetwork != NetworkType.UNKNOWN &&
            matched.detectedCarrierNetwork != network
        return NetworkSimResolution(
            network = network,
            status = if (mismatch) SimResolutionStatus.NETWORK_MISMATCH_WARNING else SimResolutionStatus.READY,
            resolvedSim = matched,
            savedSelection = saved,
            message = if (mismatch) {
                "Warning: ${matched.label} looks like ${matched.detectedCarrierNetwork.displayName}, selected for ${network.displayName}."
            } else {
                "Assigned: ${matched.label}"
            }
        )
    }

    suspend fun assignSimToNetwork(network: NetworkType, sim: ActiveSimInfo) {
        simPreferencesStore.saveSelection(sim.toSavedSelection(network))
    }

    val telecelOperationalIdFlow = simPreferencesStore.telecelOperationalIdFlow
    val agentSettingsFlow = simPreferencesStore.agentSettingsFlow
    val allFeatureSelectionsFlow = simPreferencesStore.allFeatureSelectionsFlow

    suspend fun assignSimToFeature(network: NetworkType, type: TransactionType, sim: ActiveSimInfo) {
        simPreferencesStore.saveFeatureSelection(
            type = type,
            selection = sim.toSavedSelection(network)
        )
    }

    suspend fun clearFeatureAssignment(network: NetworkType, type: TransactionType) {
        simPreferencesStore.clearFeatureSelection(network, type)
    }

    suspend fun assignSimToAllFeatures(network: NetworkType, sim: ActiveSimInfo) {
        simPreferencesStore.saveFeatureSelectionsForNetwork(
            types = SimPreferencesStore.featuresForNetwork(network),
            selection = sim.toSavedSelection(network)
        )
    }

    suspend fun saveAllSimConfiguration(
        networkSelections: Map<NetworkType, ActiveSimInfo?>,
        featureSelections: Map<Pair<NetworkType, TransactionType>, ActiveSimInfo?>
    ) {
        val savedNetworks = networkSelections.mapValues { (network, sim) ->
            sim?.toSavedSelection(network)
        }
        val savedFeatures = featureSelections.mapValues { (key, sim) ->
            sim?.toSavedSelection(key.first)
        }
        simPreferencesStore.saveAllSimConfiguration(savedNetworks, savedFeatures)
    }

    private fun ActiveSimInfo.toSavedSelection(network: NetworkType) = SavedSimSelection(
        network = network,
        subscriptionId = subscriptionId,
        slotIndex = slotIndex,
        carrierName = carrierName,
        displayName = displayName,
        iccIdHash = iccIdHash,
        isEmbedded = isEmbedded
    )

    suspend fun clearAllFeatureAssignments(network: NetworkType) {
        simPreferencesStore.clearFeatureSelectionsForNetwork(
            network = network,
            types = SimPreferencesStore.featuresForNetwork(network)
        )
    }

    suspend fun updateAgentSettings(transform: (AgentSettings) -> AgentSettings) {
        simPreferencesStore.updateAgentSettings(transform)
    }

    suspend fun getTelecelOperationalId(): String {
        return simPreferencesStore.getTelecelOperationalId()
    }

    suspend fun saveTelecelOperationalId(operationalId: String) {
        simPreferencesStore.saveTelecelOperationalId(operationalId)
    }

    suspend fun clearAssignment(network: NetworkType) {
        simPreferencesStore.clearSelection(network)
    }

    private fun matchesSaved(active: ActiveSimInfo, saved: SavedSimSelection): Boolean {
        if (active.subscriptionId == saved.subscriptionId) return true
        return saved.iccIdHash.isNotBlank() && active.iccIdHash == saved.iccIdHash
    }

    private fun inferNetworkFromCarrier(raw: String): NetworkType {
        val norm = raw.lowercase()
        return when {
            norm.contains("mtn") -> NetworkType.MTN
            norm.contains("airtel") || norm.contains("tigo") || norm.contains("at ") || norm == "at" -> NetworkType.AT
            norm.contains("telecel") || norm.contains("vodafone") -> NetworkType.TELECEL
            else -> NetworkType.UNKNOWN
        }
    }

    private fun sha256Short(input: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(input.toByteArray())
        return digest.take(8).joinToString("") { "%02x".format(it) }
    }
}

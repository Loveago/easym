package com.easymomo.app

import android.content.Context
import android.content.ContextWrapper
import com.easymomo.app.data.local.AgentSettings
import com.easymomo.app.data.local.SavedSimSelection
import com.easymomo.app.data.local.SimSelectionStore
import com.easymomo.app.domain.model.NetworkType
import com.easymomo.app.domain.model.TransactionType
import com.easymomo.app.ussd.ActiveSimInfo
import com.easymomo.app.ussd.CapabilityStatus
import com.easymomo.app.ussd.PermissionCapabilityManager
import com.easymomo.app.ussd.PermissionReport
import com.easymomo.app.ussd.SimManager
import com.easymomo.app.ussd.SimResolutionStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.lang.reflect.Proxy

class SimManagerTest {

    private class InMemorySimSelectionStore : SimSelectionStore {
        val networkSelections = mutableMapOf<NetworkType, SavedSimSelection>()
        val featureSelections = mutableMapOf<Pair<NetworkType, TransactionType>, SavedSimSelection>()
        private val featureFlow = MutableStateFlow<Map<Pair<NetworkType, TransactionType>, SavedSimSelection>>(emptyMap())
        private val opIdFlow = MutableStateFlow("")
        private val settingsFlow = MutableStateFlow(AgentSettings())

        override val allFeatureSelectionsFlow: Flow<Map<Pair<NetworkType, TransactionType>, SavedSimSelection>> =
            featureFlow
        override val telecelOperationalIdFlow: Flow<String> = opIdFlow
        override val agentSettingsFlow: Flow<AgentSettings> = settingsFlow

        override suspend fun getSelection(network: NetworkType): SavedSimSelection? =
            networkSelections[network]

        override suspend fun saveSelection(selection: SavedSimSelection) {
            networkSelections[selection.network] = selection
        }

        override suspend fun clearSelection(network: NetworkType) {
            networkSelections.remove(network)
        }

        override suspend fun getFeatureSelection(
            network: NetworkType,
            type: TransactionType
        ): SavedSimSelection? = featureSelections[network to type]

        override suspend fun saveFeatureSelection(type: TransactionType, selection: SavedSimSelection) {
            featureSelections[selection.network to type] = selection
            featureFlow.value = featureSelections.toMap()
        }

        override suspend fun clearFeatureSelection(network: NetworkType, type: TransactionType) {
            featureSelections.remove(network to type)
            featureFlow.value = featureSelections.toMap()
        }

        override suspend fun saveFeatureSelectionsForNetwork(
            types: List<TransactionType>,
            selection: SavedSimSelection
        ) {
            types.forEach { featureSelections[selection.network to it] = selection }
            featureFlow.value = featureSelections.toMap()
        }

        override suspend fun clearFeatureSelectionsForNetwork(
            network: NetworkType,
            types: List<TransactionType>
        ) {
            types.forEach { featureSelections.remove(network to it) }
            featureFlow.value = featureSelections.toMap()
        }

        override suspend fun saveAllSimConfiguration(
            networkSelections: Map<NetworkType, SavedSimSelection?>,
            featureSelections: Map<Pair<NetworkType, TransactionType>, SavedSimSelection?>
        ) {
            networkSelections.forEach { (network, sel) ->
                if (sel == null) this.networkSelections.remove(network) else this.networkSelections[network] = sel
            }
            featureSelections.forEach { (key, sel) ->
                if (sel == null) this.featureSelections.remove(key) else this.featureSelections[key] = sel
            }
            featureFlow.value = this.featureSelections.toMap()
        }

        override suspend fun updateAgentSettings(transform: (AgentSettings) -> AgentSettings) {
            settingsFlow.value = transform(settingsFlow.value)
        }

        override suspend fun getTelecelOperationalId(): String = opIdFlow.value

        override suspend fun saveTelecelOperationalId(operationalId: String) {
            opIdFlow.value = operationalId
        }
    }

    private fun fakeContext(): Context = ContextWrapper(null)

    private fun grantedPerms() = PermissionReport(
        callPhone = CapabilityStatus.GRANTED,
        readPhoneState = CapabilityStatus.GRANTED,
        readPhoneNumbers = CapabilityStatus.GRANTED,
        accessibilityService = CapabilityStatus.GRANTED,
        notifications = CapabilityStatus.GRANTED,
        hasTelephonyHardware = true
    )

    private val mtnSim = ActiveSimInfo(
        subscriptionId = 1,
        slotIndex = 0,
        carrierName = "MTN",
        displayName = "MTN Ghana",
        phoneNumber = "0240000001",
        iccIdHash = "hash_mtn_1",
        isEmbedded = false,
        detectedCarrierNetwork = NetworkType.MTN
    )

    private val telecelSim = ActiveSimInfo(
        subscriptionId = 2,
        slotIndex = 1,
        carrierName = "Telecel",
        displayName = "Telecel Cash",
        phoneNumber = "0200000002",
        iccIdHash = "hash_telecel_2",
        isEmbedded = false,
        detectedCarrierNetwork = NetworkType.TELECEL
    )

    private fun createSimManager(store: SimSelectionStore): SimManager {
        val ctx = fakeContext()
        return SimManager(ctx, store, PermissionCapabilityManager(ctx))
    }

    @Test
    fun `dual sim falls back to assigned network SIM when no per-feature override is set`() = runTest {
        val store = InMemorySimSelectionStore()
        val manager = createSimManager(store)
        val activeSims = listOf(mtnSim, telecelSim)
        val perms = grantedPerms()

        manager.assignSimToNetwork(NetworkType.MTN, mtnSim)
        manager.assignSimToNetwork(NetworkType.TELECEL, telecelSim)

        val mtnData = manager.resolveForNetworkAndFeature(NetworkType.MTN, TransactionType.DATA, activeSims, perms)
        assertEquals(SimResolutionStatus.READY, mtnData.status)
        assertEquals(mtnSim.subscriptionId, mtnData.resolvedSim?.subscriptionId)

        val mtnMerchant = manager.resolveForNetworkAndFeature(NetworkType.MTN, TransactionType.MERCHANT_PAY, activeSims, perms)
        assertEquals(SimResolutionStatus.READY, mtnMerchant.status)
        assertEquals(mtnSim.subscriptionId, mtnMerchant.resolvedSim?.subscriptionId)

        val telecelCashIn = manager.resolveForNetworkAndFeature(NetworkType.TELECEL, TransactionType.CASH_IN, activeSims, perms)
        assertEquals(SimResolutionStatus.READY, telecelCashIn.status)
        assertEquals(telecelSim.subscriptionId, telecelCashIn.resolvedSim?.subscriptionId)

        val telecelAirtime = manager.resolveForNetworkAndFeature(NetworkType.TELECEL, TransactionType.AIRTIME, activeSims, perms)
        assertEquals(SimResolutionStatus.READY, telecelAirtime.status)
        assertEquals(telecelSim.subscriptionId, telecelAirtime.resolvedSim?.subscriptionId)
    }

    @Test
    fun `per-feature override takes precedence over assigned network SIM`() = runTest {
        val store = InMemorySimSelectionStore()
        val manager = createSimManager(store)
        val activeSims = listOf(mtnSim, telecelSim)
        val perms = grantedPerms()

        manager.assignSimToNetwork(NetworkType.MTN, mtnSim)
        manager.assignSimToFeature(NetworkType.MTN, TransactionType.EVD_BUNDLE, telecelSim)

        val evdResolution = manager.resolveForNetworkAndFeature(NetworkType.MTN, TransactionType.EVD_BUNDLE, activeSims, perms)
        assertEquals(SimResolutionStatus.NETWORK_MISMATCH_WARNING, evdResolution.status)
        assertEquals(telecelSim.subscriptionId, evdResolution.resolvedSim?.subscriptionId)

        val cashInResolution = manager.resolveForNetworkAndFeature(NetworkType.MTN, TransactionType.CASH_IN, activeSims, perms)
        assertEquals(SimResolutionStatus.READY, cashInResolution.status)
        assertEquals(mtnSim.subscriptionId, cashInResolution.resolvedSim?.subscriptionId)
    }

    @Test
    fun `dual sim blocks with REQUIRES_SELECTION when neither network nor feature SIM is set`() = runTest {
        val store = InMemorySimSelectionStore()
        val manager = createSimManager(store)
        val activeSims = listOf(mtnSim, telecelSim)
        val perms = grantedPerms()

        val mtnRes = manager.resolveForNetworkAndFeature(NetworkType.MTN, TransactionType.CASH_IN, activeSims, perms)
        assertEquals(SimResolutionStatus.REQUIRES_SELECTION, mtnRes.status)
        assertNull(mtnRes.resolvedSim)

        val telecelRes = manager.resolveForNetworkAndFeature(NetworkType.TELECEL, TransactionType.DATA, activeSims, perms)
        assertEquals(SimResolutionStatus.REQUIRES_SELECTION, telecelRes.status)
        assertNull(telecelRes.resolvedSim)
    }

    @Test
    fun `saveAllSimConfiguration atomically updates and clears network and feature SIMs`() = runTest {
        val store = InMemorySimSelectionStore()
        val manager = createSimManager(store)
        val activeSims = listOf(mtnSim, telecelSim)
        val perms = grantedPerms()

        // Initially set an override for MTN DATA
        manager.saveAllSimConfiguration(
            networkSelections = mapOf(
                NetworkType.MTN to mtnSim,
                NetworkType.TELECEL to telecelSim
            ),
            featureSelections = mapOf(
                (NetworkType.MTN to TransactionType.DATA) to telecelSim
            )
        )

        val overridden = manager.resolveForNetworkAndFeature(NetworkType.MTN, TransactionType.DATA, activeSims, perms)
        assertEquals(telecelSim.subscriptionId, overridden.resolvedSim?.subscriptionId)

        // Now clear the feature override back to null -> should fall back to MTN network SIM
        manager.saveAllSimConfiguration(
            networkSelections = mapOf(
                NetworkType.MTN to mtnSim,
                NetworkType.TELECEL to telecelSim
            ),
            featureSelections = mapOf(
                (NetworkType.MTN to TransactionType.DATA) to null
            )
        )

        val fallback = manager.resolveForNetworkAndFeature(NetworkType.MTN, TransactionType.DATA, activeSims, perms)
        assertEquals(SimResolutionStatus.READY, fallback.status)
        assertEquals(mtnSim.subscriptionId, fallback.resolvedSim?.subscriptionId)
    }

    @Test
    fun `single sim auto-resolves without per-feature selection`() = runTest {
        val store = InMemorySimSelectionStore()
        val manager = createSimManager(store)
        val activeSims = listOf(mtnSim)
        val perms = grantedPerms()

        val res = manager.resolveForNetworkAndFeature(NetworkType.MTN, TransactionType.CASH_IN, activeSims, perms)
        assertEquals(SimResolutionStatus.READY, res.status)
        assertEquals(mtnSim.subscriptionId, res.resolvedSim?.subscriptionId)
    }
}

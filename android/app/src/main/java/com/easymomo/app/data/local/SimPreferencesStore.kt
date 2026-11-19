package com.easymomo.app.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.easymomo.app.domain.model.NetworkType
import com.easymomo.app.domain.model.TransactionType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.simPreferencesDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "easymomo_sim_preferences"
)

data class SavedSimSelection(
    val network: NetworkType,
    val subscriptionId: Int,
    val slotIndex: Int,
    val carrierName: String,
    val displayName: String,
    val iccIdHash: String,
    val isEmbedded: Boolean
)

data class AgentSettings(
    val defaultNetworkMode: String = "ASK", // "ASK", "MTN", "TELECEL", "AT"
    val autoClearFormAfterTx: Boolean = false,
    val saveCustomerNumbersEnabled: Boolean = true,
    val hideTodayTotalsOnHome: Boolean = false,
    val customerCloudSyncEnabled: Boolean = true,
    val darkModeEnabled: Boolean = false,
    val autoEnterTelecelOperationalId: Boolean = false
)

/**
 * Contract for reading and writing SIM preferences, enabling unit testing without DataStore.
 */
interface SimSelectionStore {
    val allFeatureSelectionsFlow: Flow<Map<Pair<NetworkType, TransactionType>, SavedSimSelection>>
    val telecelOperationalIdFlow: Flow<String>
    val agentSettingsFlow: Flow<AgentSettings>

    suspend fun getSelection(network: NetworkType): SavedSimSelection?
    suspend fun saveSelection(selection: SavedSimSelection)
    suspend fun clearSelection(network: NetworkType)
    suspend fun getFeatureSelection(network: NetworkType, type: TransactionType): SavedSimSelection?
    suspend fun saveFeatureSelection(type: TransactionType, selection: SavedSimSelection)
    suspend fun clearFeatureSelection(network: NetworkType, type: TransactionType)
    suspend fun saveFeatureSelectionsForNetwork(types: List<TransactionType>, selection: SavedSimSelection)
    suspend fun clearFeatureSelectionsForNetwork(network: NetworkType, types: List<TransactionType>)
    suspend fun saveAllSimConfiguration(
        networkSelections: Map<NetworkType, SavedSimSelection?>,
        featureSelections: Map<Pair<NetworkType, TransactionType>, SavedSimSelection?>
    )
    suspend fun updateAgentSettings(transform: (AgentSettings) -> AgentSettings)
    suspend fun getTelecelOperationalId(): String
    suspend fun saveTelecelOperationalId(operationalId: String)
}

/**
 * Persists per-network SIM preferences using Jetpack DataStore.
 * Retained across app restarts, logouts, and USSD configuration migrations.
 */
class SimPreferencesStore(private val context: Context) : SimSelectionStore {

    private fun subIdKey(net: NetworkType) = intPreferencesKey("sim_${net.name}_sub_id")
    private fun slotKey(net: NetworkType) = intPreferencesKey("sim_${net.name}_slot")
    private fun carrierKey(net: NetworkType) = stringPreferencesKey("sim_${net.name}_carrier")
    private fun displayKey(net: NetworkType) = stringPreferencesKey("sim_${net.name}_display")
    private fun iccHashKey(net: NetworkType) = stringPreferencesKey("sim_${net.name}_icc_hash")
    private fun esimKey(net: NetworkType) = booleanPreferencesKey("sim_${net.name}_esim")

    val allSelectionsFlow: Flow<Map<NetworkType, SavedSimSelection>> =
        context.simPreferencesDataStore.data.map { prefs ->
            buildMap {
                listOf(NetworkType.MTN, NetworkType.AT, NetworkType.TELECEL).forEach { net ->
                    val subId = prefs[subIdKey(net)]
                    if (subId != null) {
                        put(
                            net,
                            SavedSimSelection(
                                network = net,
                                subscriptionId = subId,
                                slotIndex = prefs[slotKey(net)] ?: -1,
                                carrierName = prefs[carrierKey(net)].orEmpty(),
                                displayName = prefs[displayKey(net)].orEmpty(),
                                iccIdHash = prefs[iccHashKey(net)].orEmpty(),
                                isEmbedded = prefs[esimKey(net)] ?: false
                            )
                        )
                    }
                }
            }
        }

    override suspend fun getSelection(network: NetworkType): SavedSimSelection? {
        return allSelectionsFlow.first()[network]
    }

    override suspend fun saveSelection(selection: SavedSimSelection) {
        context.simPreferencesDataStore.edit { prefs ->
            prefs[subIdKey(selection.network)] = selection.subscriptionId
            prefs[slotKey(selection.network)] = selection.slotIndex
            prefs[carrierKey(selection.network)] = selection.carrierName
            prefs[displayKey(selection.network)] = selection.displayName
            prefs[iccHashKey(selection.network)] = selection.iccIdHash
            prefs[esimKey(selection.network)] = selection.isEmbedded
        }
    }

    private val telecelOperationalIdKey = stringPreferencesKey("telecel_operational_id")
    private val defaultNetworkModeKey = stringPreferencesKey("agent_default_network_mode")
    private val autoClearFormKey = booleanPreferencesKey("agent_auto_clear_form")
    private val saveCustomerNumbersKey = booleanPreferencesKey("agent_save_customer_numbers")
    private val hideTodayTotalsKey = booleanPreferencesKey("agent_hide_today_totals")
    private val customerCloudSyncKey = booleanPreferencesKey("agent_customer_cloud_sync_enabled")
    private val darkModeEnabledKey = booleanPreferencesKey("agent_dark_mode_enabled")
    private val autoEnterTelecelOpIdKey = booleanPreferencesKey("agent_auto_enter_telecel_op_id")
    private val lastCustomerSyncAtKey = longPreferencesKey("customer_sync_last_at")
    private val lastCustomerSyncErrorKey = stringPreferencesKey("customer_sync_last_error")
    private val lastCustomerSyncUserIdKey = stringPreferencesKey("customer_sync_last_user_id")

    override val agentSettingsFlow: Flow<AgentSettings> =
        context.simPreferencesDataStore.data.map { prefs ->
            AgentSettings(
                defaultNetworkMode = prefs[defaultNetworkModeKey] ?: "ASK",
                autoClearFormAfterTx = prefs[autoClearFormKey] ?: false,
                saveCustomerNumbersEnabled = prefs[saveCustomerNumbersKey] ?: true,
                hideTodayTotalsOnHome = prefs[hideTodayTotalsKey] ?: false,
                customerCloudSyncEnabled = prefs[customerCloudSyncKey] ?: true,
                darkModeEnabled = prefs[darkModeEnabledKey] ?: false,
                autoEnterTelecelOperationalId = prefs[autoEnterTelecelOpIdKey] ?: false
            )
        }

    override suspend fun updateAgentSettings(transform: (AgentSettings) -> AgentSettings) {
        context.simPreferencesDataStore.edit { prefs ->
            val current = AgentSettings(
                defaultNetworkMode = prefs[defaultNetworkModeKey] ?: "ASK",
                autoClearFormAfterTx = prefs[autoClearFormKey] ?: false,
                saveCustomerNumbersEnabled = prefs[saveCustomerNumbersKey] ?: true,
                hideTodayTotalsOnHome = prefs[hideTodayTotalsKey] ?: false,
                customerCloudSyncEnabled = prefs[customerCloudSyncKey] ?: true,
                darkModeEnabled = prefs[darkModeEnabledKey] ?: false,
                autoEnterTelecelOperationalId = prefs[autoEnterTelecelOpIdKey] ?: false
            )
            val updated = transform(current)
            prefs[defaultNetworkModeKey] = updated.defaultNetworkMode
            prefs[autoClearFormKey] = updated.autoClearFormAfterTx
            prefs[saveCustomerNumbersKey] = updated.saveCustomerNumbersEnabled
            prefs[hideTodayTotalsKey] = updated.hideTodayTotalsOnHome
            prefs[customerCloudSyncKey] = updated.customerCloudSyncEnabled
            prefs[darkModeEnabledKey] = updated.darkModeEnabled
            prefs[autoEnterTelecelOpIdKey] = updated.autoEnterTelecelOperationalId
        }
    }

    val customerSyncMetaFlow: Flow<Triple<Long, String?, String?>> =
        context.simPreferencesDataStore.data.map { prefs ->
            Triple(
                prefs[lastCustomerSyncAtKey] ?: 0L,
                prefs[lastCustomerSyncErrorKey],
                prefs[lastCustomerSyncUserIdKey]
            )
        }

    suspend fun getLastCustomerSyncMeta(): Triple<Long, String?, String?> =
        customerSyncMetaFlow.first()

    suspend fun saveCustomerSyncMeta(lastSyncAt: Long, error: String?, userId: String?) {
        context.simPreferencesDataStore.edit { prefs ->
            prefs[lastCustomerSyncAtKey] = lastSyncAt
            if (error.isNullOrBlank()) {
                prefs.remove(lastCustomerSyncErrorKey)
            } else {
                prefs[lastCustomerSyncErrorKey] = error
            }
            if (userId.isNullOrBlank()) {
                prefs.remove(lastCustomerSyncUserIdKey)
            } else {
                prefs[lastCustomerSyncUserIdKey] = userId
            }
        }
    }

    override val telecelOperationalIdFlow: Flow<String> =
        context.simPreferencesDataStore.data.map { prefs ->
            prefs[telecelOperationalIdKey].orEmpty()
        }

    override suspend fun getTelecelOperationalId(): String {
        return telecelOperationalIdFlow.first()
    }

    override suspend fun saveTelecelOperationalId(operationalId: String) {
        context.simPreferencesDataStore.edit { prefs ->
            val trimmed = operationalId.trim()
            if (trimmed.isEmpty()) {
                prefs.remove(telecelOperationalIdKey)
            } else {
                prefs[telecelOperationalIdKey] = trimmed
            }
        }
    }

    override suspend fun clearSelection(network: NetworkType) {
        context.simPreferencesDataStore.edit { prefs ->
            prefs.remove(subIdKey(network))
            prefs.remove(slotKey(network))
            prefs.remove(carrierKey(network))
            prefs.remove(displayKey(network))
            prefs.remove(iccHashKey(network))
            prefs.remove(esimKey(network))
        }
    }

    private fun featureSubIdKey(net: NetworkType, type: TransactionType) =
        intPreferencesKey("sim_${net.name}_${type.name}_sub_id")
    private fun featureSlotKey(net: NetworkType, type: TransactionType) =
        intPreferencesKey("sim_${net.name}_${type.name}_slot")
    private fun featureCarrierKey(net: NetworkType, type: TransactionType) =
        stringPreferencesKey("sim_${net.name}_${type.name}_carrier")
    private fun featureDisplayKey(net: NetworkType, type: TransactionType) =
        stringPreferencesKey("sim_${net.name}_${type.name}_display")
    private fun featureIccHashKey(net: NetworkType, type: TransactionType) =
        stringPreferencesKey("sim_${net.name}_${type.name}_icc_hash")
    private fun featureEsimKey(net: NetworkType, type: TransactionType) =
        booleanPreferencesKey("sim_${net.name}_${type.name}_esim")

    override val allFeatureSelectionsFlow: Flow<Map<Pair<NetworkType, TransactionType>, SavedSimSelection>> =
        context.simPreferencesDataStore.data.map { prefs ->
            buildMap {
                SUPPORTED_NETWORKS.forEach { net ->
                    CORE_SIM_FEATURES.forEach { type ->
                        val subId = prefs[featureSubIdKey(net, type)]
                        if (subId != null) {
                            put(
                                net to type,
                                SavedSimSelection(
                                    network = net,
                                    subscriptionId = subId,
                                    slotIndex = prefs[featureSlotKey(net, type)] ?: -1,
                                    carrierName = prefs[featureCarrierKey(net, type)].orEmpty(),
                                    displayName = prefs[featureDisplayKey(net, type)].orEmpty(),
                                    iccIdHash = prefs[featureIccHashKey(net, type)].orEmpty(),
                                    isEmbedded = prefs[featureEsimKey(net, type)] ?: false
                                )
                            )
                        }
                    }
                }
            }
        }

    override suspend fun getFeatureSelection(network: NetworkType, type: TransactionType): SavedSimSelection? {
        return allFeatureSelectionsFlow.first()[network to type]
    }

    override suspend fun saveFeatureSelection(type: TransactionType, selection: SavedSimSelection) {
        context.simPreferencesDataStore.edit { prefs ->
            writeFeatureSelection(prefs, selection.network, type, selection)
        }
    }

    override suspend fun clearFeatureSelection(network: NetworkType, type: TransactionType) {
        context.simPreferencesDataStore.edit { prefs ->
            writeFeatureSelection(prefs, network, type, null)
        }
    }

    override suspend fun saveFeatureSelectionsForNetwork(
        types: List<TransactionType>,
        selection: SavedSimSelection
    ) {
        context.simPreferencesDataStore.edit { prefs ->
            val net = selection.network
            types.forEach { type ->
                writeFeatureSelection(prefs, net, type, selection)
            }
        }
    }

    override suspend fun clearFeatureSelectionsForNetwork(
        network: NetworkType,
        types: List<TransactionType>
    ) {
        context.simPreferencesDataStore.edit { prefs ->
            types.forEach { type ->
                writeFeatureSelection(prefs, network, type, null)
            }
        }
    }

    /**
     * Atomically persists or clears all per-network and per-feature SIM selections
     * in a single DataStore transaction.
     */
    override suspend fun saveAllSimConfiguration(
        networkSelections: Map<NetworkType, SavedSimSelection?>,
        featureSelections: Map<Pair<NetworkType, TransactionType>, SavedSimSelection?>
    ) {
        context.simPreferencesDataStore.edit { prefs ->
            networkSelections.forEach { (network, selection) ->
                writeNetworkSelection(prefs, network, selection)
            }
            featureSelections.forEach { (key, selection) ->
                writeFeatureSelection(prefs, key.first, key.second, selection)
            }
        }
    }

    private fun writeNetworkSelection(
        prefs: MutablePreferences,
        network: NetworkType,
        selection: SavedSimSelection?
    ) {
        if (selection == null) {
            prefs.remove(subIdKey(network))
            prefs.remove(slotKey(network))
            prefs.remove(carrierKey(network))
            prefs.remove(displayKey(network))
            prefs.remove(iccHashKey(network))
            prefs.remove(esimKey(network))
        } else {
            prefs[subIdKey(network)] = selection.subscriptionId
            prefs[slotKey(network)] = selection.slotIndex
            prefs[carrierKey(network)] = selection.carrierName
            prefs[displayKey(network)] = selection.displayName
            prefs[iccHashKey(network)] = selection.iccIdHash
            prefs[esimKey(network)] = selection.isEmbedded
        }
    }

    private fun writeFeatureSelection(
        prefs: MutablePreferences,
        network: NetworkType,
        type: TransactionType,
        selection: SavedSimSelection?
    ) {
        if (selection == null) {
            prefs.remove(featureSubIdKey(network, type))
            prefs.remove(featureSlotKey(network, type))
            prefs.remove(featureCarrierKey(network, type))
            prefs.remove(featureDisplayKey(network, type))
            prefs.remove(featureIccHashKey(network, type))
            prefs.remove(featureEsimKey(network, type))
        } else {
            prefs[featureSubIdKey(network, type)] = selection.subscriptionId
            prefs[featureSlotKey(network, type)] = selection.slotIndex
            prefs[featureCarrierKey(network, type)] = selection.carrierName
            prefs[featureDisplayKey(network, type)] = selection.displayName
            prefs[featureIccHashKey(network, type)] = selection.iccIdHash
            prefs[featureEsimKey(network, type)] = selection.isEmbedded
        }
    }

    companion object {
        val SUPPORTED_NETWORKS = listOf(NetworkType.MTN, NetworkType.AT, NetworkType.TELECEL)
        val CORE_SIM_FEATURES = listOf(
            TransactionType.CASH_IN,
            TransactionType.CASH_OUT,
            TransactionType.DATA,
            TransactionType.AIRTIME,
            TransactionType.EVD_BUNDLE,
            TransactionType.EVD_AIRTIME,
            TransactionType.GIG,
            TransactionType.MERCHANT_PAY,
            TransactionType.BALANCE
        )

        val TELECEL_AT_SIM_FEATURES = listOf(
            TransactionType.CASH_IN,
            TransactionType.DATA,
            TransactionType.AIRTIME,
            TransactionType.GIG,
            TransactionType.BALANCE
        )

        fun featuresForNetwork(network: NetworkType): List<TransactionType> {
            return when (network) {
                NetworkType.TELECEL, NetworkType.AT -> TELECEL_AT_SIM_FEATURES
                else -> CORE_SIM_FEATURES
            }
        }
    }
}

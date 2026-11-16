package com.easymomo.app.data.local

import android.content.Context
import androidx.datastore.core.DataStore
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
    val customerCloudSyncEnabled: Boolean = true
)

/**
 * Persists per-network SIM preferences using Jetpack DataStore.
 * Retained across app restarts, logouts, and USSD configuration migrations.
 */
class SimPreferencesStore(private val context: Context) {

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

    suspend fun getSelection(network: NetworkType): SavedSimSelection? {
        return allSelectionsFlow.first()[network]
    }

    suspend fun saveSelection(selection: SavedSimSelection) {
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
    private val lastCustomerSyncAtKey = longPreferencesKey("customer_sync_last_at")
    private val lastCustomerSyncErrorKey = stringPreferencesKey("customer_sync_last_error")
    private val lastCustomerSyncUserIdKey = stringPreferencesKey("customer_sync_last_user_id")

    val agentSettingsFlow: Flow<AgentSettings> =
        context.simPreferencesDataStore.data.map { prefs ->
            AgentSettings(
                defaultNetworkMode = prefs[defaultNetworkModeKey] ?: "ASK",
                autoClearFormAfterTx = prefs[autoClearFormKey] ?: false,
                saveCustomerNumbersEnabled = prefs[saveCustomerNumbersKey] ?: true,
                hideTodayTotalsOnHome = prefs[hideTodayTotalsKey] ?: false,
                customerCloudSyncEnabled = prefs[customerCloudSyncKey] ?: true
            )
        }

    suspend fun updateAgentSettings(transform: (AgentSettings) -> AgentSettings) {
        context.simPreferencesDataStore.edit { prefs ->
            val current = AgentSettings(
                defaultNetworkMode = prefs[defaultNetworkModeKey] ?: "ASK",
                autoClearFormAfterTx = prefs[autoClearFormKey] ?: false,
                saveCustomerNumbersEnabled = prefs[saveCustomerNumbersKey] ?: true,
                hideTodayTotalsOnHome = prefs[hideTodayTotalsKey] ?: false,
                customerCloudSyncEnabled = prefs[customerCloudSyncKey] ?: true
            )
            val updated = transform(current)
            prefs[defaultNetworkModeKey] = updated.defaultNetworkMode
            prefs[autoClearFormKey] = updated.autoClearFormAfterTx
            prefs[saveCustomerNumbersKey] = updated.saveCustomerNumbersEnabled
            prefs[hideTodayTotalsKey] = updated.hideTodayTotalsOnHome
            prefs[customerCloudSyncKey] = updated.customerCloudSyncEnabled
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

    val telecelOperationalIdFlow: Flow<String> =
        context.simPreferencesDataStore.data.map { prefs ->
            prefs[telecelOperationalIdKey].orEmpty()
        }

    suspend fun getTelecelOperationalId(): String {
        return telecelOperationalIdFlow.first()
    }

    suspend fun saveTelecelOperationalId(operationalId: String) {
        context.simPreferencesDataStore.edit { prefs ->
            val trimmed = operationalId.trim()
            if (trimmed.isEmpty()) {
                prefs.remove(telecelOperationalIdKey)
            } else {
                prefs[telecelOperationalIdKey] = trimmed
            }
        }
    }

    suspend fun clearSelection(network: NetworkType) {
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

    val allFeatureSelectionsFlow: Flow<Map<Pair<NetworkType, TransactionType>, SavedSimSelection>> =
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

    suspend fun getFeatureSelection(network: NetworkType, type: TransactionType): SavedSimSelection? {
        return allFeatureSelectionsFlow.first()[network to type]
    }

    suspend fun saveFeatureSelection(type: TransactionType, selection: SavedSimSelection) {
        context.simPreferencesDataStore.edit { prefs ->
            val net = selection.network
            prefs[featureSubIdKey(net, type)] = selection.subscriptionId
            prefs[featureSlotKey(net, type)] = selection.slotIndex
            prefs[featureCarrierKey(net, type)] = selection.carrierName
            prefs[featureDisplayKey(net, type)] = selection.displayName
            prefs[featureIccHashKey(net, type)] = selection.iccIdHash
            prefs[featureEsimKey(net, type)] = selection.isEmbedded
        }
    }

    suspend fun clearFeatureSelection(network: NetworkType, type: TransactionType) {
        context.simPreferencesDataStore.edit { prefs ->
            prefs.remove(featureSubIdKey(network, type))
            prefs.remove(featureSlotKey(network, type))
            prefs.remove(featureCarrierKey(network, type))
            prefs.remove(featureDisplayKey(network, type))
            prefs.remove(featureIccHashKey(network, type))
            prefs.remove(featureEsimKey(network, type))
        }
    }

    companion object {
        val SUPPORTED_NETWORKS = listOf(NetworkType.MTN, NetworkType.AT, NetworkType.TELECEL)
        val CORE_SIM_FEATURES = listOf(
            TransactionType.CASH_IN,
            TransactionType.CASH_OUT,
            TransactionType.DATA,
            TransactionType.AIRTIME,
            TransactionType.GIG,
            TransactionType.BALANCE
        )
    }
}

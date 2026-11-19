package com.easymomo.app.data.repository

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.easymomo.app.data.local.CustomerNumberDao
import com.easymomo.app.data.local.CustomerNumberEntity
import com.easymomo.app.data.local.SessionStore
import com.easymomo.app.data.local.SimPreferencesStore
import com.easymomo.app.data.remote.EasyMoMoApiService
import com.easymomo.app.data.remote.dto.CustomerCloudDto
import com.easymomo.app.data.remote.dto.CustomerSyncItemDto
import com.easymomo.app.data.remote.dto.CustomerSyncRequest
import com.easymomo.app.data.sync.CustomerSyncWorker
import com.easymomo.app.domain.model.NetworkPrefixDetector
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.math.max
import kotlin.math.min

enum class CustomerCloudSyncState(val label: String) {
    SYNCED("Synced"),
    SYNCING("Syncing"),
    PENDING("Pending"),
    OFFLINE("Offline"),
    SYNC_FAILED("Sync failed")
}

data class CustomerSyncStatusSnapshot(
    val state: CustomerCloudSyncState = CustomerCloudSyncState.SYNCED,
    val autoSyncEnabled: Boolean = true,
    val localCount: Int = 0,
    val syncedCount: Int = 0,
    val pendingCount: Int = 0,
    val lastSuccessAt: Long = 0L,
    val lastError: String? = null
)

sealed interface SyncExecutionOutcome {
    data object Success : SyncExecutionOutcome
    data class RetryableError(val reason: String) : SyncExecutionOutcome
    data class AuthExpired(val reason: String) : SyncExecutionOutcome
    data class Skipped(val reason: String) : SyncExecutionOutcome
}

class CustomerSyncManager(
    private val context: Context,
    private val customerNumberDao: CustomerNumberDao,
    private val api: EasyMoMoApiService,
    private val sessionStore: SessionStore,
    private val simPreferencesStore: SimPreferencesStore
) {
    private val syncMutex = Mutex()
    private val _isSyncing = MutableStateFlow(false)

    val statusFlow: Flow<CustomerSyncStatusSnapshot> = combine(
        customerNumberDao.observeActiveCount(),
        customerNumberDao.observeSyncedCount(),
        customerNumberDao.observePendingCount(),
        simPreferencesStore.agentSettingsFlow,
        simPreferencesStore.customerSyncMetaFlow,
        _isSyncing
    ) { arr ->
        val localCount = arr[0] as Int
        val syncedCount = arr[1] as Int
        val pendingCount = arr[2] as Int
        val settings = arr[3] as com.easymomo.app.data.local.AgentSettings
        @Suppress("UNCHECKED_CAST")
        val meta = arr[4] as Triple<Long, String?, String?>
        val syncing = arr[5] as Boolean

        val (lastSyncAt, lastError, _) = meta
        val state = when {
            syncing -> CustomerCloudSyncState.SYNCING
            !isNetworkAvailable() -> CustomerCloudSyncState.OFFLINE
            !lastError.isNullOrBlank() -> CustomerCloudSyncState.SYNC_FAILED
            pendingCount > 0 -> CustomerCloudSyncState.PENDING
            else -> CustomerCloudSyncState.SYNCED
        }

        CustomerSyncStatusSnapshot(
            state = state,
            autoSyncEnabled = settings.customerCloudSyncEnabled,
            localCount = localCount,
            syncedCount = syncedCount,
            pendingCount = pendingCount,
            lastSuccessAt = lastSyncAt,
            lastError = lastError
        )
    }

    fun scheduleAutoSyncIfEnabled(enabled: Boolean) {
        if (!enabled) return
        val token = sessionStore.authToken
        if (token.isNullOrBlank() || token == DEBUG_GUEST_TOKEN) return
        if (!sessionStore.getSubscription().hasActiveAccess()) return

        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val request = OneTimeWorkRequestBuilder<CustomerSyncWorker>()
            .setConstraints(constraints)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .build()

        runCatching {
            WorkManager.getInstance(context).enqueueUniqueWork(
                UNIQUE_SYNC_WORK_NAME,
                ExistingWorkPolicy.REPLACE,
                request
            )
        }
    }

    suspend fun onUserAuthenticated(userId: String) {
        customerNumberDao.adoptUnownedOrGuestRecords(userId)
        val (_, _, lastUserId) = simPreferencesStore.getLastCustomerSyncMeta()
        if (lastUserId != null && lastUserId != "debug-guest-agent" && lastUserId != userId) {
            customerNumberDao.deleteOtherAccountRecords(userId)
            simPreferencesStore.saveCustomerSyncMeta(lastSyncAt = 0L, error = null, userId = userId)
        }
        val settings = simPreferencesStore.agentSettingsFlow.first()
        if (settings.customerCloudSyncEnabled) {
            syncNow(forceFullRestore = true)
        }
    }

    suspend fun onUserLoggedOut(previousUserId: String?) {
        runCatching {
            WorkManager.getInstance(context).cancelUniqueWork(UNIQUE_SYNC_WORK_NAME)
        }
        simPreferencesStore.saveCustomerSyncMeta(lastSyncAt = 0L, error = null, userId = null)
    }

    suspend fun syncNow(forceFullRestore: Boolean = false): SyncExecutionOutcome = syncMutex.withLock {
        val token = sessionStore.authToken
        val user = sessionStore.getUser()
        val (prevLastSyncAt, _, prevLastUserId) = simPreferencesStore.getLastCustomerSyncMeta()
        if (token.isNullOrBlank() || user == null || token == DEBUG_GUEST_TOKEN) {
            val msg = "Sign in with your EasyMoMo cloud account (not Guest) to sync customer numbers."
            simPreferencesStore.saveCustomerSyncMeta(prevLastSyncAt, msg, user?.id)
            return@withLock SyncExecutionOutcome.Skipped(msg)
        }
        if (!sessionStore.getSubscription().hasActiveAccess()) {
            val msg = "Active subscription or trial required for cloud sync."
            simPreferencesStore.saveCustomerSyncMeta(prevLastSyncAt, msg, user.id)
            return@withLock SyncExecutionOutcome.Skipped(msg)
        }
        if (!isNetworkAvailable()) {
            val msg = "No internet connection available for cloud sync."
            simPreferencesStore.saveCustomerSyncMeta(prevLastSyncAt, msg, user.id)
            return@withLock SyncExecutionOutcome.RetryableError(msg)
        }

        _isSyncing.value = true
        try {
            customerNumberDao.adoptUnownedOrGuestRecords(user.id)
            val (lastSyncAt, _, lastUserId) = simPreferencesStore.getLastCustomerSyncMeta()
            if (lastUserId != null && lastUserId != "debug-guest-agent" && lastUserId != user.id) {
                customerNumberDao.deleteOtherAccountRecords(user.id)
            }

            // 1. Push pending local uploads and deletions in batches of 100
            val rawPending = customerNumberDao.getPendingChanges()
            val validPendingByNorm = linkedMapOf<String, MutableList<CustomerNumberEntity>>()
            val nowMs = System.currentTimeMillis()
            for (entity in rawPending) {
                val candidate = entity.normalizedPhoneNumber.ifBlank { entity.phoneNumber }
                val norm = NetworkPrefixDetector.normalizeGhanaPhone(candidate)
                if (!NetworkPrefixDetector.isValidGhanaPhone(norm)) {
                    // Clean up invalid legacy/non-mobile records so they never poison a sync batch
                    if (entity.deletedAt != null) {
                        customerNumberDao.deleteById(entity.id)
                    } else {
                        customerNumberDao.markSynced(
                            id = entity.id,
                            cloudId = entity.cloudId ?: "local-only",
                            ownerUserId = user.id,
                            version = entity.version,
                            syncedAt = nowMs
                        )
                    }
                } else {
                    validPendingByNorm.getOrPut(norm) { mutableListOf() }.add(entity)
                }
            }

            val dedupedEntries = validPendingByNorm.entries.toList()
            for (batch in dedupedEntries.chunked(BATCH_SIZE)) {
                val payload = CustomerSyncRequest(
                    customers = batch.map { (norm, entities) ->
                        val primary = entities.maxByOrNull { it.updatedAt } ?: entities.first()
                        val displayPhone = primary.phoneNumber.trim().ifBlank { norm }
                        val safeLast = primary.lastUsedAt.takeIf { it > 0L } ?: nowMs
                        val safeFirst = primary.firstUsedAt.takeIf { it in 1L..safeLast } ?: safeLast
                        val safeUpdated = primary.updatedAt.takeIf { it > 0L } ?: safeLast
                        CustomerSyncItemDto(
                            cloudId = primary.cloudId,
                            phoneNumber = displayPhone,
                            normalizedPhoneNumber = norm,
                            nickname = primary.nickname?.trim()?.takeIf { it.isNotEmpty() },
                            firstUsedAt = safeFirst,
                            lastUsedAt = safeLast,
                            usageCount = primary.usageCount.coerceAtLeast(1),
                            updatedAt = safeUpdated,
                            deletedAt = primary.deletedAt
                        )
                    }
                )
                val response = api.syncCustomers(payload)
                if (response.code() == 401) {
                    val msg = "Session expired. Please sign in again."
                    simPreferencesStore.saveCustomerSyncMeta(lastSyncAt, msg, user.id)
                    return@withLock SyncExecutionOutcome.AuthExpired(msg)
                }
                if (response.code() == 402) {
                    val msg = "Active subscription or trial required for cloud sync."
                    simPreferencesStore.saveCustomerSyncMeta(lastSyncAt, msg, user.id)
                    return@withLock SyncExecutionOutcome.Skipped(msg)
                }
                if (!response.isSuccessful) {
                    val msg = "Sync upload failed (${response.code()})"
                    simPreferencesStore.saveCustomerSyncMeta(lastSyncAt, msg, user.id)
                    return@withLock SyncExecutionOutcome.RetryableError(msg)
                }

                val syncedRows = response.body()?.synced.orEmpty()
                val syncedByNorm = syncedRows.associateBy {
                    NetworkPrefixDetector.normalizeGhanaPhone(it.normalizedPhoneNumber)
                }
                val now = System.currentTimeMillis()
                batch.forEach { (norm, localEntities) ->
                    val remote = syncedByNorm[norm]
                    if (remote != null) {
                        localEntities.forEach { local ->
                            customerNumberDao.markSynced(
                                id = local.id,
                                cloudId = remote.id,
                                ownerUserId = user.id,
                                version = remote.version,
                                syncedAt = now
                            )
                        }
                    }
                }
            }
            customerNumberDao.purgeSyncedTombstones()

            // 2. Pull incremental or full paginated customer records from cloud
            val localActiveCount = customerNumberDao.getActiveCount()
            val needsFullPull = forceFullRestore ||
                localActiveCount == 0 ||
                lastUserId != user.id ||
                prevLastUserId != user.id ||
                lastSyncAt <= 0L
            val sinceParam = if (needsFullPull) null else lastSyncAt
            var cursor: String? = null
            var latestServerTime = lastSyncAt

            do {
                val response = api.getCustomers(
                    since = sinceParam,
                    cursor = cursor,
                    limit = BATCH_SIZE,
                    includeDeleted = sinceParam != null
                )
                if (response.code() == 401) {
                    val msg = "Session expired. Please sign in again."
                    simPreferencesStore.saveCustomerSyncMeta(lastSyncAt, msg, user.id)
                    return@withLock SyncExecutionOutcome.AuthExpired(msg)
                }
                if (response.code() == 402) {
                    val msg = "Active subscription or trial required for cloud sync."
                    simPreferencesStore.saveCustomerSyncMeta(lastSyncAt, msg, user.id)
                    return@withLock SyncExecutionOutcome.Skipped(msg)
                }
                if (!response.isSuccessful) {
                    val msg = "Sync download failed (${response.code()})"
                    simPreferencesStore.saveCustomerSyncMeta(lastSyncAt, msg, user.id)
                    return@withLock SyncExecutionOutcome.RetryableError(msg)
                }

                val body = response.body() ?: break
                latestServerTime = max(latestServerTime, body.serverTime)
                for (remote in body.customers) {
                    mergeCloudCustomer(user.id, remote)
                }
                cursor = body.nextCursor
            } while (body.hasMore && !cursor.isNullOrBlank())

            simPreferencesStore.saveCustomerSyncMeta(
                lastSyncAt = latestServerTime.takeIf { it > 0L } ?: System.currentTimeMillis(),
                error = null,
                userId = user.id
            )
            SyncExecutionOutcome.Success
        } catch (_: IOException) {
            val (lastSyncAt, _, _) = simPreferencesStore.getLastCustomerSyncMeta()
            val msg = "Network unavailable during sync"
            simPreferencesStore.saveCustomerSyncMeta(lastSyncAt, msg, user.id)
            SyncExecutionOutcome.RetryableError(msg)
        } catch (e: Exception) {
            val (lastSyncAt, _, _) = simPreferencesStore.getLastCustomerSyncMeta()
            val msg = e.message ?: "Customer sync failed"
            simPreferencesStore.saveCustomerSyncMeta(lastSyncAt, msg, user.id)
            SyncExecutionOutcome.RetryableError(msg)
        } finally {
            _isSyncing.value = false
        }
    }

    internal suspend fun mergeCloudCustomer(userId: String, remote: CustomerCloudDto) {
        val normalized = NetworkPrefixDetector.normalizeGhanaPhone(remote.normalizedPhoneNumber)
        if (!NetworkPrefixDetector.isValidGhanaPhone(normalized)) return

        val existing = customerNumberDao.getByNormalizedPhone(normalized)
        val now = System.currentTimeMillis()

        if (existing == null) {
            if (remote.deletedAt != null) return
            customerNumberDao.upsert(
                CustomerNumberEntity(
                    phoneNumber = remote.phoneNumber.ifBlank { normalized },
                    normalizedPhoneNumber = normalized,
                    nickname = remote.nickname,
                    firstUsedAt = remote.firstUsedAt,
                    lastUsedAt = remote.lastUsedAt,
                    usageCount = max(1, remote.usageCount),
                    cloudId = remote.id,
                    ownerUserId = userId,
                    syncStatus = "SYNCED",
                    version = remote.version,
                    updatedAt = remote.updatedAt,
                    deletedAt = null,
                    lastSyncedAt = now
                )
            )
            return
        }

        // Deletion tombstone wins unless local record was actively used AFTER deletion
        if (remote.deletedAt != null) {
            if (existing.lastUsedAt > remote.deletedAt && existing.syncStatus == "PENDING_UPLOAD") {
                // Keep local active usage and let next push update the cloud
                return
            }
            customerNumberDao.deleteById(existing.id)
            return
        }

        // If local has a pending deletion newer than remote's last use, keep local deletion
        if (existing.syncStatus == "PENDING_DELETE" && existing.deletedAt != null) {
            if (remote.lastUsedAt <= existing.deletedAt) {
                return
            }
        }

        val remoteIsNewer = remote.updatedAt >= existing.updatedAt
        customerNumberDao.upsert(
            existing.copy(
                phoneNumber = if (remoteIsNewer) remote.phoneNumber.ifBlank { normalized } else existing.phoneNumber,
                nickname = if (remoteIsNewer) remote.nickname else existing.nickname,
                firstUsedAt = min(existing.firstUsedAt, remote.firstUsedAt),
                lastUsedAt = max(existing.lastUsedAt, remote.lastUsedAt),
                usageCount = max(existing.usageCount, remote.usageCount),
                cloudId = remote.id,
                ownerUserId = userId,
                syncStatus = if (existing.syncStatus == "PENDING_UPLOAD" && existing.updatedAt > remote.updatedAt) {
                    "PENDING_UPLOAD"
                } else {
                    "SYNCED"
                },
                version = max(existing.version, remote.version),
                updatedAt = max(existing.updatedAt, remote.updatedAt),
                deletedAt = null,
                lastSyncedAt = now
            )
        )
    }

    private fun isNetworkAvailable(): Boolean {
        return runCatching {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
                ?: return true
            val network = cm.activeNetwork ?: return false
            val caps = cm.getNetworkCapabilities(network) ?: return false
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        }.getOrDefault(true)
    }

    companion object {
        const val UNIQUE_SYNC_WORK_NAME = "easymomo_customer_cloud_sync"
        private const val BATCH_SIZE = 100
        private const val DEBUG_GUEST_TOKEN = "debug-guest-token"
    }
}

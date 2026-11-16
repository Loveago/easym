package com.easymomo.app.data.repository

import android.content.Context
import com.easymomo.app.data.local.CustomerNumberDao
import com.easymomo.app.data.local.CustomerNumberEntity
import com.easymomo.app.data.local.DataBundleDao
import com.easymomo.app.data.local.DataBundleEntity
import com.easymomo.app.data.local.SyncQueueDao
import com.easymomo.app.data.local.SyncQueueEntity
import com.easymomo.app.data.local.TransactionDao
import com.easymomo.app.data.local.TransactionEntity
import com.easymomo.app.data.local.TransactionEventEntity
import com.easymomo.app.data.local.UssdFlowConfigDao
import com.easymomo.app.data.local.UssdFlowConfigEntity
import com.easymomo.app.data.remote.EasyMoMoApiService
import com.easymomo.app.data.remote.dto.SyncTransactionItemDto
import com.easymomo.app.data.remote.dto.SyncTransactionsRequest
import com.easymomo.app.domain.TransactionLifecycleManager
import com.easymomo.app.domain.model.NetworkPrefixDetector
import com.easymomo.app.domain.model.NetworkType
import com.easymomo.app.domain.model.ProcessingMethod
import com.easymomo.app.domain.model.TransactionStatus
import com.easymomo.app.domain.model.TransactionType
import com.easymomo.app.ussd.model.UssdFlowProfile
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import kotlinx.coroutines.flow.Flow
import java.util.UUID

class TransactionRepository(
    private val transactionDao: TransactionDao,
    private val ussdFlowConfigDao: UssdFlowConfigDao,
    private val dataBundleDao: DataBundleDao,
    private val syncQueueDao: SyncQueueDao,
    private val api: EasyMoMoApiService,
    private val gson: Gson = Gson(),
    private val customerNumberDao: CustomerNumberDao? = null,
    private val currentUserIdProvider: (() -> String?)? = null,
    private val onCustomerSyncNeeded: (() -> Unit)? = null
) {
    private data class FlowsWrapper(
        @SerializedName("flows") val flows: List<UssdFlowProfile>
    )

    fun observeRecentTransactions(limit: Int = 5): Flow<List<TransactionEntity>> =
        transactionDao.observeRecentTransactions(limit)

    fun observeAllTransactions(): Flow<List<TransactionEntity>> =
        transactionDao.observeAllTransactions()

    fun observeFilteredTransactions(
        network: String?,
        type: String?,
        status: String?,
        query: String?
    ): Flow<List<TransactionEntity>> =
        transactionDao.observeFilteredTransactions(
            network = network?.takeIf { it.isNotBlank() },
            type = type?.takeIf { it.isNotBlank() },
            status = status?.takeIf { it.isNotBlank() },
            query = query?.takeIf { it.isNotBlank() }
        )

    fun observeTransactionsBetween(startMs: Long, endMs: Long): Flow<List<TransactionEntity>> =
        transactionDao.observeTransactionsBetween(startMs, endMs)

    suspend fun getTransactionsBetween(startMs: Long, endMs: Long): List<TransactionEntity> =
        transactionDao.getTransactionsBetween(startMs, endMs)

    fun observeEvents(localId: String): Flow<List<TransactionEventEntity>> =
        transactionDao.observeEventsForTransaction(localId)

    fun observeActiveBundles(network: NetworkType): Flow<List<DataBundleEntity>> =
        dataBundleDao.observeActiveBundles(network.code)

    fun observeAllUssdFlows(): Flow<List<UssdFlowConfigEntity>> =
        ussdFlowConfigDao.observeAllFlows()

    fun observePendingSyncCount(): Flow<Int> =
        syncQueueDao.observePendingCount()

    fun observeCustomerNumbers(): Flow<List<CustomerNumberEntity>> =
        customerNumberDao?.observeAll() ?: kotlinx.coroutines.flow.flowOf(emptyList())

    suspend fun recordCustomerNumberUsage(rawPhone: String): Long? {
        val dao = customerNumberDao ?: return null
        if (!NetworkPrefixDetector.isValidGhanaPhone(rawPhone)) return null
        val normalized = NetworkPrefixDetector.normalizeGhanaPhone(rawPhone)
        val now = System.currentTimeMillis()
        val ownerId = currentUserIdProvider?.invoke()
        val existing = dao.getByNormalizedPhone(normalized)
        val recordId = if (existing != null) {
            dao.incrementUsage(existing.id, normalized, now, ownerId)
            existing.id
        } else {
            dao.insert(
                CustomerNumberEntity(
                    phoneNumber = normalized,
                    normalizedPhoneNumber = normalized,
                    firstUsedAt = now,
                    lastUsedAt = now,
                    usageCount = 1,
                    ownerUserId = ownerId,
                    syncStatus = "PENDING_UPLOAD",
                    updatedAt = now
                )
            )
        }
        onCustomerSyncNeeded?.invoke()
        return recordId
    }

    suspend fun searchCustomerNumberSuggestions(rawQuery: String, limit: Int = 5): List<CustomerNumberEntity> {
        val dao = customerNumberDao ?: return emptyList()
        val digitQuery = NetworkPrefixDetector.normalizeSearchQuery(rawQuery)
        val rawLower = rawQuery.trim().lowercase()
        if (digitQuery.length < 2 && rawLower.length < 2) return emptyList()
        val effectiveDigitQuery = if (digitQuery.length >= 2) digitQuery else rawLower
        return dao.searchSuggestions(effectiveDigitQuery, rawLower, limit)
    }

    suspend fun updateCustomerNickname(id: Long, nickname: String?) {
        customerNumberDao?.updateNickname(id, nickname?.trim()?.takeIf { it.isNotEmpty() }, System.currentTimeMillis())
        onCustomerSyncNeeded?.invoke()
    }

    suspend fun deleteCustomerNumber(id: Long) {
        customerNumberDao?.markDeleted(id, System.currentTimeMillis())
        onCustomerSyncNeeded?.invoke()
    }

    suspend fun clearCustomerNumbers() {
        customerNumberDao?.markAllDeleted(System.currentTimeMillis())
        onCustomerSyncNeeded?.invoke()
    }

    suspend fun createDraftTransaction(
        network: NetworkType,
        type: TransactionType,
        processingMethod: ProcessingMethod,
        recipientNumber: String,
        amount: Double,
        bundleId: String? = null,
        bundleName: String? = null
    ): TransactionEntity {
        val now = System.currentTimeMillis()
        val localId = UUID.randomUUID().toString()
        val customerNumberId = recordCustomerNumberUsage(recipientNumber)
        val entity = TransactionEntity(
            localId = localId,
            network = network.code,
            transactionType = type.code,
            processingMethod = processingMethod.code,
            recipientNumber = recipientNumber,
            amount = amount,
            bundleId = bundleId,
            bundleName = bundleName,
            status = TransactionStatus.DRAFT.code,
            createdAt = now,
            updatedAt = now,
            customerNumberId = customerNumberId
        )
        transactionDao.insertTransaction(entity)
        transactionDao.insertEvent(
            TransactionEventEntity(
                id = UUID.randomUUID().toString(),
                transactionLocalId = localId,
                eventType = "CREATED",
                previousStatus = null,
                newStatus = TransactionStatus.DRAFT.code,
                details = "${network.displayName} ${type.displayName} initiated",
                createdAt = now
            )
        )
        return entity
    }

    suspend fun transitionStatus(
        localId: String,
        newStatus: TransactionStatus,
        providerReference: String? = null,
        confirmationSource: String? = null,
        providerResponse: String? = null,
        details: String? = null,
        isExplicitAgentVerification: Boolean = false
    ): TransactionEntity {
        val existing = transactionDao.getByLocalId(localId)
            ?: throw IllegalArgumentException("Transaction $localId not found")
        val currentStatus = TransactionStatus.fromCode(existing.status)
        if (currentStatus == newStatus) {
            return existing
        }

        TransactionLifecycleManager.requireValidTransition(
            from = currentStatus,
            to = newStatus,
            isExplicitAgentVerification = isExplicitAgentVerification
        )

        val now = System.currentTimeMillis()
        val updated = existing.copy(
            status = newStatus.code,
            providerReference = providerReference ?: existing.providerReference,
            confirmationSource = confirmationSource ?: existing.confirmationSource,
            providerResponse = providerResponse ?: existing.providerResponse,
            updatedAt = now
        )
        transactionDao.updateTransaction(updated)
        transactionDao.insertEvent(
            TransactionEventEntity(
                id = UUID.randomUUID().toString(),
                transactionLocalId = localId,
                eventType = "STATUS_CHANGE",
                previousStatus = currentStatus.code,
                newStatus = newStatus.code,
                details = details ?: providerResponse,
                createdAt = now
            )
        )
        enqueueForSync(updated)
        return updated
    }

    suspend fun transitionStatusSafely(
        localId: String,
        newStatus: TransactionStatus,
        providerReference: String? = null,
        confirmationSource: String? = null,
        providerResponse: String? = null,
        details: String? = null,
        isExplicitAgentVerification: Boolean = false
    ): TransactionEntity? {
        val existing = transactionDao.getByLocalId(localId) ?: return null
        val currentStatus = TransactionStatus.fromCode(existing.status)
        if (currentStatus == newStatus) return existing
        if (!TransactionLifecycleManager.canTransition(currentStatus, newStatus, isExplicitAgentVerification)) {
            return existing
        }
        return runCatching {
            transitionStatus(
                localId = localId,
                newStatus = newStatus,
                providerReference = providerReference,
                confirmationSource = confirmationSource,
                providerResponse = providerResponse,
                details = details,
                isExplicitAgentVerification = isExplicitAgentVerification
            )
        }.getOrDefault(existing)
    }

    private fun toSyncDto(tx: TransactionEntity): SyncTransactionItemDto {
        return SyncTransactionItemDto(
            clientTransactionId = tx.localId,
            providerReference = tx.providerReference,
            network = tx.network,
            transactionType = tx.transactionType,
            processingType = tx.processingMethod,
            recipientNumber = tx.recipientNumber,
            amount = tx.amount,
            bundleId = tx.bundleId,
            bundleName = tx.bundleName,
            status = tx.status,
            confirmationSource = tx.confirmationSource,
            providerResponse = tx.providerResponse,
            createdAt = java.time.Instant.ofEpochMilli(tx.createdAt).toString(),
            updatedAt = java.time.Instant.ofEpochMilli(tx.updatedAt).toString()
        )
    }

    private suspend fun enqueueForSync(tx: TransactionEntity) {
        syncQueueDao.enqueue(
            SyncQueueEntity(
                id = tx.localId,
                entityType = "TRANSACTION",
                entityId = tx.localId,
                payloadJson = gson.toJson(toSyncDto(tx))
            )
        )
    }

    suspend fun syncPendingTransactions(): Result<Int> {
        return runCatching {
            val unsynced = transactionDao.getUnsyncedTransactions()
            if (unsynced.isEmpty()) return@runCatching 0

            val payload = unsynced.map { toSyncDto(it) }
            val response = api.syncTransactions(SyncTransactionsRequest(payload))
            if (!response.isSuccessful) {
                throw IllegalStateException("Sync failed (${response.code()})")
            }
            val now = System.currentTimeMillis()
            val results = response.body()?.results.orEmpty()
            var syncedCount = 0
            for (record in results) {
                if (record.outcome != "REJECTED") {
                    transactionDao.markSynced(record.clientTransactionId, record.serverId, now)
                    syncQueueDao.deleteById(record.clientTransactionId)
                    syncedCount++
                }
            }
            syncedCount
        }
    }

    suspend fun getUssdFlowProfile(flowId: String): UssdFlowProfile? {
        val entity = ussdFlowConfigDao.getFlow(flowId) ?: return null
        return runCatching { gson.fromJson(entity.configJson, UssdFlowProfile::class.java) }.getOrNull()
    }

    suspend fun saveCustomOrVerifiedFlow(profile: UssdFlowProfile) {
        val entity = UssdFlowConfigEntity(
            flowId = profile.flowId,
            network = profile.network,
            transactionType = profile.transactionType,
            version = profile.version,
            configJson = gson.toJson(profile),
            enabled = profile.enabled,
            lastVerifiedAt = if (profile.verifiedWithSim) java.time.Instant.now().toString() else null,
            updatedAt = System.currentTimeMillis()
        )
        ussdFlowConfigDao.upsertFlow(entity)
    }

    suspend fun seedInitialUssdConfigsIfNeeded(context: Context) {
        val json = context.assets.open("ussd_flows.json").bufferedReader().use { it.readText() }
        val wrapper = gson.fromJson(json, FlowsWrapper::class.java)
        val now = System.currentTimeMillis()
        val toUpsert = mutableListOf<UssdFlowConfigEntity>()
        for (profile in wrapper.flows) {
            val existing = ussdFlowConfigDao.getFlow(profile.flowId)
            val shouldUpgrade = existing == null ||
                existing.version < profile.version ||
                (existing.configJson.contains("VERIFY_FROM_AGENT_SIM") && profile.entryCode != "VERIFY_FROM_AGENT_SIM")
            if (shouldUpgrade) {
                toUpsert.add(
                    UssdFlowConfigEntity(
                        flowId = profile.flowId,
                        network = profile.network,
                        transactionType = profile.transactionType,
                        version = profile.version,
                        configJson = gson.toJson(profile),
                        enabled = profile.enabled,
                        lastVerifiedAt = if (profile.verifiedWithSim) java.time.Instant.now().toString() else null,
                        updatedAt = now
                    )
                )
            }
        }
        if (toUpsert.isNotEmpty()) {
            ussdFlowConfigDao.upsertFlows(toUpsert)
        }
    }

    suspend fun refreshRemoteConfigs() {
        runCatching {
            val flowsResp = api.getUssdFlows()
            if (flowsResp.isSuccessful) {
                val now = System.currentTimeMillis()
                val entities = flowsResp.body()?.flows.orEmpty().map { dto ->
                    val flowId = dto.flowId ?: "${dto.network}_${dto.transactionType}"
                    UssdFlowConfigEntity(
                        flowId = flowId,
                        network = dto.network,
                        transactionType = dto.transactionType,
                        version = dto.version,
                        configJson = dto.configuration.toString(),
                        enabled = dto.enabled,
                        lastVerifiedAt = dto.lastVerifiedAt,
                        updatedAt = now
                    )
                }
                if (entities.isNotEmpty()) ussdFlowConfigDao.upsertFlows(entities)
            }
        }
        runCatching {
            val bundlesResp = api.getDataBundles()
            if (bundlesResp.isSuccessful) {
                val entities = bundlesResp.body()?.bundles.orEmpty().map { dto ->
                    DataBundleEntity(
                        bundleId = dto.id,
                        network = dto.network,
                        displayName = dto.name,
                        allowance = dto.allowance,
                        price = dto.price,
                        validity = dto.validity,
                        providerMenuOption = dto.providerOption,
                        active = dto.active,
                        lastVerifiedAt = dto.lastVerifiedAt
                    )
                }
                if (entities.isNotEmpty()) dataBundleDao.upsertBundles(entities)
            }
        }
    }
}

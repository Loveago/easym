package com.easymomo.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface UssdFlowConfigDao {
    @Query("SELECT * FROM ussd_flow_configs WHERE flowId = :flowId LIMIT 1")
    suspend fun getFlow(flowId: String): UssdFlowConfigEntity?

    @Query("SELECT * FROM ussd_flow_configs WHERE network = :network")
    suspend fun getFlowsByNetwork(network: String): List<UssdFlowConfigEntity>

    @Query("SELECT * FROM ussd_flow_configs ORDER BY network, transactionType")
    fun observeAllFlows(): Flow<List<UssdFlowConfigEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertFlows(flows: List<UssdFlowConfigEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertFlow(flow: UssdFlowConfigEntity)

    @Query("SELECT COUNT(*) FROM ussd_flow_configs")
    suspend fun count(): Int
}

@Dao
interface DataBundleDao {
    @Query("SELECT * FROM data_bundles WHERE network = :network AND active = 1 ORDER BY price ASC")
    fun observeActiveBundles(network: String): Flow<List<DataBundleEntity>>

    @Query("SELECT * FROM data_bundles WHERE network = :network ORDER BY price ASC")
    fun observeAllBundlesForNetwork(network: String): Flow<List<DataBundleEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertBundles(bundles: List<DataBundleEntity>)

    @Query("SELECT COUNT(*) FROM data_bundles")
    suspend fun count(): Int
}

@Dao
interface SyncQueueDao {
    @Query("SELECT * FROM sync_queue WHERE status = 'PENDING' ORDER BY id ASC")
    suspend fun getPendingItems(): List<SyncQueueEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun enqueue(item: SyncQueueEntity)

    @Query("UPDATE sync_queue SET retryCount = retryCount + 1, lastAttemptAt = :attemptAt WHERE id = :id")
    suspend fun markAttempted(id: String, attemptAt: Long)

    @Query("DELETE FROM sync_queue WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("SELECT COUNT(*) FROM sync_queue WHERE status = 'PENDING'")
    fun observePendingCount(): Flow<Int>
}

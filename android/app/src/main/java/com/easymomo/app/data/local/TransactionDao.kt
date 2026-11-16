package com.easymomo.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface TransactionDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTransaction(transaction: TransactionEntity)

    @Update
    suspend fun updateTransaction(transaction: TransactionEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEvent(event: TransactionEventEntity)

    @Query("SELECT * FROM transactions WHERE localId = :localId LIMIT 1")
    suspend fun getByLocalId(localId: String): TransactionEntity?

    @Query("SELECT * FROM transactions ORDER BY createdAt DESC")
    fun observeAllTransactions(): Flow<List<TransactionEntity>>

    @Query("SELECT * FROM transactions ORDER BY createdAt DESC LIMIT :limit")
    fun observeRecentTransactions(limit: Int = 5): Flow<List<TransactionEntity>>

    @Query(
        """
        SELECT * FROM transactions
        WHERE (:network IS NULL OR network = :network)
          AND (:type IS NULL OR transactionType = :type)
          AND (:status IS NULL OR status = :status)
          AND (:query IS NULL OR recipientNumber LIKE '%' || :query || '%' OR providerReference LIKE '%' || :query || '%' OR localId LIKE '%' || :query || '%')
        ORDER BY createdAt DESC
        """
    )
    fun observeFilteredTransactions(
        network: String?,
        type: String?,
        status: String?,
        query: String?
    ): Flow<List<TransactionEntity>>

    @Query("SELECT * FROM transactions WHERE createdAt BETWEEN :startMs AND :endMs ORDER BY createdAt DESC")
    fun observeTransactionsBetween(startMs: Long, endMs: Long): Flow<List<TransactionEntity>>

    @Query("SELECT * FROM transactions WHERE createdAt BETWEEN :startMs AND :endMs ORDER BY createdAt DESC")
    suspend fun getTransactionsBetween(startMs: Long, endMs: Long): List<TransactionEntity>

    @Query("SELECT * FROM transaction_events WHERE transactionLocalId = :localId ORDER BY createdAt ASC")
    fun observeEventsForTransaction(localId: String): Flow<List<TransactionEventEntity>>

    @Query("SELECT * FROM transaction_events WHERE transactionLocalId = :localId ORDER BY createdAt ASC")
    suspend fun getEventsForTransaction(localId: String): List<TransactionEventEntity>

    @Query("SELECT * FROM transactions WHERE syncedAt IS NULL OR updatedAt > syncedAt")
    suspend fun getUnsyncedTransactions(): List<TransactionEntity>

    @Query("UPDATE transactions SET syncedAt = :syncedAt, serverId = :serverId WHERE localId = :localId")
    suspend fun markSynced(localId: String, serverId: String?, syncedAt: Long)
}

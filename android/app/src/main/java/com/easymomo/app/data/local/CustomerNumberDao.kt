package com.easymomo.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface CustomerNumberDao {

    @Query("SELECT * FROM customer_numbers WHERE normalizedPhoneNumber = :normalized LIMIT 1")
    suspend fun getByNormalizedPhone(normalized: String): CustomerNumberEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(entity: CustomerNumberEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: CustomerNumberEntity): Long

    @Query(
        """
        UPDATE customer_numbers
        SET phoneNumber = :displayPhone,
            lastUsedAt = :lastUsedAt,
            usageCount = usageCount + 1,
            updatedAt = :lastUsedAt,
            deletedAt = NULL,
            syncStatus = 'PENDING_UPLOAD',
            ownerUserId = COALESCE(:ownerUserId, ownerUserId)
        WHERE id = :id
        """
    )
    suspend fun incrementUsage(
        id: Long,
        displayPhone: String,
        lastUsedAt: Long,
        ownerUserId: String? = null
    )

    @Query(
        """
        SELECT * FROM customer_numbers
        WHERE deletedAt IS NULL
          AND (
              normalizedPhoneNumber LIKE '%' || :digitQuery || '%'
              OR phoneNumber LIKE '%' || :digitQuery || '%'
              OR (:rawLowerQuery != '' AND LOWER(COALESCE(nickname, '')) LIKE '%' || :rawLowerQuery || '%')
          )
        ORDER BY
            CASE WHEN normalizedPhoneNumber LIKE :digitQuery || '%' THEN 0 ELSE 1 END ASC,
            lastUsedAt DESC,
            usageCount DESC
        LIMIT :limit
        """
    )
    suspend fun searchSuggestions(
        digitQuery: String,
        rawLowerQuery: String,
        limit: Int = 5
    ): List<CustomerNumberEntity>

    @Query("SELECT * FROM customer_numbers WHERE deletedAt IS NULL ORDER BY lastUsedAt DESC, usageCount DESC")
    fun observeAll(): Flow<List<CustomerNumberEntity>>

    @Query(
        """
        UPDATE customer_numbers
        SET nickname = :nickname,
            updatedAt = :updatedAt,
            syncStatus = 'PENDING_UPLOAD'
        WHERE id = :id
        """
    )
    suspend fun updateNickname(id: Long, nickname: String?, updatedAt: Long = System.currentTimeMillis())

    @Query(
        """
        UPDATE customer_numbers
        SET deletedAt = :deletedAt,
            updatedAt = :deletedAt,
            syncStatus = 'PENDING_DELETE'
        WHERE id = :id
        """
    )
    suspend fun markDeleted(id: Long, deletedAt: Long = System.currentTimeMillis())

    @Query(
        """
        UPDATE customer_numbers
        SET deletedAt = :deletedAt,
            updatedAt = :deletedAt,
            syncStatus = 'PENDING_DELETE'
        WHERE deletedAt IS NULL
        """
    )
    suspend fun markAllDeleted(deletedAt: Long = System.currentTimeMillis())

    @Query("DELETE FROM customer_numbers WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM customer_numbers")
    suspend fun deleteAll()

    @Query("SELECT * FROM customer_numbers WHERE syncStatus != 'SYNCED' ORDER BY updatedAt ASC")
    suspend fun getPendingChanges(): List<CustomerNumberEntity>

    @Query(
        """
        UPDATE customer_numbers
        SET cloudId = :cloudId,
            ownerUserId = :ownerUserId,
            version = :version,
            syncStatus = 'SYNCED',
            lastSyncedAt = :syncedAt
        WHERE id = :id
        """
    )
    suspend fun markSynced(
        id: Long,
        cloudId: String,
        ownerUserId: String,
        version: Int,
        syncedAt: Long
    )

    @Query("DELETE FROM customer_numbers WHERE syncStatus = 'SYNCED' AND deletedAt IS NOT NULL")
    suspend fun purgeSyncedTombstones()

    @Query("DELETE FROM customer_numbers WHERE ownerUserId = :ownerUserId")
    suspend fun deleteAllForOwner(ownerUserId: String)

    @Query("DELETE FROM customer_numbers WHERE ownerUserId IS NOT NULL AND ownerUserId != :currentUserId")
    suspend fun deleteOtherAccountRecords(currentUserId: String)

    @Query("SELECT COUNT(*) FROM customer_numbers WHERE deletedAt IS NULL")
    fun observeActiveCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM customer_numbers WHERE deletedAt IS NULL AND syncStatus = 'SYNCED'")
    fun observeSyncedCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM customer_numbers WHERE syncStatus != 'SYNCED'")
    fun observePendingCount(): Flow<Int>
}

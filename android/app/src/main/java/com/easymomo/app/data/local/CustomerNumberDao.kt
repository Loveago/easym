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

    @Query(
        """
        UPDATE customer_numbers
        SET phoneNumber = :displayPhone,
            lastUsedAt = :lastUsedAt,
            usageCount = usageCount + 1
        WHERE id = :id
        """
    )
    suspend fun incrementUsage(id: Long, displayPhone: String, lastUsedAt: Long)

    @Query(
        """
        SELECT * FROM customer_numbers
        WHERE normalizedPhoneNumber LIKE '%' || :digitQuery || '%'
           OR phoneNumber LIKE '%' || :digitQuery || '%'
           OR (:rawLowerQuery != '' AND LOWER(COALESCE(nickname, '')) LIKE '%' || :rawLowerQuery || '%')
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

    @Query("SELECT * FROM customer_numbers ORDER BY lastUsedAt DESC, usageCount DESC")
    fun observeAll(): Flow<List<CustomerNumberEntity>>

    @Query("UPDATE customer_numbers SET nickname = :nickname WHERE id = :id")
    suspend fun updateNickname(id: Long, nickname: String?)

    @Query("DELETE FROM customer_numbers WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM customer_numbers")
    suspend fun deleteAll()
}

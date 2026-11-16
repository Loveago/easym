package com.easymomo.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface CustomUssdDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCaptureSession(session: UssdCaptureSessionEntity)

    @Update
    suspend fun updateCaptureSession(session: UssdCaptureSessionEntity)

    @Query("SELECT * FROM ussd_capture_sessions WHERE id = :sessionId LIMIT 1")
    suspend fun getCaptureSessionById(sessionId: String): UssdCaptureSessionEntity?

    @Query("SELECT * FROM ussd_capture_sessions ORDER BY startedAt DESC")
    fun observeCaptureSessions(): Flow<List<UssdCaptureSessionEntity>>

    @Query("SELECT * FROM ussd_capture_sessions WHERE status IN ('RECORDING', 'PAUSED') ORDER BY startedAt DESC")
    suspend fun getActiveOrLingeringSessions(): List<UssdCaptureSessionEntity>

    @Query("UPDATE ussd_capture_sessions SET status = :status, finishedAt = :finishedAt WHERE id = :sessionId")
    suspend fun updateSessionStatus(sessionId: String, status: String, finishedAt: Long?)

    @Query("UPDATE ussd_capture_sessions SET flowName = :flowName WHERE id = :sessionId")
    suspend fun renameCaptureSession(sessionId: String, flowName: String)

    @Query("DELETE FROM ussd_capture_sessions WHERE id = :sessionId")
    suspend fun deleteCaptureSession(sessionId: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCaptureEvent(event: UssdCaptureEventEntity)

    @Query("SELECT * FROM ussd_capture_events WHERE sessionId = :sessionId ORDER BY sequenceNumber ASC")
    suspend fun getCaptureEventsForSession(sessionId: String): List<UssdCaptureEventEntity>

    @Query("SELECT * FROM ussd_capture_events WHERE sessionId = :sessionId ORDER BY sequenceNumber ASC")
    fun observeCaptureEventsForSession(sessionId: String): Flow<List<UssdCaptureEventEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCustomFlow(flow: CustomUssdFlowEntity)

    @Update
    suspend fun updateCustomFlow(flow: CustomUssdFlowEntity)

    @Query("SELECT * FROM custom_ussd_flows ORDER BY updatedAt DESC")
    fun observeAllCustomFlows(): Flow<List<CustomUssdFlowEntity>>

    @Query("SELECT * FROM custom_ussd_flows WHERE id = :flowId LIMIT 1")
    suspend fun getCustomFlowById(flowId: String): CustomUssdFlowEntity?

    @Query("DELETE FROM custom_ussd_flows WHERE id = :flowId")
    suspend fun deleteCustomFlow(flowId: String)
}

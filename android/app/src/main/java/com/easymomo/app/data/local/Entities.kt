package com.easymomo.app.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "transactions",
    indices = [
        Index(value = ["network"]),
        Index(value = ["transactionType"]),
        Index(value = ["status"]),
        Index(value = ["createdAt"])
    ]
)
data class TransactionEntity(
    @PrimaryKey
    val localId: String,
    val serverId: String? = null,
    val providerReference: String? = null,
    val network: String,
    val transactionType: String,
    val processingMethod: String,
    val recipientNumber: String,
    val amount: Double,
    val bundleId: String? = null,
    val bundleName: String? = null,
    val status: String,
    val confirmationSource: String? = null,
    val providerResponse: String? = null,
    val commissionAmount: Double? = null,
    val createdAt: Long,
    val updatedAt: Long,
    val syncedAt: Long? = null,
    val customerNumberId: Long? = null
)

@Entity(
    tableName = "transaction_events",
    foreignKeys = [
        ForeignKey(
            entity = TransactionEntity::class,
            parentColumns = ["localId"],
            childColumns = ["transactionLocalId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["transactionLocalId"])]
)
data class TransactionEventEntity(
    @PrimaryKey
    val id: String,
    val transactionLocalId: String,
    val eventType: String,
    val previousStatus: String?,
    val newStatus: String,
    val details: String?,
    val createdAt: Long
)

@Entity(tableName = "ussd_flow_configs")
data class UssdFlowConfigEntity(
    @PrimaryKey
    val flowId: String,
    val network: String,
    val transactionType: String,
    val version: Int,
    val configJson: String,
    val enabled: Boolean,
    val lastVerifiedAt: String?,
    val updatedAt: Long
)

@Entity(
    tableName = "data_bundles",
    indices = [Index(value = ["network"])]
)
data class DataBundleEntity(
    @PrimaryKey
    val bundleId: String,
    val network: String,
    val displayName: String,
    val allowance: String,
    val price: Double,
    val validity: String,
    val providerMenuOption: String,
    val active: Boolean,
    val lastVerifiedAt: String?
)

@Entity(
    tableName = "customer_numbers",
    indices = [
        Index(value = ["normalizedPhoneNumber"], unique = true),
        Index(value = ["phoneNumber"]),
        Index(value = ["lastUsedAt"]),
        Index(value = ["usageCount"]),
        Index(value = ["syncStatus"]),
        Index(value = ["ownerUserId"])
    ]
)
data class CustomerNumberEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val phoneNumber: String,
    val normalizedPhoneNumber: String,
    val nickname: String? = null,
    val firstUsedAt: Long,
    val lastUsedAt: Long,
    val usageCount: Int = 1,
    val cloudId: String? = null,
    val ownerUserId: String? = null,
    val syncStatus: String = "PENDING_UPLOAD",
    val version: Int = 1,
    val updatedAt: Long = lastUsedAt,
    val deletedAt: Long? = null,
    val lastSyncedAt: Long? = null
)

@Entity(
    tableName = "ussd_capture_sessions",
    indices = [
        Index(value = ["status"]),
        Index(value = ["startedAt"])
    ]
)
data class UssdCaptureSessionEntity(
    @PrimaryKey
    val id: String,
    val flowName: String,
    val network: String,
    val entryCode: String,
    val subscriptionId: Int? = null,
    val startedAt: Long,
    val finishedAt: Long? = null,
    val status: String,
    val capturedEventCount: Int = 0,
    val captureVersion: Int = 1
)

@Entity(
    tableName = "ussd_capture_events",
    foreignKeys = [
        ForeignKey(
            entity = UssdCaptureSessionEntity::class,
            parentColumns = ["id"],
            childColumns = ["sessionId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["sessionId"]),
        Index(value = ["sequenceNumber"])
    ]
)
data class UssdCaptureEventEntity(
    @PrimaryKey
    val id: String,
    val sessionId: String,
    val sequenceNumber: Int,
    val eventType: String,
    val promptText: String,
    val visibleOptionsJson: String = "[]",
    val selectedAction: String? = null,
    val inputControlType: String? = null,
    val observedNodeClass: String? = null,
    val nodeMetadataSummary: String? = null,
    val observedTimestamp: Long,
    val confidence: String,
    val isSensitive: Boolean = false,
    val isComplete: Boolean = true
)

@Entity(
    tableName = "custom_ussd_flows",
    indices = [
        Index(value = ["network"]),
        Index(value = ["status"]),
        Index(value = ["updatedAt"])
    ]
)
data class CustomUssdFlowEntity(
    @PrimaryKey
    val id: String,
    val flowName: String,
    val network: String,
    val entryCode: String,
    val serviceDescription: String = "",
    val transactionCategory: String = "CUSTOM",
    val status: String = "DRAFT",
    val enabled: Boolean = false,
    val sourceCaptureSessionId: String? = null,
    val stepsJson: String = "[]",
    val stepCount: Int = 0,
    val flowVersion: Int = 1,
    val createdAt: Long,
    val updatedAt: Long
)

@Entity(tableName = "sync_queue")
data class SyncQueueEntity(
    @PrimaryKey
    val id: String,
    val entityType: String,
    val entityId: String,
    val payloadJson: String,
    val retryCount: Int = 0,
    val lastAttemptAt: Long? = null,
    val status: String = "PENDING"
)

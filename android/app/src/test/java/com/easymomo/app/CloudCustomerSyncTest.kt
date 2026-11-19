package com.easymomo.app

import com.easymomo.app.data.local.CustomerNumberDao
import com.easymomo.app.data.local.CustomerNumberEntity
import com.easymomo.app.data.local.DataBundleDao
import com.easymomo.app.data.local.EasyMoMoDatabase
import com.easymomo.app.data.local.SyncQueueDao
import com.easymomo.app.data.local.TransactionDao
import com.easymomo.app.data.local.TransactionEntity
import com.easymomo.app.data.local.UssdFlowConfigDao
import com.easymomo.app.data.remote.EasyMoMoApiService
import com.easymomo.app.data.repository.TransactionRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.lang.reflect.Proxy

class CloudCustomerSyncTest {

    private class InMemoryCustomerDao : CustomerNumberDao {
        val items = mutableListOf<CustomerNumberEntity>()
        private val flow = MutableStateFlow<List<CustomerNumberEntity>>(emptyList())
        private var nextId = 1L

        override suspend fun getByNormalizedPhone(normalized: String): CustomerNumberEntity? =
            items.firstOrNull { it.normalizedPhoneNumber == normalized }

        override suspend fun insert(entity: CustomerNumberEntity): Long {
            val saved = entity.copy(id = nextId++)
            items.add(saved)
            emit()
            return saved.id
        }

        override suspend fun upsert(entity: CustomerNumberEntity): Long {
            val idx = items.indexOfFirst { it.normalizedPhoneNumber == entity.normalizedPhoneNumber }
            return if (idx >= 0) {
                items[idx] = entity.copy(id = items[idx].id)
                emit()
                items[idx].id
            } else {
                insert(entity)
            }
        }

        override suspend fun incrementUsage(id: Long, displayPhone: String, lastUsedAt: Long, ownerUserId: String?) {
            val idx = items.indexOfFirst { it.id == id }
            if (idx >= 0) {
                val cur = items[idx]
                items[idx] = cur.copy(
                    phoneNumber = displayPhone,
                    lastUsedAt = lastUsedAt,
                    usageCount = cur.usageCount + 1,
                    updatedAt = lastUsedAt,
                    deletedAt = null,
                    syncStatus = "PENDING_UPLOAD",
                    ownerUserId = ownerUserId ?: cur.ownerUserId
                )
                emit()
            }
        }

        override suspend fun searchSuggestions(digitQuery: String, rawLowerQuery: String, limit: Int): List<CustomerNumberEntity> =
            items.filter { it.deletedAt == null && it.normalizedPhoneNumber.contains(digitQuery) }.take(limit)

        override fun observeAll(): Flow<List<CustomerNumberEntity>> = flow

        override suspend fun updateNickname(id: Long, nickname: String?, updatedAt: Long) {
            val idx = items.indexOfFirst { it.id == id }
            if (idx >= 0) {
                items[idx] = items[idx].copy(nickname = nickname, updatedAt = updatedAt, syncStatus = "PENDING_UPLOAD")
                emit()
            }
        }

        override suspend fun markDeleted(id: Long, deletedAt: Long) {
            val idx = items.indexOfFirst { it.id == id }
            if (idx >= 0) {
                items[idx] = items[idx].copy(deletedAt = deletedAt, updatedAt = deletedAt, syncStatus = "PENDING_DELETE")
                emit()
            }
        }

        override suspend fun markAllDeleted(deletedAt: Long) {
            for (i in items.indices) {
                if (items[i].deletedAt == null) {
                    items[i] = items[i].copy(deletedAt = deletedAt, updatedAt = deletedAt, syncStatus = "PENDING_DELETE")
                }
            }
            emit()
        }

        override suspend fun deleteById(id: Long) { items.removeAll { it.id == id }; emit() }
        override suspend fun deleteAll() { items.clear(); emit() }
        override suspend fun getPendingChanges(): List<CustomerNumberEntity> = items.filter { it.syncStatus != "SYNCED" }
        override suspend fun markSynced(id: Long, cloudId: String, ownerUserId: String, version: Int, syncedAt: Long) {
            val idx = items.indexOfFirst { it.id == id }
            if (idx >= 0) {
                items[idx] = items[idx].copy(cloudId = cloudId, ownerUserId = ownerUserId, version = version, syncStatus = "SYNCED", lastSyncedAt = syncedAt)
                emit()
            }
        }
        override suspend fun purgeSyncedTombstones() { items.removeAll { it.syncStatus == "SYNCED" && it.deletedAt != null }; emit() }
        override suspend fun deleteAllForOwner(ownerUserId: String) { items.removeAll { it.ownerUserId == ownerUserId }; emit() }
        override suspend fun deleteOtherAccountRecords(currentUserId: String) {
            items.removeAll {
                it.ownerUserId != null &&
                    it.ownerUserId != "debug-guest-agent" &&
                    it.ownerUserId != currentUserId
            }
            emit()
        }
        override suspend fun adoptUnownedOrGuestRecords(ownerUserId: String) {
            for (i in items.indices) {
                val cur = items[i]
                if (cur.ownerUserId == null || cur.ownerUserId == "debug-guest-agent") {
                    items[i] = cur.copy(
                        ownerUserId = ownerUserId,
                        syncStatus = if (cur.deletedAt != null) "PENDING_DELETE" else "PENDING_UPLOAD"
                    )
                }
            }
            emit()
        }
        override suspend fun getActiveCount(): Int = items.count { it.deletedAt == null }
        override fun observeActiveCount(): Flow<Int> = MutableStateFlow(items.count { it.deletedAt == null })
        override fun observeSyncedCount(): Flow<Int> = MutableStateFlow(items.count { it.deletedAt == null && it.syncStatus == "SYNCED" })
        override fun observePendingCount(): Flow<Int> = MutableStateFlow(items.count { it.syncStatus != "SYNCED" })

        private fun emit() {
            flow.value = items.filter { it.deletedAt == null }
        }
    }

    private fun createRepo(dao: CustomerNumberDao, onSyncNeeded: () -> Unit): TransactionRepository {
        val txDao = Proxy.newProxyInstance(TransactionDao::class.java.classLoader, arrayOf(TransactionDao::class.java)) { _, m, _ ->
            if (m.name.startsWith("observe")) MutableStateFlow(emptyList<TransactionEntity>()) else null
        } as TransactionDao
        val flowDao = Proxy.newProxyInstance(UssdFlowConfigDao::class.java.classLoader, arrayOf(UssdFlowConfigDao::class.java)) { _, _, _ -> null } as UssdFlowConfigDao
        val bundleDao = Proxy.newProxyInstance(DataBundleDao::class.java.classLoader, arrayOf(DataBundleDao::class.java)) { _, _, _ -> null } as DataBundleDao
        val syncDao = Proxy.newProxyInstance(SyncQueueDao::class.java.classLoader, arrayOf(SyncQueueDao::class.java)) { _, _, _ -> null } as SyncQueueDao
        val api = Proxy.newProxyInstance(EasyMoMoApiService::class.java.classLoader, arrayOf(EasyMoMoApiService::class.java)) { _, _, _ -> null } as EasyMoMoApiService

        return TransactionRepository(
            transactionDao = txDao,
            ussdFlowConfigDao = flowDao,
            dataBundleDao = bundleDao,
            syncQueueDao = syncDao,
            api = api,
            customerNumberDao = dao,
            currentUserIdProvider = { "agent-1" },
            onCustomerSyncNeeded = onSyncNeeded
        )
    }

    @Test
    fun `saving customer offline marks PENDING_UPLOAD and queues sync without blocking`() = runTest {
        val dao = InMemoryCustomerDao()
        var syncQueuedCount = 0
        val repo = createRepo(dao) { syncQueuedCount++ }

        val id = repo.recordCustomerNumberUsage("+233241234567")
        assertNotNull(id)
        assertEquals(1, syncQueuedCount)

        val pending = dao.getPendingChanges()
        assertEquals(1, pending.size)
        assertEquals("0241234567", pending[0].normalizedPhoneNumber)
        assertEquals("PENDING_UPLOAD", pending[0].syncStatus)
        assertEquals("agent-1", pending[0].ownerUserId)
    }

    @Test
    fun `deleting customer creates PENDING_DELETE tombstone and hides from active list`() = runTest {
        val dao = InMemoryCustomerDao()
        var syncQueuedCount = 0
        val repo = createRepo(dao) { syncQueuedCount++ }

        val id = repo.recordCustomerNumberUsage("0551234567")!!
        repo.deleteCustomerNumber(id)

        assertTrue(repo.observeCustomerNumbers().first().isEmpty())
        val pending = dao.getPendingChanges()
        assertEquals(1, pending.size)
        assertEquals("PENDING_DELETE", pending[0].syncStatus)
        assertNotNull(pending[0].deletedAt)
        assertEquals(2, syncQueuedCount)
    }

    @Test
    fun `database migration 3 to 4 has expected version bounds`() {
        assertEquals(3, EasyMoMoDatabase.MIGRATION_3_4.startVersion)
        assertEquals(4, EasyMoMoDatabase.MIGRATION_3_4.endVersion)
    }

    @Test
    fun `adopts guest and unowned customer numbers when user signs into cloud account`() = runTest {
        val dao = InMemoryCustomerDao()
        val now = 1_000L
        dao.insert(
            CustomerNumberEntity(
                phoneNumber = "0241112222",
                normalizedPhoneNumber = "0241112222",
                firstUsedAt = now,
                lastUsedAt = now,
                ownerUserId = "debug-guest-agent",
                syncStatus = "SYNCED"
            )
        )
        dao.insert(
            CustomerNumberEntity(
                phoneNumber = "0553334444",
                normalizedPhoneNumber = "0553334444",
                firstUsedAt = now,
                lastUsedAt = now,
                ownerUserId = null,
                syncStatus = "SYNCED"
            )
        )

        dao.adoptUnownedOrGuestRecords("real-agent-1")

        val pending = dao.getPendingChanges()
        assertEquals(2, pending.size)
        assertTrue(pending.all { it.ownerUserId == "real-agent-1" && it.syncStatus == "PENDING_UPLOAD" })
    }

    @Test
    fun `manual customer save normalizes phone, stores nickname, and triggers cloud sync`() = runTest {
        val dao = InMemoryCustomerDao()
        var syncQueuedCount = 0
        val repo = createRepo(dao) { syncQueuedCount++ }

        val saved = repo.saveCustomerNumberManually("0209489266", "Ama Shop").getOrThrow()
        assertEquals("0209489266", saved.normalizedPhoneNumber)
        assertEquals("Ama Shop", saved.nickname)
        assertEquals("PENDING_UPLOAD", saved.syncStatus)
        assertEquals(1, syncQueuedCount)
    }
}

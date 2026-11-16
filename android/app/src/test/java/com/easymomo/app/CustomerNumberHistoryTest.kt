package com.easymomo.app

import com.easymomo.app.data.local.EasyMoMoDatabase
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
import com.easymomo.app.data.repository.TransactionRepository
import com.easymomo.app.domain.model.NetworkPrefixDetector
import com.easymomo.app.domain.model.NetworkType
import com.easymomo.app.domain.model.ProcessingMethod
import com.easymomo.app.domain.model.TransactionType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.lang.reflect.Proxy

class CustomerNumberHistoryTest {

    private class FakeCustomerNumberDao : CustomerNumberDao {
        private val items = mutableListOf<CustomerNumberEntity>()
        private val flow = MutableStateFlow<List<CustomerNumberEntity>>(emptyList())
        private var nextId = 1L

        override suspend fun getByNormalizedPhone(normalized: String): CustomerNumberEntity? =
            items.firstOrNull { it.normalizedPhoneNumber == normalized }

        override suspend fun insert(entity: CustomerNumberEntity): Long {
            val inserted = entity.copy(id = nextId++)
            items.add(inserted)
            emit()
            return inserted.id
        }

        override suspend fun incrementUsage(id: Long, displayPhone: String, lastUsedAt: Long) {
            val idx = items.indexOfFirst { it.id == id }
            if (idx >= 0) {
                val cur = items[idx]
                items[idx] = cur.copy(
                    phoneNumber = displayPhone,
                    lastUsedAt = lastUsedAt,
                    usageCount = cur.usageCount + 1
                )
                emit()
            }
        }

        override suspend fun searchSuggestions(
            digitQuery: String,
            rawLowerQuery: String,
            limit: Int
        ): List<CustomerNumberEntity> {
            return items.filter {
                it.normalizedPhoneNumber.contains(digitQuery) ||
                    it.phoneNumber.contains(digitQuery) ||
                    (rawLowerQuery.isNotEmpty() && it.nickname?.lowercase()?.contains(rawLowerQuery) == true)
            }.sortedWith(
                compareBy<CustomerNumberEntity>(
                    { if (it.normalizedPhoneNumber.startsWith(digitQuery)) 0 else 1 },
                    { -it.lastUsedAt },
                    { -it.usageCount }
                )
            ).take(limit)
        }

        override fun observeAll(): Flow<List<CustomerNumberEntity>> = flow

        override suspend fun updateNickname(id: Long, nickname: String?) {
            val idx = items.indexOfFirst { it.id == id }
            if (idx >= 0) {
                items[idx] = items[idx].copy(nickname = nickname)
                emit()
            }
        }

        override suspend fun deleteById(id: Long) {
            items.removeAll { it.id == id }
            emit()
        }

        override suspend fun deleteAll() {
            items.clear()
            emit()
        }

        private fun emit() {
            flow.value = items.sortedWith(compareBy({ -it.lastUsedAt }, { -it.usageCount }))
        }
    }

    private fun createRepo(customerDao: CustomerNumberDao): TransactionRepository {
        val txDao = Proxy.newProxyInstance(
            TransactionDao::class.java.classLoader,
            arrayOf(TransactionDao::class.java)
        ) { _, method, _ ->
            when (method.name) {
                "observeRecentTransactions",
                "observeAllTransactions",
                "observeFilteredTransactions",
                "observeTransactionsBetween" -> MutableStateFlow(emptyList<TransactionEntity>())
                "getEventsForTransaction",
                "getUnsyncedTransactions" -> emptyList<Any>()
                else -> null
            }
        } as TransactionDao
        val emptyFlowConfigDao = Proxy.newProxyInstance(
            UssdFlowConfigDao::class.java.classLoader,
            arrayOf(UssdFlowConfigDao::class.java)
        ) { _, _, _ -> null } as UssdFlowConfigDao
        val emptyBundleDao = Proxy.newProxyInstance(
            DataBundleDao::class.java.classLoader,
            arrayOf(DataBundleDao::class.java)
        ) { _, _, _ -> null } as DataBundleDao
        val emptySyncDao = Proxy.newProxyInstance(
            SyncQueueDao::class.java.classLoader,
            arrayOf(SyncQueueDao::class.java)
        ) { _, _, _ -> null } as SyncQueueDao
        val emptyApi = Proxy.newProxyInstance(
            EasyMoMoApiService::class.java.classLoader,
            arrayOf(EasyMoMoApiService::class.java)
        ) { _, _, _ -> null } as EasyMoMoApiService

        return TransactionRepository(
            transactionDao = txDao,
            ussdFlowConfigDao = emptyFlowConfigDao,
            dataBundleDao = emptyBundleDao,
            syncQueueDao = emptySyncDao,
            api = emptyApi,
            customerNumberDao = customerDao
        )
    }

    @Test
    fun `normalizes local and international Ghanaian numbers identically`() {
        assertEquals("0241234567", NetworkPrefixDetector.normalizeGhanaPhone("0241234567"))
        assertEquals("0241234567", NetworkPrefixDetector.normalizeGhanaPhone("233241234567"))
        assertEquals("0241234567", NetworkPrefixDetector.normalizeGhanaPhone("+233241234567"))
        assertEquals("02412", NetworkPrefixDetector.normalizeSearchQuery("+2332412"))
        assertEquals("02412", NetworkPrefixDetector.normalizeSearchQuery("2332412"))
        assertEquals("4567", NetworkPrefixDetector.normalizeSearchQuery("4567"))
    }

    @Test
    fun `saves valid number on initiation, prevents duplicates, and updates usage stats`() = runTest {
        val dao = FakeCustomerNumberDao()
        val repo = createRepo(dao)

        // Invalid / incomplete numbers must not be saved
        assertNull(repo.recordCustomerNumberUsage("02412"))
        assertTrue(dao.observeAll().first().isEmpty())

        // Initiating a transaction saves the customer number immediately
        val tx1 = repo.createDraftTransaction(
            network = NetworkType.MTN,
            type = TransactionType.CASH_IN,
            processingMethod = ProcessingMethod.AGENT_USSD,
            recipientNumber = "0241234567",
            amount = 20.0
        )
        assertNotNull(tx1.customerNumberId)

        // Reusing same number in +233 format does NOT create a duplicate
        val id2 = repo.recordCustomerNumberUsage("+233241234567")
        assertEquals(tx1.customerNumberId, id2)

        val saved = dao.observeAll().first()
        assertEquals(1, saved.size)
        assertEquals("0241234567", saved.first().normalizedPhoneNumber)
        assertEquals(2, saved.first().usageCount)
    }

    @Test
    fun `autocompletes by prefix, partial digits, and ranks prefix matches first`() = runTest {
        val dao = FakeCustomerNumberDao()
        val repo = createRepo(dao)

        dao.insert(
            CustomerNumberEntity(
                phoneNumber = "0550249999",
                normalizedPhoneNumber = "0550249999",
                firstUsedAt = 100L,
                lastUsedAt = 500L,
                usageCount = 10
            )
        )
        dao.insert(
            CustomerNumberEntity(
                phoneNumber = "0241234567",
                normalizedPhoneNumber = "0241234567",
                nickname = "Kwame",
                firstUsedAt = 100L,
                lastUsedAt = 300L,
                usageCount = 1
            )
        )

        // Prefix search "024" ranks exact prefix 0241234567 ahead of mid-match 0550249999
        val prefixMatches = repo.searchCustomerNumberSuggestions("024")
        assertEquals(listOf("0241234567", "0550249999"), prefixMatches.map { it.phoneNumber })

        // International prefix search "+233241" matches 0241234567
        val intlMatches = repo.searchCustomerNumberSuggestions("+233241")
        assertEquals(listOf("0241234567"), intlMatches.map { it.phoneNumber })

        // Partial digit search "9999" matches 0550249999
        val partialMatches = repo.searchCustomerNumberSuggestions("9999")
        assertEquals(listOf("0550249999"), partialMatches.map { it.phoneNumber })

        // Single digit returns empty (not enough digits)
        assertTrue(repo.searchCustomerNumberSuggestions("0").isEmpty())
    }

    @Test
    fun `deletes individual number and clears history`() = runTest {
        val dao = FakeCustomerNumberDao()
        val repo = createRepo(dao)

        val id1 = repo.recordCustomerNumberUsage("0241111111")!!
        repo.recordCustomerNumberUsage("0202222222")!!
        assertEquals(2, dao.observeAll().first().size)

        repo.deleteCustomerNumber(id1)
        assertEquals(listOf("0202222222"), dao.observeAll().first().map { it.phoneNumber })

        repo.clearCustomerNumbers()
        assertTrue(dao.observeAll().first().isEmpty())
    }

    @Test
    fun `migration 1 to 2 is registered on EasyMoMoDatabase`() {
        assertEquals(1, EasyMoMoDatabase.MIGRATION_1_2.startVersion)
        assertEquals(2, EasyMoMoDatabase.MIGRATION_1_2.endVersion)
    }

    @Test
    fun `selected suggestion populates shared transaction variables for all services`() = runTest {
        val dao = FakeCustomerNumberDao()
        val repo = createRepo(dao)

        val now = 1_000L
        dao.insert(
            CustomerNumberEntity(
                phoneNumber = "0241234567",
                normalizedPhoneNumber = "0241234567",
                firstUsedAt = now,
                lastUsedAt = now,
                usageCount = 1
            )
        )
        val suggestion = repo.searchCustomerNumberSuggestions("0241").first()
        assertEquals("0241234567", suggestion.phoneNumber)

        val services = listOf(
            NetworkType.MTN to TransactionType.CASH_IN,
            NetworkType.TELECEL to TransactionType.CASH_IN,
            NetworkType.MTN to TransactionType.CASH_OUT,
            NetworkType.MTN to TransactionType.DATA,
            NetworkType.TELECEL to TransactionType.DATA,
            NetworkType.MTN to TransactionType.AIRTIME,
            NetworkType.TELECEL to TransactionType.AIRTIME
        )
        services.forEach { (network, type) ->
            val tx = repo.createDraftTransaction(
                network = network,
                type = type,
                processingMethod = ProcessingMethod.AGENT_USSD,
                recipientNumber = suggestion.phoneNumber,
                amount = 15.0
            )
            assertEquals("0241234567", tx.recipientNumber)
        }

        val updated = dao.getByNormalizedPhone("0241234567")!!
        assertEquals(8, updated.usageCount)
        assertTrue(updated.lastUsedAt > now)
    }
}

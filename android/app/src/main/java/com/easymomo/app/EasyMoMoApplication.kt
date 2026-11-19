package com.easymomo.app

import android.app.Application
import com.easymomo.app.data.local.EasyMoMoDatabase
import com.easymomo.app.data.local.SessionStore
import com.easymomo.app.data.local.SimPreferencesStore
import com.easymomo.app.data.remote.ApiClient
import com.easymomo.app.data.remote.EasyMoMoApiService
import com.easymomo.app.data.repository.AuthSubscriptionRepository
import com.easymomo.app.data.repository.CustomerSyncManager
import com.easymomo.app.data.repository.CustomUssdRepository
import com.easymomo.app.data.repository.TransactionRepository
import com.easymomo.app.ussd.PermissionCapabilityManager
import com.easymomo.app.ussd.SimManager
import com.easymomo.app.ussd.UssdLauncher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class EasyMoMoApplication : Application() {

    val database: EasyMoMoDatabase by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        EasyMoMoDatabase.getInstance(this)
    }
    val sessionStore: SessionStore by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        SessionStore(this)
    }
    val apiService: EasyMoMoApiService by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        ApiClient.create(sessionStore)
    }
    val customerSyncManager: CustomerSyncManager by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        CustomerSyncManager(
            context = this,
            customerNumberDao = database.customerNumberDao(),
            api = apiService,
            sessionStore = sessionStore,
            simPreferencesStore = simPreferencesStore
        )
    }
    val authRepository: AuthSubscriptionRepository by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        AuthSubscriptionRepository(
            api = apiService,
            sessionStore = sessionStore,
            onUserAuthenticated = { userId -> customerSyncManager.onUserAuthenticated(userId) },
            onUserLoggedOut = { previousUserId -> customerSyncManager.onUserLoggedOut(previousUserId) }
        )
    }
    val transactionRepository: TransactionRepository by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        TransactionRepository(
            transactionDao = database.transactionDao(),
            ussdFlowConfigDao = database.ussdFlowConfigDao(),
            dataBundleDao = database.dataBundleDao(),
            syncQueueDao = database.syncQueueDao(),
            api = apiService,
            customerNumberDao = database.customerNumberDao(),
            currentUserIdProvider = { sessionStore.getUser()?.id },
            onCustomerSyncNeeded = {
                appScope.launch {
                    val enabled = simPreferencesStore.agentSettingsFlow.first().customerCloudSyncEnabled
                    customerSyncManager.scheduleAutoSyncIfEnabled(enabled)
                }
            }
        )
    }
    val ussdLauncher: UssdLauncher by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        UssdLauncher(this)
    }
    val permissionManager: PermissionCapabilityManager by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        PermissionCapabilityManager(this)
    }
    val simPreferencesStore: SimPreferencesStore by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        SimPreferencesStore(this)
    }
    val simManager: SimManager by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        SimManager(this, simPreferencesStore, permissionManager)
    }
    val customUssdRepository: CustomUssdRepository by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        CustomUssdRepository(database.customUssdDao())
    }

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        // Defer heavy initialization (Keystore/EncryptedPrefs, OkHttp/Retrofit, Room seed) off the main thread
        appScope.launch {
            sessionStore.warmUp()
            val seedPrefs = getSharedPreferences(PREFS_BOOTSTRAP, MODE_PRIVATE)
            if (seedPrefs.getInt(KEY_USSD_SEED_VERSION, 0) < BUNDLED_FLOWS_REVISION) {
                transactionRepository.seedInitialUssdConfigsIfNeeded(this@EasyMoMoApplication)
                seedPrefs.edit().putInt(KEY_USSD_SEED_VERSION, BUNDLED_FLOWS_REVISION).apply()
            }
        }
    }

    companion object {
        private const val PREFS_BOOTSTRAP = "easymomo_bootstrap"
        private const val KEY_USSD_SEED_VERSION = "ussd_seed_version"
        private const val BUNDLED_FLOWS_REVISION = 17
    }
}

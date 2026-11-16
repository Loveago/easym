package com.easymomo.app.data.sync

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.easymomo.app.EasyMoMoApplication
import com.easymomo.app.data.repository.SyncExecutionOutcome

class CustomerSyncWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val app = applicationContext as? EasyMoMoApplication ?: return Result.failure()
        return when (app.customerSyncManager.syncNow()) {
            is SyncExecutionOutcome.Success,
            is SyncExecutionOutcome.Skipped -> Result.success()
            is SyncExecutionOutcome.AuthExpired -> Result.failure()
            is SyncExecutionOutcome.RetryableError -> Result.retry()
        }
    }
}

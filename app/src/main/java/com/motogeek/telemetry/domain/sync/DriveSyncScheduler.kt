package com.motogeek.telemetry.domain.sync

import android.content.Context
import android.content.Intent
import androidx.room.InvalidationTracker
import androidx.work.*
import com.motogeek.telemetry.data.local.MotoGeekDatabase
import com.motogeek.telemetry.domain.backup.GoogleAuthManager
import com.motogeek.telemetry.worker.GarageSyncWorker
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.TimeUnit

enum class DriveSyncPhase { IDLE, WAITING, SYNCING, SYNCED, NEEDS_PERMISSION, ACCOUNT_MISMATCH, ERROR }
data class DriveSyncStatus(
    val account: String? = null,
    val phase: DriveSyncPhase = DriveSyncPhase.IDLE,
    val message: String? = null,
    val authorizationIntent: Intent? = null
)

object DriveSyncScheduler {
    private const val WORK_NAME = "garage-google-account-sync"
    private val mutableStatus = MutableStateFlow(DriveSyncStatus())
    val status = mutableStatus.asStateFlow()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var pendingChange: Job? = null
    private var observer: InvalidationTracker.Observer? = null

    @Synchronized fun observeChanges(context: Context) {
        if (observer != null) return
        val app = context.applicationContext
        observer = object : InvalidationTracker.Observer(garageSyncTables.map { it.name }.toTypedArray()) {
            override fun onInvalidated(tables: Set<String>) {
                synchronized(DriveSyncScheduler) {
                    pendingChange?.cancel()
                    pendingChange = scope.launch {
                        delay(5_000)
                        // Append one trailing pass if edits arrived during a running upload.
                        enqueue(app, afterCurrent = true)
                    }
                }
            }
        }.also { MotoGeekDatabase.getDatabase(app).invalidationTracker.addObserver(it) }
    }

    fun enqueue(context: Context, afterCurrent: Boolean = false) {
        val email = GoogleAuthManager(context).getSignedInAccount()?.email ?: return
        val current = mutableStatus.value
        if (current.account != email || current.phase !in setOf(DriveSyncPhase.SYNCING, DriveSyncPhase.NEEDS_PERMISSION)) {
            mutableStatus.value = DriveSyncStatus(email, DriveSyncPhase.WAITING)
        }
        val request = OneTimeWorkRequestBuilder<GarageSyncWorker>()
            .setInputData(workDataOf("account" to email))
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(WORK_NAME,
            if (afterCurrent) ExistingWorkPolicy.APPEND_OR_REPLACE else ExistingWorkPolicy.KEEP, request)
    }

    fun report(value: DriveSyncStatus) { mutableStatus.value = value }

    fun cancel(context: Context) {
        pendingChange?.cancel()
        WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
        mutableStatus.value = DriveSyncStatus()
    }
}

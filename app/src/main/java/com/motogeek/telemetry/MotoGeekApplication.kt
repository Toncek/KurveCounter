package com.motogeek.telemetry

import android.app.Application
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.ExistingPeriodicWorkPolicy
import com.motogeek.telemetry.worker.MaintenanceCheckWorker
import java.util.concurrent.TimeUnit

class MotoGeekApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        com.motogeek.telemetry.domain.sync.DriveSyncScheduler.observeChanges(this)

        // Schedule daily maintenance checks
        val workRequest = PeriodicWorkRequestBuilder<MaintenanceCheckWorker>(24, TimeUnit.HOURS)
            .build()

        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "MaintenanceCheckWork",
            ExistingPeriodicWorkPolicy.KEEP,
            workRequest
        )
    }
}

package com.motogeek.telemetry.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.google.api.client.googleapis.extensions.android.gms.auth.UserRecoverableAuthIOException
import com.google.api.client.http.HttpResponseException
import com.motogeek.telemetry.domain.backup.GoogleAuthManager
import com.motogeek.telemetry.domain.sync.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import java.io.IOException

class GarageSyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val email = inputData.getString("account") ?: return Result.failure()
        if (GoogleAuthManager(applicationContext).getSignedInAccount()?.email != email) return Result.success()
        DriveSyncScheduler.report(DriveSyncStatus(email, DriveSyncPhase.SYNCING))
        return try {
            DriveSyncManager(applicationContext).sync(email)
            DriveSyncScheduler.report(DriveSyncStatus(email, DriveSyncPhase.SYNCED))
            Result.success()
        } catch (e: CancellationException) {
            throw e
        } catch (e: SyncAccountChangedException) {
            Result.success()
        } catch (e: SyncAccountMismatchException) {
            if (GoogleAuthManager(applicationContext).getSignedInAccount()?.email == email) {
                DriveSyncScheduler.report(DriveSyncStatus(email, DriveSyncPhase.ACCOUNT_MISMATCH, e.message))
            }
            Result.failure()
        } catch (e: UserRecoverableAuthIOException) {
            currentCoroutineContext().ensureActive()
            if (GoogleAuthManager(applicationContext).getSignedInAccount()?.email != email) return Result.success()
            DriveSyncScheduler.report(DriveSyncStatus(email, DriveSyncPhase.NEEDS_PERMISSION,
                "Allow Google Drive access to sync your devices.", e.intent))
            Result.failure()
        } catch (e: Exception) {
            currentCoroutineContext().ensureActive()
            if (GoogleAuthManager(applicationContext).getSignedInAccount()?.email != email) return Result.success()
            val retryable = when (e) {
                is HttpResponseException -> e.statusCode == 408 || e.statusCode == 429 || e.statusCode >= 500
                is IOException -> true
                else -> false
            }
            DriveSyncScheduler.report(DriveSyncStatus(email,
                if (retryable) DriveSyncPhase.WAITING else DriveSyncPhase.ERROR,
                if (e is SyncRideInProgressException) e.message
                else if (retryable) "Sync will retry when Google Drive is reachable." else e.message ?: "Google Drive sync failed."))
            if (retryable) Result.retry() else Result.failure()
        }
    }
}

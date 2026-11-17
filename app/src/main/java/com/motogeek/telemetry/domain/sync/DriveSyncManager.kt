package com.motogeek.telemetry.domain.sync

import android.content.Context
import androidx.room.withTransaction
import com.google.api.client.http.ByteArrayContent
import com.google.api.services.drive.model.File
import com.motogeek.telemetry.data.local.MotoGeekDatabase
import com.motogeek.telemetry.domain.backup.GoogleAuthManager
import com.motogeek.telemetry.domain.backup.GoogleDriveClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.ByteArrayOutputStream
import java.util.UUID
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

class SyncAccountChangedException : Exception("Google account changed; sync stopped")
class SyncAccountMismatchException : Exception("This garage is linked to another Google account. Sign back into that account to sync.")
class SyncRideInProgressException : java.io.IOException("Sync will resume after the current ride finishes.")

class DriveSyncManager(private val context: Context) {
    private val json = Json { encodeDefaults = true }
    private val prefs = context.getSharedPreferences("garage_sync", Context.MODE_PRIVATE)

    /** Called only after the user confirms sharing this device's garage with the newly selected account. */
    suspend fun confirmAccountChange(email: String) = mutex.withLock {
        withContext(Dispatchers.IO) {
            checkAccount(email)
            val db = MotoGeekDatabase.getDatabase(context)
            db.withTransaction {
                db.openHelper.writableDatabase.delete("cloud_sync_state", "accountEmail = ?", arrayOf(email))
                checkAccount(email)
            }
            check(prefs.edit().putString("bound_account", email).commit()) { "Could not save sync account" }
        }
    }

    suspend fun sync(email: String) = mutex.withLock {
        withContext(Dispatchers.IO) {
            checkAccount(email)
            val bound = prefs.getString("bound_account", null)
            if (bound != null && bound != email) throw SyncAccountMismatchException()
            val drive = GoogleDriveClient.create(context, email)
            val device = deviceId(context)
            val ownName = "kurvecounter_sync_v1_$device.json.gz"
            val files = mutableListOf<File>()
            var page: String? = null
            do {
                val result = drive.files().list().setSpaces("appDataFolder")
                    .setQ("trashed = false and appProperties has { key='kurveSync' and value='v1' }")
                    .setFields("nextPageToken,files(id,name)").setPageSize(100).setPageToken(page).execute()
                files += result.files.orEmpty()
                page = result.nextPageToken
                currentCoroutineContext().ensureActive()
            } while (page != null)

            var remote = GarageSyncDocument()
            var own: GarageSyncDocument? = null
            val ownFile = files.firstOrNull { it.name == ownName }
            for (file in files) {
                checkAccount(email)
                val document = drive.files().get(file.id).executeMediaAsInputStream().use { input ->
                    GZIPInputStream(input).bufferedReader(Charsets.UTF_8).use { reader ->
                        json.decodeFromString<GarageSyncDocument>(reader.readText())
                    }
                }
                remote = GarageSyncMerge.merge(remote, document)
                if (file.id == ownFile?.id) own = document
                currentCoroutineContext().ensureActive()
            }
            checkAccount(email)
            currentCoroutineContext().ensureActive()
            // Binding stops an account switch from silently publishing the previous account's garage.
            check(prefs.edit().putString("bound_account", email).commit()) { "Could not save sync account" }
            val merged = GarageSyncStore(MotoGeekDatabase.getDatabase(context)).mergeAndApply(email, device, remote) {
                checkAccount(email)
                if (com.motogeek.telemetry.service.TelemetryForegroundService.state.value.isRiding) {
                    throw SyncRideInProgressException()
                }
            }
            checkAccount(email)
            currentCoroutineContext().ensureActive()
            if (merged != own) {
                val bytes = ByteArrayOutputStream().use { output ->
                    GZIPOutputStream(output).use { it.write(json.encodeToString(merged).toByteArray(Charsets.UTF_8)) }
                    output.toByteArray()
                }
                val content = ByteArrayContent("application/gzip", bytes)
                if (ownFile == null) {
                    val metadata = File().setName(ownName).setParents(listOf("appDataFolder"))
                        .setAppProperties(mapOf("kurveSync" to "v1"))
                    drive.files().create(metadata, content).setFields("id").execute()
                } else {
                    drive.files().update(ownFile.id, null, content).setFields("id").execute()
                }
            }
            checkAccount(email)
            prefs.edit().putLong("last_sync_$email", System.currentTimeMillis()).apply()
        }
    }

    private fun checkAccount(expected: String) {
        if (GoogleAuthManager(context).getSignedInAccount()?.email != expected) throw SyncAccountChangedException()
    }

    companion object {
        private val mutex = Mutex()
        // Excluded from Android backup: a restored phone must publish its own replica.
        @Synchronized private fun deviceId(context: Context): String {
            val file = java.io.File(context.noBackupFilesDir, "garage_sync_device_id")
            if (file.exists()) return file.readText().trim().also { UUID.fromString(it) }
            return UUID.randomUUID().toString().also { file.writeText(it) }
        }
    }
}

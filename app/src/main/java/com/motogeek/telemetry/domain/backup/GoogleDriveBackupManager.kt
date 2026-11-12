package com.motogeek.telemetry.domain.backup

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.common.api.Scope
import com.google.api.client.extensions.android.http.AndroidHttp
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
import com.google.api.client.http.ByteArrayContent
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.drive.Drive
import com.google.api.services.drive.DriveScopes
import com.motogeek.telemetry.domain.maintenance.ImportExportManager
import com.motogeek.telemetry.domain.maintenance.RestoreMode
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.time.Instant

class CloudBackupUriRequiredException : Exception("Select where to save your Google Drive backup")
class CloudRestoreUriRequiredException : Exception("Select your Google Drive backup file to restore")

class GoogleDriveBackupManager(
    private val context: Context,
    private val importExportManager: ImportExportManager
) {
    private val prefs = context.getSharedPreferences("google_drive_prefs", Context.MODE_PRIVATE)

    var lastBackupTimestamp: Long
        get() = accountPreferenceKey()?.let { prefs.getLong(it, 0L) } ?: 0L
        private set(value) {
            accountPreferenceKey()?.let { prefs.edit().putLong(it, value).apply() }
        }

    private fun currentAccountEmail(): String? =
        prefs.getString("signed_in_account_email", null)
            ?: GoogleSignIn.getLastSignedInAccount(context)?.email

    private fun accountPreferenceKey(): String? =
        currentAccountEmail()?.let { "last_backup_timestamp_$it" }

    private fun accountUriKey(): String? =
        currentAccountEmail()?.let { "cloud_backup_uri_$it" }

    private fun getDriveService(): Drive? {
        val account = GoogleSignIn.getLastSignedInAccount(context) ?: return null
        if (!GoogleSignIn.hasPermissions(account, Scope(DriveScopes.DRIVE_APPDATA))) {
            return null
        }
        val credential = GoogleAccountCredential.usingOAuth2(
            context, listOf(DriveScopes.DRIVE_APPDATA)
        )
        credential.selectedAccount = account.account

        return Drive.Builder(
            AndroidHttp.newCompatibleTransport(),
            GsonFactory.getDefaultInstance(),
            credential
        )
            .setApplicationName("KurveCounter MotoGeek Telemetry")
            .build()
    }

    suspend fun saveBackupToPickedUri(uri: Uri): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val email = currentAccountEmail()
                ?: return@withContext Result.failure(Exception("Not signed in to Google Drive"))
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
            }
            val jsonString = importExportManager.generateBackupJsonString()
            context.contentResolver.openOutputStream(uri, "wt")?.use { outputStream ->
                outputStream.write(jsonString.toByteArray(Charsets.UTF_8))
                outputStream.flush()
            } ?: return@withContext Result.failure(Exception("Failed to open output stream for selected file"))

            accountUriKey()?.let { prefs.edit().putString(it, uri.toString()).apply() }
            lastBackupTimestamp = Instant.now().toEpochMilli()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e("DriveBackup", "Failed to save backup to picked URI", e)
            Result.failure(e)
        }
    }

    suspend fun restoreBackupFromPickedUri(
        uri: Uri,
        restoreMode: RestoreMode = RestoreMode.MERGE
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val email = currentAccountEmail()
                ?: return@withContext Result.failure(Exception("Not signed in to Google Drive"))
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
            }
            val jsonString = context.contentResolver.openInputStream(uri)?.use { inputStream ->
                inputStream.bufferedReader(Charsets.UTF_8).readText()
            } ?: return@withContext Result.failure(Exception("Failed to read selected backup file"))

            val result = importExportManager.importGarageBackupFromString(jsonString, restoreMode)
            (result.exceptionOrNull() as? CancellationException)?.let { throw it }
            if (result.isSuccess) {
                accountUriKey()?.let { prefs.edit().putString(it, uri.toString()).apply() }
                Result.success(Unit)
            } else {
                Result.failure(result.exceptionOrNull() ?: Exception("Failed to import restored data"))
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e("DriveBackup", "Failed to restore backup from picked URI", e)
            Result.failure(e)
        }
    }

    suspend fun backupToDrive(): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val email = currentAccountEmail()
                ?: return@withContext Result.failure(Exception("Not signed in to Google Drive"))

            // 1. Try Google Drive REST API if authenticated
            val googleAccount = GoogleSignIn.getLastSignedInAccount(context)
            if (googleAccount != null && GoogleSignIn.hasPermissions(googleAccount, Scope(DriveScopes.DRIVE_APPDATA))) {
                val restResult = runCatching {
                    val drive = getDriveService() ?: error("Failed to initialize Google Drive service")
                    val jsonString = importExportManager.generateBackupJsonString()
                    val content = ByteArrayContent.fromString("application/json", jsonString)

                    val fileMetadata = com.google.api.services.drive.model.File().apply {
                        name = "kurvecounter_backup.json"
                        parents = listOf("appDataFolder")
                    }

                    val files = drive.files().list()
                        .setSpaces("appDataFolder")
                        .setQ("name='kurvecounter_backup.json' and trashed = false")
                        .setOrderBy("modifiedTime desc")
                        .setPageSize(1)
                        .setFields("files(id)")
                        .execute()

                    if (files.files.isNotEmpty()) {
                        val fileId = files.files[0].id
                        drive.files().update(fileId, null, content).execute()
                    } else {
                        drive.files().create(fileMetadata, content).execute()
                    }

                    lastBackupTimestamp = Instant.now().toEpochMilli()
                    Unit
                }
                if (restResult.isSuccess) {
                    return@withContext Result.success(Unit)
                }
                Log.w("DriveBackup", "REST API backup failed, falling back to SAF: ${restResult.exceptionOrNull()?.message}")
            }

            // 2. Try saved SAF URI if available
            val savedUriStr = accountUriKey()?.let { prefs.getString(it, null) }
            if (!savedUriStr.isNullOrBlank()) {
                val uri = Uri.parse(savedUriStr)
                val safResult = runCatching {
                    val jsonString = importExportManager.generateBackupJsonString()
                    context.contentResolver.openOutputStream(uri, "wt")?.use { outputStream ->
                        outputStream.write(jsonString.toByteArray(Charsets.UTF_8))
                        outputStream.flush()
                    } ?: error("Could not open output stream for saved cloud backup URI")
                    lastBackupTimestamp = Instant.now().toEpochMilli()
                    Unit
                }
                if (safResult.isSuccess) {
                    return@withContext Result.success(Unit)
                }
                Log.w("DriveBackup", "Saved SAF URI backup failed, requesting new URI: ${safResult.exceptionOrNull()?.message}")
            }

            // 3. Fallback: prompt user to pick cloud destination
            Result.failure(CloudBackupUriRequiredException())
        } catch (e: CancellationException) {
            throw e
        } catch (e: java.net.UnknownHostException) {
            Log.e("DriveBackup", "Backup failed - Network Error", e)
            Result.failure(Exception("Network error: Please check your internet connection."))
        } catch (e: Exception) {
            Log.e("DriveBackup", "Backup failed", e)
            Result.failure(e)
        }
    }

    suspend fun restoreFromDrive(restoreMode: RestoreMode = RestoreMode.MERGE): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val email = currentAccountEmail()
                ?: return@withContext Result.failure(Exception("Not signed in to Google Drive"))

            // 1. Try Google Drive REST API if authenticated
            val googleAccount = GoogleSignIn.getLastSignedInAccount(context)
            if (googleAccount != null && GoogleSignIn.hasPermissions(googleAccount, Scope(DriveScopes.DRIVE_APPDATA))) {
                val restResult = runCatching {
                    val drive = getDriveService() ?: error("Failed to initialize Google Drive service")
                    val files = drive.files().list()
                        .setSpaces("appDataFolder")
                        .setQ("name='kurvecounter_backup.json' and trashed = false")
                        .setOrderBy("modifiedTime desc")
                        .setPageSize(1)
                        .setFields("files(id)")
                        .execute()

                    if (files.files.isEmpty()) {
                        error("No backup found on Google Drive AppData folder")
                    }

                    val fileId = files.files[0].id
                    val jsonString = ByteArrayOutputStream().use { outputStream ->
                        drive.files().get(fileId).executeMediaAndDownloadTo(outputStream)
                        outputStream.toString("UTF-8")
                    }
                    val result = importExportManager.importGarageBackupFromString(jsonString, restoreMode)
                    (result.exceptionOrNull() as? CancellationException)?.let { throw it }
                    if (result.isFailure) {
                        throw (result.exceptionOrNull() ?: Exception("Failed to import restored data"))
                    }
                    Unit
                }
                if (restResult.isSuccess) {
                    return@withContext Result.success(Unit)
                }
                Log.w("DriveBackup", "REST API restore failed, falling back to SAF: ${restResult.exceptionOrNull()?.message}")
            }

            // 2. Try saved SAF URI if available
            val savedUriStr = accountUriKey()?.let { prefs.getString(it, null) }
            if (!savedUriStr.isNullOrBlank()) {
                val uri = Uri.parse(savedUriStr)
                val safResult = runCatching {
                    val jsonString = context.contentResolver.openInputStream(uri)?.use { inputStream ->
                        inputStream.bufferedReader(Charsets.UTF_8).readText()
                    } ?: error("Could not open input stream for saved cloud backup URI")

                    val result = importExportManager.importGarageBackupFromString(jsonString, restoreMode)
                    (result.exceptionOrNull() as? CancellationException)?.let { throw it }
                    if (result.isFailure) {
                        throw (result.exceptionOrNull() ?: Exception("Failed to import restored data"))
                    }
                    Unit
                }
                if (safResult.isSuccess) {
                    return@withContext Result.success(Unit)
                }
                Log.w("DriveBackup", "Saved SAF URI restore failed, requesting file selection: ${safResult.exceptionOrNull()?.message}")
            }

            // 3. Fallback: prompt user to pick backup file to restore
            Result.failure(CloudRestoreUriRequiredException())
        } catch (e: CancellationException) {
            throw e
        } catch (e: java.net.UnknownHostException) {
            Log.e("DriveBackup", "Restore failed - Network Error", e)
            Result.failure(Exception("Network error: Please check your internet connection."))
        } catch (e: Exception) {
            Log.e("DriveBackup", "Restore failed", e)
            Result.failure(e)
        }
    }
}

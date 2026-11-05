package com.motogeek.telemetry.domain.backup

import android.content.Context
import android.util.Log
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.api.client.extensions.android.http.AndroidHttp
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
import com.google.api.client.http.ByteArrayContent
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.drive.Drive
import com.google.api.services.drive.DriveScopes
import com.motogeek.telemetry.domain.maintenance.ImportExportManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.time.Instant

class GoogleDriveBackupManager(
    private val context: Context,
    private val importExportManager: ImportExportManager
) {
    private val prefs = context.getSharedPreferences("google_drive_prefs", Context.MODE_PRIVATE)

    var lastBackupTimestamp: Long
        get() = prefs.getLong("last_backup_timestamp", 0L)
        set(value) = prefs.edit().putLong("last_backup_timestamp", value).apply()

    private fun getDriveService(): Drive? {
        val account = GoogleSignIn.getLastSignedInAccount(context) ?: return null
        val credential = GoogleAccountCredential.usingOAuth2(
            context, listOf(DriveScopes.DRIVE_APPDATA, DriveScopes.DRIVE_FILE)
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

    suspend fun backupToDrive(): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val drive = getDriveService() ?: return@withContext Result.failure(Exception("Not signed in"))

            val jsonString = importExportManager.generateBackupJsonString()
            val content = ByteArrayContent.fromString("application/json", jsonString)

            val fileMetadata = com.google.api.services.drive.model.File().apply {
                name = "kurvecounter_backup.json"
                parents = listOf("appDataFolder")
            }

            // check if file exists
            val files = drive.files().list()
                .setSpaces("appDataFolder")
                .setQ("name='kurvecounter_backup.json'")
                .execute()

            if (files.files.isNotEmpty()) {
                val fileId = files.files[0].id
                drive.files().update(fileId, null, content).execute()
            } else {
                drive.files().create(fileMetadata, content).execute()
            }

            lastBackupTimestamp = Instant.now().toEpochMilli()
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e("DriveBackup", "Backup failed", e)
            Result.failure(e)
        }
    }

    suspend fun restoreFromDrive(): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val drive = getDriveService() ?: return@withContext Result.failure(Exception("Not signed in"))

            val files = drive.files().list()
                .setSpaces("appDataFolder")
                .setQ("name='kurvecounter_backup.json'")
                .execute()

            if (files.files.isEmpty()) {
                return@withContext Result.failure(Exception("No backup found on Drive"))
            }

            val fileId = files.files[0].id
            val outputStream = ByteArrayOutputStream()
            drive.files().get(fileId).executeMediaAndDownloadTo(outputStream)

            val jsonString = outputStream.toString("UTF-8")
            val result = importExportManager.importGarageBackupFromString(jsonString)

            if (result.isSuccess) {
                Result.success(Unit)
            } else {
                Result.failure(result.exceptionOrNull() ?: Exception("Restore failed"))
            }
        } catch (e: Exception) {
            Log.e("DriveBackup", "Restore failed", e)
            Result.failure(e)
        }
    }
}

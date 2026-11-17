package com.motogeek.telemetry.domain.backup

import android.accounts.Account
import android.content.Context
import com.google.api.client.extensions.android.http.AndroidHttp
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.drive.Drive
import com.google.api.services.drive.DriveScopes

/** Always authorize the selected account, never a different cached GoogleSignIn account. */
object GoogleDriveClient {
    fun create(context: Context, email: String): Drive {
        val credential = GoogleAccountCredential.usingOAuth2(context, listOf(DriveScopes.DRIVE_APPDATA))
        credential.selectedAccount = Account(email, "com.google")
        return Drive.Builder(AndroidHttp.newCompatibleTransport(), GsonFactory.getDefaultInstance()) { request ->
            credential.initialize(request)
            request.connectTimeout = 20_000
            request.readTimeout = 60_000
        }.setApplicationName("KurveCounter MotoGeek Telemetry").build()
    }
}

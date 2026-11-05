package com.motogeek.telemetry.domain.backup

import android.content.Context
import android.content.Intent
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.Scope
import com.google.api.services.drive.DriveScopes
import kotlinx.coroutines.tasks.await

class GoogleAuthManager(private val context: Context) {

    private val signInOptions: GoogleSignInOptions = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
        .requestEmail()
        .requestScopes(Scope(DriveScopes.DRIVE_APPDATA), Scope(DriveScopes.DRIVE_FILE))
        .build()

    private val signInClient: GoogleSignInClient = GoogleSignIn.getClient(context, signInOptions)

    fun getSignInIntent(): Intent = signInClient.signInIntent

    fun getSignedInAccount(): GoogleSignInAccount? = GoogleSignIn.getLastSignedInAccount(context)

    suspend fun signOut() {
        try {
            signInClient.signOut().await()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}

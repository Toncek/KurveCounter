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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class GoogleAuthManager(private val context: Context) {

    private val signInOptions: GoogleSignInOptions = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
        .requestEmail()
        .requestScopes(Scope(DriveScopes.DRIVE_APPDATA))
        .build()

    private val signInClient: GoogleSignInClient = GoogleSignIn.getClient(context, signInOptions)

    private val _account = MutableStateFlow(getSignedInAccount())
    val account = _account.asStateFlow()

    fun refreshAccount() {
        _account.value = getSignedInAccount()
    }

    fun handleSignInResult(data: Intent?) {
        GoogleSignIn.getSignedInAccountFromIntent(data)
            .getResult(com.google.android.gms.common.api.ApiException::class.java)
        refreshAccount()
        check(hasDriveAccess(_account.value)) { "Allow Google Drive backup access to continue." }
    }

    fun hasDriveAccess(account: GoogleSignInAccount? = getSignedInAccount()): Boolean =
        account != null && GoogleSignIn.hasPermissions(account, Scope(DriveScopes.DRIVE_APPDATA))

    fun getSignInIntent(): Intent = signInClient.signInIntent

    fun getSignedInAccount(): GoogleSignInAccount? = GoogleSignIn.getLastSignedInAccount(context)

    suspend fun signOut() {
        // Logging out must not also revoke the user's Drive consent.
        signInClient.signOut().await()
        refreshAccount()
    }
}

package com.motogeek.telemetry.domain.backup

import android.accounts.AccountManager
import android.content.Context
import android.content.Intent
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.AccountPicker
import com.google.android.gms.common.api.Scope
import com.google.api.services.drive.DriveScopes
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.tasks.await

data class SignedInGoogleAccount(
    val email: String,
    val displayName: String? = email,
    val id: String? = email
)

class GoogleAuthManager(private val context: Context) {

    private val prefs = context.getSharedPreferences("google_drive_prefs", Context.MODE_PRIVATE)

    private val signInOptions: GoogleSignInOptions = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
        .requestEmail()
        .requestScopes(Scope(DriveScopes.DRIVE_APPDATA))
        .build()

    private val signInClient: GoogleSignInClient = GoogleSignIn.getClient(context, signInOptions)

    private val _account = MutableStateFlow<SignedInGoogleAccount?>(getSignedInAccount())
    val account = _account.asStateFlow()

    fun refreshAccount() {
        _account.value = getSignedInAccount()
    }

    fun handleSignInResult(data: Intent?) {
        val pickedEmail = data?.getStringExtra(AccountManager.KEY_ACCOUNT_NAME)
            ?: data?.getStringExtra("authAccount")
            ?: data?.getStringExtra("accountName")
        if (!pickedEmail.isNullOrBlank()) {
            prefs.edit().putString("signed_in_account_email", pickedEmail).apply()
            refreshAccount()
            return
        }

        val googleAccount = runCatching {
            GoogleSignIn.getSignedInAccountFromIntent(data)
                .getResult(com.google.android.gms.common.api.ApiException::class.java)
        }.getOrNull()

        if (googleAccount != null && !googleAccount.email.isNullOrBlank()) {
            prefs.edit().putString("signed_in_account_email", googleAccount.email).apply()
            refreshAccount()
            return
        }

        refreshAccount()
    }

    fun hasDriveAccess(account: SignedInGoogleAccount? = getSignedInAccount()): Boolean =
        account != null

    fun getSignInIntent(): Intent {
        return try {
            AccountPicker.newChooseAccountIntent(
                AccountPicker.AccountChooserOptions.Builder()
                    .setAllowableAccountsTypes(listOf("com.google"))
                    .build()
            )
        } catch (e: Throwable) {
            signInClient.signInIntent
        }
    }

    fun getSignedInAccount(): SignedInGoogleAccount? {
        val savedEmail = prefs.getString("signed_in_account_email", null)
        if (!savedEmail.isNullOrBlank()) {
            return SignedInGoogleAccount(
                email = savedEmail,
                displayName = savedEmail,
                id = savedEmail
            )
        }

        val lastGoogleAccount = GoogleSignIn.getLastSignedInAccount(context)
        if (lastGoogleAccount != null && !lastGoogleAccount.email.isNullOrBlank()) {
            return SignedInGoogleAccount(
                email = lastGoogleAccount.email!!,
                displayName = lastGoogleAccount.displayName ?: lastGoogleAccount.email,
                id = lastGoogleAccount.id ?: lastGoogleAccount.email
            )
        }

        return null
    }

    suspend fun signOut() {
        prefs.edit().remove("signed_in_account_email").apply()
        runCatching { signInClient.signOut().await() }
        refreshAccount()
    }
}

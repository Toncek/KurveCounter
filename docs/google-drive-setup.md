# Google account and Drive backup setup

The app uses Google Play services sign-in with the `drive.appdata` scope.
The same Google account flow handles returning and new app users; adding or
creating a Google account is managed by Google on the device.

For each distributed build:

1. Enable the Google Drive API in the Google Cloud project used by the app.
2. Configure the OAuth consent screen with the Drive app-data scope. While the
   OAuth app is in Testing, include the signing-in accounts as test users.
3. Register an Android OAuth client for `com.motogeek.telemetry` and the SHA-1
   fingerprint of the certificate signing that build. Debug, locally signed
   release, and Google Play App Signing certificates may differ. Obtain local
   fingerprints with `./gradlew :app:signingReport`; get the Play certificate
   fingerprint from Play Console.
4. Test on a device with current Google Play services and internet access.

This flow does not require a web client secret or Firebase authentication.
Configuration errors (10/12500) require checking the Cloud configuration above.

Manual verification: sign in, grant Drive access, create a backup, sign out,
sign back in to the same account, and restore. Then sign in with a different
account and confirm its timestamp and backup are separate. Also test cancelling
sign-in/consent, revoked consent, offline operation, and an account with no backup.
Backups live in the hidden Drive app-data folder and are not visible as normal
Drive files. Restore merges garage records by ID, as the confirmation describes.

package com.motogeek.telemetry.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.motogeek.telemetry.domain.maintenance.RestoreMode
import com.motogeek.telemetry.ui.theme.*
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OfflineBackupScreen(viewModel: MainViewModel, navController: NavController) {
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val activeBike by viewModel.activeMotorcycle.collectAsState()

    var selectedRestoreMode by remember { mutableStateOf(RestoreMode.MERGE) }
    var hasSafetySnapshot by remember { mutableStateOf(viewModel.importExportManager.hasPreRestoreSnapshot()) }

    // SAF Import Launcher (Supports Local Storage, Google Drive, Nextcloud, Dropbox, etc.)
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            scope.launch {
                val res = viewModel.importExportManager.importGarageBackup(uri, activeBike?.id, selectedRestoreMode)
                hasSafetySnapshot = viewModel.importExportManager.hasPreRestoreSnapshot()
                if (res.isSuccess) {
                    val s = res.getOrNull()
                    snackbarHostState.showSnackbar(
                        "Import Successful! ${s?.motorcyclesImported ?: 0} bikes, ${s?.tasksImported ?: 0} tasks, ${s?.logsImported ?: 0} service logs, ${s?.ridesImported ?: 0} rides."
                    )
                } else {
                    snackbarHostState.showSnackbar("Import Error: ${res.exceptionOrNull()?.message}")
                }
            }
        }
    }

    // SAF Export Launcher (Supports Local Storage, Google Drive, Nextcloud, Dropbox, etc.)
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) {
            scope.launch {
                val res = viewModel.importExportManager.exportGarageBackup(uri)
                if (res.isSuccess) {
                    snackbarHostState.showSnackbar("Garage backup exported successfully!")
                } else {
                    snackbarHostState.showSnackbar("Export Error: ${res.exceptionOrNull()?.message}")
                }
            }
        }
    }



    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = tr("JSON & Cloud Backup", "JSON a Cloud záloha"),
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    )
                },
                navigationIcon = {
                    IconButton(onClick = {
                        if (!navController.popBackStack()) {
                            navController.navigate("home")
                        }
                    }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = RacingAmberPrimary)
                    }
                },
                actions = {
                    val appLang = LocalAppLang.current
                    TextButton(onClick = { viewModel.toggleAppLanguage() }) {
                        Text(
                            text = if (appLang == AppLang.EN) "EN ▸ SK" else "SK ▸ EN",
                            color = TelemetryIceCyan,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = CarbonObsidianBg)
            )
        },
        containerColor = CarbonObsidianBg
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Spacer(modifier = Modifier.height(2.dp))


                Spacer(modifier = Modifier.height(16.dp))
                
                GoogleDriveCloudSyncCard(viewModel = viewModel, snackbarHostState = snackbarHostState)
                
            // Section 1: Backup & Restore Actions Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp),
                colors = CardDefaults.cardColors(containerColor = TftSurfaceDark),
                border = BorderStroke(1.dp, TitaniumBorder)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "GARAGE DATA STORAGE & RESTORE",
                        color = Color(0xFF6B7A99),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        letterSpacing = 1.sp
                    )
                    Text(
                        text = "Full garage backup v2 includes all motorcycles, schedules, torques, service checklists, fuel & part logs, reminders, and GPS ride sessions.",
                        color = Color.Gray,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(top = 4.dp, bottom = 10.dp)
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterChip(
                            selected = selectedRestoreMode == RestoreMode.MERGE,
                            onClick = { selectedRestoreMode = RestoreMode.MERGE },
                            label = { Text("Import Mode: Merge (Safe)", fontSize = 11.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = TelemetryIceCyan.copy(alpha = 0.2f),
                                selectedLabelColor = TelemetryIceCyan
                            )
                        )
                        FilterChip(
                            selected = selectedRestoreMode == RestoreMode.REPLACE,
                            onClick = { selectedRestoreMode = RestoreMode.REPLACE },
                            label = { Text("Replace Garage", fontSize = 11.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = RacingAmberPrimary.copy(alpha = 0.2f),
                                selectedLabelColor = RacingAmberPrimary
                            )
                        )
                    }

                    // Export Full Garage JSON Button
                    Button(
                        onClick = { exportLauncher.launch("kurve_garage_backup_${System.currentTimeMillis()}.json") },
                        colors = ButtonDefaults.buttonColors(containerColor = RacingAmberPrimary),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.CloudUpload, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Export Garage to JSON (Local / Cloud Drive)", fontWeight = FontWeight.Bold)
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Import JSON File Button
                    OutlinedButton(
                        onClick = { importLauncher.launch(arrayOf("application/json", "text/*")) },
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, TitaniumBorder),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.FileOpen, contentDescription = null, tint = TelemetryIceCyan, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Import JSON Backup File (${selectedRestoreMode.name})", color = Color.White, fontWeight = FontWeight.Bold)
                    }

                    if (hasSafetySnapshot) {
                        Spacer(modifier = Modifier.height(10.dp))
                        OutlinedButton(
                            onClick = {
                                scope.launch {
                                    val res = viewModel.importExportManager.restoreLastSafetySnapshot()
                                    if (res.isSuccess) {
                                        snackbarHostState.showSnackbar("Safety Snapshot Restored Successfully!")
                                    } else {
                                        snackbarHostState.showSnackbar("Snapshot Restore Failed: ${res.exceptionOrNull()?.message}")
                                    }
                                }
                            },
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, WarningGold),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.Restore, contentDescription = null, tint = WarningGold, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Undo Last Replace (Restore Safety Snapshot)", color = WarningGold, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(30.dp))
        }
    }
}

@Composable
fun GoogleDriveCloudSyncCard(viewModel: MainViewModel, snackbarHostState: SnackbarHostState?) {
    val scope = rememberCoroutineScope()
    val googleAccount by viewModel.googleAuthManager.account.collectAsState()
    var isSigningIn by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(false) }
    var isSigningOut by remember { mutableStateOf(false) }
    var accountError by remember { mutableStateOf<String?>(null) }
    val lifecycleOwner = androidx.compose.ui.platform.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, viewModel) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                viewModel.googleAuthManager.refreshAccount()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        viewModel.googleAuthManager.refreshAccount()
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    var isBackingUp by remember { mutableStateOf(false) }
    var isRestoring by remember { mutableStateOf(false) }
    var showRestoreConfirm by remember { mutableStateOf(false) }
    var lastCloudBackupTime by remember { mutableStateOf(viewModel.googleDriveBackupManager.lastBackupTimestamp) }
    var pendingRestoreMode by remember { mutableStateOf(RestoreMode.MERGE) }

    LaunchedEffect(googleAccount?.id, googleAccount?.email) {
        lastCloudBackupTime = viewModel.googleDriveBackupManager.lastBackupTimestamp
    }

    val createDocLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) {
            isBackingUp = true
            scope.launch {
                accountError = null
                val res = try {
                    viewModel.googleDriveBackupManager.saveBackupToPickedUri(uri)
                } finally {
                    isBackingUp = false
                }
                if (res.isSuccess) {
                    lastCloudBackupTime = viewModel.googleDriveBackupManager.lastBackupTimestamp
                    snackbarHostState?.showSnackbar("Cloud backup successful")
                } else {
                    accountError = res.exceptionOrNull()?.message ?: "Backup failed"
                }
            }
        }
    }

    val openDocLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            isRestoring = true
            scope.launch {
                accountError = null
                val res = try {
                    viewModel.googleDriveBackupManager.restoreBackupFromPickedUri(uri, pendingRestoreMode)
                } finally {
                    isRestoring = false
                }
                if (res.isSuccess) {
                    snackbarHostState?.showSnackbar("Restore from Drive (${pendingRestoreMode.name}) successful")
                } else {
                    accountError = res.exceptionOrNull()?.message ?: "Restore failed"
                }
            }
        }
    }

    val authorizationLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        viewModel.googleAuthManager.refreshAccount()
        accountError = if (result.resultCode == android.app.Activity.RESULT_OK) {
            "Drive access granted. Tap Backup or Restore again."
        } else {
            "Drive access was cancelled. Allow access to back up or restore."
        }
    }

    fun handleCloudFailure(error: Throwable?) {
        if (error is com.motogeek.telemetry.domain.backup.CloudBackupUriRequiredException) {
            createDocLauncher.launch("kurvecounter_backup.json")
            return
        }
        if (error is com.motogeek.telemetry.domain.backup.CloudRestoreUriRequiredException) {
            openDocLauncher.launch(arrayOf("application/json", "text/*", "*/*"))
            return
        }
        accountError = error?.message ?: "Google Drive operation failed. Please try again."
        val recoveryIntent = (error as? com.google.api.client.googleapis.extensions.android.gms.auth.UserRecoverableAuthIOException)?.intent
        if (recoveryIntent != null) {
            try {
                authorizationLauncher.launch(recoveryIntent)
            } catch (e: Exception) {
                accountError = "Unable to open Google authorization. Check Google Play services and try again."
            }
        }
    }

    val googleSignInLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        isSigningIn = false
        if (result.resultCode == android.app.Activity.RESULT_CANCELED) {
            accountError = "Google sign-in cancelled."
            return@rememberLauncherForActivityResult
        }
        try {
            viewModel.googleAuthManager.handleSignInResult(result.data)
            accountError = null
            lastCloudBackupTime = viewModel.googleDriveBackupManager.lastBackupTimestamp
        } catch (e: com.google.android.gms.common.api.ApiException) {
            accountError = when (e.statusCode) {
                12501 -> "Google sign-in cancelled."
                7 -> "Check your internet connection and try signing in again."
                10, 12500 -> "Google sign-in configuration error. Check the Android OAuth client package and signing SHA-1 in Google Cloud."
                else -> "Google sign-in failed (${e.statusCode}). Please try again."
            }
        } catch (e: Exception) {
            accountError = e.message ?: "Google sign-in failed. Please try again."
        }
    }

    val launchSignIn: () -> Unit = {
        accountError = null
        isSigningIn = true
        try {
            googleSignInLauncher.launch(viewModel.googleAuthManager.getSignInIntent())
        } catch (e: Exception) {
            isSigningIn = false
            accountError = "Unable to open Google sign-in. Check Google Play services and try again."
        }
    }
    val isBusy = isBackingUp || isRestoring || isSigningIn || isSigningOut

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = DarkCharcoalBg),
        border = BorderStroke(1.dp, TitaniumBorder)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.CloudSync, contentDescription = null, tint = ApexAmber, modifier = Modifier.size(24.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Google Drive Cloud Backup", color = ApexAmber, fontWeight = FontWeight.Bold, fontSize = 18.sp)
            }
            Spacer(modifier = Modifier.height(8.dp))
            
            accountError?.let {
                Text(it, color = ApexAmber, fontSize = 13.sp)
                Spacer(modifier = Modifier.height(8.dp))
            }
            if (googleAccount == null || !viewModel.googleAuthManager.hasDriveAccess(googleAccount)) {
                Text("Sign in or create a Google account to back up your garage and service history. Allow Drive access when asked.", color = Color.LightGray, fontSize = 14.sp)
                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = launchSignIn,
                    enabled = !isBusy,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = ApexAmber)
                ) {
                    Icon(
                        Icons.Default.AccountCircle,
                        contentDescription = "Google Icon",
                        modifier = Modifier.size(20.dp),
                        tint = DarkCharcoalBg
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(if (isSigningIn) "Connecting…" else if (googleAccount != null) "Allow Google Drive access" else "Sign in / Sign up with Google", color = DarkCharcoalBg, fontWeight = FontWeight.Bold)
                }
            } else {
                Text("Signed in as: ${googleAccount?.email ?: googleAccount?.displayName}", color = Color.LightGray, fontSize = 14.sp)
                Spacer(modifier = Modifier.height(8.dp))
                
                if (lastCloudBackupTime > 0) {
                    val timeStr = java.time.Instant.ofEpochMilli(lastCloudBackupTime)
                        .atZone(java.time.ZoneId.systemDefault())
                        .format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))
                    Text("Last cloud backup: $timeStr", color = ApexAmber, fontSize = 12.sp)
                } else {
                    Text("Last cloud backup: Never", color = Color.LightGray, fontSize = 12.sp)
                }
                Spacer(modifier = Modifier.height(16.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Button(
                        onClick = {
                            isBackingUp = true
                            scope.launch {
                                accountError = null
                                val res = try {
                                    viewModel.googleDriveBackupManager.backupToDrive()
                                } finally {
                                    isBackingUp = false
                                }
                                if (res.isSuccess) {
                                    lastCloudBackupTime = viewModel.googleDriveBackupManager.lastBackupTimestamp
                                    snackbarHostState?.showSnackbar("Cloud backup successful")
                                } else {
                                    handleCloudFailure(res.exceptionOrNull())
                                }
                            }
                        },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = RacingRed),
                        enabled = !isBusy
                    ) {
                        if (isBackingUp) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color.White, strokeWidth = 2.dp)
                        } else {
                            Text("Backup to Drive", color = Color.White, fontSize = 12.sp)
                        }
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    OutlinedButton(
                        onClick = { showRestoreConfirm = true },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = ApexAmber),
                        border = BorderStroke(1.dp, ApexAmber),
                        enabled = !isBusy
                    ) {
                        if (isRestoring) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), color = ApexAmber, strokeWidth = 2.dp)
                        } else {
                            Text("Restore from Drive", fontSize = 12.sp)
                        }
                    }
                }
                
            }
            if (googleAccount != null) {
                Spacer(modifier = Modifier.height(16.dp))
                OutlinedButton(
                    onClick = {
                        isSigningOut = true
                        scope.launch {
                            accountError = null
                            try {
                                viewModel.googleAuthManager.signOut()
                                lastCloudBackupTime = 0L
                            } catch (e: kotlinx.coroutines.CancellationException) {
                                throw e
                            } catch (e: Exception) {
                                accountError = "Sign-out failed. Please try again."
                            } finally {
                                isSigningOut = false
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    border = BorderStroke(1.dp, TitaniumBorder),
                    enabled = !isBusy,
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.LightGray)
                ) {
                    Text(if (isSigningOut) "Signing out…" else "Sign out")
                }
            }
        }
    }

    if (showRestoreConfirm) {
        var cloudRestoreMode by remember { mutableStateOf(RestoreMode.MERGE) }
        AlertDialog(
            onDismissRequest = { showRestoreConfirm = false },
            title = { Text("Restore from Cloud?", color = Color.White) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Choose how to restore your Google Drive garage backup:",
                        color = Color.LightGray,
                        fontSize = 13.sp
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = cloudRestoreMode == RestoreMode.MERGE,
                            onClick = { cloudRestoreMode = RestoreMode.MERGE },
                            label = { Text("Merge (Safe)", fontSize = 12.sp) }
                        )
                        FilterChip(
                            selected = cloudRestoreMode == RestoreMode.REPLACE,
                            onClick = { cloudRestoreMode = RestoreMode.REPLACE },
                            label = { Text("Replace Garage", fontSize = 12.sp) }
                        )
                    }
                    if (cloudRestoreMode == RestoreMode.REPLACE) {
                        Text(
                            "A pre-restore safety snapshot of your current local garage will be saved automatically before replacing.",
                            color = ApexAmber,
                            fontSize = 11.sp
                        )
                    }
                }
            },
            containerColor = DarkCharcoalBg,
            confirmButton = {
                Button(
                    onClick = {
                        showRestoreConfirm = false
                        isRestoring = true
                        scope.launch {
                            accountError = null
                            pendingRestoreMode = cloudRestoreMode
                            val res = try {
                                viewModel.googleDriveBackupManager.restoreFromDrive(cloudRestoreMode)
                            } finally {
                                isRestoring = false
                            }
                            if (res.isSuccess) {
                                snackbarHostState?.showSnackbar("Restore from Drive (${cloudRestoreMode.name}) successful")
                            } else {
                                handleCloudFailure(res.exceptionOrNull())
                            }
                        }
                    },
                    enabled = !isBusy && viewModel.googleAuthManager.hasDriveAccess(googleAccount),
                    colors = ButtonDefaults.buttonColors(containerColor = RacingRed)
                ) {
                    Text("Confirm Restore", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { showRestoreConfirm = false }) { Text("Cancel", color = ApexAmber) }
            }
        )
    }
}

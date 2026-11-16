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
import com.motogeek.telemetry.domain.maintenance.ImportPreviewPlan
import com.motogeek.telemetry.domain.maintenance.RestoreMode
import com.motogeek.telemetry.ui.components.GuidedImportDialog
import com.motogeek.telemetry.ui.theme.*
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OfflineBackupScreen(viewModel: MainViewModel, navController: NavController) {
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val activeBike by viewModel.activeMotorcycle.collectAsState()
    val allBikes by viewModel.allMotorcycles.collectAsState()
    val appLang = LocalAppLang.current

    var selectedRestoreMode by remember { mutableStateOf(RestoreMode.MERGE) }
    var hasSafetySnapshot by remember { mutableStateOf(viewModel.importExportManager.hasPreRestoreSnapshot()) }

    var pendingPlan by remember { mutableStateOf<ImportPreviewPlan?>(null) }
    var isImportingPlan by remember { mutableStateOf(false) }
    var showReplaceConfirmDialog by remember { mutableStateOf(false) }
    var pendingReplaceUri by remember { mutableStateOf<android.net.Uri?>(null) }
    var showRestoreSnapshotConfirmDialog by remember { mutableStateOf(false) }

    // SAF Import Launcher (Supports Local Storage, Google Drive, Nextcloud, Dropbox, etc.)
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            if (selectedRestoreMode == RestoreMode.MERGE) {
                scope.launch {
                    val planRes = viewModel.importExportManager.analyzeJsonFile(uri)
                    if (planRes.isSuccess) {
                        pendingPlan = planRes.getOrNull()
                    } else {
                        snackbarHostState.showSnackbar(
                            if (appLang == AppLang.SK)
                                "Chyba analýzy JSON súboru: ${planRes.exceptionOrNull()?.message}"
                            else
                                "Failed to analyze JSON file: ${planRes.exceptionOrNull()?.message}"
                        )
                    }
                }
            } else {
                pendingReplaceUri = uri
                showReplaceConfirmDialog = true
            }
        }
    }

    // SAF Export Launcher (Supports Local Storage, Google Drive, Nextcloud, Dropbox, etc.)
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) {
            scope.launch {
                val res = viewModel.importExportManager.exportGarageBackup(uri)
                if (res.isSuccess) {
                    snackbarHostState.showSnackbar(
                        if (appLang == AppLang.SK) "Záloha garáže bola úspešne exportovaná!"
                        else "Garage backup exported successfully!"
                    )
                } else {
                    snackbarHostState.showSnackbar(
                        if (appLang == AppLang.SK) "Chyba exportu: ${res.exceptionOrNull()?.message}"
                        else "Export Error: ${res.exceptionOrNull()?.message}"
                    )
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
                        text = tr("GARAGE DATA STORAGE & RESTORE", "ZÁLOHA A OBNOVENIE GARÁŽE"),
                        color = Color(0xFF6B7A99),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        letterSpacing = 1.sp
                    )
                    Text(
                        text = tr(
                            "Full garage backup v2 includes all motorcycles, schedules, torques, service checklists, fuel & part logs, reminders, and GPS ride sessions.",
                            "Kompletná záloha v2 obsahuje všetky motocykle, plány, uťahovacie momenty, servisné checklisty, tankovania, diely, pripomienky a GPS jazdy."
                        ),
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
                            label = { Text(tr("Import Mode: Merge (Safe)", "Režim importu: Zlúčiť (Bezpečné)"), fontSize = 11.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = TelemetryIceCyan.copy(alpha = 0.2f),
                                selectedLabelColor = TelemetryIceCyan
                            )
                        )
                        FilterChip(
                            selected = selectedRestoreMode == RestoreMode.REPLACE,
                            onClick = { selectedRestoreMode = RestoreMode.REPLACE },
                            label = { Text(tr("Replace Garage", "Nahradiť garáž"), fontSize = 11.sp) },
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
                        Text(
                            text = tr("Export Garage to JSON (Local / Cloud Drive)", "Exportovať garáž do JSON (Lokálne / Cloud)"),
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Import JSON File Button (SAF with application/json, text/*, */*)
                    OutlinedButton(
                        onClick = { importLauncher.launch(arrayOf("application/json", "text/*", "*/*")) },
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, TitaniumBorder),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.FileOpen, contentDescription = null, tint = TelemetryIceCyan, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = tr(
                                "Import JSON Backup File (${if (selectedRestoreMode == RestoreMode.MERGE) "Merge" else "Replace"})",
                                "Importovať JSON zálohu (${if (selectedRestoreMode == RestoreMode.MERGE) "Zlúčenie" else "Nahradenie"})"
                            ),
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    if (hasSafetySnapshot) {
                        Spacer(modifier = Modifier.height(10.dp))
                        OutlinedButton(
                            onClick = { showRestoreSnapshotConfirmDialog = true },
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, WarningGold),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.Restore, contentDescription = null, tint = WarningGold, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = tr("Undo Last Replace (Restore Safety Snapshot)", "Vrátiť nahradenie (Obnoviť bezpečnú zálohu)"),
                                color = WarningGold,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(30.dp))
        }
    }

    // Guided Non-Destructive Import Dialog
    if (pendingPlan != null) {
        GuidedImportDialog(
            plan = pendingPlan!!,
            existingBikes = allBikes,
            isImporting = isImportingPlan,
            onDismiss = { pendingPlan = null },
            onConfirm = { selection ->
                scope.launch {
                    isImportingPlan = true
                    val res = viewModel.importExportManager.executeGuidedImport(
                        plan = pendingPlan!!,
                        selection = selection,
                        langCode = appLang.code
                    )
                    isImportingPlan = false
                    pendingPlan = null
                    hasSafetySnapshot = viewModel.importExportManager.hasPreRestoreSnapshot()
                    if (res.isSuccess) {
                        val s = res.getOrNull()
                        snackbarHostState.showSnackbar(
                            if (appLang == AppLang.SK)
                                "Import dokončený! ${s?.motorcyclesImported ?: 0} motocyklov, ${s?.tasksImported ?: 0} úloh, ${s?.logsImported ?: 0} servisov, ${s?.fuelLogsImported ?: 0} tankovaní, ${s?.ridesImported ?: 0} jázd."
                            else
                                "Import Successful! ${s?.motorcyclesImported ?: 0} bikes, ${s?.tasksImported ?: 0} tasks, ${s?.logsImported ?: 0} service logs, ${s?.fuelLogsImported ?: 0} fuel logs, ${s?.ridesImported ?: 0} rides."
                        )
                    } else {
                        snackbarHostState.showSnackbar(
                            if (appLang == AppLang.SK) "Chyba importu: ${res.exceptionOrNull()?.message}"
                            else "Import Error: ${res.exceptionOrNull()?.message}"
                        )
                    }
                }
            }
        )
    }

    // Replace Garage Warning Dialog
    if (showReplaceConfirmDialog) {
        AlertDialog(
            onDismissRequest = {
                showReplaceConfirmDialog = false
                pendingReplaceUri = null
            },
            containerColor = TftSurfaceDark,
            title = {
                Text(
                    text = tr("Replace Entire Garage?", "Nahradiť celú garáž?"),
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    text = tr(
                        "Warning: Replace mode will wipe all current motorcycles, service logs, and telemetry data and replace them with this backup. A pre-restore safety snapshot will be created automatically before wiping so you can undo.",
                        "Upozornenie: Režim nahradenia vymaže všetky súčasné motocykle, servisné záznamy a telemetriu a nahradí ich touto zálohou. Pred zmazaním sa automaticky vytvorí bezpečnostná záloha na vrátenie zmien."
                    ),
                    color = Color.LightGray,
                    fontSize = 13.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val uri = pendingReplaceUri
                        showReplaceConfirmDialog = false
                        pendingReplaceUri = null
                        if (uri != null) {
                            scope.launch {
                                val res = viewModel.importExportManager.importGarageBackup(uri, activeBike?.id, RestoreMode.REPLACE)
                                hasSafetySnapshot = viewModel.importExportManager.hasPreRestoreSnapshot()
                                if (res.isSuccess) {
                                    val s = res.getOrNull()
                                    snackbarHostState.showSnackbar(
                                        if (appLang == AppLang.SK)
                                            "Garáž úspešne nahradená! ${s?.motorcyclesImported ?: 0} motocyklov, ${s?.tasksImported ?: 0} úloh, ${s?.logsImported ?: 0} servisov, ${s?.ridesImported ?: 0} jázd."
                                        else
                                            "Garage Replaced Successfully! ${s?.motorcyclesImported ?: 0} bikes, ${s?.tasksImported ?: 0} tasks, ${s?.logsImported ?: 0} service logs, ${s?.ridesImported ?: 0} rides."
                                    )
                                } else {
                                    snackbarHostState.showSnackbar(
                                        if (appLang == AppLang.SK) "Chyba nahradenia: ${res.exceptionOrNull()?.message}"
                                        else "Replace Error: ${res.exceptionOrNull()?.message}"
                                    )
                                }
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = RedlineCrimson)
                ) {
                    Text(tr("Replace Garage", "Nahradiť garáž"), color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showReplaceConfirmDialog = false
                        pendingReplaceUri = null
                    }
                ) {
                    Text(tr("Cancel", "Zrušiť"), color = Color.Gray)
                }
            }
        )
    }

    // Safety Snapshot Undo Confirmation Dialog
    if (showRestoreSnapshotConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showRestoreSnapshotConfirmDialog = false },
            containerColor = TftSurfaceDark,
            title = {
                Text(
                    text = tr("Restore Safety Snapshot?", "Obnoviť bezpečnostnú zálohu?"),
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    text = tr(
                        "This will revert your garage to the snapshot automatically saved immediately before the last Replace operation. All data created since then will be reverted.",
                        "Týmto obnovíte garáž zo zálohy vytvorenej tesne pred poslednou operáciou nahradenia. Údaje vytvorené odvtedy budú vrátené."
                    ),
                    color = Color.LightGray,
                    fontSize = 13.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showRestoreSnapshotConfirmDialog = false
                        scope.launch {
                            val res = viewModel.importExportManager.restoreLastSafetySnapshot()
                            hasSafetySnapshot = viewModel.importExportManager.hasPreRestoreSnapshot()
                            if (res.isSuccess) {
                                snackbarHostState.showSnackbar(
                                    if (appLang == AppLang.SK) "Bezpečnostná záloha úspešne obnovená!"
                                    else "Safety Snapshot Restored Successfully!"
                                )
                            } else {
                                snackbarHostState.showSnackbar(
                                    if (appLang == AppLang.SK) "Obnovenie zálohy zlyhalo: ${res.exceptionOrNull()?.message}"
                                    else "Snapshot Restore Failed: ${res.exceptionOrNull()?.message}"
                                )
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = WarningGold)
                ) {
                    Text(tr("Restore Snapshot", "Obnoviť zálohu"), color = Color.Black, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showRestoreSnapshotConfirmDialog = false }) {
                    Text(tr("Cancel", "Zrušiť"), color = Color.Gray)
                }
            }
        )
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
                Text(tr("Google Drive Cloud Backup", "Google Drive Cloudová záloha"), color = ApexAmber, fontWeight = FontWeight.Bold, fontSize = 18.sp)
            }
            Spacer(modifier = Modifier.height(8.dp))

            accountError?.let {
                Text(it, color = ApexAmber, fontSize = 13.sp)
                Spacer(modifier = Modifier.height(8.dp))
            }
            if (googleAccount == null || !viewModel.googleAuthManager.hasDriveAccess(googleAccount)) {
                Text(
                    text = tr(
                        "Sign in or create a Google account to back up your garage and service history. Allow Drive access when asked.",
                        "Prihláste sa k účtu Google pre zálohovanie garáže a servisnej histórie. Povoľte prístup k Disku Google."
                    ),
                    color = Color.LightGray,
                    fontSize = 14.sp
                )
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
                    Text(
                        text = if (isSigningIn) tr("Connecting…", "Pripája sa…")
                        else if (googleAccount != null) tr("Allow Google Drive access", "Povoliť prístup k Disku Google")
                        else tr("Sign in / Sign up with Google", "Prihlásiť sa cez Google"),
                        color = DarkCharcoalBg,
                        fontWeight = FontWeight.Bold
                    )
                }
            } else {
                Text(
                    text = tr(
                        "Signed in as: ${googleAccount?.email ?: googleAccount?.displayName}",
                        "Prihlásený ako: ${googleAccount?.email ?: googleAccount?.displayName}"
                    ),
                    color = Color.LightGray,
                    fontSize = 14.sp
                )
                Spacer(modifier = Modifier.height(8.dp))

                if (lastCloudBackupTime > 0) {
                    val timeStr = java.time.Instant.ofEpochMilli(lastCloudBackupTime)
                        .atZone(java.time.ZoneId.systemDefault())
                        .format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))
                    Text(
                        text = tr("Last cloud backup: $timeStr", "Posledná cloud záloha: $timeStr"),
                        color = ApexAmber,
                        fontSize = 12.sp
                    )
                } else {
                    Text(
                        text = tr("Last cloud backup: Never", "Posledná cloud záloha: Nikdy"),
                        color = Color.LightGray,
                        fontSize = 12.sp
                    )
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
                            Text(tr("Backup to Drive", "Zálohovať na Disk"), color = Color.White, fontSize = 12.sp)
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
                            Text(tr("Restore from Drive", "Obnoviť z Disku"), fontSize = 12.sp)
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
                    Text(if (isSigningOut) tr("Signing out…", "Odhlasovanie…") else tr("Sign out", "Odhlásiť sa"))
                }
            }
        }
    }

    if (showRestoreConfirm) {
        var cloudRestoreMode by remember { mutableStateOf(RestoreMode.MERGE) }
        AlertDialog(
            onDismissRequest = { showRestoreConfirm = false },
            title = { Text(tr("Restore from Cloud?", "Obnoviť z cloudu?"), color = Color.White) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        tr("Choose how to restore your Google Drive garage backup:", "Zvoľte spôsob obnovenia zálohy z Disku Google:"),
                        color = Color.LightGray,
                        fontSize = 13.sp
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = cloudRestoreMode == RestoreMode.MERGE,
                            onClick = { cloudRestoreMode = RestoreMode.MERGE },
                            label = { Text(tr("Merge (Safe)", "Zlúčiť (Bezpečné)"), fontSize = 12.sp) }
                        )
                        FilterChip(
                            selected = cloudRestoreMode == RestoreMode.REPLACE,
                            onClick = { cloudRestoreMode = RestoreMode.REPLACE },
                            label = { Text(tr("Replace Garage", "Nahradiť garáž"), fontSize = 12.sp) }
                        )
                    }
                    if (cloudRestoreMode == RestoreMode.REPLACE) {
                        Text(
                            tr(
                                "A pre-restore safety snapshot of your current local garage will be saved automatically before replacing.",
                                "Pred nahradením sa automaticky vytvorí bezpečnostná záloha lokálnej garáže."
                            ),
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
                    Text(tr("Confirm Restore", "Potvrdiť obnovenie"), color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { showRestoreConfirm = false }) { Text(tr("Cancel", "Zrušiť"), color = ApexAmber) }
            }
        )
    }
}

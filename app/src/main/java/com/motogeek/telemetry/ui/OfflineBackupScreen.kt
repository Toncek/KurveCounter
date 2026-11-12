package com.motogeek.telemetry.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.motogeek.telemetry.domain.maintenance.MotorcycleBackupDto
import com.motogeek.telemetry.domain.maintenance.MotorcycleTemplateCatalog
import com.motogeek.telemetry.domain.maintenance.RestoreMode
import com.motogeek.telemetry.ui.theme.*
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OfflineBackupScreen(viewModel: MainViewModel, navController: NavController) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val activeBike by viewModel.activeMotorcycle.collectAsState()

    var showPasteDialog by remember { mutableStateOf(false) }
    var pasteJsonText by remember { mutableStateOf("") }
    var selectedRestoreMode by remember { mutableStateOf(RestoreMode.MERGE) }
    var hasSafetySnapshot by remember { mutableStateOf(viewModel.importExportManager.hasPreRestoreSnapshot()) }
    var pendingTemplate by remember { mutableStateOf<Pair<String, MotorcycleBackupDto>?>(null) }

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

                    Spacer(modifier = Modifier.height(10.dp))

                    // Paste JSON from Clipboard Button
                    OutlinedButton(
                        onClick = {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            val clipItem = clipboard.primaryClip?.getItemAt(0)?.text?.toString()
                            if (!clipItem.isNullOrBlank()) {
                                pasteJsonText = clipItem
                            }
                            showPasteDialog = true
                        },
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, TitaniumBorder),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.ContentPaste, contentDescription = null, tint = ApexEmeraldGreen, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Paste AI JSON / Clipboard Import", color = Color.White, fontWeight = FontWeight.Bold)
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

            // Section 2: AI Service Manual Prompt Generator
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp),
                colors = CardDefaults.cardColors(containerColor = TftSurfaceDark),
                border = BorderStroke(1.dp, TitaniumBorder)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "AI SERVICE MANUAL EXTRACTOR",
                            color = Color(0xFF6B7A99),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            letterSpacing = 1.sp
                        )
                        Icon(Icons.Default.Psychology, contentDescription = null, tint = RacingAmberPrimary)
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Feed your motorcycle's PDF service manual or workshop book into ChatGPT, Claude, or Gemini using our Master Mechanic prompt" + (activeBike?.let { " tailored for ${it.year} ${it.make} ${it.model}" } ?: "") + ". Copy the resulting JSON and paste it into KurveCounter to auto-populate all service schedules and bolt torques!",
                        color = Color.LightGray,
                        fontSize = 12.sp,
                        lineHeight = 17.sp
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    val appLang = LocalAppLang.current
                    Button(
                        onClick = {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            val prompt = viewModel.importExportManager.buildAiServiceManualPrompt(activeBike, appLang.code)
                            clipboard.setPrimaryClip(ClipData.newPlainText("AI Mechanic Prompt", prompt))
                            scope.launch {
                                snackbarHostState.showSnackbar(
                                    if (appLang == AppLang.SK) "AI prompt skopírovaný do schránky!"
                                    else "AI Master Mechanic Prompt copied to clipboard!"
                                )
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = TftSurfaceElevated),
                        border = BorderStroke(1.dp, RacingAmberPrimary),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.ContentCopy, contentDescription = null, tint = RacingAmberPrimary, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Copy AI Master Mechanic Prompt", color = RacingAmberPrimary, fontWeight = FontWeight.Bold)
                    }
                }
            }

            // Section 3: Built-In Factory Motorcycle Templates
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp),
                colors = CardDefaults.cardColors(containerColor = TftSurfaceDark),
                border = BorderStroke(1.dp, TitaniumBorder)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "BUILT-IN MOTORCYCLE TEMPLATES",
                        color = Color(0xFF6B7A99),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        letterSpacing = 1.sp
                    )
                    Spacer(modifier = Modifier.height(10.dp))

                    TemplateTile("Universal Motorcycle", "Standard 5000 km intervals, 4-stroke", onApply = {
                        pendingTemplate = "Universal Motorcycle" to MotorcycleTemplateCatalog.universal
                    })
                    TemplateTile("Yamaha MT-09 / Tracer 9", "CP3 890cc • 10,000 km service • Factory torques", onApply = {
                        pendingTemplate = "Yamaha MT-09" to MotorcycleTemplateCatalog.mt09
                    })
                    TemplateTile("Honda CB650R / CBR650R", "Inline-4 649cc • 12,000 km service intervals", onApply = {
                        pendingTemplate = "Honda CB650R" to MotorcycleTemplateCatalog.cb650r
                    })
                    TemplateTile("BMW R1250GS Boxer", "1254cc ShiftCam • Cardan shaft & valve checks", onApply = {
                        pendingTemplate = "BMW R1250GS" to MotorcycleTemplateCatalog.r1250gs
                    })
                    TemplateTile("KTM 890 Duke / Adventure", "889cc Parallel-Twin • 15,000 km intervals & torques", onApply = {
                        pendingTemplate = "KTM 890 Duke" to MotorcycleTemplateCatalog.ktm890
                    })
                    TemplateTile("Kawasaki Z900", "948cc Inline-4 • 12,000 km intervals & torques", onApply = {
                        pendingTemplate = "Kawasaki Z900" to MotorcycleTemplateCatalog.z900
                    })
                }
            }

            Spacer(modifier = Modifier.height(30.dp))
        }
    }

    pendingTemplate?.let { (label, templateDto) ->
        AlertDialog(
            onDismissRequest = { pendingTemplate = null },
            containerColor = TftSurfaceDark,
            title = { Text("Apply $label Template?", color = Color.White, fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    text = if (activeBike != null) {
                        "Choose whether to apply $label factory specs & schedule to your active motorcycle (${activeBike?.displayName}) or add it as a new motorcycle in your garage."
                    } else {
                        "Add $label with factory specs, torques, and maintenance schedule to your garage?"
                    },
                    color = Color.LightGray,
                    fontSize = 13.sp
                )
            },
            confirmButton = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (activeBike != null) {
                        Button(
                            onClick = {
                                viewModel.applyTemplate(templateDto, applyToActiveBike = true)
                                pendingTemplate = null
                                scope.launch { snackbarHostState.showSnackbar("Applied $label specs & schedule to ${activeBike?.displayName}!") }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = RacingAmberPrimary),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Update Active Bike (${activeBike?.displayName})", fontWeight = FontWeight.Bold)
                        }
                    }
                    OutlinedButton(
                        onClick = {
                            viewModel.applyTemplate(templateDto, applyToActiveBike = false)
                            pendingTemplate = null
                            scope.launch { snackbarHostState.showSnackbar("Added $label as a new motorcycle!") }
                        },
                        border = BorderStroke(1.dp, TelemetryIceCyan),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Add as New Motorcycle", color = TelemetryIceCyan, fontWeight = FontWeight.Bold)
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingTemplate = null }) {
                    Text("Cancel", color = Color.Gray)
                }
            }
        )
    }

    if (showPasteDialog) {
        AlertDialog(
            onDismissRequest = { showPasteDialog = false },
            containerColor = TftSurfaceDark,
            title = { Text("Paste AI JSON / Schedule", color = Color.White, fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text(
                        text = "Paste the JSON output produced by the LLM from your service manual:",
                        color = Color.LightGray,
                        fontSize = 12.sp
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedTextField(
                        value = pasteJsonText,
                        onValueChange = { pasteJsonText = it },
                        placeholder = { Text("{\n  \"technicalSpecs\": {...},\n  \"maintenanceSchedule\": [...]\n}", color = Color.DarkGray) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = RacingAmberPrimary,
                            unfocusedBorderColor = TitaniumBorder,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp)
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        scope.launch {
                            val res = viewModel.importExportManager.importFromJsonString(
                                pasteJsonText,
                                activeBike?.id,
                                selectedRestoreMode
                            )
                            hasSafetySnapshot = viewModel.importExportManager.hasPreRestoreSnapshot()
                            if (res.isSuccess) {
                                val s = res.getOrNull()
                                snackbarHostState.showSnackbar("Successfully imported! ${s?.motorcyclesImported ?: 0} bikes, ${s?.tasksImported ?: 0} tasks.")
                                showPasteDialog = false
                            } else {
                                snackbarHostState.showSnackbar("Parsing Failed: ${res.exceptionOrNull()?.message}")
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = RacingAmberPrimary)
                ) {
                    Text("Import JSON", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showPasteDialog = false }) { Text("Cancel", color = Color.Gray) }
            }
        )
    }
}

@Composable
private fun TemplateTile(title: String, subtitle: String, onApply: () -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        shape = RoundedCornerShape(8.dp),
        color = TftSurfaceElevated,
        border = BorderStroke(1.dp, TitaniumBorder)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Text(subtitle, color = Color.Gray, fontSize = 11.sp)
            }
            Button(
                onClick = onApply,
                colors = ButtonDefaults.buttonColors(containerColor = RacingAmberPrimary.copy(alpha = 0.2f)),
                border = BorderStroke(1.dp, RacingAmberPrimary),
                shape = RoundedCornerShape(6.dp),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                modifier = Modifier.height(32.dp)
            ) {
                Text("Apply", color = RacingAmberPrimary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
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

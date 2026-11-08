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
import com.motogeek.telemetry.domain.maintenance.MotorcycleTemplateCatalog
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



    // SAF Import Launcher (Supports Local Storage, Google Drive, Nextcloud, Dropbox, etc.)
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            scope.launch {
                val res = viewModel.importExportManager.importGarageBackup(uri, activeBike?.id)
                if (res.isSuccess) {
                    val s = res.getOrNull()
                    snackbarHostState.showSnackbar("Import Successful! ${s?.motorcyclesImported ?: 0} bikes, ${s?.tasksImported ?: 0} tasks, ${s?.logsImported ?: 0} logs added.")
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
                        text = "JSON & Cloud Backup",
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
                        text = "Full garage backup includes motorcycles, custom schedules, torque tables, and all service records.",
                        color = Color.Gray,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(top = 4.dp, bottom = 14.dp)
                    )

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
                        Text("Import JSON Backup File", color = Color.White, fontWeight = FontWeight.Bold)
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

                    Button(
                        onClick = {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            clipboard.setPrimaryClip(ClipData.newPlainText("AI Mechanic Prompt", viewModel.importExportManager.aiServiceManualPrompt))
                            scope.launch {
                                snackbarHostState.showSnackbar("AI Master Mechanic Prompt copied to clipboard!")
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
                        viewModel.applyTemplate(MotorcycleTemplateCatalog.universal)
                        scope.launch { snackbarHostState.showSnackbar("Universal template applied!") }
                    })
                    TemplateTile("Yamaha MT-09 / Tracer 9", "CP3 890cc • 10,000 km service • Factory torques", onApply = {
                        viewModel.applyTemplate(MotorcycleTemplateCatalog.mt09)
                        scope.launch { snackbarHostState.showSnackbar("Yamaha MT-09 template applied!") }
                    })
                    TemplateTile("Honda CB650R / CBR650R", "Inline-4 649cc • 12,000 km service intervals", onApply = {
                        viewModel.applyTemplate(MotorcycleTemplateCatalog.cb650r)
                        scope.launch { snackbarHostState.showSnackbar("Honda CB650R template applied!") }
                    })
                    TemplateTile("BMW R1250GS Boxer", "1254cc ShiftCam • Cardan shaft & valve checks", onApply = {
                        viewModel.applyTemplate(MotorcycleTemplateCatalog.r1250gs)
                        scope.launch { snackbarHostState.showSnackbar("BMW R1250GS template applied!") }
                    })
                    TemplateTile("KTM 890 Duke / Adventure", "889cc Parallel-Twin • 15,000 km intervals & torques", onApply = {
                        viewModel.applyTemplate(MotorcycleTemplateCatalog.ktm890)
                        scope.launch { snackbarHostState.showSnackbar("KTM 890 template applied!") }
                    })
                }
            }

            Spacer(modifier = Modifier.height(30.dp))
        }
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
                            val res = viewModel.importExportManager.importFromJsonString(pasteJsonText, activeBike?.id)
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
    var googleAccount by remember { mutableStateOf(viewModel.googleAuthManager.getSignedInAccount()) }
    var isBackingUp by remember { mutableStateOf(false) }
    var isRestoring by remember { mutableStateOf(false) }
    var showRestoreConfirm by remember { mutableStateOf(false) }
    var lastCloudBackupTime by remember { mutableStateOf(viewModel.googleDriveBackupManager.lastBackupTimestamp) }

    val googleSignInLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            val task = com.google.android.gms.auth.api.signin.GoogleSignIn.getSignedInAccountFromIntent(result.data)
            try {
                val account = task.getResult(com.google.android.gms.common.api.ApiException::class.java)
                googleAccount = account
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

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
            
            if (googleAccount == null) {
                Text("Sign in to securely backup your garage telemetry and service history to Google Drive.", color = Color.LightGray, fontSize = 14.sp)
                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = { googleSignInLauncher.launch(viewModel.googleAuthManager.getSignInIntent()) },
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
                    Text("Sign in with Google", color = DarkCharcoalBg, fontWeight = FontWeight.Bold)
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
                                val res = viewModel.googleDriveBackupManager.backupToDrive()
                                isBackingUp = false
                                if (res.isSuccess) {
                                    lastCloudBackupTime = viewModel.googleDriveBackupManager.lastBackupTimestamp
                                    snackbarHostState?.showSnackbar("Cloud backup successful")
                                } else {
                                    snackbarHostState?.showSnackbar("Cloud backup failed: ${res.exceptionOrNull()?.message}")
                                }
                            }
                        },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = RacingRed),
                        enabled = !isBackingUp && !isRestoring
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
                        enabled = !isBackingUp && !isRestoring
                    ) {
                        if (isRestoring) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), color = ApexAmber, strokeWidth = 2.dp)
                        } else {
                            Text("Restore from Drive", fontSize = 12.sp)
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                OutlinedButton(
                    onClick = {
                        scope.launch {
                            viewModel.googleAuthManager.signOut()
                            googleAccount = null
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    border = BorderStroke(1.dp, TitaniumBorder),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.LightGray)
                ) {
                    Text("Sign out")
                }
            }
        }
    }

    if (showRestoreConfirm) {
        AlertDialog(
            onDismissRequest = { showRestoreConfirm = false },
            title = { Text("Restore from Cloud?", color = Color.White) },
            text = { Text("This will add the cloud garage backup to your current data. Duplicates may be overwritten based on ID.", color = Color.LightGray) },
            containerColor = DarkCharcoalBg,
            confirmButton = {
                Button(
                    onClick = {
                        showRestoreConfirm = false
                        isRestoring = true
                        scope.launch {
                            val res = viewModel.googleDriveBackupManager.restoreFromDrive()
                            isRestoring = false
                            if (res.isSuccess) {
                                snackbarHostState?.showSnackbar("Restore from Drive successful")
                            } else {
                                snackbarHostState?.showSnackbar("Restore failed: ${res.exceptionOrNull()?.message}")
                            }
                        }
                    },
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

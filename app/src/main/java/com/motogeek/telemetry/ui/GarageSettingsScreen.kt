package com.motogeek.telemetry.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope

@Composable
fun GarageSettingsScreen(viewModel: MainViewModel) {
    val clipboardManager = LocalClipboardManager.current
    val coroutineScope = rememberCoroutineScope()

    var showPasteDialog by remember { mutableStateOf(false) }
    var pastedJson by remember { mutableStateOf("") }

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            coroutineScope.launch {
                viewModel.importExportManager.importGarageBackup(uri)
            }
        }
    }

    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri: Uri? ->
        if (uri != null) {
            coroutineScope.launch {
                viewModel.importExportManager.exportGarageBackup(uri)
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text(
            text = "Garage & Settings",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(32.dp))

        Button(
            onClick = {
                clipboardManager.setText(AnnotatedString(viewModel.importExportManager.aiServiceManualPrompt))
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Copy AI Service Manual Prompt")
        }

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = { showPasteDialog = true },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Paste JSON Schedule / Template")
        }

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = { importLauncher.launch(arrayOf("application/json", "*/*")) },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Import JSON from File")
        }

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = { exportLauncher.launch("motogeek_garage_backup.json") },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Export Garage Backup")
        }

        if (showPasteDialog) {
            AlertDialog(
                onDismissRequest = { showPasteDialog = false },
                title = { Text("Paste JSON") },
                text = {
                    OutlinedTextField(
                        value = pastedJson,
                        onValueChange = { pastedJson = it },
                        modifier = Modifier.fillMaxWidth().height(200.dp),
                        label = { Text("JSON String") }
                    )
                },
                confirmButton = {
                    Button(onClick = {
                        coroutineScope.launch {
                            viewModel.importExportManager.importGarageBackupFromString(pastedJson)
                        }
                        showPasteDialog = false
                    }) { Text("Import") }
                },
                dismissButton = {
                    TextButton(onClick = { showPasteDialog = false }) { Text("Cancel") }
                }
            )
        }
    }
}

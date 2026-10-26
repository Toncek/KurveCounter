package com.motogeek.telemetry.ui

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import kotlinx.coroutines.launch
import com.motogeek.telemetry.ui.theme.CrimsonHeaderRed

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OfflineBackupScreen(viewModel: MainViewModel, navController: NavController) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            scope.launch {
                val res = viewModel.importExportManager.importGarageBackup(uri, null)
                if (res.isSuccess) {
                    val summary = res.getOrNull()
                    Toast.makeText(context, "Imported ${summary?.motorcyclesImported} bikes, ${summary?.tasksImported} tasks, ${summary?.logsImported} logs.", Toast.LENGTH_LONG).show()
                } else {
                    Toast.makeText(context, "Import Failed: ${res.exceptionOrNull()?.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) {
            scope.launch {
                val res = viewModel.importExportManager.exportGarageBackup(uri)
                if (res.isSuccess) {
                    Toast.makeText(context, "Backup exported successfully", Toast.LENGTH_LONG).show()
                } else {
                    Toast.makeText(context, "Export Failed: ${res.exceptionOrNull()?.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Offline Backup", color = androidx.compose.ui.graphics.Color.White) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = CrimsonHeaderRed)
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier.padding(innerPadding).padding(16.dp).fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Button(onClick = { importLauncher.launch(arrayOf("application/json")) }, modifier = Modifier.fillMaxWidth()) {
                Text("Import from JSON File")
            }
            Button(onClick = {
                // In a real implementation we would paste from clipboard manager
                Toast.makeText(context, "Paste JSON not fully implemented in UI", Toast.LENGTH_SHORT).show()
            }, modifier = Modifier.fillMaxWidth()) {
                Text("Paste JSON from Clipboard")
            }
            Button(onClick = { exportLauncher.launch("motogeek_backup.json") }, modifier = Modifier.fillMaxWidth()) {
                Text("Export Garage Backup (JSON)")
            }
            Button(onClick = {
                val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                val clip = android.content.ClipData.newPlainText("AI Prompt", viewModel.importExportManager.aiServiceManualPrompt)
                clipboard.setPrimaryClip(clip)
                Toast.makeText(context, "Prompt copied to clipboard!", Toast.LENGTH_SHORT).show()
            }, modifier = Modifier.fillMaxWidth()) {
                Text("Copy AI Service Manual Prompt")
            }
        }
    }
}

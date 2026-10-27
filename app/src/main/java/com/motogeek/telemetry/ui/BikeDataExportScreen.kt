package com.motogeek.telemetry.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import kotlinx.coroutines.launch
import com.motogeek.telemetry.ui.theme.CrimsonHeaderRed

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BikeDataExportScreen(viewModel: MainViewModel, navController: NavController) {
    val activeBike by viewModel.activeMotorcycle.collectAsState(initial = null)
    val scope = rememberCoroutineScope()

    val exportHtmlLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/html")) { uri ->
        uri?.let {
            scope.launch {
                activeBike?.let { bike ->
                    viewModel.importExportManager.exportBikeServiceBookHtml(bike.id)
                }
            }
        }
    }

    val exportCsvLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        uri?.let {
            scope.launch {
                activeBike?.let { bike ->
                    viewModel.importExportManager.exportAllLogsAsCsv(it, bike.id)
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Export Data", color = Color.White) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = CrimsonHeaderRed)
            )
        }
    ) { innerPadding ->
        Column(modifier = Modifier.padding(innerPadding).padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Button(onClick = { exportHtmlLauncher.launch("service_book.html") }, modifier = Modifier.fillMaxWidth()) {
                Text("Export Service Book (HTML)")
            }
            Button(onClick = { exportCsvLauncher.launch("logs.csv") }, modifier = Modifier.fillMaxWidth()) {
                Text("Export Ride (CSV)")
            }
        }
    }
}

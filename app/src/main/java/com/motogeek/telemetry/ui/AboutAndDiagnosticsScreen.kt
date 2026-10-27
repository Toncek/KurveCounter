package com.motogeek.telemetry.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.motogeek.telemetry.ui.theme.CrimsonHeaderRed

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutAndDiagnosticsScreen(viewModel: MainViewModel, navController: NavController) {
    val telemetryState by viewModel.liveTelemetryState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("About & Diagnostics", color = Color.White) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = CrimsonHeaderRed)
            )
        }
    ) { innerPadding ->
        Column(modifier = Modifier.padding(innerPadding).padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("Diagnostics")
            Text("Lean Angle: \${telemetryState.leanAngleDeg}")
            Text("Lateral G: \${telemetryState.lateralG}")
            Text("Longitudinal G: \${telemetryState.longitudinalG}")

            Button(onClick = { viewModel.resetPeaks() }) {
                Text("Test Pocket Mode Calibration")
            }
        }
    }
}

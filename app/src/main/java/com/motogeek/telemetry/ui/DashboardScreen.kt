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
fun DashboardScreen(viewModel: MainViewModel, navController: NavController) {
    val activeBike by viewModel.activeMotorcycle.collectAsState(initial = null)

    val totalService by produceState(initialValue = 0.0, activeBike) {
        activeBike?.let { value = viewModel.repository.getTotalServiceCost(it.id) ?: 0.0 }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Dashboard", color = Color.White) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = CrimsonHeaderRed)
            )
        }
    ) { innerPadding ->
        Column(modifier = Modifier.padding(innerPadding).padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Total Cost of Ownership")
                    Text("Services: $totalService")
                }
            }
        }
    }
}

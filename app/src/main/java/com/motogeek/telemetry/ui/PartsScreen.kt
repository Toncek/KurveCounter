package com.motogeek.telemetry.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.motogeek.telemetry.ui.theme.CrimsonHeaderRed

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PartsScreen(viewModel: MainViewModel, navController: NavController) {
    val activeBike by viewModel.activeMotorcycle.collectAsState(initial = null)
    val partLogs by produceState(initialValue = emptyList<com.motogeek.telemetry.data.local.PartLogEntity>(), activeBike) {
        activeBike?.let {
            viewModel.repository.getPartLogsForMotorcycle(it.id).collect { logs ->
                value = logs
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Parts") },
                actions = { TextButton(onClick = { /* Add Logic */ }) { Text("+ ADD") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = CrimsonHeaderRed, titleContentColor = androidx.compose.ui.graphics.Color.White, actionIconContentColor = androidx.compose.ui.graphics.Color.White)
            )
        }
    ) { innerPadding ->
        LazyColumn(contentPadding = innerPadding, modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(partLogs) { log ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("${log.partName} - ${log.cost}฿")
                        Text("Brand: ${log.brand}, Part #: ${log.partNumber}")
                    }
                }
            }
        }
    }
}

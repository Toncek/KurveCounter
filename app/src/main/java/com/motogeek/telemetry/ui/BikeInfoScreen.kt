package com.motogeek.telemetry.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.motogeek.telemetry.ui.components.ActiveBikeDropdownCard
import com.motogeek.telemetry.ui.components.SegmentedDigitalText
import com.motogeek.telemetry.ui.theme.CrimsonHeaderRed
import com.motogeek.telemetry.toSafeDouble
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BikeInfoScreen(viewModel: MainViewModel, navController: NavController) {
    val activeBike by viewModel.activeMotorcycle.collectAsState(initial = null)
    val allBikes by viewModel.repository.getAllMotorcycles().collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    var name by remember(activeBike) { mutableStateOf(activeBike?.name ?: "") }
    var type by remember(activeBike) { mutableStateOf(activeBike?.type ?: "") }
    var brand by remember(activeBike) { mutableStateOf(activeBike?.make ?: "") }
    var model by remember(activeBike) { mutableStateOf(activeBike?.model ?: "") }
    var startDateIso by remember(activeBike) { mutableStateOf(activeBike?.startDateIso ?: "") }
    var startOdo by remember(activeBike) { mutableStateOf(activeBike?.baseOdometerKm?.toString() ?: "") }
    var buyingPrice by remember(activeBike) { mutableStateOf(activeBike?.buyingPrice?.toString() ?: "") }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("Bike Information", color = Color.White) },
                actions = {
                    TextButton(onClick = {
                        activeBike?.let { bike ->
                            scope.launch {
                                viewModel.repository.updateMotorcycle(
                                    bike.copy(
                                        name = name,
                                        type = type,
                                        make = brand,
                                        model = model,
                                        startDateIso = startDateIso,
                                        baseOdometerKm = startOdo.toSafeDouble(),
                                        buyingPrice = buyingPrice.toSafeDouble()
                                    )
                                )
                                snackbarHostState.showSnackbar("Saved")
                            }
                        }
                    }) {
                        Text("SAVE", color = Color.White)
                    }
                    IconButton(onClick = { /* Menu */ }) {
                        Icon(Icons.Filled.MoreVert, contentDescription = "Menu", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = CrimsonHeaderRed)
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            ActiveBikeDropdownCard(
                activeBike = activeBike,
                allBikes = allBikes,
                onBikeSelected = { id ->
                    scope.launch { viewModel.repository.selectActiveMotorcycle(id) }
                },
                onAddNewBike = { navController.navigate("garage") },
                showOdometer = false
            )

            // Form Fields
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Name") },
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = type,
                onValueChange = { type = it },
                label = { Text("Type") },
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = brand,
                onValueChange = { brand = it },
                label = { Text("Brand") },
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = model,
                onValueChange = { model = it },
                label = { Text("Model") },
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = startDateIso,
                onValueChange = { startDateIso = it },
                label = { Text("Start Date") },
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = startOdo,
                onValueChange = { startOdo = it },
                label = { Text("Starting Odometer Reading") },
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = buyingPrice,
                onValueChange = { buyingPrice = it },
                label = { Text("Buying Price") },
                modifier = Modifier.fillMaxWidth()
            )

            // Current Odometer Reading Box
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Current Odometer Reading")
                    SegmentedDigitalText(text = "${activeBike?.totalOdometerKm?.toInt() ?: 0} km")
                    Text("Current Odometer Reading, update it regularly", color = Color.Gray, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

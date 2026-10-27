package com.motogeek.telemetry.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
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
import com.motogeek.telemetry.data.local.RideSessionEntity
import com.motogeek.telemetry.ui.components.ActiveBikeDropdownCard
import com.motogeek.telemetry.ui.theme.*
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BikeDataExportScreen(viewModel: MainViewModel, navController: NavController) {
    val activeBike by viewModel.activeMotorcycle.collectAsState()
    val allBikes by viewModel.allMotorcycles.collectAsState()
    val rides by viewModel.currentRideSessions.collectAsState()
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Digital Service Book & Export",
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
        ) {
            Spacer(modifier = Modifier.height(4.dp))

            ActiveBikeDropdownCard(
                activeBike = activeBike,
                allBikes = allBikes,
                onBikeSelected = { viewModel.selectActiveMotorcycle(it) },
                onAddNewBike = { navController.navigate("bike_info") },
                onSyncOdometer = { viewModel.updateActiveBikeOdometer(it) },
                showOdometer = true
            )

            if (activeBike == null) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No motorcycle selected.", color = Color.Gray)
                }
                return@Scaffold
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Primary Export Actions Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp),
                colors = CardDefaults.cardColors(containerColor = TftSurfaceDark),
                border = BorderStroke(1.dp, TitaniumBorder)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "OFFICIAL GARAGE EXPORTS",
                        color = Color(0xFF6B7A99),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        letterSpacing = 1.sp
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    // 1. Digital Service Book HTML
                    Button(
                        onClick = {
                            scope.launch {
                                val res = viewModel.importExportManager.shareBikeServiceBookHtml(activeBike!!.id)
                                if (res.isFailure) {
                                    snackbarHostState.showSnackbar("Export failed: ${res.exceptionOrNull()?.message}")
                                }
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = RacingAmberPrimary),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.MenuBook, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Share Digital Service Book (HTML Report)", fontWeight = FontWeight.Bold)
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // 2. All Garage Logs CSV
                    OutlinedButton(
                        onClick = {
                            scope.launch {
                                val res = viewModel.importExportManager.shareAllLogsCsv(activeBike!!.id)
                                if (res.isFailure) {
                                    snackbarHostState.showSnackbar("Export failed: ${res.exceptionOrNull()?.message}")
                                }
                            }
                        },
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, TitaniumBorder),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.TableChart, contentDescription = null, tint = TelemetryIceCyan, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Export Fuel, Parts & Service Logs (CSV)", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Section: Recorded Telemetry Rides Exporter
            Text(
                text = "RECORDED RIDE SESSIONS (${rides.size})",
                color = Color(0xFF6B7A99),
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                letterSpacing = 1.sp,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            if (rides.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(top = 20.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("No recorded rides yet. Record a ride in Live Cockpit first.", color = Color.Gray, fontSize = 13.sp)
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(bottom = 32.dp)
                ) {
                    items(rides, key = { it.rideId }) { ride ->
                        RideExportCard(
                            ride = ride,
                            onExportGpx = {
                                scope.launch {
                                    val res = viewModel.importExportManager.shareRideGpx(ride.rideId)
                                    if (res.isFailure) {
                                        snackbarHostState.showSnackbar("GPX export failed: ${res.exceptionOrNull()?.message}")
                                    }
                                }
                            },
                            onExportGoProCsv = {
                                scope.launch {
                                    val res = viewModel.importExportManager.shareRideGoProCsv(ride.rideId)
                                    if (res.isFailure) {
                                        snackbarHostState.showSnackbar("CSV export failed: ${res.exceptionOrNull()?.message}")
                                    }
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RideExportCard(
    ride: RideSessionEntity,
    onExportGpx: () -> Unit,
    onExportGoProCsv: () -> Unit
) {
    val dateStr = remember(ride.startTimeEpochMs) {
        val instant = Instant.ofEpochMilli(ride.startTimeEpochMs)
        DateTimeFormatter.ofPattern("MMM dd, yyyy HH:mm")
            .withZone(ZoneId.systemDefault())
            .format(instant)
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = TftSurfaceDark),
        border = BorderStroke(1.dp, TitaniumBorder)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = ride.name?.ifBlank { "Ride #${ride.rideId}" } ?: "Ride #${ride.rideId}",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                    Text(
                        text = "$dateStr • ${String.format("%.1f", ride.distanceKm)} km • ${ride.avgSpeedKmh.toInt()} km/h avg",
                        color = Color.Gray,
                        fontSize = 11.sp
                    )
                }

                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = TelemetryIceCyan.copy(alpha = 0.15f),
                    border = BorderStroke(1.dp, TelemetryIceCyan.copy(alpha = 0.5f))
                ) {
                    Text(
                        text = "${ride.maxLeanLeftDeg.toInt()}°L / ${ride.maxLeanRightDeg.toInt()}°R",
                        color = TelemetryIceCyan,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onExportGpx,
                    shape = RoundedCornerShape(6.dp),
                    border = BorderStroke(1.dp, TitaniumBorder),
                    modifier = Modifier.weight(1f).height(36.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp)
                ) {
                    Icon(Icons.Default.ShareLocation, contentDescription = null, tint = ApexEmeraldGreen, modifier = Modifier.size(15.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Export GPX", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }

                OutlinedButton(
                    onClick = onExportGoProCsv,
                    shape = RoundedCornerShape(6.dp),
                    border = BorderStroke(1.dp, TitaniumBorder),
                    modifier = Modifier.weight(1f).height(36.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp)
                ) {
                    Icon(Icons.Default.Videocam, contentDescription = null, tint = RacingAmberPrimary, modifier = Modifier.size(15.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("GoPro Overlay CSV", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

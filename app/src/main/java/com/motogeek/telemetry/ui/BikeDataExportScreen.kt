package com.motogeek.telemetry.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.MenuBook
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
import com.motogeek.telemetry.domain.maintenance.ImportExportManager
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
    val appLang = LocalAppLang.current

    var rideForGpxSave by remember { mutableStateOf<RideSessionEntity?>(null) }
    var rideForCsvSave by remember { mutableStateOf<RideSessionEntity?>(null) }

    val importGpxLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                val targetBikeId = activeBike?.id ?: allBikes.firstOrNull()?.id ?: ""
                val res = viewModel.importExportManager.importGpxFromUri(uri, targetBikeId)
                if (res.isSuccess) {
                    val imported = res.getOrNull()
                    val msg = if (imported != null) {
                        appLang.pick(
                            "Imported GPX: ${imported.name ?: "Ride #${imported.rideId}"}",
                            "GPX importované: ${imported.name ?: "Jazda #${imported.rideId}"}"
                        )
                    } else {
                        appLang.pick("GPX track imported successfully", "GPX stopa úspešne importovaná")
                    }
                    snackbarHostState.showSnackbar(msg)
                } else {
                    snackbarHostState.showSnackbar(
                        appLang.pick(
                            "GPX import failed: ${res.exceptionOrNull()?.message}",
                            "Import GPX zlyhal: ${res.exceptionOrNull()?.message}"
                        )
                    )
                }
            }
        }
    }

    val saveGpxLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/gpx+xml")
    ) { uri: Uri? ->
        val ride = rideForGpxSave
        if (uri != null && ride != null) {
            scope.launch {
                val res = viewModel.importExportManager.exportRideAsGpx(uri, ride.rideId)
                if (res.isSuccess) {
                    snackbarHostState.showSnackbar(
                        appLang.pick("GPX saved successfully", "GPX súbor bol úspešne uložený")
                    )
                } else {
                    snackbarHostState.showSnackbar(
                        appLang.pick(
                            "GPX save failed: ${res.exceptionOrNull()?.message}",
                            "Uloženie GPX zlyhalo: ${res.exceptionOrNull()?.message}"
                        )
                    )
                }
            }
        }
    }

    val saveCsvLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/csv")
    ) { uri: Uri? ->
        val ride = rideForCsvSave
        if (uri != null && ride != null) {
            scope.launch {
                val res = viewModel.importExportManager.exportRideAsCsv(uri, ride.rideId)
                if (res.isSuccess) {
                    snackbarHostState.showSnackbar(
                        appLang.pick("CSV saved successfully", "CSV súbor bol úspešne uložený")
                    )
                } else {
                    snackbarHostState.showSnackbar(
                        appLang.pick(
                            "CSV save failed: ${res.exceptionOrNull()?.message}",
                            "Uloženie CSV zlyhalo: ${res.exceptionOrNull()?.message}"
                        )
                    )
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
                        text = tr("Digital Service Book & Export", "Digitálna servisná knižka & export"),
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
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = tr("Back", "Späť"), tint = RacingAmberPrimary)
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
                onAddNewBike = { viewModel.startNewBikeTutorial() },
                onSyncOdometer = { viewModel.updateActiveBikeOdometer(it) },
                showOdometer = true
            )

            if (activeBike == null) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(tr("No motorcycle selected.", "Nie je vybraný motocykel."), color = Color.Gray)
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
                        text = tr("OFFICIAL GARAGE EXPORTS", "OFICIÁLNE EXPORTY GARÁŽE"),
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
                                    snackbarHostState.showSnackbar(
                                        appLang.pick(
                                            "Export failed: ${res.exceptionOrNull()?.message}",
                                            "Export zlyhal: ${res.exceptionOrNull()?.message}"
                                        )
                                    )
                                }
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = RacingAmberPrimary),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.AutoMirrored.Filled.MenuBook, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            tr("Share Digital Service Book (HTML Report)", "Zdieľať servisnú knižku (HTML report)"),
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // 2. All Garage Logs CSV
                    OutlinedButton(
                        onClick = {
                            scope.launch {
                                val res = viewModel.importExportManager.shareAllLogsCsv(activeBike!!.id)
                                if (res.isFailure) {
                                    snackbarHostState.showSnackbar(
                                        appLang.pick(
                                            "Export failed: ${res.exceptionOrNull()?.message}",
                                            "Export zlyhal: ${res.exceptionOrNull()?.message}"
                                        )
                                    )
                                }
                            }
                        },
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, TitaniumBorder),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.TableChart, contentDescription = null, tint = TelemetryIceCyan, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            tr("Export Fuel, Parts & Service Logs (CSV)", "Exportovať servis, diely & tankovanie (CSV)"),
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Section: Recorded Telemetry Rides Exporter
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = tr("RECORDED RIDE SESSIONS (${rides.size})", "ZÁZNAMY JÁZD (${rides.size})"),
                    color = Color(0xFF6B7A99),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    letterSpacing = 1.sp
                )
                OutlinedButton(
                    onClick = {
                        importGpxLauncher.launch(arrayOf("application/gpx+xml", "application/xml", "text/xml", "*/*"))
                    },
                    shape = RoundedCornerShape(6.dp),
                    border = BorderStroke(1.dp, TelemetryIceCyan.copy(alpha = 0.6f)),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                    modifier = Modifier.height(28.dp)
                ) {
                    Icon(Icons.Default.FileUpload, contentDescription = null, tint = TelemetryIceCyan, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(tr("Import .GPX", "Importovať .GPX"), color = TelemetryIceCyan, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }

            if (rides.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(top = 20.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            tr("No recorded rides yet. Record a ride in Live Cockpit first.", "Zatiaľ žiadne záznamy jázd. Najprv nahrajte jazdu v Kokpite."),
                            color = Color.Gray,
                            fontSize = 13.sp
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        OutlinedButton(
                            onClick = {
                                importGpxLauncher.launch(arrayOf("application/gpx+xml", "application/xml", "text/xml", "*/*"))
                            },
                            border = BorderStroke(1.dp, RacingAmberPrimary)
                        ) {
                            Icon(Icons.Default.FileUpload, contentDescription = null, tint = RacingAmberPrimary)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(tr("Import GPX Track", "Importovať GPX stopu"), color = RacingAmberPrimary)
                        }
                    }
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(bottom = 32.dp)
                ) {
                    items(rides, key = { it.rideId }) { ride ->
                        RideExportCard(
                            ride = ride,
                            onSaveGpx = {
                                rideForGpxSave = ride
                                saveGpxLauncher.launch("ride_${ride.rideId}.gpx")
                            },
                            onShareGpx = {
                                scope.launch {
                                    val res = viewModel.importExportManager.shareRideGpx(ride.rideId)
                                    if (res.isFailure) {
                                        snackbarHostState.showSnackbar(
                                            appLang.pick(
                                                "GPX share failed: ${res.exceptionOrNull()?.message}",
                                                "Zdieľanie GPX zlyhalo: ${res.exceptionOrNull()?.message}"
                                            )
                                        )
                                    }
                                }
                            },
                            onSaveCsv = {
                                rideForCsvSave = ride
                                saveCsvLauncher.launch("ride_${ride.rideId}.csv")
                            },
                            onShareCsv = {
                                scope.launch {
                                    val res = viewModel.importExportManager.shareRideGoProCsv(ride.rideId)
                                    if (res.isFailure) {
                                        snackbarHostState.showSnackbar(
                                            appLang.pick(
                                                "CSV share failed: ${res.exceptionOrNull()?.message}",
                                                "Zdieľanie CSV zlyhalo: ${res.exceptionOrNull()?.message}"
                                            )
                                        )
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
    onSaveGpx: () -> Unit,
    onShareGpx: () -> Unit,
    onSaveCsv: () -> Unit,
    onShareCsv: () -> Unit
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
                        text = "$dateStr • ${String.format("%.1f", ride.distanceKm)} km • ${ride.avgSpeedKmh.toInt()} km/h ${tr("avg", "priemer")}",
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
                    onClick = onSaveGpx,
                    shape = RoundedCornerShape(6.dp),
                    border = BorderStroke(1.dp, TitaniumBorder),
                    modifier = Modifier.weight(1f).height(36.dp),
                    contentPadding = PaddingValues(horizontal = 6.dp)
                ) {
                    Icon(Icons.Default.Download, contentDescription = null, tint = ApexEmeraldGreen, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(tr("Save GPX", "Uložiť GPX"), color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }

                OutlinedButton(
                    onClick = onShareGpx,
                    shape = RoundedCornerShape(6.dp),
                    border = BorderStroke(1.dp, TitaniumBorder),
                    modifier = Modifier.weight(1f).height(36.dp),
                    contentPadding = PaddingValues(horizontal = 6.dp)
                ) {
                    Icon(Icons.Default.ShareLocation, contentDescription = null, tint = ApexEmeraldGreen, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(tr("Share GPX", "Zdieľať GPX"), color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onSaveCsv,
                    shape = RoundedCornerShape(6.dp),
                    border = BorderStroke(1.dp, TitaniumBorder),
                    modifier = Modifier.weight(1f).height(36.dp),
                    contentPadding = PaddingValues(horizontal = 6.dp)
                ) {
                    Icon(Icons.Default.Download, contentDescription = null, tint = RacingAmberPrimary, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(tr("Save CSV", "Uložiť CSV"), color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }

                OutlinedButton(
                    onClick = onShareCsv,
                    shape = RoundedCornerShape(6.dp),
                    border = BorderStroke(1.dp, TitaniumBorder),
                    modifier = Modifier.weight(1f).height(36.dp),
                    contentPadding = PaddingValues(horizontal = 6.dp)
                ) {
                    Icon(Icons.Default.Videocam, contentDescription = null, tint = RacingAmberPrimary, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(tr("Share CSV", "Zdieľať CSV"), color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

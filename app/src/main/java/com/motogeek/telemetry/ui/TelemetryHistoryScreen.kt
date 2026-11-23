package com.motogeek.telemetry.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.motogeek.telemetry.data.local.CornerEventEntity
import com.motogeek.telemetry.data.local.RideSessionEntity
import com.motogeek.telemetry.data.local.TelemetryTrackPointEntity
import com.motogeek.telemetry.domain.maintenance.ImportExportManager
import com.motogeek.telemetry.ui.components.RideSocialCardGenerator
import com.motogeek.telemetry.ui.components.RideTrajectoryHeatmapCanvas
import com.motogeek.telemetry.ui.components.SynchronizedTelemetryChart
import com.motogeek.telemetry.ui.theme.*
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TelemetryHistoryScreen(
    viewModel: MainViewModel,
    initialRideId: Long? = null,
    onBack: (() -> Unit)? = null
) {
    val rides by viewModel.allRides.collectAsState(initial = emptyList())
    val activeBike by viewModel.activeMotorcycle.collectAsState()
    val allBikes by viewModel.allMotorcycles.collectAsState()

    var selectedRide by remember { mutableStateOf<RideSessionEntity?>(null) }
    var filterActiveBikeOnly by remember { mutableStateOf(false) }
    var selectedStyle by remember { mutableStateOf("All") }
    var searchQuery by remember { mutableStateOf("") }
    var scrubbedTimestampMs by remember { mutableStateOf<Long?>(null) }
    var cornerDirectionFilter by remember { mutableStateOf("ALL") }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showEditDialog by remember { mutableStateOf(false) }

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val appLang = LocalAppLang.current

    // Auto-select initialRideId when rides load
    LaunchedEffect(rides, initialRideId) {
        if (initialRideId != null && selectedRide == null && rides.isNotEmpty()) {
            selectedRide = rides.find { it.rideId == initialRideId }
        }
    }

    // Intercept back button when in detail view
    BackHandler(enabled = selectedRide != null) {
        selectedRide = null
        scrubbedTimestampMs = null
    }

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
                    if (imported != null) {
                        selectedRide = imported
                    }
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
        val ride = selectedRide
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
        val ride = selectedRide
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

    if (showDeleteConfirm && selectedRide != null) {
        val ride = selectedRide!!
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text(tr("Delete Ride", "Vymazať jazdu")) },
            text = { Text(tr("Are you sure you want to delete this ride? This cannot be undone.", "Naozaj chcete vymazať túto jazdu? Túto akciu nemožno vrátiť späť.")) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteRideSession(ride.rideId)
                    showDeleteConfirm = false
                    selectedRide = null
                    scrubbedTimestampMs = null
                }) {
                    Text(tr("Delete", "Vymazať"), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text(tr("Cancel", "Zrušiť"))
                }
            }
        )
    }

    if (showEditDialog && selectedRide != null) {
        val ride = selectedRide!!
        var editName by remember(ride.rideId) { mutableStateOf(ride.name ?: "") }
        var editNotes by remember(ride.rideId) { mutableStateOf(ride.notes ?: "") }

        AlertDialog(
            onDismissRequest = { showEditDialog = false },
            title = { Text(tr("Edit Ride Details", "Upraviť detail jazdy")) },
            text = {
                Column {
                    OutlinedTextField(
                        value = editName,
                        onValueChange = { editName = it },
                        label = { Text(tr("Ride Name", "Názov jazdy")) },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = editNotes,
                        onValueChange = { editNotes = it },
                        label = { Text(tr("Notes", "Poznámky")) },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 3
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.updateRideSessionDetails(
                        ride.rideId,
                        editName.takeIf { it.isNotBlank() },
                        editNotes.takeIf { it.isNotBlank() }
                    )
                    selectedRide = ride.copy(
                        name = editName.takeIf { it.isNotBlank() },
                        notes = editNotes.takeIf { it.isNotBlank() }
                    )
                    showEditDialog = false
                }) {
                    Text(tr("Save", "Uložiť"))
                }
            },
            dismissButton = {
                TextButton(onClick = { showEditDialog = false }) {
                    Text(tr("Cancel", "Zrušiť"))
                }
            }
        )
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = CarbonObsidianBg,
        topBar = {
            if (selectedRide == null) {
                TopAppBar(
                    title = {
                        Text(
                            text = tr("Ride History", "História jázd"),
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            fontSize = 18.sp
                        )
                    },
                    navigationIcon = {
                        if (onBack != null) {
                            IconButton(onClick = onBack) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = tr("Back", "Späť"), tint = RacingAmberPrimary)
                            }
                        }
                    },
                    actions = {
                        TextButton(onClick = { viewModel.toggleAppLanguage() }) {
                            Text(
                                text = if (appLang == AppLang.EN) "EN ▸ SK" else "SK ▸ EN",
                                color = TelemetryIceCyan,
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp
                            )
                        }
                        TextButton(
                            onClick = {
                                importGpxLauncher.launch(arrayOf("application/gpx+xml", "application/xml", "text/xml", "*/*"))
                            }
                        ) {
                            Icon(Icons.Filled.FileUpload, contentDescription = null, tint = RacingAmberPrimary, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(tr("Import GPX", "Import GPX"), color = RacingAmberPrimary, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = CarbonObsidianBg)
                )
            } else {
                val ride = selectedRide!!
                TopAppBar(
                    title = {
                        Text(
                            text = ride.name ?: tr("Ride Details", "Detail jazdy"),
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = {
                            selectedRide = null
                            scrubbedTimestampMs = null
                        }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = tr("Back", "Späť"), tint = RacingAmberPrimary)
                        }
                    },
                    actions = {
                        IconButton(onClick = { showEditDialog = true }) {
                            Icon(Icons.Filled.Edit, contentDescription = tr("Edit", "Upraviť"), tint = Color.White)
                        }
                        IconButton(onClick = { showDeleteConfirm = true }) {
                            Icon(Icons.Filled.Delete, contentDescription = tr("Delete", "Vymazať"), tint = RedlineCrimson)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = CarbonObsidianBg)
                )
            }
        }
    ) { paddingValues ->
        if (selectedRide == null) {
            // LIST VIEW
            val displayedRides = remember(rides, activeBike, filterActiveBikeOnly, selectedStyle, searchQuery) {
                rides.filter { ride ->
                    val matchesBike = if (filterActiveBikeOnly && activeBike != null) {
                        ride.motorcycleId == activeBike!!.id
                    } else true

                    val matchesStyle = if (selectedStyle == "All") true else {
                        ride.ridingStyleBadge.contains(selectedStyle, ignoreCase = true)
                    }

                    val matchesQuery = if (searchQuery.isBlank()) true else {
                        (ride.name?.contains(searchQuery, ignoreCase = true) == true) ||
                        (ride.notes?.contains(searchQuery, ignoreCase = true) == true)
                    }

                    matchesBike && matchesStyle && matchesQuery
                }
            }

            val totalKm = displayedRides.sumOf { it.distanceKm }
            val peakLean = displayedRides.maxOfOrNull { maxOf(it.maxLeanLeftDeg, it.maxLeanRightDeg) } ?: 0.0
            val totalCorners = displayedRides.sumOf { it.totalCornersCount }

            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 80.dp, top = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // KPI Strip
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = TftSurfaceDark),
                        border = BorderStroke(1.dp, TitaniumBorder)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceAround,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            SummaryKpiItem(
                                label = tr("RIDES", "JAZDY"),
                                value = "${displayedRides.size}",
                                color = Color.White
                            )
                            SummaryKpiItem(
                                label = tr("TOTAL DIST", "CELKOVO KM"),
                                value = String.format(Locale.US, "%.0f km", totalKm),
                                color = RacingAmberPrimary
                            )
                            SummaryKpiItem(
                                label = tr("PEAK LEAN", "MAX NÁKLON"),
                                value = "${peakLean.toInt()}°",
                                color = TelemetryIceCyan
                            )
                            SummaryKpiItem(
                                label = tr("CORNERS", "ZÁKRUTY"),
                                value = "$totalCorners",
                                color = ApexEmeraldGreen
                            )
                        }
                    }
                }

                // Search box
                item {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp),
                        placeholder = {
                            Text(
                                tr("Search by ride name or notes…", "Hľadať podľa názvu alebo poznámok…"),
                                fontSize = 13.sp,
                                color = Color.Gray
                            )
                        },
                        leadingIcon = {
                            Icon(Icons.Default.Search, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(18.dp))
                        },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { searchQuery = "" }) {
                                    Icon(Icons.Default.Close, contentDescription = tr("Clear", "Vymazať"), tint = Color.Gray, modifier = Modifier.size(16.dp))
                                }
                            }
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(10.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = TftSurfaceDark,
                            unfocusedContainerColor = TftSurfaceDark,
                            focusedBorderColor = RacingAmberPrimary,
                            unfocusedBorderColor = TitaniumBorder
                        )
                    )
                }

                // Filter chips: Styles and Bikes
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val styles = listOf("All", "Track", "Canyon", "Urban")
                        styles.forEach { style ->
                            val label = when (style) {
                                "All" -> tr("All", "Všetky")
                                "Track" -> tr("Track", "Okruh")
                                "Canyon" -> tr("Canyon", "Priesmyk")
                                "Urban" -> tr("Urban", "Mesto")
                                else -> style
                            }
                            FilterChip(
                                selected = selectedStyle == style,
                                onClick = { selectedStyle = style },
                                label = { Text(label, fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = RacingAmberPrimary.copy(alpha = 0.2f),
                                    selectedLabelColor = RacingAmberPrimary
                                ),
                                border = FilterChipDefaults.filterChipBorder(
                                    borderColor = if (selectedStyle == style) RacingAmberPrimary else TitaniumBorder,
                                    selectedBorderColor = RacingAmberPrimary,
                                    enabled = true,
                                    selected = selectedStyle == style
                                )
                            )
                        }

                        if (activeBike != null) {
                            FilterChip(
                                selected = filterActiveBikeOnly,
                                onClick = { filterActiveBikeOnly = !filterActiveBikeOnly },
                                label = {
                                    Text(
                                        if (filterActiveBikeOnly) activeBike!!.displayName else tr("All Bikes", "Všetky motorky"),
                                        fontSize = 11.sp
                                    )
                                },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = TelemetryIceCyan.copy(alpha = 0.2f),
                                    selectedLabelColor = TelemetryIceCyan
                                ),
                                border = FilterChipDefaults.filterChipBorder(
                                    borderColor = if (filterActiveBikeOnly) TelemetryIceCyan else TitaniumBorder,
                                    selectedBorderColor = TelemetryIceCyan,
                                    enabled = true,
                                    selected = filterActiveBikeOnly
                                )
                            )
                        }
                    }
                }

                // Empty state or ride cards
                if (displayedRides.isEmpty()) {
                    item {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 24.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = TftSurfaceDark),
                            border = BorderStroke(1.dp, TitaniumBorder)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(32.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Icon(
                                    Icons.Default.Route,
                                    contentDescription = null,
                                    tint = Color.Gray,
                                    modifier = Modifier.size(48.dp)
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                Text(
                                    tr("No rides found", "Nenašli sa žiadne jazdy"),
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    tr(
                                        "Record telemetry in Live Cockpit or import a .GPX track.",
                                        "Nahrajte telemetriu v Kokpite alebo importujte .GPX stopu."
                                    ),
                                    color = Color.Gray,
                                    fontSize = 12.sp,
                                    textAlign = TextAlign.Center
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                Button(
                                    onClick = {
                                        importGpxLauncher.launch(arrayOf("application/gpx+xml", "application/xml", "text/xml", "*/*"))
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = RacingAmberPrimary),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Icon(Icons.Filled.FileUpload, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(tr("Import GPX File", "Importovať GPX súbor"), fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                } else {
                    items(displayedRides, key = { it.rideId }) { ride ->
                        val sdf = remember { SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault()) }
                        val dateStr = remember(ride.startTimeEpochMs) { sdf.format(Date(ride.startTimeEpochMs)) }
                        val bikeName = remember(ride.motorcycleId, allBikes, appLang) {
                            allBikes.find { it.id == ride.motorcycleId }?.displayName ?: appLang.pick("Motorcycle", "Motocykel")
                        }
                        val durationSeconds = remember(ride.startTimeEpochMs, ride.endTimeEpochMs) {
                            ((ride.endTimeEpochMs - ride.startTimeEpochMs) / 1000).coerceAtLeast(0L)
                        }

                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { selectedRide = ride },
                            shape = RoundedCornerShape(10.dp),
                            colors = CardDefaults.cardColors(containerColor = TftSurfaceDark),
                            border = BorderStroke(1.dp, TitaniumBorder)
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                // Title row
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = ride.name?.ifBlank { dateStr } ?: dateStr,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White,
                                            fontSize = 15.sp,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = "$bikeName • $dateStr",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = Color.Gray,
                                            fontSize = 11.sp
                                        )
                                    }

                                    Surface(
                                        shape = RoundedCornerShape(4.dp),
                                        color = RacingAmberPrimary.copy(alpha = 0.15f),
                                        border = BorderStroke(1.dp, RacingAmberPrimary.copy(alpha = 0.4f))
                                    ) {
                                        Text(
                                            text = ride.ridingStyleBadge,
                                            color = RacingAmberPrimary,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(10.dp))

                                // Metrics grid
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    RideMetricMini(
                                        label = tr("DIST", "VZDIAL"),
                                        value = String.format(Locale.US, "%.1f km", ride.distanceKm)
                                    )
                                    RideMetricMini(
                                        label = tr("TIME", "ČAS"),
                                        value = formatRideDuration(durationSeconds)
                                    )
                                    RideMetricMini(
                                        label = tr("AVG/MAX", "PR/MAX"),
                                        value = "${ride.avgSpeedKmh.toInt()}/${ride.maxSpeedKmh.toInt()} km/h"
                                    )
                                    RideMetricMini(
                                        label = tr("MAX LEAN", "NÁKLON"),
                                        value = "${ride.maxLeanLeftDeg.toInt()}°L | ${ride.maxLeanRightDeg.toInt()}°R",
                                        highlightColor = TelemetryIceCyan
                                    )
                                }

                                Spacer(modifier = Modifier.height(8.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    RideMetricMini(
                                        label = tr("CORNERS", "ZÁKRUTY"),
                                        value = "${ride.totalCornersCount}"
                                    )
                                    RideMetricMini(
                                        label = tr("SCORE", "SKÓRE"),
                                        value = "${ride.avgCornerScore.toInt()}",
                                        highlightColor = ApexEmeraldGreen
                                    )
                                    RideMetricMini(
                                        label = tr("ELEVATION", "PREVÝŠENIE"),
                                        value = "+${ride.elevationGainMeters.toInt()} m"
                                    )
                                    RideMetricMini(
                                        label = tr("LAT G", "BOČNÉ G"),
                                        value = String.format(Locale.US, "%.2fg", ride.maxLateralG)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        } else {
            // DETAIL VIEW
            val ride = selectedRide!!
            val trackPoints by viewModel.getTelemetryTrackPointsForRide(ride.rideId).collectAsState(initial = emptyList())
            val cornerEvents by viewModel.getCornerEventsForRide(ride.rideId).collectAsState(initial = emptyList())

            val displayedCornerEvents = remember(cornerEvents, cornerDirectionFilter) {
                when (cornerDirectionFilter) {
                    "LEFT" -> cornerEvents.filter { it.direction.contains("Left", ignoreCase = true) }
                    "RIGHT" -> cornerEvents.filter { it.direction.contains("Right", ignoreCase = true) }
                    else -> cornerEvents
                }
            }

            val durationSeconds = remember(ride.startTimeEpochMs, ride.endTimeEpochMs) {
                ((ride.endTimeEpochMs - ride.startTimeEpochMs) / 1000).coerceAtLeast(0L)
            }
            val bikeDisplayName = remember(ride.motorcycleId, allBikes, appLang) {
                allBikes.find { it.id == ride.motorcycleId }?.displayName ?: appLang.pick("Motorcycle", "Motocykel")
            }

            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 48.dp, top = 8.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Action Strip
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        colors = CardDefaults.cardColors(containerColor = TftSurfaceDark),
                        border = BorderStroke(1.dp, TitaniumBorder)
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Text(
                                text = tr("EXPORT & SHARE", "EXPORT A ZDIEĽANIE"),
                                color = Color(0xFF6B7A99),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace,
                                letterSpacing = 1.sp,
                                modifier = Modifier.padding(bottom = 8.dp)
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                OutlinedButton(
                                    onClick = { saveGpxLauncher.launch("ride_${ride.rideId}.gpx") },
                                    shape = RoundedCornerShape(6.dp),
                                    border = BorderStroke(1.dp, TitaniumBorder),
                                    modifier = Modifier.weight(1f).height(34.dp),
                                    contentPadding = PaddingValues(horizontal = 4.dp)
                                ) {
                                    Icon(Icons.Default.Download, contentDescription = null, tint = ApexEmeraldGreen, modifier = Modifier.size(13.dp))
                                    Spacer(modifier = Modifier.width(3.dp))
                                    Text(tr("Save GPX", "Uložiť GPX"), color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                }

                                OutlinedButton(
                                    onClick = {
                                        scope.launch {
                                            val res = viewModel.importExportManager.shareRideGpx(ride.rideId)
                                            if (res.isFailure) {
                                                snackbarHostState.showSnackbar(
                                                    appLang.pick("GPX share failed: ${res.exceptionOrNull()?.message}", "Zdieľanie GPX zlyhalo: ${res.exceptionOrNull()?.message}")
                                                )
                                            }
                                        }
                                    },
                                    shape = RoundedCornerShape(6.dp),
                                    border = BorderStroke(1.dp, TitaniumBorder),
                                    modifier = Modifier.weight(1f).height(34.dp),
                                    contentPadding = PaddingValues(horizontal = 4.dp)
                                ) {
                                    Icon(Icons.Default.ShareLocation, contentDescription = null, tint = ApexEmeraldGreen, modifier = Modifier.size(13.dp))
                                    Spacer(modifier = Modifier.width(3.dp))
                                    Text(tr("Share GPX", "Zdieľať GPX"), color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                }

                                OutlinedButton(
                                    onClick = { saveCsvLauncher.launch("ride_${ride.rideId}.csv") },
                                    shape = RoundedCornerShape(6.dp),
                                    border = BorderStroke(1.dp, TitaniumBorder),
                                    modifier = Modifier.weight(1f).height(34.dp),
                                    contentPadding = PaddingValues(horizontal = 4.dp)
                                ) {
                                    Icon(Icons.Default.Download, contentDescription = null, tint = RacingAmberPrimary, modifier = Modifier.size(13.dp))
                                    Spacer(modifier = Modifier.width(3.dp))
                                    Text(tr("Save CSV", "Uložiť CSV"), color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                }
                            }

                            Spacer(modifier = Modifier.height(6.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                OutlinedButton(
                                    onClick = {
                                        scope.launch {
                                            val res = viewModel.importExportManager.shareRideGoProCsv(ride.rideId)
                                            if (res.isFailure) {
                                                snackbarHostState.showSnackbar(
                                                    appLang.pick("CSV share failed: ${res.exceptionOrNull()?.message}", "Zdieľanie CSV zlyhalo: ${res.exceptionOrNull()?.message}")
                                                )
                                            }
                                        }
                                    },
                                    shape = RoundedCornerShape(6.dp),
                                    border = BorderStroke(1.dp, TitaniumBorder),
                                    modifier = Modifier.weight(1f).height(34.dp),
                                    contentPadding = PaddingValues(horizontal = 4.dp)
                                ) {
                                    Icon(Icons.Default.Videocam, contentDescription = null, tint = RacingAmberPrimary, modifier = Modifier.size(13.dp))
                                    Spacer(modifier = Modifier.width(3.dp))
                                    Text(tr("Share CSV", "Zdieľať CSV"), color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                }

                                Button(
                                    onClick = {
                                        scope.launch {
                                            try {
                                                val file = RideSocialCardGenerator.generateAndSaveCard(context, ride, trackPoints, bikeDisplayName, appLang)
                                                val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                                                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                                    type = "image/png"
                                                    putExtra(Intent.EXTRA_STREAM, uri)
                                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                                }
                                                context.startActivity(Intent.createChooser(shareIntent, appLang.pick("Share Ride Card", "Zdieľať kartu jazdy")))
                                            } catch (e: Exception) {
                                                snackbarHostState.showSnackbar(
                                                    appLang.pick("Card generation failed: ${e.message}", "Vytvorenie karty zlyhalo: ${e.message}")
                                                )
                                            }
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = RacingAmberPrimary),
                                    shape = RoundedCornerShape(6.dp),
                                    modifier = Modifier.weight(1f).height(34.dp),
                                    contentPadding = PaddingValues(horizontal = 4.dp)
                                ) {
                                    Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(13.dp))
                                    Spacer(modifier = Modifier.width(3.dp))
                                    Text(tr("Social Card", "Karta jazdy"), fontWeight = FontWeight.Bold, fontSize = 10.sp)
                                }
                            }
                        }
                    }
                }

                // Notes (if any)
                if (!ride.notes.isNullOrBlank()) {
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(8.dp),
                            colors = CardDefaults.cardColors(containerColor = TftSurfaceDark),
                            border = BorderStroke(1.dp, TitaniumBorder)
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(
                                    text = tr("NOTES", "POZNÁMKY"),
                                    color = Color.Gray,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = ride.notes,
                                    color = Color.White,
                                    fontSize = 13.sp
                                )
                            }
                        }
                    }
                }

                // Trajectory Heatmap
                item {
                    Column {
                        Text(
                            tr("Trajectory Heatmap", "Tepelná mapa trajektórie"),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        RideTrajectoryHeatmapCanvas(
                            trackPoints = trackPoints,
                            cornerEvents = displayedCornerEvents,
                            selectedTimestampMs = scrubbedTimestampMs,
                            onPointSelected = { scrubbedTimestampMs = it },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }

                // Synchronized Telemetry Chart
                item {
                    Column {
                        Text(
                            tr("Telemetry Analytics", "Analýza telemetrie"),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        SynchronizedTelemetryChart(
                            trackPoints = trackPoints,
                            onScrub = { timestamp -> scrubbedTimestampMs = timestamp },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }

                // Full Telemetry Cards Grid
                item {
                    Text(
                        tr("Ride Performance Metrics", "Výkonnostné metriky jazdy"),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        colors = CardDefaults.cardColors(containerColor = TftSurfaceDark),
                        border = BorderStroke(1.dp, TitaniumBorder)
                    ) {
                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                DetailMetricTile(
                                    label = tr("SPEED (AVG / MAX)", "RÝCHLOSŤ (PR / MAX)"),
                                    value = "${ride.avgSpeedKmh.toInt()} / ${ride.maxSpeedKmh.toInt()} km/h",
                                    color = Color.White
                                )
                                DetailMetricTile(
                                    label = tr("DURATION", "TRVANIE"),
                                    value = formatRideDuration(durationSeconds),
                                    color = Color.White
                                )
                                DetailMetricTile(
                                    label = tr("ELEVATION GAIN", "PREVÝŠENIE"),
                                    value = "+${ride.elevationGainMeters.toInt()} m",
                                    color = TelemetryIceCyan
                                )
                            }

                            HorizontalDivider(color = TitaniumBorder, thickness = 0.5.dp)

                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                DetailMetricTile(
                                    label = tr("LATERAL G", "BOČNÉ PREŤAŽENIE"),
                                    value = String.format(Locale.US, "%.2fg", ride.maxLateralG),
                                    color = RacingAmberPrimary
                                )
                                DetailMetricTile(
                                    label = tr("BRAKING G", "BRZDNÉ PREŤAŽENIE"),
                                    value = String.format(Locale.US, "%.2fg", ride.maxBrakingG),
                                    color = RedlineCrimson
                                )
                                DetailMetricTile(
                                    label = tr("ACCEL G", "ZRÝCHLENIE"),
                                    value = String.format(Locale.US, "%.2fg", ride.maxAccelG),
                                    color = ApexEmeraldGreen
                                )
                            }

                            HorizontalDivider(color = TitaniumBorder, thickness = 0.5.dp)

                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                DetailMetricTile(
                                    label = tr("TURN-IN RATE", "RÝCHLOSŤ KLOPENIA"),
                                    value = String.format(Locale.US, "%.1f°/s", ride.maxTurnInRateDegPerSec),
                                    color = TelemetryIceCyan
                                )
                                DetailMetricTile(
                                    label = tr("TRAIL BRAKING", "TRAIL BRAKING"),
                                    value = "${ride.trailBrakingScorePct.toInt()}%",
                                    color = WarningGold
                                )
                                DetailMetricTile(
                                    label = tr("CORNER SCORE", "SKÓRE ZÁKRUT"),
                                    value = "${ride.avgCornerScore.toInt()}",
                                    color = ApexEmeraldGreen
                                )
                            }

                            HorizontalDivider(color = TitaniumBorder, thickness = 0.5.dp)

                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                DetailMetricTile(
                                    label = tr("GPS SAMPLES", "VZORKY GPS"),
                                    value = "${trackPoints.size}",
                                    color = Color.LightGray
                                )
                                DetailMetricTile(
                                    label = tr("TOTAL CORNERS", "CELKOVO ZÁKRUT"),
                                    value = "${ride.totalCornersCount} (${ride.leftCornersCount}L / ${ride.rightCornersCount}R)",
                                    color = Color.White
                                )
                                DetailMetricTile(
                                    label = tr("STYLE BADGE", "ŠTÝL JAZDY"),
                                    value = ride.ridingStyleBadge,
                                    color = RacingAmberPrimary
                                )
                            }
                        }
                    }
                }

                // Top Corners Podium
                item {
                    Text(tr("Top Corners Podium", "Pódium najlepších zákrut"), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Color.White)
                    Spacer(modifier = Modifier.height(8.dp))

                    if (cornerEvents.isNotEmpty()) {
                        val topCorners = cornerEvents.sortedWith(
                            compareByDescending<CornerEventEntity> { it.cornerScore }
                                .thenByDescending { it.apexLeanDeg }
                        ).take(3)

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                            verticalAlignment = Alignment.Bottom
                        ) {
                            if (topCorners.size > 1) {
                                PodiumCard(
                                    corner = topCorners[1],
                                    rank = 2,
                                    color = Color(0xFFC0C0C0),
                                    height = 120.dp,
                                    onClick = { scrubbedTimestampMs = topCorners[1].timestampMs }
                                )
                            }
                            if (topCorners.isNotEmpty()) {
                                PodiumCard(
                                    corner = topCorners[0],
                                    rank = 1,
                                    color = Color(0xFFFFD700),
                                    height = 140.dp,
                                    onClick = { scrubbedTimestampMs = topCorners[0].timestampMs }
                                )
                            }
                            if (topCorners.size > 2) {
                                PodiumCard(
                                    corner = topCorners[2],
                                    rank = 3,
                                    color = Color(0xFFCD7F32),
                                    height = 100.dp,
                                    onClick = { scrubbedTimestampMs = topCorners[2].timestampMs }
                                )
                            }
                        }
                    } else {
                        Text(tr("No corner data recorded for this ride.", "Pre túto jazdu neboli zaznamenané žiadne zákruty."), style = MaterialTheme.typography.bodyMedium, color = Color.Gray)
                    }
                }

                // Corner Inspector with Left/Right filter & Scrub
                item {
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                tr("Corner Inspector (Tap to scrub)", "Inšpektor zákrut (kliknutím preskúmať)"),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )

                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                val leftCount = cornerEvents.count { it.direction.contains("Left", ignoreCase = true) }
                                val rightCount = cornerEvents.count { it.direction.contains("Right", ignoreCase = true) }

                                FilterChip(
                                    selected = cornerDirectionFilter == "ALL",
                                    onClick = { cornerDirectionFilter = "ALL" },
                                    label = { Text(tr("All (${cornerEvents.size})", "Všetky (${cornerEvents.size})"), fontSize = 10.sp) },
                                    modifier = Modifier.height(28.dp)
                                )
                                FilterChip(
                                    selected = cornerDirectionFilter == "LEFT",
                                    onClick = { cornerDirectionFilter = "LEFT" },
                                    label = { Text(tr("Left ($leftCount)", "Ľavé ($leftCount)"), fontSize = 10.sp) },
                                    modifier = Modifier.height(28.dp)
                                )
                                FilterChip(
                                    selected = cornerDirectionFilter == "RIGHT",
                                    onClick = { cornerDirectionFilter = "RIGHT" },
                                    label = { Text(tr("Right ($rightCount)", "Pravé ($rightCount)"), fontSize = 10.sp) },
                                    modifier = Modifier.height(28.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        if (displayedCornerEvents.isNotEmpty()) {
                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                contentPadding = PaddingValues(vertical = 4.dp)
                            ) {
                                items(displayedCornerEvents, key = { it.cornerId }) { corner ->
                                    val isSelected = scrubbedTimestampMs == corner.timestampMs
                                    Card(
                                        modifier = Modifier
                                            .width(130.dp)
                                            .clickable { scrubbedTimestampMs = corner.timestampMs },
                                        shape = RoundedCornerShape(8.dp),
                                        colors = CardDefaults.cardColors(
                                            containerColor = if (isSelected) RacingAmberPrimary.copy(alpha = 0.2f) else TftSurfaceDark
                                        ),
                                        border = BorderStroke(
                                            1.dp,
                                            if (isSelected) RacingAmberPrimary else TitaniumBorder
                                        )
                                    ) {
                                        Column(modifier = Modifier.padding(8.dp)) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween
                                            ) {
                                                Text(
                                                    "T${corner.sequenceNumber}",
                                                    fontWeight = FontWeight.Bold,
                                                    color = Color.White,
                                                    fontSize = 12.sp
                                                )
                                                Text(
                                                    corner.direction.take(1).uppercase(),
                                                    color = if (corner.direction.contains("Left", ignoreCase = true)) TelemetryIceCyan else WarningGold,
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 12.sp
                                                )
                                            }
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Text(
                                                "${String.format(Locale.US, "%.1f", corner.apexLeanDeg)}° ${tr("lean", "náklon")}",
                                                color = Color.LightGray,
                                                fontSize = 11.sp
                                            )
                                            Text(
                                                "${corner.entrySpeedKmh.toInt()} km/h",
                                                color = Color.Gray,
                                                fontSize = 10.sp
                                            )
                                            Text(
                                                "${tr("Score", "Skóre")}: ${corner.cornerScore.toInt()}",
                                                color = ApexEmeraldGreen,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 11.sp
                                            )
                                        }
                                    }
                                }
                            }
                        } else {
                            Text(
                                tr("No corners in this filter.", "Žiadne zákruty v tomto filtri."),
                                color = Color.Gray,
                                fontSize = 12.sp
                            )
                        }
                    }
                }

                // Asymmetry Analyzer
                item {
                    Text(tr("Asymmetry Analyzer", "Analyzátor asymetrie"), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Color.White)
                    Spacer(modifier = Modifier.height(8.dp))

                    val leftCorners = cornerEvents.filter { it.direction.contains("Left", ignoreCase = true) }
                    val rightCorners = cornerEvents.filter { it.direction.contains("Right", ignoreCase = true) }

                    val avgLeftSpeed = if (leftCorners.isNotEmpty()) leftCorners.map { it.entrySpeedKmh }.average() else 0.0
                    val avgRightSpeed = if (rightCorners.isNotEmpty()) rightCorners.map { it.entrySpeedKmh }.average() else 0.0

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        colors = CardDefaults.cardColors(containerColor = TftSurfaceDark),
                        border = BorderStroke(1.dp, TitaniumBorder)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Column(horizontalAlignment = Alignment.Start) {
                                    Text(tr("LEFT", "VĽAVO"), fontWeight = FontWeight.Bold, color = TelemetryIceCyan)
                                    Text("${tr("Avg Entry", "Priem. nájazd")}: ${String.format(Locale.US, "%.1f", avgLeftSpeed)} km/h", color = Color.LightGray, fontSize = 12.sp)
                                    Text("${tr("Max Lean", "Max náklon")}: ${String.format(Locale.US, "%.1f", ride.maxLeanLeftDeg)}°", color = Color.White, fontSize = 12.sp)
                                }
                                Column(horizontalAlignment = Alignment.End) {
                                    Text(tr("RIGHT", "VPRAVO"), fontWeight = FontWeight.Bold, color = WarningGold)
                                    Text("${tr("Avg Entry", "Priem. nájazd")}: ${String.format(Locale.US, "%.1f", avgRightSpeed)} km/h", color = Color.LightGray, fontSize = 12.sp)
                                    Text("${tr("Max Lean", "Max náklon")}: ${String.format(Locale.US, "%.1f", ride.maxLeanRightDeg)}°", color = Color.White, fontSize = 12.sp)
                                }
                            }

                            Spacer(modifier = Modifier.height(16.dp))

                            // Tire Cross-Section Canvas
                            Canvas(modifier = Modifier.fillMaxWidth().height(150.dp)) {
                                val width = size.width
                                val height = size.height
                                val radius = height * 0.9f
                                val center = Offset(width / 2, height)

                                // Base Tire
                                drawArc(
                                    color = Color.DarkGray,
                                    startAngle = 180f,
                                    sweepAngle = 180f,
                                    useCenter = false,
                                    topLeft = Offset(center.x - radius, center.y - radius),
                                    size = Size(radius * 2, radius * 2),
                                    style = Stroke(width = 40f, cap = StrokeCap.Round)
                                )

                                // Used Tire (Left)
                                val leftSweep = (ride.maxLeanLeftDeg.toFloat() / 60f).coerceIn(0f, 1f) * 90f
                                drawArc(
                                    color = TelemetryIceCyan,
                                    startAngle = 270f - leftSweep,
                                    sweepAngle = leftSweep,
                                    useCenter = false,
                                    topLeft = Offset(center.x - radius, center.y - radius),
                                    size = Size(radius * 2, radius * 2),
                                    style = Stroke(width = 40f)
                                )

                                // Used Tire (Right)
                                val rightSweep = (ride.maxLeanRightDeg.toFloat() / 60f).coerceIn(0f, 1f) * 90f
                                drawArc(
                                    color = WarningGold,
                                    startAngle = 270f,
                                    sweepAngle = rightSweep,
                                    useCenter = false,
                                    topLeft = Offset(center.x - radius, center.y - radius),
                                    size = Size(radius * 2, radius * 2),
                                    style = Stroke(width = 40f)
                                )

                                // Center line
                                drawLine(
                                    color = Color.White,
                                    start = Offset(center.x, center.y - radius - 20f),
                                    end = Offset(center.x, center.y - radius + 20f),
                                    strokeWidth = 2f
                                )
                            }

                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                tr("Tire Utilization (Estimated from Lean Angle)", "Využitie pneumatiky (odhad z náklonu)"),
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.Gray,
                                modifier = Modifier.align(Alignment.CenterHorizontally)
                            )
                            Text(
                                "${tr("Chicken Strips", "Nevyužitý okraj")}: ${tr("L", "Ľ")}: ${String.format(Locale.US, "%.1f", 100f - (ride.maxLeanLeftDeg.toFloat() / 60f).coerceIn(0f, 1f) * 100f)}% | ${tr("R", "P")}: ${String.format(Locale.US, "%.1f", 100f - (ride.maxLeanRightDeg.toFloat() / 60f).coerceIn(0f, 1f) * 100f)}%",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.LightGray,
                                modifier = Modifier.align(Alignment.CenterHorizontally)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SummaryKpiItem(label: String, value: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = label,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            color = Color.Gray
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = value,
            fontSize = 16.sp,
            fontWeight = FontWeight.Black,
            fontFamily = FontFamily.Monospace,
            color = color
        )
    }
}

@Composable
private fun RideMetricMini(label: String, value: String, highlightColor: Color = Color.White) {
    Column {
        Text(
            text = label,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            color = Color.Gray
        )
        Text(
            text = value,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            color = highlightColor
        )
    }
}

@Composable
private fun DetailMetricTile(label: String, value: String, color: Color) {
    Column {
        Text(
            text = label,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            color = Color.Gray
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = value,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            color = color
        )
    }
}

@Composable
fun PodiumCard(
    corner: CornerEventEntity,
    rank: Int,
    color: Color,
    height: Dp,
    onClick: () -> Unit = {}
) {
    Card(
        modifier = Modifier
            .width(100.dp)
            .height(height)
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.2f)),
        border = BorderStroke(2.dp, color)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text("#$rank", fontWeight = FontWeight.Bold, color = color, style = MaterialTheme.typography.titleLarge)
            Spacer(modifier = Modifier.height(4.dp))
            Text("T${corner.sequenceNumber} (${corner.direction.take(1)})", style = MaterialTheme.typography.bodySmall, color = Color.White)
            Text("${tr("Score", "Skóre")}: ${corner.cornerScore.toInt()}", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, color = Color.White)
            Text("${String.format(Locale.US, "%.1f", corner.apexLeanDeg)}°", style = MaterialTheme.typography.bodySmall, color = Color.LightGray)
        }
    }
}

private fun formatRideDuration(seconds: Long): String {
    val h = seconds / 3600
    val m = (seconds % 3600) / 60
    val s = seconds % 60
    return if (h > 0) "${h}h ${m}m" else "${m}m ${s}s"
}

package com.motogeek.telemetry.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.motogeek.telemetry.ui.theme.*
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private data class ActivityItem(
    val id: String,
    val timestampMs: Long,
    val category: String,
    val title: String,
    val details: String,
    val dateDisplay: String,
    val icon: ImageVector,
    val color: Color
)

private fun localizedCategoryLabel(category: String, appLang: AppLang): String = when (category.uppercase()) {
    "ALL" -> appLang.pick("ALL", "VŠETKO")
    "RIDES" -> appLang.pick("RIDES", "JAZDY")
    "SERVICE" -> appLang.pick("SERVICE", "SERVIS")
    "FUEL" -> appLang.pick("FUEL", "PALIVO")
    "PARTS" -> appLang.pick("PARTS", "DIELY")
    "SYSTEM" -> appLang.pick("SYSTEM", "SYSTÉM")
    else -> category.uppercase()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LogsScreen(viewModel: MainViewModel, navController: NavController) {
    val activeBike by viewModel.activeMotorcycle.collectAsState()
    val appLogs by viewModel.currentAppLogs.collectAsState()
    val fuelLogs by viewModel.currentFuelLogs.collectAsState()
    val serviceLogs by viewModel.currentServiceLogs.collectAsState()
    val partLogs by viewModel.currentPartLogs.collectAsState()
    val rides by viewModel.currentRideSessions.collectAsState()

    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val appLang = LocalAppLang.current
    val currency = activeBike?.currencySymbol?.ifBlank { "€" } ?: "€"

    var selectedFilter by remember { mutableStateOf("ALL") }
    var searchQuery by remember { mutableStateOf("") }
    var showClearConfirmDialog by remember { mutableStateOf(false) }
    val categories = listOf("ALL", "RIDES", "SERVICE", "FUEL", "PARTS", "SYSTEM")

    val unifiedTimeline = remember(appLogs, fuelLogs, serviceLogs, partLogs, rides, currency, appLang) {
        val items = mutableListOf<ActivityItem>()

        // 1. App Activity Logs
        appLogs.forEach { log ->
            items.add(
                ActivityItem(
                    id = log.logId,
                    timestampMs = log.timestampEpochMs,
                    category = log.category.uppercase(),
                    title = log.title,
                    details = log.details,
                    dateDisplay = Instant.ofEpochMilli(log.timestampEpochMs).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("MMM dd, yyyy HH:mm")),
                    icon = Icons.Default.Info,
                    color = TelemetryIceCyan
                )
            )
        }

        // 2. Rides
        rides.forEach { ride ->
            items.add(
                ActivityItem(
                    id = "ride_${ride.rideId}",
                    timestampMs = ride.startTimeEpochMs,
                    category = "RIDES",
                    title = ride.name?.ifBlank { appLang.pick("Ride #${ride.rideId}", "Jazda #${ride.rideId}") }
                        ?: appLang.pick("Ride #${ride.rideId}", "Jazda #${ride.rideId}"),
                    details = appLang.pick(
                        "${String.format("%.1f", ride.distanceKm)} km • Peak Lean: ${ride.maxLeanLeftDeg.toInt()}°L / ${ride.maxLeanRightDeg.toInt()}°R • ${ride.avgSpeedKmh.toInt()} km/h avg",
                        "${String.format("%.1f", ride.distanceKm)} km • Max náklon: ${ride.maxLeanLeftDeg.toInt()}°Ľ / ${ride.maxLeanRightDeg.toInt()}°P • ${ride.avgSpeedKmh.toInt()} km/h priem."
                    ),
                    dateDisplay = Instant.ofEpochMilli(ride.startTimeEpochMs).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("MMM dd, yyyy HH:mm")),
                    icon = Icons.Default.Speed,
                    color = RacingAmberPrimary
                )
            )
        }

        // 3. Service Logs
        serviceLogs.forEach { s ->
            val epoch = try {
                LocalDate.parse(s.performedAtIsoDate).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
            } catch (_: Exception) { System.currentTimeMillis() }

            items.add(
                ActivityItem(
                    id = s.logId,
                    timestampMs = epoch,
                    category = "SERVICE",
                    title = s.serviceTitle,
                    details = appLang.pick(
                        "Odo: ${s.odometerAtServiceKm.toInt()} km • ${s.locationOrShop} ${if (s.costEur > 0) "• ${s.costEur.toInt()} $currency" else ""} • ${s.notes}",
                        "Tach: ${s.odometerAtServiceKm.toInt()} km • ${s.locationOrShop} ${if (s.costEur > 0) "• ${s.costEur.toInt()} $currency" else ""} • ${s.notes}"
                    ),
                    dateDisplay = s.performedAtIsoDate,
                    icon = Icons.Default.Build,
                    color = ApexEmeraldGreen
                )
            )
        }

        // 4. Fuel Logs
        fuelLogs.forEach { f ->
            val epoch = try {
                LocalDate.parse(f.dateIso).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
            } catch (_: Exception) { System.currentTimeMillis() }

            items.add(
                ActivityItem(
                    id = "fuel_${f.fuelId}",
                    timestampMs = epoch,
                    category = "FUEL",
                    title = appLang.pick("${f.liters} L Fill-up", "${f.liters} L Tankovanie"),
                    details = appLang.pick(
                        "Total: ${String.format("%.2f", f.totalCost)} $currency • Odo: ${f.odometerKm.toInt()} km ${if (f.notes.isNotBlank()) "• ${f.notes}" else ""}",
                        "Celkom: ${String.format("%.2f", f.totalCost)} $currency • Tach: ${f.odometerKm.toInt()} km ${if (f.notes.isNotBlank()) "• ${f.notes}" else ""}"
                    ),
                    dateDisplay = f.dateIso,
                    icon = Icons.Default.LocalGasStation,
                    color = TelemetryIceCyan
                )
            )
        }

        // 5. Part Logs
        partLogs.forEach { p ->
            val epoch = try {
                LocalDate.parse(p.dateIso).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
            } catch (_: Exception) { System.currentTimeMillis() }

            items.add(
                ActivityItem(
                    id = "part_${p.partId}",
                    timestampMs = epoch,
                    category = "PARTS",
                    title = appLang.pick("Part: ${p.partName}", "Diel: ${p.partName}"),
                    details = appLang.pick(
                        "${p.brand} ${p.partNumber} • ${p.cost.toInt()} $currency • Installed at ${p.odometerKm.toInt()} km",
                        "${p.brand} ${p.partNumber} • ${p.cost.toInt()} $currency • Namontované pri ${p.odometerKm.toInt()} km"
                    ),
                    dateDisplay = p.dateIso,
                    icon = Icons.Default.Handyman,
                    color = WarningGold
                )
            )
        }

        items.sortedByDescending { it.timestampMs }
    }

    val filtered = unifiedTimeline.filter { item ->
        val matchesCategory = if (selectedFilter == "ALL") true else item.category.equals(selectedFilter, ignoreCase = true)
        val matchesQuery = item.title.contains(searchQuery, ignoreCase = true) || item.details.contains(searchQuery, ignoreCase = true)
        matchesCategory && matchesQuery
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = tr("Activity Timeline & Logs", "Časová os & záznamy"),
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
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = tr("Back", "Späť"),
                            tint = RacingAmberPrimary
                        )
                    }
                },
                actions = {
                    if (selectedFilter == "ALL" || selectedFilter == "SYSTEM") {
                        IconButton(onClick = { showClearConfirmDialog = true }) {
                            Icon(Icons.Default.DeleteSweep, contentDescription = tr("Clear", "Vymazať"), tint = Color.Gray)
                        }
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

            // Search Bar
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text(tr("Search activity feed...", "Hľadať v záznamoch aktivity..."), color = Color.Gray, fontSize = 12.sp) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(18.dp)) },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = RacingAmberPrimary,
                    unfocusedBorderColor = TitaniumBorder,
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White
                ),
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Category Filter Chips
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                items(categories) { cat ->
                    FilterChip(
                        selected = selectedFilter == cat,
                        onClick = { selectedFilter = cat },
                        label = { Text(localizedCategoryLabel(cat, appLang), fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = RacingAmberPrimary.copy(alpha = 0.2f),
                            selectedLabelColor = RacingAmberPrimary
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            if (filtered.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(tr("No activities found.", "Nenašli sa žiadne aktivity."), color = Color.Gray)
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(bottom = 32.dp)
                ) {
                    items(filtered, key = { it.id }) { item ->
                        TimelineItemCard(item = item, appLang = appLang)
                    }
                }
            }
        }
    }

    if (showClearConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showClearConfirmDialog = false },
            containerColor = TftSurfaceDark,
            title = {
                Text(
                    text = tr("Clear System Logs", "Vymazať systémové záznamy"),
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    text = tr(
                        "Are you sure you want to clear all system activity logs? Ride, service, fuel, and part records will remain untouched.",
                        "Naozaj chcete vymazať všetky systémové záznamy aktivity? Záznamy jázd, servisu, tankovania a dielov zostanú zachované."
                    ),
                    color = Color.LightGray
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.clearAppLogs()
                    showClearConfirmDialog = false
                    scope.launch {
                        snackbarHostState.showSnackbar(
                            appLang.pick("Activity system logs cleared", "Systémové záznamy aktivity boli vymazané")
                        )
                    }
                }) {
                    Text(tr("Clear Logs", "Vymazať záznamy"), color = RedlineCrimson, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirmDialog = false }) {
                    Text(tr("Cancel", "Zrušiť"), color = Color.Gray)
                }
            }
        )
    }
}

@Composable
private fun TimelineItemCard(item: ActivityItem, appLang: AppLang) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = TftSurfaceDark),
        border = BorderStroke(1.dp, TitaniumBorder)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.Top
        ) {
            // Icon pill
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = item.color.copy(alpha = 0.15f),
                border = BorderStroke(1.dp, item.color.copy(alpha = 0.5f)),
                modifier = Modifier.size(38.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(item.icon, contentDescription = null, tint = item.color, modifier = Modifier.size(20.dp))
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = item.title,
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = item.color.copy(alpha = 0.15f)
                    ) {
                        Text(
                            text = localizedCategoryLabel(item.category, appLang),
                            color = item.color,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.ExtraBold,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = item.dateDisplay,
                    color = Color.Gray,
                    fontSize = 11.sp
                )

                if (item.details.isNotBlank()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = item.details,
                        color = Color(0xFFC0CAD9),
                        fontSize = 12.sp,
                        lineHeight = 16.sp
                    )
                }
            }
        }
    }
}

package com.motogeek.telemetry.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import android.content.Intent
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.motogeek.telemetry.data.local.CornerEventEntity
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.FileProvider
import com.motogeek.telemetry.data.local.RideSessionEntity
import com.motogeek.telemetry.ui.components.RideSocialCardGenerator
import com.motogeek.telemetry.ui.components.RideTrajectoryHeatmapCanvas
import com.motogeek.telemetry.ui.components.SynchronizedTelemetryChart
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TelemetryHistoryScreen(viewModel: MainViewModel) {
    val rides by viewModel.allRides.collectAsState(initial = emptyList())
    var selectedRide by remember { mutableStateOf<RideSessionEntity?>(null) }

    if (selectedRide == null) {
        Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
            Text(
                text = "Ride History",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(16.dp))

            LazyColumn(
                contentPadding = PaddingValues(bottom = 80.dp)
            ) {
                items(rides) { ride ->
                    val sdf = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault())
                    val dateStr = sdf.format(Date(ride.startTimeEpochMs))

                    Card(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp).clickable {
                            selectedRide = ride
                        }
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(text = ride.name ?: dateStr, fontWeight = FontWeight.Bold)
                            if (ride.name != null) {
                                Text(text = dateStr, style = MaterialTheme.typography.bodySmall)
                            }
                            Text(text = "Distance: " + String.format("%.1f km", ride.distanceKm))
                            Text(text = "Style: ${ride.ridingStyleBadge}")
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Max Lean L: " + String.format("%.0f°", ride.maxLeanLeftDeg))
                                Text("Max Lean R: " + String.format("%.0f°", ride.maxLeanRightDeg))
                            }
                        }
                    }
                }
            }
        }
    } else {
        val ride = selectedRide!!
        val trackPoints by viewModel.getTelemetryTrackPointsForRide(ride.rideId).collectAsState(initial = emptyList())
        val cornerEvents by viewModel.getCornerEventsForRide(ride.rideId).collectAsState(initial = emptyList())

        var synchronizedTimestamp by remember { mutableStateOf<Long?>(null) }
        var showDeleteConfirm by remember { mutableStateOf(false) }
        var showEditDialog by remember { mutableStateOf(false) }
        val context = LocalContext.current
        val coroutineScope = rememberCoroutineScope()

        if (showDeleteConfirm) {
            AlertDialog(
                onDismissRequest = { showDeleteConfirm = false },
                title = { Text("Delete Ride") },
                text = { Text("Are you sure you want to delete this ride? This cannot be undone.") },
                confirmButton = {
                    TextButton(onClick = {
                        viewModel.deleteRideSession(ride.rideId)
                        showDeleteConfirm = false
                        selectedRide = null
                    }) {
                        Text("Delete", color = MaterialTheme.colorScheme.error)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showDeleteConfirm = false }) {
                        Text("Cancel")
                    }
                }
            )
        }

        if (showEditDialog) {
            var editName by remember { mutableStateOf(ride.name ?: "") }
            var editNotes by remember { mutableStateOf(ride.notes ?: "") }

            AlertDialog(
                onDismissRequest = { showEditDialog = false },
                title = { Text("Edit Ride Details") },
                text = {
                    Column {
                        OutlinedTextField(
                            value = editName,
                            onValueChange = { editName = it },
                            label = { Text("Ride Name") },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value = editNotes,
                            onValueChange = { editNotes = it },
                            label = { Text("Notes") },
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
                        // Update local state to reflect changes instantly before DB refresh
                        selectedRide = ride.copy(
                            name = editName.takeIf { it.isNotBlank() },
                            notes = editNotes.takeIf { it.isNotBlank() }
                        )
                        showEditDialog = false
                    }) {
                        Text("Save")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showEditDialog = false }) {
                        Text("Cancel")
                    }
                }
            )
        }

        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(ride.name ?: "Ride Details") },
                    navigationIcon = {
                        IconButton(onClick = { selectedRide = null }) {
                            Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
                        }
                    },
                    actions = {
                        IconButton(onClick = {
                            coroutineScope.launch {
                                val file = RideSocialCardGenerator.generateAndSaveCard(context, ride, trackPoints)
                                val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                    type = "image/png"
                                    putExtra(Intent.EXTRA_STREAM, uri)
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                                context.startActivity(Intent.createChooser(shareIntent, "Share Ride Card"))
                            }
                        }) {
                            Icon(Icons.Filled.Share, contentDescription = "Share")
                        }
                        IconButton(onClick = { showEditDialog = true }) {
                            Icon(Icons.Filled.Edit, contentDescription = "Edit")
                        }
                        IconButton(onClick = { showDeleteConfirm = true }) {
                            Icon(Icons.Filled.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error)
                        }
                    }
                )
            }
        ) { paddingValues ->
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(paddingValues),
                contentPadding = PaddingValues(16.dp)
            ) {
                item {
                    if (ride.notes != null) {
                        Text("Notes: ${ride.notes}", style = MaterialTheme.typography.bodyMedium)
                        Spacer(modifier = Modifier.height(16.dp))
                    }

                    Text("Trajectory Heatmap", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(8.dp))
                    RideTrajectoryHeatmapCanvas(
                        trackPoints = trackPoints,
                        cornerEvents = cornerEvents,
                        synchronizedTimestampMs = synchronizedTimestamp,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                }

                item {
                    Text("Telemetry Analytics", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(8.dp))
                    SynchronizedTelemetryChart(
                        trackPoints = trackPoints,
                        onScrub = { timestamp -> synchronizedTimestamp = timestamp },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                }

                item {
                    Text("Top Corners Podium", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(8.dp))

                    if (cornerEvents.isNotEmpty()) {
                        val topCorners = cornerEvents.sortedWith(
                            compareByDescending<CornerEventEntity> { it.cornerScore }
                                .thenByDescending { it.apexLeanDeg }
                        ).take(3)

                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.Bottom) {
                            // Silver (2nd)
                            if (topCorners.size > 1) {
                                PodiumCard(corner = topCorners[1], rank = 2, color = androidx.compose.ui.graphics.Color(0xFFC0C0C0), height = 120.dp)
                            }
                            // Gold (1st)
                            if (topCorners.isNotEmpty()) {
                                PodiumCard(corner = topCorners[0], rank = 1, color = androidx.compose.ui.graphics.Color(0xFFFFD700), height = 140.dp)
                            }
                            // Bronze (3rd)
                            if (topCorners.size > 2) {
                                PodiumCard(corner = topCorners[2], rank = 3, color = androidx.compose.ui.graphics.Color(0xFFCD7F32), height = 100.dp)
                            }
                        }
                    } else {
                        Text("No corner data recorded for this ride.", style = MaterialTheme.typography.bodyMedium)
                    }
                    Spacer(modifier = Modifier.height(24.dp))
                }

                item {
                    Text("Asymmetry Analyzer", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(8.dp))

                    val leftCorners = cornerEvents.filter { it.direction.contains("Left", ignoreCase = true) }
                    val rightCorners = cornerEvents.filter { it.direction.contains("Right", ignoreCase = true) }

                    val avgLeftSpeed = if (leftCorners.isNotEmpty()) leftCorners.map { it.entrySpeedKmh }.average() else 0.0
                    val avgRightSpeed = if (rightCorners.isNotEmpty()) rightCorners.map { it.entrySpeedKmh }.average() else 0.0

                    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Column(horizontalAlignment = Alignment.Start) {
                                    Text("LEFT", fontWeight = FontWeight.Bold, color = androidx.compose.ui.graphics.Color.Cyan)
                                    Text("Avg Entry: ${String.format("%.1f", avgLeftSpeed)} km/h")
                                    Text("Max Lean: ${String.format("%.1f", ride.maxLeanLeftDeg)}°")
                                }
                                Column(horizontalAlignment = Alignment.End) {
                                    Text("RIGHT", fontWeight = FontWeight.Bold, color = androidx.compose.ui.graphics.Color.Yellow)
                                    Text("Avg Entry: ${String.format("%.1f", avgRightSpeed)} km/h")
                                    Text("Max Lean: ${String.format("%.1f", ride.maxLeanRightDeg)}°")
                                }
                            }

                            Spacer(modifier = Modifier.height(16.dp))

                            // Tire Cross-Section Canvas
                            androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxWidth().height(150.dp)) {
                                val width = size.width
                                val height = size.height
                                val radius = height * 0.9f
                                val center = androidx.compose.ui.geometry.Offset(width / 2, height)

                                // Base Tire (Unused / Chicken Strips)
                                drawArc(
                                    color = androidx.compose.ui.graphics.Color.DarkGray,
                                    startAngle = 180f,
                                    sweepAngle = 180f,
                                    useCenter = false,
                                    topLeft = androidx.compose.ui.geometry.Offset(center.x - radius, center.y - radius),
                                    size = androidx.compose.ui.geometry.Size(radius * 2, radius * 2),
                                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = 40f, cap = androidx.compose.ui.graphics.StrokeCap.Round)
                                )

                                // Used Tire (Left) - Map max lean (0 to 60 deg) to sweep angle (0 to 90 deg)
                                val leftSweep = (ride.maxLeanLeftDeg.toFloat() / 60f).coerceIn(0f, 1f) * 90f
                                drawArc(
                                    color = androidx.compose.ui.graphics.Color.Cyan,
                                    startAngle = 270f - leftSweep,
                                    sweepAngle = leftSweep,
                                    useCenter = false,
                                    topLeft = androidx.compose.ui.geometry.Offset(center.x - radius, center.y - radius),
                                    size = androidx.compose.ui.geometry.Size(radius * 2, radius * 2),
                                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = 40f)
                                )

                                // Used Tire (Right)
                                val rightSweep = (ride.maxLeanRightDeg.toFloat() / 60f).coerceIn(0f, 1f) * 90f
                                drawArc(
                                    color = androidx.compose.ui.graphics.Color.Yellow,
                                    startAngle = 270f,
                                    sweepAngle = rightSweep,
                                    useCenter = false,
                                    topLeft = androidx.compose.ui.geometry.Offset(center.x - radius, center.y - radius),
                                    size = androidx.compose.ui.geometry.Size(radius * 2, radius * 2),
                                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = 40f)
                                )

                                // Center line marker
                                drawLine(
                                    color = androidx.compose.ui.graphics.Color.White,
                                    start = androidx.compose.ui.geometry.Offset(center.x, center.y - radius - 20f),
                                    end = androidx.compose.ui.geometry.Offset(center.x, center.y - radius + 20f),
                                    strokeWidth = 2f
                                )
                            }

                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                "Tire Utilization (Estimated from Lean Angle)",
                                style = MaterialTheme.typography.bodySmall,
                                color = androidx.compose.ui.graphics.Color.Gray,
                                modifier = Modifier.align(Alignment.CenterHorizontally)
                            )
                            Text(
                                "Chicken Strips: L: ${String.format("%.1f", 100f - (ride.maxLeanLeftDeg.toFloat() / 60f).coerceIn(0f, 1f) * 100f)}% | R: ${String.format("%.1f", 100f - (ride.maxLeanRightDeg.toFloat() / 60f).coerceIn(0f, 1f) * 100f)}%",
                                style = MaterialTheme.typography.bodySmall,
                                color = androidx.compose.ui.graphics.Color.LightGray,
                                modifier = Modifier.align(Alignment.CenterHorizontally)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(24.dp))
                }
            }
        }
    }
}

@Composable
fun PodiumCard(corner: com.motogeek.telemetry.data.local.CornerEventEntity, rank: Int, color: androidx.compose.ui.graphics.Color, height: androidx.compose.ui.unit.Dp) {
    Card(
        modifier = Modifier.width(100.dp).height(height),
        colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.2f)),
        border = androidx.compose.foundation.BorderStroke(2.dp, color)
    ) {
        Column(modifier = Modifier.fillMaxSize().padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Text("#$rank", fontWeight = FontWeight.Bold, color = color, style = MaterialTheme.typography.titleLarge)
            Spacer(modifier = Modifier.height(4.dp))
            Text("T${corner.sequenceNumber} (${corner.direction})", style = MaterialTheme.typography.bodySmall, color = androidx.compose.ui.graphics.Color.White)
            Text("Score: ${String.format("%.0f", corner.cornerScore)}", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, color = androidx.compose.ui.graphics.Color.White)
            Text("${String.format("%.1f", corner.apexLeanDeg)}°", style = MaterialTheme.typography.bodySmall, color = androidx.compose.ui.graphics.Color.LightGray)
        }
    }
}

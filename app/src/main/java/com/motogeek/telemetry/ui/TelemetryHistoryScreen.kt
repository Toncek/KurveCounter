package com.motogeek.telemetry.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun TelemetryHistoryScreen(viewModel: MainViewModel) {
    val rides by viewModel.allRides.collectAsState(initial = emptyList())

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
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(text = dateStr, fontWeight = FontWeight.Bold)
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
}

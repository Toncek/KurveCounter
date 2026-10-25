package com.motogeek.telemetry.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.motogeek.telemetry.domain.maintenance.TaskStatus

@Composable
fun MaintenanceScreen(viewModel: MainViewModel) {
    val activeBike by viewModel.activeMotorcycle.collectAsState(initial = null)

    if (activeBike == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = androidx.compose.ui.Alignment.Center) {
            Text("No active motorcycle found.")
        }
        return
    }

    val tasks by viewModel.getMaintenanceTasks(activeBike!!.id).collectAsState(initial = emptyList())
    val logs by viewModel.getServiceLogs(activeBike!!.id).collectAsState(initial = emptyList())

    val evaluatedTasks = viewModel.evaluator.evaluate(activeBike!!, tasks, logs)

    var showRecordDialog by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text(
            text = "Maintenance",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )
        Text(text = "Odometer: " + String.format("%.0f km", activeBike!!.totalOdometerKm))
        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = { showRecordDialog = true },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Record Service")
        }

        Spacer(modifier = Modifier.height(16.dp))

        LazyColumn(
            contentPadding = PaddingValues(bottom = 80.dp)
        ) {
            items(evaluatedTasks.sortedByDescending { it.status }) { status ->
                val color = when(status.status) {
                    TaskStatus.OVERDUE -> Color(0xFFFF1744)
                    TaskStatus.DUE_SOON -> Color(0xFFFF5722)
                    TaskStatus.OK -> Color(0xFF00E676)
                }

                Card(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(text = status.task.taskName, fontWeight = FontWeight.Bold)
                            Text(text = status.status.name, color = color, fontWeight = FontWeight.Bold)
                        }
                        Text(text = "Remaining: ${status.remainingKm?.toInt() ?: "N/A"} km / ${status.remainingDays ?: "N/A"} days")
                        LinearProgressIndicator(
                            progress = { status.progressFraction },
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                            color = color
                        )
                    }
                }
            }
        }

        if (showRecordDialog) {
            AlertDialog(
                onDismissRequest = { showRecordDialog = false },
                title = { Text("Record Service") },
                text = { Text("Logging functionality to be fully connected to ViewModels in production.") },
                confirmButton = {
                    Button(onClick = { showRecordDialog = false }) { Text("OK") }
                }
            )
        }
    }
}

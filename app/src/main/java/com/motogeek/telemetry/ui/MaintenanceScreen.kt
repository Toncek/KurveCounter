package com.motogeek.telemetry.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Opacity
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.TireRepair
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.motogeek.telemetry.data.local.MaintenanceTaskEntity
import com.motogeek.telemetry.domain.maintenance.MaintenanceTaskStatus
import com.motogeek.telemetry.domain.maintenance.TaskStatus
import kotlinx.coroutines.launch
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MaintenanceScreen(viewModel: MainViewModel) {
    val activeBike by viewModel.activeMotorcycle.collectAsState(initial = null)

    if (activeBike == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("No active motorcycle found.")
        }
        return
    }

    val tasks by viewModel.getMaintenanceTasks(activeBike!!.id).collectAsState(initial = emptyList())
    val logs by viewModel.getServiceLogs(activeBike!!.id).collectAsState(initial = emptyList())

    val evaluatedTasks = viewModel.evaluator.evaluate(activeBike!!, tasks, logs)

    var showRecordDialog by remember { mutableStateOf(false) }
    var showAddTaskSheet by remember { mutableStateOf(false) }
    var selectedCategoryFilter by remember { mutableStateOf("All") }
    var selectedTab by remember { mutableStateOf(0) }

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    val categories = listOf("All", "Overdue/Soon", "Engine", "Drivetrain", "Brakes", "Tires", "Suspension", "Ignition", "Cooling")

    val filteredTasks = evaluatedTasks.filter { status ->
        when (selectedCategoryFilter) {
            "All" -> true
            "Overdue/Soon" -> status.status == TaskStatus.OVERDUE || status.status == TaskStatus.DUE_SOON
            else -> status.task.category.equals(selectedCategoryFilter, ignoreCase = true)
        }
    }.sortedByDescending { it.status }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            if (selectedTab == 0) {
                FloatingActionButton(onClick = { showAddTaskSheet = true }) {
                    Icon(Icons.Filled.Add, contentDescription = "Add Task")
                }
            }
        }
    ) { paddingValues ->
        Column(modifier = Modifier.fillMaxSize().padding(paddingValues).padding(16.dp)) {
            Text(
                text = "Maintenance",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )
            Text(text = "Odometer: " + String.format("%.0f km", activeBike!!.totalOdometerKm))
            Spacer(modifier = Modifier.height(16.dp))

            QuickReferenceSheetCard(activeBike!!)
            Spacer(modifier = Modifier.height(16.dp))

            TabRow(selectedTabIndex = selectedTab) {
                Tab(selected = selectedTab == 0, onClick = { selectedTab = 0 }, text = { Text("Schedule") })
                Tab(selected = selectedTab == 1, onClick = { selectedTab = 1 }, text = { Text("History & Costs") })
            }
            Spacer(modifier = Modifier.height(16.dp))

            if (selectedTab == 0) {
                // Schedule Tab
                // Quick Service Bar
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    item {
                        AssistChip(
                            onClick = {
                                scope.launch {
                                    viewModel.logQuickServiceByCategory(activeBike!!, "chain", "Chain Lube")
                                    val result = snackbarHostState.showSnackbar(
                                        message = "Logged Chain Lube",
                                        actionLabel = "Undo",
                                        duration = SnackbarDuration.Short
                                    )
                                    if (result == SnackbarResult.ActionPerformed) {
                                        viewModel.undoLastQuickService()
                                    }
                                }
                            },
                            label = { Text("Chain Lubed Today") },
                            leadingIcon = { Icon(Icons.Filled.Settings, contentDescription = null) }
                        )
                    }
                    item {
                        AssistChip(
                            onClick = {
                                scope.launch {
                                    viewModel.logQuickServiceByCategory(activeBike!!, "tire", "Tire Pressure Check")
                                    val result = snackbarHostState.showSnackbar(
                                        message = "Logged Tire Pressure Check",
                                        actionLabel = "Undo",
                                        duration = SnackbarDuration.Short
                                    )
                                    if (result == SnackbarResult.ActionPerformed) {
                                        viewModel.undoLastQuickService()
                                    }
                                }
                            },
                            label = { Text("Tire Pressure Checked") },
                            leadingIcon = { Icon(Icons.Filled.TireRepair, contentDescription = null) }
                        )
                    }
                    item {
                        AssistChip(
                            onClick = {
                                scope.launch {
                                    viewModel.logQuickServiceByCategory(activeBike!!, "oil", "Oil Level Check")
                                    val result = snackbarHostState.showSnackbar(
                                        message = "Logged Oil Level Check",
                                        actionLabel = "Undo",
                                        duration = SnackbarDuration.Short
                                    )
                                    if (result == SnackbarResult.ActionPerformed) {
                                        viewModel.undoLastQuickService()
                                    }
                                }
                            },
                            label = { Text("Oil Level Checked") },
                            leadingIcon = { Icon(Icons.Filled.Opacity, contentDescription = null) }
                        )
                    }
                    item {
                        AssistChip(
                            onClick = { showRecordDialog = true },
                            label = { Text("Custom Log") },
                            leadingIcon = { Icon(Icons.Filled.Add, contentDescription = null) }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Category Filter Chips
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(categories) { category ->
                        FilterChip(
                            selected = selectedCategoryFilter == category,
                            onClick = { selectedCategoryFilter = category },
                            label = { Text(category) }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                LazyColumn(
                    contentPadding = PaddingValues(bottom = 80.dp)
                ) {
                    items(filteredTasks) { status ->
                        MaintenanceTaskCard(status)
                    }
                }
            } else {
                // History & Costs Tab
                val totalSpend = logs.sumOf { it.costEur }
                val costPer100Km = if (activeBike!!.accumulatedTrackedKm > 0) {
                    (totalSpend / activeBike!!.accumulatedTrackedKm) * 100
                } else 0.0
                val totalRides = 0 // Would come from rides flow in a real app, keeping simple for this scope

                Card(modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(text = "Garage Cost & Mileage Summary", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Total Maintenance Spend: €${String.format("%.2f", totalSpend)}")
                        Text("Cost per 100 km: €${String.format("%.2f", costPer100Km)}")
                        Text("Total Rides Logged: $totalRides")
                    }
                }

                Text("Service History Timeline", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(8.dp))

                LazyColumn(contentPadding = PaddingValues(bottom = 80.dp)) {
                    items(logs.sortedByDescending { it.performedAtIsoDate }) { log ->
                        Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                            Row(modifier = Modifier.padding(16.dp).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(text = log.notes.ifEmpty { "Service" }, fontWeight = FontWeight.Bold)
                                    Text(text = "${log.performedAtIsoDate} - ${log.odometerAtServiceKm.toInt()} km", style = MaterialTheme.typography.bodySmall)
                                    if (log.costEur > 0) {
                                        Text(text = "Cost: €${log.costEur}", style = MaterialTheme.typography.bodySmall)
                                    }
                                }
                                IconButton(onClick = {
                                    scope.launch {
                                        viewModel.repository.deleteServiceLog(log.logId)
                                    }
                                }) {
                                    Icon(Icons.Filled.Delete, contentDescription = "Delete Log", tint = MaterialTheme.colorScheme.error)
                                }
                            }
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

            if (showAddTaskSheet) {
                ModalBottomSheet(
                    onDismissRequest = { showAddTaskSheet = false },
                    modifier = Modifier.fillMaxHeight(0.9f)
                ) {
                    AddEditMaintenanceTaskSheet(
                        motorcycleId = activeBike!!.id,
                        onDismiss = { showAddTaskSheet = false },
                        onSave = { entity ->
                            scope.launch {
                                viewModel.repository.insertMaintenanceTask(entity)
                                showAddTaskSheet = false
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun MaintenanceTaskCard(status: MaintenanceTaskStatus) {
    val color = when(status.status) {
        TaskStatus.OVERDUE -> Color(0xFFFF1744) // Red
        TaskStatus.DUE_SOON -> Color(0xFFFF9800) // Amber/Orange
        TaskStatus.OK -> Color(0xFF00E676) // Green
    }

    val animatedProgress by animateFloatAsState(targetValue = status.progressFraction)

    val categoryIcon = when (status.task.category.lowercase()) {
        "engine" -> Icons.Filled.Build
        "drivetrain" -> Icons.Filled.Settings
        "brakes" -> Icons.Filled.Speed
        "tires" -> Icons.Filled.TireRepair
        "suspension" -> Icons.Filled.Build
        "ignition" -> Icons.Filled.Build
        "cooling" -> Icons.Filled.Opacity
        else -> Icons.Filled.Info
    }

    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(categoryIcon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = status.task.taskName, fontWeight = FontWeight.Bold)
                }
                Text(text = status.status.name, color = color, fontWeight = FontWeight.Bold)
            }

            Spacer(modifier = Modifier.height(8.dp))

            val remainingKmText = status.remainingKm?.let { "${it.toInt()} km" } ?: "N/A"
            val remainingDaysText = status.remainingDays?.let { "$it days" } ?: "N/A"
            val dueAtText = status.nextDueOdometerKm?.let { " (Due at ${it.toInt()} km)" } ?: ""

            Text(
                text = "Remaining: $remainingKmText or $remainingDaysText$dueAtText",
                style = MaterialTheme.typography.bodyMedium
            )

            LinearProgressIndicator(
                progress = { animatedProgress },
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp).height(8.dp),
                color = color
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddEditMaintenanceTaskSheet(motorcycleId: String, onDismiss: () -> Unit, onSave: (MaintenanceTaskEntity) -> Unit) {
    var taskName by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("Engine") }
    var actionType by remember { mutableStateOf("Replace") }
    var breakInKm by remember { mutableStateOf("") }
    var intervalKm by remember { mutableStateOf("") }
    var intervalMonths by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }

    var expandedCategory by remember { mutableStateOf(false) }
    val categories = listOf("Engine", "Drivetrain", "Brakes", "Tires", "Suspension", "Ignition", "Cooling", "General")

    val scrollState = rememberScrollState()

    Column(modifier = Modifier.padding(16.dp).verticalScroll(scrollState)) {
        Text("Add/Edit Task", style = MaterialTheme.typography.titleLarge)
        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(value = taskName, onValueChange = { taskName = it }, label = { Text("Task Name") }, modifier = Modifier.fillMaxWidth())

        ExposedDropdownMenuBox(
            expanded = expandedCategory,
            onExpandedChange = { expandedCategory = it },
        ) {
            OutlinedTextField(
                modifier = Modifier.menuAnchor().fillMaxWidth(),
                readOnly = true,
                value = category,
                onValueChange = {},
                label = { Text("Category") }
            )
            ExposedDropdownMenu(
                expanded = expandedCategory,
                onDismissRequest = { expandedCategory = false },
            ) {
                categories.forEach { selectionOption ->
                    DropdownMenuItem(
                        text = { Text(selectionOption) },
                        onClick = {
                            category = selectionOption
                            expandedCategory = false
                        }
                    )
                }
            }
        }

        OutlinedTextField(value = actionType, onValueChange = { actionType = it }, label = { Text("Action (Replace, Inspect, Lube)") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(value = breakInKm, onValueChange = { breakInKm = it }, label = { Text("First Break-In (km)") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(value = intervalKm, onValueChange = { intervalKm = it }, label = { Text("Recurring Interval (km)") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(value = intervalMonths, onValueChange = { intervalMonths = it }, label = { Text("Recurring Interval (months)") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(value = notes, onValueChange = { notes = it }, label = { Text("Notes / Torques") }, modifier = Modifier.fillMaxWidth(), minLines = 3)

        Spacer(modifier = Modifier.height(24.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            TextButton(onClick = onDismiss) { Text("Cancel") }
            Spacer(modifier = Modifier.width(8.dp))
            Button(onClick = {
                val task = MaintenanceTaskEntity(
                    taskId = UUID.randomUUID().toString(),
                    motorcycleId = motorcycleId,
                    category = category,
                    taskName = taskName,
                    actionType = actionType,
                    firstBreakInAtKm = breakInKm.toDoubleOrNull(),
                    intervalKm = intervalKm.toDoubleOrNull(),
                    intervalMonths = intervalMonths.toIntOrNull(),
                    warningThresholdKm = 500.0,
                    warningThresholdDays = 30,
                    oemPartNumbersJson = "[]",
                    notes = notes
                )
                onSave(task)
            }) {
                Text("Save")
            }
        }
        Spacer(modifier = Modifier.height(80.dp))
    }
}

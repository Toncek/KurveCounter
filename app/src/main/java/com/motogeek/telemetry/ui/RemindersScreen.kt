package com.motogeek.telemetry.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.motogeek.telemetry.data.local.ReminderEntity
import com.motogeek.telemetry.domain.maintenance.MaintenanceTaskStatus
import com.motogeek.telemetry.domain.maintenance.TaskStatus
import com.motogeek.telemetry.domain.toSafeDouble
import com.motogeek.telemetry.ui.theme.*
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RemindersScreen(viewModel: MainViewModel, navController: NavController) {
    val activeBike by viewModel.activeMotorcycle.collectAsState()
    val reminders by viewModel.currentReminders.collectAsState()
    val serviceLogs by viewModel.currentServiceLogs.collectAsState()
    val allTasks by produceState(initialValue = emptyList<com.motogeek.telemetry.data.local.MaintenanceTaskEntity>(), activeBike) {
        activeBike?.let {
            viewModel.repository.getMaintenanceTasksForMotorcycleFlow(it.id).collect { t ->
                value = t
            }
        }
    }

    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    var showAddDialog by remember { mutableStateOf(false) }

    // Evaluated automatic alerts
    val evaluatedTasks = remember(activeBike, allTasks, serviceLogs) {
        if (activeBike != null) {
            viewModel.evaluator.evaluate(activeBike!!, allTasks, serviceLogs)
        } else emptyList()
    }

    val autoAlerts = evaluatedTasks.filter {
        it.status == TaskStatus.OVERDUE || it.status == TaskStatus.DUE_SOON
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Reminders & Alerts",
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
                actions = {
                    TextButton(onClick = { showAddDialog = true }) {
                        Text("+ ADD REMINDER", color = RacingAmberPrimary, fontWeight = FontWeight.Bold)
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
            if (activeBike == null) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No active motorcycle selected.", color = Color.Gray)
                }
                return@Scaffold
            }

            Spacer(modifier = Modifier.height(8.dp))

            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(bottom = 32.dp)
            ) {
                // Section 1: Automatic Maintenance Alerts
                item {
                    Text(
                        text = "AUTOMATIC MAINTENANCE ALERTS (${autoAlerts.size})",
                        color = Color(0xFF6B7A99),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        letterSpacing = 1.sp,
                        modifier = Modifier.padding(vertical = 4.dp)
                    )
                }

                if (autoAlerts.isEmpty()) {
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(8.dp),
                            colors = CardDefaults.cardColors(containerColor = TftSurfaceDark),
                            border = BorderStroke(1.dp, ApexEmeraldGreen.copy(alpha = 0.4f))
                        ) {
                            Row(
                                modifier = Modifier.padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.CheckCircle, contentDescription = null, tint = ApexEmeraldGreen, modifier = Modifier.size(20.dp))
                                Spacer(modifier = Modifier.width(10.dp))
                                Text("All service schedule intervals are currently within limits.", color = Color.White, fontSize = 13.sp)
                            }
                        }
                    }
                } else {
                    items(autoAlerts) { alert ->
                        AutoAlertCard(
                            alert = alert,
                            onServiceNow = { navController.navigate("maintenance") }
                        )
                    }
                }

                item { Spacer(modifier = Modifier.height(14.dp)) }

                // Section 2: Custom User Reminders
                item {
                    Text(
                        text = "CUSTOM REMINDERS (${reminders.size})",
                        color = Color(0xFF6B7A99),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        letterSpacing = 1.sp,
                        modifier = Modifier.padding(vertical = 4.dp)
                    )
                }

                if (reminders.isEmpty()) {
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(8.dp),
                            colors = CardDefaults.cardColors(containerColor = TftSurfaceDark),
                            border = BorderStroke(1.dp, TitaniumBorder)
                        ) {
                            Text(
                                text = "No custom reminders set. Tap '+ ADD REMINDER' to create alerts for tire swaps, insurance, chain lube, etc.",
                                color = Color.Gray,
                                fontSize = 13.sp,
                                modifier = Modifier.padding(14.dp)
                            )
                        }
                    }
                } else {
                    items(reminders, key = { it.reminderId }) { reminder ->
                        CustomReminderCard(
                            reminder = reminder,
                            onToggleComplete = { isChecked ->
                                viewModel.updateReminder(reminder.copy(isCompleted = isChecked))
                            },
                            onDelete = {
                                viewModel.deleteReminder(reminder.reminderId)
                                scope.launch { snackbarHostState.showSnackbar("Reminder deleted") }
                            }
                        )
                    }
                }
            }
        }
    }

    if (showAddDialog && activeBike != null) {
        AddCustomReminderDialog(
            activeBike = activeBike!!,
            onDismiss = { showAddDialog = false },
            onSave = { entity ->
                viewModel.addReminder(entity)
                showAddDialog = false
                scope.launch { snackbarHostState.showSnackbar("Reminder added") }
            }
        )
    }
}

@Composable
private fun AutoAlertCard(
    alert: MaintenanceTaskStatus,
    onServiceNow: () -> Unit
) {
    val isOverdue = alert.status == TaskStatus.OVERDUE
    val badgeColor = if (isOverdue) RedlineCrimson else WarningGold

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = TftSurfaceDark),
        border = BorderStroke(1.dp, badgeColor.copy(alpha = 0.6f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = badgeColor.copy(alpha = 0.15f),
                        border = BorderStroke(1.dp, badgeColor.copy(alpha = 0.5f))
                    ) {
                        Text(
                            text = if (isOverdue) "OVERDUE" else "DUE SOON",
                            color = badgeColor,
                            fontWeight = FontWeight.Bold,
                            fontSize = 9.sp,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = alert.task.taskName,
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))
                val remainingKm = alert.remainingKm?.let { "${it.toInt()} km remaining" } ?: ""
                val remainingDays = alert.remainingDays?.let { " • $it days" } ?: ""
                val dueAt = alert.nextDueOdometerKm?.let { " (Due at ${it.toInt()} km)" } ?: ""
                Text(
                    text = "$remainingKm$remainingDays$dueAt",
                    color = Color.LightGray,
                    fontSize = 11.sp
                )
            }

            Button(
                onClick = onServiceNow,
                colors = ButtonDefaults.buttonColors(containerColor = RacingAmberPrimary),
                shape = RoundedCornerShape(6.dp),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                modifier = Modifier.height(32.dp)
            ) {
                Text("Service", fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun CustomReminderCard(
    reminder: ReminderEntity,
    onToggleComplete: (Boolean) -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = TftSurfaceDark),
        border = BorderStroke(1.dp, TitaniumBorder)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Checkbox(
                    checked = reminder.isCompleted,
                    onCheckedChange = onToggleComplete,
                    colors = CheckboxDefaults.colors(
                        checkedColor = ApexEmeraldGreen,
                        uncheckedColor = Color.Gray
                    )
                )
                Spacer(modifier = Modifier.width(6.dp))
                Column {
                    Text(
                        text = reminder.title,
                        color = if (reminder.isCompleted) Color.Gray else Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        textDecoration = if (reminder.isCompleted) TextDecoration.LineThrough else null
                    )
                    val targetParts = mutableListOf<String>()
                    reminder.targetOdometerKm?.let { targetParts.add("Target: ${it.toInt()} km") }
                    reminder.targetDateIso?.takeIf { it.isNotBlank() }?.let { targetParts.add("Date: $it") }

                    if (targetParts.isNotEmpty()) {
                        Text(
                            text = targetParts.joinToString(" • "),
                            color = Color.Gray,
                            fontSize = 11.sp
                        )
                    }
                }
            }

            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, contentDescription = "Delete", tint = RedlineCrimson.copy(alpha = 0.7f), modifier = Modifier.size(18.dp))
            }
        }
    }
}

@Composable
private fun AddCustomReminderDialog(
    activeBike: com.motogeek.telemetry.data.local.MotorcycleEntity,
    onDismiss: () -> Unit,
    onSave: (ReminderEntity) -> Unit
) {
    var title by remember { mutableStateOf("") }
    var targetOdoText by remember { mutableStateOf("") }
    var targetDateIso by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = TftSurfaceDark,
        title = { Text("Add Custom Reminder", color = Color.White, fontWeight = FontWeight.Bold) },
        text = {
            Column {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Reminder Title (e.g. Clean & Lube Chain)") },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = RacingAmberPrimary,
                        unfocusedBorderColor = TitaniumBorder,
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(10.dp))
                OutlinedTextField(
                    value = targetOdoText,
                    onValueChange = { targetOdoText = it },
                    label = { Text("Target Odometer km (Optional)") },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = RacingAmberPrimary,
                        unfocusedBorderColor = TitaniumBorder,
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(10.dp))
                OutlinedTextField(
                    value = targetDateIso,
                    onValueChange = { targetDateIso = it },
                    label = { Text("Target Date YYYY-MM-DD (Optional)") },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = RacingAmberPrimary,
                        unfocusedBorderColor = TitaniumBorder,
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (title.isNotBlank()) {
                        val odo = targetOdoText.toSafeDouble(0.0).takeIf { it > 0 }
                        onSave(
                            ReminderEntity(
                                motorcycleId = activeBike.id,
                                title = title.trim(),
                                targetOdometerKm = odo,
                                targetDateIso = targetDateIso.takeIf { it.isNotBlank() },
                                isCompleted = false
                            )
                        )
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = RacingAmberPrimary)
            ) {
                Text("Save Reminder", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = Color.Gray) }
        }
    )
}

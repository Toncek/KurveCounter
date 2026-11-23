package com.motogeek.telemetry.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import java.time.LocalDate

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
    val appLang = LocalAppLang.current

    var showAddDialog by remember { mutableStateOf(false) }
    var editingReminder by remember { mutableStateOf<ReminderEntity?>(null) }
    var pendingDeleteReminder by remember { mutableStateOf<ReminderEntity?>(null) }

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
                        text = tr("Reminders & Alerts", "Pripomienky & upozornenia"),
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
                    TextButton(onClick = {
                        editingReminder = null
                        showAddDialog = true
                    }) {
                        Text(tr("+ ADD REMINDER", "+ PRIDAŤ PRIPOMIENKU"), color = RacingAmberPrimary, fontWeight = FontWeight.Bold)
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
                    Text(tr("No active motorcycle selected.", "Nie je vybraný žiadny aktívny motocykel."), color = Color.Gray)
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
                        text = tr(
                            "AUTOMATIC MAINTENANCE ALERTS (${autoAlerts.size})",
                            "AUTOMATICKÉ SERVISNÉ UPOZORNENIA (${autoAlerts.size})"
                        ),
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
                                Text(
                                    tr(
                                        "All service schedule intervals are currently within limits.",
                                        "Všetky servisné intervaly sú momentálne v norme."
                                    ),
                                    color = Color.White,
                                    fontSize = 13.sp
                                )
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
                        text = tr("CUSTOM REMINDERS (${reminders.size})", "VLASTNÉ PRIPOMIENKY (${reminders.size})"),
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
                                text = tr(
                                    "No custom reminders set. Tap '+ ADD REMINDER' to create alerts for tire swaps, insurance, chain lube, etc.",
                                    "Zatiaľ nemáte vlastné pripomienky. Klepnite na '+ PRIDAŤ PRIPOMIENKU' pre upozornenia na výmenu pneu, STK, mazanie reťaze a pod."
                                ),
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
                            onEdit = {
                                editingReminder = reminder
                                showAddDialog = true
                            },
                            onToggleComplete = { isChecked ->
                                viewModel.updateReminder(reminder.copy(isCompleted = isChecked))
                            },
                            onDelete = {
                                pendingDeleteReminder = reminder
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
            existing = editingReminder,
            onDismiss = {
                showAddDialog = false
                editingReminder = null
            },
            onSave = { entity ->
                if (editingReminder != null) {
                    viewModel.updateReminder(entity)
                    scope.launch { snackbarHostState.showSnackbar(appLang.pick("Reminder updated", "Pripomienka aktualizovaná")) }
                } else {
                    viewModel.addReminder(entity)
                    scope.launch { snackbarHostState.showSnackbar(appLang.pick("Reminder added", "Pripomienka pridaná")) }
                }
                showAddDialog = false
                editingReminder = null
            }
        )
    }

    pendingDeleteReminder?.let { reminderToDelete ->
        AlertDialog(
            onDismissRequest = { pendingDeleteReminder = null },
            containerColor = TftSurfaceDark,
            title = {
                Text(
                    text = tr("Delete Reminder", "Vymazať pripomienku"),
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    text = tr(
                        "Are you sure you want to delete \"${reminderToDelete.title}\"? This cannot be undone.",
                        "Naozaj chcete vymazať pripomienku \"${reminderToDelete.title}\"? Túto akciu nemožno vrátiť späť."
                    ),
                    color = Color.LightGray
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteReminder(reminderToDelete.reminderId)
                    pendingDeleteReminder = null
                    scope.launch {
                        snackbarHostState.showSnackbar(appLang.pick("Reminder deleted", "Pripomienka vymazaná"))
                    }
                }) {
                    Text(tr("Delete", "Vymazať"), color = RedlineCrimson, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDeleteReminder = null }) {
                    Text(tr("Cancel", "Zrušiť"), color = Color.Gray)
                }
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
                            text = if (isOverdue) tr("OVERDUE", "PO TERMÍNE") else tr("DUE SOON", "ČOSKORO"),
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
                val remainingKm = alert.remainingKm?.let { tr("${it.toInt()} km remaining", "zostáva ${it.toInt()} km") } ?: ""
                val remainingDays = alert.remainingDays?.let { tr(" • $it days", " • $it dní") } ?: ""
                val dueAt = alert.nextDueOdometerKm?.let { tr(" (Due at ${it.toInt()} km)", " (Termín pri ${it.toInt()} km)") } ?: ""
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
                Text(tr("Service", "Servis"), fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun CustomReminderCard(
    reminder: ReminderEntity,
    onEdit: () -> Unit,
    onToggleComplete: (Boolean) -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onEdit),
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
                    reminder.targetOdometerKm?.let { targetParts.add(tr("Target: ${it.toInt()} km", "Cieľ: ${it.toInt()} km")) }
                    reminder.targetDateIso?.takeIf { it.isNotBlank() }?.let { targetParts.add(tr("Date: $it", "Dátum: $it")) }

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
                Icon(Icons.Default.Delete, contentDescription = tr("Delete", "Vymazať"), tint = RedlineCrimson.copy(alpha = 0.7f), modifier = Modifier.size(18.dp))
            }
        }
    }
}

@Composable
private fun AddCustomReminderDialog(
    activeBike: com.motogeek.telemetry.data.local.MotorcycleEntity,
    existing: ReminderEntity? = null,
    onDismiss: () -> Unit,
    onSave: (ReminderEntity) -> Unit
) {
    var title by remember(existing?.reminderId) { mutableStateOf(existing?.title ?: "") }
    var targetOdoText by remember(existing?.reminderId) {
        mutableStateOf(existing?.targetOdometerKm?.toInt()?.toString() ?: "")
    }
    var targetDateIso by remember(existing?.reminderId) { mutableStateOf(existing?.targetDateIso ?: "") }
    var showTitleError by remember { mutableStateOf(false) }

    val today = remember { LocalDate.now() }
    val datePresets = remember(today) {
        listOf(
            "+7d" to today.plusDays(7).toString(),
            "+30d" to today.plusDays(30).toString(),
            "+90d" to today.plusDays(90).toString(),
            "+1y" to today.plusYears(1).toString()
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = TftSurfaceDark,
        title = {
            Text(
                text = if (existing != null) tr("Edit Custom Reminder", "Upraviť pripomienku") else tr("Add Custom Reminder", "Pridať vlastnú pripomienku"),
                color = Color.White,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column {
                OutlinedTextField(
                    value = title,
                    onValueChange = {
                        title = it
                        if (showTitleError && it.isNotBlank()) showTitleError = false
                    },
                    label = { Text(tr("Reminder Title (e.g. Clean & Lube Chain)", "Názov pripomienky (napr. Vyčistiť & namazať reťaz)")) },
                    isError = showTitleError,
                    supportingText = if (showTitleError) {
                        { Text(tr("Title is required", "Názov je povinný"), color = RedlineCrimson, fontSize = 11.sp) }
                    } else null,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = RacingAmberPrimary,
                        unfocusedBorderColor = TitaniumBorder,
                        errorBorderColor = RedlineCrimson,
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(10.dp))
                OutlinedTextField(
                    value = targetOdoText,
                    onValueChange = { targetOdoText = it },
                    label = { Text(tr("Target Odometer km (Optional)", "Cieľový nájazd km (voliteľné)")) },
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
                    label = { Text(tr("Target Date YYYY-MM-DD (Optional)", "Cieľový dátum RRRR-MM-DD (voliteľné)")) },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = RacingAmberPrimary,
                        unfocusedBorderColor = TitaniumBorder,
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    datePresets.forEach { (chipKey, presetDate) ->
                        val chipText = if (chipKey == "+1y") tr("+1y", "+1r") else chipKey
                        FilterChip(
                            selected = targetDateIso == presetDate,
                            onClick = { targetDateIso = presetDate },
                            label = { Text(chipText, fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = RacingAmberPrimary.copy(alpha = 0.2f),
                                selectedLabelColor = RacingAmberPrimary,
                                labelColor = Color.LightGray
                            ),
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (title.isBlank()) {
                        showTitleError = true
                        return@Button
                    }
                    val odo = targetOdoText.toSafeDouble(0.0).takeIf { it > 0 }
                    onSave(
                        ReminderEntity(
                            reminderId = existing?.reminderId ?: 0L,
                            motorcycleId = existing?.motorcycleId ?: activeBike.id,
                            title = title.trim(),
                            targetOdometerKm = odo,
                            targetDateIso = targetDateIso.trim().takeIf { it.isNotBlank() },
                            isCompleted = existing?.isCompleted ?: false
                        )
                    )
                },
                colors = ButtonDefaults.buttonColors(containerColor = RacingAmberPrimary)
            ) {
                Text(tr("Save Reminder", "Uložiť pripomienku"), fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(tr("Cancel", "Zrušiť"), color = Color.Gray) }
        }
    )
}

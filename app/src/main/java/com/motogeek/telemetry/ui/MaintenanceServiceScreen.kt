package com.motogeek.telemetry.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.motogeek.telemetry.data.local.*
import com.motogeek.telemetry.domain.maintenance.MaintenanceTaskStatus
import com.motogeek.telemetry.domain.maintenance.TaskStatus
import com.motogeek.telemetry.domain.toSafeDouble
import com.motogeek.telemetry.ui.theme.*
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun MaintenanceServiceScreen(viewModel: MainViewModel, navController: NavController) {
    val activeBike by viewModel.activeMotorcycle.collectAsState()
    val serviceLogs by viewModel.currentServiceLogs.collectAsState()
    val allTasks by produceState(initialValue = emptyList<MaintenanceTaskEntity>(), activeBike) {
        activeBike?.let {
            viewModel.repository.getMaintenanceTasksForMotorcycleFlow(it.id).collect { t ->
                value = t
            }
        }
    }

    var selectedTabIndex by remember { mutableStateOf(0) }
    val tabs = listOf("CHECKLIST & RECENT", "SCHEDULED INTERVALS", "SERVICE HISTORY")

    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    var showServiceDialog by remember { mutableStateOf(false) }
    var prefilledTaskName by remember { mutableStateOf("") }
    var prefilledTaskId by remember { mutableStateOf("GENERAL_SERVICE") }
    var prefilledLimitKm by remember { mutableStateOf(5000.0) }

    // Evaluated tasks for Tab 2
    val evaluatedTasks = remember(activeBike, allTasks, serviceLogs) {
        if (activeBike != null) {
            viewModel.evaluator.evaluate(activeBike!!, allTasks, serviceLogs)
        } else emptyList()
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Maintenance Service Hub",
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
                    TextButton(onClick = {
                        prefilledTaskName = "Periodic Inspection"
                        prefilledTaskId = "PERIODIC_INSPECTION"
                        prefilledLimitKm = 5000.0
                        showServiceDialog = true
                    }) {
                        Text("+ LOG SERVICE", color = RacingAmberPrimary, fontWeight = FontWeight.Bold)
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
        ) {
            // Tab Row
            TabRow(
                selectedTabIndex = selectedTabIndex,
                containerColor = TftSurfaceDark,
                contentColor = RacingAmberPrimary,
                indicator = { tabPositions ->
                    TabRowDefaults.SecondaryIndicator(
                        Modifier.tabIndicatorOffset(tabPositions[selectedTabIndex]),
                        color = RacingAmberPrimary
                    )
                }
            ) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTabIndex == index,
                        onClick = { selectedTabIndex = index },
                        text = {
                            Text(
                                text = title,
                                fontSize = 11.sp,
                                fontWeight = if (selectedTabIndex == index) FontWeight.Bold else FontWeight.Normal,
                                color = if (selectedTabIndex == index) RacingAmberPrimary else Color.Gray
                            )
                        }
                    )
                }
            }

            if (activeBike == null) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No motorcycle selected", color = Color.Gray)
                }
                return@Scaffold
            }

            when (selectedTabIndex) {
                0 -> ChecklistAndLastServiceTab(
                    activeBike = activeBike!!,
                    latestService = serviceLogs.firstOrNull(),
                    onNewInspection = {
                        prefilledTaskName = "16-Point Inspection"
                        prefilledTaskId = "INSPECTION_16_POINT"
                        prefilledLimitKm = 5000.0
                        showServiceDialog = true
                    }
                )
                1 -> ScheduledIntervalsTab(
                    activeBike = activeBike!!,
                    evaluatedTasks = evaluatedTasks,
                    onCompleteTask = { task ->
                        prefilledTaskName = task.taskName
                        prefilledTaskId = task.taskId
                        prefilledLimitKm = task.intervalKm ?: 5000.0
                        showServiceDialog = true
                    },
                    onAddTask = { newTask ->
                        scope.launch {
                            viewModel.repository.insertMaintenanceTask(newTask)
                            snackbarHostState.showSnackbar("Task added to schedule")
                        }
                    }
                )
                2 -> ServiceHistoryTab(
                    serviceLogs = serviceLogs,
                    currency = activeBike!!.currencySymbol,
                    onDelete = { logId ->
                        scope.launch {
                            viewModel.deleteServiceLog(logId)
                            snackbarHostState.showSnackbar("Service record deleted")
                        }
                    }
                )
            }
        }
    }

    if (showServiceDialog && activeBike != null) {
        RecordServiceDialog(
            activeBike = activeBike!!,
            initialTitle = prefilledTaskName,
            initialTaskId = prefilledTaskId,
            initialLimitKm = prefilledLimitKm,
            onDismiss = { showServiceDialog = false },
            onSave = { entity ->
                viewModel.addServiceLog(entity)
                showServiceDialog = false
                scope.launch { snackbarHostState.showSnackbar("Service recorded successfully") }
            }
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ChecklistAndLastServiceTab(
    activeBike: MotorcycleEntity,
    latestService: ServiceLogEntity?,
    onNewInspection: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        // Hero Card: Latest Inspection
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(10.dp),
            colors = CardDefaults.cardColors(containerColor = TftSurfaceDark),
            border = BorderStroke(1.dp, TitaniumBorder)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = latestService?.serviceTitle ?: "No Inspection Recorded",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 17.sp
                        )
                        Text(
                            text = latestService?.let { "Performed on ${it.performedAtIsoDate} • ${it.locationOrShop}" }
                                ?: "Tap below to log your first 16-point service",
                            color = Color.Gray,
                            fontSize = 12.sp
                        )
                    }

                    if (latestService != null && latestService.costEur > 0) {
                        Text(
                            text = "${latestService.costEur.toInt()} ${activeBike.currencySymbol}",
                            color = RacingAmberPrimary,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Black,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Mileage Progress Bar
                if (latestService != null) {
                    val servicedKm = latestService.odometerAtServiceKm
                    val limitKm = latestService.limitKm.takeIf { it > 0 } ?: 5000.0
                    val nextDueKm = servicedKm + limitKm
                    val currentKm = activeBike.totalOdometerKm
                    val kmSinceService = (currentKm - servicedKm).coerceAtLeast(0.0)
                    val progress = (kmSinceService / limitKm).coerceIn(0.0, 1.0).toFloat()

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Serviced: ${servicedKm.toInt()} km", color = ApexEmeraldGreen, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        Text("Interval: ${limitKm.toInt()} km", color = TelemetryIceCyan, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        Text("Next: ${nextDueKm.toInt()} km", color = if (currentKm >= nextDueKm) RedlineCrimson else WarningGold, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp),
                        color = if (progress >= 1f) RedlineCrimson else if (progress >= 0.8f) WarningGold else ApexEmeraldGreen,
                        trackColor = TitaniumBorder
                    )

                    Spacer(modifier = Modifier.height(14.dp))
                }

                // 16-Point Inspection Badges
                Text(
                    text = "16-POINT SYSTEM INSPECTION STATUS",
                    color = Color(0xFF6B7A99),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    letterSpacing = 1.sp
                )

                Spacer(modifier = Modifier.height(8.dp))

                val checklistItems = latestService?.parseChecklist() ?: DefaultServiceChecklistItems

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    checklistItems.forEach { item ->
                        val badgeColor = when (item.status) {
                            "CHECKED" -> ApexEmeraldGreen
                            "WARNING" -> WarningGold
                            "SKIPPED" -> Color.Gray
                            else -> ApexEmeraldGreen
                        }

                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = badgeColor.copy(alpha = 0.12f),
                            border = BorderStroke(1.dp, badgeColor.copy(alpha = 0.5f))
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .background(badgeColor, RoundedCornerShape(3.dp))
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = item.name,
                                    color = badgeColor,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Button(
                    onClick = onNewInspection,
                    colors = ButtonDefaults.buttonColors(containerColor = RacingAmberPrimary),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Checklist, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(if (latestService != null) "Repeat / Edit 16-Point Inspection" else "Log New 16-Point Inspection", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun ScheduledIntervalsTab(
    activeBike: MotorcycleEntity,
    evaluatedTasks: List<MaintenanceTaskStatus>,
    onCompleteTask: (MaintenanceTaskEntity) -> Unit,
    onAddTask: (MaintenanceTaskEntity) -> Unit
) {
    var selectedCategoryFilter by remember { mutableStateOf("ALL") }
    var showAddTaskDialog by remember { mutableStateOf(false) }

    val categories = listOf("ALL", "OVERDUE/SOON", "ENGINE", "DRIVETRAIN", "BRAKES", "TIRES", "SUSPENSION")

    val filtered = evaluatedTasks.filter { status ->
        when (selectedCategoryFilter) {
            "ALL" -> true
            "OVERDUE/SOON" -> status.status == TaskStatus.OVERDUE || status.status == TaskStatus.DUE_SOON
            else -> status.task.category.equals(selectedCategoryFilter, ignoreCase = true)
        }
    }.sortedByDescending { it.status }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Filter Row + Add Task Action
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.weight(1f)
            ) {
                items(categories) { cat ->
                    FilterChip(
                        selected = selectedCategoryFilter == cat,
                        onClick = { selectedCategoryFilter = cat },
                        label = { Text(cat, fontSize = 11.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = RacingAmberPrimary.copy(alpha = 0.2f),
                            selectedLabelColor = RacingAmberPrimary
                        )
                    )
                }
            }
            IconButton(onClick = { showAddTaskDialog = true }) {
                Icon(Icons.Default.Add, contentDescription = "Add Task", tint = RacingAmberPrimary)
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        if (filtered.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No maintenance tasks match filter.", color = Color.Gray)
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(bottom = 32.dp)
            ) {
                items(filtered) { status ->
                    ScheduledTaskCard(status = status, onComplete = { onCompleteTask(status.task) })
                }
            }
        }
    }

    if (showAddTaskDialog) {
        AddScheduleTaskDialog(
            motorcycleId = activeBike.id,
            onDismiss = { showAddTaskDialog = false },
            onSave = { task ->
                onAddTask(task)
                showAddTaskDialog = false
            }
        )
    }
}

@Composable
private fun ScheduledTaskCard(
    status: MaintenanceTaskStatus,
    onComplete: () -> Unit
) {
    val statusColor = when (status.status) {
        TaskStatus.OVERDUE -> RedlineCrimson
        TaskStatus.DUE_SOON -> WarningGold
        TaskStatus.OK -> ApexEmeraldGreen
    }

    val animatedProgress by animateFloatAsState(targetValue = status.progressFraction, label = "progress")

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = TftSurfaceDark),
        border = BorderStroke(1.dp, if (status.status == TaskStatus.OVERDUE) RedlineCrimson else TitaniumBorder)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = status.task.taskName,
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                    Text(
                        text = "${status.task.category.uppercase()} • ACTION: ${status.task.actionType.uppercase()}",
                        color = Color.Gray,
                        fontSize = 11.sp
                    )
                }

                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = statusColor.copy(alpha = 0.15f),
                    border = BorderStroke(1.dp, statusColor.copy(alpha = 0.5f))
                ) {
                    Text(
                        text = status.status.name.replace("_", " "),
                        color = statusColor,
                        fontWeight = FontWeight.Bold,
                        fontSize = 10.sp,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            val remainingKm = status.remainingKm?.let { "${it.toInt()} km" } ?: "N/A"
            val remainingDays = status.remainingDays?.let { "$it days" } ?: "N/A"
            val dueAt = status.nextDueOdometerKm?.let { "Due at ${it.toInt()} km" } ?: ""

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Remaining: $remainingKm / $remainingDays",
                    color = Color.LightGray,
                    fontSize = 12.sp
                )
                if (dueAt.isNotBlank()) {
                    Text(text = dueAt, color = Color.Gray, fontSize = 11.sp)
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            LinearProgressIndicator(
                progress = { animatedProgress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp),
                color = statusColor,
                trackColor = TitaniumBorder
            )

            if (status.task.notes.isNotBlank()) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Specs / Notes: ${status.task.notes}",
                    color = Color(0xFF8E9BB0),
                    fontSize = 11.sp
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                Button(
                    onClick = onComplete,
                    colors = ButtonDefaults.buttonColors(containerColor = RacingAmberPrimary),
                    shape = RoundedCornerShape(6.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                    modifier = Modifier.height(34.dp)
                ) {
                    Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Complete Task", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun ServiceHistoryTab(
    serviceLogs: List<ServiceLogEntity>,
    currency: String,
    onDelete: (String) -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }

    val filtered = serviceLogs.filter {
        it.serviceTitle.contains(searchQuery, ignoreCase = true) ||
        it.locationOrShop.contains(searchQuery, ignoreCase = true) ||
        it.notes.contains(searchQuery, ignoreCase = true)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = { Text("Search service history...", color = Color.Gray) },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = Color.Gray) },
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = RacingAmberPrimary,
                unfocusedBorderColor = TitaniumBorder,
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White
            ),
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(12.dp))

        if (filtered.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No service logs found.", color = Color.Gray)
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(bottom = 32.dp)
            ) {
                items(filtered) { log ->
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
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = log.serviceTitle,
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp
                                )
                                Text(
                                    text = "${log.performedAtIsoDate} • ${log.odometerAtServiceKm.toInt()} km • ${log.locationOrShop}",
                                    color = Color.Gray,
                                    fontSize = 12.sp
                                )
                                if (log.notes.isNotBlank()) {
                                    Text(
                                        text = log.notes,
                                        color = Color.LightGray,
                                        fontSize = 11.sp,
                                        modifier = Modifier.padding(top = 2.dp)
                                    )
                                }
                                if (log.costEur > 0) {
                                    Text(
                                        text = "Cost: ${log.costEur} $currency",
                                        color = RacingAmberPrimary,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }

                            IconButton(onClick = { onDelete(log.logId) }) {
                                Icon(Icons.Default.Delete, contentDescription = "Delete", tint = RedlineCrimson)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RecordServiceDialog(
    activeBike: MotorcycleEntity,
    initialTitle: String,
    initialTaskId: String,
    initialLimitKm: Double,
    onDismiss: () -> Unit,
    onSave: (ServiceLogEntity) -> Unit
) {
    var title by remember { mutableStateOf(initialTitle) }
    var odoText by remember { mutableStateOf(activeBike.totalOdometerKm.toInt().toString()) }
    var limitKmText by remember { mutableStateOf(initialLimitKm.toInt().toString()) }
    var costText by remember { mutableStateOf("0") }
    var location by remember { mutableStateOf("Home Garage") }
    var notes by remember { mutableStateOf("") }

    // 16 inspection items
    val checklistState = remember {
        mutableStateMapOf<String, String>().apply {
            DefaultServiceChecklistNames.forEach { put(it, "CHECKED") }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = TftSurfaceDark,
        title = { Text("Record Service Log", color = Color.White, fontWeight = FontWeight.Bold) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Service Title") },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = RacingAmberPrimary,
                        unfocusedBorderColor = TitaniumBorder,
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = odoText,
                        onValueChange = { odoText = it },
                        label = { Text("Odo (km)") },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = RacingAmberPrimary,
                            unfocusedBorderColor = TitaniumBorder,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        ),
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = limitKmText,
                        onValueChange = { limitKmText = it },
                        label = { Text("Limit (km)") },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = RacingAmberPrimary,
                            unfocusedBorderColor = TitaniumBorder,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        ),
                        modifier = Modifier.weight(1f)
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = costText,
                        onValueChange = { costText = it },
                        label = { Text("Cost (${activeBike.currencySymbol})") },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = RacingAmberPrimary,
                            unfocusedBorderColor = TitaniumBorder,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        ),
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = location,
                        onValueChange = { location = it },
                        label = { Text("Shop / Location") },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = RacingAmberPrimary,
                            unfocusedBorderColor = TitaniumBorder,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        ),
                        modifier = Modifier.weight(1f)
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Notes / Torques") },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = RacingAmberPrimary,
                        unfocusedBorderColor = TitaniumBorder,
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(14.dp))
                Text(
                    text = "16-POINT CHECKLIST (Tap to cycle status)",
                    color = Color.Gray,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(6.dp))

                checklistState.keys.toList().chunked(2).forEach { pair ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        pair.forEach { item ->
                            val currentStatus = checklistState[item] ?: "CHECKED"
                            val color = when (currentStatus) {
                                "CHECKED" -> ApexEmeraldGreen
                                "WARNING" -> WarningGold
                                "SKIPPED" -> Color.Gray
                                else -> ApexEmeraldGreen
                            }

                            Surface(
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable {
                                        checklistState[item] = when (currentStatus) {
                                            "CHECKED" -> "WARNING"
                                            "WARNING" -> "SKIPPED"
                                            else -> "CHECKED"
                                        }
                                    },
                                shape = RoundedCornerShape(4.dp),
                                color = color.copy(alpha = 0.15f),
                                border = BorderStroke(1.dp, color.copy(alpha = 0.6f))
                            ) {
                                Text(
                                    text = "$item ($currentStatus)",
                                    color = color,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(4.dp)
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val odo = odoText.toSafeDouble(activeBike.totalOdometerKm)
                    val limit = limitKmText.toSafeDouble(5000.0)
                    val cost = costText.toSafeDouble(0.0)
                    val checklistJson = Json.encodeToString(
                        checklistState.map { ServiceChecklistItem(it.key, it.value) }
                    )

                    val entity = ServiceLogEntity(
                        logId = UUID.randomUUID().toString(),
                        motorcycleId = activeBike.id,
                        taskId = initialTaskId,
                        serviceTitle = title.ifBlank { "Periodic Service" },
                        locationOrShop = location.ifBlank { "Home Garage" },
                        performedAtIsoDate = LocalDate.now().format(DateTimeFormatter.ISO_DATE),
                        odometerAtServiceKm = odo,
                        servicedAtKm = odo,
                        limitKm = limit,
                        endKm = odo + limit,
                        costEur = cost,
                        performedBy = "Self",
                        notes = notes,
                        checklistItemsJson = checklistJson
                    )
                    onSave(entity)
                },
                colors = ButtonDefaults.buttonColors(containerColor = RacingAmberPrimary)
            ) {
                Text("Save Service Log", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = Color.Gray) }
        }
    )
}

@Composable
private fun AddScheduleTaskDialog(
    motorcycleId: String,
    onDismiss: () -> Unit,
    onSave: (MaintenanceTaskEntity) -> Unit
) {
    var taskName by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("Engine") }
    var actionType by remember { mutableStateOf("Replace") }
    var intervalKm by remember { mutableStateOf("10000") }
    var intervalMonths by remember { mutableStateOf("12") }
    var notes by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = TftSurfaceDark,
        title = { Text("Add Maintenance Task", color = Color.White, fontWeight = FontWeight.Bold) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                OutlinedTextField(
                    value = taskName,
                    onValueChange = { taskName = it },
                    label = { Text("Task Name") },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = RacingAmberPrimary,
                        unfocusedBorderColor = TitaniumBorder,
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = category,
                    onValueChange = { category = it },
                    label = { Text("Category (Engine, Brakes, Tires)") },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = RacingAmberPrimary,
                        unfocusedBorderColor = TitaniumBorder,
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = actionType,
                    onValueChange = { actionType = it },
                    label = { Text("Action (Replace, Inspect, Lube)") },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = RacingAmberPrimary,
                        unfocusedBorderColor = TitaniumBorder,
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = intervalKm,
                        onValueChange = { intervalKm = it },
                        label = { Text("Interval (km)") },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = RacingAmberPrimary,
                            unfocusedBorderColor = TitaniumBorder,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        ),
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = intervalMonths,
                        onValueChange = { intervalMonths = it },
                        label = { Text("Interval (mo)") },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = RacingAmberPrimary,
                            unfocusedBorderColor = TitaniumBorder,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        ),
                        modifier = Modifier.weight(1f)
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Notes / Torques (Nm)") },
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
                    val task = MaintenanceTaskEntity(
                        taskId = UUID.randomUUID().toString(),
                        motorcycleId = motorcycleId,
                        category = category.ifBlank { "General" },
                        taskName = taskName.ifBlank { "Periodic Check" },
                        actionType = actionType.ifBlank { "Inspect" },
                        firstBreakInAtKm = 1000.0,
                        intervalKm = intervalKm.toSafeDouble(10000.0),
                        intervalMonths = intervalMonths.toIntOrNull() ?: 12,
                        warningThresholdKm = 500.0,
                        warningThresholdDays = 30,
                        oemPartNumbersJson = "[]",
                        notes = notes
                    )
                    onSave(task)
                },
                colors = ButtonDefaults.buttonColors(containerColor = RacingAmberPrimary)
            ) {
                Text("Add Task", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = Color.Gray) }
        }
    )
}

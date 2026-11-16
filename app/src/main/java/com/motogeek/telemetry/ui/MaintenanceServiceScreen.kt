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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Info
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
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.navigation.NavController
import com.motogeek.telemetry.data.local.*
import com.motogeek.telemetry.domain.maintenance.GeneralWorkshopCheckTemplate
import com.motogeek.telemetry.domain.maintenance.MaintenanceTaskStatus
import com.motogeek.telemetry.domain.maintenance.MotorcycleTemplateCatalog
import com.motogeek.telemetry.domain.maintenance.ServiceChecklistCatalog
import com.motogeek.telemetry.domain.maintenance.ServiceSubOption
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
    val appLang = LocalAppLang.current
    val tabs = listOf(
        tr("CHECKLIST & RECENT", "KONTROLA A POSLEDNÝ"),
        tr("SCHEDULED INTERVALS", "SERVISNÉ INTERVALY"),
        tr("SERVICE HISTORY", "HISTÓRIA SERVISU")
    )

    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    var showServiceDialog by remember { mutableStateOf(false) }
    var editingServiceLog by remember { mutableStateOf<ServiceLogEntity?>(null) }
    var prefilledTaskName by remember { mutableStateOf("") }
    var prefilledTaskId by remember { mutableStateOf("GENERAL_SERVICE") }
    var prefilledLimitKm by remember { mutableStateOf(5000.0) }
    var prefilledNotes by remember { mutableStateOf("") }
    var isLaunchedFromCompleteTask by remember { mutableStateOf(false) }

    val activeSpecs = remember(activeBike?.technicalSpecsJson) {
        try {
            val raw = activeBike?.technicalSpecsJson
            if (!raw.isNullOrBlank() && raw != "{}") {
                Json { ignoreUnknownKeys = true; isLenient = true }.decodeFromString<com.motogeek.telemetry.domain.maintenance.TechnicalSpecsDto>(raw)
            } else null
        } catch (_: Exception) {
            null
        }
    }

    // Evaluated tasks for Tab 1 & Service dialog
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
                        text = tr("Maintenance Service Hub", "Servis a údržba"),
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
                    TextButton(onClick = { viewModel.toggleAppLanguage() }) {
                        Text(
                            text = if (appLang == AppLang.EN) "EN ▸ SK" else "SK ▸ EN",
                            color = TelemetryIceCyan,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp
                        )
                    }
                    TextButton(onClick = {
                        editingServiceLog = null
                        prefilledTaskName = if (appLang == AppLang.SK) "Pravidelný servis a kontrola" else "Periodic Service & Inspection"
                        prefilledTaskId = "GENERAL_SERVICE"
                        prefilledLimitKm = 5000.0
                        prefilledNotes = ""
                        isLaunchedFromCompleteTask = false
                        showServiceDialog = true
                    }) {
                        Text(
                            text = tr("+ LOG SERVICE", "+ ZAPÍSAŤ SERVIS"),
                            color = RacingAmberPrimary,
                            fontWeight = FontWeight.Bold
                        )
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
                    Text(tr("No motorcycle selected", "Nie je vybraný žiadny motocykel"), color = Color.Gray)
                }
                return@Scaffold
            }

            when (selectedTabIndex) {
                0 -> ChecklistAndLastServiceTab(
                    activeBike = activeBike!!,
                    latestService = serviceLogs.firstOrNull(),
                    allTasks = allTasks,
                    onNewInspection = {
                        editingServiceLog = null
                        prefilledTaskName = if (appLang == AppLang.SK) "16-bodová kontrola" else "16-Point Inspection"
                        prefilledTaskId = "INSPECTION_16_POINT"
                        prefilledLimitKm = 5000.0
                        prefilledNotes = ""
                        isLaunchedFromCompleteTask = false
                        showServiceDialog = true
                    }
                )
                1 -> ScheduledIntervalsTab(
                    activeBike = activeBike!!,
                    specs = activeSpecs,
                    evaluatedTasks = evaluatedTasks,
                    onCompleteTask = { task ->
                        editingServiceLog = null
                        prefilledTaskName = task.taskName
                        prefilledTaskId = task.taskId
                        prefilledLimitKm = task.intervalKm ?: 5000.0
                        prefilledNotes = task.notes
                        isLaunchedFromCompleteTask = true
                        showServiceDialog = true
                    },
                    onAddTask = { newTask ->
                        scope.launch {
                            viewModel.repository.insertMaintenanceTask(newTask)
                            snackbarHostState.showSnackbar(appLang.pick("Schedule updated", "Servisný plán aktualizovaný"))
                        }
                    },
                    onDeleteTask = { task ->
                        scope.launch {
                            viewModel.deleteMaintenanceTask(task.taskId, activeBike!!.id)
                            snackbarHostState.showSnackbar(
                                appLang.pick("Removed %s from schedule", "%s odstránený z plánu").format(task.taskName)
                            )
                        }
                    }
                )
                2 -> ServiceHistoryTab(
                    serviceLogs = serviceLogs,
                    allTasks = allTasks,
                    currency = activeBike!!.currencySymbol,
                    onEdit = { log ->
                        editingServiceLog = log
                        prefilledTaskName = log.serviceTitle
                        prefilledTaskId = log.taskId
                        prefilledLimitKm = log.limitKm
                        prefilledNotes = log.notes
                        isLaunchedFromCompleteTask = false
                        showServiceDialog = true
                    },
                    onDelete = { logId ->
                        scope.launch {
                            viewModel.deleteServiceLog(logId)
                            snackbarHostState.showSnackbar(appLang.pick("Service record deleted", "Servisný záznam zmazaný"))
                        }
                    }
                )
            }
        }
    }

    if (showServiceDialog && activeBike != null) {
        RecordServiceDialog(
            activeBike = activeBike!!,
            existingLog = editingServiceLog,
            initialTitle = prefilledTaskName,
            initialTaskId = prefilledTaskId,
            initialLimitKm = prefilledLimitKm,
            initialNotes = prefilledNotes,
            evaluatedTasks = evaluatedTasks,
            allTasks = allTasks,
            specs = activeSpecs,
            launchedFromCompleteTask = isLaunchedFromCompleteTask,
            onDismiss = {
                showServiceDialog = false
                editingServiceLog = null
                isLaunchedFromCompleteTask = false
            },
            onSave = { entity ->
                if (editingServiceLog != null) {
                    viewModel.updateServiceLog(entity)
                    scope.launch { snackbarHostState.showSnackbar(appLang.pick("Service record updated", "Servisný záznam aktualizovaný")) }
                } else {
                    viewModel.addServiceLog(entity)
                    scope.launch { snackbarHostState.showSnackbar(appLang.pick("Service recorded successfully", "Servis úspešne zaznamenaný")) }
                }
                showServiceDialog = false
                editingServiceLog = null
                isLaunchedFromCompleteTask = false
            }
        )
    }
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun ChecklistAndLastServiceTab(
    activeBike: MotorcycleEntity,
    latestService: ServiceLogEntity?,
    allTasks: List<MaintenanceTaskEntity>,
    onNewInspection: () -> Unit
) {
    val appLang = LocalAppLang.current

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
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = latestService?.serviceTitle ?: tr("No Inspection Recorded", "Žiadny servis nezaznamenaný"),
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 17.sp
                        )
                        Text(
                            text = latestService?.let {
                                tr("Performed on %s • %s", "Vykonané dňa %s • %s").format(it.performedAtIsoDate, it.locationOrShop)
                            } ?: tr("Tap below to log your first service", "Klepnutím nižšie zapíšete svoj prvý servis"),
                            color = Color.Gray,
                            fontSize = 12.sp
                        )
                    }

                    if (latestService != null && latestService.costEur > 0) {
                        Spacer(modifier = Modifier.width(8.dp))
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
                        Text(
                            text = tr("Serviced: %d km", "Servis: %d km").format(servicedKm.toInt()),
                            color = ApexEmeraldGreen,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = tr("Interval: %d km", "Interval: %d km").format(limitKm.toInt()),
                            color = TelemetryIceCyan,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = tr("Next: %d km", "Ďalší: %d km").format(nextDueKm.toInt()),
                            color = if (currentKm >= nextDueKm) RedlineCrimson else WarningGold,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
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

                // Checklist Section Header
                Text(
                    text = tr("RECORDED SERVICE & INSPECTION CHECKLIST", "KONTROLNÝ ZOZNAM VYKONANÉHO SERVISU"),
                    color = Color(0xFF6B7A99),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    letterSpacing = 1.sp
                )

                Spacer(modifier = Modifier.height(8.dp))

                val recordedItems = remember(latestService) { latestService?.parseChecklistOrEmpty() ?: emptyList() }

                if (recordedItems.isNotEmpty()) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        recordedItems.forEach { item ->
                            val statusColor = if (item.completesTask || item.status.equals("DONE", ignoreCase = true)) {
                                ApexEmeraldGreen
                            } else if (item.status.equals("WARNING", ignoreCase = true)) {
                                WarningGold
                            } else {
                                TelemetryIceCyan
                            }

                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = Color(0xFF141923),
                                border = BorderStroke(1.dp, TitaniumBorder.copy(alpha = 0.5f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(8.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = ServiceChecklistCatalog.localizeItemName(item, allTasks, appLang.code),
                                            color = Color.White,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 12.sp
                                        )
                                        Surface(
                                            shape = RoundedCornerShape(4.dp),
                                            color = statusColor.copy(alpha = 0.15f),
                                            border = BorderStroke(1.dp, statusColor.copy(alpha = 0.5f))
                                        ) {
                                            Text(
                                                text = ServiceChecklistCatalog.localizeStatus(item.status, item.completesTask, appLang.code),
                                                color = statusColor,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 9.sp,
                                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                                            )
                                        }
                                    }

                                    if (item.subActions.isNotEmpty()) {
                                        Spacer(modifier = Modifier.height(3.dp))
                                        Text(
                                            text = item.subActions.map { ServiceChecklistCatalog.localizeSubAction(it, appLang.code) }.joinToString(" • "),
                                            color = TelemetryIceCyan,
                                            fontSize = 11.sp
                                        )
                                    }

                                    if (item.note.isNotBlank()) {
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = "↳ ${item.note}",
                                            color = Color.LightGray,
                                            fontSize = 10.sp
                                        )
                                    }
                                }
                            }
                        }
                    }
                } else {
                    // Fallback to default items if no checklist recorded
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        val itemsToRender = latestService?.parseChecklist() ?: DefaultServiceChecklistItems
                        itemsToRender.forEach { item ->
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
                                        text = ServiceChecklistCatalog.localizeItemName(item, allTasks, appLang.code),
                                        color = badgeColor,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
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
                    Text(
                        text = if (latestService != null) tr("Repeat / Edit Service", "Zopakovať / upraviť servis") else tr("Log New Service", "Zapísať nový servis"),
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
private fun ScheduledIntervalsTab(
    activeBike: MotorcycleEntity,
    specs: com.motogeek.telemetry.domain.maintenance.TechnicalSpecsDto?,
    evaluatedTasks: List<MaintenanceTaskStatus>,
    onCompleteTask: (MaintenanceTaskEntity) -> Unit,
    onAddTask: (MaintenanceTaskEntity) -> Unit,
    onDeleteTask: (MaintenanceTaskEntity) -> Unit
) {
    var selectedCategoryFilter by remember { mutableStateOf("ALL") }
    var showAddTaskDialog by remember { mutableStateOf(false) }
    var editingTask by remember { mutableStateOf<MaintenanceTaskEntity?>(null) }

    val categories = listOf("ALL", "OVERDUE/SOON", "ENGINE", "DRIVETRAIN", "BRAKES", "TIRES", "COOLING", "IGNITION")

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
        val appLang = LocalAppLang.current

        Button(
            onClick = {
                val newTasks = MotorcycleTemplateCatalog.buildStandardMaintenanceTasks(specs, appLang.code)
                newTasks.forEach { taskDto ->
                    onAddTask(
                        MaintenanceTaskEntity(
                            taskId = taskDto.taskId,
                            motorcycleId = activeBike.id,
                            category = taskDto.category,
                            taskName = taskDto.taskName,
                            actionType = taskDto.actionType,
                            firstBreakInAtKm = taskDto.firstBreakInAtKm,
                            intervalKm = taskDto.intervalKm,
                            intervalMonths = taskDto.intervalMonths,
                            warningThresholdKm = taskDto.warningThresholdKm,
                            warningThresholdDays = taskDto.warningThresholdDays,
                            notes = taskDto.notes,
                            oemPartNumbersJson = "[]"
                        )
                    )
                }
            },
            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
        ) {
            Text(
                tr(
                    "+ Load Standard Factory Schedule (Oil, Coolant, Brakes, Tires, Valves)",
                    "+ Štandardné úkony (Chladiaca, Olej, Brzdy, Pneu, Ventily)"
                ),
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp
            )
        }

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
                    val label = when (cat) {
                        "ALL" -> tr("ALL", "VŠETKO")
                        "OVERDUE/SOON" -> tr("OVERDUE/SOON", "PO TERMÍNE / ČOSKORO")
                        "ENGINE" -> tr("ENGINE", "MOTOR")
                        "DRIVETRAIN" -> tr("DRIVETRAIN", "POHON")
                        "BRAKES" -> tr("BRAKES", "BRZDY")
                        "TIRES" -> tr("TIRES", "PNEUMATIKY")
                        "COOLING" -> tr("COOLING", "CHLADENIE")
                        "IGNITION" -> tr("IGNITION", "ZAPAĽOVANIE")
                        else -> cat
                    }
                    FilterChip(
                        selected = selectedCategoryFilter == cat,
                        onClick = { selectedCategoryFilter = cat },
                        label = { Text(label, fontSize = 11.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = RacingAmberPrimary.copy(alpha = 0.2f),
                            selectedLabelColor = RacingAmberPrimary
                        )
                    )
                }
            }
            IconButton(onClick = {
                editingTask = null
                showAddTaskDialog = true
            }) {
                Icon(Icons.Default.Add, contentDescription = tr("Add Task", "Pridať úkon"), tint = RacingAmberPrimary)
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        if (filtered.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(tr("No maintenance tasks match filter.", "Žiadne úkony nevyhovujú filtru."), color = Color.Gray)
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(bottom = 32.dp)
            ) {
                items(filtered) { status ->
                    ScheduledTaskCard(
                        status = status,
                        specs = specs,
                        onComplete = { onCompleteTask(status.task) },
                        onEdit = {
                            editingTask = status.task
                            showAddTaskDialog = true
                        },
                        onDelete = { onDeleteTask(status.task) }
                    )
                }
            }
        }
    }

    if (showAddTaskDialog) {
        AddScheduleTaskDialog(
            motorcycleId = activeBike.id,
            existingTask = editingTask,
            onDismiss = {
                showAddTaskDialog = false
                editingTask = null
            },
            onSave = { task ->
                onAddTask(task)
                showAddTaskDialog = false
                editingTask = null
            }
        )
    }
}

@Composable
private fun ScheduledTaskCard(
    status: MaintenanceTaskStatus,
    specs: com.motogeek.telemetry.domain.maintenance.TechnicalSpecsDto?,
    onComplete: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val statusColor = when (status.status) {
        TaskStatus.OVERDUE -> RedlineCrimson
        TaskStatus.DUE_SOON -> WarningGold
        TaskStatus.OK -> ApexEmeraldGreen
    }
    var showGuideExpanded by remember { mutableStateOf(false) }

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
                        text = "${status.task.category.uppercase()} • " + tr("ACTION: %s", "ÚKON: %s").format(status.task.actionType.uppercase()),
                        color = Color.Gray,
                        fontSize = 11.sp
                    )
                }

                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = statusColor.copy(alpha = 0.15f),
                    border = BorderStroke(1.dp, statusColor.copy(alpha = 0.5f))
                ) {
                    val statusText = when (status.status) {
                        TaskStatus.OVERDUE -> tr("OVERDUE", "PO TERMÍNE")
                        TaskStatus.DUE_SOON -> tr("DUE SOON", "ČOSKORO")
                        TaskStatus.OK -> tr("OK", "V PORIADKU")
                    }
                    Text(
                        text = statusText,
                        color = statusColor,
                        fontWeight = FontWeight.Bold,
                        fontSize = 10.sp,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            val remainingKmStr = status.remainingKm?.let { "${it.toInt()} km" } ?: "N/A"
            val remainingDaysStr = status.remainingDays?.let { tr("$it days", "$it dní") } ?: "N/A"
            val dueAt = status.nextDueOdometerKm?.let { tr("Due at %d km", "Termín pri %d km").format(it.toInt()) } ?: ""

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = tr("Remaining: %s / %s", "Zostáva: %s / %s").format(remainingKmStr, remainingDaysStr),
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

            val appLang = LocalAppLang.current
            val procedureGuide = remember(status.task, specs, appLang) {
                MotorcycleTemplateCatalog.getProcedureGuideForTask(status.task.taskName, status.task.category, specs, appLang.code)
            }
            val finalNotes = procedureGuide.ifBlank { status.task.notes }

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    if (finalNotes.isNotBlank()) {
                        TextButton(
                            onClick = { showGuideExpanded = !showGuideExpanded },
                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Icon(
                                if (showGuideExpanded) Icons.Default.ExpandLess else Icons.Default.Build,
                                contentDescription = null,
                                tint = TelemetryIceCyan,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                if (showGuideExpanded) tr("Hide Repair Guide", "Skryť postup") else tr("Repair Guide & Torques", "Postup a momenty"),
                                color = TelemetryIceCyan,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                    IconButton(onClick = onEdit, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Default.Edit, contentDescription = tr("Edit task", "Upraviť úkon"), tint = Color.Gray, modifier = Modifier.size(15.dp))
                    }
                    IconButton(onClick = onDelete, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Default.Delete, contentDescription = tr("Delete task", "Zmazať úkon"), tint = RedlineCrimson.copy(alpha = 0.8f), modifier = Modifier.size(15.dp))
                    }
                }

                Button(
                    onClick = onComplete,
                    colors = ButtonDefaults.buttonColors(containerColor = RacingAmberPrimary),
                    shape = RoundedCornerShape(6.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                    modifier = Modifier.height(34.dp)
                ) {
                    Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(tr("Complete Task", "Hotovo"), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }

            if (showGuideExpanded && finalNotes.isNotBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF151A24), RoundedCornerShape(8.dp))
                        .padding(10.dp)
                ) {
                    Text(text = finalNotes, color = Color.LightGray, fontSize = 12.sp, lineHeight = 17.sp)
                }
            }
        }
    }
}

@Composable
private fun ServiceHistoryTab(
    serviceLogs: List<ServiceLogEntity>,
    allTasks: List<MaintenanceTaskEntity>,
    currency: String,
    onEdit: (ServiceLogEntity) -> Unit,
    onDelete: (String) -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    val appLang = LocalAppLang.current

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
            placeholder = { Text(tr("Search service history...", "Hľadať v histórii servisu..."), color = Color.Gray) },
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
                Text(tr("No service logs found.", "Nenašli sa žiadne servisné záznamy."), color = Color.Gray)
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(bottom = 32.dp)
            ) {
                items(filtered, key = { it.logId }) { log ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        colors = CardDefaults.cardColors(containerColor = TftSurfaceDark),
                        border = BorderStroke(1.dp, TitaniumBorder)
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
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
                                            text = tr("Cost: %s %s", "Cena: %s %s").format(log.costEur, currency),
                                            color = RacingAmberPrimary,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }

                                Row {
                                    IconButton(onClick = { onEdit(log) }) {
                                        Icon(Icons.Default.Edit, contentDescription = tr("Edit", "Upraviť"), tint = TelemetryIceCyan)
                                    }
                                    IconButton(onClick = { onDelete(log.logId) }) {
                                        Icon(Icons.Default.Delete, contentDescription = tr("Delete", "Zmazať"), tint = RedlineCrimson)
                                    }
                                }
                            }

                            // Recorded checklist items expandable section
                            val checklistItems = remember(log) { log.parseChecklistOrEmpty() }
                            if (checklistItems.isNotEmpty()) {
                                var expanded by remember { mutableStateOf(false) }
                                Spacer(modifier = Modifier.height(8.dp))
                                HorizontalDivider(color = TitaniumBorder.copy(alpha = 0.5f), thickness = 0.5.dp)
                                Spacer(modifier = Modifier.height(6.dp))

                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { expanded = !expanded },
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = tr(
                                            "${checklistItems.size} checklist items recorded",
                                            "${checklistItems.size} zaznamenaných úkonov"
                                        ),
                                        color = TelemetryIceCyan,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Icon(
                                        imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                        contentDescription = null,
                                        tint = TelemetryIceCyan,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }

                                if (expanded) {
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                        checklistItems.forEach { item ->
                                            val statusColor = if (item.completesTask || item.status.equals("DONE", ignoreCase = true)) {
                                                ApexEmeraldGreen
                                            } else if (item.status.equals("WARNING", ignoreCase = true)) {
                                                WarningGold
                                            } else {
                                                TelemetryIceCyan
                                            }

                                            Surface(
                                                shape = RoundedCornerShape(4.dp),
                                                color = Color(0xFF141923),
                                                border = BorderStroke(0.5.dp, TitaniumBorder),
                                                modifier = Modifier.fillMaxWidth()
                                            ) {
                                                Column(modifier = Modifier.padding(8.dp)) {
                                                    Row(
                                                        modifier = Modifier.fillMaxWidth(),
                                                        horizontalArrangement = Arrangement.SpaceBetween,
                                                        verticalAlignment = Alignment.CenterVertically
                                                    ) {
                                                        Text(
                                                            text = ServiceChecklistCatalog.localizeItemName(item, allTasks, appLang.code),
                                                            color = Color.White,
                                                            fontWeight = FontWeight.SemiBold,
                                                            fontSize = 12.sp
                                                        )
                                                        Surface(
                                                            shape = RoundedCornerShape(3.dp),
                                                            color = statusColor.copy(alpha = 0.15f),
                                                            border = BorderStroke(0.5.dp, statusColor.copy(alpha = 0.5f))
                                                        ) {
                                                            Text(
                                                                text = ServiceChecklistCatalog.localizeStatus(item.status, item.completesTask, appLang.code),
                                                                color = statusColor,
                                                                fontWeight = FontWeight.Bold,
                                                                fontSize = 9.sp,
                                                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                                            )
                                                        }
                                                    }
                                                    if (item.subActions.isNotEmpty()) {
                                                        Text(
                                                            text = item.subActions.map { ServiceChecklistCatalog.localizeSubAction(it, appLang.code) }.joinToString(" • "),
                                                            color = TelemetryIceCyan,
                                                            fontSize = 10.sp,
                                                            modifier = Modifier.padding(top = 2.dp)
                                                        )
                                                    }
                                                    if (item.note.isNotBlank()) {
                                                        Text(
                                                            text = "↳ ${item.note}",
                                                            color = Color.LightGray,
                                                            fontSize = 10.sp,
                                                            modifier = Modifier.padding(top = 2.dp)
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
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
    existingLog: ServiceLogEntity? = null,
    initialTitle: String,
    initialTaskId: String,
    initialLimitKm: Double,
    initialNotes: String = "",
    evaluatedTasks: List<MaintenanceTaskStatus>,
    allTasks: List<MaintenanceTaskEntity>,
    specs: com.motogeek.telemetry.domain.maintenance.TechnicalSpecsDto?,
    launchedFromCompleteTask: Boolean = false,
    onDismiss: () -> Unit,
    onSave: (ServiceLogEntity) -> Unit
) {
    val appLang = LocalAppLang.current

    var title by remember { mutableStateOf(existingLog?.serviceTitle ?: initialTitle) }
    var odoText by remember { mutableStateOf((existingLog?.odometerAtServiceKm ?: activeBike.totalOdometerKm).toInt().toString()) }
    var limitKmText by remember { mutableStateOf((existingLog?.limitKm ?: initialLimitKm).toInt().toString()) }
    var costText by remember { mutableStateOf((existingLog?.costEur ?: 0.0).toString()) }
    var location by remember { mutableStateOf(existingLog?.locationOrShop ?: if (appLang == AppLang.SK) "Domáca garáž" else "Home Garage") }
    var notes by remember { mutableStateOf(existingLog?.notes ?: (if (launchedFromCompleteTask) "" else initialNotes)) }

    // Section 1: Sorted evaluated tasks (pre-focused initialTaskId or OVERDUE first, then DUE_SOON, then OK)
    val sortedTasks = remember(evaluatedTasks, initialTaskId) {
        evaluatedTasks.sortedWith(
            compareBy<MaintenanceTaskStatus> {
                if (it.task.taskId.equals(initialTaskId, ignoreCase = true)) 0 else 1
            }.thenBy {
                when (it.status) {
                    TaskStatus.OVERDUE -> 0
                    TaskStatus.DUE_SOON -> 1
                    TaskStatus.OK -> 2
                }
            }
        )
    }

    // Section 2: Remaining general workshop checks not overlapping any existing task
    val remainingGeneralChecks = remember(allTasks) {
        ServiceChecklistCatalog.remainingGeneralChecks(allTasks)
    }

    // Map: itemKey -> Set<String> of selected sub-option keys
    val selectedSubKeysMap = remember(existingLog, launchedFromCompleteTask, initialTaskId) {
        mutableStateMapOf<String, Set<String>>().apply {
            if (existingLog != null) {
                val parsed = existingLog.parseChecklistOrEmpty()
                parsed.forEach { item ->
                    val key = item.taskId
                        ?: sortedTasks.firstOrNull { it.task.taskName.equals(item.name, ignoreCase = true) }?.task?.taskId
                        ?: remainingGeneralChecks.firstOrNull { it.nameEn.equals(item.name, ignoreCase = true) || it.nameSk.equals(item.name, ignoreCase = true) }?.key
                        ?: item.name

                    if (item.subActions.isNotEmpty()) {
                        put(key, item.subActions.toSet())
                    } else {
                        // Legacy checklist fallback
                        val matchingTask = allTasks.firstOrNull { it.taskId == key }
                        val actionType = matchingTask?.actionType ?: "Inspect"
                        val opts = ServiceChecklistCatalog.getSubOptionsFor(key, item.name, matchingTask?.category ?: "")
                        if (item.completesTask || item.status.equals("DONE", ignoreCase = true)) {
                            put(key, ServiceChecklistCatalog.defaultCompletionKeysForTask(actionType, opts))
                        } else if (item.status.equals("CHECKED", ignoreCase = true)) {
                            val inspectKeys = opts.filter { it.completesInspectionTask }.map { it.key }.toSet()
                            put(key, inspectKeys.ifEmpty { setOf(opts.first().key) })
                        }
                    }
                }
            } else if (launchedFromCompleteTask && initialTaskId.isNotBlank()) {
                val matchingTask = sortedTasks.firstOrNull { it.task.taskId == initialTaskId }?.task
                if (matchingTask != null) {
                    val opts = ServiceChecklistCatalog.getSubOptionsFor(matchingTask.taskId, matchingTask.taskName, matchingTask.category)
                    val defaultKeys = ServiceChecklistCatalog.defaultCompletionKeysForTask(matchingTask.actionType, opts)
                    put(initialTaskId, defaultKeys)
                }
            }
        }
    }

    // Map: itemKey -> per-task notes
    val taskNotesMap = remember(existingLog) {
        mutableStateMapOf<String, String>().apply {
            if (existingLog != null) {
                val parsed = existingLog.parseChecklistOrEmpty()
                parsed.forEach { item ->
                    val key = item.taskId
                        ?: sortedTasks.firstOrNull { it.task.taskName.equals(item.name, ignoreCase = true) }?.task?.taskId
                        ?: remainingGeneralChecks.firstOrNull { it.nameEn.equals(item.name, ignoreCase = true) || it.nameSk.equals(item.name, ignoreCase = true) }?.key
                        ?: item.name
                    if (item.note.isNotBlank()) {
                        put(key, item.note)
                    }
                }
            }
        }
    }

    // Map: itemKey -> manual completion flag (synced with sub-options)
    val manualDoneMap = remember(existingLog, launchedFromCompleteTask, initialTaskId) {
        mutableStateMapOf<String, Boolean>().apply {
            if (existingLog != null) {
                val parsed = existingLog.parseChecklistOrEmpty()
                parsed.forEach { item ->
                    val key = item.taskId
                        ?: sortedTasks.firstOrNull { it.task.taskName.equals(item.name, ignoreCase = true) }?.task?.taskId
                        ?: remainingGeneralChecks.firstOrNull { it.nameEn.equals(item.name, ignoreCase = true) || it.nameSk.equals(item.name, ignoreCase = true) }?.key
                        ?: item.name
                    if (item.completesTask || item.status.equals("DONE", ignoreCase = true)) {
                        put(key, true)
                    }
                }
            } else if (launchedFromCompleteTask && initialTaskId.isNotBlank()) {
                put(initialTaskId, true)
            }
        }
    }

    // Map: itemKey -> boolean inline repair guide expanded
    val guideExpandedMap = remember { mutableStateMapOf<String, Boolean>() }

    fun isTaskCompleted(key: String, actionType: String, subOptions: List<ServiceSubOption>): Boolean {
        val subKeys = selectedSubKeysMap[key] ?: emptySet()
        val manual = manualDoneMap[key] == true
        return manual || ServiceChecklistCatalog.evaluateCompletesTask(actionType, subKeys, subOptions)
    }

    fun toggleMainCompletion(key: String, actionType: String, subOptions: List<ServiceSubOption>, willBeDone: Boolean) {
        if (willBeDone) {
            manualDoneMap[key] = true
            val defaultKeys = ServiceChecklistCatalog.defaultCompletionKeysForTask(actionType, subOptions)
            val current = selectedSubKeysMap[key] ?: emptySet()
            selectedSubKeysMap[key] = current + defaultKeys
        } else {
            manualDoneMap[key] = false
            selectedSubKeysMap[key] = emptySet()
        }
    }

    fun toggleSubOption(key: String, optKey: String, actionType: String, subOptions: List<ServiceSubOption>) {
        val current = (selectedSubKeysMap[key] ?: emptySet()).toMutableSet()
        if (optKey in current) {
            current.remove(optKey)
        } else {
            current.add(optKey)
        }
        selectedSubKeysMap[key] = current
        val completes = ServiceChecklistCatalog.evaluateCompletesTask(actionType, current, subOptions)
        manualDoneMap[key] = completes
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = false
        )
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.96f)
                .fillMaxHeight(0.92f),
            shape = RoundedCornerShape(16.dp),
            color = TftSurfaceDark,
            border = BorderStroke(1.dp, TitaniumBorder),
            shadowElevation = 12.dp
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // FIXED HEADER
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF141923))
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = if (existingLog != null) tr("Edit Service Log", "Upraviť servisný záznam") else tr("Record Service Log", "Zapísať servisný záznam"),
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 17.sp
                        )
                        Text(
                            text = "${activeBike.name} • ${activeBike.make} ${activeBike.model} (${activeBike.totalOdometerKm.toInt()} km)",
                            color = Color.Gray,
                            fontSize = 11.sp
                        )
                    }
                    IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.Close, contentDescription = tr("Close", "Zavrieť"), tint = Color.Gray)
                    }
                }

                HorizontalDivider(color = TitaniumBorder, thickness = 1.dp)

                // LAZYCOLUMN BODY
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    // Item 0: General Service Metadata Form
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFF131822)),
                            border = BorderStroke(1.dp, TitaniumBorder)
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = tr("SERVICE LOG DETAILS", "ÚDAJE O SERVISE"),
                                    color = TelemetryIceCyan,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace
                                )

                                OutlinedTextField(
                                    value = title,
                                    onValueChange = { title = it },
                                    label = { Text(tr("Service Title", "Názov servisu"), fontSize = 11.sp) },
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = RacingAmberPrimary,
                                        unfocusedBorderColor = TitaniumBorder,
                                        focusedTextColor = Color.White,
                                        unfocusedTextColor = Color.White
                                    ),
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth()
                                )

                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    OutlinedTextField(
                                        value = odoText,
                                        onValueChange = { odoText = it },
                                        label = { Text(tr("Odometer (km)", "Stav tachometra (km)"), fontSize = 11.sp) },
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedBorderColor = RacingAmberPrimary,
                                            unfocusedBorderColor = TitaniumBorder,
                                            focusedTextColor = Color.White,
                                            unfocusedTextColor = Color.White
                                        ),
                                        singleLine = true,
                                        modifier = Modifier.weight(1f)
                                    )
                                    OutlinedTextField(
                                        value = limitKmText,
                                        onValueChange = { limitKmText = it },
                                        label = { Text(tr("Next Interval (km)", "Ďalší interval (km)"), fontSize = 11.sp) },
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedBorderColor = RacingAmberPrimary,
                                            unfocusedBorderColor = TitaniumBorder,
                                            focusedTextColor = Color.White,
                                            unfocusedTextColor = Color.White
                                        ),
                                        singleLine = true,
                                        modifier = Modifier.weight(1f)
                                    )
                                }

                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    OutlinedTextField(
                                        value = costText,
                                        onValueChange = { costText = it },
                                        label = { Text(tr("Cost (%s)", "Cena (%s)").format(activeBike.currencySymbol), fontSize = 11.sp) },
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedBorderColor = RacingAmberPrimary,
                                            unfocusedBorderColor = TitaniumBorder,
                                            focusedTextColor = Color.White,
                                            unfocusedTextColor = Color.White
                                        ),
                                        singleLine = true,
                                        modifier = Modifier.weight(1f)
                                    )
                                    OutlinedTextField(
                                        value = location,
                                        onValueChange = { location = it },
                                        label = { Text(tr("Shop / Location", "Dielňa / Miesto"), fontSize = 11.sp) },
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedBorderColor = RacingAmberPrimary,
                                            unfocusedBorderColor = TitaniumBorder,
                                            focusedTextColor = Color.White,
                                            unfocusedTextColor = Color.White
                                        ),
                                        singleLine = true,
                                        modifier = Modifier.weight(1f)
                                    )
                                }

                                OutlinedTextField(
                                    value = notes,
                                    onValueChange = { notes = it },
                                    label = { Text(tr("General Service Notes & Summary", "Všeobecné poznámky k servisu"), fontSize = 11.sp) },
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = RacingAmberPrimary,
                                        unfocusedBorderColor = TitaniumBorder,
                                        focusedTextColor = Color.White,
                                        unfocusedTextColor = Color.White
                                    ),
                                    minLines = 2,
                                    maxLines = 3,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                    }

                    // Section 1 Header: Scheduled Maintenance Tasks
                    item {
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = tr("SECTION 1: SCHEDULED MAINTENANCE TASKS", "SEKCIA 1: PLÁNOVANÉ ÚKONY ÚDRŽBY"),
                                color = RacingAmberPrimary,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace,
                                letterSpacing = 0.5.sp
                            )
                            Text(
                                text = tr("${sortedTasks.size} tasks", "${sortedTasks.size} úkonov"),
                                color = Color.Gray,
                                fontSize = 10.sp
                            )
                        }
                    }

                    // Section 1 Items: Evaluated tasks
                    items(sortedTasks, key = { it.task.taskId }) { taskStatus ->
                        val task = taskStatus.task
                        val opts = remember(task) {
                            ServiceChecklistCatalog.getSubOptionsFor(task.taskId, task.taskName, task.category)
                        }
                        val completes = isTaskCompleted(task.taskId, task.actionType, opts)
                        val subKeys = selectedSubKeysMap[task.taskId] ?: emptySet()
                        val taskNote = taskNotesMap[task.taskId] ?: ""
                        val isGuideExpanded = guideExpandedMap[task.taskId] == true

                        val statusPillText = when (taskStatus.status) {
                            TaskStatus.OVERDUE -> tr("OVERDUE", "PO TERMÍNE")
                            TaskStatus.DUE_SOON -> tr("DUE SOON", "ČOSKORO")
                            TaskStatus.OK -> tr("OK", "V PORIADKU")
                        }
                        val statusColor = when (taskStatus.status) {
                            TaskStatus.OVERDUE -> RedlineCrimson
                            TaskStatus.DUE_SOON -> WarningGold
                            TaskStatus.OK -> ApexEmeraldGreen
                        }

                        val isActive = completes || subKeys.isNotEmpty() || taskNote.isNotBlank()
                        val effectChipInfo = when {
                            !isActive -> null
                            completes -> when (taskStatus.status) {
                                TaskStatus.OVERDUE -> Pair(tr("✓ CLEARS OVERDUE", "✓ VYRIEŠI PO TERMÍNE"), ApexEmeraldGreen)
                                TaskStatus.DUE_SOON -> Pair(tr("✓ CLEARS OVERDUE", "✓ VYRIEŠI PO TERMÍNE"), ApexEmeraldGreen)
                                TaskStatus.OK -> Pair(tr("✓ RESETS INTERVAL", "✓ RESETUJE INTERVAL"), ApexEmeraldGreen)
                            }
                            else -> Pair(tr("CHECKED ONLY", "LEN KONTROLA"), WarningGold)
                        }

                        val guideText = remember(task, specs, appLang) {
                            val procedure = MotorcycleTemplateCatalog.getProcedureGuideForTask(task.taskName, task.category, specs, appLang.code)
                            val customNotes = task.notes.trim()
                            val oemPartsList = try {
                                if (task.oemPartNumbersJson.isNotBlank() && task.oemPartNumbersJson != "[]") {
                                    Json { ignoreUnknownKeys = true; isLenient = true }.decodeFromString<List<String>>(task.oemPartNumbersJson)
                                } else emptyList()
                            } catch (_: Exception) {
                                emptyList()
                            }
                            val oemPartsText = if (oemPartsList.isNotEmpty()) {
                                (if (appLang == AppLang.SK) "OEM diely: " else "OEM Parts: ") + oemPartsList.joinToString(", ")
                            } else ""

                            buildString {
                                if (procedure.isNotBlank()) append(procedure)
                                if (customNotes.isNotBlank() && customNotes != procedure) {
                                    if (isNotEmpty()) append("\n\n")
                                    append(if (appLang == AppLang.SK) "Pokyny k úkonu: " else "Task Instructions: ").append(customNotes)
                                }
                                if (oemPartsText.isNotBlank()) {
                                    if (isNotEmpty()) append("\n\n")
                                    append(oemPartsText)
                                }
                                if (isEmpty()) {
                                    append(if (appLang == AppLang.SK) "Pre tento úkon nie je dostupný postup." else "No repair guide available for this task.")
                                }
                            }
                        }

                        ServiceTaskCheckCard(
                            taskName = task.taskName,
                            category = task.category,
                            actionType = task.actionType,
                            statusPillText = statusPillText,
                            statusColor = statusColor,
                            effectChipText = effectChipInfo?.first,
                            effectChipColor = effectChipInfo?.second ?: ApexEmeraldGreen,
                            subOptions = opts,
                            selectedSubKeys = subKeys,
                            completesTask = completes,
                            taskNote = taskNote,
                            isGuideExpanded = isGuideExpanded,
                            guideText = guideText,
                            onToggleMain = { willBeDone ->
                                toggleMainCompletion(task.taskId, task.actionType, opts, willBeDone)
                            },
                            onToggleSubOption = { optKey ->
                                toggleSubOption(task.taskId, optKey, task.actionType, opts)
                            },
                            onNoteChange = { taskNotesMap[task.taskId] = it },
                            onToggleGuide = { guideExpandedMap[task.taskId] = !isGuideExpanded },
                            appLang = appLang
                        )
                    }

                    // Section 2 Header: General Workshop Checks
                    if (remainingGeneralChecks.isNotEmpty()) {
                        item {
                            Spacer(modifier = Modifier.height(6.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = tr("SECTION 2: GENERAL WORKSHOP & SAFETY CHECKS", "SEKCIA 2: VŠEOBECNÁ KONTROLA A DIELŇA"),
                                    color = TelemetryIceCyan,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace,
                                    letterSpacing = 0.5.sp
                                )
                                Text(
                                    text = tr("${remainingGeneralChecks.size} checks", "${remainingGeneralChecks.size} kontrol"),
                                    color = Color.Gray,
                                    fontSize = 10.sp
                                )
                            }
                        }

                        items(remainingGeneralChecks, key = { it.key }) { tpl ->
                            val opts = remember(tpl) {
                                ServiceChecklistCatalog.getSubOptionsFor(tpl.key, tpl.nameEn, tpl.category)
                            }
                            val completes = isTaskCompleted(tpl.key, tpl.actionType, opts)
                            val subKeys = selectedSubKeysMap[tpl.key] ?: emptySet()
                            val checkNote = taskNotesMap[tpl.key] ?: ""
                            val isGuideExpanded = guideExpandedMap[tpl.key] == true

                            val isActive = completes || subKeys.isNotEmpty() || checkNote.isNotBlank()
                            val effectChipInfo = when {
                                !isActive -> null
                                completes -> Pair(tr("✓ COMPLETED", "✓ HOTOVO"), ApexEmeraldGreen)
                                else -> Pair(tr("CHECKED ONLY", "LEN KONTROLA"), WarningGold)
                            }

                            val guideText = remember(tpl, specs, appLang) {
                                val procedure = MotorcycleTemplateCatalog.getProcedureGuideForTask(tpl.nameEn, tpl.category, specs, appLang.code)
                                if (procedure.isNotBlank()) procedure
                                else if (appLang == AppLang.SK) {
                                    "Štandardná dielenská kontrola bezpečnosti a uťahovacích momentov. Skontrolujte spoje, vôľu, netesnosti a opotrebovanie."
                                } else {
                                    "Standard workshop safety & torque inspection. Check fasteners, play, leaks, and general wear and tear."
                                }
                            }

                            ServiceTaskCheckCard(
                                taskName = tpl.name(appLang.code),
                                category = tpl.category,
                                actionType = tpl.actionType,
                                statusPillText = tr("GENERAL", "VŠEOBECNÉ"),
                                statusColor = TelemetryIceCyan,
                                effectChipText = effectChipInfo?.first,
                                effectChipColor = effectChipInfo?.second ?: ApexEmeraldGreen,
                                subOptions = opts,
                                selectedSubKeys = subKeys,
                                completesTask = completes,
                                taskNote = checkNote,
                                isGuideExpanded = isGuideExpanded,
                                guideText = guideText,
                                onToggleMain = { willBeDone ->
                                    toggleMainCompletion(tpl.key, tpl.actionType, opts, willBeDone)
                                },
                                onToggleSubOption = { optKey ->
                                    toggleSubOption(tpl.key, optKey, tpl.actionType, opts)
                                },
                                onNoteChange = { taskNotesMap[tpl.key] = it },
                                onToggleGuide = { guideExpandedMap[tpl.key] = !isGuideExpanded },
                                appLang = appLang
                            )
                        }
                    }
                }

                HorizontalDivider(color = TitaniumBorder, thickness = 1.dp)

                // FIXED FOOTER
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF141923))
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val completedCount = remember {
                        derivedStateOf {
                            val s1 = sortedTasks.count {
                                val opts = ServiceChecklistCatalog.getSubOptionsFor(it.task.taskId, it.task.taskName, it.task.category)
                                isTaskCompleted(it.task.taskId, it.task.actionType, opts)
                            }
                            val s2 = remainingGeneralChecks.count {
                                val opts = ServiceChecklistCatalog.getSubOptionsFor(it.key, it.nameEn, it.category)
                                isTaskCompleted(it.key, it.actionType, opts)
                            }
                            s1 + s2
                        }
                    }

                    Text(
                        text = tr("${completedCount.value} completed", "${completedCount.value} hotových"),
                        color = if (completedCount.value > 0) ApexEmeraldGreen else Color.Gray,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        TextButton(onClick = onDismiss) {
                            Text(tr("Cancel", "Zrušiť"), color = Color.Gray)
                        }

                        Button(
                            onClick = {
                                val recordedItems = mutableListOf<ServiceChecklistItem>()

                                // 1. Section 1 scheduled tasks
                                sortedTasks.forEach { taskStatus ->
                                    val task = taskStatus.task
                                    val subKeys = selectedSubKeysMap[task.taskId] ?: emptySet()
                                    val note = (taskNotesMap[task.taskId] ?: "").trim()
                                    val opts = ServiceChecklistCatalog.getSubOptionsFor(task.taskId, task.taskName, task.category)
                                    val completes = isTaskCompleted(task.taskId, task.actionType, opts)

                                    if (subKeys.isNotEmpty() || completes || note.isNotBlank()) {
                                        recordedItems.add(
                                            ServiceChecklistItem(
                                                name = task.taskName,
                                                status = if (completes) "DONE" else "CHECKED",
                                                taskId = task.taskId,
                                                subActions = subKeys.toList(),
                                                note = note,
                                                completesTask = completes
                                            )
                                        )
                                    }
                                }

                                // 2. Section 2 general checks
                                remainingGeneralChecks.forEach { tpl ->
                                    val subKeys = selectedSubKeysMap[tpl.key] ?: emptySet()
                                    val note = (taskNotesMap[tpl.key] ?: "").trim()
                                    val opts = ServiceChecklistCatalog.getSubOptionsFor(tpl.key, tpl.nameEn, tpl.category)
                                    val completes = isTaskCompleted(tpl.key, tpl.actionType, opts)

                                    if (subKeys.isNotEmpty() || completes || note.isNotBlank()) {
                                        recordedItems.add(
                                            ServiceChecklistItem(
                                                name = tpl.nameEn,
                                                status = if (completes) "DONE" else "CHECKED",
                                                taskId = tpl.key,
                                                subActions = subKeys.toList(),
                                                note = note,
                                                completesTask = completes
                                            )
                                        )
                                    }
                                }

                                val completedTaskIds = recordedItems.filter { it.completesTask }.mapNotNull { it.taskId }
                                val finalTaskId = when {
                                    completedTaskIds.size == 1 -> completedTaskIds.first()
                                    completedTaskIds.size > 1 -> "MULTI_TASK_SERVICE"
                                    else -> existingLog?.taskId ?: initialTaskId.ifBlank { "GENERAL_SERVICE" }
                                }

                                val odo = odoText.toSafeDouble(activeBike.totalOdometerKm)
                                val limit = limitKmText.toSafeDouble(5000.0)
                                val cost = costText.toSafeDouble(0.0)
                                val checklistJson = Json.encodeToString(recordedItems)

                                val entity = ServiceLogEntity(
                                    logId = existingLog?.logId ?: UUID.randomUUID().toString(),
                                    motorcycleId = activeBike.id,
                                    taskId = finalTaskId,
                                    serviceTitle = title.ifBlank { if (appLang == AppLang.SK) "Pravidelný servis" else "Periodic Service" },
                                    locationOrShop = location.ifBlank { if (appLang == AppLang.SK) "Domáca garáž" else "Home Garage" },
                                    performedAtIsoDate = existingLog?.performedAtIsoDate ?: LocalDate.now().format(DateTimeFormatter.ISO_DATE),
                                    odometerAtServiceKm = odo,
                                    servicedAtKm = odo,
                                    limitKm = limit,
                                    endKm = odo + limit,
                                    costEur = cost,
                                    performedBy = existingLog?.performedBy ?: "Self",
                                    notes = notes,
                                    checklistItemsJson = checklistJson
                                )
                                onSave(entity)
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = RacingAmberPrimary),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (existingLog != null) tr("Update Service Log", "Aktualizovať servis") else tr("Save Service Log", "Uložiť servis"),
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ServiceTaskCheckCard(
    taskName: String,
    category: String,
    actionType: String,
    statusPillText: String,
    statusColor: Color,
    effectChipText: String?,
    effectChipColor: Color,
    subOptions: List<ServiceSubOption>,
    selectedSubKeys: Set<String>,
    completesTask: Boolean,
    taskNote: String,
    isGuideExpanded: Boolean,
    guideText: String,
    onToggleMain: (Boolean) -> Unit,
    onToggleSubOption: (String) -> Unit,
    onNoteChange: (String) -> Unit,
    onToggleGuide: () -> Unit,
    appLang: AppLang
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (completesTask) Color(0xFF162330) else Color(0xFF131822)
        ),
        border = BorderStroke(
            width = if (completesTask) 1.5.dp else 1.dp,
            color = if (completesTask) ApexEmeraldGreen.copy(alpha = 0.8f) else TitaniumBorder
        )
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            // Header Row: Checkbox, Name, Status Pill, Effect Chip, (i) Info Button
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Checkbox(
                    checked = completesTask,
                    onCheckedChange = onToggleMain,
                    colors = CheckboxDefaults.colors(
                        checkedColor = ApexEmeraldGreen,
                        uncheckedColor = TitaniumBorder
                    ),
                    modifier = Modifier.size(28.dp)
                )

                Spacer(modifier = Modifier.width(6.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = taskName,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = Color.White,
                            modifier = Modifier.weight(1f, fill = false)
                        )

                        // Interval status pill
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = statusColor.copy(alpha = 0.15f),
                            border = BorderStroke(1.dp, statusColor.copy(alpha = 0.5f))
                        ) {
                            Text(
                                text = statusPillText,
                                color = statusColor,
                                fontWeight = FontWeight.Bold,
                                fontSize = 9.sp,
                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                            )
                        }

                        // Live effect chip
                        if (effectChipText != null) {
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = effectChipColor.copy(alpha = 0.15f),
                                border = BorderStroke(1.dp, effectChipColor.copy(alpha = 0.5f))
                            ) {
                                Text(
                                    text = effectChipText,
                                    color = effectChipColor,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 9.sp,
                                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }

                    Text(
                        text = "${category.uppercase()} • " + tr("ACTION: %s", "ÚKON: %s").format(actionType.uppercase()),
                        color = Color.Gray,
                        fontSize = 10.sp
                    )
                }

                IconButton(
                    onClick = onToggleGuide,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Info,
                        contentDescription = tr("Repair Guide", "Postup a momenty"),
                        tint = if (isGuideExpanded) RacingAmberPrimary else TelemetryIceCyan,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            // Inline Repair Guide & Torques
            if (isGuideExpanded) {
                Spacer(modifier = Modifier.height(8.dp))
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = Color(0xFF141923),
                    border = BorderStroke(1.dp, TelemetryIceCyan.copy(alpha = 0.35f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                Icons.Outlined.Info,
                                contentDescription = null,
                                tint = TelemetryIceCyan,
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                text = tr("REPAIR GUIDE, TORQUES & OEM", "POSTUP, MOMENTY A OEM"),
                                color = TelemetryIceCyan,
                                fontWeight = FontWeight.Bold,
                                fontSize = 10.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = guideText,
                            color = Color.LightGray,
                            fontSize = 11.sp,
                            lineHeight = 16.sp
                        )
                    }
                }
            }

            // Body Row: Left Column (weight 1.1f) Sub-checkboxes, Right Column (weight 0.9f) Notes TextField
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Left Column: Sub-options
                Column(
                    modifier = Modifier.weight(1.1f),
                    verticalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    subOptions.forEach { opt ->
                        val isChecked = opt.key in selectedSubKeys
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onToggleSubOption(opt.key) }
                                .padding(vertical = 1.dp)
                        ) {
                            Checkbox(
                                checked = isChecked,
                                onCheckedChange = { onToggleSubOption(opt.key) },
                                modifier = Modifier.size(20.dp),
                                colors = CheckboxDefaults.colors(
                                    checkedColor = ApexEmeraldGreen,
                                    uncheckedColor = TitaniumBorder
                                )
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = opt.label(appLang.code),
                                fontSize = 11.sp,
                                color = if (isChecked) Color.White else Color.Gray,
                                lineHeight = 14.sp
                            )
                        }
                    }
                }

                // Right Column: Dedicated per-task Notes TextField
                Column(modifier = Modifier.weight(0.9f)) {
                    OutlinedTextField(
                        value = taskNote,
                        onValueChange = onNoteChange,
                        placeholder = {
                            Text(
                                tr("Task notes & torques...", "Poznámka a momenty..."),
                                fontSize = 10.sp,
                                color = Color.Gray
                            )
                        },
                        modifier = Modifier.fillMaxWidth(),
                        textStyle = LocalTextStyle.current.copy(fontSize = 11.sp, color = Color.White),
                        minLines = 2,
                        maxLines = 4,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = RacingAmberPrimary,
                            unfocusedBorderColor = TitaniumBorder,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        )
                    )
                }
            }
        }
    }
}

@Composable
private fun AddScheduleTaskDialog(
    motorcycleId: String,
    existingTask: MaintenanceTaskEntity? = null,
    onDismiss: () -> Unit,
    onSave: (MaintenanceTaskEntity) -> Unit
) {
    var taskName by remember { mutableStateOf(existingTask?.taskName ?: "") }
    var category by remember { mutableStateOf(existingTask?.category ?: "Engine") }
    var actionType by remember { mutableStateOf(existingTask?.actionType ?: "Replace") }
    var intervalKm by remember { mutableStateOf((existingTask?.intervalKm?.toInt() ?: 10000).toString()) }
    var intervalMonths by remember { mutableStateOf((existingTask?.intervalMonths ?: 12).toString()) }
    var notes by remember { mutableStateOf(existingTask?.notes ?: "") }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = TftSurfaceDark,
        title = {
            Text(
                text = if (existingTask != null) tr("Edit Maintenance Task", "Upraviť úkon údržby") else tr("Add Maintenance Task", "Pridať úkon údržby"),
                color = Color.White,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                OutlinedTextField(
                    value = taskName,
                    onValueChange = { taskName = it },
                    label = { Text(tr("Task Name", "Názov úkonu")) },
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
                    label = { Text(tr("Category (Engine, Brakes, Tires)", "Kategória (Motor, Brzdy, Pneu)")) },
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
                    label = { Text(tr("Action (Replace, Inspect, Lube)", "Úkon (Výmena, Kontrola, Mazanie)")) },
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
                        label = { Text(tr("Interval (km)", "Interval (km)")) },
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
                        label = { Text(tr("Interval (mo)", "Interval (mes.)")) },
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
                    label = { Text(tr("Notes / Torques (Nm)", "Poznámky / momenty (Nm)")) },
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
                        taskId = existingTask?.taskId ?: UUID.randomUUID().toString(),
                        motorcycleId = motorcycleId,
                        category = category.ifBlank { "General" },
                        taskName = taskName.ifBlank { "Periodic Check" },
                        actionType = actionType.ifBlank { "Inspect" },
                        firstBreakInAtKm = existingTask?.firstBreakInAtKm ?: 1000.0,
                        intervalKm = intervalKm.toSafeDouble(10000.0),
                        intervalMonths = intervalMonths.toIntOrNull() ?: 12,
                        warningThresholdKm = existingTask?.warningThresholdKm ?: 500.0,
                        warningThresholdDays = existingTask?.warningThresholdDays ?: 30,
                        oemPartNumbersJson = existingTask?.oemPartNumbersJson ?: "[]",
                        notes = notes
                    )
                    onSave(task)
                },
                colors = ButtonDefaults.buttonColors(containerColor = RacingAmberPrimary)
            ) {
                Text(
                    text = if (existingTask != null) tr("Save Changes", "Uložiť zmeny") else tr("Add Task", "Pridať úkon"),
                    fontWeight = FontWeight.Bold
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(tr("Cancel", "Zrušiť"), color = Color.Gray) }
        }
    )
}

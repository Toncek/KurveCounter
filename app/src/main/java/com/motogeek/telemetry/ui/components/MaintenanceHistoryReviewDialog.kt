package com.motogeek.telemetry.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.motogeek.telemetry.data.local.MaintenanceTaskEntity
import com.motogeek.telemetry.domain.maintenance.MaintenanceEvaluator
import com.motogeek.telemetry.domain.maintenance.MaintenanceTaskDto
import com.motogeek.telemetry.domain.maintenance.MaintenanceTaskStatus
import com.motogeek.telemetry.domain.maintenance.TaskStatus
import com.motogeek.telemetry.domain.onboarding.BikePurchaseCondition
import com.motogeek.telemetry.domain.onboarding.MaintenanceHistoryReviewItem
import com.motogeek.telemetry.domain.onboarding.NewBikeTutorialDraft
import com.motogeek.telemetry.domain.onboarding.ServiceBookBaselineCalculator
import com.motogeek.telemetry.ui.theme.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.time.LocalDate
import java.util.UUID

@Composable
fun MaintenanceHistoryReviewDialog(
    draft: NewBikeTutorialDraft,
    onUpdateItem: (MaintenanceHistoryReviewItem) -> Unit,
    onResetItem: (taskId: String) -> Unit,
    onAddTask: (
        task: MaintenanceTaskDto,
        lastPerformedKmText: String,
        lastPerformedDateIso: String,
        wasNeverDone: Boolean
    ) -> Unit,
    onSave: () -> Unit,
    onDiscard: (deletePersistedBike: Boolean) -> Unit,
    onMinimize: () -> Unit
) {
    val appLang = LocalAppLang.current
    val evaluator = remember { MaintenanceEvaluator() }
    val today = remember { LocalDate.now() }
    val todayIso = remember(today) { today.toString() }

    var showDiscardChoiceDialog by remember { mutableStateOf(false) }
    var showInlineAddTask by remember(draft.maintenanceReviewItems.isEmpty()) {
        mutableStateOf(draft.maintenanceReviewItems.isEmpty())
    }

    val currentOdo = remember(draft.currentOdometerKmText) {
        draft.currentOdometerKmText.trim().replace(',', '.').toDoubleOrNull()
            ?.takeIf { it.isFinite() && it >= 0.0 } ?: 0.0
    }
    val condition = remember(draft.purchaseCondition, currentOdo) {
        draft.purchaseCondition ?: if (currentOdo > 0.0) {
            BikePurchaseCondition.USED
        } else {
            BikePurchaseCondition.NEW
        }
    }
    val previewBike = remember(draft, todayIso) {
        draft.toMotorcycleEntity(todayIso = todayIso, isActive = true)
    }

    val expectedByTaskId = remember(
        draft.maintenanceReviewItems,
        condition,
        currentOdo,
        draft.yearText,
        draft.purchaseDateIso,
        today
    ) {
        val dtos = draft.maintenanceReviewItems.map { it.toTaskDto() }
        ServiceBookBaselineCalculator.buildExpectedHistory(
            condition = condition,
            currentOdometerKm = currentOdo,
            bikeYear = draft.yearText.trim().toIntOrNull()?.takeIf { it > 0 },
            purchaseDateIso = draft.purchaseDateIso.trim().ifBlank { todayIso },
            tasks = dtos,
            today = today
        ).associateBy { it.taskId }
    }

    val bikeTitleParts = listOfNotNull(
        draft.yearText.trim().toIntOrNull()?.takeIf { it > 0 }?.toString(),
        draft.make.trim().takeIf { it.isNotBlank() },
        draft.model.trim().takeIf { it.isNotBlank() }
    ).joinToString(" ").ifBlank { tr("New Motorcycle", "Nový motocykel") }

    val conditionLabel = when (condition) {
        BikePurchaseCondition.NEW -> tr("NEW", "NOVÝ")
        BikePurchaseCondition.USED -> tr("USED", "OJAZDENÝ")
    }
    val bikePillText = "$bikeTitleParts • $conditionLabel • ${currentOdo.toInt()} km"

    BackHandler(onBack = onMinimize)

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = CarbonObsidianBg,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            Surface(
                color = TftSurfaceDark,
                border = BorderStroke(1.dp, TitaniumBorder)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .windowInsetsPadding(
                            WindowInsets.safeDrawing.only(
                                WindowInsetsSides.Top + WindowInsetsSides.Horizontal
                            )
                        )
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = tr("MAINTENANCE HISTORY", "HISTÓRIA ÚDRŽBY"),
                                color = TelemetryIceCyan,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace,
                                letterSpacing = 1.sp
                            )
                            Text(
                                text = tr("Service Book History Review", "Kontrola servisnej knižky"),
                                color = Color.White,
                                fontSize = 17.sp,
                                fontWeight = FontWeight.ExtraBold
                            )
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            val autosaved = draft.lastAutosavedEpochMs > 0L
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = (if (autosaved) ApexEmeraldGreen else Color.Gray).copy(alpha = 0.14f),
                                border = BorderStroke(
                                    1.dp,
                                    (if (autosaved) ApexEmeraldGreen else Color.Gray).copy(alpha = 0.5f)
                                )
                            ) {
                                Text(
                                    text = if (autosaved) {
                                        tr("✓ Autosaved", "✓ Uložené")
                                    } else {
                                        tr("Draft", "Návrh")
                                    },
                                    color = if (autosaved) ApexEmeraldGreen else Color.LightGray,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }

                            IconButton(onClick = onMinimize) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = tr("Minimize review", "Minimalizovať kontrolu"),
                                    tint = Color.LightGray
                                )
                            }
                        }
                    }

                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = TftSurfaceElevated,
                        border = BorderStroke(1.dp, TitaniumBorder)
                    ) {
                        Text(
                            text = bikePillText,
                            color = RacingAmberPrimary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                        )
                    }
                }
            }
        },
        bottomBar = {
            // Bottom Bar: ALWAYS keeps explicit Discard and Save
            Surface(
                color = TftSurfaceDark,
                border = BorderStroke(1.dp, TitaniumBorder)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .windowInsetsPadding(
                            WindowInsets.navigationBars
                                .union(WindowInsets.ime)
                                .union(WindowInsets.displayCutout.only(WindowInsetsSides.Horizontal))
                        )
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(
                        onClick = { showDiscardChoiceDialog = true },
                        border = BorderStroke(1.dp, RedlineCrimson.copy(alpha = 0.8f)),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            text = tr("Discard", "Zahodiť"),
                            color = RedlineCrimson,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Button(
                        onClick = onSave,
                        colors = ButtonDefaults.buttonColors(containerColor = ApexEmeraldGreen),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.weight(1.5f)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = null,
                            tint = Color.Black,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = tr("Save", "Uložiť"),
                            color = Color.Black,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item(key = "explanation_banner") {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = TelemetryIceCyan.copy(alpha = 0.10f),
                    border = BorderStroke(1.dp, TelemetryIceCyan.copy(alpha = 0.45f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.MenuBook,
                            contentDescription = null,
                            tint = TelemetryIceCyan,
                            modifier = Modifier.size(20.dp)
                        )
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                text = tr(
                                    "Service Book Baseline Assumption",
                                    "Predpoklad podľa servisnej knižky"
                                ),
                                color = Color.White,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = tr(
                                    "We assumed all important scheduled maintenance was completed on time according to the service book up to ${currentOdo.toInt()} km. Review each item below and update any mileage, date, or mark tasks as not done yet.",
                                    "Predpokladali sme, že všetka dôležitá predpísaná údržba bola vykonaná včas podľa servisnej knižky do stavu ${currentOdo.toInt()} km. Skontrolujte jednotlivé úkony a upravte km, dátum alebo označte úkony, ktoré ešte neboli vykonané."
                                ),
                                color = Color.LightGray,
                                fontSize = 12.sp,
                                lineHeight = 16.sp
                            )
                        }
                    }
                }
            }

            if (draft.maintenanceReviewItems.isEmpty()) {
                item(key = "empty_schedule_notice") {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = TftSurfaceDark,
                        border = BorderStroke(1.dp, TitaniumBorder),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = tr(
                                    "No scheduled maintenance tasks yet",
                                    "Zatiaľ žiadne plánované úkony údržby"
                                ),
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                            Text(
                                text = tr(
                                    "Add key service tasks below (for example Engine Oil & Filter, Chain, or Brake Fluid) to track your service history.",
                                    "Pridajte nižšie kľúčové servisné úkony (napr. Motorový olej a filter, Reťaz alebo Brzdová kvapalina) pre sledovanie servisnej histórie."
                                ),
                                color = Color.Gray,
                                fontSize = 12.sp
                            )
                        }
                    }
                }
            }

            items(draft.maintenanceReviewItems, key = { it.taskId }) { item ->
                val taskEntity = remember(item, previewBike.id) { item.toTaskEntity(previewBike.id) }
                val baselineLog = remember(item, previewBike.id, condition, todayIso) {
                    ServiceBookBaselineCalculator.buildBaselineServiceLog(
                        motorcycleId = previewBike.id,
                        item = item,
                        condition = condition,
                        defaultDateIso = draft.purchaseDateIso.trim().ifBlank { todayIso }
                    )
                }
                val evaluatedStatus = remember(previewBike, taskEntity, baselineLog, today) {
                    evaluator.evaluate(
                        motorcycle = previewBike,
                        tasks = listOf(taskEntity),
                        logs = listOfNotNull(baselineLog),
                        currentDate = today
                    ).firstOrNull()
                }

                ReviewItemCard(
                    item = item,
                    expectedHintItem = expectedByTaskId[item.taskId],
                    evaluatedStatus = evaluatedStatus,
                    onUpdateItem = onUpdateItem,
                    onResetItem = { onResetItem(item.taskId) }
                )
            }

            item(key = "quick_add_section") {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = { showInlineAddTask = !showInlineAddTask },
                        border = BorderStroke(1.dp, RacingAmberPrimary),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            imageVector = if (showInlineAddTask) Icons.Default.ExpandLess else Icons.Default.Add,
                            contentDescription = null,
                            tint = RacingAmberPrimary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = tr("+ Add Maintenance Task", "+ Pridať servisný úkon"),
                            color = RacingAmberPrimary,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    AnimatedVisibility(visible = showInlineAddTask) {
                        InlineAddReviewTaskCard(
                            appLangCode = appLang.code,
                            onAdd = { task, kmText, dateIso, neverDone ->
                                onAddTask(task, kmText, dateIso, neverDone)
                            }
                        )
                    }
                }
            }

            item(key = "bottom_spacer") {
                Spacer(modifier = Modifier.height(12.dp))
            }
        }
    }

    if (showDiscardChoiceDialog) {
        AlertDialog(
            onDismissRequest = { showDiscardChoiceDialog = false },
            containerColor = TftSurfaceDark,
            title = {
                Text(
                    text = tr("Discard Options", "Možnosti zahodenia"),
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    text = tr(
                        "Choose whether to discard only the service book history review (keeping your new bike & specs) or discard the entire new motorcycle.",
                        "Vyberte, či chcete zahodiť iba kontrolu servisnej histórie (motorka a špecifikácie zostanú uložené), alebo zahodiť celý nový motocykel."
                    ),
                    color = Color.LightGray,
                    fontSize = 13.sp
                )
            },
            confirmButton = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = {
                            showDiscardChoiceDialog = false
                            onDiscard(false)
                        },
                        border = BorderStroke(1.dp, WarningGold),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = tr("Discard Maintenance History Only", "Zahodiť iba históriu údržby"),
                            color = WarningGold,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Button(
                        onClick = {
                            showDiscardChoiceDialog = false
                            onDiscard(true)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = RedlineCrimson),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = tr("Discard Entire New Bike", "Zahodiť celý nový motocykel"),
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { showDiscardChoiceDialog = false }) {
                    Text(tr("Cancel", "Zrušiť"), color = Color.Gray)
                }
            }
        )
    }
}

@Composable
private fun ReviewItemCard(
    item: MaintenanceHistoryReviewItem,
    expectedHintItem: MaintenanceHistoryReviewItem?,
    evaluatedStatus: MaintenanceTaskStatus?,
    onUpdateItem: (MaintenanceHistoryReviewItem) -> Unit,
    onResetItem: () -> Unit
) {
    val (statusLabel, statusColor) = when (evaluatedStatus?.status) {
        TaskStatus.OVERDUE -> tr("OVERDUE", "PO TERMÍNE") to RedlineCrimson
        TaskStatus.DUE_SOON -> tr("DUE SOON", "ČOSKORO") to WarningGold
        TaskStatus.OK, null -> tr("OK", "OK") to ApexEmeraldGreen
    }

    val intervalParts = buildList {
        item.firstBreakInAtKm?.let { add(tr("Break-in: ${it.toInt()} km", "Zábeh: ${it.toInt()} km")) }
        item.intervalKm?.let { add(tr("Every ${it.toInt()} km", "Každých ${it.toInt()} km")) }
        item.intervalMonths?.let { add(tr("Every $it mo", "Každých $it mes.")) }
    }
    val intervalSummary = if (intervalParts.isEmpty()) {
        tr("No interval configured", "Bez nastaveného intervalu")
    } else {
        intervalParts.joinToString(" • ")
    }

    val expectedHintText = if (expectedHintItem != null) {
        if (expectedHintItem.wasNeverDone) {
            tr("Service book baseline: Not due / not done yet", "Servisná knižka: Ešte nebolo potrebné")
        } else {
            val kmStr = expectedHintItem.lastPerformedKmText.ifBlank { "0" }
            val dateStr = expectedHintItem.lastPerformedDateIso
            if (dateStr.isNotBlank()) {
                tr("Service book baseline: $kmStr km ($dateStr)", "Servisná knižka: $kmStr km ($dateStr)")
            } else {
                tr("Service book baseline: $kmStr km", "Servisná knižka: $kmStr km")
            }
        }
    } else {
        ""
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = TftSurfaceDark),
        border = BorderStroke(1.dp, TitaniumBorder)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Title + Live Evaluator Pill
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = item.taskName.ifBlank { item.taskId },
                        color = Color.White,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "${item.category} • $intervalSummary",
                        color = Color.Gray,
                        fontSize = 11.sp
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                Column(horizontalAlignment = Alignment.End) {
                    Surface(
                        shape = RoundedCornerShape(5.dp),
                        color = statusColor.copy(alpha = 0.16f),
                        border = BorderStroke(1.dp, statusColor.copy(alpha = 0.65f))
                    ) {
                        Text(
                            text = statusLabel,
                            color = statusColor,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.ExtraBold,
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                        )
                    }
                    evaluatedStatus?.nextDueOdometerKm?.let { nextDue ->
                        Text(
                            text = tr("Next: ${nextDue.toInt()} km", "Ďalší: ${nextDue.toInt()} km"),
                            color = TelemetryIceCyan,
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier.padding(top = 3.dp)
                        )
                    }
                }
            }

            // Expected hint + Reset button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = expectedHintText,
                    color = Color(0xFF8E9BB0),
                    fontSize = 11.sp,
                    modifier = Modifier.weight(1f)
                )
                TextButton(
                    onClick = onResetItem,
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = null,
                        tint = TelemetryIceCyan,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(tr("Reset", "Obnoviť"), color = TelemetryIceCyan, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }

            // Not done yet / Unknown checkbox
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        onUpdateItem(item.copy(wasNeverDone = !item.wasNeverDone))
                    },
                verticalAlignment = Alignment.CenterVertically
            ) {
                Checkbox(
                    checked = item.wasNeverDone,
                    onCheckedChange = { checked ->
                        onUpdateItem(item.copy(wasNeverDone = checked))
                    },
                    colors = CheckboxDefaults.colors(
                        checkedColor = RacingAmberPrimary,
                        uncheckedColor = Color.Gray,
                        checkmarkColor = Color.White
                    )
                )
                Text(
                    text = tr("Not done yet / Unknown", "Ešte nevykonané / Neznáme"),
                    color = if (item.wasNeverDone) RacingAmberPrimary else Color.LightGray,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }

            // Last Done Mileage & Date
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = item.lastPerformedKmText,
                    onValueChange = { onUpdateItem(item.copy(lastPerformedKmText = it)) },
                    enabled = !item.wasNeverDone,
                    label = { Text(tr("Last Done (km)", "Naposledy (km)"), fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    placeholder = { Text("km", color = Color.DarkGray, fontSize = 12.sp) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    colors = reviewFieldColors(),
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = item.lastPerformedDateIso,
                    onValueChange = { onUpdateItem(item.copy(lastPerformedDateIso = it)) },
                    enabled = !item.wasNeverDone,
                    label = { Text(tr("Date (YYYY-MM-DD)", "Dátum (RRRR-MM-DD)"), fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    placeholder = { Text("YYYY-MM-DD", color = Color.DarkGray, fontSize = 12.sp) },
                    singleLine = true,
                    colors = reviewFieldColors(),
                    modifier = Modifier.weight(1f)
                )
            }

            OutlinedTextField(
                value = item.logNotes,
                onValueChange = { onUpdateItem(item.copy(logNotes = it)) },
                label = { Text(tr("Notes (optional)", "Poznámky (voliteľné)"), fontSize = 11.sp) },
                placeholder = { Text(tr("e.g. Dealer invoice #123, Motul 7100", "napr. Faktúra zo servisu, Motul 7100"), color = Color.DarkGray, fontSize = 12.sp) },
                singleLine = true,
                colors = reviewFieldColors(),
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun InlineAddReviewTaskCard(
    appLangCode: String,
    onAdd: (
        task: MaintenanceTaskDto,
        lastPerformedKmText: String,
        lastPerformedDateIso: String,
        wasNeverDone: Boolean
    ) -> Unit
) {
    var taskName by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("") }
    var intervalKmText by remember { mutableStateOf("") }
    var intervalMonthsText by remember { mutableStateOf("") }
    var lastKmText by remember { mutableStateOf("") }
    var lastDateIso by remember { mutableStateOf("") }
    var neverDone by remember { mutableStateOf(false) }

    val quickSuggestions = remember(appLangCode) {
        if (appLangCode == "sk") {
            listOf(
                "Motorový olej a filter" to "Engine",
                "Reťaz a rozety" to "Drivetrain",
                "Brzdová kvapalina" to "Brakes",
                "Vzduchový filter" to "Engine"
            )
        } else {
            listOf(
                "Engine Oil & Filter" to "Engine",
                "Drive Chain & Sprockets" to "Drivetrain",
                "Brake Fluid" to "Brakes",
                "Air Filter" to "Engine"
            )
        }
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = TftSurfaceElevated),
        border = BorderStroke(1.dp, RacingAmberPrimary.copy(alpha = 0.6f))
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = tr("QUICK-ADD SERVICE TASK", "RÝCHLO PRIDAŤ SERVISNÝ ÚKON"),
                color = RacingAmberPrimary,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )

            @OptIn(ExperimentalLayoutApi::class)
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                quickSuggestions.forEach { (label, cat) ->
                    AssistChip(
                        onClick = {
                            taskName = label
                            category = cat
                        },
                        label = { Text(label, fontSize = 11.sp) },
                        colors = AssistChipDefaults.assistChipColors(
                            containerColor = TftSurfaceDark,
                            labelColor = TelemetryIceCyan
                        )
                    )
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = taskName,
                    onValueChange = { taskName = it },
                    label = { Text(tr("Task Name *", "Názov úkonu *"), fontSize = 11.sp) },
                    placeholder = { Text(tr("e.g. Engine Oil & Filter", "napr. Motorový olej"), color = Color.Gray, fontSize = 12.sp) },
                    singleLine = true,
                    colors = reviewFieldColors(),
                    modifier = Modifier.weight(1.4f)
                )
                OutlinedTextField(
                    value = category,
                    onValueChange = { category = it },
                    label = { Text(tr("Category", "Kategória"), fontSize = 11.sp) },
                    placeholder = { Text("Engine", color = Color.Gray, fontSize = 12.sp) },
                    singleLine = true,
                    colors = reviewFieldColors(),
                    modifier = Modifier.weight(1f)
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = intervalKmText,
                    onValueChange = { intervalKmText = it },
                    label = { Text(tr("Interval (km)", "Interval (km)"), fontSize = 11.sp) },
                    placeholder = { Text("e.g. 10000", color = Color.Gray, fontSize = 12.sp) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    colors = reviewFieldColors(),
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = intervalMonthsText,
                    onValueChange = { intervalMonthsText = it },
                    label = { Text(tr("Months", "Mesiace"), fontSize = 11.sp) },
                    placeholder = { Text("e.g. 12", color = Color.Gray, fontSize = 12.sp) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    colors = reviewFieldColors(),
                    modifier = Modifier.weight(1f)
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = lastKmText,
                    onValueChange = { lastKmText = it },
                    enabled = !neverDone,
                    label = { Text(tr("Last Done (km)", "Naposledy (km)"), fontSize = 11.sp) },
                    placeholder = { Text("km", color = Color.Gray, fontSize = 12.sp) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    colors = reviewFieldColors(),
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = lastDateIso,
                    onValueChange = { lastDateIso = it },
                    enabled = !neverDone,
                    label = { Text(tr("Date (YYYY-MM-DD)", "Dátum"), fontSize = 11.sp) },
                    placeholder = { Text("YYYY-MM-DD", color = Color.Gray, fontSize = 12.sp) },
                    singleLine = true,
                    colors = reviewFieldColors(),
                    modifier = Modifier.weight(1f)
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.clickable { neverDone = !neverDone }
            ) {
                Checkbox(
                    checked = neverDone,
                    onCheckedChange = { neverDone = it },
                    colors = CheckboxDefaults.colors(checkedColor = RacingAmberPrimary)
                )
                Text(
                    text = tr("Not done yet / Unknown", "Ešte nevykonané / Neznáme"),
                    color = Color.LightGray,
                    fontSize = 12.sp
                )
            }

            Button(
                onClick = {
                    val cleanName = taskName.trim()
                    if (cleanName.isNotEmpty()) {
                        val dto = MaintenanceTaskDto(
                            taskId = "custom_${UUID.randomUUID().toString().take(8)}",
                            category = category.trim().ifBlank { "General" },
                            taskName = cleanName,
                            actionType = "Inspect / Replace",
                            intervalKm = intervalKmText.trim().replace(',', '.').toDoubleOrNull()?.takeIf { it.isFinite() && it > 0.0 },
                            intervalMonths = intervalMonthsText.trim().toIntOrNull()?.takeIf { it > 0 }
                        )
                        onAdd(dto, lastKmText, lastDateIso, neverDone)
                        taskName = ""
                        category = ""
                        intervalKmText = ""
                        intervalMonthsText = ""
                        lastKmText = ""
                        lastDateIso = ""
                        neverDone = false
                    }
                },
                enabled = taskName.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = RacingAmberPrimary),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(tr("Add to Review", "Pridať do kontroly"), color = Color.White, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun reviewFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = RacingAmberPrimary,
    unfocusedBorderColor = TitaniumBorder,
    focusedTextColor = Color.White,
    unfocusedTextColor = Color.White,
    disabledTextColor = Color.Gray,
    disabledBorderColor = TitaniumBorder.copy(alpha = 0.4f),
    focusedLabelColor = RacingAmberPrimary,
    unfocusedLabelColor = Color.LightGray,
    disabledLabelColor = Color.Gray
)

private fun MaintenanceHistoryReviewItem.toTaskDto(): MaintenanceTaskDto = MaintenanceTaskDto(
    taskId = taskId,
    category = category,
    taskName = taskName,
    actionType = actionType,
    firstBreakInAtKm = firstBreakInAtKm,
    intervalKm = intervalKm,
    intervalMonths = intervalMonths,
    warningThresholdKm = warningThresholdKm,
    warningThresholdDays = warningThresholdDays,
    oemPartNumbers = oemPartNumbers,
    notes = notes
)

private fun MaintenanceHistoryReviewItem.toTaskEntity(motorcycleId: String): MaintenanceTaskEntity =
    MaintenanceTaskEntity(
        taskId = taskId,
        motorcycleId = motorcycleId,
        category = category,
        taskName = taskName,
        actionType = actionType,
        firstBreakInAtKm = firstBreakInAtKm?.takeIf { it.isFinite() && it > 0.0 },
        intervalKm = intervalKm?.takeIf { it.isFinite() && it > 0.0 },
        intervalMonths = intervalMonths?.takeIf { it > 0 },
        warningThresholdKm = warningThresholdKm?.takeIf { it.isFinite() && it >= 0.0 },
        warningThresholdDays = warningThresholdDays?.takeIf { it >= 0 },
        oemPartNumbersJson = Json.encodeToString(oemPartNumbers),
        notes = notes
    )

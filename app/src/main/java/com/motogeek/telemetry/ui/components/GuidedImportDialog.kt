package com.motogeek.telemetry.ui.components

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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.motogeek.telemetry.data.local.MotorcycleEntity
import com.motogeek.telemetry.domain.maintenance.*
import com.motogeek.telemetry.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GuidedImportDialog(
    plan: ImportPreviewPlan,
    existingBikes: List<MotorcycleEntity>,
    isImporting: Boolean = false,
    onDismiss: () -> Unit,
    onConfirm: (GuidedImportSelection) -> Unit
) {
    val initialSelection = remember(plan) { GuidedImportPlanner.buildDefaultSelection(plan) }
    var selectionMap by remember(plan) { mutableStateOf(initialSelection.perBikeSelections) }
    var applyPreferences by remember(plan) { mutableStateOf(initialSelection.applyPreferences) }

    val expandedServices = remember { mutableStateMapOf<String, Boolean>() }
    val expandedFuel = remember { mutableStateMapOf<String, Boolean>() }
    val expandedBikeDropdown = remember { mutableStateMapOf<String, Boolean>() }

    fun updateBike(sourceKey: String, transform: (BikeImportSelection) -> BikeImportSelection) {
        val current = selectionMap[sourceKey] ?: return
        selectionMap = selectionMap + (sourceKey to transform(current))
    }

    Dialog(
        onDismissRequest = { if (!isImporting) onDismiss() },
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = !isImporting,
            dismissOnClickOutside = !isImporting
        )
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.96f)
                .fillMaxHeight(0.92f),
            shape = RoundedCornerShape(12.dp),
            color = TftSurfaceDark,
            border = BorderStroke(1.dp, TitaniumBorder)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                // Header: Title + Format Badge + Dismiss Icon
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.weight(1f, fill = false)
                    ) {
                        Icon(
                            imageVector = Icons.Default.CloudDownload,
                            contentDescription = null,
                            tint = TelemetryIceCyan,
                            modifier = Modifier.size(24.dp)
                        )
                        Column {
                            Text(
                                text = tr("Guided Non-Destructive Import", "Sprievodca bezpečným importom"),
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp
                            )
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier.padding(top = 2.dp)
                            ) {
                                val (badgeLabel, badgeColor) = when (plan.format) {
                                    ImportSourceFormat.MYBIKES_EXPORT -> tr("MyBikes v1 Export", "MyBikes v1 Export") to RacingAmberPrimary
                                    ImportSourceFormat.KURVE_GARAGE_BACKUP -> tr("Kurve Counter Backup", "Kurve Counter Záloha") to TelemetryIceCyan
                                    ImportSourceFormat.AI_MANUAL_SCHEDULE -> tr("AI Manual / Service Schedule", "AI Manuál / Plán údržby") to ApexEmeraldGreen
                                }
                                Surface(
                                    color = badgeColor.copy(alpha = 0.18f),
                                    shape = RoundedCornerShape(4.dp),
                                    border = BorderStroke(1.dp, badgeColor.copy(alpha = 0.6f))
                                ) {
                                    Text(
                                        text = badgeLabel,
                                        color = badgeColor,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }
                    }

                    IconButton(
                        onClick = onDismiss,
                        enabled = !isImporting,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = Color.LightGray
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Non-destructive Safety Banner
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    color = CarbonObsidianBg,
                    border = BorderStroke(1.dp, ApexEmeraldGreen.copy(alpha = 0.5f))
                ) {
                    Row(
                        modifier = Modifier.padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Shield,
                            contentDescription = null,
                            tint = ApexEmeraldGreen,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = tr("Safe & Non-Destructive", "Bezpečný & nedeštruktívny import"),
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            )
                            Text(
                                text = tr(
                                    "Existing garage records won't be wiped. You pick target motorcycles and selectively import history logs.",
                                    "Existujúce záznamy garáže sa nezmažú. Zvoľte cieľové motocykle a vyberte konkrétne záznamy."
                                ),
                                color = Color(0xFFBAC5D5),
                                fontSize = 11.sp
                            )
                        }
                    }
                }

                // Raw Changelog Skipped Notice if present
                if (plan.rawChangelogEntriesCount > 0) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp),
                        color = CarbonObsidianBg,
                        border = BorderStroke(1.dp, WarningGold.copy(alpha = 0.4f))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = null,
                                tint = WarningGold,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = tr(
                                    "${plan.rawChangelogEntriesCount} raw changelog/audit events skipped (only bikes, maintenance, and logs imported).",
                                    "${plan.rawChangelogEntriesCount} auditných záznamov preskočených (importujú sa len motocykle, servis a údržba)."
                                ),
                                color = Color.LightGray,
                                fontSize = 11.sp
                            )
                        }
                    }
                }

                // Global Preferences Banner if present
                if (plan.preferences != null) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp),
                        color = TftSurfaceElevated,
                        border = BorderStroke(1.dp, TitaniumBorder)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { applyPreferences = !applyPreferences }
                                .padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = applyPreferences,
                                onCheckedChange = { applyPreferences = it },
                                colors = CheckboxDefaults.colors(checkedColor = TelemetryIceCyan)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Column {
                                Text(
                                    text = tr("Restore App Preferences & Units", "Obnoviť nastavenia aplikácie a jednotiek"),
                                    color = Color.White,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    text = tr("Telemetry units, tire pressure, temperature, and display settings.", "Jednotky telemetrie, tlaku, teploty a zobrazenia."),
                                    color = Color.Gray,
                                    fontSize = 10.sp
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Scrollable Bike Groups List
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(plan.bikeGroups, key = { it.sourceKey }) { group ->
                        val bikeSelection = selectionMap[group.sourceKey] ?: return@items
                        val totalOdo = (group.motorcycle.baseOdometerKm + group.motorcycle.accumulatedTrackedKm + group.motorcycle.manualAdjustmentOffsetKm).toInt()

                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = if (bikeSelection.includeBike) TftSurfaceElevated else CarbonObsidianBg
                            ),
                            border = BorderStroke(
                                1.dp,
                                if (bikeSelection.includeBike) RacingAmberPrimary.copy(alpha = 0.5f) else TitaniumBorder
                            )
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                // Master Checkbox + Bike Title Row
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            updateBike(group.sourceKey) { it.copy(includeBike = !it.includeBike) }
                                        },
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Checkbox(
                                        checked = bikeSelection.includeBike,
                                        onCheckedChange = { checked ->
                                            updateBike(group.sourceKey) { it.copy(includeBike = checked) }
                                        },
                                        colors = CheckboxDefaults.colors(checkedColor = RacingAmberPrimary)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = group.motorcycle.name.ifBlank { "${group.motorcycle.make} ${group.motorcycle.model}".trim() },
                                            color = if (bikeSelection.includeBike) Color.White else Color.Gray,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 14.sp
                                        )
                                        Row(
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = "${group.motorcycle.make} ${group.motorcycle.model} (${group.motorcycle.year})",
                                                color = Color.LightGray,
                                                fontSize = 11.sp
                                            )
                                            if (group.motorcycle.startDateIso.isNotBlank()) {
                                                Text("•", color = Color.DarkGray, fontSize = 11.sp)
                                                Text(group.motorcycle.startDateIso, color = Color.Gray, fontSize = 11.sp)
                                            }
                                        }
                                        Text(
                                            text = tr(
                                                "Odometer: ${group.motorcycle.baseOdometerKm.toInt()} km → $totalOdo km",
                                                "Tachometer: ${group.motorcycle.baseOdometerKm.toInt()} km → $totalOdo km"
                                            ),
                                            color = TelemetryIceCyan,
                                            fontSize = 11.sp,
                                            fontFamily = FontFamily.Monospace
                                        )
                                    }
                                }

                                if (bikeSelection.includeBike) {
                                    HorizontalDivider(
                                        color = TitaniumBorder.copy(alpha = 0.6f),
                                        modifier = Modifier.padding(vertical = 8.dp)
                                    )

                                    // Destination Selector: Create New vs Merge Into Existing
                                    Text(
                                        text = tr("To which motorbike?", "Do ktorého motocykla?"),
                                        color = Color(0xFF6B7A99),
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = FontFamily.Monospace,
                                        letterSpacing = 0.5.sp
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        FilterChip(
                                            selected = bikeSelection.destination is ImportDestination.CreateNewBike,
                                            onClick = {
                                                updateBike(group.sourceKey) { current ->
                                                    current.copy(
                                                        destination = ImportDestination.CreateNewBike(
                                                            currencySymbol = group.motorcycle.currencySymbol.ifBlank { "€" },
                                                            seedDefaultScheduleIfEmpty = group.maintenanceTasks.isEmpty()
                                                        ),
                                                        updateBikeProfileAndOdo = true
                                                    )
                                                }
                                            },
                                            label = { Text(tr("Create New Bike", "Vytvoriť nový motocykel"), fontSize = 11.sp) },
                                            colors = FilterChipDefaults.filterChipColors(
                                                selectedContainerColor = RacingAmberPrimary.copy(alpha = 0.2f),
                                                selectedLabelColor = RacingAmberPrimary
                                            )
                                        )

                                        FilterChip(
                                            selected = bikeSelection.destination is ImportDestination.MergeIntoExistingBike,
                                            enabled = existingBikes.isNotEmpty(),
                                            onClick = {
                                                val targetId = group.suggestedExistingBikeId ?: existingBikes.firstOrNull()?.id ?: ""
                                                updateBike(group.sourceKey) { current ->
                                                    current.copy(
                                                        destination = ImportDestination.MergeIntoExistingBike(targetId),
                                                        updateBikeProfileAndOdo = false
                                                    )
                                                }
                                            },
                                            label = { Text(tr("Merge Into Existing Bike", "Zlúčiť s existujúcim motocyklom"), fontSize = 11.sp) },
                                            colors = FilterChipDefaults.filterChipColors(
                                                selectedContainerColor = TelemetryIceCyan.copy(alpha = 0.2f),
                                                selectedLabelColor = TelemetryIceCyan
                                            )
                                        )
                                    }

                                    // Sub-options for Create New Bike
                                    val destCreate = bikeSelection.destination as? ImportDestination.CreateNewBike
                                    if (destCreate != null) {
                                        val dest = destCreate
                                        Spacer(modifier = Modifier.height(6.dp))

                                        // Currency picker chips
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Text(
                                                text = tr("Currency:", "Mena:"),
                                                color = Color.LightGray,
                                                fontSize = 11.sp
                                            )
                                            val currencies = listOf("€", "฿", "$", "£", "Kč")
                                            currencies.forEach { sym ->
                                                FilterChip(
                                                    selected = dest.currencySymbol == sym,
                                                    onClick = {
                                                        updateBike(group.sourceKey) {
                                                            it.copy(destination = dest.copy(currencySymbol = sym))
                                                        }
                                                    },
                                                    label = { Text(sym, fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                                                    modifier = Modifier.height(28.dp),
                                                    colors = FilterChipDefaults.filterChipColors(
                                                        selectedContainerColor = ApexEmeraldGreen.copy(alpha = 0.25f),
                                                        selectedLabelColor = ApexEmeraldGreen
                                                    )
                                                )
                                            }
                                        }

                                        // Seed default maintenance schedule when tasks are empty
                                        if (group.maintenanceTasks.isEmpty()) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .clickable {
                                                        updateBike(group.sourceKey) {
                                                            it.copy(destination = dest.copy(seedDefaultScheduleIfEmpty = !dest.seedDefaultScheduleIfEmpty))
                                                        }
                                                    }
                                                    .padding(top = 4.dp)
                                            ) {
                                                Checkbox(
                                                    checked = dest.seedDefaultScheduleIfEmpty,
                                                    onCheckedChange = { checked ->
                                                        updateBike(group.sourceKey) {
                                                            it.copy(destination = dest.copy(seedDefaultScheduleIfEmpty = checked))
                                                        }
                                                    },
                                                    colors = CheckboxDefaults.colors(checkedColor = TelemetryIceCyan)
                                                )
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text(
                                                    text = tr("Load standard maintenance schedule", "Nahrať predvolený plán údržby"),
                                                    color = Color.LightGray,
                                                    fontSize = 11.sp
                                                )
                                            }
                                        }
                                    }

                                    // Sub-options for Merge Into Existing Bike
                                    val destMerge = bikeSelection.destination as? ImportDestination.MergeIntoExistingBike
                                    if (destMerge != null) {
                                        val dest = destMerge
                                        val selectedExistingBike = existingBikes.firstOrNull { it.id == dest.existingBikeId } ?: existingBikes.firstOrNull()

                                        Spacer(modifier = Modifier.height(6.dp))
                                        Box(modifier = Modifier.fillMaxWidth()) {
                                            Surface(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .clickable { expandedBikeDropdown[group.sourceKey] = true },
                                                shape = RoundedCornerShape(6.dp),
                                                color = CarbonObsidianBg,
                                                border = BorderStroke(1.dp, TitaniumBorder)
                                            ) {
                                                Row(
                                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.SpaceBetween
                                                ) {
                                                    Column(modifier = Modifier.weight(1f)) {
                                                        Text(
                                                            text = selectedExistingBike?.displayName ?: tr("Select motorcycle", "Vyberte motocykel"),
                                                            color = Color.White,
                                                            fontSize = 12.sp,
                                                            fontWeight = FontWeight.Medium
                                                        )
                                                        if (selectedExistingBike != null) {
                                                            Text(
                                                                text = "${selectedExistingBike.year} • ${selectedExistingBike.totalOdometerKm.toInt()} km",
                                                                color = Color.Gray,
                                                                fontSize = 10.sp
                                                            )
                                                        }
                                                    }
                                                    Icon(
                                                        imageVector = Icons.Default.ArrowDropDown,
                                                        contentDescription = null,
                                                        tint = TelemetryIceCyan
                                                    )
                                                }
                                            }

                                            DropdownMenu(
                                                expanded = expandedBikeDropdown[group.sourceKey] == true,
                                                onDismissRequest = { expandedBikeDropdown[group.sourceKey] = false },
                                                modifier = Modifier.background(TftSurfaceElevated)
                                            ) {
                                                existingBikes.forEach { bike ->
                                                    DropdownMenuItem(
                                                        text = {
                                                            Column {
                                                                Text(bike.displayName, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                                                Text("${bike.make} ${bike.model} (${bike.year}) • ${bike.totalOdometerKm.toInt()} km", color = Color.LightGray, fontSize = 10.sp)
                                                            }
                                                        },
                                                        onClick = {
                                                            expandedBikeDropdown[group.sourceKey] = false
                                                            updateBike(group.sourceKey) { it.copy(destination = ImportDestination.MergeIntoExistingBike(bike.id)) }
                                                        }
                                                    )
                                                }
                                            }
                                        }

                                        if (group.suggestedExistingBikeId != null && group.suggestedExistingBikeId == dest.existingBikeId) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                                modifier = Modifier.padding(top = 4.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.AutoAwesome,
                                                    contentDescription = null,
                                                    tint = ApexEmeraldGreen,
                                                    modifier = Modifier.size(12.dp)
                                                )
                                                Text(
                                                    text = tr("Suggested match by name/model", "Automatická zhoda podľa názvu/modelu"),
                                                    color = ApexEmeraldGreen,
                                                    fontSize = 10.sp
                                                )
                                            }
                                        }
                                    }

                                    HorizontalDivider(
                                        color = TitaniumBorder.copy(alpha = 0.5f),
                                        modifier = Modifier.padding(vertical = 8.dp)
                                    )

                                    // Selective Data Checkboxes
                                    Text(
                                        text = tr("SELECT DATA TO IMPORT", "VYBERTE DÁTA NA IMPORT"),
                                        color = Color(0xFF6B7A99),
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = FontFamily.Monospace,
                                        letterSpacing = 0.5.sp
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))

                                    // 1. Update bike profile & odometer
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                updateBike(group.sourceKey) { it.copy(updateBikeProfileAndOdo = !it.updateBikeProfileAndOdo) }
                                            }
                                    ) {
                                        Checkbox(
                                            checked = bikeSelection.updateBikeProfileAndOdo,
                                            onCheckedChange = { checked ->
                                                updateBike(group.sourceKey) { it.copy(updateBikeProfileAndOdo = checked) }
                                            },
                                            colors = CheckboxDefaults.colors(checkedColor = TelemetryIceCyan)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Column {
                                            Text(
                                                text = tr("Update bike profile & odometer", "Aktualizovať profil motocykla a tachometer"),
                                                color = Color.White,
                                                fontSize = 12.sp
                                            )
                                            Text(
                                                text = if (bikeSelection.destination is ImportDestination.CreateNewBike) {
                                                    tr("Sets make, model, displacement, start date & base odometer", "Uloží značku, model, objem, dátum a tachometer")
                                                } else {
                                                    tr("Overwrites existing bike specs and base odometer reading", "Prepíše existujúce parametre a počiatočný tachometer")
                                                },
                                                color = Color.Gray,
                                                fontSize = 10.sp
                                            )
                                        }
                                    }

                                    // 2. Technical specs & bolt torques
                                    if (group.technicalSpecs != null) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable {
                                                    updateBike(group.sourceKey) { it.copy(importTechnicalSpecs = !it.importTechnicalSpecs) }
                                                }
                                        ) {
                                            Checkbox(
                                                checked = bikeSelection.importTechnicalSpecs,
                                                onCheckedChange = { checked ->
                                                    updateBike(group.sourceKey) { it.copy(importTechnicalSpecs = checked) }
                                                },
                                                colors = CheckboxDefaults.colors(checkedColor = TelemetryIceCyan)
                                            )
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(
                                                text = tr("Technical specs & bolt torques", "Technické parametre & uťahovacie momenty"),
                                                color = Color.White,
                                                fontSize = 12.sp
                                            )
                                        }
                                    }

                                    // 3. Maintenance tasks
                                    if (group.maintenanceTasks.isNotEmpty()) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable {
                                                    updateBike(group.sourceKey) { it.copy(importMaintenanceTasks = !it.importMaintenanceTasks) }
                                                }
                                        ) {
                                            Checkbox(
                                                checked = bikeSelection.importMaintenanceTasks,
                                                onCheckedChange = { checked ->
                                                    updateBike(group.sourceKey) { it.copy(importMaintenanceTasks = checked) }
                                                },
                                                colors = CheckboxDefaults.colors(checkedColor = TelemetryIceCyan)
                                            )
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(
                                                text = tr(
                                                    "Maintenance schedule (${group.maintenanceTasks.size} tasks)",
                                                    "Plán údržby (${group.maintenanceTasks.size} úloh)"
                                                ),
                                                color = Color.White,
                                                fontSize = 12.sp
                                            )
                                        }
                                    }

                                    // 4. Service Logs category + expandable candidate list
                                    if (group.serviceLogs.isNotEmpty()) {
                                        val isExpanded = expandedServices[group.sourceKey] == true
                                        val allSvcKeys = group.serviceLogs.map { it.selectionKey }.toSet()
                                        val hasSomeSvc = bikeSelection.selectedServiceLogKeys.isNotEmpty()
                                        val nonDupSvcKeys = group.serviceLogs.filter { !it.isDuplicateInFile }.map { it.selectionKey }.toSet()

                                        Column(modifier = Modifier.fillMaxWidth()) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                modifier = Modifier.fillMaxWidth()
                                            ) {
                                                Checkbox(
                                                    checked = hasSomeSvc,
                                                    onCheckedChange = { checked ->
                                                        val newKeys = if (checked) {
                                                            if (nonDupSvcKeys.isNotEmpty()) nonDupSvcKeys else allSvcKeys
                                                        } else emptySet()
                                                        updateBike(group.sourceKey) { it.copy(selectedServiceLogKeys = newKeys) }
                                                    },
                                                    colors = CheckboxDefaults.colors(checkedColor = TelemetryIceCyan)
                                                )
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text(
                                                    text = tr(
                                                        "Service history (${bikeSelection.selectedServiceLogKeys.size}/${group.serviceLogs.size})",
                                                        "História servisu (${bikeSelection.selectedServiceLogKeys.size}/${group.serviceLogs.size})"
                                                    ),
                                                    color = Color.White,
                                                    fontSize = 12.sp,
                                                    fontWeight = FontWeight.Medium,
                                                    modifier = Modifier
                                                        .weight(1f)
                                                        .clickable { expandedServices[group.sourceKey] = !isExpanded }
                                                )
                                                IconButton(
                                                    onClick = { expandedServices[group.sourceKey] = !isExpanded },
                                                    modifier = Modifier.size(28.dp)
                                                ) {
                                                    Icon(
                                                        imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                                        contentDescription = null,
                                                        tint = TelemetryIceCyan,
                                                        modifier = Modifier.size(18.dp)
                                                    )
                                                }
                                            }

                                            if (isExpanded) {
                                                Column(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .padding(start = 16.dp, top = 2.dp, bottom = 4.dp),
                                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                                ) {
                                                    group.serviceLogs.forEach { svc ->
                                                        val isCandidateSelected = svc.selectionKey in bikeSelection.selectedServiceLogKeys
                                                        Surface(
                                                            modifier = Modifier
                                                                .fillMaxWidth()
                                                                .clickable {
                                                                    val next = if (isCandidateSelected) {
                                                                        bikeSelection.selectedServiceLogKeys - svc.selectionKey
                                                                    } else {
                                                                        bikeSelection.selectedServiceLogKeys + svc.selectionKey
                                                                    }
                                                                    updateBike(group.sourceKey) { it.copy(selectedServiceLogKeys = next) }
                                                                },
                                                            shape = RoundedCornerShape(6.dp),
                                                            color = CarbonObsidianBg,
                                                            border = BorderStroke(0.5.dp, TitaniumBorder)
                                                        ) {
                                                            Row(
                                                                modifier = Modifier.padding(6.dp),
                                                                verticalAlignment = Alignment.CenterVertically
                                                            ) {
                                                                Checkbox(
                                                                    checked = isCandidateSelected,
                                                                    onCheckedChange = { checked ->
                                                                        val next = if (checked) {
                                                                            bikeSelection.selectedServiceLogKeys + svc.selectionKey
                                                                        } else {
                                                                            bikeSelection.selectedServiceLogKeys - svc.selectionKey
                                                                        }
                                                                        updateBike(group.sourceKey) { it.copy(selectedServiceLogKeys = next) }
                                                                    },
                                                                    colors = CheckboxDefaults.colors(checkedColor = TelemetryIceCyan),
                                                                    modifier = Modifier.size(24.dp)
                                                                )
                                                                Spacer(modifier = Modifier.width(8.dp))
                                                                Column(modifier = Modifier.weight(1f)) {
                                                                    Row(
                                                                        verticalAlignment = Alignment.CenterVertically,
                                                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                                                    ) {
                                                                        Text(
                                                                            text = svc.log.performedAtIsoDate,
                                                                            color = Color.White,
                                                                            fontSize = 11.sp,
                                                                            fontWeight = FontWeight.SemiBold
                                                                        )
                                                                        Text(
                                                                            text = "${svc.log.odometerAtServiceKm.toInt()} km",
                                                                            color = TelemetryIceCyan,
                                                                            fontSize = 11.sp,
                                                                            fontFamily = FontFamily.Monospace
                                                                        )
                                                                        if (svc.log.costEur > 0.0) {
                                                                            Text(
                                                                                text = "${svc.log.costEur.toInt()} €",
                                                                                color = ApexEmeraldGreen,
                                                                                fontSize = 11.sp,
                                                                                fontWeight = FontWeight.Medium
                                                                            )
                                                                        }
                                                                        if (svc.isDuplicateInFile) {
                                                                            Surface(
                                                                                color = RedlineCrimson.copy(alpha = 0.2f),
                                                                                shape = RoundedCornerShape(3.dp),
                                                                                border = BorderStroke(0.5.dp, RedlineCrimson)
                                                                            ) {
                                                                                Text(
                                                                                    text = tr("Duplicate in file", "Duplikát v súbore"),
                                                                                    color = RedlineCrimson,
                                                                                    fontSize = 9.sp,
                                                                                    fontWeight = FontWeight.Bold,
                                                                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                                                                )
                                                                            }
                                                                        }
                                                                    }
                                                                    val label = svc.log.serviceTitle.ifBlank { svc.log.notes }
                                                                    if (label.isNotBlank()) {
                                                                        Text(
                                                                            text = label,
                                                                            color = Color.LightGray,
                                                                            fontSize = 10.sp,
                                                                            maxLines = 2
                                                                        )
                                                                    }
                                                                    if (svc.log.locationOrShop.isNotBlank() && svc.log.locationOrShop != "Home Garage") {
                                                                        Text(
                                                                            text = svc.log.locationOrShop,
                                                                            color = Color.Gray,
                                                                            fontSize = 9.sp
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

                                    // 5. Fuel Logs category + expandable list
                                    if (group.fuelLogs.isNotEmpty()) {
                                        val isExpanded = expandedFuel[group.sourceKey] == true
                                        val allFuelKeys = group.fuelLogs.map { it.selectionKey }.toSet()
                                        val hasSomeFuel = bikeSelection.selectedFuelLogKeys.isNotEmpty()

                                        Column(modifier = Modifier.fillMaxWidth()) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                modifier = Modifier.fillMaxWidth()
                                            ) {
                                                Checkbox(
                                                    checked = hasSomeFuel,
                                                    onCheckedChange = { checked ->
                                                        val next = if (checked) allFuelKeys else emptySet()
                                                        updateBike(group.sourceKey) { it.copy(selectedFuelLogKeys = next) }
                                                    },
                                                    colors = CheckboxDefaults.colors(checkedColor = TelemetryIceCyan)
                                                )
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text(
                                                    text = tr(
                                                        "Fuel logs (${bikeSelection.selectedFuelLogKeys.size}/${group.fuelLogs.size})",
                                                        "Záznamy tankovania (${bikeSelection.selectedFuelLogKeys.size}/${group.fuelLogs.size})"
                                                    ),
                                                    color = Color.White,
                                                    fontSize = 12.sp,
                                                    fontWeight = FontWeight.Medium,
                                                    modifier = Modifier
                                                        .weight(1f)
                                                        .clickable { expandedFuel[group.sourceKey] = !isExpanded }
                                                )
                                                IconButton(
                                                    onClick = { expandedFuel[group.sourceKey] = !isExpanded },
                                                    modifier = Modifier.size(28.dp)
                                                ) {
                                                    Icon(
                                                        imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                                        contentDescription = null,
                                                        tint = TelemetryIceCyan,
                                                        modifier = Modifier.size(18.dp)
                                                    )
                                                }
                                            }

                                            if (isExpanded) {
                                                Column(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .padding(start = 16.dp, top = 2.dp, bottom = 4.dp),
                                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                                ) {
                                                    group.fuelLogs.forEach { fuel ->
                                                        val isCandidateSelected = fuel.selectionKey in bikeSelection.selectedFuelLogKeys
                                                        Surface(
                                                            modifier = Modifier
                                                                .fillMaxWidth()
                                                                .clickable {
                                                                    val next = if (isCandidateSelected) {
                                                                        bikeSelection.selectedFuelLogKeys - fuel.selectionKey
                                                                    } else {
                                                                        bikeSelection.selectedFuelLogKeys + fuel.selectionKey
                                                                    }
                                                                    updateBike(group.sourceKey) { it.copy(selectedFuelLogKeys = next) }
                                                                },
                                                            shape = RoundedCornerShape(6.dp),
                                                            color = CarbonObsidianBg,
                                                            border = BorderStroke(0.5.dp, TitaniumBorder)
                                                        ) {
                                                            Row(
                                                                modifier = Modifier.padding(6.dp),
                                                                verticalAlignment = Alignment.CenterVertically
                                                            ) {
                                                                Checkbox(
                                                                    checked = isCandidateSelected,
                                                                    onCheckedChange = { checked ->
                                                                        val next = if (checked) {
                                                                            bikeSelection.selectedFuelLogKeys + fuel.selectionKey
                                                                        } else {
                                                                            bikeSelection.selectedFuelLogKeys - fuel.selectionKey
                                                                        }
                                                                        updateBike(group.sourceKey) { it.copy(selectedFuelLogKeys = next) }
                                                                    },
                                                                    colors = CheckboxDefaults.colors(checkedColor = TelemetryIceCyan),
                                                                    modifier = Modifier.size(24.dp)
                                                                )
                                                                Spacer(modifier = Modifier.width(8.dp))
                                                                Column(modifier = Modifier.weight(1f)) {
                                                                    Row(
                                                                        verticalAlignment = Alignment.CenterVertically,
                                                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                                                    ) {
                                                                        Text(
                                                                            text = fuel.log.dateIso,
                                                                            color = Color.White,
                                                                            fontSize = 11.sp,
                                                                            fontWeight = FontWeight.SemiBold
                                                                        )
                                                                        Text(
                                                                            text = "${fuel.log.odometerKm.toInt()} km",
                                                                            color = TelemetryIceCyan,
                                                                            fontSize = 11.sp,
                                                                            fontFamily = FontFamily.Monospace
                                                                        )
                                                                        if (fuel.log.liters > 0.0) {
                                                                            Text(
                                                                                text = "${fuel.log.liters} L",
                                                                                color = RacingAmberPrimary,
                                                                                fontSize = 11.sp
                                                                            )
                                                                        }
                                                                        if (fuel.log.totalCost > 0.0) {
                                                                            Text(
                                                                                text = "${fuel.log.totalCost} €",
                                                                                color = ApexEmeraldGreen,
                                                                                fontSize = 11.sp,
                                                                                fontWeight = FontWeight.Medium
                                                                            )
                                                                        }
                                                                    }
                                                                    val note = fuel.stationOrFuelLabel.ifBlank { fuel.log.notes }
                                                                    if (note.isNotBlank()) {
                                                                        Text(
                                                                            text = note,
                                                                            color = Color.LightGray,
                                                                            fontSize = 10.sp,
                                                                            maxLines = 1
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

                                    // 6. Parts
                                    if (group.partLogs.isNotEmpty()) {
                                        val allPartKeys = group.partLogs.map { it.selectionKey }.toSet()
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable {
                                                    val next = if (bikeSelection.selectedPartLogKeys.isNotEmpty()) emptySet() else allPartKeys
                                                    updateBike(group.sourceKey) { it.copy(selectedPartLogKeys = next) }
                                                }
                                        ) {
                                            Checkbox(
                                                checked = bikeSelection.selectedPartLogKeys.isNotEmpty(),
                                                onCheckedChange = { checked ->
                                                    val next = if (checked) allPartKeys else emptySet()
                                                    updateBike(group.sourceKey) { it.copy(selectedPartLogKeys = next) }
                                                },
                                                colors = CheckboxDefaults.colors(checkedColor = TelemetryIceCyan)
                                            )
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(
                                                text = tr(
                                                    "Part replacement logs (${bikeSelection.selectedPartLogKeys.size}/${group.partLogs.size})",
                                                    "Záznamy o dieloch (${bikeSelection.selectedPartLogKeys.size}/${group.partLogs.size})"
                                                ),
                                                color = Color.White,
                                                fontSize = 12.sp
                                            )
                                        }
                                    }

                                    // 7. Reminders
                                    if (group.reminders.isNotEmpty()) {
                                        val allRemKeys = group.reminders.map { it.selectionKey }.toSet()
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable {
                                                    val next = if (bikeSelection.selectedReminderKeys.isNotEmpty()) emptySet() else allRemKeys
                                                    updateBike(group.sourceKey) { it.copy(selectedReminderKeys = next) }
                                                }
                                        ) {
                                            Checkbox(
                                                checked = bikeSelection.selectedReminderKeys.isNotEmpty(),
                                                onCheckedChange = { checked ->
                                                    val next = if (checked) allRemKeys else emptySet()
                                                    updateBike(group.sourceKey) { it.copy(selectedReminderKeys = next) }
                                                },
                                                colors = CheckboxDefaults.colors(checkedColor = TelemetryIceCyan)
                                            )
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(
                                                text = tr(
                                                    "Reminders (${bikeSelection.selectedReminderKeys.size}/${group.reminders.size})",
                                                    "Pripomienky (${bikeSelection.selectedReminderKeys.size}/${group.reminders.size})"
                                                ),
                                                color = Color.White,
                                                fontSize = 12.sp
                                            )
                                        }
                                    }

                                    // 8. Rides
                                    if (group.rides.isNotEmpty()) {
                                        val allRideKeys = group.rides.map { it.selectionKey }.toSet()
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable {
                                                    val next = if (bikeSelection.selectedRideKeys.isNotEmpty()) emptySet() else allRideKeys
                                                    updateBike(group.sourceKey) { it.copy(selectedRideKeys = next) }
                                                }
                                        ) {
                                            Checkbox(
                                                checked = bikeSelection.selectedRideKeys.isNotEmpty(),
                                                onCheckedChange = { checked ->
                                                    val next = if (checked) allRideKeys else emptySet()
                                                    updateBike(group.sourceKey) { it.copy(selectedRideKeys = next) }
                                                },
                                                colors = CheckboxDefaults.colors(checkedColor = TelemetryIceCyan)
                                            )
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(
                                                text = tr(
                                                    "GPS rides (${bikeSelection.selectedRideKeys.size}/${group.rides.size})",
                                                    "GPS jazdy (${bikeSelection.selectedRideKeys.size}/${group.rides.size})"
                                                ),
                                                color = Color.White,
                                                fontSize = 12.sp
                                            )
                                        }
                                    }

                                    // 9. AppLogs
                                    if (group.appLogs.isNotEmpty()) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable {
                                                    updateBike(group.sourceKey) { it.copy(importAppLogs = !it.importAppLogs) }
                                                }
                                        ) {
                                            Checkbox(
                                                checked = bikeSelection.importAppLogs,
                                                onCheckedChange = { checked ->
                                                    updateBike(group.sourceKey) { it.copy(importAppLogs = checked) }
                                                },
                                                colors = CheckboxDefaults.colors(checkedColor = TelemetryIceCyan)
                                            )
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(
                                                text = tr(
                                                    "Diagnostic / App logs (${group.appLogs.size})",
                                                    "Diagnostické záznamy (${group.appLogs.size})"
                                                ),
                                                color = Color.White,
                                                fontSize = 12.sp
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                HorizontalDivider(
                    color = TitaniumBorder,
                    modifier = Modifier.padding(vertical = 10.dp)
                )

                // Footer: Live Selected Summary & Actions
                val includedBikes = selectionMap.filter { it.value.includeBike }
                val selectedBikesCount = includedBikes.size
                val totalSvcCount = includedBikes.values.sumOf { it.selectedServiceLogKeys.size }
                val totalFuelCount = includedBikes.values.sumOf { it.selectedFuelLogKeys.size }
                val totalRidesCount = includedBikes.values.sumOf { it.selectedRideKeys.size }

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = tr(
                                "Selected: $selectedBikesCount bikes, $totalSvcCount services, $totalFuelCount fuel, $totalRidesCount rides",
                                "Vybrané: $selectedBikesCount motocyklov, $totalSvcCount servisov, $totalFuelCount tankovaní, $totalRidesCount jázd"
                            ),
                            color = Color.White,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium
                        )
                        if (applyPreferences) {
                            Text(
                                text = tr("+ App preferences & units", "+ Nastavenia aplikácie & jednotiek"),
                                color = TelemetryIceCyan,
                                fontSize = 10.sp
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedButton(
                            onClick = onDismiss,
                            enabled = !isImporting,
                            border = BorderStroke(1.dp, TitaniumBorder),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.LightGray)
                        ) {
                            Text(tr("Cancel", "Zrušiť"), fontSize = 12.sp)
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        Button(
                            onClick = {
                                onConfirm(
                                    GuidedImportSelection(
                                        perBikeSelections = selectionMap,
                                        applyPreferences = applyPreferences
                                    )
                                )
                            },
                            enabled = !isImporting && selectedBikesCount > 0,
                            colors = ButtonDefaults.buttonColors(containerColor = RacingAmberPrimary)
                        ) {
                            if (isImporting) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(14.dp),
                                    color = Color.White,
                                    strokeWidth = 2.dp
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(tr("Importing…", "Importujem…"), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            } else {
                                Icon(
                                    imageVector = Icons.Default.Download,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(tr("Import Selected", "Importovať vybrané"), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }
}

package com.motogeek.telemetry.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.motogeek.telemetry.data.local.PartLogEntity
import com.motogeek.telemetry.domain.toSafeDouble
import com.motogeek.telemetry.ui.components.SegmentedDigitalText
import com.motogeek.telemetry.ui.theme.*
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PartsScreen(viewModel: MainViewModel, navController: NavController) {
    val activeBike by viewModel.activeMotorcycle.collectAsState()
    val partLogs by viewModel.currentPartLogs.collectAsState()

    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val appLang = LocalAppLang.current

    var showAddEditDialog by remember { mutableStateOf(false) }
    var editingPartLog by remember { mutableStateOf<PartLogEntity?>(null) }
    var pendingDeletePart by remember { mutableStateOf<PartLogEntity?>(null) }

    val totalPartsCount = partLogs.size
    val totalInvested = partLogs.sumOf { it.cost }
    val currency = activeBike?.currencySymbol?.ifBlank { "€" } ?: "€"

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = tr("Parts & Modifications", "Diely & úpravy"),
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
                        editingPartLog = null
                        showAddEditDialog = true
                    }) {
                        Text(tr("+ ADD PART", "+ PRIDAŤ DIEL"), color = RacingAmberPrimary, fontWeight = FontWeight.Bold)
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

            // Summary Header Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp),
                colors = CardDefaults.cardColors(containerColor = TftSurfaceDark),
                border = BorderStroke(1.dp, TitaniumBorder)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        text = tr("PARTS & MODIFICATIONS SUMMARY", "PREHĽAD DIELOV A ÚPRAV"),
                        color = Color(0xFF6B7A99),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        letterSpacing = 1.sp
                    )
                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(tr("INSTALLED PARTS", "NAINŠTALOVANÉ DIELY"), color = Color.Gray, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
                            SegmentedDigitalText(
                                text = "$totalPartsCount",
                                unit = tr("ITEMS", "KS"),
                                color = TelemetryIceCyan,
                                fontSize = 18.sp
                            )
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text(tr("TOTAL INVESTED", "CELKOVÁ INVESTÍCIA"), color = Color.Gray, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
                            SegmentedDigitalText(
                                text = String.format("%,d", totalInvested.toInt()),
                                unit = currency,
                                color = RacingAmberPrimary,
                                fontSize = 18.sp
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Parts List
            if (partLogs.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(top = 40.dp),
                    contentAlignment = Alignment.TopCenter
                ) {
                    Text(
                        text = tr(
                            "No parts or mods recorded. Tap '+ ADD PART' to add exhaust, tires, sprockets, etc.",
                            "Zatiaľ žiadne diely ani úpravy. Klepnite na '+ PRIDAŤ DIEL' pre pridanie výfuku, pneumatík, reťaze a pod."
                        ),
                        color = Color.Gray,
                        fontSize = 13.sp
                    )
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(bottom = 32.dp)
                ) {
                    items(partLogs, key = { it.partId }) { part ->
                        PartLogCard(
                            part = part,
                            currency = currency,
                            onEdit = {
                                editingPartLog = part
                                showAddEditDialog = true
                            },
                            onDelete = {
                                pendingDeletePart = part
                            }
                        )
                    }
                }
            }
        }
    }

    if (showAddEditDialog && activeBike != null) {
        AddEditPartDialog(
            activeBike = activeBike!!,
            existing = editingPartLog,
            onDismiss = { showAddEditDialog = false },
            onSave = { entity ->
                if (editingPartLog != null) {
                    viewModel.updatePartLog(entity)
                    scope.launch { snackbarHostState.showSnackbar(appLang.pick("Part updated", "Diel aktualizovaný")) }
                } else {
                    viewModel.addPartLog(entity)
                    scope.launch { snackbarHostState.showSnackbar(appLang.pick("Part installed and logged", "Diel nainštalovaný a zaznamenaný")) }
                }
                showAddEditDialog = false
            }
        )
    }

    pendingDeletePart?.let { partToDelete ->
        AlertDialog(
            onDismissRequest = { pendingDeletePart = null },
            containerColor = TftSurfaceDark,
            title = {
                Text(
                    text = tr("Delete Part Log", "Vymazať záznam dielu"),
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    text = tr(
                        "Are you sure you want to delete \"${partToDelete.partName}\"? This cannot be undone.",
                        "Naozaj chcete vymazať diel \"${partToDelete.partName}\"? Túto akciu nemožno vrátiť späť."
                    ),
                    color = Color.LightGray
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deletePartLog(partToDelete.partId)
                    pendingDeletePart = null
                    scope.launch {
                        snackbarHostState.showSnackbar(appLang.pick("Part log deleted", "Záznam dielu vymazaný"))
                    }
                }) {
                    Text(tr("Delete", "Vymazať"), color = RedlineCrimson, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDeletePart = null }) {
                    Text(tr("Cancel", "Zrušiť"), color = Color.Gray)
                }
            }
        )
    }
}

@Composable
private fun PartLogCard(
    part: PartLogEntity,
    currency: String,
    onEdit: () -> Unit,
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
                .padding(14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = part.partName,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                )
                Text(
                    text = "${part.brand} ${if (part.partNumber.isNotBlank()) "• #${part.partNumber}" else ""}",
                    color = Color.Gray,
                    fontSize = 12.sp
                )
                Text(
                    text = tr(
                        "Installed on ${part.dateIso} at ${part.odometerKm.toInt()} km",
                        "Namontované ${part.dateIso} pri ${part.odometerKm.toInt()} km"
                    ),
                    color = Color(0xFF8E9BB0),
                    fontSize = 11.sp
                )

                if (part.notes.isNotBlank()) {
                    Text(
                        text = part.notes,
                        color = Color.LightGray,
                        fontSize = 11.sp,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                if (part.cost > 0) {
                    Text(
                        text = "${part.cost.toInt()} $currency",
                        color = RacingAmberPrimary,
                        fontWeight = FontWeight.Black,
                        fontSize = 15.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
                Spacer(modifier = Modifier.width(4.dp))
                IconButton(onClick = onDelete) {
                    Icon(Icons.Default.Delete, contentDescription = tr("Delete", "Vymazať"), tint = RedlineCrimson.copy(alpha = 0.7f), modifier = Modifier.size(20.dp))
                }
            }
        }
    }
}

@Composable
private fun AddEditPartDialog(
    activeBike: com.motogeek.telemetry.data.local.MotorcycleEntity,
    existing: PartLogEntity?,
    onDismiss: () -> Unit,
    onSave: (PartLogEntity) -> Unit
) {
    var partName by remember { mutableStateOf(existing?.partName ?: "") }
    var brand by remember { mutableStateOf(existing?.brand ?: "") }
    var partNumber by remember { mutableStateOf(existing?.partNumber ?: "") }
    var costText by remember { mutableStateOf(existing?.cost?.toString() ?: "0") }
    var dateIso by remember { mutableStateOf(existing?.dateIso ?: LocalDate.now().format(DateTimeFormatter.ISO_DATE)) }
    var odoText by remember { mutableStateOf(existing?.odometerKm?.toInt()?.toString() ?: activeBike.totalOdometerKm.toInt().toString()) }
    var notes by remember { mutableStateOf(existing?.notes ?: "") }
    var showPartNameError by remember { mutableStateOf(false) }
    val currency = activeBike.currencySymbol.ifBlank { "€" }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = TftSurfaceDark,
        title = {
            Text(
                text = if (existing != null) tr("Edit Part / Mod", "Upraviť diel / úpravu") else tr("Add Installed Part / Mod", "Pridať namontovaný diel / úpravu"),
                color = Color.White,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                OutlinedTextField(
                    value = partName,
                    onValueChange = {
                        partName = it
                        if (showPartNameError && it.isNotBlank()) showPartNameError = false
                    },
                    label = { Text(tr("Part Name (e.g., Akrapovic Exhaust, DID Chain)", "Názov dielu (napr. Akrapovič výfuk, DID reťaz)")) },
                    isError = showPartNameError,
                    supportingText = if (showPartNameError) {
                        { Text(tr("Part name is required", "Názov dielu je povinný"), color = RedlineCrimson, fontSize = 11.sp) }
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
                Spacer(modifier = Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = brand,
                        onValueChange = { brand = it },
                        label = { Text(tr("Brand / OEM", "Značka / OEM")) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = RacingAmberPrimary,
                            unfocusedBorderColor = TitaniumBorder,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        ),
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = partNumber,
                        onValueChange = { partNumber = it },
                        label = { Text(tr("Part #", "Kód dielu #")) },
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
                        label = { Text(tr("Cost ($currency)", "Cena ($currency)")) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = RacingAmberPrimary,
                            unfocusedBorderColor = TitaniumBorder,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        ),
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = odoText,
                        onValueChange = { odoText = it },
                        label = { Text(tr("Odo (km)", "Tachometer (km)")) },
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
                    value = dateIso,
                    onValueChange = { dateIso = it },
                    label = { Text(tr("Date (YYYY-MM-DD)", "Dátum (RRRR-MM-DD)")) },
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
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text(tr("Notes / Tightening Torque (Nm)", "Poznámky / Uťahovací moment (Nm)")) },
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
                    if (partName.isBlank()) {
                        showPartNameError = true
                        return@Button
                    }
                    val cost = costText.toSafeDouble(0.0)
                    val odo = odoText.toSafeDouble(activeBike.totalOdometerKm)
                    val entity = PartLogEntity(
                        partId = existing?.partId ?: 0L,
                        motorcycleId = activeBike.id,
                        dateIso = dateIso.ifBlank { LocalDate.now().format(DateTimeFormatter.ISO_DATE) },
                        odometerKm = odo,
                        partName = partName.trim(),
                        brand = brand.ifBlank { "Aftermarket" },
                        partNumber = partNumber,
                        cost = cost,
                        notes = notes
                    )
                    onSave(entity)
                },
                colors = ButtonDefaults.buttonColors(containerColor = RacingAmberPrimary)
            ) {
                Text(tr("Save Part", "Uložiť diel"), fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(tr("Cancel", "Zrušiť"), color = Color.Gray) }
        }
    )
}

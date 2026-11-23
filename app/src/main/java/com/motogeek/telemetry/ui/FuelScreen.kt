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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.motogeek.telemetry.data.local.FuelLogEntity
import com.motogeek.telemetry.domain.toSafeDouble
import com.motogeek.telemetry.ui.components.SegmentedDigitalText
import com.motogeek.telemetry.ui.theme.*
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FuelScreen(viewModel: MainViewModel, navController: NavController) {
    val activeBike by viewModel.activeMotorcycle.collectAsState()
    val fuelLogs by viewModel.currentFuelLogs.collectAsState()
    val fuelStats by viewModel.fuelEconomyStats.collectAsState()

    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val appLang = LocalAppLang.current

    var showAddEditDialog by remember { mutableStateOf(false) }
    var editingFuelLog by remember { mutableStateOf<FuelLogEntity?>(null) }
    var pendingDeleteFuelLog by remember { mutableStateOf<FuelLogEntity?>(null) }

    val currency = activeBike?.currencySymbol?.ifBlank { "€" } ?: "€"

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = tr("Fuel & Consumption", "Palivo & spotreba"),
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
                        editingFuelLog = null
                        showAddEditDialog = true
                    }) {
                        Text(tr("+ ADD FILL-UP", "+ PRIDAŤ TANKOVANIE"), color = RacingAmberPrimary, fontWeight = FontWeight.Bold)
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
                        text = tr("FUEL ECONOMY OVERVIEW", "PREHĽAD SPOTREBY PALIVA"),
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
                            Text(tr("AVG CONSUMPTION", "PRIEM. SPOTREBA"), color = Color.Gray, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
                            SegmentedDigitalText(
                                text = if (fuelStats.avgLitersPer100Km > 0) String.format("%.2f", fuelStats.avgLitersPer100Km) else "—",
                                unit = "L/100",
                                color = TelemetryIceCyan,
                                fontSize = 18.sp
                            )
                        }
                        Column {
                            Text(tr("EFFICIENCY", "EFEKTIVITA"), color = Color.Gray, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
                            SegmentedDigitalText(
                                text = if (fuelStats.avgKmPerLiter > 0) String.format("%.1f", fuelStats.avgKmPerLiter) else "—",
                                unit = "KM/L",
                                color = ApexEmeraldGreen,
                                fontSize = 18.sp
                            )
                        }
                        Column {
                            Text(tr("TOTAL SPENT", "CELKOM MINUTÉ"), color = Color.Gray, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
                            SegmentedDigitalText(
                                text = String.format("%.0f", fuelStats.totalFuelCost),
                                unit = currency,
                                color = RacingAmberPrimary,
                                fontSize = 18.sp
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                    HorizontalDivider(color = TitaniumBorder.copy(alpha = 0.5f))
                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = tr("Total Liters: ${String.format("%.1f", fuelStats.totalLiters)} L", "Celkom: ${String.format("%.1f", fuelStats.totalLiters)} L"),
                            color = Color.LightGray,
                            fontSize = 11.sp
                        )
                        Text(
                            text = tr("Tracked: ${fuelStats.totalFuelDistanceKm.toInt()} km", "Sledované: ${fuelStats.totalFuelDistanceKm.toInt()} km"),
                            color = Color.LightGray,
                            fontSize = 11.sp
                        )
                        Text(
                            text = tr("Fill-ups: ${fuelLogs.size}", "Tankovania: ${fuelLogs.size}"),
                            color = Color.LightGray,
                            fontSize = 11.sp
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Fuel Logs List
            if (fuelLogs.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(top = 40.dp),
                    contentAlignment = Alignment.TopCenter
                ) {
                    Text(
                        text = tr(
                            "No fuel logs yet. Tap '+ ADD FILL-UP' to log your first tank.",
                            "Zatiaľ žiadne záznamy tankovania. Klepnite na '+ PRIDAŤ TANKOVANIE' pre prvý záznam."
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
                    items(fuelLogs, key = { it.fuelId }) { log ->
                        FuelLogCard(
                            log = log,
                            currency = currency,
                            onEdit = {
                                editingFuelLog = log
                                showAddEditDialog = true
                            },
                            onDelete = {
                                pendingDeleteFuelLog = log
                            }
                        )
                    }
                }
            }
        }
    }

    if (showAddEditDialog && activeBike != null) {
        AddEditFuelDialog(
            activeBike = activeBike!!,
            existing = editingFuelLog,
            onDismiss = { showAddEditDialog = false },
            onSave = { entity ->
                if (editingFuelLog != null) {
                    viewModel.updateFuelLog(entity)
                    scope.launch { snackbarHostState.showSnackbar(appLang.pick("Fuel log updated", "Záznam tankovania aktualizovaný")) }
                } else {
                    viewModel.addFuelLog(entity)
                    scope.launch { snackbarHostState.showSnackbar(appLang.pick("Fill-up logged", "Tankovanie zaznamenané")) }
                }
                showAddEditDialog = false
            }
        )
    }

    pendingDeleteFuelLog?.let { logToDelete ->
        AlertDialog(
            onDismissRequest = { pendingDeleteFuelLog = null },
            containerColor = TftSurfaceDark,
            title = {
                Text(
                    text = tr("Delete Fuel Log", "Vymazať záznam tankovania"),
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    text = tr(
                        "Are you sure you want to delete the ${logToDelete.liters} L fill-up from ${logToDelete.dateIso}? This cannot be undone.",
                        "Naozaj chcete vymazať tankovanie ${logToDelete.liters} L zo dňa ${logToDelete.dateIso}? Túto akciu nemožno vrátiť späť."
                    ),
                    color = Color.LightGray
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteFuelLog(logToDelete.fuelId)
                    pendingDeleteFuelLog = null
                    scope.launch {
                        snackbarHostState.showSnackbar(appLang.pick("Fuel log deleted", "Záznam tankovania vymazaný"))
                    }
                }) {
                    Text(tr("Delete", "Vymazať"), color = RedlineCrimson, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDeleteFuelLog = null }) {
                    Text(tr("Cancel", "Zrušiť"), color = Color.Gray)
                }
            }
        )
    }
}

@Composable
private fun FuelLogCard(
    log: FuelLogEntity,
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
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "${log.liters} L",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    if (log.isFullTank) {
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = ApexEmeraldGreen.copy(alpha = 0.15f),
                            border = BorderStroke(1.dp, ApexEmeraldGreen.copy(alpha = 0.5f))
                        ) {
                            Text(
                                text = tr("FULL TANK", "PLNÁ NÁDRŽ"),
                                color = ApexEmeraldGreen,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "${log.dateIso} • ${tr("Odo", "Tach")}: ${log.odometerKm.toInt()} km",
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
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "${String.format("%.2f", log.totalCost)} $currency",
                        color = RacingAmberPrimary,
                        fontWeight = FontWeight.Black,
                        fontSize = 15.sp,
                        fontFamily = FontFamily.Monospace
                    )
                    if (log.pricePerLiter > 0) {
                        Text(
                            text = "${log.pricePerLiter} $currency/L",
                            color = Color.Gray,
                            fontSize = 11.sp
                        )
                    }
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
private fun AddEditFuelDialog(
    activeBike: com.motogeek.telemetry.data.local.MotorcycleEntity,
    existing: FuelLogEntity?,
    onDismiss: () -> Unit,
    onSave: (FuelLogEntity) -> Unit
) {
    var dateIso by remember { mutableStateOf(existing?.dateIso ?: LocalDate.now().format(DateTimeFormatter.ISO_DATE)) }
    var odoText by remember { mutableStateOf(existing?.odometerKm?.toInt()?.toString() ?: activeBike.totalOdometerKm.toInt().toString()) }
    var litersText by remember { mutableStateOf(existing?.liters?.toString() ?: "") }
    var pricePerLiterText by remember { mutableStateOf(existing?.pricePerLiter?.toString() ?: "") }
    var totalCostText by remember { mutableStateOf(existing?.totalCost?.toString() ?: "") }
    var isFullTank by remember { mutableStateOf(existing?.isFullTank ?: true) }
    var notes by remember { mutableStateOf(existing?.notes ?: "") }
    var showLitersError by remember { mutableStateOf(false) }
    val currency = activeBike.currencySymbol.ifBlank { "€" }

    // Auto-compute total cost if liters and pricePerLiter change
    LaunchedEffect(litersText, pricePerLiterText) {
        val l = litersText.toSafeDouble()
        val p = pricePerLiterText.toSafeDouble()
        if (l > 0 && p > 0 && totalCostText.isBlank()) {
            totalCostText = String.format("%.2f", l * p).replace(',', '.')
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = TftSurfaceDark,
        title = {
            Text(
                text = if (existing != null) tr("Edit Fuel Fill-Up", "Upraviť tankovanie") else tr("Record Fuel Fill-Up", "Zaznamenať tankovanie"),
                color = Color.White,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = litersText,
                        onValueChange = {
                            litersText = it
                            if (showLitersError) showLitersError = false
                        },
                        label = { Text(tr("Liters (L)", "Litre (L)")) },
                        isError = showLitersError,
                        supportingText = if (showLitersError) {
                            { Text(tr("Enter liters > 0", "Zadajte litre > 0"), color = RedlineCrimson, fontSize = 11.sp) }
                        } else null,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = RacingAmberPrimary,
                            unfocusedBorderColor = TitaniumBorder,
                            errorBorderColor = RedlineCrimson,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        ),
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = pricePerLiterText,
                        onValueChange = { pricePerLiterText = it },
                        label = { Text(tr("Price/L", "Cena/L")) },
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
                    value = totalCostText,
                    onValueChange = { totalCostText = it },
                    label = { Text(tr("Total Cost ($currency)", "Celková cena ($currency)")) },
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
                    label = { Text(tr("Fuel Station / Brand / Notes", "Čerpacia stanica / Značka / Poznámky")) },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = RacingAmberPrimary,
                        unfocusedBorderColor = TitaniumBorder,
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(tr("Full Tank Fill-up", "Plná nádrž"), color = Color.LightGray, fontSize = 13.sp)
                    Switch(
                        checked = isFullTank,
                        onCheckedChange = { isFullTank = it },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = ApexEmeraldGreen
                        )
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val liters = litersText.toSafeDouble()
                    if (liters <= 0.0) {
                        showLitersError = true
                        return@Button
                    }
                    val pricePerL = pricePerLiterText.toSafeDouble()
                    val total = totalCostText.toSafeDouble(liters * pricePerL)
                    val odo = odoText.toSafeDouble(activeBike.totalOdometerKm)

                    val entity = FuelLogEntity(
                        fuelId = existing?.fuelId ?: 0L,
                        motorcycleId = activeBike.id,
                        dateIso = dateIso.ifBlank { LocalDate.now().format(DateTimeFormatter.ISO_DATE) },
                        odometerKm = odo,
                        liters = liters,
                        pricePerLiter = pricePerL,
                        totalCost = total,
                        isFullTank = isFullTank,
                        notes = notes
                    )
                    onSave(entity)
                },
                colors = ButtonDefaults.buttonColors(containerColor = RacingAmberPrimary)
            ) {
                Text(tr("Save Fill-up", "Uložiť tankovanie"), fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(tr("Cancel", "Zrušiť"), color = Color.Gray) }
        }
    )
}

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

    var showAddEditDialog by remember { mutableStateOf(false) }
    var editingFuelLog by remember { mutableStateOf<FuelLogEntity?>(null) }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Fuel & Consumption",
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
                        editingFuelLog = null
                        showAddEditDialog = true
                    }) {
                        Text("+ ADD FILL-UP", color = RacingAmberPrimary, fontWeight = FontWeight.Bold)
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

            // Summary Header Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp),
                colors = CardDefaults.cardColors(containerColor = TftSurfaceDark),
                border = BorderStroke(1.dp, TitaniumBorder)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        text = "FUEL ECONOMY OVERVIEW",
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
                            Text("AVG CONSUMPTION", color = Color.Gray, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
                            SegmentedDigitalText(
                                text = if (fuelStats.avgLitersPer100Km > 0) String.format("%.2f", fuelStats.avgLitersPer100Km) else "—",
                                unit = "L/100",
                                color = TelemetryIceCyan,
                                fontSize = 18.sp
                            )
                        }
                        Column {
                            Text("EFFICIENCY", color = Color.Gray, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
                            SegmentedDigitalText(
                                text = if (fuelStats.avgKmPerLiter > 0) String.format("%.1f", fuelStats.avgKmPerLiter) else "—",
                                unit = "KM/L",
                                color = ApexEmeraldGreen,
                                fontSize = 18.sp
                            )
                        }
                        Column {
                            Text("TOTAL SPENT", color = Color.Gray, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
                            SegmentedDigitalText(
                                text = String.format("%.0f", fuelStats.totalFuelCost),
                                unit = activeBike!!.currencySymbol,
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
                            text = "Total Liters: ${String.format("%.1f", fuelStats.totalLiters)} L",
                            color = Color.LightGray,
                            fontSize = 11.sp
                        )
                        Text(
                            text = "Total Tracked: ${fuelStats.totalFuelDistanceKm.toInt()} km",
                            color = Color.LightGray,
                            fontSize = 11.sp
                        )
                        Text(
                            text = "Fill-ups: ${fuelLogs.size}",
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
                        text = "No fuel logs yet. Tap '+ ADD FILL-UP' to log your first tank.",
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
                            currency = activeBike!!.currencySymbol,
                            onEdit = {
                                editingFuelLog = log
                                showAddEditDialog = true
                            },
                            onDelete = {
                                scope.launch {
                                    viewModel.deleteFuelLog(log.fuelId)
                                    snackbarHostState.showSnackbar("Fuel log deleted")
                                }
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
                    scope.launch { snackbarHostState.showSnackbar("Fuel log updated") }
                } else {
                    viewModel.addFuelLog(entity)
                    scope.launch { snackbarHostState.showSnackbar("Fill-up logged") }
                }
                showAddEditDialog = false
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
                                text = "FULL TANK",
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
                    text = "${log.dateIso} • Odo: ${log.odometerKm.toInt()} km",
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
                    Icon(Icons.Default.Delete, contentDescription = "Delete", tint = RedlineCrimson.copy(alpha = 0.7f), modifier = Modifier.size(20.dp))
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
                text = if (existing != null) "Edit Fuel Fill-Up" else "Record Fuel Fill-Up",
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
                        label = { Text("Date (YYYY-MM-DD)") },
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
                        label = { Text("Odo (km)") },
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
                        onValueChange = { litersText = it },
                        label = { Text("Liters (L)") },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = RacingAmberPrimary,
                            unfocusedBorderColor = TitaniumBorder,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        ),
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = pricePerLiterText,
                        onValueChange = { pricePerLiterText = it },
                        label = { Text("Price/L") },
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
                    label = { Text("Total Cost (${activeBike.currencySymbol})") },
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
                    label = { Text("Fuel Station / Brand / Notes") },
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
                    Text("Full Tank Fill-up", color = Color.LightGray, fontSize = 13.sp)
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
                Text("Save Fill-up", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = Color.Gray) }
        }
    )
}

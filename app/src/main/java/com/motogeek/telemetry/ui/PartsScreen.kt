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

    var showAddEditDialog by remember { mutableStateOf(false) }
    var editingPartLog by remember { mutableStateOf<PartLogEntity?>(null) }

    val totalPartsCount = partLogs.size
    val totalInvested = partLogs.sumOf { it.cost }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Parts & Modifications",
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
                        editingPartLog = null
                        showAddEditDialog = true
                    }) {
                        Text("+ ADD PART", color = RacingAmberPrimary, fontWeight = FontWeight.Bold)
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
                        text = "PARTS & MODIFICATIONS SUMMARY",
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
                            Text("INSTALLED PARTS", color = Color.Gray, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
                            SegmentedDigitalText(
                                text = "$totalPartsCount",
                                unit = "ITEMS",
                                color = TelemetryIceCyan,
                                fontSize = 18.sp
                            )
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text("TOTAL INVESTED", color = Color.Gray, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
                            SegmentedDigitalText(
                                text = String.format("%,d", totalInvested.toInt()),
                                unit = activeBike!!.currencySymbol,
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
                        text = "No parts or mods recorded. Tap '+ ADD PART' to add exhaust, tires, sprockets, etc.",
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
                            currency = activeBike!!.currencySymbol,
                            onEdit = {
                                editingPartLog = part
                                showAddEditDialog = true
                            },
                            onDelete = {
                                scope.launch {
                                    viewModel.deletePartLog(part.partId)
                                    snackbarHostState.showSnackbar("Part log deleted")
                                }
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
                    scope.launch { snackbarHostState.showSnackbar("Part updated") }
                } else {
                    viewModel.addPartLog(entity)
                    scope.launch { snackbarHostState.showSnackbar("Part installed and logged") }
                }
                showAddEditDialog = false
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
                    text = "Installed on ${part.dateIso} at ${part.odometerKm.toInt()} km",
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
                    Icon(Icons.Default.Delete, contentDescription = "Delete", tint = RedlineCrimson.copy(alpha = 0.7f), modifier = Modifier.size(20.dp))
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

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = TftSurfaceDark,
        title = {
            Text(
                text = if (existing != null) "Edit Part / Mod" else "Add Installed Part / Mod",
                color = Color.White,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                OutlinedTextField(
                    value = partName,
                    onValueChange = { partName = it },
                    label = { Text("Part Name (e.g., Akrapovic Exhaust, DID Chain)") },
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
                        value = brand,
                        onValueChange = { brand = it },
                        label = { Text("Brand / OEM") },
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
                        label = { Text("Part #") },
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
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Notes / Tightening Torque (Nm)") },
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
                    val cost = costText.toSafeDouble(0.0)
                    val odo = odoText.toSafeDouble(activeBike.totalOdometerKm)
                    val entity = PartLogEntity(
                        partId = existing?.partId ?: 0L,
                        motorcycleId = activeBike.id,
                        dateIso = dateIso.ifBlank { LocalDate.now().format(DateTimeFormatter.ISO_DATE) },
                        odometerKm = odo,
                        partName = partName.ifBlank { "Custom Part" },
                        brand = brand.ifBlank { "Aftermarket" },
                        partNumber = partNumber,
                        cost = cost,
                        notes = notes
                    )
                    onSave(entity)
                },
                colors = ButtonDefaults.buttonColors(containerColor = RacingAmberPrimary)
            ) {
                Text("Save Part", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = Color.Gray) }
        }
    )
}

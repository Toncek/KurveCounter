package com.motogeek.telemetry.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
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
import com.motogeek.telemetry.data.local.MotorcycleEntity
import com.motogeek.telemetry.domain.maintenance.TechnicalSpecsDto
import com.motogeek.telemetry.domain.toSafeDouble
import com.motogeek.telemetry.domain.toSafeInt
import com.motogeek.telemetry.ui.components.ActiveBikeDropdownCard
import com.motogeek.telemetry.ui.components.SegmentedDigitalText
import com.motogeek.telemetry.ui.theme.*
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BikeInfoScreen(viewModel: MainViewModel, navController: NavController) {
    val activeBike by viewModel.activeMotorcycle.collectAsState()
    val allBikes by viewModel.allMotorcycles.collectAsState()
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    var name by remember(activeBike) { mutableStateOf(activeBike?.name ?: "") }
    var type by remember(activeBike) { mutableStateOf(activeBike?.type ?: "Naked") }
    var make by remember(activeBike) { mutableStateOf(activeBike?.make ?: "") }
    var model by remember(activeBike) { mutableStateOf(activeBike?.model ?: "") }
    var yearText by remember(activeBike) { mutableStateOf(activeBike?.year?.toString() ?: "2024") }
    var ccText by remember(activeBike) { mutableStateOf(activeBike?.engineDisplacementCc?.toString() ?: "890") }
    var startDateIso by remember(activeBike) { mutableStateOf(activeBike?.startDateIso ?: "") }
    var baseOdoText by remember(activeBike) { mutableStateOf(activeBike?.baseOdometerKm?.toInt()?.toString() ?: "0") }
    var buyingPriceText by remember(activeBike) { mutableStateOf(activeBike?.buyingPrice?.toString() ?: "0") }
    var currencySymbol by remember(activeBike) { mutableStateOf(activeBike?.currencySymbol ?: "€") }

    var showNewBikeDialog by remember { mutableStateOf(false) }
    var showAddTorqueDialog by remember { mutableStateOf(false) }
    var showEditSpecsDialog by remember { mutableStateOf(false) }
    var showDeleteBikeConfirm by remember { mutableStateOf(false) }
    var useBarUnits by remember { mutableStateOf(false) }
    var torqueSearchQuery by remember { mutableStateOf("") }

    // Parse technical specs & bolt torques
    val json = remember { Json { ignoreUnknownKeys = true; isLenient = true; encodeDefaults = true } }
    val specs = remember(activeBike?.technicalSpecsJson) {
        try {
            if (!activeBike?.technicalSpecsJson.isNullOrBlank() && activeBike?.technicalSpecsJson != "{}") {
                json.decodeFromString<TechnicalSpecsDto>(activeBike!!.technicalSpecsJson)
            } else TechnicalSpecsDto()
        } catch (_: Exception) {
            TechnicalSpecsDto()
        }
    }

    // Consolidated torque map (all 11 named fields + torquesNm + boltTorquesNm)
    val allTorques = remember(specs) {
        specs.toConsolidatedTorqueMap()
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Bike Profile & Torques",
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
                    TextButton(onClick = { showNewBikeDialog = true }) {
                        Text("+ NEW BIKE", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                    Button(
                        onClick = {
                            activeBike?.let { bike ->
                                scope.launch {
                                    val updated = bike.copy(
                                        name = name.ifBlank { "$make $model".trim() },
                                        type = type,
                                        make = make,
                                        model = model,
                                        year = yearText.toSafeInt(2024),
                                        engineDisplacementCc = ccText.toSafeInt(890),
                                        startDateIso = startDateIso,
                                        baseOdometerKm = baseOdoText.toSafeDouble(0.0),
                                        buyingPrice = buyingPriceText.toSafeDouble(0.0),
                                        currencySymbol = currencySymbol
                                    )
                                    viewModel.repository.updateMotorcycle(updated)
                                    viewModel.repository.logActivity(
                                        bike.id,
                                        "SYSTEM",
                                        "Profile Updated",
                                        "Saved changes for ${updated.displayName}"
                                    )
                                    snackbarHostState.showSnackbar("Motorcycle profile saved successfully!")
                                }
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = RacingAmberPrimary),
                        shape = RoundedCornerShape(6.dp),
                        modifier = Modifier.padding(end = 8.dp)
                    ) {
                        Text("SAVE", fontWeight = FontWeight.Bold)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = CarbonObsidianBg)
            )
        },
        containerColor = CarbonObsidianBg
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Spacer(modifier = Modifier.height(2.dp))

            // Active Bike Dropdown Header
            ActiveBikeDropdownCard(
                activeBike = activeBike,
                allBikes = allBikes,
                onBikeSelected = { id -> viewModel.selectActiveMotorcycle(id) },
                onAddNewBike = { showNewBikeDialog = true },
                onSyncOdometer = { newKm -> viewModel.updateActiveBikeOdometer(newKm) },
                showOdometer = true
            )

            // Current Odometer & Calibration Banner
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
                    Column {
                        Text("CURRENT TOTAL ODOMETER", color = Color(0xFF6B7A99), fontSize = 10.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                        SegmentedDigitalText(
                            text = String.format("%,d", activeBike?.totalOdometerKm?.toInt() ?: 0),
                            unit = "KM",
                            color = TelemetryIceCyan,
                            fontSize = 22.sp
                        )
                        Text(
                            text = "Base: ${activeBike?.baseOdometerKm?.toInt() ?: 0} km • Tracked: ${activeBike?.accumulatedTrackedKm?.toInt() ?: 0} km • Offset: ${activeBike?.manualAdjustmentOffsetKm?.toInt() ?: 0} km",
                            color = Color.Gray,
                            fontSize = 10.sp
                        )
                    }
                    Button(
                        onClick = {
                            activeBike?.let {
                                viewModel.updateActiveBikeOdometer(it.totalOdometerKm)
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = TftSurfaceElevated),
                        shape = RoundedCornerShape(6.dp),
                        border = BorderStroke(1.dp, TitaniumBorder)
                    ) {
                        Text("CALIBRATE", color = RacingAmberPrimary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            // General Bike Details Form Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp),
                colors = CardDefaults.cardColors(containerColor = TftSurfaceDark),
                border = BorderStroke(1.dp, TitaniumBorder)
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "MOTORCYCLE SPECIFICATIONS",
                        color = Color(0xFF6B7A99),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        letterSpacing = 1.sp
                    )

                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("Display Name / Nickname") },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = RacingAmberPrimary,
                            unfocusedBorderColor = TitaniumBorder,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = make,
                            onValueChange = { make = it },
                            label = { Text("Make (e.g., Yamaha)") },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = RacingAmberPrimary,
                                unfocusedBorderColor = TitaniumBorder,
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White
                            ),
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = model,
                            onValueChange = { model = it },
                            label = { Text("Model (e.g., MT-09)") },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = RacingAmberPrimary,
                                unfocusedBorderColor = TitaniumBorder,
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White
                            ),
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = yearText,
                            onValueChange = { yearText = it },
                            label = { Text("Year") },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = RacingAmberPrimary,
                                unfocusedBorderColor = TitaniumBorder,
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White
                            ),
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = ccText,
                            onValueChange = { ccText = it },
                            label = { Text("Displacement (cc)") },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = RacingAmberPrimary,
                                unfocusedBorderColor = TitaniumBorder,
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White
                            ),
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = type,
                            onValueChange = { type = it },
                            label = { Text("Category") },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = RacingAmberPrimary,
                                unfocusedBorderColor = TitaniumBorder,
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White
                            ),
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = baseOdoText,
                            onValueChange = { baseOdoText = it },
                            label = { Text("Starting Odometer (km)") },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = RacingAmberPrimary,
                                unfocusedBorderColor = TitaniumBorder,
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White
                            ),
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = buyingPriceText,
                            onValueChange = { buyingPriceText = it },
                            label = { Text("Purchase Price") },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = RacingAmberPrimary,
                                unfocusedBorderColor = TitaniumBorder,
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White
                            ),
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = currencySymbol,
                            onValueChange = { currencySymbol = it },
                            label = { Text("Currency") },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = RacingAmberPrimary,
                                unfocusedBorderColor = TitaniumBorder,
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White
                            ),
                            modifier = Modifier.width(80.dp)
                        )
                    }
                }
            }

            // Factory Specs & Bolt Torques Card
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
                        Text(
                            text = "FACTORY SPECS & BOLT TORQUES",
                            color = Color(0xFF6B7A99),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            letterSpacing = 1.sp
                        )
                        Row {
                            TextButton(onClick = { useBarUnits = !useBarUnits }) {
                                Text(if (useBarUnits) "UNIT: BAR" else "UNIT: PSI", color = TelemetryIceCyan, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            }
                            TextButton(onClick = { showEditSpecsDialog = true }) {
                                Text("EDIT SPECS", color = ApexEmeraldGreen, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            }
                            TextButton(onClick = { showAddTorqueDialog = true }) {
                                Text("+ TORQUE", color = RacingAmberPrimary, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    val frontPsi = specs.frontTirePressurePsi ?: 36.0
                    val rearPsi = specs.rearTirePressurePsi ?: 42.0
                    val frontPressureStr = if (useBarUnits) String.format("%.2f BAR", frontPsi / 14.5038) else "${frontPsi.toInt()} PSI"
                    val rearPressureStr = if (useBarUnits) String.format("%.2f BAR", rearPsi / 14.5038) else "${rearPsi.toInt()} PSI"

                    // Key Fluid & Tire Pressure Chips
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        SpecPill("Oil Cap", "${specs.oilCapacityLiters ?: 3.2} L", TelemetryIceCyan, Modifier.weight(1f))
                        SpecPill("Front Tire", frontPressureStr, ApexEmeraldGreen, Modifier.weight(1f))
                        SpecPill("Rear Tire", rearPressureStr, ApexEmeraldGreen, Modifier.weight(1f))
                        SpecPill("Chain Slack", specs.chainSlackMm ?: "25-35 mm", WarningGold, Modifier.weight(1f))
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        SpecPill("Oil Grade", specs.oilViscosityAndSpec.ifBlank { "10W-40" }.take(10), TelemetryIceCyan, Modifier.weight(1f))
                        SpecPill("Coolant", "${specs.coolantCapacityLiters ?: 1.8} L", TelemetryIceCyan, Modifier.weight(1f))
                        SpecPill("Brake Fluid", specs.brakeFluidType.ifBlank { "DOT 4" }, WarningGold, Modifier.weight(1f))
                        SpecPill("Spark Plug", specs.sparkPlugModel.ifBlank { "OEM" }.take(10), RacingAmberPrimary, Modifier.weight(1f))
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = torqueSearchQuery,
                        onValueChange = { torqueSearchQuery = it },
                        placeholder = { Text("Search bolt or nut torque (Nm)...", color = Color.Gray, fontSize = 12.sp) },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(18.dp)) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = RacingAmberPrimary,
                            unfocusedBorderColor = TitaniumBorder,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        ),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    val filteredTorques = allTorques.filter {
                        it.key.contains(torqueSearchQuery, ignoreCase = true)
                    }

                    if (filteredTorques.isEmpty()) {
                        Text(
                            text = "No bolt torques found. Tap '+ QUICK ADD TORQUE' to add one.",
                            color = Color.Gray,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(vertical = 12.dp)
                        )
                    } else {
                        filteredTorques.forEach { (boltName, torqueNm) ->
                            val isCustomBolt = specs.boltTorquesNm?.containsKey(boltName) == true
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 5.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = boltName,
                                    color = Color.White,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium,
                                    modifier = Modifier.weight(1f)
                                )
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = "${torqueNm.toInt()} Nm",
                                        color = RacingAmberPrimary,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = FontFamily.Monospace
                                    )
                                    if (isCustomBolt && activeBike != null) {
                                        IconButton(
                                            onClick = {
                                                scope.launch {
                                                    val updatedTorques = (specs.boltTorquesNm ?: emptyMap()).toMutableMap().apply {
                                                        remove(boltName)
                                                    }
                                                    val updatedSpecs = specs.copy(boltTorquesNm = updatedTorques)
                                                    val updatedBike = activeBike!!.copy(technicalSpecsJson = json.encodeToString(updatedSpecs))
                                                    viewModel.repository.updateMotorcycle(updatedBike)
                                                    snackbarHostState.showSnackbar("Removed custom torque: $boltName")
                                                }
                                            },
                                            modifier = Modifier.size(28.dp)
                                        ) {
                                            Icon(Icons.Default.Close, contentDescription = "Delete torque", tint = Color.Gray, modifier = Modifier.size(14.dp))
                                        }
                                    }
                                }
                            }
                            HorizontalDivider(color = TitaniumBorder.copy(alpha = 0.4f), thickness = 0.5.dp)
                        }
                    }
                }
            }

            if (allBikes.size > 1 && activeBike != null) {
                OutlinedButton(
                    onClick = { showDeleteBikeConfirm = true },
                    border = BorderStroke(1.dp, RedlineCrimson),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Delete, contentDescription = null, tint = RedlineCrimson, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Delete Motorcycle (${activeBike?.displayName})", color = RedlineCrimson, fontWeight = FontWeight.Bold)
                }
            }

            Spacer(modifier = Modifier.height(30.dp))
        }
    }

    if (showDeleteBikeConfirm && activeBike != null) {
        AlertDialog(
            onDismissRequest = { showDeleteBikeConfirm = false },
            containerColor = TftSurfaceDark,
            title = { Text("Delete ${activeBike?.displayName}?", color = Color.White, fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    "This will permanently delete ${activeBike?.displayName} and its associated logs. Your other motorcycles will remain untouched.",
                    color = Color.LightGray,
                    fontSize = 13.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val idToDelete = activeBike!!.id
                        showDeleteBikeConfirm = false
                        viewModel.deleteMotorcycle(idToDelete)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = RedlineCrimson)
                ) {
                    Text("Delete Motorcycle", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteBikeConfirm = false }) {
                    Text("Cancel", color = Color.Gray)
                }
            }
        )
    }

    if (showEditSpecsDialog && activeBike != null) {
        EditTechnicalSpecsDialog(
            initialSpecs = specs,
            onDismiss = { showEditSpecsDialog = false },
            onSave = { updatedSpecs ->
                scope.launch {
                    val updatedBike = activeBike!!.copy(technicalSpecsJson = json.encodeToString(updatedSpecs))
                    viewModel.repository.updateMotorcycle(updatedBike)
                    showEditSpecsDialog = false
                    snackbarHostState.showSnackbar("Technical & fluid specifications updated!")
                }
            }
        )
    }

    if (showNewBikeDialog) {
        AddNewBikeDialog(
            onDismiss = { showNewBikeDialog = false },
            onSave = { name, make, model, year, cc, odo, price, type ->
                viewModel.addNewMotorcycle(name, make, model, year, cc, odo, price, type)
                showNewBikeDialog = false
                scope.launch { snackbarHostState.showSnackbar("Added $make $model to garage!") }
            }
        )
    }

    if (showAddTorqueDialog && activeBike != null) {
        QuickAddTorqueDialog(
            onDismiss = { showAddTorqueDialog = false },
            onSave = { boltName, torqueNm ->
                scope.launch {
                    val updatedTorques = (specs.boltTorquesNm ?: emptyMap()).toMutableMap().apply {
                        put(boltName, torqueNm)
                    }
                    val updatedSpecs = specs.copy(boltTorquesNm = updatedTorques)
                    val updatedBike = activeBike!!.copy(technicalSpecsJson = json.encodeToString(updatedSpecs))
                    viewModel.repository.updateMotorcycle(updatedBike)
                    snackbarHostState.showSnackbar("Added torque spec: $boltName = ${torqueNm.toInt()} Nm")
                    showAddTorqueDialog = false
                }
            }
        )
    }
}

@Composable
private fun SpecPill(label: String, value: String, color: Color, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(6.dp),
        color = TftSurfaceElevated,
        border = BorderStroke(1.dp, TitaniumBorder)
    ) {
        Column(
            modifier = Modifier.padding(6.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(label, color = Color.Gray, fontSize = 9.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(2.dp))
            Text(value, color = color, fontSize = 11.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
        }
    }
}

@Composable
private fun EditTechnicalSpecsDialog(
    initialSpecs: TechnicalSpecsDto,
    onDismiss: () -> Unit,
    onSave: (TechnicalSpecsDto) -> Unit
) {
    var oilCap by remember { mutableStateOf(initialSpecs.oilCapacityLiters?.toString() ?: "3.2") }
    var oilVisc by remember { mutableStateOf(initialSpecs.oilViscosityAndSpec.ifBlank { "10W-40" }) }
    var coolantCap by remember { mutableStateOf(initialSpecs.coolantCapacityLiters?.toString() ?: "1.8") }
    var brakeFluid by remember { mutableStateOf(initialSpecs.brakeFluidType.ifBlank { "DOT 4" }) }
    var frontPsi by remember { mutableStateOf(initialSpecs.frontTirePressurePsi?.toString() ?: "36.0") }
    var rearPsi by remember { mutableStateOf(initialSpecs.rearTirePressurePsi?.toString() ?: "42.0") }
    var chainSlack by remember { mutableStateOf(initialSpecs.chainSlackMm ?: "25-35") }
    var sparkPlug by remember { mutableStateOf(initialSpecs.sparkPlugModel) }
    var valveIntake by remember { mutableStateOf(initialSpecs.valveClearanceIntakeMm) }
    var valveExhaust by remember { mutableStateOf(initialSpecs.valveClearanceExhaustMm) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = TftSurfaceDark,
        title = { Text("Edit Fluid & Workshop Specs", color = Color.White, fontWeight = FontWeight.Bold) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = oilCap,
                        onValueChange = { oilCap = it },
                        label = { Text("Oil w/ Filter (L)") },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = RacingAmberPrimary,
                            unfocusedBorderColor = TitaniumBorder,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        ),
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = oilVisc,
                        onValueChange = { oilVisc = it },
                        label = { Text("Oil Viscosity") },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = RacingAmberPrimary,
                            unfocusedBorderColor = TitaniumBorder,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        ),
                        modifier = Modifier.weight(1f)
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = coolantCap,
                        onValueChange = { coolantCap = it },
                        label = { Text("Coolant (L)") },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = RacingAmberPrimary,
                            unfocusedBorderColor = TitaniumBorder,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        ),
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = brakeFluid,
                        onValueChange = { brakeFluid = it },
                        label = { Text("Brake Fluid") },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = RacingAmberPrimary,
                            unfocusedBorderColor = TitaniumBorder,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        ),
                        modifier = Modifier.weight(1f)
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = frontPsi,
                        onValueChange = { frontPsi = it },
                        label = { Text("Front Tire (PSI)") },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = RacingAmberPrimary,
                            unfocusedBorderColor = TitaniumBorder,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        ),
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = rearPsi,
                        onValueChange = { rearPsi = it },
                        label = { Text("Rear Tire (PSI)") },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = RacingAmberPrimary,
                            unfocusedBorderColor = TitaniumBorder,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        ),
                        modifier = Modifier.weight(1f)
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = chainSlack,
                        onValueChange = { chainSlack = it },
                        label = { Text("Chain Slack (mm)") },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = RacingAmberPrimary,
                            unfocusedBorderColor = TitaniumBorder,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        ),
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = sparkPlug,
                        onValueChange = { sparkPlug = it },
                        label = { Text("Spark Plug Model") },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = RacingAmberPrimary,
                            unfocusedBorderColor = TitaniumBorder,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        ),
                        modifier = Modifier.weight(1f)
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = valveIntake,
                        onValueChange = { valveIntake = it },
                        label = { Text("Intake Valve (mm)") },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = RacingAmberPrimary,
                            unfocusedBorderColor = TitaniumBorder,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        ),
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = valveExhaust,
                        onValueChange = { valveExhaust = it },
                        label = { Text("Exhaust Valve (mm)") },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = RacingAmberPrimary,
                            unfocusedBorderColor = TitaniumBorder,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        ),
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSave(
                        initialSpecs.copy(
                            oilCapacityLiters = oilCap.toSafeDouble(3.2),
                            oilViscosityAndSpec = oilVisc.trim(),
                            coolantCapacityLiters = coolantCap.toSafeDouble(1.8),
                            brakeFluidType = brakeFluid.trim(),
                            frontTirePressurePsi = frontPsi.toSafeDouble(36.0),
                            rearTirePressurePsi = rearPsi.toSafeDouble(42.0),
                            chainSlackMm = chainSlack.trim(),
                            sparkPlugModel = sparkPlug.trim(),
                            valveClearanceIntakeMm = valveIntake.trim(),
                            valveClearanceExhaustMm = valveExhaust.trim()
                        )
                    )
                },
                colors = ButtonDefaults.buttonColors(containerColor = RacingAmberPrimary)
            ) {
                Text("Save Specs", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = Color.Gray) }
        }
    )
}

@Composable
private fun QuickAddTorqueDialog(
    onDismiss: () -> Unit,
    onSave: (String, Double) -> Unit
) {
    var boltName by remember { mutableStateOf("") }
    var torqueNmText by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = TftSurfaceDark,
        title = { Text("Quick Add Torque Spec", color = Color.White, fontWeight = FontWeight.Bold) },
        text = {
            Column {
                OutlinedTextField(
                    value = boltName,
                    onValueChange = { boltName = it },
                    label = { Text("Bolt / Fastener Name (e.g. Front Axle Pinch)") },
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
                    value = torqueNmText,
                    onValueChange = { torqueNmText = it },
                    label = { Text("Torque in Nm (e.g. 23)") },
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
                    val nm = torqueNmText.toSafeDouble(0.0)
                    if (boltName.isNotBlank() && nm > 0) {
                        onSave(boltName.trim(), nm)
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = RacingAmberPrimary)
            ) {
                Text("Add Torque", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = Color.Gray) }
        }
    )
}

@Composable
private fun AddNewBikeDialog(
    onDismiss: () -> Unit,
    onSave: (name: String, make: String, model: String, year: Int, cc: Int, odo: Double, price: Double, type: String) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var make by remember { mutableStateOf("") }
    var model by remember { mutableStateOf("") }
    var yearText by remember { mutableStateOf("2024") }
    var ccText by remember { mutableStateOf("890") }
    var odoText by remember { mutableStateOf("0") }
    var priceText by remember { mutableStateOf("0") }
    var type by remember { mutableStateOf("Naked") }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = TftSurfaceDark,
        title = { Text("Add New Motorcycle", color = Color.White, fontWeight = FontWeight.Bold) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Bike Name (Optional)") },
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
                        value = make,
                        onValueChange = { make = it },
                        label = { Text("Make (e.g. Honda)") },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = RacingAmberPrimary,
                            unfocusedBorderColor = TitaniumBorder,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        ),
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = model,
                        onValueChange = { model = it },
                        label = { Text("Model (e.g. CBR650R)") },
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
                        value = yearText,
                        onValueChange = { yearText = it },
                        label = { Text("Year") },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = RacingAmberPrimary,
                            unfocusedBorderColor = TitaniumBorder,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        ),
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = ccText,
                        onValueChange = { ccText = it },
                        label = { Text("CC") },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = RacingAmberPrimary,
                            unfocusedBorderColor = TitaniumBorder,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        ),
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = type,
                        onValueChange = { type = it },
                        label = { Text("Category") },
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
                        value = priceText,
                        onValueChange = { priceText = it },
                        label = { Text("Price (€)") },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = RacingAmberPrimary,
                            unfocusedBorderColor = TitaniumBorder,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        ),
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSave(
                        name,
                        make.ifBlank { "Yamaha" },
                        model.ifBlank { "MT-09" },
                        yearText.toSafeInt(2024),
                        ccText.toSafeInt(890),
                        odoText.toSafeDouble(0.0),
                        priceText.toSafeDouble(0.0),
                        type.ifBlank { "Naked" }
                    )
                },
                colors = ButtonDefaults.buttonColors(containerColor = RacingAmberPrimary)
            ) {
                Text("Add Motorcycle", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = Color.Gray) }
        }
    )
}

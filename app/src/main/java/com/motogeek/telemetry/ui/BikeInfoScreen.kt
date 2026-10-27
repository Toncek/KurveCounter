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
    var torqueSearchQuery by remember { mutableStateOf("") }

    // Parse technical specs & bolt torques
    val json = remember { Json { ignoreUnknownKeys = true; isLenient = true } }
    val specs = remember(activeBike?.technicalSpecsJson) {
        try {
            if (!activeBike?.technicalSpecsJson.isNullOrBlank() && activeBike?.technicalSpecsJson != "{}") {
                json.decodeFromString<TechnicalSpecsDto>(activeBike!!.technicalSpecsJson)
            } else TechnicalSpecsDto()
        } catch (_: Exception) {
            TechnicalSpecsDto()
        }
    }

    // Consolidated torque map
    val allTorques = remember(specs) {
        val map = mutableMapOf<String, Double>()
        specs.rearAxleNutTorqueNm?.let { map["Rear Axle Nut"] = it }
        specs.oilDrainPlugTorqueNm?.let { map["Oil Drain Plug"] = it }
        specs.boltTorquesNm?.forEach { (k, v) -> map[k] = v }
        map
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
                        TextButton(onClick = { showAddTorqueDialog = true }) {
                            Text("+ QUICK ADD TORQUE", color = RacingAmberPrimary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    // Key Fluid & Tire Pressure Chips
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        SpecPill("Oil Cap", "${specs.oilCapacityLiters ?: 3.2} L", TelemetryIceCyan, Modifier.weight(1f))
                        SpecPill("Front Tire", "${specs.frontTirePressurePsi ?: 36} PSI", ApexEmeraldGreen, Modifier.weight(1f))
                        SpecPill("Rear Tire", "${specs.rearTirePressurePsi ?: 42} PSI", ApexEmeraldGreen, Modifier.weight(1f))
                        SpecPill("Chain Slack", specs.chainSlackMm ?: "25-35 mm", WarningGold, Modifier.weight(1f))
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
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    text = "${torqueNm.toInt()} Nm",
                                    color = RacingAmberPrimary,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                            HorizontalDivider(color = TitaniumBorder.copy(alpha = 0.4f), thickness = 0.5.dp)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(30.dp))
        }
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

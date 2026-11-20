package com.motogeek.telemetry.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.motogeek.telemetry.data.local.MotorcycleEntity
import com.motogeek.telemetry.domain.maintenance.ImportPreviewPlan
import com.motogeek.telemetry.domain.maintenance.ImportSourceFormat
import com.motogeek.telemetry.domain.maintenance.MotorcycleBackupDto
import com.motogeek.telemetry.domain.maintenance.MotorcycleTemplateCatalog
import com.motogeek.telemetry.domain.maintenance.RestoreMode
import com.motogeek.telemetry.domain.maintenance.TechnicalSpecsDto
import com.motogeek.telemetry.domain.toSafeDouble
import com.motogeek.telemetry.ui.components.ActiveBikeDropdownCard
import com.motogeek.telemetry.ui.components.GuidedImportDialog
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
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val appLang = LocalAppLang.current

    var pendingGuidedPlan by remember { mutableStateOf<ImportPreviewPlan?>(null) }
    var isImportingGuidedPlan by remember { mutableStateOf(false) }

    val jsonFileImportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            scope.launch {
                val res = viewModel.importExportManager.analyzeJsonFile(uri)
                if (res.isSuccess) {
                    pendingGuidedPlan = res.getOrNull()
                } else {
                    snackbarHostState.showSnackbar(
                        if (appLang == AppLang.SK) "Chyba analýzy JSON súboru: ${res.exceptionOrNull()?.message}"
                        else "Failed to analyze JSON file: ${res.exceptionOrNull()?.message}"
                    )
                }
            }
        }
    }

    var name by remember(activeBike) { mutableStateOf(activeBike?.name ?: "") }
    var type by remember(activeBike) { mutableStateOf(activeBike?.type ?: "") }
    var make by remember(activeBike) { mutableStateOf(activeBike?.make ?: "") }
    var model by remember(activeBike) { mutableStateOf(activeBike?.model ?: "") }
    var yearText by remember(activeBike) { mutableStateOf(activeBike?.year?.takeIf { it > 0 }?.toString() ?: "") }
    var ccText by remember(activeBike) { mutableStateOf(activeBike?.engineDisplacementCc?.takeIf { it > 0 }?.toString() ?: "") }
    var startDateIso by remember(activeBike) { mutableStateOf(activeBike?.startDateIso ?: "") }
    var baseOdoText by remember(activeBike) {
        mutableStateOf(
            activeBike?.baseOdometerKm?.let { odo ->
                if (odo == odo.toLong().toDouble()) odo.toLong().toString() else odo.toString()
            } ?: ""
        )
    }
    var buyingPriceText by remember(activeBike) {
        mutableStateOf(
            activeBike?.buyingPrice?.takeIf { it > 0.0 }?.let { price ->
                if (price == price.toLong().toDouble()) price.toLong().toString() else price.toString()
            } ?: ""
        )
    }
    var currencySymbol by remember(activeBike) { mutableStateOf(activeBike?.currencySymbol?.ifBlank { "€" } ?: "€") }

    val hasUnfinishedDraft by viewModel.hasUnfinishedTutorialDraft.collectAsState()

    var showPasteDialog by remember { mutableStateOf(false) }
    var pasteJsonText by remember { mutableStateOf("") }
    var pendingTemplate by remember { mutableStateOf<Pair<String, MotorcycleBackupDto>?>(null) }

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
                    TextButton(onClick = { viewModel.startNewBikeTutorial() }) {
                        Text("+ NEW BIKE", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                    Button(
                        onClick = {
                            activeBike?.let { bike ->
                                scope.launch {
                                    val updated = bike.copy(
                                        name = name.trim().ifBlank { "$make $model".trim() },
                                        type = type.trim(),
                                        make = make.trim(),
                                        model = model.trim(),
                                        year = yearText.trim().toIntOrNull()?.takeIf { it > 0 } ?: 0,
                                        engineDisplacementCc = ccText.trim().toIntOrNull()?.takeIf { it > 0 } ?: 0,
                                        startDateIso = startDateIso.trim(),
                                        baseOdometerKm = baseOdoText.toSafeDouble(0.0),
                                        buyingPrice = buyingPriceText.toSafeDouble(0.0),
                                        currencySymbol = currencySymbol.trim().ifBlank { "€" }
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
                onAddNewBike = { viewModel.startNewBikeTutorial() },
                onSyncOdometer = { newKm -> viewModel.updateActiveBikeOdometer(newKm) },
                showOdometer = true
            )

            if (hasUnfinishedDraft) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    colors = CardDefaults.cardColors(containerColor = TftSurfaceElevated),
                    border = BorderStroke(1.dp, RacingAmberPrimary)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = tr("Resume unfinished bike setup", "Dokončiť rozpracované nastavenie"),
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                            Text(
                                text = tr("Your draft is autosaved.", "Váš návrh je automaticky uložený."),
                                color = Color.Gray,
                                fontSize = 11.sp
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            TextButton(
                                onClick = { viewModel.discardTutorial(deletePersistedBike = true) },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Text(tr("Discard", "Zahodiť"), color = RedlineCrimson, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                            Button(
                                onClick = { viewModel.resumeNewBikeTutorial() },
                                colors = ButtonDefaults.buttonColors(containerColor = RacingAmberPrimary),
                                shape = RoundedCornerShape(6.dp),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                            ) {
                                Text(tr("Resume", "Pokračovať"), color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

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

                    val frontPressureStr = specs.frontTirePressurePsi?.let { frontPsi ->
                        if (useBarUnits) String.format("%.2f BAR", frontPsi / 14.5038) else "${frontPsi.toInt()} PSI"
                    } ?: "—"
                    val rearPressureStr = specs.rearTirePressurePsi?.let { rearPsi ->
                        if (useBarUnits) String.format("%.2f BAR", rearPsi / 14.5038) else "${rearPsi.toInt()} PSI"
                    } ?: "—"

                    // Key Fluid & Tire Pressure Chips
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        SpecPill("Oil Cap", specs.oilCapacityLiters?.let { "$it L" } ?: "—", TelemetryIceCyan, Modifier.weight(1f))
                        SpecPill("Front Tire", frontPressureStr, ApexEmeraldGreen, Modifier.weight(1f))
                        SpecPill("Rear Tire", rearPressureStr, ApexEmeraldGreen, Modifier.weight(1f))
                        SpecPill("Chain Slack", specs.chainSlackMm?.trim()?.ifBlank { null } ?: "—", WarningGold, Modifier.weight(1f))
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        SpecPill("Oil Grade", specs.oilViscosityAndSpec.trim().ifBlank { "—" }.take(10), TelemetryIceCyan, Modifier.weight(1f))
                        SpecPill("Coolant", specs.coolantCapacityLiters?.let { "$it L" } ?: "—", TelemetryIceCyan, Modifier.weight(1f))
                        SpecPill("Brake Fluid", specs.brakeFluidType.trim().ifBlank { "—" }, WarningGold, Modifier.weight(1f))
                        SpecPill("Spark Plug", specs.sparkPlugModel.trim().ifBlank { "—" }.take(10), RacingAmberPrimary, Modifier.weight(1f))
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

            // AI Service Manual Prompt Generator
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
                        Text(
                            text = "AI SERVICE MANUAL EXTRACTOR",
                            color = Color(0xFF6B7A99),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            letterSpacing = 1.sp
                        )
                        Icon(Icons.Default.Psychology, contentDescription = null, tint = RacingAmberPrimary)
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Feed your motorcycle's PDF service manual or workshop book into ChatGPT, Claude, or Gemini using our Master Mechanic prompt" + (activeBike?.let { " tailored for ${it.year} ${it.make} ${it.model}" } ?: "") + ". Copy the resulting JSON and paste it into KurveCounter to auto-populate all service schedules and bolt torques!",
                        color = Color.LightGray,
                        fontSize = 12.sp,
                        lineHeight = 17.sp
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    Button(
                        onClick = {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            val prompt = viewModel.importExportManager.buildAiServiceManualPrompt(activeBike, appLang.code)
                            clipboard.setPrimaryClip(ClipData.newPlainText("AI Mechanic Prompt", prompt))
                            scope.launch {
                                snackbarHostState.showSnackbar(
                                    if (appLang == AppLang.SK) "AI prompt skopírovaný do schránky!"
                                    else "AI Master Mechanic Prompt copied to clipboard!"
                                )
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = TftSurfaceElevated),
                        border = BorderStroke(1.dp, RacingAmberPrimary),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.ContentCopy, contentDescription = null, tint = RacingAmberPrimary, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Copy AI Master Mechanic Prompt", color = RacingAmberPrimary, fontWeight = FontWeight.Bold)
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Paste JSON from Clipboard Button
                    OutlinedButton(
                        onClick = {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            val clipItem = clipboard.primaryClip?.getItemAt(0)?.text?.toString()
                            if (!clipItem.isNullOrBlank()) {
                                pasteJsonText = clipItem
                            }
                            showPasteDialog = true
                        },
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, TitaniumBorder),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.ContentPaste, contentDescription = null, tint = ApexEmeraldGreen, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Paste AI JSON / Clipboard Import", color = Color.White, fontWeight = FontWeight.Bold)
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Import JSON File (MyBikes / Backup / AI)
                    OutlinedButton(
                        onClick = { jsonFileImportLauncher.launch(arrayOf("application/json", "text/*", "*/*")) },
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, TitaniumBorder),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.FileOpen, contentDescription = null, tint = TelemetryIceCyan, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = tr("Import JSON File (MyBikes / Backup / AI)", "Importovať JSON súbor (MyBikes / Záloha / AI)"),
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            // Built-In Factory Motorcycle Templates
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp),
                colors = CardDefaults.cardColors(containerColor = TftSurfaceDark),
                border = BorderStroke(1.dp, TitaniumBorder)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "BUILT-IN MOTORCYCLE TEMPLATES",
                        color = Color(0xFF6B7A99),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        letterSpacing = 1.sp
                    )
                    Spacer(modifier = Modifier.height(10.dp))

                    TemplateTile("Universal Motorcycle", "Standard 5000 km intervals, 4-stroke", onApply = {
                        pendingTemplate = "Universal Motorcycle" to MotorcycleTemplateCatalog.universal
                    })
                    TemplateTile("Yamaha MT-09 / Tracer 9", "CP3 890cc • 10,000 km service • Factory torques", onApply = {
                        pendingTemplate = "Yamaha MT-09" to MotorcycleTemplateCatalog.mt09
                    })
                    TemplateTile("Honda CB650R / CBR650R", "Inline-4 649cc • 12,000 km service intervals", onApply = {
                        pendingTemplate = "Honda CB650R" to MotorcycleTemplateCatalog.cb650r
                    })
                    TemplateTile("BMW R1250GS Boxer", "1254cc ShiftCam • Cardan shaft & valve checks", onApply = {
                        pendingTemplate = "BMW R1250GS" to MotorcycleTemplateCatalog.r1250gs
                    })
                    TemplateTile("KTM 890 Duke / Adventure", "889cc Parallel-Twin • 15,000 km intervals & torques", onApply = {
                        pendingTemplate = "KTM 890 Duke" to MotorcycleTemplateCatalog.ktm890
                    })
                    TemplateTile("Kawasaki Z900", "948cc Inline-4 • 12,000 km intervals & torques", onApply = {
                        pendingTemplate = "Kawasaki Z900" to MotorcycleTemplateCatalog.z900
                    })
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

    pendingTemplate?.let { (label, templateDto) ->
        AlertDialog(
            onDismissRequest = { pendingTemplate = null },
            containerColor = TftSurfaceDark,
            title = { Text("Apply $label Template?", color = Color.White, fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    text = if (activeBike != null) {
                        "Choose whether to apply $label factory specs & schedule to your active motorcycle (${activeBike?.displayName}) or add it as a new motorcycle in your garage."
                    } else {
                        "Add $label with factory specs, torques, and maintenance schedule to your garage?"
                    },
                    color = Color.LightGray,
                    fontSize = 13.sp
                )
            },
            confirmButton = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (activeBike != null) {
                        Button(
                            onClick = {
                                viewModel.applyTemplate(templateDto, applyToActiveBike = true)
                                pendingTemplate = null
                                scope.launch { snackbarHostState.showSnackbar("Applied $label specs & schedule to ${activeBike?.displayName}!") }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = RacingAmberPrimary),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Update Active Bike (${activeBike?.displayName})", fontWeight = FontWeight.Bold)
                        }
                    }
                    OutlinedButton(
                        onClick = {
                            viewModel.applyTemplate(templateDto, applyToActiveBike = false)
                            pendingTemplate = null
                            scope.launch { snackbarHostState.showSnackbar("Added $label as a new motorcycle!") }
                        },
                        border = BorderStroke(1.dp, TelemetryIceCyan),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Add as New Motorcycle", color = TelemetryIceCyan, fontWeight = FontWeight.Bold)
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingTemplate = null }) {
                    Text("Cancel", color = Color.Gray)
                }
            }
        )
    }

    if (showPasteDialog) {
        AlertDialog(
            onDismissRequest = { showPasteDialog = false },
            containerColor = TftSurfaceDark,
            title = { Text("Paste AI JSON / Schedule", color = Color.White, fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text(
                        text = "Paste the JSON output produced by the LLM from your service manual:",
                        color = Color.LightGray,
                        fontSize = 12.sp
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedTextField(
                        value = pasteJsonText,
                        onValueChange = { pasteJsonText = it },
                        placeholder = { Text("{\n  \"technicalSpecs\": {...},\n  \"maintenanceSchedule\": [...]\n}", color = Color.DarkGray) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = RacingAmberPrimary,
                            unfocusedBorderColor = TitaniumBorder,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp)
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        scope.launch {
                            val planResult = viewModel.importExportManager.analyzeJsonString(pasteJsonText)
                            val plan = planResult.getOrNull()
                            if (plan != null && (plan.format == ImportSourceFormat.MYBIKES_EXPORT || plan.format == ImportSourceFormat.KURVE_GARAGE_BACKUP)) {
                                showPasteDialog = false
                                pendingGuidedPlan = plan
                            } else {
                                val res = viewModel.importExportManager.importFromJsonString(
                                    pasteJsonText,
                                    activeBike?.id,
                                    RestoreMode.MERGE
                                )
                                if (res.isSuccess) {
                                    val s = res.getOrNull()
                                    snackbarHostState.showSnackbar(
                                        if (appLang == AppLang.SK)
                                            "Úspešne naimportované! ${s?.motorcyclesImported ?: 0} motocyklov, ${s?.tasksImported ?: 0} úloh."
                                        else
                                            "Successfully imported! ${s?.motorcyclesImported ?: 0} bikes, ${s?.tasksImported ?: 0} tasks."
                                    )
                                    showPasteDialog = false
                                } else {
                                    snackbarHostState.showSnackbar(
                                        if (appLang == AppLang.SK)
                                            "Spracovanie zlyhalo: ${res.exceptionOrNull()?.message}"
                                        else
                                            "Parsing Failed: ${res.exceptionOrNull()?.message}"
                                    )
                                }
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = RacingAmberPrimary)
                ) {
                    Text(tr("Import JSON", "Importovať JSON"), fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showPasteDialog = false }) { Text(tr("Cancel", "Zrušiť"), color = Color.Gray) }
            }
        )
    }

    if (pendingGuidedPlan != null) {
        GuidedImportDialog(
            plan = pendingGuidedPlan!!,
            existingBikes = allBikes,
            isImporting = isImportingGuidedPlan,
            onDismiss = { pendingGuidedPlan = null },
            onConfirm = { selection ->
                scope.launch {
                    isImportingGuidedPlan = true
                    val result = viewModel.importExportManager.executeGuidedImport(
                        plan = pendingGuidedPlan!!,
                        selection = selection,
                        langCode = appLang.code
                    )
                    isImportingGuidedPlan = false
                    pendingGuidedPlan = null
                    if (result.isSuccess) {
                        val s = result.getOrNull()
                        snackbarHostState.showSnackbar(
                            if (appLang == AppLang.SK)
                                "Import dokončený: ${s?.motorcyclesImported ?: 0} motocyklov, ${s?.tasksImported ?: 0} úloh, ${s?.logsImported ?: 0} servisov, ${s?.fuelLogsImported ?: 0} tankovaní, ${s?.ridesImported ?: 0} jázd."
                            else
                                "Import Successful! ${s?.motorcyclesImported ?: 0} bikes, ${s?.tasksImported ?: 0} tasks, ${s?.logsImported ?: 0} service logs, ${s?.fuelLogsImported ?: 0} fuel logs, ${s?.ridesImported ?: 0} rides."
                        )
                    } else {
                        snackbarHostState.showSnackbar(
                            if (appLang == AppLang.SK)
                                "Chyba importu: ${result.exceptionOrNull()?.message}"
                            else
                                "Import Error: ${result.exceptionOrNull()?.message}"
                        )
                    }
                }
            }
        )
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
    var oilCap by remember { mutableStateOf(initialSpecs.oilCapacityLiters?.toString() ?: "") }
    var oilVisc by remember { mutableStateOf(initialSpecs.oilViscosityAndSpec) }
    var coolantCap by remember { mutableStateOf(initialSpecs.coolantCapacityLiters?.toString() ?: "") }
    var brakeFluid by remember { mutableStateOf(initialSpecs.brakeFluidType) }
    var frontPsi by remember { mutableStateOf(initialSpecs.frontTirePressurePsi?.toString() ?: "") }
    var rearPsi by remember { mutableStateOf(initialSpecs.rearTirePressurePsi?.toString() ?: "") }
    var chainSlack by remember { mutableStateOf(initialSpecs.chainSlackMm ?: "") }
    var sparkPlug by remember { mutableStateOf(initialSpecs.sparkPlugModel) }
    var valveIntake by remember { mutableStateOf(initialSpecs.valveClearanceIntakeMm) }
    var valveExhaust by remember { mutableStateOf(initialSpecs.valveClearanceExhaustMm) }

    fun parseOptionalPositiveDouble(raw: String): Double? {
        val parsed = raw.trim().replace(',', '.').toDoubleOrNull() ?: return null
        return parsed.takeIf { it.isFinite() && it > 0.0 }
    }

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
                            oilCapacityLiters = parseOptionalPositiveDouble(oilCap),
                            oilViscosityAndSpec = oilVisc.trim(),
                            coolantCapacityLiters = parseOptionalPositiveDouble(coolantCap),
                            brakeFluidType = brakeFluid.trim(),
                            frontTirePressurePsi = parseOptionalPositiveDouble(frontPsi),
                            rearTirePressurePsi = parseOptionalPositiveDouble(rearPsi),
                            chainSlackMm = chainSlack.trim().ifBlank { null },
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
private fun TemplateTile(title: String, subtitle: String, onApply: () -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        shape = RoundedCornerShape(8.dp),
        color = TftSurfaceElevated,
        border = BorderStroke(1.dp, TitaniumBorder)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Text(subtitle, color = Color.Gray, fontSize = 11.sp)
            }
            Button(
                onClick = onApply,
                colors = ButtonDefaults.buttonColors(containerColor = RacingAmberPrimary.copy(alpha = 0.2f)),
                border = BorderStroke(1.dp, RacingAmberPrimary),
                shape = RoundedCornerShape(6.dp),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                modifier = Modifier.height(32.dp)
            ) {
                Text("Apply", color = RacingAmberPrimary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

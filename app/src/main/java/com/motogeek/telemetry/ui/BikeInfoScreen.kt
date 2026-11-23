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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
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
import com.motogeek.telemetry.domain.maintenance.localizeTorqueName
import com.motogeek.telemetry.domain.toSafeDouble
import com.motogeek.telemetry.ui.components.ActiveBikeDropdownCard
import com.motogeek.telemetry.ui.components.GuidedImportDialog
import com.motogeek.telemetry.ui.components.SegmentedDigitalText
import com.motogeek.telemetry.ui.theme.*
import java.util.Locale
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
    var showCalibrateOdoDialog by remember { mutableStateOf(false) }
    var calibrateOdoInput by remember(activeBike) {
        mutableStateOf(
            activeBike?.totalOdometerKm?.let { odo ->
                if (odo == odo.toLong().toDouble()) odo.toLong().toString() else odo.toString()
            } ?: ""
        )
    }
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
                        text = tr("Bike Profile & Torques", "Profil motorky a momenty"),
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 17.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
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
                    TextButton(
                        onClick = { viewModel.toggleAppLanguage() },
                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = if (appLang == AppLang.EN) "EN ▸ SK" else "SK ▸ EN",
                            color = TelemetryIceCyan,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp
                        )
                    }
                    TextButton(
                        onClick = { viewModel.startNewBikeTutorial() },
                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = tr("+ NEW BIKE", "+ NOVÁ"),
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp
                        )
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
                                    snackbarHostState.showSnackbar(
                                        appLang.pick(
                                            "Motorcycle profile saved successfully!",
                                            "Profil motorky bol úspešne uložený!"
                                        )
                                    )
                                }
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = RacingAmberPrimary),
                        shape = RoundedCornerShape(6.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        modifier = Modifier.padding(end = 8.dp)
                    ) {
                        Text(tr("SAVE", "ULOŽIŤ"), fontWeight = FontWeight.Bold, fontSize = 12.sp)
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
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = tr("CURRENT TOTAL ODOMETER", "AKTUÁLNE CELKOVÉ KILOMETRE"),
                            color = Color(0xFF6B7A99),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                        SegmentedDigitalText(
                            text = String.format("%,d", activeBike?.totalOdometerKm?.toInt() ?: 0),
                            unit = "KM",
                            color = TelemetryIceCyan,
                            fontSize = 22.sp
                        )
                        Text(
                            text = tr(
                                "Base: ${activeBike?.baseOdometerKm?.toInt() ?: 0} km • Tracked: ${activeBike?.accumulatedTrackedKm?.toInt() ?: 0} km • Offset: ${activeBike?.manualAdjustmentOffsetKm?.toInt() ?: 0} km",
                                "Základ: ${activeBike?.baseOdometerKm?.toInt() ?: 0} km • Zaznamenané: ${activeBike?.accumulatedTrackedKm?.toInt() ?: 0} km • Korekcia: ${activeBike?.manualAdjustmentOffsetKm?.toInt() ?: 0} km"
                            ),
                            color = Color.Gray,
                            fontSize = 10.sp
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            activeBike?.let { bike ->
                                calibrateOdoInput = if (bike.totalOdometerKm == bike.totalOdometerKm.toLong().toDouble()) {
                                    bike.totalOdometerKm.toLong().toString()
                                } else {
                                    bike.totalOdometerKm.toString()
                                }
                                showCalibrateOdoDialog = true
                            }
                        },
                        enabled = activeBike != null,
                        colors = ButtonDefaults.buttonColors(containerColor = TftSurfaceElevated),
                        shape = RoundedCornerShape(6.dp),
                        border = BorderStroke(1.dp, TitaniumBorder)
                    ) {
                        Text(tr("CALIBRATE", "KALIBROVAŤ"), color = RacingAmberPrimary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
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
                        text = tr("MOTORCYCLE SPECIFICATIONS", "ŠPECIFIKÁCIE MOTOCYKLA"),
                        color = Color(0xFF6B7A99),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        letterSpacing = 1.sp
                    )

                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text(tr("Display Name / Nickname", "Zobrazovaný názov / Prezývka")) },
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
                            label = { Text(tr("Make (e.g., Yamaha)", "Značka (napr. Yamaha)")) },
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
                            label = { Text(tr("Model (e.g., MT-09)", "Model (napr. MT-09)")) },
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
                            label = { Text(tr("Year", "Rok")) },
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
                            label = { Text(tr("Displacement (cc)", "Objem (cm³)")) },
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
                            label = { Text(tr("Category", "Kategória")) },
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
                            label = { Text(tr("Starting Odometer (km)", "Počiatočný stav km")) },
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
                            label = { Text(tr("Purchase Price", "Kúpna cena")) },
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
                            label = { Text(tr("Currency", "Mena")) },
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
                            text = tr("FACTORY SPECS & BOLT TORQUES", "TOVÁRENSKÉ ÚDAJE A MOMENTY"),
                            color = Color(0xFF6B7A99),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            letterSpacing = 1.sp,
                            modifier = Modifier.weight(1f)
                        )
                        Row {
                            TextButton(
                                onClick = { useBarUnits = !useBarUnits },
                                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = if (useBarUnits) tr("UNIT: BAR", "JEDN: BAR") else tr("UNIT: PSI", "JEDN: PSI"),
                                    color = TelemetryIceCyan,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            TextButton(
                                onClick = { showEditSpecsDialog = true },
                                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = tr("EDIT SPECS", "UPRAVIŤ"),
                                    color = ApexEmeraldGreen,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            TextButton(
                                onClick = { showAddTorqueDialog = true },
                                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = tr("+ TORQUE", "+ MOMENT"),
                                    color = RacingAmberPrimary,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    val frontPressureStr = specs.frontTirePressurePsi?.let { frontPsi ->
                        if (useBarUnits) String.format(Locale.US, "%.2f BAR", frontPsi / 14.5038) else "${frontPsi.toInt()} PSI"
                    } ?: "—"
                    val rearPressureStr = specs.rearTirePressurePsi?.let { rearPsi ->
                        if (useBarUnits) String.format(Locale.US, "%.2f BAR", rearPsi / 14.5038) else "${rearPsi.toInt()} PSI"
                    } ?: "—"

                    // Key Fluid & Tire Pressure Chips
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        SpecPill(tr("Oil Cap", "Objem oleja"), specs.oilCapacityLiters?.let { "$it L" } ?: "—", TelemetryIceCyan, Modifier.weight(1f))
                        SpecPill(tr("Front Tire", "Predná pneu"), frontPressureStr, ApexEmeraldGreen, Modifier.weight(1f))
                        SpecPill(tr("Rear Tire", "Zadná pneu"), rearPressureStr, ApexEmeraldGreen, Modifier.weight(1f))
                        SpecPill(tr("Chain Slack", "Vôľa reťaze"), specs.chainSlackMm?.trim()?.ifBlank { null } ?: "—", WarningGold, Modifier.weight(1f))
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        SpecPill(tr("Oil Grade", "Typ oleja"), specs.oilViscosityAndSpec.trim().ifBlank { "—" }.take(10), TelemetryIceCyan, Modifier.weight(1f))
                        SpecPill(tr("Coolant", "Chladiaca"), specs.coolantCapacityLiters?.let { "$it L" } ?: "—", TelemetryIceCyan, Modifier.weight(1f))
                        SpecPill(tr("Brake Fluid", "Brzdovka"), specs.brakeFluidType.trim().ifBlank { "—" }, WarningGold, Modifier.weight(1f))
                        SpecPill(tr("Spark Plug", "Sviečka"), specs.sparkPlugModel.trim().ifBlank { "—" }.take(10), RacingAmberPrimary, Modifier.weight(1f))
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = torqueSearchQuery,
                        onValueChange = { torqueSearchQuery = it },
                        placeholder = {
                            Text(
                                tr("Search bolt or nut torque (Nm)...", "Hľadať moment skrutky / matice (Nm)..."),
                                color = Color.Gray,
                                fontSize = 12.sp
                            )
                        },
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
                        it.key.contains(torqueSearchQuery, ignoreCase = true) ||
                            localizeTorqueName(it.key, appLang.code).contains(torqueSearchQuery, ignoreCase = true)
                    }

                    if (filteredTorques.isEmpty()) {
                        Text(
                            text = tr(
                                "No bolt torques found. Tap '+ TORQUE' to add one.",
                                "Nenašli sa žiadne uťahovacie momenty. Ťuknite na '+ MOMENT' pre pridanie."
                            ),
                            color = Color.Gray,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(vertical = 12.dp)
                        )
                    } else {
                        filteredTorques.forEach { (boltName, torqueNm) ->
                            val isCustomBolt = specs.boltTorquesNm?.containsKey(boltName) == true
                            val localizedBoltName = localizeTorqueName(boltName, appLang.code)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 5.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = localizedBoltName,
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
                                                    snackbarHostState.showSnackbar(
                                                        appLang.pick(
                                                            "Removed custom torque: $localizedBoltName",
                                                            "Odstránený vlastný moment: $localizedBoltName"
                                                        )
                                                    )
                                                }
                                            },
                                            modifier = Modifier.size(28.dp)
                                        ) {
                                            Icon(
                                                Icons.Default.Close,
                                                contentDescription = tr("Delete torque", "Odstrániť moment"),
                                                tint = Color.Gray,
                                                modifier = Modifier.size(14.dp)
                                            )
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
                            text = tr("AI SERVICE MANUAL EXTRACTOR", "AI EXTRAKTOR SERVISNÉHO MANUÁLU"),
                            color = Color(0xFF6B7A99),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            letterSpacing = 1.sp
                        )
                        Icon(Icons.Default.Psychology, contentDescription = null, tint = RacingAmberPrimary)
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    val bikeTailoredEn = activeBike?.let { " tailored for ${it.year} ${it.make} ${it.model}" } ?: ""
                    val bikeTailoredSk = activeBike?.let { " prispôsobený pre ${it.year} ${it.make} ${it.model}" } ?: ""
                    Text(
                        text = tr(
                            "Feed your motorcycle's PDF service manual or workshop book into ChatGPT, Claude, or Gemini using our Master Mechanic prompt$bikeTailoredEn. Copy the resulting JSON and paste it into KurveCounter to auto-populate all service schedules and bolt torques!",
                            "Nahrajte PDF servisný manuál alebo dielenskú príručku do ChatGPT, Claude alebo Gemini s naším Master Mechanic promptom$bikeTailoredSk. Skopírujte výsledný JSON a vložte ho do KurveCounter na automatické vyplnenie servisných intervalov a momentov!"
                        ),
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
                        Text(
                            text = tr("Copy AI Master Mechanic Prompt", "Kopírovať AI Master Mechanic Prompt"),
                            color = RacingAmberPrimary,
                            fontWeight = FontWeight.Bold
                        )
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
                        Text(
                            text = tr("Paste AI JSON / Clipboard Import", "Vložiť AI JSON zo schránky"),
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        )
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
                        text = tr("BUILT-IN MOTORCYCLE TEMPLATES", "VSTAVANÉ ŠABLÓNY MOTOCYKLOV"),
                        color = Color(0xFF6B7A99),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        letterSpacing = 1.sp
                    )
                    Spacer(modifier = Modifier.height(10.dp))

                    val universalLabel = tr("Universal Motorcycle", "Univerzálny motocykel")
                    TemplateTile(
                        title = universalLabel,
                        subtitle = tr("Standard 5000 km intervals, 4-stroke", "Štandardné intervaly 5000 km, 4-takt"),
                        onApply = { pendingTemplate = universalLabel to MotorcycleTemplateCatalog.universal }
                    )
                    TemplateTile(
                        title = "Yamaha MT-09 / Tracer 9",
                        subtitle = tr("CP3 890cc • 10,000 km service • Factory torques", "CP3 890 cm³ • servis 10 000 km • Továrenské momenty"),
                        onApply = { pendingTemplate = "Yamaha MT-09" to MotorcycleTemplateCatalog.mt09 }
                    )
                    TemplateTile(
                        title = "Honda CB650R / CBR650R",
                        subtitle = tr("Inline-4 649cc • 12,000 km service intervals", "Radový 4-valec 649 cm³ • intervaly 12 000 km"),
                        onApply = { pendingTemplate = "Honda CB650R" to MotorcycleTemplateCatalog.cb650r }
                    )
                    TemplateTile(
                        title = "BMW R1250GS Boxer",
                        subtitle = tr("1254cc ShiftCam • Cardan shaft & valve checks", "1254 cm³ ShiftCam • Kardan a kontrola ventilov"),
                        onApply = { pendingTemplate = "BMW R1250GS" to MotorcycleTemplateCatalog.r1250gs }
                    )
                    TemplateTile(
                        title = "KTM 890 Duke / Adventure",
                        subtitle = tr("889cc Parallel-Twin • 15,000 km intervals & torques", "889 cm³ dvojvalec • intervaly 15 000 km a momenty"),
                        onApply = { pendingTemplate = "KTM 890 Duke" to MotorcycleTemplateCatalog.ktm890 }
                    )
                    TemplateTile(
                        title = "Kawasaki Z900",
                        subtitle = tr("948cc Inline-4 • 12,000 km intervals & torques", "948 cm³ radový 4-valec • intervaly 12 000 km a momenty"),
                        onApply = { pendingTemplate = "Kawasaki Z900" to MotorcycleTemplateCatalog.z900 }
                    )
                }
            }

            if (activeBike != null) {
                OutlinedButton(
                    onClick = { showDeleteBikeConfirm = true },
                    border = BorderStroke(1.dp, RedlineCrimson),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Delete, contentDescription = null, tint = RedlineCrimson, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = tr("Delete Motorcycle (${activeBike?.displayName})", "Vymazať motorku (${activeBike?.displayName})"),
                        color = RedlineCrimson,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(30.dp))
        }
    }

    if (showCalibrateOdoDialog && activeBike != null) {
        val parsedKm = calibrateOdoInput.trim().replace(',', '.').toDoubleOrNull()?.takeIf { it.isFinite() && it >= 0.0 }
        AlertDialog(
            onDismissRequest = { showCalibrateOdoDialog = false },
            containerColor = TftSurfaceDark,
            title = {
                Text(
                    text = tr("Calibrate Odometer", "Kalibrovať počítadlo km"),
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column {
                    Text(
                        text = tr(
                            "Enter the current dashboard odometer reading for ${activeBike?.displayName}:",
                            "Zadajte aktuálny stav počítadla km z prístrojovej dosky pre ${activeBike?.displayName}:"
                        ),
                        color = Color.LightGray,
                        fontSize = 13.sp
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedTextField(
                        value = calibrateOdoInput,
                        onValueChange = { calibrateOdoInput = it },
                        label = { Text(tr("Dashboard Odometer (km)", "Stav počítadla (km)")) },
                        isError = calibrateOdoInput.isNotBlank() && parsedKm == null,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = RacingAmberPrimary,
                            unfocusedBorderColor = TitaniumBorder,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        ),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (parsedKm != null) {
                            viewModel.updateActiveBikeOdometer(parsedKm)
                            showCalibrateOdoDialog = false
                            scope.launch {
                                snackbarHostState.showSnackbar(
                                    appLang.pick(
                                        "Odometer calibrated to ${parsedKm.toInt()} km",
                                        "Počítadlo nakalibrované na ${parsedKm.toInt()} km"
                                    )
                                )
                            }
                        }
                    },
                    enabled = parsedKm != null,
                    colors = ButtonDefaults.buttonColors(containerColor = RacingAmberPrimary)
                ) {
                    Text(tr("Calibrate", "Kalibrovať"), fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showCalibrateOdoDialog = false }) {
                    Text(tr("Cancel", "Zrušiť"), color = Color.Gray)
                }
            }
        )
    }

    pendingTemplate?.let { (label, templateDto) ->
        AlertDialog(
            onDismissRequest = { pendingTemplate = null },
            containerColor = TftSurfaceDark,
            title = { Text(tr("Apply $label Template?", "Použiť šablónu $label?"), color = Color.White, fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    text = if (activeBike != null) {
                        tr(
                            "Choose whether to apply $label factory specs & schedule to your active motorcycle (${activeBike?.displayName}) or add it as a new motorcycle in your garage.",
                            "Vyberte, či chcete použiť továrenské údaje a servisný plán šablóny $label pre aktívnu motorku (${activeBike?.displayName}), alebo ju pridať ako novú motorku do garáže."
                        )
                    } else {
                        tr(
                            "Add $label with factory specs, torques, and maintenance schedule to your garage?",
                            "Pridať $label s továrenskými údajmi, momentmi a servisným plánom do garáže?"
                        )
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
                                scope.launch {
                                    snackbarHostState.showSnackbar(
                                        appLang.pick(
                                            "Applied $label specs & schedule to ${activeBike?.displayName}!",
                                            "Údaje a plán šablóny $label použité pre ${activeBike?.displayName}!"
                                        )
                                    )
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = RacingAmberPrimary),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(tr("Update Active Bike (${activeBike?.displayName})", "Aktualizovať aktívnu (${activeBike?.displayName})"), fontWeight = FontWeight.Bold)
                        }
                    }
                    OutlinedButton(
                        onClick = {
                            viewModel.applyTemplate(templateDto, applyToActiveBike = false)
                            pendingTemplate = null
                            scope.launch {
                                snackbarHostState.showSnackbar(
                                    appLang.pick(
                                        "Added $label as a new motorcycle!",
                                        "Motorka $label bola pridaná ako nová!"
                                    )
                                )
                            }
                        },
                        border = BorderStroke(1.dp, TelemetryIceCyan),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(tr("Add as New Motorcycle", "Pridať ako novú motorku"), color = TelemetryIceCyan, fontWeight = FontWeight.Bold)
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingTemplate = null }) {
                    Text(tr("Cancel", "Zrušiť"), color = Color.Gray)
                }
            }
        )
    }

    if (showPasteDialog) {
        AlertDialog(
            onDismissRequest = { showPasteDialog = false },
            containerColor = TftSurfaceDark,
            title = { Text(tr("Paste AI JSON / Schedule", "Vložiť AI JSON / Plán"), color = Color.White, fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text(
                        text = tr(
                            "Paste the JSON output produced by the LLM from your service manual:",
                            "Vložte JSON výstup vygenerovaný AI z vášho servisného manuálu:"
                        ),
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
            title = { Text(tr("Delete ${activeBike?.displayName}?", "Vymazať ${activeBike?.displayName}?"), color = Color.White, fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    tr(
                        "This will permanently delete ${activeBike?.displayName} and its associated logs. Your other motorcycles will remain untouched.",
                        "Týmto natrvalo vymažete motorku ${activeBike?.displayName} a jej záznamy. Ostatné motocykle zostanú nedotknuté."
                    ),
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
                    Text(tr("Delete Motorcycle", "Vymazať motorku"), fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteBikeConfirm = false }) {
                    Text(tr("Cancel", "Zrušiť"), color = Color.Gray)
                }
            }
        )
    }

    if (showEditSpecsDialog && activeBike != null) {
        EditTechnicalSpecsDialog(
            initialSpecs = specs,
            useBarUnits = useBarUnits,
            onDismiss = { showEditSpecsDialog = false },
            onSave = { updatedSpecs ->
                scope.launch {
                    val updatedBike = activeBike!!.copy(technicalSpecsJson = json.encodeToString(updatedSpecs))
                    viewModel.repository.updateMotorcycle(updatedBike)
                    showEditSpecsDialog = false
                    snackbarHostState.showSnackbar(
                        appLang.pick(
                            "Technical & fluid specifications updated!",
                            "Technické a prevádzkové špecifikácie aktualizované!"
                        )
                    )
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
                    snackbarHostState.showSnackbar(
                        appLang.pick(
                            "Added torque spec: ${localizeTorqueName(boltName, appLang.code)} = ${torqueNm.toInt()} Nm",
                            "Pridaný uťahovací moment: ${localizeTorqueName(boltName, appLang.code)} = ${torqueNm.toInt()} Nm"
                        )
                    )
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
    useBarUnits: Boolean,
    onDismiss: () -> Unit,
    onSave: (TechnicalSpecsDto) -> Unit
) {
    val psiPerBar = 14.5038
    var oilCap by remember(initialSpecs) { mutableStateOf(initialSpecs.oilCapacityLiters?.toString() ?: "") }
    var oilVisc by remember(initialSpecs) { mutableStateOf(initialSpecs.oilViscosityAndSpec) }
    var coolantCap by remember(initialSpecs) { mutableStateOf(initialSpecs.coolantCapacityLiters?.toString() ?: "") }
    var brakeFluid by remember(initialSpecs) { mutableStateOf(initialSpecs.brakeFluidType) }
    var frontPressure by remember(initialSpecs, useBarUnits) {
        mutableStateOf(
            initialSpecs.frontTirePressurePsi?.let {
                if (useBarUnits) String.format(Locale.US, "%.2f", it / psiPerBar) else it.toString()
            } ?: ""
        )
    }
    var rearPressure by remember(initialSpecs, useBarUnits) {
        mutableStateOf(
            initialSpecs.rearTirePressurePsi?.let {
                if (useBarUnits) String.format(Locale.US, "%.2f", it / psiPerBar) else it.toString()
            } ?: ""
        )
    }
    var chainSlack by remember(initialSpecs) { mutableStateOf(initialSpecs.chainSlackMm ?: "") }
    var sparkPlug by remember(initialSpecs) { mutableStateOf(initialSpecs.sparkPlugModel) }
    var valveIntake by remember(initialSpecs) { mutableStateOf(initialSpecs.valveClearanceIntakeMm) }
    var valveExhaust by remember(initialSpecs) { mutableStateOf(initialSpecs.valveClearanceExhaustMm) }

    fun parseOptionalPositiveDouble(raw: String): Double? {
        val parsed = raw.trim().replace(',', '.').toDoubleOrNull() ?: return null
        return parsed.takeIf { it.isFinite() && it > 0.0 }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = TftSurfaceDark,
        title = { Text(tr("Edit Fluid & Workshop Specs", "Upraviť náplne a dielenské údaje"), color = Color.White, fontWeight = FontWeight.Bold) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = oilCap,
                        onValueChange = { oilCap = it },
                        label = { Text(tr("Oil w/ Filter (L)", "Olej s filtrom (L)")) },
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
                        label = { Text(tr("Oil Viscosity", "Viskozita oleja")) },
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
                        label = { Text(tr("Coolant (L)", "Chladiaca (L)")) },
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
                        label = { Text(tr("Brake Fluid", "Brzdová kvapalina")) },
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
                        value = frontPressure,
                        onValueChange = { frontPressure = it },
                        label = {
                            Text(
                                if (useBarUnits) tr("Front Tire (BAR)", "Predná pneu (BAR)")
                                else tr("Front Tire (PSI)", "Predná pneu (PSI)")
                            )
                        },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = RacingAmberPrimary,
                            unfocusedBorderColor = TitaniumBorder,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        ),
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = rearPressure,
                        onValueChange = { rearPressure = it },
                        label = {
                            Text(
                                if (useBarUnits) tr("Rear Tire (BAR)", "Zadná pneu (BAR)")
                                else tr("Rear Tire (PSI)", "Zadná pneu (PSI)")
                            )
                        },
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
                        label = { Text(tr("Chain Slack (mm)", "Vôľa reťaze (mm)")) },
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
                        label = { Text(tr("Spark Plug Model", "Model sviečky")) },
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
                        label = { Text(tr("Intake Valve (mm)", "Sací ventil (mm)")) },
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
                        label = { Text(tr("Exhaust Valve (mm)", "Výfukový ventil (mm)")) },
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
                    val parsedFrontPsi = parseOptionalPositiveDouble(frontPressure)?.let {
                        if (useBarUnits) it * psiPerBar else it
                    }
                    val parsedRearPsi = parseOptionalPositiveDouble(rearPressure)?.let {
                        if (useBarUnits) it * psiPerBar else it
                    }
                    onSave(
                        initialSpecs.copy(
                            oilCapacityLiters = parseOptionalPositiveDouble(oilCap),
                            oilViscosityAndSpec = oilVisc.trim(),
                            coolantCapacityLiters = parseOptionalPositiveDouble(coolantCap),
                            brakeFluidType = brakeFluid.trim(),
                            frontTirePressurePsi = parsedFrontPsi,
                            rearTirePressurePsi = parsedRearPsi,
                            chainSlackMm = chainSlack.trim().ifBlank { null },
                            sparkPlugModel = sparkPlug.trim(),
                            valveClearanceIntakeMm = valveIntake.trim(),
                            valveClearanceExhaustMm = valveExhaust.trim()
                        )
                    )
                },
                colors = ButtonDefaults.buttonColors(containerColor = RacingAmberPrimary)
            ) {
                Text(tr("Save Specs", "Uložiť údaje"), fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(tr("Cancel", "Zrušiť"), color = Color.Gray) }
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
        title = { Text(tr("Quick Add Torque Spec", "Rýchle pridanie momentu"), color = Color.White, fontWeight = FontWeight.Bold) },
        text = {
            Column {
                OutlinedTextField(
                    value = boltName,
                    onValueChange = { boltName = it },
                    label = { Text(tr("Bolt / Fastener Name (e.g. Front Axle Pinch)", "Názov skrutky / matice (napr. Predná os)")) },
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
                    label = { Text(tr("Torque in Nm (e.g. 23)", "Moment v Nm (napr. 23)")) },
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
                Text(tr("Add Torque", "Pridať moment"), fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(tr("Cancel", "Zrušiť"), color = Color.Gray) }
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
                Text(tr("Apply", "Použiť"), color = RacingAmberPrimary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

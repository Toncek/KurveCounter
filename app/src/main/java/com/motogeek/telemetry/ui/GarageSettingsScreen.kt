package com.motogeek.telemetry.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.motogeek.telemetry.data.local.MotorcycleEntity
import com.motogeek.telemetry.domain.maintenance.MotorcycleTemplateCatalog
import com.motogeek.telemetry.domain.maintenance.TechnicalSpecsDto
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import java.util.Locale
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GarageSettingsScreen(viewModel: MainViewModel) {
    val clipboardManager = LocalClipboardManager.current
    val coroutineScope = rememberCoroutineScope()
    val activeBike by viewModel.activeMotorcycle.collectAsState(initial = null)

    var showPasteDialog by remember { mutableStateOf(false) }
    var pastedJson by remember { mutableStateOf("") }
    var showAddBikeSheet by remember { mutableStateOf(false) }
    var showTemplatesDialog by remember { mutableStateOf(false) }

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            coroutineScope.launch {
                viewModel.importExportManager.importGarageBackup(uri)
            }
        }
    }

    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri: Uri? ->
        if (uri != null) {
            coroutineScope.launch {
                viewModel.importExportManager.exportGarageBackup(uri)
            }
        }
    }

    val scrollState = rememberScrollState()

    Column(modifier = Modifier.fillMaxSize().padding(16.dp).verticalScroll(scrollState)) {
        Text(
            text = "Garage & Settings",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(32.dp))

        activeBike?.let { bike ->
            QuickReferenceSheetCard(bike)
            Spacer(modifier = Modifier.height(32.dp))
        }

        Button(
            onClick = { showAddBikeSheet = true },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(if (activeBike != null) "Edit Motorcycle" else "Add Motorcycle")
        }

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = { showTemplatesDialog = true },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
        ) {
            Text("Load Built-In Template")
        }

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = {
                clipboardManager.setText(AnnotatedString(viewModel.importExportManager.aiServiceManualPrompt))
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Copy AI Service Manual Prompt")
        }

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = { showPasteDialog = true },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Paste JSON Schedule / Template")
        }

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = { importLauncher.launch(arrayOf("application/json", "*/*")) },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Import JSON from File")
        }

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = { exportLauncher.launch("motogeek_garage_backup.json") },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Export Garage Backup")
        }

        Spacer(modifier = Modifier.height(80.dp))

        if (showPasteDialog) {
            AlertDialog(
                onDismissRequest = { showPasteDialog = false },
                title = { Text("Paste JSON") },
                text = {
                    OutlinedTextField(
                        value = pastedJson,
                        onValueChange = { pastedJson = it },
                        modifier = Modifier.fillMaxWidth().height(200.dp),
                        label = { Text("JSON String") }
                    )
                },
                confirmButton = {
                    Button(onClick = {
                        coroutineScope.launch {
                            viewModel.importExportManager.importGarageBackupFromString(pastedJson)
                        }
                        showPasteDialog = false
                    }) { Text("Import") }
                },
                dismissButton = {
                    TextButton(onClick = { showPasteDialog = false }) { Text("Cancel") }
                }
            )
        }

        if (showTemplatesDialog) {
            AlertDialog(
                onDismissRequest = { showTemplatesDialog = false },
                title = { Text("Select Template") },
                text = {
                    Column {
                        MotorcycleTemplateCatalog.allTemplates.forEach { template ->
                            TextButton(
                                onClick = {
                                    viewModel.applyTemplate(template)
                                    showTemplatesDialog = false
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(template.motorcycle.model)
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showTemplatesDialog = false }) { Text("Cancel") }
                }
            )
        }

        if (showAddBikeSheet) {
            ModalBottomSheet(
                onDismissRequest = { showAddBikeSheet = false },
                modifier = Modifier.fillMaxHeight(0.9f)
            ) {
                AddEditMotorcycleSheet(
                    existingBike = activeBike,
                    onDismiss = { showAddBikeSheet = false },
                    onSave = { entity ->
                        coroutineScope.launch {
                            if (activeBike != null) {
                                viewModel.repository.updateMotorcycle(entity)
                            } else {
                                viewModel.repository.insertMotorcycle(entity)
                            }
                            showAddBikeSheet = false
                        }
                    }
                )
            }
        }
    }
}

@Composable
fun QuickReferenceSheetCard(motorcycle: MotorcycleEntity) {
    val json = Json { ignoreUnknownKeys = true }
    val specs: TechnicalSpecsDto? = try {
        if (motorcycle.technicalSpecsJson.isNotBlank() && motorcycle.technicalSpecsJson != "{}") {
            json.decodeFromString<TechnicalSpecsDto>(motorcycle.technicalSpecsJson)
        } else null
    } catch (e: Exception) { null }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(text = "Torque & Specs Quick-Sheet", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(text = "Garbážny Ťahák", style = MaterialTheme.typography.labelMedium)
            Spacer(modifier = Modifier.height(16.dp))

            if (specs != null) {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    item { SpecBox("Tire Pressure", "F: ${specs.frontTirePressurePsi?.div(14.5038)?.let { String.format(Locale.US, "%.1f", it) }} Bar\nR: ${specs.rearTirePressurePsi?.div(14.5038)?.let { String.format(Locale.US, "%.1f", it) }} Bar") }
                    item { SpecBox("Chain Slack", "${specs.chainSlackMm ?: "N/A"} mm") }
                    item { SpecBox("Oil Capacity", "${specs.oilCapacityLiters ?: "N/A"} L") }
                    item { SpecBox("Rear Axle", "${specs.rearAxleNutTorqueNm ?: "N/A"} Nm") }
                    item { SpecBox("Oil Drain", "${specs.oilDrainPlugTorqueNm ?: "N/A"} Nm") }
                    item { SpecBox("Caliper Bolts", "${specs.frontCaliperBoltsTorqueNm ?: "N/A"} Nm") }
                }
            } else {
                Text("No technical specs available. Edit motorcycle to add them.")
            }
        }
    }
}

@Composable
fun SpecBox(label: String, value: String) {
    Card(modifier = Modifier.size(120.dp, 100.dp)) {
        Column(
            modifier = Modifier.fillMaxSize().padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(text = label, style = MaterialTheme.typography.labelMedium, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            Spacer(modifier = Modifier.height(8.dp))
            Text(text = value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        }
    }
}

@Composable
fun AddEditMotorcycleSheet(existingBike: MotorcycleEntity?, onDismiss: () -> Unit, onSave: (MotorcycleEntity) -> Unit) {
    val json = Json { ignoreUnknownKeys = true }
    val specs: TechnicalSpecsDto? = try {
        if (existingBike != null && existingBike.technicalSpecsJson.isNotBlank() && existingBike.technicalSpecsJson != "{}") {
            json.decodeFromString<TechnicalSpecsDto>(existingBike.technicalSpecsJson)
        } else null
    } catch (e: Exception) { null }

    var make by remember { mutableStateOf(existingBike?.make ?: "") }
    var model by remember { mutableStateOf(existingBike?.model ?: "") }
    var year by remember { mutableStateOf(existingBike?.year?.toString() ?: "") }
    var cc by remember { mutableStateOf(existingBike?.engineDisplacementCc?.toString() ?: "") }
    var odo by remember { mutableStateOf(existingBike?.baseOdometerKm?.toString() ?: "") }

    // Technical Specs
    var oilCapacity by remember { mutableStateOf(specs?.oilCapacityLiters?.toString() ?: "") }
    var frontTire by remember { mutableStateOf(specs?.frontTirePressurePsi?.div(14.5038)?.let { String.format(Locale.US, "%.1f", it) } ?: "") }
    var rearTire by remember { mutableStateOf(specs?.rearTirePressurePsi?.div(14.5038)?.let { String.format(Locale.US, "%.1f", it) } ?: "") }
    var chainSlack by remember { mutableStateOf(specs?.chainSlackMm ?: "") }

    // Torque Specs
    var oilDrainTorque by remember { mutableStateOf(specs?.oilDrainPlugTorqueNm?.toString() ?: "") }
    var rearAxleTorque by remember { mutableStateOf(specs?.rearAxleNutTorqueNm?.toString() ?: "") }
    var frontAxleTorque by remember { mutableStateOf("") }
    var caliperTorque by remember { mutableStateOf(specs?.frontCaliperBoltsTorqueNm?.toString() ?: "") }
    var sparkPlugTorque by remember { mutableStateOf("") }

    val scrollState = rememberScrollState()

    Column(modifier = Modifier.padding(16.dp).verticalScroll(scrollState)) {
        Text("Add/Edit Motorcycle", style = MaterialTheme.typography.titleLarge)
        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(value = make, onValueChange = { make = it }, label = { Text("Make (e.g. Yamaha)") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(value = model, onValueChange = { model = it }, label = { Text("Model (e.g. MT-09)") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(value = year, onValueChange = { year = it }, label = { Text("Year") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(value = cc, onValueChange = { cc = it }, label = { Text("Displacement (cc)") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(value = odo, onValueChange = { odo = it }, label = { Text("Current Odometer (km)") }, modifier = Modifier.fillMaxWidth())

        Spacer(modifier = Modifier.height(16.dp))
        Text("Technical Specs", style = MaterialTheme.typography.titleMedium)
        OutlinedTextField(value = oilCapacity, onValueChange = { oilCapacity = it }, label = { Text("Oil Capacity (L)") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(value = frontTire, onValueChange = { frontTire = it }, label = { Text("Front Tire Pressure (Bar)") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(value = rearTire, onValueChange = { rearTire = it }, label = { Text("Rear Tire Pressure (Bar)") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(value = chainSlack, onValueChange = { chainSlack = it }, label = { Text("Chain Slack (mm)") }, modifier = Modifier.fillMaxWidth())

        Spacer(modifier = Modifier.height(16.dp))
        Text("Torque Specs (Nm)", style = MaterialTheme.typography.titleMedium)
        OutlinedTextField(value = oilDrainTorque, onValueChange = { oilDrainTorque = it }, label = { Text("Oil Drain Bolt") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(value = rearAxleTorque, onValueChange = { rearAxleTorque = it }, label = { Text("Rear Axle") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(value = frontAxleTorque, onValueChange = { frontAxleTorque = it }, label = { Text("Front Axle") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(value = caliperTorque, onValueChange = { caliperTorque = it }, label = { Text("Caliper Bolts") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(value = sparkPlugTorque, onValueChange = { sparkPlugTorque = it }, label = { Text("Spark Plugs") }, modifier = Modifier.fillMaxWidth())

        Spacer(modifier = Modifier.height(24.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            TextButton(onClick = onDismiss) { Text("Cancel") }
            Spacer(modifier = Modifier.width(8.dp))
            Button(onClick = {
                val specsJson = """
                {
                    "oilCapacityLiters": ${oilCapacity.toDoubleOrNull()},
                    "frontTirePressurePsi": ${frontTire.toDoubleOrNull()?.times(14.5038)},
                    "rearTirePressurePsi": ${rearTire.toDoubleOrNull()?.times(14.5038)},
                    "chainSlackMm": "$chainSlack",
                    "rearAxleNutTorqueNm": ${rearAxleTorque.toDoubleOrNull()},
                    "oilDrainPlugTorqueNm": ${oilDrainTorque.toDoubleOrNull()},
                    "frontCaliperBoltsTorqueNm": ${caliperTorque.toDoubleOrNull()}
                }
                """.trimIndent()

                val bikeId = existingBike?.id ?: UUID.randomUUID().toString()

                val bike = MotorcycleEntity(
                    id = bikeId,
                    make = make,
                    model = model,
                    year = year.toIntOrNull() ?: 2024,
                    engineDisplacementCc = cc.toIntOrNull() ?: 0,
                    baseOdometerKm = odo.toDoubleOrNull() ?: 0.0,
                    accumulatedTrackedKm = existingBike?.accumulatedTrackedKm ?: 0.0,
                    manualAdjustmentOffsetKm = existingBike?.manualAdjustmentOffsetKm ?: 0.0,
                    technicalSpecsJson = specsJson,
                    isActive = true
                )
                onSave(bike)
            }) {
                Text("Save")
            }
        }
        Spacer(modifier = Modifier.height(80.dp))
    }
}

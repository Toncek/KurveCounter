package com.motogeek.telemetry.ui

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorManager
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import com.motogeek.telemetry.ui.components.SegmentedDigitalText
import com.motogeek.telemetry.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutAndDiagnosticsScreen(viewModel: MainViewModel, navController: NavController) {
    val context = LocalContext.current
    val telemetryState by viewModel.liveTelemetryState.collectAsState()
    val isDemoMode by viewModel.isDemoMode.collectAsState()

    val sensorManager = remember { context.getSystemService(Context.SENSOR_SERVICE) as SensorManager }
    val accelSensor = remember { sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) }
    val gyroSensor = remember { sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE) }
    val baroSensor = remember { sensorManager.getDefaultSensor(Sensor.TYPE_PRESSURE) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Sensor Diagnostics & About",
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
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Spacer(modifier = Modifier.height(2.dp))

            // 1. Live Hardware IMU Readouts Card
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
                            text = "LIVE SENSOR FUSION ENGINE",
                            color = Color(0xFF6B7A99),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            letterSpacing = 1.sp
                        )

                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = (if (telemetryState.calibrationStatus == "LOCKED") ApexEmeraldGreen else WarningGold).copy(alpha = 0.2f),
                            border = BorderStroke(1.dp, if (telemetryState.calibrationStatus == "LOCKED") ApexEmeraldGreen else WarningGold)
                        ) {
                            Text(
                                text = telemetryState.calibrationStatus,
                                color = if (telemetryState.calibrationStatus == "LOCKED") ApexEmeraldGreen else WarningGold,
                                fontWeight = FontWeight.Bold,
                                fontSize = 10.sp,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        DiagnosticMetricBox("LEAN ANGLE", String.format("%.1f", telemetryState.leanAngleDeg), "°", TelemetryIceCyan, Modifier.weight(1f))
                        Spacer(modifier = Modifier.width(8.dp))
                        DiagnosticMetricBox("SPEED", "${telemetryState.currentSpeedKmh.toInt()}", "KM/H", Color.White, Modifier.weight(1f))
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        DiagnosticMetricBox("LONGITUDINAL G", String.format("%.2f", telemetryState.longitudinalG), "G", if (telemetryState.longitudinalG < 0) RedlineCrimson else ApexEmeraldGreen, Modifier.weight(1f))
                        Spacer(modifier = Modifier.width(8.dp))
                        DiagnosticMetricBox("LATERAL G", String.format("%.2f", telemetryState.lateralG), "G", RacingAmberPrimary, Modifier.weight(1f))
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = { viewModel.resetPeaks() },
                            colors = ButtonDefaults.buttonColors(containerColor = TftSurfaceElevated),
                            shape = RoundedCornerShape(6.dp),
                            border = BorderStroke(1.dp, TitaniumBorder),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Reset Peak Holds", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }

                        Button(
                            onClick = {
                                if (isDemoMode) viewModel.stopDemoMode() else viewModel.startDemoMode()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = if (isDemoMode) RedlineCrimson else RacingAmberPrimary),
                            shape = RoundedCornerShape(6.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(if (isDemoMode) "Stop Sim" else "Simulate 100Hz", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            // 2. Hardware Sensor Availability Checklist Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp),
                colors = CardDefaults.cardColors(containerColor = TftSurfaceDark),
                border = BorderStroke(1.dp, TitaniumBorder)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "ON-DEVICE HARDWARE SENSORS",
                        color = Color(0xFF6B7A99),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        letterSpacing = 1.sp
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    HardwareSensorRow(
                        name = "3-Axis Accelerometer",
                        sensor = accelSensor,
                        fallbackDesc = "High-G Tri-axial Motion Sensor"
                    )
                    HorizontalDivider(color = TitaniumBorder.copy(alpha = 0.5f), thickness = 0.5.dp)

                    HardwareSensorRow(
                        name = "3-Axis Gyroscope",
                        sensor = gyroSensor,
                        fallbackDesc = "Precision Angular Velocity (Roll/Pitch/Yaw)"
                    )
                    HorizontalDivider(color = TitaniumBorder.copy(alpha = 0.5f), thickness = 0.5.dp)

                    HardwareSensorRow(
                        name = "Barometric Altimeter",
                        sensor = baroSensor,
                        fallbackDesc = "Atmospheric Pressure & Elevation Gain"
                    )
                    HorizontalDivider(color = TitaniumBorder.copy(alpha = 0.5f), thickness = 0.5.dp)

                    // GPS Row
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("Fused GNSS / GPS", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            Text("Multi-constellation GPS/GLONASS/Galileo", color = Color.Gray, fontSize = 11.sp)
                        }
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = ApexEmeraldGreen.copy(alpha = 0.15f),
                            border = BorderStroke(1.dp, ApexEmeraldGreen.copy(alpha = 0.5f))
                        ) {
                            Text("PRESENT", color = ApexEmeraldGreen, fontSize = 9.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                        }
                    }
                }
            }

            // 3. App Details & Architecture Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp),
                colors = CardDefaults.cardColors(containerColor = TftSurfaceDark),
                border = BorderStroke(1.dp, TitaniumBorder)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "ABOUT KURVECOUNTER TELEMETRY",
                        color = Color(0xFF6B7A99),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        letterSpacing = 1.sp
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    AppInfoRow("Application", "KurveCounter (MotoGeek Telemetry)")
                    AppInfoRow("Package", "com.motogeek.telemetry")
                    AppInfoRow("Version", "1.0.0 • ApexCarbon TFT Edition")
                    AppInfoRow("Target SDK", "Android 15 (API 35)")
                    AppInfoRow("Minimum SDK", "Android 10 (API 29)")
                    AppInfoRow("Sensors Sample Rate", "100 Hz (Biquad Butterworth Filtered)")
                    AppInfoRow("Database", "Room Database v2 (KSP Compiler)")
                }
            }

            Spacer(modifier = Modifier.height(28.dp))
        }
    }
}

@Composable
private fun DiagnosticMetricBox(label: String, value: String, unit: String, color: Color, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(8.dp),
        color = TftSurfaceElevated,
        border = BorderStroke(1.dp, TitaniumBorder)
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(label, color = Color.Gray, fontSize = 9.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
            Spacer(modifier = Modifier.height(3.dp))
            SegmentedDigitalText(
                text = value,
                unit = unit,
                color = color,
                fontSize = 16.sp
            )
        }
    }
}

@Composable
private fun HardwareSensorRow(name: String, sensor: Sensor?, fallbackDesc: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(name, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            Text(
                text = sensor?.name ?: fallbackDesc,
                color = Color.Gray,
                fontSize = 11.sp,
                maxLines = 1
            )
        }

        Surface(
            shape = RoundedCornerShape(4.dp),
            color = if (sensor != null) ApexEmeraldGreen.copy(alpha = 0.15f) else WarningGold.copy(alpha = 0.15f),
            border = BorderStroke(1.dp, if (sensor != null) ApexEmeraldGreen.copy(alpha = 0.5f) else WarningGold.copy(alpha = 0.5f))
        ) {
            Text(
                text = if (sensor != null) "AVAILABLE" else "SIMULATED",
                color = if (sensor != null) ApexEmeraldGreen else WarningGold,
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
            )
        }
    }
}

@Composable
private fun AppInfoRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, color = Color.Gray, fontSize = 12.sp)
        Text(value, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, fontFamily = FontFamily.Monospace)
    }
}

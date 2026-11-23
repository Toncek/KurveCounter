package com.motogeek.telemetry.ui

import android.content.Context
import android.content.Intent
import android.hardware.Sensor
import android.hardware.SensorManager
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.motogeek.telemetry.BuildConfig
import com.motogeek.telemetry.service.TelemetryForegroundService
import com.motogeek.telemetry.ui.components.SegmentedDigitalText
import com.motogeek.telemetry.ui.theme.*
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutAndDiagnosticsScreen(viewModel: MainViewModel, navController: NavController) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val telemetryState by viewModel.liveTelemetryState.collectAsState()
    val isDemoMode by viewModel.isDemoMode.collectAsState()
    val appLang = LocalAppLang.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    val sensorManager = remember { context.getSystemService(Context.SENSOR_SERVICE) as SensorManager }
    val accelSensor = remember { sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) }
    val gyroSensor = remember { sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE) }
    val baroSensor = remember { sensorManager.getDefaultSensor(Sensor.TYPE_PRESSURE) }

    val isCalLocked = telemetryState.calibrationStatus.contains("LOCKED", ignoreCase = true)
    val localizedCalStatus = when (telemetryState.calibrationStatus.uppercase()) {
        "LOCKED" -> tr("LOCKED", "ZAMKNUTÉ")
        "MANUAL 0° LOCKED" -> tr("MANUAL 0° LOCKED", "MANUÁLNE 0° ZAMKNUTÉ")
        "GRAVITY_LOCKED" -> tr("GRAVITY LOCKED", "GRAVITÁCIA ZAMKNUTÁ")
        "WAITING" -> tr("WAITING", "ČAKÁ SA")
        else -> telemetryState.calibrationStatus
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = tr("Sensor Diagnostics & About", "Diagnostika senzorov & O aplikácii"),
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
                            text = tr("LIVE SENSOR FUSION ENGINE", "ŽIVÁ FÚZIA SENZOROV"),
                            color = Color(0xFF6B7A99),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            letterSpacing = 1.sp
                        )

                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = (if (isCalLocked) ApexEmeraldGreen else WarningGold).copy(alpha = 0.2f),
                            border = BorderStroke(1.dp, if (isCalLocked) ApexEmeraldGreen else WarningGold)
                        ) {
                            Text(
                                text = localizedCalStatus,
                                color = if (isCalLocked) ApexEmeraldGreen else WarningGold,
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
                        DiagnosticMetricBox(tr("LEAN ANGLE", "NÁKLON"), String.format("%.1f", telemetryState.leanAngleDeg), "°", TelemetryIceCyan, Modifier.weight(1f))
                        Spacer(modifier = Modifier.width(8.dp))
                        DiagnosticMetricBox(tr("SPEED", "RÝCHLOSŤ"), "${telemetryState.currentSpeedKmh.toInt()}", "KM/H", Color.White, Modifier.weight(1f))
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        DiagnosticMetricBox(tr("LONGITUDINAL G", "POZDĹŽNE G"), String.format("%.2f", telemetryState.longitudinalG), "G", if (telemetryState.longitudinalG < 0) RedlineCrimson else ApexEmeraldGreen, Modifier.weight(1f))
                        Spacer(modifier = Modifier.width(8.dp))
                        DiagnosticMetricBox(tr("LATERAL G", "BOČNÉ G"), String.format("%.2f", telemetryState.lateralG), "G", RacingAmberPrimary, Modifier.weight(1f))
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                viewModel.resetPeaks()
                                scope.launch {
                                    snackbarHostState.showSnackbar(appLang.pick("Peak holds reset", "Maximálne hodnoty vynulované"))
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = TftSurfaceElevated),
                            shape = RoundedCornerShape(6.dp),
                            border = BorderStroke(1.dp, TitaniumBorder),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(tr("Reset Peak Holds", "Vynulovať maximá"), color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                        }

                        Button(
                            onClick = {
                                if (isDemoMode) viewModel.stopDemoMode() else viewModel.startDemoMode()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = if (isDemoMode) RedlineCrimson else RacingAmberPrimary),
                            shape = RoundedCornerShape(6.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                if (isDemoMode) tr("Stop Sim", "Zastaviť sim") else tr("Simulate 100Hz", "Simulovať 100Hz"),
                                color = Color.White,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedButton(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            val intent = Intent(context, TelemetryForegroundService::class.java).apply {
                                action = TelemetryForegroundService.ACTION_CALIBRATE_ZERO
                            }
                            try {
                                context.startService(intent)
                            } catch (_: Exception) {}
                            scope.launch {
                                snackbarHostState.showSnackbar(appLang.pick("0° lean angle calibrated", "Nulový náklon (0°) nakalibrovaný"))
                            }
                        },
                        shape = RoundedCornerShape(6.dp),
                        border = BorderStroke(1.dp, TelemetryIceCyan.copy(alpha = 0.6f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(tr("Calibrate 0°", "Kalibrovať 0°"), color = TelemetryIceCyan, fontSize = 12.sp, fontWeight = FontWeight.Bold)
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
                        text = tr("ON-DEVICE HARDWARE SENSORS", "HARDVÉROVÉ SENZORY ZARIADENIA"),
                        color = Color(0xFF6B7A99),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        letterSpacing = 1.sp
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    HardwareSensorRow(
                        name = tr("3-Axis Accelerometer", "3-osový akcelerometer"),
                        sensor = accelSensor,
                        fallbackDesc = tr("High-G Tri-axial Motion Sensor", "3-osový snímač pohybu s vysokým G"),
                        isDemoMode = isDemoMode
                    )
                    HorizontalDivider(color = TitaniumBorder.copy(alpha = 0.5f), thickness = 0.5.dp)

                    HardwareSensorRow(
                        name = tr("3-Axis Gyroscope", "3-osový gyroskop"),
                        sensor = gyroSensor,
                        fallbackDesc = tr("Precision Angular Velocity (Roll/Pitch/Yaw)", "Presná uhlová rýchlosť (Roll/Pitch/Yaw)"),
                        isDemoMode = isDemoMode
                    )
                    HorizontalDivider(color = TitaniumBorder.copy(alpha = 0.5f), thickness = 0.5.dp)

                    HardwareSensorRow(
                        name = tr("Barometric Altimeter", "Barometrický výškomer"),
                        sensor = baroSensor,
                        fallbackDesc = tr("Atmospheric Pressure & Elevation Gain", "Atmosférický tlak a prevýšenie"),
                        isDemoMode = isDemoMode
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
                        Column(modifier = Modifier.weight(1f)) {
                            Text(tr("Fused GNSS / GPS", "Fúzované GNSS / GPS"), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            Text(tr("Multi-constellation GPS/GLONASS/Galileo", "Viackonštelačné GPS/GLONASS/Galileo"), color = Color.Gray, fontSize = 11.sp)
                        }
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = ApexEmeraldGreen.copy(alpha = 0.15f),
                            border = BorderStroke(1.dp, ApexEmeraldGreen.copy(alpha = 0.5f))
                        ) {
                            Text(
                                tr("PRESENT", "PRÍTOMNÝ"),
                                color = ApexEmeraldGreen,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
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
                        text = tr("ABOUT KURVECOUNTER TELEMETRY", "O APLIKÁCII KURVECOUNTER"),
                        color = Color(0xFF6B7A99),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        letterSpacing = 1.sp
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    AppInfoRow(tr("Application", "Aplikácia"), "KurveCounter (MotoGeek Telemetry)")
                    AppInfoRow(tr("Package", "Balík"), "com.motogeek.telemetry")
                    AppInfoRow(tr("Version", "Verzia"), "${BuildConfig.VERSION_NAME} • ApexCarbon TFT Edition")
                    AppInfoRow(tr("Target SDK", "Cieľové SDK"), "Android 15 (API 35)")
                    AppInfoRow(tr("Minimum SDK", "Minimálne SDK"), "Android 10 (API 29)")
                    AppInfoRow(tr("Sensors Sample Rate", "Vzorkovanie senzorov"), tr("100 Hz (Biquad Butterworth Filtered)", "100 Hz (Biquad Butterworth filter)"))
                    AppInfoRow(tr("Database", "Databáza"), tr("Room Database v3 (KSP Compiler)", "Room Database v3 (KSP kompilátor)"))
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
private fun HardwareSensorRow(
    name: String,
    sensor: Sensor?,
    fallbackDesc: String,
    isDemoMode: Boolean
) {
    val (statusLabel, statusColor) = when {
        sensor != null -> tr("AVAILABLE", "DOSTUPNÝ") to ApexEmeraldGreen
        isDemoMode -> tr("SIMULATED", "SIMULOVANÝ") to WarningGold
        else -> tr("UNAVAILABLE", "NEDOSTUPNÝ") to RedlineCrimson
    }

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

        Spacer(modifier = Modifier.width(8.dp))

        Surface(
            shape = RoundedCornerShape(4.dp),
            color = statusColor.copy(alpha = 0.15f),
            border = BorderStroke(1.dp, statusColor.copy(alpha = 0.5f))
        ) {
            Text(
                text = statusLabel,
                color = statusColor,
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

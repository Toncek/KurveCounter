package com.motogeek.telemetry.ui

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.view.WindowManager
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.motogeek.telemetry.service.LiveTelemetryState
import com.motogeek.telemetry.service.TelemetryForegroundService
import com.motogeek.telemetry.service.TelemetryPreferences
import com.motogeek.telemetry.ui.theme.WarningGold
import com.motogeek.telemetry.ui.theme.tr
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

enum class CockpitLayoutMode {
    APEX_R, CIRCUIT, LAB
}

@Composable
fun CockpitScreen(viewModel: MainViewModel, onStartRide: () -> Unit, onStopRide: () -> Unit) {
    val telemetryState by viewModel.liveTelemetryState.collectAsState()
    val isDemoMode by viewModel.isDemoMode.collectAsState()
    val activeBike by viewModel.activeMotorcycle.collectAsState(initial = null)

    val animatedLeanAngle by animateFloatAsState(
        targetValue = telemetryState.leanAngleDeg.toFloat(),
        animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessMediumLow),
        label = "leanAngle"
    )
    val animatedSpeed by animateFloatAsState(
        targetValue = telemetryState.currentSpeedKmh.toFloat(),
        animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessMediumLow),
        label = "speed"
    )
    val animatedLongitudinalG by animateFloatAsState(
        targetValue = telemetryState.longitudinalG.toFloat(),
        animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessMediumLow),
        label = "longitudinalG"
    )
    val animatedLateralG by animateFloatAsState(
        targetValue = telemetryState.lateralG.toFloat(),
        animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessMediumLow),
        label = "lateralG"
    )

    val haptic = LocalHapticFeedback.current
    val configuration = LocalConfiguration.current
    val context = LocalContext.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    val isRidingOrDemo = telemetryState.isRiding || isDemoMode
    var currentLayoutMode by remember { mutableStateOf(CockpitLayoutMode.APEX_R) }

    DisposableEffect(isRidingOrDemo) {
        val window = (context as? Activity)?.window
        if (isRidingOrDemo) {
            window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        onDispose {
            window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    if (isLandscape) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            // Compact top bar: Layout mode chips, Auto-Start / Boot chips (when not riding), calibration badge
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                CockpitLayoutMode.entries.forEach { mode ->
                    val isSelected = currentLayoutMode == mode
                    FilterChip(
                        selected = isSelected,
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            currentLayoutMode = mode
                        },
                        label = {
                            Text(
                                text = mode.name.replace("_", "-"),
                                fontSize = 11.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    )
                }

                if (!isRidingOrDemo) {
                    FilterChip(
                        selected = telemetryState.autoStartAbove5Kmh,
                        onClick = {
                            val prefs = TelemetryPreferences(context)
                            prefs.autoStartAbove5Kmh = !prefs.autoStartAbove5Kmh
                            sendTelemetryServiceAction(context, TelemetryForegroundService.ACTION_SYNC_PREFS)
                        },
                        label = { Text(tr("Auto-Start > 5 km/h", "Auto-Štart > 5 km/h"), fontSize = 11.sp) }
                    )
                    FilterChip(
                        selected = telemetryState.startOnBoot,
                        onClick = {
                            val prefs = TelemetryPreferences(context)
                            prefs.startOnBoot = !prefs.startOnBoot
                            sendTelemetryServiceAction(context, TelemetryForegroundService.ACTION_SYNC_PREFS)
                        },
                        label = { Text(tr("Run on Boot", "Po štarte"), fontSize = 11.sp) }
                    )
                }

                Badge(
                    containerColor = if (telemetryState.calibrationStatus == "LOCKED") Color(0xFF00E676) else Color(0xFFFFEA00)
                ) {
                    Text(
                        text = localizedCalibrationStatus(telemetryState.calibrationStatus),
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        fontSize = 10.sp
                    )
                }

                if (telemetryState.isRiding && telemetryState.currentSpeedKmh < 1.0 && !isDemoMode) {
                    Badge(containerColor = MaterialTheme.colorScheme.tertiary) {
                        Text(
                            text = tr("AUTO-PAUSED", "AUTO-PAUZA"),
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            color = Color.Black,
                            fontWeight = FontWeight.Bold,
                            fontSize = 10.sp
                        )
                    }
                }
            }

            if (activeBike == null && !isRidingOrDemo) {
                Spacer(modifier = Modifier.height(6.dp))
                NullActiveBikeWarningBanner()
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                when (currentLayoutMode) {
                    CockpitLayoutMode.APEX_R -> {
                        // Left: Lean Gauge
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight(),
                            contentAlignment = Alignment.Center
                        ) {
                            Box(modifier = Modifier.size(190.dp), contentAlignment = Alignment.Center) {
                                LeanAngleGauge(
                                    leanAngle = animatedLeanAngle.toDouble(),
                                    peakLeft = telemetryState.peakLeanLeftDeg,
                                    peakRight = telemetryState.peakLeanRightDeg
                                )
                                TextButton(
                                    onClick = { viewModel.resetPeaks() },
                                    modifier = Modifier.align(Alignment.BottomCenter).offset(y = 18.dp)
                                ) {
                                    Text(tr("Reset Peaks", "Vynulovať maximá"), fontSize = 10.sp)
                                }
                            }
                        }

                        // Center: Speed & Corner Counter
                        Column(
                            modifier = Modifier.weight(0.9f),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Text(
                                text = "${animatedSpeed.toInt()}",
                                fontSize = 60.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace,
                                style = LocalTextStyle.current.copy(fontFeatureSettings = "tnum")
                            )
                            Text(text = "km/h", fontSize = 15.sp)
                            Spacer(modifier = Modifier.height(10.dp))
                            MetricCard(tr("Corners", "Zákruty"), "${telemetryState.leftCorners}L | ${telemetryState.rightCorners}R")
                        }

                        // Right: GG-Diagram + Controls
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .verticalScroll(rememberScrollState()),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Box(modifier = Modifier.size(120.dp)) {
                                GForceGauge(
                                    longitudinalG = animatedLongitudinalG.toDouble(),
                                    lateralG = animatedLateralG.toDouble()
                                )
                            }
                            Spacer(modifier = Modifier.height(10.dp))
                            LandscapeActionControls(
                                isRidingOrDemo = isRidingOrDemo,
                                isDemoMode = isDemoMode,
                                isPaused = telemetryState.isPaused,
                                hasActiveBike = activeBike != null,
                                onPauseRide = {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    viewModel.pauseRide()
                                },
                                onResumeRide = {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    viewModel.resumeRide()
                                },
                                onStop = {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    if (isDemoMode) viewModel.stopDemoMode() else onStopRide()
                                },
                                onStartRide = {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    onStartRide()
                                },
                                onCalibrateZero = {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    sendTelemetryServiceAction(context, TelemetryForegroundService.ACTION_CALIBRATE_ZERO)
                                },
                                onStartDemo = { viewModel.startDemoMode() }
                            )
                        }
                    }

                    CockpitLayoutMode.CIRCUIT -> {
                        Box(
                            modifier = Modifier
                                .weight(1.8f)
                                .fillMaxHeight()
                                .verticalScroll(rememberScrollState()),
                            contentAlignment = Alignment.Center
                        ) {
                            CircuitLayout(
                                telemetryState = telemetryState,
                                animatedLongitudinalG = animatedLongitudinalG,
                                animatedLateralG = animatedLateralG
                            )
                        }

                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .verticalScroll(rememberScrollState()),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            LandscapeActionControls(
                                isRidingOrDemo = isRidingOrDemo,
                                isDemoMode = isDemoMode,
                                isPaused = telemetryState.isPaused,
                                hasActiveBike = activeBike != null,
                                onPauseRide = {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    viewModel.pauseRide()
                                },
                                onResumeRide = {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    viewModel.resumeRide()
                                },
                                onStop = {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    if (isDemoMode) viewModel.stopDemoMode() else onStopRide()
                                },
                                onStartRide = {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    onStartRide()
                                },
                                onCalibrateZero = {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    sendTelemetryServiceAction(context, TelemetryForegroundService.ACTION_CALIBRATE_ZERO)
                                },
                                onStartDemo = { viewModel.startDemoMode() }
                            )
                        }
                    }

                    CockpitLayoutMode.LAB -> {
                        Box(
                            modifier = Modifier
                                .weight(1.8f)
                                .fillMaxHeight()
                                .verticalScroll(rememberScrollState()),
                            contentAlignment = Alignment.Center
                        ) {
                            LabLayout(telemetryState = telemetryState)
                        }

                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .verticalScroll(rememberScrollState()),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            LandscapeActionControls(
                                isRidingOrDemo = isRidingOrDemo,
                                isDemoMode = isDemoMode,
                                isPaused = telemetryState.isPaused,
                                hasActiveBike = activeBike != null,
                                onPauseRide = {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    viewModel.pauseRide()
                                },
                                onResumeRide = {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    viewModel.resumeRide()
                                },
                                onStop = {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    if (isDemoMode) viewModel.stopDemoMode() else onStopRide()
                                },
                                onStartRide = {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    onStartRide()
                                },
                                onCalibrateZero = {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    sendTelemetryServiceAction(context, TelemetryForegroundService.ACTION_CALIBRATE_ZERO)
                                },
                                onStartDemo = { viewModel.startDemoMode() }
                            )
                        }
                    }
                }
            }
        }
    } else {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = tr("Cockpit", "Kokpit"),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(16.dp))

            if (activeBike == null && !isRidingOrDemo) {
                NullActiveBikeWarningBanner()
                Spacer(modifier = Modifier.height(12.dp))
            }

            // Layout Mode Segmented Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .background(Color.DarkGray.copy(alpha = 0.3f), shape = MaterialTheme.shapes.large),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                CockpitLayoutMode.entries.forEach { mode ->
                    val isSelected = currentLayoutMode == mode
                    TextButton(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            currentLayoutMode = mode
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            text = mode.name.replace("_", "-"),
                            color = if (isSelected) Color(0xFF00E5FF) else Color.Gray,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            if (!telemetryState.isRiding && !isDemoMode) {
                ElevatedFilterChip(
                    selected = false,
                    onClick = { viewModel.startDemoMode() },
                    label = { Text(tr("Try Demo Ride (Simulator)", "Skúsiť demo jazdu (Simulátor)"), fontWeight = FontWeight.Bold) },
                    colors = FilterChipDefaults.elevatedFilterChipColors(
                        containerColor = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.2f),
                        labelColor = MaterialTheme.colorScheme.tertiary
                    )
                )
                Spacer(modifier = Modifier.height(16.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    FilterChip(
                        selected = telemetryState.autoStartAbove5Kmh,
                        onClick = {
                            val prefs = TelemetryPreferences(context)
                            prefs.autoStartAbove5Kmh = !prefs.autoStartAbove5Kmh
                            sendTelemetryServiceAction(context, TelemetryForegroundService.ACTION_SYNC_PREFS)
                        },
                        label = { Text(tr("Auto-Start > 5 km/h", "Auto-Štart > 5 km/h")) }
                    )
                    FilterChip(
                        selected = telemetryState.startOnBoot,
                        onClick = {
                            val prefs = TelemetryPreferences(context)
                            prefs.startOnBoot = !prefs.startOnBoot
                            sendTelemetryServiceAction(context, TelemetryForegroundService.ACTION_SYNC_PREFS)
                        },
                        label = { Text(tr("Run on Phone Boot", "Spustiť po štarte")) }
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))
            }

            // Calibration Badge
            Badge(
                containerColor = if (telemetryState.calibrationStatus == "LOCKED") Color(0xFF00E676) else Color(0xFFFFEA00)
            ) {
                Text(localizedCalibrationStatus(telemetryState.calibrationStatus), modifier = Modifier.padding(4.dp))
            }

            Spacer(modifier = Modifier.height(32.dp))

            if (telemetryState.isRiding && telemetryState.currentSpeedKmh < 1.0 && !isDemoMode) {
                Spacer(modifier = Modifier.height(8.dp))
                Badge(containerColor = MaterialTheme.colorScheme.tertiary) {
                    Text(tr("AUTO-PAUSED", "AUTO-PAUZA"), modifier = Modifier.padding(4.dp), color = Color.Black, fontWeight = FontWeight.Bold)
                }
            }

            when (currentLayoutMode) {
                CockpitLayoutMode.APEX_R -> {
                    ApexRLayout(
                        telemetryState = telemetryState,
                        animatedLeanAngle = animatedLeanAngle,
                        animatedSpeed = animatedSpeed,
                        onResetPeaks = { viewModel.resetPeaks() }
                    )
                }
                CockpitLayoutMode.CIRCUIT -> {
                    CircuitLayout(
                        telemetryState = telemetryState,
                        animatedLongitudinalG = animatedLongitudinalG,
                        animatedLateralG = animatedLateralG
                    )
                }
                CockpitLayoutMode.LAB -> {
                    LabLayout(telemetryState = telemetryState)
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            if (isRidingOrDemo) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (!isDemoMode && !telemetryState.isPaused) {
                        Button(
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                viewModel.pauseRide()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.tertiary),
                            modifier = Modifier.weight(1f).heightIn(min = 68.dp)
                        ) {
                            Text(tr("PAUSE", "PAUZA"), color = Color.Black)
                        }
                    } else if (!isDemoMode && telemetryState.isPaused) {
                        Button(
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                viewModel.resumeRide()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00E676)),
                            modifier = Modifier.weight(1f).heightIn(min = 68.dp)
                        ) {
                            Text(tr("RESUME", "POKRAČOVAŤ"), color = Color.Black)
                        }
                    }
                    Button(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            if (isDemoMode) viewModel.stopDemoMode() else onStopRide()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                        modifier = Modifier.weight(1f).heightIn(min = 68.dp)
                    ) {
                        Text(tr("STOP", "ZASTAVIŤ"))
                    }
                }
            } else {
                Button(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onStartRide()
                    },
                    enabled = activeBike != null,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 68.dp)
                ) {
                    Text(tr("START RIDE", "SPUSTIŤ JAZDU"))
                }
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedButton(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        sendTelemetryServiceAction(context, TelemetryForegroundService.ACTION_CALIBRATE_ZERO)
                    },
                    modifier = Modifier.heightIn(min = 68.dp).fillMaxWidth()
                ) {
                    Text(tr("CALIBRATE 0° (SET ZERO)", "KALIBROVAŤ 0° (NULA)"))
                }
            }
            Spacer(modifier = Modifier.height(80.dp)) // padding for bottom nav
        }
    }
}

private fun sendTelemetryServiceAction(context: Context, action: String) {
    val intent = Intent(context, TelemetryForegroundService::class.java).apply {
        this.action = action
    }
    try {
        context.startService(intent)
    } catch (_: Exception) {}
}

@Composable
private fun localizedCalibrationStatus(status: String): String = when (status) {
    "LOCKED" -> tr("LOCKED", "UZAMKNUTÉ")
    "MANUAL 0° LOCKED" -> tr("MANUAL 0° LOCKED", "MANUÁLNE 0° UZAMKNUTÉ")
    "CALIBRATING POCKET MODE" -> tr("CALIBRATING POCKET MODE", "KALIBRÁCIA REŽIMU VRECKA")
    "SIMULATOR" -> tr("SIMULATOR", "SIMULÁTOR")
    else -> status
}

@Composable
private fun NullActiveBikeWarningBanner(modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = WarningGold.copy(alpha = 0.12f),
        border = BorderStroke(1.dp, WarningGold.copy(alpha = 0.7f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Warning,
                contentDescription = null,
                tint = WarningGold,
                modifier = Modifier.size(18.dp)
            )
            Text(
                text = tr(
                    "No active motorcycle selected. Select or add a bike in Garage to start a ride.",
                    "Nie je vybraný žiadny motocykel. Vyberte alebo pridajte motorku v Garáži pre spustenie jazdy."
                ),
                color = WarningGold,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
private fun LandscapeActionControls(
    isRidingOrDemo: Boolean,
    isDemoMode: Boolean,
    isPaused: Boolean,
    hasActiveBike: Boolean,
    onPauseRide: () -> Unit,
    onResumeRide: () -> Unit,
    onStop: () -> Unit,
    onStartRide: () -> Unit,
    onCalibrateZero: () -> Unit,
    onStartDemo: () -> Unit
) {
    if (isRidingOrDemo) {
        if (!isDemoMode && !isPaused) {
            Button(
                onClick = onPauseRide,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.tertiary),
                modifier = Modifier.heightIn(min = 52.dp).fillMaxWidth()
            ) {
                Text(tr("PAUSE RIDE", "POZASTAVIŤ JAZDU"), color = Color.Black)
            }
            Spacer(modifier = Modifier.height(6.dp))
        } else if (!isDemoMode && isPaused) {
            Button(
                onClick = onResumeRide,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00E676)),
                modifier = Modifier.heightIn(min = 52.dp).fillMaxWidth()
            ) {
                Text(tr("RESUME RIDE", "POKRAČOVAŤ V JAZDE"), color = Color.Black)
            }
            Spacer(modifier = Modifier.height(6.dp))
        }

        Button(
            onClick = onStop,
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
            modifier = Modifier.heightIn(min = 52.dp).fillMaxWidth()
        ) {
            Text(if (isDemoMode) tr("STOP DEMO", "ZASTAVIŤ DEMO") else tr("STOP RIDE", "UKONČIŤ JAZDU"))
        }
    } else {
        Button(
            onClick = onStartRide,
            enabled = hasActiveBike,
            modifier = Modifier.heightIn(min = 52.dp).fillMaxWidth()
        ) {
            Text(tr("START RIDE", "SPUSTIŤ JAZDU"))
        }
        Spacer(modifier = Modifier.height(6.dp))
        OutlinedButton(
            onClick = onCalibrateZero,
            modifier = Modifier.heightIn(min = 52.dp).fillMaxWidth()
        ) {
            Text(tr("CALIBRATE 0° (SET ZERO)", "KALIBROVAŤ 0° (NULA)"))
        }
        Spacer(modifier = Modifier.height(6.dp))
        ElevatedFilterChip(
            selected = false,
            onClick = onStartDemo,
            label = { Text(tr("Try Demo Ride", "Skúsiť demo jazdu"), fontWeight = FontWeight.Bold) },
            colors = FilterChipDefaults.elevatedFilterChipColors(
                containerColor = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.2f),
                labelColor = MaterialTheme.colorScheme.tertiary
            )
        )
    }
}

@Composable
fun LeanAngleGauge(leanAngle: Double, peakLeft: Double, peakRight: Double) {
    Canvas(modifier = Modifier.fillMaxSize()) {
        val sweepAngle = 110f
        val startAngle = 270f - (sweepAngle / 2f)
        val radius = size.minDimension / 2 - 20f

        // 5 Zones Colors
        val zoneColors = listOf(
            Color(0xFFFF1744), // Red > 48
            Color(0xFFFF5722), // Orange 40-48
            Color(0xFFFFEA00), // Yellow 30-40
            Color(0xFF00E5FF), // Cyan 15-30
            Color(0xFF00E676), // Green 0-15
            Color(0xFF00E5FF), // Cyan 15-30
            Color(0xFFFFEA00), // Yellow 30-40
            Color(0xFFFF5722), // Orange 40-48
            Color(0xFFFF1744)  // Red > 48
        )

        // Gradient Arc
        drawArc(
            brush = Brush.sweepGradient(
                colors = zoneColors,
                center = center
            ),
            startAngle = startAngle,
            sweepAngle = sweepAngle,
            useCenter = false,
            style = Stroke(width = 20f, cap = StrokeCap.Round)
        )

        // Tick marks
        for (i in -55..55 step 10) {
            val tickAngle = 270f + i
            val tickRadiusInner = radius - 15f
            val tickRadiusOuter = radius + 5f
            val startX = center.x + tickRadiusInner * cos(tickAngle * PI / 180).toFloat()
            val startY = center.y + tickRadiusInner * sin(tickAngle * PI / 180).toFloat()
            val endX = center.x + tickRadiusOuter * cos(tickAngle * PI / 180).toFloat()
            val endY = center.y + tickRadiusOuter * sin(tickAngle * PI / 180).toFloat()

            drawLine(
                color = Color.White.copy(alpha = 0.5f),
                start = Offset(startX, startY),
                end = Offset(endX, endY),
                strokeWidth = 2f
            )
        }

        // Animated peak-hold needles
        val drawPeak = { peakVal: Double, color: Color ->
            val peakAngle = 270f + peakVal
            val peakX = center.x + radius * cos(peakAngle * PI / 180).toFloat()
            val peakY = center.y + radius * sin(peakAngle * PI / 180).toFloat()
            drawLine(
                color = color.copy(alpha = 0.7f),
                start = center,
                end = Offset(peakX, peakY),
                strokeWidth = 4f,
                cap = StrokeCap.Round
            )
        }
        drawPeak(-peakLeft, Color.Red)
        drawPeak(peakRight, Color.Red)

        // Current Lean Needle
        val leanClamped = leanAngle.coerceIn(-55.0, 55.0).toFloat()
        val leanColor = when {
            kotlin.math.abs(leanClamped) < 15 -> Color(0xFF00E676)
            kotlin.math.abs(leanClamped) < 30 -> Color(0xFF00E5FF)
            kotlin.math.abs(leanClamped) < 40 -> Color(0xFFFFEA00)
            kotlin.math.abs(leanClamped) < 48 -> Color(0xFFFF5722)
            else -> Color(0xFFFF1744)
        }

        val centerAngle = 270f
        val needleAngle = centerAngle + leanClamped
        val needleX = center.x + radius * cos(needleAngle * PI / 180).toFloat()
        val needleY = center.y + radius * sin(needleAngle * PI / 180).toFloat()

        drawLine(
            color = leanColor,
            start = center,
            end = Offset(needleX, needleY),
            strokeWidth = 8f,
            cap = StrokeCap.Round
        )
    }
}

@Composable
fun MetricCard(label: String, value: String) {
    Card(
        modifier = Modifier.size(120.dp, 80.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(text = label, style = MaterialTheme.typography.labelMedium)
            Text(text = value, style = MaterialTheme.typography.titleMedium.copy(fontFeatureSettings = "tnum", fontFamily = FontFamily.Monospace), fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun GForceGauge(longitudinalG: Double, lateralG: Double) {
    // Ring buffer for comet trail
    val trailPoints = remember { mutableStateListOf<Pair<Double, Double>>() }

    LaunchedEffect(longitudinalG, lateralG) {
        trailPoints.add(Pair(longitudinalG, lateralG))
        if (trailPoints.size > 30) {
            trailPoints.removeAt(0)
        }
    }

    Canvas(modifier = Modifier.fillMaxSize()) {
        val radius = size.minDimension / 2
        val maxG = 1.5 // Assuming max G for scale is 1.5G

        // Draw concentric circles
        for (i in 1..3) {
            drawCircle(
                color = Color.DarkGray.copy(alpha = 0.5f),
                radius = radius * (i / 3f),
                style = Stroke(width = 2f)
            )
        }

        // Draw crosshairs
        drawLine(
            color = Color.DarkGray.copy(alpha = 0.5f),
            start = Offset(center.x, 0f),
            end = Offset(center.x, size.height),
            strokeWidth = 2f
        )
        drawLine(
            color = Color.DarkGray.copy(alpha = 0.5f),
            start = Offset(0f, center.y),
            end = Offset(size.width, center.y),
            strokeWidth = 2f
        )

        // Draw comet trail
        trailPoints.forEachIndexed { index, point ->
            val alpha = (index + 1) / trailPoints.size.toFloat()
            val pointRadius = 10f * alpha
            val x = center.x + (((point.second / maxG).toFloat() * radius).takeIf { it.isFinite() } ?: 0f)
            val y = center.y - (((point.first / maxG).toFloat() * radius).takeIf { it.isFinite() } ?: 0f) // Invert Y so positive long G is up

            drawCircle(
                color = Color(0xFF00E5FF).copy(alpha = alpha),
                radius = pointRadius,
                center = Offset(x, y)
            )
        }

        // Draw current G-force point
        val currentX = center.x + (((lateralG / maxG).toFloat() * radius).takeIf { it.isFinite() } ?: 0f)
        val currentY = center.y - (((longitudinalG / maxG).toFloat() * radius).takeIf { it.isFinite() } ?: 0f)
        drawCircle(
            color = Color(0xFFFF2A54),
            radius = 12f,
            center = Offset(currentX, currentY)
        )
    }
}

@Composable
fun ApexRLayout(
    telemetryState: LiveTelemetryState,
    animatedLeanAngle: Float,
    animatedSpeed: Float,
    onResetPeaks: () -> Unit
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
        // Live Gauge Area
        Box(modifier = Modifier.size(300.dp), contentAlignment = Alignment.Center) {
            LeanAngleGauge(leanAngle = animatedLeanAngle.toDouble(), peakLeft = telemetryState.peakLeanLeftDeg, peakRight = telemetryState.peakLeanRightDeg)
            TextButton(onClick = onResetPeaks, modifier = Modifier.align(Alignment.BottomCenter).offset(y = 40.dp)) {
                Text(tr("Reset Peaks", "Vynulovať maximá"), fontSize = 12.sp)
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "${animatedSpeed.toInt()}",
                    fontSize = 48.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    style = LocalTextStyle.current.copy(fontFeatureSettings = "tnum")
                )
                Text(text = "km/h", fontSize = 16.sp)
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            MetricCard(tr("Avg Speed", "Priem. rýchlosť"), "${telemetryState.averageSpeedKmh.toInt()} km/h")
            MetricCard(tr("Corners", "Zákruty"), "${telemetryState.leftCorners}L | ${telemetryState.rightCorners}R")
        }
    }
}

@Composable
fun CircuitLayout(
    telemetryState: LiveTelemetryState,
    animatedLongitudinalG: Float,
    animatedLateralG: Float
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // GG Diagram on the left
            Box(modifier = Modifier.size(150.dp)) {
                GForceGauge(longitudinalG = animatedLongitudinalG.toDouble(), lateralG = animatedLateralG.toDouble())
            }

            // Metrics on the right
            Column(
                modifier = Modifier.padding(start = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                MetricCard(tr("Timer", "Časovač"), formatDuration(telemetryState.durationSeconds))
                MetricCard(tr("Turn-In °/s", "Nájazd °/s"), "${telemetryState.peakRollRateDegPerSec.toInt()} °/s")
                MetricCard(
                    tr("Corner Score", "Skóre zákruty"),
                    if (telemetryState.lastCornerScore > 0) "${telemetryState.lastCornerScore.toInt()} / 100" else "—"
                )
            }
        }
    }
}

fun formatDuration(seconds: Long): String {
    val h = seconds / 3600
    val m = (seconds % 3600) / 60
    val s = seconds % 60
    return if (h > 0) String.format("%d:%02d:%02d", h, m, s) else String.format("%02d:%02d", m, s)
}

@Composable
fun LabLayout(
    telemetryState: LiveTelemetryState
) {
    // Ring buffers for sparklines (approx 15 seconds at some sample rate, here we just keep last N for demo)
    val leanHistory = remember { mutableStateListOf<Float>() }
    val longGHistory = remember { mutableStateListOf<Float>() }
    val latGHistory = remember { mutableStateListOf<Float>() }

    LaunchedEffect(telemetryState.leanAngleDeg, telemetryState.longitudinalG, telemetryState.lateralG) {
        leanHistory.add(telemetryState.leanAngleDeg.toFloat())
        longGHistory.add(telemetryState.longitudinalG.toFloat())
        latGHistory.add(telemetryState.lateralG.toFloat())

        val maxPoints = 100 // Arbitrary demo limit
        if (leanHistory.size > maxPoints) leanHistory.removeAt(0)
        if (longGHistory.size > maxPoints) longGHistory.removeAt(0)
        if (latGHistory.size > maxPoints) latGHistory.removeAt(0)
    }

    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        SparklineChart(title = tr("Lean Angle (°)", "Uhol náklonu (°)"), data = leanHistory, color = Color(0xFF00E5FF), yRange = -60f..60f)
        SparklineChart(title = tr("Longitudinal G", "Pozdĺžne preťaženie G"), data = longGHistory, color = Color(0xFFFF2A54), yRange = -1.5f..1.5f)
        SparklineChart(title = tr("Lateral G", "Bočné preťaženie G"), data = latGHistory, color = Color(0xFFFFEA00), yRange = -1.5f..1.5f)
    }
}

@Composable
fun SparklineChart(title: String, data: List<Float>, color: Color, yRange: ClosedFloatingPointRange<Float>) {
    Column {
        Text(text = title, style = MaterialTheme.typography.labelSmall, color = Color.Gray)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(60.dp)
                .background(Color.DarkGray.copy(alpha = 0.2f))
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                if (data.isEmpty()) return@Canvas

                val width = size.width
                val height = size.height
                val pointWidth = width / 100f // maxPoints

                val path = Path()
                data.forEachIndexed { index, value ->
                    val rawNorm = (value - yRange.start) / (yRange.endInclusive - yRange.start)
                    val normalizedY = (1f - rawNorm).takeIf { it.isFinite() } ?: 0.5f
                    val x = (index * pointWidth).takeIf { it.isFinite() } ?: 0f
                    val y = (normalizedY * height).takeIf { it.isFinite() } ?: 0f

                    if (index == 0) {
                        path.moveTo(x, y)
                    } else {
                        path.lineTo(x, y)
                    }
                }

                drawPath(
                    path = path,
                    color = color,
                    style = Stroke(width = 3f)
                )
            }
        }
    }
}

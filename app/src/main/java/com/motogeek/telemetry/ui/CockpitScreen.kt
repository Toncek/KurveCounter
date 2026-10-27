package com.motogeek.telemetry.ui

import android.app.Activity
import android.content.res.Configuration
import android.view.WindowManager
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
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
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Left: Lean Gauge
            Box(modifier = Modifier.size(200.dp), contentAlignment = Alignment.Center) {
                LeanAngleGauge(leanAngle = animatedLeanAngle.toDouble(), peakLeft = telemetryState.peakLeanLeftDeg, peakRight = telemetryState.peakLeanRightDeg)
                TextButton(onClick = { viewModel.resetPeaks() }, modifier = Modifier.align(Alignment.BottomCenter).offset(y = 20.dp)) {
                    Text("Reset Peaks", fontSize = 10.sp)
                }
            }

            // Center: Speed & Corner Counter
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "${animatedSpeed.toInt()}",
                    fontSize = 64.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    style = LocalTextStyle.current.copy(fontFeatureSettings = "tnum")
                )
                Text(text = "km/h", fontSize = 16.sp)
                Spacer(modifier = Modifier.height(16.dp))
                MetricCard("Corners", "${telemetryState.leftCorners}L | ${telemetryState.rightCorners}R")
            }

            // Right: GG-Diagram + Controls
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(modifier = Modifier.size(150.dp)) {
                    GForceGauge(longitudinalG = animatedLongitudinalG.toDouble(), lateralG = animatedLateralG.toDouble())
                }
                Spacer(modifier = Modifier.height(16.dp))
                if (isRidingOrDemo) {
                    if (!isDemoMode && !telemetryState.isPaused) {
                        Button(
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                viewModel.pauseRide()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.tertiary),
                            modifier = Modifier.heightIn(min = 68.dp).fillMaxWidth()
                        ) {
                            Text("PAUSE RIDE", color = Color.Black)
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                    } else if (!isDemoMode && telemetryState.isPaused) {
                        Button(
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                viewModel.resumeRide()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00E676)),
                            modifier = Modifier.heightIn(min = 68.dp).fillMaxWidth()
                        ) {
                            Text("RESUME RIDE", color = Color.Black)
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                    }

                    Button(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            if (isDemoMode) viewModel.stopDemoMode() else onStopRide()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                        modifier = Modifier.heightIn(min = 68.dp).fillMaxWidth()
                    ) {
                        Text(if (isDemoMode) "STOP DEMO" else "STOP RIDE")
                    }
                } else {
                    Button(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onStartRide()
                        },
                        enabled = activeBike != null,
                        modifier = Modifier.heightIn(min = 68.dp).fillMaxWidth()
                    ) {
                        Text("START RIDE")
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    ElevatedFilterChip(
                        selected = false,
                        onClick = { viewModel.startDemoMode() },
                        label = { Text("Try Demo Ride", fontWeight = FontWeight.Bold) },
                        colors = FilterChipDefaults.elevatedFilterChipColors(
                            containerColor = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.2f),
                            labelColor = MaterialTheme.colorScheme.tertiary
                        )
                    )
                }
            }
        }
    } else {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Cockpit",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Layout Mode Segmented Bar
            var currentLayoutMode by remember { mutableStateOf(CockpitLayoutMode.APEX_R) }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .background(Color.DarkGray.copy(alpha = 0.3f), shape = MaterialTheme.shapes.large),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                CockpitLayoutMode.values().forEach { mode ->
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
                    label = { Text("Try Demo Ride (Simulator)", fontWeight = FontWeight.Bold) },
                    colors = FilterChipDefaults.elevatedFilterChipColors(
                        containerColor = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.2f),
                        labelColor = MaterialTheme.colorScheme.tertiary
                    )
                )
                Spacer(modifier = Modifier.height(16.dp))
            }

            // Calibration Badge
            Badge(
                containerColor = if (telemetryState.calibrationStatus == "LOCKED") Color(0xFF00E676) else Color(0xFFFFEA00)
            ) {
                Text(telemetryState.calibrationStatus, modifier = Modifier.padding(4.dp))
            }

            Spacer(modifier = Modifier.height(32.dp))

            if (telemetryState.isRiding && telemetryState.currentSpeedKmh < 1.0 && !isDemoMode) {
                Spacer(modifier = Modifier.height(8.dp))
                Badge(containerColor = MaterialTheme.colorScheme.tertiary) {
                    Text("AUTO-PAUSED", modifier = Modifier.padding(4.dp), color = Color.Black, fontWeight = FontWeight.Bold)
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
                            Text("PAUSE", color = Color.Black)
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
                            Text("RESUME", color = Color.Black)
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
                        Text(if (isDemoMode) "STOP" else "STOP")
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
                    Text("START RIDE")
                }
            }
            Spacer(modifier = Modifier.height(80.dp)) // padding for bottom nav
        }
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
        val currentX = center.x + (lateralG / maxG).toFloat() * radius
        val currentY = center.y - (longitudinalG / maxG).toFloat() * radius
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
                Text("Reset Peaks", fontSize = 12.sp)
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
            MetricCard("Avg Speed", "${telemetryState.averageSpeedKmh.toInt()} km/h")
            MetricCard("Corners", "${telemetryState.leftCorners}L | ${telemetryState.rightCorners}R")
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
                MetricCard("Timer", formatDuration(telemetryState.durationSeconds))
                MetricCard("Turn-In °/s", "N/A") // Simplified as requested
                MetricCard("Corner Score", "N/A") // Simplified as requested
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
        SparklineChart(title = "Lean Angle (°)", data = leanHistory, color = Color(0xFF00E5FF), yRange = -60f..60f)
        SparklineChart(title = "Longitudinal G", data = longGHistory, color = Color(0xFFFF2A54), yRange = -1.5f..1.5f)
        SparklineChart(title = "Lateral G", data = latGHistory, color = Color(0xFFFFEA00), yRange = -1.5f..1.5f)
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
                    // Normalize value to height
                    val normalizedY = 1f - ((value - yRange.start) / (yRange.endInclusive - yRange.start))
                    val x = index * pointWidth
                    val y = normalizedY * height

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

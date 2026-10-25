package com.motogeek.telemetry.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.motogeek.telemetry.service.LiveTelemetryState
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun CockpitScreen(viewModel: MainViewModel, onStartRide: () -> Unit, onStopRide: () -> Unit) {
    val telemetryState by viewModel.liveTelemetryState.collectAsState()
    val activeBike by viewModel.activeMotorcycle.collectAsState(initial = null)

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

        // Calibration Badge
        Badge(
            containerColor = if (telemetryState.calibrationStatus == "LOCKED") Color(0xFF00E676) else Color(0xFFFFEA00)
        ) {
            Text(telemetryState.calibrationStatus, modifier = Modifier.padding(4.dp))
        }

        Spacer(modifier = Modifier.height(32.dp))

        // Live Gauge Area
        Box(modifier = Modifier.size(300.dp), contentAlignment = Alignment.Center) {
            LeanAngleGauge(leanAngle = telemetryState.leanAngleDeg)
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "${telemetryState.currentSpeedKmh.toInt()}",
                    fontSize = 48.sp,
                    fontWeight = FontWeight.Bold
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

        Spacer(modifier = Modifier.weight(1f))

        if (telemetryState.isRiding) {
            Button(
                onClick = onStopRide,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("STOP RIDE")
            }
        } else {
            Button(
                onClick = onStartRide,
                enabled = activeBike != null,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("START RIDE")
            }
        }
        Spacer(modifier = Modifier.height(80.dp)) // padding for bottom nav
    }
}

@Composable
fun LeanAngleGauge(leanAngle: Double) {
    Canvas(modifier = Modifier.fillMaxSize()) {
        val sweepAngle = 110f
        val startAngle = 270f - (sweepAngle / 2f)

        // Background Arc
        drawArc(
            color = Color.DarkGray,
            startAngle = startAngle,
            sweepAngle = sweepAngle,
            useCenter = false,
            style = Stroke(width = 20f, cap = StrokeCap.Round)
        )

        // Lean Arc
        // -55 to 55 degrees
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
        val radius = size.minDimension / 2 - 20f

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
            Text(text = value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        }
    }
}

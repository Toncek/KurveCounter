package com.motogeek.telemetry.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.motogeek.telemetry.data.local.CornerEventEntity
import com.motogeek.telemetry.data.local.TelemetryTrackPointEntity
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

enum class HeatmapMode {
    LEAN_ANGLE,
    BRAKING_ACCEL,
    SPEED
}

@Composable
fun RideTrajectoryHeatmapCanvas(
    trackPoints: List<TelemetryTrackPointEntity>,
    cornerEvents: List<CornerEventEntity>,
    synchronizedTimestampMs: Long? = null,
    modifier: Modifier = Modifier
) {
    if (trackPoints.isEmpty()) {
        Box(modifier = modifier.fillMaxWidth().height(300.dp).background(Color.DarkGray), contentAlignment = Alignment.Center) {
            Text("No telemetry data available.", color = Color.White)
        }
        return
    }

    var selectedMode by remember { mutableStateOf(HeatmapMode.LEAN_ANGLE) }
    var selectedCorner by remember { mutableStateOf<CornerEventEntity?>(null) }

    Column(modifier = modifier.fillMaxWidth()) {
        // Toggle chips
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            FilterChip(
                selected = selectedMode == HeatmapMode.LEAN_ANGLE,
                onClick = { selectedMode = HeatmapMode.LEAN_ANGLE },
                label = { Text("Lean") }
            )
            FilterChip(
                selected = selectedMode == HeatmapMode.BRAKING_ACCEL,
                onClick = { selectedMode = HeatmapMode.BRAKING_ACCEL },
                label = { Text("G-Force") }
            )
            FilterChip(
                selected = selectedMode == HeatmapMode.SPEED,
                onClick = { selectedMode = HeatmapMode.SPEED },
                label = { Text("Speed") }
            )
        }

        Box(modifier = Modifier.fillMaxWidth().height(350.dp)) {
            val minLat = trackPoints.minOf { it.latitude }
            val maxLat = trackPoints.maxOf { it.latitude }
            val minLon = trackPoints.minOf { it.longitude }
            val maxLon = trackPoints.maxOf { it.longitude }

            val latDiff = maxLat - minLat
            val lonDiff = maxLon - minLon

            val maxSpeed = trackPoints.maxOfOrNull { it.speedKmh } ?: 1.0

            // Map bounds to canvas keeping aspect ratio

            var canvasWidth = 1f
            var canvasHeight = 1f
            var cornerPinOffsets = remember { mutableStateListOf<Pair<CornerEventEntity, Offset>>() }

            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFF121212))
                    .pointerInput(Unit) {
                        detectTapGestures { tapOffset ->
                            // Find closest corner pin
                            val threshold = 50f
                            val closest = cornerPinOffsets.minByOrNull { (it.second - tapOffset).getDistance() }
                            if (closest != null && (closest.second - tapOffset).getDistance() < threshold) {
                                selectedCorner = closest.first
                            } else {
                                selectedCorner = null
                            }
                        }
                    }
            ) {
                canvasWidth = size.width
                canvasHeight = size.height

                val padding = 40.dp.toPx()
                val usableWidth = canvasWidth - padding * 2
                val usableHeight = canvasHeight - padding * 2

                // Calculate scale to fit while preserving aspect ratio
                // Since this is generic, we use a simple projection
                val scaleX = if (lonDiff > 0) (usableWidth / lonDiff.toFloat()).takeIf { it.isFinite() } ?: 1f else 1f
                val scaleY = if (latDiff > 0) (usableHeight / latDiff.toFloat()).takeIf { it.isFinite() } ?: 1f else 1f
                val scale = min(scaleX, scaleY)

                val xOffset = (usableWidth - lonDiff.toFloat() * scale) / 2f + padding
                val yOffset = (usableHeight - latDiff.toFloat() * scale) / 2f + padding

                fun getOffset(lat: Double, lon: Double): Offset {
                    // latitude increases going up (north), but canvas Y increases going down
                    val x = (lon - minLon).toFloat() * scale + xOffset
                    val y = (maxLat - lat).toFloat() * scale + yOffset
                    return Offset(x, y)
                }

                // Draw trajectory segments
                for (i in 0 until trackPoints.size - 1) {
                    val p1 = trackPoints[i]
                    val p2 = trackPoints[i + 1]

                    val color = when (selectedMode) {
                        HeatmapMode.LEAN_ANGLE -> {
                            val lean = abs(p1.leanAngleDeg)
                            when {
                                lean < 10 -> Color.Green
                                lean < 30 -> Color.Yellow
                                lean < 45 -> Color(0xFFFFA500) // Orange
                                else -> Color.Red
                            }
                        }
                        HeatmapMode.BRAKING_ACCEL -> {
                            val longG = p1.longitudinalG
                            when {
                                longG < -0.15 -> Color.Red // Braking
                                longG > 0.15 -> Color.Green // Accel
                                else -> Color.Blue // Coasting
                            }
                        }
                        HeatmapMode.SPEED -> {
                            val speedRatio = p1.speedKmh / maxSpeed
                            // Cyan to Magenta
                            Color(
                                red = speedRatio.toFloat(),
                                green = (1f - speedRatio).toFloat(),
                                blue = 1f
                            )
                        }
                    }

                    drawLine(
                        color = color,
                        start = getOffset(p1.latitude, p1.longitude),
                        end = getOffset(p2.latitude, p2.longitude),
                        strokeWidth = 6f,
                        cap = StrokeCap.Round
                    )
                }

                // Draw synced marker
                synchronizedTimestampMs?.let { syncTime ->
                    val closestPoint = trackPoints.minByOrNull { abs(it.timestampMs - syncTime) }
                    if (closestPoint != null) {
                        val offset = getOffset(closestPoint.latitude, closestPoint.longitude)
                        drawCircle(
                            color = Color.White,
                            radius = 12f,
                            center = offset
                        )
                        drawCircle(
                            color = Color.Cyan,
                            radius = 8f,
                            center = offset
                        )
                    }
                }

                // Draw Corner Pins
                cornerPinOffsets.clear()
                cornerEvents.forEachIndexed { index, corner ->
                    val offset = getOffset(corner.latitude, corner.longitude)
                    cornerPinOffsets.add(corner to offset)

                    val isSelected = selectedCorner == corner

                    drawCircle(
                        color = if (isSelected) Color.White else Color.Red,
                        radius = if (isSelected) 18f else 12f,
                        center = offset
                    )
                    drawCircle(
                        color = if (isSelected) Color.Red else Color.White,
                        radius = if (isSelected) 14f else 8f,
                        center = offset
                    )
                }
            }

            // Floating HUD for selected corner
            selectedCorner?.let { corner ->
                Card(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(8.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.Black.copy(alpha = 0.8f))
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text("Turn ${corner.sequenceNumber} (${corner.direction})", color = Color.White, fontWeight = FontWeight.Bold)
                        Text("Entry: ${String.format("%.1f", corner.entrySpeedKmh)} km/h", color = Color.LightGray, fontSize = 12.sp)
                        Text("Apex Lean: ${String.format("%.1f", corner.apexLeanDeg)}°", color = Color.LightGray, fontSize = 12.sp)
                        Text("Peak G: ${String.format("%.2f", corner.peakLateralG)}g", color = Color.LightGray, fontSize = 12.sp)
                        Text("Roll Rate: ${String.format("%.1f", corner.maxRollRateDegPerSec)}°/s", color = Color.LightGray, fontSize = 12.sp)
                        Text("Score: ${String.format("%.0f", corner.cornerScore)}", color = Color.Yellow, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    }
                }
            }
        }
    }
}

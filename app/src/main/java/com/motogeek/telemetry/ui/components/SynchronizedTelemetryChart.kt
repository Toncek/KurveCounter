package com.motogeek.telemetry.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.motogeek.telemetry.data.local.TelemetryTrackPointEntity
import kotlin.math.max

@Composable
fun SynchronizedTelemetryChart(
    trackPoints: List<TelemetryTrackPointEntity>,
    onScrub: (Long?) -> Unit,
    modifier: Modifier = Modifier
) {
    if (trackPoints.isEmpty()) {
        Box(modifier = modifier.fillMaxWidth().height(200.dp).background(Color.DarkGray), contentAlignment = Alignment.Center) {
            Text("No telemetry data to chart.", color = Color.White)
        }
        return
    }

    var scrubX by remember { mutableStateOf<Float?>(null) }
    var scrubbedPoint by remember { mutableStateOf<TelemetryTrackPointEntity?>(null) }

    Column(modifier = modifier.fillMaxWidth()) {
        Box(modifier = Modifier.fillMaxWidth().height(200.dp)) {
            val minTime = trackPoints.first().timestampMs
            val maxTime = trackPoints.last().timestampMs
            val duration = maxTime - minTime

            val maxSpeed = trackPoints.maxOfOrNull { it.speedKmh }?.takeIf { it > 0 } ?: 1.0
            val maxLean = trackPoints.maxOfOrNull { kotlin.math.abs(it.leanAngleDeg) }?.takeIf { it > 0 } ?: 1.0
            val maxG = trackPoints.maxOfOrNull { kotlin.math.max(kotlin.math.abs(it.lateralG), kotlin.math.abs(it.longitudinalG)) }?.takeIf { it > 0 } ?: 1.0
            val maxAlt = trackPoints.maxOfOrNull { it.altitudeMeters }?.takeIf { it > 0 } ?: 1.0

            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFF1E1E1E))
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onTap = { offset ->
                                scrubX = offset.x
                                updateScrub(offset.x, size.width, minTime, duration, trackPoints, onScrub) { p -> scrubbedPoint = p }
                            },
                            onPress = { offset ->
                                scrubX = offset.x
                                updateScrub(offset.x, size.width, minTime, duration, trackPoints, onScrub) { p -> scrubbedPoint = p }
                                tryAwaitRelease()
                                scrubX = null
                                scrubbedPoint = null
                                onScrub(null)
                            }
                        )
                    }
                    .pointerInput(Unit) {
                        detectHorizontalDragGestures(
                            onDragStart = { offset ->
                                scrubX = offset.x
                                updateScrub(offset.x, size.width, minTime, duration, trackPoints, onScrub) { p -> scrubbedPoint = p }
                            },
                            onDragEnd = {
                                scrubX = null
                                scrubbedPoint = null
                                onScrub(null)
                            },
                            onDragCancel = {
                                scrubX = null
                                scrubbedPoint = null
                                onScrub(null)
                            },
                            onHorizontalDrag = { change, _ ->
                                scrubX = change.position.x
                                updateScrub(change.position.x, size.width, minTime, duration, trackPoints, onScrub) { p -> scrubbedPoint = p }
                            }
                        )
                    }
            ) {
                val width = size.width
                val height = size.height

                fun getX(timestampMs: Long): Float {
                    return if (duration > 0) (((timestampMs - minTime).toFloat() / duration.toFloat()) * width).takeIf { it.isFinite() } ?: 0f else 0f
                }

                fun getY(value: Double, maxVal: Double): Float {
                    val safeMax = if (maxVal > 0) maxVal else 1.0
                    val normalized = (value / safeMax).coerceIn(-1.0, 1.0).toFloat().takeIf { it.isFinite() } ?: 0f
                    val adjusted = (normalized + 1f) / 2f
                    return (height - (adjusted * height)).takeIf { it.isFinite() } ?: (height / 2f)
                }

                fun getPositiveY(value: Double, maxVal: Double): Float {
                    val safeMax = if (maxVal > 0) maxVal else 1.0
                    val normalized = (value / safeMax).coerceIn(0.0, 1.0).toFloat().takeIf { it.isFinite() } ?: 0f
                    return (height - (normalized * height)).takeIf { it.isFinite() } ?: height
                }

                val speedPath = Path()
                val leanPath = Path()
                val latGPath = Path()
                val longGPath = Path()
                val altPath = Path()

                trackPoints.forEachIndexed { index, point ->
                    val x = getX(point.timestampMs)
                    val speedY = getPositiveY(point.speedKmh, maxSpeed)
                    val leanY = getY(point.leanAngleDeg, maxLean)
                    val latGY = getY(point.lateralG, maxG)
                    val longGY = getY(point.longitudinalG, maxG)
                    val altY = getPositiveY(point.altitudeMeters, maxAlt)

                    if (index == 0) {
                        speedPath.moveTo(x, speedY)
                        leanPath.moveTo(x, leanY)
                        latGPath.moveTo(x, latGY)
                        longGPath.moveTo(x, longGY)
                        altPath.moveTo(x, altY)
                    } else {
                        speedPath.lineTo(x, speedY)
                        leanPath.lineTo(x, leanY)
                        latGPath.lineTo(x, latGY)
                        longGPath.lineTo(x, longGY)
                        altPath.lineTo(x, altY)
                    }
                }

                drawPath(path = speedPath, color = Color.Cyan, style = Stroke(width = 3f))
                drawPath(path = leanPath, color = Color.Yellow, style = Stroke(width = 3f))
                drawPath(path = latGPath, color = Color.Red, style = Stroke(width = 2f))
                drawPath(path = longGPath, color = Color.Green, style = Stroke(width = 2f))
                drawPath(path = altPath, color = Color.White.copy(alpha = 0.5f), style = Stroke(width = 1f))

                // Draw scrub line
                scrubX?.let { x ->
                    drawLine(
                        color = Color.White,
                        start = Offset(x, 0f),
                        end = Offset(x, height),
                        strokeWidth = 2f
                    )
                }
            }

            // Scrub details HUD
            scrubbedPoint?.let { point ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color.Black.copy(alpha = 0.7f))
                        .padding(4.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    Text("Speed: ${String.format("%.0f", point.speedKmh)}", color = Color.Cyan, fontSize = 12.sp)
                    Text("Lean: ${String.format("%.0f", point.leanAngleDeg)}°", color = Color.Yellow, fontSize = 12.sp)
                    Text("Lat G: ${String.format("%.2f", point.lateralG)}", color = Color.Red, fontSize = 12.sp)
                    Text("Long G: ${String.format("%.2f", point.longitudinalG)}", color = Color.Green, fontSize = 12.sp)
                    Text("Alt: ${String.format("%.0f", point.altitudeMeters)}m", color = Color.White, fontSize = 12.sp)
                }
            }
        }
    }
}

private fun updateScrub(
    x: Float,
    width: Int,
    minTime: Long,
    duration: Long,
    trackPoints: List<TelemetryTrackPointEntity>,
    onScrub: (Long?) -> Unit,
    onPointFound: (TelemetryTrackPointEntity) -> Unit
) {
    if (width <= 0 || duration <= 0) return
    val ratio = (x / width.toFloat()).coerceIn(0f, 1f)
    val targetTime = minTime + (ratio * duration).toLong()
    val closest = trackPoints.minByOrNull { kotlin.math.abs(it.timestampMs - targetTime) }
    if (closest != null) {
        onScrub(closest.timestampMs)
        onPointFound(closest)
    }
}

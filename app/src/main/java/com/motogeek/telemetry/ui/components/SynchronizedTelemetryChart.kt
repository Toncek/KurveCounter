package com.motogeek.telemetry.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.motogeek.telemetry.data.local.TelemetryTrackPointEntity
import com.motogeek.telemetry.ui.theme.ApexEmeraldGreen
import com.motogeek.telemetry.ui.theme.CarbonObsidianBg
import com.motogeek.telemetry.ui.theme.RedlineCrimson
import com.motogeek.telemetry.ui.theme.TelemetryIceCyan
import com.motogeek.telemetry.ui.theme.TftSurfaceDark
import com.motogeek.telemetry.ui.theme.WarningGold
import com.motogeek.telemetry.ui.theme.tr
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max

private val AltitudePurple = Color(0xFFB388FF)

@Composable
fun SynchronizedTelemetryChart(
    trackPoints: List<TelemetryTrackPointEntity>,
    scrubbedTimestampMs: Long?,
    onScrub: (Long?) -> Unit,
    modifier: Modifier = Modifier
) {
    val validPoints = remember(trackPoints) {
        trackPoints.filter { it.timestampMs > 0 && it.speedKmh.isFinite() }
    }

    if (validPoints.isEmpty()) {
        Box(
            modifier = modifier
                .fillMaxWidth()
                .height(200.dp)
                .background(TftSurfaceDark, RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = tr("No telemetry data to chart.", "Nie sú k dispozícii žiadne telemetrické údaje pre graf."),
                color = Color.LightGray,
                style = MaterialTheme.typography.bodyMedium
            )
        }
        return
    }

    var showSpeed by remember { mutableStateOf(true) }
    var showLean by remember { mutableStateOf(true) }
    var showGForce by remember { mutableStateOf(true) }
    var showElev by remember { mutableStateOf(true) }

    var localScrubTimestamp by remember { mutableStateOf<Long?>(null) }
    val activeTimestamp = scrubbedTimestampMs ?: localScrubTimestamp

    val minTime = validPoints.first().timestampMs
    val maxTime = validPoints.last().timestampMs
    val duration = (maxTime - minTime).coerceAtLeast(1L)

    val maxSpeed = remember(validPoints) {
        validPoints.maxOfOrNull { it.speedKmh }?.takeIf { it.isFinite() && it > 0 } ?: 1.0
    }
    val maxLean = remember(validPoints) {
        validPoints.maxOfOrNull { abs(it.leanAngleDeg) }?.takeIf { it.isFinite() && it > 0 } ?: 1.0
    }
    val maxG = remember(validPoints) {
        validPoints.maxOfOrNull { max(abs(it.lateralG), abs(it.longitudinalG)) }?.takeIf { it.isFinite() && it > 0 } ?: 1.0
    }
    val minAlt = remember(validPoints) {
        validPoints.minOfOrNull { it.altitudeMeters }?.takeIf { it.isFinite() } ?: 0.0
    }
    val maxAlt = remember(validPoints) {
        validPoints.maxOfOrNull { it.altitudeMeters }?.takeIf { it.isFinite() && it > minAlt } ?: (minAlt + 10.0)
    }
    val altSpan = (maxAlt - minAlt).coerceAtLeast(1.0)

    val scrubbedPoint = remember(activeTimestamp, validPoints) {
        activeTimestamp?.let { t ->
            validPoints.minByOrNull { abs(it.timestampMs - t) }
        }
    }

    val totalDurationSec = duration / 1000
    val totalMin = totalDurationSec / 60
    val totalSec = totalDurationSec % 60
    val durationLabel = String.format(Locale.US, "%02d:%02d", totalMin, totalSec)

    Column(modifier = modifier.fillMaxWidth()) {
        // Channel Toggle Chips
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally)
        ) {
            FilterChip(
                selected = showSpeed,
                onClick = { showSpeed = !showSpeed },
                label = { Text(tr("Speed", "Rýchlosť"), fontSize = 11.sp) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = TelemetryIceCyan.copy(alpha = 0.25f),
                    selectedLabelColor = TelemetryIceCyan
                )
            )
            FilterChip(
                selected = showLean,
                onClick = { showLean = !showLean },
                label = { Text(tr("Lean", "Náklon"), fontSize = 11.sp) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = WarningGold.copy(alpha = 0.25f),
                    selectedLabelColor = WarningGold
                )
            )
            FilterChip(
                selected = showGForce,
                onClick = { showGForce = !showGForce },
                label = { Text(tr("G-Force", "Preťaženie"), fontSize = 11.sp) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = RedlineCrimson.copy(alpha = 0.25f),
                    selectedLabelColor = RedlineCrimson
                )
            )
            FilterChip(
                selected = showElev,
                onClick = { showElev = !showElev },
                label = { Text(tr("Elev", "Výška"), fontSize = 11.sp) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = AltitudePurple.copy(alpha = 0.25f),
                    selectedLabelColor = AltitudePurple
                )
            )
        }

        // Live Scrub Readout HUD / Legend Strip
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 4.dp),
            color = CarbonObsidianBg,
            shape = RoundedCornerShape(8.dp)
        ) {
            if (scrubbedPoint != null) {
                val offsetMs = (scrubbedPoint.timestampMs - minTime).coerceAtLeast(0L)
                val scrubSec = offsetMs / 1000
                val scrubMin = scrubSec / 60
                val scrubRemainderSec = scrubSec % 60
                val timeOffsetStr = String.format(Locale.US, "+%02d:%02d", scrubMin, scrubRemainderSec)

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(timeOffsetStr, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 11.sp)

                    if (showSpeed) {
                        Text(
                            "${String.format(Locale.US, "%.0f", scrubbedPoint.speedKmh)} km/h",
                            color = TelemetryIceCyan,
                            fontSize = 11.sp
                        )
                    }
                    if (showLean) {
                        Text(
                            "${String.format(Locale.US, "%.0f", scrubbedPoint.leanAngleDeg)}°",
                            color = WarningGold,
                            fontSize = 11.sp
                        )
                    }
                    if (showGForce) {
                        Text(
                            "L:${String.format(Locale.US, "%.2f", scrubbedPoint.lateralG)}g",
                            color = RedlineCrimson,
                            fontSize = 11.sp
                        )
                        Text(
                            "A:${String.format(Locale.US, "%.2f", scrubbedPoint.longitudinalG)}g",
                            color = ApexEmeraldGreen,
                            fontSize = 11.sp
                        )
                    }
                    if (showElev) {
                        Text(
                            "${String.format(Locale.US, "%.0f", scrubbedPoint.altitudeMeters)}m",
                            color = AltitudePurple,
                            fontSize = 11.sp
                        )
                    }

                    IconButton(
                        onClick = {
                            localScrubTimestamp = null
                            onScrub(null)
                        },
                        modifier = Modifier.size(18.dp)
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Clear", tint = Color.LightGray)
                    }
                }
            } else {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = tr("Tap or scrub chart to analyze synchronized telemetry", "Kliknite alebo potiahnite graf pre synchronizovanú analýzu"),
                        color = Color.Gray,
                        fontSize = 11.sp
                    )
                }
            }
        }

        // Chart Canvas Box
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp)
                .background(TftSurfaceDark, RoundedCornerShape(10.dp))
        ) {
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(minTime, duration, validPoints) {
                        fun handleScrub(touchX: Float, width: Int) {
                            if (width <= 0 || duration <= 0) return
                            val ratio = (touchX / width.toFloat()).coerceIn(0f, 1f)
                            val targetTime = minTime + (ratio * duration).toLong()
                            val closest = validPoints.minByOrNull { abs(it.timestampMs - targetTime) }
                            if (closest != null) {
                                localScrubTimestamp = closest.timestampMs
                                onScrub(closest.timestampMs)
                            }
                        }

                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            handleScrub(down.position.x, size.width)

                            while (true) {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                if (!change.pressed) {
                                    break
                                }
                                handleScrub(change.position.x, size.width)
                                change.consume()
                            }
                        }
                    }
            ) {
                val width = size.width
                val height = size.height

                fun getX(timestampMs: Long): Float {
                    if (duration <= 0) return 0f
                    val ratio = ((timestampMs - minTime).toFloat() / duration.toFloat()).coerceIn(0f, 1f)
                    return ratio * width
                }

                fun getYCenterBipolar(value: Double, maxVal: Double): Float {
                    val safeMax = if (maxVal > 0) maxVal else 1.0
                    val normalized = (value / safeMax).coerceIn(-1.0, 1.0).toFloat().takeIf { it.isFinite() } ?: 0f
                    val adjusted = (normalized + 1f) / 2f
                    return (height - (adjusted * height)).coerceIn(0f, height)
                }

                fun getYBottomUnipolar(value: Double, maxVal: Double): Float {
                    val safeMax = if (maxVal > 0) maxVal else 1.0
                    val normalized = (value / safeMax).coerceIn(0.0, 1.0).toFloat().takeIf { it.isFinite() } ?: 0f
                    return (height - (normalized * height)).coerceIn(0f, height)
                }

                // 1. Draw Zero Baselines
                // Bipolar zero line (center)
                val midY = height / 2f
                drawLine(
                    color = Color.White.copy(alpha = 0.2f),
                    start = Offset(0f, midY),
                    end = Offset(width, midY),
                    strokeWidth = 1f,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f)
                )

                // 2. Build paths
                val speedPath = Path()
                val leanPath = Path()
                val latGPath = Path()
                val longGPath = Path()
                val altPath = Path()

                validPoints.forEachIndexed { index, point ->
                    val x = getX(point.timestampMs)
                    val speedY = getYBottomUnipolar(point.speedKmh, maxSpeed)
                    val leanY = getYCenterBipolar(point.leanAngleDeg, maxLean)
                    val latGY = getYCenterBipolar(point.lateralG, maxG)
                    val longGY = getYCenterBipolar(point.longitudinalG, maxG)
                    val altY = height - (((point.altitudeMeters - minAlt) / altSpan).toFloat().coerceIn(0f, 1f) * height)

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

                // 3. Draw enabled paths
                if (showElev) {
                    drawPath(path = altPath, color = AltitudePurple.copy(alpha = 0.5f), style = Stroke(width = 1.5f))
                }
                if (showGForce) {
                    drawPath(path = longGPath, color = ApexEmeraldGreen.copy(alpha = 0.85f), style = Stroke(width = 2f))
                    drawPath(path = latGPath, color = RedlineCrimson.copy(alpha = 0.85f), style = Stroke(width = 2f))
                }
                if (showLean) {
                    drawPath(path = leanPath, color = WarningGold, style = Stroke(width = 2.5f))
                }
                if (showSpeed) {
                    drawPath(path = speedPath, color = TelemetryIceCyan, style = Stroke(width = 2.5f))
                }

                // 4. Draw vertical scrub line and curve intersection dots
                if (activeTimestamp != null) {
                    val scrubX = getX(activeTimestamp)
                    drawLine(
                        color = Color.White,
                        start = Offset(scrubX, 0f),
                        end = Offset(scrubX, height),
                        strokeWidth = 2f
                    )

                    scrubbedPoint?.let { sp ->
                        if (showSpeed) {
                            val sy = getYBottomUnipolar(sp.speedKmh, maxSpeed)
                            drawCircle(color = TelemetryIceCyan, radius = 4f, center = Offset(scrubX, sy))
                        }
                        if (showLean) {
                            val ly = getYCenterBipolar(sp.leanAngleDeg, maxLean)
                            drawCircle(color = WarningGold, radius = 4f, center = Offset(scrubX, ly))
                        }
                        if (showGForce) {
                            val latY = getYCenterBipolar(sp.lateralG, maxG)
                            val longY = getYCenterBipolar(sp.longitudinalG, maxG)
                            drawCircle(color = RedlineCrimson, radius = 3.5f, center = Offset(scrubX, latY))
                            drawCircle(color = ApexEmeraldGreen, radius = 3.5f, center = Offset(scrubX, longY))
                        }
                        if (showElev) {
                            val ey = height - (((sp.altitudeMeters - minAlt) / altSpan).toFloat().coerceIn(0f, 1f) * height)
                            drawCircle(color = AltitudePurple, radius = 3.5f, center = Offset(scrubX, ey))
                        }
                    }
                }
            }
        }

        // Bottom Axis Labels: Duration 00:00 -> End Time
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("00:00", color = Color.Gray, fontSize = 10.sp)
            Text(
                "${tr("Duration", "Trvanie")}: $durationLabel",
                color = Color.LightGray.copy(alpha = 0.6f),
                fontSize = 10.sp
            )
            Text(durationLabel, color = Color.Gray, fontSize = 10.sp)
        }
    }
}

@Composable
fun SynchronizedTelemetryChart(
    trackPoints: List<TelemetryTrackPointEntity>,
    onScrub: (Long?) -> Unit,
    modifier: Modifier = Modifier
) = SynchronizedTelemetryChart(
    trackPoints = trackPoints,
    scrubbedTimestampMs = null,
    onScrub = onScrub,
    modifier = modifier
)

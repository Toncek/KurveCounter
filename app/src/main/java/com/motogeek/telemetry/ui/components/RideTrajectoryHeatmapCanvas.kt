package com.motogeek.telemetry.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.motogeek.telemetry.data.local.CornerEventEntity
import com.motogeek.telemetry.data.local.TelemetryTrackPointEntity
import com.motogeek.telemetry.ui.theme.ApexEmeraldGreen
import com.motogeek.telemetry.ui.theme.CarbonObsidianBg
import com.motogeek.telemetry.ui.theme.RacingAmberPrimary
import com.motogeek.telemetry.ui.theme.RedlineCrimson
import com.motogeek.telemetry.ui.theme.TelemetryIceCyan
import com.motogeek.telemetry.ui.theme.TftSurfaceDark
import com.motogeek.telemetry.ui.theme.TitaniumBorder
import com.motogeek.telemetry.ui.theme.WarningGold
import com.motogeek.telemetry.ui.theme.tr
import java.util.Locale
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min

enum class HeatmapMode {
    LEAN_ANGLE,
    BRAKING_ACCEL,
    SPEED
}

private data class ResolvedCorner(
    val corner: CornerEventEntity,
    val latitude: Double,
    val longitude: Double
)

@Composable
fun RideTrajectoryHeatmapCanvas(
    trackPoints: List<TelemetryTrackPointEntity>,
    cornerEvents: List<CornerEventEntity>,
    selectedTimestampMs: Long? = null,
    onPointSelected: ((Long?) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val validTrackPoints = remember(trackPoints) {
        trackPoints.filter { p ->
            p.latitude.isFinite() && p.longitude.isFinite() &&
                !(abs(p.latitude) < 1e-6 && abs(p.longitude) < 1e-6)
        }
    }

    if (validTrackPoints.isEmpty()) {
        Box(
            modifier = modifier
                .fillMaxWidth()
                .height(350.dp)
                .background(TftSurfaceDark, RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = tr("No GPS trajectory data available.", "Nie sú k dispozícii žiadne GPS údaje."),
                color = Color.LightGray,
                style = MaterialTheme.typography.bodyMedium
            )
        }
        return
    }

    var selectedMode by remember { mutableStateOf(HeatmapMode.LEAN_ANGLE) }
    var selectedCorner by remember { mutableStateOf<CornerEventEntity?>(null) }
    var zoom by remember { mutableFloatStateOf(1f) }
    var pan by remember { mutableStateOf(Offset.Zero) }
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }

    val resolvedCorners = remember(cornerEvents, validTrackPoints) {
        cornerEvents.mapNotNull { corner ->
            val hasValidCoords = corner.latitude.isFinite() && corner.longitude.isFinite() &&
                !(abs(corner.latitude) < 1e-6 && abs(corner.longitude) < 1e-6)
            if (hasValidCoords) {
                ResolvedCorner(corner, corner.latitude, corner.longitude)
            } else {
                val nearest = validTrackPoints.minByOrNull { abs(it.timestampMs - corner.timestampMs) }
                if (nearest != null) {
                    ResolvedCorner(corner, nearest.latitude, nearest.longitude)
                } else {
                    null
                }
            }
        }
    }

    val minLat = remember(validTrackPoints) { validTrackPoints.minOf { it.latitude } }
    val maxLat = remember(validTrackPoints) { validTrackPoints.maxOf { it.latitude } }
    val minLon = remember(validTrackPoints) { validTrackPoints.minOf { it.longitude } }
    val maxLon = remember(validTrackPoints) { validTrackPoints.maxOf { it.longitude } }

    val avgLat = (minLat + maxLat) / 2.0
    val cosLat = cos(Math.toRadians(avgLat)).coerceAtLeast(0.2)

    val latSpan = (maxLat - minLat).coerceAtLeast(1e-7)
    val lonSpan = ((maxLon - minLon) * cosLat).coerceAtLeast(1e-7)

    val maxSpeed = remember(validTrackPoints) {
        validTrackPoints.maxOfOrNull { it.speedKmh }?.takeIf { it.isFinite() && it > 0.0 } ?: 1.0
    }

    val peakLeanPoint = remember(validTrackPoints) {
        validTrackPoints.maxByOrNull { abs(it.leanAngleDeg) }
    }

    val paddingPx = 48f
    val usableWidth = (canvasSize.width - paddingPx * 2).coerceAtLeast(1f)
    val usableHeight = (canvasSize.height - paddingPx * 2).coerceAtLeast(1f)

    val scaleX = usableWidth / lonSpan.toFloat()
    val scaleY = usableHeight / latSpan.toFloat()
    val baseScale = min(scaleX, scaleY).takeIf { it.isFinite() && it > 0 } ?: 1f

    val baseXOffset = (usableWidth - lonSpan.toFloat() * baseScale) / 2f + paddingPx
    val baseYOffset = (usableHeight - latSpan.toFloat() * baseScale) / 2f + paddingPx

    val centerX = canvasSize.width / 2f
    val centerY = canvasSize.height / 2f

    fun toScreenOffset(lat: Double, lon: Double): Offset {
        val normX = ((lon - minLon) * cosLat).toFloat() * baseScale + baseXOffset
        val normY = (maxLat - lat).toFloat() * baseScale + baseYOffset
        val x = (normX - centerX) * zoom + centerX + pan.x
        val y = (normY - centerY) * zoom + centerY + pan.y
        return Offset(x, y)
    }

    val cornerPinOffsets = remember(
        resolvedCorners, canvasSize, zoom, pan,
        minLat, maxLat, minLon, cosLat, baseScale, baseXOffset, baseYOffset, centerX, centerY
    ) {
        if (canvasSize.width <= 0 || canvasSize.height <= 0) {
            emptyList()
        } else {
            resolvedCorners.map { rc ->
                rc to toScreenOffset(rc.latitude, rc.longitude)
            }
        }
    }

    val isZoomed = zoom > 1.05f || pan != Offset.Zero

    Column(modifier = modifier.fillMaxWidth()) {
        // Mode Selector Chips
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)
        ) {
            FilterChip(
                selected = selectedMode == HeatmapMode.LEAN_ANGLE,
                onClick = { selectedMode = HeatmapMode.LEAN_ANGLE },
                label = { Text(tr("Lean", "Náklon")) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = WarningGold.copy(alpha = 0.25f),
                    selectedLabelColor = WarningGold
                )
            )
            FilterChip(
                selected = selectedMode == HeatmapMode.BRAKING_ACCEL,
                onClick = { selectedMode = HeatmapMode.BRAKING_ACCEL },
                label = { Text(tr("G-Force", "Preťaženie")) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = ApexEmeraldGreen.copy(alpha = 0.25f),
                    selectedLabelColor = ApexEmeraldGreen
                )
            )
            FilterChip(
                selected = selectedMode == HeatmapMode.SPEED,
                onClick = { selectedMode = HeatmapMode.SPEED },
                label = { Text(tr("Speed", "Rýchlosť")) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = TelemetryIceCyan.copy(alpha = 0.25f),
                    selectedLabelColor = TelemetryIceCyan
                )
            )
        }

        // Map Canvas Box
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(360.dp)
                .background(CarbonObsidianBg, RoundedCornerShape(12.dp))
        ) {
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .onSizeChanged { canvasSize = it }
                    .pointerInput(Unit) {
                        detectTapGestures { tapOffset ->
                            // Corner pin hit test
                            val cornerThreshold = 50f
                            val closestCorner = cornerPinOffsets.minByOrNull { (_, off) -> (off - tapOffset).getDistance() }
                            if (closestCorner != null && (closestCorner.second - tapOffset).getDistance() < cornerThreshold) {
                                selectedCorner = closestCorner.first.corner
                                onPointSelected?.invoke(closestCorner.first.corner.timestampMs)
                                return@detectTapGestures
                            }

                            selectedCorner = null

                            // Route trackpoint hit test
                            val routeThreshold = 65f
                            var bestPoint: TelemetryTrackPointEntity? = null
                            var bestDist = Float.MAX_VALUE
                            for (pt in validTrackPoints) {
                                val ptOffset = toScreenOffset(pt.latitude, pt.longitude)
                                val dist = (ptOffset - tapOffset).getDistance()
                                if (dist < bestDist) {
                                    bestDist = dist
                                    bestPoint = pt
                                }
                            }

                            if (bestPoint != null && bestDist < routeThreshold) {
                                onPointSelected?.invoke(bestPoint.timestampMs)
                            } else {
                                onPointSelected?.invoke(null)
                            }
                        }
                    }
                    .pointerInput(Unit) {
                        detectTransformGestures { _, panDelta, zoomDelta, _ ->
                            val newZoom = (zoom * zoomDelta).coerceIn(1f, 10f)
                            zoom = newZoom
                            if (newZoom <= 1.001f) {
                                pan = Offset.Zero
                            } else {
                                pan += panDelta
                            }
                        }
                    }
            ) {
                if (validTrackPoints.size == 1) {
                    val singleCenter = toScreenOffset(validTrackPoints[0].latitude, validTrackPoints[0].longitude)
                    drawCircle(color = ApexEmeraldGreen, radius = 16f, center = singleCenter)
                    drawCircle(color = Color.White, radius = 8f, center = singleCenter)
                    return@Canvas
                }

                // 1. Draw dark casing halo under track segments for high contrast
                for (i in 0 until validTrackPoints.size - 1) {
                    val p1 = validTrackPoints[i]
                    val p2 = validTrackPoints[i + 1]
                    drawLine(
                        color = Color.Black,
                        start = toScreenOffset(p1.latitude, p1.longitude),
                        end = toScreenOffset(p2.latitude, p2.longitude),
                        strokeWidth = 11f,
                        cap = StrokeCap.Round
                    )
                }

                // 2. Draw colored heatmap segments
                for (i in 0 until validTrackPoints.size - 1) {
                    val p1 = validTrackPoints[i]
                    val p2 = validTrackPoints[i + 1]

                    val color = when (selectedMode) {
                        HeatmapMode.LEAN_ANGLE -> {
                            val lean = abs(p1.leanAngleDeg)
                            when {
                                lean < 12.0 -> Color(0xFF00E676) // Neon Green
                                lean < 25.0 -> Color(0xFFCDDC39) // Lime
                                lean < 38.0 -> Color(0xFFFFD600) // Electric Yellow
                                lean < 48.0 -> Color(0xFFFF9100) // Orange
                                else -> Color(0xFFFF1744)        // Redline Crimson
                            }
                        }
                        HeatmapMode.BRAKING_ACCEL -> {
                            val longG = p1.longitudinalG
                            when {
                                longG < -0.35 -> Color(0xFFFF1744) // Hard braking
                                longG < -0.12 -> Color(0xFFFF7043) // Moderate braking
                                longG > 0.30 -> Color(0xFF00E676)  // Hard acceleration
                                longG > 0.12 -> Color(0xFF76FF03)  // Moderate accel
                                else -> Color(0xFF00BCD4)          // Coasting / neutral
                            }
                        }
                        HeatmapMode.SPEED -> {
                            val ratio = (p1.speedKmh / maxSpeed).coerceIn(0.0, 1.0).toFloat().takeIf { it.isFinite() } ?: 0f
                            when {
                                ratio < 0.5f -> {
                                    val r = ratio / 0.5f
                                    Color(
                                        red = r,
                                        green = 0.9f + 0.1f * r,
                                        blue = 1f - r
                                    )
                                }
                                else -> {
                                    val r = (ratio - 0.5f) / 0.5f
                                    Color(
                                        red = 1f,
                                        green = 1f - 0.8f * r,
                                        blue = 0.1f * (1f - r)
                                    )
                                }
                            }
                        }
                    }

                    drawLine(
                        color = color,
                        start = toScreenOffset(p1.latitude, p1.longitude),
                        end = toScreenOffset(p2.latitude, p2.longitude),
                        strokeWidth = 6.5f,
                        cap = StrokeCap.Round
                    )
                }

                val textPaint = android.graphics.Paint().apply {
                    isAntiAlias = true
                    textAlign = android.graphics.Paint.Align.CENTER
                    typeface = android.graphics.Typeface.DEFAULT_BOLD
                }

                // 3. Draw Peak Lean Marker
                peakLeanPoint?.let { peakPoint ->
                    val peakOffset = toScreenOffset(peakPoint.latitude, peakPoint.longitude)
                    drawCircle(color = WarningGold.copy(alpha = 0.35f), radius = 18f, center = peakOffset)
                    drawCircle(color = Color.Black, radius = 12f, center = peakOffset)
                    drawCircle(color = WarningGold, radius = 9f, center = peakOffset)
                }

                // 4. Draw Start marker (S)
                val startPt = validTrackPoints.first()
                val startOffset = toScreenOffset(startPt.latitude, startPt.longitude)
                drawCircle(color = Color.Black, radius = 15f, center = startOffset)
                drawCircle(color = ApexEmeraldGreen, radius = 12f, center = startOffset)
                textPaint.color = android.graphics.Color.BLACK
                textPaint.textSize = 28f
                drawContext.canvas.nativeCanvas.drawText(
                    "S",
                    startOffset.x,
                    startOffset.y - (textPaint.descent() + textPaint.ascent()) / 2f,
                    textPaint
                )

                // 5. Draw Finish marker (F)
                val finishPt = validTrackPoints.last()
                val finishOffset = toScreenOffset(finishPt.latitude, finishPt.longitude)
                drawCircle(color = Color.Black, radius = 15f, center = finishOffset)
                drawCircle(color = RedlineCrimson, radius = 12f, center = finishOffset)
                textPaint.color = android.graphics.Color.WHITE
                textPaint.textSize = 28f
                drawContext.canvas.nativeCanvas.drawText(
                    "F",
                    finishOffset.x,
                    finishOffset.y - (textPaint.descent() + textPaint.ascent()) / 2f,
                    textPaint
                )

                // 6. Draw Numbered Corner Pins (read from precomputed list)
                cornerPinOffsets.forEach { (rc, offset) ->
                    val isSelected = selectedCorner?.cornerId == rc.corner.cornerId
                    val outerRadius = if (isSelected) 18f else 13f
                    val innerRadius = if (isSelected) 15f else 11f

                    drawCircle(color = Color.Black, radius = outerRadius + 2f, center = offset)
                    drawCircle(
                        color = if (isSelected) Color.White else RacingAmberPrimary,
                        radius = outerRadius,
                        center = offset
                    )
                    drawCircle(
                        color = if (isSelected) RacingAmberPrimary else Color(0xFF1E1E1E),
                        radius = innerRadius,
                        center = offset
                    )

                    textPaint.color = android.graphics.Color.WHITE
                    textPaint.textSize = if (isSelected) 26f else 22f
                    val numStr = "${rc.corner.sequenceNumber}"
                    drawContext.canvas.nativeCanvas.drawText(
                        numStr,
                        offset.x,
                        offset.y - (textPaint.descent() + textPaint.ascent()) / 2f,
                        textPaint
                    )
                }

                // 7. Draw Synced Highlight Reticle
                selectedTimestampMs?.let { syncTime ->
                    val closest = validTrackPoints.minByOrNull { abs(it.timestampMs - syncTime) }
                    if (closest != null) {
                        val reticleOffset = toScreenOffset(closest.latitude, closest.longitude)
                        drawCircle(
                            color = TelemetryIceCyan.copy(alpha = 0.45f),
                            radius = 22f,
                            center = reticleOffset,
                            style = Stroke(width = 4f)
                        )
                        drawCircle(
                            color = Color.White,
                            radius = 14f,
                            center = reticleOffset,
                            style = Stroke(width = 2.5f)
                        )
                        drawCircle(
                            color = TelemetryIceCyan,
                            radius = 6f,
                            center = reticleOffset
                        )

                        // Crosshairs
                        drawLine(
                            color = Color.White,
                            start = Offset(reticleOffset.x - 26f, reticleOffset.y),
                            end = Offset(reticleOffset.x - 16f, reticleOffset.y),
                            strokeWidth = 2f
                        )
                        drawLine(
                            color = Color.White,
                            start = Offset(reticleOffset.x + 16f, reticleOffset.y),
                            end = Offset(reticleOffset.x + 26f, reticleOffset.y),
                            strokeWidth = 2f
                        )
                        drawLine(
                            color = Color.White,
                            start = Offset(reticleOffset.x, reticleOffset.y - 26f),
                            end = Offset(reticleOffset.x, reticleOffset.y - 16f),
                            strokeWidth = 2f
                        )
                        drawLine(
                            color = Color.White,
                            start = Offset(reticleOffset.x, reticleOffset.y + 16f),
                            end = Offset(reticleOffset.x, reticleOffset.y + 26f),
                            strokeWidth = 2f
                        )
                    }
                }
            }

            // Reset Zoom Chip
            if (isZoomed) {
                AssistChip(
                    onClick = {
                        zoom = 1f
                        pan = Offset.Zero
                    },
                    label = { Text(tr("Reset", "Pôvodné"), fontSize = 12.sp) },
                    leadingIcon = {
                        Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(14.dp))
                    },
                    colors = AssistChipDefaults.assistChipColors(
                        containerColor = TftSurfaceDark.copy(alpha = 0.9f),
                        labelColor = Color.White,
                        leadingIconContentColor = RacingAmberPrimary
                    ),
                    border = BorderStroke(1.dp, TitaniumBorder),
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(8.dp)
                )
            }

            // Floating HUD for Selected Corner
            selectedCorner?.let { corner ->
                Card(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(8.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.Black.copy(alpha = 0.88f)),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val dirStr = if (corner.direction.equals("LEFT", ignoreCase = true)) tr("Left", "Vľavo") else tr("Right", "Vpravo")
                            Text(
                                text = "${tr("Turn", "Zákruta")} ${corner.sequenceNumber} ($dirStr)",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                            IconButton(
                                onClick = {
                                    selectedCorner = null
                                    onPointSelected?.invoke(null)
                                },
                                modifier = Modifier.size(20.dp)
                            ) {
                                Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.LightGray)
                            }
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            Column {
                                Text(
                                    "${tr("Entry", "Nájazd")}: ${String.format(Locale.US, "%.1f", corner.entrySpeedKmh)} km/h",
                                    color = Color.LightGray,
                                    fontSize = 11.sp
                                )
                                Text(
                                    "${tr("Apex Lean", "Náklon")}: ${String.format(Locale.US, "%.1f", corner.apexLeanDeg)}°",
                                    color = WarningGold,
                                    fontSize = 11.sp
                                )
                            }
                            Column {
                                Text(
                                    "${tr("Peak G", "Max G")}: ${String.format(Locale.US, "%.2f", corner.peakLateralG)}g",
                                    color = RedlineCrimson,
                                    fontSize = 11.sp
                                )
                                Text(
                                    "${tr("Score", "Skóre")}: ${String.format(Locale.US, "%.0f", corner.cornerScore)}",
                                    color = ApexEmeraldGreen,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.sp
                                )
                            }
                        }
                    }
                }
            }
        }

        // Color Legend Strip
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 6.dp),
            color = TftSurfaceDark,
            shape = RoundedCornerShape(6.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                when (selectedMode) {
                    HeatmapMode.LEAN_ANGLE -> {
                        Text(tr("Lean:", "Náklon:"), fontSize = 11.sp, color = Color.Gray)
                        LegendItem(color = Color(0xFF00E676), label = "<12°")
                        LegendItem(color = Color(0xFFCDDC39), label = "25°")
                        LegendItem(color = Color(0xFFFFD600), label = "38°")
                        LegendItem(color = Color(0xFFFF9100), label = "48°")
                        LegendItem(color = Color(0xFFFF1744), label = "50°+")
                    }
                    HeatmapMode.BRAKING_ACCEL -> {
                        Text(tr("G-Force:", "G-Sily:"), fontSize = 11.sp, color = Color.Gray)
                        LegendItem(color = Color(0xFFFF1744), label = tr("Braking", "Brzdenie"))
                        LegendItem(color = Color(0xFF00BCD4), label = tr("Coast", "Plynule"))
                        LegendItem(color = Color(0xFF00E676), label = tr("Accel", "Akcelerácia"))
                    }
                    HeatmapMode.SPEED -> {
                        Text(tr("Speed:", "Rýchlosť:"), fontSize = 11.sp, color = Color.Gray)
                        LegendItem(color = Color(0xFF00E5FF), label = "0 km/h")
                        LegendItem(color = Color(0xFFFFD600), label = "${(maxSpeed * 0.5).toInt()} km/h")
                        LegendItem(color = Color(0xFFFF1744), label = "${maxSpeed.toInt()} km/h")
                    }
                }
            }
        }
    }
}

@Composable
private fun LegendItem(color: Color, label: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .background(color, CircleShape)
        )
        Text(text = label, fontSize = 10.sp, color = Color.LightGray)
    }
}

@Composable
fun RideTrajectoryHeatmapCanvas(
    trackPoints: List<TelemetryTrackPointEntity>,
    cornerEvents: List<CornerEventEntity>,
    synchronizedTimestampMs: Long?,
    modifier: Modifier = Modifier
) = RideTrajectoryHeatmapCanvas(
    trackPoints = trackPoints,
    cornerEvents = cornerEvents,
    selectedTimestampMs = synchronizedTimestampMs,
    onPointSelected = null,
    modifier = modifier
)

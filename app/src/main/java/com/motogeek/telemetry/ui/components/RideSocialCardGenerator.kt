package com.motogeek.telemetry.ui.components

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import androidx.core.content.FileProvider
import com.motogeek.telemetry.data.local.RideSessionEntity
import com.motogeek.telemetry.data.local.TelemetryTrackPointEntity
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object RideSocialCardGenerator {
    suspend fun generateAndSaveCard(
        context: Context,
        ride: RideSessionEntity,
        trackPoints: List<TelemetryTrackPointEntity>
    ): File = withContext(Dispatchers.IO) {
        val width = 1080
        val height = 1350
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        // Background
        canvas.drawColor(Color.parseColor("#121212"))

        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }

        // Header
        textPaint.textSize = 64f
        val makeModelStr = "KurveCounter: ${ride.motorcycleId.uppercase(Locale.getDefault())}"
        canvas.drawText(makeModelStr, 80f, 120f, textPaint)

        textPaint.textSize = 36f
        textPaint.color = Color.LTGRAY
        textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        val sdf = SimpleDateFormat("MMM dd, yyyy - HH:mm", Locale.getDefault())
        val dateStr = ride.name ?: sdf.format(Date(ride.startTimeEpochMs))
        canvas.drawText(dateStr, 80f, 180f, textPaint)

        // Draw Map (simplified trajectory polyline)
        if (trackPoints.isNotEmpty()) {
            val mapRect = RectF(80f, 250f, width - 80f, height - 400f)

            // Background for map
            val mapBgPaint = Paint().apply { color = Color.parseColor("#1E1E1E") }
            canvas.drawRoundRect(mapRect, 30f, 30f, mapBgPaint)

            val minLat = trackPoints.minOf { it.latitude }
            val maxLat = trackPoints.maxOf { it.latitude }
            val minLon = trackPoints.minOf { it.longitude }
            val maxLon = trackPoints.maxOf { it.longitude }

            val latDiff = maxLat - minLat
            val lonDiff = maxLon - minLon

            val usableWidth = mapRect.width() - 80f
            val usableHeight = mapRect.height() - 80f

            val scaleX = if (lonDiff > 0) (usableWidth / lonDiff.toFloat()).takeIf { it.isFinite() } ?: 1f else 1f
            val scaleY = if (latDiff > 0) (usableHeight / latDiff.toFloat()).takeIf { it.isFinite() } ?: 1f else 1f
            val scale = min(scaleX, scaleY)

            val xOffset = mapRect.left + 40f + (usableWidth - lonDiff.toFloat() * scale) / 2f
            val yOffset = mapRect.top + 40f + (usableHeight - latDiff.toFloat() * scale) / 2f

            fun getX(lon: Double) = ((lon - minLon).toFloat() * scale + xOffset)
            fun getY(lat: Double) = ((maxLat - lat).toFloat() * scale + yOffset)

            val path = Path()
            var isFirst = true

            for (point in trackPoints) {
                val x = getX(point.longitude)
                val y = getY(point.latitude)
                if (isFirst) {
                    path.moveTo(x, y)
                    isFirst = false
                } else {
                    path.lineTo(x, y)
                }
            }

            val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = 8f
                color = Color.parseColor("#00FFCC") // MotoGeek Cyan
                strokeCap = Paint.Cap.ROUND
                strokeJoin = Paint.Join.ROUND
            }
            canvas.drawPath(path, strokePaint)
        }

        // Stats Footer
        val statsY = height - 320f

        fun drawStatBadge(label: String, value: String, x: Float, y: Float, highlightColor: Int) {
            // Label
            val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.LTGRAY
                textSize = 28f
                textAlign = Paint.Align.CENTER
            }
            canvas.drawText(label, x, y, labelPaint)

            // Value
            val valPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = highlightColor
                textSize = 48f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                textAlign = Paint.Align.CENTER
            }
            canvas.drawText(value, x, y + 60f, valPaint)
        }

        val col1 = width * 0.25f
        val col2 = width * 0.5f
        val col3 = width * 0.75f

        drawStatBadge("DISTANCE", String.format("%.1f km", ride.distanceKm), col1, statsY, Color.WHITE)
        drawStatBadge("MAX LEAN (L|R)", "${String.format("%.0f", ride.maxLeanLeftDeg)}° | ${String.format("%.0f", ride.maxLeanRightDeg)}°", col2, statsY, Color.parseColor("#FFD700"))
        drawStatBadge("CORNERS", "${ride.totalCornersCount}", col3, statsY, Color.WHITE)

        val statsY2 = height - 160f
        drawStatBadge("MAX G (LAT|BRK)", "${String.format("%.2f", ride.maxLateralG)} | ${String.format("%.2f", ride.maxBrakingG)}", col1, statsY2, Color.parseColor("#FF4444"))
        drawStatBadge("STYLE", ride.ridingStyleBadge, col2, statsY2, Color.parseColor("#00FFCC"))
        drawStatBadge("AVG SCORE", String.format("%.0f", ride.avgCornerScore), col3, statsY2, Color.parseColor("#FFD700"))

        val file = File(context.cacheDir, "ride_card_${ride.rideId}.png")
        FileOutputStream(file).use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
        file // Implicit return for the withContext lambda
    }
}

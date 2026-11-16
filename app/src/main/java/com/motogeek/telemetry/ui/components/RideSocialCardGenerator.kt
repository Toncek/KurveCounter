package com.motogeek.telemetry.ui.components

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import com.motogeek.telemetry.data.local.RideSessionEntity
import com.motogeek.telemetry.data.local.TelemetryTrackPointEntity
import com.motogeek.telemetry.ui.theme.AppLang
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.min
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object RideSocialCardGenerator {
    suspend fun generateAndSaveCard(
        context: Context,
        ride: RideSessionEntity,
        trackPoints: List<TelemetryTrackPointEntity>,
        bikeDisplayName: String? = null,
        lang: AppLang = AppLang.EN
    ): File = withContext(Dispatchers.IO) {
        val width = 1080
        val height = 1350
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        // Background
        canvas.drawColor(Color.parseColor("#0B0E14"))

        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }

        // Header - Bike Title
        val fallbackBikeName = if (lang == AppLang.SK) "MOTOCYKEL" else "MOTORCYCLE"
        val bikeName = bikeDisplayName?.takeIf { it.isNotBlank() }
            ?: (if (ride.motorcycleId.length > 20 || ride.motorcycleId.contains("-")) fallbackBikeName else ride.motorcycleId)
        val bikeHeader = bikeName.uppercase(Locale.US)
        val makeModelStr = "KurveCounter: $bikeHeader"

        textPaint.textSize = 54f
        textPaint.color = Color.WHITE
        textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        canvas.drawText(makeModelStr, 80f, 110f, textPaint)

        // Ride Date / Time & Custom Name
        val dateLocale = if (lang == AppLang.SK) Locale("sk", "SK") else Locale.US
        val sdf = SimpleDateFormat("MMM dd, yyyy - HH:mm", dateLocale)
        val formattedDate = sdf.format(Date(ride.startTimeEpochMs))

        if (!ride.name.isNullOrBlank()) {
            textPaint.textSize = 38f
            textPaint.color = Color.parseColor("#00FFCC")
            textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            canvas.drawText(ride.name, 80f, 165f, textPaint)

            textPaint.textSize = 28f
            textPaint.color = Color.LTGRAY
            textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            canvas.drawText(formattedDate, 80f, 210f, textPaint)
        } else {
            textPaint.textSize = 36f
            textPaint.color = Color.LTGRAY
            textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            canvas.drawText(formattedDate, 80f, 175f, textPaint)
        }

        // Filter valid GPS points
        val validPoints = trackPoints.filter { pt ->
            pt.latitude.isFinite() && pt.longitude.isFinite() &&
                !(abs(pt.latitude) < 1e-6 && abs(pt.longitude) < 1e-6)
        }

        // Draw Map Container
        val mapRect = RectF(80f, 240f, width - 80f, height - 390f)
        val mapBgPaint = Paint().apply { color = Color.parseColor("#131822") }
        canvas.drawRoundRect(mapRect, 24f, 24f, mapBgPaint)

        if (validPoints.isNotEmpty()) {
            val minLat = validPoints.minOf { it.latitude }
            val maxLat = validPoints.maxOf { it.latitude }
            val minLon = validPoints.minOf { it.longitude }
            val maxLon = validPoints.maxOf { it.longitude }

            val avgLat = (minLat + maxLat) / 2.0
            val cosLat = cos(Math.toRadians(avgLat)).coerceAtLeast(0.2)

            val latSpan = (maxLat - minLat).coerceAtLeast(1e-7)
            val lonSpan = ((maxLon - minLon) * cosLat).coerceAtLeast(1e-7)

            val usableWidth = mapRect.width() - 80f
            val usableHeight = mapRect.height() - 80f

            val scaleX = (usableWidth / lonSpan.toFloat()).takeIf { it.isFinite() && it > 0 } ?: 1f
            val scaleY = (usableHeight / latSpan.toFloat()).takeIf { it.isFinite() && it > 0 } ?: 1f
            val scale = min(scaleX, scaleY)

            val xOffset = mapRect.left + 40f + (usableWidth - lonSpan.toFloat() * scale) / 2f
            val yOffset = mapRect.top + 40f + (usableHeight - latSpan.toFloat() * scale) / 2f

            fun getX(lon: Double) = ((lon - minLon) * cosLat).toFloat() * scale + xOffset
            fun getY(lat: Double) = (maxLat - lat).toFloat() * scale + yOffset

            if (validPoints.size == 1) {
                val cx = getX(validPoints[0].longitude)
                val cy = getY(validPoints[0].latitude)
                val ptPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#00FFCC") }
                canvas.drawCircle(cx, cy, 20f, ptPaint)
            } else {
                val path = Path()
                var isFirst = true
                for (point in validPoints) {
                    val x = getX(point.longitude)
                    val y = getY(point.latitude)
                    if (isFirst) {
                        path.moveTo(x, y)
                        isFirst = false
                    } else {
                        path.lineTo(x, y)
                    }
                }

                // Dark casing stroke under polyline for halo effect
                val haloPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    style = Paint.Style.STROKE
                    strokeWidth = 14f
                    color = Color.BLACK
                    strokeCap = Paint.Cap.ROUND
                    strokeJoin = Paint.Join.ROUND
                }
                canvas.drawPath(path, haloPaint)

                // Foreground Cyan trajectory line
                val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    style = Paint.Style.STROKE
                    strokeWidth = 8f
                    color = Color.parseColor("#00FFCC")
                    strokeCap = Paint.Cap.ROUND
                    strokeJoin = Paint.Join.ROUND
                }
                canvas.drawPath(path, strokePaint)

                // Marker paint helper
                val markerPaint = Paint(Paint.ANTI_ALIAS_FLAG)

                // Start Marker (S)
                val startX = getX(validPoints.first().longitude)
                val startY = getY(validPoints.first().latitude)
                markerPaint.color = Color.BLACK
                canvas.drawCircle(startX, startY, 18f, markerPaint)
                markerPaint.color = Color.parseColor("#00E676")
                canvas.drawCircle(startX, startY, 14f, markerPaint)

                markerPaint.color = Color.BLACK
                markerPaint.textSize = 18f
                markerPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                markerPaint.textAlign = Paint.Align.CENTER
                canvas.drawText("S", startX, startY - (markerPaint.descent() + markerPaint.ascent()) / 2f, markerPaint)

                // Finish Marker (F)
                val endX = getX(validPoints.last().longitude)
                val endY = getY(validPoints.last().latitude)
                markerPaint.color = Color.BLACK
                canvas.drawCircle(endX, endY, 18f, markerPaint)
                markerPaint.color = Color.parseColor("#FF1744")
                canvas.drawCircle(endX, endY, 14f, markerPaint)

                markerPaint.color = Color.WHITE
                markerPaint.textSize = 18f
                markerPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                markerPaint.textAlign = Paint.Align.CENTER
                canvas.drawText("F", endX, endY - (markerPaint.descent() + markerPaint.ascent()) / 2f, markerPaint)
            }
        } else {
            val emptyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.GRAY
                textSize = 34f
                textAlign = Paint.Align.CENTER
            }
            val emptyMsg = if (lang == AppLang.SK) "Nie sú k dispozícii žiadne GPS údaje" else "No GPS trajectory recorded"
            canvas.drawText(emptyMsg, mapRect.centerX(), mapRect.centerY(), emptyPaint)
        }

        // Stats Footer
        val statsY = height - 300f

        fun drawStatBadge(label: String, value: String, x: Float, y: Float, highlightColor: Int) {
            val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#8E9AA8")
                textSize = 26f
                textAlign = Paint.Align.CENTER
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            }
            canvas.drawText(label, x, y, labelPaint)

            val valPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = highlightColor
                textSize = 44f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                textAlign = Paint.Align.CENTER
            }
            canvas.drawText(value, x, y + 54f, valPaint)
        }

        val col1 = width * 0.22f
        val col2 = width * 0.50f
        val col3 = width * 0.78f

        val distLabel = if (lang == AppLang.SK) "VZDIALENOSŤ" else "DISTANCE"
        val leanLabel = if (lang == AppLang.SK) "MAX NÁKLON (Ľ|P)" else "MAX LEAN (L|R)"
        val cornersLabel = if (lang == AppLang.SK) "ZÁKRUTY" else "CORNERS"
        val gLabel = if (lang == AppLang.SK) "MAX G (BOČ|BRZ)" else "MAX G (LAT|BRK)"
        val styleLabel = if (lang == AppLang.SK) "ŠTÝL" else "STYLE"
        val scoreLabel = if (lang == AppLang.SK) "PRIEM. SKÓRE" else "AVG SCORE"

        val distanceSafe = ride.distanceKm.takeIf { it.isFinite() } ?: 0.0
        val leanLeftSafe = ride.maxLeanLeftDeg.takeIf { it.isFinite() } ?: 0.0
        val leanRightSafe = ride.maxLeanRightDeg.takeIf { it.isFinite() } ?: 0.0
        val lateralGSafe = ride.maxLateralG.takeIf { it.isFinite() } ?: 0.0
        val brakingGSafe = ride.maxBrakingG.takeIf { it.isFinite() } ?: 0.0
        val scoreSafe = ride.avgCornerScore.takeIf { it.isFinite() } ?: 0.0

        val localizedStyle = when (ride.ridingStyleBadge.trim().uppercase(Locale.US)) {
            "RACER" -> if (lang == AppLang.SK) "PRETEKÁR" else "RACER"
            "TOURER" -> if (lang == AppLang.SK) "TURISTA" else "TOURER"
            "TECHNICIAN" -> if (lang == AppLang.SK) "TECHNIK" else "TECHNICIAN"
            "BALANCED" -> if (lang == AppLang.SK) "VYVÁŽENÝ" else "BALANCED"
            "CANYON CARVER" -> if (lang == AppLang.SK) "ZÁKRUTÁR" else "CANYON CARVER"
            "UNKNOWN" -> if (lang == AppLang.SK) "NEZNÁMY" else "UNKNOWN"
            else -> ride.ridingStyleBadge
        }

        drawStatBadge(distLabel, String.format(Locale.US, "%.1f km", distanceSafe), col1, statsY, Color.WHITE)
        drawStatBadge(
            leanLabel,
            "${String.format(Locale.US, "%.0f", leanLeftSafe)}° | ${String.format(Locale.US, "%.0f", leanRightSafe)}°",
            col2,
            statsY,
            Color.parseColor("#FFD700")
        )
        drawStatBadge(cornersLabel, "${ride.totalCornersCount}", col3, statsY, Color.WHITE)

        val statsY2 = height - 160f
        drawStatBadge(
            gLabel,
            "${String.format(Locale.US, "%.2f", lateralGSafe)} | ${String.format(Locale.US, "%.2f", brakingGSafe)}",
            col1,
            statsY2,
            Color.parseColor("#FF4444")
        )
        drawStatBadge(styleLabel, localizedStyle, col2, statsY2, Color.parseColor("#00FFCC"))
        drawStatBadge(scoreLabel, String.format(Locale.US, "%.0f", scoreSafe), col3, statsY2, Color.parseColor("#FFD700"))

        val file = File(context.cacheDir, "ride_card_${ride.rideId}.png")
        FileOutputStream(file).use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
        file
    }
}

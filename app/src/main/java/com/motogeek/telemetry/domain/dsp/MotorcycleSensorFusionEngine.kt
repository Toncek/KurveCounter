package com.motogeek.telemetry.domain.dsp

import android.location.Location
import com.motogeek.telemetry.data.local.CornerEventEntity
import kotlin.math.*

class MotorcycleSensorFusionEngine(
    private val cornerEventCallback: (CornerEventEntity) -> Unit
) {
    private val accelFilter = ThreeAxisButterworthFilter(100.0, 3.5)
    private val gyroFilter = ThreeAxisButterworthFilter(100.0, 3.5)
    val calibrator = PocketModeCalibrator()

    var currentLeanAngleDeg = 0.0
        private set
    var currentLongitudinalG = 0.0
        private set
    var currentLateralG = 0.0
        private set
    var currentSpeedKmh = 0.0
        private set

    var totalCorners = 0
        private set
    var leftCorners = 0
        private set
    var rightCorners = 0
        private set

    private enum class CornerState { STRAIGHT, CORNER_CANDIDATE, CONFIRMED_CORNER }
    private var cornerState = CornerState.STRAIGHT

    private var lastStopStartTimeMs: Long = 0
    var isAutoPaused = false
        private set
    private var cornerStartTimeMs = 0L
    private var cornerMaxLean = 0.0
    private var cornerEntrySpeed = 0.0
    private var cornerMaxBrakingG = 0.0
    private var cornerMaxLateralG = 0.0
    private var cornerExitAccelG = 0.0
    private var cornerMaxRollRate = 0.0
    private var lastLat = 0.0
    private var lastLon = 0.0

    private var lastGpsTimeMs: Long = 0L
    private var lastGpsLat = 0.0
    private var lastGpsLon = 0.0
    private var lastGpsSpeedMs = 0.0
    
    private var lastImuTimestampNanos: Long = 0L

    fun processImu(
        timestampNanos: Long,
        accelX: Double, accelY: Double, accelZ: Double,
        gyroX: Double, gyroY: Double, gyroZ: Double
    ) {
        val dtSec = if (lastImuTimestampNanos > 0) {
            ((timestampNanos - lastImuTimestampNanos) / 1_000_000_000.0).coerceIn(0.001, 0.05)
        } else {
            0.01
        }
        lastImuTimestampNanos = timestampNanos

        val (fAx, fAy, fAz) = accelFilter.process(accelX, accelY, accelZ)
        val (fGx, fGy, fGz) = gyroFilter.process(gyroX, gyroY, gyroZ)

        calibrator.processImuSample(timestampNanos, fAx, fAy, fAz)

        if (!calibrator.isCalibrated) {
            return
        }

        val (bikeAx, bikeAy, bikeAz) = calibrator.transform(fAx, fAy, fAz)
        val (bikeGx, bikeGy, bikeGz) = calibrator.transform(fGx, fGy, fGz)

        // Physics: A right turn is clockwise seen from above, which is a negative rotation about the Z axis.
        // The convention is a right turn yields positive lean and positive lateral G.
        val yawRateRight = -bikeGz
        val rollRateDeg = Math.toDegrees(bikeGx) // Note: roll rate sign might need to be adjusted to align with lean depending on how it's used. Since positive roll implies positive lean change, and we want positive lean for right turn, let's keep rollRateDeg as is assuming bikeGx is positive for right lean.

        if (calibrator.state == CalibrationState.LOCKED) {
            currentLongitudinalG = (bikeAx / 9.80665).takeIf { it.isFinite() } ?: 0.0
        }

        val speedMs = lastGpsSpeedMs
        
        val kinematicLateralG = (speedMs * yawRateRight / 9.80665).takeIf { it.isFinite() } ?: 0.0
        currentLateralG = kinematicLateralG.coerceIn(-3.0, 3.0)

        val kinematicLeanRad = atan2(kinematicLateralG, 1.0)
        val kinematicLeanDeg = Math.toDegrees(kinematicLeanRad)

        val alpha = 1.0 / (1.0 + dtSec) 
        val delta = (rollRateDeg * dtSec).takeIf { it.isFinite() } ?: 0.0
        
        var newLean = alpha * (currentLeanAngleDeg + delta) + (1 - alpha) * kinematicLeanDeg
        if (speedMs < 2.0) {
            newLean = newLean * 0.95
        }
        currentLeanAngleDeg = newLean.coerceIn(-70.0, 70.0).takeIf { it.isFinite() } ?: 0.0

        updateCornerStateMachine(timestampNanos / 1_000_000, rollRateDeg)
    }
    
    fun processGps(
        timestampMs: Long,
        speedMs: Double,
        hasSpeed: Boolean,
        bearingDeg: Double,
        hasBearing: Boolean,
        latitude: Double,
        longitude: Double
    ) {
        var finalSpeedMs = speedMs
        var derivedSpeed = false
        
        val dtSec = if (lastGpsTimeMs > 0) (timestampMs - lastGpsTimeMs) / 1000.0 else 0.0
        
        if (!hasSpeed && dtSec > 0) {
            val dist = haversineDistance(lastGpsLat, lastGpsLon, latitude, longitude)
            finalSpeedMs = (dist / dtSec).takeIf { it.isFinite() } ?: 0.0
            derivedSpeed = true
        }

        currentSpeedKmh = (finalSpeedMs * 3.6).takeIf { it.isFinite() } ?: 0.0

        if (currentSpeedKmh < 1.5) {
            if (lastStopStartTimeMs == 0L) {
                lastStopStartTimeMs = timestampMs
            } else if (timestampMs - lastStopStartTimeMs > 10000) {
                isAutoPaused = true
            }
        } else if (currentSpeedKmh > 5.0) {
            lastStopStartTimeMs = 0L
            isAutoPaused = false
        }

        var finalYawRateDegSec = 0.0
        var finalLongAccelMs2 = 0.0
        
        if (dtSec > 0) {
            val speedChange = finalSpeedMs - lastGpsSpeedMs
            finalLongAccelMs2 = (speedChange / dtSec).takeIf { it.isFinite() } ?: 0.0
            
            if (calibrator.state != CalibrationState.LOCKED) {
                currentLongitudinalG = (finalLongAccelMs2 / 9.80665).takeIf { it.isFinite() } ?: 0.0
            }
        }
        
        calibrator.processGpsSample(timestampMs, finalSpeedMs, finalLongAccelMs2, finalYawRateDegSec)
        
        lastGpsSpeedMs = finalSpeedMs
        lastGpsLat = latitude
        lastGpsLon = longitude
        lastGpsTimeMs = timestampMs
        lastLat = latitude
        lastLon = longitude
    }

    fun processLocation(location: Location) {
        processGps(
            timestampMs = location.time,
            speedMs = location.speed.toDouble(),
            hasSpeed = location.hasSpeed(),
            bearingDeg = location.bearing.toDouble(),
            hasBearing = location.hasBearing(),
            latitude = location.latitude,
            longitude = location.longitude
        )
    }

    private fun haversineDistance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val R = 6371000.0
        val phi1 = Math.toRadians(lat1)
        val phi2 = Math.toRadians(lat2)
        val deltaPhi = Math.toRadians(lat2 - lat1)
        val deltaLambda = Math.toRadians(lon2 - lon1)

        val a = sin(deltaPhi / 2) * sin(deltaPhi / 2) +
                cos(phi1) * cos(phi2) *
                sin(deltaLambda / 2) * sin(deltaLambda / 2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return (R * c).takeIf { it.isFinite() } ?: 0.0
    }

    private fun updateCornerStateMachine(timestampMs: Long, rollRateDeg: Double) {
        val absLean = abs(currentLeanAngleDeg)

        when (cornerState) {
            CornerState.STRAIGHT -> {
                if (absLean >= 10.0) {
                    cornerState = CornerState.CORNER_CANDIDATE
                    cornerStartTimeMs = timestampMs
                    cornerMaxLean = currentLeanAngleDeg
                    cornerEntrySpeed = currentSpeedKmh
                    cornerMaxBrakingG = min(currentLongitudinalG, 0.0)
                    cornerMaxLateralG = currentLateralG
                    cornerExitAccelG = max(currentLongitudinalG, 0.0)
                    cornerMaxRollRate = abs(rollRateDeg)
                }
            }
            CornerState.CORNER_CANDIDATE -> {
                updateCornerStats(rollRateDeg)
                if (absLean < 6.0) {
                    cornerState = CornerState.STRAIGHT
                } else if (timestampMs - cornerStartTimeMs >= 1200) {
                    cornerState = CornerState.CONFIRMED_CORNER
                }
            }
            CornerState.CONFIRMED_CORNER -> {
                updateCornerStats(rollRateDeg)
                if (absLean < 6.0) {
                    val direction = if (cornerMaxLean > 0) "RIGHT" else "LEFT"
                    totalCorners++
                    if (direction == "LEFT") leftCorners++ else rightCorners++

                    val cornerScore = min(100.0, max(0.0, (abs(cornerMaxLean) / 50.0) * 100.0))

                    val event = CornerEventEntity(
                        rideId = 0L,
                        sequenceNumber = totalCorners,
                        timestampMs = cornerStartTimeMs,
                        direction = direction,
                        entrySpeedKmh = cornerEntrySpeed,
                        apexLeanDeg = abs(cornerMaxLean),
                        peakLateralG = abs(cornerMaxLateralG),
                        peakBrakingG = cornerMaxBrakingG,
                        exitAccelG = cornerExitAccelG,
                        maxRollRateDegPerSec = cornerMaxRollRate,
                        cornerScore = cornerScore,
                        latitude = lastLat,
                        longitude = lastLon
                    )
                    cornerEventCallback(event)

                    cornerState = CornerState.STRAIGHT
                }
            }
        }
    }

    private fun updateCornerStats(rollRateDeg: Double) {
        if (abs(currentLeanAngleDeg) > abs(cornerMaxLean)) {
            cornerMaxLean = currentLeanAngleDeg
        }
        cornerMaxBrakingG = min(cornerMaxBrakingG, currentLongitudinalG)
        cornerMaxLateralG = if (abs(currentLateralG) > abs(cornerMaxLateralG)) currentLateralG else cornerMaxLateralG
        cornerExitAccelG = max(cornerExitAccelG, currentLongitudinalG)
        cornerMaxRollRate = max(cornerMaxRollRate, abs(rollRateDeg))
    }

    fun classifyRidingStyle(avgG: Double, maxLean: Double): String {
        return when {
            maxLean > 45.0 && avgG > 0.8 -> "RACER"
            maxLean < 35.0 && avgG < 0.4 -> "TOURER"
            else -> "TECHNICIAN"
        }
    }
    
    fun reset() {
        accelFilter.reset()
        gyroFilter.reset()
        calibrator.reset()
        
        currentLeanAngleDeg = 0.0
        currentLongitudinalG = 0.0
        currentLateralG = 0.0
        currentSpeedKmh = 0.0
        
        totalCorners = 0
        leftCorners = 0
        rightCorners = 0
        
        cornerState = CornerState.STRAIGHT
        isAutoPaused = false
        lastStopStartTimeMs = 0L
        
        lastGpsTimeMs = 0L
        lastGpsSpeedMs = 0.0
        lastImuTimestampNanos = 0L
    }
}

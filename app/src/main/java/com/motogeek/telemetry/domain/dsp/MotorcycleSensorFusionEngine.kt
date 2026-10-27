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

    // State machine for corner detection
    private enum class CornerState { STRAIGHT, CORNER_CANDIDATE, CONFIRMED_CORNER }
private var cornerState = CornerState.STRAIGHT

    // Auto Pause
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

    // For kinematic lean calculation
    private var lastLocation: Location? = null

    fun processImu(
        timestampMs: Long,
        accelX: Double, accelY: Double, accelZ: Double,
        gyroX: Double, gyroY: Double, gyroZ: Double
    ) {
        val (fAx, fAy, fAz) = accelFilter.process(accelX, accelY, accelZ)
        val (fGx, fGy, fGz) = gyroFilter.process(gyroX, gyroY, gyroZ)

        if (!calibrator.isCalibrated) {
            // Cannot reliably compute G forces or lean without calibration
            return
        }

        val (bikeAx, bikeAy, bikeAz) = calibrator.transform(fAx, fAy, fAz)
        val (bikeGx, bikeGy, bikeGz) = calibrator.transform(fGx, fGy, fGz)

        // G-forces (divide by 9.80665)
        currentLongitudinalG = bikeAx / 9.80665
        currentLateralG = bikeAy / 9.80665

        // Complementary filter for lean angle could be implemented here using gyro integration
        // For simplicity and to solve the lean angle trap, we strongly rely on kinematic lean from GPS
        // but update it with integrated roll rate for high frequency
        val rollRateDeg = Math.toDegrees(bikeGx)
        val dt = 0.01 // 100Hz
        val delta = (rollRateDeg * dt).takeIf { it.isFinite() } ?: 0.0
        currentLeanAngleDeg += delta

        // Hysteresis State Machine for Corner Detection
        updateCornerStateMachine(timestampMs, rollRateDeg)
    }

    fun processLocation(location: Location) {
        val speedMs = location.speed.toDouble()
        currentSpeedKmh = speedMs * 3.6
if (speedMs * 3.6 < 1.5) {
            if (lastStopStartTimeMs == 0L) {
                lastStopStartTimeMs = location.time
            } else if (location.time - lastStopStartTimeMs > 10000) {
                isAutoPaused = true
            }
        } else if (speedMs * 3.6 > 5.0) {
            lastStopStartTimeMs = 0L
            isAutoPaused = false
        }

        lastLat = location.latitude
        lastLon = location.longitude

        if (lastLocation != null) {
            val dtSec = (location.time - lastLocation!!.time) / 1000.0
            if (dtSec > 0) {
                val bearingChange = location.bearing - lastLocation!!.bearing
                // Normalize bearing change to -180..180
                var normBearingChange = bearingChange.toDouble()
                while (normBearingChange > 180) normBearingChange -= 360
                while (normBearingChange < -180) normBearingChange += 360

                val yawRateRadSec = Math.toRadians(normBearingChange / dtSec)

                // Kinematic Lean Angle: phi = atan((v * w) / g)
                val kinematicLeanRad = atan2((speedMs * yawRateRadSec).takeIf { it.isFinite() } ?: 0.0, 9.80665)
                val kinematicLeanDeg = Math.toDegrees(kinematicLeanRad)

                // Try calibrating if not calibrated
                if (!calibrator.isCalibrated) {
                    // Send uncalibrated raw IMU values (since we don't have them here,
                    // this requires tightly coupling IMU and GPS.
                    // For this architecture, calibrator is called in `processImu` if we pass the latest GPS values.)
                }

                // Complementary filter (fuse integrated gyro with kinematic lean)
                // If calibrated, we trust kinematic lean for low frequency, gyro for high frequency
                currentLeanAngleDeg = 0.98 * currentLeanAngleDeg + 0.02 * kinematicLeanDeg
            }
        }
        lastLocation = location
    }

    fun updateCalibratorGpsData(speedMs: Double, yawRateDegSec: Double, longAccelMs2: Double, ax: Double, ay: Double, az: Double) {
        calibrator.processSample(ax, ay, az, speedMs, yawRateDegSec, longAccelMs2)
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
                } else if (timestampMs - cornerStartTimeMs >= 1200) { // 1.2 seconds sustained
                    cornerState = CornerState.CONFIRMED_CORNER
                }
            }
            CornerState.CONFIRMED_CORNER -> {
                updateCornerStats(rollRateDeg)
                if (absLean < 6.0) {
                    // Corner exit
                    val direction = if (cornerMaxLean < 0) "LEFT" else "RIGHT"
                    totalCorners++
                    if (direction == "LEFT") leftCorners++ else rightCorners++

                    val cornerScore = min(100.0, max(0.0, (abs(cornerMaxLean) / 50.0) * 100.0)) // Simple scoring

                    val event = CornerEventEntity(
                        rideId = 0L, // To be filled by Service or Repo
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
}

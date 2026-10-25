package com.motogeek.telemetry.domain.dsp

import kotlin.math.sqrt

class PocketModeCalibrator {

    private val calibrationBuffer = mutableListOf<Triple<Double, Double, Double>>()
    private val MAX_BUFFER_SIZE = 100 // 1 second at 100Hz

    // 3x3 Rotation matrix to transform from Phone Frame to Bike Frame
    // X_bike = Forward, Y_bike = Lateral, Z_bike = Vertical
    private var rotationMatrix: Array<DoubleArray>? = null
    var isCalibrated = false
        private set

    fun processSample(
        accelX: Double, accelY: Double, accelZ: Double,
        gpsSpeedMs: Double, gpsYawRateDegSec: Double, longAccelMs2: Double
    ) {
        if (isCalibrated) return

        // Calibration criteria: moving straight and upright
        val isStraight = gpsSpeedMs > 8.0 && kotlin.math.abs(gpsYawRateDegSec) < 1.5 && kotlin.math.abs(longAccelMs2) < 0.4

        if (isStraight) {
            calibrationBuffer.add(Triple(accelX, accelY, accelZ))
            if (calibrationBuffer.size >= MAX_BUFFER_SIZE) {
                calibrate()
            }
        } else {
            calibrationBuffer.clear()
        }
    }

    private fun calibrate() {
        if (calibrationBuffer.isEmpty()) return

        // Compute average gravity vector (Z-axis in bike frame)
        var sumX = 0.0
        var sumY = 0.0
        var sumZ = 0.0
        for (v in calibrationBuffer) {
            sumX += v.first
            sumY += v.second
            sumZ += v.third
        }
        val count = calibrationBuffer.size.toDouble()
        val avgX = sumX / count
        val avgY = sumY / count
        val avgZ = sumZ / count

        // Gravity points DOWN, so we want the UP vector
        val zBike = normalize(Triple(-avgX, -avgY, -avgZ))

        // Default to orthogonal projection for X-axis (forward) based on phone's Y axis
        // Assuming phone is somewhat upright in pocket
        val tempYPhone = Triple(0.0, 1.0, 0.0)

        // Y_bike = Z_bike x tempYPhone
        var yBike = crossProduct(zBike, tempYPhone)
        if (magnitude(yBike) < 0.1) {
            // Handle edge case where Z_bike is parallel to phone Y
            yBike = crossProduct(zBike, Triple(1.0, 0.0, 0.0))
        }
        yBike = normalize(yBike)

        // X_bike = Y_bike x Z_bike
        val xBike = normalize(crossProduct(yBike, zBike))

        rotationMatrix = arrayOf(
            doubleArrayOf(xBike.first, xBike.second, xBike.third),
            doubleArrayOf(yBike.first, yBike.second, yBike.third),
            doubleArrayOf(zBike.first, zBike.second, zBike.third)
        )
        isCalibrated = true
        calibrationBuffer.clear()
    }

    fun transform(phoneX: Double, phoneY: Double, phoneZ: Double): Triple<Double, Double, Double> {
        if (rotationMatrix == null) return Triple(phoneX, phoneY, phoneZ)

        val mat = rotationMatrix!!
        val bikeX = mat[0][0] * phoneX + mat[0][1] * phoneY + mat[0][2] * phoneZ
        val bikeY = mat[1][0] * phoneX + mat[1][1] * phoneY + mat[1][2] * phoneZ
        val bikeZ = mat[2][0] * phoneX + mat[2][1] * phoneY + mat[2][2] * phoneZ

        return Triple(bikeX, bikeY, bikeZ)
    }

    fun reset() {
        isCalibrated = false
        rotationMatrix = null
        calibrationBuffer.clear()
    }

    private fun normalize(v: Triple<Double, Double, Double>): Triple<Double, Double, Double> {
        val mag = magnitude(v)
        return if (mag == 0.0) v else Triple(v.first / mag, v.second / mag, v.third / mag)
    }

    private fun magnitude(v: Triple<Double, Double, Double>): Double {
        return sqrt(v.first * v.first + v.second * v.second + v.third * v.third)
    }

    private fun crossProduct(a: Triple<Double, Double, Double>, b: Triple<Double, Double, Double>): Triple<Double, Double, Double> {
        return Triple(
            a.second * b.third - a.third * b.second,
            a.third * b.first - a.first * b.third,
            a.first * b.second - a.second * b.first
        )
    }
}

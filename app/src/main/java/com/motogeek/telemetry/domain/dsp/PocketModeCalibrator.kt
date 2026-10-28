package com.motogeek.telemetry.domain.dsp

import kotlin.math.sqrt

enum class CalibrationState {
    WAITING, GRAVITY_LOCKED, LOCKED
}

class PocketModeCalibrator {

    var state: CalibrationState = CalibrationState.WAITING
        private set

    var progress: Double = 0.0
        private set

    val isCalibrated: Boolean
        get() = state == CalibrationState.GRAVITY_LOCKED || state == CalibrationState.LOCKED

    private var rotationMatrix: Array<DoubleArray>? = null

    private val accelWindow = mutableListOf<Triple<Double, Double, Double>>()
    private val GRAVITY_WINDOW_SIZE = 150
    private var totalSamplesProcessed = 0L

    private var accumulatedForwardAccel = Triple(0.0, 0.0, 0.0)
    private var accumulatedEvidenceFrames = 0
    private val REQUIRED_HEADING_FRAMES = 300

    private var currentUpVector = Triple(0.0, 0.0, 1.0)
    private var currentForwardVector = Triple(1.0, 0.0, 0.0)
    
    private var gpsSpeedMs = 0.0
    private var gpsLongAccelMs2 = 0.0
    private var gpsYawRateDegSec = 0.0

    fun processImuSample(timestampNanos: Long, ax: Double, ay: Double, az: Double) {
        totalSamplesProcessed++

        if (state == CalibrationState.WAITING) {
            accelWindow.add(Triple(ax, ay, az))
            if (accelWindow.size > GRAVITY_WINDOW_SIZE) {
                accelWindow.removeAt(0)
            }
            progress = (accelWindow.size.toDouble() / GRAVITY_WINDOW_SIZE).coerceIn(0.0, 1.0)

            if (accelWindow.size == GRAVITY_WINDOW_SIZE) {
                tryGravityLock()
            }
            if (state == CalibrationState.WAITING && totalSamplesProcessed >= 1000) {
                forceGravityLock()
            }
        } else if (state == CalibrationState.GRAVITY_LOCKED) {
            progress = 1.0
            if (kotlin.math.abs(gpsLongAccelMs2) > 1.0 && kotlin.math.abs(gpsYawRateDegSec) < 3.0 && gpsSpeedMs > 3.0) {
                val gravityProj = currentUpVector
                val dotGravity = ax * gravityProj.first + ay * gravityProj.second + az * gravityProj.third
                val horizX = ax - dotGravity * gravityProj.first
                val horizY = ay - dotGravity * gravityProj.second
                val horizZ = az - dotGravity * gravityProj.third
                
                val sign = if (gpsLongAccelMs2 > 0) 1.0 else -1.0
                accumulatedForwardAccel = Triple(
                    accumulatedForwardAccel.first + horizX * sign,
                    accumulatedForwardAccel.second + horizY * sign,
                    accumulatedForwardAccel.third + horizZ * sign
                )
                accumulatedEvidenceFrames++

                if (accumulatedEvidenceFrames >= REQUIRED_HEADING_FRAMES) {
                    lockHeading()
                }
            }
        }
    }

    fun processGpsSample(timestampMs: Long, speedMs: Double, longAccelMs2: Double, yawRateDegSec: Double) {
        this.gpsSpeedMs = speedMs.takeIf { it.isFinite() } ?: 0.0
        this.gpsLongAccelMs2 = longAccelMs2.takeIf { it.isFinite() } ?: 0.0
        this.gpsYawRateDegSec = yawRateDegSec.takeIf { it.isFinite() } ?: 0.0
    }

    private fun tryGravityLock() {
        var sumX = 0.0
        var sumY = 0.0
        var sumZ = 0.0
        for (v in accelWindow) {
            sumX += v.first
            sumY += v.second
            sumZ += v.third
        }
        val count = accelWindow.size.toDouble()
        val meanX = (sumX / count).takeIf { it.isFinite() } ?: 0.0
        val meanY = (sumY / count).takeIf { it.isFinite() } ?: 0.0
        val meanZ = (sumZ / count).takeIf { it.isFinite() } ?: 0.0

        val meanMag = magnitude(Triple(meanX, meanY, meanZ))
        if (kotlin.math.abs(meanMag - 9.80665) > 0.8) return

        var varX = 0.0
        var varY = 0.0
        var varZ = 0.0
        for (v in accelWindow) {
            varX += (v.first - meanX) * (v.first - meanX)
            varY += (v.second - meanY) * (v.second - meanY)
            varZ += (v.third - meanZ) * (v.third - meanZ)
        }
        val stdX = sqrt(varX / count).takeIf { it.isFinite() } ?: 0.0
        val stdY = sqrt(varY / count).takeIf { it.isFinite() } ?: 0.0
        val stdZ = sqrt(varZ / count).takeIf { it.isFinite() } ?: 0.0

        if (stdX < 0.35 && stdY < 0.35 && stdZ < 0.35) {
            lockGravity(meanX, meanY, meanZ)
        }
    }

    private fun forceGravityLock() {
        if (accelWindow.isEmpty()) return
        var sumX = 0.0
        var sumY = 0.0
        var sumZ = 0.0
        for (v in accelWindow) {
            sumX += v.first
            sumY += v.second
            sumZ += v.third
        }
        val count = accelWindow.size.toDouble()
        val meanX = (sumX / count).takeIf { it.isFinite() } ?: 0.0
        val meanY = (sumY / count).takeIf { it.isFinite() } ?: 0.0
        val meanZ = (sumZ / count).takeIf { it.isFinite() } ?: 0.0
        lockGravity(meanX, meanY, meanZ)
    }

    private fun lockGravity(meanX: Double, meanY: Double, meanZ: Double) {
        val up = normalize(Triple(meanX, meanY, meanZ))
        if (magnitude(up) == 0.0) return

        currentUpVector = up

        val tempYPhone = Triple(0.0, 1.0, 0.0)
        var yBike = crossProduct(currentUpVector, tempYPhone)
        if (magnitude(yBike) < 0.1) {
            yBike = crossProduct(currentUpVector, Triple(1.0, 0.0, 0.0))
        }
        yBike = normalize(yBike)
        val forward = normalize(crossProduct(yBike, currentUpVector))

        currentForwardVector = forward
        updateRotationMatrix(forward, yBike, up)
        state = CalibrationState.GRAVITY_LOCKED
        progress = 1.0
    }

    private fun lockHeading() {
        val forwardGuess = normalize(accumulatedForwardAccel)
        if (magnitude(forwardGuess) > 0.0) {
            val yBike = normalize(crossProduct(currentUpVector, forwardGuess))
            if (magnitude(yBike) > 0.0) {
                val finalForward = normalize(crossProduct(yBike, currentUpVector))
                currentForwardVector = finalForward
                updateRotationMatrix(finalForward, yBike, currentUpVector)
                state = CalibrationState.LOCKED
            }
        }
    }

    private fun updateRotationMatrix(xBike: Triple<Double, Double, Double>, yBike: Triple<Double, Double, Double>, zBike: Triple<Double, Double, Double>) {
        rotationMatrix = arrayOf(
            doubleArrayOf(xBike.first, xBike.second, xBike.third),
            doubleArrayOf(yBike.first, yBike.second, yBike.third),
            doubleArrayOf(zBike.first, zBike.second, zBike.third)
        )
    }

    fun transform(phoneX: Double, phoneY: Double, phoneZ: Double): Triple<Double, Double, Double> {
        val mat = rotationMatrix
        if (mat == null) return Triple(phoneX, phoneY, phoneZ)

        val bikeX = mat[0][0] * phoneX + mat[0][1] * phoneY + mat[0][2] * phoneZ
        val bikeY = mat[1][0] * phoneX + mat[1][1] * phoneY + mat[1][2] * phoneZ
        val bikeZ = mat[2][0] * phoneX + mat[2][1] * phoneY + mat[2][2] * phoneZ

        return Triple(bikeX, bikeY, bikeZ)
    }

    fun reset() {
        state = CalibrationState.WAITING
        progress = 0.0
        rotationMatrix = null
        accelWindow.clear()
        totalSamplesProcessed = 0L
        accumulatedForwardAccel = Triple(0.0, 0.0, 0.0)
        accumulatedEvidenceFrames = 0
        gpsSpeedMs = 0.0
        gpsLongAccelMs2 = 0.0
        gpsYawRateDegSec = 0.0
    }

    private fun normalize(v: Triple<Double, Double, Double>): Triple<Double, Double, Double> {
        val mag = magnitude(v)
        return if (mag == 0.0 || !mag.isFinite()) Triple(0.0, 0.0, 0.0) else Triple(v.first / mag, v.second / mag, v.third / mag)
    }

    private fun magnitude(v: Triple<Double, Double, Double>): Double {
        val mag = sqrt(v.first * v.first + v.second * v.second + v.third * v.third)
        return mag.takeIf { it.isFinite() } ?: 0.0
    }

    private fun crossProduct(a: Triple<Double, Double, Double>, b: Triple<Double, Double, Double>): Triple<Double, Double, Double> {
        val x = a.second * b.third - a.third * b.second
        val y = a.third * b.first - a.first * b.third
        val z = a.first * b.second - a.second * b.first
        return Triple(
            x.takeIf { it.isFinite() } ?: 0.0,
            y.takeIf { it.isFinite() } ?: 0.0,
            z.takeIf { it.isFinite() } ?: 0.0
        )
    }
}

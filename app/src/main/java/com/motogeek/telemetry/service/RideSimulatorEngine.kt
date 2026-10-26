package com.motogeek.telemetry.service

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlin.math.PI
import kotlin.math.sin

class RideSimulatorEngine {
    private val _simState = MutableStateFlow(LiveTelemetryState(isRiding = true))
    val simState: StateFlow<LiveTelemetryState> = _simState.asStateFlow()

    private var isRunning = false

    suspend fun startSimulation() {
        isRunning = true
        var t = 0.0
        var accumulatedDistance = 0.0

        while (isRunning) {
            // Synthetic mountain-pass logic (10Hz for simplicity, but representing high freq)
            val dt = 0.1
            t += dt

            // Simulated track sections using sine waves
            val trackPhase = (t % 60) / 60.0 * 2 * PI // 60-second lap

            // Lean angle: Deep left and right corners up to 49 degrees
            val leanTarget = sin(trackPhase * 3) * 50.0
            // Add some noise
            val leanAngle = leanTarget + (Math.random() - 0.5) * 2.0

            // Speed: Speeds up on straights, slows in corners
            val speedTarget = 100.0 + cos(trackPhase * 3) * 50.0
            val speed = speedTarget.coerceAtLeast(30.0)

            // G-Forces
            // Lateral G is roughly proportional to lean angle
            val lateralG = (leanAngle / 45.0) * 1.0 + (Math.random() - 0.5) * 0.1

            // Longitudinal G: braking entering corners (lean increasing), accelerating exiting
            // Roughly derivative of speed
            val speedDerivative = -sin(trackPhase * 3) * 50.0 * 3
            val longitudinalG = (speedDerivative / 50.0).coerceIn(-1.2, 0.8)

            accumulatedDistance += (speed / 3.6) * dt / 1000.0 // km

            _simState.update { current ->
                val newMaxLeft = if (leanAngle < 0) maxOf(current.peakLeanLeftDeg, kotlin.math.abs(leanAngle)) else current.peakLeanLeftDeg
                val newMaxRight = if (leanAngle > 0) maxOf(current.peakLeanRightDeg, leanAngle) else current.peakLeanRightDeg

                // Simplified corner counting logic for demo
                val leftCorners = current.leftCorners + if (leanAngle < -40 && current.leanAngleDeg >= -40) 1 else 0
                val rightCorners = current.rightCorners + if (leanAngle > 40 && current.leanAngleDeg <= 40) 1 else 0

                current.copy(
                    leanAngleDeg = leanAngle,
                    currentSpeedKmh = speed,
                    longitudinalG = longitudinalG,
                    lateralG = lateralG,
                    distanceKm = accumulatedDistance,
                    durationSeconds = t.toLong(),
                    peakLeanLeftDeg = newMaxLeft,
                    peakLeanRightDeg = newMaxRight,
                    leftCorners = leftCorners,
                    rightCorners = rightCorners,
                    calibrationStatus = "SIMULATOR",
                    isRiding = true
                )
            }

            delay(100) // 10Hz update rate
        }
    }

    fun stopSimulation() {
        isRunning = false
        _simState.update { it.copy(isRiding = false) }
    }

    // Quick helper for cosine since it's not imported above
    private fun cos(x: Double) = kotlin.math.cos(x)
}

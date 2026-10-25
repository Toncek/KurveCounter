package com.motogeek.telemetry.service

data class LiveTelemetryState(
    val isRiding: Boolean = false,
    val leanAngleDeg: Double = 0.0,
    val peakLeanLeftDeg: Double = 0.0,
    val peakLeanRightDeg: Double = 0.0,
    val currentSpeedKmh: Double = 0.0,
    val averageSpeedKmh: Double = 0.0,
    val longitudinalG: Double = 0.0,
    val lateralG: Double = 0.0,
    val leftCorners: Int = 0,
    val rightCorners: Int = 0,
    val calibrationStatus: String = "CALIBRATING POCKET MODE",
    val distanceKm: Double = 0.0,
    val durationSeconds: Long = 0L
)

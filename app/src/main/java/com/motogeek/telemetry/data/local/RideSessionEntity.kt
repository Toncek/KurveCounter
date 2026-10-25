package com.motogeek.telemetry.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

@Serializable
@Entity(
    tableName = "ride_sessions",
    foreignKeys = [
        ForeignKey(
            entity = MotorcycleEntity::class,
            parentColumns = ["id"],
            childColumns = ["motorcycleId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("motorcycleId")]
)
data class RideSessionEntity(
    @PrimaryKey(autoGenerate = true)
    val rideId: Long = 0,
    val motorcycleId: String,
    val startTimeEpochMs: Long,
    val endTimeEpochMs: Long,
    val distanceKm: Double,
    val avgSpeedKmh: Double,
    val maxSpeedKmh: Double,
    val maxLeanLeftDeg: Double,
    val maxLeanRightDeg: Double,
    val maxLateralG: Double,
    val maxBrakingG: Double,
    val maxAccelG: Double,
    val totalCornersCount: Int,
    val leftCornersCount: Int,
    val rightCornersCount: Int,
    val maxTurnInRateDegPerSec: Double,
    val trailBrakingScorePct: Double,
    val avgCornerScore: Double,
    val elevationGainMeters: Double,
    val ridingStyleBadge: String
)

package com.motogeek.telemetry.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

@Serializable
@Entity(
    tableName = "telemetry_track_points",
    foreignKeys = [
        ForeignKey(
            entity = RideSessionEntity::class,
            parentColumns = ["rideId"],
            childColumns = ["rideId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("rideId")]
)
data class TelemetryTrackPointEntity(
    @PrimaryKey(autoGenerate = true)
    val pointId: Long = 0,
    val rideId: Long,
    val timestampMs: Long,
    val latitude: Double,
    val longitude: Double,
    val speedKmh: Double,
    val leanAngleDeg: Double,
    val longitudinalG: Double,
    val lateralG: Double,
    val altitudeMeters: Double
)

package com.motogeek.telemetry.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

@Serializable
@Entity(
    tableName = "corner_events",
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
data class CornerEventEntity(
    @PrimaryKey(autoGenerate = true)
    val cornerId: Long = 0,
    val rideId: Long,
    val sequenceNumber: Int,
    val timestampMs: Long,
    val direction: String,
    val entrySpeedKmh: Double,
    val apexLeanDeg: Double,
    val peakLateralG: Double,
    val peakBrakingG: Double,
    val exitAccelG: Double,
    val maxRollRateDegPerSec: Double,
    val cornerScore: Double,
    val latitude: Double,
    val longitude: Double
)

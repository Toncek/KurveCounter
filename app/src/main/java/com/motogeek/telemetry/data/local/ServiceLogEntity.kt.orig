package com.motogeek.telemetry.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

@Serializable
@Entity(
    tableName = "service_logs",
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
data class ServiceLogEntity(
    @PrimaryKey
    val logId: String,
    val motorcycleId: String,
    val taskId: String,
    val performedAtIsoDate: String,
    val odometerAtServiceKm: Double,
    val costEur: Double,
    val performedBy: String,
    val notes: String
)

package com.motogeek.telemetry.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

@Serializable
@Entity(
    tableName = "part_logs",
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
data class PartLogEntity(
    @PrimaryKey(autoGenerate = true)
    val partId: Long = 0,
    val motorcycleId: String,
    val dateIso: String,
    val odometerKm: Double,
    val partName: String,
    val brand: String,
    val partNumber: String,
    val cost: Double,
    val notes: String
)

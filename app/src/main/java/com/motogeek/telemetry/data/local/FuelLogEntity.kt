package com.motogeek.telemetry.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

@Serializable
@Entity(
    tableName = "fuel_logs",
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
data class FuelLogEntity(
    @PrimaryKey(autoGenerate = true)
    val fuelId: Long = 0,
    val motorcycleId: String,
    val dateIso: String,
    val odometerKm: Double,
    val liters: Double,
    val pricePerLiter: Double,
    val totalCost: Double,
    val isFullTank: Boolean,
    val notes: String
)

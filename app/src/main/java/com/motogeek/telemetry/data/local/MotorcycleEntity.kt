package com.motogeek.telemetry.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

@Serializable
@Entity(tableName = "motorcycles")
data class MotorcycleEntity(
    @PrimaryKey
    val id: String,
    val make: String,
    val model: String,
    val year: Int,
    val engineDisplacementCc: Int,
    val baseOdometerKm: Double,
    val accumulatedTrackedKm: Double,
    val manualAdjustmentOffsetKm: Double,
    val technicalSpecsJson: String,
    val isActive: Boolean,
    val name: String = "",
    val type: String = "MotorCycle",
    val startDateIso: String = "23/12/2024",
    val buyingPrice: Double = 0.0,
    val currencySymbol: String = "฿"
) {
    val totalOdometerKm: Double
        get() = baseOdometerKm + accumulatedTrackedKm + manualAdjustmentOffsetKm
}

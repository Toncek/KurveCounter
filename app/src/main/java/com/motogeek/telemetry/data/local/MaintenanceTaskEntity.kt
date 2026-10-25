package com.motogeek.telemetry.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import kotlinx.serialization.Serializable

@Serializable
@Entity(
    tableName = "maintenance_tasks",
    primaryKeys = ["taskId", "motorcycleId"],
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
data class MaintenanceTaskEntity(
    val taskId: String,
    val motorcycleId: String,
    val category: String,
    val taskName: String,
    val actionType: String,
    val firstBreakInAtKm: Double?,
    val intervalKm: Double?,
    val intervalMonths: Int?,
    val warningThresholdKm: Double?,
    val warningThresholdDays: Int?,
    val oemPartNumbersJson: String,
    val notes: String
)

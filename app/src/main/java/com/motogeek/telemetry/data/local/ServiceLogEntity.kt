package com.motogeek.telemetry.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
data class ServiceChecklistItem(
    val name: String,
    val status: String // "CHECKED" or "WARNING" or "INSPECT"
)

val DefaultServiceChecklistItems = listOf(
    "Battery System", "Light System", "Fuel System", "Engine Oil", "Engine Oil Filter",
    "Air Filter", "Spark Plug", "Valve Clearance", "Idle RPM", "Lever: Clutch, Brake",
    "Brake Pads", "Throttle", "Brake Oil", "Fork Oil", "Bearings", "Tire Air Pressure"
).map { ServiceChecklistItem(it, "INSPECT") }

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
    val taskId: String = "GENERAL_SERVICE",
    val performedAtIsoDate: String,
    val odometerAtServiceKm: Double,
    val costEur: Double,
    val performedBy: String,
    val notes: String,
    val serviceTitle: String = "Maintenance Service",
    val locationOrShop: String = "Home",
    val limitKm: Double = 5000.0,
    val servicedAtKm: Double = odometerAtServiceKm,
    val endKm: Double = servicedAtKm + limitKm,
    val checklistItemsJson: String = Json.encodeToString(DefaultServiceChecklistItems)
)

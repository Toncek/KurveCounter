package com.motogeek.telemetry.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.UUID

@Serializable
data class ServiceChecklistItem(
    val name: String,
    val status: String // "CHECKED", "WARNING", "SKIPPED", or "INSPECT"
)

val DefaultServiceChecklistNames = listOf(
    "Engine Oil", "Engine Oil Filter", "Air Filter", "Spark Plugs",
    "Drive Chain & Sprockets", "Brake Pads Front/Rear", "Brake Fluid", "Coolant / Radiator",
    "Tire Pressure & Tread", "Clutch & Throttle Freeplay", "Steering Head Bearings",
    "Front Fork Seals & Oil", "Rear Shock Absorber", "Battery & Charging System",
    "Lights & Indicators", "Bolts & Fasteners Torque"
)

val DefaultServiceChecklistItems = DefaultServiceChecklistNames.map {
    ServiceChecklistItem(it, "CHECKED")
}

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
    val logId: String = UUID.randomUUID().toString(),
    val motorcycleId: String,
    val taskId: String = "GENERAL_SERVICE",
    val serviceTitle: String = "Periodic Maintenance",
    val locationOrShop: String = "Home Garage",
    val performedAtIsoDate: String,
    val odometerAtServiceKm: Double,
    val servicedAtKm: Double = odometerAtServiceKm,
    val limitKm: Double = 5000.0,
    val endKm: Double = servicedAtKm + limitKm,
    val costEur: Double = 0.0,
    val performedBy: String = "Self",
    val notes: String = "",
    val checklistItemsJson: String = "[]"
) {
    fun parseChecklist(): List<ServiceChecklistItem> {
        return try {
            if (checklistItemsJson.isBlank() || checklistItemsJson == "[]") {
                DefaultServiceChecklistItems
            } else {
                val json = Json { ignoreUnknownKeys = true; isLenient = true }
                json.decodeFromString<List<ServiceChecklistItem>>(checklistItemsJson)
            }
        } catch (_: Exception) {
            DefaultServiceChecklistItems
        }
    }
}

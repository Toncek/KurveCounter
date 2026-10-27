package com.motogeek.telemetry.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable
import java.util.UUID

@Serializable
@Entity(
    tableName = "app_logs",
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
data class AppLogEntity(
    @PrimaryKey
    val logId: String = UUID.randomUUID().toString(),
    val motorcycleId: String,
    val timestampEpochMs: Long = System.currentTimeMillis(),
    val category: String, // "RIDE", "SERVICE", "FUEL", "PARTS", "SYSTEM"
    val title: String,
    val details: String,
    val iconKey: String = "INFO"
)

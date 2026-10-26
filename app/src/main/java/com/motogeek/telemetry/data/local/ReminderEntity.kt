package com.motogeek.telemetry.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

@Serializable
@Entity(
    tableName = "reminders",
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
data class ReminderEntity(
    @PrimaryKey(autoGenerate = true)
    val reminderId: Long = 0,
    val motorcycleId: String,
    val title: String,
    val targetOdometerKm: Double? = null,
    val targetDateIso: String? = null,
    val isCompleted: Boolean = false
)

package com.motogeek.telemetry.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        MotorcycleEntity::class,
        RideSessionEntity::class,
        CornerEventEntity::class,
        TelemetryTrackPointEntity::class,
        MaintenanceTaskEntity::class,
        ServiceLogEntity::class,
        FuelLogEntity::class,
        PartLogEntity::class,
        ReminderEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class MotoGeekDatabase : RoomDatabase() {
    abstract fun motoGeekDao(): MotoGeekDao

    companion object {
        @Volatile
        private var INSTANCE: MotoGeekDatabase? = null

        fun getDatabase(context: Context): MotoGeekDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    MotoGeekDatabase::class.java,
                    "motogeek_database"
                ).fallbackToDestructiveMigration().build()
                INSTANCE = instance
                instance
            }
        }
    }
}

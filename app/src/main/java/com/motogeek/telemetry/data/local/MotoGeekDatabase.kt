package com.motogeek.telemetry.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.room.migration.Migration

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
        ReminderEntity::class,
        AppLogEntity::class
    ],
    version = 2,
    exportSchema = false
)
abstract class MotoGeekDatabase : RoomDatabase() {
    abstract fun motoGeekDao(): MotoGeekDao

    companion object {
        @Volatile
        private var INSTANCE: MotoGeekDatabase? = null


        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                try { db.execSQL("ALTER TABLE motorcycles ADD COLUMN name TEXT NOT NULL DEFAULT ''") } catch (_: Exception) {}
                try { db.execSQL("ALTER TABLE service_logs ADD COLUMN notes TEXT NOT NULL DEFAULT ''") } catch (_: Exception) {}
                try { db.execSQL("ALTER TABLE ride_sessions ADD COLUMN notes TEXT") } catch (_: Exception) {}
            }
        }

        fun getDatabase(context: Context): MotoGeekDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    MotoGeekDatabase::class.java,
                    "motogeek_database"
                )
                    .addMigrations(MIGRATION_1_2)
                    .fallbackToDestructiveMigrationOnDowngrade()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}

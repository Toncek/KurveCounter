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
        AppLogEntity::class,
        CloudSyncStateEntity::class
    ],
    version = 3,
    exportSchema = false
)
abstract class MotoGeekDatabase : RoomDatabase() {
    abstract fun motoGeekDao(): MotoGeekDao

    companion object {
        @Volatile
        private var INSTANCE: MotoGeekDatabase? = null

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS cloud_sync_state (accountEmail TEXT NOT NULL PRIMARY KEY, stateJson TEXT NOT NULL)")
            }
        }


        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                try { db.execSQL("ALTER TABLE motorcycles ADD COLUMN name TEXT NOT NULL DEFAULT ''") } catch (_: Exception) {}
                try { db.execSQL("ALTER TABLE service_logs ADD COLUMN notes TEXT NOT NULL DEFAULT ''") } catch (_: Exception) {}
                try { db.execSQL("ALTER TABLE ride_sessions ADD COLUMN notes TEXT") } catch (_: Exception) {}
                db.execSQL("CREATE TABLE IF NOT EXISTS app_logs (logId TEXT NOT NULL, motorcycleId TEXT NOT NULL, timestampEpochMs INTEGER NOT NULL, category TEXT NOT NULL, title TEXT NOT NULL, details TEXT NOT NULL, iconKey TEXT NOT NULL, PRIMARY KEY(logId), FOREIGN KEY(motorcycleId) REFERENCES motorcycles(id) ON UPDATE NO ACTION ON DELETE CASCADE)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_app_logs_motorcycleId ON app_logs (motorcycleId)")
            }
        }

        fun getDatabase(context: Context): MotoGeekDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    MotoGeekDatabase::class.java,
                    "motogeek_database"
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                    .fallbackToDestructiveMigrationOnDowngrade()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}

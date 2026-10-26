package com.motogeek.telemetry.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface MotoGeekDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMotorcycle(motorcycle: MotorcycleEntity)

    @Update
    suspend fun updateMotorcycle(motorcycle: MotorcycleEntity)

    @Query("DELETE FROM motorcycles WHERE id = :id")
    suspend fun deleteMotorcycle(id: String)

    @Query("SELECT * FROM motorcycles")
    fun getAllMotorcycles(): Flow<List<MotorcycleEntity>>

    @Query("SELECT * FROM motorcycles WHERE isActive = 1 LIMIT 1")
    suspend fun getActiveMotorcycle(): MotorcycleEntity?

    @Query("SELECT * FROM motorcycles WHERE isActive = 1 LIMIT 1")
    fun getActiveMotorcycleFlow(): Flow<MotorcycleEntity?>

    @Query("UPDATE motorcycles SET manualAdjustmentOffsetKm = :offset WHERE id = :id")
    suspend fun updateMotorcycleManualOffset(id: String, offset: Double)

    @Query("UPDATE motorcycles SET accumulatedTrackedKm = accumulatedTrackedKm + :distanceKm WHERE id = :id")
    suspend fun addAccumulatedTrackedKm(id: String, distanceKm: Double)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRideSession(rideSession: RideSessionEntity): Long

    @Query("SELECT * FROM ride_sessions WHERE rideId = :rideId")
    suspend fun getRideSessionById(rideId: Long): RideSessionEntity?

    @Query("SELECT * FROM ride_sessions ORDER BY startTimeEpochMs DESC")
    fun getAllRideSessions(): Flow<List<RideSessionEntity>>

    @Query("DELETE FROM ride_sessions WHERE rideId = :rideId")
    suspend fun deleteRideSession(rideId: Long)

    @Query("UPDATE ride_sessions SET name = :name, notes = :notes WHERE rideId = :rideId")
    suspend fun updateRideSessionDetails(rideId: Long, name: String?, notes: String?)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCornerEvents(cornerEvents: List<CornerEventEntity>)

    @Query("SELECT * FROM corner_events WHERE rideId = :rideId ORDER BY sequenceNumber ASC")
    fun getCornerEventsForRide(rideId: Long): Flow<List<CornerEventEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTelemetryTrackPoints(trackPoints: List<TelemetryTrackPointEntity>)

    @Query("SELECT * FROM telemetry_track_points WHERE rideId = :rideId ORDER BY timestampMs ASC")
    fun getTelemetryTrackPointsForRide(rideId: Long): Flow<List<TelemetryTrackPointEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMaintenanceTask(task: MaintenanceTaskEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMaintenanceTasks(tasks: List<MaintenanceTaskEntity>)

    @Query("DELETE FROM maintenance_tasks WHERE taskId = :taskId AND motorcycleId = :motorcycleId")
    suspend fun deleteMaintenanceTask(taskId: String, motorcycleId: String)

    @Query("SELECT * FROM maintenance_tasks WHERE motorcycleId = :motorcycleId")
    suspend fun getMaintenanceTasksForMotorcycle(motorcycleId: String): List<MaintenanceTaskEntity>

    @Query("SELECT * FROM maintenance_tasks WHERE motorcycleId = :motorcycleId")
    fun getMaintenanceTasksForMotorcycleFlow(motorcycleId: String): Flow<List<MaintenanceTaskEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertServiceLog(serviceLog: ServiceLogEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertServiceLogs(serviceLogs: List<ServiceLogEntity>)

    @Query("DELETE FROM service_logs WHERE logId = :logId")
    suspend fun deleteServiceLog(logId: String)

    @Query("SELECT * FROM service_logs WHERE motorcycleId = :motorcycleId")
    suspend fun getServiceLogsForMotorcycle(motorcycleId: String): List<ServiceLogEntity>

    @Query("SELECT * FROM service_logs WHERE motorcycleId = :motorcycleId ORDER BY performedAtIsoDate DESC")
    fun getServiceLogsForMotorcycleFlow(motorcycleId: String): Flow<List<ServiceLogEntity>>
}

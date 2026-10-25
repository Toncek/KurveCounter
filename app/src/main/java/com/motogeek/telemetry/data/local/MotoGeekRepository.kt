package com.motogeek.telemetry.data.local

import kotlinx.coroutines.flow.Flow

class MotoGeekRepository(private val dao: MotoGeekDao) {
    fun getAllMotorcycles(): Flow<List<MotorcycleEntity>> = dao.getAllMotorcycles()

    suspend fun getActiveMotorcycle(): MotorcycleEntity? = dao.getActiveMotorcycle()

    fun getActiveMotorcycleFlow(): Flow<MotorcycleEntity?> = dao.getActiveMotorcycleFlow()

    suspend fun insertMotorcycle(motorcycle: MotorcycleEntity) {
        dao.insertMotorcycle(motorcycle)
    }

    suspend fun updateMotorcycle(motorcycle: MotorcycleEntity) {
        dao.updateMotorcycle(motorcycle)
    }

    suspend fun updateOdometerManualOffset(motorcycleId: String, userTargetOdometerKm: Double, baseOdometerKm: Double, accumulatedTrackedKm: Double) {
        val offset = userTargetOdometerKm - (baseOdometerKm + accumulatedTrackedKm)
        dao.updateMotorcycleManualOffset(motorcycleId, offset)
    }

    suspend fun addAccumulatedTrackedKm(motorcycleId: String, distanceKm: Double) {
        dao.addAccumulatedTrackedKm(motorcycleId, distanceKm)
    }

    fun getAllRideSessions(): Flow<List<RideSessionEntity>> = dao.getAllRideSessions()

    suspend fun getRideSessionById(rideId: Long): RideSessionEntity? = dao.getRideSessionById(rideId)

    suspend fun insertRideSession(rideSession: RideSessionEntity): Long {
        return dao.insertRideSession(rideSession)
    }

    suspend fun insertCornerEvents(cornerEvents: List<CornerEventEntity>) {
        dao.insertCornerEvents(cornerEvents)
    }

    fun getCornerEventsForRide(rideId: Long): Flow<List<CornerEventEntity>> = dao.getCornerEventsForRide(rideId)

    suspend fun insertTelemetryTrackPoints(trackPoints: List<TelemetryTrackPointEntity>) {
        dao.insertTelemetryTrackPoints(trackPoints)
    }

    fun getTelemetryTrackPointsForRide(rideId: Long): Flow<List<TelemetryTrackPointEntity>> = dao.getTelemetryTrackPointsForRide(rideId)

    suspend fun insertMaintenanceTasks(tasks: List<MaintenanceTaskEntity>) {
        dao.insertMaintenanceTasks(tasks)
    }

    suspend fun getMaintenanceTasksForMotorcycle(motorcycleId: String): List<MaintenanceTaskEntity> {
        return dao.getMaintenanceTasksForMotorcycle(motorcycleId)
    }

    fun getMaintenanceTasksForMotorcycleFlow(motorcycleId: String): Flow<List<MaintenanceTaskEntity>> {
        return dao.getMaintenanceTasksForMotorcycleFlow(motorcycleId)
    }

    suspend fun insertServiceLog(serviceLog: ServiceLogEntity) {
        dao.insertServiceLog(serviceLog)
    }

    suspend fun insertServiceLogs(serviceLogs: List<ServiceLogEntity>) {
        dao.insertServiceLogs(serviceLogs)
    }

    suspend fun getServiceLogsForMotorcycle(motorcycleId: String): List<ServiceLogEntity> {
        return dao.getServiceLogsForMotorcycle(motorcycleId)
    }

    fun getServiceLogsForMotorcycleFlow(motorcycleId: String): Flow<List<ServiceLogEntity>> {
        return dao.getServiceLogsForMotorcycleFlow(motorcycleId)
    }
}

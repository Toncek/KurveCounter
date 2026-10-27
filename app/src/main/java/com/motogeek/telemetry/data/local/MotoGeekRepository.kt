package com.motogeek.telemetry.data.local

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

class MotoGeekRepository(val dao: MotoGeekDao) {
    fun getAllMotorcycles(): Flow<List<MotorcycleEntity>> = dao.getAllMotorcycles()

    suspend fun getActiveMotorcycle(): MotorcycleEntity? = dao.getActiveMotorcycle()

    fun getActiveMotorcycleFlow(): Flow<MotorcycleEntity?> = dao.getActiveMotorcycleFlow()

    suspend fun getMotorcycleById(id: String): MotorcycleEntity? = dao.getMotorcycleById(id)

    suspend fun clearActiveMotorcycles() {
        dao.clearActiveMotorcycles()
    }

    suspend fun selectActiveMotorcycle(motorcycleId: String) {
        dao.clearActiveMotorcycles()
        dao.setActiveMotorcycle(motorcycleId)
    }

    suspend fun insertMotorcycle(motorcycle: MotorcycleEntity) {
        dao.insertMotorcycle(motorcycle)
    }

    suspend fun updateMotorcycle(motorcycle: MotorcycleEntity) {
        dao.updateMotorcycle(motorcycle)
    }

    suspend fun deleteMotorcycle(motorcycleId: String) {
        dao.deleteMotorcycle(motorcycleId)
    }

    suspend fun updateOdometerManualOffset(motorcycleId: String, userTargetOdometerKm: Double, baseOdometerKm: Double, accumulatedTrackedKm: Double) {
        val offset = userTargetOdometerKm - (baseOdometerKm + accumulatedTrackedKm)
        dao.updateMotorcycleManualOffset(motorcycleId, offset)
    }

    suspend fun addAccumulatedTrackedKm(motorcycleId: String, distanceKm: Double) {
        dao.addAccumulatedTrackedKm(motorcycleId, distanceKm)
    }

    fun getAllRideSessions(): Flow<List<RideSessionEntity>> = dao.getAllRideSessions()

    fun getRideSessionsForMotorcycle(motorcycleId: String): Flow<List<RideSessionEntity>> =
        dao.getRideSessionsForMotorcycle(motorcycleId)

    suspend fun getRideSessionById(rideId: Long): RideSessionEntity? = dao.getRideSessionById(rideId)

    suspend fun deleteRideSession(rideId: Long) {
        dao.deleteRideSession(rideId)
    }

    suspend fun updateRideSessionDetails(rideId: Long, name: String?, notes: String?) {
        dao.updateRideSessionDetails(rideId, name, notes)
    }

    suspend fun insertRideSession(rideSession: RideSessionEntity): Long {
        return dao.insertRideSession(rideSession)
    }

    suspend fun insertCornerEvents(cornerEvents: List<CornerEventEntity>) {
        dao.insertCornerEvents(cornerEvents)
    }

    fun getCornerEventsForRide(rideId: Long): Flow<List<CornerEventEntity>> = dao.getCornerEventsForRide(rideId)

    suspend fun getCornerEventsListForRide(rideId: Long): List<CornerEventEntity> =
        dao.getCornerEventsListForRide(rideId)

    suspend fun insertTelemetryTrackPoints(trackPoints: List<TelemetryTrackPointEntity>) {
        dao.insertTelemetryTrackPoints(trackPoints)
    }

    fun getTelemetryTrackPointsForRide(rideId: Long): Flow<List<TelemetryTrackPointEntity>> =
        dao.getTelemetryTrackPointsForRide(rideId)

    suspend fun getTelemetryTrackPointsListForRide(rideId: Long): List<TelemetryTrackPointEntity> =
        dao.getTelemetryTrackPointsListForRide(rideId)

    // --- Maintenance Tasks ---
    suspend fun insertMaintenanceTask(task: MaintenanceTaskEntity) {
        dao.insertMaintenanceTask(task)
    }

    suspend fun insertMaintenanceTasks(tasks: List<MaintenanceTaskEntity>) {
        dao.insertMaintenanceTasks(tasks)
    }

    suspend fun deleteMaintenanceTask(taskId: String, motorcycleId: String) {
        dao.deleteMaintenanceTask(taskId, motorcycleId)
    }

    suspend fun getMaintenanceTasksForMotorcycle(motorcycleId: String): List<MaintenanceTaskEntity> {
        return dao.getMaintenanceTasksForMotorcycle(motorcycleId)
    }

    fun getMaintenanceTasksForMotorcycleFlow(motorcycleId: String): Flow<List<MaintenanceTaskEntity>> {
        return dao.getMaintenanceTasksForMotorcycleFlow(motorcycleId)
    }

    // --- Service Logs ---
    suspend fun insertServiceLog(serviceLog: ServiceLogEntity) {
        dao.insertServiceLog(serviceLog)
    }

    suspend fun insertServiceLogs(serviceLogs: List<ServiceLogEntity>) {
        dao.insertServiceLogs(serviceLogs)
    }

    suspend fun updateServiceLog(serviceLog: ServiceLogEntity) {
        dao.updateServiceLog(serviceLog)
    }

    suspend fun deleteServiceLog(logId: String) {
        dao.deleteServiceLog(logId)
    }

    suspend fun getServiceLogsForMotorcycle(motorcycleId: String): List<ServiceLogEntity> {
        return dao.getServiceLogsForMotorcycle(motorcycleId)
    }

    fun getServiceLogsForMotorcycleFlow(motorcycleId: String): Flow<List<ServiceLogEntity>> {
        return dao.getServiceLogsForMotorcycleFlow(motorcycleId)
    }

    // --- Fuel Logs ---
    suspend fun insertFuelLog(fuelLog: FuelLogEntity) { dao.insertFuelLog(fuelLog) }
    suspend fun updateFuelLog(fuelLog: FuelLogEntity) { dao.updateFuelLog(fuelLog) }
    suspend fun deleteFuelLog(fuelId: Long) { dao.deleteFuelLog(fuelId) }
    fun getFuelLogsForMotorcycle(motorcycleId: String): Flow<List<FuelLogEntity>> =
        dao.getFuelLogsForMotorcycle(motorcycleId)
    suspend fun getFuelLogsForMotorcycleList(motorcycleId: String): List<FuelLogEntity> =
        dao.getFuelLogsForMotorcycleList(motorcycleId)

    // --- Part Logs ---
    suspend fun insertPartLog(partLog: PartLogEntity) { dao.insertPartLog(partLog) }
    suspend fun updatePartLog(partLog: PartLogEntity) { dao.updatePartLog(partLog) }
    suspend fun deletePartLog(partId: Long) { dao.deletePartLog(partId) }
    fun getPartLogsForMotorcycle(motorcycleId: String): Flow<List<PartLogEntity>> =
        dao.getPartLogsForMotorcycle(motorcycleId)
    suspend fun getPartLogsForMotorcycleList(motorcycleId: String): List<PartLogEntity> =
        dao.getPartLogsForMotorcycleList(motorcycleId)

    // --- Reminders ---
    suspend fun insertReminder(reminder: ReminderEntity) { dao.insertReminder(reminder) }
    suspend fun updateReminder(reminder: ReminderEntity) { dao.updateReminder(reminder) }
    suspend fun deleteReminder(reminderId: Long) { dao.deleteReminder(reminderId) }
    fun getRemindersForMotorcycle(motorcycleId: String): Flow<List<ReminderEntity>> =
        dao.getRemindersForMotorcycle(motorcycleId)

    // --- Activity App Logs ---
    suspend fun logActivity(motorcycleId: String, category: String, title: String, details: String, iconKey: String = "INFO") {
        dao.insertAppLog(
            AppLogEntity(
                motorcycleId = motorcycleId,
                category = category,
                title = title,
                details = details,
                iconKey = iconKey
            )
        )
    }
    fun getAppLogsForMotorcycle(motorcycleId: String): Flow<List<AppLogEntity>> =
        dao.getAppLogsForMotorcycle(motorcycleId)
    suspend fun deleteAppLog(logId: String) { dao.deleteAppLog(logId) }
    suspend fun clearAppLogs(motorcycleId: String) { dao.clearAppLogs(motorcycleId) }

    // --- Aggregations ---
    suspend fun getTotalServiceCost(motorcycleId: String): Double? = dao.getTotalServiceCost(motorcycleId)
    suspend fun getTotalPartsCost(motorcycleId: String): Double? = dao.getTotalPartsCost(motorcycleId)
    suspend fun getTotalFuelCost(motorcycleId: String): Double? = dao.getTotalFuelCost(motorcycleId)
    fun getAvgFuelConsumption(motorcycleId: String): Flow<Double?> = dao.getAvgFuelConsumption(motorcycleId)
}

package com.motogeek.telemetry

import android.content.Context
import android.content.ContextWrapper
import com.motogeek.telemetry.data.local.*
import com.motogeek.telemetry.domain.maintenance.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.io.File
import java.util.UUID

class GuidedImportTest {

    class FakeContext : ContextWrapper(null) {
        private val tempDir = File(System.getProperty("java.io.tmpdir"), "fake_ctx_${UUID.randomUUID()}").apply { mkdirs() }
        override fun getFilesDir(): File = tempDir
        override fun getCacheDir(): File = tempDir
    }

    /**
     * In-memory FakeMotoGeekDao that faithfully simulates Room SQLite's ON CONFLICT REPLACE
     * cascade deletion behavior on motorcycles.
     *
     * In SQLite Room schema:
     *   @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertMotorcycle(motorcycle: MotorcycleEntity)
     * executes "INSERT OR REPLACE INTO motorcycles ...", which deletes the conflicting row first,
     * triggering ON DELETE CASCADE on child tables (rides, service_logs, fuel_logs, etc.).
     *
     * Calling updateMotorcycle instead performs an in-place UPDATE without cascade.
     */
    class FakeMotoGeekDao : MotoGeekDao {
        val motorcycles = LinkedHashMap<String, MotorcycleEntity>()
        val maintenanceTasks = mutableListOf<MaintenanceTaskEntity>()
        val serviceLogs = mutableListOf<ServiceLogEntity>()
        val fuelLogs = mutableListOf<FuelLogEntity>()
        val partLogs = mutableListOf<PartLogEntity>()
        val reminders = mutableListOf<ReminderEntity>()
        val rideSessions = mutableListOf<RideSessionEntity>()
        val cornerEvents = mutableListOf<CornerEventEntity>()
        val trackPoints = mutableListOf<TelemetryTrackPointEntity>()
        val appLogs = mutableListOf<AppLogEntity>()

        private var nextFuelId = 1L
        private var nextPartId = 1L
        private var nextReminderId = 1L
        private var nextRideId = 1L
        private var nextCornerId = 1L
        private var nextPointId = 1L

        private fun cascadeDeleteMotorcycle(bikeId: String) {
            motorcycles.remove(bikeId)
            maintenanceTasks.removeAll { it.motorcycleId == bikeId }
            serviceLogs.removeAll { it.motorcycleId == bikeId }
            fuelLogs.removeAll { it.motorcycleId == bikeId }
            partLogs.removeAll { it.motorcycleId == bikeId }
            reminders.removeAll { it.motorcycleId == bikeId }
            appLogs.removeAll { it.motorcycleId == bikeId }
            val removedRides = rideSessions.filter { it.motorcycleId == bikeId }
            rideSessions.removeAll { it.motorcycleId == bikeId }
            val removedRideIds = removedRides.map { it.rideId }.toSet()
            cornerEvents.removeAll { it.rideId in removedRideIds }
            trackPoints.removeAll { it.rideId in removedRideIds }
        }

        override suspend fun insertMotorcycle(motorcycle: MotorcycleEntity) {
            if (motorcycles.containsKey(motorcycle.id)) {
                // Simulating Room SQLite REPLACE cascade deletion
                cascadeDeleteMotorcycle(motorcycle.id)
            }
            motorcycles[motorcycle.id] = motorcycle
        }

        override suspend fun updateMotorcycle(motorcycle: MotorcycleEntity) {
            if (motorcycles.containsKey(motorcycle.id)) {
                motorcycles[motorcycle.id] = motorcycle
            }
        }

        override suspend fun deleteMotorcycle(id: String) {
            cascadeDeleteMotorcycle(id)
        }

        override suspend fun deleteAllMotorcycles() {
            val ids = motorcycles.keys.toList()
            ids.forEach { cascadeDeleteMotorcycle(it) }
        }

        override fun getAllMotorcycles(): Flow<List<MotorcycleEntity>> =
            flowOf(motorcycles.values.toList())

        override suspend fun getMotorcycleById(id: String): MotorcycleEntity? =
            motorcycles[id]

        override suspend fun getActiveMotorcycle(): MotorcycleEntity? =
            motorcycles.values.firstOrNull { it.isActive }

        override fun getActiveMotorcycleFlow(): Flow<MotorcycleEntity?> =
            flowOf(motorcycles.values.firstOrNull { it.isActive })

        override suspend fun clearActiveMotorcycles() {
            motorcycles.keys.toList().forEach { id ->
                motorcycles[id]?.let { motorcycles[id] = it.copy(isActive = false) }
            }
        }

        override suspend fun setActiveMotorcycle(id: String) {
            clearActiveMotorcycles()
            motorcycles[id]?.let { motorcycles[id] = it.copy(isActive = true) }
        }

        override suspend fun updateMotorcycleManualOffset(id: String, offset: Double) {
            motorcycles[id]?.let { motorcycles[id] = it.copy(manualAdjustmentOffsetKm = offset) }
        }

        override suspend fun addAccumulatedTrackedKm(id: String, distanceKm: Double) {
            motorcycles[id]?.let { motorcycles[id] = it.copy(accumulatedTrackedKm = it.accumulatedTrackedKm + distanceKm) }
        }

        override suspend fun insertRideSession(rideSession: RideSessionEntity): Long {
            val id = if (rideSession.rideId > 0L) rideSession.rideId else nextRideId++
            rideSessions.removeAll { it.rideId == id }
            rideSessions.add(rideSession.copy(rideId = id))
            return id
        }

        override suspend fun getRideSessionById(rideId: Long): RideSessionEntity? =
            rideSessions.firstOrNull { it.rideId == rideId }

        override fun getAllRideSessions(): Flow<List<RideSessionEntity>> =
            flowOf(rideSessions.sortedByDescending { it.startTimeEpochMs })

        override fun getRideSessionsForMotorcycle(motorcycleId: String): Flow<List<RideSessionEntity>> =
            flowOf(rideSessions.filter { it.motorcycleId == motorcycleId }.sortedByDescending { it.startTimeEpochMs })

        override suspend fun getRideSessionsForMotorcycleList(motorcycleId: String): List<RideSessionEntity> =
            rideSessions.filter { it.motorcycleId == motorcycleId }.sortedByDescending { it.startTimeEpochMs }

        override suspend fun deleteRideSession(rideId: Long) {
            rideSessions.removeAll { it.rideId == rideId }
            cornerEvents.removeAll { it.rideId == rideId }
            trackPoints.removeAll { it.rideId == rideId }
        }

        override suspend fun updateRideSessionDetails(rideId: Long, name: String?, notes: String?) {
            val idx = rideSessions.indexOfFirst { it.rideId == rideId }
            if (idx >= 0) {
                val r = rideSessions[idx]
                rideSessions[idx] = r.copy(name = name ?: r.name, notes = notes ?: r.notes)
            }
        }

        override suspend fun insertCornerEvents(cornerEvents: List<CornerEventEntity>) {
            cornerEvents.forEach { e ->
                val id = if (e.cornerId > 0L) e.cornerId else nextCornerId++
                this.cornerEvents.add(e.copy(cornerId = id))
            }
        }

        override fun getCornerEventsForRide(rideId: Long): Flow<List<CornerEventEntity>> =
            flowOf(cornerEvents.filter { it.rideId == rideId }.sortedBy { it.sequenceNumber })

        override suspend fun getCornerEventsListForRide(rideId: Long): List<CornerEventEntity> =
            cornerEvents.filter { it.rideId == rideId }.sortedBy { it.sequenceNumber }

        override suspend fun insertTelemetryTrackPoints(trackPoints: List<TelemetryTrackPointEntity>) {
            trackPoints.forEach { p ->
                val id = if (p.pointId > 0L) p.pointId else nextPointId++
                this.trackPoints.add(p.copy(pointId = id))
            }
        }

        override fun getTelemetryTrackPointsForRide(rideId: Long): Flow<List<TelemetryTrackPointEntity>> =
            flowOf(trackPoints.filter { it.rideId == rideId }.sortedBy { it.timestampMs })

        override suspend fun getTelemetryTrackPointsListForRide(rideId: Long): List<TelemetryTrackPointEntity> =
            trackPoints.filter { it.rideId == rideId }.sortedBy { it.timestampMs }

        override suspend fun insertMaintenanceTask(task: MaintenanceTaskEntity) {
            maintenanceTasks.removeAll { it.taskId == task.taskId && it.motorcycleId == task.motorcycleId }
            maintenanceTasks.add(task)
        }

        override suspend fun insertMaintenanceTasks(tasks: List<MaintenanceTaskEntity>) {
            tasks.forEach { insertMaintenanceTask(it) }
        }

        override suspend fun deleteMaintenanceTask(taskId: String, motorcycleId: String) {
            maintenanceTasks.removeAll { it.taskId == taskId && it.motorcycleId == motorcycleId }
        }

        override suspend fun getMaintenanceTasksForMotorcycle(motorcycleId: String): List<MaintenanceTaskEntity> =
            maintenanceTasks.filter { it.motorcycleId == motorcycleId }

        override fun getMaintenanceTasksForMotorcycleFlow(motorcycleId: String): Flow<List<MaintenanceTaskEntity>> =
            flowOf(maintenanceTasks.filter { it.motorcycleId == motorcycleId })

        override suspend fun insertServiceLog(serviceLog: ServiceLogEntity) {
            // Room primary key on service_logs is logId alone!
            serviceLogs.removeAll { it.logId == serviceLog.logId }
            serviceLogs.add(serviceLog)
        }

        override suspend fun insertServiceLogs(serviceLogs: List<ServiceLogEntity>) {
            serviceLogs.forEach { insertServiceLog(it) }
        }

        override suspend fun updateServiceLog(serviceLog: ServiceLogEntity) {
            val idx = serviceLogs.indexOfFirst { it.logId == serviceLog.logId }
            if (idx >= 0) serviceLogs[idx] = serviceLog
        }

        override suspend fun deleteServiceLog(logId: String) {
            serviceLogs.removeAll { it.logId == logId }
        }

        override suspend fun getServiceLogsForMotorcycle(motorcycleId: String): List<ServiceLogEntity> =
            serviceLogs.filter { it.motorcycleId == motorcycleId }

        override fun getServiceLogsForMotorcycleFlow(motorcycleId: String): Flow<List<ServiceLogEntity>> =
            flowOf(serviceLogs.filter { it.motorcycleId == motorcycleId }.sortedByDescending { it.performedAtIsoDate })

        override suspend fun insertFuelLog(fuelLog: FuelLogEntity) {
            val id = if (fuelLog.fuelId > 0L) fuelLog.fuelId else nextFuelId++
            fuelLogs.removeAll { it.fuelId == id }
            fuelLogs.add(fuelLog.copy(fuelId = id))
        }

        override suspend fun updateFuelLog(fuelLog: FuelLogEntity) {
            val idx = fuelLogs.indexOfFirst { it.fuelId == fuelLog.fuelId }
            if (idx >= 0) fuelLogs[idx] = fuelLog
        }

        override suspend fun deleteFuelLog(fuelId: Long) {
            fuelLogs.removeAll { it.fuelId == fuelId }
        }

        override fun getFuelLogsForMotorcycle(motorcycleId: String): Flow<List<FuelLogEntity>> =
            flowOf(fuelLogs.filter { it.motorcycleId == motorcycleId }.sortedByDescending { it.dateIso })

        override suspend fun getFuelLogsForMotorcycleList(motorcycleId: String): List<FuelLogEntity> =
            fuelLogs.filter { it.motorcycleId == motorcycleId }.sortedByDescending { it.dateIso }

        override suspend fun insertPartLog(partLog: PartLogEntity) {
            val id = if (partLog.partId > 0L) partLog.partId else nextPartId++
            partLogs.removeAll { it.partId == id }
            partLogs.add(partLog.copy(partId = id))
        }

        override suspend fun updatePartLog(partLog: PartLogEntity) {
            val idx = partLogs.indexOfFirst { it.partId == partLog.partId }
            if (idx >= 0) partLogs[idx] = partLog
        }

        override suspend fun deletePartLog(partId: Long) {
            partLogs.removeAll { it.partId == partId }
        }

        override fun getPartLogsForMotorcycle(motorcycleId: String): Flow<List<PartLogEntity>> =
            flowOf(partLogs.filter { it.motorcycleId == motorcycleId }.sortedByDescending { it.dateIso })

        override suspend fun getPartLogsForMotorcycleList(motorcycleId: String): List<PartLogEntity> =
            partLogs.filter { it.motorcycleId == motorcycleId }.sortedByDescending { it.dateIso }

        override suspend fun insertReminder(reminder: ReminderEntity) {
            val id = if (reminder.reminderId > 0L) reminder.reminderId else nextReminderId++
            reminders.removeAll { it.reminderId == id }
            reminders.add(reminder.copy(reminderId = id))
        }

        override suspend fun updateReminder(reminder: ReminderEntity) {
            val idx = reminders.indexOfFirst { it.reminderId == reminder.reminderId }
            if (idx >= 0) reminders[idx] = reminder
        }

        override suspend fun deleteReminder(reminderId: Long) {
            reminders.removeAll { it.reminderId == reminderId }
        }

        override fun getRemindersForMotorcycle(motorcycleId: String): Flow<List<ReminderEntity>> =
            flowOf(reminders.filter { it.motorcycleId == motorcycleId })

        override suspend fun getRemindersForMotorcycleList(motorcycleId: String): List<ReminderEntity> =
            reminders.filter { it.motorcycleId == motorcycleId }

        override suspend fun insertAppLog(appLog: AppLogEntity) {
            val id = if (appLog.logId.isNotBlank()) appLog.logId else UUID.randomUUID().toString()
            appLogs.removeAll { it.logId == id }
            appLogs.add(appLog.copy(logId = id))
        }

        override suspend fun insertAppLogs(appLogs: List<AppLogEntity>) {
            appLogs.forEach { insertAppLog(it) }
        }

        override fun getAppLogsForMotorcycle(motorcycleId: String): Flow<List<AppLogEntity>> =
            flowOf(appLogs.filter { it.motorcycleId == motorcycleId }.sortedByDescending { it.timestampEpochMs })

        override suspend fun getAppLogsForMotorcycleList(motorcycleId: String): List<AppLogEntity> =
            appLogs.filter { it.motorcycleId == motorcycleId }.sortedByDescending { it.timestampEpochMs }

        override suspend fun deleteAppLog(logId: String) {
            appLogs.removeAll { it.logId == logId }
        }

        override suspend fun clearAppLogs(motorcycleId: String) {
            appLogs.removeAll { it.motorcycleId == motorcycleId }
        }

        override suspend fun getTotalServiceCost(motorcycleId: String): Double? =
            serviceLogs.filter { it.motorcycleId == motorcycleId }.sumOf { it.costEur }.takeIf { it > 0.0 }

        override suspend fun getTotalPartsCost(motorcycleId: String): Double? =
            partLogs.filter { it.motorcycleId == motorcycleId }.sumOf { it.cost }.takeIf { it > 0.0 }

        override suspend fun getTotalFuelCost(motorcycleId: String): Double? =
            fuelLogs.filter { it.motorcycleId == motorcycleId }.sumOf { it.totalCost }.takeIf { it > 0.0 }

        override fun getAvgFuelConsumption(motorcycleId: String): Flow<Double?> =
            flowOf(0.0)
    }

    private fun createRideSession(
        rideId: Long = 0L,
        motorcycleId: String,
        name: String = "Test Ride",
        startTimeEpochMs: Long = 1700000000000L,
        endTimeEpochMs: Long = 1700003600000L,
        distanceKm: Double = 50.0
    ): RideSessionEntity = RideSessionEntity(
        rideId = rideId,
        motorcycleId = motorcycleId,
        startTimeEpochMs = startTimeEpochMs,
        endTimeEpochMs = endTimeEpochMs,
        distanceKm = distanceKm,
        avgSpeedKmh = 60.0,
        maxSpeedKmh = 100.0,
        maxLeanLeftDeg = 35.0,
        maxLeanRightDeg = 38.0,
        maxLateralG = 0.8,
        maxBrakingG = 0.6,
        maxAccelG = 0.5,
        totalCornersCount = 10,
        leftCornersCount = 5,
        rightCornersCount = 5,
        maxTurnInRateDegPerSec = 45.0,
        trailBrakingScorePct = 80.0,
        avgCornerScore = 85.0,
        elevationGainMeters = 200.0,
        ridingStyleBadge = "BALANCED",
        name = name,
        notes = null
    )

    private fun createCornerEvent(
        cornerId: Long = 0L,
        rideId: Long,
        sequenceNumber: Int = 1,
        apexLeanDeg: Double = 42.0
    ): CornerEventEntity = CornerEventEntity(
        cornerId = cornerId,
        rideId = rideId,
        sequenceNumber = sequenceNumber,
        timestampMs = 1700000001000L,
        direction = "LEFT",
        entrySpeedKmh = 60.0,
        apexLeanDeg = apexLeanDeg,
        peakLateralG = 0.8,
        peakBrakingG = 0.4,
        exitAccelG = 0.5,
        maxRollRateDegPerSec = 30.0,
        cornerScore = 90.0,
        latitude = 18.0,
        longitude = 98.0
    )

    private fun createTrackPoint(
        pointId: Long = 0L,
        rideId: Long,
        timestampMs: Long = 1700000001000L,
        speedKmh: Double = 75.0
    ): TelemetryTrackPointEntity = TelemetryTrackPointEntity(
        pointId = pointId,
        rideId = rideId,
        timestampMs = timestampMs,
        latitude = 18.0,
        longitude = 98.0,
        speedKmh = speedKmh,
        leanAngleDeg = 25.0,
        longitudinalG = 0.1,
        lateralG = 0.2,
        altitudeMeters = 300.0
    )

    private fun createFuelLog(
        fuelId: Long = 0L,
        motorcycleId: String,
        dateIso: String = "2024-01-12",
        odometerKm: Double = 14200.0,
        liters: Double = 15.0,
        totalCost: Double = 25.0
    ): FuelLogEntity = FuelLogEntity(
        fuelId = fuelId,
        motorcycleId = motorcycleId,
        dateIso = dateIso,
        odometerKm = odometerKm,
        liters = liters,
        pricePerLiter = if (liters > 0.0) totalCost / liters else 1.5,
        totalCost = totalCost,
        isFullTank = true,
        notes = "Fuel test"
    )

    private lateinit var fakeDao: FakeMotoGeekDao
    private lateinit var repository: MotoGeekRepository
    private lateinit var manager: ImportExportManager
    private lateinit var realJson: String

    @Before
    fun setUp() {
        fakeDao = FakeMotoGeekDao()
        repository = MotoGeekRepository(fakeDao)
        val context = FakeContext()
        manager = ImportExportManager(context, repository)

        val candidatePaths = listOf(
            File("docs/mybikes-export-2026-11-16T12-15-04.json"),
            File("../docs/mybikes-export-2026-11-16T12-15-04.json"),
            File("/home/toncek/work/projects/KurveCounter/docs/mybikes-export-2026-11-16T12-15-04.json")
        )
        val file = candidatePaths.firstOrNull { it.exists() }
            ?: throw IllegalStateException("Could not find mybikes export json in candidate paths")
        realJson = file.readText()
    }

    @Test
    fun testParseRealMyBikesExportStructure() = runBlocking {
        val planResult = manager.analyzeJsonString(realJson)
        assertTrue(planResult.isSuccess)
        val plan = planResult.getOrThrow()

        // 1. Verify format & changelog entries count
        assertEquals(ImportSourceFormat.MYBIKES_EXPORT, plan.format)
        assertEquals(21, plan.rawChangelogEntriesCount)

        // 2. Verify 2 bikes: Kawasexi x300 and V-storm 650 XT
        assertEquals(2, plan.bikeGroups.size)
        val kawasexi = plan.bikeGroups.find { it.motorcycle.name.contains("Kawasexi", ignoreCase = true) }
        val vstrom = plan.bikeGroups.find { it.motorcycle.name.contains("V-storm", ignoreCase = true) }
        assertNotNull(kawasexi)
        assertNotNull(vstrom)

        // Kawasexi specifics
        assertEquals("6ace3810-6ede-4491-bfa4-01437eb20e3d", kawasexi!!.sourceKey)
        assertEquals("Kawasaki", kawasexi.motorcycle.make.trim())
        assertEquals("Versys x300", kawasexi.motorcycle.model.trim())
        assertEquals(22000.0, kawasexi.motorcycle.baseOdometerKm, 0.001)
        assertEquals(1, kawasexi.serviceLogs.size)
        assertEquals(0, kawasexi.fuelLogs.size)

        // Kawasexi service log checklist mapping
        val kLog = kawasexi.serviceLogs[0].log
        assertEquals("b6cbb08f-43e8-427e-b91b-2a19b7a10fc8", kLog.logId)
        assertEquals("2026-11-04", kLog.performedAtIsoDate)
        assertEquals(67707.0, kLog.odometerAtServiceKm, 0.001)
        assertEquals("MULTI_TASK_SERVICE", kLog.taskId)
        assertTrue(kLog.checklistItemsJson.contains("Engine Oil & Filter"))

        // V-storm specifics
        assertEquals("c67322a9-d62d-4f38-a3af-3bbcf88d9cab", vstrom!!.sourceKey)
        assertEquals("Suzuki", vstrom.motorcycle.make.trim())
        assertEquals("Vstrom 650 XT", vstrom.motorcycle.model.trim())
        assertEquals(5, vstrom.serviceLogs.size)
        assertEquals(1, vstrom.fuelLogs.size)

        // Verify total 6 services across bikes
        val totalServices = kawasexi.serviceLogs.size + vstrom.serviceLogs.size
        assertEquals(6, totalServices)

        // Verify file-duplicate flagging on ff0e6224
        val dupService = vstrom.serviceLogs.find { it.log.logId == "ff0e6224-04b8-4b6b-8302-7ddb969ecb54" }
        assertNotNull(dupService)
        assertTrue("Duplicate service ff0e6224 must be flagged as isDuplicateInFile", dupService!!.isDuplicateInFile)

        // Verify default selection automatically unchecks the duplicate service in file
        val defaultSel = GuidedImportPlanner.buildDefaultSelection(plan)
        val vstromSel = defaultSel.perBikeSelections[vstrom.sourceKey]
        assertNotNull(vstromSel)
        assertFalse(
            "Default selection must exclude the duplicate service key",
            vstromSel!!.selectedServiceLogKeys.contains(dupService.selectionKey)
        )
        assertEquals(4, vstromSel.selectedServiceLogKeys.size)
        assertEquals(1, vstromSel.selectedFuelLogKeys.size)
    }

    @Test
    fun testSelectiveImportSkippingBikeAndUncheckingItems() = runBlocking {
        val plan = manager.analyzeJsonString(realJson).getOrThrow()
        val defaultSel = GuidedImportPlanner.buildDefaultSelection(plan)

        val kawasexiGroup = plan.bikeGroups.find { it.motorcycle.name.contains("Kawasexi", ignoreCase = true) }!!
        val vstromGroup = plan.bikeGroups.find { it.motorcycle.name.contains("V-storm", ignoreCase = true) }!!

        // Selectively customize:
        // 1. Skip Kawasexi entirely
        val kawasexiSel = defaultSel.perBikeSelections[kawasexiGroup.sourceKey]!!.copy(
            includeBike = false
        )

        // 2. On V-storm: pick only 2 specific service logs, uncheck fuel log, set custom destination
        val selectedTwoLogs = vstromGroup.serviceLogs.filter { !it.isDuplicateInFile }.take(2).map { it.selectionKey }.toSet()
        val vstromSel = defaultSel.perBikeSelections[vstromGroup.sourceKey]!!.copy(
            includeBike = true,
            destination = ImportDestination.CreateNewBike(currencySymbol = "฿", seedDefaultScheduleIfEmpty = true),
            selectedServiceLogKeys = selectedTwoLogs,
            selectedFuelLogKeys = emptySet() // fuel unchecked
        )

        val customSelection = GuidedImportSelection(
            perBikeSelections = mapOf(
                kawasexiGroup.sourceKey to kawasexiSel,
                vstromGroup.sourceKey to vstromSel
            )
        )

        val summaryResult = manager.executeGuidedImport(plan, customSelection)
        assertTrue(summaryResult.isSuccess)
        val summary = summaryResult.getOrThrow()

        // Assert summary matches selections
        assertEquals(1, summary.motorcyclesImported)
        assertEquals(2, summary.logsImported)
        assertEquals(0, summary.fuelLogsImported)
        assertTrue("Seeded schedule should have imported standard tasks", summary.tasksImported > 0)

        // Assert database contents
        assertEquals(1, fakeDao.motorcycles.size)
        val importedBike = fakeDao.motorcycles.values.first()
        assertEquals("V-storm 650 XT", importedBike.name)
        assertEquals("฿", importedBike.currencySymbol)
        assertTrue("First imported bike in empty garage should be active", importedBike.isActive)

        assertEquals(2, fakeDao.serviceLogs.size)
        assertEquals(0, fakeDao.fuelLogs.size)
        assertTrue(fakeDao.maintenanceTasks.isNotEmpty())

        // System activity log was created
        val logs = fakeDao.getAppLogsForMotorcycleList(importedBike.id)
        assertTrue(logs.any { it.category == "SYSTEM" && it.title.contains("Import") })
    }

    @Test
    fun testMergeIntoExistingBikePreservesExistingDataWithoutCascade() = runBlocking {
        // 1. Seed an existing bike in the garage with its own rides, service logs, and fuel
        val existingBikeId = "existing_vstrom_650"
        val existingBike = MotorcycleEntity(
            id = existingBikeId,
            name = "My Old V-Strom",
            make = "Suzuki",
            model = "DL 650",
            year = 2020,
            engineDisplacementCc = 645,
            baseOdometerKm = 15000.0,
            accumulatedTrackedKm = 1200.0,
            manualAdjustmentOffsetKm = 0.0,
            technicalSpecsJson = "{}",
            isActive = true
        )
        fakeDao.insertMotorcycle(existingBike)

        // Existing ride session with corner events and track points
        val rideId = fakeDao.insertRideSession(
            createRideSession(
                rideId = 100L,
                motorcycleId = existingBikeId,
                name = "Sunday Mountain Pass",
                startTimeEpochMs = 1700000000000L,
                endTimeEpochMs = 1700003600000L,
                distanceKm = 85.0
            )
        )
        fakeDao.insertCornerEvents(
            listOf(createCornerEvent(cornerId = 1L, rideId = rideId, sequenceNumber = 1, apexLeanDeg = 42.0))
        )
        fakeDao.insertTelemetryTrackPoints(
            listOf(createTrackPoint(pointId = 1L, rideId = rideId, timestampMs = 1700000001000L, speedKmh = 75.0))
        )

        // Existing pre-existing service log and fuel log
        val preExistingServiceLog = ServiceLogEntity(
            logId = "existing_service_1",
            motorcycleId = existingBikeId,
            performedAtIsoDate = "2024-01-10",
            odometerAtServiceKm = 14000.0,
            notes = "Dealer initial check"
        )
        fakeDao.insertServiceLog(preExistingServiceLog)

        val preExistingFuelLog = createFuelLog(
            fuelId = 10L,
            motorcycleId = existingBikeId,
            dateIso = "2024-01-12",
            odometerKm = 14200.0,
            liters = 15.0,
            totalCost = 25.0
        )
        fakeDao.insertFuelLog(preExistingFuelLog)

        // Existing maintenance task
        fakeDao.insertMaintenanceTask(
            MaintenanceTaskEntity(
                taskId = "STD_ENGINE_OIL",
                motorcycleId = existingBikeId,
                category = "Engine",
                taskName = "Engine Oil",
                actionType = "Replace",
                firstBreakInAtKm = null,
                intervalKm = 6000.0,
                intervalMonths = 12,
                warningThresholdKm = 500.0,
                warningThresholdDays = 30,
                oemPartNumbersJson = "[]",
                notes = "Pre-existing oil task"
            )
        )

        // 2. Execute Guided Import with MergeIntoExistingBike
        val plan = manager.analyzeJsonString(realJson).getOrThrow()
        val defaultSel = GuidedImportPlanner.buildDefaultSelection(plan)

        val vstromGroup = plan.bikeGroups.find { it.motorcycle.name.contains("V-storm", ignoreCase = true) }!!
        val kawasexiGroup = plan.bikeGroups.find { it.motorcycle.name.contains("Kawasexi", ignoreCase = true) }!!

        val selection = GuidedImportSelection(
            perBikeSelections = mapOf(
                kawasexiGroup.sourceKey to defaultSel.perBikeSelections[kawasexiGroup.sourceKey]!!.copy(includeBike = false),
                vstromGroup.sourceKey to defaultSel.perBikeSelections[vstromGroup.sourceKey]!!.copy(
                    includeBike = true,
                    destination = ImportDestination.MergeIntoExistingBike(existingBikeId),
                    updateBikeProfileAndOdo = true,
                    importTechnicalSpecs = true
                )
            )
        )

        val result = manager.executeGuidedImport(plan, selection)
        assertTrue(result.isSuccess)
        val summary = result.getOrThrow()
        assertEquals(0, summary.motorcyclesImported) // No new bike inserted, merged!
        assertEquals(4, summary.logsImported) // 4 non-duplicate logs
        assertEquals(1, summary.fuelLogsImported) // 1 fuel log

        // 3. VERIFY NON-DESTRUCTIVE GUARANTEE:
        // Existing bike was NOT replaced by Room SQLite REPLACE (which would have wiped children!)
        assertEquals(1, fakeDao.motorcycles.size)
        val bikeAfter = fakeDao.getMotorcycleById(existingBikeId)
        assertNotNull(bikeAfter)
        assertEquals("V-storm 650 XT", bikeAfter!!.name) // updated profile
        assertEquals(1200.0, bikeAfter.accumulatedTrackedKm, 0.001) // preserved tracked distance
        assertTrue(bikeAfter.isActive) // preserved active state

        // CRITICAL: Ride session, corner events, and track points must remain 100% intact!
        assertEquals(1, fakeDao.rideSessions.size)
        assertEquals("Sunday Mountain Pass", fakeDao.rideSessions[0].name)
        assertEquals(1, fakeDao.cornerEvents.size)
        assertEquals(1, fakeDao.trackPoints.size)

        // CRITICAL: Pre-existing service log and pre-existing fuel log must remain 100% intact!
        val allServices = fakeDao.getServiceLogsForMotorcycle(existingBikeId)
        assertEquals(5, allServices.size) // 1 pre-existing + 4 imported
        assertTrue(allServices.any { it.logId == "existing_service_1" })

        val allFuel = fakeDao.getFuelLogsForMotorcycleList(existingBikeId)
        assertEquals(2, allFuel.size) // 1 pre-existing + 1 imported
        assertTrue(allFuel.any { it.fuelId == 10L })

        // Pre-existing maintenance task was preserved
        val tasks = fakeDao.getMaintenanceTasksForMotorcycle(existingBikeId)
        assertTrue(tasks.any { it.taskId == "STD_ENGINE_OIL" && it.notes == "Pre-existing oil task" })
    }

    @Test
    fun testReimportingSameFileAddsZeroDuplicates() = runBlocking {
        // 1. Initial import of MyBikes export via importFromJsonString
        val res1 = manager.importFromJsonString(realJson, targetMotorcycleId = null, restoreMode = RestoreMode.MERGE)
        assertTrue(res1.isSuccess)
        val sum1 = res1.getOrThrow()
        assertEquals(2, sum1.motorcyclesImported)
        assertEquals(5, sum1.logsImported) // 1 Kawasexi + 4 V-storm (1 dup skipped)
        assertEquals(1, sum1.fuelLogsImported)

        val bikesCount1 = fakeDao.motorcycles.size
        val servicesCount1 = fakeDao.serviceLogs.size
        val fuelCount1 = fakeDao.fuelLogs.size
        val tasksCount1 = fakeDao.maintenanceTasks.size

        // 2. Re-import the exact same file
        val res2 = manager.importFromJsonString(realJson, targetMotorcycleId = null, restoreMode = RestoreMode.MERGE)
        assertTrue(res2.isSuccess)
        val sum2 = res2.getOrThrow()

        // Re-importing must add 0 duplicates!
        assertEquals(0, sum2.motorcyclesImported)
        assertEquals(0, sum2.logsImported)
        assertEquals(0, sum2.fuelLogsImported)
        assertEquals(0, sum2.tasksImported)

        // Totals in database must remain exactly the same
        assertEquals(bikesCount1, fakeDao.motorcycles.size)
        assertEquals(servicesCount1, fakeDao.serviceLogs.size)
        assertEquals(fuelCount1, fakeDao.fuelLogs.size)
        assertEquals(tasksCount1, fakeDao.maintenanceTasks.size)
    }

    @Test
    fun testGlobalServiceLogIdCollisionMintsFreshUuid() = runBlocking {
        // Bike A exists with logId = "colliding_log_id"
        val bikeA = MotorcycleEntity(
            id = "bike_A",
            name = "Bike A",
            make = "Honda",
            model = "CB500X",
            year = 2021,
            engineDisplacementCc = 471,
            baseOdometerKm = 10000.0,
            accumulatedTrackedKm = 0.0,
            manualAdjustmentOffsetKm = 0.0,
            technicalSpecsJson = "{}",
            isActive = true
        )
        fakeDao.insertMotorcycle(bikeA)
        fakeDao.insertServiceLog(
            ServiceLogEntity(
                logId = "colliding_log_id",
                motorcycleId = "bike_A",
                performedAtIsoDate = "2024-05-01",
                odometerAtServiceKm = 10000.0,
                notes = "Bike A original service"
            )
        )

        // Plan for Bike B contains a service log with the same logId = "colliding_log_id"
        val bikeBGroup = ImportSourceBikeGroup(
            sourceKey = "bike_B",
            motorcycle = MotorcycleDto(
                id = "bike_B",
                name = "Bike B",
                make = "Yamaha",
                model = "Tenere 700",
                year = 2022,
                engineDisplacementCc = 689,
                baseOdometerKm = 5000.0,
                accumulatedTrackedKm = 0.0,
                manualAdjustmentOffsetKm = 0.0,
                isActive = false
            ),
            serviceLogs = listOf(
                ImportServiceLogCandidate(
                    selectionKey = "svc_b_1",
                    log = ServiceLogDto(
                        logId = "colliding_log_id", // Collides globally with Bike A!
                        taskId = "GENERAL_SERVICE",
                        performedAtIsoDate = "2025-06-01",
                        odometerAtServiceKm = 5000.0,
                        costEur = 0.0,
                        performedBy = "Self",
                        notes = "Bike B imported service"
                    )
                )
            )
        )

        val plan = ImportPreviewPlan(
            format = ImportSourceFormat.MYBIKES_EXPORT,
            bikeGroups = listOf(bikeBGroup)
        )
        val selection = GuidedImportSelection(
            perBikeSelections = mapOf(
                "bike_B" to BikeImportSelection(
                    includeBike = true,
                    destination = ImportDestination.CreateNewBike(currencySymbol = "€"),
                    selectedServiceLogKeys = setOf("svc_b_1")
                )
            )
        )

        val result = manager.executeGuidedImport(plan, selection)
        assertTrue(result.isSuccess)
        val summary = result.getOrThrow()
        assertEquals(1, summary.logsImported)

        // Both service logs must exist simultaneously; Bike A's service must NOT be overwritten or moved!
        val bikeALogs = fakeDao.getServiceLogsForMotorcycle("bike_A")
        val bikeBLogs = fakeDao.getServiceLogsForMotorcycle("bike_B")

        assertEquals(1, bikeALogs.size)
        assertEquals("colliding_log_id", bikeALogs[0].logId)
        assertEquals("Bike A original service", bikeALogs[0].notes)

        assertEquals(1, bikeBLogs.size)
        assertNotEquals("colliding_log_id", bikeBLogs[0].logId) // Fresh UUID minted!
        assertEquals("Bike B imported service", bikeBLogs[0].notes)
    }

    @Test
    fun testKurveGarageBackupFormatARestoreMergeIsNonDestructive() = runBlocking {
        // Pre-populate bike with ride and service log
        val localBikeId = "kurve_bike_1"
        fakeDao.insertMotorcycle(
            MotorcycleEntity(
                id = localBikeId,
                name = "Kurve Bike",
                make = "KTM",
                model = "890 Adventure",
                year = 2023,
                engineDisplacementCc = 889,
                baseOdometerKm = 5000.0,
                accumulatedTrackedKm = 300.0,
                manualAdjustmentOffsetKm = 0.0,
                technicalSpecsJson = "{}",
                isActive = true
            )
        )
        fakeDao.insertRideSession(
            createRideSession(
                rideId = 555L,
                motorcycleId = localBikeId,
                name = "Local Ride Before Backup Merge",
                startTimeEpochMs = 1710000000000L
            )
        )
        fakeDao.insertServiceLog(
            ServiceLogEntity(
                logId = "existing_ktm_service",
                motorcycleId = localBikeId,
                performedAtIsoDate = "2024-03-01",
                odometerAtServiceKm = 5000.0,
                notes = "First oil change"
            )
        )

        // Format A JSON with the same bike ID, an extra service log, and the same service log
        val formatAJson = """
        {
          "schemaVersion": 2,
          "exportDateIso": "2026-11-16T00:00:00Z",
          "motorcycles": [
            {
              "motorcycle": {
                "id": "$localBikeId",
                "make": "KTM",
                "model": "890 Adventure R",
                "year": 2023,
                "engineDisplacementCc": 889,
                "baseOdometerKm": 5000.0,
                "accumulatedTrackedKm": 0.0,
                "manualAdjustmentOffsetKm": 0.0,
                "isActive": false,
                "name": "Kurve Bike Updated"
              },
              "maintenanceSchedule": [],
              "serviceLogs": [
                {
                  "logId": "existing_ktm_service",
                  "taskId": "GENERAL_SERVICE",
                  "performedAtIsoDate": "2024-03-01",
                  "odometerAtServiceKm": 5000.0,
                  "costEur": 50.0,
                  "performedBy": "Self",
                  "notes": "First oil change"
                },
                {
                  "logId": "new_ktm_service",
                  "taskId": "GENERAL_SERVICE",
                  "performedAtIsoDate": "2024-09-01",
                  "odometerAtServiceKm": 9000.0,
                  "costEur": 80.0,
                  "performedBy": "Dealer",
                  "notes": "Second service"
                }
              ],
              "fuelLogs": [],
              "partLogs": [],
              "reminders": [],
              "rides": [],
              "appLogs": []
            }
          ]
        }
        """.trimIndent()

        val result = manager.importFromJsonString(formatAJson, targetMotorcycleId = null, restoreMode = RestoreMode.MERGE)
        assertTrue(result.isSuccess)
        val summary = result.getOrThrow()

        // 0 bikes added (updated existing), 1 new service log added (1 duplicate skipped)
        assertEquals(0, summary.motorcyclesImported)
        assertEquals(1, summary.logsImported)

        // Verify pre-existing ride is intact! (Proves no REPLACE cascade happened!)
        val rides = fakeDao.getRideSessionsForMotorcycleList(localBikeId)
        assertEquals(1, rides.size)
        assertEquals("Local Ride Before Backup Merge", rides[0].name)

        // Verify service logs: 1 pre-existing + 1 new = 2 total
        val services = fakeDao.getServiceLogsForMotorcycle(localBikeId)
        assertEquals(2, services.size)
        assertTrue(services.any { it.logId == "existing_ktm_service" })
        assertTrue(services.any { it.logId == "new_ktm_service" })
    }
}

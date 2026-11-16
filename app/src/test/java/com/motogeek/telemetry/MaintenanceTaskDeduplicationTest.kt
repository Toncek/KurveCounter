package com.motogeek.telemetry

import android.content.Context
import android.content.ContextWrapper
import com.motogeek.telemetry.data.local.*
import com.motogeek.telemetry.domain.maintenance.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.io.File
import java.time.LocalDate
import java.util.UUID

class MaintenanceTaskDeduplicationTest {

    class FakeContext : ContextWrapper(null) {
        private val tempDir = File(System.getProperty("java.io.tmpdir"), "fake_ctx_${UUID.randomUUID()}").apply { mkdirs() }
        override fun getFilesDir(): File = tempDir
        override fun getCacheDir(): File = tempDir
    }

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

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = true
    }

    private lateinit var context: FakeContext
    private lateinit var dao: FakeMotoGeekDao
    private lateinit var repository: MotoGeekRepository
    private lateinit var importExportManager: ImportExportManager
    private val evaluator = MaintenanceEvaluator()

    private fun task(
        taskId: String,
        motorcycleId: String = "bike_1",
        category: String = "Engine",
        taskName: String,
        actionType: String = "Replace",
        firstBreakInAtKm: Double? = null,
        intervalKm: Double? = null,
        intervalMonths: Int? = null,
        warningThresholdKm: Double? = null,
        warningThresholdDays: Int? = null,
        oemPartNumbersJson: String = "[]",
        notes: String = ""
    ) = MaintenanceTaskEntity(
        taskId = taskId,
        motorcycleId = motorcycleId,
        category = category,
        taskName = taskName,
        actionType = actionType,
        firstBreakInAtKm = firstBreakInAtKm,
        intervalKm = intervalKm,
        intervalMonths = intervalMonths,
        warningThresholdKm = warningThresholdKm,
        warningThresholdDays = warningThresholdDays,
        oemPartNumbersJson = oemPartNumbersJson,
        notes = notes
    )

    @Before
    fun setUp() {
        context = FakeContext()
        dao = FakeMotoGeekDao()
        repository = MotoGeekRepository(dao)
        importExportManager = ImportExportManager(context, repository)
    }

    // =========================================================================
    // 1. Accent Stripping & Canonical Task Key Matching
    // =========================================================================

    @Test
    fun testStripAccentsDiacriticsAndWhitespaceNormalization() {
        assertEquals("vzduchovy filter", ServiceTaskDeduplicator.stripAccents("Vzduchový filter"))
        assertEquals("vzduchovy filter", ServiceTaskDeduplicator.stripAccents("  Vzduchovy   filter  "))
        assertEquals("zapalovacie sviecky", ServiceTaskDeduplicator.stripAccents("Zapaľovacie sviečky"))
        assertEquals("hnacia retaz a rozety", ServiceTaskDeduplicator.stripAccents("Hnacia reťaz a rozety"))
        assertEquals("brzdove dosticky (p/z)", ServiceTaskDeduplicator.stripAccents("Brzdové doštičky (P/Z)"))
        assertEquals("dotiahnutie skrutiek a spojov", ServiceTaskDeduplicator.stripAccents("Dotiahnutie skrutiek a spojov"))
        assertEquals("krk riadenia", ServiceTaskDeduplicator.stripAccents("Krk riadenia"))
        assertEquals("tlmic pruzenia", ServiceTaskDeduplicator.stripAccents("Tlmič pruženia"))
    }

    // =========================================================================
    // 2. Air Filter 3x and 4x Deduplication (Rule 4)
    // =========================================================================

    @Test
    fun testAirFilter4xDuplicatesMergeIntoSingleStdAirFilter() {
        val bikeId = "bike_air_4x"
        val tasks = listOf(
            task(
                taskId = "STD_AIR_FILTER",
                motorcycleId = bikeId,
                category = "Engine",
                taskName = "Air Filter Element",
                actionType = "Replace",
                intervalKm = 12000.0,
                intervalMonths = null,
                warningThresholdKm = null,
                oemPartNumbersJson = json.encodeToString(listOf("1WD-E4451-00")),
                notes = "OEM paper element inspection"
            ),
            task(
                taskId = "CUSTOM_AIR_EN",
                motorcycleId = bikeId,
                category = "Engine",
                taskName = "Air Filter",
                actionType = "Inspect",
                intervalKm = 10000.0,
                intervalMonths = 12,
                warningThresholdKm = 500.0,
                oemPartNumbersJson = json.encodeToString(listOf("HFA4921")),
                notes = "Blow with compressed air from clean side"
            ),
            task(
                taskId = "CUSTOM_AIR_SK_DIACRITICS",
                motorcycleId = bikeId,
                category = "Engine",
                taskName = "Vzduchový filter",
                actionType = "Replace",
                intervalKm = null,
                intervalMonths = 24,
                warningThresholdKm = 400.0,
                oemPartNumbersJson = json.encodeToString(listOf("OEM-VF-99")),
                notes = "Vymeniť filtračnú vložku"
            ),
            task(
                taskId = "CUSTOM_AIR_SK_ASCII",
                motorcycleId = bikeId,
                category = "Motor",
                taskName = "Vzduchovy filter",
                actionType = "Replace",
                intervalKm = 12000.0,
                intervalMonths = null,
                warningThresholdKm = 600.0,
                oemPartNumbersJson = "[]",
                notes = "Skontrolovat znecistenie"
            )
        )

        val result = ServiceTaskDeduplicator.deduplicateTasks(tasks)

        assertEquals("All 4 air filter tasks should merge into 1", 1, result.deduplicatedTasks.size)
        val survivor = result.deduplicatedTasks.first()

        assertEquals("Survivor task ID must be STD_AIR_FILTER", "STD_AIR_FILTER", survivor.taskId)
        assertEquals("Survivor intervalKm preserved", 12000.0, survivor.intervalKm)
        assertEquals("Survivor intervalMonths merged", 12, survivor.intervalMonths)
        assertEquals("Survivor warningThresholdKm merged", 500.0, survivor.warningThresholdKm)

        val oemParts = json.decodeFromString<List<String>>(survivor.oemPartNumbersJson)
        assertTrue("Contains 1WD-E4451-00", oemParts.contains("1WD-E4451-00"))
        assertTrue("Contains HFA4921", oemParts.contains("HFA4921"))
        assertTrue("Contains OEM-VF-99", oemParts.contains("OEM-VF-99"))
        assertEquals("OEM parts distinct", 3, oemParts.size)

        assertTrue("Notes contain OEM note", survivor.notes.contains("OEM paper element inspection"))
        assertTrue("Notes contain English note", survivor.notes.contains("Blow with compressed air"))
        assertTrue("Notes contain Slovak note", survivor.notes.contains("Vymeniť filtračnú vložku"))

        assertEquals("Remapped task IDs size", 3, result.remappedTaskIds.size)
        assertEquals("STD_AIR_FILTER", result.remappedTaskIds["CUSTOM_AIR_EN"])
        assertEquals("STD_AIR_FILTER", result.remappedTaskIds["CUSTOM_AIR_SK_DIACRITICS"])
        assertEquals("STD_AIR_FILTER", result.remappedTaskIds["CUSTOM_AIR_SK_ASCII"])

        assertEquals("Removed task IDs size", 3, result.removedTaskIds.size)
        assertTrue(result.removedTaskIds.contains("CUSTOM_AIR_EN"))
        assertTrue(result.removedTaskIds.contains("CUSTOM_AIR_SK_DIACRITICS"))
        assertTrue(result.removedTaskIds.contains("CUSTOM_AIR_SK_ASCII"))
    }

    @Test
    fun testAirFilter3xDuplicatesWithoutStdId() {
        val bikeId = "bike_air_3x"
        val tasks = listOf(
            task(
                taskId = "AI_AIR_1",
                motorcycleId = bikeId,
                category = "Engine",
                taskName = "Air Filter",
                actionType = "Replace",
                intervalKm = 10000.0,
                intervalMonths = null,
                oemPartNumbersJson = json.encodeToString(listOf("KN-YA09")),
                notes = "K&N washable"
            ),
            task(
                taskId = "AI_AIR_2",
                motorcycleId = bikeId,
                category = "Engine",
                taskName = "Vzduchový filter",
                actionType = "Inspect",
                intervalKm = null,
                intervalMonths = 12,
                oemPartNumbersJson = "[]",
                notes = "Ročná kontrola"
            ),
            task(
                taskId = "AI_AIR_3",
                motorcycleId = bikeId,
                category = "Motor",
                taskName = "Vzduchovy filter",
                actionType = "Replace",
                intervalKm = 10000.0,
                intervalMonths = null,
                oemPartNumbersJson = "[]",
                notes = "Vymena"
            )
        )

        val result = ServiceTaskDeduplicator.deduplicateTasks(tasks)

        assertEquals(1, result.deduplicatedTasks.size)
        val survivor = result.deduplicatedTasks.first()
        assertEquals("STD_AIR_FILTER", survivor.taskId)
        assertEquals(10000.0, survivor.intervalKm)
        assertEquals(12, survivor.intervalMonths)

        assertEquals(3, result.remappedTaskIds.size)
        assertEquals(3, result.removedTaskIds.size)
    }

    @Test
    fun testAirFilterDtoDeduplication() {
        val dtos = listOf(
            MaintenanceTaskDto(
                taskId = "STD_AIR_FILTER",
                category = "Engine",
                taskName = "Air Filter",
                actionType = "Replace",
                intervalKm = 10000.0,
                intervalMonths = null,
                oemPartNumbers = listOf("PART-1")
            ),
            MaintenanceTaskDto(
                taskId = "AIR_DTO_2",
                category = "Engine",
                taskName = "Vzduchový filter",
                actionType = "Replace",
                intervalKm = null,
                intervalMonths = 12,
                oemPartNumbers = listOf("PART-2")
            ),
            MaintenanceTaskDto(
                taskId = "AIR_DTO_3",
                category = "Engine",
                taskName = "Vzduchovy filter",
                actionType = "Replace",
                intervalKm = 10000.0,
                intervalMonths = 12,
                oemPartNumbers = listOf("PART-1", "PART-3")
            )
        )

        val deduped = ServiceTaskDeduplicator.deduplicateTaskDtos(dtos)
        assertEquals(1, deduped.size)
        assertEquals("STD_AIR_FILTER", deduped[0].taskId)
        assertEquals(10000.0, deduped[0].intervalKm)
        assertEquals(12, deduped[0].intervalMonths)
        assertEquals(listOf("PART-1", "PART-2", "PART-3"), deduped[0].oemPartNumbers)
    }

    // =========================================================================
    // 3. Distinct Tasks Stay Separate (Rule 5)
    // =========================================================================

    @Test
    fun testDistinctTasksStaySeparate() {
        // Engine Oil vs Fork Oil
        val engineOilKeyEn = ServiceTaskDeduplicator.canonicalTaskKey("STD_ENGINE_OIL", "Engine Oil & Filter", "Engine")
        val engineOilKeySk = ServiceTaskDeduplicator.canonicalTaskKey(null, "Motorový olej a filter", "Motor")
        val forkOilKeyEn = ServiceTaskDeduplicator.canonicalTaskKey("GEN_FORKS", "Front Fork Seals & Oil", "Suspension")
        val forkOilKeySk = ServiceTaskDeduplicator.canonicalTaskKey(null, "Olej v prednej vidlici", "Podvozok")

        assertEquals("STD_ENGINE_OIL", engineOilKeyEn)
        assertEquals("STD_ENGINE_OIL", engineOilKeySk)
        assertEquals("GEN_FORKS", forkOilKeyEn)
        assertEquals("GEN_FORKS", forkOilKeySk)
        assertNotEquals("Engine oil must not match fork oil", engineOilKeyEn, forkOilKeyEn)

        // Brake Fluid vs Brake Pads
        val brakeFluidKeyEn = ServiceTaskDeduplicator.canonicalTaskKey("STD_BRAKE_FLUID", "Brake Fluid Flush", "Brakes")
        val brakeFluidKeySk = ServiceTaskDeduplicator.canonicalTaskKey(null, "Brzdová kvapalina", "Brzdy")
        val brakePadsKeyEn = ServiceTaskDeduplicator.canonicalTaskKey("GEN_BRAKE_PADS", "Brake Pads Front/Rear", "Brakes")
        val brakePadsKeySk = ServiceTaskDeduplicator.canonicalTaskKey(null, "Brzdové platničky", "Brzdy")

        assertEquals("STD_BRAKE_FLUID", brakeFluidKeyEn)
        assertEquals("STD_BRAKE_FLUID", brakeFluidKeySk)
        assertEquals("GEN_BRAKE_PADS", brakePadsKeyEn)
        assertEquals("GEN_BRAKE_PADS", brakePadsKeySk)
        assertNotEquals("Brake fluid must not match brake pads", brakeFluidKeyEn, brakePadsKeyEn)

        // Front Pads vs Rear Pads
        val frontPadsKeyEn = ServiceTaskDeduplicator.canonicalTaskKey("FRONT_BRAKE_PADS", "Front Brake Pads", "Brakes")
        val frontPadsKeySk = ServiceTaskDeduplicator.canonicalTaskKey(null, "Predné brzdové platničky", "Brzdy")
        val rearPadsKeyEn = ServiceTaskDeduplicator.canonicalTaskKey("REAR_BRAKE_PADS", "Rear Brake Pads", "Brakes")
        val rearPadsKeySk = ServiceTaskDeduplicator.canonicalTaskKey(null, "Zadné brzdové doštičky", "Brzdy")

        assertEquals("STD_BRAKE_PADS_FRONT", frontPadsKeyEn)
        assertEquals("STD_BRAKE_PADS_FRONT", frontPadsKeySk)
        assertEquals("STD_BRAKE_PADS_REAR", rearPadsKeyEn)
        assertEquals("STD_BRAKE_PADS_REAR", rearPadsKeySk)
        assertNotEquals("Front pads must not match rear pads", frontPadsKeyEn, rearPadsKeyEn)

        // Chain vs Valves
        val chainKeyEn = ServiceTaskDeduplicator.canonicalTaskKey("STD_CHAIN_LUBE", "Drive Chain Clean & Lube", "Drivetrain")
        val chainKeySk = ServiceTaskDeduplicator.canonicalTaskKey(null, "Hnacia reťaz a rozety", "Pohon")
        val valvesKeyEn = ServiceTaskDeduplicator.canonicalTaskKey("STD_VALVE_CLEARANCE", "Valve Clearance Check", "Engine")
        val valvesKeySk = ServiceTaskDeduplicator.canonicalTaskKey(null, "Kontrola ventilových vôlí", "Motor")

        assertEquals("STD_CHAIN_LUBE", chainKeyEn)
        assertEquals("STD_CHAIN_LUBE", chainKeySk)
        assertEquals("STD_VALVE_CLEARANCE", valvesKeyEn)
        assertEquals("STD_VALVE_CLEARANCE", valvesKeySk)
        assertNotEquals("Chain must not match valves", chainKeyEn, valvesKeyEn)
    }

    @Test
    fun testDistinctTasksInBatchDeduplicationRemainIntact() {
        val bikeId = "bike_distinct_all"
        val tasks = listOf(
            task(taskId = "STD_ENGINE_OIL", motorcycleId = bikeId, category = "Engine", taskName = "Engine Oil & Filter", actionType = "Replace", intervalKm = 10000.0),
            task(taskId = "GEN_FORKS", motorcycleId = bikeId, category = "Suspension", taskName = "Front Fork Seals & Oil", actionType = "Inspect", intervalKm = 20000.0),
            task(taskId = "STD_BRAKE_FLUID", motorcycleId = bikeId, category = "Brakes", taskName = "Brake Fluid", actionType = "Replace", intervalMonths = 24),
            task(taskId = "STD_BRAKE_PADS_FRONT", motorcycleId = bikeId, category = "Brakes", taskName = "Front Brake Pads", actionType = "Inspect", intervalKm = 10000.0),
            task(taskId = "STD_BRAKE_PADS_REAR", motorcycleId = bikeId, category = "Brakes", taskName = "Rear Brake Pads", actionType = "Inspect", intervalKm = 10000.0),
            task(taskId = "STD_CHAIN_LUBE", motorcycleId = bikeId, category = "Drivetrain", taskName = "Drive Chain & Sprockets", actionType = "Clean/Lube", intervalKm = 1000.0),
            task(taskId = "STD_VALVE_CLEARANCE", motorcycleId = bikeId, category = "Engine", taskName = "Valve Clearance Check", actionType = "Inspect", intervalKm = 40000.0)
        )

        val result = ServiceTaskDeduplicator.deduplicateTasks(tasks)

        assertEquals("All 7 distinct tasks must remain separate", 7, result.deduplicatedTasks.size)
        assertTrue("No task IDs remapped", result.remappedTaskIds.isEmpty())
        assertTrue("No task IDs removed", result.removedTaskIds.isEmpty())
    }

    // =========================================================================
    // 4. Accent-Insensitive Slovak Matching in Catalog (Rule 7)
    // =========================================================================

    @Test
    fun testAccentInsensitiveSlovakMatchingInRemainingGeneralChecks() {
        val bikeId = "bike_sk_checks"
        val existingTasks = listOf(
            // Without accents:
            task(taskId = "T1", motorcycleId = bikeId, category = "Motor", taskName = "Vzduchovy filter", actionType = "Replace"),
            task(taskId = "T2", motorcycleId = bikeId, category = "Motor", taskName = "Motorovy olej", actionType = "Replace"),
            task(taskId = "T3", motorcycleId = bikeId, category = "Brzdy", taskName = "Brzdova kvapalina", actionType = "Replace"),
            task(taskId = "T4", motorcycleId = bikeId, category = "Zapalovanie", taskName = "Zapalovacie sviecky", actionType = "Replace"),
            task(taskId = "T5", motorcycleId = bikeId, category = "Pohon", taskName = "Hnacia retaz a rozety", actionType = "Clean/Lube"),
            task(taskId = "T6", motorcycleId = bikeId, category = "Podvozok", taskName = "Predna vidlica a gufera", actionType = "Inspect")
        )

        val remaining = ServiceChecklistCatalog.remainingGeneralChecks(existingTasks)
        val remainingKeys = remaining.map { it.key }.toSet()

        assertFalse("GEN_AIR_FILTER should be excluded", remainingKeys.contains("GEN_AIR_FILTER"))
        assertFalse("GEN_ENGINE_OIL should be excluded", remainingKeys.contains("GEN_ENGINE_OIL"))
        assertFalse("GEN_BRAKE_FLUID should be excluded", remainingKeys.contains("GEN_BRAKE_FLUID"))
        assertFalse("GEN_SPARK_PLUGS should be excluded", remainingKeys.contains("GEN_SPARK_PLUGS"))
        assertFalse("GEN_CHAIN should be excluded", remainingKeys.contains("GEN_CHAIN"))
        assertFalse("GEN_FORKS should be excluded", remainingKeys.contains("GEN_FORKS"))

        // Unmatched checks must remain available:
        assertTrue("GEN_BATTERY should remain", remainingKeys.contains("GEN_BATTERY"))
        assertTrue("GEN_BOLTS should remain", remainingKeys.contains("GEN_BOLTS"))
        assertTrue("GEN_STEERING should remain", remainingKeys.contains("GEN_STEERING"))
    }

    @Test
    fun testAccentInsensitiveSlovakMatchingInGetSubOptionsFor() {
        val airOptionsDiacritics = ServiceChecklistCatalog.getSubOptionsFor(null, "Vzduchový filter", "Motor")
        val airOptionsAscii = ServiceChecklistCatalog.getSubOptionsFor(null, "Vzduchovy filter", "Motor")
        assertEquals(airOptionsDiacritics.map { it.key }, airOptionsAscii.map { it.key })
        assertTrue(airOptionsAscii.any { it.key == "AIR_CHANGED" })

        val oilOptionsDiacritics = ServiceChecklistCatalog.getSubOptionsFor(null, "Motorový olej", "Motor")
        val oilOptionsAscii = ServiceChecklistCatalog.getSubOptionsFor(null, "Motorovy olej", "Motor")
        assertEquals(oilOptionsDiacritics.map { it.key }, oilOptionsAscii.map { it.key })
        assertTrue(oilOptionsAscii.any { it.key == "OIL_CHANGED" })

        val brakeFluidDiacritics = ServiceChecklistCatalog.getSubOptionsFor(null, "Brzdová kvapalina", "Brzdy")
        val brakeFluidAscii = ServiceChecklistCatalog.getSubOptionsFor(null, "Brzdova kvapalina", "Brzdy")
        assertEquals(brakeFluidDiacritics.map { it.key }, brakeFluidAscii.map { it.key })
        assertTrue(brakeFluidAscii.any { it.key == "BRAKE_FLUID_CHANGED" })

        val chainDiacritics = ServiceChecklistCatalog.getSubOptionsFor(null, "Hnacia reťaz", "Pohon")
        val chainAscii = ServiceChecklistCatalog.getSubOptionsFor(null, "Hnacia retaz", "Pohon")
        assertEquals(chainDiacritics.map { it.key }, chainAscii.map { it.key })
        assertTrue(chainAscii.any { it.key == "CHAIN_CLEANED_LUBED" })

        val plugsDiacritics = ServiceChecklistCatalog.getSubOptionsFor(null, "Zapaľovacie sviečky", "Zapaľovanie")
        val plugsAscii = ServiceChecklistCatalog.getSubOptionsFor(null, "Zapalovacie sviecky", "Zapalovanie")
        assertEquals(plugsDiacritics.map { it.key }, plugsAscii.map { it.key })
        assertTrue(plugsAscii.any { it.key == "PLUG_CHANGED" })

        val steeringDiacritics = ServiceChecklistCatalog.getSubOptionsFor(null, "Ložiská krku riadenia", "Podvozok")
        val steeringAscii = ServiceChecklistCatalog.getSubOptionsFor(null, "Loziska krku riadenia", "Podvozok")
        assertEquals(steeringDiacritics.map { it.key }, steeringAscii.map { it.key })
        assertTrue(steeringAscii.any { it.key == "STEERING_CHECKED" })
    }

    @Test
    fun testAccentInsensitiveItemMatchesAndCompletesTask() {
        val taskAir = task(
            taskId = "STD_AIR_FILTER",
            motorcycleId = "bike_1",
            category = "Engine",
            taskName = "Vzduchový filter",
            actionType = "Replace"
        )
        val itemAir = ServiceChecklistItem(
            name = "Vzduchovy filter",
            status = "DONE",
            taskId = null,
            completesTask = true
        )
        assertTrue(ServiceChecklistCatalog.itemMatchesAndCompletesTask(itemAir, taskAir))

        val taskBrake = task(
            taskId = "STD_BRAKE_FLUID",
            motorcycleId = "bike_1",
            category = "Brakes",
            taskName = "Brzdová kvapalina",
            actionType = "Replace"
        )
        val itemBrake = ServiceChecklistItem(
            name = "Brzdova kvapalina",
            status = "DONE",
            taskId = null,
            completesTask = true
        )
        assertTrue(ServiceChecklistCatalog.itemMatchesAndCompletesTask(itemBrake, taskBrake))
    }

    // =========================================================================
    // 5. Repository Deduplication & Log Remapping & Evaluator (Rule 6)
    // =========================================================================

    @Test
    fun testMotoGeekRepositoryDeduplicateMaintenanceTasksForBike() = runBlocking {
        val bike = MotorcycleEntity(
            id = "bike_repo_dedup",
            make = "Kawasaki",
            model = "Ninja 400",
            year = 2022,
            engineDisplacementCc = 399,
            baseOdometerKm = 15000.0,
            accumulatedTrackedKm = 0.0,
            manualAdjustmentOffsetKm = 0.0,
            technicalSpecsJson = "{}",
            isActive = true
        )
        repository.insertMotorcycle(bike)

        val survivorTask = task(
            taskId = "STD_AIR_FILTER",
            motorcycleId = bike.id,
            category = "Engine",
            taskName = "Air Filter Element",
            actionType = "Replace",
            intervalKm = 10000.0,
            warningThresholdKm = 500.0
        )
        val loserTask = task(
            taskId = "CUSTOM_AIR_OLD_UUID",
            motorcycleId = bike.id,
            category = "Engine",
            taskName = "Vzduchový filter",
            actionType = "Replace",
            intervalKm = 10000.0,
            warningThresholdKm = 500.0
        )
        repository.insertMaintenanceTasks(listOf(survivorTask, loserTask))

        // Service log recorded against the loser task
        val oldServiceLog = ServiceLogEntity(
            logId = "log_air_service_old",
            motorcycleId = bike.id,
            taskId = "CUSTOM_AIR_OLD_UUID",
            serviceTitle = "Air filter service",
            performedAtIsoDate = "2026-06-01",
            odometerAtServiceKm = 14500.0,
            checklistItemsJson = json.encodeToString(
                listOf(
                    ServiceChecklistItem(
                        name = "Vzduchový filter",
                        status = "DONE",
                        taskId = "CUSTOM_AIR_OLD_UUID",
                        subActions = listOf("AIR_CHANGED"),
                        completesTask = true
                    )
                )
            )
        )
        repository.insertServiceLog(oldServiceLog)

        // Run repository deduplication
        val removedCount = repository.deduplicateMaintenanceTasksForBike(bike.id)
        assertEquals("1 loser task removed by deduplication", 1, removedCount)

        // Verify DB state
        val tasksInDb = repository.getMaintenanceTasksForMotorcycle(bike.id)
        assertEquals("Loser task was deleted from database", 1, tasksInDb.size)
        assertEquals("STD_AIR_FILTER", tasksInDb[0].taskId)

        val logsInDb = repository.getServiceLogsForMotorcycle(bike.id)
        assertEquals(1, logsInDb.size)
        val remappedLog = logsInDb[0]
        assertEquals("Service log taskId remapped to STD_AIR_FILTER", "STD_AIR_FILTER", remappedLog.taskId)

        val checklistItems = remappedLog.parseChecklistOrEmpty()
        assertEquals(1, checklistItems.size)
        assertEquals("Checklist item taskId remapped to STD_AIR_FILTER", "STD_AIR_FILTER", checklistItems[0].taskId)

        // Verify MaintenanceEvaluator marks survivor task OK
        val evaluations = evaluator.evaluate(
            motorcycle = bike,
            tasks = tasksInDb,
            logs = logsInDb,
            currentDate = LocalDate.of(2026, 6, 2)
        )
        assertEquals(1, evaluations.size)
        val airStatus = evaluations[0]
        assertEquals("Survivor task evaluated as OK", TaskStatus.OK, airStatus.status)
        assertEquals(9500.0, airStatus.remainingKm!!, 0.01)
        assertEquals(24500.0, airStatus.nextDueOdometerKm!!, 0.01)
    }

    @Test
    fun testGeneralWorkshopServiceLogChecklistRemappedDuringDeduplication() = runBlocking {
        val bike = MotorcycleEntity(
            id = "bike_general_workshop",
            make = "Honda",
            model = "CB650R",
            year = 2023,
            engineDisplacementCc = 649,
            baseOdometerKm = 12000.0,
            accumulatedTrackedKm = 0.0,
            manualAdjustmentOffsetKm = 0.0,
            technicalSpecsJson = "{}",
            isActive = true
        )
        repository.insertMotorcycle(bike)

        val stdOil = task(taskId = "STD_ENGINE_OIL", motorcycleId = bike.id, category = "Engine", taskName = "Engine Oil & Filter", actionType = "Replace", intervalKm = 12000.0)
        val customOil = task(taskId = "AI_OIL_123", motorcycleId = bike.id, category = "Engine", taskName = "Motorový olej", actionType = "Replace", intervalKm = 12000.0)
        repository.insertMaintenanceTasks(listOf(stdOil, customOil))

        val generalLog = ServiceLogEntity(
            logId = "log_gen_service",
            motorcycleId = bike.id,
            taskId = "GENERAL_SERVICE",
            serviceTitle = "Full 12,000 km Service",
            performedAtIsoDate = "2026-05-15",
            odometerAtServiceKm = 12000.0,
            checklistItemsJson = json.encodeToString(
                listOf(
                    ServiceChecklistItem(
                        name = "Motorovy olej",
                        status = "DONE",
                        taskId = "AI_OIL_123",
                        subActions = listOf("OIL_CHANGED", "OIL_FILTER_CHANGED"),
                        completesTask = true
                    )
                )
            )
        )
        repository.insertServiceLog(generalLog)

        repository.deduplicateMaintenanceTasksForBike(bike.id)

        val tasks = repository.getMaintenanceTasksForMotorcycle(bike.id)
        assertEquals(1, tasks.size)
        assertEquals("STD_ENGINE_OIL", tasks[0].taskId)

        val logs = repository.getServiceLogsForMotorcycle(bike.id)
        assertEquals("GENERAL_SERVICE task ID is preserved for multi-task logs", "GENERAL_SERVICE", logs[0].taskId)
        val checklist = logs[0].parseChecklistOrEmpty()
        assertEquals(1, checklist.size)
        assertEquals("Checklist item remapped to STD_ENGINE_OIL", "STD_ENGINE_OIL", checklist[0].taskId)

        val eval = evaluator.evaluate(bike, tasks, logs, LocalDate.of(2026, 5, 20))
        assertEquals(TaskStatus.OK, eval[0].status)
    }

    // =========================================================================
    // 6. Checklist Item Deduplication within a Service Log
    // =========================================================================

    @Test
    fun testDeduplicateChecklistItemsMergesSameTaskChecklistEntries() {
        val existingTasks = listOf(
            task(taskId = "STD_AIR_FILTER", motorcycleId = "b1", category = "Engine", taskName = "Air Filter Element", actionType = "Replace")
        )
        val remapped = mapOf("OLD_AIR_TASK" to "STD_AIR_FILTER")

        val items = listOf(
            ServiceChecklistItem(
                name = "Air Filter",
                status = "CHECKED",
                taskId = "OLD_AIR_TASK",
                subActions = listOf("AIR_CHECKED"),
                note = "Visual inspection OK",
                completesTask = false
            ),
            ServiceChecklistItem(
                name = "Vzduchový filter",
                status = "DONE",
                taskId = "STD_AIR_FILTER",
                subActions = listOf("AIR_CHANGED"),
                note = "New OEM filter fitted",
                completesTask = true
            )
        )

        val deduped = ServiceTaskDeduplicator.deduplicateChecklistItems(items, existingTasks, remapped)

        assertEquals("2 items referring to air filter merge into 1", 1, deduped.size)
        val merged = deduped[0]
        assertEquals("STD_AIR_FILTER", merged.taskId)
        assertEquals("DONE", merged.status)
        assertTrue("completesTask is true", merged.completesTask)
        assertTrue("Contains AIR_CHECKED", merged.subActions.contains("AIR_CHECKED"))
        assertTrue("Contains AIR_CHANGED", merged.subActions.contains("AIR_CHANGED"))
        assertTrue("Notes merged", merged.note.contains("Visual inspection OK") && merged.note.contains("New OEM filter fitted"))
    }

    // =========================================================================
    // 7. Import AI Schedule JSON & Re-apply Standard Tasks (Rule 8)
    // =========================================================================

    @Test
    fun testImportAiScheduleAndReapplyingStandardTasksCreatesZeroDuplicates() = runBlocking {
        val bike = MotorcycleEntity(
            id = "bike_ai_import_dedup",
            make = "Yamaha",
            model = "MT-07",
            year = 2023,
            engineDisplacementCc = 689,
            baseOdometerKm = 10000.0,
            accumulatedTrackedKm = 0.0,
            manualAdjustmentOffsetKm = 0.0,
            technicalSpecsJson = "{}",
            isActive = true
        )
        repository.insertMotorcycle(bike)

        val aiScheduleJson = """
        {
          "make": "Yamaha",
          "model": "MT-07",
          "year": 2023,
          "maintenanceSchedule": [
            {
              "taskId": "AI_AIR_CLEANER",
              "category": "Engine",
              "taskName": "Air Cleaner Element",
              "actionType": "Replace",
              "intervalKm": 20000.0,
              "intervalMonths": 24,
              "oemPartNumbers": ["1WS-14451-00"],
              "notes": "Replace element every 20,000 km or 24 months"
            },
            {
              "taskId": "AI_ENGINE_OIL",
              "category": "Engine",
              "taskName": "Engine Oil & Filter",
              "actionType": "Replace",
              "intervalKm": 10000.0,
              "intervalMonths": 12,
              "oemPartNumbers": ["5GH-13440-60"],
              "notes": "Yamalube 10W-40 2.6L"
            },
            {
              "taskId": "AI_SPARK_PLUGS",
              "category": "Ignition",
              "taskName": "Spark Plugs",
              "actionType": "Replace",
              "intervalKm": 20000.0,
              "intervalMonths": 24,
              "oemPartNumbers": ["LMAR8A-9"],
              "notes": "Check electrode gap 0.8 mm"
            }
          ]
        }
        """.trimIndent()

        // 1. Import AI Schedule
        val importRes = importExportManager.importFromJsonString(aiScheduleJson, targetMotorcycleId = bike.id)
        assertTrue("AI manual schedule import succeeds", importRes.isSuccess)

        // 2. Re-apply standard factory tasks onto the bike
        val standardTasks = MotorcycleTemplateCatalog.buildStandardMaintenanceTasks(null, "en").map { dto ->
            MaintenanceTaskEntity(
                taskId = dto.taskId,
                motorcycleId = bike.id,
                category = dto.category,
                taskName = dto.taskName,
                actionType = dto.actionType,
                firstBreakInAtKm = dto.firstBreakInAtKm,
                intervalKm = dto.intervalKm,
                intervalMonths = dto.intervalMonths,
                warningThresholdKm = dto.warningThresholdKm,
                warningThresholdDays = dto.warningThresholdDays,
                oemPartNumbersJson = json.encodeToString(dto.oemPartNumbers),
                notes = dto.notes
            )
        }
        repository.insertMaintenanceTasks(standardTasks)

        // 3. Deduplicate
        repository.deduplicateMaintenanceTasksForBike(bike.id)

        // 4. Verify ZERO duplicate tasks exist
        val finalTasks = repository.getMaintenanceTasksForMotorcycle(bike.id)
        val canonicalKeys = finalTasks.map {
            ServiceTaskDeduplicator.canonicalTaskKey(it.taskId, it.taskName, it.category)
        }
        assertEquals("All tasks must have unique canonical keys (zero duplicates)", canonicalKeys.size, canonicalKeys.distinct().size)

        // Verify standard tasks survived and absorbed AI OEM parts & notes
        val airTask = finalTasks.first { it.taskId == "STD_AIR_FILTER" }
        val airOemParts = json.decodeFromString<List<String>>(airTask.oemPartNumbersJson)
        assertTrue("Air filter has AI OEM part 1WS-14451-00", airOemParts.contains("1WS-14451-00"))

        val oilTask = finalTasks.first { it.taskId == "STD_ENGINE_OIL" }
        val oilOemParts = json.decodeFromString<List<String>>(oilTask.oemPartNumbersJson)
        assertTrue("Oil task has AI OEM part 5GH-13440-60", oilOemParts.contains("5GH-13440-60"))

        val sparkTask = finalTasks.first { it.taskId == "STD_SPARK_PLUGS" }
        val sparkOemParts = json.decodeFromString<List<String>>(sparkTask.oemPartNumbersJson)
        assertTrue("Spark plugs task has AI OEM part LMAR8A-9", sparkOemParts.contains("LMAR8A-9"))
    }
}

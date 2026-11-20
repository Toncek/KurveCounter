package com.motogeek.telemetry

import android.content.ContextWrapper
import com.motogeek.telemetry.data.local.*
import com.motogeek.telemetry.domain.maintenance.*
import com.motogeek.telemetry.domain.onboarding.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.time.LocalDate
import java.util.UUID

class NewBikeTutorialTest {

    private class FakeContext : ContextWrapper(null) {
        private val tempDir = File(System.getProperty("java.io.tmpdir"), "onboarding_ctx_${UUID.randomUUID()}").apply { mkdirs() }
        override fun getFilesDir(): File = tempDir
        override fun getCacheDir(): File = tempDir
    }

    private class InMemoryTutorialDraftPersistence : TutorialDraftPersistence {
        var storedJson: String? = null
        override fun readDraftJson(): String? = storedJson
        override fun writeDraftJson(rawJson: String) {
            storedJson = rawJson
        }
        override fun clearDraftJson() {
            storedJson = null
        }
    }

    /**
     * Local FakeMotoGeekDao simulating Room SQLite's ON CONFLICT REPLACE cascade deletion
     * on insertMotorcycle vs in-place UPDATE on updateMotorcycle.
     */
    private class FakeMotoGeekDao : MotoGeekDao {
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
            val removedRideIds = rideSessions.filter { it.motorcycleId == bikeId }.map { it.rideId }.toSet()
            rideSessions.removeAll { it.motorcycleId == bikeId }
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

        override suspend fun deleteMotorcycle(id: String) = cascadeDeleteMotorcycle(id)

        override suspend fun deleteAllMotorcycles() {
            motorcycles.keys.toList().forEach { cascadeDeleteMotorcycle(it) }
        }

        override fun getAllMotorcycles(): Flow<List<MotorcycleEntity>> = flowOf(motorcycles.values.toList())
        override suspend fun getMotorcycleById(id: String): MotorcycleEntity? = motorcycles[id]
        override suspend fun getActiveMotorcycle(): MotorcycleEntity? = motorcycles.values.firstOrNull { it.isActive }
        override fun getActiveMotorcycleFlow(): Flow<MotorcycleEntity?> = flowOf(motorcycles.values.firstOrNull { it.isActive })

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

        override fun getAllRideSessions(): Flow<List<RideSessionEntity>> = flowOf(rideSessions)
        override fun getRideSessionsForMotorcycle(motorcycleId: String): Flow<List<RideSessionEntity>> =
            flowOf(rideSessions.filter { it.motorcycleId == motorcycleId })
        override suspend fun getRideSessionsForMotorcycleList(motorcycleId: String): List<RideSessionEntity> =
            rideSessions.filter { it.motorcycleId == motorcycleId }
        override suspend fun deleteRideSession(rideId: Long) {
            rideSessions.removeAll { it.rideId == rideId }
        }
        override suspend fun updateRideSessionDetails(rideId: Long, name: String?, notes: String?) {}
        override suspend fun insertCornerEvents(cornerEvents: List<CornerEventEntity>) {
            cornerEvents.forEach { this.cornerEvents.add(it.copy(cornerId = nextCornerId++)) }
        }
        override fun getCornerEventsForRide(rideId: Long): Flow<List<CornerEventEntity>> =
            flowOf(cornerEvents.filter { it.rideId == rideId })
        override suspend fun getCornerEventsListForRide(rideId: Long): List<CornerEventEntity> =
            cornerEvents.filter { it.rideId == rideId }
        override suspend fun insertTelemetryTrackPoints(trackPoints: List<TelemetryTrackPointEntity>) {
            trackPoints.forEach { this.trackPoints.add(it.copy(pointId = nextPointId++)) }
        }
        override fun getTelemetryTrackPointsForRide(rideId: Long): Flow<List<TelemetryTrackPointEntity>> =
            flowOf(trackPoints.filter { it.rideId == rideId })
        override suspend fun getTelemetryTrackPointsListForRide(rideId: Long): List<TelemetryTrackPointEntity> =
            trackPoints.filter { it.rideId == rideId }

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
            flowOf(serviceLogs.filter { it.motorcycleId == motorcycleId })

        override suspend fun insertFuelLog(fuelLog: FuelLogEntity) {
            val id = if (fuelLog.fuelId > 0L) fuelLog.fuelId else nextFuelId++
            fuelLogs.removeAll { it.fuelId == id }
            fuelLogs.add(fuelLog.copy(fuelId = id))
        }
        override suspend fun updateFuelLog(fuelLog: FuelLogEntity) {}
        override suspend fun deleteFuelLog(fuelId: Long) { fuelLogs.removeAll { it.fuelId == fuelId } }
        override fun getFuelLogsForMotorcycle(motorcycleId: String): Flow<List<FuelLogEntity>> =
            flowOf(fuelLogs.filter { it.motorcycleId == motorcycleId })
        override suspend fun getFuelLogsForMotorcycleList(motorcycleId: String): List<FuelLogEntity> =
            fuelLogs.filter { it.motorcycleId == motorcycleId }

        override suspend fun insertPartLog(partLog: PartLogEntity) {
            val id = if (partLog.partId > 0L) partLog.partId else nextPartId++
            partLogs.removeAll { it.partId == id }
            partLogs.add(partLog.copy(partId = id))
        }
        override suspend fun updatePartLog(partLog: PartLogEntity) {}
        override suspend fun deletePartLog(partId: Long) { partLogs.removeAll { it.partId == partId } }
        override fun getPartLogsForMotorcycle(motorcycleId: String): Flow<List<PartLogEntity>> =
            flowOf(partLogs.filter { it.motorcycleId == motorcycleId })
        override suspend fun getPartLogsForMotorcycleList(motorcycleId: String): List<PartLogEntity> =
            partLogs.filter { it.motorcycleId == motorcycleId }

        override suspend fun insertReminder(reminder: ReminderEntity) {
            val id = if (reminder.reminderId > 0L) reminder.reminderId else nextReminderId++
            reminders.add(reminder.copy(reminderId = id))
        }
        override suspend fun updateReminder(reminder: ReminderEntity) {}
        override suspend fun deleteReminder(reminderId: Long) {}
        override fun getRemindersForMotorcycle(motorcycleId: String): Flow<List<ReminderEntity>> =
            flowOf(reminders.filter { it.motorcycleId == motorcycleId })
        override suspend fun getRemindersForMotorcycleList(motorcycleId: String): List<ReminderEntity> =
            reminders.filter { it.motorcycleId == motorcycleId }

        override suspend fun insertAppLog(appLog: AppLogEntity) {
            val id = if (appLog.logId.isNotBlank()) appLog.logId else UUID.randomUUID().toString()
            appLogs.removeAll { it.logId == id }
            appLogs.add(appLog.copy(logId = id))
        }
        override suspend fun insertAppLogs(appLogs: List<AppLogEntity>) { appLogs.forEach { insertAppLog(it) } }
        override fun getAppLogsForMotorcycle(motorcycleId: String): Flow<List<AppLogEntity>> =
            flowOf(appLogs.filter { it.motorcycleId == motorcycleId })
        override suspend fun getAppLogsForMotorcycleList(motorcycleId: String): List<AppLogEntity> =
            appLogs.filter { it.motorcycleId == motorcycleId }
        override suspend fun deleteAppLog(logId: String) { appLogs.removeAll { it.logId == logId } }
        override suspend fun clearAppLogs(motorcycleId: String) { appLogs.removeAll { it.motorcycleId == motorcycleId } }

        override suspend fun getTotalServiceCost(motorcycleId: String): Double? = null
        override suspend fun getTotalPartsCost(motorcycleId: String): Double? = null
        override suspend fun getTotalFuelCost(motorcycleId: String): Double? = null
        override fun getAvgFuelConsumption(motorcycleId: String): Flow<Double?> = flowOf(0.0)
    }

    private fun createCoordinator(
        dao: FakeMotoGeekDao = FakeMotoGeekDao(),
        persistence: InMemoryTutorialDraftPersistence = InMemoryTutorialDraftPersistence(),
        today: LocalDate = LocalDate.of(2026, 11, 20)
    ): Triple<NewBikeTutorialCoordinator, MotoGeekRepository, InMemoryTutorialDraftPersistence> {
        val repo = MotoGeekRepository(dao)
        val store = NewBikeTutorialDraftStore(persistence)
        return Triple(NewBikeTutorialCoordinator(repo, store) { today }, repo, persistence)
    }

    @Test
    fun scenario1_zeroReferenceDefaultsAndInputGuards() {
        val defaultDto = TechnicalSpecsDto()
        assertEquals("", defaultDto.brakeFluidType)
        assertNull(defaultDto.purchaseCondition)
        assertNull(defaultDto.oilCapacityLiters)
        assertNull(defaultDto.frontTirePressurePsi)
        assertEquals("", defaultDto.oilViscosityAndSpec)

        val draft = NewBikeTutorialDraft()
        assertEquals("", draft.make)
        assertEquals("", draft.model)
        assertEquals("", draft.yearText)
        assertEquals("", draft.engineDisplacementCcText)
        assertEquals("", draft.bikeType)
        assertEquals("", draft.currentOdometerKmText)
        assertEquals("", draft.buyingPriceText)
        assertEquals("€", draft.currencySymbol)
        assertNull(draft.purchaseCondition)
        assertTrue(draft.maintenanceTasks.isEmpty())
        assertTrue(draft.maintenanceReviewItems.isEmpty())
        assertFalse(draft.hasAnyUserInput())

        val specsFromEmpty = draft.toTechnicalSpecsDto()
        assertEquals("", specsFromEmpty.brakeFluidType)
        assertNull(specsFromEmpty.purchaseCondition)
        assertNull(specsFromEmpty.oilCapacityLiters)
        assertNull(specsFromEmpty.frontTirePressurePsi)

        val entityFromEmpty = draft.toMotorcycleEntity(todayIso = "2026-11-20", isActive = true)
        assertEquals(0, entityFromEmpty.year)
        assertEquals(0, entityFromEmpty.engineDisplacementCc)
        assertEquals(0.0, entityFromEmpty.baseOdometerKm, 0.001)
        assertEquals(0.0, entityFromEmpty.buyingPrice, 0.001)
        assertEquals("", entityFromEmpty.type)
        assertEquals("2026-11-20", entityFromEmpty.startDateIso)

        val invalidNumbersDraft = draft.copy(
            currentOdometerKmText = "NaN",
            buyingPriceText = "Infinity",
            oilCapacityLitersText = "-3.5",
            yearText = "-2020"
        )
        assertFalse(invalidNumbersDraft.hasAnyUserInput())
        val zeroOdoDraft = draft.copy(currentOdometerKmText = "0")
        assertTrue(zeroOdoDraft.hasAnyUserInput())
    }

    @Test
    fun scenario2_fieldAutosaveAndReloadAcrossSessions() = runBlocking {
        val dao = FakeMotoGeekDao()
        val persistence = InMemoryTutorialDraftPersistence()
        val (coord1, _, _) = createCoordinator(dao, persistence)

        val started = coord1.startOrResume()
        assertTrue(started.isOpen)
        assertEquals(TutorialStage.BIKE_BASICS, started.stage)

        val edited = started.copy(
            stage = TutorialStage.SPECS_AND_SCHEDULE,
            purchaseCondition = BikePurchaseCondition.USED,
            make = "Honda",
            model = "CB650R",
            yearText = "2022",
            engineDisplacementCcText = "649",
            bikeType = "Naked",
            currentOdometerKmText = "14500",
            buyingPriceText = "6800",
            oilCapacityLitersText = "2.7",
            brakeFluidType = "DOT 4"
        )
        val saved = coord1.updateDraft(edited)
        assertTrue(saved.lastAutosavedEpochMs > 0L)
        assertNotNull(persistence.storedJson)

        // Simulate app restart with fresh Store & Coordinator backed by same persistence
        val (coord2, _, _) = createCoordinator(dao, persistence)
        val resumed = coord2.startOrResume()
        assertEquals(started.draftBikeId, resumed.draftBikeId)
        assertEquals(TutorialStage.SPECS_AND_SCHEDULE, resumed.stage)
        assertEquals(BikePurchaseCondition.USED, resumed.purchaseCondition)
        assertEquals("Honda", resumed.make)
        assertEquals("CB650R", resumed.model)
        assertEquals("2022", resumed.yearText)
        assertEquals("649", resumed.engineDisplacementCcText)
        assertEquals("Naked", resumed.bikeType)
        assertEquals("14500", resumed.currentOdometerKmText)
        assertEquals("6800", resumed.buyingPriceText)
        assertEquals("2.7", resumed.oilCapacityLitersText)
        assertEquals("DOT 4", resumed.brakeFluidType)
    }

    @Test
    fun scenario3_updateDraftUsesUpdateMotorcycleWithoutCascadeDelete() = runBlocking {
        val dao = FakeMotoGeekDao()
        val persistence = InMemoryTutorialDraftPersistence()
        val (coord, repo, _) = createCoordinator(dao, persistence)

        val started = coord.startOrResume().copy(
            purchaseCondition = BikePurchaseCondition.USED,
            make = "Yamaha",
            model = "MT-09",
            yearText = "2023",
            currentOdometerKmText = "12000",
            maintenanceTasks = listOf(
                MaintenanceTaskDto(
                    taskId = "STD_ENGINE_OIL",
                    category = "Engine",
                    taskName = "Engine Oil & Filter",
                    actionType = "Replace",
                    intervalKm = 10000.0,
                    intervalMonths = 12
                )
            )
        )
        val reviewDraft = coord.saveBikeAndOpenMaintenanceReview(started)
        val bikeId = reviewDraft.persistedBikeId!!

        // Attach child records that would be wiped if insertMotorcycle REPLACE ran
        repo.insertRideSession(
            RideSessionEntity(
                motorcycleId = bikeId,
                startTimeEpochMs = 1700000000000L,
                endTimeEpochMs = 1700003600000L,
                distanceKm = 85.0,
                avgSpeedKmh = 70.0,
                maxSpeedKmh = 140.0,
                maxLeanLeftDeg = 41.0,
                maxLeanRightDeg = 43.0,
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
                name = "Mountain Loop",
                notes = null
            )
        )
        repo.insertFuelLog(
            FuelLogEntity(
                motorcycleId = bikeId,
                dateIso = "2026-11-15",
                odometerKm = 12050.0,
                liters = 12.5,
                pricePerLiter = 1.68,
                totalCost = 21.0,
                isFullTank = true,
                notes = ""
            )
        )
        repo.insertPartLog(
            PartLogEntity(
                motorcycleId = bikeId,
                dateIso = "2026-11-10",
                odometerKm = 11900.0,
                partName = "Oil Filter",
                brand = "Yamaha",
                partNumber = "5GH-13440",
                cost = 15.0,
                notes = ""
            )
        )

        // Now update draft while persistedBikeId != null
        coord.updateDraft(reviewDraft.copy(nickname = "MT-09 SP Custom", currentOdometerKmText = "12100"))

        val updatedBike = repo.getMotorcycleById(bikeId)
        assertNotNull(updatedBike)
        assertEquals("MT-09 SP Custom", updatedBike!!.name)
        assertEquals(12100.0, updatedBike.totalOdometerKm, 0.01)
        assertEquals(1, dao.rideSessions.count { it.motorcycleId == bikeId })
        assertEquals(1, dao.fuelLogs.count { it.motorcycleId == bikeId })
        assertEquals(1, dao.partLogs.count { it.motorcycleId == bikeId })
        assertEquals(1, dao.maintenanceTasks.count { it.motorcycleId == bikeId })
        assertEquals(1, dao.serviceLogs.count { it.motorcycleId == bikeId })
    }

    @Test
    fun scenario4_llmJsonPastePreservesUserBasicsAndTorquesAndDeduplicates() = runBlocking {
        val (coord, repo, _) = createCoordinator()
        val importExport = ImportExportManager(FakeContext(), repo)

        val prompt = importExport.buildAiServiceManualPrompt(
            make = "Kawasaki",
            model = "Z900",
            year = 2022,
            engineDisplacementCc = 948,
            type = "Naked",
            langCode = "en"
        )
        assertTrue(prompt.contains("2022 Kawasaki Z900 (948cc, Naked)"))

        val delegatedPrompt = importExport.buildAiServiceManualPrompt(
            MotorcycleEntity(
                id = "b1", make = "Kawasaki", model = "Z900", year = 2022,
                engineDisplacementCc = 948, baseOdometerKm = 0.0,
                accumulatedTrackedKm = 0.0, manualAdjustmentOffsetKm = 0.0,
                technicalSpecsJson = "{}", type = "Naked"
            ),
            "en"
        )
        assertEquals(prompt, delegatedPrompt)

        val userDraft = coord.startOrResume().copy(
            purchaseCondition = BikePurchaseCondition.USED,
            make = "Kawasaki",
            model = "Z900",
            yearText = "2022",
            engineDisplacementCcText = "948",
            bikeType = "Naked",
            currentOdometerKmText = "14200",
            buyingPriceText = "7900",
            currencySymbol = "$"
        )

        val llmJson = """
        ```json
        {
          "motorcycleInfo": {
            "make": "WrongMake",
            "model": "WrongModel",
            "year": 2018,
            "engineDisplacementCc": 500
          },
          "technicalSpecs": {
            "oilCapacityLiters": 3.8,
            "oilViscosityAndSpec": "10W-40 JASO MA2",
            "brakeFluidType": "DOT 4",
            "frontTirePressurePsi": 36.0,
            "rearTirePressurePsi": 42.0,
            "rearAxleNutTorqueNm": 108.0,
            "boltTorquesNm": {
              "Clutch Cover Bolts": 11.0,
              "Swingarm Pivot": 108.0
            }
          },
          "maintenanceSchedule": [
            {
              "taskId": "OIL_CHANGE",
              "category": "Engine",
              "taskName": "Engine Oil & Filter Change",
              "actionType": "Replace",
              "firstBreakInAtKm": 1000.0,
              "intervalKm": 12000.0,
              "intervalMonths": 12,
              "notes": "Replace oil and filter"
            },
            {
              "taskId": "STD_ENGINE_OIL",
              "category": "Engine",
              "taskName": "Engine Oil",
              "actionType": "Replace",
              "intervalKm": 12000.0,
              "intervalMonths": 12,
              "notes": "Duplicate entry"
            },
            {
              "taskId": "STD_BRAKE_FLUID",
              "category": "Brakes",
              "taskName": "Brake Fluid Flush",
              "actionType": "Replace",
              "intervalMonths": 24,
              "notes": "DOT 4"
            }
          ]
        }
        ```
        """.trimIndent()

        val result = coord.applyLlmJsonToDraft(userDraft, llmJson)
        assertTrue(result.isSuccess)
        val updated = result.getOrThrow()

        // User's entered basics, condition, mileage, price, currency must NOT be overwritten
        assertEquals(BikePurchaseCondition.USED, updated.purchaseCondition)
        assertEquals("Kawasaki", updated.make)
        assertEquals("Z900", updated.model)
        assertEquals("2022", updated.yearText)
        assertEquals("948", updated.engineDisplacementCcText)
        assertEquals("Naked", updated.bikeType)
        assertEquals("14200", updated.currentOdometerKmText)
        assertEquals("7900", updated.buyingPriceText)
        assertEquals("$", updated.currencySymbol)

        // Editable specs & full boltTorquesNm preserved
        assertEquals("3.8", updated.oilCapacityLitersText)
        assertEquals("10W-40 JASO MA2", updated.oilViscosityAndSpec)
        assertEquals("DOT 4", updated.brakeFluidType)
        assertEquals("36", updated.frontTirePressurePsiText)
        assertEquals("42", updated.rearTirePressurePsiText)
        assertEquals("108", updated.rearAxleNutTorqueNmText)
        assertTrue(updated.extraSpecsJson.contains("Clutch Cover Bolts"))

        val torqueMap = updated.toTechnicalSpecsDto().toConsolidatedTorqueMap()
        assertEquals(11.0, torqueMap["Clutch Cover Bolts"]!!, 0.01)
        assertEquals(108.0, torqueMap["Swingarm Pivot"]!!, 0.01)

        // Schedule deduplicated (2 oil tasks -> 1)
        assertEquals(2, updated.maintenanceTasks.size)
        assertEquals(1, updated.maintenanceTasks.count { it.taskId == "STD_ENGINE_OIL" })
    }

    @Test
    fun scenario5_usedBikeServiceBookBaselineAndNanZeroEdgeCases() {
        val today = LocalDate.of(2026, 11, 20)
        val tasks = listOf(
            MaintenanceTaskDto(
                taskId = "STD_ENGINE_OIL",
                category = "Engine",
                taskName = "Engine Oil",
                actionType = "Replace",
                firstBreakInAtKm = 1000.0,
                intervalKm = 10000.0,
                intervalMonths = 12
            ),
            MaintenanceTaskDto(
                taskId = "STD_BRAKE_FLUID",
                category = "Brakes",
                taskName = "Brake Fluid",
                actionType = "Replace",
                intervalKm = null,
                intervalMonths = 24
            )
        )

        // Normal USED bike at 24500 km (2021)
        val items = ServiceBookBaselineCalculator.buildExpectedHistory(
            condition = BikePurchaseCondition.USED,
            currentOdometerKm = 24500.0,
            bikeYear = 2021,
            purchaseDateIso = "2026-11-20",
            tasks = tasks,
            today = today
        )
        val oilItem = items.first { it.taskId == "STD_ENGINE_OIL" }
        assertFalse(oilItem.wasNeverDone)
        assertEquals("20000", oilItem.lastPerformedKmText)

        val brakeItem = items.first { it.taskId == "STD_BRAKE_FLUID" }
        assertFalse(brakeItem.wasNeverDone)
        assertEquals("2025-01-01", brakeItem.lastPerformedDateIso)

        // Low-mileage USED bike (3500 km, 2021 -> below 10000 km interval, relies on intervalMonths time milestone)
        val lowMileageItems = ServiceBookBaselineCalculator.buildExpectedHistory(
            condition = BikePurchaseCondition.USED,
            currentOdometerKm = 3500.0,
            bikeYear = 2021,
            purchaseDateIso = "2026-11-20",
            tasks = tasks,
            today = today
        )
        val lowOil = lowMileageItems.first { it.taskId == "STD_ENGINE_OIL" }
        assertFalse(lowOil.wasNeverDone)
        assertEquals("2026-01-01", lowOil.lastPerformedDateIso)
        val lowKm = lowOil.lastPerformedKmText.toDouble()
        assertTrue(lowKm in 1000.0..3500.0)

        // NaN / Infinity / 0 / negative guards
        val poisonTasks = listOf(
            MaintenanceTaskDto(
                taskId = "POISON_1",
                category = "Engine",
                taskName = "Zero Interval",
                actionType = "Inspect",
                firstBreakInAtKm = Double.NaN,
                intervalKm = 0.0,
                intervalMonths = 0
            ),
            MaintenanceTaskDto(
                taskId = "POISON_2",
                category = "Engine",
                taskName = "Inf Interval",
                actionType = "Inspect",
                firstBreakInAtKm = Double.POSITIVE_INFINITY,
                intervalKm = Double.NEGATIVE_INFINITY,
                intervalMonths = -12
            )
        )
        val poisonItems = ServiceBookBaselineCalculator.buildExpectedHistory(
            condition = BikePurchaseCondition.USED,
            currentOdometerKm = Double.NaN,
            bikeYear = 2026,
            purchaseDateIso = "invalid-date",
            tasks = poisonTasks,
            today = today
        )
        assertEquals(2, poisonItems.size)
        poisonItems.forEach { item ->
            assertTrue(item.wasNeverDone)
            assertNull(
                ServiceBookBaselineCalculator.buildBaselineServiceLog(
                    motorcycleId = "b1",
                    item = item,
                    condition = BikePurchaseCondition.USED,
                    defaultDateIso = "2026-11-20"
                )
            )
        }
    }

    @Test
    fun scenario6_newBikePreservesFirstBreakInAtKm() = runBlocking {
        val today = LocalDate.of(2026, 11, 20)
        val (coord, repo, _) = createCoordinator(today = today)
        val evaluator = MaintenanceEvaluator()

        val breakInTask = MaintenanceTaskDto(
            taskId = "STD_ENGINE_OIL",
            category = "Engine",
            taskName = "Engine Oil & Filter",
            actionType = "Replace",
            firstBreakInAtKm = 1000.0,
            intervalKm = 10000.0,
            intervalMonths = 12,
            warningThresholdKm = 500.0
        )

        // NEW bike at 350 km (< 1000 km break-in) -> no baseline log, break-in preserved
        val draft350 = coord.startOrResume().copy(
            purchaseCondition = BikePurchaseCondition.NEW,
            make = "Yamaha",
            model = "MT-07",
            yearText = "2026",
            currentOdometerKmText = "350",
            purchaseDateIso = "2026-11-01",
            maintenanceTasks = listOf(breakInTask)
        )
        val review350 = coord.saveBikeAndOpenMaintenanceReview(draft350)
        assertTrue(review350.maintenanceReviewItems.single().wasNeverDone)

        val bikeId = review350.persistedBikeId!!
        val bike = repo.getMotorcycleById(bikeId)!!
        val tasks = repo.getMaintenanceTasksForMotorcycle(bikeId)
        val logs = repo.getServiceLogsForMotorcycle(bikeId)
        assertTrue(logs.isEmpty())

        val eval = evaluator.evaluate(bike, tasks, logs, today).single()
        assertEquals(1000.0, eval.nextDueOdometerKm!!, 0.01)
        assertEquals(650.0, eval.remainingKm!!, 0.01)
        assertEquals(TaskStatus.OK, eval.status)

        // NEW bike at 0 km -> buildBaselineServiceLog returns null when !isUserModified
        val zeroNewItem = MaintenanceHistoryReviewItem(
            taskId = "STD_ENGINE_OIL",
            category = "Engine",
            taskName = "Engine Oil",
            lastPerformedKmText = "0",
            wasNeverDone = false,
            isUserModified = false
        )
        assertNull(
            ServiceBookBaselineCalculator.buildBaselineServiceLog(
                motorcycleId = bikeId,
                item = zeroNewItem,
                condition = BikePurchaseCondition.NEW,
                defaultDateIso = "2026-11-20"
            )
        )

        // NEW bike at 1500 km (>= 1000 km break-in) -> baseline at 1000 km, next due 11000 km
        val history1500 = ServiceBookBaselineCalculator.buildExpectedHistory(
            condition = BikePurchaseCondition.NEW,
            currentOdometerKm = 1500.0,
            bikeYear = 2026,
            purchaseDateIso = "2026-09-01",
            tasks = listOf(breakInTask),
            today = today
        ).single()
        assertFalse(history1500.wasNeverDone)
        assertEquals("1000", history1500.lastPerformedKmText)
    }

    @Test
    fun scenario7_maintenanceReviewRewriteRecalculatesEvaluatorStatusAndFinalizes() = runBlocking {
        val today = LocalDate.of(2026, 11, 20)
        val (coord, repo, persistence) = createCoordinator(today = today)
        val evaluator = MaintenanceEvaluator()

        val oilTask = MaintenanceTaskDto(
            taskId = "STD_ENGINE_OIL",
            category = "Engine",
            taskName = "Engine Oil",
            actionType = "Replace",
            firstBreakInAtKm = 1000.0,
            intervalKm = 10000.0,
            warningThresholdKm = 500.0
        )

        val draft = coord.startOrResume().copy(
            purchaseCondition = BikePurchaseCondition.USED,
            make = "BMW",
            model = "R1250GS",
            yearText = "2022",
            currentOdometerKmText = "24500",
            purchaseDateIso = "2026-11-20",
            maintenanceTasks = listOf(oilTask)
        )
        val reviewDraft = coord.saveBikeAndOpenMaintenanceReview(draft)
        val bikeId = reviewDraft.persistedBikeId!!
        val bike = repo.getMotorcycleById(bikeId)!!
        val tasks = repo.getMaintenanceTasksForMotorcycle(bikeId)

        // Initially baseline at 20000 km -> nextDue 30000 -> remaining 5500 -> OK
        val initialEval = evaluator.evaluate(bike, tasks, repo.getServiceLogsForMotorcycle(bikeId), today).single()
        assertEquals(TaskStatus.OK, initialEval.status)
        assertEquals(30000.0, initialEval.nextDueOdometerKm!!, 0.01)

        // User rewrites last oil change to 14200 km -> nextDue 24200 -> remaining -300 -> OVERDUE
        val existingItem = reviewDraft.maintenanceReviewItems.single()
        val rewrittenDraft = coord.updateMaintenanceReviewItem(
            reviewDraft,
            existingItem.copy(lastPerformedKmText = "14200", lastPerformedDateIso = "2025-08-10")
        )
        assertTrue(rewrittenDraft.maintenanceReviewItems.single().isUserModified)

        val logsAfterRewrite = repo.getServiceLogsForMotorcycle(bikeId)
        assertEquals(1, logsAfterRewrite.size)
        assertEquals(ServiceBookBaselineCalculator.baselineLogId(bikeId, "STD_ENGINE_OIL"), logsAfterRewrite.single().logId)

        val updatedEval = evaluator.evaluate(bike, tasks, logsAfterRewrite, today).single()
        assertEquals(TaskStatus.OVERDUE, updatedEval.status)
        assertEquals(24200.0, updatedEval.nextDueOdometerKm!!, 0.01)
        assertEquals(-300.0, updatedEval.remainingKm!!, 0.01)

        // Add another task in maintenance review
        val chainTask = MaintenanceTaskDto(
            taskId = "STD_DRIVE_CHAIN",
            category = "Drivetrain",
            taskName = "Chain & Sprockets",
            actionType = "Replace",
            intervalKm = 25000.0
        )
        val withAddedTask = coord.addMaintenanceReviewTask(
            draft = rewrittenDraft,
            task = chainTask,
            lastPerformedKmText = "",
            wasNeverDone = true
        )
        assertEquals(2, withAddedTask.maintenanceReviewItems.size)
        assertNull(repo.getServiceLogsForMotorcycle(bikeId).find { it.taskId == "STD_DRIVE_CHAIN" })

        // Finalize setup
        val finalized = coord.saveFinal(withAddedTask)
        assertFalse(finalized.isOpen)
        assertNull(persistence.storedJson)
        val appLogs = repo.getAppLogsForMotorcycleList(bikeId)
        assertTrue(appLogs.any { it.title == "Motorcycle Setup Completed" })
    }

    @Test
    fun scenario8_bothDiscardModes() = runBlocking {
        val dao = FakeMotoGeekDao()
        val persistence = InMemoryTutorialDraftPersistence()
        val (coord, repo, _) = createCoordinator(dao, persistence)

        // Seed previous active bike
        val prevBike = MotorcycleEntity(
            id = "prev_bike",
            make = "Ducati",
            model = "Monster",
            year = 2020,
            engineDisplacementCc = 821,
            baseOdometerKm = 5000.0,
            accumulatedTrackedKm = 0.0,
            manualAdjustmentOffsetKm = 0.0,
            technicalSpecsJson = "{}",
            isActive = true
        )
        repo.insertMotorcycle(prevBike)

        val oilTask = MaintenanceTaskDto(
            taskId = "STD_ENGINE_OIL",
            category = "Engine",
            taskName = "Engine Oil",
            actionType = "Replace",
            intervalKm = 10000.0
        )

        // Mode A: discardDraft(..., deletePersistedBike = true) deletes draft bike & restores prev_bike
        val draftA = coord.startOrResume().copy(
            purchaseCondition = BikePurchaseCondition.USED,
            make = "Suzuki",
            model = "GSX-8S",
            yearText = "2024",
            currentOdometerKmText = "12000",
            maintenanceTasks = listOf(oilTask)
        )
        val reviewA = coord.saveBikeAndOpenMaintenanceReview(draftA)
        val draftBikeIdA = reviewA.persistedBikeId!!
        assertEquals(draftBikeIdA, repo.getActiveMotorcycle()?.id)

        val discardedA = coord.discardDraft(reviewA, deletePersistedBike = true)
        assertFalse(discardedA.isOpen)
        assertNull(discardedA.persistedBikeId)
        assertNull(persistence.storedJson)
        assertNull(repo.getMotorcycleById(draftBikeIdA))
        assertEquals("prev_bike", repo.getActiveMotorcycle()?.id)

        // Mode B: discardDraft(..., deletePersistedBike = false) keeps bike & non-baseline logs, removes baseline_${bikeId}_*
        val draftB = coord.startOrResume().copy(
            purchaseCondition = BikePurchaseCondition.USED,
            make = "Triumph",
            model = "Street Triple",
            yearText = "2023",
            currentOdometerKmText = "15000",
            maintenanceTasks = listOf(oilTask)
        )
        val reviewB = coord.saveBikeAndOpenMaintenanceReview(draftB)
        val draftBikeIdB = reviewB.persistedBikeId!!

        // Add a non-baseline user log to ensure only baseline_${bikeId}_* logs are deleted
        repo.insertServiceLog(
            ServiceLogEntity(
                logId = "manual_log_1",
                motorcycleId = draftBikeIdB,
                taskId = "STD_ENGINE_OIL",
                performedAtIsoDate = "2026-11-20",
                odometerAtServiceKm = 15000.0
            )
        )
        assertEquals(2, repo.getServiceLogsForMotorcycle(draftBikeIdB).size)

        val discardedB = coord.discardDraft(reviewB, deletePersistedBike = false)
        assertFalse(discardedB.isOpen)
        assertNull(persistence.storedJson)
        assertNotNull(repo.getMotorcycleById(draftBikeIdB))
        val remainingLogs = repo.getServiceLogsForMotorcycle(draftBikeIdB)
        assertEquals(1, remainingLogs.size)
        assertEquals("manual_log_1", remainingLogs.single().logId)
    }
}

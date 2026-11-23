package com.motogeek.telemetry

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.motogeek.telemetry.data.local.AppLogEntity
import com.motogeek.telemetry.data.local.CornerEventEntity
import com.motogeek.telemetry.data.local.FuelLogEntity
import com.motogeek.telemetry.data.local.MaintenanceTaskEntity
import com.motogeek.telemetry.data.local.MotoGeekDatabase
import com.motogeek.telemetry.data.local.MotoGeekRepository
import com.motogeek.telemetry.data.local.MotorcycleEntity
import com.motogeek.telemetry.data.local.PartLogEntity
import com.motogeek.telemetry.data.local.ReminderEntity
import com.motogeek.telemetry.data.local.RideSessionEntity
import com.motogeek.telemetry.data.local.ServiceChecklistItem
import com.motogeek.telemetry.data.local.ServiceLogEntity
import com.motogeek.telemetry.data.local.TelemetryTrackPointEntity
import com.motogeek.telemetry.domain.dsp.MotorcycleSensorFusionEngine
import com.motogeek.telemetry.domain.maintenance.GarageBackupPayload
import com.motogeek.telemetry.domain.maintenance.ImportExportManager
import com.motogeek.telemetry.domain.maintenance.MaintenanceEvaluator
import com.motogeek.telemetry.domain.maintenance.MotorcycleTemplateCatalog
import com.motogeek.telemetry.domain.maintenance.RestoreMode
import com.motogeek.telemetry.domain.maintenance.ServiceChecklistCatalog
import com.motogeek.telemetry.domain.maintenance.TaskStatus
import com.motogeek.telemetry.domain.telemetry.GpxTelemetryCodec
import com.motogeek.telemetry.ui.RiderAnalyticsCalculator
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.time.LocalDate
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class DeepFunctionalInstrumentedTest {

    private lateinit var baseContext: Context
    private lateinit var isolatedDir: File
    private lateinit var isolatedContext: Context
    private lateinit var db: MotoGeekDatabase
    private lateinit var repository: MotoGeekRepository
    private val prefPrefix = "deep_test_${UUID.randomUUID()}_"

    @Before
    fun setUp() {
        baseContext = ApplicationProvider.getApplicationContext()
        isolatedDir = File(baseContext.cacheDir, "deep_functional_${UUID.randomUUID()}").apply { mkdirs() }
        isolatedContext = object : ContextWrapper(baseContext) {
            override fun getFilesDir(): File = isolatedDir
            override fun getCacheDir(): File = isolatedDir
            override fun getSharedPreferences(name: String, mode: Int): SharedPreferences =
                baseContext.getSharedPreferences("$prefPrefix$name", mode)
        }
        db = Room.inMemoryDatabaseBuilder(baseContext, MotoGeekDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = MotoGeekRepository(db.motoGeekDao())
    }

    @After
    fun tearDown() {
        db.close()
        isolatedDir.deleteRecursively()
        baseContext.deleteSharedPreferences("${prefPrefix}motogeek_telemetry_prefs")
    }

    private fun sampleBike(
        id: String = "bike-1",
        make: String = "Yamaha",
        model: String = "MT-09",
        baseOdo: Double = 10000.0,
        trackedKm: Double = 500.0,
        manualOffset: Double = 0.0,
        isActive: Boolean = true,
        buyingPrice: Double = 9500.0,
        startDateIso: String = "2026-01-01"
    ): MotorcycleEntity = MotorcycleEntity(
        id = id,
        make = make,
        model = model,
        year = 2024,
        engineDisplacementCc = 890,
        baseOdometerKm = baseOdo,
        accumulatedTrackedKm = trackedKm,
        manualAdjustmentOffsetKm = manualOffset,
        technicalSpecsJson = Json.encodeToString(MotorcycleTemplateCatalog.mt09.technicalSpecs!!),
        isActive = isActive,
        name = "$make $model",
        type = "Naked",
        startDateIso = startDateIso,
        buyingPrice = buyingPrice,
        currencySymbol = "€"
    )

    @Test
    fun testRoomMigrations1To2And2To3() = runBlocking {
        val dbV1Name = "deep-migration-v1-${UUID.randomUUID()}.db"
        val dbV2Name = "deep-migration-v2-${UUID.randomUUID()}.db"
        baseContext.deleteDatabase(dbV1Name)
        baseContext.deleteDatabase(dbV2Name)

        try {
            // 1. Build genuine v1 SQLite schema (without motorcycles.name, service_logs.notes, ride_sessions.notes, app_logs, cloud_sync_state)
            val v1Helper = object : SQLiteOpenHelper(baseContext, dbV1Name, null, 1) {
                override fun onCreate(sqLite: SQLiteDatabase) {
                    sqLite.execSQL("CREATE TABLE IF NOT EXISTS `motorcycles` (`id` TEXT NOT NULL, `make` TEXT NOT NULL, `model` TEXT NOT NULL, `year` INTEGER NOT NULL, `engineDisplacementCc` INTEGER NOT NULL, `baseOdometerKm` REAL NOT NULL, `accumulatedTrackedKm` REAL NOT NULL, `manualAdjustmentOffsetKm` REAL NOT NULL, `technicalSpecsJson` TEXT NOT NULL, `isActive` INTEGER NOT NULL, `type` TEXT NOT NULL, `startDateIso` TEXT NOT NULL, `buyingPrice` REAL NOT NULL, `currencySymbol` TEXT NOT NULL, PRIMARY KEY(`id`))")
                    sqLite.execSQL("CREATE TABLE IF NOT EXISTS `ride_sessions` (`rideId` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `motorcycleId` TEXT NOT NULL, `startTimeEpochMs` INTEGER NOT NULL, `endTimeEpochMs` INTEGER NOT NULL, `distanceKm` REAL NOT NULL, `avgSpeedKmh` REAL NOT NULL, `maxSpeedKmh` REAL NOT NULL, `maxLeanLeftDeg` REAL NOT NULL, `maxLeanRightDeg` REAL NOT NULL, `maxLateralG` REAL NOT NULL, `maxBrakingG` REAL NOT NULL, `maxAccelG` REAL NOT NULL, `totalCornersCount` INTEGER NOT NULL, `leftCornersCount` INTEGER NOT NULL, `rightCornersCount` INTEGER NOT NULL, `maxTurnInRateDegPerSec` REAL NOT NULL, `trailBrakingScorePct` REAL NOT NULL, `avgCornerScore` REAL NOT NULL, `elevationGainMeters` REAL NOT NULL, `ridingStyleBadge` TEXT NOT NULL, `name` TEXT, FOREIGN KEY(`motorcycleId`) REFERENCES `motorcycles`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE)")
                    sqLite.execSQL("CREATE INDEX IF NOT EXISTS `index_ride_sessions_motorcycleId` ON `ride_sessions` (`motorcycleId`)")
                    sqLite.execSQL("CREATE TABLE IF NOT EXISTS `corner_events` (`cornerId` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `rideId` INTEGER NOT NULL, `sequenceNumber` INTEGER NOT NULL, `timestampMs` INTEGER NOT NULL, `direction` TEXT NOT NULL, `entrySpeedKmh` REAL NOT NULL, `apexLeanDeg` REAL NOT NULL, `peakLateralG` REAL NOT NULL, `peakBrakingG` REAL NOT NULL, `exitAccelG` REAL NOT NULL, `maxRollRateDegPerSec` REAL NOT NULL, `cornerScore` REAL NOT NULL, `latitude` REAL NOT NULL, `longitude` REAL NOT NULL, FOREIGN KEY(`rideId`) REFERENCES `ride_sessions`(`rideId`) ON UPDATE NO ACTION ON DELETE CASCADE)")
                    sqLite.execSQL("CREATE INDEX IF NOT EXISTS `index_corner_events_rideId` ON `corner_events` (`rideId`)")
                    sqLite.execSQL("CREATE TABLE IF NOT EXISTS `telemetry_track_points` (`pointId` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `rideId` INTEGER NOT NULL, `timestampMs` INTEGER NOT NULL, `latitude` REAL NOT NULL, `longitude` REAL NOT NULL, `speedKmh` REAL NOT NULL, `leanAngleDeg` REAL NOT NULL, `longitudinalG` REAL NOT NULL, `lateralG` REAL NOT NULL, `altitudeMeters` REAL NOT NULL, FOREIGN KEY(`rideId`) REFERENCES `ride_sessions`(`rideId`) ON UPDATE NO ACTION ON DELETE CASCADE)")
                    sqLite.execSQL("CREATE INDEX IF NOT EXISTS `index_telemetry_track_points_rideId` ON `telemetry_track_points` (`rideId`)")
                    sqLite.execSQL("CREATE TABLE IF NOT EXISTS `maintenance_tasks` (`taskId` TEXT NOT NULL, `motorcycleId` TEXT NOT NULL, `category` TEXT NOT NULL, `taskName` TEXT NOT NULL, `actionType` TEXT NOT NULL, `firstBreakInAtKm` REAL, `intervalKm` REAL, `intervalMonths` INTEGER, `warningThresholdKm` REAL, `warningThresholdDays` INTEGER, `oemPartNumbersJson` TEXT NOT NULL, `notes` TEXT NOT NULL, PRIMARY KEY(`taskId`, `motorcycleId`), FOREIGN KEY(`motorcycleId`) REFERENCES `motorcycles`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE)")
                    sqLite.execSQL("CREATE INDEX IF NOT EXISTS `index_maintenance_tasks_motorcycleId` ON `maintenance_tasks` (`motorcycleId`)")
                    sqLite.execSQL("CREATE TABLE IF NOT EXISTS `service_logs` (`logId` TEXT NOT NULL, `motorcycleId` TEXT NOT NULL, `taskId` TEXT NOT NULL, `serviceTitle` TEXT NOT NULL, `locationOrShop` TEXT NOT NULL, `performedAtIsoDate` TEXT NOT NULL, `odometerAtServiceKm` REAL NOT NULL, `servicedAtKm` REAL NOT NULL, `limitKm` REAL NOT NULL, `endKm` REAL NOT NULL, `costEur` REAL NOT NULL, `performedBy` TEXT NOT NULL, `checklistItemsJson` TEXT NOT NULL, PRIMARY KEY(`logId`), FOREIGN KEY(`motorcycleId`) REFERENCES `motorcycles`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE)")
                    sqLite.execSQL("CREATE INDEX IF NOT EXISTS `index_service_logs_motorcycleId` ON `service_logs` (`motorcycleId`)")
                    sqLite.execSQL("CREATE TABLE IF NOT EXISTS `fuel_logs` (`fuelId` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `motorcycleId` TEXT NOT NULL, `dateIso` TEXT NOT NULL, `odometerKm` REAL NOT NULL, `liters` REAL NOT NULL, `pricePerLiter` REAL NOT NULL, `totalCost` REAL NOT NULL, `isFullTank` INTEGER NOT NULL, `notes` TEXT NOT NULL, FOREIGN KEY(`motorcycleId`) REFERENCES `motorcycles`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE)")
                    sqLite.execSQL("CREATE INDEX IF NOT EXISTS `index_fuel_logs_motorcycleId` ON `fuel_logs` (`motorcycleId`)")
                    sqLite.execSQL("CREATE TABLE IF NOT EXISTS `part_logs` (`partId` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `motorcycleId` TEXT NOT NULL, `dateIso` TEXT NOT NULL, `odometerKm` REAL NOT NULL, `partName` TEXT NOT NULL, `brand` TEXT NOT NULL, `partNumber` TEXT NOT NULL, `cost` REAL NOT NULL, `notes` TEXT NOT NULL, FOREIGN KEY(`motorcycleId`) REFERENCES `motorcycles`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE)")
                    sqLite.execSQL("CREATE INDEX IF NOT EXISTS `index_part_logs_motorcycleId` ON `part_logs` (`motorcycleId`)")
                    sqLite.execSQL("CREATE TABLE IF NOT EXISTS `reminders` (`reminderId` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `motorcycleId` TEXT NOT NULL, `title` TEXT NOT NULL, `targetOdometerKm` REAL, `targetDateIso` TEXT, `isCompleted` INTEGER NOT NULL, FOREIGN KEY(`motorcycleId`) REFERENCES `motorcycles`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE)")
                    sqLite.execSQL("CREATE INDEX IF NOT EXISTS `index_reminders_motorcycleId` ON `reminders` (`motorcycleId`)")
                }

                override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
            }

            v1Helper.writableDatabase.use { sqLite ->
                sqLite.execSQL(
                    "INSERT INTO motorcycles (id, make, model, year, engineDisplacementCc, baseOdometerKm, accumulatedTrackedKm, manualAdjustmentOffsetKm, technicalSpecsJson, isActive, type, startDateIso, buyingPrice, currencySymbol) " +
                        "VALUES ('v1-bike', 'Honda', 'CB650R', 2023, 649, 4200.0, 100.0, 0.0, '{}', 1, 'Naked', '01/03/2023', 8200.0, '€')"
                )
                sqLite.execSQL(
                    "INSERT INTO service_logs (logId, motorcycleId, taskId, serviceTitle, locationOrShop, performedAtIsoDate, odometerAtServiceKm, servicedAtKm, limitKm, endKm, costEur, performedBy, checklistItemsJson) " +
                        "VALUES ('v1-log', 'v1-bike', 'STD_ENGINE_OIL', 'Oil Service', 'Dealer', '2024-05-01', 4200.0, 4200.0, 10000.0, 14200.0, 110.0, 'Dealer', '[]')"
                )
            }
            v1Helper.close()

            val migratedV1ToV3 = Room.databaseBuilder(baseContext, MotoGeekDatabase::class.java, dbV1Name)
                .addMigrations(MotoGeekDatabase.MIGRATION_1_2, MotoGeekDatabase.MIGRATION_2_3)
                .allowMainThreadQueries()
                .build()
            try {
                val migratedBike = migratedV1ToV3.motoGeekDao().getMotorcycleById("v1-bike")
                assertNotNull(migratedBike)
                assertEquals("Honda", migratedBike!!.make)
                assertEquals("", migratedBike.name)

                val migratedLogs = migratedV1ToV3.motoGeekDao().getServiceLogsForMotorcycle("v1-bike")
                assertEquals(1, migratedLogs.size)
                assertEquals("", migratedLogs[0].notes)

                val rawDb = migratedV1ToV3.openHelper.writableDatabase
                rawDb.query("SELECT name FROM sqlite_master WHERE type='table' AND name='app_logs'").use { c ->
                    assertTrue("app_logs table must exist after MIGRATION_1_2", c.moveToFirst())
                }
                rawDb.query("SELECT name FROM sqlite_master WHERE type='index' AND name='index_app_logs_motorcycleId'").use { c ->
                    assertTrue("index_app_logs_motorcycleId must exist after MIGRATION_1_2", c.moveToFirst())
                }
                rawDb.query("SELECT name FROM sqlite_master WHERE type='table' AND name='cloud_sync_state'").use { c ->
                    assertTrue("cloud_sync_state table must exist after MIGRATION_2_3", c.moveToFirst())
                }

                // Verify app_logs read/write works cleanly after migration
                val repoV3 = MotoGeekRepository(migratedV1ToV3.motoGeekDao())
                repoV3.logActivity("v1-bike", "SYSTEM", "Migrated", "v1 to v3 ok")
                assertEquals(1, repoV3.getAppLogsForMotorcycleList("v1-bike").size)
            } finally {
                migratedV1ToV3.close()
            }

            // 2. Test MIGRATION_2_3 standalone from v2 -> v3
            val v2Db = Room.databaseBuilder(baseContext, MotoGeekDatabase::class.java, dbV2Name)
                .allowMainThreadQueries()
                .build()
            v2Db.motoGeekDao().insertMotorcycle(sampleBike(id = "v2-bike"))
            v2Db.openHelper.writableDatabase.execSQL("DROP TABLE cloud_sync_state")
            v2Db.openHelper.writableDatabase.version = 2
            v2Db.close()

            val migratedV2ToV3 = Room.databaseBuilder(baseContext, MotoGeekDatabase::class.java, dbV2Name)
                .addMigrations(MotoGeekDatabase.MIGRATION_2_3)
                .allowMainThreadQueries()
                .build()
            try {
                assertEquals("Yamaha", migratedV2ToV3.motoGeekDao().getMotorcycleById("v2-bike")!!.make)
                migratedV2ToV3.openHelper.writableDatabase.query(
                    "SELECT name FROM sqlite_master WHERE type='table' AND name='cloud_sync_state'"
                ).use { c ->
                    assertTrue("cloud_sync_state must be created by MIGRATION_2_3", c.moveToFirst())
                }
                migratedV2ToV3.openHelper.writableDatabase.execSQL(
                    "INSERT INTO cloud_sync_state (accountEmail, stateJson) VALUES ('rider@example.com', '{}')"
                )
                migratedV2ToV3.openHelper.writableDatabase.query("SELECT COUNT(*) FROM cloud_sync_state").use { c ->
                    c.moveToFirst()
                    assertEquals(1, c.getInt(0))
                }
            } finally {
                migratedV2ToV3.close()
            }
        } finally {
            baseContext.deleteDatabase(dbV1Name)
            baseContext.deleteDatabase(dbV2Name)
        }
    }

    @Test
    fun testGarageCrudOdometerCalibrationMonotonicSyncAndAnalytics() = runBlocking {
        val bike1 = sampleBike(id = "bike-1", make = "Yamaha", model = "MT-09", baseOdo = 10000.0, trackedKm = 500.0, isActive = true)
        val bike2 = sampleBike(id = "bike-2", make = "Ducati", model = "Monster", baseOdo = 4000.0, trackedKm = 0.0, isActive = false, buyingPrice = 11000.0)
        repository.insertMotorcycle(bike1)
        repository.insertMotorcycle(bike2)

        assertEquals(2, repository.getAllMotorcycles().first().size)
        assertEquals("bike-1", repository.getActiveMotorcycle()?.id)

        // Update bike-1 name & verify
        repository.updateMotorcycle(bike1.copy(name = "Track MT-09"))
        assertEquals("Track MT-09", repository.getMotorcycleById("bike-1")?.name)

        // Switch active bike
        repository.selectActiveMotorcycle("bike-2")
        assertEquals("bike-2", repository.getActiveMotorcycle()?.id)
        assertFalse(repository.getMotorcycleById("bike-1")!!.isActive)
        assertTrue(repository.getMotorcycleById("bike-2")!!.isActive)

        // Manual odometer offset calibration on bike-1 (10000 + 500 = 10500 -> calibrate to 12345)
        repository.updateOdometerManualOffset("bike-1", userTargetOdometerKm = 12345.0, baseOdometerKm = 10000.0, accumulatedTrackedKm = 500.0)
        val calibrated = repository.getMotorcycleById("bike-1")!!
        assertEquals(1845.0, calibrated.manualAdjustmentOffsetKm, 0.01)
        assertEquals(12345.0, calibrated.totalOdometerKm, 0.01)

        // Tracked ride distance adds onto total odometer
        repository.addAccumulatedTrackedKm("bike-1", 155.0)
        assertEquals(12500.0, repository.getMotorcycleById("bike-1")!!.totalOdometerKm, 0.01)

        // Monotonic odometer sync: higher fuel log advances odometer; lower historical log does NOT regress it
        suspend fun applyMonotonicSync(motorcycleId: String, loggedOdoKm: Double) {
            val current = repository.getMotorcycleById(motorcycleId) ?: return
            if (loggedOdoKm.isFinite() && loggedOdoKm > current.totalOdometerKm) {
                repository.updateOdometerManualOffset(
                    current.id,
                    loggedOdoKm,
                    current.baseOdometerKm,
                    current.accumulatedTrackedKm
                )
            }
        }

        val fuel1 = FuelLogEntity(motorcycleId = "bike-1", dateIso = "2026-04-01", odometerKm = 12000.0, liters = 14.0, pricePerLiter = 1.8, totalCost = 25.2, isFullTank = true, notes = "")
        repository.insertFuelLog(fuel1)
        applyMonotonicSync("bike-1", fuel1.odometerKm)
        assertEquals(12500.0, repository.getMotorcycleById("bike-1")!!.totalOdometerKm, 0.01)

        val fuel2 = FuelLogEntity(motorcycleId = "bike-1", dateIso = "2026-04-10", odometerKm = 12300.0, liters = 15.0, pricePerLiter = 1.8, totalCost = 27.0, isFullTank = true, notes = "")
        val fuel3 = FuelLogEntity(motorcycleId = "bike-1", dateIso = "2026-04-20", odometerKm = 12800.0, liters = 25.0, pricePerLiter = 1.8, totalCost = 45.0, isFullTank = true, notes = "")
        repository.insertFuelLog(fuel2)
        repository.insertFuelLog(fuel3)
        applyMonotonicSync("bike-1", fuel3.odometerKm)
        val afterMonotonic = repository.getMotorcycleById("bike-1")!!
        assertEquals(12800.0, afterMonotonic.totalOdometerKm, 0.01)

        // Fuel economy stats (12000 -> 12300: 15L/300km = 5.0; 12300 -> 12800: 25L/500km = 5.0)
        val fuelLogs = repository.getFuelLogsForMotorcycleList("bike-1")
        val fuelStats = RiderAnalyticsCalculator.computeFuelEconomyStats(fuelLogs)
        assertEquals(5.0, fuelStats.avgLitersPer100Km, 0.05)
        assertEquals(20.0, fuelStats.avgKmPerLiter, 0.1)
        assertEquals(800.0, fuelStats.trackedSpanKm, 0.01)
        assertEquals(3, fuelStats.fillCount)

        // TCO stats
        val serviceLog = ServiceLogEntity(motorcycleId = "bike-1", serviceTitle = "Annual", performedAtIsoDate = "2026-03-01", odometerAtServiceKm = 11000.0, costEur = 180.0)
        val partLog = PartLogEntity(motorcycleId = "bike-1", dateIso = "2026-03-15", odometerKm = 11500.0, partName = "Rear Tire", brand = "Pirelli", partNumber = "DR4", cost = 220.0, notes = "")
        repository.insertServiceLog(serviceLog)
        repository.insertPartLog(partLog)

        val nowMs = 1777593600000L // 2026-05-01T00:00:00Z
        val tco = RiderAnalyticsCalculator.computeTcoStats(
            bike = afterMonotonic,
            fuelLogs = fuelLogs,
            serviceLogs = repository.getServiceLogsForMotorcycle("bike-1"),
            partLogs = repository.getPartLogsForMotorcycleList("bike-1"),
            rides = emptyList(),
            nowMs = nowMs
        )
        val expectedFuelCost = 25.2 + 27.0 + 45.0
        val expectedOperating = expectedFuelCost + 180.0 + 220.0
        assertEquals(9500.0, tco.purchasePrice, 0.01)
        assertEquals(expectedOperating, tco.totalOperatingCost, 0.01)
        assertEquals(9500.0 + expectedOperating, tco.totalCost, 0.01)
        assertEquals(2800.0, tco.ownedDistanceKm, 0.01)
        assertEquals(expectedOperating / 2800.0, tco.costPerKm, 0.001)

        // Delete bike-1 cascades child rows
        repository.deleteMotorcycle("bike-1")
        assertNull(repository.getMotorcycleById("bike-1"))
        assertTrue(repository.getFuelLogsForMotorcycleList("bike-1").isEmpty())
        assertTrue(repository.getServiceLogsForMotorcycle("bike-1").isEmpty())
        assertTrue(repository.getPartLogsForMotorcycleList("bike-1").isEmpty())
    }

    @Test
    fun testMaintenanceEvaluatorChecklistCatalogAndTaskDeduplication() = runBlocking {
        val bike = sampleBike(id = "maint-bike", baseOdo = 12000.0, trackedKm = 0.0, startDateIso = "2024-01-01")
        repository.insertMotorcycle(bike)

        val duplicateTasks = listOf(
            MaintenanceTaskEntity(
                taskId = "STD_ENGINE_OIL",
                motorcycleId = bike.id,
                category = "Engine",
                taskName = "Engine Oil & Filter",
                actionType = "Replace",
                firstBreakInAtKm = 1000.0,
                intervalKm = 10000.0,
                intervalMonths = 12,
                warningThresholdKm = 500.0,
                warningThresholdDays = 30,
                oemPartNumbersJson = "[]",
                notes = "Canonical oil"
            ),
            MaintenanceTaskEntity(
                taskId = "CUSTOM_OIL_DUPE",
                motorcycleId = bike.id,
                category = "Lubrication",
                taskName = "Motorový olej a filter",
                actionType = "Replace",
                firstBreakInAtKm = null,
                intervalKm = 10000.0,
                intervalMonths = 12,
                warningThresholdKm = 500.0,
                warningThresholdDays = 30,
                oemPartNumbersJson = "[]",
                notes = "Duplicate oil"
            ),
            MaintenanceTaskEntity(
                taskId = "STD_CHAIN_LUBE",
                motorcycleId = bike.id,
                category = "Drivetrain",
                taskName = "Chain Clean, Lube & Slack",
                actionType = "Clean/Lube",
                firstBreakInAtKm = null,
                intervalKm = 1000.0,
                intervalMonths = null,
                warningThresholdKm = 200.0,
                warningThresholdDays = null,
                oemPartNumbersJson = "[]",
                notes = ""
            )
        )
        repository.insertMaintenanceTasks(duplicateTasks)

        // Service log initially points to the duplicate task ID
        val initialLog = ServiceLogEntity(
            logId = "log-oil-1",
            motorcycleId = bike.id,
            taskId = "CUSTOM_OIL_DUPE",
            serviceTitle = "Oil Change",
            performedAtIsoDate = "2025-11-01",
            odometerAtServiceKm = 11500.0
        )
        repository.insertServiceLog(initialLog)

        val removedCount = repository.deduplicateMaintenanceTasksForBike(bike.id)
        assertEquals(1, removedCount)

        val remainingTasks = repository.getMaintenanceTasksForMotorcycle(bike.id)
        assertEquals(2, remainingTasks.size)
        assertTrue(remainingTasks.any { it.taskId == "STD_ENGINE_OIL" })
        assertTrue(remainingTasks.none { it.taskId == "CUSTOM_OIL_DUPE" })

        val remappedLogs = repository.getServiceLogsForMotorcycle(bike.id)
        assertEquals("STD_ENGINE_OIL", remappedLogs.single().taskId)

        // ServiceChecklistCatalog sub-options & completion evaluation
        val oilSubOptions = ServiceChecklistCatalog.getSubOptionsFor("STD_ENGINE_OIL", "Engine Oil & Filter", "Engine")
        assertFalse(ServiceChecklistCatalog.evaluateCompletesTask("Replace", setOf("OIL_CHECKED"), oilSubOptions))
        assertTrue(ServiceChecklistCatalog.evaluateCompletesTask("Replace", setOf("OIL_CHANGED"), oilSubOptions))
        val defaultKeys = ServiceChecklistCatalog.defaultCompletionKeysForTask("Replace", oilSubOptions)
        assertTrue("OIL_CHANGED" in defaultKeys)

        val oilTask = remainingTasks.first { it.taskId == "STD_ENGINE_OIL" }
        val completedChecklistItem = ServiceChecklistItem(
            name = "Engine Oil",
            status = "DONE",
            taskId = "STD_ENGINE_OIL",
            subActions = listOf("OIL_CHANGED"),
            completesTask = true
        )
        assertTrue(ServiceChecklistCatalog.itemMatchesAndCompletesTask(completedChecklistItem, oilTask))

        // Remaining general checks excludes Engine Oil & Chain tasks
        val remainingGeneral = ServiceChecklistCatalog.remainingGeneralChecks(remainingTasks)
        assertTrue(remainingGeneral.none { it.key == "GEN_ENGINE_OIL" || it.key == "GEN_CHAIN" })
        assertTrue(remainingGeneral.any { it.key == "GEN_BRAKE_FLUID" })

        // MaintenanceEvaluator: oil serviced at 11500 -> next due 21500 -> OK; chain never serviced at 12000 -> OVERDUE
        val evalDate = LocalDate.of(2026, 3, 1)
        val statuses = MaintenanceEvaluator().evaluate(bike, remainingTasks, remappedLogs, evalDate)
        val oilStatus = statuses.first { it.task.taskId == "STD_ENGINE_OIL" }
        val chainStatus = statuses.first { it.task.taskId == "STD_CHAIN_LUBE" }
        assertEquals(TaskStatus.OK, oilStatus.status)
        assertEquals(TaskStatus.OVERDUE, chainStatus.status)

        // Move bike odometer to 21200 (300 km before 21500 due, within 500 km warning) -> DUE_SOON
        val bikeDueSoon = bike.copy(baseOdometerKm = 21200.0)
        val dueSoonStatuses = MaintenanceEvaluator().evaluate(bikeDueSoon, listOf(oilTask), remappedLogs, evalDate)
        assertEquals(TaskStatus.DUE_SOON, dueSoonStatuses.single().status)
    }

    @Test
    fun testSensorFusionCornerDetectionAutoStartStopAndGpxRoundTrip() {
        val detectedCorners = mutableListOf<CornerEventEntity>()
        val engine = MotorcycleSensorFusionEngine { corner -> detectedCorners.add(corner) }

        // 1. Auto-start > 20 km/h boundary checks
        assertFalse(engine.evaluateAutoStart(isRiding = false, autoStartEnabled = true, speedKmh = 20.0))
        assertTrue(engine.evaluateAutoStart(isRiding = false, autoStartEnabled = true, speedKmh = 20.1))
        assertFalse(engine.evaluateAutoStart(isRiding = true, autoStartEnabled = true, speedKmh = 45.0))
        assertFalse(engine.evaluateAutoStart(isRiding = false, autoStartEnabled = false, speedKmh = 45.0))

        // 2. 100Hz IMU calibration + GPS corner detection
        var tsNanos = 1_000_000_000L
        var wallMs = 1_700_000_000_000L
        for (i in 0..160) {
            engine.processImu(tsNanos, 0.0, 0.0, 9.80665, 0.0, 0.0, 0.0, wallClockMs = wallMs)
            tsNanos += 10_000_000L
            wallMs += 10L
        }
        assertTrue(engine.calibrator.isCalibrated)

        // Cruising at 72 km/h (20 m/s)
        val trackPoints = mutableListOf<TelemetryTrackPointEntity>()
        engine.processGps(wallMs, 20.0, true, 0.0, true, 48.1486, 17.1077)
        trackPoints.add(
            TelemetryTrackPointEntity(
                rideId = 1L,
                timestampMs = wallMs,
                latitude = 48.1486,
                longitude = 17.1077,
                speedKmh = engine.currentSpeedKmh,
                leanAngleDeg = engine.currentLeanAngleDeg,
                longitudinalG = engine.currentLongitudinalG,
                lateralG = engine.currentLateralG,
                altitudeMeters = 250.0
            )
        )

        // Sustained right-hand corner (2.0s at 100Hz)
        for (i in 0..200) {
            val rollRateRad = if (i < 35) 1.0 else 0.0
            engine.processImu(tsNanos, 0.0, 0.0, 9.80665, 0.0, rollRateRad, -0.35, wallClockMs = wallMs)
            if (i % 50 == 0) {
                val lat = 48.1486 + (i / 50 + 1) * 0.0005
                val lon = 17.1077 + (i / 50 + 1) * 0.0005
                engine.processGps(wallMs, 20.0, true, 30.0, true, lat, lon)
                trackPoints.add(
                    TelemetryTrackPointEntity(
                        rideId = 1L,
                        timestampMs = wallMs,
                        latitude = lat,
                        longitude = lon,
                        speedKmh = engine.currentSpeedKmh,
                        leanAngleDeg = engine.currentLeanAngleDeg,
                        longitudinalG = engine.currentLongitudinalG,
                        lateralG = engine.currentLateralG,
                        altitudeMeters = 252.0 + i * 0.02
                    )
                )
            }
            tsNanos += 10_000_000L
            wallMs += 10L
        }

        // Straighten out -> emits corner
        for (i in 0..150) {
            val rollRateRad = if (i < 35) -1.0 else 0.0
            engine.processImu(tsNanos, 0.0, 0.0, 9.80665, 0.0, rollRateRad, 0.0, wallClockMs = wallMs)
            tsNanos += 10_000_000L
            wallMs += 10L
        }
        engine.processGps(wallMs, 20.0, true, 45.0, true, 48.1510, 17.1100)
        trackPoints.add(
            TelemetryTrackPointEntity(
                rideId = 1L,
                timestampMs = wallMs,
                latitude = 48.1510,
                longitude = 17.1100,
                speedKmh = engine.currentSpeedKmh,
                leanAngleDeg = engine.currentLeanAngleDeg,
                longitudinalG = engine.currentLongitudinalG,
                lateralG = engine.currentLateralG,
                altitudeMeters = 260.0
            )
        )

        assertEquals(1, engine.totalCorners)
        assertEquals(1, engine.rightCorners)
        assertEquals(1, detectedCorners.size)
        assertEquals("RIGHT", detectedCorners[0].direction)
        assertTrue(detectedCorners[0].apexLeanDeg >= 12.0)

        // 3. Auto-stop 5 min <= 5 km/h & timer reset on 5.5 km/h
        val stopStartMs = wallMs + 1_000L
        engine.processGps(stopStartMs, 4.0 / 3.6, true, 45.0, true, 48.1510, 17.1100)
        assertFalse(engine.isAutoStopTriggered)
        engine.processGps(stopStartMs + 299_999L, 5.0 / 3.6, true, 45.0, true, 48.1510, 17.1100)
        assertFalse(engine.isAutoStopTriggered)

        // Speed rises to 5.5 km/h -> resets timer
        engine.processGps(stopStartMs + 300_000L - 10_000L, 5.5 / 3.6, true, 45.0, true, 48.1510, 17.1100)
        assertEquals(0L, engine.lowSpeedStartTimeMs)
        assertFalse(engine.isAutoStopTriggered)

        val secondStopMs = stopStartMs + 300_000L
        assertFalse(engine.checkAutoStopTimeout(secondStopMs, 3.0))
        assertFalse(engine.checkAutoStopTimeout(secondStopMs + 299_999L, 5.0))
        assertTrue(engine.checkAutoStopTimeout(secondStopMs + 300_000L, 5.0))
        assertTrue(engine.isAutoStopTriggered)

        // 4. GPX round-trip with detected corner and track points
        val ride = RideSessionEntity(
            rideId = 1L,
            motorcycleId = "bike-1",
            startTimeEpochMs = trackPoints.first().timestampMs,
            endTimeEpochMs = trackPoints.last().timestampMs,
            distanceKm = 0.5,
            avgSpeedKmh = 72.0,
            maxSpeedKmh = 72.0,
            maxLeanLeftDeg = 0.0,
            maxLeanRightDeg = detectedCorners[0].apexLeanDeg,
            maxLateralG = detectedCorners[0].peakLateralG,
            maxBrakingG = 0.0,
            maxAccelG = 0.0,
            totalCornersCount = detectedCorners.size,
            leftCornersCount = 0,
            rightCornersCount = 1,
            maxTurnInRateDegPerSec = detectedCorners[0].maxRollRateDegPerSec,
            trailBrakingScorePct = 0.0,
            avgCornerScore = detectedCorners[0].cornerScore,
            elevationGainMeters = 10.0,
            ridingStyleBadge = "TECHNICIAN",
            name = "Alpine Pass"
        )
        val gpxXml = GpxTelemetryCodec.encodeRideToGpx(ride, trackPoints, detectedCorners, "Yamaha MT-09")
        val parsed = GpxTelemetryCodec.parseGpxToRide(gpxXml, "bike-1")

        assertTrue(parsed.hasEmbeddedImuTelemetry)
        assertEquals("Alpine Pass", parsed.ride.name)
        assertEquals(trackPoints.size, parsed.trackPoints.size)
        assertEquals(trackPoints.first().latitude, parsed.trackPoints.first().latitude, 0.0001)
        assertEquals(trackPoints.first().speedKmh, parsed.trackPoints.first().speedKmh, 0.05)
        assertEquals(1, parsed.cornerEvents.size)
        assertEquals("RIGHT", parsed.cornerEvents[0].direction)
        assertEquals(detectedCorners[0].apexLeanDeg, parsed.cornerEvents[0].apexLeanDeg, 0.2)
    }

    @Test
    fun testImportExportLosslessBackupV2MergeAndSafetySnapshotRollback() = runBlocking {
        val manager = ImportExportManager(isolatedContext, repository)

        // 1. Populate complete garage data for lossless backup v2
        val bike = sampleBike(id = "orig-bike", make = "Yamaha", model = "MT-09", baseOdo = 15000.0, trackedKm = 420.0, isActive = true)
        repository.insertMotorcycle(bike)
        val task = MaintenanceTaskEntity(
            taskId = "STD_ENGINE_OIL",
            motorcycleId = bike.id,
            category = "Engine",
            taskName = "Engine Oil & Filter",
            actionType = "Replace",
            firstBreakInAtKm = 1000.0,
            intervalKm = 10000.0,
            intervalMonths = 12,
            warningThresholdKm = 500.0,
            warningThresholdDays = 30,
            oemPartNumbersJson = """["5GH-13440-80"]""",
            notes = "Drain bolt 43 Nm"
        )
        repository.insertMaintenanceTask(task)
        val checklistJson = Json.encodeToString(
            listOf(ServiceChecklistItem("Engine Oil", "DONE", taskId = "STD_ENGINE_OIL", subActions = listOf("OIL_CHANGED"), completesTask = true))
        )
        val serviceLog = ServiceLogEntity(
            logId = "svc-orig-1",
            motorcycleId = bike.id,
            taskId = "STD_ENGINE_OIL",
            serviceTitle = "15k Service",
            performedAtIsoDate = "2026-04-01",
            odometerAtServiceKm = 15000.0,
            costEur = 140.0,
            notes = "Motul 7100",
            checklistItemsJson = checklistJson
        )
        repository.insertServiceLog(serviceLog)
        repository.insertFuelLog(FuelLogEntity(motorcycleId = bike.id, dateIso = "2026-04-02", odometerKm = 15100.0, liters = 12.5, pricePerLiter = 1.8, totalCost = 22.5, isFullTank = true, notes = "Shell"))
        repository.insertPartLog(PartLogEntity(motorcycleId = bike.id, dateIso = "2026-04-03", odometerKm = 15150.0, partName = "Brake Pads", brand = "Brembo", partNumber = "07YA23", cost = 75.0, notes = "Front"))
        repository.insertReminder(ReminderEntity(motorcycleId = bike.id, title = "Chain Check", targetOdometerKm = 16000.0, targetDateIso = "2026-06-01"))
        val rideId = repository.insertRideSession(
            RideSessionEntity(
                motorcycleId = bike.id,
                startTimeEpochMs = 1_775_000_000_000L,
                endTimeEpochMs = 1_775_003_600_000L,
                distanceKm = 85.0,
                avgSpeedKmh = 85.0,
                maxSpeedKmh = 142.0,
                maxLeanLeftDeg = 38.0,
                maxLeanRightDeg = 41.0,
                maxLateralG = 0.85,
                maxBrakingG = -0.7,
                maxAccelG = 0.6,
                totalCornersCount = 1,
                leftCornersCount = 0,
                rightCornersCount = 1,
                maxTurnInRateDegPerSec = 55.0,
                trailBrakingScorePct = 100.0,
                avgCornerScore = 82.0,
                elevationGainMeters = 320.0,
                ridingStyleBadge = "TECHNICIAN",
                name = "Pezinska Baba",
                notes = "Clean tarmac"
            )
        )
        repository.insertCornerEvents(
            listOf(
                CornerEventEntity(
                    rideId = rideId,
                    sequenceNumber = 1,
                    timestampMs = 1_775_000_500_000L,
                    direction = "RIGHT",
                    entrySpeedKmh = 78.0,
                    apexLeanDeg = 41.0,
                    peakLateralG = 0.85,
                    peakBrakingG = -0.35,
                    exitAccelG = 0.45,
                    maxRollRateDegPerSec = 55.0,
                    cornerScore = 82.0,
                    latitude = 48.35,
                    longitude = 17.18
                )
            )
        )
        repository.insertTelemetryTrackPoints(
            listOf(
                TelemetryTrackPointEntity(
                    rideId = rideId,
                    timestampMs = 1_775_000_500_000L,
                    latitude = 48.35,
                    longitude = 17.18,
                    speedKmh = 78.0,
                    leanAngleDeg = 41.0,
                    longitudinalG = -0.35,
                    lateralG = 0.85,
                    altitudeMeters = 410.0
                )
            )
        )
        repository.logActivity(bike.id, "SYSTEM", "Baseline Ready", "Full v2 export test")

        val backupV2Json = manager.generateBackupJsonString()
        val parsedPayload = Json { ignoreUnknownKeys = true }.decodeFromString<GarageBackupPayload>(backupV2Json)
        assertEquals(2, parsedPayload.schemaVersion)

        // Wipe and restore via REPLACE -> verify lossless restoration
        repository.deleteAllMotorcycles()
        assertTrue(repository.getAllMotorcycles().first().isEmpty())

        val restoreV2Result = manager.importGarageBackupFromString(backupV2Json, RestoreMode.REPLACE)
        assertTrue(restoreV2Result.isSuccess)
        val restoredBike = repository.getMotorcycleById("orig-bike")!!
        assertEquals(420.0, restoredBike.accumulatedTrackedKm, 0.01)
        assertEquals(1, repository.getMaintenanceTasksForMotorcycle("orig-bike").size)
        assertEquals("Motul 7100", repository.getServiceLogsForMotorcycle("orig-bike").single().notes)
        assertEquals(1, repository.getServiceLogsForMotorcycle("orig-bike").single().parseChecklist().size)
        assertEquals(1, repository.getFuelLogsForMotorcycleList("orig-bike").size)
        assertEquals(1, repository.getPartLogsForMotorcycleList("orig-bike").size)
        assertEquals(1, repository.getRemindersForMotorcycleList("orig-bike").size)
        val restoredRide = repository.getRideSessionsForMotorcycleList("orig-bike").single()
        assertEquals("Pezinska Baba", restoredRide.name)
        assertEquals(1, repository.getCornerEventsListForRide(restoredRide.rideId).size)
        assertEquals(1, repository.getTelemetryTrackPointsListForRide(restoredRide.rideId).size)
        assertEquals(1, repository.getAppLogsForMotorcycleList("orig-bike").size)

        // 2. Format A MERGE with targetMotorcycleId
        // Build a single-bike backup from a different source bike ID with new fuel & service log
        repository.deleteAllMotorcycles()
        repository.insertMotorcycle(sampleBike(id = "external-src", make = "Yamaha", model = "MT-09", baseOdo = 15000.0, trackedKm = 10.0))
        repository.insertFuelLog(FuelLogEntity(motorcycleId = "external-src", dateIso = "2026-04-15", odometerKm = 15450.0, liters = 11.0, pricePerLiter = 1.85, totalCost = 20.35, isFullTank = true, notes = "OMV"))
        repository.insertServiceLog(ServiceLogEntity(logId = "svc-ext-2", motorcycleId = "external-src", taskId = "STD_BRAKE_FLUID", serviceTitle = "Brake Flush", performedAtIsoDate = "2026-04-16", odometerAtServiceKm = 15460.0))
        val singleBikeMergeJson = manager.generateBackupJsonString()

        // Restore orig-bike as the local target garage
        repository.deleteAllMotorcycles()
        manager.importGarageBackupFromString(backupV2Json, RestoreMode.MERGE)
        assertEquals(420.0, repository.getMotorcycleById("orig-bike")!!.accumulatedTrackedKm, 0.01)

        val mergeResult = manager.importFromJsonString(
            singleBikeMergeJson,
            targetMotorcycleId = "orig-bike",
            restoreMode = RestoreMode.MERGE
        )
        assertTrue(mergeResult.isSuccess)
        assertNull("Source bike ID must not be inserted separately when merged via targetMotorcycleId", repository.getMotorcycleById("external-src"))
        assertEquals(420.0, repository.getMotorcycleById("orig-bike")!!.accumulatedTrackedKm, 0.01)
        assertEquals(2, repository.getFuelLogsForMotorcycleList("orig-bike").size)
        assertEquals(2, repository.getServiceLogsForMotorcycle("orig-bike").size)

        // Idempotent second merge must not duplicate logs
        val secondMergeResult = manager.importFromJsonString(
            singleBikeMergeJson,
            targetMotorcycleId = "orig-bike",
            restoreMode = RestoreMode.MERGE
        )
        assertTrue(secondMergeResult.isSuccess)
        assertEquals(2, repository.getFuelLogsForMotorcycleList("orig-bike").size)
        assertEquals(2, repository.getServiceLogsForMotorcycle("orig-bike").size)

        // 3. Pre-restore safety snapshot rollback
        val otherGarageBackup = """
            {
              "schemaVersion": 2,
              "exportDateIso": "2026-05-01T00:00:00Z",
              "motorcycles": [
                {
                  "motorcycle": {
                    "id": "replacement-bike",
                    "make": "BMW",
                    "model": "R1250GS",
                    "year": 2024,
                    "engineDisplacementCc": 1254,
                    "baseOdometerKm": 5000.0,
                    "accumulatedTrackedKm": 0.0,
                    "manualAdjustmentOffsetKm": 0.0,
                    "isActive": true,
                    "name": "GS Adventure"
                  }
                }
              ]
            }
        """.trimIndent()

        val replaceResult = manager.importFromJsonString(otherGarageBackup, targetMotorcycleId = null, restoreMode = RestoreMode.REPLACE)
        assertTrue(replaceResult.isSuccess)
        assertTrue(manager.hasPreRestoreSnapshot())
        assertNull(repository.getMotorcycleById("orig-bike"))
        assertNotNull(repository.getMotorcycleById("replacement-bike"))

        val rollbackResult = manager.restoreLastSafetySnapshot()
        assertTrue(rollbackResult.isSuccess)
        assertNull(repository.getMotorcycleById("replacement-bike"))
        assertNotNull(repository.getMotorcycleById("orig-bike"))
        assertEquals(2, repository.getFuelLogsForMotorcycleList("orig-bike").size)
        assertEquals(2, repository.getServiceLogsForMotorcycle("orig-bike").size)
    }
}

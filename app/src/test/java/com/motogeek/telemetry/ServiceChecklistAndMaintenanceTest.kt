package com.motogeek.telemetry

import com.motogeek.telemetry.data.local.MaintenanceTaskEntity
import com.motogeek.telemetry.data.local.MotorcycleEntity
import com.motogeek.telemetry.data.local.ServiceChecklistItem
import com.motogeek.telemetry.data.local.ServiceLogEntity
import com.motogeek.telemetry.domain.maintenance.MaintenanceEvaluator
import com.motogeek.telemetry.domain.maintenance.MotorcycleTemplateCatalog
import com.motogeek.telemetry.domain.maintenance.ServiceChecklistCatalog
import com.motogeek.telemetry.domain.maintenance.TaskStatus
import com.motogeek.telemetry.domain.maintenance.TechnicalSpecsDto
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class ServiceChecklistAndMaintenanceTest {

    private val evaluator = MaintenanceEvaluator()

    private fun createBaseBike(odometerKm: Double): MotorcycleEntity = MotorcycleEntity(
        id = "bike_test_1",
        make = "Yamaha",
        model = "MT-09",
        year = 2024,
        engineDisplacementCc = 890,
        baseOdometerKm = odometerKm,
        accumulatedTrackedKm = 0.0,
        manualAdjustmentOffsetKm = 0.0,
        technicalSpecsJson = "{}",
        isActive = true
    )

    private fun createTask(
        taskId: String,
        motorcycleId: String = "bike_test_1",
        category: String,
        taskName: String,
        actionType: String,
        firstBreakInAtKm: Double? = null,
        intervalKm: Double? = null,
        intervalMonths: Int? = null,
        warningThresholdKm: Double? = null,
        warningThresholdDays: Int? = null,
        oemPartNumbersJson: String = "[]",
        notes: String = ""
    ): MaintenanceTaskEntity = MaintenanceTaskEntity(
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

    @Test
    fun testOilCheckedVsOilChangedClearsOverdue() {
        val bike = createBaseBike(12000.0)
        val taskOil = createTask(
            taskId = "STD_ENGINE_OIL",
            motorcycleId = bike.id,
            category = "Engine",
            taskName = "Engine Oil & Filter",
            actionType = "Replace",
            intervalKm = 10000.0,
            warningThresholdKm = 500.0
        )

        // Without logs, current 12,000 km exceeds 10,000 km interval -> OVERDUE
        val initialStatus = evaluator.evaluate(bike, listOf(taskOil), emptyList(), LocalDate.of(2026, 6, 1))
        assertEquals(TaskStatus.OVERDUE, initialStatus[0].status)

        // SubOption evaluation check: OIL_CHECKED does not complete replacement task
        val oilOptions = ServiceChecklistCatalog.getSubOptionsFor("STD_ENGINE_OIL", "Engine Oil & Filter", "Engine")
        val completesWithChecked = ServiceChecklistCatalog.evaluateCompletesTask("Replace", setOf("OIL_CHECKED"), oilOptions)
        assertFalse(completesWithChecked)

        // Case A: OIL_CHECKED with completesTask = false -> remains OVERDUE
        val logChecked = ServiceLogEntity(
            motorcycleId = bike.id,
            taskId = "GENERAL_SERVICE",
            serviceTitle = "Routine Check",
            performedAtIsoDate = "2026-06-01",
            odometerAtServiceKm = 12000.0,
            checklistItemsJson = Json.encodeToString(
                listOf(
                    ServiceChecklistItem(
                        name = "Engine Oil",
                        status = "CHECKED",
                        taskId = "STD_ENGINE_OIL",
                        subActions = listOf("OIL_CHECKED"),
                        note = "Level within sight glass",
                        completesTask = false
                    )
                )
            )
        )
        val statusAfterCheck = evaluator.evaluate(bike, listOf(taskOil), listOf(logChecked), LocalDate.of(2026, 6, 1))
        assertEquals(TaskStatus.OVERDUE, statusAfterCheck[0].status)

        // SubOption evaluation check: OIL_CHANGED completes replacement task
        val completesWithChanged = ServiceChecklistCatalog.evaluateCompletesTask("Replace", setOf("OIL_CHANGED"), oilOptions)
        assertTrue(completesWithChanged)

        // Case B: OIL_CHANGED with completesTask = true -> clears OVERDUE to OK
        val logChanged = ServiceLogEntity(
            motorcycleId = bike.id,
            taskId = "GENERAL_SERVICE",
            serviceTitle = "Oil Service",
            performedAtIsoDate = "2026-06-01",
            odometerAtServiceKm = 12000.0,
            checklistItemsJson = Json.encodeToString(
                listOf(
                    ServiceChecklistItem(
                        name = "Engine Oil & Filter",
                        status = "DONE",
                        taskId = "STD_ENGINE_OIL",
                        subActions = listOf("OIL_CHANGED", "OIL_FILTER_CHANGED"),
                        note = "Motul 7100 10W-40 + OEM filter",
                        completesTask = true
                    )
                )
            )
        )
        val statusAfterChange = evaluator.evaluate(bike, listOf(taskOil), listOf(logChanged), LocalDate.of(2026, 6, 1))
        assertEquals(TaskStatus.OK, statusAfterChange[0].status)
        assertEquals(10000.0, statusAfterChange[0].remainingKm!!, 0.01)
        assertEquals(22000.0, statusAfterChange[0].nextDueOdometerKm!!, 0.01)
    }

    @Test
    fun testCoolantCheckedVsCoolantChangedClearsOverdue() {
        val bike = createBaseBike(32000.0)
        val taskCoolant = createTask(
            taskId = "STD_COOLANT",
            motorcycleId = bike.id,
            category = "Cooling",
            taskName = "Coolant Replacement",
            actionType = "Replace",
            intervalKm = 30000.0,
            warningThresholdKm = 1000.0
        )

        val coolantOptions = ServiceChecklistCatalog.getSubOptionsFor("STD_COOLANT", "Coolant Replacement", "Cooling")
        assertFalse(ServiceChecklistCatalog.evaluateCompletesTask("Replace", setOf("COOLANT_CHECKED"), coolantOptions))
        assertTrue(ServiceChecklistCatalog.evaluateCompletesTask("Replace", setOf("COOLANT_CHANGED"), coolantOptions))

        val logChecked = ServiceLogEntity(
            motorcycleId = bike.id,
            taskId = "GENERAL_SERVICE",
            serviceTitle = "Coolant Level Inspection",
            performedAtIsoDate = "2026-06-01",
            odometerAtServiceKm = 32000.0,
            checklistItemsJson = Json.encodeToString(
                listOf(
                    ServiceChecklistItem(
                        name = "Coolant",
                        status = "CHECKED",
                        taskId = "STD_COOLANT",
                        subActions = listOf("COOLANT_CHECKED"),
                        completesTask = false
                    )
                )
            )
        )
        val statusChecked = evaluator.evaluate(bike, listOf(taskCoolant), listOf(logChecked), LocalDate.of(2026, 6, 1))
        assertEquals(TaskStatus.OVERDUE, statusChecked[0].status)

        val logChanged = ServiceLogEntity(
            motorcycleId = bike.id,
            taskId = "GENERAL_SERVICE",
            serviceTitle = "Coolant Flush",
            performedAtIsoDate = "2026-06-01",
            odometerAtServiceKm = 32000.0,
            checklistItemsJson = Json.encodeToString(
                listOf(
                    ServiceChecklistItem(
                        name = "Coolant Replacement",
                        status = "DONE",
                        taskId = "STD_COOLANT",
                        subActions = listOf("COOLANT_CHANGED", "COOLANT_BLED"),
                        completesTask = true
                    )
                )
            )
        )
        val statusChanged = evaluator.evaluate(bike, listOf(taskCoolant), listOf(logChanged), LocalDate.of(2026, 6, 1))
        assertEquals(TaskStatus.OK, statusChanged[0].status)
        assertEquals(30000.0, statusChanged[0].remainingKm!!, 0.01)
    }

    @Test
    fun testChainCheckedVsChainCleanedLubedClearsOverdue() {
        val bike = createBaseBike(900.0)
        val taskChain = createTask(
            taskId = "STD_CHAIN_LUBE",
            motorcycleId = bike.id,
            category = "Drivetrain",
            taskName = "Chain Clean, Lube & Slack",
            actionType = "Clean/Lube",
            intervalKm = 800.0,
            warningThresholdKm = 150.0
        )

        val chainOptions = ServiceChecklistCatalog.getSubOptionsFor("STD_CHAIN_LUBE", "Chain Clean, Lube & Slack", "Drivetrain")
        assertFalse(ServiceChecklistCatalog.evaluateCompletesTask("Clean/Lube", setOf("CHAIN_CHECKED"), chainOptions))
        assertTrue(ServiceChecklistCatalog.evaluateCompletesTask("Clean/Lube", setOf("CHAIN_CLEANED_LUBED"), chainOptions))

        val logChecked = ServiceLogEntity(
            motorcycleId = bike.id,
            taskId = "GENERAL_SERVICE",
            serviceTitle = "Chain Slack Check",
            performedAtIsoDate = "2026-06-01",
            odometerAtServiceKm = 900.0,
            checklistItemsJson = Json.encodeToString(
                listOf(
                    ServiceChecklistItem(
                        name = "Drive Chain",
                        status = "CHECKED",
                        taskId = "STD_CHAIN_LUBE",
                        subActions = listOf("CHAIN_CHECKED"),
                        completesTask = false
                    )
                )
            )
        )
        val statusChecked = evaluator.evaluate(bike, listOf(taskChain), listOf(logChecked), LocalDate.of(2026, 6, 1))
        assertEquals(TaskStatus.OVERDUE, statusChecked[0].status)

        val logLubed = ServiceLogEntity(
            motorcycleId = bike.id,
            taskId = "GENERAL_SERVICE",
            serviceTitle = "Chain Maintenance",
            performedAtIsoDate = "2026-06-01",
            odometerAtServiceKm = 900.0,
            checklistItemsJson = Json.encodeToString(
                listOf(
                    ServiceChecklistItem(
                        name = "Drive Chain & Sprockets",
                        status = "DONE",
                        taskId = "STD_CHAIN_LUBE",
                        subActions = listOf("CHAIN_CLEANED_LUBED", "CHAIN_ADJUSTED"),
                        completesTask = true
                    )
                )
            )
        )
        val statusLubed = evaluator.evaluate(bike, listOf(taskChain), listOf(logLubed), LocalDate.of(2026, 6, 1))
        assertEquals(TaskStatus.OK, statusLubed[0].status)
        assertEquals(800.0, statusLubed[0].remainingKm!!, 0.01)
    }

    @Test
    fun testInspectionTaskClearedWithTirePressureChecked() {
        val bike = createBaseBike(2500.0)
        val taskTires = createTask(
            taskId = "STD_TIRES_AXLES",
            motorcycleId = bike.id,
            category = "Tires",
            taskName = "Tires, Pressures & Axles",
            actionType = "Inspect",
            intervalKm = 2000.0,
            warningThresholdKm = 250.0
        )

        val tireOptions = ServiceChecklistCatalog.getSubOptionsFor("STD_TIRES_AXLES", "Tires, Pressures & Axles", "Tires")
        assertTrue(ServiceChecklistCatalog.evaluateCompletesTask("Inspect", setOf("TIRE_PRESSURE_CHECKED"), tireOptions))

        val logTires = ServiceLogEntity(
            motorcycleId = bike.id,
            taskId = "GENERAL_SERVICE",
            serviceTitle = "Tire Pressure & Axle Inspection",
            performedAtIsoDate = "2026-06-01",
            odometerAtServiceKm = 2500.0,
            checklistItemsJson = Json.encodeToString(
                listOf(
                    ServiceChecklistItem(
                        name = "Tire Pressure & Tread",
                        status = "DONE",
                        taskId = "STD_TIRES_AXLES",
                        subActions = listOf("TIRE_PRESSURE_CHECKED", "TIRE_TREAD_CHECKED"),
                        note = "Front 2.5 bar, Rear 2.9 bar",
                        completesTask = true
                    )
                )
            )
        )
        val status = evaluator.evaluate(bike, listOf(taskTires), listOf(logTires), LocalDate.of(2026, 6, 1))
        assertEquals(TaskStatus.OK, status[0].status)
        assertEquals(2000.0, status[0].remainingKm!!, 0.01)
    }

    @Test
    fun testMultiTaskCompletionInOneServiceLogClearsMultipleOverdueTasks() {
        val bike = createBaseBike(35000.0)

        val taskOil = createTask(
            taskId = "STD_ENGINE_OIL",
            motorcycleId = bike.id,
            category = "Engine",
            taskName = "Engine Oil & Filter",
            actionType = "Replace",
            intervalKm = 10000.0,
            warningThresholdKm = 500.0
        )
        val taskCoolant = createTask(
            taskId = "STD_COOLANT",
            motorcycleId = bike.id,
            category = "Cooling",
            taskName = "Coolant Replacement",
            actionType = "Replace",
            intervalKm = 30000.0,
            warningThresholdKm = 1000.0
        )
        val taskChain = createTask(
            taskId = "STD_CHAIN_LUBE",
            motorcycleId = bike.id,
            category = "Drivetrain",
            taskName = "Chain Clean, Lube & Slack",
            actionType = "Clean/Lube",
            intervalKm = 800.0,
            warningThresholdKm = 150.0
        )

        val tasks = listOf(taskOil, taskCoolant, taskChain)

        // Initially all 3 are OVERDUE without logs at 35,000 km
        val initialStatuses = evaluator.evaluate(bike, tasks, emptyList(), LocalDate.of(2026, 6, 1))
        assertTrue(initialStatuses.all { it.status == TaskStatus.OVERDUE })

        // Single service log with all 3 items completed and individual notes
        val multiTaskLog = ServiceLogEntity(
            motorcycleId = bike.id,
            taskId = "GENERAL_SERVICE",
            serviceTitle = "Major 35k Workshop Service",
            performedAtIsoDate = "2026-06-01",
            odometerAtServiceKm = 35000.0,
            notes = "Full annual service in garage",
            checklistItemsJson = Json.encodeToString(
                listOf(
                    ServiceChecklistItem(
                        name = "Engine Oil",
                        status = "DONE",
                        taskId = "STD_ENGINE_OIL",
                        subActions = listOf("OIL_CHANGED", "OIL_FILTER_CHANGED"),
                        note = "Motul 7100 10W-40 + OEM filter",
                        completesTask = true
                    ),
                    ServiceChecklistItem(
                        name = "Coolant / Radiator",
                        status = "DONE",
                        taskId = "STD_COOLANT",
                        subActions = listOf("COOLANT_CHANGED", "COOLANT_BLED"),
                        note = "Flushed and refilled Yamacool 50/50",
                        completesTask = true
                    ),
                    ServiceChecklistItem(
                        name = "Drive Chain & Sprockets",
                        status = "DONE",
                        taskId = "STD_CHAIN_LUBE",
                        subActions = listOf("CHAIN_CLEANED_LUBED", "CHAIN_ADJUSTED"),
                        note = "Slack adjusted to 30 mm",
                        completesTask = true
                    )
                )
            )
        )

        val updatedStatuses = evaluator.evaluate(bike, tasks, listOf(multiTaskLog), LocalDate.of(2026, 6, 1))
        assertEquals(3, updatedStatuses.size)
        assertTrue(updatedStatuses.all { it.status == TaskStatus.OK })
        assertEquals(10000.0, updatedStatuses.first { it.task.taskId == "STD_ENGINE_OIL" }.remainingKm!!, 0.01)
        assertEquals(30000.0, updatedStatuses.first { it.task.taskId == "STD_COOLANT" }.remainingKm!!, 0.01)
        assertEquals(65000.0, updatedStatuses.first { it.task.taskId == "STD_COOLANT" }.nextDueOdometerKm!!, 0.01)
        assertEquals(800.0, updatedStatuses.first { it.task.taskId == "STD_CHAIN_LUBE" }.remainingKm!!, 0.01)

        // Verify parseChecklist() compatibility and per-task notes preservation
        val parsedItems = multiTaskLog.parseChecklist()
        assertEquals(3, parsedItems.size)
        assertEquals("Motul 7100 10W-40 + OEM filter", parsedItems[0].note)
        assertEquals("Flushed and refilled Yamacool 50/50", parsedItems[1].note)
        assertEquals("Slack adjusted to 30 mm", parsedItems[2].note)

        // Verify fallback when checklistItemsJson is empty or blank
        val fallbackLog = ServiceLogEntity(
            motorcycleId = bike.id,
            performedAtIsoDate = "2026-06-01",
            odometerAtServiceKm = 1000.0,
            checklistItemsJson = "[]"
        )
        assertTrue(fallbackLog.parseChecklistOrEmpty().isEmpty())
        assertEquals(16, fallbackLog.parseChecklist().size)
    }

    @Test
    fun testRemainingGeneralChecksCatalog() {
        // Without existing tasks, all 16 general workshop checks remain
        val allRemaining = ServiceChecklistCatalog.remainingGeneralChecks(emptyList())
        assertEquals(16, allRemaining.size)

        // With standard MT-09 maintenance tasks (Oil, Coolant, Brake Fluid, Tires, Chain, Valve, Plugs, Air Filter)
        val specs = MotorcycleTemplateCatalog.mt09.technicalSpecs!!
        val standardTasks = MotorcycleTemplateCatalog.buildStandardMaintenanceTasks(specs, "en").map { dto ->
            createTask(
                taskId = dto.taskId,
                motorcycleId = "mt09",
                category = dto.category,
                taskName = dto.taskName,
                actionType = dto.actionType,
                firstBreakInAtKm = dto.firstBreakInAtKm,
                intervalKm = dto.intervalKm,
                intervalMonths = dto.intervalMonths,
                warningThresholdKm = dto.warningThresholdKm,
                warningThresholdDays = dto.warningThresholdDays,
                notes = dto.notes
            )
        }

        val remaining = ServiceChecklistCatalog.remainingGeneralChecks(standardTasks)
        val remainingKeys = remaining.map { it.key }

        // Standard overlapping checks must be filtered out
        assertFalse(remainingKeys.contains("GEN_ENGINE_OIL"))
        assertFalse(remainingKeys.contains("GEN_OIL_FILTER"))
        assertFalse(remainingKeys.contains("GEN_AIR_FILTER"))
        assertFalse(remainingKeys.contains("GEN_SPARK_PLUGS"))
        assertFalse(remainingKeys.contains("GEN_CHAIN"))
        assertFalse(remainingKeys.contains("GEN_BRAKE_FLUID"))
        assertFalse(remainingKeys.contains("GEN_COOLANT"))
        assertFalse(remainingKeys.contains("GEN_TIRES"))

        // Non-overlapping chassis, suspension, controls, electrical checks must remain
        assertTrue(remainingKeys.contains("GEN_BRAKE_PADS"))
        assertTrue(remainingKeys.contains("GEN_CLUTCH_THROTTLE"))
        assertTrue(remainingKeys.contains("GEN_STEERING"))
        assertTrue(remainingKeys.contains("GEN_FORKS"))
        assertTrue(remainingKeys.contains("GEN_REAR_SHOCK"))
        assertTrue(remainingKeys.contains("GEN_BATTERY"))
        assertTrue(remainingKeys.contains("GEN_LIGHTS"))
        assertTrue(remainingKeys.contains("GEN_BOLTS"))
        assertEquals(8, remaining.size)
    }

    @Test
    fun testLocalizeSubActionAndLocalizeItemName() {
        // SubAction EN / SK localizations
        assertEquals("Oil Changed", ServiceChecklistCatalog.localizeSubAction("OIL_CHANGED", "en"))
        assertEquals("Olej vymenený", ServiceChecklistCatalog.localizeSubAction("OIL_CHANGED", "sk"))
        assertEquals("Checked Level", ServiceChecklistCatalog.localizeSubAction("OIL_CHECKED", "en"))
        assertEquals("Skontrolovaná hladina", ServiceChecklistCatalog.localizeSubAction("OIL_CHECKED", "sk"))
        assertEquals("Water / Coolant Changed", ServiceChecklistCatalog.localizeSubAction("COOLANT_CHANGED", "en"))
        assertEquals("Kvapalina vymenená", ServiceChecklistCatalog.localizeSubAction("COOLANT_CHANGED", "sk"))
        assertEquals("Cleaned & Lubed", ServiceChecklistCatalog.localizeSubAction("CHAIN_CLEANED_LUBED", "en"))
        assertEquals("Vyčistená a namazaná", ServiceChecklistCatalog.localizeSubAction("CHAIN_CLEANED_LUBED", "sk"))
        assertEquals("Pressure Checked", ServiceChecklistCatalog.localizeSubAction("TIRE_PRESSURE_CHECKED", "en"))
        assertEquals("Tlak skontrolovaný", ServiceChecklistCatalog.localizeSubAction("TIRE_PRESSURE_CHECKED", "sk"))

        // Unknown key formatting fallback
        assertEquals("Custom task done", ServiceChecklistCatalog.localizeSubAction("CUSTOM_TASK_DONE", "en"))

        // Item name localizations for standard tasks
        val oilItem = ServiceChecklistItem(name = "Engine Oil", status = "DONE", taskId = "STD_ENGINE_OIL")
        assertEquals("Engine Oil & Filter", ServiceChecklistCatalog.localizeItemName(oilItem, emptyList(), "en"))
        assertEquals("Motorový olej a filter", ServiceChecklistCatalog.localizeItemName(oilItem, emptyList(), "sk"))

        val coolantItem = ServiceChecklistItem(name = "Coolant", status = "DONE", taskId = "STD_COOLANT")
        assertEquals("Coolant Replacement", ServiceChecklistCatalog.localizeItemName(coolantItem, emptyList(), "en"))
        assertEquals("Chladiaca kvapalina", ServiceChecklistCatalog.localizeItemName(coolantItem, emptyList(), "sk"))

        // Item name localizations for general workshop checks
        val brakePadsItem = ServiceChecklistItem(name = "Brake Pads Front/Rear", status = "CHECKED", taskId = "GEN_BRAKE_PADS")
        assertEquals("Brake Pads Front/Rear", ServiceChecklistCatalog.localizeItemName(brakePadsItem, emptyList(), "en"))
        assertEquals("Brzdové platničky (P/Z)", ServiceChecklistCatalog.localizeItemName(brakePadsItem, emptyList(), "sk"))
    }

    @Test
    fun testExtendedRepairGuidesInCatalog() {
        val specs = TechnicalSpecsDto(
            motorcycleMake = "Yamaha",
            motorcycleModel = "MT-09",
            frontCaliperBoltsTorqueNm = 35.0,
            rearCaliperBoltsTorqueNm = 27.0,
            forkOilCapacityPerLegMl = 520.0,
            forkOilWeight = "5W",
            frontPinchBoltsTorqueNm = 23.0,
            frontAxleTorqueNm = 65.0,
            rearAxleNutTorqueNm = 105.0,
            idleSpeedRpm = "1,150–1,250 RPM"
        )

        // 1. Brake Pads (specific branch before general brakes)
        val padEn = MotorcycleTemplateCatalog.getProcedureGuideForTask("Brake Pads Front/Rear", "Brakes", specs, "en")
        val padSk = MotorcycleTemplateCatalog.getProcedureGuideForTask("Brzdové platničky (P/Z)", "Brakes", specs, "sk")
        val padDosticeSk = MotorcycleTemplateCatalog.getProcedureGuideForTask("Brzdové doštičky", "Brakes", specs, "sk")

        assertTrue(padEn.contains("35.0 Nm"))
        assertTrue(padEn.contains("27.0 Nm"))
        assertTrue(padEn.contains("Min. Pad Thickness"))
        assertTrue(padSk.contains("35.0 Nm"))
        assertTrue(padSk.contains("27.0 Nm"))
        assertTrue(padSk.contains("Min. hrúbka obloženia"))
        assertTrue(padDosticeSk.contains("35.0 Nm"))

        // General brake fluid guide should still be returned when task does not contain pad/platnič/doštič
        val fluidEn = MotorcycleTemplateCatalog.getProcedureGuideForTask("Brake Fluid Flush", "Brakes", specs, "en")
        assertTrue(fluidEn.contains("Bleed from furthest caliper"))

        // 2. Front Forks
        val forksEn = MotorcycleTemplateCatalog.getProcedureGuideForTask("Front Fork Seals & Oil", "Suspension", specs, "en")
        val forksSk = MotorcycleTemplateCatalog.getProcedureGuideForTask("Guferá a olej prednej vidlice", "Suspension", specs, "sk")
        assertTrue(forksEn.contains("520.0 ml/leg"))
        assertTrue(forksEn.contains("5W"))
        assertTrue(forksEn.contains("23.0 Nm"))
        assertTrue(forksSk.contains("520.0 ml/klzák"))
        assertTrue(forksSk.contains("5W"))
        assertTrue(forksSk.contains("vidlic"))

        // 3. Rear Shock
        val shockEn = MotorcycleTemplateCatalog.getProcedureGuideForTask("Rear Shock Absorber", "Suspension", specs, "en")
        val shockSk = MotorcycleTemplateCatalog.getProcedureGuideForTask("Zadný tlmič pruženia", "Suspension", specs, "sk")
        assertTrue(shockEn.contains("105.0 Nm"))
        assertTrue(shockEn.contains("30–40 mm"))
        assertTrue(shockSk.contains("105.0 Nm"))
        assertTrue(shockSk.contains("tlmič"))

        // 4. Clutch & Throttle
        val controlsEn = MotorcycleTemplateCatalog.getProcedureGuideForTask("Clutch & Throttle Freeplay", "Controls", specs, "en")
        val controlsSk = MotorcycleTemplateCatalog.getProcedureGuideForTask("Vôľa spojky a plynu", "Controls", specs, "sk")
        assertTrue(controlsEn.contains("1,150–1,250 RPM"))
        assertTrue(controlsEn.contains("Clutch Lever Freeplay"))
        assertTrue(controlsSk.contains("1,150–1,250 RPM"))
        assertTrue(controlsSk.contains("spojk"))

        // 5. Steering Head Bearings
        val steeringEn = MotorcycleTemplateCatalog.getProcedureGuideForTask("Steering Head Bearings", "Chassis", specs, "en")
        val steeringSk = MotorcycleTemplateCatalog.getProcedureGuideForTask("Ložiská krku riadenia", "Chassis", specs, "sk")
        assertTrue(steeringEn.contains("Steering Stem Nut"))
        assertTrue(steeringEn.contains("23.0 Nm"))
        assertTrue(steeringSk.contains("krku"))
        assertTrue(steeringSk.contains("23.0 Nm"))

        // 6. Battery & Charging
        val batteryEn = MotorcycleTemplateCatalog.getProcedureGuideForTask("Battery & Charging System", "Electrical", specs, "en")
        val batterySk = MotorcycleTemplateCatalog.getProcedureGuideForTask("Batéria a dobíjanie", "Electrical", specs, "sk")
        assertTrue(batteryEn.contains("13.8–14.8 V"))
        assertTrue(batteryEn.contains("1,150–1,250 RPM"))
        assertTrue(batterySk.contains("13.8–14.8 V"))
        assertTrue(batterySk.contains("batér"))

        // 7. Bolts & Fasteners Torque
        val boltsEn = MotorcycleTemplateCatalog.getProcedureGuideForTask("Bolts & Fasteners Torque", "Chassis", specs, "en")
        val boltsSk = MotorcycleTemplateCatalog.getProcedureGuideForTask("Dotiahnutie skrutiek a spojov", "Chassis", specs, "sk")
        assertTrue(boltsEn.contains("65.0 Nm"))
        assertTrue(boltsEn.contains("105.0 Nm"))
        assertTrue(boltsEn.contains("35.0 Nm"))
        assertTrue(boltsEn.contains("27.0 Nm"))
        assertTrue(boltsEn.contains("23.0 Nm"))
        assertTrue(boltsSk.contains("65.0 Nm"))
        assertTrue(boltsSk.contains("105.0 Nm"))
        assertTrue(boltsSk.contains("35.0 Nm"))
        assertTrue(boltsSk.contains("27.0 Nm"))
        assertTrue(boltsSk.contains("23.0 Nm"))

        // Air filter typo fix check: line 72 "Vytrite" instead of "Vytite"
        val airSk = MotorcycleTemplateCatalog.getProcedureGuideForTask("Vzduchový filter", "Engine", specs, "sk")
        assertTrue(airSk.contains("Vytrite"))
        assertFalse(airSk.contains("Vytite"))
    }
}

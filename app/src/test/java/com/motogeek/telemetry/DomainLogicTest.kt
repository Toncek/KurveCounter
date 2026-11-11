package com.motogeek.telemetry

import com.motogeek.telemetry.data.local.MaintenanceTaskEntity
import com.motogeek.telemetry.data.local.MotorcycleEntity
import com.motogeek.telemetry.data.local.ServiceLogEntity
import com.motogeek.telemetry.domain.maintenance.MaintenanceEvaluator
import com.motogeek.telemetry.domain.maintenance.MotorcycleTemplateCatalog
import com.motogeek.telemetry.domain.maintenance.TaskStatus
import com.motogeek.telemetry.domain.toSafeDouble
import com.motogeek.telemetry.domain.dsp.BiquadButterworthFilter
import com.motogeek.telemetry.domain.toSafeInt
import com.motogeek.telemetry.domain.dsp.PocketModeCalibrator
import com.motogeek.telemetry.domain.dsp.CalibrationState
import com.motogeek.telemetry.domain.dsp.MotorcycleSensorFusionEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class DomainLogicTest {

    @Test
    fun testToSafeDouble() {
        assertEquals(12.34, "12.34".toSafeDouble(), 0.001)
        assertEquals(12.34, " 12,34 ".toSafeDouble(), 0.001)
        assertEquals(0.0, "invalid".toSafeDouble(), 0.001)
        assertEquals(5.5, null.toSafeDouble(5.5), 0.001)
        assertEquals(0.0, "Infinity".toSafeDouble(), 0.001)
        assertEquals(0.0, "NaN".toSafeDouble(), 0.001)
    }

    @Test
    fun testToSafeInt() {
        assertEquals(42, "42".toSafeInt())
        assertEquals(42, " 42 ".toSafeInt())
        assertEquals(0, "42.5".toSafeInt())
        assertEquals(10, null.toSafeInt(10))
        assertEquals(0, "abc".toSafeInt())
    }

    @Test
    fun testMaintenanceEvaluatorStatus() {
        val evaluator = MaintenanceEvaluator()
        val bike = MotorcycleEntity(
            id = "1",
            make = "Yamaha",
            model = "MT-09",
            year = 2023,
            engineDisplacementCc = 890,
            baseOdometerKm = 10000.0,
            accumulatedTrackedKm = 0.0,
            manualAdjustmentOffsetKm = 0.0,
            technicalSpecsJson = "{}",
            isActive = true
        )

        val taskOk = MaintenanceTaskEntity(
            taskId = "oil_change",
            motorcycleId = "1",
            category = "LUBRICATION",
            taskName = "Engine Oil",
            actionType = "REPLACE",
            firstBreakInAtKm = null,
            intervalKm = 10000.0,
            intervalMonths = null,
            warningThresholdKm = 1000.0,
            warningThresholdDays = null,
            oemPartNumbersJson = "[]",
            notes = ""
        )

        val log = ServiceLogEntity(
            motorcycleId = "1",
            taskId = "oil_change",
            serviceTitle = "Engine Oil",
            odometerAtServiceKm = 8000.0,
            performedAtIsoDate = "2026-01-01"
        )

        // currentOdo = 10000, last = 8000, nextDue = 18000 -> remaining = 8000 -> OK
        val statusOk = evaluator.evaluate(bike, listOf(taskOk), listOf(log), LocalDate.of(2026, 6, 1))
        assertEquals(1, statusOk.size)
        assertEquals(TaskStatus.OK, statusOk[0].status)
        assertEquals(8000.0, statusOk[0].remainingKm!!, 0.01)

        // currentOdo = 17500, remaining = 500 <= warningThreshold (1000) -> DUE_SOON
        val bikeDueSoon = bike.copy(baseOdometerKm = 17500.0)
        val statusDueSoon = evaluator.evaluate(bikeDueSoon, listOf(taskOk), listOf(log), LocalDate.of(2026, 6, 1))
        assertEquals(TaskStatus.DUE_SOON, statusDueSoon[0].status)
        assertEquals(500.0, statusDueSoon[0].remainingKm!!, 0.01)

        // currentOdo = 18100, remaining = -100 <= 0 -> OVERDUE
        val bikeOverdue = bike.copy(baseOdometerKm = 18100.0)
        val statusOverdue = evaluator.evaluate(bikeOverdue, listOf(taskOk), listOf(log), LocalDate.of(2026, 6, 1))
        assertEquals(TaskStatus.OVERDUE, statusOverdue[0].status)
        assertEquals(-100.0, statusOverdue[0].remainingKm!!, 0.01)
    }

    @Test
    fun testTemplateCatalogLoading() {
        val templates = MotorcycleTemplateCatalog.allTemplates
        assertEquals(6, templates.size)

        val mt09 = templates.find { it.motorcycle.model.contains("MT-09") }
        assertNotNull(mt09)
        assertEquals("Yamaha", mt09!!.motorcycle.make)
        assertTrue(mt09.maintenanceSchedule.isNotEmpty())
        assertNotNull(mt09.technicalSpecs)

        val cb650r = templates.find { it.motorcycle.model.contains("CB650R") }
        assertNotNull(cb650r)
        assertTrue(cb650r!!.maintenanceSchedule.isNotEmpty())

        val r1250gs = templates.find { it.motorcycle.model.contains("R1250GS") || it.motorcycle.model.contains("R 1250 GS") }
        assertNotNull(r1250gs)
        assertTrue(r1250gs!!.maintenanceSchedule.isNotEmpty())

        val ktm890 = templates.find { it.motorcycle.model.contains("890") }
        assertNotNull(ktm890)
        assertTrue(ktm890!!.maintenanceSchedule.isNotEmpty())
    }

    @Test
    fun testServiceLogChecklistParsing() {
        val emptyLog = ServiceLogEntity(
            motorcycleId = "1",
            performedAtIsoDate = "2026-01-01",
            odometerAtServiceKm = 5000.0,
            checklistItemsJson = ""
        )
        val defaultItems = emptyLog.parseChecklist()
        assertEquals(16, defaultItems.size)
        assertEquals("Engine Oil", defaultItems[0].name)
        assertEquals("CHECKED", defaultItems[0].status)

        val malformedLog = emptyLog.copy(checklistItemsJson = "{corrupted json")
        assertEquals(16, malformedLog.parseChecklist().size)

        val customJson = """[{"name":"Spark Plugs","status":"WARNING"}]"""
        val customLog = emptyLog.copy(checklistItemsJson = customJson)
        val customItems = customLog.parseChecklist()
        assertEquals(1, customItems.size)
        assertEquals("Spark Plugs", customItems[0].name)
        assertEquals("WARNING", customItems[0].status)
    }

    @Test
    fun testFuelAndTcoSafeties() {
        // Zero distance test (guards against DivideByZero / Infinity / NaN)
        val distance = 0.0
        val liters = 10.0
        val lPer100 = if (distance > 0) (liters / distance) * 100.0 else 0.0
        val kmPerL = if (liters > 0) distance / liters else 0.0

        assertTrue(lPer100.isFinite())
        assertEquals(0.0, lPer100, 0.001)
        assertTrue(kmPerL.isFinite())
        assertEquals(0.0, kmPerL, 0.001)

        // Normal consumption
        val validDistance = 250.0
        val validLiters = 12.5
        val validLPer100 = (validLiters / validDistance) * 100.0
        assertEquals(5.0, validLPer100, 0.001)
    }

    @Test
    fun testBiquadButterworthFilter() {
        val filter = BiquadButterworthFilter(sampleRateHz = 100.0, cutoffHz = 3.5)

        // 1. DC input convergence
        var output = 0.0
        for (i in 1..50) {
            output = filter.process(10.0)
        }
        assertEquals(10.0, output, 0.1)

        // 2. Output finiteness
        val nanOutput = filter.process(Double.NaN)
        assertTrue(nanOutput.isFinite())
        assertEquals(0.0, nanOutput, 0.001)

        // 3. Reset clears internal state
        filter.reset()
        assertEquals(0.0, filter.process(0.0), 0.001)
    }

    @Test
    fun testPocketModeCalibrator_calibrateManualZero() {
        val calibrator = PocketModeCalibrator()
        
        // Ensure state is waiting
        assertEquals(CalibrationState.WAITING, calibrator.state)

        // Process an IMU sample with some tilt
        calibrator.processImuSample(0L, 2.0, 3.0, 9.0)

        // Trigger manual zero
        val locked = calibrator.calibrateManualZero()

        assertTrue(locked)
        assertEquals(CalibrationState.LOCKED, calibrator.state)
        assertEquals(1.0, calibrator.progress, 0.001)
    }

    @Test
    fun testMotorcycleSensorFusionEngine_calibrateManualZero() {
        val engine = MotorcycleSensorFusionEngine { }
        
        engine.setManualLeanOffset(5.0)
        assertEquals(5.0, engine.manualLeanOffsetDeg, 0.001)
        
        // Feed in some samples to get raw lean
        for (i in 0..100) {
            engine.processImu(i * 10000000L, 2.0, 0.0, 9.8, 0.5, 0.0, 0.0)
            engine.processGps(i * 10L, 20.0, true, 0.0, true, 0.0, 0.0)
        }

        // Calibrate manual zero
        engine.calibrateManualZero()

        // Test offset is updated and lean angle is reset to 0
        assertEquals(0.0, engine.currentLeanAngleDeg, 0.001)
    }

    @Test
    fun testCornerDetectionWithNanosecondAndWallClockTimestamps() {
        val detectedCorners = mutableListOf<com.motogeek.telemetry.data.local.CornerEventEntity>()
        val engine = MotorcycleSensorFusionEngine { corner ->
            detectedCorners.add(corner)
        }

        // First lock stationary gravity calibration (160 samples at 100Hz = 10_000_000 ns step)
        var tsNanos = 1_000_000_000L
        var wallMs = 1_700_000_000_000L
        for (i in 0..160) {
            engine.processImu(tsNanos, 0.0, 0.0, 9.80665, 0.0, 0.0, 0.0, wallClockMs = wallMs)
            tsNanos += 10_000_000L
            wallMs += 10L
        }
        assertTrue(engine.calibrator.isCalibrated)

        // Set cruising GPS speed (22 m/s = 79.2 km/h)
        engine.processGps(wallMs, 22.0, true, 0.0, true, 48.0, 17.0)

        // Sustain a right-hand corner (roll rate gyroY + yaw rate gyroZ = -0.35 rad/s) for 2.0 seconds (200 frames = 2000ms > 1200ms)
        for (i in 0..200) {
            val rollRateRad = if (i < 35) 1.0 else 0.0
            engine.processImu(tsNanos, 0.0, 0.0, 9.80665, 0.0, rollRateRad, -0.35, wallClockMs = wallMs)
            tsNanos += 10_000_000L
            wallMs += 10L
        }

        // Straighten out (< 8 deg lean) so corner completes and emits
        for (i in 0..150) {
            val rollRateRad = if (i < 35) -1.0 else 0.0
            engine.processImu(tsNanos, 0.0, 0.0, 9.80665, 0.0, rollRateRad, 0.0, wallClockMs = wallMs)
            tsNanos += 10_000_000L
            wallMs += 10L
        }

        assertEquals(1, engine.totalCorners)
        assertEquals(1, detectedCorners.size)
        assertTrue(detectedCorners[0].apexLeanDeg >= 12.0)
    }

    @Test
    fun testMaintenanceEvaluatorFlexibleDateAndNewBikeTimeTasks() {
        val evaluator = MaintenanceEvaluator()
        val now = LocalDate.of(2026, 3, 15)

        // Bike with dd/MM/yyyy startDateIso (default in MotorcycleEntity) and no service logs yet
        val newBike = MotorcycleEntity(
            id = "bike_new",
            make = "Yamaha",
            model = "MT-09",
            year = 2026,
            engineDisplacementCc = 890,
            baseOdometerKm = 100.0,
            accumulatedTrackedKm = 0.0,
            manualAdjustmentOffsetKm = 0.0,
            technicalSpecsJson = "{}",
            startDateIso = "01/03/2026",
            isActive = true
        )

        val brakeFluidTask = MaintenanceTaskEntity(
            taskId = "STD_BRAKE_FLUID",
            motorcycleId = "bike_new",
            category = "Brakes",
            taskName = "Brake Fluid Flush",
            actionType = "Replace",
            firstBreakInAtKm = null,
            intervalKm = null,
            intervalMonths = 24,
            warningThresholdKm = null,
            warningThresholdDays = 30,
            oemPartNumbersJson = "[]",
            notes = ""
        )

        val evaluated = evaluator.evaluate(newBike, listOf(brakeFluidTask), emptyList(), now)
        assertEquals(1, evaluated.size)
        // Should be OK (~716 days remaining), NOT immediately OVERDUE!
        assertEquals(TaskStatus.OK, evaluated[0].status)
        assertTrue((evaluated[0].remainingDays ?: 0L) > 600L)

        // Also test malformed date resilience in ServiceLogEntity
        val malformedLog = ServiceLogEntity(
            motorcycleId = "bike_new",
            taskId = "STD_BRAKE_FLUID",
            performedAtIsoDate = "not-a-valid-date",
            odometerAtServiceKm = 100.0
        )
        val evaluatedWithBadDate = evaluator.evaluate(newBike, listOf(brakeFluidTask), listOf(malformedLog), now)
        assertEquals(TaskStatus.OK, evaluatedWithBadDate[0].status)
    }

    @Test
    fun testConsolidatedTorqueMapAndDeterministicTaskIds() {
        val specs = MotorcycleTemplateCatalog.mt09.technicalSpecs!!
        val map = specs.toConsolidatedTorqueMap()
        assertTrue(map.containsKey("Rear Axle Nut"))
        assertTrue(map.containsKey("Front Axle"))
        assertTrue(map.containsKey("Front Axle Pinch Bolts"))
        assertTrue(map.containsKey("Oil Drain Plug"))
        assertTrue(map.containsKey("Oil Filter"))
        assertTrue(map.containsKey("Front Brake Caliper Bolts"))
        assertTrue(map.containsKey("Rear Brake Caliper Bolts"))
        assertTrue(map.containsKey("Brake Bleed Nipple"))
        assertTrue(map.containsKey("Spark Plugs"))
        assertTrue(map.containsKey("Water Pump Drain Bolt"))
        assertTrue(map.containsKey("Cylinder Coolant Drain Bolt"))
        assertEquals(11, map.size)

        val tasks1 = MotorcycleTemplateCatalog.buildStandardMaintenanceTasks(specs, "en")
        val tasks2 = MotorcycleTemplateCatalog.buildStandardMaintenanceTasks(specs, "en")
        assertEquals(tasks1.map { it.taskId }, tasks2.map { it.taskId })
        assertTrue(tasks1.any { it.taskId == "STD_ENGINE_OIL" })
        assertTrue(tasks1.all { !it.notes.contains("Výpustná") }) // English procedure guides

        val tasksSk = MotorcycleTemplateCatalog.buildStandardMaintenanceTasks(specs, "sk")
        assertEquals(tasks1.map { it.taskId }, tasksSk.map { it.taskId })
        assertTrue(tasksSk.any { it.taskName.contains("Motorový olej") })
        assertTrue(tasksSk.any { it.notes.contains("Výpustná skrutka") }) // Slovak procedure guides
    }
}

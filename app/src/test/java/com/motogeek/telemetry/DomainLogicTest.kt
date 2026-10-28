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
}

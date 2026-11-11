package com.motogeek.telemetry.domain.backup

import com.motogeek.telemetry.data.local.FuelLogEntity
import com.motogeek.telemetry.data.local.PartLogEntity
import com.motogeek.telemetry.data.local.ReminderEntity
import com.motogeek.telemetry.data.local.RideSessionEntity
import com.motogeek.telemetry.domain.maintenance.GarageBackupPayload
import com.motogeek.telemetry.domain.maintenance.MotorcycleBackupDto
import com.motogeek.telemetry.domain.maintenance.MotorcycleDto
import com.motogeek.telemetry.domain.maintenance.RideBackupDto
import com.motogeek.telemetry.domain.maintenance.ServiceLogDto
import com.motogeek.telemetry.domain.maintenance.TelemetryPreferencesDto
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GoogleDriveBackupTest {

    private val json = Json {
        encodeDefaults = true
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }

    @Test
    fun testV1BackupPayloadBackwardCompatibility() {
        val v1PayloadStr = """
            {"exportDateIso":"2023-01-01T00:00:00Z","motorcycles":[{"motorcycle":{"id":"bike1","make":"Yamaha","model":"MT-07","year":2021,"engineDisplacementCc":689,"baseOdometerKm":10000.0,"accumulatedTrackedKm":150.0,"manualAdjustmentOffsetKm":0.0,"isActive":true},"technicalSpecs":null,"maintenanceSchedule":[],"serviceLogs":[]}]}
        """.trimIndent()

        val decoded = json.decodeFromString<GarageBackupPayload>(v1PayloadStr)
        assertEquals(1, decoded.motorcycles.size)
        assertEquals("Yamaha", decoded.motorcycles[0].motorcycle.make)
        assertEquals("MT-07", decoded.motorcycles[0].motorcycle.model)
        assertEquals(10000.0, decoded.motorcycles[0].motorcycle.baseOdometerKm, 0.01)
        assertTrue(decoded.motorcycles[0].fuelLogs.isEmpty())
        assertTrue(decoded.motorcycles[0].rides.isEmpty())
    }

    @Test
    fun testV2FullGarageBackupRoundTrip() {
        val original = GarageBackupPayload(
            schemaVersion = 2,
            exportDateIso = "2026-11-11T10:00:00Z",
            preferences = TelemetryPreferencesDto(
                autoStartAbove5Kmh = true,
                startOnBoot = false,
                manualLeanOffsetDeg = 1.5
            ),
            motorcycles = listOf(
                MotorcycleBackupDto(
                    motorcycle = MotorcycleDto(
                        id = "bike_mt09",
                        name = "Track Weapon",
                        make = "Yamaha",
                        model = "MT-09",
                        year = 2024,
                        engineDisplacementCc = 890,
                        baseOdometerKm = 5000.0,
                        accumulatedTrackedKm = 1200.0,
                        manualAdjustmentOffsetKm = 50.0,
                        isActive = true,
                        type = "Naked",
                        startDateIso = "2024-05-01",
                        buyingPrice = 10900.0,
                        currencySymbol = "€"
                    ),
                    serviceLogs = listOf(
                        ServiceLogDto(
                            logId = "log_1",
                            taskId = "STD_ENGINE_OIL",
                            performedAtIsoDate = "2026-04-01",
                            odometerAtServiceKm = 6000.0,
                            costEur = 95.0,
                            performedBy = "Self",
                            notes = "Yamalube 10W-40",
                            serviceTitle = "Engine Oil & Filter",
                            locationOrShop = "Home Garage",
                            servicedAtKm = 6000.0,
                            limitKm = 10000.0,
                            endKm = 16000.0,
                            checklistItemsJson = """[{"name":"Engine Oil","status":"CHECKED"}]"""
                        )
                    ),
                    fuelLogs = listOf(
                        FuelLogEntity(
                            fuelId = 1L,
                            motorcycleId = "bike_mt09",
                            dateIso = "2026-05-01",
                            odometerKm = 6200.0,
                            liters = 11.5,
                            pricePerLiter = 1.65,
                            totalCost = 18.98,
                            isFullTank = true,
                            notes = "98 RON"
                        )
                    ),
                    partLogs = listOf(
                        PartLogEntity(
                            partId = 1L,
                            motorcycleId = "bike_mt09",
                            partName = "S22 Rear Tire",
                            brand = "Bridgestone",
                            partNumber = "180/55ZR17",
                            cost = 165.0,
                            odometerKm = 6100.0,
                            dateIso = "2026-04-20",
                            notes = "Mounted & balanced"
                        )
                    ),
                    reminders = listOf(
                        ReminderEntity(
                            reminderId = 1L,
                            motorcycleId = "bike_mt09",
                            title = "Renew Insurance",
                            targetOdometerKm = 15000.0,
                            targetDateIso = "2027-01-01"
                        )
                    ),
                    rides = listOf(
                        RideBackupDto(
                            session = RideSessionEntity(
                                rideId = 10L,
                                motorcycleId = "bike_mt09",
                                name = "Mountain Pass",
                                startTimeEpochMs = 1715000000000L,
                                endTimeEpochMs = 1715003600000L,
                                distanceKm = 82.4,
                                avgSpeedKmh = 72.5,
                                maxSpeedKmh = 148.0,
                                maxLeanLeftDeg = 42.5,
                                maxLeanRightDeg = 44.1,
                                maxTurnInRateDegPerSec = 68.0,
                                maxBrakingG = -0.75,
                                maxAccelG = 0.62,
                                maxLateralG = 0.88,
                                trailBrakingScorePct = 50.0,
                                totalCornersCount = 24,
                                leftCornersCount = 12,
                                rightCornersCount = 12,
                                avgCornerScore = 88.5,
                                elevationGainMeters = 640.0,
                                ridingStyleBadge = "Canyon Carver"
                            )
                        )
                    )
                )
            )
        )

        val encoded = json.encodeToString(original)
        val restored = json.decodeFromString<GarageBackupPayload>(encoded)

        assertEquals(2, restored.schemaVersion)
        assertEquals(1.5, restored.preferences?.manualLeanOffsetDeg ?: 0.0, 0.001)
        val restoredBike = restored.motorcycles[0]
        assertEquals("Track Weapon", restoredBike.motorcycle.name)
        assertEquals(10900.0, restoredBike.motorcycle.buyingPrice, 0.01)
        assertEquals(1, restoredBike.serviceLogs.size)
        assertTrue(restoredBike.serviceLogs[0].checklistItemsJson.contains("Engine Oil"))
        assertEquals(1, restoredBike.fuelLogs.size)
        assertEquals(11.5, restoredBike.fuelLogs[0].liters, 0.01)
        assertEquals(1, restoredBike.partLogs.size)
        assertEquals("Bridgestone", restoredBike.partLogs[0].brand)
        assertEquals(1, restoredBike.reminders.size)
        assertEquals("Renew Insurance", restoredBike.reminders[0].title)
        assertEquals(1, restoredBike.rides.size)
        assertEquals(82.4, restoredBike.rides[0].session.distanceKm, 0.01)
    }
}

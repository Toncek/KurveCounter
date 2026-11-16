package com.motogeek.telemetry

import android.content.Context
import android.content.ContextWrapper
import android.net.Uri
import com.motogeek.telemetry.data.local.*
import com.motogeek.telemetry.domain.maintenance.ImportExportManager
import com.motogeek.telemetry.domain.telemetry.GpxTelemetryCodec
import com.motogeek.telemetry.ui.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.util.Locale
import java.util.UUID

class RideHistoryGpxAndAnalyticsTest {

    // =========================================================================
    // 1. GpxTelemetryCodec Tests
    // =========================================================================

    @Test
    fun testIsValidCoordinate() {
        // Valid coordinates
        assertTrue(GpxTelemetryCodec.isValidCoordinate(48.1486, 17.1077)) // Bratislava
        assertTrue(GpxTelemetryCodec.isValidCoordinate(-33.8688, 151.2093)) // Sydney
        assertTrue(GpxTelemetryCodec.isValidCoordinate(0.5, 0.5))
        assertTrue(GpxTelemetryCodec.isValidCoordinate(89.99, 179.99))
        assertTrue(GpxTelemetryCodec.isValidCoordinate(-89.99, -179.99))

        // Invalid: near (0, 0)
        assertFalse(GpxTelemetryCodec.isValidCoordinate(0.0, 0.0))
        assertFalse(GpxTelemetryCodec.isValidCoordinate(0.00005, 0.00005))
        assertFalse(GpxTelemetryCodec.isValidCoordinate(-0.00005, -0.00005))

        // Invalid: out of bounds
        assertFalse(GpxTelemetryCodec.isValidCoordinate(90.1, 10.0))
        assertFalse(GpxTelemetryCodec.isValidCoordinate(-90.1, 10.0))
        assertFalse(GpxTelemetryCodec.isValidCoordinate(10.0, 180.1))
        assertFalse(GpxTelemetryCodec.isValidCoordinate(10.0, -180.1))

        // Invalid: non-finite
        assertFalse(GpxTelemetryCodec.isValidCoordinate(Double.NaN, 17.0))
        assertFalse(GpxTelemetryCodec.isValidCoordinate(48.0, Double.NaN))
        assertFalse(GpxTelemetryCodec.isValidCoordinate(Double.POSITIVE_INFINITY, 17.0))
        assertFalse(GpxTelemetryCodec.isValidCoordinate(48.0, Double.NEGATIVE_INFINITY))
    }

    @Test
    fun testFilterValidTrackPoints() {
        val points = listOf(
            TelemetryTrackPointEntity(pointId = 1L, rideId = 1L, timestampMs = 1000L, latitude = 48.1486, longitude = 17.1077, speedKmh = 50.0, leanAngleDeg = 0.0, longitudinalG = 0.0, lateralG = 0.0, altitudeMeters = 100.0),
            TelemetryTrackPointEntity(pointId = 2L, rideId = 1L, timestampMs = 2000L, latitude = 0.0, longitude = 0.0, speedKmh = 50.0, leanAngleDeg = 0.0, longitudinalG = 0.0, lateralG = 0.0, altitudeMeters = 100.0), // invalid (0,0)
            TelemetryTrackPointEntity(pointId = 3L, rideId = 1L, timestampMs = 3000L, latitude = Double.NaN, longitude = 17.1080, speedKmh = 50.0, leanAngleDeg = 0.0, longitudinalG = 0.0, lateralG = 0.0, altitudeMeters = 100.0), // NaN lat
            TelemetryTrackPointEntity(pointId = 4L, rideId = 1L, timestampMs = 4000L, latitude = 48.1500, longitude = 17.1100, speedKmh = 50.0, leanAngleDeg = 0.0, longitudinalG = 0.0, lateralG = 0.0, altitudeMeters = 100.0),
            TelemetryTrackPointEntity(pointId = 5L, rideId = 1L, timestampMs = 5000L, latitude = 95.0, longitude = 17.1100, speedKmh = 50.0, leanAngleDeg = 0.0, longitudinalG = 0.0, lateralG = 0.0, altitudeMeters = 100.0) // out of bounds
        )

        val filtered = GpxTelemetryCodec.filterValidTrackPoints(points)
        assertEquals(2, filtered.size)
        assertEquals(1L, filtered[0].pointId)
        assertEquals(4L, filtered[1].pointId)
    }

    @Test
    fun testXmlEscape() {
        val raw = """Speed & Lean <Track> "Fast" 'Corners'"""
        val escaped = GpxTelemetryCodec.xmlEscape(raw)
        assertEquals("""Speed &amp; Lean &lt;Track&gt; &quot;Fast&quot; &apos;Corners&apos;""", escaped)
    }

    @Test
    fun testGpx11ExportAndRoundTripImport_withSpecialCharsAndSlovakLocale() {
        val originalLocale = Locale.getDefault()
        try {
            // Set Slovak locale (uses comma as decimal separator: e.g. 12,34)
            // to ensure GPX formatting strictly enforces Locale.US (dots)
            Locale.setDefault(Locale("sk", "SK"))

            val ride = RideSessionEntity(
                rideId = 42L,
                motorcycleId = "bike_yamaha_mt09",
                startTimeEpochMs = 1715335200000L, // 2024-05-10T10:00:00Z
                endTimeEpochMs = 1715338800000L,   // 2024-05-10T11:00:00Z
                distanceKm = 45.8,
                avgSpeedKmh = 62.5,
                maxSpeedKmh = 124.6,
                maxLeanLeftDeg = 44.8,
                maxLeanRightDeg = 47.2,
                maxLateralG = 1.15,
                maxBrakingG = 0.92,
                maxAccelG = 0.58,
                totalCornersCount = 28,
                leftCornersCount = 13,
                rightCornersCount = 15,
                maxTurnInRateDegPerSec = 72.4,
                trailBrakingScorePct = 81.0,
                avgCornerScore = 88.5,
                elevationGainMeters = 620.0,
                ridingStyleBadge = "TECHNICIAN",
                name = """Pezinská Baba & "Ostrý Roh" <Slovakia>""",
                notes = "Great grip & dry asphalt"
            )

            val trackPoints = listOf(
                TelemetryTrackPointEntity(
                    pointId = 1L,
                    rideId = 42L,
                    timestampMs = 1715335200000L,
                    latitude = 48.331200,
                    longitude = 17.185600,
                    altitudeMeters = 340.5,
                    speedKmh = 55.4,
                    leanAngleDeg = -12.3,
                    longitudinalG = 0.15,
                    lateralG = 0.28
                ),
                TelemetryTrackPointEntity(
                    pointId = 2L,
                    rideId = 42L,
                    timestampMs = 1715335202000L,
                    latitude = 48.332500,
                    longitude = 17.187200,
                    altitudeMeters = 352.0,
                    speedKmh = 72.1,
                    leanAngleDeg = 46.5,
                    longitudinalG = -0.45,
                    lateralG = 1.12
                ),
                TelemetryTrackPointEntity(
                    pointId = 3L,
                    rideId = 42L,
                    timestampMs = 1715335205000L,
                    latitude = 48.334100,
                    longitude = 17.189500,
                    altitudeMeters = 365.8,
                    speedKmh = 88.3,
                    leanAngleDeg = -38.2,
                    longitudinalG = 0.32,
                    lateralG = -0.85
                )
            )

            val cornerEvents = listOf(
                CornerEventEntity(
                    cornerId = 101L,
                    rideId = 42L,
                    sequenceNumber = 1,
                    timestampMs = 1715335202000L,
                    direction = "RIGHT",
                    entrySpeedKmh = 75.0,
                    apexLeanDeg = 46.5,
                    peakLateralG = 1.12,
                    peakBrakingG = 0.45,
                    exitAccelG = 0.35,
                    maxRollRateDegPerSec = 68.0,
                    cornerScore = 92.0,
                    latitude = 48.332500, // Valid apex coordinates
                    longitude = 17.187200
                ),
                CornerEventEntity(
                    cornerId = 102L,
                    rideId = 42L,
                    sequenceNumber = 2,
                    timestampMs = 1715335205000L,
                    direction = "LEFT",
                    entrySpeedKmh = 80.0,
                    apexLeanDeg = 38.2,
                    peakLateralG = 0.85,
                    peakBrakingG = 0.30,
                    exitAccelG = 0.32,
                    maxRollRateDegPerSec = 55.0,
                    cornerScore = 86.0,
                    latitude = 0.0, // Invalid coordinate -> MUST fallback to nearest trackpoint!
                    longitude = 0.0
                )
            )

            // Encode to GPX 1.1 XML
            val gpxXml = GpxTelemetryCodec.encodeRideToGpx(
                ride = ride,
                trackPoints = trackPoints,
                cornerEvents = cornerEvents,
                bikeName = "Yamaha MT-09 SP & Akrapovič"
            )

            // Assertions on generated GPX XML
            assertTrue(gpxXml.startsWith("""<?xml version="1.0" encoding="UTF-8"?>"""))
            assertTrue(gpxXml.contains("""<gpx version="1.1""""))
            assertTrue(gpxXml.contains("""xmlns="http://www.topografix.com/GPX/1/1""""))
            assertTrue(gpxXml.contains("""xmlns:gpxtpx="http://www.garmin.com/xmlschemas/TrackPointExtension/v2""""))
            assertTrue(gpxXml.contains("""xmlns:kurve="https://kurvecounter.app/xmlschemas/TelemetryExtension/v1""""))

            // Verify XML special character escaping in ride name & bike name
            assertTrue(gpxXml.contains("Pezinská Baba &amp; &quot;Ostrý Roh&quot; &lt;Slovakia&gt;"))
            assertTrue(gpxXml.contains("Yamaha MT-09 SP &amp; Akrapovič"))

            // Verify decimal dots used despite Slovak locale
            assertTrue(gpxXml.contains("""lat="48.331200" lon="17.185600""""))
            assertFalse(gpxXml.contains("""lat="48,331200""""))

            // Verify waypoint pins for corner events exist
            assertTrue(gpxXml.contains("<wpt lat=\"48.332500\" lon=\"17.187200\">"))
            // Verify invalid corner (0,0) fell back to nearest trackpoint (point 3 at 48.334100, 17.189500)
            assertTrue(gpxXml.contains("<wpt lat=\"48.334100\" lon=\"17.189500\">"))

            // Verify extensions
            assertTrue(gpxXml.contains("<gpxtpx:TrackPointExtension>"))
            assertTrue(gpxXml.contains("<kurve:TelemetryExtension>"))
            assertTrue(gpxXml.contains("<kurve:leanAngle>46.50</kurve:leanAngle>"))

            // Now perform round-trip import
            val imported = GpxTelemetryCodec.parseGpxToRide(
                gpxXml = gpxXml,
                motorcycleId = "bike_imported_1",
                fallbackName = "Imported Ride"
            )

            assertTrue(imported.hasEmbeddedImuTelemetry)
            assertEquals("bike_imported_1", imported.ride.motorcycleId)
            assertEquals("""Pezinská Baba & "Ostrý Roh" <Slovakia>""", imported.ride.name)
            assertEquals(3, imported.trackPoints.size)
            assertEquals(2, imported.cornerEvents.size)

            // Check trackpoint details parsed accurately
            assertEquals(48.3312, imported.trackPoints[0].latitude, 0.0001)
            assertEquals(17.1856, imported.trackPoints[0].longitude, 0.0001)
            assertEquals(55.4, imported.trackPoints[0].speedKmh, 0.1)
            assertEquals(-12.3, imported.trackPoints[0].leanAngleDeg, 0.1)

            assertEquals(46.5, imported.trackPoints[1].leanAngleDeg, 0.1)

            // Check corner event fallback coordinate resolution
            val corner2 = imported.cornerEvents.find { it.sequenceNumber == 2 }
            assertNotNull(corner2)
            assertTrue(GpxTelemetryCodec.isValidCoordinate(corner2!!.latitude, corner2.longitude))
            assertEquals(48.3341, corner2.latitude, 0.0001)
            assertEquals(17.1895, corner2.longitude, 0.0001)

            // Check ride metrics are positive and finite
            assertTrue(imported.ride.distanceKm > 0.0)
            assertTrue(imported.ride.maxSpeedKmh > 0.0)
            assertTrue(imported.ride.maxLeanRightDeg > 0.0)
            assertTrue(imported.ride.distanceKm.isFinite())
            assertTrue(imported.ride.avgSpeedKmh.isFinite())
        } finally {
            Locale.setDefault(originalLocale)
        }
    }

    @Test
    fun testExternalGpxWithoutKurveExtension() {
        // Standard GPX 1.1 file produced by a Garmin, OsmAnd, or Kurviger device without Kurve extensions
        val externalGpxXml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <gpx version="1.1" creator="External GPS Device" xmlns="http://www.topografix.com/GPX/1/1">
              <metadata>
                <name>Grossglockner High Alpine Road</name>
                <time>2026-06-15T09:00:00Z</time>
              </metadata>
              <trk>
                <name>Grossglockner High Alpine Road</name>
                <trkseg>
                  <trkpt lat="47.0742" lon="12.8385">
                    <ele>1850.0</ele>
                    <time>2026-06-15T09:00:00Z</time>
                  </trkpt>
                  <trkpt lat="47.0765" lon="12.8410">
                    <ele>1885.0</ele>
                    <time>2026-06-15T09:00:20Z</time>
                  </trkpt>
                  <trkpt lat="47.0790" lon="12.8445">
                    <ele>1920.0</ele>
                    <time>2026-06-15T09:00:45Z</time>
                  </trkpt>
                  <trkpt lat="47.0815" lon="12.8480">
                    <ele>1955.0</ele>
                    <time>2026-06-15T09:01:10Z</time>
                  </trkpt>
                </trkseg>
              </trk>
            </gpx>
        """.trimIndent()

        val imported = GpxTelemetryCodec.parseGpxToRide(
            gpxXml = externalGpxXml,
            motorcycleId = "target_bike_id",
            fallbackName = "Default Ride"
        )

        assertFalse("External GPX should not flag embedded IMU telemetry", imported.hasEmbeddedImuTelemetry)
        assertEquals("Grossglockner High Alpine Road", imported.ride.name)
        assertEquals(4, imported.trackPoints.size)

        // Verify computed metrics
        assertTrue("Distance should be computed via Haversine", imported.ride.distanceKm > 0.0)
        assertTrue("Elevation gain should be positive", imported.ride.elevationGainMeters >= 100.0)
        assertTrue("Average speed should be computed from timestamps & distance", imported.ride.avgSpeedKmh > 0.0)

        // Verify all values are completely finite (zero NaN / Infinity)
        for (pt in imported.trackPoints) {
            assertTrue(pt.latitude.isFinite())
            assertTrue(pt.longitude.isFinite())
            assertTrue(pt.speedKmh.isFinite())
            assertTrue(pt.leanAngleDeg.isFinite())
            assertTrue(pt.longitudinalG.isFinite())
            assertTrue(pt.lateralG.isFinite())
            assertTrue(pt.altitudeMeters.isFinite())
        }
    }

    @Test(expected = IllegalArgumentException::class)
    fun testParseGpxToRide_BlankThrows() {
        GpxTelemetryCodec.parseGpxToRide("   ", "bike1")
    }

    @Test(expected = IllegalArgumentException::class)
    fun testParseGpxToRide_NoTrackPointsThrows() {
        val emptyGpx = """
            <?xml version="1.0" encoding="UTF-8"?>
            <gpx version="1.1" creator="Empty" xmlns="http://www.topografix.com/GPX/1/1">
              <metadata><name>Empty Ride</name></metadata>
              <trk><name>Empty</name><trkseg></trkseg></trk>
            </gpx>
        """.trimIndent()
        GpxTelemetryCodec.parseGpxToRide(emptyGpx, "bike1")
    }

    // =========================================================================
    // 2. ImportExportManager.exportRideAsCsv Non-Hanging Test
    // =========================================================================

    @Test(timeout = 5000L)
    fun testExportRideAsCsvDoesNotHang() = runBlocking {
        val fakeDao = GuidedImportTest.FakeMotoGeekDao()
        val repo = MotoGeekRepository(fakeDao)

        // Seed fake trackpoints
        fakeDao.trackPoints.addAll(
            listOf(
                TelemetryTrackPointEntity(
                    pointId = 1L,
                    rideId = 99L,
                    timestampMs = 1700000000000L,
                    latitude = 48.1486,
                    longitude = 17.1077,
                    altitudeMeters = 150.0,
                    speedKmh = 50.0,
                    leanAngleDeg = 15.0,
                    longitudinalG = 0.2,
                    lateralG = 0.3
                ),
                TelemetryTrackPointEntity(
                    pointId = 2L,
                    rideId = 99L,
                    timestampMs = 1700000001000L,
                    latitude = 48.1490,
                    longitude = 17.1085,
                    altitudeMeters = 152.0,
                    speedKmh = 55.0,
                    leanAngleDeg = 20.0,
                    longitudinalG = 0.1,
                    lateralG = 0.4
                )
            )
        )

        // Verify list method terminates immediately
        val list = repo.getTelemetryTrackPointsListForRide(99L)
        assertEquals(2, list.size)

        // Test exportRideAsCsv execution completes cleanly within timeout
        val context = GuidedImportTest.FakeContext()
        val manager = ImportExportManager(context, repo)

        // Attempting to export with a dummy URI:
        // Even if the dummy URI cannot open a real file stream, exportRideAsCsv must terminate
        // cleanly and return Result.failure (or success), NEVER hanging indefinitely!
        val dummyUri = try {
            Uri.parse("content://dummy/test_ride.csv")
        } catch (_: Throwable) {
            null
        }

        if (dummyUri != null) {
            withTimeout(3000L) {
                val result = manager.exportRideAsCsv(dummyUri, 99L)
                assertNotNull(result)
            }
        }
    }

    // =========================================================================
    // 3. Fuel Economy Calculation Tests
    // =========================================================================

    @Test
    fun testComputeFuelEconomyStats_FullTankSegments() {
        val logs = listOf(
            FuelLogEntity(fuelId = 1L, motorcycleId = "b1", dateIso = "2026-05-01", odometerKm = 1000.0, liters = 15.0, pricePerLiter = 2.0, totalCost = 30.0, isFullTank = true, notes = ""),
            FuelLogEntity(fuelId = 2L, motorcycleId = "b1", dateIso = "2026-05-05", odometerKm = 1150.0, liters = 5.0, pricePerLiter = 2.0, totalCost = 10.0, isFullTank = false, notes = ""),
            FuelLogEntity(fuelId = 3L, motorcycleId = "b1", dateIso = "2026-05-10", odometerKm = 1300.0, liters = 10.0, pricePerLiter = 2.0, totalCost = 20.0, isFullTank = true, notes = ""),
            FuelLogEntity(fuelId = 4L, motorcycleId = "b1", dateIso = "2026-05-18", odometerKm = 1600.0, liters = 14.0, pricePerLiter = 2.0, totalCost = 28.0, isFullTank = true, notes = "")
        )

        val stats = RiderAnalyticsCalculator.computeFuelEconomyStats(logs)

        // Segment 1 (1000 -> 1300): 300 km, intermediate fill (5L) + full fill (10L) = 15L -> 5.0 L/100km
        // Segment 2 (1300 -> 1600): 300 km, full fill (14L) = 14L -> 4.67 L/100km
        // Total segment distance: 600 km, total segment liters: 29L -> Avg: (29 / 600) * 100 = 4.83 L/100km
        assertEquals(4.83, stats.avgLitersPer100Km, 0.05)
        assertEquals(4.67, stats.bestLitersPer100Km, 0.05)
        assertEquals(100.0 / stats.avgLitersPer100Km, stats.avgKmPerLiter, 0.05)
        assertEquals(44.0, stats.totalLiters, 0.01)
        assertEquals(44.0, stats.totalFuelLiters, 0.01)
        assertEquals(88.0, stats.totalFuelCost, 0.01)
        assertEquals(88.0, stats.totalFuelSpent, 0.01)
        assertEquals(2.0, stats.avgPricePerLiter, 0.01)
        assertEquals(600.0, stats.trackedSpanKm, 0.01)
        assertEquals(4, stats.fillCount)

        assertTrue(stats.avgLitersPer100Km.isFinite())
        assertTrue(stats.bestLitersPer100Km.isFinite())
        assertTrue(stats.avgKmPerLiter.isFinite())
    }

    @Test
    fun testComputeFuelEconomyStats_FallbackPartialFills() {
        // Less than 2 full tanks: all partial
        val logs = listOf(
            FuelLogEntity(fuelId = 1L, motorcycleId = "b1", dateIso = "2026-05-01", odometerKm = 1000.0, liters = 10.0, pricePerLiter = 1.8, totalCost = 18.0, isFullTank = false, notes = ""),
            FuelLogEntity(fuelId = 2L, motorcycleId = "b1", dateIso = "2026-05-06", odometerKm = 1200.0, liters = 10.0, pricePerLiter = 1.8, totalCost = 18.0, isFullTank = false, notes = ""),
            FuelLogEntity(fuelId = 3L, motorcycleId = "b1", dateIso = "2026-05-12", odometerKm = 1400.0, liters = 10.0, pricePerLiter = 1.8, totalCost = 18.0, isFullTank = false, notes = "")
        )

        val stats = RiderAnalyticsCalculator.computeFuelEconomyStats(logs)

        // Span: 1400 - 1000 = 400 km. Consumed: drop(1) liters = 20L -> (20 / 400) * 100 = 5.0 L/100km
        assertEquals(5.0, stats.avgLitersPer100Km, 0.01)
        assertEquals(5.0, stats.bestLitersPer100Km, 0.01)
        assertEquals(20.0, stats.avgKmPerLiter, 0.01)
        assertEquals(30.0, stats.totalLiters, 0.01)
        assertEquals(400.0, stats.trackedSpanKm, 0.01)
        assertEquals(3, stats.fillCount)

        assertTrue(stats.avgLitersPer100Km.isFinite())
        assertTrue(stats.bestLitersPer100Km.isFinite())
        assertTrue(stats.avgKmPerLiter.isFinite())
    }

    @Test
    fun testComputeFuelEconomyStats_EdgeCases() {
        // 1. Empty logs
        val emptyStats = RiderAnalyticsCalculator.computeFuelEconomyStats(emptyList())
        assertEquals(0.0, emptyStats.avgLitersPer100Km, 0.001)
        assertEquals(0.0, emptyStats.avgKmPerLiter, 0.001)
        assertEquals(0.0, emptyStats.bestLitersPer100Km, 0.001)
        assertEquals(0.0, emptyStats.totalLiters, 0.001)
        assertEquals(0, emptyStats.fillCount)

        // 2. Single log
        val singleLog = listOf(
            FuelLogEntity(fuelId = 1L, motorcycleId = "b1", dateIso = "2026-05-01", odometerKm = 1000.0, liters = 12.0, pricePerLiter = 1.8, totalCost = 21.6, isFullTank = true, notes = "")
        )
        val singleStats = RiderAnalyticsCalculator.computeFuelEconomyStats(singleLog)
        assertEquals(0.0, singleStats.avgLitersPer100Km, 0.001)
        assertEquals(0.0, singleStats.avgKmPerLiter, 0.001)
        assertEquals(12.0, singleStats.totalLiters, 0.001)
        assertEquals(1, singleStats.fillCount)

        // 3. Same odometer reading (zero distance)
        val zeroDistLogs = listOf(
            FuelLogEntity(fuelId = 1L, motorcycleId = "b1", dateIso = "2026-05-01", odometerKm = 1000.0, liters = 12.0, pricePerLiter = 1.8, totalCost = 21.6, isFullTank = true, notes = ""),
            FuelLogEntity(fuelId = 2L, motorcycleId = "b1", dateIso = "2026-05-02", odometerKm = 1000.0, liters = 5.0, pricePerLiter = 1.8, totalCost = 9.0, isFullTank = true, notes = "")
        )
        val zeroDistStats = RiderAnalyticsCalculator.computeFuelEconomyStats(zeroDistLogs)
        assertEquals(0.0, zeroDistStats.avgLitersPer100Km, 0.001)
        assertEquals(0.0, zeroDistStats.avgKmPerLiter, 0.001)
        assertTrue(zeroDistStats.avgLitersPer100Km.isFinite())
    }

    // =========================================================================
    // 4. TCO Calculation Tests
    // =========================================================================

    @Test
    fun testComputeTcoStats() {
        val bike = MotorcycleEntity(
            id = "bike_1",
            make = "Yamaha",
            model = "MT-09",
            year = 2024,
            engineDisplacementCc = 890,
            baseOdometerKm = 10000.0, // Prior owner mileage
            accumulatedTrackedKm = 5000.0,
            manualAdjustmentOffsetKm = 0.0,
            technicalSpecsJson = "{}",
            buyingPrice = 8500.0,
            currencySymbol = "€",
            startDateIso = "2026-01-01"
        )
        // Total odo = 10000 + 5000 = 15000 km.
        // Owner-ridden distance = 15000 - 10000 = 5000 km.

        val fuelLogs = listOf(
            FuelLogEntity(fuelId = 1L, motorcycleId = "bike_1", dateIso = "2026-02-01", odometerKm = 11000.0, liters = 50.0, pricePerLiter = 1.8, totalCost = 300.0, isFullTank = true, notes = ""),
            FuelLogEntity(fuelId = 2L, motorcycleId = "bike_1", dateIso = "2026-03-01", odometerKm = 12500.0, liters = 55.0, pricePerLiter = 1.8, totalCost = 200.0, isFullTank = true, notes = "")
        ) // fuel total = 500.0

        val serviceLogs = listOf(
            ServiceLogEntity(logId = "s1", motorcycleId = "bike_1", serviceTitle = "Oil & Filter", performedAtIsoDate = "2026-02-15", odometerAtServiceKm = 11500.0, costEur = 150.0)
        ) // service total = 150.0

        val partLogs = listOf(
            PartLogEntity(partId = 1L, motorcycleId = "bike_1", dateIso = "2026-03-10", odometerKm = 12800.0, partName = "Tire", brand = "Michelin", partNumber = "PR5", cost = 150.0, notes = "")
        ) // parts total = 150.0

        val rides = emptyList<RideSessionEntity>()

        // NowMs set to 2026-05-01 (approx 4 months since 2026-01-01)
        val nowMs = 1777593600000L // 2026-05-01T00:00:00Z
        val stats = RiderAnalyticsCalculator.computeTcoStats(bike, fuelLogs, serviceLogs, partLogs, rides, nowMs)

        assertEquals(8500.0, stats.purchasePrice, 0.01)
        assertEquals(500.0, stats.totalFuelCost, 0.01)
        assertEquals(500.0, stats.fuelCost, 0.01)
        assertEquals(150.0, stats.totalServiceCost, 0.01)
        assertEquals(150.0, stats.serviceCost, 0.01)
        assertEquals(150.0, stats.totalPartsCost, 0.01)
        assertEquals(150.0, stats.partsCost, 0.01)

        val expectedOperating = 500.0 + 150.0 + 150.0 // 800.0
        assertEquals(expectedOperating, stats.totalOperatingCost, 0.01)
        assertEquals(8500.0 + expectedOperating, stats.totalCost, 0.01)
        assertEquals(8500.0 + expectedOperating, stats.totalInvestment, 0.01)

        // Owner ridden distance
        assertEquals(5000.0, stats.ownedDistanceKm, 0.01)
        // Cost per km = 800.0 / 5000.0 = 0.16 €/km
        assertEquals(800.0 / 5000.0, stats.costPerKm, 0.001)

        // Cost per month: ~800 / ~4 months > 0
        assertTrue(stats.costPerMonth > 0.0)
        assertTrue(stats.costPerMonth.isFinite())
    }

    @Test
    fun testComputeTcoStats_ZeroDistanceAndFallbacks() {
        val bike = MotorcycleEntity(
            id = "bike_2",
            make = "Honda",
            model = "CB650R",
            year = 2023,
            engineDisplacementCc = 649,
            baseOdometerKm = 0.0,
            accumulatedTrackedKm = 0.0,
            manualAdjustmentOffsetKm = 0.0,
            technicalSpecsJson = "{}",
            buyingPrice = 7000.0
        )

        // When bike has 0 km, fallback to ride sum
        val rides = listOf(
            RideSessionEntity(rideId = 1L, motorcycleId = "bike_2", startTimeEpochMs = 1000L, endTimeEpochMs = 2000L, distanceKm = 120.0, avgSpeedKmh = 60.0, maxSpeedKmh = 100.0, maxLeanLeftDeg = 30.0, maxLeanRightDeg = 30.0, maxLateralG = 0.8, maxBrakingG = 0.6, maxAccelG = 0.4, totalCornersCount = 10, leftCornersCount = 5, rightCornersCount = 5, maxTurnInRateDegPerSec = 40.0, trailBrakingScorePct = 70.0, avgCornerScore = 80.0, elevationGainMeters = 200.0, ridingStyleBadge = "TOURER")
        )

        val fuel = listOf(
            FuelLogEntity(fuelId = 1L, motorcycleId = "bike_2", dateIso = "2026-01-01", odometerKm = 0.0, liters = 10.0, pricePerLiter = 1.5, totalCost = 15.0, isFullTank = true, notes = "")
        )

        val stats = RiderAnalyticsCalculator.computeTcoStats(bike, fuel, emptyList(), emptyList(), rides)

        assertEquals(120.0, stats.ownedDistanceKm, 0.01)
        assertEquals(15.0 / 120.0, stats.costPerKm, 0.001)
        assertTrue(stats.costPerKm.isFinite())
    }

    // =========================================================================
    // 5. Rider Analytics Calculation Tests
    // =========================================================================

    @Test
    fun testComputeRiderAnalytics() {
        val rides = listOf(
            RideSessionEntity(
                rideId = 1L,
                motorcycleId = "b1",
                startTimeEpochMs = 1715000000000L,
                endTimeEpochMs = 1715003600000L, // 3600 sec = 1 hour
                distanceKm = 60.0,
                avgSpeedKmh = 60.0,
                maxSpeedKmh = 110.0,
                maxLeanLeftDeg = 44.0,
                maxLeanRightDeg = 42.0,
                maxLateralG = 0.95,
                maxBrakingG = 0.85,
                maxAccelG = 0.50,
                totalCornersCount = 30,
                leftCornersCount = 14,
                rightCornersCount = 16,
                maxTurnInRateDegPerSec = 62.0,
                trailBrakingScorePct = 75.0,
                avgCornerScore = 84.0,
                elevationGainMeters = 450.0,
                ridingStyleBadge = "TECHNICIAN"
            ),
            RideSessionEntity(
                rideId = 2L,
                motorcycleId = "b1",
                startTimeEpochMs = 1715100000000L,
                endTimeEpochMs = 1715107200000L, // 7200 sec = 2 hours
                distanceKm = 140.0,
                avgSpeedKmh = 70.0,
                maxSpeedKmh = 135.0,
                maxLeanLeftDeg = 47.5,
                maxLeanRightDeg = 49.0, // Personal best lean!
                maxLateralG = 1.18,      // Personal best lateral!
                maxBrakingG = 0.98,      // Personal best braking!
                maxAccelG = 0.65,        // Personal best accel!
                totalCornersCount = 70,
                leftCornersCount = 36,
                rightCornersCount = 34,
                maxTurnInRateDegPerSec = 78.5, // Personal best turn-in!
                trailBrakingScorePct = 85.0,
                avgCornerScore = 90.0,
                elevationGainMeters = 950.0,
                ridingStyleBadge = "RACER"
            )
        )

        val analytics = RiderAnalyticsCalculator.computeRiderAnalytics(rides)

        assertEquals(2, analytics.totalRides)
        assertEquals(200.0, analytics.totalDistanceKm, 0.01)
        assertEquals(3600000L + 7200000L, analytics.totalDurationMs)
        assertEquals(1400.0, analytics.totalElevationGainM, 0.01)
        assertEquals(135.0, analytics.maxSpeedKmh, 0.01)

        // Weighted avg speed: (60km * 60km/h + 140km * 70km/h) / 200km = (3600 + 9800) / 200 = 67.0 km/h
        assertEquals(67.0, analytics.avgSpeedWeightedKmh, 0.01)

        assertEquals(47.5, analytics.peakLeanLeftDeg, 0.01)
        assertEquals(49.0, analytics.peakLeanRightDeg, 0.01)
        assertEquals(100, analytics.totalCorners)
        assertEquals(50, analytics.totalLeftCorners)
        assertEquals(50, analytics.totalRightCorners)

        // Scores
        assertEquals(87.0, analytics.avgCornerScore, 0.01) // (84 + 90) / 2
        assertEquals(80.0, analytics.avgTrailBrakingPct, 0.01) // (75 + 85) / 2

        // Peak G forces & rates
        assertEquals(1.18, analytics.peakLateralG, 0.01)
        assertEquals(0.98, analytics.peakBrakingG, 0.01)
        assertEquals(0.65, analytics.peakAccelG, 0.01)
        assertEquals(78.5, analytics.peakTurnInRateDegPerSec, 0.01)

        // Top rides / personal bests
        assertNotNull(analytics.topLeanRide)
        assertEquals(2L, analytics.topLeanRide!!.rideId)

        assertNotNull(analytics.fastestRide)
        assertEquals(2L, analytics.fastestRide!!.rideId)

        assertNotNull(analytics.longestRide)
        assertEquals(2L, analytics.longestRide!!.rideId)
    }

    @Test
    fun testComputeRiderAnalytics_EmptyAndMalformed() {
        // 1. Empty list
        val emptyAnalytics = RiderAnalyticsCalculator.computeRiderAnalytics(emptyList())
        assertEquals(0, emptyAnalytics.totalRides)
        assertEquals(0.0, emptyAnalytics.totalDistanceKm, 0.001)
        assertEquals(0.0, emptyAnalytics.avgSpeedWeightedKmh, 0.001)
        assertNull(emptyAnalytics.topLeanRide)
        assertNull(emptyAnalytics.fastestRide)
        assertNull(emptyAnalytics.longestRide)

        // 2. Rides with NaN or negative values
        val malformedRides = listOf(
            RideSessionEntity(
                rideId = 1L,
                motorcycleId = "b1",
                startTimeEpochMs = 1000L,
                endTimeEpochMs = 2000L,
                distanceKm = Double.NaN,
                avgSpeedKmh = -10.0,
                maxSpeedKmh = Double.POSITIVE_INFINITY,
                maxLeanLeftDeg = Double.NaN,
                maxLeanRightDeg = -5.0,
                maxLateralG = Double.NaN,
                maxBrakingG = -1.0,
                maxAccelG = Double.NaN,
                totalCornersCount = -1,
                leftCornersCount = 0,
                rightCornersCount = 0,
                maxTurnInRateDegPerSec = Double.NaN,
                trailBrakingScorePct = Double.NaN,
                avgCornerScore = Double.NaN,
                elevationGainMeters = Double.NaN,
                ridingStyleBadge = "UNKNOWN"
            )
        )

        val clean = RiderAnalyticsCalculator.computeRiderAnalytics(malformedRides)
        assertTrue(clean.totalDistanceKm.isFinite())
        assertEquals(0.0, clean.totalDistanceKm, 0.001)
        assertTrue(clean.avgSpeedWeightedKmh.isFinite())
        assertEquals(0.0, clean.avgSpeedWeightedKmh, 0.001)
        assertTrue(clean.maxSpeedKmh.isFinite())
        assertEquals(0.0, clean.maxSpeedKmh, 0.001)
        assertTrue(clean.peakLeanLeftDeg.isFinite())
        assertEquals(0.0, clean.peakLeanLeftDeg, 0.001)
        assertTrue(clean.peakLeanRightDeg.isFinite())
        assertEquals(0.0, clean.peakLeanRightDeg, 0.001)
    }
}

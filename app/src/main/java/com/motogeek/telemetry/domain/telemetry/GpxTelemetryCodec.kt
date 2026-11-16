package com.motogeek.telemetry.domain.telemetry

import com.motogeek.telemetry.data.local.CornerEventEntity
import com.motogeek.telemetry.data.local.RideSessionEntity
import com.motogeek.telemetry.data.local.TelemetryTrackPointEntity
import org.w3c.dom.Document
import org.w3c.dom.Element
import org.w3c.dom.Node
import java.io.ByteArrayInputStream
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZonedDateTime
import java.util.Locale
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.math.*

object GpxTelemetryCodec {

    data class ParsedGpxImport(
        val ride: RideSessionEntity,
        val trackPoints: List<TelemetryTrackPointEntity>,
        val cornerEvents: List<CornerEventEntity>,
        val hasEmbeddedImuTelemetry: Boolean
    )

    fun isValidCoordinate(lat: Double, lon: Double): Boolean {
        if (!lat.isFinite() || !lon.isFinite()) return false
        if (lat < -90.0 || lat > 90.0) return false
        if (lon < -180.0 || lon > 180.0) return false
        if (abs(lat) < 0.0001 && abs(lon) < 0.0001) return false
        return true
    }

    fun filterValidTrackPoints(points: List<TelemetryTrackPointEntity>): List<TelemetryTrackPointEntity> {
        return points.filter { isValidCoordinate(it.latitude, it.longitude) }
    }

    fun xmlEscape(value: String): String {
        return value
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&apos;")
    }

    fun encodeRideToGpx(
        ride: RideSessionEntity,
        trackPoints: List<TelemetryTrackPointEntity>,
        cornerEvents: List<CornerEventEntity> = emptyList(),
        bikeName: String? = null
    ): String {
        val validPoints = filterValidTrackPoints(trackPoints)
        val sb = StringBuilder()
        sb.append("""<?xml version="1.0" encoding="UTF-8"?>""").append("\n")
        sb.append("""<gpx version="1.1" creator="KurveCounter MotoGeek Telemetry"""")
        sb.append(""" xmlns="http://www.topografix.com/GPX/1/1"""")
        sb.append(""" xmlns:gpxtpx="http://www.garmin.com/xmlschemas/TrackPointExtension/v2"""")
        sb.append(""" xmlns:kurve="https://kurvecounter.app/xmlschemas/TelemetryExtension/v1"""")
        sb.append(""" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"""")
        sb.append(""" xsi:schemaLocation="http://www.topografix.com/GPX/1/1 http://www.topografix.com/GPX/1/1/gpx.xsd http://www.garmin.com/xmlschemas/TrackPointExtension/v2 http://www.garmin.com/xmlschemas/TrackPointExtensionv2.xsd">""").append("\n")

        val rideName = xmlEscape(ride.name ?: "Ride #${ride.rideId}")
        val metaTime = try {
            Instant.ofEpochMilli(ride.startTimeEpochMs).toString()
        } catch (_: Exception) {
            Instant.now().toString()
        }

        sb.append("  <metadata>\n")
        sb.append("    <name>").append(rideName).append("</name>\n")
        sb.append("    <time>").append(metaTime).append("</time>\n")
        if (!bikeName.isNullOrBlank()) {
            sb.append("    <desc>Motorcycle: ").append(xmlEscape(bikeName)).append("</desc>\n")
        }
        sb.append("  </metadata>\n")

        // Waypoint pins for corner events
        for (corner in cornerEvents) {
            val coords = if (isValidCoordinate(corner.latitude, corner.longitude)) {
                Pair(corner.latitude, corner.longitude)
            } else {
                val nearest = validPoints.minByOrNull { abs(it.timestampMs - corner.timestampMs) }
                if (nearest != null && isValidCoordinate(nearest.latitude, nearest.longitude)) {
                    Pair(nearest.latitude, nearest.longitude)
                } else null
            } ?: continue

            val cornerIsoTime = try {
                Instant.ofEpochMilli(corner.timestampMs).toString()
            } catch (_: Exception) {
                Instant.now().toString()
            }

            sb.append("  <wpt lat=\"").append(String.format(Locale.US, "%.6f", coords.first))
                .append("\" lon=\"").append(String.format(Locale.US, "%.6f", coords.second)).append("\">\n")
            sb.append("    <time>").append(cornerIsoTime).append("</time>\n")
            sb.append("    <name>").append(xmlEscape("${corner.direction} Turn #${corner.sequenceNumber}")).append("</name>\n")
            val desc = String.format(
                Locale.US,
                "Lean: %.1f°, Speed: %.1f km/h, Score: %.0f",
                corner.apexLeanDeg,
                corner.entrySpeedKmh,
                corner.cornerScore
            )
            sb.append("    <desc>").append(xmlEscape(desc)).append("</desc>\n")
            sb.append("    <sym>Flag</sym>\n")
            sb.append("  </wpt>\n")
        }

        sb.append("  <trk>\n")
        sb.append("    <name>").append(rideName).append("</name>\n")
        sb.append("    <trkseg>\n")

        for (pt in validPoints) {
            val isoTime = try {
                Instant.ofEpochMilli(pt.timestampMs).toString()
            } catch (_: Exception) {
                Instant.now().toString()
            }
            val speedMs = (pt.speedKmh / 3.6).takeIf { it.isFinite() } ?: 0.0
            val leanAngle = pt.leanAngleDeg.takeIf { it.isFinite() } ?: 0.0
            val longG = pt.longitudinalG.takeIf { it.isFinite() } ?: 0.0
            val latG = pt.lateralG.takeIf { it.isFinite() } ?: 0.0
            val speedKmh = pt.speedKmh.takeIf { it.isFinite() } ?: 0.0
            val throttleProxy = (longG.coerceAtLeast(0.0)).takeIf { it.isFinite() } ?: 0.0
            val ele = pt.altitudeMeters.takeIf { it.isFinite() } ?: 0.0

            sb.append("      <trkpt lat=\"").append(String.format(Locale.US, "%.6f", pt.latitude))
                .append("\" lon=\"").append(String.format(Locale.US, "%.6f", pt.longitude)).append("\">\n")
            sb.append("        <ele>").append(String.format(Locale.US, "%.2f", ele)).append("</ele>\n")
            sb.append("        <time>").append(isoTime).append("</time>\n")
            sb.append("        <extensions>\n")
            sb.append("          <gpxtpx:TrackPointExtension>\n")
            sb.append("            <gpxtpx:speed>").append(String.format(Locale.US, "%.2f", speedMs)).append("</gpxtpx:speed>\n")
            sb.append("          </gpxtpx:TrackPointExtension>\n")
            sb.append("          <kurve:TelemetryExtension>\n")
            sb.append("            <kurve:leanAngle>").append(String.format(Locale.US, "%.2f", leanAngle)).append("</kurve:leanAngle>\n")
            sb.append("            <kurve:longitudinalG>").append(String.format(Locale.US, "%.3f", longG)).append("</kurve:longitudinalG>\n")
            sb.append("            <kurve:lateralG>").append(String.format(Locale.US, "%.3f", latG)).append("</kurve:lateralG>\n")
            sb.append("            <kurve:speedKmh>").append(String.format(Locale.US, "%.2f", speedKmh)).append("</kurve:speedKmh>\n")
            sb.append("            <kurve:throttleProxy>").append(String.format(Locale.US, "%.3f", throttleProxy)).append("</kurve:throttleProxy>\n")
            sb.append("          </kurve:TelemetryExtension>\n")
            sb.append("        </extensions>\n")
            sb.append("      </trkpt>\n")
        }

        sb.append("    </trkseg>\n")
        sb.append("  </trk>\n")
        sb.append("</gpx>\n")

        return sb.toString()
    }

    fun parseGpxToRide(gpxXml: String, motorcycleId: String, fallbackName: String? = null): ParsedGpxImport {
        if (gpxXml.isBlank()) {
            throw IllegalArgumentException("GPX content is empty")
        }

        val factory = DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = false
            try {
                setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
                setFeature("http://xml.org/sax/features/external-general-entities", false)
                setFeature("http://xml.org/sax/features/external-parameter-entities", false)
            } catch (_: Exception) {}
        }
        val builder = factory.newDocumentBuilder()
        val doc = builder.parse(ByteArrayInputStream(gpxXml.toByteArray(Charsets.UTF_8)))
        doc.documentElement.normalize()

        val parsedTrackName = findElementValue(doc.documentElement, "name")
        val rideName = parsedTrackName?.ifBlank { null } ?: fallbackName ?: "GPX Ride"

        val rawTrkpts = getAllElements(doc, "trkpt")
        if (rawTrkpts.isEmpty()) {
            throw IllegalArgumentException("No track points found in GPX")
        }

        var detectedImuTelemetry = false
        data class RawPoint(
            val lat: Double,
            val lon: Double,
            val ele: Double,
            val timestampMs: Long?,
            val explicitSpeedKmh: Double?,
            val explicitLeanAngle: Double?,
            val explicitLongG: Double?,
            val explicitLatG: Double?
        )

        val rawPoints = mutableListOf<RawPoint>()
        for (node in rawTrkpts) {
            val latStr = node.getAttribute("lat")
            val lonStr = node.getAttribute("lon")
            val lat = latStr.toDoubleOrNull() ?: continue
            val lon = lonStr.toDoubleOrNull() ?: continue
            if (!isValidCoordinate(lat, lon)) continue

            val eleStr = findElementValue(node, "ele")
            val ele = eleStr?.toDoubleOrNull()?.takeIf { it.isFinite() } ?: 0.0

            val timeStr = findElementValue(node, "time")
            val timestamp = parseIsoTimeToEpochMs(timeStr)

            val speedKmhStr = findElementValue(node, "speedKmh")
            val speedMsStr = findElementValue(node, "speed")
            val explicitSpeedKmh = speedKmhStr?.toDoubleOrNull()?.takeIf { it.isFinite() }
                ?: speedMsStr?.toDoubleOrNull()?.takeIf { it.isFinite() }?.let { it * 3.6 }

            val leanStr = findElementValue(node, "leanAngle") ?: findElementValue(node, "leanDeg")
            val explicitLean = leanStr?.toDoubleOrNull()?.takeIf { it.isFinite() }

            val longGStr = findElementValue(node, "longitudinalG")
            val explicitLongG = longGStr?.toDoubleOrNull()?.takeIf { it.isFinite() }

            val latGStr = findElementValue(node, "lateralG")
            val explicitLatG = latGStr?.toDoubleOrNull()?.takeIf { it.isFinite() }

            if (explicitLean != null || explicitLatG != null || explicitLongG != null) {
                detectedImuTelemetry = true
            }

            rawPoints.add(
                RawPoint(
                    lat = lat,
                    lon = lon,
                    ele = ele,
                    timestampMs = timestamp,
                    explicitSpeedKmh = explicitSpeedKmh,
                    explicitLeanAngle = explicitLean,
                    explicitLongG = explicitLongG,
                    explicitLatG = explicitLatG
                )
            )
        }

        if (rawPoints.isEmpty()) {
            throw IllegalArgumentException("No valid GPS coordinates found in GPX")
        }

        // Fill missing timestamps monotonically
        var currentSyntheticTime = rawPoints.firstOrNull { it.timestampMs != null }?.timestampMs
            ?: parseIsoTimeToEpochMs(findElementValue(doc.documentElement, "time"))
            ?: System.currentTimeMillis()

        val pointsWithTime = mutableListOf<RawPoint>()
        for (pt in rawPoints) {
            val resolvedTime = if (pt.timestampMs != null) {
                if (pointsWithTime.isNotEmpty() && pt.timestampMs <= pointsWithTime.last().timestampMs!!) {
                    pointsWithTime.last().timestampMs!! + 100L
                } else {
                    pt.timestampMs
                }
            } else {
                currentSyntheticTime += 1000L
                currentSyntheticTime
            }
            currentSyntheticTime = resolvedTime
            pointsWithTime.add(pt.copy(timestampMs = resolvedTime))
        }

        // Compute speed if missing via Haversine distance / dt
        val speedsKmh = DoubleArray(pointsWithTime.size)
        for (i in pointsWithTime.indices) {
            val explicit = pointsWithTime[i].explicitSpeedKmh
            if (explicit != null && explicit >= 0.0) {
                speedsKmh[i] = explicit.coerceIn(0.0, 350.0)
            } else if (i > 0) {
                val prev = pointsWithTime[i - 1]
                val curr = pointsWithTime[i]
                val distM = haversineDistanceMeters(prev.lat, prev.lon, curr.lat, curr.lon)
                val dtSec = ((curr.timestampMs!! - prev.timestampMs!!) / 1000.0).coerceAtLeast(0.05)
                val speedMs = distM / dtSec
                speedsKmh[i] = (speedMs * 3.6).coerceIn(0.0, 350.0)
            } else if (pointsWithTime.size > 1) {
                val next = pointsWithTime[1]
                val curr = pointsWithTime[0]
                val distM = haversineDistanceMeters(curr.lat, curr.lon, next.lat, next.lon)
                val dtSec = ((next.timestampMs!! - curr.timestampMs!!) / 1000.0).coerceAtLeast(0.05)
                val speedMs = distM / dtSec
                speedsKmh[i] = (speedMs * 3.6).coerceIn(0.0, 350.0)
            } else {
                speedsKmh[i] = 0.0
            }
        }

        // Synthetic IMU calculation if embedded IMU is absent
        val rawLeanAngles = DoubleArray(pointsWithTime.size)
        val rawLateralGs = DoubleArray(pointsWithTime.size)
        val rawLongitudinalGs = DoubleArray(pointsWithTime.size)

        if (detectedImuTelemetry) {
            for (i in pointsWithTime.indices) {
                rawLeanAngles[i] = pointsWithTime[i].explicitLeanAngle?.coerceIn(-70.0, 70.0) ?: 0.0
                rawLateralGs[i] = pointsWithTime[i].explicitLatG?.coerceIn(-3.0, 3.0) ?: 0.0
                rawLongitudinalGs[i] = pointsWithTime[i].explicitLongG?.coerceIn(-2.0, 2.0) ?: 0.0
            }
        } else {
            for (i in pointsWithTime.indices) {
                val speedKmh = speedsKmh[i]
                val speedMs = speedKmh / 3.6

                // Longitudinal G from speed derivative
                if (i > 0 && i < pointsWithTime.size - 1) {
                    val dt = ((pointsWithTime[i + 1].timestampMs!! - pointsWithTime[i - 1].timestampMs!!) / 1000.0).coerceAtLeast(0.1)
                    val dv = (speedsKmh[i + 1] - speedsKmh[i - 1]) / 3.6
                    rawLongitudinalGs[i] = (dv / dt / 9.80665).coerceIn(-1.5, 1.5)
                } else if (i > 0) {
                    val dt = ((pointsWithTime[i].timestampMs!! - pointsWithTime[i - 1].timestampMs!!) / 1000.0).coerceAtLeast(0.05)
                    val dv = (speedsKmh[i] - speedsKmh[i - 1]) / 3.6
                    rawLongitudinalGs[i] = (dv / dt / 9.80665).coerceIn(-1.5, 1.5)
                }

                // Lateral G and Lean from bearing change
                if (speedKmh > 5.0 && i > 0 && i < pointsWithTime.size - 1) {
                    val pPrev = pointsWithTime[i - 1]
                    val pCurr = pointsWithTime[i]
                    val pNext = pointsWithTime[i + 1]

                    val b1 = initialBearing(pPrev.lat, pPrev.lon, pCurr.lat, pCurr.lon)
                    val b2 = initialBearing(pCurr.lat, pCurr.lon, pNext.lat, pNext.lon)
                    val deltaThetaDeg = angleDifference(b2, b1)
                    val dtSec = ((pNext.timestampMs!! - pPrev.timestampMs!!) / 1000.0).coerceIn(0.1, 10.0)

                    val yawRateRadSec = Math.toRadians(deltaThetaDeg) / dtSec
                    val latG = (speedMs * yawRateRadSec / 9.80665).coerceIn(-2.5, 2.5)
                    rawLateralGs[i] = latG

                    val leanDeg = Math.toDegrees(atan2(latG, 1.0)).coerceIn(-65.0, 65.0)
                    rawLeanAngles[i] = leanDeg
                } else {
                    rawLateralGs[i] = 0.0
                    rawLeanAngles[i] = 0.0
                }
            }

            // Low-pass filter synthetic lean angles to eliminate GPS jitter
            val smoothed = DoubleArray(rawLeanAngles.size)
            for (i in rawLeanAngles.indices) {
                var sum = 0.0
                var count = 0
                for (k in -2..2) {
                    val idx = i + k
                    if (idx in rawLeanAngles.indices) {
                        sum += rawLeanAngles[idx]
                        count++
                    }
                }
                smoothed[i] = if (count > 0) sum / count else rawLeanAngles[i]
            }
            for (i in rawLeanAngles.indices) {
                rawLeanAngles[i] = smoothed[i]
            }
        }

        // Build TelemetryTrackPointEntity list
        val finalTrackPoints = ArrayList<TelemetryTrackPointEntity>(pointsWithTime.size)
        for (i in pointsWithTime.indices) {
            val p = pointsWithTime[i]
            finalTrackPoints.add(
                TelemetryTrackPointEntity(
                    pointId = 0L,
                    rideId = 0L,
                    timestampMs = p.timestampMs!!,
                    latitude = p.lat,
                    longitude = p.lon,
                    speedKmh = speedsKmh[i].takeIf { it.isFinite() } ?: 0.0,
                    leanAngleDeg = rawLeanAngles[i].takeIf { it.isFinite() } ?: 0.0,
                    longitudinalG = rawLongitudinalGs[i].takeIf { it.isFinite() } ?: 0.0,
                    lateralG = rawLateralGs[i].takeIf { it.isFinite() } ?: 0.0,
                    altitudeMeters = p.ele.takeIf { it.isFinite() } ?: 0.0
                )
            )
        }

        // Peak corner detection
        val detectedCorners = mutableListOf<CornerEventEntity>()
        var cornerState = 0 // 0 = STRAIGHT, 1 = CANDIDATE, 2 = CONFIRMED
        var cornerStartTime = 0L
        var cornerMaxLean = 0.0
        var cornerEntrySpeed = 0.0
        var cornerMaxBrakingG = 0.0
        var cornerMaxLateralG = 0.0
        var cornerExitAccelG = 0.0
        var cornerMaxRollRate = 0.0
        var cornerApexLat = 0.0
        var cornerApexLon = 0.0
        var cornerApexTime = 0L

        for (i in finalTrackPoints.indices) {
            val pt = finalTrackPoints[i]
            val absLean = abs(pt.leanAngleDeg)
            val prevPt = if (i > 0) finalTrackPoints[i - 1] else pt
            val dtSec = ((pt.timestampMs - prevPt.timestampMs) / 1000.0).coerceAtLeast(0.05)
            val rollRate = abs(pt.leanAngleDeg - prevPt.leanAngleDeg) / dtSec

            when (cornerState) {
                0 -> { // STRAIGHT
                    if (absLean >= 10.0) {
                        cornerState = 1 // CANDIDATE
                        cornerStartTime = pt.timestampMs
                        cornerMaxLean = pt.leanAngleDeg
                        cornerEntrySpeed = pt.speedKmh
                        cornerMaxBrakingG = min(pt.longitudinalG, 0.0)
                        cornerMaxLateralG = pt.lateralG
                        cornerExitAccelG = max(pt.longitudinalG, 0.0)
                        cornerMaxRollRate = rollRate
                        cornerApexLat = pt.latitude
                        cornerApexLon = pt.longitude
                        cornerApexTime = pt.timestampMs
                    }
                }
                1 -> { // CANDIDATE
                    if (absLean > abs(cornerMaxLean)) {
                        cornerMaxLean = pt.leanAngleDeg
                        cornerApexLat = pt.latitude
                        cornerApexLon = pt.longitude
                        cornerApexTime = pt.timestampMs
                    }
                    cornerMaxBrakingG = min(cornerMaxBrakingG, pt.longitudinalG)
                    cornerMaxLateralG = if (abs(pt.lateralG) > abs(cornerMaxLateralG)) pt.lateralG else cornerMaxLateralG
                    cornerExitAccelG = max(cornerExitAccelG, pt.longitudinalG)
                    cornerMaxRollRate = max(cornerMaxRollRate, rollRate)

                    if (absLean < 6.0) {
                        cornerState = 0
                    } else if (pt.timestampMs - cornerStartTime >= 1000L) {
                        cornerState = 2 // CONFIRMED
                    }
                }
                2 -> { // CONFIRMED
                    if (absLean > abs(cornerMaxLean)) {
                        cornerMaxLean = pt.leanAngleDeg
                        cornerApexLat = pt.latitude
                        cornerApexLon = pt.longitude
                        cornerApexTime = pt.timestampMs
                    }
                    cornerMaxBrakingG = min(cornerMaxBrakingG, pt.longitudinalG)
                    cornerMaxLateralG = if (abs(pt.lateralG) > abs(cornerMaxLateralG)) pt.lateralG else cornerMaxLateralG
                    cornerExitAccelG = max(cornerExitAccelG, pt.longitudinalG)
                    cornerMaxRollRate = max(cornerMaxRollRate, rollRate)

                    if (absLean < 6.0) {
                        val direction = if (cornerMaxLean > 0) "RIGHT" else "LEFT"
                        val score = min(100.0, max(0.0, (abs(cornerMaxLean) / 50.0) * 100.0))
                        val corner = CornerEventEntity(
                            rideId = 0L,
                            sequenceNumber = detectedCorners.size + 1,
                            timestampMs = cornerApexTime,
                            direction = direction,
                            entrySpeedKmh = cornerEntrySpeed.takeIf { it.isFinite() } ?: 0.0,
                            apexLeanDeg = abs(cornerMaxLean).takeIf { it.isFinite() } ?: 0.0,
                            peakLateralG = abs(cornerMaxLateralG).takeIf { it.isFinite() } ?: 0.0,
                            peakBrakingG = cornerMaxBrakingG.takeIf { it.isFinite() } ?: 0.0,
                            exitAccelG = cornerExitAccelG.takeIf { it.isFinite() } ?: 0.0,
                            maxRollRateDegPerSec = cornerMaxRollRate.takeIf { it.isFinite() } ?: 0.0,
                            cornerScore = score.takeIf { it.isFinite() } ?: 0.0,
                            latitude = cornerApexLat,
                            longitude = cornerApexLon
                        )
                        detectedCorners.add(corner)
                        cornerState = 0
                    }
                }
            }
        }

        if (cornerState == 2) {
            val direction = if (cornerMaxLean > 0) "RIGHT" else "LEFT"
            val score = min(100.0, max(0.0, (abs(cornerMaxLean) / 50.0) * 100.0))
            val corner = CornerEventEntity(
                rideId = 0L,
                sequenceNumber = detectedCorners.size + 1,
                timestampMs = cornerApexTime,
                direction = direction,
                entrySpeedKmh = cornerEntrySpeed.takeIf { it.isFinite() } ?: 0.0,
                apexLeanDeg = abs(cornerMaxLean).takeIf { it.isFinite() } ?: 0.0,
                peakLateralG = abs(cornerMaxLateralG).takeIf { it.isFinite() } ?: 0.0,
                peakBrakingG = cornerMaxBrakingG.takeIf { it.isFinite() } ?: 0.0,
                exitAccelG = cornerExitAccelG.takeIf { it.isFinite() } ?: 0.0,
                maxRollRateDegPerSec = cornerMaxRollRate.takeIf { it.isFinite() } ?: 0.0,
                cornerScore = score.takeIf { it.isFinite() } ?: 0.0,
                latitude = cornerApexLat,
                longitude = cornerApexLon
            )
            detectedCorners.add(corner)
        }

        val wptElements = getAllElements(doc, "wpt")
        val wptCorners = mutableListOf<CornerEventEntity>()
        for (wptNode in wptElements) {
            val name = findElementValue(wptNode, "name") ?: ""
            val desc = findElementValue(wptNode, "desc") ?: ""

            if (!name.contains("Turn", ignoreCase = true) && !desc.contains("Lean:", ignoreCase = true)) {
                continue
            }

            val latRaw = wptNode.getAttribute("lat").toDoubleOrNull() ?: 0.0
            val lonRaw = wptNode.getAttribute("lon").toDoubleOrNull() ?: 0.0
            val timeStr = findElementValue(wptNode, "time")
            val timeMs = parseIsoTimeToEpochMs(timeStr) ?: finalTrackPoints.first().timestampMs

            val nearestPt = finalTrackPoints.minByOrNull { abs(it.timestampMs - timeMs) } ?: finalTrackPoints.first()

            val (finalLat, finalLon) = if (isValidCoordinate(latRaw, lonRaw)) {
                Pair(latRaw, lonRaw)
            } else {
                Pair(nearestPt.latitude, nearestPt.longitude)
            }

            val seqMatch = Regex("""#(\d+)""").find(name) ?: Regex("""#(\d+)""").find(desc)
            val seq = seqMatch?.groupValues?.get(1)?.toIntOrNull() ?: (wptCorners.size + 1)

            val direction = when {
                name.contains("LEFT", ignoreCase = true) || desc.contains("LEFT", ignoreCase = true) -> "LEFT"
                name.contains("RIGHT", ignoreCase = true) || desc.contains("RIGHT", ignoreCase = true) -> "RIGHT"
                nearestPt.leanAngleDeg < 0.0 -> "LEFT"
                else -> "RIGHT"
            }

            val leanMatch = Regex("""Lean:\s*([+-]?\d+(?:[.,]\d+)?)""", RegexOption.IGNORE_CASE).find(desc)
                ?: Regex("""Lean:\s*([+-]?\d+(?:[.,]\d+)?)""", RegexOption.IGNORE_CASE).find(name)
            val apexLean = leanMatch?.groupValues?.get(1)?.replace(',', '.')?.toDoubleOrNull()
                ?.let { abs(it) } ?: abs(nearestPt.leanAngleDeg)

            val speedMatch = Regex("""Speed:\s*([+-]?\d+(?:[.,]\d+)?)""", RegexOption.IGNORE_CASE).find(desc)
                ?: Regex("""Speed:\s*([+-]?\d+(?:[.,]\d+)?)""", RegexOption.IGNORE_CASE).find(name)
            val entrySpeed = speedMatch?.groupValues?.get(1)?.replace(',', '.')?.toDoubleOrNull()
                ?: nearestPt.speedKmh

            val scoreMatch = Regex("""Score:\s*([+-]?\d+(?:[.,]\d+)?)""", RegexOption.IGNORE_CASE).find(desc)
                ?: Regex("""Score:\s*([+-]?\d+(?:[.,]\d+)?)""", RegexOption.IGNORE_CASE).find(name)
            val score = scoreMatch?.groupValues?.get(1)?.replace(',', '.')?.toDoubleOrNull()
                ?: min(100.0, max(0.0, (apexLean / 50.0) * 100.0))

            val corner = CornerEventEntity(
                rideId = 0L,
                sequenceNumber = seq,
                timestampMs = timeMs,
                direction = direction,
                entrySpeedKmh = entrySpeed.takeIf { it.isFinite() } ?: 0.0,
                apexLeanDeg = apexLean.takeIf { it.isFinite() } ?: 0.0,
                peakLateralG = abs(nearestPt.lateralG).takeIf { it.isFinite() } ?: 0.0,
                peakBrakingG = nearestPt.longitudinalG.takeIf { it.isFinite() } ?: 0.0,
                exitAccelG = max(0.0, nearestPt.longitudinalG).takeIf { it.isFinite() } ?: 0.0,
                maxRollRateDegPerSec = 0.0,
                cornerScore = score.takeIf { it.isFinite() } ?: 0.0,
                latitude = finalLat,
                longitude = finalLon
            )
            wptCorners.add(corner)
        }

        val finalCorners = if (wptCorners.isNotEmpty()) wptCorners.sortedBy { it.sequenceNumber } else detectedCorners

        // Calculate session summary
        val startTime = finalTrackPoints.first().timestampMs
        val endTime = finalTrackPoints.last().timestampMs
        var totalDistanceKm = 0.0
        var totalElevationGain = 0.0
        var maxSpeed = 0.0
        var maxLeftLean = 0.0
        var maxRightLean = 0.0
        var maxLatG = 0.0
        var maxBrakeG = 0.0
        var maxAccG = 0.0

        for (i in finalTrackPoints.indices) {
            val pt = finalTrackPoints[i]
            if (i > 0) {
                val prev = finalTrackPoints[i - 1]
                val d = haversineDistanceMeters(prev.latitude, prev.longitude, pt.latitude, pt.longitude)
                if (d.isFinite() && d > 0.0) {
                    totalDistanceKm += d / 1000.0
                }
                val altDiff = pt.altitudeMeters - prev.altitudeMeters
                if (altDiff.isFinite() && altDiff >= 1.0) {
                    totalElevationGain += altDiff
                }
            }
            if (pt.speedKmh > maxSpeed) maxSpeed = pt.speedKmh
            if (pt.leanAngleDeg > 0) {
                if (pt.leanAngleDeg > maxRightLean) maxRightLean = pt.leanAngleDeg
            } else {
                val absL = abs(pt.leanAngleDeg)
                if (absL > maxLeftLean) maxLeftLean = absL
            }
            val absLat = abs(pt.lateralG)
            if (absLat > maxLatG) maxLatG = absLat
            if (pt.longitudinalG < maxBrakeG) maxBrakeG = pt.longitudinalG
            if (pt.longitudinalG > maxAccG) maxAccG = pt.longitudinalG
        }

        val durationSec = ((endTime - startTime) / 1000.0).coerceAtLeast(0.0)
        val avgSpeed = if (durationSec > 0.0) {
            (totalDistanceKm / (durationSec / 3600.0)).takeIf { it.isFinite() } ?: 0.0
        } else {
            finalTrackPoints.map { it.speedKmh }.average().takeIf { it.isFinite() } ?: 0.0
        }

        val leftCornersCount = finalCorners.count { it.direction == "LEFT" }
        val rightCornersCount = finalCorners.count { it.direction == "RIGHT" }
        val maxTurnInRate = finalCorners.maxOfOrNull { it.maxRollRateDegPerSec }?.takeIf { it.isFinite() } ?: 0.0
        val avgScore = if (finalCorners.isNotEmpty()) {
            finalCorners.map { it.cornerScore }.average().takeIf { it.isFinite() } ?: 0.0
        } else 0.0
        val trailBrakingPct = if (finalCorners.isNotEmpty()) {
            val countTrail = finalCorners.count { it.peakBrakingG <= -0.12 }
            ((countTrail.toDouble() / finalCorners.size) * 100.0).takeIf { it.isFinite() } ?: 0.0
        } else 0.0

        val avgG = ((abs(maxBrakeG) + maxAccG + maxLatG) / 3.0).takeIf { it.isFinite() } ?: 0.0
        val maxLeanOverall = max(maxLeftLean, maxRightLean)
        val badge = when {
            maxLeanOverall > 45.0 && avgG > 0.8 -> "RACER"
            maxLeanOverall < 35.0 && avgG < 0.4 -> "TOURER"
            else -> "TECHNICIAN"
        }

        val ride = RideSessionEntity(
            rideId = 0L,
            motorcycleId = motorcycleId,
            startTimeEpochMs = startTime,
            endTimeEpochMs = endTime,
            distanceKm = totalDistanceKm.takeIf { it.isFinite() } ?: 0.0,
            avgSpeedKmh = avgSpeed.takeIf { it.isFinite() } ?: 0.0,
            maxSpeedKmh = maxSpeed.takeIf { it.isFinite() } ?: 0.0,
            maxLeanLeftDeg = maxLeftLean.takeIf { it.isFinite() } ?: 0.0,
            maxLeanRightDeg = maxRightLean.takeIf { it.isFinite() } ?: 0.0,
            maxLateralG = maxLatG.takeIf { it.isFinite() } ?: 0.0,
            maxBrakingG = maxBrakeG.takeIf { it.isFinite() } ?: 0.0,
            maxAccelG = maxAccG.takeIf { it.isFinite() } ?: 0.0,
            totalCornersCount = finalCorners.size,
            leftCornersCount = leftCornersCount,
            rightCornersCount = rightCornersCount,
            maxTurnInRateDegPerSec = maxTurnInRate,
            trailBrakingScorePct = trailBrakingPct,
            avgCornerScore = avgScore,
            elevationGainMeters = totalElevationGain.takeIf { it.isFinite() } ?: 0.0,
            ridingStyleBadge = badge,
            name = rideName,
            notes = "Imported GPX (${if (detectedImuTelemetry) "Kurve IMU" else "Synthetic Lean"})"
        )

        return ParsedGpxImport(
            ride = ride,
            trackPoints = finalTrackPoints,
            cornerEvents = finalCorners,
            hasEmbeddedImuTelemetry = detectedImuTelemetry
        )
    }

    fun haversineDistanceMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r = 6371000.0
        val phi1 = Math.toRadians(lat1)
        val phi2 = Math.toRadians(lat2)
        val deltaPhi = Math.toRadians(lat2 - lat1)
        val deltaLambda = Math.toRadians(lon2 - lon1)

        val sinDphi = sin(deltaPhi / 2.0)
        val sinDlam = sin(deltaLambda / 2.0)
        val a = sinDphi * sinDphi + cos(phi1) * cos(phi2) * sinDlam * sinDlam
        val c = 2.0 * atan2(sqrt(a.coerceIn(0.0, 1.0)), sqrt((1.0 - a).coerceIn(0.0, 1.0)))
        return (r * c).takeIf { it.isFinite() } ?: 0.0
    }

    fun initialBearing(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val phi1 = Math.toRadians(lat1)
        val phi2 = Math.toRadians(lat2)
        val deltaLambda = Math.toRadians(lon2 - lon1)
        val y = sin(deltaLambda) * cos(phi2)
        val x = cos(phi1) * sin(phi2) - sin(phi1) * cos(phi2) * cos(deltaLambda)
        val bearingRad = atan2(y, x)
        return (Math.toDegrees(bearingRad) + 360.0) % 360.0
    }

    fun angleDifference(angle2: Double, angle1: Double): Double {
        var diff = (angle2 - angle1) % 360.0
        if (diff > 180.0) diff -= 360.0
        if (diff < -180.0) diff += 360.0
        return diff
    }

    private fun parseIsoTimeToEpochMs(timeStr: String?): Long? {
        if (timeStr.isNullOrBlank()) return null
        return try {
            Instant.parse(timeStr).toEpochMilli()
        } catch (_: Exception) {
            try {
                OffsetDateTime.parse(timeStr).toInstant().toEpochMilli()
            } catch (_: Exception) {
                try {
                    ZonedDateTime.parse(timeStr).toInstant().toEpochMilli()
                } catch (_: Exception) {
                    null
                }
            }
        }
    }

    private fun findElementValue(parent: Node, targetName: String): String? {
        val children = parent.childNodes ?: return null
        for (i in 0 until children.length) {
            val child = children.item(i)
            if (child.nodeType == Node.ELEMENT_NODE) {
                val name = child.nodeName
                val localName = if (name.contains(':')) name.substringAfter(':') else name
                if (name.equals(targetName, ignoreCase = true) || localName.equals(targetName, ignoreCase = true)) {
                    val text = child.textContent?.trim()
                    if (!text.isNullOrBlank()) return text
                }
                val nested = findElementValue(child, targetName)
                if (nested != null) return nested
            }
        }
        return null
    }

    private fun getAllElements(doc: Document, targetTag: String): List<Element> {
        val list = mutableListOf<Element>()
        val nodes = doc.getElementsByTagName(targetTag)
        if (nodes.length > 0) {
            for (i in 0 until nodes.length) {
                val node = nodes.item(i)
                if (node is Element) list.add(node)
            }
        } else {
            fun search(node: Node) {
                if (node is Element) {
                    val local = if (node.nodeName.contains(':')) node.nodeName.substringAfter(':') else node.nodeName
                    if (local.equals(targetTag, ignoreCase = true)) {
                        list.add(node)
                    }
                }
                val children = node.childNodes
                if (children != null) {
                    for (i in 0 until children.length) {
                        search(children.item(i))
                    }
                }
            }
            search(doc.documentElement)
        }
        return list
    }
}

package com.motogeek.telemetry.domain.maintenance

import android.content.Context
import android.net.Uri
import com.motogeek.telemetry.data.local.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import java.io.OutputStreamWriter
import java.util.UUID
import kotlinx.coroutines.flow.first

data class ImportSummary(
    val motorcyclesImported: Int = 0,
    val tasksImported: Int = 0,
    val logsImported: Int = 0
)

class ImportExportManager(private val context: Context, private val repository: MotoGeekRepository) {

    private val json = Json {
        encodeDefaults = true
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
        explicitNulls = false
        prettyPrint = true
    }

    val aiServiceManualPrompt: String = """
        You are an expert motorcycle mechanic. I am providing you with the service manual for a motorcycle.
        Extract periodic maintenance items, fluids specifications, and full bolt tightening torques into the JSON format provided. Include boltTorquesNm as a key-value map mapping bolt names to numeric torque values in Nm. Ensure oilCapacityLiters is numeric.
        Please extract the maintenance schedule and technical specifications into the following strict JSON format.
        Do NOT output any markdown blocks or extra text, only the raw JSON.

        {
          "technicalSpecs": {
            "oilCapacityLiters": 3.4,
            "frontTirePressurePsi": 36.0,
            "rearTirePressurePsi": 42.0,
            "chainSlackMm": "20-30",
            "rearAxleNutTorqueNm": 150.0,
            "oilDrainPlugTorqueNm": 43.0,
            "frontCaliperBoltsTorqueNm": 35.0
          },
          "maintenanceSchedule": [
            {
              "taskId": "OIL_CHANGE",
              "category": "Engine",
              "taskName": "Engine Oil & Filter Change",
              "actionType": "Replace",
              "firstBreakInAtKm": 1000.0,
              "intervalKm": 10000.0,
              "intervalMonths": 12,
              "warningThresholdKm": 500.0,
              "warningThresholdDays": 30,
              "oemPartNumbers": ["15410-MFJ-D01"],
              "notes": "Use 10W-40 Fully Synthetic"
            }
          ]
        }
    """.trimIndent()

    private fun sanitizeJsonInput(raw: String): String {
        val noFences = raw.replace("```json", "").replace("```", "")
        val firstBrace = noFences.indexOf('{')
        val lastBrace = noFences.lastIndexOf('}')
        if (firstBrace != -1 && lastBrace != -1 && lastBrace >= firstBrace) {
            return noFences.substring(firstBrace, lastBrace + 1).trim()
        }
        return noFences.trim()
    }

    suspend fun importFromJsonString(rawJson: String, targetMotorcycleId: String?): Result<ImportSummary> {
        val sanitizedJson = rawJson.replace(Regex("```json|```"), "").trim()
        return try {
            val cleanedJson = sanitizeJsonInput(rawJson)
            val jsonElement = Json.parseToJsonElement(cleanedJson).jsonObject

            var bikesImported = 0
            var tasksImported = 0
            var logsImported = 0

            if (jsonElement.containsKey("motorcycles")) {
                // Format A (Full Garage Backup)
                val payload = json.decodeFromString<GarageBackupPayload>(cleanedJson)
                payload.motorcycles.forEach { backup ->
                    val bikeEntity = MotorcycleEntity(
                        id = backup.motorcycle.id,
                        make = backup.motorcycle.make,
                        model = backup.motorcycle.model,
                        year = backup.motorcycle.year,
                        engineDisplacementCc = backup.motorcycle.engineDisplacementCc,
                        baseOdometerKm = backup.motorcycle.baseOdometerKm,
                        accumulatedTrackedKm = backup.motorcycle.accumulatedTrackedKm,
                        manualAdjustmentOffsetKm = backup.motorcycle.manualAdjustmentOffsetKm,
                        technicalSpecsJson = backup.technicalSpecs?.let { json.encodeToString(it) } ?: "{}",
                        isActive = backup.motorcycle.isActive
                    )
                    repository.insertMotorcycle(bikeEntity)
                    bikesImported++

                    val taskEntities = backup.maintenanceSchedule.map { task ->
                        MaintenanceTaskEntity(
                            taskId = task.taskId,
                            motorcycleId = bikeEntity.id,
                            category = task.category,
                            taskName = task.taskName,
                            actionType = task.actionType,
                            firstBreakInAtKm = task.firstBreakInAtKm,
                            intervalKm = task.intervalKm,
                            intervalMonths = task.intervalMonths,
                            warningThresholdKm = task.warningThresholdKm,
                            warningThresholdDays = task.warningThresholdDays,
                            oemPartNumbersJson = json.encodeToString(task.oemPartNumbers),
                            notes = task.notes
                        )
                    }
                    repository.insertMaintenanceTasks(taskEntities)
                    tasksImported += taskEntities.size

                    val logEntities = backup.serviceLogs.map { log ->
                        ServiceLogEntity(
                            logId = log.logId,
                            motorcycleId = bikeEntity.id,
                            taskId = log.taskId,
                            performedAtIsoDate = log.performedAtIsoDate,
                            odometerAtServiceKm = log.odometerAtServiceKm,
                            costEur = log.costEur,
                            performedBy = log.performedBy,
                            notes = log.notes
                        )
                    }
                    repository.insertServiceLogs(logEntities)
                    logsImported += logEntities.size
                }
            } else {
                // Formats B & C
                val targetBike = if (targetMotorcycleId != null) {
                    repository.getAllMotorcycles().first().find { it.id == targetMotorcycleId }
                } else {
                    val activeBike = repository.getActiveMotorcycle()
                    activeBike
                }

                val hasMotorcycleInfo = jsonElement.containsKey("motorcycleInfo")
                val technicalSpecsJson = jsonElement["technicalSpecs"]?.let { json.encodeToString(it) } ?: "{}"

                val bikeEntity = if (targetBike != null) {
                    var updatedBike = targetBike.copy(technicalSpecsJson = technicalSpecsJson)
                    if (hasMotorcycleInfo) {
                        val motoInfo = json.decodeFromString<MotorcycleInfoV2Dto>(jsonElement["motorcycleInfo"].toString())
                        updatedBike = updatedBike.copy(
                            make = motoInfo.make,
                            model = motoInfo.model,
                            year = motoInfo.year,
                            engineDisplacementCc = motoInfo.engineDisplacementCc
                        )
                    } else {
                         val specs = json.decodeFromString<TechnicalSpecsDto>(technicalSpecsJson)
                         if (specs.motorcycleMake != null || specs.motorcycleModel != null) {
                             updatedBike = updatedBike.copy(
                                 make = specs.motorcycleMake ?: updatedBike.make,
                                 model = specs.motorcycleModel ?: updatedBike.model
                             )
                         }
                    }
                    updatedBike
                } else {
                    // Create new bike
                    var make = "Unknown"
                    var model = "Unknown"
                    var year = 2024
                    var cc = 0
                    if (hasMotorcycleInfo) {
                        val motoInfo = json.decodeFromString<MotorcycleInfoV2Dto>(jsonElement["motorcycleInfo"].toString())
                        make = motoInfo.make
                        model = motoInfo.model
                        year = motoInfo.year
                        cc = motoInfo.engineDisplacementCc
                    } else {
                        val specs = json.decodeFromString<TechnicalSpecsDto>(technicalSpecsJson)
                        make = specs.motorcycleMake ?: "Unknown"
                        model = specs.motorcycleModel ?: "Unknown"
                    }

                    MotorcycleEntity(
                        id = UUID.randomUUID().toString(),
                        make = make,
                        model = model,
                        year = year,
                        engineDisplacementCc = cc,
                        baseOdometerKm = 0.0,
                        accumulatedTrackedKm = 0.0,
                        manualAdjustmentOffsetKm = 0.0,
                        technicalSpecsJson = technicalSpecsJson,
                        isActive = true
                    )
                }

                repository.insertMotorcycle(bikeEntity)
                if (targetBike == null) bikesImported++

                val maintenanceScheduleStr = jsonElement["maintenanceSchedule"]?.toString()
                if (maintenanceScheduleStr != null) {
                    val schedule = json.decodeFromString<List<MaintenanceTaskDto>>(maintenanceScheduleStr)
                    val taskEntities = schedule.map { task ->
                        MaintenanceTaskEntity(
                            taskId = task.taskId,
                            motorcycleId = bikeEntity.id,
                            category = task.category,
                            taskName = task.taskName,
                            actionType = task.actionType,
                            firstBreakInAtKm = task.firstBreakInAtKm,
                            intervalKm = task.intervalKm,
                            intervalMonths = task.intervalMonths,
                            warningThresholdKm = task.warningThresholdKm,
                            warningThresholdDays = task.warningThresholdDays,
                            oemPartNumbersJson = json.encodeToString(task.oemPartNumbers),
                            notes = task.notes
                        )
                    }
                    repository.insertMaintenanceTasks(taskEntities)
                    tasksImported += taskEntities.size
                }
            }

            Result.success(ImportSummary(bikesImported, tasksImported, logsImported))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun importGarageBackup(uri: Uri, targetMotorcycleId: String? = null): Result<ImportSummary> {
        return try {
            val jsonString = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
            } ?: throw Exception("Cannot read file")
            importFromJsonString(jsonString, targetMotorcycleId)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun importGarageBackupFromString(jsonString: String): Result<Unit> {
        // Legacy compat
        val res = importFromJsonString(jsonString, null)
        return if (res.isSuccess) Result.success(Unit) else Result.failure(res.exceptionOrNull()!!)
    }

    suspend fun exportGarageBackup(uri: Uri): Result<Unit> {
        return try {
            val motorcycles = repository.getAllMotorcycles().first()
            val backupMotorcycles = motorcycles.map { bike ->
                val tasks = repository.getMaintenanceTasksForMotorcycle(bike.id)
                val logs = repository.getServiceLogsForMotorcycle(bike.id)

                val mDto = MotorcycleDto(
                    id = bike.id,
                    make = bike.make,
                    model = bike.model,
                    year = bike.year,
                    engineDisplacementCc = bike.engineDisplacementCc,
                    baseOdometerKm = bike.baseOdometerKm,
                    accumulatedTrackedKm = bike.accumulatedTrackedKm,
                    manualAdjustmentOffsetKm = bike.manualAdjustmentOffsetKm,
                    isActive = bike.isActive
                )

                val tDto = if (bike.technicalSpecsJson.isNotBlank() && bike.technicalSpecsJson != "{}") {
                    json.decodeFromString<TechnicalSpecsDto>(bike.technicalSpecsJson)
                } else null

                val taskDtos = tasks.map { t ->
                    MaintenanceTaskDto(
                        taskId = t.taskId,
                        category = t.category,
                        taskName = t.taskName,
                        actionType = t.actionType,
                        firstBreakInAtKm = t.firstBreakInAtKm,
                        intervalKm = t.intervalKm,
                        intervalMonths = t.intervalMonths,
                        warningThresholdKm = t.warningThresholdKm,
                        warningThresholdDays = t.warningThresholdDays,
                        oemPartNumbers = if (t.oemPartNumbersJson.isNotBlank() && t.oemPartNumbersJson != "[]") {
                             json.decodeFromString(t.oemPartNumbersJson)
                        } else emptyList(),
                        notes = t.notes
                    )
                }

                val logDtos = logs.map { l ->
                    ServiceLogDto(
                        logId = l.logId,
                        taskId = l.taskId,
                        performedAtIsoDate = l.performedAtIsoDate,
                        odometerAtServiceKm = l.odometerAtServiceKm,
                        costEur = l.costEur,
                        performedBy = l.performedBy,
                        notes = l.notes
                    )
                }

                MotorcycleBackupDto(
                    motorcycle = mDto,
                    technicalSpecs = tDto,
                    maintenanceSchedule = taskDtos,
                    serviceLogs = logDtos
                )
            }

            val payload = GarageBackupPayload(
                exportDateIso = java.time.Instant.now().toString(),
                motorcycles = backupMotorcycles
            )

            val jsonString = json.encodeToString(payload)
            context.contentResolver.openOutputStream(uri)?.use { os ->
                OutputStreamWriter(os).use { writer ->
                    writer.write(jsonString)
                }
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun exportRideAsCsv(uri: Uri, rideId: Long): Result<Unit> {
        return try {
            context.contentResolver.openOutputStream(uri)?.use { os ->
                OutputStreamWriter(os).use { writer ->
                    writer.write("TimestampMs,Latitude,Longitude,SpeedKmh,LeanAngleDeg,LongitudinalG,LateralG,AltitudeM\n")

                    repository.getTelemetryTrackPointsForRide(rideId).collect { points ->
                        points.forEach { p ->
                            writer.write("${p.timestampMs},${p.latitude},${p.longitude},${p.speedKmh},${p.leanAngleDeg},${p.longitudinalG},${p.lateralG},${p.altitudeMeters}\n")
                        }
                    }
                }
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun shareBikeServiceBookHtml(motorcycleId: String): Result<Unit> = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        try {
            val motorcycles = repository.getAllMotorcycles().first()
            val bike = motorcycles.find { it.id == motorcycleId } ?: throw Exception("Bike not found")
            val services = repository.getServiceLogsForMotorcycle(motorcycleId).sortedByDescending { it.performedAtIsoDate }
            val parts = repository.getPartLogsForMotorcycleList(motorcycleId).sortedByDescending { it.dateIso }
            val fuelLogs = repository.getFuelLogsForMotorcycleList(motorcycleId)

            val totalFuelLiters = fuelLogs.sumOf { it.liters }
            val totalFuelCost = fuelLogs.sumOf { it.totalCost }
            val totalServiceCost = services.sumOf { it.costEur }
            val totalPartsCost = parts.sumOf { it.cost }

            val html = """
                <!DOCTYPE html>
                <html>
                <head>
                    <meta charset="UTF-8">
                    <title>Digital Service Book - ${bike.displayName}</title>
                    <style>
                        body { font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, Helvetica, Arial, sans-serif; background-color: #0b0e14; color: #e0e0e0; margin: 0; padding: 24px; }
                        .container { max-width: 800px; margin: 0 auto; background: #131822; padding: 32px; border-radius: 12px; border: 1px solid #2a3447; }
                        h1 { color: #ff5f1f; margin-top: 0; font-size: 26px; }
                        h2 { color: #00e5ff; font-size: 18px; border-bottom: 1px solid #2a3447; padding-bottom: 8px; margin-top: 28px; }
                        .hero { display: flex; justify-content: space-between; margin-bottom: 24px; background: #1c2331; padding: 16px; border-radius: 8px; }
                        .metric-value { font-size: 20px; font-weight: bold; color: #00e5ff; font-family: monospace; }
                        table { width: 100%; border-collapse: collapse; margin-top: 12px; font-size: 13px; }
                        th { background: #1c2331; color: #ff5f1f; text-align: left; padding: 10px; border-bottom: 1px solid #2a3447; }
                        td { padding: 10px; border-bottom: 1px solid #2a3447; }
                        .badge { background: rgba(0, 230, 118, 0.15); color: #00e676; padding: 2px 6px; border-radius: 4px; font-size: 11px; }
                        .footer { margin-top: 36px; text-align: center; color: #6b7a99; font-size: 11px; }
                    </style>
                </head>
                <body>
                    <div class="container">
                        <h1>KURVE // DIGITAL SERVICE BOOK</h1>
                        <div class="hero">
                            <div>
                                <strong style="font-size: 18px; color: #fff;">${bike.displayName.uppercase()}</strong><br>
                                <span style="color: #999;">${bike.year} • ${bike.engineDisplacementCc} CC • ${bike.type}</span>
                            </div>
                            <div style="text-align: right;">
                                <div class="metric-value">${String.format("%,d", bike.totalOdometerKm.toInt())} KM</div>
                                <span style="color: #999;">Total Odometer</span>
                            </div>
                        </div>

                        <h2>Financial & Garage Summary</h2>
                        <table>
                            <tr>
                                <td><strong>Total Service Spend:</strong> ${totalServiceCost.toInt()} ${bike.currencySymbol}</td>
                                <td><strong>Total Parts Spend:</strong> ${totalPartsCost.toInt()} ${bike.currencySymbol}</td>
                            </tr>
                            <tr>
                                <td><strong>Total Fuel Spend:</strong> ${totalFuelCost.toInt()} ${bike.currencySymbol}</td>
                                <td><strong>Total Fuel Volume:</strong> ${String.format("%.1f", totalFuelLiters)} L</td>
                            </tr>
                        </table>

                        <h2>Periodic Maintenance & Service Records (${services.size})</h2>
                        ${if (services.isEmpty()) "<p style='color: #888;'>No service records logged yet.</p>" else """
                        <table>
                            <thead>
                                <tr><th>Date</th><th>Odometer</th><th>Service Item</th><th>Location/Shop</th><th>Cost</th></tr>
                            </thead>
                            <tbody>
                                ${services.joinToString("") { s ->
                                    "<tr><td>${s.performedAtIsoDate}</td><td>${s.odometerAtServiceKm.toInt()} km</td><td><strong>${s.serviceTitle}</strong><br><span style='color:#888;'>${s.notes}</span></td><td>${s.locationOrShop}</td><td>${s.costEur.toInt()} ${bike.currencySymbol}</td></tr>"
                                }}
                            </tbody>
                        </table>
                        """}

                        <h2>Installed Modifications & Replacement Parts (${parts.size})</h2>
                        ${if (parts.isEmpty()) "<p style='color: #888;'>No aftermarket parts or repairs logged.</p>" else """
                        <table>
                            <thead>
                                <tr><th>Date</th><th>Odometer</th><th>Part Name</th><th>Brand / Part #</th><th>Cost</th></tr>
                            </thead>
                            <tbody>
                                ${parts.joinToString("") { p ->
                                    "<tr><td>${p.dateIso}</td><td>${p.odometerKm.toInt()} km</td><td><strong>${p.partName}</strong></td><td>${p.brand} ${p.partNumber}</td><td>${p.cost.toInt()} ${bike.currencySymbol}</td></tr>"
                                }}
                            </tbody>
                        </table>
                        """}

                        <div class="footer">
                            Generated by KurveCounter MotoGeek Telemetry • Verified Garage Record
                        </div>
                    </div>
                </body>
                </html>
            """.trimIndent()

            val sharedDir = java.io.File(context.cacheDir, "shared_files").apply { mkdirs() }
            val file = java.io.File(sharedDir, "service_book_${bike.make}_${bike.model}.html")
            file.writeText(html)

            val uri = androidx.core.content.FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )

            val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                type = "text/html"
                putExtra(android.content.Intent.EXTRA_STREAM, uri)
                putExtra(android.content.Intent.EXTRA_SUBJECT, "Digital Service Book - ${bike.displayName}")
                addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(android.content.Intent.createChooser(intent, "Share Service Book").apply {
                addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            })

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun shareAllLogsCsv(motorcycleId: String): Result<Unit> = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        try {
            val motorcycles = repository.getAllMotorcycles().first()
            val bike = motorcycles.find { it.id == motorcycleId } ?: throw Exception("Bike not found")
            val services = repository.getServiceLogsForMotorcycle(motorcycleId)
            val parts = repository.getPartLogsForMotorcycleList(motorcycleId)
            val fuelLogs = repository.getFuelLogsForMotorcycleList(motorcycleId)

            val csvBuilder = StringBuilder()
            csvBuilder.append("Category,Date,OdometerKm,TitleOrItem,BrandOrShop,Cost,Notes\n")

            services.forEach { s ->
                csvBuilder.append("SERVICE,${s.performedAtIsoDate},${s.odometerAtServiceKm},\"${s.serviceTitle}\",\"${s.locationOrShop}\",${s.costEur},\"${s.notes}\"\n")
            }
            parts.forEach { p ->
                csvBuilder.append("PART,${p.dateIso},${p.odometerKm},\"${p.partName}\",\"${p.brand} ${p.partNumber}\",${p.cost},\"${p.notes}\"\n")
            }
            fuelLogs.forEach { f ->
                csvBuilder.append("FUEL,${f.dateIso},${f.odometerKm},\"${f.liters} Liters\",\"FullTank: ${f.isFullTank}\",${f.totalCost},\"${f.notes}\"\n")
            }

            val sharedDir = java.io.File(context.cacheDir, "shared_files").apply { mkdirs() }
            val file = java.io.File(sharedDir, "garage_logs_${bike.make}_${bike.model}.csv")
            file.writeText(csvBuilder.toString())

            val uri = androidx.core.content.FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )

            val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                type = "text/csv"
                putExtra(android.content.Intent.EXTRA_STREAM, uri)
                putExtra(android.content.Intent.EXTRA_SUBJECT, "Garage Logs CSV - ${bike.displayName}")
                addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(android.content.Intent.createChooser(intent, "Share Logs CSV").apply {
                addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            })

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun shareRideGpx(rideId: Long): Result<Unit> = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        try {
            val trackPoints = repository.getTelemetryTrackPointsListForRide(rideId)
            val session = repository.getRideSessionById(rideId)

            val gpxBuilder = StringBuilder()
            gpxBuilder.append("""<?xml version="1.0" encoding="UTF-8"?>""").append("\n")
            gpxBuilder.append("""<gpx version="1.1" creator="KurveCounter MotoGeek Telemetry">""").append("\n")
            gpxBuilder.append("  <trk>\n")
            gpxBuilder.append("    <name>${session?.name ?: "Ride $rideId"}</name>\n")
            gpxBuilder.append("    <trkseg>\n")

            trackPoints.forEach { pt ->
                val isoTime = java.time.Instant.ofEpochMilli(pt.timestampMs).toString()
                gpxBuilder.append("""      <trkpt lat="${pt.latitude}" lon="${pt.longitude}">""").append("\n")
                gpxBuilder.append("""        <ele>${pt.altitudeMeters}</ele>""").append("\n")
                gpxBuilder.append("""        <time>$isoTime</time>""").append("\n")
                gpxBuilder.append("""        <extensions>""").append("\n")
                gpxBuilder.append("""          <speedKmh>${pt.speedKmh}</speedKmh>""").append("\n")
                gpxBuilder.append("""          <leanDeg>${pt.leanAngleDeg}</leanDeg>""").append("\n")
                gpxBuilder.append("""          <lateralG>${pt.lateralG}</lateralG>""").append("\n")
                gpxBuilder.append("""        </extensions>""").append("\n")
                gpxBuilder.append("""      </trkpt>""").append("\n")
            }

            gpxBuilder.append("    </trkseg>\n")
            gpxBuilder.append("  </trk>\n")
            gpxBuilder.append("</gpx>\n")

            val sharedDir = java.io.File(context.cacheDir, "shared_files").apply { mkdirs() }
            val file = java.io.File(sharedDir, "ride_${rideId}.gpx")
            file.writeText(gpxBuilder.toString())

            val uri = androidx.core.content.FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )

            val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                type = "application/gpx+xml"
                putExtra(android.content.Intent.EXTRA_STREAM, uri)
                putExtra(android.content.Intent.EXTRA_SUBJECT, "Ride GPX - Ride #$rideId")
                addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(android.content.Intent.createChooser(intent, "Share GPX Track").apply {
                addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            })

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun shareRideGoProCsv(rideId: Long): Result<Unit> = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        try {
            val trackPoints = repository.getTelemetryTrackPointsListForRide(rideId)

            val csvBuilder = StringBuilder()
            csvBuilder.append("TimestampMs,Latitude,Longitude,AltitudeM,SpeedKmh,LeanAngleDeg,LongitudinalG,LateralG\n")
            trackPoints.forEach { pt ->
                csvBuilder.append("${pt.timestampMs},${pt.latitude},${pt.longitude},${pt.altitudeMeters},${pt.speedKmh},${pt.leanAngleDeg},${pt.longitudinalG},${pt.lateralG}\n")
            }

            val sharedDir = java.io.File(context.cacheDir, "shared_files").apply { mkdirs() }
            val file = java.io.File(sharedDir, "telemetry_overlay_${rideId}.csv")
            file.writeText(csvBuilder.toString())

            val uri = androidx.core.content.FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )

            val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                type = "text/csv"
                putExtra(android.content.Intent.EXTRA_STREAM, uri)
                putExtra(android.content.Intent.EXTRA_SUBJECT, "GoPro/Telemetry Overlay CSV - Ride #$rideId")
                addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(android.content.Intent.createChooser(intent, "Share GoPro Telemetry CSV").apply {
                addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            })

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}


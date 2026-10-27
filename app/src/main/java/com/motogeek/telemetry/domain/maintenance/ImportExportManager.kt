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

    suspend fun exportBikeServiceBookHtml(motorcycleId: String): Result<Uri> = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        try {
            val motorcycles = repository.getAllMotorcycles().first()
            val bike = motorcycles.find { it.id == motorcycleId } ?: throw Exception("Bike not found")
            val services = repository.getServiceLogsForMotorcycle(motorcycleId)

            val html = """
                <html><body>
                <h1>Service Book: \${bike.displayName}</h1>
                <p>Odometer: \${bike.totalOdometerKm} km</p>
                <h2>Services</h2>
                <ul>
                \${services.joinToString("") { "<li>\${it.performedAtIsoDate} - \${it.serviceTitle} (\${it.odometerAtServiceKm} km)</li>" }}
                </ul>
                </body></html>
            """.trimIndent()

            val uri = Uri.fromFile(java.io.File(context.cacheDir, "service_book.html"))
            context.contentResolver.openOutputStream(uri)?.use { os ->
                OutputStreamWriter(os).use { writer -> writer.write(html) }
            }
            Result.success(uri)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun exportAllLogsAsCsv(uri: Uri, motorcycleId: String): Result<Unit> = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        try {
            // Simplified CSV generation
            context.contentResolver.openOutputStream(uri)?.use { os ->
                OutputStreamWriter(os).use { writer ->
                    writer.write("Type,Date,Odometer,Details\n")
                    val services = repository.getServiceLogsForMotorcycle(motorcycleId)
                    services.forEach { writer.write("Service,\${it.performedAtIsoDate},\${it.odometerAtServiceKm},\${it.serviceTitle}\n") }
                }
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

}

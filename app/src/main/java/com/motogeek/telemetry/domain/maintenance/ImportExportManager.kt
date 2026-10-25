package com.motogeek.telemetry.domain.maintenance

import android.content.Context
import android.net.Uri
import com.motogeek.telemetry.data.local.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.OutputStreamWriter
import kotlinx.coroutines.flow.first

class ImportExportManager(private val context: Context, private val repository: MotoGeekRepository) {

    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }

    val aiServiceManualPrompt: String = """
        You are an expert motorcycle mechanic. I am providing you with the service manual for a motorcycle.
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

    suspend fun importGarageBackupFromString(jsonString: String): Result<Unit> {
        return try {
            val cleanedJson = jsonString.replace("```json", "").replace("```", "").trim()
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
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun importGarageBackup(uri: Uri): Result<Unit> {
        return try {
            val inputStream = context.contentResolver.openInputStream(uri) ?: throw Exception("Cannot open file")
            val jsonString = inputStream.bufferedReader().use { it.readText() }
            importGarageBackupFromString(jsonString)
        } catch (e: Exception) {
            Result.failure(e)
        }
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
}

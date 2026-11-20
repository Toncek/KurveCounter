package com.motogeek.telemetry.domain.maintenance

import android.content.Context
import android.net.Uri
import com.motogeek.telemetry.data.local.*
import com.motogeek.telemetry.domain.telemetry.GpxTelemetryCodec
import com.motogeek.telemetry.service.TelemetryPreferences
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import java.io.File
import java.io.OutputStreamWriter
import java.util.Locale
import java.util.UUID
import kotlinx.coroutines.flow.first

enum class RestoreMode {
    MERGE,
    REPLACE
}

data class ImportSummary(
    val motorcyclesImported: Int = 0,
    val tasksImported: Int = 0,
    val logsImported: Int = 0,
    val fuelLogsImported: Int = 0,
    val partLogsImported: Int = 0,
    val remindersImported: Int = 0,
    val ridesImported: Int = 0
)

class ImportExportManager(private val context: Context, private val repository: MotoGeekRepository) {

    private val safetySnapshotFile: File
        get() = try {
            File(context.filesDir, "pre_restore_safety_snapshot.json")
        } catch (_: Exception) {
            File(System.getProperty("java.io.tmpdir") ?: "/tmp", "pre_restore_safety_snapshot.json")
        }

    fun hasPreRestoreSnapshot(): Boolean {
        return try {
            safetySnapshotFile.exists() && safetySnapshotFile.length() > 10L
        } catch (_: Exception) {
            false
        }
    }

    suspend fun savePreRestoreSnapshot() {
        try {
            val currentBikes = repository.getAllMotorcycles().first()
            if (currentBikes.isNotEmpty()) {
                val snapshotJson = generateBackupJsonString()
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    safetySnapshotFile.writeText(snapshotJson)
                }
            }
        } catch (_: Exception) {
            // Non-fatal in test or restricted environments
        }
    }

    suspend fun restoreLastSafetySnapshot(): Result<ImportSummary> {
        return try {
            val rawJson = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                if (!safetySnapshotFile.exists()) throw IllegalStateException("No safety snapshot available")
                safetySnapshotFile.readText()
            }
            repository.deleteAllMotorcycles()
            importFromJsonString(rawJson, targetMotorcycleId = null, restoreMode = RestoreMode.MERGE)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private val json = Json {
        encodeDefaults = true
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
        explicitNulls = false
        prettyPrint = true
    }

    val aiServiceManualPrompt: String get() = buildAiServiceManualPrompt(null)

    fun buildAiServiceManualPrompt(
        bike: com.motogeek.telemetry.data.local.MotorcycleEntity?,
        langCode: String = "en"
    ): String {
        return buildAiServiceManualPrompt(
            make = bike?.make.orEmpty(),
            model = bike?.model.orEmpty(),
            year = bike?.year,
            engineDisplacementCc = bike?.engineDisplacementCc,
            type = bike?.type.orEmpty(),
            langCode = langCode
        )
    }

    fun buildAiServiceManualPrompt(
        make: String,
        model: String,
        year: Int?,
        engineDisplacementCc: Int?,
        type: String,
        langCode: String = "en"
    ): String {
        val cleanYear = year?.takeIf { it > 0 }?.toString()
        val cleanMake = make.trim().takeIf { it.isNotBlank() }
        val cleanModel = model.trim().takeIf { it.isNotBlank() }
        val cleanCc = engineDisplacementCc?.takeIf { it > 0 }?.let { "${it}cc" }
        val cleanType = type.trim().takeIf { it.isNotBlank() }

        val bikeIdentity = listOfNotNull(cleanYear, cleanMake, cleanModel).joinToString(" ")
        val bikeDetails = listOfNotNull(cleanCc, cleanType).joinToString(", ")
        val bikeContext = when {
            bikeIdentity.isNotBlank() && bikeDetails.isNotBlank() ->
                "for a $bikeIdentity ($bikeDetails)."
            bikeIdentity.isNotBlank() ->
                "for a $bikeIdentity."
            bikeDetails.isNotBlank() ->
                "for a motorcycle ($bikeDetails)."
            else ->
                "for a motorcycle."
        }
        val langInstruction = if (langCode.equals("sk", ignoreCase = true)) {
            "Write all taskName and notes fields in Slovak (Slovenčina), keeping JSON keys and category names in English."
        } else {
            "Write all taskName and notes fields in clear English."
        }
        return """
        You are an expert motorcycle mechanic. I am providing you with the service manual $bikeContext
        $langInstruction
        Extract ALL available technical specs, fluid volumes (oil with/without filter, coolant L, fork oil ml, brake fluid), tire pressures/sizes, valve clearances, full boltTorquesNm map, and a complete maintenance schedule.
        Ensure every task's notes field contains detailed mechanic instructions including exact drain valve/bolt counts and locations, tightening torques in Nm, and fluid capacities in L/ml. Include specific tasks for coolant (e.g. 2 drain bolts—front water pump X Nm, side cylinder Y Nm, Z L required), oil, brakes, tires, chain, valves, spark plugs.
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
            "frontCaliperBoltsTorqueNm": 35.0,
            "waterPumpDrainBoltTorqueNm": 5.0,
            "cylinderDrainBoltTorqueNm": 9.0,
            "oilFilterTorqueNm": 17.0,
            "brakeBleedNippleTorqueNm": 8.0,
            "rearCaliperBoltsTorqueNm": 27.0,
            "frontAxleTorqueNm": 65.0,
            "frontPinchBoltsTorqueNm": 20.0,
            "sparkPlugTorqueNm": 13.0,
            "coolantType": "ethylene glycol 50/50",
            "brakeFluidVolumeMl": 300,
            "frontTireSize": "120/70 ZR17",
            "rearTireSize": "180/55 ZR17"
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
              "notes": "Drain plug 30 Nm, filter 17 Nm, 3.4L 10W-40"
            }
          ]
        }
        """.trimIndent()
    }

    private fun sanitizeJsonInput(raw: String): String {
        val noFences = raw.replace("```json", "").replace("```", "")
        val firstBrace = noFences.indexOf('{')
        val lastBrace = noFences.lastIndexOf('}')
        if (firstBrace != -1 && lastBrace != -1 && lastBrace >= firstBrace) {
            return noFences.substring(firstBrace, lastBrace + 1).trim()
        }
        return noFences.trim()
    }

    suspend fun analyzeJsonString(rawJson: String): Result<ImportPreviewPlan> {
        return try {
            val existing = repository.getAllMotorcycles().first()
            GuidedImportPlanner.analyzeJsonString(rawJson, existing)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun analyzeJsonFile(uri: Uri): Result<ImportPreviewPlan> {
        return try {
            val rawJson = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                context.contentResolver.openInputStream(uri)?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }
            } ?: throw Exception("Cannot read file")
            analyzeJsonString(rawJson)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun executeGuidedImport(
        plan: ImportPreviewPlan,
        selection: GuidedImportSelection,
        langCode: String = "en"
    ): Result<ImportSummary> {
        return try {
            if (selection.applyPreferences && plan.preferences != null) {
                try {
                    val prefs = TelemetryPreferences(context)
                    prefs.autoStartAbove5Kmh = plan.preferences.autoStartAbove5Kmh
                    prefs.startOnBoot = plan.preferences.startOnBoot
                    if (plan.preferences.manualLeanOffsetDeg.isFinite()) {
                        prefs.manualLeanOffsetDeg = plan.preferences.manualLeanOffsetDeg
                    }
                    if (plan.preferences.appLanguageCode.isNotBlank()) {
                        prefs.appLanguageCode = plan.preferences.appLanguageCode
                    }
                } catch (_: Exception) {}
            }

            var totalBikesImported = 0
            var totalTasksImported = 0
            var totalLogsImported = 0
            var totalFuelImported = 0
            var totalPartsImported = 0
            var totalRemindersImported = 0
            var totalRidesImported = 0

            val initialBikes = repository.getAllMotorcycles().first()
            val hadNoBikesInitially = initialBikes.isEmpty()
            val usedBikeIdsInRun = mutableSetOf<String>()

            for (group in plan.bikeGroups) {
                val sel = selection.perBikeSelections[group.sourceKey] ?: continue
                if (!sel.includeBike) continue

                val targetBikeId: String
                var seededTasksCount = 0
                when (val dest = sel.destination) {
                    is ImportDestination.CreateNewBike -> {
                        val idCollides = group.motorcycle.id.isBlank() ||
                            group.motorcycle.id in usedBikeIdsInRun ||
                            repository.getMotorcycleById(group.motorcycle.id) != null
                        targetBikeId = if (idCollides) UUID.randomUUID().toString() else group.motorcycle.id
                        usedBikeIdsInRun.add(targetBikeId)

                        val isActive = hadNoBikesInitially && totalBikesImported == 0
                        val m = group.motorcycle
                        val currency = dest.currencySymbol.ifBlank { m.currencySymbol.ifBlank { "€" } }
                        val specsJson = if (sel.importTechnicalSpecs && group.technicalSpecs != null) {
                            json.encodeToString(group.technicalSpecs)
                        } else "{}"

                        val newBike = MotorcycleEntity(
                            id = targetBikeId,
                            name = m.name.ifBlank { "${m.make} ${m.model}".trim() },
                            make = m.make,
                            model = m.model,
                            year = m.year,
                            engineDisplacementCc = m.engineDisplacementCc,
                            baseOdometerKm = m.baseOdometerKm,
                            accumulatedTrackedKm = 0.0,
                            manualAdjustmentOffsetKm = m.manualAdjustmentOffsetKm,
                            technicalSpecsJson = specsJson,
                            buyingPrice = m.buyingPrice,
                            type = m.type,
                            startDateIso = m.startDateIso,
                            currencySymbol = currency,
                            isActive = isActive
                        )
                        if (repository.getMotorcycleById(targetBikeId) == null) {
                            repository.insertMotorcycle(newBike)
                            totalBikesImported++
                        }

                        if (dest.seedDefaultScheduleIfEmpty && (!sel.importMaintenanceTasks || group.maintenanceTasks.isEmpty())) {
                            val seededTasks = MotorcycleTemplateCatalog.buildStandardMaintenanceTasks(group.technicalSpecs, langCode)
                            val taskEntities = seededTasks.map { task ->
                                MaintenanceTaskEntity(
                                    taskId = task.taskId,
                                    motorcycleId = targetBikeId,
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
                            if (taskEntities.isNotEmpty()) {
                                repository.insertMaintenanceTasks(taskEntities)
                                totalTasksImported += taskEntities.size
                                seededTasksCount = taskEntities.size
                            }
                        }
                    }
                    is ImportDestination.MergeIntoExistingBike -> {
                        targetBikeId = dest.existingBikeId
                        val existing = repository.getMotorcycleById(dest.existingBikeId) ?: continue
                        var updated = existing
                        if (sel.updateBikeProfileAndOdo) {
                            val m = group.motorcycle
                            updated = updated.copy(
                                name = m.name.ifBlank { updated.name },
                                make = m.make.ifBlank { updated.make },
                                model = m.model.ifBlank { updated.model },
                                year = if (m.year > 0) m.year else updated.year,
                                engineDisplacementCc = if (m.engineDisplacementCc > 0) m.engineDisplacementCc else updated.engineDisplacementCc,
                                baseOdometerKm = m.baseOdometerKm,
                                manualAdjustmentOffsetKm = m.manualAdjustmentOffsetKm,
                                type = m.type.ifBlank { updated.type },
                                startDateIso = m.startDateIso.ifBlank { updated.startDateIso },
                                buyingPrice = if (m.buyingPrice > 0.0) m.buyingPrice else updated.buyingPrice,
                                currencySymbol = m.currencySymbol.ifBlank { updated.currencySymbol }
                            )
                        }
                        if (sel.importTechnicalSpecs && group.technicalSpecs != null) {
                            updated = updated.copy(
                                technicalSpecsJson = json.encodeToString(group.technicalSpecs)
                            )
                        }
                        if (updated != existing) {
                            repository.updateMotorcycle(updated)
                        }
                    }
                }

                var newTasksForGroup = seededTasksCount
                var groupTasksImported = seededTasksCount
                var groupLogsImported = 0
                var groupFuelImported = 0
                var groupPartsImported = 0
                var groupRemindersImported = 0
                var groupRidesImported = 0

                // Maintenance tasks
                if (sel.importMaintenanceTasks && group.maintenanceTasks.isNotEmpty()) {
                    val existingTasks = repository.getMaintenanceTasksForMotorcycle(targetBikeId)
                    val existingTaskIds = existingTasks.map { it.taskId }.toSet()
                    val tasksToInsert = group.maintenanceTasks
                        .filter { it.taskId !in existingTaskIds }
                        .map { task ->
                            MaintenanceTaskEntity(
                                taskId = task.taskId,
                                motorcycleId = targetBikeId,
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
                    if (tasksToInsert.isNotEmpty()) {
                        repository.insertMaintenanceTasks(tasksToInsert)
                        totalTasksImported += tasksToInsert.size
                        groupTasksImported += tasksToInsert.size
                        newTasksForGroup += tasksToInsert.size
                    }
                }

                // Service logs
                val allBikes = repository.getAllMotorcycles().first()
                val otherBikesLogIds = allBikes
                    .filter { it.id != targetBikeId }
                    .flatMap { repository.getServiceLogsForMotorcycle(it.id) }
                    .map { it.logId }
                    .toSet()

                val existingTargetLogs = repository.getServiceLogsForMotorcycle(targetBikeId).toMutableList()
                val serviceLogsToInsert = mutableListOf<ServiceLogEntity>()

                for (candidate in group.serviceLogs) {
                    if (candidate.selectionKey !in sel.selectedServiceLogKeys) continue
                    val log = candidate.log

                    val isDuplicateOnTarget = existingTargetLogs.any { other ->
                        other.logId == log.logId || (
                            other.performedAtIsoDate == log.performedAtIsoDate &&
                            kotlin.math.abs(other.odometerAtServiceKm - log.odometerAtServiceKm) < 0.5 &&
                            other.notes.trim() == log.notes.trim()
                        )
                    }
                    if (isDuplicateOnTarget) continue

                    val effectiveLogId = if (log.logId.isBlank() || log.logId in otherBikesLogIds) {
                        UUID.randomUUID().toString()
                    } else {
                        log.logId
                    }

                    val effectiveServicedKm = if (log.servicedAtKm > 0.0) log.servicedAtKm else log.odometerAtServiceKm
                    val serviceEntity = ServiceLogEntity(
                        logId = effectiveLogId,
                        motorcycleId = targetBikeId,
                        taskId = log.taskId,
                        serviceTitle = log.serviceTitle,
                        locationOrShop = log.locationOrShop,
                        performedAtIsoDate = log.performedAtIsoDate,
                        odometerAtServiceKm = log.odometerAtServiceKm,
                        servicedAtKm = effectiveServicedKm,
                        limitKm = log.limitKm,
                        endKm = if (log.endKm > 0.0) log.endKm else effectiveServicedKm + log.limitKm,
                        costEur = log.costEur,
                        performedBy = log.performedBy,
                        notes = log.notes,
                        checklistItemsJson = log.checklistItemsJson
                    )
                    serviceLogsToInsert.add(serviceEntity)
                    existingTargetLogs.add(serviceEntity)
                }

                if (serviceLogsToInsert.isNotEmpty()) {
                    repository.insertServiceLogs(serviceLogsToInsert)
                    totalLogsImported += serviceLogsToInsert.size
                    groupLogsImported += serviceLogsToInsert.size
                }

                // Deduplicate maintenance tasks and remap service logs
                val removedTasksCount = repository.deduplicateMaintenanceTasksForBike(targetBikeId)
                if (removedTasksCount > 0) {
                    val netNewTasks = maxOf(0, newTasksForGroup - removedTasksCount)
                    val taskReduction = newTasksForGroup - netNewTasks
                    totalTasksImported -= taskReduction
                    groupTasksImported = maxOf(0, groupTasksImported - taskReduction)
                }

                // Fuel logs: fuelId = 0L, dedupe on dateIso, odometerKm < 0.1, liters < 0.01
                val existingFuel = repository.getFuelLogsForMotorcycleList(targetBikeId).toMutableList()
                for (candidate in group.fuelLogs) {
                    if (candidate.selectionKey !in sel.selectedFuelLogKeys) continue
                    val f = candidate.log
                    val isDuplicate = existingFuel.any { other ->
                        other.dateIso == f.dateIso &&
                            kotlin.math.abs(other.odometerKm - f.odometerKm) < 0.1 &&
                            kotlin.math.abs(other.liters - f.liters) < 0.01
                    }
                    if (!isDuplicate) {
                        val entity = f.copy(fuelId = 0L, motorcycleId = targetBikeId)
                        repository.insertFuelLog(entity)
                        existingFuel.add(entity)
                        totalFuelImported++
                        groupFuelImported++
                    }
                }

                // Part logs: partId = 0L
                val existingParts = repository.getPartLogsForMotorcycleList(targetBikeId).toMutableList()
                for (candidate in group.partLogs) {
                    if (candidate.selectionKey !in sel.selectedPartLogKeys) continue
                    val p = candidate.log
                    val isDuplicate = existingParts.any { other ->
                        other.dateIso == p.dateIso &&
                            other.partName == p.partName &&
                            kotlin.math.abs(other.odometerKm - p.odometerKm) < 0.1
                    }
                    if (!isDuplicate) {
                        val entity = p.copy(partId = 0L, motorcycleId = targetBikeId)
                        repository.insertPartLog(entity)
                        existingParts.add(entity)
                        totalPartsImported++
                        groupPartsImported++
                    }
                }

                // Reminders: reminderId = 0L
                val existingReminders = repository.getRemindersForMotorcycleList(targetBikeId).toMutableList()
                for (candidate in group.reminders) {
                    if (candidate.selectionKey !in sel.selectedReminderKeys) continue
                    val r = candidate.reminder
                    val isDuplicate = existingReminders.any { other ->
                        other.title == r.title &&
                            other.targetOdometerKm == r.targetOdometerKm &&
                            other.targetDateIso == r.targetDateIso
                    }
                    if (!isDuplicate) {
                        val entity = r.copy(reminderId = 0L, motorcycleId = targetBikeId)
                        repository.insertReminder(entity)
                        existingReminders.add(entity)
                        totalRemindersImported++
                        groupRemindersImported++
                    }
                }

                // Rides: rideId = 0L, dedupe startTimeEpochMs
                val existingRides = repository.getRideSessionsForMotorcycleList(targetBikeId).toMutableList()
                for (candidate in group.rides) {
                    if (candidate.selectionKey !in sel.selectedRideKeys) continue
                    val r = candidate.ride
                    val isDuplicate = existingRides.any { other ->
                        other.startTimeEpochMs == r.session.startTimeEpochMs
                    }
                    if (!isDuplicate) {
                        val newRideId = repository.insertRideSession(
                            r.session.copy(rideId = 0L, motorcycleId = targetBikeId)
                        )
                        if (r.corners.isNotEmpty()) {
                            repository.insertCornerEvents(
                                r.corners.map { it.copy(cornerId = 0L, rideId = newRideId) }
                            )
                        }
                        if (r.trackPoints.isNotEmpty()) {
                            repository.insertTelemetryTrackPoints(
                                r.trackPoints.map { it.copy(pointId = 0L, rideId = newRideId) }
                            )
                        }
                        existingRides.add(r.session.copy(rideId = newRideId, motorcycleId = targetBikeId))
                        totalRidesImported++
                        groupRidesImported++
                    }
                }

                // AppLogs: dedupe logId when sel.importAppLogs
                if (sel.importAppLogs && group.appLogs.isNotEmpty()) {
                    val existingAppLogs = repository.getAppLogsForMotorcycleList(targetBikeId).toMutableList()
                    val existingAppLogIds = existingAppLogs.map { it.logId }.toSet()
                    val appLogsToInsert = group.appLogs
                        .filter { it.logId !in existingAppLogIds }
                        .map { it.copy(motorcycleId = targetBikeId) }
                    if (appLogsToInsert.isNotEmpty()) {
                        repository.insertAppLogs(appLogsToInsert)
                        existingAppLogs.addAll(appLogsToInsert)
                    }
                }

                // Log summary activity to repository.logActivity(targetBikeId, "SYSTEM", ...)
                repository.logActivity(
                    motorcycleId = targetBikeId,
                    category = "SYSTEM",
                    title = "Guided Import Completed",
                    details = "Imported $groupTasksImported tasks, $groupLogsImported service logs, $groupFuelImported fuel logs, $groupPartsImported parts, $groupRemindersImported reminders, $groupRidesImported rides",
                    iconKey = "INFO"
                )
            }

            Result.success(
                ImportSummary(
                    motorcyclesImported = totalBikesImported,
                    tasksImported = totalTasksImported,
                    logsImported = totalLogsImported,
                    fuelLogsImported = totalFuelImported,
                    partLogsImported = totalPartsImported,
                    remindersImported = totalRemindersImported,
                    ridesImported = totalRidesImported
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun importFromJsonString(
        rawJson: String,
        targetMotorcycleId: String?,
        restoreMode: RestoreMode = RestoreMode.MERGE
    ): Result<ImportSummary> {
        return try {
            val cleanedJson = sanitizeJsonInput(rawJson)
            val jsonElement = Json.parseToJsonElement(cleanedJson).jsonObject

            // If JSON is a MyBikes export ("data" array), route through GuidedImportPlanner + executeGuidedImport
            if (jsonElement.containsKey("data") && jsonElement["data"] is kotlinx.serialization.json.JsonArray) {
                val existingBikes = repository.getAllMotorcycles().first()
                val planResult = GuidedImportPlanner.analyzeJsonString(cleanedJson, existingBikes)
                if (planResult.isFailure) {
                    return Result.failure(planResult.exceptionOrNull() ?: Exception("Failed to analyze MyBikes export"))
                }
                val plan = planResult.getOrThrow()
                val defaultSel = GuidedImportPlanner.buildDefaultSelection(plan)
                val selection = if (restoreMode == RestoreMode.MERGE || targetMotorcycleId != null) {
                    val updatedMap = defaultSel.perBikeSelections.toMutableMap()
                    for ((key, bSel) in defaultSel.perBikeSelections) {
                        val group = plan.bikeGroups.find { it.sourceKey == key }
                        val mergeBikeId = targetMotorcycleId
                            ?: group?.suggestedExistingBikeId
                            ?: (group?.motorcycle?.id?.let { id -> repository.getMotorcycleById(id)?.id })
                        if (mergeBikeId != null) {
                            updatedMap[key] = bSel.copy(
                                destination = ImportDestination.MergeIntoExistingBike(mergeBikeId)
                            )
                        }
                    }
                    defaultSel.copy(perBikeSelections = updatedMap)
                } else {
                    defaultSel
                }
                return executeGuidedImport(plan, selection)
            }

            var bikesImported = 0
            var tasksImported = 0
            var logsImported = 0
            var fuelImported = 0
            var partsImported = 0
            var remindersImported = 0
            var ridesImported = 0

            if (jsonElement.containsKey("motorcycles")) {
                // Format A (Full Garage Backup v1 & v2)
                val payload = json.decodeFromString<GarageBackupPayload>(cleanedJson)

                if (restoreMode == RestoreMode.REPLACE) {
                    savePreRestoreSnapshot()
                    repository.deleteAllMotorcycles()
                }

                payload.preferences?.let { prefDto ->
                    try {
                        val prefs = TelemetryPreferences(context)
                        prefs.autoStartAbove5Kmh = prefDto.autoStartAbove5Kmh
                        prefs.startOnBoot = prefDto.startOnBoot
                        if (prefDto.manualLeanOffsetDeg.isFinite()) {
                            prefs.manualLeanOffsetDeg = prefDto.manualLeanOffsetDeg
                        }
                        if (prefDto.appLanguageCode.isNotBlank()) {
                            prefs.appLanguageCode = prefDto.appLanguageCode
                        }
                    } catch (_: Exception) {}
                }

                payload.motorcycles.forEach { backup ->
                    val m = backup.motorcycle
                    val existingBike = if (restoreMode == RestoreMode.MERGE) {
                        repository.getMotorcycleById(m.id)
                    } else null

                    val bikeEntity = MotorcycleEntity(
                        id = m.id,
                        name = m.name.ifBlank { "${m.make} ${m.model}".trim() },
                        make = m.make,
                        model = m.model,
                        year = m.year,
                        engineDisplacementCc = m.engineDisplacementCc,
                        baseOdometerKm = m.baseOdometerKm,
                        accumulatedTrackedKm = existingBike?.accumulatedTrackedKm ?: m.accumulatedTrackedKm,
                        manualAdjustmentOffsetKm = m.manualAdjustmentOffsetKm,
                        technicalSpecsJson = backup.technicalSpecs?.let { json.encodeToString(it) } ?: existingBike?.technicalSpecsJson ?: "{}",
                        buyingPrice = m.buyingPrice,
                        type = m.type,
                        startDateIso = m.startDateIso,
                        currencySymbol = m.currencySymbol,
                        isActive = existingBike?.isActive ?: m.isActive
                    )
                    if (existingBike != null) {
                        repository.updateMotorcycle(bikeEntity)
                    } else {
                        repository.insertMotorcycle(bikeEntity)
                        bikesImported++
                    }

                    val existingTasks = if (restoreMode == RestoreMode.MERGE) {
                        repository.getMaintenanceTasksForMotorcycle(bikeEntity.id).map { it.taskId }.toSet()
                    } else emptySet()

                    var newTasksForBike = 0
                    val taskEntities = backup.maintenanceSchedule
                        .filter { if (restoreMode == RestoreMode.MERGE) it.taskId !in existingTasks else true }
                        .map { task ->
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
                    if (taskEntities.isNotEmpty()) {
                        repository.insertMaintenanceTasks(taskEntities)
                        tasksImported += taskEntities.size
                        newTasksForBike = taskEntities.size
                    }

                    val existingLogs = if (restoreMode == RestoreMode.MERGE) {
                        repository.getServiceLogsForMotorcycle(bikeEntity.id)
                    } else emptyList()

                    val logsToInsert = mutableListOf<ServiceLogEntity>()
                    backup.serviceLogs.forEach { log ->
                        val isDuplicate = if (restoreMode == RestoreMode.MERGE) {
                            existingLogs.any {
                                it.logId == log.logId || (
                                    it.performedAtIsoDate == log.performedAtIsoDate &&
                                    kotlin.math.abs(it.odometerAtServiceKm - log.odometerAtServiceKm) < 0.5 &&
                                    it.notes.trim() == log.notes.trim()
                                )
                            }
                        } else false

                        if (!isDuplicate) {
                            val effectiveServicedKm = if (log.servicedAtKm > 0.0) log.servicedAtKm else log.odometerAtServiceKm
                            logsToInsert.add(
                                ServiceLogEntity(
                                    logId = log.logId,
                                    motorcycleId = bikeEntity.id,
                                    taskId = log.taskId,
                                    serviceTitle = log.serviceTitle,
                                    locationOrShop = log.locationOrShop,
                                    performedAtIsoDate = log.performedAtIsoDate,
                                    odometerAtServiceKm = log.odometerAtServiceKm,
                                    servicedAtKm = effectiveServicedKm,
                                    limitKm = log.limitKm,
                                    endKm = if (log.endKm > 0.0) log.endKm else effectiveServicedKm + log.limitKm,
                                    costEur = log.costEur,
                                    performedBy = log.performedBy,
                                    notes = log.notes,
                                    checklistItemsJson = log.checklistItemsJson
                                )
                            )
                        }
                    }
                    if (logsToInsert.isNotEmpty()) {
                        repository.insertServiceLogs(logsToInsert)
                        logsImported += logsToInsert.size
                    }

                    val removedFormatATasks = repository.deduplicateMaintenanceTasksForBike(bikeEntity.id)
                    if (removedFormatATasks > 0) {
                        val netNewTasks = maxOf(0, newTasksForBike - removedFormatATasks)
                        val taskReduction = newTasksForBike - netNewTasks
                        tasksImported -= taskReduction
                    }

                    val existingFuel = if (restoreMode == RestoreMode.MERGE) {
                        repository.getFuelLogsForMotorcycleList(bikeEntity.id)
                    } else emptyList()
                    backup.fuelLogs.forEach { fuel ->
                        val isDuplicate = existingFuel.any {
                            it.dateIso == fuel.dateIso &&
                                kotlin.math.abs(it.odometerKm - fuel.odometerKm) < 0.1 &&
                                kotlin.math.abs(it.liters - fuel.liters) < 0.01
                        }
                        if (!isDuplicate) {
                            repository.insertFuelLog(fuel.copy(fuelId = 0L, motorcycleId = bikeEntity.id))
                            fuelImported++
                        }
                    }

                    val existingParts = if (restoreMode == RestoreMode.MERGE) {
                        repository.getPartLogsForMotorcycleList(bikeEntity.id)
                    } else emptyList()
                    backup.partLogs.forEach { part ->
                        val isDuplicate = existingParts.any {
                            it.dateIso == part.dateIso &&
                                it.partName == part.partName &&
                                kotlin.math.abs(it.odometerKm - part.odometerKm) < 0.1
                        }
                        if (!isDuplicate) {
                            repository.insertPartLog(part.copy(partId = 0L, motorcycleId = bikeEntity.id))
                            partsImported++
                        }
                    }

                    val existingReminders = if (restoreMode == RestoreMode.MERGE) {
                        repository.getRemindersForMotorcycleList(bikeEntity.id)
                    } else emptyList()
                    backup.reminders.forEach { rem ->
                        val isDuplicate = existingReminders.any {
                            it.title == rem.title &&
                                it.targetOdometerKm == rem.targetOdometerKm &&
                                it.targetDateIso == rem.targetDateIso
                        }
                        if (!isDuplicate) {
                            repository.insertReminder(rem.copy(reminderId = 0L, motorcycleId = bikeEntity.id))
                            remindersImported++
                        }
                    }

                    val existingRides = if (restoreMode == RestoreMode.MERGE) {
                        repository.getRideSessionsForMotorcycleList(bikeEntity.id)
                    } else emptyList()
                    backup.rides.forEach { rideBackup ->
                        val isDuplicate = existingRides.any {
                            it.startTimeEpochMs == rideBackup.session.startTimeEpochMs
                        }
                        if (!isDuplicate) {
                            val newRideId = repository.insertRideSession(
                                rideBackup.session.copy(rideId = 0L, motorcycleId = bikeEntity.id)
                            )
                            if (rideBackup.corners.isNotEmpty()) {
                                repository.insertCornerEvents(
                                    rideBackup.corners.map { it.copy(cornerId = 0L, rideId = newRideId) }
                                )
                            }
                            if (rideBackup.trackPoints.isNotEmpty()) {
                                repository.insertTelemetryTrackPoints(
                                    rideBackup.trackPoints.map { it.copy(pointId = 0L, rideId = newRideId) }
                                )
                            }
                            ridesImported++
                        }
                    }

                    if (backup.appLogs.isNotEmpty()) {
                        val existingAppLogs = if (restoreMode == RestoreMode.MERGE) {
                            repository.getAppLogsForMotorcycleList(bikeEntity.id).map { it.logId }.toSet()
                        } else emptySet()
                        val appLogsToInsert = backup.appLogs
                            .filter { if (restoreMode == RestoreMode.MERGE) it.logId !in existingAppLogs else true }
                            .map { it.copy(motorcycleId = bikeEntity.id) }
                        if (appLogsToInsert.isNotEmpty()) {
                            repository.insertAppLogs(appLogsToInsert)
                        }
                    }
                }

                val allBikes = repository.getAllMotorcycles().first()
                if (allBikes.isNotEmpty() && allBikes.none { it.isActive }) {
                    repository.selectActiveMotorcycle(allBikes.first().id)
                }
            } else {
                // Formats B & C (AI Service Manual / Schedule Import)
                val targetBike = if (targetMotorcycleId != null) {
                    repository.getAllMotorcycles().first().find { it.id == targetMotorcycleId }
                } else {
                    repository.getActiveMotorcycle()
                }

                val hasMotorcycleInfo = jsonElement.containsKey("motorcycleInfo")
                val technicalSpecsJson = jsonElement["technicalSpecs"]?.let { json.encodeToString(it) } ?: "{}"

                val bikeEntity = if (targetBike != null) {
                    var updatedBike = targetBike.copy(technicalSpecsJson = technicalSpecsJson)
                    if (hasMotorcycleInfo) {
                        val motoInfo = json.decodeFromString<MotorcycleInfoV2Dto>(jsonElement["motorcycleInfo"].toString())
                        updatedBike = updatedBike.copy(
                            make = motoInfo.make.ifBlank { updatedBike.make },
                            model = motoInfo.model.ifBlank { updatedBike.model },
                            year = if (motoInfo.year > 0) motoInfo.year else updatedBike.year,
                            engineDisplacementCc = if (motoInfo.engineDisplacementCc > 0) motoInfo.engineDisplacementCc else updatedBike.engineDisplacementCc
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
                        name = "$make $model".trim(),
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

                if (targetBike != null) {
                    repository.updateMotorcycle(bikeEntity)
                } else {
                    repository.insertMotorcycle(bikeEntity)
                    bikesImported++
                }

                var newTasksForBike = 0
                val maintenanceScheduleStr = jsonElement["maintenanceSchedule"]?.toString()
                if (maintenanceScheduleStr != null) {
                    val schedule = json.decodeFromString<List<MaintenanceTaskDto>>(maintenanceScheduleStr)
                    val existingTaskIds = if (targetBike != null) {
                        repository.getMaintenanceTasksForMotorcycle(bikeEntity.id).map { it.taskId }.toSet()
                    } else emptySet()
                    val taskEntities = schedule
                        .filter { it.taskId !in existingTaskIds }
                        .map { task ->
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
                    if (taskEntities.isNotEmpty()) {
                        repository.insertMaintenanceTasks(taskEntities)
                        tasksImported += taskEntities.size
                        newTasksForBike = taskEntities.size
                    }
                }

                val removedFormatBCTasks = repository.deduplicateMaintenanceTasksForBike(bikeEntity.id)
                if (removedFormatBCTasks > 0) {
                    val netNewTasks = maxOf(0, newTasksForBike - removedFormatBCTasks)
                    val taskReduction = newTasksForBike - netNewTasks
                    tasksImported -= taskReduction
                }
            }

            Result.success(
                ImportSummary(
                    motorcyclesImported = bikesImported,
                    tasksImported = tasksImported,
                    logsImported = logsImported,
                    fuelLogsImported = fuelImported,
                    partLogsImported = partsImported,
                    remindersImported = remindersImported,
                    ridesImported = ridesImported
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun importGarageBackup(
        uri: Uri,
        targetMotorcycleId: String? = null,
        restoreMode: RestoreMode = RestoreMode.MERGE
    ): Result<ImportSummary> {
        return try {
            val jsonString = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
            } ?: throw Exception("Cannot read file")
            importFromJsonString(jsonString, targetMotorcycleId, restoreMode)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun importGarageBackupFromString(
        jsonString: String,
        restoreMode: RestoreMode = RestoreMode.MERGE
    ): Result<Unit> {
        val res = importFromJsonString(jsonString, null, restoreMode)
        return if (res.isSuccess) Result.success(Unit) else Result.failure(res.exceptionOrNull()!!)
    }

    suspend fun generateBackupJsonString(): String {
        val motorcycles = repository.getAllMotorcycles().first()
        val backupMotorcycles = motorcycles.map { bike ->
            val tasks = repository.getMaintenanceTasksForMotorcycle(bike.id)
            val logs = repository.getServiceLogsForMotorcycle(bike.id)
            val fuelLogs = repository.getFuelLogsForMotorcycleList(bike.id)
            val partLogs = repository.getPartLogsForMotorcycleList(bike.id)
            val reminders = repository.getRemindersForMotorcycleList(bike.id)
            val sessions = repository.getRideSessionsForMotorcycleList(bike.id)
            val rides = sessions.map { session ->
                val corners = repository.getCornerEventsForRide(session.rideId).first()
                val trackPoints = repository.getTelemetryTrackPointsListForRide(session.rideId)
                RideBackupDto(
                    session = session,
                    corners = corners,
                    trackPoints = trackPoints
                )
            }
            val appLogs = repository.getAppLogsForMotorcycleList(bike.id)

            val mDto = MotorcycleDto(
                id = bike.id,
                make = bike.make,
                model = bike.model,
                year = bike.year,
                engineDisplacementCc = bike.engineDisplacementCc,
                baseOdometerKm = bike.baseOdometerKm,
                accumulatedTrackedKm = bike.accumulatedTrackedKm,
                manualAdjustmentOffsetKm = bike.manualAdjustmentOffsetKm,
                isActive = bike.isActive,
                name = bike.name,
                type = bike.type,
                startDateIso = bike.startDateIso,
                buyingPrice = bike.buyingPrice,
                currencySymbol = bike.currencySymbol
            )

            val tDto = if (bike.technicalSpecsJson.isNotBlank() && bike.technicalSpecsJson != "{}") {
                try {
                    json.decodeFromString<TechnicalSpecsDto>(bike.technicalSpecsJson)
                } catch (_: Exception) {
                    null
                }
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
                        try {
                            json.decodeFromString(t.oemPartNumbersJson)
                        } catch (_: Exception) {
                            emptyList()
                        }
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
                    notes = l.notes,
                    serviceTitle = l.serviceTitle,
                    locationOrShop = l.locationOrShop,
                    servicedAtKm = l.servicedAtKm,
                    limitKm = l.limitKm,
                    endKm = l.endKm,
                    checklistItemsJson = l.checklistItemsJson
                )
            }

            MotorcycleBackupDto(
                motorcycle = mDto,
                technicalSpecs = tDto,
                maintenanceSchedule = taskDtos,
                serviceLogs = logDtos,
                fuelLogs = fuelLogs,
                partLogs = partLogs,
                reminders = reminders,
                rides = rides,
                appLogs = appLogs
            )
        }

        val preferencesDto = try {
            val prefs = TelemetryPreferences(context)
            TelemetryPreferencesDto(
                autoStartAbove5Kmh = prefs.autoStartAbove5Kmh,
                startOnBoot = prefs.startOnBoot,
                manualLeanOffsetDeg = prefs.manualLeanOffsetDeg,
                appLanguageCode = prefs.appLanguageCode
            )
        } catch (_: Exception) {
            null
        }

        val payload = GarageBackupPayload(
            schemaVersion = 2,
            exportDateIso = java.time.Instant.now().toString(),
            motorcycles = backupMotorcycles,
            preferences = preferencesDto
        )

        return json.encodeToString(payload)
    }

    suspend fun exportGarageBackup(uri: Uri): Result<Unit> {
        return try {
            val jsonString = generateBackupJsonString()
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

    suspend fun exportRideAsCsv(uri: Uri, rideId: Long): Result<Unit> = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        try {
            val points = repository.getTelemetryTrackPointsListForRide(rideId)
            context.contentResolver.openOutputStream(uri)?.use { os ->
                OutputStreamWriter(os, Charsets.UTF_8).use { writer ->
                    writer.write("TimestampMs,Latitude,Longitude,SpeedKmh,LeanAngleDeg,LongitudinalG,LateralG,AltitudeM\n")
                    points.forEach { p ->
                        val line = String.format(
                            java.util.Locale.US,
                            "%d,%.6f,%.6f,%.2f,%.2f,%.3f,%.3f,%.2f\n",
                            p.timestampMs,
                            p.latitude,
                            p.longitude,
                            p.speedKmh,
                            p.leanAngleDeg,
                            p.longitudinalG,
                            p.lateralG,
                            p.altitudeMeters
                        )
                        writer.write(line)
                    }
                }
            } ?: throw IllegalStateException("Cannot open output stream for CSV export")
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
            val session = repository.getRideSessionById(rideId) ?: throw IllegalArgumentException("Ride session not found: $rideId")
            val trackPoints = repository.getTelemetryTrackPointsListForRide(rideId)
            val validPoints = GpxTelemetryCodec.filterValidTrackPoints(trackPoints)
            if (validPoints.isEmpty()) {
                throw IllegalStateException("Ride has no valid GPS track points to export")
            }
            val corners = repository.getCornerEventsListForRide(rideId)
            val bike = repository.getMotorcycleById(session.motorcycleId)
            val bikeName = bike?.displayName ?: bike?.let { "${it.year} ${it.make} ${it.model}".trim() }

            val gpxXml = GpxTelemetryCodec.encodeRideToGpx(
                ride = session,
                trackPoints = validPoints,
                cornerEvents = corners,
                bikeName = bikeName
            )

            val sharedDir = java.io.File(context.cacheDir, "shared_files").apply { mkdirs() }
            val file = java.io.File(sharedDir, "ride_${rideId}.gpx")
            file.writeText(gpxXml)

            val uri = androidx.core.content.FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )

            val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                type = "application/gpx+xml"
                putExtra(android.content.Intent.EXTRA_STREAM, uri)
                putExtra(android.content.Intent.EXTRA_SUBJECT, "Ride GPX - ${session.name ?: "Ride #$rideId"}")
                addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            val chooser = android.content.Intent.createChooser(intent, "Share GPX Track").apply {
                addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                context.startActivity(chooser)
            }

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
                val line = String.format(
                    Locale.US,
                    "%d,%.6f,%.6f,%.2f,%.2f,%.2f,%.3f,%.3f\n",
                    pt.timestampMs,
                    pt.latitude,
                    pt.longitude,
                    pt.altitudeMeters,
                    pt.speedKmh,
                    pt.leanAngleDeg,
                    pt.longitudinalG,
                    pt.lateralG
                )
                csvBuilder.append(line)
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
            val chooser = android.content.Intent.createChooser(intent, "Share GoPro Telemetry CSV").apply {
                addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                context.startActivity(chooser)
            }

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun exportRideAsGpx(outputUri: Uri, rideId: Long): Result<Int> = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        try {
            val session = repository.getRideSessionById(rideId) ?: throw IllegalArgumentException("Ride session not found: $rideId")
            val trackPoints = repository.getTelemetryTrackPointsListForRide(rideId)
            val validPoints = GpxTelemetryCodec.filterValidTrackPoints(trackPoints)
            if (validPoints.isEmpty()) {
                throw IllegalStateException("Ride has no valid GPS track points to export")
            }
            val corners = repository.getCornerEventsListForRide(rideId)
            val bike = repository.getMotorcycleById(session.motorcycleId)
            val bikeName = bike?.displayName ?: bike?.let { "${it.year} ${it.make} ${it.model}".trim() }

            val gpxXml = GpxTelemetryCodec.encodeRideToGpx(
                ride = session,
                trackPoints = validPoints,
                cornerEvents = corners,
                bikeName = bikeName
            )

            context.contentResolver.openOutputStream(outputUri)?.use { os ->
                OutputStreamWriter(os, Charsets.UTF_8).use { writer ->
                    writer.write(gpxXml)
                }
            } ?: throw IllegalStateException("Cannot open output stream for GPX export")

            Result.success(validPoints.size)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun importGpxString(
        gpxXml: String,
        targetMotorcycleId: String,
        fallbackName: String? = null
    ): Result<RideSessionEntity> = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        try {
            val parsed = GpxTelemetryCodec.parseGpxToRide(
                gpxXml = gpxXml,
                motorcycleId = targetMotorcycleId,
                fallbackName = fallbackName
            )

            val insertedRideId = repository.insertRideSession(parsed.ride.copy(rideId = 0L, motorcycleId = targetMotorcycleId))
            val finalRide = parsed.ride.copy(rideId = insertedRideId, motorcycleId = targetMotorcycleId)

            if (parsed.trackPoints.isNotEmpty()) {
                val mappedPoints = parsed.trackPoints.map { it.copy(pointId = 0L, rideId = insertedRideId) }
                repository.insertTelemetryTrackPoints(mappedPoints)
            }

            if (parsed.cornerEvents.isNotEmpty()) {
                val mappedCorners = parsed.cornerEvents.map { it.copy(cornerId = 0L, rideId = insertedRideId) }
                repository.insertCornerEvents(mappedCorners)
            }

            if (finalRide.distanceKm > 0.0) {
                repository.addAccumulatedTrackedKm(targetMotorcycleId, finalRide.distanceKm)
            }

            Result.success(finalRide)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun importGpxFromUri(uri: Uri, targetMotorcycleId: String): Result<RideSessionEntity> = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        try {
            val gpxXml = context.contentResolver.openInputStream(uri)?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }
                ?: throw IllegalStateException("Cannot read GPX file from URI")

            val fallbackName = try {
                var name: String? = null
                context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                    val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                    if (nameIndex != -1 && cursor.moveToFirst()) {
                        name = cursor.getString(nameIndex)
                    }
                }
                name?.removeSuffix(".gpx")?.removeSuffix(".GPX")
            } catch (_: Exception) {
                null
            }

            importGpxString(gpxXml, targetMotorcycleId, fallbackName)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}


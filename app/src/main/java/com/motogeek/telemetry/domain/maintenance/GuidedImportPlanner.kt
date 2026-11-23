package com.motogeek.telemetry.domain.maintenance

import com.motogeek.telemetry.data.local.AppLogEntity
import com.motogeek.telemetry.data.local.FuelLogEntity
import com.motogeek.telemetry.data.local.MotorcycleEntity
import com.motogeek.telemetry.data.local.PartLogEntity
import com.motogeek.telemetry.data.local.ReminderEntity
import com.motogeek.telemetry.data.local.ServiceChecklistItem
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.util.UUID

enum class ImportSourceFormat {
    MYBIKES_EXPORT,
    KURVE_GARAGE_BACKUP,
    AI_MANUAL_SCHEDULE
}

data class ImportServiceLogCandidate(
    val selectionKey: String,
    val log: ServiceLogDto,
    val isDuplicateInFile: Boolean = false
)

data class ImportFuelLogCandidate(
    val selectionKey: String,
    val log: FuelLogEntity,
    val stationOrFuelLabel: String = ""
)

data class ImportPartLogCandidate(
    val selectionKey: String,
    val log: PartLogEntity
)

data class ImportReminderCandidate(
    val selectionKey: String,
    val reminder: ReminderEntity
)

data class ImportRideCandidate(
    val selectionKey: String,
    val ride: RideBackupDto
)

data class ImportSourceBikeGroup(
    val sourceKey: String,
    val motorcycle: MotorcycleDto,
    val technicalSpecs: TechnicalSpecsDto? = null,
    val maintenanceTasks: List<MaintenanceTaskDto> = emptyList(),
    val serviceLogs: List<ImportServiceLogCandidate> = emptyList(),
    val fuelLogs: List<ImportFuelLogCandidate> = emptyList(),
    val partLogs: List<ImportPartLogCandidate> = emptyList(),
    val reminders: List<ImportReminderCandidate> = emptyList(),
    val rides: List<ImportRideCandidate> = emptyList(),
    val appLogs: List<AppLogEntity> = emptyList(),
    val suggestedExistingBikeId: String? = null,
    val hasExplicitBikeMetadata: Boolean = true
)

data class ImportPreviewPlan(
    val format: ImportSourceFormat,
    val bikeGroups: List<ImportSourceBikeGroup>,
    val preferences: TelemetryPreferencesDto? = null,
    val rawChangelogEntriesCount: Int = 0
)

sealed class ImportDestination {
    data class CreateNewBike(
        val currencySymbol: String = "€",
        val seedDefaultScheduleIfEmpty: Boolean = true
    ) : ImportDestination()

    data class MergeIntoExistingBike(
        val existingBikeId: String
    ) : ImportDestination()
}

data class BikeImportSelection(
    val includeBike: Boolean = true,
    val destination: ImportDestination,
    val updateBikeProfileAndOdo: Boolean = false,
    val importTechnicalSpecs: Boolean = true,
    val importMaintenanceTasks: Boolean = true,
    val selectedServiceLogKeys: Set<String> = emptySet(),
    val selectedFuelLogKeys: Set<String> = emptySet(),
    val selectedPartLogKeys: Set<String> = emptySet(),
    val selectedReminderKeys: Set<String> = emptySet(),
    val selectedRideKeys: Set<String> = emptySet(),
    val importAppLogs: Boolean = false
)

data class GuidedImportSelection(
    val perBikeSelections: Map<String, BikeImportSelection>,
    val applyPreferences: Boolean = false
)

object GuidedImportPlanner {

    @OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
    private val lenientJson = Json {
        encodeDefaults = true
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
        explicitNulls = false
    }

    fun sanitizeJsonInput(raw: String): String {
        val noFences = raw.replace("```json", "").replace("```", "")
        val firstBrace = noFences.indexOf('{')
        val lastBrace = noFences.lastIndexOf('}')
        if (firstBrace != -1 && lastBrace != -1 && lastBrace >= firstBrace) {
            return noFences.substring(firstBrace, lastBrace + 1).trim()
        }
        return noFences.trim()
    }

    fun analyzeJsonString(
        rawJson: String,
        existingBikes: List<MotorcycleEntity>
    ): Result<ImportPreviewPlan> {
        return try {
            val cleanedJson = sanitizeJsonInput(rawJson)
            val root = lenientJson.parseToJsonElement(cleanedJson).jsonObject

            val plan = when {
                root.containsKey("data") && root["data"] is JsonArray -> {
                    parseMyBikesExport(root, existingBikes)
                }
                root.containsKey("motorcycles") && root["motorcycles"] is JsonArray -> {
                    parseKurveBackup(cleanedJson, existingBikes)
                }
                else -> {
                    parseAiManualSchedule(root, existingBikes)
                }
            }
            Result.success(plan)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun buildDefaultSelection(plan: ImportPreviewPlan): GuidedImportSelection {
        val map = linkedMapOf<String, BikeImportSelection>()
        plan.bikeGroups.forEach { group ->
            val destination = if (plan.format == ImportSourceFormat.AI_MANUAL_SCHEDULE && group.suggestedExistingBikeId != null) {
                ImportDestination.MergeIntoExistingBike(group.suggestedExistingBikeId)
            } else {
                ImportDestination.CreateNewBike(
                    currencySymbol = group.motorcycle.currencySymbol.ifBlank { "€" },
                    seedDefaultScheduleIfEmpty = group.maintenanceTasks.isEmpty()
                )
            }
            map[group.sourceKey] = BikeImportSelection(
                includeBike = true,
                destination = destination,
                updateBikeProfileAndOdo = destination is ImportDestination.CreateNewBike,
                importTechnicalSpecs = group.technicalSpecs != null,
                importMaintenanceTasks = group.maintenanceTasks.isNotEmpty(),
                selectedServiceLogKeys = group.serviceLogs.filter { !it.isDuplicateInFile }.map { it.selectionKey }.toSet(),
                selectedFuelLogKeys = group.fuelLogs.map { it.selectionKey }.toSet(),
                selectedPartLogKeys = group.partLogs.map { it.selectionKey }.toSet(),
                selectedReminderKeys = group.reminders.map { it.selectionKey }.toSet(),
                selectedRideKeys = group.rides.map { it.selectionKey }.toSet(),
                importAppLogs = false
            )
        }
        return GuidedImportSelection(
            perBikeSelections = map,
            applyPreferences = false
        )
    }

    private fun parseMyBikesExport(
        root: JsonObject,
        existingBikes: List<MotorcycleEntity>
    ): ImportPreviewPlan {
        val dataArray = root["data"]?.jsonArray ?: JsonArray(emptyList())
        val rawLogsCount = (root["logs"] as? JsonArray)?.size ?: 0

        val bikesById = linkedMapOf<String, MotorcycleDto>()
        val servicesByBike = linkedMapOf<String, MutableList<ServiceLogDto>>()
        val fuelByBike = linkedMapOf<String, MutableList<ImportFuelLogCandidate>>()
        val partsByBike = linkedMapOf<String, MutableList<PartLogEntity>>()
        val remindersByBike = linkedMapOf<String, MutableList<ReminderEntity>>()

        dataArray.forEach { elem ->
            val obj = elem as? JsonObject ?: return@forEach
            val entity = obj.str("entity").lowercase()
            val collection = obj.str("collectionName").lowercase()

            when {
                entity == "bikeinfo" || collection == "bikeinfo" -> {
                    val bikeId = obj.str("id").ifBlank { obj.str("_id") }.ifBlank { UUID.randomUUID().toString() }
                    val name = obj.str("name").trim()
                    val make = obj.str("brand").ifBlank { obj.str("make") }.trim().ifBlank { "Unknown" }
                    val model = obj.str("model").trim()
                    val type = obj.str("type").trim().ifBlank { "MotorCycle" }
                    val startDate = obj.str("startDate").trim().ifBlank { "2025-01-01" }
                    val buyingPrice = obj.dbl("buyingPrice", 0.0)
                    val buyingOdo = obj.dbl("buyingOdometerReading", 0.0)
                    val currentOdo = obj.dbl("odometerReading", buyingOdo)
                    val baseOdo = if (buyingOdo > 0.0) buyingOdo else currentOdo
                    val manualOffset = if (buyingOdo > 0.0 && currentOdo >= buyingOdo) {
                        currentOdo - buyingOdo
                    } else 0.0
                    val year = obj.intOrNull("year")?.takeIf { it > 1900 }
                        ?: Regex("""^(\d{4})""").find(startDate)?.groupValues?.getOrNull(1)?.toIntOrNull()
                        ?: 2024
                    val cc = obj.intOrNull("engineDisplacementCc") ?: 0

                    bikesById[bikeId] = MotorcycleDto(
                        id = bikeId,
                        name = name.ifBlank { "$make $model".trim() },
                        make = make,
                        model = model,
                        year = year,
                        engineDisplacementCc = cc,
                        baseOdometerKm = baseOdo,
                        accumulatedTrackedKm = 0.0,
                        manualAdjustmentOffsetKm = manualOffset,
                        isActive = false,
                        type = type,
                        startDateIso = startDate,
                        buyingPrice = buyingPrice,
                        currencySymbol = obj.str("currencySymbol").ifBlank { "€" }
                    )
                }

                entity == "servicing" || collection == "services" -> {
                    val bikeKey = obj.str("partitionKey").ifBlank { "unassigned" }
                    val logId = obj.str("_id").ifBlank { obj.str("id") }.ifBlank { UUID.randomUUID().toString() }
                    val dateIso = obj.str("servicingDate").trim()
                        .ifBlank { obj.str("createdAt").trim().take(10) }
                        .ifBlank { "2025-01-01" }
                    val odo = obj.dbl("odometerReadingWhenServiced", 0.0)
                    val cost = obj.dbl("cost", 0.0)
                    val center = obj.str("servicingCenter").trim().ifBlank { "Home Garage" }
                    val limit = obj.dblOrNull("limit")?.takeIf { it > 0.0 } ?: 5000.0
                    val note = obj.str("note").trim()

                    val checklist = buildMyBikesServiceChecklist(obj)
                    val completedIds = checklist.filter { it.completesTask }.mapNotNull { it.taskId }.distinct()
                    val derivedTaskId = when {
                        completedIds.size == 1 -> completedIds.first()
                        completedIds.size > 1 -> "MULTI_TASK_SERVICE"
                        else -> "GENERAL_SERVICE"
                    }
                    val title = deriveServiceTitle(note, checklist, center)

                    val dto = ServiceLogDto(
                        logId = logId,
                        taskId = derivedTaskId,
                        performedAtIsoDate = dateIso,
                        odometerAtServiceKm = odo,
                        costEur = cost,
                        performedBy = center,
                        notes = note,
                        serviceTitle = title,
                        locationOrShop = center,
                        servicedAtKm = odo,
                        limitKm = limit,
                        endKm = odo + limit,
                        checklistItemsJson = lenientJson.encodeToString(checklist)
                    )
                    servicesByBike.getOrPut(bikeKey) { mutableListOf() }.add(dto)
                }

                entity == "fuel" || collection == "fuels" -> {
                    val bikeKey = obj.str("partitionKey").ifBlank { "unassigned" }
                    val dateIso = obj.str("refuelingDate").trim()
                        .ifBlank { obj.str("createdAt").trim().take(10) }
                        .ifBlank { "2025-01-01" }
                    val odo = obj.dbl("odometerReadingWhenRefueled", 0.0)
                    val liters = obj.dbl("fuelVolume", 0.0)
                    val unitPrice = obj.dblOrNull("unitPrice") ?: 0.0
                    val totalCost = obj.dblOrNull("totalCost") ?: if (unitPrice > 0.0) unitPrice * liters else 0.0
                    val inTank = obj.dblOrNull("fuelAlreadyInTank")
                    val pump = obj.str("pumpStation").trim()
                    val fuelType = obj.str("fuelType").trim()
                    val rawNote = obj.str("note").trim()

                    val noteParts = buildList {
                        if (pump.isNotBlank()) add(pump)
                        if (fuelType.isNotBlank()) add(fuelType)
                        if (inTank != null) add("In tank: ${inTank.toInt()} L")
                        if (rawNote.isNotBlank()) add(rawNote)
                    }
                    val fuelIdKey = obj.str("_id").ifBlank { obj.str("id") }.ifBlank { UUID.randomUUID().toString() }
                    val fuelEntity = FuelLogEntity(
                        fuelId = 0L,
                        motorcycleId = bikeKey,
                        dateIso = dateIso,
                        odometerKm = odo,
                        liters = liters,
                        pricePerLiter = unitPrice,
                        totalCost = totalCost,
                        isFullTank = inTank == null || inTank <= 0.0,
                        notes = noteParts.joinToString(" • ")
                    )
                    val stationLabel = listOf(pump, fuelType, rawNote).filter { it.isNotBlank() }.joinToString(" • ")
                    fuelByBike.getOrPut(bikeKey) { mutableListOf() }.add(
                        ImportFuelLogCandidate(
                            selectionKey = "fuel_$fuelIdKey",
                            log = fuelEntity,
                            stationOrFuelLabel = stationLabel
                        )
                    )
                }

                entity == "part" || entity == "parts" || collection == "parts" -> {
                    val bikeKey = obj.str("partitionKey").ifBlank { "unassigned" }
                    partsByBike.getOrPut(bikeKey) { mutableListOf() }.add(
                        PartLogEntity(
                            partId = 0L,
                            motorcycleId = bikeKey,
                            partName = obj.str("partName").ifBlank { obj.str("name") }.ifBlank { "Part" },
                            brand = obj.str("brand").ifBlank { "OEM" },
                            partNumber = obj.str("partNumber"),
                            cost = obj.dbl("cost", 0.0),
                            odometerKm = obj.dbl("odometerKm", obj.dbl("odometerReading", 0.0)),
                            dateIso = obj.str("dateIso").ifBlank { obj.str("date").ifBlank { "2025-01-01" } },
                            notes = obj.str("note").ifBlank { obj.str("notes") }
                        )
                    )
                }

                entity == "reminder" || entity == "reminders" || collection == "reminders" -> {
                    val bikeKey = obj.str("partitionKey").ifBlank { "unassigned" }
                    remindersByBike.getOrPut(bikeKey) { mutableListOf() }.add(
                        ReminderEntity(
                            reminderId = 0L,
                            motorcycleId = bikeKey,
                            title = obj.str("title").ifBlank { obj.str("name") }.ifBlank { "Reminder" },
                            targetOdometerKm = obj.dblOrNull("targetOdometerKm"),
                            targetDateIso = obj.str("targetDateIso").ifBlank { null },
                            isCompleted = obj.boolOrNull("isCompleted") ?: false
                        )
                    )
                }
            }
        }

        val allKeys = (bikesById.keys + servicesByBike.keys + fuelByBike.keys + partsByBike.keys + remindersByBike.keys).distinct()
        val groups = allKeys.map { key ->
            val explicitBike = bikesById[key]
            val bikeDto = explicitBike ?: MotorcycleDto(
                id = if (key == "unassigned") UUID.randomUUID().toString() else key,
                name = "Imported Bike Records",
                make = "Motorcycle",
                model = "",
                year = 2024,
                engineDisplacementCc = 0,
                baseOdometerKm = 0.0,
                accumulatedTrackedKm = 0.0,
                manualAdjustmentOffsetKm = 0.0,
                isActive = false
            )

            val rawServices = servicesByBike[key].orEmpty()
                .sortedWith(
                    compareByDescending<ServiceLogDto> { it.performedAtIsoDate }
                        .thenByDescending { it.odometerAtServiceKm }
                )
            val seenFingerprints = mutableSetOf<String>()
            val serviceCandidates = rawServices.mapIndexed { idx, svc ->
                val fp = "${svc.performedAtIsoDate}|${svc.odometerAtServiceKm.toInt()}|${svc.costEur.toInt()}|${svc.notes.trim().lowercase()}"
                val isDup = !seenFingerprints.add(fp)
                ImportServiceLogCandidate(
                    selectionKey = "svc_${key}_${idx}_${svc.logId}",
                    log = svc,
                    isDuplicateInFile = isDup
                )
            }

            val partCandidates = partsByBike[key].orEmpty().mapIndexed { idx, p ->
                ImportPartLogCandidate("part_${key}_$idx", p)
            }
            val reminderCandidates = remindersByBike[key].orEmpty().mapIndexed { idx, r ->
                ImportReminderCandidate("rem_${key}_$idx", r)
            }

            ImportSourceBikeGroup(
                sourceKey = key,
                motorcycle = bikeDto,
                serviceLogs = serviceCandidates,
                fuelLogs = fuelByBike[key].orEmpty().sortedByDescending { it.log.dateIso },
                partLogs = partCandidates,
                reminders = reminderCandidates,
                suggestedExistingBikeId = findSuggestedExistingBikeId(bikeDto, existingBikes),
                hasExplicitBikeMetadata = explicitBike != null
            )
        }

        return ImportPreviewPlan(
            format = ImportSourceFormat.MYBIKES_EXPORT,
            bikeGroups = groups,
            preferences = null,
            rawChangelogEntriesCount = rawLogsCount
        )
    }

    private fun parseKurveBackup(
        cleanedJson: String,
        existingBikes: List<MotorcycleEntity>
    ): ImportPreviewPlan {
        val payload = lenientJson.decodeFromString<GarageBackupPayload>(cleanedJson)
        val groups = payload.motorcycles.mapIndexed { bikeIdx, backup ->
            val m = backup.motorcycle
            val seenFingerprints = mutableSetOf<String>()
            val serviceCandidates = backup.serviceLogs.mapIndexed { idx, s ->
                val fp = "${s.performedAtIsoDate}|${s.odometerAtServiceKm.toInt()}|${s.serviceTitle.trim().lowercase()}|${s.notes.trim().lowercase()}"
                val isDup = !seenFingerprints.add(fp)
                ImportServiceLogCandidate("svc_${m.id}_${idx}_${s.logId}", s, isDup)
            }
            val fuelCandidates = backup.fuelLogs.mapIndexed { idx, f ->
                ImportFuelLogCandidate(
                    selectionKey = "fuel_${m.id}_${idx}_${f.fuelId}",
                    log = f,
                    stationOrFuelLabel = f.notes
                )
            }
            val partCandidates = backup.partLogs.mapIndexed { idx, p ->
                ImportPartLogCandidate("part_${m.id}_${idx}_${p.partId}", p)
            }
            val reminderCandidates = backup.reminders.mapIndexed { idx, r ->
                ImportReminderCandidate("rem_${m.id}_${idx}_${r.reminderId}", r)
            }
            val rideCandidates = backup.rides.mapIndexed { idx, r ->
                ImportRideCandidate("ride_${m.id}_${idx}_${r.session.startTimeEpochMs}", r)
            }

            ImportSourceBikeGroup(
                sourceKey = "${m.id}#$bikeIdx",
                motorcycle = m,
                technicalSpecs = backup.technicalSpecs,
                maintenanceTasks = ServiceTaskDeduplicator.deduplicateTaskDtos(backup.maintenanceSchedule),
                serviceLogs = serviceCandidates,
                fuelLogs = fuelCandidates,
                partLogs = partCandidates,
                reminders = reminderCandidates,
                rides = rideCandidates,
                appLogs = backup.appLogs,
                suggestedExistingBikeId = findSuggestedExistingBikeId(m, existingBikes),
                hasExplicitBikeMetadata = true
            )
        }

        return ImportPreviewPlan(
            format = ImportSourceFormat.KURVE_GARAGE_BACKUP,
            bikeGroups = groups,
            preferences = payload.preferences
        )
    }

    private fun parseAiManualSchedule(
        root: JsonObject,
        existingBikes: List<MotorcycleEntity>
    ): ImportPreviewPlan {
        val techSpecs = root["technicalSpecs"]?.let {
            lenientJson.decodeFromString<TechnicalSpecsDto>(it.toString())
        }
        val rawSchedule = root["maintenanceSchedule"]?.let {
            lenientJson.decodeFromString<List<MaintenanceTaskDto>>(it.toString())
        } ?: emptyList()
        val schedule = ServiceTaskDeduplicator.deduplicateTaskDtos(rawSchedule)

        val motoInfo = root["motorcycleInfo"]?.let {
            lenientJson.decodeFromString<MotorcycleInfoV2Dto>(it.toString())
        }

        val make = motoInfo?.make?.ifBlank { null } ?: techSpecs?.motorcycleMake ?: "Motorcycle"
        val model = motoInfo?.model?.ifBlank { null } ?: techSpecs?.motorcycleModel ?: "Specs Import"
        val year = motoInfo?.year?.takeIf { it > 0 } ?: 2024
        val cc = motoInfo?.engineDisplacementCc?.takeIf { it > 0 } ?: 0

        val bikeDto = MotorcycleDto(
            id = "ai_manual_import",
            name = "$make $model".trim(),
            make = make,
            model = model,
            year = year,
            engineDisplacementCc = cc,
            baseOdometerKm = 0.0,
            accumulatedTrackedKm = 0.0,
            manualAdjustmentOffsetKm = 0.0,
            isActive = false
        )

        val suggested = existingBikes.firstOrNull { it.isActive }?.id ?: existingBikes.firstOrNull()?.id

        return ImportPreviewPlan(
            format = ImportSourceFormat.AI_MANUAL_SCHEDULE,
            bikeGroups = listOf(
                ImportSourceBikeGroup(
                    sourceKey = "ai_manual_import",
                    motorcycle = bikeDto,
                    technicalSpecs = techSpecs,
                    maintenanceTasks = schedule,
                    suggestedExistingBikeId = suggested,
                    hasExplicitBikeMetadata = motoInfo != null || techSpecs?.motorcycleMake != null
                )
            )
        )
    }

    private fun buildMyBikesServiceChecklist(obj: JsonObject): List<ServiceChecklistItem> {
        val items = mutableListOf<ServiceChecklistItem>()

        val oilDone = obj.boolOrNull("engineOilCheck") == true
        val filterDone = obj.boolOrNull("engineOilFilterCheck") == true
        if (oilDone || filterDone) {
            if (oilDone) {
                items.add(
                    ServiceChecklistItem(
                        name = if (filterDone) "Engine Oil & Filter" else "Engine Oil",
                        status = "DONE",
                        taskId = "STD_ENGINE_OIL",
                        subActions = if (filterDone) listOf("OIL_CHANGED", "OIL_FILTER_CHANGED") else listOf("OIL_CHANGED"),
                        completesTask = true
                    )
                )
            } else {
                items.add(
                    ServiceChecklistItem(
                        name = "Engine Oil Filter",
                        status = "DONE",
                        taskId = "GEN_OIL_FILTER",
                        subActions = listOf("OIL_FILTER_CHANGED"),
                        completesTask = false
                    )
                )
            }
        }

        if (obj.boolOrNull("airFilterCheck") == true) {
            items.add(
                ServiceChecklistItem(
                    name = "Air Filter Element",
                    status = "DONE",
                    taskId = "STD_AIR_FILTER",
                    subActions = listOf("AIR_CLEANED"),
                    completesTask = true
                )
            )
        }

        if (obj.boolOrNull("sparkPlugCheck") == true) {
            items.add(
                ServiceChecklistItem(
                    name = "Spark Plugs",
                    status = "DONE",
                    taskId = "STD_SPARK_PLUGS",
                    subActions = listOf("PLUG_CHANGED"),
                    completesTask = true
                )
            )
        }

        if (obj.boolOrNull("valveClearenceCheck") == true || obj.boolOrNull("valveClearanceCheck") == true) {
            items.add(
                ServiceChecklistItem(
                    name = "Valve Clearance Check",
                    status = "DONE",
                    taskId = "STD_VALVE_CLEARANCE",
                    subActions = listOf("VALVES_CHECKED"),
                    completesTask = true
                )
            )
        }

        if (obj.boolOrNull("brakeOilCheck") == true) {
            items.add(
                ServiceChecklistItem(
                    name = "Brake Fluid",
                    status = "DONE",
                    taskId = "STD_BRAKE_FLUID",
                    subActions = listOf("BRAKE_FLUID_CHANGED"),
                    completesTask = true
                )
            )
        }

        if (obj.boolOrNull("tirePressureCheck") == true) {
            items.add(
                ServiceChecklistItem(
                    name = "Tire Pressure & Tread",
                    status = "DONE",
                    taskId = "STD_TIRES_AXLES",
                    subActions = listOf("TIRE_PRESSURE_CHECKED"),
                    completesTask = true
                )
            )
        }

        if (obj.boolOrNull("brakePadCheck") == true) {
            items.add(
                ServiceChecklistItem(
                    name = "Brake Pads Front/Rear",
                    status = "CHECKED",
                    taskId = "GEN_BRAKE_PADS",
                    subActions = listOf("FRONT_PADS_CHECKED", "REAR_PADS_CHECKED"),
                    completesTask = false
                )
            )
        }

        if (obj.boolOrNull("batteryCheck") == true) {
            items.add(
                ServiceChecklistItem(
                    name = "Battery & Charging System",
                    status = "CHECKED",
                    taskId = "GEN_BATTERY",
                    subActions = listOf("BATTERY_CHECKED"),
                    completesTask = false
                )
            )
        }

        if (obj.boolOrNull("lightSystemCheck") == true) {
            items.add(
                ServiceChecklistItem(
                    name = "Lights & Indicators",
                    status = "CHECKED",
                    taskId = "GEN_LIGHTS",
                    subActions = listOf("LIGHTS_CHECKED"),
                    completesTask = false
                )
            )
        }

        if (obj.boolOrNull("clutchBrakeLeverCheck") == true || obj.boolOrNull("throttleCheck") == true) {
            items.add(
                ServiceChecklistItem(
                    name = "Clutch & Throttle Freeplay",
                    status = "CHECKED",
                    taskId = "GEN_CLUTCH_THROTTLE",
                    subActions = listOf("CONTROLS_CHECKED"),
                    completesTask = false
                )
            )
        }

        if (obj.boolOrNull("forkOilCheck") == true) {
            items.add(
                ServiceChecklistItem(
                    name = "Front Fork Seals & Oil",
                    status = "CHECKED",
                    taskId = "GEN_FORKS",
                    subActions = listOf("FORKS_CHECKED"),
                    completesTask = false
                )
            )
        }

        if (obj.boolOrNull("bearingCheck") == true) {
            items.add(
                ServiceChecklistItem(
                    name = "Steering Head Bearings",
                    status = "CHECKED",
                    taskId = "GEN_STEERING",
                    subActions = listOf("STEERING_CHECKED"),
                    completesTask = false
                )
            )
        }

        if (obj.boolOrNull("fuelSystemCheck") == true) {
            items.add(ServiceChecklistItem(name = "Fuel System Check", status = "CHECKED"))
        }
        if (obj.boolOrNull("idleRpmCheck") == true) {
            items.add(ServiceChecklistItem(name = "Idle RPM Check", status = "CHECKED"))
        }
        if (obj.boolOrNull("wash") == true) {
            items.add(ServiceChecklistItem(name = "Bike Wash", status = "CHECKED"))
        }

        (obj["customServiceCheckList"] as? JsonArray)?.forEach { elem ->
            val cObj = elem as? JsonObject ?: return@forEach
            val customName = cObj.str("name").trim()
            val checked = cObj.boolOrNull("value") == true
            if (customName.isNotBlank() && checked) {
                items.add(ServiceChecklistItem(name = customName, status = "CHECKED"))
            }
        }

        return ServiceTaskDeduplicator.deduplicateChecklistItems(items)
    }

    private fun deriveServiceTitle(
        note: String,
        checklist: List<ServiceChecklistItem>,
        center: String
    ): String {
        val cleanedNoteLine = note.lineSequence().firstOrNull { it.isNotBlank() }?.trim().orEmpty()
        return when {
            cleanedNoteLine.isNotBlank() -> cleanedNoteLine.take(56)
            checklist.isNotEmpty() -> checklist.take(3).joinToString(", ") { it.name }.take(56)
            else -> "Workshop Service ($center)"
        }
    }

    private fun findSuggestedExistingBikeId(
        sourceBike: MotorcycleDto,
        existingBikes: List<MotorcycleEntity>
    ): String? {
        existingBikes.firstOrNull { it.id == sourceBike.id }?.let { return it.id }
        val srcModel = sourceBike.model.trim().lowercase()
        val srcName = sourceBike.name.trim().lowercase()
        if (srcModel.isNotBlank()) {
            existingBikes.firstOrNull {
                it.model.trim().lowercase().contains(srcModel) ||
                    srcModel.contains(it.model.trim().lowercase().takeIf { m -> m.isNotBlank() } ?: "___")
            }?.let { return it.id }
        }
        if (srcName.isNotBlank()) {
            existingBikes.firstOrNull {
                it.displayName.trim().lowercase() == srcName
            }?.let { return it.id }
        }
        return null
    }

    private fun JsonObject.str(key: String): String {
        val elem = this[key] ?: return ""
        if (elem is JsonNull) return ""
        return (elem as? JsonPrimitive)?.contentOrNull ?: ""
    }

    private fun JsonObject.dblOrNull(key: String): Double? {
        val elem = this[key] ?: return null
        if (elem is JsonNull) return null
        return (elem as? JsonPrimitive)?.doubleOrNull?.takeIf { it.isFinite() }
    }

    private fun JsonObject.dbl(key: String, default: Double): Double = dblOrNull(key) ?: default

    private fun JsonObject.intOrNull(key: String): Int? {
        val elem = this[key] ?: return null
        if (elem is JsonNull) return null
        return (elem as? JsonPrimitive)?.contentOrNull?.toIntOrNull()
            ?: (elem as? JsonPrimitive)?.doubleOrNull?.toInt()
    }

    private fun JsonObject.boolOrNull(key: String): Boolean? {
        val elem = this[key] ?: return null
        if (elem is JsonNull) return null
        return (elem as? JsonPrimitive)?.booleanOrNull
    }
}

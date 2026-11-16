package com.motogeek.telemetry.domain.maintenance

import com.motogeek.telemetry.data.local.MaintenanceTaskEntity
import com.motogeek.telemetry.data.local.ServiceChecklistItem
import com.motogeek.telemetry.data.local.ServiceLogEntity
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.text.Normalizer

data class TaskDeduplicationResult(
    val deduplicatedTasks: List<MaintenanceTaskEntity>,
    val remappedTaskIds: Map<String, String>,
    val removedTaskIds: Set<String>
)

object ServiceTaskDeduplicator {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = true
    }

    private val DIACRITICS_REGEX = Regex("\\p{InCombiningDiacriticalMarks}+")
    private val WHITESPACE_REGEX = Regex("\\s+")
    private val NON_ALNUM_REGEX = Regex("[^a-z0-9]+")

    private val GENERAL_LOG_TASK_IDS = setOf(
        "GENERAL_SERVICE",
        "MULTI_TASK_SERVICE",
        "INSPECTION_16_POINT"
    )

    fun stripAccents(input: String): String {
        if (input.isBlank()) return ""
        val normalized = Normalizer.normalize(input, Normalizer.Form.NFD)
        return DIACRITICS_REGEX.replace(normalized, "")
            .replace('ł', 'l')
            .replace('Ł', 'l')
            .lowercase()
            .trim()
            .replace(WHITESPACE_REGEX, " ")
    }

    fun canonicalTaskKey(
        taskId: String?,
        taskName: String,
        category: String = ""
    ): String {
        val id = taskId.orEmpty().trim().uppercase()
        val n = stripAccents(taskName)
        val cat = stripAccents(category)

        // 1. Air Filter
        if (
            id in setOf("STD_AIR_FILTER", "GEN_AIR_FILTER", "AIR_FILTER", "AIR_FILTER_CHANGE", "AIR_FILTER_CLEAN") ||
            n.contains("air filter") ||
            n.contains("airfilter") ||
            n.contains("air cleaner") ||
            n.contains("air element") ||
            n.contains("vzduchovy filter") ||
            n.contains("vzduchoveho filtra") ||
            n.contains("vzduchova vlozka") ||
            n.contains("filter vzduchu") ||
            (n.contains("vzduch") && (n.contains("filter") || n.contains("filtr") || n.contains("vlozk")))
        ) {
            return "STD_AIR_FILTER"
        }

        // 2. Standalone Oil Filter (distinct from combined Engine Oil & Filter)
        val isStandaloneOilFilterName = (
            n == "oil filter" ||
                n == "engine oil filter" ||
                n == "olejovy filter" ||
                n == "filter oleja"
            ) && !n.contains("&") && !n.contains(" and ") && !n.contains(" a ") &&
            !n.contains("change") && !n.contains("vymena")

        if (
            id in setOf("GEN_OIL_FILTER", "OIL_FILTER") ||
            (isStandaloneOilFilterName && id !in setOf("STD_ENGINE_OIL", "GEN_ENGINE_OIL"))
        ) {
            return "GEN_OIL_FILTER"
        }

        // 3. Engine Oil & Filter (excludes fork oil, brake oil/fluid, shock oil, chain lube, air filter)
        if (
            id in setOf("STD_ENGINE_OIL", "GEN_ENGINE_OIL", "OIL_CHANGE", "ENGINE_OIL", "ENGINE_OIL_CHANGE", "OIL_AND_FILTER") ||
            (
                (n.contains("oil") || n.contains("olej")) &&
                    !n.contains("fork") &&
                    !n.contains("vidlic") &&
                    !n.contains("brake") &&
                    !n.contains("brzd") &&
                    !n.contains("shock") &&
                    !n.contains("tlmic") &&
                    !n.contains("chain") &&
                    !n.contains("retaz") &&
                    !n.contains("air") &&
                    !n.contains("vzduch") &&
                    !isStandaloneOilFilterName
                )
        ) {
            return "STD_ENGINE_OIL"
        }

        // 4. Coolant / Radiator
        if (
            id in setOf("STD_COOLANT", "GEN_COOLANT", "COOLANT", "COOLANT_FLUSH", "COOLANT_CHANGE") ||
            n.contains("coolant") ||
            n.contains("radiator") ||
            n.contains("chladiac") ||
            n.contains("chladic")
        ) {
            return "STD_COOLANT"
        }

        // 5. Brake Fluid
        if (
            id in setOf("STD_BRAKE_FLUID", "GEN_BRAKE_FLUID", "BRAKE_FLUID", "BRAKE_OIL") ||
            ((n.contains("brake") || n.contains("brzd")) && (n.contains("fluid") || n.contains("kvapalin") || n.contains("oil") || n.contains("olej") || n.contains("dot")))
        ) {
            return "STD_BRAKE_FLUID"
        }

        // 6. Brake Pads (keeps Front-only, Rear-only, and Combined Front/Rear distinct)
        if (
            id in setOf(
                "GEN_BRAKE_PADS",
                "STD_BRAKE_PADS",
                "BRAKE_PADS",
                "FRONT_BRAKE_PADS",
                "REAR_BRAKE_PADS",
                "STD_BRAKE_PADS_FRONT",
                "STD_BRAKE_PADS_REAR"
            ) ||
            n.contains("pad") ||
            n.contains("platnic") ||
            n.contains("dostic") ||
            n.contains("oblozen")
        ) {
            val hasFront = n.contains("front") || n.contains("predn")
            val hasRear = n.contains("rear") || n.contains("zadn")
            val isBoth = n.contains("p/z") || n.contains("f/r")
            return when {
                id == "FRONT_BRAKE_PADS" || id == "STD_BRAKE_PADS_FRONT" || (hasFront && !hasRear && !isBoth) -> "STD_BRAKE_PADS_FRONT"
                id == "REAR_BRAKE_PADS" || id == "STD_BRAKE_PADS_REAR" || (hasRear && !hasFront && !isBoth) -> "STD_BRAKE_PADS_REAR"
                else -> "GEN_BRAKE_PADS"
            }
        }

        // 7. Spark Plugs
        if (
            id in setOf("STD_SPARK_PLUGS", "GEN_SPARK_PLUGS", "SPARK_PLUGS", "SPARK_PLUG") ||
            n.contains("spark plug") ||
            n.contains("sparkplug") ||
            n.contains("svieck") ||
            (cat == "ignition" && n.contains("spark"))
        ) {
            return "STD_SPARK_PLUGS"
        }

        // 8. Drive Chain & Sprockets
        if (
            id in setOf("STD_CHAIN_LUBE", "GEN_CHAIN", "CHAIN_LUBE", "CHAIN_MAINTENANCE") ||
            n.contains("chain") ||
            n.contains("retaz") ||
            n.contains("sprocket") ||
            n.contains("rozet")
        ) {
            return "STD_CHAIN_LUBE"
        }

        // 9. Valve Clearance
        if (
            id in setOf("STD_VALVE_CLEARANCE", "VALVE_CLEARANCE", "VALVES") ||
            ((n.contains("valve") || n.contains("ventil")) && !n.contains("tire") && !n.contains("tyre") && !n.contains("pneu"))
        ) {
            return "STD_VALVE_CLEARANCE"
        }

        // 10. Tires & Axles
        if (
            id in setOf("STD_TIRES_AXLES", "GEN_TIRES", "TIRES", "TIRE_PRESSURE") ||
            n.contains("tire") ||
            n.contains("tyre") ||
            n.contains("pneu") ||
            (n.contains("axle") && !n.contains("bolt") && !n.contains("skrut"))
        ) {
            return "STD_TIRES_AXLES"
        }

        // 11. Front Forks & Fork Oil
        if (
            id in setOf("GEN_FORKS", "FORK_OIL", "FRONT_FORKS") ||
            n.contains("fork") ||
            n.contains("vidlic")
        ) {
            return "GEN_FORKS"
        }

        // 12. Rear Shock Absorber
        if (
            id in setOf("GEN_REAR_SHOCK", "REAR_SHOCK") ||
            n.contains("shock") ||
            n.contains("tlmic")
        ) {
            return "GEN_REAR_SHOCK"
        }

        // 13. Steering Head Bearings
        if (
            id in setOf("GEN_STEERING", "STEERING_BEARINGS") ||
            n.contains("steering") ||
            n.contains("krk riadenia") ||
            n.contains("krku riadenia") ||
            n.contains("hlavove zlozenie")
        ) {
            return "GEN_STEERING"
        }

        // 14. Clutch & Throttle Freeplay
        if (
            id == "GEN_CLUTCH_THROTTLE" ||
            n.contains("clutch") ||
            n.contains("throttle") ||
            n.contains("spojk") ||
            n.contains("plyn")
        ) {
            return "GEN_CLUTCH_THROTTLE"
        }

        // 15. Battery & Charging
        if (
            id in setOf("GEN_BATTERY", "BATTERY") ||
            n.contains("battery") ||
            n.contains("bater") ||
            n.contains("akumulator")
        ) {
            return "GEN_BATTERY"
        }

        // 16. Lights & Indicators
        if (
            id in setOf("GEN_LIGHTS", "LIGHTS") ||
            n.contains("light") ||
            n.contains("svetl") ||
            n.contains("smerovk")
        ) {
            return "GEN_LIGHTS"
        }

        // 17. Bolts & Fasteners Torque
        if (
            id in setOf("GEN_BOLTS", "BOLTS_TORQUE") ||
            n.contains("bolt") ||
            n.contains("fastener") ||
            n.contains("torque") ||
            n.contains("skrut")
        ) {
            return "GEN_BOLTS"
        }

        // Fallback for custom tasks
        val slug = NON_ALNUM_REGEX.replace(n, "_").trim('_')
        return if (slug.isNotBlank()) "CUSTOM_$slug" else id.ifBlank { "UNKNOWN_TASK" }
    }

    fun deduplicateTasks(tasks: List<MaintenanceTaskEntity>): TaskDeduplicationResult {
        if (tasks.isEmpty()) {
            return TaskDeduplicationResult(emptyList(), emptyMap(), emptySet())
        }

        val grouped = linkedMapOf<Pair<String, String>, MutableList<MaintenanceTaskEntity>>()
        for (task in tasks) {
            val key = canonicalTaskKey(task.taskId, task.taskName, task.category)
            grouped.getOrPut(task.motorcycleId to key) { mutableListOf() }.add(task)
        }

        val deduplicated = mutableListOf<MaintenanceTaskEntity>()
        val remappedTaskIds = linkedMapOf<String, String>()
        val removedTaskIds = linkedSetOf<String>()

        for ((groupKey, group) in grouped) {
            val (_, canonicalKey) = groupKey
            if (group.size == 1) {
                deduplicated.add(group.first())
                continue
            }

            val scored = group.sortedByDescending { scoreEntity(it, canonicalKey) }
            val best = scored.first()

            val stdId = group.firstOrNull { it.taskId.startsWith("STD_", ignoreCase = true) }?.taskId
            val survivorTaskId = when {
                canonicalKey.startsWith("STD_") -> canonicalKey
                stdId != null -> stdId
                else -> best.taskId
            }

            val mergedOemParts = group.flatMap { decodeOemParts(it.oemPartNumbersJson) }.distinct()
            val mergedNotes = mergeNotes(scored.map { it.notes })

            val survivor = best.copy(
                taskId = survivorTaskId,
                taskName = best.taskName.ifBlank { group.firstNotNullOfOrNull { it.taskName.takeIf(String::isNotBlank) } ?: survivorTaskId },
                category = best.category.ifBlank { group.firstNotNullOfOrNull { it.category.takeIf(String::isNotBlank) } ?: "General" },
                actionType = best.actionType.ifBlank { group.firstNotNullOfOrNull { it.actionType.takeIf(String::isNotBlank) } ?: "Inspect" },
                firstBreakInAtKm = best.firstBreakInAtKm?.takeIf { it > 0.0 }
                    ?: scored.firstNotNullOfOrNull { it.firstBreakInAtKm?.takeIf { v -> v > 0.0 } },
                intervalKm = best.intervalKm?.takeIf { it > 0.0 }
                    ?: scored.firstNotNullOfOrNull { it.intervalKm?.takeIf { v -> v > 0.0 } },
                intervalMonths = best.intervalMonths?.takeIf { it > 0 }
                    ?: scored.firstNotNullOfOrNull { it.intervalMonths?.takeIf { v -> v > 0 } },
                warningThresholdKm = best.warningThresholdKm?.takeIf { it > 0.0 }
                    ?: scored.firstNotNullOfOrNull { it.warningThresholdKm?.takeIf { v -> v > 0.0 } },
                warningThresholdDays = best.warningThresholdDays?.takeIf { it > 0 }
                    ?: scored.firstNotNullOfOrNull { it.warningThresholdDays?.takeIf { v -> v > 0 } },
                oemPartNumbersJson = if (mergedOemParts.isNotEmpty()) json.encodeToString(mergedOemParts) else "[]",
                notes = mergedNotes
            )

            deduplicated.add(survivor)
            for (member in group) {
                if (member.taskId != survivor.taskId) {
                    remappedTaskIds[member.taskId] = survivor.taskId
                    removedTaskIds.add(member.taskId)
                }
            }
        }

        return TaskDeduplicationResult(
            deduplicatedTasks = deduplicated,
            remappedTaskIds = remappedTaskIds,
            removedTaskIds = removedTaskIds
        )
    }

    fun deduplicateTaskDtos(tasks: List<MaintenanceTaskDto>): List<MaintenanceTaskDto> {
        if (tasks.isEmpty()) return emptyList()

        val grouped = linkedMapOf<String, MutableList<MaintenanceTaskDto>>()
        for (task in tasks) {
            val key = canonicalTaskKey(task.taskId, task.taskName, task.category)
            grouped.getOrPut(key) { mutableListOf() }.add(task)
        }

        return grouped.entries.map { (canonicalKey, group) ->
            if (group.size == 1) {
                group.first()
            } else {
                val scored = group.sortedByDescending { scoreDto(it, canonicalKey) }
                val best = scored.first()
                val stdId = group.firstOrNull { it.taskId.startsWith("STD_", ignoreCase = true) }?.taskId
                val survivorTaskId = when {
                    canonicalKey.startsWith("STD_") -> canonicalKey
                    stdId != null -> stdId
                    else -> best.taskId
                }
                val mergedParts = group.flatMap { it.oemPartNumbers }.map { it.trim() }.filter { it.isNotBlank() }.distinct()
                val mergedNotes = mergeNotes(scored.map { it.notes })

                best.copy(
                    taskId = survivorTaskId,
                    taskName = best.taskName.ifBlank { group.firstNotNullOfOrNull { it.taskName.takeIf(String::isNotBlank) } ?: survivorTaskId },
                    category = best.category.ifBlank { group.firstNotNullOfOrNull { it.category.takeIf(String::isNotBlank) } ?: "General" },
                    actionType = best.actionType.ifBlank { group.firstNotNullOfOrNull { it.actionType.takeIf(String::isNotBlank) } ?: "Inspect" },
                    firstBreakInAtKm = best.firstBreakInAtKm?.takeIf { it > 0.0 }
                        ?: scored.firstNotNullOfOrNull { it.firstBreakInAtKm?.takeIf { v -> v > 0.0 } },
                    intervalKm = best.intervalKm?.takeIf { it > 0.0 }
                        ?: scored.firstNotNullOfOrNull { it.intervalKm?.takeIf { v -> v > 0.0 } },
                    intervalMonths = best.intervalMonths?.takeIf { it > 0 }
                        ?: scored.firstNotNullOfOrNull { it.intervalMonths?.takeIf { v -> v > 0 } },
                    warningThresholdKm = best.warningThresholdKm?.takeIf { it > 0.0 }
                        ?: scored.firstNotNullOfOrNull { it.warningThresholdKm?.takeIf { v -> v > 0.0 } },
                    warningThresholdDays = best.warningThresholdDays?.takeIf { it > 0 }
                        ?: scored.firstNotNullOfOrNull { it.warningThresholdDays?.takeIf { v -> v > 0 } },
                    oemPartNumbers = mergedParts,
                    notes = mergedNotes
                )
            }
        }
    }

    fun deduplicateChecklistItems(
        items: List<ServiceChecklistItem>,
        existingTasks: List<MaintenanceTaskEntity> = emptyList(),
        remappedTaskIds: Map<String, String> = emptyMap()
    ): List<ServiceChecklistItem> {
        if (items.isEmpty()) return emptyList()

        val existingById = existingTasks.associateBy { it.taskId }
        val existingByCanonicalKey = existingTasks.groupBy {
            canonicalTaskKey(it.taskId, it.taskName, it.category)
        }.mapValues { (_, list) -> list.first() }

        val grouped = linkedMapOf<String, MutableList<Pair<ServiceChecklistItem, String?>>>()

        for (item in items) {
            val rawTid = item.taskId?.takeIf { it.isNotBlank() }
            val remappedTid = rawTid?.let { remappedTaskIds[it] ?: it }
            val directTask = remappedTid?.let { existingById[it] }
            val key = if (directTask != null) {
                canonicalTaskKey(directTask.taskId, directTask.taskName, directTask.category)
            } else {
                canonicalTaskKey(remappedTid, item.name)
            }
            val resolvedTid = directTask?.taskId
                ?: existingByCanonicalKey[key]?.taskId
                ?: remappedTid

            grouped.getOrPut(key) { mutableListOf() }.add(item to resolvedTid)
        }

        return grouped.values.map { entries ->
            val groupItems = entries.map { it.first }
            val resolvedTaskId = entries.firstNotNullOfOrNull { it.second }
            val completes = groupItems.any { it.completesTask }
            val status = when {
                completes || groupItems.any { it.status.equals("DONE", ignoreCase = true) } -> "DONE"
                groupItems.any { it.status.equals("WARNING", ignoreCase = true) } -> "WARNING"
                groupItems.any { it.status.equals("CHECKED", ignoreCase = true) } -> "CHECKED"
                else -> groupItems.first().status
            }
            val subActions = groupItems.flatMap { it.subActions }.distinct()
            val note = groupItems.map { it.note.trim() }.filter { it.isNotBlank() }.distinct().joinToString(" • ")

            ServiceChecklistItem(
                name = groupItems.first().name,
                status = status,
                taskId = resolvedTaskId,
                subActions = subActions,
                note = note,
                completesTask = completes
            )
        }
    }

    fun remapServiceLog(
        log: ServiceLogEntity,
        existingTasks: List<MaintenanceTaskEntity>,
        remappedTaskIds: Map<String, String> = emptyMap()
    ): ServiceLogEntity {
        val existingById = existingTasks.associateBy { it.taskId }
        val existingByCanonicalKey = existingTasks.groupBy {
            canonicalTaskKey(it.taskId, it.taskName, it.category)
        }.mapValues { (_, list) -> list.first() }

        var newTaskId = log.taskId
        if (newTaskId in remappedTaskIds) {
            newTaskId = remappedTaskIds.getValue(newTaskId)
        } else if (
            newTaskId.isNotBlank() &&
            newTaskId.uppercase() !in GENERAL_LOG_TASK_IDS &&
            !existingById.containsKey(newTaskId)
        ) {
            val key = canonicalTaskKey(newTaskId, log.serviceTitle)
            existingByCanonicalKey[key]?.let { matched ->
                newTaskId = matched.taskId
            }
        }

        val parsedChecklist = log.parseChecklistOrEmpty()
        var newChecklistJson = log.checklistItemsJson
        if (parsedChecklist.isNotEmpty()) {
            val deduped = deduplicateChecklistItems(parsedChecklist, existingTasks, remappedTaskIds)
            if (deduped != parsedChecklist) {
                newChecklistJson = json.encodeToString(deduped)
            }
        }

        return if (newTaskId != log.taskId || newChecklistJson != log.checklistItemsJson) {
            log.copy(taskId = newTaskId, checklistItemsJson = newChecklistJson)
        } else {
            log
        }
    }

    private fun scoreEntity(task: MaintenanceTaskEntity, canonicalKey: String): Int {
        var score = 0
        if (task.taskId.startsWith("STD_", ignoreCase = true)) score += 1000
        if (task.taskId.equals(canonicalKey, ignoreCase = true)) score += 500
        if ((task.intervalKm ?: 0.0) > 0.0) score += 200
        if ((task.intervalMonths ?: 0) > 0) score += 100
        if (task.notes.isNotBlank()) score += 50 + minOf(task.notes.trim().length, 100)
        if (decodeOemParts(task.oemPartNumbersJson).isNotEmpty()) score += 30
        return score
    }

    private fun scoreDto(task: MaintenanceTaskDto, canonicalKey: String): Int {
        var score = 0
        if (task.taskId.startsWith("STD_", ignoreCase = true)) score += 1000
        if (task.taskId.equals(canonicalKey, ignoreCase = true)) score += 500
        if ((task.intervalKm ?: 0.0) > 0.0) score += 200
        if ((task.intervalMonths ?: 0) > 0) score += 100
        if (task.notes.isNotBlank()) score += 50 + minOf(task.notes.trim().length, 100)
        if (task.oemPartNumbers.isNotEmpty()) score += 30
        return score
    }

    private fun decodeOemParts(rawJson: String): List<String> {
        if (rawJson.isBlank() || rawJson == "[]") return emptyList()
        return try {
            json.decodeFromString<List<String>>(rawJson)
                .map { it.trim() }
                .filter { it.isNotBlank() }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun mergeNotes(notesInPriorityOrder: List<String>): String {
        val result = mutableListOf<String>()
        val seenNormalized = mutableListOf<String>()
        for (raw in notesInPriorityOrder) {
            val trimmed = raw.trim()
            if (trimmed.isBlank()) continue
            val norm = stripAccents(trimmed)
            if (seenNormalized.any { it == norm || it.contains(norm) }) continue
            result.add(trimmed)
            seenNormalized.add(norm)
        }
        return result.joinToString("\n\n")
    }
}

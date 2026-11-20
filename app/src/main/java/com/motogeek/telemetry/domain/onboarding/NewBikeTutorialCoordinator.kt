package com.motogeek.telemetry.domain.onboarding

import com.motogeek.telemetry.data.local.MotoGeekRepository
import com.motogeek.telemetry.data.local.MotorcycleEntity
import com.motogeek.telemetry.domain.maintenance.GuidedImportPlanner
import com.motogeek.telemetry.domain.maintenance.MaintenanceTaskDto
import com.motogeek.telemetry.domain.maintenance.ServiceTaskDeduplicator
import com.motogeek.telemetry.domain.maintenance.TechnicalSpecsDto
import kotlinx.serialization.encodeToString
import java.time.LocalDate
import java.util.UUID

class NewBikeTutorialCoordinator(
    private val repository: MotoGeekRepository,
    private val draftStore: NewBikeTutorialDraftStore,
    private val todayProvider: () -> LocalDate = { LocalDate.now() }
) {

    suspend fun startOrResume(): NewBikeTutorialDraft {
        val existing = draftStore.loadDraft()
        if (existing != null && existing.isOpen) {
            return existing
        }
        val previousActiveId = repository.getActiveMotorcycle()?.id
        val fresh = NewBikeTutorialDraft(
            draftBikeId = UUID.randomUUID().toString(),
            previousActiveBikeId = previousActiveId,
            isOpen = true,
            stage = TutorialStage.BIKE_BASICS
        )
        return draftStore.saveDraft(fresh)
    }

    suspend fun updateDraft(draft: NewBikeTutorialDraft): NewBikeTutorialDraft {
        val saved = draftStore.saveDraft(draft)
        val persistedId = saved.persistedBikeId?.takeIf { it.isNotBlank() } ?: return saved
        val existingBike = repository.getMotorcycleById(persistedId) ?: return saved
        val updatedEntity = saved.toMotorcycleEntity(
            todayIso = todayProvider().toString(),
            isActive = existingBike.isActive
        ).copy(
            id = existingBike.id,
            accumulatedTrackedKm = existingBike.accumulatedTrackedKm
        )
        repository.updateMotorcycle(updatedEntity)
        return saved
    }

    fun applyLlmJsonToDraft(
        draft: NewBikeTutorialDraft,
        rawJson: String
    ): Result<NewBikeTutorialDraft> {
        val analysisResult = GuidedImportPlanner.analyzeJsonString(rawJson, existingBikes = emptyList())
        if (analysisResult.isFailure) {
            return Result.failure(analysisResult.exceptionOrNull() ?: IllegalArgumentException("Invalid JSON"))
        }
        val group = analysisResult.getOrNull()?.bikeGroups?.firstOrNull()
            ?: return Result.failure(IllegalArgumentException("No bike schedule found in JSON"))

        val specs = group.technicalSpecs
        val encodedExtraSpecs = specs?.let { onboardingJson.encodeToString(it) } ?: draft.extraSpecsJson
        val dedupedTasks = ServiceTaskDeduplicator.deduplicateTaskDtos(
            draft.maintenanceTasks + group.maintenanceTasks
        )

        val updated = draft.copy(
            rawLlmJson = rawJson,
            extraSpecsJson = encodedExtraSpecs,
            maintenanceTasks = dedupedTasks,
            make = if (draft.make.isBlank() && group.hasExplicitBikeMetadata) {
                group.motorcycle.make
            } else {
                draft.make
            },
            model = if (draft.model.isBlank() && group.hasExplicitBikeMetadata) {
                group.motorcycle.model
            } else {
                draft.model
            },
            yearText = if (draft.yearText.isBlank() && group.hasExplicitBikeMetadata && group.motorcycle.year > 0) {
                group.motorcycle.year.toString()
            } else {
                draft.yearText
            },
            engineDisplacementCcText = if (
                draft.engineDisplacementCcText.isBlank() &&
                group.hasExplicitBikeMetadata &&
                group.motorcycle.engineDisplacementCc > 0
            ) {
                group.motorcycle.engineDisplacementCc.toString()
            } else {
                draft.engineDisplacementCcText
            },
            oilCapacityLitersText = specs?.oilCapacityLiters?.let(::formatCleanNumber)?.ifBlank { null }
                ?: draft.oilCapacityLitersText,
            oilCapacityNoFilterLitersText = specs?.oilCapacityNoFilterLiters?.let(::formatCleanNumber)?.ifBlank { null }
                ?: draft.oilCapacityNoFilterLitersText,
            oilTotalDryCapacityLitersText = specs?.oilTotalDryCapacityLiters?.let(::formatCleanNumber)?.ifBlank { null }
                ?: draft.oilTotalDryCapacityLitersText,
            oilViscosityAndSpec = specs?.oilViscosityAndSpec?.trim()?.ifBlank { null }
                ?: draft.oilViscosityAndSpec,
            coolantCapacityLitersText = specs?.coolantCapacityLiters?.let(::formatCleanNumber)?.ifBlank { null }
                ?: draft.coolantCapacityLitersText,
            coolantType = specs?.coolantType?.trim()?.ifBlank { null }
                ?: draft.coolantType,
            forkOilCapacityPerLegMlText = specs?.forkOilCapacityPerLegMl?.let(::formatCleanNumber)?.ifBlank { null }
                ?: draft.forkOilCapacityPerLegMlText,
            forkOilWeight = specs?.forkOilWeight?.trim()?.ifBlank { null }
                ?: draft.forkOilWeight,
            brakeFluidType = specs?.brakeFluidType?.trim()?.ifBlank { null }
                ?: draft.brakeFluidType,
            brakeFluidVolumeMlText = specs?.brakeFluidVolumeMl?.takeIf { it > 0 }?.toString()
                ?: draft.brakeFluidVolumeMlText,
            valveClearanceIntakeMm = specs?.valveClearanceIntakeMm?.trim()?.ifBlank { null }
                ?: draft.valveClearanceIntakeMm,
            valveClearanceExhaustMm = specs?.valveClearanceExhaustMm?.trim()?.ifBlank { null }
                ?: draft.valveClearanceExhaustMm,
            sparkPlugModel = specs?.sparkPlugModel?.trim()?.ifBlank { null }
                ?: draft.sparkPlugModel,
            sparkPlugGapMm = specs?.sparkPlugGapMm?.trim()?.ifBlank { null }
                ?: draft.sparkPlugGapMm,
            idleSpeedRpm = specs?.idleSpeedRpm?.trim()?.ifBlank { null }
                ?: draft.idleSpeedRpm,
            frontTirePressurePsiText = specs?.frontTirePressurePsi?.let(::formatCleanNumber)?.ifBlank { null }
                ?: draft.frontTirePressurePsiText,
            rearTirePressurePsiText = specs?.rearTirePressurePsi?.let(::formatCleanNumber)?.ifBlank { null }
                ?: draft.rearTirePressurePsiText,
            frontTireSize = specs?.frontTireSize?.trim()?.ifBlank { null }
                ?: draft.frontTireSize,
            rearTireSize = specs?.rearTireSize?.trim()?.ifBlank { null }
                ?: draft.rearTireSize,
            chainSlackMm = specs?.chainSlackMm?.trim()?.ifBlank { null }
                ?: draft.chainSlackMm,
            rearAxleNutTorqueNmText = specs?.rearAxleNutTorqueNm?.let(::formatCleanNumber)?.ifBlank { null }
                ?: draft.rearAxleNutTorqueNmText,
            oilDrainPlugTorqueNmText = specs?.oilDrainPlugTorqueNm?.let(::formatCleanNumber)?.ifBlank { null }
                ?: draft.oilDrainPlugTorqueNmText,
            oilFilterTorqueNmText = specs?.oilFilterTorqueNm?.let(::formatCleanNumber)?.ifBlank { null }
                ?: draft.oilFilterTorqueNmText,
            frontCaliperBoltsTorqueNmText = specs?.frontCaliperBoltsTorqueNm?.let(::formatCleanNumber)?.ifBlank { null }
                ?: draft.frontCaliperBoltsTorqueNmText,
            rearCaliperBoltsTorqueNmText = specs?.rearCaliperBoltsTorqueNm?.let(::formatCleanNumber)?.ifBlank { null }
                ?: draft.rearCaliperBoltsTorqueNmText,
            frontAxleTorqueNmText = specs?.frontAxleTorqueNm?.let(::formatCleanNumber)?.ifBlank { null }
                ?: draft.frontAxleTorqueNmText,
            frontPinchBoltsTorqueNmText = specs?.frontPinchBoltsTorqueNm?.let(::formatCleanNumber)?.ifBlank { null }
                ?: draft.frontPinchBoltsTorqueNmText,
            sparkPlugTorqueNmText = specs?.sparkPlugTorqueNm?.let(::formatCleanNumber)?.ifBlank { null }
                ?: draft.sparkPlugTorqueNmText,
            waterPumpDrainBoltTorqueNmText = specs?.waterPumpDrainBoltTorqueNm?.let(::formatCleanNumber)?.ifBlank { null }
                ?: draft.waterPumpDrainBoltTorqueNmText,
            cylinderDrainBoltTorqueNmText = specs?.cylinderDrainBoltTorqueNm?.let(::formatCleanNumber)?.ifBlank { null }
                ?: draft.cylinderDrainBoltTorqueNmText,
            brakeBleedNippleTorqueNmText = specs?.brakeBleedNippleTorqueNm?.let(::formatCleanNumber)?.ifBlank { null }
                ?: draft.brakeBleedNippleTorqueNmText
        )

        val saved = draftStore.saveDraft(updated)
        return Result.success(saved)
    }

    suspend fun saveBikeOnly(draft: NewBikeTutorialDraft): NewBikeTutorialDraft {
        val (persistedDraft, entity) = persistBikeAndTasks(draft)
        repository.logActivity(
            motorcycleId = entity.id,
            category = "SYSTEM",
            title = "Motorcycle Setup Completed",
            details = "Completed setup for ${entity.displayName.ifBlank { entity.id }}"
        )
        draftStore.clearDraft()
        return persistedDraft.copy(isOpen = false)
    }

    suspend fun saveBikeAndOpenMaintenanceReview(draft: NewBikeTutorialDraft): NewBikeTutorialDraft {
        val (persistedDraft, entity) = persistBikeAndTasks(draft)
        val condition = resolveCondition(persistedDraft, entity.totalOdometerKm)
        val today = todayProvider()
        val expected = ServiceBookBaselineCalculator.buildExpectedHistory(
            condition = condition,
            currentOdometerKm = entity.totalOdometerKm,
            bikeYear = entity.year.takeIf { it > 0 },
            purchaseDateIso = entity.startDateIso,
            tasks = persistedDraft.maintenanceTasks,
            today = today
        )

        val modifiedByTaskId = persistedDraft.maintenanceReviewItems
            .filter { it.isUserModified }
            .associateBy { it.taskId }

        val mergedItems = expected.map { calcItem ->
            modifiedByTaskId[calcItem.taskId] ?: calcItem
        } + persistedDraft.maintenanceReviewItems.filter { existing ->
            existing.isUserModified && expected.none { it.taskId == existing.taskId }
        }

        syncBaselineLogs(
            bikeId = entity.id,
            items = mergedItems,
            condition = condition,
            defaultDateIso = entity.startDateIso.ifBlank { today.toString() }
        )

        val updated = persistedDraft.copy(
            stage = TutorialStage.MAINTENANCE_REVIEW,
            maintenanceReviewItems = mergedItems
        )
        return draftStore.saveDraft(updated)
    }

    suspend fun updateMaintenanceReviewItem(
        draft: NewBikeTutorialDraft,
        updatedItem: MaintenanceHistoryReviewItem
    ): NewBikeTutorialDraft {
        val modified = updatedItem.copy(isUserModified = true)
        val updatedItems = if (draft.maintenanceReviewItems.any { it.taskId == modified.taskId }) {
            draft.maintenanceReviewItems.map { if (it.taskId == modified.taskId) modified else it }
        } else {
            draft.maintenanceReviewItems + modified
        }

        val bikeId = draft.persistedBikeId?.takeIf { it.isNotBlank() }
        if (bikeId != null) {
            val currentOdo = draft.currentOdometerKmText.toFiniteNonNegativeDoubleOrNull() ?: 0.0
            val condition = resolveCondition(draft, currentOdo)
            val defaultDate = draft.purchaseDateIso.trim().ifBlank { todayProvider().toString() }
            upsertOrDeleteBaselineLog(bikeId, modified, condition, defaultDate)
        }

        return draftStore.saveDraft(draft.copy(maintenanceReviewItems = updatedItems))
    }

    suspend fun addMaintenanceReviewTask(
        draft: NewBikeTutorialDraft,
        task: MaintenanceTaskDto,
        lastPerformedKmText: String = "",
        lastPerformedDateIso: String = "",
        wasNeverDone: Boolean = false
    ): NewBikeTutorialDraft {
        val dedupedTasks = ServiceTaskDeduplicator.deduplicateTaskDtos(draft.maintenanceTasks + task)
        val targetKey = ServiceTaskDeduplicator.canonicalTaskKey(task.taskId, task.taskName, task.category)
        val resolvedTask = dedupedTasks.firstOrNull {
            it.taskId == task.taskId ||
                ServiceTaskDeduplicator.canonicalTaskKey(it.taskId, it.taskName, it.category) == targetKey
        } ?: task

        val bikeId = draft.persistedBikeId?.takeIf { it.isNotBlank() }
        if (bikeId != null) {
            repository.insertMaintenanceTask(resolvedTask.toEntity(bikeId))
            repository.deduplicateMaintenanceTasksForBike(bikeId)
        }

        val today = todayProvider()
        val currentOdo = draft.currentOdometerKmText.toFiniteNonNegativeDoubleOrNull() ?: 0.0
        val condition = resolveCondition(draft, currentOdo)
        val hasExplicitBaseline = lastPerformedKmText.isNotBlank() || lastPerformedDateIso.isNotBlank() || wasNeverDone

        val newItem = if (hasExplicitBaseline) {
            MaintenanceHistoryReviewItem(
                taskId = resolvedTask.taskId,
                category = resolvedTask.category,
                taskName = resolvedTask.taskName,
                actionType = resolvedTask.actionType,
                firstBreakInAtKm = resolvedTask.firstBreakInAtKm?.takeIf { it.isFinite() && it > 0.0 },
                intervalKm = resolvedTask.intervalKm?.takeIf { it.isFinite() && it > 0.0 },
                intervalMonths = resolvedTask.intervalMonths?.takeIf { it > 0 },
                warningThresholdKm = resolvedTask.warningThresholdKm?.takeIf { it.isFinite() && it >= 0.0 },
                warningThresholdDays = resolvedTask.warningThresholdDays?.takeIf { it >= 0 },
                oemPartNumbers = resolvedTask.oemPartNumbers,
                notes = resolvedTask.notes,
                lastPerformedKmText = lastPerformedKmText.trim(),
                lastPerformedDateIso = lastPerformedDateIso.trim(),
                wasNeverDone = wasNeverDone,
                isUserModified = true
            )
        } else {
            ServiceBookBaselineCalculator.buildExpectedHistory(
                condition = condition,
                currentOdometerKm = currentOdo,
                bikeYear = draft.yearText.toNonNegativeIntOrNull(),
                purchaseDateIso = draft.purchaseDateIso.trim().ifBlank { today.toString() },
                tasks = listOf(resolvedTask),
                today = today
            ).first()
        }

        val updatedItems = if (draft.maintenanceReviewItems.any { it.taskId == newItem.taskId }) {
            draft.maintenanceReviewItems.map { if (it.taskId == newItem.taskId) newItem else it }
        } else {
            draft.maintenanceReviewItems + newItem
        }

        if (bikeId != null) {
            val defaultDate = draft.purchaseDateIso.trim().ifBlank { today.toString() }
            upsertOrDeleteBaselineLog(bikeId, newItem, condition, defaultDate)
        }

        return draftStore.saveDraft(
            draft.copy(
                maintenanceTasks = dedupedTasks,
                maintenanceReviewItems = updatedItems
            )
        )
    }

    suspend fun saveFinal(draft: NewBikeTutorialDraft): NewBikeTutorialDraft {
        val (persistedDraft, entity) = persistBikeAndTasks(draft)
        val condition = resolveCondition(persistedDraft, entity.totalOdometerKm)
        val defaultDate = entity.startDateIso.ifBlank { todayProvider().toString() }
        syncBaselineLogs(
            bikeId = entity.id,
            items = persistedDraft.maintenanceReviewItems,
            condition = condition,
            defaultDateIso = defaultDate
        )
        repository.logActivity(
            motorcycleId = entity.id,
            category = "SYSTEM",
            title = "Motorcycle Setup Completed",
            details = "Completed setup for ${entity.displayName.ifBlank { entity.id }}"
        )
        draftStore.clearDraft()
        return persistedDraft.copy(isOpen = false)
    }

    suspend fun discardDraft(
        draft: NewBikeTutorialDraft,
        deletePersistedBike: Boolean
    ): NewBikeTutorialDraft {
        draftStore.clearDraft()
        val bikeId = draft.persistedBikeId?.takeIf { it.isNotBlank() }
        if (bikeId != null) {
            if (deletePersistedBike) {
                repository.deleteMotorcycle(bikeId)
                val previousId = draft.previousActiveBikeId?.takeIf { it.isNotBlank() }
                if (previousId != null && repository.getMotorcycleById(previousId) != null) {
                    repository.selectActiveMotorcycle(previousId)
                }
            } else {
                val baselinePrefix = "baseline_${bikeId}_"
                repository.getServiceLogsForMotorcycle(bikeId)
                    .filter { it.logId.startsWith(baselinePrefix) }
                    .forEach { log -> repository.deleteServiceLog(log.logId) }
            }
        }
        return draft.copy(
            isOpen = false,
            persistedBikeId = if (deletePersistedBike) null else draft.persistedBikeId
        )
    }

    private suspend fun persistBikeAndTasks(
        draft: NewBikeTutorialDraft
    ): Pair<NewBikeTutorialDraft, MotorcycleEntity> {
        val todayIso = todayProvider().toString()
        val existingId = draft.persistedBikeId?.takeIf { it.isNotBlank() }
        val existingBike = existingId?.let { repository.getMotorcycleById(it) }

        repository.clearActiveMotorcycles()
        val baseEntity = draft.toMotorcycleEntity(todayIso = todayIso, isActive = true)
        val entity = if (existingBike != null) {
            val updated = baseEntity.copy(
                id = existingBike.id,
                accumulatedTrackedKm = existingBike.accumulatedTrackedKm,
                isActive = true
            )
            repository.updateMotorcycle(updated)
            updated
        } else {
            repository.insertMotorcycle(baseEntity)
            baseEntity
        }

        val dedupedTasks = ServiceTaskDeduplicator.deduplicateTaskDtos(draft.maintenanceTasks)
        if (dedupedTasks.isNotEmpty()) {
            val entities = dedupedTasks.map { it.toEntity(entity.id) }
            repository.insertMaintenanceTasks(entities)
            repository.deduplicateMaintenanceTasksForBike(entity.id)
        }

        val persistedDraft = draft.copy(
            persistedBikeId = entity.id,
            maintenanceTasks = dedupedTasks
        )
        return persistedDraft to entity
    }

    private suspend fun syncBaselineLogs(
        bikeId: String,
        items: List<MaintenanceHistoryReviewItem>,
        condition: BikePurchaseCondition,
        defaultDateIso: String
    ) {
        for (item in items) {
            upsertOrDeleteBaselineLog(bikeId, item, condition, defaultDateIso)
        }
    }

    private suspend fun upsertOrDeleteBaselineLog(
        bikeId: String,
        item: MaintenanceHistoryReviewItem,
        condition: BikePurchaseCondition,
        defaultDateIso: String
    ) {
        val log = ServiceBookBaselineCalculator.buildBaselineServiceLog(
            motorcycleId = bikeId,
            item = item,
            condition = condition,
            defaultDateIso = defaultDateIso
        )
        if (log != null) {
            repository.insertServiceLog(log)
        } else {
            repository.deleteServiceLog(ServiceBookBaselineCalculator.baselineLogId(bikeId, item.taskId))
        }
    }

    private fun resolveCondition(
        draft: NewBikeTutorialDraft,
        odometerKm: Double
    ): BikePurchaseCondition {
        return draft.purchaseCondition ?: if (odometerKm > 0.0) {
            BikePurchaseCondition.USED
        } else {
            BikePurchaseCondition.NEW
        }
    }
}

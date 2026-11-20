package com.motogeek.telemetry.domain.onboarding

import com.motogeek.telemetry.data.local.MaintenanceTaskEntity
import com.motogeek.telemetry.data.local.MotorcycleEntity
import com.motogeek.telemetry.domain.maintenance.MaintenanceTaskDto
import com.motogeek.telemetry.domain.maintenance.TechnicalSpecsDto
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.UUID

internal val onboardingJson = Json {
    ignoreUnknownKeys = true
    isLenient = true
    coerceInputValues = true
    encodeDefaults = true
    explicitNulls = false
}

internal fun String.toFiniteNonNegativeDoubleOrNull(): Double? {
    val parsed = trim().replace(',', '.').toDoubleOrNull() ?: return null
    return parsed.takeIf { it.isFinite() && it >= 0.0 }
}

internal fun String.toFinitePositiveDoubleOrNull(): Double? {
    val parsed = trim().replace(',', '.').toDoubleOrNull() ?: return null
    return parsed.takeIf { it.isFinite() && it > 0.0 }
}

internal fun String.toNonNegativeIntOrNull(): Int? {
    val parsed = trim().toIntOrNull() ?: return null
    return parsed.takeIf { it >= 0 }
}

internal fun formatCleanNumber(value: Double?): String {
    if (value == null || !value.isFinite() || value < 0.0) return ""
    val asLong = value.toLong()
    return if (value == asLong.toDouble()) asLong.toString() else value.toString()
}

@Serializable
enum class BikePurchaseCondition {
    NEW,
    USED
}

@Serializable
enum class TutorialStage {
    BIKE_BASICS,
    SPECS_AND_SCHEDULE,
    MAINTENANCE_REVIEW
}

@Serializable
enum class SpecsInputMode {
    LLM_JSON,
    MANUAL
}

@Serializable
data class MaintenanceHistoryReviewItem(
    val taskId: String,
    val category: String,
    val taskName: String,
    val actionType: String = "Inspect",
    val firstBreakInAtKm: Double? = null,
    val intervalKm: Double? = null,
    val intervalMonths: Int? = null,
    val warningThresholdKm: Double? = null,
    val warningThresholdDays: Int? = null,
    val oemPartNumbers: List<String> = emptyList(),
    val notes: String = "",
    val lastPerformedKmText: String = "",
    val lastPerformedDateIso: String = "",
    val wasNeverDone: Boolean = false,
    val isUserModified: Boolean = false,
    val logNotes: String = ""
)

@Serializable
data class NewBikeTutorialDraft(
    val draftBikeId: String = "",
    val persistedBikeId: String? = null,
    val previousActiveBikeId: String? = null,
    val isOpen: Boolean = false,
    val stage: TutorialStage = TutorialStage.BIKE_BASICS,
    val specsInputMode: SpecsInputMode = SpecsInputMode.LLM_JSON,
    val purchaseCondition: BikePurchaseCondition? = null,
    val make: String = "",
    val model: String = "",
    val nickname: String = "",
    val yearText: String = "",
    val engineDisplacementCcText: String = "",
    val bikeType: String = "",
    val currentOdometerKmText: String = "",
    val purchaseOdometerKmText: String = "",
    val purchaseDateIso: String = "",
    val buyingPriceText: String = "",
    val currencySymbol: String = "€",
    val rawLlmJson: String = "",
    val oilCapacityLitersText: String = "",
    val oilCapacityNoFilterLitersText: String = "",
    val oilTotalDryCapacityLitersText: String = "",
    val oilViscosityAndSpec: String = "",
    val coolantCapacityLitersText: String = "",
    val coolantType: String = "",
    val forkOilCapacityPerLegMlText: String = "",
    val forkOilWeight: String = "",
    val brakeFluidType: String = "",
    val brakeFluidVolumeMlText: String = "",
    val valveClearanceIntakeMm: String = "",
    val valveClearanceExhaustMm: String = "",
    val sparkPlugModel: String = "",
    val sparkPlugGapMm: String = "",
    val idleSpeedRpm: String = "",
    val frontTirePressurePsiText: String = "",
    val rearTirePressurePsiText: String = "",
    val frontTireSize: String = "",
    val rearTireSize: String = "",
    val chainSlackMm: String = "",
    val rearAxleNutTorqueNmText: String = "",
    val oilDrainPlugTorqueNmText: String = "",
    val oilFilterTorqueNmText: String = "",
    val frontCaliperBoltsTorqueNmText: String = "",
    val rearCaliperBoltsTorqueNmText: String = "",
    val frontAxleTorqueNmText: String = "",
    val frontPinchBoltsTorqueNmText: String = "",
    val sparkPlugTorqueNmText: String = "",
    val waterPumpDrainBoltTorqueNmText: String = "",
    val cylinderDrainBoltTorqueNmText: String = "",
    val brakeBleedNippleTorqueNmText: String = "",
    val extraSpecsJson: String = "",
    val maintenanceTasks: List<MaintenanceTaskDto> = emptyList(),
    val maintenanceReviewItems: List<MaintenanceHistoryReviewItem> = emptyList(),
    val lastAutosavedEpochMs: Long = 0L
) {
    fun toTechnicalSpecsDto(): TechnicalSpecsDto {
        val base = if (extraSpecsJson.isNotBlank()) {
            try {
                onboardingJson.decodeFromString<TechnicalSpecsDto>(extraSpecsJson)
            } catch (_: Exception) {
                TechnicalSpecsDto()
            }
        } else {
            TechnicalSpecsDto()
        }

        return base.copy(
            oilCapacityLiters = oilCapacityLitersText.toFinitePositiveDoubleOrNull() ?: base.oilCapacityLiters,
            oilCapacityNoFilterLiters = oilCapacityNoFilterLitersText.toFinitePositiveDoubleOrNull() ?: base.oilCapacityNoFilterLiters,
            oilTotalDryCapacityLiters = oilTotalDryCapacityLitersText.toFinitePositiveDoubleOrNull() ?: base.oilTotalDryCapacityLiters,
            oilViscosityAndSpec = oilViscosityAndSpec.trim().ifBlank { base.oilViscosityAndSpec },
            coolantCapacityLiters = coolantCapacityLitersText.toFinitePositiveDoubleOrNull() ?: base.coolantCapacityLiters,
            coolantType = coolantType.trim().ifBlank { base.coolantType },
            forkOilCapacityPerLegMl = forkOilCapacityPerLegMlText.toFinitePositiveDoubleOrNull() ?: base.forkOilCapacityPerLegMl,
            forkOilWeight = forkOilWeight.trim().ifBlank { base.forkOilWeight },
            brakeFluidType = brakeFluidType.trim().ifBlank { base.brakeFluidType },
            brakeFluidVolumeMl = brakeFluidVolumeMlText.toNonNegativeIntOrNull()?.takeIf { it > 0 } ?: base.brakeFluidVolumeMl,
            valveClearanceIntakeMm = valveClearanceIntakeMm.trim().ifBlank { base.valveClearanceIntakeMm },
            valveClearanceExhaustMm = valveClearanceExhaustMm.trim().ifBlank { base.valveClearanceExhaustMm },
            sparkPlugModel = sparkPlugModel.trim().ifBlank { base.sparkPlugModel },
            sparkPlugGapMm = sparkPlugGapMm.trim().ifBlank { base.sparkPlugGapMm },
            idleSpeedRpm = idleSpeedRpm.trim().ifBlank { base.idleSpeedRpm },
            frontTirePressurePsi = frontTirePressurePsiText.toFinitePositiveDoubleOrNull() ?: base.frontTirePressurePsi,
            rearTirePressurePsi = rearTirePressurePsiText.toFinitePositiveDoubleOrNull() ?: base.rearTirePressurePsi,
            frontTireSize = frontTireSize.trim().ifBlank { base.frontTireSize },
            rearTireSize = rearTireSize.trim().ifBlank { base.rearTireSize },
            chainSlackMm = chainSlackMm.trim().takeIf { it.isNotEmpty() } ?: base.chainSlackMm,
            rearAxleNutTorqueNm = rearAxleNutTorqueNmText.toFinitePositiveDoubleOrNull() ?: base.rearAxleNutTorqueNm,
            oilDrainPlugTorqueNm = oilDrainPlugTorqueNmText.toFinitePositiveDoubleOrNull() ?: base.oilDrainPlugTorqueNm,
            oilFilterTorqueNm = oilFilterTorqueNmText.toFinitePositiveDoubleOrNull() ?: base.oilFilterTorqueNm,
            frontCaliperBoltsTorqueNm = frontCaliperBoltsTorqueNmText.toFinitePositiveDoubleOrNull() ?: base.frontCaliperBoltsTorqueNm,
            rearCaliperBoltsTorqueNm = rearCaliperBoltsTorqueNmText.toFinitePositiveDoubleOrNull() ?: base.rearCaliperBoltsTorqueNm,
            frontAxleTorqueNm = frontAxleTorqueNmText.toFinitePositiveDoubleOrNull() ?: base.frontAxleTorqueNm,
            frontPinchBoltsTorqueNm = frontPinchBoltsTorqueNmText.toFinitePositiveDoubleOrNull() ?: base.frontPinchBoltsTorqueNm,
            sparkPlugTorqueNm = sparkPlugTorqueNmText.toFinitePositiveDoubleOrNull() ?: base.sparkPlugTorqueNm,
            waterPumpDrainBoltTorqueNm = waterPumpDrainBoltTorqueNmText.toFinitePositiveDoubleOrNull() ?: base.waterPumpDrainBoltTorqueNm,
            cylinderDrainBoltTorqueNm = cylinderDrainBoltTorqueNmText.toFinitePositiveDoubleOrNull() ?: base.cylinderDrainBoltTorqueNm,
            brakeBleedNippleTorqueNm = brakeBleedNippleTorqueNmText.toFinitePositiveDoubleOrNull() ?: base.brakeBleedNippleTorqueNm,
            motorcycleMake = make.trim().takeIf { it.isNotEmpty() } ?: base.motorcycleMake,
            motorcycleModel = model.trim().takeIf { it.isNotEmpty() } ?: base.motorcycleModel,
            purchaseCondition = purchaseCondition?.name ?: base.purchaseCondition
        )
    }

    fun toMotorcycleEntity(todayIso: String, isActive: Boolean): MotorcycleEntity {
        val resolvedId = persistedBikeId?.takeIf { it.isNotBlank() }
            ?: draftBikeId.takeIf { it.isNotBlank() }
            ?: UUID.randomUUID().toString()
        val cleanMake = make.trim()
        val cleanModel = model.trim()
        val cleanName = nickname.trim().ifBlank { "$cleanMake $cleanModel".trim() }
        val parsedCurrentOdo = currentOdometerKmText.toFiniteNonNegativeDoubleOrNull()
        val parsedPurchaseOdo = purchaseOdometerKmText.toFiniteNonNegativeDoubleOrNull()
        val baseOdo: Double
        val manualOffset: Double
        when {
            parsedPurchaseOdo != null && parsedCurrentOdo != null && parsedCurrentOdo >= parsedPurchaseOdo -> {
                baseOdo = parsedPurchaseOdo
                manualOffset = parsedCurrentOdo - parsedPurchaseOdo
            }
            parsedCurrentOdo != null -> {
                baseOdo = parsedCurrentOdo
                manualOffset = 0.0
            }
            parsedPurchaseOdo != null -> {
                baseOdo = parsedPurchaseOdo
                manualOffset = 0.0
            }
            else -> {
                baseOdo = 0.0
                manualOffset = 0.0
            }
        }

        val specsDto = toTechnicalSpecsDto()
        val specsJson = onboardingJson.encodeToString(specsDto)

        return MotorcycleEntity(
            id = resolvedId,
            make = cleanMake,
            model = cleanModel,
            year = yearText.toNonNegativeIntOrNull() ?: 0,
            engineDisplacementCc = engineDisplacementCcText.toNonNegativeIntOrNull() ?: 0,
            baseOdometerKm = baseOdo,
            accumulatedTrackedKm = 0.0,
            manualAdjustmentOffsetKm = manualOffset,
            technicalSpecsJson = specsJson,
            isActive = isActive,
            name = cleanName,
            type = bikeType.trim(),
            startDateIso = purchaseDateIso.trim().ifBlank { todayIso },
            buyingPrice = buyingPriceText.toFiniteNonNegativeDoubleOrNull() ?: 0.0,
            currencySymbol = currencySymbol.trim().ifBlank { "€" }
        )
    }

    fun hasAnyUserInput(): Boolean {
        val textFields = listOf(
            make,
            model,
            nickname,
            bikeType,
            purchaseDateIso,
            rawLlmJson,
            oilViscosityAndSpec,
            coolantType,
            forkOilWeight,
            brakeFluidType,
            valveClearanceIntakeMm,
            valveClearanceExhaustMm,
            sparkPlugModel,
            sparkPlugGapMm,
            idleSpeedRpm,
            frontTireSize,
            rearTireSize,
            chainSlackMm,
            extraSpecsJson
        )
        if (textFields.any { it.isNotBlank() }) return true
        if (purchaseCondition != null) return true
        if (currencySymbol.trim().let { it.isNotEmpty() && it != "€" }) return true
        if (maintenanceTasks.isNotEmpty() || maintenanceReviewItems.isNotEmpty()) return true

        val numericFields = listOf(
            yearText,
            engineDisplacementCcText,
            currentOdometerKmText,
            purchaseOdometerKmText,
            buyingPriceText,
            oilCapacityLitersText,
            oilCapacityNoFilterLitersText,
            oilTotalDryCapacityLitersText,
            coolantCapacityLitersText,
            forkOilCapacityPerLegMlText,
            brakeFluidVolumeMlText,
            frontTirePressurePsiText,
            rearTirePressurePsiText,
            rearAxleNutTorqueNmText,
            oilDrainPlugTorqueNmText,
            oilFilterTorqueNmText,
            frontCaliperBoltsTorqueNmText,
            rearCaliperBoltsTorqueNmText,
            frontAxleTorqueNmText,
            frontPinchBoltsTorqueNmText,
            sparkPlugTorqueNmText,
            waterPumpDrainBoltTorqueNmText,
            cylinderDrainBoltTorqueNmText,
            brakeBleedNippleTorqueNmText
        )
        return numericFields.any { raw ->
            val parsed = raw.trim().replace(',', '.').toDoubleOrNull()
            parsed != null && parsed.isFinite() && parsed >= 0.0
        }
    }
}

internal fun MaintenanceTaskDto.toEntity(motorcycleId: String): MaintenanceTaskEntity {
    return MaintenanceTaskEntity(
        taskId = taskId,
        motorcycleId = motorcycleId,
        category = category,
        taskName = taskName,
        actionType = actionType,
        firstBreakInAtKm = firstBreakInAtKm?.takeIf { it.isFinite() && it > 0.0 },
        intervalKm = intervalKm?.takeIf { it.isFinite() && it > 0.0 },
        intervalMonths = intervalMonths?.takeIf { it > 0 },
        warningThresholdKm = warningThresholdKm?.takeIf { it.isFinite() && it >= 0.0 },
        warningThresholdDays = warningThresholdDays?.takeIf { it >= 0 },
        oemPartNumbersJson = onboardingJson.encodeToString(oemPartNumbers),
        notes = notes
    )
}

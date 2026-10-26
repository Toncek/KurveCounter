package com.motogeek.telemetry.domain.maintenance

import kotlinx.serialization.Serializable

@Serializable
data class GarageBackupPayload(
    val exportDateIso: String,
    val motorcycles: List<MotorcycleBackupDto>
)

@Serializable
data class MotorcycleInfoV2Dto(
    val make: String = "",
    val model: String = "",
    val year: Int = 0,
    val engineDisplacementCc: Int = 0
)

@Serializable
data class MotorcycleBackupDto(
    val motorcycle: MotorcycleDto,
    val technicalSpecs: TechnicalSpecsDto?,
    val maintenanceSchedule: List<MaintenanceTaskDto>,
    val serviceLogs: List<ServiceLogDto>
)

@Serializable
data class MotorcycleDto(
    val id: String,
    val make: String,
    val model: String,
    val year: Int,
    val engineDisplacementCc: Int,
    val baseOdometerKm: Double,
    val accumulatedTrackedKm: Double,
    val manualAdjustmentOffsetKm: Double,
    val isActive: Boolean
)

@Serializable
data class TechnicalSpecsDto(
    val oilCapacityLiters: Double? = null,
    val frontTirePressurePsi: Double? = null,
    val rearTirePressurePsi: Double? = null,
    val chainSlackMm: String? = null,
    val rearAxleNutTorqueNm: Double? = null,
    val oilDrainPlugTorqueNm: Double? = null,
    val frontCaliperBoltsTorqueNm: Double? = null,
    val motorcycleMake: String? = null,
    val motorcycleModel: String? = null,
    val boltTorquesNm: Map<String, Double>? = null,
    val torquesNm: Map<String, Double>? = null
)

@Serializable
data class MaintenanceTaskDto(
    val taskId: String,
    val category: String,
    val taskName: String,
    val actionType: String,
    val firstBreakInAtKm: Double? = null,
    val intervalKm: Double? = null,
    val intervalMonths: Int? = null,
    val warningThresholdKm: Double? = null,
    val warningThresholdDays: Int? = null,
    val oemPartNumbers: List<String> = emptyList(),
    val notes: String = ""
)

@Serializable
data class ServiceLogDto(
    val logId: String,
    val taskId: String,
    val performedAtIsoDate: String,
    val odometerAtServiceKm: Double,
    val costEur: Double,
    val performedBy: String,
    val notes: String
)
